# Egea Asistan

Fiat Egea (2019) içindeki Çin malı Android multimedya teybi için **Türkçe sesli asistan**.
Ekrandaki yüzen mikrofon butonuna ya da direksiyondaki sesli komut tuşuna basıyorsunuz, Türkçe konuşuyorsunuz;
uygulama işlemi yapıyor, sesli cevap veriyor ve kapanıp önceki ekrana (harita/müzik) dönüyor.

**APK (her zaman son sürüm, link değişmez):**
<https://github.com/fonzi3441-collab/egea-asistan/releases/latest/download/egea-asistan.apk>

---

## 1. Teybe kurulum

**A) Tarayıcıdan (en kolayı)**
1. Teybi telefonun hotspot'una bağlayın.
2. Teybin tarayıcısında yukarıdaki linki açın, `egea-asistan.apk` inecek.
3. İndirilenler'den APK'ya dokunun. "Bilinmeyen kaynak" uyarısı çıkarsa tarayıcıya/dosya yöneticisine izin verin.
4. **Yükle**'ye basın.

**B) USB bellekle**
1. Bilgisayarda linkten APK'yı indirin, USB belleğe kopyalayın.
2. Belleği teybe takın, teybin dosya yöneticisinden APK'yı açıp yükleyin.

Sonraki sürümler aynı imza anahtarıyla imzalandığı için **eskisinin üstüne kurulur** (verileriniz kalır).
Uygulama içinden de güncelleyebilirsiniz: *Egea Ayarlar → Güncellemeleri denetle ve kur*.

Kurulumdan sonra üç ikon görürsünüz:
- **Egea Asistan** (mikrofon): basınca doğrudan dinler.
- **Egea** (ekran): ana ekran — büyük saat, Radyo, TV, Asistan, hızlı kutular (navigasyon, müzik, telefon, eve git).
- **Egea Ayarlar** (dişli): ayarlar, "Hey Egea" öğretme, kurulum sihirbazı, tanı ekranı, güncelleme.

## 2. İlk kurulum

Egea Asistan ikonuna ilk basışta kurulum sihirbazı açılır ve izinleri **tek tek** ister:

| Adım | Ne için | Zorunlu mu |
|---|---|---|
| Mikrofon | Konuşmayı duymak | Evet |
| Kişiler | "Ahmet'i ara" | Arama için |
| Arama | Numarayı doğrudan aramak | Hayır (yoksa numara hazırlanır) |
| Telefon durumu | Bluetooth (HFP) telefon hesabını bulmak | Hayır |
| Bildirim gösterme (Android 13+) | Yüzen buton bildirimi | Hayır |
| Üstte gösterme | Yüzen mikrofon butonu; "Hey Egea" duyulunca dinleme ekranının arka plandan açılabilmesi (Android 10+) | Buton ve "Hey Egea" için evet |
| Bildirim erişimi | Çalan müziğe doğrudan komut | Hayır (yoksa medya tuşları) |
| Bilinmeyen kaynak | Uygulama içi güncelleme | Hayır |
| Pil optimizasyonu | Teyp uygulamayı kapatmasın | Hayır |
| Direksiyon tuşu (erişilebilirlik) | Tuş kodu ile tetikleme | Hayır |
| Varsayılan asistan | Sesli komut tuşu doğrudan açsın | Hayır |

Bu teyplerde bazı ayar sayfaları gizli olabiliyor. Sayfa açılmazsa sihirbaz **ne yapılacağını yazar** ve adım
**atlanabilir**. Bilgisayardan USB hata ayıklama ile de verilebilir:

```bash
adb shell pm grant com.egea.asistan android.permission.RECORD_AUDIO
```

```bash
adb shell appops set com.egea.asistan SYSTEM_ALERT_WINDOW allow
```

```bash
adb shell cmd notification allow_listener com.egea.asistan/com.egea.asistan.servis.MedyaDinleyiciServisi
```

```bash
adb shell appops set com.egea.asistan REQUEST_INSTALL_PACKAGES allow
```

```bash
adb shell settings put secure enabled_accessibility_services com.egea.asistan/com.egea.asistan.servis.TusErisilebilirlikServisi
```

