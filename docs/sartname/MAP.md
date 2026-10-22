# MAP — Aşama 6 şartnamesi (`:map`: Y1–Y12, Y15–Y21, N5)

Durum: A6.1 **bitti** (18–19 Ekim 2026; Claude Opus · xhigh + ultracode, en çok 3 paralel ajan (S13); 11.9–17 ve 0 tek oturumda, alt ajansız). Kullanıcı soruları 17.1'de varsayılanlarıyla; cevaplar plan Bölüm 8'e yazılınca ilgili bölüm güncellenir. Kaynak: `Myapplab/kurye_nav` — `yolgo-test` @ `870ab6e` (YolGo `1.11.0+6006`; Dart paketi `kurye_nav`, uygulama kimliği `az.duzyol.app`, Kotlin paketi `az.kurye.kurye_nav`) ve gerektiğinde `rawstats` @ `6388c04`. Satır numaraları bu commit'te açılıp doğrulanır; ham bulgular (`bulgular/Y_routing.md`, `Y_nav.md`, `Y_tracking.md`, `Y_contract.md`) kaynakla karşılaştırılır, düzeltmeler "(bulgu düzeltmesi)" diye işaretlidir.

Kısaltmalar: **YG** = `yolgo-test` kökü (`lib/…`, `android_overlay/…`, `tool/…`, `ci/…`, `assets/…`); **YT** = `test/` (Dart testleri), **YK** = `android_overlay/test/` (Kotlin testleri); **RS** = `rawstats` `app/src/main/java/com/kuryelog/app/`, **RT** = `rawstats` `app/src/test/java/com/kuryelog/app/`. Davranış metni kodun söylediğidir; "WEEM" satırları bu şartnamenin kararıdır. Kapsam ya da plan kararı gerektiren noktalar karar verilmeden Bölüm 17'ye soru olarak yazılır.

**Kim kullanır:** Bölüm 0.2 görev dizini (A6.2a … A6.11: her görevin bölümü, eski dosyaları, testleri, veri adları, WEEM yeri). Bölümler: 0 özet · 1 kaynak haritası · 2 Y7 harita verisi · 3 Y3 rota motoru · 4 Y8 sokak kararları · 5 Y15 bisiklet yolu, Y16 restoran noktaları, Y21 bölge bilgisi · 6 Y1 harita ekranı, Y2 ikinci görünüm (MapLibre) · 7 Y4 navigasyon · 8 Y5 Wolt/Bolt bağlantıları, Y6 adres bulma · 9 Y12 yol kalitesi öğrenme · 10 Y9 hız ve batarya, N5 menzil, Y10 hava · 11 Y11 trafik ışıkları ve YolGo gün motoru · 12 Y17 istatistik, Y18 sürüş kayıtları · 13 Y19 teklif veri seti, Y20 görevler ve hatırlatıcılar · 14 veri sözleşmesi · 15 WEEM yerleşimi · 16 test eşlemesi (A6.2a/A6.2b) · 17 açık noktalar, riskler, sorular. Plan bağlamı: `WEEM_PLANI.md` 3 (Y kodları), 4.2–4.4 (`:map`, `NavPort`, `WeatherPort`, `NavTarget`, `RouteStatus`, `WeatherNow`), 8 (S1, S3, S4, S6, S9).

<!-- BEGIN sec:0 -->
## 0. Özet: WEEM kuralları ve görev dizini

### 0.1 WEEM kuralları

| Konu | WEEM kuralı | Bölüm |
|---|---|---|
| Kapsam | Y1–Y12, Y15–Y21, N5 WEEM 1.0'da; Y13 (yüzey ve titreşim) ve Y14 (kalibrasyon) sonra — 1.0'da yalnız sınır noktaları (Ses− elle kötü nokta alt kümesi, SORU-Q1) | 1, 3.16, 9.13 |
| Modül | saf mantık `logic/map` (`geo` … `reminders`; yön kuralı 15.1), Android `:map` ince (sensör, dosya, ağ, harita yüzeyi, zamanlayıcılar), ekranlar `:app`; `:map` yalnız `:core`'u görür, öteki modüllerle portlar ve `WeemApplication` dikişleri üstünden; Y19 teklif veri seti `logic/orders` `dataset` + `RouteMeasurePort` | 15 |
| Veri (S1) | YolGo'dan tercih ya da dosya kendiliğinden gelmez; adlar ve biçimler aynı (`map` tercih dosyası, `flutter.` öneksiz; `files/mapdata/`, `files/map/`); YolGo göç kuralları ve işaretleri yazılmaz; tek içe aktarılan YolGo sürüş kayıtları (`duzyol-journal-v1`; plan Bölüm 1, iş A7.1; 12.15) | 14 |
| Harita verisi (Y7) | KNG1 v2 ikili biçimi değişmez; Overpass dört ayna; SRTM yükseklik; gömülü harita CI'da; hazır veri sunucusu zinciri (SORU, 2.15) | 2 |
| Rota motoru (Y3, Y8) | A* ve maliyet modeli birebir (eşit maliyetli sıralar dahil); zaman ve batarya iki ayrı plan; sokak kararları, saatli ters yön, bisiklet yolu ×0,85, Y12 kalite ve ters yön oranı girdileri; işçi coroutine | 3, 4, 5.1, 9.9 |
| Harita ekranı (Y1, Y2; S4) | Google Maps + MapLibre (OSM raster + vektör; SORU-N5); HUD ve dokunma kuralı; uzun basış menüsü (Y13 satırı yok); gün kartı = `ShiftCard` + harita eylemleri (SORU-N7) | 6 |
| Navigasyon (Y4–Y6) | konum tek kaynak `NavLocation` (Fused 500 ms; gün açıkken `:core` `ShiftService` ön planda); bağlantı filtreleri `:app` `MainActivity`'de; ayrıştırma ve Plus Code ağsız; Google Maps'te rota açma yok (S9); yalnız-nav bildirimi 7402 (SORU-N4) | 7, 8 |
| Gün (G1) | gün = vardiya: gün motoru açılış ve kapanışı `ShiftPort.state`'ten (`ShiftFollower`); `dayId` = `yolgo-<startedAtMs>`; takvim günü `LogTime` (telefon saat dilimi; Bakü'de aynı); süreç ölüyken yeni vardiya başladıysa eski gün kapanır | 11.2, 11.14 |
| Sipariş aşamaları | yalnız `:orders`'tan (`OrderEvents.stages` → `CourierStageFollower`, saatli zaman çizelgesi); GPS duruşu aşama değiştirmez; olay verisinde `source: "kuryeplus"` kayıt dizgisi korunur | 11.6, 11.7 |
| Işıklar (Y11) | motor + canlı ölçer; kural madde 14 (≤ 5 m; 2 dk üstü `light?`); gün sonu Onayla/Reddet; `lights.v1`'de son cevap kazanır | 11 |
| Kayıtlar (Y17, Y18) | sürüş günlüğü JSONL, ayrı sürüş dosyaları, gün raporu `.gun.json`, rota kaydı `duzyol-route-v1`, dışa aktarma `duzyol-journal-v1` — biçimler aynı, klasör `files/map/logs/`; YolGo telemetri servisi yok; gün silme yalnız harita verisini siler | 12 |
| Veri seti (Y19) | `:orders`'ta, servissiz ve okumasız; v1 biçimi aynı (`kontrol.py` uyumlu); gün kayıtları `DayLogSnapshot` (R18 anonimleştirmesi) | 13.2–13.11 |
| Görevler, hatırlatıcılar (Y20) | 1000/1200 m tetik, yalnız teslimatta ya da yeniden girişte; "Rota Oluştur" WEEM navigasyonu (S9), görev hedefi siparişe bağlanmaz; kartlar uygulama kabuğunda | 13.12–13.16 |
| Y12 | sıfırdan öğrenir (S3), Ses+/Ses− yakalama dahil (Harita sekmesi öndeyken, SORU-Q4); öğrenme ve sorular yalnız Receiver'da | 9 |
| Batarya, hava, menzil | Y9 tahmini (barometre, DEM) ve canlı scooter `ScooterBridge.live`; hava Open-Meteo, her telefon kendi (S6), `WeatherPort` → R15; N5 görünümü SORU-T1-1 | 10 |
| Metinler | TR/AZ birebir (`Bilingual`, `TextCoverageTest`); YolGo'da AZ'si eksik ya da bozuk metinler iki dilli yazılır; değişiklik önerileri 17.3 | 17.3 |
| Bildirimler (G4) | yalnız `NotificationIds`: 7100–7102, 7201, 7301, (7402); 7400 ve 7401 kullanılmaz; YolGo kökenliler 30 sn ve gün bağlı; WEEM öndeyken yalnız uygulama içi kart | 14.6; CORE.md 3 |
| Gizlilik | ev koordinatı `:core`'da, `:map` kayıtlarına girmez; sırlar (`googleKey`, `orsKey`, `cartoKey`, `weather.key.v1`) hiçbir kayda ve yedeğe yazılmaz; veri setinde adres metni yok, noktalar 3 ondalık | 14.10 |
| 2 telefon | harita, navigasyon, gün motoru, Y11, Y12, Y17, Y18, Y20 Receiver'da; Wolt/Bolt bağlantı yakalama ve Y19 Provider'da; mesajlar `NavTarget`, `RouteStatus`, `TripStages`, `ShiftState`, `BatterySummary` (plan 4.4) | 15.5 |
| Testler | YolGo 564 + 88 testin 356'sı çevrilir (A6.2a 110, A6.2b 246) + Rawstats `LogFeedV3Test` 10; çeviri "derlenir, kırmızı", uygulama görevi yeşil yapar | 16 |
| Açık sorular | 19 kullanıcı sorusu varsayılanlarıyla, koordinatör kararları, metin onayları, riskler | 17 |

### 0.2 Görev dizini

| Görev | İş | Bölüm | Eski kod (YG, ana dosyalar) | Test (çevrilen) | Veri adları | WEEM yeri | Sorular |
|---|---|---|---|---|---|---|---|
| **A6.2a** | Dart testlerini çevir: rota, harita verisi, Y12 (+ Y15/Y16 payı) | 16.1, 16.2 (ayrıntı 2.13, 3.14, 4.6, 5.2, 5.6, 9.11, 10.12) | `test/` 17 dosya | **110** | — | `logic/map/src/test/…` (`graph`, `route`, `settings`, `roads`) + en küçük API iskeleti | SORU 1, Q1, Q2 |
| **A6.2b** | Dart/Kotlin testlerini çevir: ekran, navigasyon, adres, batarya, hava, gün motoru ve ışık, kayıtlar, veri seti, görevler | 16.1, 16.3 (ayrıntı 6.14, 7.15, 8.11, 10.12, 11.16, 12.13, 13.18) | `test/` 43 dosya, `android_overlay/test/` 5 dosya, RS `LogFeedV3Test` | **246** + 10 | — | `logic/map` (`ui`, `nav`, `input`, `ride`, `weather`, `day`, `logs`, `stats`, `tasks`, `reminders`), `logic/orders` `dataset`, `:app`/`:map` Robolectric | — |
| **A6.3** | Y7 harita verisi | 2 (+ 5.3 POI dosyası) | `lib/data/*`, `lib/core/geo.dart`, `tool/build_map.dart`, `ci/osm_extract.py` | 13 | `files/mapdata/*`, `map.bundled.*`, `assets/map/` | `geo`, `net`, `graph`; Android `MapData`, `AndroidHttp` | SORU (2.15) |
| **A6.4** | Y3 rota motoru, Y8 sokak kararları | 3, 4 (+ 5.1, 9.9, 14.2) | `lib/routing/*`, `lib/core/physics.dart` | 63 | `streets.*`, `settings.v1` | `route`, `settings`, `surface` (alt küme) | SORU 1, Q1 |
| **A6.5** | Y1, Y2 harita ekranı | 6 | `lib/ui/` map_view, google_map_surface, map_hud*, puck_style, locate_button, nav_screen, home_picker, setup/settings yapısı; `lib/main.dart`, `theme.dart` | 24 | `mapStyle`, `passive.pausedAt.v2` | `ui`; Android `MapSurface`, `GoogleMapSurface`, `MapLibreSurface`, `HomePickScreen`; `:app` `MapTab` | N5, N6, N7 |
| **A6.6** | Y4 navigasyon, Y5 bağlantılar, Y6 adres | 7, 8 | `lib/nav/*`, `lib/input/*`, `lib/tracking/location_service.dart` …, manifest filtreleri | 75 | `clipboard.lastHash`, `search.recents.v2`, `googleKey`, `orsKey` | `input`, `nav`; Android `NavLocation`, `NavPortImpl`, `LinkIntake`, `ClipboardWatch`, `NavOnlyService` | N1, N2, N4 |
| **A6.7** | Y9 batarya, N5 menzil, Y10 hava | 10 | `speed_options`, `battery_estimator`, `scooter_live`, `weather.dart`, `Weather*.kt` | 29 | `battery.*`, `weather.*`, `duzyol_weather_v1`, 7301 | `ride`, `weather`; Android `BatteryRuntime`, `WeatherRuntime`, `WeatherWork`, `WeatherPortImpl`, `RangePortImpl` | T1-1, T1-2 |
| **A6.8** | Y11 ve gün motoru, Y15 bisiklet, Y16 POI, Y21 bölge | 11, 5 | `shift_engine`, `shift_controller`, `light_tracker`, `stop_semantics`, `courier_stages`; `bikeways`, `bikeway_layer`, `pois`, `poi_corrections`, `poi_editor`, `region_info` | 80 | `shift.v1`, `shift.last`, `courier.follow.v1`, `day.shift.handled.v1`, `lights.v1`, `sidewalk.zones.v1`, `streets.bikeway.v1`, `places.local.v1` | `day`, `route.BikewayMarks`, `graph.PoiCorrections`, `ui.*LayerRules`; Android `DayRuntime` | 11-1, 11-2, N7 |
| **A6.9** | Y17 istatistik, Y18 kayıtlar, Y19 veri seti, Y20 görevler ve hatırlatıcılar | 12, 13 | `stats_screen`, `shift_widgets` (içerik), `day_deletion`, `ride_logger`, `log_store`, `route_recorder`, `ride_logs_screen`; `dataset_*`, `Dataset*.kt`, RS `LogFeedV3*`, `tools/veri_seti/`; `lib/tasks/*`, `lib/reminders/*`, `tasks_screen`, `reminder_widgets` | 48 + 10 | `files/map/logs/*`, `ride.active.v1`, `shift.deleted.days.v2`, `files/TeklifVeriSeti/*`, `daily.tasks.*`, `reminders.*`, 7100–7102, 7201 | `logs`, `stats`, `tasks`, `reminders`, `logic/orders` `dataset`; Android `RideLogRuntime`, `DayReportWriter`, `DayDeletionRuntime`, `TaskRuntime`, `ReminderRuntime`, `RouteMeasurePortImpl`, `:orders` `DatasetRuntime`; `:app` ekranları | 12-1, 12-3/Q5, 13-1, 13-2, 13-3; M-12, M-13 |
| **A6.11** | Y12 yol kalitesi öğrenme | 9 | `lib/roads/*`, `lib/routing/quality.dart`, `roads_screen`, `road_score`, ses tuşu (`MainActivity`) | 24 | `files/map/roads/*`, `manual-marks.json`, `volumeKeys`, `wwMaxProblemPct`, `wwAskAlways` | `roads` (+ `surface` alt küme); Android `RoadsRuntime`, `VolumeKeys`, `RoadFilesAndroid` | Q1–Q7 |
| **A6.10** | (K) eski YolGo ile yan yana sürüş | 17.4 | — | — | — | — | — |

Bağımlılıklar `WEEM_ISLER.md` Aşama 6 tablosundadır; A6.8'e A6.2a eklendi (Y15/Y16 testleri orada, 16.5). YolGo sürüş kayıtlarının içe aktarılması Aşama 7'dedir (A7.1, A6.9'a bağlı; kural 12.15).
<!-- END sec:0 -->

<!-- BEGIN sec:1 -->
## 1. Kaynak haritası

YG @ `870ab6e`: `lib/` **115 Dart dosyası, 24.352 satır** (`find lib -name '*.dart' | xargs wc -l`); `android_overlay/` 33 Kotlin/Java + manifest + `res/` (testlerle 4.896 satır); YT 83 dosya (11.585 satır, 564 test; `flutter test` döngülerle 637), YK 14 dosya (88 `@Test`). Y13 (`lib/surface/`, 1.327 satır) ve Y14 (`lib/calibration/`, 559 satır; `android_overlay/RoadCalibration*`, `SurfaceSignal.kt`, `RawDiagnostics.kt`) WEEM 1.0'da yok (`BEKLEYEN_OZELLIKLER.md`); yalnız Y12/Y3 sınır notu (Bölüm 3, 9). Satır sayıları `wc -l`.

| YG dosya (satır) | Özellik | Bölüm | Görev |
|---|---|---|---|
| `lib/data/` road_graph (577), graph_builder (295), overpass (153), elevation (145), map_data_service (485); `tool/build_map.dart`; `ci/osm_extract.py`, `ci/build.sh` 198–228; `assets/map/manifest.json` | Y7 | 2 | A6.3 |
| `lib/data/` pois (136), poi_corrections (76); `lib/ui/poi_editor.dart` (45) | Y16 | 5 | A6.8 |
| `lib/routing/` router (862), planner (201), route (202), route_worker (163); `lib/core/physics.dart` (126), `geo.dart` (138) | Y3 | 3 | A6.4 |
| `lib/routing/` street_prefs (146), route_hours (31); `lib/ui/road_hours.dart` (66) | Y8 | 4 | A6.4 |
| `lib/routing/bikeways.dart` (67); `lib/ui/bikeway_layer.dart` (148); `graph_builder.dart` `isBikewayTags` | Y15 | 5 | A6.8 |
| `lib/ui/region_info.dart` (134); `assets/baku/` (3 jpg + `credits.json`; CI'da `ci/fetch_photos.py` Wikimedia Commons'tan yeniler) | Y21 | 5 | A6.8 |
| `lib/ui/` map_view (1178), google_map_surface (302), map_hud (111), map_hud_layout (29), puck_style (38), locate_button (66), brand (78), maps_help (45), setup_screen (211), settings_screen (549, yapı), home_picker (107), widgets (581; 150–289 hariç); `lib/core/theme.dart` (190), `app_info.dart` (21); `lib/main.dart` (178); `assets/brand/`, `assets/fonts/` (Inter) | Y1, Y2, G2 seçici | 6 | A6.5 |
| `lib/nav/` nav_controller (987), nav_feed (227), reroute_gate (40), approach_zoom (30), arrival_focus (54), route_line (34), toast (53); `lib/ui/nav_screen.dart` (399); `lib/tracking/` location_service (224), map_matcher (103), nav_only (55), compass (22), heading_filter (10); `android_overlay/NavFeedProvider.kt` (142) | Y4 | 7 | A6.6 |
| `lib/input/` address_parser (367), clipboard_sniffer (275), destination_resolver (128), geocoder (201), place_search (198), places_rest (224), plus_code (87); `lib/ui/api_settings.dart` (162); `android_overlay/AndroidManifest.xml` 27–120, `MainActivity.kt` (bağlantı yöntemleri) | Y5, Y6 | 8 | A6.6 |
| `lib/roads/` road_controller (442), road_knowledge (593), street_scores (183), street_ratio (56), day_contributions (102), quality_prompt (77); `lib/routing/quality.dart` (77); `lib/ui/` roads_screen (700), road_score (150); `MainActivity.kt` `setVolumeCapture`/`onVolume*` | Y12 | 9 | A6.11 |
| `lib/routing/speed_options.dart` (44); `lib/tracking/battery_estimator.dart` (220); `lib/scooter/scooter_live.dart` (232); `lib/ui/scooter_sheet.dart` (59), `widgets.dart` 150–289 | Y9 | 10 | A6.7 |
| `lib/economics/` offer_engine (425), route_costing (149) | N5 | 10 | A6.7 |
| `lib/weather/weather.dart` (174); `lib/ui/` weather_section (69), weather_widget (373); `android_overlay/` WeatherWorker (141), WeatherFeed (42), WeatherFeedProvider (35) | Y10 | 10 | A6.7 |
| `lib/tracking/` shift_engine (680), shift_controller (814), light_tracker (88), stop_semantics (34); `lib/core/day_scope.dart` (9); `lib/economics/courier_stages.dart` (350); `android_overlay/MotionReceiver.kt` (20) | Y11, YolGo gün motoru | 11 | A6.8 |
| `lib/ui/` stats_screen (294), shift_widgets (316); `lib/tracking/day_deletion.dart` (70) | Y17 | 12 | A6.9 |
| `lib/tracking/` ride_logger (261), log_store (88), native_telemetry (139); `lib/nav/route_recorder.dart` (143); `lib/ui/ride_logs_screen.dart` (199); `android_overlay/` TelemetryJournal (106), TelemetryService (354), TelemetryBridge (52) | Y18 | 12 | A6.9 |
| `lib/economics/` dataset_runtime (119), dataset_days (29); `lib/ui/` dataset_screen (113), dataset_manual (79); `android_overlay/Dataset*.kt` (8 dosya, 833); `tools/veri_seti/` | Y19 | 13 | A6.9 |
| `lib/tasks/` daily_task (66), task_controller (122); `lib/reminders/reminders.dart` (351); `lib/ui/` tasks_screen (175), reminder_widgets (230) | Y20 | 13 | A6.9 |
| `lib/core/settings.dart` (361) | `settings.v1` (tüm Y) | 14 | hepsi |
| `lib/core/i18n.dart` (89) | G3 (`:app`, A1.3 bitti) | 14 | — |
| `lib/tracking/` home_day (163), rawstats_shift (64); `lib/core/rawstats_home.dart` (102); `android_overlay/` HomeShare/HomePick*, NotifyPolicy (14) | G1, G2, G4 (`:core`, Aşama 2 bitti) | başvuru: 6, 11 | — |
| `lib/tracking/home_departure.dart` (34) | ölü kod (hiçbir yerde kullanılmıyor, testi yok) | — | çevrilmez |
| `lib/economics/` offer_feed (97), rawstats_signals (108); `android_overlay/` CourierFeedBridge (93), CourierFeedSigner (30), RawstatsObserver (159) | iki uygulama sözleşmesi (N1) | 17 | çevrilmez / uyarlanır `:link` (A8) |
| `lib/surface/*`, `lib/calibration/*`, `lib/ui/surface_screen.dart` (320), `road_calibration_screen.dart` (474); `tool/benchmark_surface.dart` | Y13, Y14 | sınır notu 3, 9 | sonra |
| `ci/` emulator_smoke.py, gradle_test.py, UiDump.java, check_feed_signer.py, VERSION; `tools/jvm-overlay/test.sh` | Flutter/CI'ya özgü | — | çevrilmez |

Testlerin bölüm ve görev eşlemesi: her bölümün "Testler" alt bölümü; toplu liste Bölüm 16.
<!-- END sec:1 -->

<!-- BEGIN sec:2 -->
## 2. Y7 — Harita verisi (A6.3)

### 2.1 Kaynak ve kapsam

| Dosya (YG) | Satır | İçerik |
|---|---|---|
| `lib/core/geo.dart` | 1–138 | `haversine` 10–17, `bearing` 20–26, `angleDiff` 29–32, `GeoPoint` 34–69, `BBox` 71–100 (`split` 88–99), `Baku` 102–110, `LocalProjection` 113–125, `pointToSegment` 128–138 |
| `lib/data/overpass.dart` | 1–153 | `OsmRaw` 12–53, `OsmWay` 55–60, `overpassQuery` 64–75, `overpassMirrors` 77–82, `overpassProblem` 86–96, `OverpassFetcher` 100–153 |
| `lib/data/graph_builder.dart` | 1–295 | `classifyHighway` 16–33, `isBikewayTags` 37–45, `wayFlagsFromTags` 47–82, `buildRoadGraph` 85–274, `isParkAreaTags` 277–278, `_Polygon` 281–295 |
| `lib/data/road_graph.dart` | 1–577 | `RoadClass` 7–23, `WayFlag` 26–43, `GraphFeature` 47–52, `isMainRoad` 54–55, `isFootClass` 58–59, `EdgeHit` 64–80, `RoadGraph` 84–577 (ızgara 230–293, `nearestEdge` 298–330, ikili dosya 442–576) |
| `lib/data/elevation.dart` | 1–145 | `HgtTile` 9–56, `ElevationModel` 59–145 |
| `lib/data/map_data_service.dart` | 1–485 | `MapDataPhase/State` 21–66, `MapDataController` 71–282, `_buildEntry` 296–366, `writeFileAtomically` 370–381, `_loadPrebuilt` 392–426, `downloadPois` 430–468 |
| `lib/data/pois.dart` | 86–136 | POI sorgusu ve ayrıştırma (dosya biçimi burada; davranış Bölüm 5.3) |
| `lib/ui/settings_screen.dart` | 446–515 | Harita verisi bölümü (ekran yapısı ajan N, Bölüm 6; metinler burada) |
| `lib/ui/setup_screen.dart` | 56–132, 134–157 | ilk kurulum adımı 2 ve açılış ekranı |
| `tool/build_map.dart` · `ci/osm_extract.py` · `ci/build.sh` | 1–120 · 1–71 · 202–231 | CI gömülü harita |
| `assets/map/manifest.json` | — | repoda yalnız yer tutucu `{"version":"","regions":{}}` (28 bayt); graf/DEM/POI CI'da üretilir |

Bulgu `Y_routing.md` §Y7 satır satır doğrulandı; içerik doğru. Bulgu düzeltmesi: satır numaraları eski (doğrusu yukarıdaki tablo; ör. `buildRoadGraph` 85–274, ikili dosya 442–576, `_installBundled` 116–157, `_buildEntry` 296–366, `_loadPrebuilt` 392–426, `downloadPois` 430–468, `features = 3` graph_builder.dart:272, `ci/build.sh` 202–231); graf düğümü "≥ 2 yolda" değil "≥ 2 kez kullanılan" düğüm (aynı yolda iki kez geçen, ör. kapalı yolun başı/sonu, de sayılır) ya da yol ucu; `contains` sınırı geometri kutusu değil ızgara kapsamıdır (kuzey/doğu kenarında 100 m paya ek en çok bir hücre ≈ 55 m). Ekler: `overpassProblem` 64 baytlık baş + 1.500 baytlık kuyruğa bakar; `regionBox` ve `isInAbsheron` lib'de kullanılmıyor (ölü); POI türlerinde `t` (market) var, `pois.dart:11` yorumunda yok (Bölüm 5.3); hazır veri sunucusu manifesti bu ortamdan 404.

### 2.2 Coğrafya yardımcıları (`geo.dart`; WEEM `logic/map` `geo`)

- `haversine`: R = 6 371 000 m, `2R·asin(min(1, √a))`. `bearing`: kuzeyden saat yönünde 0–360. `angleDiff`: 0–180.
- `LocalProjection(refLat, refLon)`: `x = (lon − refLon)·R·π/180·cos(refLat)`, `y = (lat − refLat)·R·π/180` (metre). `pointToSegment(p, a, b)` → `(dist, t∈[0,1])`; sıfır boylu parçada `t = 0`.
- `GeoPoint`: `toJson {lat, lon}`, `fromJson` yalnız sayısal `lat`/`lon`; `toString` 6 ondalık `lat,lon`. `isInAzerbaijan` = 38.3 ≤ lat ≤ 42.0 ve 44.7 ≤ lon ≤ 51.0 (Y5/Y6 kullanır); `isInAbsheron` (40.0–40.8 / 49.3–50.6) **hiçbir yerde kullanılmıyor**.
- `BBox(south, west, north, east)`: `contains` kapalı aralık; `overpass` = `"$south,$west,$north,$east"`; `split(maxDeg)`: `ny = clamp(ceil((n−s)/maxDeg), 1, 50)`, `nx` aynı; sıra güneyden kuzeye satır, her satırda batıdan doğuya.
- `Baku.center = (40.4093, 49.8671)`; `Baku.centralBox = BBox(40.335, 49.760, 40.445, 49.960)`; `Baku.wideBox = BBox(40.280, 49.580, 40.650, 50.250)` (`ci/osm_extract.py:7` ile aynı). `wideBox.split(0.055)` = 7 × 13 = **91 karo**; `split(0.1)` = 4 × 7 = 28.

### 2.3 Ham OSM (`OsmRaw`)

- `addNode(id, lat, lon)`: aynı id ikinci kez gelirse yok sayılır (ilk kazanır); iç indeks ekleme sırası. `addWay`: aynı way id ikinci kez yok sayılır.
- `addOverpassJson`: `elements` dizisi; `node` → `id`, `lat`, `lon` sayı; `way` → `nodes` (≥ 2 değilse atlanır), `tags` her anahtar/değer dizgiye çevrilir (`'$k' = '$v'`). Karo sınırında tekrar eden öğeler bu yüzden tek kalır. Bilinmeyen tür atlanır.
- WEEM: `graph.OsmRaw` (aynı sıra kuralı; düğüm iç indeksi graf düğüm sırasını belirler).

### 2.4 Graf kurma (`buildRoadGraph(raw, elevation)`; saf, A6.3)

Sabitler: `maxSpacing = 25 m` (sıklaştırma), `minGradeRun = 20 m` (eğim paydası alt sınırı).

1. **Ayırma** (`way` sırasıyla, graph_builder.dart:94–117):
   - Park alanı: `isParkAreaTags` (`leisure=park|garden` ya da `landuse=recreation_ground`) **ve** `highway` yok **ve** ≥ 4 düğüm **ve** ilk = son düğüm **ve** bütün düğümler bilinir → park çokgeni (kenar değil).
   - `classifyHighway(highway)` null → atlanır. Tablo: `trunk|trunk_link`→trunk, `primary(_link)`→primary, `secondary(_link)`→secondary, `tertiary(_link)`→tertiary, `unclassified`, `residential`, `living_street`→livingStreet, `service`, `road`, `track`, `cycleway`, `path`, `footway`, `pedestrian`, `steps`.
   - `service` ∈ {`driveway`, `parking_aisle`, `drive-through`, `emergency_access`} → atlanır. Düğümlerinden biri bilinmiyorsa → atlanır.
   - `pedestrian` + `area=yes` → yaya bölgesi çokgeni (`zoneLat/zoneLon` + `zoneStart`), kenar değil.
   - Kalan yol: `(way, sınıf, wayFlagsFromTags)`.
2. **Park yolu** (madde 10, :118–130): park çokgeni varsa, yaya sınıfı (`path|footway|pedestrian`) ve `bicycle != no` yolun düğümlerinden `içeride × 2 ≥ düğüm sayısı` → `WayFlag.park`. İçerilik: sınır kutusu ön elemesi + ışın atma (`_Polygon.contains`).
3. **Bayraklar** (`wayFlagsFromTags`, :47–82):

   | Bayrak | Koşul |
   |---|---|
   | `onewayFwd` | `oneway` ∈ {yes, true, 1}; ya da `oneway != no` ve `junction` ∈ {roundabout, circular} |
   | `onewayBwd` | `oneway` ∈ {-1, reverse} |
   | `contraflow` | `oneway:bicycle=no` ya da `cycleway*` ile başlayan herhangi bir anahtarın değeri `opposite` ile başlıyor |
   | `unpaved` | `surface` ∈ {unpaved, gravel, fine_gravel, dirt, ground, sand, grass, mud, earth, pebblestone, rock, woodchips} ya da (`track` ve `surface` yok) |
   | `rough` | `surface` ∈ {sett, cobblestone, unhewn_cobblestone} |
   | `bikeOk` | `bicycle` ∈ {yes, designated, permissive} |
   | `footNo` | `foot` ∈ {no, private} |
   | `restricted` | `access` ∈ {private, no, customers, delivery, destination, permit} ya da `vehicle`/`bicycle` ∈ {private, no, customers, permit} |
   | `sidewalk` | `footway` ∈ {sidewalk, crossing} |
   | `bikeway` | `isBikewayTags` (Bölüm 5.1) |
   | `area` (64) | **hiç yazılmaz** (tanımlı, kullanılmıyor) |

4. **Düğümler** (:132–152): yollarda kullanım sayacı (255'te doyar); `kullanım ≥ 2` ya da yol ucu → graf düğümü. Graf düğüm numarası = ham düğüm iç indeks sırası.
5. **Kenarlar** (:154–244): yol, ilk düğümden başlayarak her graf düğümünde bölünür (`la.length ≥ 2` ise kenar). Kapalı yolda iç kavşak yoksa `from == to` döngü kenarı oluşur. `emitEdge`:
   - Sıklaştırma: ardışık iki nokta arası `d = haversine`, `steps = ceil(d/25)`; `s = 1..steps−1` için doğrusal enlem/boylam ara noktaları.
   - Yükseklik: her noktada `elevation(lat, lon)`; iç noktalarda (ilk ve son hariç) ham değerlerin 3'lü ortalaması.
   - Her parça: `len += d`; `dh = ele[k] − ele[k−1]`; `grade = dh / max(d, 20)`; `dh > 0` → `upLenF += d`, `climbF += dh`, `maxGradeF = max(…, grade)`; `dh < 0` → `upLenB += d`, `climbB += −dh`, `maxGradeB = max(…, −grade)`; `dh == 0` hiçbirine.
   - Way tablosu yol başına bir kez: `wayOsmId`, `wayClass` (RoadClass indeksi), `wayFlags`, `wayName`. Ad = `name ?? name:az ?? name:en ?? ''` (yalnız null'a bakar; boş `name` → adsız); adlar birebir eşitlikle tekilleşir, yoksa −1.
6. `features = bikeways | parks = 3` (her zaman). Dönen graf kurulurken komşuluk ve ızgara hesaplanır (2.6).

### 2.5 KNG1 ikili biçimi (`serialize`/`deserialize`, road_graph.dart:442–576) — DEĞİŞMEZ

- **Bayt sırası: little-endian** (Dart `TypedData` yerel sıra; Android ARM ve x86 little-endian). Kotlin'de `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)` zorunlu (Java varsayılanı big-endian).
- Başlık 10 × Int32 = 40 bayt:

  | Alan | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
  |---|---|---|---|---|---|---|---|---|---|---|
  | Değer | sihir `0x4B4E4731` ('KNG1') | sürüm `2` | N düğüm | E kenar | G geometri noktası | W way | `zoneStart.length` (Zs = bölge + 1) | Z bölge noktası | ad baytı | `features` (E13 öncesi 0) |

- Sonra diziler bu sırayla; **her dizi bitince 8 bayta sıfır dolgu** (dosya başından ölçülür): `nodeLat f64[N]`, `nodeLon f64[N]`, `edgeFrom i32[E]`, `edgeTo i32[E]`, `edgeWay i32[E]`, `edgeGeomStart i32[E]`, `edgeGeomCount i32[E]`, `edgeLen f32[E]`, `upLenF f32[E]`, `climbF f32[E]`, `upLenB f32[E]`, `climbB f32[E]`, `maxGradeF f32[E]`, `maxGradeB f32[E]`, `geomLat f64[G]`, `geomLon f64[G]`, `geomEle f32[G]`, `wayOsmId i64[W]`, `wayClass u8[W]`, `wayFlags u16[W]`, `wayName i32[W]` (−1 = adsız), `zoneStart i32[Zs]`, `zoneLat f64[Z]`, `zoneLon f64[Z]`; en sonda adlar UTF-8, `U+0000` ile birleşik, **dolgusuz**. Ad baytı 0 → boş ad listesi.
- Okuma: sihir ya da sürüm farklı → `FormatException('Harita dosyası eski sürüm')`; denetleyici bunu `'Harita dosyası okunamadı, yeniden indir (…)'` diye gösterir (2.9). `features` bitleri: `bikeways = 1`, `parks = 2`; eski dosya (0) okunur, `hasBikewayData`/`hasParkData` false olur ve ekran "güncelle" der (5.1, 2.9). Biçim değişmedikçe sürüm artırılmaz; yeni alan eklemek = sürüm 3 (bu şartnamede yok).
- **RoadClass (u8, sıra = değer):** trunk 0, primary 1, secondary 2, tertiary 3, unclassified 4, residential 5, livingStreet 6, service 7, road 8, track 9, cycleway 10, path 11, footway 12, pedestrian 13, steps 14. `isMainRoad` = trunk|primary|secondary; `isFootClass` = path|footway|pedestrian.
- **WayFlag (u16):** onewayFwd 1, onewayBwd 2, contraflow 4, unpaved 8, rough 16, bikeOk 32, area 64, footNo 128, restricted 256, sidewalk 512, bikeway 1024, park 2048.
- WEEM: `graph.RoadGraph` aynı biçimi yazar ve okur (YolGo dosyası ve WEEM dosyası birbirinin aynısı; CI'da üretilen gömülü dosya da). Diziler `DoubleArray/IntArray/FloatArray/LongArray`; `u8`/`u16` işaretsiz okunur (`and 0xFF`, `and 0xFFFF`). `f32` alanları hesapta `toDouble()` ile kullanılır (Dart ile aynı değer).

### 2.6 Bellek içi sorgular (`RoadGraph`)

- **Komşuluk** (CSR, :183–208): `adjStart[N+1]`, `adjEdge[2E]`, `adjDir[2E]` (0: düğüm kenarın `from` ucu, kenar çizim yönünde çıkar; 1: `to` ucu). Doldurma sırası kenar sırası; `degree(n)` = komşu kayıt sayısı (döngü kenarı 2 sayılır).
- **Izgara** (:230–293): hücre `0.0005°` enlem × `0.00065°` boylam (~55 m); köken geometri min enlem/boylam; satır/sütun = `floor(Δ/hücre) + 1`; her kenar, geometri parçalarının hücre aralığındaki bütün hücrelere (dikdörtgen, ardışık tekrarsız) yazılır. Boş grafta köken 0.
- `nearestEdge(lat, lon, maxDist = 60, accept?, heading?, headingTol = 50)`: arama yarıçapı `reach = ceil(maxDist/50) + 1` hücre; hücreler satır (r0−reach → r0+reach) ve sütun sırasıyla, kenarlar hücre listesi sırasıyla taranır, her kenar sorgu başına bir kez (damga). `_projectOnEdge`: yerel izdüşümde her parçaya uzaklık; `heading` verilmişse parça yönü ile fark ≤ tol **ya da** ters yönle fark ≤ tol olmayan parçalar atlanır; en küçük uzaklık (eşitlikte ilk bulunan), `fraction` = izdüşüme kadar yerel uzunluk / toplam (0–1), izdüşüm noktası, parça yönü. `dist ≤ maxDist` olmalı.
- `nearestEdges(…, maxDist = 80, limit = 4)`: art arda `nearestEdge`, bulunanları dışlayarak.
- `contains(lat, lon, marginM = 100)`: ızgara sınırı + pay; enlemde `margin/111320`, boylamda `margin/85000` derece (ızgara üst sınırı en büyük geometri değerinden bir hücreye kadar fazla olabilir). Geometri yoksa false.
- `edgesWithin(s, w, n, e)`: hücre aralığındaki tekil kenarlar. `geometry(e, f0, f1, reverse)`: haversine kümülatif; `f0..f1` (sıralanır) aralığının iki ucu ara değerle, aradaki ham noktalar dahil; `reverse` → ters sıra; nokta yüksekliği de ara değerle.
- `inPedestrianZone(lat, lon)`: bölge çokgenlerinde ışın atma.
- `wayIndexOf(osmId)` (sözlük), `edgesOfOsmWay`, `nameOfOsmWay`, `classOfOsmWay` (doğrusal tarama, O(E)/O(W)); `nameOf(e)`, `classOf(e)`, `flagsOf(e)`, `osmIdOf(e)`, `isBikeway(e)`.
- WEEM: aynı sorgular `graph.RoadGraph`'ta; doğrusal taramalar yerine way → kenar listesi dizini kurulabilir (sonuç ve sıra aynı kalmak şartıyla).

### 2.7 Yükseklik (SRTM HGT; `elevation.dart`)

- `HgtTile(lat0, lon0, bytes)`: boyut 3601² × 2 (1") ya da 1201² × 2 (3"), başka boyut → `FormatException('Beklenmeyen HGT boyutu: …')`. Değerler **big-endian** int16, satır 0 = kuzey kenarı; −32768 = boşluk. Ad `N40E049` biçimi (`nameFor`: enlem 2, boylam 3 hane).
- `sample(lat, lon)`: `fy = (lat0 + 1 − lat)·(size−1)`, `fx = (lon − lon0)·(size−1)`; aralık dışı → null; `r = clamp(floor(fy), 0, size−2)`, `c` aynı; dört köşe; biri boşluksa geçerli köşelerin **ağırlıksız ortalaması** (hepsi boşsa null); değilse çift doğrusal.
- `ElevationModel.at`: karolar sırayla, kapalı kutu içinde ve örnek null değilse o; hiçbiri yoksa **0** (düz).
- `ensure(box, dir, client, log)`: kutunun tam derece karoları (Bakü: N40E049, N40E050); dosya yoksa sırayla `https://s3.amazonaws.com/elevation-tiles-prod/skadi/{DIR}/{NAME}.hgt.gz`, `https://elevation-tiles-prod.s3.amazonaws.com/skadi/{DIR}/{NAME}.hgt.gz` (`DIR` = adın ilk 3 harfi), 3 dk zaman aşımı, 200 + gzip açma; anahtar gerekmez. Günlük: `'Yükseklik: $name indiriliyor'`, başarısızsa `'Yükseklik: $name indirilemedi, düz kabul edilecek'`. Okunamayan dosya silinir (sonraki sefer yeniden iner). `loadLocal`: yalnız diskteki karolar (ağ yok).

### 2.8 Overpass (`overpass.dart`)

- Yol sorgusu (aynen; `{bbox}` = `BBox.overpass`):
  ```
  [out:json][timeout:90];
  (
    way["highway"~"^(trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service|road|track|cycleway|path|footway|pedestrian|steps)$"]["area"!="yes"]["access"!~"^(private|no)$"]["service"!~"^(parking_aisle|driveway)$"]({bbox});
    way["highway"="pedestrian"]["area"="yes"]({bbox});
    way["leisure"~"^(park|garden)$"]({bbox});
    way["landuse"="recreation_ground"]({bbox});
  );
  out body qt;
  >;
  out skel qt;
  ```
- Aynalar (sırayla): `https://overpass-api.de/api/interpreter`, `https://overpass.private.coffee/api/interpreter`, `https://maps.mail.ru/osm/tools/overpass/api/interpreter`, `https://overpass.kumi.systems/api/interpreter`.
- `OverpassFetcher.fetch(tile, log, query)`: 3 tur; her turda 4 ayna, son başarılı aynadan başlayarak (`_preferred`); POST form `data=<sorgu>`, başlık `User-Agent: YolGo/1.11.0 (personal courier navigation)` (WEEM: `WEEM/<sürüm> (personal courier navigation)`), 120 sn zaman aşımı. 200 + `overpassProblem == null` → ham baytlar. Turlar arası `15·(tur+1)` sn (15, 30). Hepsi düşerse `Exception('Yol verisi indirilemedi (<son hata>)')`.
- `overpassProblem(body)`: boş → `'boş cevap'`; ilk 64 bayt (bozuk UTF-8 serbest) baştaki boşluk atılınca `{` ile başlamıyor → `'JSON değil'`; son 1.500 baytta `"remark"\s*:\s*"([^"]*)` ve değeri `error` içeriyor → değerin kendisi; yoksa null.
- Günlük metinleri (TR; AZ `trMsg` sözlüğüyle, `i18n.dart:30–89`): `'<host> eksik veri verdi, başka sunucu deneniyor'`, `'<host> meşgul (<kod>), başka sunucu deneniyor'`, `'<host> yanıt vermedi, başka sunucu deneniyor'`, `'<host> bağlantı hatası, başka sunucu deneniyor'`, `'Tüm sunucular meşgul, <n> sn sonra tekrar'`; son hata `'<host>: yanıt yok'` / `'<host>: HTTP <kod>'`.

### 2.9 Harita durumu, gömülü harita ve güncelleme (`MapDataController`)

- Fazlar: `checking` (açılış), `unpacking` (gömülü harita açılıyor), `missing`, `building` (indirme), `ready`, `error`. `ready` = `ready` fazı **ve** graf var. `updateFailed(err, graph, elevation, pois)` = `error` fazı + eski veri bellekte (navigasyon sürer). Ekran: graf varsa harita/nav ekranı; yoksa `checking/unpacking` → açılış ("Bakü haritası hazırlanıyor…" / "Bakı xəritəsi hazırlanır…"), değilse kurulum ekranı (`main.dart:170–175`).
- Yollar: `<filesDir>/mapdata/graph_<region>.bin`, `mapdata/dem/N40E049.hgt`, `mapdata/dem/N40E050.hgt`, `mapdata/pois_<region>.json`, karo parçaları `mapdata/parts_graph_<region>/<s>_<w>_<n>_<e>.json.gz`. `region` = `settings.v1.region` (`'central'` varsayılan | `'wide'`): **yalnız dosya adını** değiştirir; indirilen ve gömülü graf her zaman `wideBox`.
- `load()` (açılışta ve bölge değişince; **ağ açmaz**): `mapdata/` oluşturulur → `_installBundled` → graf dosyası yoksa `missing`; varsa arka planda okunur, `ElevationModel.loadLocal(wideBox)`, POI dosyası (bozuksa null) → `ready` (`builtAt` = dosya değişiklik zamanı, `sizeBytes`). Okuma hatası → `missing` + `'Harita dosyası okunamadı, yeniden indir (<hata>)'`.
- `_installBundled(region)`: `assets/map/manifest.json` okunamazsa ya da `version` boşsa geçer. Tercih `map.bundled.<region>` == version ve graf dosyası varsa geçer. Değilse faz `unpacking` (ilerleme 0.3, `'Gömülü harita açılıyor'`): `assets/map/graph_<src>.bin.gz` (`src` = manifest `regions` listesinde bölge varsa bölge, yoksa `wide`) → graf dosyası; `N40E049.hgt.gz`, `N40E050.hgt.gz` → `dem/` (her biri ayrı ayrı, hata yutulur); `pois_wide.json.gz` → `pois_<region>.json` (hata yutulur); gzip açma arka planda; sonra `map.bundled.<region>` ve `map.bundledAt` = version. Graf asset'i yoksa sessizce geçer (kurulum ekranı). **Not:** uygulama yeni gömülü haritayla güncellenince kullanıcının elle indirdiği (daha yeni olabilecek) dosyanın üzerine yazılır.
- `download()` ("İndir / güncelle"; yalnız elle — Ayarlar → İlave ayarlar → Harita verisi ve kurulum ekranı; kendiliğinden/periyodik güncelleme **yok**): `building` sürerken ikinci çağrı yok sayılır. Eski graf/DEM/POI bellekte kalır. Arka plan işi ilerleme yollar: `0.01 'Başlıyor…'` → `0.02 'Yükseklik verisi (SRTM)'` (`ensure`, günlükler 0.05'te) → hazır veri sunucusu (`0.15 + 0.65·v`; `'Hazır yol verisi aranıyor'`, `'Yol verisi i/n (hazır veri sunucusu)'`) → olmazsa Overpass karoları (`wideBox.split(0.055)`, `0.15 + 0.65·i/91`, `'Yol verisi i/91'`, günlük `'… · <mesaj>'`) → `0.82 'Yol ağı kuruluyor (<n> yol)'` → `buildRoadGraph`; kenar 0 → `'Bu bölgede yol bulunamadı'` → `0.95 'Kaydediliyor'` (atomik yazım) → parça klasörü silinir → `1 'Hazır'`. Sonra `load()`; başarılıysa POI'ler arka planda tazelenir (`_fetchPoisLater`, hata sessiz).
  - Hazır veri sunucusu: `https://github.com/mehemmedsh/kurye_nav_data/releases/latest/download` + `manifest.json` (20 sn); `format == 1`, `regions` sözlüğünde `box` = [s, w, n, e] `wideBox` ile 1e-6 içinde eşit olan bölgenin `parts` listesi; her parça 90 sn, gzip sihri (1F 8B) varsa açılır, Overpass JSON olarak eklenir; bir parça 200 değilse ya da istisna → Overpass'a düşülür. (Bu ortamdan `manifest.json` 404 döndü; sunucu yaşamıyor olabilir — doğrulanmadı.)
  - Karo parçaları: varsa gzip açılıp kullanılır (bozuksa silinir), yoksa indirilir ve gzipli saklanır (yarıda kalırsa "kaldığı yerden"); indirmeden önce 2 günden eski parçalar silinir.
  - Hata: `'Exception: '` öneki atılır; parça sayısı `0 < kaydedilen < 91` ise ek satır `'\n<k>/91 parça kaydedildi. Tekrar "İndir"e bas, kaldığı yerden devam eder.'`. `load()` sonrası eski harita hazırsa durum `ready` + `error = 'Güncelleme başarısız: <hata>'`; değilse `error` fazı.
- `writeFileAtomically(target, bytes)`: `<target>.tmp`'ye yaz + `flush`, sonra `rename`; hata olursa `.tmp` silinir ve hata yeniden atılır (hedef dokunulmaz).
- `deleteAndReload()`: `mapdata/` klasörünü siler, faz `missing` (adına rağmen yeniden yüklemez).
- Ekran metinleri (TR / AZ): "Harita verisi" / "Xəritə məlumatları"; hazırsa `'<MB:1 ondalık> MB · <E> yol parçası'` / `'… yol hissəsi'`, değilse "Hazır değil" / "Hazır deyil"; alt satır `'Güncelleme: GG.AA.YYYY · yükseklik var|yok'` (`'Yenilənib: … · hündürlük var|yoxdur'`), tarih yoksa `'Yükseklik: var|yok'`; park bilgisi yoksa `' · park yolları yok, güncelle'` / `' · park yolları yoxdur, yenilə'`; bölge seçici "Merkez Bakü / Geniş Bakü" ("Mərkəzi Bakı / Böyük Bakı") + ⓘ "Bölgeler neyi kapsıyor?" (Bölüm 5.4); düğme "İndir / güncelle" / "Yüklə / yenilə" (`building` iken pasif); ilerleme `'%<yüzde> · <aşama>'`; açıklama "OSM yolları ayda bir güncellemek yeterli. Geniş bölge daha büyük indirme demek." / "Xəritə tətbiqin içində gəlir; burdan yalnız yeniləmək olar. Böyük Bakı daha çox yer tutur." (iki dil farklı metin — aynen korunur). Kurulum ekranı adım 2 "Bakü yol haritası" / "Bakı yol xəritəsi", düğme "İndir ve başla" / "Yüklə və başla", indirme sırasında "Ekranı açık tut. Uygulamayı kapatma." / "Ekranı açıq saxla. Tətbiqi bağlama.", alt yazı "Harita © OpenStreetMap katkıcıları · Yükseklik: SRTM" / "Xəritə © OpenStreetMap iştirakçıları · Hündürlük: SRTM" (ODbL atfı; korunur).

### 2.10 POI dosyası (Y7 akışı; POI davranışı Bölüm 5.3)

- POI sorgusu (`poiQuery`, pois.dart:86–98, aynen): `[out:json][timeout:120]; ( nwr["amenity"~"^(restaurant|fast_food|cafe)$"]({bbox}); nwr["shop"="mall"]({bbox}); nwr["shop"~"^(convenience|supermarket|bakery|butcher)$"]({bbox}); nwr["station"="subway"]({bbox}); node["highway"="traffic_signals"]({bbox}); node["crossing"="traffic_signals"]({bbox}); node["highway"="crossing"]["crossing:signals"="yes"]({bbox}); ); out tags center qt;`
- Sıkı biçim `pois_<region>.json` (gzipsiz): `{"v":1,"pois":[[lat6, lon6, "<tür>", "<ad>", "<osmRef>"], …]}` — koordinat 6 ondalığa yuvarlanır, liste enleme göre sıralı; okurken ≥ 4 elemanlı satır kabul (5. yoksa `osmRef = ''`).
- `downloadPois(box)`: önce hazır veri sunucusu manifestinde aynı kutunun `pois` dosyası (60 sn; gzip sihri varsa açılır; `PoiSet.decode` ile denenir, bozuksa düşülür), yoksa Overpass `poiQuery(box)` → `poisFromOverpass` → sıkı biçim; hata → null (sessiz).

### 2.11 CI gömülü harita (YolGo) ve WEEM karşılığı

- YolGo (`ci/build.sh:202–231`, her APK derlemesinde): Geofabrik `https://download.geofabrik.de/asia/azerbaijan-latest.osm.pbf` → `ci/osm_extract.py az.pbf roads.json pois.json` (pyosmium; `wideBox` içinde ≥ 1 düğümü olan `highway ∈ HW` yollar; `area != yes`, `access ∉ {private,no}`, `service ∉ {parking_aisle, driveway}` — `drive-through/emergency_access` burada süzülmez, graf kurarken süzülür; `pedestrian`+`area=yes` alınır; kapalı park çokgenleri yalnız `leisure/landuse` etiketiyle; düğüm koordinatı 7 ondalık; düğümler yollardan önce, ilk görülme sırasıyla; POI düğümleri ve way merkezleri (düğüm ortalaması), `highway=traffic_signals` way'leri hariç) → `timeout 1500 dart run tool/build_map.dart assets/map roads.json pois.json` (Geofabrik başarısızsa uyarı + Overpass `wideBox.split(0.1)`).
- `tool/build_map.dart`: `ensure(wideBox)` (DEM yoksa hata `'yükseklik verisi yok'`); her `.hgt` için `_maskOutside` (karo içinde `wideBox ± 0.02°` dışındaki örnekler **0** yazılır — boşluk değil; dosya biçimi aynı, gzip küçülür) → `<ad>.hgt.gz`; `buildRoadGraph(raw, dem.at)` → `graph_wide.bin.gz` (kenar < 1.000 → `'wide: çok az yol (<n>)'` hatası); POI → `pois_wide.json.gz` ve `pois_central.json.gz` (`centralBox ± 0.01°`); `manifest.json` = `{"version": "<UTC YYYY-MM-DDTHH:MM>", "format": 2, "regions": ["wide"], "pois": {<tür>: <sayı>}}`. Harita üretilemezse yayın durur (`fail "Gömülü harita"`).
- **WEEM (A6.3, öneri):** `ci/osm_extract.py` aynen taşınır (Python, pyosmium); `tool/build_map.dart`'ın Kotlin karşılığı `logic/map` içinde saf `graph.MapBuild` + JVM giriş noktası (ör. `./gradlew -p logic :map:buildMap --args="<çıktı> roads.json pois.json"`), aynı çıktıları `map/src/main/assets/map/` altına üretir (repoya girmez; repoda yalnız boş sürümlü yer tutucu manifest). Yalnız `apk.yml`'de (`weem-v*` etiketi / elle) çalışır, PR CI'ında değil (Actions dakikası, `AGENTS.md`); üretilemezse APK işi kırmızı. Yerel geliştirmede harita gömülü değilse uygulama kurulum ekranından indirir. Gömülü harita APK'ya ~MB ekler: A6.5'teki MapLibre ölçümüyle birlikte APK boyutu belgeye yazılır.

### 2.12 Veri

| YolGo | Tür | WEEM |
|---|---|---|
| `files/mapdata/graph_<region>.bin` (KNG1 v2) | dosya | aynı yol ve biçim (`filesDir/mapdata/…`; yeniden üretilir, S1 aktarım yok) |
| `files/mapdata/dem/N40E049.hgt`, `N40E050.hgt` | dosya | aynı |
| `files/mapdata/pois_<region>.json` | dosya | aynı |
| `files/mapdata/parts_graph_<region>/*.json.gz` | önbellek | aynı (2 gün kuralı) |
| `assets/map/manifest.json`, `graph_wide.bin.gz`, `N40E04{9,50}.hgt.gz`, `pois_wide.json.gz`, `pois_central.json.gz` | APK asset | aynı adlar, `:map` modülünün `assets/map/` klasörü |
| `flutter.map.bundled.<region>`, `flutter.map.bundledAt` | String sürüm | `map` tercih dosyasında `map.bundled.<region>`, `map.bundledAt` |
| `settings.v1.region` | `'central'`\|`'wide'` | `MapSettings.region` (alan tablosu Bölüm 14) |
| Ağ uç noktaları | Overpass aynaları, AWS skadi, Geofabrik (CI), hazır veri sunucusu | aynı; `User-Agent` WEEM adıyla |

### 2.13 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| YT `overpass_test` | 3 | "düzgün cevap kabul edilir" (`{"version":0.6,"elements":[]}` → null); "sondaki runtime error notu yakalanır" (remark değeri döner); "JSON olmayan ve boş cevap reddedilir" (`'JSON değil'`, `'boş cevap'`) | `logic/map` `graph.OverpassProblemTest` | A6.2a → A6.3 | — |
| YT `map_data_update_test` | 2 | "güncelleme hatası eldeki haritayı korur" (`updateFailed` fazı `error`, graf aynı nesne, `ready == false`); "atomik yazma eski dosyayı … korur" (`.tmp` kalmaz; hedef klasör yoksa hata, eski dosya aynı) | `graph.MapDataStateTest` (saf durum) + `:map` `AtomicFileTest` (JVM dosya, Android'siz) | A6.2a → A6.3 | atomik yazma `logic/`'te `java.io.File` ile saf yazılabilir |
| YT `router_test` "graf kurulur ve dosyaya yazılıp okunabilir" | 1 (38'in) | M iç nokta kavşak değil → N = 4, E = 4; serialize→deserialize sonrası `edgeCount`, `climbF`, `names` aynı | `graph.RoadGraphFileTest` | A6.2a → A6.3 | kalan 37 router testi Bölüm 3.14 |
| YT `bikeway_e13_test` "E13 etiketler" (3) + "E13 harita dosyası" (2) | 5 (18'in) | bayrak tablosu; Overpass sorgusu `cycleway` içerir + `out body`; eski başlık (`features` = 0, ofset 36) okunur ve uyarır; sürüm 1 (ofset 4) → `FormatException` | `graph.BikewayTagsTest`, `graph.RoadGraphFileTest` | A6.2a → A6.3 | dosyanın tamamı Bölüm 5.1'de eşlenir |
| YT `park_paths_test` 1–2 | 2 (4'ün) | park bayrağı (park/garden/recreation, `bicycle=no` hariç, parktaki residential almaz), `hasParkData` | `graph.ParkPathsTest` | A6.2a → A6.3 | 3–4 Bölüm 3.14 |
| **Yeni (karakterizasyon)** | — | (a) KNG1 altın baytları: küçük sentetik OSM'den (router_test `_graph(hill: 10)`) üretilen dosyanın başlığı, hizaları, `u8/u16` alanları ve ad bölümü bayt bayt; little-endian; (b) `buildRoadGraph` sayıları: sıklaştırma (60 m parça → `ceil(60/25) = 3` adım, 2 ara nokta), 3'lü ortalama, `minGradeRun`, ad önceliği; (c) `HgtTile.sample` boşluk ortalaması ve kenar; (d) `BBox.split` 91/28 karo; (e) `nearestEdge` `heading` süzgeci ve `contains` payı | `graph.*Test` | A6.2a (yeni yazılır) → A6.3 | Dart tarafında ilgili sayılar YolGo üstünde bir kez çalıştırılıp sabitlenir; transandantal fonksiyonlarda (sin/cos/asin) ulp farkı için `closeTo` toleransı |

### 2.14 WEEM yerleşimi

- `logic/map` `geo`: `GeoPoint`, `BBox`, `Baku`, `Geo` (haversine, bearing, angleDiff, `LocalProjection`, `pointToSegment`).
- `logic/map` `graph`: `OsmRaw`/`OsmWay`, `RoadClass`, `WayFlag`, `GraphFeature`, `GraphBuilder` (`buildRoadGraph`, `classifyHighway`, `wayFlagsFromTags`, `isParkAreaTags`), `RoadGraph` (sorgular + `serialize/deserialize`), `HgtTile`, `ElevationModel` (örnekleme saf; indirme `HttpPort` arayüzüyle), `Overpass` (sorgu, aynalar, `overpassProblem`, `OverpassFetcher` — ağ `HttpPort` ile), `MapDataState`/`MapDataPhase`, `PoiSet`/`Poi`/`poiQuery`/`poisFromOverpass`, `MapBuild` (CI aracı), indirme akışını yöneten saf `MapDownload` (ilerleme metinleri, parça adları, 2 gün kuralı, hata metni; dosya ve ağ portlarla).
- Android `:map` (`com.kuryelog.app.map`): `MapData` (tek örnek; `StateFlow<MapDataState>`; asset açma, dosya okuma `Dispatchers.IO`, graf çözme `Dispatchers.Default`), indirme işi (kullanıcı başlatır; YolGo'daki gibi ekran açıkken süren coroutine; ilerleme `StateFlow`), `HttpPort` uygulaması (OkHttp/`HttpURLConnection`, `User-Agent`). Ekranlar `:app`: Daha fazla → Harita ayarları → "Harita verisi" bölümü, ilk kurulum adımı (Bölüm 6).
- `:core` portları: kullanılmaz (harita verisi `:map` içi); `ShiftPort`'a bağlı değil.
- 2 telefon: harita verisi telefon başına (her telefon kendi `mapdata/`'sını açar/indirir; mesaj yok). Receiver'da rota motoru için şart; Provider'da da sekmeler aynı olduğundan bulunur.

### 2.15 Farklar ve açık noktalar

- **Bayt sırası:** KNG1 little-endian, HGT big-endian; Kotlin'de ikisi de açıkça belirtilir (eski hata riski: `ByteBuffer` varsayılanı big-endian).
- **Kesinlik:** `f32` alanlar Dart `Float32List` ile aynı yuvarlanır (`toFloat()`); sin/cos/asin/sqrt Dart VM ile JVM arasında son haneyi farklı verebilir → testler `closeTo` (YolGo testleri de öyle); bayt bayt altın test yalnız tamsayı/yapı alanları ve doğrudan kopyalanan koordinatlar için.
- Karo parça dosya adı Dart `double.toString` biçimiyle (`40.28_49.58_40.332857142857144_…`); yalnız önbellek, WEEM'de aynı kural `BBox.overpass` ile (JDK 21 kısa gösterim aynı basamakları verir; farklı olursa yalnız önbellek ıskalanır).
- `regionBox` ve `isInAbsheron` ölü kod; `region` yalnız dosya adı. WEEM'de davranış aynı kalır (S9); ölü kod taşınmaz.
- Yer tutucu manifestte `regions` sözlük, `build_map` liste yazar; kod yalnız `is List` denetler (uyumlu). WEEM yer tutucusu `{"version":"","regions":[]}`.
- Gömülü harita sürümü değişince elle indirilmiş harita üzerine yazılır (YolGo davranışı; korunur).
- **AZ çeviri hatası (eski hata, WEEM'de baştan doğru):** arka plan mesajları `trMsg` ile alt dize değişimiyle çevrilir ve sözlük sırası yüzünden karışık çıkar: `'Hazır yol verisi aranıyor'` → "Hazırdır yol məlumatı axtarılır", `'Tüm sunucular meşgul, 15 sn sonra tekrar'` → "Tüm sunucular məşğuldur, 15 san sonra yenidən", `'Yol verisi indirilemedi (…)'` → "Yol məlumatı indirilemedi (…)"; `'Harita dosyası eski sürüm'` hiç çevrilmez. WEEM: ilerleme ve hata metinleri kodlu (`MapDownload` aşaması + sayılar), dil tablosunda her mesajın bütün AZ karşılığı (sözlüğün hedef metinleri: "Hazır yol məlumatı axtarılır", "Bütün serverlər məşğuldur, N san sonra yenidən", "Yol məlumatı yüklənmədi (…)"; "Harita dosyası eski sürüm" için AZ A6.3'te yazıldı: "Xəritə faylı köhnə versiyadır", 17.3 M-2-1). TR metinler harfiyen aynı.
- `edgesOfOsmWay` vb. doğrusal tarama (performans; WEEM dizinle hızlandırabilir, sonuç aynı).
- **SORU (koordinatöre):** hazır veri sunucusu `mehemmedsh/kurye_nav_data` bu ortamdan erişilemedi (manifest 404). Seçenekler: (a) zincir aynen kalsın (sunucu yoksa ≤ 20 sn sonra Overpass'a düşer); (b) WEEM'de kaldırılsın, doğrudan Overpass. Önerilen varsayılan (a) — davranış aynı (S9), zararsız.

### 2.16 WEEM uygulaması (A6.3, 19 Ekim)

- `logic/map` `geo` (`pointToSegment`, `GeoPoint.isInAzerbaijan/toJson/fromJson/toString` eklendi), `net` (`HttpResponse.bytes` + `HttpRequest.binary` ikili indirme için, `UserAgent`, `UrlHttp` = `HttpURLConnection`), `graph`: `RoadGraph` (KNG1 okuma/yazma, CSR, ızgara, `nearestEdge(s)`, `contains`, `edgesWithin`, `geometry` → `GeomPoint`, `inPedestrianZone`; way → kenar dizini, sonuç Dart taramasıyla aynı), `GraphBuilder`, `OsmRaw.addOverpassJson`, `Overpass` + `OverpassFetcher`, `HgtTile`/`ElevationModel` (`Elevation.kt`), `MapMsg` (kodlu TR/AZ ilerleme, günlük ve hata metinleri; YolGo `trMsg` sözlüğünün hedef metinleri bütün hâlinde) + `MapDataException`, `MapDownload` (`_buildEntry` + `_loadPrebuilt`), `MapDataController` (`load`, `download`, `deleteAndReload`, `_installBundled`; `MapAssets` ve `MapPrefs` portları), `MapBuild` (`tool/build_map.dart`; `main` + `./gradlew -p logic :map:buildMap --args="<çıktı> roads.json pois.json"`).
- Android `:map`: `MapData.controller(context)` (tek örnek; `filesDir/mapdata`, `assets/map/`, dil `kuryelog` dosyasındaki `lang`), `AndroidHttp` (= `UrlHttp`), `MapPrefsStore` (`SharedPreferences("map")`; StringList JSON dizisi, Double bit deseni `Long`). Ekranlar (Harita verisi bölümü, kurulum adımı) bağlı değil: A6.5 / Bölüm 6.
- CI: `ci/osm_extract.py` (YolGo'nunki aynen), `ci/build_map.sh` (Geofabrik → pyosmium → `MapBuild`; olmazsa Overpass; üretilemezse çıkış 1), `apk.yml` adımı "Gömülü harita" (iş süresi 45 dk). Repoda yalnız `map/src/main/assets/map/manifest.json` = `{"version":"","regions":[]}`; üretilen dosyalar `.gitignore`'da. Yerel deneme (sentetik yol dosyası + gerçek SRTM): `N40E049.hgt.gz` 1,1 MB, `N40E050.hgt.gz` 0,37 MB; Geofabrik bu ortamdan kapalı (403), gerçek Bakü grafının boyutu ve süresi ilk APK işinde ölçülür.
- **Farklar (davranış aynı):** indirilen SRTM karosu da atomik yazılır; kısa/bozuk KNG1 dosyası `FormatException("Harita dosyası bozuk")` (Dart `RangeError`; denetleyici ikisini de "okunamadı, yeniden indir" der); POI indirme/tazeleme (`downloadPois`, `_fetchPoisLater`) ve `MapBuild` yer adımı `PoiSet` ile **A6.8**'de (o zamana dek `MapBuild` yerleri atlar, manifest `pois` = `{}`); hazır veri sunucusu zinciri 17.1 #12 varsayılanı (a) ile aynen.
- **Risk:** YolGo'nun ürettiği bir KNG1 dosyasıyla bayt bayt kıyas yapılmadı (Dart yok); uyumluluk 2.5'e göre yazıldı ve testlendi. İlk gerçek harita dosyası (APK işi ya da telefonda "İndir / güncelle") cihazda doğrulanmadı.
- **A6.4 ekinde (19 Ekim):** Dart SDK bu ortamda kuruldu (`storage.googleapis.com/dart-archive`); `tools/route-golden` YolGo `buildRoadGraph` + `serialize`'ını aynı OSM ve yükseklik girdisiyle çalıştırır, `route.RouterGoldenTest` WEEM `GraphBuilder` çıktısının KNG1 baytlarını (boy + FNV-1a) karşılaştırır: 4 sentetik şehirde (park, meydan, çift yol, bütün etiket türleri) **bayt bayt aynı**. Gerçek Bakü dosyası ve Overpass JSON yolu yine doğrulanmadı.
<!-- END sec:2 -->

<!-- BEGIN sec:3 -->
## 3. Y3 — Rota motoru (A6.4)

### 3.1 Kaynak ve kapsam

| Dosya (YG) | Satır | İçerik |
|---|---|---|
| `lib/core/physics.dart` | 1–126 | `VehicleProfile` 6–19, `RideMode` 21–26, `RideParams` 29–126 |
| `lib/routing/router.dart` | 1–862 | `RoutingException` 13–18, `CostModel` 26–342 (`_eval` 242–341), `_Heap` 344–390, `_Step` 392–396, `_WalkingApproach` 400–454, itme sabitleri 457, yaya bölgesinden çıkış 459–566, `_routeLegFrom` 568–587, `_routeLeg` (A*) 589–862 |
| `lib/routing/planner.dart` | 1–201 | `PlanRequest` 11–56, `computePlans` 65–77, yüzey adayı 82–101, `_planWithScale` 103–136, `_bestOrder` 140–189, `_permutations` 191–201 |
| `lib/routing/route.dart` | 1–202 | `RoutePoint`, `RouteSeg`, `RouteTurn`, `RouteLeg`, `Plan`, `progressAlong` 159–174, `pushedRuns` 177–202 |
| `lib/routing/route_worker.dart` | 1–163 | izole işçi, zaman aşımı |
| `lib/core/settings.dart` | 133–153 | `gradeLimitFor`, `paramsFor` |
| başvuru | — | `lib/routing/quality.dart` (Y12, Bölüm 9), `lib/surface/bad_spots.dart` (kötü nokta aralıkları), `lib/routing/route_hours.dart` (Bölüm 4), `lib/nav/nav_controller.dart:473–507, 976–985` (isteği kuran ve hatayı çeviren; ajan N, Bölüm 7), `lib/economics/dataset_runtime.dart:50–75` (Y19 rota çağrısı; ajan T) |

Bulgu `Y_routing.md` §Y3 kaynakta doğrulandı; kurallar ve sabitler doğru. Bulgu düzeltmesi: satır numaraları eski (doğrusu yukarıdaki tablo; ör. `CostModel` 26–342, `_eval` 242–341, sezgisel 653–658, `pushCostFactor` 457, `heuristicScale` 86–92). Ekler: kara liste denetimi `_eval`'de **en sonda** (sonuç aynı: kenar kapalı); `walkable` (yürüme) ve `allowed` (sürüş) ayrı kurallardır (3.5); `softBlock` parametresi tanımlı ama **hiç kullanılmıyor** (ölü); `wrongWayKmh` (18) ve `parkPenaltyS` (15) ayardan değil `RideParams` varsayılanından gelir.

### 3.2 Fizik ve parametreler (`RideParams`, physics.dart:29–126)

| Alan | Varsayılan | Ayar kaynağı (`paramsFor(battery)`) |
|---|---|---|
| `flatWhPerM` | 0.0135 Wh/m | `settings.flatWhPerM` |
| `climbWhPerM` | 0.38 Wh/dikey m | `settings.climbWhPerM` |
| `climbSafety` | 1.0 | `settings.climbSafety` |
| `flatKmh` / `climbKmh` / `walkKmh` | 28.5 / 18.5 / 4.5 | aynı adlar |
| `maxGrade` | 0.18 | `gradeLimitFor(battery)` = (`battery != null && battery < lowBatteryPct (50)` ? `lowBatteryGradePct (10)` : `maxGradePct (18)`) / 100 |
| `mainRoadPenalty` | 1.15 | `settings.mainRoadPenalty` |
| `wrongWayKmh` | 18 | — (sabit) |
| `walkRadius` | 150 m | `settings.walkRadius` |
| `parkPenaltyS` | 15 sn | — (sabit) |
| `qualityWeight` | 0.10 | `qualityTolerancePct / 100` (10; kaydırıcı 0–30, Yollarım → Kalite, Bölüm 9) |
| `turnPenaltyS` | 12 sn | `settings.turnPenaltyS` |
| `bikewayFactor` | 0.85 | `bikewayCostPct.clamp(50, 100) / 100` (Bölüm 5.1) |

- Türetilen: `flatMps = flatKmh/3.6`, `walkMps`, `wrongWayMps`; `betaWhPerS = 0.02`, `gammaSPerWh = 1.0`.
- `climbMps(grade)`: `grade ≤ 0.02` → `flatKmh`; `≤ 0.08` → `flatKmh − (flatKmh − climbKmh)·(grade − 0.02)/0.06`; üstü → `max(10, climbKmh·0.08/grade)`; sonuç `/3.6`.
- `energyWh(L, climb) = flatWhPerM·L + climbWhPerM·climbSafety·climb`.
- `combine(mode, s, wh)`: `time` → `s + 1.0·wh`; `battery` → `wh + 0.02·s`.
- A* alt sınırları: `minRideCostPerM` = time `1/flatMps`, battery `flatWhPerM + 0.02/flatMps`; `minWalkCostPerM` = time `1/walkMps`, battery `0.02/walkMps`.
- `withMaxGrade(g)`: yalnız `maxGrade` değişen kopya. `RideMode` sırası `battery`, `time` (planlayıcı bu sırayla dener); etiketler "BATARYA"/"BATAREYA", "ZAMAN"/"VAXT", simge 🔋/⚡.
- `VehicleProfile`: `g3` "Max G3 (598 Wh)" 598 Wh; `g3x` "Max G3 + ek batarya (1066 Wh)" 1066 Wh; bilinmeyen id → ilki. (Batarya kullanımı Bölüm 10.)
- 1.11'de bu katsayıların ekranı yok (yalnız JSON varsayılanı); ekranda değişenler: `qualityTolerancePct`, `bikewayCostPct`, bölge, ışık, ses tuşu, hızlar, ters yön ayarları.

### 3.3 Maliyet modeli girdileri (`CostModel`, `PlanRequest`)

| Girdi | Tür | Üreten (WEEM bölümü) | Boşken |
|---|---|---|---|
| `approved`, `blocked`, `steep` | OSM way id kümeleri | Y8 sokak kararları (4) | kural yok |
| `reverseHours` | way id → `ReverseHours` | Y8 (4) | onaylı ters yön her saat |
| `ghost` | **kenar indeksi** kümesi ("Burada yol yok") | Y12 `roads.ghost.v1` (9) | etkisiz |
| `learned` | way id → `(q, c, r)` | Y12 (9) | kalite = OSM ön tahmini (aşağıda) |
| `wwRatio`, `wwAvoid` | way id → oran / küme | Y12 ters yön soruları (9) | oran 0, yasak yok |
| `wrongWayEdges` | kenar indeksi (ana caddede kesim izni) | Y12 `roads.wwseg.v1` (9) | yok |
| `bikeways` | way id (elle bisiklet yolu) | Y15 (5.1) | yalnız OSM bayrağı |
| `marks` | `SurfaceMark` listesi (Ses− kötü nokta, "geçilebilirlik: kapalı") | Y13 deposu; WEEM 1.0'da Ses− kaydı Bölüm 9 kararı | etkisiz |
| `surface`, `surfaceQuality` | ölçülmüş yüzey parçaları, sokak puanı | Y13/Y14 (**sonra**) | etkisiz; planlayıcı tek aday |
| `departure` | UTC an | çağıran (`DateTime.now().toUtc()`; Y19 kayıt anı) | şimdi |
| `heading` | derece | çağıran: hız ≥ 2 m/s ve yön ≥ 0 iken (Bölüm 7) | yönsüz |
| `fixedOrder` | bool | çağıran (Y19 `true`) | sıra denenir |
| `avoidBad`, `surfaceScale` | — | planlayıcı (3.10) | — |

**Önemli:** `learned` boşken bile kalite terimi nötr değildir: her way için `quality = priorQuality(sınıf, bayrak)` (trunk/primary 0.60, secondary 0.68, tertiary 0.76, unclassified/residential/road 0.82, livingStreet 0.70, service 0.75, track 0.45, cycleway 0.90, path/footway/pedestrian 0.60, steps 0; `unpaved` ≤ 0.35, `rough` ≤ 0.50; quality.dart:23–39). Öğrenilmiş değer gelince `blendQuality` (Bölüm 9). Kenar kalitesi = way kalitesi.

### 3.4 Kenar maliyeti `_eval(e, dir)` (router.dart:242–341)

Her kenarın iki yönü ayrı dizi hücresi: `i = e·2 + dir` (0 = çizim yönü `from→to`, 1 = ters). Diziler: `cost`, `time`, `wh` (f64), `wrong`, `pushed` (u8), `quality` (kenar başına f32). Başlangıçta `cost = +∞`. Sıra (her `return` = yasak):

1. Kenar `_closed` (kullanıcının "kapalı" işareti: `marks` içinde `!removed`, `source == 'user'`, `access == closed`; aynı way'de `nearestEdge` ≤ 25 m) ya da `ghost` → yasak.
2. `restricted` ya da `sidewalk` bayrağı → yasak. `service` sınıfı → yasak ("dükkan önü/cep yolları geçiş koridoru değildir"). `avoidBad` ve (`badWay(osm)` ya da `unpaved`) → yasak. `steps` → yasak.
3. **İtme kipi** (madde 9): yaya sınıfı **ve** `bikeOk` yok **ve** onaysız **ve** `park` değil → `footNo` ya da kara listede ise yasak; değilse `t = len·1.2/walkMps`, `cost = combine(mode, t·2.5 + 20, 0)` (`pushCostFactor = 2.5`, `pushPenaltyS = 20`), `time = t`, `wh = 0`, `wrong = 0`, `pushed = 1`; bitti (tek yön kuralı uygulanmaz).
4. **Ters yön:** `against` = (dir 0 ve `onewayBwd`) ya da (dir 1 ve `onewayFwd`); `wrongWay = against && !contraflow`. `wrongWay` ve değil (`(onaylı && !isMainRoad(sınıf)) || wrongWayEdges ∋ e`) → yasak. `wrongWay` ve `wwAvoid ∋ osm` → yasak. `twoWay = wrongWay && onaylı && !isMainRoad && (wwRatio[osm] ?? 0) == 0` (madde 10b: "sorun yok" yeşil sokak iki yönlü sayılır).
5. Eğim: `maxGradeF/B` (yöne göre) `> params.maxGrade` → yasak.
6. Aşırı eğimli (`steep ∋ osm`): `upHere = climb(bu yön)`, `upBack = climb(ters yön)`; iniş = `upBack ≥ 1.0 && upHere ≤ 0.3·upBack`; iniş değilse yasak (yükseklik yönü ayırt edemiyorsa iki yön de kapalı).
7. Hız: `sf` = `unpaved` 0.6 / `rough` 0.8 / 1.0; `vFlat = flatMps·sf`; `vClimb = climbMps(up > 1 ? climb/up : 0)·sf` (`up` = bu yönün `upLen`). `wrongWay && !twoWay` → ikisi `min(…, wrongWayMps)`. Yaya sınıfı (onaylı ya da `bikeOk`) ve park değil → ikisi `min(…, 15/3.6)`. `t = (len − up)/vFlat + up/vClimb`.
8. Enerji: `energyWh(len·(unpaved ? 1.3 : 1.0), climb)`.
9. Maliyet: `tCost = t`; trunk|primary → `×mainRoadPenalty`; `wrongWay && !twoWay` → `×(1.2 + 2.0·(wwRatio[osm] ?? 0))`; `c = combine(mode, tCost, energy)`; `c ×= 1 + qualityWeight·(1 − quality[e])`; residential|livingStreet|unclassified|road → `c ×= 1.35`; `badWay(osm)` → `c ×= 4`; `!wrongWay` ve (bayrak `bikeway` ya da `bikeways ∋ osm`) → `c ×= bikewayFactor`.
10. `blocked ∋ osm` → yasak. Değilse `cost = c`, `time = t`, `wh = energy`, `wrong = wrongWay ? 1 : 0`.

`badWay(osm)`: `learned[osm]` yoksa false; `r > 0` → `r ≤ 25` (`QualityRating.bad`); değilse `c ≥ 0.8 && q < 0.3`.

### 3.5 Yardımcı kurallar (CostModel)

- `allowed(e, dir)` = `cost` sonlu. `rideable(e)` = bir yönde izinli **ve** o yön itilen değil (başlangıç, hedef ve yaya bölgesinden çıkış buna bağlanır).
- `walkable(e, dir)` (son yürüyüş ve yaya çıkışı): `_closed`, `ghost`, `blocked` → hayır; `restricted` → hayır; `avoidBad` ve (`badWay` ya da `unpaved`) → hayır; `steps`, **`trunk`**, `footNo` → hayır; yön eğimi `> maxGrade` → hayır; `steep` ve iniş değil (`down ≥ 1 && up ≤ 0.3·down` değil) → hayır. `service` ve `sidewalk` yürünür; tek yön yürüyüşte geçerli değil.
- `allowedAt(e, dir, elapsedS, portion = 1, f0 = 0, f1 = 1)`: `allowed` değilse hayır; `avoidBad` iken bu yönün ölçülmüş parçalarından (`calibrated && confidence ≥ 0.65 && score < 30`) aralığa değen varsa hayır (Y13); `avoidBad` iken doğrulanmış kötü nokta aralığına değiyorsa hayır; `portion ≤ 0` ya da ters yön değilse evet; yoksa `reverseHours[osm]` null → evet, değilse `allowsTrip(departure + elapsedS, time·portion)` (Bölüm 4.3).
- `segmentCost(e, dir, f0, f1)`: `base = cost[i]` sonsuzsa ∞; `total = base·|f1 − f0|`; her ölçülmüş parça için `+ base·overlap·(1 − q)·conf·(q < 0.3 ? 3 : qualityWeight)·surfaceScale` (q = sokak puanı varsa o ve güven 1, yoksa `score/100` ve parçanın güveni); her kötü nokta aralığı için `+ base·overlap·(confirmed ? 6 : 3)`. Kötü nokta: `isBadSpot` (`!removed`, `source == 'user'`, `access != closed`, etiket ∈ {rough, pothole, raisedAsphalt, dirt, gravel, obstacle}); aralık = aynı way'de en yakın kenar (≤ 25 m) üstünde `fraction ± 25/len` (0–1'e kıstırılır); aynı way'de 30 m içindeki işaretler **farklı Bakü günlerinden** ≥ 2 ise `confirmed` (bad_spots.dart:9–51).
- Dönüş: `startB/endB[e·2+dir]` = kenarın ilk/son geometri parçasının yönü (yöne göre); `turnAngle(inE, inDir, outE, outDir) = (startB[out] − endB[in] + 540) mod 360 − 180` (+ sağ, − sol). `turnCost = combine(mode, turnPenaltyS, 0)`; `turnExtra` = `|açı| < 35` → 0, `> 120` → `1.5·turnCost`, arası `turnCost` (turnCost ≤ 0 ise 0). `junctionCost = combine(mode, 2.0, 0)`.
- `heuristicScale` = grafta `bikeway` bayrağı ya da elle `bikeways` varsa `min(1, bikewayFactor)`, yoksa 1.

### 3.6 A* tek bacak (`_routeLeg`, router.dart:589–862)

1. Başlangıç kenarı: `nearestEdge(from, 60 m, accept = rideable, heading)`, bulunamazsa yönsüz; yoksa `'Konumunun yakınında sürülebilir yol yok'`.
2. Hedef: `_WalkingApproach(to, aday)` (3.7); hedef kenarı yoksa `'Hedefe bağlı yol bulunamadı; yol olmayan yerden geçiş çizilmedi'`. `dest` = hedef kenarı `rideable` ise o, değilse yok.
3. Bitiş teklifleri (düğüm → (maliyet, tür); küçük olan kalır): `walkCost(m) = m ≤ 20 ? 0 : combine(m·1.2/walkMps + parkPenaltyS, 0)`; `dest` varsa tür 1: `from` düğümünden hedef oranına ileri sür (`segmentCost(e,0,0,f) + walkCost(dest.dist)`, yön izinliyse), tür 2: `to` düğümünden geri (`segmentCost(e,1,f,1) + walkCost`); yürüme yaklaşımının her düğümü tür 0: `d < 0.5 ? 0 : combine(d·1.2/walkMps + parkPenaltyS, 0)`. Hiç teklif yoksa `'Hedefe yürüyüş mesafesinde bağlı araç yolu bulunamadı'`.
4. Sanal hedef düğümü = `nodeCount`. Sezgisel: `cMin = min(minRideCostPerM, minWalkCostPerM)·0.99·heuristicScale`; `h(v) = max(0, ‖v − to‖ − walkRadius − 300)·cMin` (uzaklık hedef noktaya yerel izdüşümde).
5. Tohum: başlangıç kenarının oranı `sf`; `allowedAt(se, 0, startOffsetS, portion 1−sf, f0 sf, f1 1)` ise `to` düğümüne `segmentCost(se,0,sf,1)` (süre `time·(1−sf)`); `allowedAt(se, 1, …, portion sf, 0..sf)` ise `from` düğümüne `segmentCost(se,1,0,sf)`. Aynı kenarda doğrudan varış (`dest.edge == se`): `df ≥ sf` ileri, değilse geri (`allowedAt` ile), maliyet + `walkCost`; hedefe yazılır (`targetFrom = −2`). Hiç tohum yoksa `'Başlangıç yolu şu kurallarla sürülemiyor'`.
6. Döngü: yığından çek; hedefse dur; kapalıysa geç; kapat. Düğümün bitiş teklifi varsa ve (tür 0 ya da `allowedAt(dest kenarı, yön, startOffsetS + elapsed[u], kısmi)`) ise `dist[u] + teklif < dist[hedef]` → hedefi güncelle ve yığına koy. Komşular (`adjStart` sırası): `c = segmentCost(e, dir, 0, 1)` sonsuzsa ya da `!allowedAt(e, dir, startOffsetS + elapsed[u])` ise geç; karşı düğüm kapalıysa geç; gelen kenar (tohumda başlangıç kenarı) ile dönüş ek maliyeti yalnız `degree(u) ≥ 3`; `nd = dist[u] + c + turn + (degree(v) ≥ 3 ? junctionCost : 0)`; küçükse güncelle, `elapsed[v] = elapsed[u] + time + (degree(v) ≥ 3 ? 2 : 0)`, yığına `nd + h(v)`.
7. Hedef sonsuzsa `'Kurallara uyan rota bulunamadı'`.
8. Geri kurulum: adımlar (başlangıç kısmı → ara kenarlar → hedef kenarı kısmı ya da yürüme yaklaşımının adımları).
9. İstatistik: her adım için `segM = len·portion`, `RouteSeg(edge, startM = o ana kadarki rideM, segM, ad, walk = pushed)`; `rideM` itilen metreyi **içerir**; `pushM`; `timeS += time·portion`; `energy += wh·portion`; uzunluk ağırlıklı kalite; ters yön metresi ve `riskSum += wwRatio·segM`; geometri noktaları art arda aynıysa atlanır; `climbM` = ardışık nokta yükseklik artışları toplamı; sokak adları (boş değil, art arda tekrar yok).
10. Dönüşler: ardışık iki adımın giriş düğümünde `degree ≥ 3` ve `|açı| ≥ 30` → `RouteTurn(atM, açı, girilen sokak adı, düğüm konumu)`; `sharpness` = `<60` 0 (hafif), `<135` 1, değilse 2.
11. Son yürüme: `walkM > 0` ise `timeS += walkM·1.2/walkMps + parkPenaltyS`.
12. Uyarılar: `pushM > 0` → `'Rotada yaklaşık <round(pushM)> m yaya yolu: scooter\'ı it'`; hedef kenarı uzaklığı > 20 m → `'Rota erişilebilir yolda biter; kapı girişi yaklaşık <round(dist)> m uzakta'`.

Yığın (`_Heap`): dizi tabanlı ikili yığın; yukarı çıkarken `ebeveyn ≤ çocuk` ise dur, aşağı inerken sol/sağ yalnız **kesin küçükse** seçilir. Eşit maliyetli yollarda aynı rotayı vermek için WEEM aynı yığını yazar (`java.util.PriorityQueue` değil). Sözlükler ekleme sırasını korur (`LinkedHashMap`).

### 3.7 Hedefe bağlanma (`_routeLegFrom`, `_WalkingApproach`)

- `from` ya da `to` graf kapsamı dışında (`contains`, 100 m pay) → `'Konum veya hedef indirilen yol haritasının dışında'`.
- Adaylar: `nearestEdges(to, 80 m, accept = bir yönde walkable, limit 4)`; boşsa `'Hedef yakınında erişilebilir yol yok; pini yol girişine taşı'`. Adaylar sırayla denenir; ilk başarılı bacak döner; hepsi düşerse son hata (yoksa `'Kurallara uyan rota bulunamadı'`).
- `_WalkingApproach`: hedef kenarının iki ucundan (yön `walkable` ise, kenar üzeri uzaklık ≤ `walkRadius`) başlayarak **yalnız `walkable` kenarlarda** uzunlukla Dijkstra (`d > walkRadius` ya da daha kötü → geç); her düğüm için hedefe yürüme mesafesi ve adımları. Kuş uçuşu yürüme yok (bina/duvar üstünden çizgi çizilmez).

### 3.8 Yaya bölgesinden çıkış (`routeLeg`, eski iç ad "Y7"; WEEM adı `StartWalkOut`)

- `routeLeg(model, from, to, heading, startOffsetS)` önce normal bacak; hata mesajı `startFailures` = {`'Konumunun yakınında sürülebilir yol yok'`, `'Kurallara uyan rota bulunamadı'`, `'Başlangıç yolu şu kurallarla sürülemiyor'`} içindeyse çıkış denenir, o da yoksa ilk hata atılır.
- Koşul: `from`'a 60 m içindeki en yakın `walkable` kenar **yaya sınıfı** olmalı; 60 m içindeki süzgeçsiz en yakın kenar araç yoluysa ve `dist + 3 < yaya kenarın dist`'i ise denenmez (araç yolunun üstünden "yürü" denmez).
- Uzunlukla Dijkstra (`walkable`, ≤ `startWalkMaxM = 400` m); komşusunda `rideable` kenar olan düğüm "çıkış"tır (genişletilmez); en çok 6 çıkış. Her çıkıştan normal bacak (`startOffsetS + walkS`, `walkS = d·1.2/walkMps`); toplam `leg.cost + combine(walkS, 0)` en düşük olan seçilir.
- Sonuç: `startWalk` (konumdan çıkışa çizgi), `startWalkM`, `timeS += startWalkM·1.2/walkMps` (park cezası yok), `cost` = toplam, uyarıların başına `'Önce yaya yolundan en yakın sürülebilir yola yürü'`.

### 3.9 Çıktı modeli (`route.dart`)

- `RouteLeg`: `ride` (yükseklikli nokta listesi), `walk` (park → kapı), `startWalk`, `rideM`, `walkM`, `startWalkM`, `timeS`, `energyWh`, `climbM`, `descentM` (ride boyunca sonlu düşüşler), `wrongWayM`, `cost`, `streets`, `destination`, `warnings`, `quality` (rideM > 0 değilse 1), `wrongWayRisk` (ters yön yoksa 0), `segs`, `turns`, `pushM`; `parkPoint` = son ride noktası (yoksa hedef); `withWarnings`.
- `Plan(mode, order, legs)`: `rideM`, `walkM` (+ `startWalkM`), `timeS`, `energyWh`, `climbM`, `descentM`, `wrongWayM` toplamları; `warnings` bacakların sırasıyla; `quality` ve `wrongWayRisk` ağırlıklı ortalama.
- `progressAlong(line, p)`: yerel izdüşümde en yakın parça; ilerleme = önceki parçaların haversine toplamı + `t·parça`.
- `pushedRuns(leg)`: `walk = true` parçaların `[startM, startM + lenM]` aralıkları (aralarında < 0.5 m boşluk birleşir) ride çizgisinden haversine mesafesiyle kesilir (uçlar ara değer) → haritada kesikli çizim (Bölüm 6/7).

### 3.10 Planlayıcı (`computePlans`, planner.dart)

- `RideMode.values` sırasıyla (`battery`, `time`) her mod için plan; `RoutingException` → ilk hata saklanır; plan yoksa `error = ilk hata ?? 'Rota bulunamadı'`, varsa `error = null`. `RoutingException` dışındaki hatalar yukarı atılır.
- Mod başına: `base = _planWithScale(0)`; `surface` boşsa base (WEEM 1.0'da hep böyle). Değilse `smooth = _planWithScale(15)` (`kQualitySeekScale`); `smooth.rideM ≤ base.rideM + 200` (`kQualityDetourM`) **ve** dönüş sayısı `≤ base + 2` (`kQualityExtraTurns`) ise smooth, değilse base; smooth hata verirse base.
- `_planWithScale`: önce `avoidBad = true`; `RoutingException` → `avoidBad = false` ile tekrar ve her bacağın uyarılarının sonuna `'Kötü yolları tamamen dışlayan bağlantı yok; kalite puanı düşük yol gerekebilir'`. Kara liste, özel alan, "yol yok", eğim gevşetilmez.
- `_bestOrder`: noktalar `[origin, ...stops]`; `fixedOrder` ya da durak > 4 → verilen sıra; değilse bütün permütasyonlar (özyinelemeli, ilk eleman sırasıyla; 4 durak → 24). Bacak: `reverseHours` boşsa önbellek anahtarı `i·100 + j` (başlangıç ofseti 0), doluysa önbelleksiz ve `startOffsetS = önceki bacakların timeS toplamı`; `heading` yalnız ilk noktadan çıkan bacakta. Toplam `cost` en küçük sıra (eşitlikte ilk); hiçbiri yoksa son hata (yoksa `'Rota bulunamadı'`). `order` 0 tabanlı durak indeksleri.

### 3.11 Arka plan işçisi (`RouteWorker`) → WEEM `RouteEngine`

- YolGo: tek izole; graf yalnız ilk istekte ya da graf nesnesi değişince kopyalanır; istekler sırayla; yanıt zaman aşımı 25 sn (testte 4 sn), açılış 10 sn; zaman aşımında işçi öldürülür, bekleyen istekler `RouteWorkerTimeout` alır; çöken işçi bekleyenleri hemen hatayla bitirir, sonraki istek yeni işçi açar; `dispose` sonrası istek `StateError`. Çağıranlar: `NavController` (kendi işçisi) ve Y19 veri seti (ayrı işçi).
- **WEEM:** `logic/map` `route.RouteEngine` (coroutine): graf paylaşılan değişmez nesne (kopya yok); hesap `Dispatchers.Default` üstünde tek sıralı kuyrukta; 25 sn zaman aşımı → `RouteTimeout` (mesaj aynı), kuyruk kilitlenmez. A* döngüsü askıya alınmaz; iptal için yönlendirici her 1.024 yığın çekişinde ve planlayıcı her bacakta bir iptal bayrağına bakar (`ensureActive`). Yeni istek geldiğinde eskisi iptal edilebilir (YolGo sonucu zaten atıyordu; görünen davranış aynı). Çağıran başına ayrı motor ya da paylaşılan motor serbest; sonuç aynı.
- Hata eşlemesi (Bölüm 7'de ekran): `RoutingException` → mesajın kendisi (dil çevirisiyle), zaman aşımı → `'Rota hesaplaması çok uzun sürdü; tekrar deneniyor'`, diğer motor hatası → `'Rota hesaplanamadı; tekrar dene'`.

### 3.12 Kullanıcıya görünen metinler (TR → AZ; YolGo `trMsg` sözlüğü `i18n.dart:30–89` ile aynı sonuç)

| TR | AZ |
|---|---|
| Konum veya hedef indirilen yol haritasının dışında | Məkan və ya ünvan yüklənmiş yol xəritəsinin xaricindədir |
| Hedef yakınında erişilebilir yol yok; pini yol girişine taşı | Ünvanın yaxınlığında giriş yolu yoxdur; işarəni yolun girişinə çək |
| Konumunun yakınında sürülebilir yol yok | Yaxınlığında sürülə bilən yol yoxdur |
| Hedefe bağlı yol bulunamadı; yol olmayan yerden geçiş çizilmedi | Ünvana bağlı yol tapılmadı; yol olmayan yerdən keçid çəkilmədi |
| Hedefe yürüyüş mesafesinde bağlı araç yolu bulunamadı | Ünvana piyada məsafəsində bağlı nəqliyyat yolu tapılmadı |
| Başlangıç yolu şu kurallarla sürülemiyor | Olduğun yol bu qaydalarla sürülə bilmir |
| Kurallara uyan rota bulunamadı | Qaydalara uyğun marşrut tapılmadı |
| Rota bulunamadı | Marşrut tapılmadı |
| Rotada yaklaşık N m yaya yolu: scooter'ı it | Marşrutda təxminən N m piyada yolu: skuteri it |
| Rota erişilebilir yolda biter; kapı girişi yaklaşık N m uzakta | Marşrut giriş yolunda bitir; qapı təxminən N m uzaqdadır |
| Önce yaya yolundan en yakın sürülebilir yola yürü | Əvvəlcə piyada yolu ilə ən yaxın sürülə bilən yola piyada get |
| Kötü yolları tamamen dışlayan bağlantı yok; kalite puanı düşük yol gerekebilir | Pis yolları tam kənarda saxlayan marşrut yoxdur; zəif keyfiyyətli yol lazım ola bilər |
| Rota hesaplaması çok uzun sürdü; tekrar deneniyor | Marşrut hesabı çox uzun çəkdi; yenidən yoxlanılır |
| Rota hesaplanamadı; tekrar dene | Marşrut hesablanmadı; yenidən yoxla |

Motor TR mesaj üretir (testler TR karşılaştırır); ekranda dil çevrilir. WEEM: motor hata **kodu** + sayı üretir (`RouteError.START_NOT_RIDEABLE` …), metin `:map` dil tablosundan; TR/AZ çıktısı yukarıdakiyle harfiyen aynı. Sözlükteki `'Hedef indirilen harita bölgesinin dışında'`, `'Kara liste ya da aşırı eğimli bir sokak kullanıldı (başka yol yok)'`, `'Eğim limiti aşıldı (başka yol yok)'` 1.11'de üretilmiyor (taşınmaz).

### 3.13 Performans

- Risk (YolGo `AI_HANDOFF` F6.3, plan Bölüm 5 "A* performansı için test"): itme kipiyle bütün yaya kenarları aramaya girdi; sezgisel `walkRadius + 300 m` (450 m) içinde sıfır, dışında zayıf (`cMin` en ucuz kenar maliyeti); ayrıca her istek `CostModel`'i bütün kenarlar için yeniden hesaplar (O(E); mod başına 1–2 kez, `avoidBad` düşüşünde 2 kez daha, yaya çıkışında çıkış başına bacak). Sahada ölçülmedi.
- **WEEM (A6.4):** (1) yönlendiriciye sayaç (çekilen düğüm, değerlendirilen kenar) — sayılar Dart ile aynı girdide aynı olmalı (deterministik karşılaştırma); (2) `RouterPerfTest`: sentetik ızgara (ör. 150 × 150 düğüm, kenarların %40'ı yaya, 3–8 km bacak) — çekilen düğüm üst sınırı ve süre kaydı (CI'da süre eşiği yok, yalnız rapor); (3) A6.4 sonunda gerçek `graph_wide.bin` (CI `MapBuild` çıktısı) üstünde 20 tipik Bakü bacağı: ortalama/en kötü süre ve `CostModel` kurulum süresi belgeye (bu bölüm) yazılır; hedef: tipik bacak telefonda < 1 sn, en kötü < 25 sn zaman aşımının çok altında. Hızlandırma (ör. `CostModel` önbelleği, daha sıkı sezgisel) yalnız aynı rotayı verirse.
- **A6.4 ölçümü (19 Ekim; JVM 21, Xeon 2,1 GHz, tek iş parçacığı; telefonda ölçülmedi):**
  - **Gerçek Bakü grafı:** YolGo CI 78. koşu (`870ab6e`, 16 Ekim) kaydı: OSM özeti 93.307 yol · 536.055 düğüm → `wide: 204.633 kenar`, harita klasörü 26 MB. Dosya bu ortama alınamadı (Geofabrik ve Overpass 403, APK özel depoda) → ölçüm **aynı ölçekte sentetik şehirde**: `RouterPerfTest` `WEEM_PERF_GRID=330` (108.794 düğüm, 234.983 kenar, %36 yaya, %15 tek yön, tepeler). Gerçek grafta 20 tipik bacak ilk APK işinden sonra (ya da telefonda) yapılacak.
  - **Sayaçlar (1):** `RouteProbe` (bacak, yığın çekişi, gevşetilen kenar); YolGo'yla birebirliği `RouterGoldenTest` doğrular (3.17).
  - **Kurulum:** `GraphTables` (graf başına bir kez: dönüş açıları, ön kalite, way dizini) ~100 ms; `CostModel` medyan 15–20 ms (önbellekten önce 118–145 ms, ~%75'i açılar). 3–8 km'lik 20 bacak: ortalama 4–7 ms, medyan 4–6 ms, en kötü 8–24 ms; çekiş / düğüm ≤ 0,1. İki modlu tek duraklı plan ≈ 2 × (kurulum + bacak) ≈ 50 ms.
  - **En kötü durumlar:** kopuk hedefe bacak (bağlı bileşen baştan sona taranır) 85–134 ms, 153 bin çekiş; dört duraktan biri erişilemeyen plan: YolGo düzeniyle **20,1 s** (300 bacak denemesi, 41,6 milyon çekiş; her sıra ve her mod hatalı bacağı yeniden hesaplar) → başarısız bacak önbelleği 4,4 s → iki mod ortak **2,1 s** (58 deneme, 4,3 milyon çekiş). Sonuçlar aynı (3.17).
  - **CI ölçeği (2):** `RouterPerfTest` 150 × 150 (22.484 düğüm, 48.346 kenar): kurulum ~5 ms, bacak ortalaması 2–3 ms; üst sınır: 3–8 km bacakta çekiş / düğüm < 1,5 (ölçülen 0,7); süre eşiği yok, rapor `logic/map/build/reports/route-perf-<n>.txt`.
  - **Telefon tahmini (doğrulanmadı):** masaüstünün 3–5 katı → tipik plan < 0,3 s, en kötü plan ~6–11 s (25 s zaman aşımının altında). İlk rota `GraphTables`'ı kurar (~0,3–0,5 s); harita yüklenince önceden kurmak A6.5/A6.6'da düşünülebilir.

### 3.14 Testler

| Dosya | Sayı | Test adları → neyi doğrular (değerler) | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| YT `router_test` | 38 | aşağıdaki gruplar | `logic/map/.../route/*Test.kt` | A6.2a → A6.4 | 1 test Bölüm 2.13'te (graf dosyası) |
| ↳ temel kurallar | 18 | özel/kurum girişi onaylı olsa da yok (800 m); servis cebi yok (800); 70 m içerideki pin yol üstünde biter (park noktası < 2 m, uyarı var, `walk` boş); harita dışı hedef → "haritasının dışında"; yürüme limitini aşan bağlı hedef → "yürüyüş mesafesinde"; düz yolda kısa (400, `walk` boş); yokuş %12.5: ZAMAN tepeden 400, BATARYA dolambaç 800 ve daha az Wh, daha uzun süre; eğim limiti %10 → 800; tek yön onaysız 800 / onaylı 400 ve `wrongWayM` 400; ana caddede onay işe yaramaz (800); kara liste 800; aşırı eğimli yokuşta 800 / inişte 400 / işaretsiz 400; yön belirsiz tepe → 800; yol olmayan yere kuş uçuşu yok (hata); alternatif yoksa kara liste gevşemez (plan yok, hata var); son yürüyüş bağlı yaya yolunu izler (`walkM` 120); yakın ama bağlantısız yaya yoluna atlamaz; "yol yok" işareti son yürüyüşte de geçilmez | `route.RouterTest` | A6.2a → A6.4 | fixture: `_graph(hill, ramp, directTags, directReversed)`, `footGraph(connected)` (yerel metre → enlem/boylam, 40.40/49.85) aynen Kotlin'e |
| ↳ yaya bölgesinden çıkış | 3 | yürür sonra sürer (`startWalkM` 250, `rideM` 400, ilk uyarı metni); kopuk servis yolu varken de çözülür; yaya yolu yoksa hata aynen, normal başlangıçta `startWalk` boş | `route.StartWalkOutTest` | A6.2a → A6.4 | `plazaGraph(stub)` |
| ↳ saat izni | 1 | 23:00–00:00 Bakü: 18:30Z yasak (800), 19:00Z ters yön 400, 19:59:50Z geçiş bitmeden kapanır (ters yön 0) | `route.RouterTest` | A6.2a → A6.4 | `departure` UTC |
| ↳ geometri | 1 | rampa inişte `climbM` ≈ 0, `descentM` ≈ 20 | `route.RouterTest` | A6.2a → A6.4 | — |
| ↳ planlayıcı | 1 | çifte sipariş: 2 bacak, `order` 2 | `route.PlannerTest` | A6.2a → A6.4 | — |
| ↳ öğrenilmiş kötü yol (Y12) | 1 | `learned {100: (0.2, 1.0, 25)}` → iki modda 800, way 100 hiç kullanılmaz | `route.PlannerTest` | A6.2a → A6.4 (A6.11 bağlar) | girdi doğrudan verilir |
| ↳ işçi | 3 | sonraki sorgu ve yeni harita kullanılır (400 → kara listeyle 800 → `access=private` yeni haritayla 800); çöken işçi yenilenir, sonraki istek < 4 sn; kapatılan motor iş kabul etmez (`StateError`) | `route.RouteEngineTest` | A6.2a → A6.4 | "çökme" → iptal/istisna benzetimi; coroutine test dağıtıcısı |
| ↳ kötü nokta (Ses−) | 1 | tek nokta yalnız ±25 m (400, uyarı yok); farklı günlerde 15 m içinde iki nokta doğrular → 800; aynı gün iki basış doğrulamaz (400); silinmiş işaret etkisiz | `route.BadSpotCostTest` | A6.2a → A6.4 | `SurfaceMark` alt kümesi; işaret deposu Bölüm 9 kararına bağlı |
| ↳ cadde oranı | 1 | `streetRatio`: tek nokta 50 m (%13), ölçülmüş 50 m + nokta → ≈110 m, düşük güven sayılmaz | `roads.StreetRatioTest` (Bölüm 9) | A6.2a → A6.11 | Y13 ölçüm kısmı Bölüm 9 kararına bağlı |
| ↳ ölçülmüş yüzey (Y13) | 7 | güven/yön eşleşmeli kötü parça (800/400/400); geçilebilir pürüzlü tek yol uyarıyla, "kapalı" işaret asla; 1.7.2 +200 m kuralı (400/600/400); E11 grubu 4 test (sokak puanı, +200 m/+2 dönüş, işçi taşır) | `route.SurfaceCostTest` | **sonra (Y13)** — SORU 1 | "kapalı" işaret (`_closed`) testi Y13 deposuna bağlı |
| YT `park_paths_test` 3–4 | 2 (4'ün) | park içinden rota 400 (onaysız), park yoksa/`bicycle=no` 800; park yolunda hız > 15.5 km/s | `route.ParkPathsTest` | A6.2a → A6.4 | 1–2 Bölüm 2.13 |
| YT `push_segments_test` | 5 | başka yol yoksa 60 m itilir (`pushM` 60, `rideM` 660, tek yürünen parça 300 m'de, uyarı "Rotada yaklaşık 60 m yaya yolu…", Wh > 0); itme süresi `> 60·1.2/walkMps − 15` fazla; ~260 m dolambaç varsa itilmez, ~1.860 m ise itilir; `foot=no`/merdiven → hata; `pushedRuns` tek parça ≈ 60 m, 300 m'den başlar | `route.PushSegmentsTest` | A6.2a → A6.4 | — |
| YT `route_hours_test`, `ww_green_two_way_test` | 3 + 4 | Bölüm 4.6 | `route.ReverseHoursTest`, `route.WrongWayCostTest` | A6.2a → A6.4 | — |
| YT `bikeway_e13_test` rota grubu | 6 | Bölüm 5.1 | `route.BikewayCostTest` | A6.2a → A6.4 | — |
| **Yeni** | — | `RouterPerfTest` (3.13); yığın eşitlik sırası; hata kodu → TR/AZ metin tablosu (3.12) | `route.*` | A6.2a → A6.4 | — |

### 3.15 WEEM yerleşimi

- `logic/map` `route`: `RideParams`, `RideMode`, `VehicleProfile`, `CostModel`, `RoutingException` (kodlu), `Router` (`routeLeg`, A*, `WalkingApproach`, `StartWalkOut`), `MinHeap` (aynı sift kuralları), `Planner` (`computePlans`, `PlanRequest`, `PlanResult`), `RouteLeg`/`Plan`/`RouteSeg`/`RouteTurn`/`RoutePoint`, `progressAlong`, `pushedRuns`, `RouteEngine` (coroutine, zaman aşımı, iptal), `BadSpots` (kötü nokta aralıkları; girdisi Bölüm 9). Girdi tipleri `ReverseHours` (Bölüm 4), `LearnedQuality` (Bölüm 9).
- `logic/map` `settings`: `MapSettings.paramsFor(batteryPct)` (alan tablosu Bölüm 14).
- Android `:map`: yok (saf); `RouteEngine` örneğini `:map` tek örneği tutar, `NavController` karşılığı (Bölüm 7) ve Y19 (Bölüm 13) kullanır.
- `:core` portları: rota sonucu `NavPort.route` (`RouteStatus`) Bölüm 7'de üretilir; batarya yüzdesi `ScooterBridge.batteryPct()` (≤ 15 sn taze) ya da Y9 tahmini (Bölüm 10) → `paramsFor`.
- 2 telefon: rota motoru navigasyonun olduğu telefonda (Receiver; Receiver yoksa Provider) çalışır; mesaj yok (`NavTarget` gelir, `RouteStatus` gider — Bölüm 7).

### 3.16 Farklar ve açık noktalar

- `softBlock` ölü parametre: taşınmaz. Sözlükte üretilmeyen üç metin taşınmaz (3.12).
- Eşit maliyet sırası: yığın, `LinkedHashMap` ekleme sırası, `adjStart` sırası, permütasyon sırası ve "ilk en küçük" kuralları aynen; aksi hâlde eşit rotalarda farklı yol seçilir (gölge karşılaştırmada fark görünür).
- `departure` UTC verilir; saat izni Bakü UTC+4 sabit (Bölüm 4.3). Y19 kaydı kendi kayıt anını verir.
- `heuristicScale` yalnız bisiklet yolu varsa küçülür; elle işaret eklemek bütün sorgularda sezgiyi zayıflatır (performans, sonuç aynı).
- `RouteLeg.rideM` itilen metreyi içerir; `Plan.walkM` başlangıç yürüyüşünü içerir — ekranlar (Bölüm 7) bu tanımlarla.
- **SORU 1 (koordinatöre):** Y13 (ölçülmüş yüzey, "sonra") girdileri `surface`/`surfaceQuality`/`surfaceScale` ve "geçilebilirlik: kapalı" işareti rota motoruna WEEM 1.0'da yazılsın mı? (a) Yazılmasın: girdiler yok, planlayıcı tek aday, 7 test "sonra (Y13)" (öneri; `BEKLEYEN_OZELLIKLER` kuralı); (b) Yazılsın (boşken nötr, Y13 gelince hazır), 7 test sentetik girdiyle şimdi çevrilir. Ses− kötü nokta aralıkları (`marks`) Y12 ses tuşu kararına bağlıdır (Bölüm 9): Ses− 1.11'deki gibi kalırsa kötü nokta maliyeti (3.5) ve testi WEEM 1.0'da yazılır.
### 3.17 WEEM uygulaması (A6.4, 19 Ekim)

- `logic/map` `route`: `RideParams` (+ `climbMps`, `minRideCostPerM`/`minWalkCostPerM`, `VehicleProfile.ALL/byId`, `RideMode.icon/label`), `Quality`, `ReverseHours`, `CostModel`, `BadSpots` + `SpotSpan`, `GraphTables`, `Router` (`routeLeg`, A*, `WalkingApproach`, yaya çıkışı `walkOut`; `MinHeap`), `Planner` (`computePlans`, `PlanRequest`, `PlanResult`), `Route.kt` (`RouteLeg.descentM/parkPoint/withWarnings`, `Plan.warnings/quality/wrongWayRisk`, `RouteTurn.sharpness`, `RouteGeometry.progressAlong/pushedRuns`), `RouteEngine`, `RouteMsg` + `RouteCode`, `RoutingException`, `RouteTimeout`, `RouteProbe`; `settings` `MapSettings` + `MapSettingsCodec` (14.2); Y8 `StreetPrefs*` (4.9). Ekrana bağlı değil (A6.5/A6.6).
- **İletiler:** motor kod + sayı üretir (`RouteMsg(code, meters)`, `Bilingual`); TR/AZ metin 3.12 tablosuyla harfiyen aynı (YolGo `trMsg` alt dize değişiminin sonucu). `RoutingException.message` TR (günlük ve testler), `msg` ekranda dil seçimiyle. `RouteLeg.warnings`, `Plan.warnings` ve `PlanResult.error` `RouteMsg` (YolGo `String`; iki test `.tr` ile güncellendi).
- **Birebirlik:** `CostModel` ve A* YolGo'nun satır satır karşılığı (f32 kalite ve açı dizileri, yığının eşitlik kuralları, komşuluk ve ekleme sırası, "ilk en küçük"). Doğrulama `route.RouterGoldenTest` (veri `logic/map/src/test/resources/route/golden-v1.json.gz`, üreteç `tools/route-golden/make_golden.sh <yolgo> <dart>`): YolGo Dart kaynağı Flutter'sız kum havuzunda, hesaba dokunmayan üç sayaç satırıyla; 3 rastgele şehir + 1 eşit maliyet şehri (ikili kesirli koordinat, çift yollar); 500 bacak + 135 plan, rastgele kurallarla (onay, kara liste, aşırı eğim, hayalet, öğrenilmiş kalite, ww oranı/yasağı, saatli izin, elle ters yön kesimi, elle bisiklet yolu, kötü nokta, kötüden kaçınma, mod ve parametreler). Hata metni, kenar dizisi, dönüş, sokak, uyarı, sıra ve **A* sayaçları birebir**; ondalıklar 1e-9 göreli (JVM `Math.sin/cos` ile Dart/glibc son basamakta ayrışabilir; telefonda ikisi de bionic libm). Bozulma denemesi: birleşim cezası 2,0001, yığında `<=`→`<`, aşağı inişte `<`→`<=`, gevşetmede `<`→`<=` → hepsi fark verdi.
- **Farklar (sonuç aynı):** (1) `GraphTables`: sorgudan bağımsız tablolar graf başına bir kez (3.13). (2) Planlayıcı bacak önbelleği başarısız bacağı da saklar; saatli izin yokken bir bacağın başarısı/hatası moddan bağımsız olduğundan (izinli kenar, aday, başlangıç ve bitiş teklifleri moda bakmaz) iki mod ortak; YolGo her sırada ve modda yeniden hesaplıyordu. `RouterGoldenTest` bu planlarda sayaçların YolGo'yu aşmadığını denetler. (3) Önbellek anahtarı çakışmasız (YolGo `i·100 + j`, 100'den çok durakta çakışırdı). (4) Bir `computePlans` bütün denemelerde tek kalkış anı kullanır (`departure` null iken YolGo her `CostModel`'de ayrı `now` alırdı). (5) `heading` yokken ikinci (yönsüz) `nearestEdge` çağrısı atlanır (aynı sorgu). (6) `RouteEngine`: tek sıralı kuyruk `Dispatchers.Default.limitedParallelism(1)`, zaman aşımı bekleme dahil istek başına; YolGo zaman aşımında işçiyi öldürüp sıradakileri de düşürürdü, burada yalnız takılan istek iptal edilir (A* 1.024 çekişte bir, planlayıcı her bacakta `ensureActive`); kapatma ve `debugKillWorker` bekleyenleri `IllegalStateException` ile bitirir.
- **Taşınmayanlar:** ölü `softBlock`; Y13 yüzey girdileri (`surface`, `surfaceQuality`, `surfaceScale`, ölçülmüş yüzey adayı) ve "geçilebilirlik: kapalı" işaret kuralı (`_closed`) — SORU 1 varsayılanı (a); kötü nokta kuralı (`isBadSpot`) `access ≠ closed` ister, Ses− işaretleri (`rough`) etkilidir. 7 Y13 testi "sonra (Y13)".
- **Testler:** A6.2a `route` + `settings` 64/64 yeşil; yeni `StreetPrefsTest` 7, `RouteEngineTest` zaman aşımı 1, `RouterGoldenTest` 3, `RouterPerfTest` 1 (`TEST_ENVANTERI` "A6.4 kod durumu"). Cihazda ve gerçek Bakü grafında doğrulanmadı.
<!-- END sec:3 -->

<!-- BEGIN sec:4 -->
## 4. Y8 — Sokak kararları (A6.4)

### 4.1 Kaynak ve kapsam

| Dosya (YG) | Satır | İçerik |
|---|---|---|
| `lib/routing/street_prefs.dart` | 1–146 | `StreetPrefs` 10–24, `StreetList` 26, `StreetPrefsController` 31–146 (göç 43–51, okuma 52–70, yazma 73–82, işlemler 87–145) |
| `lib/routing/route_hours.dart` | 1–31 | `ReverseHours` |
| `lib/ui/road_hours.dart` | 1–66 | "Saatli çift yön izni" sayfası |
| `lib/ui/settings_screen.dart` | 236–379 | Ayarlar → Yollarım → "Sokak bilgileri" sayıları ve listeler (`_streetList`) |
| başvuru | — | `lib/nav/nav_controller.dart:575–740` (ses tuşu → sokak kararı, öneri; ajan N/Q), `:806–827` (ters yön önerisi), `lib/ui/nav_screen.dart:219–300` (uzun basış menüsü; ajan N), `lib/roads/road_controller.dart:226–260` ("Sorun yok" → onay; ajan Q, Bölüm 9), `lib/routing/router.dart` (kurallar Bölüm 3.4–3.5) |

Bulgu: Y8 için ham bulgu yoktu (yalnız `Y_routing.md` §Y3'te girdi adları); bu bölüm kaynaktan çıkarıldı.

### 4.2 Model, işlemler, kalıcılık (`StreetPrefs`)

- Üç OSM way id kümesi: `approved` ("iki yönlü onaylı": ana cadde olmayan sokakta ters yön serbest), `blocked` ("Kötü Yol"/tamamen kapalı: sürüş ve yürüyüş yok), `steep` ("aşırı eğimli": yokuş yukarı yok, iniş serbest); `names` (way → ad, liste ekranı için); `reverseHours` (way → saat aralığı; Bölüm 4.3).
- İşlemler (her biri hemen kaydeder):

  | İşlem | approved | blocked | steep | names | reverseHours |
  |---|---|---|---|---|---|
  | `approve(id, ad)` | + | − (ikisi birden olamaz) | aynı | ad boş değilse yazılır | aynı |
  | `block(id, ad)` | − | + | aynı | aynı kural | aynı (saat kalır) |
  | `markSteep(id, ad)` | aynı | aynı | + | aynı kural | aynı |
  | `clearWay(id)` | − | − | − | aynı (silinmez) | − |
  | `remove(liste, id)` | yalnız o listeden; `approved`'dan çıkarınca saati de silinir | | | aynı | (yalnız `approved`) |
  | `clearAll(liste)` | o liste boşalır; `approved` boşalınca bütün saatler de | | | aynı | (yalnız `approved`) |
  | `setHours(id, h?)` | aynı | aynı | aynı | aynı | `h` geçersizse yok sayılır; `null` → silinir; değilse yazılır |

- Tercihler (YolGo `FlutterSharedPreferences`, `flutter.` önekli; WEEM `map` dosyasında öneksiz, aynı biçim):

  | Anahtar | Tip | Biçim | Okuma kuralı |
  |---|---|---|---|
  | `streets.approved`, `streets.blocked`, `streets.steep` | StringList | ondalık way id | `int.tryParse`, bozuk atlanır, küme |
  | `streets.names` | StringList | `"<id>\|<ad>"` | ilk `|` konumu > 0 olmalı; ad `|` içerebilir |
  | `streets.hours.v1` | StringList | `"<id>\|<başlangıç dk>\|<bitiş dk>"` | tam 3 alan, üçü tamsayı, `valid` olmalı |
  | `streets.blocked.legacy.v1` | StringList | eski `streets.blocked` yedeği | hiç okunmaz |
  | `streets.blocked.migrated.v2` | Bool | göç işareti | — |

  Yazma her seferinde beş anahtarı birden yazar (küme ekleme sırasıyla). Göç (1.3.1, bir kez): işaret `true` değilse eski `streets.blocked` boş değilse `streets.blocked.legacy.v1`'e kopyalanır, `streets.blocked` boşaltılır, işaret yazılır (eski Ses− kayıtları bütün caddeyi kapatıyordu ve nerede basıldığı bilinmiyordu).
- WEEM (S1): YolGo verisi aktarılmaz, liste boş başlar → göç ve `legacy`/`migrated` anahtarları yazılmaz (eski veri olamaz).

### 4.3 Saatli ters yön izni (`ReverseHours`)

- `ReverseHours(startMinute, endMinute)`; `valid` = ikisi de 0–1439 ve eşit değil. Saat **Bakü sabit UTC+4** (telefon saat diliminden bağımsız): `dakika = (an UTC + 4 sa).saat·60 + dakika`.
- `allows(an)`: geçersizse false; `start < end` → `start ≤ dk < end`; değilse gece yarısını geçer: `dk ≥ start || dk < end`.
- `allowsTrip(giriş, saniye)`: `allows(giriş)` değilse, saniye sonlu değilse ya da negatifse false; `s = 60, 120, … < saniye` her adımda `allows(giriş + s)`; son olarak `allows(giriş + ceil(saniye·1000) ms)`. Geçiş izin bitmeden tamamlanmalı.
- `label` = `"HH:MM–HH:MM"`.
- Rota motorunda (Bölüm 3.5 `allowedAt`): yalnız ters yön kenarında (`wrong = 1`) ve way'in saati varsa; an = `departure + startOffsetS + elapsed` (düğüme varış süresi), süre = kenarın (kısmi) süresi. Saat varken planlayıcı bacak önbelleğini kullanmaz (Bölüm 3.10). Onay olmadan saat tek başına izin açmaz; ana caddede ancak elle kesim izniyle (`wrongWayEdges`, Bölüm 9) ters yön varsa o kenara da saat uygulanır.

### 4.4 Kararın verildiği yerler (eylem → etki → metin)

| Yer (ekran sahibi) | Eylem | Etki | Metin TR / AZ |
|---|---|---|---|
| Sürüş, ses tuşları (Bölüm 9.5 ve 7.13) | Ses+ tek (450 ms içinde ikincisi gelmezse) | eşleşen sokak `approve`; açık ters yön önerisi varsa (ana cadde değil) önerilen sokak | "Onaylandı: $ad (iki yön)" / "Təsdiqləndi: $ad (iki tərəfli)"; yeşil, 6 sn, "Geri al" / "Geri qaytar" (öncesindeki üç liste + saat geri yüklenir) |
| 〃 | Ses+ ana caddede | yok | "Ana caddede ters yön onayı kapalı" / "Əsas küçədə əks istiqamət təsdiqi olmur" (kırmızı, titreşim) |
| 〃 | Ses− iki kez (450 ms içinde) | `markSteep` | "Aşırı eğimli: $ad — yokuş yukarı kullanılmaz" / "Çox dik: $ad — yoxuşa çıxılmayacaq"; mor, 6 sn, geri al |
| 〃 | Ses− tek | sokak kararı **değil**: ±25 m kötü nokta (Bölüm 9) | — |
| 〃 | Ses− uzun (≥ 1.000 ms) | o yerde kötü nokta yoksa ve sokak kara listede (ya da elle puanı ≤ 25) ise `remove(blocked)` (+ puan sıfırlanır) | "Kötü yol işareti kaldırıldı: $ad" / "Pis yol qeydi silindi: $ad"; hiçbiri yoksa "Burada kötü nokta işareti yok" / "Burada pis nöqtə qeydi yoxdur" |
| 〃 | eşleşen yol yok | yok | "Yol eşleşmedi — biraz daha sür" / "Yol tanınmadı — bir az da sür" (turuncu, titreşim) |
| Sürüş, ters yön tespiti (Bölüm 7; ajan N) | onaysız ara sokakta ters yön | öneri 15 sn açık (Ses+ onu onaylar) | "Ters yön: $ad — onay için Ses+" / "Əks istiqamət: $ad — təsdiq üçün Səs+" (10 sn); ana caddede "Ana caddede ters yöndesin!" / "Əsas küçədə əks istiqamətdəsən!" |
| Ters yön sorusu "Sorun yok" (Bölüm 9; ajan Q) | cevap | geçişin ve aynı kesintisiz ters yön sürüşünün sokakları `approve` (kara listedeki ve ana cadde hariç) | "Onaylandı: <adlar> (iki yön)", geri al |
| Haritada uzun basış menüsü (Bölüm 6/7; ajan N) | "Saatli çift yön izni" / "Saatla iki istiqamət icazəsi" (yalnız ana cadde **değilse**) | 4.5 sayfası | — |
| 〃 | "Bu yolu tamamen kapat" / "Bu yolu tam bağla" ↔ "Yol kapatmayı kaldır" / "Yolun bağlanmasını ləğv et" | `block` / `remove(blocked)`; rota varsa yeniden hesap | alt yazı "$ad · Ses− artık yalnız ±25 m işaretler" / "$ad · Səs− indi yalnız ±25 m qeyd edir" |
| Ayarlar → Yollarım → "Sokak bilgileri" / "Küçə məlumatları" | sayılar ve listeler | liste sayfası: başlık "Onaylı sokaklar" / "Təsdiqli küçələr", "Kötü Yol listesi" / "Pis yol siyahısı", "Aşırı eğimli sokaklar" / "Çox dik küçələr"; satır ad (yoksa "isimsiz yol" / "adsız yol") + "OSM way <id>", çöp kutusu `remove`; "Tümünü sil" / "Hamısını sil" `clearAll`; boşsa "Liste boş" / "Siyahı boşdur" | satırlar: "Onaylı (iki yön): N" / "Təsdiqli (iki tərəfli): N" — "Sürerken Ses+ → bulunduğun sokak" / "Sürərkən Səs+ → olduğun küçə"; "Tamamen kapalı yol: N" / "Tam bağlı yol: N" — "Haritada yola uzun bas → \"Bu yolu tamamen kapat\". Ses− artık yalnız ±25 m kötü nokta işaretler." / "Xəritədə yola barmağını saxla → \"Bu yolu tam bağla\". Səs− indi yalnız ±25 m pis nöqtə qeyd edir."; "Aşırı eğimli: N" / "Çox dik: N" — "Sürerken Ses− iki kez → yokuş yukarı kullanılmaz (motor sağlığı)" / "Sürərkən Səs− iki dəfə → yoxuşa çıxılmır (motoru qorumaq üçün)" |

Her kalıcı değişiklikten sonra rota açıksa `replan(reason: 'prefs')` (Bölüm 7). Olay günlüğü: `approve`/`block`/`steep` `{way}`, `undo` `{way}`, `bad_road_clear` `{way}` (Bölüm 12).

### 4.5 "Saatli çift yön izni" sayfası (`road_hours.dart`)

- Başlık sokak adı (boşsa "Sokak saatleri" / "Küçənin saatları"); açıklama "Bakü saati. Seçilen aralık dışında sokağın normal tek yön kuralı geçerli." / "Bakı vaxtı. Seçilən saatlardan kənarda küçənin adi tək istiqamət qaydası qalır."
- Anahtar "Ters yön izni saatli" / "Əks istiqamət icazəsi saatla" (kayıtlı saat varsa açık). Açıkken "Başlangıç  HH:MM" / "Başlanğıc", "Bitiş  HH:MM" / "Son" (24 saat seçici); kayıt yoksa varsayılan **23:00 – 00:00**. Eşitse kırmızı "Başlangıç ve bitiş farklı olmalı." / "Başlanğıc və son fərqli olmalıdır." ve Kaydet pasif; değilse "Bitiş daha erkense izin gece yarısını geçer." / "Son saat daha erkəndirsə, icazə gecə yarısından keçir."
- "Kaydet" / "Yadda saxla": sokak **onaylanır** (`approve`) ve saat yazılır (anahtar kapalıysa saat silinir → her saat onaylı); rota açıksa yeniden hesap.

### 4.6 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| YT `route_hours_test` | 3 | "Bakü saatleri telefonun saat diliminden bağımsızdır" (11:00–19:00: 07:00Z ve 11:00+04 evet, 06:59:59Z ve 15:00Z hayır); "gece yarısını geçen aralık ve bitişten önce çıkış" (23:00–07:00: 19:00Z evet, 02:59Z evet, 03:00Z hayır; 02:59:30Z'den 20 sn evet, 40 sn hayır; 24 sa hayır); "bozuk aralık izin açmaz" ((0,0) hayır; −1 ve 1440 geçersiz) | `route.ReverseHoursTest` | A6.2a → A6.4 | — |
| YT `ww_green_two_way_test` | 4 | onaysız ters yön kullanılmaz; onaylı + `wwRatio` yok → %10 uzun yasal yol yerine ters yön (400, `wrongWayM` 400); `wwRatio 0.1` ya da `wwAvoid` → yasal yol; ana cadde onaylı olsa da ters yok | `route.WrongWayCostTest` | A6.2a → A6.4 | `wwRatio`/`wwAvoid` doğrudan verilir (üretim Bölüm 9) |
| YT `street_prefs_migration_test` | 1 | eski bütün-cadde yasakları bir kez yedeğe taşınır, yeni kapatmalar kalır, diğer listelere dokunulmaz | — | **çevrilmez** (öneri) | S1: WEEM'de eski Ses− kara listesi olamaz, göç yazılmaz; koordinatör onayı (4.8) |
| YT `router_test` (onay, ana cadde, kara liste, aşırı eğim ×2, saat izni) | 6 | Bölüm 3.14 | `route.RouterTest` | A6.2a → A6.4 | — |
| YT `ww_clean_run_test`, `ww_no_problem_green_test` | 5 + 5 | "Sorun yok"/Ses− geçiş kararları → onay | Bölüm 9 | A6.2a → A6.11 | ajan Q |
| **Yeni** | — | `StreetPrefsTest`: işlem tablosu (4.2) satır satır; kodek: bozuk id, `\|` konumu 0, 2 alanlı saat, geçersiz saat atlanır; yazma sırası; geri al öncesi durumu aynen kurar | `route.StreetPrefsTest` | A6.2a → A6.4 | eski testi olmayan davranış (karakterizasyon) |

### 4.7 WEEM yerleşimi

- `logic/map` `route`: `StreetPrefs` (değişmez model + işlemler + `StreetPrefsCodec` tercih biçimi), `ReverseHours`, `StreetList`. Kalıcılık arayüzü `MapPrefs` (anahtar → StringList/Bool); Android `:map` `MapPrefsStore` = `SharedPreferences("map")`.
- Ekranlar `:app`: Daha fazla → Harita ayarları → "Yollarım" → "Sokak bilgileri" (sayılar, liste sayfası), uzun basış menüsü ve "Saatli çift yön izni" sayfası Harita sekmesinde (Bölüm 6/7). Toast ve "Geri al" ortak harita bildirim şeridi (Bölüm 7).
- 2 telefon: kararlar sürüşte verilir → Receiver'da (ses tuşları, harita); veri o telefonda kalır, mesaj yok. Tek telefonda aynı telefon.
- Rota motoruna girdi: `approved`, `blocked`, `steep`, `reverseHours` (Bölüm 3.3); Y19 veri seti rotası da aynı kümeleri kullanır (Bölüm 13).

### 4.8 Farklar ve açık noktalar

- `block` saati silmez, `remove(blocked)` da; saat ancak onay kaldırılınca gider (davranış korunur).
- `streets.blocked.legacy.v1` YolGo'da hiç okunmaz (yalnız yedek).
- Ses tuşu davranışının tamamı (yakalama, ayar `volumeKeys`, kötü nokta) Bölüm 9'dadır; bu bölüm yalnız sokak listelerine etkisini tanımlar.
- **SORU 2 (koordinatöre):** `street_prefs_migration_test` (1) `TEST_ENVANTERI`'nde `:map` hedefli; S1 gereği WEEM'de göç kodu anlamsız. Öneri: "çevrilmez (S1)" olarak işaretlensin; ya da göç kodu boş veride etkisiz kalacak şekilde yazılıp test çevrilsin.
### 4.9 WEEM uygulaması (A6.4, 19 Ekim)

- `logic/map` `route`: `StreetPrefs` (değişmez; kümeler ekleme sırasıyla), `StreetList`, `StreetPrefsCodec` (`load`/`save`, beş anahtar birden), `StreetPrefsStore` (`current`, `approve`, `block`, `markSteep`, `clearWay`, `remove`, `clearAll`, `setHours`; her işlem hemen yazar), `ReverseHours` (`allows`, `allowsTrip`, `label`; Bakü UTC+4 saniye aritmetiğiyle). Kalıcılık `settings.MapPrefs` (Android `MapPrefsStore`).
- S1 ve SORU 2 varsayılanı: 1.3.1 göçü ve `streets.blocked.legacy.v1` / `streets.blocked.migrated.v2` yok; `street_prefs_migration_test` çevrilmedi. Id ayrıştırma `toLongOrNull` (Dart `int.tryParse` `0x…` onaltılık ve boşluklu dizgeyi de kabul ederdi; değerleri yalnız WEEM yazdığından fark doğmaz).
- "Geri al" depoda ayrı işlem değil: çağıran (`NavController`, A6.6) YolGo gibi `clearWay` + önceki onay/kapalı/eğim + `setHours` yapar; `StreetPrefsTest` bu dizinin önceki durumu aynen kurduğunu sınar. Ekranlar (Yollarım → Sokak bilgileri, uzun basış menüsü, saatli izin sayfası) A6.5/A6.6/A6.11.
<!-- END sec:4 -->

<!-- BEGIN sec:5 -->
## 5. Y15 Bisiklet yolu katmanı, Y16 Restoran noktaları, Y21 Bölge bilgisi (A6.8)

Kaynak: Y15 `lib/routing/bikeways.dart` 1–67, `lib/ui/bikeway_layer.dart` 1–148, `lib/data/graph_builder.dart:37–45`, `lib/routing/router.dart:86–92, 332`, `lib/core/settings.dart:53–54, 140–152, 312`; Y16 `lib/data/pois.dart` 1–136, `lib/data/poi_corrections.dart` 1–76, `lib/ui/poi_editor.dart` 1–45, başvuru `lib/ui/map_view.dart:897–1100` (`displayPoisProvider`, `PoiLayer`; ekran ajan N), `lib/ui/nav_screen.dart:160–352` (uzun basış menüsü), `lib/nav/nav_controller.dart:421–431`, `lib/tracking/shift_controller.dart:599, 618`; Y21 `lib/ui/region_info.dart` 1–134, `assets/baku/` + `credits.json`, `ci/fetch_photos.py`. Ham bulgu yoktu (Y15/Y16 `Y_routing.md`'de "sırada" kalmıştı); kaynaktan çıkarıldı.

### 5.1 Y15 — Bisiklet yolları

**Etiketler** (`isBikewayTags`, E13, kullanıcı listesi 14 Ekim): `highway=cycleway`; `cycleway` ∈ {lane, track}; `cycleway=share_busway`; `cycleway:left|right|both` ∈ {lane, track}; `bicycle=designated`. Sayılmaz (test listesi): `cycleway=shared_lane|opposite`, `cycleway:right=no`, `cycleway:both=separate`, `cycleway:left=share_busway`, `bicycle=yes|permissive`; ayrıca `cycleway=right` (değer kümede yok). Bayrak `WayFlag.bikeway` (1024) graf kurulurken yazılır; dosya başlığı `GraphFeature.bikeways` (Bölüm 2.5). Yön kuralları ayrı ve değişmez (`onewayFwd`, `contraflow`); `bicycle=designated` aynı zamanda `bikeOk` (yaya yolu sürülebilir).

**Elle işaret** (`BikewayMarks`): OSM'de eksik bisiklet yolu kullanıcı tarafından **bütün OSM yolu** için işaretlenir; yalnız bu telefonda, kalıcı; rotada ve haritada OSM bayrağı gibi davranır.
- Tercih `streets.bikeway.v1` StringList, öğe `"<wayId>|<ad>"` (ad boş olabilir: `"402|"`). Okuma: `|` yoksa bütün dizge id; id ayrıştırılamazsa atlanır; ad yalnız `|` son karakter değilse. Yazma küme sırasıyla `"$w|${ad ?? ''}"`.
- `mark(way, ad)` (ad boşsa ad yazılmaz), `unmark(way)`, `isMarked`.

**Rota etkisi** (Bölüm 3.4 adım 9): yalnız izinli yönde ve ters yön **değilken** `cost ×= bikewayFactor`; süre ve enerji değişmez; ters yönü açmaz (onaylı ara sokakta ters yön serbest ama indirimsiz; ana caddede onay ters yönü açmaz). `bikewayFactor = bikewayCostPct.clamp(50, 100)/100` (varsayılan 85 → ×0.85; `fromJson` da 50–100'e kıstırır: 20 → 0.5, 140 → 1.0). Grafta bayrak ya da elle işaret varsa A* sezgisi `min(1, bikewayFactor)` ile küçülür (rota en iyi kalır).

**Harita katmanı** (`bikewayLines`): `showBikewayLayer` (varsayılan açık) ve graf varken her bisiklet yolu kenarı (OSM bayrağı ya da elle işaretli way) bir çizgi: kalınlık 4, renk `AppColors.bikeway` (koyu tema `#3DDCE8`, açık `#00838F`) %85 opak; OSM düz, elle işaretli kesikli `[10, 6]`. Tema değişince yeniden. İki harita yüzeyi de çizer (`map_view.dart:455, 486, 532`): OSM/sade yüzeyde POI katmanının üstünde, sokak kaplamasının (`streetOverlayProvider`: onaylı/kapalı/eğimli sokak renkleri) ve rotaların altında; Google yüzeyinde çizgilerin ilki (en altta; işaretler çizgilerin üstünde). Ekran Bölüm 6.

**Uzun basış menüsü satırı** (`bikewayMenuAction`): way elle işaretliyse "Bisiklet yolu işaretini kaldır" / "Velosiped yolu qeydini sil" (`unmark`); değilse kenar OSM bisiklet yoluysa satır **pasif** (`alreadyOsm`), alt yazı "$ad · OSM'de zaten bisiklet yolu" / "$ad · OSM-də artıq velosiped yoludur"; değilse "Bisiklet yolu olarak işaretle" / "Velosiped yolu kimi qeyd et", alt yazı "$ad · bütün sokak; rotada tercih edilir" / "$ad · bütün küçə; marşrutda üstünlük verilir". Basınca `toggleBikewayWithUndo`: işaretler/kaldırır, toast "Bisiklet yolu işaretlendi: $ad" / "Velosiped yolu qeyd olundu: $ad" ya da "Bisiklet yolu işareti kaldırıldı: $ad" / "Velosiped yolu qeydi silindi: $ad" (ad boşsa "isimsiz yol" / "adsız yol"), renk bisiklet, 7 sn, "Geri al" / "Geri qaytar" son işlemi tersine çevirir. Açık rota kendiliğinden yeniden hesaplanır: elle işaret kümesi ya da ayarlardan `qualityTolerancePct`, `wwMaxProblemPct`, `turnPenaltyS`, `bikewayCostPct` biri değişince `replan(reason: 'prefs')` (`nav_controller.dart:243–251`; Bölüm 7.5).

**Yollarım → Bisiklet sekmesi** (`BikewayTab`; Yollarım'ın dördüncü sekmesi "Bisiklet" / "Velosiped", `roads_screen.dart:41–48`; Yollarım ekranı Bölüm 9.8): başlık "Bisiklet yolları" / "Velosiped yolları"; not "Bakü'de OSM bisiklet yolu kaydı az; eksikleri elle işaretleyebilirsin." / "Bakıda OSM-də velosiped yolu qeydləri azdır; çatışmayanları əl ilə qeyd edə bilərsən."; yardım "Haritada sokağa uzun bas → \"Bisiklet yolu olarak işaretle\". İşaret yalnız bu telefonda kalır, OSM bisiklet yolu gibi kullanılır." / "Xəritədə küçəyə uzun bas → \"Velosiped yolu kimi qeyd et\". Qeyd yalnız bu telefonda qalır, OSM velosiped yolu kimi istifadə olunur."; graf bisiklet bilgisi taşıyorsa istatistik "OSM: N sokak · <fmtKm>" / "OSM: N küçə · …" (OSM bayraklı way sayısı ve kenar uzunlukları toplamı; `fmtKm` < 1.000 m → "N m", değilse "X.X km"); eski haritada turuncu uyarı "Kayıtlı harita bisiklet yolu bilgisi içermiyor (eski harita). Ayarlar → İlave ayarlar → Harita verisi → İndir / güncelle." / "Yaddaşdakı xəritədə velosiped yolu məlumatı yoxdur (köhnə xəritə). Tənzimləmələr → Əlavə tənzimləmələr → Xəritə məlumatları → Yüklə / yenilə."; anahtar "Haritada bisiklet yolları" / "Xəritədə velosiped yolları" (alt "Düz çizgi: OSM · kesikli: elle işaretli" / "Düz xətt: OSM · qırıq xətt: əl ilə qeyd olunan"); "Bisiklet yolu tercihi" / "Velosiped yoluna üstünlük" (alt "Rota maliyeti bu çarpanla (×1.00: tercih yok). Süre, tek yön ve ters yön kuralları değişmez." / "Marşrut xərci bu əmsalla (×1.00: üstünlük yoxdur). Vaxt, birtərəfli hərəkət və əks istiqamət qaydaları dəyişmir.", sağda `×0.85` biçimi 2 ondalık); kaydırıcı 50–100, 10 bölme (adım 5); "Elle işaretlenen bisiklet yolları" / "Əl ilə qeyd olunan velosiped yolları" listesi (boşsa "Henüz yok" / "Hələ yoxdur"; satır ad ya da "isimsiz yol", alt "OSM #<id>", çöp kutusu ipucu "İşareti kaldır" / "Qeydi sil" → `unmark`, geri al yok).

### 5.2 Y15 testleri (`bikeway_e13_test`, 18; döngüler tek sayılır)

| Grup | Sayı | Neyi doğrular | WEEM hedefi | Görev |
|---|---:|---|---|---|
| E13 etiketler | 3 | 12 etiket bileşimi (döngüyle) bayrak yazar; 8 bileşim yazmaz; `footway+bicycle=designated+oneway=yes` → `bikeOk\|onewayFwd\|bikeway`; Overpass sorgusu `cycleway` içerir + `out body`, ham JSON etiketi grafa ulaşır | `graph.BikewayTagsTest` | A6.2a → A6.3 |
| E13 harita dosyası | 2 | yeni graf bisiklet bilgisini taşır, kaydet/oku aynı, uyarı yok; başlık ofset 36 = 0 → okunur, `hasBikewayData` false, uyarı TR "İndir / güncelle" ve AZ "Yüklə / yenilə" içerir; sürüm (ofset 4) 1 → `FormatException` | `graph.RoadGraphFileTest` | A6.2a → A6.3 |
| E13 rota | 6 | `cycleway:right=lane` kenarı maliyet ×0.85, süre/Wh aynı; `cycleway=right` bisiklet değil; çarpan 1.0 etkisiz, 0.6 ×0.6; elle işaret (`{500}`) ×0.85, başka way etkisiz; bisiklet şeridi ve elle işaret ters yönü açmaz, onaylı ters yön indirimsiz, ana caddede onay açmaz; 40 m uzun bisiklet yolu ×0.85'te seçilir (480), ×0.95 ve ×1.00'da seçilmez (440), elle işaretle seçilir; motor elle işareti taşır; `heuristicScale` 1.0 / 0.85 / elle 0.85 / çarpan 1.0 iken 1.0 | `route.BikewayCostTest`, `route.RouteEngineTest` | A6.2a → A6.4 |
| E13 ayarlar, elle işaret | 2 | varsayılan 85 ve katman açık; eski ayar dosyası (`qualityTolerancePct 12`, `turnPenaltyS 8`) okunur ve yeni alanlar varsayılan; gidiş-dönüş 70/false; 20 → 0.5, 140 → 1.0; `withMaxGrade` çarpanı korur; işaret kalıcı (`"401\|Test küçəsi"`, `"402\|"`), yeniden açılışta okunur, kaldırma yazılır, `streets.approved` değişmez | `settings.MapSettingsTest`, `route.BikewayMarksTest` | A6.2a → A6.8 |
| uygulamada (yeniden rota) | 2 | işaret açık rotayı değiştirir ve geri alınca eski rota (440 → 480 → 440); ağırlık 100 yapılınca rota yeniden (480 → 440) | saf `nav` denetleyici testi (Bölüm 7.15) | A6.2a → A6.6 |
| uygulamada (menü, katman) | 2 | menü eylemi `mark`/`alreadyOsm`/`unmark`, toast metinleri ("Bisiklet yolu işaretlendi: Batı küçəsi", "… işareti kaldırıldı: …") ve "Geri al"; katman: OSM çizgi sayısı = kenar sayısı, renk %85, elle işaret kesikli, katman kapatılınca boş | saf `route.BikewayMenuTest`, `ui.BikewayLayerRulesTest` | A6.2a → A6.8 |
| uygulamada (ekran) | 1 | Bisiklet sekmesi: not, anahtar, `×0.85`, "OSM: 3 ", liste, çöp kutusu, anahtar kapatma, AZ not | `:app` Robolectric `BikewayTabTest` | A6.2b → A6.8 |

Fixture'lar aynen: `_single(tags)` (way 500, 400 m kuzey), `_parallel(east)` (batı 440 m ways 301–303, doğu 480 m ways 401–403).

### 5.3 Y16 — Restoran ve yer noktaları

**Türler** (`poiKind`; tek harf): `r` restaurant, `f` fast_food, `c` cafe (amenity); `m` `shop=mall` (AVM); `t` `shop` ∈ {convenience, supermarket, bakery, butcher} (market; doc yorumunda yok — bulgu düzeltmesi); `s` `station=subway` (metro); `l` trafik ışığı (`highway=traffic_signals` ya da `crossing=traffic_signals` ya da `highway=crossing`+`crossing:signals=yes`). Sıra: amenity önce.
- `poisFromOverpass`: etiketsiz öğe atlanır; koordinat `lat/lon` ya da `center`; ad `l` için boş, diğerleri `name:az` → `name` → `brand` → `name:en` içinden kırpılıp boşluğu teke indirilmiş ilk dolu değer; `osmRef` = `"<type>/<id>"` (id yoksa boş). Dosya biçimi ve indirme Bölüm 2.10.
- `PoiSet`: enleme göre sıralı (Dart sıralaması kararlı değil; WEEM eşit enlemde girdi sırasını korur — fark yalnız eşitlik durumunda). `within(s, w, n, e)`: ikili arama + boylam süzgeci. `nearest(tür, lat, lon, maxM = 40)`: `d = maxM/111320` derece; kutu enlem `±d`, boylam `±1.4·d`; haversine ≤ en iyi (eşitlikte **sonraki** kazanır, `<=`).
- **Düzeltmeler** (`places.local.v1`, StringList; her öğe JSON `{lat, lon, kind, name, osmRef, originalRef, originalLat, originalLon, originalKind}`; okurken `lat`/`lon` sonlu ve aralıkta olmalı, bozuk öğe atlanır): ekleme ya da ad/tür düzeltme; silme yok. `replaces(p)`: `originalRef` doluysa `p.osmRef == originalRef`; değilse orijinal konum ve tür varken aynı tür ve ≤ 1 m. Düzeltilmiş küme = harita POI'lerinden düzeltmenin yerini aldıkları çıkarılır + bütün düzeltmeler. `save(poi, original)`: aynı orijinale ait eski düzeltme (aynı nesne, aynı `osmRef`, ya da aynı tür+ad ve ≤ 1 m) değiştirilir, orijinal bilgileri ilk düzeltmeden korunur; liste sonuna eklenir.
- **Düzeltme penceresi** (`poi_editor.dart`): başlık "İşletme ekle" / "Obyekt əlavə et" ya da "İşletme adını düzelt" / "Obyektin adını düzəlt"; ad alanı (en çok 100, kelime başı büyük, "Ad"); tür seçici "Restoran", "Fast food", "Kafe", "Mağaza" (t), "AVM" / "Ticarət mərkəzi" (m) — başka tür (s, l) gelirse `r`; not "Bu düzeltme YolGo’da saklanır; harita güncellense de korunur." / "Bu düzəliş YolGo-da qalır; xəritə yenilənsə də itmir." (kesme işareti U+2019; WEEM: "WEEM’de" / "WEEM-də"); "Vazgeç" / "Ləğv et"; "Kaydet" / "Yadda saxla" (ad boşsa pasif). Yeni işletme basılan noktaya, düzeltme orijinalin konumuna yazılır.
- **Kullanım yerleri:**
  - Harita gösterimi (`displayPoisProvider` + `PoiLayer`; ekran Bölüm 6): düzeltilmiş küme + ışık işaretleri (Y11 `lightSpots`: "yok" işaretinin 25 m içindeki OSM ışığı gizlenir, "var" işareti 25 m içinde OSM ışığı yoksa eklenir). Yakınlık eşikleri (kod; dosyadaki yorum eski — bulgu düzeltmesi): katman z < 12 yok; metro z ≥ 13 (ad z ≥ 16), AVM z ≥ 15 (ad z ≥ 17), ışık z ≥ 16.5 (küçük nokta), restoran/fast food/kafe/market z ≥ 16.5 (34 px hücrede tek simge; ad z ≥ 17.6); öncelik ışık → metro → AVM → diğer; en çok 180 simge, 55 ad (çakışan ad atlanır). Simgeler: metro `#E30613` daire, beyaz "M" (30 px); AVM `#7B4DFF` yuvarlak köşeli kutu, `local_mall` (32 px); ışık `#222222` daire, `#FFB13D` `traffic` simgesi (16 px); yemek noktası `AppColors.food` daire (30 px; f `fastfood`, c `local_cafe`, t `storefront`, diğer `restaurant`).
  - Uzun basış menüsü (Bölüm 6/7): 30 m içinde `r, f, c, t, m` türleri sırayla, her bulunanda yarıçap o uzaklığa iner → en yakın işletme; r/f/c ise "Restorana git: <ad>" / "Restorana get: <ad>" (hedef restoran), değilse "Bu noktayı restoran olarak seç" / "Bu nöqtəni restoran kimi seç"; "Buraya işletme ekle" / "Bura obyekt əlavə et" ya da "İşletme adını düzelt" + ad; 25 m içindeki ışık "Bu noktada ışık yok" konumu için (Bölüm 11).
  - Vardiya hedefinin restoran sayılması (YolGo gün motoru, Bölüm 11; `nav_controller.dart:419–441`): vardiya açıkken ve hedef ev değilken, restoran bilgisi (çağrıdan ya da duraktan) yoksa ve kapı notu (`unitNote`) yoksa hedefe 8 m içinde `r/f/c` varsa restoran; sonuç `setDestination(…, restaurant)` ve durak o siparişe bağlanır.
  - Çevrimdışı arama dizini (Y6, Bölüm 8.8; `place_search.dart:63, 92`): düzeltilmiş POI adları; düzeltme değişince dizin sıfırlanır.
  - Işık ve AVM duruşu (Y11, Bölüm 11): **düzeltilmemiş** harita POI'leri — `nearest('l', 55 m)`, `nearest('m', mallRadiusM = 80 m)` (`shift_controller.dart:599, 618`; `stop_semantics.dart:5`).

### 5.4 Y21 — Bölge bilgisi

- Giriş: ayarlardaki Harita verisi bölümünde ve kurulum ekranında bölge seçicinin yanındaki ⓘ ("Bölgeler neyi kapsıyor?" / "Ərazilər nəyi əhatə edir?").
- Alt sayfa: başlık "Harita bölgeleri" / "Xəritə əraziləri"; 280 px yükseklikte OSM raster haritası (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`, retina, döndürme kapalı, `wideBox` sınırlarına 12 px payla sığdırılır); iki dikdörtgen: Böyük Bakı turuncu (%10 dolgu, 3 px kenar), Mərkəzi Bakı mavi (%18 dolgu). Açıklamalar:
  - "Merkez Bakü · ~17 × 12 km" / "Mərkəzi Bakı · …" (boyut `~W × H km`: güney kenarı boyunca doğu-batı ve batı kenarı boyunca kuzey-güney haversine km, yuvarlanır) + "Badamdar, Bayıl, Sabail, İçerişehir, Yasamal, Nesimi, Narimanov / Gençlik, Hatai (Ağ şehir), Montin, 8-ci km'nin batısı. Kuryelerin çoğu siparişi bu alanda. Gömülü yol haritası her iki seçimde de geniş alanı kapsar." / "Badamdar, Bayıl, Səbail, İçərişəhər, Yasamal, Nəsimi, Nərimanov / Gənclik, Xətai (Ağ şəhər), Montin, 8-ci km-in qərbi. Sifarişlərin çoxu bu ərazidədir. Tətbiqin yol xəritəsi hər iki seçimdə də geniş ərazini əhatə edir."
  - "Geniş Bakü · ~57 × 41 km" / "Böyük Bakı · …" + "Merkez + Binagadi, Hırdalan, Masazır, Novhanı, Maştağa, Ahmedli, Hazi Aslanov, Bakıhanov, Garaçuhur, Sulutepe, Bibiheybet. Uzak siparişlere gidiyorsan bunu seç." / "Mərkəz + Binəqədi, Xırdalan, Masazır, Novxanı, Maştağa, Əhmədli, Həzi Aslanov, Bakıxanov, Qaraçuxur, Sulutəpə, Bibiheybət. Uzaq sifarişlərə gedirsənsə bunu seç."
  - Düğmeler "Merkez" / "Mərkəz" ve "Geniş" / "Böyük": tarayıcıda `https://www.openstreetmap.org/?minlon=<w>&minlat=<s>&maxlon=<e>&maxlat=<n>&box=yes`.
  - Alt not "Her iki seçimde de geniş yol ağı kullanılır. Turuncu sınırın dışındaki hedefler için ek harita verisi gerekir." / "Hər iki seçimdə geniş yol şəbəkəsi işləyir. Narıncı sərhəddən kənardakı ünvanlar üçün əlavə xəritə lazımdır."
- Çerçeveler kod içi `Baku.centralBox` / `Baku.wideBox` (Bölüm 2.2).
- `assets/baku/` (`baku1.jpg`–`baku3.jpg`, `credits.json`): bölge ekranında **kullanılmaz**; hava ayrıntısının arka planıdır (Bölüm 10). CI'da `ci/fetch_photos.py` Wikimedia Commons'tan iner (yalnız JPEG, genişlik ≥ 1.600 ve yataydan büyük, lisans CC/PD), `credits.json` = `[{file, title, artist, license, url}]`. Mevcut üç fotoğraf: "Flame towers from Baku boulevard" (Xoncha, **CC BY-SA 4.0**), "Baku Boulevard pier" (Radosław Botev, **CC BY 3.0 pl**), "Heydar Aliyev International Conference Center Baku Azerbaijan" (Fanti Salms, **CC BY-SA 4.0**). Lisans gereği yazar + lisans + kaynak bağlantısı uygulamada gösterilmeli (YolGo: hava ayrıntısındaki ⓘ). WEEM: dosyalar ve `credits.json` aynen `:map` (ya da `:app`) asset'i; atıf gösterimi korunur.
- Testi yok → **WEEM karakterizasyon testi** (A6.2b): Robolectric/Compose `RegionInfoSheetTest` — iki dildeki başlık ve açıklamalar birebir, km etiketleri (`~W × H km` hesabı saf `geo.BBox` yardımcısında birim test: merkez `~17 × 12 km` (16.952 × 12.231), geniş `~57 × 41 km` (56.836 × 41.142); R = 6 371 000 m haversine ile hesaplandı), OSM bağlantı biçimi.

### 5.5 Veri

| YolGo | Biçim | WEEM |
|---|---|---|
| `streets.bikeway.v1` | StringList `"id\|ad"` | `map` tercih dosyasında aynı ad/biçim (S1: boş başlar) |
| `settings.v1.bikewayCostPct` (85, 50–100), `showBikewayLayer` (true) | JSON alanı | `MapSettings` (Bölüm 14) |
| `places.local.v1` | StringList JSON | `map` tercih dosyasında aynı ad/biçim |
| `files/mapdata/pois_<region>.json` | Bölüm 2.10 | aynı |
| `assets/baku/*.jpg`, `credits.json` | asset | aynı adlar |
| OSM karo sunucusu (bölge haritası) | `tile.openstreetmap.org`, `userAgentPackageName` = paket kimliği (`az.duzyol.app`) | aynı sunucu, kimlik `com.kuryelog.app` (OSM karo kullanım kuralı; Y2 MapLibre raster stiliyle ortak, Bölüm 6) |

### 5.6 Testler (Y16, Y21)

| Dosya | Sayı | Neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| YT `pois_test` | 4 | Overpass → 2 nokta (hastane yok), way merkezi, ad `name:az` önce ("Dönər"); görünür alan süzgeci (`['B']`) ve kodlama gidiş-dönüş (ilk `A`, sıralı); boş `name:az` → `brand` ("Araz"), bakkal `t`, sinyalli geçit `l`, `osmRef` "node/10" korunur; eski 4 alanlı satır okunur, `osmRef` boş | `graph.PoiSetTest` | A6.2a → A6.8 | — |
| **Yeni** | — | `PoiCorrectionsTest` (replaces kuralları, kaydet-değiştir, bozuk öğe, sıra); `PoiLayerRulesTest` (yakınlık eşikleri, öncelik, 34 px hücre, 180/55 sınırı — saf kural fonksiyonu); `RegionInfoSheetTest` (5.4) | `graph.*`, `ui.*`, `:app` Robolectric | A6.2b → A6.8 | eski testi yok (karakterizasyon) |

### 5.7 WEEM yerleşimi

- `logic/map` `graph`: `Poi`, `PoiSet`, `poiKind`, `poisFromOverpass`, `PoiCorrections` (model + `replaces` + kodek), `BikewayTags` (graf kurmada). `route`: `BikewayMarks` (model + kodek), maliyet kuralı `CostModel`'de. `ui`: `BikewayLayerRules` (hangi kenar, düz/kesikli), `PoiLayerRules` (eşikler, öncelik, seyreltme), `BikewayMenuAction`. `geo`: `BBox.sizeKmLabel` (bölge ekranı).
- Android `:map`: katman çizimi iki yüzeyde (Google Maps polyline/marker; MapLibre katmanları — Bölüm 6); `MapPrefsStore` (`streets.bikeway.v1`, `places.local.v1`).
- `:app` ekranları: Yollarım → Bisiklet sekmesi (Bölüm 9.7 sekme düzeni), POI düzeltme penceresi, bölge alt sayfası (Harita ayarları ve kurulum).
- 2 telefon: harita olan telefonda; veri telefon başına (mesaj yok).

### 5.8 Farklar ve açık noktalar

- `displayPoisProvider` yorumundaki eşikler (metro z ≥ 12, AVM z ≥ 13, restoran z ≥ 15, ışık z ≥ 16) koddakiyle uyuşmuyor; WEEM kodu esas alır (5.3).
- Y11 ışık/AVM kararı düzeltilmemiş POI'leri kullanır, harita ve menü düzeltilmişleri (YolGo davranışı; korunur).
- POI düzeltmesi silinemez (YolGo davranışı; korunur).
- Dart `List.sort` kararsız; eşit enlem ya da eşit öncelikte sıra farkı yalnız görsel/eşitlik durumunda.
- POI penceresindeki not uygulama adını içerir ("YolGo") → WEEM'de "WEEM" (metin değişikliği yalnız ad).
- **Belge tutarsızlığı (A6.1 sonunda düzeltildi, 17.5):** `OZELLIK_HARITASI.md` Y15 ve Y16 "Veri" satırları `streets.bikeway.v1` ve `places.local.v1` için "(içe aktar)" diyordu; S1 ve `VERI_SOZLESMESI.md` 5.4 "yeniden girilir". Bu bölüm S1'e uyar (aktarım yok, boş başlar). Ayrıca Y16'ya bağımlı olanlar Y6'nın yanında Y11 (ışık/AVM) ve YolGo gün motorudur (5.3).
### 5.9 A6.8 uygulama notları (20 Ekim 2026)

- **Saf (`logic/map`):** `graph.Pois`/`PoiSet` (Overpass sorgusu 2.10 aynen, tür/ad kuralları, sıkı biçim, `within` ikili arama, `nearest` kutu ± `d`/`1,4·d`, eşitlikte sonraki), `graph.PoiFix`/`PoiCorrections` (`places.local.v1`; `replaces`, `save` kuralları, bozuk öğe atlanır), `MapDownload.downloadPois` (hazır sunucu manifesti → Overpass; sessiz hata) + `MapDataController.fetchPoisLater` (güncelleme başarılıysa), `MapBuild` yer adımı (iskelet `NotImplementedError` yakalama kalktı; `MapBuildTest` yer dosyasıyla), `route.BikewayMarksStore` (`streets.bikeway.v1`, sıra korunur), `ui.BikewayMenu` (`action`, `toggleWithUndo` 7 sn, "Geri al"), `ui.BikewayLayerRules.lines` (OSM düz, elle kesikli; `#3DDCE8`/`#00838F` %85), `ui.BikewayText`, `ui.PoiLayerRules` (`withLights`: "yok" 25 m OSM ışığını gizler, "var" ışık yoksa ekler; `bounds`: merkez ± yarım ekran dp, 256 dp dünya; `select`: eşikler, öncelik, 34 dp hücre r/f/c/t, ≤180), `ui.PlaceText`/`PoiEditRules` (ad ≤100, kelime başı büyük, 5 tür), `geo.BBox.widthKm/heightKm/sizeKmLabel`.
- **Android:** `:map` `MapPois` (düzeltilmemiş `base`, düzeltmeli `corrected`/`all`, `foodNear` 8 m, `nearestBusiness` 30 m r→f→c→t→m yarıçap iner), yeni `MarkerKind.POI_*` + `MarkerBitmaps.poi/key/of` (iki yüzey aynı bit eşlem; metro `#E30613` M, AVM `#7B4DFF` A, ışık `#222222`, yemek `palette.food` R/F/C/T harfli); `:app` `BikewayTab` gövdesi (not, yardım, "OSM: N sokak · km", eski harita uyarısı, katman anahtarı, ×0,85 + isteğe bağlı kaydırıcı 50–100/5, elle işaret listesi, "İşareti kaldır"), `MapDayUi` (`DayToastBanner`, `PoiEditDialog`, `RegionInfoContent`/`RegionInfoSheet`), `MapScreen` (bisiklet çizgileri z −1, yer işaretleri `PoiLayerRules`, uzun basış satırları 5, 6, 16, 17, bisiklet, işletme düzeltme/ekleme, restorana git), ⓘ "Bölgeler neyi kapsıyor?" kurulum kartında ve Harita verisi bölümünde.
- **Bilerek ertelenen:** yer **adı etiketleri** (≥17,6 yakınlıkta ilk 55) çizilmedi — MapLibre vektör altlığında yazı tipi kaynağı (glyph) bağlı değil; işaretler harf/şekille. Bölge sayfasında 280 px OSM raster önizleme haritası yok (iki çerçevenin boyutu, açıklaması ve "Merkez/Geniş" OSM bağlantısı var). Yollarım ekranı (A6.11) olmadığından `BikewayTab` yalnız bileşen olarak hazır; ekrana A6.11'de takılır. Arama dizini düzeltme değişince `PlaceSearch` anahtarıyla kendiliğinden yenilenir.
- **Sapma:** `BikewayMenu.toggleWithUndo` `ALREADY_OSM` satırında hiçbir şey yapmaz (satır pasif); `BikewayTabState` `osmMeters` (isteğe bağlı) ve `hasBikewayData` alanları eklendi.

<!-- END sec:5 -->

<!-- BEGIN sec:6 -->
## 6. Y1 Harita ekranı ve Y2 İkinci harita görünümü (A6.5)

### 6.1 Kaynak ve kapsam

- YG `lib/main.dart` (178) ve `lib/core/day_scope.dart:8-9` (ana kabuk, pasif oturum); `lib/ui/nav_screen.dart:40-153` (yerleşim), `:155-366` (uzun basış menüsü); `map_hud_layout.dart` (29); `map_hud.dart` (arama çubuğu Bölüm 8.8, `RouteFab` Bölüm 7.11); `map_view.dart` (1178: yüzey seçimi, altlıklar, işaret akışı, katmanlar, işaretler); `google_map_surface.dart` (302); `puck_style.dart` (38); `locate_button.dart` (66); `lib/core/theme.dart` (190), `app_info.dart` (19), `lib/ui/brand.dart` (78); `setup_screen.dart` (211); `settings_screen.dart:26-92, 138-196, 418-527` (yapı); `maps_help.dart` (45); `home_picker.dart` (107) + `android_overlay/HomePickActivity.kt` (56), `HomePickContract.java`; `MainActivity.kt:99-104, 198-222, 236-258, 282-286, 299-338` (ekran açık, dosya kaydetme, `openUrl`, Play Services, yaşam döngüsü); `ci/build.sh:87-104` (Maps anahtarı); `pubspec.yaml:19-23, 44, 56-68`; `assets/fonts/` (Inter, 6 dosya, 2,0 MB); `assets/brand/` (hiçbir yerde kullanılmıyor).
- Başkalarının bölümü: takip, yaklaşma yakınlaştırması, varış odağı, rota çizimi, rota paneli ve sol göstergeler Bölüm 7; harita verisi fazları ve ekran metinleri Bölüm 2.9; sokak kararı/hayalet/ww çizgilerinin ölçüleri 9.8.7; POI verisi ve düzenleyicisi Bölüm 5 (Y16); ışık işaretleri Bölüm 11 (Y11); hava yuvarlağı Bölüm 10; hatırlatıcı ve görev kartları Bölüm 13; gün kartının içeriği (aşama satırları, İstatistik) Bölüm 11/12.
- Bulgu: Y1/Y2 için ham bulgu yoktu (`OZELLIK_HARITASI` Y1/Y2 satırları; Y2'nin testi yok → karakterizasyon testi). G2 haritadan seçme `bulgular/Y_tracking.md` §G2 satır satır doğrulandı (düzeltme 6.16).

### 6.2 Ana kabuk (`main.dart`)

- **Açılış:** yalnız dikey (`portraitUp`, :40); tercihler önyüklenir; ayrı Dart girişi `pickHomeMain` ev seçici içindir (6.11).
- **Hep yaşayanlar** (:112-128): yüzey servisi, konum, sensörler, sürüş günlüğü, veri seti, batarya, pano, yerel köprü, Rawstats evi, yalnız-nav, nav yayını; gün, yol öğrenme ve hatırlatıcı dinlenir.
- **Dil ve tema** (:138-158): `I18n.resolve(settings.lang)`; tema `light` → gündüz paleti, `auto` → telefonun gece/gündüz modu, diğer her değer (`dark` varsayılan, eski `dark-ui`) → gece paleti. Durum çubuğu saydam, simgeler zemine zıt; gezinme çubuğu zemin rengi. Tema geçişi animasyonsuz. Yerel `tr`/`az`.
- **İlk ekran** (:171-175): graf hazırsa harita ekranı; faz `checking`/`unpacking` ise açılış ekranı; aksi kurulum ekranı (6.9; fazlar Bölüm 2.9).
- **Ekranı açık tutma** (`_updateAwake`, :82-86): koşul `gün açık || rota var`; wakelock + yerel `setScreenAwake` (`FLAG_KEEP_SCREEN_ON`, `MainActivity.kt:99-104`; her `onResume`'da yeniden uygulanır, :324-330). Yeniden değerlendirme: açılışta, öne dönüşte, gün açılıp kapanınca ve `hasRoute || durak var` değişince. Ses tuşları da ancak ekran açıkken yakalanır (Bölüm 9).
- **Pasif oturum** (`day_scope.dart:8-9`; `main.dart:63-74, 93-108`): uygulama arka plana geçerken gün kapalıysa `passive.pausedAt.v2 = şimdi` (ms) yazılır ve wakelock kapanır. Açılışta ve öne dönüşte `!gün açık && pausedAt != null && şimdi − pausedAt ≥ 5 dk` ise rota silinir (`clearRoute`); anahtar her seferinde silinir. Öne dönüşte ayrıca yüzey servisi yeniden denenir (Y13, sonra).
- **WEEM:** tek Activity (`:app` `MainActivity`), Harita sekmesi `MapTab` (açılış sekmesi). Dil ve tema `:app`'in (`lang`, `theme`; `logic/app` `ThemeMode` SYSTEM/LIGHT/DARK): YolGo'nun varsayılanı `dark`, WEEM'in SYSTEM (A1.3) — fark bilerek. Pasif oturum saf `logic/map` `nav.PassiveSession` (5 dk), anahtar `passive.pausedAt.v2` (`map` dosyası); öne/arkaya geçiş `MainActivity` `onStart`/`onStop`. Ekran açık: `:app` Activity bayrağı modüllerin isteklerinin VEYA'sı — `:map` (gün açık ya da rota var) ve `:scooter` (S2 `keep_screen_on`, bugün `ScooterTab` görünümünde).

### 6.3 Harita ekranı yerleşimi (`nav_screen.dart`, `map_hud_layout.dart`)

Üç kip (`nav_screen.dart:57-153`):
1. **Varış odağı** (klavye kapalı ve odak açık; Bölüm 7.10): üstte bildirim şeridi + hatırlatıcı kartı; altta ters yön kartı + `ArrivalAddressCard`; arama, ayar, göstergeler, gün kartı, konum ve rota düğmesi yok.
2. **Yalnız harita** (haritaya tek dokunuş açar/kapatır, `_toggleMapOnly`): sağ üstte "Panelleri göster" / "Panelləri göstər" (`show-panels`, `fullscreen_exit`), altta konum düğmesi.
3. **Normal:** üstte [arama çubuğu (Bölüm 8.8) | ayar düğmesi (`open-settings`, `settings_rounded`, ipucu "Ayarlar" / "Tənzimləmələr") → Ayarlar sayfası], 16 px, hava yuvarlağı (ayar açıksa; Bölüm 10), 6 px, sol göstergeler (`ModePager`; klavye açıkken yok; Bölüm 7.11), 6 px, bildirim şeridi, hatırlatıcı kartı (Bölüm 13). Ana ekranda menü (`main-menu`) ve kayıt noktası yok. Altta gün kartı katlıysa (`shiftBarCollapsed`): puan sorusu kartı, ters yön kartı (Bölüm 9), sonra satır [solda gün kartı simgeleri | sağda konum düğmesi, 10 px, rota düğmesi]; açıksa sağa hizalı sütun: konum düğmesi, 10 px, puan sorusu, ters yön kartı, rota düğmesi, 8 px, gün kartı. En üstte görev uyarısı katmanı (Bölüm 13).

`MapHudLayout` (:4-27): harita tam ekran; üst panel `SafeArea` (alt hariç), kenar 8/6/8/0 px, yüksekliği en çok `kullanılabilir × (klavye açık ? 0,95 : 0,64)` (kullanılabilir = ekran − klavye − sistem boşlukları), taşarsa kaydırılır; alt panel yalnız klavye kapalıyken, sol/sağ/alt 8 px, en çok ekranın %58'i, alttan dolarak kaydırılır; uyarı katmanı en üstte. **Kural:** paneller yalnız çizdikleri denetimlere sahiptir, boş alan haritanındır (kaydırma ve iki parmak yakınlaştırma geçer; `HitTestBehavior.deferToChild`); uyarı engeli (`ModalBarrier`) varken harita girdi almaz.

**WEEM:** Compose'da `Modifier.verticalScroll` ve `pointerInput` boş alanda da sürüklemeyi yutar. HUD kapsayıcıları içeriği kadar yer kaplar (`wrapContentHeight` + `heightIn(max = …)`), kaydırma yalnız içerik sınırı aşınca; haritanın üstünde tam ekran saydam katman olmaz. `map_hud_gesture_test` bu kuralı sınar (6.14).

### 6.4 Harita yüzeyleri

**Ortak** (`map_view.dart:161-500`):
- **Seçim:** `mapStyle == "google"` ve derleme Google'a açıksa (`GOOGLE_MAPS_ENABLED`, 6.10) Google yüzeyi; aksi flutter_map (anahtarsız derlemede `google` → `minimal`). Yüzey değişince kamera "hazır değil" sayılır.
- **Başlangıç:** merkez = işaret ?? son konum ?? Bakü (40.4093, 49.8671); yakınlık 16,2 (`standardZoom`). Hazır olunca son konum varsa işaret oraya; Google'da rota varsa rota sığdırılır, yoksa konuma dönülür; flutter_map'te her zaman konuma dönülür.
- **Sınırlar:** flutter_map yakınlık 11–19, döndürme jesti kapalı (harita yalnız kamera yönüyle döner); Google 11–20, eğme, pusula, yakınlık düğmeleri, araç çubuğu ve "konumum" düğmesi kapalı, iç boşluk 16/120/16/240 px (HUD payı).
- **Kamera hareketi kısıtı** (`_move`, :193-209): flutter_map en sık 80 ms'de bir; Google en sık 160 ms'de bir ve önceki hareket bitmeden yenisi yok (animasyonsuz `moveCamera`). Takip, yaklaşma, odak ve yön kuralları Bölüm 7.10.
- **Konum işaretinin akışı** (`_onFix`, :306-370): doğruluk >65 m ya da geçersiz → yok sayılır. Hareket = hız ≥1,5 m/s ve yön 0–360°; dönüş = önceki yönle fark ≥25°. İşaret yön filtresi (`HeadingFilter`) yalnız hareketli, doğruluk ≤25 m ve yön doğruluğu ≤45° iken beslenir (ağırlık dönüşte 0,85, aksi 0,4), aksi sıfırlanır. Kayma süresi `clamp(0,85 × iki fix arası, 250, 1000)` ms, doğrusal ara değer. Graf varken, hareketli ve doğruluk ≤25 m ise **yola oturtma**: en yakın kenar (dönüşte 10 m ve yön toleransı 25°, aksi 15 m / 40°; "yol yok" kenarları hariç); nokta kenara taşınır; kenar yönü (iki yönden GPS'e yakın olanı) GPS yönüne <30° ise yön o olur. **İleri tahmin:** `min(hız × (dönüşte 0,3 : 0,7) sn, 15 m)` yön boyunca. >120 m sıçramada (ilk fix, tünel çıkışı) kaymadan geçilir; ilk fix'te kamera yönü bu yön olur.

**Google yüzeyi** (`google_map_surface.dart`):
- Yalnız görüntü katmanı: bütün çizgiler YolGo'nun; Map ID, Routes, Navigation ve Places SDK kullanılmaz (:54-55).
- **Stil:** renk şeması sabit açık (çalışırken değiştirmek 1.4.0'da bazı telefonlarda boş bej harita veriyordu, :253-256); koyu harita JSON stille (`googleMapStyle`, :23-49; 16 kural, zemin `#1d2330`, yazı `#8a96a8`/`#141922`, yollar `#2f3a4c`/`#3a465a`/`#4a566c`, su `#0e1626` …; aynen alınır); koyu = gece paleti ve tema `dark-ui` değil (`googleDarkMap`, :52); yakınlık <17'de `poi.business` ve `transit` gizli (YolGo kendi yerlerini çizer). Tema ya da uyumluluk değişince yüzey yeniden kurulur.
- **Çizgiler:** flutter_map çizgileri aynı genişlik ve renkle, sıra = `zIndex`; düz olmayan her desen 8 px çizgi / 6 px boşluk olur (6.16).
- **İşaretler:** yerler (kamera merkezinin 900 m içi, en yakından en çok 100; yakınlık eşiği ışık 16,5, metro 13, AVM 15, diğerleri 16,5; ışık dışındakilere ≥17,6'da ilk 35'e ad etiketi; ikonlar 3× ölçekli bit eşlemi, önbellek >250'de boşalır; zoom ≥0,4 değişince ya da merkez >100 m kayınca yenilenir), duraklar (mor iğne, bilgi "1", "2"…), varılan hedef (yeşil iğne), konum (düz, yönle döner, z 100; yön yoksa nokta, ok koşulu varsa ok, aksi koni + nokta; 6.5), doğruluk dairesi (8 < doğruluk < 80 m; dolgu `#1F4285F4`, kenar `#334285F4` 1 px). Konum çizimi en sık 120 ms'de bir. Parmak 8 px'ten fazla kayarsa "elle hareket" (Bölüm 7.10).
- `googleMapCompatibility` (API ekranında "Google Maps uyumluluk modu" / "Google Maps uyğunluq rejimi", varsayılan kapalı): Flutter platform görünümü kipi (`useAndroidViewSurface`, :99-100).

**flutter_map yüzeyi = Y2** (`_baseLayers`, `map_view.dart:625-690`; Esri kökü `https://server.arcgisonline.com/ArcGIS/rest/services/…/MapServer/tile/{z}/{y}/{x}`; User-Agent paket adı):

| Seçim (ayar metni TR / AZ) | Açık | Koyu (gece paleti, `dark-ui` hariç) | maxNative | Atıf (sol alt, 9 pt) |
|---|---|---|---|---|
| CARTO anahtarı dolu (her seçimi ezer) | `https://{a,b,c,d}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png?key=<anahtar>` | aynı yol `dark_all` | — | "© OpenStreetMap · © CARTO" |
| `minimal` "Sade · YolGo" / "Sadə · YolGo" (**varsayılan**) | `Canvas/World_Light_Gray_Base` + sade sokak katmanı | `Canvas/World_Dark_Gray_Base` + sokak katmanı | 16 | "© Esri · © OpenStreetMap" |
| `osm` "OpenStreetMap" | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | Esri Dark Gray | 19 | "© OpenStreetMap" |
| `esri` "Esri · detaylı" / "Esri · ətraflı" | `World_Street_Map` | Esri Dark Gray | 19 | "© Esri · © OpenStreetMap" |
| `sat` "Uydu" / "Peyk" | `World_Imagery` + `Reference/World_Transportation` + `Reference/World_Boundaries_and_Places` | aynı, fotoğraf %40 karartılır | 19 | "© Esri · © OpenStreetMap" |
| `google` "Google Maps" (anahtarsız derlemede seçilemez) | Google yüzeyi | JSON koyu stil | — | Google'ın kendi logosu |

- **Sade sokak katmanı** (`_StreetDetailLayer`, :930-970): yalnız `minimal` ve CARTO anahtarı yokken; yakınlık ≥16,5'te görünen alandaki graf kenarları (en çok 1 800; "yol yok" hariç): ana yol 4 px, yaya sınıfı 1,1 px, diğer 2,4 px; renk koyu temada ana `#455267` / diğer `#303E50`, açıkta `#D1D9E2` / `#E2E7ED`. Yakınlık ≥17,3'te sokak adları: OSM yolu başına bir, en çok 35, kenarın orta noktasında, 150×35 px kutular çakışmaz, 10,5 pt.
- **WEEM (S4):** ikinci görünüm MapLibre Native, iki stil: **(1) OSM raster** (`tile.openstreetmap.org`; atıf "© OpenStreetMap contributors", dokununca `https://www.openstreetmap.org/copyright`), **(2) vektör** (anahtarsız kaynak, SORU-N5). OSM karo kullanım politikası: tanıtıcı User-Agent (`WEEM/<sürüm> (personal courier navigation)`), HTTP önbellek başlıklarına uyma, toplu indirme ve önceden doldurma yok, görünür atıf; ihlalde sunucu engelleyebilir. Tek kullanıcılı kurye kullanımı bu sınırda; MapLibre'nin disk önbelleği açık kalır, çevrimdışı paket indirilmez. Vektör kaynak önerileri: (a) kendi Bakü PMTiles dosyası (OSM verisinden, uygulamayla ya da harita verisi gibi elle indirilir; çevrimdışı, sunucusuz; boyut ve MapLibre sürümünün `pmtiles://` desteği A6.5'te doğrulanır), (b) OpenFreeMap (anahtarsız ve kayıtsız barındırılan vektör karoları ve stilleri; dış sunucu). APK boyutu: MapLibre'nin yerel kütüphaneleri ABI başına ölçülür, sonuç bu bölüme yazılır (A6.5, plan S4) — **ölçüldü, 6.17:** 71,65 → 123,19 MB (+51,5 MB; arm64 `libmaplibre.so` 12,8 MB).

### 6.5 Konum işareti ve konum düğmesi

**İşaret** (`puck_style.dart`, `map_view.dart:830-898`; iki yüzey aynı ölçüler):
- Ölçüler: beyaz halka r 10, mavi nokta r 7,5 (`#1A73E8`), gölge r 11 (`#40000000`, bulanıklık 4), yön konisi r 28 ve ±30° (radyal geçiş `#4285F4` %55 → %0), çizim kutusu 60×60, ok boyu 22 (Ekim planı madde 5: Google Maps gibi küçük; 1.x'teki büyütme geri alındı).
- Ok yalnız **rota açık ve işaret yönü belli** iken (`puckShowsArrow(route, course) = route && course != null`); dururken ya da rota yokken nokta (+ yön biliniyorsa koni). Ok yolu: uç `(c.x, c.y − s/2)`, sağ `(c.x + 0,4s, c.y + s/2)`, çentik `(c.x, c.y + 0,22s)`, sol `(c.x − 0,4s, c.y + s/2)`; çizim: 1 px aşağı kaymış gölge (`#40000000`, bulanıklık 3), 3 px beyaz kenar (yuvarlak köşe), mavi dolgu; yöne göre döndürülür.
- Yön kaynağı: ok için yalnız GPS yönü (filtreli, 6.4); koni için GPS yönü ?? pusula (Bölüm 7.6), ikisi de yoksa yalnız nokta (iki yüzeyde aynı). Google'da koni bit eşlemi `#AA4285F4` → `#004285F4` (flutter_map'te %55; küçük fark, 6.16).
- Doğruluk dairesi: 8 < doğruluk < 80 m iken, metre yarıçaplı (renkler 6.4).

**Konum düğmesi** (`locate_button.dart`; `map_view.dart:211-264`):
- Kamera kipi `cameraModeOf(follow, headingUp)`: takip yoksa `free`, takipte yön-yukarıysa `followHeading`, kuzey-yukarıysa `followNorth`; başlangıç `followHeading`.
- Düğme (`smart-locate`, 64×64, daire, panel zemini, çerçeve; simge 30, mavi): `free` → `location_searching_rounded`, ipucu "Konumuma dön" / "Mövqeyimə qayıt"; `followNorth` → `my_location_rounded`, "Gidiş yönüne çevir" / "Hərəkət istiqamətinə çevir"; `followHeading` → `navigation_rounded`, "Kuzey yukarı" / "Şimal yuxarı". Simge anahtarı `locate-<kip>`.
- Basınca (`smartLocate`): takipte ama elle yakınlaştırılmışsa yalnız standart yakınlığa döner (`recenter`); aksi `locateActionFor`: `free` → konuma dön (yön tercihi korunur), `followNorth` → yön-yukarı, `followHeading` → kuzey-yukarı (kuzeye geçerken kamera hemen 0°'ye döner); yön değişiminde de takip ve otomatik yakınlık yeniden açılır.
- `recenter(resetZoom = true)`: bekleyen geri-takip iptal, takip açık, `resetZoom` ise otomatik yakınlık açık; işaret yoksa son konum, o da yoksa konum servisi başlatılır; kamera hazırsa `resetZoom ? otomatikYakınlık : şimdiki` ve yön-yukarıysa kamera yönüyle.

### 6.6 Haritadaki katmanlar ve işaretler

Çizim sırası (flutter_map, `map_view.dart:502-619`; Google'da aynı içerik çizgi ve işaret olarak):
1. Altlık (6.4) ve sade sokak katmanı.
2. **Yer katmanı** (`PoiLayer`, :972-1031). Kaynak `displayPoisProvider` (:902-918): düzeltilmiş POI kümesi (Bölüm 5) + yerel ışık işaretleri (Y11, Bölüm 11): "ışık yok" işaretinin 25 m içindeki OSM ışıkları gizlenir; 25 m içinde ışık olmayan "ışık var" işareti eklenir. Yakınlık <12'de hiçbir şey; görünen alanda öncelik ışık < metro (`s`) < AVM (`m`) < diğerleri; en çok 180 işaret, 55 ad.

| Tür | Görünür yakınlık | İşaret | Ad etiketi |
|---|---|---|---|
| metro `s` | ≥13 | 30×30 kırmızı `#E30613` daire, beyaz 2,5 kenar, "M" (w900, 15) | ≥16 |
| AVM `m` | ≥15 | 32×32 mor `#7B4DFF` yuvarlak kare (r 9), beyaz 2 kenar, `local_mall` | ≥17 |
| ışık `l` | ≥16,5 | 16×16 `#222222` (r 4), beyaz 1 kenar, sarı `#FFB13D` ışık simgesi | yok |
| restoran `r`, fast food `f`, kafe `c`, dükkân `t` | ≥16,5; ekranın 34 px ızgarasında hücre başına bir | 30×30 yemek rengi daire, beyaz 1,5 kenar; simge `f` fastfood, `c` local_cafe, `t` storefront, aksi restaurant | ≥17,6 |

   Ad: 11 pt w700, iki katlı gölge, 145×35 px kutular çakışmaz, işaretin üstünde.
3. Bisiklet yolları (Y15, Bölüm 5); sokak katmanı (`streetOverlayProvider`, :82-144: ww kesimi, hayalet, onaylı, kara liste, eğimli; ölçüler 9.8.7; Y13 yüzey çizgileri `showQualityLayer` — WEEM 1.0'da yok).
4. Öteki modun planı (soluk gri 4 px `#66A0A8B8`) ve etkin rota (Bölüm 7.11).
5. İşaretler (harita yönüyle döner): bekleme sürerken ışık (`lightAt`, Y11; 24×24, `#1B1B1B` zemin, turuncu `#FF9F1C` 3 px halka, ışık simgesi 14, 900 ms'de 0,85↔1,15 nabız); varılan hedef (`reached-pin`, 34×34, siyah zemin, yeşil 3 px halka + 12 bulanık parıltı, bayrak 18); duraklar (34×34; numara 1, 2…; ilk durak tam mor, diğerleri %60; 15 pt w800); ilk durakta yeni rota halkası (`Ripple`, 180×180, 1 600 ms, üç halka 0,18 arayla, 3 px mor, saydamlık `(1 − k) × 0,9`, yarıçap 12 → kutunun yarısı).
6. Doğruluk dairesi ve konum işareti (6.5); atıf yazısı (6.4).

### 6.7 Uzun basış menüsü (`nav_screen.dart:155-366`)

Basılan nokta için: 40 m içindeki en yakın kenar (yol adı; adsızsa "isimsiz yol" / "adsız yol"), 30 m içindeki en yakın yer (türler `r`, `f`, `c`, `t`, `m`), 25 m içindeki en yakın ışık. Alttan açılan, kaydırılabilir sayfa; satıra dokununca sayfa kapanır ve eylem çalışır. Satırlar sırayla:

| # | Koşul | Metin TR / AZ (alt yazı) | Eylem → veri bölümü |
|---|---|---|---|
| 1 | her zaman | "Buraya git" / "Bura get" | `goTo(nokta)`, kaynak `map` → 7.3 |
| 2 | her zaman | "Durak olarak ekle (çifte sipariş)" / "Ünvan kimi əlavə et (ikiqat sifariş)" | `addStop` → 7.3 |
| 3 | yakın yer `r`/`f`/`c` | "Restorana git: <ad>" / "Restorana get: <ad>" | `goTo(yer, label = ad, restaurant = true)` |
| 3' | aksi | "Bu noktayı restoran olarak seç" / "Bu nöqtəni restoran kimi seç" | `goTo(nokta, restaurant = true)` |
| 4 | her zaman | "Bu konuma günlük iş ekle" / "Bu ünvana gündəlik iş əlavə et" | görev düzenleyici → 13 |
| 5 | her zaman | "Burada kaldırımda bekliyorum" / "Burada səkidə gözləyirəm" ("Yakındaki ışıkla karıştırma" / "Yaxındakı işıqforla qarışdırma") | kaldırım işareti → 11 |
| 6 | her zaman | "Kaldırım işaretini kaldır" / "Səki işarəsini sil" | kaldırım işaretini sil → 11 |
| 7 | yol var | "Yüzey türü / geçilebilirlik" / "Səth növü / keçid" (yol adı) | yüzey işareti (Y13) — **WEEM 1.0'da satır yok** |
| 8 | yol, ana cadde değil | "Saatli çift yön izni" / "Saatla iki istiqamət icazəsi" (yol adı) | saat izni sayfası → 4.5 |
| 9 | ana cadde ve tek yön | izinliyse "Bu kesimde ters yön iznini kaldır" / "Bu hissədə əks istiqamət icazəsini sil", değilse "Bu kesimde ters yöne izin ver" / "Bu hissədə əks istiqamətə icazə ver" ("<ad> · yalnız bu kavşak arası, ana cadde" / "<ad> · yalnız bu kəsişmələr arası, əsas küçə") | ww kesimi + bildirim → 9.8.6 |
| 10 | yol | "Elle yüzey puanı · <ad>" / "Əl ilə səth balı · <ad>"; sağda 👍 "Kaliteli yol" / "Keyfiyyətli yol", 👎 "Kalitesiz yol" / "Keyfiyyətsiz yol" | puan penceresi, `rate` → 9.4.1, 9.4.2 |
| 11 | yol | cadde kötü kesim oranı ("Ölçülmüş kötü kesimler + kötü noktalar (±25 m)" / "Ölçülmüş pis hissələr + pis nöqtələr (±25 m)") | yalnız bilgi → 9.7 |
| 12 | yol | kapalıysa "Yol kapatmayı kaldır" / "Yolun bağlanmasını ləğv et", değilse "Bu yolu tamamen kapat" / "Bu yolu tam bağla" ("<ad> · Ses− artık yalnız ±25 m işaretler" / "<ad> · Səs− indi yalnız ±25 m qeyd edir") | `streets.blocked`; rota varsa `replan(prefs)` → 4.4 |
| 13 | yol | "Bisiklet yolu olarak işaretle" / "Velosiped yolu kimi qeyd et" ya da "Bisiklet yolu işaretini kaldır" / "Velosiped yolu qeydini sil"; OSM'de zaten bisiklet yoluysa pasif ("<ad> · OSM'de zaten bisiklet yolu" / "<ad> · OSM-də artıq velosiped yoludur"), aksi "<ad> · bütün sokak; rotada tercih edilir" / "<ad> · bütün küçə; marşrutda üstünlük verilir" | geri alınabilir işaret → 5 (Y15) |
| 14 | yol | "Burada yol yok" / "Burada yol yoxdur" ("<ad> · bu parça rotaya hiç girmez" / "<ad> · bu hissə marşruta heç vaxt düşməyəcək") | hayalet kenar → 9.8.5 |
| 15 | yol | "Bu sokağın tamamı yok" / "Bu küçə ümumiyyətlə yoxdur" (yol adı) | hayalet yol → 9.8.5 |
| 16 | her zaman | "Burada trafik ışığı var" / "Burada işıqfor var" | ışık var → 11 |
| 17 | her zaman | "Bu noktada ışık yok" / "Bu nöqtədə işıqfor yoxdur" | 25 m içindeki ışığın (yoksa noktanın) "yok" işareti → 11 |
| 18 | her zaman | yer yoksa "Buraya işletme ekle" / "Bura obyekt əlavə et", varsa "İşletme adını düzelt" / "Obyektin adını düzəlt" (yer adı) | POI düzenleyici → 5 (Y16) |

Satır 7–15 yalnız yol bulunduğunda (40 m). **WEEM:** koşullar ve sıra saf `ui.LongPressMenu` (girdi: nokta, kenar bilgisi, yakın yer, yakın ışık, ww izni, kapalı mı, bisiklet durumu → satır listesi); `MapTab` yalnız çizer.

### 6.8 Tema ve marka

- **Paletler** (`theme.dart:30-68`; gece / gündüz): zemin `#0A101B` / `#F3F5F8`, panel `#F20F1725` / `#F2FFFFFF`, yüzey `#101827` / `#FFFFFF`, yüksek yüzey `#1B2638` / `#E6EAF0`, çizgi `#2B384C` / `#CBD2DB`, mavi (düz/iniş) `#8BB5FF` / `#1765D1`, turuncu (hafif yokuş) `#FF9F1C` / `#D45D00`, kırmızı (dik) `#FF2D55` / `#D7002E`, yeşil (onaylı/iyi) `#8ACDB8` / `#137D65`, pembe (duraklar) `#B68CFF` / `#C0008A`, eğimli sokak `#B26BFF` / `#7B2FD0`, yemek `#FF8A3D` / `#E2580B`, yürüme `#E8F7FF` / `#16222E`, yazı `#E8F7FF` / `#0B1218`, soluk yazı `#A5B0C1` / `#55616D`, gölge `#99000000` / `#33000000`; bisiklet yolu `#3DDCE8` / `#00838F`.
- **Eğim rengi** (`forGrade`): eğim > %8 kırmızı, > %3 turuncu, aksi mavi (iniş dahil).
- **Material teması** (:109-189): Material 3, yazı tipi Inter; birincil mavi, ikincil yeşil, hata kırmızı; giriş alanı dolgulu (r 18), iletişim kutusu r 24, alt sayfa r 28 + tutamak, dolu düğme en az 48×52 (r 18), çerçeveli 48×48; uygulama çubuğu başlığı 22 pt w700; sayılarda sabit genişlikli rakam (`tabularFigures`).
- **Marka** (`brand.dart`): `BrandMark` (108'lik ızgara; lacivert geçişli zemin `#1C3157` → `#0B1428`, r 26; yeşil yol `#8ACDB8` 10 px; mavi iğne `#8BB5FF`) ve `BrandTitle` ("YolGo" + "kurye navigasyonu" / "kuryer naviqasiyası"). `assets/brand/yolgo-logo.png|svg` hiçbir yerde kullanılmıyor.
- **WEEM:** HUD panelleri ve metinler `:app` paletindedir (`logic/app` `Palette`, `PaletteContrastTest` 4,5:1; A1.3). Harita çizim renkleri (rota eğimi, duraklar, eğimli/onaylı/kara liste, yemek, yürüme, bisiklet yolu) saf `ui.MapPalette` (gece/gündüz, yukarıdaki değerler) + `forGrade`. Inter taşınmaz (sistem yazı tipi, `:app`); sayılar `fontFeatureSettings = "tnum"`. YolGo markası ve logosu taşınmaz (WEEM adı ve simgesi `:app`).

### 6.9 İlk kurulum, açılış ve ayarlar yapısı

- **Kurulum ekranı** (`setup_screen.dart:15-131`; graf yokken; zemin `#0A0E17`): marka + dil seçici TR/AZ (`lang`'a yazar); "Scooter için yokuş, batarya ve yol kalitesi bilinçli navigasyon" / "Skuter üçün yoxuşu, batareyanı və yolun keyfiyyətini nəzərə alan naviqasiya"; adım 1 "Konum izni" / "Məkan icazəsi" — "Navigasyon, sürüş kaydı ve batarya tahmini için. "Uygulama kullanılırken" yeterli." / "Naviqasiya, sürüş qeydi və batareya hesabı üçün. "Tətbiqdən istifadə edərkən" kifayətdir.", "İzin ver" / "İcazə ver" (konum servisini başlatır, izin ister); adım 2 "Bakü yol haritası" / "Bakı yol xəritəsi" (metin ve bölge seçici "Merkez Bakü" / "Mərkəzi Bakı", "Geniş Bakü" / "Böyük Bakı", ⓘ "Bölgeler neyi kapsıyor?"; Bölüm 2.9); indirme sürerken 10 px ilerleme, yüzde, aşama ve "Ekranı açık tut. Uygulamayı kapatma." / "Ekranı açıq saxla. Tətbiqi bağlama."; aksi "İndir ve başla" / "Yüklə və başla"; hata kırmızı; altta "Harita © OpenStreetMap katkıcıları · Yükseklik: SRTM" / "Xəritə © OpenStreetMap iştirakçıları · Hündürlük: SRTM".
- **Açılış ekranı** (:134-157): marka; gömülü harita açılırken "Bakü haritası hazırlanıyor…" / "Bakı xəritəsi hazırlanır…".
- **WEEM:** ayrı kurulum ekranı yok: dil ve izinler `:app` ilk açılışında (G5); graf yokken Harita sekmesi vardiya kartının altında kurulum kartını gösterir (adım 2 ve ilerleme; harita yüzeyi yok, YolGo'daki gibi), açılırken aynı kartta "Bakü haritası hazırlanıyor…".
- **Ayarlar** (`settings_screen.dart`): her satır aynı yuvarlak kart (r 20); uzun açıklamalar ⓘ simgesinde (`info-toggle`, ipucu "Bilgi" / "Məlumat"), dokununca 220 ms'de aşağı açılır; açılır gruplar oturum boyunca açık kalır (tercih değildir).

| YolGo yeri (sıra) | İçerik | Sahibi | WEEM |
|---|---|---|---|
| Ayarlar → "Yol Kalitesi Kalibrasyonu" | kalibrasyon ekranı | Y14 (sonra) | yok |
| Ayarlar → "Teklif veri seti" | veri seti ekranı | Bölüm 13 (Y19) | Harita ayarları |
| Ayarlar → "Yollarım" | Yolları görüntüle, Sürüş kayıtları, Sokak bilgileri (fiziksel ölçüm; Yollarım: kalite · ters yön · yol yok; onaylı/kapalı/eğimli listeleri; ışık mesafesi `lightMaxM`; ses tuşları `volumeKeys`) | Bölüm 9, 12, 4, 11 (fiziksel ölçüm Y13, sonra) | Harita ayarları → "Yollarım" |
| Ayarlar → "Hatırlatıcılar", "Günlük işler" | | Bölüm 13 (Y20) | Harita ayarları |
| Ayarlar → "Harita görünümü" / "Xəritənin görünüşü" | altlık seçici (6.4), ⓘ "Sade görünümde ayrıntılar yaklaşınca belirir. Google Maps bağlantı kontrolü İlave ayarlar → Geliştirici ayarları → API ve bağlantılar'da; rota ve sokak kararlarını yine YolGo hesaplar." / "Sadə görünüşdə detallar yaxınlaşanda görünür. Google Maps bağlantısı Əlavə tənzimləmələr → Tərtibatçı tənzimləmələri → API və bağlantılar-dadır; marşrutu və küçə qaydalarını yenə YolGo hesablayır." | **Bölüm 6** | Harita ayarları (seçenekler SORU-N5) |
| Ayarlar → İlave ayarlar → "Rawstats bilgileri" | Rawstats canlı değerleri; Ev konumu + Yenile | S2 (`:scooter`), G2 (`:core`) | kalkar (S7): scooter canlı değerleri Scooter sekmesinde, ev Daha fazla → Ev konumu |
| İlave ayarlar → "Hava durumu" | | Bölüm 10 | Harita ayarları |
| İlave ayarlar → Geliştirici ayarları → "Navigasyon bağlantıları" / "Naviqasiya keçidləri" ("Waze, Yandex ve konum bağlantılarını YolGo ile aç" / "Waze, Yandex və məkan keçidlərini YolGo ilə aç") | iletişim kutusu "YolGo ile aç" / "YolGo ilə aç": "Android seçim ekranında YolGo → Her zaman seç. Web bağlantıları için aşağıdaki varsayılan ayarları aç. Wolt doğrudan Waze uygulamasını çağırıyorsa bu seçim Android tarafından YolGo’a aktarılamaz; konumu Paylaş → YolGo ile gönder." / "Android seçim pəncərəsində YolGo → Həmişə seç. Veb keçidləri üçün aşağıdakı standart parametrləri aç. Wolt birbaşa Waze tətbiqini açırsa, Android bu keçidi YolGo-a ötürmür. Məkanı Paylaş → YolGo ilə göndər."; "Kapat" / "Bağla", "Android ayarlarını aç" / "Android parametrlərini aç" (`openLinkSettings`, 8.2) | Bölüm 8 | Harita ayarları; "YolGo" → "WEEM" |
| Geliştirici → "Harita verisi" | | Bölüm 2.9 | Harita ayarları |
| Geliştirici → "API ve bağlantılar" ("Maps · CARTO · Places · ORS · Open-Meteo") | | Bölüm 8.9 | Harita ayarları |
| Ayarlar sonu: "Görünüm" (Otomatik / Açık / Koyu; eski `dark-ui` Koyu görünür ve korunur), "Dil" ("Türkçe" / "Azərbaycanca" / "Otomatik"), "YolGo 1.11.0 · az.duzyol.app" | | `:app` (G3, A1.3) | Daha fazla (tek tema/dil) |

- **WEEM:** Daha fazla → "Harita ayarları" (`:app` `MapSettingsScreen`; emsal `ScooterSettingsScreen`); sıra YolGo'daki gibi, kalkan satırlar çıkar. Harita üstündeki ayar düğmesi (6.3) bu sayfayı açan kısayol olarak kalır.

### 6.10 Google Maps anahtarı ve yardım

- **Derleme** (`ci/build.sh:87-104`): CI gizli değişkeni `GOOGLE_MAPS_API_KEY` yalnız ortamdan okunur; biçim `AIza[0-9A-Za-z_-]{35}` değilse derleme "Google Maps anahtarı biçimi geçersiz" ile durur; günlüğe yalnız son 4 karakter yazılır; `res/values/google_maps.xml` → `google_maps_key` (`translatable="false"`) ve `GOOGLE_MAPS_ENABLED=true`. Değişken yoksa kaynakta boş `google_maps_key` kalır, Google seçeneği kapalıdır. Manifest `com.google.android.geo.API_KEY = @string/google_maps_key`. Değer hiçbir belgeye, günlüğe ya da dışa aktarmaya yazılmaz.
- **Durum** (`mapsStatus`, `MainActivity.kt:114-126`): anahtar `AIza` ile başlıyor mu, son 4 karakter, paket, imza SHA-1 (iki noktalı büyük onaltılık), Play Services kodu (`isGooglePlayServicesAvailable`; okunamazsa −1).
- **Yardım sayfası** (`maps_help.dart`): başlık "Google Maps"; "APK harita anahtarı" / "APK xəritə açarı": anahtarsız derleme ya da anahtar yok → "Anahtar eklenmemiş" / "Açar əlavə edilməyib", durum gelmeden "Kontrol ediliyor…" / "Yoxlanılır…", aksi `••••<son 4>`; "Google Play Services": 0 "Hazır" / "Hazırdır", −1 "Kontrol edilemedi" / "Yoxlama tamamlanmadı", diğer "Güncelleme veya etkinleştirme gerekiyor (kod N)" / "Yeniləmək və ya aktivləşdirmək lazımdır (kod N)"; açıklama "Google Cloud’da bu anahtarın projesinde Maps SDK for Android ve faturalandırma açık olmalı. Anahtar kısıtlaması Android uygulamaları olmalı; paket ve SHA-1 aşağıdaki değerlerle eşleşmeli." / "Google Cloud-da bu açarın layihəsində Maps SDK for Android və ödəniş hesabı aktiv olmalıdır. Açar Android tətbiqləri üçün olmalı, paket və SHA-1 aşağıdakı məlumatlarla uyğun gəlməlidir."; seçilebilir `Paket: <paket>` / `SHA-1: <sha1>`; "SHA-1 kopyala" / "SHA-1-i kopyala"; "Anahtar ayarlarını aç" / "Açar parametrlərini aç" (`https://console.cloud.google.com/apis/credentials`); "Maps SDK ayarlarını aç" / "Maps SDK parametrlərini aç" (`https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com`); "Tamam" / "Oldu". Bağlantılar `openUrl` ile (`ACTION_VIEW` + yeni görev; açılamazsa false).
- **WEEM:** Gradle aynı gizli değişkeni okur (CI; yerelde `local.properties`), aynı biçim denetimi ve son-4 günlüğü, `resValue("string", "google_maps_key", …)` + `BuildConfig.MAPS_ENABLED`; imza SHA-1 `GET_SIGNING_CERTIFICATES` ile. API ekranının denetimleri Bölüm 8.9.

### 6.11 Haritadan ev seçme (`home_picker.dart`, `HomePickActivity.kt`)

- **YolGo:** `HomePickActivity` (dışa açık, eylem `com.myapplab.yolgo.PICK_HOME`) önce `RESULT_CANCELED` koyar; çağıran `com.kuryelog.app` ve eylem eşleşmiyorsa kapanır; aksi dışa kapalı `HomePickMapActivity`'yi (ayrı Dart girişi `pickHomeMain`, kanal `kurye_nav/home_pick`: `load`, `attribution`, `save`, `cancel`) açar. Girdi `lat/lon` yalnız `Double`, sonlu ve aralıkta (`HomePickContract`). YolGo ayarına yazmaz, YolGo evini okumaz; dil ve tema `settings.v1`'den salt okunur.
- **Ekran:** başlık "Ev konumunu seç" / "Ev məkanını seç"; geri oku ve sistem geri tuşu = iptal; OSM karoları (`tile.openstreetmap.org`), yakınlık 17, döndürme kapalı; koyu temada karolar renk matrisiyle ters çevrilir; seçili nokta = kamera merkezi (kaydırınca değişir); uzun basış kamerayı basılan noktaya taşır; nokta çevresinde 50 m daire (birincil renk %15 dolgu, 2 px kenar); ortada sabit iğne (`location_pin` 40, 20 px yukarı); sağ altta "© OpenStreetMap contributors" (11 pt; dokununca `https://www.openstreetmap.org/copyright`). Altta "Haritayı kaydır veya uzun bas" / "Xəritəni sürüşdür və ya uzun bas", koordinat `%.6f, %.6f` ya da "—", "İptal" / "İmtina" ve "Kaydet" / "Saxla" (nokta yokken ya da kapanırken pasif). Açılış: girdi varsa o; yoksa izin `denied` ise istenir, yüksek doğrulukla en çok 8 sn konum; hata → "Konum alınamadı · Tekrar dene" / "Məkan alınmadı · Yenidən yoxla" (dokununca yeniden), İptal yine çalışır.
- **WEEM:** `:map` `HomePickScreen` (Compose; MapLibre OSM raster stili; aynı kurallar ve metinler); Daha fazla → Ev konumu → haritadan seç (`:app`, CORE.md 2) girdiyi (taslak ya da kayıtlı ev, yoksa yok) verir; dönüş `HomePickResult.parse(ok, lat, lon)` → `HomeDraft(lat, lon, accuracy = null)` (A2.2). Dışa açık Activity, `PICK_HOME`, `SHARE_HOME` yok (S7). Koyu temada ters çevirme MapLibre raster boyasıyla (`raster-brightness-min = 1`, `raster-brightness-max = 0`; görsel eşdeğerlik A6.5'te gözle denetlenir).

### 6.12 Gün kartı (`ShiftBar`) ve WEEM vardiya kartı sınırı

- **YolGo** (`shift_widgets.dart:22-174`; içerik satırları Bölüm 11/12): katlı hâl sol kenarda tek sırada üç simge — "Eve dön" / "Evə qayıt" (`shift-home`, `home_rounded`), "İstatistik" / "Statistika" (`shift-stats`, `bar_chart_rounded`), "Günü bitir" / "Günü tamamla" (`shift-end`, `stop_circle_outlined`); simge 27, dokunma yüksekliği ≥48 px; sağda tutamak. Soldan sağa ≥24 px (ya da hız >250) kaydırma veya tutamak kartı açar; açık kart yalnız bilgi + aynı üç düğme; sağdan sola kaydırma, kart dışına dokunma ya da 20 sn (`collapseAfter`) katlar. Gün/sipariş no/aşama/sıradaki sipariş/teslim sayısı/ışık beklemesi ya da rota/hesap/bekleyen hedef değişince kendiliğinden açılır. Gün kapalıyken Eve dön ve Günü bitir pasif, İstatistik açık; kart satırı "Gün kapalı · evden çıkınca otomatik başlar" / "Gün bağlıdır · evdən çıxanda avtomatik başlayır". "Günü Başlat", sipariş ve aşama düğmeleri yok (aşamalar Rawstats'tan). Onaylar: "Eve dön" — "Evine rota çizilsin mi? Aktif gün devam eder." / "Evə marşrut çəkilsin? Aktiv gün davam edir." (ev yoksa "Ev konumu Rawstats'ta ayarlanır." / "Ev ünvanı Rawstats-da təyin olunur.", onay düğmesi yok), "Vazgeç" / "Ləğv et", "Eve dön" / "Evə qayıt" (`confirm-home`) → `goHome` (7.3); "Günü bitir?" / "Günü tamamlamaq istəyirsən?" — "Kayıt durur ve günün istatistiği açılır." / "Günün qeydi tamamlanır və statistika açılır.", "Bitir" / "Tamamla". Aşama satırları: `toRestaurant` "Restorana gidiyorsun" / "Restorana gedirsən", `atRestaurant` "Restorana vardın" / "Restorana çatdın", `readyForCustomer` "Sipariş alındı" / "Sifariş götürüldü", `toCustomer` "Müşteriye gidiyorsun" / "Müştəriyə gedirsən", `atCustomer` "Müşteriye vardın" / "Müştəriyə çatdın".
- **WEEM sınırı (öneri, SORU-N7):** Harita sekmesindeki tek vardiya kartı `:app` `ShiftCard`'dır (A2.4; `ShiftPort.state`, başlat/bitir, ekran okuma uyarısı, scooter satırları). YolGo gün kartı ayrı kart olarak gelmez: "Günü bitir" = `ShiftCard`'ın bitir düğmesi (vardiya = gün, G1); "Eve dön" ve "İstatistik" `:map`'in `ShiftCard`'a eklediği eylemlerdir (`MapShiftActions`; ev yoksa metin "Ev konumu ayarlanmamış — Daha fazla → Ev konumu", 7.17); aşama satırı `OrderEvents.stages`'ten (`:orders` metinleriyle); katlanma kuralı (20 sn, kaydırma, değişiklikte açılma, konum/rota düğmelerinin yanına inmesi) Harita sekmesinin alt panelinde `ShiftCard`'ın katlı hâli olarak.

### 6.13 Veri

| Ad | Tür, varsayılan | YG | WEEM |
|---|---|---|---|
| `settings.v1` → `mapStyle` | String, `minimal`; `minimal` \| `osm` \| `esri` \| `sat` \| `google` | `settings.dart:45, 317-318` | `MapSettings.mapStyle`; değerler ve varsayılan SORU-N5 |
| `settings.v1` → `cartoKey` | String, `""` | `settings.dart:36` | yalnız CARTO kalırsa (SORU-N5); değer belgeye yazılmaz |
| `settings.v1` → `googleMapCompatibility` | bool, false | `settings.dart:46` | girmez (Flutter platform görünümü kipi) |
| `settings.v1` → `mapDesignV1` | bool, her yazımda true (eski `osm` varsayılanını `minimal`'e çeviren göç işareti) | `settings.dart:273, 317` | girmez (S1: veri aktarılmaz) |
| `settings.v1` → `theme`, `lang` | `dark` (`dark` \| `dark-ui` \| `light` \| `auto`), `""` | `settings.dart:37, 81` | `:app` `theme`, `lang` (`kuryelog`); `MapSettings`'te yok |
| `settings.v1` → `showQualityLayer` | bool, false | `settings.dart:41` | Bölüm 9 (Y13 çizimi; 1.0'da gizli) |
| `passive.pausedAt.v2` | Int ms | `main.dart:69, 105` | `map` dosyası, aynı ad (`Long`) |
| Kanal `kurye_nav/native`: `setScreenAwake`, `mapsStatus`, `openUrl`, `startCompass`/`stopCompass`, `saveFile` | Flutter köprüsü | `MainActivity.kt` | çevrilmez (doğrudan Kotlin) |
| `saveFile` | `ACTION_CREATE_DOCUMENT` (istek 702), kaynak `filesDir`/`cacheDir`/`app_flutter` altında olmalı; MIME `.jsonl` → `text/plain`, aksi `application/json`; varsayılan ad `DuzYol.json`; hatalar "Dosya kaydedilemiyor", "Belge seçicisi açılamadı", "Dosya kaydedilemedi" (yalnız TR); tek çağıran sürüş kayıtları (Y18) | `MainActivity.kt:198-222, 299-316`; `clipboard_sniffer.dart:198-200` | `:app` kayıt dışa aktarma (Bölüm 12; iki dilli metinler) |
| Kanal `kurye_nav/home_pick`, eylem `com.myapplab.yolgo.PICK_HOME`, `HomePickActivity`, `HomePickMapActivity` | | manifest :121-128 | kalkar (S7); 6.11 |
| Maps anahtarı | `@string/google_maps_key` ← CI `GOOGLE_MAPS_API_KEY`; manifest `com.google.android.geo.API_KEY` | 6.10 | aynı düzen (Gradle) |
| Karo uç noktaları | OSM, Esri (6 hizmet), CARTO (Voyager, Dark Matter) | 6.4 | 6.4 WEEM (S4) |

### 6.14 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| YT `hud_layout_test` | 2 (1. tanım 320/390 px × açık/koyu: 4 çalışma) | harita kontrolleri taşmaz; gün kartı simgelerle başlar, kaydırınca açılır, "Restorana vardın", `add-second-order`/`order-next` yok, üç düğme ≥48 px; hava yuvarlağı kapalıyken yalnız "23°" ve `compactSize`, dokununca "Şimdi" + dört saat + yenile; dışarı dokunuş kartı katlar; aşama ilerleyince "Müşteriye gidiyorsun" | `:app` Robolectric `MapTabLayoutTest` | A6.2b → A6.5 | 2. test (hava saat ayrıntısı, %40 klavyeli sayfa) Bölüm 10; Inter yerine sistem yazı tipi; altın görüntüler (`HUD_CAPTURE`) çevrilmez |
| YT `locate_button_test` | 3 | kip eşlemesi (4 durum); düğme eylemi ve elle kaydırma → bas → bas → bas dizisi (`followHeading` → `followNorth` → `followHeading`); simgeler kipe göre, dokunuş geri çağrısı, 64×64 | `ui.LocateButtonTest` (2) + `:app` Robolectric `SmartLocateButtonTest` (1) | A6.2b → A6.5 | |
| YT `map_hud_gesture_test` | 2 (1. tanım 320/390 × klavye: 4 çalışma) | ortadaki boş alanda yatay/dikey sürükleme ve sağa hizalı düğmenin solundaki saydam alan haritaya gider; denetime dokunuş haritaya gitmez; klavye açıkken alt panel yok; iki parmak ölçek >1,2; uzun üst panel kaydırılır ve harita girdi almaz; görev engeli varken harita girdi almaz | `:app` Robolectric `MapHudLayoutTest` | A6.2b → A6.5 | Compose kuralı 6.3; harita yerine sayaçlı `pointerInput` kutusu |
| YT `puck_arrow_test` | 3 (3. tanım 3 durumla) | ok yalnız rota + yön varken; ok boyu 20–24 ve ≤ kutu; ok yolu yüksekliği = boy, ucu yukarıda, genişlik < boy; `puck-arrow` / `puck-dot` anahtarları | `ui.PuckStyleTest` (2) + `:map` Robolectric `LocationPuckTest` (1) | A6.2b → A6.5 | ok noktaları saf `PuckStyle.arrowPoints` |
| YT `yolgo_sade_2_test` | 10 (çalışmada 16) | widget (5 tanım, 11 çalışma): katlı kartta yalnız üç düğme (ipuçları, tek sıra, sol kenar <20 px), ışık/oynat simgesi ve 24 kaldırılmış metin yok; gün kapalıyken Eve dön ve Günü bitir pasif, İstatistik açık, "otomatik" satırı; açık kart dört aşama metni, `order-next`/`start-order`/`add-second-order` yok; rota varken "#1" ve menü yok; yeni siparişte açılır, 21 sn sonra katlı. Motor (4): restoran adresi paylaşmak sipariş açmaz; hedef yazılır, aşama geri sarılmaz, müşteri bacağı kendiliğinden başlamaz; hedefte 200 sn duruş soru üretmez; eski kayıt alanları okunur, yazılmaz. Ayar (1): eski `followCourierStages: false` true okunur | widget → `:app` Robolectric `MapShiftActionsTest`; motor → `day.ShiftEngineStagesTest` (Bölüm 11) | A6.2b → A6.5 / A6.8 | "Günü Başlat yok" iddiası uyarlanır (WEEM `ShiftCard`'ta başlat düğmesi var, G1); ayar testi çevrilmez (ayar yok, S1) |
| YT `home_picker_test` | 5 (5. tanım Geri/İmtina ile 2; çalışmada 6) | girdi merkez, sabit iğne, 50 m daire, Kaydet sürüklenen noktayı `Double` olarak döner, tercih yazılmaz, ev okunmaz; girdi yoksa GPS; uzun basış noktayı seçer ve merkez olur; konum hatasında Kaydet pasif, İptal çalışır; AZ + koyu: "Ev məkanını seç", koyu tema, Geri ve "İmtina" iptal döner | `:map` Robolectric `HomePickScreenTest` | A6.2b → A6.5 | MapLibre yerine sahte yüzey; "ev okunmaz" → "yalnız sonuç döner, ev kaydına yazmaz"; sonuç doğrulaması A2.2 `HomePickResultTest` |
| RT `RawPaletteContrastTest` | 2 | tema karşıtlığı | karşılandı: A1.3 `logic/app` `PaletteContrastTest` (3) | — | harita çizim renkleri metin değil; ayrı karşıtlık testi yok |
| RT `HomePickContractTest` | 6 | haritadan ev seçme sonucu | karşılandı: A2.2 `logic/core` `HomePickResultTest` (6) | — | |
| RT `YolGoNavE8Test` | 13 | YolGo nav hedefi ve kalan yol tüketimi | karşılandı: A3.1 `:orders` (10 uyarlandı `RouteStatus`/`NavPort`, 2 A2.5 `MessagesTest`, 1 çevrilmez; `TEST_ENVANTERI`) | — | üretici taraf Bölüm 7.9 |
| Yeni (Y2 karakterizasyonu; eski test yok) | — | altlık seçimi ve anahtarsız `google` düşüşü, atıf metinleri, POI yakınlık eşikleri ve sınırları, sokak ayrıntı kuralları, `forGrade` eşikleri (%3/%8), işaret akışı sabitleri (6.4), uzun basış satır koşulları (6.7), pasif oturum 5 dk | `ui.MapStylesTest`, `ui.MapPaletteTest`, `ui.PuckMotionTest`, `ui.LongPressMenuTest`, `nav.PassiveSessionTest` | A6.2b → A6.5 | değerler bu bölümden |

Bu bölümün dosyaları: **25 test**; çevrilen 24 (1'i Bölüm 10'da, 4'ü Bölüm 11'de), çevrilmez 1. `offer_navigation_removed_test`'in ilk iki testi (`MapTabTest`) Bölüm 7.15'te.

### 6.15 WEEM yerleşimi

- **`logic/map` paketi `ui`** (saf): `HudLayout` (0,64/0,95/0,58 oranları, kenarlar), `CameraMode` + `LocateAction`, `PuckStyle` (ölçüler, `showsArrow`, ok noktaları), `PuckMotion` (6.4 işaret akışı: kayma süresi, yola oturtma eşikleri, ileri tahmin, sıçrama), `MapStyles` (altlık seçimi, atıf, Google koyu JSON stili, POI ve sokak ayrıntı kuralları), `MapPalette` (+ `forGrade`), `LongPressMenu`. Paket `nav`: `PassiveSession`.
- **Android `:map`** (`com.kuryelog.app.map`): `MapSurface` arayüzü (kamera, çizgiler, işaretler, konum işareti, uzun basış, dokunma, elle hareket bildirimi); `GoogleMapSurface` (Maps SDK `MapView`, Compose `AndroidView`; maps-compose eklenmez), `MapLibreSurface` (MapLibre Native `MapView`; iki stil, 6.4); iki yüzey aynı çizgi desenlerini kullanır; `HomePickScreen`; `MapsKeyStatus` (`mapsStatus` karşılığı); ekran açık isteği (`:app` birleştirir).
- **`:app`:** `MapTab` (HUD, üç kip, uzun basış sayfası, kurulum kartı, `ShiftCard` + `MapShiftActions`), `MapSettingsScreen` (6.9), `MapApiSettingsScreen` (8.9), `MapsHelpSheet` (6.10), Daha fazla → Ev konumu → haritadan seç.
- **`:core` bağları:** `ShiftPort.state` (ekran açık, pasif oturum, gün kartı eylemleri), ev (`HomeDraft`, `HomePickResult`; CORE.md 2).
- **2 telefon:** Harita sekmesi iki telefonda da çizilir; navigasyonun asıl kaynağı Receiver'dır (Bölüm 7.16); Provider'daki harita görünümünün içeriği A8'de (LINK.md) kesinleşir.
- **S kararları:** S1 (göç işaretleri yok), S4 (MapLibre, iki stil), S7 (Rawstats bağı, `PICK_HOME`/`SHARE_HOME`, "Rawstats bilgileri" kalkar), S9 (Google Maps'te rota açma yok, Bölüm 8.13).

### 6.16 Farklar ve açık noktalar

- **Bulgu düzeltmesi** (`Y_tracking.md` §G2, ev seçici): doğru; eksikler eklendi — konum izni `denied` ise istenir; koyu temada karolar renk matrisiyle ters çevrilir; sistem geri tuşu iptal sayılır; atıfa dokununca OSM telif sayfası açılır.
- **Kod yorumu ↔ kod:** POI yakınlık eşikleri yorumda "metro 12, AVM 13, restoran 15, ışık 16", kodda 13 / 15 / 16,5 / 16,5 → kod esas (6.6).
- **WEEM'de baştan doğru:** (1) koyu temada `osm` ve `esri` Esri koyu altlığına döner ama atıf "© OpenStreetMap" kalır → atıf gerçek kaynağı söyler; (2) Google yüzeyinde her desen 8/6 kesikli (noktalı eğimli çizgi, 4/6 hayalet farklı görünür) → iki yüzey aynı desen (Google `PatternItem` Dot/Dash/Gap, MapLibre `line-dasharray`); (3) koni saydamlığı Google'da %67, flutter_map'te %55 → tek değer %55; (4) ekran açık tutma dinleyicisi "durak var"da tetiklenir ama koşul yalnız `hasRoute` → WEEM koşulu `gün açık || durak var` (hesap sürerken de kararmaz); (5) ekran açık için wakelock + pencere bayrağı ikilisi → tek Activity bayrağı.
- **Çevrilmez:** `BrandMark`/`BrandTitle`, `assets/brand/` (ölü), Inter yazı tipi dosyaları, `googleMapCompatibility`, `mapDesignV1` göçü, kurulum ekranının dil ve izin adımları (G5'e geçer).
- **Geçiş (A10, kullanıcı işi):** Google Cloud'daki anahtar kısıtına `com.kuryelog.app` ve `com.kuryelog.app.dev` ile WEEM imzasının SHA-1'i eklenmeli; yoksa Google harita gri kalır ve Places `appBlocked` döner (Bölüm 8.8). Yardım sayfası paket ve SHA-1'i gösterir.
- **SORU-N5 (koordinatöre; S4'ün kapsamı):** YolGo'da beş altlık (Sade/OSM/Esri/Uydu/Google) + CARTO anahtarı var; S4 MapLibre için iki stil diyor. (i) Seçenekler: (a) Google (anahtar varsa) + MapLibre "OSM" (raster) + MapLibre "Vektör" (anahtarsız; "Sade"nin yerine, varsayılan); Esri, Uydu ve CARTO kalkar — **önerilen**; (b) (a) + Esri ve Uydu MapLibre raster kaynağı olarak kalır (Esri kullanım koşulları A6.5'te doğrulanır); (c) YolGo'nun beşi + CARTO aynen. (ii) Vektör kaynağı: (a) kendi Bakü PMTiles dosyası (çevrimdışı) — **önerilen**, boyut A6.5 ölçümüyle kabul edilemezse (b) OpenFreeMap. `mapStyle` değerleri (a)'da `google` \| `osm` \| `vector`.
- **SORU-N6 (koordinatöre; `:app`):** YolGo yalnız dikey çalışır (gidon montajı); WEEM manifestinde yön kilidi yok. (a) Activity dikey kilitli — **önerilen**; (b) serbest, Harita sekmesi yatayda da düzgün çizilir (ek HUD düzeni ve test).
- **SORU-N7 (koordinatöre; G1 ekranı):** YolGo gün kartı ile WEEM `ShiftCard`. (a) Tek kart: `ShiftCard` + `:map` eylemleri (Eve dön, İstatistik) + aşama satırı + YolGo'nun katlanma kuralı — **önerilen** (6.12); (b) iki kart: `ShiftCard` üstte, YolGo gün kartı Harita sekmesinin altında aynen.
- **Risk:** MapLibre yerel kütüphanelerinin APK boyutu (A6.5 ölçümü); yüzey değişiminde eski harita görünümünün kapatılması (bellek); Compose HUD'unun dokunma kuralı (6.3) her ekran boyunda (`MapHudLayoutTest` 320/390 dp).
### 6.17 A6.5 uygulama notları (19 Ekim 2026)

- **Saf (`logic/map` `ui`):** `LocateButtonRules` (+ `INITIAL`), `PuckStyle` (renkler, doğruluk dairesi), `PuckMotion` (6.4 işaret akışı; `HeadingFilter` A6.6'dan öne alındı), `GoogleMapStyle` (+ `HIDE_POIS_BELOW_ZOOM`), `HudLayout` (0,64/0,95/0,58, kenarlar), `MapStyles` + `BaseMap` + `MapCamera` + `PoiZoomRules` (SORU-N5 (a): `vector` varsayılan, `osm`, `google`; `minimal`/`esri`/`sat`/bilinmeyen → vektör; `MapSettings.mapStyle` varsayılanı `vector`), `MapPalette`, `LongPressMenu` (satır 7 yok), `MapText` (ekran metinleri); `nav.PassiveSession`.
- **Android `:map`:** `MapSurface` modeli (`MapLine` + `LinePattern` düz/kesikli 8-6/noktalı, `MapMarker`, `MapPuck`, `MapCameraCommand`, `MapSurfaceEvents`), `MapLibreSurface` (MapLibre Native **13.6.1**; OSM raster stili JSON, koyuda `raster-brightness-min 1 / max 0`; vektör stili OpenFreeMap; `weem-*` kaynak ve katmanları; logo/atıf düğmesi kapalı, atıf HUD'da; OSM User-Agent OkHttp ağ önleyicisiyle), `GoogleMapSurface` (Maps SDK **20.0.0**, `MapView` + `AndroidView`, maps-compose yok), `MarkerBitmaps`, `PuckBitmaps` + `LocationPuck`, `HomePickScreen` + `HomePickMap`, `MapsKeyStatus`, `MapSettingsRepo`, `rememberMapPuck` (GPS 1 sn, ilk konum ağdan; kayma her karede).
- **`:app`:** `MapScreen` (graf yokken `ShiftCard` + `MapSetupCard`; hazırsa yüzey + `MapTab`; takip ve konum düğmesi 6.5; vardiya kapalıyken `ShiftCard` HUD'un üstünde; ekran açık = vardiya açık), `MapTab` (üç kip, harita görünümü `movableContentOf` ile kipler arasında taşınır), `MapHudLayout` (kaydırıcı yalnız içerik taşınca), `MapShiftActions` (YolGo `ShiftBar`), `MapSettingsScreen` (görünüm, harita verisi, Google Maps durumu; hava bölümü A6.7), Daha fazla → "Harita ayarları", Ev konumu → "Haritadan seç". Gradle: `GOOGLE_MAPS_API_KEY` (CI gizli değişkeni ya da `local.properties`), biçim denetimi, günlükte son 4, `resValue google_maps_key`, `BuildConfig.MAPS_ENABLED`; `apk.yml` gizli değişkeni geçirir.
- **Henüz bağlı değil (sahip görevler):** rota, arama, uzun basış eylemleri, "Eve dön" rotası, pasif oturumun rota silmesi, yaklaşma/varış kamerası (A6.6); sol göstergeler ve hava (A6.7: `MapScreen` `metrics = {}` ve `weatherStrip = false` verir, A6.7 kaldırır); gün motorunun km/duruşları, `StageFeed` aşamaları, POI/ışık/bisiklet katmanları (A6.8); İstatistik, hatırlatıcı ve görev yuvaları (A6.9); uzun basış menüsü yalnız bağlı eylem varken açılır (A6.5'te hiçbiri yok, menü açılmaz).
- **APK boyutu (S4 ölçümü, `assembleRelease`, küçültmesiz, 4 ABI):** MapLibre'siz 71,65 MB → MapLibre + Maps SDK ile **123,19 MB (+51,5 MB)**. `libmaplibre.so`: arm64-v8a 12,8 MB, armeabi-v7a 9,5 MB, x86 13,2 MB, x86_64 13,4 MB (sıkıştırılmadan saklanır); dex +2,4 MB (28,2 → 30,6 MB). Tek telefona düşen (arm64, ABI ayrımıyla) ≈ +15 MB. **Öneri (kullanıcı kararı):** yalnız `arm64-v8a` derlemek (Pixel ve Poco arm64) APK'yı ~123 MB'tan ~60 MB'a indirir (ML Kit OCR da ABI başına 7–11 MB). **Karar S30 (21 Ekim, A10.1):** yalnız arm64-v8a + release'te R8; sonuç `docs/OPTIMIZASYON.md` 1.
- **Vektör kaynağı (SORU-N5 (ii)):** kendi Bakü PMTiles dosyası bu oturumda üretilemedi (Geofabrik ve karo araçları bu ortamda kapalı); vektör altlık şimdilik OpenFreeMap (`styles/positron`, koyuda `styles/dark`) — (b) yedeği. Koyu stil adı ve OpenFreeMap erişimi bu ortamdan **doğrulanmadı** (ağ kapalı). PMTiles (MapLibre 13 `pmtiles://` destekler) ilk APK işinde CI'da üretilip boyutu ölçülebilir.
- **Açık:** SORU-N6 (ekran yönü) uygulanmadı: Activity kilitlenirse Scooter sekmesinin tam ekranı ve PiP de dikey olur, kullanıcıya soruldu. Google yüzeyi yalnız anahtarlı derlemede; MapLibre ve Google yüzeyleri cihazda denenmedi (Robolectric harita çizmez).

<!-- END sec:6 -->

<!-- BEGIN sec:7 -->
## 7. Y4 — Navigasyon (A6.6)

### 7.1 Kaynak ve kapsam

- YG `lib/nav/nav_controller.dart` (987; merkez: duraklar → iki modda rota → takip), `reroute_gate.dart` (40), `approach_zoom.dart` (30), `arrival_focus.dart` (54), `route_line.dart` (34), `toast.dart` (53), `nav_feed.dart` (227); `lib/tracking/location_service.dart` (224), `map_matcher.dart` (103), `nav_only.dart` (55), `compass.dart` (22), `heading_filter.dart` (10), `native_telemetry.dart:56-103` (hızlı konum okuması; dosyanın kalanı Bölüm 12); `lib/ui/widgets.dart:148-191, 212-227, 285-551` (rota göstergeleri, alt panel, bildirim şeridi), `map_hud.dart:78-111` (`RouteFab`), `nav_screen.dart:57-153, 370-399` (yerleşim, varış kartı), `map_view.dart:161-500, 692-753` (kamera ve rota çizimi); `android_overlay/NavFeedProvider.kt` (sağlayıcı, çevrilmez), `MainActivity.kt:249-258, 340-374` (pusula).
- Başkalarının bölümü: rota motoru ve `Plan`/`RouteLeg` modeli Bölüm 3; sokak kararları (`streets.*`, saat izni) Bölüm 4; ters yön soruları, Ses+/Ses− ve `_learn` (yol öğrenme) Bölüm 9; sipariş aşaması ve hedef bağlama (`ShiftController.setDestination`, `startHomeReturn`), ışık/kaldırım Bölüm 11; rota kaydı (`RouteRecorder`, `*_route_*.json`) ve sürüş günlüğü Bölüm 12; harita ekranı ve uzun basış menüsü Bölüm 6; hedefin metinden çözülmesi Bölüm 8.
- Bulgu: Y4 için ham bulgu yoktu; bu bölüm kaynaktan tam çıkarmadır. `TEST_ENVANTERI`'ndeki "sapma 8 sn" (`yolgo_rules_test`) düzeltmesi 7.17'de.

### 7.2 Durum modeli

`Stop` (`nav_controller.dart:63-94`): `point`, `label`, `unitNote?` (kapı notu), `confidence` (high|medium|low), `restaurant?` (true/false/null = bilinmiyor), `orderId?`, `purpose` (`manual` varsayılan | `order` | `home`), `origin` (`intent`|`clipboard`|`search`|`map`|`order`; varsayılan `map`), `requestedAt?`, `sourcePackage?`. `forOrder(n, toRestaurant)` aynı Stop'u `purpose=order`, `orderId=n`, `restaurant=toRestaurant` ile kopyalar (kaynak, saat, gönderen korunur).

`NavState` (:96-170): `stops`, `plans: Map<RideMode, Plan>` (iki mod: `battery` "BATARYA"/"BATAREYA" 🔋, `time` "ZAMAN"/"VAXT" ⚡; `physics.dart:21-25`), `mode` (varsayılan `battery`), `busy`, `busyText?`, `error?`, `seq` (her yeni rotada +1; ekranda halka efekti ve rotayı sığdırma), `pending?` (Bölüm 8), `suggestion?` (ters yön önerisi, Bölüm 9), `arrived?` (son varılan durak; kapı notu kartı), `reached?` (varılan hedefin iğnesi; `reachedKeepM = 250`), `homeSuggest`. Türetilen: `plan = plans[mode] ?? ilk plan`; `hasRoute = plans` dolu; `activeStop` = planın sırasındaki ilk durak (`plan.order.first`), plan yoksa ilk durak.

### 7.3 Rota açan girişler

| Giriş | YG | Davranış |
|---|---|---|
| Dış metin (bağlantı, paylaşım, pano) | `onExternalText` :276-342 | Bölüm 8.7 (ayrıştır, çöz, <60 m kuralı, durak yoksa rota, varsa bekleyen kart) |
| Arama çubuğu (Enter) | `search` :356-379 | Bölüm 8.7; `origin=search`, doğrudan yeni rota |
| Öneri seçimi / haritada nokta | `goTo(p, label?, restaurant?, origin="map")` :387-393 | tek durak (`label` yoksa "Harita noktası"/"Xəritə nöqtəsi"), `arrived`, `reached`, `pending` silinir; `_bindShiftTarget`; `replan()` |
| "Durak olarak ekle" | `addStop(p, label?)` :395-399 | durak sona eklenir, `arrived` silinir (`reached` kalır); `replan()` |
| Bekleyen kart | `acceptPending(addStop)` :345-352 | Bölüm 8.7 |
| Eve dön | `goHome` :401-418 | yalnız gün açıkken; ev yoksa bildirim (7.8); `homeSuggest=false`; `startHomeReturn` (Bölüm 11); tek durak `{ev, label "Ev"/"Ev", restaurant=false, purpose=home}`; `replan()` |
| Sipariş değişimi | `build` :214-228 | şu anki sipariş numarası değişince (önceki null değilse): hedef = restorandan çıkılmadıysa `restTarget`, çıkıldıysa `customerTarget`. Hedef yoksa rota silinir ve "Sipariş #N seçildi · hedef konumu gir" / "Sifariş #N seçildi · ünvanı daxil et" (6 sn); varsa `goTo(hedef, restaurant = restorandan çıkılmadı, label = restoran adı, origin "order")` |
| Bitir | `clearRoute` :443-448 | rota kaydı `clear` ile kapanır; olay `route_clear`; sıra++; `stops`, `plans`, `busy`, `error`, `pending`, `reached` temizlenir |
| Mod | `setMode(m)` :450-455 | aynıysa yok; olay `mode {m}`; seçim titreşimi |

`_bindShiftTarget(stop, restaurant?)` (:431-441): gün açık ve `purpose != home` ise restoran mı (`_restaurantTarget`, Bölüm 8.7) hesaplanır, `ShiftController.setDestination(nokta, restaurant, name=label)` çağrılır (siparişin restoran/müşteri hedefi saklanır, Bölüm 11); açık sipariş varsa aynı Stop `forOrder(n, restoran)` olur.

### 7.4 Rota hesabı (`replan(reason)`, :459-574)

Nedenler: `route` (yeni hedef; varsayılan), `reroute` (sapma), `prefs` (sokak/öğrenme/ayar değişti), `hours` (saat izni değişti), `battery` (düşük batarya eşiği).
1. Sıra numarası (`_planSeq`) artar; yanıt geldiğinde değişmişse atılır.
2. `prefs` ve `hours`'ta eski plan HEMEN silinir (artık izinsiz rota ekranda etkin kalmasın).
3. Graf yoksa `error` = "Harita verisi yok — Ayarlar → İlave ayarlar → Harita verisi" / "Xəritə məlumatı yoxdur — Tənzimləmələr → Əlavə tənzimləmələr → Xəritə məlumatları"; durak yoksa planlar silinir.
4. `busy`, "Rota hesaplanıyor…" / "Marşrut hesablanır…".
5. Başlangıç `LocationService.current()` (7.6); yön yalnız son fix hızı ≥2 m/s ve yön ≥0 ise.
6. `PlanRequest` (Bölüm 3): `graph`, `origin`, `heading`, `stops` (noktalar), `params = settings.paramsFor(batarya %)`, `approved/blocked/steep/reverseHours` (Bölüm 4), `departure = şimdi UTC`, `ghost`, `learned`, `wwRatio`, `wwAvoid = road.wrongWayAvoid(wwMaxProblemPct/100)`, `wrongWayEdges` (Bölüm 9), `surface`, `surfaceQuality`, `marks` (Y13; WEEM 1.0'da boş, Bölüm 3), `bikeways` (Bölüm 5). İşçide (`RouteWorker`, Bölüm 3) hesaplanır.
7. Plan yoksa: olay `routing_failure {reason, originInMap, targets: [{inside, nearestM}]}` (`nearestM` = 500 m içinde en yakın kenar, yuvarlak). `reroute`/`battery` ise ve eski plan varsa (H3, `keepPlanOnFailure`) eski rota kalır, `RerouteGate.failed(şimdi, başlangıç)`, turuncu bildirim "Yeni rota bulunamadı — eski rota sürüyor" / "Yeni marşrut tapılmadı — köhnə marşrut davam edir" (4 sn); aksi planlar silinir, `error = sonuç hatası`, titreşim.
8. Plan varsa: `RerouteGate.reset()`; `plans`, `error=null`, `seq` yalnız `route`'ta +1; rota kaydı (`_record`, Bölüm 12: `route` ya da kayıt yoksa yeni kayıt, aksi `replanned`); olay `route` (nedeni `route` ise) ya da `reroute` (diğer her neden) `{mode, m (sürüş, yuvarlak), walk, s, wh (0,1 hane), up, stops, to: [[lat 4 hane, lon 4 hane]…]}`; `route`'ta güçlü titreşim.
9. Uyarı (H2, `shouldShowRouteWarning`): planın ilk uyarısı, anahtar `"<ilk durak lat>,<lon>|<uyarı>"`; `route`'ta her zaman, diğer nedenlerde yalnız anahtar değiştiyse turuncu bildirim (metin Bölüm 3; AZ `trMsg`). Son anahtar saklanır.
10. İstisna: `reroute`/`battery` ve eski plan varsa sessizce eski rota (`failed(şimdi, null)`); aksi `error = _humanize(e)` (Bölüm 8.7), titreşim.

### 7.5 Yeniden hesap tetikleri (`build`, :196-261)

| Tetik | Koşul | Neden |
|---|---|---|
| Saat izni | gün açıkken 5 sn'de bir izinli ters yön kümesi (`ReverseHours.allows(şimdi)`) yeniden hesaplanır; küme değiştiyse ve durak varsa | `hours` |
| "Yol yok" işareti (`ghostRev`) | önceki değer vardı, değişti, rota var | `prefs` |
| Ters yön onayı (`approvedRev`) | aynı | `prefs` |
| Ayar `qualityTolerancePct`, `wwMaxProblemPct`, `turnPenaltyS`, `bikewayCostPct` | aynı | `prefs` |
| Elle bisiklet yolu (`bikewayMarks`) | aynı | `prefs` |
| Harita verisi sonradan geldi | graf null → dolu, durak var | `_bindShiftTarget` + `route` |
| Batarya `lowBatteryPct` altına indi | 7.8 | `battery` |
| Rotadan sapma | 7.7 `RerouteGate` | `reroute` |
| Varıştan sonra kalan durak | 7.8 | `route` |
| Ses tuşu / uzun basış sokak kararı, kötü nokta, kalite puanı | Bölüm 9, 4 | `prefs` |

### 7.6 Konum (`LocationService`)

- **Akış** (`location_service.dart:110-146`): izin varsa son bilinen konum (≤30 sn ve geçerliyse) önbelleğe; Fused sağlayıcı, `bestForNavigation`, mesafe filtresi 0, aralık 500 ms. Gün açıkken ya da yalnız-nav kipindeyken ön plan bildirimi: başlık "<Ad> aktif" / "<Ad> işləyir"; metin gün açıkken "Navigasyon ve sürüş kaydı çalışıyor" / "Naviqasiya və sürüş qeydi işləyir", yalnız-nav'da "Gün bitti; rota bitene kadar navigasyon sürüyor" / "Gün bitdi; marşrut bitənə qədər naviqasiya davam edir"; kanal "<Ad> konum" / "<Ad> məkan"; uyanık tutma; kalıcı.
- **Kapsam:** akış yalnız `günAçık || yalnızNav || önde` iken; gün açık ve yerel telemetri servisi varsa Dart akışı durur, konum yerel servisten gelir (`configure(dayActive, nativeAvailable)`, :56-77). Yerel fix: `latest` ~300 ms'de bir (önde), arkadayken en çok 1 sn'de bir; aynı `utc` ikinci kez yayınlanmaz; `day` alanı açık günle eşleşmeli (`native_telemetry.dart:56-103`).
- **Süzgeç** (`_emit`, :78-82): izinli kapsamda, taze (≤15 sn; doğruluk 0–65 m; geçerli koordinat; gelecek ≤5 sn), zaman damgası öncekinden büyük.
- `lastPoint` = son fix ≤30 sn tazeyse. Bekçi: 15 sn'de bir son fix 25 sn'den eskiyse yeniden başlat; hata ya da akış bitince 3 sn sonra yeniden başlat.
- **`current(maxAge = 20 sn)`** (:156-186): taze önbellek varsa o; yoksa izin (yoksa `LocationUnavailable("Konum izni yok veya GPS kapalı")`); `getCurrentPosition(high, 8 sn)` taze değilse `"GPS konumu eski veya belirsiz"`; hata olursa `routeOrigin(son, bilinenSon, şimdi)`, o da yoksa `"GPS sinyali alınamadı"`.
- **`routeOrigin`** (:190-202, saf): son ve Android'in bilinen son konumundan en yenisi; doğruluk 0–150 m, geçerli ve `(0,0)` değil, yaş −5 sn…3 dk.
- **İzin** (:204-214): konum servisi kapalı → `serviceOff`; izin `denied` ise iste; `always`/`whileInUse` yeterli.
- **Pusula** (`MainActivity.kt:340-374`, `compass.dart`, `heading_filter.dart`): döndürme vektörü; telefon dikse (`|rot[8]| < 0.7`, gidonda) arka kameranın yönü, yataysa üst kenar; Bakü manyetik sapması `GeomagneticField(40.40, 49.87, 0)` eklenir; ≥100 ms ve ≥1,5° değişimde gönderilir; önde açılır, arkada kapanır. Dart'ta `compass = eski + fark·0.35` (360 sarması); `HeadingFilter` ağırlık 0.4 (dönüşte 0.85, Bölüm 6). Pusula yalnız konum okunun yönü içindir; rota başlangıç yönü GPS'ten.

### 7.7 Takip döngüsü (`_onPosition`, :806-864)

Her fix'te sırayla:
1. **Yol eşleştirme** (`MapMatcher.update`, `map_matcher.dart:54-102`): doğruluk >35 m → önceki sonuç; hareket = hız ≥2 m/s ve yön ≥0; en yakın kenar 30 m içinde (hareketteyse yönle, bulunamazsa yönsüz; "yol yok" kenarları hariç); `forward` = yön ile parça yönü farkı ≤90° (dururken öncekisi); `wrongWay` = hareketli, tek yönün tersi ve `contraflow` yok. Ters yön önerisi: onaysız sokakta ve izinli kesim değilken aynı yolda ≥40 m ters gidilince **bir kez** (`_suggested`) `WrongWayEvent(way, ad, mainRoad)`. Ana caddede kırmızı bildirim "Ana caddede ters yöndesin!" / "Əsas küçədə əks istiqamətdəsən!" + titreşim; değilse öneri (`suggestion`) + turuncu "Ters yön: <ad> — onay için Ses+" / "Əks istiqamət: <ad> — təsdiq üçün Səs+" (10 sn), öneri 15 sn sonra kalkar. Olay `wrongway {way, main}`. Adsız yol "isimsiz yol"/"adsız yol". (Onay akışı Bölüm 9.)
2. **Öğrenme** (`_learn`, Bölüm 9) ve **rota kaydı** (`_recordFix`, Bölüm 12: rotaya uzaklık, ortalama hız).
3. **İğne:** `reached` varken doğruluk ≤30 m ve iğneye uzaklık >250 m ise iğne kalkar.
4. Plan yoksa, hesap sürüyorsa ya da doğruluk >30 m ise dur.
5. **Varış** (ilk bacak): kapıya ≤35 m, ya da bacakta yürüme varken park noktasına (sürüş çizgisinin sonu) ≤20 m ve hız <2 m/s → `_arrive`; park noktasında varışta ayrıca "Park et · N m yürü" / "Saxla · N m piyada" (10 sn, yürüme simgesi).
6. **Sapma:** rotaya uzaklık = sürüş çizgisine; bacak başında yaya yolu yürüyüşü (`startWalk`, ≥2 nokta) varsa ikisinin küçüğü. `RerouteGate.feed` (`reroute_gate.dart:6-31`): doğruluk sonlu, 0–30 m ve fix yaşı ±5 sn değilse sayaç sıfır; eşik `clamp(1.5·doğruluk + 12, 25, 45)` m; eşiği aşan **art arda 2** fix; son denemeden 2 sn geçmeli; başarısız denemeden sonra 10 sn geçmeden ve başarısız konumdan 30 m'den fazla gidilmeden yeniden denenmez (H3) → `replan(reroute)`.

Uzaklık (`_distanceToPolyline`, :964-974): yerel düzlem izdüşümü (`LocalProjection`), parçalara en kısa uzaklık; çizgi <2 nokta → 0.

### 7.8 Varış, iğne, eve dönüş, batarya

- `_arrive(plan)` (:866-893): `idx = plan.order.first` (yoksa 0); rota kaydı `arrive` ile kapanır; olay `arrive {la, lo}` (4 hane); güçlü titreşim; 90 sn sonra `arrived` kalkar (aynı durak hâlâ oradaysa); `stops` = kalanlar, `plans` silinir (eski sıra geçersiz), `arrived = reached = durak`; kalan durak varsa `replan(route)`; kalan yoksa batarya % ≤ `homeReturnPct` (30) ise `homeSuggest`.
- İğne (`reached`): kapı notu kartı kapanınca da kalır; yeni rota (`goTo`, yeni durakla `acceptPending(false)`, `search`), rota silme ya da 250 m uzaklaşma kaldırır; durak eklemek kaldırmaz.
- Batarya (`_onBattery`, :895-908): önceki % ≥ `lowBatteryPct` (50) ve yeni < eşik ise bir kez turuncu "Batarya ~%N — eğim limiti %M" / "Batareya ~%N — yoxuş limiti %M" (6 sn; M = `lowBatteryGradePct`, 10) ve rota varsa `replan(battery)`; eşik üstüne çıkınca bayrak sıfırlanır. % ≤ `homeReturnPct` ve durak yokken `homeSuggest`. Batarya kaynağı Bölüm 10.
- Eve dönüş kartı (`BottomPanel`): "Batarya düşük — eve dön?" / "Batareya azdır — evə qayıdaq?", "Eve git" / "Evə get", kapat.
- Ev yoksa (`goHome`): turuncu "Ev konumu Rawstats'ta ayarlanır" / "Ev ünvanı Rawstats-da təyin olunur" (6 sn). **WEEM:** ev `:core`'da; metin "Ev konumu ayarlanmamış — Daha fazla → Ev konumu" (yeni metin; 7.17). YolGo'nun H5 `refreshIfStale` (Rawstats'tan evi tazeleme) kalkar (tek ev, S7).

### 7.9 Kalan yol ve `RouteStatus`

- **Ölçüm** (`NavFeedLeg`, `nav_feed.dart:94-110`): etkin (ilk) bacağın sürüş çizgisi (`RouteLine`: kümülatif haversine uzunluğu; `project` = en yakın parçaya izdüşüm, `along` ve dik uzaklık) ve dönüşlerin çizgi üstündeki yerleri; fix için `kalan = max(0, uzunluk − along) + walkM` (0,1 m'ye yuvarlanır), `kalanDönüş = along'dan sonraki dönüş sayısı`, `onRoute = dik uzaklık ≤ 40 m` (`offRouteM`), `measuredAtMs = fix zamanı`.
- **Canlı ölçüm** (`NavFeedRouteController`, :167-199): yalnız doğruluk ≤50 m fix'lerle ölçülür (kaba konum yok sayılır); bacak değişince çizgi yeniden kurulur; açılışta son konum ölçülür. Hem göstergeler hem yayın bunu kullanır.
- **Yayın** (YolGo: `content://az.duzyol.app.nav/nav/v1/current`, Rawstats için): `NavFeed.target(activeStop, seenAt)`: durak yoksa ya da ev rotasıysa null (ev koordinatı gönderilmez); geçersiz koordinat null; lat/lon 6 haneye yuvarlanır; `kind` = restaurant/customer/unknown (`Stop.restaurant`); `origin` bilinmeyen değerse `map`; `requestedAtMs` = `requestedAt` ya da hedefin ilk görüldüğü an. Yük v1 `{schemaVersion 1, source "yolgo", generatedAtMs, updatedAtMs, target{lat, lon, kind, origin, requestedAtMs, sourcePackage}|null, route{remainingM, remainingTurns, measuredAtMs, onRoute}|null}`, ≤4 000 karakter.
- **Ne zaman** (`NavFeedGate`, :114-140): açılışta bir kez (hedef yok da olsa); hedef ya da rotanın varlığı değişince; dönüş sayısı değişince; kalan ≥20 m değişince; rotadayken (`onRoute`) son yayından 10 sn sonra (rota dışında kalp atışı yok). `updatedAtMs` yalnız içerik değişince ilerler.
- **WEEM:** sağlayıcı ve imza kapısı kalkar (S7); aynı ölçüm `NavPort.route: StateFlow<RouteStatus?>` olarak verilir (`logic/core` `NavMessages.kt`: `RouteStatus{target{lat, lon, kind, origin, requestedAtMs, sourcePackage}, route{remainingM, remainingTurns, measuredAtMs}, updatedAtMs}`). `onRoute` iç alandır (`:map` `RouteProgress`), `RouteStatus.Route`'ta yoktur; Rawstats okuyucusu (`NavBinding`, `CustomerCard`) onu kullanmıyordu. `StateFlow` her ölçümde değil, `NavFeedGate` kuralıyla güncellenir (`:orders` ve 2 telefon `RouteStatus` R→P mesajı aynı akışı okur; plan 4.4: en çok 1/sn). Ev rotasında `target=null`, `route` var.

### 7.10 Kamera

- **Hız tabanı** (`approach_zoom.dart:15`): `<6 km/s → 16.6`, `<16 → 16.2`, `<24 → 15.9`, aksi `15.6`.
- **Yaklaşma** (`zoomFor`, :18-25): kalan ≥1 000 m ya da bilinmiyorsa taban; aksi `t = (1000 − clamp(d, 100, 1000)) / 900`, `zoom = taban + (17.5 − taban)·(t²(3 − 2t))` (smoothstep). Kalan = rota üstündeyse ölçüm (7.9), değilse park noktasına kuş uçuşu (`map_view.dart:412-426`). Otomatik yakınlık her karede hedefe %4 yaklaşır.
- **Elle dokunma** (`refollowAfterGesture`, :31-34; `map_view.dart:430-439`): kaydırma/yakınlaştırma takibi ve otomatik zoom'u durdurur; rota açıkken son dokunuştan **10 sn** sonra takip + otomatik zoom döner; rota yokken hareket hâlindeyken **8 sn** sonra takip döner, seçilen ölçek korunur.
- **Yeni rota** (`seq` değişince): bütün rota + konum ekrana sığdırılır (OSM yüzeyinde kenar payı 40/170/40/230 px, en çok zoom 16.5; Google'da 32 px), takip durur, 5 sn sonra konuma dönülür.
- **Varış odağı** (`arrival_focus.dart`): rota açık ve varılmamışken kalan ≤100 m'de girer, >150 m'de çıkar (histerezis); kalan bilinmiyorsa (rota dışı ya da ölçüm yok) durum değişmez; rota kalkınca kapanır. Odakta kamera `focusCamera`: merkez = konum ile hedefin orta noktası; iki nokta kısa kenarın %70'ine sığacak zoom `log2(156543.03392·cos(lat)·px / mesafe)`, [18, 19] aralığına kıstırılır; merkez kayması ve zoom her karede %8 yaklaşır. Odaktayken ekran (Bölüm 6): yalnız harita, konum, bildirim şeridi, hatırlatıcı kartı, iş uyarıları, ters yön kartı ve alttaki `ArrivalAddressCard` (hedef adı 2 satır, kapı notu 2 satır, rotadaysa sağda "N m"; ev simgesi / restoran simgesi / konum simgesi).
- **Yön:** yön-yukarı kipte kamera yönü hareket yönüne her karede %12 (dönüşte %25) yaklaşır; kuzey-yukarı kipte 0. Konum düğmesi kipleri Bölüm 6.

### 7.11 Ekran parçaları ve metinler

- **Rota paneli** (`BottomPanel`, `widgets.dart:315-394`; `RouteFab`, `map_hud.dart:78-111` ile açılır; hata ya da bekleyen gelince kendiliğinden açılır; en çok 220 px, kaydırılır). Gövde önceliği: bekleyen kart → hesaplanıyor (`busyText` ya da "Çalışıyor…"/"Hesablanır…") → etkin durak → varış kartı → hata satırı (`trMsg(error)` + durak varsa "Tekrar"/"Yenidən" → `replan`). Üstte eve dönüş önerisi; çifte siparişte ilk kapıya varınca varış kartı sonraki rota sırasında da görünür. Altta: rota varsa "Yol yok"/"Yol yoxdur" (rota üzerindeki parçayı "yol yok" işaretleme seçicisi, Bölüm 9), durak ya da rota varsa "Bitir"/"Tamamla" (`clearRoute`).
- **Etkin durak** (`_ActiveStop`, :396-442): etiket (1 satır), sağda `"<dk> dk · <HH:mm>"` (`route-eta`; `<1` dk altı; varış saati Bölüm 10 `arrivalClock`), kapı notu (20 pt, turuncu), ortalama hız satırı (`_AvgSpeed`: "Ort. N km/s" + "· hareketteyken M"; rota kaydından, Bölüm 12), "Güzergâh: A → B → C" (ilk bacağın ilk 3 sokağı), güven düşükse "⚠ Adres emin değil — pini kontrol et" / "⚠ Ünvan dəqiq deyil — nöqtəni yoxla".
- **Varış kartı** (`_Arrived`, :470-491): "Vardın: <etiket>" / "Çatdın: <etiket>", kapı notu 24 pt turuncu, kapat.
- **Sol göstergeler** (`ModePager`, :148-191; ayrıntı ve hız/batarya Bölüm 10): rota varken "Rota süresi" (`metric-time`, `<dk> dk`, alt yazı varış saati), rotadayken "Durağa kalan" / "Dayanacağa qalan" (`metric-remaining`, kalan `x.x km` (1 hane), alt yazı "yürüme dahil" / "piyada daxil"; ölçüm yoksa ya da rota dışındaysa gizli), rota kaydı ortalaması varsa "Rota ortalama hızı" (`metric-avg`). Dokununca açılan panel (:212-227): mod seçimi (BATARYA/ZAMAN), `"Varış HH:mm · <toplam sürüş km>[ · Kalan x.x km] · ≈W Wh · ↑m · ↓m"`, `"Yürüme: N m · K durak"`, `"Ters yön: N m · risk %R"`, yüzey satırı (Y13; WEEM 1.0'da "yeterli ölçüm yok" metni), "Yol ayrıntıları" → Yollarım (Bölüm 9).
- **Bildirim şeridi** (`ToastController`/`ToastBanner`, `toast.dart`, `widgets.dart:285-311`): tek bildirim; yenisi eskisini siler; varsayılan 4 sn, renk varsayılan mavi; isteğe bağlı simge ve eylem ("Geri al"); kenarlık rengi bildirim rengi.
- **Rota çizimi** (`map_view.dart:692-753`): her bacakta parlama (14 px, mavi %18) + eğime göre renkli çekirdek (6 px; renk `AppColors.forGrade(Δh / max(d, 20))`, Bölüm 6) + itilerek yürünen parçalar (`pushedRuns`, kesikli 8/6, yürüme rengi) + başta yaya yolu yürüyüşü (`startWalk`, 4 px kesikli) + sondaki yürüme (4 px düz). Diğer modun planı soluk gri (4 px, `0x66A0A8B8`). Durak rozetleri sırayla 1, 2…; ilk durakta yeni rotada genişleyen halka (`seq`); varılan iğne yeşil bayrak.

### 7.12 Yalnız-navigasyon kipi (`nav_only.dart`, H1/F4.4)

Saf geçiş `nextNavOnly(s, dayActive, hasRoute, now, dayJustEnded)`: gün açık ya da rota yok → kapalı; açıkken 90 dk (`navOnlyMax`) dolunca kapanır; kapalıyken yalnız gün **bu anda** kapandıysa ve rota açıksa açılır (`since = şimdi`). Açıkken konum arka planda sürer (ön plan bildirimi 7.6; gün satırı ve yüzey kaydı yok); varış/rota silme kapatır. Gün kapalıyken çizilen rota bu kipi açmaz.

### 7.13 Ses tuşları ve sokak işaretleri

`onVolumeKey(up)` ve `onVolumeDownHold` (:576-746) navigasyon denetleyicisindedir ama davranışları Y12/Y8'dir: sıra = açık ters yön sorusu → kalite yüzdesi penceresi → Ses+ tek basış onay / çift basış (450 ms) kalite penceresi, Ses− tek basış ±25 m kötü nokta / çift basış aşırı eğimli, Ses− uzun basış (≥1 000 ms) işaret kaldırma; her biri bildirim, "Geri al" ve `replan(prefs)`. Ayrıntı ve metinler **Bölüm 9** (Ses tuşu yakalama) ve **Bölüm 4** (sokak kararları).

### 7.14 Veri

| Ad | Ayrıntı | WEEM |
|---|---|---|
| Sürüş günlüğü olayları | `route`/`reroute {mode, m, walk, s, wh, up, stops, to}`, `routing_failure {reason, originInMap, targets[{inside, nearestM}]}`, `route_clear`, `mode {m}`, `arrive {la, lo}` (4 hane), `wrongway {way, main}`, `route_end` (Bölüm 12), `intent`/`external`/`resolved` (Bölüm 8) | aynı adlar ve alanlar (Bölüm 12) |
| Rota kaydı | `app_flutter/logs/<gün>_route_<ms>.json` (`logging` ayarı açıkken, ≥2 iz noktası) | Bölüm 12 |
| `nav/v1/current` | 7.9 yük v1 | kalkar → `NavPort.route` (`RouteStatus`) |
| Ayarlar | `homeReturnPct` 30, `lowBatteryPct` 50, `lowBatteryGradePct` 10, `qualityTolerancePct` 10, `wwMaxProblemPct` 50, `turnPenaltyS` 12, `bikewayCostPct` 85 (değişince yeniden hesap) | `MapSettings` (Bölüm 14) |
| Konum ön plan bildirimi | geolocator'ın kendi kanalı "<Ad> konum"; YolGo numarası yok | gün açıkken `:core` vardiya servisi (1, `shift`) taşır; yalnız-nav için yeni numara gerekir → 7.17 SORU-N4 |

### 7.15 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| YT `approach_zoom_test` | 4 | hız tabanı 0/10/20/30 km/s → 16.6/16.2/15.9/15.6; ≥1 km ya da bilinmiyorsa taban; ≤100 m 17.5, 1 km→100 m tek yönlü, 10 m'de <0.05 sıçrama, uçlarda yavaş; dokunmadan sonra rota açıkken 10 sn → takip+zoom, rota yokken hareketteyken 8 sn → takip (ölçek korunur), duruyorken hiç | `nav.ApproachZoomTest` | A6.2b → A6.6 | saf |
| YT `arrival_focus_test` | 4 | histerezis 100/150 m, bilinmeyen kalanda durum korunur, varınca/rota yokken kapalı; sağlayıcı kalan yolu izler, plan silinince kapanır; `focusCamera` orta nokta, zoom [18, 19], iki nokta ekranda; 3 m'de 19, 400 m'de 18 | `nav.ArrivalFocusTest` | A6.2b → A6.6 | saf (sağlayıcı testi `StateFlow` ile) |
| YT `arrival_pin_test` | 3 | varınca durak silinir, `arrived` ve `reached` aynı durak; 91 sn sonra `arrived` null, `reached` kalır; kartı kapatmak iğneyi silmez; 120 m'de kalır, 80 m doğruluklu fix silmez, 280 m'de kalkar; `addStop` iğneyi korur, `goTo` ve `clearRoute` kaldırır | `nav.NavControllerArrivalTest` | A6.2b → A6.6 | sahte konum akışı, boş graf, sanal saat (90 sn); günlük sahte |
| YT `native_fast_fix_test` | 2 | hızlı okuma günlük satırı istemez, aynı `utc` iki kez yayınlanmaz (`latest` 3 çağrı → 2 yayın); arka planda en çok 1/sn, öne dönünce hemen | `nav.FastFixGateTest` (saf: `utc` tekilleştirme + arka plan 1 sn kısıtı) | A6.2b → A6.6 | WEEM'de yerel telemetri köprüsü yok; kural `:map` konum kaynağı birleştiricisine (7.16) taşınır |
| YT `nav_assistant_test` | 1 (döngüyle 2) | restoran=true: konuma yakın dış hedef (<60 m) restoran olduğu için kabul, sipariş açmaz; aşamalar yalnız sipariş akışından (`to_pickup` → `toRestaurant`, `at_dropoff` → `atCustomer`, boş → teslim 1), teslimde yakındaki görev uyarısı (Y20). restoran=false: hata, durak yok | `nav.NavAssistantTest` | A6.2b → A6.6 (+ A6.9 görev kısmı) | `OrderEvents.stages` sahte `TripStages` ile beslenir (feed v2 JSON yerine); görev tetikleyici Bölüm 13 |
| YT `nav_feed_test` | 11 | hedef: tür/kaynak/istek saati/gönderen, 6 hane; ev rotası ve durak yok → null; `forOrder` kaynağı korur; kalan 1 040 m/2 dönüş, 540/1, 800'de 0 dönüş, 120 m doğuda `onRoute=false`; rota/konum yok → null; yük anahtar sırası ve ≤4 000; yayın kapısı (açılış, hedef, ≥20 m, dönüş, 10 sn, rota dışında kalp yok, rota silindi); `IncomingLink.parse` (Bölüm 8); köprü teslimi; denetleyici + yayıncı: intent hedefi siparişe bağlanınca da `intent` kalır, `map`, silinince null; pano/arama kaynakları | `nav.RouteProgressTest`, `nav.RouteStatusGateTest`, `nav.NavControllerOriginTest`; `input.IncomingLinkTest` (2 test) | A6.2b → A6.6 | yük JSON'u yerine `RouteStatus` alanları ve `NavPort.route` akışı denetlenir; "kurye uygulaması köprüsü" testi `LinkIntake` saf kısmına uyarlanır |
| YT `nav_only_location_test` | 2 | saf geçişler (90 dk, varış, yeni gün, rota yokken biten gün açmaz); sağlayıcı: gün bitince rota açıksa konum sürer, varışta kapanır | `nav.NavOnlyTest` | A6.2b → A6.6 | `ShiftPort.state` sahte |
| YT `remaining_km_e12_test` | 7 (5 tanım + 2 dilde dönen 2 tanım; çalışmada 9) | gösterge = yayınla aynı kalan (sürüş + yürüme, `rideM` değil), her fix'te tazelenir (20 m yayın eşiğinden bağımsız), AZ "Dayanacağa qalan", rota dışında/rota yokken gizli, yeni bacak son fix'le ölçülür, açılır panelde "toplam · Kalan x.x km", başlık + "yürüme dahil"/"piyada daxil" | `nav.RouteProgressTest` (ölçüm) + `:app` Robolectric `MapMetricsTest` (gösterge metinleri) | A6.2b → A6.6 (gösterge A6.7 ile) | Compose ekranı Bölüm 6/10 |
| YT `route_warnings_h2_h3_test` | 3 | H2 uyarı yeni rotada hep, diğerinde durak/uyarı değişince; H3 `keepPlanOnFailure` yalnız `reroute`/`battery`; başarısızlıktan sonra 10 sn ya da 30 m | `nav.RerouteGateTest` | A6.2b → A6.6 | saf |
| YT `v150_features_test` | 15 | **rota başlangıcı** (3): 1 dk/90 m → kabul, en yeni seçilir, 4 dk/400 m/(0,0) red → `nav.RouteOriginTest`; **evden çıkınca gün** (5): `HomeDepartureDetector` + `autoStartDay` göçü → çevrilmez (ölü kod; ev kuralı `:core` `ShiftMachine`, CORE.md); **hız seçenekleri** (3) → `ride.SpeedOptionsTest` (Bölüm 10); **koyu Google harita** (1) → `ui.GoogleMapStyleTest` (Bölüm 6); **ana ekran parçaları** (3) → `:app` Robolectric `MapMetricsTest`/`SpeedSheetTest` (Bölüm 6/10) | (satırda) | A6.2b → A6.6/A6.5/A6.7 | 10 çevrilir, 5 çevrilmez |
| YT `yolgo_rules_test` | 4 | iki taze sapma fix'i (35 m, doğruluk 5) 2. fix'te yeniden rota ister, 2 sn sonra tekrar; doğruluk 100 / 20 sn eski fix / 10 m sapma istemez → `nav.RerouteGateTest`; sipariş JSON'u geri sarılmaz → `day` (Bölüm 11); belirsiz yüzey ışık eşleşmesini kapatmaz → `day` (Bölüm 11); hava şeridi saatleri → `weather` (Bölüm 10) | (satırda) | A6.2b → A6.6/A6.8/A6.7 | 4'ü de çevrilir |
| YK `NavFeedContractTest` (6'nın 4'ü) | 4 | yol/sütun/hedef paket sabitleri; yalnız v1 `yolgo` yükü ve ≤4 000; boş durum yükü; her sorguda paket + imza denetimi | — | — | `pathsMatchRawstatsReader`, `everyQueryRechecksTheCallersPackageAndSigner` **çevrilmez** (sağlayıcı ve imza kalkar, S7); `onlyAYolGoV1PayloadIsServed`, `withoutAPublishedStateTheProviderSaysThereIsNoTarget` A2.5'te karşılandı (`logic/core` `MessagesTest.routeStatusFromTodaysNavFeed`); kalan 2 test Bölüm 8 |
| YT `offer_navigation_removed_test` | 4 | varış odağında yalnız harita + alttaki adres kartı (ekranın alt yarısında), uyarılar görünür; ana ekranda menü yok, ayar simgesi Ayarlar'ı açar, kayıt noktası yok, Yollarım'da "● Kayıt sürüyor"; Ayarlar'da "Teklif kazancı" yok, veri seti açılır (2 dil); teklif açılışı sayfa açmaz, veri seti zenginleştirmesi sinyalle | ilk 2 test `:app` Robolectric `MapTabTest` (Bölüm 6) | A6.2b → A6.5/A6.6 | `TEST_ENVANTERI` hepsini "çevrilmez" sayıyordu; **öneri:** ilk 2 uyarlanır (Y1/Y4 ekran kuralı), son 2 çevrilmez (kaldırılmış teklif sayfası; veri seti tetikleri Bölüm 13) |

Bu bölümün sahip olduğu dosyalardan çevrilen (A6.2b): approach 4 + arrival_focus 4 + arrival_pin 3 + fast_fix 2 + nav_assistant 1 + nav_feed 11 (2'si `input` paketine) + nav_only 2 + remaining_km 7 + h2_h3 3 + v150 10 (3'ü `ride`, 1'i `ui`, 3'ü `:app`) + yolgo_rules 4 (2'si `day`, 1'i `weather`) + offer_navigation_removed 2 = **53 test**; çevrilmez: v150 5, `NavFeedContractTest` 2, `offer_navigation_removed` 2; A2.5'te karşılandı: `NavFeedContractTest` 2.

### 7.16 WEEM yerleşimi

- **`logic/map` paketi `nav`:** `NavController` (saf durum makinesi; girişler: hedefler, fix'ler, ayar/öğrenme sürümleri, batarya, vardiya; çıkışlar: `NavState`, olaylar, `RouteStatus`), `NavState`, `Stop`, `RerouteGate` (+ `shouldShowRouteWarning`, `keepPlanOnFailure`), `ApproachZoom` (`speedZoom`, `zoomFor`, `refollowAfterGesture`), `ArrivalFocus` (+ `focusCamera`), `RouteLine`, `RouteProgress` (`NavFeedLeg` karşılığı, `onRoute` dahil), `RouteStatusGate` (`NavFeedGate`), `MapMatcher` (+ `MatchInfo`, `WrongWayEvent`), `NavOnly`, `RouteOrigin` (`routeOrigin`), `FixGate` (taze/artan süzgeç, `utc` tekilleştirme, arka planda 1 sn), `HeadingFilter`, `CompassSmoother`. Rota hesabı `route` paketinde (Bölüm 3); işçi Android'de coroutine (`Dispatchers.Default`) + zaman aşımı.
- **Android `:map`:** `NavLocation` (Fused 500 ms, `PRIORITY_HIGH_ACCURACY`; harita önde, gün açık ya da yalnız-nav iken; gün açıkken ön plan servisi `:core` `ShiftService`'tir (1), onun yanında ikinci istek olarak açılır — `ShiftService`'in kendi GPS'i 2 sn aralıklı, navigasyon için yetmez), yalnız-nav ön plan servisi (`NavOnlyService`, tür `location`; 7.17 SORU-N4), `CompassSensor` (döndürme vektörü, Bakü sapması, ≥100 ms/≥1,5°), `NavPortImpl` (`route` = `RouteStatusGate`'ten geçen `RouteStatus`; `open(target)` → `NavController.openTarget`, Bölüm 8.12), rota işçisi.
- **`:app`:** `MapTab` (harita + HUD; Bölüm 6), rota paneli, varış kartı, sol göstergeler, bildirim şeridi.
- **`:core` bağları:** `ShiftPort.state` (gün açık mı; yalnız-nav geçişi; `goHome` koşulu), `:core` ev (`goHome`), `OrderEvents.stages` (YolGo'nun feed v2 sipariş izleyicisinin karşılığı; sipariş hedef bağı Bölüm 11), `ScooterBridge.live`/batarya (Bölüm 10).
- **2 telefon:** navigasyon Receiver'da çalışır (harita, rota, takip). `RouteStatus` R→P (Provider'daki `:orders` varış ve müşteri kartı için; plan 4.4). Hedefler P→R `NavTarget` (Bölüm 8.12). Receiver'da vardiya `ShiftState` mesajıyla izlenir; Receiver'ın konum ön plan servisi `:map`'in kendi servisidir (A8.3'te kesinleşir).

### 7.17 Farklar ve açık noktalar

- **Bulgu/envanter düzeltmesi:** `yolgo_rules_test` "sapma 8 sn" diye özetlenmişti; test "iki taze sapma fix'i 8 sn **beklemeden** yeniden rota ister" (eşik `clamp(1.5·doğruluk + 12, 25, 45)`, 2 fix, 2 sn soğuma). 8 sn kuralı yalnız rota yokken dokunmadan sonra takibe dönüştür (7.10).
- **Ölü kod:** `HomeDepartureDetector` (`home_departure.dart`) ve onun `v150_features_test` grubu (5 test) çevrilmez; `check(force)` (Bölüm 8).
- **WEEM'de baştan doğru:** yerel telemetri köprüsü (`yolgo/telemetry-v2`, `latest`/`poll`) yok; konum tek `:map` kaynağından, `FixGate` aynı kuralı uygular. "GPS konumu eski veya belirsiz" iki dilli olur (Bölüm 8 SORU-N2). Ev uyarısı metni Rawstats'a gönderme yapmaz (7.8): önerilen TR "Ev konumu ayarlanmamış — Daha fazla → Ev konumu", AZ "Ev ünvanı təyin edilməyib — Daha çox → Ev ünvanı" (A6.6'da `:app` menü adlarıyla eşlenir).
- **Aynen korunan:** iki mod (batarya/zaman) iki ayrı plan; uyarı ve hata metinleri Bölüm 3'ün `trMsg` sözlüğünden; `routing_failure` olayında yalnız `inside`/`nearestM` (koordinat yazılmaz); varışta plan silinir ve kalan duraklar yeniden sıralanır.
- **SORU-N4 (koordinatöre; Bölüm 14 `NotificationIds`):** gün bittiğinde rota açıksa (yalnız-nav, ≤90 dk) konumun arka planda sürmesi için bir ön plan servisi bildirimi gerekir; YolGo bunu geolocator'ın kendi bildirimiyle yapıyordu, `CORE.md` 3.3 tablosunda karşılığı yok. Öneri: yeni `NAV_ONLY_FGS` **7402**, yeni kanal `navigation` (LOW; ad "WEEM navigasyon"/"WEEM naviqasiya"; `yolgo_recording` kullanılmaz, CORE.md 3.3'te vardiya servisine birleşti); vardiya sonu iptal listesine girmez (kendi 90 dk sınırıyla kapanır). Varsayılan: öneri.
- **Risk:** Wolt/Bolt öndeyken navigasyonun arka planda sürmesi vardiya servisinin ömrüne bağlıdır (gün açıkken); Xiaomi/Poco arka plan kısıtı ve pil iyileştirmesi telefonda denenmeli (A6.10 yan yana sürüş). 500 ms Fused isteği + `ShiftService` 2 sn GPS isteği birlikte çalışır (Android birleştirir); pil etkisi A10'da ölçülür.
### 7.18 A6.6 uygulama notları (20 Ekim 2026)

- **Saf:** `nav` (7.16 listesi) + `NavPanelText` (`fmtMin` "<1", "dk/dəq · HH:mm", "Güzergâh", "Vardın"); `ui.RouteLayer` (7.11 çizim: öteki mod gri, parlama %18, eğim renkli çekirdek parçaları, itilen/baştaki yürüme kesikli, sondaki yürüme düz; rozetler bacak hedeflerinde; `fit` Web Mercator + eşit olmayan kenar payı, ≤16,5); `ride.SpeedOptions.arrivalClock` (A6.7 iskeletinden öne alındı). Testler `RouteLayerTest` 3, `NavPanelTextTest` 2.
- **Android:** `NavRuntime` (tek denetleyici, konum, pano, bağlantı, `NavPort`, `speedKmh`, `goHome` → `:core` ev), `NavOnlyService` (7402, kanal `navigation`; SORU-N4 varsayılanı; Android 12+ arka plandan başlatmayı reddederse sessizce düşer — telefonda denenecek). `:app` `MapScreen`: çizgiler/rozetler, yeni rotada sığdırma (MapLibre 40/170/40/230; Google'da harita iç boşluğu düşülüp her yanda 32) ve 5 sn sonra takip, yaklaşma zoom'u (rota üstünde ölçüm, değilse park noktasına kuş uçuşu; her kamera adımında %15 yaklaşır), varış odağı (yalnız ölçümle), elle dokunma → takip ve otomatik zoom durur, 10 sn (rota) / 8 sn (rota yok, ≥3 km/s) sonra döner; `RoutePanel` (`route-fab` ile açılır, hata/bekleyen gelince kendiliğinden), `NavToastBanner`, titreşim, `SearchOverlay` (tam ekran katman; Enter → `NavController.search`, seçim → `goTo(origin = search)`), uzun basış sayfası (yalnız hedef satırları: Buraya git, Durak ekle, restoran olarak seç; öteki satırlar A6.8–A6.11), Eve dön → `goHome`, ekran açık = gün açık ya da durak var. `MapApiSettingsScreen` (8.9; CARTO ve uyumluluk modu yok, Open-Meteo kartı A6.7).
- **Bilerek ertelenen:** mod seçimi (BATARYA/ZAMAN) sol gösterge panelinde (A6.7, YolGo `ModePager`); ortalama hız satırı (rota kaydı, A6.9); "Yol yok" seçicisi (A6.11); ilk durakta genişleyen halka (`Ripple`) çizilmedi; aramada vardiya aşamasına göre restoran (A6.8, şimdilik önerinin bayrağı).
- **Doğrulanmadı:** hiçbiri telefonda denenmedi (kamera davranışı, servis, titreşim, MapLibre/Google çizgileri).
<!-- END sec:7 -->

<!-- BEGIN sec:8 -->
## 8. Y5 Wolt/Bolt navigasyon bağlantıları ve Y6 Adres bulma (A6.6)

### 8.1 Kaynak ve kapsam

- **Y5:** YG `android_overlay/AndroidManifest.xml:27-120` (Activity ve filtreler), `MainActivity.kt:66-68, 95, 105-109` (bekleyen bağlantı), `:452-476` (`onNewIntent`, `linkOf`, `extractLink`), `:434-450` (`openLinkSettings`), `:145-168` (`openGoogleRoute`, S9 ile kalkar); `NavFeedProvider.kt:121-142` (`IncomingLink`); `lib/input/clipboard_sniffer.dart:86-127, 154-172, 229-236` (köprü, Dart `IncomingLink`).
- **Y6:** `lib/input/clipboard_sniffer.dart:13-84` (pano), `address_parser.dart` (367), `plus_code.dart` (87), `destination_resolver.dart` (128), `geocoder.dart` (201), `place_search.dart` (198), `places_rest.dart` (224); `lib/nav/nav_controller.dart:270-441, 976-986` (hedefin kabulü); `lib/ui/map_hud.dart:14-76` (arama çubuğu), `widgets.dart:493-524` (`_Pending`), `api_settings.dart` (162); `lib/core/app_info.dart:16` (User-Agent), `geo.dart:43-50, 102-110`, `i18n.dart:57-61` (AZ hata metinleri).
- **Bulgu:** `bulgular/Y_nav.md` §Y5 ve §Y6 satır satır doğrulandı; düzeltmeler 8.13'te. YolGo'da bu iki özelliğin kodu birdir (aynı ayrıştırıcı, aynı `onExternalText`); bu bölüm ikisini birlikte verir. Rotanın kendisi Bölüm 3, navigasyon Bölüm 7.

### 8.2 Bağlantının gelişi (Y5)

Activity `.MainActivity`: `exported=true`, `launchMode=singleTop`, `taskAffinity=""` (manifest :27-35). Filtreler (hepsi `DEFAULT`; VIEW'lerde ayrıca `BROWSABLE`; `autoVerify` YOK):

| # | Eylem | Veri | YG satır |
|---|---|---|---|
| 1 | MAIN + LAUNCHER | — | :40-43 |
| 2 | VIEW | şema `geo` | :46-51 |
| 3 | VIEW | şema `google.navigation` | :53-58 |
| 4 | VIEW | şema `waze` | :61-66 |
| 5 | VIEW | https `waze.com`, `www.waze.com` | :67-73 |
| 6 | VIEW | https `www.google.com`, pathPrefix `/maps` | :74-79 |
| 7 | VIEW | https `maps.google.com` | :80-85 |
| 8 | VIEW | https `maps.app.goo.gl` | :86-91 |
| 9 | VIEW | şemalar `yandexnavi`, `yandexmaps` | :92-98 |
| 10 | VIEW | https `yandex.az`, `yandex.ru`, `yandex.com`, pathPrefix `/maps` | :99-106 |
| 11 | VIEW | https `maps.yandex.ru`, `maps.yandex.com` | :107-113 |
| 12 | SEND | mimeType `text/plain` | :115-119 |

- `PROCESS_TEXT` **filtre değil**, yalnız `<queries>`'de (:164-167) (bulgu düzeltmesi; `VERI_SOZLESMESI` 2'deki "PROCESS_TEXT `text/plain`" filtre satırı da düzeltilmeli).
- `autoVerify` olmadığından https bağlantıları için kullanıcı Android "Varsayılan olarak aç" ayarından izin verir; `openLinkSettings` (`MainActivity.kt:434-450`): API ≥31'de `ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`, olmazsa `ACTION_APPLICATION_DETAILS_SETTINGS` (`package:<paket>`, `FLAG_ACTIVITY_NEW_TASK`); ilk açılan kazanır. Ayarlar ekranından açılır (`settings_screen.dart:443`, Bölüm 6).
- Metin (`extractLink`, :469-476): VIEW → `intent.dataString`; SEND → `EXTRA_TEXT`; başka eylem → yok.
- `linkOf` (:463-467): `IncomingLink.payload(metin, action, referrerPackage(getReferrer()), System.currentTimeMillis())`. Gönderen yalnız o intent işlenirken bilinir (`getReferrer`).
- Soğuk açılış (:95): intent teklif açılışı (`CourierFeedBridge.isLaunch`) değilse `pendingLink = linkOf(intent)`. Flutter `getInitialLink` çağırınca (:105-109) `linksReady = true`, bekleyen bir kez verilir ve silinir.
- Uygulama açıkken (`onNewIntent`, :452-460): `setIntent`; teklif açılışıysa dur; `linksReady` ise hemen `onLink`, değilse `pendingLink` (yalnız **son** bağlantı tutulur).
- Yük (`IncomingLink.payload`, `NavFeedProvider.kt:136-141`): `{text, action: "view"|"send"|"other", referrer, atMs}`. `referrerPackage` (:130-134): yalnız `android-app://` öneki; ilk `/`'a kadarki ad `[A-Za-z0-9_.]{1,100}` ile **tam** eşleşmeli, aksi null; yükte `referrer` aynı desenle yeniden süzülür.
- Dart `IncomingLink.parse` (`clipboard_sniffer.dart:115-126`): düz String (eski köprü) → kırpılmış metin, `view=true`; Map → `text` kırpılmış ve dolu olmalı, `view = action == "view"`, `referrer` `^[A-Za-z0-9_.]{1,100}$` değilse null, `at` = `atMs` int ve >0 ise.
- Teslim (`nativeBridgeProvider`, :86-96): `onExternalText(text, source: "intent", origin: view ? "intent" : "clipboard", referrer, requestedAt: at)` → 8.7.
- **WEEM:** aynı 12 filtre `:app` `MainActivity`'de (`VERI_SOZLESMESI` 2: "aynı filtreler"); teklif açılışı (`CourierFeedBridge`) kalkar (S7). `IncomingLink` saf (`logic/map` `input`). Android `LinkIntake` (`:map`) `onCreate`/`onNewIntent`'ten çağrılır; "linksReady" yerine harita hazır olana dek tek elemanlı bekleyen (yenisi eskisinin üstüne yazar). `NavOrigin` eşlemesi (`logic/core` `NavMessages.kt`): VIEW → `INTENT`, SEND ve pano → `CLIPBOARD`, arama → `SEARCH`, haritadan seçim → `MAP`, sipariş hedefi → `ORDER`.

### 8.3 Pano (Y6)

- `ClipboardSniffer` (`clipboard_sniffer.dart:26-84`): uygulama öne gelince (`AppLifecycleListener.onResume`) ve ilk karede `check()`; aynı anda tek denetim (`_busy`). Android 10+ panoyu yalnız öndeki uygulamaya verir; arka planda okuma yok.
- Okuma: `text/plain`, `trim`, boşsa yok; ilk okuma boşsa 400 ms sonra bir kez daha (odak geç gelir).
- Tekrar önleme: FNV-1a 32 bit, UTF-16 kod birimleri üstünde: `h = 0x811c9dc5`; her birim için `h ^= c; h = (h * 0x01000193) & 0xFFFFFFFF` (:74-81). `clipboard.lastHash` eşitse işlenmez; değilse önce hash yazılır, sonra `onExternalText(metin, source: "clipboard")`.
- `check(force: true)` ("Panodan oku") kodda var ama `lib/`'de çağıran yok (ölü).
- Pano metni ayrıştırılamazsa **sessiz** (8.7); serbest metin ancak "güçlü" ise adres sayılır (8.4 adım 4), sohbet mesajı ya da sipariş kodu rota çizmez.
- **WEEM:** `:map` `ClipboardWatch` (süreç öne gelince + Activity odağı; 400 ms tek tekrar); hash saf `ClipboardHash.fnv1a(String): Long` (0…2³²−1).

### 8.4 Ayrıştırma (`AddressParser`, saf)

Sonuç türleri (`address_parser.dart:5-33`): `CoordinateDestination(raw, point, label?, unitNote?)`, `ShortLinkDestination(raw, url, unitNote?)`, `AddressDestination(raw, query, strong, unitNote?)`.

`parse(input)` (:108-155): `raw = trim(input)`; uzunluk 5–800 dışıysa null. Sıra:

0. **Uygulama şemaları** (`_parseNavUri`, :164-197). İlk `:`'den önceki küçük harfli şema `_appSchemes` = {`waze`, `yandexnavi`, `yandexmaps`, `dgis`, `here-route`, `here-location`, `here.directions`, `comgooglemaps`} (:158-161) içindeyse `_parseAppScheme`. Değilse yalnız `geo:` ve `google.navigation:`: gövde = ilk `:` sonrası; `?` varsa yol ve sorgu ayrılır; `?` yoksa `google.navigation:`'da gövdenin tamamı sorgudur (`geo:`'da sorgu boş). Sorgu `splitQueryString` (hata → boş); `q ?? daddr ?? ll` içinde `_pair` varsa `_orient` → `CoordinateDestination(label = ilk "(…)" içeriği)`; çift yoksa ve kırpılmış uzunluk ≥4 ise `parse(q) ?? AddressDestination(raw, withCity(q.trim()), strong=true)`; son çare yoldaki ilk `_pair` (`0,0` reddedilir).
   - `_parseAppScheme` (:202-223): sorgu parametreleri (hata → boş). Öncelik: `lat_to`+`lon_to` → `rtext`'in son `~` parçasındaki son çift → `ll, daddr, q, destination, to, pt` (her birinde son çift) → yoldaki son çift → ham metindeki son çift. "Son çift" (`_lastPair`, :226-233) = `_pair` eşleşmelerinden `_orient`'i geçen sonuncusu. Hiçbiri yoksa `q ?? text ?? daddr` (kırpılmış, ≥4) → `parse(text) ?? AddressDestination(raw, withCity(text), strong=true)`.
1. **Metindeki linkler** (:117-127): `_url`'in her eşleşmesi; sondaki `).,;!?` dizisi silinir; `http` ile başlamıyorsa `https://` eklenir; URI çözülemezse sıradaki. Sırayla: `coordsFromUri` → koordinat; `isShortMapLink` → kısa link; `textQueryFromUri` → `parse(q) ?? AddressDestination(raw, withCity(q), strong=true)`.
   - `coordsFromUri` (:246-296), öncelik: (1) `decodeFull(url)` içinde `!3d<lat>!4d<lon>` (gerçek pin); (2) OSM `mlat`+`mlon`; (3) `rtext` son `~` parçasının son çifti; (4) `lat_to`+`lon_to`; (5) `q, query, destination, daddr, ll, pt, center, sll` sırasıyla, her birinde ilk `_pair`; (6) yolda `dir` segmenti varsa **yalnız son durak** (`@` ve `data=` ile başlayan segmentler atılır); son durakta çift yoksa null (metin sorgusuna/geokoda düşer; başlangıç ya da `@` görünüm merkezi hedef sayılmaz); (7) yol segmentleri sondan başa, `@` ile başlayanlar atlanarak ilk çift (2GIS `/to/boylam,enlem`, HERE `/r/enlem,boylam`); (8) `@enlem,boylam` görünüm merkezi. Bozuk `%` kodlaması → null.
   - `isShortMapLink` (:298-304): host `maps.app.goo.gl`; ya da `goo.gl` ve path `/maps` ile başlar; ya da `g.co`; ya da host'ta `yandex` ve path'te `/-/`.
   - `textQueryFromUri` (:307-326): yalnız host `waze.com`, `*.waze.com`, `yandex.az`, `yandex.ru`, `yandex.com`, `maps.yandex.ru`, `maps.yandex.com`, `google.com`, `*.google.com`. Parametreler `q, query, destination, daddr, text` (kırpılmış, ≥4, `loc:` ile başlamayan) → `/dir/` son durağı (`+` → boşluk) → `/place/<ad>` (`+` → boşluk).
2. **Plus Code** (:130-134): `PlusCode.pattern` ilk eşleşmesi `looksReal` ise (en az bir harf `[CFGHJMPQRVWX]`, "2345+67" gibi sayıları eler) `recover(kod, Baku.center)` → AZ içindeyse `CoordinateDestination(label = raw)`.
3. **Çıplak koordinat** (:137-141): `_pairStrict` ilk eşleşmesi → `_orient`.
4. **Serbest adres** (:144-154): metinde URL varsa null (harita dışı link, ör. Instagram); `_extractUnit` → `_clean`; `\p{L}{2,}` yoksa ya da uzunluk 4–250 dışıysa null; **güçlü değilse null**: `_streetWord` ya da `_district` ya da `_streetNumber` eşleşmeli. Sonuç `AddressDestination(raw, withCity(query), strong=true, unitNote)`.

Yardımcılar:
- `_orient(a, b)` (:329-336): iki sayı; (a, b) AZ kutusundaysa o, değilse (b, a) (Yandex/2GIS boylam-enlem sırası); ikisi de dışarıdaysa null → yalnız Azerbaycan içi hedef. Kutu `isInAzerbaijan`: enlem 38.3–42.0, boylam 44.7–51.0 (`geo.dart:45-46`).
- `_extractUnit` (:338-351): önce **bütün** metinde `İ` → `i` (Dart'ın büyük/küçük harf eşlemesi `İ`'yi `i` saymaz); `_unitRules` sırayla (`Mənzil`, `Mərtəbə` iki kalıp, `Giriş`, `Kod`; :89-95), her eşleşme metinden silinir (yerine boşluk); etiket başına **ilk** değer tutulur; not = `"<etiket> <değer>"`lerin kural sırasıyla ` · ` ile birleşimi (ör. `Mənzil 45 · Mərtəbə 7 · Giriş 2 · Kod 1234`).
- `_clean` (:353-364): telefon (`_phone`) → etiketler (`ünvan|unvan|address|adres|адрес` + `:`, :97) → parantez içi → `[\r\n\t]+` → `, ` → kısaltmalar (:99-105: `küç.|kuc.` → `küçəsi`, `pr.|prosp.` → `prospekti`, `şos.` → `şossesi`, `məh.` → `məhəlləsi`, `mkr.|m/r` → `mikrorayon`) → boşluklar teke → virgül dizileri tek `, ` → baş/sondaki `[\s,.;:–-]` atılır.
- `_withCity(q)` (:366): `_city` (`bakı|baki|baku|баку|sumqayıt|xırdalan|abşeron`, :86) yoksa `q + ", Bakı"`.
- `parseExplicit` (:38-44; elle arama): `parse` sonuç vermezse metin 2–250 karakter, ≥2 harf ve URL'siz ise `AddressDestination(raw, raw, strong=true)` (mekân adı; şehir eklenmez).
- Listeler `_streetWord` (:65-72), `_district` (:74-84, `az\d{4}` posta kodu dahil), `_city`, `_unitRules`, `_labels`, `_abbr`, `_appSchemes` Kotlin'e **harfiyen** kopyalanır.

Desenler ve WEEM yazımı (plan Bölüm 5: her desen ICU testinden geçer; kural `CAPTURE.md` 7.5). Dart `\s` Unicode boşluk (NBSP dahil), `\d` ve `\b` ASCII'dir; JDK 21 `\s`/`\b` ASCII, Android ICU Unicode'dur. WEEM yazımı Dart sonucunu hem JVM testinde hem cihazda verir:

| Desen (YG satır) | Dart | WEEM Kotlin |
|---|---|---|
| `_url` (:45) | `(?:https?://\|www\.)[^\s<>"']+` (i) | `[^\s\p{Z}<>"']+` |
| `_pair` (:46), `_pairStrict` (:48) | `(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)`; `(-?\d{1,2}\.\d{3,})\s*[,;]\s*(-?\d{1,3}\.\d{3,})` | `\d` → `[0-9]`, `\s` → `[\s\p{Z}]` |
| `_pin3d4d` (:49), `_atPair` (:50) | `!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)`; `@(-?\d+\.\d+),(-?\d+\.\d+)` | `\d` → `[0-9]` |
| `_phone` (:51-52) | `(?:\+?994\|\b0)[\s-]?\(?\d{2}\)?[\s-]?\d{3}[\s-]?\d{2}[\s-]?\d{2}` | `\b0` → `(?<![A-Za-z0-9_])0` (Dart'ın ASCII sınırı; ICU'nun `\b`'si "а0…" gibi Kiril harfe bitişik sayıda farklı), `\s` → `[\s\p{Z}]`, `\d` → `[0-9]` |
| `_hasLetters` (:53), `_streetNumber` (:56-58) | `\p{L}{2,}`; `^\s*\p{L}[\p{L}\s.'’-]{2,40}?\s\d{1,4}(?:[/-]\d{1,4})?\s?[\p{L}]?\s*(?:,\|$)` (u) | `\s` → `[\s\p{Z}]`, `\d` → `[0-9]` |
| `_kw(alt)`, `_r(p)` (:60-63) | `(?<!\p{L})(?:alt)(?!\p{L})`, `(?<!\p{L})(?:p)` (i, u) | aynı; `RegexOption.IGNORE_CASE` (Kotlin/JVM Unicode harf eşlemesi ekler) |
| `PlusCode.pattern` (`plus_code.dart:9-11`) | `(?<![0-9A-Z])([23456789CFGHJMPQRVWX]{4,8}\+[23456789CFGHJMPQRVWX]{2,3})(?![0-9A-Z])` (i) | aynı (IGNORE_CASE) |
| `_clean`/`_snippet` içi | `\([^)]*\)`, `[\r\n\t]+`, `\s+`, `(?:\s*,\s*)+`, `^[\s,.;:–-]+\|[\s,.;:–-]+$` | `\s` → `[\s\p{Z}]` |

Dart `Uri` davranışı da şartnamedir: WEEM `java.net.URI` kullanmaz (boşluk, `|`, `{` gibi karakterleri reddeder); saf `LinkUri`: şema, küçük harfli host, `%`-çözülmüş yol segmentleri, sorgu parametreleri (`+` → boşluk, `%XX` UTF-8; aynı anahtarda **son** değer; bozuk kodlama → o adımda parametre yok, istisna yok), `decodeFull` (`+`'ya dokunmaz).

### 8.5 Plus Code (saf, ağsız)

`plus_code.dart`: alfabe `23456789CFGHJMPQRVWX`; çift değerleri `[20, 1, 0.05, 0.0025, 0.000125]`.
- `decode(kod)` (:20-41): büyük harf, `+` silinir, en az 10 karakter; 5 çift: `lat = -90 + Σ ...`, `lon = -180 + Σ ...`; sonraki ızgara hanelerinde hücre enlemde /5, boylamda /4; `lat += (d div 4)·hücre`, `lon += (d mod 4)·hücre`; sonuç hücre merkezi. Bilinmeyen harf → null.
- `_prefix(lat, lon, n)` (:44-59): `lat` [−90, 89.9999999]'a sıkıştırılıp +90; `lon = ((lon+180) mod 360 + 360) mod 360`; her çiftte `floor(x/pv)` 0–19'a sıkıştırılır.
- `recover(kod, ref)` (:62-86): `+` yoksa null; `+` 8. konumda ya da sonra → `decode`; dolgu `8 − konum` tekse null; `prefix(ref, dolgu) + kod` çözülür; çözünürlük `20^(2 − dolgu/2)`; referans hücre sınırına yakınsa en yakın hücre (`ref ± çözünürlük/2` dışındaysa ±çözünürlük kaydırılır).
- Referans `Baku.center = (40.4093, 49.8671)` (`geo.dart:103`). Örnek: `8HGF9RQW+344` ve `recover("9RQW+344")` → (40.38764, 49.84533).

### 8.6 Çözüm ve geokod

`DestinationResolver.resolve(parsed, near, depth = 0)` (`destination_resolver.dart:63-110`):
- Koordinat → `ResolvedDestination(point, label = kısaEtiket(label) ?? "Pin <lat 5 hane>, <lon 5 hane>", confidence = high, unitNote, source = "coord")`.
- Kısa link → `depth > 1` ise `ResolveException("Kısa link çözülemedi")`; `_expand` (:113-127): en çok 5 `GET`, yönlendirme elle (`followRedirects = false`), her biri 6 sn, gövde atılır; `Location` yoksa dur; göreli adres çözülür; host `consent.` ile başlıyorsa `continue` parametresi izlenir; adreste koordinat bulununca dur. Sonra `parse(son adres)`; null ya da yine kısa link → `ResolveException("Linkte konum bulunamadı")`; değilse `depth + 1` ile özyineleme, `unitNote` öncelik dıştaki.
- Adres → `geocoder.search(query, near)`; null → `ResolveException("Adres bulunamadı: <query>")`; `label = kısaEtiket(hit.label) ?? query`; `confidence = strong ? hit.confidence : low`; `source = hit.source`; `restaurant = hit.restaurant`.
- `kısaEtiket` (:106-110): virgülle böl, kırp, boşları at, ilk iki parça `", "` ile.
- `near` = son taze konum (≤30 sn, doğruluk ≤65 m; Bölüm 7) ?? `Baku.center`.

Zincir (`destinationResolverProvider`, :16-27): `googleKey` doluysa `GoogleGeocoder` → `orsKey` doluysa `OrsGeocoder` → her zaman `NominatimGeocoder`. `FallbackGeocoder` (`geocoder.dart:43-63`): sırayla dener; istisna ya da null → sıradaki; güveni `low` değilse hemen döner; hepsi `low` ise ilk `low`.

| Sağlayıcı | İstek (6 sn) | Sonuç kuralları |
|---|---|---|
| Google (`:66-114`) | `GET https://maps.googleapis.com/maps/api/geocode/json?address=<q>&bounds=40.25,49.55\|40.65,50.45&language=az&key=<kullanıcı anahtarı>` | 200 ve `status=OK` değilse null; ilk sonuç. "İnce" türler {`street_address`, `premise`, `subpremise`, `route`, `establishment`, `point_of_interest`, `intersection`, `plus_code`}; ince değil ve `locality`/`country`/`administrative_area*` varsa null. Güven: ince değil → low; `ROOFTOP`/`RANGE_INTERPOLATED` → high (`partial_match` → medium); `GEOMETRIC_CENTER` → medium; diğer → low. Etiket `formatted_address`. Restoran: `restaurant`/`cafe`/`meal_takeaway` → true; `street_address`/`subpremise` → false; aksi null. Ülke filtresi (`components`) YOK. **APK anahtarı kullanılmaz.** |
| ORS (`:117-160`) | `GET https://api.openrouteservice.org/geocode/search?api_key=<anahtar>&text=<q>[&focus.point.lat&focus.point.lon]&size=1` | özellik yoksa null; katman {`country`, `macroregion`, `region`, `macrocounty`, `county`, `localadmin`, `locality`, `borough`} → null; `street`/`neighbourhood` → low; `confidence` ≥0.8 high, ≥0.5 medium, aksi low; etiket `properties.label`. |
| Nominatim (`:163-200`) | `GET https://nominatim.openstreetmap.org/search?q=<q>&format=jsonv2&viewbox=49.55,40.65,50.45,40.25&limit=1&addressdetails=0&accept-language=az`, başlık `User-Agent: YolGo/1.11.0 (personal courier navigation)` | tür = `addresstype ?? type`; kaba {`country`, `state`, `region`, `province`, `county`, `municipality`, `city`, `town`, `village`, `city_district`, `district`, `borough`, `administrative`} → null; `house`/`building` ya da `class=amenity` → high; `road`/`street`/semt {`suburb`, `quarter`, `neighbourhood`, `residential`, `hamlet`, `locality`, `isolated_dwelling`} → low; aksi medium. Restoran: `type` ∈ {`restaurant`, `fast_food`, `cafe`} → true; tür ∈ {`house`, `apartments`, `residential`} → false. `viewbox` yalnız yön verir (`bounded` yok). |

### 8.7 Hedefin kabulü (`NavController`)

`onExternalText(text, source, origin?, referrer?, requestedAt?)` (`nav_controller.dart:276-342`):
1. `asked = requestedAt ?? şimdi`.
2. `source == "intent"` → sürüş günlüğüne `intent {scheme, host}` (adres yazılmaz).
3. `parse(text)`; null ise `source != "clipboard"` olduğunda turuncu bildirim (simge `link_off`): "Bu linkte konum bulunamadı (<şema>)" / "Bu linkdə ünvan tapılmadı (<şema>)" (şema boşsa parantez yok); panodan gelende sessiz.
4. `external {src, kind: coord|link|address}`; seçim titreşimi; meşgul metni "Adres çözülüyor…" / "Ünvan axtarılır…".
5. `resolve(parsed, near)`; `resolved {src, by: coord|google|ors|osm, conf: high|medium|low, dm?}` (`dm` = konuma uzaklık, yuvarlanmış m).
6. `Stop(point, label, unitNote, confidence, restaurant, origin = origin ?? (source == "clipboard" ? "clipboard" : "intent"), requestedAt = asked, sourcePackage = referrer)`.
7. **<60 m kuralı:** `dm < 60` ve hedef restoran değilse rota çizilmez (meşgul kapanır, titreşim), hata:
   - TR: `Adres bulunduğun yere çıktı (N m), yanlış olabilir.\nGelen metin: «<metin>»\nDoğru yeri haritada uzun basıp "Buraya git" ile seç.`
   - AZ: `Ünvan olduğun yerə düşdü (N m), səhv ola bilər.\nGələn mətn: «<metin>»\nDüzgün yeri xəritədə barmağını saxlayıb "Bura get" ilə seç.`
   - `<metin>` (`_snippet`, :381-384): boşluklar teke, 80 karakterden uzunsa ilk 80 + `…`.
   - Restoran hedefi (`_restaurantTarget`, :420-430): çözümdeki `restaurant` bayrağı; null ise ve kapı notu yoksa 8 m içinde `r`, `f`, `c` türünde POI (Y16, Bölüm 5).
8. Durak yoksa → `stops = [stop]`, `arrived`/`reached` silinir, `_bindShiftTarget` (Bölüm 7), `replan()`. Durak varsa → `pending = stop`.
9. İstisna → meşgul kapanır, `error = _humanize(e)`, titreşim.

`acceptPending(addStop)` (:345-352): true → durak sona eklenir (çifte sipariş, `reached` korunur); false → tek durak (yeni rota, `reached` silinir); `_bindShiftTarget`, `replan`. `dismissPending` yalnız kartı kapatır. Kart (`_Pending`, `widgets.dart:493-524`): "Yeni adres geldi" / "Yeni ünvan gəldi", etiket (en çok 2 satır), dolu düğme "Durak ekle" / "Ünvan əlavə et", çerçeveli "Yeni rota" / "Yeni marşrut", kapat ×. Hata ya da bekleyen gelince rota paneli kendiliğinden açılır (`map_hud.dart:89-91`).

`search(text)` (:356-379; arama çubuğunda Enter): `parseExplicit`; null → bildirim "Adres, mekân adı, koordinat veya harita linki yaz" / "Ünvan, obyekt adı, koordinat və ya xəritə linki yaz"; meşgul "Adres aranıyor…" / "Ünvan axtarılır…"; sıra numarası (`_planSeq`) eski yanıtı atar; `Stop.origin = "search"`; doğrudan yeni rota (bekleyen kart yok, <60 m denetimi yok).

`_humanize` (:976-986): `RoutingException`, `ResolveException`, `LocationUnavailable` → mesajı (AZ'de `trMsg`); `RouteWorkerTimeout` → "Rota hesaplaması çok uzun sürdü; tekrar deneniyor"; `StateError` → "Rota hesaplanamadı; tekrar dene"; zaman aşımı → "Ağ zaman aşımı — hotspot bağlı mı?" / "Şəbəkə cavab vermir — hotspot qoşuludur?"; HTTP istemci hatası → "İnternet yok" / "İnternet yoxdur"; diğer → `toString`. Bu bölümün AZ karşılıkları (`i18n.dart:57-61`): "Qısa link açılmadı", "Linkdə ünvan tapılmadı", "Ünvan tapılmadı" (+ `: <sorgu>`), "Məkan icazəsi yoxdur və ya GPS sönülüdür", "GPS siqnalı tutulmadı" ("GPS konumu eski veya belirsiz" için AZ yok → 8.13).

### 8.8 Yer arama (`PlaceSearch`) ve Google Places (New)

**Arama çubuğu** (`map_hud.dart:14-76`): ipucu "Adres veya mekân ara" / "Ünvan və ya obyekt axtar"; yazdıkça `query`; boşken dokununca `query("")` (son aramalar); Enter ya da → düğmesi ("Rota çiz" / "Marşrut çək") → `NavController.search`. Öneri listesi alan odaktayken ve (metin ≥2 ya da öğe var) iken; yükseklik ≤ ekranın %27'si; satır simgesi çevrimdışıysa `offline_pin`, değilse `location_on`; hata satırı (11 pt); öğe yoksa "Sonuç bulunamadı" / "Nəticə tapılmadı"; Google öğesi varsa altta "Google Maps" atfı. Seçim → `select` → nokta varsa `goTo(nokta, label = başlık, restaurant, origin "search")`; `restaurant` = vardiyada sipariş varsa aşama `toRestaurant`/`atRestaurant` mi, yoksa önerinin bayrağı (:35-38).

**`query(value)`** (`place_search.dart:72-83`): kırp; aynı ve boş değilse yok say; debounce iptal; sıra++. Uzunluk <2 → boşsa son aramalar, 1 karakterse **boş liste** (`offline = true`). Oturum belirteci yoksa yeni (UUID v4, `newPlacesSessionToken`, `places_rest.dart:55-60`); meşgul; 350 ms sonra `_search`.

**`_search`** (:105-140): `near` = son konum ?? `Baku.center`.
- `googleKey` boş **ve** `orsKey` dolu → ORS: `GET https://api.openrouteservice.org/geocode/autocomplete?api_key&text&focus.point.lat&focus.point.lon&size=6` (6 sn; 200 değilse hata). Önbellek: anahtar `<orsKey>/<sorgu>/<lat 2 hane>,<lon 2 hane>`, 60 sn, en çok 32 kayıt (dolunca ilk eklenen atılır). Öğe (`_pelias`, :174-179): id `gid ?? id`, başlık `name ?? label`, adres `label`, nokta, restoran `category` ∈ {`restaurant`, `food`, `cafe`}.
- Aksi → Google Places autocomplete (kimlik aşağıda).
- `PlacesFailure` → kısa metin (tablo); başka her hata (Error dahil) → "Arama tamamlanamadı · çevrimdışı sonuçlar" / "Axtarış alınmadı · oflayn nəticələr". Hata varsa çevrimdışı dizin (o da düşerse son aramalar). Sıra ya da sorgu değiştiyse yanıt atılır.

**Çevrimdışı dizin** (`buildOfflineIndex`, :181-198; ayrı iş parçacığı): adı olan POI'ler (id `osmRef`, boşsa `"lat,lon"`; adres "Bakü · kayıtlı mekân" / "Bakı · qeydə alınmış obyekt"; restoran = tür `r|f|c`) + graf yolları (OSM way başına bir; id `way/<id>`; adres "Bakü · sokak" / "Bakı · küçə"; nokta = ilk kenarın `from` düğümü). Etiketler ana iş parçacığında dile göre seçilir. Eşleşme: `normalizeSearch` (küçük harf; `ı→i, ə→e, ş→s, ç→c, ğ→g, ö→o, ü→u`; kırp; :172-173) sorgu sözcüklerinin **hepsi** `başlık + " " + adres` içinde; sıra: başlığı sorguyla başlayan önce, sonra konuma uzaklık; id tekil; ilk 6. Graf ya da POI düzeltmeleri değişince dizin yeniden kurulur.

**`select(öneri)`** (:141-169): Google ise ayrıntı → nokta, adres (boşsa önerininki), restoran. Son aramalar: Google öğesi için **yalnız** `{id, title = kullanıcının yazdığı sorgu, address = "Son arama · bağlantı gerekir" / "Son axtarış · internet lazımdır", source "google"}` (nokta ve ayrıntı kalıcı yazılmaz: Google şartı); diğerleri seçilen öğenin tamamı. Liste başa eklenir, id tekil, en çok 12, `search.recents.v2`; oturum biter. Hata → "Mekânın konumu alınamadı · tekrar dene" / "Obyektin yeri alınmadı · yenidən yoxla".

**Places (New) REST** (`places_rest.dart`), host `places.googleapis.com`:
- Autocomplete `POST /v1/places:autocomplete`, gövde `{input, sessionToken?, languageCode: "tr"|"az" (dil), regionCode: "az", locationBias: {circle: {center: {latitude, longitude}, radius: 30000.0}}}`, 6 sn.
- Ayrıntı `GET /v1/places/<id>?sessionToken&languageCode&regionCode=az`, başlık `X-Goog-FieldMask: id,location,formattedAddress,types`, 8 sn; id boş ya da `/` içeriyorsa hata.
- Başlıklar: `X-Goog-Api-Key`; doluysa `X-Android-Package`, `X-Android-Cert` (imza SHA-1, onaltılık, büyük harf, iki noktasız).
- Kimlik (`googleCredentialsProvider`, `place_search.dart:40-44`; `ApkGoogleConfig`, `places_rest.dart:36-51`): Ayarlar'daki anahtar varsa o + APK'nın paket ve SHA-1'i; yoksa APK anahtarı (`googleRestConfig`, `MainActivity.kt:130-144`: `AIza` ile başlamıyorsa boş). Anahtar yoksa `missingKey`, **istek atılmaz**.
- `_send` (:123-139): zaman aşımı → `unavailable` (`TIMEOUT`); soket/istemci hatası → `offline`; 200 değilse `classify`; gövde nesne değilse `unavailable` (`BODY`).
- `classify(status, body)` (:143-165): `error.details[]` içindeki ilk `reason` ve `error.status`: `API_KEY_INVALID|API_KEY_NOT_FOUND|API_KEY_EXPIRED` → keyInvalid; `API_KEY_ANDROID_APP_BLOCKED|API_KEY_HTTP_REFERRER_BLOCKED|API_KEY_IP_ADDRESS_BLOCKED|API_KEY_IOS_APP_BLOCKED` → appBlocked; `SERVICE_DISABLED|ACCESS_NOT_CONFIGURED` → apiDisabled; `API_KEY_SERVICE_BLOCKED` → apiBlocked; `BILLING_DISABLED|BILLING_NOT_ENABLED` → billing; `RATE_LIMIT_EXCEEDED|RESOURCE_EXHAUSTED` → quota; neden yokken HTTP 429 ya da durum `RESOURCE_EXHAUSTED` → quota; gövdede "API key not valid" → keyInvalid; aksi unavailable. Ham Google mesajı hiçbir yerde gösterilmez (anahtar/proje içerebilir).
- `parseAutocomplete` (:167-181): `suggestions[].placePrediction` yoksa atla; `placeId` boşsa atla; başlık `structuredFormat.mainText.text`, boşsa `text.text` (o da boşsa atla); adres `structuredFormat.secondaryText.text`. `parseDetails` (:183-190): `location.latitude/longitude` sonlu sayı değilse `FormatException`; restoran türleri {`restaurant`, `cafe`, `meal_takeaway`, `fast_food_restaurant`, `bakery`}.

| Tür | Kısa metin TR (`placesFailureShort`, :194-204) | AZ |
|---|---|---|
| offline | İnternet yok · çevrimdışı sonuçlar | İnternet yoxdur · oflayn nəticələr |
| missingKey | Google anahtarı yok · çevrimdışı sonuçlar | Google açarı yoxdur · oflayn nəticələr |
| keyInvalid | Google anahtarı geçersiz · çevrimdışı sonuçlar | Google açarı etibarsızdır · oflayn nəticələr |
| appBlocked | Anahtar bu APK’ya izin vermiyor · çevrimdışı sonuçlar | Açar bu APK-ya icazə vermir · oflayn nəticələr |
| apiDisabled, apiBlocked | Places API (New) kapalı · çevrimdışı sonuçlar | Places API (New) bağlıdır · oflayn nəticələr |
| billing | Google faturalandırma kapalı · çevrimdışı sonuçlar | Google ödənişi bağlıdır · oflayn nəticələr |
| quota | Google limiti doldu · çevrimdışı sonuçlar | Google limiti doldu · oflayn nəticələr |
| unavailable | Arama API’si kullanılamıyor · API ayarlarını kontrol et | Axtarış API-si işləmir · API tənzimləmələrini yoxla |

Ayrıntı metinleri (`placesFailureDetail`, :207-223) iki dilde aynen alınır; kod eki `" (<reason>)"`, yoksa `" (HTTP <status>)"`, status 0 ise boş; `appBlocked` metnindeki paket adı WEEM'de `com.kuryelog.app`.

### 8.9 "API ve bağlantılar" ekranı

`api_settings.dart` (başlık "API ve bağlantılar" / "API və bağlantılar"; Ayarlar'dan açılır, Bölüm 6):
- Kartlar: (1) "Google Maps · SDK": denetim `mapsStatus` (`MainActivity.kt:114-126`: APK'da `AIza` anahtarı var mı, son 4 hane, paket, iki noktalı SHA-1, Play Services kodu) → anahtar yoksa "APK içinde Google anahtarı yok; harita sade altlıkla çalışır." / "APK-da Google açarı yoxdur; xəritə sadə altlıqla işləyir."; Play Services 0 değilse "Google Play Services hazır değil (kod N)." / AZ; aksi `probeGoogleKey(apkOnly)` → `mapsKeyVerdict` (:149-162). (2) "Google Maps uyumluluk modu" anahtarı (`googleMapCompatibility`, Bölüm 6). (3) "Maps anahtarı / paket / imza bilgileri" → `showMapsHelp` (Bölüm 6). (4) "CARTO · Harita" anahtarı + denetim (`https://a.basemaps.cartocdn.com/dark_all/0/0/0.png?key=<anahtar>`; Bölüm 6). (5) "Google Places (New) · Adres arama" (`googleKey`) + denetim `probeGoogleKey` (sorgu "Baku", `Baku.center`, oturumsuz, 10 sn) → "Places araması yanıt verdi · N gerçek öneri." / "Places axtarışı cavab verdi · N həqiqi təklif." ya da `placesFailureDetail`. (6) "OpenRouteService · Arama yedeği" (`orsKey`) + denetim (`/geocode/autocomplete?text=Baku&size=1`). (7) "Rota motoru": graf yoksa "Yol ağı yüklenmedi." / "Yol şəbəkəsi yüklənməyib.", varsa "<N> yol parçası hazır" / "<N> yol hissəsi hazırdır". (8) "Open-Meteo · Hava durumu" (`weather.key.v1`; Bölüm 10). Altta paket adı + "Kontroller yalnızca bu düğmelere dokununca yapılır; 5 dk önbellek."
- Denetim (:41-51): aynı kart 5 dk içinde yeniden denetlenmez; 12 sn zaman aşımı ya da hata → "Bağlantı veya yetki doğrulanamadı; internet, API etkinliği ve anahtar kısıtlamasını kontrol et." / "Bağlantı və ya icazə təsdiqlənmədi; interneti, API aktivliyini və açar məhdudiyyətini yoxla."; sonucun altında "Son kontrol HH:mm".
- `mapsKeyVerdict`: başarı → "APK anahtarı geçerli ve bu uygulamaya izinli. Harita gri kalırsa projede Maps SDK for Android açık mı bak." / AZ; keyInvalid, appBlocked, missingKey, billing → ayrıntı + " Bu durumda Google harita da yüklenmez." / " Bu halda Google xəritəsi də yüklənmir."; apiDisabled, apiBlocked → "APK anahtarı Google’da tanınıyor; Places API (New) bu anahtara kapalı. Harita için Maps SDK for Android ayrıca açık olmalı." / AZ; diğer → ayrıntı.
- Anahtar penceresi (:29-40): gizli alan, göster/gizle, "Vazgeç"/"Ləğv et", "Kaydet"/"Yadda saxla"; kayıttan sonra denetim sonuçları silinir. `maskedKey` (:18-19): boş → "Anahtar girilmedi" / "Açar yazılmayıb"; aksi `••••` + son 4 karakter.

### 8.10 Veri

| Ad | Tür / biçim | YG | WEEM |
|---|---|---|---|
| `clipboard.lastHash` | int, FNV-1a 32 bit (0…2³²−1) | `clipboard_sniffer.dart:32` | `map` tercih dosyası, `Long` (aynı değer); S1: aktarılmaz |
| `search.recents.v2` | String JSON dizi (≤12): `{id, title, address, source: google\|ors\|offline, point: {lat, lon}\|null, restaurant}`; Google öğesinde nokta yok | `place_search.dart:85, 158-160` | `map`, aynı ad ve biçim |
| `settings.v1` → `googleKey`, `orsKey`, `cartoKey` | String, varsayılan `""` (kullanıcının anahtarı) | `settings.dart:34-36` | `MapSettings` aynı alan adları (Bölüm 14); değerler hiçbir kayda, dışa aktarmaya, belgeye yazılmaz |
| APK Maps anahtarı | `@string/google_maps_key` ← CI gizli değişkeni `GOOGLE_MAPS_API_KEY` (biçim `AIza[0-9A-Za-z_-]{35}`, değilse derleme durur; günlüğe yalnız son 4) | `ci/build.sh:87-104`; manifest :25-26 | aynı düzen (CI gizli değişkeni → kaynak; A6.5) |
| Sürüş günlüğü olayları | `intent {scheme, host}`; `external {src: intent\|clipboard, kind: coord\|link\|address}`; `resolved {src, by: coord\|google\|ors\|osm, conf: high\|medium\|low, dm?}` | `nav_controller.dart:282, 293-311` | aynı adlar ve alanlar (Y18, Bölüm 12) |
| Ağ | Google Geocoding; Places (New) autocomplete/ayrıntı; ORS `geocode/search` ve `geocode/autocomplete`; Nominatim `search`; CARTO karo (yalnız denetim); kısa link yönlendirmeleri (herhangi host) | 8.6, 8.8, 8.9 | aynı; User-Agent `WEEM/<versionName> (personal courier navigation)` |
| Intent filtreleri | 8.2 tablosu | manifest :40-119 | `:app` manifestinde aynı |
| Kanal `kurye_nav/native`: `getInitialLink`, `onLink`, `openLinkSettings`, `googleRestConfig`, `mapsStatus`, `openGoogleRoute` | Flutter köprüsü | `MainActivity.kt` | çevrilmez (doğrudan Kotlin); `openGoogleRoute` kalkar (S9) |

### 8.11 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| YT `address_parser_test` | 17 | Plus Code: tam kod `8HGF9RQW+344` ve `recover("9RQW+344", Baku.center)` → (40.38764, 49.84533) ±1e-4; Bolt "9RQW+344 Baku, Baku, AZ1000" → koordinat; Wolt "Mehdi Hüseyn 99" → güçlü, sorgu "Mehdi Hüseyn 99, Bakı"; "Təbriz 52, Баку" şehir korunur; İngilizce Bolt adresi güçlü; kapı notu + telefon → sorgu "Nizami küçəsi 12, Bakı", not "Mənzil 45 · Mərtəbə 7 · Giriş 2 · Kod 1234"; WhatsApp `q=`; Yandex `pt=` (boylam,enlem) çevrilir; `geo:…(Xengeland)` etiketi, `google.navigation:q=…&mode=b`, `geo:0,0?q=metin` → adres; kısa link; Waze (`ll`, web `ul`, `q` → adres); Yandex `lat_to`/`rtext` (uygulama ve web); 2GIS/HERE yol çiftleri; Google `/dir/` son durak (koordinat ve metin, görünüm merkezi değil); Waze/Yandex web metni → "Nizami küçəsi 12, Bakı"; 4 adres olmayan metin → null | `input.AddressParserTest`, `input.PlusCodeTest` (ilk 2 test) | A6.2b → A6.6 | saf; WEEM ek vektörleri 8.13 (NBSP, Kiril'e bitişik telefon, büyük harf, `BAKİ`) |
| YT `place_search_test` | 8 | çevrimdışı dizin gerçek nokta ve restoran türü; Places 500 → çevrimdışı, meşgul kalmaz; `SocketException`/`StateError`/`TimeoutException` her biri çevrimdışına düşer (çökme yok); AZ'de hata ve etiket AZ; kimlik yokken istek atılmaz, "anahtarı yok"; debounce 350 ms (3 yazımda 1 istek), eski yanıt atılır, oturum belirteci ayrıntıya taşınır (≤36, `[A-Za-z0-9_-]`), başlıklar (`X-Goog-Api-Key`, `X-Android-Package`, `X-Android-Cert`), FieldMask `location` içerir, son aramada ayrıntı adresi yok, boş sorguda son arama (başlık = sorgu, nokta null); `classify` 6 neden + kısa/ayrıntı metinlerinde anahtar ve "secret project" yok + ayrıntıda paket adı; `parseDetails`/`parseAutocomplete` | `input.PlaceSearchTest`, `input.PlacesRestTest` | A6.2b → A6.6 | saf: sahte `HttpPort`, sanal saat (debounce), sahte konum, boş graf, POI listesi (`nav_assistant_test`'teki `TestLocation`/`EmptyMap` karşılıkları, Bölüm 7); paket beklentisi `az.duzyol.app` → `com.kuryelog.app` |
| YT `geocoder_scope_test` | 1 | Google geokodda `components` (ülke filtresi) yok; Tbilisi (41.7, 44.8) `ROOFTOP` → high | `input.GeocoderTest` | A6.2b → A6.6 | sahte HTTP |
| YT `api_settings_test` | 1 | `mapsKeyVerdict`: başarı "geçerli"; keyInvalid/appBlocked "harita da yüklenmez"; apiDisabled "tanınıyor", "geçersiz" geçmez | `input.ApiChecksTest` | A6.2b → A6.6 | saf |
| YK `NavFeedContractTest` (6'nın 2'si) | 2 | `referrerIsAnAndroidAppPackage` (`android-app://paket[/…]`; null, boş, https, boş ad, boşluklu ad, 101 karakter → null); `aLinkCarriesItsActionReferrerAndTime` (view/send, referrer, `atMs`) | `input.IncomingLinkTest` | A6.2b → A6.6 | kalan 4 test (sağlayıcı sözleşmesi) Bölüm 7 |

Bu bölümün çeviri payı: **5 dosya, 29 test** (hepsi A6.2b, saf `logic/map`). `offer_navigation_removed_test` Bölüm 6/7'de değerlendirilir.

### 8.12 WEEM yerleşimi

- **`logic/map` paketi `input`:** `AddressParser` + `ParsedDestination` (mühürlü: `Coordinate`, `ShortLink`, `Address`), `PlusCode`, `LinkUri` (8.4 sonu), `IncomingLink` (Android'den saf hâle), `ClipboardHash`, `DestinationResolver` (+ `ResolvedDestination`, `ResolveException`), `Geocoder`, `FallbackGeocoder`, `GoogleGeocoder`, `OrsGeocoder`, `NominatimGeocoder` (istek kurma ve yanıt ayrıştırma saf; ağ `HttpPort` üstünden), `PlacesRest` (+ `PlacesFailure`, `classify`, `parseAutocomplete`, `parseDetails`, `sessionToken`), `PlaceSearch` (durum makinesi; debounce için saat/zamanlayıcı enjekte), `OfflineIndex` (+ `normalizeSearch`), `SearchRecents`, `ApiChecks` (`mapsKeyVerdict`, `maskedKey`, 5 dk kısıtı), `MapText` (iki dilli metinler, `logic/core` `Bilingual`; YolGo'nun `trMsg` alt dize çevirisi yerine her mesaj iki dilli yazılır).
- **`HttpPort`** (öneri, Y7/Y10 ile ortak; yeri Bölüm 15'te tekleşir): `suspend fun send(HttpRequest): HttpResponse` — yöntem, URL, başlıklar, gövde, zaman aşımı, `followRedirects` (kısa link için false).
- **Android `:map`** (`com.kuryelog.app.map`): `LinkIntake` (intent → `IncomingLink`; gönderen `Activity.getReferrer()`), `ClipboardWatch`, `AndroidHttp` (`HttpURLConnection`; yeni bağımlılık yok), `ApkGoogleConfig` (anahtar derleme kaynağından, paket, imza SHA-1 `GET_SIGNING_CERTIFICATES`).
- **`:app`:** `MainActivity` filtreleri, `onNewIntent` → `LinkIntake`; `MapTab` arama çubuğu ve bekleyen kartı; `MapApiSettingsScreen` (Daha fazla → Harita ayarları → API ve bağlantılar).
- **`NavPort.open(NavTarget)`:** başka modülden ya da öteki telefondan gelen hedef → `NavController.openTarget` = `goTo` + Stop alanları (`origin`, `kind` → `restaurant`, `requestedAtMs`, `sourcePackage`, `label`) + 8.7'deki <60 m denetimi ve durak varken bekleyen kart.
- **2 telefon (plan 4.5, S5):** Wolt/Bolt Provider'da olduğundan VIEW/SEND intent'i Provider'daki WEEM'e gelir. Provider ayrıştırır ve çözer (kendi interneti; kısa link ve geokod dahil), `NavTarget{requestId (UUID), lat, lon, kind (Stop.restaurant: true → restaurant, false → customer, null → unknown), origin INTENT|CLIPBOARD, sourcePackage, requestedAtMs = bağlantının geliş anı, label = çözülen etiket + kapı notu}` → P→R; Receiver `NavPort.open` ile rotayı açar. Receiver'a ulaşılamazsa rota Provider'da açılır. <60 m denetimi rotayı açan telefonda (konum oradadır). Pano her telefonda kendi panosu; elle arama hangi telefondaysa orada. Müşteri adı, telefonu ve notları `NavTarget`'a girmez (ayrıştırıcı telefonu zaten siler).
- `:capture` ağsız kalır; okunan müşteri adresinin Android Geocoder ile çözümü `:orders`'ta (`ORDERS.md`); `:map` geokod zinciri ondan ayrıdır.

### 8.13 Farklar ve açık noktalar

**Bulgu düzeltmeleri:** (1) `PROCESS_TEXT` Activity filtresi değil, yalnız `<queries>` (`Y_nav.md` ve `VERI_SOZLESMESI` 2). (2) Tek karakterlik sorgu son aramaları göstermez; yalnız boş sorgu gösterir. (3) Nominatim isteğinde `addressdetails=0` da var. (4) Satırlar: `getInitialLink` `MainActivity.kt:105-109`, `openGoogleRoute` :145-168, `openLinkSettings` :434-450, `onNewIntent` :452-460, `linkOf`/`extractLink` :463-476. (5) "Panodan oku" (`force`) ölü kod. (6) Ayrıştırıcı `strong=false` hiç üretmez; `DestinationResolver`'daki "zayıf adres → low" yolu fiilen ölü (davranış: güven = geokodun güveni).

**WEEM'de baştan doğru:**
- F-N1 Nominatim isteklerine en az 1 sn aralık (OSM kullanım kuralı; YolGo'da sınırlayıcı yok). Zincir seyrek çalıştığından kullanıcı fark görmez.
- F-N2 Desen taşınabilirliği (8.4 tablosu) ve `LinkUri`; WEEM'de ek test vektörleri: NBSP'li "Mehdi Hüseyn\u00A099" → güçlü (Dart `\s` NBSP'yi eşler); "а0501234567" (Kiril harfe bitişik telefon) → telefon silinir; büyük harf "MƏNZİL 45" → `Mənzil 45`, "КВ. 12" → `Mənzil 12`; "BAKİ" (noktalı büyük İ): YolGo'nun `İ → i` değişimi bütün metne uygulanır → "BAKi"; Kotlin ve Dart ikisi de bunu şehir sayar, `, Bakı` eklenmez (A6.6'da düzeltildi: önceki "Kotlin/Dart farkı" notu yanlıştı; `AddressParserTest` sabitler).
- F-N3 User-Agent `WEEM/<sürüm> (personal courier navigation)`; `appBlocked` ayrıntısındaki paket `com.kuryelog.app`.
- F-N4 Her kullanıcı metni iki dilli (`MapText`): "GPS konumu eski veya belirsiz" (`location_service.dart:171`) AZ karşılığı YolGo'da yok (AZ'de Türkçe görünür) → SORU-N2.

**Aynen korunan tutarsızlıklar:** Google Geocoding APK anahtarını kullanmaz, Places kullanır; `PlaceSearch` ORS'yi yalnız Google anahtarı boşken kullanır, geokod zincirinde ise ikisi sırayla; `GeoPoint.isInAbsheron` hiçbir yerde kullanılmaz (geokod sonucu Abşeron dışına düşebilir; `geocoder_scope_test` ülke filtresi olmamasını bekler); manifestte çıplak `google.com` ve `goo.gl` filtresi yok (ayrıştırıcı bu linkleri pano ve paylaşımda yine tanır).

**Geçiş notu:** paket `az.duzyol.app` → `com.kuryelog.app`: Android'in "Varsayılan olarak aç" izni ve Wolt/Bolt'taki navigasyon uygulaması seçimi WEEM için yeniden yapılır (Rawstats'ta bu filtreler yoktu; A10 geçiş adımı, kullanıcı işi).

**S9:** `openGoogleRoute` ve tek çağıranı (`task_controller.dart:112`, Y20 "Rota Oluştur") kalkar; WEEM'de "Rota Oluştur" `NavController.goTo` (Bölüm 13). Kanal yöntemi ve tarayıcı yedeği (`CATEGORY_APP_BROWSER`) de kalkar.

**SORU-N1 (koordinatöre):** kapı notundaki `Kod` (domofon) Receiver'a gitsin mi? S5 adres metninin (sokak, bina, kat, giriş) gittiğini, notların gitmediğini söylüyor; domofon kodu adres ayrıntısı mı not mu belli değil. (a) gider (kapıda Receiver ekranında gerekir; önerilen varsayılan); (b) gitmez (yalnız Provider'da gösterilir).

**SORU-N2 (koordinatöre):** YolGo'da AZ'si olmayan tek metin için AZ önerisi: "GPS mövqeyi köhnə və ya qeyri-dəqiqdir". Varsayılan: öneri kullanılır (S19 gibi kullanıcı onayına sunulur).
<!-- END sec:8 -->

<!-- BEGIN sec:9 -->
## 9. Y12 — Yol kalitesi öğrenme (A6.11; sıfırdan, S3)

Y12 = sürüşten ve sürücünün elle girdiklerinden sokak bilgisi biriktirip rota maliyetine katmak: elle kalite puanı (%), ters yön (ww) geçiş/sorun sayaçları ve "Sorun yok" onayı, "yol yok" (hayalet) parçalar, ana caddede kesim bazlı ters yön izni, otomatik dur-kalk istatistiği. Girişler: Ses+/Ses− tuşları, ekran kartları, harita uzun basış menüsü, Yollarım ekranı. **1.11.0 gerçeği:** otomatik dur-kalk/titreşim öğrenmesi (`roads.ways.v1`) rotayı **etkilemez** (yalnız ekranda); rotayı etkileyen Y12 girdileri elle puan, ww sayaçları, hayalet ve ww kesimidir (9.9). YolGo'daki "Yol puanları" (E10/E11 sokak puanı), "Haritada yüzey renkleri" ve kalibrasyon Y13/Y14'tür (9.8, 9.13). YolGo'nun iç değişiklik kodları "Y7" (yaya bölgesinden çıkış) ve "Y9" (ters yön temiz sürüş onayı, 8 Ekim saha) WEEM özellik kodu değildir; burada **YG-Y9** diye anılır. WEEM sıfırdan öğrenir: `roads.*` aktarılmaz (S1, S3), ilk açılışta bütün Y12 verisi boştur.

### 9.1 Kaynak ve kapsam

| Dosya (YG) | Satır | İçerik | WEEM |
|---|---|---|---|
| `lib/roads/road_knowledge.dart` | 1–593 | `WayStats` 25–59, `WwStats` 61–80, `GhostMark` 82–121, `RoadKnowledge` 123–251, `LearnFix` 258–286, `WwRunWay`/`WwRun` 288–317, `wwCleanApprovals` 320–331, `WwPass` 333–352, `RoadLearner` 365–593 | `logic/map/roads` birebir |
| `lib/roads/road_controller.dart` | 1–442 | `RoadController` (Riverpod): yükle/kaydet, vardiya kapsamı, soru akışı, elle eylemler, gün silme | saf çekirdek `roads.RoadBook` + Android bağ (9.12) |
| `lib/roads/day_contributions.dart` | 1–102 | `RoadDayContributions`: gün kovaları, gün silme | `roads` birebir |
| `lib/roads/quality_prompt.dart` | 1–77 | "%" sorusu, 6 sn otomatik kayıt | `roads.QualityPrompt` saf |
| `lib/roads/street_ratio.dart` | 1–56 | cadde kötü kesim oranı | `roads` (1.0'da yalnız kötü noktalarla) |
| `lib/roads/street_scores.dart` | 1–183 | E11 sokak puanı (Y13 parçaları + Y14 kalibrasyonu) | **sonra** (9.11, SORU-Q2) |
| `lib/routing/quality.dart` | 1–77 | `priorQuality`, `learnedScore`, `learnedConfidence`, `blendQuality`, `QualityRating`, ww oranları, `QualityBand` | `route/Quality.kt` (A6.4 yazar; `CostModel` ister) |
| `lib/surface/bad_spots.dart`; `surface_models.dart:16-37`; `surface_store.dart:92-93, 113, 284-287, 369-379` | — | Ses− kötü noktası (`SurfaceMark`), `manual-marks.json` | alt küme `logic/map/surface` (SORU-Q1) |
| `lib/ui/roads_screen.dart` | 1–700 | Yollarım (4 sekme), kalite sayfası, ww satırı, hayalet listesi/seçici, `WrongWayCard`, `QualityPromptCard` | `:app` Compose |
| `lib/ui/surface_screen.dart` | 46–97 | `SurfaceQualityTab` (Yollarım → Kalite gövdesi; çoğu Y13/Y14) | yalnız Y12 parçaları (9.8) |
| `lib/nav/nav_controller.dart` | 235–247, 480–506, 578–751, 761–779, 781–830 | yeniden rota tetikleri, plan girdileri, ses tuşları, hayalet önbelleği, öğrenme fix'i, ters yön önerisi | `nav` (Bölüm 7) + `roads` |
| `lib/tracking/map_matcher.dart` | 1–103 | eşleme, ters yön tespiti (öğrenmenin girdisi) | Bölüm 7 (ajan N); özet 9.3.1 |
| `lib/ui/nav_screen.dart` | 61–146, 155–330, 357–366 | kart yerleşimi, uzun basış menüsünün Y12 satırları, 👍/👎 | menü Bölüm 6; metinler 9.4, 9.8 |
| `lib/ui/settings_screen.dart` | 243–308 | Ayarlar → Yollarım akordeonu, ses tuşu anahtarı | `:app` Harita ayarları |
| `lib/core/settings.dart` | 40–43, 84–97, 150, 253–257, 301–305 | `volumeKeys`, `qualityTolerancePct`, `showQualityLayer`, `wwMaxProblemPct`, `wwAskAlways` | `settings.MapSettings` (Bölüm 14) |
| `android_overlay/MainActivity.kt` | 54–65, 110–113, 478–503 | ses tuşu yakalama | `:app` `MainActivity` + `:map` `VolumeKeys` |
| `lib/input/clipboard_sniffer.dart` | 86–100, 154–161, 238–242 | köprü `onVolume`, `onVolumeDownHold`, `setVolumeCapture` | doğrudan çağrı |
| `lib/tracking/day_deletion.dart` | 15–70 | gün silmede yol katkıları | Bölüm 12 (ajan T) + 9.3.5 |
| `lib/ui/road_score.dart` | 1–150 | `RoadScoreDisplay`, kalibrasyon istemleri | **Y14, Y12 değil** (sonra) |

Bulgu doğrulaması: `Y_contract.md:71` `roads.*` satırı (`road_controller.dart:53-58, 94-98`) doğru (`roads.days.v2`, `roads.days.damaged.v2` dizge olarak 91–99); `:83` "roads.* için dışa aktarma yolu bulunmadı" doğru (YolGo'da `roads.*` dışa aktarımı yok; S3 ile önemsiz). `Y_routing.md:35` "learned (Y12)" → **(bulgu düzeltmesi:** `learned` yalnız **elle puanları** taşır; `learnedForRouting()` `ratings`'ten `(q 0, c 0, r)` üretir, otomatik `WayStats` bilerek atlanır — `road_knowledge.dart:159-168`, yorum "traffic stops/vibration … must not affect surface routing costs"). `Y_routing.md:36` "Y12 (learned, wwRatio/wwAvoid — "sonra"), Y13/Y14 (surface, marks — "sonra") … Y18? (ghost …)" → **(bulgu düzeltmesi:** S3 ile Y12 1.0'da (A6.11): `learned`, `wwRatio`, `wwAvoid`, `ghost`, `wrongWayEdges` dolu gelir; `ghost` ve `wrongWayEdges` Y12'nin (`roads.ghost.v1`, `roads.wwseg.v1`), Y18'in değil; `marks` yalnız Y13 değil, Ses− kötü noktası da (SORU-Q1); `surface`/`surfaceQuality` boş (Y13/Y14)). `OZELLIK_HARITASI:161` Y12 eski kodundaki `road_score.dart` → **(düzeltme:** Y14 kalibrasyon UI'ı, Y12 değil). `TEST_ENVANTERI:227-238, 294` Y12 = 55 test doğru; bölüşüm 9.11 (SORU-Q2).

### 9.2 Kavramlar ve veri modeli

- **Kimlik:** sokak = OSM way id (`Long`, grafın `wayOsmId`); harita güncellense de kalır (`road_knowledge.dart:10`). Kenar kimliği haritaya özgüdür: kenara bağlı işaretler (hayalet, ww kesimi) **orta nokta koordinatıyla** saklanır, her haritada `nearestEdge(lat, lon, maxDist 30, accept: aynı way)` ile yeniden bulunur; `all` hayalet way'in bütün kenarlarıdır (`edgesOfOsmWay`) (`road_knowledge.dart:198-219`).
- **Kalite** 0 (kötü) – 1 (mükemmel) = akıcılık: az dur-kalk, az sarsıntı (`quality.dart:4-9`). Üç kaynak: OSM ön tahmini `priorQuality`, otomatik `learnedScore` (+ güven `learnedConfidence`), elle yüzde `r` (1–100, 0 = yok). `LearnedQuality = (q, c, r)`.
- **`QualityRating`** (`quality.dart:12-19`): `good = 90` (👍), `bad = 25` (👎), `none = 0`; eski 1/−1 → `migrate`: 1→90, −1→25, diğer aynen.
- **`WayStats`** (sokak başına, otomatik): `m` sürülen metre, `s` süre sn, `stops` dur-kalk (tam durma 1, durmadan yavaşlama 0,5), `vibSum`/`vibN` titreşim RMS toplamı/sayısı, `passes` geçiş, `lastAt` ms. Türetilen: `vib` = `vibN < 10 ? null : vibSum/vibN`; `stopsPerKm` = `m ≤ 0 ? 0 : stops/(m/1000)`; `avgKmh` = `s ≤ 0 ? 0 : m/s×3,6`; `score` = `learnedScore(m, stops, vib)`; `confidence` = `learnedConfidence(m)` (`road_knowledge.dart:25-59`).
- **`WwStats`** (yalnız onaylı sokakta ters yön): `passes`, `problems` (cevap "evet" 1; olaylı ama cevapsız 0,5), `lastAt`; `ratio` = ham `wrongWayRatio` (`road_knowledge.dart:61-80`).
- **`GhostMark`** "haritada var, gerçekte yok": `way`, `lat`/`lon` (parçanın orta noktası), `name`, `at` ms, `all` (sokağın tamamı). Aynı tip **ww kesimi** listesinde de kullanılır (`wwSegs`: ana caddede kesim bazlı ters yön izni; `all` hiç kullanılmaz) (`road_knowledge.dart:82-145`).
- **`RoadKnowledge`** = `ways: Map<Long, WayStats>`, `ww: Map<Long, WwStats>`, `ghosts`, `wwSegs: List<GhostMark>`, `ratings: Map<Long, Int>`, `names: Map<Long, String>` + `touchedWays`/`touchedWw` (gün katkısı için; `stats(way)`/`wwStats(way)` çağrısı ekler, yoksa yeni kayıt açar) (`road_knowledge.dart:123-154`). `nameOf(way)`: yok/boş → "isimsiz yol" / "adsız yol". `learnedKm` = Σm/1000 (kullanılmıyor).
- **Gün katkısı:** otomatik artışlar vardiya kimliği kovasına, elle eylemler ve ilk açılıştaki toplamlar "taban"a yazılır (9.3.5).

### 9.3 Öğrenme

#### 9.3.1 Girdi `LearnFix` (`nav_controller.dart:781-804`)

Her konum fix'inde harita eşleştirmesinden sonra, **yalnız vardiya (gün) açıkken**:

| Alan | Değer |
|---|---|
| `t`, `lat`, `lon`, `accuracy` | fix (zaman fix'in kendi zaman damgası) |
| `kmh` | `speed < 0 ? 0 : speed × 3,6` |
| `way`, `name` | eşleşme `info.dist ≤ 20 m` ise `osmId`/ad, değilse `null`/"" |
| `wrongWay` | eşleştirici: hareketli (hız ≥ 2 m/s ve yön ≥ 0) + tek yönün tersi + `contraflow` yok (`map_matcher.dart:57, 71-75`) |
| `approved` | `streets.approved` way'i içeriyor (Y8, Bölüm 4) |
| `vib` | `SensorHub.vibRms` — **1.11.0'da hep 0** (`ride_logger.dart:33, 41-44`: `start()` boş, fiziksel yüzey Y13'ün ayrı hattında) |
| `excludeStops` | sipariş aşaması `atRestaurant` / `readyForCustomer` / `atCustomer` (Bölüm 11'deki aşama eşlemesi) **ya da** bir durağa < 60 m **ya da** varılan noktaya < 80 m |
| `mainRoad` | `isMainRoad(cls)` = trunk / primary / secondary (`road_graph.dart:54-55`) |

Eşleştirici özeti (ayrıntı Bölüm 7): doğruluk > 35 m fix'te güncellenmez, önceki eşleşmeyi döner; `nearestEdge(maxDist 30)` önce yönlü (hareketliyse), olmazsa yönsüz; "yol yok" kenarları kabul edilmez; ww kesimleri `allowedEdges` (öneri/uyarı üretmez) (`map_matcher.dart:54-101`).

#### 9.3.2 `RoadLearner.feed` (`road_knowledge.dart:414-495`)

1. `accuracy > 25` → yok say (boş liste).
2. Adım: önceki fix varsa `dt` = fark (sn, ms/1000); `0 < dt ≤ 5` ise `d = haversine`, `d > 100 m` → `d = 0` (sıçrama); aksi `dt = 0`, `d = 0`. Son zaman, konum ve **önceki hız** saklanır.
3. **Sokak istatistiği:** way değişince eski way'de `passM ≥ 20 m` ise `passes++`; `passM = 0`. Way varsa: ad boş değil ve farklıysa `names[way] = ad`; `d > 0` ise `m += d`, `s += dt`, `lastAt = t`, `passM += d`; `kmh ≥ 10 && vib > 0` ise `vibSum += vib`, `vibN++`.
4. **Dur-kalk** (`_stopAndGo`, 546-593; sabitler 370-374: akıyor ≥ 12, yavaş < 8, durdu < 5 km/s; `stopMinS` 3, `stopMaxS` 150 sn):
   - `kmh ≥ 12`: yavaşlamış, durma sayılmamış, `slowWay` var ve bu fix `excludeStops` değilse `stops(slowWay) += 0,5`; `moving = true`; yavaşlama durumu sıfırlanır.
   - `!moving` ya da `kmh ≥ 8` → hiçbir şey (8–12 arası ölü bölge).
   - Yavaşlama başlar: `slowSince ??= t`; ilk kez ise `slowWay = way ?? sonWay`. `kmh < 5`, süre (tam saniye, `inSeconds`) ≥ 3 ve sayılmamışsa → `stops(way ?? slowWay) += 1` (`excludeStops` değilse), `stopWay` saklanır.
   - Süre > 150 sn (park/teslimat): `stopWay` varsa `stops = max(0, stops − 1)`; `moving = false`; sıfırla.
5. **Kesintisiz ters yön sürüşü** (`_trackRun`, 510-521): way yoksa çık. Son sürüşün son fix'inden > 10 sn (`runGapS`) geçtiyse sürüş bitmiş sayılır. `wrongWay` fix sürüşü uzatır (yoksa yeni `WwRun(t)` açar); ters yön değil ama aynı way'de `kmh < 8` (durma/yavaşlama: eşleştirici hareketsizken yön veremez) de uzatır. Uzatma: parça (`WwRunWay(ad, main)`) yoksa eklenir, boş ad sonradan dolar, `meters += d`, `last = t`, `lastWay = way`. Onaylı/onaysız her parça girer; ana cadde `main` işaretli.
6. **Ters yön geçişi** (yalnız onaylı sokak; 450-494): `wrongWay && approved && way` → oturum başka way'deyse kapanır (geçiş döner), yoksa `_WwSession(way, ad, t)` açılır; `wwOff = 0`, `meters += d`, olay denetimi. Oturum varken: aynı way'de `kmh < 8` → sürer (`meters += d`, olay); aksi `wwOff++`, 3 fix ya da başka way'e geçiş → kapanır.
   - **Olay** (`_incident`, 497-508): sert fren (`0 < dt ≤ 2,5` ve `öncekiHız − kmh ≥ 9`) ya da durma (`kmh < 5`, önceki ≥ 5); iki olay arası ≥ 5 sn.
   - **Kapanış** (`_endWw`, 525-544): oturum yok ya da `meters < 25` (`wwMinM`) → geçiş yok. Aksi `ww[way].passes++`, `lastAt` = son fix zamanı; geçişte "Sorun yaşadım" işaretlendiyse `problems += 1`; `WwPass(way, ad, metre, olay, bitiş, reported, run = (sürüş bu way'i içeriyorsa) sürüş)`.
   - `flush()`: süren oturumu kapatır (gün sonu). `liveWw`: süren oturum `(way, ad, metre)`; `markLiveReported()`: süren oturuma "sorun" işareti (yoksa `false`).

#### 9.3.3 Denetleyici (`RoadController`, `road_controller.dart:80-158, 180-202, 398-441`)

- Açılış: `roads.*` okunur (9.10), `RoadLearner` kurulur, gün katkı defteri yüklenir (bozuksa 9.3.5). Öğrenme kapsamı vardiyaya bağlı: gün açılınca `learningDay = dayId`, 5 sn'de bir tik; kapanınca `flush` + katkı yakalama + `learningDay = null` + yeni öğrenici + soru/son geçiş/canlı/"temiz sürüş" sıfır + zorla kayıt (103-111).
- `onFix(f)`: gün kapalıysa çık; `feed`; biten her geçiş `_onPass` (9.6); canlı geçiş değiştiyse yayın; katkı yakala; kayıt (141-158).
- Tik (5 sn): açık soru ≥ 20 sn (`askTimeoutS`) ise zaman aşımı (9.6); kayıt.
- Kayıt (`_save`, 407-432): kirli değil ve zorla değilse atla; zorla değilse en çok **30 sn'de bir**; son yazılan dizgeyle aynıysa yazma (`_written`). Zorla: cevap, sonradan sorun ekleme, bütün elle değişiklikler, gün sonu, uygulama arka plana (`AppLifecycleListener.onPause`), kapanış. `roads.days.v2` yazımları zincirli (`_writes`), gün silme yazımın bitmesini bekler.
- Yeniden rota tetikleri (`nav_controller.dart:235-247`): `ghostRev` (hayalet/ww kesimi değişti), `approvedRev` ("Sorun yok" onayı ya da geri alma), ayarlar `qualityTolerancePct`, `wwMaxProblemPct` (+ `turnPenaltyS`, `bikewayCostPct`) değişince rota varsa `replan('prefs')`. Elle puan (%, 👍/👎, kalite sayfası) kaydı da rota varsa yeniden rota çağırır.

#### 9.3.4 Elle eylemler (`road_controller.dart:297-396`)

| Eylem | Etki |
|---|---|
| `rate(way, r, ad?)` | ad yazılır; `r == 0` → puan silinir, aksi `ratings[way] = r`; `_changed` (katman sayacı++, zorla kayıt, yayın) |
| `resetQuality(way)` | defterde way sıfırlanır; `ways` ve `ratings`'ten silinir |
| `resetAllQuality()` | hepsi — **UI'da kullanılmıyor (ölü)** |
| `addWwPass(way, problem, ad?)` | `passes++`, sorunsa `problems += 1`, `lastAt = şimdi`; katkı **tabana** (`manual`) |
| `resetWw(way)` | defterde ww satırı sıfır; `ww`'den silinir |
| `markGhost(g, edge, all)` | aynı way'in `all` işareti varsa onu döner; orta nokta (`geometry(edge, f0 .5, f1 .5).first`), ad; aynı way'de (`all` ise hepsi, değilse orta noktaya < 15 m olan) eski işaret silinir; eklenir; olay `ghost {way, all?}`; `ghostRev++` |
| `setWwSegment(g, edge, allow)` | aynı way'de orta noktaya < 15 m kesim silinir; `allow` ise eklenir; olay `ww_segment {way, allow}`; `ghostRev++` |
| `removeGhost(m)`, `clearGhosts()` | `ghostRev++` |

#### 9.3.5 Gün katkıları (`RoadDayContributions`, `day_contributions.dart`)

- Amaç: bir günün **otomatik** artışlarını gün silmede geri almak; ilk açılıştaki toplamlar ve elle eklenenler korunur (5-6).
- Satırlar: way `[m, s, stops, vibSum, vibN, passes, lastAt]` (7 double), ww `[passes, problems, lastAt]` (3 double); son eleman (`lastAt`) toplanmaz, **en büyüğü** alınır (26-27, 46-47, 90).
- `capture(k, day)`: dokunulan her way/ww için (`touchedWays/Ww`) son görülen değere göre fark o günün kovasına eklenir, görülen güncellenir, dokunulanlar temizlenir (37-52). `manual(k)`: fark tabana (41).
- `deleteDate(k, date)`: anahtarı `yolgo-<ms>` olan ve `bakuDay(ms) == date` olan kovalar silinir; `k.ways`/`k.ww` **taban + kalan kovalardan** baştan kurulur; silinen kimlikler döner (53-60, 83-98). Başka önekli kova silinmez; `RoadKnowledge`'ın elle alanları (puan, ad, hayalet, ww kesimi) dokunulmaz.
- `resetWay(id?, wrongWay)`: tabandan ve bütün kovalardan (id yoksa hepsi) siler, görüleni unutur (61-82).
- İlk açılış (kayıt yok): mevcut toplamlar taban (17-19). Bozuk kayıt: ham dizge `roads.days.damaged.v2`'ye kopyalanır, defter mevcut toplamları taban alarak yeniden başlar — geçmiş gün üyeliği uydurulmaz (`road_controller.dart:94-100`).
- Gün silme (`day_deletion.dart:21-53`; ayrıntı Bölüm 12): seçilen tarih aktif günse önce gün kapatılır; … `deleteContributions(date)` = önce yakala, sonra `deleteDate`, zorla kayıt, yazımı bekle; dönen kimlikler gün kaydından da silinir. Onay metni: "Bu tarihin sürüş, sipariş, olay, istatistik ve otomatik yol ölçümleri silinir. Diğer günler ve elle kaydedilen yollar korunur." / "Bu tarixdəki sürüş, sifariş, hadisə, statistika və avtomatik yol ölçmələri silinir. Başqa günlər və əl ilə qeyd olunan yollar saxlanır."
- **WEEM:** kova anahtarı aynen `yolgo-<vardiya başlangıcı ms>` (`ShiftPort.state.startedAtMs`; test ve `deleteDate` ayrıştırması birebir kalsın, anlamı WEEM vardiyası); `bakuDay` → `:core` tek saat kuralı `LogTime.day(ms, Clock.zone)` (`CORE.md` "Gün sınırı", 176. satır: Azerbaycan'da fark yok). Gün silme hangi ekrandan tetiklenirse (Y17 gün silme, R12 Kayıtlar; Bölüm 12/15) `:map` kancası `RoadBook.deleteContributions(date)`'i çağırır.

### 9.4 Puan soruları ve kartlar

#### 9.4.1 "Bu yolun kalitesi?" (`quality_prompt.dart`; kart `roads_screen.dart:647-700`)

- Açılış: (a) sürerken **Ses+ iki kez** (450 ms içinde) — o anki eşleşen sokak; eşleşme yoksa toast "Yol eşleşmedi — biraz daha sür" / "Yol tanınmadı — bir az da sür" (turuncu) + titreşim (`nav_controller.dart:695-704`); (b) harita uzun basış satırı "Elle yüzey puanı · <ad>" / "Əl ilə səth balı · <ad>" (`nav_screen.dart:250-256`); (c) kalite sayfası "Kaliteyi % olarak gir" / "Keyfiyyəti faizlə yaz" (`roads_screen.dart:220-227`).
- Başlangıç değeri: `pct ?? round(qualityOf(way) × 10) × 10`, 10–100'e kıstırılır (`quality_prompt.dart:34-39`). **Not:** `qualityOf` burada grafsız çağrılır → OSM ön tahmini yerine sabit 0,8 (puan/istatistik yoksa 80 başlar; `road_knowledge.dart:171-184`). Açılınca güçlü titreşim.
- Açıkken: Ses+ / Ses− **±10** (10–100) + seçim titreşimi; çipler %20 · %40 · %60 · %80 · %100; her dokunuş 6 sn sayacını yeniden kurar; **6 sn dokunulmazsa kaydedilir** (`autoSaveS = 6`); "Kaydet" / "Yadda saxla" hemen kaydeder; "Vazgeç" / "Ləğv et" kaydetmeden kapatır (25-76).
- Kayıt: `rate(way, pct, ad)` + orta titreşim + rota varsa `replan('prefs')`.
- Kart: başlık "Bu yolun kalitesi?" / "Bu yolun keyfiyyəti?" (renk `qualityColor(pct/100)`), alt satır sokak adı, sağda büyük `%`, ipucu "Ses+ artır · Ses− azalt · 6 sn sonra kendiliğinden kaydedilir" / "Səs+ artır · Səs− azalt · 6 san sonra özü yadda saxlayır". Sıklık sınırı yok (yalnız kullanıcı açar).

#### 9.4.2 👍 / 👎

- Uzun basış menüsündeki kalite satırının sağında 👍 (ipucu "Kaliteli yol" / "Keyfiyyətli yol") → `rate(90)`, 👎 ("Kalitesiz yol" / "Keyfiyyətsiz yol") → `rate(25)`; ad "isimsiz yol" ise ad yazılmaz. Toast `r ≥ 50`: "Kaliteli yol: <ad>" / "Keyfiyyətli yol: <ad>" (yeşil), aksi "Kalitesiz yol: <ad> — mümkünse kaçınılır" / "Keyfiyyətsiz yol: <ad> — mümkün qədər yan keçiləcək" (turuncu); rota varsa yeniden rota (`nav_screen.dart:357-366`).
- Satır alt yazısı: `st == null || st.m < 50` → "Fiziksel ölçüm ayrıntıları Yollarım → Kalite bölümünde" / "Fiziki ölçmə detalları Yollarım → Keyfiyyət bölməsində", aksi "<x,x> dur-kalk/km · <uzunluk> sürüldü" / "<x,x> dur-qalx/km · <uzunluk> sürülüb"; simge rengi `qualityColor(qualityOf(way, g))` (`nav_screen.dart:250-270`; menü eşiği: basılan yere `nearestEdge(maxDist 40)`, 158-167).

#### 9.4.3 "Ters yönde sorun yaşadın mı?" kartı (`WrongWayCard`, `roads_screen.dart:559-645`)

- Soru açıkken: başlık "Ters yönde sorun yaşadın mı?" / "Əks istiqamətdə problem oldu?" (turuncu, büyük); alt satır "<ad>[ · sert fren / durma algılandı] · Ses+ evet / Ses− hayır · ~<kalan> sn" / "<ad>[ · qəfil əyləc / dayanma oldu] · Səs+ hə / Səs− yox · ~<kalan> san" (kalan = 20 − geçen sn, 0–99; köşeli kısım olay > 0 ise). Düğmeler: "Evet, sorun" / "Hə, oldu" (kırmızı, 52 dp) → `answer(true)`; "Hayır" / "Xeyr" → `answer(false)`.
- Soru yok, onaylı ters yön geçişi sürerken ince şerit: "Ters yön (onaylı): <ad>[ · sorun <%oran>]" / "Əks istiqamət (təsdiqli): <ad>[ · problem <%oran>]" (oran yalnız geçiş > 0 ise) + düğme "Sorun yaşadım" / "Problem oldu" → `reportProblem()`; kaydedilirse toast "Ters yön sorunu kaydedildi" / "Əks istiqamət problemi qeyd olundu" (turuncu).
- Zaman aşımı 20 sn (`askTimeoutS`); denetim 5 sn'lik tikte (pratikte 20–25 sn). Mantık 9.6.

#### 9.4.4 Kartların yeri ve sürüşte güvenlik (`nav_screen.dart:61-146`)

- Normal görünüm: alt panelde `QualityPromptCard` + `WrongWayCard` (rota düğmesinin üstünde; gün kartı katlıyken de).
- Varış odağı (hedefe ≤ 100 m, Bölüm 7): yalnız `WrongWayCard` (+ adres kartı); kalite kartı gizli.
- "Yalnız harita" kipi: ikisi de gizli; ses tuşları yine işler, zaman aşımı yine sayar.
- Cevap ekrana bakmadan ses tuşuyla ya da dokunarak. Titreşim: soru/kalite açılınca güçlü, cevap ve ±10'da seçim, kayıtta orta, hata durumunda kısa titreşim. WEEM: aynı dört düzey (`HapticFeedbackConstants` karşılıkları, `:map` ince katman).

### 9.5 Ses+ / Ses− tuşu yakalama

#### 9.5.1 Yerel katman (`MainActivity.kt:54-65, 110-113, 478-503`)

- `captureVolume` (varsayılan `true`); Dart açılışta ve ayar değişince `setVolumeCapture(volumeKeys)` gönderir (`clipboard_sniffer.dart:98-100`, `fireImmediately`; köprü uygulama açılışında kurulur, `main.dart:120`).
- `dispatchKeyEvent`: yakalama açık + tuş `KEYCODE_VOLUME_UP`/`DOWN` + kanal hazır → olay **tüketilir** (`return true`, sistem sesi değişmez; tekrarlar ve bırakışlar dahil); aksi normal akış.
  - **Ses+:** `ACTION_DOWN` ve `repeatCount == 0` → hemen `onVolume("up")` (basışta).
  - **Ses−:** `ACTION_DOWN` (tekrar değil) → `downHeld = true`, 1000 ms sonra `downHold` (hâlâ basılı ve yakalama açıksa `onVolumeDownHold` + `downLongFired`); `ACTION_UP` → gecikme iptal; basılıydı, olay iptal değil ve uzun basış atılmadıysa: `eventTime − downTime ≥ 1000` → `onVolumeDownHold`, aksi `onVolume("down")` (bırakışta).
- Kapsam: yalnız Activity odaktayken (ekran açık, uygulama önde); ekran kapalı/kilitli ya da arka planda tuşlar sisteme gider (MediaSession ya da erişilebilirlik kullanılmaz). Sürüşte ekran açık tutulur (`setScreenAwake`, Bölüm 6). Gün/rota durumuna bakılmaz: ayar açıksa YolGo önde olduğu her an ses tuşları ses ayarlamaz.

#### 9.5.2 Dağıtım (`NavController.onVolumeKey` / `onVolumeDownHold`, `nav_controller.dart:578-751`)

Öncelik sırası: (1) ters yön sorusu açık → `answer(problem: up)` (Ses+ = "evet, sorun", Ses− = "hayır") · (2) kalite % kartı açık → ±10 · (3) Ses+: 450 ms içinde ikinci Ses+ → kalite % sorusu, tek → 450 ms sonra **onay** · (4) Ses−: 450 ms içinde ikinci Ses− → **aşırı eğimli**, tek → 450 ms sonra **kötü nokta ±25 m** · (5) Ses− uzun (≥ 1 sn) → **sil** (bekleyen tek Ses− zamanlayıcısı iptal).

| Tuş | Koşul | Etki | Toast (TR / AZ) |
|---|---|---|---|
| Ses+ tek | 15 sn içinde ters yön önerisi var, öneri ana cadde değil | önerilen sokak onaylı (Y8 `approve`), öneri temizlenir | "Onaylandı: <ad> (iki yön)" / "Təsdiqləndi: <ad> (iki tərəfli)" (yeşil, 6 sn, "Geri al" / "Geri qaytar") |
| Ses+ tek | eşleşme yok | — | "Yol eşleşmedi — biraz daha sür" / "Yol tanınmadı — bir az da sür" + titreşim |
| Ses+ tek | eşleşen sokak ana cadde | — | "Ana caddede ters yön onayı kapalı" / "Əsas küçədə əks istiqamət təsdiqi olmur" (kırmızı) + titreşim |
| Ses+ tek | aksi | eşleşen sokak onaylı | "Onaylandı: <ad> (iki yön)" (yukarıdaki gibi) |
| Ses− ×2 | eşleşme var | `steep` (Y8) | "Aşırı eğimli: <ad> — yokuş yukarı kullanılmaz" / "Çox dik: <ad> — yoxuşa çıxılmayacaq" (+ "Geri al") |
| Ses− tek | eşleşme ve konum var | kötü nokta `SurfaceMark(id "spot-<ms>", way, lat, lon, at, label rough, source "user")` kaydedilir, durak varsa yeniden rota; olay `bad_spot {way}`; güçlü titreşim | "Kötü nokta: <ad> · ±25 m (uzun bas: sil)" / "Pis nöqtə: <ad> · ±25 m (uzun bas: sil)" (turuncu) |
| Ses− tek | konum yok | — | "Konum yok — biraz daha sür" / "Mövqe yoxdur — bir az da sür" + titreşim |
| Ses− uzun | eşleşme yok | — | "Yol eşleşmedi; kaldırmak için yolun üzerinde dene" / "Yol tanınmadı; silmək üçün yolun üstündə yoxla" |
| Ses− uzun | aynı way'de konuma ≤ 40 m etkin kötü nokta | hepsi `removed`; durak varsa yeniden rota; olay `bad_spot_clear {way, n}`; orta titreşim | "Kötü nokta silindi: <ad>" / "Pis nöqtə silindi: <ad>" (yeşil) |
| Ses− uzun | nokta yok; sokak kara listede ya da puanı ≤ 25 | kara listeden çıkar, puan ≤ 25 ise silinir; olay `bad_road_clear {way}`; durak varsa yeniden rota | "Kötü yol işareti kaldırıldı: <ad>" / "Pis yol qeydi silindi: <ad>" (yeşil) |
| Ses− uzun | hiçbiri | — | "Burada kötü nokta işareti yok" / "Burada pis nöqtə qeydi yoxdur" |

- Onay/eğim (`_setStreet`, 706-750): `streets.*` güncellenir (Bölüm 4), olay `approve|steep {way}`, güçlü titreşim, rota varsa yeniden rota; "Geri al": sokak temizlenir, önceki onay / kara liste / eğim / saat izni geri yazılır, olay `undo {way}`. `_setStreet(block)` dalı ("Kötü Yol: <ad>" / "Pis yol: <ad>") ses tuşundan **erişilmez** (1.3.1'den beri Ses− kötü nokta; sokağı tamamen kapatmak harita menüsünden, Bölüm 4) → WEEM'e taşınmaz.
- Ters yön önerisi (`_onPosition`, 806-830): onaysız tek yönlü sokakta ters yönde ≥ 40 m (eşleştirici; sokak başına eşleştirici ömründe bir kez, `map_matcher.dart:88-96`) → ana caddede toast "Ana caddede ters yöndesin!" / "Əsas küçədə əks istiqamətdəsən!" (kırmızı) + titreşim; değilse öneri + toast "Ters yön: <ad> — onay için Ses+" / "Əks istiqamət: <ad> — təsdiq üçün Səs+" (turuncu, 10 sn); öneri 15 sn sonra düşer; olay `wrongway {way, main}`. ww kesimi izinli kenarda öneri/uyarı yok.
- Ayar (`settings_screen.dart:300-306`): "Ses tuşları (Ses+ onay · Ses− kötü nokta ±25 m · Ses− uzun: sil · Ses− ×2 aşırı eğimli)" / "Səs düymələri (Səs+ təsdiq · Səs+ ×2 keyfiyyət · Səs− pis nöqtə ±25 m · Səs− uzun: sil · Səs− ×2 çox dik)", alt yazı "Kapalıyken tuşlar normal ses ayarı yapar" / "Söndürüləndə düymələr adi səsi tənzimləyir" (TR'de "Ses+ ×2 kalite" eksik, 9.13).

#### 9.5.3 WEEM

- Tek Activity: `:app` `MainActivity.dispatchKeyEvent` → `:map` `VolumeKeys` (ince: tüketme kararı, 1000 ms uzun basış `Handler`'ı) → saf `roads.VolumeDispatch` (450 ms çift basış, öncelik sırası, eylem seçimi; saat enjekte, `Clock.monoMs`). Tüketme kuralı YolGo'daki gibi: yakalama açıkken ses tuşu olayları (basış, bırakış, tekrar) tüketilir.
- Yakalama koşulu: `MapSettings.volumeKeys` (varsayılan açık) **ve** Harita sekmesi ya da onun alt ekranı (Yollarım, Harita ayarları) önde; diğer sekmelerde tuşlar normal (SORU-Q4). Ekran kapalı/kilitli ya da PiP'te yakalama yok; MediaSession ve erişilebilirlik tuş süzgeci **kullanılmaz** (`WatchService` ek yetki almaz).
- 2 telefon: yakalama ve bütün Y12 eylemleri Receiver'da (9.12).

### 9.6 Ters yön (ww) soruları ve kararlar (`road_controller.dart:160-295`; `road_knowledge.dart:186-196, 288-331`)

- **Geçiş bitince** (`_onPass`): olay `ww_pass {way, m (yuvarlak), inc, reported?}`; son geçiş saklanır (`recent`; eklenen sorun `recentAdded` = geçişte bildirildiyse 1, aksi 0). Geçişte bildirildiyse soru yok. Olaysız (`incidents == 0`) ve "Sorun yok" denmiş sürüşün devamıysa (`identical(run, cleanRun)`) soru yok. `incidents > 0` **ya da** `wwAskAlways` → açık soru varsa önce zaman aşımı kuralıyla kapanır; yeni `WwQuestion(pass, şimdi)` + güçlü titreşim.
- **Zaman aşımı** (`_expireQuestion`, 190-202): soru kapanır; olaylıysa `problems += 0,5` (son geçişse `recentAdded = 0,5`); olaysızsa hiçbir şey.
- **Cevap** `answer(problem)` (204-229): soru yoksa `false` (ses tuşu sonraki önceliğe geçer). "Evet" → `problems += 1` (`recentAdded = 1`). "Hayır" → `recent` temizlenir (sonradan sorun eklenemez) ve **YG-Y9 temiz sürüş onayı** `_approveClean`. Olay `ww_answer {way, problem, approved?: [id…]}`; seçim titreşimi; zorla kayıt.
- **`_approveClean(p)`** (231-262): `cleanRun = p.run`; `wwCleanApprovals(p)`'deki her sokak (zaten onaylı ya da kara listede olan hariç) `streets.approved`'a eklenir (Y8 `approve(id, ad)`); eklenen varsa `approvedRev++` (yeniden rota) ve toast "Onaylandı: <ad1, ad2> (iki yön)" / "Təsdiqləndi: <ad1, ad2> (iki tərəfli)" (adlar tekilleştirilip ", " ile, boş ad "isimsiz yol" / "adsız yol"), yeşil, 6 sn, "Geri al" → eklenenler onaydan çıkar, olay `undo {way}`, `approvedRev++`.
- **`wwCleanApprovals(p)`** (320-331): `{p.way: p.name}` + aynı sürüşteki her parça: ana cadde değil, eklenmemiş ve (ters yönde ≥ 25 m **ya da** geçişle aynı boş olmayan ad).
- **"Sorun yaşadım"** `reportProblem()` (264-287): açık soru → `answer(true)`; süren onaylı geçiş → `markLiveReported` (olay `ww_report {way}`); son geçiş ≤ 90 sn önce bittiyse ve `recentAdded < 1` → `problems += 1 − recentAdded`, `recentAdded = 1` (olay `ww_report`). `canReportRecent` aynı koşul.
- **Rota girdileri** (`road_knowledge.dart:186-196`): `wrongWayRatios()` = geçişi olan sokaklar için **yumuşatılmış** `(problems + 0,1) / (passes + 1)`; `wrongWayAvoid(eşik, minPasses 3)` = geçiş ≥ 3 ve **ham** `problems/passes` ≥ eşik; eşik = `wwMaxProblemPct / 100` (varsayılan %50). Etkisi 9.9.
- **Elle:** ww satırı menüsü (9.8.4) "Sorunlu geçiş ekle", "Sorunsuz geçiş ekle" (`addWwPass`), "Sayaçları sıfırla" (`resetWw`).
- **Y8 sınırı:** onay, kara liste, eğim ve saat izni verisi `streets.*` (Bölüm 4, ajan R); Y12 yalnız `approve` / `remove(approved)` çağırır ve `approved` kümesini okur. Ters yön yalnız onaylı (ana cadde olmayan) sokakta ya da ww kesiminde serbesttir (Bölüm 3).
- **Ayarlar:** `wwMaxProblemPct` (varsayılan 50; Ters yön sekmesi "Sorun eşiği" / "Problem həddi" adımlayıcı 20–90, adım 5), `wwAskAlways` (varsayılan `false`; "Her geçişten sonra sor" / "Hər keçiddən sonra soruş", alt yazı "Kapalı: sadece sert fren / durma algılanınca sorar" / "Söndürülüb: yalnız qəfil əyləc / dayanma olanda soruşur").

### 9.7 Cadde oranı ve kötü noktalar

- **Kötü nokta** (`bad_spots.dart`): `isBadSpot` = silinmemiş, `source == "user"`, `access != closed`, etiket ∈ {rough, pothole, raisedAsphalt, dirt, gravel, obstacle}; etki ±25 m (`badSpotRadiusM`); aynı way'de 30 m içindeki etkin işaretler ≥ 2 **farklı Bakü gününden** ise doğrulanmış (`badSpotConfirmM`); kenar başına kesir aralığı `SpotSpan(edge, f0, f1, confirmed)` (`nearestEdge(maxDist 25, aynı way)`; `r = 25/kenarUzunluğu`). Rota etkisi (Bölüm 3): kesim maliyeti +3× (doğrulanmış +6×), "kötü yollardan kaçın" denemesinde doğrulanmış kesim yasak.
- **Cadde kötü kesim oranı** (`street_ratio.dart`): kötü uzunluk = Y13 ölçülmüş kesimler (puan < 50, güven ≥ 0,35) ∪ kötü noktaların aralıkları, kenar başına aralık birleşimi; toplam = way'in tekil kenarlarının uzunluğu. Metin: kötü < 1 m → "Kötü kesim yok · <uzunluk>" / "Pis hissə yoxdur · <uzunluk>", aksi "%<p> kötü · <m> m / <uzunluk>" / "%<p> pis · <m> m / <uzunluk>" (uzunluk < 1000 m "N m", aksi Dart `toStringAsFixed(1)` "2.0 km" — noktalı). Uzun basış menüsünde, alt yazı "Ölçülmüş kötü kesimler + kötü noktalar (±25 m)" / "Ölçülmüş pis hissələr + pis nöqtələr (±25 m)" (`nav_screen.dart:271-277`). Test: `router_test` "cadde oranı" (Bölüm 3.14 → `roads.StreetRatioTest`, 9.11). WEEM 1.0: yalnız kötü noktalarla (Y13 özeti yok), metin aynen.
- **E11 sokak puanı** (`street_scores.dart`, "Yol puanları"): Y13 parça özetleri + Y14 kalibrasyon referanslarından 1–10 → **Y12 değil, sonra** (9.11, SORU-Q2). Y12'den tek kullandığı `RoadKnowledge.names`.

### 9.8 Ekranlar

#### 9.8.1 Yollarım (`RoadsScreen`, `roads_screen.dart:23-52`)

Başlık "Yollarım" (TR/AZ aynı); sekmeler "Kalite" / "Keyfiyyət", "Ters yön" / "Əks istiqamət", "Yol yok" / "Yol yoxdur", "Bisiklet" / "Velosiped" (Y15, Bölüm 5). Girişler: Ayarlar → Yollarım akordeonu "Yolları görüntüle" / "Yollara bax" (alt "Kalite · ters yön · yol yok · bisiklet" / "Keyfiyyət · əks istiqamət · yol yoxdur · velosiped"), Sokak bilgileri altında "Yollarım: kalite · ters yön sorunu · yol yok" / "Yol keyfiyyəti və küçə qaydaları" (`settings_screen.dart:243-271`), rota ayrıntısı "Yol ayrıntıları" / "Yol detalları" (`widgets.dart:225`).

#### 9.8.2 Kalite sekmesi

YolGo'da `SurfaceQualityTab(manualRows)` (`surface_screen.dart:46-97`) — Y13/Y14 ağırlıklı:

| Parça | Kaynak | WEEM 1.0 |
|---|---|---|
| "Yol puanları" kartı (E11 açıklaması, ölçüm durumu, "Ölçüm ve kalibrasyon" düğmesi) | Y13/Y14 | yok (sonra) |
| "Elle girilen puanlar" / "Əl ilə yazılan ballar" (açılır): satır = sol `%` kutusu (elle puan, `fmtPct`, renk `qualityColor`), ad, alt yazı `st == null` → "Elle girildi" / "Özün yazmısan", aksi "<x,x> dur-kalk/km · <sarsıntı> · <uzunluk> · <n> geçiş" / "<x,x> dur-qalx/km · <sarsıntı> · <uzunluk> · <n> keçid"; sağda elle puan simgesi; dokununca kalite sayfası | Y12 | var |
| "Haritada yüzey renkleri" / "Xəritədə səth rəngləri" (`showQualityLayer`) | yalnız Y13 özetlerini çizer (`map_view.dart:82-99`) | gizli; ayar saklanır |
| "Kalite toleransı" / "Keyfiyyət tolerantlığı" `%` + kaydırıcı 0–30 (30 adım) → `qualityTolerancePct` | Y12/Y3 | var |
| sokak puanı listesi / "Henüz fiziksel yol ölçümü yok" | Y13/Y14 | yok |
| "Elle işaretlenen yüzeyler" / "Əl ilə qeyd olunan səthlər": "<ad> · <etiket>", alt "<access> · Kaldırıldı / Kullanıcı kaydı", sil / geri al (rota varsa yeniden rota), dokununca yüzey düzenleyici | Y13 deposu | yalnız Ses− kötü noktaları, sil/geri al; düzenleyici yok (SORU-Q1) |
| "Eski trafik kayıtları" / "Köhnə trafik qeydləri", alt "Korundu · yüzey metriğine dönüştürülmedi" / "Saxlanıldı · səth ölçməsinə çevrilmədi": `m ≥ 50` way'ler "<uzunluk> · <x,x> dur-kalk/km" | Y12 `WayStats` | var (başlık SORU-Q3) |

Sarsıntı metni (`_bump`, 56-61): `vib == null` → "sarsıntı ?" / "silkələnmə ?", < 2,5 "sarsıntı az" / "silkələnmə az", < 4 "sarsıntı orta" / "silkələnmə orta", aksi "sarsıntı çok" / "silkələnmə çox" (WEEM 1.0'da hep "?", 9.13).

#### 9.8.3 Kalite sayfası (`showQualitySheet`, 172-252)

Başlık ad; alt yazı `st == null` → "Kalite <%q> (tahmin)" / "Keyfiyyət <%q> (təxmini)", aksi "Kalite <%q> · <x,x> dur-kalk/km · <sarsıntı> · <uzunluk> sürüldü" / "… sürülüb" (`q = qualityOf(way, g)`, OSM ön tahminli). Seçenekler: 👍 "Kaliteli yüzey · kullanıcı puanı" / "Hamar səth · istifadəçi balı" (90), 👎 "Bozuk yüzey · kullanıcı puanı" / "Nahamar səth · istifadəçi balı" (25), "Kaliteyi % olarak gir" (9.4.1), puan varsa "Elle puanı kaldır" / "Yazdığım faizi sil", istatistik varsa "Öğrenileni sıfırla" / "Öyrəniləni sıfırla". Her seçimden sonra rota varsa yeniden rota.

#### 9.8.4 Ters yön sekmesi (`_WrongWayTab` 254-318, `_WwRow` 320-418)

- Bilgi kartı: "Ters yönde sorun oranı" / "Əks istiqamətdə problem faizi" + açıklama (273-283, metin aynen), "Sorun eşiği" adımlayıcı, "Her geçişten sonra sor" anahtarı (9.6).
- Liste: onaylı ∪ ww kaydı olan sokaklar; ham orana göre azalan (kaydı yok = −1), eşitlikte geçişe göre azalan. Boşsa "Onaylı sokak yok. Tek yönlü bir sokakta ters giderken Ses+ ile onaylarsın; ana caddelerde onay kapalı." / "Təsdiqli küçə yoxdur. Birtərəfli küçədə əks istiqamətdə gedərkən Səs+ ilə təsdiqləyirsən; əsas küçələrdə təsdiq olmur."
- Satır: ad (Y8 `streets.names` önce, sonra `nameOf`); alt "<n> geçiş · <sorun> sorun · <durum>" / "<n> keçid · <sorun> problem · <durum>" (sorun tam sayıysa tam, değilse 1 ondalık); durum: onaylı değil "onay kaldırılmış" / "təsdiq götürülüb", kaçınılıyor (geçiş ≥ 3 ve oran ≥ eşik) "kaçınılıyor" / "yan keçilir" (alt yazı kırmızı), geçiş 0 "veri yok" / "məlumat yoxdur", aksi "kullanılıyor" / "istifadə olunur"; sağda oran `%` (geçiş 0 → "—"), renk: geçiş 0 soluk, ≥ eşik kırmızı, ≥ eşik/2 turuncu, aksi yeşil; simge onaylıysa yeşil.
- Satır menüsü: "Sorunlu geçiş ekle" / "Problemli keçid əlavə et", "Sorunsuz geçiş ekle" / "Problemsiz keçid əlavə et", "Sayaçları sıfırla" / "Sayğacları sıfırla"; onaylıysa "İzin saatlerini düzenle" / "İcazə saatlarını dəyiş" (alt: saat etiketi ya da "Günün tamamı" / "Bütün gün"; Y8 saat ekranı, Bölüm 4) ve "Onayı kaldır (ters yön kapalı)" / "Təsdiqi götür (əks istiqamət bağlı)".

#### 9.8.5 Yol yok sekmesi ve hayalet ekleme (`_GhostTab` 420-469; 486-555)

- Kart "Haritada var, gerçekte yok" / "Xəritədə var, əslində yoxdur" + açıklama (432-437 aynen). Liste yeniden eskiye: ad, "Sokağın tamamı" / "Bütün küçə" ya da "Bir parça" / "Bir hissə" · `gg.aa.yyyy` (telefon yerel saati), sil (ipucu "Sil (yol varmış)"); boş "Liste boş" / "Siyahı boşdur"; "Tümünü sil" / "Hamısını sil" → onay "Tüm "yol yok" işaretleri silinsin mi?" / "Bütün "yol yoxdur" qeydləri silinsin?" ("Vazgeç" / "Ləğv et", "Sil").
- Ekleme: harita uzun basış "Burada yol yok" / "Burada yol yoxdur" (alt "<ad> · bu parça rotaya hiç girmez" / "<ad> · bu hissə marşruta heç vaxt düşməyəcək") ve "Bu sokağın tamamı yok" / "Bu küçə ümumiyyətlə yoxdur" (`nav_screen.dart:311-329`); rota sırasında "Yol yok" düğmesi (`widgets.dart:381-387`) → seçici "Hangi yol yok?" / "Hansı yol yoxdur?" (alt "Seçtiğin parça bir daha rotaya girmez; rota hemen yeniden hesaplanır." / "Seçdiyin hissə bir daha marşruta düşməyəcək; marşrut dərhal yenidən hesablanır."): rotanın ilk bacağında konumdan sonraki (bitişi > konum + 3 m) en çok 6 parça, satır "şu an üzerindesin · <uzunluk>" / "hazırda bunun üstündəsən · <uzunluk>" ya da "<mesafe> sonra · <uzunluk> uzunluk" / "<mesafe> sonra · uzunluğu <uzunluk>", dipte "Listede yoksa: haritada o yola uzun bas → "Burada yol yok"." / "Siyahıda yoxdursa: xəritədə o yolun üstündə barmağını saxla → "Burada yol yoxdur"." (506-555).
- Ekleyince toast "Yol yok: <ad>[ (tamamı)] — rotaya girmez" / "Yol yoxdur: <ad>[ (bütün küçə)] — marşruta düşməyəcək" (turuncu, 7 sn, "Geri al" → işaret silinir) (486-504).

#### 9.8.6 ww kesimi (`nav_screen.dart:226-247`)

Harita uzun basış, yalnız ana cadde **ve** tek yön bayraklı kenarda: "Bu kesimde ters yöne izin ver" / "Bu hissədə əks istiqamətə icazə ver" ya da (izinliyse) "Bu kesimde ters yön iznini kaldır" / "Bu hissədə əks istiqamət icazəsini sil", alt "<ad> · yalnız bu kavşak arası, ana cadde" / "<ad> · yalnız bu kəsişmələr arası, əsas küçə" → toast "Bu kesimde ters yön serbest: <ad>" / "Bu hissədə əks istiqamət açıqdır: <ad>" ya da "Ters yön izni kaldırıldı: <ad>" / "Əks istiqamət icazəsi silindi: <ad>" (yeşil).

#### 9.8.7 Harita katmanı (`map_view.dart:37-50, 82-140`; çizim Bölüm 6)

Hayalet kenar gri kesikli (5 px, desen 4/6), ww kesimi yeşil kesikli (4 px, 10/5); onaylı yeşil / kara liste kırmızı / eğimli (Y8). Kalite renk katmanı Y13'tür (9.8.2). Hayalet ve ww kesimi kümeleri graf ya da `ghostRev` değişince yeniden hesaplanır (önbellek).

**WEEM:** ekranlar `:app` (`RoadsScreen.kt` 4 sekme; Bisiklet sekmesi Y15 içeriği), kartlar Harita sekmesinin alt paneline (`WrongWayCard`, `QualityPromptCard`), uzun basış satırları Bölüm 6'nın menüsüne, Ayarlar → Harita ayarları "Yollarım" grubu (ses tuşu anahtarı dahil). Metinler TR/AZ aynen (farklar 9.13).

### 9.9 Rota maliyetine bağ

Planlayıcı girdileri her planda yeniden üretilir (`nav_controller.dart:480-506` → `PlanRequest`). Maliyet formülü Bölüm 3.3–3.5'te; burada yalnız Y12'nin ürettiği girdiler:

| Girdi | Y12 üretimi | Boş veride | Etkisi (Bölüm 3) |
|---|---|---|---|
| `learned` | `learnedForRouting()`: **yalnız elle puanlar** `(q 0, c 0, r)` | `{}` → kalite = `priorQuality` | way kalitesi `blendQuality` = `r/100` → kalite çarpanı (3.4 madde 9); `badWay` = `r ≤ 25` → ×4, `avoidBad` denemesinde (yürüyüş dahil) yasak (3.4, 3.5) |
| `params.qualityWeight` | `qualityTolerancePct / 100` (`settings.dart:150`) | 0,10 | kalite çarpanı (3.2, 3.4 madde 9) |
| `wwRatio` | `wrongWayRatios()`: yumuşatılmış, yalnız geçişi olan sokak | `{}` → 0 | ters yön çarpanı ve `twoWay` (3.4 madde 4, 7, 9); `wrongWayRisk` (3.9) |
| `wwAvoid` | `wrongWayAvoid(wwMaxProblemPct / 100)`: geçiş ≥ 3 ve ham oran ≥ eşik | `{}` | o sokakta ters yön yasak (3.4 madde 4) |
| `ghost` | `ghostEdges(g)` (önbellek: graf ya da `ghostRev` değişince, `nav_controller.dart:761-779`) | `{}` | kenar yasak, yürüyüşte de (3.4 madde 1, 3.5); eşleştirici de oturtmaz (9.3.1) |
| `wrongWayEdges` | `wwSegEdges(g)` (aynı önbellek) | `{}` | ana caddede ters yön izni; `twoWay` sayılmaz (3.4 madde 4) |
| `marks` | Ses− kötü noktaları (9.7, SORU-Q1) | `[]` | ±25 m kesim cezası, doğrulanmışı `avoidBad` denemesinde yasak (3.5) |
| `surface`, `surfaceQuality` | Y13 / Y14 | `[]`, `{}` | 1.0'da yok |

- **Sıfırdan öğrenmede bile** (S3) kalite terimi nötr değildir: her kenarın kalitesi OSM etiketlerinden `priorQuality` (`quality.dart:23-39`, değerler 3.3) ve 0,10 ağırlıkla maliyete girer (ör. trunk ×1,04, residential ×1,018). YolGo'da da otomatik öğrenme rotaya girmediği için ilk gün WEEM rotaları bu bakımdan YolGo'yla aynıdır; fark yalnız YolGo'da birikmiş elle puan, ww sayacı, hayalet, ww kesimi, kötü nokta (ve Y8 onay/kara liste) verisinden doğar — A6.10'da açıklanacak fark budur.
- **Ekran fonksiyonları** (rotaya girmez): `learnedScore` (44-50) = `m ≤ 0` → 1; `q = 1 − 0,11 × durma/km`; `vib > 2,5` → `q −= clamp((vib − 2,5) × 0,1, 0, 0,3)`; sonuç `clamp(0,05, 1)` (km başına 2 durma ≈ %78, 4 ≈ %56, 6 ≈ %34). `learnedConfidence` = `clamp(m/800, 0, 1)`. `blendQuality(prior, l)`: `l == null` → prior; `r > 0` → `clamp(r/100, 0, 1)`; aksi `clamp(prior + (q − prior) × clamp(c, 0, 1), 0, 1)`. `qualityOf(way, g?)`: prior = graf varsa `priorQuality` (way dizini `wayIndexOf`), yoksa 0,8; istatistik varsa `(score, confidence, r)`, yoksa yalnız `r` (`road_knowledge.dart:171-184`).
- **Oranlar** (65-71): `wrongWayRatio(p, x)` = `p ≤ 0 ? 0 : clamp(x/p, 0, 1)` (ekran ve `wwAvoid`); `wrongWaySmoothed(p, x)` = `clamp((x + 0,1)/(p + 1), 0, 1)` (rota). `streetScoreQuality(s)` = `clamp((s − 1)/9, 0, 1)` (E11, sonra). `QualityBand`: good 0,75, fair 0,5 (renk sınıfı, `qualityColor`).
- **Not (madde 10b):** "iki yönlü" kuralı `wwRatio == 0` ister; yumuşatma yüzünden **bir geçiş kaydı olan her sokakta oran > 0** olur (1 temiz geçiş → 0,05). "Hayır" cevabı geçişi zaten saydığından sorulan sokak iki yönlü sayılmaz; kural yalnız hiç geçişi kaydedilmemiş onaylı sokakta (ör. Ses+ ile onaylanıp henüz ters sürülmemiş, ya da "Hayır" ile yeni onaylanan komşu parça ilk geçişine kadar) işler. `ww_green_two_way_test` yalnız `CostModel`'i sınar (Bölüm 4.6); Bölüm 3.4 madde 4'teki "sorun yok yeşil sokak iki yönlü" ifadesi niyettir, uygulamadaki etkisi bu kadardır. SORU-Q6.

### 9.10 Veri

**YolGo** (`FlutterSharedPreferences`, `flutter.` önekli; S3: **aktarılmaz**):

| Anahtar | Biçim |
|---|---|
| `roads.ways.v1` | `{"<wayId>": [m, s, stops, vibSum, vibN, passes, lastAt]}` — `m`, `s` tam sayıya yuvarlak, `stops` ve `vibSum` 1 ondalık, diğerleri tam; < 7 eleman → boş istatistik |
| `roads.ww.v1` | `{"<wayId>": [passes, problems (1 ondalık), lastAt]}`; < 3 eleman → boş |
| `roads.ghost.v1` | `[{"w": wayId, "la": lat (6 ondalık), "lo": lon (6), "n": ad, "t": ms, "all": true?}]` — `all` yalnız `true` ise yazılır; `w`/`la`/`lo` sayı değilse öğe atlanır; `t` yoksa 0, `n` yoksa "" |
| `roads.wwseg.v1` | aynı `GhostMark` dizisi (`all` yok) |
| `roads.rate.v1` | `{"<wayId>": yüzde}`; okurken `migrate` (1→90, −1→25), sayı değilse 0 |
| `roads.names.v1` | `{"<wayId>": "ad"}` (dizge değilse "") |
| `roads.days.v2` | `{"v": 2, "baseWays": {"<id>": [7 sayı]}, "baseWw": {"<id>": [3 sayı]}, "days": {"<yolgo-ms>": {"ways": {…}, "ww": {…}}}}` |
| `roads.days.damaged.v2` | okunamayan `roads.days.v2`'nin ham kopyası |

Kod çözme (`road_knowledge.dart:222-250`): anahtar `int.tryParse` (değilse atlanır); JSON hatası → **sessizce boş** (sonraki kayıt üstüne yazar, 9.13).

**Elle kötü noktalar** (YolGo'da Y13 deposu `files/surface-v2/manual-marks.json`): `[{"id": "spot-<ms>", "way": id, "lat", "lon", "at": ms, "label": "rough", "access": "unknown", "source": "user", "removed": false}]` (`surface_models.dart:19-37`); silme = `removed: true` (geri alınabilir); yazım `.tmp` + rename; okunamazsa depo `StateError` (`surface_store.dart:284-287, 369-379`). Gün silme elle işaretleri silmez.

**Olaylar** (sürüş kaydı, Bölüm 12): `ww_pass {way, m, inc, reported?}`, `ww_answer {way, problem, approved?}`, `ww_report {way}`, `undo {way}`, `ghost {way, all?}`, `ww_segment {way, allow}`, `approve` / `steep {way}`, `bad_spot {way}`, `bad_spot_clear {way, n}`, `bad_road_clear {way}`, `wrongway {way, main}`. Koordinat yazılmaz.

**Ayarlar** (`settings.v1` → WEEM `settings.MapSettings`, alan tablosu Bölüm 14):

| Alan | Varsayılan | Sınır / UI |
|---|---|---|
| `volumeKeys` | `true` | anahtar (9.5) |
| `qualityTolerancePct` | 10 | kaydırıcı 0–30 (okuma kıstırmaz, UI kıstırır) → `qualityWeight` = /100 |
| `showQualityLayer` | `false` | yalnız Y13 katmanı (1.0'da etkisiz) |
| `wwMaxProblemPct` | 50 | adımlayıcı 20–90, adım 5 |
| `wwAskAlways` | `false` | anahtar |

**WEEM yeri (öneri):** `files/map/roads/<anahtar>.json` — her YolGo anahtarı ayrı dosya, içerik **birebir aynı JSON** (`roads.ways.v1.json`, `roads.ww.v1.json`, `roads.ghost.v1.json`, `roads.wwseg.v1.json`, `roads.rate.v1.json`, `roads.names.v1.json`, `roads.days.v2.json`, `roads.days.damaged.v2.json`); yazım `.tmp` + rename, aynı 30 sn kısıtı ve "aynıysa yazma". Gerekçe: sürüşte 30 sn'de bir yazılan, sokak sayısıyla büyüyen bloblar (Bakü'de binlerce way → yüzlerce KB) `map` tercih dosyasında her yazımda bütün XML'i yeniden yazdırır; `kuryelog` dosyasına hiç girmez. Kötü noktalar `files/map/manual-marks.json` (aynı biçim; Y13 gelince o depo bu dosyayı devralır). Okunamayan dosya sessizce boşaltılmaz: `<ad>.damaged-<ms>` olarak saklanır, boş başlanır (YolGo'nun `roads.days.damaged.v2` ilkesi bütün dosyalara). Haftalık şifreli yedek (S22) bunları içermez (SORU-Q5).

### 9.11 Testler

Sayım `grep -cE '^\s*(test|testWidgets)\('` (döngü üretimi ayrıca belirtildi); gövdeler okundu.

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---|---|---|---|---|
| `quality_test` | 9 | (1) ön tahmin: residential > primary, unpaved < 0,4, rough ≤ 0,5 · (2) `learnedScore`: 0 durma 1, 2/km ≈ 0,78 (±0,01), 5 < 2, vib 5 düşürür, güven 80 m < 800 m, 5000 m = 1 · (3) `blendQuality`: null → 0,82, (0,1; 0,1) ≈ 0,748, tam güven 0,1, 👍 0,9, 👎 0,25, r 40 → 0,4, migrate · (4) eşit iki yol: kalitesi yüksek seçilir · (5) tolerans 0,10 kısa yol / 0,30 kaliteli / 0 kalite etkisiz (r 30 vs 👍, doğu %16 uzun) · (6) hayalet her koşulda (`routeLeg` ve `computePlans` 800 m) · (7) ww: onaylı 400 m ters, oran 0,02 → risk 0,02, 0,6 → dolambaç, `wwAvoid` → dolambaç · (8) parçalar sıralı, Σ = `rideM` · (9) ham/yumuşatılmış oran 0; 0,25; 0,55; < 0,02 | `route.QualityTest` | A6.2a → **A6.4** (hepsi; `Quality.kt` ve `CostModel` girdileri A6.4'te) | sentetik graf `OsmRaw` + `buildRoadGraph(raw, düz yükseklik)` (Bölüm 2 test yardımcısı); A6.11 bozmadan korur |
| `road_learner_test` | 9 | metre ≈ 161 / süre ≈ 29 / ad, way değişince geçiş · tam durma 1 + yavaşlama 0,5 = 1,5 · 200 sn bekleme ve `excludeStops` sayılmaz · ≤ 2 sn durma 0,5 · titreşim yalnız sürerken (5, 9 değil) · onaylı ters yön: 1 geçiş, olay ≥ 1, > 25 m, sorun 0 · ters yönde durup devam tek geçiş · onaysız ve ~11 m sayılmaz; "sorun yaşadım" → sorun 1, oran 1 · JSON gidiş-dönüş, `learnedForRouting` r 35, `wrongWayAvoid(0,3)` = {9}, 0,5 → boş | `roads.RoadLearnerTest` | A6.2a → A6.11 | 1 Hz sahte sürücü (`_Ride`), kuzeye düz yol |
| `road_save_test` | 1 | arka plana geçişteki zorla kayıt katman sayacını artırmaz; `rate(42, 25)` artırır ve `roads.rate.v1`'e yazar | `roads.RoadBookTest` | A6.2a → A6.11 | yaşam döngüsü yerine `onPause()`; bellek içi dosya deposu; sayaç = katman yenileme sayacı |
| `day_contributions_test` | 3 | (1) seçili gün yalnız kendi otomatik farkını siler; taban/elle/diğer gün kalır; yeniden yüklemede aynı; aynı tarihli yeni gün de silinir · (2) elle sıfırlama yeniden yüklemede kalır · (3) `SurfacePassTracker` (Y13) | `roads.DayContributionsTest` (1–2) | A6.2a → A6.11 (2); **sonra Y13** (1) | kova `yolgo-<ms>`; `bakuDay` → `LogTime.day(ms, ZoneOffset +4)` test saati |
| `ww_clean_run_test` | 5 | onaysız→onaylı parça: sürüş [101, 100], `wwCleanApprovals` {100, 101} · soru sonrası aynı sürüşte girilen kısa aynı adlı parça eklenir · ana cadde ve kısa başka adlı onaylanmaz, uzun adsız onaylanır ({100, 302}) · araya > 10 sn normal sürüş girerse önceki sokak katılmaz · ters yönde ışıkta durmak sürüşü bölmez | `roads.WwCleanRunTest` | A6.2a → A6.11 | 1 Hz güneye sürücü |
| `ww_no_problem_green_test` | 5 (6 çalışma; son test TR/AZ döngüsü) | saha: olaylı geçiş, Ses− (hayır) → {100, 101} onaylı ve `streets.approved`'da, yeşil kenar sayısı, toast "Onaylandı: Test küçəsi (iki yön)", sorun 0; 101 devamı yeniden sorulmaz; yeniden açılışta aynı · `wwAskAlways`: olaysız geçişte de sorulur, devamı sorulmaz · "Evet" (Ses+): sorun 1, onay eklenmez, toast "Onaylandı" değil · "Geri al": yalnız yeni onay kalkar · kart TR/AZ: "Hayır"/"Xeyr" dokunuşu → onay + toast metni | `roads.WwAnswerFlowTest` (sahte eşleştirme + sahte `StreetPrefs` + toast havuzu, `VolumeDispatch` üstünden) + `:app` Robolectric `WrongWayCardTest` (TR/AZ) | A6.2a → A6.11 | yeşil çizgi sayısı yerine onaylı kenar kümesi (Bölüm 6 katman fonksiyonu); GPS → eşleştirici yolu Bölüm 7 sahte konumuyla |
| `router_test` ↳ cadde oranı (Bölüm 3.14) | 1 | `streetRatio`: boş → 400 m / 0; tek nokta yalnız kendi 50 m'si (%13, metinde "%13"); ölçülmüş kötü 50 m + 200/210 m'deki iki nokta (birleşir) ≈ 110 m; güveni 0,2 ölçüm sayılmaz | `roads.StreetRatioTest` | A6.2a → A6.11 | 1.0'da nokta kısmı; ölçülmüş kesim kısmı `SurfaceSummary` ister → Y13'le |
| `street_scores_test` | 12 | E11: ağırlıklı ortanca, yarıda ortalama, sınır sırası, "ölçüm az" (3 parça / 200 m), puansız parça, aynı ad tek sokak, **(7) adsız yol adı TR/AZ (`RoadKnowledge.nameOf`)**, sıra, etiket ekleri, rota kalitesi (v−1)/9, isolate, kalibrasyon puanlayıcı | `roads.RoadNamesTest` (yalnız 7) | A6.2a → A6.11 (1); **sonra Y13/Y14** (11) | 11'i `SurfaceSummary` + `RoadCalibrationRecord` ister (SORU-Q2) |
| `road_score_ui_test` | 11 | E10/E11 UI: ayrıntıda eski "Rota/harita puanı", sınır etiketleri, parça sırası, Kalite sekmesi sokak listesi, "E10 için yeniden kalibre et", doğrulanmış darbe, ters/eşit referans, eksik seviye istemi, hız bandı uyarıları | — | **sonra Y13/Y14** (11) | hepsi kalibrasyon ve yüzey deposu ister (SORU-Q2) |

Toplam: Y12'ye yazılı 55 testten **32 tanım (33 çalışma) A6.2a'da çevrilir** — quality 9 (A6.4'te yeşil), road_learner 9, road_save 1, day_contributions 2, ww_clean_run 5, ww_no_problem_green 5 (6), street_scores 1 — ve **23'ü sonra (Y13/Y14)**: street_scores 11, road_score_ui 11, day_contributions 1 (SORU-Q2 onaylanırsa `TEST_ENVANTERI` ve `OZELLIK_HARITASI` bu bölüme göre düzeltilir). Y12 girdilerini sınayan öteki testler (A6.4'te yeşil, A6.11 bozmadan korur): Bölüm 3.14 `router_test` "öğrenilmiş kötü yol" (`route.PlannerTest`) ve "kötü nokta" (`route.BadSpotCostTest`), Bölüm 4.6 `ww_green_two_way_test` (4).

**WEEM ekleri (eski testte yok):** `VolumeDispatchTest` (450 ms çift basış, öncelik sırası, uzun basış iptali, soru/kart açıkken yönlendirme, ana cadde reddi), `QualityPromptTest` (başlangıç 80 ve yuvarlama, ±10 sınırları 10/100, 6 sn otomatik kayıt, vazgeç), `RoadFilesTest` (atomik yazım, "aynıysa yazma", 30 sn kısıtı, okunamayan dosyanın `damaged` kopyası), `MarkStoreTest` (kötü nokta ekle/sil/geri al, 2 Bakü günü doğrulama, gün silmede kalır).

### 9.12 WEEM yerleşimi

- **`logic/map`** (saf):
  - `route/Quality.kt`: `quality.dart` birebir (`LearnedQuality`, `QualityRating`, `priorQuality`, `learnedScore`, `learnedConfidence`, `blendQuality`, `streetScoreQuality`, `wrongWayRatio`, `wrongWaySmoothed`, `QualityBand`) — `CostModel` (A6.4) ve `roads` kullanır; paket döngüsü olmasın diye `roads`'ta değil.
  - `surface/` (Y13'ün 1.0 alt kümesi, SORU-Q1): `SurfaceMark`, `SurfaceLabel`, `Passability`, `isBadSpot`, `badSpotSpans`, `SpotSpan`, sabitler 25 m / 30 m — router (A6.4) ve Ses− (A6.11) kullanır; Y13 sonra bu pakete eklenir.
  - `roads/`: `WayStats`, `WwStats`, `GhostMark`, `RoadKnowledge`, `LearnFix`, `WwRun(Way)`, `WwPass`, `wwCleanApprovals`, `RoadLearner` (birebir); `RoadDayContributions`; `StreetRatio` (`streetRatio`, `streetRatioText`); `QualityPrompt` (durum + 6 sn, saat enjekte); `VolumeDispatch` (9.5.2 tablosu: girdi tuş olayı + durum → eylem); `RoadBook` = `RoadController`'ın Riverpod'suz çekirdeği (vardiya kapsamı, `onFix`, soru ve zaman aşımı tik ile, cevap, "Sorun yaşadım", elle eylemler, kayıt politikası, gün silme, rota girdileri `forPlan(g)`); bağımlılıkları port: `RoadFiles` (oku/yaz), `StreetPrefs` (Y8, `route`), `Clock`, `RideLog` olay havuzu, `Toasts` (metin TR/AZ, renk, "Geri al" eylemi, süre — veri olarak), `Haptics`.
- **`:map`** (Android, `com.kuryelog.app.map`): `RoadFilesAndroid` (`files/map/roads/`, `files/map/manual-marks.json`), `VolumeKeys` (9.5.3), `RoadsRuntime` (`ShiftPort.state` → `RoadBook.scope`; konum + eşleştirici (Bölüm 7) → `onFix`; `OrderEvents.stages` → `excludeStops` için aşama; 5 sn tik; `onPause` → zorla kayıt), plan isteğine girdiler (Bölüm 7 nav).
- **`:app`:** `RoadsScreen.kt` (4 sekme), `WrongWayCard`, `QualityPromptCard` (Harita sekmesi alt paneli), uzun basış menüsünün Y12 satırları (Bölüm 6), Harita ayarları "Yollarım" grubu ve ses tuşu anahtarı; `MainActivity.dispatchKeyEvent` → `VolumeKeys`.
- **`:core` portları:** `ShiftPort` (öğrenme kapsamı, kova anahtarı `yolgo-<startedAtMs>`), `OrderEvents.stages` (yer/durak yakını), `Clock` (`LogTime.day` ile gün), gün silme kancası (Bölüm 12/15). Bildirim kullanılmaz (bütün geri bildirim uygulama içi toast + titreşim).
- **2 telefon (plan 4.5):** "2 telefon modu" Kapalı → tek telefonda Y12 tam. Receiver (scooter telefonu; harita ve navigasyon) → Y12 öğrenme, sorular, kartlar ve ses tuşu yakalama **yalnız burada**. **WEEM (öneri):** Provider rolünde Y12 kapalı (öğrenme, soru, kart, ses tuşu yakalama yok); Yollarım ve uzun basış menüsünün Y12 satırları için SORU-Q7. `:link` mesajlarında Y12 verisi yok: Receiver'a ulaşılamayıp yedek rota Provider'da açılırsa planlayıcı Y12 girdileri boş hesaplar (rota Receiver'ınkinden ayrılabilir; A8.3'te kesinleşir). `ShiftPort`/`OrderEvents` Receiver'da uzak olsa da (A8.3) arayüz aynı; Y12 farkı görmez.
- **Görevler:** A6.4 → `Quality.kt`, `surface/` alt kümesi, `CostModel`'in Y12 girdileri, `quality_test`; A6.5 → katman çizimi ve uzun basış menüsü iskeleti; A6.11 → `roads/` tamamı, `RoadBook`, ses tuşu, ekranlar/kartlar, kalan Y12 testleri, rota girdilerinin bağlanması.

### 9.13 Farklar, açık noktalar ve sorular

**Y13/Y14 sınırı (1.0'da kesilen kod yolları; boşken nötr):** `SensorHub.vibRms` hep 0 → `vibSum/vibN` birikmez, `vib` null, ekranda "sarsıntı ?" (WEEM titreşim sensörü eklemez); `surface`/`surfaceQuality` boş → `segmentCost` yüzey terimi ve `allowedAt` ölçülmüş kesim dışlaması işlemez; `closed` işaretli `SurfaceMark` yalnız Y13 yüzey menüsünden doğar → 1.0'da yok; `streetRouteQualityInBackground` boş liste → `{}`; `street_scores`, `road_score.dart`, `SurfaceQualityTab`'ın Y13/Y14 parçaları, `StreetPiecesScreen`, "Ölçüm ve kalibrasyon", "Haritada yüzey renkleri" katmanı → sonra.

**Eski hatalar / tutarsızlıklar (WEEM kararı):**
- JSON okuma hatası veriyi sessizce boşaltır ve sonraki kayıt üstüne yazar (`road_knowledge.dart:222-249`) → WEEM: `damaged` kopyası her `roads` dosyası için (9.10).
- `resetAllQuality` ve `_setStreet(block)` ölü → taşınmaz.
- `statsRev` (katman sayacı) yalnız testte okunur; harita katmanı artık elle puana bağlı değil (`road_controller.dart:398-404` yorumu eski) → WEEM'de yalnız `road_save_test` uyarlaması için sayaç.
- `QualityPrompt.open` grafsız `qualityOf` (prior 0,8) → başlangıç değeri aynen (davranış).
- Gün kimliği/tarih: gün silme ve kötü nokta doğrulaması Bakü günü (UTC+4), hayalet listesindeki tarih telefon yerel → WEEM tek `Clock.zone` (`CORE.md`).
- Sürüş kaydı olayları adlarıyla korunur (Bölüm 12 biçimi).

**SORU (koordinatöre):**
- **SORU-Q1 — Ses− kötü noktası Y13 deposuna yazar.** Y13 1.0'da yok; Ses− tek basış (±25 m kötü nokta), Ses− uzun (sil), Kalite sekmesindeki "Elle işaretlenen yüzeyler" ve router'ın kötü nokta cezası bu depoya bağlı. (a) **1.0'da elle kötü nokta alt kümesi** (öneri): `surface/` tipleri + `files/map/manual-marks.json`, 25 m / 30 m / 2 Bakü günü kuralı, rota cezası, uzun basışla silme, Kalite sekmesinde liste (düzenleyici yok); Y13 gelince aynı dosya/tip genişler. `router_test`'in kötü nokta testleri de bunu ister. (b) Ses− tek basış 1.0'da etkisiz — S3 ("Ses+/Ses− dahil") ve S9'a aykırı. Önerilen: (a).
- **SORU-Q2 — Y12'ye yazılmış 23 test aslında Y13/Y14.** `street_scores_test` (11/12), `road_score_ui_test` (11/11) ve `day_contributions_test` 3. test (`SurfacePassTracker`) E10/E11 "Yol puanları" ve yüzey/kalibrasyon tiplerini ister. (a) "sonra (Y13/Y14)"e taşı (öneri; A6.2a Y12 payı 32 tanım); (b) `Street`/`StreetScore` saf mantığını veri kaynağı olmadan 1.0'da yaz (kullanılmayan kod). Önerilen: (a).
- **SORU-Q3 — metinler.** (a) aynen; (b) öneri: Kalite sekmesindeki "Eski trafik kayıtları" / "Köhnə trafik qeydləri" (alt "Korundu · yüzey metriğine dönüştürülmedi") WEEM'de sıfırdan biriken veri için yanıltıcı → "Trafik kayıtları" / "Trafik qeydləri", alt "Rotayı etkilemez" / "Marşruta təsir etmir"; ses tuşu anahtarının TR metnine AZ'deki gibi "Ses+ ×2 kalite" eklenir. Önerilen: (b).
- **SORU-Q4 — ses tuşu yakalama kapsamı.** YolGo tek amaçlı uygulamaydı, önde olduğu her an yakalardı; WEEM'de başka sekmeler var. (a) yalnız Harita sekmesi ve alt ekranları önde iken (öneri; Siparişler/Scooter/Uzaktan/Daha fazla'da tuşlar normal ses); (b) WEEM önde olduğu her an (YolGo gibi). Önerilen: (a).
- **SORU-Q5 — Y12 verisi yedekte.** S22 haftalık şifreli yedeği `kuryelog` ayarları, gün kayıtları, `restaurants.json`, `courier.db` içerir; `files/map/roads/*` ve `manual-marks.json` girmez → telefon değişince Y12 sıfırdan başlar. (a) S22 aynen (öneri; karar değişmez); (b) `:map`'in elle/öğrenilmiş verisi (Y12 ve Y8 `streets.*`, Y11 ışıklar, Y15 elle bisiklet yolları — öteki bölümlerle birlikte) yedeğe eklensin. Önerilen: (a), istenirse (b) A6.11 sonrasında ayrı iş.
- **SORU-Q6 — madde 10b "iki yönlü" kuralı yumuşatılmış oranla neredeyse hiç işlemiyor** (9.9 not). (a) aynen (öneri: davranış aynı, A6.10 karşılaştırması temiz); (b) niyete göre: ham `problems == 0` olan onaylı ara sokak iki yönlü (ceza/hız tavanı yok) — rota YolGo'dan ayrılır. Önerilen: (a).
- **SORU-Q7 — Provider'da Yollarım.** Plan "sekmeler iki telefonda aynı, veri asıl kaynağından" der; Y12 verisi Receiver'da ve `:link`'te yok. (a) Provider'da Yollarım'ın Kalite/Ters yön/Yol yok sekmeleri ve uzun basış Y12 satırları gizli, yerine "Yol bilgileri Receiver telefonunda" notu (öneri; 1.0 basit, A8.3'e iş eklemez; AZ metni A6.11'de); (b) Receiver'ın Y12 verisi `:link` üstünden gösterilir, elle eylemler Receiver'a gönderilir (A8.3'e yeni mesaj). Önerilen: (a).
### 9.14 A6.11 uygulama notları (20 Ekim 2026)

- **Saf (`logic/map`):** `roads` — `RoadKnowledge`/`WayStats`/`WwStats`/`GhostMark` ve `RoadLearner` (`road_knowledge.dart` birebir; süreler Dart `inSeconds` gibi tam saniyeye kesilir), `RoadDayContributions` (`day_contributions.dart` birebir, gün `LogTime.day(ms, Clock.zone)`), `RoadBook` (`RoadController` çekirdeği: `startDay`/`endDay`, `onFix`, `tick` 20 sn zaman aşımı, `answer`, `reportProblem` (+ "Ters yön sorunu kaydedildi"), elle eylemler, kötü noktalar, `forPlan(g)` (hayalet/ww kesimi kenarları önbellekli), `deleteContributions`, kayıt 30 sn kısıtı + "aynıysa yazma" + `onPause`), `VolumeDispatch` (9.5.2 tablosu; 450 ms tek basış `due()` ile, uzun basış `onDownHold`), `QualityPrompt` (6 sn `due()`), `LearnFixes` (9.3.1), `RoadsText` (TR/AZ metinler, `wwRows`); `surface.MarkStore` (`manual-marks.json`, SORU-Q1 (a)); `route.StreetRatio` gövdesi. `MapSettings.roadLearning` (S26, varsayılan `false`, `settings.v1`'e WEEM alanı). `NavController.setRoadInputs(r, replan)`, `clearSuggestion`, `replanIfRoute`; `ui.Toasts.onChange`/`hide`.
- **Android:** `:map` `RoadsRuntime` (`NavRuntime.roads`): `RoadFilesAndroid` (`files/map/roads/<ad>.json`, `files/map/manual-marks.json`, `.tmp` + rename; okunamayan dosya `<ad>.damaged-<ms>`, gün defteri `roads.days.damaged.v2.json`); öğrenme kapsamı = vardiya açık **ve** `roadLearning` → `startDay("yolgo-<ShiftHub.startedAtMs>")`; `NavController.onMatchedFix` → `LearnFixes` → `onFix`; rota girdileri `combine(graf, RoadBook.states, eşik)` → `setRoadInputs` (elle değişiklik `routeRev` → açık rota yeniden hesap; sokak kararı değişince önce `reloadStreetPrefs`); 1 sn `prompt.due`, 5 sn `tick`; `DayDeletionRuntime.deleteContributions` bağlı; titreşim `Vibrator` (15/35/60/400 ms). `MainActivity.onKeyDown`/`onKeyUp` → `RoadsRuntime.onKeyEvent` (9.5.1 kuralları; Ses− 1000 ms `Handler`; `ComponentActivity.dispatchKeyEvent` androidx'te kısıtlı API olduğundan lint hatası verir); `onStop` → `onPause` zorla kayıt.
- **S26 uygulaması:** anahtar kapalıyken öğrenme kapsamı açılmaz (fix işlenmez, ters yön sorusu ve "Sorun yaşadım" şeridi çıkmaz), ses tuşları yakalanmaz (normal ses). Elle girilen bilgi (👍/👎, % kartı uzun basıştan, "Yol yok", ww kesimi, Yollarım düzenlemeleri) ve önceden öğrenilenler her zaman rotaya girer; ses tuşu anahtarı öğrenme kapalıyken pasif görünür. **Yorum:** "puan soruları hiç çalışmaz" = otomatik ters yön sorusu ve Ses+ ×2 kalite kartı; uzun basıştaki elle kalite kartı açık kalır (kullanıcı isterse kapatılır).
- **Ekranlar (`:app`):** `WrongWayCard` + `QualityPromptCard` (`RoadCards.kt`) Harita alt panelinde (`MapTab.wrongWay` yuvası; varış odağında yalnız ters yön kartı); `RoadsScreen`/`RoadsHost` (Kalite: elle puanlar + kalite sayfası, kalite toleransı 0–30, kötü noktalar sil/geri al, "Trafik kayıtları" (SORU-Q3 b); Ters yön: bilgi kartı, eşik 20–90 adım 5, "Her geçişten sonra sor", liste ve satır menüsü; Yol yok: liste, sil, "Tümünü sil" onayı; Bisiklet: `BikewayTab` OSM istatistiği, katman, ağırlık, elle işaretler). Girişler: Harita ayarları → "Yollarım" grubu (S26 anahtarı, ses tuşları, "Yolları görüntüle"), sol göstergelerin rota panelindeki "Yol ayrıntıları". Uzun basış: elle puan (% kartı) + 👍/👎, kötü kesim oranı, "Bu yolu tamamen kapat"/kaldır, ww kesimi, "Burada yol yok"/"Bu sokağın tamamı yok" (+ "Geri al" 7 sn). Harita katmanı: hayalet gri kesikli 5 px, ww kesimi yeşil kesikli 4 px.
- **Açık / sonraya:** rota sırasında "Yol yok" seçicisi (9.8.5, rota parçaları listesi) yok — uzun basışla ekleniyor; uzun basıştaki "Saatli çift yön izni" (Y8 saat düzenleyicisi) ve Ters yön satırındaki "İzin saatlerini düzenle" yok; onaylı/kara liste/eğimli sokak katmanı (Y8 çizimi) yok; `OrderEvents` Android uygulayıcısı olmadığından `excludeStops` aşama kısmı (`atPlace`) hep `false` (durak 60 m / varış 80 m kısmı çalışır); 2 telefon rolü (SORU-Q7) A8.3'te; Y12 verisi yedeğe girmez (SORU-Q5 a). **AZ metin onayı bekleyenler (17.3'e):** "Yol keyfiyyətini öyrənmə" ve alt yazısı, kötü nokta satırı "pis nöqtə ±25 m", "Silinib" / "İstifadəçi qeydi".

<!-- END sec:9 -->

<!-- BEGIN sec:10 -->
## 10. Y9 Hız ve batarya tahmini, N5 Menzil uyarısı, Y10 Hava durumu (A6.7)

### 10.1 Kaynak ve kapsam

| YG dosya | Satır | İçerik | WEEM |
|---|---|---|---|
| `lib/routing/speed_options.dart` | 1–44 | `aeroShare`, `speedOption`, `speedOptions`, `arrivalClock` | `logic/map` `ride.SpeedOptions` |
| `lib/tracking/battery_estimator.dart` | 13–47 · 57–126 · 131–220 | `BatteryState` · `EnergyOdometer` · `BatteryController` | `ride.BatteryState`, `ride.EnergyOdometer`, `ride.BatteryEstimator`; Android `:map` `BatteryRuntime` |
| `lib/scooter/scooter_live.dart` | 13–82 · 84–232 | Rawstats `scooter/v1/live` yükü ve yoklayıcı | yük: `logic/core` `ScooterLive` (A2.5); yoklayıcı **çevrilmez** (iç `ScooterBridge.live`) |
| `lib/ui/scooter_sheet.dart` | 10–59 | batarya kutusuna dokununca canlı değerler | `:app` `ScooterValuesSheet` |
| `lib/ui/widgets.dart` | 148–230 · 232–281 | sol sütun: batarya kutusu, hız kutusu · `SpeedSheet` | `:app` (sol sütunun kalanı Bölüm 6/7.11) |
| `lib/economics/offer_engine.dart` | 5–307 · 322–383 · 385–422 | v1 teklif motoru `evaluateOffer`, `evaluateIncremental`, `compareDistances` | N5 temeli (10.6); kod taşınmaz |
| `lib/economics/route_costing.dart` | 8–143 · 145–149 | `economicLeg` (Y13 yüzeyine bağlı), `planLeavesBox` | N5 girdi kuralları (10.6) |
| `lib/weather/weather.dart` | 11–60 · 69–128 · 130–174 | `WeatherReport.parse`, `LiveWeather`, ayarlar, konum güncelleme | `logic/map` `weather.*`; Android `:map` `WeatherRuntime` |
| `lib/ui/weather_widget.dart` | 13–41 · 42–135 · 150–360 | simge/açıklama/renk, `LiveWeatherChip`, `WeatherBubble`, `WeatherDetail`, `WeatherMotion` | `:app` |
| `lib/ui/weather_section.dart` | 8–69 | ayar bölümü | `:app` Harita ayarları → "Hava durumu" |
| `android_overlay/WeatherWorker.kt` | 25–38 · 39–108 · 110–141 | `WeatherScheduler`, `WeatherData` (istek, önbellek, bildirim metni), `WeatherWorker` | `logic/map` `weather.WeatherRequest`/`WeatherText`; Android `:map` `WeatherFetch`, `WeatherWork` |
| `android_overlay/WeatherFeed.kt`, `WeatherFeedProvider.kt` | 10–42 · 15–35 | Rawstats'a özet (K28) ve sağlayıcı | özet kuralı → `WeatherPort` (10.10); sağlayıcı **kalkar** (S7) |
| `android_overlay/MainActivity.kt` | 169–197, 275, 383–390 | `configureWeather`, `updateWeatherLocation`, `weatherNow`, `weatherStatus`, `requestNotifPermission` | Android `:map` doğrudan (kanal yok) |

Bulgu doğrulaması (`Y_tracking.md` §Y9): formüller, eşikler, anahtarlar doğru. **(bulgu düzeltmesi:** "elle girişte hemen kayıt; olay `battery_set`" — 1.7.0'dan beri `setPercent`'i çağıran arayüz yok (CHANGELOG:179 "Elle % girme ve araç profili kalktı"), `battery_set` hiç üretilmez; `vehicleId`'yi değiştiren ekran da yok. UI satırları `widgets.dart:169–176`.) N5'in eski karşılığı yok (`OZELLIK_HARITASI` N5: "yeni"); `offer_engine`/`route_costing` uygulamada hiçbir yerden çağrılmıyor (yalnız testler; "Teklif kazancı" sayfası 13 Ekim'de kaldırıldı, CHANGELOG:148).

### 10.2 Hız seçenekleri (`speed_options.dart`)

- `speedOption(ride, p, kmh)` (:16–32): `v = kmh/3,6`, `ref = p.flatKmh/3,6`; düz Wh/m `flatPerM = flatWhPerM × ((1 − 0,6) + 0,6 × (v/ref)²)` (`aeroShare = 0,6`: düz tüketimin %60'ı hava direnci). Her ardışık nokta çifti (`RoutePoint`): `d = haversine`, `d ≤ 0` atlanır; `up = b.ele − a.ele`; `grade = up / max(d, 20)`; `grade > 0,02` ise `speed = min(v, p.climbMps(grade) × clamp(v/ref, 0,5, 1,2))`, değilse `v`; `t += d/speed`; `wh += flatPerM·d + (up > 0 ? climbWhPerM·climbSafety·up : 0)`. `climbMps` Bölüm 3.2. Sonuç `(kmh, seconds, wh)`.
- `speedOptions(ride, p, speeds?)` (:35–38): liste `speeds ?? [20, 25, round(flatKmh)]` → küme (tekrarsız) → artan sıra → her biri için `speedOption`.
- `arrivalClock(now, seconds)` (:41–44): `now + round(seconds)` → `"HH:mm"` (telefon yerel saati).
- Girdi rotası: planın bütün bacaklarının `ride` noktaları art arda (`[for l in plan.legs ...l.ride]`; yürüme dahil değil); parametreler `settings.paramsFor(battery.percent)` (Bölüm 3.2; batarya < `lowBatteryPct` → eğim limiti). Rota noktası < 2 ise seçenek yok.
- Hız kutusu (`widgets.dart:192–205`, Key `speed-popup`; yalnız seçenek varken): simge + her hız için `"<kmh yuvarlak> · <fmtMin(s)>dk"` / `"…dəq"`; dokununca `SpeedSheet`. `fmtMin(s)` = `s < 60 ? "<1" : round(s/60)` (`widgets.dart:33`).
- `SpeedSheet` (:239–281): başlık "Farklı hızlarda rota" / "Fərqli sürətlərdə marşrut"; alt "Süre, varış saati ve batarya tahmini. Hızı − / + ile değiştir." / "Vaxt, çatma saatı və batareya təxmini. Sürəti − / + ilə dəyiş."; `speedChoices`'ın her elemanı için satır: `speed-minus-i` / `speed-plus-i` (±1, `clampD(v, 5, 45)`, sonuç `speedChoices`'a yazılır, sıra korunur), `"<kmh> km/s"` / `"km/saat"`, rota varsa `"<dk> dk · varış HH:mm"` / `"dəq · çatma"` ve `"<Wh 0 hane> Wh (%<wh/capacityWh·100, 1 hane>)"` (`capacityWh > 0` ise), yoksa "Rota yok" / "Marşrut yoxdur".
- `speedChoices` (3 değer, 5–45; bozuk → `[20, 25, 29]`) ve okuma kuralı Bölüm 14 (`MapSettings`).

### 10.3 Batarya durumu (`BatteryState`, `BatteryController`)

- `BatteryState` (:13–47): `setPercent?` (çapa %), `consumedWh` (çapadan beri tahmini harcama), `capacityWh` (araçtan), `setAt?`, `livePercent?`, `liveExternalPercent?`; `live = livePercent != null`; `percent = livePercent ?? (setPercent == null ? null : clampD(setPercent − consumedWh/capacityWh·100, 0, 100))`; `remainingWh = capacityWh × percent/100`.
- Kapasite: `VehicleProfile` (Bölüm 3.2: `g3` 598 Wh, `g3x` 1066 Wh; bilinmeyen → g3) `settings.vehicleId`'den; ayar değişince durum yeniden kurulur.
- Canlı değer (:143–152): canlı görünüm (`scooterLive.live`, yalnız taze; 10.4) değişince: iç ve dış % aynıysa hiçbir şey; iç % yoksa yalnız canlılık düşer (`withLive(null, ext)`; tahmin eski çapadan sürer); iç % varsa **çapa olur**: odometre sıfırlanır, `setPercent = pct`, `consumedWh = 0`, `setAt = şimdi`, kayıt (30 sn kısıtlı). Sonuç: bağlantı koparsa tahmin son canlı %'den yürür (`v170` testi).
- Tahmin (:191–208): `setPercent == null` ya da canlıyken fix işlenmez. Aksi hâlde `EnergyOdometer.feed` (aşağıda) dönen Wh `consumedWh`'a eklenir. Yükseklik: önce barometre (`baro`), yoksa DEM (`dem`; harita yüklüyse, Bölüm 2.7), yoksa yok.
- `EnergyOdometer.feed` (:75–125), sırayla: doğruluk sonlu değil / < 0 / > 25 m ya da koordinat sonlu değil → 0. İlk fix → çapa + pencere başı yüksekliği, 0. `dt ≤ 0` → 0 (çapa kalır). `dt > 30 sn` ya da `d/dt > 16 m/sn` → çapa taşınır, 0 (sıçrama). Hız biliniyor (`speedMps ≥ 0`) ve `< 2,5 km/s` → çapa taşınır, 0 (duruş titreşimi). `d < 3 m` ya da `d < 0,35 × doğruluk` → 0 (çapa kalır; yavaş hareket birikir). Değilse çapa = nokta, `winDist += d`; `winDist < 30 m` → 0. Pencere kapanınca: tırmanış = (yükseklik ve pencere başı aynı kaynaktan ve artış) ? fark : 0; `wh = flatWhPerM·winDist + climbWhPerM·climb` (**`climbSafety` yok**; `speedOption`'da var), pencere sıfırlanır, başlangıç yüksekliği = son değer.
- Kalıcılık (:210–219): `battery.setPct` (Double, yalnız varsa), `battery.consumed` (Double Wh), `battery.setAt` (Int ms, yalnız varsa); en çok 30 sn'de bir (çapa değişiminde de kısıtlı). Açılışta okunur.
- `setPercent(pct)` (:165–181) ve `battery_set` olayı `{pct, estimated?, prevSet?, usedWh?, sinceMin?, capacityWh}`: **ölü yol** (arayüz yok). WEEM: yazılmaz (elle % girişi 1.7.0'da kullanıcı kararıyla kalktı).

### 10.4 Canlı scooter değeri → `ScooterBridge.live`

- YolGo okuyucusu (`scooter_live.dart`): yük ≤ 8000 karakter JSON, `schemaVersion == 1`, `generatedAtMs` sayı; `scooter{internalPct 0–100, externalPct 0–100, speedKmh 0–80, powerW −5000–5000, voltageV 0–100, rangeKm 0–300, tempC −40–120, odometerKm 0–1e6}`, `trip{wh −1000–5000, km 0–1000, avgMovingKmh 0–80}`; aralık dışı → o alan null (uydurulmaz). `drawW = −powerW` (çekilen güç pozitif). Taze (`freshAt`): `linkActive` ve `0 ≤ ageMs ≤ 15000` ve `|now − generatedAt| ≤ 30 sn`. Yoklama 2/15/60 sn, okuma zaman aşımı 3 sn, 8 boş okumadan sonra 60 sn, E8 duyurusunda hemen (≥300 ms aralık, 10 sn pencere).
- **WEEM:** tek telefonda `:scooter` aynı süreçte `ScooterBridge.live: StateFlow<ScooterLive?>` verir (`logic/core` `ScooterMessages.kt`: `fresh = linkActive && ageMs in 0..15000`, kaynak `weem_surus_paneli`); yoklama, duyuru, zaman aşımı, 30 sn yük yaşı **çevrilmez** (iç akış). Y9 yalnız `fresh` değeri canlı sayar. Alan eşlemesi: `internalPct` → `scooter.internalPct`, `externalPct` → `scooter.externalPct`, `speedKmh`, `powerW` (gösterimde `−powerW`), `voltageV`, `rangeKm`, `tempC`, `odometerKm` → `scooter.*`; `tripWh`/`tripKm`/`tripAvgKmh` → `trip.wh`/`trip.km`/`trip.avgMovingKmh`. YolGo'nun aralık süzgeci `:scooter` `ScooterLiveMapper`'da (A5.3) var; Y9 ayrıca süzmez.
- **2 telefon:** saniyelik `ScooterLive` Receiver'dan çıkmaz (plan 4.4). Receiver'da Y9 `ScooterBridge.live` ile aynı. Provider'da `BatterySummary` (R→P; 60 sn'de bir ya da %1 değişince, bağlantı kopunca; `internalPct`, `externalPct`, `rangeKm`, `tempC`, `ageMs`) canlı sayılır: `linkActive && ageMs ≤ 15000` ve alış anı (saat farkı düzeltilmiş `atMs`) ≤ 90 sn eski (öneri; A8.3'te kesinleşir). Provider'da GPS tahmini (`EnergyOdometer`) yürümez (konum Receiver'da, telefon cepte).
- `ScooterSheet` (batarya kutusuna dokununca): başlık "Scooter · Rawstats canlı" / "Skuter · Rawstats canlı"; canlıysa alanlar (140 dp ızgara): "İç batarya"/"Daxili batareya" %, "Harici batarya"/"Xarici batareya" %, "Hız"/"Sürət" `x,x km/s`, "Güç"/"Güc" `drawW W`, "Menzil"/"Məsafə" km, "Voltaj"/"Gərginlik" `x,x V`, "Sıcaklık"/"Temperatur" °, "Kilometre"/"Kilometr" `x,x km`, "Bu sipariş"/"Bu sifariş" `x,x Wh`, "Ort. hız (harekette)"/"Orta sürət (hərəkətdə)" `x,x km/s`; değer yoksa `—`. Canlı değilken iki metin (Rawstats'a bağlı/bağlı değil) Rawstats ayarlarını tarif eder. **WEEM:** başlık "Scooter · canlı" / "Skuter · canlı"; değer yoksa tek metin "Scooter bağlı değil — Scooter sekmesinden bağlan" / "Skuter qoşulmayıb — Scooter bölməsindən qoş" (öneri; Rawstats paylaşım anahtarı yok, S7).

### 10.5 Sol batarya kutusu (`widgets.dart:169–176`)

- Yalnız **rota varken** (YolGo SORU-3 kararı; rota yokken canlı batarya da gizli). Key `metric-battery`; simge canlı `battery_charging_full`, değilse `battery_unknown_outlined`; başlık "Batarya"/"Batareya"; değer `"<round(%)>%"` ya da `"—"`.
- Renk sahnesi (degrade, koyu yazı `#17283A`): canlı değil → gri `[#E3EAF2, #B9C7D6]`, simge `#3D5266`; canlı ve `% ≤ lowBatteryPct` (50) → turuncu `[#FFE1C2, #FFB27A]`, simge `#B4570F`; canlı ve üstü → yeşil `[#DDF3E6, #A6DDBD]`, simge `#1C7A47`. Alt yazı: harici % varsa `"+<round>%"`, yoksa canlı değilse "tahmin" / "təxmin", canlıysa yok. Dokununca `ScooterSheet`.
- Kutunun tahmin hâli (gri, "tahmin") **renkte de %50 eşiğini göstermez** (eşik yalnız canlıya uygulanır) — eski davranış, korunur.
- Aynı sütunun rota süresi, "Durağa kalan", rota ortalama hızı ve açılan panel Bölüm 7.11'de (Y4); yüzey kutusu Y13 (sonra).

### 10.6 N5 Menzil uyarısı (yeni)

- **Bugün YolGo'da:** ayrı menzil uyarısı yok. Var olan batarya davranışları Y4'te kalır (Bölüm 7.8): % `lowBatteryPct`'nin (50) altına inince bir kez "Batarya ~%N — eğim limiti %M" + yeniden rota (eğim `lowBatteryGradePct` 10); son durakta ya da durak yokken % ≤ `homeReturnPct` (30) → "Batarya düşük — eve dön?" kartı.
- **Temel taşlar (eski kod, uygulamada kullanılmıyor):** `offer_engine.dart` = ekonomi **v1** motoru (`evaluateOffer`: `worstEscapeRequiredWh = work.wh + returnToReference.wh + policy.reserveWh`, `batteryMarginWh = state.availableEnergyWh − escape`, `< 0` → `BATTERY_RESERVE_NOT_MET` engeli; dönüş olasılığı düşük olsa bile tam dönüş istenir). Rawstats ekonomi v2 bunun yerini aldı (`rawstats:docs/ekonomi-v2/SPEC_V2.md:12–20`: `ref.py --selftest offer-economics.json` v1 uyumu 40/40; v1 kuralının neden zayıf olduğu SPEC_V2:120). WEEM `:economics` (A4.1) v2'yi taşıdı: `EconomicsV2.hardBlocks` (`EconomicsV2.kt:538–548`) `need = (whWork + destToChargerKm·whPerKm)·1,15 + max(50, 0,25·(whWork + destToChargerKm·whPerKm))`, `availableWh < need` → `BATTERY_ESCAPE_NOT_MET`; `availableWh` = taze BLE % (≤15 sn) × 597 Wh × `soh`, yoksa `estimatorWh`, yoksa "unknown" (`EconomicsV2.kt:526–533`). `route_costing.dart` `economicLeg` plan → `{km, minutes, energyWh, roadKnownKm, roadRoughKm, access}` (bilinen/pürüzlü km yalnız Y13 kalibre yüzey ölçümünden; WEEM 1.0'da hep 0) ve `planLeavesBox` (rota ya da durak kutu dışına çıkıyor mu).
- **Bugün WEEM'de (A4.1 sonrası):** `ShadowEvaluator.Context` v2 girdisine `state.scooter` (`blePct`, `bleAtMs`, `estimatorWh`) ve `offer.destToChargerKm`/`destToHomeMin`/`state.hereToHomeMin` koymuyor → v2 her teklifte `BATTERY_UNKNOWN` ya da `CHARGER_DISTANCE_UNKNOWN` yazar (Rawstats'ta da aynı).
- **WEEM N5 (A6.7) önerisi:** yeni hesap yazılmaz; v2'nin var olan kuralı **girdileriyle** beslenir ve sonucu gösterilir.
  1. `:core`'a yeni port `RangePort` (öneri adı; `:map` uygular, `:economics` tüketir — `economics=core,orders`, `map=core` sınırı korunur): `battery(): RangeBattery?` = `{pct, wh, source: ble_fresh|estimator, atMs}` (canlı → `ScooterBridge.live`/`BatterySummary`; değilse Y9 tahmini `BatteryState.remainingWh`), `suspend rideTo(from, to): RangeLeg?` = `{km, minutes, wh}` (rota motoru `RideMode.battery`, Bölüm 3; harita yoksa null). `NONE` uygulaması veri uydurmaz.
  2. `:economics` teklif anında: bırakış noktası (`OfferSummary.dropoffLat/Lon`, çözüldüyse) → ev (`:core` ev; şarj yeri = ev) `rideTo` → `destToChargerKm`, `destToHomeMin`; konum → ev → `hereToHomeMin`; `battery()` → `state.scooter`. Ev, bırakış noktası ya da batarya yoksa alan yazılmaz (bugünkü "unknown" sürer).
  3. Gösterim ve ses: **SORU-T1-1**.
- **2 telefon:** teklif ve `:economics` Provider'da; rota Provider'ın kendi harita verisiyle hesaplanır (harita verisi telefon başına, Bölüm 2.14); batarya Provider'da `BatterySummary`'den (10.4). Receiver'a giden `OfferSummary.netAzn`/`score` gibi, sonuç (engel var/yok) `OfferSummary`'ye eklenecekse alan A8.3'te `:core` mesajına girer (yeni alan → `MessagesTest`).

### 10.7 Hava verisi (Open-Meteo)

- İstek (`WeatherWorker.kt:45–76`): `GET https://api.open-meteo.com/v1/forecast?latitude=<lat>&longitude=<lon>&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,wind_speed_10m,wind_gusts_10m,weather_code,is_day,relative_humidity_2m,cloud_cover,visibility,pressure_msl,uv_index,wind_direction_10m,dew_point_2m&current=temperature_2m,apparent_temperature,precipitation,wind_speed_10m,weather_code,is_day&forecast_hours=10&past_hours=1&timeformat=unixtime&timezone=auto&wind_speed_unit=kmh`; kullanıcı anahtarı varsa host `customer-api.open-meteo.com` ve `&apikey=<URL-kodlu>` (anahtar `weather.key.v1`, API ekranı Bölüm 8.9; değeri belgeye/koda yazılmaz). Anahtarsız çalışır. Bağlanma/okuma 12 sn. HTTP ≠ 200 ya da `hourly.time` < 5 → hata (eski doğru tahmin silinmez).
- Konum: `lat`/`lon` dizge, yoksa Bakü merkezi `40.4093, 49.8671`. Dart her taze fix'te (doğruluk 0–65 m, yaş −5…+30 sn), en çok 2 dk'da bir `updateWeatherLocation` → `lat`, `lon`, `locationAt` (ms) (`weather.dart:162–172`; geçersiz koordinat yazılmaz).
- Önbellek (yerel tercih dosyası `duzyol_weather`): `report` = `{"forecast": <yanıt>, "fetchedAt": ms, "latitude", "longitude", "locationAt"}`, `fetched` ms; `force` yoksa `0 ≤ now − fetched ≤ 1 200 000` (20 dk) iken ağ açılmaz. Anahtar değişince `report`/`fetched` silinir. Gün kapalıyken (`TelemetryService.accepts` → WEEM `ShiftPort` kapalı) gün kimlikli istek reddedilir ("day closed").
- `WeatherReport.parse(raw, now)` (`weather.dart:26–59`): `utc_offset_seconds` (yoksa 14400); taban = şu anki UTC saat başı; `[taban, taban + 8 sa)` içindeki saatler; taban saatinde `current` değeri varsa o (sıcaklık, hissedilen, yağış mm, rüzgâr, kod, gündüz, nem, bulut, basınç, rüzgâr yönü), olasılık/hamle/görüş/UV/çiy hep saatlikten; ≥ 4 satır ve ilk satır taban saati değilse `FormatException`. Saat etiketi servis saat diliminin duvar saati. Şerit 4 saat (`stripHours`), liste 8 (`detailHours`).
- `LiveWeather` (`weather.dart:69–128`): açılışta bir yenileme (gün kapalıyken yalnız önbellek); gün açıkken ve uygulama öndeyken 10 dk'da bir; gün açılınca hemen; gün kapanınca rapor kalır, zamanlayıcı durur. Önbellek < 20 dk ve ≥ 8 saatse ağ yok; < 8 saatse süreç başına bir kez (gün kapalı da olsa) ağdan tazelenir. Hata → önceki rapor kalır, "Hava güncellenemedi" / "Hava yenilənmədi".
- Ayarlar: `weather.enabled.v1` (Bool, true; bildirim), `weather.strip.v1` (Bool, true; harita şeridi), `weather.key.v1` (String), `weather.permission.asked.v1` (Bool; gün açık ve hava açıkken bir kez bildirim izni sorulur). Dil `az` bayrağı yerel tercihe (`I18n.resolve(lang)`).

### 10.8 Hava bildirimi (WorkManager, 7301)

- `WeatherScheduler.configure` (:26–37): gün açık değil ya da hava kapalı → `duzyol_weather_v1` iptal, süren arka plan isteği kesilir, 7301 kaldırılır. Değilse `PeriodicWorkRequest` **2 saat**, ağ gerekli, girdi `day`, `ExistingPeriodicWorkPolicy.UPDATE`.
- `WeatherWorker.doWork` (:110–140, tek seferde bir): gün kabul edilmiyor / hava kapalı → başarı (bir şey yapmaz); `NotifyPolicy.weatherDue(notifiedDay, day, notified, now)` (`:core`'da var, A2.4: aynı gün ve saat geri gitmediyse false → **günde bir**); API ≥ 33'te `POST_NOTIFICATIONS` yok ya da bildirimler kapalı → başarı. İstek (önbellek kuralıyla) → metin → yeniden gün/ayar denetimi → kanal `duzyol_weather` "Hava tahmini" / "Hava proqnozu" (DEFAULT) → bildirim **7301**: başlık "Önümüzdeki 4 saatin havası" / "Növbəti 4 saatın havası", içerik metnin 2. satırı, büyük metin tamamı, dokununca uygulama, `setTimeoutAfter(30 000)`, `autoCancel`, `onlyAlertOnce`; sonra `notifiedDay`, `notified` yazılır. Hata: gün kapandıysa başarı, değilse `runAttemptCount < 3` → yeniden dene, sonra başarı.
- Metin (`WeatherData.text`, :77–107; saat dilimi yanıtın `timezone`'u, yoksa `Asia/Baku`): ilk satır `"<yer> · HH:mm–HH:mm"` (yer: `locationAt == 0` → "Bakı", değilse "Son bilinen konum" / "Son məlum məkan"; aralık şimdi → +4 sa); sonra şimdiden sonraki ilk 4 saat: TR `"HH:mm · <t>°C (hissedilen <f>°) · yağış %<p> · <mm 1 hane> mm · rüzgâr <w> km/h (hamle <g>)"`, AZ `hiss`, `yağıntı`, `külək`, `ani`; değer yoksa `—`; son satır "Open-Meteo". Tam 4 saat yoksa hata.
- **WEEM:** `NotificationIds.WEATHER` 7301, kanal `duzyol_weather`, vardiya sonu iptal listesinde (CORE.md 3.3, `NotificationIds.SHIFT_END_CANCEL`); iş adı `duzyol_weather_v1` korunur; "gün açık" = `ShiftPort.state.active`. Bildirim `NotifyPort.post(Notify(WEATHER, "duzyol_weather", …, timeoutMs = NotifyPolicy.TIMEOUT_MS))`.

### 10.9 Hava ekranları

- **Şerit** (`LiveWeatherChip`, harita üstünde `weather.strip.v1` açıkken; `nav_screen.dart:113`): kapalıyken 62 dp yuvarlak (Key `live-weather`, `weather-compact`): şimdiki saatin simgesi, sıcaklık, 3. satır yağış ihtimali ≥ %10 ise damla + `%`, değilse rüzgâr (`weatherCompactExtra`). Dokununca 4 saatlik şerit: "Şimdi"/"İndi" + saatler, her saat yuvarlakta (`weather-strip-i`), altında yağış %; `weather-collapse` küçültür; `weather-toggle` 8 saatlik listeyi açar (`weather-hour-0…7`; rapor < 8 saatse açılınca yenilenir). Liste altı: `"Eski tahmin · "` (rapor ≥ 40 dk ya da hata) + hata metni ya da `"Open-Meteo · HH:mm"` (Bakü saati, `bakuTime(fetchedAt)`), "Yenile"/"Yenilə" (zorla). Boşken "Hava yükleniyor…"/"Hava yüklənir…" ya da "Hava verisi yok"/"Hava məlumatı yoxdur". Anlamsal etiket "Hava durumu"/"Hava proqnozu".
- **Simge/açıklama** (`weatherIcon`, :13–33), sırayla: rüzgâr ≥ 30 ya da hamle ≥ 45 km/s → rüzgâr "Rüzgarlı"/"Küləkli"; kod yok → yağış > 0 damla, değilse bulut; ≥ 95 "Gök gürültülü"/"Göy gurultulu"; 71–77, 85, 86 "Karlı"/"Qarlı"; ≥ 51 "Yağmurlu"/"Yağışlı"; 45, 48 "Sisli"/"Dumanlı"; ≥ 2 "Bulutlu"/"Buludlu"; gece "Açık gece"/"Açıq gecə"; diğer "Güneşli"/"Günəşli". Her simgenin degrade renkleri `weather_widget.dart:141–150`; yazı rengi son rengin parlaklığı > 0,35 ise koyu `#17283A`, değilse beyaz. Simge hareketi tek paylaşılan 80 ms saat (`WeatherMotion`).
- **Ayrıntı** (`WeatherDetail`, saate dokununca; ekranın %40'ı, Key `weather-detail`): başlık `"Şimdi|HH:mm · <açıklama>"` + sıcaklık; alanlar "Hissedilen", "Yağış ihtimali", "Yağış", "Rüzgar", "Hamle", "Rüzgar yönü", "Nem", "Bulut", "Görüş" (km, 1 hane), "Basınç" (hPa), "UV", "Çiy noktası" (AZ: "Hiss edilən", "Yağıntı ehtimalı", "Yağıntı", "Külək", "Külək şiddəti", "Küləyin istiqaməti", "Rütubət", "Bulud", "Görünüş", "Təzyiq", "UV", "Şeh nöqtəsi"); arka plan `assets/baku/credits.json`'daki fotoğraf (≥ 3 ise `index % sayı`; Bölüm 5), alt satır `"Open-Meteo · Tahmin · HH:mm"` (Bakü saati) ve fotoğraf bilgisi `"<artist> · <license> · Wikimedia Commons"` + URL.
- **Ayar bölümü** (`WeatherSection`; YolGo İlave ayarlar → "Hava durumu", Rawstats bilgilerinin altında): "2 saatte bir hava bildirimi" / "Hər 2 saatdan bir hava bildirişi" (alt "Sonraki 4 saat · yağış, rüzgâr, sıcaklık"), bilgi düğmesi (Key `weather-info`) penceresi "Son bilinen konuma göre; konum yoksa Bakı. Bildirim izni gerekir. Pil/ağ koşulları saati geciktirebilir. Kaynak: Open-Meteo." (AZ aynı yerde), "Haritada hava şeridini göster" (Key `weather-strip-toggle`, alt "Kapatınca üstteki 4 saatlik şerit tamamen gizlenir"), "8 saatlik tahmini göster" (dokununca ağdan; hata "Tahmin alınamadı. İnternet bağlantısını kontrol et." / "Proqnoz alınmadı. İnternet bağlantısını yoxla."; liste "Güncellendi H:mm" + "Kapat"). **WEEM:** Daha fazla → Harita ayarları → "Hava durumu" (Bölüm 6); anahtar başlığı **eski hata**: bildirim 1.7.0'dan beri günde bir → "Günde bir hava bildirimi" / "Gündə bir hava bildirişi" (öneri; S19 gibi onaya sunulur).

### 10.10 `WeatherPort` → R15 ve kayıt dizgisi

- `:map` `WeatherPort.current()`: son rapordan `WeatherNow` (`logic/core`, ≤ 90 dk taze): YolGo `WeatherFeed.summary` kuralı (`WeatherFeed.kt:15–41`) — saat dilimi `fetchedAt`'e göre son `time ≤ fetchedAt` olan saatlik dilim; sıcaklık, hissedilen, yağış mm, rüzgâr, kod `current`'tan (yoksa o dilim); yağış ihtimali ve hamle dilimden; eksik değer null; konum **yazılmaz**. Rapor yok ya da bozuksa null.
- `:orders` `OfferWeather.from(w, now)` teklif satırına `weather_source: "yolgo_open_meteo"` yazar (`logic/orders/OfferWeather.kt:57–59`; kayıt dizgisi korunur, `VERI_SOZLESMESI` 7.1); `WeatherNow.source = "open_meteo"` yalnız iç mesaj etiketidir, kayda girmez. `:economics` `EnergySegments` `weather_source` aynı yoldan. `is_day` `WeatherNow`'da yok (teklif kaydında da yoktu).
- S6: her telefon kendi havasını çeker; `WeatherNow` `:link`'ten gönderilmez. `WeatherFeedProvider` (`az.duzyol.app.weather`) kalkar (S7).

### 10.11 Veri

| Ad | Tip · varsayılan | YolGo yeri | WEEM |
|---|---|---|---|
| `battery.setPct`, `battery.consumed`, `battery.setAt` | Double, Double Wh, Int ms | Flutter tercihleri | `map` tercih dosyası, aynı adlar (S1: aktarılmaz) |
| `speedChoices`, `vehicleId`, enerji katsayıları, `lowBatteryPct`, `lowBatteryGradePct`, `homeReturnPct` | `settings.v1` | Flutter | `MapSettings` (Bölüm 14) |
| `weather.enabled.v1` true, `weather.strip.v1` true, `weather.key.v1` "", `weather.permission.asked.v1` | Bool/Bool/String/Bool | Flutter | `map` tercih dosyası, aynı adlar |
| `duzyol_weather` dosyası: `enabled`, `az`, `key`, `report`, `fetched`, `notified`, `notifiedDay`, `lat`, `lon`, `locationAt` | yerel tercih | native | WEEM'de tek kaynak: `map` dosyası ya da ayrı `weather` dosyası (öneri: tek `map`; `az` yerine `:app` `lang`, `enabled`/`key` yukarıdaki anahtarlar; önbellek `files/map/weather.json`) |
| WorkManager `duzyol_weather_v1` | periyodik 2 sa | native | aynı ad |
| bildirim 7301, kanal `duzyol_weather` | DEFAULT, 30 sn, günde bir | native | `NotificationIds.WEATHER` (CORE.md 3.3) |
| ağ: `api.open-meteo.com`, `customer-api.open-meteo.com` `/v1/forecast` | 12 sn | native | aynı; `HttpPort` (Bölüm 8.12) |
| `content://az.duzyol.app.weather/weather/current` | K28 | sağlayıcı | kalkar (S7); iç `WeatherPort` |

### 10.12 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| `battery_estimator_test` | 5 | 600 sn ±6 m titreşim, hız 0 → 0 Wh · hızı bilinmeyen 2,5 m sıçrama (doğruluk 15) → 0 · 1050 m düz sürüş ≈ `1050·0,0135` (±30 m pencere) · baro→DEM geçişi sahte tırmanış eklemez, aynı kaynak artışı sayar · 900 m/2 sn sıçrama → 0 | `ride.EnergyOdometerTest` | A6.2b → A6.7 | 1. test Dart `Random(7)` dizisi kullanır; sonuç diziden bağımsız (0), Kotlin'de sabit sahte dizi |
| `v140_features_test` | 14 | **Y11** (5): ışık mesafesi 5/10 m ayarı, AVM ışık değil + ışık yarıçapında yol kenarı ışık, `isRoadside` (yarım şerit, doğruluk 15 → hayır), canlı ölçer uzak ışıkta başlamaz, AVM duruşu `mall` · **Y3/Y8** (3): dönüş cezası (merdiven 600 m/5 dönüş vs L 716 m; ceza süreye eklenmez), ana caddede elle ters yön izni (1200 → 400 m), izin arka plan işçisinde de · **Y18** (3): sapma iki fix sonra açılır/kapanır + ort. hız + `duzyol-route-v1` + adres yazılmaz, yeniden hesap sapmayı kapatır, `_route_` dosyası "route" · **Y9** (1): 20/25/29 km/s; hızlı = kısa ama çok Wh; ayar hızında Wh ≈ `flatWhPerM·1000`, süre ≈ `1000/flatMps`; %8 yokuşta yavaş · **ayar** (2): eski JSON varsayılan alır (`lightMaxM` 5, `turnPenaltyS` 12), eski `voice` yok sayılır ve parametreler korunur | Y11 → `day.LightRuleTest`, `day.LightTrackerTest`, `day.ShiftEngineTest`; Y3/Y8 → `route.RouterTest`, `route.RouteEngineTest`; Y18 → `logs.RouteRecorderTest`, `logs.RideLogStoreTest`; Y9 → `ride.SpeedOptionsTest`; ayar → `settings.MapSettingsTest` | A6.2a (Y3/Y8 → A6.4), A6.2b (Y9 → A6.7, Y11 → A6.8, Y18 → A6.9; ayar → A6.4, Bölüm 14) | dosya çok özellikli: her test ayrı hedefe; sentetik graflar (`_turnGraph`, `_mainOneway`) `route` test yardımcısına |
| `v160_y1_y2_test` | 5 | 11 saatlik cevaptan şimdi + 7 saat (12:00…19:00, ilk sıcaklık 11) · eski 6 saatlik önbellek 4–6 saatle geçerli · şimdiki saat yok ya da < 4 saat → hata · widget: şerit 4 saat, liste 8 satır, 8. saatin ayrıntısı · konum imleci ~20 dp (Y1) | `weather.WeatherReportTest` (3), `:app` Robolectric `WeatherChipTest` (1); imleç → `ui.PuckStyleTest` (Bölüm 6) | A6.2b → A6.7 (imleç A6.5) | `_raw` yardımcısı JSON üretir |
| `v170_features_test` | 13 | yük ayrıştırma (78/64 %, `drawW` 310, şema dışı/bozuk/aralık dışı null) · tazelik (bağlantı yok, çerçeve 20 sn, yük 2 dk) · canlı % tahmini geçer, bağlantı kopunca son canlı %'den sürer · voltaj/km değişimi ekranı tazeler · yoklama 2/15/60 sn · takılan okuma zaman aşımı · Rawstats ev cevabı · yüzey dizini · gün kartı simgeleri ve 20 sn · alt yer düğmesi = konum düğmesi boyu · hava yuvarlağı ve şerit · rota yokken batarya gizli · canlı batarya + `+64%` + sayfa `310 W` | ayrıştırma/tazelik → `logic/core` `MessagesTest` (A2.5'te `ScooterLive`; aralıklar `:scooter` `ScooterLiveMapper`, A5.3) — yalnız uyarlanır; batarya çapası → `ride.BatteryEstimatorTest`; ekran: `:app` Robolectric `MapMetricsTest` (2), `WeatherChipTest` (1), gün kartı ve düğme boyu Bölüm 6 (`ShiftBar`, `RouteFab`) | A6.2b → A6.7 (ekranlar A6.5/A6.7) | **çevrilmez:** voltaj/km tazeleme (StateFlow eşitliği), yoklama aralığı, zaman aşımı (Rawstats yoklaması yok), Rawstats ev cevabı (S7); **sonra:** yüzey dizini (Y13) |
| `weather_section_test` | 4 | şerit anahtarı varsayılan açık, kapatılınca saklanır · açıklama bilgi penceresinde · Ayarlar'da Hava yok, İlave ayarlar başlığı hava der · İlave ayarlar'da Rawstats bilgilerinin altında, kayıtlı ayar korunur | `:app` Robolectric `MapWeatherSettingsTest` | A6.2b → A6.7 | yer testleri WEEM ayar yapısına uyarlanır (Harita ayarları → Hava durumu; "Rawstats bilgileri" yok) |
| YK `WeatherFeedTest` | 2 | `current` kazanır, saatlik dilim tamamlar (12,7°; %40; hamle 33,5; 0,4 mm; kod 61; kaynak `yolgo_open_meteo`; `fetched_at`; hissedilen null; **konum yok**) · `current` yokken dilim; bozuk → null | `weather.WeatherSummaryTest` (`WeatherNow` üretimi) | A6.2b → A6.7 | kaynak etiketi WEEM'de `WeatherNow.SOURCE`; kayıt dizgisi `OfferWeather` testinde (A3.6) |
| `offer_engine_test` | 7 | 40 fikstürlük "JS/Dart parity" döngüsü (46 çalışma) · düşük dönüş olasılığı tam kaçış bataryasını zayıflatmaz · fiyat artışı kapalı rotayı geçerli yapmaz · ek teklifte aynı nokta/politika zorunlu · ek teslimatın fırsat maliyeti · eşleşmeyen mesafe kapsamı reddi | — | — | **SORU-T1-2** (öneri: çevrilmez; v1 motoru ölü kod, v2 `:economics`'te; fikstür `test/fixtures/offer-economics.json` başvuru olarak) |
| `route_costing_test` | 6 | yalnız kalibre kısım bilinen · ters yön ölçümü sayılmaz · çift ölçüm iki kez sayılmaz · eski/başka profil sayılmaz · kısmi rota tüm sokak uzunluğunu almaz · merkez denetimi rota geometrisini ve durağı içerir | 5 → **sonra (Y13)**; `planLeavesBox` (1) → SORU-T1-2 | — | 5 test `SurfaceSummary`'ye bağlı |

Toplam (bu bölüm): 56 test tanımı; şimdi çevrilen 36 (A6.2b 33, A6.2a 3 — v140 rota), uyarlanan 2 (v170 yük ayrıştırma/tazelik), çevrilmeyen 4 (v170), SORU-T1-2'de 8, sonra 6 (Y13). `remaining_km_e12` ve `v150_features` Y9 parçaları Bölüm 7.15'te (`ride` hedefi).

### 10.13 WEEM yerleşimi

- `logic/map` `ride`: `SpeedOptions` (`speedOption`, `speedOptions`, `arrivalClock`, `AERO_SHARE`), `BatteryState`, `EnergyOdometer`, `BatteryEstimator` (saf durum makinesi: girdiler fix + yükseklik örneği + `ScooterLive?` + ayar; çıktı `BatteryState` + kayıt isteği), `RangeInputs` (N5: `RangePort` sonuçlarından v2 girdi alanları). `logic/map` `weather`: `WeatherRequest` (URL kurma), `WeatherReport`/`WeatherHour` (`parse`), `WeatherSummary` (`WeatherNow` üretimi), `WeatherText` (bildirim metni), `WeatherIcons` (simge/açıklama/renk), `WeatherSchedule` (10 dk, 20 dk önbellek, kısa önbellek kuralı).
- Android `:map`: `BatteryRuntime` (fix akışı `NavLocation`, barometre `SensorManager`, DEM `MapData`; tercihler), `WeatherRuntime` (konum güncelleme, `LiveWeather` karşılığı `StateFlow`), `WeatherFetch` (`HttpPort` ile istek + önbellek), `WeatherWork` (WorkManager `duzyol_weather_v1`), `WeatherPortImpl`, `RangePortImpl`. `:core`: `RangePort` (yeni, öneri), `WeatherPort`, `NotifyPort`, `ShiftPort`, `ScooterBridge`.
- `:app`: sol sütun batarya ve hız kutuları, `SpeedSheet`, `ScooterValuesSheet`, `WeatherChip` (şerit), `WeatherDetailSheet`, Harita ayarları → "Hava durumu".
- 2 telefon: Y9 tahmini ve hava şeridi her telefonda (Provider'da batarya `BatterySummary`'den, GPS tahmini yok); hava her telefonda ayrı (S6); N5 Provider'da (`:economics`).

### 10.14 Farklar ve açık noktalar

- **Eski hatalar / tutarsızlıklar (WEEM'de baştan doğru):** ayar başlığı "2 saatte bir" ama bildirim günde bir (10.9); `EnergyOdometer` tırmanışa `climbSafety` uygulamaz, `speedOption` uygular — davranış aynen korunur (iki ayrı model), not düşülür; `battery_set` ve `setPercent` ölü kod (yazılmaz); gün kapalıyken bile süreç başına bir kez ağdan hava çekilir (`_shortFetched`) — korunur.
- **Saat:** `arrivalClock`, ayar bölümündeki "Güncellendi H:mm" telefon yerel saati; şerit/ayrıntı alt satırı Bakü saati (`bakuTime`), bildirim metni yanıtın `timezone`'u (`auto` → Bakü). WEEM: hepsi telefon yerel saatiyle aynı olur (`LogTime` Bakü'dür; Bakü'deki telefonda fark yok); şerit/ayrıntıda `bakuTime` korunur (yurt dışı kullanım yok).
- **SORU-T1-1 (koordinatöre; N5 kapsamı):** N5 "Menzil uyarısı" eski uygulamada yok; yalnız adı plan kapsamında. Öneri: (a) teklif anında v2 `BATTERY_ESCAPE_NOT_MET` (10.6 girdileriyle) doğarsa Siparişler'deki teklif rozetine/kartına tek satır "Batarya yetmeyebilir · eve ~<km> km" / "Batareya çatmaya bilər · evə ~<km> km" + harita sekmesinde Y4'ün var olan "eve dön?" kartı; kabul yine kullanıcıda (WEEM hiçbir şeye basmaz); (b) yalnız v2 girdilerini doldur ve kaydet (gölge modda karşılaştırma için), görünür uyarı WEEM 1.0 sonrası; (c) ayrı menzil ekranı (kalan km tahmini). Varsayılan önerisi (a).
- **SORU-T1-2 (koordinatöre; test kapsamı):** `offer_engine_test` (7; 40 fikstür) ve `route_costing_test`'teki `planLeavesBox` testi (1) YolGo'da hiçbir özelliğin kullanmadığı v1 kodunu sınar; v2 `:economics`'te (A4.1, altın vektörler). Öneri: (a) **çevrilmez** — `TEST_ENVANTERI`'nde "çevrilmez (ölü kod; ekonomi v2 ile değişti)"; N5 için yeni testler v2 girdileriyle yazılır (`RangeInputsTest`: ev/bırakış/batarya yokken alan yok; kaçış ihtiyacı formülü `EconomicsV2` ile aynı); (b) v1 motoru `logic/economics`'e başvuru olarak taşınır, 46 çalışma yeşil olur (kullanan yok). Varsayılan (a).
- **Risk:** Provider'da `BatterySummary` 60 sn'lik olduğundan v2'nin 15 sn BLE tazelik kuralına uymaz; öneri: Provider'da `source = estimator`, `wh = internalPct × kapasite` (10.4); Receiver'ın son değeri 90 sn'den eskiyse batarya yok sayılır. Bırakış koordinatı yalnız Wolt "Customer drop-off" ya da çözülen adresten gelir (`OfferSummary.dropoffLat/Lon`, S5); yoksa N5 sessiz kalır.
### 10.15 Uygulama notları (A6.7)

- **Saf mantık (`logic/map`):** `ride` — `SpeedOptions` (10.2), `EnergyOdometer`/`AltitudeSample`/`BatteryState`/`BatteryEstimator` (10.3; çapa canlı değerdir, kayıt `battery.*` en çok 30 sn'de bir, ilk yazım hemen; elle % yok; `setCapacity` araç profili değişince), `RangeInputs` (10.6). `weather` — `WeatherReport.parse` (+ `WeatherHour` ayrıntı alanları), `WeatherSummary` (`WeatherNow`), `WeatherIcons`/`WeatherKind` (simge sırası, açıklama, degrade, yazı rengi, `compactExtra`), `WeatherRequest` (adres, 20 dk önbellek, konum doğrulama), `WeatherNotifyText` (bildirim metni), `WeatherPrefs` (anahtarlar 10.11; yardımcı anahtarlar `weather.fetched`/`notified`/`notifiedDay`/`lat`/`lon`/`locationAt`), `WeatherFetcher` (çekim, önbellek, gün kapısı; `ReportStore`), `WeatherNotifier` (günde bir; `NotifyPolicy.weatherDue`), `LiveWeather` (YolGo `LiveWeather`: önbellek < 20 dk ve ≥ 8 saat ağ yok, kısa önbellek süreç başına bir kez, hata → önceki rapor). `ui` — `RideText`, `WeatherUiText` (`:app`'te düz metin yok).
- **Android (`:map`):** `BatteryRuntime` (fix akışı `NavLocation`, barometre → DEM, `:app` `bind(Scooter.bridge)` ile taze canlı değer; `range: RangePort`), `WeatherRuntime` (durum, ayarlar, 10 dk yenileme, konum güncellemesi, bildirim, `WeatherPort`), `FileReportStore` (`files/map/weather.json`, atomik). **Sapma (WorkManager yok):** hava bildirimi eski `duzyol_weather_v1` WorkManager işi (2 saat) yerine süreç içi döngüyle: gün açıkken vardiya ön plan servisi süreci canlı tutar; gün açılınca hemen, hata olursa 2 saatte bir (en çok 3 kez), günde bir (`NotifyPolicy.weatherDue`). Yeni bağımlılık eklenmedi; süreç ölürse (gün açıkken olmaz) bildirim o gün atlanır. `ShiftHub` `startedAtMs` gün kimliğidir. İzin: gün açık, hava açık, hiç sorulmamış ve API ≥ 33 iken `MapScreen` bir kez `POST_NOTIFICATIONS` ister.
- **`:app`:** `MapMetrics` (sol sütun: `metric-battery`/`-time`/`-remaining`/`-avg`, `speed-popup`, açılır panel; dil değişince panel kapanır), `SpeedSheet`, `ScooterValuesSheet` (`scooter-sheet`; Rawstats paylaşım anahtarı yok → "Scooter bağlı değil — Scooter sekmesinden bağlan"), `WeatherChip`/`WeatherDetailSheet`/`WeatherMotion`, Harita ayarları → "Hava durumu" (başlığa dokununca açılır; M-S19 başlığı "Günde bir hava bildirimi"). Pencereler `Dialog` ile alt kenara oturur (ModalBottomSheet değil; JVM denemelerinde kararlı). Ayrıntı fotoğrafı (`assets/baku/credits.json`) yapılmadı: açık gradyan zemin; lisans/kredi satırı yok (fotoğraf yok).
- **Hava bağı (R15):** `Orders.bindWeather(WeatherRuntime.port)` (`WeemApplication`); `OrderFlow.Env.weather` ve `EconomicsObserver.Env.weather` aynı yoldan. `WeatherPort.current()` = son rapor özeti, ≤ 90 dk taze; konum yazılmaz.
- **N5 (menzil) durumu — kısmi:** `:core` `RangePort`/`RangeBattery`/`RangeLeg` (+`NONE`), `logic/map` `RangeInputs` (v2 alan adları; ev/bırakış/batarya yoksa alan yok), `BatteryRuntime.range` (batarya: taze BLE % ya da Y9 tahmini; rota: `RideMode.BATTERY`). **Bağlanmadı:** `ShadowEvaluator.Context` / `EconomicsObserver` (teklif anı eşzamansız rota isteği ister; eşzamanlı gölge değerlendirme tek karede biter) ve görünür rozet — SORU-T1-1 (a) onayı ve tasarım kararı bekler (aşağıda "Açık").
- **Açık:** (1) N5 bağı: teklif karesinde rota hesaplanamadığından önerilen yol — bırakış çözülünce `RangeInputs` önbellekte hazırlanır, bir sonraki kare/yenilemede v2 girdisine girer; rozet metni "Batarya yetmeyebilir · eve ~<km> km" (SORU-T1-1 (a)); (2) sol sütunda `metric-avg` rota kaydından (A6.9); (3) `metric-surface` Y13 ile sonra; (4) ayrıntı penceresinde fotoğraf; (5) cihazda hiçbir şey denenmedi (barometre, bildirim, konum izni, Open-Meteo ağı).

<!-- END sec:10 -->

<!-- BEGIN sec:11 -->
## 11. Y11 Trafik ışığı duruşları ve YolGo gün motoru (A6.8)

### 11.1 Kaynak ve kapsam

| YG dosya | Satır | İçerik | WEEM |
|---|---|---|---|
| `lib/tracking/shift_engine.dart` | 13–37 · 39–73 · 75–121 · 125–196 · 198–237 · 238–367 | sabitler, `stoppedSince` · `startDay`/`endDay` · `startOrder`/`cancelOrder`/`setDestination` · `feed` (fix süzgeci, duruş adayı) ve `_odometer` · `_endStop` (duruş türü) · aşama geçişleri (`_arrive`, `_depart`, `advanceFromFeed`, `stageFromFeed`, `deliverFromFeed`, `_pickedUp`, `beginCustomerJourney`, `phaseRank`) | `logic/map` `day.ShiftEngine` |
| aynı | 372–421 · 423–430 · 432–507 · 509–578 · 580–680 | `Fix`, `StopSurface`, `RoadInfo` (`atLight`, `allowsTrafficLight`) · `ShiftEvent`, `OrderPhase` · `StopRec`, `lightReviewTeaches` · `OrderRec` · `ShiftState` (`dayId`, `toJson`/`fromJson`), `_Cand` | `day.DayFix`, `day.RoadInfo`, `day.StopRec`, `day.OrderRec`, `day.DayState` (ad `:core` `ShiftState` ile çakışmasın diye; JSON biçimi aynı) |
| `lib/tracking/shift_controller.dart` | 37–137 · 153–293 · 297–352 · 356–443 · 452–533 · 545–568 · 571–593 · 596–656 · 658–722 · 727–814 | `ShiftView`, kurulum, tıklar · komutlar (`markLight`, `forgetDay`, `startDay`, `endDay`, `setDestination`, `markSidewalk`, eve dönüş ölçümü, `reviewLight`) · gün raporu (`writeReport`, `summarize`) · fix akışı, Rawstats vardiya eşitlemesi, ev kuralı · feed v2 okuma düzeni · `_reconcileStops` · `history` · `_roadInfo` · `_apply`/`_notify` (günlük, toast) · `LightsDb` | `day.DayController` (saf), `day.RoadInfoBuilder`, `day.LightsDb`; Android `:map` `DayRuntime`; rapor/geçmiş Bölüm 12 |
| `lib/tracking/light_tracker.dart` | 8–88 | canlı ışık ölçer `LightTracker` | `day.LightTracker` |
| `lib/tracking/stop_semantics.dart` | 5–34 | `mallRadiusM`, `classifySurface`, `roadHalfWidthM`, `isRoadside` | `day.StopSemantics` |
| `lib/core/day_scope.dart` | 1–9 | `bakuTime`, `bakuDay` (UTC+4 sabit), `passiveSessionExpired` | `:core` `LogTime.day(ms, Clock.zone())`; pasif oturum Bölüm 6 (`nav.PassiveSession`) |
| `lib/economics/courier_stages.dart` | 7–24 · 26–149 · 172–350 | aşama adları ve `phaseOfStage` · feed v2 ve `stages` okuyucusu (`CourierFeedV2`, `CourierTimeline`) · `CourierStageFollower` | `day.CourierStageFollower` + `day.StageFeed` (`OrderEvents.stages` → izleyici girdisi); feed JSON okuyucusu **çevrilmez** |
| `android_overlay/MotionReceiver.kt` | 9–20 | etkinlik tanıma → `TelemetryService.walkConfidence`, `activityAt` | **sonra (Y13)**: tek tüketicisi `lib/surface/motion_gate.dart:76-120` |
| yalnız başvuru: `lib/tracking/home_day.dart`, `rawstats_shift.dart`; `lib/economics/offer_feed.dart:64-80`, `rawstats_signals.dart:84-108`; `android_overlay/NotifyPolicy.kt` | — | YolGo ev kuralı, Rawstats vardiya eşitlemesi, feed okuma, duyuru kapısı (`ReadGate`, 300 ms), bildirim kuralları | `:core` (CORE.md 1.6–1.11, 3): yazılmaz ya da orada; 11.2 |

Kapsam dışı (başkalarında): uzun basış menüsündeki ışık/kaldırım satırları ve haritadaki ışık işaretleri (6.7, 6.5 `displayPoisProvider`), "Eve dön" düğmesi ve `goHome` (6.12, 7.3), `_bindShiftTarget`/`_restaurantTarget` çağrısı (5, 7.3; burada yalnız motor tarafı), gün kartı ve İstatistik ekranı içerikleri, gün raporu dosyası, sürüş günlüğü biçimi, gün silme (Bölüm 12), görev sinyalleri `activeDay`/`activeOrder`/`deliveryFinished` (Bölüm 13), yol kalitesi öğrenmenin `excludeStops` aşama eşlemesi (9; aşamalar 11.6).

Bulgu doğrulaması (`Y_tracking.md` §G1, §Y11, §G4): eşikler, tür sırası, `RoadInfo.atLight` formülü, `classifySurface`/`isRoadside` sayıları, `LightTracker` kuralları, `LightsDb` biçimi, `lightReviewTeaches`, çift kayıt önleme ve toast metinleri `870ab6e`'de doğru. **(bulgu düzeltmeleri:** (1) `MotionReceiver` G1/Y11'e girdi değildir — `walkConfidence`/`activityAt` yalnız `TelemetryService` kanıt satırına yazılır ve tek okuyucusu Y13'ün `MotionGate`'idir (`motion_gate.dart:76-77`); ışık ve gün motoru yürüme bilgisi kullanmaz. (2) `LightsDb` `nearest` yalnız `yes ≠ no` olan noktaları tarar (`spots`, `:745-756`), `lookup` 25 m `_near` ile bütün noktaları (`:765-783`). (3) `endDay` sırasında ölçer kaydı motorun `_endStop`'undan **önce** eklenir (`:213-219`). (4) `light_answer` olayı gün kapandıktan sonra verilen cevapta **yazılmaz**: `RideLogger._write` gün kimliği yokken satırı atar (`ride_logger.dart:131-132`); başka bir gün açıksa o günün dosyasına ve kimliğiyle yazılır (11.18). (5) `stopHomeReturn` (`:254-257`), `ShiftView.stoppedSince` ve `lightsLearned` getter'ı arayüzde hiçbir yerden kullanılmıyor (ölü yol).**)**

### 11.2 Gün = WEEM vardiyası

- **YolGo:** gün kendi motorundadır (`ShiftEngine.s.active`); üç yoldan açılır/kapanır: YolGo ev kuralı (`home_departure`/`home_arrival`, `shift_controller.dart:417-443`), Rawstats vardiyası eşitlemesi (`rawstats_shift_start`/`rawstats_shift_end`, `:383-413`, 60 sn yedek `:72, :184`), "Günü bitir" (`manual`). `startDay(reason, at)`: `_ending` sürerken yok; `at` gelecekteyse şimdi; olay varsa `HomeDayRules.dayStarted()`, `CourierStageFollower.dayChanged()`, `rawstatsHomeProvider.refreshIfStale()` (`:191-202`). `endDay(reason, at)` tek `Future`'da birleşir (`_ending ??=`, `:206-207`); `at` [gün başı, şimdi] aralığına sıkıştırılır (`:211`).
- **WEEM:** gün = `:core` vardiyası (G1, CORE.md 0, 1.11). `:map` gün motoru açılış/kapanış kararı **vermez**; `ShiftPort.state`'i `ShiftFollower` (`logic/core` `ShiftState.kt`; `rawstats_shift_end_test` uyarlaması, A2.2) ile izler: `follow(state, dayActive, dayStartedAtMs)` → `START_DAY` (`atMs = startedAtMs`) → `ShiftEngine.startDay(at, reason)`; `END_DAY` (`atMs = endedAtMs`) → `endDay(at, reason = state.endReason ?: "manual")`; `NONE` → hiçbir şey. Çağrı: her `ShiftPort.state` değişiminde ve 5 sn tıkta. İşlenmiş damgalar `map` tercih dosyasında `day.shift.handled.v1` = `{"end": ms?, "start": ms?}` (yeni ad; eski `rawstats.shiftEnd/Start.handled.v1` girmez, Bölüm 14). `at` sıkıştırma kuralı aynen (`at > şimdi` → şimdi; `at < gün başı` → gün başı).
- **Gün kimliği** `dayId = "yolgo-<startedAt ms>"` (`shift_engine.dart:602`); WEEM'de `startedAt = ShiftState.startedAtMs` → kimlik vardiyanınkiyle aynı ms (Bölüm 9 kova anahtarı `yolgo-<startedAtMs>` ile tutarlı; yeniden açılış aynı kimliği verir). Gün gece yarısı bölünmez; takvim günü `bakuDay(start)` → `LogTime.day(startedAtMs, clock.zone())` (Azerbaycan'da aynı sonuç; CORE.md 1.10).
- **Nedenler:** `startReason` ∈ `manual` · `home_departure` (WEEM'de `rawstats_shift_start` üretilmez); `endReason` ∈ `manual` · `home_arrival` (`ShiftMachine.stop`, `ShiftMachine.kt:182-195`). `ShiftState` v1'de başlangıç nedeni **yok** (yalnız `endReason`) → **SORU-11-1**. Eski (içe aktarılan) gün raporlarındaki `rawstats_shift_*` nedenleri okunur ve gösterilir (Bölüm 12 `dayReasons`).
- **Yazılmayanlar:** YolGo `HomeDayRules` ve `day.home.v1` (CORE.md 1.9), Rawstats vardiya okuması (`readShiftRaw`, `decideRawstatsShift`), 60 sn yedek, `AppLifecycleListener` resume okuması, `refreshIfStale` (ev `:core`'da tek kayıt), `autoStartDay`/`autoEndDay`/`followCourierStages` ayarları (1.10.0'dan beri hep `true`, `settings.dart:311-314`), toast'lar "Rawstats vardiyası bitti — gün de bitti" / "Rawstats növbəsi bitdi — gün də bitdi" ve "Rawstats vardiyası başladı — gün de başladı" / "Rawstats növbəsi başladı — gün də başladı" (`:399-404`).
- **Toast (gün):** `day_start` → "Gün başladı — km, süre ve durmalar kaydediliyor" / "Gün başladı — km, vaxt və işıqforlar qeyd olunur" (yeşil, `:689-690`). YolGo'da evden çıkışta hemen ardından "Evden çıkıldı — gün otomatik başladı" / "Evdən çıxdın — gün avtomatik başladı" önceki toast'ın yerine geçer (tek toast, 7.11), eve dönüşte "Eve döndün — gün otomatik bitti" / "Evə qayıtdın — gün avtomatik bitdi"; elle bitişte toast yok (İstatistik açılır, 6.12). **WEEM:** aynı metinler nedenden seçilir (`home_departure` → "Evden çıkıldı…", diğer başlangıç → "Gün başladı…", `home_arrival` bitişi → "Eve döndün…"); yalnız uygulama içi şerit, sistem bildirimi değil (G4; `:core`'un kendi vardiya bildirimleri ayrı, CORE.md 3).
- **Tek vardiya güvencesi:** gün yalnız vardiyayı izlediğinden "elle bitirilen gün aynı vardiyada yeniden açılmaz" kuralı (`ShiftFollower`: gün açıkken görülen başlangıç işlendi) ve "kapanmış vardiyaya geç gelen başlatma günü açmaz" (`native_telemetry_test` ilkesi, CORE.md 6.2) korunur.

### 11.3 Motor: fix süzgeci ve km sayacı (`feed`, `_odometer`)

- `feed(f, road)` (`:126-157`): gün kapalı, doğruluk sonlu değil / < 0 / > 50 m, koordinat sonlu değil ya da |lat| > 90 / |lon| > 180, ya da zaman son kabul edilenden ileri değil → olay yok (`_lastFeedAt` yalnız kabulde ilerler). `kmh = speedMps < 0 ? 0 : speedMps × 3,6` — **bilinmeyen hız duruş sayacında 0 km/s sayılır**.
- `_odometer(f, kmh)` (`:161-196`), sırayla: doğruluk > 35 m, < 0, sonlu değil ya da koordinat geçersiz → atla (hareket süresi de). Zaman `_prevT`'den ileri değil → atla. `_prevT = t`. Önceki varsa ve `kmh > 5`: `dt = t − prevT` (sn, ms/1000), `0 < dt ≤ 15` ise `movingS += dt`. Çapa (`_odo`) yoksa çapa = fix. `d = haversine(çapa, fix)`, `dt = t − çapa.t`; `dt ≤ 0` → dur; `dt > 30` ya da `d/dt > 16 m/sn` → çapa = fix (sıçrama/kesinti düz çizgi sayılmaz); hız biliniyor (`speedMps ≥ 0`) ve `kmh < 2,5` → çapa = fix; `d ≥ 3` ve `d ≥ 0,35 × doğruluk` → `distM += d`, süren siparişin `distM += d`, çapa = fix; değilse çapa kalır (yavaş hareket birikir). **Bilinmeyen hız km sayacında hareketli sayılır** (çapa taşınmaz).
- Uzaklık: `haversine` (`lib/core/geo.dart:3-10`, R = 6 371 000 m) → WEEM `geo` paketindeki aynı fonksiyon (Bölüm 3).
- `movingS` ve `distM` JSON'da yuvarlanır (`'m': distM.round()`, `'movingS': movingS.round()`, `:628-629`); motor içinde Double.
- **Bu km = YolGo gün km'si** (Y17 istatistik, gün kartı); vardiya sonu `shift_end.km` (Rawstats D8, `:core` `ShiftDistance`) ayrı ölçümdür (CORE.md 0 "Mesafe"; ikisinin gösterimi **SORU-12-1**).

### 11.4 Motor: duruş tespiti ve tür sırası

- Sabitler (`:18-24`): `stopMinS = 10`, `stopRadiusM = 30`, `stopKmh = 5`, `departKmh = 10`, `lightMaxS = 180` (ışık sayılan en uzun duruş `lightMaxS × 2 = 360` sn; yorum "3 dk" der, kod 6 dk — davranış koddur), `lightLongS = 120`.
- Aday (`feed`, `:137-155`): `_fast = kmh > 10 ? _fast + 1 : 0`. Aday yoksa ve `kmh ≤ 5` → aday `(t, lat, lon)`. Aday varken `d = haversine(aday, fix)`; **ayrılış** = `_fast ≥ 2` ya da (`d > 30` ve doğruluk ≤ 25): `_endStop(aday)`; yeni aday yalnız bu fix `kmh ≤ 5` ise. Ayrılış değilse: `kmh ≤ 5` ise `aday.last = t` (5–10 km/s arası fix duruşu ne uzatır ne bitirir); `last − start ≥ 10 sn` ve duyurulmadıysa bir kez `stopping` olayı (`t` = fix anı).
- `stoppedSince` (`:33-37`): aday ≥ 10 sn ise başlangıcı (arayüzde kullanılmıyor; WEEM'de yazılmaz).
- `_endStop(c, road)` (`:198-237`): `dur = last − start` (tam sn); `dur < 10` → kayıt yok (yol bilgisi de sorulmaz). Tür, sırayla:
  1. aşama `atRestaurant` / `readyForCustomer` / `atCustomer` → `order`;
  2. `info.mall` → `mall`;
  3. `info.atLight` ve `dur ≤ 360` → `dur > 120` ise `light?` (+ `longWait`), değilse `light`;
  4. `info.surface == sidewalk` → `sidewalk`;
  5. diğer → `stop`.
- Kayıt `StopRec(start, end = last, lat/lon = aday noktası, kind, onRoad, junctionM, inOrder = süren sipariş var, lightLat/Lon yalnız `light*` türünde, surface, longWait = kind == "light?")` → `stops`'a eklenir, olay `stop` (veri `StopRec.toJson`, zaman `end`).
- Gün sonunda açık aday `_endStop` ile kapanır (`endDay`, `:58-60`); 10 sn'den kısa duruş, GPS sıçraması sonrası kaybolan aday ve 360 sn'yi aşan ışık beklemesi ışık sayılmaz (sonuncusu kaldırım/`stop` olur).
- **Bilinen davranış (korunur):** motor `light?` türünü yalnız bilinen ışıkta 120–360 sn duruşa verir; bilinmeyen kavşak adayı yalnız canlı ölçerden gelir (11.9).

### 11.5 Veri modeli ve `shift.v1` JSON

- `DayFix(t, lat, lon, speedMps = 0, accuracy = 5)` (YolGo `Fix`, `:372-378`; `speedMps < 0` = bilinmiyor). WEEM'de `NavLocation` fix'inden: `t` = fix zamanı (duvar saati ms), hız/doğruluk Android değerleri (hız yoksa −1).
- `StopRec` (`:432-502`) JSON: `start`, `end` (ms) · `lat`, `lon` (6 haneye yuvarlanmış sayı) · `kind` (`light` \| `light?` \| `stop` \| `order` \| `mall` \| `sidewalk`) · `onRoad` · `jm` (yuvarlak m, varsa) · `inOrder` · `answer` (`light` \| `not`, varsa) · `llat`, `llon` (6 hane, varsa) · `measured: true` (yalnız ölçer kaydında) · `surface` (`unknown` \| `road` \| `sidewalk`) · `long: true` (yalnız uzun ışık). Okuma: `onRoad`/`inOrder` yoksa false, `measured == true`, `long == true`, bilinmeyen `surface` → `unknown`. `durS = end − start` (tam sn); `isLight = answer == "light" || (answer == null && kind == "light")`.
- `OrderRec` (`:509-578`) JSON: `n` · `start` · `restArrived`, `restLeft`, `custArrived`, `delivered`, `ended` (ms, varsa) · `restLat`, `restLon`, `custLat`, `custLon` (varsa, yuvarlanmaz) · `restAuto`, `custAuto`, `cancelled` · `m` (yuvarlak) · `restTarget`, `customerTarget` (`{lat, lon}`, varsa) · `restName` (boş değilse) · `pickupFlow: 2` (hep) · `customerDeparted` (varsa). Okuma: `customerDeparted` yoksa ve `pickupFlow` da yoksa (eski kayıt) `restLeft` kullanılır. `phase`: `custArrived` → `atCustomer`; `restLeft` → `customerDeparted` yoksa `readyForCustomer`, varsa `toCustomer`; `restArrived` → `atRestaurant`; diğer `toRestaurant`. Süreler (sn, eksikse null): `toRestS`, `restWaitS = restLeft − restArrived`, `toCustS`, `custWaitS = delivered − custArrived`, `totalS = (delivered ?? ended) − start`.
- `DayState` (YolGo `ShiftState`, `:580-670`) JSON: `v: 2` · `dayId` (`yolgo-<start>`; okunmaz, türetilir) · `active` · `start`, `end` (ms, varsa) · `m`, `movingS` (yuvarlak) · `stops[]`, `orders[]`, `queuedOrders[]` · `current` (varsa) · `phase` (ad; bilinmeyen → `none`) · `homeReturnAt` (varsa) · `homeStartM`, `homeDistM` (Double) · `homeTimeS` · `startReason`, `endReason` (varsa). Bilinmeyen anahtarlar okunmaz ve yazılmaz (eski `question`, `pickupGroup`, `secondOrderWindowUsed`; `yolgo_sade_2` motor testi 4). Türetilenler: `lights` (`isLight`), `lightCandidates` (`kind == "light?" && answer == null`), `delivered` (teslim sayısı).
- `queuedOrders` 1.10.0 öncesi ikinci sipariştir; motor artık üretmez (yalnız `_activateNext` okur). WEEM: alan modelde ve JSON'da kalır (içe aktarılan eski gün raporları, Bölüm 12), WEEM hiç doldurmaz.
- Kalıcılık: aktif gün `shift.v1` (her olayda, `save` isteğinde ya da son kayıttan ≥ 15 sn sonra; gün kapalıysa silinir, `shift_controller.dart:671-678`); biten son gün `shift.last` (`:224-226`). Açılışta `shift.v1` okunur, aktif değilse yeni motor (`:107-109`); motorun iç durumu (aday, km çapası, `_fast`, son fix zamanı) kalıcı değildir — süreç yeniden başlayınca ilk fix'ten yeniden kurulur. **WEEM:** `map` tercih dosyasında aynı adlar ve biçim (S1: YolGo'dan aktarılmaz, boş başlar); yazım `:map` Android katmanında (`DayRuntime`), karar `logic/map`'te.

### 11.6 Sipariş aşamaları (motor geçişleri)

- `OrderPhase` = `none`, `toRestaurant`, `atRestaurant`, `readyForCustomer`, `toCustomer`, `atCustomer`; `phaseRank` −1, 0, 1, 2, 3, 4 (`:352-359`).
- `startOrder(now)` (`:78-83`): gün açık ve süren sipariş yoksa `OrderRec(n = max(0, bitenler, süren, sıradakiler) + 1, startedAt = now)`, aşama `toRestaurant`; olay `order_start {n}`.
- `cancelOrder(now)` (`:85-93`): süren → `cancelled = true`, `endedAt = now`, bitenlere; `_activateNext()` (sıradaki ya da yok; aşama onun `phase`'i ya da `none`; `_fast = 0`); olay `order_cancel {n}`.
- `setDestination(point, now, restaurant, name)` (`:108-121`): süren yoksa hiçbir şey (sipariş **açmaz**). Restoran hedefi: restorandan çıkılmışsa yazılmaz (eldeki siparişe başka restoran yok), değilse `restTarget = point`, `restName = name`. Müşteri hedefi: yalnız restorandan çıkılmışsa `customerTarget = point`. Olay `order_target {n, restaurant}`. Aşama değişmez (varış geri sarılmaz, müşteri adresi müşteri bacağını başlatmaz).
- `_arrive(at, lat, lon)` (`:239-260`): `toRestaurant` → `restArrivedAt`, `restLat/Lon`, `restAuto = true`, `atRestaurant`, olay `arrive_restaurant {n, auto: true}`; `toCustomer` → `custArrivedAt`, `custLat/Lon`, `custAuto = true`, `atCustomer`, `arrive_customer {n, auto: true}`.
- `_depart(at)` (`:262-278`): `atRestaurant` → `restLeftAt`, `readyForCustomer`, `leave_restaurant {n, waitS}`; `atCustomer` → `deliveredAt = endedAt = at`, bitenlere, `_activateNext`, `delivered {n, totalS, m}`.
- `beginCustomerJourney(at)` (`:361-367`): `readyForCustomer` → `customerDepartedAt ??= at`, `toCustomer`, `customer_journey {n}`.
- `advanceFromFeed(target, now, lat, lon, deliver)` (`:283-300`; saatsiz eski kural): en çok 6 adım, sipariş aynı kaldıkça; `deliver` değilse `rank(aşama) ≥ rank(target)` olunca durur; adım aşamaya göre `_arrive(now)` / `_depart(now)` / `beginCustomerJourney(now)`; bütün adımlar `now` saatiyle.
- `stageFromFeed(target, at, lat, lon)` (`:307-322`; E8 saatli): süren yok ya da `rank(aşama) ≥ rank(target)` → yok. `atRestaurant` → `_arrive(at)`. `toCustomer`/`atCustomer` → aşama `toRestaurant` ise `_pickedUp(at)` (`restLeftAt = at`, `readyForCustomer`, `leave_restaurant {n, waitS: null}` — varış uydurulmaz, D17), `atRestaurant` ise `_depart(at)`, `readyForCustomer` ise `beginCustomerJourney(at)`, hedef `atCustomer` ve aşama `toCustomer` ise `_arrive(at)`.
- `deliverFromFeed(at)` (`:326-335`): `atCustomer` → `_depart(at)`; diğer → `deliveredAt = endedAt = at` (`custArrivedAt` null kalır), bitenlere, `_activateNext`, `delivered {n, totalS, m}`.
- Feed'den gelen her olayın verisine `source: "kuryeplus"` eklenir (`_fromFeed`, `:302, :346-349`).
- `endDay(now, road, reason)` (`:55-73`): açık aday `_endStop`; süren ve sıradaki siparişler `cancelled = true`, `endedAt = now`, bitenlere; aşama `none`; `active = false`, `endedAt`, `endReason`; olay `day_end {reason}`. `startDay(now, reason)` (`:42-51`): açıksa yok; yeni durum `(active, startedAt, startReason)`, iç sayaçlar sıfır; `day_start {reason}`.
- GPS duruşu aşama değiştirmez, "geldin mi?" sorusu (`ask`), elle aşama düğmesi ve ikinci sipariş yok (Sadeleştirme 2, `:6-12`).
- **WEEM aşama eşlemesi:** `TripStages.Stage` `TO_PICKUP` → `toRestaurant`, `AT_PICKUP` → `atRestaurant`, `TO_DROPOFF` → `toCustomer`, `AT_DROPOFF` → `atCustomer` (`courier_stages.dart:19-24`). `readyForCustomer` feed'den gelen geçişte aynı çağrıda `toCustomer`'a ilerler; durağan hâli yalnız eski kayıtta. Duruş türü `order` (11.4) ve Y12 `excludeStops` (Bölüm 9) aynı üçlüyü kullanır: `atRestaurant`, `readyForCustomer`, `atCustomer`.

### 11.7 Aşama izleyici (`CourierStageFollower`) ve `OrderEvents.stages`

- Durum (`:180-188`): bağlı yolculuk `_trip`, YolGo sipariş no `_order`, son uygulanan aşama `_stage`, eksik okuma `_missing`, son uygulanan saat `_at`, kapalı yolculuklar `_closed` (en çok `keepClosed = 20`), bırakılan siparişler `_released`. `missingReads = 2`, `freshStage = 30 sn`.
- `sync(e, feed, now, lat, lon)` (`:196-260`):
  1. Okuma yok ya da gün kapalı → hiçbir şey.
  2. Bağlı sipariş artık açık değilse (elle/gün sonuyla kapandı) bağ kapanır (`_close`: yolculuk `_closed`'a; aynı yolculuk bir daha sipariş açmaz).
  3. **Bağ varken**, yolculuk açıklar listesinde **yoksa**: `stages`'te bitmiş ve sonucu belli satırı varsa ve süren sipariş bağlıysa → `_ended`, bağ kapanır. Yoksa `_missing` artar (en çok 2); 2'den azsa ya da süren sipariş başkasıysa bekler; 2. eksik okumada: son aşama `to_dropoff`/`at_dropoff` → `advanceFromFeed(atCustomer, now, deliver: true)` (teslim); aşama `toRestaurant`/`atRestaurant` → `cancelOrder(now)`; diğer (alınmış) → sipariş `_released` (elle durum kalır), olay yok; bağ kapanır.
  4. **Bağ varken** yolculuk listedeyse: `_missing = 0`; süren sipariş başkasıysa yok; satır saatliyse (açık ve olayı var) `_timeline`; değilse aşama yok ya da `_stage` ile aynıysa yok, değilse `advanceFromFeed(phaseOfStage)` (yalnız ileri; aynı aşama tekrarında hiçbir şey).
  5. **Bağ yoksa:** açıklar listesinde aşaması bilinen ve kapalı olmayan **ilk** yolculuk; süren sipariş yoksa `startOrder(start)` (saatliyse ilk olayın saati [gün başı, şimdi] aralığında, değilse şimdi); süren sipariş `_released`'taysa yok. Bağ kurulur; saatliyse `_stage = null` + `_timeline`, değilse `_stage = aşama` + `advanceFromFeed`.
- `_timeline` (`:270-285`): her olay sırayla; `olay.index ≤ _stage.index` atlanır (geri yön, ikinci restoranın `to_pickup`/`at_pickup`'ı); `at = clamp(atMs, _at ?? sipariş başı, now)`; konum yalnız `now − at ≤ 30 sn` ise yazılır; `stageFromFeed(phaseOfStage(olay), at)`; `_stage`, `_at` güncellenir; sipariş değiştiyse durur. `_clamp(atMs, floor, now) = atMs.clamp(min(floor, now), now)` (`:264-267`).
- `_ended` (`:288-303`): kalan olaylar `_timeline`; sipariş aynıysa `end = clamp(endedAtMs ?? now, _at ?? başlangıç, now)`; `cancelled` → aşama `toRestaurant`/`atRestaurant` ise `cancelOrder(end)`, değilse `_released`; `delivered`, `delivered_unverified`, `unconfirmed` → `deliverFromFeed(end)`.
- `dayChanged()` (`:306-309`): bağ kapanır, `_released` temizlenir (gün başı ve sonu). `awaitingSecondRead` = bağ var ve `0 < _missing < 2` (`:194`).
- Kalıcı durum `courier.follow.v1` (`:322-349`): `{"v": 1, "trip"?, "order"?, "stage"? (to_pickup…at_dropoff), "at"?, "missing"? (> 0), "closed": [...], "released"? (sıralı)}`; geri yüklemede `trip` ya da `order` yoksa bağ alanları sıfırlanır; adres ve müşteri bilgisi yok. Değişince yazılır (`_saveRules`, `shift_controller.dart:536-543`).
- YolGo okuma düzeni (`shift_controller.dart:452-533`): gün açıkken 5 sn tıkta ve Rawstats duyurusunda (≥ 300 ms aralık, `ReadGate`); okuma 5 sn zaman aşımı; yedek aralık okuma yoksa 30 sn, duyuru dinlenebiliyorsa 60 sn, değilse 4 sn; eksik okumadan sonra 2 sn (`feedRecheck`); `order_start` olursa eve dönüş ölçümü biter (`:478-479`).
- **WEEM (`day.StageFeed`):** girdi `OrderEvents.stages: StateFlow<TripStages>` (`:orders` → `:map`, plan 4.3; feed v2 JSON okunmaz). Uyarlama: açıklar = `trips.filter { active }` → `(tripId, platformPackage, stage)` (sıra korunur: RS `TripStages.feed` açıkları `acceptedAt` sırasıyla verir, `courierState.activeTrips` ile aynı, `TripStages.kt:66-76`, `EconomicsLogs.kt:166-168`); zaman çizelgesi = bütün `trips` kimliğe göre (`active`, `stage`, `outcome`, `endedAtMs`, `atMs > 0` olaylar); aynı kimlik ikinci kez gelirse ilki geçer (YolGo `continue`/`putIfAbsent`). Bilinmeyen aşama `TripStages.Stage.of` → null (yolculuk var, aşama izlenmez). Çağrı: her `stages` değişiminde, 5 sn tıkta ve `awaitingSecondRead` iken 2 sn sonra bir kez. `ReadGate`, zaman aşımı ve yedek aralıklar **çevrilmez** (aynı süreç); `changedAtMs`/`generatedAtMs` kullanılmaz. `lat/lon` = `NavLocation` son fix'i. Olay verisindeki `source: "kuryeplus"` korunur (günlük biçimi; Bölüm 12).

### 11.8 Işık kuralı ve yol bilgisi (`RoadInfo`, `stop_semantics`, `_roadInfo`)

- `RoadInfo` varsayılanları (`:384-386`): `onRoad = false`, `junctionM = null`, `known = null` (true: bilinen ışık; false: ışık değil; null: bilinmiyor), `lightLat/Lon = null`, `surface = unknown`, `lightM = null`, `maxLightM = 5`, `mall = false`, `roadside = false`, `manualSidewalk = false`, `footM = null`.
- `allowsTrafficLight = onRoad && surface != sidewalk && !mall && !roadside` (`:412`).
- **Işık kuralı (madde 14 / F9.2, `:418-420`):** `atLight = !mall && !manualSidewalk && known == true && (lightM != null ? lightM ≤ maxLightM && (onRoad || (footM ?? ∞) ≤ 1) : surface != sidewalk && !roadside)`. Uzaklık ölçülmediyse eski kural (kaldırım ve yol kenarı ışık değil); ölçüldüyse yol kenarı (arabaların yanı) yoldadır.
- `classifySurface(accuracy, roadM, footM, manualSidewalk)` (`stop_semantics.dart:8-18`): elle → `sidewalk`; doğruluk sonlu değil / < 0 / > 12 → `unknown`; `footM ≤ 8` ve (`roadM` yok ya da `footM + 3 < roadM`) → `sidewalk`; `roadM ≤ 6`, doğruluk ≤ 8 ve (`footM` yok ya da `roadM + 3 < footM`) → `road`; diğer `unknown`. Yakındaki ışık bu karara girmez.
- `roadHalfWidthM` (`:21-27`): trunk 9, primary 7, secondary 6, tertiary 5, diğer 4 m. `isRoadside(accuracy, roadM, cls)` (`:31-34`): `roadM`/`cls` yok ya da doğruluk sonlu değil / < 0 / > 8 → false; değilse `roadM > yarım genişlik`. `mallRadiusM = 80` (`:5`).
- **Yol bilgisi** (`_roadInfo(lat, lon)`, `shift_controller.dart:596-656`; WEEM `day.RoadInfoBuilder`, saf):
  1. `sig` = **düzeltilmemiş** harita POI'lerinde (`mapData.pois`, Bölüm 5) 55 m içindeki en yakın ışık (`l`).
  2. `known = LightsDb.lookup(lat, lon)` (25 m); `local = LightsDb.nearest(lat, lon, 55)` (yalnız `yes ≠ no` noktalar).
  3. `local` "ışık var" ise `known = true`, ışık yeri `local`; değilse `sig` var ve `known != false` ise `known = (lookup(sig) != false)` (OSM ışığı 25 m içinde "ışık yok" diye öğrenilmemişse ışık), yer `sig`; değilse `known == true` ise yer = 25 m içindeki öğrenilmiş nokta ya da duruş noktası.
  4. `lightM` = duruş ↔ ışık yeri haversine (yer yoksa null); `maxLightM = settings.lightMaxM`.
  5. `mall` = 80 m içinde AVM (`m`) POI'si; `manual` = 12 m içinde kaldırım işareti.
  6. Yol grafiği yoksa: `RoadInfo(onRoad = !manual, known, ışık yeri, surface = manual ? sidewalk : unknown, lightM, maxLightM, mall, manualSidewalk = manual)`.
  7. Graf varsa: `hit` = 35 m içinde en yakın sürülebilir kenar (yaya sınıfı ve `steps` değil), `foot` = 35 m içinde en yakın yaya kenarı (`isFootClass`, Bölüm 2); doğruluk = **son kabul edilen fix'in** doğruluğu (`_latestFix`; duruşun kendi fix'i değil; yoksa ∞ → `unknown`); `surface = classifySurface(doğruluk, hit.dist, foot.dist, manual)`; `roadside = hit != null && isRoadside(doğruluk, hit.dist, sınıf)`; kavşak `jm`: hit varsa duruşun ±80 m kutusundaki (`80/111320`°, boylamda ×1,4) kenar uçlarından derecesi ≥ 3 olup yerel izdüşümde |dx|, |dy| ≤ 80 m olanların en yakını (m), yoksa null; `onRoad = hit != null && hit.dist ≤ 15 && surface != sidewalk`; `footM = foot?.dist`.
- Belirsiz GPS (doğruluk > 12) yüzeyi `unknown` yapar ama `onRoad` yol çizgisine ≤ 15 m ile kalır: belirsiz yüzey tek başına ışığı silmez (`:651-652`; `yolgo_rules_test` 3, 7.15).
- **WEEM:** `RoadInfoBuilder(pois, graph?, lights, sidewalks, lightMaxM, lastAccuracy)` — POI dizini ve yol grafiği Bölüm 2/5'in `logic/map` tipleri (`nearest`, `nearestEdge`, `edgesWithin`, `degree`, `classOf`); harita verisi yoksa 6. adım.

### 11.9 Canlı ışık ölçer (`LightTracker`, `light_tracker.dart:8-88`)

Sipariş/varış motorundan bağımsız; motorun 10 sn eşiğinin altındaki ışık beklemelerini de ölçer. "Hareketin ilk fix'i bitiştir, ikinci fix yalnız hareketi doğrular; GPS kesintileri beklemeye eklenmez" (`:6-7`).

- Durum: başlangıç fix'i `start`, son kabul edilen fix `last`, ilk hareket fix'i `firstMoving`, yol bilgisi `info` (başta `RoadInfo()`), son yol bilgisi zamanı `checked`, ardışık hareket sayısı `moving`.
- `feed(f, road, orderStop, inOrder)` (`:51-87`), sırayla:
  1. `orderStop` (aşama `atRestaurant` / `readyForCustomer` / `atCustomer`) → `reset()`, kayıt yok (sipariş duruşunda ışık ölçülmez).
  2. `last` var ve `f.t ≤ last.t` → yok (sıra dışı fix).
  3. Doğruluk > 35 m, < 0 ya da sonlu değil → `last` var ve `f.t − last.t > 12 sn` ise `reset()`; yok. `last` ilerlemez: tek tük kötü fix beklemeyi bozmaz, 12 sn'yi aşan kötü fix dizisi sıfırlar.
  4. `last` var ve `f.t − last.t > 12 sn` → `reset()` (kesinti).
  5. `last = f`.
  6. Başlangıç yoksa: `0 ≤ speedMps ≤ 1,1` ise `start = f`, `info = road(f)`, `checked = f.t`; yok (hızı bilinmeyen fix ölçüm başlatmaz).
  7. `checked` yok ya da `f.t − checked ≥ 5 sn` → `info = road(start)`, `checked = f.t` (yol bilgisi **başlangıç noktası** için 5 sn'de bir tazelenir: bekleme sırasında öğrenilen ışık ya da elle işaret etkiler).
  8. `movedAway = haversine(start, f) > max(35, 2 × doğruluk)`; `speedMps ≥ 1,6` ya da `movedAway` → `firstMoving ??= f`, `moving++`; değilse `moving = 0`, `firstMoving = null`.
  9. `moving < 2` → yok; değilse `record(firstMoving.t)`, `reset()`, kayıt döner.
- `record(end, inOrder)` (`:34-49`): `s = end − start` (tam sn); `known = info.atLight` (11.8); `candidate = info.known == null && info.allowsTrafficLight && (info.junctionM ?? ∞) ≤ 45`; `long = known && s > 120` (`lightLongS`). Koşul `s ≤ 360 && ((known && s ≥ 3) || (candidate && s ≥ 10))` → `StopRec(start = start.t, end, lat/lon = start, kind = known && !long ? "light" : "light?", onRoad, junctionM, inOrder, lightLat/Lon = info'nunki, measured = true, surface, longWait = long)`; değilse yok. Öğrenilmiş "ışık yok" yeri (`known == false`) ne ışık ne aday olur.
- `finish(now, inOrder)` (`:26-32`): `last` yok ya da `now − last.t > 12 sn` → yok; değilse `record(firstMoving?.t ?? last.t)` (bekleme son geçerli fix'e kadar); her durumda `reset()`.
- Canlı değerler: `since` = başlangıç ve son fix var, `info.atLight` ve `last.t − start.t ≥ 3 sn` → `start.t`, değilse null (adaylar sayaç göstermez); `lightAt` = `since` varken `(info.lightLat ?? start.lat, info.lightLon ?? start.lon)`.
- Çağrılar: her kabul edilen fix'te motordan sonra (11.14); `endDay`'de motor kapanmadan önce `finish(at)` (11.1 düzeltme 3); `startDay` ve `markSidewalk`'ta `reset()`.
- Ekranda: `lightAt` haritada nabızlı ışık işareti (6.6 madde 5); `since` gün kartında "Trafik ışığında bekliyorsun" / "İşıqforda gözləyirsən" + `m:ss` sayacı (1 sn'de bir; `LightWaitChip`, `shift_widgets.dart:274-316`; turuncu, ışık simgesi); katlı kartta da görünür, değişince kart açılır (6.12).
- **WEEM:** `day.LightTracker` aynen (saat fix zamanı, ms). `DayView.lightAt`, `DayView.lightSinceMs`; sayaç satırı `ShiftCard`'da (SORU-N7; gün kartı satırları Bölüm 12), harita işareti `MapSurface` katmanında (Bölüm 6).

### 11.10 Ölçer ve motor kayıtlarının birleşmesi (`_reconcileStops`, `shift_controller.dart:545-568`)

- Aynı duruşun iki kez kaydını önler. Girdi: bu adımın motor olayları ve (varsa) ölçerin yeni kaydı (önceden `stops`'a eklenmiş).
- Her `stop` olayı için motorun ölçülmemiş kaydı `raw` (`!measured`, `start` olayınkiyle aynı ms) aranır; yoksa olay kalır. `matching` = ölçer kayıtlarından zamanı `raw` ile ±3 sn örtüşen (`s.start < raw.end + 3 sn` ve `s.end > raw.start − 3 sn`) ve noktası ≤ 40 m olanlar.
  - `raw.kind == "order"` → eşleşen ölçer kayıtları `stops`'tan silinir (sipariş duruşu ışık sayılmaz), olay kalır.
  - eşleşen varsa → `raw` silinir, olay **atılır** (ölçer kazanır: 3 sn eşik, bitiş = ilk hareket).
  - değilse olay kalır.
- Ölçer kaydı hâlâ `stops`'taysa `stop` olayı eklenir (zaman `end`, veri `toJson`).
- Sıra: fix'te motor olayları (+ `home_return_arrived`) → ölçer kaydı `stops`'a → birleştirme → `_apply`; gün sonunda ölçer `finish` → `stops`'a → motor `endDay` → birleştirme.
- Ölçer çoğunlukla motordan önce biter (iki fix ≥ 1,6 m/sn ≈ 5,8 km/s; motor iki fix > 10 km/s). Motor 30 m kaymayla (tek fix, doğruluk ≤ 25) önce biterse ölçer kaydı sonraki adımda gelir ve ikisi de kalır — YolGo davranışı, korunur (11.18).
- **WEEM:** `day.StopReconciler.reconcile(state, events, measured): List<DayEvent>` (saf; `DayState.stops`'u değiştirir).

### 11.11 Gün sonu ışık cevabı (`reviewLight`) ve `LightsDb`

- **Soru listesi:** `DayState.lightCandidates` = `kind == "light?"` ve cevapsız duruşlar (ölçer adayları ve 2 dk'yı aşan bilinen ışık beklemeleri). Gösterim ve "Onayla" / "Reddet" düğmeleri İstatistik ekranında, gün gün (Bölüm 12).
- `reviewLight(day, stop, isLight)` (`:269-293`): gün silinmişse (`shift.deleted.days.v2` ∋ `dayId`) hiçbir şey. `stop.answer = isLight ? "light" : "not"`. Yer = ışık pini (`llat/llon`), yoksa duruş noktası. `teach = lightReviewTeaches(stop, isLight)` (`shift_engine.dart:504-507`: `longWait && !isLight` → null, değilse `isLight` — uzun beklemede "Reddet" bilinen ışığı silmez, "Onayla" evet öğretir; adayda ikisi de öğretir). `teach` null değilse `LightsDb.record(yer, isLight = teach, waitS = durS)`, ardından `setManual(yer, teach)`; `lightsRev++` (harita katmanı). Olay `light_answer {la, lo (duruş noktası, 5 hane), light, dur, jm? (yuvarlak), long?: true}` (sürüş günlüğü; 11.1 düzeltme 4). Gün `lastDay` ise `shift.last` yeniden yazılır; gün raporu yeniden yazılır (Bölüm 12); `_apply(emit, save)` (gün açıksa `shift.v1`).
- Etki: duruşun `isLight`'ı (11.5) cevaba göre değişir → gün özetindeki `lights`, `lightWaitS`, `lightCandidates` (Bölüm 12); cevaplanan duruş soru listesinden çıkar.
- **`LightsDb`** (`:725-814`; tercih `lights.v1`): nokta = `(lat, lon, yes, no, waitS)`; saklama `"lat,lon,yes,no,waitS"` (lat/lon 6 hane), liste sırası korunur. Okuma: < 4 alan ya da ilk dört alandan biri sayı değil → satır atlanır; 5. alan yok ya da bozuk → `waitS = 0`.
  - `lookup(lat, lon)`: 25 m (`≤`) içindeki en yakın nokta (bütün noktalar); yok ya da `yes == no` → null; değilse `yes > no`.
  - `nearest(lat, lon, maxM = 55)`: yalnız `yes ≠ no` noktalarda en yakın (`< maxM`) → `LightSpot(lat, lon, exists = yes > no)`.
  - `spots`: `yes ≠ no` noktalar (harita katmanı ve uzun basış, 5.3, 6.6–6.7); `confirmed`: `yes > no` sayısı (gün raporu `lightsLearned`).
  - `record(lat, lon, isLight, waitS)`: 25 m içinde nokta yoksa yeni `(1, 0, waitS)` ya da `(0, 1, 0)`; varsa ışıksa `yes++` ve `waitS += waitS`, değilse `no++`.
  - `setManual(lat, lon, exists)`: 25 m içindeki en yakın nokta silinir; basılan yerde yeni `(1, 0)` ya da `(0, 1)`, `waitS` eskisinden.
  - Her değişiklikte bütün liste yazılır.
  - `reviewLight` iki çağrıyı arka arkaya yaptığından sonuç: o yerdeki nokta son cevaba göre `1/0` ya da `0/1` olur, konumu son cevabın yerine taşınır, `waitS` birikir (ışıksa bu bekleme eklenir); evet/hayır sayıları birikmez, son cevap kazanır. Davranış korunur.
- **WEEM:** `day.LightsDb` saf (girdi/çıktı dizge listesi, aynı kodek); Android'de `map` tercihine JSON dizi dizgesi (14.1). `reviewLight` → `DayController.reviewLight(dayId, stopStartMs, isLight)`: duruş gün içinde `start` ms ile bulunur (YolGo nesne kimliği kullanır); biten günün durumu gün raporundan okunur ve oraya yazılır (Bölüm 12).

### 11.12 Elle işaretler: ışık var/yok, kaldırım

- `markLight(lat, lon, exists)` (`:153-160`): `LightsDb.setManual`; `lightsRev++`; `_apply(emit, save)`; toast "Trafik ışığı kaydedildi" / "İşıqfor yadda saxlanıldı" ya da "Bu ışık haritadan gizlendi" / "Bu işıqfor xəritədən gizlədildi" (turuncu, ışık simgesi). Çağıran: uzun basış satırları 16 ("ışık var": basılan nokta) ve 17 ("ışık yok": 25 m içindeki ışığın yeri, yoksa nokta) (6.7). Gün kapalıyken de çalışır; olay yazılmaz.
- `markSidewalk(point, remove)` (`:235-244`): 12 m içindeki işaretler silinir; `remove` değilse nokta eklenir; `sidewalk.zones.v1` = `[{lat, lon}]` JSON yazılır; ölçer `reset()`; olay `stop_surface_override {surface: "sidewalk" | "unknown"}` (gün kapalıysa günlük yazmaz); `_apply(emit)`; toast "Bu nokta kaldırım beklemesi olarak kaydedilecek" / "Bu nöqtədə gözləmə səki kimi qeyd olunacaq" ya da "Kaldırım işareti kaldırıldı" / "Səki işarəsi silindi" (yürüyüş simgesi). Çağıran: uzun basış satırları 5 ve 6 (6.7).
- Etki: yol bilgisinde 12 m içinde işaret → `manualSidewalk` (11.8 madde 5): ışık değil, yüzey `sidewalk`, motor türü `sidewalk`, ölçer kaydetmez. Kaldırım işaretleri haritada çizilmez; ışık işaretleri çizilir (`lightSpots`, 6.6).
- Kalıcılık: `sidewalk.zones.v1` açılışta okunur (bozuk öğe atlanır, bozuk JSON → boş liste).
- **WEEM:** `day.SidewalkMarks` (12 m kuralı, kodek) + `DayController.markLight` / `markSidewalk`; toast metinleri `DayText`.

### 11.13 Eve dönüş ölçümü (`startHomeReturn`, `home_return_*`)

- `startHomeReturn()` (`:246-252`; "Eve dön" → `goHome`, 7.3): gün açık, süren sipariş yok ve ölçüm sürmüyorsa `homeReturnAt = şimdi`, `homeStartM = distM`, olay `home_return_start` (sürüş kaydı açılır, Bölüm 12), `save`.
- Biter: (a) eve varış — ölçüm sürerken, ev var, doğruluk ≤ 20 m, `0 ≤ hız < 1,3 m/sn` ve eve ≤ 35 m olan kabul edilmiş fix → `finishHomeReturn(fix zamanı)` + olay `home_return_arrived` (sürüş kaydı kapanır); (b) aşama izleyici yeni sipariş açtı (`order_start`) → `finishHomeReturn(şimdi)`, olay yok (`:478-479`); (c) gün sonu (`_endDay` başında, `at` ile).
- `finishHomeReturn(now)` (`:259-266`): `homeTimeS += max(0, now − homeReturnAt)` (tam sn), `homeDistM += max(0, distM − homeStartM)`, `homeReturnAt = null`. Birden çok eve dönüş birikir.
- Gün özeti `homeKm`, `homeS` süren ölçümü de katar (Bölüm 12).
- **WEEM:** ev `:core`'dan (`KuryelogPrefs` `home.*`; `:map` saklamaz); ev koordinatı hiçbir olaya ve gün raporuna yazılmaz (yalnız mesafe ve süre). `goHome` (7.3) → `DayController.startHomeReturn(nowMs)`.

### 11.14 Çalışma düzeni: fix, tık, kalıcılık, olaylar, toast (`ShiftController` → `DayController` + `DayRuntime`)

- **Kurulum** (`build`, `:99-137`): `courier.follow.v1`, `shift.deleted.days.v2`, `lights.v1`, `shift.v1` (aktif değilse yeni boş motor), `shift.last`, `sidewalk.zones.v1` okunur (bozuksa yok sayılır); sürüş kaydına gün kimliği (Bölüm 12), görevlere `activeDay` / `activeOrder` (Bölüm 13); konum, teklif duyurusu, Rawstats vardiya sinyali ve yaşam döngüsü dinlenir; gün açıksa 5 sn tık. **WEEM:** aynı tercihler ve `day.shift.handled.v1` (11.2) okunur; `ShiftPort.state` (→ `ShiftFollower`, 11.2), `OrderEvents.stages` (→ izleyici, 11.7) ve `NavLocation` fix'leri dinlenir; ev kuralı ve Rawstats okuması yok.
- **5 sn tık** (`_scopeTick`, `:177-187`; yalnız gün açıkken): `_apply(emit)` (15 sn kalıcılık kuralı, görünüm tazeleme), aşama okuması (11.7), 60 sn'de bir vardiya okuması (WEEM'de yok: vardiya `ShiftPort.state` akışından).
- **Fix** (`_onPosition`, `:356-379`): (YolGo'da önce ev kuralı — WEEM'de `:core`.) Gün kapalı → dur. Fix yaşı `şimdi − fix zamanı` −5 … +30 sn dışında → dur (motor ve ölçer bu fix'i görmez). `latestFix = fix` (yol bilgisinin doğruluğu, 11.8). `DayFix(fix zamanı, lat, lon, hız, doğruluk)` → motor `feed` (11.3–11.4) → eve varış denetimi (11.13) → ölçer `feed(orderStop, inOrder)` (11.9) → ölçer kaydı `stops`'a → birleştirme (11.10) → `_apply(emit)`.
- **`_apply(events, emit, save)`** (`:658-681`): olay yok ve `emit`/`save` yoksa hiçbir şey. Gün açıksa sürüş kaydının gün kimliği `dayId`; görevlere `activeDay(dayId?)` ve `activeOrder(süren sipariş var)`; her olay → sürüş günlüğüne `shift_<tür>` (veri olayınki; satır zamanı kaydedicinin saati, olayın `t`'si değil — Bölüm 12), toast (aşağıda), `delivered` → `deliveryFinished()` (Bölüm 13); gün kapandıysa kayıt gün kimliği null. Kalıcılık: `save` ya da olay var ya da son yazımdan ≥ 15 sn → gün açıksa `shift.v1` = durum JSON'u, kapalıysa `shift.v1` silinir. Görünüm: `ShiftView(durum, rev, lastDay, lightAt, lightSince, lightsRev)`.
- **Olay → toast** (`_notify`, `:683-722`; `m:ss` = dakika:saniye, bilinmiyorsa "?"; `<ek>`: veri `source == "kuryeplus"` → " (KuryePlus)", değilse `auto == true` → " (otomatik)" / " (avtomatik)", değilse boş):

| Olay | TR | AZ | Not |
|---|---|---|---|
| `day_start` | "Gün başladı — km, süre ve durmalar kaydediliyor" | "Gün başladı — km, vaxt və işıqforlar qeyd olunur" | yeşil; evden çıkış metni 11.2 |
| `order_start` | "Sipariş N başladı → restorana" | "Sifariş N başladı → restorana" | pembe |
| `arrive_restaurant` | "Restorana vardın<ek> — bekleme başladı" | "Restorana çatdın<ek> — gözləmə başladı" | orta titreşim |
| `leave_restaurant` | bekleme yok: "Restorandan ayrıldın<ek> · bekleme bilinmiyor"; varsa "Restorandan ayrıldın · bekleme m:ss" | "Restorandan çıxdın<ek> · gözləmə məlum deyil" / "Restorandan çıxdın · gözləmə m:ss" | orta titreşim; bekleme bilinmiyorsa uydurulmaz (E8) |
| `arrive_customer` | "Müşteriye vardın<ek>" | "Müştəriyə çatdın<ek>" | orta titreşim |
| `delivered` | "Sipariş N teslim edildi · m:ss" (`totalS`) | "Sifariş N çatdırıldı · m:ss" | güçlü titreşim, 6 sn |
| `stop` (`kind == "light"`) | "Trafik ışığında m:ss bekledin" | "İşıqforda m:ss gözlədin" | turuncu; süre `end − start` |
| `order_cancel` (yalnız feed kaynaklı) | "Sipariş N KuryePlus'ta alınmadan bitti — iptal sayıldı" | "Sifariş N KuryePlus-da götürülmədən bitdi — ləğv sayıldı" | turuncu |

  Öteki olaylar (`stopping`, `order_target`, `customer_journey`, `day_end`, `home_return_*`; `light?`, `stop`, `order`, `mall`, `sidewalk` duruşları) toast üretmez. Toast yalnız uygulama içi şerittir (7.11; G4).
- **WEEM metin kararı (öneri, M-11-1; Bölüm 17):** "KuryePlus" adı WEEM'de yok (aşamalar WEEM'in kendi ekran okumasından). `<ek>` hep boş olur (bugün varış olaylarının hepsi feed kaynaklı olduğundan " (otomatik)" eki de görünmez); iptal metni "Sipariş N alınmadan bitti — iptal sayıldı" / "Sifariş N götürülmədən bitdi — ləğv sayıldı". Olay verisindeki `source: "kuryeplus"` kayıt dizgisi olarak kalır (11.7). Toast ve titreşim yalnız Harita sekmesi görünürken (`:app` şeridi); kaçan toast sonradan gösterilmez.
- **`startDay(at, reason)`** (`:191-202`): bitiş sürüyorsa yok; ölçer sıfırlanır; `at` gelecekteyse şimdi; motor `startDay`; olay varsa izleyici `dayChanged()` (YolGo'da ayrıca ev kuralı ve Rawstats ev tazelemesi — WEEM'de yok); `_apply(save)`; tık başlar.
- **`endDay(at, reason)`** (`:206-231`): aynı anda gelen bitişler tek işlemde birleşir. Sıra: `at` sıkıştırma (11.2) → eve dönüş ölçümü biter (11.13) → ölçer `finish(at)` (kayıt `stops`'a) → motor `endDay(at, roadInfo, reason)` → `day_end` olduysa izleyici `dayChanged()` → birleştirme → tık durur → `_apply(save)` (olaylar günlüğe; `shift_day_end` sürüş kaydını kapatır) → telemetri `configure(day: null, eligible: false)` (Bölüm 12) → `lastDay = gün`, `shift.last` = gün JSON'u, `shift.v1` silinir → gün raporu (Bölüm 12) → yeni boş motor → görünüm. YolGo'da elle bitişten sonra rota temizlenir ve İstatistik o güne odaklı açılır (`shift_widgets.dart:175-178`); WEEM'de `ShiftCard`'ın bitir eylemi (SORU-N7).
- **`setDestination(point, restaurant, name)`** (`:233-234`): navigasyonun hedef bağı (`_bindShiftTarget`, 7.3; restoran sayılması 5.3) → motor `setDestination` (11.6) + `_apply(save)`.
- **WEEM düzeni:** `day.DayController` saf durum makinesidir: girdiler `onFix(DayFix, nowMs)`, `onStages(FollowerFeed?, nowMs, lastPoint)`, `onShift(ShiftState?, nowMs)` (`ShiftFollower` kararı → `startDay` / `endDay`), `onTick(nowMs)`; komutlar `markLight`, `markSidewalk`, `setDestination`, `startHomeReturn`, `reviewLight`; çıktı `DayStep(events, save, toasts, haptic, view)`. Yan etkiler (`map` tercihleri, sürüş günlüğü, gün raporu, görev sinyalleri, toast şeridi) Android `DayRuntime`'dadır; saat `:core` `Clock`.
- **Ek WEEM kuralı (öneri):** açılışta `shift.v1` aktif, vardiya da aktif ama `startedAtMs` gün başından farklıysa (süreç ölüyken vardiya bitip yenisi başladı) eski gün yeni vardiyanın başlangıcıyla `endDay(reason = "manual")` kapanır, yeni gün o başlangıçla açılır (YolGo'da eski gün sürerdi; WEEM'de gün = vardiya, `dayId` vardiyanınki olmalı). Vardiya kapalı ve `shift.v1` aktifse `ShiftFollower` `END_DAY`'i `endedAtMs` ile verir (gün geriye dönük kapanır).
- **Aşama okuması yokken (WEEM):** `day.StageFeed` `OrderEvents` bağlanmadıysa (`NONE`) ya da 2 telefonda Receiver henüz `TripStages` almadıysa null verir (YolGo "okuma yok" = hiçbir şey değişmez); tek telefonda `WeemApplication` `Orders.bind`'ı (geri yükleme dahil) `Maps.bindOrders`'tan önce yapar, böylece süreç başındaki boş `TripStages` eksik okuma sayılmaz.

### 11.15 Veri

| Ad | Tip · biçim | YolGo yeri | WEEM |
|---|---|---|---|
| `shift.v1` | String JSON (`DayState`, `v: 2`; 11.5) | Flutter tercihleri | `map` tercih dosyası, aynı ad ve biçim (S1: aktarılmaz) |
| `shift.last` | String JSON (biten son gün) | aynı | aynı |
| `courier.follow.v1` | String JSON (11.7) | aynı | aynı |
| `lights.v1` | List<String> `"lat,lon,yes,no,waitS"` | aynı | `map`, JSON dizi dizgesi (14.1); S1: yeniden öğrenilir |
| `sidewalk.zones.v1` | String JSON `[{lat, lon}]` | aynı | aynı; S1: yeniden girilir |
| `shift.deleted.days.v2` | List<String> gün kimlikleri | aynı | Bölüm 12 |
| `day.shift.handled.v1` | String JSON `{"end": ms?, "start": ms?}` | — (YolGo `rawstats.shiftEnd/Start.handled.v1`, int) | yeni (11.2) |
| `day.home.v1`, `rawstats.shiftEnd.handled.v1`, `rawstats.shiftStart.handled.v1` | — | aynı | **girmez** (14.3) |
| sürüş günlüğü olayları | `shift_day_start {reason}`, `shift_day_end {reason}`, `shift_order_start {n}`, `shift_order_cancel {n}`, `shift_order_target {n, restaurant}`, `shift_arrive_restaurant {n, auto}`, `shift_leave_restaurant {n, waitS}`, `shift_customer_journey {n}`, `shift_arrive_customer {n, auto}`, `shift_delivered {n, totalS, m}`, `shift_stopping`, `shift_stop {StopRec}`, `shift_home_return_start`, `shift_home_return_arrived`; aşama izleyiciden gelenlerde `source: "kuryeplus"`; `light_answer`, `stop_surface_override` | `logs/YYYY-MM-DD.jsonl` | biçim Bölüm 12, dizin 14.5 |
| gün raporu `logs/YYYY-MM-DD_HHMM.gun.json` | `DayState` JSON + `summary` + `lightsLearned` | `app_flutter/logs/` | Bölüm 12 |

Kayıtlarda ev koordinatı, müşteri adı/telefonu, adres metni yok. Gün durumunda restoran adı (`restName`) ve restoran/müşteri noktaları (`restLat/Lon`, `custLat/Lon`, `restTarget`, `customerTarget`; yuvarlanmaz) vardır — paylaşım ve dışa aktarma kuralı Bölüm 12.

### 11.16 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| `shift_engine_test` | 9 | 60 sn 18 km/s → 300 m (±15), hareket 59 sn (±2), 30 sn duruşta km artmaz · geri zamanlı fix, 41° sıçraması ve geri dönüş km eklemez · hızsız ±8 m titreme km eklemez · eve dönüş alanları olmayan eski gün JSON'u okunur, alanlar yazılıp okununca korunur · ışık yokken 20 sn duruş `stop`, 5 sn kayıt yok, yol dışı `stop`, ışık yanında 15 sn `light` · hedefte 135 sn duruş aşama ve soru üretmez; `stageFromFeed` → `atRestaurant` (`restAuto`), 20 sn sonra `toCustomer` (`restWaitS` 20), `atCustomer` (teslim yok), `deliverFromFeed` → `delivered` · kavşakta 80 sn kırmızı (ışık bilinmiyor) varış değil, `stop` · siparişsiz 200 sn duruş sipariş açmaz · JSON gidiş-dönüş (aktif, `atRestaurant`, km ±1, sipariş 1) | `day.ShiftEngineTest` | A6.2b → A6.8 | `_Sim` → `DaySim` yardımcısı (1 Hz kuzeye fix); `DateTime(2026,10,1,12)` yerel saat → sabit ms |
| `light_tracker_test` | 4 | bilinen ışıkta 5 sn, `since` = başlangıç; 1. hareket fix'i kayıt vermez, 2.si → `light`, `durS` 6 (bitiş ilk hareket), `measured`, `since` null · bilinmeyen kavşakta (jm 10) 12 sn → `light?`; öğrenilmiş "yok" yerde aday yok · 30 sn GPS boşluğu beklemeye eklenmez; sipariş duruşunda (`orderStop`) `finish` boş · `finish(10 sn)` son geçerli fix'e (5 sn) kadar; son fix'ten 25 sn sonra `finish` boş | `day.LightTrackerTest` | A6.2b → A6.8 | |
| `light_leaks_f52_test` | 3 | bilinen ışıkta 4 sn → `light`; bilinmeyen kavşakta 8 sn yok, 12 sn `light?` · 5 sn'de bir kötü fix (60 m) beklemeyi bozmaz; 14 sn kötü fix dizisi sıfırlar, kalan bekleme yine `light` · sipariş duruşunda ölçülmez | `day.LightLeaksTest` | A6.2b → A6.8 | karakterizasyon: kural değişirse bilerek güncellenir |
| `light_rule_m14_test` | 14 | **F9.2** (6): 4 m yolda / yol kenarında / 5 m → ışık · yaya yolunda ≤ 1 m ışık, 3 m ve yolda değil → değil, ne yolda ne yaya yolunda → değil · 6 m ve 12 m → değil · AVM, elle kaldırım, bilinmeyen ışık → değil · motor: yaya yolunda ışık altında 30 sn `light`, yol kenarı `light`, 12 m kaldırım `sidewalk`, AVM `mall` · ölçer: yaya yolunda 20 sn → `light` · **F9.3** (5): ölçer 100 sn `light`, 150 sn `light?` + `longWait` + ışık yeri, `isLight` false · motor 60 sn `light`, 150 sn `light?` + `longWait` · `long` alanı yazılır/okunur, eski kayıtta false, kısa beklemede yazılmaz · gün sonu: 2 aday + 1 ışık, cevaplanınca aday 0, ışık 2 · `lightReviewTeaches` (uzun + Reddet → null; aday iki yönde öğretir) · **F9.1** (3): varsayılan 5 m (`AppSettings`, `RoadInfo`, boş JSON) · 25 → 5 göçü · göçten sonra elle 25 korunur | F9.2 → `day.LightRuleTest` 4, `day.ShiftEngineTest` 1, `day.LightTrackerTest` 1; F9.3 → `day.LongLightTest` 5; F9.1 1. → `day.LightRuleTest` (`RoadInfo` ve `MapSettings` varsayılanı) | A6.2b → A6.8 | F9.1 2.–3. **çevrilmez** (S1: `lightMaxMv2/v3` göçü yazılmaz, 14.2) |
| `courier_stage_follow_test` | 14 | **ayrıştırma** (3): `activeTrips` aşamaları ve `phaseOfStage` · v1 / paylaşım kapalı / bozuk / büyük yük null · bilinmeyen aşama: yolculuk var, aşama null · **izleme** (11): `to_pickup` → … → bitti, elle basmadan teslim (`source: kuryeplus`, aynı aşama boş, tek eksik okuma bekler, ikincisinde `delivered`, bitmiş yolculuk yeni sipariş açmaz) · atlanan aşamalar sırayla · ilk görülen yolculuk mevcut aşamasına kadar · geri yön uygulanmaz (çok duraklı yolculukta sonraki müşteri dahil) · elle iptal / teslim edilen sipariş aynı yolculuktan açılmaz · restoran adresi önceden paylaşılsa da siparişi aşama açar, adres hedef olur · alıştan önce biten yolculuk iki eksik okumada iptal, okuma yokken (null) değişmez · okuma yoksa sipariş açılmaz · gün kapalıyken izlenmez, gün değişince süren yolculuk yeni günde sipariş açmaz · ikinci yolculuk ilki bitince sipariş 2 · kalıcı durum `{"v":1, "trip", "order", "stage", "closed": []}` ve geri yükleme | izleme 11 → `day.CourierStageFollowerTest` (girdi `FollowerFeed(trips, stages = null)`); ayrıştırma 1. ve 3. → **uyarlanır** `day.StageFeedTest` (`TripStages` → `FollowerFeed`: açıklar sırası, aşama eşlemesi, aşaması bilinmeyen yolculuk) | A6.2b → A6.8 | ayrıştırma 2. **çevrilmez** (feed v2 zarfı yok, S7; `TripStages.parse` A2.5 `MessagesTest`'te) |
| `courier_stages_e8_test` | 13 (döngüyle 14) | **ayrıştırma** (2): `stages` ve `changedAtMs` okunur (olaylar sıralı ve saatli, `pickup` / `source`, biten satırın sonucu ve saati; dört sonuç adı) · eski Rawstats (`stages` yok), bozuk `stages`, bilinmeyen aşama atlanır · **zaman çizelgesi** (11): aşamalar Rawstats saatleriyle, yeni sipariş ilk aşamanın saatinde (4 olay: 4, 300, 600, 600 sn) · D17: kaçan okumada varış ve çıkış kendi saatlerinde · varış görülmediyse `restArrivedAt` ve `waitS` null · biten sipariş sonucu ve saatiyle hemen (`custWaitS` 50), ikinci okuma beklenmez · `delivered_unverified` / `unconfirmed`: teslim saatinde, `custArrivedAt` null (döngü 2) · alıştan önce `cancelled` → bitiş saatinde iptal; alınmışsa sipariş kalır · saatler [son uygulanan, şimdi] aralığına kırpılır · ikinci restoran (`pickup` 1) geri yön sayılmaz, çıkış son alışın saatinde · `stages` yoksa eski kural + `awaitingSecondRead` · `stages` var ama yolculuk yoksa eski kurala düşer · kalıcı durum `at` ile; eski (saatsiz) kayıt okunur | zaman çizelgesi 11 → `day.CourierStageTimelineTest` (girdi `FollowerFeed(trips, stages)`); ayrıştırma 1. → **uyarlanır** `day.StageFeedTest` (olay sırası ve saatleri, sonuç ve bitiş saati eşlemesi) | A6.2b → A6.8 | ayrıştırma 2. **çevrilmez** (eski Rawstats ve bozuk zarf: WEEM'de `TripStages` tiplidir, S7) |
| `home_day_controller_test` (4. test) | 1 | gün açık; okuma yok → sipariş yok; `to_pickup` → `toRestaurant`; `at_pickup` → `atRestaurant` + toast "Restorana vardın (KuryePlus) — bekleme başladı"; `courier.follow.v1` `stage = at_pickup`, restoran adı yok; eski `followCourierStages: false` izlemeyi durdurmaz | `day.DayControllerTest` (sahte `stages`, sahte tercih deposu) | A6.2b → A6.8 | **uyarlanır:** toast M-11-1 ("Restorana vardın — bekleme başladı"); ayar kısmı düşer (ayar yok, 14.2). 1.–3. test `:core`'da (16.4), 5. test Bölüm 12 |
| `yolgo_sade_2_test` (motor) | 4 | restoran adresi paylaşmak sipariş açmaz · adres yalnız hedef; aşama geri sarılmaz, müşteri bacağı kendiliğinden başlamaz · hedefte 200 sn duruş soru üretmez, `question` yazılmaz · eski kayıt alanları (`question`, `pickupGroup`, `secondOrderWindowUsed`) okunur, yazılmaz | `day.ShiftEngineStagesTest` | A6.2b → A6.8 | widget testleri 6.14 |
| `yolgo_rules_test` (2) | 2 | eski sipariş JSON'u (`restArrived` = `restLeft`, `customerDeparted` yok) `toCustomer` okunur, gidiş-dönüşte geri sarılmaz · belirsiz yüzey (doğruluk 20, `onRoad` false, ışık bilinen) ölçerde `light` | `day.DayStateJsonTest` 1, `day.LightTrackerTest` 1 | A6.2b → A6.8 | öteki 2 test 7.15, 10.12 |
| `v140_features_test` (Y11) | 5 | ayardaki mesafe (5 m: 8 m değil, 4 m ışık; 10 m ayarıyla 8 m ışık) · AVM ışık değil, ışık yarıçapında yol kenarı ışık, AVM'de `allowsTrafficLight` false · `isRoadside` (yarım şerit 4 m / 7 m; doğruluk 15 → hayır) · ölçer 30 m uzaktaki ışıkta başlamaz · AVM yanında duruş `mall`, `lights` boş | `day.LightRuleTest` 3, `day.LightTrackerTest` 1, `day.ShiftEngineTest` 1 | A6.2b → A6.8 | öteki 9 test 10.12 |
| `assistant_test` (gün motoru) | 6 | hedefe yakınlık ve hız varış yapmaz, varışı ve alışı aşama verir (`restWaitS` 20) · restoran ya da konut adresi sipariş açmaz · aynı ya da geri GPS zamanı sipariş durumunu değiştirmez · sipariş ya da restoran adresi günü kendiliğinden başlatmaz · `classifySurface` (yaya 1 m / yol 12 m kaldırım, doğruluk 20 ve yaya 2 m belirsiz, elle kaldırım) ve kaldırımda ölçer saymaz · kaldırım duruşu `sidewalk`, `isLight` false, JSON'da yüzey korunur | `day.ShiftEngineStagesTest` 4, `day.StopSemanticsTest` 1, `day.ShiftEngineTest` 1 | A6.2b → A6.8 | öteki 8 test: Y20 5 (13.18), Y6 1 (8.11), `HeadingFilter` 1 (7.15), hava 1 (10.12) |
| **Yeni** (karakterizasyon; eski test yok) | — | `LightsDb` (biçim, bozuk satır, 25 m `lookup`, `nearest` yalnız `yes ≠ no`, `record` + `setManual` son cevap kazanır, `waitS` birikir) · birleştirme (ölçer kazanır, sipariş duruşu ölçeri siler, ±3 sn / 40 m) · yol bilgisi (11.8 adımları: OSM ışığı + öğrenilmiş "yok", AVM 80 m, kaldırım 12 m, graf yokken, son fix doğruluğu, kavşak ±80 m) · eve dönüş (başlama koşulları, varış 35 m / 1,3 m/sn / 20 m, sipariş açılınca biter, birikme) · vardiya bağı (`ShiftFollower` → gün, `at` sıkıştırma, yeni vardiyada eski gün kapanır) · toast metinleri TR/AZ (M-11-1) · `StageFeed` okuma yokken null | `day.LightsDbTest`, `day.StopReconcilerTest`, `day.RoadInfoBuilderTest`, `day.HomeReturnTest`, `day.DayShiftSyncTest`, `day.DayTextTest`, `day.StageFeedTest` | A6.2b → A6.8 | değerler bu bölümden |

Toplam (bu bölüm): 75 test tanımı; çevrilen 71 (4'ü uyarlanır: `StageFeedTest` 3, `DayControllerTest` 1), çevrilmeyen 4 (F9.1 göçü 2, feed v2 zarfı 2). `home_day_controller_test` 5. test (`dayReasons`) Bölüm 12'de.

### 11.17 WEEM yerleşimi

- `logic/map` **`day`**: `DayFix`, `StopSurface`, `RoadInfo`, `StopSemantics` (`classifySurface`, `roadHalfWidthM`, `isRoadside`, `MALL_RADIUS_M`), `RoadInfoBuilder`, `StopRec`, `OrderRec`, `OrderPhase`, `DayState` (+ JSON, `v: 2`), `DayEvent` (YolGo `ShiftEvent`: tür, zaman, veri), `ShiftEngine`, `LightTracker`, `LightsDb`, `SidewalkMarks`, `StopReconciler`, `CourierStageFollower`, `FollowerFeed` + `StageFeed` (`TripStages` uyarlayıcısı), `HomeReturn`, `DayController`, `DayView`, `DayText` (toast metinleri, `Bilingual`). Kullandığı paketler: `geo`, `graph` (yol grafiği, POI dizini), `settings` (`lightMaxM`); `logic/core` (`ShiftState`, `ShiftFollower`, `TripStages`, `Clock`).
- Android `:map`: **`DayRuntime`** — `NavLocation` fix'leri → `onFix`; `OrderEvents.stages` → `onStages` (+ 2 sn sonra ikinci okuma); `ShiftPort.state` → `onShift`; 5 sn tık; `map` tercihleri (`shift.v1`, `shift.last`, `courier.follow.v1`, `lights.v1`, `sidewalk.zones.v1`, `day.shift.handled.v1`); sürüş günlüğü ve gün raporu yazıcısı (Bölüm 12); görev sinyalleri (Bölüm 13); `StateFlow<DayView>` (`:app`'e). `MotionReceiver` **yazılmaz** (Y13 ile, 11.1).
- `:app`: `ShiftCard` satırları (ışık sayacı, aşama satırı, tamamlanan sipariş; SORU-N7, Bölüm 12), harita ışık işareti (6.6), uzun basış satırları 5, 6, 16, 17 (6.7), İstatistik'teki ışık soruları (Bölüm 12), toast şeridi (7.11).
- `:core`: `ShiftPort`, `ShiftFollower`, `OrderEvents.stages`, `Clock`, ev (`KuryelogPrefs`). Yeni `:core` girdisi gerekmez (SORU-11-1 (a) seçilirse `ShiftState` v1'e `startReason`).
- **2 telefon (öneri):** gün motoru, ışık ölçer ve ışık soruları **konumun ve haritanın olduğu telefonda** (Receiver; tek telefonda aynı telefon); girdiler `ShiftState` ve `TripStages` P → R mesajlarıyla gelir (plan 4.4). Provider'da gün motoru çalışmaz (çift km ve çift ışık sayımı olmasın); Provider'ın gün kartı ve İstatistik ekranı için R → P mesajı yok → SORU-11-2 (Bölüm 17).

### 11.18 Farklar ve açık noktalar

- **Bilinen davranışlar (korunur):** motor 30 m kaymayla ölçerden önce biterse aynı duruşun iki kaydı kalabilir (11.10); `light_answer` gün kapalıyken yazılmaz, başka bir gün açıksa o günün dosyasına ve kimliğiyle yazılır (11.1 düzeltme 4); `reviewLight` evet/hayır saymaz, son cevap kazanır (11.11); `shift_*` günlük satırlarının zamanı kaydedicinin saatidir, olayın kendi zamanı değil (aşama izleyicinin geriye dönük saatlerinde fark eder; doğru saatler gün durumunda ve raporda) (Bölüm 12); motor `light?`'ı yalnız bilinen ışıkta 120–360 sn duruşa verir, bilinmeyen kavşak adayı yalnız ölçerden (11.4); `lightMaxS` yorumu "3 dk", kod 6 dk (11.4).
- **Ölü yollar (yazılmaz):** `stoppedSince`, `stopHomeReturn`, `lightsLearned` getter'ı (gün raporundaki `lightsLearned` alanı kalır), `queuedOrders` üretimi (alan okunur), `autoStartDay` / `autoEndDay` / `followCourierStages`, `MotionReceiver` (Y13'e kadar), YolGo `home_departure.dart` (1).
- **WEEM farkları:** gün açılış ve kapanışı vardiyadan (11.2; ev kuralı, Rawstats vardiya okuması, 60 sn yedek, feed okuma düzeni, `ReadGate` yok); aşama girdisi `OrderEvents.stages` (11.7, 11.14); toast eki ve iptal metni M-11-1; yeni vardiyada eski aktif gün kapanır (11.14); toast yalnız Harita sekmesi görünürken.
- **SORU-11-1 (koordinatöre; Bölüm 17):** `ShiftState` v1 başlangıç nedenini taşımıyor; gün raporundaki "Evden çıkınca başladı" satırı (`dayReasons`, Bölüm 12) ve `day_start {reason}` olayı için gerekir. (a) `ShiftState` v1'e isteğe bağlı `startReason` (`manual` \| `home_departure`) eklenir (`:core` küçük şema eki; okuyucu bilinmeyen alanı zaten yok sayar, `schemaVersion` aynı kalır; `ShiftMachine` başlatırken yazar); (b) `:map` başlangıç nedenini hep `manual` yazar (satır hiç görünmez); (c) `:map` ev kuralının son kararını `:core`'dan ayrı bir portla sorar. Öneri (a).
- **SORU-11-2 (koordinatöre; Bölüm 17):** 2 telefonda Provider'ın gün kartı (km, süre, ışık sayacı) ve İstatistik ekranı neyi gösterir? (a) A8.3'te R → P `DaySnapshot` mesajı (gün durumu özeti 60 sn'de bir ve gün sonunda gün raporu; son durum geçerli sınıfı); (b) Provider'da bu alanlar gizlenir, "Receiver'da" notu; (c) Provider kendi GPS'iyle ayrı gün tutar (çift kayıt). Öneri (a) (plan 4.5: "veri asıl kaynağından gelir").
- **M-11-1 (metin onayı; Bölüm 17):** " (KuryePlus)" eki kalkar; "Sipariş N alınmadan bitti — iptal sayıldı" / "Sifariş N götürülmədən bitdi — ləğv sayıldı".
### 11.19 A6.8 uygulama notları (20 Ekim 2026)

- **Saf (`logic/map/day`):** `DayModel` (`RoadInfo.atLight`/`allowsTrafficLight`, `StopRec`/`OrderRec`/`DayState` JSON; `OrderPhase.jsonName` camelCase = Dart adları, eski kayıtta `pickupFlow` yoksa `customerDeparted = restLeft`), `ShiftEngine.feed`/`endDay` (11.3–11.4 aynen: `_odometer`, aday, tür sırası; `stopping` olayı), `StopSemantics`, `LightTracker` + `LightReview`, `LightsDb`, `SidewalkMarks`, `StopReconciler`, `RoadInfoBuilder` (11.8 adımları; `lastAccuracy` çağrıyla), `StageFeed` + `CourierStageFollower` (11.7 aynen: zaman çizelgesi, `_ended`, iki eksik okuma, kalıcı durum), `DayText`/`DayToasts` (M-11-1 metinleri; ton yeşil/pembe/turuncu), `DayController` (`onFix`, `onStages`, `onShift` → `ShiftFollower`, `onTick`, `startDay`/`endDay`, `markLight`, `markSidewalk`, `setDestination`, `startHomeReturn`, `reviewLight`; `shift.v1` 15 sn kuralı, `shift.last`, `courier.follow.v1`, `lights.v1`, `sidewalk.zones.v1`, `day.shift.handled.v1`). `:core` `ShiftFollower.handled()` eklendi (damgaları tercihe yazmak için).
- **Android:** `:map` `DayRuntime` (fix → `onFix`; `ShiftHub.state` ve 5 sn tık → `onShift`; `bindOrders(OrderEvents)` → `onStages` + 2 sn sonra ikinci okuma; `navShift` = `NavController.shift`; `steps`/`view` akışları; `onStep` kancası A6.9 günlüğü için), `NavRuntime` (fix'ler günü besler, `controller.shift`, `controller.restaurantNear`, arama dizinine düzeltmeli yerler, `bikeway` deposu).
- **Açık (başka görevlerde):** `OrderEvents.stages` için Android uygulayıcısı henüz yok (`:orders` `OrderEvents`'i yayımlamıyor) → `DayRuntime.bindOrders` çağrılmıyor, aşama izleyici cihazda çalışmaz (okuma yok = hiçbir şey değişmez); bağlama `:orders` tarafında `OrderEvents` verilince tek satır. Sürüş günlüğü, gün raporu, görev sinyalleri A6.9. Eve varış denetimi için `DayController.home` her tıkta `Home`'dan verilir ama ev kuralı `:core`'da. `NavAssistantTest` 1 test `TODO("A6.9")` (görev denetleyicisi) bekler — A6.8 yeşile döndürmez.
- **Test notu:** `CourierStageTimelineTest` iki beklentisi `listOf<Any?>` yapıldı (Long/Int uyumsuzluğu; davranış aynı).

<!-- END sec:11 -->

<!-- BEGIN sec:12 -->
## 12. Y17 İstatistik ekranı ve Y18 Sürüş kayıtları (A6.9)

### 12.1 Kaynak ve kapsam

| YG dosya | Satır | İçerik | WEEM |
|---|---|---|---|
| `lib/ui/stats_screen.dart` | 18–99 · 101–238 · 241–283 · 286–294 | `StatsScreen` (sekmeler, yenile, boş metin) · `_DayView` (kutular, ışıklar, eksik ışık kontrolü, siparişler, paylaş, sil) · `_LightsMap` · `dayReasons` | `:app` `StatsScreen`, `DayStatsPage`, `LightsMiniMap`; saf `stats.StatsTabs`, `stats.LightStats`, `stats.DayReasons`, `stats.StreetLabel` |
| `lib/ui/shift_widgets.dart` | 15–20 · 175–183 · 185–193 · 195–249 · 251–272 · 274–316 | `fmtDur`, `fmtClock` · elle bitişten sonra İstatistik · `_phaseInfo` · `_ShiftCard` içerik satırları · `_OrderRow` · `LightWaitChip` (11.9) | `stats.DurationText`; `:app` `ShiftCard` satırları (SORU-N7); kart düzeni 6.12 |
| `lib/tracking/shift_controller.dart` | 164–176 · 297–352 · 571–593 | `forgetDay` · gün raporu (`_reportFile`, `writeReport`, `shareReport`, `summarize`) · `history` | `stats.DayStats`, `stats.DayReportFile`, `stats.DayHistory`; Android `DayReportWriter` |
| `lib/tracking/day_deletion.dart` | 14–54 · 56–70 | `DayDeletion.delete` · `confirmDeleteDay` | `stats.DayDeletionPlan` (saf sıra) + Android `DayDeletionRuntime`; `:app` onay penceresi |
| `lib/tracking/ride_logger.dart` | 24–57 · 63–74 · 76–261 | `SensorHub` (barometre, `baroAltitude`; titreşim hep 0) · sağlayıcı (konum akışı, `logging` ayarı) · `RideLogger` | `logs.RideLogWriter` (saf) + Android `RideLogRuntime`; barometre `BatteryRuntime` kaynağından (10.13) |
| `lib/tracking/log_store.dart` | 5–13 · 15–88 | `RideLogEntry` · `RideLogStore` (ad kalıbı, liste, gruplama, silme, dışa aktarma) | `logs.RideLogNames`, `logs.RideLogStore` (`java.io`, Android'siz), `logs.JournalExport` |
| `lib/nav/route_recorder.dart` | 7–22 · 27–143 | `RouteDeviation` · `RouteRecorder` | `logs.RouteRecorder`, `logs.RouteDeviation` (saf) |
| `lib/nav/nav_controller.dart` | 910–959 · 964–974 | `_record`, `_recordFix`, `_finishRecording` · `_distanceToPolyline` | `NavRuntime` → `RouteRecorder`; rota dosyasını `RideLogRuntime` yazar |
| `lib/ui/ride_logs_screen.dart` | 17–33 · 35–199 | `rideLogStatus`, `RideLogStatusText` · `RideLogsScreen` | `logs.RideLogStatus` (saf), `:app` `RideLogsScreen` |
| `lib/tracking/native_telemetry.dart`; `android_overlay/` `TelemetryService.kt` (354), `TelemetryJournal.kt` (106), `TelemetryBridge.kt` (52) | tümü | Flutter ↔ yerel telemetri servisi: günlük GPS sahibi, barometre, Y13 sensör pencereleri, kalıcı günlük `telemetry-v2.jsonl`, tercih dosyası `yolgo_runtime_v2`, bildirim 7400 | **kalkar** (12.11) |
| başvuru | — | `lib/ui/settings_screen.dart:251-260` (giriş satırı, 6.9), `MainActivity.kt` `saveFile` (6.13), `lib/core/day_scope.dart` (11.1), `lib/ui/widgets.dart:444-468` (`_AvgSpeed`, 7.11) | |

Kapsam dışı (başkalarında): gün motoru, ışıklar ve gün sonu ışık cevabı mantığı (Bölüm 11), aşama metinleri ve gün kartının düzeni (6.12), rota motoru ve navigasyon olaylarının üretimi (3, 7, 8), olay adlarının dizini (14.5), Y12 gün katkılarının silinmesi (9.10), Y13 yüzey deposu (sonra).

Bulgu doğrulaması (`Y_contract.md` dosyalar ve dışa aktarma satırları; Y17/Y18 için `Y_tracking.md`'de ayrı bölüm yok): ad kalıbı, satır alanları, `duzyol-journal-v1` biçimi ve satır numaraları `870ab6e`'de doğru. **(bulgu düzeltmesi:** gün raporu adı 1.11'de hep `YYYY-MM-DD_HHMM.gun.json`; kalıptaki `YYYY-MM-DD.gun.json` yalnız eski kayıtların okunması içindir.)**

### 12.2 Sürüş günlüğü (`RideLogger`, `ride_logger.dart:76-261`)

- **Klasör:** YolGo `<documents>/logs` (Android `app_flutter/logs/`). **WEEM:** `files/map/logs/` (14.1, 14.4; `files/logs/` Rawstats gün klasörleriyle karışmasın, `VERI_SOZLESMESI` 7.1). Dosya adları ve biçimler aynen.
- **Gün kapsamı** (`setDay(dayId?)`, `:91-103`): aynı gün → hiçbir şey; önceki gün varsa açık sürüş `ride_end {reason: "day_scope_closed"}`; zamanlayıcılar durur; `dayDate` = gün kimliği `yolgo-<ms>` ise o anın takvim günü (`bakuDay`), değilse bugün; gün null → açık sürüş adı ve `ride.active.v1` silinir, tampon boşaltılır; gün varsa 5 sn'de bir boşaltma, 1 sn'de bir `env`. Çağıran gün motoru (`_apply`, 11.14): gün açıkken `dayId`, kapanınca null. **Gün gece yarısı bölünmez:** bütün satırlar günün başladığı takvim gününün dosyasına gider (`log_store_test` 6).
- **Ayar:** `enabled` = `MapSettings.logging` (varsayılan true, 14.2); açıktan kapalıya geçişte açık sürüş `ride_end {reason: "logging_off"}` (yazım kapanmadan önce).
- **Satır** (`_write`, `:131-138`): ayar kapalı ya da gün yoksa yazılmaz. `{"t": şimdi ms, "dayId": "yolgo-…", …alanlar}` → `<dayDate>.jsonl` tamponuna ve sürüş açıksa sürüş dosyasının tamponuna (aynı satır iki dosyada). `t` kaydedicinin saatidir (olayın kendi zamanı değil, 11.18).
- **Satır türleri:**
  - `k: "gps"` (`gps`, `:161-172`): son yazılan gps satırının fix zamanına göre fark < 900 ms (mutlak) ise atlanır; `{k, la, lo (6 hane), ac (1), sp (2; −1 = bilinmiyor da yazılır), hd (1), al (1)}`. Kaynak: konum akışının her fix'i (7.6).
  - `k: "env"` (`_env`, `:174-181`; 1 sn'de bir): barometre yüksekliği yok ve titreşim 0 ise yazılmaz; `{k, hpa? (3), balt? (2), vr (3), vm (2)}`; `balt = 44330 × (1 − (hPa / 1013,25)^0,1903)`, basınç ≤ 3 sn tazeyse (`SensorHub.baroAltitude`, `:49-53`). 1.11'de titreşim ölçülmez: `vr`, `vm` hep 0 (`SensorHub.start` boş, `:42-44`).
  - `k: "ev"` (`event`, `:184-196`): `{k, e: ad, …veri}`; olay dizini 14.5.
- **Tampon ve yazım** (`flush`, `:198-220`): dosya başına metin tamponu; boşaltmalar sırayla (önceki yazım bitince), klasör yoksa yaratılır, her dosyaya ekleme; hata olursa metin tamponun başına geri konur (veri kaybolmaz; eşzamanlı boşaltmalar sırayı korur, `log_store_test` 6). Boşaltma: 5 sn'de bir, sürüş kapanışında, gün kapanışında, listeleme / silme / dışa aktarma öncesinde, kapanışta.
- **WEEM:** `logs.RideLogWriter` saf: girdiler `setDay(dayId?, nowMs)`, `setEnabled(Boolean)`, `gps(fix)`, `env(pressureHpa?, pressureAtMs, nowMs)`, `event(name, data, nowMs)`; çıktı dosya adı → satırlar + `ride.active.v1` değişikliği. Android `RideLogRuntime`: `files/map/logs/`'a ekler (tek iş parçacığı, ekleme kipi), 5 sn zamanlayıcı, `NavLocation` fix'leri, `BatteryRuntime`'ın basınç okuması; takvim günü `LogTime.day(startedAtMs, clock.zone())` (11.2).

### 12.3 Sürüşler (ayrı sürüş dosyaları)

- `beginRide(reason)` (`:140-148`): ayar açık, gün var ve açık sürüş yoksa `stamp = max(şimdi ms, son sürüş damgası + 1)`; ad `<dayDate>_ride_<stamp>.jsonl`; `ride.active.v1` = ad; satır `{k: "ev", e: "ride_start", reason}`.
- `endRide(reason)` (`:150-156`): açık sürüş varsa `{k: "ev", e: "ride_end", reason}` (iki dosyaya), sürüş kapanır, `ride.active.v1` silinir, boşaltma.
- Olaylara göre (`event`): `route` önce açık sürüşü `new_route` ile kapatır; `route`, `shift_day_start`, `shift_order_start`, `shift_leave_restaurant`, `shift_home_return_start` sürüş açar (`reason` = olay adı; açık sürüş varsa bir şey olmaz); olay satırı yazılır; ardından `arrive`, `route_clear`, `shift_day_end`, `shift_arrive_restaurant`, `shift_arrive_customer`, `shift_delivered`, `shift_order_cancel`, `shift_home_return_arrived` sürüşü kapatır (`reason` = olay adı). `reroute`, `mode`, `route_end` sürüşü açmaz ve kapatmaz.
- Süreç yeniden başlarsa `ride.active.v1` (kalıp `^\d{4}-\d{2}-\d{2}_ride_\d+\.jsonl$`) okunur ve aynı sürüş dosyasına devam edilir (`:80-83`).

### 12.4 Kayıt deposu (`RideLogStore`, `log_store.dart`)

- **Ad kalıbı** (`:18-19`): `^(\d{4}-\d{2}-\d{2})(?:\.jsonl|(?:_\d{4})?\.gun\.json|_ride_(\d+)\.jsonl|_route_(\d+)\.json)$`. Tür: `_route_` → `route`, `_ride_` → `ride`, `.gun.json` → `report`, diğer → `day`. Başlangıç: sürüş ve rotada damga (ms), raporda `_HHMM` → o günün HH:MM'si (yerel saat), gün dosyası ve eski `YYYY-MM-DD.gun.json`'da yok. Kalıba uymayan dosyalar listelenmez. Sıra: ada göre azalan; `groupByDay`: güne göre azalan.
- **Silme** (`delete`, `:52-61`): yalnız bu klasördeki, kalıba uyan, bağlantı (symlink) olmayan normal dosya; değilse `ArgumentError("Geçersiz kayıt dosyası")` (`log_store_test` 3). `deleteDay(day)`: gün `YYYY-MM-DD` değilse hata; o günün bütün kayıtları.
- **Etkin kayıt** (`RideLogger.isActive`, `:229-230`): ayar açık, gün var ve ad açık sürüşün ya da `<dayDate>.jsonl` ise silinemez (`StateError("Aktif kayıt silinemez")`); gün dosyası gün boyu etkindir. `deleteDay` açık günün tarihiyse hata (önce gün kapanır, 12.9).
- **Dışa aktarma** (`export`, `:67-87`): seçilen günün (ya da hepsinin) kayıtları tek JSON'da: `{"format": "duzyol-journal-v1", "day": <gün | null>, "files": [{"name", "day", "kind", "content"}]}`; `content` dosyanın ham metni (aynen), sıra listeleme sırası; dosya `cache/exports/<µs>/YolGo_<gün | tum_kayitlar>.json`. **WEEM:** biçim adı ve alanlar aynen; dosya adı `WEEM_surus_<gün | tum_kayitlar>.json` (öneri, M-12-1); geçici klasör `cacheDir/exports/<µs>/`.

### 12.5 Rota kaydı (`RouteRecorder`, `duzyol-route-v1`)

- **Açılış** (`nav_controller.dart:913-927`): plan bacaksızsa kayıt yok. Yeni rota (`reason == "route"`) ya da kayıt yoksa önceki kayıt `new_route` ile biter ve yeni `RouteRecorder(startedAt = şimdi, mode, planned = bütün bacakların ride noktaları, destination = ilk bacağın hedefi, plannedM = rideM, plannedS = timeS)` açılır; yeniden hesapta `replanned(şimdi, reason, yeni çizgi)`.
- **Fix** (`_recordFix`, `:929-939`): plan yoksa ya da hesaplanıyorsa `off = null`, değilse ilk bacağın ride çizgisine yerel izdüşümle en kısa uzaklık (`_distanceToPolyline`, `:964-974`); `addFix(fix zamanı, lat, lon, doğruluk, hız (< 0 → 0), off)`; ortalama hızlar ekrana (`_AvgSpeed`, 7.11).
- **`addFix`** (`route_recorder.dart:60-102`): koordinat ya da doğruluk sonlu değil veya doğruluk > 35 → yok; zaman öncekinden ileri değil → yok. Önceki varsa `d`, `dt` (sn): `0 < dt ≤ 30`, `d/dt ≤ 16` ve `d ≥ max(2, 0,3 × doğruluk)` → `distM += d`, açık sapmaya `lenM += d`; `0 < dt ≤ 15` ve hız ≥ 1 m/sn → `movingS += dt`. İz noktası: ilk fix, ≥ 2 m ya da ≥ 5 sn sonra `[t ms, lat (6), lon (6), doğruluk (1)]`. Sapma (yalnız `off` var ve doğruluk ≤ 30): açık sapma yoksa `off > 45` ardışık sayaç, 2'de sapma açılır (`start = t`, `maxOff = off`, ilk nokta); açıkken nokta eklenir, `maxOff` güncellenir, `off ≤ 30` → kapanır (`end = t`). `offM = 45`, `backM = 30`.
- `replanned(t, reason, çizgi)` (`:104-110`): `reroutes += {t, reason}`, `plans += çizgi`, açık sapma kapanır, sayaç sıfır.
- `avgKmh(now)` = başlangıçtan ≥ 20 sn ve `distM ≥ 30` → `distM / sn × 3,6` (duruşlar dahil), değilse null; `movingKmh` = `movingS ≥ 20` ve `distM ≥ 30` → `distM / movingS × 3,6`.
- **Bitiş** (`_finishRecording`, `:941-959`; nedenler `new_route`, `arrive` (7.8), `clear` (rota temizleme)): ortalama hız ekranı sıfırlanır; olay `route_end {reason, dev (sapma sayısı), m (yuvarlak), avg? (1 ondalık)}`; `logging` açık ve iz ≥ 2 noktaysa dosya `logs/<bakuDay(başlangıç)>_route_<başlangıç ms>.json` = `toJson(şimdi, reason)`. Dosya gün kapalıyken de yazılır (`route_end` olayı ise gün yoksa düşer).
- **Biçim** (`toJson`, `:120-142`; açık sapma `end` ile kapanır): `{"format": "duzyol-route-v1", "start", "end", "reason", "mode", "to": [lat, lon] (6), "planned": {"m", "s"} (yuvarlak), "driven": {"m", "s" (= end − start sn), "movingS", "avgKmh"?, "movingKmh"? (1 ondalık)}, "deviations": [{"start", "end"?, "m", "maxOff", "pts": [[lat, lon]]}], "reroutes": [{"t", "reason"}], "plans": [[[lat, lon], …], …], "track": [[t, lat, lon, doğruluk], …]}`. Adres metni yok (yalnız koordinat).
- **WEEM:** `logs.RouteRecorder` aynen (saf); `NavRuntime` açar, besler, bitirir (7.16); dosyayı `RideLogRuntime` yazar (`.tmp` + `rename`, 14.4).

### 12.6 Gün raporu (`.gun.json`) ve gün geçmişi

- **Dosya** (`_reportFile`, `:297-304`): `logs/<bakuDay(başlangıç)>_<HHmm, Bakü saati>.gun.json` (başlangıç yoksa şimdi). **WEEM:** `files/map/logs/<LogTime.day(başlangıç)>_<HHmm yerel>.gun.json`.
- **Yazım** (`writeReport`, `:306-319`): sıralı (önceki yazım bitince); gün silinmişse (`shift.deleted.days.v2`; başta ve dosya hazırlandıktan sonra iki kez bakılır) yazılmaz; içerik `DayState.toJson()` + `"summary": summarize(gün)` + `"lightsLearned": LightsDb.confirmed`, bir boşluk girintili JSON; hata sessiz. Çağıranlar: gün sonu (11.14), `reviewLight` (11.11), paylaşma.
- **`summarize(d)`** (`:330-352`): `totalS` = (bitiş ?? şimdi) − başlangıç (sn; başlangıç yoksa 0) · `movingS` (yuvarlak) · `km` = `distM / 1000` (2 ondalık) · `homeKm` = (`homeDistM` + süren eve dönüşün `distM − homeStartM`'i) / 1000 (2 ondalık) · `homeS` = `homeTimeS` + süren eve dönüşün süresi · `stops` (bütün duruşlar) · `lights` (`isLight`) · `lightWaitS` (ışık sürelerinin toplamı) · `lightCandidates` · `orders` (iptaller dahil) · `delivered` (`deliveredAt` olanlar) · `restWaitS`, `custWaitS` (teslim edilenlerin bilinen bekleme toplamları).
- **Paylaşma** (`shareReport`, `:321-328`): önce yazılır; paylaşım sayfası `application/json`, metin "<Ad> gün özeti <dosya adı>" / "<Ad> günün xülasəsi <dosya adı>" (`<Ad>` YolGo'da `AppInfo.name`; WEEM'de "WEEM").
- **Geçmiş** (`history`, `:571-593`): süren gün (varsa) + `logs/`'taki en yeni 60 `.gun.json` (ada göre azalan; okunamayan atlanır) + son biten gün (`shift.last`); başlangıç ms'ine göre tekil (ilk gelen kazanır: süren gün, dosya, `shift.last`); başlangıca göre azalan (başlangıçsız en sona).
- **WEEM:** `stats.DayStats.summarize(DayState, nowMs)`, `stats.DayReportFile` (ad, kodek), `stats.DayHistory.merge(active, files, last)`; Android `DayReportWriter` (sıralı yazım, `.tmp` + `rename`).

### 12.7 İstatistik ekranı (Y17)

- **Açılış:** gün kartının "İstatistik" düğmesi (6.12; WEEM `MapShiftActions`); elle gün bitişinden sonra o güne odaklı (11.14). Başlık "İstatistik" / "Statistika", yenile düğmesi (geçmişi yeniden okur); süren gün canlı (motor değiştikçe yeniden çizilir).
- **Sekmeler:** her gün bir sekme (kaydırılabilir, başa hizalı). Etiket: başlangıç yoksa `#<sıra>`; süren gün ya da bugün (telefon takvimi) → "Bugün HH:MM" / "Bu gün HH:MM"; değilse "<gün> <ay> HH:MM" (ay TR `Oca Şub Mar Nis May Haz Tem Ağu Eyl Eki Kas Ara`, AZ `yan fev mar apr may iyn iyl avq sen okt noy dek`); saat telefon yerel saati. Odak: başlangıcı eşleşen sekme. Gün yoksa "Henüz gün kaydı yok. Gün evden çıkınca kendiliğinden başlar; km, süre, siparişler ve trafik ışıkları burada toplanır." / "Hələ gün qeydi yoxdur. Gün evdən çıxanda özü başlayır; km, vaxt, sifarişlər və işıqforlar burada toplanır."
- **Gün sayfası** (`_DayView`, `:101-238`), yukarıdan aşağı:
  1. Başlık satırı: takvim günü `YYYY-MM-DD` (`bakuDay(başlangıç ?? şimdi)`), alt satır `dayReasons` (`day-reasons`; yoksa yok), sağda "Bu günü sil" düğmesi (12.9).
  2. Kutular: "toplam süre" / "ümumi vaxt" (`fmtDur(totalS)`), "km" (`summary.km`), "hareket" / "hərəkət" (`fmtDur(movingS)`); "teslim / sipariş" / "çatdırılıb / sifariş" (`<teslim>/<sipariş>`, yeşil), "restoranda bekleme" / "restoranda gözləmə", "kapıda bekleme" / "qapıda gözləmə"; eve dönüş varsa (`homeS > 0` ya da `homeKm > 0`) "eve dönüş km" / "evə qayıdış km" (mavi), "eve dönüş süresi" / "evə qayıdış vaxtı".
  3. "TRAFİK IŞIKLARI" / "İŞIQFORLAR": "ışıkta durma" / "işıqforda dayanma" (sayı, turuncu), "toplam bekleme" / "cəmi gözləmə" (turuncu), "ortalama / en uzun" / "orta / ən uzun" (`fmtDur(ortalama)` / `fmtDur(en uzun)`; ortalama tam sayı bölmesi). Işık varsa 260 px yükseklikte mini harita (`_LightsMap`, `:240-283`: OSM karoları, döndürme kapalı; tek ışıkta nokta ile (+0,002°, +0,002°) kutusu, çoğunda bütün noktalar, kenar boşluğu 36, en çok z 17; her ışıkta `fmtDur` etiketi (siyah %90 zemin, sarı `#FFB13D` 11 pt) ve turuncu `#FF9F1C` ışık simgesi; nokta = ışık pini, yoksa duruş) ve liste (sokak adı: yol grafiğinde 40 m içindeki en yakın kenarın adı, yoksa `lat, lon` 4 hane; alt satır başlangıç saati; sağda süre). Işık yoksa "Bu gün trafik ışığında bekleme kaydedilmedi." / "Bu gün işıqforda gözləmə qeyd olunmayıb."
  4. Aday varsa "EKSİK IŞIK KONTROLÜ" / "ÇATIŞMAYAN İŞIQFORLAR" + açıklama "Bu duruşlar ışık olabilir. Onayladığın noktalar sonraki sürüşlerde hatırlanır. "?" işaretliler ışıkta 2 dk'dan uzun beklemeler: Onayla → ışık sayılır, Reddet → sayılmaz." / "Bu dayanmalar işıqfor ola bilər. Təsdiqlədiyin nöqtələr növbəti sürüşdə yadda qalır. "?" olanlar işıqforda 2 dəq-dən uzun gözləmələrdir: Təsdiqlə → sayılır, Rədd et → sayılmır."; her aday: başlık (`longWait` ise "? " + sokak), alt satır "HH:MM · <süre>" (+ " · ışıkta 2 dk'dan uzun" / " · işıqforda 2 dəq-dən uzun"); yeşil onay düğmesi (ipucu `longWait` ? "Onayla" / "Təsdiqlə" : "Işık vardı" / "İşıqfor var idi") → `reviewLight(true)`; gri ret düğmesi ("Reddet" / "Rədd et" : "Işık değildi" / "İşıqfor deyildi") → `reviewLight(false)` (11.11).
  5. Sipariş varsa "SİPARİŞLER" / "SİFARİŞLƏR": her siparişte simge (iptal gri çarpı, değilse yeşil onay), "#n · HH:MM · x.x km · <toplam süre>", alt satır "restoran <bekleme | –> · kapı <bekleme | –>" / "restoran … · qapı …".
  6. Gün kapalıysa "Günü paylaş" / "Günü paylaş" (12.6).
- **Biçimler** (`shift_widgets.dart:15-20`): `fmtDur(s)`: < 60 → "N sn" / "N san"; saat varsa "H sa M dk" / "H saat M dəq"; değilse "M dk" / "M dəq". `fmtClock(t)` = `HH:MM` (telefon yerel saati).
- **`dayReasons(d)`** (`:285-294`): sırayla `home_departure` → "Evden çıkınca başladı" / "Evdən çıxanda başladı", `rawstats_shift_start` → "Rawstats vardiyasıyla başladı" / "Rawstats növbəsi ilə başladı", `home_arrival` → "Eve dönünce bitti" / "Evə qayıdanda bitdi", `rawstats_shift_end` → "Rawstats vardiyası bitince bitti" / "Rawstats növbəsi bitəndə bitdi"; " · " ile birleşir; hiçbiri yoksa null.
- **WEEM:** `:app` `StatsScreen` (`ScrollableTabRow`), `DayStatsPage`, `LightsMiniMap` (MapLibre raster stili, S4; karo kuralları 6.4); saf `stats.StatsTabs`, `stats.LightStats` (sayı, toplam, ortalama, en uzun), `stats.DayReasons`, `stats.DurationText`, `stats.StreetLabel` (yol grafiği yoksa koordinat). WEEM `rawstats_*` nedeni üretmez; bu iki metin yalnız içe aktarılan eski raporlarda görünür (A7.1, 12.15).

### 12.8 Gün kartı içerik satırları (`shift_widgets.dart:185-272`; kart düzeni ve aşama metinleri 6.12)

- **Üst satır:** gün kapalı → "Gün kapalı · evden çıkınca otomatik başlar" (6.12); kart katlı ve sipariş varsa "#n <aşama metni>" (eski kayıtta + " · +<sıradaki sayısı>"), aşama rengiyle; değilse "<fmtDur(gün süresi)> · <km, 1 ondalık> km" (katlıyken teslim varsa + " · 📦<teslim>"). Solda 8 px nokta (gün açık yeşil, değilse gri); sağda üç düğme (6.12).
- **Açık kartta:** gün açık ya da son gün varsa "Tamamlanan sipariş: N" / "Tamamlanan sifariş: N" (`completed-orders`; teslim edilmiş ve iptal olmayan farklı sipariş numaraları; gün kapalıysa son günün); gün açık ve sipariş yoksa "Sipariş yok · Rawstats'ta sipariş başlayınca burada görünür" / "Sifariş yoxdur · Rawstats-da sifariş başlayanda burada görünəcək"; ışık sayacı (11.9); süren sipariş satırı (`_OrderRow`: aşama simgesi, metni ve rengi; sağda aşamanın başından beri `fmtDur` — `atRestaurant` → restorana varış, `readyForCustomer` / `toCustomer` → restorandan çıkış, `atCustomer` → müşteriye varış, diğer → sipariş başı); eski kayıtta sıradaki sipariş satırları ("#n · Alındı" / "Götürüldü" ya da "Alış bekliyor" / "Götürülmə gözləyir").
- **Simge ve renk** (`_phaseInfo`, `:185-193`): `toRestaurant` ve `toCustomer` scooter simgesi mavi, `atRestaurant` restoran simgesi yemek rengi, `readyForCustomer` onay yeşil, `atCustomer` kapı pembe, `none` çanta gri ("Sipariş" / "Sifariş").
- **WEEM (SORU-N7 önerisiyle):** bu satırlar `:app` `ShiftCard`'a `MapShiftActions` ile eklenir: km satırı `DayView`'dan (vardiya süresi `ShiftCard`'da zaten varsa tekrarlanmaz, A6.5'te birleştirilir), ışık sayacı, aşama satırı (`:orders` metinleriyle aynı dört metin, 6.12), "Tamamlanan sipariş". **Metin onayı (M-12-2):** "Sipariş yok · Wolt/Bolt'ta sipariş başlayınca burada görünür" / "Sifariş yoxdur · Wolt/Bolt-da sifariş başlayanda burada görünəcək" (Rawstats WEEM'in içinde). Elle bitişten sonra İstatistik'in o güne odaklı açılması ve rotanın temizlenmesi `ShiftCard`'ın bitir eylemine bağlanır (11.14).

### 12.9 Gün silme (`DayDeletion`, `forgetDay`)

- **Girişler:** İstatistik gün sayfasındaki "Bu günü sil" ve Sürüş kayıtları'ndaki "Günün tamamını sil" (12.10); ikisi farklı onay metinleriyle aynı `DayDeletion.delete`'i çağırır.
- **Onay** (`confirmDeleteDay`, `day_deletion.dart:56-70`): başlık "<YYYY-MM-DD> · günü sil" / "<…> · günü sil"; metin "Bu tarihin sürüş, sipariş, olay, istatistik ve otomatik yol ölçümleri silinir. Diğer günler ve elle kaydedilen yollar korunur." / "Bu tarixdəki sürüş, sifariş, hadisə, statistika və avtomatik yol ölçmələri silinir. Başqa günlər və əl ilə qeyd olunan yollar saxlanır." + gün açıksa "\n\nAktif gün durdurulacak." / "\n\nAktiv gün dayandırılacaq."; "Vazgeç" / "Ləğv et", "Bu günü sil" / "Bu günü sil"; silindikten sonra İstatistik kapanır.
- **`delete(date)`** (`:21-53`; aynı anda bir kez): (1) açık günün takvim günü buysa gün kimliği alınır, `endDay()` (11.14), telemetri kapatılır, rota temizlenir; (2) kayıtlar boşaltılır; (3) o tarihin rapor kayıtlarından gün kimlikleri (`dayId`, yoksa `yolgo-<start>`; okunamayan atlanır); (4) Y13 yüzey deposu (`deleteDate`) ve Y12 gün katkıları (`deleteContributions(date)`, 9.10) silinir, döndükleri kimlikler eklenir; (5) `forgetDay(date, ids)`; (6) her kimlik için Y13 `deleteDay`; (7) `RideLogger.deleteDay(date)` (o tarihin bütün dosyaları).
- **`forgetDay(date, ids)`** (`shift_controller.dart:164-176`): açık gün bu tarihteyse `StateError`; kimlikler `shift.deleted.days.v2`'ye eklenir (son gün o tarihteyse onun kimliği de); bekleyen rapor yazımları beklenir; son gün o tarihteyse `lastDay = null`, `shift.last` silinir. Silinen gün kimlikleri bir daha rapor yazdırmaz ve ışık cevabı almaz (11.11).
- **WEEM:** `stats.DayDeletionPlan` (saf: tarih, açık gün, rapor kimlikleri → adımlar) + Android `DayDeletionRuntime` (Y13 adımları yok; Y12 adımı `RoadsRuntime.deleteContributions(date)`, 9.12). Açık gün silinirse yalnız `:map` günü kapanır (`DayController.endDay(reason = "manual")`): vardiya ve Siparişler sürer, gün bu vardiyada yeniden açılmaz (`ShiftFollower`, 11.2). Silme yalnız `:map` verisini siler (sürüş kayıtları, gün raporu, Y12 katkıları, `shift.last`); Siparişler'in gün klasörü (`files/logs/<gün>/`, R12) ve kazanç kayıtları **silinmez**. **Metin onayı (M-12-3):** onay metni "Bu tarihin harita kayıtları (sürüşler, gün özeti, olaylar, otomatik yol ölçümleri) silinir. Siparişler ve kazanç kayıtları, diğer günler ve elle kaydedilen yollar korunur." / "Bu tarixin xəritə qeydləri (sürüşlər, günün xülasəsi, hadisələr, avtomatik yol ölçmələri) silinir. Sifarişlər və qazanc qeydləri, digər günlər və əl ilə qeyd olunan yollar saxlanır."; "Aktif gün durdurulacak." → "Haritanın aktif günü durdurulacak; vardiya sürer." / "Xəritənin aktiv günü dayandırılacaq; növbə davam edir." Sürüş kayıtları'ndaki ikinci onay metni (12.10) de aynı içerikle.

### 12.10 Sürüş kayıtları ekranı (Y18, `ride_logs_screen.dart`)

- **Giriş:** Ayarlar → Yollarım → "Sürüş kayıtları" / "Sürüş tarixçəsi" (alt yazı "Günlere göre · ayrı sürüşler · kaydet ve paylaş" / "Günlər üzrə · ayrı sürüşlər · saxla və paylaş" + durum satırı; `settings_screen.dart:251-260`). WEEM: Harita ayarları → Yollarım → Sürüş kayıtları (6.9, 15.4).
- **Durum** (`rideLogStatus`, `:17-19`): ayar kapalı → "Kayıt kapalı" / "Qeyd bağlıdır"; gün açık → "● Kayıt sürüyor" / "● Qeyd gedir" (kırmızı); değilse "Gün başlayınca kaydedilir" / "Gün başlayanda qeyd olunur" (gri). Anahtar `ride-log-status`.
- **Üst çubuk:** başlık + durum; yenile ("Yenile" / "Yenilə"); indirme menüsü (ipucu "Tüm günlüğü kaydet" / "Bütün tarixçəni saxla"): "Tüm günlüğü tek dosyada kaydet" / "Bütün tarixçəni bir faylda saxla", "Tüm günlüğü paylaş" / "Bütün tarixçəni paylaş" (ikisi de gün seçmeden dışa aktarma).
- **Gövde** (aşağı çekince yenilenir): anahtar "Sürüş kaydı" / "Sürüş qeydi" (alt yazı "GPS, yükseklik ve olaylar telefonda saklanır." / "GPS, hündürlük və hadisələr telefonda saxlanılır."; `logging`); açıklama "Gün ve sipariş başlangıcı veya yeni rota ayrı sürüş açar; varışta kapanır. Günlük dosya tüm hareketleri içerir. Devam eden kaydı silmek için önce kaydı durdur." / "Günə və sifarişə başlayanda, yaxud yeni marşrut seçəndə ayrıca sürüş açılır; ünvana çatanda bitir. Gündəlik faylda bütün hərəkətlər saxlanılır. Aktiv qeydi silmək üçün əvvəlcə qeydi dayandır."; iş sürerken ilerleme çubuğu; günler (ilk gün açık, katlanır kart): başlık `GG.AA.YYYY`, alt satır "N sürüş · M kayıt" / "N sürüş · M qeyd"; gün menüsü (ipucu "Günün tamamını kaydet" / "Bütün günü saxla"): kaydet, paylaş, "Günün tamamını sil" / "Bütün günü sil" (kırmızı). Kayıt satırları: simge türe göre; başlık `route` "Rota · plan, iz ve sapmalar · HH:MM:SS" / "Marşrut · plan, iz və kənara çıxmalar · HH:MM:SS", `ride` "Sürüş · HH:MM:SS", `report` "Gün özeti" / "Günün xülasəsi" (+ " · HH:MM:SS"), `day` "Günlük hareket kaydı" / "Gündəlik hərəkət qeydi"; alt satır "x.xx MB" (+ " · kayıt sürüyor" / " · qeyd davam edir"); menü "Telefona kaydet" / "Telefonda saxla", "Paylaş" / "Paylaş", etkin değilse "Bu kaydı sil" / "Bu qeydi sil" (kırmızı). Boş: "Henüz sürüş kaydı yok" / "Hələ sürüş qeydi yoxdur"; okuma hatası: "Kayıtlar okunamadı" / "Qeydlər açılmadı".
- **Eylemler** (`_act`, `:55-105`; aynı anda bir): gün silme — başlık "<GG.AA.YYYY> · Günü sil?" / "<…> · Günü silmək istəyirsən?", metin (gün açıksa "Bu gün aktif: kayıt ve bildirimler durdurulacak.\n\n" / "Bu gün aktivdir: qeyd və bildirişlər dayandırılacaq.\n\n") + "Bu günün sürüşleri, siparişleri, olayları, istatistikleri ve otomatik yol kalitesi katkıları silinir. Diğer günler ve elle işaretlenen yollar korunur." / "Bu günün sürüşləri, sifarişləri, hadisələri, statistikası və avtomatik yol keyfiyyəti töhfələri silinir. Digər günlər və əl ilə işarələnən yollar saxlanılır.", "Vazgeç" / "Ləğv et", "Günü sil" / "Günü sil" → 12.9; tek kayıt silme — "Bu kaydı sil?" / "Bu qeydi silmək istəyirsən?", "Yalnız bu kayıt silinir. Diğer sürüşler ve gün özeti korunur." / "Yalnız bu qeyd silinəcək. Digər sürüşlər və günün xülasəsi saxlanılacaq.", "Vazgeç", "Sil" / "Sil" (kırmızı); kaydet / paylaş — tek kayıtta dosyanın kendisi, gün ya da hepsi için dışa aktarma dosyası (12.4); kaydet → belge seçici (`saveFile`, 6.13) ve "Dosya kaydedildi" / "Fayl saxlanıldı"; paylaş → MIME `.jsonl` için `text/plain`, değilse `application/json`, metin "<Ad> sürüş kaydı" / "<Ad> sürüş qeydi". Hata: "İşlem tamamlanamadı. Tekrar dene." / "Əməliyyat tamamlanmadı. Yenidən cəhd et."
- **WEEM:** `:app` `RideLogsScreen` (Compose; kaydet `ActivityResultContracts.CreateDocument`, paylaş `FileProvider`); `logs.RideLogStatus`; `<Ad>` = WEEM; gün silme metinleri M-12-3 ile; `saveFile` hata metinleri iki dilli (6.13).

### 12.11 Yerel telemetri servisi (YolGo `TelemetryService`) → WEEM'de kalkar

- **YolGo'da** (`TelemetryService.kt`, `TelemetryBridge.kt`, `native_telemetry.dart`): gün açıkken ön plan servisi (bildirim 7400, kanal `yolgo_recording` "YolGo aktif gün", başlık "YolGo · Aktif gün", metin "Konum ve uygun sipariş etapları kaydediliyor"); Fused 500 ms konum (hata olursa `LocationManager` GPS + ağ 1 sn); barometre (200 ms); Y13 için ivme / dönüş vektörü / jiroskop / adım sensörleri ve etkinlik tanıma (`MotionReceiver`); `latest` (son fix + basınç) Flutter'a 300 ms (önde) / 1 sn yoklamayla; kalıcı günlük `files/telemetry-v2.jsonl` (`TelemetryJournal`, ≤ 3000 satır, Flutter onayladıkça baştan silinir) yalnız Y13 `eligible` iken `evidence` / `window` satırları ile `boundary` / `status` satırları; tercih dosyası `yolgo_runtime_v2` (`day`, `leg`, `eligible`, `paused`, `rawDebug`, `calibrationLabel`, `profile`, `boot`); `configure(day: null)` → servis durur, 7400 ve 7100–7310 bildirimleri silinir, `duzyol_weather_v1` iptal edilir; `RawDiagnostics` (Y14). Sürüş günlüğü bu günlüğü **kullanmaz**: `RideLogger` kendi dosyalarına yazar; servisten yalnız konum (`LocationService`) ve basınç (`SensorHub`) okunur.
- **WEEM 1.0:** yazılmaz. Konum: `NavLocation` (Fused 500 ms) + gün açıkken `:core` `ShiftService` ön plan servisi (7.16). Barometre: `:map` `BatteryRuntime`'ın `SensorManager` okuması (10.13); sürüş günlüğünün `env` satırı ve Y9 yüksekliği aynı kaynaktan. Bildirim 7400 kullanılmaz (14.6). Gün sonu bildirim iptalleri `:core` vardiya sonu listesinde (CORE.md 3.3, 14.6), hava işi 10.8. Y13 sensörleri, `telemetry-v2.jsonl`, `yolgo_runtime_v2`, `MotionReceiver`, `RawDiagnostics` → sonra (Y13/Y14). **Karar (CORE.md 6.2 "A6.1 karar verir"):** `TelemetryJournalTest` (8) Y18'e çevrilmez; tek kullanıcısı Y13 olduğu için Y13 ile gelir. `native_telemetry_test` (1) çevrilmez (köprü yok; ilkesi `ShiftMachine` testinde, CORE.md 6.2).

### 12.12 Veri

| Ad | Tip · biçim | YolGo yeri | WEEM |
|---|---|---|---|
| `logs/YYYY-MM-DD.jsonl` | gün günlüğü (JSONL; `t`, `dayId`, `k` `gps` \| `env` \| `ev`) | `app_flutter/logs/` | `files/map/logs/`, aynı ad ve biçim |
| `logs/YYYY-MM-DD_ride_<ms>.jsonl` | sürüş (aynı satırlar, `ride_start` … `ride_end`) | aynı | aynı |
| `logs/YYYY-MM-DD_HHMM.gun.json` (eski `YYYY-MM-DD.gun.json` okunur) | gün raporu (`DayState` + `summary` + `lightsLearned`) | aynı | aynı |
| `logs/YYYY-MM-DD_route_<ms>.json` | rota kaydı `duzyol-route-v1` | aynı | aynı |
| dışa aktarma `YolGo_<gün \| tum_kayitlar>.json` | `duzyol-journal-v1` | `cache/exports/<µs>/` | biçim aynı; ad `WEEM_surus_<…>.json` (M-12-1) |
| `ride.active.v1` | String, açık sürüş dosya adı | Flutter tercihleri | `map` tercih dosyası |
| `shift.deleted.days.v2` | List<String>, silinen gün kimlikleri | aynı | `map`, JSON dizi dizgesi (14.1) |
| `settings.v1` → `logging` | bool, true | aynı | `MapSettings.logging` |
| sürüş günlüğü olayları (bu bölümün) | `ride_start {reason}`, `ride_end {reason}`, `route_end {reason, dev, m, avg?}` | `logs/` | aynı (dizin 14.5) |
| `files/telemetry-v2.jsonl`, `yolgo_runtime_v2`, bildirim 7400 `yolgo_recording` | yerel telemetri | native | **yok** (12.11) |

Gizlilik: günlükler ve raporlar tam izi (6 hane), restoran ve müşteri hedef noktalarını (`to`, `restTarget`, `customerTarget`, `custLat/Lon`) ve restoran adını taşır; adres metni, müşteri adı/telefonu ve ev koordinatı yoktur. Telefondan yalnız kullanıcı "kaydet" ya da "paylaş" deyince çıkar (YolGo davranışı). S22 haftalık yedeğe bugün girmez (SORU-12-3).

### 12.13 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| `log_store_test` | 7 | eski günlük, iki biçim gün özeti ve sürüş aynı güne gruplanır, `private.txt` dışarıda; `_1030` raporunun başlangıç saati 10; sürüşün başlangıcı var · tek kayıt silme öteki kayıtları korur · klasör dışındaki dosya silinemez (`ArgumentError`, dosya kalır) · tüm günlük tek dosyada, `content` ham metin aynen; gün seçimi süzer · rota değişimi ayrı sürüş açar: ilk sürüş `reroute`'u içerir, ikincisi içermez; gün dosyası 9 satır; sonda `ride.active.v1` yok · Bakü gece yarısında ve eşzamanlı boşaltmada iki olay başlangıç gününün dosyasında, ertesi günün dosyası yok · etkin sürüş silinemez (`StateError`), kayıt kapatılınca silinir, geriye yalnız gün dosyası | `logs.RideLogStoreTest` 4, `logs.RideLogWriterTest` 3 | A6.2b → A6.9 | `DateTime(2026,10,2,10)` yerel → sabit ms; gece yarısı testi sahte `Clock` (bölge `Asia/Baku`) ile |
| `ride_log_status_test` | 2 (döngüyle 4) | üç durumun TR/AZ çiftleri · durum yazısı (`ride-log-status`) üç durumda | `logs.RideLogStatusTest` 1, `:app` Robolectric `RideLogStatusTextTest` 1 | A6.2b → A6.9 | |
| `v140_features_test` (Y18) | 3 | sapma 2 fix sonra açılır, dönünce kapanır (`maxOff` 80), ortalama hız > 17, `duzyol-route-v1`, adres yok · yeniden hesap sapmayı kapatır, `plans` 2 · `_route_` → `route`, `_ride_` → `ride`, rota başlangıcı damgadan | `logs.RouteRecorderTest` 2, `logs.RideLogStoreTest` 1 | A6.2b → A6.9 | öteki 11 test 10.12 ve 11.16 |
| `home_day_controller_test` (5. test) | 1 | `dayReasons`: evden çıkış + eve dönüş; yalnız eve dönüş; elle → null; 1.5.0 kaydı (neden yok) → null | `stats.DayReasonsTest` | A6.2b → A6.9 | saf işlev; SORU-11-1'e bağlı değil |
| YK `TelemetryJournalTest` | 8 | baş satırlar sırayla · onay baştan siler, kuyruğu ve sırayı korur · yalnız baş taranır · boş ya da eski onay yeniden yazmaz · bozuk baş satırı onayda düşer · düşürülebilir satırlar sınırda durur, sınır satırları kalır · süreç yeniden başlayınca sayım · gün silme bütün dosyayı tarar | — | — | **sonra (Y13)** (12.11) |
| `native_telemetry_test` | 1 | gün kapandıktan sonra geç gelen `configure` yoklamayı açmaz | — | — | **çevrilmez** (12.11; CORE.md 6.2) |
| **Yeni** (karakterizasyon; eski test yok) | — | sürüş açan / kapatan olay kümeleri, `ride.active.v1` ile devam, gps 900 ms seyreltme, `env` satırı (3 sn tazelik, `vr`/`vm` 0), ayar kapanınca `logging_off` · gün raporu adı (yerel `_HHMM`), `summarize` alanları (süren eve dönüş dahil), silinmiş günün raporu yazılmaz · geçmiş birleştirme (60 dosya, tekil başlangıç, sıra) · sekme etiketleri (Bugün, ay adları TR/AZ), ışık istatistiği, süre biçimi, sokak etiketi · gün silme sırası (açık gün kapanır, Y12 katkıları, `forgetDay`, dosyalar; Siparişler'e dokunmaz) · `:app` İstatistik (bölümler, aday düğmeleri, paylaş yalnız kapalı günde) ve Sürüş kayıtları (gruplar, etkin kayıt silinemez, metinler) | `logs.RideLogWriterTest`, `stats.DayReportFileTest`, `stats.DayStatsTest`, `stats.DayHistoryTest`, `stats.StatsTabsTest`, `stats.DayDeletionPlanTest`, `:app` Robolectric `StatsScreenTest`, `RideLogsScreenTest` | A6.2b → A6.9 | değerler bu bölümden |

Toplam (bu bölüm): 22 test tanımı; çevrilen 13 (A6.2b), sonra 8 (Y13), çevrilmeyen 1.

### 12.14 WEEM yerleşimi

- `logic/map` **`logs`**: `RideLogWriter`, `RideLogNames` (kalıp, tür, başlangıç), `RideLogStore` (`java.io.File`; liste, silme, gün silme), `JournalExport` (`duzyol-journal-v1`), `RouteRecorder`, `RouteDeviation`, `RideLogStatus`. **`stats`**: `DayStats` (`summarize`), `DayReportFile`, `DayHistory`, `DayReasons`, `StatsTabs`, `LightStats`, `DurationText`, `StreetLabel`, `DayDeletionPlan`. Kullandıkları: `day` (`DayState`), `graph` (sokak adı), `geo`; `logic/core` `LogTime`, `Bilingual`.
- Android `:map`: `RideLogRuntime` (sürüş günlüğü, rota dosyası, `ride.active.v1`), `DayReportWriter`, `DayDeletionRuntime`; rota kaydının sürülmesi `NavRuntime`'da (7.16).
- `:app`: `StatsScreen`, `DayStatsPage`, `LightsMiniMap`, `RideLogsScreen`, `ShiftCard` satırları (12.8); girişler `MapShiftActions` "İstatistik" ve Harita ayarları → Yollarım → Sürüş kayıtları.
- **2 telefon:** gün motoru Receiver'da olduğundan (11.17) sürüş günlüğü, rota kayıtları, gün raporları ve İstatistik Receiver'dadır; Provider'da gün olmadığı için sürüş günlüğü yazılmaz (rota Provider'da açılırsa yalnız rota dosyası yazılır, `logging` açıksa). Provider'ın İstatistik ekranı SORU-11-2.

### 12.15 Farklar, açık noktalar ve sorular

- **Bilinen davranışlar (korunur):** satır zamanı kaydedicinin saati (11.18); `sp` −1 yazılabilir; `env`'de titreşim 0; gün dosyası gün boyu etkindir (silinemez); rota dosyası gün kapalıyken de yazılır (koşul yalnız `logging` ve ≥ 2 iz noktası), `route_end` olayı ise gün yoksa düşer; geçmiş en çok 60 rapor okur; günlükler otomatik silinmez (YolGo'da da yok).
- **WEEM farkları:** klasör `files/map/logs/`; takvim günü ve `_HHMM` telefon saat dilimiyle (`LogTime`, CORE.md 1.10; Bakü'de aynı sonuç); `<Ad>` = WEEM; dışa aktarma dosya adı (M-12-1); yerel telemetri servisi yok (12.11); gün silme yalnız harita verisini siler ve vardiyayı bitirmez (12.9, M-12-3); "Rawstats" metni (M-12-2); İstatistik mini haritası MapLibre (S4).
- **SORU-12-1 (CORE.md 7 (1); koordinatöre):** iki km ölçüsü bir uygulamada. (a) İkisi de eski yerinde: İstatistik ve gün kartı YolGo gün km'si (`:map`, 11.3), Siparişler gün sayfası ve `shift_end` olayı vardiya km'si (D8; scooter odometresi varsa o); (b) İstatistik'e ikinci kutu "vardiya km" (D8) eklenir, fark görünür; (c) tek km: her yerde D8 (harita km'si yalnız gün raporunda). Öneri (a): davranış iki eski uygulamayla aynı kalır, gölge mod ve A6.10 karşılaştırması kolay; fark sahada büyük çıkarsa (b).
- **YolGo sürüş kayıtlarını içe aktarma (soru değil; plan Bölüm 1 ve A7.1):** plan, eski YolGo'nun bugünkü dışa aktarmasını (`duzyol-journal-v1`; `VERI_SOZLESMESI` 7.3 "içe aktar"; YolGo'ya ek gerekmez) WEEM'e almayı kapsama almış; iş **A7.1** (A6.9'a bağlı). A6.9 için kural: WEEM dosya adları ve biçimleri eskisiyle aynı olduğundan içe aktarılan dosya WEEM'in kendi dosyası gibi okunur (eski `….gun.json` adı da, 12.4). A7.1 için öneri: Sürüş kayıtları'nda "YolGo kayıtlarını içe aktar" eylemi: belge seçiciyle `duzyol-journal-v1` dosyası; her `files[]` öğesi adı kalıba uyuyorsa ve aynı adlı dosya yoksa ham metniyle `files/map/logs/`'a yazılır (var olan dosya ezilmez); sonuç "N dosya eklendi, M atlandı"; içe aktarılan gün raporları İstatistik'te WEEM günleri gibi görünür (ışık cevabı `lights.v1`'e öğretir, `rawstats_*` neden metinleri gösterilir). Metinler ve test A7.1'de.
- **SORU-12-3 (koordinatöre; S22 yedek kapsamı; 17.1 #15'te SORU-Q5 ile birleşik):** `files/map/logs/` haftalık şifreli yedeğe girsin mi? Sürüş günlüğü sürüş saati başına ~0,8 MB (gps satırı ~125 bayt/sn, sürüşteyken iki dosyada), 10 saatlik gün ~7 MB. (a) Hepsi; (b) yalnız gün raporları (`.gun.json`, gün başına birkaç KB) ve rota kayıtları; (c) hiçbiri (bugünkü S22). Öneri (b).
- **M-12-1 (metin onayı):** dışa aktarma dosya adı `WEEM_surus_<gün | tum_kayitlar>.json`.
- **M-12-2 (metin onayı):** "Sipariş yok · Wolt/Bolt'ta sipariş başlayınca burada görünür" / "Sifariş yoxdur · Wolt/Bolt-da sifariş başlayanda burada görünəcək".
- **M-12-3 (metin onayı):** gün silme onay metinleri (12.9).
- **Risk:** sürüş günlüğü saniyede bir satır ve 5 sn'de bir diske ekleme yapar; pil ve depolama etkisi A10'da ölçülür (YolGo'da sorun bildirilmedi). Paylaşılan gün raporu ve rota kaydı müşteri hedef noktasını içerir (YolGo'da da öyle; `Redactor` kuralı yalnız ad ve telefon için) — bilerek korunur.
### 12.16 A6.9 uygulama notları (20 Ekim 2026)

- **Saf (`logic/map`):** `logs` — `RideLogWriter` (dosya başına tampon, `flush` iki kilitle sıralı ve hatada başa geri koyar; gün kapsamı `setDay`, `gps` 900 ms seyreltme, `env` 3 sn taze basınç, olay kümeleri 12.3, `ride.active.v1` `MapPrefs.remove` ile silinir), `RideLogStore` (kalıp, tür, `groupByDay`, silme, `deleteDay`, `export` → `WEEM_surus_<gün | tum_kayitlar>.json`), `RouteRecorder` (12.5 aynen; `off` null olabilir), `RouteRecording` (`NavRecording` uygulaması: yeni rota `new_route` ile bitirir, `route_end` olayı, dosya yalnız `logging` ve ≥ 2 iz noktasında, ortalama hız geri çağrısı), `DayLogBridge` (gün motoru adımları → `shift_<tür>`, `light_answer`/`stop_surface_override` öneksiz, `stop` kaydı düz alanlara açılır). `stats` — `DayStats.summarize`, `DayReportFile` (ad `<gün>_<HHmm>.gun.json`, kodek; `lightsLearned` onaylı ışık **sayısı**), `DayHistory` (≤ 60 dosya, tekil, azalan), `StatsTabs`, `LightStats`, `DurationText`, `StreetLabel`, `DayReasons`, `DayDeletionPlan`.
- **Android:** `:map` `RideLogRuntime` (yazıcı, konum, 1 sn `env`, 5 sn boşaltma, rota dosyası `.tmp` + `rename`, `routeSpeed` → `metric-avg`), `DayReportWriter` (gün sonu ve ışık cevabında sıralı yazım, silinmiş gün yazılmaz, `files()`/`history()`), `DayDeletionRuntime` (12.9; Y12 adımı `deleteContributions` A6.11'de takılır), `DayRuntime.addStepListener` (günlük, rapor, görev ve hatırlatıcı aynı adıma bağlanır), `NavRuntime` (`log`, `recording`, `lastFix`, `resolvePlace`); `WeemApplication` `NavRuntime`'ı süreçle birlikte kaldırır (vardiya arka planda sürerken gün motoru çalışsın). `:app` `StatsScreen`/`DayStatsPage`/`LightsMiniMap` (MapLibre OSM işaretleri; testte yer tutucu), `RideLogsScreen`, `MapDataHosts` (kaydet/paylaş `Downloads` → `ACTION_SEND`; belge seçici yerine İndirilenler/WEEM, `Downloads` ile tutarlı), girişler Harita ayarlarında; elle gün bitişinden sonra İstatistik o güne odaklı açılır ve rota temizlenir (`MapScreen`).
- **Farklar:** sürüş kayıtlarının "kaydet" eylemi `CreateDocument` yerine `Download/WEEM`'e yazar; "Yollarım → Sürüş kayıtları" yolu A6.11'in Yollarım ekranı gelene dek Harita ayarlarında doğrudan satır; `DayDeletionRuntime` açık günü `DayRuntime.endDay` ile kapatır (vardiya ve Siparişler sürer).

<!-- END sec:12 -->

<!-- BEGIN sec:13 -->
### 12.17 A7.1 YolGo kayıtlarını içe aktarma (20 Ekim 2026)

- **Akış:** Sürüş kayıtları → üst menü (⤓) → "YolGo kayıtlarını içe aktar" → belge seçici (`OpenDocument`, `*/*`) → YolGo'nun "Tüm günlüğü paylaş/kaydet" dosyası (`YolGo_<gün | tum_kayitlar>.json`, `duzyol-journal-v1`, 12.4). Sonuç satırı "N kayıt eklendi · M zaten vardı" (+ " · K okunamadı"); biçim yanlışsa hata "Bu dosya YolGo kayıt dosyası değil". WEEM'in kendi dışa aktarması (`WEEM_surus_…json`) da aynı yoldan geri yüklenir.
- **Kural (saf `logs.JournalImport`):** dosya akışla okunur (YolGo dışa aktarmayı akışla yazar, "tüm kayıtlar" çok büyük olabilir; her `content` doğrudan diske); önce `files/map/logs/.import-<ns>/` geçici klasörüne açılır, biçim `duzyol-journal-v1` ve belge eksiksizse her öğe ham metniyle aynı adla `files/map/logs/`'a taşınır (`Files.move`, ezmeden). Ad 12.4 kalıbına uymayan (yol kaçışı dahil), `content`'i metin olmayan öğe "okunamadı"; aynı adlı dosya varsa, dosyada ikinci kez geçiyorsa ya da etkin kayıtsa (açık günün `<gün>.jsonl`'u, açık sürüş; `RideLogWriter.isActive`) atlanır. `kind`/`day` alanları okunmaz (ad belirler). Yanlış biçim, bozuk ya da yarım dosyada klasöre hiçbir şey yazılmaz (`JournalFormatException`); yarım kalmış eski geçici klasör sonraki içe aktarmada silinir. Alan sırası ve bilinmeyen alanlar fark etmez.
- **Sonuç:** içe aktarılan gün raporları İstatistik'te (≤ 60, 12.6) ve Sürüş kayıtları'nda WEEM günleri gibi görünür (eski `….gun.json` adı da); `lights.v1`, Y12 ve diğer YolGo verisi aktarılmaz (S1). Bilinen: içe aktarılan gün dosyası WEEM'in o günkü kaydıyla birleşmez (aynı ad varsa atlanır).
- **Testler:** `logic/map` `JournalImportTest` 12; `:app` `RideLogsScreenTest` +1 (menü + sonuç metni).
- **M-12-4 (metin onayı, AZ):** "YolGo qeydlərini köçür", "%d qeyd əlavə olundu · %d artıq var idi", " · %d oxunmadı", "Bu fayl YolGo qeyd faylı deyil".

## 13. Y19 Teklif veri seti ekranı ve Y20 Görev kartları, hatırlatıcılar (A6.9)

### 13.1 Kaynak ve kapsam

| YG dosya | Satır | İçerik | WEEM |
|---|---|---|---|
| `lib/economics/dataset_runtime.dart` | 18–21 · 25 · 29–119 | `DatasetClient` (kanal `yolgo/offer-dataset`) · `datasetEnrichFallback` 60 sn · `datasetRuntimeProvider` (zenginleştirme işçisi, vardiya tetikleri) | zenginleştirme → `logic/orders` `dataset.DatasetEnricher` + `:core` `RouteMeasurePort` (`:map` uygular); tetikler iç akış (13.11) |
| `lib/economics/dataset_days.dart` | 5–16 · 18–29 | `datasetDays` (vardiyanın değdiği takvim günleri) · `datasetShift` (vardiya satırı + bitişte toplama) | `dataset.DatasetDays`, `dataset.DatasetShifts` |
| `lib/ui/dataset_screen.dart` | 12–113 | `DatasetScreen` (gün listesi, Günü topla, Başka gün, Elle ekle, Paylaş) | `:app` `DatasetScreen` (Harita ayarları → "Teklif veri seti", 6.9) |
| `lib/ui/dataset_manual.dart` | 5–11 · 13–28 · 30–79 | `datasetManualLabels` · `addDatasetManual` · `_ManualDialog` | `:app` `DatasetManualSheet`; doğrulama `dataset.DatasetManual` |
| `android_overlay/DatasetBridge.kt` | 14–67 · 68–79 | kanal yöntemleri (`configure`, `pending`, `list`, `manual`, `collect`, `seal`, `shift`, `enrich`, `pickScreenshot`) · `pending` | kanal **kalkar** (Kotlin içi çağrı); `pending` → `DatasetEnricher` |
| `android_overlay/DatasetCapture.kt` | 21–122 · 123–135 | okuma işçisi (duyuru + yedek yoklama), satır yazımı · `cachedWeather` | iç gözlemci `DatasetRecorder` (`OrderEvents.offers`); yoklama **çevrilmez** |
| `android_overlay/DatasetContract.kt` | 15–24 · 28–40 · 41–124 · 126–173 | E8 sabitleri · gün/klasör (Asia/Baku) · ortak alanlar, nokta, koordinat/Plus Code, teklif satırı · ölçü, parmak izi, `flagUnknowns` | `logic/orders` `dataset.DatasetContract` (yoklama sabitleri hariç) |
| `android_overlay/DatasetFiles.kt` | 9–26 | kök, gün klasörü, satır ekleme (5 dosya adı), satır okuma | `dataset.DatasetFiles` (saf) + Android `:orders` kök yolu |
| `android_overlay/DatasetManual.kt` | 12–80 | ekran görüntüsü seçici (opak kopya), haftalık ödeme / vardiya notu kaydı | `dataset.DatasetManual` (saf doğrulama) + `:app` belge seçici |
| `android_overlay/DatasetPack.kt` | 17–80 · 81–109 · 110–129 · 130–172 · 173–202 · 203–213 | v3 gün toplama · ham klasör değişimi, satır sayımı · eşleştirme · gün özeti · mühür ve ZIP · liste | `dataset.DatasetPack` (saf); v3 sağlayıcı okuması yerine `DayLogSnapshot` (13.6) |
| `android_overlay/DatasetReads.kt` | 11–42 · 49–64 | `OfferRowGate` · `PollRowGate` | `OfferRowGate` aynen; `PollRowGate` **çevrilmez** |
| `android_overlay/DatasetRecordingService.kt` | 79–121 | ön plan servisi 7401, `offer_dataset_v1.active` | **kalkar** (öneri, 13.11): kayıt `:core` vardiya servisinin sürecinde |
| `android_overlay/AndroidManifest.xml` | 9–14 · 140–142 | izinler (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`) · servis (`specialUse`) | servis ve `SPECIAL_USE` izni gerekmez (13.11) |
| `android_overlay/MainActivity.kt` | 90 · 289–291 | `DatasetBridge.install` · `onActivityResult` → `DatasetManual.reply` | `:app` `ActivityResult` sözleşmesi |
| `tools/veri_seti/README.md`, `kontrol.py` | 1–12 · 1–411 | çevrimdışı ZIP denetimi (öz-test 8) | WEEM `tools/veri_seti/` (13.10) |
| RS `LogFeedV3.kt` | 13–26 · 29–207 | `DailyLogSecret` · v3 gün anlık görüntüsü (`build`, `common`, `manifest`, `page`) | `logic/orders` `dataset.DayLogSnapshot`, `DailyLogSecret` (13.6) |
| RS `LogFeedV3Android.kt` | 13–76 | sağlayıcı yapıştırıcısı (`economics.share`, kilit altında dondurma, önbellek, sayfa) | sağlayıcı **kalkar** (S7); dondurma kuralı `:core` `Store` kilidinde |
| `lib/tasks/daily_task.dart` | 3–28 · 32–65 | `DailyTask` · `TaskTrigger` | `logic/map` `tasks.DailyTask`, `tasks.TaskTrigger` |
| `lib/tasks/task_controller.dart` | 13–17 · 20–121 | `TaskView` · `TaskController` | `tasks.TaskController` (saf durum) + Android `:map` `TaskRuntime` |
| `lib/ui/tasks_screen.dart` | 12–46 · 48–129 · 132–175 | `TasksScreen` · `showTaskEditor`/`_TaskEditor` · `TaskAlertOverlay` | `:app` `TasksScreen`, `TaskEditorSheet`, `TaskAlertCard` |
| `lib/reminders/reminders.dart` | 19–145 · 147–183 · 187–227 · 231–351 | `ReminderKind`, `ReminderItem`, `ReminderSettings` · `reminderLateMin`, `reminderShowFor`, `fmtMinuteOfDay`, `reminderDue` · `ReminderSettingsController` · `ActiveReminder`, `ReminderController` | `logic/map` `reminders.*` (13.15–13.16) |
| `lib/ui/reminder_widgets.dart` | 11–15 · 20–59 · 62–80 · 82–230 | renk · `ReminderCard` · `ReminderSettingsSection` · `_ReminderTile` | `:app` `ReminderCard`, `ReminderSettingsSection` |
| `android_overlay/MainActivity.kt` | 145–170 · 259–274 · 383–390 · 392–431 | `openGoogleRoute` · `notify`/`cancelNotify` · `requestNotifPermission` · `reminderBuilder`/`showReminder` | `openGoogleRoute` **kalkar** (S9, 8.13); bildirim `NotifyPort` (`:core`) |
| `android_overlay/NotifyPolicy.kt` | 4–14 | `TIMEOUT_MS`, `inAppOnly`, `weatherDue` | `logic/core` `NotifyPolicy` (A2.4, bitti; CORE.md 3.1) |
| başvuru | — | `lib/tracking/shift_controller.dart:117–118, 662–668` (görevlere gün/sipariş/teslim bağı; Bölüm 11), `lib/ui/nav_screen.dart:60–152, 208–210` (kart yerleri, uzun basma menüsü; Bölüm 6), `lib/ui/settings_screen.dart:43–50` (ayar girişleri; 6.9), `lib/input/clipboard_sniffer.dart:186–225` (`NativeBridge.openGoogleRoute`, `notify`, `cancelNotify`, `requestNotifPermission`), `lib/economics/rawstats_signals.dart` ve `android_overlay/RawstatsObserver.kt`, `CourierFeedBridge.kt`, `CourierFeedSigner.kt` (iki uygulama sözleşmesi; Bölüm 17) | |

Bulgu doğrulaması: Y19 için ham bulgu yok; davranış kaynaktan çıkarıldı. `Y_tracking.md` §Y20 ve §G4 kaynakla uyuşuyor; düzeltmeler: **(bulgu düzeltmesi:** varsayılanlarda `onlyShift=false` yalnız yemekte; su ve kontrol `ReminderItem` varsayılanı `onlyShift = true` (`reminders.dart:29, 126–128`). "Açma/aralık/onlyShift/useTimes değişince sayaç sıfırlanır" yalnız kapalı → açık geçişte ve üç alan değişince (`:221–224`); açık → kapalı sıfırlamaz. "Rota Oluştur … `:97-121`": `dismiss` :97–107, `openRoute` :108–121. `showReminder` `MainActivity.kt:397–431`, yöntem tablosu `notify` :259–268. Görev bildirimi 7201 de `showReminder`'dan, kanal `hatirlatici` ile çıkar (ayrı kanal yok). Bölgeye "yeniden giriş" yalnız önceki fix dışarıdaysa (`before == false`) sayılır: uygulama bölge içinde açılırsa (`before == null`) kapatılmış görev yeniden girişle gelmez, ancak teslimle gelir (`daily_task.dart:52–59`).) `OZELLIK_HARITASI`'nda Y20 altında sayılan `courier_stage_follow_test` (14) Bölüm 11'in, `nav_assistant_test` (1) Bölüm 7.15'in tablosunda; burada yalnız başvurulur.

### 13.2 Y19: amaç ve akış

- **Amaç** (YolGo 1.7.2, 10 Ekim; E8 okuma ölçüsü 15 Ekim): ekonomi analizi için **gün başına çevrimdışı paket**. İçerik: YolGo'nun Rawstats teklif akışından gördüğü teklifler (`yolgo-live.jsonl`), yerel rota/yükseklik ölçümü (`yolgo-routes.jsonl`), mevcut hava önbelleğinden üç değer, okuma ölçüleri (`yolgo-poll.jsonl`), vardiya aralıkları (`shifts.jsonl`), Rawstats'ın anonim gün kayıtları (feed v3 → `rawstats/`), kullanıcının elle eklediği ekranlar ve notlar (`manual.jsonl`, `attachments/`), hepsinin SHA-256/satır manifesti ve günlük ZIP. Ağ çağrısı, OCR, karar ya da Wolt/Bolt'ta eylem yok (CHANGELOG:167).
- **Akış:**
  1. Vardiya açılınca: `configure(region, active = true)` → ön plan servisi; `datasetShift(start, null)` → `shifts.jsonl`'a açık satır; zenginleştirme taraması (13.4, 13.5).
  2. Vardiya boyunca servis işçisi Rawstats duyurusunda (≥300 ms arayla) ya da yedek yoklamada (60 sn; duyuru dinlenemiyorsa 7 sn) feed v2'yi okur, teklif satırlarını ve okuma satırını yazar (13.3); yeni satır Dart'a duyurulur → rota ölçülür (13.4).
  3. Vardiya bitince: kapanış satırı her güne; her gün için v3 toplama → `rawstats/`, `collection-status.json`, mühür → `manifest.json` + ZIP (13.6–13.7).
  4. Ekran: liste, elle toplama, başka gün, elle ekleme, paylaşma (13.8–13.9).
- **Klasör:** kök `<dataDir>/app_flutter/TeklifVeriSeti` (`DatasetFiles.kt:9`); gün klasörü `YYYY-MM-DD_<Gün>` — gün adı her zaman Türkçe `Pazartesi … Pazar`, takvim günü **Asia/Baku** (`DatasetContract.kt:25–40`); ZIP kökte `YolGo_teklif_<klasör>.zip` (`DatasetPack.kt:192`). Satır eklenebilen dosyalar yalnız `yolgo-live.jsonl`, `yolgo-poll.jsonl`, `yolgo-routes.jsonl`, `shifts.jsonl`, `manual.jsonl` (`DatasetFiles.kt:12`); her ekleme son satır `\n` ile bitmiyorsa önce `\n`, sonra `flagUnknowns(row)` + `\n` (UTF-8), tek kilit `DatasetFiles.lock`. Okuma boş satırı atlar, bozuk JSON'u sayar (`rows`, :19–26).
- **Ortak alanlar** (`common`, `DatasetContract.kt:41–45`; her satırda): `wall_ms`, `mono_ns` (`elapsedRealtimeNanos`), `schema_version` 1, `source` (`yolgo` ya da elle `manual`), `wall_ms_source` `native_clock`, `mono_ns_source` `elapsed_realtime`, `scooter_id` null + `scooter_id_source` `unavailable` (ya da `record`), `power_verified` false.
- **Bilinmeyen değer kuralı** (`flagUnknowns`, :162–173): satırdaki her null yaprak (`a.b`, `a[0]` yolu) `field_sources`'ta bayrak alır: varsa `<yol>_source` değeri, yoksa `unavailable`; değer uydurulmaz.

### 13.3 Y19: canlı teklif satırları (servis ve okuma)

- **Servis** (`DatasetRecordingService.kt:79–121`): `active` = yerel tercih `offer_dataset_v1.active` (Bool, false). `configure(active)` önce yazar (`commit`), sonra `false` → `stopService`, `true` → `startForegroundService` (API ≥ 26). `onStartCommand`: etkin değilse `stopSelf` + `START_NOT_STICKY`; zaten çalışıyorsa `START_STICKY`; kanal `yolgo_offer_dataset` "Teklif veri seti" (LOW, yalnız Türkçe); bildirim **7401** başlık "YolGo · Teklif veri seti", metin "Vardiyanın teklif verileri kaydediliyor", sürekli, dokununca uygulama (istek kodu 7401); API ≥ 34 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (manifest alt türü "User initiated offline courier offer dataset recording during an active shift", `AndroidManifest.xml:140–142`); kısmi uyanık kilidi `YolGo:offer-dataset`. Başlatma hatasında `yolgo-poll.jsonl`'a `status: background_service_unavailable`, `batteryImpactWh: null`, `batteryImpact_source: unmeasured_first_day_baseline_required` + `stopSelf`. GPS iznine bağlı değildir.
- **Ayar** (`DatasetBridge.kt:24–29`): `region` `[a-z0-9_-]{1,40}` değilse null → `offer_dataset_v1.region`; Dart `settings.region` (`central`/`wide`, Bölüm 14.2) ve `shift.active`'i açılışta, bölge ve vardiya değişince gönderir (`dataset_runtime.dart:92–95, 104–106, 114`).
- **Okuma zamanı** (`DatasetCapture.kt:21–65`; ayrı `HandlerThread` "YolGo-offer-dataset"): Rawstats teklif duyurusu → `ReadGate(300 ms)`: hemen ya da kalan süre sonra tek okuma (son duyuru kaybolmaz); her okumadan sonra yedek okuma `pollMs(listening)` = 60 000 ms (duyuru dinleniyor) / 7 000 ms (dinlenemiyor) sonraya kurulur (`DatasetContract.kt:17–24`). Servis etkin değilse okumaz.
- **Bir okuma** (`poll`, :67–122): `day = DatasetContract.day(wall)` (Bakü). İmza güvenilirse (`CourierFeedSigner.trusted`) feed v2 `payload` okunur; geçerli = `schemaVersion == 2`, `manualSession`, `offers` 0–8 öğe ve imza hâlâ güvenilir → `status ok`; değilse `unavailable`; imza yoksa `untrusted_or_absent`. Kurye noktası: son telemetri fix'i yaşı −5…+30 sn ve doğruluğu 0–65 m ise `point(lat, lon)`, değilse null. Her teklif → `DatasetContract.offer(…)` + `rawstatsVersion` (`producer.versionName`, `\d+(\.\d+){1,3}`) + `cachedWeather` + `measure` → `OfferRowGate.select` → `yolgo-live.jsonl`. Yazılan varsa Dart'a `rows(day, n)` sinyali.
- **Teklif satırı** (`DatasetContract.offer`, :87–124): `v2` yoksa, `offerKey` `offer-[0-9]+-[0-9]+` değilse, platform `wolt`/`bolt` değilse, `firstSeenAtMs`/`lastSeenAtMs` yok, ≤ 0 ya da `last < first` ise satır yok. Alanlar: ortak + `recordId` (rastgele UUID), `offerKey`, `platform`, `firstSeenAtMs`, `lastSeenAtMs`, `feeAzn` (`displayedFeeAzn`), `platformKm` (`platformDistanceKm` ?? `shown.km`), `shown` (yalnız `km`, `minLo`, `minHi`, `stops`, `readyInMin`, `approachMin`, `deliveryMin`, `totalMin`, `multiplier`, `boostPct`; `demandLabel` gibi metinler atılır), `courier`, `pickup`, `dropoff` (adres metninden yalnız koordinat ya da Plus Code; **adres metni yazılmaz**), `pickupZone`/`dropoffZone` (`Z1`–`Z4`, değilse null), `region`, `timezone` `Asia/Baku`, `utcOffsetMinutes` 240, `feeObservationSource` (`accessibility`/`ocr`/`notification`), `parseConfidence` (`low`/`medium`/`high`), `feeBasis` (`gross`/`net`), ölçülecekler null: `pickupAirKm`, `deliveryAirKm`, `routeKm`, `routeSeconds`, `demGainM`, `demLossM`, `temperatureC`, `windKmh`, `rainMm`. Bayraklar: `feeAzn`/`platformKm`/bölgeler `feed_v2`, `courier` `gps_coarse`, `pickup`/`dropoff` `coordinate_or_plus_code_coarse` ya da `unavailable_no_network`, `region` `yolgo_setting`, yoksa `unavailable`. Müşteri adı, telefonu, kapı kodu, not alanı hiç kopyalanmaz (`DatasetContractTest`).
- **Nokta ve koordinat** (:47–86): `point` sonlu ve aralıkta ise 3 ondalık (`HALF_EVEN`, ≈110 m). `coordinate(text, ref)`: önce "lat,lon" (`-?\d{1,2}\.\d{3,}` `[,;]` `-?\d{1,3}\.\d{3,}`), yoksa Plus Code (`[23456789CFGHJMPQRVWX]{4,8}\+[…]{2,3}`, büyük/küçük harf duyarsız) yerel OLC çözümü; kısa kod yalnız **gözlenmiş kurye noktasıyla** tamamlanır, varsayılan şehir merkezi kullanılmaz.
- **Hava** (`cachedWeather`, :123–135): Y10 önbelleği (`duzyol_weather.report`, Bölüm 10.7) `fetchedAt` ile okuma arası 0…1 200 000 ms (20 dk) ise `current.temperature_2m` → `temperatureC`, `wind_speed_10m` → `windKmh`, `precipitation` → `rainMm` (bayrak `existing_weather_cache`), `weatherFetchedAtMs`. Ağa çıkmaz.
- **Okuma ölçüsü** (E8, `measure`, :137–146): `receivedAtMs` (okuma anı), `rawstatsChangedAtMs` (feed v2 `changedAtMs`; eski Rawstats'ta null, bayrak `unavailable_old_rawstats`), `feedDelayMs` = fark (ikisi de varsa; bayrak `computed`), `readTrigger` `observer`/`fallback`.
- **Satır seyreltme** (`OfferRowGate`, `DatasetReads.kt:11–42`): parmak izi `changedFingerprint` = satır − `wall_ms`, `mono_ns`, `recordId`, ölçü alanları; `coreFingerprint` = ayrıca − `lastSeenAtMs`, `courier`. Teklif ilk kez → yaz; tam iz aynı → yazma (bekleyen silinir); çekirdek iz değişti ya da son yazımdan ≥ `ROW_GAP_MS` 5 000 ms → yaz; değilse beklet. Bu okumada görünmeyen teklifin bekleyen satırı yazılır (son görülme kaybolmaz). En çok 512 teklif hatırlanır.
- **Okuma satırı** (`yolgo-poll.jsonl`, :105–121; `PollRowGate`, `DatasetReads.kt:49–64`): duyuruyla gelen, satır yazmayan, durumu ve `changedAt`'ı son okuma satırıyla aynı okuma 5 sn içinde ayrı satır almaz; sonraki satır `readsSinceLastRow` ile sayar; yedek okuma hep yazılır. Alanlar: ortak + `pollIntervalMs`, `actualIntervalMs`, `readDurationMs`, `status`, `offersRead`, `recordsWritten`, `observerListening`, `readsSinceLastRow`, `phoneBatteryPct`, `phoneChargeUah`, `batteryImpactWh` null (`unmeasured_first_day_baseline_required`) + ölçü.

### 13.4 Y19: zenginleştirme (rota ve yükseklik)

- **Bekleyenler** (`DatasetBridge.pending`, :68–79): gün klasörleri yeniden eskiye; her günde `yolgo-routes.jsonl`'daki `recordId`'ler bitmiş sayılır; `yolgo-live.jsonl`'daki diğer satırlar, toplam en çok **16**.
- **Ölçüm** (`dataset_runtime.dart:33–91`; tek işçi, meşgulken gelen istek bir kez daha sıraya): `courier`, `pickup`, `dropoff` noktaları satırdan (3 ondalık). `pickupAirKm` = haversine(kurye, alış)/1000, `deliveryAirKm` = haversine(alış, bırakış)/1000 (biri yoksa null). Graf yüklü ve üç nokta da varsa rota: `PlanRequest(origin = kurye, stops = [alış, bırakış], fixedOrder = true, params = settings.paramsFor(battery%), approved/blocked/steep/reverseHours = sokak kararları, departure = wall_ms UTC)` (rota motoru Bölüm 3.15, kararlar Bölüm 4); `RideMode.time` planı: `routeKm = (rideM + walkM)/1000`, `routeSeconds = timeS`; yükseklik yalnız planın bütün noktaları (`ride`, `walk`, `startWalk`) DEM'de örneklenebiliyorsa: `demGainM = climbM`, `demLossM = Σ descentM` ("DEM boşluğu sıfır yükseklik olmaz"). Önbellek (en çok 128, ilk giren çıkar) anahtarı: graf kimliği, üç nokta, ayar JSON'u, batarya %, dört sokak kümesi, `wall_ms` saati. Rota hatası → null (kanıt olarak kalır). Adres çevrimiçi çözülmez.
- **Bayraklar:** null → `unavailable`; `*AirKm` → `coarse_coordinate_distance`; `dem*` → `local_dem`; diğerleri `local_yolgo_route`.
- **Yazım** (`enrich`, `DatasetBridge.kt:44–59`): `offerKey` `offer-[0-9]+-[0-9]+`, `recordId` `[0-9a-f-]{36}` değilse hata; ortak + `offerKey`, `recordId`, altı ölçü (sonlu ve ≥ 0 değilse null), `field_sources` (yalnız üç izinli bayrak, değilse `unavailable`), `routeMode` `time` → `yolgo-routes.jsonl`.
- **Tetik:** satır sinyali (`rawstatsSignalsProvider.rows`) → hemen; yedek her `datasetEnrichFallback` 60 sn; vardiya açılınca; açılışta bir kez (`:105–118`). Kanal hatası sessiz (gün toplanırken görünür).
- Gerçekte `pickup` çoğu teklifte boş (Wolt alış adresi koordinat taşımaz; Bolt'ta Plus Code olabilir), bu yüzden rota çoğu satırda `unavailable`; yalnız hava uzaklıkları dolar. Davranış budur; WEEM'de alış noktası 13.11'de.

### 13.5 Y19: vardiya satırları ve gün sınırı

- `datasetDays(start, end)` (`dataset_days.dart:5–16`): `end < start` → `ArgumentError('Bitiş başlangıçtan önce')`; iki ucun **Bakü** takvim günü ve aradaki her gün, `YYYY-MM-DD`. Gece yarısını geçen vardiya iki güne yazılır; vardiya kimliği değişmez.
- `datasetShift(start, end?)` (:18–29): `shift` → her güne `shifts.jsonl` satırı: ortak + `shiftStartMs`, `shiftEndMs` (açıkken null), `shiftStart_source` `yolgo_shift`, `shiftEnd_source` `open_shift`/`yolgo_shift` (`DatasetBridge.kt:35–43`; `start > 0`, `end ≥ start`). `end` varsa her gün için `collect`; bir günün hatası ötekini durdurmaz (durum `collection-status.json`'da, ekrandan yeniden toplanır).
- Tetikler (`dataset_runtime.dart:96–113`): vardiya açık olunca açık satır; açılışta vardiya açıksa açık satır; `endedAt` her bitiş anı için bir kez (bellekte küme) → kapanış satırı + toplama. Ekrandaki "Günü topla" vardiya açıksa önce açık satırı yineler (`dataset_screen.dart:41–45`). Aynı vardiyanın birden çok satırı olur; özet başlangıca göre birleştirir, son satır kazanır (13.7).
- **WEEM:** girdi `ShiftPort.state` (`startedAtMs`, `endedAtMs`; vardiya kimliği = başlangıç, CORE.md 1.9). Gün `LogTime.day(t, clock.zone())` — CORE.md 1.10 kararı: YolGo'nun Bakü saati yerine **telefon saat dilimi** (WEEM gün klasörleriyle aynı; Bakü'deki telefonda fark yok). `datasetDays` aradaki günleri de verir (`DailyPack.days` yalnız iki ucu verir; veri seti kendi kuralını korur). Kayıt dizgileri (`yolgo_shift`, `open_shift`) korunur; anlamı "WEEM vardiyası".

### 13.6 Y19: gün toplama (v3 gün anlık görüntüsü → `DayLogSnapshot`)

- **YolGo** (`DatasetPack.collect`, `:35-80`): gün geçerli olmalı (`DatasetContract.date`: `YYYY-MM-DD` ve takvimde gerçek gün); gün klasöründe geçici `.raw-<uuid>`. Rawstats sağlayıcısından `content://com.kuryelog.app.economics/logs/v3/day/<gün>` (imza her sorguda güvenilir, yük ≤ 262 144 karakter): meta `flowVersion == 3`, `schema_version == 3`, `day` aynı, `snapshot` UUID, `files` 1–128, adlar izinli (`gps.csv`, `screens.jsonl`, `online-sessions.jsonl`, `platforms.jsonl`, `events.jsonl`, `errors.jsonl`, `economics-*.jsonl`, `scooter*.jsonl`) ve tekil. Her dosya 32 768 baytlık sayfalarla (`snapshot`, `file`, `offset`, `limit`; Base64): sayfa alanları, `nextOffset` ve `eof` tutarlı, boş olmayan dosyada boş sayfa yok (boş dosya da bir sayfa sorar: izin her dosyada denetlenir); sonda boyut, SHA-256 ve satır sayısı (`lineCount`: `.csv` boş olmayan satır − 1 başlık, `.jsonl` boş olmayan satır) meta ile aynı. Zorunlu dosyalar `economics-offers.jsonl`, `economics-energy.jsonl`, `gps.csv`, `screens.jsonl`. Meta `feed-manifest.json` olarak yazılır; kilit altında `replaceRaw` (eski `rawstats/` → `.raw-previous`, geçici → `rawstats/`, sonra eskisi silinir; yarıda kalan değişimi `recoverRaw` onarır: `rawstats/` yoksa ve `.raw-previous` varsa geri adlandırılır), `collection-status.json` = ortak alanlar + `complete: true`, `reason: "verified_v3"`, ardından mühür (13.7). Hata: `complete: false`, `reason: "v3_not_collected_retry_required"` ve hata yukarı iletilir; geçici klasör her durumda silinir.
- **Rawstats tarafı** (RS `LogFeedV3Android.query` + `LogFeedV3.build`): `economics.share` açık ve geri yükleme sürmüyorken; `Store` kilidi altında gün klasöründeki izinli dosyaların açık tanıtıcıları ve o anki boyları alınır, kilit dışında yalnız o boy kadar kopyalanır (`copyPrefix`: sonraki eklemeler sonraki toplamaya kalır, kaynak kısalmışsa hata). Gün sırrı `noBackupFilesDir/economics-day-secrets-v3/<gün>` (32 bayt, `.tmp` + `rename` ile bir kez yaratılır; anlık görüntüye, dışa aktarmaya ve yedeğe girmez). `build`: izinli adların her biri için (`LogFeedV3.names` = `BackupFiles.logNames` içinden `economics-*`, `scooter*`, `online-sessions`, `platforms`, `events`, `errors`, `gps.csv`, `screens` + `economics-payouts.jsonl`, sıralı) — `gps.csv`: `AnalysisExport.redact(GPS)`, başlığa `wall_ms, mono_ns, schema_version, source, scooter_id, wall_ms_source, mono_ns_source, scooter_id_source, power_verified` ve her eski sütun için `<sütun>_source`; satırlara `t`'den çözülen `wall_ms` (ya da boş), boş `mono_ns`, `3`, `kuryeplus`, boş `scooter_id`, `legacy_t` / `unavailable`, `unavailable`, `unavailable`, `false` ve sütun bayrakları `legacy_csv` / `unavailable`. JSONL: boş olmayan her satır JSON ve `AnalysisExport.row(kind)` (R18 kuralı); olmazsa reddedilen sayılır, olursa `common` eklenir (aşağıda). Dosya meta `{name, available, bytes, rows, sourceRows, rejectedRows, sha256}`; anlık görüntü manifesti `{schema_version: 3, flowVersion: 3, day, snapshot, files, wall_ms, mono_ns, source: "kuryeplus", scooter_id: null, scooter_id_source: "unavailable", power_verified: false, specVersion, producer {package, versionName}, redaction {locDecimals: 3, secretExported: false, pseudonymScope: "day"}}`; 1 saatten eski anlık görüntüler silinir.
- **`common(row, orijinal, ad)`** (`LogFeedV3.kt:141-171`): `wall_ms` kayıttan, yoksa eski `t` (`yyyy-MM-dd'T'HH:mm:ss.SSSZ`) çözümü, yoksa null (indirme anı **yazılmaz**); `mono_ns` kayıttan ya da null; `schema_version` 3; `source` kayıttaki izinli değer (`kuryeplus`, `surus_paneli`, `yolgo`, `manual`), yoksa dosyaya göre (`scooter*` ve `economics-scooter` → `surus_paneli`, `economics-payouts` → `manual`, diğer `kuryeplus`); kayıttaki `source` `ocr` / `accessibility` / `notification` ise `capture_source` olarak korunur; `scooter_id` kayıttan ya da null; `power_verified` hep false; `*_source` bayrakları (`record` / `legacy_t` / `unavailable`); bütün null yapraklar `field_sources`'ta (`<yol>_source` değeri ya da `unavailable`).
- **WEEM:** sağlayıcı, sayfa, Base64, imza ve `economics.share` izni **yok** (S7; aynı uygulama). `dataset.DatasetPack.collect(day)` → `dataset.DayLogSnapshot.build(kaynak, hedef, day, secret, version, wallMs, monoNs)` doğrudan: kaynak `:core` `Store` kilidi altında dondurulan gün klasörü kopyası (`files/logs/<gün>/`, aynı izinli adlar, `copyPrefix` kuralı), sır `DailyLogSecret` (aynı yol), çıktı doğrudan geçici `rawstats/` klasörüne + `feed-manifest.json` (aynı alanlar). Boy, SHA-256 ve satır doğrulaması aynı manifestten yapılır; zorunlu dosya kuralı kalır (yazıcı her izinli adı boş da olsa üretir). `AnalysisExport` WEEM'de var (`logic/orders`, A4.3); kural birebir. `LogFeedV3Test` (10) burada uyarlanır (13.18).

### 13.7 Y19: eşleştirme, gün özeti, mühür ve ZIP (`DatasetPack.kt:81-213`)

- **Eşleştirme** (`match(raw, live)`, `:110-129`; girdiler teklif anahtarı → son satır): önce aynı `offerKey` (`method: "offerKey"`); kalan her canlı teklif için adaylar = kalan ham teklifler içinde aynı platform, `|firstSeenAtMs farkı| ≤ 5000` ve `|feeAzn farkı| ≤ 0,010001` (ikisi de sayı); yalnız tek adayı olan ve o aday başka hiçbir canlı teklifin aday listesinde olmayan çift eşleşir (`method: "platform_time_fee"`). Çıktı `{matched, onlyRawstats, onlyYolgo, ratio = eşleşen / (eşleşen + yalnız ham + yalnız canlı) | null, ambiguousFallback, pairs: [{rawOfferKey, yolgoOfferKey, method}]}`; `ambiguousFallback` = birden çok adayı olan ya da tek adayı paylaşılan canlı teklif sayısı.
- **Gün özeti** (`summary`, `:130-172`): `recoverRaw`; ham = `rawstats/economics-offers.jsonl`, canlı = `yolgo-live.jsonl` (anahtar başına son satır); canlı satırlara `yolgo-routes.jsonl`'dan (`recordId`) altı ölçü eklenir. `offers` = platform başına ham sayısı + eşleşmeyen canlı sayısı; `emptyFieldRatio` = canlı satırlarda 15 alanın (`feeAzn`, `platformKm`, `pickupAirKm`, `deliveryAirKm`, `routeKm`, `routeSeconds`, `demGainM`, `demLossM`, `temperatureC`, `windKmh`, `rainMm`, `scooter_id`, `pickup`, `dropoff`, `courier`) yok ya da null payı; `routeComputedRatio` = `routeKm`'si dolu canlı satır payı. Vardiya pencereleri `shifts.jsonl`'dan (başlangıç başına son satır kazanır; açıksa bitiş = şimdi), günün gece yarısı ile +24 sa arasına kırpılır (YolGo Bakü); scooter kapsaması = `rawstats/economics-scooter.jsonl` örneklerinden pencere içindekilerin ardışık farkları (1–5000 ms, aynı pencerede) toplamı / pencere süresi (0–1); `scooter_id` = tek kimlik varsa o (`scooter_id_source: "rawstats"`), değilse null (`"unavailable_or_multiple"`); `missingManual` = 10 elle türünden (`wolt_earnings`, `bolt_earnings`, `wolt_rating`, `bolt_rating`, `wolt_acceptance`, `bolt_acceptance`, `wolt_completion`, `bolt_completion`, `weekly_payout`, `shift_note`) `manual.jsonl`'da olmayanlar. Ayrıca `liveOffers`, `rawOffers`, `scooterCoverageMs`, `shiftDurationMs`, `coverageAsOfMs`, `shifts`.
- **Mühür** (`seal(day)`, `:173-202`; kilit altında): özet; ham meta `rawstats/feed-manifest.json`; listelenen dosyalar = gizli olmayan (`.` ile başlamayan; gizli klasörlere inilmez) bütün dosyalar, `manifest.json` hariç, göreli yola göre sıralı; her biri `{name (göreli), bytes, rows | null, rows_source ("counted" | "binary_or_json_document"), sha256}`. `manifest.json` = `flagUnknowns(ortak alanlar (scooter = özetteki kimlik) + day, dayName (Türkçe gün adı), yolgoVersion, yolgoVersionCode, rawstatsVersion (ham meta `producer.versionName`), flowVersions {live: 2 | null, logs: 3 | null}, specVersion: "2.1.0", timezone: "Asia/Baku", files, summary, matching, shifts, rawstatsFiles (ham meta dosyaları | []), collection (durum dosyası | {complete: false, reason: "not_collected"}))`. ZIP kökte `YolGo_teklif_<klasör>.zip` (`.<ad>.tmp` + `rename`), girdiler `<klasör>/<göreli yol>` + `manifest.json`. Dönüş: özet + `day`, `folder`, `zip`, `collectionComplete`.
- **Liste** (`list`, `:203-213`): `YYYY-MM-DD_…` klasörleri yeniden eskiye; her biri için özet + `day`, `folder`, `collectionComplete`, varsa `zip` (her listede bütün günlerin özeti yeniden hesaplanır).
- **WEEM:** `dataset.DatasetPack` saf (`java.io`, `java.util.zip`, `MessageDigest`; Android'siz): `collect`, `match`, `summary`, `seal(day, version)`, `list`; kök Android `:orders` `DatasetRuntime`'dan. Kayıt dizgileri ve alan adları aynen (`yolgo-live.jsonl`, `yolgoOfferKey`, `onlyYolgo`, `yolgoVersion` ve `rawstatsVersion` = WEEM sürümü, `flowVersions.live` 2 = feed v2 alanları); `kontrol.py` uyumu böyle korunur (13.10). Gün sınırı ve `timezone` telefon saat dilimiyle (13.5, 13.11). WEEM'de ham ve canlı teklifler aynı uygulamanın aynı anahtarlarıdır: eşleşme oranı beklenen 1,0 (fark kayıt hatası demektir; analizde denetim değeri olarak kalır). **Metin onayı (M-13-1):** ZIP adı `WEEM_teklif_<klasör>.zip`, paylaşım metni "WEEM teklif veri seti · <gün>" / "WEEM təklif məlumat dəsti · <gün>".

### 13.8 Y19: ekran (`DatasetScreen`, `dataset_screen.dart`)

- **Giriş:** Ayarlar → "Teklif veri seti" (`settings_screen.dart:43-44`); WEEM: Harita ayarları → "Teklif veri seti" (6.9).
- Üst çubuk "Teklif veri seti", yenile ("Yenile"); aşağı çekince yenilenir. Açıklama (TR/AZ): "Vardiya açıkken Rawstats duyurusu gelince teklifler anında okunur. Duyurular dinlenemiyorsa 7 saniyede bir, dinlenebiliyorsa yedek olarak 60 saniyede bir yoklanır. Paketlerde rota, yükseklik, mevcut hava bilgisi ve RAW Stats kayıtları bir araya gelir." / "Növbə açıq olanda Rawstats bildirişi gələn kimi təkliflər dərhal oxunur. Bildirişləri dinləmək mümkün deyilsə 7 saniyədən bir, mümkündürsə ehtiyat olaraq 60 saniyədən bir yoxlanır. Paketlərdə marşrut, yüksəklik, mövcud hava məlumatı və RAW Stats qeydləri bir araya gəlir."
- Düğmeler "Günü topla" (bugün), "Başka gün" (tarih seçici, 2020 → bugün); iş sürerken ilerleme çubuğu; hata metni kırmızı; gün yoksa "Henüz gün yok. Vardiya kaydıyla veya Günü topla ile başlayabilirsin."
- **Gün kartları** (katlanır, başta kapalı; açık olanlar yenilemede korunur; anahtar `dataset-day-<gün>`): başlık klasör adı (`_` → ` · `, ör. "2026-10-10 · Cumartesi"); içerik "Teklifler: Wolt N · Bolt M", "Eşleşme: %x.x · Eşleşen N", "Yalnız RAW Stats N · Yalnız YolGo M", "Rota km hesaplanan: %x.x", "Boş alan oranı: %x.x", "Scooter kaydı kapsaması: %x.x" (sayı değilse "Bilinmiyor"), toplama durumu ("RAW Stats dosyaları toplandı ve doğrulandı" ya da "RAW Stats gün dosyaları eksik — Günü topla ile yeniden dene"), elle eksikler ("Elle eklenenler tamam" ya da "Eksik elle eklenenler: <etiketler>"); düğmeler "Günü topla", "Elle ekle", "Paylaş".
- **Eylemler** (aynı anda bir): toplama — vardiya açıksa önce açık vardiya satırı (13.5), sonra `collect(gün)`; paylaşma — yeniden mühür (seçilen ekler ve canlı satırlarla hash'ler uyuşsun), ZIP paylaşılır, metin "YolGo teklif veri seti · <gün>"; işlem hatası "İşlem tamamlanamadı. RAW Stats paylaşımını, uygulama sürümünü ve boş alanı kontrol et. Gün eksik olarak işaretlendi; tekrar toplayabilirsin."; liste hatası "Gün listesi okunamadı. Tekrar dene.", köprü yoksa "Bu sürümde veri seti köprüsü bulunamadı."; işlemden sonra liste yeniden okunur (işlem hatası liste başarısıyla silinmez). Açıklama dışındaki metinler yalnız Türkçe.
- **WEEM:** `:app` `DatasetScreen` (Compose; `DatasetRuntime` çağrıları arka planda); bütün metinler TR/AZ (`TextCoverageTest`). **Metin onayı (M-13-2):** açıklama WEEM akışına göre "Vardiya açıkken teklifler görüldükleri anda kaydedilir. Paketlerde rota, yükseklik, mevcut hava bilgisi ve WEEM gün kayıtları bir araya gelir." / "Növbə açıq olanda təkliflər göründüyü anda qeyd olunur. Paketlərdə marşrut, yüksəklik, mövcud hava məlumatı və WEEM gün qeydləri bir araya gəlir."; etiketlerde "RAW Stats" → "gün kayıtları" / "gün qeydləri", "YolGo" → "canlı kayıt" / "canlı qeyd" (ör. "Yalnız gün kayıtları N · Yalnız canlı kayıt M"); işlem hatası "İşlem tamamlanamadı. Boş alanı kontrol et. Gün eksik olarak işaretlendi; tekrar toplayabilirsin." / "Əməliyyat tamamlanmadı. Boş yeri yoxla. Gün natamam kimi qeyd olundu; yenidən toplaya bilərsən."; köprü metni yazılmaz. Öteki metinlerin AZ karşılıkları A6.9'da yazılır ve onaya sunulur.

### 13.9 Y19: elle ekleme (`dataset_manual.dart`, `DatasetManual.kt`)

- **Türler** (`datasetManualLabels`): `wolt_earnings` "Wolt kazanç ekranı", `bolt_earnings` "Bolt kazanç ekranı", `wolt_rating` "Wolt rating ekranı", `bolt_rating` "Bolt rating ekranı", `wolt_acceptance` "Wolt kabul oranı ekranı", `bolt_acceptance` "Bolt kabul oranı ekranı", `wolt_completion` "Wolt tamamlama ekranı", `bolt_completion` "Bolt tamamlama ekranı", `weekly_payout` "Haftalık ödeme", `shift_note` "Vardiya notu" (yalnız TR).
- **Seçim sayfası:** başlık "Güne elle ekle", alt yazı "Ekranlar yalnız seçtiğin dosyalardan eklenir; içerik okunmaz."; simgeler (not, ödeme, fotoğraf ekle).
- **Ekran görüntüsü** (8 tür; `DatasetManual.pick` / `reply`, `:18-54`): seçici açıksa "Dosya seçimi açık"; gün ya da tür geçersizse "Gün veya ekran türü geçersiz"; `ACTION_OPEN_DOCUMENT` (`image/*`, istek 720); iptal → false; MIME `image/` değilse hata; uzantı `png` / `jpg` / `webp` / `image`; dosya **opak kopya** (çözülmez, OCR yok) `attachments/<tür>_<uuid>.<uzantı>` (dosyanın kendi adı alınmaz: kişi adı taşımasın); `manual.jsonl` satırı ortak alanlar (`source: "manual"`) + `kind`, `file`, `content_read: false`, `ocr: false`, `attachment_source: "user_selected_opaque"`, `mime`; hata "Seçilen dosya eklenemedi"; seçici açılamazsa "Dosya seçici açılamadı".
- **Haftalık ödeme / vardiya notu** (`_ManualDialog`, `DatasetManual.save`, `:55-80`): ödeme — "Platform" (Wolt / Bolt, zorunlu), "Ödenen tutar (AZN)" (zorunlu, 0–100 000), "Dönem başlangıcı (YYYY-MM-DD, isteğe bağlı)", "Dönem bitişi (YYYY-MM-DD, isteğe bağlı)"; not — "Mola (dakika)" (0–1440), "Hava" (Açık, Yağışlı, Rüzgârlı, Soğuk, Sıcak, Değişken), "Meşgulken diğer uygulama" (Wolt, Bolt, Diğer uygulama yok), "Diğer uygulamada yaptığım" (Teslimat, Teklif bekledim, Çevrimdışıydım, Mola, İşlem yapmadım), "Gözlenen ret cezası (dakika)" (0–1440), "Gözlenen ret cezası (AZN)" (0–100 000); her seçimde "Bilinmiyor" (null). Doğrulama: sayılarda virgül ya da nokta, sonlu, 0 ≤ x ≤ sınır ("Tutar ve süreleri kontrol et"); tarih `YYYY-MM-DD` ve gerçek gün ("Tarih YYYY-MM-DD olmalı"); ödemede platform ve tutar ("Platform ve ödeme tutarı gerekli"); "Vazgeç", "Kaydet". Satır: ortak (`manual`) + `kind` + alanlar (boş → null) + `field_sources` (`user_entered` | `unavailable`); serbest metin ve müşteri alanı **yok**.
- **WEEM:** `dataset.DatasetManual` saf doğrulama ve satır kurma; `:app` `DatasetManualSheet` (seçim sayfası, pencere) ve `ActivityResultContracts.OpenDocument` (`image/*`); kopya `:orders` `DatasetRuntime`'da (tek kilit). Metinler TR/AZ (M-13-2 kapsamında).

### 13.10 Y19: çevrimdışı denetim aracı (`tools/veri_seti/`)

- `kontrol.py` (Python 3.9+, `-I`; ağ, ek paket, ZIP açıp diske yazma ve OCR yok): `--self-test` (8 sentetik test) ya da ZIP yolu. Denetimler: ZIP yolları (mutlak, `..`, `\`, yinelenen ad) ve tek gün / tek manifest; klasör adı `<gün>_<Türkçe gün adı>/`, `schema_version` 1, `specVersion` 2.1.0 (değilse uyarı); manifest dosya listesi = ZIP içeriği, her dosyanın bayt boyu ve SHA-256'sı, `.jsonl` / `.csv` satır sayısı; her satırda ortak alanlar, `schema_version` (ham 3, diğer 1), `source` kümesi, `power_verified` false, null saatlerin `*_source: "unavailable"` bayrağı, `scooter_id` bayrağı; teklif anahtarı ve platform, ilk/son görülme sırası (> 2 sa uyarı); negatif ya da geçersiz ücret, km, süre; şüpheli rota hızı (> 120 km/sa); ters platform süre aralığı; müşteri anahtarları (`customerName` …; yalnız `[note]`, `[tel]`, `[code]` maskeleri izinli) ve maskelenmemiş telefon; `lat` / `lon` sınırı ve **3 ondalık** kuralı; scooter zamanının geri gitmesi ve > 5 sn boşluk; eşleştirme, rota kapsaması ve scooter kapsamasının manifestle aynı hesaplanması; toplamanın eksikliği, kaynakta olmayan dosya, reddedilen satır uyarıları. Çıkış kodu 0 geçerli, 1 tutarsızlık, 2 ZIP okunamadı; Markdown tablo raporu.
- **WEEM:** `tools/veri_seti/` aynı araç ve README (biçim aynı kaldığından değişiklik yalnız metinlerde: rapor başlığı "YolGo" → "WEEM", ZIP adı örneği); `logic/orders` testinde (emsal A4.3 `AnalysisToolsTest`) `python3 -I tools/veri_seti/kontrol.py --self-test` ve WEEM'in ürettiği sentetik ZIP'in denetimi (python yoksa atlanır). Öz-testler Python'da kalır (8; çevrilmez).

### 13.11 Y19: WEEM kayıt düzeni (servis, satır kaynağı, tetikler, 2 telefon)

- **Kayıt yeri:** veri seti, teklif verisinin olduğu `:orders`'ta (`logic/orders` `dataset`, Android `:orders` `DatasetRuntime`); kök `files/TeklifVeriSeti/` (YolGo `app_flutter/TeklifVeriSeti/`; klasör ve dosya adları aynı). Rota ve yükseklik ölçümü `:map`'ten **`RouteMeasurePort`** (yeni `:core` portu, öneri: `measure(courier, pickup, dropoff, atMs): RouteMeasure?` = 13.4'teki altı değer ve bayrakları; `:map` uygular, `NONE` null döner).
- **Servis yok:** YolGo'nun ayrı ön plan servisi (7401, kanal `yolgo_offer_dataset`, `specialUse`, uyanık kilidi, `offer_dataset_v1`) **yazılmaz**: kayıt `:orders`'ın teklif akışının içindedir ve vardiya boyunca süreci `:core` `ShiftService` (1) ayakta tutar. `NotificationIds.DATASET_FGS` (7401), kanal `yolgo_offer_dataset`, `FOREGROUND_SERVICE_SPECIAL_USE` izni ve `offer_dataset_v1` tercih dosyası gerekmez (CORE.md 3.3, 14.6 ve 14.8 bu karara göre düzeltildi).
- **Satır kaynağı (13.1 düzeltmesi):** `OrderEvents.offers`'ın `OfferSummary`'si yetmez (alış adresi, gözlem kaynağı, ayrıştırma güveni, ücret tabanı yok). `DatasetRecorder` feed v2 teklif nesnesinin karşılığını (RS `OfferFeedContract.offer` + `EconomicsLogs.feedOfferV2`, `OfferFeed.kt:80-112`) aynı süreçte, `OfferSummaries`'ın girdisi olan iç teklif durumundan kurar ve 13.3'teki `DatasetContract.offer` kuralıyla satıra çevirir; adres metinleri yalnız koordinat / Plus Code çıkarmak için okunur, satıra yazılmaz.
- **Okuma yok:** satır teklif durumu her değiştiğinde üretilir (`OfferRowGate` ile seyreltilir, 13.3); yoklama, `ReadGate`, `PollRowGate` ve `yolgo-poll.jsonl` **yok** (okuma satırının ölçtüğü gecikme ve pil etkisi tek uygulamada anlamsız; `kontrol.py` dosyayı zorunlu tutmaz). Ölçü alanları korunur: `receivedAtMs` = satır anı, `rawstatsChangedAtMs` = `:orders`'ın teklif değişim anı (bayrak dizgisi `rawstats_feed_v2` aynen), `feedDelayMs` = fark, `readTrigger` = `observer`.
- **Kurye noktası:** `NavLocation`'ın son fix'i (yaş −5…+30 sn, doğruluk 0–65 m) → `point` (3 ondalık); 2 telefonda Provider'ın kendi GPS'i.
- **Alış noktası:** YolGo kuralı aynen (yalnız alış adresi metninde koordinat ya da Plus Code varsa, kısa kod gözlenmiş kurye noktasıyla); restoran adından nokta bulma yapılmaz (yeni özellik olurdu → SORU-13-1).
- **Hava:** `cachedWeather` yerine `WeatherPort.current()` (10.10); `now − fetchedAtMs ≤ 20 dk` kuralı korunur, değerler `tempC`, `windKmh`, `rainMm`, `weatherFetchedAtMs`, bayrak `existing_weather_cache` (küçük fark: `WeatherNow` `current` yoksa saatlik dilimden doldurur, YolGo yalnız `current` okurdu).
- **Tetikler:** vardiya açılınca açık vardiya satırı ve zenginleştirme taraması; teklif satırı yazılınca zenginleştirme isteği (tek işçi); yedek 60 sn tarama ve açılışta bir kez (13.4); vardiya bitince kapanış satırı + toplama + mühür (13.5–13.7). Gün sonu işi `ShiftHub.listeners`'a ad ve sırayla eklenir (15.2): `:orders`'ın kendi gün sonu yazımlarından sonra (gün klasörü tamamlanmış olsun).
- **Gün sınırı:** YolGo Bakü sabiti yerine telefon saat dilimi (CORE.md 1.10, 13.5): `DatasetContract.day` / `folder` / `date` ve özetteki gece yarısı `Clock.zone()` ile; satırlardaki `timezone` ve `utcOffsetMinutes` o bölgenin kimliği ve o anki ofseti (Bakü'de `Asia/Baku`, 240; sonuç aynı).
- **2 telefon:** veri seti Provider'da (teklifler ve gün kayıtları orada); rota ölçümü Provider'ın kendi `:map` verisiyle (harita verisi her telefonda, 15.5); scooter kapsaması `rawstats/economics-scooter.jsonl`'dan gelir, bu dosya 2 telefonda Receiver'da yazılır (plan 4.4: saniyelik scooter verisi Receiver'dan çıkmaz) → SORU-13-2.

### 13.12 Y20: günlük görev modeli (`DailyTask`, `TaskTrigger`; `daily_task.dart`)

- `DailyTask(id, title, place, point, due, completedAt?)` (`:3-28`): `eligible(now)` = tamamlanmamış, `now ≥ due` ve `now` ile `due` aynı takvim günü (telefon yerel saati); `complete(now)`; JSON `{id, title, place, point: {lat, lon}, due (ms), completed? (ms)}`; okumada nokta yok, sonlu değil ya da aralık dışı → `FormatException` (görev atlanır).
- `TaskTrigger` (`:30-65`; "her teslimat ayrı fırsattır; kapatma yalnız mevcut gösterimi susturur"): `enterM = 1000`, `exitM = 1200` (histerezis). Görev başına durum: içeride mi, kapatma anı, gösterimde mi. `validFix(nokta, doğruluk, fixAt, now)`: koordinat sonlu ve aralıkta, doğruluk 0–65 m, yaş −5…+30 sn. `evaluate(tasks, nokta, now, fixAt, doğruluk, deliveryFinished, activeOrder)`: geçersiz fix → boş; her uygun görev için uzaklık; içeride = ≤ 1000 m → true, > 1200 m → false, arada önceki değer (yoksa false); yeniden giriş = önceki değer `false`, şimdi içeride, görev kapatılmış ve kapatma bugün; **gösterilir** = ≤ 1000 m, gösterimde değil ve (`deliveryFinished` ya da (yeniden giriş ve açık sipariş yok)); gösterilenler `due` sırasıyla. `dismiss(id, now)`: gösterimden çıkar, kapatma anı yazılır; `remove(id)`: bütün durum silinir; `restoreDismissed(id, at)`.
- Uygulama bölge içinde açılırsa (önceki değer yok) kapatılmış görev yeniden girişle gelmez, yalnız teslimatla (13.1 düzeltmesi).
- **WEEM:** `logic/map` `tasks.DailyTask`, `tasks.TaskTrigger` aynen; takvim günü `Clock.zone()` (bulgu notundaki "tek saat kuralı": WEEM'de bütün gün sınırları `Clock.zone()`, Bakü'de sonuç aynı).

### 13.13 Y20: görev denetleyicisi (`TaskController`, `task_controller.dart:20-121`)

- **Kalıcılık:** `daily.tasks.v1` (JSON dizi; bozuk öğe atlanır, bozuk dizi → boş) ve `daily.tasks.closed.v1` (JSON harita `id → ms`; açılışta `restoreDismissed`). Yazım sıralı; başarısızlık (`StateError`) çağırana.
- **Sinyaller** (gün motorundan, 11.14): `activeDay(dayId?)` — aynıysa yok; gün açıkken 30 sn'de bir süre denetimi (`_expire`: artık uygun olmayan uyarılar düşer, tetik durumu silinir, uyarı kalmadıysa 7201 iptal); gün kapanınca uyarılar temizlenir, 7201 iptal. `activeOrder(bool)`. `deliveryFinished()` → son konumla `_check(delivered = true)`.
- **`_check(fix, delivered)`** (konum akışının her fix'inde `delivered = false`): gün kapalı → yok; süresi geçen uyarılar düşer; `evaluate`; yeni yok ve değişiklik yoksa yok; uyarılar = kalanlar + yeniler; yeni varsa bildirim **7201** başlık "Yakınında yapılacak iş var" / "Yaxınlıqda işin var", metin her satırda "<başlık> · <yer>", gün kimliğiyle (CORE.md 3.1: gün eşleşmiyorsa sistem bildirimi yok; WEEM öndeyken yalnız kart).
- `add(task)` (`due` sırasıyla), `delete(id)` (tetik durumu silinir, uyarıdan çıkar), `dismiss(id)` ("Tamam / Kapat": tetikte kapatma, uyarıdan çıkar, `daily.tasks.closed.v1[id] = şimdi`, uyarı kalmadıysa 7201 iptal; görev **tamamlanmaz**).
- **`openRoute(task)`** ("Rota Oluştur"; aynı anda bir, `opening`): YolGo'da Google Maps'te iki tekerli rota (`openGoogleRoute`: `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lon>&travelmode=two-wheeler&dir_action=navigate`, Maps yoksa tarayıcı); yalnız açıldıysa görev `completedAt = şimdi`, kalıcı, tetik durumu silinir, uyarıdan çıkar, olay `task_route_opened`, uyarı kalmadıysa 7201 iptal; açılmadıysa false ("Google Maps açılamadı; iş bekliyor." / "Google Maps açılmadı; iş gözləyir."); kayıt hatası "İş tamamlanmış olarak kaydedilemedi." / "İş tamamlanmış kimi saxlanılmadı.".
- **WEEM (S9):** "Rota Oluştur" WEEM navigasyonunu açar: `NavController.goTo(görev noktası, label = "<başlık> · <yer>", origin = "task", purpose = "task")` ve Harita sekmesine geçiş; başarı = hedef kabul edildi (nokta geçerli); görev o anda tamamlanır (YolGo'nun "açıldıysa tamamla" kuralı). **Ek kural:** `_bindShiftTarget` (7.3) `purpose == "task"` hedefini siparişin restoran/müşteri hedefi saymaz (YolGo'da rota Google Maps'e gittiği için bu bağ hiç kurulmazdı). Başarısızlık metni "Rota açılamadı; iş bekliyor." / "Marşrut açılmadı; iş gözləyir." (M-13-3); `task_route_opened` olayı aynen. Saf durum `tasks.TaskController` (komutlar → durum + kalıcılık + bildirim istekleri), Android `:map` `TaskRuntime` (konum, 30 sn zamanlayıcı, `NotifyPort`).

### 13.14 Y20: görev ekranı, düzenleyici ve uyarı kartı (`tasks_screen.dart`)

- **"Günlük işler"** (`TasksScreen`, `:12-46`; Ayarlar → "Günlük işler", WEEM Harita ayarları → "Günlük işler"): başlık "Günlük işler" / "Gündəlik işlər"; açıklama "Seçtiğin saatten sonra, teslimat bittiğinde 1 km içindeki işler hatırlatılır. Kapatınca tamamlanmaz; sonraki teslimatta veya bölgeye yeniden girince tekrar gösterilir." / "Seçdiyin saatdan sonra, sifarişi çatdıranda 1 km yaxınlıqdakı işlər xatırladılır. Bağlamaq işi bitirmir; növbəti çatdırılmada və ya yenidən yaxınlaşanda göstərilir."; boş "Henüz iş eklenmedi" / "Hələ iş əlavə edilməyib"; görev kartı: simge (bekliyor mavi yer, tamam yeşil onay), başlık, alt satırlar "G.A · SS:DD", yer, "Bekliyor" / "Gözləyir" ya da "Tamamlandı · rota açıldı" / "Tamamlandı · marşrut açıldı"; sil ("Sil") → "Bu işi sil?" / "Bu işi silmək istəyirsən?", "Vazgeç" / "Ləğv et", "Sil"; yüzen düğme "İş ekle" / "İş əlavə et".
- **Düzenleyici** (`showTaskEditor(point?)`, `_TaskEditor`, `:48-129`; uzun basış satırı 4 noktayla açar, 6.7): başlık "Günlük iş ekle" / "Gündəlik iş əlavə et"; alanlar "Yapılacak iş" / "Görüləcək iş" ve "Mekân adı, koordinat veya Maps bağlantısı" / "Məkanın adı, koordinat və ya Maps keçidi" (2 satır; değişince nokta silinir; nokta verildiyse metin = noktanın yazısı); "Konumu bul" / "Ünvanı tap" → `AddressParser.parseExplicit` (8.4; ASAN, banka gibi mekân adları kabul) + `DestinationResolver.resolve(yakın = son konum ?? Bakü merkezi)` (8.6); bulunursa "<etiket>\n<nokta>\nKaydetmeden önce doğru şubeyi kontrol et." / "… Saxlamazdan əvvəl filialın düzgün olduğunu yoxla." (mavi); bulunamazsa "Konum bulunamadı. Şube adı veya koordinat/Maps bağlantısı gir." / "Ünvan tapılmadı. Filialın adını, koordinatı və ya Maps keçidini yaz."; tarih (dün … bir yıl sonra) ve saat seçici (varsayılan şimdi); "Konumu onayla ve kaydet" / "Ünvanı təsdiqlə və saxla" → başlık boş ya da nokta yoksa "İş adını yaz ve hedef konumu bul." / "İşin adını yaz və ünvanı tap."; kayıt `id` = şimdinin mikro saniyesi, `place` = etiket ?? metin, `due` = seçilen gün + saat (yerel); hata "İş kaydedilemedi" / "İş saxlanılmadı".
- **Uyarı kartı** (`TaskAlertOverlay`, `:131-175`): uyarı varsa ilki; tam ekran yarı saydam engel (dokunuşla kapanmaz) + ortada kart (en çok ekranın %80'i, kaydırılır): görev simgesi (48), başlık (28, kalın), yer (20), son konum varsa "<m> m yakınında" / "<m> m yaxınlıqdadır"; "Rota Oluştur" / "Marşrut qur" (68 px, harita simgesi) → `openRoute`; "Tamam / Kapat" / "Oldu / Bağla" (60 px) → `dismiss`; "Kapatırsan iş beklemeye devam eder." / "Bağlasan, iş gözləməyə davam edəcək.". Yer: ana ekranda bütün kiplerin üstünde (`nav_screen.dart:73, 89, 150`).
- **WEEM:** `:app` `TasksScreen`, `TaskEditorSheet`, `TaskAlertCard`. Uyarı kartı ve hatırlatıcı kartı (13.16) **uygulama kabuğunda**, her sekmenin üstünde: CORE.md 3.1'e göre WEEM öndeyken (hangi sekme olursa olsun) sistem bildirimi atılmaz; kart yalnız Harita sekmesinde olsaydı uyarı kaçardı.

### 13.15 Y20: hatırlatıcı modeli ve zamanlama (`reminders.dart:19-183`)

- `ReminderKind` = `water`, `check`, `food`. `ReminderItem(kind, enabled = false, text = "", useTimes = false, intervalMin = 45, times = [], onlyShift = true, since = 0)`; başlık "Su iç" / "Su iç", "Kontrol et" / "Yoxla", "Yemek ye" / "Yemək ye"; emoji 💧, 🔧, 🍽️; mesaj = metin boşsa varsayılan: "Birkaç yudum su iç" / "Bir neçə qurtum su iç", "Batarya, lastik, fren ve çantayı kontrol et" / "Batareyanı, təkəri, əyləci və çantanı yoxla", "Yemek zamanı — bir şeyler ye" / "Yemək vaxtıdır — bir şey ye".
- JSON (`reminders.v1`): `{water | check | food: {on, text, useTimes, every, at: [günün dakikası], shift, since}}`; okumada her alanın tipi doğruysa alınır, değilse o alanın varsayılanı (alan bazında); bozuk JSON → bütün varsayılanlar.
- **Varsayılanlar** (`:124-129`): su açık, 45 dk, gün içinde; kontrol kapalı, 60 dk, gün içinde; yemek kapalı, saatli 15:15, `onlyShift = false`.
- Sabitler: `reminderLateMin = 3`, `reminderShowFor = 30 sn` (1.7.0'da 20 → 30), `fmtMinuteOfDay(m) = "SS:DD"`.
- **`reminderDue(it, now, last?, shiftStart?, shiftActive, snoozeUntil?)`** (`:157-183`): kapalı ya da **gün kapalı** → false (`onlyShift = false` da olsa); erteleme varsa `now ≥ snoozeUntil`; saatli: her saat için bugünün o dakikası; `now` ondan önce ya da 3 dk ve daha geç → atla; bugün o saatten sonra gösterildiyse atla; değilse true; aralıklı: `from = since`; `onlyShift` ve gün başı daha yeniyse gün başı; son gösterim daha yeniyse o; `now − from ≥ intervalMin` dk.
- **WEEM:** `logic/map` `reminders.ReminderKind`, `ReminderItem`, `ReminderSettings` (+ kodek), `ReminderDue`; saat `Clock.zone()` (YolGo'da da yerel).

### 13.16 Y20: hatırlatıcı denetleyicisi, kart ve ayarlar (`reminders.dart:187-351`, `reminder_widgets.dart`)

- **Ayarlar** (`ReminderSettingsController`, `:187-227`): açılışta `since == 0` olan öğelere şimdi (açılır açılmaz hatırlatmasın); `updateItem(tür, değişiklik)`: kapalıdan açığa geçişte ya da `intervalMin`, `onlyShift`, `useTimes` değişince `since = şimdi` (açıktan kapalıya, metin ve saat listesi değişimi sıfırlamaz); her değişiklikte `reminders.v1` yazılır.
- **Çalışan** (`ReminderController`, `:231-351`): son gösterimler `reminders.last.v1` (`"<tür>:<ms>"` listesi; bozuk öğe atlanır). Gün değişiminde: zamanlayıcılar ve ertelemeler sıfırlanır; gün kapandıysa kart kalkar ve 7100–7102 iptal; açıldıysa 20 sn'de bir `check`, gün yeni açıldıysa (önceki yok) ve açık hatırlatıcı varsa bildirim izni istenir. `check(now)`: kart görünürken ya da gün yokken yok; gün açık ve aynı gün değilse yok; öğe sırasıyla (su, kontrol, yemek) ilk zamanı gelen gösterilir (bir denetimde en çok bir).
- **`_fire(it, now, record)`**: kaydediliyorsa (önizleme değil) gün açık ve aynı olmalı; son gösterim = şimdi, erteleme silinir, `reminders.last.v1` yazılır; kart durumu; güçlü titreşim; bildirim **7100 + tür sırası** başlık "<emoji> <başlık>", metin mesaj, gün kimliğiyle (önizlemede `preview`); olay `reminder {k: tür, preview?: true}`; 30 sn sonra (hâlâ aynı gösterimse) kart kalkar, bildirim iptal. `done()` ("Tamam"): kart kalkar, bildirim iptal. `snooze(10 dk)`: kodda var, düğmesi yok. `preview(tür)` ("Dene"): bildirim izni ister, zamanlamayı bozmadan gösterir.
- **Sistem bildirimi** (`MainActivity.notify` / `showReminder`, `:259-268, 392-431`): gün kimliği açık günle eşleşmeli (ya da önizleme ve uygulama önde); uygulama öndeyken ve önizleme değilse **yalnız kart** (`NotifyPolicy.inAppOnly`); kanal `hatirlatici` "Hatırlatıcılar" (HIGH, açıklama "Su iç, kontrol et, yemek ye", titreşim `0, 350, 200, 350`), `CATEGORY_REMINDER`, 30 sn zaman aşımı, `setOnlyAlertOnce`, `setAutoCancel`, dokununca uygulama (istek kodu = bildirim no). Görev bildirimi 7201 aynı yol ve kanaldan (13.1 düzeltmesi). İzin: Android 13+ `POST_NOTIFICATIONS` (istek 701).
- **Kart** (`ReminderCard`, `reminder_widgets.dart:20-59`): tek satır, küçük (≤ 44 px; `reminder-card`): emoji, başlık (tür rengi: su mavi, kontrol turuncu, yemek yemek rengi) + " · <mesaj>" (taşarsa üç nokta), ✓ düğmesi (`reminder-done`, ipucu "Tamam" / "Oldu"); erteleme düğmesi yok. Yer: ana ekranın üstünde, toast şeridinin altında (`nav_screen.dart:70, 119`).
- **Ayarlar bölümü** (`ReminderSettingsSection`, `_ReminderTile`, `:61-230`; Ayarlar → "Hatırlatıcılar" / "Xatırlatmalar"): her tür bir satır (`reminder-tile-<tür>`): emoji, başlık (açıksa renkli), özet ("Kapalı" / "Söndürülüb"; saatli "saat SS:DD, …" ya da "saat seçilmedi" / "saat seçilməyib"; aralıklı "her N dk" / "hər N dəq"; `onlyShift` ise + " · gün içinde" / " · iş günü ərzində"), açma anahtarı (açınca bildirim izni); açık satıra dokununca ayrıntı: "Aralıkla" / "Aralıqla" – "Saatte" / "Saatda" seçici; aralıkta "Her" / "Hər", − / "N dk" / + (5'er, 10–240); saatlide saat çipleri (silinebilir) + "Saat ekle" / "Saat əlavə et" (seçici varsayılanı 15:15; aynı dakika tekrar eklenmez, sıralı); "Sadece gün başlatılmışken" / "Yalnız gün başladılanda" anahtarı; mesaj (2 satır), "Metni düzenle" / "Mətni dəyiş" (pencere başlığı "<emoji> <başlık> — metin" / "— mətn", ipucu varsayılan metin, "Vazgeç" / "Ləğv et", "Kaydet" / "Yadda saxla"), "Dene" / "Yoxla". Altta "Nasıl çalışır" / "Necə işləyir" açılımı: TR "Uygulama öndeyken ekranda kart, Wolt/Bolt öndeyken bildirim gelir (ses + titreşim). "Sadece gün başlatılmışken": Gün bitince tüm hatırlatmalar susar; önizleme düğmesi bu ekranda denenebilir."; AZ metni fazladan "Yalnız təyin olunan vaxtda gəlir. ✓ ilə bağlanır; toxunulmasa 20 san sonra gedir və növbəti vaxtında yenidən gəlir." cümlelerini içerir (TR'de yok; "20 san" kodla çelişir, kod 30 sn).
- **WEEM:** saf `reminders.ReminderController` (durum, zamanlama, kayıt istekleri), Android `:map` `ReminderRuntime` (20 sn zamanlayıcı, gün sinyali gün motorundan (`DayView`), `NotifyPort` + `NotificationIds.reminder(kind)`, kanal `hatirlatici` iki dilli adla, CORE.md 3.3); `:app` `ReminderCard` (uygulama kabuğunda, 13.14), `ReminderSettingsSection` (Harita ayarları → "Hatırlatıcılar"). **Metin onayı (M-13-4):** bilgi metni TR ve AZ eşitlenir: "Yalnız belirlenen zamanda gelir; ✓ ile kapanır, dokunulmazsa 30 sn sonra gider ve sonraki zamanında yeniden gelir. WEEM öndeyken ekranda kart, değilse bildirim gelir (ses + titreşim). "Sadece gün başlatılmışken": gün bitince bütün hatırlatmalar susar; "Dene" ile bu ekranda denenebilir." / "Yalnız təyin olunan vaxtda gəlir; ✓ ilə bağlanır, toxunulmasa 30 san sonra gedir və növbəti vaxtında yenidən gəlir. WEEM açıq olanda ekranda kart, olmayanda bildiriş gəlir (səs + titrəmə). "Yalnız gün başladılanda": gün bitəndə bütün xatırlatmalar susur; "Yoxla" ilə bu ekranda yoxlanıla bilər."

### 13.17 Veri

| Ad | Tip · biçim | YolGo yeri | WEEM |
|---|---|---|---|
| `TeklifVeriSeti/<YYYY-MM-DD_Gün>/` | gün klasörü: `yolgo-live.jsonl`, `yolgo-routes.jsonl`, `yolgo-poll.jsonl`, `shifts.jsonl`, `manual.jsonl`, `attachments/`, `rawstats/` (+ `feed-manifest.json`), `collection-status.json`, `manifest.json` | `app_flutter/TeklifVeriSeti/` | `files/TeklifVeriSeti/`, aynı adlar ve biçimler; `yolgo-poll.jsonl` yazılmaz (13.11) |
| `TeklifVeriSeti/YolGo_teklif_<klasör>.zip` | gün paketi | aynı kök | `WEEM_teklif_<klasör>.zip` (M-13-1) |
| `offer_dataset_v1` (`region`, `active`) | yerel tercih | native | **yok** (servis yok; bölge `MapSettings.region`, 14.2) |
| `noBackupFilesDir/economics-day-secrets-v3/<gün>` | 32 bayt gün sırrı | Rawstats | aynı yol (`VERI_SOZLESMESI` 7.1 notu) |
| `daily.tasks.v1` | String JSON dizi `{id, title, place, point, due, completed?}` | Flutter tercihleri | `map` tercih dosyası, aynı ad ve biçim (S1: yeniden girilir) |
| `daily.tasks.closed.v1` | String JSON harita `id → ms` | aynı | aynı |
| `reminders.v1` | String JSON `{water \| check \| food: {on, text, useTimes, every, at, shift, since}}` | aynı | aynı (S1: yeniden girilir) |
| `reminders.last.v1` | List<String> `"<tür>:<ms>"` | aynı | `map`, JSON dizi dizgesi (14.1) |
| bildirimler | 7100–7102 `REMINDER(kind)`, 7201 `TASK_NEARBY` (kanal `hatirlatici`); 7401 (kanal `yolgo_offer_dataset`) | native | 7100–7102, 7201 aynı (CORE.md 3.3); 7401 kullanılmaz (13.11) |
| sürüş günlüğü olayları | `task_route_opened`, `reminder {k, preview?}` | `logs/` | aynı (14.5) |

Ağ: veri seti, görevler ve hatırlatıcılar ağ kullanmaz (görev konumunun çözümü Y6 geokodlayıcısıyla, 8.6). Gizlilik: veri setinde müşteri adı, telefonu, kapı kodu, not ve **adres metni** yok; noktalar 3 ondalık (~110 m); ekler opak, adları üretilmiş; `rawstats/` R18 anonimleştirmesinden geçer (gün sırrı dışarı çıkmaz). Görev başlığı ve yeri kullanıcının yazdığı metindir; yalnız `map` tercihlerinde kalır, bildirimde görünür.

### 13.18 Testler

| Dosya | Sayı | Test adları → neyi doğrular | WEEM hedefi | Görev | Not |
|---|---:|---|---|---|---|
| `dataset_days_test` | 2 | gece yarısını geçen vardiya iki takvim günü · telefonun saat dilimi aynı Bakü gününü değiştirmez | `dataset.DatasetDaysTest` | A6.2b → A6.9 | **2. test uyarlanır:** WEEM telefon saat dilimini kullanır (13.5); test sahte `Clock` (`Asia/Baku`) ile aynı sonucu doğrular, "bölgeden bağımsız" iddiası düşer |
| `dataset_screen_test` | 5 (döngüyle 8) | başarılı Yenile eski okuma hatasını temizler · toplama hatası başarılı liste okumasında kaybolmaz · açıklama duyuruyu ve iki yedek aralığı anlatır (TR, AZ) · gün özeti eksikleri ve platform sayılarını gösterir (kapalıyken görünmez; "Bilinmiyor"; "Haftalık ödeme") · 380 px'te günler kapalı, bağımsız açılır, yenile/topla açık günü korur (TR/AZ × açık/koyu) | `:app` Robolectric `DatasetScreenTest` 5 | A6.2b → A6.9 | **3. test uyarlanır:** M-13-2 açıklaması (yoklama aralıkları yok); kanal sahtesi yerine sahte `DatasetRuntime` |
| `assistant_test` (Y20) | 5 | görev saati/günü ve 1 km sınırı · kapatmak tamamlamaz, sonraki teslimat tekrar tetikler · yeniden giriş, histerezis, aktif sipariş · eski ya da hatalı GPS bildirim üretmez · kapatma ve tamamlanma yeniden açılışta okunur | `tasks.TaskTriggerTest` | A6.2b → A6.9 | öteki 9 test: gün motoru 6 (11.16), Y6 1 (8.11), `HeadingFilter` 1 (7.15), hava 1 (10.12) |
| `task_controller_test` | 1 (döngüyle 2) | dış rota açılırsa görev tamamlanır, açılmazsa bekler; yeniden açılışta korunur | `tasks.TaskControllerTest` | A6.2b → A6.9 | **uyarlanır (S9):** "Google Maps açıldı mı" yerine "WEEM navigasyonu hedefi kabul etti mi" (sahte navigasyon kapısı); döngü iki değerle aynen |
| `reminders_test` | 5 | aralık: gün başından N dk sonra, sonra her N dk · gün kapalıyken susar (`onlyShift` kapalıyken de) · saatli: yalnız belirlenen dakikada bir kez, 3 dk geçtiyse atlanır, ikinci saat · erteleme süresi dolunca · ayarlar gidiş-dönüş, eksik alan varsayılan, `fmtMinuteOfDay` | `reminders.ReminderDueTest` 4, `reminders.ReminderSettingsTest` 1 | A6.2b → A6.9 | `DateTime(2026,10,1)` yerel → sahte `Clock` |
| `reminder_card_test` | 3 | kart tek satır ≤ 44 px, uzun metin taşmaz · ✓ tamamlar, erteleme düğmesi yok · kart 30 sn | `:app` Robolectric `ReminderCardTest` 2, `reminders.ReminderTimingTest` 1 | A6.2b → A6.9 | yükseklik sınırı Compose'da `dp` |
| RT `LogFeedV3Test` | 10 | ekran ve tıklama satırları müşteri verisi olmadan R18 kuralıyla (`[card]`, `[note]`; `AnalysisExport.row` ile aynı) · gün sırrı yeniden yaratılınca aynı, manifestte yok · `offerKey` aynen, bilinmeyen saatler null + bayrak · enerji saatleri ve scooter kimliği korunur, güç doğrulanmamış · iç içe null bayrakları · `copyPrefix` sonraki eklemeleri almaz, kısalan kaynağı reddeder · büyük UTF-8 dosyada hash ve satır · yeni toplama eski anlık görüntüyü değiştirmez · bozuk satır ve olmayan dosya açık · yol, tarih, ofset ve sınır kapalı kalır | `dataset.DayLogSnapshotTest` 10 | A6.2b → A6.9 | **uyarlanır:** sayfalama (`page`) yok — 7. test sayfa yerine bütün dosyanın hash/satırı, 10. test yalnız gün ve yol denetimi (ofset/sınır kısmı düşer); `TEST_ENVANTERI`'nin "uyarlanır :core" satırı `logic/orders` `dataset`'e |
| YK `DatasetContractTest` | 6 | yalnız güvenli alanlar, kimlik korunur (müşteri adı, telefon, adres, not yok; alış 3 ondalık, bırakış null, ölçüler `unavailable`) · güvensiz anahtar ve ters zaman reddedilir · takvim günü gece yarısında bölünür, klasör "2026-10-10_Cumartesi" · Plus Code yalnız gözlenmiş referansla, varsayılan yok · okuma saati teklif değişikliği uydurmaz · iç içe null bayrakları | `dataset.DatasetContractTest` 6 | A6.2b → A6.9 | 3. test sahte `Clock` (`Asia/Baku`) ile |
| YK `DatasetMatchingTest` | 4 | tam anahtar + 5 sn yedek bire bir · belirsiz yedek tahmin edilmez · ücret yok ya da > 5 sn eşleşmez · aynı gün yeniden toplama yalnız en yeni dosyaları tutar, yarıda kalan değişimi onarır | `dataset.DatasetPackTest` 4 | A6.2b → A6.9 | |
| YK `DatasetReadsTest` | 6 | yoklama sabitleri (60 sn, 7 sn, 300 ms, 5 sn) · her satır okuma ölçüsünü taşır; eski Rawstats'ta gecikme null + bayrak; `changedAt` okuma · ölçü teklif değişikliği değildir; çekirdek iz · `lastSeen` ya da kurye değişimi teklif başına en çok 5 sn'de bir · bekletilen son görülme teklif gidince yazılır · sessiz okumalar tek okuma satırında | `dataset.DatasetMeasureTest` 1, `dataset.OfferRowGateTest` 3 | A6.2b → A6.9 | 1. test (yoklama sabitleri; yalnız `ROW_GAP_MS` kalır, 4. testte denetlenir) ve 6. test (`PollRowGate`) **çevrilmez** (13.11) |
| **Yeni** (karakterizasyon; eski test yok) | — | WEEM kayıt düzeni (iç teklif durumundan satır, sahte `RouteMeasurePort`, `WeatherPort` 20 dk kuralı, `yolgo-poll.jsonl` yok, gün sonu kancası sırası) · `DatasetManual` doğrulaması · `kontrol.py --self-test` ve WEEM ZIP'i (python yoksa atlanır) · "Rota Oluştur" → `goTo(purpose = task)`, `_bindShiftTarget` bağlamaz · hatırlatıcı `_fire` / `check` (gün bağı, sıra, önizleme, 30 sn, iptaller) · kartlar uygulama kabuğunda, sekmeden bağımsız | `dataset.DatasetRecorderTest`, `dataset.DatasetManualTest`, `dataset.DatasetToolTest`, `nav.TaskTargetTest`, `reminders.ReminderControllerTest`, `:app` Robolectric `AppAlertsTest` | A6.2b → A6.9 | değerler bu bölümden |

Toplam (bu bölüm): 47 test tanımı; çevrilen 45 (13'ü uyarlanır: `LogFeedV3Test` 10, `dataset_days` 1, `dataset_screen` 1, `task_controller` 1), çevrilmeyen 2 (`DatasetReadsTest`); ayrıca `kontrol.py` öz-testleri (8, Python) araçla birlikte koşar.

### 13.19 WEEM yerleşimi

- `logic/orders` **`dataset`**: `DatasetContract` (yoklama sabitleri hariç), `DatasetFiles` (saf), `OfferRowGate`, `DatasetRecorder`, `DatasetEnricher` (bekleyenler, önbellek, `RouteMeasurePort`), `DatasetDays`, `DatasetShifts`, `DatasetManual`, `DayLogSnapshot`, `DailyLogSecret`, `DatasetPack`. Kullandıkları: `logic/core` (`ShiftState`, `WeatherNow`, `LogTime`, `BackupFiles`), `logic/orders` (`AnalysisExport`, iç teklif durumu).
- `logic/map` **`tasks`**: `DailyTask`, `TaskTrigger`, `TaskController`; **`reminders`**: `ReminderKind`, `ReminderItem`, `ReminderSettings`, `ReminderDue`, `ReminderController`. Kullandıkları: `geo`, `input` (görev konumu çözümü), `nav` (görev hedefi).
- Android `:orders`: `DatasetRuntime` (kök `files/TeklifVeriSeti/`, tek kilit, `ShiftHub` kancası, belge seçiciden kopya, sürüm). Android `:map`: `RouteMeasurePortImpl` (rota motoru ve DEM, 13.4), `TaskRuntime`, `ReminderRuntime`.
- `:core`: **`RouteMeasurePort`** (yeni, öneri; `:map` → `:orders`), `NotifyPort` + `NotificationIds` (7100–7102, 7201), `ShiftHub`, `Clock`, `WeatherPort`.
- `:app`: `DatasetScreen`, `DatasetManualSheet`, `TasksScreen`, `TaskEditorSheet`, `TaskAlertCard` ve `ReminderCard` (kabukta), `ReminderSettingsSection`; girişler Harita ayarları → "Teklif veri seti", "Hatırlatıcılar", "Günlük işler" (6.9), uzun basış satırı 4 (6.7).
- **2 telefon (öneri):** Y19 Provider'da (13.11); görevler ve hatırlatıcılar Receiver'da (gün motoru, teslim olayı ve konum orada; ekran scooter'da görünür), Provider'da çalışmaz (çift bildirim olmasın) → SORU-13-3.

### 13.20 Farklar, açık noktalar ve sorular

- **Bilinen davranışlar (korunur):** görevde "Tamam / Kapat" tamamlamaz; uygulama bölge içinde açılırsa kapatılmış görev yeniden girişle gelmez (13.12); hatırlatıcılar `onlyShift` kapalıyken de yalnız gün açıkken gelir (13.15); bir denetimde en çok bir hatırlatıcı, kart varken yenisi yok; `snooze` kodda var, düğmesi yok; veri seti listesi her açılışta bütün günlerin özetini yeniden hesaplar; gün paketi her vardiya sonunda kendiliğinden yazılır, ZIP'ler otomatik silinmez.
- **Eski hatalar (WEEM'de baştan doğru):** hatırlatıcı bilgi metninin TR/AZ farkı ve "20 san" (M-13-4); veri seti ekranı metinlerinin yalnız TR olması (M-13-2); "Google Maps açılamadı" metni (S9, M-13-3).
- **WEEM farkları:** veri seti `:orders`'ta, servis ve okuma yok (`yolgo-poll.jsonl` yok), Rawstats sağlayıcısı ve izni yok, gün telefon saat dilimiyle (13.5, 13.11); hava `WeatherPort` (20 dk kuralı korunur); eşleşme oranı tek uygulamada ~1,0; "Rota Oluştur" WEEM navigasyonu (S9), görev hedefi siparişe bağlanmaz; görev ve hatırlatıcı kartları uygulama kabuğunda.
- **SORU-13-1 (koordinatöre; yeni özellik sınırı):** teklif satırında alış noktası çoğunlukla boş (Wolt alış adresi koordinat taşımaz), bu yüzden rota ölçümü çoğu satırda yok. (a) YolGo gibi: yalnız adres metninde koordinat / Plus Code varsa; (b) restoran adı WEEM'in restoran kaydından (`restaurants.json`, R6) ya da harita POI'lerinden (Y16) eşleşirse o nokta, bayrak `restaurant_registry` / `poi_name_match` (yeni davranış; rota ölçümü çoğu satırda dolar); (c) (b) yalnız gölge modda. Öneri (a) WEEM 1.0'da, (b) `BEKLEYEN_OZELLIKLER`'e aday.
- **SORU-13-2 (koordinatöre; 2 telefon):** 2 telefonda veri seti Provider'da; `economics-scooter.jsonl` Receiver'da yazıldığı için paketin scooter kapsaması ve `scooter_id` boş kalır. (a) Kabul: Provider paketi scooter alanlarını "Bilinmiyor" bırakır (bugün de scooter verisi yoksa öyle); (b) A8.3'te Receiver gün sonunda o günün scooter örnek zamanlarını ve kimliğini gönderir; (c) veri seti Receiver'da toplanır, gün kayıtları Provider'dan istenir. Öneri (a).
- **SORU-13-3 (koordinatöre; 2 telefon):** görevler ve hatırlatıcılar hangi telefonda? (a) Receiver (harita, konum ve gün motoru orada; ekran görünür); (b) Provider (cepte; titreşim hissedilir); (c) ikisi (çift bildirim). Öneri (a).
- **M-13-1:** ZIP adı ve paylaşım metni (13.7). **M-13-2:** veri seti ekranı metinleri (13.8). **M-13-3:** "Rota açılamadı; iş bekliyor." / "Marşrut açılmadı; iş gözləyir." (13.13). **M-13-4:** hatırlatıcı bilgi metni (13.16).
- **Risk:** `rawstats/` kopyası gün başına MB'larca (`gps.csv`, `screens.jsonl`) ve ZIP; depolama A10'da ölçülür. `RouteMeasurePort` her teklif satırı için rota hesabı ister (önbellek 128, en çok 16 bekleyen); süre A6.4'ün büyük harita ölçümüyle birlikte değerlendirilir.
### 13.21 A6.9 uygulama notları (20 Ekim 2026)

- **Saf:** `logic/map` `tasks` (`DailyTask`, `TaskTrigger`, `TaskController` — gün kapısı `TaskRuntime`'da; `TaskEvents`: bildirim 7201 ve `task_route_opened`; `daily.tasks.v1`, `daily.tasks.closed.v1`), `reminders` (`ReminderItem`/`ReminderSettings` kodeği, `ReminderDue`, `ReminderController`: ayar sayacı kuralları, 20 sn denetim, 30 sn gizleme, önizleme, `reminders.last.v1`), `ui.StatsText/RideLogText/DatasetText/TaskText/ReminderText` (iki dilli). `logic/orders` `dataset` — `DatasetContract` (13.3; `offer(…, zone)` ile `timezone`/`utcOffsetMinutes`), `DatasetDays`, `OfferRowGate`, `DayLogSnapshot` + `DailyLogSecret`, `DatasetPack` (`match`, `summary`, `seal`, `list`, `collect`; ZIP `WEEM_teklif_<klasör>.zip`), `DatasetFiles`, `DatasetShifts`, `DatasetRecorder` (iç teklif durumundan satır; `OrderFlow.exportableOffers`), `DatasetManual`.
- **Android:** `:map` `TaskRuntime` ("Rota Oluştur" = `goTo(purpose = StopPurpose.TASK)` + Harita sekmesi; görev hedefi siparişe bağlanmaz), `ReminderRuntime` (bildirim `7100 + tür`, kanal `hatirlatici`, titreşim; WEEM öndeyken yalnız kart: `AppForeground`), `NotificationIds.CHANNEL_REMINDER`; `:app` `AppDatasetRuntime` (kök `files/TeklifVeriSeti/`, gün klasörü `Store.locked` altında dondurulur) + `DatasetRecording` (vardiya açılınca/kapanınca `shifts.jsonl`, saniyelik tik → `yolgo-live.jsonl`, `ShiftHub.listeners["dataset"]` ile gün toplama + mühür), `DatasetManualSheet`, `TasksScreen`/`TaskEditorSheet`/`TaskAlertCard` ve `ReminderCard` uygulama kabuğunda, `ReminderSettingsSection`. `tools/veri_seti/kontrol.py` + README (başlık WEEM; WEEM ZIP'i denetimden geçer).
- **Açık:** rota/yükseklik ölçümü (`RouteMeasurePort`, 13.4) ve `yolgo-routes.jsonl` yazılmıyor (alanlar `unavailable`, `routeComputedRatio` 0); elle ekleme formunun AZ metinleri (M-13-2 kapsamı) onay bekler; veri seti satırı kurye noktası `NavRuntime.lastFix` (yaş −5…+30 sn, doğruluk ≤ 65 m); 2 telefon düzeni (SORU-13-2/13-3) A8.3'te.

<!-- END sec:13 -->

<!-- BEGIN sec:14 -->
## 14. Veri sözleşmesi

`:map`'in kalıcı adları ve biçimleri tek yerde. Üst sözleşme `VERI_SOZLESMESI.md` (5.4 YolGo tercihleri, 7.3 YolGo dosyaları, 8 bildirimler; bir adı değiştirmek kullanıcı kararı); bu bölüm WEEM tarafındaki yeri, tipi ve okuma kuralını verir, ayrıntı ilgili bölümün "Veri" alt bölümündedir. **S1:** YolGo'nun tercihleri ve dosyaları WEEM'e kendiliğinden geçmez (WEEM başka paketin verisini okuyamaz, eski YolGo'ya dışa aktarma eki yok); her şey boş başlar, yeniden girilir ya da yeniden öğrenilir (Y12 S3). Bu yüzden YolGo'nun **göç kuralları ve göç işaretleri WEEM'de yazılmaz** (14.2, 14.3); okuma kuralları (bozuk değer → varsayılan) aynen kalır, çünkü WEEM'in kendi yazdığı veri de bozulabilir. Tek istisna: YolGo'nun bugünkü sürüş günlüğü dışa aktarması `duzyol-journal-v1` WEEM'e içe aktarılır (plan Bölüm 1, iş A7.1; `VERI_SOZLESMESI` 7.3) — 12.15 ve 14.9.

### 14.1 Depolama yerleri ve tip eşlemesi

| Yer | WEEM | Ne |
|---|---|---|
| Tercih dosyası **`map`** | `SharedPreferences("map", MODE_PRIVATE)`; saf arayüz `logic/map` `MapPrefs`, Android `:map` `MapPrefsStore` (Bölüm 4.7) | `settings.v1` (14.2) ve küçük durumlar (14.3); YolGo adları **`flutter.` öneki olmadan**, aynı ad ve biçim |
| Tercih dosyası `kuryelog` | `:core` (`VERI_SOZLESMESI` 5.1) | YolGo'dan oraya geçenler: `lang` (`Lang.PREF_KEY`), `theme` (`Shell.PREF_KEY`), ev `home.lat`/`home.lon` (`KuryelogPrefs`) |
| `files/mapdata/` | Y7 (Bölüm 2.12) | graf, DEM, POI, parça önbelleği (YolGo `files/` = support dizini; yol aynı) |
| `files/map/` | WEEM `:map` (yeni alt klasör) | büyüyen JSON blobları (Y12 `roads/`, `manual-marks.json`), hava önbelleği, sürüş günlüğü ve gün raporları `files/map/logs/` (Bölüm 12) |
| `files/TeklifVeriSeti/` | WEEM `:orders` (Y19, Bölüm 13.11) | teklif veri seti gün klasörleri ve ZIP'leri (YolGo `app_flutter/TeklifVeriSeti/`) |
| APK asset'leri | `:map` modülü `assets/map/`, `assets/baku/` | gömülü harita, bölge fotoğrafları (Bölüm 2.12, 5.5) |

Tip eşlemesi (`map` dosyası; YolGo Dart `SharedPreferences` → Android):

| YolGo tipi | WEEM yazımı | Okuma |
|---|---|---|
| `String` | `putString` | aynı |
| `bool` | `putBoolean` | aynı |
| `int` (64 bit) | `putLong` | aynı (ör. `clipboard.lastHash` 0…2³²−1, ms damgaları) |
| `double` | `putString(Double.toString(v))` (Flutter da Android'de double'ı önekli String saklar; `putFloat` 32 bit olduğu için kullanılmaz) | `toDoubleOrNull()`; sonlu değilse yok sayılır |
| `List<String>` | `putString(JSONArray(liste).toString())` — sıra ve tekrar korunur (`putStringSet` sırayı ve tekrarı kaybeder) | dizi değilse ya da öğe dizge değilse anahtar yok sayılır (YolGo'da okunamayan liste = yok) |

Yazım: bir eylemin bütün anahtarları tek `edit()…apply()`'da (Bölüm 4.2: sokak kararlarının beş anahtarı birlikte). Okuma hatası hiçbir zaman çökme değildir; anahtar yok sayılır (YolGo davranışı) ve `FailureSink`'e tek satır düşer (`:core` `guarded`). `map` dosyası ve `files/map*` S22 haftalık şifreli yedeğe bugün girmez (yalnız `kuryelog` ayarları; SORU-Q5 Y12 için soruyor, 17'de toplu); `allowBackup="false"` + `dataExtractionRules` (`VERI_SOZLESMESI` 1) Android yedeğine de sokmaz.

### 14.2 `MapSettings` — `settings.v1` alan tablosu

YolGo: `FlutterSharedPreferences` → `flutter.settings.v1` (JSON nesne, `settings.dart:17-361`). WEEM: `map` dosyasında **`settings.v1`** (String JSON), `logic/map` `settings.MapSettings` (değişmez veri sınıfı) + `MapSettingsCodec` (aynı alan adları); Android `:map` `MapSettingsStore` (`StateFlow<MapSettings>`, `update { }`).

**Okuma kuralı** (`fromJson`, :276-320; `SettingsController.build`, :339-348): dizge yok → bütün varsayılanlar; JSON değil ya da nesne değil → bütün varsayılanlar; alan yok ya da `null` → o alanın varsayılanı; **alan var ama tipi yanlış** (ör. sayı yerine dizge) → Dart `as` dökümü istisna atar → **bütün ayarlar varsayılana döner** (alan bazında değil). Bilinmeyen anahtarlar (ör. eski `voice`) yok sayılır. Sayılar `num` → `double` (tam sayı da kabul). WEEM `MapSettingsCodec.decode` aynı sonucu verir (`settings.MapSettingsTest`; Bölüm 10.12 `v140_features` "ayar" testleri). **Yazma kuralı** (`update`, :350-353): her değişiklikte bütün nesne yeniden yazılır.

| Alan | Tip · varsayılan | Okuma / sınır | Arayüz (YolGo) | Kullanan | WEEM |
|---|---|---|---|---|---|
| `vehicleId` | String · `g3` | bilinmeyen kimlik → `g3` profili (`vehicleById`; `g3` 598 Wh, `g3x` 1066 Wh) | yok (1.7.0'da kalktı) | Y3, Y9 (3.2, 10.3) | `MapSettings` (ekran yok) |
| `flatWhPerM` | double · 0.0135 | — | yok | Y3, Y9 | `MapSettings` |
| `climbWhPerM` | double · 0.38 | — | yok | Y3, Y9 | `MapSettings` |
| `climbSafety` | double · 1.0 | — | yok | Y3 (`speedOption`'da; `EnergyOdometer`'da yok, 10.14) | `MapSettings` |
| `flatKmh` | double · 28.5 | — | yok | Y3, Y9 | `MapSettings` |
| `climbKmh` | double · 18.5 | — | yok | Y3, Y9 | `MapSettings` |
| `walkKmh` | double · 4.5 | — | yok | Y3 | `MapSettings` |
| `maxGradePct` | double · 18 | `gradeLimitFor`: batarya < `lowBatteryPct` → `lowBatteryGradePct`, değilse bu; /100 | yok | Y3, Y4 | `MapSettings.gradeLimitFor` |
| `lowBatteryGradePct` | double · 10 | aynı | yok | Y3, Y4 (7.8) | `MapSettings` |
| `lowBatteryPct` | double · 50 | aynı; batarya kutusu turuncu eşiği (10.5) | yok | Y4, Y9 | `MapSettings` |
| `homeReturnPct` | double · 30 | "Batarya düşük — eve dön?" eşiği (7.8) | yok | Y4 | `MapSettings` |
| `mainRoadPenalty` | double · 1.15 | — | yok | Y3 | `MapSettings` |
| `walkRadius` | double · 150 | — | yok | Y3 | `MapSettings` |
| `turnPenaltyS` | double · 12 | — | yok | Y3 | `MapSettings` |
| `qualityTolerancePct` | double · 10 | okuma kıstırmaz; `qualityWeight = /100` | kaydırıcı 0–30, 30 adım (`surface_screen.dart:74`, Y13 ekranında) | Y3, Y12 | `MapSettings`; kaydırıcının WEEM yeri Bölüm 9 |
| `bikewayCostPct` | double · 85 | okurken 50–100'e kıstırılır (:315); `bikewayFactor = clamp(50,100)/100` | kaydırıcı 50–100, 10 adım (`bikeway_layer.dart:128`) | Y3, Y15 | `MapSettings` |
| `showBikewayLayer` | bool · true | — | anahtar (`bikeway_layer.dart:120`) | Y15 | `MapSettings` |
| `speedChoices` | double[3] · `[20, 25, 29]` | dizi değil, uzunluk ≠ 3 ya da öğe 5–45 dışı/sayı değil → varsayılan (`_speeds`, :329-333) | `SpeedSheet` ±1, 5–45 (`widgets.dart:252`) | Y9 (10.2) | `MapSettings` |
| `lightMaxM` | double · 5 | YolGo göçü: `lightMaxMv3 != true` ve kayıtlı 25 → 5 (`_lightMaxM`, :326-327) — **WEEM'de yok** (S1) | sayı satırı 3–40 m, adım 1 (`settings_screen.dart:291`) | Y11 (Bölüm 11) | `MapSettings` |
| `volumeKeys` | bool · true | — | anahtar (`settings_screen.dart:305`) | Y12 (9.5) | `MapSettings` |
| `showQualityLayer` | bool · false | — | anahtar (Y13 ekranı) | Y13 (sonra) | `MapSettings` (1.0'da etkisiz, Bölüm 9) |
| `wwMaxProblemPct` | double · 50 | — | adımlayıcı 20–90, adım 5 (`roads_screen.dart:290`) | Y3, Y12 | `MapSettings` |
| `wwAskAlways` | bool · false | — | anahtar (`roads_screen.dart:301`) | Y12 | `MapSettings` |
| `logging` | bool · true | — | anahtar (`ride_logs_screen.dart:137`) | Y18 (Bölüm 12) | `MapSettings` (anlamı Bölüm 12) |
| `region` | String · `central` | `wide` dışındaki her değer merkez kutu (`regionBox`) | kurulum ve Harita verisi seçicisi (`setup_screen.dart:76`, `settings_screen.dart:469`) | Y7 (2.9) | `MapSettings` |
| `mapStyle` | String · `minimal` | YolGo göçü: `mapDesignV1 != true` ve (`null` ya da `osm`) → `minimal` (:317-318) — **WEEM'de yok** (S1) | altlık seçici (`settings_screen.dart:68`) | Y1, Y2 (6.4) | `MapSettings`; değerler SORU-N5 |
| `googleKey`, `orsKey`, `cartoKey` | String · `""` | — | "API ve bağlantılar" anahtar penceresi (`api_settings.dart:85-102`) | Y6, Y1 (8.9, 6.13) | `MapSettings` aynı adlar; **sır** (14.10); `cartoKey` yalnız CARTO kalırsa (SORU-N5) |
| `home` | `{lat, lon}` ya da `null` | `GeoPoint.fromJson`: nesne değil ya da sayı değil → `null` | Rawstats'tan önbellek (`rawstats_home.dart:90`), `clearHome` | G1, G2, Y4 | **`MapSettings`'e girmez:** `:core` ev (`kuryelog` `home.lat`/`home.lon`, CORE.md) |
| `theme` | String · `dark` (`dark` \| `dark-ui` \| `light` \| `auto`) | — | "Görünüm" (`settings_screen.dart:162`) | Y1 | **girmez:** `:app` `theme` (`kuryelog`, A1.3; Bölüm 6.2) |
| `lang` | String · `""` (`tr` \| `az` \| `""` telefon) | — | kurulum, "Dil" | G3 | **girmez:** `:app`/`:core` `lang` (`kuryelog`) |
| `googleMapCompatibility` | bool · false | — | geliştirici anahtarı (`api_settings.dart:79`) | Flutter platform görünümü | **girmez** (6.13) |
| `autoStartDay`, `autoEndDay`, `followCourierStages` | bool · true | okurken ve her yazımda **zorla `true`** (:312-314, 351) | yok (1.10.0) | G1 | **girmez:** tek vardiya kuralı `:core` (CORE.md, S2) |
| `lightMaxMv2`, `lightMaxMv3`, `mapDesignV1` | bool · her yazımda `true` | göç işaretleri | — | — | **girmez** (S1) |

Türetilenler (`MapSettings` yöntemleri, saf): `vehicle` (profil), `regionBox` (`Baku.wideBox` / `Baku.centralBox`, Bölüm 2.2), `gradeLimitFor(batteryPct)`, `paramsFor(batteryPct)` → `RideParams` (`qualityWeight = qualityTolerancePct/100`, `bikewayFactor = clamp(bikewayCostPct, 50, 100)/100`; Bölüm 3.2). `qualityTolerancePct`, `wwMaxProblemPct`, `turnPenaltyS`, `bikewayCostPct` değişince açık rota yeniden hesaplanır (`replan('prefs')`, Bölüm 7.5); öteki alanlar sonraki hesapta etkili olur.

### 14.3 `map` tercih dosyasının öteki anahtarları

| Anahtar (YolGo adı) | Tip · biçim | Bölüm | WEEM |
|---|---|---|---|
| `map.bundled.<region>`, `map.bundledAt` | String sürüm | 2.9, 2.12 | aynı |
| `streets.approved`, `streets.blocked`, `streets.steep` | List<String> way id | 4.2 | aynı |
| `streets.names`, `streets.hours.v1` | List<String> `"<id>\|<ad>"`, `"<id>\|<dk>\|<dk>"` | 4.2, 4.3 | aynı |
| `streets.blocked.legacy.v1`, `streets.blocked.migrated.v2` | göç yedeği ve işareti | 4.2 | **yazılmaz** (S1) |
| `streets.bikeway.v1` | List<String> `"id\|ad"` | 5.1, 5.5 | aynı |
| `places.local.v1` | List<String> JSON | 5.3, 5.5 | aynı |
| `passive.pausedAt.v2` | int ms | 6.2, 6.13 | aynı |
| `clipboard.lastHash` | int (FNV-1a 32) | 8.3, 8.10 | aynı (`Long`) |
| `search.recents.v2` | String JSON dizi (≤ 12) | 8.8, 8.10 | aynı |
| `battery.setPct`, `battery.consumed`, `battery.setAt` | double, double Wh, int ms | 10.3 | aynı |
| `weather.enabled.v1` (true), `weather.strip.v1` (true), `weather.key.v1` (""), `weather.permission.asked.v1` | bool, bool, String (**sır**), bool | 10.7 | aynı |
| `lights.v1` | List<String> `"lat,lon,yes,no,waitS"` (6 hane) | 11.11 | aynı (JSON dizi dizgesi) |
| `sidewalk.zones.v1` | String JSON dizi `[{lat, lon}]` | 11.12 | aynı |
| `shift.v1`, `shift.last` | String JSON (`DayState`, `v: 2`; son gün) | 11.5, 11.14 | aynı |
| `courier.follow.v1` | String JSON (aşama izleyici) | 11.7 | aynı |
| `day.shift.handled.v1` | String JSON `{"end": ms?, "start": ms?}` | 11.2 | **yeni** (`ShiftFollower` damgaları) |
| `shift.deleted.days.v2` | List<String> gün kimlikleri | 12.9 | aynı (JSON dizi dizgesi) |
| `ride.active.v1` | String, açık sürüş dosya adı | 12.3 | aynı |
| `daily.tasks.v1`, `daily.tasks.closed.v1` | String JSON dizi, String JSON harita | 13.13 | aynı |
| `reminders.v1`, `reminders.last.v1` | String JSON, List<String> `"<kind>:<ms>"` | 13.15, 13.16 | aynı (liste JSON dizi dizgesi) |
| `day.home.v1` | ev kuralı durumu | G1 | **`map`'e girmez:** `:core` ev kuralı (`HomeShift`, CORE.md) |
| `rawstats.shiftEnd.handled.v1`, `rawstats.shiftStart.handled.v1` | int ms | G1 | **girmez:** yerine `day.shift.handled.v1` (11.2) |
| `rawstats.home.lastSuccessAt.v1` | int ms | G2 | **girmez** (S7: Rawstats ev okuması yok) |
| `surface.paused.day`, `surface.raw.debug` | Y13 | — | sonra |

YolGo'nun **yerel (Kotlin) tercih dosyaları**: `duzyol_weather` (Y10; WEEM'de `map` + `files/map/weather.json`, Bölüm 10.11), `yolgo_runtime_v2` (`TelemetryService`: `day`, `leg`, `eligible`, `paused`, `rawDebug`, `calibrationLabel`, `profile`, `boot`; `VERI_SOZLESMESI` 5.4'te eksikti, 14.11) — WEEM'de **yazılmaz** (servis yok, 12.11), `offer_dataset_v1` (`region`, `active`) — **yazılmaz** (servis yok, 13.11), `road_calibration_overlay_v1` (Y14, sonra). WEEM'de ayrı yerel dosya açılmaz.

### 14.4 Dosyalar ve asset'ler

| YolGo yolu | Biçim | Bölüm | WEEM |
|---|---|---|---|
| `files/mapdata/graph_<region>.bin` | KNG1 v2 ikili (**değişmez**, 2.5) | 2.5, 2.12 | aynı yol ve biçim |
| `files/mapdata/dem/N40E049.hgt`, `N40E050.hgt` | SRTM HGT | 2.7 | aynı |
| `files/mapdata/pois_<region>.json` | sıkı POI JSON | 2.10, 5.3 | aynı |
| `files/mapdata/parts_graph_<region>/*.json.gz` | Overpass parça önbelleği (2 gün) | 2.9 | aynı |
| APK `assets/map/manifest.json`, `graph_wide.bin.gz`, `N40E049.hgt.gz`, `N40E050.hgt.gz`, `pois_wide.json.gz`, `pois_central.json.gz` | gömülü harita (CI) | 2.11 | `:map` `assets/map/`, aynı adlar |
| APK `assets/baku/*.jpg`, `credits.json` | bölge fotoğrafları (CI Wikimedia) | 5.4 | `:map` `assets/baku/`, aynı adlar |
| `files/surface-v2/manual-marks.json` (Y13 deposu) | elle kötü noktalar | 9.10 | `files/map/manual-marks.json` (aynı biçim; SORU-Q1) |
| tercihlerde `roads.*` (8 anahtar) | JSON blob | 9.10 | `files/map/roads/<anahtar>.json` (birebir JSON; S3 sıfırdan) |
| `duzyol_weather` → `report` | Open-Meteo yanıtı + meta | 10.7 | `files/map/weather.json` (öneri, 10.11) |
| `app_flutter/logs/YYYY-MM-DD.jsonl`, `…_ride_<ms>.jsonl`, `…_HHMM.gun.json` (eski `….gun.json`), `…_route_<ms>.json` | sürüş günlüğü, sürüş, gün raporu, rota kaydı (`duzyol-route-v1`) | 12.2–12.6 | `files/map/logs/`, aynı adlar ve biçimler |
| `files/telemetry-v2.jsonl` (≤ 3000 satır) | yerel telemetri günlüğü (Y13 kanıtı) | 12.11 | **yok** (Y13 ile gelir) |
| `app_flutter/TeklifVeriSeti/<gün>/…`, `YolGo_teklif_<klasör>.zip` | teklif veri seti | 13.2, 13.7 | `files/TeklifVeriSeti/` (`:orders`), `yolgo-poll.jsonl` hariç aynı; ZIP `WEEM_teklif_<klasör>.zip` (M-13-1) |
| `files/surface-raw-v2/…`, `files/road-calibration-v1/<id>.json` | Y13/Y14 | — | sonra |

Bütün WEEM tam dosya yazımları `.tmp` + `rename` (YolGo Y12/Y13 depolarının ilkesi); ekleme dosyaları (`.jsonl` günlükler, veri seti satırları) ekleme kipinde yazılır (12.2, 13.2); okunamayan JSON dosyası silinmez, `<ad>.damaged-<ms>` olarak saklanır (Bölüm 9.10 kuralı bütün `files/map/` dosyalarına).

### 14.5 Sürüş günlüğü olay adları (dizin)

Biçim ve dosya Bölüm 12'de; olay adları ve alanları **aynen** korunur (eski kayıtlarla aynı okunur). Koordinatlar yalnız belirtilen yerlerde, belirtilen hane sayısıyla; ev koordinatı hiçbir olaya yazılmaz.

| Olay | Alanlar | Bölüm |
|---|---|---|
| `route`, `reroute` | `mode, m, walk, s, wh, up, stops, to` | 7.4 |
| `routing_failure` | `reason, originInMap, targets[{inside, nearestM}]` | 7.4 |
| `route_clear`, `route_end`, `mode {m}`, `arrive {la, lo}` (4 hane) | | 7.8, 7.14 |
| `wrongway {way, main}` | | 7.7, 9.6 |
| `intent {scheme, host}`, `external {src, kind}`, `resolved {src, by, conf, dm?}` | | 8.2, 8.7 |
| `approve`, `block`, `steep` `{way}`, `undo {way}`, `bad_road_clear {way}` | | 4.4 |
| `ww_pass {way, m, inc, reported?}`, `ww_answer {way, problem, approved?}`, `ww_report {way}`, `ghost {way, all?}`, `ww_segment {way, allow}`, `bad_spot {way}`, `bad_spot_clear {way, n}` | | 9.10 |
| `battery_set` | — | 10.3: **üretilmez** (ölü yol) |
| `shift_day_start {reason}`, `shift_day_end {reason}`, `shift_order_start {n}`, `shift_order_cancel {n}`, `shift_order_target {n, restaurant}`, `shift_arrive_restaurant {n, auto}`, `shift_leave_restaurant {n, waitS}`, `shift_customer_journey {n}`, `shift_arrive_customer {n, auto}`, `shift_delivered {n, totalS, m}`, `shift_stopping`, `shift_stop {StopRec}`, `shift_home_return_start`, `shift_home_return_arrived` | aşama izleyiciden gelenlerde `source: "kuryeplus"` | 11.14, 11.15 |
| `light_answer {la, lo (5 hane), light, dur, jm?, long?}`, `stop_surface_override {surface}` | | 11.11, 11.12 |
| `ride_start {reason}`, `ride_end {reason}`, `route_end {reason, dev, m, avg?}` | | 12.3, 12.5 |
| satır türleri `k: "gps" {la, lo (6 hane), ac, sp, hd, al}`, `k: "env" {hpa?, balt?, vr, vm}`, `k: "ev" {e, …}`; her satırda `t`, `dayId` | | 12.2 |
| `task_route_opened`, `reminder {k, preview?}` | | 13.13, 13.16 |

### 14.6 Bildirimler, kanallar, arka plan işleri, servisler

Tek tablo `:core` `NotificationIds` (`CORE.md` 3.3); kanal kimlikleri eski adlarıyla (kullanıcının kanal ayarı kanala bağlı).

| No | Ad (`NotificationIds`) | Kanal | Bölüm | Not |
|---|---|---|---|---|
| 7100–7102 | `REMINDER` (`reminder(kind)`: `WATER`, `CHECK`, `FOOD`) | `hatirlatici` | 13 | vardiya sonu iptal |
| 7201 | `TASK_NEARBY` | `hatirlatici` | 13 | vardiya sonu iptal |
| 7301 | `WEATHER` | `duzyol_weather` | 10.8 | günde bir; vardiya sonu iptal |
| 7401 | `DATASET_FGS` | `yolgo_offer_dataset` | 13.11 | **kullanılmaz:** veri seti servisi yok (kayıt `:orders` akışında; CORE.md 3.3'te işaretlendi, sabit A6.9'da kalkar) |
| 7402 | `NAV_ONLY_FGS` (**öneri**, SORU-N4) | `navigation` (yeni) | 7.12, 7.17 | vardiya bitince yalnız-nav ≤ 90 dk |
| 7400 | — | `yolgo_recording` | 12 | **kullanılmaz:** YolGo gün kaydı servisi vardiya servisine (1, `shift`) birleşti (`VERI_SOZLESMESI` 8.2) |
| 7435 | `CALIBRATION_FGS` | `yolgo_calibration` | — | Y14, sonra |

Arka plan: WorkManager `duzyol_weather_v1` (periyodik 2 sa, ağ gerekli; 10.8). Ön plan servisleri: gün açıkken konum `:core` vardiya servisinde (1); `:map`'in kendi servisi yok (yalnız-nav için SORU-N4); YolGo `TelemetryService` (12.11) ve `DatasetRecordingService` (13.11) yazılmaz. Etkinlik tanıma (`MotionReceiver`) 1.0'da yok (Y13 ile; 11.1).

### 14.7 Ağ uç noktaları

`:map` ağı yalnız `HttpPort` üstünden (Bölüm 2.14, 8.12; testte sahte); ekran okuma kodu ağ kullanmaz (AGENTS.md). Bütün isteklerde `User-Agent: WEEM/<versionName> (personal courier navigation)` (YolGo `YolGo/1.11.0 (…)`; F-N3); karo isteklerinde paket kimliği `com.kuryelog.app`.

| Hizmet | Uç nokta | Bölüm |
|---|---|---|
| Overpass (4 ayna, sırayla) | `overpass-api.de`, `overpass.private.coffee`, `maps.mail.ru/osm/tools/overpass`, `overpass.kumi.systems` → `/api/interpreter` (POST `data=`) | 2.8 |
| Hazır harita verisi | `github.com/mehemmedsh/kurye_nav_data/releases/latest/download` + `manifest.json` | 2.9 (koşul: 2.15 sorusu) |
| Yükseklik (SRTM) | `s3.amazonaws.com/elevation-tiles-prod/skadi/…`, `elevation-tiles-prod.s3.amazonaws.com/skadi/…` (`.hgt.gz`) | 2.7 |
| Geofabrik (yalnız CI) | `download.geofabrik.de/asia/azerbaijan-latest.osm.pbf` | 2.11 |
| Wikimedia Commons (yalnız CI) | `commons.wikimedia.org/w/api.php` | 5.4 |
| Karolar | OSM `tile.openstreetmap.org/{z}/{x}/{y}.png`; Esri `server.arcgisonline.com/ArcGIS/rest/services/…`; CARTO `{s}.basemaps.cartocdn.com` | 6.4 (WEEM stilleri S4, SORU-N5) |
| Google Maps SDK | `com.google.android.geo.API_KEY` (CI gizli değişkeni) | 6.10 |
| Google Geocoding / Places (New) | `maps.googleapis.com`, `places.googleapis.com` (kullanıcı `googleKey`) | 8.6, 8.8 |
| OpenRouteService | `api.openrouteservice.org` `geocode/search`, `geocode/autocomplete` (`orsKey`) | 8.6, 8.8 |
| Nominatim | `nominatim.openstreetmap.org/search` | 8.6 |
| Kısa bağlantılar | `maps.app.goo.gl`, `goo.gl`, `g.co` … (yönlendirme izlenir; `consent.` ara sayfası) | 8.6 |
| Open-Meteo | `api.open-meteo.com/v1/forecast`, anahtarla `customer-api.open-meteo.com` | 10.7 |
| Google Maps'te rota | `https://www.google.com/maps/dir/` (`openGoogleRoute`) | **kalkar** (S9; 8.13, 13) |

### 14.8 Manifest: intent'ler, alıcılar, izinler; kalkan sağlayıcılar

- **Intent filtreleri** (Y5): YolGo `MainActivity`'nin 10 VIEW filtresi + SEND `text/plain` + PROCESS_TEXT `text/plain` aynen `:app` `MainActivity`'ye (Bölüm 8.2, `VERI_SOZLESMESI` 2).
- **Kalkar (S7):** sağlayıcılar `az.duzyol.app.nav` (`nav/v1/current` → `NavPort.route`, 7.9) ve `az.duzyol.app.weather` (`weather/current` → `WeatherPort`, 10.10); `SHARE_HOME` (`az.duzyol.app.action.SHARE_HOME`, `com.kuryelog.app.action.SHARE_HOME`), `PICK_HOME` (`com.myapplab.yolgo.PICK_HOME`) ve ev seçici aktiviteleri (6.11); Rawstats sağlayıcısının YolGo okuyucuları (`offers/v2/current`, `scooter/v1/live`, `shift/v1/state`, `logs/v3/day/…`) → iç akışlar (`OrderEvents`, `ScooterBridge.live`, `ShiftPort`, Bölüm 13'te veri seti).
- **Kalır:** WorkManager hava işi. **1.0'da yok:** `MotionReceiver` ve Activity Recognition isteği (Y13 ile, 11.1), `TelemetryService` (12.11), `DatasetRecordingService` (13.11).
- **İzinler** (`VERI_SOZLESMESI` 3; `:map`'in kullandıkları): konum (fine/coarse; arka plan `:core` vardiya servisinde), `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_LOCATION` (vardiya ve SORU-N4); `:map` 1.0'da `ACTIVITY_RECOGNITION` kullanmaz (Y13 ile), `FOREGROUND_SERVICE_SPECIAL_USE` gerekmez (13.11), `SYSTEM_ALERT_WINDOW` yok (`:map` yüzen pencere açmaz).

### 14.9 Eski YolGo verisi (S1) ve 2 telefon

- **Yeniden girilir:** `settings.v1`'in kullanıcı alanları (14.2), sokak kararları ve saatler, bisiklet yolları, POI düzeltmeleri, ışık işaretleri, kaldırım bölgeleri, görevler, hatırlatıcılar, API anahtarları, son aramalar (`VERI_SOZLESMESI` 5.4).
- **Yeniden öğrenilir / üretilir:** Y12 yol bilgisi (S3), harita verisi (Y7), batarya tahmini durumu, ışık veritabanının öğrenilmiş kısmı.
- **Taşınmaz (anlık):** gün durumu (`shift.*`, `day.home.v1`, `courier.follow.v1`, `rawstats.*`, `ride.active.v1`, `passive.pausedAt.v2`), önbellekler.
- **İçe aktarılır (plan Bölüm 1, iş A7.1):** sürüş günlüğü `duzyol-journal-v1` (`VERI_SOZLESMESI` 7.3); öneri: Sürüş kayıtları'nda içe aktarma, dosyalar ham metniyle `files/map/logs/`'a, var olan ezilmez (12.15).
- **2 telefon (plan 4.4):** `:map` verisi telefon başınadır (harita verisi, `map` dosyası, `files/map/`); `:link`'ten yalnız tanımlı mesajlar gider (`RouteStatus` R→P, `BatterySummary`, …; Bölüm 7.16, 10.4). Rota kaydı, Y12 öğrenmesi, gün motoru, sürüş günlüğü, gün raporları, görevler ve hatırlatıcılar konumun ve haritanın olduğu telefonda (Receiver) yazılır (11.17, 12.14, 13.19); teklif veri seti Provider'da (13.11). Provider'ın ekranları gerekeni mesajdan gösterir (SORU-Q7, SORU-11-2 ve 17).

### 14.10 Gizlilik ve sırlar

- **Sırlar:** `googleKey`, `orsKey`, `cartoKey`, `weather.key.v1` ve APK Maps anahtarı hiçbir kayda, olaya, dışa aktarmaya, yedeğe, çökme kaydına ve belgeye yazılmaz; günlükte yalnız var/yok (YolGo CI'daki gibi en çok son 4 karakter). `map` dosyası yedeğe girerse (17) bu dört alan çıkarılır.
- **Koordinat:** ev koordinatı `:core`'da kalır; `:map` olaylarına, gün raporuna, sürüş kaydına yazılmaz (YolGo `home_day.dart:8-10` ilkesi). Olaylardaki koordinatlar hane sınırlı (`arrive` 4, `light_answer` 5). Hava özeti konum taşımaz (10.10).
- **Müşteri verisi:** `:map` müşteri adı/telefonu tutmaz; navigasyon hedefi adres metni ve koordinatı (S5) yalnız rota için; dışa aktarmada `Redactor` kuralları (`VERI_SOZLESMESI` 9). Gün raporu ve rota kaydı restoran ve müşteri hedef noktalarını (yuvarlanmadan) ve tam izi taşır; telefondan yalnız kullanıcı "kaydet/paylaş" deyince çıkar (YolGo davranışı, 12.12). Teklif veri setinde adres metni yok, noktalar 3 ondalık (13.17).

### 14.11 `VERI_SOZLESMESI.md`'ye işlenen düzeltmeler (A6.1 sonunda yapıldı)

- 5.4 "YG native" satırına `yolgo_runtime_v2` (`TelemetryService` durumu) eklendi; `yolgo_runtime_v2` ve `offer_dataset_v1` WEEM'de yazılmaz (12.11, 13.11).
- 5.4 notu: YolGo göç işaretleri (`lightMaxMv2`, `lightMaxMv3`, `mapDesignV1`, `streets.blocked.legacy.v1`, `streets.blocked.migrated.v2`) WEEM'de yazılmaz (S1).
- Yeni 5.5 (`map` tercih dosyası) ve 7.5 (`files/map/`, `files/TeklifVeriSeti/`, ZIP adı M-13-1), ikisi de `yeni`; tip eşlemesi 14.1.
- 7.3: sürüş kayıtları satırına içe aktarma işi A7.1 ve WEEM yolu `files/map/logs/`; harita verisi, telemetri ve veri seti satırlarına WEEM karşılığı.
- 8.2 ve CORE.md 3.3: 7401 `DATASET_FGS` kullanılmaz (13.11); 7402 öneri (SORU-N4).
<!-- END sec:14 -->

<!-- BEGIN sec:15 -->
## 15. WEEM yerleşimi

Kurallar (plan 4.2–4.3, `gradle/module-graph.properties`): **`map=core`** — `:map` yalnız `:core`'u görür; `:orders`, `:economics`, `:scooter`, `:link` ile yalnız `:core` portları üstünden konuşur, dikişleri `:app` (`WeemApplication`) kurar. Saf mantık `logic/map` (Android'siz, yerelde test edilir), Android `:map` ince: sensör, dosya, ağ, servis, harita yüzeyi. Ekranlar `:app`'te (Compose). Her bölümün "WEEM yerleşimi" alt bölümü ayrıntıdır; bu bölüm toplu dizin ve aralarındaki bağlardır.

### 15.1 `logic/map` paketleri (`com.kuryelog.logic.map.<paket>`)

| Paket | İçerik (özet) | Bölüm | Kullandığı paketler | İlk görev |
|---|---|---|---|---|
| `geo` | `GeoPoint`, `BBox` (+ `sizeKmLabel`, `overpass`), `Baku`, `Geo` (haversine, bearing, `LocalProjection`, `pointToSegment`) | 2.2, 5.4 | — | A6.3 |
| `net` (yeni) | `HttpPort` (`suspend send(HttpRequest): HttpResponse`; yöntem, URL, başlıklar, gövde, zaman aşımı, `followRedirects`), `UserAgent` (`WEEM/<sürüm> (personal courier navigation)`), sahte uygulama testlerde | 8.12, 2.14, 10.13 | — | A6.3 |
| `graph` | `OsmRaw`, `RoadClass`, `WayFlag`, `GraphBuilder`, `RoadGraph` (KNG1), `HgtTile`, `ElevationModel`, `Overpass`/`OverpassFetcher`, `MapDataState`, `MapDownload`, `MapBuild` (CI), `Poi`/`PoiSet`/`PoiCorrections`, `BikewayTags` | 2, 5 | `geo`, `net` | A6.3 (POI düzeltmeleri A6.8) |
| `surface` (Y13'ün 1.0 alt kümesi) | `SurfaceMark`, `SurfaceLabel`, `Passability`, `badSpotSpans`, sabitler 25/30 m | 9.12 (SORU-Q1) | `geo` | A6.4 |
| `route` | `RideParams`, `VehicleProfile`, `CostModel`, `Router`, `MinHeap`, `Planner`, `Plan`/`RouteLeg`/…, `RouteEngine`, `BadSpots`, `StreetPrefs` (+ kodek), `ReverseHours`, `StreetList`, `BikewayMarks`, `Quality.kt` | 3, 4, 5, 9.12 | `geo`, `graph`, `surface` | A6.4 |
| `settings` | `MapSettings` (+ `paramsFor`, `gradeLimitFor`), `MapSettingsCodec`, `MapPrefs` (tercih arayüzü, tip eşlemesi 14.1) | 14.1, 14.2 | `route` (`RideParams`) | A6.4 |
| `ui` (saf görünüm kuralları) | `HudLayout`, `CameraMode`, `PuckStyle`, `PuckMotion`, `MapStyles`, `MapPalette`, `LongPressMenu`, `BikewayLayerRules`, `PoiLayerRules`, `BikewayMenuAction` | 5.7, 6.15 | `geo`, `graph`, `route`, `settings` | A6.5 (katmanlar A6.8) |
| `input` | `AddressParser`, `PlusCode`, `LinkUri`, `IncomingLink`, `ClipboardHash`, `DestinationResolver`, geokodlayıcılar, `PlacesRest`, `PlaceSearch`, `OfflineIndex`, `SearchRecents`, `ApiChecks`, `MapText` | 8.12 | `geo`, `graph`, `net` | A6.6 |
| `nav` | `NavController` (saf durum makinesi), `NavState`, `Stop`, `RerouteGate`, `ApproachZoom`, `ArrivalFocus`, `RouteLine`, `RouteProgress`, `RouteStatusGate`, `MapMatcher`, `NavOnly`, `RouteOrigin`, `FixGate`, `HeadingFilter`, `CompassSmoother`, `PassiveSession` | 6.15, 7.16 | `geo`, `graph`, `route`, `settings`, `input` | A6.6 |
| `ride` | `SpeedOptions`, `BatteryState`, `EnergyOdometer`, `BatteryEstimator`, `RangeInputs` | 10.13 | `geo`, `route`, `settings` | A6.7 |
| `weather` | `WeatherRequest`, `WeatherReport`, `WeatherSummary`, `WeatherText`, `WeatherIcons`, `WeatherSchedule` | 10.13 | `net` | A6.7 |
| `roads` (Y12) | `WayStats`, `WwStats`, `GhostMark`, `RoadKnowledge`, `RoadLearner`, `RoadDayContributions`, `StreetRatio`, `QualityPrompt`, `VolumeDispatch`, `RoadBook` | 9.12 | `geo`, `graph`, `route`, `surface`, `settings` | A6.11 |
| `day` | `DayFix`, `RoadInfo`, `StopSemantics`, `RoadInfoBuilder`, `StopRec`, `OrderRec`, `OrderPhase`, `DayState`, `DayEvent`, `ShiftEngine`, `LightTracker`, `LightsDb`, `SidewalkMarks`, `StopReconciler`, `CourierStageFollower`, `FollowerFeed`/`StageFeed`, `HomeReturn`, `DayController`, `DayView`, `DayText` | 11.17 | `geo`, `graph`, `settings` | A6.8 |
| `logs` | `RideLogWriter`, `RideLogNames`, `RideLogStore`, `JournalExport`, `RouteRecorder`, `RouteDeviation`, `RideLogStatus` | 12.14 | `geo`, `route` | A6.9 |
| `stats` | `DayStats`, `DayReportFile`, `DayHistory`, `DayReasons`, `StatsTabs`, `LightStats`, `DurationText`, `StreetLabel`, `DayDeletionPlan` | 12.14 | `graph`, `day`, `logs` | A6.9 |
| `tasks` | `DailyTask`, `TaskTrigger`, `TaskController` | 13.19 | `geo` | A6.9 |
| `reminders` | `ReminderKind`, `ReminderItem`, `ReminderSettings`, `ReminderDue`, `ReminderController` | 13.19 | — | A6.9 |

**Yön kuralı:** tablo sırası aşağıdan yukarıya; bir paket yalnız kendinden önceki satırları kullanır, döngü yok (`nav` → `roads` değil: Y12'nin navigasyona etkisi rota girdileriyle (`RoadBook.forPlan`) ve Android `:map`'teki çalışma zamanı üstünden). `nav` ↔ `day`: `NavController` hedef bağını (`setDestination`) ve eve dönüşü (`startHomeReturn`) çıktı olayı olarak verir, Android çalışma zamanı `DayController`'a iletir (11.14); `tasks` "Rota Oluştur"u çıktı isteği olarak verir, çalışma zamanı `NavController.goTo(purpose = task)` çağırır (13.13). Y19 teklif veri seti `logic/map`'te değil, `logic/orders` `dataset` paketindedir (13.19); `:map`'ten yalnız `RouteMeasurePort` ile ölçüm alır. Saat yalnız `:core` `Clock` (+ `LogTime`), metinler `logic/core` `Bilingual` (`MapText`). `logic/map` `org.json` ve `kotlinx.coroutines` dışında bağımlılık almaz (öteki `logic/` modülleri gibi).

### 15.2 Android `:map` (`com.kuryelog.app.map`)

| Bileşen | Ne | Bölüm |
|---|---|---|
| `Maps` (tek giriş nesnesi; emsal `Orders`, `Scooter`, `Economics`) | süreç başına tek örnekler: `MapData`, `MapSettingsStore`, `NavRuntime`, `RoadsRuntime`, `BatteryRuntime`, `WeatherRuntime`, `DayRuntime`, `RideLogRuntime`, `TaskRuntime`, `ReminderRuntime`; dışa portlar `nav: NavPort`, `weather: WeatherPort`, `range: RangePort`, `routeMeasure: RouteMeasurePort`; içe dikişler `bindOrders(OrderEvents)`, `bindScooter(ScooterBridge)` | 15.3 |
| `MapPrefsStore`, `MapSettingsStore` | `SharedPreferences("map")`, `settings.v1` (`StateFlow<MapSettings>`) | 14.1, 14.2 |
| `AndroidHttp` | `HttpPort` (`HttpURLConnection`; yeni bağımlılık yok) | 8.12 |
| `MapData` | graf/DEM/POI açma, asset, indirme işi | 2.14 |
| `MapSurface`, `GoogleMapSurface`, `MapLibreSurface`, `LocationPuck` | iki harita yüzeyi (S4), aynı çizgi desenleri | 6.15 |
| `HomePickScreen`, `MapsKeyStatus`, `ApkGoogleConfig` | ev seçici, Maps anahtarı durumu ve paket/SHA-1 | 6.11, 6.10, 8.12 |
| `NavLocation`, `CompassSensor`, `NavOnlyService`, `NavPortImpl`, rota işçisi | konum (Fused 500 ms), pusula, yalnız-nav servisi (SORU-N4), `NavPort` | 7.16 |
| `LinkIntake`, `ClipboardWatch` | intent ve pano → `IncomingLink` | 8.12 |
| `RoadFilesAndroid`, `VolumeKeys`, `RoadsRuntime` | Y12 dosyaları, ses tuşu yakalama, öğrenme döngüsü | 9.12 |
| `BatteryRuntime`, `WeatherRuntime`, `WeatherFetch`, `WeatherWork`, `WeatherPortImpl`, `RangePortImpl` | batarya tahmini, hava, `duzyol_weather_v1` | 10.13 |
| `DayRuntime` | gün motoru, ışık ölçer, aşama izleyici (`OrderEvents.stages`), `ShiftFollower` bağı, `map` tercihleri, toast/titreşim istekleri, `StateFlow<DayView>` | 11.14, 11.17 |
| `RideLogRuntime`, `DayReportWriter`, `DayDeletionRuntime` | sürüş günlüğü ve rota dosyaları (`files/map/logs/`), gün raporu, gün silme | 12.14 |
| `TaskRuntime`, `ReminderRuntime` | görev tetiği (konum, 30 sn), hatırlatıcı zamanlayıcısı (20 sn), `NotifyPort` | 13.19 |
| `RouteMeasurePortImpl` | veri seti için rota ve yükseklik ölçümü (`:orders`'a) | 13.4, 13.11 |
| — (yazılmaz) | `MotionReceiver` (Y13), `TelemetryService` (12.11), `DatasetRecordingService` (13.11) | 11.1, 12.11, 13.11 |

**Açılış dikişi** (`WeemApplication.onCreate`, öteki modüllerden sonra): `Maps.bind(this)` → `Maps.bindOrders(Orders.events)` (aşamalar, `busy`, teklifler), `Maps.bindScooter(Scooter.bridge)`; ters yönde (yeni dikişler, öneri adları) `Orders.bindMap(Maps.nav, Maps.weather)` → `OrderFlow.Env.weather` (bugün `NONE`; R15 `OfferWeather`) ve `NavPort.route` akışı → `OrderFlow.navigation(state, now)` (E8 hedef bağı), `Economics.bindMap(Maps.weather, Maps.range)` → `EconomicsObserver.Env.weather` (bugün `NONE`) ve N5 girdileri (`RangePort`, SORU-T1-1). İkisi de A3/A4'te `:map` yokken `NONE` ile yazıldı; dikiş A6.6 (nav) ve A6.7 (hava, menzil) görevlerinde kurulur. `Maps.bindOrders` `Orders.bind`'dan (geri yükleme dahil) sonra çağrılır: süreç başındaki boş `TripStages` eksik okuma sayılmasın (11.14). `Orders.bindRouteMeasure(Maps.routeMeasure)` veri setinin rota ölçümünü bağlar (13.11). `:map` gün motoru vardiyayı `ShiftHub` dinleyicisi olarak değil `ShiftPort.state` akışından izler (`ShiftFollower`, 11.2); veri setinin gün sonu işi `ShiftHub.listeners`'a `:orders`'ın kendi gün sonu yazımlarından sonra eklenir (13.11). Bağlama hatası `Store.guarded` ile kaydedilir; bağlanmamış port `NONE` kalır (veri uydurulmaz).

### 15.3 `:core` portları ve yeni `:core` girdileri

| Port | Yön | `:map`'in kullanımı | Bölüm |
|---|---|---|---|
| `ShiftPort.state` | `:core` → `:map` | gün açık mı, başlangıç (`startedAtMs` → kova `yolgo-<ms>`), yalnız-nav geçişi, öğrenme kapsamı, hava zamanlayıcısı, gün motoru (`ShiftFollower`) | 6, 7, 9, 10, 11 |
| `OrderEvents` (`stages`, `busy`, `offers`) | `:orders` → `:map` | sipariş aşaması (restoran/müşteri hedefi, `excludeStops`, gün motoru aşamaları), teslimat bitti (görevler) | 7, 9, 11, 13 |
| `ScooterBridge` (`live`, `batteryPct`) | `:scooter` → `:map` | canlı batarya, eğim limiti | 3, 10 |
| `NotifyPort` + `NotificationIds` | `:map` → `:core` | 7100–7102, 7201, 7301, (7402); 7401 kullanılmaz (13.11) | 14.6 |
| `Clock`, `LogTime` | `:core` | bütün saatler; Bakü günü | 9, 10, 11 |
| ev (`KuryelogPrefs` `home.*`, `HomePickResult`) | `:core` → `:map` | eve dön, ev kuralı dışı her şey, haritadan seçme | 6.11, 7.8 |
| `NavPort` (`route: StateFlow<RouteStatus?>`, `open(NavTarget)`) | `:map` → `:orders`, `:link` | `NavPortImpl` | 7.9, 8.12 |
| `WeatherPort.current()` | `:map` → `:orders`, `:economics` | `WeatherPortImpl` (≤ 90 dk) | 10.10 |
| **`RangePort`** (yeni, öneri) | `:map` → `:economics` | `battery()`, `rideTo(from, to)` | 10.6 |
| **`RouteMeasurePort`** (yeni, öneri) | `:map` → `:orders` | `measure(courier, pickup, dropoff, atMs)`: veri seti satırının altı ölçüsü (13.4) | 13.11 |

Yeni `:core` girdileri (öneri; ilgili uygulama görevinde, `NotificationIdsTest`/`PortsTest` ile): `RangePort` (+ `NONE`), `RouteMeasurePort` (+ `NONE`, 13.11), `NotificationIds.NAV_ONLY_FGS = 7402` ve kanal `navigation` (SORU-N4), `NotificationIds`'ten `DATASET_FGS` (7401) ve kanal `yolgo_offer_dataset` çıkar (13.11), SORU-11-1 (a) seçilirse `ShiftState.startReason`.

### 15.4 `:app` ekranları

| Yer | Ekran / parça | Bölüm |
|---|---|---|
| Harita sekmesi (`Tab.MAP`, açılış sekmesi) | `MapTab`: harita yüzeyi, HUD (üst arama çubuğu + ayar kısayolu, hava yuvarlağı, sol göstergeler, konum düğmesi, rota paneli), uzun basış sayfası, varış kartı, bildirim şeridi ("Geri al"), ters yön ve kalite kartları, kurulum kartı; üstte `ShiftCard` + `MapShiftActions` (SORU-N7) | 6, 7, 9, 10 |
| Harita sekmesi sayfaları | `SpeedSheet`, `ScooterValuesSheet`, `WeatherDetailSheet`, `MapsHelpSheet`, bölge bilgisi, POI düzeltme, "Saatli çift yön izni" | 4.5, 5, 6.10, 10 |
| Daha fazla → **Harita ayarları** (`MapSettingsScreen`; sıra 6.9) | Teklif veri seti · Yollarım (`RoadsScreen` 4 sekme + Bisiklet, Sürüş kayıtları, Sokak bilgileri, ışık mesafesi, ses tuşları) · Hatırlatıcılar · Günlük işler · Harita görünümü · Hava durumu · Harita verisi · API ve bağlantılar (`MapApiSettingsScreen`) · Navigasyon bağlantıları | 6.9, 8.9, 9.8, 10.9, 12, 13 |
| Daha fazla → Ev konumu | "Haritadan seç" → `HomePickScreen` | 6.11 |
| Harita sekmesi `ShiftCard` + `MapShiftActions` | gün kartı satırları (km, ışık sayacı, aşama, tamamlanan sipariş), "İstatistik", "Eve dön" | 6.12, 11.9, 12.8 |
| İstatistik (`StatsScreen`, `DayStatsPage`, `LightsMiniMap`) | gün sekmeleri, ışık soruları, paylaş, sil | 12.7, 12.9 |
| Harita ayarları → Yollarım → Sürüş kayıtları (`RideLogsScreen`) | günlükler, sürüşler, rota kayıtları, kaydet/paylaş/sil | 12.10 |
| Harita ayarları → Teklif veri seti (`DatasetScreen`, `DatasetManualSheet`), Günlük işler (`TasksScreen`, `TaskEditorSheet`), Hatırlatıcılar (`ReminderSettingsSection`) | | 13.8, 13.9, 13.14, 13.16 |
| Uygulama kabuğu (her sekmenin üstünde) | `TaskAlertCard`, `ReminderCard` (WEEM öndeyken sistem bildirimi yok, CORE.md 3.1) | 13.14, 13.16 |

Tema ve dil `:app`'in (`theme`, `lang`; 6.2); Harita sekmesi kendi tema anahtarı tutmaz. Metinler TR/AZ birebir, `TextCoverageTest` kuralıyla.

### 15.5 2 telefon (plan 4.4–4.5; ayrıntı A8.3)

| İş | Tek telefon | Receiver (scooter telefonu) | Provider (Wolt/Bolt telefonu) | `:link` |
|---|---|---|---|---|
| Harita verisi, `map` ayarları | var | kendi | kendi | — |
| Navigasyon, rota motoru, takip | var | **asıl** | Receiver'a ulaşılamazsa | `NavTarget` P→R, `RouteStatus` R→P |
| Bağlantı yakalama ve adres çözme (Y5/Y6) | var | elle arama | **asıl** (Wolt/Bolt burada) | `NavTarget` |
| Y12 öğrenme, sorular, ses tuşu | var | **yalnız burada** | kapalı (SORU-Q7) | — |
| Y9 batarya tahmini | var | GPS + canlı scooter | `BatterySummary`'den (GPS tahmini yok) | `BatterySummary` R→P |
| Y10 hava | var | kendi interneti | kendi interneti (S6) | — |
| N5 menzil | var | — | `:economics` (teklifler burada) | `BatterySummary`; sonuç alanı A8.3 |
| Gün motoru, Y11, Y17, Y18 | var | **asıl** (konum, harita) | yok; ekranlar SORU-11-2 (`DaySnapshot` R→P önerisi) | `ShiftState`, `TripStages` P→R |
| Y19 teklif veri seti | var | — | **asıl** (`:orders`; rota ölçümü kendi `:map` verisiyle) | — (scooter kapsaması SORU-13-2) |
| Y20 görevler, hatırlatıcılar | var | **asıl** (SORU-13-3) | yok | `TripStages` (teslim) P→R |

Vardiya (`ShiftState`) her iki telefonda aynı arayüzle izlenir (Receiver'da uzak, A8.3); `:map` farkı görmez.
<!-- END sec:15 -->

<!-- BEGIN sec:16 -->
## 16. Test eşlemesi (A6.2a, A6.2b)

Her bölümün "Testler" alt bölümü test test ayrıntıdır (neyi doğrular, değerler, yardımcılar); bu bölüm **dosya dizini**, görev bölüşümü ve toplamlardır. Sayım: YT `^\s*(test|testWidgets)\(` tanımı (döngüyle üretilenler tek), YK `@Test` (`TEST_ENVANTERI` sayım komutları). Bir dosya birden çok bölüme dağılıyorsa satırı her görevde yalnız o görevin payıyla yer alır.

### 16.1 Çeviri kuralları (A6.2a, A6.2b)

- **"Testler derlenir, kırmızı":** çeviri görevi testleri ve testlerin derlenmesi için gereken **en küçük API iskeletini** (bölümlerin "WEEM yerleşimi" adları; gövde `TODO()`) yazar; uygulama görevi (A6.3 …) iskeleti doldurup testleri yeşil yapar. İskelet adı ya da imzası uygulamada değişirse test aynı commit'te güncellenir; beklenen değer değişmez (değişirse şartname farkı olarak bölümüne yazılır).
- **Yer:** saf testler `logic/map/src/test/kotlin/com/kuryelog/logic/map/<paket>/<Ad>Test.kt` (JUnit 4, `org.json` var; paketler 15.1). Ekran testleri `:app` Robolectric (Compose `createComposeRule`; emsal `OrdersTabTest`, `OrdersSettingsScreenTest`), Android'e değen `:map` testleri `:map` Robolectric. YolGo `Key('…')` → `Modifier.testTag("…")` aynı dizge; metin denetimleri TR ve AZ birebir.
- **Ad:** test işlevi adı Dart açıklamasının Türkçesi (ters tırnaklı Kotlin adı), sınıf KDoc'unda kaynak dosya ve sayı (emsal `ShiftFollowerTest`). Döngüyle üretilen testler (`for lang in Lang.values`, `for dark …`) tek test içinde döngü ya da `@RunWith(Parameterized::class)`; sayımda tanım sayılır.
- **Girdiler:** `test/fixtures/*` → `logic/map/src/test/resources/fixtures/` aynı adlarla; sentetik graflar (`_graph`, `footGraph`, `plazaGraph`, `_turnGraph`, `_mainOneway`, …) tek test yardımcısında (`route` `TestGraphs`, Bölüm 3.14); Dart `Random(seed)` dizisi sabit örnek dizisine (10.12); saat `DateTime.now()` yerine sahte `Clock` (`:core`), Bakü günü `LogTime`; ağ sahte `HttpPort`; konum sahte fix akışı.
- **Bölünmez kural:** bir YolGo testinin beklentisi WEEM kararıyla değişiyorsa (S1, S7, S9 …) test **uyarlanır** ve satırında neden yazılır; sessizce düşülmez. Çevrilmeyen her test 16.4'te gerekçesiyle.

### 16.2 A6.2a — rota, harita verisi, Y12 (Y3, Y7, Y8, Y12, Y15, Y16)

| Dosya | Sayı (A6.2a payı) | WEEM hedefi | Uygulama | Bölüm |
|---|---:|---|---|---|
| YT `overpass_test` | 3 | `graph.OverpassProblemTest` | A6.3 | 2.13 |
| YT `map_data_update_test` | 2 | `graph.MapDataStateTest`, `AtomicFileTest` (saf `java.io.File`) | A6.3 | 2.13 |
| YT `router_test` | 31 (38'in; 7'si sonra) | `graph.RoadGraphFileTest` 1 (A6.3) · `route.RouterTest` 20, `route.StartWalkOutTest` 3, `route.PlannerTest` 2, `route.RouteEngineTest` 3, `route.BadSpotCostTest` 1 (A6.4) · `roads.StreetRatioTest` 1 (A6.11) | A6.3, A6.4, A6.11 | 2.13, 3.14, 4.6, 9.11 |
| YT `park_paths_test` | 4 | `graph.ParkPathsTest` 2 (A6.3), `route.ParkPathsTest` 2 (A6.4) | A6.3, A6.4 | 2.13, 3.14 |
| YT `push_segments_test` | 5 | `route.PushSegmentsTest` | A6.4 | 3.14 |
| YT `route_hours_test` | 3 | `route.ReverseHoursTest` | A6.4 | 3.14, 4.6 |
| YT `ww_green_two_way_test` | 4 | `route.WrongWayCostTest` | A6.4 | 3.14, 4.6 |
| YT `bikeway_e13_test` | 17 (18'in; 1'i A6.2b) | `graph.BikewayTagsTest` 3, `graph.RoadGraphFileTest` 2 (A6.3) · `route.BikewayCostTest` / `route.RouteEngineTest` 6 (A6.4) · `nav` denetleyici 2 (A6.6) · `settings.MapSettingsTest` + `route.BikewayMarksTest` 2, `route.BikewayMenuTest` + `ui.BikewayLayerRulesTest` 2 (A6.8) | A6.3, A6.4, A6.6, A6.8 | 5.2 |
| YT `pois_test` | 4 | `graph.PoiSetTest` | A6.8 | 5.6 |
| YT `quality_test` | 9 | `route.QualityTest` | A6.4 | 9.11 |
| YT `road_learner_test` | 9 | `roads.RoadLearnerTest` | A6.11 | 9.11 |
| YT `road_save_test` | 1 | `roads.RoadBookTest` | A6.11 | 9.11 |
| YT `day_contributions_test` | 2 (3'ün; 1'i sonra) | `roads.DayContributionsTest` | A6.11 | 9.11 |
| YT `ww_clean_run_test` | 5 | `roads.WwCleanRunTest` | A6.11 | 9.11 |
| YT `ww_no_problem_green_test` | 5 | `roads.WwAnswerFlowTest` | A6.11 | 9.11 |
| YT `street_scores_test` | 1 (12'nin; 11'i sonra) | `roads.RoadNamesTest` | A6.11 | 9.11 |
| YT `v140_features_test` | 5 (14'ün; 9'u A6.2b) | Y3/Y8 3 → `route.RouterTest`, `route.RouteEngineTest`; ayar 2 → `settings.MapSettingsTest` | A6.4 | 10.12, 14.2 |
| **Yeni** (karakterizasyon) | — | `graph.*` (2.13), `route.StreetPrefsTest` (4.6), `route.*` (3.14) | A6.3, A6.4 | 2.13, 3.14, 4.6 |

**Koordinatör düzeltmesi:** `v140_features_test`'in iki "ayar" testi 10.12'de A6.2b'ye yazılmıştı; `MapSettings` rota motorunun girdisi olduğundan (A6.4 yalnız A6.3 ← A6.2a'ya bağlı) **A6.2a**'ya alındı.

### 16.3 A6.2b — navigasyon, adres, ekran, batarya, hava, gün motoru ve ışık, kayıtlar, veri seti, görevler

| Dosya | Sayı (A6.2b payı) | WEEM hedefi | Uygulama | Bölüm |
|---|---:|---|---|---|
| YT `hud_layout_test` | 2 | `:app` `MapTabLayoutTest` 1 (A6.5); 2. test (hava saati ayrıntısı, %40 klavyeli sayfa) `:app` `WeatherDetailSheetTest` 1 (A6.7) | A6.5, A6.7 | 6.14 (2. test 10.9) |
| YT `locate_button_test` | 3 | `ui.LocateButtonTest` 2, `:app` `SmartLocateButtonTest` 1 | A6.5 | 6.14 |
| YT `map_hud_gesture_test` | 2 | `:app` `MapHudLayoutTest` | A6.5 | 6.14 |
| YT `puck_arrow_test` | 3 | `ui.PuckStyleTest` 2, `:map` `LocationPuckTest` 1 | A6.5 | 6.14 |
| YT `yolgo_sade_2_test` | 9 (10'un; 1'i çevrilmez) | `:app` `MapShiftActionsTest` 5 (A6.5) · `day.ShiftEngineStagesTest` 4 (A6.8) | A6.5, A6.8 | 6.14, 11 |
| YT `home_picker_test` | 5 | `:map` `HomePickScreenTest` | A6.5 | 6.14 |
| YT `approach_zoom_test` | 4 | `nav.ApproachZoomTest` | A6.6 | 7.15 |
| YT `arrival_focus_test` | 4 | `nav.ArrivalFocusTest` | A6.6 | 7.15 |
| YT `arrival_pin_test` | 3 | `nav.NavControllerArrivalTest` | A6.6 | 7.15 |
| YT `native_fast_fix_test` | 2 | `nav.FastFixGateTest` | A6.6 | 7.15 |
| YT `nav_assistant_test` | 1 | `nav.NavAssistantTest` (görev kısmı A6.9) | A6.6 | 7.15 |
| YT `nav_feed_test` | 11 | `nav.RouteProgressTest`, `nav.RouteStatusGateTest`, `nav.NavControllerOriginTest` 9 · `input.IncomingLinkTest` 2 | A6.6 | 7.15 |
| YT `nav_only_location_test` | 2 | `nav.NavOnlyTest` | A6.6 | 7.15 |
| YT `remaining_km_e12_test` | 7 | `nav.RouteProgressTest` + `:app` `MapMetricsTest` | A6.6 (gösterge A6.7) | 7.15 |
| YT `route_warnings_h2_h3_test` | 3 | `nav.RerouteGateTest` | A6.6 | 7.15 |
| YT `v150_features_test` | 10 (15'in; 5'i çevrilmez) | `nav.RouteOriginTest` 3 (A6.6) · `ride.SpeedOptionsTest` 3 (A6.7) · `ui.GoogleMapStyleTest` 1 (A6.5) · `:app` `MapMetricsTest`/`SpeedSheetTest` 3 (A6.5/A6.7) | A6.5, A6.6, A6.7 | 7.15 |
| YT `yolgo_rules_test` | 4 | `nav.RerouteGateTest` 1 (A6.6) · `day` 2 (A6.8) · `weather` 1 (A6.7) | A6.6, A6.7, A6.8 | 7.15, 11 |
| YT `offer_navigation_removed_test` | 2 (4'ün; 2'si çevrilmez) | `:app` `MapTabTest` (uyarlanır) | A6.5, A6.6 | 7.15 |
| YK `NavFeedContractTest` | 2 (6'nın; 2'si A2.5'te karşılandı, 2'si çevrilmez) | `input.IncomingLinkTest` | A6.6 | 7.15, 8.11 |
| YT `address_parser_test` | 17 | `input.AddressParserTest`, `input.PlusCodeTest` | A6.6 | 8.11 |
| YT `place_search_test` | 8 | `input.PlaceSearchTest`, `input.PlacesRestTest` | A6.6 | 8.11 |
| YT `geocoder_scope_test` | 1 | `input.GeocoderTest` | A6.6 | 8.11 |
| YT `api_settings_test` | 1 | `input.ApiChecksTest` | A6.6 | 8.11 |
| YT `battery_estimator_test` | 5 | `ride.EnergyOdometerTest` | A6.7 | 10.12 |
| YT `v140_features_test` | 9 (14'ün; 5'i A6.2a) | Y11 5 → `day.LightRuleTest`, `day.LightTrackerTest`, `day.ShiftEngineTest` (A6.8) · Y18 3 → `logs.RouteRecorderTest`, `logs.RideLogStoreTest` (A6.9) · Y9 1 → `ride.SpeedOptionsTest` (A6.7) | A6.7, A6.8, A6.9 | 10.12, 11, 12 |
| YT `v160_y1_y2_test` | 5 | `weather.WeatherReportTest` 3, `:app` `WeatherChipTest` 1 (A6.7) · `ui.PuckStyleTest` 1 (A6.5) | A6.5, A6.7 | 10.12 |
| YT `v170_features_test` | 6 (13'ün; 2'si A2.5/A5.3'te karşılandı, 4'ü çevrilmez, 1'i sonra) | `ride.BatteryEstimatorTest` 1 · `:app` `MapMetricsTest` 2, `WeatherChipTest` 1 · gün kartı simgeleri 1, alt yer düğmesi boyu 1 (`:app`, Bölüm 6) | A6.5, A6.7 | 10.12 |
| YT `weather_section_test` | 4 | `:app` `MapWeatherSettingsTest` | A6.7 | 10.12 |
| YK `WeatherFeedTest` | 2 | `weather.WeatherSummaryTest` | A6.7 | 10.12 |
| YT `bikeway_e13_test` | 1 (18'in; 17'si A6.2a) | `:app` `BikewayTabTest` | A6.8 | 5.2 |
| YT `shift_engine_test` | 9 | `day.ShiftEngineTest` | A6.8 | 11.16 |
| YT `light_tracker_test` | 4 | `day.LightTrackerTest` | A6.8 | 11.16 |
| YT `light_leaks_f52_test` | 3 | `day.LightLeaksTest` | A6.8 | 11.16 |
| YT `light_rule_m14_test` | 12 (14'ün; 2'si çevrilmez) | `day.LightRuleTest` 5, `day.ShiftEngineTest` 1, `day.LightTrackerTest` 1, `day.LongLightTest` 5 | A6.8 | 11.16 |
| YT `courier_stage_follow_test` | 13 (14'ün; 1'i çevrilmez) | `day.CourierStageFollowerTest` 11, `day.StageFeedTest` 2 (uyarlanır) | A6.8 | 11.16 |
| YT `courier_stages_e8_test` | 12 (13'ün; 1'i çevrilmez) | `day.CourierStageTimelineTest` 11, `day.StageFeedTest` 1 (uyarlanır) | A6.8 | 11.16 |
| YT `home_day_controller_test` | 2 (5'in; 3'ü `:core`'da) | `day.DayControllerTest` 1 (uyarlanır; A6.8) · `stats.DayReasonsTest` 1 (A6.9) | A6.8, A6.9 | 11.16, 12.13 |
| YT `assistant_test` | 14 | `day.*` 6 (A6.8) · `tasks.TaskTriggerTest` 5 (A6.9) · `input.AddressParserTest` 1, `nav.HeadingFilterTest` 1 (A6.6) · `weather.WeatherReportTest` 1 (A6.7) | A6.6–A6.9 | 11.16, 13.18 |
| YT `log_store_test` | 7 | `logs.RideLogStoreTest` 4, `logs.RideLogWriterTest` 3 | A6.9 | 12.13 |
| YT `ride_log_status_test` | 2 | `logs.RideLogStatusTest` 1, `:app` `RideLogStatusTextTest` 1 | A6.9 | 12.13 |
| YT `dataset_days_test` | 2 | `logic/orders` `dataset.DatasetDaysTest` (1'i uyarlanır) | A6.9 | 13.18 |
| YT `dataset_screen_test` | 5 | `:app` `DatasetScreenTest` (1'i uyarlanır) | A6.9 | 13.18 |
| YT `task_controller_test` | 1 | `tasks.TaskControllerTest` (uyarlanır, S9) | A6.9 | 13.18 |
| YT `reminders_test` | 5 | `reminders.ReminderDueTest` 4, `reminders.ReminderSettingsTest` 1 | A6.9 | 13.18 |
| YT `reminder_card_test` | 3 | `:app` `ReminderCardTest` 2, `reminders.ReminderTimingTest` 1 | A6.9 | 13.18 |
| YK `DatasetContractTest` | 6 | `logic/orders` `dataset.DatasetContractTest` | A6.9 | 13.18 |
| YK `DatasetMatchingTest` | 4 | `logic/orders` `dataset.DatasetPackTest` | A6.9 | 13.18 |
| YK `DatasetReadsTest` | 4 (6'nın; 2'si çevrilmez) | `logic/orders` `dataset.DatasetMeasureTest` 1, `dataset.OfferRowGateTest` 3 | A6.9 | 13.18 |
| RT `LogFeedV3Test` (Rawstats) | 10 | `logic/orders` `dataset.DayLogSnapshotTest` (uyarlanır) | A6.9 | 13.6, 13.18 |
| **Yeni** (karakterizasyon) | — | `ui.MapStylesTest`, `ui.MapPaletteTest`, `ui.PuckMotionTest`, `ui.LongPressMenuTest` (6.14), `:app` `RegionInfoSheetTest` (5.4), `graph`/`ui` POI katmanı (5.6) | A6.5, A6.8 | 5, 6 |
| **Yeni** (karakterizasyon) | — | `day.LightsDbTest`, `day.StopReconcilerTest`, `day.RoadInfoBuilderTest`, `day.HomeReturnTest`, `day.DayShiftSyncTest`, `day.DayTextTest`, `day.StageFeedTest` (11.16) · `logs.RideLogWriterTest`, `stats.DayReportFileTest`, `stats.DayStatsTest`, `stats.DayHistoryTest`, `stats.StatsTabsTest`, `stats.DayDeletionPlanTest`, `:app` `StatsScreenTest`, `RideLogsScreenTest` (12.13) · `dataset.DatasetRecorderTest`, `dataset.DatasetManualTest`, `dataset.DatasetToolTest`, `nav.TaskTargetTest`, `reminders.ReminderControllerTest`, `:app` `AppAlertsTest` (13.18) | A6.8, A6.9 | 11–13 |

**Koordinatör düzeltmesi (2):** `assistant_test` (14) hiçbir bölümün test tablosunda yoktu; payları yukarıda (8.11'e 1, 7.15'e 1, 10.12'ye 1, 11.16'ya 6, 13.18'e 5) — 7.15, 8.11 ve 10.12'nin tabloları bu satırla birlikte okunur. `bikeway_e13_test`'in A6.2b payı (1, `BikewayTabTest`, 5.2) 16.3'e eklendi.

### 16.4 Çevrilmeyenler, sonraya kalanlar, başka görevde karşılananlar

| Dosya (sayı) | Durum | Gerekçe | Bölüm |
|---|---|---|---|
| `street_prefs_migration_test` (1) | çevrilmez | S1: eski Ses− kara listesi WEEM'de olamaz, göç yazılmaz | 4.6, 4.8 |
| `yolgo_sade_2_test` ayar testi (1) | çevrilmez | `followCourierStages` ayarı yok (14.2), S1 | 6.14 |
| `v150_features_test` "evden çıkınca gün" (5) | çevrilmez | `HomeDepartureDetector` ölü kod, `autoStartDay` göçü; ev kuralı `:core` `ShiftMachine` | 7.15 |
| `offer_navigation_removed_test` son 2 | çevrilmez | kaldırılmış teklif sayfası; veri seti tetikleri Bölüm 13 | 7.15, 13 |
| `NavFeedContractTest` `pathsMatchRawstatsReader`, `everyQueryRechecksTheCallersPackageAndSigner` (2) | çevrilmez | sağlayıcı ve imza kalkar (S7) | 7.15 |
| `NavFeedContractTest` (2) | A2.5'te karşılandı | `logic/core` `MessagesTest.routeStatusFromTodaysNavFeed` | 7.15 |
| `v170_features_test` yük ayrıştırma/tazelik (2) | A2.5/A5.3'te karşılandı (uyarlanır) | `MessagesTest` (`ScooterLive`), `:scooter` `ScooterLiveMapper` | 10.12 |
| `v170_features_test` (4) | çevrilmez | Rawstats yoklaması, zaman aşımı, StateFlow eşitliği, Rawstats ev cevabı (S7) | 10.12 |
| `offer_engine_test` (7), `route_costing_test` `planLeavesBox` (1) | **SORU-T1-2** (öneri: çevrilmez) | v1 teklif motoru ölü kod; v2 `:economics`'te (A4.1) | 10.12, 10.14 |
| `router_test` ölçülmüş yüzey (7), `route_costing_test` (5), `v170` yüzey dizini (1), `day_contributions_test` (1), `street_scores_test` (11), `road_score_ui_test` (11) | sonra (Y13/Y14) | `SurfaceSummary`/kalibrasyon deposu (SORU-Q2: 23 Y12 testi) | 3.14, 9.11, 10.12 |
| `road_surface_score` (4), `surface_gate` (11), `surface_single_flight` (2), `surface_store_exclusions` (4), `surface_store` (12), `road_calibration_channel` (6), `road_calibration_score` (10), `road_calibration_screen` (4), `road_calibration` (7); YK `SurfaceSignalTest` (11), `RoadCalibrationCountdownTest` (5), `RoadCalibrationRoadRmsTest` (6), `RoadCalibrationTest` (18) | sonra (Y13/Y14) | `BEKLEYEN_OZELLIKLER.md` | 1 |
| `light_rule_m14_test` F9.1 göç testleri (2) | çevrilmez | S1: `lightMaxMv2/v3` göçü yazılmaz (14.2) | 11.16 |
| `courier_stage_follow_test` ayrıştırma 2. (1), `courier_stages_e8_test` ayrıştırma 2. (1) | çevrilmez | feed v2 zarfı ve eski Rawstats yok (S7); `TripStages.parse` A2.5 `MessagesTest`'te | 11.16 |
| YK `DatasetReadsTest` yoklama sabitleri ve `PollRowGate` (2) | çevrilmez | okuma ve yoklama yok (13.11) | 13.18 |
| YK `TelemetryJournalTest` (8) | sonra (Y13) | `telemetry-v2.jsonl`'ın tek kullanıcısı Y13; Y18 bu günlüğü kullanmaz (12.11; CORE.md 6.2'deki karar) | 12.13 |
| `tools/veri_seti/kontrol.py` öz-testleri (8, Python) | çevrilmez (araçla kalır) | WEEM `tools/veri_seti/` aynı araç; `logic/orders` testinden koşturulur | 13.10 |
| `home_day_controller_test` 1–3, `home_day_e6` (10), `home_day_end_rule` (5), `home_day_rules` (19), YK `NotifyPolicyTest` (2) | `:core`'da çevrildi (A2) | ev kuralı, `ShiftMachine`, `NotifyPolicy` (CORE.md 6.2) | — |
| `rawstats_shift_end_test` (11) | `:core`'da uyarlandı (A2) | `ShiftFollower`; `:map` gün motoru kullanır (Bölüm 11) | 11 |
| `rawstats_signals_test` (10), YK `CourierFeedPayloadTest` (6), `RawstatsObserverTest` (5) | uyarlanır `:link` (A8) | uygulamalar arası duyuru/yük → `:link` | — |
| `scooter_language_section_test` (3) | `:app` | dil ve scooter bölümü (A1.3/A5.4) | — |
| `native_telemetry_test` (1), `offer_feed_versions_test` (6), `rawstats_home_stale_test` (6), `rawstats_home_sync_test` (13), `app_version_test` (1), `ci_apk_workflow_test` (15), YK `HomeShareTest` (3), `HomePickContractCheck` (main, `@Test` yok) | çevrilmez | Flutter/CI'a ya da iki uygulamaya özgü (A2.1, A3.1; S7) | — |

### 16.5 Toplamlar ve denetim

Sayım YolGo `870ab6e`'den betikle yapıldı (YT `^\s*(test|testWidgets)\(`, YK `@Test`); her dosyanın 16.2–16.4'teki payları toplamı gerçek sayısına eşit (uyuşmazlık yok). Rawstats `LogFeedV3Test` ayrıca sayılır.

| Kategori | YT (83 dosya, 564) | YK (15 dosya, 88) | Toplam |
|---|---:|---:|---:|
| **A6.2a** (çevrilir) | 110 (17 dosya) | — | **110** |
| **A6.2b** (çevrilir) | 228 | 18 | **246** (+ RT `LogFeedV3Test` 10 = 256) |
| başka görevde karşılandı / uyarlandı (A1.3, A2, A2.5, A5.3–A5.4) | 53 | 4 | 57 |
| uyarlanır `:link` (A8) | 10 | 11 | 21 |
| SORU-T1-2 (öneri: çevrilmez) | 8 | — | 8 |
| çevrilmez | 59 | 7 | 66 |
| sonra (Y13/Y14) | 96 | 48 | 144 |
| **Toplam** | **564** | **88** | **652** |

Uygulama görevi başına çevrilen testler (A6.2a + A6.2b): **A6.3** 13 · **A6.4** 63 · **A6.5** 24 · **A6.6** 75 (A6.2a 2 + A6.2b 73) · **A6.7** 29 · **A6.8** 80 (A6.2a 8 + A6.2b 72) · **A6.9** 48 (+ `LogFeedV3Test` 10 = 58) · **A6.11** 24 — toplam 356 (+ 10). Yeni karakterizasyon testleri (her bölümün "Yeni" satırı) bu sayılara girmez; çeviri görevi onları da yazar.

Görev bağımlılığı için sonuç: A6.8 ve A6.6 iki çeviri görevinin de testlerini kullanır (A6.8: Y15/Y16 A6.2a'da, Y11 ve gün motoru A6.2b'de; A6.6: `bikeway_e13` yeniden rota testleri A6.2a'da) → `WEEM_ISLER.md`'de A6.8'in bağımlılığına A6.2a eklenir (A6.6 zaten A6.4 ← A6.3 ← A6.2a üstünden bağlı).
<!-- END sec:16 -->

<!-- BEGIN sec:17 -->
## 17. Açık noktalar, riskler ve kullanıcı soruları

Bölümlerin sorularının tek listesi (ayrıntı ve gerekçe kendi bölümünde; kodlar aynı). Her soruda **varsayılan** yazılıdır: kullanıcı cevap verene kadar uygulama görevleri varsayılanla ilerler; cevap `WEEM_PLANI.md` Bölüm 8'e tarih ve S numarasıyla yazılır, bu bölüm ve ilgili bölüm aynı commit'te güncellenir. Hiçbir soru A6.2a/A6.2b'yi (test çevirisi) engellemez; uygulama görevlerinden hangisini etkilediği satırında.

### 17.1 Kullanıcıya sorulacaklar (kapsam ve davranış)

| # | Kod | Soru | Seçenekler | Varsayılan | Görev | Bölüm |
|---|---|---|---|---|---|---|
| 1 | SORU-T1-1 | N5 "Menzil uyarısı" nasıl görünsün? (eski uygulamada yok) | (a) teklif kartında tek satır "Batarya yetmeyebilir · eve ~<km> km" + haritada var olan "eve dön?" kartı; (b) yalnız kayıt (gölge mod), görünür uyarı sonra; (c) ayrı menzil ekranı | (a) | A6.7 | 10.6, 10.14 |
| 2 | SORU-Q1 | Ses− tek basış (±25 m kötü nokta) Y13 deposuna yazar; Y13 1.0'da yok | (a) 1.0'da elle kötü nokta alt kümesi (dosya, 25 m/30 m/2 gün kuralı, rota cezası, uzun basışla silme); (b) Ses− 1.0'da etkisiz | (a) | A6.4, A6.11 | 9.5, 9.13 |
| 3 | SORU 1 | Y13 ölçülmüş yüzey girdileri ve "geçilebilirlik: kapalı" rota motoruna 1.0'da yazılsın mı? | (a) yazılmasın (7 test sonra); (b) boşken nötr yazılsın | (a) | A6.4 | 3.16 |
| 4 | SORU-N5 | Harita altlıkları ve vektör kaynağı (S4'ün kapsamı) | altlık: (a) Google (anahtar varsa) + MapLibre OSM raster + MapLibre vektör, Esri/Uydu/CARTO kalkar; (b) (a) + Esri ve Uydu; (c) YolGo'nun beşi + CARTO; vektör: (a) kendi Bakü PMTiles dosyası, (b) OpenFreeMap | (a) / (a) | A6.5 | 6.4, 6.16 |
| 5 | SORU-N7 | YolGo gün kartı ile WEEM `ShiftCard` | (a) tek kart: `ShiftCard` + harita eylemleri (Eve dön, İstatistik) + aşama, km, ışık satırları + katlanma kuralı; (b) iki ayrı kart | (a) | A6.5, A6.8, A6.9 | 6.12, 12.8 |
| 6 | SORU-N6 | Ekran yönü | (a) dikey kilit (gidon montajı); (b) serbest (yatay HUD düzeni ve test) | (a) | A6.5 | 6.16 |
| 7 | SORU-Q4 | Ses+/Ses− yakalama ne zaman? | (a) yalnız Harita sekmesi ve alt ekranları öndeyken; (b) WEEM öndeyken her an | (a) | A6.11 | 9.5 |
| 8 | SORU-12-1 | İki km ölçüsü (CORE.md 7 (1)): YolGo gün km'si ve vardiya km'si (D8) | (a) ikisi de eski yerinde (İstatistik/gün kartı harita km'si; Siparişler gün sayfası D8); (b) İstatistik'e ikinci kutu "vardiya km"; (c) her yerde tek km (D8) | (a) | A6.9 | 11.3, 12.15 |
| 9 | SORU-11-1 | Gün raporundaki "Evden çıkınca başladı" için başlangıç nedeni | (a) `ShiftState` v1'e isteğe bağlı `startReason` (`:core` küçük ek); (b) hep `manual` (satır görünmez); (c) ayrı port | (a) | A6.8 (+ `:core`) | 11.2, 11.18 |
| 10 | SORU-13-1 | Teklif veri setinde alış noktası (çoğu teklifte boş) | (a) YolGo gibi (yalnız adres metninde koordinat/Plus Code); (b) restoran kaydı ya da POI ad eşleşmesi (yeni davranış); (c) (b) yalnız gölge modda | (a); (b) `BEKLEYEN_OZELLIKLER` adayı | A6.9 | 13.11, 13.20 |
| 11 | SORU-Q6 | Ters yön madde 10b "iki yönlü" kuralı yumuşatılmış oranla neredeyse hiç işlemiyor | (a) aynen (rota YolGo ile aynı); (b) niyete göre (`problems == 0` onaylı ara sokak iki yönlü) | (a) | A6.11 | 9.9, 9.13 |
| 12 | SORU (2.15) | Hazır harita verisi sunucusu (`kurye_nav_data`) bu ortamdan erişilemedi | (a) zincir aynen (sunucu yoksa ≤ 20 sn sonra Overpass); (b) kaldırılsın, doğrudan Overpass | (a) | A6.3 | 2.9, 2.15 |
| 13 | SORU-N4 | Gün bitince rota açıksa (yalnız-nav ≤ 90 dk) arka plan konumu için bildirim | yeni `NAV_ONLY_FGS` 7402 + kanal `navigation` ("WEEM navigasyon" / "WEEM naviqasiya"; vardiya sonu iptal listesinde değil) | öneri | A6.6 (+ `:core`) | 7.12, 7.17 |
| 14 | SORU-N1 | Kapı notundaki domofon kodu Receiver'a gitsin mi? (S5) | (a) gider (kapıda Receiver ekranında gerekir); (b) gitmez | (a) | A6.6, A8.3 | 8.13 |
| 15 | SORU-Q5 + SORU-12-3 | Harita verisi haftalık şifreli yedeğe (S22) girsin mi? Bugün girmiyor: telefon değişince Y12 öğrenmesi, sokak kararları, ışıklar, bisiklet işaretleri, görevler ve sürüş kayıtları kaybolur | (a) S22 aynen (hiçbiri); (b) küçük harita verisi: `map` tercihleri (sırlar hariç; 14.10), `files/map/roads/`, `manual-marks.json`, gün raporları ve rota kayıtları; sürüş günlükleri hariç; (c) hepsi (sürüş günlükleri dahil, haftada ~40 MB) | (a) cevaba kadar; öneri (b) (A6.11 sonrası ayrı iş) | A4.3 eki, A6.9, A6.11 | 9.13, 12.15, 14.1 |
| 16 | SORU-13-2 | 2 telefonda veri seti Provider'da; scooter kaydı Receiver'da → paketin scooter kapsaması boş | (a) kabul ("Bilinmiyor"); (b) A8.3'te Receiver gün sonunda scooter örnek zamanlarını ve kimliğini gönderir; (c) veri seti Receiver'da toplanır | (a) | A6.9, A8.3 | 13.11 |
| 17 | SORU-11-2 | 2 telefonda Provider'ın gün kartı ve İstatistik'i (gün motoru Receiver'da) | (a) A8.3'te R → P `DaySnapshot` (60 sn'de bir + gün sonu raporu); (b) gizli + "Receiver'da" notu; (c) Provider ayrı gün tutar (çift km) | (a) A8.3'te; o zamana kadar (b) | A8.3 | 11.17 |
| 18 | SORU-13-3 | Görevler ve hatırlatıcılar hangi telefonda? | (a) Receiver; (b) Provider; (c) ikisi (çift bildirim) | (a) | A6.9, A8.3 | 13.19 |
| 19 | SORU-Q7 | Provider'da Yollarım (Y12 verisi Receiver'da) | (a) Kalite/Ters yön/Yol yok sekmeleri ve uzun basış Y12 satırları gizli + not; (b) `:link` üstünden gösterilir, eylemler Receiver'a | (a) | A6.11, A8.3 | 9.12, 9.13 |

SORU-12-2 (YolGo sürüş kayıtlarını içe aktarma) A6.1 sonundaki tutarlılık okumasında listeden çıktı: plan Bölüm 1 ve A7.1 içe aktarmaya zaten karar vermiş; kural önerisi 12.15, iş A7.1.

### 17.2 Koordinatör kararları (test ve belge kapsamı; ürün davranışını değiştirmez, itiraz yoksa böyle)

| Kod | Karar | Bölüm |
|---|---|---|
| SORU 2 | `street_prefs_migration_test` çevrilmez (S1: göç kodu yazılmaz) | 4.8, 16.4 |
| SORU-Q2 | Y12'ye yazılmış 23 test (`street_scores` 11, `road_score_ui` 11, `day_contributions` 1) Y13/Y14 testidir → "sonra" | 9.13, 16.4 |
| SORU-T1-2 | `offer_engine_test` (7) ve `route_costing_test` `planLeavesBox` (1) çevrilmez (v1 teklif motoru ölü kod; ekonomi v2 `:economics`'te); N5 testleri v2 girdileriyle yeni yazılır | 10.14, 16.4 |
| — | `TelemetryJournalTest` (8) Y18'e çevrilmez, Y13 ile gelir (CORE.md 6.2'nin "A6.1 karar verir" maddesi) | 12.11 |
| — | `LogFeedV3Test` (10) `logic/orders` `dataset`'te uyarlanır (A6.9) | 13.6, 13.18 |
| — | YolGo `TelemetryService`, `DatasetRecordingService` ve `MotionReceiver` WEEM 1.0'da yazılmaz; 7401 kullanılmaz | 12.11, 13.11, 14.6 |
| — | Y19 veri seti `:orders`'ta yazılır (`logic/orders` `dataset`; teklif verisi orada), `:map` yalnız `RouteMeasurePort` ile rota ölçer. Plan Bölüm 3 modül tablosunun `:map` satırı "veri seti" der: plan belgesi olduğu için kullanıcı onayıyla düzeltilir (itiraz yoksa A6.9'da) | 13.11, 15 |

### 17.3 Metin onayları (S19 gibi; kullanıcı onaylayınca kesinleşir)

| Kod | Yer | Öneri TR / AZ | Bölüm |
|---|---|---|---|
| SORU-N2 | "GPS konumu eski veya belirsiz" (AZ'si yoktu) | AZ "GPS mövqeyi köhnə və ya qeyri-dəqiqdir" | 8.13 |
| SORU-Q3 | Yollarım → Kalite: "Eski trafik kayıtları" | "Trafik kayıtları" / "Trafik qeydləri", alt "Rotayı etkilemez" / "Marşruta təsir etmir"; ses tuşu anahtarının TR metnine AZ'deki gibi "Ses+ ×2 kalite" | 9.13 |
| 7.17 | ev yokken "Eve dön" | "Ev konumu ayarlanmamış — Daha fazla → Ev konumu" / "Ev ünvanı təyin edilməyib — Daha çox → Ev ünvanı" | 7.17 |
| 10.9 | hava bildirimi anahtarı ("2 saatte bir" ama günde bir) | "Günde bir hava bildirimi" / "Gündə bir hava bildirişi" | 10.9 |
| M-11-1 | aşama toast'ları | " (KuryePlus)" eki kalkar; "Sipariş N alınmadan bitti — iptal sayıldı" / "Sifariş N götürülmədən bitdi — ləğv sayıldı" | 11.14 |
| M-12-1 | sürüş günlüğü dışa aktarma dosyası | `WEEM_surus_<gün \| tum_kayitlar>.json` | 12.4 |
| M-12-2 | gün kartı, sipariş yokken | "Sipariş yok · Wolt/Bolt'ta sipariş başlayınca burada görünür" / "Sifariş yoxdur · Wolt/Bolt-da sifariş başlayanda burada görünəcək" | 12.8 |
| M-12-3 | gün silme onayı | "Bu tarihin harita kayıtları (sürüşler, gün özeti, olaylar, otomatik yol ölçümleri) silinir. Siparişler ve kazanç kayıtları, diğer günler ve elle kaydedilen yollar korunur." + "Haritanın aktif günü durdurulacak; vardiya sürer." (AZ 12.9) | 12.9 |
| M-2-1 | "Harita dosyası eski sürüm" (AZ'si yoktu; A6.3) | AZ "Xəritə faylı köhnə versiyadır" | 2.15 |
| M-13-1 | veri seti ZIP ve paylaşım | `WEEM_teklif_<klasör>.zip`; "WEEM teklif veri seti · <gün>" / "WEEM təklif məlumat dəsti · <gün>" | 13.7 |
| M-13-2 | Teklif veri seti ekranı | açıklama, "RAW Stats" → "gün kayıtları", "YolGo" → "canlı kayıt", hata metni; bütün metinlerin AZ karşılıkları | 13.8 |
| M-13-3 | görev "Rota Oluştur" başarısız | "Rota açılamadı; iş bekliyor." / "Marşrut açılmadı; iş gözləyir." | 13.13 |
| M-13-4 | Hatırlatıcılar → "Nasıl çalışır" | TR ve AZ eşit, 30 sn, "WEEM öndeyken" (13.16) | 13.16 |
| — | yalnız uygulama adı | "YolGo" → "WEEM" (POI penceresi notu 5.8, paylaşım metinleri 12.6/12.10, `<Ad>` geçen her yer) | 5.8, 12 |

Metin değişikliği olmayan eski çeviri hataları (2.15 arka plan mesajlarının bozuk AZ'si, 13.16 hatırlatıcı bilgi metninin AZ farkı) WEEM'de baştan doğru yazılır.

### 17.4 Riskler

- **Performans:** büyük haritada A* süresi (A6.4 ölçümü, 3.13); `RouteMeasurePort` her teklif satırında rota ister (13.20); MapLibre yerel kütüphanelerinin APK boyutu ve yüzey değişiminde bellek (6.16).
- **Arka plan ve pil:** Wolt/Bolt öndeyken navigasyon ve gün motoru `:core` vardiya servisinin ömrüne bağlı; Xiaomi/Poco arka plan kısıtı ve pil iyileştirmesi telefonda denenmeli (7.17, A6.10); 500 ms Fused + vardiya servisinin 2 sn GPS isteği birlikte; sürüş günlüğü saniyede bir satır (12.15).
- **Depolama:** sürüş günlükleri (~0,8 MB/sürüş saati), veri seti `rawstats/` kopyaları ve ZIP'ler otomatik silinmez (YolGo'da da) — A10'da ölçülür, gerekirse saklama süresi kullanıcıya sorulur.
- **Sayısal eşitlik:** Dart VM ile JVM'de trigonometri son hanede farklı olabilir → testler `closeTo`, bayt eşitliği yalnız tamsayı/yapı alanlarında (2.15); eşit maliyetli rotalarda sıra kuralları aynen korunmazsa gölge karşılaştırmada fark görünür (3.16).
- **2 telefon:** Provider'daki `BatterySummary` 60 sn'lik, v2'nin 15 sn tazelik kuralına uymaz (10.14); gün motoru ve Y12 yalnız Receiver'da olduğundan Provider ekranları A8.3'e kadar eksik (17.1 #17, #19).
- **Geçiş (A10, kullanıcı işi):** Google Cloud anahtar kısıtına `com.kuryelog.app`, `com.kuryelog.app.dev` ve WEEM imzasının SHA-1'i (6.16); Android "Varsayılan olarak aç" ve Wolt/Bolt'taki navigasyon uygulaması seçimi WEEM için yeniden (8.13).
- **Dış hizmet:** hazır harita verisi sunucusu erişilemedi (17.1 #12); Overpass aynalarının hız sınırları (2.8).
- **Doğrulanmadı:** bu şartnamenin hiçbir davranışı telefonda denenmedi; ilk karşılaştırma A6.10 (eski YolGo ile yan yana sürüş; Y12 sıfırdan öğrendiği için farklar açıklanır).

### 17.5 Belge tutarlılığı (A6.1 sonunda yapıldı)

- `OZELLIK_HARITASI.md`: Aşama 6 satırlarının (Y1–Y12, Y15–Y21, N5) Ayrıntı bağlantıları bu belgeye; Y8, Y11, Y15, Y16, Y20 "Veri" satırlarında "(içe aktar)" → "yeniden girilir" (S1; 5.8); Y19 `:orders` + `:map` (13.11), N5 `:economics` + `:map` (`RangePort`, 10.6); R4 ve N5'in eski testlerinin durumu (SORU-T1-2).
- `TEST_ENVANTERI.md`: YolGo satırlarının hedefleri Bölüm 16'ya göre (ilk hedefi değişen 11 dosya, karışık dosyalarda paylar, `LogFeedV3Test` `:orders`); özet tablo (`:map` 57 dosya / 375, `:orders` 54 / 338, sonra 16 / 125, çevrilmez 12 / 64) ve çevrilmeyenler listesi.
- `VERI_SOZLESMESI.md`: 14.11'deki düzeltmeler.
- `CORE.md` 3.3: 7401 `DATASET_FGS` kullanılmaz (sabit A6.9'da `NotificationIds`'ten kalkar, numara boş kalır), 7402 öneri; 6.2 `TelemetryJournalTest` → sonra (Y13).
- `WEEM_ISLER.md`: A6.8'in bağımlılığına A6.2a (16.5).
- Bu belge: SORU-12-2 soru listesinden çıktı (plan Bölüm 1 ve A7.1 içe aktarmaya karar vermiş; 12.15, 17.1).
- Açık kalan (kullanıcı onayı): plan Bölüm 3 modül tablosunda veri seti `:map` satırında (17.2).
<!-- END sec:17 -->

## 18. A10.4b — Y13 yüzey ve titreşim ölçümü + Y14 yol kalibrasyonu (S32; WEEM 1.1; 21 Ekim 2026)

YolGo `yolgo-test` @ `870ab6e` `lib/surface/`, `lib/calibration/`, `lib/roads/street_scores.dart`, `lib/ui/surface_screen.dart`, `road_calibration_screen.dart`, `road_score.dart`, `android_overlay/SurfaceSignal.kt`, `RoadCalibration*.kt`, `TelemetryService.kt`'ın sensör kısmı. Eski Kotlin testleri aynen (paket adı değişti), Dart testlerinin saf mantığı Kotlin'e çevrildi (aynı girdi → aynı çıktı). **Cihazda denenmedi.**

### 18.1 Yapılan (modül `logic/map`, paketler `surface`, `calibration`, `roads`, `ui`, `route`, `nav`)
- **Sinyal ve kapı:** `SurfaceSignal` (100 Hz, 1,5 sn çakışmayan pencere, darbe, bantlar), `MotionGate` + `BufferedSurface` (8 sn gecikmeli tampon) + `SurfacePassTracker`, `SpeedBand`. **Hat:** `SurfaceFeed` (konum + ivme + yönelim + jiroskop + adım → `evidence`/`window`/`boundary` satırları; `TelemetryService`'in saf kısmı), `SurfaceProcessor` (`SurfaceController._process/_observation`: kapı kararı, harita eşleme, kesim anahtarı `way/çapa/yön/indeks`, 8 sn sonra depoya), `SurfaceLeg` (`_actualLeg`: gün + sipariş aşaması + gerçek navigasyon hedefi; rotasız sürüş "serbest" etap).
- **Depo ve puan:** `SurfaceModels` (`SurfaceProfile`, `CalibrationBand`, `SurfaceObservation`, `PassEvidence`, `SegmentHistory`, `SurfaceSummary`; `SurfaceScoring.summarizeSegment/summarizeSurface`: gün sonu ortalaması, güven, doğrulanmış darbe), `SurfaceStore` (`files/surface-v2/`: `observations.jsonl`, `metadata.json`, `decisions.jsonl`, `history.json`, bozuk satırlar `<k>.unreadable.jsonl`, dışa aktarım `WEEM-surface-diagnostics.json`; geri alma/dışlama, gün silme, gün sonu özetleme, bozuk metaveri kapalı başarısız).
- **Y14:** `RoadCalibration` (PR #23 ham RMS, boylamsal ivme, ±%10 hız kapısı), `RoadCalibrationRoadRms` (E10 yol RMS'i, metre ağırlıklı pencere), geri sayım (ofset ≤ 15 sn), `RoadCalibrationStore` (`files/road-calibration-v1/<id>.json`, dışa `WEEM-yol-kalibrasyonu.json`), `RoadCalibrationRecord`/`RoadCalibrationReferences` (seviye 1–10, hız bandı 15/20/25/30, ölçek, çelişki, eksik seviyeler), `CalibrationText`, `SurfaceText.roadScore`.
- **Rota:** `CostModel` `surface`/`surfaceQuality`/`surfaceScale` + "geçilebilirlik: kapalı" işaret (iki yön kapalı, yürünmez); `Planner` düzgün yol adayı (≤ 200 m uzun, ≤ 2 ek dönüş; `QUALITY_SEEK_SCALE` 15); `StreetRatios` ölçülmüş kötü kesim; `NavController.surfaceInputs` (rota hesabında okunur, açık rota yüzey değişince yeniden hesaplanmaz — YolGo gibi); E11 `StreetScores` (sokak puanı, "ölçüm az", sıra, rota kalitesi).
- **Android (`:map`):** `SurfaceSensors` (sensörler + GPS 500 ms, duruşta yavaş örnekleme), `SurfaceRuntime` (gün/etap/duraklatma/profil, kalibrasyon etiketi, montaj yönü, gün silme kancası, `routeInputs`), `RoadCalibrationService` (ön plan servisi, bildirim 7435, kullanıcı başlatır) + `RoadCalibrationOverlay` (yüzen panel), manifest servis. **Ekranlar (`:app`):** Yollarım → Kalite sekmesinin üstünde "Yol puanları" (`SurfaceQualityPanel`), `StreetPiecesScreen`, parça ayrıntısı + darbe olayları + yüzey kaydı/kapanış (`RoadBook.saveMark`), `SurfaceSetupScreen` (Ölçüm ve kalibrasyon), `RoadCalibrationScreen`; harita katmanı "Haritada yüzey renkleri" (`SurfaceLayerRules`, `MapScreen`).

### 18.2 Bilerek farklar
- **Günlük/onay zinciri yok:** YolGo satırları dayanıklı bir günlükten (`telemetry-v2.jsonl`) Flutter'a taşırdı; WEEM tek süreçte satırları doğrudan işler. Sonuç: tamponda bekleyen (≤ 8 sn) pencereler çökmede kaybolur; `TelemetryJournal`/`TelemetryJournalTest` (8) taşınmadı.
- **Play Services yok:** konum `LocationManager` GPS (YolGo FusedLocation); etkinlik tanıma (`MotionReceiver`) yok; yürüme güveni yalnız adım dedektöründen (**`ACTIVITY_RECOGNITION` izni WEEM'de yok** — izin yokken adım dedektörü açılmaz, kapı "sınırlı hareket kanıtı" ile güven 0,65 çalışır). İzin eklensin mi: kullanıcıya soru (SORU-Y13-1).
- Elle işaretler `surface-v2/manual-marks.json`'da değil `MarkStore`'da (`files/map/manual-marks.json`, A6.11) kalır; `SurfaceStore` işaret tutmaz.
- Ham tanı kaydı (`RawDiagnostics`, son 10 dk ham sensör JSONL) ve "Ham JSONL paylaş" **yok**; `surface.raw.debug` anahtarı yok.
- Kalibrasyon ekranı metinleri Türkçe (YolGo'da da öyleydi; yalnız "E10 için yeniden kalibre et" iki dilli).
- Ekran metinleri `:app`'te düz yazılmaz (`TextCoverageTest`): sabitler `SurfaceUiText` (TR/AZ), birleşik satırlar `SurfaceText` (`detailLines`, `impactTitle/Subtitle`, `technical`, `bandStatus`, `diagnostics`, `conflictLine`), kalibrasyon ekranı `CalibrationText` (yalnız TR). "Teknik ölçümler" ve "Ölçüm tanılama" blokları YolGo gibi yalnız Türkçe.
- Dışa aktarım adları: `WEEM-surface-diagnostics.json`, `WEEM-yol-kalibrasyonu.json` (YolGo adları taşınmadı; sıfırdan geçiş S31).
- `route_costing_test` (5) ve `economicLeg` (N5 ekonomi bağı) yazılmadı: SORU-T1-1 onayını bekliyor.

### 18.3 Testler
`SurfaceSignalTest` 11, `RoadCalibrationTest` 18, `RoadCalibrationCountdownTest` 5, `RoadCalibrationRoadRmsTest` 6 (eski Kotlin testleri); `SurfaceGateTest` 12 (`surface_gate_test` + `SurfacePassTracker`), `SurfaceStoreTest` 21 (`surface_store_test` 10, `surface_store_exclusions_test` 4, `road_surface_score_test` 4, birim 3), `RoadReferencesTest` 17 (`road_calibration_test` 7, `road_calibration_score_test` 10), `RouterSurfaceTest` 9 (`router_test` yüzey), `StreetScoresTest` 12 (`street_scores_test` 12; isolate sınaması tek iş parçacığı), `SurfaceTextTest` 8 + `CalibrationTextTest` 6 (`road_score_ui_test` mantığı; ekran metinleri `logic/map`'e taşınınca +6 ve +1: ayrıntı/darbe/teknik/tanılama satırları, `SurfaceUiText` iki dil), `SurfacePipelineTest` 14 (eski testi yoktu: `SurfaceFeed`/`SurfaceProcessor`/`SurfaceLeg`), `SurfaceLayerTest` 3, `NavControllerSurfaceTest` 2; `:app` `SurfaceScreensTest` 5 (Robolectric). `CrashGuardTest` kalibrasyon servisini de kapsar. Toplam `logic/map` +144, `:app` +5.

### 18.4 Açık
- Cihaz sınaması: sensör örnekleme hızı, duruşta yavaşlatma, GPS 500 ms, yüzen panelin üst üste çizimi (Wolt/Bolt üstünde), geri sayım titreşimi, ekran kilidinde servis.
- Yürüme algılama izni (SORU-Y13-1); AZ metin onayı (kullanıcı onayladı, 22 Ekim): yeni AZ satırları (Yol puanları, Ölçüm ve kalibrasyon, Yüzey kaydı) **M-Y13-1**.
- `MAP.md` 9.14 listesindeki "sonra" kalemleri (Y13/Y14) bu bölümle kapandı; Yollarım'da "Eski trafik kayıtları" bölümü YolGo'daki gibi yok (WEEM sıfırdan başlar).
