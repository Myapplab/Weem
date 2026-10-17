# CAPTURE — Aşama 3 şartnamesi (`:capture`: R1, R2, R16; R3 alanları; R19 ekran okuma uyarısı)

Durum: A3.1, 17 Ekim 2026 (Claude Opus · max, tek ajan; S14) — **yazılıyor, bölüm bölüm**. Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04` (Rawstats 0.25.1, versionCode 33); satır numaraları bu commit'te açılıp doğrulandı. `bulgular/R_capture.md`'nin yerini alır (oradaki düzeltilen kalemler 9. bölümde).

Kısaltmalar: **RS** = `app/src/main/java/com/kuryelog/app/`, **RT** = `app/src/test/java/com/kuryelog/app/`, **RR** = `app/src/test/resources/` (rawstats). Davranış metni kodun söylediğidir; "WEEM" satırları bu şartnamenin kararıdır.

**Kim kullanır:** A3.2 (testleri çevir: Bölüm 8), A3.4 (`logic/capture`, ağ yasağı ve ICU testleri: Bölüm 1–7), A3.7 (Android servisleri, pencereler: Bölüm 1, 3, 4, 5, 7), A3.9 (gölge mod: Bölüm 6). Sipariş tarafı (teklif yaşam döngüsü, sipariş durum makinesi, rozet, balonlar, nakit, boost, resmî değerler): `ORDERS.md`. Plan bağlamı: `WEEM_PLANI.md` 4.2–4.4, 5, 8 (S5, S7, S9).

## 0. Özet: WEEM kuralları

| Konu | WEEM kuralı | Neden / kaynak |
|---|---|---|
| Ne zaman okunur | Yalnız kayıt izni varken: `ManualSessionPolicy.canCapture(shift, manualSession, importing)` (A2.4, `logic/core`). Vardiya dışında hiçbir satır yazılmaz ve erişilebilirlik olayları yalnız WEEM'in kendi paketinden gelir (pil) | RS `Store.kt:16`, `WatchService.kt:69, 332-346` |
| Hangi uygulama | Tercih `targets` (StringSet, varsayılan boş). `userPicked` false iken uygulama açılışında başlatılabilir Wolt/Bolt kurye uygulamaları listeye **eklenir** (çıkarılmaz); elle seçim `userPicked=true`. Kodda sabit hedef listesi yok; Wolt'a özgü kurallar `com.wolt.courierapp`, `com.wolt.partner` paketlerine bağlı | RS `MainActivity.kt:159, 1428-1462`, `CourierParser.kt:125` |
| Nasıl okunur | Erişilebilirlik ağacı + (API ≥ 30 ve `ocr` açık) ML Kit OCR; tempo `OcrPace` (D6) ve teklif yeniden okuma (K37) **aynen** | RS `WatchService.kt`, `OfferCapture.kt` |
| Basmaz | `canPerformGestures` yok; kaynakta `performAction`, `dispatchGesture`, `performGlobalAction` yok (test, 7.4) | Plan Bölüm 5 |
| Ağsız | `:capture` (saf ve Android) hiçbir ağ sınıfı kullanmaz; çalışma sınıf yolu izin listesiyle denetlenir; ML Kit'in telemetri arka ucu çıkarılır (7.3) | Plan 4.2, 5; S: "ekran okuma kısmı ağsız" (10 Ekim) |
| Desenler | Bütün modüllerde Android'in ICU'sunun reddettiği desen yapıları yasak (`(?U)`, `UNICODE_CHARACTER_CLASS`, `CANON_EQ`, `\p{java…}`); kaynak taraması testi | 0.25.0 çökmesi (RS `NavDistance.kt:16-19`, RT `IcuRegexTest`) |
| Kayıt | `screens.jsonl`, `notifs.jsonl`, `events.jsonl` (`click`, `screenshot_unavailable`, `screen_reading_off/on`), `errors.jsonl`: ad, alan, anlam aynı (6). Müşteri metni yazılmadan önce `NoteFields.redact` (telefon → `[tel]`) | `VERI_SOZLESMESI` 7.1, 9 |
| Telefon numarası | Telefon (arama) uygulaması yalnız `:orders` "numara bekleniyor" derken okunur; okunan hiçbir şey diske yazılmaz, numara `:orders`'a bellekte gider | RS `WatchService.kt:71-75, 119-126` |
| Navigasyon bildirimi (R16) | Google Maps, Waze, Yandex, 2GIS, HERE bildiriminden yalnız "süre · mesafe" özet satırı; bellekte, diske yazılmaz. WEEM'in kendi rotası `NavPort.route`'tan gelir (`ORDERS.md`) | RS `NavDistance.kt`, `NotifService.kt:28-35` |
| Uyarı (R19, E7) | Vardiyada ekran okuma 10 sn kesintisiz kapalıysa bildirim **5104** (`NotificationIds.SCREEN_READING_OFF`, kanal `screen_reading`; eskiden 5103) + izin varsa kırmızı rozet; açılınca ya da vardiya bitince kalkar | RS `ScreenReading.kt`, `GpsService.kt:117-155`; `CORE.md` 3.3 |
| Modül sınırı | `:capture` → yalnız `:core`. Ayrıştırıcının bugün sipariş tarafından aldığı yardımcılar (`OverlayText.isOwn/withoutOwn`, `CustomerMatch.sameName/looksLikeAddress`, `NoteFields`, `Dialer`) `:capture`'a taşınır; `:orders` onları buradan kullanır. Ekran ve bildirim olayları `:orders`'a `CaptureSink` ile gider (`:capture`'ta tanımlı, `:orders` uygular, `:app` bağlar; 7.1) | Plan 4.2 (izinli yön `:orders` → `:capture`) |
| Ölü kod | `MotionGate` (vardiya servisi besler, hiçbir karar okumaz) ve `OfferIdentity` (yalnız testte) yazılmaz; testleri çevrilmez (8) | RS `RecordingPolicy.kt:18-62, 83-97`; `rg` ile tarandı |

## 1. R1 — Ekran okuma servisi (`WatchService`)

### 1.1 Yapılandırma (RS `res/xml/accessibility_config.xml:1-11`)

- Olaylar `typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeWindowsChanged|typeNotificationStateChanged`; bayraklar `flagRetrieveInteractiveWindows|flagReportViewIds|flagIncludeNotImportantViews`; `canRetrieveWindowContent`, `canTakeScreenshot` (API 30'dan; 29'da yok sayılır, OCR da kapalı); `notificationTimeout=0`; `feedbackGeneric`. **`canPerformGestures` yok.**
- WEEM: `capture/src/main/res/xml/accessibility_config.xml` A1.2'de birebir yazıldı; değişmez. Etiket "WEEM ekran kaydı", açıklama `acc_desc` Rawstats sözüyle aynı (ad WEEM).

### 1.2 Bağlanma ve kapanma (RS `WatchService.kt:56-62, 402-408`)

- `onServiceConnected`: `Store.ensureManualMode` + `OrderTracker.init` korumalı (hata kaynağı `screen_start`, fırlatılmaz: fırlatılırsa Android servisi kapalı bırakır); `connected = true`, `running = true` (E7, 5.2), `DeliveryReminder` kurulur (hata → yok), tick başlar.
- `onDestroy`: `connected = false`, `running = false`, bekleyen işler silinir, OCR hazırlık iş parçacığı kapatılır, rozet ve hatırlatıcı pencereleri kaldırılır, OCR tanıyıcı kapatılır. `onInterrupt` boş.
- WEEM: `running` → `:capture` `CaptureState.connected` (`@Volatile`; R19 bunu okur). `OrderTracker.init` ve hatırlatıcı kurulumu `CaptureHost.onConnected(service)` ile `:orders`'a geçer (7.1).

### 1.3 Olay işleme (`onEvent`, RS `WatchService.kt:65-105`)

Her olay korumalıdır (kaynak `event`). Sıra:

1. Kayıt izni yoksa dön.
2. **Telefon uygulaması** (`Dialer.isDialer(pkg)`: `com.android.dialer`, `com.google.android.dialer`, `com.samsung.android.dialer`, `com.samsung.android.incallui`, `com.android.incallui`, `com.android.contacts`, `com.android.server.telecom`, `com.oneplus.dialer`, `com.miui.contacts`, `com.huawei.contacts` ya da `.dialer`/`.incallui` ile biten; RS `Dialer.kt:3-6`) **ve** `OrderTracker.awaitingPhone(now)`: okuma zamanlanmamışsa hemen bir okuma + 180 ms sonra ikinci okuma; dön.
3. Paket `targets`'ta değilse dön.
4. `TYPE_VIEW_CLICKED`: kaynak düğümden başlayarak derinlik ≤ 3 gezilir, her düğümün boş olmayan `text` ve `contentDescription`'ı etiket olur (etiket sayısı 25'i aşınca gezinti durur; sınır kesin değil, son düğüm iki etiket ekleyebilir); sonra olayın `text` öğeleri ve `contentDescription`'ı eklenir. Tekilleştirilmiş etiketler boşlukla birleşip boş değilse: `events.jsonl` `type=click {pkg, text}` ve **her** tekil etiket için `OrderTracker.onClick(pkg, etiket, now)`. Olay burada bitmez: 7. adıma düşer.
5. `TYPE_NOTIFICATION_STATE_CHANGED`: `OrderTracker.notification(pkg, olay metinleri "\n" ile, at)`, `OcrPace.trigger(pkg, at)`, okuma zamanlanmamışsa hemen bir okuma (ikinci okuma yok); dön.
6. `TYPE_WINDOW_STATE_CHANGED`: `OcrPace.trigger(pkg, now)`.
7. Okuma zamanlanmamışsa: hemen bir okuma + 180 ms sonra ikinci okuma (izin hâlâ varsa).

"Zamanlanmış" bayrağı ilk okuma çalışınca iner; 180 ms okuması ve K37 yeniden okuması bayrağa dokunmaz. WEEM: 180 ms `CaptureTiming.SECOND_READ_MS` sabiti (`logic/capture`); Android katmanında düz sayı yok.

### 1.4 Ekran okuma (`snapshot`, RS `WatchService.kt:106-155`)

- Her okuma korumalıdır (kaynak `screen`; 180 ms ve K37 okumaları dahil — eskiden korumasızdı ve tek bozuk kare uygulamayı kapatıyordu, `:107-109`). Servis bağlı değilse ya da izin yoksa dön.
- Pencereler: `windows` içinde yalnız `TYPE_APPLICATION`; kökü ya da paketi olmayan atlanır.
  - Telefon uygulaması penceresi: `awaitingPhone(now)` iken metinleri aynı gezintiyle toplanır → `OrderTracker.dialerPhone(metinler, now)`; **hiçbir şey kaydedilmez**; pencere sonra atlanır.
  - `targets` dışındaki atlanır.
- **Gezinti** (`walk`, `:178-195`): derinlik > 50, düğüm satırı sayısı > 4000 ya da `!isVisibleToUser` → o dal biter; düğümün ekran sınırı pencere sınırıyla kesişmiyorsa atlanır (alt dalı da). `actionable = isEnabled && (isClickable || ebeveyn actionable)`. Değerler = boş olmayan `text` ve `contentDescription` (tekil): hepsi metinlere, `actionable` ise eylemlere. Değeri olan düğüm satırı `[text, contentDescription, viewIdResourceName, className, bounds.flattenToString(), actionable]`. Yalnız Wolt paketlerinde (`CourierParser.WOLT`): sınıfı `ProgressBar` olan düğümlerin kutuları ve teslim etiketi (`CourierParser.isDeliver`) taşıyan düğümlerin kutuları ayrıca toplanır (K33, 2.8), `isCheckable` düğümlerin `isChecked` değeri toplanır (M16).
- Pencere okuması: `id`, `pkg`, `bounds`, `texts.distinct()`, `actions.distinct()`, düğüm satırları, `spinner` = Wolt ∧ `SliderSpinner.onSlider(kutular, etiketler)`, `checked` = Wolt'ta (işaretlenebilir düğüm yoksa null, varsa herhangi biri işaretli mi), diğerlerinde null. **Metni boş pencere de listede kalır** (Wolt'un harita/teklif/görev ekranlarında erişilebilirlik metni yok; OCR onları okur, 3).
- Metni olan her izinli pencere `accept(..., "accessibility")` (1.5). Bir kare OFFER ise "teklif ekranda"; `OfferCapture.reread(gözlem, yapılanEkOkuma)` (OFFER ∧ ücret yok ∧ ek okuma < 10) ise "ücret eksik".
- **K37:** ekranda teklif yoksa ek okuma sayacı 0'lanır; "ücret eksik" ve ek okuma zamanlanmamışsa sayaç +1 ve `REREAD_MS` = 100 ms sonra yeni okuma.
- **OCR:** API ≥ 30, OCR meşgul değil, tercih `ocr` (varsayılan true) → `OcrPace.retain(bu okumadaki pencere id'leri)`; `OcrPace.next(...)` bir pencere seçerse onun ekran görüntüsü (3).

### 1.5 Kaydetme (`accept`, RS `WatchService.kt:156-171`)

1. İzin yoksa hiçbir şey.
2. Tanı: `seen[pkg] = at`; eylemlerden biri `isAccept` ise `seenAccept[pkg] = at` (izlenen uygulamalar kartı, 1.9).
3. `CourierParser.parse(pkg, metinler, eylemler, at, kaynak)` (2).
4. `ScreenDeduper.shouldSave(pkg + kaynak, metinler)` (2.9) true ise `screens.jsonl` satırı (alanlar 6.1). Ekran sayacı +1.
5. Her durumda (kaydedilmese de) `OrderTracker.onScreen(pkg, metinler, eylemler, at, kaynak, spinner = (accessibility ∧ spinner), parsed = gözlem, checked = accessibility ise checked, değilse null)`; gözlem döner.

### 1.6 Tik (RS `WatchService.kt:301-331`)

- Servis bağlı değilse tik durur (yeniden kurulmaz).
- Adımlar (her biri tek `try` içinde; hata `tick`, `LinkageError` da yakalanır — 0.25.0'da `NavDistance` sınıfı yüklenemiyordu, `:323-325`; sonraki tik **her zaman** kurulur):
  1. `HomeWatch.refresh` (ev izleyici; kendi 5 sn sınırı var, `CORE.md` 1.7).
  2. `shift = Store.shiftOn`; `syncEventScope(kayıt izni)` (1.7).
  3. Vardiyada: `OrderTracker.tick(now)`; `ScooterLink.publish` (E8, YolGo'ya); okuma zamanlanmamışsa **bir okuma** (yani vardiyada ekran en geç her tikte okunur); tercih `overlay` (varsayılan true) ise rozet yoksa eklenir ve güncellenir, değilse kaldırılır; `DeliveryReminder.update(OrderTracker.reminder)`.
  4. Vardiya dışında: rozet kaldırılır, hatırlatıcılar `update(null)`.
- Sonraki tik: vardiyada **500 ms**, dışında **2000 ms** (0.22.2 pil).
- WEEM: `ScooterLink.publish` yazılmaz (YolGo içeride; scooter durumu `ScooterBridge.live` akışında). 3. ve 4. adımların sipariş işleri `CaptureHost.onTick(service, now, shiftOn)` ile `:orders`'a gider (7.1). Süreler `CaptureTiming.TICK_SHIFT_MS` / `TICK_IDLE_MS`.

### 1.7 Olay kapsamı (`syncEventScope`, RS `WatchService.kt:332-346`)

- Kayıt izni varken `serviceInfo.packageNames = null` (tüm uygulamalar), yokken `[kendi paketi]` (vardiya dışında başka uygulamaların her içerik değişimi servisi uyandırmasın). Yalnız değişince yazılır; hata `event_scope`.
- WEEM: aynı; kendi paketi `.dev` yapıda `com.kuryelog.app.dev`.

### 1.8 Hata kaynakları ve son hata

| Kaynak | Nerede | Satır |
|---|---|---|
| `screen_start` | bağlanma (`ensureManualMode`, sipariş tarafının açılışı) | `WatchService.kt:59` |
| `event` | her erişilebilirlik olayı | `:66` |
| `screen` | her ekran okuması (ilk, 180 ms, K37) | `:109` |
| `ocr` | ekran görüntüsü geri çağrısı, kırpma/ölçek, ML Kit sonucu | `:205, 220, 257` |
| `tick` | tik adımları | `:322, 325` |
| `event_scope` | olay kapsamı | `:345` |
| `notification` | bildirim dinleyici (4) | `NotifService.kt:25, 67` |

- Her hata `errors.jsonl` `{t, source, error = "<kaynak>: <SınıfınKısaAdı>: <mesaj>"}` ve süreç içi "son hata" (`Store.failure`, RS `Store.kt:28-31`; WEEM `LogStore.failure`, A2.3).
- Kullanıcıya görünen son hata metinleri: ekran görüntüsü alınamadı → "OCR ekranı okunamadı (kod N); erişilebilirlik okumaları sürüyor." / "OCR ekranı oxumadı (kod N); əlçatanlıq oxunuşları davam edir." (`:230`); rozet açılamadı → "Rozet açılamadı: " / "Nişan açılmadı: " + mesaj (`:364`).
- WEEM: koruma `Store.guarded(ctx, kaynak)` (A2.3). Fark (bilerek): WEEM `guarded` `VirtualMachineError` dışındaki her `Throwable`'ı yakalar; Rawstats tiki yalnız `Exception` + `LinkageError` yakalıyordu (başka `Error` süreci kapatırdı). Metinler `:capture` metin tablosunda (TR/AZ, `TextCoverageTest` kapsamında).

### 1.9 İzlenen uygulamalar (RS `MainActivity.kt:159, 1170-1185, 1428-1462`; manifest `<queries>`)

- **Kendiliğinden ekleme** (`autoDetectTargets`, her uygulama açılışında, korumalı `targets`): `userPicked` true ise hiçbir şey. Değilse başlatılabilir uygulamalardan (`ACTION_MAIN` + `CATEGORY_LAUNCHER`, kendi paketi hariç) `(paket + " " + etiket).lowercase(Locale.ROOT)` içinde `wolt` ∧ (`partner` ∨ `courier` ∨ `kuryer`) ya da (`bolt` ∨ `mtakso`) ∧ (`courier` ∨ `food` ∨ `driver` ∨ `kuryer` ∨ `partner`) geçenler mevcut kümeye **eklenir**; küme büyüdüyse yazılır.
- **Elle seçim** (`pickApps`): tüm başlatılabilir uygulamalar; kurye benzerleri (`wolt|bolt|mtakso|courier|kurye|kuryer`) önce, sonra etikete göre; çoklu seçim; Kaydet → `targets` + `userPicked = true`.
- **Tanı kartı:** her hedef için etiket (yoksa paket), son okuma saati (yoksa "hiç"), görülen teklif sayısı, son "Accept" görülme saati; hedef yoksa kart kırmızı ve "yok".
- Manifest: `<queries>` içinde `ACTION_MAIN`/`CATEGORY_LAUNCHER` (Android 11+ paket görünürlüğü). Rawstats'taki `az.duzyol.app` sorgusu WEEM'de yok (S7: `PICK_HOME` kalktı).
- WEEM: eşleştirme kuralları saf `TargetApps.autoDetected(paket, etiket)` ve `TargetApps.courierLike(paket, etiket)` (`logic/capture`); liste ve kart `:app` Daha fazla → "İzlenen uygulamalar" (A3.7); kendiliğinden ekleme uygulama açılışında (`WeemApplication`/kabuk), Rawstats gibi.

## 2. R1/R3 — Ayrıştırıcı (`CourierParser`, `CourierScreens`, `OfferRules`, `ScreenDeduper`, `SliderSpinner`, `NoteFields`)

**Taşıma kuralı (WEEM):** ayrıştırıcı Kotlin'den Kotlin'e **satır satır kopyalanır** (RS `CourierParser.kt:1-552`, `CourierScreens.kt`, `RecordingPolicy.kt:64-80, 99-111`, `SliderSpinner.kt`, `NoteFields.kt`, `Dialer.kt`, `OverlayText.kt` tanıma kısmı, `DeliveryCore.kt:900-918` ad/adres eşleştirme). Değişen yalnız paket (`com.kuryelog.logic.capture`) ve 7.1'deki taşınan yardımcıların adlarıdır; her başka değişiklik bu belgede bir "WEEM" satırı ister. Gerekçe: gölge mod (A3.9) aynı kareden aynı gözlemi bekler; bu bölüm kuralların okunur özeti ve gözden geçirme listesidir, çelişkide kaynak kod ve testler kazanır.

### 2.1 Gözlem tipi (RS `CourierParser.kt:6-119`)

- `ScreenStage` = `OFFER | PICKUP | DROPOFF | IDLE | OFFLINE | UNKNOWN`. Görünmeyen/kaybolan pencere asla IDLE değildir (`:6`).
- `CourierObservation` alanları: `pkg, at, stage, complete, source, raw (= lines), actions (= taps)`; ücret ve rota `gross, km, minLo, minHi, stops, quantity`; yerler `restaurant, restaurantAddress, customerAddress, orderIds, customer: DeliveryDetails`; saatler `readyIn, pickupClock, dropoffClock, travelEta`; durum `arrived, ready, delivered, deliverControl, preparing`; platform `demand, bonusPct, bonusFrom, multipliers, offlineUntil, availability, availabilityEvidence, declineDialog, declinedCount`; alış `restaurantNote, pickupCode, items`; Bolt bacakları `approachMin, deliveryMin`; boost `boostPct, boostWindow, boostStartsInMin, boostSlots: List<BoostSlot>`; istatistik `statsEarningsAzn, statsTasksDone, statsCompletionRate, statsAcceptanceRate, statsRating, statsPeriodLabel, statsPeriod, statsDistanceKm, statsEarningsNoTipsAzn, statsTipsAzn`; teklif nakdi `offerCash, changeNeeded`.
- **Ayrıştırıcının hiç doldurmadığı** alanlar (sonra `copy` ile): `deliverSpinner` (K33, okuyucu, 1.5), `cashOrder, cashAmount, cashStepsSeen, cashChecked, cashChangeAzn` (K31/M16, `:orders` `CashPayment.attach`). `complete` yalnız parametredir (varsayılan true; üretim kodunda false veren yok, testler verir).
- `BoostSlot(pct, window, from, active, date)` (`:116-119`).
- WEEM: `CourierObservation`, `ScreenStage`, `DeliveryDetails`, `BoostSlot` `logic/capture`'ta; `:orders` bunları "gözlem tipleri" istisnasıyla görür (plan 4.2). Alan adları değişmez (`RecordJson.observation` bunları JSON'a yazar, `ORDERS.md`).

### 2.2 Normalleştirme ve giriş (RS `CourierParser.kt:122-124, 171-178`)

- `norm(s)`: `lowercase(Locale.ROOT)` → `ə→e`, `ı→i`, `ё→е` → NFD → `\p{M}+` silinir → `\s+` tek boşluk → kırp. ("Qəbul" → "qebul", "Çatdırıldı" → "catdirildi", "İ" → "i".)
- Girişin hazırlanışı: her metin satırlara bölünür, kırpılır, boşlar ve **WEEM'in kendi rozet satırları** (`OverlayText.isOwn`, 2.10) atılır → `shown` (tekrarlı, ekran sırası); `lines = shown.distinct()`; `ns = lines.map(norm)`; `all = ns` satır sonuyla birleşik; eylemler (`taps`) satırlara **bölünmeden** yalnız kendi satırlardan arınır; `acts = taps.map(norm)`.
- Tekrarlı `shown` yalnız iki yerde kullanılır: Bolt bacak süreleri (iki eşit bacak kalsın, 2.6) ve boost dilimleri (M21: aynı tarih/yüzde birkaç satırda, 2.7).

### 2.3 Söz dağarcığı (hepsi `norm` sonrası karşılaştırılır; RS satırları)

| Ad | Değerler | Satır |
|---|---|---|
| Wolt paketleri | `com.wolt.courierapp`, `com.wolt.partner` | `:125` |
| Çevrimiçi ol | `go online`, `slide to go online`, `cevrim ici ol` | `:126` |
| Çevrimdışı ol | `go offline`, `slide to go offline` | `:127` |
| Ret afişi | `^(\d+) (deliveries\|delivery\|orders\|order\|tasks\|task\|offers\|offer) declined$` | `:128` |
| Satır etiketleri | `entrance, giris` → giriş; `floor, kat, mertebe` → kat; `apartment, flat, daire, menzil` → daire; `door code, entrance code` → kapı | `:129-130` |
| Kabul | `OfferRules.isAccept` sözlüğü (`accept`, `accept order`, `accept delivery`, `qəbul`, `qəbul et`, `qəbul etmək`, `kabul`, `kabul et`, `принять`, `принять заказ`; sondaki `(12s)`/`[20 sec]` sayacı atılır; `OfferRules` kendi `norm`'unu kullanır: kırp, küçült, boşluk — aksan **silinmez**) **ya da** tutar + `\b\d+\s*(min\|deq\|dk)\b` taşıyan etiket ("2.21 AZN · 22 min") | `RecordingPolicy.kt:64-75`; `:153-156` |
| Ret | `decline`, `decline delivery`, `reject`, `reddet`, `redd et`, `imtina`, `imtina et`, `imtina edin`, `отклонить` | `:157` |
| Alış (Collect) | `slide to confirm` silinince: `pick up`, `pickup`, `collect`, `confirm pickup`, `tehvilalmani tamamla`, `teslim al`, `siparisi al`, `goturdum`, `tehvil al`, `забрать`, `получить заказ` | `:158-161` |
| Teslim | `slide to confirm` silinip sondaki `! . …` atılınca: `delivered`, `order delivered`, `complete delivery`, `finish delivery`, `confirm delivery`, `catdirildi`, `teslim edildi`, `teslimati tamamla`, `tehvilvermeni tamamla`, `доставлено`, `завершить доставку` | `:162` |
| Arama düğmesi | içinde `support, destek, restaurant, restoran, venue, поддерж, ресторан` geçmeyen ve `(^\|\s)(call\|phone\|zeng\|zeng et\|ara\|позвонить\|звонок)(\s\|$)` taşıyan | `:164-168` |
| Vardım | `slide to confirm` silinince: `arrived`, `i have arrived`, `catdim`, `vardim`, `на месте` | `:169` |
| Geçmiş sayfası | `history`, `order history`, `delivery history`, `completed orders`, `past orders`, `geçmiş`, `sipariş geçmişi`, `tamamlanan siparişler`, `tarixçə`, `sifariş tarixçəsi`, `история`, `история заказов`, `завершённые заказы`, `завершенные заказы` (`OfferRules` normu) | `RecordingPolicy.kt:68-77` |
| Adres etiketleri | `address, unvan, adres, адрес, delivery address, drop-off address` | `:149` |
| Bölüm başlıkları (`sections`) | adres etiketleri + `instructions, additional note, note, notes, floor, apartment, flat, door code, entrance code, building name, building, entrance, block, unit, kat, daire, mertebe, bina, giris, qapi kodu, menzil, telimatlar, qeyd, elave qeyd, этаж, квартира, подъезд, код двери, инструкции, комментарий, call, chat, translate, view, delivery issues?, get help, arrived, pick up, collect, complete delivery, slide to confirm, customer drop-off, pickup from, dropoff to, orders to pick up:, address details, order details, difficulties with the delivery?, send a message, info, items` | `:150` |
| Ücret olmayan satır (`notFee`) | `cash\|balance\|basket\|nakit\|balans\|negd\|nagd\|наличн` içeren | `:145` |

Tutar (`amount`, `:132, 152`): `₼ 2.61` ya da `2.61 ₼/AZN/manat` (büyük/küçük harf duyarsız); ondalık `.` ya da `,`, en çok 2 hane; başka rakama yapışık sayı tutar değildir ("4.499 AZN" asla 499 değil, R04). Wolt tutar satırı (`woltAzn`, `:135-136`): tam satır `AZN 2.61`; OCR'ın `:` ondalığı kabul ("AZN 2:13" → 2.13).

### 2.4 Ücret ve kabul (RS `CourierParser.kt:179-200`)

1. **Wolt kartı** (`woltCard`): Wolt paketinde `expected earn` ile başlayan satır var. Ücret = o satırdan **hemen önceki** satır `woltAzn` ise o tutar; değilse yok. Sonrasındaki tutarlar (para üstü "AZN 45.75 | Total change needed") asla ücret değildir; kart kayarken gelen ilk karelerde bu satır olmadığından ücret yoktur (D1/D21).
2. Wolt kartı değilse sırayla: kabul eylemlerindeki **tek** farklı tutar → kazanç sözcüklü (`earning|courier fee|estimated fee|kazanc|qazanc|доход`, `notFee` değil) satırlardaki tek tutar → `notFee` olmayan satırlardaki tüm tutarlardan tek olan → yok. Birden çok aday = ücret yok.
3. `accept` = bir eylem kabul **ya da** (metinlerde hem kabul hem ret) **ya da** Wolt kartı.

### 2.5 Aşama (RS `CourierParser.kt:201-257`)

Önce bayraklar (`labels` = `ns`'in baş/sonundaki `> › < ‹ → ! .` ve boşluk atılmış hâli):

- `goOnline` / `goOffline`: bir etiket çevrimiçi/çevrimdışı ol sözlüğünde.
- `woltFeedback` (Wolt): `submit feedback` **ve** (`how was your collaboration with the venue?` ∨ `was everything ok with wolt support?`) — "Go offline" kaydırıldıktan hemen sonra gelen sayfa.
- `offline` = `goOnline` ∨ `unable to go online` ile başlayan ∨ `set offline until` içeren satır.
- `declineDialog` = `decline offer?` ∨ `decline offer` ∨ `decline this delivery` ile başlayan satır (Wolt 2.202.6 onay sayfası).
- `declinedCount` = ret afişindeki sayı.
- `multiDrop` (Wolt) = `^(\d+) drop ?offs to$` satırı (R1, aynı müşteriye çoklu sipariş).
- `deliveryPanel` = `multiDrop` ∨ `dropoff to` ∨ `drop off to` ∨ `delivery timeline` ∨ `address details`.
- `slider` = `slide to confirm` içeren satır.
- `deliveryControl` = bir eylem teslim ∨ (Wolt ∧ `deliveryPanel` ∧ bir satır teslim) ∨ (`slider` ∧ bir satır teslim ∧ (`drop off`/`dropoff` ile başlayan ∨ `#[a-z0-9]{4,}` içeren satır)). Kaydırıcı etiketi asla "tamamlandı" mesajı sayılmaz.
- `delivered` = ¬`deliveryControl` ∧ (`delivery completed` ∨ `order delivered` satırı).
- `demandMap` (Wolt, D19) = `delivery demand` ile başlayan ∨ `quieter than usual` içeren ∨ `expected demand` satırı. Talep haritası çevrimiçi ve çevrimdışı ekranlarda vardır: **durum kanıtı değildir**.
- `waitingText` = `expect a task soon`, `new tasks incoming`, `waiting for orders`, `looking for orders` içeren ∨ `you are online` ∨ `stop new orders`.
- `idle` = `demandMap` ∨ `goOffline` ∨ `waitingText` ∨ `orders coming in hot` içeren ∨ `^\d+ live hotspots?$` ∨ `online`.
- `availability`: `woltFeedback` → `offline`; `offline ∧ ¬goOffline` → `offline`; `(goOffline ∨ waitingText) ∧ ¬offline` → `online`; diğer → yok. Kanıt (`availabilityEvidence`): `wolt_feedback_after_go_offline` · `slider_go_online` · `offline_message` · `slider_go_offline` · `waiting_text` (bu sırayla).
- `drop` = `delivered` ∨ `deliveryControl` ∨ `multiDrop` ∨ (`drop off in`/`dropoff ` ile başlayan ∨ `dropoff to` ∨ `drop off to`) ∨ (`#[a-z0-9]{4,}` ∧ bir adres etiketi) ∨ (`check id` ∧ `dropoff` ile başlayan).
- `pickup` = `pickup from` ∨ `^(\d+) pickups? from$` ∨ `pickup ` ile başlayan ∨ `order is being prepared`/`order is ready for pickup` içeren ∨ `orders to pick up`/`arrive in` ile başlayan ∨ bir eylem alış.
- `controls` = `accept` ∨ `deliveryControl` ∨ bir eylem alış.

Aşama (ilk tutan): geçmiş sayfası → UNKNOWN · `declineDialog` → UNKNOWN (teklif "Decline offer?" arkasında hâlâ kararsız) · menü (`CourierScreens.isMenu`) ∨ (¬`controls` ∧ ¬`idle` ∧ ¬`offline` ∧ sayfa `isPage`) → UNKNOWN · `accept` → OFFER · `drop` → DROPOFF · `pickup` → PICKUP · `offline` → OFFLINE · `idle` → IDLE · diğer → UNKNOWN.

**`CourierScreens`** (RS `CourierScreens.kt:9-43`; girdi `ns`): `isMenu` = tekil satırlardan **≥ 3**'ü menü öğesi (Wolt `delivery settings, insights, finances, stats, earn extra, benefits and insurance, referrals, help center`; Bolt `all deliveries, earnings, campaigns, inbox, performance, profile, help, invite friends, about, settings, support`), `earn extra` ve `support` sayılmaz (Wolt bekleme sayfasında da var); `isPage` = sayfa başlıklarından biri (Wolt 37, Bolt 23 başlık; kaynaktaki küme aynen) ∨ `select the relevant o` ile başlayan; `isStats` = ¬menü ∧ istatistik başlığı (`your stats, deliveries completed, tasks accepted, task completion rate, earnings (estimate), performance, acceptance rate, completion rate, completed deliveries`).

### 2.6 Alanlar (RS `CourierParser.kt:258-398`)

- **Sipariş numaraları** `orderIds`: satırlar boşlukla birleşik metinde `#[A-Za-z0-9]{4,}`, Wolt'ta ayrıca tek başına 3 karakterli `#408`; sıra: uzunlar sonra kısalar, tekil. (Müşteri anahtarı 4+ karakterli olandır, K10, `ORDERS.md`.)
- **Adet** `quantity` = `orders to pick up\s*:?\s*(\d+)` ya da o satırdan sonraki sayı satırı; yoksa kart sayısı (`N pickups from`, Wolt teklifinde `N deliveries from`, tek "Delivery from" = 1).
- **Durak** `stops` = `(\d+)\s*stops`.
- **km**: Wolt kartında önce tam satır `^N km$`; yoksa (her karede) metnin ilk `N km`'si. (Ham notlardaki "Wolt kartında km yalnız tam satır" yanlış: geri düşüş var.)
- **Süre** `minLo/minHi` = metnin ilk `N–M min` aralığı (`–`, `−`, `-`); yoksa tutar taşıyan ilk satırdaki `N min` iki alana birden.
- **Hazır/saatler:** `woltLabelStage` = Wolt ∧ aşama OFFER/PICKUP. Wolt'ta `(?:pickup|^-?kup)\s*(−?\d{1,3})\s*min` ("Pickup 6 min", OCR "8 Pickup 4 min", "-kup 8 min") = hazır olmaya kalan dk (D22: artık son tarih değil); yoksa `now`/`pickup now` → 0. Bolt alış ekranında (R2) mavi balon `^N ?min$` ya da `N` + sonraki satır `min` = restoranın kalan dakikası. `readyIn` = `ready in\s*(−?\d+)\s*min` ?: Wolt etiketi ?: Bolt balonu. `pickupClock` = Wolt etiketi yoksa `(?:pickup|arrive in)\s*(−?\d+)\s*min`. `dropoffClock` = `drop ?off(?: in)?\s*(−?\d+)\s*min` (eksi olabilir: platform son tarihi). `travelEta` = `(?:travel time|eta|away)\s*:?\s*(\d+)\s*min` (son tarih saatleri kalan yol süresi değildir).
- **Restoran ve adresler:**
  - `pickup from` ya da `N pickups from` satırı varsa: restoran = sonraki satır; restoran adresi = ondan sonraki, bölüm başlığına kadar olan satırlar.
  - OFFER ∧ Wolt kartı (D2): `delivery from`/`N deliveries from` sonrası satır = restoran; **son kabul satırından sonraki** satırlar = restoranın adresi (kabul satırı yoksa `baku` ya da `az NNNN` taşıyan son satır).
  - OFFER, Wolt kartı değil: `stops` içeren satır varsa restoran = sonraki satır; `customer drop-off` o satırdan sonraysa arası restoran adresi, `customer drop-off`'tan sonraki satır müşteri adresi. Yoksa (Bolt) `~` ile başlayıp `min` içeren ilk satırdan **iki önceki** restoran, bir önceki restoran adresi (bu satır ≥ 2. indisteyse).
  - PICKUP (alış başlığı yoksa): `arrive in` ile başlayan ya da `arrived` olan son satırdan sonraki satır restoran, ondan sonraki adres (bölüm başlığı ya da `#` ile başlayan değilse).
- **Giriş|Kat|Daire satırı** (OCR üç etiketi sonra üç değeri okur): ardışık en az iki etiket, ardından aynı sayıda değer; değerlerden hiçbiri başlık ya da etiket değilse eşlenir (ilk değer kalır).
- **Müşteri** (`DeliveryDetails`, sonra `fillFromText`, 2.8): ad = `dropoff to`/`drop off to`/`N dropoffs to`'dan sonraki satır; yoksa `#` ile başlamayan, `#XXXX` taşıyan ilk satırın `#`'ten önceki kısmı. Adres = adres etiketinden sonraki alan ?: (dropoff başlığından iki sonraki satır) ?: teklifteki müşteri adresi. `instructions` (`instructions, telimatlar, инструкции`), `note` (`additional note, note, notes, qeyd, elave qeyd, комментарий`), `building` (`building name, building, block, bina`), giriş/kat/daire/kapı kodu (önce satır eşlemesi, sonra etiket alanı), `checkId` = metinde `check id`, `addressDetails` = son `address details` satırından sonraki, başlık ya da `#` gelene kadar olan satırlar. **Alan okuma** (`after`/`field`): etiket satırından sonraki satırlar; bölüm başlığı, `items (`, `#`, kabul ya da teslim satırında durur; etiket satırı yoksa `etiket:` ile başlayan satırın iki noktadan sonrası. `customerAddress` = müşteri adresi ?: teklifteki.
- **Talep** `demand` (D19): `delivery demand` satırında ya da bir sonrakinde `^(?:delivery demand)?\s*\S{0,4}\s*(high|medium|low)$` (OCR çubuk simgesini 0–4 çöp karakter okur); yoksa `delivery demand` içeren satırdaki high/medium/low; yoksa tek başına `high`/`medium`/`low` satırı (her platform). Baş harfi büyük.
- **Çarpanlar** `multipliers` (yalnız IDLE/OFFLINE/OFFER): tam satır `x1.4` / `1.6x` (`×` de), 1.0–10.0, tekil. "1.1–1.65x" (tahmin), "Cook Box 2 by…", "2x burger" değil (VM3: Bolt Earnings Schedule sayfasından asla).
- **Bolt bacakları** (Bolt OFFER): `shown` (tekrarlı) satırlardan `^[~≈∼]\s*(\d{1,3})\s*(min|deq|dk|мин)$` değerleri; tam 2 değer ya da tekilde 2 → `approachMin` (restorana), `deliveryMin` (müşteriye); tek ya da 3+ farklı değer → ikisi de yok.
- **Teklif nakdi** (D25, Wolt kartı): `offerCash` = `cash order` satırı; `changeNeeded` = `total change needed`/`change needed` satırının önündeki (yoksa arkasındaki) `AZN x.yy`, `no change needed` → 0.0. Asla ücret değil.
- **Durum bayrakları:** `arrived` = `arrived` satırı var ∧ hiçbir eylem "vardım" değil (düğme değil, durum yazısı); `ready` = `order is ready for pickup` içeren ∨ `ready for pickup`/`order is ready`/`order ready` ∨ (Wolt etiket aşamasında) `ready`; `preparing` = `being prepared`/`is preparing` içeren ∨ `preparing`; `deliverControl` = DROPOFF ∧ `deliveryControl`; `offlineUntil` = `offline until\s*(\d{1,2}:\d{2})`.
- **Alış ekranı (PICKUP) ek alanları:** `restaurantNote` = müşteri notu alanı (alış ekranında "Notes" restoranın yerini anlatır); `pickupCode` = tam satır `#XXX…` (3+); `items` = `^\d+\s*[x×]\s+\S` satırları, en çok 6.
- **Boost** (2.7): `boostPct/boostWindow/boostStartsInMin` ekrandaki boost (aktif ya da değil); `bonusPct` yalnız aktif olan (gelecekteki değil, FP10); `bonusFrom` = metindeki ilk `from HH:MM`.
- **İstatistik** (2.7): yalnız UNKNOWN ∧ `isStats`.

### 2.7 Boost dilimleri ve performans sayfaları (RS `CourierParser.kt:400-551`)

- Boost sözcüğü: `extra, elave, ekstra, boost, bonus, дополнительно, бонус` (harf/rakam sınırlı); yüzde: `(?<![0-9.,])\+?(\d{1,3})\s*%`; saat aralığı `H:MM-H:MM` (`-–—−`, saat 0–29, dakika 0–59; çıktı `HH:MM-HH:MM`); "başlar" ifadesi EN (`starts in [an|a|N] hours [and] [N] minutes`), AZ (`N saat N deqiqe/deq/dakika/dk sonra bas…`), RU (`через N час… N мин…`) → dakika.
- **`readBoost`** (teklif/ekran boost'u): yüzdeli ilk satır; aynı satırda yüzdeden önce boost sözcüğü ya da (yüzde satır başında ∧ önceki satırda boost sözcüğü). Pencere ve "başlar" ±3 satırda, en yakından; `future` = başlamaya > 0 dk ∨ yakın satırlardan biri `starts ` ile başlıyor ∨ `upcoming` ∨ `coming up` içeriyor ("Acceptance rate 85%" boost değil, R03).
- **`readBoostSlots`** (D24/M21; girdi tekrarlı satırlar): her yüzdeli satır için boost sözcüğü aynı satırda yüzdeden önce ya da (yüzde satır başında ∧ önceki 2 satırdan birinde); yüzde 1–300; pencere bu satır ve sonraki 2 satırda (araya başka yüzde girince durur; komşu dilimler saat ödünç almaz); `from HH:MM` (iki haneli); `active` = önceki 4 satırdan biri `^now\W*boost$`; tarih = önceki 1–2 satırdaki `D/M` (başka yüzdeye kadar), `DD/MM`; aynı (yüzde, pencere, from, tarih) bir kez.
- **Performans sayfası** (`readStats`): `stat(etiketler, desen)` = etiketle başlayan satırın kalanı (`:` ve boşluk kırpılır) ya da sonraki satır desene **tam** uyarsa; en uzun etiket kazanır; `except` etiketleriyle başlayan satır atlanır. Kazanç (`earnings (estimate), earnings, gross earnings, total earnings, qazanc, kazanc`; `earnings without tips` hariç, tutar deseni `^(₼|azn|manat) N` ya da `N (₼|azn|manat)$`), görev (`deliveries completed, completed deliveries, tasks completed, completed tasks`; 1–5 hane), tamamlama ve kabul oranı (`task completion rate, completion rate` / `acceptance rate, tasks accepted`; `N%` → `BigDecimal` /100, 0..1), puan (`rating, your rating, average rating, customer rating, reyting`; 0–5, ★/⭐/* olabilir), mesafe (`distance on- and off-task, distance on and off task, distance`; `N km`), bahşişsiz kazanç (`earnings without tips`), bahşiş (`tips`). Dönem etiketi = dönem adlarından (`today, yesterday, this week, last week, this month, last month, week, month, day, custom, bu gun, dunen, bu hefte, bu ay, bugun, dun, bu hafta`) **tek** satır varsa onun ham metni (≤ 40).
- **`statsPeriod`** (F8.1): gün `16 october [2026]` → `day:2026-10-16`; hafta `11 oct - 16 oct` → `week:2026-10-11..2026-10-16`; ay `october [2026]` → `month:2026-10`. Ay adları İngilizce tam ya da 3 harf (OCR `0` → `o`) ve Azerbaycanca tam; ay başlığı yalnız tam ad. Yıl yazılı değilse `at`'in yılı, ay `at`'in ayından sonraysa bir önceki yıl; haftanın başı ayı sonundan büyükse bir önceki yıl. Geçersiz gün → yok. Tek bulgu yoksa (0 ya da 2+) null. Saat dilimi parametre (varsayılan cihaz); WEEM testleri `Asia/Baku` verir.

### 2.8 Müşteri ayrıntıları (`DeliveryDetails`, RS `CourierParser.kt:8-68`) ve `NoteFields` (RS `NoteFields.kt:9-113`)

- Alanlar: `name, address, note, instructions, building, entrance, floor, apartment, doorCode, checkId, addressDetails, phone`. **`phone` yalnız kart ve `courier.db` içindir (K25)**: kayıt ve yedeğe girmez (`ORDERS.md`).
- `merge(d)`: ad — yeni ad aynı kişiyse ve daha az sözcüklüyse eski kalır (OCR soyadını düşürür); adres — eski adres rakam taşıyor, yenisi taşımıyorsa eski kalır; not — yeni not kısaltılmış (`…`/`...` ile biter) ve eskinin başıysa eski kalır; diğer alanlar yeni değer varsa yazılır; `checkId` VEYA'lanır.
- `fillFromText()` (F7.1): giriş/kat/daire değeri başka alan sözcüğü taşıyorsa (`menzil, daire, kvartira, квартира, kv, кв, kod, mertebe, kat, floor, этаж, otaq, otag, ofis, office, room, giris, giriw, podyezd`) serbest metin sayılır: kendi değeri ondan okunur, kalanlar boş alanları doldurur; sonra `addressDetails`, `note`, `instructions`'tan boş alanlar (`building, entrance, floor, apartment` — yoksa `Oda N` —, `doorCode`) ve ilk telefon numarası doldurulur. Platformun kendi alanındaki değer asla değişmez.
- `facts(az)` / `display(az)` (kart metni): `Kapı kodu/Qapı kodu`, `Bina`, `Giriş`, `Kat/Mərtəbə`, `Daire/Mənzil`, `Kimlik kontrolü gerekli/Şəxsiyyət yoxlaması lazımdır`; sonra `Telefon`, `Adres detayı/Ünvan detalları`, `Not/Qeyd`, (nottan farklıysa) `Talimat/Təlimat`, adres, kısaltılmış not uyarısı ("Not kısaltılmış: tamamı için kurye uygulamasında Notes'u aç." / AZ). **`display` telefon satırını içerir** → bildirim 12'ye ve 2 telefon moduna giden metinde kullanılmaz (`ORDERS.md` R6, S5).
- `NoteFields.extract(metinler)`: telefonlar önce bulunur ve metinden çıkarılır (rakamları kat/kod sanılmasın); metin `norm`'lanır. Kurallar (ilk tutan; sıra sonları `-cı/-cü/-ci/-cu/nd/rd/th/st/.`): kapı kodu (`domofon[un] [kodu]`, `qapi[nin] kodu`, `giris kodu`, `door code`, `entrance code`, `intercom [code]`, `kod dveri`, `код [двери|домофона|подъезда]`, `домофон` + `[:=-–]` + `[#]` + `N[#*N][#*]`; `kod[u]: N`; `kod N`), kat (`mertebe|kat|floor|etaj|этаж N` ya da `N. kat`), daire (`menzil|daire|apartment|apt|flat|kvartira|квартира|kv|кв [no|№|#] N[harf]` ya da `N menzil…`), giriş (`podyezd|podezd|pod.|giris|entrance|подъезд N` ya da `N giris`, harf girişi `A giriw`), oda (`otaq|otag|ofis|office|room|комната|офис N`), bina (`blok|block|korpus|корпус|bldg|building` + harf ya da `N[harf]`; `bina N`). Bina, giriş, daire büyük harfe; kapı kodundaki boşluklar silinir.
- `NoteFields.phones(metin)`: yön işaretleri (`U+200E/F, 202A–202E, 2066–2069`) silinir, sert boşluklar (`U+00A0, 2007, 202F`) boşluk olur. Azerbaycan: `(+994|994|0)?` + operatör `10|12|50|51|55|60|70|77|99` + 3-2-2 hane; önek yoksa tam 9 hane şart → `+994 OO AAA BB CC`. Uluslararası: `+` (994 değil) + 9–15 hane → `+<rakamlar>`. Sıra korunur, tekil.
- `NoteFields.redact(metin)`: aynı iki desen `[tel]` olur (önündeki boşluk kalır); **diske yazılan her ham metin** (ekran satırı, düğüm metni, bildirim alanı, bildirim ipucu) bundan geçer.
- `NoteFields.truncated` = `…` ya da `...` ile biter.

### 2.9 Kayıt politikası yardımcıları (RS `RecordingPolicy.kt`)

- `ManualSessionPolicy` (`:5-15`): A2.4'te `logic/core`'a taşındı (`mayStart`, `canCapture`, `onStartFailure`, `interrupted`).
- `OfferRules` (`:64-80`): `isAccept`, `isHistory`, `isOffer(eylemler, metinler, ayrıntı var) = ayrıntı ∧ kabul eylemi ∧ ¬geçmiş` (`isOffer` üretimde çağrılmaz; testte kalır, ucuz).
- `ScreenDeduper` (`:99-111`): imza = kırpılmış, boş olmayan, geri sayım (`^\d+\s*(s|sec|seconds|san|с)$`) olmayan satırlarda saatler (`\b\d{1,2}:\d{2}\b`) `<time>`, ölçüler (`N km|min|dk|dəq`, büyük/küçük duyarsız) `<metric>` yapılmış **küme**. Boş imza ya da öncekiyle aynı → yazma; değilse yaz ve sakla. Anahtar `pkg + kaynak` (erişilebilirlik ve OCR ayrı). Sıra değişimi, geri sayım ve ETA değişimi yeni satır yazdırmaz; boş okuma son ekranı silmez.
- **Yazılmayanlar (WEEM):** `MotionGate` (`:18-62`; vardiya servisi `observe/reset` yapar ama `canCapture/status`'u hiçbir kod çağırmaz) ve `OfferIdentity` (`:83-97`; yalnız `RecordingPolicyTest`; üretimde teklif kimliği `OfferState.same`, `ORDERS.md`). Gözlenebilir davranışları olmadığından yazılmaz; A2.4'ün `ShiftMachine.kt:159`'daki "A3: MotionGate" notu A3.4'te silinir.

### 2.10 Kendi satırlarını tanıma (`OverlayText.isOwn/withoutOwn`, RS `OverlayText.kt:8-17, 99-134`)

- Ayrıştırıcı, rozetin kendi çizdiği satırları (kaynak ne olursa olsun: erişilebilirlik, OCR kırpması, bildirim) okumadan önce atar (N11: "● 30.1 km" ya da "Wolt · 2.21₼ · Teklif" teklifin km'si/ücreti olmasın).
- Tanınanlar (`norm` sonrası, tam satır): `<platform><ayraç><tutar>[<ayraç>net <tutar>]<ayraç>…` (teklif satırı), `<platform><ayraç>…<ayraç><aşama>`, `<platform><ayraç><aşama>`, `<platform><ayraç><durum>`; mesafe `[·•●∙⋅*] N km`; gün `Bugün|Bu gün [:] N sipariş|sifariş`. Platform: `wolt|bolt food|bolt` (gevşek ayraç: madde imi, boşluklu tire/nokta ya da yalnız boşluk) ya da 2–30 harf/rakam (sıkı ayraç). Tutar `N[.,NN] (₼|azn)`. Aşama/durum metinleri TR ve AZ (`Restorana gidiliyor/gedilir`, `Restoranda bekleniyor/gözlənilir`, `Müşteriye gidiliyor/Müştəriyə gedilir`, `Müşteriye varıldı/Müştəriyə çatıldı`, `Teslim edildi/Çatdırıldı`, `Sonuç doğrulanamadı/Nəticə təsdiqlənmədi`, `Sipariş bekleniyor/Sifariş gözlənilir`, `Çevrimdışı/Oflayn`). `OverlayText`'in kendi `norm`'u: küçült (ROOT), `ə→e`, `ı→i`, NFD, işaret sil, `[\s\p{Z}]+` tek boşluk (ayrıştırıcınınkinden farkı: `ё` dönüşmez, `\p{Z}` de boşluk sayılır).
- `withoutOwn(metinler)`: tek satırlık metin kendi satırıysa atılır; çok satırlıda yalnız kendi satırları.
- WEEM: tanıma ve söz dağarcığı (`OwnLines`: ayraç, madde imi, para birimi, `net`, aşama/durum/gün metinleri) `logic/capture`'ta; `:orders` rozet biçimlendiricisi **aynı sabitleri** kullanır (tek kaynak) ve her biçimlendirdiği satırın `isOwn` olduğunu test eder (`ORDERS.md` R7). Aşama metinleri `:capture`'ta adla tutulur (`TripPhase` `:orders`'ta kalır; eşleme orada).
- Ad/adres eşleştirme (`CustomerMatch.distance`, `sameName`, `looksLikeAddress`; RS `DeliveryCore.kt:900-918`, `merge` bunları kullanır) `logic/capture` `NameMatch`'e taşınır; kayıt düzeyindeki `CustomerMatch.find/distinct` `:orders`'ta kalır ve `NameMatch`'i çağırır. `sameName`: sözcükler `norm`'lanır; kısa adın ilk sözcüğü uzununkine eşit ve kısanın her sözcüğü uzunda var (≥ 5 harfli sözcükte 1 düzenleme farkı eşit sayılır). `looksLikeAddress` = rakam içerir.

### 2.11 Wolt kaydırıcısındaki dönen gösterge (K33, `SliderSpinner`, RS `SliderSpinner.kt:14-38`)

- Yalnız sınıfı `ProgressBar` (ya da `.ProgressBar` ile biten; Compose da bu sınıfı verir) olan ve **kareye yakın** (genişlik ≤ 2 × yükseklik ve tersi, boş değil) kutular sayılır.
- Aynı karede teslim etiketi kutusu varsa gösterge etiketin satırında olmalı (merkezinin y'si etiketin üstünden bir etiket boyu yukarı ile altından bir etiket boyu aşağı arasında); etiket yoksa (gösterge etiketin yerini almış olabilir) kare sayılır.
- Sonuç yalnız ek kanıttır; tek başına teslim değildir. `:orders` bunu müşterinin kaydırıcı karesinden sonra `SPINNER_AFTER_SLIDER_MS` = 10 sn içinde kabul eder (`ORDERS.md`). Kayıt satırında `deliver_spinner: true` (yalnız erişilebilirlik).
