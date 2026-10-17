package com.din131.mangafox

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cek rilis terbaru di GitHub Releases. Kalau ada versi lebih baru,
 * tawarkan untuk mengunduh dan meng-install APK-nya langsung.
 */
class UpdateChecker(private val activity: Activity) {

    companion object {
        private const val REPO = "Din131/mangafox-app"
        private const val APK_NAME = "MangaFox.apk"
        private const val PREFS = "update"
    }

    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val currentVersion: Long by lazy {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    }

    /**
     * Hapus file APK update yang sudah terpasang (versinya <= versi sekarang).
     * APK yang belum sempat di-install dibiarkan.
     */
    private fun cleanupInstalledApks() {
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        dir.listFiles()?.forEach { f ->
            if (!f.name.endsWith(".apk")) return@forEach
            val ver = f.name.removeSuffix(".apk").substringAfterLast('.').toLongOrNull()
            if (ver == null || ver <= currentVersion) f.delete()
        }
    }

    /** Dipanggil setiap kali aplikasi dibuka. */
    fun checkInBackground() = check(manual = false)

    /** Dari tombol "Cek update" di halaman Akun: selalu memberi kabar hasilnya. */
    fun checkNow() {
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show()
        check(manual = true)
    }

    private fun toast(res: Int) = activity.runOnUiThread {
        if (!activity.isFinishing) Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
    }

    private fun check(manual: Boolean) {
        Thread {
            try { cleanupInstalledApks() } catch (_: Exception) {}
            try {
                val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != 200) { if (manual) toast(R.string.update_error); return@Thread }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()

                val tag = json.optString("tag_name")             // contoh: v1.0.9
                val latest = tag.substringAfterLast('.').toLongOrNull() ?: return@Thread
                if (latest <= currentVersion) { if (manual) toast(R.string.update_none); return@Thread }
                if (!manual && prefs.getLong("skipped", -1L) == latest) return@Thread

                val assets = json.optJSONArray("assets") ?: return@Thread
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name") == APK_NAME) apkUrl = a.optString("browser_download_url")
                }
                val url = apkUrl ?: return@Thread

                activity.runOnUiThread { showDialog(tag, latest, url) }
            } catch (_: Exception) {
                // Tidak ada internet / GitHub tidak bisa dijangkau: diam saja (kecuali dicek manual)
                if (manual) toast(R.string.update_error)
            }
        }.start()
    }

    private fun showDialog(tag: String, latest: Long, url: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_title))
            .setMessage(activity.getString(R.string.update_message, tag, "v1.0.$currentVersion"))
            .setPositiveButton(R.string.update_now) { _, _ -> download(url, tag) }
            .setNegativeButton(R.string.update_later, null)
            .setNeutralButton(R.string.update_skip) { _, _ ->
                prefs.edit().putLong("skipped", latest).apply()
            }
            .show()
    }

    private fun download(url: String, tag: String) {
        // Android 8+: aplikasi butuh izin "Install unknown apps" untuk memasang update
        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "MangaFox-$tag.apk"
        activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
            dir.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() } // hapus unduhan lama
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(activity.getString(R.string.app_name) + " $tag")
            .setDescription(activity.getString(R.string.update_downloading))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
        val id = dm.enqueue(request)
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                try { context.unregisterReceiver(this) } catch (_: Exception) {}
                val uri = dm.getUriForDownloadedFile(id)
                if (uri == null) {
                    Toast.makeText(context, R.string.update_failed, Toast.LENGTH_LONG).show()
                    return
                }
                val install = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(install)
            }
        }
        ContextCompat.registerReceiver(
            activity.applicationContext, receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}
