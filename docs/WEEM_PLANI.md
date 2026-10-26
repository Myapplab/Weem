# WEEM planı — native (Kotlin + Compose), sıfırdan

Durum: **17 Ekim 2026 — karar verildi, kapsam seçildi, şartname çıkarıldı (A0.1: `docs/sartname/`), açık sorular yanıtlandı (A0.2, Bölüm 8); kod yazılıyor (Aşama 1).** YolGo + Rawstats + Sürüş Paneli tek native uygulama **WEEM** olarak sıfırdan yazılır. Önceki "modül olarak birleştir" planı (`TEK_UYGULAMA_PLANI.md`, 10–17 Ekim) iptal; bu dosya onun yerine geçti (eski hâli git geçmişinde).

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
- **YolGo verisi** (`az.duzyol.app`): eski YolGo'ya ek yapılmaz (S1). Bugün dışa aktarılabilen kayıtlar (`duzyol-journal-v1`: gün günlüğü, sürüşler, gün raporları) WEEM'e içe aktarılır; aktarılamayanlar (ayarlar ve ev, sokak kararları, ışıklar, POI düzeltmeleri, görevler, yol kalitesi) WEEM'de yeniden girilir ya da yeniden öğrenilir.
- minSdk 29, target/compile 36. Debug = `applicationIdSuffix ".dev"` → eski uygulamaların yanına ayrı kurulur (gölge mod).
- Kesin liste (bileşen, izin, tercih anahtarı, veritabanı, dosya, Keystore takma adı, bildirim kanalı/numarası) ve kaynak satırları: `docs/sartname/VERI_SOZLESMESI.md`.

## 3. Özellik kapsamı (kullanıcı seçimi, 17 Ekim)

Kaynak: seçim sayfası <https://claude.ai/artifact/5RTwBJxg8JHXCgUTbLAGyD> (kayıt `secim/v1`). Her özelliğin ayrıntılı davranışı başvuru noktalarındaki kod ve testlerdir.

