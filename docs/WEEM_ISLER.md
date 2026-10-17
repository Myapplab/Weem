# WEEM iş listesi

Kurallar: `AGENTS.md` → "Birden çok Claude hesabı". Durumlar: `bekliyor` · `sürüyor (<tarih saat>)` · `bitti (<commit>)` · `engel (<neden>)`. Her görev bir oturuma sığar; "Bağımlı" sütunundakiler bitmeden başlanmaz. Özellik kodları (Y/R/S/G/N) `WEEM_PLANI.md` Bölüm 3'te; her özelliğin eski kodu, testleri ve verisi `docs/sartname/OZELLIK_HARITASI.md`'de, korunacak adlar `VERI_SOZLESMESI.md`'de, test listesi `TEST_ENVANTERI.md`'de. (K) = kullanıcının telefonda yapacağı deneme.

**Usage notu (17 Ekim):** 10 paralel ajan bir hesabın usage'ını ~1 saatte bitirdi. Şartname görevleri (A2.1, A3.1, A5.1, A6.1) **tek ajanla, sırayla**; önce `docs/sartname/bulgular/` ve özellik haritası okunur, yalnız eksik kalan özellikler kaynak koddan çıkarılır. Çok ajan yalnız kullanıcı açıkça isterse (Açık soru S13).

## Şu an

