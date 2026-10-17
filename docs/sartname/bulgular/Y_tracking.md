# YolGo — vardiya/gün/ev, takip, batarya, hava, ışıklar, istatistik, kayıtlar, veri seti, görevler, bölge, ayarlar/kurulum bulguları
Kaynak: `yolgo-test` @ `870ab6e`; kök `/home/user/kurye_nav_yolgo` (Dart paket adı `kurye_nav`, Android paket `az.kurye.kurye_nav` Kotlin; uygulama kimliği `az.duzyol.app`)

(Durum: YAZILIYOR — bölümler parça parça ekleniyor.)

## 1. Özellikler

### G1 Tek vardiya / gün (YolGo tarafı)
- Eski kod: `lib/tracking/shift_engine.dart:13-370` (`ShiftEngine`, saf), `:372-680` (`Fix`, `RoadInfo`, `StopRec`, `OrderRec`, `ShiftState`); `lib/tracking/shift_controller.dart:64-725` (`ShiftController`, Riverpod); `lib/tracking/home_day.dart:24-163` (`HomeDayRules`, saf); `lib/tracking/rawstats_shift.dart:1-64` (`RawstatsShift`, `decideRawstatsShift`, saf); `lib/core/day_scope.dart:1-9` (`bakuTime`, `bakuDay`, `passiveSessionExpired`); `lib/tracking/day_deletion.dart:17-70` (`DayDeletion`, `confirmDeleteDay`); `lib/tracking/home_departure.dart:8-34` (`HomeDepartureDetector` — **ölü kod**: lib/ içinde kullanılmıyor, test dosyası da yok).
- Davranış (gün):
  - Gün kimliği `dayId = 'yolgo-<startedAt ms>'` (`shift_engine.dart:603`). Gün gece yarısı bölünmez; takvim günü Bakü saati UTC+4 sabit (`day_scope.dart:1-7`, telefon saat diliminden bağımsız).
  - `startDay(now, reason)`: gün açıksa no-op; `reason` ∈ `manual` (eski kayıt/test) · `home_departure` · `rawstats_shift_start` (`shift_engine.dart:42-53`, `shift_controller.dart:191-204`). Controller `at` geleceğe düşerse şimdi kullanır; başlayınca `HomeDayRules.dayStarted()`, `CourierStageFollower.dayChanged()`, gün başında `rawstatsHomeProvider.refreshIfStale()` (H5) çağrılır.
  - `endDay(now, road, reason)`: açık duruş kapatılır, süren + sıradaki siparişler `cancelled=true` ile `orders`'a eklenir; `reason` ∈ `manual` · `home_arrival` · `rawstats_shift_end` (`shift_engine.dart:55-74`). Controller `at`'i [gün başı, şimdi] aralığına sıkıştırır (`shift_controller.dart:208-231`); eve dönüş ölçümü kapanır, `LightTracker.finish` ölçümü eklenir, telemetri `configure({'day': null,'eligible': false})`, `shift.last`'e yazılır, `shift.v1` silinir, gün raporu yazılır.
  - Eşzamanlı `endDay` tek `Future`'da birleşir (`_ending ??=`, `shift_controller.dart:206-207`); `_ending` sürerken `startDay` no-op (`:192`).
  - Kalıcılık: aktif gün `shift.v1` (JSON `ShiftState.toJson`, `v: 2`); olay varsa ya da 15 sn'de bir kaydedilir (`shift_controller.dart:658-681`). 5 sn'lik tık gün açıkken: yeniden çiz + KuryePlus feed oku + 60 sn'de bir Rawstats vardiya oku (`:177-189`).
  - Durma/km (aynı motor; Y11/Y17 ile ortak): `feed()` fix filtresi: doğruluk 0–50 m, koordinat geçerli, zaman artan (`shift_engine.dart:126-131`). Kilometre (`_odometer`, `:161-196`): doğruluk >35 m fix atlanır; `movingS` += dt yalnız >5 km/s ve dt ≤15 sn; atlama: dt >30 sn ya da >16 m/s → düz çizgi sayılmaz; <2,5 km/s → çapa taşınır; adım ≥3 m ve ≥0,35×doğruluk ise eklenir.
  - Durma: ≤5 km/s ile başlar, ≥10 sn sürerse durma, 30 m içinde kalmalı; çıkış: art arda 2 fix >10 km/s ya da 30 m dışı ve doğruluk ≤25 (`:18-21,:137-157`). Tür sırası: sipariş aşamasındaysa `order` → AVM `mall` → `atLight` ve süre ≤360 sn ise `light` (>120 sn `light?` + `long`) → kaldırım `sidewalk` → `stop` (`:198-237`).
  - Sipariş aşamaları yalnız Rawstats feed'inden (Sadeleştirme 2): `startOrder/stageFromFeed/advanceFromFeed/deliverFromFeed/cancelOrder`; yalnız ileri, görülmeyen aşamaya saat uydurulmaz (`:78-370`). Olaylar `source: 'kuryeplus'`.
  - Gün silme (`day_deletion.dart:22-54`): onay diyaloğu → seçilen tarih aktif günse önce `endDay()` + telemetri kapat + rota sil; raporlardan `dayId` toplanır (eski raporda `'yolgo-<start>'` türetilir); yüzey/yol katkıları silinir; `shift.deleted.days.v2` listesine eklenir; aktif günü silme `StateError` (`shift_controller.dart:164-176`).
  - `passiveSessionExpired`: gün kapalı ve `passive.pausedAt.v2`'den ≥5 dk geçtiyse pasif oturum biter (`day_scope.dart:8-9`, kullanım `lib/main.dart:69,97`).
