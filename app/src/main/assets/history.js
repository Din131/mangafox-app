// Riwayat baca MangaFox.
// - Halaman baca (roll_manga): catat manga + chapter + halaman terakhir, lanjutkan dari halaman itu.
// - Halaman detail manga: simpan judul & sampul, tandai chapter yang sudah dibaca, tombol "Lanjut baca".
// Data disimpan di aplikasi lewat window.MFHistory (get/set JSON).
(function () {
  if (window.__mfHist || !window.MFHistory) return;
  window.__mfHist = true;

  var MAX_MANGA = 200;
  var path = location.pathname;

  function load() {
    try { return JSON.parse(window.MFHistory.get() || '{}') || {}; } catch (e) { return {}; }
  }
  function save(h) {
    // Batasi jumlah manga yang diingat (buang yang paling lama)
    var keys = Object.keys(h).filter(function (k) { return k !== '__meta'; });
    if (keys.length > MAX_MANGA) {
      keys.sort(function (a, b) { return (h[a].at || 0) - (h[b].at || 0); });
      for (var i = 0; i < keys.length - MAX_MANGA; i++) delete h[keys[i]];
    }
    // Judul & sampul manga yang pernah dibuka (juga yang belum dibaca), maksimal 300
    if (h.__meta) {
      var mk = Object.keys(h.__meta);
      if (mk.length > 300) {
        mk.sort(function (a, b) { return (h.__meta[a].at || 0) - (h.__meta[b].at || 0); });
        for (var j = 0; j < mk.length - 300; j++) delete h.__meta[mk[j]];
      }
    }
    try { window.MFHistory.set(JSON.stringify(h)); } catch (e) {}
  }
  function abs(u) {
    if (!u) return '';
    if (u.indexOf('//') === 0) return 'https:' + u;
    if (u.charAt(0) === '/') return 'https://m.fanfox.net' + u;
    return u.trim();
  }
  function chNum(u) {
    var m = /\/c([\d.]+)(\/|$)/.exec(u || '');
    return m ? m[1] : null;
  }
  function toast(text) {
    var el = document.createElement('div');
    el.textContent = text;
    el.style.cssText = 'position:fixed;left:50%;bottom:64px;transform:translateX(-50%);z-index:2147483647;' +
      'background:rgba(0,0,0,.75);color:#fff;font:600 14px sans-serif;padding:9px 16px;border-radius:20px;' +
      'pointer-events:none;transition:opacity .4s;white-space:nowrap';
    document.body.appendChild(el);
    setTimeout(function () { el.style.opacity = '0'; }, 2500);
    setTimeout(function () { el.remove(); }, 3000);
  }

  // ---------- Halaman baca ----------
  var rm = /^\/roll_manga\/([^\/]+)\/(.*?)\/?(?:\d+\.html)?$/.exec(path);
  if (rm) {
    var slug = rm[1], ch = chNum(path);
    if (!ch) return;
    var h = load();
    var m = h[slug] || (h[slug] = { ch: {} });
    m.ch = m.ch || {};
    var meta = (h.__meta || {})[slug] || {};
    m.t = meta.t || m.t || (document.title || '').trim();
    if (meta.c && (!m.cAt || (meta.at || 0) > m.cAt)) { m.c = meta.c; m.cAt = meta.at || Date.now(); }
    var titleEl = document.querySelector('.mangaread-title');
    var label = titleEl ? titleEl.textContent.replace(/\s+/g, ' ').trim() : ('Ch ' + ch);
    var prev = m.ch[ch];
    m.ch[ch] = { p: prev ? prev.p : 0, n: prev ? prev.n : 0, done: prev ? prev.done : false, at: Date.now() };
    m.last = ch;
    m.lastLabel = label;
    m.lastUrl = 'https://m.fanfox.net/roll_manga/' + slug + '/' + rm[2].replace(/\/?$/, '/') + '1.html';
    m.at = Date.now();
    save(h);

    var pages = function () { return document.querySelectorAll('img.reader-page'); };
    var current = function () {
      var imgs = pages();
      for (var i = 0; i < imgs.length; i++) {
        if (imgs[i].getBoundingClientRect().bottom > 60) return i;
      }
      return imgs.length - 1;
    };
    var saveTimer = null;
    var record = function () {
      var imgs = pages();
      if (!imgs.length) return;
      var hh = load(), mm = hh[slug];
      if (!mm || !mm.ch || !mm.ch[ch]) return;
      var e = mm.ch[ch], idx = current();
      var docH = document.documentElement.scrollHeight;
      // Dianggap selesai kalau sudah di ujung bawah halaman yang memang panjang
      var atEnd = docH > window.innerHeight * 2 && window.innerHeight + window.scrollY >= docH - 400;
      e.p = idx; e.n = imgs.length; e.at = Date.now();
      if (atEnd || idx >= imgs.length - 1) e.done = true;
      mm.at = Date.now();
      save(hh);
    };
    window.addEventListener('scroll', function () {
      clearTimeout(saveTimer);
      saveTimer = setTimeout(record, 700);
    }, { passive: true });
    document.addEventListener('visibilitychange', function () { if (document.hidden) record(); });

    // Lanjutkan dari halaman terakhir kalau chapter ini belum selesai dibaca
    if (prev && !prev.done && prev.p > 0) {
      var jump = function () {
        var imgs = pages();
        if (imgs.length <= prev.p) return false;
        imgs[prev.p].scrollIntoView();
        window.scrollBy(0, -48);
        return true;
      };
      setTimeout(function () {
        if (window.scrollY > 200 || !jump()) return;
        toast('Lanjut dari halaman ' + (prev.p + 1));
        var y = window.scrollY;
        // Ulangi sekali setelah gambar di sekitarnya selesai dimuat (posisi bisa bergeser)
        setTimeout(function () { if (Math.abs(window.scrollY - y) < 30) jump(); }, 1500);
      }, 600);
    }
    return;
  }

  // ---------- Halaman detail manga ----------
  var dm = /^\/manga\/([^\/]+)\/?$/.exec(path);
  if (dm && document.querySelector('.manga-detail')) {
    var s = dm[1];
    var hist = load();
    // Simpan judul & sampul (link sampul dari situs punya masa berlaku, jadi selalu diperbarui)
    var titleP = document.querySelector('.manga-detail-top .title');
    var cover = document.querySelector('img.detail-cover');
    var t = titleP ? titleP.textContent.trim() : '', c = cover ? abs(cover.getAttribute('src')) : '';
    hist.__meta = hist.__meta || {};
    hist.__meta[s] = { t: t, c: c, at: Date.now() };
    var info = hist[s];
    if (info) { if (t) info.t = t; if (c) { info.c = c; info.cAt = Date.now(); } }
    save(hist);
    if (!info || !info.ch) return; // belum pernah dibaca

    // Tandai chapter yang sudah dibaca
    var style = document.createElement('style');
    style.textContent =
      '.mf-read{opacity:.45}' +
      '.mf-read::before{content:"\\2713  ";color:#75b520;font-weight:bold}' +
      '.mf-last{opacity:1!important;background:#eef7e2!important}' +
      '.mf-last::after{content:"  \\2190 terakhir dibaca";color:#5a8f16;font-size:12px;font-weight:600}' +
      '.mf-continue{display:block;margin:10px 0 0;padding:10px 12px;background:#75b520;color:#fff!important;' +
      'border-radius:8px;text-align:center;font-weight:600;text-decoration:none}';
    document.head.appendChild(style);
    var links = document.querySelectorAll('.chlist a[href]');
    for (var i = 0; i < links.length; i++) {
      var n = chNum(links[i].getAttribute('href'));
      if (!n || !info.ch[n]) continue;
      links[i].classList.add('mf-read');
      if (n === info.last) {
        links[i].classList.add('mf-last');
        // Buka grup volume yang berisi chapter terakhir dibaca
        var dd = links[i].closest('.chlist');
        if (dd) dd.style.display = 'block';
      }
    }
    // Tombol "Lanjut baca"
    if (info.lastUrl) {
      var e2 = info.ch[info.last] || {};
      var a = document.createElement('a');
      a.className = 'mf-continue';
      a.href = info.lastUrl;
      a.textContent = 'Lanjut baca: ' + (info.lastLabel || ('Ch ' + info.last)) +
        (e2.done ? ' (selesai)' : (e2.p ? ' • hal. ' + (e2.p + 1) : ''));
      var host = document.querySelector('.manga-detail-top .detail-info') || document.querySelector('.manga-detail-top');
      if (host) host.appendChild(a);
    }
  }
})();