- **Son yapılan:** 17 Ekim — A0.1 planın son hâli: `docs/sartname/` (özellik haritası, veri sözleşmesi, test envanteri, ham bulgular), plan mimarisi (4.1–4.7: modül sınırları, arayüzler, 2 telefon mesajları), bu iş listesi oturumluk görevlere bölündü, açık sorular.
- **Sıradaki iş:** A1.1 (Gradle iskeleti, `logic/` ayrı build) — Opus · high. Açık sorular A1'i engellemez; S2 A2.4'ten, S1/S3 Aşama 6–7'den önce yanıtlanmalı.
- **Devam eden (yarım) iş:** A0.2 (dal `ccr-2e70dcaf-vhiw6n`)
- **Kullanıcıdan beklenen:** "Açık sorular" (S1–S13) yanıtları; K0.1 kodsuz saha denemesi; uygulama simgesi; Receiver (scooter) telefonu; her hesabın ortam ağ ayarında `dl.google.com` izni (Android'i yerelde derlemek için).

## Aşama 0 — Planın son hâli

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A0.1 | Planın son hâli: `docs/sartname/OZELLIK_HARITASI.md`, `VERI_SOZLESMESI.md`, `TEST_ENVANTERI.md`, `bulgular/`; plan mimarisi ve arayüzler; iş listesi; açık sorular | Belgeler `main`'de | Opus · xhigh + ultracode | — | bitti (`5fd61fc`; çok ajanlı okuma usage nedeniyle yarıda durduruldu, eksikler şartname görevlerine yazıldı) |
| A0.2 | (K) Açık soruları yanıtla (bu dosyanın sonu); kararlar `WEEM_PLANI.md` Bölüm 8'e tarihle yazılır | Kararlar plan Bölüm 8'de | Sonnet · medium (yazma) | A0.1 | sürüyor (17 Ekim 2026) |
| K0.1 | (K) Kodsuz saha denemesi, 1–2 vardiya: yedek telefon scooter'da (Sürüş Paneli + YolGo, hedef elle), Pixel'de Wolt + Bolt + Rawstats, Pixel'deki Sürüş Paneli kapalı. Bakılacak: scooter Bluetooth'u kopuyor mu, ısınma, güneşte okunma, hotspot'un Pixel piline etkisi, **Pixel cepteyken teklifler okunuyor mu**, scooter yedek telefonla eşleşince Pixel'in eşleşmesi düşüyor mu | Kullanıcı sonuçları yazar | — | — | bekliyor |

## Aşama 1 — İskelet

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A1.1 | Gradle iskeleti: wrapper 9.3.1, AGP 9.1.0, Kotlin 2.4.0 (Rawstats ile aynı), sürüm kataloğu. `logic/` ayrı build (Android eklentisi yok; alt projeler `core capture orders economics scooter map link remote`), kök build `includeBuild("logic")`; Android modülleri `:app :core :capture :orders :economics :scooter :map :link :remote` (boş). Oturum başlangıç kancası `logic` önbelleğini ısıtır (zaten yazılı) | Yerelde `./gradlew -p logic test` yeşil (modül başına örnek test); bağımlılık yönü testi (4.2) | Opus · high | A0.1 | bekliyor |
| A1.2 | Kimlik: `applicationId` `com.kuryelog.app`, debug `.dev`; imza = `rawstats` `ci/kuryelog-test.keystore` (alias ve şifre Rawstats `app/build.gradle.kts`'teki gibi); sürüm kodu tek kaynaktan, >33; minSdk 29, target/compile 36; `allowBackup=false` + Rawstats `data_extraction_rules.xml`; manifestte `com.kuryelog.app.WatchService` ve `NotifService` (boş gövde), izinler (`VERI_SOZLESMESI` Bölüm 3) | CI'da `aapt2 dump badging`: paket, sürüm, imza SHA-256 Rawstats ile aynı | Opus · high | A1.1 | bekliyor |
| A1.3 | Kabuk: tek Activity + Compose alt menü (Harita · Siparişler · Scooter · Uzaktan · Daha fazla), sekme durumu korunur, açılış Harita; gece/gündüz teması (renk karşıtlığı testi, `RawPaletteContrastTest` karşılığı); dil TR/AZ (G3) ve çeviri kapsam testi (`I18nCoverageTest` karşılığı) | Kabuk testleri yeşil; `.dev` APK derlenir | Sonnet · high | A1.1 | bekliyor |
| A1.4 | CI: `ci.yml` (PR: `logic` testleri + Android birim testleri + lint + `assembleDebug`; Gradle önbelleği; `.md` hariç) ve `apk.yml` (`weem-v*` etiketi ya da elle → Release) | Bir yeşil koşu | Sonnet · high | A1.2 | bekliyor |
| A1.5 | (K) `.dev` APK yedek telefonda açılır, sekmeler arası geçişte durum korunur | Kullanıcı onayı | — | A1.4 | bekliyor |

## Aşama 2 — Çekirdek (`:core`)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A2.1 | Şartname `docs/sartname/CORE.md`: G1, G2, G4, R20 ve depolama — kaynak `bulgular/R_core_contract.md`, `Y_tracking.md` §G1–G4 + eksikler (Rawstats `Store`, `CourierDb`, `DailyRecords`, `RecordJson`, `HomeShift`, `GpsService`; YolGo `home_day`, `shift_engine`); eşikler ve durum geçişleri kaynak satırıyla; iki vardiya kuralının farkları (S2) | Belge; S2 yanıtlandıysa tek kural yazılı | Opus · high (tek ajan) | A0.1 | bekliyor |
| A2.2 | `:core` testlerini çevir: `TEST_ENVANTERI` hedefi `:core` (27 dosya, 172) + uyarlanır `rawstats_shift_end_test`, `rawstats_home_*`, `HomePickContractTest` | Testler derlenir, kırmızı | Sonnet · high | A2.1, A1.1 | bekliyor |
| A2.3 | Depolama: gün klasörü ve `logNames`, JSONL yazma/okuma, `courier.db` (şema 1), `kuryelog` tercihleri, eski göç işaretleri; çökme kaydı (R20: `crashes.jsonl`, korumalı girişler) | A2.2 depolama ve R20 testleri yeşil | Opus · high | A2.2 | bekliyor |
| A2.4 | Vardiya / gün / ev tek kural (G1, G2; F4.5b GPS kayması önlemi S2'ye göre), saat ve cihaz kimliği, bildirim tablosu (`NotificationIds`, çakışma testi; G4 `NotifyPolicy`) | A2.2 testleri yeşil; 5103 ve 4000–8095 çakışması testle yok | Opus · high | A2.2, S2 | bekliyor |
| A2.5 | Modüller arası arayüzler ve mesaj tipleri (plan 4.3–4.4): `ShiftPort`, `OrderEvents`, `ScooterBridge`, `NavPort`, `WeatherPort`, `NotifyPort`; tipler `ShiftState`, `OfferSummary`, `TripStages`, `NavTarget`, `DaySummary`, `Energy*`, `BatterySummary`, `RouteStatus`, `WeatherNow`; JSON gidiş-dönüş testleri (bugünkü feed alanlarıyla) | Tip testleri yeşil | Opus · high | A2.2 | bekliyor |
| A2.6 | (K) Eski Rawstats verisi (ZIP yedek ya da kurulu Rawstats) WEEM `.dev`'de okunur | Kullanıcı onayı | — | A2.3 | bekliyor |

## Aşama 3 — Ekran okuma ve siparişler (R1–R3, R5–R13, R15–R16, R19)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A3.1 | Şartname `docs/sartname/CAPTURE.md`, `ORDERS.md` — kaynak `bulgular/R_capture.md`, `R_orders.md` + eksikler (R12, R13, R15; `DeliveryCore` durum makinesinin tamamı); saha kareleri (`saha_kareleri_2026-10-11_12`) | Belge | Opus · xhigh (tek ajan; ultracode yalnız S13 izin verirse) | A2.5 | bekliyor |
| A3.2 | Testleri çevir: `:capture` (15 dosya, 203) + `:orders` sipariş akışı (K34, D3, D9, M18–M22, E2, E15, R1, K21, K29, `CoreLifecycleV2Test`, `SahaTekrarTest`) | Testler derlenir, kırmızı | Sonnet · high | A3.1 | bekliyor |
| A3.3 | Testleri çevir: `:orders` para, platform, rozet, balon, gün sayfası (K31–K32, M15–M17, F4.6–F4.8, K27, M21, E3, K36, D15, F8.1–F8.5, K35, E14, E16, F7.1–F7.3, K25, E1, K28) | Testler derlenir, kırmızı | Sonnet · high | A3.1 | bekliyor |
| A3.4 | `:capture` kodu: ayrıştırıcı, ekran tanıma, OCR temposu, kayıt politikası, bildirim okuma, R16 mesafe; **ağ yasağı testi** (`:capture` bağımlılıklarında ağ sınıfı yok) ve **ICU desen testi** (tüm modüller) | A3.2 `:capture` testleri yeşil | Opus · high | A3.2 | bekliyor |
| A3.5 | `:orders` çekirdek: teklif ve sipariş durum makinesi (R3, R5): aşamalar, restoran/müşteri varışı, bekleme, hazır sayacı, gecikme, `TripStages` | A3.2 sipariş testleri ve `SahaTekrarTest` yeşil | Opus · high | A3.4 | bekliyor |
| A3.6 | `:orders` para ve platform: nakit/bahşiş (R8), cepteki nakit ve CASH PENDING (R9), boost (R10), çevrimiçi süre (R11), resmî değerler (R13), teklif havası (R15, kaynak `WeatherPort` boş) | A3.3 testleri yeşil | Sonnet · high | A3.5 | bekliyor |
| A3.7 | Android katmanı: `WatchService`/`NotifService` servisleri, ML Kit OCR, rozet penceresi (R7), restoran/müşteri balonları (R6), bahşiş/nakit bildirimleri ve pencereleri, uyarılar (R19); hiçbir şeye basmama denetimi (`performAction`/`dispatchGesture` yok testi) | Android birim testleri yeşil; `.dev` APK | Opus · high | A3.6 | bekliyor |
| A3.8 | Siparişler sekmesi (Compose): gün sayfaları, eski günler, arşiv, resmî değerler kartı, nakit kartı (R12) | Testler yeşil | Sonnet · high | A3.6, A1.3 | bekliyor |
| A3.9 | Gölge mod raporu: WEEM `.dev` ile Rawstats'ın aynı gün klasörlerini karşılaştıran araç (`logic/`, JVM'de çalışır; girdi: iki ZIP) | Saha karelerinde fark 0 | Opus · high | A3.7 | bekliyor |
| A3.10 | (K) Pixel'de 1–2 vardiya `.dev` gölge modda; ZIP'ler A3.9 ile karşılaştırılır | Fark yok | — | A3.9 | bekliyor |

## Aşama 4 — Ekonomi ve yedek (R4, R17, R18, N3, N4, N8)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A4.1 | Ekonomi v2 (`docs/ekonomi-v2`: SPEC 2.1.0, `ref.py`, altın vektörler), teklif puanı, gölge değerlendirme, teklif bölgesi (`:economics`, 11 dosya, 101) | Altın vektörler geçer | Opus · high | A3.5 | bekliyor |
| A4.2 | Gün sonu raporu ve net kazanç (N4), günlük kazanç hedefi (N8) — tanımlar S8'e göre | Testler yeşil | Sonnet · high | A4.1, S8 | bekliyor |
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

## Aşama 6 — Harita ve navigasyon (Y1–Y11, Y15–Y21, N5)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A6.1 | Şartname `docs/sartname/MAP.md` — kaynak `bulgular/Y_routing.md`, `Y_nav.md`, `Y_tracking.md`, `Y_contract.md` + eksikler (Y1, Y2, Y4, Y8, Y10, Y15–Y19, Y21); Y12'nin rota maliyetindeki payı (S3) | Belge | Opus · xhigh (tek ajan; ultracode yalnız S13) | A2.5, S3 | bekliyor |
| A6.2a | Dart testlerini Kotlin'e çevir: rota ve veri (`router`, `park_paths`, `push_segments`, `bikeway_e13`, `route_*`, `ww_*`, `overpass`, `map_data_update`, `pois`, `street_prefs_migration`) | Testler derlenir, kırmızı | Sonnet · high | A6.1 | bekliyor |
| A6.2b | Dart testlerini Kotlin'e çevir: navigasyon, adres, ekran, ışık, batarya, hava, görev, kayıt, veri seti (`TEST_ENVANTERI` hedef `:map` kalanları) | Testler derlenir, kırmızı | Sonnet · high | A6.1 | bekliyor |
| A6.3 | Harita verisi (Y7): graf ikili biçimi, `buildRoadGraph`, Overpass güncellemesi, DEM; CI'da harita derlemesi (`ci/osm_extract.py` karşılığı) | Testler yeşil | Opus · high | A6.2a | bekliyor |
| A6.4 | Rota motoru (Y3, Y8): A*, maliyetler, bisiklet/park yolları, itme, ters yön ve saat izni, sokak kararları | Testler yeşil; büyük haritada süre ölçümü belgede | Opus · xhigh | A6.3 | bekliyor |
| A6.5 | Harita ekranı (Y1): Google Maps + OSM görünümü (Y2; kütüphane kullanıcıya sorulur) + tema, HUD, konum oku | Testler yeşil | Opus · high | A6.2b, A1.3 | bekliyor |
| A6.6 | Navigasyon (Y4: sapma, yeniden rota, yaklaşma, varış), Wolt/Bolt bağlantıları (Y5, intent filtreleri), adres bulma (Y6) | Testler yeşil | Opus · high | A6.4, A6.5 | bekliyor |
| A6.7 | Hız ve batarya tahmini (Y9), menzil uyarısı (N5), hava (Y10) ve `WeatherPort` → R15 | Testler yeşil | Sonnet · high | A6.4 | bekliyor |
| A6.8 | Trafik ışıkları (Y11), bisiklet katmanı (Y15), restoran noktaları (Y16), bölge bilgisi (Y21) | Testler yeşil | Sonnet · high | A6.5 | bekliyor |
| A6.9 | İstatistik ekranı (Y17), sürüş kayıtları (Y18), teklif veri seti (Y19), görev kartları ve hatırlatıcılar (Y20) | Testler yeşil | Sonnet · high | A6.8, A3.5 | bekliyor |
| A6.10 | (K) Eski YolGo ile yan yana sürüş | Aynı rotalar (fark S3'e göre açıklanır) | — | A6.9 | bekliyor |

## Aşama 7 — YolGo verisi ve genel (G1–G5)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A7.1 | YolGo verisini içe aktarma (`VERI_SOZLESMESI` 5.4 ve 7.3 "evet" satırları); S1 kabul edilirse eski YolGo'ya (`kurye_nav` `yolgo-test`) küçük dışa aktarma eki | Yedek telefonda tam taşıma | Opus · high | A6.9, S1 | bekliyor |
| A7.2 | İlk açılış kurulumu (G5: izinler, harita verisi, Xiaomi/Pixel pil rehberi), dil bütünlüğü (G3), az bildirim (G4) uçtan uca | Testler yeşil | Sonnet · high | A7.1 | bekliyor |
| A7.3 | (K) Yedek telefonda YolGo'dan tam taşıma | Kullanıcı onayı | — | A7.2 | bekliyor |

## Aşama 8 — 2 telefon modu (N1, N6)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A8.1 | Taşıma: hotspot Wi-Fi TCP + Bluetooth yedek, keşif, kopma/yeniden bağlanma; Bluetooth'un scooter BLE'sine etkisi ölçümü | Testler yeşil (sahte taşıma) | Opus · xhigh | A2.5 | bekliyor |
| A8.2 | QR eşleştirme, anahtar değişimi, şifreli zarf, kuyruk (son durum / teslim zorunlu), `ack`, saat farkı, sürüm denetimi (plan 4.5) | Testler yeşil | Opus · xhigh | A8.1 | bekliyor |
| A8.3 | Roller ve mesajlar (plan 4.4): uzak `ScooterBridge`/`OrderEvents`/`NavPort`; sekmelerde öteki telefonun verisi; enerji sonucunun dönüşü; Receiver'a gitmeyecek alan testi | Testler yeşil | Opus · high | A8.2, A6.6, A5.3 | bekliyor |
| A8.4 | Hırsızlık koruması (N6) — tanım S10'a göre | Testler yeşil | Sonnet · high | A8.3, S10 | bekliyor |
| A8.5 | (K) İki telefonla gerçek vardiya | Kullanıcı onayı | — | A8.4 | bekliyor |

## Aşama 9 — Uzaktan kullanım (N2)

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A9.1 | Ekran yakalama + H.264 + akış (yalnız Wi-Fi, `:link` eşleşmesi) | Testler yeşil | Opus · high | A8.2, S12 | bekliyor |
| A9.2 | Dokunuş iletimi (ayrı erişilebilirlik servisi), oturum işaretleri, bitirme | Testler yeşil | Opus · high | A9.1 | bekliyor |
| A9.3 | (K) Yakınken öteki telefon kullanılır | Kullanıcı onayı | — | A9.2 | bekliyor |

## Aşama 10 — WEEM 1.0

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A10.1 | Optimizasyon: pil, bellek, APK boyutu, arka planda kalma | Ölçümler belgede | Opus · high | A9.3 | bekliyor |
| A10.2 | Kod sadeleştirme: tekrar eden kod, gereksiz katman, ölü kod (`/simplify`); davranış aynı, testler yeşil | Testler yeşil, satır sayısı belgede | Opus · high | A10.1 | bekliyor |
| A10.3 | Büyük denetim: güvenlik, izinler, `/code-review xhigh` | Bulgular kapandı | Opus · xhigh (ultracode yalnız S13) | A10.2 | bekliyor |
| A10.4 | Geri yükleme provası, geçiş rehberi | Belge + deneme | Sonnet · high | A10.3 | bekliyor |
| A10.5 | (K) Geçiş: ZIP yedek → WEEM Rawstats'ın üstüne → YolGo içe aktar | İş telefonlarında WEEM | — | A10.4 | bekliyor |

## Aşama 11 — Sonrası

| No | İş | Durum |
|---|---|---|
| A11.1 | `docs/BEKLEYEN_OZELLIKLER.md` kullanıcıya tek tek sorulur | bekliyor |

## Açık sorular

Kullanıcıya sorulacaklar (A0.2). Yanıt gelince karar `WEEM_PLANI.md` Bölüm 8'e tarihle yazılır, soru buradan silinir. Hiçbiri Aşama 1'i engellemez.

| No | Soru | Neden / seçenekler | Engellediği |
|---|---|---|---|
| S1 | **YolGo verisini taşımak için eski YolGo'ya küçük bir dışa aktarma eki yapılsın mı?** | YolGo'da yalnız sürüş/gün kayıtları dışa aktarılıyor (`duzyol-journal-v1`); ayarlar (ev, katsayılar), sokak kararları, ışıklar, POI düzeltmeleri, bisiklet yolları, kaldırım bölgeleri, görevler, hatırlatıcılar için yol yok ve WEEM başka paketin verisini okuyamaz. Seçenek: (a) `kurye_nav` `yolgo-test`'e tek "Tümünü dışa aktar" düğmesi (bir sürüm), (b) bunlar WEEM'de elle yeniden girilir. Teklif veri seti klasörü de aktarılsın mı? | A7.1 |
| S2 | **Tek vardiya kuralı ve GPS kayması:** Rawstats (50 m / 30 sn / <5 km/s; açık sipariş varken bitmez) ile YolGo (50 m / her fix <5 km/s / 30 sn; çıkış bölgesi 200 m) birleşiyor. F4.5b'de ev yakınında beklerken GPS sapması vardiyayı bitirebiliyor. Önlem ne olsun? | Öneri: bitiş için doğruluğu ≤20 m olan ardışık fix'ler + daha uzun süre (ör. 60 sn) + "vardiya bitti, geri al" bildirimi. YolGo'daki açık soru (F4.5: Rawstats okunurken YolGo ev kuralı devre dışı mı) WEEM'de anlamsız olur. | A2.4 |
| S3 | **Y12 (yol kalitesi) "sonra" ama bugün rotayı etkiliyor.** WEEM 1.0'da ne yapılsın? | (a) Y12'nin yalnız rota maliyetine giren kısmı (öğrenilmiş puanlar ve ters yön oranları okunur; soru/ekran yok) 1.0'da korunur ve `roads.*` içe aktarılır; (b) etkisi kapatılır, rotalar YolGo'dan farklı olabilir; (c) Y12 tamamen 1.0'a alınır. | A6.1, A6.4, A7.1 |
| S4 | İkinci harita görünümü (Y2) için OSM kütüphanesi | osmdroid (raster karo, basit) ya da MapLibre (vektör, daha ağır); plan "Aşama 6'da seçilir" diyor — şimdi tercih var mı? | A6.5 |
| S5 | **Receiver'a giden rota/teklif verisinde müşteri konumu:** müşteri adı, telefonu, notları gitmez (karar). Rota için müşteri **koordinatı** gitmek zorunda; adres metni (bina, kat, giriş) Receiver ekranında gösterilsin mi? Teklif kartında müşteri yerine yalnız bölge (Z1..Z4) ve mesafe yeterli mi? | Öneri: koordinat gider, adres metni gitmez; teklif kartında bölge + mesafe. | A8.3 |
| S6 | 2 telefon modunda hava kimden? | Plan: Receiver çeker, Provider'a gönderir. Provider'ın da interneti var; her telefon kendi çekebilir (daha basit, ağ biraz artar). | A8.3 |
| S7 | Eski iki uygulama sözleşmeleri WEEM'de kaldırılsın mı? | `com.kuryelog.app.economics` sağlayıcısı, `SHARE_HOME`/`PICK_HOME` aktiviteleri yalnız eski YolGo içindi; WEEM'de YolGo kalmayınca gereksiz. Öneri: kaldır (analiz araçları ZIP'ten okur, etkilenmez). | A3.7 |
| S8 | **Net kazanç (N4) ve hedef (N8) tanımı** | Net = brüt − filo komisyonu (bugünkü `commission`) − enerji maliyeti (kWh fiyatı ve şarj verimi ne olsun?) − başka gider (scooter kira/amortisman, telefon interneti)? Hedef günlük sabit mi, gün başına mı; brüt mü net mi? | A4.2 |
| S9 | Listede adı olmayan eski davranışlar (`OZELLIK_HARITASI.md` son bölüm: eve dönüş batarya eşikleri, kaldırım bölgeleri, Google Maps'te rota açma yedeği, gün silme …) korunsun mu? | Öneri: hepsi korunur (bugünkü davranış). | A3, A6 |
| S10 | **Hırsızlık koruması (N6) ayrıntısı** | "Uzaklaşınca" = Provider ile bağlantı kopunca mı, belirli mesafe mi? Kilit = ekran sabitleme + PIN mi? Alarm Receiver'da ses mi, Provider'a bildirim mi, ikisi mi? "Scooter hareket ederse" = scooter hızı >0 mı, telefon ivmeölçeri mi? | A8.4 |
| S11 | Ninebot iki telefonla eşleşmeyi kabul ediyor mu (biliyor musun)? | Receiver eşleşince Pixel'in eşleşmesi düşerse tek telefon moduna dönüşte yeniden eşleşme gerekir. Bilinmiyorsa K0.1'de denenir. | A5.2, A8.3 |
| S12 | **Uzaktan kullanım (N2) kapsamı** | Hangi yön: Receiver'dan Pixel'i (cepteyken Wolt/Bolt'u görmek, kabul etmek) mi, iki yön mü? Sürüş hâlindeyken kullanılabilsin mi (güvenlik)? | A9.1 |
| S13 | **Usage kuralı:** AGENTS.md "Aşama 3 ve 6 başında ultracode" diyor; bugün 10 paralel ajan usage'ı ~1 saatte bitirdi. Kural "şartname görevleri tek ajanla, sırayla; çok ajan yalnız kullanıcı açıkça isterse" diye değiştirilsin mi? | Öneri: evet (iş listesi şimdiden buna göre yazıldı). | A3.1, A6.1, A10.3 |
