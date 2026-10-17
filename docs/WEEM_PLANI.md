# WEEM planı — native (Kotlin + Compose), sıfırdan

Durum: **17 Ekim 2026 — karar verildi, kapsam seçildi, şartname çıkarıldı (A0.1: `docs/sartname/`); kod yazılmadı.** YolGo + Rawstats + Sürüş Paneli tek native uygulama **WEEM** olarak sıfırdan yazılır. Önceki "modül olarak birleştir" planı (`TEK_UYGULAMA_PLANI.md`, 10–17 Ekim) iptal; bu dosya onun yerine geçti (eski hâli git geçmişinde).

**Başvuru noktaları (şartname):** `yolgo-test` @ `870ab6e` (YolGo 1.11.0, Release `yolgo-test-78`) ve `rawstats` @ `6388c04` (Rawstats 0.25.1 / kod 33 + Sürüş Paneli modülü, Release `rawstats-35`). Eski uygulamalar WEEM 1.0'a kadar kullanılır; onlara yalnız kritik düzeltme girer.

Repo: **`Myapplab/Weem`**. Bu dosya WEEM'in tek kaynağıdır; ayrıntılı iş listesi ve "şu an" durumu `docs/WEEM_ISLER.md`'de. Oturum kuralları (hesaplar arası devam dahil): `AGENTS.md`.

---

## 1. Hedef

- Tek APK **WEEM** ("Work Efficient, Earn More"), Kotlin + Jetpack Compose; Flutter yok.
- **Aynı davranış:** eski uygulamalarda ne çalışıyorsa WEEM'de de aynı çalışır. Davranışın şartnamesi eski testler ve kodlardır (Bölüm 5).
- İki telefonda tam işlevli; **2 telefon modu** (Provider / Receiver, her telefon her rolü alabilir); **uzaktan kullanım**; altta 5 sekme.
- **WEEM 1.0 = Bölüm 3'teki "İlk sürüm" özelliklerinin tamamı** (kullanıcı kararı: hepsi kesinlikle ilk sürümde).

## 2. Değişmeyecekler (kimlik ve veri)

- `applicationId` **`com.kuryelog.app`**, imza **`ci/kuryelog-test.keystore`** (alias `kuryelogtest`): Pixel'de Rawstats'ın üstüne kurulur; erişilebilirlik ve bildirim izni, Ninebot eşleşmesi (Android Keystore anahtarları uygulama kimliğine bağlı) ve tüm kayıtlar kalır. Sürüm kodu 33'ten büyük, tek yönlü artar.
- **Servis adları aynı:** erişilebilirlik `com.kuryelog.app.WatchService`, bildirim `com.kuryelog.app.NotifService` (izin bileşen adına bağlı; değişirse izin kapanır).
- **Kayıt biçimleri aynı:** gün klasörleri `files/logs/YYYY-MM-DD/`, JSONL dosya adları ve şemaları (ekonomi v2 logları, `economics-energy.jsonl`, `notifs.jsonl`, `events.jsonl`, `gps.csv`, `crashes.jsonl` …), `courier.db`, `ninebot-logger.db`, `pairing-v1`, SharedPreferences anahtarları. İç yapı yeni olabilir; WEEM ilk açılışta eski veriyi okur, analiz araçları çalışmaya devam eder.
- **YolGo verisi** (`az.duzyol.app`): eski YolGo'dan dışa aktarma → WEEM'de içe aktarma (ev, sokak kararları, ışıklar, POI düzeltmeleri, kayıtlar, ayarlar).
- minSdk 29, target/compile 36. Debug = `applicationIdSuffix ".dev"` → eski uygulamaların yanına ayrı kurulur (gölge mod).
- Kesin liste (bileşen, izin, tercih anahtarı, veritabanı, dosya, Keystore takma adı, bildirim kanalı/numarası) ve kaynak satırları: `docs/sartname/VERI_SOZLESMESI.md`.

## 3. Özellik kapsamı (kullanıcı seçimi, 17 Ekim)

Kaynak: seçim sayfası <https://claude.ai/artifact/5RTwBJxg8JHXCgUTbLAGyD> (kayıt `secim/v1`). Her özelliğin ayrıntılı davranışı başvuru noktalarındaki kod ve testlerdir.

