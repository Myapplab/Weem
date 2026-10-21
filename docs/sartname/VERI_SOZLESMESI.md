# Veri sözleşmesi — WEEM'in koruyacağı adlar ve biçimler

Durum: A0.1, 17 Ekim 2026; A3.1 notları 7.1'de; A5.1 (scooter) notları 5.2, 5.3, 7.2'de; A6.1 (harita) notları 5.4, 5.5, 7.3, 7.5, 8.2'de (`MAP.md` 14). Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04` (kısaltma **RS** = `app/src/main/java/com/kuryelog/app/`, **NB** = `ninebot-logger/app/src/main/java/az/ninebot/logger/`) ve `yolgo-test` @ `870ab6e` (**YG**). Kural: plan Bölüm 2. Bu belgedeki bir adı değiştirmek kullanıcı kararıdır.

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
| **`com.kuryelog.app.remote.TouchService`** (yeni, A9.2) | AccessibilityService ("WEEM uzaktan dokunuş"), exported, `BIND_ACCESSIBILITY_SERVICE`, meta-data → `@xml/remote_touch_config` (yalnız `canPerformGestures`) | — (N2 yeni) | **bundan sonra aynı ad** (izin bileşen adına bağlı; `REMOTE.md` 2.1) |
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
| 2 telefon / uzaktan | — | — | — | A8.1: `ACCESS_NETWORK_STATE` (`yeni`; taşıma `INTERNET` + `BLUETOOTH_CONNECT` ile; klasik Bluetooth olduğundan `BLUETOOTH_ADVERTISE` ve `NEARBY_WIFI_DEVICES` gerekmez, S23). targetSdk 37'de `ACCESS_LOCAL_NETWORK` (Android 17). A8.2/A9'da: `CAMERA` (QR), `FOREGROUND_SERVICE_MEDIA_PROJECTION` |

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
| 2 telefon taşıması (`yeni`, A8.1) | TCP 47310, UDP işareti 47311, RFCOMM UUID `55a2849b-5970-4a15-a703-6e6dfd08f28f`, işaret `WEEM` + sürüm 1, çerçeve `HELLO` bağlantı sürümü **2** (A8.2: bayrak baytı, tür 6 `NOISE`, `BYE` 8–9; `LINK.md` 1.2–1.4, 3.4) | P ↔ R | iki telefonda aynı olmalı; değişirse eski sürümle bağlanmaz (sürüm artırılır) |
| 2 telefon güvenliği (`yeni`, A8.2) | Noise `Noise_XX_P256_AESGCM_SHA256` / `Noise_XXpsk3_P256_AESGCM_SHA256`, prologue `WEEM link 2`, bilgi yükü (protokol **1.0**), zarf v1 (sınıf, ad, `id`, `seq`, `sentAtMs`, `ttlMs`), denetim adları `link.ack`, `link.time`, `link.unpair` (`LINK.md` 3.2–3.11) | P ↔ R | iki telefonda aynı; protokol ana sürümü artarsa eskisiyle bağlanmaz (`issue = VERSION`), küçük sürüm bağlanır |
| Eşleştirme QR'ı (`yeni`, A8.2; ekran A8.3, S24) | `weem://pair?v=1&id=<cihaz kimliği>&k=<P-256 açık anahtar, base64url>&t=<jeton 32 bayt, base64url>&n=<cihaz adı>` (`LINK.md` 3.3) | veren → okuyan (kamera) | uygulama dışına çıkmaz (Google code scanner); `v` artarsa eski okuyan "güncelleyin" der |

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
| `weem.deviceId` | String: küçük harfli UUID (36 karakter; `DeviceId`) | **yeni** (`CORE` 1.10) | `:core` · A2.4 |
| `weem.setupDone` | Bool (false): ilk açılış kurulumu bitti/atlandı; şifreli yedeğe girmez | **yeni** (G5, A7.2) | `:app` · A7.2 |
| `targets` | StringSet | izlenen uygulamalar | `:capture` · A3 |
| `userPicked`, `structuredMigration07` | Bool (false) | `CORE` 5.4 | `:capture` / `:orders` · A3 |
| `ocr`, `overlay` | Bool (**true**; `CAPTURE.md` 6.4, `ORDERS.md` 6.2) | OCR, rozet | `:capture` / `:orders` · A3 |
| `bx`, `by` | Int (12, 95) | rozet konumu | `:orders` · A3 |
| `commission`, `commission:<pkg>` | Float | filo komisyonu | `:orders` · A3 |
| `customerCard` | Bool | müşteri kartı | `:orders` · A3 |
| `shadowMode` | Bool (false) | gölge mod (yalnız `.dev`; Rawstats'ta karşılığı yok): soru ve pencereler kapalı, kayıt sürer | `:orders` · A3.11 |
| `customerCardSizePct`, `restaurantBubbleWidthPct` | Int (85, 90) | balon boyu | `:orders` · A3 |
| `onlineSessions.v1`, `economicsIdle.v1`, `openWaits.v1` | String JSON | açık oturum/bekleme | `:orders` · A3 (`onlineSessions.v1`); `:economics` · A4.1 (öbür ikisi, `EconomicsObserver`) |
| `cashPending.azn`, `cashPending.at` | String / Long | son Wolt nakit borcu | `:orders` · A3 |
| `economics.vehicle`, `economics.zone`, `economics.appMode` (`shadow`), `economics.scooterId` | String | ekonomi ayarları | `:economics` · A4 |
| `dailyPack` | Bool (true) | günlük paket | `:core` · A4.3 |
| `scooter.autoConnect` | Bool (true) | vardiyada scooter'a bağlan | `:scooter` · A5 |
| `scooter.shiftLink` | Bool | bağlantıyı vardiya açtı | `:scooter` · A5 |
| `shiftOdoFirstKm`, `shiftOdoLastKm` | Long m (−1) | `CORE` 1.4 D8 | `:scooter` · A5 (okuyan `:core`) |
| `shiftOdoLastAt` | Long ms | `CORE` 1.4 | `:scooter` · A5 |
| `economics.kwhPrice`, `economics.chargeEfficiencyPct`, `economics.dailyGoalAzn` | Float (yok: maliyet yok sayılır / %90 / hedef yok) | **yeni** (A4.2, S8): kWh fiyatı ₼, şarj verimi %, günlük net hedef ₼ | `:economics` · A4.2 (`NetSettings`) |
| `economics.share` | Bool | YolGo'ya paylaşım anahtarı | **kalkar** (S7; okunmaz, silinmez) |

`home.ui.*` tercih değildir (Activity durum paketi, 2.2). `pocket_cash`, `cash_received` bildirim yanıt (`RemoteInput`) anahtarlarıdır, tercih değil.

Kaynak: RS `Store.kt:46-47` ve anahtarları yazan dosyalar (`rg` ile A2.1'de tarandı, `CORE.md` 1.1, 2.1, 5.4). Düzeltmeler (17 Ekim, A2.1): `manualPolicy` Int; `home.lat`/`home.lon` String; `home.ui.*` tercih değil.

### 5.2 Sürüş Paneli — dosya `logger` (eski `MainActivity` dosyasından bir kez göç, `legacy_main_migrated`) — WEEM: **oku + aynı**

`auto_live` (false), `battery_probe` (false), `ble_name`, `crypto_mode` (`gen2_fw`; eski `gen3_zero` aynı anlama okunur), `detailed_diagnostics` (false), `device` (BLE adresi), `device_name`, `external_bms_boards` ("06" gibi), `float_ball_dp` (100), `float_ball_field`, `float_fields`, `float_h_pct` (80), `float_text_land_pct` (100), `float_text_port_pct` (100), `float_thick_pct` (125), `float_v_pct` (60), `float_x`, `float_y`, `floating` (vars. true), `keep_screen_on` (vars. true), `legacy_main_migrated`, `sweep` (false) — NB `LoggerPreferences.java:8-20` ve kullanan dosyalar (`grep -ohE '(get|put)(String|Boolean|Int|Long|Float)\("[a-z_]+"'`). Göç: `device` yoksa `device`/`device_name`/`ble_name` birlikte, `sweep`, `crypto_mode` eski `MainActivity` dosyasından bir kez. **A5.1 düzeltmesi:** `export_full`, `export_latest`, `export_location`, `immersive` tercih değil, `DashboardActivity` örnek durum paketi (NB `DashboardActivity.java:36, 61, 203`). Ayrıntı `SCOOTER.md` 11.1.

### 5.3 Eşleşme deposu — dosya **`pairing-v1`** + Keystore — WEEM: **aynı** (taşınmazsa scooter yeniden eşleşir)

| Kalem | Değer | Kaynak |
|---|---|---|
| SharedPreferences | `pairing-v1` (cihaz başına şifreli eşleşme malzemesi) | NB `KeyVault.java:14` |
| Android Keystore takma adı | **`NinebotLogger.Pairing.v1`** (AndroidKeyStore; anahtar uygulama kimliğine ve imzaya bağlı) | NB `KeyVault.java:13-15` |
| Değerler (adres = BLE MAC) | `<adres>` etkin, `<adres>.confirmed` (Bool), `<adres>.pending` aday, `<adres>.previous`, `<adres>.previous.confirmed`; değer `Base64.NO_WRAP(IV12 ‖ AES-GCM(anahtar16)+etiket16)`, AAD = adres UTF-8 (A5.1, `SCOOTER.md` 4.1) | NB `KeyVault.java:15-26` |

### 5.3b WEEM şifreli yedek — dosya **`weem-backup`** + Keystore `WEEM.Backup.v1` — WEEM: **yeni** (A4.3, N3, S22)

Parola AndroidKeyStore AES/GCM anahtarıyla şifreli (`password` = Base64 IV `:` Base64 şifreli metin); `exported.<Pazartesi>` (Long) = o haftanın yedeği dışa aktarıldı. `kuryelog` dışında: hiçbir yedeğe girmez. Tercih `dailyPack` (Bool, true) `kuryelog`'da (5.1).

### 5.3c WEEM 2 telefon eşleşmesi — dosya **`weem-link`** + Keystore **`WEEM.Link.v1`** — WEEM: **yeni** (A8.2, `LINK.md` 3.12)

| Anahtar | Tip / değer |
|---|---|
| `identity.key` | String: Base64 NO_WRAP(IV 12 ‖ AES-256-GCM(kimlik özel anahtarı, PKCS#8) + etiket 16), AAD `weem-link/identity/v1`; ana anahtar AndroidKeyStore `WEEM.Link.v1` (AES-256-GCM, kullanıcı doğrulaması yok) |
| `identity.public` | String: Base64 NO_WRAP(P-256 SEC1 sıkıştırılmamış, 65 bayt) |
| `peer.id`, `peer.public`, `peer.name`, `peer.pairedAtMs` | eşleşen telefon: cihaz kimliği (küçük harfli UUID), açık anahtarı (Base64 SEC1), cihaz adı (≤ 32 bayt), eşleşme zamanı (Long, ms) |
| `peer.btAddress` | String: A8.1'in öğrendiği Bluetooth adresi (büyük harf), yoksa yok |
| `role` | String: 2 telefon rolü `off` \| `provider` \| `receiver` (A8.3, `LINK.md` 4.1); yoksa ya da tanınmazsa `off`. Eşleşme kaldırılınca kalır |

Dosya `MODE_PRIVATE`, yazma eşzamanlı (`commit`). Yedeğe, dışa aktarmaya ve `kuryelog`'a girmez; eşleşme kaldırılınca `peer.*` silinir, `identity.*` kalır.

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
| `weather.fetched` (Long ms), `weather.notified` (Long ms), `weather.notifiedDay` (String), `weather.lat`, `weather.lon` (String), `weather.locationAt` (Long ms) | hava çalışma durumu (A6.7; YolGo `duzyol_weather` içindeki `fetched`/`notified`/`notifiedDay`/`lat`/`lon`/`locationAt`); rapor gövdesi `files/map/weather.json` | hayır (önbellek) |
| `map.bundled.<region>`, `map.bundledAt`, `clipboard.lastHash` | önbellek | hayır |
| `roads.ways.v1`, `roads.ww.v1`, `roads.ghost.v1`, `roads.wwseg.v1`, `roads.rate.v1`, `roads.names.v1`, `roads.days.v2`, `roads.days.damaged.v2`, `surface.*` | Y12/Y13 verisi | aktarılmaz; Y12 WEEM'de sıfırdan öğrenir (S3, A6.11); Y13 sonra |

YG native: `duzyol_weather` (enabled, az, key, report, fetched, lat, lon, locationAt), `offer_dataset_v1` (region, active), `yolgo_runtime_v2` (`TelemetryService` durumu: day, leg, eligible, paused, rawDebug, calibrationLabel, profile, boot), `road_calibration_overlay_v1`. WEEM: `duzyol_weather`'ın karşılığı `map` dosyası + `files/map/weather.json` (`MAP.md` 10.11); `offer_dataset_v1` ve `yolgo_runtime_v2` yazılmaz (servisleri yok, `MAP.md` 12.11, 13.11); `road_calibration_overlay_v1` Y14 ile sonra; ayrı yerel tercih dosyası açılmaz.

YolGo göç işaretleri (`settings.v1` alanları `lightMaxMv2`, `lightMaxMv3`, `mapDesignV1`; anahtarlar `streets.blocked.legacy.v1`, `streets.blocked.migrated.v2`) WEEM'de yazılmaz (S1: göç edilecek eski veri yok; `MAP.md` 14.2–14.3).

**Önemli:** YolGo'da bu tercihleri ve `lights.v1`, POI düzeltmeleri, görevler, Y12 verisi için **dışa aktarma yolu yok** (`RideLogStore.export` yalnız `logs/` dosyalarını `duzyol-journal-v1` biçiminde paylaşır). Karar (S1, 17 Ekim): eski YolGo'ya ek yapılmaz; bunlar WEEM'de yeniden girilir ya da yeniden öğrenilir.

### 5.5 WEEM harita — dosya **`map`** — WEEM: **yeni** (Aşama 6; `MAP.md` 14.1–14.3)

5.4'teki YolGo anahtarlarının WEEM'deki yeri: aynı ad ve biçim, **`flutter.` öneki olmadan** (`settings.v1` alan tablosu `MAP.md` 14.2, öteki anahtarlar 14.3); tip eşlemesi `MAP.md` 14.1 (double → `putString`, `List<String>` → JSON dizi dizgesi, int → `putLong`). Boş başlar (S1). İstisnalar: dil, tema ve ev `kuryelog`'da (5.1: `lang`, `theme`, `home.lat`/`home.lon`); `day.home.v1` ve `rawstats.*` yazılmaz (ev kuralı `:core`'da; `rawstats.*` yerine yeni `day.shift.handled.v1`); Y12 `roads.*` tercihlerde değil, `files/map/roads/`'ta (7.5); göç işaretleri yazılmaz (5.4). Sırlar (`googleKey`, `orsKey`, `cartoKey`, `weather.key.v1`) hiçbir kayda, dışa aktarmaya ve yedeğe yazılmaz (`MAP.md` 14.10). Haftalık şifreli yedeğe (S22) bugün girmez (soru `MAP.md` 17.1 #15).

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
| `economics-cash-tips.jsonl` (K32, schemaVersion 1), `economics-pocket-cash.jsonl` (M15), `economics-cash-pending.jsonl` (M17) | `CashTips`, `PocketCash`, `CashPending` | kod (`ORDERS.md` 7–8) |
| `economics-boosts.jsonl` (K27/M21, schemaVersion 1) | `BoostLog` | kod (`ORDERS.md` 9.1; null'lar `JSONObject.NULL`, aşağıdaki not) |
| `economics-energy.jsonl` | `EnergySegments` | enerji şeması (bacak/bekleme) |
| `economics-scooter.jsonl` | `ScooterSamples` (SCHEMA 1, 1 Hz, yalnız vardiyada) | kod |
| `economics-offer-scores.jsonl`, `economics-offer-ratings.jsonl` | `OfferScore` (E4) | kod |

Diğer: dışa aktarma ZIP'i `YYYY-MM-DD/<dosya>` + kökte `restaurants.json` (`BackupFiles.checkedPath`); `files/crashes.jsonl` (`CrashLog.kt:19`); gün sırları `noBackupFilesDir/economics-day-secrets-v3/<gün>` (32 bayt; ZIP/yedeğe girmez); anonim dışa aktarma işareti `ANALIZ_ANONIM.json` (`AnalysisExport.kt:273`). Analiz araçları (`tools/analysis/kl_export.py`, `anonymize.py`) bu adları bekler.

**A4.3 (18 Ekim):** dışa aktarma ve paylaşım WEEM adlarıyla (RS `Rawstats`): tam yedek `İndirilenler/WEEM/WEEM_<gün|all>_<dosya|logs>_<HHmmssSSS>.zip`, anonim `WEEM_analiz_anonim_<gün>_<HHmmssSSS>.zip`, günlük paket `İndirilenler/WEEM/gunluk/WEEM_gunluk_<gün>.zip`; ZIP içi biçim Rawstats ile aynı (`BackupExportTest`, `AnalysisToolsTest`). N3 şifreli yedek `İndirilenler/WEEM/yedek/WEEM_yedek_<Pazartesi>_<Pazar>.weem`: `WEEMBAK1` + PBKDF2 tur (4 bayt) + tuz 16 + IV 12 + AES-256-GCM (başlık AAD) şifreli ZIP: gün dosyaları, `restaurants.json`, `kuryelog.json` (ev konumu `home.*`, anlık vardiya anahtarları ve `weem.deviceId` hariç), `courier/trips.jsonl`, `courier/offers.jsonl`, `WEEM_YEDEK.json` (`weem-encrypted-backup/1`); `logic/core` `EncryptedBackup`.

**A3.1 notları (17 Ekim; ayrıntı `CAPTURE.md` 6, `ORDERS.md` 9, 12, 14):** adlar ve şemalar aynı; aşağıdakiler değer/yazım ayrıntılarıdır.
- `events.jsonl` `click.text` WEEM'de `NoteFields.redact`'ten geçer (telefon → `[tel]`); Rawstats redact etmeden yazıyordu (müşteri kartında dokunulan telefon JSONL'e girebiliyordu, bu §9 kuralının deliği). Alan adı değişmez; gölge modda fark yalnız telefon içeren tıklama satırlarında çıkar ("beklenen fark", `CAPTURE.md` 6.3).
- `economics-boosts.jsonl` (ve teklife yazılan `weather_*` alanları) null değerleri `JSONObject.NULL` ile yazar: anahtar vardır, değer `null`. Öteki dosyalarda `put(k, null)` anahtarı siler (null alan yazılmaz). WEEM ikisini de aynen korur; JSON karşılaştırması ayrıştırılmış değerle yapılır (`ORDERS.md` 9.1).
- `weather_source` değeri `yolgo_open_meteo` **korunur** (WEEM'de YolGo ayrı uygulama değil, ama eski/yeni kayıtlar aynı okunur; analiz araçları ve `EnergySegments` bunu bekler). Aynı şekilde `nav_yolgo`, `gps_dwell_yolgo_target`, `gps_near_yolgo_target`, `yolgo_venue` kayıt dizgileri korunur (`ORDERS.md` 12, 16).
- Tercih varsayılanları: `ocr` ve `overlay` **true**, `targets` boş, `userPicked` false (`CAPTURE.md` 6.4).

### 7.2 Sürüş Paneli

Dışa aktarma ZIP'i `NinebotLogger-<ms>.zip`: `manifest.json` (`schema` 1, `app` "Sürüş Paneli", `version`, `decoder_available`, `device_verified`, `utc_export_ms`, `wire_transcript_sensitive`, `full_diagnostics`, `location_included`, `latest_session_only`, `note`) + `sessions.jsonl` + `events.jsonl` (filtreli ZIP'te izin listesi, `device` null; NB `LogStore.java:45-75`, `LogExport.java:7-34`, `DashboardActivity.java:194`). `sessions.state` değerleri ve `events.kind` listesi: `SCOOTER.md` 11.2. WEEM: aynı biçim. WEEM'de dosya İndirilenler/WEEM'e yazılır ve paylaşılır (A5.4, `SCOOTER.md` F-S9).

### 7.3 YolGo (Android yolları: `app_flutter/` = documents, `files/` = support) — WEEM: **içe aktar** ya da yeniden üret

| Yol | İçerik | WEEM |
|---|---|---|
| `app_flutter/logs/YYYY-MM-DD.jsonl`, `…_ride_<ms>.jsonl`, `…[_HHMM].gun.json`, `…_route_<ms>.json` | gün günlüğü, sürüş, gün raporu, rota kaydı (Y17, Y18) | içe aktar (A7.1; dışa aktarma biçimi `duzyol-journal-v1` var); WEEM yolu `files/map/logs/`, aynı adlar ve biçimler (7.5) |
| `files/mapdata/graph_<region>.bin`, `pois_<region>.json`, `dem/N40E049.hgt`, `dem/N40E050.hgt`, `parts_graph_<region>/` | harita verisi (Y7) | yeniden üret (CI `ci/osm_extract.py` + Overpass); WEEM'de aynı yol ve biçim (`MAP.md` 2.12) |
| `files/telemetry-v2.jsonl` (≤3000 satır) | yerel telemetri | hayır (Y13 ile gelir; `MAP.md` 12.11) |
| `app_flutter/TeklifVeriSeti/<gün>/…` | veri seti (Y19) | hayır (S1: dışa aktarma yolu yok); WEEM'de yeni veri seti `files/TeklifVeriSeti/` (7.5) |
| `files/surface-v2/…`, `files/surface-raw-v2/…`, `files/road-calibration-v1/<id>.json` | Y13/Y14 | sonra |

### 7.4 WEEM 2 telefon kuyruğu `files/link/` — WEEM: **yeni** (A8.2, `LINK.md` 3.7–3.8, 3.12)

| Yol | İçerik |
|---|---|
| `files/link/outbox/<seq, 20 hane>.env` | teslim zorunlu zarf (ikili, `LINK.md` 3.6), onay gelince ya da TTL'de silinir; en çok 2 000 |
| `files/link/seq` | sıradaki `seq` bloğunun başı (ondalık metin; 256'lık bloklar) |
| `files/link/seen` | işlenmiş teslim zorunlu `id`'ler (ikili: `id` 16 · tutulma sonu ms i64; en çok 4 096) |

Uygulamanın özel deposunda, ek şifresiz (öteki kayıtlar gibi); yedeğe ve dışa aktarmaya girmez; eşleşme değişince ya da kaldırılınca silinir.

### 7.5 WEEM harita dosyaları `files/map/` ve teklif veri seti `files/TeklifVeriSeti/` — WEEM: **yeni** (Aşama 6; `MAP.md` 14.1, 14.4)

| Yol | İçerik | Ayrıntı |
|---|---|---|
| `files/map/roads/<anahtar>.json` | Y12 yol bilgisi (YolGo'da `roads.*` tercihleri; birebir JSON, sıfırdan öğrenilir, S3) | `MAP.md` 9.10 |
| `files/map/manual-marks.json` | elle kötü noktalar (YolGo `files/surface-v2/manual-marks.json` biçimi; SORU-Q1) | `MAP.md` 9.10 |
| `files/map/weather.json` | hava önbelleği (öneri; YolGo `duzyol_weather` → `report`) | `MAP.md` 10.11 |
| `files/map/logs/` | sürüş günlüğü `YYYY-MM-DD.jsonl`, sürüşler `…_ride_<ms>.jsonl`, gün raporu `…_HHMM.gun.json` (eski `….gun.json` okunur), rota kaydı `…_route_<ms>.json` (`duzyol-route-v1`); adlar ve biçimler 7.3 ile aynı; dışa aktarma `duzyol-journal-v1` | `MAP.md` 12.2–12.6 |
| `files/TeklifVeriSeti/<gün>/…`, ZIP `WEEM_teklif_<klasör>.zip` | teklif veri seti (`:orders`; `yolgo-poll.jsonl` hariç YolGo ile aynı dosyalar; ZIP adı metin onayı M-13-1) | `MAP.md` 13.2, 13.7, 13.11 |

Tam dosya yazımı `.tmp` + `rename`, ekleme dosyaları ekleme kipinde; okunamayan JSON silinmez, `<ad>.damaged-<ms>` olarak saklanır (`MAP.md` 14.4). Android yedeğine (1) ve haftalık şifreli yedeğe (S22) bugün girmez (soru `MAP.md` 17.1 #15).

## 8. Bildirim kanalları ve numaraları

### 8.1 Bugün

| No | Kanal (ad, önem) | Uygulama | Kaynak | Sorun |
|---|---|---|---|---|
| 1 | `shift` "Vardiya kaydı", LOW, ongoing (FGS) | RS | `GpsService.kt:157-175` | — |
| 12 | `delivery_notes` "Müşteri teslimat notu", HIGH, titreşim | RS | `DeliveryReminder.kt:303-308` | metinde müşteri adı ve telefonu |
| 4000 + (tripId.hashCode() & 0xFFF) = **4000–8095** | `cash_tips` "Nakit bahşiş sorusu", HIGH | RS | `CashTipPrompt.kt:22-24` | **çakışma:** aralık 4901, 4902, 5101–5103, 7100, 7301'i kapsar |
| 4901 | `pocket_cash` "Vardiya başı nakit sorusu", HIGH | RS | `PocketCashPrompt.kt:25-28` | aralık içinde |
| 4902 | `scooter_link` "Scooter bağlantısı" | RS | `ScooterLink.kt:31-44` | aralık içinde |
| 4903 | `day_report` "Gün sonu raporu", DEFAULT | **WEEM (yeni)** | `DayReportHook.kt` (A4.2) | çakışma yok (`NotificationIds.DAY_REPORT`) |
| 5101 / 5102 | `auto_start` "Otomatik başlatma", HIGH (ev / scooter) | RS | `AutoStartNotice.kt:17-23` | aralık içinde |
| **5103** | `auto_start` (vardiya) **ve** `screen_reading` "Ekran okuma uyarısı" | RS | `AutoStartNotice.kt:20`, `ScreenReading.kt:38` | **iki sabit aynı numara** |
| 7 | `recording` "Scooter kaydı" (FGS) | NB | `RecordingService.java:64-66` | — |
| 8 | `pairing-attention` "Eşleştirme onayı", HIGH | NB | `PairAttention.java:11,22` | — |
| 7100 (vars.) | `hatirlatici` "Hatırlatıcılar", HIGH; 30 sn zaman aşımı | YG | `MainActivity.kt`, `NotifyPolicy.kt` | — |
| 7301 | `duzyol_weather` "Hava tahmini", DEFAULT, günde bir | YG | `WeatherWorker.kt:129-133` | — |
| — | `yolgo_recording`, `yolgo_calibration`, `yolgo_offer_dataset` (LOW, FGS) | YG | `TelemetryService.kt:144-147` … | — |

### 8.2 WEEM kuralı (A2.4)

Tek tablo `:core` `NotificationIds` (tam tablo `CORE.md` 3.3): her bildirim sabit numara ya da ayrılmış, çakışmayan aralık; test çakışmayı reddeder. Eski numaralar korunur; iki değişiklik: ekran okuma uyarısı **5103 → 5104** (5103 H4 vardiya kesintisinde kalır), nakit bahşiş **4000 + (h & 0xFFF) → 10000 + (h & 0xFFF)** (10000–14095). 7400 (YolGo gün kaydı servisi) kullanılmaz: vardiya servisiyle (1) birleşir. 7401 (YolGo veri seti servisi, `DATASET_FGS`) de kullanılmaz: veri seti `:orders`'ın teklif akışında, servissiz (A6.1; `MAP.md` 13.11). Aşama 6 numaraları ve kanalları `MAP.md` 14.6 (öneri: 7402 `NAV_ONLY_FGS`, kanal `navigation`, SORU-N4). Gün sonu iptali aralık değil açık liste. Kanal kimlikleri aynı kalır (kullanıcının kanal ayarları kanal kimliğine bağlı); yeni kanallar `yeni`.

**A9.1 (yeni):** 7404 `REMOTE_FGS` (kanal `remote` "Uzaktan kullanım", DEFAULT; ekran paylaşımı servisi, ongoing, "Bitir"), 7405 `REMOTE_ASK` (aynı kanal, 60 sn; öteki telefon ekranı görmek istiyor). Uzaktan kullanım kalıcı veri yazmaz (anahtarlar yalnız bellekte; `REMOTE.md` 1.3). A9.2: yeni bildirim yok; oturum işareti erişilebilirlik üst penceresi (`TouchService`, `REMOTE.md` 2.4); dokunuş kaydedilmez.

## 9. Gizlilik sınırı (WEEM'de korunur)

- Müşteri **telefonu** yalnız `courier.db` (`withPhone=true`), bellek, rozet/balon ve bildirim 12'de; JSONL ve ZIP yedekte yok (`PhoneK25Test`). İstisna (S22, 18 Ekim): N3 parolalı şifreli yedekte `courier.db` satırlarıyla birlikte durur.
- Müşteri **adı** `courier.db`, `orders.jsonl` (`record`) ve `latest` gözleminde düz; dışa aktarmada `AnalysisExport`/`Redactor` gizler.
- 2 telefon bağlantısı (A8.2): her ileti uçtan uca şifreli (Noise, `LINK.md` 3.5); anahtar, jeton, QR metni ve düz metin yük loga, `FailureSink`'e ya da çökme kaydına yazılmaz.
- 2 telefon modunda Receiver'a gitmeyecekler: müşteri adı, telefonu, notlar, bildirim 12 içeriği. Gidebilecekler: aşama, saat, bölge (`Z1..Z4`), restoran adı/koordinatı, müşteri koordinatı ve adres metni (`dropoffAddress`; S5, 17 Ekim), rota hedefi (plan Bölüm 4.4).
- Uzaktan kullanım (A9.1, S12): ekran görüntüsü yalnız eşleşmiş telefona, yerel ağda şifreli (AES-256-GCM, link üstünden geçici ECDH) gider; kaydedilmez, diske ve loga yazılmaz. S5 süzgeci görüntüye uygulanamaz (S12 bunu kapsar; `REMOTE.md` 1.1).
