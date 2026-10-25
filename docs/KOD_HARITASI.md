# WEEM kod haritası

Kod değiştirmeden önce buradan başla, sonra `rg -n`; dosyayı baştan sona okuma. Dosya adı listesi için `git ls-files '<modül>/*.kt'` doğrudur; bu belge yalnız "nerede ne var" ve "neyi koşmalı" der. Yeni paket ya da yeni kural eklenince ilgili satırı güncelle (tek satır).

## Katmanlar

- **`logic/<modül>/`** — saf Kotlin/JVM, Android'siz: kural, durum makinesi, ayrıştırma, biçim ve **ekrana giden metin**. Paket `com.kuryelog.logic.<modül>`, yol `logic/<modül>/src/main/kotlin/com/kuryelog/logic/<modül>/`. Testler `logic/<modül>/src/test/` (JUnit, saniyeler). Karar veren kod burada olmalı.
- **`<modül>/`** (kökte, Android) — ince katman: servis, sensör, BLE, konum, bildirim, dosya, Compose ekranı. Paket `com.kuryelog.app.<modül>`, yol `<modül>/src/main/kotlin/com/kuryelog/app/<modül>/`. `*Runtime` / `*Host` / `*Hub` = logic nesnesini Android'e bağlayan sınıf.
- **`:app`** — kabuk ve sekme ekranları: `app/src/main/kotlin/com/kuryelog/app/` (harita ekranları `map/`, ortak bileşenler `ui/`). Robolectric testleri `app/src/test/`, `map/src/test/`.
- Modüller birbirini yalnız `logic/core` arayüzleriyle görür (`Ports.kt`: `ShiftPort`, `OrderEvents`, `ScooterBridge`, `NavPort`, `WeatherPort`, `NotifyPort`; mesajlar `OfferSummary`, `TripStages`, `NavMessages`, `ScooterMessages`, `WeatherNow`). İzinli bağımlılıklar `gradle/module-graph.properties` (iki build de denetler). Mimari: `WEEM_PLANI.md` 4.

## Nerede ne var

Kısaltma: L = logic modülü, A = Android modülü, E = `:app` ekranı. Şartname `docs/sartname/<AD>.md` (büyük: `rg -n '^## ' <dosya>` ile bölüm bul).