Sonra **Egea Ayarlar**'da:
- Navigasyon uygulaması (Otomatik / Google Haritalar / Waze / Yandex Navigasyon)
- Ev ve iş adresi ("eve git", "işe git" için)
- Müzik uygulaması (Otomatik / Spotify / YouTube Music / YouTube)
- Rehber teybe eşitlenmiyorsa telefondan rehberi `.vcf` olarak dışa aktarıp **.vcf içe aktar** deyin, ya da **favori** ekleyin
  ("Annem", "Eşim" gibi).

## 3. "Hey Egea" ile eller serbest

"Hey Egea" ya da "Hey Ege" deyince asistan **"Dinliyorum"** der ve komutunu dinler; komutu söylersin, işlemi yapıp
önceki ekrana döner. Uyandırma kelimesi **internetsiz ve sadece teypte** tanınır (kayıt hiçbir yere gönderilmez).

**Bir kerelik öğretme (1 dakika):**
1. **Egea Ayarlar → "Hey Egea"yı öğret** (ya da Egea ana ekranındaki "Hey Egea" yazısına dokun).
2. Müzik kapalıyken 4 kayıt al: 2 kez "Hey Egea", 2 kez "Hey Ege". Her kayıtta "ŞİMDİ SÖYLE" yazınca normal sesle söyle.
3. **Kaydet ve aç**. **Dene (15 sn)** ile birkaç kez söyleyip algılandığını gör. Ekranda her sözün "eşiğe oranı"
   yazar: **1,00 ve altı algılanır**. "Hey Egea" dediğinde 0,7–0,9 civarı, başka sözlerde 1,2 üstü görmek idealdir.
4. Sık yanlış tetikleniyorsa duyarlılığı sola, zor algılıyorsa sağa çek (Seçici ↔ Dengeli ↔ Kolay tetiklenir).

**Nasıl çalışır:** Her konuşma parçası, aynı sözün iki kaydıyla karşılaştırılır (MFCC + hız katsayıları, ses
seviyesinden bağımsız normalizasyon, DTW). Eşik, senin iki kaydının birbirine benzerliğinden hesaplanır; yolda
gürültü artınca eşik kendiliğinden biraz gevşer. "Hey Egea"dan sonra hiç konuşmazsan (yanlış uyanma) asistan
"Anlayamadım" demeden sessizce kapanır ve müzik devam eder.

İpuçları:
- Kim öğrettiyse onun sesine göre çalışır; başka sürücü kullanacaksa kendi sesiyle yeniden öğretsin.
- Müzik çok yüksekken algılama zorlaşır; o zaman direksiyon tuşunu ya da ekrandaki mikrofonu kullan.
  Ayarlar'da mikrofon kaynağını "İletişim (yankı giderme)" yapmak bazı teyplerde müziği bastırır.
- Telefon görüşmesi sırasında dinleme kendiliğinden durur.
- Android 10 ve üstünde, "Hey Egea" duyulunca dinleme ekranının başka bir uygulamanın (harita, müzik) üstünde
  açılabilmesi için **"Üstte gösterme" izni** gerekir. İzin yoksa asistan bunu sesli söyler ve dinlemeye devam eder.
- Teyp yeniden açıldıktan sonra Android arka planda mikrofonu kısıtlayabilir; **Egea'yı bir kez açmak** yeterli.
  Durumu Tanı ekranındaki "HEY EGEA" bölümünde görürsün.
- 1.0.8 ve öncesinde öğrettiysen kayıtların otomatik dönüştürülür. O sürümlerde kaydın başındaki sessizlik yüzünden
  bazen bir kayıt bozuk kaydedilebiliyordu; böyle bir kayıt artık kullanılmaz ve Tanı ekranı "yeniden öğretin" der.
  En iyi sonuç için bir kez **yeniden öğretmen** önerilir.

## 4. Radyo ve TV (ücretsiz, uygulama reklam eklemez)

