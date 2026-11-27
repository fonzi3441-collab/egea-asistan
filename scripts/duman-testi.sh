#!/usr/bin/env bash
# Emülatör duman testi: debug APK'yı kurar, ekranları açar, metin komutlarını dener,
# radyo/TV oynatmayı kontrol eder, ekran metinlerini ve logları yazar; uygulama çökerse hata verir.
set -u
PKG=com.egea.asistan
APK=app/build/outputs/apk/debug/app-debug.apk
CIKTI=duman-ciktisi
mkdir -p "$CIKTI"

a() { timeout 30 adb "$@"; }

ekran() {
  local ad="$1"
  a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  a pull /sdcard/ui.xml "$CIKTI/$ad.xml" >/dev/null 2>&1
  timeout 20 adb exec-out screencap -p > "$CIKTI/$ad.png" 2>/dev/null
  echo "----------------- EKRAN: $ad -----------------"
  python3 scripts/ui_metin.py "$CIKTI/$ad.xml" | head -80
}

dokun() {
  local ad="$1" metin="$2"
  local xy
  xy=$(python3 scripts/ui_metin.py "$CIKTI/$ad.xml" "$metin")
  if [ -n "$xy" ]; then
    echo ">> dokun: \"$metin\" ($xy)"
    a shell input tap $xy
  else
    echo ">> bulunamadı: \"$metin\""
  fi
}

komut() {
  echo
  echo "================= KOMUT: $1"
  a shell "am start -n $PKG/.ui.ListenActivity --ez test_kurulum_tamam true --es test_metin \"$1\"" | grep -E "Error|Exception" || true
  sleep "${2:-7}"
  a shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -2
}

medya_durumu() {
  echo "-- medya oturumları:"
  a shell dumpsys media_session | grep -E "package=|state=PlaybackState|active=" | head -12
}

echo "== Cihaz"
a shell getprop ro.build.version.release
a shell getprop ro.build.version.sdk
echo "== Teyp ekranına benzet: 1280x720, 160 dpi"
a shell wm size 1280x720
a shell wm density 160

echo "== Kurulum"
timeout 120 adb install -r -g "$APK" || exit 1
a shell appops set $PKG SYSTEM_ALERT_WINDOW allow
a shell cmd notification allow_listener $PKG/$PKG.servis.MedyaDinleyiciServisi || true
a logcat -c

echo "== İlk açılış (kurulum sihirbazı beklenir)"
a shell am start -n $PKG/.ui.ListenActivity >/dev/null
sleep 5
ekran 01-ilk-acilis
dokun 01-ilk-acilis "Kurulumu bitir"
sleep 2
ekran 02-ozet

echo "== Ana ekran"
a shell am start -n $PKG/.ui.AnaEkranActivity >/dev/null
sleep 5
ekran 03-ana-ekran

echo "== Radyo sayfası"
dokun 03-ana-ekran "Radyo"
sleep 12
ekran 04-radyo
# ilk istasyon kartına dokun (kart metinleri arasında 'istasyon' sayısı satırından sonraki ilk ad)
XY=$(python3 - "$CIKTI/04-radyo.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
kok = ET.parse(sys.argv[1]).getroot()
adaylar = []
for d in kok.iter("node"):
    if d.get("clickable") == "true" and d.get("class", "").endswith("FrameLayout"):
        m = re.findall(r"\d+", d.get("bounds") or "")
        if len(m) == 4:
            x1, y1, x2, y2 = map(int, m)
            if y1 > 150 and (x2 - x1) > 100:
                adaylar.append(((x1 + x2) // 2, (y1 + y2) // 2))
print(f"{adaylar[0][0]} {adaylar[0][1]}" if adaylar else "")
PY
)
if [ -n "$XY" ]; then echo ">> ilk istasyon: $XY"; a shell input tap $XY; else echo ">> istasyon kartı bulunamadı"; fi
sleep 15
ekran 05-radyo-caliyor
medya_durumu

echo "== TV sayfası"
ekran 06-once
dokun 06-once "TV"
sleep 12
ekran 07-tv
dokun 07-tv "TRT 1"
sleep 20
ekran 08-tv-oynatici
medya_durumu
a shell input keyevent KEYCODE_BACK
sleep 3

komut "saat kaç"
komut "sesi on beş yap"
komut "Kral FM aç" 15
medya_durumu
komut "sonraki kanal" 10
komut "radyoyu kapat"
medya_durumu
komut "televizyon aç" 15
a shell dumpsys activity activities | grep -E "TvActivity" | head -2
a shell input keyevent KEYCODE_BACK
sleep 2
komut "Ahmet'i ara"
komut "Kadıköy'e git"
komut "YouTube'da Tarkan ara"
komut "Tarkan Şımarık aç"
komut "iptal"
komut "bla bla bla" 12

echo "== Hey Egea öğretme ekranı (ana sayfadan)"
a shell am start -n $PKG/.ui.AnaEkranActivity >/dev/null
sleep 3
dokun 06-once "Ana Sayfa"
sleep 2
ekran 09-ana-tekrar
dokun 09-ana-tekrar "Hey Egea"
sleep 4
ekran 10-ogret
a shell input keyevent KEYCODE_BACK
sleep 2

echo "== Tanı ekranı (Ayarlar → Tanı ekranı)"
a shell am start -n $PKG/.ui.SettingsActivity >/dev/null
sleep 4
ekran 11-ayarlar
dokun 11-ayarlar "Tanı ekranı"
sleep 8
ekran 12-tani

echo "== Servisler"
a shell dumpsys activity services $PKG | grep -E "ServiceRecord|isForeground|foregroundId|types=" | head -12

echo "== Uygulama logu"
a logcat -d -s EgeaAsistan:V | tail -150

echo "== Çökme kontrolü"
a logcat -d -b crash > "$CIKTI/crash.txt" 2>/dev/null || true
a logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $PKG|ANR in $PKG" -A 30 > "$CIKTI/fatal.txt" || true
cat "$CIKTI/crash.txt"
cat "$CIKTI/fatal.txt"
if grep -q "$PKG" "$CIKTI/fatal.txt" "$CIKTI/crash.txt" 2>/dev/null; then
  echo "!!! UYGULAMA ÇÖKTÜ"
  exit 1
fi
echo "== Çökme yok"
