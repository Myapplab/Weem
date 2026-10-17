# İki uygulama protokolü (YolGo ↔ Rawstats ↔ Sürüş Paneli) bulguları
Kaynak: Rawstats `rawstats @ 6388c04`, kök `/home/user/kurye_nav` (yollar `app/src/main/java/com/kuryelog/app/` altındaysa kısaca `RS:Dosya.kt:satır`); YolGo `yolgo-test @ 870ab6e`, kök `/home/user/kurye_nav_yolgo` (kısaca `YG:yol:satır`).
Durum: YAZILIYOR (bölüm bölüm ekleniyor). Bu alan özellik kodu değil mesaj kodu (P1…) ile düzenlendi; her mesajın ilgili özellik kodları "Bağımlı"da.

## 1. Özellikler (mesajlar)

### P0 Ortak kapı: sağlayıcı, izin, imza, bildirim (bütün Rawstats→YolGo kanalları)
- Eski kod: `RS:OfferFeed.kt:15-68` (`OfferFeed` URI'ler, grant/revoke), `RS:OfferFeed.kt:119-159` (`OfferFeedProvider.query`), `RS:OfferFeedAccess.kt:1-14`, `RS:FeedSigner.kt:1-19`, `RS:FeedChanges.kt:1-99`, `RS:FeedNotify.kt:1-39`; manifest `app/src/main/AndroidManifest.xml:78-87`.
- Taşıma: tek sağlayıcı `.OfferFeedProvider`, authority `com.kuryelog.app.economics`, `exported="false"`, `grantUriPermissions="false"`, yol başına `<grant-uri-permission>`: `/offers/current`, `/offers/v2/current`, `pathPrefix /logs/v3/day/`, `/scooter/v1/live`, `/shift/v1/state` (manifest:78-87). Özel `<permission>`/protectionLevel YOK; erişim yalnız `Context.grantUriPermission(TARGET="az.duzyol.app", uri, FLAG_GRANT_READ_URI_PERMISSION)` ile (v3'te `| FLAG_GRANT_PREFIX_URI_PERMISSION`) (OfferFeed.kt:30-67).
- Sorgu sonucu: her kanal `MatrixCursor(arrayOf("payload"))`, tek satır, sütun `payload` = JSON metni (OfferFeed.kt:154). `getType` = `application/json`; insert/delete/update → `SecurityException("Read only")` (OfferFeed.kt:121,156-158). Bilinmeyen yol → `SecurityException("Unsupported path")` (OfferFeed.kt:128).
- Her sorguda yeniden yetki (OfferFeed.kt:129-146): `ownUid` ya da [çağıran paket `az.duzyol.app` ∧ anahtar açık ∧ `checkUriPermission(uri, pid, uid, READ)==GRANTED` ∧ imza]. İmza yalnız v2, v3, shift için aranır; **v1 ve scooter için imza denetimi yok** (`signed = ownUid || (!(v2||logs||shift) || FeedSigner.trusted(...))`, OfferFeed.kt:142). Kural `OfferFeedAccess.signedAllowed` (OfferFeedAccess.kt:9-10).
- Anahtar ("enabled") kanal başına (OfferFeed.kt:136-141): v1/v2 `OfferFeed.enabled` = pref `economics.share` (vars. false) ∧ `Store.canRecordScreen`; scooter `scooterEnabled` = yalnız `economics.share`; shift `shiftEnabled` = yalnız `economics.share`; v3 `LogFeedV3Android.enabled` = `economics.share` ∧ `!Store.importing` (LogFeedV3Android.kt:16). Kullanıcı arayüzü: "YolGo ile teklif ve scooter paylaşımı" anahtarı (`RS:MainActivity.kt:1161`; değişince `refreshGrant`, `refreshScooterGrant`, `FeedNotify.all`).
- Grant zamanlaması: v1/v2 vardiya başında `refreshGrant` (OrderTracker.kt:277), vardiya sonunda `revoke` (OrderTracker.kt:290, OfferFeed.kt:49-54); scooter grant yalnız anahtara bağlı (vardiyadan önce de pil gösterilsin diye, OfferFeed.kt:29-33; `ScooterLink.install` çağırır, ScooterLink.kt:62); v3 ve shift grant anahtar ∧ YolGo imzası doğruysa, vardiya bitince de kalır (OfferFeed.kt:56-67).
- İmza (`FeedSigner`): `YOLGO_SHA256 = "32d058416d70b9c705fce4353fda1beee45de6f6ef4a1013bca5b1ff9a04cb45"` (FeedSigner.kt:9) = YolGo `android_overlay/debug.keystore` sertifikasının SHA-256'sı. `getPackageInfo(pkg, GET_SIGNING_CERTIFICATES).signingInfo.apkContentsSigners` → her sertifika baytının SHA-256 hex'i; **tam olarak tek imzacı ve eşit** olmalı (`signersAllowed`: `signers.size == 1 && single() == expected`, OfferFeedAccess.kt:6-7). Döndürme geçmişindeki eski sertifika sayılmaz (FeedSigner.kt:6). Hata → false. İmza mesaj alanlarını İMZALAMAZ; yalnız karşı uygulamanın paket sertifikasını doğrular (yükte imza/MAC yok).
- CI pin denetimi: `ci/check_feed_signer.py` (105 satır): sabit tam 64 küçük harf hex, kaynakta tek `const val` literal, `keytool -exportcert -storepass android -alias <alias>` DER'inin SHA-256'sı eşit (check_feed_signer.py:12-36,83-101). Rawstats CI: `--constant YOLGO_SHA256 --keystore .ci-feed-peer/android_overlay/debug.keystore --alias androiddebugkey` (`.github/workflows/ci.yml:50-51`, `rawstats.yml:57-58`). Öz-test 8 durum (SignerCheckTest; "extra e" regresyonu `1beee45`→`1beeee45`).
- Değişiklik bildirimi (E8): `FeedChanges.publish(channel, key, now)` içerik anahtarı son duyurulandan farklı ve kanal ≥`MIN_GAP_MS=200` sessizse true (FeedChanges.kt:26-33,41); aralıkta kalan değişiklik sonraki kontrolde çıkar; `changedAt` = içeriğin İLK görüldüğü an (`pendingSince`). Kanallar `OFFERS="offers"`, `SCOOTER="scooter"`; `HEARTBEAT_MS=5000` (görünen teklifin `lastSeenAtMs` 5 sn dilimle sayılır); `SCOOTER_STALE_MS=15000` (FeedChanges.kt:39-44). `canonical()` anahtarları sıralar (FeedChanges.kt:72-78).
- `FeedNotify`: ayrı `HandlerThread("rawstats-feed-notify")`, `contentResolver.notifyChange(uri, null, 0)` (bayrak 0: sync adapter tetiklenmez) (FeedNotify.kt:10-38). `offers()` → v1+v2 birlikte; `scooter()`; `shift()` (GpsService.kt:87 başla, :290 bitir); `all()` → 5 URI (anahtar değişince; v3 öneki); `day(day)` → `NotifyThrottle(DAY_GAP_MS=5000)` gün başına en çok 5 sn'de bir, son değişiklik her zaman (FeedChanges.kt:86-98, FeedNotify.kt:23,28-33); `Store.kt:64` v3 dosyasına satır yazılınca çağırır. Bildirimle veri gitmez. Anahtar kapalıyken hiçbir şey duyurulmaz (`publishFeeds` ve `ScooterLink.publish` `scooterEnabled` ile döner, OrderTracker.kt:91-96, ScooterLink.kt:146-152).
- Tetik noktaları: `OrderTracker.tick` her vardiya tikinde `publishFeeds` (OrderTracker.kt:575-576; kayıtçı vardiyada ≥0,5 sn'de bir), her kare/dokunuş sonrası, vardiya başı/sonu (OrderTracker.kt:277,290), YolGo hedefi bağlanınca (OrderTracker.kt:739).
- Testler: `FeedSignerTest`, `OfferFeedAccessTest`, `FeedChangesE8Test` (sayılar §3).
- Bağımlı: G1 (vardiya kapısı), N1 (2 telefon taşıması), R19 (anahtar uyarıları).
- WEEM: tek uygulamada sağlayıcı/imza/grant GEREKSİZ (aynı süreç); `FeedChanges` içerik-anahtarı + 200 ms + 5 sn kalp atışı mantığı `logic/` saf (`:core` ya da `:link` kuyruğu). 2 telefon modunda imza kapısının yerini QR eşleştirme + şifreli kimlikli mesaj alır (`:link`, Aşama 8).
- Not: E8 belgesi "v1+v2 vardiya + anahtar açıkken grant" der; kod v1/v2 `enabled` için ayrıca `Store.canRecordScreen` ister (OfferFeed.kt:27). Android IPC (gözlemci, izin, imza) telefonda **doğrulanmadı** (E8_IKI_UYGULAMA.md:104).

### P1 Teklif feed v1 — `content://com.kuryelog.app.economics/offers/current`
- Yön: Rawstats (yazar, `OfferFeedContract.snapshot`) → YolGo (okur). Dondurulmuş (YolGo 1.x başka sürümü reddeder; feed_v2.schema.json:4).
- Eski kod: `RS:OfferFeed.kt:71-99`, `RS:OrderTracker.kt:68-72` (`economicSnapshot`, `feedV1`).
- Alanlar (kök): `schemaVersion` int =1 zorunlu · `generatedAtMs` int ms (duvar saati, sorgu anı) zorunlu · `manualSession` bool (= `OfferFeed.enabled ∧ core.running`) zorunlu · `offers` dizi zorunlu (v1'de üst sınır kodda yok; şema `maxItems 8`) (OfferFeed.kt:97-98; şema:6-12). v1'de `producer`/`courierState` YASAK (şema:19-20).
- Teklif nesnesi (OfferFeed.kt:80-92; şema:39-61), hepsi zorunlu anahtar:
  | alan | tip | birim/değer | kaynak |
  |---|---|---|---|
  | `id` | string | teklif anahtarı `offer-<wall ms>-<sıra>` (LOG_FEED_V3.md:12) | OfferFeed.kt:82 |
  | `platformPackage` | string | ör. `com.wolt.courierapp` | :82 |
  | `firstObservedAtMs` | int ms | ilk görülme | :83 |
  | `observedAtMs` | int ms | son kare | :83 |
  | `source` | string | kare kaynağı (`accessibility`/`ocr`/`notification`) | :84 |
  | `complete` | bool | kare tam mı | :84 |
  | `currency` | const `"AZN"` | | :84 |
  | `displayedFeeAzn` | number/null | AZN, brüt | :85 |
  | `feeBasis` | enum `net_to_courier|gross|unknown` | kod hep `"gross"` (OrderTracker.kt:72) | :85 |
  | `feeScope` | enum | `whole_package` (adet>1 ya da >1 sipariş no) yoksa `unknown` | :86 |
  | `platformDistanceKm` | number/null | km | :87 |
  | `platformDistanceScope` | enum | hep `"unknown"` | :87 |
  | `remainingOfferMs` | int/null | hep null | :88 |
  | `restaurant` | string/null | ≤200 karakter | :89 |
  | `pickupAddress` | string/null | ≤1000 karakter (restoran adresi) | :90 |
  | `dropoffAddress` | string/null | ≤1000 karakter **MÜŞTERİ ADRESİ** | :91 |
- Kurallar: yalnız `active` iken teklif listelenir (OfferFeed.kt:96); `exportable` = `now − lastSeenAt ≤ MAX_UNSEEN_MS=90000` ∧ `lastSeenAt ≥ restartedAtMs` (K21: hayalet teklif yok; süreç yeniden başlamadan önce görülen teklif yeniden görülene dek yok) (OfferFeed.kt:73-78). Yük ≤100.000 karakter (iki okuyucu keser/reddeder; şema:4).
- Gizlilik: `dropoffAddress` müşteri adresi → Receiver'a GİTMEMELİ (ya da yalnız bölge). `pickupAddress`/`restaurant` müşteri verisi değil. Allowlist yorumu: "no raw screens, customer notes, identity or credentials" (OfferFeed.kt:70). Müşteri adı/telefonu bu yükte YOK.
- Bağımlı: R3, R4, Y19, Y20.

### P2 Teklif feed v2 — `content://com.kuryelog.app.economics/offers/v2/current`
- Yön: Rawstats → YolGo. Eski kod: `RS:OfferFeed.kt:100-116`, `RS:OrderTracker.kt:73-86`, `RS:EconomicsLogs.kt:140-190,556-562`, `RS:TripStages.kt`; şema `docs/ekonomi-v2/feed_v2.schema.json` (153 satır, `$id kuryelog.economics.feed.v2`).
- Kök: v1 kökü + `schemaVersion`=2, `producer` (zorunlu), `courierState` (zorunlu; null verilirse `unknownCourierState()`), E8 eki `stages` (hep var) ve `changedAtMs` (ilk duyurudan önce YOK) (OfferFeed.kt:113-115; OrderTracker.kt:79,85; TripStages.kt:197-201). Şema kökü ek alana izin verir (E8:22).
- Teklif: v1 nesnesi + `v2` (zorunlu); en çok `MAX_V2_OFFERS=8` (OfferFeed.kt:75,108); `feedOfferV2` null dönerse (platform wolt/bolt değilse) teklif atlanır (OfferFeed.kt:110; EconomicsLogs.kt:141).
- `producer` (kapalı şema): `package` const `com.kuryelog.app` · `versionName` string · `specVersion` `^2\.[0-9]+\.[0-9]+$`, kod `EconomicsLogs.SPEC_VERSION="2.1.0"` (EconomicsLogs.kt:27; OrderTracker.kt:83).
- `v2` (kapalı şema; EconomicsLogs.kt:140-154):
  | alan | tip | değer/kural |
  |---|---|---|
  | `offerKey` | string | = `o.id` |
  | `platform` | enum `wolt|bolt` | |
  | `firstSeenAtMs` | int ms | |
  | `lastSeenAtMs` | int ms | `max(lastSeenAt, firstAt)` |
  | `visibleNow` | bool | `now − lastSeenAt ≤ FRESH_MS=5000` (EconomicsLogs.kt:29) |
  | `expiresAtMs` | int/null | `firstAt + offerWindowMs(pkg)` (PRIORS `documented_window_ms`, DOĞRULANMAMIŞ üst sınır; DeliveryCore.kt:868-872) |
  | `expirySource` | enum | `documented_upper_bound` ya da `unknown` (şemadaki `screen_timer/screen_progress/empirical_p10` üretilmiyor) |
  | `shown` | nesne | `minLo,minHi,km,kmScope("unknown"),stops,readyInMin,approachMin,deliveryMin,totalMin,multiplier,boostPct,demandLabel` hepsi null olabilir (EconomicsLogs.kt:82-86) |
  | `pickupZone`,`dropoffZone` | enum `Z1..Z4|unknown` | adres metninden bölge |
  | `dropoffKnown` | bool | `customerAddress != null` (Bolt kartında müşteri yok, K8) |
  | `optional` | bool | Bolt "yarıçap dışı" |
  | `duringTripId` | string/null | teklif görüldüğünde açık sipariş |
  | `tipConfirmedAzn` | number≥0 | şemada var, kod yazmıyor |
  | `parse` | `{feeSource: accessibility|ocr|notification, confidence: high|medium|low, secondAmountSeen?}` | `high` = ücret var ∧ accessibility ∧ complete; ücret yoksa `low` |
- `courierState` (kapalı şema; EconomicsLogs.kt:163-186):
  - `appsOnline` string[] (sıralı, tekil paketler) · `activeTrips` ≤3 (`acceptedAt` sırası, son 3) her biri `{tripId, platformPackage, stage, remainingMinEstimate(hep null), dropZone}` (`dropZone` = müşteri koordinatından kaba bölge) · `wolt {declinesInWindow ≥0, windowMin = DECLINE_WINDOW_MS/60000, offlineUntilMs, offlineReason declines|scheduled|cash_limit|unknown|null, penaltyHistory{triggerCounts[]}}` · `bolt {acceptanceRate, acceptanceRateSource screen|unknown}` · `scooter {batteryPct, observedAtMs, source}` **kod hep null/null/"unknown"** (EconomicsLogs.kt:180-181 "not read by KuryePlus yet") · `hereZone` · `speedKmh` (son konum ≤`FRESH_FIX_MS` taze ve hızlıysa ×3,6, 1 basamak) · `repositioning` bool.
- `stages` (E8, `TripStages`, version 1): ayrıntı P3.
- `changedAtMs`: `FeedChanges.changedAt(OFFERS)` = içeriğin ilk görüldüğü duvar saati; YolGo gecikmesi = okuma − `changedAtMs` (E8:24; kabul ≤1000 ms, E8:84-90).
- İçerik anahtarı (bildirim için; FeedChanges.kt:47-60): v1+v2 birlikte; çıkarılan: `generatedAtMs`, `changedAtMs`, teklif `observedAtMs/source/complete`, v2 `producer`, `courierState.speedKmh`; `v2.lastSeenAtMs` → `/5000`.
- Gizlilik: v1 alanları (dolayısıyla `dropoffAddress`) v2'de de var → Receiver'a gitmemeli. `courierState`'te müşteri verisi yok (yalnız bölge).
- Testler: `OfferFeedV2Test`, `FeedChangesE8Test`, `TripStagesE8Test` (§3).
- Bağımlı: R3, R4, R5, N5 (menzil kartı teklif özetini ister), Y19.

### P3 Sipariş aşamaları `stages` (feed v2 üst düzey alanı)
- Eski kod: `RS:TripStages.kt:1-84`; YolGo tüketici `YG:lib/economics/courier_stages.dart` (aşağıda).
- Şema: `{"version":1,"trips":[{tripId, platformPackage, active, stage, outcome, endedAtMs, events[]}]}` (TripStages.kt:177-193; E8:26-33).
  - `stage`: açıkken `to_pickup|at_pickup|to_dropoff|at_dropoff` (TripPhase eşlemesi EconomicsLogs.kt:156-160; bilinmeyen faz → `to_pickup`), bitmişte null.
  - `outcome`: açıkken null; `delivered` (FINISHED) · `delivered_unverified` (K34/D3 çıkarım) · `cancelled` (hiç alış yok, D7) · `unconfirmed` (alındı, teslim görülmedi) (TripStages.kt:139-145).
  - `endedAtMs`: FINISHED → `completedAt ?: closedAt`; çıkarım → `deliveredInferredAt ?: closedAt`; diğer → `closedAt` (kayıtçının gördüğü kapanış, `tripEnds`) (TripStages.kt:148-153).
  - `events[]` sıralı `{stage, atMs, pickup?, source?}`: `to_pickup` (acceptedAt, `pickup:0`); her alış i: `at_pickup` (arrivedAt, `pickup:i`, `source: gps` (arrivalSource `gps…` ile başlarsa) | `screen`); `pickedAt` yoksa dur; son alış değilse `to_pickup` (`pickup:i+1`) yoksa `to_dropoff`; müşteriler teslim sırasıyla: `at_dropoff` (arrivedAt), teslim yoksa dur, son müşteri değilse `to_dropoff` (TripStages.kt:156-175). Saati görülmeyen aşama YOK.
  - Sınırlar: açık ≤`MAX_ACTIVE=3`, bitmiş ≤`MAX_ENDED=3` ve `now − endedAt ∈ [0, ENDED_KEEP_MS=600000]`; sıra: bitenler (endedAt) sonra açıklar (acceptedAt) (TripStages.kt:132-135,185-193).
- Gizlilik: yalnız kimlik, paket, aşama adı ve saat; müşteri verisi YOK (TripStages.kt:129) → Receiver'a gidebilir.
- Bağımlı: R5, Y20 (görev kartları), R14 (enerji parçaları), N1.
- WEEM: `:orders` üretir, `:map`/`:scooter` tüketir; saf `logic/`.

### P4 Canlı scooter — `content://com.kuryelog.app.economics/scooter/v1/live`
- Yön: Rawstats (Sürüş Paneli birleşik) → YolGo harita. Eski kod: `RS:ScooterFeed.kt:1-38` (`ScooterFeedContract`), `RS:ScooterLink.kt:66-76` (`liveValues`), `:143-152` (`publish`).
- Kapı: yalnız `economics.share` (ekran okuma gerekmez, vardiya gerekmez), **imza denetimi yok** (P0).
- Alanlar (ScooterFeed.kt:203-214), hepsi anahtar olarak var, ölçülmeyen null (uydurulmaz; NaN/∞ → null):
  - kök: `schemaVersion`=1 · `generatedAtMs` int ms · `source` = `"rawstats_surus_paneli"` · `linkActive` bool (`SurusPaneli.active()`) · `ageMs` int/null (son karenin yaşı = `elapsedRealtime − monoMs`; bağlantı yoksa null).
  - `scooter`: `internalPct` % · `externalPct` % · `speedKmh` km/h · `powerW` W · `voltageV` V · `currentA` A · `rangeKm` km · `tempC` °C · `odometerKm` km.
  - `trip` (sipariş sayacı, bağlantı aktifken `SurusPaneli.trip()`): `wh` Wh · `pctUsed` % · `avgMovingKmh` · `km` · `movingMs` int ms · `startedAtMs` int ms (duvar).
  - Okuma hatası → `linkActive=false, ageMs=null`, diğerleri null (ScooterLink.kt:76).
- Bayatlık: `ageMs` > `SCOOTER_STALE_MS=15000` ya da null → bayat (FeedChanges.kt:44,63-69; YolGo `ScooterLive.maxFrameAgeMs` ile aynı). `ScooterLink.FRESH_MS=15000` (ScooterLink.kt:27).
- Tetik: her 1 Hz örnekte (`sample` → `publish`, ScooterLink.kt:162) ve vardiyada 0,5 sn tikte; içerik anahtarı `ageMs` yerine `stale` bool, `generatedAtMs` hariç (FeedChanges.kt:63-69).
- Gizlilik: yalnız scooter değerleri + sipariş sayacı; sipariş, müşteri, adres, ev, konum YOK (ScooterFeed.kt:8).
- Bağımlı: S2, S4, S5, Y9 (batarya tahmini), N5, N7.
- WEEM: `:scooter` üretir, `:map` tüketir. 2 telefonda bu mesaj Receiver içinde kalır (scooter Receiver'da); Provider'a yalnız "anlık batarya özeti" seyrek gider (plan §4).

### P5 Saniyelik scooter verisi ve enerji (ScooterBridge; uygulama içi + dosya)
- Eski kod: `RS:ScooterBridge.kt:1-44`, `RS:ScooterLink.kt:21-171`, `RS:ScooterSamples.kt:1-60`; belge `docs/SURUS_PANELI_BIRLESME.md:20-25,44-56`.
- Arayüz `ScooterBridge.Source` (ScooterBridge.kt:16-25): `energy(fromWallMs, toWallMs): Map<String,Any?>?` (enerji bloğu, `EnergySegments.energy` anahtarları) · `batteryPct(): Double?` (taze kare ≤15 sn, 0..100) · `orderAccepted(tripId)` (`SurusPaneli.resetTrip`) · `orderDone(tripId)` (`SurusPaneli.endTrip`, vars. boş). `SOURCE="surus_paneli"`.
- Koruma: kaynak yoksa `EnergySegments.energy()` (= `not_measured`); açık pencere (`to==null || to<=from`) ölçülmez; her çağrı `runCatching`; pil sonlu ve 0..100 değilse null (ScooterBridge.kt:33-43).
- Saniyelik veri = `economics-scooter.jsonl` (gün klasörü; `ScooterSamples.FILE`, `SCHEMA=1`): yalnız vardiyada ve `Store.canRecordScreen` iken, Sürüş Paneli işçi iş parçacığında 1 Hz (ScooterLink.kt:159-171). Satır: `schemaVersion:1, kind:"scooter_sample", wall_ms, mono_ns (=monoMs×1e6), source:"surus_paneli", scooter_id, scooter_id_source ("settings"|"default_single_scooter"), power_verified:false, speed_kmh(1), vxa_w(1; tüketim +), int_pct(0), int_v(2), int_a(2), ext_pct(0), ext_v(2), ext_a(2), temp_c(1), odometer_km(1)` (ScooterSamples.kt:48-56). Konum YOK (gps.csv'de).
- Sipariş başına enerji: iki örnek arası trapez `ScooterSamples.step` (≤`ScooterEnergy.MAX_STEP_MS`) → `OrderTracker.scooterEnergy` → `core.addEnergy` (ScooterLink.kt:164-165); bacak/boş süre satırları `economics-energy.jsonl` (`ScooterBridge.energy`).
- Bağımlı: S3, R14, N4, N5.
- WEEM: tek telefonda `:scooter` → `:orders/:economics` doğrudan çağrı. 2 telefonda saniyelik veri Receiver'dan ÇIKMAZ (plan §4); `energy(from,to)` sonucu (economics-energy bloğu) Receiver→Provider eşzamansız; `orderAccepted/orderDone` Provider→Receiver.

### P6 Vardiya durumu — `content://com.kuryelog.app.economics/shift/v1/state`
- Yön: Rawstats → YolGo (YolGo günü Rawstats vardiyasıyla başlatır/bitirir; F4.1). Eski kod: `RS:ShiftStateFeed.kt:1-23`, `RS:OfferFeed.kt:161-165` (`fromPrefs`).
- Kapı: `economics.share` ∧ YolGo imzası; vardiya bitince de okunur (grant v3 gibi) (OfferFeed.kt:55-61).
- Alanlar: `schemaVersion`=1 · `active` bool (pref `shift`) · `startedAtMs` int/null (pref `shiftStart`, 0→null) · `endedAtMs` int/null (`shiftEndedAt`) · `endReason` string/null (`shiftEndReason`) · `interrupted` bool (`ManualSessionPolicy.interrupted(active, shiftInterruptedAt, startedAt)`, H4, yalnız süren vardiya) · `generatedAtMs` int ms (ShiftStateFeed.kt:172-178). Koordinat YOK.
- Tetik: `FeedNotify.shift` vardiya başı/sonu (GpsService.kt:87,290) ve `all()`.
- Bağımlı: G1 (tek vardiya), Y17.
- WEEM: G1 ile tek kaynak `:core`; mesaj 2 telefonda Provider→Receiver "vardiya başla/bitir".

### P7 Gün kayıtları v3 — `content://com.kuryelog.app.economics/logs/v3/day/YYYY-MM-DD`
- Yön: Rawstats → YolGo (veri seti, Y19). Eski kod: `RS:LogFeedV3.kt:1-207`, `RS:LogFeedV3Android.kt:1-76`; belge `docs/LOG_FEED_V3.md`.
- Kapı: `economics.share` ∧ `!Store.importing` ∧ imza ∧ prefix grant; vardiya gerekmez; büyük kopya sırasında her adımda `authorize()` yeniden (LogFeedV3Android.kt:30,40-51,64,69).
- Adım 1 (parametresiz): manifest JSON: `schema_version:3, flowVersion:3, day, snapshot (UUID, `[a-f0-9-]{36}`), files[], wall_ms, mono_ns, source:"kuryeplus", scooter_id:null, scooter_id_source:"unavailable", power_verified:false, specVersion ("2.1.0"), producer{package:"com.kuryelog.app", versionName}, redaction{locDecimals:3, secretExported:false, pseudonymScope:"day"}` (LogFeedV3.kt:124-131). `files[]` öğesi: `name, available, bytes, rows (CSV başlığı hariç), sourceRows, rejectedRows, sha256 (maskeli baytların)` (LogFeedV3.kt:120-122).
- Adım 2: `?snapshot=<token>&file=<name>&offset=<byte>&limit=<1..32768>` (`PAGE_BYTES=32768`; limit yoksa 32768, offset yoksa 0) → `{schema_version:3, flowVersion:3, day, snapshot, name, offset, nextOffset, eof, dataBase64 (NO_WRAP)}` (LogFeedV3Android.kt:58-74; LogFeedV3.kt:186-197). Sayfalar UTF-8 baytlarıdır: birleştir sonra çöz.
- Dosya allowlist (`LogFeedV3.names`, LogFeedV3.kt:48-51): `BackupFiles.logNames` içinden `economics-*` + `scooter*` + `online-sessions.jsonl, platforms.jsonl, events.jsonl, errors.jsonl, gps.csv, screens.jsonl` + `economics-payouts.jsonl` (yazılmıyor, `available:false`). `orders.jsonl`, `notifs.jsonl`, `restaurants.json` YOK (LOG_FEED_V3.md:11).
- Maskeleme: `AnalysisExport(secret).row(kind, j)` / `redact(GPS)`; gün sırrı `noBackupFilesDir/economics-day-secrets-v3/<day>` 32 bayt (ZIP/akış/yedeğe girmez); aynı gün aynı takma adlar (LogFeedV3.kt:13-26; LOG_FEED_V3.md:11).
- Ortak alanlar her satıra: `wall_ms` (yoksa `t`'den `legacy_t`, yoksa null; indirme zamanı ASLA yazılmaz), `mono_ns`, `schema_version:3`, `source` (`kuryeplus|surus_paneli|yolgo|manual`), `scooter_id`, `power_verified:false`, `wall_ms_source`, `mono_ns_source`, `scooter_id_source`, `field_sources{yol:kaynak}` (null alanlar `unavailable`), `capture_source` (ham kaynak ocr/accessibility/notification ise) (LogFeedV3.kt:141-171). gps.csv'ye aynı sütunlar + her sütun için `<sütun>_source` (LogFeedV3.kt:78-102).
- Anlık görüntü: dosya tanıtıcıları + uzunluklar Store kilidi altında dondurulur, yalnız o uzunluk kopyalanır (sonraki satırlar sonraki çekişe) (LogFeedV3Android.kt:28-43; LogFeedV3.kt:31-40). Kopyalar 1 saat tutulur, sonraki liste sorgusunda silinir (LogFeedV3Android.kt:52-55). Aynı tokenın sayfaları değişmez; işlem >1 saat sürerse listeyi yeniden al.
- Hatalar: geçersiz gün/token/ad/offset/limit → `require` (IllegalArgumentException); yetki yok → SecurityException.
- Tetik: `FeedNotify.day` (gün başına ≤5 sn).
- Gizlilik: maskeli; konum 3 ondalık; müşteri adı/telefonu çıkarılır (AnalysisExport). Receiver'a gerek yok (veri seti Provider'da üretilebilir).
- Bağımlı: Y19, R18, R17.
- WEEM: tek uygulamada IPC gereksiz; Y19 veri seti doğrudan `:core` deposundan okur. Maskeleme kuralları R18 ile ortak `logic/`.

### P8 Navigasyon durumu — `content://az.duzyol.app.nav/nav/v1/current` (YolGo → Rawstats)
- Eski kod (okuyucu): `RS:YolGoNav.kt:15-90` (`parse`, `pickupOf`), `RS:YolGoNav.kt:93-108` (`ScreenTrail`), `RS:YolGoNavReader.kt:1-93`, `RS:OrderTracker.kt:41-52,717-740`. Belge E8 §3.
- Taşıma: authority `az.duzyol.app.nav`, tek satır, sütun `payload`, ≤`MAX_CHARS=4000` (YolGoNav.kt:16-20). Okuyucu sağlayıcının `resolveContentProvider(...).packageName == "az.duzyol.app"` ve `FeedSigner.trusted` (YolGo imzası) olduğunu denetler; güven 60 sn önbellek (`TRUST_MS`) (YolGoNavReader.kt:130,160-168). Anahtar: `OfferFeed.scooterEnabled` (= `economics.share`).
- Tetik: ContentObserver (`registerContentObserver(uri, false, o)`) → okuma; patlama tek okumaya `MIN_GAP_MS=250`; dinleyici yoksa `RETRY_MS=30000`'de bir yeniden dene; yedek okuma vardiyada `FALLBACK_MS=60000` (YolGoNavReader.kt:127-129,150-189). Vardiya başında `start` (kaydol + bir oku), sonunda `stop` (son durum bellekte kalır). Kendi iş parçacığı `rawstats-yolgo-nav`.
- Yük v1 (YolGoNav.kt:36-61; E8:57-62):
  - kök: `schemaVersion`==1 ve `source`=="yolgo" değilse RED · `generatedAtMs` >0 zorunlu · `updatedAtMs` (yoksa generated) · `target` nesne/null · `route` nesne/null. Bozuk `target`/`route` tek başına atılır, gerisi kalır.
  - `target`: `lat` [-90,90] · `lon` [-180,180] · `kind` ∈ `restaurant|customer|unknown` · `origin` ∈ `intent|clipboard|search|map|order` · `requestedAtMs` >0 · `sourcePackage` `[A-Za-z0-9_.]{1,100}` ya da null. Rota yoksa ya da ev rotasıysa `target:null` (ev koordinatı gönderilmez, E8:66).
  - `route`: `remainingM` [0, 500000] m · `remainingTurns` int [0,1000] ya da null (aralık dışı → route atılır) · `measuredAtMs` >0 zorunlu.
- Bayatlık: `TARGET_MAX_AGE_MS=5400000` (90 dk) · `TRAIL_MAX_GAP_MS=120000` (istekten önceki ekran ≤2 dk) · `ROUTE_MAX_AGE_MS=30000` (YolGoNav.kt:22-26); `pickupOf` `now − requestedAt ∈ [−60000, 90 dk]` (YolGoNav.kt:69).
- Rawstats kullanımı: (a) `origin:intent` hedefi, Wolt alış ekranındayken istendiyse o alışın restoran koordinatı (D9 `gps_dwell_yolgo_target`, geocode'dan önce) (YolGoNav.kt:68-89; VenueArrival.kt:142); müşteri ekranı (DROPOFF) → yok; ekran yoksa yalnız `kind=restaurant` ve tek aday. (b) M19/M20: `kind=customer` ve bacak başından sonra istenen hedef müşteri koordinatı ve kalan mesafe kaynağı (`ArrivalDistance.YOLGO`) (OrderTracker.kt:41-52). (c) E16 müşteri kartı: 300 m kuş uçuşu ya da YolGo rotasında 300 m / son 2 dönüş (OrderTracker.kt:578-579 yorum).
- Günlük: koordinat yazılmaz; `yolgo_venue` olayı `{trip, pickup, kind, source_package, geocode_gap_m}` (OrderTracker.kt:735-736).
- Gizlilik: `target` müşteri koordinatı olabilir (`kind=customer`) → müşteri konumu; 2 telefonda Receiver üretir, Provider'a gider (sorun değil: Provider müşteri verisini zaten tutar). Ev koordinatı hiç gönderilmez.
- Testler: `YolGoNavE8Test` (§3).
- Bağımlı: Y4, Y5, R5, R6, R16, N1.
- WEEM: `:map` üretir, `:orders` tüketir. 2 telefonda Receiver→Provider "rota durumu" (plan §4).

### P9 Hava — `content://az.duzyol.app.weather/weather/current` (YolGo → Rawstats)
- Okuyucu: `RS:WeatherFeedReader.kt:1-34`, `RS:OfferWeather.kt:1-47`. Rawstats'ın İNTERNET izni yok (manifest:3-4 `tools:node="remove"`); hava tek kaynak YolGo Open-Meteo (OfferWeather.kt:7-8).
- Okuma: projeksiyon null, ilk sütun `getString(0)`; kendi iş parçacığında (`kp-weather`), en çok `CACHE_MS=120000`'de bir; erişilebilirlik iş parçacığını bekletmez (K37); grant yoksa SecurityException → hava yok (WeatherFeedReader.kt:13-33). İmza denetimi YOK (yalnız YolGo'nun verdiği grant).
- Yük (OfferWeather.kt:18-25): `schemaVersion`==1 ∧ `source`=="yolgo_open_meteo" değilse RED · `fetched_at` int ms zorunlu · `temp_c`, `feels_c`, `rain_mm`, `rain_pct`, `wind_kmh`, `gust_kmh` number/null · `code` (WMO) int/null. Bayatlık: `now − fetched_at ∉ [0, MAX_AGE_MS=5400000]` (90 dk) → null; teklif `not_observed`.
- Teklif kaydına: `weather_source, weather_fetched_at, weather_temp_c, weather_feels_c, weather_rain_mm, weather_rain_pct, weather_wind_kmh, weather_gust_kmh, weather_code` (OfferWeather.kt:33-36); ekonomi v2 `weather{ambientC, gustMs (=gust_kmh/3,6, 2 bas.), rain (rain_mm>0 ya da code 51..99), source:"open_meteo"}` (EconomicsLogs.kt:133-136).
- Tetik: teklif anında ve IDLE ekranda `WeatherFeedReader.current` (OrderTracker.kt:351,359).
- Bağımlı: Y10, R15.
- WEEM: `:map` (hava) üretir, `:orders` tüketir. 2 telefonda Receiver→Provider "hava" (Receiver interneti Provider hotspot'undan; Provider kendi de çekebilir — açık soru §5).

### P10 Ev paylaşımı (Rawstats → YolGo) — action `com.kuryelog.app.action.SHARE_HOME`
- Eski kod: `RS:HomeShareActivity.kt:1-25`; manifest:89-95 (`exported="true"`, `excludeFromRecents`, `noHistory`, `Theme.Translucent.NoTitleBar`, kategori DEFAULT).
- Davranış: `callingPackage == "az.duzyol.app"` ise `HomeLocation.get` → `RESULT_OK` + extras `lat`,`lon` (Double); ev yoksa ya da başka çağıran → `RESULT_CANCELED`; ekran yok, hemen `finish` (HomeShareActivity.kt:13-19). İmza denetimi YOK (yalnız paket adı). Ev hiçbir zaman günlüğe, dışa aktarmaya, sağlayıcı feed'ine girmez (HomeShareActivity.kt:11).
- Bağımlı: G2.

### P11 Haritadan ev seçme (Rawstats → YolGo) — action `com.myapplab.yolgo.PICK_HOME`
- Eski kod: `RS:HomePickContract.kt:1-15`, `RS:MainActivity.kt:1282-1295,1467-1475`; manifest `<queries><package android:name="az.duzyol.app"/>` (manifest:16-18).
- Davranış: `Intent(ACTION).setPackage("az.duzyol.app")`, kayıtlı ev varsa extras `lat`,`lon` başlangıç olarak; `startActivityForResult(…, REQUEST=32)`. Sonuç `RESULT_OK` + `lat`,`lon` **Double** olmalı (başka tip → null), `HomeDraft.create` doğrular (sonlu, [-90,90], [-180,180]) → taslak, kaydetmeden önce kullanıcı onayı. Hata metinleri: `ActivityNotFoundException` → "YolGo kurulu değil veya bu sürüm haritadan seçimi desteklemiyor…", `SecurityException` → "YolGo açılamadı…", geçersiz sonuç → "YolGo geçerli bir konum göndermedi." (MainActivity.kt:1287-1293,1473).
- Bağımlı: G2, Y1.

### P0-YG YolGo tarafı: imza, okuma köprüsü, gözlemci
- İmza: `CourierFeedPayload.PRODUCER="com.kuryelog.app"`, `PRODUCER_SHA256="f679b77eaedd8cc7363aa79e5e9149a7cf91d413f0f13dfa472e990551f11883"` (Rawstats `ci/kuryelog-test.keystore`) (`YG:android_overlay/CourierFeedBridge.kt:75-79`); `trustedSigner` = paket eşit ∧ tek imzacı ∧ SHA-256 eşit. `CourierFeedSigner.trusted` önce `resolveContentProvider("com.kuryelog.app.economics")` sağlayıcısının paketinin `com.kuryelog.app` olduğunu, sonra imzasını denetler; API<28'de `GET_SIGNATURES` (`YG:android_overlay/CourierFeedSigner.kt:8-29`).
- Okuma köprüsü `MethodChannel("yolgo/courier-feed")` (CourierFeedBridge.kt:22-65), tek işçi iş parçacığı: `read` (v1; imza YOK; ≤100000 karakter), `readV2` (imza şart, değilse hata `untrusted_producer` "RAW Stats identity not verified"; `accept` ≤`MAX_CHARS=100000`), `readScooter` (imza YOK; ≤`SCOOTER_MAX_CHARS=8000`), `readShift` (imza şart; ≤`SHIFT_MAX_CHARS=4000`), `getLaunchRequest`. Projeksiyon `["payload"]`, `getColumnIndexOrThrow("payload")`. Boş yük reddedilir. Başarılı okuma → `RawstatsObserver.readSucceeded` (grant var: hemen dinle).
- Eski başlatma yolu: `isLaunch` = extra `courierEconomics=true` ∧ data == v1 URI → Flutter'a `open` (CourierFeedBridge.kt:21,66-70). Rawstats tarafı bu niyeti E5'te kaldırdı (`RS-test:YolGoButtonE5Test.kt:9,31`); YolGo'da alıcı hâlâ duruyor (ölü kod).
- Gözlemci `RawstatsObserver` (`YG:android_overlay/RawstatsObserver.kt:72-120`): tek `ContentObserver`, `registerContentObserver(Uri.parse("content://com.kuryelog.app.economics"), notifyForDescendants=true, o)`; SecurityException → `RETRY_MS=30000` sonra yeniden; kayıt süreç boyu kalır; `HandlerThread("YolGo-rawstats-observer")`. Yol → kanal (`RawstatsChanges.of`, :26-32): `/offers/current|/offers/v2/current`→`offers`, `/scooter/v1/live`→`scooter`, `/shift/v1/state`→`shift`, `/logs/v3/…`→`logs`, diğer→null (= herkesi ilgilendirir, `concerns`, :35).
- `ReadGate(minGapMs)` (:43-62): duyuru → 0 (şimdi oku) / gecikme (bir takip okuması) / null (zaten planlı); okumalar en az `minGapMs` aralıklı, son değişiklik kaybolmaz.
- Flutter'a köprü `MethodChannel("yolgo/rawstats-signals")` (:127-158): `changed` (arg `offers|scooter|shift|all`; `logs` GÖNDERİLMEZ), `listening` (bool), `rows` ({day,count}: veri seti `yolgo-live.jsonl` yeni satır), `status` → `{listening}`.

### P8-YG Navigasyon sağlayıcısı (üretici tarafı)
- Eski kod: `YG:lib/nav/nav_feed.dart:1-227` (yük, ölçüm, kapı), `YG:android_overlay/NavFeedProvider.kt:24-119` (`NavFeedContract`, `NavFeedProvider`, `NavFeedBridge`), `:126-142` (`IncomingLink`).
- Üretim (nav_feed.dart): `target` = `NavState.activeStop`; `stop.purpose=='home'` ya da durak yok → null; koordinat sonlu ve |lat|≤90, |lon|≤180 değilse null; lat/lon **6 ondalığa yuvarlanır**; `kind` = `stop.restaurant` true/false/null → `restaurant|customer|unknown`; `origin` bilinmeyen → `map`; `requestedAtMs` = `stop.requestedAt ?? seenAt` (eski durakta hedefin ilk görüldüğü an); `sourcePackage` = `stop.sourcePackage` (nav_feed.dart:68-77).
- `route` = etkin (ilk) bacak, konum izdüşümü: `remainingM` = max(0, çizgi uzunluğu − izdüşüm) + `leg.walkM`, 0,1 m'ye yuvarlı; `remainingTurns` = izdüşümden sonraki dönüş sayısı (hep int); `measuredAtMs` = fix zamanı; **`onRoute`** = izdüşüm sapması ≤ `offRouteM=40` m (Rawstats bu alanı OKUMAZ) (nav_feed.dart:47-59,94-110). Yalnız doğruluğu ≤50 m olan fix kullanılır (nav_feed.dart:186,193).
- Yayın kapısı `NavFeedGate` (nav_feed.dart:114-140): hedef değişti (eşitlik tüm alanlar) ∨ rota var/yok değişti ∨ dönüş sayısı değişti ∨ |Δ remainingM| ≥ `minMoveM=20` → hemen; yoksa rotadayken (`onRoute`) son yayından ≥`heartbeat=10 sn`. `updatedAtMs` yalnız içerik değişince ilerler; `generatedAtMs` her yayında. Açılışta bir kez yayın. Yük >4000 karakter → gönderilmez.
- Taşıma: Flutter → `MethodChannel("yolgo/nav-feed")` `publish(payload)` → `NavFeedProvider.publish`: `accept` (JSON nesne, `schemaVersion`==1, `source`=="yolgo", `generatedAtMs`>0, ≤4000) değilse false; yük **yalnız bellekte** (`@Volatile payload`), grant yenile, `notifyChange(uri, null, 0)` (NavFeedProvider.kt:34-40,63-69,104-118). Süreçte hiç yayın yoksa `empty(now)` = `{schemaVersion:1, source:"yolgo", generatedAtMs:now, updatedAtMs:now, target:null, route:null}` (:43-44).
- Grant: `CourierFeedSigner.trustedPackage(c, "com.kuryelog.app")` ise `grantUriPermission(TARGET, uri, READ)` yoksa revoke (API≥26 paket hedefli) — uygulama açılışında ve her yayında (:72-79,109). Sorgu: yol `/nav/v1/current` değilse BOŞ cursor (istisna değil); çağıran kendi uid ya da [paket listesinde `com.kuryelog.app` ∧ imza güvenilir] değilse `SecurityException("YolGo navigation feed: caller not allowed")` (:84-95). insert/delete/update sessizce null/0 (Rawstats'taki gibi istisna değil).
- `IncomingLink.payload` (Flutter'a): `{text, action: view|send|other, referrer (android-app:// paketinden; `[A-Za-z0-9_.]{1,100}`), atMs}` → `origin:intent` ve `sourcePackage` buradan (:126-142).

### P1/P2/P3-YG Teklif feed'i ve aşamalar: YolGo tüketici tarafı
- Okuyucu: `YG:lib/economics/offer_feed.dart:4-96` (`OfferFeedClient`), `YG:lib/economics/courier_stages.dart:1-350` (`CourierFeedV2`, `CourierTimeline`, `CourierStageFollower`), `YG:lib/tracking/shift_controller.dart:54-58,94,120-122,444-520`.
- `OfferFeedClient.read`: önce `readV2`; `untrusted_producer` hatası → null (v1'e DÜŞÜLMEZ: "signer rejection must never be turned into a downgrade", offer_feed.dart:8,13); `MissingPluginException` (eski köprü) → v1 `read`. `decode(raw, version)`: ≤100000, JSON nesne, `schemaVersion==version`, **`manualSession == true`** (değilse null), `offers` List; v2'de ayrıca `offers.length ≤ 8`, `producer` Map, `courierState` Map, her teklifte `v2` Map ∧ `offerKey` String ∧ `platform ∈ {wolt,bolt}` ∧ `firstSeenAtMs`,`lastSeenAtMs` num; bir teklif bozuksa BÜTÜN yük null (offer_feed.dart:27-57). Bilinmeyen alanlar yok sayılır (Map olarak geçer).
- `CourierFeedV2.parse` (courier_stages.dart:122-148): ≤100000; `schemaVersion==2`, `manualSession==true`, `offers` List, `generatedAtMs` int, `courierState` Map, `activeTrips` List; her satır Map ∧ `tripId` boş olmayan String ∧ `platformPackage` String olmalı, **tek bozuk satır = bütün okuma null** ("yolculuk bitti" sanılmasın); yinelenen tripId atlanır; bilinmeyen aşama → `stage=null` (yolculuk var, aşama izlenmez). `changedAtMs` int>0 değilse null. Null = "bilinmiyor", "bitti" DEĞİL. Teklif adresleri okunmaz/saklanmaz (courier_stages.dart:104-105).
- `CourierTimeline.parseAll` (:76-101): `stages.version==1` ∧ `trips` List değilse null (eski Rawstats); her trip: `tripId` boş olmayan String, `platformPackage` String, `active` bool, `events` List zorunlu; olay `stage` bilinmiyor ya da `atMs` int>0 değil → o olay atlanır; `pickup` int/null, `source` String/null; `stage` yalnız aktifte, `outcome` yalnız bitmişte (`delivered|delivered_unverified|cancelled|unconfirmed`, bilinmeyen → null); `endedAtMs` int>0 değilse null; aynı tripId ilk geleni kalır.
- `CourierStageFollower` (missingReads=2, keepClosed=20, `freshStage=30 sn`) (:172-350):
  - Bağlı yolculuk yok → kapatılmamış ve aşaması bilinen İLK `activeTrips` öğesi bağlanır; YolGo'da sipariş yoksa yeni sipariş (zaman çizelgesi varsa ilk olayın `atMs`'i [gün başı, şimdi]'ye kırpılarak), varsa süren siparişe bağlanır (`_released` siparişler hariç).
  - Zaman çizelgesi (`active` ∧ olay var) varsa: son uygulanandan İLERİ her olay kendi `atMs`'iyle, saat [son uygulanan ya da sipariş başı, şimdi] aralığına kırpılır; geri yönlü olay uygulanmaz; konum yalnız olay ≤30 sn tazeyse yazılır.
  - Yoksa (eski Rawstats): aşama değişince ileri al (elle değişiklik kazanır).
  - Bağlı yolculuk `activeTrips`'te yok: `stages`'te bitmiş + `outcome` varsa hemen: `cancelled` → alıştan önceyse iptal, değilse sipariş serbest; diğer üçü → `endedAtMs`'te teslim. `stages` yoksa art arda 2 okumada yoksa: son aşama to/at_dropoff → teslim; restoran aşamasındaysa iptal; değilse serbest. İlk eksik okumadan sonra `feedRecheck=2 sn` sonra ikinci okuma.
  - Kalıcı durum pref `courier.follow.v1`: `{v:1, trip, order, stage, at, missing, closed[], released[]}` (müşteri verisi yok) (:322-349; shift_controller.dart:67).
- Okuma temposu (shift_controller.dart:446-520): yalnız gün açık ∧ `settings.followCourierStages`; duyuru → hemen (ReadGate 300 ms, okuma sürerken gelen → tek takip); yedek tık: okuma yoksa 30 sn, gözlemci dinliyorsa 60 sn, dinlemiyorsa 4 sn (`feedFallback`); okuma zaman aşımı 5 sn. Belge E8 §5 "7 sn yoklama" der (eski YolGo 1.8.x).