**Radyo:** Türkiye'deki ~700 internet radyosu ([Radio Browser](https://www.radio-browser.info), açık ve ücretsiz
veritabanı). Egea → **Radyo**: arama, tür çipleri (Pop, Türkçe, Rock, Arabesk, Türkü, Haber…), ★ favoriler.
Radyo arka planda çalar; bildirim, direksiyon medya tuşları ve "sonraki/önceki/durdur" komutları çalışır.
Asistan dinlerken radyo otomatik durur, bitince devam eder.

**TV:** Herkese açık, ücretsiz yayınlar (varsayılan liste: [iptv-org](https://github.com/iptv-org/iptv) Türkiye
listesi — TRT 1, TRT Haber, Kanal D, ATV, Star, NOW, TV8, Habertürk…). Egea → **TV** → kanala dokun, tam ekran açılır;
dokununca üst çubuk ve kanal listesi gelir, ◀ ▶ ile kanal değişir. İstersen Ayarlar'dan kendi M3U listeni girebilirsin.
Bazı kanallar "Yalnız Türkiye'de" (yurt dışında açılmaz) ya da "Kesintili yayın" (7/24 yayın yok) işaretlidir.

**Sürüş güvenliği:** Araç hareket halindeyken (GPS hızı > 10 km/s) TV görüntüsü gizlenir, **ses devam eder**;
araç durunca görüntü geri gelir. Konum izni gerekir; Ayarlar → Radyo ve TV'den kapatılabilir (önerilmez).

Uygulama kendi reklamını eklemez; kanalların/radyoların kendi yayınındaki reklamlar yayının parçasıdır.

## 5. Direksiyon tuşunu bağlama (Hiworld CanBox)

Direksiyon tuşları CanBox'tan teybin kendi CAN uygulamasına gelir; teyp bunu ya bir **tuş kodu** ya da bir
**sesli komut intent'i** olarak iletir. Hangisi olduğunu öğrenmek için:

1. **Egea Ayarlar → Tanı ekranı**'nı açın ve direksiyondaki sesli komut tuşuna basın.
2. Orta sütundaki **TUŞLAR** bölümüne bakın.

**Tuş kodu görünüyorsa** (ör. `KEYCODE_VOICE_ASSIST (231)`, `KEYCODE_SEARCH (84)`):
- Kurulum'daki **Direksiyon tuşu (erişilebilirlik)** adımını açın (Ayarlar → Erişilebilirlik → *Egea Asistan direksiyon tuşu*).
- **Egea Ayarlar → Direksiyon tuşu → Tuşa basarak öğren**'e basıp direksiyon tuşuna basın. Artık hangi uygulama açık olursa olsun tuş dinlemeyi başlatır.

**Tuş kodu görünmüyor, tuş teybin/Google'ın sesli komutunu açıyorsa:**
- Egea Asistan `VOICE_COMMAND`, `ASSIST`, `VOICE_SEARCH_HANDS_FREE` ve `SEARCH_LONG_PRESS` intent'lerini dinler.
  Tuşa basınca "Şununla aç" sorulursa **Egea Asistan → Her zaman** seçin.
- Ya da Kurulum'daki **Varsayılan asistan** adımından Egea Asistan'ı varsayılan dijital asistan yapın.
- Teybin ayarlarında "Direksiyon tuşu öğrenme / Steering wheel / Wheel key" menüsü varsa tuşa **Sesli komut/Voice** atayın.

Hiçbiri olmazsa yüzen mikrofon butonu her zaman çalışır.

## 6. Komutlar

Ekleri ve farklı söyleyişleri de anlar ("Park'a/Parka", "Ahmet'i/Ahmeti", "arar mısın", "götürür müsün"…).

**Telefon araması**
- "Ahmet'i ara", "annemi ara", "Ayşe Kaya'yı arar mısın", "Ahmet'e telefon et", "beni Ahmet'e bağla"
- "0532 123 45 67'yi ara", "sıfır beş yüz otuz iki yüz yirmi üç kırk beş altmış yediyi ara", "112'yi ara"
- "ara" → *"Kimi arayayım?"* diye sorar
- Birden çok eşleşme → *"Ahmet Yılmaz mı, Ahmet Kaya mı?"* → "Yılmaz", "Kaya olan", "ikincisi", "ilki" ya da "iptal"
- Cevap: *"Ahmet Yılmaz aranıyor"* (sadece numara tuşlanabiliyorsa *"Numara hazır, yeşil tuşa bas"*)

**Navigasyon** (rota doğrudan başlar)
- "Marmara Park'a gideceğim", "beni Kadıköy'e götür", "Esenyurt'a rota", "Ankara'ya git"
- "Taksim'e nasıl giderim", "Kadıköy'e yol tarifi", "navigasyon Kadıköy'e başlat", "en yakın benzinliğe git"
- "eve git", "beni eve götür", "eve dön", "işe git", "iş yerine götür", "ofise gidelim"
- Cevap: *"Marmara Park'a rota başlatıyorum"*
- Haritada sadece aramak için: "haritada Marmara Park'ı göster", "en yakın eczaneyi ara"

**Müzik kontrolü**
- "sonraki şarkı", "müziği değiştir", "şarkıyı geç", "başka şarkı aç", "önceki", "geri"
- "durdur", "müziği durdur", "müziği kapat" (komuttan sonra müzik kendiliğinden devam etmez)
- "devam et", "müziği aç"
- "sesi aç", "sesi biraz kıs", "sesi çok aç", "sesi on beş yap", "ses yirmi", "sesi yüzde elli yap",
  "sesi sonuna kadar aç", "sesi kapat", "sessize al"

**Şarkı açma** (seçili müzik uygulamasında aratıp çalar)
- "Tarkan Şımarık aç", "Sezen Aksu çal", "Tarkan'ın Şımarık şarkısını aç", "biraz Barış Manço dinleyelim"
- "Spotify'dan Sezen Aksu çal", "YouTube'dan Tarkan aç", "YouTube Müzik'te Tarkan çal"

**Radyo**
- "radyo aç", "radyoyu aç" (son çalan istasyon), "Kral FM'i aç", "Power FM aç", "radyoda Joy Türk aç",
  "Virgin Radio çal", "Radyo Fenomen dinle"
- "sonraki kanal", "radyoyu değiştir", "radyoyu kapat"

**Televizyon**
- "televizyonu aç", "TV'yi aç", "TRT 1 aç", "Show TV aç", "Kanal D'yi aç", "TRT Haber izle", "haber kanalını aç"
- "sonraki kanal", "televizyonu kapat"

**Uygulamada arama** (telefon araması değil)
- "YouTube'da Tarkan ara", "Spotify'da Sezen Aksu ara", "Google'da hava durumu ara"

**Uygulama açma** (yüklü uygulama adlarıyla bulanık eşleştirme)
- "YouTube'u aç", "Spotify aç", "haritayı aç", "ayarları aç", "telefonu aç", "radyo uygulamasını aç" (teybin FM radyosu)

**Diğer**
- "saat kaç", "bugün günlerden ne"
- "iptal", "vazgeç", "boş ver", "kapat", "tamam"
- Anlaşılmazsa *"Anlayamadım, tekrar söyler misin?"* deyip bir kez daha dinler.

## 7. Telefon araması nasıl yapılıyor?

Teybin Bluetooth uygulaması üreticiye göre değiştiği için bir **yöntem zinciri** var. Her denemenin sonucu
Tanı ekranındaki komut loguna yazılır:

1. `ACTION_CALL tel:` — çözen bir activity varsa
2. `TelecomManager.placeCall` — Bluetooth HFP telefon hesabı (PhoneAccount) varsa
3. `ACTION_DIAL` — numara hazırlanır, *"Numara hazır, yeşil tuşa bas"* denir
4. **Özel intent şablonu** — Egea Ayarlar'dan doldurulur

Tanı ekranında **tel: ÇÖZÜCÜLER**, **TELECOM PHONEACCOUNT** ve **BLUETOOTH / TELEFON UYGULAMALARI**
(paket adlarıyla) listelenir. Teybin Bluetooth uygulamasının arama intent'ini bulduğunuzda Egea Ayarlar →
*Özel arama intent'i* bölümüne yazın (tür: activity/broadcast/service, paket, sınıf, action, `tel:{numara}` gibi
data şablonu, extra anahtarı/değeri) ve **Özel intent'i test et** ile deneyin. Çalışınca *Telefon araması yöntemi*
bölümünden **Önce özel intent** ya da **Sadece özel intent** seçin.

