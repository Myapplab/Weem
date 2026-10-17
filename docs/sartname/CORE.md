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

1. Vardiya kapalıysa yok say. Fix yaşı = `elapsedRealtime − loc.elapsedRealtimeNanos/1e6`; **0–12 000 ms dışı** ya da mesafe çapasından (adım 6) eski/eşit zaman → yok say (`:199`).
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

## 2. G2 — Ev konumu

### 2.1 Kayıt (RS `HomeRepository.kt:4-47`, `HomePreferences.kt:6-28`)

- Tek kaynak `HomeRepository(storage)`; Android tercihleri bağdaştırıcı. Taslaklar depoya girmez.
- Tercih anahtarları (`kuryelog`): `home.lat`, `home.lon` (**String**, `Double.toString`), `home.updatedAt` (Long; **yoksa "bilinmiyor"** — eski kurulumlarda uydurulmaz), `home.startBlocked`, `home.endArmed` (Bool). Okuma `HomePreferences.read` (`:7-9`); yazma: `lat` ya da `lon` null → **beş anahtarın hepsi silinir**; değilse ikisi yazılır, `updatedAt` null ise anahtar silinir, iki bool yazılır (`:10-20`).
- `point()`: `parseHome(lat, lon)` (1.6) geçerliyse `Point(lat, lon, updatedAt)`, değilse null.
- `set(lat, lon, now)`: `parseHome(lat.toString(), lon.toString())` geçersizse `require` hatası (**geçerli ev silinmez**); yazılan `Saved(lat, lon, updatedAt = max(now, eskiUpdatedAt + 1))` — revizyon tek yönlü artar; iki bool **false** (eski koruyucular düşer); motor önbelleği atılır (`:16-20`).
- `clear()`: tüm `home.*` silinir (`:21`). `set`/`clear` sonrası (cephe `HomeLocation.changed`, `HomeLocation.kt:32-35`): 5101 bildirimi silinir, izleyici durur, plan sıfırlanır, yeniden denetlenir.
- `acceptsRevision(r)`: ev var ve `r != null` ve `r == (updatedAt ?: 0)` (`:42-44`) — eski ev için kuyruğa girmiş `HOME_START` reddedilir (aynı milisaniyede kaydedilse bile, `max(now, eski+1)` sayesinde).
- **Gizlilik:** ev koordinatı yalnız bu tercihlerde ve bellekte; günlüğe, `events.jsonl`'e, dışa aktarmaya, mesaja (`ShiftState`), 2 telefon yüküne yazılmaz (`HomeLocation.kt:16-18`, `HomeShift.kt:9`). Olay ve hata kayıtlarında da yok (`reasonsHaveNoCoordinates`).

### 2.2 Seçim — taslak (RS `HomeDraft.kt`, `HomeFixes.kt`, `HomeFixSampler.kt`)