- Davranış (ev kuralı `HomeDayRules`, F4.5c, varsayılanlar `home_day.dart:26-30`): `radiusM=50`, `exitZoneM=200`, `startRadiusM=50`, `startKmh=10`, `startAccuracyM=20`, `endKmh=5`, `needFixes=2`, `dwell=30 sn`, `spreadM=10`, `maxAccuracyM=35`, `maxJumpMps=16`, `maxGap=30 sn`, `speedWindow=10 sn`, `minBaseline=2 sn`.
  - Fix filtresi: doğruluk ≤35, koordinat geçerli, zaman artan (`:57-66`). Ev değişirse `_seenInside`, `_armedDay` sıfırlanır (`:67-70`).
  - Sıçrama: (mesafe − ortalama doğruluk) / dt > 16 m/s → pencere temizlenir (`:74-81`). Hız: GPS hızı >0 ise o, yoksa ≥2 sn tabanlı pencere hızı (mesafe ≥ max(3, ort. doğruluk) değilse 0) (`:84-93`).
  - Başlangıç (gün kapalı): evden ≥50 m, doğruluk ≤20, **yalnız GPS'in kendi hızı** ≥10 km/s, art arda 2 bağlı fix; koşulu bozan fix sayacı sıfırlar (`:99-116`).
  - Elle bitirme sonrası `waitExit`: önce 200 m içinde görülmeli, sonra 200 m dışına çıkılmalı; o zamana kadar otomatik başlamaz (`:101-108,:145-147`). Otomatik bitişte (`manual=false`) bekleme yok.
  - Bitiş (gün açık): gün içinde `d − doğruluk > 50 m` görülünce `armedDay=dayId` kurulur (`:119-124`); 50 m içinde, sipariş yok (`busy` = süren ya da sıradaki sipariş), her fix <5 km/s, ilk fix'ten (çapa) 10 m içinde 30 sn → `end` (`:125-135`). Hız bilinmiyorsa (null) bitişi engellemez.
  - Kalıcı durum JSON (koordinat YOK): `{'v':1,'waitExit','seenInside','armedDay'?}` → `day.home.v1` (`:149-162`, `shift_controller.dart:536-543`).
  - Controller: fix yaşı −5…+30 sn dışında ev kuralı çalışmaz (`shift_controller.dart:417-449`); `autoStartDay/autoEndDay` her zaman true (1.10.0, `settings.dart` fromJson ve `update`). Toast: "Evden çıkıldı — gün otomatik başladı" / "Eve döndün — gün otomatik bitti".
  - "Eve dön" ölçümü: `startHomeReturn` (gün açık, sipariş yok) → `homeReturnAt`, `homeStartM`; ev 35 m içinde, doğruluk ≤20, hız 0–1,3 m/s → `home_return_arrived`; yeni sipariş açılınca da biter (`shift_controller.dart:246-267,:364-369,:478`).