## 8. Tanı ekranı

Tek ekranda üç sütun (fotoğrafını çekip gönderebilirsiniz; **Kopyala/Paylaş** düğmeleri de var):
- **Sistem:** ayarlarda yazan Android sürümü ile **gerçek API seviyesi**, ABI'ler, gerçek RAM (`/proc/meminfo`),
  depolama, ekran, SpeechRecognizer ve Türkçe tanıma desteği, TTS motoru ve **Türkçe ses durumu**, tüm izinler.
- **Uygulamalar ve arama:** yüklü navigasyon/müzik/Bluetooth uygulamaları (paket adlarıyla), `tel:` için CALL ve
  DIAL çözen bileşenler, Telecom PhoneAccount'ları, arama zinciri durumu, bu ekran açıkken basılan tuşların
  KeyEvent kodları.
- **Son 50 komut:** tanınan metin → niyet → yöntem → sonuç.
- Ayrıca: ekran yenileme hızı ve ölçülen FPS, "Hey Egea" motor durumu (eşik, son mesafe, algılama sayısı),
  radyo/TV listeleri ve çalan yayın.

Türkçe ses yoksa ekran uyarır: **Google Konuşma Hizmetleri** (`com.google.android.tts`) kurun ve
*Ayarlar → Dil → Metin okuma çıkışı*'ndan Türkçe ses verisini indirin.