| İş | Kod | Şartname |
|---|---|---|
| Kabuk, alt menü, Daha fazla, dil, tema, ekran yönü, ilk açılış | L `app`: `Shell.kt`, `Strings.kt` (`Text`), `Setup.kt`, `Palette.kt` · E `WeemApp.kt`, `MainActivity.kt`, `WeemTheme.kt`, `ShellPrefs.kt`, `SetupScreen.kt`, `PermissionsCard.kt` | plan 4.1 |
| Tasarım bileşenleri | E `ui/` (`WeemTokens`, `WeemType`, `WeemTone`, `Bars`, `Buttons`, `Rows`, `Sheets`, `Surfaces`, `Inputs`; `WeemIcons.kt` üretilir: `tools/icons/weem_icons.py`, açma) · A `core` `WeemFonts.kt` · sistem üstü katman rengi L `core` `OverlayPalette.kt` | `docs/TASARIM.md` |
| Vardiya, gün, ev | L `core`: `ShiftMachine`, `ShiftState`, `HomeShift`, `HomeWatchCore`, `HomeRepository`, `ShiftDistance` · A `core`: `ShiftService`, `ShiftHub`, `Shift`, `HomeWatch` · E `ShiftCard.kt`, `HomeScreen.kt` | `CORE.md` |
| Bildirim numaraları | L `core` `NotificationIds.kt` (tek tablo), `NotifyPolicy` · A `core` `Notices.kt` | `CORE.md` |
| Kayıt, yedek, dışa aktarma, gizleme | L `core`: `LogStore`, `LogImporter`, `CourierSchema`, `DailyRecords`, `BackupExport`, `EncryptedBackup`, `AllExport`, `Redactor` · A `core` `Store`, `CourierDb`, `Downloads` · E `RecordsScreen.kt`, `RecordExport.kt`, `EncryptedBackups.kt` | `CORE.md`, `VERI_SOZLESMESI.md` |
| Çökme kaydı, sekme hata sınırı | L `core` `CrashReport`, `CrashFile`, `Guard` · A `core` `CrashLog.kt` · E `Guarded.kt`, `WeemApplication.kt` | `CORE.md` 4 |
| Ekran okuma (Wolt/Bolt), OCR | L `capture`: `CourierParser`, `CourierScreens`, `OfferCapture`, `OfferRules`, `OcrAcceptance`, `ScreenReading`, `CaptureTiming`, `NameMatch`, `OwnLines` · A `capture`: `WatchService`, `NotifService`, `CaptureHost` | `CAPTURE.md` |
| Sipariş akışı, aşamalar, varış, nakit/bahşiş, platform toplamları, rozet | L `orders`: `OrderFlow`, `DeliveryCore`, `OrderStages`, `Readiness`, `VenueArrival`, `CashTips`, `CashPending`, `PocketCash`, `PlatformTotals`, `OnlineSessions`, `OverlayText`, `OrdersScreen`, `OrderSettings` · A `orders`: `Orders`, `OrdersHost`, `DeliveryReminder`, istem pencereleri · E `OrdersTab.kt`, `OrdersBlocks.kt`, `OrdersSettingsScreen.kt`, `OrdersTools.kt` | `ORDERS.md` |
| Teklif veri seti (Y19) | L `orders/dataset/` · E `map/DatasetScreen.kt`, `map/AppDatasetRuntime.kt` | `MAP.md` 13 |
| Ekonomi, teklif puanı, gün raporu, menzil | L `economics`: `EconomicsV2`, `OfferScore`, `DayReport`, `DayReportCard`, `EconomicsLogs`, `OfferRange` · A `economics/Economics.kt` · E `OfferScoresPage.kt`, `DayReportHook.kt` | `ECONOMICS.md` |
| Scooter (Ninebot BLE, panel, yüzen top, kayıt) | L `scooter`: `NinebotSession`, `NinebotCrypto`, `NinebotFrames`, `GattPlan`, `PairingVault`, `ScooterPanel`, `TripMeter`, `FloatRules` · A `scooter`: `BleLink`, `ScooterService`, `DashboardView`, `FloatingPanel`, `FloatPainter`, `LogStore`, `LoggerPreferences` · E `ScooterTab.kt`, `ScooterSettingsScreen.kt` | `SCOOTER.md`; kripto değişince `tools/scooter-reference-parity.sh` |
| Harita ekranı ve yüzeyleri | L `map/ui/` (`MapStyles`, `Basemap`, `HudLayout`, `MapPalette`, `PuckAnimator`, `RouteLayer`, `MapText`) · A `map`: `MapLibreSurface`, `GoogleMapSurface`, `MapSurface`, `Basemap`, `LocationPuck`, `CompassSensor` · E `map/MapTab.kt`, `map/MapScreen.kt`, `map/MapControls.kt`, `map/MapNav.kt`, `map/MapDayUi.kt` | `MAP.md` 6 |
| Harita verisi (graf, yükseklik, yerler), gömülü harita | L `map/graph/` (`RoadGraph`, `MapDataController`, `GraphBuilder`, `MapBuild`, `Overpass`, `Pois`, `Elevation`) · A `map`: `MapData`, `MapPois` · CI `ci/build_map.sh`, `ci/build_tiles.sh`; altlık stili `tools/basemap/make_style.cjs` → `map/src/main/assets/basemap/` | `MAP.md` 2 |
| Rota, navigasyon, adres ve bağlantılar | L `map/route/` (`Router`, `RouteEngine`, `Planner`, `CostModel`), `map/nav/` (`NavController`, `MapMatcher`, `RerouteGate`, `PassiveSession`, `OrderSwitch`), `map/input/` (`DestinationResolver`, `AddressParser`, `PlusCode`, `Geocoder`, `IncomingLink`) · A `map`: `NavRuntime`, `NavLocation`, `NavOnlyService`, `LinkIntake` | `MAP.md` 3, 4, 7, 8 |
| Gün motoru, ışıklar, istatistik, sürüş kayıtları | L `map/day/`, `map/stats/`, `map/logs/` · A `map`: `DayRuntime`, `DayReportWriter`, `RideLogRuntime` · E `map/StatsScreen.kt`, `map/RideLogsScreen.kt` | `MAP.md` 11, 12 |
| Yol kalitesi (Y12), yüzey (Y13), kalibrasyon (Y14) | L `map/roads/`, `map/surface/`, `map/calibration/` · A `map`: `RoadsRuntime`, `SurfaceRuntime`, `SurfaceSensors`, `RoadCalibrationService` · E `map/RoadsScreen.kt`, `map/SurfaceScreens.kt`, `map/RoadCalibrationScreen.kt`, `map/MoreScreens.kt` | `MAP.md` 9, 18 |
| Hava, görevler, hatırlatıcılar, hız/batarya | L `map/weather/`, `map/tasks/`, `map/reminders/`, `map/ride/` · A `map`: `WeatherRuntime`, `TaskRuntime`, `ReminderRuntime`, `BatteryRuntime` · E `map/WeatherUi.kt`, `map/TasksScreen.kt`, `map/ReminderCard.kt` | `MAP.md` 10, 13 |
| Harita ayarları | L `map/settings/` (`MapPrefs`, `MapSettings`, `MapBackup`) · A `map`: `MapPrefsStore`, `MapSettingsRepo` · E `map/MapSettingsSections.kt`, `map/MapApiSettingsScreen.kt` | `MAP.md` 14 |
| 2 telefon modu | L `link`: `LinkMachine`, `PhoneBridge`, `Messenger`, `Pairing`, `Noise`, `SecureHandshake`, `ClockSync` · A `link`: `LinkRuntime`, `LinkService`, `LinkHub`, `LanWatch` · E `TwoPhone.kt`, `TwoPhoneScreen.kt` | `LINK.md` |
| Hırsızlık koruması | L `link`: `TheftGuard`, `TheftMessages`, `TheftText` · A `link`: `TheftRuntime`, `TheftReceiver` · E `TheftUi.kt` | `LINK.md` 5 |
| Uzaktan kullanım | L `remote`: `HostMachine`, `ViewerMachine`, `StreamWire`, `StreamCrypto`, `Touch`, `Video` · A `remote`: `CaptureService`, `TouchService`, `Player`, `StreamNet`, `RemoteHub` · E `RemoteScreen.kt` | `REMOTE.md` |

