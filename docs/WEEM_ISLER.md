# WEEM iş listesi

Kurallar: `AGENTS.md` → "Birden çok Claude hesabı". Durumlar: `bekliyor` · `sürüyor (<tarih saat>)` · `bitti (<commit>)` · `engel (<neden>)`. Her görev bir oturuma sığar; "Bağımlı" sütunundakiler bitmeden başlanmaz. Özellik kodları (Y/R/S/G/N) `WEEM_PLANI.md` Bölüm 3'te; her özelliğin eski kodu, testleri ve verisi `docs/sartname/OZELLIK_HARITASI.md`'de, korunacak adlar `VERI_SOZLESMESI.md`'de, test listesi `TEST_ENVANTERI.md`'de. (K) = kullanıcının telefonda yapacağı deneme.

**Usage kuralı (S13, ayrıntı `AGENTS.md`):** ultracode yalnız A3.1, A6.1 (şartname) ve A10.3 (büyük denetim); paralel ajan en çok 2–3, her ajan çıktısını bölüm bölüm repoya yazar ve her bölümde commit + push. Diğer şartname görevleri (A2.1, A5.1) tek ajanla; önce `docs/sartname/bulgular/` ve özellik haritası okunur, yalnız eksikler kaynak koddan çıkarılır.

## Şu an

