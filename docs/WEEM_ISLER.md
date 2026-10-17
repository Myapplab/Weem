# WEEM iş listesi

Kurallar: `AGENTS.md` → "Birden çok Claude hesabı". Durumlar: `bekliyor` · `sürüyor (<tarih saat>)` · `bitti (<commit>)` · `engel (<neden>)`. Her görev bir oturuma sığar; "Bağımlı" sütunundakiler bitmeden başlanmaz. Özellik kodları (Y/R/S/G/N) `WEEM_PLANI.md` Bölüm 3'te; her özelliğin eski kodu, testleri ve verisi `docs/sartname/OZELLIK_HARITASI.md`'de, korunacak adlar `VERI_SOZLESMESI.md`'de, test listesi `TEST_ENVANTERI.md`'de. (K) = kullanıcının telefonda yapacağı deneme.

**Usage kuralı (S13, S14; ayrıntı `AGENTS.md`):** ultracode yalnız A6.1 (şartname) ve A10.3 (büyük denetim); paralel ajan en çok 2–3, her ajan çıktısını bölüm bölüm repoya yazar ve her bölümde commit + push. Diğer şartname görevleri (A2.1, A3.1, A5.1) tek ajanla (A3.1: Opus · max, S14); önce `docs/sartname/bulgular/` ve özellik haritası okunur, yalnız eksikler kaynak koddan çıkarılır.

## Şu an

- **Son yapılan:** 17 Ekim — A3.3 (testleri çevir: `:orders` para, platform, rozet, balon, gün sayfası) bitti: Rawstats @ `6388c04`'ten **141 test** Kotlin'e çevrildi — `logic/orders` 135, `logic/capture` 6 (`NoteFieldsF71Test`). `./gradlew -p logic compileTestKotlin` yeşil; kök Android `:orders`/`:capture` `compileDebugKotlin` yeşil; `-p logic test`: `capture` 100 test (97 kırmızı), `orders` 374 (371 kırmızı), `core` 216 yeşil. Yeni iskelet imzalar: `PocketCash`, `CashPending`, `PlatformTotals`, `RestaurantBubble`, `CustomerCard`, `CustomerBubble`, `RecordPages`, `OfferSummaries` (`OrderEvents.offers` üreticisi). Beklentiler Rawstats ile birebir; elle değişenler: ad (WEEM, "hava bilgisi gelmedi"), `YolGoNav` → `RouteStatus`, `OfferFeed` → `OfferSummaries`, `AnalysisExport` ifadeleri A4.3'e, 20 kaynak taraması A3.7/A3.8'e. Ayrıntı: `TEST_ENVANTERI.md` "A3.3 çeviri durumu".
- **Sıradaki iş:** A3.4 (dal `ccr-e90bca59-fxabvl`'de: `git fetch origin ccr-e90bca59-fxabvl && git checkout ccr-e90bca59-fxabvl`) — `:capture` kodu: ayrıştırıcı, ekran tanıma, OCR temposu, kayıt politikası, bildirim okuma, R16 mesafe; ağ yasağı testi ve ICU desen testi (`CAPTURE.md`; `logic/capture` 97 kırmızı test yeşile döner) — **Opus · high**.
- **Devam eden (yarım) iş:** A3.2 ve A3.3 tamam ama **`main`'e girmedi** (`AGENTS.md` madde 4: kırmızı testler `main`'e girmez; `:capture` ve `:orders` testleri A3.4–A3.6'ya kadar kırmızı, CI `logic/ testleri` adımı kırar). Dal `ccr-e90bca59-fxabvl`, kod son commit `cd9a845`. **A3.4, A3.5… aynı dalda sürer (yeni dal açma)**; testler yeşile dönünce (A3.6 ya da kullanıcı "şimdi birleştir" derse) `main`'e birleştirilir. Kalınan adım: A3.4 sürüyor (17 Ekim 18:15, Opus · high). `main`'deki bu not ile dal aynı metni taşır (birleşmede çakışma çıkmasın).
- **Kullanıcıdan beklenen:** AZ metni onayı (`BoostTempE3Test`: "hava məlumatı gəlmədi", belgede yok, A3.3'te ben seçtim); üç yeni soru (S15–S17, aşağıda "Açık sorular"; A3.3–A3.6'yı engellemez); evde ev konumunu kaydedip "Her zaman" konum izni vermek ve otomatik vardiyayı denemek (çıkış ≥50 m ve ≥10 km/s, dönüşte 30 sn); K0.1 kodsuz saha denemesi; uygulama simgesi; kalıcı Receiver telefonu (bugün Poco); her hesabın ortam ağ ayarında `dl.google.com` izni (Android'i yerelde derlemek için; nasıl: `AGENTS.md` → "Ortam").

## Aşama 0 — Planın son hâli

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A0.1 | Planın son hâli: `docs/sartname/OZELLIK_HARITASI.md`, `VERI_SOZLESMESI.md`, `TEST_ENVANTERI.md`, `bulgular/`; plan mimarisi ve arayüzler; iş listesi; açık sorular | Belgeler `main`'de | Opus · xhigh + ultracode | — | bitti (`5fd61fc`; çok ajanlı okuma usage nedeniyle yarıda durduruldu, eksikler şartname görevlerine yazıldı) |
| A0.2 | (K) Açık soruları yanıtla (bu dosyanın sonu); kararlar `WEEM_PLANI.md` Bölüm 8'e tarihle yazılır | Kararlar plan Bölüm 8'de | Sonnet · medium (yazma) | A0.1 | bitti (17 Ekim; plan Bölüm 8) |
| K0.1 | (K) Kodsuz saha denemesi, 1–2 vardiya: Poco scooter'da (Sürüş Paneli + YolGo, hedef elle; scooter'a yalnız Poco bağlı), Pixel'de Wolt + Bolt + Rawstats, Pixel'deki Sürüş Paneli kapalı ve Bluetooth'ta scooter yok. Bakılacak: scooter Bluetooth'u kopuyor mu, ısınma, güneşte okunma, hotspot'un (bugün Poco'da) pile etkisi, **Pixel cepteyken teklifler okunuyor mu** | Kullanıcı sonuçları yazar | — | — | bekliyor |