- Davranış (Rawstats vardiyası eşitleme, F4.3/F4.5, `rawstats_shift.dart`):
  - Kaynak: Rawstats sağlayıcısı `/shift/v1/state` (okuma `OfferFeedClient.readShiftRaw`, `shift_controller.dart:58`). Şema: `schemaVersion==1`, `active` bool, `startedAtMs`, `endedAtMs` (int >0), `endReason` (String), `interrupted` (bool, H4). Bozuk/başka şema → null → hiçbir şey (`:15-26`).
  - Karar (`:44-64`): gün açık + Rawstats kapalı + `endedAt` işlenmemiş ve gün başından önce değil → `endDay(at: endedAt)` (`rawstats_shift_end`). Gün açık + Rawstats açık → başlangıç yalnız "işlendi" yazılır (elle bitirilen gün aynı vardiyada yeniden açılmaz). Gün kapalı + Rawstats açık + başlangıç işlenmemiş → `startDay(at: startedAt)`.
  - İşlenmiş damgalar: `rawstats.shiftEnd.handled.v1`, `rawstats.shiftStart.handled.v1` (int ms) (`shift_controller.dart:70-71,:390-394`).
  - Tetikler: soğuk açılış, gerçek resume (`AppLifecycleListener`), Rawstats `shift` duyurusu (`rawstatsSignalsProvider.shift`), gün açıkken 60 sn yedek (`rawstatsShiftFallback`) (`:118-123,:177-189`).
  - YolGo ev kuralı Rawstats okunabilirken de çalışır: iki taraf ayrı ayrı bitirebilir (bkz. Bölüm 5).
- Testler: `shift_engine_test` (9), `home_day_rules_test` (19), `home_day_e6_test` (10), `home_day_end_rule_test` (5), `home_day_controller_test` (5), `rawstats_shift_end_test` (11), `courier_stage_follow_test` (14), `courier_stages_e8_test` (13), `yolgo_sade_2_test` (10, kısmen) — ayrıntı Bölüm 3.
- Veri: SharedPreferences (Flutter: dosya `FlutterSharedPreferences`, anahtar öneki `flutter.`) `shift.v1`, `shift.last`, `day.home.v1`, `courier.follow.v1`, `rawstats.shiftEnd.handled.v1`, `rawstats.shiftStart.handled.v1`, `shift.deleted.days.v2`, `passive.pausedAt.v2`; dosya `<appDocs>/logs/<YYYY-MM-DD>_<HHmm>.gun.json` (Bölüm 2).
- Bağımlı: G2 (ev koordinatı), Y11 (durma motoru), Y17/Y18 (gün raporu, kayıt), R5/Rawstats vardiyası (feed v2 + `/shift/v1/state`), Y20 (görevler `activeDay/activeOrder/deliveryFinished`).
- WEEM: `:core` — `ShiftEngine`, `HomeDayRules`, `decideRawstatsShift`, `bakuDay` saf `logic/`; kalıcılık/zamanlayıcı Android. Aşama 2 (tek kural) + 7 (YolGo içe aktarma). WEEM'de iki uygulama tek olduğundan `rawstats_shift` eşitlemesi gereksizleşir; tek vardiya kuralı Rawstats F4.5 ile birleşir.
- Not: `settings.dart` `autoEndDay` belge yorumu eski ("<10 km/s, 2 dk 10 m") — kod F4.5c (50 m, <5 km/s, 30 sn). F4.5b (ev bitişi GPS kayması) YolGo'da ayrı düzeltme YOK: tek karşılığı `d − accuracy > radiusM` kurma koşulu ve 10 m `spreadM` çapası. `HomeDepartureDetector` (150/200 m, 10 km/s) ölü kod.

