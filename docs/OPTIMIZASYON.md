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