## 9. Varsayımlar

İstenen şekilde soru sorulmadan aşağıdaki varsayımlarla ilerlendi:

- **Sürümler:** teypteki sürüm/RAM bilgisi şişirilmiş olabileceğinden `minSdk 24`, `targetSdk 34`; güncel AndroidX
  kütüphaneleri için `compileSdk 37`. Saf Kotlin, native kütüphane yok → her ABI'de çalışır.
  Derleme: AGP 9.5.2, Gradle 9.9.2, Kotlin 2.4.21, CI'da JDK 17 (Kasım 2026 itibarıyla güncel sürümler).
- **Ses tanıma** cihazdaki tanıma servisini kullanır (genelde Google uygulaması). `SpeechRecognizer` yoksa ya da hata
  verirse `RecognizerIntent` ekranı denenir; ikisi de yoksa Tanı ekranı bunu gösterir. Çoğu cihazda internet
  (telefon hotspot'u) gerekir.
- **Sesli cevap** uygulama seviyesinde tutulur; dinleme ekranı kapansa da cümle tamamlanır. Ses `STREAM_MUSIC`
  (medya sesi) üzerinden çıkar.
- **Ses odağı:** dinlerken `AUDIOFOCUS_GAIN_TRANSIENT` alınır (müzik durur), cevap bitince bırakılır (müzik devam eder).
  "durdur" komutunda bırakmadan önce kalıcı odak alınır; böylece müzik uygulaması kendiliğinden devam etmez.
- **Eylem sırası:** arama, şarkı açma ve sonraki/önceki gibi komutlarda önce konuşulur, sonra işlem yapılır (arama sesi
  cevabı kesmesin diye). Navigasyon ve uygulama açmada işlem hemen yapılır, cevap arka planda söylenir.
- **Kişi eşleştirme:** favoriler + Android rehberi + içe aktarılan .vcf birleştirilir. Kişinin birden çok numarası varsa
  sırasıyla birincil → cep → ilk numara aranır. Birden çok kişi eşleşirse en çok 4 seçenek sorulur; favori olan tek
  eşleşme öncelikli. Kesme işaretsiz isimlerde ekler için birden çok aday denenir ("Ahmeti" → Ahmet, "Ali" → Ali).
- **Ses seviyesi:** "sesi on beş yap" → söylenen sayı maksimum seviyeden küçük/eşitse doğrudan seviye, büyükse yüzde
  olarak yorumlanır; "yüzde elli" her zaman yüzdedir. "aç/kıs" maksimumun yaklaşık 1/8'i kadar değiştirir
  ("biraz" yarısı, "çok" iki katı). Bazı teyplerde ses kendi amfisinden yönetilir; o durumda etkisi sınırlı olabilir.
- **Kelime yorumları:** tek başına "dur", "sus", "kapat", "tamam" → iptal (müziği durdurmak için "durdur" ya da "müziği
  durdur/kapat"). Tek başına "geri" → önceki şarkı. "müzik aç / müziği aç" → çalmaya devam; "radyoyu aç" → radyo
  uygulaması. "X aç" uygulama adı değilse şarkı olarak çalınır.
- **Navigasyon:** Google Haritalar'da `google.navigation:q=…&mode=d` ile rota doğrudan başlar. Waze ve Yandex için
  koordinat Android Geocoder ile bulunur; bulunamazsa (ör. Google servisleri yoksa) adla arama yapılır. Yandex
  Navigasyon imzasız intent'lerde onay isteyebilir. Kesme işareti olmadan söylenen hedeflerde ek sezgisel atılır
  ("Parka" → Park, "Kadıköye" → Kadıköy, "Ankaraya" → Ankara); "rota Bursa" gibi anahtar kelime önce gelirse çıplak
  -a/-e atılmaz.
- **YouTube:** API anahtarı varsa ilk video doğrudan açılır, yoksa YouTube arama sonuçları açılır. Anahtarı Android
  uygulamasına kısıtlarsanız Egea Ayarlar'da gösterilen paket adı ve SHA-1 parmak izini kullanın.
- **Spotify / YouTube Music:** önce açık medya oturumu üzerinden `playFromSearch` denenir (ekran değişmez), olmazsa
  paket belirtilmiş `MEDIA_PLAY_FROM_SEARCH` intent'i gönderilir.
- **Arama zinciri:** "başarılı" kaydı intent'in hatasız gönderildiği anlamına gelir; teybin aramayı gerçekten başlatıp
  başlatmadığı uygulama tarafından bilinemez. Bu yüzden Tanı logu ve Ayarlar'daki yöntem seçimi var.
- **Yüzen buton** varsayılan olarak sağ kenarın ortasındadır; sürükleyince yeri hatırlanır. Teyp açıldığında
  (`BOOT_COMPLETED`, hızlı açılış) ve uygulama güncellendiğinde kendiliğinden başlar.
- **Güncelleme ve dağıtım:** her dala push'ta GitHub Actions APK derler, `latest` etiketli release silinip yeniden
  oluşturulur (son push edilen dalın APK'sı yayında olur). `versionCode` = Actions çalıştırma numarası; workflow dosyası
  yeniden adlandırılırsa numara sıfırlanır ve yeni APK eskisinin üstüne kurulmaz (kaldırıp kurmak gerekir).
- **İmza anahtarı** (`keystore/`) kişisel uygulama olduğu için **bilerek** repoda; debug ve release aynı anahtarla
  imzalanır.
- **Depo public** olduğu için indirme linki ve uygulama içi güncelleme giriş gerektirmez. Depo private yapılırsa ikisi de
  çalışmaz.
- Arayüz Türkçe, koyu tema, 1280x720 yatay ekran ve büyük butonlar için tasarlandı; diğer ekranlarda kaydırılabilir.
- **Akıcılık (FPS):** zayıf teyp işlemcileri için Jetpack Compose yerine klasik View + RecyclerView kullanıldı; listeler
  görünüm geri dönüşümüyle, logolar arka planda hedef boyuta küçültülüp önbelleğe alınarak yüklenir; geçişler GPU
  katmanında yapılır; ekran 60 Hz'den yüksek modu destekliyorsa en yüksek yenileme hızı istenir. Ölçülen FPS Tanı
  ekranında ve (isteğe bağlı) ana ekran köşesinde görünür.
- **"Hey Egea":** Picovoice/Vosk gibi native kütüphaneler "native kütüphane yok" kuralı nedeniyle kullanılmadı;
  saf Kotlin MFCC + enerji tabanlı konuşma bölütleme + DTW şablon eşleştirme yazıldı. Bu yüzden kullanıcının sesiyle bir
  kez öğretme gerekir ve konuşan kişiye bağımlıdır. Google'ın tanıyıcısını sürekli açık tutmak (internet tüketir, bip
  sesi çıkarır, müziği keser) tercih edilmedi. "Hey Egea" sonrası her zaman "Dinliyorum" denir; mikrofon/direksiyon tuşu
  ile açınca da denmesi istenirse Ayarlar'dan açılabilir.
- **Radyo/TV kaynakları:** Radyo listesi Radio Browser API'den (24 saat önbellek), TV listesi M3U adresinden (12 saat
  önbellek). Birçok yayın `http://` olduğu için açık metin trafiğine izin verildi (`usesCleartextTraffic=true`).
  Kanal adı söylenip bulunamazsa ("TRT 1 aç" gibi komutlar önce TV/radyo listesinde aranır) şarkı olarak aranır.
- **Sürüş kilidi** varsayılan olarak açık; GPS hızı 10 km/s üstünde iki ölçüm "hareketli", 5 km/s altında üç ölçüm
  "duruyor" sayılır. Konum izni verilmezse kilit çalışamaz (görüntü gizlenmez).
- "radyo aç" artık uygulamanın internet radyosunu açar; teybin kendi FM radyosu için "radyo uygulamasını aç" denir.

## 10. Geliştirme

```
core/   Saf Kotlin komut çözümleyici (Android'den bağımsız) + JVM testleri
app/    Android uygulaması
```

- `core` modülü: normalize (İ/ı, kesme işaretleri), Türkçe ek atma, yazıyla sayılar, niyet çıkarma
  (`ARA`, `NAVIGASYON`, `MEDYA`, `SARKI_CAL`, `UYGULAMA_AC`, `SAAT`, `IPTAL`), kişi ve uygulama bulanık eşleştirme,
  seçim cevabı çözme, .vcf okuyucu, M3U okuyucu, radyo/kanal adı eşleştirme ve "Hey Egea" sinyal işleme (MFCC, VAD, DTW).
  190'dan fazla JVM testi (150'den fazlası örnek cümle).
