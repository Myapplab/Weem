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
| 2 | Y7 harita verisi | A6.3 | ajan R | bekliyor <!-- st:2 --> |
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

_Yazılıyor (ajan R)._
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