### Y11 Trafik ışığı duruşları
- Eski kod: `ShiftEngine._endStop` (`shift_engine.dart:198-237`), `RoadInfo.atLight` (`:383-421`), `StopRec` (`:432-504`), `lightReviewTeaches` (`:506-507`); `LightTracker` (`lib/tracking/light_tracker.dart:8-88`, motordan bağımsız canlı ölçer); `stop_semantics.dart` (`classifySurface`, `isRoadside`, `mallRadiusM`); `ShiftController._roadInfo` (`shift_controller.dart:600-656`), `reviewLight` (`:269-293`), `markLight` (`:153-160`), `markSidewalk` (`:235-245`), `_reconcileStops` (`:545-568`), `LightsDb` (`:727-814`).
- Davranış:
  - Işık kuralı (madde 14, F9.2): `atLight = !mall && !manualSidewalk && known==true && (lightM != null ? lightM <= maxLightM && (onRoad || footM <= 1) : surface != sidewalk && !roadside)` (`shift_engine.dart:418-420`). `maxLightM` = ayar `lightMaxM` (varsayılan 5 m).
  - `known`: OSM ışık POI'si (`pois.nearest('l', maxM: 55)`) ya da öğrenilmiş `LightsDb` (yarıçap 25 m; `nearest` 55 m) (`shift_controller.dart:603-619`). `onRoad`: sürülebilir yol kenarına ≤15 m ve yüzey kaldırım değil (`:654`). `footM`: en yakın yaya yolu (35 m içinde). AVM: `pois.nearest('m', maxM: 80)` (`mallRadiusM = 80`, `stop_semantics.dart:5`). Elle kaldırım: işaretli noktaya ≤12 m.
  - Yüzey (`classifySurface`): elle → sidewalk; doğruluk >12 → unknown; yaya ≤8 m ve yoldan 3 m yakın → sidewalk; yol ≤6 m, doğruluk ≤8, yayadan 3 m yakın → road (`stop_semantics.dart:8-18`). Yol kenarı: doğruluk ≤8 ve yol çizgisine > yarım genişlik (trunk 9, primary 7, secondary 6, tertiary 5, diğer 4 m) (`:21-34`).
  - Motor: ≥10 sn durma, ışıkta süre ≤ `lightMaxS*2` = 360 sn; >`lightLongS`=120 sn → `light?` + `longWait` (`shift_engine.dart:22-24,:212-215`).
  - Canlı ölçer: başlangıç hız 0–1,1 m/s; yol bilgisi 5 sn'de bir yenilenir; hareket = hız ≥1,6 m/s ya da başlangıçtan > max(35, 2×doğruluk) m, art arda 2 fix; bitiş = ilk hareket fix'i; >12 sn fix boşluğu sıfırlar; doğruluk >35 fix yok sayılır; sipariş duruşunda ölçmez. Kayıt: süre ≤360 ve (bilinen ışık ≥3 sn ya da bilinmeyen aday — `known==null`, `allowsTrafficLight`, kavşağa ≤45 m — ≥10 sn); tür `light` ya da `light?`, `measured: true` (`light_tracker.dart:26-87`). Ekranda "ışıkta" ≥3 sn sonra (`since`).
  - Çift kayıt önleme: ölçer kaydı ile motor duruşu ±3 sn ve ≤40 m örtüşürse motorunki atılır; `order` duruşunda ölçer kaydı atılır (`shift_controller.dart:545-568`).
  - Gün sonu (Onayla/Reddet): `lightCandidates` = `kind=='light?' && answer==null`; cevap `answer` = `light|not`; öğrenme `lightReviewTeaches`: uzun duruşta Reddet hiçbir şey öğretmez, diğerleri cevabı öğretir → `LightsDb.record` + `setManual`; olay `light_answer` `{la, lo (5 hane), light, dur, jm?, long?}`; `shift.last` ve rapor yeniden yazılır (`shift_controller.dart:269-293`).
  - `LightsDb` (`lights.v1`, StringList, öğe `"lat,lon,yes,no,waitS"` 6 hane): `lookup` yes>no → true, yes<no → false, eşit → null; `setManual` 25 m içindeki eskisini değiştirir (1/0 ya da 0/1) (`:727-814`). Haritadan ışık ekle/gizle `markLight`.
  - Toast "Trafik ışığında m:ss bekledin" (`:713-719`).
- Testler: `light_rule_m14_test` (14), `light_leaks_f52_test` (3, karakterizasyon), `light_tracker_test` (4), `shift_engine_test` (kısmen). `lightMaxM` taşıma testleri `light_rule_m14_test` F9.1 grubunda (3).
- Veri: `lights.v1`, `sidewalk.zones.v1` (JSON liste `GeoPoint.toJson`), ayar `lightMaxM` (+ işaretler `lightMaxMv2`, `lightMaxMv3`); olaylar `shift_stop`, `light_answer`, `stop_surface_override`.
- Bağımlı: Y7 harita verisi (POI `l`/`m`, yol grafiği, yaya sınıfları), G1 motor, Y17 (ışık sayıları), Y20 değil.
- WEEM: `:map` (kural `logic/` saf: `atLight`, `classifySurface`, `LightTracker`, `lightReviewTeaches`; `LightsDb` depolama `:core`). Aşama 6.
- Not: **≤1 m yaya yolu koşulu** (plan Bölüm 5 "ışık kuralı ≤1 m" bilinen eski hata): GPS doğruluğu (≥3–5 m) ile `footM <= 1` sahada neredeyse tutmaz; AI_HANDOFF F9 "telefonda denenmedi". `light_leaks_f52` (c) maddesi yok (yalnız a, b, d). Eski `lightMaxMv2` (5→25) taşıması kodda artık yok; yalnız işaret yazılıyor.