- "Hey Egea" ölçümü: `scripts/uyandirma_verisi.py` espeak-ng'nin Türkçe sesleriyle (8 konuşmacı, farklı hız/perde,
  araç içi uğultu) öğretme / "Hey Egea" / benzer söz ("Egea", "Ece", "Hey Emre"…) kayıtları üretir.
  `core/src/test/resources/uyandirma` küçük regresyon seti CI'da her seferinde çalışır (`UyandirmaRegresyonTest`).
  Büyük setle ayrıntılı rapor:

  ```bash
  python3 scripts/uyandirma_verisi.py --cikti /tmp/sesler20 --snr 20
  EGEA_SES_VERISI=/tmp/sesler20 ./gradlew :core:test --tests '*UyandirmaDegerlendirme*'   # /tmp/sesler20/rapor.txt
  ```

  Bu setle (264 benzer söz) önceki yöntem benzer sözlerin %76'sını kabul ediyordu; şimdiki yöntem varsayılan
  duyarlılıkta 20 dB gürültüde "Hey Egea"ların %100'ünü algılayıp benzer sözlerin %0,4'ünü, 10 dB (yol) gürültüde
  %79'unu algılayıp hiçbirini kabul etmiyor. Sentetik sesler gerçek insandan az değişkendir; gerçek oranlar farklı
  olur, ama yöntemler arasındaki fark büyüktür.
- `scripts/duman-testi.sh` + `.github/workflows/duman-testi.yml`: her push'ta debug APK'yı Android emülatöründe
  (1280x720, 160 dpi) kurar; ekranları açar, metin komutlarını dener, radyo/TV oynatmayı kontrol eder, çökme arar.
- CI: `.github/workflows/build.yml` — her dala push'ta ve elle tetiklemede: JDK 17, unit testler, `assembleRelease`,
  APK'yı `egea-asistan.apk` adıyla artifact olarak ve `latest` release'e yükler.

Yerelde (Android SDK kuruluysa):

```bash
./gradlew :core:test assembleRelease
```