**İlk sürüm (58) — WEEM 1.0'da kesinlikle olacak:**
- **Harita ve navigasyon (YolGo):** Y1 Harita ekranı · Y2 İkinci harita görünümü (OSM) · Y3 Kendi rota motoru · Y4 Navigasyon · Y5 Wolt/Bolt navigasyon bağlantıları · Y6 Adres bulma · Y7 Harita verisi · Y8 Sokak kararları · Y9 Hız ve batarya tahmini · Y10 Hava durumu · Y11 Trafik ışığı duruşları · Y15 Bisiklet yolu katmanı · Y16 Restoran noktaları · Y17 İstatistik ekranı · Y18 Sürüş kayıtları · Y19 Teklif veri seti ekranı · Y20 Görev kartları ve hatırlatıcılar · Y21 Bölge bilgisi
- **Siparişler ve kazanç (Rawstats):** R1 Wolt ve Bolt ekranını okuma · R2 Bildirim okuma · R3 Teklif kaydı · R4 Teklif puanı · R5 Sipariş takibi · R6 Restoran ve müşteri balonları · R7 Kurye uygulaması üstünde gösterge · R8 Nakit ve bahşiş · R9 Cepteki nakit · R10 Boost / Earn extra · R11 Çevrimiçi süreler · R12 Siparişler ve gün sayfaları · R13 Resmî değerlerle karşılaştırma · R14 Sipariş başına enerji · R15 Teklif anının havası · R16 Başka navigasyon uygulamasından mesafe · R17 Yedek ve geri yükleme · R18 Anonim analiz dışa aktarımı · R19 Uyarılar · R20 Çökme kaydı
- **Scooter (Sürüş Paneli):** S1 Scooter bağlantısı · S2 Canlı panel · S3 Sürüş kaydı · S4 Yolculuk sayacı · S5 Harici batarya · S6 Yeniden bağlanma · S7 Yüzen panel · S8 Tanı araçları
- **Genel:** G1 Tek vardiya / gün · G2 Ev konumu · G3 Dil (Türkçe / Azərbaycanca) · G4 Az bildirim · G5 İlk açılış kurulumu
- **Yeni:** N1 2 telefon modu · N2 Uzaktan kullanım · N3 Otomatik şifreli yedek · N4 Gün sonu raporu ve net kazanç · N5 Menzil uyarısı · N6 Hırsızlık koruması · N8 Günlük kazanç hedefi

**Sonra (4):** Y12 Yol kalitesi öğrenme · Y13 Yüzey ve titreşim ölçümü · Y14 Yol kalibrasyonu · N7 Sesli uyarılar → `docs/BEKLEYEN_OZELLIKLER.md`. WEEM 1.0 tamamen hazır olunca kullanıcıya sorulur; o zamana kadar yazılmaz.

**Çıkar (1):** N9 Bakım hatırlatıcı — yazılmaz.

## 4. Mimari

