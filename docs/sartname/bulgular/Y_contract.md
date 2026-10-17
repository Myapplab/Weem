# YolGo veri sözleşmesi, içe aktarma, Y12–Y14 başvurusu, test envanteri bulguları
Kaynak: yolgo-test @ 870ab6e (`.git/refs/heads/yolgo-test` = 870ab6ee7268835b418412c5b3d8e3f36f447334); kök /home/user/kurye_nav_yolgo
(Ara kayıt: bölümler tamamlandıkça eklenir.)

## 0. Kimlik ve derleme (özet)
- applicationId **`az.duzyol.app`** (doğrulandı): `ci/build.sh` `APP_ID="az.duzyol.app"` + sed ile `applicationId` değiştirilir (ci/build.sh "Paket adı" bloğu). Eski KuryeNav paketinden ayrı.
- Proje `flutter create --org az.kurye --project-name kurye_nav --platforms android` ile CI'da her seferinde üretilir; overlay kopyalanır: `android_overlay/AndroidManifest.xml` → `android/app/src/main/AndroidManifest.xml`, `res/` → `android/app/src/main/res/`, `*.kt` → `android/app/src/main/kotlin/az/kurye/kurye_nav/`, `*.java` → `.../java/az/kurye/kurye_nav/`, `test/*.kt` → `android/app/src/test/kotlin/az/kurye/kurye_nav/` (ci/build.sh). Kotlin paketi/namespace **`az.kurye.kurye_nav`** (tüm overlay dosyaları `package az.kurye.kurye_nav`); bileşen tam adları ör. `az.kurye.kurye_nav.MainActivity`, uygulama kimliği `az.duzyol.app`.
- Bağımlılık eki: `androidx.work:work-runtime:2.11.2`, `play-services-location:21.3.0`, test `junit:4.13.2`, `org.json:20240303`. compileSdk ≥36, minSdk ≥24 (sed). targetSdk = Flutter varsayılanı (doğrulanmadı).
- İmza: `android_overlay/debug.keystore`, storePassword `android`, keyAlias `androiddebugkey`, keyPassword `android` (signingConfig `kurye`, release+debug). CI SHA-256'yı keytool ile karşılaştırır, farklıysa yayın durur. `useLegacyPackaging = true`. Sürüm `ci/VERSION` = `1.11.0`; versionCode = 2000 + max(6000+GITHUB_RUN_NUMBER, (now−1759276800)/60) (arm64 split).
- Google Maps anahtarı: `GOOGLE_MAPS_API_KEY` ortamından `res/values/google_maps.xml` → `@string/google_maps_key` (biçim `AIza[0-9A-Za-z_-]{35}`), `--dart-define=GOOGLE_MAPS_ENABLED=true|false`.
- WEEM paketi `com.kuryelog.app` ≠ `az.duzyol.app`: Android uygulama verisi (SharedPreferences, app_flutter, files) paketler arası OTOMATİK TAŞINMAZ; tek yol dışa aktarma → içe aktarma (ya da YolGo'nun sağlayıcı/aktivite ile sunması).

## 2. Veri sözleşmesi kalemleri (ara)
| Tür | Ad (tam yazım) | Ayrıntı | Kaynak |
|---|---|---|---|
| izin | INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS, WAKE_LOCK, SYSTEM_ALERT_WINDOW, VIBRATE, ACTIVITY_RECOGNITION, com.google.android.gms.permission.ACTIVITY_RECOGNITION (maxSdk 28) | — | android_overlay/AndroidManifest.xml:5-19 |
| uygulama | `<application android:label="YolGo">` | `android:allowBackup` YOK (varsayılan true; dataExtractionRules/fullBackupContent yok) | AndroidManifest.xml:20-24 |
| meta | `com.google.android.geo.API_KEY` = `@string/google_maps_key` | | :25-26 |
| activity | `.MainActivity` exported=true, singleTop, taskAffinity="" | intent-filter: MAIN/LAUNCHER; VIEW+BROWSABLE şemalar `geo`, `google.navigation`, `waze`, https `waze.com`/`www.waze.com`, https `www.google.com` pathPrefix `/maps`, https `maps.google.com`, https `maps.app.goo.gl`, `yandexnavi`, `yandexmaps`, https `yandex.az|yandex.ru|yandex.com` pathPrefix `/maps`, https `maps.yandex.ru|maps.yandex.com`; SEND `text/plain` | :27-122 |
| activity | `.HomePickActivity` exported=true | action `com.myapplab.yolgo.PICK_HOME`; yalnız çağıran `com.kuryelog.app` (HomePickContract.accepts) | :123-128; HomePickContract.java:8-11 |
| activity | `.HomePickMapActivity` exported=false | ayrı Dart giriş noktası `pickHomeMain`; kanal `kurye_nav/home_pick` (load/attribution/save/cancel) | :129-130; HomePickActivity.kt:30-47 |
| activity | `.HomeShareActivity` exported=true, noHistory, Translucent | action `az.duzyol.app.action.SHARE_HOME`; yalnız `callingPackage == "com.kuryelog.app"`; RESULT_OK + extra `lat`,`lon` (Double) ya da RESULT_CANCELED; kaynak `FlutterSharedPreferences` / `flutter.settings.v1` → JSON `home{lat,lon}` | :132-137; HomeShareActivity.kt:14-35 |
| receiver | `.MotionReceiver` exported=false | ActivityRecognitionResult; WALKING/ON_FOOT güveni → TelemetryService.walkConfidence | :138; MotionReceiver.kt |
| service | `.TelemetryService` exported=false, fgsType=location | kanal `yolgo_recording` "YolGo aktif gün" LOW | :139; TelemetryService.kt:144-147 |
| service | `.RoadCalibrationService` exported=false, fgsType=location | kanal `yolgo_calibration` "Yol kalitesi kalibrasyonu" LOW | :140; RoadCalibrationService.kt:116-119 |
| service | `.DatasetRecordingService` exported=false, fgsType=specialUse | PROPERTY_SPECIAL_USE_FGS_SUBTYPE "User initiated offline courier offer dataset recording during an active shift"; kanal `yolgo_offer_dataset` "Teklif veri seti" LOW | :141-143; DatasetRecordingService.kt:33-41 |
| provider | `.WeatherFeedProvider` authority `az.duzyol.app.weather` exported=false grantUriPermissions=false | grant-uri-permission path `/weather/current`; KuryePlus (com.kuryelog.app) için | :145-148 |
| provider | `.NavFeedProvider` authority `az.duzyol.app.nav` exported=false | grant path `/nav/v1/current`; Rawstats imzasına göre, her sorguda yeniden denetim | :150-153 |
| queries | intent MAIN+APP_BROWSER; PROCESS_TEXT text/plain; package `com.kuryelog.app` | | :159-171 |
| intent | Rawstats'a ev sorma: action `com.kuryelog.app.action.SHARE_HOME`, package `com.kuryelog.app`, requestCode 703; cevap `{lat,lon}` / "none" / "missing" / "busy" | | HomeShareActivity.kt:38-47; MainActivity.kt:"rawstatsHome" |
| bildirim kanalı | `hatirlatici` "Hatırlatıcılar" IMPORTANCE_HIGH, titreşim 0,350,200,350; id Dart'tan (vars. 7100); setTimeoutAfter 30 000 ms (`NotifyPolicy.TIMEOUT_MS`); ön plandayken sistem bildirimi yok (`inAppOnly`) | MainActivity.kt:47,247-256,396-430; NotifyPolicy.kt |
| bildirim kanalı | `duzyol_weather` "Hava tahmini"/"Hava proqnozu" DEFAULT, no **7301**; günde bir (`NotifyPolicy.weatherDue`) | WeatherWorker.kt:129-133 |
| WorkManager | `duzyol_weather_v1` periyodik, ExistingPeriodicWorkPolicy.UPDATE | WeatherWorker.kt:36 |
| MethodChannel | `kurye_nav/native` (MainActivity): setScreenAwake, getInitialLink, setVolumeCapture, mapsStatus, googleRestConfig, openGoogleRoute (Google Maps `travelmode=two-wheeler&dir_action=navigate`), configureWeather, updateWeatherLocation, weatherNow, weatherStatus, saveFile (ACTION_CREATE_DOCUMENT, req 702, yalnız filesDir/cacheDir/app_flutter altı, vars. ad `DuzYol.json`), openLinkSettings, rawstatsHome, openUrl, startCompass, stopCompass, notify, cancelNotify, requestNotifPermission (req 701). Native→Dart: onVolume("up"/"down"), onVolumeDownHold (≥1000 ms), onHeading (≥100 ms ve ≥1.5°; Bakü sapması 40.40,49.87), onLink | MainActivity.kt:96-275,340-505 |
| MethodChannel | `yolgo/telemetry-v2`, `yolgo/courier-feed`, `yolgo/offer-dataset`, `yolgo/road-calibration-v1`, `yolgo/rawstats-signals`, `yolgo/nav-feed`, `kurye_nav/home_pick` | ayrıntı aşağıda | TelemetryBridge.kt:13; CourierFeedBridge.kt:24; DatasetBridge.kt:15; RoadCalibrationBridge.kt:19; RawstatsObserver.kt:136; NavFeedProvider.kt:110; HomePickActivity.kt:34 |
| SharedPreferences (native) | `duzyol_weather`: enabled, az, key, report, fetched, lat, lon, locationAt | WeatherWorker.kt:43; MainActivity.kt:"configureWeather"/"updateWeatherLocation" |
| SharedPreferences (native) | `offer_dataset_v1`: region (String), active (Boolean) | DatasetBridge.kt:26; DatasetRecordingService.kt:18-20 |
| SharedPreferences (native) | `road_calibration_overlay_v1` | RoadCalibrationOverlay.kt:18 |
| SharedPreferences (native) | TelemetryService `STORE` (ad doğrulanacak) | TelemetryService.kt:32 |
| dosya (native) | `files/telemetry-v2.jsonl` (TelemetryJournal, cap 3000 satır, .tmp ile atomik) | TelemetryService.kt:35; TelemetryJournal.kt:21 |
| dosya (native) | `files/road-calibration-v1/<id>.json` (id `[a-zA-Z0-9-]+`), dışa aktarma `files/YolGo-yol-kalibrasyonu.json` | RoadCalibrationStore.kt:13,59,70 |
| dosya (native) | `files/surface-raw-v2/<n>.jsonl`; paylaşım `cache/YolGo-raw-last-10-minutes.jsonl` | RawDiagnostics.kt:16,28,39 |
| dosya (native) | `app_flutter/TeklifVeriSeti/<gün klasörü>/{yolgo-live.jsonl, yolgo-poll.jsonl, yolgo-routes.jsonl, shifts.jsonl, manual.jsonl}`; `rawstats/` alt klasörü (gps.csv, screens.jsonl, online-sessions.jsonl, platforms.jsonl, events.jsonl, errors.jsonl, economics-*.jsonl, scooter*.jsonl, feed-manifest.json); collection-status.json; manifest.json; ZIP `TeklifVeriSeti/YolGo_teklif_<gün>.zip` | DatasetFiles.kt:9-13; DatasetPack.kt:33-34,69-70,95,191-193 |

### Dart SharedPreferences (FlutterSharedPreferences dosyası, her anahtar `flutter.` önekli)
| Anahtar | Tip / içerik | Kaynak |
|---|---|---|
| settings.v1 | String JSON AppSettings: vehicleId 'g3', flatWhPerM 0.0135, climbWhPerM 0.38, climbSafety 1.0, flatKmh 28.5, climbKmh 18.5, walkKmh 4.5, maxGradePct 18, lowBatteryGradePct 10, lowBatteryPct 50, homeReturnPct 30, mainRoadPenalty 1.15, walkRadius 150, home {lat,lon}?, region 'central'\|'wide', googleKey '', orsKey '', cartoKey '', theme 'dark', logging true, volumeKeys true, qualityTolerancePct 10, showQualityLayer false, wwMaxProblemPct 50, wwAskAlways false, lang '', mapStyle 'minimal', googleMapCompatibility false, lightMaxM 5 (+işaret lightMaxMv2/lightMaxMv3), turnPenaltyS 12, speedChoices [20,25,29] (3 değer 5–45), autoStartDay/autoEndDay/followCourierStages hep true, bikewayCostPct 85 (50–100 kıstırılır), showBikewayLayer true, mapDesignV1 true | lib/core/settings.dart:19-58,232-273,275-322,336 |
| shift.v1 / shift.last | String JSON ShiftState (aktif gün / son gün) | lib/tracking/shift_controller.dart:65-66 |
| day.home.v1 | String JSON HomeDayRules | :67 |
| courier.follow.v1 | String JSON CourierStageFollower | :68 |
| rawstats.shiftEnd.handled.v1 / rawstats.shiftStart.handled.v1 | Int ms | :70-71 |
| shift.deleted.days.v2 | StringList gün kimlikleri | :105,170 |
| sidewalk.zones.v1 | String JSON [GeoPoint] | :112,238 |
| lights.v1 | StringList (LightsDb) | :739,798 |
| ride.active.v1 | String `YYYY-MM-DD_ride_<ms>.jsonl` | lib/tracking/ride_logger.dart:80,146 |
| battery.setPct / battery.consumed / battery.setAt | Double / Double / Int ms | lib/tracking/battery_estimator.dart:132 |
| passive.pausedAt.v2 | Int ms | lib/main.dart:69,105 |
| weather.enabled.v1 (true), weather.strip.v1 (true), weather.key.v1, weather.permission.asked.v1 | Bool/Bool/String/Bool | lib/weather/weather.dart:133-159; lib/ui/api_settings.dart:114-116 |
| places.local.v1 | StringList JSON POI düzeltmeleri | lib/data/poi_corrections.dart:40 |
| map.bundled.<region>, map.bundledAt | String sürüm | lib/data/map_data_service.dart:126,153 |
| search.recents.v2 | String JSON | lib/input/place_search.dart:85,160 |
| clipboard.lastHash | Int | lib/input/clipboard_sniffer.dart:32 |
| streets.bikeway.v1 | StringList `wayId\|ad` | lib/routing/bikeways.dart:20 |
| streets.approved, streets.blocked, streets.steep | StringList wayId | lib/routing/street_prefs.dart:32-34 |
| streets.names | StringList `id\|ad` | :35 |
| streets.hours.v1 | StringList ters yön saatleri | :36 |
| streets.blocked.legacy.v1, streets.blocked.migrated.v2 | StringList / Bool (göç) | :37-50 |
| daily.tasks.v1, daily.tasks.closed.v1 | String JSON | lib/tasks/task_controller.dart:21,43 |
| reminders.v1, reminders.last.v1 | String JSON / StringList | lib/reminders/reminders.dart:191,241 |
| roads.ways.v1, roads.ww.v1, roads.ghost.v1, roads.wwseg.v1, roads.rate.v1, roads.names.v1, roads.days.v2, roads.days.damaged.v2 | String JSON (Y12) | lib/roads/road_controller.dart:53-58,94-98 |
| surface.paused.day, surface.raw.debug | String / Bool (Y13) | lib/surface/surface_controller.dart:50,151; lib/ui/surface_screen.dart:287 |
| rawstats.home.lastSuccessAt.v1 | Int ms | lib/core/rawstats_home.dart:17 |

### Dart dosyaları (Android: getApplicationDocumentsDirectory = `app_flutter/`, getApplicationSupportDirectory = `files/`, getTemporaryDirectory = `cache/`)
- `app_flutter/logs/`: `YYYY-MM-DD.jsonl` (gün günlüğü), `YYYY-MM-DD_ride_<ms>.jsonl` (sürüş), `YYYY-MM-DD[_HHMM].gun.json` (gün raporu), `YYYY-MM-DD_route_<ms>.json` (rota kaydı). Satır: `{"t":ms,"dayId":..,"k":"gps"|"env"|"ev",...}`; gps alanları `la`,`lo` (6 ondalık). lib/tracking/log_store.dart:18-19; ride_logger.dart:121-190; shift_controller.dart:297-303; nav_controller.dart:953-956.
- `files/mapdata/`: `graph_<region>.bin`, `pois_<region>.json`, `dem/N40E049.hgt`, `dem/N40E050.hgt`, `parts_graph_<region>/` (Overpass parçaları). lib/data/map_data_service.dart:79-82,130-150,301-350. Gömülü kaynak `assets/map/graph_wide.bin.gz`, `pois_wide.json.gz`, `manifest.json`.
- `files/surface-v2/` (Y13): observations.jsonl, metadata.json, manual-marks.json, decisions.jsonl, history.json, `<k>.unreadable.jsonl`, `history.unreadable.<ms>.json`, dışa `YolGo-surface-diagnostics.json`. lib/surface/surface_store.dart:40,110-116,174,303,403.

### Dışa aktarma / paylaşma (ara bulgu)
- Kayıtlar: `RideLogStore.export` → `cache/exports/<µs>/YolGo_<gün|tum_kayitlar>.json`, biçim `{"format":"duzyol-journal-v1","day":..,"files":[{name,day,kind,content}]}` (yalnız `logs/` dosyaları, ham metin aynen). lib/tracking/log_store.dart:67-87; ride_logger.dart:242-256 (share_plus) ve ride_logs_screen.dart:90 (`saveFile` → ACTION_CREATE_DOCUMENT).
- Gün raporu paylaşma: shift_controller.dart:323-325. Yüzey tanı: surface_screen.dart:293,304. Kalibrasyon: road_calibration_screen.dart:157. Teklif veri seti ZIP: dataset_screen.dart:57.
- Ayarlar (settings.v1: ev, anahtarlar), sokak kararları (streets.*), ışıklar (lights.v1), POI düzeltmeleri (places.local.v1), bisiklet yolu işaretleri, görevler/hatırlatıcılar, Y12 roads.* için dışa aktarma yolu BULUNMADI (devam ediyor).
