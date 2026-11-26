#!/usr/bin/env bash
# Emülatör duman testi: debug APK'yı kurar, ekranları açar, metin komutlarını dener,
# ekran metinlerini ve logları yazar; uygulama çökerse hata verir.
set -u
PKG=com.egea.asistan
APK=app/build/outputs/apk/debug/app-debug.apk
CIKTI=duman-ciktisi
mkdir -p "$CIKTI"

ekran() {
  local ad="$1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$CIKTI/$ad.xml" >/dev/null 2>&1
  adb exec-out screencap -p > "$CIKTI/$ad.png" 2>/dev/null
  echo "----------------- EKRAN: $ad -----------------"
  python3 scripts/ui_metin.py "$CIKTI/$ad.xml"
}

dokun() {
  local ad="$1" metin="$2"
  local xy
  xy=$(python3 scripts/ui_metin.py "$CIKTI/$ad.xml" "$metin")
  if [ -n "$xy" ]; then
    echo ">> dokun: \"$metin\" ($xy)"
    adb shell input tap $xy
  else
    echo ">> bulunamadı: \"$metin\""
  fi
}

komut() {
  echo
  echo "================= KOMUT: $1"
  adb shell "am start -W -n $PKG/.ui.ListenActivity --ez test_kurulum_tamam true --es test_metin \"$1\"" | grep -E "Status|Activity|Error" || true
  sleep "${2:-7}"
  adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -2
}

echo "== Cihaz"
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density

echo "== Teyp ekranına benzet: 1280x720, 160 dpi"
adb shell wm size 1280x720
adb shell wm density 160

echo "== Kurulum"
adb install -r -g "$APK" || exit 1
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener $PKG/$PKG.servis.MedyaDinleyiciServisi || true
adb logcat -c

echo "== İlk açılış (kurulum sihirbazı beklenir)"
adb shell am start -W -n $PKG/.ui.ListenActivity | grep -E "Status|Activity" || true
sleep 4
ekran 01-ilk-acilis
dokun 01-ilk-acilis "Atla"
sleep 2
ekran 02-sihirbaz-adim2
dokun 02-sihirbaz-adim2 "Kurulumu bitir"
sleep 2
ekran 03-ozet

echo "== Ayarlar"
adb shell am start -W -n $PKG/.ui.SettingsActivity | grep -E "Status|Activity" || true
sleep 4
ekran 04-ayarlar

komut "saat kaç"
komut "sesi on beş yap"
komut "sonraki şarkı"
komut "durdur"
komut "Ahmet'i ara"
komut "0532 123 45 67'yi ara" 9
adb shell input keyevent KEYCODE_ENDCALL
sleep 2
komut "Kadıköy'e git"
komut "eve git"
komut "YouTube'da Tarkan ara"
komut "Tarkan Şımarık aç"
komut "haritayı aç"
komut "ayarları aç"
komut "iptal"
komut "bla bla bla" 12

echo "== Sesli komut intent'i (VOICE_COMMAND)"
adb shell am start -W -a android.intent.action.VOICE_COMMAND | grep -E "Status|Activity|Error" || true
sleep 6

echo "== Tanı ekranı (Ayarlar → Tanı ekranı)"
adb shell am start -W -n $PKG/.ui.SettingsActivity | grep -E "Status|Activity" || true
sleep 3
ekran 05-ayarlar-tekrar
dokun 05-ayarlar-tekrar "Tanı ekranı"
sleep 6
adb shell input keyevent KEYCODE_VOLUME_UP
sleep 2
ekran 06-tani

echo "== Yüzen buton servisi"
adb shell dumpsys activity services $PKG | grep -E "ServiceRecord|isForeground|foregroundId" | head -10

echo "== Uygulama logu"
adb logcat -d -s EgeaAsistan:V | tail -120

echo "== Çökme kontrolü"
adb logcat -d -b crash > "$CIKTI/crash.txt" 2>/dev/null || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $PKG|ANR in $PKG" -A 25 > "$CIKTI/fatal.txt" || true
cat "$CIKTI/crash.txt"
cat "$CIKTI/fatal.txt"
if grep -q "$PKG" "$CIKTI/fatal.txt" "$CIKTI/crash.txt" 2>/dev/null; then
  echo "!!! UYGULAMA ÇÖKTÜ"
  exit 1
fi
echo "== Çökme yok"
