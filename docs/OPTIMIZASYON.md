# WEEM optimizasyon ölçümleri (A10.1)

21 Ekim 2026, `main` `9a1d883` + A10.1 (dal `ccr-a451c797-hjk38y`). Telefonda ölçülmedi: pil ve bellek sayıları A10.4'ten sonra Bölüm 4'teki yolla alınır (S28: ara saha denemesi yok).

## 1. APK boyutu (ölçüldü, bulut oturumu, Google Maps anahtarsız)

| Derleme | Önce | Sonra | Ne değişti |
|---|---|---|---|
| `.dev` (debug, küçültmesiz) | 135.832.909 bayt (4 ABI) | **69.718.203 bayt** | yalnız `arm64-v8a` (S30) |
| release `com.kuryelog.app` | ≈123 MB (4 ABI, R8 yok; `MAP.md` 6.17) | **31.994.290 bayt** | `arm64-v8a` + R8 + kaynak küçültme (S30) |

Ayrıntı (sıkıştırılmış, MiB): önce `.dev` = dex 41,2 · x86_64 23,9 · x86 23,6 · arm64 22,8 · armeabi-v7a 15,5 · ML Kit modelleri 1,2 · diğer 1,2. arm64 içinde `libmaplibre.so` 12,2, `libmlkit_google_ocr_pipeline.so` 10,6. R8'li release'te dex 5,8 MiB (41,2'den). CI'daki `.dev` Google Maps anahtarlı derlendiği için bu sayılardan biraz büyüktür (`weem-dev-4`: 161.878.325 bayt, 4 ABI).

- `ci/check_identity.sh` artık APK'da yalnız `lib/arm64-v8a` olduğunu ve `com.kuryelog.app.WatchService` / `NotifService` sınıflarının dex'te adlarıyla durduğunu denetler (R8 adı değiştirirse Rawstats'tan kalan erişilebilirlik ve bildirim izinleri düşer).
- R8 yalnız release'te: `.dev` küçültmesiz kalır (Robolectric testleri ve gölge mod aynı kodla). Kod yansıma (`Class.forName`, `getDeclared*`) kullanmıyor; kütüphaneler kendi kurallarını getiriyor. Release'in telefonda ilk açılışı A10.4'te; sorun çıkarsa `app/build.gradle.kts` → `isMinifyEnabled = false`.
- Daha küçültmek için kalan büyük parça yerel kütüphaneler (MapLibre 12,2 + ML Kit OCR 10,6 MiB). İkisi de özellik (S4, R1 OCR); çıkarmak kullanıcı kararı, önerilmez.

## 2. Arka planda çalışan işler (envanter, koddan)

| Kaynak | Ne zaman açık | Tempo | Değerlendirme |
|---|---|---|---|
| `:core` `ShiftService` GPS | vardiya açık (ön plan, `location`) | 2 sn | gerekli (Rawstats ile aynı) |
| `:map` `NavLocation` GPS + barometre | harita önde, gün açık ya da yalnız-nav | 500 ms | gerekli; Android aynı sağlayıcının istekleri birleştirir (çip en hızlı tempoda bir kez çalışır) |
| `:map` `rememberMapPuck` GPS | harita sekmesi seçili | 1 sn | **düzeltildi:** uygulama arka plandayken de açıktı (Wolt/Bolt önde, vardiyada); artık yalnız Activity STARTED iken (`MapPuckLifecycleTest`) |
| `:core` `HomeWatch` | otomatik vardiya, evden çıkış izleme | 30 sn, çıkış anında 2 sn | Rawstats ile aynı; düşük güç kalitesi uzakta |
| `:scooter` `ScooterService` GPS + `PARTIAL_WAKE_LOCK` | yalnız scooter kaydı/canlı panel (Receiver) | 1 sn | Sürüş Paneli ile aynı (şartname); kayıt bitince bırakılır |
| `:link` `TheftRuntime` GPS + ivme | yalnız Receiver'da koruma devredeyken | 1 sn | gerekli (S10) |
| `:capture` `WatchService` ekran okuma | Wolt/Bolt önde | vardiyada 500 ms, boşta 2 sn; OCR en çok 350 ms'de bir | Rawstats ile aynı (`CaptureTiming`); OCR karesi değişmediyse tekrar çalışmaz (`pace.same`) |
| `:remote` `CaptureService` | paylaşım açık | kodlayıcı yalnız izleyen bağlıyken | izleyensiz 10 dk sonra kapanır |
| Saf zamanlayıcılar (`NavRuntime`, `RoadsRuntime`, `ReminderRuntime`, `RideLogRuntime` 1 sn; `DayRuntime` 5 sn; `Orders` vardiyada 500 ms, boşta 5 sn; `LinkHub` eşleşmeliyken 1 sn) | süreç ayaktayken | — | bellek içi iş (dosya/ağ yok; günlük 5 sn'de bir yazılır). Uyandırma kilidi tutmadıkları için ekran kapalı ve Doze'da CPU'yu uyandırmazlar; değiştirilmedi |

Ağ: hava 10 dk'da bir yalnız gün açık + harita önde; OSM karoları MapLibre disk önbelleğinden; ekran okuma ağsız (`CAPTURE.md` 7.3).

### 2.1 S38 (23 Ekim, A10.4k): telefonda görülen pil tüketimi ve yarım çizilen harita

Kullanıcı: "çok fazla batarya tüketiyor", "uygulama bir süre sonra kapanıp açılıyor", haritada çizgilerin bir kısmı yok. Koddan bulunan nedenler ve düzeltmeler (telefonda ölçülmedi):

| Neden | Etki | Düzeltme |
|---|---|---|
| Konum işareti her karede (60–120 Hz) kayarken harita sekmesi baştan oluşuyor, MapLibre'ye bütün çizgiler (bisiklet yolları dahil binlerce nokta) `String.format` ile yazılıp `setGeoJson` ile yeniden yükleniyordu | ana iş parçacığı ve GPU sürekli dolu, çok çöp (bellek), karolar bitmediği için çizgiler yarım | kaynak yalnız değişince; işaret ve kamera `MapLive` ile yalnız yüzeye; `StringBuilder` (`MAP.md` 6.18) |
| Kamera her durduğunda (takipte saniyede ~12) bütün yerler ve ışıklar yeniden seçiliyordu; ışık birleştirme karesel | ana iş parçacığında periyodik yük, öğrenilen ışıklar çoğaldıkça büyüyen | yalnız belirgin kayma/yakınlıkta; dizinli birleştirme |
| Haritadaki hava hapında sonsuz animasyon | harita açıkken (vardiyada ekran açık) ekran saniyede 60–120 kez yeniden çiziliyor, değişken yenileme hızı düşemiyor | hapta simge durağan |
| İşaret dururken de GPS titremesiyle her saniye ~0,85 sn kayıyordu | dururken bile kare kare çizim | dururken <2 m oynama yok; ~30 fps sınırı; değişiklik yokken kare yok |
| Navigasyon GPS'i harita arkadayken de 500 ms | ekran kapalıyken saniyede 2 uyanma | harita arkada ve rota yokken 1 sn |
| Harita sekmesi scooter karesi başına ve yüzey ölçümü her kesimde yeniden oluşuyordu | gereksiz yeniden oluşturma | scooter değerleri yalnız pencere açıkken, yüzey özetleri yalnız katman açıkken |

Kapanıp açılma için ek tanı: çökme kartı artık WEEM görünürken ya da vardiya servisi çalışırken bellek/kaynak yüzünden kapatılmayı da gösterir (`LowMemoryKill`, `ExcessiveResourceUsage`; `CrashReport.exitShown`). Kart çıkarsa metni kopyalanıp gönderilmeli.

### 2.2 S39 (24 Ekim, A10.4l): dokunma tepkisi ve pil

Ayrıntı ve kullanıcı kararı isteyen seçenekler: `docs/SADELESTIRME.md` 5. Uygulananlar: Harita sekmesi arkada uyur (yeniden kurulmaz; gizliyken konum, pusula, işaret animasyonu, ekran açık tutma ve sık akışlar durur); baseline profile (`app/src/main/baseline-prof.txt`: WEEM kodu + MapLibre Java katmanı, joker kurallar ~24.500 metoda açılır; profileinstaller Play dışı kurulumda yazar, ART boştayken derler); ekran okumada içerik değişikliği en çok 250 ms'de bir (`CaptureTiming.CONTENT_READ_MS`); gömülü altlık (karo ağı yok); MapLibre ≤60 kare/sn; Google'da işaret farkı ve 100 sınırı; graf ve SRTM bellek eşlemeli. Release APK (bulut, anahtarsız, gömülü harita ve karosuz) 32.570.447 bayt (+0,3 MB: glifler, stiller, profil); karolar CI'da ölçülür (`tiles.json`).

## 3. Bellek (koddan)

- Ekran görüntüleri: donanım tamponu kopyalanır kopyalanmaz kapatılır, kırpma/ölçek ara görüntüleri ve OCR girdisi her yolda `recycle` edilir (`WatchService`).
- Uzaktan: sanal ekran izleyen yokken yüzeysiz; kodlayıcı ve `VirtualDisplay` bitişte bırakılır.
- Ölçülmedi: yol grafiği (`RoadGraph`) ve MapLibre'nin bellekteki boyutu (harita verisi bu ortamda yok) → Bölüm 4.

## 4. Telefonda ölçüm (A10.4 sonrası, kullanıcı ya da bilgisayara bağlı ajan)

USB hata ayıklama ile, bir vardiya önce ve sonra:

1. `adb shell dumpsys batterystats --reset` (vardiya başı) → vardiya sonu `adb shell dumpsys batterystats com.kuryelog.app > pil.txt` (GPS süresi, uyandırma kilidi, CPU).
2. `adb shell dumpsys meminfo com.kuryelog.app` (harita açık, vardiya ortası): öneri: toplam PSS < 400 MB.
3. `adb shell dumpsys location | grep -A3 kuryelog` (Wolt önde iken): yalnız `ShiftService` ve `NavLocation` istekleri görünmeli.
4. Bilgisayarsız: Ayarlar → Pil → WEEM yüzdesi ve "arka planda" süresi, aynı vardiyada Rawstats + YolGo toplamıyla karşılaştırılır.

Sonuçlar bu bölüme tarihle yazılır; sorun A10.5'te düzeltilir.

## 5. A10.7 öncesi optimizasyon ve iyileştirme listesi (S42, 26 Ekim; koddan ve CI kayıtlarından, telefonda ölçülmedi)

Öncelik: **Y** yüksek · **O** orta · **D** düşük. "Karar" = davranış değişir, kullanıcı seçer; diğerleri davranışı değiştirmez (A10.7'de ya da öncesinde yapılabilir).

| # | Öncelik | Bulgu (yer) | Etki | Öneri | Karar |
|---|---|---|---|---|---|
| 1 | Y | Dururken harita pusulayla döner (`PuckHeading.current` → `compass ?: fallback`); `CompassSensor.onAccuracyChanged` boş, kalibrasyon durumu yok sayılır. Scooter gidonunda motor/mıknatıs pusulayı saptırır | "Harita ters yöne bakıyor" geri bildiriminin olası kökü (S42'de elle döndürme eklendi ama kök durur) | Doğruluk `LOW`/`UNRELIABLE` iken pusulayı kullanma (son GPS yönü), ya da ayar: "Dururken yön: pusula / son gidiş yönü" | evet (S38'de pusula istenmişti) |
| 2 | Y | Kamera, takip, yalnız harita, takibe dönüş kuralları `MapScreen.kt`'de (783 satır Compose); S42 değişiklikleri yalnız `MapTabTest` (Robolectric) ile sınanıyor | Hata riski, test edilemeyen karar kodu ("karar logic'te" kuralı) | `logic/map/ui` `CameraFollow` durum makinesi + birim testleri; `MapScreen` yalnız bağlar | hayır |
| 3 | O | Haritada rota ilerlemesi, `nav`, `roadState` ekranın en üstünde okunuyor: her GPS fix'inde (1–2 Hz) bütün harita ekranı yeniden oluşur (S38 yalnız işaret ve kamerayı ayırdı) | Ana iş parçacığı ve pil; dokunma gecikmesi | Göstergeleri kendi durumunu okuyan alt bileşenlere böl; Layout Inspector'da yeniden oluşturma sayısıyla ölç | hayır |
| 4 | O | 7 adet 1 sn'lik zamanlayıcı (`NavRuntime`, `RoadsRuntime`, `ReminderRuntime`, `RideLogRuntime`, `LinkHub`, `TheftRuntime`, `AppDatasetRuntime`) süreç boyunca; vardiyada scooter uyandırma kilidi (P4) açıkken ekran kapalıyken de her saniye çalışır (Bölüm 2'deki "Doze'da uyandırmaz" yalnız kilit yokken) | Vardiya boyunca gereksiz CPU uyanması | Tek ortak saat; iş yokken (öğrenme kapalı, hatırlatıcı yok, eşleşme yok) dur. `dumpsys batterystats` ile önce/sonra | hayır |
| 5 | O | Yer/ışık seçimi (`poiMarkers`) kompozisyon içinde, ana iş parçacığında (binlerce yer; kamera belirgin kayınca) | Kaydırma sonrası takılma olasılığı | `produceState` + `Dispatchers.Default`; ölç | hayır · **yapıldı (S43)** |
| 6 | O | Pil seçenekleri P1–P5 (`SADELESTIRME.md` 5) karar bekliyor; en büyük kazanç P1 (rota yokken ekran kapanabilsin) ve P2 (Y13 sensörleri, saniyede ~200 olay) | Pil | SORU-S39-2 | evet |
| 7 | O | `WeemApplication.onCreate` her süreç başlangıcında (yalnız erişilebilirlik servisi için açılsa da) harita, hava, batarya, 2 telefon çalışma zamanlarını kurar | Soğuk açılış ve bellek | Önce ölç (`adb shell am start -W com.kuryelog.app/.MainActivity`, Perfetto); gerekmeyenleri ilk kullanıma ertele | hayır |
| 8 | O | 9 ayrı dosya işi `Thread {}` (`EncryptedBackups` 3, `RecordExport` 2, `Scooter` 2, `DayReportHook`, `AppDatasetRuntime`); ortak sıra ve hata yakalayıcı yok, çakışma `Store.importing` gibi bayraklara dağınık | Nadir yarış ve sessiz hata | Tek sıralı IO yürütücüsü (`Store.failure` ile) | hayır |
| 9 | D | `EconomicsObserver.scooterEnergy` içinde `runBlocking` (ekran okuma/sipariş iş parçacığı; 2 telefonda uzaktan enerji) | Kare kaçırma riski (PhoneBridge bekleme 0 dediği için düşük) | Doğrula; zaman aşımı ya da son değerin önbelleği | hayır |
| 10 | O | Ters yön önerisi "onay için Ses+" der; ses tuşu ayarı kapalıyken ya da tuş başka uygulamaya giderken dokunarak onay yolu yok | Kaçan onaylar | Öneri şeridine "Onayla" düğmesi (şeritte "Geri al" altyapısı var) | evet (küçük) |
| 11 | Y | PR CI ~24 dk (#29: logic testleri 8 dk, Android test + lint + `assembleDebug` + `assembleRelease` 15 dk; #30 10 dk); `gradle.properties`'te paralel ve derleme önbelleği kapalı | Actions dakikası (ayda 2.000, `kurye_nav` ile ortak) | (a) `org.gradle.parallel=true` + `org.gradle.caching=true` (logic'in 9 modülü paralel, değişmeyen modülün testi önbellekten); (b) PR'da `assembleRelease` yok (R8 derlemesini ve kimlik denetimini `apk.yml` zaten yapar); (c) test görevlerinde `maxParallelForks`. Tahmin: PR başına 8–12 dk az | evet (CI kuralı) |
| 12 | O | Yerelde `tools/test.sh logic` ~6 dk | Ajan beklemesi ve usage | 11 (a)/(c) yerelde de kısaltır | hayır |
| 13 | O | SORU-S39-1 madde 4–11 (uygulama içi harita indirme, Y13/Y14, uzaktan kullanım, hırsızlık koruması, teklif veri seti, ekonomi v2, modül birleştirme, şartname özeti) | Kod ve pil | Kullanıcı yanıtı | evet |
| 14 | O | `:app` Android katmanı 13.106 satır, `logic/app` 563: ekranlarda karar kodu kalmış olabilir (ör. `MapScreen` kamera, `RecordsScreen` akışları) | Test edilemeyen davranış | A10.7'de "Android ince" taraması (Compose dosyalarında `if`/`when` yoğunluğu) | hayır |
| 15 | D | `docs/sartname/` ~10 bin satır; işi biten bölümler | Ajan usage'ı | Modül başına kısa özet (madde 13'teki 11) | evet |
| 16 | — | Hızlı güvenlik taraması: dışa açık bileşenler yalnız sistem izinli servisler (erişilebilirlik, bildirim dinleyici) ve başlatıcı Activity; `allowBackup=false` | — | A10.7 odakları: bağlantı/intent girişi (`LinkIntake`), 2 telefon şifrelemesi (Noise), uzaktan kullanım, SMS (hırsızlık), FileProvider yolları, dışa aktarmada gizleme (`Redactor`) | — |

S43 (28 Ekim) ön denetim (`/code-review high`, Android modülleri) düzeltmeleri: `LinkService` yeniden başlatılınca durdurulamıyordu; `StreamNet` kabulde kopan eş uygulamayı çökertiyordu; `ScooterService` canlı panelde ön plan reddi çöküyordu; Wi-Fi işaret (UDP) yuvası düşünce yeniden açılmıyordu; hırsızlık PIN'i tuz okunamazsa atlanıyordu; M23 bildirimi "koptu" kapanınca siliniyordu; ekran görüntüsü hatası korumasızdı; rozet penceresi reddedilince her 500 ms deneniyordu (artık 30 sn); siren kuralı `logic`'e (`TheftGuard.sirenOn` + test). Açık: `StreamNet.listen` bağlanamazsa `HostMachine`'e haber vermiyor (düşük olasılık).

S42'de yapılanlar (bu listeden ayrı): gölge mod, eski kayıt içe aktarma ve OSM raster silindi (~1.250 satır); simge üreticisi yalnız kullanılan simgeleri yazar.

## 6. Google Maps anahtarı (S42)

- Google Maps Platform'da yeni anahtar her durumda projede **faturalandırma hesabı** (kart) ister; bunun meşru bir yolu yok. Android'de harita yüklemeleri (Maps SDK for Android, "Mobile Native Dynamic Maps") ücretsiz ve sınırsızdır: kart girilse de harita için ücret çıkmaz; ücret Places araması gibi başka SKU'larda (ücretsiz eşik var). Kaynak: [Maps SDK for Android Usage and Billing](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing).
- **WEEM Google'sız tam çalışır:** harita gömülü vektör altlık (ağsız), adres araması OSM Nominatim (isteğe bağlı OpenRouteService anahtarı ücretsiz kayıtla), rota telefonda.
- **Eski anahtar:** aynı Google hesabıyla `console.cloud.google.com` → eski proje → APIs & Services → Credentials → anahtarı kopyala; Billing'de proje hâlâ bir faturalandırma hesabına bağlıysa çalışır. GitHub → Weem → Settings → Secrets and variables → Actions → `GOOGLE_MAPS_API_KEY` → APK'yı yeniden derlet (`apk.yml`). Kısıtlama: Android uygulamaları, paket `com.kuryelog.app` + SHA-1 (Harita ayarları → Google Maps'te kopyalanır).
