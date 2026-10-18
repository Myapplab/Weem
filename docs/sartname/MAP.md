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
| 1 | kaynak haritası | tümü | koordinatör | bekliyor <!-- st:1 --> |
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

_Yazılıyor (koordinatör)._
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