- `HomeDraft.create(lat, lon, accuracyM?)`: lat/lon null, sonsuz ya da aralık dışı → null; doğruluk varsa sonlu ve ≥0 olmalı (0 geçerli) → yoksa null. `needsWarning` = doğruluk **var ve** > 25 m (doğruluk bilinmiyorsa uyarı yok) (`HomeDraft.kt:5-13`). Taslak oluşturmak ya da atmak tercihe yazmaz.
- `HomeFixes` (`HomeFixes.kt:4-27`): örnek `(lat, lon, accuracyM?, atMs monoton)`. `add`: doğruluk yok, sonsuz, negatif → red; yaş `now − at` 0…15 000 ms dışı (gelecek dahil) → red; koordinat geçersiz → red; eklemeden önce bayatlar atılır. `best` = en küçük doğruluk, eşitse **en yeni**. `bestAt(now)` önce bayatları atar. Sabitler: `WARN_ACCURACY_M = 25`, `MAX_AGE_MS = 15 000`, `COLLECT_MS = 6 000`; `needsWarning(sample)` doğruluk yoksa **true** (taslağınkinden farklı, ikisi de korunur).
- `HomeFixSampler` (Android, `HomeFixSampler.kt:21-54`): FINE izni yoksa başlamaz; etkin sağlayıcılar (PASSIVE hariç) yoksa başlamaz; her sağlayıcıdan son bilinen konum + 1 sn'lik güncelleme; hiçbir istek kaydolmazsa iptal; 6 sn sonra en iyi örnek (o anda bayat olanlar atılmış) sonuç; her yeni en iyi örnekte ilerleme (doğruluk) bildirilir; ekran kapanınca iptal.
- **Ekran (bugünkü Rawstats, `MainActivity.kt:1298-1345`):** "Kayıtlı ev" kartı: koordinat `%.6f, %.6f` (`Locale.US`), "Son güncelleme: `dd.MM.yyyy HH:mm`" (dil yereli) ya da "bilinmiyor"; Düzenle; Sil (onay diyaloğu). Ev yoksa ya da düzenlemede: "Şu anki konumumu kullan" (izin yoksa FINE+COARSE ister; ölçerken "6 saniye ölçülüyor…", canlı "Doğruluk: ±x,x m", 25 m'den kötüyse uyarı rengi ve "Doğruluk 25 m'den kötü; daha açık bir yerde yeniden ölçebilirsin."; sonuç yoksa "Güncel konum alınamadı; dışarıda tekrar dene."; sağlayıcı yoksa "Konumu açıp tekrar dene.") ve haritadan seçme. Taslak kartı "Seçilen konum · taslak": koordinat, doğruluk, uyarı; Kaydet → `set`; İptal. Ekran durumu (açık, ayarlardan mı gelindi, düzenleme, taslak lat/lon/doğruluk) Activity `savedInstanceState` paketinde (`home.ui.*`, `:149-154,187-192`) — **tercih değildir**, veri sözleşmesine girmez.
- **Haritadan seçme:** bugün YolGo'nun `PICK_HOME` ekranı (OSM, yakınlık 17, sabit merkez iğnesi, 50 m daire, uzun basış noktayı taşır; giriş yoksa yüksek doğruluklu GPS 8 sn; YG `lib/ui/home_picker.dart:14-107`). Dönüş yalnız `Double` lat/lon, sonlu, aralıkta; `RESULT_OK` değilse hiçbir şey seçilmez; sonuç **doğruluğu bilinmeyen** taslaktır (`HomePickContract.parse`, RS `HomePickContract.kt:11-14`). WEEM: aynı ekran `:map`'te (A6), dönüş aynı doğrulamadan geçen `HomeDraft(lat, lon, null)`; A6'ya kadar yalnız GPS ile seçilir.

### 2.3 YolGo tarafı (WEEM'de yok)

YolGo evi Rawstats'tan okur (`SHARE_HOME`, istek 703, 20 sn zaman aşımı, 6 sa/1 sa bayatlık; YG `lib/core/rawstats_home.dart:13-102`) ve `settings.v1.home` önbelleğinde tutar. WEEM'de tek ev olduğu için eşitleme, bayatlık ve iki yönlü paylaşım aktiviteleri yazılmaz (S7). Kalan ilke: **başarısız okuma geçerli evi silmez** (= `set`'in geçersiz değerde `require` hatası vermesi). YolGo'nun eski `settings.v1.home` değeri içe aktarılmaz (S1).

## 3. G4 — Az bildirim

### 3.1 Kurallar (eski davranış korunur)

- **YolGo kökenli bildirimler** (hatırlatıcı, görev, hava) 30 sn sonra kendiliğinden kalkar: `NotifyPolicy.TIMEOUT_MS = 30 000` (`setTimeoutAfter`; YG `YO/NotifyPolicy.kt:6`, `YO/MainActivity.kt:416`, `YO/WeatherWorker.kt:132`); `setOnlyAlertOnce(true)`, `setAutoCancel(true)`.
- `inAppOnly(foreground, preview) = foreground && !preview`: uygulama ekrandayken hatırlatıcı yalnız uygulama içi kart; "Dene" (önizleme) yine sistem bildirimi atar (`YO/NotifyPolicy.kt:9`). WEEM: "ekranda" = WEEM Activity'si önde (hangi sekme olursa olsun).
- `weatherDue(notifiedDay, day, lastMs, nowMs) = !(notifiedDay != null && notifiedDay == day && lastMs > 0 && nowMs >= lastMs)`: hava özeti günde bir; saat geri giderse yeniden (`YO/NotifyPolicy.kt:12-13`).
- YolGo bildirimleri yalnız aktif gün varken (önizleme hariç); gün kapanınca hatırlatıcı, görev ve hava bildirimleri iptal (`YO/TelemetryService.kt:64` `(7100..7310)` aralığını iptal eder). **WEEM:** aralık değil **açık liste** iptal edilir (3.3 `SHIFT_END_CANCEL`), çünkü birleşik uygulamada aralık başka bildirimlere (eski nakit bahşiş havuzu gibi) denk gelebilir.
- **Rawstats bildirimleri** kendi zaman aşımlarını korur: 5101/5102 15 dk, 5103 yok (dokunana ya da silinene kadar), nakit bahşiş onayı 15 sn (`CashTipPrompt.kt:86`), cepteki nakit onayı 10 sn (`PocketCashPrompt.kt:51`), scooter bağlandı 15 sn (`ScooterLink.kt:51`), teslimat notu (12) vardiya sonunda iptal. Uygulama içi bildirimler (gün başladı/bitti, aşama, ışık toast'ları; YG `shift_controller.dart:683-724`) sistem bildirimi değildir.

### 3.2 Bugünkü numaralar ve çakışmalar

`VERI_SOZLESMESI.md` 8.1 tablosu doğrulandı (RS `AutoStartNotice.kt:18-20`, `ScreenReading.kt:38`, `CashTipPrompt.kt:22`, `PocketCashPrompt.kt:26`, `ScooterLink.kt:32`, `DeliveryReminder.kt:303-308`, `GpsService.kt:174`; NB `RecordingService.java:64-66`, `PairAttention.java:22-24`; YG `reminders.dart:327`, `task_controller.dart:76`, `YO/WeatherWorker.kt:133`, `YO/TelemetryService.kt:26,144`, `YO/DatasetRecordingService.kt:17`, `YO/RoadCalibrationService.kt:21`). İki sorun: **5103** iki sabitte (H4 vardiya ve ekran okuma), **nakit bahşiş** `4000 + (tripId.hashCode() and 0xFFF)` = 4000–8095 aralığı 4901, 4902, 5101–5103, 7100–7102, 7201, 7301, 7400, 7401, 7435'i kapsar.

### 3.3 WEEM `NotificationIds` (A2.4 uygular; `logic/core`, saf)

| No | Ad | Kanal (kimlik aynı) | Önem | Kalkış | Sahibi · aşama |
|---|---|---|---|---|---|
| 1 | `SHIFT_FGS` | `shift` | LOW | ongoing | `:core` · A2.4 |
| 7 | `SCOOTER_FGS` | `recording` | (NB'deki gibi) | ongoing | `:scooter` · A5 |
| 8 | `PAIRING` | `pairing-attention` | HIGH | (NB'deki gibi) | `:scooter` · A5 |
| 12 | `DELIVERY_NOTE` | `delivery_notes` | HIGH, titreşim | vardiya sonu iptal | `:orders` · A3 |
| 4901 | `POCKET_CASH` | `pocket_cash` | HIGH | onay 10 sn | `:orders` · A3 |
| 4902 | `SCOOTER_LINK` | `scooter_link` | DEFAULT | bağlıysa 15 sn | `:scooter` · A5 |
| 5101 | `HOME_AUTO_START` | `auto_start` | HIGH | 15 dk | `:core` · A2.4 |
| 5102 | `SCOOTER_AUTO_START` | `auto_start` | HIGH | 15 dk | `:scooter` · A5 |
| 5103 | `SHIFT_INTERRUPTED` | `auto_start` | HIGH | yok | `:core` · A2.4 |
| **5104** | `SCREEN_READING_OFF` (**eskiden 5103**) | `screen_reading` | HIGH, ongoing | ekran okuma dönünce | `:orders`/R19 · A3 |
| 7100–7102 | `REMINDER(kind)` = 7100 + `water/check/food` sırası | `hatirlatici` | HIGH, titreşim | 30 sn | `:map`/Y20 · A6 |
| 7201 | `TASK_NEARBY` | `hatirlatici` | HIGH | 30 sn | `:map`/Y20 · A6 |
| 7301 | `WEATHER` | `duzyol_weather` | DEFAULT | 30 sn; günde bir | `:map`/Y10 · A6 |
| 7401 | `DATASET_FGS` | `yolgo_offer_dataset` | LOW | ongoing | `:map`/Y19 · A6 |
| 7435 | `CALIBRATION_FGS` | `yolgo_calibration` | LOW | ongoing | Y14 · sonra |
| **10000–14095** | `CASH_TIP(tripId)` = 10000 + (`tripId.hashCode() and 0xFFF`) (**eskiden 4000+**) | `cash_tips` | HIGH | onay 15 sn | `:orders` · A3 |

- **7400 kullanılmaz:** YolGo'nun gün kaydı servisi (`yolgo_recording`) vardiya servisiyle birleşir (1 numara). `SHIFT_END_CANCEL = {12, 7100, 7101, 7102, 7201, 7301}`.
- Numara değişiklikleri (5103→5104, 4000+→10000+) kullanıcıya görünmez: bildirim numarası yalnız sürecin çalışma anında anlamlı; kanal kimlikleri ve ayarları değişmez.
- Test (`NotificationIdsTest`, yeni): her sabit ve aralık ayrık; aralık formülünün her girdi için aralık içinde kalması (`and 0xFFF`); `SHIFT_END_CANCEL` tabloda; kaynakta (`app/`, modüller) `notify(`/`cancel(` çağrısına düz sayı yazılmaz (yalnız `NotificationIds`).
- Kanal adları iki dilli (`Text` tablosu, A1.3); YolGo'da kanal adı yalnız Türkçe olanlar ("Hatırlatıcılar", veri seti) WEEM'de TR/AZ olur.

## 4. R20 — Çökme kaydı ve korumalı girişler

### 4.1 Çökme raporu (RS `CrashLog.kt:18-61`, `CrashReport.kt:11-63`, `RawstatsApp.kt:7-12`)

- **Kurulum:** `Application.attachBaseContext` içinde, sağlayıcı/servis/Activity'den önce `CrashLog.install` (bir kez). Varsayılan yakalanmamış istisna işleyicisi: raporu yaz (hata yutulur) → önceki işleyiciye devret; önceki yoksa `killProcess` + `exitProcess(10)`. `Store` kilitleri alınmaz (çöken iş parçacığı tutuyor olabilir); dosya yazımı kendi kilidinde.
- **Dosya** `filesDir/crashes.jsonl` (gün klasörü değil, `logNames`'te değil): satırlar okunur, boşlar atılır, yeni rapor eklenir, `trim` sonrası tümü yeniden yazılır (`\n` ile, sonda `\n`).
- **Rapor** `json(at, version, thread, e)`: `{at (ms), version (versionName ya da "?"), thread, error (sınıf tam adı), message (ilk satır, ≤200), stack}`. `stack` = `stackTraceToString` satırları; sekme/boşlukla başlamayan (başlık: "X: mesaj", "Caused by: …") satırlar 200 karaktere kırpılır; toplam ≤ 8 000 (`STACK_CHARS`, `MESSAGE_CHARS`).
- `trim(lines, maxChars=200 000, keep=10)`: `Σ(uzunluk+1) ≤ maxChars` ise hepsi, değilse son 10.
- **Android çıkış kayıtları** (API 30+): `getHistoricalProcessExitReasons(pkg, 0, 5)` içinden `exitShown` olan ilki: neden `5` NativeCrash, `6` ANR, `7` InitializationFailure ve `0 ≤ now − at ≤ 3 gün`. `exitJson(at, "?", reason, description, process)` = `{at, version:"?", thread:"android", exit, error: etiket (yoksa "Exit<n>"), message: açıklamanın ilk satırı ≤200, stack: "Android exit reason <n> (<etiket>) · <süreç ya da ?>\n<mesaj>"}`.
- **Kart:** `unseen` = `last` (dosyadaki en yeni ayrıştırılabilir satır) ve `lastExit` içinden `at > crashSeenAt` olanların en yenisi; Gizle → `crashSeenAt = at` (yeni çökme yeniden görünür). Özet `summary`: `<sınıf kısa adı>[: <mesaj ≤120>][ (<ilk uygulama karesi>)]`; ilk kare deseni `at (?:com\.kuryelog\.app|az\.ninebot\.logger)\.[^(\s]*\(([^)]+)\)` → **WEEM:** `at (?:com\.kuryelog\.(?:app|logic)|az\.ninebot\.logger)\.…` (WEEM kodu `com.kuryelog.app`/`com.kuryelog.logic`'te; eski Sürüş Paneli paketi zararsız kalır, eski test aynen geçer). Paylaşılan metin `"<ad> <sürüm> · <saat> · <thread>\n<stack>"`, ad "Rawstats" → **"WEEM"**. Kartta kopyala, paylaş, Gizle (`MainActivity.kt:367-378`).
- Çizilemeyen sayfa (`renderFailure`, `MainActivity.kt:345-364`): aynı biçimde kart + `Store.failure("page_<ad>")`; vardiya düğmesi çalışır kalır. WEEM: her sekme kendi hata sınırında; sekmenin çizimi hata verirse sekme yerine kart, alt menü ve vardiya düğmesi çalışır.

### 4.2 Çökmeyen hatalar ve korumalı girişler

- `Store.failure(source, e)` (`Store.kt:28-31`): `lastError = "<source>: <SimpleName>: <message>"`; `errors.jsonl` (gün klasörü) satırı `{t: ts(), source, error: lastError}`. Bugünkü kaynak etiketleri (A3 için liste): `screen_start`, `event`, `screen`, `ocr`, `tick`, `event_scope`, `notification`, `gps`, `gps_orders`, `shift_start`, `shift_resume`, `shift_orders`, `shift_scooter`, `shift_end`, `economics_idle`, `open_waits`, `online_sessions`, `page_<ad>`.
- **Koruma ilkesi** (16 Eki saha düzeltmesi; `CrashGuardTest`): sistemin çağırdığı her giriş (erişilebilirlik olayı ve tik'i, ekran okuma, OCR geri çağrıları, bildirim dinleyici, konum geri çağrısı, vardiya servisinin başlatma/fix/bitirme adımları, sayfa çizimi ve dokunuşlar) hatayı kaydeder, **fırlatmaz**; tik her durumda yeniden kurulur (`finally`); ön plan diye başlatılan servis reddedilse de `startForeground`'a ulaşır (1.2 adım 4).
- **WEEM (A2.3):** `guarded`/`attempt` (`logic/core` `Guard.kt`), Android'de `Store.guarded(ctx, kaynak)`, dokunuşlar `Context.safely`, sayfalar `GuardedPage` (Compose çizimin içindeki istisnayı yakalayamadığı için sayfanın durumu korumalı `build`'de hesaplanır, çizim yalnız çizer). Dil (`Lang`) `logic/core`'a taşındı; `:core` metinleri `CoreText`.
- **WEEM:** tek yardımcı `guarded(source) { … }` (`logic/core`, saf; kayıt hedefi arayüz): `Throwable` yakalar, `VirtualMachineError`'ı yeniden fırlatır (bugünkü `GpsService.guarded`, `:113-115`). Eski kodda bazı girişler `runCatching` kullanıyor (bellek hatasını da yutuyor); WEEM'de hepsi `guarded` — **bilerek küçük fark** (bellek/yığın taşması artık süreci kapatır ve çökme raporu yazılır).

## 5. Depolama (A2.3)

### 5.1 Yerler

| Yer | İçerik | Sahibi · aşama |
|---|---|---|
| `filesDir/logs/YYYY-MM-DD/` | gün klasörü; `logNames` dosyaları + `.archived`, `.archived-<ad>` işaretleri | `:core` · A2.3 |
| `filesDir/crashes.jsonl` | çökme raporları (4.1) | `:core` · A2.3 |
| `filesDir/restaurants.json` | restoran öğrenmesi (JSON nesne; anahtar başına `n` sayacı) | `:orders` · A3 (içe aktarma birleştirmesi A2.3) |
| `databases/courier.db` | 5.5 | `:core` şema · A2.3; satırlar `:orders` · A3 |
| `shared_prefs/kuryelog.xml` | 5.6 | anahtar sahibine göre |
| `noBackupFilesDir/economics-day-secrets-v3/<gün>` | 32 bayt gün sırrı; yedeğe girmez | `:core` · A4.3 |
| `logger`, `pairing-v1` tercihleri; `ninebot-logger.db`; Keystore | Sürüş Paneli | `:scooter` · A5 |

### 5.2 Satır ekleme ve olaylar (RS `Store.kt:56-88`)

- `append(name, line, day = null): Boolean` — **tek kilit** (`@Synchronized`, tüm dosyalar): `day` verilmişse `\d{4}-\d{2}-\d{2}` olmalı (değilse hata → aşağıdaki gibi false), o klasöre; yoksa `day(now)`. Klasör yoksa açılır; `line + "\n"` eklenir; aynı klasörde `.archived` ve `.archived-<name>` **silinir** (yeni kayıt günü arşivden çıkarır). Ad `LogFeedV3.names` içindeyse gün değişikliği duyurulur (WEEM: iç olay `DayLogChanged(day)`, gün başına en çok 5 sn'de bir; `:map` Y19 dinler). Hata → `lastError = "Kayıt yazılamadı: <mesaj>"` / "Qeyd yazılmadı: …", **false**, fırlatmaz.
- `event(type, extra?)` → `events.jsonl`: `extra` nesnesine `t = ts()`, `type`, son konum varsa `loc = {lat, lon, acc, age_s}` (`age_s` = (şimdi − fix duvar zamanı)/1000, tam sayı). Vardiya olayları (A2.4): `shift_start {auto, reason}`, `shift_end {km, km_gps, km_scooter, km_from_scooter, min, reason}`, `shift_interrupted {err}`, `shift_resumed {interruptedAt}`, `gps_error {err}`, `gps_no_permission`, `gps_disabled`, `home_watch_no_permission`, `home_watch_error {err}`, `home_auto_start_failed {err}`.
- `errors.jsonl`: 4.2. `gps.csv`: 1.3 adım 5.
- **WEEM yerleşimi (A2.3):** saf `LogStore` (`logic/core`; saat/gün `LogTime`, Rawstats biçimiyle birebir), Android `Store` (`core/`) onun tek örneğini tutar. Bilerek küçük farklar: `day` parametresi gerçek tarih de olmalı (`LogTime.checkedDay`; Rawstats yalnız deseni denetliyordu); gün değişikliği duyurusu şimdilik dinleyici kancası (`dayWritten`), 5 sn kısıtı ve ad süzgeci dinleyicisiyle (`:map` Y19, A6) gelir.
- **JSON:** org.json API (Android'de platformunki; `logic/` testlerinde `org.json:json:20240303`, Rawstats'ın test bağımlılığı). Eşitlik **ayrıştırılmış değerle** ölçülür (alan sırası ve kaçış biçimi önemsiz; Android `/` karakterini `\/` yazar, JVM kütüphanesi yazmaz). Gölge mod karşılaştırması da böyle.

### 5.3 Gün kayıtları (RS `BackupFiles.kt:5-48`, `DailyRecords.kt:7-52`)

- **`logNames`** (27 ad, tek kaynak; listede olmayan dosya dışa/içe aktarma ve gün silmede yok sayılır): `screens.jsonl`, `notifs.jsonl`, `events.jsonl`, `orders.jsonl`, `gps.csv`, `scooter.jsonl`, `scooter_events.jsonl`, `scooter_averages.jsonl`, `platforms.jsonl`, `errors.jsonl`, `economics-waits.jsonl`, `economics-exposure.jsonl`, `online-sessions.jsonl`, `economics-offers.jsonl`, `economics-legs.jsonl`, `economics-idle.jsonl`, `economics-penalties.jsonl`, `economics-platform-history.jsonl`, `economics-platform-totals.jsonl`, `economics-cash-tips.jsonl`, `economics-pocket-cash.jsonl`, `economics-cash-pending.jsonl`, `economics-boosts.jsonl`, `economics-energy.jsonl`, `economics-scooter.jsonl`, `economics-offer-scores.jsonl`, `economics-offer-ratings.jsonl`. WEEM yeni gün dosyası eklerse bu listeye girer (`everyFileWrittenByTheAppIsInTheBackupList` WEEM kaynağını tarar).
- `DailyRecords(root)`: tarih `\d{4}-\d{2}-\d{2}` ve `LocalDate.parse` geçerli olmalı (yoksa `require`/parse hatası — yol geçişi reddi). `days()`: klasörler, tanınan dosyası olmayan gün yok, `Day(date, bytes = Σ boyut, archived = .archived var, files ad sırasıyla)`, tarihe göre **yeniden eskiye**. `files(date)`: yalnız `logNames` içindeki dosyalar. `archived(date, name)` = `.archived-<name>` var. `archive(date, name?, enabled)`: klasör olmalı; işaret yazılır/silinir (içerik hiç değişmez). `delete(date)`: yalnız tanınan dosyalar ve bizim işaretlerimiz; bilinmeyen dosya kalır; klasör boşaldıysa silinir. `delete(date, name)`: o dosya ve işareti.
- **İçe aktarma** (`LogImporter.restore`, RS `LogImporter.kt:10-66`) — **A2.3'e alınır** (A2.6'da `.dev` eski veriyi ancak Rawstats ZIP'iyle okuyabilir; başka paketin dosyası okunamaz): vardiya açıkken başlatılmaz; sürerken `importing = true` (vardiya başlatma ve ekran kaydı durur). Önce hepsi geçici klasöre: dizin girdileri atlanır; ad `checkedPath` (yalnız `restaurants.json` ya da `YYYY-MM-DD/<logNames>`; `\` ve baştaki `/` red; tarih geçerli); aynı ad iki kez → red; ≥2000 dosya → red; dosya ≤32 MB, toplam ≤256 MB; `.jsonl` her dolu satır JSON nesnesi, `restaurants.json` JSON nesnesi, `.csv` ilk satır `gps.csv` başlığı olmalı; boş ZIP red. **Hepsi geçerliyse** yazılır: gün dosyaları `mergeLines(var olan, gelen)` (boş satırsız, sıra korunur, aynı satır bir kez, sonda `\n`), `restaurants.json` anahtar başına `n`'i büyük olan; her dosya `<ad>.restore`'a yazılıp yeniden adlandırılır. Sonra `OrderTracker.reloadImportedStats` (A3'te dolar). Herhangi bir hata (bellek dahil) → hiçbir kayıt değişmez (yazma aşamasına geçilmediyse), mesaj gösterilir, `importing = false`.
- Dışa aktarma ZIP'i, günlük paket, anonim dışa aktarma: A4.3 (R17, R18).
- **A2.6 için ekran (öneri):** "Daha fazla → Kayıtlar": ZIP içe aktar + gün listesi (tarih, dosya sayısı, boyut, arşiv). Rawstats'taki içe aktarma ve gün sayfasının (R12) ilk parçası; A3 R12 genişletir.
- **WEEM yerleşimi (A2.3):** saf `LogImporter.restore(input, filesDir, logsRoot, cacheDir)`; Android `LogImport` (arka plan iş parçacığı, `Throwable` yakalar, `importing` bayrağı) ve `RecordsScreen` (Daha fazla → Kayıtlar). Arşivleme/silme düğmeleri A3 (R12).

### 5.4 Göç işaretleri

- `manualPolicy` (Int): 1.1.
- `structuredMigration07` (Bool): `OrderTracker` ilk açılışta false ise `courier.db`'yi gün klasörlerindeki `orders.jsonl`'den yeniden kurar (`rebuildRecords`, RS `OrderTracker.kt:270-273,910+`), sonra true. **A3'te korunur**; Rawstats'ın üstüne kurulumda zaten true. `.dev`'de içe aktarmadan sonra `reloadImportedStats`.
- `userPicked` (Bool) + `targets` (StringSet): izlenen uygulamalar elle seçildi mi (R2, A3).

### 5.5 `courier.db` (RS `CourierDb.kt:10-45`)

- `SQLiteOpenHelper("courier.db", version 1)`; `onCreate` tam olarak:
  `CREATE TABLE trips(id TEXT PRIMARY KEY,pkg TEXT NOT NULL,day TEXT NOT NULL,payload TEXT NOT NULL)` · `CREATE INDEX trips_day ON trips(day)` · `CREATE TABLE offers(id TEXT PRIMARY KEY,pkg TEXT NOT NULL,day TEXT NOT NULL,result TEXT,payload TEXT NOT NULL)` · `CREATE INDEX offers_day ON offers(day)` · `CREATE TABLE session(k TEXT PRIMARY KEY,payload TEXT NOT NULL)`. `onUpgrade` boş.
- İşlemler (hepsi tek kilitte): `saveTrip(j)` / `saveOffer(j, result?)` = `id`, `pkg`, `day` alanları yüklemden, `payload = j.toString()`, çakışmada **REPLACE**; `trips(day?)` ve `offers(day)` `id` sırasıyla; `offer(id)`; `offerCount(day, result)` SQL'de sayar (yük ayrıştırılmaz); `session()` / `session(j)` tek satır `k='active'`; `batch {}` tek işlem; `clear()` üç tabloyu tek işlemde boşaltır.
- **WEEM:** ad, sürüm ve DDL aynı; DDL dizgileri `logic/core` `CourierSchema`'da sabit (birebir dizgi testi), Android `CourierDb` (`core/`) onları çalıştırır. Yeni tablo gerekirse sürüm 2 + gerçek göç (boş `onUpgrade` kalmaz). Müşteri telefonu yalnız burada (`withPhone=true`, `VERI_SOZLESMESI` 9).

### 5.6 `kuryelog` tercihleri

Tam liste (anahtar, tip, varsayılan, sahibi modül ve aşama) A2.1'de kaynaktan çıkarıldı ve `VERI_SOZLESMESI.md` 5.1'e yazıldı (tek kaynak). Aşama 2'nin anahtarları: vardiya (1.1), ev (2.1), `crashSeenAt` (4.1), yeni `weem.deviceId` (1.10).

## 6. Test eşlemesi (A2.2 bunu izler)

Yer: saf testler `logic/core/src/test/kotlin/com/kuryelog/logic/core/` (JVM, Android'siz; `org.json` test bağımlılığı). "Kaynak taraması" = eski test Rawstats kaynak dosyasında dizgi arıyor; WEEM'de **karar saf koda alındığı için** çoğu saf teste döner, kalanlar WEEM'in kendi Android kaynağını tarar (bileşen yazıldığı görevde).

**Durum (A2.2, 17 Ekim):** aşağıda "A2.2" yazan her test `logic/core/src/test/kotlin/com/kuryelog/logic/core/` altında; Rawstats testleri aynı adla, YolGo testleri `HomeDayRulesTest` (19 → 18 + 1 ek: sıçrayan çıkış fix'i), `HomeDayE6Test` (10), `HomeDayEndRuleTest` (5), `ShiftFollowerTest` (11 → 7 saf karar + 4 denetleyici senaryosu), `NotifyPolicyTest` (2), `HomePickResultTest` (6). Sınıflar eski koddan taşındı (testler yeşil: 126 test). **A2.4'ün eklemesi kalanlar:** `HomeShiftE6Test.watcherAndSettingsFollowTheRule`, `HomeEndRuleF45bTest.serviceTellsTheRuleWhetherAnOrderIsOpen`, `ShiftInterruptH4Test.serviceKeepsTheShiftWritesTheEventAndAsksForATap`, `home_day_controller_test` 1–3 (`ShiftMachine`). **A2.3:** `CrashGuardTest` (WEEM `Application`, sekme hata sınırı, `guarded`), `DailyRecordsTest.everyFileWrittenByTheAppIsInTheBackupList`. Çevirirken görülen küçük sapmalar: konum yardımcıları saniye yerine ms; `ShiftState.parse` Dart'taki gibi `startedAtMs/endedAtMs ≤ 0` değerini null sayar.

### 6.1 Rawstats (19 dosya, 113)

| Test (sayı) | WEEM | Görev | Not |
|---|---|---|---|
| `HomeShiftK38Test` (11) | saf, aynen | A2.2 → A2.4 | |
| `HomeShiftE6Test` (9) | 8 saf aynen; `watcherAndSettingsFollowTheRule` → WEEM izleyici kaynağı taraması (HIGH = GPS 2 sn, hız `hasSpeed` ise; ayar metni `START_RADIUS_M`'den) | A2.4 | |
| `HomeEndRuleF45bTest` (7) | 6 saf aynen; `serviceTellsTheRuleWhetherAnOrderIsOpen` → `ShiftMachine` saf testi (`busy` sağlayıcısı ev kuralına geçer) + A3'te `OrderEvents` bağlantısı | A2.4 | |
| `ShiftInterruptH4Test` (3) | 2 saf aynen; kaynak taraması → `ShiftMachine` saf testi (H4: olay, tercih, 5103, devamda `shift_resumed` + silme) | A2.4 | |
| `ShiftStateF41Test` (5) | `runningShift`, `endedShiftAndUnknowns`, `interruptedShiftIsVisible` saf aynen; `pathAndUri`, `providerGrantAndAnnouncement` **çevrilmez** (sağlayıcı kalkar, S7) | A2.2 | ShiftState v1 alanları 1.8 |
| `EndedShiftMinutesTest` (5) | saf aynen (`java.io`) | A2.2 → A2.4 | |
| `ShiftDistanceD8Test` (4) | saf aynen | A2.2 → A2.4 | |
| `HomeRepositoryTest` (7) | saf aynen (sahte `Storage`) | A2.2 → A2.4 | |
| `HomeDraftTest` (5), `HomeFixesTest` (4), `HomeFixRegressionTest` (2) | saf aynen | A2.2 → A2.4 | |
| `CrashReportTest` (7) | saf; bir beklenti WEEM'e uyarlanır: paylaşılan metin "WEEM …"; ilk kare deseni eski kareleri yine tanır, `com.kuryelog.logic` kareleri için ek test | A2.2 → A2.3 | 4.1 |
| `CrashGuardTest` (10) | `everyProcessStartInstallsTheCrashRecorderFirst`, `pagesAndTapsDoNotCloseTheApp` → A2.3 (WEEM `Application`, sekme hata sınırı); `gpsServiceStartFixAndStopAreGuarded`, `refusedForegroundStartStillReachesStartForeground` → A2.4 (vardiya servisi); `everyScreenReadGoesThroughTheGuard`, `accessibilityEventsAreGuarded`, `tickRecordsAFailureAndAlwaysRunsAgain`, `notificationListenerNeverThrows`, `screenServiceStartAndOcrCallbacksAreGuarded`, `gpsOrderStepsAreGuarded` → **A3** (bileşenleri orada). Ek saf test: `guarded` yakalar/kaydeder, `VirtualMachineError`'ı fırlatır | A2.3 / A2.4 / A3 | 4.2 |
| `DailyRecordsTest` (8) | saf aynen (`TemporaryFolder`); `everyFileWrittenByTheAppIsInTheBackupList` WEEM kaynağını tarar (`append(…, "<ad>")`) | A2.2 → A2.3 | |
| `RecordJsonTest` (6) | **`:orders`'a** (yolculuk/teklif tipleri orada) | A3 | |
| `AnalysisExportTest` (13), `DailyPackTest` (2), `ExportKeysParityTest` (1), `RedactorParityTest` (4) | `:core` modülü ama **A4.3** (R17, R18) | A4.3 | |

Aşama 2 toplamı: 79 test (+ yeni `guarded`, `NotificationIdsTest`, `ShiftMachine`, DDL testleri); A3: 12; A4.3: 20; çevrilmez: 2.

### 6.2 YolGo (Dart 6 + Kotlin 2 dosya)

"Aynen" = YolGo senaryosu ve beklentisi Rawstats kuralında aynı (1.9'daki koşuyla doğrulandı); `HomeDayRules` çağrıları `HomeShift`'e: `idle(f)` → `observe(f, false)`, `day(f, busy)` → `observe(f, true, busy)`, `dayEnded(manual: true)` → `onManualStop()`, `dayStarted()` (elle) → `onManualStart()`; konum dönüşümü YolGo yardımcısındaki gibi.

| Test (sayı) | WEEM | Görev |
|---|---|---|
| `home_day_rules_test` (19) | **aynen 8:** "50 m ötede iki tutarlı…", "10 km/s sınırın altında…", "GPS hız vermezse (0)…", "dışarıda dururken GPS titremesi…", "başlangıçta doğruluk >20 m ve sıra dışı zaman…", "evde elle bitirince…", "dışarıda elle bitirince…", "eve dönünce otomatik bitince…". **Fark 1:** "sıçrayan fix…" → beklenti 1.9 (a: hiç başlamaz; b: 31. sn'de başlar). **F4.5b'ye ölçeklenir 8** (`legacy()` grubu 200 m/10 km/s/2 dk ile parametreliydi; WEEM kuralı parametresiz): konumlar 150→20 m, giriş hızı 6→4 km/s, 2 dk→30 sn; beklentiler: "tam 120. sn" → ilk `end` giriş fix'inden 30 sn sonra (dizin 2+30); "günü evde başlatıp…" (40 m) → hiç; "tek sapan fix" (70 m, doğruluk 30) → **kurar**, 40 m'de 30 sn sonra `end` (doğruluk 31–35 ile kurmaz); "10 m'yi aşan kayma" (10,5 m) → hiç; "bölgeye ≥10 km/s" (≥5 km/s'ye) → yavaş ilk fix'ten 30 sn sonra `end` (dizin 30); "süren siparişte…" → hiç (ayar kısmı çevrilmez); "dışarıya çıkınca pencere iptal" → gerçekçi çıkışla (380 m/60 sn) dönüşün ilk fix'inden 30 sn sonra `end` (dizin 30), 2 sn'lik sıçramayla ayrıca dönüşteki ilk fix'te `end` (fark, 1.9); "yeni gün eski günün kurulumunu taşımaz" → yeni vardiya `onManualStart` sonrası 20 m'de hiç. **Çevrilmez 1:** "ayar kapalıysa başlamaz" (ayar yok). **Uyarlanır 1:** "kalıcı durum koordinat içermez…" → `home.startBlocked`/`home.endArmed` iki bool, koordinat yok; ev değişince koruyucular düşer (`HomeRepository.set`) | A2.2 → A2.4 |
| `home_day_e6_test` (10) | aynen 9; **fark 1:** "elle bitirdikten sonra 50–200 m içinde…" → hepsi `none`, `startBlocked == true` | A2.2 → A2.4 |
| `home_day_end_rule_test` (5) | "varsayılanlar" → `HomeShift` sabitleri; "29 sn yetmez…", "60 m ışık", "sipariş açıkken" aynen; **fark 1:** "50 m içinde 30 sn dur" → ilk `end` **36. sn** | A2.2 → A2.4 |
| `home_day_controller_test` (5) | 1–2 `ShiftMachine` saf testi (neden `home_departure`/`manual`/`home_arrival`, elle bitirince evde yeniden başlamaz, 30 sn sonra `home_arrival`); 3'ün "ev yoksa başlamaz" kısmı aynı (ayar kısmı çevrilmez); 4 (feed aşamaları) ve 5 (gün istatistiği nedeni metni) → **`:map` A6** | A2.4 / A6 |
| `rawstats_shift_end_test` (11) | **uyarlanır:** saf `ShiftFollower` (`logic/core`): kendi "gün"ünü tutan modül (`:map` gün motoru) `ShiftState`'i izler — kapalı vardiya + işlenmemiş `endedAtMs` (gün başından önce değil) → günü o anla bitir; açık vardiya + kapalı gün + işlenmemiş `startedAtMs` → o anla başlat; gün açıkken görülen başlangıç "işlendi" sayılır (elle bitirilen gün aynı vardiyada yeniden açılmaz); her damga bir kez; bozuk/başka şema ya da okuma yok → hiçbir şey | A2.2 → A2.5 |
| `shift_engine_test` (9) | **`:map`'e** (YolGo gün motoru: km, duruş, ışık, sipariş aşaması; Y11/Y17/Y18) | A6 |
| `native_telemetry_test` (1) | **çevrilmez** (Flutter kanal yarışı); ilkesi `ShiftMachine` testinde: kapanmış vardiyaya geç gelen eski başlatma/yapılandırma isteği vardiyayı açmaz | A2.4 |
| `NotifyPolicyTest` (2, Kotlin) | saf aynen | A2.2 → A2.4 |
| `TelemetryJournalTest` (8, Kotlin) | **`:map`'e** (YolGo yerel servis → Flutter tamponu; WEEM'de köprü yok; Y18 kayıt yazıcısı aynı sınırlı günlüğü kullanırsa çevrilir, A6.1 karar verir) | A6 |
| `rawstats_home_sync_test` (13), `rawstats_home_stale_test` (6) | **çevrilmez** (tek ev, S7; korunacak tek ilke "başarısız okuma evi silmez" `HomeRepositoryTest.invalidSaveDoesNotEraseAValidHome`'da) | — |
| `HomePickContractTest` (6, Rawstats, uyarlanır) | saf `HomePickResult.parse(ok, lat: Any?, lon: Any?)`: yalnız `Double`, sayıya/dizgiye zorlama yok, sonlu ve aralıkta, 0 ve sınırlar geçerli, iptal hiçbir şey seçmez; sonuç doğruluğu bilinmeyen taslak. `:map` seçicisi (A6) bunu kullanır | A2.2 → A2.4 |

`TEST_ENVANTERI.md` hedef sütunu buna göre güncellendi (Bölüm 7).

## 7. Açık noktalar ve riskler

- **Kullanıcıya sorulacak (engel değil):** (1) İki km ölçüsü (Rawstats D8 `shift_end.km` ve YolGo gün km'si) bir ekranda yan yana görünecek (Y17 istatistik, R12 gün sayfası); hangisi gösterilsin ya da ikisi mi — A6.1'de sorulur, o zamana kadar ikisi de eski yerinde. (2) 1.9'daki farklar S2 kararının sonucu; saha denemesinde (K0.1, gölge mod) "evde tek sapan fix vardiyayı kurdu" gibi bir bulgu çıkarsa kural kullanıcı kararıyla değişir.
- **Doğrulanmadı:** ev izleyicinin ve H4'ün telefonda davranışı (Rawstats'ta da "telefonda denenmedi" notu, `HomeShiftE6Test.kt:107`); 1.3'teki Android `Location.distanceTo` ile saf haversine farkı (A2.4 seçimi, gölge modda denetlenir).
- **Bilerek küçük farklar:** bildirim numaraları 5103→5104 (ekran okuma) ve 4000+→10000+ (nakit bahşiş); `runCatching` → `guarded` (bellek hatası artık çökme raporu); çökme metninde "Rawstats" → "WEEM". Hepsi kullanıcıya görünmez ya da yalnız metin.
- `VERI_SOZLESMESI.md` düzeltildi: `manualPolicy` Int, `home.*` lat/lon String, `home.ui.*` tercih değil, eksik anahtarlar (5.6), 5103 ve bahşiş için WEEM numaraları (8.2).
- `bulgular/R_core_contract.md` satır numaralarının bir kısmı yanlıştı (ör. `HomePreferences.kt:54-74`, dosya 28 satır); bu belge doğrularını verir.