Şartname belgeleri: `docs/sartname/OZELLIK_HARITASI.md` (63 özellik → eski kod, test, veri, modül), `VERI_SOZLESMESI.md` (korunacak adlar), `TEST_ENVANTERI.md` (1.761 eski test/denetim, 1.582'si çevrilir). Ham okuma notları: `docs/sartname/bulgular/` (kısmi).

### 4.1 Katmanlar ve derleme

- Kotlin, Jetpack Compose, **tek Activity + alt menü:** Harita | Siparişler | Scooter | Uzaktan | Daha fazla. Her sekme kaldığı yerden devam eder; açılış sekmesi Harita.
- **İki Gradle build** (A1.1):
  - `logic/` — saf Kotlin/JVM, Android eklentisi yok; her ortamda derlenir ve test edilir (JDK 21, Maven Central). Alt projeler modül başına: `logic/core`, `logic/capture`, `logic/orders`, `logic/economics`, `logic/scooter`, `logic/map`, `logic/link`, `logic/remote`. Eski testlerin çevirisi buraya.
  - Kök build (Android) — `:app` ve modül başına ince Android katmanı (`:core` … `:remote`); `logic/` `includeBuild` ile bağlanır. Android modülleri yalnız servis, pencere, BLE, konum, bildirim, dosya/veritabanı erişimi ve Compose ekranları içerir.
- Kural: karar veren her kod `logic/`'te (eşik, durum makinesi, ayrıştırma, biçim); Android katmanında `if` yalnız platform durumu için. Hedef: satırların ≥%60'ı `logic/`.

### 4.2 Modül sınırları

| Modül | Sahibi olduğu iş | Sahibi olduğu veri (`VERI_SOZLESMESI`) | Bağımlı olduğu |
|---|---|---|---|
| `:app` | kabuk, alt menü, izin ve ilk açılış (G5), tema, dil (G3), sekmelerin ekranları | — | hepsi |
| `:core` | ortak tipler ve **mesaj sözleşmeleri** (4.4), olay akışı, depolama (eski biçimler), vardiya/gün/ev tek kural (G1, G2), saat ve cihaz kimliği, bildirim tablosu (G4), yedek/dışa aktarma (R17, R18, N3), çökme kaydı (R20) | `kuryelog` tercihleri, gün klasörü, `courier.db`, `crashes.jsonl`, ZIP biçimi | — |
| `:capture` | Wolt/Bolt ekran ve bildirim okuma, OCR, ayrıştırma (R1, R2, R16, R3 alanları); **ağ sınıfı yok** (test denetler); hiçbir şeye basmaz | `screens.jsonl`, `notifs.jsonl`; `WatchService`/`NotifService` adları | `:core` |
| `:orders` | teklif ve sipariş durum makinesi, aşamalar, varış, bekleme, gecikme, balonlar, rozet, nakit/bahşiş, boost, çevrimiçi süre, resmî değerler, uyarılar, gün sayfaları (R3, R5–R13, R15, R19) | `orders.jsonl`, `online-sessions.jsonl`, `economics-boosts/cash-*/platform-*.jsonl`, `courier.db` tabloları | `:core`, `:capture` (gözlem tipleri) |
| `:economics` | ekonomi v2, teklif puanı, gün sonu ve net kazanç, menzil, hedef (R4, N4, N5, N8) | `economics-offers/legs/idle/penalties/waits/exposure/offer-scores.jsonl` | `:core`, `:orders` (kayıt tipleri); enerji `ScooterBridge` üstünden |
| `:scooter` | Ninebot BLE (şifreleme, oturum, eşleşme), canlı panel, kayıt, sayaç, harici batarya, enerji, yüzen panel, tanı (S1–S8, R14) | `logger`, `pairing-v1`, Keystore `NinebotLogger.Pairing.v1`, `ninebot-logger.db`, `economics-scooter/energy.jsonl` | `:core` |
| `:map` | harita, harita verisi, rota motoru, navigasyon, bağlantılar, adres, sokak kararları, hız/batarya, hava, ışıklar, bisiklet, POI, istatistik, sürüş kayıtları, veri seti, görevler, bölge (Y1–Y11, Y15–Y21) | `files/mapdata/`, YolGo içe aktarılan tercihler | `:core` |
| `:link` | 2 telefon modu: taşıma, eşleştirme, şifreleme, kuyruk, saat farkı, sürüm; rol kuralı; hırsızlık koruması (N1, N6) | eşleşme anahtarları (yeni), giden kuyruk | `:core` |
| `:remote` | uzaktan kullanım: ekran yakalama, H.264, dokunuş iletimi (N2) | — | `:core`, `:link` (eşleştirme) |

Bağımlılık yönü tek: `:core` ← diğerleri; modüller birbirini yalnız `:core`'daki arayüzlerle görür (istisna: `:orders` → `:capture` gözlem tipleri, `:economics` → `:orders` kayıt tipleri). Döngü derleme hatasıdır.

### 4.3 Modüller arası arayüzler (`:core`, öneri — A2'de kesinleşir)

| Arayüz | Yöntemler | Üreten → tüketen | Eski karşılığı |
|---|---|---|---|
| `ShiftPort` | `state: StateFlow<ShiftState>`, `start(reason)`, `stop(reason)` | `:core` → hepsi | `GpsService` + `ShiftStateFeed`, YolGo `ShiftController` |
| `OrderEvents` | `offers: StateFlow<List<OfferSummary>>`, `stages: StateFlow<TripStages>`, `day: StateFlow<DaySummary>` | `:orders` → `:map`, `:economics`, `:link` | feed v2 + `stages` |
| `ScooterBridge` | `orderAccepted(tripId, atWallMs)`, `orderDone(tripId, atWallMs)`, `suspend energy(fromWallMs, toWallMs): EnergyBlock?`, `batteryPct(): Double?` (≤15 sn taze), `live: StateFlow<ScooterLive?>` | `:scooter` → `:orders`, `:economics`, `:map` | `ScooterBridge.Source` + `scooter/v1/live` |
| `NavPort` | `route: StateFlow<RouteStatus?>`, `open(target: NavTarget)` | `:map` → `:orders` (varış, M20), `:link` | `nav/v1/current`, YolGo intent |
| `WeatherPort` | `current(): WeatherNow?` (≤90 dk) | `:map` → `:orders` (R15), `:economics` | `weather/current` |
| `NotifyPort` | `post(id: NotificationId, …)`, `cancel(id)` | hepsi → `:core` | dağınık `notify(n)` |

Tek telefonda bu arayüzler aynı süreçte doğrudan çağrılır. 2 telefon modunda `:link` aynı arayüzün **uzak** uygulamasını takar (Provider'da `ScooterBridge` uzak, Receiver'da `OrderEvents` uzak); modüller farkı görmez. `energy()` her iki durumda da askıya alınan çağrıdır; uzakta yanıt gelene kadar kayıt "bekleniyor" durur.

### 4.4 Mesajlar (tek telefonda iç olay, 2 telefon modunda `:link` yükü)

Ortak kurallar: her mesaj `schemaVersion` taşır; okuyucu bilinmeyen alanı yok sayar (bugünkü feed kuralı); zamanlar gönderenin duvar saati (ms), alıcı saat farkıyla düzeltir; sayısal alan ölçülmediyse `null` (uydurulmaz).

| Mesaj | Yön (2 telefon) | Ne zaman | Alanlar | Eski kaynak |
|---|---|---|---|---|
| `ShiftState` v1 | P → R | vardiya başı/sonu, bağlanışta | `active` bool · `startedAtMs` · `endedAtMs` · `endReason` · `interrupted` bool · `generatedAtMs` (koordinat yok) | `shift/v1/state` |
| `OfferSummary` v1 | P → R | teklif görünür olunca, değişince (≥200 ms), kaybolunca | `offerKey` · `platform` `wolt\|bolt` · `firstSeenAtMs` · `lastSeenAtMs` · `visibleNow` · `expiresAtMs` · `displayedFeeAzn` · `platformDistanceKm` · `restaurant` (≤200) · `pickupZone`, `dropoffZone` `Z1..Z4\|unknown` · `dropoffKnown` · `shown{minLo, minHi, km, stops, readyInMin, approachMin, deliveryMin, totalMin, multiplier, boostPct}` · `score` 1–10 (R4) · `netAzn` · **yok:** `dropoffAddress`, müşteri adı/telefonu, notlar | feed v2 teklif + `v2` |
| `TripStages` v1 | P → R | her aşama değişiminde | `trips[≤3 açık + ≤3 biten (10 dk)]{tripId, platformPackage, active, stage to_pickup\|at_pickup\|to_dropoff\|at_dropoff, outcome delivered\|delivered_unverified\|cancelled\|unconfirmed, endedAtMs, events[{stage, atMs, pickup?, source?}]}` (müşteri verisi yok) | `stages` (`TripStages.kt`) |
| `NavTarget` v1 | P → R (Receiver yoksa P içinde) | Wolt/Bolt "Navigasyon" bağlantısı yakalanınca; yedek: okunan adresten çözülen nokta | `requestId` · `lat`, `lon` · `kind` `restaurant\|customer\|unknown` · `origin` `intent\|clipboard\|search\|order` · `sourcePackage` · `requestedAtMs` · `label` (yalnız restoran adı) — adres metni Provider'da çözülür, gönderilmez (Açık soru S5) | YolGo `IncomingLink` + nav `target` |
| `DaySummary` v1 | P → R | sipariş bitince, en çok 1/dk | `day` · platform başına `orders`, `grossAzn`, `netAzn`, `onlineMin` · `cashAzn` | `OrderHistory`, `OnlineSessions` |
| `EnergyRequest` / `EnergyResult` v1 | P → R / R → P | sipariş ya da bekleme parçası kapanınca; yanıt gelene kadar kuyrukta | istek `{requestId, segmentId, kind leg\|idle, fromWallMs, toWallMs}` · yanıt `{requestId, energy}` = `EnergySegments.energy` bloğu (ölçülemezse `not_measured`) | `ScooterBridge.energy` |
| `BatterySummary` v1 | R → P | 60 sn'de bir ya da %1 değişince; bağlantı kopunca | `atMs` · `linkActive` · `ageMs` · `internalPct` · `externalPct` · `rangeKm` · `tempC` | `scooter/v1/live` alt kümesi |
| `RouteStatus` v1 | R → P | rota açıkken değişince, en çok 1/sn | `target{lat, lon, kind, origin, requestedAtMs, sourcePackage}` (ev rotasında `null`) · `route{remainingM 0–500000, remainingTurns, measuredAtMs}` · `updatedAtMs` | `nav/v1/current` |
| `WeatherNow` v1 | R → P (S6) | yenilenince (≤90 dk geçerli) | `source` `open_meteo` · `fetchedAtMs` · `tempC`, `feelsC`, `rainMm`, `rainPct`, `windKmh`, `gustKmh`, `code` (WMO) | `weather/current` |

Saniyelik scooter verisi (`ScooterLive`, `economics-scooter.jsonl`) Receiver'dan **çıkmaz**; müşteri adı, telefonu, adresi, notları, bildirim 12 içeriği Receiver'a **gitmez** (`VERI_SOZLESMESI` Bölüm 9).

### 4.5 2 telefon modu (`:link`)

- Ayarlar → "2 telefon modu: Kapalı / Provider / Receiver"; kapalıyken tek telefon her şeyi yapar. Her telefon her rolü alabilir; sekmeler iki telefonda da aynıdır, veri asıl kaynağından gelir (kararlar 16–17 Ekim, Bölüm 8).
  - **Provider** = Wolt/Bolt telefonu (bugün Pixel 10 Pro, sürüşte çoğunlukla cepte): ekran ve bildirim okuma, siparişler, kazanç, gün sonu, net kazanç, yedek. Scooter Bluetooth'u kapalı.
  - **Receiver** = scooter'a sabit telefon: harita ve navigasyon, Sürüş Paneli (scooter'a tek Bluetooth bağlantısı), saniyelik scooter verisi, enerji kartı, sesli teklif kartı (kabul her zaman Provider'da Wolt/Bolt'ta, otomatik kabul yok). Ekran okuma yok; müşteri adı/telefonu saklanmaz.
  - Rota: Wolt "Navigasyon" bağlantısını Provider yakalar → `NavTarget` → Receiver'da rota kendiliğinden açılır; unutulursa okunan adresten çözülen nokta; Receiver'a ulaşılamazsa rota Provider'da açılır.
