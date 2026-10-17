# Veri sözleşmesi — WEEM'in koruyacağı adlar ve biçimler

Durum: A0.1, 17 Ekim 2026. Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04` (kısaltma **RS** = `app/src/main/java/com/kuryelog/app/`, **NB** = `ninebot-logger/app/src/main/java/az/ninebot/logger/`) ve `yolgo-test` @ `870ab6e` (**YG**). Kural: plan Bölüm 2. Bu belgedeki bir adı değiştirmek kullanıcı kararıdır.

Sütun **WEEM**: `aynı` = harfiyen korunur · `oku` = WEEM eski veriyi okur (ilk açılış/içe aktarma), yeni yazım aynı biçimde · `içe aktar` = YolGo'dan dışa aktarma → WEEM'e alma (Aşama 7) · `kalkar` = tek uygulamada gereksiz (S7, plan Bölüm 8) · `yeni` = WEEM'de eklenir.

Ayrıntılı alan listeleri ve satır başvuruları: `docs/sartname/bulgular/` (ham, kısmi; mekanik denetim: 634 başvurudan 18'i geçersiz satır, aşağıda kullanılmadı).

## 1. Kimlik ve derleme

| Kalem | Değer | Kaynak | WEEM |
|---|---|---|---|
| applicationId / namespace | `com.kuryelog.app` | `app/build.gradle.kts:7-16` | aynı |
| İmza | `ci/kuryelog-test.keystore`, alias `kuryelogtest`; şifreler `app/build.gradle.kts:21,23`'te (belgeye yazılmaz) | `app/build.gradle.kts:18-29` | aynı anahtar: WEEM debug (`.dev`) ve release (`com.kuryelog.app`) ikisi de bununla imzalı; sertifika SHA-256 `f679…1883`, `ci/check_identity.sh` denetler |
| Sürüm | versionCode 33, versionName `0.25.1` | `app/build.gradle.kts` | kod >33, tek yönlü artar |
| SDK | minSdk 29, target/compile 36, Java/JVM 17 | `app/build.gradle.kts` | aynı (JVM 17 ya da 21: A1.1'de) |
| Araçlar | Gradle 9.3.1, AGP 9.1.0, Kotlin 2.4.0; sürüm kataloğu yok; `android.builtInKotlin=false`, `android.newDsl=false`, `nonTransitiveRClass=true` | `gradle/wrapper/gradle-wrapper.properties:3`, kök `build.gradle.kts:2-4`, `gradle.properties` | aynı sürümler; katalog `yeni` |
| Bağımlılık | `com.google.mlkit:text-recognition:16.0.1` (yerel OCR), `androidx.viewpager2:1.1.0`; test `junit 4.13.2`, `org.json 20240303` | `app/build.gradle.kts:49-55` | ML Kit aynı; viewpager2 kalkar (Compose) |
| Sürüş Paneli modülü | `:surus-paneli` = `ninebot-logger/app`, namespace `az.ninebot.logger`, `BuildInfo.VERSION` | `settings.gradle.kts` | kalkar (`:scooter` Kotlin); bileşen sürümü `yeni` |
| Eski YolGo | paket `az.duzyol.app`, etiket "YolGo"; imza SHA-256 `32d0…cb45` (`android_overlay/debug.keystore`, RS `FeedSigner.kt:9`) | YG `android_overlay/AndroidManifest.xml:20-24` | içe aktarma kaynağı |
| Yedekleme | RS `allowBackup="false"` + `dataExtractionRules=@xml/data_extraction_rules` (bulut ve cihaz aktarımında root/database/sharedpref/file hariç) · YG `allowBackup` yok (varsayılan true) | RS `AndroidManifest.xml:25-39`, `res/xml/data_extraction_rules.xml:2-3` | aynı (RS kuralı) |

## 2. Manifest bileşenleri

| Bileşen | Tür ve özellikler | Kaynak | WEEM |
|---|---|---|---|
| `com.kuryelog.app.RawstatsApp` | Application; çökme kaydı kurulumu | RS manifest:30 | ad serbest (önerilen `WeemApp`) |
| `.MainActivity` | exported, MAIN/LAUNCHER | RS manifest:40-46 | ad serbest; tek Activity (Compose); YolGo intent filtreleri buraya (Bölüm 4) |
| **`com.kuryelog.app.WatchService`** | AccessibilityService, exported, `BIND_ACCESSIBILITY_SERVICE`, meta-data `android.accessibilityservice` → `@xml/accessibility_config` | RS manifest:52-62 | **aynı ad** (izin bileşen adına bağlı) |
| **`com.kuryelog.app.NotifService`** | NotificationListenerService, exported, `BIND_NOTIFICATION_LISTENER_SERVICE` | RS manifest:65-72 | **aynı ad** |
| `.GpsService` | exported=false, `foregroundServiceType="location"` (vardiya) | RS manifest:75-77 | ad serbest; tür aynı |
| `.OfferFeedProvider` | authority `com.kuryelog.app.economics`, exported=false, grant yolları `/offers/current`, `/offers/v2/current`, `/logs/v3/day/*`, `/scooter/v1/live`, `/shift/v1/state` | RS manifest:78-88 | kalkar (S7) |
| `.HomeShareActivity` | exported, action `com.kuryelog.app.action.SHARE_HOME` (yalnız `az.duzyol.app`) | RS manifest:89-95 | kalkar (tek uygulama) |
| `.TipAmountActivity`, `.PocketCashActivity` | exported=false, excludeFromRecents (bahşiş/nakit penceresi) | RS manifest:97-104 | Compose diyalog; ad serbest |
| `.PocketCashReceiver`, `.CashTipReceiver` | exported=false (bildirim "Yanıtla") | RS manifest:105-110 | ad serbest |
| `<queries>` | package `az.duzyol.app`; MAIN/LAUNCHER intent (uygulama listesi) | RS manifest:14-24 | `az.duzyol.app` gerekmez (S1: içe aktarma paylaşılan dosyadan) |
| `az.ninebot.logger.DashboardActivity`, `.MainActivity` | exported=false, "Sürüş Paneli" | NB manifest:20-21 | Scooter sekmesi |
| `az.ninebot.logger.RecordingService` | exported=false, `foregroundServiceType="location\|connectedDevice"` | NB manifest:22 | tür aynı; ad serbest |
| YG `.MainActivity` intent filtreleri | VIEW (10 filtre): şemalar `geo`, `google.navigation`, `waze`, `yandexnavi`, `yandexmaps`; https hostlar `waze.com`, `www.waze.com`, `www.google.com` (`/maps`), `maps.google.com`, `maps.app.goo.gl`, `maps.yandex.ru`, `maps.yandex.com`, `yandex.ru`, `yandex.com`, `yandex.az` (pathPrefix `/maps` 4 filtrede); SEND `text/plain`; PROCESS_TEXT `text/plain` | YG `android_overlay/AndroidManifest.xml:27-120` | **aynı filtreler** WEEM Activity'sinde (Y5) |
| YG `.HomePickActivity` / `.HomePickMapActivity` | action `com.myapplab.yolgo.PICK_HOME` | YG manifest:123-130 | kalkar |
| YG `.TelemetryService` (location), `.RoadCalibrationService` (location, Y14), `.DatasetRecordingService` (specialUse) | FGS | YG manifest:139-143 | vardiya servisiyle birleşir; Y14 sonra |
| YG `.WeatherFeedProvider` `az.duzyol.app.weather`, `.NavFeedProvider` `az.duzyol.app.nav` | exported=false, grant | YG manifest:145-153 | kalkar (iç arayüz) |
| YG `.MotionReceiver` | ActivityRecognition (yürüme güveni) | YG manifest:138 | `yeni` değil: korunur (`:map`) |

## 3. İzinler

| İzin | RS | NB | YG | WEEM |
|---|---|---|---|---|
| `INTERNET` | **kaldırılmış** (`tools:node="remove"`, manifest:4) | — | var | var (karar: uygulama internetli, `:capture` ağsız ve test denetler) |
| `ACCESS_FINE/COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` | var | fine/coarse | fine/coarse | var |
| `FOREGROUND_SERVICE`, `_LOCATION` | var | var | var | var |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | — | var | — | var (scooter) |
| `FOREGROUND_SERVICE_SPECIAL_USE` | — | — | var (veri seti) | gerekirse |
| `POST_NOTIFICATIONS`, `WAKE_LOCK`, `VIBRATE` | var | var | var | var |
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (+ `BLUETOOTH`/`_ADMIN` maxSdk 30), feature `bluetooth_le` required=false | — | var | — | var |
| `SYSTEM_ALERT_WINDOW` | — | var (yüzen panel) | var | var |
| `ACTIVITY_RECOGNITION` | — | — | var | var |
| 2 telefon / uzaktan | — | — | — | `yeni`: `NEARBY_WIFI_DEVICES`, `BLUETOOTH_ADVERTISE`, `CAMERA` (QR), `FOREGROUND_SERVICE_MEDIA_PROJECTION` (A8/A9'da kesinleşir) |

## 4. Sağlayıcılar, intent'ler, URI'ler (iki uygulama sözleşmesi)

Bugünkü mesajlar (alan alan: `bulgular/X_protocol.md` P1–P11; WEEM'deki karşılıkları plan Bölüm 4.4):

| Kanal | URI / action | Yön | WEEM |
|---|---|---|---|
| Teklif feed v1 | `content://com.kuryelog.app.economics/offers/current` (schemaVersion 1, dondurulmuş) | RS → YG | kalkar; içerik `OfferSummary` iç mesajına |
| Teklif feed v2 + `stages` | `…/offers/v2/current` (schemaVersion 2, `docs/ekonomi-v2/feed_v2.schema.json`, `TripStages` version 1) | RS → YG | iç mesaj + 2 telefon P→R |
| Canlı scooter | `…/scooter/v1/live` (schemaVersion 1, `source` `rawstats_surus_paneli`) | RS → YG | iç mesaj (Receiver içinde) |
| Vardiya durumu | `…/shift/v1/state` (schemaVersion 1) | RS → YG | iç mesaj + P→R |
| Gün kayıtları v3 | `…/logs/v3/day/YYYY-MM-DD` (manifest + 32 KB sayfa, `docs/LOG_FEED_V3.md`) | RS → YG (veri seti) | kalkar; Y19 dosyaları doğrudan okur |
| Navigasyon | `content://az.duzyol.app.nav/nav/v1/current` (schemaVersion 1, `source` `yolgo`) | YG → RS | iç mesaj + R→P |
| Hava | `content://az.duzyol.app.weather/weather/current` (schemaVersion 1, `source` `yolgo_open_meteo`) | YG → RS | iç olay; 2 telefon modunda gönderilmez, her telefon kendi çeker (S6) |
| Ev paylaşımı | action `com.kuryelog.app.action.SHARE_HOME` (YG istek kodu 703; extra `lat`,`lon`) | RS → YG | kalkar (S7; tek ev, `:core`) |
| Ev seçme | action `com.myapplab.yolgo.PICK_HOME` | RS → YG | kalkar (S7; WEEM içi harita) |
| Değişiklik bildirimi | `contentResolver.notifyChange` (`FeedChanges`: içerik anahtarı, ≥200 ms, kalp atışı) | RS → YG | iç olay akışı; `:link` kuyruğunda aynı kural |

## 5. SharedPreferences

### 5.1 Rawstats — dosya `kuryelog` (`Store.prefs`, RS `Store.kt:46-47`) — WEEM: **oku + aynı**

| Anahtar | Tip (varsayılan) | Anlam | Sahibi · aşama |
|---|---|---|---|
| `lang` | String `tr`\|`az`\|yok | dil | `:app` · A1.3 |
| `theme` | String `system`\|`light`\|`dark` | **yeni** (A1.3) | `:app` |
| `shift`, `manualSession`, `autoStarted`, `autoShift` | Bool (false) | `CORE` 1.1 | `:core` · A2.4 |
| `manualPolicy` | **Int** (0) | göç işareti | `:core` · A2.4 |
| `shiftStart`, `shiftEndedAt`, `shiftInterruptedAt` | Long ms (0) | `CORE` 1.1, 1.5 | `:core` · A2.4 |
| `shiftStartReason`, `shiftEndReason` | String | `CORE` 1.1 | `:core` · A2.4 |
| `dist` | Float m (0) | `CORE` 1.3 | `:core` · A2.4 |
| `home.lat`, `home.lon` | **String** | `CORE` 2.1 | `:core` · A2.4 |
| `home.updatedAt` | Long (yok = bilinmiyor) | `CORE` 2.1 | `:core` · A2.4 |
| `home.startBlocked`, `home.endArmed` | Bool (false) | `CORE` 1.6 | `:core` · A2.4 |
| `crashSeenAt` | Long (0) | `CORE` 4.1 | `:core` · A2.3 |
| `weem.deviceId` | String | **yeni** (`CORE` 1.10) | `:core` · A2.4 |
| `targets` | StringSet | izlenen uygulamalar | `:capture` · A3 |
| `userPicked`, `structuredMigration07` | Bool (false) | `CORE` 5.4 | `:capture` / `:orders` · A3 |
| `ocr`, `overlay` | Bool | OCR, rozet | `:capture` / `:orders` · A3 |
| `bx`, `by` | Int (12, 95) | rozet konumu | `:orders` · A3 |
| `commission`, `commission:<pkg>` | Float | filo komisyonu | `:orders` · A3 |
| `customerCard` | Bool | müşteri kartı | `:orders` · A3 |
| `customerCardSizePct`, `restaurantBubbleWidthPct` | Int (85, 90) | balon boyu | `:orders` · A3 |
| `onlineSessions.v1`, `economicsIdle.v1`, `openWaits.v1` | String JSON | açık oturum/bekleme | `:orders` · A3 |
| `cashPending.azn`, `cashPending.at` | String / Long | son Wolt nakit borcu | `:orders` · A3 |
| `economics.vehicle`, `economics.zone`, `economics.appMode` (`shadow`), `economics.scooterId` | String | ekonomi ayarları | `:economics` · A4 |
| `dailyPack` | Bool (true) | günlük paket | `:core` · A4.3 |
| `scooter.autoConnect` | Bool (true) | vardiyada scooter'a bağlan | `:scooter` · A5 |
| `scooter.shiftLink` | Bool | bağlantıyı vardiya açtı | `:scooter` · A5 |
| `shiftOdoFirstKm`, `shiftOdoLastKm` | Long m (−1) | `CORE` 1.4 D8 | `:scooter` · A5 (okuyan `:core`) |
| `shiftOdoLastAt` | Long ms | `CORE` 1.4 | `:scooter` · A5 |
| `economics.share` | Bool | YolGo'ya paylaşım anahtarı | **kalkar** (S7; okunmaz, silinmez) |

`home.ui.*` tercih değildir (Activity durum paketi, 2.2). `pocket_cash`, `cash_received` bildirim yanıt (`RemoteInput`) anahtarlarıdır, tercih değil.

Kaynak: RS `Store.kt:46-47` ve anahtarları yazan dosyalar (`rg` ile A2.1'de tarandı, `CORE.md` 1.1, 2.1, 5.4). Düzeltmeler (17 Ekim, A2.1): `manualPolicy` Int; `home.lat`/`home.lon` String; `home.ui.*` tercih değil.

### 5.2 Sürüş Paneli — dosya `logger` (eski `MainActivity` dosyasından bir kez göç, `legacy_main_migrated`) — WEEM: **oku + aynı**

`auto_live`, `battery_probe`, `ble_name`, `crypto_mode`, `detailed_diagnostics`, `device`, `device_name`, `export_full`, `export_latest`, `export_location`, `external_bms_boards`, `float_ball_dp`, `float_ball_field`, `float_fields`, `float_h_pct`, `float_text_land_pct`, `float_text_port_pct`, `float_thick_pct`, `float_v_pct`, `float_x`, `float_y`, `floating` (vars. true), `immersive`, `keep_screen_on` (vars. true), `legacy_main_migrated`, `sweep` — NB `LoggerPreferences.java:9-18` ve kullanan dosyalar (`grep -ohE '(get|put)(String|Boolean|Int|Long|Float)\("[a-z_]+"'`).

### 5.3 Eşleşme deposu — dosya **`pairing-v1`** + Keystore — WEEM: **aynı** (taşınmazsa scooter yeniden eşleşir)

| Kalem | Değer | Kaynak |
|---|---|---|
| SharedPreferences | `pairing-v1` (cihaz başına şifreli eşleşme malzemesi) | NB `KeyVault.java:14` |
| Android Keystore takma adı | **`NinebotLogger.Pairing.v1`** (AndroidKeyStore; anahtar uygulama kimliğine ve imzaya bağlı) | NB `KeyVault.java:13-15` |

### 5.4 YolGo — `FlutterSharedPreferences` (her anahtar `flutter.` önekli) — WEEM: **aktarılmaz** (S1: eski YolGo'ya dışa aktarma eki yok; WEEM başka paketin verisini okuyamaz)

Anahtarların anlamı WEEM `:map` ayarları için başvurudur; değerler WEEM'de yeniden girilir ya da yeniden öğrenilir.

| Anahtar | İçerik | WEEM (S1) |
|---|---|---|
| `settings.v1` | JSON AppSettings: araç `g3`, enerji katsayıları (`flatWhPerM` 0.0135, `climbWhPerM` 0.38 …), hızlar (`flatKmh` 28.5, `climbKmh` 18.5, `walkKmh` 4.5), eğim/batarya eşikleri, **ev**, `lang`, `lightMaxM` … | yeniden girilir |
| `streets.approved`, `streets.blocked`, `streets.steep`, `streets.names`, `streets.hours.v1`, `streets.blocked.legacy.v1`, `streets.blocked.migrated.v2` | sokak kararları (Y8) | yeniden girilir |
| `streets.bikeway.v1` | elle bisiklet yolları `wayId\|ad` (Y15) | yeniden girilir |
| `lights.v1` | ışık veritabanı (Y11) | yeniden girilir |
| `places.local.v1` | POI düzeltmeleri (Y16) | yeniden girilir |
| `sidewalk.zones.v1` | kaldırım bölgeleri | yeniden girilir |
| `daily.tasks.v1`, `daily.tasks.closed.v1`, `reminders.v1`, `reminders.last.v1` | görevler, hatırlatıcılar (Y20) | yeniden girilir |
| `search.recents.v2` | son aramalar | yeniden girilir |
| `battery.setPct`, `battery.consumed`, `battery.setAt` | batarya tahmini durumu | hayır (anlık) |
| `shift.v1`, `shift.last`, `day.home.v1`, `courier.follow.v1`, `shift.deleted.days.v2`, `ride.active.v1`, `passive.pausedAt.v2`, `rawstats.*` | anlık gün durumu | hayır (WEEM tek vardiya) |
| `weather.enabled.v1`, `weather.strip.v1`, `weather.key.v1`, `weather.permission.asked.v1` | hava ayarları | yeniden girilir (anahtar değeri belgeye yazılmaz) |
| `map.bundled.<region>`, `map.bundledAt`, `clipboard.lastHash` | önbellek | hayır |
| `roads.ways.v1`, `roads.ww.v1`, `roads.ghost.v1`, `roads.wwseg.v1`, `roads.rate.v1`, `roads.names.v1`, `roads.days.v2`, `roads.days.damaged.v2`, `surface.*` | Y12/Y13 verisi | aktarılmaz; Y12 WEEM'de sıfırdan öğrenir (S3, A6.11); Y13 sonra |

YG native: `duzyol_weather` (enabled, az, key, report, fetched, lat, lon, locationAt), `offer_dataset_v1` (region, active), `road_calibration_overlay_v1` — WEEM'de karşılığı `:map` ayarları.

**Önemli:** YolGo'da bu tercihleri ve `lights.v1`, POI düzeltmeleri, görevler, Y12 verisi için **dışa aktarma yolu yok** (`RideLogStore.export` yalnız `logs/` dosyalarını `duzyol-journal-v1` biçiminde paylaşır). Karar (S1, 17 Ekim): eski YolGo'ya ek yapılmaz; bunlar WEEM'de yeniden girilir ya da yeniden öğrenilir.

## 6. Veritabanları

| Veritabanı | Sürüm | Tablolar (CREATE) | Göç | Kaynak | WEEM |
|---|---|---|---|---|---|
| `courier.db` | 1 | `trips(id TEXT PRIMARY KEY, pkg TEXT NOT NULL, day TEXT NOT NULL, payload TEXT NOT NULL)` + `INDEX trips_day(day)` · `offers(id TEXT PRIMARY KEY, pkg TEXT NOT NULL, day TEXT NOT NULL, result TEXT, payload TEXT NOT NULL)` + `INDEX offers_day(day)` · `session(k TEXT PRIMARY KEY, payload TEXT NOT NULL)` (tek satır `k='active'`) | `onUpgrade` boş | RS `CourierDb.kt:10-18,36-37` | aynı ad ve şema; payload = `RecordJson.trip(t, withPhone=true)` / `RecordJson.offer` (müşteri telefonu YALNIZ burada) |
| `ninebot-logger.db` | 1, WAL, `foreign_keys=ON` | `sessions(id INTEGER PRIMARY KEY, started_ms INTEGER NOT NULL, ended_ms INTEGER, state TEXT NOT NULL, device TEXT)` · `events(id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id), wall_ms INTEGER NOT NULL, mono_ns INTEGER NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL)` + `INDEX event_session(session_id,id)` | `onUpgrade` → `IllegalStateException("Migration required")` | NB `LogStore.java:12-20` | aynı ad ve şema; sürüm artarsa göç yazılmalı |

## 7. Dosyalar

### 7.1 Rawstats gün klasörü `files/logs/YYYY-MM-DD/` — WEEM: **aynı adlar ve şemalar**

Tek kaynak `BackupFiles.logNames` (RS `BackupFiles.kt:8-28`; listede olmayan dosya dışa/içe aktarma ve gün silmede kaybolur — `DailyRecordsTest`):

| Dosya | Yazan | Şema kaynağı |
|---|---|---|
| `screens.jsonl`, `notifs.jsonl`, `events.jsonl`, `errors.jsonl` | `WatchService`, `NotifService`, `Store.event/failure` | ham kareler (müşteri verisi `NoteFields.redact` ile) |
| `orders.jsonl` | `OrderTracker.process` | olay satırı: `t, type, pkg, offer_id, id, reason, session_id, record, gross, net, total_min, wait_min, km, restaurant, offer, loc, phone_battery_pct` (müşteri adı düz; telefon yok) |
| `gps.csv` | `GpsService` | CSV |
| `platforms.jsonl` | `OrderTracker` | IDLE/OFFLINE/çarpan |
| `online-sessions.jsonl` | `OnlineSessions` | schemaVersion 2 (v1 okunur) |
| `scooter.jsonl`, `scooter_events.jsonl`, `scooter_averages.jsonl` | eski scooter kaydı | eski (oku) |
| `economics-offers.jsonl`, `economics-legs.jsonl`, `economics-idle.jsonl`, `economics-penalties.jsonl`, `economics-waits.jsonl`, `economics-exposure.jsonl` | `EconomicsLogs` (SPEC 2.1.0) | `docs/ekonomi-v2/kuryelog_logs_v2.schema.json`, `SPEC_V2.md` §17.2 |
| `economics-platform-history.jsonl` (K36), `economics-platform-totals.jsonl` (F8.3, schemaVersion 1) | `PlatformHistory`, `PlatformTotals` | kod |
| `economics-cash-tips.jsonl` (K32, schemaVersion 1), `economics-pocket-cash.jsonl` (M15), `economics-cash-pending.jsonl` (M17) | `CashTips`, `PocketCash`, `CashPending` | kod (`bulgular/R_orders.md` §R8–R9) |
| `economics-boosts.jsonl` (K27/M21, schemaVersion 1) | `BoostLog` | kod (`bulgular/R_orders.md` §R10) |
| `economics-energy.jsonl` | `EnergySegments` | enerji şeması (bacak/bekleme) |
| `economics-scooter.jsonl` | `ScooterSamples` (SCHEMA 1, 1 Hz, yalnız vardiyada) | kod |
| `economics-offer-scores.jsonl`, `economics-offer-ratings.jsonl` | `OfferScore` (E4) | kod |

Diğer: dışa aktarma ZIP'i `YYYY-MM-DD/<dosya>` + kökte `restaurants.json` (`BackupFiles.checkedPath`); `files/crashes.jsonl` (`CrashLog.kt:19`); gün sırları `noBackupFilesDir/economics-day-secrets-v3/<gün>` (32 bayt; ZIP/yedeğe girmez); anonim dışa aktarma işareti `ANALIZ_ANONIM.json` (`AnalysisExport.kt:273`). Analiz araçları (`tools/analysis/kl_export.py`, `anonymize.py`) bu adları bekler.

### 7.2 Sürüş Paneli

Dışa aktarma ZIP'i: `manifest.json` (`schema` 1, `app` "Sürüş Paneli", `version`, `full_diagnostics`, `location_included`, `latest_session_only` …) + filtreli sürüş örnekleri (NB `LogExport.java:15,52-53`). WEEM: aynı biçim.

### 7.3 YolGo (Android yolları: `app_flutter/` = documents, `files/` = support) — WEEM: **içe aktar** ya da yeniden üret

| Yol | İçerik | WEEM |
|---|---|---|
| `app_flutter/logs/YYYY-MM-DD.jsonl`, `…_ride_<ms>.jsonl`, `…[_HHMM].gun.json`, `…_route_<ms>.json` | gün günlüğü, sürüş, gün raporu, rota kaydı (Y17, Y18) | içe aktar (dışa aktarma biçimi `duzyol-journal-v1` var) |
| `files/mapdata/graph_<region>.bin`, `pois_<region>.json`, `dem/N40E049.hgt`, `dem/N40E050.hgt`, `parts_graph_<region>/` | harita verisi (Y7) | yeniden üret (CI `ci/osm_extract.py` + Overpass) |
| `files/telemetry-v2.jsonl` (≤3000 satır) | yerel telemetri | hayır |
| `app_flutter/TeklifVeriSeti/<gün>/…` | veri seti (Y19) | hayır (S1: dışa aktarma yolu yok); WEEM'de yeni veri seti |
| `files/surface-v2/…`, `files/surface-raw-v2/…`, `files/road-calibration-v1/<id>.json` | Y13/Y14 | sonra |

## 8. Bildirim kanalları ve numaraları

### 8.1 Bugün

| No | Kanal (ad, önem) | Uygulama | Kaynak | Sorun |
|---|---|---|---|---|
| 1 | `shift` "Vardiya kaydı", LOW, ongoing (FGS) | RS | `GpsService.kt:157-175` | — |
| 12 | `delivery_notes` "Müşteri teslimat notu", HIGH, titreşim | RS | `DeliveryReminder.kt:303-308` | metinde müşteri adı ve telefonu |
| 4000 + (tripId.hashCode() & 0xFFF) = **4000–8095** | `cash_tips` "Nakit bahşiş sorusu", HIGH | RS | `CashTipPrompt.kt:22-24` | **çakışma:** aralık 4901, 4902, 5101–5103, 7100, 7301'i kapsar |
| 4901 | `pocket_cash` "Vardiya başı nakit sorusu", HIGH | RS | `PocketCashPrompt.kt:25-28` | aralık içinde |
| 4902 | `scooter_link` "Scooter bağlantısı" | RS | `ScooterLink.kt:31-44` | aralık içinde |
| 5101 / 5102 | `auto_start` "Otomatik başlatma", HIGH (ev / scooter) | RS | `AutoStartNotice.kt:17-23` | aralık içinde |
| **5103** | `auto_start` (vardiya) **ve** `screen_reading` "Ekran okuma uyarısı" | RS | `AutoStartNotice.kt:20`, `ScreenReading.kt:38` | **iki sabit aynı numara** |
| 7 | `recording` "Scooter kaydı" (FGS) | NB | `RecordingService.java:64-66` | — |
| 8 | `pairing-attention` "Eşleştirme onayı", HIGH | NB | `PairAttention.java:11,22` | — |
| 7100 (vars.) | `hatirlatici` "Hatırlatıcılar", HIGH; 30 sn zaman aşımı | YG | `MainActivity.kt`, `NotifyPolicy.kt` | — |
| 7301 | `duzyol_weather` "Hava tahmini", DEFAULT, günde bir | YG | `WeatherWorker.kt:129-133` | — |
| — | `yolgo_recording`, `yolgo_calibration`, `yolgo_offer_dataset` (LOW, FGS) | YG | `TelemetryService.kt:144-147` … | — |

### 8.2 WEEM kuralı (A2.4)

Tek tablo `:core` `NotificationIds` (tam tablo `CORE.md` 3.3): her bildirim sabit numara ya da ayrılmış, çakışmayan aralık; test çakışmayı reddeder. Eski numaralar korunur; iki değişiklik: ekran okuma uyarısı **5103 → 5104** (5103 H4 vardiya kesintisinde kalır), nakit bahşiş **4000 + (h & 0xFFF) → 10000 + (h & 0xFFF)** (10000–14095). 7400 (YolGo gün kaydı servisi) kullanılmaz: vardiya servisiyle (1) birleşir. Gün sonu iptali aralık değil açık liste. Kanal kimlikleri aynı kalır (kullanıcının kanal ayarları kanal kimliğine bağlı); yeni kanallar `yeni`.

## 9. Gizlilik sınırı (WEEM'de korunur)

- Müşteri **telefonu** yalnız `courier.db` (`withPhone=true`), bellek, rozet/balon ve bildirim 12'de; JSONL ve yedekte yok (`PhoneK25Test`).
- Müşteri **adı** `courier.db`, `orders.jsonl` (`record`) ve `latest` gözleminde düz; dışa aktarmada `AnalysisExport`/`Redactor` gizler.
- 2 telefon modunda Receiver'a gitmeyecekler: müşteri adı, telefonu, notlar, bildirim 12 içeriği. Gidebilecekler: aşama, saat, bölge (`Z1..Z4`), restoran adı/koordinatı, müşteri koordinatı ve adres metni (`dropoffAddress`; S5, 17 Ekim), rota hedefi (plan Bölüm 4.4).
