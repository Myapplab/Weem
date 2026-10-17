# CORE — Aşama 2 şartnamesi (`:core`: G1, G2, G4, R20, depolama)

Durum: A2.1, 17 Ekim 2026 (Claude Opus · high, tek ajan). Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04`, `yolgo-test` @ `870ab6e`; satır numaraları bu commit'lerde açılıp doğrulandı (`bulgular/`'daki bazı satırlar yanlıştı, burada düzeltildi).

Kısaltmalar: **RS** = `app/src/main/java/com/kuryelog/app/`, **RT** = `app/src/test/java/com/kuryelog/app/` (rawstats); **YG** = yolgo-test kökü, **YO** = `android_overlay/`. Davranış metni kodun söylediğidir; "WEEM" satırları bu şartnamenin kararıdır.

**Kim kullanır:** A2.2 (testleri çevir: Bölüm 6), A2.3 (depolama, R20: Bölüm 4–5), A2.4 (vardiya/ev/bildirim: Bölüm 1–3), A2.5 (arayüzler: 1.8 ve 1.11). Plan bağlamı: `WEEM_PLANI.md` 4.2–4.4, 8 (S2, S7).

## 0. Özet: WEEM'deki tek kurallar

| Konu | WEEM kuralı | Neden / kaynak |
|---|---|---|
| Vardiya = gün (G1) | **Tek vardiya**; kural Rawstats `HomeShift` aynen (E6 başlangıç, F4.5b bitiş, K38 korumaları). YolGo `HomeDayRules` ve Rawstats↔YolGo eşitlemesi (`rawstats_shift.dart`) yazılmaz | S2 (17 Ekim): "bugünkü kural, ek GPS önlemi yok". YolGo farkları 1.9'da, senaryo senaryo |
| Vardiya başlatma | Yalnız 3 yol: elle, evden çıkış (geçerli ev revizyonuyla), H4 kesintiden devam; içe aktarma sürerken hiçbiri | RS `RecordingPolicy.kt:6`, `GpsService.kt:44-47` |
| Gün sınırı | Kayıt dosyaları **kayıt anının** telefon yerel tarihine (`files/logs/YYYY-MM-DD/`); vardiyanın kimliği başlangıç anı (`startedAtMs`), gece yarısında bölünmez | RS `Store.kt:43,50,53-58`; YG `day_scope.dart:1-7`. Azerbaycan'da yerel saat = UTC+4 = YolGo'nun Bakü saati (yaz saati yok), sonuç aynı |
| Ev (G2) | Tek kayıt: `kuryelog` `home.*` (Rawstats biçimi); GPS ile ya da WEEM haritasından seçilir; `SHARE_HOME`/`PICK_HOME` yok | S7; RS `HomeRepository.kt`, `HomePreferences.kt` |
| Bildirim (G4) | Tek tablo `NotificationIds` (3.3); her numara sabit ya da kendine ayrılmış aralık; derleme testi çakışmayı reddeder. Kanal kimlikleri aynı | Plan Bölüm 5; `VERI_SOZLESMESI` 8 |
| Çökme kaydı (R20) | Rawstats `CrashLog`/`CrashReport` aynen; `crashes.jsonl` `filesDir` kökünde; her giriş noktası korumalı | RS `CrashLog.kt`, `CrashReport.kt` |
| Depolama | Ad, yol, şema, tercih anahtarı aynı (Bölüm 5); `courier.db` sürüm 1, şema aynı | Plan Bölüm 2 |
| Mesafe | İki ölçüm kalır: vardiya sonu `shift_end.km` = Rawstats D8 (1.4); YolGo gün km'si (istatistik, Y17) YolGo algoritmasıyla `:map`'te | Gölge mod iki eski uygulamayla ayrı ayrı karşılaştırır; birleştirme 7. bölümde açık nokta |

## 1. G1 — Vardiya

### 1.1 Durum ve kalıcılık (`kuryelog` tercihleri)

- **Vardiya açık** ⇔ `shift == true` **ve** `manualSession == true` (`Store.shiftOn`, RS `Store.kt:93`).
- Ekran kaydı ancak vardiya açık ve içe aktarma yokken: `ManualSessionPolicy.canCapture(shift, manualSession, importing)` (RS `RecordingPolicy.kt:7`, `Store.kt:16`). `importing` süreç içi bayrak (`Store.kt:15`), tercih değil.
- Anahtarlar: `shift` (Bool), `manualSession` (Bool), `autoStarted` (Bool, **her başlangıçta false** yazılır; evden başlasa da — bilgi `shiftStartReason`'da), `autoShift` (Bool, yalnız göçte false), `manualPolicy` (**Int**, göç işareti), `shiftStart` (Long ms, duvar saati), `shiftStartReason` (`manual` \| `home_departure`), `shiftEndedAt` (Long ms), `shiftEndReason` (`manual` \| `home_arrival`), `shiftInterruptedAt` (Long ms, H4), `dist` (Float m, 15 sn'de bir), `home.startBlocked`, `home.endArmed` (Bool; ev kuralının koruyucuları, 1.6).
- `ensureManualMode` (RS `Store.kt:19-25`): `manualPolicy < 1` ise bir kez `manualPolicy=1`, `shift/manualSession/autoShift/autoStarted=false`. Her `GpsService.onStartCommand` başında çağrılır (`GpsService.kt:37`). WEEM: aynı (Rawstats'ın üstüne kurulunca işaret zaten 1'dir).

### 1.2 Başlatma (RS `GpsService.kt:36-103`)

Intent action'ları (`GpsService.kt:310-315`): `com.kuryelog.app.MANUAL_START` (elle; Activity ve H4 bildirimi), `com.kuryelog.app.HOME_START` (ev izleyici; extra `homeRevision` Long), `com.kuryelog.app.STOP` (bitir). İlk ikisi `startForegroundService` ile gelir (`FOREGROUND_ACTIONS`).

1. `ensureManualMode`.
2. `STOP` → `Store.distanceM = max(bellekteki, dist)`; `stopShift("manual")` (1.4); `START_NOT_STICKY`.
3. `homeStart` = action `HOME_START` **ve** `HomeLocation.acceptsStart`: intent'te `homeRevision` var ve kayıtlı evin `updatedAt ?: 0` değerine eşit (`HomeLocation.kt:38-39`, `HomeRepository.kt:42-44`; ev yoksa false). `HOME_START` geldiyse 5101 bildirimi her durumda silinir (`:46`).
4. `ManualSessionPolicy.mayStart(explicit = action==MANUAL_START || homeStart, manualSessionActive = shiftOn, importing)` = `!importing && (explicit || shiftOn)` (`RecordingPolicy.kt:6`). Değilse `refuse`: action ön plan türündeyse önce `startForeground` + hemen kaldır (Android "startForeground çağrılmadı" diye uygulamayı kapatmasın, 16 Eki), sonra `stopSelf` (`:107-111`). Yani intent'siz (null) yapışkan yeniden başlatma yalnız vardiya zaten açıksa devam eder.
5. `startForeground(1, …, LOCATION)` (`:157-175`): kanal `shift` "Vardiya kaydı"/"Növbə qeydi" LOW, ongoing, dokununca uygulama, eylem "Vardiyayı bitir"/"Növbəni bitir" (`PendingIntent.getService` req 11 → `STOP`). **Hata** → `gps_error {err}`; `onStartFailure(stickyRestart = intent==null, shiftOn)`: ikisi de doğruysa **H4** (1.5), değilse `Store.lastError = "Vardiya başlatılamadı: …"` ve vardiya açılmaz; `stopSelf`.
6. Bundan sonra her adım `guarded(kaynak)` içinde (`:113-115`): `Throwable` yakalanır (`VirtualMachineError` hariç, yeniden fırlatılır), `Store.failure(kaynak, e)` yazılır, vardiya sürer.
7. H4 devamı (`:68-72`): `shiftInterruptedAt > 0` ise ve `shift` açıksa `shift_resumed {interruptedAt}` olayı; anahtar silinir; 5103 silinir.
8. **Yeni vardiya** (`shift == false`, `:73-90`), bu sırayla: `shift=true, manualSession=true, autoStarted=false, shiftStart=now, dist=0`; bellek: mesafe 0, son fix, `lastLocation`, `MotionGate.reset`, ekran/teklif sayaçları 0; scooter vardiya odometresi sıfırlanır; `shiftStartReason` = `home_departure` (homeStart) ya da `manual`; ev koruyucusu: evden başladıysa `endArmed=true` (`onAutoStart`), elle başladıysa `onManualStart` (1.6); olay `shift_start {auto: homeStart, reason}`; YolGo duyurusu (WEEM: `ShiftPort.state` yayını); R9 cepteki nakit sorusu (`PocketCashPrompt.ask`); M17 `cashPendingShift(SHIFT_START)`.
9. **Açık vardiyanın yeniden başlaması** (`:91-94`): `distanceM = max(bellekteki, dist)`.
10. Her iki durumda: `OrderTracker.start`, GPS isteği (GPS sağlayıcısı, 2000 ms, 0 m; izin yoksa `gps_no_permission`, başka hata `gps_error`, `:177-189`), `ScooterLink.onShiftStart`, ekran okuma denetimi (E7, R19) başlar, ev izleyici yeniden denetlenir (vardiyada dinlemez). `START_STICKY`.

### 1.3 Fix işleme (vardiyada, RS `GpsService.kt:192-250`)

Her fix `runCatching` içinde (hata `errors.jsonl` `gps`, fırlatılmaz). Sıra önemlidir:

1. Vardiya kapalıysa yok say. Fix yaşı = `elapsedRealtime − loc.elapsedRealtimeNanos/1e6`; **0–12 000 ms dışı** ya da önceki mesafe fix'inden eski/eşit zaman → yok say (`:199`).
2. Türetilmiş hız (`:200-207`): önceki ve bu fix doğruluğu ≤30 m, 0 < dt < 15 sn, yer değişimi > max(8 m, iki doğruluğun toplamı) ise `d/dt`, değilse null. Hız = GPS hızı varsa o, yoksa türetilmiş. `MotionGate.observe(at, max(hız, türetilmiş), doğruluk)` (ekran okuma kuralı, Aşama 3).
3. `Store.lastLocation = loc`.
4. **Ev kuralı önce:** `HomeLocation.observe(loc, shiftActive=true, busy = OrderTracker.hasOpenOrder())`; `END` → `stopShift("home_arrival")` ve **çık** — bitiş fix'i `gps.csv`'ye yazılmaz (`:213`). `busy` = açık (aktif) bir sipariş var.
5. `gps.csv` (gün klasörü): dosya yoksa başlık `t,lat,lon,acc_m,speed_ms,bearing,alt_m`; satır `Store.ts(loc.time),lat,lon,accuracy,speed,bearing,altitude` (Android `Location` değerleri, Float/Double `toString`) (`:214-219`).
6. **Mesafe (D8 GPS birikimi, `:222-237`):** yalnız doğruluk ≤30 m. İlk fix çapa. dt > 30 sn → çapa bu fix (boşluk yol sayılmaz). 0 < dt, `d/dt ≤ 25 m/s` ve `d > max(4 m, (doğrulukların ortalaması))` → `distanceM += d`, siparişe `OrderTracker.distance(d)`, çapa bu fix. Diğer durumda çapa kalır. `d` = Android `Location.distanceTo`.
7. `dist` tercihi en çok 15 sn'de bir (`:239-243`).
8. Sipariş varış/bekleme (`OrderTracker.location`), scooter yeniden bağlanma tik'i, `OrderTracker.tick` — her biri ayrı korumalı (Aşama 3, 5).

`onProviderDisabled` → `gps_disabled` olayı.

### 1.4 Bitirme (RS `GpsService.kt:260-299`)

`stopShift(reason)`; `reason` ∈ `manual` (STOP) · `home_arrival` (ev kuralı). Vardiya zaten kapalıysa yalnız `stopSelf`. Sıra (her adım korumalı; bir adımın hatası vardiyayı açık bırakmaz):

1. `start = shiftStart` (yoksa şimdi). `ShiftDistance.result(liveM = distanceM, savedM = dist, odo = scooter vardiya odometresi)` → olay `shift_end {km, km_gps, km_scooter (yoksa null), km_from_scooter, min = (now − start)/60000.0 (Double), reason}`; `distanceM = gpsKm × 1000`.
2. `manual` ise `HomeLocation.onManualStop` (1.6 `startBlocked`).
3. M17 `cashPendingShift(SHIFT_END)`; `DailyPack.afterShift(start)` (R17, Aşama 4); `OrderTracker.stop`; `ScooterLink.onShiftEnd`; scooter odometresi sıfırlanır.
4. Tercihler: `shift=false, manualSession=false, dist, shiftEndedAt=now, shiftEndReason=reason`.
5. YolGo duyurusu (WEEM: `ShiftPort.state`); bildirim **12** (teslimat notu) iptal; ekran okuma uyarısı kapanır; ev izleyici yeniden denetlenir; `MotionGate.reset`; GPS isteği kaldırılır; ön plan kalkar; `stopSelf`.

**D8 `ShiftDistance`** (RS `ShiftDistance.kt:12-53`, saf): `result`: `ok(x) = x sonlu ve >0 ise x, değilse 0`; `gps = max(ok(live), ok(saved))/1000`; scooter = `odo.km` (`max(0, last−first)`); scooter > gps ise `Result(km=scooter, gps, scooter, fromScooter=true)`, değilse `Result(gps, gps, scooter, false)`. `observe(prev, km, at)`: km null/sonsuz/negatif → değişmez; prev yok → `Odo(km,km,at)`; geri giden km → hiç yol yoksa (`prev.km==0`) yeniden başlar, varsa değişmez; adım > max(0,2 km, saat × 60 km/s) → aynı kural; değilse `Odo(first, km, at)`. Kayıt: `shiftOdoFirstKm`, `shiftOdoLastKm` (tam metre Long, −1 = yok), `shiftOdoLastAt` (`meters`, `load`: `first<0` ya da `last<first` → null).

### 1.5 H4 — Android'in reddettiği yeniden başlatma

- Koşul: yapışkan yeniden başlatma (intent null) ve vardiya açık iken `startForeground` hatası (`RecordingPolicy.kt:11-12`). Vardiya **açık kalır** (`shift_end` yok); `shiftInterruptedAt = now`; olay `shift_interrupted {err}`; bildirim **5103** "Vardiya durdu" / "GPS kaydı yok. Devam etmek için dokun." (kanal `auto_start`, **zaman aşımı yok**, dokununca `MANUAL_START` ile ön plan servisi) (`GpsService.kt:53-57`, `AutoStartNotice.kt:41-46`).
- `interrupted(shiftOn, interruptedAt, shiftStart) = shiftOn && interruptedAt > 0 && interruptedAt >= shiftStart` (`RecordingPolicy.kt:14`): eski vardiyanın damgası yeni vardiyada sayılmaz.
- Devam: 1.2 adım 7.

### 1.6 Ev kuralı `HomeShift` (RS `HomeShift.kt:12-141`, saf) — WEEM'in tek kuralı

`Fix(at: monoton ms (elapsedRealtime), lat, lon, accuracyM: Double?, speedMps: Double?)`; `Decision {NONE, START, END}`. Mesafe: haversine, R = 6371,0088 km (`OfferGeo.haversineKm`, RS `OfferGeo.kt:29,143-152`; işlem sırası aynı tutulur). Ev noktası yalnız bellekte ve tercihte; bu sınıf koordinat yazmaz.

**Sabitler** (`HomeShift.kt:107-129`):

| Ad | Değer | Anlam |
|---|---|---|
| `START_RADIUS_M` | 50 | başlangıç: evden düz çizgi ≥ bu |
| `START_SPEED_MPS` | 10/3,6 | başlangıç: **GPS'in kendi hızı** ≥ bu (iki konumdan türetilen sayılmaz) |
| `START_MAX_ACCURACY_M` | 20 | başlangıç fix'i doğruluğu ≤ bu |
| `MAX_PAIR_GAP_MS` | 120 000 | iki başlangıç fix'i arası; ayrıca sıçrama ve hız penceresi |
| `HOME_SEEN_ACCURACY_M` | 50 | vardiya dışında bundan gevşek fix hiçbir şey söylemez (çifti bozar) |
| `RADIUS_M` | 50 | bitiş yarıçapı (K38'de 200) |
| `END_MAX_ACCURACY_M` | 30 | vardiyada bundan gevşek fix yok sayılır |
| `END_SPEED_MPS` | 5/3,6 | bitiş penceresinde hız < bu |
| `STILL_AREA_M` | 10 | pencere çapasından uzaklık < bu |
| `STILL_MS` | 30 000 | pencere süresi ≥ bu |
| `MIN_WINDOW_FIXES` | 3 | pencerede en az fix |
| `MAX_GAP_MS` | 45 000 | pencerede iki fix arası ≤ bu |
| `MAX_PLAUSIBLE_MPS` | 50 | sıçrama eşiği |

**Ortak giriş** (`observe`, `:29-36`): enlem/boylam sonlu ve aralıkta, doğruluk var, sonlu, ≥0 değilse → `startPrev = null`, `NONE`. Sonra vardiya kapalıysa `observeIdle`, açıksa `observeShift`.

**Vardiya dışı — başlangıç** (`observeIdle`, `:38-55`):
1. Bitiş durumu temizlenir (`endPrev`, pencere).
2. Doğruluk > 50 → `startPrev = null`, NONE.
3. Önceki kullanılabilir fix (`idlePrev`) varken zaman geri/eşit **ya da sıçrama** (dt 1…120 000 ms ve `d/dt > 50 m/s`) → `startPrev = null`, NONE; **`idlePrev` değişmez** (sapan fix taban olmaz; sonraki fix'ler 120 sn boyunca eski tabana göre sıçrama denetlenir).
4. `idlePrev = f`. Evden `d ≤ 50` → `startBlocked = false` (evde görüldü).
5. Koşul: doğruluk ≤20 **ve** `d ≥ 50` **ve** geçerli GPS hızı (sonlu, ≥0) ≥ 10/3,6. Koşul yoksa ya da `startBlocked` → `startPrev = null`, NONE.
6. `startPrev` yok ya da ondan > 120 sn → `startPrev = f`, NONE. Varsa → `startPrev = null`, **`endArmed = true`**, `START`.

**Vardiyada — bitiş** (`observeShift`, `:57-80`):
1. `startPrev = null`, `idlePrev = null`. Doğruluk > 30 → NONE (hiçbir durum değişmez).
2. `endPrev` varken zaman geri/eşit ya da sıçrama → NONE (`endPrev` ve pencere **değişmez**: tek sapan fix pencereyi bozmaz).
3. `endPrev = f`. `d > 50` → **`endArmed = true`**, pencere temizlenir, NONE.
4. `endArmed` değilse NONE. `busy` → pencere temizlenir, NONE.
5. Hız: `endPrev` önceden varsa `speedOf(prev, f)` (dt ≤0 ya da >120 sn → null; türetilmiş > 50 m/s → null; yoksa GPS hızı geçerliyse o, değilse türetilmiş), yoksa geçerli GPS hızı. Hız null ya da ≥ 5/3,6 → pencere temizlenir, NONE.
6. Çapa yok, son pencere fix'inden > 45 sn ya da çapadan ≥10 m → çapa = f, pencere 1 fix, NONE.
7. Pencere fix sayısı +1; `f.at − çapa.at ≥ 30 000` ve fix ≥3 → pencere ve `endPrev` temizlenir, `endArmed = false`, `startBlocked = false`, **`END`**.

**Koruyucular:** `onManualStart` (`:83`): `endArmed = false` (evde elle açılan vardiya ancak bir kez yarıçap dışına çıkınca biter), `endPrev` ve pencere temizlenir. `onManualStop` (`:86`): `startBlocked = true` (evde — `d ≤ 50`, doğruluk ≤ 50 — bir fix görülene kadar otomatik başlama yok), tüm geçici durum temizlenir. Evden başlayan vardiya: `endArmed = true` (`HomeLocation.onAutoStart`).

**Kalıcılık** (`HomeRepository.kt:28-41`): motor bellekte önbellekli (ev aynıysa); her `observe`/koruyucu değişikliğinden sonra yalnız iki bool değiştiyse `home.startBlocked`, `home.endArmed` yazılır. Süreç ölünce geçici durum (çift, pencere) kaybolur, koruyucular kalır.

`parseHome(lat: String?, lon: String?)` (`:134-139`): ikisi de `toDoubleOrNull`, sonlu, aralıkta değilse null — **tahmin yok**.

### 1.7 Ev izleyici (vardiya dışı; RS `HomeShift.kt:150-183`, `HomeLocation.kt:61-168`)

- **İzleme koşulu** `mayWatch` (`HomeRepository.kt:45-46`): ev var, vardiya kapalı, içe aktarma yok, `ACCESS_FINE_LOCATION` ve `ACCESS_BACKGROUND_LOCATION` verilmiş.
- Sürücü: tek izleyici süreç başına (`HomeWatch`); `WatchService`/`NotifService` tik'i ve vardiya başı/sonu çağırır; denetim en çok 5 sn'de bir (`HomeWatcher.refresh`, `:94-101`), `recheck` beklemeyi sıfırlar. WEEM: sürücü vardiya servisi + okuma servisleri + uygulama açılışı (aynı 5 sn kuralı).
- `HomeWatchPlan` (saf): `LOW` = dengeli güç 30 sn (min 15 sn; `FUSED` API 31+, yoksa ağ sağlayıcısı, o da yoksa GPS), `HIGH` = GPS 2 sn. LOW'da fix: `fast` = GPS hızı sonlu ve ≥ 10/3,6; `away` = doğruluk sonlu ve `d − doğruluk ≥ 50`. `fast` **ya da** (`away` **ve** son HIGH bitişinden ≥ 10 dk) → HIGH. HIGH 5 dk sonra (fix olmasa da, `tick`) LOW'a döner ve bekleme başlar. `reset` (vardiya başı, ev değişti, izleme bitti): LOW, bekleme yok (`:150-183`).
- İzleyici fix'i (`:137-157`): vardiya açık ya da içe aktarma varsa yok say. `observe(shiftActive=false)`; `START` değilse plan güncellenir (mod değiştiyse istek değişir). `START` → plan sıfır, `startForegroundService(HOME_START + homeRevision)`. Android reddederse: olay `home_auto_start_failed {err: sınıf adı}` + bildirim **5101** "Evden çıktın" / "Vardiyayı başlatmak için dokun." (kanal `auto_start` HIGH, 15 dk zaman aşımı, dokununca aynı `HOME_START` intent'i ön plan servisi olarak) (`AutoStartNotice.kt:26-31`).
- İstek hataları: `SecurityException` → `home_watch_no_permission`; diğer → `home_watch_error {err: sınıf adı}`.

### 1.8 Vardiya durumu yükü ve biten vardiya dakikaları

- **`ShiftState` v1** (= `ShiftStateFeed.snapshot`, RS `ShiftStateFeed.kt:15-22`): `{schemaVersion: 1, active, startedAtMs, endedAtMs, endReason, interrupted, generatedAtMs}`; `startedAtMs`/`endedAtMs` 0 ise `null`; `endReason` yoksa `null`; `interrupted` = 1.5 kuralı. **Koordinat yok.** Kaynak tercihler: `shiftOn`, `shiftStart`, `shiftEndedAt`, `shiftEndReason`, `shiftInterruptedAt`. WEEM: içerik sağlayıcısı kalkar (S7); aynı alanlar `ShiftPort.state` ve 2 telefon `ShiftState` v1 mesajı (plan 4.4).
- **`EndedShiftMinutes`** (RS `EndedShiftMinutes.kt:11-58`): bugünün `events.jsonl`'ündeki `type == "shift_end"` satırlarının `min` toplamı. Değişmemiş dosya (yol, uzunluk, değişme zamanı aynı) okunmaz; uzayan dosya kalınan bayttan okunur, yarım son satır tamamlanınca bir kez sayılır; başka dosya, kısalan ya da yerinde yeniden yazılan dosya baştan; dosya yoksa 0 ve sıfırlanır. Satır `"shift_end"` içermiyorsa ayrıştırılmaz; bozuk satır atlanır.

### 1.9 YolGo'nun ev/gün kuralıyla farklar (WEEM Rawstats'ı izler)

YolGo `HomeDayRules` (YG `lib/tracking/home_day.dart:24-163`, F4.5c varsayılanları `:26-30`) başlangıç eşiklerinde ve bitiş yarıçapı/hız/süresinde Rawstats ile aynıdır; aşağıdakiler farklıdır. WEEM'de **sol sütun** geçerlidir.

| Konu | Rawstats (WEEM) | YolGo (yazılmaz) |
|---|---|---|
| Fix süzgeci | dışarıda ≤50 m (başlangıç ≤20), vardiyada ≤30 m | her zaman ≤35 m (`:57-61`) |
| Sıçrama | `d/dt > 50 m/s` (dt ≤120 sn) → fix atılır, **önceki taban kalır**; pencere bozulmaz | `(d − ort. doğruluk)/dt > 16 m/s` (dt ≤30 sn) → hız penceresi temizlenir, bu fix yeni zincirin ilki (`:74-81`) |
| Başlangıç çifti | iki koşullu fix, arası ≤120 sn | arası ≤30 sn ("bağlı") (`:74,109`) |
| Elle bitirmeden sonra | `startBlocked`: evde (≤50 m, doğruluk ≤50) bir fix görülene kadar başlamaz | `waitExit`: 200 m içinde görülmeli, sonra 200 m dışına çıkılmalı (`:101-108`) |
| Bitiş kurulumu | doğruluk ≤30 bir fix `d > 50` | o gün içinde `d − doğruluk > 50` (`:119-124`); kimlik `armedDay` (gün başına) |
| Elle başlatma | `endArmed=false`; evden başlarsa `true` | her gün başında `armedDay = null` |
| Bitiş hızı | GPS hızı (≥0) ya da önceki fix'ten türetilmiş; **bilinmiyorsa pencere bozulur** | GPS hızı >0 ya da 10 sn pencere hızı (taban ≥2 sn, `d < max(3, ort. doğr.)` → 0); **bilinmiyorsa engellemez** (`:84-93,127`) |
| Bitiş penceresi | çapadan <10 m, ≥30 sn, **≥3 fix**, fix arası ≤45 sn | çapadan <10 m, ≥30 sn; fix sayısı ve aralık şartı yok (`:128-133`) |
| `busy` | açık (aktif) sipariş (`OrderTracker.hasOpenOrder`) | süren ya da sıradaki sipariş (`shift_controller.dart:427`) |
| Fix yaşı | vardiyada 0–12 sn (monoton saat); izleyicide denetim yok | −5…+30 sn (duvar saati, `shift_controller.dart:421-422`) |
| Kalıcı durum | `home.startBlocked`, `home.endArmed` (bool) | `day.home.v1` `{v:1, waitExit, seenInside, armedDay?}` |
| Otomatik aç/kapa ayarı | yok (her zaman açık) | `autoStartDay`/`autoEndDay` (1.10.0'dan beri hep true) |
| Gün kimliği | vardiya = `shiftStart` | `dayId = 'yolgo-<startedAt>'` (`shift_engine.dart:603`) |

**YolGo test senaryolarının Rawstats kuralındaki sonucu** (A2.1'de Rawstats `HomeShift`'in birebir kopyasıyla koşturuldu; YolGo test yardımcılarıyla aynı konum dönüşümü: `home_day_rules_test` 1/111320°, `home_day_e6_test` 1/111195°, `home_day_end_rule_test` 1/111195,08°). Aynı çıkanlar Bölüm 6'da "aynen"; farklı çıkanlar:

| YolGo testi | YolGo beklentisi | Rawstats kuralı (WEEM beklentisi) | Neden |
|---|---|---|---|
| `home_day_rules_test` "sıçrayan fix (1 sn'de 500 m)…" (a) | 250→750→752→756 m (1 sn arayla, 15 km/s): 4. fix'te `start` | hiç başlamaz (4 fix'in hepsi `none`) | 750 m sıçraması atılır, taban 250 m kalır; 752 ve 756 da ona göre sıçrama |
| aynı test (b) "31 sn kesinti" | 250 (0 sn) → 260 (31 sn): `none`, 264 (32 sn): `start` | 260 (31 sn) fix'inde `start` | çift aralığı 120 sn |
| `home_day_e6_test` "elle bitirdikten sonra 50–200 m içinde hızlı fixler…" | 80, 120, 190, 205 m: `none`; 209 m: `start` | hepsi `none`, `startBlocked` kalır | 50 m içinde görülmedi |
| `home_day_end_rule_test` "50 m içinde 30 sn dur" | ilk `end` 30–34. sn | ilk `end` **36. sn** | 300 m → 21 m (1 sn) gibi fix'ler 5. sn'ye kadar sıçrama sayılır; çapa 6. sn |
| `home_day_rules_test` "evde tek sapan fix kurmaz" (F4.5b'ye ölçeklenmiş: elle başla, 40 m, sonra 70 m doğruluk 30) | kurmaz | **kurar**; 40 m'de 30 sn sonra `end` | Rawstats kurulumda doğruluk payı düşmez. Doğruluk 31–35 m ile aynı sonuç (fix atılır) |
| `home_day_rules_test` "dışarıya çıkınca pencere iptal…" (çıkış fix'i 2 sn'de 380 m) | pencere baştan | çıkış fix'i sıçrama → atılır; pencere sürer, dönüşteki ilk fix'te `end` | Rawstats sıçrama kuralı; gerçekçi çıkışla (≤50 m/s) aynı sonuç: pencere baştan, 30. sn'de `end` |

Bunlar S2 kararının sonucudur ("ek GPS önlemi yok"); WEEM'de değiştirmek kullanıcı kararıdır.

### 1.10 Gün ve saat

- `Store.day(t)` = `SimpleDateFormat("yyyy-MM-dd", Locale.US)` telefonun saat diliminde (`Store.kt:43,50`). `Store.ts(t)` = `yyyy-MM-dd'T'HH:mm:ss.SSSZ`, `Locale.US`, telefon saat dilimi (ör. `2026-10-17T15:45:00.123+0400`); `parseTs` hata → 0 (`:44,49,51`).
- Gece yarısını geçen vardiya: her satır kendi anının gün klasörüne; `append(day=…)` ile bilerek önceki güne yazılabilir (bekleme parçası gibi, `Store.kt:56-58`).
- Duvar saati (ms) kayıt ve mesajlarda; monoton saat (`elapsedRealtime`) ev kuralı, izleyici planı ve fix yaşında. WEEM `:core`: `Clock { wallMs(); monoMs(); zone }` arayüzü; testlerde sahte saat. YolGo'nun Bakü saati (`day_scope.dart`) ve telefon yerel saatiyle çalışan hatırlatıcı/görev kuralları tek `Clock.zone` kullanır (Azerbaycan'da fark yok; telefon başka dilimdeyse WEEM Rawstats gibi telefonu izler).
- **Cihaz kimliği:** eski uygulamalarda yok. WEEM `yeni`: ilk açılışta rastgele 128 bit (`UUID`), `kuryelog` `weem.deviceId`; donanım kimliğinden türetilmez; yedeğe girmez. İlk kullanıcı 2 telefon modu (A8) ve N3; biçim A2.4'te sabitlenir.

### 1.11 WEEM yerleşimi (A2.4, A2.5)

- `logic/core` (saf, JVM test): `HomeShift`, `HomeWatchPlan`, `ManualSessionPolicy`, `ShiftDistance`, `ShiftStateSnapshot` (1.8 alanları), `EndedShiftMinutes` (dosya okuma `java.io`, Android'siz), D8 GPS birikimi ve fix yaşı/türetilmiş hız (1.3 adım 1, 2, 6 — Android `Location` yerine sade fix tipi; `distanceTo` yerine aynı sonucu veren WGS84 hesap **ya da** Android katmanında `distanceTo` çağrılıp saf koda mesafe verilir: A2.4 seçer, gölge modda eşitlik denetlenir), `ShiftMachine`: 1.2/1.4/1.5'teki karar ve sıra (hangi olay, hangi tercih, hangi kanca) — Android yalnız uygular.
- Android `:core`: vardiya ön plan servisi (ad serbest, tür `location`), ev izleyici, fix örnekleyici, bildirimler. Vardiya başı/sonu kancaları (`OrderTracker`, `ScooterLink`, `PocketCashPrompt`, `CashPending`, `DailyPack`) `ShiftPort` dinleyicisi olur; ilgili modül kendi aşamasında takar. Aşama 2'de kancalar boş.
- `ShiftPort` (plan 4.3): `state: StateFlow<ShiftState>`, `start(reason)`, `stop(reason)`; `busy` sağlayıcısı `OrderEvents`'ten (Aşama 3'e kadar false).
- YolGo'nun "gün"ü (`:map`, Y11/Y17/Y18) `ShiftPort.state`'i izler: başlangıç/bitiş nedeni aynı dizgiler (`manual`, `home_departure`, `home_arrival`); gün açılışı `startedAtMs`, kapanışı `endedAtMs`; aynı vardiya için bir kez (`rawstats_shift_end_test` uyarlaması, Bölüm 6).
