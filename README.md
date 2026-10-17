# MangaFox

Aplikasi Android pribadi untuk membaca manga di m.fanfox.net tanpa browser, dengan adblock bawaan.

## Unduh APK

Buka halaman **[Releases](../../releases/latest)**, unduh `MangaFox.apk`, lalu install di HP
(izinkan "Install unknown apps" kalau diminta). Setelah terpasang, update berikutnya
akan ditawarkan otomatis setiap kali aplikasi dibuka.

## Fitur

- Langsung membuka situs, layar penuh tanpa address bar
- Blokir iklan berbasis domain (daftar [StevenBlack](https://github.com/StevenBlack/hosts) + daftar bawaan),
  termasuk banner iklan judi yang disisipkan situs
- Pop-up, tab baru, dan redirect ke situs lain diblokir
- DNS-over-HTTPS (Cloudflare, cadangan Google) supaya tidak terkena blokir DNS operator
- Mode baca gulir tanpa pembatas, zoom dua jari hanya di halaman baca, tarik ke bawah untuk refresh
- Riwayat baca: chapter & halaman terakhir, lanjut otomatis, tanda "chapter baru"
- Tab Cari: filter multi-genre (sertakan/kecualikan), jenis, status, rating minimal, urutan
  (Populer/Rating/Update terbaru/Manga baru/A–Z), plus Ranking harian/mingguan/bulanan/total
- Rating, penulis, dan genre lengkap di halaman detail manga
- Bilah navigasi bawah: Beranda, Cari, Riwayat, Bookmark, Akun
- Tab Akun: status login, Masuk/Daftar, Log Out, cek update manual, bersihkan cache
- Membaca: penanda halaman (12 / 22), lanjut otomatis ke chapter berikutnya, muat ulang gambar yang gagal
- Mode gelap (Ikuti sistem / Terang / Gelap) di tab Akun
- Splash screen dan layar tidak mati selama membaca
- Cek update dari GitHub Releases setiap aplikasi dibuka

## Ganti alamat situs

Edit `start_url` di [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml) lalu commit.
