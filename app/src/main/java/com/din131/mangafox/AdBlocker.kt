package com.din131.mangafox

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pemblokir iklan berbasis domain.
 * - Daftar bawaan: jaringan iklan / pop-under yang umum di situs streaming.
 * - assets/hosts.txt: daftar StevenBlack, diunduh otomatis saat build di GitHub Actions.
 */
object AdBlocker {

    private val builtIn = setOf(
        // Pop-under & redirect
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com",
        "onclickads.net", "onclickalgo.com", "onclkds.com", "adsterra.com",
        "adsterratools.com", "highperformanceformat.com", "profitablegatecpm.com",
        "profitablecpmrate.com", "monetag.com", "clickadu.com", "hilltopads.net",
        "adcash.com", "exoclick.com", "exosrv.com", "realsrv.com", "juicyads.com",
        "trafficjunky.net", "trafficstars.com", "tsyndicate.com", "a-ads.com",
        "adskeeper.com", "mgid.com", "galaksion.com", "admaven.com", "ad-maven.com",
        "richpartners.co", "pushhouse.io", "bidgear.com", "dtscout.com",
        // Iklan & tracking umum
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "pubmatic.com", "rubiconproject.com",
        "taboola.com", "outbrain.com", "revcontent.com", "criteo.com", "criteo.net",
        "amazon-adsystem.com", "adform.net", "smartadserver.com", "histats.com", "mc.yandex.ru"
    )

    @Volatile
    private var hosts: Set<String> = emptySet()
    private val loading = AtomicBoolean(false)

    /** Muat daftar hosts di background thread (sekitar 80 ribu domain). */
    fun init(context: Context) {
        if (!loading.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread {
            val set = HashSet<String>(100_000)
            try {
                appContext.assets.open("hosts.txt").bufferedReader().useLines { lines ->
                    for (raw in lines) {
                        val line = raw.substringBefore('#').trim()
                        if (line.isEmpty()) continue
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size < 2) continue
                        if (parts[0] != "0.0.0.0" && parts[0] != "127.0.0.1") continue
                        val host = parts[1].lowercase()
                        if (host == "localhost" || host == "0.0.0.0" || host.endsWith(".localdomain")) continue
                        set.add(host)
                    }
                }
            } catch (_: Exception) {
                // hosts.txt tidak ada: pakai daftar bawaan saja
            }
            hosts = set
        }.start()
    }

    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var h = host.lowercase()
        val list = hosts
        // Cek domain dan semua domain induknya: a.b.ads.com -> b.ads.com -> ads.com
        while (true) {
            if (h in builtIn || h in list) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }
}
