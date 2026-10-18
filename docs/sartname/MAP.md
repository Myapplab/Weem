# MAP — Aşama 6 şartnamesi (`:map`: Y1–Y12, Y15–Y21, N5)

Durum: A6.1, 18 Ekim 2026 (Claude Opus · xhigh + ultracode; en çok 3 paralel ajan, S13) — **yazılıyor**, bölüm durumu 0.3'te. Kaynak: `Myapplab/kurye_nav` — `yolgo-test` @ `870ab6e` (YolGo `1.11.0+6006`; Dart paketi `kurye_nav`, uygulama kimliği `az.duzyol.app`, Kotlin paketi `az.kurye.kurye_nav`) ve gerektiğinde `rawstats` @ `6388c04`. Satır numaraları bu commit'te açılıp doğrulanır; ham bulgular (`bulgular/Y_routing.md`, `Y_nav.md`, `Y_tracking.md`, `Y_contract.md`) kaynakla karşılaştırılır, düzeltmeler "(bulgu düzeltmesi)" diye işaretlidir.

Kısaltmalar: **YG** = `yolgo-test` kökü (`lib/…`, `android_overlay/…`, `tool/…`, `ci/…`, `assets/…`); **YT** = `test/` (Dart testleri), **YK** = `android_overlay/test/` (Kotlin testleri); **RS** = `rawstats` `app/src/main/java/com/kuryelog/app/`, **RT** = `rawstats` `app/src/test/java/com/kuryelog/app/`. Davranış metni kodun söylediğidir; "WEEM" satırları bu şartnamenin kararıdır. Kapsam ya da plan kararı gerektiren noktalar karar verilmeden Bölüm 17'ye soru olarak yazılır.

**Kim kullanır:** Bölüm 0.2 görev dizini (A6.2a … A6.11: her görevin bölümü, eski dosyaları, testleri, veri adları, WEEM yeri). Bölümler: 0 özet · 1 kaynak haritası · 2 Y7 harita verisi · 3 Y3 rota motoru · 4 Y8 sokak kararları · 5 Y15 bisiklet yolu, Y16 restoran noktaları, Y21 bölge bilgisi · 6 Y1 harita ekranı, Y2 ikinci görünüm (MapLibre) · 7 Y4 navigasyon · 8 Y5 Wolt/Bolt bağlantıları, Y6 adres bulma · 9 Y12 yol kalitesi öğrenme · 10 Y9 hız ve batarya, N5 menzil, Y10 hava · 11 Y11 trafik ışıkları ve YolGo gün motoru · 12 Y17 istatistik, Y18 sürüş kayıtları · 13 Y19 teklif veri seti, Y20 görevler ve hatırlatıcılar · 14 veri sözleşmesi · 15 WEEM yerleşimi · 16 test eşlemesi (A6.2a/A6.2b) · 17 açık noktalar, riskler, sorular. Plan bağlamı: `WEEM_PLANI.md` 3 (Y kodları), 4.2–4.4 (`:map`, `NavPort`, `WeatherPort`, `NavTarget`, `RouteStatus`, `WeatherNow`), 8 (S1, S3, S4, S6, S9).

<!-- BEGIN sec:0 -->
## 0. Özet: WEEM kuralları ve görev dizini

_Yazılıyor (koordinatör; ajan bölümleri bitince)._
<!-- END sec:0 -->

### 0.3 Yazım durumu (A6.1 sürerken; A6.1 bitince silinir)

Her bölüm `<!-- BEGIN sec:N -->` … `<!-- END sec:N -->` arasındadır; ajanlar bölümü yalnız bu işaretler arasında yazar. Yarıda kalırsa: dalı çek, aşağıda "bitti" olmayan bölümü aynı dosya listesiyle sürdür.