- **Zarf** (öneri): `{v:1, type, id (UUID), seq (gönderen başına artan), sentAtMs, ttlMs, payload}`. Türler iki sınıf: *son durum geçerli* (`ShiftState`, `OfferSummary`, `BatterySummary`, `RouteStatus`, `WeatherNow`, `DaySummary`: kuyrukta yalnız en yenisi, TTL geçince atılır) ve *teslim zorunlu* (`TripStages` olayları, `EnergyRequest/Result`, yedek parçaları: kalıcı kuyruk, `ack` gelene kadar üstel geri çekilmeyle yeniden gönderilir, alıcı `id` ile tekrarları atar).
- **Taşıma:** Provider'ın hotspot'u üstünden TCP (Wi-Fi; Receiver'ın interneti de buradan), yoksa Bluetooth (yalnız küçük mesajlar; scooter BLE bağlantısını bozmaması A8.1'de ölçülür). Provider'da "Hotspot'u otomatik kapat" kapalı olmalı.
- **Eşleştirme ve şifreleme:** QR ile bir kez (cihaz kimliği, açık anahtar, tek kullanımlık jeton); oturum anahtarı anahtar değişimiyle türetilir, her mesaj kimlikli şifreli (AEAD), `seq` tekrar saldırısını önler. Anahtarlar Android Keystore'da (yeni takma ad; `VERI_SOZLESMESI` yeni kalem). Algoritma seçimi A8.2'de.
- **Bağlanışta:** sürüm denetimi (`protocolVersion` ana sürümü eşit olmalı; küçük fark: bilinmeyen alan yok sayılır; uygulama sürüm farkı Daha fazla'da uyarı), saat farkı ölçümü (4 zaman damgalı ping, 5 dk'da bir yenilenir), son durum mesajlarının tamamı yeniden gönderilir.
- **Hırsızlık koruması (N6):** `:link` bağlantı durumu + scooter hareketi (`ScooterLive.speedKmh`) + telefon sensörü; ayrıntı Açık soru S10.