- **Son yapılan:** 17 Ekim — A0.2 (açık soruların yanıtı plan Bölüm 8'de; Y12 1.0'a, A6.11; AGENTS.md'ye paralel ajan kuralı ve "Devam promptu") ve A1.1 (Gradle 9.3.1 / AGP 9.1.0 / Kotlin 2.4.0, `gradle/libs.versions.toml`, `logic/` ayrı build 8 modül + örnek testler, kök Android build `includeBuild("logic")` + 9 boş modül, modül yönü denetimi `gradle/module-graph.properties`).
- **Sıradaki iş:** A1.2 (kimlik ve imza) — Opus · high. Öneri: A1.2'yi A1.4 (CI) ile aynı oturumda yap; Android katmanı burada derlenmiyor, ilk doğrulama CI'da (tek koşu, Actions dakikası az).
- **Devam eden (yarım) iş:** A1.2 sürüyor (dal `ccr-2794f98b-53t67j`)
- **Kullanıcıdan beklenen:** K0.1 kodsuz saha denemesi; uygulama simgesi; kalıcı Receiver telefonu (bugün Poco); her hesabın ortam ağ ayarında `dl.google.com` izni (Android'i yerelde derlemek için; nasıl: `AGENTS.md` → "Ortam").

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
| A1.2 | Kimlik: `applicationId` `com.kuryelog.app`, debug `.dev`; imza = `rawstats` `ci/kuryelog-test.keystore` (alias ve şifre Rawstats `app/build.gradle.kts`'teki gibi); sürüm kodu tek kaynaktan, >33; minSdk 29, target/compile 36; `allowBackup=false` + Rawstats `data_extraction_rules.xml`; manifestte `com.kuryelog.app.WatchService` ve `NotifService` (boş gövde), izinler (`VERI_SOZLESMESI` Bölüm 3) | CI'da `aapt2 dump badging`: paket, sürüm, imza SHA-256 Rawstats ile aynı | Opus · high | A1.1 | sürüyor (17 Ekim 2026) |
| A1.3 | Kabuk: tek Activity + Compose alt menü (Harita · Siparişler · Scooter · Uzaktan · Daha fazla), sekme durumu korunur, açılış Harita; gece/gündüz teması (renk karşıtlığı testi, `RawPaletteContrastTest` karşılığı); dil TR/AZ (G3) ve çeviri kapsam testi (`I18nCoverageTest` karşılığı) | Kabuk testleri yeşil; `.dev` APK derlenir | Sonnet · high | A1.1 | bekliyor |
| A1.4 | CI: `ci.yml` (PR: `logic` testleri + Android birim testleri + lint + `assembleDebug`; Gradle önbelleği; `.md` hariç) ve `apk.yml` (`weem-v*` etiketi ya da elle → Release) | Bir yeşil koşu | Sonnet · high | A1.2 | bekliyor |
| A1.5 | (K) `.dev` APK yedek telefonda açılır, sekmeler arası geçişte durum korunur | Kullanıcı onayı | — | A1.4 | bekliyor |

## Aşama 2 — Çekirdek (`:core`)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A2.1 | Şartname `docs/sartname/CORE.md`: G1, G2, G4, R20 ve depolama — kaynak `bulgular/R_core_contract.md`, `Y_tracking.md` §G1–G4 + eksikler (Rawstats `Store`, `CourierDb`, `DailyRecords`, `RecordJson`, `HomeShift`, `GpsService`; YolGo `home_day`, `shift_engine`); eşikler ve durum geçişleri kaynak satırıyla; tek vardiya kuralı = bugünkü Rawstats F4.5b (S2, ek GPS önlemi yok), YolGo kuralıyla farklar belgelenir | Belge; tek kural yazılı | Opus · high (tek ajan) | A0.1 | bekliyor |
| A2.2 | `:core` testlerini çevir: `TEST_ENVANTERI` hedefi `:core` (27 dosya, 172) + uyarlanır `rawstats_shift_end_test`, `rawstats_home_*`, `HomePickContractTest` | Testler derlenir, kırmızı | Sonnet · high | A2.1, A1.1 | bekliyor |
| A2.3 | Depolama: gün klasörü ve `logNames`, JSONL yazma/okuma, `courier.db` (şema 1), `kuryelog` tercihleri, eski göç işaretleri; çökme kaydı (R20: `crashes.jsonl`, korumalı girişler) | A2.2 depolama ve R20 testleri yeşil | Opus · high | A2.2 | bekliyor |
| A2.4 | Vardiya / gün / ev tek kural (G1, G2; bitiş bugünkü F4.5b kuralı, S2), saat ve cihaz kimliği, bildirim tablosu (`NotificationIds`, çakışma testi; G4 `NotifyPolicy`) | A2.2 testleri yeşil; 5103 ve 4000–8095 çakışması testle yok | Opus · high | A2.2 | bekliyor |
| A2.5 | Modüller arası arayüzler ve mesaj tipleri (plan 4.3–4.4): `ShiftPort`, `OrderEvents`, `ScooterBridge`, `NavPort`, `WeatherPort`, `NotifyPort`; tipler `ShiftState`, `OfferSummary`, `TripStages`, `NavTarget`, `DaySummary`, `Energy*`, `BatterySummary`, `RouteStatus`, `WeatherNow`; JSON gidiş-dönüş testleri (bugünkü feed alanlarıyla) | Tip testleri yeşil | Opus · high | A2.2 | bekliyor |
| A2.6 | (K) Eski Rawstats verisi (ZIP yedek ya da kurulu Rawstats) WEEM `.dev`'de okunur | Kullanıcı onayı | — | A2.3 | bekliyor |

## Aşama 3 — Ekran okuma ve siparişler (R1–R3, R5–R13, R15–R16, R19)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A3.1 | Şartname `docs/sartname/CAPTURE.md`, `ORDERS.md` — kaynak `bulgular/R_capture.md`, `R_orders.md` + eksikler (R12, R13, R15; `DeliveryCore` durum makinesinin tamamı); saha kareleri (`saha_kareleri_2026-10-11_12`) | Belge (bölüm bölüm push) | Opus · xhigh + ultracode (S13: en çok 2–3 paralel ajan) | A2.5 | bekliyor |
| A3.2 | Testleri çevir: `:capture` (15 dosya, 203) + `:orders` sipariş akışı (K34, D3, D9, M18–M22, E2, E15, R1, K21, K29, `CoreLifecycleV2Test`, `SahaTekrarTest`) | Testler derlenir, kırmızı | Sonnet · high | A3.1 | bekliyor |
| A3.3 | Testleri çevir: `:orders` para, platform, rozet, balon, gün sayfası (K31–K32, M15–M17, F4.6–F4.8, K27, M21, E3, K36, D15, F8.1–F8.5, K35, E14, E16, F7.1–F7.3, K25, E1, K28) | Testler derlenir, kırmızı | Sonnet · high | A3.1 | bekliyor |
| A3.4 | `:capture` kodu: ayrıştırıcı, ekran tanıma, OCR temposu, kayıt politikası, bildirim okuma, R16 mesafe; **ağ yasağı testi** (`:capture` bağımlılıklarında ağ sınıfı yok) ve **ICU desen testi** (tüm modüller) | A3.2 `:capture` testleri yeşil | Opus · high | A3.2 | bekliyor |
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

Yok: S1–S13 17 Ekim'de yanıtlandı (A0.2) → `WEEM_PLANI.md` Bölüm 8. Yeni soru çıkarsa buraya tabloyla eklenir; yanıt gelince Bölüm 8'e yazılır ve buradan silinir.