### Y9 Hız ve batarya tahmini
- Eski kod: `lib/routing/speed_options.dart:7-44` (`speedOption`, `speedOptions`, `arrivalClock`, `aeroShare`); `lib/core/physics.dart:5-126` (`VehicleProfile`, `RideParams`); `lib/tracking/battery_estimator.dart:13-220` (`BatteryState`, `EnergyOdometer`, `BatteryController`); UI `lib/ui/widgets.dart:150-289` (`metric-battery`, `speed-popup`, `SpeedSheet`).
- Davranış:
  - Hız seçenekleri: ayar `speedChoices` (3 değer, 5–45 km/s, varsayılan `[20, 25, 29]`); bozuksa varsayılan (`settings.dart:57,_speeds`). `SpeedSheet` −/+ 1 km/s, `clampD(5,45)`.
  - `speedOption(ride, p, kmh)`: v = kmh/3,6, ref = `flatKmh`/3,6 (28,5); düz Wh/m = `flatWhPerM × (0,4 + 0,6·(v/ref)²)`; her parça: eğim = Δh / max(d, 20); eğim >0,02 ise hız = min(v, `climbMps(eğim)` × clamp(v/ref, 0,5, 1,2)); t += d/hız; Wh += düz·d + (Δh>0 ? `climbWhPerM·climbSafety·Δh` : 0) (`speed_options.dart:16-32`). Liste tekilleştirilip sıralanır; `speeds` yoksa `[20, 25, round(flatKmh)]`.
  - `climbMps`: eğim ≤%2 düz hız; %2–8 doğrusal `flatKmh → climbKmh`; >%8 `max(10, climbKmh·0,08/eğim)` km/s (`physics.dart:83-94`).
  - Varış saati `now + süre` "HH:mm" (`speed_options.dart:41-44`). Sheet'te batarya `%` = Wh / `capacityWh` × 100.
  - Batarya durumu: canlı Rawstats iç % varsa o; yoksa `setPercent − consumedWh/capacityWh×100` (0–100) (`battery_estimator.dart:33-38`). Canlı değer gelince çapa olur (`consumedWh=0`).
  - `EnergyOdometer`: doğruluk ≤25; dt >30 sn ya da >16 m/s → atla; <2,5 km/s → çapa taşı; adım ≥3 m ve ≥0,35×doğruluk; 30 m pencere: Wh = `flatWhPerM·mesafe + climbWhPerM·tırmanış` (tırmanış yalnız aynı kaynak — `baro` ya da `dem` — ve artış) (`:57-126`). Canlıyken tahmin yürümez.
  - Kayıt: 30 sn'de bir (`_save`), elle girişte hemen; olay `battery_set {pct, estimated?, prevSet?, usedWh?, sinceMin?, capacityWh}` (`:165-181`).
  - Kapasite: araç `g3` 598 Wh, `g3x` 1066 Wh (`physics.dart:13-16`). Eğim limiti: batarya < `lowBatteryPct` (50) → `lowBatteryGradePct` (10) else `maxGradePct` (18) (`settings.dart` `gradeLimitFor`).
  - UI: sol batarya kutusu yalnız rota varken; ≤ `lowBatteryPct` turuncu; harici batarya `+N%`; tahminse alt yazı "tahmin" (`widgets.dart:164-173`).
- Testler: `v140_features_test` (hız seçenekleri, kısmen), `v150_features_test` (özel hızlar, kısmen), `battery_estimator_test` (5).
- Veri: `battery.setPct` (double), `battery.consumed` (double Wh), `battery.setAt` (int ms); ayarlar `speedChoices`, `vehicleId`, `flatWhPerM`, `climbWhPerM`, `climbSafety`, `flatKmh`, `climbKmh`, `lowBatteryPct`, `lowBatteryGradePct`, `maxGradePct`.
- Bağımlı: Y3/Y4 (rota `RoutePoint.ele`), S1/S2 (canlı %), N5 (menzil), Y7 (DEM).
- WEEM: `:map` (`speedOption` saf `logic/`), `EnergyOdometer` saf `logic/` (`:economics`/`:scooter` ile paylaşılabilir). Aşama 6.

