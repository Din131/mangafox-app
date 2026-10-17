package com.din131.mangafox

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Mengambil halaman lewat DNS-over-HTTPS, supaya tidak memakai DNS operator
 * (yang sering memblokir situs). Dipakai dari shouldInterceptRequest WebView.
 *
 * Batasan: WebView tidak memberikan isi request POST, jadi hanya GET yang lewat DoH.
 * Request lain (misalnya kirim form login) tetap memakai jaringan biasa.
 */
object DohClient {

    @Volatile private var client: OkHttpClient? = null

    fun init(cacheDir: File) {
        if (client != null) return
        val bootstrap = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "doh"), 1L * 1024 * 1024))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val cloudflare = DnsOverHttps.Builder().client(bootstrap)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
            .build()
        val google = DnsOverHttps.Builder().client(bootstrap)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
            .build()

        // Coba Cloudflare dulu, lalu Google
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                try { cloudflare.lookup(hostname) } catch (e: Exception) { google.lookup(hostname) }
        }

        client = bootstrap.newBuilder()
            .cache(null)
            .dns(dns)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Ambil isi halaman sebagai teks (dipakai fitur Cari, Ranking, dan rating).
     * null kalau gagal. Cookie WebView ikut dikirim supaya pengaturan situs (misalnya
     * konfirmasi konten dewasa) tetap berlaku.
     */
    fun getText(url: String, userAgent: String): String? {
        val http = client ?: return null
        return try {
            val builder = Request.Builder().url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
            CookieManager.getInstance().getCookie(url)?.let { if (it.isNotEmpty()) builder.header("Cookie", it) }
            http.newCall(builder.build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** null = biarkan WebView memuat sendiri dengan jaringan biasa. */
    fun fetch(request: WebResourceRequest, userAgent: String): WebResourceResponse? {
        val http = client ?: return null
        if (!request.method.equals("GET", ignoreCase = true)) return null
        val url = request.url.toString()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        return try {
            val builder = Request.Builder().url(url)
            for ((name, value) in request.requestHeaders) {
                // Kompresi diurus OkHttp sendiri
                if (name.equals("Accept-Encoding", ignoreCase = true)) continue
                builder.header(name, value)
            }
            if (request.requestHeaders.keys.none { it.equals("User-Agent", true) }) {
                builder.header("User-Agent", userAgent)
            }
            val cookies = CookieManager.getInstance()
            cookies.getCookie(url)?.let { if (it.isNotEmpty()) builder.header("Cookie", it) }

            val response = http.newCall(builder.build()).execute()

            // Simpan cookie dari server ke WebView (penting untuk login)
            val finalUrl = response.request.url.toString()
            for (c in response.headers("Set-Cookie")) cookies.setCookie(finalUrl, c)

            val code = response.code
            if (code in 300..399) { response.close(); return null }

            val body = response.body ?: run { response.close(); return null }
            val contentType = body.contentType()
            val mime = contentType?.let { "${it.type}/${it.subtype}" } ?: guessMime(url)
            val charset = contentType?.charset()?.name()

            val headers = HashMap<String, String>()
            for (name in response.headers.names()) {
                if (name.equals("Content-Encoding", true) || name.equals("Content-Length", true) ||
                    name.equals("Transfer-Encoding", true) || name.equals("Set-Cookie", true)) continue
                headers[name] = response.headers.values(name).joinToString(", ")
            }
            val reason = response.message.ifBlank { if (code in 200..299) "OK" else "Error" }

            WebResourceResponse(mime, charset, code, reason, headers, body.byteStream())
        } catch (e: Exception) {
            // DoH gagal: serahkan ke jaringan biasa
            null
        }
    }

    private fun guessMime(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".svg") -> "image/svg+xml"
            else -> "text/html"
        }
    }
}
