# Özellik haritası — 63 özellik → eski kod, testler, veri, bağımlılık, WEEM modülü

Durum: A0.1, 17 Ekim 2026; Aşama 3 satırlarının (R1–R3, R5–R13, R15, R16, R19) Ayrıntı bağlantıları A3.1'de `CAPTURE.md`/`ORDERS.md`'ye çevrildi; Aşama 5 satırları (S1–S8, R14) A5.1'de `SCOOTER.md`'ye bağlandı; Aşama 6 satırları (Y1–Y12, Y15–Y21, N5) A6.1'de `MAP.md`'ye bağlandı (test sütunu ad eşlemesi olarak kalır; çeviri kararları `MAP.md` 16). Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04` (**RS** = `app/src/main/java/com/kuryelog/app/`, **NB** = `ninebot-logger/app/src/main/java/az/ninebot/logger/`) ve `yolgo-test` @ `870ab6e` (**YG**). Kapsam ve kararlar: `docs/WEEM_PLANI.md` Bölüm 3 ve 8. Test adları ve sayıları `TEST_ENVANTERI.md` ile aynı betikten (elle yazılmadı). Veri adları: `VERI_SOZLESMESI.md`.

**Nasıl kullanılır:** bir modüle başlamadan önce o modülün satırlarındaki eski dosyalar ve testler o aşamanın şartname görevinde (A2.1, A3.1, A5.1, A6.1) okunur; davranış ayrıntısı (eşikler, durum geçişleri) varsa `bulgular/` dosyasındadır. `bulgular/` ham ve kısmidir (çok ajanlı okuma usage nedeniyle yarıda durduruldu); boş kalan özelliklerin ayrıntısı şartname görevinde çıkarılır.

**Kesinlik:** eski kod sütunu dosya adları ve ham bulgulardan; test sütunu test adından eşlendi (bir test birden çok özelliğe sayılabilir, toplamlar bu yüzden test envanterinden büyüktür). Testi **yok** olan özellikler: Y2, Y17, Y21 (eski kodda ekran var, test yok → WEEM'de karakterizasyon testi yazılır) ve yeni özellikler N2, N3, N6, N8.

## Özet — WEEM 1.0 (59)

| Kod | Özellik | WEEM modül · aşama | Test (adet) |
|---|---|---|---:|
| Y1 | Harita ekranı | `:map` · 6 (A6.5) | 22 |
| Y2 | İkinci harita görünümü (OSM) | `:map` · 6 (A6.5; MapLibre, S4) | 0 |
| Y3 | Kendi rota motoru | `:map` `logic/` · 6 (A6.4) | 81 |
| Y4 | Navigasyon | `:map` · 6 (A6.6) | 75 |
| Y5 | Wolt/Bolt navigasyon bağlantıları | `:map` `logic/` + `:app` · 6 (A6.6) | 23 |
| Y6 | Adres bulma | `:map` `logic/` · 6 (A6.6) | 27 |
| Y7 | Harita verisi | `:map` `logic/` · 6 (A6.3) | 5 |
| Y8 | Sokak kararları | `:map` · 6 (A6.4) | 18 |
| Y9 | Hız ve batarya tahmini | `:map` `logic/` · 6 (A6.7) | 54 |
| Y10 | Hava durumu | `:map` · 6 (A6.7) | 16 |
| Y11 | Trafik ışığı duruşları | `:map` `logic/` · 6 (A6.8) | 48 |
| Y12 | Yol kalitesi öğrenme | `:map` `logic/` · 6 (A6.11; sıfırdan, S3) | 55 |
| Y15 | Bisiklet yolu katmanı | `:map` · 6 (A6.8) | 18 |
| Y16 | Restoran noktaları | `:map` · 6 (A6.8) | 4 |
| Y17 | İstatistik ekranı | `:map` · 6 (A6.9) | 0 |
| Y18 | Sürüş kayıtları | `:map` · 6 (A6.9) | 17 |
| Y19 | Teklif veri seti ekranı | `:orders` + `:map` · 6 (A6.9) | 33 |
| Y20 | Görev kartları ve hatırlatıcılar | `:map` · 6 (A6.9) | 38 |
| Y21 | Bölge bilgisi | `:map` · 6 (A6.8) | 0 |
| R1 | Wolt ve Bolt ekranını okuma | `:capture` `logic/` + servis · 3 (A3.4) | 186 |
| R2 | Bildirim okuma | `:capture` · 3 (A3.4) | 0 |
| R3 | Teklif kaydı | `:capture` + `:orders` · 3 (A3.5) | 158 |
| R4 | Teklif puanı | `:economics` `logic/` · 4 (A4.1) | 90 |
| R5 | Sipariş takibi | `:orders` `logic/` · 3 (A3.5) | 296 |
| R6 | Restoran ve müşteri balonları | `:orders` · 3 (A3.7) | 36 |
| R7 | Kurye uygulaması üstünde gösterge | `:orders` + `:capture` · 3 (A3.7) | 13 |
| R8 | Nakit ve bahşiş | `:orders` · 3 (A3.6) | 32 |
| R9 | Cepteki nakit | `:orders` · 3 (A3.6) | 14 |
| R10 | Boost / Earn extra | `:orders` · 3 (A3.6) | 16 |
| R11 | Çevrimiçi süreler | `:orders` · 3 (A3.6) | 0 |
| R12 | Siparişler ve gün sayfaları | `:orders` + `:app` · 3 (A3.8) | 46 |
| R13 | Resmî değerlerle karşılaştırma | `:orders` · 3 (A3.6) | 42 |
| R14 | Sipariş başına enerji | `:scooter` + `:economics` · 5 (A5.3) | 43 |
| R15 | Teklif anının havası | `:orders` · 3 (A3.6) — Y10 Aşama 6'da gelir | 9 |
| R16 | Başka navigasyon uygulamasından mesafe | `:capture` `logic/` · 3 (A3.4) | 4 |
| R17 | Yedek ve geri yükleme | `:core` · 4 (A4.3) | 17 |
| R18 | Anonim analiz dışa aktarımı | `:core` · 4 (A4.3) | 30 |
| R19 | Uyarılar | `:capture` + `:app` + `:core` · 3 (A3.4, A3.7) | 5 |
| R20 | Çökme kaydı | `:core` + `:app` · 2 (A2.3) | 17 |
| S1 | Scooter bağlantısı | `:scooter` `logic/` + BLE · 5 (A5.2) | 138 |
| S2 | Canlı panel | `:scooter` · 5 (A5.3) | 82 |
| S3 | Sürüş kaydı | `:scooter` · 5 (A5.3) | 26 |
| S4 | Yolculuk sayacı | `:scooter` `logic/` · 5 (A5.3) | 41 |
| S5 | Harici batarya | `:scooter` · 5 (A5.3) — enerji ölçümü açık sorun | 64 |
| S6 | Yeniden bağlanma | `:scooter` · 5 (A5.2) | 14 |
| S7 | Yüzen panel | `:scooter` + `:app` · 5 (A5.4, bitti; cihazda doğrulanmadı) | 23 |
| S8 | Tanı araçları | `:scooter` · 5 (A5.4, bitti; cihazda doğrulanmadı) | 36 |
| G1 | Tek vardiya / gün | `:core` `logic/` · 2 (A2.4) + 7 | 139 |
| G2 | Ev konumu | `:core` + `:map` · 2 (A2.4) + 7 | 51 |
| G3 | Dil | `:app` · 1 (A1.3) | 5 |
| G4 | Az bildirim | `:core` · 2 (A2.4) | 5 |
| G5 | İlk açılış kurulumu | `:app` · 7 (A7.2) | 3 |
| N1 | 2 telefon modu | `:link` · 8 | 133 |
| N2 | Uzaktan kullanım | `:remote` · 9 | 0 |
| N3 | Otomatik şifreli yedek | `:core` · 4 (A4.3) | 0 |
| N4 | Gün sonu raporu ve net kazanç | `:economics` · 4 (A4.2) | 6 |
| N5 | Menzil uyarısı | `:economics` + `:map` · 6 (A6.7) | 13 |
| N6 | Hırsızlık koruması | `:link` + `:scooter` · 8 (A8.4) | 0 |
| N8 | Günlük kazanç hedefi | `:economics` · 4 (A4.2) | 0 |

Modül başına: `:capture` R1 R2 R16 (+R3 alanları) · `:orders` R3 R5–R13 R15 R19 Y19 (veri seti) · `:economics` R4 N4 N5 N8 · `:core` G1 G2 G4 R17 R18 R20 N3 · `:scooter` S1–S8 R14 · `:map` Y1–Y12 Y15–Y21 (Y19'da rota ölçümü, N5'te batarya) · `:link` N1 N6 · `:remote` N2 · `:app` G3 G5.

## Ayrıntı

### Y1 Harita ekranı
- **Eski kod:** YG `lib/ui/map_view.dart`, `google_map_surface.dart`, `map_hud*.dart`, `puck_style.dart`, `locate_button.dart`, `lib/core/theme.dart`, `lib/ui/brand.dart`, `lib/main.dart`
- **Testler:** `RawPaletteContrastTest` (2); `hud_layout_test` (2); `locate_button_test` (3); `map_hud_gesture_test` (2); `puck_arrow_test` (3); `yolgo_sade_2_test` (10) — **22**
- **Veri:** `settings.v1` (tema)
- **Bağımlı:** Y3, Y4, G3
- **WEEM:** `:map` · 6 (A6.5)
- **Ayrıntı:** `MAP.md` 6 (6.2 kabuk, 6.3 yerleşim, 6.4 yüzeyler, 6.5–6.7 konum işareti, katmanlar, uzun basış, 6.8 tema, 6.9 kurulum ve ayarlar, 6.12 gün kartı sınırı, 6.14 testler)

### Y2 İkinci harita görünümü (OSM)
- **Eski kod:** YG `lib/ui/map_view.dart` (flutter_map yüzeyi), `pubspec.yaml` (`flutter_map`, `latlong2`)
- **Testler:** **yok**
- **Veri:** OSM karoları (ağ)
- **Bağımlı:** Y1
- **WEEM:** `:map` · 6 (A6.5; MapLibre Native, iki stil: bugünkü OSM raster karoları ve vektör — S4)
- **Ayrıntı:** `MAP.md` 6.4 (yüzeyler, altlıklar, karo kuralları; SORU-N5), 6.16

### Y3 Kendi rota motoru
- **Eski kod:** YG `lib/routing/router.dart` (CostModel, A*), `planner.dart`, `route.dart`, `route_worker.dart`, `quality.dart`, `speed_options.dart`, `route_hours.dart`, `lib/core/physics.dart`, `lib/core/geo.dart`
- **Testler:** `bikeway_e13_test` (18); `park_paths_test` (4); `push_segments_test` (5); `route_hours_test` (3); `router_test` (38); `ww_green_two_way_test` (4); `quality_test` (9) — **81**
- **Veri:** `graph_<region>.bin`, DEM `.hgt`, `streets.*`, yol kalitesi verisi (Y12, WEEM'de sıfırdan)
- **Bağımlı:** Y7, Y8, Y15, Y12 (A6.11), Y9
- **WEEM:** `:map` `logic/` · 6 (A6.4)
- **Ayrıntı:** `MAP.md` 3 (3.2 fizik, 3.4 kenar maliyeti, 3.6 A*, 3.10 planlayıcı, 3.14 testler); ham: `bulgular/Y_routing.md` §Y3

### Y4 Navigasyon
- **Eski kod:** YG `lib/nav/nav_controller.dart`, `reroute_gate.dart`, `approach_zoom.dart`, `arrival_focus.dart`, `route_line.dart`, `toast.dart`, `nav_feed.dart`, `lib/ui/nav_screen.dart`, `lib/tracking/location_service.dart`, `map_matcher.dart`, `nav_only.dart`
- **Testler:** `YolGoNavE8Test` (13); `approach_zoom_test` (4); `arrival_focus_test` (4); `arrival_pin_test` (3); `native_fast_fix_test` (2); `nav_assistant_test` (1); `nav_feed_test` (11); `nav_only_location_test` (2); `remaining_km_e12_test` (7); `route_warnings_h2_h3_test` (3); `v150_features_test` (15); `yolgo_rules_test` (4); `NavFeedContractTest` (6) — **75**
- **Veri:** `nav/v1/current` (R5 tüketir)
- **Bağımlı:** Y3, Y1, Y6, G1
- **WEEM:** `:map` · 6 (A6.6)
- **Ayrıntı:** `MAP.md` 7 (7.4 rota hesabı, 7.7 takip, 7.8 varış, 7.9 `RouteStatus`, 7.10 kamera, 7.12 yalnız-nav, 7.15 testler)

### Y5 Wolt/Bolt navigasyon bağlantıları
- **Eski kod:** YG `android_overlay/AndroidManifest.xml:27-120`, `MainActivity.kt` (`linkOf`, `extractLink`), `NavFeedProvider.kt` (`IncomingLink`), `lib/input/address_parser.dart`
- **Testler:** `address_parser_test` (17); `NavFeedContractTest` (6) — **23**
- **Veri:** intent filtreleri (veri sözleşmesi §2)
- **Bağımlı:** Y6, Y4, N1 (Provider'da yakalanır)
- **WEEM:** `:map` `logic/` + `:app` · 6 (A6.6)
- **Ayrıntı:** `MAP.md` 8.2 (bağlantının gelişi), 8.7 (hedefin kabulü), 8.11 (testler); ham: `bulgular/Y_nav.md` §Y5

### Y6 Adres bulma
- **Eski kod:** YG `lib/input/clipboard_sniffer.dart`, `address_parser.dart`, `plus_code.dart`, `destination_resolver.dart`, `geocoder.dart`, `place_search.dart`, `places_rest.dart`, `lib/ui/api_settings.dart`
- **Testler:** `address_parser_test` (17); `api_settings_test` (1); `geocoder_scope_test` (1); `place_search_test` (8) — **27**
- **Veri:** `search.recents.v2`, `clipboard.lastHash`, Google anahtarı ayarı
- **Bağımlı:** Y5, Y16, Y7, Y4
- **WEEM:** `:map` `logic/` · 6 (A6.6)
- **Ayrıntı:** `MAP.md` 8.3–8.9 (pano, ayrıştırma, Plus Code, geokod, yer arama, API ekranı), 8.11; ham: `bulgular/Y_nav.md` §Y6

### Y7 Harita verisi
- **Eski kod:** YG `lib/data/road_graph.dart`, `graph_builder.dart`, `map_data_service.dart`, `overpass.dart`, `elevation.dart`, `tool/build_map.dart`, `ci/osm_extract.py`, `assets/map/manifest.json`
- **Testler:** `map_data_update_test` (2); `overpass_test` (3) — **5**
- **Veri:** `files/mapdata/*` (graf ikili biçimi, POI, DEM)
- **Bağımlı:** Y3, Y16, Y15, Y21
- **WEEM:** `:map` `logic/` · 6 (A6.3)
- **Ayrıntı:** `MAP.md` 2 (2.4 graf kurma, 2.5 KNG1 biçimi, 2.8 Overpass, 2.9 güncelleme, 2.11 CI, 2.13 testler); ham: `bulgular/Y_routing.md` §Y7

### Y8 Sokak kararları
- **Eski kod:** YG `lib/routing/street_prefs.dart`, `route_hours.dart`, `lib/ui/road_hours.dart`
- **Testler:** `route_hours_test` (3); `street_prefs_migration_test` (1); `ww_clean_run_test` (5); `ww_green_two_way_test` (4); `ww_no_problem_green_test` (5) — **18**
- **Veri:** `streets.approved/blocked/steep/names/hours.v1` (yeniden girilir, S1)
- **Bağımlı:** Y3, Y12 (ww soruları)
- **WEEM:** `:map` · 6 (A6.4)
- **Ayrıntı:** `MAP.md` 4 (4.2 model ve kalıcılık, 4.3 saatli izin, 4.4 karar yerleri, 4.6 testler; `street_prefs_migration_test` çevrilmez, S1)

### Y9 Hız ve batarya tahmini
- **Eski kod:** YG `lib/routing/speed_options.dart`, `lib/core/physics.dart`, `lib/tracking/battery_estimator.dart`, `lib/ui/widgets.dart:150-289`
- **Testler:** `battery_estimator_test` (5); `remaining_km_e12_test` (7); `v140_features_test` (14); `v150_features_test` (15); `v170_features_test` (13) — **54**
- **Veri:** `battery.*`, `settings.v1` katsayıları
- **Bağımlı:** Y3, S2 (canlı %), N5
- **WEEM:** `:map` `logic/` · 6 (A6.7)
- **Ayrıntı:** `MAP.md` 10.2–10.5 (hız seçenekleri, batarya durumu, canlı scooter değeri, sol kutu), 10.12; ham: `bulgular/Y_tracking.md` §Y9

### Y10 Hava durumu
- **Eski kod:** YG `lib/weather/weather.dart`, `lib/ui/weather_section.dart`, `weather_widget.dart`, `android_overlay/WeatherWorker.kt`, `WeatherFeed*.kt`
- **Testler:** `api_settings_test` (1); `v160_y1_y2_test` (5); `weather_section_test` (4); `yolgo_rules_test` (4); `WeatherFeedTest` (2) — **16**
- **Veri:** Open-Meteo (ağ), `weather.*`, `duzyol_weather`; yük `weather/current`
- **Bağımlı:** R15 (tüketir), N1 (R→P)
- **WEEM:** `:map` · 6 (A6.7)
- **Ayrıntı:** `MAP.md` 10.7–10.10 (Open-Meteo, bildirim 7301, ekranlar, `WeatherPort`), 10.12

### Y11 Trafik ışığı duruşları
- **Eski kod:** YG `lib/tracking/shift_engine.dart:198-237,383-507`, `light_tracker.dart`, `stop_semantics.dart`
- **Testler:** `shift_engine_test` (9); `light_leaks_f52_test` (3); `light_rule_m14_test` (14); `light_tracker_test` (4); `v140_features_test` (14); `yolgo_rules_test` (4) — **48**
- **Veri:** `lights.v1` (yeniden girilir ve öğrenilir, S1), `lightMaxM`
- **Bağımlı:** G1, Y7, Y17
- **WEEM:** `:map` `logic/` · 6 (A6.8)
- **Ayrıntı:** `MAP.md` 11 (11.2 gün = vardiya, 11.4 duruş tespiti, 11.8 ışık kuralı, 11.9–11.12 canlı ölçer, birleşme, gün sonu cevabı, elle işaretler, 11.16 testler); ham: `bulgular/Y_tracking.md` §Y11

### Y12 Yol kalitesi öğrenme (17 Ekim'de 1.0'a alındı, S3)
- **Eski kod:** YG `lib/roads/`, `lib/ui/roads_screen.dart`, `road_score.dart`, `lib/routing/quality.dart`; ses tuşu `setVolumeCapture` (Ses+/Ses−)
- **Testler:** `day_contributions_test` (3); `quality_test` (9); `road_learner_test` (9); `road_save_test` (1); `road_score_ui_test` (11); `street_scores_test` (12); `ww_clean_run_test` (5); `ww_no_problem_green_test` (5) — **55**
- **Veri:** YolGo `roads.*` aktarılmaz (S1); WEEM aynı anlamda yeni veri tutar, sıfırdan öğrenir
- **Bağımlı:** Y3 (rota maliyeti: öğrenilen kalite, ters yön oranı), Y7, Y8
- **WEEM:** `:map` `logic/` · 6 (A6.11)
- **Ayrıntı:** `MAP.md` 9 (9.3 öğrenme, 9.4 puan soruları, 9.5 Ses+/Ses−, 9.6 ters yön, 9.8 ekranlar, 9.9 rota maliyeti, 9.11 testler; Y13/Y14'e ait 23 test sonra, SORU-Q2); ham: `bulgular/Y_contract.md` (Y12–Y14 başvurusu)

### Y15 Bisiklet yolu katmanı
- **Eski kod:** YG `lib/routing/bikeways.dart`, `lib/ui/bikeway_layer.dart`, `graph_builder.dart` (`isBikewayTags`)
- **Testler:** `bikeway_e13_test` (18) — **18**
- **Veri:** `streets.bikeway.v1` (yeniden girilir, S1)
- **Bağımlı:** Y3, Y7
- **WEEM:** `:map` · 6 (A6.8)
- **Ayrıntı:** `MAP.md` 5.1–5.2

### Y16 Restoran noktaları
- **Eski kod:** YG `lib/data/pois.dart`, `poi_corrections.dart`, `lib/ui/poi_editor.dart`
- **Testler:** `pois_test` (4) — **4**
- **Veri:** `pois_<region>.json`, `places.local.v1` (yeniden girilir, S1)
- **Bağımlı:** Y7, Y6, Y11 (AVM ve ışık kuralı) ve YolGo gün motoru
- **WEEM:** `:map` · 6 (A6.8)
- **Ayrıntı:** `MAP.md` 5.3, 5.6 (POI dosyası 2.10)

### Y17 İstatistik ekranı
- **Eski kod:** YG `lib/ui/stats_screen.dart`, `shift_widgets.dart`, `ShiftController` gün raporu
- **Testler:** **yok**
- **Veri:** `app_flutter/logs/*.gun.json` → WEEM `files/map/logs/`
- **Bağımlı:** G1, Y11, Y18, R12
- **WEEM:** `:map` · 6 (A6.9)
- **Ayrıntı:** `MAP.md` 12.6–12.9 (gün raporu ve geçmiş, İstatistik, gün kartı satırları, gün silme)

### Y18 Sürüş kayıtları
- **Eski kod:** YG `lib/tracking/ride_logger.dart`, `log_store.dart`, `lib/nav/route_recorder.dart`, `lib/ui/ride_logs_screen.dart`, `android_overlay/TelemetryJournal.kt`
- **Testler:** `log_store_test` (7); `ride_log_status_test` (2); `TelemetryJournalTest` (8) — **17**
- **Veri:** `app_flutter/logs/*_ride_*.jsonl`, `*_route_*.json` → WEEM `files/map/logs/`; dışa aktarma `duzyol-journal-v1`
- **Bağımlı:** G1, Y4
- **WEEM:** `:map` · 6 (A6.9)
- **Ayrıntı:** `MAP.md` 12.2–12.5, 12.10 (sürüş günlüğü, sürüşler, kayıt deposu, rota kaydı, ekran), 12.13; `TelemetryJournalTest` (8) Y13 ile gelir (12.11); YolGo kayıtlarını içe aktarma A7.1 (12.15)

### Y19 Teklif veri seti ekranı
- **Eski kod:** YG `lib/ui/dataset_screen.dart`, `dataset_manual.dart`, `lib/economics/dataset_runtime.dart`, `dataset_days.dart`, `android_overlay/Dataset*.kt`; RS `LogFeedV3*.kt`
- **Testler:** `LogFeedV3Test` (10); `dataset_days_test` (2); `dataset_screen_test` (5); `DatasetContractTest` (6); `DatasetMatchingTest` (4); `DatasetReadsTest` (6) — **33**
- **Veri:** `app_flutter/TeklifVeriSeti/…` → WEEM `files/TeklifVeriSeti/`; RS gün klasörü
- **Bağımlı:** R1–R18 kayıtları, G1
- **WEEM:** `:orders` (`logic/orders` `dataset`) + `:map` (`RouteMeasurePort`) · 6 (A6.9)
- **Ayrıntı:** `MAP.md` 13.2–13.11 (akış, satırlar, zenginleştirme, gün toplama, mühür ve ZIP, ekran, elle ekleme, `kontrol.py`, WEEM kayıt düzeni), 13.18 (`LogFeedV3Test` `logic/orders` `dataset`'te uyarlanır)

### Y20 Görev kartları ve hatırlatıcılar
- **Eski kod:** YG `lib/tasks/daily_task.dart`, `task_controller.dart`, `lib/ui/tasks_screen.dart`, `lib/reminders/reminders.dart`, `lib/ui/reminder_widgets.dart`
- **Testler:** `assistant_test` (14); `courier_stage_follow_test` (14); `nav_assistant_test` (1); `reminder_card_test` (3); `reminders_test` (5); `task_controller_test` (1) — **38**
- **Veri:** `daily.tasks.*`, `reminders.*` (yeniden girilir, S1); kanal `hatirlatici`
- **Bağımlı:** G1, R5 (aşamalar), Y6, G4
- **WEEM:** `:map` · 6 (A6.9)
- **Ayrıntı:** `MAP.md` 13.12–13.16 (görev modeli, denetleyici, ekran, hatırlatıcılar), 13.18 (`courier_stage_follow_test` gün motorunda 11.16, `nav_assistant_test` Y4'te 7.15, `assistant_test` paylara bölünür 16.3); ham: `bulgular/Y_tracking.md` §Y20

### Y21 Bölge bilgisi
- **Eski kod:** YG `lib/ui/region_info.dart`, `assets/baku/`
- **Testler:** **yok**
- **Veri:** Mərkəzi / Böyük Bakı çerçeveleri (kod içi)
- **Bağımlı:** Y7
- **WEEM:** `:map` · 6 (A6.8)

### R1 Wolt ve Bolt ekranını okuma
- **Eski kod:** RS `WatchService.kt`, `CourierParser.kt`, `CourierScreens.kt`, `OfferCapture.kt` (`OcrPace`), `RecordingPolicy.kt`, `SliderSpinner.kt`, `res/xml/accessibility_config.xml`
- **Testler:** `BoltBubbleR2Test` (5); `OcrPaceD6Test` (11); `ParserEconomicsTest` (23); `RecordingPolicyTest` (12); `WoltDeliverySliderTest` (9); `WoltOfferFeeK37Test` (6); `WoltSliderSpinnerK33Test` (9); `IcuRegexTest` (2); `CourierFlowTest` (44); `DeliveryRecognitionTest` (24); `SahaTekrarTest` (16); `WoltFlowK1K5Test` (25) — **186**
- **Veri:** `screens.jsonl`, `events.jsonl`; ML Kit OCR (yerel)
- **Bağımlı:** —
- **WEEM:** `:capture` `logic/` + servis · 3 (A3.4)
- **Ayrıntı:** `CAPTURE.md` 1 (servis), 2 (ayrıştırıcı), 3 (OCR tempo, ekran görüntüsü, maskeleme), 6.1 (`screens.jsonl`), 8 (test eşlemesi)

### R2 Bildirim okuma
- **Eski kod:** RS `NotifService.kt`
- **Testler:** **yok**
- **Veri:** `notifs.jsonl`
- **Bağımlı:** R3, R5, R16
- **WEEM:** `:capture` · 3 (A3.4)
- **Ayrıntı:** `CAPTURE.md` 4.1 (bildirim dinleyici), 6.2 (`notifs.jsonl`)

### R3 Teklif kaydı
- **Eski kod:** RS `CourierParser.parse`, `DeliveryCore.kt` (teklif yaşam döngüsü, `OfferState`), `OrderTracker.kt`
- **Testler:** `ParserEconomicsTest` (23); `WoltOfferFeeK37Test` (6); `CourierFlowTest` (44); `SahaTekrarTest` (16); `WoltFlowK1K5Test` (25); `CoreLifecycleV2Test` (23); `DualOfferStatusK30Test` (3); `OfferFeedV2Test` (6); `offer_feed_versions_test` (6); `CourierFeedPayloadTest` (6) — **158**
- **Veri:** `courier.db` `offers`, `economics-offers.jsonl`
- **Bağımlı:** R1, R4, R10, R15
- **WEEM:** `:capture` + `:orders` · 3 (A3.5)
- **Ayrıntı:** `CAPTURE.md` 2.4–2.6 (ücret, kabul, aşama, alanlar); `ORDERS.md` 2 (teklif yaşam döngüsü), 10.1 (kayıt biçimi), 13.2 (`OfferSummary`)

### R4 Teklif puanı
- **Eski kod:** RS `EconomicsV2.kt`, `EconomicsV2Estimators.kt`, `EconomicsLogs.kt`, `OfferScore.kt`, `OfferGeo.kt`, `ShadowEvaluator.kt`; `docs/ekonomi-v2/` (`ref.py`, SPEC 2.1.0)
- **Testler:** `EconomicsLogsTest` (21); `EconomicsObservationTest` (13); `EconomicsV2GoldenTest` (8); `EconomicsV2PriorsTest` (4); `OfferGeoTest` (12); `OfferScoreE4Test` (8); `ShadowEvaluatorTest` (11); `offer_engine_test` (7); `route_costing_test` (6) — **90**
- **Veri:** `economics-*.jsonl`, `economics-offer-scores/ratings.jsonl`
- **Bağımlı:** R3, R10, R14, R15, Y3/Y9 (rota maliyeti)
- **WEEM:** `:economics` `logic/` · 4 (A4.1)
- **Ayrıntı:** `ECONOMICS.md` (A4.1 bitti: Rawstats 77 + ekonomi payı 19 test yeşil, saha uçtan uca, E4 ekranı; YolGo `offer_engine_test`/`route_costing_test` 13: çevrilmez ya da Y13 ile sonra, `MAP.md` 10.14, 16.4)

### R5 Sipariş takibi
- **Eski kod:** RS `DeliveryCore.kt`, `OrderTracker.kt`, `TripStages.kt`, `VenueArrival.kt`, `ArrivalDistance.kt`, `WaitObservation.kt`, `DeliveryDeadline.kt`, `Readiness.kt`
- **Testler:** `WoltDeliverySliderTest` (9); `WoltSliderSpinnerK33Test` (9); `CourierFlowTest` (44); `DeliveryRecognitionTest` (24); `SahaTekrarTest` (16); `WoltFlowK1K5Test` (25); `ArrivalDistanceM20Test` (4); `CoreLifecycleV2Test` (23); `CustomerMatchK21Test` (5); `CustomerNearM19Test` (5); `CustomerStageK34ClosedCardTest` (10); `CustomerStageK34LateDeliveryTest` (7); `CustomerStageK34RecordTest` (10); `CustomerStageK34RoutingTest` (5); `CustomerStageK34Test` (10); `DeliveryCountD3Test` (9); `DeliveryDeadlineK29Test` (4); `ReadinessE15Test` (5); `RestaurantWaitE2Test` (6); `StuckCustomerM22Test` (8); `TripStagesE8Test` (6); `VenueArrivalD9Test` (11); `VenueNearM18Test` (5); `WaitObservationProcessDeathTest` (5); `WoltSameCustomerR1Test` (5); `YolGoNavE8Test` (13); `courier_stages_e8_test` (13) — **296**
- **Veri:** `courier.db` `trips`/`session`, `orders.jsonl`, `economics-waits/legs.jsonl`
- **Bağımlı:** R1, R3, R16, Y4 (nav hedefi), R14
- **WEEM:** `:orders` `logic/` · 3 (A3.5)
- **Ayrıntı:** `ORDERS.md` 1 (veri modeli), 3 (durum makinesi), 4 (sipariş akışı; 4.8 varış kuralları, 4.9 navigasyon bağı), 13.2 (`TripStages`), 15.1 (test eşlemesi)

### R6 Restoran ve müşteri balonları
- **Eski kod:** RS `DeliveryReminder.kt`, `CustomerBubble.kt`, `CustomerCard.kt`, `RestaurantBubble.kt`, `NoteFields.kt`, `Dialer.kt`
- **Testler:** `BoltBubbleR2Test` (5); `CustomerBubbleF72Test` (7); `CustomerBubbleViewF73Test` (3); `CustomerCardE16Test` (6); `NoteFieldsF71Test` (6); `PhoneK25Test` (3); `RestaurantBubbleE14Test` (6) — **36**
- **Veri:** bildirim 12 `delivery_notes`; `customerCard*`, `restaurantBubbleWidthPct`
- **Bağımlı:** R5, R16, Y4
- **WEEM:** `:orders` · 3 (A3.7)
- **Ayrıntı:** `ORDERS.md` 5 (hatırlatıcı, pencereler, bildirim 12), 15.2 (test eşlemesi); gizlilik 14.2

### R7 Kurye uygulaması üstünde gösterge
- **Eski kod:** RS `OverlayText.kt`, `OrderTracker.badgeLines`, `WatchService` rozet penceresi
- **Testler:** `DualOfferStatusK30Test` (3); `OfferNetLineTest` (6); `OverlayBadgeK35Test` (4) — **13**
- **Veri:** `overlay`, `commission*`
- **Bağımlı:** R1, R5, R11
- **WEEM:** `:orders` + `:capture` · 3 (A3.7)
- **Ayrıntı:** `ORDERS.md` 6 (rozet metni ve pencere); pencere yönetimi `CAPTURE.md` 1.6, `CaptureHost` 7.1

### R8 Nakit ve bahşiş
- **Eski kod:** RS `CashTips.kt`, `CashTipPrompt.kt`, `TipAmountActivity.kt`, `res/layout/tip_quick.xml`
- **Testler:** `CashScreenM16Test` (7); `CashOrderK31K32Test` (16); `TipNotificationF46Test` (4); `TipRecordF48Test` (5) — **32**
- **Veri:** `economics-cash-tips.jsonl`; kanal `cash_tips` — bildirim numaraları **10000–14095** (eskiden 4000–8095; çakışma çözüldü, `CORE.md` 3.3)
- **Bağımlı:** R1, R5
- **WEEM:** `:orders` · 3 (A3.6)
- **Ayrıntı:** `ORDERS.md` 7 (7.1 nakit ekranı K31/M16, 7.2 bahşiş sorusu ve kaydı K32), 13.6 (bildirimler)

### R9 Cepteki nakit
- **Eski kod:** RS `PocketCash.kt`, `PocketCashPrompt.kt`, `CashPending.kt`
- **Testler:** `CashPendingM17Test` (5); `CashReconcileM17Test` (5); `PocketCashM15Test` (4) — **14**
- **Veri:** `economics-pocket-cash.jsonl`, `economics-cash-pending.jsonl`, `cashPending.*`; kanal `pocket_cash` 4901
- **Bağımlı:** R8, R13, G1
- **WEEM:** `:orders` · 3 (A3.6)
- **Ayrıntı:** `ORDERS.md` 8 (8.1 vardiya başı cep nakdi M15, 8.2 CASH PENDING ve yatırma M17)

### R10 Boost / Earn extra
- **Eski kod:** RS `BoostLog.kt`
- **Testler:** `BoostListM21Test` (4); `BoostLogK27Test` (7); `BoostTempE3Test` (5) — **16**
- **Veri:** `economics-boosts.jsonl`
- **Bağımlı:** R1, R3, R4
- **WEEM:** `:orders` · 3 (A3.6)
- **Ayrıntı:** `ORDERS.md` 9.1 (boost gözlemi), 9.2 (teklifin boost'u)

### R11 Çevrimiçi süreler
- **Eski kod:** RS `OnlineSessions.kt`
- **Testler:** **yok**
- **Veri:** `online-sessions.jsonl` (v2), `onlineSessions.v1`
- **Bağımlı:** R1, R7
- **WEEM:** `:orders` · 3 (A3.6)
- **Ayrıntı:** `ORDERS.md` 9.3 (oturumlar; eski testi yok, `OnlineSessionsTest` yeni: 15.3)

### R12 Siparişler ve gün sayfaları
- **Eski kod:** RS `MainActivity.kt` (gün sayfası), `RecordPages.kt`, `OrderHistory.kt`, `DailyRecords.kt`, `RecordJson.kt`
- **Testler:** `DailyRecordsTest` (8); `RecordJsonTest` (6); `ShiftDistanceD8Test` (4); `AverageListE1Test` (6); `DeliveryCountD3Test` (9); `OrderHistoryTest` (9); `RecordPagesTest` (2); `TodayAveragesTest` (2) — **46**
- **Veri:** `courier.db`, gün klasörü, arşiv
- **Bağımlı:** R5, R13, G1
- **WEEM:** `:orders` + `:app` · 3 (A3.8)
- **Ayrıntı:** `ORDERS.md` 10 (10.1 kayıt biçimi, 10.2 gün ve sipariş özetleri, 10.3 ekranlar)

### R13 Resmî değerlerle karşılaştırma
- **Eski kod:** RS `PlatformTotals.kt`, `PlatformHistory.kt`, `CourierParser.statsPeriod`
- **Testler:** `WoltStatsF81Test` (6); `PlatformHistoryK36Test` (10); `PlatformListD15Test` (5); `PlatformTotalsF82Test` (6); `PlatformTotalsF83Test` (5); `PlatformTotalsF84Test` (7); `PlatformTotalsF85Test` (3) — **42**
- **Veri:** `economics-platform-totals.jsonl`, `economics-platform-history.jsonl`
- **Bağımlı:** R1, R12, R9
- **WEEM:** `:orders` · 3 (A3.6)
- **Ayrıntı:** `ORDERS.md` 11 (11.1 platform kazanç listesi, 11.2 nakit tahsilat satırları, 11.3 resmî toplamlar, 11.4 karşılaştırma ve gün kartı)

### R14 Sipariş başına enerji
- **Eski kod:** RS `ScooterBridge.kt`, `ScooterLink.kt`, `ScooterSamples.kt`, `EnergySegments.kt`; NB `ScooterEnergy.java`
- **Testler:** `EnergySegmentsTest` (4); `ScooterOrderEnergyTest` (7); `ScooterBridgeTest` (6); `ScooterSamplesTest` (5); `EnergyTest` (21) — **43**
- **Veri:** `economics-energy.jsonl`, `economics-scooter.jsonl`
- **Bağımlı:** S1–S4, R5, N1 (R→P)
- **WEEM:** `:scooter` + `:economics` · 5 (A5.3)
- **Ayrıntı:** `SCOOTER.md` 8, 12.2 (blok satırı `:economics`: `bulgular/X_protocol.md` §P5)

### R15 Teklif anının havası
- **Eski kod:** RS `OfferWeather.kt`, `WeatherFeedReader.kt`
- **Testler:** `BoostTempE3Test` (5); `OfferWeatherK28Test` (2); `WeatherFeedTest` (2) — **9**
- **Veri:** teklif satırında `weather_*`; yük `weather/current`
- **Bağımlı:** Y10, R3
- **WEEM:** `:orders` · 3 (A3.6) — Y10 Aşama 6'da gelir
- **Ayrıntı:** `ORDERS.md` 12 (kaynak `WeatherPort`, 13.5); ham: `bulgular/X_protocol.md` §P9 (YolGo hava akışı; WEEM'de yok, S7)

### R16 Başka navigasyon uygulamasından mesafe
- **Eski kod:** RS `NavDistance.kt` (0.25.1 ICU hatası), `NotifService.kt:28-35`
- **Testler:** `ArrivalDistanceM20Test` (4) — **4**
- **Veri:** bellek (`navSummary`)
- **Bağımlı:** R2, R5
- **WEEM:** `:capture` `logic/` · 3 (A3.4)
- **Ayrıntı:** `CAPTURE.md` 4.2 (`NavDistance`); mesafenin varış kuralındaki kullanımı `ORDERS.md` 4.8 (`ArrivalDistance`)

### R17 Yedek ve geri yükleme
- **Eski kod:** RS `BackupFiles.kt`, `LogImporter.kt`, `LegacyRecords.kt`, `DailyPack.kt`, `MainActivity.renderRecordTools`
- **Testler:** `DailyPackTest` (2); `DailyRecordsTest` (8); `ExportKeysParityTest` (1); `RecordJsonTest` (6) — **17**
- **Veri:** ZIP `YYYY-MM-DD/<dosya>` + `restaurants.json`; `dailyPack`
- **Bağımlı:** R4–R18 dosyaları, N3
- **WEEM:** `:core` · 4 (A4.3)
- **Ayrıntı:** `bulgular/R_economics.md` §R17

### R18 Anonim analiz dışa aktarımı
- **Eski kod:** RS `AnalysisExport.kt`, `Redactor.kt`, `docs/ekonomi-v2/redact.py`, `tools/analysis/`
- **Testler:** `AnalysisExportTest` (13); `DailyPackTest` (2); `ExportKeysParityTest` (1); `RedactorParityTest` (4); `LogFeedV3Test` (10) — **30**
- **Veri:** `ANALIZ_ANONIM.json`, gün sırları `economics-day-secrets-v3/`
- **Bağımlı:** R17
- **WEEM:** `:core` · 4 (A4.3)

### R19 Uyarılar
- **Eski kod:** RS `ScreenReading.kt`, `AutoStartNotice.kt`, `GpsService.kt:117-155`
- **Testler:** `ScreenReadingE7Test` (4); `AutoStartNoticeTest` (1) — **5**
- **Veri:** kanallar `screen_reading` (bildirim **5104**, eskiden 5103; çakışma çözüldü), `auto_start` (5101 ev, 5103 vardiya kesildi: A2.4; 5102 scooter: A5)
- **Bağımlı:** R1, G1, S1
- **WEEM:** `:capture` (E7 kuralı ve uyarı dinleyicisi) + `:app` (izinler kartı, vardiya kartı satırı) + `:core` (5101/5103 bildirimleri, A2.4) · 3 (A3.4 kural; A3.7 Android)
- **Ayrıntı:** `CAPTURE.md` 5 (E7 ekran okuma uyarısı); `ORDERS.md` 13.7 (izinler kartı, pil bilgi satırı, otomatik başlatma bildirimleri)

### R20 Çökme kaydı
- **Eski kod:** RS `RawstatsApp.kt`, `CrashLog.kt`, `CrashReport.kt`, `Store.failure`
- **Testler:** `CrashGuardTest` (10); `CrashReportTest` (7) — **17**
- **Veri:** `files/crashes.jsonl`, `errors.jsonl`, `crashSeenAt`
- **Bağımlı:** —
- **WEEM:** `:core` + `:app` · 2 (A2.3)
- **Ayrıntı:** `CORE.md` Bölüm 4 (şartname, A2.1); ham: `bulgular/R_capture.md` §R20

### S1 Scooter bağlantısı
- **Eski kod:** NB `NinebotCrypto`, `NinebotFrames`, `NinebotSession`, `NinebotLink`, `BleCapture`, `SessionKey`, `KeyVault`, `PairAttention`, `NinebotMode`, `Timing`; `tools/reference/nb_crypto.py`
- **Testler:** `LinkTest` (23); `PairFlowTest` (26); `PowerCadenceTest` (23); `ProtocolTest` (33); `SpeedCadenceTest` (23); `TimingTest` (10) — **138**
- **Veri:** `pairing-v1`, Keystore `NinebotLogger.Pairing.v1`, `device`, `crypto_mode`
- **Bağımlı:** —
- **WEEM:** `:scooter` `logic/` + BLE · 5 (A5.2)
- **Ayrıntı:** `SCOOTER.md` 2 (GATT, çerçeve, şifreleme, el sıkışma, oturum sabitleri), 3 (registerlar), 4 (`pairing-v1`/Keystore)

### S2 Canlı panel
- **Eski kod:** NB `NinebotMetrics`, `NinebotDashboard`, `DashboardView`, `DashboardActivity`, `Ui`, `RawPalette`
- **Testler:** `ScooterFeedTest` (5); `DashboardTest` (15); `PowerCadenceTest` (23); `SpeedCadenceTest` (23); `scooter_language_section_test` (3); `v170_features_test` (13) — **82**
- **Veri:** yük `scooter/v1/live`
- **Bağımlı:** S1
- **WEEM:** `:scooter` · 5 (A5.3)
- **Ayrıntı:** `SCOOTER.md` 6.1–6.2, 11.5 (yük: `bulgular/X_protocol.md` §P4)

### S3 Sürüş kaydı
- **Eski kod:** NB `RecordingService`, `LogStore`, `LogExport`, `NinebotEventQueue`, `NinebotLogPolicy`, `NinebotGpsPolicy`
- **Testler:** `ScooterSamplesTest` (5); `EventQueueTest` (7); `LogPolicyTest` (14) — **26**
- **Veri:** `ninebot-logger.db`, dışa aktarma ZIP (`manifest.json` schema 1)
- **Bağımlı:** S1
- **WEEM:** `:scooter` · 5 (A5.3)
- **Ayrıntı:** `SCOOTER.md` 7

### S4 Yolculuk sayacı
- **Eski kod:** NB `TripMeter`, `ScooterEnergy`
- **Testler:** `EnergyTest` (21); `TripMeterTest` (20) — **41**
- **Veri:** sayaç (yük `trip`)
- **Bağımlı:** S2
- **WEEM:** `:scooter` `logic/` · 5 (A5.3)
- **Ayrıntı:** `SCOOTER.md` 8.1–8.3

### S5 Harici batarya
- **Eski kod:** NB `PackPresence`, `BleCapture.batteryFound` (BMS kartı arama)
- **Testler:** `BatteryProbeTest` (64) — **64**
- **Veri:** `external_bms_boards`, `battery_probe`
- **Bağımlı:** S1
- **WEEM:** `:scooter` · 5 (A5.3) — enerji ölçümü açık sorun
- **Ayrıntı:** `SCOOTER.md` 9

### S6 Yeniden bağlanma
- **Eski kod:** NB `NinebotLink`/`RecordingService` (yeniden deneme), RS `ScooterReconnect.kt` (M23)
- **Testler:** `ScooterReconnectM23Test` (4); `TimingTest` (10) — **14**
- **Veri:** kanal `scooter_link` 4902; olaylar `scooter_data_back/missing`
- **Bağımlı:** S1
- **WEEM:** `:scooter` · 5 (A5.2)
- **Ayrıntı:** `SCOOTER.md` 5

### S7 Yüzen panel
- **Eski kod:** NB `FloatingPanel`, `FloatPainter`, `FloatGeometry`, `PanelField`
- **Testler:** `FloatingTest` (23) — **23**
- **Veri:** `floating`, `float_*`
- **Bağımlı:** S2
- **WEEM:** `:scooter` + `:app` · 5 (A5.4)
- **Ayrıntı:** `SCOOTER.md` 6.3

### S8 Tanı araçları
- **Eski kod:** NB `BleCapture` ayrıntılı kayıt, sweep, `InfoSheet`; `tools/g3_readonly.py`
- **Testler:** `LogPolicyTest` (14); `SweepTest` (22) — **36**
- **Veri:** `sweep`, `detailed_diagnostics`, `export_full`
- **Bağımlı:** S1, S3
- **WEEM:** `:scooter` · 5 (A5.4)
- **Ayrıntı:** `SCOOTER.md` 10

### G1 Tek vardiya / gün
- **Eski kod:** RS `HomeShift.kt`, `HomeLocation.kt` (`HomeWatch`), `GpsService.kt`, `ShiftStateFeed.kt`, `EndedShiftMinutes.kt`; YG `lib/tracking/shift_engine.dart`, `shift_controller.dart`, `home_day.dart`, `rawstats_shift.dart`, `nav_only.dart`
- **Testler:** `EndedShiftMinutesTest` (5); `HomeEndRuleF45bTest` (7); `HomeShiftE6Test` (9); `HomeShiftK38Test` (11); `ShiftDistanceD8Test` (4); `ShiftInterruptH4Test` (3); `ShiftStateF41Test` (5); `AutoStartNoticeTest` (1); `home_day_controller_test` (5); `home_day_e6_test` (10); `home_day_end_rule_test` (5); `home_day_rules_test` (19); `native_telemetry_test` (1); `shift_engine_test` (9); `courier_stage_follow_test` (14); `nav_only_location_test` (2); `yolgo_sade_2_test` (10); `rawstats_shift_end_test` (11); `TelemetryJournalTest` (8) — **139**
- **Veri:** `shift*` (RS), `shift.v1`, `day.home.v1` (YG); kanal `shift` 1
- **Bağımlı:** G2, R5 (açık sipariş bitişi engeller), R9, Y11, Y17
- **WEEM:** `:core` `logic/` · 2 (A2.4) + 7
- **Ayrıntı:** `CORE.md` Bölüm 1 (şartname, A2.1: tek kural, YolGo farkları); YolGo gün motorunun vardiyaya bağlanması `MAP.md` 11.2, 11.14; ham: `bulgular/R_core_contract.md` §G1, Y_tracking

### G2 Ev konumu
- **Eski kod:** RS `HomeRepository.kt`, `HomePreferences.kt`, `HomeDraft.kt`, `HomeFixes.kt`, `HomeFixSampler.kt`; YG `lib/ui/home_picker.dart`, `lib/core/rawstats_home.dart`
- **Testler:** `HomeDraftTest` (5); `HomeFixRegressionTest` (2); `HomeFixesTest` (4); `HomeRepositoryTest` (7); `HomePickContractTest` (6); `home_picker_test` (5); `rawstats_home_stale_test` (6); `rawstats_home_sync_test` (13); `HomeShareTest` (3) — **51**
- **Veri:** `home.*` (RS), `settings.v1` ev (YG)
- **Bağımlı:** G1, Y1 (haritadan seçme)
- **WEEM:** `:core` + `:map` · 2 (A2.4) + 7
- **Ayrıntı:** `CORE.md` Bölüm 2 (şartname, A2.1); ham: `bulgular/R_core_contract.md` §G2, Y_tracking

### G3 Dil
- **Eski kod:** RS `I18n.kt` (`I18n.tl`); YG `lib/core/i18n.dart`
- **Testler:** `I18nCoverageTest` (2); `scooter_language_section_test` (3) — **5**
- **Veri:** `lang`
- **Bağımlı:** —
- **WEEM:** `:app` · 1 (A1.3)
- **Ayrıntı:** `bulgular/Y_tracking.md` §G3

### G4 Az bildirim
- **Eski kod:** YG `android_overlay/NotifyPolicy.kt`; RS bildirim kuralları (zaman aşımları, tek bildirim)
- **Testler:** `reminder_card_test` (3); `NotifyPolicyTest` (2) — **5**
- **Veri:** bildirim tablosu (veri sözleşmesi §8)
- **Bağımlı:** R19, Y20
- **WEEM:** `:core` · 2 (A2.4)
- **Ayrıntı:** `CORE.md` Bölüm 3 (şartname, A2.1: `NotificationIds` tablosu); Aşama 6 numaraları ve kanalları `MAP.md` 14.6; ham: `bulgular/Y_tracking.md` §G4

### G5 İlk açılış kurulumu
- **Eski kod:** YG `lib/ui/setup_screen.dart`, `settings_screen.dart`, `maps_help.dart`; RS `MainActivity` izin akışı, `Readiness`
- **Testler:** `BrandingK26Test` (3) — **3**
- **Veri:** izin durumu, harita verisi
- **Bağımlı:** G3, R19, Y7
- **WEEM:** `:app` · 7 (A7.2)
- **A7.2 (20 Ekim):** saf `logic/app` `Setup.kt` (`SetupStep` dil → izinler → harita verisi → pil; `Permissions`: RS sırası ve "! n/m" sayacı, eksikte açık, pil satırı yalnız bilgi; `Vendor`/`BatteryGuide` Xiaomi·Redmi·POCO / Pixel / diğer; `SetupText` TR/AZ), `:app` `SetupScreen` (ilk açılışta bir kez, `weem.setupDone`; atlanabilir), `PermissionsCard`/`BatteryGuideCard` (Daha fazla → İzinler; "Kurulumu yeniden göster"), harita adımı `MapDataStep` (Harita sekmesinin kurulum kartı). `BrandingK26Test` uyarlandı (2; üçüncüsü çevrilmez: WEEM'de alt menü var). G3: scooter durum metinleri ekranda AZ (`ScooterStatusText`); G4: `NotificationAuditTest`. Cihazda denenmedi; yeni AZ metinleri onay bekler (M-G5-1).

### N1 2 telefon modu
- **Eski kod:** yeni; temeli RS↔YG protokolü (`FeedChanges`, feed v2, `stages`, nav v1, hava, scooter canlı) ve `ScooterBridge`
- **Testler:** `ShiftStateF41Test` (5); `FeedChangesE8Test` (8); `OfferFeedV2Test` (6); `TripStagesE8Test` (6); `YolGoNavE8Test` (13); `ScooterBridgeTest` (6); `ScooterFeedTest` (5); `courier_stage_follow_test` (14); `courier_stages_e8_test` (13); `nav_feed_test` (11); `v170_features_test` (13); `offer_feed_versions_test` (6); `rawstats_signals_test` (10); `NavFeedContractTest` (6); `CourierFeedPayloadTest` (6); `RawstatsObserverTest` (5) — **133**
- **Veri:** plan Bölüm 4.4 mesajları
- **Bağımlı:** R3, R5, R14, Y4, Y5, Y10, G1, S2
- **WEEM:** `:link` · 8
- **Ayrıntı:** `bulgular/X_protocol.md`

### N2 Uzaktan kullanım
- **Eski kod:** yeni
- **Testler:** **yok**
- **Veri:** —
- **Bağımlı:** N1 (eşleştirme)
- **WEEM:** `:remote` · 9

### N3 Otomatik şifreli yedek
- **Eski kod:** yeni; temeli R17 (`BackupFiles`, ZIP)
- **Testler:** **yok**
- **Veri:** haftalık `.weem` (S22; biçim `VERI_SOZLESMESI.md` 7.1 A4.3 notu), tercih dosyası `weem-backup` + Keystore `WEEM.Backup.v1`
- **Bağımlı:** R17, N1 (öteki telefona)
- **WEEM:** `:core` · 4 (A4.3)
- **Ayrıntı:** `bulgular/R_economics.md` §R17

### N4 Gün sonu raporu ve net kazanç
- **Eski kod:** yeni; temeli `OrderHistory` (brüt/net), `OfferNetLineTest`, `PlatformTotals`, `EnergySegments`
- **Testler:** `OfferNetLineTest` (6) — **6**
- **Veri:** gün klasörü
- **Bağımlı:** R12, R13, R14, R8
- **WEEM:** `:economics` · 4 (A4.2) — **bitti** (18 Ekim): `DayReport`/`DayReportCard`/`DaySummaries`, Siparişler kartı + vardiya sonu bildirimi; ayrıntı `ECONOMICS.md` 6.1

### N5 Menzil uyarısı
- **Eski kod:** yeni; temeli YG `battery_estimator.dart`, `remaining_km_e12`, `lib/economics/offer_engine.dart`, `route_costing.dart`
- **Testler:** `offer_engine_test` (7); `route_costing_test` (6) — **13**
- **Veri:** teklif özeti + canlı batarya
- **Bağımlı:** Y9, S2, R4, G2
- **WEEM:** `:economics` + `:map` (`RangePort`) · 6 (A6.7)
- **Ayrıntı:** `MAP.md` 10.6 (öneri, SORU-T1-1), 10.14; eski testler çevrilmez (`offer_engine_test`, `planLeavesBox`; SORU-T1-2) ya da Y13 ile sonra (`route_costing_test` 5); N5 testleri ekonomi v2 girdileriyle yeni yazılır

### N6 Hırsızlık koruması
- **Eski kod:** yeni
- **Testler:** **yok**
- **Veri:** —
- **Bağımlı:** N1, S1
- **WEEM:** `:link` + `:scooter` · 8 (A8.4)

### N8 Günlük kazanç hedefi
- **Eski kod:** yeni
- **Testler:** **yok**
- **Veri:** yeni tercih
- **Bağımlı:** N4
- **WEEM:** `:economics` · 4 (A4.2) — **bitti** (18 Ekim): `NetSettings.DAILY_GOAL`, rapor satırı; `DayReportTest` 10

## Sonra (3) ve çıkarılan (1) — WEEM 1.0'da yazılmaz

| Kod | Özellik | Eski kod | Test (adet) |
|---|---|---|---:|
| Y13 | Yüzey ve titreşim ölçümü | YG `lib/surface/`, `android_overlay/SurfaceSignal.kt`, `RawDiagnostics.kt` | 44 |
| Y14 | Yol kalibrasyonu | YG `lib/calibration/`, `android_overlay/RoadCalibration*.kt` | 56 |
| N7 | Sesli uyarılar | yeni | 0 |
| N9 | Bakım hatırlatıcı (çıkarıldı) | — | 0 |

Y12 17 Ekim'de (S3) WEEM 1.0'a alındı; yukarıdaki ayrıntıya bakın.

## Kapsam listesinde adı olmayan eski davranışlar

63 listesinde ayrı satırı yok; aşağıdaki özelliğe bağlanarak korunur (S9, 17 Ekim: hepsi kalır, yalnız Google Maps'te rota açma kaldırılır):

| Davranış | Eski kod | Bağlandığı özellik |
|---|---|---|
| Eve dönüş batarya eşikleri (`homeReturnPct` 30, `lowBatteryPct` 50, `lowBatteryGradePct`) | YG `settings.v1`, `battery_estimator.dart` | Y9, N5 |
| Kaldırım bölgeleri ve "kaldırımda bekliyorum" | YG `sidewalk.zones.v1`, `stop_semantics.dart` | Y11 |
| Hareket tanıma (yürüme güveni) | YG `MotionReceiver.kt` | G1, Y11 |
| ~~Google Maps'te iki tekerli rota açma (yedek)~~ | YG `MainActivity.kt` `openGoogleRoute` | **kaldırıldı (S9)**; görev kartındaki "Rota Oluştur" WEEM navigasyonunu açar (Y20) |
| Sürüşte ekranı açık tutma; ses tuşu yakalama (Ses+/Ses−) | YG `setScreenAwake`, `setVolumeCapture`; NB `keep_screen_on` | Y1, S2; ses tuşu Y12 ile (A6.11) |
| Gün silme | YG `day_deletion.dart`; RS `DailyRecords` | R12, Y17 |
| İzlenen uygulama seçimi (başlatılabilir uygulama listesi) | RS manifest `<queries>`, `MainActivity` | R2 |
| Müşteri adresinin Android Geocoder ile koordinata çevrilmesi (<50 km) | RS `OrderTracker.kt` (`resolve`) | R5 (`:orders`; `:capture` ağsız kalır) |
| Komisyon ayarı ve net satırı | RS `commission`, `OverlayText.net` | R7, N4 |
| Ekonomi araç/bölge ayarı | RS `economics.vehicle`, `economics.zone` | R4 |
| Canlı panelin otomatik açılması, tam ekran | NB `auto_live`, `immersive` | S2 |
| Teklif veri setine elle not | YG `dataset_manual.dart`, `DatasetManual.kt` | Y19 |