| Bölüm | Konu | Görev | Yazan | Durum |
|---|---|---|---|---|
| 0 | özet, WEEM kuralları, görev dizini | tümü | koordinatör | yazılıyor (ajanlardan sonra) (12:17 UTC) <!-- st:0 --> |
| 1 | kaynak haritası | tümü | koordinatör | bitti (12:23 UTC) <!-- st:1 --> |
| 2 | Y7 harita verisi | A6.3 | ajan R | bitti (12:32 UTC) <!-- st:2 --> |
| 3 | Y3 rota motoru | A6.4 | ajan R | bekliyor <!-- st:3 --> |
| 4 | Y8 sokak kararları | A6.4 | ajan R | bekliyor <!-- st:4 --> |
| 5 | Y15, Y16, Y21 | A6.8 | ajan R | bekliyor <!-- st:5 --> |
| 6 | Y1, Y2 harita ekranı | A6.5 | ajan N | bekliyor <!-- st:6 --> |
| 7 | Y4 navigasyon | A6.6 | ajan N | bekliyor <!-- st:7 --> |
| 8 | Y5, Y6 bağlantılar ve adres | A6.6 | ajan N | bekliyor <!-- st:8 --> |
| 9 | Y12 yol kalitesi öğrenme | A6.11 | ajan Q | bekliyor <!-- st:9 --> |
| 10 | Y9, N5, Y10 | A6.7 | ajan T | bekliyor <!-- st:10 --> |
| 11 | Y11 ve YolGo gün motoru | A6.8 | ajan T | bekliyor <!-- st:11 --> |
| 12 | Y17, Y18 | A6.9 | ajan T | bekliyor <!-- st:12 --> |
| 13 | Y19, Y20 | A6.9 | ajan T | bekliyor <!-- st:13 --> |
| 14 | veri sözleşmesi | tümü | koordinatör | bekliyor <!-- st:14 --> |
| 15 | WEEM yerleşimi | tümü | koordinatör | bekliyor <!-- st:15 --> |
| 16 | test eşlemesi A6.2a/A6.2b | A6.2a, A6.2b | koordinatör | bekliyor <!-- st:16 --> |
| 17 | açık noktalar ve sorular | tümü | koordinatör | bekliyor <!-- st:17 --> |

