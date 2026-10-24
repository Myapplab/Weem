# WEEM sadeleştirme raporu (S39, 24 Ekim 2026; A10.4l + A10.6)

Kullanıcı isteği (S39): "Uygulamayı en çok ne kadar sadeleştirebilirsin; ciddiye al, farklı yöntemler kullan, web'e de bak; yap, sonra raporla; pil için de aynısını yap."
Bu belge: (1) ölçüm yöntemleri ve sonuçlar, (2) bu oturumda yapılan kod sadeleştirmesi, (3) **kullanıcı kararı isteyen** özellik düzeyi sadeleştirme seçenekleri,
(4) pil araştırması ve uygulananlar. Telefonda ölçülmedi; sayılar koddan ve derlemeden.

## 1. Yöntemler

| Yöntem | Ne bulur | Araç |
|---|---|---|
| Satır sayımı (modül, özellik paketi) | Nerede ne kadar kod var | betik (yorum ve boş satır hariç) |
| **R8 erişilemeyen kod raporu** | Android giriş noktalarından (Activity, servisler, alıcılar) hiç ulaşılmayan sınıf ve üyeler | release derlemesi `-dontoptimize -dontobfuscate -printusage` (geçici, repoya girmedi) |
| Kaynak başvuru dizini | R8'in "kullanılmıyor" dediğinin kaynakta gerçekten kullanılmadığı mı, yalnız testte mi | betik: her ad için `src/main` ve `src/test` sayımı |
| **PMD CPD** (kopya-yapıştır) | Aynı kod blokları (≥120 ve ≥70 sözcük birimi) | `net.sourceforge.pmd:pmd-cli/pmd-kotlin 7.7.0` (Maven Central) |
| Şartnameyle karşılaştırma | Yazılmış ama **hiç bağlanmamış** davranışlar ("ölü" görünen ama aslında eksik bağlantı) | YolGo `nav_controller.dart` @ `870ab6e` |
| Web araştırması | Compose tepki süresi, baseline profile, pil (sensör toplu teslimi, erişilebilirlik olayları, harita kare hızı) | Android Developers belgeleri, MapLibre Android belgeleri, Protomaps belgeleri |

## 2. Ölçüm sonuçları

**Kod tabanı (yorum/boş satır hariç):** ana kod **55.875 satır** (509 dosya), test **35.530 satır** (460 dosya). En büyükler: `logic/map` 14.063, `:app` 11.496, `logic/orders` 5.440, `:map` 3.620, `logic/link` 3.374.

**Tekrar (CPD):** ≥120 birimde 6 blok, ≥70 birimde 19 blok; toplam fazla satır **~156 (%0,3)**. Kod tekrarı sorun değil.