### 4.6 Uzaktan kullanım (`:remote`)

Yalnız telefonlar yakınken Wi-Fi (`:link` eşleşmesi üstünden). Ekran yakalama (MediaProjection; Android 14+ her oturumda kullanıcı onayı) + H.264 donanım kodlayıcı; dokunuş, okuma servisinden **ayrı** bir erişilebilirlik servisiyle yalnız kullanıcının uzaktan dokunuşu olarak iletilir (`WatchService` dokunma yetkisi almaz). Ekranda oturum işareti; tek dokunuşla bitirme. Kapsam ve yön Açık soru S12.

### 4.7 Bağımlılıklar

Az ve sabit sürümlü: Compose BOM, Play Services Location, Maps SDK, ML Kit text-recognition (Rawstats'taki sürüm), WorkManager, coroutines; OSM görünümü için kütüphane Aşama 6'da seçilir. Sunucu, analitik, reklam yok.

## 5. Kurallar (ZORUNLU)

- **Şartname testlerdir:** bir modül yazılmadan önce eski testleri (Rawstats ~800, YolGo ~640, Sürüş Paneli) Kotlin'e çevrilir: aynı girdi → aynı çıktı. Yeni kod bunları geçmeden birleşmez. Saha kayıtları (ekran kareleri, günlükler) varsa altın test olur.
- **Gölge mod:** her aşamanın `.dev` sürümü eski uygulamaların yanında çalışır; gün sonunda iki kaydı karşılaştıran rapor. Fark sıfırlanmadan geçiş yok.
- **Bilinen eski hatalar baştan doğru:** Android'de derlenmeyen desen (0.25.1, ICU) → tüm Regex desenleri ICU testinden geçer; bildirim numaraları tek tabloda (eski 5103 ve nakit bahşiş çakışması yok); ev bitişi GPS kayması (F4.5b), ışık kuralı ≤1 m, A* performansı için test.
- Ekran okuma kodu ağ kullanmaz (test denetler). **WEEM Wolt/Bolt'ta kendiliğinden hiçbir şeye basmaz**; uzaktan kullanım yalnız kullanıcının dokunuşunu iletir, okuma servisi dokunma yetkisi almaz.
- `allowBackup=false` + `dataExtractionRules`; dışa aktarmada müşteri bilgisi gizlenir.
- **Derleme:** 17 Ekim'deki çalışma ortamında `dl.google.com` kapalıydı (Android SDK ve Google Maven inmiyor). Bu yüzden saf Kotlin mantık ayrı bir Gradle build'inde (`logic/`, Android eklentisi yok) durur ve her ortamda derlenip test edilir (JDK 21, Maven Central); Android katmanı CI'da derlenir. Ortam ağ ayarında `dl.google.com`'a izin verilirse yerelde tam derleme yapılır.
- **CI:** PR'da test + lint (Gradle önbellekli, `.md` hariç); APK yalnız `weem-v*` etiketi ya da elle; ara commit'ler `[skip ci]`; Actions ayda 2.000 dk (Myapplab ortak havuzu, `kurye_nav` ile paylaşılır).
- Commit: `feat:` `fix:` `perf:` `refactor:` `test:` `docs:` `build:`; bir mantıksal değişiklik = bir commit. Cihazda denenmeyene "çalışıyor" denmez.

## 6. Yol haritası

Her aşama: testler yeşil → `.dev` APK → telefonda gölge mod denemesi (kullanıcı). Süreler kabaca, oturum sayısı.

| Aşama | İçerik | Bitti sayılır | Süre |
|---|---|---|---|
| 0 | Kapsam seçimi (bitti); planın son hâli: şartname haritası, veri sözleşmesi, test envanteri, ayrıntılı iş listesi (bitti, A0.1); açık soruların yanıtı; kullanıcının kodsuz saha denemesi | `docs/sartname/` belgeleri + güncel iş listesi | 1 (bitti) |
| 1 | Gradle iskeleti (`logic/` ayrı build) ve modüller; alt menü kabuğu, tema, dil; imza, sürüm, `.dev`; CI | Boş sekmeli `.dev` APK kurulur | 4 |
| 2 | `:core`: depolama, eski Rawstats verisini okuma, vardiya/gün/ev tek kural, saat/cihaz kimliği, bildirim tablosu, çökme kaydı; modüller arası arayüzler (4.3) | Eski kayıtlar WEEM'de okunur | 5 |
| 3 | `:capture` + `:orders` (R1–R3, R5–R13, R15–R16, R19) | Pixel'de gölge mod: WEEM ile Rawstats aynı günü aynı kaydeder | 9–11 |
| 4 | `:economics` + yedek (R4, R17–R18, N3, N4, N8) | Altın vektörler geçer; gün sonu eski verilerle tutarlı | 3–4 |
| 5 | `:scooter` (S1–S8, R14) | Yedek telefonda Sürüş Paneli ile aynı değerler | 4–5 |
| 6 | `:map` (Y1–Y11, Y15–Y21, N5) | YolGo testleri geçer; yan yana sürüşte aynı rotalar | 10–12 |
| 7 | YolGo verisini içe aktarma (eski YolGo'ya dışa aktarma eki gerekebilir, S1); G1–G5 bütünlüğü | Yedek telefonda YolGo'dan tam taşıma | 2–3 |
| 8 | `:link` 2 telefon modu (N1) + hırsızlık koruması (N6) | İki telefonla gerçek vardiya | 4–5 |
| 9 | `:remote` uzaktan kullanım (N2) | Yakınken öteki telefon kullanılır | 2–3 |
| 10 | Optimizasyon (pil, bellek, APK boyutu, arka planda kalma), kod sadeleştirme (`/simplify`), güvenlik ve büyük denetim, geri yükleme provası; **WEEM 1.0**; geçiş: ZIP yedek → WEEM Rawstats'ın üstüne → YolGo içe aktar | İş telefonlarında WEEM | 4 |
| 11 | `BEKLEYEN_OZELLIKLER.md` kullanıcıya sorulur | Kullanıcı kararı | — |

Toplam 47 oturumluk görev (`docs/WEEM_ISLER.md`; aksamalarla kabaca 50–55 oturum) + telefon denemeleri. Ara `.dev` sürümleri gölge modda sahada denenir; geçiş yalnız WEEM 1.0 ile.

**Satır tahmini (kabaca, A0.1'de güncellendi):** bugün ana kod ~45.300 satır (YolGo Dart 24.352 + `android_overlay` Kotlin ~4.600, Rawstats 14.423, Sürüş Paneli 1.980); test 1.761 test/denetim (Rawstats 785, Sürüş Paneli 324 denetim / 14 suit, YolGo Dart 564 + Kotlin 88), 1.582'si çevrilir. WEEM 1.0 ana kod: `:map` 15–18 bin, `:orders` 7–8 bin, `:capture` 2,5–3 bin, `:economics` 3–4 bin, `:core` 3–4 bin, `:scooter` 2–2,5 bin, `:link` 2,5–3,5 bin, `:remote` 1,5–2 bin, `:app` 2–3 bin → **~40–48 bin**, tamamı Kotlin; test ~26–30 bin. Satır sayısı amaç değil; asıl kazanç tek dil, tek kural kaynağı.

## 7. Riskler ve donanım

- Süre uzun ve WEEM 1.0 tüm ilk sürüm özellikleriyle geliyor → iş telefonundaki geçiş geç olur. Önlem: her aşama gölge modda sahada.
- İkinci harita görünümü (Y2) ve uzaktan kullanım (N2) ek iş; Android 14+ ekran yakalama onayı, Play Protect, Xiaomi arka plan kısıtları.
- Yeniden yazımda sahada düzeltilmiş durumların kaçması → testler ve gölge mod karşılaştırması.
- Harici batarya enerjisi hâlâ ölçülmüyor (açık sorun).
- **Y12 ile rota bağı:** "sonra" seçilen Y12 verisi bugün rota maliyetine giriyor; Y12'siz Y3 YolGo'dan farklı rota verebilir (Açık soru S3).
- **YolGo verisinin taşınması:** YolGo'da ayarlar, sokak kararları, ışıklar, POI düzeltmeleri, görevler için dışa aktarma yok; WEEM başka paketin özel verisini okuyamaz → eski YolGo'ya küçük ek gerekir (Açık soru S1).
- **Şartname eksikleri:** çok ajanlı okuma usage nedeniyle yarıda kaldı; Sürüş Paneli (S1–S8) ve Y1, Y2, Y4, Y8, Y10, Y15–Y19, Y21, R4, R12–R15, R18 için ayrıntılı bulgu yok → ilgili aşamanın şartname görevi (A3.1, A5.1, A6.1) bunları tam çıkarır. Bu görevler sıralı ve tek ajanla (usage) planlandı.
- **Bilinen eski hatalar** (baştan doğru): ICU'da derlenmeyen desen (`NavDistance` `(?U)`, 0.25.1), bildirim no 5103 iki sabitte ve nakit bahşiş aralığı 4000–8095 diğer numaraları kapsıyor, F4.5b ev bitişinde GPS kayması (Açık soru S2), ışık kuralı ≤1 m (sahada tutmayabilir), A* yaya kenarlarıyla büyüyen arama (F6.3).
- **Scooter eşleşmesi iki telefonda:** Ninebot'un aynı anda iki telefonla eşleşmeyi kabul edip etmediği bilinmiyor (K0.1'de denenir).
- **En kritik bilinmeyen:** Pixel cepteyken ve ekranı kapalıyken Wolt/Bolt tekliflerinin okunup okunmadığı. Kodsuz saha denemesinde ölçülür (iş listesi K0.1).
- **Receiver telefonu:** GPS şart (birçok Wi-Fi tablette yok), güneşte okunur ekran, IP67/68, scooter ya da powerbank beslemesi, %80 şarj sınırı (sürekli şarj), ısınmaya dayanım, hırsızlığa karşı hızlı çıkarılır tutucu ve uygulama sabitleme. Yeni almadan önce yedek S22 ya da Poco X3 ile denenir.

## 8. Kullanıcı kararları

| Karar | Seçim |
|---|---|
| Uygulama | **WEEM**, tamamı **native, sıfırdan** (Kotlin + Compose), davranış aynı — 17 Ekim. Simge: _bekliyor_ |
| Kapsam | Bölüm 3; ilk sürüm seçilenler kesinlikle 1.0'da; sonra seçilenler `BEKLEYEN_OZELLIKLER.md`'de, 1.0 tam hazır olunca sorulur — 17 Ekim |
| Paket / imza | `com.kuryelog.app`, Rawstats anahtarı — 10 Ekim |
| İnternet | Uygulama internetli, ekran okuma kısmı ağsız — 10 Ekim |
| Açılış ekranı | Harita — 10 Ekim |
| Wolt/Bolt | Pixel 10 Pro'da (Provider) — 16 Ekim |
| Receiver interneti | Pixel'in hotspot'u — 16 Ekim |
| Pixel sürüşte | Çoğunlukla cepte — 16 Ekim |
| 2 telefon modu | Provider / Receiver; her telefon her rolü alabilir; WEEM iki telefonda tam işlevli — 17 Ekim |
| Veri bölüşümü | Provider sipariş olaylarını gönderir; scooter verisi ve enerji kartı Receiver'da; net kazanç Provider'da — 17 Ekim |
| Bağlantı | Wi-Fi (hotspot) + Bluetooth — 17 Ekim |
| Arayüz | Altta 4–5 sekme, sekme durumu korunur — 17 Ekim |
| Uzaktan kullanım | WEEM içinde modül (AnyDesk benzeri), yalnız telefonlar yakınken — 17 Ekim |
| Ekran paylaşımlı düzen | "Her şey scooter telefonunda + ekran paylaşımı" seçilmedi — 17 Ekim |
| Çalışma düzeni | 2–3 Claude hesabı sırayla; repo `Myapplab/Weem` (eski repo şartname); planın son hâli ultracode ile yeni oturumda — 17 Ekim |
| Receiver telefonu | _bekliyor_ (önce yedek Poco/S22 ile deneme) |

## 9. İlerleme

| Tarih | Adım | Kim | Sonuç |
|---|---|---|---|
| 2026-10-10 | Modül birleşme planı (`tek-uygulama`) | Claude | 17 Ekim'de iptal |
| 2026-10-10 | Rawstats araç yükseltmesi: Gradle 9.3.1, AGP 9.1.0, Kotlin 2.4.0, SDK 36 | Codex + Claude | `rawstats`'ta; WEEM aynı sürümlerle başlar |
| 2026-10-17 | Native sıfırdan kararı; kapsam seçimi (58 ilk sürüm, 4 sonra, 1 çıkar); bu plan | Claude | `kurye_nav` oturum dalı `ccr-4d54acb0-ohiz3j` |
| 2026-10-17 | Repo `Myapplab/Weem` kuruldu: kurallar (hesaplar arası devam, model/effort), iş listesi, oturum başlangıç kancası | Claude | İlk commit |
| 2026-10-17 | Repo kullanıcı tarafından Myapplab'a taşındı; son plan kontrolü: 2 telefon akışı, donanım, kritik deneme, sadeleştirme, satır tahmini | Claude | `9a4c75a` |
| 2026-10-17 | A0.1 planın son hâli: `docs/sartname/` (özellik haritası, veri sözleşmesi, test envanteri, ham bulgular), mimari 4.1–4.7, iş listesi oturumluk görevler, açık sorular. Çok ajanlı okuma usage nedeniyle yarıda durduruldu; kalan ayrıntı aşama şartname görevlerinde | Claude | dal `claude/weem-plan-specification-dq4yxx` |

**Sıradaki:** `docs/WEEM_ISLER.md` → "Şu an".