Dosya dağılımı (YG; her dosyanın tek sahibi var, başkası yalnız başvurur):
- **Ajan R (2–5):** `lib/data/*` (road_graph, graph_builder, overpass, elevation, map_data_service, pois, poi_corrections), `lib/routing/` router, planner, route, route_worker, route_hours, street_prefs, bikeways; `lib/core/physics.dart`, `geo.dart`; `lib/ui/bikeway_layer.dart`, `poi_editor.dart`, `region_info.dart`, `road_hours.dart`; `tool/build_map.dart`, `ci/osm_extract.py`, `ci/build.sh` harita kısmı, `assets/map/`, `assets/baku/`. Testler: `router`, `park_paths`, `push_segments`, `bikeway_e13`, `route_hours`, `ww_green_two_way`, `street_prefs_migration`, `overpass`, `map_data_update`, `pois`.
- **Ajan N (6–8):** `lib/ui/` map_view, google_map_surface, map_hud, map_hud_layout, puck_style, locate_button, brand, maps_help, nav_screen, home_picker, settings_screen (yapı), setup_screen, api_settings, widgets (150–289 hariç); `lib/core/theme.dart`, `app_info.dart`; `lib/main.dart`; `lib/nav/*` (route_recorder hariç); `lib/tracking/location_service.dart`, `map_matcher.dart`, `nav_only.dart`, `compass.dart`, `heading_filter.dart`; `lib/input/*`; `android_overlay/MainActivity.kt` (bağlantı, ekran, pusula, dosya, ayarlar yöntemleri), `NavFeedProvider.kt`, `AndroidManifest.xml` intent filtreleri. Testler: `hud_layout`, `locate_button`, `map_hud_gesture`, `puck_arrow`, `yolgo_sade_2`, `home_picker`, `approach_zoom`, `arrival_focus`, `arrival_pin`, `native_fast_fix`, `nav_assistant`, `nav_feed`, `nav_only_location`, `remaining_km_e12`, `route_warnings_h2_h3`, `v150_features`, `yolgo_rules`, `address_parser`, `api_settings`, `geocoder_scope`, `place_search`, YK `NavFeedContractTest`.
- **Ajan Q (9):** `lib/roads/*`, `lib/routing/quality.dart`, `lib/ui/roads_screen.dart`, `road_score.dart`; ses tuşu yakalama (`MainActivity.kt` `setVolumeCapture`, `onVolume`, `onVolumeDownHold`); router'daki öğrenme girdileri (`learned`, `wwRatio`, `wwAvoid`, `wrongWayEdges`, `ghost`) — maliyet formülünün kendisi Bölüm 3'te. Testler: `quality`, `road_learner`, `road_save`, `road_score_ui`, `street_scores`, `day_contributions`, `ww_clean_run`, `ww_no_problem_green`.
- **Ajan T (10–13):** `lib/routing/speed_options.dart`; `lib/tracking/` battery_estimator, shift_engine, shift_controller, light_tracker, stop_semantics, ride_logger, log_store, day_deletion, native_telemetry, home_day/rawstats_shift (yalnız başvuru); `lib/nav/route_recorder.dart`; `lib/core/day_scope.dart`; `lib/economics/*` (offer_engine, route_costing, courier_stages, dataset_*; offer_feed/rawstats_signals başvuru); `lib/scooter/scooter_live.dart`; `lib/weather/weather.dart`; `lib/tasks/*`, `lib/reminders/*`; `lib/ui/` stats_screen, shift_widgets, ride_logs_screen, dataset_screen, dataset_manual, tasks_screen, reminder_widgets, weather_section, weather_widget, scooter_sheet, widgets 150–289; `android_overlay/` Weather*, Dataset*, Telemetry*, MotionReceiver, NotifyPolicy (başvuru); `tools/veri_seti/`. Testler: `battery_estimator`, `v140_features`, `v160_y1_y2`, `v170_features`, `weather_section`, `offer_engine`, `route_costing`, `shift_engine`, `light_leaks_f52`, `light_rule_m14`, `light_tracker`, `courier_stage_follow`, `courier_stages_e8`, `home_day_controller` (4–5), `log_store`, `ride_log_status`, `dataset_days`, `dataset_screen`, `assistant`, `reminder_card`, `reminders`, `task_controller`; YK `WeatherFeedTest`, `TelemetryJournalTest`, `DatasetContractTest`, `DatasetMatchingTest`, `DatasetReadsTest`.
- **Koordinatör (0, 1, 14–17):** `lib/core/settings.dart` (`settings.v1` alan tablosu), `i18n.dart`, manifest ve kimlik, `CHANGELOG.md`; Y13/Y14 (`lib/surface/`, `lib/calibration/`) yalnız sınır notu.

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

