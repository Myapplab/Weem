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
| 2 | Y7 harita verisi | A6.3 | ajan R | bitti (12:38 UTC) <!-- st:2 --> |
| 3 | Y3 rota motoru | A6.4 | ajan R | bitti (12:37 UTC) <!-- st:3 --> |
| 4 | Y8 sokak kararları | A6.4 | ajan R | bekliyor <!-- st:4 --> |
| 5 | Y15, Y16, Y21 | A6.8 | ajan R | bekliyor <!-- st:5 --> |
| 6 | Y1, Y2 harita ekranı | A6.5 | ajan N | bekliyor <!-- st:6 --> |
| 7 | Y4 navigasyon | A6.6 | ajan N | bekliyor <!-- st:7 --> |
| 8 | Y5, Y6 bağlantılar ve adres | A6.6 | ajan N | bitti (12:34 UTC) <!-- st:8 --> |
| 9 | Y12 yol kalitesi öğrenme | A6.11 | ajan Q | kısmen: 9.1–9.8 yazıldı; sırada 9.9–9.13 (rota bağı, veri, testler, yerleşim, sorular) (12:39 UTC) <!-- st:9 --> |
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
- **AZ çeviri hatası (eski hata, WEEM'de baştan doğru):** arka plan mesajları `trMsg` ile alt dize değişimiyle çevrilir ve sözlük sırası yüzünden karışık çıkar: `'Hazır yol verisi aranıyor'` → "Hazırdır yol məlumatı axtarılır", `'Tüm sunucular meşgul, 15 sn sonra tekrar'` → "Tüm sunucular məşğuldur, 15 san sonra yenidən", `'Yol verisi indirilemedi (…)'` → "Yol məlumatı indirilemedi (…)"; `'Harita dosyası eski sürüm'` hiç çevrilmez. WEEM: ilerleme ve hata metinleri kodlu (`MapDownload` aşaması + sayılar), dil tablosunda her mesajın bütün AZ karşılığı (sözlüğün hedef metinleri: "Hazır yol məlumatı axtarılır", "Bütün serverlər məşğuldur, N san sonra yenidən", "Yol məlumatı yüklənmədi (…)"; "Harita dosyası eski sürüm" için AZ A6.3'te yazılır). TR metinler harfiyen aynı.
- `edgesOfOsmWay` vb. doğrusal tarama (performans; WEEM dizinle hızlandırabilir, sonuç aynı).
- **SORU (koordinatöre):** hazır veri sunucusu `mehemmedsh/kurye_nav_data` bu ortamdan erişilemedi (manifest 404). Seçenekler: (a) zincir aynen kalsın (sunucu yoksa ≤ 20 sn sonra Overpass'a düşer); (b) WEEM'de kaldırılsın, doğrudan Overpass. Önerilen varsayılan (a) — davranış aynı (S9), zararsız.
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

Bulgu `Y_routing.md` §Y3 kaynakta doğrulandı. Düzeltmeler: satır aralıkları yukarıdaki gibi (`CostModel` 26–342, `_eval` 242–341); kara liste denetimi `_eval`'de **en sonda** (sonuç aynı: kenar kapalı); `walkable` (yürüme) ve `allowed` (sürüş) ayrı kurallardır (3.5); `softBlock` parametresi tanımlı ama **hiç kullanılmıyor** (ölü); `wrongWayKmh` (18) ve `parkPenaltyS` (15) ayardan değil `RideParams` varsayılanından gelir.

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
- F-N2 Desen taşınabilirliği (8.4 tablosu) ve `LinkUri`; WEEM'de ek test vektörleri: NBSP'li "Mehdi Hüseyn\u00A099" → güçlü (Dart `\s` NBSP'yi eşler); "а0501234567" (Kiril harfe bitişik telefon) → telefon silinir; büyük harf "MƏNZİL 45" → `Mənzil 45`, "КВ. 12" → `Mənzil 12`; "BAKİ" (noktalı büyük İ): Kotlin `İ`'yi `i` sayar, Dart saymaz → `_withCity` Kotlin'de `, Bakı` eklemez. WEEM Kotlin sonucunu kabul eder (Azerice doğru), test sabitler; `_extractUnit`'teki elle `İ → i` korunur.
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

Bulgu doğrulaması: `Y_contract.md` `roads.*` satırı (`road_controller.dart:53-58, 94-98`) doğru (`roads.days.v2`, `roads.days.damaged.v2` dizge olarak 91–99). `Y_routing.md` §Y3 "`learned` (Y12)" → **(bulgu düzeltmesi:** `learned` yalnız **elle puanları** taşır; `learnedForRouting()` `ratings`'ten `(q 0, c 0, r)` üretir, otomatik `WayStats` bilerek atlanır — `road_knowledge.dart:159-168`, yorum "traffic stops/vibration … must not affect surface routing costs"). "Y12/Y13 sonra olduğundan WEEM 1.0'da learned/surface/marks/wwRatio boş gelebilir" → **(bulgu düzeltmesi:** S3 ile Y12 1.0'da; `learned`, `wwRatio`, `wwAvoid`, `ghost`, `wrongWayEdges` dolu gelir; `surface`/`surfaceQuality` boş (Y13/Y14); `marks` yalnız elle kötü nokta, SORU-Q1). `OZELLIK_HARITASI` Y12 eski kodundaki `lib/ui/road_score.dart` → **(düzeltme:** Y14 kalibrasyon UI'ı). Y12 test sayısı 9.11'de bölündü.

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
- **Cadde kötü kesim oranı** (`street_ratio.dart`): kötü uzunluk = Y13 ölçülmüş kesimler (puan < 50, güven ≥ 0,35) ∪ kötü noktaların aralıkları, kenar başına aralık birleşimi; toplam = way'in tekil kenarlarının uzunluğu. Metin: kötü < 1 m → "Kötü kesim yok · <uzunluk>" / "Pis hissə yoxdur · <uzunluk>", aksi "%<p> kötü · <m> m / <uzunluk>" / "%<p> pis · <m> m / <uzunluk>" (uzunluk < 1000 m "N m", aksi Dart `toStringAsFixed(1)` "2.0 km" — noktalı). Uzun basış menüsünde, alt yazı "Ölçülmüş kötü kesimler + kötü noktalar (±25 m)" / "Ölçülmüş pis hissələr + pis nöqtələr (±25 m)" (`nav_screen.dart:271-277`). Test: `router_test` "cadde oranı" (Bölüm 3). WEEM 1.0: yalnız kötü noktalarla (Y13 özeti yok), metin aynen.
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