**İlk sürüm (59) — WEEM 1.0'da kesinlikle olacak:**
- **Harita ve navigasyon (YolGo):** Y1 Harita ekranı · Y2 İkinci harita görünümü (OSM) · Y3 Kendi rota motoru · Y4 Navigasyon · Y5 Wolt/Bolt navigasyon bağlantıları · Y6 Adres bulma · Y7 Harita verisi · Y8 Sokak kararları · Y9 Hız ve batarya tahmini · Y10 Hava durumu · Y11 Trafik ışığı duruşları · Y12 Yol kalitesi öğrenme (17 Ekim A0.2'de eklendi; sıfırdan öğrenir, Ses+/Ses− tuşu yakalama dahil) · Y15 Bisiklet yolu katmanı · Y16 Restoran noktaları · Y17 İstatistik ekranı · Y18 Sürüş kayıtları · Y19 Teklif veri seti ekranı · Y20 Görev kartları ve hatırlatıcılar · Y21 Bölge bilgisi
- **Siparişler ve kazanç (Rawstats):** R1 Wolt ve Bolt ekranını okuma · R2 Bildirim okuma · R3 Teklif kaydı · R4 Teklif puanı · R5 Sipariş takibi · R6 Restoran ve müşteri balonları · R7 Kurye uygulaması üstünde gösterge · R8 Nakit ve bahşiş · R9 Cepteki nakit · R10 Boost / Earn extra · R11 Çevrimiçi süreler · R12 Siparişler ve gün sayfaları · R13 Resmî değerlerle karşılaştırma · R14 Sipariş başına enerji · R15 Teklif anının havası · R16 Başka navigasyon uygulamasından mesafe · R17 Yedek ve geri yükleme · R18 Anonim analiz dışa aktarımı · R19 Uyarılar · R20 Çökme kaydı
- **Scooter (Sürüş Paneli):** S1 Scooter bağlantısı · S2 Canlı panel · S3 Sürüş kaydı · S4 Yolculuk sayacı · S5 Harici batarya · S6 Yeniden bağlanma · S7 Yüzen panel · S8 Tanı araçları
- **Genel:** G1 Tek vardiya / gün · G2 Ev konumu · G3 Dil (Türkçe / Azərbaycanca) · G4 Az bildirim · G5 İlk açılış kurulumu
- **Yeni:** N1 2 telefon modu · N2 Uzaktan kullanım · N3 Otomatik şifreli yedek · N4 Gün sonu raporu ve net kazanç · N5 Menzil uyarısı · N6 Hırsızlık koruması · N8 Günlük kazanç hedefi

**Sonra (3):** Y13 Yüzey ve titreşim ölçümü · Y14 Yol kalibrasyonu · N7 Sesli uyarılar → `docs/BEKLEYEN_OZELLIKLER.md`. **S32 (21 Ekim):** Y13 + Y14 ve S20 WEEM 1.1'de (geçişten hemen sonra); N7 çıkarıldı.

**Çıkar (1):** N9 Bakım hatırlatıcı — yazılmaz.

## 4. Mimari

Şartname belgeleri: `docs/sartname/OZELLIK_HARITASI.md` (63 özellik → eski kod, test, veri, modül), `VERI_SOZLESMESI.md` (korunacak adlar), `TEST_ENVANTERI.md` (1.761 eski test/denetim, 1.601'i çevrilir; A3.1'de güncellendi), aşama şartnameleri (`CORE.md`: Aşama 2; `CAPTURE.md`, `ORDERS.md`: Aşama 3; `LINK.md`: Aşama 8). Ham okuma notları: `docs/sartname/bulgular/` (kısmi).

### 4.1 Katmanlar ve derleme

- Kotlin, Jetpack Compose, **tek Activity + alt menü:** Harita | Siparişler | Scooter | Uzaktan | Daha fazla. Her sekme kaldığı yerden devam eder; açılış sekmesi Harita.
- **İki Gradle build** (A1.1):
  - `logic/` — saf Kotlin/JVM, Android eklentisi yok; her ortamda derlenir ve test edilir (JDK 21, Maven Central). Alt projeler: `logic/app` (kabuk mantığı: sekme, dil, tema, palet; A1.3) ve modül başına `logic/core`, `logic/capture`, `logic/orders`, `logic/economics`, `logic/scooter`, `logic/map`, `logic/link`, `logic/remote`. Eski testlerin çevirisi buraya.
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
| `:map` | harita, harita verisi, rota motoru, navigasyon, bağlantılar, adres, sokak kararları, hız/batarya, hava, ışıklar, yol kalitesi öğrenme, bisiklet, POI, istatistik, sürüş kayıtları, veri seti, görevler, bölge (Y1–Y12, Y15–Y21) | `files/mapdata/`, `:map` ayarları, yol kalitesi verisi (`roads.*` karşılığı, sıfırdan), içe aktarılan YolGo kayıtları | `:core` |
| `:link` | 2 telefon modu: taşıma, eşleştirme, şifreleme, kuyruk, saat farkı, sürüm; rol kuralı; hırsızlık koruması (N1, N6) | eşleşme anahtarları (yeni), giden kuyruk | `:core` |
| `:remote` | uzaktan kullanım: ekran yakalama, H.264, dokunuş iletimi (N2) | — | `:core`, `:link` (eşleştirme) |

Bağımlılık yönü tek: `:core` ← diğerleri; modüller birbirini yalnız `:core`'daki arayüzlerle görür (istisna: `:orders` → `:capture` gözlem tipleri, `:economics` → `:orders` kayıt tipleri). Döngü derleme hatasıdır.

### 4.3 Modüller arası arayüzler (`:core`; A2.5'te kesinleşti, `logic/core/Ports.kt`)

| Arayüz | Yöntemler | Üreten → tüketen | Eski karşılığı |
|---|---|---|---|
| `ShiftPort` | `state: StateFlow<ShiftState>`, `start(reason)`, `stop(reason)` | `:core` → hepsi | `GpsService` + `ShiftStateFeed`, YolGo `ShiftController` |
| `OrderEvents` | `offers: StateFlow<List<OfferSummary>>`, `stages: StateFlow<TripStages>`, `day: StateFlow<DaySummary>` | `:orders` → `:map`, `:economics`, `:link` | feed v2 + `stages` |
| `ScooterBridge` | `orderAccepted(tripId, atWallMs)`, `orderDone(tripId, atWallMs)`, `suspend energy(fromWallMs, toWallMs): EnergyBlock?`, `batteryPct(): Double?` (≤15 sn taze), `live: StateFlow<ScooterLive?>` | `:scooter` → `:orders`, `:economics`, `:map` | `ScooterBridge.Source` + `scooter/v1/live` |
| `NavPort` | `route: StateFlow<RouteStatus?>`, `open(target: NavTarget)` | `:map` → `:orders` (varış, M20), `:link` | `nav/v1/current`, YolGo intent |
| `WeatherPort` | `current(): WeatherNow?` (≤90 dk) | `:map` → `:orders` (R15), `:economics` | `weather/current` |
| `NotifyPort` | `post(id: NotificationId, …)`, `cancel(id)` | hepsi → `:core` | dağınık `notify(n)` |

A2.5'te kesinleşen farklar: `ShiftPort.start/stop` nedeni varsayılan `manual` (evden çıkış/eve dönüş kuralın kendisi); `OrderEvents.busy()` (açık sipariş, ev kuralına gider); `NotifyPort.post(Notify)` (numara `NotificationIds` tablosundan, değilse hata); modüllerin vardiya başı/sonu işleri `ShiftListeners`'a takılır; üreticisi yazılmamış arayüzün `NONE` uygulaması veri uydurmaz. A3.1'de eklenen modül arayüzleri (`:core` dışında tanımlı, izinli yönde): `CaptureSink`/`CaptureHost` (`:capture` tanımlar, `:orders` uygular), `OrderObserver`/`OfferAnnotator` (`:orders` tanımlar, `:economics` uygular), Android `:core`'da `OwnOverlays` (kendi pencerelerimizin dikdörtgeni, OCR maskesi); `:app` bağlar, bağlanmamışsa `NONE` (`CAPTURE.md` 7.1, `ORDERS.md` 4.14, 13). Tek telefonda bu arayüzler aynı süreçte doğrudan çağrılır. 2 telefon modunda `:link` aynı arayüzün **uzak** uygulamasını takar (Provider'da `ScooterBridge` uzak, Receiver'da `OrderEvents` uzak); modüller farkı görmez. `energy()` her iki durumda da askıya alınan çağrıdır; uzakta yanıt gelene kadar kayıt "bekleniyor" durur.

### 4.4 Mesajlar (tek telefonda iç olay, 2 telefon modunda `:link` yükü)

Ortak kurallar (A2.5'te kod: her tip `toJson()`/`parse()`, `MessagesTest` gidiş-dönüş ve bugünkü feed yükleriyle): her mesaj `schemaVersion` taşır (bugünkü `stages` `version` yazar; okuyucu ikisini de kabul eder); okuyucu bilinmeyen alanı yok sayar (bugünkü feed kuralı); zamanlar gönderenin duvar saati (ms), alıcı saat farkıyla düzeltir; sayısal alan ölçülmediyse `null` (uydurulmaz).

| Mesaj | Yön (2 telefon) | Ne zaman | Alanlar | Eski kaynak |
|---|---|---|---|---|
| `ShiftState` v1 | P → R | vardiya başı/sonu, bağlanışta | `active` bool · `startedAtMs` · `endedAtMs` · `endReason` · `interrupted` bool · `generatedAtMs` (koordinat yok) | `shift/v1/state` |
| `OfferSummary` v1 | P → R | teklif görünür olunca, değişince (≥200 ms), kaybolunca | `offerKey` · `platform` `wolt\|bolt` · `firstSeenAtMs` · `lastSeenAtMs` · `visibleNow` · `expiresAtMs` · `displayedFeeAzn` · `platformDistanceKm` · `restaurant` (≤200) · `pickupZone`, `dropoffZone` `Z1..Z4\|unknown` · `dropoffKnown` · `shown{minLo, minHi, km, stops, readyInMin, approachMin, deliveryMin, totalMin, multiplier, boostPct}` · `score` 1–10 (R4) · `netAzn` · `dropoffAddress` (adres metni, okunduysa; S5) · `dropoffLat`, `dropoffLon` (çözüldüyse) · **yok:** müşteri adı/telefonu, notlar | feed v2 teklif + `v2` |
| `TripStages` v1 | P → R | her aşama değişiminde | `trips[≤3 açık + ≤3 biten (10 dk)]{tripId, platformPackage, active, stage to_pickup\|at_pickup\|to_dropoff\|at_dropoff, outcome delivered\|delivered_unverified\|cancelled\|unconfirmed, endedAtMs, events[{stage, atMs, pickup?, source?}]}` (müşteri verisi yok) | `stages` (`TripStages.kt`) |
| `NavTarget` v1 | P → R (Receiver yoksa P içinde) | Wolt/Bolt "Navigasyon" bağlantısı yakalanınca; yedek: okunan adresten çözülen nokta | `requestId` · `lat`, `lon` · `kind` `restaurant\|customer\|unknown` · `origin` `intent\|clipboard\|search\|order` · `sourcePackage` · `requestedAtMs` · `label` (restoran adı ya da müşteri adres metni: sokak, bina, kat, giriş; S5) — müşteri adı, telefonu, notları yok | YolGo `IncomingLink` + nav `target` |
| `DaySummary` v1 | P → R | sipariş bitince, en çok 1/dk | `day` · platform başına `orders`, `grossAzn`, `netAzn`, `onlineMin` · `cashAzn` | `OrderHistory`, `OnlineSessions` |
| `EnergyRequest` / `EnergyResult` v1 | P → R / R → P | sipariş ya da bekleme parçası kapanınca; yanıt gelene kadar kuyrukta | istek `{requestId, segmentId, kind leg\|idle, fromWallMs, toWallMs}` · yanıt `{requestId, energy}` = `EnergySegments.energy` bloğu (ölçülemezse `not_measured`) | `ScooterBridge.energy` |
| `BatterySummary` v1 | R → P | 60 sn'de bir ya da %1 değişince; bağlantı kopunca | `atMs` · `linkActive` · `ageMs` · `internalPct` · `externalPct` · `rangeKm` · `tempC` | `scooter/v1/live` alt kümesi |
| `RouteStatus` v1 | R → P | rota açıkken değişince, en çok 1/sn | `target{lat, lon, kind, origin, requestedAtMs, sourcePackage}` (ev rotasında `null`) · `route{remainingM 0–500000, remainingTurns, measuredAtMs}` · `updatedAtMs` | `nav/v1/current` |
| `WeatherNow` v1 | gönderilmez (S6: her telefon kendi internetinden çeker; tip yalnız iç olay) | yenilenince (≤90 dk geçerli) | `source` `open_meteo` · `fetchedAtMs` · `tempC`, `feelsC`, `rainMm`, `rainPct`, `windKmh`, `gustKmh`, `code` (WMO) | `weather/current` |

Saniyelik scooter verisi (`ScooterLive`, `economics-scooter.jsonl`) Receiver'dan **çıkmaz**; müşteri adı, telefonu, notları, bildirim 12 içeriği Receiver'a **gitmez**; müşteri koordinatı ve adres metni **gider** (S5; `VERI_SOZLESMESI` Bölüm 9).

### 4.5 2 telefon modu (`:link`)

- Ayarlar → "2 telefon modu: Kapalı / Provider / Receiver"; kapalıyken tek telefon her şeyi yapar. Her telefon her rolü alabilir; sekmeler iki telefonda da aynıdır, veri asıl kaynağından gelir (kararlar 16–17 Ekim, Bölüm 8).
  - **Provider** = Wolt/Bolt telefonu (bugün Pixel 10 Pro, sürüşte çoğunlukla cepte): ekran ve bildirim okuma, siparişler, kazanç, gün sonu, net kazanç, yedek. **Provider rolünde WEEM scooter'a hiç bağlanmaz** (S11); resmî Ninebot uygulaması Pixel'de gerektiğinde kullanılır, sonra Pixel'in Bluetooth'u kapatılır ya da scooter unutturulur.
  - **Receiver** = scooter'a sabit telefon (bugün Poco): harita ve navigasyon, Sürüş Paneli (scooter'a tek Bluetooth bağlantısı), saniyelik scooter verisi, enerji kartı, sesli teklif kartı (kabul her zaman Provider'da Wolt/Bolt'ta, otomatik kabul yok). Ekran okuma yok; müşteri adı/telefonu saklanmaz.
  - Rota: Wolt "Navigasyon" bağlantısını Provider yakalar → `NavTarget` → Receiver'da rota kendiliğinden açılır; unutulursa okunan adresten çözülen nokta; Receiver'a ulaşılamazsa rota Provider'da açılır.
- **Zarf** (öneri): `{v:1, type, id (UUID), seq (gönderen başına artan), sentAtMs, ttlMs, payload}`. Türler iki sınıf: *son durum geçerli* (`ShiftState`, `OfferSummary`, `BatterySummary`, `RouteStatus`, `DaySummary`: kuyrukta yalnız en yenisi, TTL geçince atılır) ve *teslim zorunlu* (`TripStages` olayları, `EnergyRequest/Result`, yedek parçaları: kalıcı kuyruk, `ack` gelene kadar üstel geri çekilmeyle yeniden gönderilir, alıcı `id` ile tekrarları atar).
- **Taşıma:** hotspot **hangi telefondaysa** onun Wi-Fi ağı üstünden TCP (S6; bugün hotspot Poco'da, Pixel ona bağlanıyor; rol ile hotspot sahibi birbirinden bağımsız, keşif iki yönde çalışır), yoksa Bluetooth (yalnız küçük mesajlar; klasik Bluetooth, S23; scooter BLE bağlantısını bozmaması A8.1'in ölçüm planıyla telefonda ölçülür). Ayrıntı `docs/sartname/LINK.md` Bölüm 1. Hotspot sahibinde "Hotspot'u otomatik kapat" kapalı olmalı. Her telefon havayı kendi internetinden alır.
- **Eşleştirme ve şifreleme:** QR ile bir kez (cihaz kimliği, açık anahtar, tek kullanımlık jeton); oturum anahtarı anahtar değişimiyle türetilir, her mesaj kimlikli şifreli (AEAD), `seq` tekrar saldırısını önler. Anahtarlar Android Keystore'da (yeni takma ad; `VERI_SOZLESMESI` yeni kalem). A8.2'de seçildi: Noise `XX` / `XXpsk3` (jeton = psk) + P-256 + AES-256-GCM + SHA-256, yalnız JCA; kimlik anahtarı Keystore AES anahtarıyla sarılı; ayrıntı `LINK.md` Bölüm 3; araçlar S24.
- **Bağlanışta:** sürüm denetimi (`protocolVersion` ana sürümü eşit olmalı; küçük fark: bilinmeyen alan yok sayılır; uygulama sürüm farkı Daha fazla'da uyarı), saat farkı ölçümü (4 zaman damgalı ping, 5 dk'da bir yenilenir), son durum mesajlarının tamamı yeniden gönderilir.
- **Hırsızlık koruması (N6):** Receiver'da çalışır: scooter hızı (`ScooterLive.speedKmh`) + Poco hareket sensörü + GPS kayması; devreye otomatik (aşama, Pixel uzak) + elle; alarm iki telefonda, bağlantı koparsa Poco SMS ile konum (S10; `LINK.md` Bölüm 5).

### 4.6 Uzaktan kullanım (`:remote`)

Yalnız telefonlar yakınken Wi-Fi (`:link` eşleşmesi üstünden). Ekran yakalama (MediaProjection; Android 14+ her oturumda kullanıcı onayı) + H.264 donanım kodlayıcı; dokunuş, okuma servisinden **ayrı** bir erişilebilirlik servisiyle yalnız kullanıcının uzaktan dokunuşu olarak iletilir (`WatchService` dokunma yetkisi almaz). Ekranda oturum işareti; tek dokunuşla bitirme. **Kapsam (S12): iki yönlü ve tam** — Pixel ↔ Poco, yanındaki telefondaki her şey (Wolt/Bolt dahil) öteki telefondan yönetilebilir; her iki telefonda hem gönderen hem alan taraf var.

### 4.7 Bağımlılıklar

Az ve sabit sürümlü: Compose BOM, Play Services Location, Maps SDK, ML Kit text-recognition (Rawstats'taki sürüm), WorkManager, coroutines; ikinci harita görünümü **MapLibre Native** (S4; iki stil: bugünkü OSM raster karoları ve vektör; APK boyutu A6.5'te ölçülür); 2 telefon eşleştirmesinde QR okuma Google code scanner (`play-services-code-scanner`) ve QR üretme ZXing core (S24; A8.3'te eklenir). Şifreleme yalnız platformun JCA'sıyla (ek kripto kütüphanesi yok; `LINK.md` 3.2). Sunucu, analitik, reklam yok.

## 5. Kurallar (ZORUNLU)

- **Şartname testlerdir:** bir modül yazılmadan önce eski testleri (Rawstats ~800, YolGo ~640, Sürüş Paneli) Kotlin'e çevrilir: aynı girdi → aynı çıktı. Yeni kod bunları geçmeden birleşmez. Saha kayıtları (ekran kareleri, günlükler) varsa altın test olur.
- **Gölge mod:** ~~her aşamanın `.dev` sürümü eski uygulamaların yanında çalışır; fark sıfırlanmadan geçiş yok.~~ **S28 (20 Ekim):** ara saha denemesi ve gölge mod karşılaştırması yapılmaz; aşamalar testler yeşil olunca biter. Telefonda ilk kullanım WEEM 1.0 geçişinden (A10.4) sonra; çıkan sorunlar A10.5'te düzeltilir (eski uygulamalar geçişe kadar telefonda kalır). Gölge mod anahtarı ve `shadowReport` aracı `.dev`'de kalır (isteğe bağlı).
- **Bilinen eski hatalar baştan doğru:** Android'de derlenmeyen desen (0.25.1, ICU) → tüm Regex desenleri ICU testinden geçer; bildirim numaraları tek tabloda (eski 5103 ve nakit bahşiş çakışması yok); ışık kuralı ≤1 m, A* performansı için test. Vardiya bitişi bugünkü Rawstats kuralıyla kalır (F4.5b; S2: ek GPS önlemi yok).
- Ekran okuma kodu ağ kullanmaz (test denetler). **WEEM Wolt/Bolt'ta kendiliğinden hiçbir şeye basmaz**; uzaktan kullanım yalnız kullanıcının dokunuşunu iletir, okuma servisi dokunma yetkisi almaz.
- `allowBackup=false` + `dataExtractionRules`; dışa aktarmada müşteri bilgisi gizlenir.
- **Derleme:** 17 Ekim'deki çalışma ortamında `dl.google.com` kapalıydı (Android SDK ve Google Maven inmiyor). Bu yüzden saf Kotlin mantık ayrı bir Gradle build'inde (`logic/`, Android eklentisi yok) durur ve her ortamda derlenip test edilir (JDK 21, Maven Central); Android katmanı CI'da derlenir. Ortam ağ ayarında `dl.google.com`'a izin verilirse yerelde tam derleme yapılır.
- **CI:** PR'da test + lint (Gradle önbellekli, `.md` hariç); APK yalnız `weem-v*` etiketi ya da elle; ara commit'ler `[skip ci]`; Actions ayda 2.000 dk (Myapplab ortak havuzu, `kurye_nav` ile paylaşılır).
- Commit: `feat:` `fix:` `perf:` `refactor:` `test:` `docs:` `build:`; bir mantıksal değişiklik = bir commit. Cihazda denenmeyene "çalışıyor" denmez.

## 6. Yol haritası

Her aşama: testler yeşil → `main` (S28: ara saha denemesi ve gölge mod karşılaştırması yok). Süreler kabaca, oturum sayısı. Aşama 0–9 bitti (20–21 Ekim); güncel durum `docs/WEEM_ISLER.md`.

| Aşama | İçerik | Bitti sayılır | Süre |
|---|---|---|---|
| 0 | Kapsam seçimi (bitti); planın son hâli: şartname haritası, veri sözleşmesi, test envanteri, ayrıntılı iş listesi (bitti, A0.1); açık soruların yanıtı; kullanıcının kodsuz saha denemesi | `docs/sartname/` belgeleri + güncel iş listesi | 1 (bitti) |
| 1 | Gradle iskeleti (`logic/` ayrı build) ve modüller; alt menü kabuğu, tema, dil; imza, sürüm, `.dev`; CI | Boş sekmeli `.dev` APK kurulur | 4 |
| 2 | `:core`: depolama, eski Rawstats verisini okuma, vardiya/gün/ev tek kural, saat/cihaz kimliği, bildirim tablosu, çökme kaydı; modüller arası arayüzler (4.3) | Eski kayıtlar WEEM'de okunur | 5 |
| 3 | `:capture` + `:orders` (R1–R3, R5–R13, R15–R16, R19) | Rawstats testleri geçer (S28: gölge mod denemesi yok) | 9–11 |
| 4 | `:economics` + yedek (R4, R17–R18, N3, N4, N8) | Altın vektörler geçer (S28) | 3–4 |
| 5 | `:scooter` (S1–S8, R14) | Sürüş Paneli testleri geçer (S28) | 4–5 |
| 6 | `:map` (Y1–Y12, Y15–Y21, N5) | YolGo testleri geçer (S28: yan yana sürüş yok) | 10–12 |
| 7 | YolGo kayıtlarını içe aktarma (eski YolGo'ya ek yok, S1; kalanı yeniden girilir/öğrenilir); G1–G5 bütünlüğü | İçe aktarma testleri geçer (S28) | 2–3 |
| 8 | `:link` 2 telefon modu (N1) + hırsızlık koruması (N6) | Testler yeşil (S28: gerçek vardiya A10.4 sonrası) | 4–5 |
| 9 | `:remote` uzaktan kullanım (N2) | Testler yeşil (S28) | 2–3 |
| 10 | Optimizasyon (pil, bellek, APK boyutu, arka planda kalma), kod sadeleştirme (`/simplify`), güvenlik ve büyük denetim, **WEEM 1.0**; geçiş (S31): arşiv ZIP → Rawstats ve YolGo kaldırılır → WEEM temiz kurulur, kayıtlar sıfırdan | İş telefonlarında WEEM | 4 |
| 11 | ~~`BEKLEYEN_OZELLIKLER.md` kullanıcıya sorulur~~ → S29: Aşama 10'da gerçek kullanımdan (A10.5) sonra, sadeleştirmeden (A10.6) önce sorulur (A10.5b) | Kullanıcı kararı | — |

Geçiş yalnız WEEM 1.0 ile (S31: sıfırdan, eski kayıtlar taşınmaz).

**Satır tahmini (kabaca, A0.1'de güncellendi):** bugün ana kod ~45.300 satır (YolGo Dart 24.352 + `android_overlay` Kotlin ~4.600, Rawstats 14.423, Sürüş Paneli 1.980); test 1.761 test/denetim (Rawstats 785, Sürüş Paneli 324 denetim / 14 suit, YolGo Dart 564 + Kotlin 88), 1.627'si çevrilir. WEEM 1.0 ana kod: `:map` 15–18 bin, `:orders` 7–8 bin, `:capture` 2,5–3 bin, `:economics` 3–4 bin, `:core` 3–4 bin, `:scooter` 2–2,5 bin, `:link` 2,5–3,5 bin, `:remote` 1,5–2 bin, `:app` 2–3 bin → **~40–48 bin**, tamamı Kotlin; test ~26–30 bin. Satır sayısı amaç değil; asıl kazanç tek dil, tek kural kaynağı.

## 7. Riskler ve donanım

- Süre uzun ve WEEM 1.0 tüm ilk sürüm özellikleriyle geliyor → iş telefonundaki geçiş geç olur. Önlem: her aşama gölge modda sahada.
- İkinci harita görünümü (Y2) ve uzaktan kullanım (N2) ek iş; Android 14+ ekran yakalama onayı, Play Protect, Xiaomi arka plan kısıtları.
- Yeniden yazımda sahada düzeltilmiş durumların kaçması → testler ve gölge mod karşılaştırması.
- Harici batarya enerjisi hâlâ ölçülmüyor (açık sorun).
- **Y12 sıfırdan öğrenir (S3):** YolGo'da öğrenilmiş yol puanları ve ters yön oranları taşınmaz; ilk haftalarda WEEM rotaları YolGo'dan farklı olabilir (A6.10'da fark bu nedenle açıklanır).
- **YolGo verisinin taşınması (S1):** ayarlar, sokak kararları, ışıklar, POI düzeltmeleri, görevler için dışa aktarma yok ve eski YolGo'ya ek yapılmayacak → bunlar WEEM'de yeniden girilir ya da öğrenilir (geçişte kullanıcı işi).
- **Şartname eksikleri:** çok ajanlı okuma usage nedeniyle yarıda kaldı; Sürüş Paneli (S1–S8) ve Y1, Y2, Y4, Y8, Y10, Y15–Y19, Y21, R4, R12–R15, R18 için ayrıntılı bulgu yok → ilgili aşamanın şartname görevi (A3.1, A5.1, A6.1) bunları tam çıkarır. Bu görevler sıralı ve tek ajanla (usage) planlandı. Durum (17 Ekim): A3.1 `CAPTURE.md`/`ORDERS.md` ve A5.1 `SCOOTER.md` (S1–S8, R14) yazıldı; kalan A6.1.
- **Bilinen eski hatalar** (baştan doğru): ICU'da derlenmeyen desen (`NavDistance` `(?U)`, 0.25.1), bildirim no 5103 iki sabitte ve nakit bahşiş aralığı 4000–8095 diğer numaraları kapsıyor, ışık kuralı ≤1 m (sahada tutmayabilir), A* yaya kenarlarıyla büyüyen arama (F6.3).
- **Scooter tek telefona bağlanır (S11):** WEEM'de scooter'a yalnız Receiver (Poco) bağlanır; Pixel'de resmî Ninebot uygulaması kullanıldıktan sonra Bluetooth kapatılmazsa Poco'nun bağlantısı aksayabilir (kullanıcı alışkanlığı).
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
| Receiver interneti | Pixel'in hotspot'u — 16 Ekim. **Not (17 Ekim, S6):** bugün hotspot Poco'dan Pixel'e veriliyor (Pixel'de kayıt sorunu var, çözülüyor); 2 telefon bağlantısı hotspot hangi telefondaysa onunla çalışmalı; her telefon havayı kendi internetinden alır |
| Pixel sürüşte | Çoğunlukla cepte — 16 Ekim |
| 2 telefon modu | Provider / Receiver; her telefon her rolü alabilir; WEEM iki telefonda tam işlevli — 17 Ekim |
| Veri bölüşümü | Provider sipariş olaylarını gönderir; scooter verisi ve enerji kartı Receiver'da; net kazanç Provider'da — 17 Ekim |
| Bağlantı | Wi-Fi (hotspot) + Bluetooth — 17 Ekim |
| Arayüz | Altta 4–5 sekme, sekme durumu korunur — 17 Ekim |
| Uzaktan kullanım | WEEM içinde modül (AnyDesk benzeri), yalnız telefonlar yakınken — 17 Ekim |
| Ekran paylaşımlı düzen | "Her şey scooter telefonunda + ekran paylaşımı" seçilmedi — 17 Ekim |
| Çalışma düzeni | 2–3 Claude hesabı sırayla; repo `Myapplab/Weem` (eski repo şartname); planın son hâli ultracode ile yeni oturumda — 17 Ekim |
| Receiver telefonu | Poco (scooter'a bağlanan tek telefon) — 17 Ekim (S11); kalıcı cihaz _bekliyor_ |
| S1 YolGo verisi | Eski YolGo'ya dışa aktarma eki yok. Aktarılamayan YolGo verisi WEEM'de yeniden girilir ya da yeniden öğrenilir — 17 Ekim |
| S2 Vardiya bitişi | Bugünkü kural (Rawstats F4.5b: ≤30 m doğruluk, evden ≤50 m, önce çıkış, <5 km/s, 30 sn, açık sipariş varken bitmez); ek GPS önlemi yok — 17 Ekim |
| S3 Y12 | Yol kalitesi öğrenme WEEM 1.0'da (59 ilk / 3 sonra); sıfırdan öğrenir (YolGo `roads.*` aktarılmaz); Ses+/Ses− tuşu yakalama dahil — 17 Ekim |
| S4 İkinci harita | MapLibre Native; iki stil: bugünkü OSM raster karoları ve vektör; APK boyutu A6.5'te ölçülür — 17 Ekim |
| S5 Receiver'a müşteri verisi | Müşteri koordinatı ve adres metni gider; müşteri adı, telefonu ve notlar gitmez — 17 Ekim |
| S6 Hava | Her telefon kendi internetinden alır (`WeatherNow` link mesajı değil) — 17 Ekim |
| S7 Eski sözleşmeler | Kaldırılır: `com.kuryelog.app.economics` sağlayıcısı, `SHARE_HOME`, `PICK_HOME` — 17 Ekim |
| S8 Net ve hedef | Net = brüt + bahşiş − filo komisyonu − enerji maliyeti (Wh × ayarlanabilir kWh fiyatı / şarj verimi). Hedef: günlük sabit tutar, net üzerinden, ayardan değişir — 17 Ekim |
| S9 Listede adı olmayan eski davranışlar | Hepsi kalır; yalnız "Google Maps'te rota açma" kaldırılır (görev kartındaki "Rota Oluştur" WEEM'in kendi navigasyonunu açar) — 17 Ekim |
| S10 Hırsızlık koruması | 21 Ekim (A8.4): koruma **scooter + Receiver telefonu (Poco)**; devreye **otomatik** (restoranda/müşteride ya da Pixel uzaklaşınca) **+ elle** ("Park"); alarm **Pixel'de ses + titreşim, Poco'da yüksek siren, Poco ekranında uyarı**; bağlantı koparsa **Poco SMS ile konum** yollar (Poco'da SIM gerekir) ve Pixel son bilinen konumu gösterir. WEEM scooter'a salt okur: kilitleyemez, scooter alarmını çaldıramaz. Ayrıntı `LINK.md` Bölüm 5 — 17/21 Ekim |
| S11 Scooter bağlantısı | Scooter tek telefona bağlanır (Receiver = Poco). Provider (Pixel) rolünde WEEM scooter'a hiç bağlanmaz; resmî Ninebot uygulaması Pixel'de gerektiğinde kullanılır, sonra Pixel Bluetooth'u kapatılır ya da scooter unutturulur — 17 Ekim |
| S12 Uzaktan kullanım | İki yönlü ve tam: yanındaki telefondaki (Pixel ↔ Poco) her şey yönetilebilir — 17 Ekim |
| S13 Usage | Ultracode yalnız gerekli yerlerde (Aşama 3 ve 6 şartnameleri, son büyük denetim); paralel ajan en çok 2–3, çıktı bölüm bölüm repoya + push; kalite en yüksek (kural `AGENTS.md`) — 17 Ekim |
| S14 A3.1 çalışma biçimi | A3.1 (Aşama 3 şartnamesi) tek ajanla, **Opus · max**; ultracode ve paralel ajan yok. S13'teki ultracode listesinden A3.1 çıkar (ultracode artık yalnız A6.1 ve A10.3). Şartname yine bölüm bölüm yazılır, her bölümde commit + push — 17 Ekim |
| S19 AZ hava metni | Teklif havası gelmediğinde AZ metni "hava məlumatı gəlmədi" (TR "hava bilgisi gelmedi"; `BoostTempE3Test`, `OrderHistory.boostTempLine`) — 17 Ekim, A3.6 oturumunda kullanıcı onayı |
| S20 Siparişler sekmesinin kalan içeriği | (b) Siparişler **Ayarları** (rozet, müşteri kartı ve boyutu, restoran balonu genişliği, OCR yedeği, izlenen uygulamalar, **filo komisyonu**) A4.2'de, net/hedef ayarlarıyla birlikte, Rawstats'taki gibi yapılır. "Çevrimiçi oturumlar" listesi ve Araçlar (bölünmüş ekran, iptal gözlemi) WEEM 1.0'dan sonra, gerekirse (`BEKLEYEN_OZELLIKLER.md`); kayıt günü "Paylaş" A4.3'te yapıldı — 18 Ekim |
| S21 A3.10 kayıtları | (c) A3.10, A4.3'ten sonra yapılır; WEEM `.dev` kayıtları A4.3'ün ZIP dışa aktarmasıyla alınır — 17 Ekim |
| S22 N3 otomatik şifreli yedek | Her vardiya sonu yazılır; dosyalar **haftalık** (Pazartesi–Pazar; bir dosya = o haftanın 7 günü, aynı haftanın dosyası her vardiyada yenilenir), `İndirilenler/WEEM/yedek/`. İçerik: haftanın gün kayıtları + `restaurants.json` + `kuryelog` ayarları + `courier.db` satırları (müşteri telefonları dahil; şifreli dosyada) — **kullanıcının ev konumu girmez** (`home.*`). Anahtar: kullanıcı parolası (PBKDF2 + AES-GCM; yeni telefonda da açılır, unutulursa açılmaz), telefonda Keystore ile saklanır ki her vardiyada sorulmasın. Kayıtlar'da haftalık yedek listesi: "Dışa aktar/paylaş", dışa aktarıldıktan sonra "Uygulamadan sil" = o haftanın gün kayıtlarını telefondan siler (onaylı; vardiya açıkken bugün silinmez; yedek dosyası kalır). Geri yükleme: dosya + parola → içe aktarma kuralları (birleştirir, üzerine yazmaz) — 18 Ekim |
| S23 Bluetooth yedeği | Klasik Bluetooth (RFCOMM): iki telefon bir kez Android Ayarlar → Bluetooth'tan eşleştirilir; WEEM tarama/yayın yapmaz. Yedek için iki telefonda da Bluetooth açık olmalı (Pixel'in Bluetooth'u kapalıyken yedek yok) — 18 Ekim (A8.1) |
| S24 QR eşleştirme araçları | QR okuma **Google code scanner** (ML Kit, Play hizmetleri; kamera izni yok, tarama ekranını Play verir), QR üretme **ZXing core**; jeton WEEM dışına çıkmaz (telefonun kamerasıyla açılan `weem://` bağlantısı kullanılmaz). Eşleştirme ekranı ve bu iki bağımlılık **A8.3**'te; A8.2 yalnız `weem://pair?…` metnini üretir ve okur (`LINK.md` 3.3) — 18 Ekim (A8.2) |
| S25 Gölge mod farklarında doğru kaynak | Rawstats'ın kaydı da hatalı olabilir (istatistik ekranında bazen hata var): sert farkta doğruyu **Wolt/Bolt'un kendi kazanç ekranı** belirler; WEEM doğru çıkarsa fark "Rawstats hatası" diye kapanır — 19 Ekim |
| S16 Gölge modda çift soru/pencere | (a) `.dev`'de "gölge mod" anahtarı: açıkken WEEM soru sormaz, rozet/balon çizmez, yalnız kayıt tutar (görev A3.11, A3.10'dan önce) — 19 Ekim |
| S26 Yol kalitesi (A6.11) | Scooter'a yeni koltuk geldi, önemi azaldı; sistem tam yazılır ve geliştirmeye açık kalır ama Ayarlarda tek anahtarla açılıp kapanır, **varsayılan kapalı**. Kapalıyken sensörler, arka plan hesabı, Ses± yakalama ve puan soruları hiç çalışmaz; önceden öğrenilmiş puanlar rotada kullanılmaya devam eder. A6.11, A6.6–A6.9'dan sonraya alınabilir — 19 Ekim |
| S27 Aşama 10 sırası | Önce kalan özellikler → optimizasyon → **geçiş öncesi kısa güvenlik kontrolü** (müşteri verisi, izinler) → geri yükleme provası → iş telefonlarına geçiş → gerçek kullanım dönemi (kullanıcı gereksiz/birleşecek özellikleri seçer) → sadeleştirme (özellik çıkarma/birleştirme dahil) → **en son** büyük denetim (ultracode, A10.7) — 20 Ekim |
| S28 Saha denemeleri | Kullanıcı ara deneme yapmak istemiyor: (K) deneme görevleri (A3.10 gölge mod, A4.4, A5.5, A6.10 YolGo ile yan yana sürüş, A7.3, A8.5, A9.3) kaldırıldı; aşama bitişi = testler yeşil. Sorunlar son sürümden sonra gerçek kullanımda (A10.4–A10.5) düzeltilir. Risk: telefonda hiç denenmemiş kod geçişte ilk kez çalışır; geçişe kadar Rawstats/YolGo/Sürüş Paneli kurulu kalır — 20 Ekim |
| S29 Bekleyen özellikler ne zaman | `BEKLEYEN_OZELLIKLER.md` kod sadeleştirmeden (A10.6) önce kullanıcıya tek tek sorulur (A10.5b; eski A11.1). Sadeleştirme bu kararları ve A10.5 listesini birlikte gözetir — 20 Ekim |
| S30 APK boyutu (A10.1) | Yalnız **arm64-v8a** derlenir (Pixel 10 Pro, Poco X3, S22 arm64; A6.5 sorusu (1) kapandı); **release'te R8 + kaynak küçültme** açık (`.dev` küçültmesiz). `.dev` 135,8 → 69,7 MB, release 32,0 MB (`docs/OPTIMIZASYON.md`) — 21 Ekim |
| S31 Geçiş sıfırdan (A10.3–A10.4) | **Tamamen temiz kurulum:** eski kayıtlar WEEM'e taşınmaz (Rawstats ZIP geri yükleme ve YolGo içe aktarma geçişte kullanılmaz; özellikler uygulamada kalır, çıkarmak A10.6 kararı). Önce **arşiv**: Rawstats ZIP dışa aktarma + YolGo kayıtları bir kez İndirilenler'e (WEEM'e yüklenmez). Sonra Rawstats ve YolGo kaldırılır, WEEM 1.0 temiz kurulur: kayıtlar, ayarlar, ev konumu, filo komisyonu, izinler ve Ninebot eşleşmesi yeniden. Kimlik (paket, imza; Bölüm 2) değişmez. A10.3 geri yükleme provası yerine kısa kurulum rehberi — 21 Ekim |
| S32 Bekleyen özellikler (A10.5b, kullanıcı erken istedi) | **Y13 Yüzey ve titreşim ölçümü** ve **Y14 Yol kalibrasyonu** birlikte yazılır (A10.4b); **S20** Siparişler çevrimiçi oturumlar listesi + Araçlar (bölünmüş ekran, iptal gözlemi) yazılır (A10.4a); **N7 Sesli uyarılar yazılmayacak** (çıkarıldı). Sıra: geçiş (A10.4) bugünkü 1.0 ile şimdi; bu özellikler hemen ardından **WEEM 1.1** (güncelleme kurulumu, kayıtlar korunur); eski testler önce Kotlin'e çevrilir — 21 Ekim |
| S33 A10.4b geçişten önce ve telefonsuz | **A10.4b (Y13 + Y14) geçişi (A10.4 K) ve telefon denemesini beklemeden yazıldı; APK şimdi derlenmez** (aşama sonunda bir kez). Kod cihazda denenmedi; ilk deneme geçiş sonrası WEEM 1.1 kurulumunda — 21 Ekim |
| S34 A10.4c ve açık sorular | **Kullanıcı kararları (22 Ekim):** (1) Y13'e **`ACTIVITY_RECOGNITION` izni eklensin** (SORU-Y13-1; "Ölçüm ve kalibrasyon"dan istenir, verilmezse sınırlı kanıt); (2) **ham tanı kaydı eklensin** (son 10 dk, varsayılan kapalı, konumsuz); (3) Y13/Y14 Azerice metinleri onaylı (M-Y13-1); (4) **S15 (a)**: bildirim 12'de müşteri telefonu aynen kalır. Kalan açık sorular (T1-1, S17, S18, A6.1, A6.5) cevap bekler — 22 Ekim |
| S35 Açık soruların cevapları ve tasarım | **Kullanıcı kararları (22 Ekim):** **T1-1** N5 menzil uyarısı **(a)** eklensin (teklif rozetine tek satır + haritada "eve dön?" kartı), **ayarlardan kapatılabilir**, ileride geliştirilir. **S17** kapandı: Rawstats'ta OCR sorunsuzdu; ML Kit'in telemetri kaydı manifestten çıkarılmış hâli kalır, telefonda OCR çalışmazsa Rawstats'taki gibi ML Kit olduğu gibi kullanılır. **S18** scooter bağlantısı kopunca **"Scooter bağlantısı koptu" yazısı** çıkar; yazıdan sonra kullanıcı elle bir şey yapmazsa **60 sn sonra otomatik yeniden dener (b)**; **Düzeltme (kullanıcı, 22 Ekim):** 2 telefon modunda scooter Bluetooth'una ve bağlantıya **yalnız Poco (Receiver)** bakar; **Pixel'in Bluetooth'unu kullanıcı elle kapatır**, WEEM Pixel için bir şey yapmaz (Android sistem Bluetooth anahtarını uygulamaya vermez). Tek telefonda her siparişte bağlanıp müşteriye giderken bağlantı kopuyor: **"scooter bağlantısı hep stabil + gün boyu tüm scooter verisi sorunsuz kayıtlı" beklentisi tek telefonda gerçekçi değil**; çözüm 2 telefon modu. Bu yüzden A10.4f yalnız kopma yazısı + 60 sn otomatik yeniden dene (Receiver/scooter bağlantısında) ve kayıttaki boşlukların "veri eksik" diye dürüstçe gösterilmesidir; "yalnız gerektiğinde bağlan" maddesi kaldırıldı. **A6.1:** #3 ölçülmüş yüzey rota maliyetine yazılsın (a→yazılsın; A10.4b'de yazıldı); #5 (a) tek kart; **#6 ekran yönü: Ayarlar'da "dikey / yatay / serbest" seçeneği**; #8 (a) iki km ölçüsü eski yerinde; #9 (a) `startReason`; #14 (a) domofon kodu Receiver'a gider; **#15 (b)** küçük harita verisi haftalık şifreli yedeğe girer (sürüş günlükleri hariç); **#17** Provider'da da gün kartı/İstatistik (R → P `DaySnapshot`), iki telefonda; **#18** görevler ve hatırlatıcılar **iki telefonda** (çift bildirim kabul); öteki maddeler varsayılan. **Tasarım:** uygulamanın görünümü beğenilmedi; **ciddi bir tasarım yenileme** işi açıldı (A10.4d, Opus · high; önce tasarım dili ve örnek ekranlar, kullanıcı onayıyla ekranlara uygulanır) — 22 Ekim |
| S36 Tasarım onayı (A10.4d) | **Kullanıcı kararı (22 Ekim):** `docs/TASARIM.md` Bölüm 0'daki önerilerin **hepsi**: **K1** vurgu mor, ama **laciverte yaklaştırıldı** (gündüz `#2C3EA8`, gece `#9BB1F5`; siyah-beyaz seçilmedi); **K2** yazı tipi **Inter** uygulamaya gömülü; **K3** örnek ekranlar onaylı; **K4** etkileşim değişikliklerinin hepsi (`TASARIM.md` 12). Lacivert vurgu "yolda" aşama mavisine yaklaştığı için o aşama hapı nötr (rota mavisi simgeyle). Uygulama (A10.4d adım 2–3) başlar — 22 Ekim |
| S37 Telefon geri bildirimi (A10.4j) | **Kullanıcı istekleri (23 Ekim, WEEM telefonda ilk açılış):** haritada arama yazılınca sonuç çıkmıyor; "Vardiya kapalı / Vardiyayı BAŞLAT" kartı çok büyük (küçülsün); haritaya dokununca ekran (paneller) gitmesin; hava paneli tam görünsün; **Google Maps** altlığı görünmüyor (anahtar girildi ama harita gelmiyor); **Daha fazla:** Dil · Tema · Ekran yönü eskisi gibi en altta üçlü seçim; **Scooter ayarları** da Daha fazla'da; Scooter ayarlarındaki pil, izin ve GPS ayarları kalksın (uygulama tek, Daha fazla → İzinler'de); **Harita ayarları** yalnız haritayla ilgili kalsın, **Ev konumu** oraya; hava durumu, yol kalitesi, ses tuşları, teklif veri seti, günlük işler, hatırlatıcılar harita ayarlarından çıksın; yeni **"Yol bilgileri"** bölümü: Yollarım'daki her şey + **Sürüş kayıtları**; **hava durumu + günlük işler + hatırlatıcılar** tek ayar ekranında; **teklif veri seti** Sipariş ayarlarında; **Scooter:** eşleşince kayıt ("vardiya") başlamasın, "Eşleşildi" yeşil; "Kaydı başlat" yerine "Kayda hazır / Eşleşildi" yazısı; scooter kaydı **vardiyayla aynı anda başlar, aynı anda biter**; **scooter kayıtları Kayıtlar'a**, mümkünse **tüm kayıtlar tek ZIP**. Yorum (ajan): eşleştirme kayıtsız canlı bağlantıyla yapılır; vardiya bağlantısı artık Sürüş Paneli kaydıdır (GPS + oturum; 1 Hz ekonomi örnekleri aynı); tek ZIP = içinde her bölümün kendi ZIP'i (içe aktarılabilir biçimler korunur) — 23 Ekim |
| S38 Telefon geri bildirimi 2 (A10.4k) | **Kullanıcı istekleri (23 Ekim, ekran görüntüleriyle):** ikinci batarya haritada görünmüyor (Sürüş Paneli görüyor); GPS'in yönü görünmüyor, dönme tepkisi gerçek zamanlı olsun; harita yönü dönmüyor; rota oluşturulunca trafik ışıkları büyüsün; uygulama belli bir süre sonra kapanıp açılıyor; çok pil tüketiyor; ekran görüntülerinde haritanın (rota ve yol çizgilerinin) bir kısmı görünüyor, bir kısmı görünmüyor; GitHub Secrets'taki Google API anahtarı uygulamaya bağlansın ve harita **Google Maps** olsun; genel optimizasyon. Kullanıcı sordu: bunlar şimdi mi, sadeleştirme (A10.6) ve büyük denetimden (A10.7) sonra mı? Ajan önerisi: **şimdi** (gerçek kullanımı engelleyen hatalar; A10.6/A10.7 düzeltilmiş kodun üstünde yapılır) — 23 Ekim |
| S39 Telefon geri bildirimi 3 + sadeleştirme (A10.4l, A10.6 erken) | **Kullanıcı istekleri (24 Ekim):** uygulama "consumer grade" değil: geçişler sorunsuz ama dokununca tepki anında değil → düzelt; **haritayı gömülü yap** (her APK'da indirmek olmaz: görsel altlık da APK'da, ağsız çalışsın); başka hata varsa onlara da bak; pili daha da azalt (web'de araştır, bulduklarını uygula); **sadeleştirme raporu**: uygulama en çok ne kadar sadeleşir, farklı yöntemlerle (web dahil) ciddi ve en iyi şekilde yap, sonra raporla. **Uygulama:** A10.6 kullanıcının isteğiyle A10.5 listesini beklemeden başlar; davranışı değiştirmeyen kod sadeleştirmesi (ölü kod, tekrar, gereksiz katman) yapılır, **özellik çıkarma/birleştirme önerileri raporda kullanıcıya sorulur** (karar kullanıcının); görevler A10.4l, A10.6 |
| S40 Scooter sekmesi sade (24 Ekim) | **Kullanıcı isteği:** Scooter sekmesinde ayarlar ve ZIP düğmeleri olmasın; vardiya kartı her yerde tek satır. **Uygulama:** ayarlar Daha fazla'da ve yüzen panelde çift dokunmada, scooter ZIP'i Kayıtlar'da; büyük vardiya kartı ve tam genişlik "Növbəni BAŞLAT" kalktı (PR #23) |
| S41 Repo düzeni ve usage (25 Ekim) | **Kullanıcı isteği:** repodaki dizilişte sorun ve fazlalık varsa düzelt; bundan sonra uygulama değişikliklerinde az usage harcansın. **Uygulama:** kısa oturum kancası (`LC_ALL=C.UTF-8`; `.claude/settings.json` env), `tools/test.sh` (yalnız özet ve hatalar), `docs/KOD_HARITASI.md` (özellik → dosya → test), "Şu an"/devir notu/AGENTS.md kısa ve tek yerde (aynı özet birden çok belgeye yazılmaz), işi biten araç ve geçmiş tablolar silindi (git'te); özellik çıkarma yok (SORU-S39-1 kullanıcıda) |
| S42 Telefon geri bildirimi 4 (26 Ekim) | **Kullanıcı istekleri:** gölge modu, içe aktarmayı "falan" sil (SORU-S39-1'e yanıt); harita elle döndürülemiyor, haritaya dokununca diğer her şey gitmiyor, haritada rahat gezilemiyor; ters yönü işaretlemek için Ses+ çalışmıyor; hava ikonları çok kötü; Google Maps anahtarı artık kredi kartı istiyor; genel optimizasyon ve iyileştirme noktaları (A10.7 öncesi). **Uygulama:** SORU-S39-1 madde 1–3 silindi (gölge mod anahtarı ve aracı — Rawstats eşitlik testi test kaynağında kaldı —, eski ZIP ve YolGo günlük içe aktarma — şifreli yedek geri yüklemesi kalır —, OSM raster altlık); madde 4–11 soruda kalır. Harita: iki parmakla döndürme; tek dokunuş ve elle hareket "yalnız harita" (S37 kararı tersine döndü), konuma dönüşte paneller gelir; takibe dönüş süresi hareket bitince sayılır. Ses+: yol öğrenme kapalıyken de ters yön önerisini onaylar (S26 yakalama kuralı daraltılarak genişledi). Yeni hava simge seti. Google anahtarı: yanıt `docs/OPTIMIZASYON.md` 6 |

## 9. İlerleme

Güncel durum `docs/WEEM_ISLER.md` ("Şu an", Aşama 0–9 özeti, Aşama 10) ve git geçmişi. 17 Ekim'e kadarki ilerleme tablosu: `git show bc790c5:docs/WEEM_PLANI.md`.