Bulgu `Y_routing.md` §Y7 satır satır doğrulandı. Düzeltmeler: başlığın 7. alanı yaya bölgesi **nokta** sayısıdır (`zoneLat.length`), 6. alan `zoneStart.length`; 8 bayt hizası okurken de yazarken de uygulanır; `overpassProblem` 64 baytlık baş + 1.500 baytlık kuyruğa bakar; POI türlerinde `t` (market) de var (Bölüm 5.3); `regionBox` hiçbir yerde kullanılmıyor (ölü); `build_map` merkez grafiği üretmez, yalnız `graph_wide.bin.gz`.

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
| YT `router_test` "graf kurulur ve dosyaya yazılıp okunabilir" | 1 (38'in) | M iç nokta kavşak değil → N = 4, E = 4; serialize→deserialize sonrası `edgeCount`, `climbF`, `names` aynı | `graph.RoadGraphFileTest` | A6.2a → A6.3 | kalan 37 router testi Bölüm 3.12 |
| YT `bikeway_e13_test` "E13 etiketler" (3) + "E13 harita dosyası" (2) | 5 (18'in) | bayrak tablosu; Overpass sorgusu `cycleway` içerir + `out body`; eski başlık (`features` = 0, ofset 36) okunur ve uyarır; sürüm 1 (ofset 4) → `FormatException` | `graph.BikewayTagsTest`, `graph.RoadGraphFileTest` | A6.2a → A6.3 | dosyanın tamamı Bölüm 5.1'de eşlenir |
| YT `park_paths_test` 1–2 | 2 (4'ün) | park bayrağı (park/garden/recreation, `bicycle=no` hariç, parktaki residential almaz), `hasParkData` | `graph.ParkPathsTest` | A6.2a → A6.3 | 3–4 Bölüm 3.12 |
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
- `edgesOfOsmWay` vb. doğrusal tarama (performans; WEEM dizinle hızlandırabilir, sonuç aynı).
- **SORU (koordinatöre):** hazır veri sunucusu `mehemmedsh/kurye_nav_data` bu ortamdan erişilemedi (manifest 404). Seçenekler: (a) zincir aynen kalsın (sunucu yoksa ≤ 20 sn sonra Overpass'a düşer); (b) WEEM'de kaldırılsın, doğrudan Overpass. Önerilen varsayılan (a) — davranış aynı (S9), zararsız.
<!-- END sec:2 -->

<!-- BEGIN sec:3 -->
## 3. Y3 — Rota motoru (A6.4)

_Yazılıyor (ajan R)._
<!-- END sec:3 -->

<!-- BEGIN sec:4 -->
## 4. Y8 — Sokak kararları (A6.4)

_Yazılıyor (ajan R)._
<!-- END sec:4 -->

<!-- BEGIN sec:5 -->
## 5. Y15 Bisiklet yolu katmanı, Y16 Restoran noktaları, Y21 Bölge bilgisi (A6.8)

_Yazılıyor (ajan R)._
<!-- END sec:5 -->

<!-- BEGIN sec:6 -->
## 6. Y1 Harita ekranı ve Y2 İkinci harita görünümü (A6.5)

_Yazılıyor (ajan N)._
<!-- END sec:6 -->

<!-- BEGIN sec:7 -->
## 7. Y4 — Navigasyon (A6.6)

_Yazılıyor (ajan N)._
<!-- END sec:7 -->

<!-- BEGIN sec:8 -->
## 8. Y5 Wolt/Bolt navigasyon bağlantıları ve Y6 Adres bulma (A6.6)

_Yazılıyor (ajan N)._
<!-- END sec:8 -->

<!-- BEGIN sec:9 -->
## 9. Y12 — Yol kalitesi öğrenme (A6.11; sıfırdan, S3)

_Yazılıyor (ajan Q)._
<!-- END sec:9 -->

<!-- BEGIN sec:10 -->
## 10. Y9 Hız ve batarya tahmini, N5 Menzil uyarısı, Y10 Hava durumu (A6.7)

_Bekliyor (ajan T, ikinci dalga)._
<!-- END sec:10 -->

<!-- BEGIN sec:11 -->
## 11. Y11 Trafik ışığı duruşları ve YolGo gün motoru (A6.8)

_Bekliyor (ajan T, ikinci dalga)._
<!-- END sec:11 -->

<!-- BEGIN sec:12 -->
## 12. Y17 İstatistik ekranı ve Y18 Sürüş kayıtları (A6.9)

_Bekliyor (ajan T, ikinci dalga)._
<!-- END sec:12 -->

<!-- BEGIN sec:13 -->
## 13. Y19 Teklif veri seti ekranı ve Y20 Görev kartları, hatırlatıcılar (A6.9)

_Bekliyor (ajan T, ikinci dalga)._
<!-- END sec:13 -->

<!-- BEGIN sec:14 -->
## 14. Veri sözleşmesi

_Yazılıyor (koordinatör)._
<!-- END sec:14 -->

<!-- BEGIN sec:15 -->
## 15. WEEM yerleşimi

_Yazılıyor (koordinatör)._
<!-- END sec:15 -->

<!-- BEGIN sec:16 -->
## 16. Test eşlemesi (A6.2a, A6.2b)

_Yazılıyor (koordinatör; ajan bölümlerindeki test tablolarından)._
<!-- END sec:16 -->

<!-- BEGIN sec:17 -->
## 17. Açık noktalar, riskler ve kullanıcı soruları

_Yazılıyor (koordinatör)._
<!-- END sec:17 -->