## Aşama 1 — İskelet

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A1.1 | Gradle iskeleti: wrapper 9.3.1, AGP 9.1.0, Kotlin 2.4.0 (Rawstats ile aynı), sürüm kataloğu. `logic/` ayrı build (Android eklentisi yok; alt projeler `core capture orders economics scooter map link remote`), kök build `includeBuild("logic")`; Android modülleri `:app :core :capture :orders :economics :scooter :map :link :remote` (boş). Oturum başlangıç kancası `logic` önbelleğini ısıtır (zaten yazılı) | Yerelde `./gradlew -p logic test` yeşil (modül başına örnek test); bağımlılık yönü testi (4.2) | Opus · high | A0.1 | bitti (`a1eb8bc`, `6776b9a`; Android katmanı yerelde derlenmedi, ilk derleme A1.4 CI'da) |
| A1.2 | Kimlik: `applicationId` `com.kuryelog.app`, debug `.dev`; imza = `rawstats` `ci/kuryelog-test.keystore` (alias ve şifre Rawstats `app/build.gradle.kts`'teki gibi); sürüm kodu tek kaynaktan, >33; minSdk 29, target/compile 36; `allowBackup=false` + Rawstats `data_extraction_rules.xml`; manifestte `com.kuryelog.app.WatchService` ve `NotifService` (boş gövde), izinler (`VERI_SOZLESMESI` Bölüm 3) | CI'da `aapt2 dump badging`: paket, sürüm, imza SHA-256 Rawstats ile aynı | Opus · high | A1.1 | bitti (`7dd7b6a`; `ci/check_identity.sh` yerelde yeşil: `.dev` ve `com.kuryelog.app`, imza SHA-256 Rawstats ile aynı; CI'da A1.4) |
| A1.3 | Kabuk: tek Activity + Compose alt menü (Harita · Siparişler · Scooter · Uzaktan · Daha fazla), sekme durumu korunur, açılış Harita; gece/gündüz teması (renk karşıtlığı testi, `RawPaletteContrastTest` karşılığı); dil TR/AZ (G3) ve çeviri kapsam testi (`I18nCoverageTest` karşılığı) | Kabuk testleri yeşil; `.dev` APK derlenir | Sonnet · high | A1.1 | bitti (`90475d2`; yerelde testler, lint, iki APK yeşil; `logic/app` eklendi) |
| A1.4 | CI: `ci.yml` (PR: `logic` testleri + Android birim testleri + lint + `assembleDebug`; Gradle önbelleği; `.md` hariç) ve `apk.yml` (`weem-v*` etiketi ya da elle → Release) | Bir yeşil koşu | Sonnet · high | A1.2 | bitti (PR #1; ilk koşu yeşil: logic + Android testleri, lint, iki APK'da kimlik denetimi) |
| A1.5 | (K) `.dev` APK yedek telefonda açılır, sekmeler arası geçişte durum korunur | Kullanıcı onayı | — | A1.4 | bitti (kullanıcı onayı, 17 Ekim 2026: `.dev` APK yedek telefonda; sekme sayacı korunuyor, TR/AZ, gündüz/gece, ayarlar yeniden açılışta kalıyor) |

## Aşama 2 — Çekirdek (`:core`)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A2.1 | Şartname `docs/sartname/CORE.md`: G1, G2, G4, R20 ve depolama — kaynak `bulgular/R_core_contract.md`, `Y_tracking.md` §G1–G4 + eksikler (Rawstats `Store`, `CourierDb`, `DailyRecords`, `RecordJson`, `HomeShift`, `GpsService`; YolGo `home_day`, `shift_engine`); eşikler ve durum geçişleri kaynak satırıyla; tek vardiya kuralı = bugünkü Rawstats F4.5b (S2, ek GPS önlemi yok), YolGo kuralıyla farklar belgelenir | Belge; tek kural yazılı | Opus · high (tek ajan) | A0.1 | bitti (17 Ekim; `CORE.md` `d66fc36`–`891a502` + sözleşme/envanter güncellemesi) |
| A2.2 | `:core` testlerini çevir — liste ve beklentiler `CORE.md` Bölüm 6 (Rawstats 79; YolGo `home_day_*` aynen/F4.5b ölçekli/fark beklentileriyle, `home_day_controller_test` 1–3, `rawstats_shift_end_test` → `ShiftFollower`, `HomePickContractTest`, `NotifyPolicyTest`; ~134). A3/A4.3/A6'ya düşenler çevrilmez | Testler derlenir; eski davranışa karşı yeşil (bkz. durum) | Sonnet · high | A2.1, A1.1 | bitti (`e3fd8b4`; `logic/core` 126 test yeşil. Kırmızı aşama atlandı: çevrilen testleri doğrulamak için saf sınıflar eski koddan taşındı, bu A2.3/A2.4 kapsamının saf kısmını da bitirdi; kalanlar o satırlarda) |
| A2.3 | Depolama (`CORE.md` 4–5): gün klasörü ve `logNames`, JSONL yazma/okuma, `DailyRecords`, ZIP içe aktarma (`LogImporter`; A2.6 için), `courier.db` (şema 1, DDL sabitleri), `kuryelog` tercihleri, eski göç işaretleri; çökme kaydı (R20: `crashes.jsonl`, `guarded`, kart); A2.6 için "Kayıtlar" listesi (öneri, 5.3) | A2.2 depolama ve R20 testleri yeşil | Opus · high | A2.2 | bitti (17 Ekim; `logic/core`: `LogStore`, `LogTime`, `LogImporter`, `CourierSchema`, `CrashFile`, `guarded`/`attempt`, `KuryelogPrefs`, `Lang`/`CoreText`; Android `core/`: `Store`, `CourierDb`, `CrashLog`, `LogImport`; `app/`: `WeemApplication`, çökme kartı, `GuardedPage`, Daha fazla → Kayıtlar. core 160 test; kök test+lint+iki APK yerelde yeşil; telefonda denenmedi) |
| A2.4 | Vardiya / gün / ev tek kural (G1, G2; `CORE.md` 1–2; bitiş bugünkü F4.5b kuralı, S2), `ShiftMachine` + vardiya servisi + ev izleyici, saat ve cihaz kimliği, bildirim tablosu (`NotificationIds` `CORE.md` 3.3, çakışma testi; G4 `NotifyPolicy`) | A2.2 testleri yeşil; 5103 ve 4000–8095 çakışması testle yok | Opus · high | A2.2 | bitti (17 Ekim; `logic/core`: `ShiftMachine`, `HomeWatchCore`, `GpsTrack`/`GpsFix` (D8, fix yaşı, türetilmiş hız), `NotificationIds` (+ çakışma ve düz sayı taraması testi), `DeviceId`, `Clock`; testler: controller 1–3, H4 servis, F45b `busy`, E6 izleyici taraması, servis korumaları. Android `core/`: `ShiftService`, `HomeWatch`, `HomeFixSampler`, `Home`, `Notices`; `app/`: vardiya kartı, Ev konumu ekranı. core 195 test; kök test+lint+iki APK yerelde yeşil; telefonda denenmedi) |
| A2.5 | Modüller arası arayüzler ve mesaj tipleri (plan 4.3–4.4; `CORE.md` 1.8, 1.11): `ShiftPort`, `ShiftFollower`, `OrderEvents`, `ScooterBridge`, `NavPort`, `WeatherPort`, `NotifyPort`; tipler `ShiftState`, `OfferSummary`, `TripStages`, `NavTarget`, `DaySummary`, `Energy*`, `BatterySummary`, `RouteStatus`, `WeatherNow`; JSON gidiş-dönüş testleri (bugünkü feed alanlarıyla) | Tip testleri yeşil | Opus · high | A2.2 | bitti (17 Ekim; `logic/core`: `Ports.kt` (`ShiftPort`, `OrderEvents`, `ScooterBridge`, `NavPort`, `WeatherPort`, `NotifyPort`/`Notify`, `ShiftListeners`, `NONE` uygulamaları), mesajlar `OfferSummary`, `TripStages`, `NavTarget`, `RouteStatus`, `DaySummary`, `ScooterLive`, `BatterySummary`, `EnergyRequest/Result`/`EnergyBlock`, `WeatherNow`, `ShiftState.toJson`; bugünkü feed okuyucuları; `MessagesTest`, `PortsTest`. Android `ShiftHub`. core 212 test; kök yeşil) |
| A2.6 | (K) Eski Rawstats verisi (ZIP yedek ya da kurulu Rawstats) WEEM `.dev`'de okunur | Kullanıcı onayı | — | A2.3 | bitti (kullanıcı onayı, 17 Ekim 2026: `weem-dev-2` APK, Rawstats "Tüm kayıtları paylaş" ZIP'i içe aktarıldı, tüm kayıtlar tamam; vardiya elle başladı, bildirim görüldü. Ev kaydı ve otomatik başlama/bitiş henüz denenmedi: kullanıcı evde değildi) |

## Aşama 3 — Ekran okuma ve siparişler (R1–R3, R5–R13, R15–R16, R19)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A3.1 | Şartname `docs/sartname/CAPTURE.md`, `ORDERS.md` — kaynak `bulgular/R_capture.md`, `R_orders.md` + eksikler (R12, R13, R15; `DeliveryCore` durum makinesinin tamamı); saha kareleri (`saha_kareleri_2026-10-11_12`) | Belge (bölüm bölüm push) | Opus · max, tek ajan (S14: ultracode ve paralel ajan yok) | A2.5 | bitti (17 Ekim; `CAPTURE.md` 526 + `ORDERS.md` 729 satır; `OZELLIK_HARITASI`, `VERI_SOZLESMESI`, `TEST_ENVANTERI` (1.601 çevrilir), `WEEM_PLANI` güncel; kullanıcı soruları S15–S17 "Açık sorular"da; yalnız belge, kod ve CI yok) |
| A3.2 | Testleri çevir (liste ve yerler: `CAPTURE.md` 8 — 203 testten 93 `logic/capture`, 100 `logic/orders`, 4 `logic/core`, 6 çevrilmez; `ORDERS.md` 15.1): `:capture` (15 dosya) + `:orders` sipariş akışı (K34, D3, D9, M18–M22, E2, E15, R1, K21, K29, `CoreLifecycleV2Test`, `SahaTekrarTest`) | Testler derlenir, kırmızı | Sonnet · high | A3.1 | bitti (`f78cf22`, dalda `ccr-e90bca59-fxabvl`; `main`'e girmedi, testler kırmızı: `AGENTS.md` madde 4; 17 Ekim: capture 93, orders 238, core 4; sapmalar `TEST_ENVANTERI` "A3.2 çeviri durumu") |
| A3.3 | Testleri çevir: `:orders` para, platform, rozet, balon, gün sayfası (K31–K32, M15–M17, F4.6–F4.8, K27, M21, E3, K36, D15, F8.1–F8.5, K35, E14, E16, F7.1–F7.3, K25, E1, K28) | Testler derlenir, kırmızı | Sonnet · high | A3.1 | bitti (`cd9a845`, dalda `ccr-e90bca59-fxabvl`; `main`'e girmedi: testler kırmızı; 135 orders + 6 capture test, 20 kaynak taraması yazılmadı, AnalysisExport ifadeleri A4.3'e; `TEST_ENVANTERI.md` "A3.3 çeviri durumu") |
| A3.4 | `:capture` kodu: ayrıştırıcı, ekran tanıma, OCR temposu, kayıt politikası, bildirim okuma, R16 mesafe; **ağ yasağı testi** (`:capture` bağımlılıklarında ağ sınıfı yok) ve **ICU desen testi** (tüm modüller) | A3.2 `:capture` testleri yeşil | Opus · high | A3.2 | sürüyor (17 Ekim 18:15; dal `ccr-e90bca59-fxabvl`) |
| A3.5 | `:orders` çekirdek: teklif ve sipariş durum makinesi (R3, R5): aşamalar, restoran/müşteri varışı, bekleme, hazır sayacı, gecikme, `TripStages` | A3.2 sipariş testleri ve `SahaTekrarTest` yeşil | Opus · high | A3.4 | bekliyor |
| A3.6 | `:orders` para ve platform: nakit/bahşiş (R8), cepteki nakit ve CASH PENDING (R9), boost (R10), çevrimiçi süre (R11), resmî değerler (R13), teklif havası (R15, kaynak `WeatherPort` boş) | A3.3 testleri yeşil | Sonnet · high | A3.5 | bekliyor |
| A3.7 | Android katmanı: `WatchService`/`NotifService` servisleri, ML Kit OCR, rozet penceresi (R7), restoran/müşteri balonları (R6), bahşiş/nakit bildirimleri ve pencereleri, uyarılar (R19); hiçbir şeye basmama denetimi (`performAction`/`dispatchGesture` yok testi); eski iki uygulama sözleşmeleri yok (S7: `com.kuryelog.app.economics` sağlayıcısı, `SHARE_HOME`, `PICK_HOME`) | Android birim testleri yeşil; `.dev` APK | Opus · high | A3.6 | bekliyor |
| A3.8 | Siparişler sekmesi (Compose): gün sayfaları, eski günler, arşiv, resmî değerler kartı, nakit kartı (R12) | Testler yeşil | Sonnet · high | A3.6, A1.3 | bekliyor |
| A3.9 | Gölge mod raporu: WEEM `.dev` ile Rawstats'ın aynı gün klasörlerini karşılaştıran araç (`logic/`, JVM'de çalışır; girdi: iki ZIP) | Saha karelerinde fark 0 | Opus · high | A3.7 | bekliyor |
| A3.10 | (K) Pixel'de 1–2 vardiya `.dev` gölge modda; ZIP'ler A3.9 ile karşılaştırılır | Fark yok | — | A3.9 | bekliyor |

## Aşama 4 — Ekonomi ve yedek (R4, R17, R18, N3, N4, N8)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A4.1 | Ekonomi v2 (`docs/ekonomi-v2`: SPEC 2.1.0, `ref.py`, altın vektörler), teklif puanı, gölge değerlendirme, teklif bölgesi (`:economics`, 11 dosya, 101) | Altın vektörler geçer | Opus · high | A3.5 | bekliyor |
| A4.2 | Gün sonu raporu ve net kazanç (N4), günlük kazanç hedefi (N8). S8: net = brüt + bahşiş − filo komisyonu − enerji maliyeti (Wh × kWh fiyatı / şarj verimi; fiyat ve verim ayardan); hedef günlük sabit tutar, net üzerinden, ayardan değişir | Testler yeşil | Sonnet · high | A4.1 | bekliyor |
| A4.3 | Yedek ve geri yükleme (ZIP, `BackupFiles` biçimi), günlük paket, anonim dışa aktarma (R17, R18; `redact_vectors`), otomatik şifreli yedek (N3) | Geri yükleme testi yeşil; `tools/analysis` eski ZIP'i okur | Opus · high | A2.3 | bekliyor |
| A4.4 | (K) Gün sonu eski verilerle tutarlı | Kullanıcı onayı | — | A4.2 | bekliyor |

## Aşama 5 — Scooter (S1–S8, R14)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A5.1 | Şartname `docs/sartname/SCOOTER.md` (ham bulgu YOK — tam çıkarma): BLE UUID'leri, el sıkışma, şifreleme, oturum sabitleri (tek bekleyen istek, 1,2 sn pencere), okunan değerler ve ölçekleri, `pairing-v1`/Keystore, `ninebot-logger.db`, `logger` tercihleri, yeniden bağlanma; `nb_crypto.py` lisansı (`PROVENANCE.md`) | Belge | Opus · high (tek ajan) | A2.5 | bekliyor |
| A5.2 | Testleri çevir (14 suit, 324 denetim; `ProtocolTest`, `PairFlowTest`, `LinkTest`, `TimingTest`, cadence testleri) + protokol, şifreleme, oturum, eşleşme (`pairing-v1` ve `NinebotLogger.Pairing.v1` korunur), yeniden bağlanma (S1, S6) | Protokol testleri yeşil | Opus · xhigh | A5.1 | bekliyor |
| A5.3 | Canlı panel (S2), sürüş kaydı ve dışa aktarma (S3), yolculuk sayacı (S4), harici batarya (S5), sipariş başına enerji (R14, `ScooterBridge`) | Testler yeşil | Sonnet · high | A5.2 | bekliyor |
| A5.4 | Yüzen panel (S7), tanı araçları (S8), Scooter sekmesi (Compose) | Testler yeşil | Sonnet · high | A5.3, A1.3 | bekliyor |
| A5.5 | (K) Yedek telefonda Sürüş Paneli ile aynı değerler | Kullanıcı onayı | — | A5.4 | bekliyor |

## Aşama 6 — Harita ve navigasyon (Y1–Y12, Y15–Y21, N5)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A6.1 | Şartname `docs/sartname/MAP.md` — kaynak `bulgular/Y_routing.md`, `Y_nav.md`, `Y_tracking.md`, `Y_contract.md` + eksikler (Y1, Y2, Y4, Y8, Y10, Y15–Y19, Y21); Y12 tamamı (öğrenme, sorular, Ses+/Ses− yakalama, ekranlar, rota maliyetine bağ; sıfırdan öğrenir — S3) | Belge (bölüm bölüm push) | Opus · xhigh + ultracode (S13: en çok 2–3 paralel ajan) | A2.5 | bekliyor |
| A6.2a | Dart testlerini Kotlin'e çevir: rota ve veri (`router`, `park_paths`, `push_segments`, `bikeway_e13`, `route_*`, `ww_*`, `overpass`, `map_data_update`, `pois`, `street_prefs_migration`) + Y12 (`quality`, `road_learner`, `road_save`, `street_scores`, `day_contributions`, `road_score_ui`) | Testler derlenir, kırmızı | Sonnet · high | A6.1 | bekliyor |
| A6.2b | Dart testlerini Kotlin'e çevir: navigasyon, adres, ekran, ışık, batarya, hava, görev, kayıt, veri seti (`TEST_ENVANTERI` hedef `:map` kalanları) | Testler derlenir, kırmızı | Sonnet · high | A6.1 | bekliyor |
| A6.3 | Harita verisi (Y7): graf ikili biçimi, `buildRoadGraph`, Overpass güncellemesi, DEM; CI'da harita derlemesi (`ci/osm_extract.py` karşılığı) | Testler yeşil | Opus · high | A6.2a | bekliyor |
| A6.4 | Rota motoru (Y3, Y8): A*, maliyetler, bisiklet/park yolları, itme, ters yön ve saat izni, sokak kararları | Testler yeşil; büyük haritada süre ölçümü belgede | Opus · xhigh | A6.3 | bekliyor |
| A6.5 | Harita ekranı (Y1): Google Maps + MapLibre görünümü (Y2, S4: iki stil — bugünkü OSM raster karoları ve vektör) + tema, HUD, konum oku | Testler yeşil; MapLibre'nin APK boyutuna etkisi belgede | Opus · high | A6.2b, A1.3 | bekliyor |
| A6.6 | Navigasyon (Y4: sapma, yeniden rota, yaklaşma, varış), Wolt/Bolt bağlantıları (Y5, intent filtreleri), adres bulma (Y6); Google Maps'te rota açma yok (S9) | Testler yeşil | Opus · high | A6.4, A6.5 | bekliyor |
| A6.7 | Hız ve batarya tahmini (Y9), menzil uyarısı (N5), hava (Y10) ve `WeatherPort` → R15 | Testler yeşil | Sonnet · high | A6.4 | bekliyor |
| A6.8 | Trafik ışıkları (Y11), bisiklet katmanı (Y15), restoran noktaları (Y16), bölge bilgisi (Y21) | Testler yeşil | Sonnet · high | A6.5 | bekliyor |
| A6.9 | İstatistik ekranı (Y17), sürüş kayıtları (Y18), teklif veri seti (Y19), görev kartları ve hatırlatıcılar (Y20; "Rota Oluştur" WEEM navigasyonunu açar, S9) | Testler yeşil | Sonnet · high | A6.8, A3.5 | bekliyor |
| A6.11 | Yol kalitesi öğrenme (Y12, sıfırdan): yol öğrenme, puan soruları ve Ses+/Ses− tuşu yakalama, gün katkıları, Yol puanları ve Yollarım ekranları, rota maliyetine bağlama (öğrenilen kalite, ters yön oranı) | A6.2a Y12 testleri yeşil | Opus · high | A6.4, A6.5 | bekliyor |
| A6.10 | (K) Eski YolGo ile yan yana sürüş | Aynı rotalar (Y12 sıfırdan öğrendiği için farklar açıklanır) | — | A6.9, A6.11 | bekliyor |

## Aşama 7 — YolGo verisi ve genel (G1–G5)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A7.1 | YolGo kayıtlarını içe aktarma (`duzyol-journal-v1`: gün günlüğü, sürüşler, gün raporları, rota kayıtları; `VERI_SOZLESMESI` 7.3). Eski YolGo'ya ek yok (S1): ayarlar, ev, sokak kararları, ışıklar, POI düzeltmeleri, görevler WEEM'de yeniden girilir, yol kalitesi yeniden öğrenilir | Yedek telefonda YolGo kayıtları WEEM'de | Opus · high | A6.9 | bekliyor |
| A7.2 | İlk açılış kurulumu (G5: izinler, harita verisi, Xiaomi/Pixel pil rehberi), dil bütünlüğü (G3), az bildirim (G4) uçtan uca | Testler yeşil | Sonnet · high | A7.1 | bekliyor |
| A7.3 | (K) Yedek telefonda YolGo kayıtları taşınır, ayarlar yeniden girilir | Kullanıcı onayı | — | A7.2 | bekliyor |

## Aşama 8 — 2 telefon modu (N1, N6)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A8.1 | Taşıma: hotspot hangi telefondaysa onun Wi-Fi'ı üstünden TCP (S6; bugün Poco) + Bluetooth yedek, keşif, kopma/yeniden bağlanma; Bluetooth'un scooter BLE'sine etkisi ölçümü | Testler yeşil (sahte taşıma) | Opus · xhigh | A2.5 | bekliyor |
| A8.2 | QR eşleştirme, anahtar değişimi, şifreli zarf, kuyruk (son durum / teslim zorunlu), `ack`, saat farkı, sürüm denetimi (plan 4.5) | Testler yeşil | Opus · xhigh | A8.1 | bekliyor |
| A8.3 | Roller ve mesajlar (plan 4.4): uzak `ScooterBridge`/`OrderEvents`/`NavPort`; sekmelerde öteki telefonun verisi; enerji sonucunun dönüşü; Receiver'a gitmeyecek alan testi (S5: müşteri koordinatı ve adres metni gider; ad, telefon, notlar gitmez); Provider'da scooter BLE hiç açılmaz (S11); hava her telefonda ayrı (S6) | Testler yeşil | Opus · high | A8.2, A6.6, A5.3 | bekliyor |
| A8.4 | Hırsızlık koruması (N6) — ayrıntı görevin başında kullanıcıya sorulur (S10) | Testler yeşil | Sonnet · high | A8.3 | bekliyor |
| A8.5 | (K) İki telefonla gerçek vardiya | Kullanıcı onayı | — | A8.4 | bekliyor |

## Aşama 9 — Uzaktan kullanım (N2)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A9.1 | Ekran yakalama + H.264 + akış (yalnız Wi-Fi, `:link` eşleşmesi); iki yönlü ve tam (S12): her telefonda hem gönderen hem alan taraf | Testler yeşil | Opus · high | A8.2 | bekliyor |
| A9.2 | Dokunuş iletimi (ayrı erişilebilirlik servisi), oturum işaretleri, bitirme | Testler yeşil | Opus · high | A9.1 | bekliyor |
| A9.3 | (K) Yakınken öteki telefon kullanılır | Kullanıcı onayı | — | A9.2 | bekliyor |

## Aşama 10 — WEEM 1.0

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A10.1 | Optimizasyon: pil, bellek, APK boyutu, arka planda kalma | Ölçümler belgede | Opus · high | A9.3 | bekliyor |
| A10.2 | Kod sadeleştirme: tekrar eden kod, gereksiz katman, ölü kod (`/simplify`); davranış aynı, testler yeşil | Testler yeşil, satır sayısı belgede | Opus · high | A10.1 | bekliyor |
| A10.3 | Büyük denetim: güvenlik, izinler, `/code-review xhigh` | Bulgular kapandı | Opus · xhigh + ultracode (S13) | A10.2 | bekliyor |
| A10.4 | Geri yükleme provası, geçiş rehberi | Belge + deneme | Sonnet · high | A10.3 | bekliyor |
| A10.5 | (K) Geçiş: ZIP yedek → WEEM Rawstats'ın üstüne → YolGo içe aktar | İş telefonlarında WEEM | — | A10.4 | bekliyor |

## Aşama 11 — Sonrası

| No | İş | Durum |
|---|---|---|
| A11.1 | `docs/BEKLEYEN_OZELLIKLER.md` kullanıcıya tek tek sorulur | bekliyor |

## Açık sorular

Kullanıcıya sorulacaklar (A3.1; ayrıntı `ORDERS.md` 16, `CAPTURE.md` 9). Yanıt gelince karar `WEEM_PLANI.md` Bölüm 8'e tarihle yazılır, soru buradan silinir. Hiçbiri A3.2–A3.6'yı engellemez. S1–S14 17 Ekim'de yanıtlandı → Bölüm 8.

| No | Soru | Neden / seçenekler | Engellediği |
|---|---|---|---|
| S15 | **Bildirim 12'de müşteri telefonu** (`ORDERS.md` 5.3): bugün büyük metin müşteri adı ve telefonunu gösterir; bildirim yansıtma (saat, bilgisayar, öteki telefon) varsa numara oraya da gider. | (a) Aynen kalsın (varsayılan, davranış aynı); (b) bildirimden telefon satırı çıksın (numara kartta ve balonda kalır). | A3.7 |
| S16 | **Gölge modda çift soru ve çift pencere** (`ORDERS.md` 14.3): `.dev` ile Rawstats aynı anda nakit bahşiş ve cep nakdi sorar, iki rozet ve iki balon çizer. | (a) `.dev`'de "gölge mod" anahtarı: soruları ve pencereleri kapatır, kaydı sürdürür (öneri; bahşiş farkları "beklenen fark" sayılır); (b) anahtar yok, ikisi birden çalışır. | A3.7 (anahtar), A3.9 |
| S17 | **ML Kit ve ağ** (`CAPTURE.md` 7.3): ekran okuma ağsız olmalı; ML Kit'in telemetri arka ucu çıkarılacak. Çıkarılınca OCR cihazda çalışmazsa? | Varsayılan: çıkar ve dene. Çalışmazsa (a) OCR'sız (yalnız erişilebilirlik; Wolt kartları okunmaz) ya da (b) ML Kit'in ölçüm göndermesine izin. Yalnız OCR çalışmazsa sorulur. | A3.4/A3.7 |