### G2 Ev konumu (YolGo tarafı)
- Eski kod: `lib/core/rawstats_home.dart:13-102` (`RawstatsHomeController`); `lib/input/clipboard_sniffer.dart:254-260` (`NativeBridge.rawstatsHome`, 20 sn zaman aşımı); `android_overlay/MainActivity.kt:224-235,:291-297` (`rawstatsHome` kanal yöntemi, `onActivityResult`); `android_overlay/HomeShareActivity.kt:14-47` (`HomeShareActivity`, `RawstatsHome`); `android_overlay/HomePickActivity.kt:9-56` + `HomePickContract.java:1-29` (Rawstats için haritadan ev seçici); `lib/ui/home_picker.dart:14-107` (`runHomePicker`, `HomePickApp`, giriş `pickHomeMain` `lib/main.dart:34`); `lib/ui/settings_screen.dart:195-230` (`RawstatsInfoSection` → "Ev konumu" + Yenile); `SettingsController.clearHome` (`settings.dart` sonu).
- Davranış:
  - **Ev yalnız Rawstats'ta seçilir** (1.7.0+): YolGo'nun `settings.home` alanı Rawstats'tan son başarılı okumanın önbelleğidir; başarısız okuma (`none`/`busy`/`missing`/istisna) önbelleği silmez (`rawstats_home.dart:11-12,:80-101`). Aynı ev tekrar yazılmaz.
  - Okuma: `startActivityForResult(Intent("com.kuryelog.app.action.SHARE_HOME").setPackage("com.kuryelog.app"), 703)`; cevap `lat/lon` Double extras, geçerli aralık dışı → `"none"`; aktivite yok → `"missing"`; okuma sürerken ikinci çağrı `"busy"`; Dart 20 sn zaman aşımı → `'missing'` (H8) (`HomeShareActivity.kt:38-47`, `MainActivity.kt:226-235`).
  - Ne zaman okunur: soğuk açılış (koşulsuz), elle "Yenile" (koşulsuz), resume: oturumda son denemeden ve son başarıdan ≥6 saat geçmiş, gün kapalı ve rota yok (`_resumeInterval = 6 sa`, `:47-56`); kendi paylaşım dönüşündeki resume sayılmaz (döngü önleme, `:33-45`). H5 `refreshIfStale`: gün başında ve "Eve dön"de, son başarı ve son deneme ≥1 saat ise, yalnız uygulama öndeyken (`staleAfter = 1 sa`, `:58-74`). Son başarı damgası `rawstats.home.lastSuccessAt.v1` (int ms).
  - Ters yön (Rawstats YolGo'nun evini ister): `HomeShareActivity` (exported, action `az.duzyol.app.action.SHARE_HOME`, ekransız) yalnız `callingPackage == "com.kuryelog.app"` ise `FlutterSharedPreferences` → `flutter.settings.v1` JSON'unun `home` alanını `lat/lon` ile döner; yoksa `RESULT_CANCELED` (`HomeShareActivity.kt:14-35`).
  - Haritadan seçme (Rawstats için): `HomePickActivity` (exported, action `com.myapplab.yolgo.PICK_HOME`) yalnız arayan `com.kuryelog.app` ve action eşleşirse açılır; giriş `lat/lon` yalnız `Double`, sonlu, |lat|≤90, |lon|≤180; Kaydet → `RESULT_OK(-1)` + Double extras, aksi `RESULT_CANCELED(0)` boş (`HomePickContract.java:7-27`). Harita ayrı Dart girişi `pickHomeMain` (unexported `HomePickMapActivity`, kanal `kurye_nav/home_pick` yöntemleri `load`/`attribution`/`save`/`cancel`); YolGo ayarına yazmaz ve YolGo evini okumaz; giriş yoksa mevcut GPS (yüksek doğruluk, 8 sn); OSM karoları, zoom 17, sabit merkez pin, 50 m daire, uzun basış noktayı taşır; dil/tema `settings.v1`'den salt okunur (`home_picker.dart:16-106`).
  - Ev koordinatı `day.home.v1`, ride log ve rapora yazılmaz (gizlilik; `home_day.dart:8-10`, `shift_controller.dart:416`).
- Testler: `home_picker_test` (5 tanım, döngüyle 6), `rawstats_home_sync_test` (13 tanım; `for status in [none,busy,missing]` ile 15), `rawstats_home_stale_test` (6), `HomeShareTest.kt` (3), `HomePickContractCheck.java` (main tabanlı, 1 suit ~16 assert satırı).
- Veri: `settings.v1` → `home` (`{lat, lon}` `GeoPoint.toJson`), `rawstats.home.lastSuccessAt.v1`; intent action'ları `com.kuryelog.app.action.SHARE_HOME`, `az.duzyol.app.action.SHARE_HOME`, `com.myapplab.yolgo.PICK_HOME`; request kodu 703.
- Bağımlı: G1 (ev kuralı), Y4 ("Eve dön"), Rawstats ev ayarı.
- WEEM: `:core` (tek ev kaydı; iki uygulama birleşince paylaşım aktiviteleri gereksiz — ama Rawstats'ın PICK_HOME çağırdığı ekran WEEM içi haritadan seçme olur) · Aşama 2 (`:core` ev) + 7 (YolGo `settings.v1.home` içe aktarma).
- Not: kodda "ev yalnız Rawstats'ta seçilir"; özellik listesinde "GPS ile ya da haritadan seçme" — WEEM'de tek ayar ekranı (Bölüm 5).

### G3 Dil
- Eski kod: `lib/core/i18n.dart:1-89` (`Lang {tr, az}`, `I18n.resolve`, `tl`, `trMsg` + `_msgAz` sözlüğü); ayar `lang` (`settings.dart`).
- Davranış:
  - Yalnız iki dil: `tr`, `az`. Ayar `lang`: `'tr'` | `'az'` | `''` (= telefon dili; sistem `az` ise az, aksi tr) (`i18n.dart:10-16`).
  - Metinler yerinde iki dilde `tl('tr','az')` (lib/ içinde 759 çağrı: `rg -o "\btl\(" lib | wc -l`). Arka plan/isolate Türkçe mesajları `trMsg` ile 57 kalıp alt-dize değişimiyle az'a çevrilir (`:22-89`).
  - Android tarafı: hava bildirimi ve kanal adı dil ayarına göre (`WeatherWorker.kt:129`), hatırlatıcı kanal adı yalnız Türkçe "Hatırlatıcılar" (`MainActivity.kt:401`); veri seti/kayıt kanalları Türkçe.
  - Ev seçici ayrı süreçte `I18n.resolve(settings.lang)` (`home_picker.dart:64`). Material yerelleri `tr`, `az`.
- Testler: `scooter_language_section_test` (3; `for dark` döngüsüyle 4), `remaining_km_e12_test` içinde `for lang in Lang.values` (2 dil), `offer_navigation_removed_test` (`for lang`). Ayrı i18n kapsam testi YOK (Rawstats'taki `I18nCoverageTest` benzeri yok).
- Veri: `settings.v1` → `lang`.
- WEEM: `:app` (dil seçimi, Android string kaynakları ya da tek `tl` tablosu) · Aşama 2/7.
- Not: Rawstats'ta `I18n.tl` + `I18nCoverageTest`; YolGo'da kapsam testi yok → WEEM'de iki tarafın metin sözlüğü birleşmeli.

### G4 Az bildirim (YolGo tarafı)
- Eski kod: `android_overlay/NotifyPolicy.kt:1-14`; `MainActivity.kt:259-268` (`notify` kanal yöntemi), `:392-430` (`showReminder`); `WeatherWorker.kt:110-140`; `lib/reminders/reminders.dart:288-325`; `lib/tasks/task_controller.dart:75-78`.
- Davranış:
  - Her YolGo bildirimi 30 sn sonra kendiliğinden kalkar (`NotifyPolicy.TIMEOUT_MS = 30_000`, `setTimeoutAfter`; uygulama kapalı olsa da) (`NotifyPolicy.kt:6`, `MainActivity.kt:416`, `WeatherWorker.kt:132`).
  - YolGo ekrandayken hatırlatıcı yalnız uygulama içi kart; sistem bildirimi yok (`inAppOnly(foreground, preview) = foreground && !preview`); "Dene" (preview) yine bildirim atar (`NotifyPolicy.kt:9`).
  - Bildirim yalnız aktif gün kimliği telemetri servisininkiyle eşleşirse (`TelemetryService.accepts(this, day)`) ya da önizleme + ön plan (`MainActivity.kt:264-265`); gün kapanınca hatırlatıcı bildirimleri iptal edilir (`reminders.dart:262-265`).
  - Hava özeti günde bir kez (önceden 2 saatte bir): `weatherDue(notifiedDay, day, lastMs, nowMs)` aynı gün ve saat geri gitmediyse false (`NotifyPolicy.kt:12-13`).
  - Kart/bildirim 30 sn (`reminderShowFor`, 1.7.0: 20 → 30 sn) (`reminders.dart:150-152`); `setOnlyAlertOnce(true)`, `setAutoCancel(true)`.
  - Gün toast'ları uygulama içi (sistem bildirimi değil): gün başladı/bitti, sipariş aşamaları, ışık (`shift_controller.dart:683-724`).
- Testler: `NotifyPolicyTest.kt` (2), `reminder_card_test` (3; "kart 30 sn").
- Veri: kanallar ve numaralar Bölüm 2 (`hatirlatici` 7100–7102, görev 7201, `duzyol_weather` 7301, `yolgo_recording` 7400, `yolgo_offer_dataset` 7401, `yolgo_calibration` 7435; konum ön plan bildirimi geolocator).
- WEEM: `:core` bildirim no tablosu + `NotifyPolicy` saf (`logic/`) · Aşama 2.
- Not: YolGo numaraları 7100–7435; Rawstats numaralarıyla tek tabloda çakışma denetlenmeli (plan Bölüm 5 "eski 5103 çakışması").

### Y20 Görev kartları ve hatırlatıcılar
- Eski kod: `lib/tasks/daily_task.dart:3-66` (`DailyTask`, `TaskTrigger`); `lib/tasks/task_controller.dart:13-122` (`TaskController`); `lib/ui/tasks_screen.dart:12-175` (`TasksScreen`, `_TaskEditor`, `TaskAlertOverlay`); `lib/reminders/reminders.dart:19-351` (`ReminderItem`, `ReminderSettings`, `reminderDue`, `ReminderSettingsController`, `ReminderController`); `lib/ui/reminder_widgets.dart:20-230` (`ReminderCard`, `ReminderSettingsSection`, `_ReminderTile`).
- Davranış (görevler = "Günlük işler"):
  - Görev: `id` (microsecondsSinceEpoch), `title`, `place`, `point`, `due` (yerel tarih+saat), `completedAt?`. Konum `AddressParser.parseExplicit` + `destinationResolver` ile bulunur (şube adı, koordinat ya da Maps bağlantısı) (`tasks_screen.dart:72-99`).
  - Uygun (`eligible`): tamamlanmamış, `now ≥ due` ve **aynı takvim günü** (telefon yerel saati) (`daily_task.dart:10-11`).
  - Tetik (`TaskTrigger`): giriş 1000 m / çıkış 1200 m histerezis; fix geçerli: doğruluk 0–65 m, yaş −5…+30 sn; uyarı yalnız **teslimat bitince** (`deliveryFinished`, `ShiftController._apply` `delivered` olayı) ya da kapatıldıktan sonra aynı gün bölgeye yeniden girişte (sipariş yokken); aynı görev bir gösterimde bir kez (`_queued`) (`daily_task.dart:32-65`).
  - Yalnız gün açıkken (`activeDay(dayId)`); 30 sn'de bir süresi geçen uyarılar düşer; gün kapanınca uyarılar temizlenir, bildirim 7201 iptal (`task_controller.dart:26-31,:51-59`).
  - Bildirim 7201 "Yakınında yapılacak iş var" + `title · place` satırları (`:75-78`).
  - "Rota Oluştur": `NativeBridge.openGoogleRoute(lat, lon)` (Google Maps); yalnız açılış başarılıysa `completedAt` yazılır, olay `task_route_opened`; "Tamam / Kapat" yalnız susturur (`dismiss`, `daily.tasks.closed.v1`) (`:97-121`).
- Davranış (hatırlatıcılar):
  - Türler `water`, `check`, `food`; varsayılan: su açık her 45 dk (gün içinde), kontrol kapalı 60 dk, yemek kapalı saat 15:15, `onlyShift=false` (`reminders.dart:124-129`).
  - `reminderDue`: kapalı ya da gün kapalı → false (not: `onlyShift=false` olsa da `shiftActive` gerekir); erteleme varsa süre dolunca; saatli: belirlenen dakikada, 3 dk'dan geç kalındıysa atlanır (`reminderLateMin = 3`), o saat bugün gösterildiyse tekrar yok; aralıklı: `max(since, gün başı (onlyShift), last)`'ten ≥ `intervalMin` dk (`:157-181`).
  - Denetim 20 sn'de bir, ekranda kart varken yenisi yok; açma/aralık/onlyShift/useTimes değişince sayaç (`since`) sıfırlanır; ilk yüklemede `since==0` ise şimdi (`:193-224,:257-296`).
  - Kart 30 sn, "✓ Tamam" (`done`), erteleme `snooze(10 dk)` kodda var ama kartta erteleme düğmesi yok (test "erteleme düğmesi yok"); olay `reminder {k, preview?}`; bildirim no `7100 + kind.index` (`:303-350`).
- Testler: `task_controller_test` (1 tanım; `for opens` ile 2), `reminders_test` (5), `reminder_card_test` (3).
- Veri: `daily.tasks.v1` (JSON liste `{id,title,place,point,due,completed?}`), `daily.tasks.closed.v1` (JSON map id→ms), `reminders.v1` (JSON `{water|check|food: {on,text,useTimes,every,at,shift,since}}`), `reminders.last.v1` (StringList `"<kind>:<ms>"`); kanal `hatirlatici`; bildirim 7100–7102, 7201.
- Bağımlı: G1 (gün/teslim olayı), Y6 (adres çözme), G4.
- WEEM: `:map` (görevler), `:core`/`:app` (hatırlatıcılar); `reminderDue`, `TaskTrigger` saf `logic/` · Aşama 6.
- Not: `reminderDue` ve `DailyTask.eligible` telefon yerel saatini kullanır (gün kimliği Bakü UTC+4 iken); WEEM tek saat kuralı seçmeli.

