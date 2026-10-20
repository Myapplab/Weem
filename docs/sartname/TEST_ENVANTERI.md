# Test envanteri — eski testler ve WEEM hedefleri

Durum: A0.1, 17 Ekim 2026. Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04` (Rawstats + Sürüş Paneli) ve `yolgo-test` @ `870ab6e` (YolGo). Kural (plan Bölüm 5): bir modül yazılmadan önce buradaki testleri Kotlin'e çevrilir; aynı girdi → aynı çıktı.

**Sayım komutları** (sayılar betikle üretildi, elle yazılmadı):
- Rawstats: `grep -c '@Test' app/src/test/java/com/kuryelog/app/*.kt` → **107 dosya, 785**. (`android-compile.sh` 799 = 785 + Sürüş Paneli sarmalayıcısı 14.)
- Sürüş Paneli: `ninebot-logger/tests/*Test.java` main() tabanlı 14 suit; sayı = `check(`/`assert…(`/`expect(` çağrısı → **324 denetim**. Gradle'da `SurusPaneliJvmTest` 14 `@Test` olarak görünür. Ayrıca cihazda: `androidTest/KeyVaultInstrument`, `LogExportTest`; Python (CI dışı): `reference_parity.py`, `laptop_client_test.py`, `upgrade_smoke.py`, `emulator_smoke.py`.
- YolGo Dart: `grep -cE '^\s*(test|testWidgets)\(' test/*.dart` → **83 dosya, 564** (döngüyle üretilen testler tek sayılır; `flutter test` 637 gösterir).
- YolGo Kotlin: `grep -c '@Test' android_overlay/test/*.kt` → **14 dosya, 88** (`tools/jvm-overlay/test.sh`).

**Hedef sütunu:** WEEM modülü (`logic/` saf kısmı). `sonra (Y13/Y14)` = WEEM 1.0'da yok (Y12 17 Ekim'de 1.0'a alındı, S3), `BEKLEYEN_OZELLIKLER.md`; `çevrilmez` = iki uygulama ya da Flutter/CI'a özgü, WEEM'de karşılığı yok; `uyarlanır` = davranış korunur ama iki uygulama sözleşmesi tek uygulamada iç arayüze döner (test yeniden yazılır). Birden çok modül yazılıysa ilk modül çeviriyi üstlenir.

**Doğrulama:** "Neyi doğrular" sütunu test adlarından ve ham bulgulardan (`docs/sartname/bulgular/`) çıkarıldı; her modülün şartname görevinde (A2.1, A3.1, A5.1, A6.1) test gövdesi okunarak kesinleşir.

## Özet (ilk hedefe göre)

| Hedef | Dosya | Test/denetim |
|---|---:|---:|
| :app | 5 | 15 |
| :capture | 15 | 202 |
| :core | 24 | 149 |
| :economics | 10 | 93 |
| :link | 1 | 8 |
| :map | 57 | 375 |
| :orders | 54 | 338 |
| :scooter | 18 | 344 |
| sonra | 16 | 125 |
| uyarlanır | 6 | 48 |
| çevrilmez | 12 | 64 |

Toplam 1.761 test/denetim (785 + 324 + 564 + 88). WEEM 1.0 için çevrilecek: **1.572** (sonra 125 ve çevrilmez 64 hariç; uyarlanır 48 dahil; dosya düzeyinde, dosya içi paylar aşağıda). A2.1 (17 Ekim) `:core` satırlarını test gövdelerine göre kesinleştirdi: 4 dosya başka modüle, 3 dosya çevrilmeze (`CORE.md` Bölüm 6). A3.1 (17 Ekim) `:capture` ve `:orders` satırlarını kesinleştirdi (`CAPTURE.md` 8, `ORDERS.md` 15): ilk hedefi değişen 5 dosya (`CashScreenM16Test` → `:orders`, `NoteFieldsF71Test` → `:capture`, `WaitObservationProcessDeathTest` → `:economics`, `AutoStartNoticeTest` → `:core`, `offer_feed_versions_test` → çevrilmez); karışık dosyalar test başına bölünür (sayılar hedef sütununda), dosya içinde çevrilmeyen 9 test (`MotionGate` 4, `OfferIdentity` 2, feed/sağlayıcı 3) şartnamelerde. A6.1 (19 Ekim) YolGo satırlarını ve `LogFeedV3Test`'i kesinleştirdi (`MAP.md` 16): ilk hedefi değişen 11 dosya (`offer_engine_test`, `street_prefs_migration_test` → çevrilmez; `route_costing_test`, `road_score_ui_test`, `TelemetryJournalTest` → sonra; `dataset_days_test`, `DatasetContractTest`, `DatasetMatchingTest`, `DatasetReadsTest` → `:orders` (`logic/orders` `dataset`); `dataset_screen_test` → `:app`; `offer_navigation_removed_test` çevrilmezden `:map`'e), `LogFeedV3Test` uyarlanır `:core` → `:orders`; karışık dosyalarda çevrilmeyen 21, sonraya kalan 20 ve başka görevde karşılanmış 4 test hedef sütununda. Aşama 6'nın kesin çeviri sayısı `MAP.md` 16.5: A6.2a 110 + A6.2b 246 (+ `LogFeedV3Test` 10). Satır tahmini plan Bölüm 6'da.

## Test listesi

### Rawstats — `app/src/test/java/com/kuryelog/app/` (JUnit, `@Test` sayısı) — 107 dosya, 785

| Test dosyası | Sayı | Neyi doğrular | Özellik | Hedef |
|---|---:|---|---|---|
| `BrandingK26Test` | 3 | K26 paket ve tercihler aynı; simge ve ad | G5 | :app |
| `I18nCoverageTest` | 2 | TR/AZ çeviri kapsamı | G3 | :app |
| `RawPaletteContrastTest` | 2 | tema renk karşıtlığı | Y1 | :app |
| `BoltBubbleR2Test` | 5 | R2 Bolt alış ekranı "N min" → hazır süresi | R1 R6 | :capture :orders (A3.1: 4 / 1; `CAPTURE.md` 8) |
| `CashScreenM16Test` | 7 | M16 Wolt nakit ekranı, para üstü, kutu ✓ | R8 | :orders (A3.1: 7, `CashPayment` `:orders`'ta; `CAPTURE.md` 8) |
| `OcrPaceD6Test` | 11 | D6 OCR temposu | R1 | :capture :orders (A3.1: 9 / 2) |
| `ParserEconomicsTest` | 23 | parser ekonomi alanları | R1 R3 | :capture :orders (A3.1: 20 / 3) |
| `RecordingPolicyTest` | 12 | kayıt politikası, tekrar süzgeci | R1 | :capture (A3.1: 4; 2 `:core` `BackupFiles`; 6 çevrilmez: `MotionGate`, `OfferIdentity` ölü kod) |
| `ScreenReadingE7Test` | 4 | E7 ekran okuma kapalı uyarısı | R19 | :capture (A3.1: 3 + 1 kaynak taraması) |
| `WoltDeliverySliderTest` | 9 | Wolt teslim kaydırıcısı | R1 R5 | :capture :orders (A3.1: 3 / 6) |
| `WoltOfferFeeK37Test` | 6 | K37 Wolt teklif ücreti yeniden okuma | R1 R3 | :capture :orders (A3.1: 2 / 4) |
| `WoltSliderSpinnerK33Test` | 9 | K33 kaydırıcı dönen gösterge | R1 R5 | :capture :orders (A3.1: 2 / 7) |
| `WoltStatsF81Test` | 6 | F8.1 Wolt Stats dönemi ve tutarlar | R13 | :capture |
| `IcuRegexTest` | 2 | ICU'da derlenmeyen desen yasağı (0.25.1 hatası) | R1 | :capture (tüm modüller; A3.1: + JDK 21 sınır testleri, `CAPTURE.md` 7.5) |
| `CourierFlowTest` | 44 | uçtan uca kurye akışı: ekran kareleri → teklif/sipariş kaydı | R1 R3 R5 | :capture :orders (A3.1: 15 / 27; 2 `:core` `ManualSessionPolicy`) |
| `DeliveryRecognitionTest` | 24 | teslim tanıma: Wolt müşteri kartı, kaydırıcı, bekleme | R1 R5 | :capture :orders (A3.1: 11 / 13) |
| `SahaTekrarTest` | 16 | 11–12 Ekim saha kareleri tekrarı (altın) | R1 R3 R5 | :capture :orders (A3.1: 6 / 10; kareler `testFixtures`) |
| `WoltFlowK1K5Test` | 25 | K1–K5 Wolt akışı | R1 R3 R5 | :capture :orders (A3.1: 5 / 20) |
| `AnalysisExportTest` | 13 | anonim analiz dışa aktarımı: takma ad, alan izin listesi, gizleme | R18 | :core |
| `CrashGuardTest` | 10 | korumalı girişler; hata errors.jsonl'a, süreç ölmez | R20 | :core |
| `CrashReportTest` | 7 | crashes.jsonl biçimi, ApplicationExitInfo süzgeci | R20 | :core |
| `DailyPackTest` | 2 | günlük paket adı ve içeriği | R17 R18 | :core |
| `DailyRecordsTest` | 8 | gün kayıtları, arşiv/geri alma, silme, logNames tamlığı, yol geçişi reddi | R12 R17 | :core |
| `EndedShiftMinutesTest` | 5 | biten vardiya dakikaları | G1 | :core |
| `ExportKeysParityTest` | 1 | dışa aktarma izin listeleri eşit | R17 R18 | :core |
| `HomeDraftTest` | 5 | onaysız ev seçimi | G2 | :core |
| `HomeEndRuleF45bTest` | 7 | F4.5b ev bitişi 50 m / 30 sn / <5 km/s | G1 | :core |
| `HomeFixRegressionTest` | 2 | ev GPS örneklemesi regresyonu | G2 | :core |
| `HomeFixesTest` | 4 | ev GPS örneklemesi | G2 | :core |
| `HomeRepositoryTest` | 7 | tek ev kaydı | G2 | :core |
| `HomeShiftE6Test` | 9 | E6 ev çıkışında vardiya başlatma | G1 | :core |
| `HomeShiftK38Test` | 11 | K38 ev kuralıyla vardiya | G1 | :core |
| `RecordJsonTest` | 6 | kayıt JSON biçimi | R12 R17 | :orders (A2.1: yolculuk/teklif tipleri orada; `CORE.md` 6.1) |
| `RedactorParityTest` | 4 | gizleme ref.py/redact.py ile eşit | R18 | :core |
| `ShiftDistanceD8Test` | 4 | D8 vardiya mesafesi, scooter odometresi yedeği | G1 R12 | :core |
| `ShiftInterruptH4Test` | 3 | H4 kesilen vardiya | G1 | :core |
| `ShiftStateF41Test` | 5 | F4.1 vardiya durumu yükü (3 çevrilir; sağlayıcı yolu ve izni çevrilmez, S7) | G1 N1 | :core |
| `EconomicsLogsTest` | 21 | ekonomi v2 log satırları | R4 | :economics |
| `EconomicsObservationTest` | 13 | sürekli teklif olayı, boşta aralıkları | R4 | :economics |
| `EconomicsV2GoldenTest` | 8 | altın vektörler (ref.py ile eşitlik) | R4 | :economics |
| `EconomicsV2PriorsTest` | 4 | öncül değerler = altın, kopya | R4 | :economics |
| `EnergySegmentsTest` | 4 | bacak/bekleme enerji satırları | R14 | :economics |
| `OfferGeoTest` | 12 | teklif bölgesi Z1..Z4 vektörleri | R4 | :economics |
| `OfferScoreE4Test` | 8 | E4 teklif puanı 1–10 | R4 | :economics |
| `ScooterOrderEnergyTest` | 7 | sipariş başına enerji | R14 | :economics |
| `ShadowEvaluatorTest` | 11 | gölge değerlendirme (shadow corpus) | R4 | :economics |
| `FeedChangesE8Test` | 8 | E8 içerik anahtarı, 200 ms aralık, kalp atışı | N1 | :link |
| `ArrivalDistanceM20Test` | 4 | M20 varış mesafesi kaynağı (YolGo nav / Maps bildirimi / GPS payı) | R5 R16 | :orders |
| `AutoStartNoticeTest` | 1 | otomatik başlatma bildirimi | R19 G1 | :core (A3.1: kaynak taraması A2.4 bildirimlerine; scooter kısmı A5; `ORDERS.md` 15.2) |
| `AverageListE1Test` | 6 | E1 ortalamalar listesi | R12 | :orders |
| `BoostListM21Test` | 4 | M21 Earn extra listesi: gün, dilim, liste kazanır | R10 | :orders |
| `BoostLogK27Test` | 7 | K27 boost değişimi ve Bolt 30 dk dilimleri | R10 | :orders |
| `BoostTempE3Test` | 5 | E3 Wolt plan boost + sıcaklık; Bolt çarpanı teklif kartından | R10 R15 | :orders |
| `CashOrderK31K32Test` | 16 | K31/K32 nakit sipariş ve bahşiş sorusu | R8 | :orders |
| `CashPendingM17Test` | 5 | M17 Wolt CASH PENDING kaydı | R9 | :orders |
| `CashReconcileM17Test` | 5 | M17 nakit mutabakatı (başlangıç + toplanan − yatırılan) | R9 | :orders |
| `CoreLifecycleV2Test` | 23 | teklif/sipariş yaşam döngüsü v2 (pencere sonu zaman aşımı ≠ ret) | R3 R5 | :orders :economics (A3.1: 15 / 8 `WaitObservation`) |
| `CustomerBubbleF72Test` | 7 | F7.2 müşteri balonu satırları | R6 | :orders |
| `CustomerBubbleViewF73Test` | 3 | F7.3 balon kaydırma/açma kararları | R6 | :orders |
| `CustomerCardE16Test` | 6 | E16 müşteri kartı | R6 | :orders (A3.1: 3 uyarlanır `RouteStatus`, metin "(WEEM)"; 1 kaynak taraması) |
| `CustomerMatchK21Test` | 5 | K21 müşteri eşleştirme | R5 | :orders |
| `CustomerNearM19Test` | 5 | M19 müşteriye ≤10 m varış | R5 | :orders |
| `CustomerStageK34ClosedCardTest` | 10 | K34 kapanan kartta müşteri aşaması | R5 | :orders |
| `CustomerStageK34LateDeliveryTest` | 7 | K34 geç teslim | R5 | :orders |
| `CustomerStageK34RecordTest` | 10 | K34 kayıt alanları | R5 | :orders (A3.1: 6; 1 `:economics`, 2 `:core` R18, 1 uyarlanır `OrderEvents`) |
| `CustomerStageK34RoutingTest` | 5 | K34 müşteriye yönlendirme | R5 | :orders |
| `CustomerStageK34Test` | 10 | K34 müşteri aşaması temel akış | R5 | :orders |
| `DeliveryCountD3Test` | 9 | D3 teslim sayımı (çıkarımlı dahil, çift sayım yok) | R5 R12 | :orders |
| `DeliveryDeadlineK29Test` | 4 | K29 platform teslim saati | R5 | :orders |
| `DualOfferStatusK30Test` | 3 | K30 iki platform durum satırı | R3 R7 | :orders |
| `NoteFieldsF71Test` | 6 | F7.1 bina/kat/daire/kod notları | R6 | :capture (A3.1: `NoteFields` `:capture`'ta; `CAPTURE.md` 2.8) |
| `OfferFeedV2Test` | 6 | teklif özeti v2 alanları | R3 N1 | :orders (A3.1: 4 uyarlanır `OrderEvents.offers`; 2 çevrilmez: feed v1, değişim anahtarı) |
| `OfferNetLineTest` | 6 | net = brüt − filo komisyonu | R7 N4 | :orders |
| `OfferWeatherK28Test` | 2 | K28 teklif anının havası | R15 | :orders |
| `OrderHistoryTest` | 9 | günlük sipariş geçmişi, brüt/net | R12 | :orders |
| `OverlayBadgeK35Test` | 4 | K35 rozet satırları (ad yok) | R7 | :orders |
| `PhoneK25Test` | 3 | K25 müşteri telefonu yalnız telefonda; kayıt/yedekte yok | R6 | :orders |
| `PlatformHistoryK36Test` | 10 | K36 platform kazanç listeleri | R13 | :orders |
| `PlatformListD15Test` | 5 | D15 Wolt gün başlığı, tekrar okunan teslim bir kez | R13 | :orders |
| `PlatformTotalsF82Test` | 6 | F8.2 Wolt Daily details, nakit toplama | R13 | :orders |
| `PlatformTotalsF83Test` | 5 | F8.3 economics-platform-totals.jsonl | R13 | :orders |
| `PlatformTotalsF84Test` | 7 | F8.4 karşılaştırma kuralları | R13 | :orders |
| `PlatformTotalsF85Test` | 3 | F8.5 resmî değerler kartı | R13 | :orders |
| `PocketCashM15Test` | 4 | M15 cepteki nakit | R9 | :orders |
| `ReadinessE15Test` | 5 | E15 hazır sayacı | R5 | :orders |
| `RecordPagesTest` | 2 | gün sayfaları sırası ve seçimi | R12 | :orders |
| `RestaurantBubbleE14Test` | 6 | E14 restoran balonu | R6 | :orders |
| `RestaurantWaitE2Test` | 6 | E2 restoran beklemesi | R5 | :orders |
| `StuckCustomerM22Test` | 8 | M22 takılı "Müşteriye gidiliyor" | R5 | :orders |
| `TipNotificationF46Test` | 4 | F4.6 bahşiş hızlı cevap bildirimi | R8 | :orders |
| `TipRecordF48Test` | 5 | F4.8 bahşiş kaydı | R8 | :orders |
| `TodayAveragesTest` | 2 | bugünün ortalamaları (yalnız ölçülen) | R12 | :orders |
| `TripStagesE8Test` | 6 | E8 sipariş aşamaları yükü | R5 N1 | :orders :economics (A3.1: 4 / 2 `courierState`) |
| `VenueArrivalD9Test` | 11 | D9 restoran varışı 60 m / 20 sn | R5 | :orders (A3.1: 10; 1 `:economics` bacak satırı) |
| `VenueNearM18Test` | 5 | M18 restorana ≤5 m varış | R5 | :orders |
| `WaitObservationProcessDeathTest` | 5 | bekleme gözlemi süreç ölümünde | R5 | :economics (A3.1: bekleme gözlemi `:economics`'in; `ORDERS.md` 15.1) |
| `WoltSameCustomerR1Test` | 5 | R1 aynı müşteriye çoklu teslim | R5 | :orders |
| `YolGoNavE8Test` | 13 | YolGo nav hedefi/kalan yol tüketimi | Y4 R5 N1 | :orders (A3.1: 10 uyarlanır `RouteStatus`/`NavPort`; 2 A2.5 `MessagesTest`'te; 1 çevrilmez) |
| `ScooterBridgeTest` | 6 | ScooterBridge arayüzü, korumalı çağrılar | R14 N1 | :scooter |
| `ScooterFeedTest` | 5 | canlı scooter yükü | S2 N1 | :scooter |
| `ScooterReconnectM23Test` | 4 | M23 veri kesildi/geldi bildirimi | S6 | :scooter |
| `ScooterSamplesTest` | 5 | economics-scooter.jsonl 1 Hz | S3 R14 | :scooter |
| `HomePickContractTest` | 6 | haritadan ev seçme sonucu doğrulama (iki uygulama sözleşmesi → WEEM içi) | G2 | uyarlanır :core |
| `LogFeedV3Test` | 10 | gün kayıtları v3: allowlist, maskeleme, sayfalama | Y19 R18 | uyarlanır :orders (A6.1: `logic/orders` `dataset.DayLogSnapshotTest`, A6.9; `MAP.md` 13.6, 13.18) |
| `FeedSignerTest` | 2 | iki uygulama arası imza kapısı (tek uygulamada yok; 2 telefon eşleştirmesi A8'de) | — | çevrilmez |
| `OfferFeedAccessTest` | 7 | sağlayıcı erişim kapısı (2 telefon izni A8'de yeniden) | — | çevrilmez |
| `YolGoButtonE5Test` | 2 | kaldırılmış YolGo düğmesi | — | çevrilmez |

### Sürüş Paneli — `ninebot-logger/tests/` (main() tabanlı suit; sayı = `check(` denetimi; JUnit sarmalayıcı `SurusPaneliJvmTest` 14 `@Test`) — 14 dosya, 324

| Test dosyası | Sayı | Neyi doğrular | Özellik | Hedef |
|---|---:|---|---|---|
| `BatteryProbeTest` | 64 | harici batarya (BMS kartı) arama | S5 | :scooter |
| `DashboardTest` | 15 | panel değerleri | S2 | :scooter |
| `EnergyTest` | 21 | enerji bloğu | S4 R14 | :scooter |
| `EventQueueTest` | 7 | olay kuyruğu | S3 | :scooter |
| `FloatingTest` | 23 | yüzen panel geometri/alanlar | S7 | :scooter |
| `LinkTest` | 23 | GATT sırası | S1 | :scooter |
| `LogPolicyTest` | 14 | kayıt politikası | S3 S8 | :scooter |
| `PairFlowTest` | 26 | eşleşme akışı | S1 | :scooter |
| `PowerCadenceTest` | 23 | güç okuma temposu | S1 S2 | :scooter |
| `ProtocolTest` | 33 | çerçeve + şifreleme protokolü | S1 | :scooter |
| `SpeedCadenceTest` | 23 | hız okuma temposu | S1 S2 | :scooter |
| `SweepTest` | 22 | değer taraması | S8 | :scooter |
| `TimingTest` | 10 | zamanlama/yeniden deneme | S1 S6 | :scooter |
| `TripMeterTest` | 20 | yolculuk sayacı | S4 | :scooter |

**A5.1 notu (17 Ekim, `SCOOTER.md` 13):** "Sayı" = `grep -c 'check('` satır sayısı (dosyanın `check` tanım satırı dahil). Çağrı yeri 336 (`Timing` 9, `Protocol` 55, `Link` 23, `PairFlow` 26, `Sweep` 21, `PowerCadence` 22, `SpeedCadence` 22, `BatteryProbe` 64, `Dashboard` 14, `EventQueue` 6, `LogPolicy` 13, `Energy` 20, `TripMeter` 19, `Floating` 22); çalışma sayıları (suitin bastığı; JDK 21'de hepsi yeşil): 9, 408, 33, 37, 24, 49, 61, 854, 14, 13, —, 20, 19, 22. Çeviri kabulü: çağrı yeri ve çalışma sayısı suit başına aynı. Yeşil olacağı görev: A5.2 `Timing`, `Protocol`, `Link`, `PairFlow`, `Sweep`, `PowerCadence`, `SpeedCadence`, `BatteryProbe`, `Dashboard`; A5.3 `EventQueue`, `LogPolicy`, `Energy`, `TripMeter`; A5.4 `Floating`. Rawstats tarafı: `ScooterFeedTest`'in 2 testi (`pathMatchesYolGoReader`, `scooterReaderStillNeedsAllGates`) çevrilmez (S7, sağlayıcı kalkar), 3'ü `ScooterLiveMapper`'a uyarlanır; `ScooterSamplesTest.fileIsBackedUpAndExported` `:core`/A4.3'e. Cihaz: `KeyVaultInstrument` → `:scooter` `androidTest`; `LogExportTest` → JVM. Özet tablodaki `:scooter` 344 bu nedenle A5.2'de 341'e iner (betikle güncellenir).

### YolGo Dart — `test/` (`^\s*(test|testWidgets)\(` sayısı) — 83 dosya, 564

| Test dosyası | Sayı | Neyi doğrular | Özellik | Hedef |
|---|---:|---|---|---|
| `scooter_language_section_test` | 3 | scooter/dil bölümü | G3 S2 | :app |
| `home_day_controller_test` | 5 | gün denetleyicisi (1–3 `:core`; 4–5 `:map` A6; `CORE.md` 6.2) | G1 | :core (A6.1: 4–5 `:map` A6.8/A6.9; `MAP.md` 11.16, 12.13) |
| `home_day_e6_test` | 10 | E6 çıkış bölgesi 200 m | G1 | :core |
| `home_day_end_rule_test` | 5 | ev bitişi 50 m / 30 sn | G1 | :core |
| `home_day_rules_test` | 19 | ev günü kuralları | G1 | :core |
| `native_telemetry_test` | 1 | yerel telemetri günlüğü (Flutter kanal yarışı; ilkesi `ShiftMachine` testinde) | G1 | çevrilmez |
| `shift_engine_test` | 9 | YolGo gün motoru: km, duruş, ışık, sipariş aşaması | G1 Y11 | :map (A2.1) |
| `offer_engine_test` | 7 | ek teklif fırsat maliyeti | R4 N5 | çevrilmez (A6.1: v1 teklif motoru ölü kod, ekonomi v2 `:economics`'te; SORU-T1-2, `MAP.md` 10.14, 16.4) |
| `route_costing_test` | 6 | rota maliyeti (teklif) | R4 N5 | sonra (Y13) (A6.1: 5 Y13 yüzeyine bağlı; `planLeavesBox` 1 çevrilmez, SORU-T1-2; `MAP.md` 10.14, 16.4) |
| `address_parser_test` | 17 | bağlantı ve adres ayrıştırma | Y5 Y6 | :map |
| `api_settings_test` | 1 | API anahtarı ayarları | Y6 Y10 | :map |
| `approach_zoom_test` | 4 | yaklaşma yakınlaştırması, 10 sn sonra takip | Y4 | :map |
| `arrival_focus_test` | 4 | varış odağı 100/150 m | Y4 | :map |
| `arrival_pin_test` | 3 | varış iğnesi | Y4 | :map |
| `assistant_test` | 14 | görev girişi, saat/gün, 1 km sınırı, bölge histerezisi | Y20 | :map (A6.1: `day` 6 A6.8, `tasks` 5 A6.9, `input`/`nav` 2 A6.6, `weather` 1 A6.7; `MAP.md` 16.3) |
| `battery_estimator_test` | 5 | batarya tahmini, enerji sayacı | Y9 | :map |
| `bikeway_e13_test` | 18 | E13 bisiklet yolu işaretleme ve rota etkisi | Y15 Y3 | :map |
| `courier_stage_follow_test` | 14 | sipariş aşaması izleyici | G1 Y20 N1 | :map (A6.1: 13; 1 çevrilmez, feed v2 zarfı; `MAP.md` 11.16) |
| `courier_stages_e8_test` | 13 | E8 aşama yükü tüketimi | N1 R5 | :map (A6.1: 12; 1 çevrilmez, feed v2 zarfı; `MAP.md` 11.16) |
| `dataset_days_test` | 2 | veri seti günleri | Y19 | :orders (A6.1: `logic/orders` `dataset`; `MAP.md` 13.18) |
| `dataset_screen_test` | 5 | veri seti ekranı | Y19 | :app (A6.1: Robolectric `DatasetScreenTest`; `MAP.md` 13.18) |
| `geocoder_scope_test` | 1 | geokod kapsamı | Y6 | :map |
| `home_picker_test` | 5 | haritadan ev seçme | G2 | :map |
| `hud_layout_test` | 2 | HUD yerleşimi | Y1 | :map |
| `light_leaks_f52_test` | 3 | F5.2 ışık karakterizasyonu | Y11 | :map |
| `light_rule_m14_test` | 14 | M14 ışık kuralı (≤5 m, yaya yoluna ≤1 m), lightMaxM göçü | Y11 | :map (A6.1: 12; 2 çevrilmez, S1 göç; `MAP.md` 11.16) |
| `light_tracker_test` | 4 | canlı ışık ölçer | Y11 | :map |
| `locate_button_test` | 3 | konum düğmesi | Y1 | :map |
| `log_store_test` | 7 | günlük/sürüş kayıtları, silme, dışa aktarma | Y18 | :map |
| `map_data_update_test` | 2 | harita verisi güncelleme | Y7 | :map |
| `map_hud_gesture_test` | 2 | HUD dokunma | Y1 | :map |
| `native_fast_fix_test` | 2 | hızlı konum okuması (≤1/sn) | Y4 | :map |
| `nav_assistant_test` | 1 | dış hedef + sağlayıcı akışı | Y4 Y20 | :map |
| `nav_feed_test` | 11 | nav/v1/current yükü | Y4 N1 | :map |
| `nav_only_location_test` | 2 | gün bitince rota açıksa yalnız-nav konum | Y4 G1 | :map |
| `overpass_test` | 3 | Overpass sorgu/ayrıştırma | Y7 | :map |
| `park_paths_test` | 4 | F6.4 park yolları | Y3 | :map |
| `place_search_test` | 8 | yer arama | Y6 | :map |
| `pois_test` | 4 | POI ve düzeltmeler | Y16 | :map |
| `puck_arrow_test` | 3 | konum oku | Y1 | :map |
| `push_segments_test` | 5 | F6.3 itme segmentleri | Y3 | :map |
| `remaining_km_e12_test` | 7 | E12 kalan km göstergesi | Y4 Y9 | :map |
| `reminder_card_test` | 3 | hatırlatıcı kartı 30 sn | Y20 G4 | :map |
| `reminders_test` | 5 | hatırlatıcılar | Y20 | :map |
| `ride_log_status_test` | 2 | sürüş kaydı durumu | Y18 | :map |
| `route_hours_test` | 3 | ters yön saat izni | Y8 Y3 | :map |
| `route_warnings_h2_h3_test` | 3 | H2/H3 rota uyarısı, başarısızlıkta eski rota | Y4 | :map |
| `router_test` | 38 | rota motoru | Y3 | :map (A6.1: 31; 7 sonra, Y13 ölçülmüş yüzey; `MAP.md` 3.14) |
| `street_prefs_migration_test` | 1 | sokak kararları göçü | Y8 | çevrilmez (A6.1: S1, göç yazılmaz; `MAP.md` 4.8) |
| `task_controller_test` | 1 | görev denetleyicisi | Y20 | :map |
| `v140_features_test` | 14 | 1.4.0: ışık kuralları, hız seçenekleri | Y11 Y9 | :map |
| `v150_features_test` | 15 | 1.5.0: rota başlangıç konumu, özel hızlar | Y4 Y9 | :map (A6.1: 10; 5 çevrilmez, `HomeDepartureDetector` ölü kod; `MAP.md` 7.15) |
| `v160_y1_y2_test` | 5 | 1.6.0: hava şeridi/önbellek | Y10 | :map |
| `v170_features_test` | 13 | 1.7.0: Rawstats canlı scooter yükü, batarya | S2 Y9 N1 | :map (A6.1: 6; 2 A2.5/A5.3'te karşılandı, 4 çevrilmez (S7), 1 sonra (Y13); `MAP.md` 10.12) |
| `weather_section_test` | 4 | hava bölümü | Y10 | :map |
| `ww_clean_run_test` | 5 | ters yön temiz geçiş/onay (Y12 sorusuyla ilişkili) | Y8 Y12 | :map |
| `ww_green_two_way_test` | 4 | onaylı temiz sokak iki yönlü | Y8 Y3 | :map |
| `ww_no_problem_green_test` | 5 | "Evet, sorun"/Ses− geçiş kararları | Y8 Y12 | :map |
| `yolgo_rules_test` | 4 | sapma 8 sn, sipariş JSON, yüzey/ışık, hava | Y4 Y11 Y10 | :map |
| `yolgo_sade_2_test` | 10 | gün kartı düğmeleri | G1 Y1 | :map (A6.1: 9; 1 çevrilmez, S1; `MAP.md` 6.14) |
| `offer_feed_versions_test` | 6 | teklif feed sürüm uyumu | N1 R3 | çevrilmez (A3.1: uygulamalar arası okuyucu, S7; sürüm/boyut denetimi `:link` A8; `ORDERS.md` 15.3) |
| `day_contributions_test` | 3 | gün katkıları defteri | Y12 | :map (A6.1: 2; 1 sonra (Y13/Y14), SORU-Q2; `MAP.md` 9.11) |
| `quality_test` | 9 | yol kalitesi ön tahmin/öğrenilen skor (rota seçimini etkiler) | Y12 Y3 | :map |
| `road_learner_test` | 9 | yol öğrenme | Y12 | :map |
| `road_save_test` | 1 | yol kaydı | Y12 | :map |
| `road_score_ui_test` | 11 | yol puanı ekranı | Y12 | sonra (Y13/Y14) (A6.1: SORU-Q2; `MAP.md` 9.11, 16.4) |
| `street_scores_test` | 12 | sokak puanları | Y12 | :map (A6.1: 1; 11 sonra (Y13/Y14), SORU-Q2; `MAP.md` 9.11) |
| `road_surface_score_test` | 4 | yüzey skoru | Y13 | sonra (Y13) |
| `surface_gate_test` | 11 | yüzey kapısı | Y13 | sonra (Y13) |
| `surface_single_flight_test` | 2 | yüzey tek uçuş | Y13 | sonra (Y13) |
| `surface_store_exclusions_test` | 4 | yüzey deposu dışlamalar | Y13 | sonra (Y13) |
| `surface_store_test` | 12 | yüzey deposu | Y13 | sonra (Y13) |
| `road_calibration_channel_test` | 6 | kalibrasyon kanalı | Y14 | sonra (Y14) |
| `road_calibration_score_test` | 10 | kalibrasyon skoru | Y14 | sonra (Y14) |
| `road_calibration_screen_test` | 4 | kalibrasyon ekranı | Y14 | sonra (Y14) |
| `road_calibration_test` | 7 | kalibrasyon | Y14 | sonra (Y14) |
| `rawstats_home_stale_test` | 6 | Rawstats ev eşitleme bayatlığı (WEEM'de tek ev, S7) | G2 | çevrilmez (A2.1) |
| `rawstats_home_sync_test` | 13 | Rawstats ev eşitleme (WEEM'de tek ev, S7) | G2 | çevrilmez (A2.1) |
| `rawstats_shift_end_test` | 11 | Rawstats vardiyası → YolGo günü; WEEM'de `ShiftFollower` (`CORE.md` 6.2) | G1 | uyarlanır :core |
| `rawstats_signals_test` | 10 | değişiklik sinyalleri | N1 | uyarlanır :link |
| `app_version_test` | 1 | Flutter sürüm dosyası | — | çevrilmez |
| `ci_apk_workflow_test` | 15 | Flutter CI iş akışı | — | çevrilmez |
| `offer_navigation_removed_test` | 4 | kaldırılmış teklif navigasyonu | — | :map (A6.1: 2 A6.5/A6.6; 2 çevrilmez, kaldırılmış teklif sayfası; `MAP.md` 7.15) |

### YolGo Kotlin — `android_overlay/test/` (JUnit, `@Test`) — 14 dosya, 88

| Test dosyası | Sayı | Neyi doğrular | Özellik | Hedef |
|---|---:|---|---|---|
| `NotifyPolicyTest` | 2 | bildirim politikası | G4 | :core |
| `TelemetryJournalTest` | 8 | telemetri günlüğü 3000 satır (YolGo servis → Flutter tamponu) | G1 Y18 | sonra (Y13) (A6.1: Y18 bu günlüğü kullanmaz; `MAP.md` 12.11, `CORE.md` 6.2) |
| `DatasetContractTest` | 6 | veri seti sözleşmesi | Y19 | :orders (A6.1: `logic/orders` `dataset`; `MAP.md` 13.18) |
| `DatasetMatchingTest` | 4 | veri seti eşleştirme | Y19 | :orders (A6.1: `logic/orders` `dataset`; `MAP.md` 13.18) |
| `DatasetReadsTest` | 6 | veri seti okuma | Y19 | :orders (A6.1: 4; 2 çevrilmez, yoklama yok; `MAP.md` 13.18) |
| `NavFeedContractTest` | 6 | nav yükü + IncomingLink | Y4 Y5 N1 | :map (A6.1: 2; 2 A2.5 `MessagesTest`'te karşılandı, 2 çevrilmez (S7); `MAP.md` 7.15) |
| `WeatherFeedTest` | 2 | hava yükü | Y10 R15 | :map |
| `SurfaceSignalTest` | 11 | yüzey sinyali | Y13 | sonra (Y13) |
| `RoadCalibrationCountdownTest` | 5 | 3-2-1 sayım | Y14 | sonra (Y14) |
| `RoadCalibrationRoadRmsTest` | 6 | yol RMS | Y14 | sonra (Y14) |
| `RoadCalibrationTest` | 18 | kalibrasyon | Y14 | sonra (Y14) |
| `CourierFeedPayloadTest` | 6 | teklif feed yükü (YolGo okuyucu) | N1 R3 | uyarlanır :link |
| `RawstatsObserverTest` | 5 | değişiklik gözlemcisi | N1 | uyarlanır :link |
| `HomeShareTest` | 3 | iki uygulama ev paylaşımı | G2 | çevrilmez |

## Altın test adayları (saha kayıtları ve vektörler)

| Dosya | Boyut | İçerik | Kullanan | WEEM |
|---|---:|---|---|---|
| `rawstats: app/src/test/resources/saha/saha_kareleri_2026-10-11_12.jsonl.gz` | 238 KB | 11–12 Ekim gerçek vardiya: 6.481 erişilebilirlik/OCR karesi (Wolt + Bolt) | `SahaTekrarTest` | `:capture` + `:orders` altın testi; gölge mod raporunun ilk girdisi |
| `rawstats: …/saha/saha_beklenen_2026-10-11_12.json` | 23 KB | aynı karelerden beklenen teklif/sipariş sonuçları | `SahaTekrarTest` | aynı |
| `rawstats: …/economics/golden_vectors.json` (= `docs/ekonomi-v2/golden_vectors.json`) | 248 KB | ekonomi v2 altın vektörleri (`ref.py` üretir) | `EconomicsV2GoldenTest`, `EconomicsV2PriorsTest` | `:economics` (A4.1) |
| `rawstats: …/economics/shadow_corpus.json` | 109 KB | gölge değerlendirme derlemi | `ShadowEvaluatorTest` | `:economics` |
| `rawstats: …/economics/redact_vectors.json` (= `docs/ekonomi-v2/vektorler/`) | 6 KB | gizleme vektörleri (`redact.py`) | `RedactorParityTest` | `:core` dışa aktarma |
| `rawstats: …/economics/offer_geo_vectors.json` (= `docs/ekonomi-v2/vektorler/`) | 7 KB | teklif bölgesi vektörleri | `OfferGeoTest` | `:economics` |
| `yolgo: test/fixtures/offer-economics.json` | — | teklif ekonomisi örneği | `offer_engine_test`, `route_costing_test` | `:economics` |
| `rawstats: ninebot-logger/tests/ProtocolTest.java`, `tools/reference/nb_crypto.py` | — | Ninebot çerçeve/şifreleme referansı (Python referansı CI dışı; lisans `tools/reference/PROVENANCE.md`) | `ProtocolTest`, `reference_parity.py` | `:scooter` (A5.1) |
| Telefon kayıtları (gün klasörleri `files/logs/YYYY-MM-DD/`) | — | kullanıcının ZIP yedekleri | — | gölge mod karşılaştırması (A3.7) ve A2.5 eski veri okuma; kullanıcıdan istenir |

Eksik: Sürüş Paneli ve YolGo için saha kaydı tabanlı altın test yok (YolGo rota beklentileri `router_test` içinde sabit). Gölge modda (`.dev`) toplanacak kayıtlar altın teste dönüştürülür.

## Çevrilmeyenler ve bekleyenler

- **Çevrilmez (12 dosya, 64):** `FeedSignerTest`, `OfferFeedAccessTest`, `YolGoButtonE5Test`, `HomeShareTest`, `app_version_test`, `ci_apk_workflow_test`, `native_telemetry_test`, `rawstats_home_sync_test`, `rawstats_home_stale_test`, `offer_feed_versions_test` (A3.1), `street_prefs_migration_test` ve `offer_engine_test` (A6.1) — davranışları WEEM'de yok; 2 telefon eşleştirme/izin testleri A8'de yeniden yazılır. `offer_navigation_removed_test`'in 2 testi A6.1'de `:map`'e alındı (2'si çevrilmez). Dosya içinde çevrilmeyenler: A3.1'de 9, A6.1'de 21 (`MAP.md` 16.4).
- **Sonra (16 dosya, 125):** Y13/Y14 testleri (13 dosya, 100) ve A6.1'de eklenen `route_costing_test` (Y13 yüzeyi), `road_score_ui_test` (SORU-Q2), `TelemetryJournalTest` (Y13 kanıt günlüğü; `MAP.md` 12.11). Y12 testleri (6 dosya, 45; `ww_clean_run_test` ve `ww_no_problem_green_test` zaten `:map`) 17 Ekim'de `:map`'e alındı (S3; A6.2a, A6.11); A6.1'e göre bunların 23'ü Y13/Y14'e aittir ve sonraya kalır (SORU-Q2; `MAP.md` 9.11). Dosya içinde sonraya kalan 20 test `MAP.md` 16.4'te.
- **Uyarlanır (6 dosya, 48):** Rawstats ↔ YolGo eşitleme testleri (`rawstats_shift_end_test`, `rawstats_signals_test`, `RawstatsObserverTest`, `CourierFeedPayloadTest`, `LogFeedV3Test`, `HomePickContractTest`): tek uygulamada iç arayüz, 2 telefon modunda `:link` mesajı olarak yeniden yazılır; `LogFeedV3Test` `logic/orders` `dataset`'te uyarlanır (A6.1, A6.9).

## A3.2 çeviri durumu (17 Ekim 2026)

Rawstats testleri `tools/test-translate/` betikleriyle bölündü ve çevrildi (kaynak `rawstats` @ `6388c04`); `./gradlew -p logic compileTestKotlin` yeşil, kök Android `:capture`/`:orders` derlemesi bozulmadı. Üretim sınıfları iskelet imza (`TODO("A3.4")`/`TODO("A3.5")`): `:capture` ve `:orders` testleri **kırmızı** (333 testten 4'ü yeşil: modül kimliği ×2, `karelerYuklenirVeKisiselVeriYok`, `noRegexConstructAndroidRejects`), `:core`'a giden 4 test yeşil (kod A2'de vardı).

| Hedef | Hedef (belge) | Çevrilen | Dosya |
|---|---:|---:|---|
| `logic/capture` | 93 | **93** | 14 dosya (`CAPTURE.md` 8); `ScreenReadingE7Test` 3 + 1 uyarlanan kaynak taraması, `IcuRegexTest` 2 (tüm modülleri tarar), `OfferCapture`/`OcrPace`/`OfferRules`/`ScreenDeduper`/`SliderSpinner`/`ScreenReading`/`NoteFields`/`NavDistance`/`OwnLines`/`NameMatch`/`Dialer` iskeletleri |
| `logic/orders`, `CAPTURE.md` 8 payı | 100 | **100** | `CourierFlowTest` 27, `DeliveryRecognitionTest` 13, `WoltFlowK1K5Test` 20, `SahaTekrarTest` 10, `WoltDeliverySliderTest` 6, `WoltSliderSpinnerK33Test` 7, `WoltOfferFeeK37Test` 4, `OcrPaceD6Test` 2, `BoltBubbleR2Test` 1, `ParserEconomicsTest` 3, `CashScreenM16Test` 7 |
| `logic/orders`, `ORDERS.md` 15.1 | 136 (M20 = 2) | **138** | `CoreLifecycleV2Test` 15, `CustomerStageK34*` 10+10+7+5, `CustomerStageK34RecordTest` 7 (6 + uyarlanan `feedV2ShowsOneOrderAtTheCustomer` → `OrderStages`/`TripStages`), `DeliveryCountD3Test` 9, `VenueArrivalD9Test` 10, `VenueNearM18Test` 5, `CustomerNearM19Test` 5, `ArrivalDistanceM20Test` 4, `StuckCustomerM22Test` 8, `RestaurantWaitE2Test` 5, `ReadinessE15Test` 4, `WoltSameCustomerR1Test` 5, `CustomerMatchK21Test` 5, `DeliveryDeadlineK29Test` 4, `TripStagesE8Test` 4 (`logic/core` `TripStages` tipine), `YolGoNavE8Test` 10 (`NavBinding`/`ScreenTrail`/`RouteStatus.Target`), `RecordJsonTest` 6 |
| `logic/core` | 4 | **4** | `ManualSessionPolicyTest` 2, `BackupRestoreTest` 2 (yeşil) |
| Çevrilmez | 6 | 0 | `MotionGate` 4, `OfferIdentity` 2 (`RecordingPolicyTest`) |

**Sapmalar (kaynağı: `ORDERS.md` 15.1 sayıları):**
- `ArrivalDistanceM20Test` belgede "orders (M20'den 2 kaynak taraması)" diyordu; dosyada kaynak taraması yok, 4 test de saf. **Dördü de `logic/orders`'a çevrildi** (+2). `googleMapsSummaryGivesMetres` yalnız `:capture`'ın `NavDistance`'ini dener; dosya bölünmesin diye `ArrivalDistanceM20Test` ile kaldı.
- Kaynak taraması testleri bu görevde **yazılmadı** (Android dosya adları A3.7/A3.8'de belli olur): `RestaurantWaitE2Test.cardAndListShowIt`, `ReadinessE15Test.restaurantNotificationUsesTheCounterAndFollowsIt` (ve A3.3 listesindekiler). Yalnız E7'nin uyarlanan taraması (`CAPTURE.md` 8) yazıldı.
- `CustomerStageK34LateDeliveryTest` (7 test): Rawstats testi ekonomi kaydedicisini (`EconomicsRecorder`) sürüyordu; kaydedicisiz kopya (ORDERS.md 15.1 `CustomerStageK34RecordTest` kuralı), altı `legs` satırı ifadesi (`assertEquals(1, r.legs(w.id).size)` vb.) çıkarıldı → **A4.1'e** (`EconomicsRecorder` testlerine) taşınacak.
- `TripStagesE8Test` (4 test) ve `CustomerStageK34RecordTest.feedV2ShowsOneOrderAtTheCustomer`: Rawstats JSON `TripStages.feed/trip` yerine `logic/core` `TripStages` tipini üreten **`OrderStages.of/trip`** (yeni ad; A3.5 yazar).
- `YolGoNavE8Test`: `YolGoNav.pickupOf/preferred` → **`NavBinding.pickupOf/preferred`** (yeni ad; kayıt dizgileri `yolgo_venue`, `nav_yolgo`, `gps_*_yolgo_target` aynı), `YolGoNav.Target` → `RouteStatus.Target`, `TARGET_MAX_AGE_MS` → `RouteStatus.TARGET_MAX_AGE_MS`.
- `SahaTekrarTest` (capture 6 + orders 10): `olcumRaporu` belgenin listesine göre `logic/orders`'ta (yalnız ayrıştırıcı kullanıyor ama listede capture'ın 6'sında yok); saha yükleyicisi `SahaFrames` (testFixtures, `SahaFrame`), kareler `logic/capture/src/testFixtures/resources/saha/` (bayt bayt kopya).
- Ekonomi payı (`CoreLifecycleV2Test` 8, `VenueArrivalD9Test` 1, `CustomerStageK34RecordTest` 1, `RestaurantWaitE2Test`/`ReadinessE15Test` taramaları, `TripStagesE8Test` 2) ve `core` (R18) 2 test A4.1/A4.3'te.

## A3.3 çeviri durumu (17 Ekim 2026)

Aynı hat (`tools/test-translate/`: `cfg.py` `A33`, `gen_a33.py`, `patch_a33.py`, `fixup_a33.py`, `regen_stubs.sh`); kaynak `rawstats` @ `6388c04`, dal `ccr-e90bca59-fxabvl`. `./gradlew -p logic compileTestKotlin` yeşil; kök Android `:orders`/`:capture` `compileDebugKotlin` yeşil. `-p logic test`: `capture` 100 test (97 kırmızı), `orders` 374 test (371 kırmızı), `core` 216 yeşil. Gövdeler A3.4–A3.6'da.

| Hedef | Hedef (belge 15.2) | Çevrilen | Dosya |
|---|---:|---:|---|
| `logic/orders` | 137 (+ M15/M17 taramaları 2) | **135** | `CashOrderK31K32Test` 16, `TipRecordF48Test` 5, `PocketCashM15Test` 3, `CashPendingM17Test` 4, `CashReconcileM17Test` 5, `BoostLogK27Test` 7, `BoostListM21Test` 4, `BoostTempE3Test` 4, `OfferWeatherK28Test` 2, `PlatformHistoryK36Test` 10, `PlatformListD15Test` 5, `PlatformTotalsF82Test` 6, `…F83Test` 4, `…F84Test` 7, `…F85Test` 2, `OverlayBadgeK35Test` 3, `DualOfferStatusK30Test` 3, `OfferNetLineTest` 4, `RestaurantBubbleE14Test` 4, `CustomerCardE16Test` 5 (3 uyarlanır), `CustomerBubbleF72Test` 7, `PhoneK25Test` 3, `AverageListE1Test` 5, `OrderHistoryTest` 9, `TodayAveragesTest` 2, `RecordPagesTest` 2, `OfferFeedV2Test` 4 (uyarlanır) |
| `logic/capture` | 6 | **6** | `NoteFieldsF71Test` (`NoteFields`) |
| Kaynak taraması (yazılmadı) | 24 (belge) | **20** + E2/E15 (A3.2'de 2) | aşağıda |
| Çevrilmez | 2 | 0 yazıldı | `OfferFeedV2Test.v1IsByteIdenticalForOffersOnScreen`, `e8ChangeKeyOverTheRealPayloads` |

Beklentiler Rawstats ile birebir; elle değişenler yalnız aşağıdakiler (`git diff` ile Rawstats testine karşı doğrulandı; `PlatformTotalsF82/84`, `BoostLog/List`, `K36/D15` vb. 0 satır fark).

**Sapmalar:**
- **Kaynak taramaları yazılmadı** (Android dosya adları A3.7/A3.8'de belli olur; dosya adları/dizgi aramaları WEEM dosyalarına yeniden yazılır): `TipNotificationF46Test` 4 (mesaj stili, `RemoteInput`, hızlı düğmeler, pencere), `BoostTempE3Test.cardTopShowsIt`, `PlatformTotalsF85Test.dayPageHasTheCard`, `OverlayBadgeK35Test.overlayUsesTheNamelessLinesAndHomeKeepsNames`, `OfferNetLineTest` (`analysisExportButtonSitsUnderKayitlarNextToTheFullExport` → A4.3, `accessibilityDescriptionSaysTheNumberStaysOnThePhone` → A3.7 `:capture`), `RestaurantBubbleE14Test` 2, `CustomerCardE16Test.oldNotificationTriggerIsGone`, `CustomerBubbleViewF73Test` 3, `AverageListE1Test.eachAverageOpensItsList`, `AutoStartNoticeTest` 1 (`:core`, A2.4 `Notices`/`HomeWatch`), "günlük yedeğe ve dışa aktarmaya girer" üçü (`PocketCashM15Test`, `CashPendingM17Test` `theLogIsBackedUpAndExported`; `PlatformTotalsF83Test.fileIsBackedUpAndExported`: `BackupFiles.logNames` + `tools/analysis/kl_export.py` dizgisi; `:core`). Belgenin "24"ü A3.2'de pür çıkan `ArrivalDistanceM20Test`'in 2 taramasını da sayıyordu: gerçek 22 (20 burada + E2/E15).
- **`AnalysisExport` ifadeleri A4.3'e (`:core`, R18)**: `CashOrderK31K32Test.theLogIsBackedUpAndExportedAnonymously` (yalnız `BackupFiles.logNames` kaldı; `AnalysisExport.kindOf(...) == KEEP_ALL` çıktı), `PlatformHistoryK36Test.cardsAndExports` (`kindOf` ve dışa aktarılan satırda `missed_by_kuryeplus:true`), `OfferNetLineTest` (`analysisExportDropsTheNetLine…` → **`theNetLineIsOneOfTheParsersOwnLines`**: yalnız `OwnLines.isOwn` yarısı; `AnalysisExport.isPanelLine` ve `AnalysisExport(...).screen(...)` ifadeleri `AnalysisExportTest`'e). A4.3 bu üç parçayı oraya yazmalı.
- **Ad değişiklikleri (bilerek farklar, ORDERS.md 16)**: `PlatformTotalsF85Test` "Rawstats:" → "WEEM:" (5 dizgi: `line`, `diffLine`); `BoostTempE3Test` "(YolGo'dan hava gelmedi)" → "(hava bilgisi gelmedi)", AZ "(hava məlumatı gəlmədi)" — **AZ ifadesi belgede yok, ben seçtim; kullanıcı doğrulamalı**; `CustomerCardE16Test` "(YolGo)"/"(YolGo, 280 m)" → "(WEEM)". `OverlayText.isOwn` → `OwnLines.isOwn` (`:capture`) `OverlayBadgeK35Test` ve `OfferNetLineTest`'te. Kayıt dizgileri (`missed_by_kuryeplus`, `weather_source`) aynı.
- **`CustomerCardE16Test`**: `YolGoNav.parse/State/Route` → `RouteStatus.fromNavFeed` (aynı yük metni, `onRoute` yok sayılır) / `RouteStatus.Route`; `CustomerCard.routeFor(state: RouteStatus?, since, now): RouteStatus.Route?`, `trigger/reason/live` `RouteStatus.Route` alır.
- **`OfferFeedV2Test`** → yeni **`OfferSummaries.of(active, offers, now, startedAtMs = Long.MIN_VALUE): List<OfferSummary>`** (`OrderEvents.offers` üreticisi; `MAX_UNSEEN_MS` 90 sn, `MAX_OFFERS` 8). 4 test: `ghostOffersAreNotExported`, `offersFromBeforeARestartWaitUntilSeenAgain` (`restartedAtMs` global'i yerine `startedAtMs` parametresi), `summaryCarriesTheV1AndV2Fields` (eski `v2PayloadExtendsTheV1Offers`: `offerKey`, `firstSeen/lastSeen`, ücret, mesafe, restoran, adres, `visibleNow`, `dropoffKnown`, `shown.approachMin/stops`, ekonomi alanları null/`unknown`), `atMostEightOffersAndOnlyKnownPlatforms` (eski `v2HasAtMostEightOffersAndOnlyKnownPlatforms`). `OfferSummary`'de olmayan: `feeBasis`, `producer`, `courierState`, `duringTripId` (sonuncusu `:economics`'in, A4.1). Araştırma çıktısı `KL_ROWS_OUT` dökümü çevrilmedi.
- Yeni iskelet sınıflar (`TODO`): `PocketCash`, `CashPending`, `PlatformTotals` (A3.6), `RestaurantBubble`, `CustomerCard`, `CustomerBubble`, `RecordPages` (etiket A3.6: A3.6'nın "bitti sayılır"ı **bütün A3.3 testleri**, balon/rozet/gün sayfası dahil), `OfferSummaries` (A3.5). Önceki iskeletlerin (`CashTips`, `BoostLog`, `OfferWeather`, `PlatformHistory`, `OrderHistory`, `OverlayText`) `A3.5` etiketi A3.2'den kaldı; sorumluluk bölüşümü `WEEM_ISLER.md`'deki görev tablosuna göre.

## A3.4 kod durumu (17 Ekim 2026)

- `logic/capture`: 129 test, **128 yeşil** (A3.2/A3.3'ten 100 + A3.4'te yazılan 29: `RegexPortabilityTest` 6, `OcrAcceptanceTest` 4, `NotificationDedupTest` 6, `TargetAppsTest` 3, `CaptureRowsTest` 4, `CaptureTextTest` 3, `NoNetworkTest` 3). Kırmızı kalan 1: `ScreenReadingE7Test.shiftServiceWarnsAndTheHomeShowsIt` (Android `:capture` uyarı dinleyicisi, `ShiftCard` satırı, `WatchService`/`NotifService` gövdeleri: A3.7).
- `logic/orders`: 374 test, 365 kırmızı (A3.5–A3.6 kodu; önce 371). `logic/core` yeşil.
- Kod: Rawstats @ `6388c04`'ten satır satır (`CAPTURE.md` 2); WEEM farkları yalnız `CAPTURE.md` 7.5 desen sınırları (cihaz sonucu aynı), `OwnLines` (sabitler `:orders` `OverlayText` için açık), `ScreenReadingGuard.NOTIFICATION_ID` 5104, `click.text` `redact`.

## A3.5 kod durumu (17 Ekim 2026)

- `logic/orders`: 383 test, **336 yeşil** (A3.2/A3.3'ten 374 + A3.5'te yazılan 9: `OverlayTextOwnLinesTest` 2, `OrderFlowTest` 7). A3.2 sipariş akışı testleri ve `SahaTekrarTest` (10/10) yeşil.
- Kırmızı kalan 47, hepsi A3.6 iskeleti (`TODO("A3.6")`): `PlatformTotalsF82Test` 6, `F83Test` 4, `F84Test` 7, `F85Test` 2, `CashReconcileM17Test` 5, `CashPendingM17Test` 3, `PocketCashM15Test` 3, `RecordPagesTest` 2, `RestaurantBubbleE14Test` 4, `CustomerBubbleF72Test` 6, `CustomerCardE16Test` 5.
- Kod: Rawstats @ `6388c04`'ten satır satır; farklar `ORDERS.md` 16.1. `logic/capture` 128/129 (kalan A3.7), `logic/core` 216 yeşil; kök `:orders`/`:capture` `compileDebugKotlin` yeşil.

## A3.6 kod durumu (17 Ekim 2026)

- `logic/orders`: 387 test, **hepsi yeşil** (A3.5'in 47 kırmızısı A3.6 gövdeleriyle yeşile döndü + `OrderFlowTest` +4: resmî toplamlar/CASH PENDING, vardiya kancaları, E14, E16).
- Kod: Rawstats @ `6388c04`'ten satır satır; farklar `ORDERS.md` 16.2. `logic/capture`'ta tek kırmızı `ScreenReadingE7Test.shiftServiceWarnsAndTheHomeShowsIt` (A3.7).

## A3.7 durumu (17 Ekim 2026)

Android `:capture`/`:orders` katmanı (dal `ccr-e90bca59-fxabvl`). Kaynak taramaları WEEM dosyalarına uyarlandı (`ORDERS.md` 16.3, `CAPTURE.md` 8 "A3.7 durumu"): `logic/capture` `ScreenReadingE7Test.shiftServiceWarnsAndTheHomeShowsIt` yeşile döndü, yeni `NoGestureTest` 4, `CaptureCrashGuardTest` 6; `logic/orders` `CustomerBubbleViewF73Test` 3, `TipNotificationF46Test` 4, `RestaurantBubbleE14Test` +2, `CustomerCardE16Test` +1, `ReadinessE15Test` +1, `OverlayBadgeK35Test` +1, `OrdersWiringTest` 3; `logic/core` `CrashGuardTest` içe aktarma kancası satırı. Sayılar: core 216, capture 139, orders 402 — **hepsi yeşil**. Kök `test lintDebug assembleDebug assembleRelease` yerelde yeşil; `.dev` APK kimliği `ci/check_identity.sh` ile doğru. Kalan taramalar: A3.8 (gün sayfası, sipariş kartı, ayarlar: `RestaurantWaitE2Test.cardAndListShowIt`, `BoostTempE3Test.cardTopShowsIt`, `PlatformTotalsF85Test.dayPageHasTheCard`, `AverageListE1Test.eachAverageOpensItsList`, E14/E16/K35 ayar ve ana sayfa kısımları), A4.3 (yedek/dışa aktarma).

## A3.8 durumu (17 Ekim 2026)

Siparişler sekmesi (Compose; dal `claude/a3-8-orders-tab-vfg3r9`, `ORDERS.md` 16.4). A3.7'nin A3.8'e bıraktığı dört kaynak taraması **modelin çıktısına** yazıldı (`OrdersScreenTest`): `PlatformTotalsF85Test.dayPageHasTheCard` → `dayPageHasTheCard` + `matchingValueIsPlainAndEmptyDayShowsTheHint` (kart, kırmızı fark, "WEEM:", fark satırları, boş gün yönergesi), `BoostTempE3Test.cardTopShowsIt` → `cardTopShowsIt` (boost/sıcaklık satırı zaman çizelgesinden önce, listenin ikinci satırı `listLine`), `RestaurantWaitE2Test.cardAndListShowIt` → `cardAndListShowIt` (restoran beklemesi zaman çizelgesinden önce, "Bekleme: 0 dk" yok, ölçülmediyse "—" ve neden), `AverageListE1Test.eachAverageOpensItsList` → `eachAverageOpensItsList` (beş ortalamanın beşi de kendi listesini açar). Ana sayfa adları (K35 yarısı) `OrdersTabWiringTest`. Yeni: `OrdersScreenTest` 17 (+ sayfa sırası/başlık/süzgeç/arşiv/nakit/kaçırılan/boost/sipariş kartı/saklı kaynak), `OrdersTabWiringTest` 4, `:app` `OrdersTabTest` 4 + `RecordsScreenTest` 1 (Robolectric + Compose, JVM). Sayılar: `logic/` core 216 · capture 139 · orders 423 (402 + 21) hepsi yeşil; `:app` 5 yeşil. E14/E16 ayar kısımları (Ayarlar ekranı) ve kayıt paylaşımı yazılmadı (S20).

## A5.2 durumu (17 Ekim 2026)

`logic/scooter` (`SCOOTER.md` 13.4): Sürüş Paneli'nin **14 suiti** Kotlin'de, hepsi yeşil; çağrı yeri sayıları eskiyle aynı (**336**, `SuiteCountTest`), çalışma sayıları da (Timing 9, Protocol 408, Link 33, PairFlow 37, Sweep 24, PowerCadence 49, SpeedCadence 61, BatteryProbe 854, Dashboard 14, EventQueue 13, Energy 20, TripMeter 19, Floating 22; LogPolicy basmaz, 20.015). EventQueue/LogPolicy/Energy/TripMeter (plan A5.3) ve Floating (plan A5.4) da A5.2'de çevrildi ve sınıflarıyla yeşil (kullanıcı kapsamı "14 suit / 324"). Rawstats `ScooterReconnectM23Test` 4 yeşil. WEEM ekleri: `PairingVaultTest` 6 (`KeyVaultInstrument` kasa denetimleri JVM'de; cihaz testi eklenmedi, `SCOOTER.md` F-S6), `ScooterGuardTest` 6, `LinkRulesTest` 3. Python `reference_parity.py` Kotlin'e karşı elle: 320/320 (`tools/scooter-reference-parity.sh`). Kalan `:scooter` testleri A5.3: `ScooterBridgeTest` 6, `ScooterFeedTest` 5 (3 uyarlanır, 2 çevrilmez), `ScooterSamplesTest` 5 (1'i `:core`'a), `ScooterOrderEnergyTest` 7 (`:economics`); özet tablodaki `:scooter` 344 → 341 düzeltmesi bunlarla A5.3'te. `LogExportTest` gizlilik denetimleri A5.3 (`LogExport` saf). **A5.4 (18 Ekim):** WEEM eki `ScooterUiTest` 23 (Scooter sekmesi, yüzen panel ve ayarların saf kuralları, TR/AZ metinler; NB arayüzünün testi yoktu); `CrashGuardTest` scooter servisinin reddedilen ön plan başlatmasını da denetler.

## A5.3 durumu (17 Ekim 2026)

`logic/scooter` + `logic/orders` (`SCOOTER.md` 13.5): Rawstats `ScooterSamplesTest` (4; yedek/dışa aktarma ifadeleri `:core`/A4.3'e), `ScooterBridgeTest` (6), `ScooterFeedTest` → `ScooterLiveMapperTest` (3 + 1; sağlayıcı testleri çevrilmedi, S7), `ScooterOrderEnergyTest` (7: adım payı 3 test `ScooterSamplesTest`'te, sipariş payı 5 test `logic/orders`'ta) hepsi yeşil. Eski androidTest `LogExportTest` JVM'de (`LogExportTest` 7 test: 12 gizlilik denetimi + ZIP düzeni). WEEM ekleri: `ScooterLinkRulesTest` 7, `ScooterTelemetryTest` 5, `ScooterContractTest` 5. Cihazda doğrulanmadı.

## A4.3 durumu (18 Ekim 2026)

R17/R18 Rawstats testleri Kotlin'de yeşil: `logic/core` `RedactorParityTest` 4 (`economics/redact_vectors.json`), `logic/orders` `AnalysisExportTest` 13, `ExportKeysParityTest` 1 (WEEM `tools/analysis/anonymize.py`); A3.3'ün bıraktığı ifadeler yerine kondu: `CashOrderK31K32Test`, `PlatformHistoryK36Test` (`missed_by_kuryeplus`), `OfferNetLineTest.analysisExportDropsTheNetLineWithTheParsersRecogniser` (ad geri geldi), `PlatformTotalsF83Test.fileIsBackedUpAndExported`, `PocketCashM15Test`/`CashPendingM17Test.theLogIsBackedUpAndExported` (`kl_export.py` dizgisi), `CustomerStageK34RecordTest` 2 (`:core` R18 payı). `DailyPackTest` 2 → `BackupExportTest.dailyPackNameFolderAndDays` (WEEM adları) + `RecordExportWiringTest.shiftEndPacksAnonymousExportWithoutNetwork`; `analysisExportButtonSitsUnderKayitlarNextToTheFullExport` → `RecordExportWiringTest`. Yeni: `BackupExportTest` 6 (biçim kilidi, geri yükleme), `AnalysisToolsTest` 3 (`python3 -I` + `kl_export.py`; python yoksa atlanır), `EncryptedBackupTest` 7 (N3), `RecordExportWiringTest` 4. Kalan: `OfferScoreE4Test` ve `EconomicsLogsTest` dışa aktarma satırları A4.1 (`:economics`); `ScooterSamplesTest` `kindOf` satırı `:scooter`'dan görülemez, `AnalysisToolsTest.everyDayFileHasAnAnonymousExportRule` bütün `logNames`'i kapsar; `LogFeedV3Test` Y19 (Aşama 6).

## A4.1 durumu (18 Ekim 2026)

R4 Rawstats testleri `logic/economics`'te yeşil (ayrıntı `ECONOMICS.md` 5): `EconomicsLogsTest` 21, `EconomicsObservationTest` 13 (2'si feed v1 yerine `OfferSummaries`, S7), `EconomicsV2GoldenTest` 8 + `EconomicsV2PriorsTest` 4 (altın vektörler birebir: 6 ondalık/1e-6, Python yuvarlaması), `OfferGeoTest` 12, `OfferScoreE4Test` 8 (kaynak taraması WEEM dosyalarına), `ShadowEvaluatorTest` 11, `WaitObservationProcessDeathTest` 5, `EnergySegmentsTest` 4 = **86**. A3.x'in bıraktığı ekonomi payı: `CoreLifecycleV2EconomicsTest` 8, `CustomerStageK34RecordLegsTest` 1, `VenueArrivalD9LegsTest` 1, `TripStagesE8EconomicsTest` 2 (uyarlandı: `OrderStages`), `CustomerStageK34LateDeliveryLegsTest` 7 (kaydedicili kopya; 6 `legs` ifadesi) = **19**. Yeni: `OfferWindowsTest` 1 (`OfferWindows` = `PRIORS`), `SahaEconomicsTest` 4 (6.481 saha karesinde 16 ekonomi dosyasının her satırı Rawstats'la aynı; fikstür `tools/shadow-fixture --economics`), `OfferScorePageTest` 3, `:orders` `OrdersScreenTest.offerScoreLineOpensTheOfferDetails` 1, `:app` `OrdersTabTest.offerScoreTabRatesAnOrder` 1 (Robolectric). `RestaurantWaitE2Test.cardAndListShowIt` ekonomi değil (sipariş kartı; `OrdersScreen.tripCard`'ın E2 satırı `OrdersScreenTest`'te), `ReadinessE15Test` taraması A3.7'de yazılmıştı.

- **R4 sayısındaki YolGo Dart dosyaları** `offer_engine_test` (7) ve `route_costing_test` (6) YolGo'nun kendi teklif/rota maliyetidir (rota motoru Y3, batarya Y9): **A6.7'ye (N5)** kalır (`OZELLIK_HARITASI` N5 aynı 13 testi sayar).
- `ScooterOrderEnergyTest` (7) A5.3'te çevrildi (`SCOOTER.md` 13.5).
- `CrashGuardTest.refusedForegroundStartStillReachesStartForeground` (A4.3 + A5.3 birleşmesinden beri kırmızıydı: `ScooterService` başlatışını vardiya servisi sayıyordu) düzeltildi.

**A4.2 (18 Ekim, yeni testler; Rawstats'ta karşılığı yok):** `logic/economics` `DayReportTest` 10 + `DaySummariesTest` 1; `logic/orders` `OrderSettingsTest` 5 + `OrdersScreenTest.daySummaryShowsTheReportAndSubtractsEnergyCostFromTheHeroNet`; `:app` Robolectric `OrdersSettingsScreenTest` 5 + `OrdersTabTest` 2 rapor testi.

## A8.1–A8.2 durumu (18 Ekim 2026)

N1 yeni (eski uygulamalarda karşılığı yok); `logic/link` **131 test** yeşil (`LINK.md` 1.8, 3.14). A8.1 taşıma: `BackoffTest` 4, `DiscoveryTest` 8, `FramesTest` 10 (A8.2'de `HELLO` v2 + 2 test), `LinkMachineTest` 25 (A8.2'de eşleşmiş güvenle), `LinkModuleTest` 1. A8.2 güvenlik ve teslim: `NoiseVectorsTest` 3 (resmî Noise vektörleri: cacophony + snow), `NoiseP256VectorsTest` 3 (bağımsız Python referansı `tools/link-noise-reference.py`, `python3 -I`, `cryptography`/OpenSSL; önce resmî vektörleri doğrular), `P256Test` 4, `PairingQrTest` 8, `EnvelopeTest` 4, `ClockSyncTest` 3, `IdentityVaultTest` 9, `MessengerStoreTest` 4, `MessengerTest` 20, `SecureLinkTest` 16 (`FakeNet`: eşleşme, yanlış/süresi geçmiş/kullanılmış jeton, araya giren, bozulmuş ve tekrarlanmış şifreli metin, sürüm, kaldırma), `MessengerLinkTest` 9 (`FakeNet`: kuyruklar, onay, yeniden başlatma, Wi-Fi ↔ Bluetooth tekrarları, saat farkı). Eski `FeedChangesE8Test` (8, hedef `:link`: içerik anahtarı, ≥ 200 ms, kalp atışı) mesaj temposudur: **A8.3**'te çevrilir. Android `:link` (`LinkVault`, `LinkRuntime`) için birim testi yok; cihazda doğrulanmadı.

## A6.2a çeviri durumu (19 Ekim 2026)

YolGo testleri (`yolgo-test` @ `870ab6e`) `logic/map` testlerine çevrildi; hedef `MAP.md` 16.2. `./gradlew -p logic :map:test`: **110 test, 110 kırmızı** (hepsi `TODO("A6.x")` iskeletinden; derleme hatası yok); `-p logic compileTestKotlin` ve kök `:map:compileDebugKotlin` yeşil. Girdi graflar `route/TestGraphs.kt` (Dart `_graph` yardımcıları, her birinin adı kaynağı gösterir). İskelet gövdesiz imza: `geo` (tam: saf formüller), `graph` (`OsmRaw` tam; `RoadGraph`, `GraphBuilder`, `Overpass`, `PoiSet`, `MapDataState`, `AtomicFile` iskelet), `route` (`RideParams` tam, `CostModel`, `Router`, `Planner`, `RouteEngine`, `Quality`, `ReverseHours`, `StreetPrefs`, `BikewayMarks`), `roads` (`RoadKnowledge`, `RoadLearner`, `RoadDayContributions`, `RoadBook`, `VolumeDispatch`), `settings` (`MapSettings`, `MapSettingsCodec`, `MapPrefs`), `surface` (`SurfaceMark`), `ui` (`BikewayMenu`, `BikewayLayerRules`, `Toasts`), `nav` (`NavController` alt kümesi).

| Hedef paket | Çevrilen | Dosya (test sayısı) | Yeşil yapan |
|---|---:|---|---|
| `graph` | **17** | `OverpassProblemTest` 3, `MapDataStateTest` 1, `AtomicFileTest` 1, `PoiSetTest` 4, `ParkPathsTest` 2, `BikewayTagsTest` 3, `RoadGraphFileTest` 3 | A6.3 (POI A6.8) |
| `route` + `settings` + `ui` + `nav` + `roads.StreetRatio` | **70** | `RouterTest` 22, `StartWalkOutTest` 3, `PlannerTest` 2, `RouteEngineTest` 5, `ParkPathsTest` 2, `PushSegmentsTest` 5, `ReverseHoursTest` 3, `WrongWayCostTest` 4, `BikewayCostTest` 5, `BadSpotCostTest` 1, `QualityTest` 9, `MapSettingsTest` 3, `BikewayMarksTest` 1, `BikewayMenuTest` 1, `BikewayLayerRulesTest` 1, `NavControllerBikewayReplanTest` 2, `StreetRatioTest` 1 | A6.4 (bisiklet işareti/menü/katman A6.8, yeniden rota A6.6, cadde oranı A6.11) |
| `roads` | **23** | `RoadLearnerTest` 9, `RoadBookTest` 1, `DayContributionsTest` 2, `WwCleanRunTest` 5, `WwAnswerFlowTest` 5, `RoadNamesTest` 1 | A6.11 |

**Sapmalar (kaynak: Dart testi; her biri kodda KDoc'ta da yazılı):**
- **Çevrilmeyenler `MAP.md` 16.2'deki gibi:** `router_test` 7 (Y13 yüzey: "physical bad segment", "passable rough-only", "1.7.2" +200 m, E11 grubu 4), `street_scores_test` 11, `day_contributions_test` 1 (`SurfacePassTracker`), `bikeway_e13_test` 1 (`BikewayTab`, A6.2b). `street_prefs_migration_test` çevrilmez (S1).
- **`StreetRatioTest`** yalnız nokta satırları (boş → 400 m; tek nokta 50 m, %13, metinde "%13"): ölçülmüş 50 m + iki nokta ≈ 110 m ve güveni 0,2 ölçümün sayılmaması `SurfaceSummary` ister (Y13, SORU-Q1) → sonra.
- **`BadSpotCostTest`:** Dart `SurfaceMark.fromJson({…, 'removed': true})` → `copy(removed = true)`.
- **`MapSettingsTest`:** `AppSettings` → `MapSettings` + `MapSettingsCodec`; `autoStartDay`/`autoEndDay`/`followCourierStages` ve `voice` iddiaları düştü (WEEM ayarında yok, `MAP.md` 14.2).
- **`WwAnswerFlowTest`:** GPS → `NavController` → `RoadController` yolu yerine saf sahte eşleştirme (`nearestEdge` + tek yön bayrağı + yön farkı) ve `VolumeDispatch`; "yeşil çizgi sayısı" yerine onaylı kenar sayısı; TR/AZ kart testi saf (`WrongWayCardText` + akış). **Kartın Compose dokunuşu (`:app` Robolectric `WrongWayCardTest`) A6.11'de ekranla birlikte eklenir** (`MAP.md` 9.11 bunu A6.2a'ya yazmıştı; ekran iskeleti olmadan `:app` testi yazılamadı).
- **`NavControllerBikewayReplanTest`** (Dart `bikeway_e13_test` "uygulamada" 2): Riverpod/geolocator/ekran yerine saf `NavController` alt kümesi (`setGraph/setOrigin/setSettings/setBikewayMarks/goTo`, `state`); asıl durum makinesi A6.6'da, bu iki test onun sözleşmesidir.
- **API seçimleri (A6.3/A6.4 değiştirebilir, testler aynı commit'te güncellenir):** OSM way id `Long`; zaman epoch ms (`Long`) ya da `Instant`; POI türü `Char`; `FormatException` kendi sınıfı (`graph`); `RoadGraph` dizileri `DoubleArray/IntArray/FloatArray/LongArray` ve `u8/u16` için `ByteArray/ShortArray`; `bikewayMapWarning` → `BikewayText.mapWarning(graph, Lang)`; "çökme" benzetimi `RouteEngine.debugKillWorker()`; atomik yazma `AtomicFile.write(File, ByteArray)`.
- **Ortam notu:** bulut kabuğu `POSIX` yerel ayarlıdır; Türkçe test adlarından üretilen lambda sınıfları (`$1.class`) dosya adı kodlamasında kırılır → `LC_ALL=C.UTF-8 ./gradlew …` (daemon'u bu ortamla yeniden başlat: `./gradlew --stop`). Test adlarında `.` `;` `:` `/` `[` `]` `<` `>` olamaz (JVM yöntem adı).

## A6.2b çeviri durumu (19 Ekim 2026)

YolGo ve Rawstats testleri (`yolgo-test` @ `870ab6e`, `rawstats` @ `6388c04`) navigasyon, adres, ekran, ışık, batarya, hava, görev, kayıt ve veri seti için çevrildi; hedef `MAP.md` 16.3. **256 test** (`MAP.md` 16.5: 246 + `LogFeedV3Test` 10), hepsi iskelet `TODO("A6.x")` yüzünden kırmızı (derleme hatası yok). Dal `claude/a6-2b-c2pozd` (A6.2a dalı `ccr-4340bbea-jlvo9l` içine birleştirildi; A6.2a + A6.2b = 366 test). Doğrulama: `LC_ALL=C.UTF-8 ./gradlew -p logic test --continue` → `:map` 298 test (297 kırmızı; `MapModuleTest` yeşil), `:orders` 26 kırmızı (öteki 481 yeşil, bozulan yok); Android: `./gradlew :app:testDebugUnitTest --tests 'com.kuryelog.app.map.*'` 37 ve `:map:testDebugUnitTest` 6, hepsi `NotImplementedError` ile kırmızı (Robolectric bu ortamda koşar); `:app:lintDebug`, `:map:lintDebug` yeşil.

| Hedef | Çevrilen | Dosya (test sayısı) | Yeşil yapan |
|---|---:|---|---|
| `logic/map` `input` | **32** | `AddressParserTest` 16, `PlusCodeTest` 2, `IncomingLinkTest` 4, `GeocoderTest` 1, `ApiChecksTest` 1, `PlaceSearchTest` 6, `PlacesRestTest` 2 | A6.6 |
| `logic/map` `nav` | **37** | `ApproachZoomTest` 4, `ArrivalFocusTest` 4, `NavControllerArrivalTest` 3, `FixGateTest` 2, `RerouteGateTest` 4, `RouteOriginTest` 3, `HeadingFilterTest` 1, `NavOnlyTest` 2, `RouteProgressTest` 6, `RouteStatusGateTest` 4, `NavControllerOriginTest` 3, `NavAssistantTest` 1 | A6.6 |
| `logic/map` `ride`, `weather`, `ui` | **23** | `EnergyOdometerTest` 5, `SpeedOptionsTest` 4, `BatteryEstimatorTest` 1 · `WeatherReportTest` 5, `WeatherSummaryTest` 2 · `LocateButtonTest` 2, `PuckStyleTest` 3, `GoogleMapStyleTest` 1 | A6.7 (`ui` A6.5) |
| `logic/map` `day` | **71** | `ShiftEngineTest` 12, `ShiftEngineStagesTest` 8, `LightTrackerTest` 7, `LightLeaksTest` 3, `LightRuleTest` 8, `LongLightTest` 5, `StopSemanticsTest` 1, `DayStateJsonTest` 1, `CourierStageFollowerTest` 11, `StageFeedTest` 3, `CourierStageTimelineTest` 11, `DayControllerTest` 1 | A6.8 |
| `logic/map` `logs`, `stats`, `tasks`, `reminders` | **24** | `RideLogStoreTest` 5, `RideLogWriterTest` 3, `RouteRecorderTest` 2, `RideLogStatusTest` 1 · `DayReasonsTest` 1 · `TaskTriggerTest` 5, `TaskControllerTest` 1 · `ReminderDueTest` 4, `ReminderSettingsTest` 1, `ReminderTimingTest` 1 | A6.9 |
| `logic/orders` `dataset` | **26** | `DatasetContractTest` 6, `DatasetPackTest` 4, `DatasetMeasureTest` 1, `OfferRowGateTest` 3, `DatasetDaysTest` 2, `DayLogSnapshotTest` 10 (Rawstats `LogFeedV3Test`) | A6.9 |
| `:app` Robolectric (`com.kuryelog.app.map`) | **37** | `MapShiftActionsTest` 5, `MapMetricsTest` 7, `SpeedSheetTest` 1, `MapControlsTest` 2, `SmartLocateButtonTest` 1, `MapHudLayoutTest` 2, `WeatherChipTest` 2, `WeatherDetailSheetTest` 1, `MapWeatherSettingsTest` 4, `MapTabLayoutTest` 1, `MapTabTest` 2, `RideLogStatusTextTest` 1, `ReminderCardTest` 2, `BikewayTabTest` 1, `DatasetScreenTest` 5 | A6.5 (ayar/hava/metrik A6.7, kayıt/hatırlatıcı/veri seti A6.9, bisiklet A6.8) |
| `:map` Robolectric (`com.kuryelog.app.map`) | **6** | `HomePickScreenTest` 5, `LocationPuckTest` 1 | A6.5 |

**İskelet** (gövde `TODO("A6.x")`, Kotlin KDoc'unda kaynak ve bölüm): `logic/map` yeni paketler `net` (`HttpPort`), `input`, `ride`, `weather`, `day`, `logs`, `stats`, `tasks`, `reminders`; `nav` genişledi (`NavController` + `Stop`, `NavFix`, `RouteProgress*`, `RouteStatusGate`, `RerouteGate`, `ApproachZoom`, `ArrivalFocus`, `FixGate`, `NavOnly`, `HeadingFilter`, `RouteOrigin`); `ui` (`LocateButtonRules`, `PuckStyle`, `GoogleMapStyle`, `HudLayout`); `logic/orders` `dataset`; `:app` `com.kuryelog.app.map` ekran iskeletleri; `:map` `HomePickScreen`, `LocationPuck`. **Yapı değişiklikleri:** `:app` → `implementation("com.kuryelog.logic:map")` ve `:core`; `:map` Compose + Robolectric bağımlılıkları (`build.gradle.kts`); `MapPrefs`'e `getDouble/setDouble/getLong/setLong` (A6.7 `battery.*` anahtarları).

**Sapmalar ve uyarlamalar (kaynak: Dart/Kotlin testi; her biri KDoc'ta da yazılı):**
- **"Yeni" (karakterizasyon) testler yazılmadı** (`MAP.md` 16.3 son iki satır ve her bölümün "Yeni" satırı: `MapStylesTest`, `PuckMotionTest`, `LongPressMenuTest`, `PassiveSessionTest`, `RegionInfoSheetTest`, `LightsDbTest`, `StopReconcilerTest`, `RoadInfoBuilderTest`, `HomeReturnTest`, `DayShiftSyncTest`, `DayTextTest`, `StageFeed` null, `stats.*` (5), `DatasetRecorder/Manual/Tool`, `TaskTargetTest`, `ReminderControllerTest`, `AppAlertsTest`, `StatsScreenTest`, `RideLogsScreenTest` …) — A6.2a ile aynı karar: eski testi olmayan, değerleri bölümlerde yazılı olanlar ilgili uygulama görevinde (A6.5–A6.9) kodla birlikte yazılır.
- **Riverpod/sağlayıcı testleri saf denetleyiciye çevrildi:** `ArrivalFocusTracker`, `NavOnlyController`, `RouteProgressTracker` + `RouteStatusGate` (yük JSON'u yerine `logic/core` `RouteStatus`; `onRoute` iç alan), `FixGate`, `NavController` (`state`, `progress`, `routeStatus`, `setOrder`, `onFix`, `tick`, `onExternalText`, `search`; `debugSetState` test kancası), `DayController`, `TaskController` (`TaskNavGate`), `RideLogWriter` (`java.io`, `Clock`). Saat epoch ms; sanal saat `FakeClock`/`VirtualDelay`; ağ `FakeHttp`.
- **`NavAssistantTest`:** üç saf denetleyici (`NavController`, `DayController`, `TaskController`) test kodunda bağlanır; çalışma zamanı dikişi (`DayRuntime`: teslim olayı → `TaskController.deliveryFinished`) A6.9'da.
- **Aşama izleyici:** feed v2 zarfı yok (S7); girdi `FollowerFeed(trips, stages)` (`stages` = `logic/core` `TripStages.Trip`); ayrıştırma testleri `StageFeedTest`'te `TripStages` → `FollowerFeed` eşlemesine uyarlandı. `DayControllerTest` toast "Restorana vardın — bekleme başladı" (M-11-1), ayar kısmı düştü.
- **Gün motoru:** `DayState`/`StopRec`/`OrderRec` değişken sınıflar (Dart gibi), JSON anahtarları `VERI_SOZLESMESI` adlarında; `DayFix`, `RoadInfo` (`atLight` kuralı F9.2). F9.1 göç testleri çevrilmez (16.4).
- **`PlaceSearchTest`:** debounce `VirtualDelay` ile (350 ms), `SocketException`/`StateError`/`TimeoutException` → `IOException`/`IllegalStateException`/`SocketTimeoutException` (+ `ServiceConfigurationError`); paket beklentisi `com.kuryelog.app`.
- **`DayLogSnapshotTest`:** sağlayıcı ve sayfalama yok; 7. test bütün dosyanın bayt/hash/satırı, 10. test yalnız gün ve yol denetimi; `build` çıktıyı doğrudan `out` klasörüne yazar. `DatasetContractTest`/`DatasetDaysTest` gün sınırı `ZoneId` parametresi (telefon dilimi, test `Asia/Baku`).
- **Compose (Robolectric):** `Key('…')` → `testTag("…")` aynı dizge; dil/tema/aşama döngüleri tek test içinde durum değiştirerek (tek `setContent`); `MapShiftActions` "Günü Başlat yok" iddiası düştü (WEEM `ShiftCard`'ta başlat var, G1); `MapTabTest` ayar simgesi ve menü yokluğuna uyarlandı (Ayarlar kabukta); `MapWeatherSettingsTest` 3–4 Harita ayarları → "Hava durumu" yapısına; `DatasetScreenTest` açıklama M-13-2 metni ve sahte `DatasetRuntime`; `WeatherDetailSheetTest` klavye yerine `availableHeightDp`; `HomePickScreenTest` sahte yüzey ve kök etiketi `home-pick-dark` (tema parlaklığı Compose'ta okunamaz); `RideLogStatusText`/`ReminderCard`/`BikewayTab` saf durum ve geri çağrılar.
- **Çevrilmeyenler ve sonraya kalanlar `MAP.md` 16.4'teki gibi** (yeni bir çevrilmeyen yok); `:map` testleri spec'teki yerinde (`HomePickScreenTest`, `LocationPuckTest`).
- **Ortam notu:** Android Gradle ilk indirmede Maven Central'dan 429 verebilir → `./gradlew … --max-workers=1` ile yeniden dene (ikinci denemede önbellek dolar). `logic/` yine `LC_ALL=C.UTF-8 ./gradlew -p logic …`.

## A6.3 kod durumu (19 Ekim 2026)

Y7 harita verisi uygulandı (`MAP.md` 2, uygulama notu 2.16); dal `ccr-1aad2834-1yglam` (← `claude/a6-2b-c2pozd`). A6.2a `graph` testlerinden A6.3'ünkü **13/13 yeşil** (`OverpassProblemTest` 3, `MapDataStateTest` 1, `AtomicFileTest` 1, `ParkPathsTest` 2, `BikewayTagsTest` 3, `RoadGraphFileTest` 3); `PoiSetTest` 4 kırmızı kalır (A6.8). Yeni testler **25**: `GraphCharacterizationTest` 12 (`MAP.md` 2.13 "Yeni" a–e: KNG1 bayt düzeni ve ad bölümü, sıklaştırma/3'lü ortalama/`minGradeRun`/ad önceliği, düğüm seçimi ve döngü kenarı, komşuluk, geometri aralığı, HGT örnekleme ve boşluk, 91/28 karo ve parça adı, `nearestEdge` yön süzgeci ve `contains` payı, bozuk dosya, Overpass sorgu metni), `MapDownloadTest` 10 (aynalar ve bekleme, Overpass yolu, kaldığı yerden devam, 2 gün kuralı, hazır veri sunucusu, gömülü harita, okunamayan dosya, güncelleme hatasında eski harita, indirme sırasında eski graf), `MapBuildTest` 3 (SRTM maskesi, manifest, hata). `:map` Robolectric `MapDataTest` 2 (`MapPrefsStore`, `MapData` yer tutucu manifest). Karakterizasyon değerleri Dart kodundan satır satır çıkarıldı; **bu ortamda Dart yok, YolGo'nun ürettiği bir KNG1 dosyasıyla bayt bayt kıyas doğrulanmadı** (biçim alanları ve hizalar `MAP.md` 2.5 ile birebir; risk 2.16).

## A6.4 kod durumu (19 Ekim 2026)

Y3 rota motoru ve Y8 sokak kararları uygulandı (`MAP.md` 3, 4; uygulama notları 3.17, 4.9, ölçüm 3.13); dal `ccr-834f2d0f-ja8gqf` (← `ccr-1aad2834-1yglam`). A6.2a `route` + `settings` testlerinden A6.4'ünkü **64/64 yeşil**: `RouterTest` 22, `StartWalkOutTest` 3, `PlannerTest` 2, `RouteEngineTest` 5, `ParkPathsTest` 2, `PushSegmentsTest` 5, `ReverseHoursTest` 3, `WrongWayCostTest` 4, `BikewayCostTest` 5, `BadSpotCostTest` 1, `QualityTest` 9, `MapSettingsTest` 3. Kırmızı kalanlar başka görevin: `BikewayMarksTest` 1, `BikewayMenuTest` 1, `BikewayLayerRulesTest` 1 (A6.8), `NavControllerBikewayReplanTest` 2 (A6.6), `StreetRatioTest` 1 (A6.11). Yan etki: ayar kodeği ve `Stop` sayesinde `LightRuleTest` 1, `SpeedOptionsTest` 1, `NavControllerOriginTest` 1 yeşile döndü (geri kalanları A6.6/A6.7/A6.8).

**Yeni testler 12:** `StreetPrefsTest` 7 (`MAP.md` 4.6 "Yeni": işlem tablosu, kodek bozuk öğeler, yazma sırası, "Geri al" dizisi), `RouteEngineTest` +1 (zaman aşımı → `RouteTimeout`, takılan hesap iptal, kuyruk kilitlenmez; iptal denetimi kapatılınca kırmızı), `RouterGoldenTest` 3 (YolGo `870ab6e` ile birebir: KNG1 baytları, 500 bacak, 135 plan, A* sayaçları; veri `route/golden-v1.json.gz`, üreteç `tools/route-golden/`), `RouterPerfTest` 1 (`MAP.md` 3.13: sentetik ızgara, süre raporu, çekiş üst sınırı, en kötü durumlar; `WEEM_PERF_GRID` ölçeği). "Yeni" satırındaki yığın eşitlik sırası ve hata kodu → TR/AZ tablosu ayrı test yerine `RouterGoldenTest` eşit maliyet şehri (yığın kuralı bozulmaları fark verir) ve hata/uyarı metinlerinin YolGo çıktısıyla harfiyen karşılaştırılmasıyla karşılandı; AZ metinleri `RouteMsg`'de 3.12 tablosundan (ekran testleri A6.6).

**Sapmalar:** `RouteLeg.warnings`, `Plan.warnings`, `PlanResult.error` kodlu `RouteMsg` (YolGo `String`): `StartWalkOutTest` ve `PushSegmentsTest` birer satırda `.tr` ile güncellendi. Y13 testleri (7) ve `street_prefs_migration_test` (1) çevrilmedi (SORU 1 (a), SORU 2).

**Doğrulama:** `LC_ALL=C.UTF-8 ./gradlew -p logic test --continue` → **1.591 test, 243 kırmızı** (`:map` 217 + `:orders` `dataset` 26; hepsi `NotImplementedError`, başka görevlerin iskeleti; önceki 310'dan 67 az); kök `:app`/`:map` `compileDebugKotlin` + `compileDebugUnitTestKotlin` yeşil (`--max-workers=1`, Maven Central 429 sonrası). CI koşusu yok (`[skip ci]`), telefonda denenmedi.

## A6.5 kod durumu (19 Ekim 2026)

Y1 harita ekranı ve Y2 ikinci görünüm uygulandı (`MAP.md` 6; uygulama notları 6.17, APK ölçümü 6.4); dal `ccr-730564f3-432vti` (← `ccr-834f2d0f-ja8gqf`). A6.5'in çevrilmiş testleri: `logic/map` `ui` 6/6 (`LocateButtonTest` 2, `PuckStyleTest` 3, `GoogleMapStyleTest` 1), `:app` Robolectric 11/13 (`MapShiftActionsTest` 5, `MapControlsTest` 2, `SmartLocateButtonTest` 1, `MapHudLayoutTest` 2, `MapTabTest` varış odağı 1), `:map` Robolectric 6/6 (`HomePickScreenTest` 5, `LocationPuckTest` 1) → **23/25 yeşil**. Kalan 2 (`MapTabLayoutTest` 1, `MapTabTest.anaEkranMenusuzKayitNoktasiYok` 1) ana ekranda `WeatherChip`/`MapMetrics` çizer: gövdeleri A6.7'de (`TODO("A6.7")`), A6.7 ile yeşile döner.

**Yeni testler 14** (`MAP.md` 6.14 "Yeni"): `MapStylesTest` 3 (altlık seçimi, eski değerler ve anahtarsız `google` → vektör, atıflar, OSM raster koyu ters çevirme, User-Agent, kamera sınırları, POI eşikleri), `MapPaletteTest` 2 (`forGrade` %3/%8, paletler), `PuckMotionTest` 5 (süzgeç, ilk okuma sıçraması ve kamera yönü, kayma süresi, ileri tahmin, yola oturtma eşikleri ve yol yönü, yön filtresi), `LongPressMenuTest` 3 (koşullar, sıra, metinler TR/AZ), `PassiveSessionTest` 1.

**A6.8'den öne alınan:** `ShiftEngine`'in sipariş aşamaları (`MAP.md` 11.6: `startDay`, `startOrder`, `cancelOrder`, `setDestination`, `stageFromFeed`, `deliverFromFeed`, `advanceFromFeed`, `beginCustomerJourney`, `phaseRank`), `OrderRec.phase`/bekleme süreleri, `DayState.queuedOrders`/`reset` — gün kartı testleri motorla durum kurar. A6.8 testlerinden `ShiftEngineStagesTest` 4/8 bununla yeşil; kalanı (`endDay`, `feed`, JSON, ışık) A6.8. `HeadingFilter` (A6.6) da işaret akışı için yazıldı (`HeadingFilterTest` yeşil).

**Sapmalar:** `MapShiftActionsTest.acikKartYalnizBilgiGosterir` döngüsünde her aşamadan önce 21 sn beklenir (YolGo her aşamayı ayrı testte taze kartla çizer; tek testte aşama değişimi kartı kendiliğinden açtığı için çeviri hatasıydı). `SmartLocateButton`/`RouteFab` imzalarına `lang` (varsayılan TR) eklendi. `MapTab`'a yuvalar (`toast`, `reminder`, `taskAlerts`, `wrongWay`, `routePanel`, `metrics`, `topCard`) ve `MapTabActions` eklendi; testler varsayılanlarla çalışır. Harita metinleri `logic/map` `ui.MapText` (`:app`'te düz metin yok; `logic/app` `TextCoverageTest`).

**Doğrulama:** `LC_ALL=C.UTF-8 ./gradlew -p logic test --continue` → **1.605 test, 232 kırmızı** (`:map` 206 + `:orders` `dataset` 26; hepsi `NotImplementedError`, başka görevlerin iskeleti; önceki 243'ten 11 az); `:app:testDebugUnitTest` 50 test, 26 kırmızı (A6.6–A6.9 iskeleti + yukarıdaki 2), `:map:testDebugUnitTest` 8/8; `:app:lintDebug` ve `:map:lintDebug` 0 hata; `:app:assembleRelease` yeşil. CI koşusu yok (`[skip ci]`), telefonda denenmedi.

## A6.7 kod durumu (20 Ekim 2026)

Y9 hız ve batarya tahmini, Y10 hava, `WeatherPort` → R15 ve N5 girdileri uygulandı (`MAP.md` 10; uygulama notları 10.15); dal `ccr-9f8c8f6c-07ubij` (← A6.6 dalı `ccr-1547f688-0aa3iw`). Çevrilmiş testlerden A6.7'ninki yeşil: `logic/map` **16** (`EnergyOdometerTest` 5, `BatteryEstimatorTest` 1, `SpeedOptionsTest` 4'ün 3'ü, `WeatherReportTest` 5, `WeatherSummaryTest` 2) + `:app` Robolectric **17** (`MapMetricsTest` 7, `SpeedSheetTest` 1, `WeatherChipTest` 2, `WeatherDetailSheetTest` 1, `MapWeatherSettingsTest` 4, `MapTabLayoutTest` 1, `MapTabTest` 2/2). `-p logic test`: **1.645 test, 149 kırmızı** (A6.6 sonu 165; kalanlar A6.8 `day`/`graph.PoiSetTest`/`route.Bikeway*`/`ui.BikewayLayerRules`/`nav.NavAssistant`, A6.9 `logs`/`reminders`/`stats`/`tasks`/`orders.dataset`, A6.11 `roads`). `RouteEngineTest` zaman aşımı testi yük altında (paralel Gradle) bir kez düşer, tek başına yeşil. `:app` Robolectric tamamı: **50 test, 9 kırmızı** (A6.6 sonunda 26; kalan: A6.8 `BikewayTabTest` 1, A6.9 `DatasetScreenTest` 5 + `ReminderCardTest` 2 + `RideLogStatusTextTest` 1); `:map` Robolectric yeşil; `:app`/`:map` `lintDebug` 0 hata (39 + 15 uyarı).

**Yeni testler 35** (`logic/map`): `RangeInputsTest` 4 (T1-2 (a): ev/bırakış/batarya yokken alan yok, taze BLE ↔ tahmin, bozuk değer), `WeatherIconsTest` 5, `WeatherRequestTest` 4, `WeatherNotifyTextTest` 4, `LiveWeatherTest` 6, `WeatherFetcherTest` 6 (önbellek, gün kapısı, gün yarıda kapanınca kayıt yok, hata eski raporu silmez, anahtar), `WeatherNotifierTest` 3 (günde bir, izin/gün/ayar, hata), `WeatherPrefsTest` 3 (varsayılanlar, kayıtlı kapalı ayar, konum). `:core` `PortsTest` `RangePort.NONE` ile genişledi.

**Çevrilmeyenler (`MAP.md` 10.12 gibi):** `offer_engine_test` 7 + `route_costing_test` `planLeavesBox` 1 (T1-2 (a), v1 ölü kod); `route_costing_test` 5 (Y13, sonra); `v170_features_test` voltaj/km tazeleme, yoklama, zaman aşımı, Rawstats ev cevabı (iç akış, S7).

**Sapmalar:** (1) `MapTabLayoutTest` tek testte dört kombinasyon koşar: her kombinasyon başında önce `waitForIdle`, sonra 21 sn ilerletme (aşama değişimi kartı açar, 20 sn sonra katlanır) ve sonunda `weather-collapse` (YolGo her kombinasyonu taze çizer). (2) Gerçek hata (A6.5'ten): `MapTab` gün kartını katlı/açık yerleşimde iki ayrı yerde çiziyordu, kart açılınca örnek yeniden kuruluyor ve "katlı" durumuna dönüyordu; aynı örnek `movableContentOf` ile taşınır (girdiler `rememberUpdatedState`). (3) `MapSettingsScreen` "Hava durumu" başlığına dokununca açılır (test `kayitliKapaliAyarKorunur` başlığa tıklar). (4) Pencereler (batarya/hız/hava ayrıntısı) `Dialog` ile alt kenara oturur; örtü ayrı katmandır (tıklanabilir üst düğüm içeriğin anlamsal ağacını birleştirip `testTag`'i gizliyordu). (5) Ayrıntı arka planı asimetrik `clip` yerine şekilli `background` (Robolectric'te `clip(RoundedCornerShape(topStart, topEnd))` dokunma isabetini bozar, kaydırma çalışmaz; cihazda fark yok). (6) `weather-detail` içeriği tek anlamsal blok (`mergeDescendants`): "N°" aramaları tek düğüm (sıcaklık ve hissedilen aynı değerde olunca çift sayılmasın).
- **Ortam notu:** `:app` testleri ilk indirmede Maven Central 429 verir (Robolectric, coroutines-test); `--max-workers=1` ile yeniden dene. İki Gradle'ı aynı anda koşturma (`includeBuild("logic")` ortak `build/` kullanır; `NoSuchFileException`).