## Sık değişiklikler

- **Ekran metni:** ilgili modülün metin enum'ına giriş, iki dil birlikte (`tr`, `az`; `Bilingual`): `logic/app` `Text` (`Strings.kt`), `SetupText`; `CoreText`, `CaptureText`, `ScooterUiText`, `LinkText`, `TheftText`, `RemoteText`; harita `MapText`, `NavText`, `DayText`, `RideText`, `PlaceText`, `WeatherUiText`, `SurfaceUiText`, `StatsText.kt` (istatistik, veri seti, görev, hatırlatıcı, sürüş kaydı). Android kaynağında düz metin testle kırılır (`TextCoverageTest`, `CoreTextTest`, `CaptureTextTest`).
- **Tercih anahtarı:** var olanın adı değişmez (`VERI_SOZLESMESI.md`). Yeri: `logic/core/…/KuryelogPrefs.kt` (`kuryelog` dosyası), `logic/map/…/settings/MapPrefs.kt`, `logic/orders/…/OrderSettings.kt`, `scooter/…/LoggerPreferences.kt`, kabuk `app/…/ShellPrefs.kt`.
- **Bildirim:** numara yalnız `NotificationIds.kt` tablosundan (`NotificationIdsTest`, `NotificationAuditTest`).
- **İzin, manifest, gizlilik:** `app/src/main/AndroidManifest.xml` ve modül manifestleri; kurallar kaynak taramasıyla korunur: `PrivacyAuditTest` (`logic/core`), `NoNetworkTest` ve `NoGestureTest` (`logic/capture`), `CrashGuardTest`. Bu testler Android kaynağını da okur: Android dosyası değişince de `logic` testlerini koş.
- **Sürüm:** `gradle.properties` → `weem.versionCode` (yalnız artar), `weem.versionName`.
- **APK:** `apk.yml` elle çalıştırma → `weem-build-N` ön sürümü (release APK, `com.kuryelog.app`; `.dev` yalnız `dev` işaretliyse); `weem-v*` etiketi → Release. Kimlik denetimi `ci/check_identity.sh <apk> <paket>`.

## Hangi test

| Değişen | Komut |
|---|---|
| `logic/<m>` | `tools/test.sh logic:<m>` (tek sınıf: `-t Ad`; saniyeler) |
| Android kaynağı (her modül) | `tools/test.sh logic:core logic:capture logic:app` (kaynak taraması kuralları) + değişen modülün `logic:<m>`'i + `lint:<m>` |
| `:app` ekranı | ek olarak `tools/test.sh app` (Robolectric; ilk koşu dakikalar) |
| `:map` Android | ek olarak `tools/test.sh map` (~3 dk ilk koşu) |
| Bütün `logic` | `tools/test.sh logic` (~2.180 test, bu ortamda ~6 dk): yalnız geniş değişiklikte |
| PR | CI hepsini koşar (`logic` + `test lintDebug assembleDebug assembleRelease`); yerelde yalnız değişenler |