**Ölü kod (R8 + dizin):** 4.591 "kullanılmayan üye"nin büyük kısmı üretilmiş kod (data sınıfı `copy`/`componentN`, `DefaultImpls`, sabit alanların getter'ı). Ayıklanınca:
- **Hiçbir yerde kullanılmayan:** 35 üye + 27 simge (malzeme simgesi yolları).
- **Yalnız testte kullanılan:** 63 üye — çoğu eski uygulamadan çevrilmiş davranış testlerinin (şartname) yardımcıları; kalır.
- Sınıf düzeyinde: modül işaretçileri (`CoreModule` …, mimari koruma testleri kullanır), sahte depolar (`*.Memory`, testler), CI aracı (`MapBuild`) — kalır.

**Sonuç:** kod düzeyinde sadeleştirme payı **%1'in altında**; kod zaten yalın. **Asıl sadeleştirme payı özellik düzeyinde** (Bölüm 4) ve bu kullanıcı kararıdır.

**Ölü görünen ama aslında eksik bağlantı (hata) — bulundu ve bağlandı:**
1. **Pasif oturum** (YolGo; MAP.md 6.2): vardiya kapalıyken uygulama ≥5 dk arka planda kaldıysa dönüşte eski rota silinir. Saf kural A6.6'dan beri vardı, hiç çağrılmıyordu.
2. **Sipariş değişimi** (YolGo `NavController.build`; MAP.md 7.3): günün açık siparişi değişince rota kendiliğinden restorana (çıkılmadıysa) ya da müşteriye; hedefsizse silinir; açık durak siparişe bağlanır. `NavRuntime` sipariş numarasını hep boş geçiyordu.
3. **Google yüzeyinde yer işaretleri** (MAP.md 6.4): YolGo kuralı (merkezin 900 m içi, en çok 100) uygulanmıyordu; MapLibre seçimi (ekran, 180) veriliyordu.

## 3. Bu oturumda yapılan kod sadeleştirmesi (davranış aynı)

| Ne | Sonuç |
|---|---|
| 27 kullanılmayan simge, `CountBadge`, `cardEdge`, `toBanner`, `StreamNet.closeAll`, `DayDeletionRuntime.activeOn`, `RoadGraph.wayOf/nameOfOsmWay`, `GeoPoint.bearingTo`, `PuckAnimator.compassDeg`, `MapSettings.regionBox`, `OrderFlow.statusText`, 2 kullanılmayan metin, 5 sabit | −104 satır |
| `CustomerMatch` ad kuralları `NameMatch`'in birebir kopyasıydı → tek kopya | −15 satır |
| Harita sekmesi ve ilk açılış kurulumu aynı yükleme ve kartı iki kez yazıyordu → `rememberMapData` + `MapSetup` | −12 satır |
| Gömülü harita: OpenFreeMap'in uzak stili yerine **62 katmanlı, sprite'sız, tek yazı tipli** yerel stil (Protomaps'ten) (yer simgeleri, kalkanlar, sınırlar, ülke/bölge adları, düşük yakınlık örtüsü yok; çok yazılı ad ifadeleri yerine tek ad) | harita her karede daha az katman ve ifade işler |

Kalan küçük tekrarlar (bilerek bırakıldı): MapLibre/Google yüzeylerinin yaşam döngüsü gözlemcisi (20 satır; iki ayrı `MapView` türü), `LinkVault`/`KeyVault` (iki modülde aynı şifreli tercih sarmalayıcı, 13 satır), `EnergySegments`/`ScooterBridgeImpl` (9 satır). Birleştirmeleri modül sınırını değiştirir; kazanç küçük.

## 4. Özellik düzeyinde sadeleştirme seçenekleri — **karar senin** (her biri tek tek sorulacak)

Satırlar ana kod + test. "Pil" sütunu vardiya boyunca sürekli çalışan iş varsa.

| # | Özellik | Kod | Pil / APK | Öneri | Not |
|---|---|---|---|---|---|
| 1 | **Gölge mod araçları** (eski uygulamalarla karşılaştırma: `logic/orders/src/shadow`, `tools/shadow-fixture`, `.dev`'deki anahtar) | ~840 + test | yok | **kaldır** | Eski uygulamalar telefonlardan silindi (A10.4); S28 anahtarı `.dev`'de tutmuştu |
| 2 | **Eski kayıtları içe aktarma** (YolGo günlüğü `JournalImport`, Rawstats `LogImport`) | ~320 + test | yok | **kaldır** | S31: geçiş sıfırdan, eski kayıt taşınmaz |
| 3 | **OSM raster altlık seçeneği** | ~60 | ağ (karo) | **kaldır** | Gömülü vektör altlık ağsız; OSM karo sunucusu politikası da kısıtlı |
| 4 | **Uygulama içinden harita indirme** ("İndir / güncelle": hazır veri, Overpass) | ~260 (+ indirme ekranı) | ağ | seçmeli | Graf her APK'yla gelir; indirme yalnız APK'sız güncelleme için. Kalkarsa harita = APK sürümü |
| 5 | **Y13 yüzey ölçümü + Y14 yol kalibrasyonu** | ~4.120 + 1.540 test | **yüksek**: vardiya boyunca ivmeölçer 100 Hz + jiroskop/yönelim 50 Hz + GPS 2 Hz (saniyede ~200 sensör olayı) | kapat ya da varsayılan kapalı | S32/S33'te istendi; en büyük sürekli CPU işi. Toplu teslim (batching) bugünkü algoritmayı bozar (örnek zamanı son GPS'le eşleniyor), yeniden yazım ister |
| 6 | **Uzaktan kullanım** (N2: ekran paylaşımı + uzaktan dokunuş) | ~2.660 + 1.225 test | yalnız kullanırken | kullanmıyorsan kaldır | İkinci erişilebilirlik servisi ve ekran kaydı izni de kalkar |
| 7 | **Hırsızlık koruması** (N6) | ~990 + 1.240 test | düşük | kullanmıyorsan kaldır | SMS izni de kalkar |
| 8 | **Teklif veri seti** (Y19) | ~1.215 + 650 test | düşük (gün sonu) | kullanmıyorsan kaldır | |
| 9 | **Ekonomi v2 tahmincileri + gölge değerlendirme** | ~1.100 | düşük | teklif puanını kullanmıyorsan sadeleştir | |
| 10 | **Modül birleştirme** (9 Android modülü → 4: `:app`, `:map`, `:scooter`, `:link`) | yapı | derleme süresi | öneri | `:economics` 45, `:core` 572, `:capture` 475, `:orders` 806 satır; aralarındaki "bağ" kodu (`Orders.bind` …) azalır. Mimari kararı (plan Bölüm 4) |
| 11 | **Belgeler** (`docs/sartname/` ~10 bin satır) | belge | yok | WEEM 1.0 sonrası modül başına kısa özet | İşi biten şartname bölümleri git'te kalır |

En büyük kazanç (kod + pil): **5** (Y13/Y14). En güvenli: **1, 2, 3**. 1+2+3 birlikte ~1.200 satır ve bir ayar ekranı satırı azaltır.

## 5. Pil — araştırma ve uygulananlar

**Araştırma (özet):** pilin büyük kısmı ekran (harita açıkken ekran açık), GNSS, hücresel veri, BLE ve sürekli CPU işinden gelir. Android'in önerileri: sensörleri toplu teslimle (`maxReportLatencyUs`) kaydetmek, erişilebilirlik olaylarını yalnız gereken uygulama ve türle sınırlamak (`packageNames`, olay türleri, `notificationTimeout`), görünmeyen ekranda çizmemek, ağ isteklerini azaltmak, Play dışı kurulumda **baseline profile** (ART önceden derleme) ile hem açılış hem dokunma gecikmesini kısmak.

**Bu oturumda uygulananlar (davranış aynı):**
1. **Harita sekmesi arkadayken hiçbir şey çizilmez ve dinlenmez:** konum dinleyicisi, pusula (~33 Hz), işaret animasyonu (~30 fps), ekran açık tutma, sık akışlar ve titreşim durur (sekme artık yeniden kurulmuyor, ama gizliyken uyur).
2. **Ekran okuma:** Wolt/Bolt'taki içerik değişikliği fırtınasında ağaç her olayda iki kez (hemen + 180 ms) okunuyordu; artık en çok 250 ms'de bir (son durum yine okunur). Vardiya tiki ayrıca 500 ms'de bir okur.
3. **Gömülü altlık:** harita karoları ağdan inmez (hücresel radyo daha az uyanır).
4. **MapLibre en çok 60 kare/sn** (120 Hz ekranda kamera animasyonu ve kaydırma 120 kare çiziliyordu).
5. **Google yüzeyi:** yer işaretleri yalnız değişen eklenir/kalkar, en çok 100 (önceden her değişimde 180'e kadar nesne silinip ekleniyordu).
6. **Bellek:** SRTM karoları bellek eşlemeli (uygulama açıkken Java yığınında kalıcı ~52 MB daha az); gömülü graf akışla açılır ve dosyadan bellek eşlemeli, toplu okunur (açılışta tepe ~200 MB daha az). Daha az çöp toplama; bellek yüzünden kapanma riski düşer.
7. **Baseline profile:** uygulama kodu kurulumdan sonra önceden derlenir (JIT işi ve ilk açılışlardaki CPU azalır).

**Kullanıcı kararı isteyenler (ölçülmedi; büyükten küçüğe tahmini etki):**
- **P1 Ekran:** vardiya açıkken harita ekranı hep açık (`keepScreenOn`). Seçenek: rota yokken ekran kendiliğinden kapanabilsin (rota açılınca açık kalır).
- **P2 Y13 sensörleri:** madde 4.5 (kapat / varsayılan kapalı / yalnız istenince).
- **P3 OCR sıklığı:** Wolt/Bolt penceresi açıkken ML Kit OCR ~1,5 sn'de bir, yeni teklif sonrası 10–20 sn boyunca 350 ms'de bir (D6). Seçenek: OCR yalnız erişilebilirlik okuması ücreti bulamayınca.
- **P4 Scooter kaydı uyandırma kilidi:** vardiya boyunca CPU uykuya geçmez (BLE okuma temposu için; Sürüş Paneli davranışı).
- **P5 GPS 2 Hz:** Y13 500 ms istiyor; kapatılırsa 1 Hz'e iner.

Kaynaklar: [Compose performansı ve baseline profile](https://developer.android.com/develop/ui/compose/migrate/compare-metrics), [Baseline profile duyurusu](https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html), [Sensör toplu teslimi (AOSP)](https://source.android.com/devices/sensors/batching), [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo), [MapLibre Android PMTiles](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/), [Protomaps indirmeleri](https://docs.protomaps.com/basemaps/downloads).
