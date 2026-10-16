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
- Zoom dua jari hanya di halaman baca chapter, tarik ke bawah untuk refresh
- Splash screen dan layar tidak mati selama membaca
- Cek update dari GitHub Releases setiap aplikasi dibuka

## Ganti alamat situs

Edit `start_url` di [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml) lalu commit.
