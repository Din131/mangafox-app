package com.din131.mangafox

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * Alamat halaman Riwayat Baca. Isinya dari assets/history.html, tapi dibuka "atas nama"
         * m.fanfox.net supaya server gambar mau mengirim sampul (ada proteksi anti-hotlink)
         * dan halaman bisa mengambil sampul dari situs. Alamat ini tidak pernah diminta ke server.
         */
        private const val HISTORY_URL = "https://m.fanfox.net/__riwayat-baca__"

        /** Halaman Cari (Jelajah + Ranking) dari assets/search.html, dengan cara yang sama. */
        private const val SEARCH_URL = "https://m.fanfox.net/__cari__"

        /** Domain yang boleh diambil lewat jembatan MFNet. */
        private val NET_HOSTS = setOf("fanfox.net", "m.fanfox.net", "newm.fanfox.net")

        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    }

    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorView: TextView

    @Volatile private var keepSplash = true
    /** Dibaca dari thread jaringan WebView, jadi disimpan sekali di sini. */
    @Volatile private var userAgent = ""

    // Bilah navigasi bawah
    private lateinit var bottomNav: View
    private lateinit var bottomNavLine: View
    private lateinit var navSearch: View
    private lateinit var navHome: View
    private lateinit var navHistory: View
    private lateinit var navBookmark: View
    private var imeVisible = false
    private var onReaderPage = false
    private var historyJs = ""

    /** Tandai tab aktif dan sembunyikan bilah di halaman baca chapter / saat keyboard muncul. */
    private fun updateBottomNav(url: String? = webView.url) {
        val u = url ?: ""
        val path = Uri.parse(u).path ?: ""
        val isHistory = u.startsWith(HISTORY_URL)
        val isSearch = u.startsWith(SEARCH_URL)
        onReaderPage = !isHistory && !isSearch && (path.startsWith("/roll_manga/") || pagedChapter.containsMatchIn(path))
        navSearch.isSelected = isSearch || (!isHistory && path.startsWith("/search"))
        navHome.isSelected = !isHistory && !isSearch && (path == "" || path == "/" || path.startsWith("/index"))
        navHistory.isSelected = isHistory
        navBookmark.isSelected = !isHistory && path.startsWith("/bookmark")
        val show = !onReaderPage && !imeVisible
        val vis = if (show) View.VISIBLE else View.GONE
        if (bottomNav.visibility != vis) {
            bottomNav.visibility = vis
            bottomNavLine.visibility = vis
        }
    }

    private fun openSearch() {
        val html = assets.open("search.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL(SEARCH_URL, html, "text/html", "utf-8", SEARCH_URL)
    }

    private val netPool = java.util.concurrent.Executors.newFixedThreadPool(4)

    /**
     * Mengambil halaman fanfox.net / newm.fanfox.net di belakang layar untuk fitur Cari,
     * Ranking, rating, dan "chapter baru". Hasil dikirim balik ke window.__mfNet(id, ok, teks).
     */
    private inner class NetBridge {
        @JavascriptInterface
        fun fetch(url: String, id: String) {
            val host = try { Uri.parse(url).host?.lowercase() } catch (e: Exception) { null }
            if (host == null || host !in NET_HOSTS || !url.startsWith("https://")) {
                deliver(id, null); return
            }
            // Versi desktop dipakai untuk direktori & pencarian lanjutan (tidak dialihkan ke m.)
            val ua = if (host == "fanfox.net") DESKTOP_UA else userAgent
            netPool.execute { deliver(id, DohClient.getText(url, ua)) }
        }

        private fun deliver(id: String, text: String?) {
            val js = "window.__mfNet&&window.__mfNet(" + org.json.JSONObject.quote(id) + "," +
                (text != null) + "," + org.json.JSONObject.quote(text ?: "") + ")"
            runOnUiThread { if (!isDestroyed) webView.evaluateJavascript(js, null) }
        }
    }

    private fun openHistory() {
        val html = assets.open("history.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL(HISTORY_URL, html, "text/html", "utf-8", HISTORY_URL)
    }

    private val historyPrefs by lazy { getSharedPreferences("history", MODE_PRIVATE) }

    /** Penyimpanan riwayat baca (JSON). Dipanggil dari JavaScript di thread terpisah. */
    private inner class HistoryBridge {
        @JavascriptInterface
        fun get(): String = historyPrefs.getString("data", "{}") ?: "{}"

        @JavascriptInterface
        fun set(json: String) {
            historyPrefs.edit().putString("data", json).apply()
        }
    }

    private var popupView: WebView? = null

    /** Domain yang boleh dibuka di halaman utama (beserta subdomainnya). */
    private val allowedDomains = mutableSetOf<String>()
    private var initialLoad = true
    private var lastBackPress = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen: tampil sampai halaman pertama siap (maksimal 4 detik)
        val splash = installSplashScreen()
        keepSplash = savedInstanceState == null
        splash.setKeepOnScreenCondition { keepSplash }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(300L)
                .withEndAction { provider.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AdBlocker.init(this)
        DohClient.init(cacheDir)

        webView = findViewById(R.id.webView)
        swipe = findViewById(R.id.swipe)
        progress = findViewById(R.id.progress)
        errorView = findViewById(R.id.errorView)

        // Jarak untuk status bar, navigasi, notch, dan keyboard (Android 15+)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            // Sembunyikan bilah bawah saat keyboard muncul, supaya ruang ketik lega
            val imeNow = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeNow != imeVisible) {
                imeVisible = imeNow
                v.post { updateBottomNav() }
            }
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val startUrl = getString(R.string.start_url)
        Uri.parse(startUrl).host?.let { allowedDomains.add(baseDomain(it)) }
        // Domain lain milik MangaFox (desktop/mirror)
        allowedDomains.addAll(listOf("fanfox.net", "mangafox.me", "mangafox.la"))

        // Layar tidak mati selama membaca
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            // Zoom dengan dua jari untuk halaman komik
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = userAgentString.replace("; wv", "")
        }
        userAgent = webView.settings.userAgentString
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.setBackgroundColor(getColor(R.color.bg))
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.setDownloadListener { _, _, _, _, _ -> /* abaikan unduhan (biasanya iklan) */ }
        // Riwayat baca: disimpan di aplikasi, diakses dari history.js dan history.html
        webView.addJavascriptInterface(HistoryBridge(), "MFHistory")
        webView.addJavascriptInterface(NetBridge(), "MFNet")
        historyJs = assets.open("history.js").bufferedReader().use { it.readText() }
        bottomNav = findViewById(R.id.bottomNav)
        bottomNavLine = findViewById(R.id.bottomNavLine)
        navSearch = findViewById(R.id.navSearch)
        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)
        navBookmark = findViewById(R.id.navBookmark)
        navSearch.setOnClickListener { if (webView.url?.startsWith(SEARCH_URL) != true) openSearch() }
        navHome.setOnClickListener { webView.loadUrl(getString(R.string.start_url)) }
        navHistory.setOnClickListener { if (webView.url?.startsWith(HISTORY_URL) != true) openHistory() }
        navBookmark.setOnClickListener { webView.loadUrl("https://m.fanfox.net/bookmark") }

        // Tarik untuk refresh hanya saat halaman berada di paling atas
        swipe.setColorSchemeColors(getColor(R.color.accent))
        swipe.setProgressBackgroundColorSchemeColor(getColor(R.color.bg))
        swipe.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        swipe.setOnRefreshListener {
            if (webView.url?.startsWith(HISTORY_URL) == true) {
                swipe.isRefreshing = false
                openHistory()
            } else if (webView.url?.startsWith(SEARCH_URL) == true) {
                swipe.isRefreshing = false
                openSearch()
            } else {
                webView.reload()
            }
        }

        webView.postDelayed({ keepSplash = false }, 4000)

        errorView.setOnClickListener {
            errorView.visibility = View.GONE
            webView.reload()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    popupView != null -> popupView!!.let { if (it.canGoBack()) it.goBack() else closePopup() }
                    webView.canGoBack() -> webView.goBack()
                    System.currentTimeMillis() - lastBackPress < 2000 -> finish()
                    else -> {
                        lastBackPress = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, R.string.press_back_again, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            initialLoad = false
        } else {
            webView.loadUrl(startUrl)
        }
        // Cek versi baru di GitHub Releases setiap aplikasi dibuka
        if (savedInstanceState == null) UpdateChecker(this).checkInBackground()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        // Simpan cookie login supaya tetap login setelah aplikasi ditutup
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // ---------- Helper ----------

    /** "m.fanfox.net" -> "fanfox.net" */
    private fun baseDomain(host: String): String {
        val parts = host.lowercase().split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host.lowercase()
    }

    /**
     * Halaman chapter per halaman ".../manga/judul/v01/c001/3.html" diubah ke mode gulir
     * ".../roll_manga/judul/v01/c001/1.html", yang menampilkan semua halaman sekaligus.
     */
    private val pagedChapter = Regex("""^/manga/([^/]+)/((?:v[^/]+/)?c[\d.]+)(?:/(?:\d+\.html)?)?$""")

    private fun toMainDomain(url: Uri): String? {
        val host = url.host?.lowercase() ?: return null
        if (!host.endsWith("mangafox.me") && !host.endsWith("mangafox.la")) return null
        val target = url.buildUpon().scheme("https").authority("m.fanfox.net").build()
        return toScrollMode(target) ?: target.toString()
    }

    private fun toScrollMode(url: Uri): String? {
        val host = url.host?.lowercase() ?: return null
        if (!host.endsWith("fanfox.net") && !host.endsWith("mangafox.me") && !host.endsWith("mangafox.la")) return null
        val m = pagedChapter.find(url.path ?: return null) ?: return null
        val (title, chapter) = m.destructured
        return "${url.scheme}://$host/roll_manga/$title/$chapter/1.html"
    }

    private fun isAllowed(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        return allowedDomains.any { h == it || h.endsWith(".$it") }
    }

    /** Halaman login & captcha pihak ketiga yang boleh dibuka. */
    private val authDomains = setOf(
        "accounts.google.com", "accounts.youtube.com", "www.google.com",
        "discord.com", "discordapp.com",
        "facebook.com", "x.com", "twitter.com", "appleid.apple.com",
        "challenges.cloudflare.com", "hcaptcha.com",
        // Firebase Authentication (dipakai login Google di banyak situs, misalnya Komiku)
        "firebaseapp.com", "web.app", "securetoken.googleapis.com", "identitytoolkit.googleapis.com"
    )

    private fun isAuth(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        return authDomains.any { h == it || h.endsWith(".$it") }
    }

    private fun blockedToast(host: String?) {
        if (host.isNullOrEmpty()) return
        Toast.makeText(this, getString(R.string.link_blocked, host), Toast.LENGTH_SHORT).show()
    }

    private val emptyResponse: WebResourceResponse
        get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    // Sembunyikan sisa elemen iklan + izinkan zoom walau situs mematikannya
    private val pageJs = """
        (function(){
          // Footer (Z6 Shop, Manga App, dll.) dan menu atas situs diganti bilah navigasi aplikasi
          if (!document.getElementById('__mf_chrome')) {
            var ch = document.createElement('style');
            ch.id = '__mf_chrome';
            ch.textContent = 'footer{display:none!important}nav.menu{display:none!important}';
            (document.head || document.documentElement).appendChild(ch);
          }
          if (!document.getElementById('__komik_css')) {
            var s = document.createElement('style');
            s.id = '__komik_css';
            s.textContent = 'ins.adsbygoogle,.adsbygoogle,[id^="ad-banner"],[class^="ad-banner"],' +
              '[id*="popunder"],[class*="popunder"],[id^="ads-"],[class^="ads-"],' +
              'iframe[src*="/ads/"],iframe[src*="doubleclick"],div[id^="div-gpt-ad"]' +
              '{display:none!important;visibility:hidden!important;height:0!important;}';
            (document.head || document.documentElement).appendChild(s);
          }
          // Sembunyikan banner iklan (termasuk iklan judi online) yang disisipkan situs:
          // gambar yang mengarah ke domain lain, atau gambar/link dengan kata kunci judi.
          if (!window.__komikBanner) {
            window.__komikBanner = true;
            var base = function (h) { return (h || '').toLowerCase().split('.').slice(-2).join('.'); };
            // Semua domain milik MangaFox dianggap satu situs
            var SITES = ['fanfox.net', 'mangafox.me', 'mangafox.la', base(location.hostname)];
            var SAFE = /(^|\.)(discord\.gg|discord\.com|facebook\.com|instagram\.com|x\.com|twitter\.com|t\.me|tiktok\.com|youtube\.com|trakteer\.id|saweria\.co|fansku\.id)$/i;
            var GAMBLE = /slot|gacor|judi|togel|toto|casino|kasino|maxwin|scatter|mahjong|sbobet|pragmatic|jackpot|depo|bonus|win88|\d{2,3}(bet|win)|dewa|hoki|zeus|olympus/i;
            // Daftar ketat untuk gambar di domain situs sendiri (hindari menyembunyikan sampul komik)
            var GAMBLE_STRICT = /slot|gacor|judi|togel|casino|kasino|maxwin|scatter|sbobet|pragmatic/i;
            var hideBox = function (el) {
              // Naik ke wadah yang hanya berisi iklan ini, supaya tidak meninggalkan ruang kosong
              var t = el;
              while (t.parentElement && t.parentElement !== document.body &&
                     t.parentElement.children.length === 1) t = t.parentElement;
              t.style.setProperty('display', 'none', 'important');
            };
            var scan = function () {
              var links = document.querySelectorAll('a[href]');
              for (var i = 0; i < links.length; i++) {
                var a = links[i];
                if (a.__komikChecked) continue;
                a.__komikChecked = true;
                var img = a.querySelector('img, picture, video');
                var host = '';
                try { host = new URL(a.href, location.href).hostname; } catch (e) {}
                var external = host && SITES.indexOf(base(host)) < 0 && !SAFE.test(host);
                var text = a.href + ' ' + (img ? (img.getAttribute('src') || '') + ' ' + (img.getAttribute('alt') || '') : '');
                if ((img && external) || (external && GAMBLE.test(text))) hideBox(a);
              }
              var imgs = document.querySelectorAll('img');
              for (var j = 0; j < imgs.length; j++) {
                var im = imgs[j];
                if (im.__komikChecked) continue;
                im.__komikChecked = true;
                var s = (im.getAttribute('src') || '') + ' ' + (im.getAttribute('alt') || '');
                if (GAMBLE_STRICT.test(s) && !/cover|thumb|chapter|series/i.test(s)) hideBox(im.closest('a') || im);
              }
            };
            scan();
            var waiting = false;
            new MutationObserver(function () {
              if (waiting) return;
              waiting = true;
              setTimeout(function () { waiting = false; scan(); }, 200);
            }).observe(document.documentElement, { childList: true, subtree: true });
          }
          // Daftar manga (beranda, dll.): 3 kolom selebar layar, sampul lebih besar,
          // dan judul lengkap 2 baris (situs memotong judul jadi "The Healer...").
          if (document.querySelector('ul.manga-list') && !window.__mfGrid) {
            window.__mfGrid = true;
            var grid = document.createElement('style');
            grid.textContent =
              'ul.manga-list{display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr))!important;' +
                'gap:14px 10px!important;padding:4px 12px 12px!important;margin:0!important;letter-spacing:0!important;text-align:left!important}' +
              'ul.manga-list>li{display:block!important;width:auto!important;margin:0!important;padding:0!important;float:none!important}' +
              'ul.manga-list .post,ul.manga-list .post>a{display:block!important;width:auto!important}' +
              'ul.manga-list .post .cover{width:100%!important;height:auto!important;aspect-ratio:2/3;border:0!important;' +
                'border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.18);background:#eee}' +
              'ul.manga-list .post .cover img{width:100%!important;height:100%!important;object-fit:cover;display:block}' +
              'ul.manga-list .post .cover p{padding:14px 0 3px!important;line-height:1.2!important;font-size:12px!important;' +
                'background:linear-gradient(transparent,rgba(0,0,0,.65))!important}' +
              'ul.manga-list .post p.title{text-align:left!important;color:#333!important;font-size:13px!important;line-height:1.3!important;' +
                'margin:6px 2px 0!important;height:auto!important;white-space:normal!important;display:-webkit-box!important;' +
                '-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}';
            (document.head || document.documentElement).appendChild(grid);
            var fullTitles = function () {
              var posts = document.querySelectorAll('ul.manga-list .post');
              for (var pi = 0; pi < posts.length; pi++) {
                var tEl = posts[pi].querySelector('p.title');
                var cImg = posts[pi].querySelector('.cover img');
                var full = cImg && cImg.getAttribute('title');
                if (tEl && full && tEl.textContent !== full) tEl.textContent = full;
              }
            };
            fullTitles();
            setInterval(fullTitles, 1500); // daftar bisa bertambah saat digulir (infinite scroll)
          }
          // Daftar bergaya list (All, hasil pencarian, genre, bookmark): susunan tetap satu per baris,
          // tapi sampul lebih besar dan tulisan lebih tebal/gelap. Judul ditampilkan lengkap.
          if (document.querySelector('ul.post-list') && !window.__mfList) {
            window.__mfList = true;
            var lst = document.createElement('style');
            lst.textContent =
              'ul.post-list .post-one{padding:10px 12px!important;min-height:156px;box-sizing:content-box}' +
              'ul.post-list .post-one .cover{width:104px!important;height:156px!important;border:0!important;' +
                'border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.18);background:#eee}' +
              'ul.post-list .post-one .cover img{object-fit:cover}' +
              'ul.post-list .post-one .cover-info{padding-left:120px!important}' +
              'ul.post-list .post-one .cover-info p{color:#555!important;font-size:14px!important;margin-top:6px!important;line-height:1.35!important}' +
              'ul.post-list .post-one .cover-info p a{color:#555!important}' +
              'ul.post-list .post-one .cover-info p.title,ul.post-list .post-one .cover-info p.title a{color:#1d1d1d!important;' +
                'font-size:16px!important;font-weight:700!important;margin-top:2px!important;line-height:1.3!important}';
            (document.head || document.documentElement).appendChild(lst);
            var tidyList = function () {
              var items = document.querySelectorAll('ul.post-list .post-one');
              for (var li = 0; li < items.length; li++) {
                var ti = items[li].querySelector('.cover-info p.title');
                var im = items[li].querySelector('.cover img');
                var fullT = im && im.getAttribute('title');
                if (ti && fullT && !ti.querySelector('a') && ti.textContent.trim() !== fullT) ti.textContent = fullT;
              }
            };
            tidyList();
            setInterval(tidyList, 1500); // daftar bertambah saat digulir
          }
          // Kotak pencarian di halaman Search: gaya sama dengan halaman Riwayat Baca
          if (document.querySelector('.genres-manga .top-search') && !window.__mfSearchBox) {
            window.__mfSearchBox = true;
            var sb = document.createElement('style');
            sb.textContent =
              '.genres-manga .top-search{box-sizing:border-box!important;width:100%!important;height:auto!important;padding:12px!important;margin:0!important}' +
              '.genres-manga .top-search-content{background:none!important;margin:0!important;padding:0!important;' +
                'border-radius:0!important;line-height:normal!important;position:relative!important}' +
              '.genres-manga .top-search-content form{position:relative;margin:0}' +
              '.genres-manga .top-search-content .search-text{box-sizing:border-box!important;width:100%!important;height:46px!important;' +
                'border:1px solid #e3e7dd!important;background:#fff!important;border-radius:10px!important;' +
                'padding:10px 70px 10px 12px!important;font:15px/1.4 system-ui,-apple-system,Roboto,sans-serif!important;color:#1d2418!important;' +
                '-webkit-appearance:none;appearance:none}' +
              '.genres-manga .top-search-content .search-text::placeholder{color:#8a9283}' +
              '.genres-manga .top-search-content .search-text:focus{outline:2px solid #75b520!important;border-color:transparent!important}' +
              '.genres-manga .top-search-content .search-icon{top:6px!important;right:6px!important;width:56px!important;height:34px!important;' +
                'line-height:34px!important;border-radius:8px!important;background:#75b520!important;color:#fff!important;' +
                'font:600 14px system-ui,-apple-system,Roboto,sans-serif!important;border:0!important;-webkit-appearance:none;appearance:none}' +
              '.genres-manga .top-search-content .search-icon:active{background:#5a8f16!important}';
            (document.head || document.documentElement).appendChild(sb);
          }
          // Mode gulir: hilangkan pembatas, nomor halaman, dan jarak antar halaman,
          // supaya halaman manga menyambung seperti satu gambar panjang.
          if (/\/roll_manga\//.test(location.pathname) && !window.__mfRoll) {
            window.__mfRoll = true;
            // Bilah hijau atas (judul) dan bawah (menu) selalu tampil. Situs menyembunyikannya
            // dengan fadeOut saat digulir; aturan !important di sini mengalahkan gaya inline-nya.
            var bars = document.createElement('style');
            bars.textContent =
              '.fixed-header,.fixed-footer{display:block!important;opacity:1!important;visibility:visible!important}' +
              '.mangaread-main{padding-top:42px!important;padding-bottom:40px!important}' +
              '.mangaread-main .adv-728-top{display:none!important}';
            (document.head || document.documentElement).appendChild(bars);
            var imp = function (el, p, v) { el.style.setProperty(p, v, 'important'); };
            // Lapis 2: fadeOut dari situs tidak berlaku untuk kedua bilah
            if (window.jQuery && jQuery.fn && !jQuery.fn.__mfPatched) {
              var origFadeOut = jQuery.fn.fadeOut;
              jQuery.fn.fadeOut = function () {
                var rest = this.not('.fixed-header,.fixed-footer');
                return rest.length ? origFadeOut.apply(rest, arguments) : this;
              };
              jQuery.fn.__mfPatched = true;
            }
            // Lapis 3: kalau tetap ada yang menyembunyikan, langsung munculkan lagi
            var keepBars = function () {
              var els = document.querySelectorAll('.fixed-header,.fixed-footer');
              for (var b = 0; b < els.length; b++) {
                var st = els[b].style;
                if (st.getPropertyValue('display') !== 'block' || st.getPropertyPriority('display') !== 'important') imp(els[b], 'display', 'block');
                if (st.getPropertyValue('opacity') !== '1' || st.getPropertyPriority('opacity') !== 'important') imp(els[b], 'opacity', '1');
              }
            };
            keepBars();
            var barEls = document.querySelectorAll('.fixed-header,.fixed-footer');
            for (var bi = 0; bi < barEls.length; bi++) {
              new MutationObserver(keepBars).observe(barEls[bi], { attributes: true, attributeFilter: ['style'] });
            }
            var COUNTER = /^\s*\d+\s*\/\s*\d+\s*$/;
            var isPage = function (img) {
              var s = (img.getAttribute('src') || '') + ' ' + (img.getAttribute('data-src') || '') +
                      ' ' + (img.getAttribute('data-original') || '');
              if (/loading/i.test(s)) return true; // gambar yang belum termuat
              return img.getBoundingClientRect().width >= window.innerWidth * 0.6;
            };
            var flatten = function () {
              var imgs = [].filter.call(document.images, isPage);
              if (imgs.length < 2) return;
              // Wadah daftar halaman = leluhur bersama dua gambar pertama
              var list = imgs[0].parentElement;
              while (list && !list.contains(imgs[1])) list = list.parentElement;
              if (!list) return;
              ['padding', 'margin', 'border'].forEach(function (p) { imp(list, p, '0'); });
              imp(list, 'width', '100%'); imp(list, 'max-width', '100%');
              for (var i = 0; i < imgs.length; i++) {
                var img = imgs[i];
                if (img.__mfFlat) continue;
                img.__mfFlat = true;
                ['margin', 'padding', 'border'].forEach(function (p) { imp(img, p, '0'); });
                imp(img, 'display', 'block'); imp(img, 'vertical-align', 'top');
                imp(img, 'max-width', '100%');
                // Dari gambar naik ke wadah daftar: buang jarak, garis, latar, dan teks nomor halaman
                for (var el = img.parentElement; el && el !== list; el = el.parentElement) {
                  ['margin', 'padding', 'border', 'min-height'].forEach(function (p) { imp(el, p, '0'); });
                  imp(el, 'background', 'transparent'); imp(el, 'box-shadow', 'none');
                  imp(el, 'font-size', '0'); imp(el, 'line-height', '0');
                  imp(el, 'width', '100%'); imp(el, 'max-width', '100%');
                  for (var c = 0; c < el.children.length; c++) {
                    var ch = el.children[c];
                    if (ch === img || ch.contains(img) || ch.querySelector('img')) continue;
                    imp(ch, 'display', 'none'); // nomor halaman, garis, dll.
                  }
                }
              }
              // Gambar yang sudah termuat dibuat selebar layar
              for (var j = 0; j < imgs.length; j++) {
                var s = imgs[j].getAttribute('src') || '';
                if (!/loading/i.test(s)) { imp(imgs[j], 'width', '100%'); imp(imgs[j], 'height', 'auto'); }
              }
              // Nomor halaman yang berdiri sendiri di dalam daftar
              var all = list.querySelectorAll('*');
              for (var k = 0; k < all.length; k++) {
                var n = all[k];
                if (!n.querySelector('img') && n.tagName !== 'IMG' && COUNTER.test(n.textContent || '')) imp(n, 'display', 'none');
              }
            };
            flatten();
            setInterval(flatten, 800); // gambar dimuat bertahap saat digulir
          }
          // Zoom dua jari hanya di halaman baca chapter
          if (!window.__komikZoom) {
            window.__komikZoom = true;
            var ZOOM_ON = 'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5, user-scalable=yes';
            var ZOOM_OFF = 'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no';
            var isReader = function () {
              if (/chapter|\/read\/|\/baca\/|\/roll_manga\/|\/manga\/[^\/]+\/(v[^\/]+\/)?c\d+/i.test(location.pathname)) return true;
              // Ciri halaman komik: banyak gambar tinggi yang hampir selebar layar
              var imgs = document.getElementsByTagName('img'), pages = 0, vw = window.innerWidth;
              for (var i = 0; i < imgs.length && pages < 3; i++) {
                var r = imgs[i].getBoundingClientRect();
                if (r.width >= vw * 0.8 && r.height >= r.width * 1.1) pages++;
              }
              return pages >= 3;
            };
            var applyZoom = function () {
              var want = isReader() ? ZOOM_ON : ZOOM_OFF;
              var m = document.querySelector('meta[name=viewport]');
              if (!m) { m = document.createElement('meta'); m.name = 'viewport'; (document.head || document.documentElement).appendChild(m); }
              if (m.content !== want) m.content = want;
            };
            applyZoom();
            // Situs berpindah halaman tanpa memuat ulang, jadi cek berkala
            setInterval(applyZoom, 1000);
          }
        })();
    """.trimIndent()

    // ---------- WebViewClient: adblock + kunci domain ----------

    private inner class Client : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val host = request.url.host
            // Jangan blokir situs utama maupun layanan login
            if (!isAllowed(host) && !isAuth(host) && AdBlocker.isBlocked(host)) return emptyResponse
            // Halaman login pihak ketiga dibiarkan memakai jaringan biasa
            if (isAuth(host)) return null
            // Ambil lewat DNS-over-HTTPS supaya tidak terkena blokir DNS operator
            return DohClient.fetch(request, userAgent)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            val scheme = url.scheme?.lowercase()

            // intent://, market://, dll. biasanya redirect iklan ke aplikasi lain
            if (scheme != "http" && scheme != "https") {
                return scheme != "about" && scheme != "blob" && scheme != "data" && scheme != "javascript"
            }

            val host = url.host
            if (AdBlocker.isBlocked(host) && !isAllowed(host)) return true
            if (!request.isForMainFrame) return false

            // Domain lama MangaFox (misalnya form pencarian ke m.mangafox.me) -> m.fanfox.net,
            // supaya tetap di satu domain (login dan cookie sama)
            toMainDomain(url)?.let { view.loadUrl(it); return true }

            // Buka chapter dalam mode gulir (semua halaman dalam satu layar)
            toScrollMode(url)?.let { view.loadUrl(it); return true }

            if (isAllowed(host) || isAuth(host)) return false

            // Saat pertama buka, ikuti redirect domain (misalnya situs pindah domain)
            if (initialLoad && request.isRedirect && host != null) {
                allowedDomains.add(baseDomain(host))
                return false
            }

            blockedToast(host)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            errorView.visibility = View.GONE
            updateBottomNav(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            updateBottomNav(url)
        }

        // Jalankan penyaring sedini mungkin, saat halaman mulai tampil
        override fun onPageCommitVisible(view: WebView, url: String?) {
            view.evaluateJavascript(pageJs, null)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            keepSplash = false
            initialLoad = false
            swipe.isRefreshing = false
            view.evaluateJavascript(pageJs, null)
            // Catat riwayat baca (hanya di halaman MangaFox)
            if (url != null && isAllowed(Uri.parse(url).host)) view.evaluateJavascript(historyJs, null)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                errorView.visibility = View.VISIBLE
                swipe.isRefreshing = false
            }
        }
    }

    // ---------- WebChromeClient: progress & pop-up ----------

    private inner class Chrome : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (newProgress >= 80) keepSplash = false
            progress.progress = newProgress
            progress.visibility = if (newProgress < 100 && !swipe.isRefreshing) View.VISIBLE else View.GONE
        }

        /**
         * Tab/jendela baru: tangkap URL tujuannya.
         * - Situs yang sama  -> buka di jendela utama
         * - Halaman login (Google, Discord, dll.) -> tampilkan sebagai jendela pop-up
         * - Domain lain (iklan) -> buang
         */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) return false
            val temp = WebView(this@MainActivity)
            setupWebView(temp)
            temp.webViewClient = object : WebViewClient() {
                private var decided = false

                /** true = batalkan pemuatan */
                private fun decide(url: Uri?): Boolean {
                    val scheme = url?.scheme?.lowercase()
                    if (url == null || scheme == "about" || scheme == "javascript") return false
                    decided = true
                    return when {
                        isAuth(url.host) -> { showPopup(temp); false }
                        isAllowed(url.host) -> {
                            webView.loadUrl(url.toString())
                            temp.post { temp.destroy() }
                            true
                        }
                        else -> {
                            blockedToast(url.host)
                            temp.post { temp.destroy() }
                            true
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    if (decided) return false
                    return decide(request.url)
                }

                override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                    if (!decided && decide(url?.let { Uri.parse(it) })) v.stopLoading()
                }
            }
            temp.webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) = closePopup()
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = temp
            resultMsg.sendToTarget()
            return true
        }
    }

    // ---------- Pop-up login ----------

    private fun showPopup(popup: WebView) {
        if (popupView === popup) return
        closePopup()
        popupView = popup
        findViewById<FrameLayout>(R.id.root).addView(popup, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun closePopup() {
        val p = popupView ?: return
        popupView = null
        findViewById<FrameLayout>(R.id.root).removeView(p)
        p.post { p.destroy() }
        CookieManager.getInstance().flush()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(w: WebView) {
        with(w.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.setBackgroundColor(getColor(R.color.bg))
    }
}
