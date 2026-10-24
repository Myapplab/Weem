# CAPTURE — Aşama 3 şartnamesi (`:capture`: R1, R2, R16; R3 alanları; R19 ekran okuma uyarısı)

Durum: A3.1, 17 Ekim 2026 (Claude Opus · max, tek ajan; S14) — bölüm 0–9 yazıldı. Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04` (Rawstats 0.25.1, versionCode 33); satır numaraları bu commit'te açılıp doğrulandı. `bulgular/R_capture.md`'nin yerini alır (oradaki düzeltilen kalemler 9. bölümde).

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
7. Okuma zamanlanmamışsa: hemen bir okuma + 180 ms sonra ikinci okuma (izin hâlâ varsa). **WEEM S39 (pil):** `typeWindowContentChanged` ve `typeWindowsChanged` için bunun yerine en çok `CaptureTiming.CONTENT_READ_MS` (250 ms) aralıkla tek okuma, sondaki değişiklik gecikmeli okunur (sayaçlı ekranlarda her olay iki ağaç gezintisi başlatıyordu); pencere durumu, bildirim, tıklama ve çevirici yolu aynı.

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

- Kayıt izni varken `serviceInfo.packageNames` = izlenen uygulamalar + arama uygulamaları (`Dialer.eventScope`: bilinen paketler + telefonun varsayılanı; A10.2, eskiden `null` = tüm uygulamalar), yokken ya da Receiver'da `[kendi paketi]` (başka uygulamaların her içerik değişimi servisi uyandırmasın). Yalnız değişince yazılır; hata `event_scope`. `packageNames` yalnız olayları süzer, pencere okumayı (`windows`) değil (Android belgesi; cihazda doğrulanmadı): okunan pencereler yine izlenenler + arama uygulaması.
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

## 3. R1 — OCR: tempo, ekran görüntüsü, maskeleme (D6, K37)

Gerekçe (RS `OfferCapture.kt:3-13`, saha 11–12 Ekim): Wolt'un harita, teklif ve görev ekranlarında erişilebilirlik metni yok; her Wolt kartı OCR'dan gelir. Wolt kartı önce ücretsiz ve rota satırsız gösterir, ücreti 1–12 sn sonra yükler (ilk kart karesinden sonra ortanca 3 sn; 28 kartın yalnız 4'ünde ücret ilk karede). Bu yüzden pencere "önemliyken" Android'in izin verdiği en kısa aralıkla okunur.

### 3.1 Sabitler (`OfferCapture`, RS `OfferCapture.kt:14-44`) — WEEM: aynen `logic/capture`

| Ad | Değer | Anlam |
|---|---:|---|
| `REREAD_MS` | 100 ms | ücretsiz teklif kartının ek okumaları arası (erişilebilirlik) |
| `MAX_REREADS` | 10 | kart başına ek okuma (≈ 1 sn) |
| `INVALID_WINDOW` | 5 | `ERROR_TAKE_SCREENSHOT_INVALID_WINDOW` (API 34) |
| `DISPLAY_FALLBACK_MS` | 60 000 ms | geçersiz pencere hatasından sonra tüm ekran görüntüsü süresi |
| `OCR_GAP_MS` | 1 500 ms | sıcak olmayan pencerenin iki OCR'ı arası |
| `SHOT_INTERVAL_MS` | 350 ms | iki ekran görüntüsü arası (Android 333 ms'den sıkını reddeder) |
| `HOT_AFTER_TRIGGER_MS` | 10 000 ms | bildirim ya da yeni pencere durumu sonrası sıcaklık (saha: kart bildirimden 2,1–6,7 sn sonra) |
| `HOT_OFFER_MS` | 20 000 ms | ücretsiz kart ilk karesinden bu yana sıcaklık üst sınırı (saha: en çok 12 sn) |
| `OCR_MAX_WIDTH` | 960 px | OCR girdisi genişlik sınırı (Wolt'un 12–14 sp yazısı 1280 px ekranda ≈ 20–24 px kalır; ML Kit ≥ 16 px ister) |

- `reread(o, yapılan)` = OFFER ∧ ücret yok ∧ yapılan < 10. `useDisplay(sdk, geçersizPencereAnı, now)` = sdk < 34 ∨ (son geçersiz pencereden bu yana 0 ≤ geçen < 60 sn). `ocrScale(w)` = w ≤ 960 ise 1, değilse 960 / w.
- WEEM ek sabitleri (Android'de düz sayı olmasın diye `CaptureTiming`): `SECOND_READ_MS` = 180, `OCR_STALE_MS` = 1 800 (3.4), `TICK_SHIFT_MS` = 500, `TICK_IDLE_MS` = 2 000.

### 3.2 `OcrPace` — hangi pencere, ne zaman (RS `OfferCapture.kt:46-111`, saf; WEEM: aynen)

Durum: pencere başına son okuma anı, paketi, "ücretsiz kart yükleniyor" başlangıcı, son görüntü (parmak izi + satırlar), son başarısız okuma; paket başına son tetik; son ekran görüntüsü anı.

- `trigger(pkg, at)`: paketin son tetik anı (yalnız ileri).
- `hot(id, pkg, now)` = (now − paketin tetiği ∈ [0, 10 sn]) ∨ (now − pencerenin yükleme başlangıcı ∈ [0, 20 sn]).
- `due(id, pkg, now)` = (now − son ekran görüntüsü ≥ 350 ms) ∧ (now − pencerenin son okuması ≥ [sıcak ∧ son okuma başarısız değil ise 350 ms, değilse 1 500 ms]).
- `next(pencereler, now)`: vadesi gelenlerden önce sıcak olanların en eski okunanı, yoksa vadesi gelenlerin en eski okunanı; yoksa null.
- `started(id, pkg, at)`: pencerenin son okuması, paketi ve son ekran görüntüsü = at. `failed(id, at)`: o okuma başarısız; pencere sıcak aralığı değil normal aralığı bekler.
- `read(id, gözlem)`: kabul edilen OCR karesi OFFER ∧ ücretsiz → yükleme başlangıcı (yoksa) = gözlem anı; başka her kare yüklemeyi bitirir.
- `hotDelay(now)`: bilinen pencerelerden biri sıcaksa bir sonraki ekran görüntüsüne kalan süre (`son ekran görüntüsü + 350 − now`, en az 0), değilse null (sonraki tik ya da olay okur).
- `same(id, iz)` / `remember(id, iz, satırlar)`: aynı parmak izli görüntü yeniden OCR'lanmaz, önceki satırlar kullanılır.
- `retain(id'ler)`: ekranda olmayan pencereler unutulur (paket tetikleri kalır).
- **Parmak izi** (RS `WatchService.kt:279-287`, `OfferCapture.kt:101-109`): 64 bit FNV-1a; başlangıç `FNV_OFFSET xor ((genişlik shl 32) or yükseklik)`, her piksel `x = (x xor (piksel and 0xFFFFFFFF)) × 0x100000001b3`; satır blokları (en çok 65 536 piksel) tek ara bellekle. Her piksel sayılır: değişen tek rakam izi değiştirir.

### 3.3 Ekran görüntüsü (RS `WatchService.kt:196-248, 288-300`)

1. OCR meşgul işaretlenir; `pace.started`; o anki `shiftStart` saklanır; `display = useDisplay(...)`.
2. API ≥ 34 ve `display` değilse **pencere görüntüsü** (`takeScreenshotOfWindow(id)`), değilse **tüm ekran** (`takeScreenshot(DEFAULT_DISPLAY)`); geri çağrılar ana iş parçacığında.
3. Başarı: donanım tamponu `ARGB_8888` kopyalanır (tüm ekranda değiştirilebilir kopya), tampon kapatılır. Tüm ekranda **kendi pencerelerimiz siyahla boyanır** (`maskOverlays`): rozet (bağlı ve genişliği > 0 ise ekrandaki yeri ve boyu) ve Sürüş Paneli'nin yüzen topu (`SurusPaneli.floatingBounds()`); topun "12.4" + "km" yazısı teklifin km'si ya da ücreti sanılmasın. Pencere görüntüsünde kendi pencerelerimiz yoktur.
4. Ayrı tek iş parçacığında (rozet ve top ana iş parçacığında çizilir): tüm ekranda pencere sınırına kırpma (kesişim boşsa ya da ekran dışıysa girdi yok), genişlik > 960 ise oranlı küçültme (süzgeçli), parmak izi. Ara görüntüler geri verilir. Girdi ya da iz yoksa OCR biter.
5. Ana iş parçacığında `recognize`: servis bağlı değilse biter. Aynı iz → önceki satırlar (3.4), OCR biter, `afterOcr`. Değilse ML Kit (`TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` = Latin yazısı; `com.google.mlkit:text-recognition:16.0.1`, model APK içinde): satırlar = bütün blokların satırları, sınır kutusunun (üst, sol) sırasıyla (kutu yoksa 0); iz ve satırlar saklanır; 3.4. Hata `ocr`; her durumda görüntü geri verilir, OCR biter, `afterOcr`.
6. Hata (`onFailure(kod)`): OCR biter; `pace.failed`; kod 5 ve pencere görüntüsüyse geçersiz pencere anı = at (60 sn tüm ekran); `events.jsonl` `screenshot_unavailable {pkg, code, display}`; son hata metni (1.8).
7. `afterOcr`: servis bağlıysa `hotDelay` kadar sonra yeni okuma.

Latin tanıyıcı Kiril okumaz: söz dağarcığındaki Rusça sözcükler yalnız erişilebilirlik metninden gelir.

### 3.4 OCR satırlarının kabulü (`ocrLines`, RS `WatchService.kt:260-273`)

- **Atılır:** servis bağlı değil ∨ kayıt izni yok ∨ `shiftStart` değişti (başka vardiya) ∨ pencere (aynı id ve aynı kök paket) artık ekranda değil ∨ ekran görüntüsünden bu yana ≥ 1 800 ms geçti ∨ satır yok.
- **Eylemler OCR'da yoktur**, satırlardan türetilir: kabul, ret, alış, teslim satırları ve (bir satır `slide to confirm` içeriyorsa) "vardım" satırları.
- Aynı pencerenin erişilebilirlik okuması (`native`) da ayrıştırılır. **Erişilebilirlik PICKUP/DROPOFF diyorken OCR karesi OFFER/IDLE/OFFLINE ise OCR karesi yok sayılır** (geçiş sırasında alınmış kare dolu sipariş durumunu ezmesin). Değilse `accept(..., "ocr")` (1.5) ve `pace.read`.
- Gözlem anı = **ekran görüntüsünün istendiği an** (`at`), OCR'ın bittiği an değil (geç kare zamanı, K37 `backfill`, `ORDERS.md`).

### 3.5 WEEM yerleşimi

- Saf (`logic/capture`): `OfferCapture`, `OcrPace` (FNV dahil), `CaptureTiming`, `ocrLines` kararı (`OcrAcceptance.accept(native, ocr, ...)` gibi saf bir işlev: atma koşulları + native/OCR çatışması + türetilmiş eylemler) — test edilebilir olsun diye.
- Android (`:capture`): ekran görüntüsü, kırpma/ölçek, parmak izi için piksel okuma, ML Kit çağrısı, maskeleme.
- **Maskeleme kaynağı:** Android `:core`'da küçük bir kayıt (`OwnOverlays`: modüller ekrandaki pencerelerinin dikdörtgenini veren işlev kaydeder). Rozet (`:orders`, A3.7) ve yüzen panel (`:scooter` S7, A5) kaydolur; `:capture` hepsini boyar. Restoran/müşteri balonları (R6) Rawstats'ta maskelenmiyordu: aynı kalır (risk 9).
- ML Kit sürümü 16.0.1 (plan 4.7); ağ ayrıntısı 7.3.

## 4. R2 — Bildirim okuma (`NotifService`) ve R16 — navigasyon bildiriminden mesafe (`NavDistance`)

### 4.1 Bildirim dinleyici (RS `NotifService.kt:11-82`)

- **Ev izleyici tiki** (E7): dinleyici bağlanınca 5 sn'de bir `HomeWatch.refresh` (korumalı); bağlantı kopunca ve kapanınca durur. Sistem dinleyiciyi bağlı tuttuğundan ekran okuma kapalıyken de evden çıkış vardiyayı başlatır (`CORE.md` 1.7). WEEM: aynı (`CaptureTiming.HOME_TICK_MS` = 5 000).
- `onNotificationPosted` korumalı (kaynak `notification`; 16 Ekim: navigasyon uygulaması bildirimini saniyede bir yeniler, tek istisna uygulamayı açılır açılmaz kapatıyordu). Sıra:
  1. **Navigasyon paketi** (`NavDistance.packages`, 4.2): bildirimin `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_SUB_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_INFO_TEXT` alanları → `OrderTracker.navigation(satırlar, now)`; **diske hiçbir şey yazılmaz**; dön. Bu adım `targets`'tan önce: navigasyon uygulamasının izlenen listede olması gerekmez; kayıt iznine `navigation` bakar.
  2. Paket `targets`'ta değilse dön.
  3. Kayıt izni yoksa tekrar süzgeci (`lastByKey`) boşaltılır, dön.
  4. `title`, `text`, `big` (`EXTRA_BIG_TEXT`), `sub` (`EXTRA_SUB_TEXT`); aynı `sbn.key` için `"title|text|big|sub"` öncekiyle aynıysa dön (yenilenen bildirim tekrar yazılmaz), değilse saklanır.
  5. `notifs.jsonl` "posted" satırı (6.2).
  6. `OrderTracker.notification(pkg, [title, text, big, sub] boş olmayan tekil, "\n" ile, sbn.postTime)`.
- `onNotificationRemoved` korumalı: `targets` dışıysa dön; tekrar süzgecinden silinir (izin yokken de); izin yoksa dön; "removed" satırı (6.2).
- Aynı bildirim `:orders`'a iki yoldan gelebilir: erişilebilirlik bildirim olayı (1.3, olay metinleri) ve dinleyici (bu bölüm). İkisi de bugünkü gibi iletilir; `:orders` (ipucu satırı, hazır sayacı) buna göre yazılmıştır.
- WEEM: Android `NotifService` (ad değişmez, A1.2 manifestte) ince; tekrar süzgeci (`NotificationDedup`: anahtar → içerik) ve satır biçimi saf `logic/capture`.

### 4.2 R16 — Navigasyon bildiriminden kalan yol (RS `NavDistance.kt:11-36`)

- Paketler: `com.google.android.apps.maps`, `com.waze`, `ru.yandex.yandexnavi`, `ru.yandex.yandexmaps`, `ru.dublgis.dgismobile`, `com.here.app.maps`.
- Süre birimleri: `min|mins|minute|minutes|dk|dak|deq|deqiqe|мин|h|hr|hrs|saat|ч`. Mesafe: `(\d+(?:[.,]\d+)?)\s*(km|m|км|м)(?![a-zа-я])`.
- `summary(satırlar)`: `norm`'lanmış hâlinde hem süre (`\b\d+\s*(birim)\b`) hem de kendisi süre olmayan bir mesafe taşıyan **ilk** satır ("8 min · 2,1 km"), kırpılmış, en çok 60 karakter. Yalnız mesafe ("150 m", "Turn left in 300 m") bir sonraki manevradır, yok sayılır.
- `metres(özet)` (M20): `norm` sonrası açık Unicode sınırlı süre (`(?<![\p{L}\p{N}_])[0-9]+[\s\p{Z}]*(birim)(?![\p{L}\p{N}_])`) yoksa null; varsa süre olmayan **son** mesafe; `,` → `.`; `km`/`км` × 1000 ("1 min · 5 m" → 5, "8 min · 2,1 km" → 2100, "3 мин · 1,2 км" → 1200, "1 saat 5 dk · 300 m" → 300; "5 minutos · 2 km", "x3 min · 2 km" → null).
- 0.25.0 çökmesi: `durationU` önce `Regex("(?U)…")` idi; Android ICU `(?U)` bilmez, nesne yüklenemedi, ilk bildirim uygulamayı kapattı; 0.25.1'de sınırlar açık yazıldı (`:16-19`).
- Kullanım (`ORDERS.md`): özet ve anı yalnız bellekte (`navSummary`, `navSummaryAt`); kartların canlı satırı "Kalan: <özet>" / "Qalıb: <özet>" (≤ 20 sn taze); varış kuralında (M18/M20) restoran aşamasında (TO_RESTAURANT, WAITING) ve müşteri yolunda (TO_CUSTOMER) kaynak `nav_google_maps` (bildirim hangi uygulamadan gelirse gelsin bu ad), ≤ 30 sn taze. E16: müşteri kartı artık bu mesafeyle açılmaz.
- **WEEM:** R16 kalır (ilk sürüm listesinde). YolGo'nun E8 rota yükünün yerini WEEM'in kendi navigasyonu alır (`NavPort.route`, `RouteStatus.remainingM`; kaynak adı `nav_yolgo` korunur, `ORDERS.md`). `summary`'deki düz `\b` JDK 21'de Kiril birimle eşleşmez ("3 мин · 1,2 км" → null; cihazda ICU ve Rawstats'ın JDK 17 testlerinde eşleşir): WEEM'de açık sınırla yazılır (7.5).

## 5. R19 — Ekran okuma kapalı uyarısı (E7)

### 5.1 Kural (RS `ScreenReading.kt:9-40`, saf)

- `ScreenReading.enabled(ayar, pkg)` = `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` `:` ile bölünür, `"<pkg>/"` ile başlayan öğe var. `on(ayar, pkg, bağlı)` = bağlı ∧ açık (açık ama çökmüş servis de hiçbir şey kaydetmez).
- `ScreenReadingGuard(graceMs = 10 000)`: `check(on, vardiya, now)` — açıksa ya da vardiya yoksa "kapalı başlangıcı" silinir; değilse (yoksa) now; uyarı = başlangıç var ∧ now − başlangıç ≥ 10 sn; uyarı değiştiyse true. Sabitler `GRACE_MS` = 10 000, `CHECK_MS` = 5 000, `NOTIFICATION_ID` = 5103 (**WEEM: 5104**, `NotificationIds.SCREEN_READING_OFF`).

### 5.2 Çalıştırma ve gösterim (RS `GpsService.kt:25-31, 100, 117-155, 293, 303, 316-326`; `MainActivity.kt:543, 1384-1390`)

- Vardiya başlangıcında 5 sn'lik denetim kurulur, vardiya sürdükçe yinelenir. Her denetim: `on = ScreenReading.on(ayar, paket, WatchService.running)`; uyarı değiştiyse göster ya da kaldır ve `events.jsonl` `screen_reading_off` / `screen_reading_on`. Vardiya bitince denetim durur, koruyucu sıfırlanır (`check(true, false, now)`), uyarı kalkar; servis kapanınca denetim silinir.
- **Bildirim:** kanal `screen_reading` ("Ekran okuma uyarısı" / "Ekran oxuma xəbərdarlığı", IMPORTANCE_HIGH); başlık "Ekran okuma kapalı — kayıt yapılmıyor" / "Ekran oxuma bağlıdır — qeyd aparılmır"; metin "Wolt ve Bolt okunmuyor. Açmak için dokun: Erişilebilirlik → Rawstats ekran kaydı." / "Wolt və Bolt oxunmur. Açmaq üçün toxun: Əlçatanlıq → Rawstats ekran kaydı."; renk paletin uyarı kırmızısı (gece/gündüz); `ongoing`, `onlyAlertOnce`; dokununca erişilebilirlik ayarı.
- **Erişilebilirlik ayarı intent'i** (K39): `ACTION_ACCESSIBILITY_SETTINGS` + `NEW_TASK`; `":settings:fragment_args_key"` = `WatchService` bileşeni, `":settings:show_fragment_args"` paketinde aynı anahtar (desteklenen telefonda servis vurgulanır); açılamazsa genel ayarlar.
- **Kırmızı rozet:** "Diğer uygulamaların üzerinde göster" izni varsa (`Settings.canDrawOverlays`) ve rozet yoksa: başlık metni, 13 sp sans-serif-medium, yazı gece `#121212` gündüz beyaz, dolgu 12/8 dp, köşe 16 dp kırmızı zemin, yükselti 6 dp; `TYPE_APPLICATION_OVERLAY`, odaklanmaz, dokunuşu geçirir; üst orta, y = 64 dp; dokununca erişilebilirlik ayarı. İzin yoksa yalnız bildirim.
- **Ana sayfa:** vardiya açık ∧ ekran okuma çalışmıyorsa kırmızı satır "Ekran okuma kapalı — kayıt yapılmıyor" + "!" → erişilebilirlik ayarı. Elle vardiya başlatma ekran okuma **istemez** (E7: vardiya okumasız da açılır).
- **WEEM:** kural `logic/capture` (`ScreenReading`, `ScreenReadingGuard`). Denetim döngüsü, bildirim (`NotifyPort`, 5104) ve kırmızı rozet Android `:capture`'ta bir `ShiftListeners` dinleyicisi (vardiya başı: döngü; vardiya sonu: durdur ve kaldır); `:core` vardiya servisi `:capture`'ı tanımaz. Metindeki servis adı "WEEM ekran kaydı" (manifest etiketi). Ana sayfa satırı Harita sekmesindeki vardiya kartında (A2.4 kartı; A3.7). `NotificationIds.kt`'deki "`:orders`/R19" sahiplik notu `:capture` olur.
- R19'un diğer kalemleri: otomatik başlatma bildirimleri 5101/5103 A2.4'te yazıldı (`Notices`), 5102 scooter A5'te; pil optimizasyonu bilgi satırı ve izinler kartı (`MainActivity.kt:1186-1210`) `:app` ilk açılış/izinler ekranında (G5; `ORDERS.md` R19 satırında ayrıntı).

## 6. Veri (A3.4 yazar, A3.9 karşılaştırır)

Gün klasörü `files/logs/YYYY-MM-DD/` (kayıt anının yerel tarihi), satır ekleme `LogStore.append` (A2.3; tek kilit). Zaman damgası `t` = `yyyy-MM-dd'T'HH:mm:ss.SSSZ` (`LogTime`, ör. `2026-10-17T18:44:00.123+0400`). `loc` (varsa) = `{lat, lon, acc, age_s}` — vardiya servisinin son konumu (`LogStore.lastLocation`), `age_s` = (şimdi − fix anı) / 1000 tam sayı. JSON'da değeri null olan alan **yazılmaz** (`org.json` `put(k, null)` anahtarı siler).

### 6.1 `screens.jsonl` (RS `WatchService.kt:161-166`)

| Alan | Değer |
|---|---|
| `t` | kare anı (`at`; OCR'da ekran görüntüsünün istendiği an) |
| `pkg` | paket |
| `window` | erişilebilirlik pencere id'si |
| `bounds` | `Rect.flattenToString()` ("0 0 1280 2856") |
| `source` | `accessibility` \| `ocr` |
| `offerLike` | aşama OFFER mı |
| `stage` | aşama adı |
| `texts` | satırlar, her biri `NoteFields.redact` |
| `actions` | eylemler, `redact` |
| `nodes` | yalnız erişilebilirlik: düğüm satırları, ilk iki öğe (`text`, `contentDescription`) `redact` |
| `phone_battery_pct` | telefon pili 0–100 (okunamazsa yok) |
| `deliver_spinner` | `true`, yalnız erişilebilirlik okumasında K33 göstergesi varsa |
| `loc` | varsa |

Satır yalnız `ScreenDeduper` imzası değiştiğinde yazılır (2.9); `:orders` her kareyi alır. Yani `screens.jsonl` karelerin **tamamı değildir**.

### 6.2 `notifs.jsonl` (RS `NotifService.kt:49-62, 73-78`)

- "posted": `t` (şimdi), `posted` (`sbn.postTime`), `ev` = `posted`, `pkg`, `key`, `ongoing`, `channel` (kanal kimliği), `title`, `text`, `big`, `sub` (her biri `redact`; yoksa alan yok), `loc`.
- "removed": `t`, `ev` = `removed`, `pkg`, `key` (konum yok).
- Navigasyon paketlerinin bildirimleri **hiç** yazılmaz.

### 6.3 `events.jsonl` ve `errors.jsonl` (`:capture`'ın yazdıkları)

| `type` | Ek alanlar | Kaynak |
|---|---|---|
| `click` | `pkg`, `text` (tekil etiketler boşlukla; Rawstats `redact` etmez, **WEEM eder**, aşağıdaki not) | `WatchService.kt:89` |
| `screenshot_unavailable` | `pkg`, `code` (Android hata kodu), `display` (tüm ekran mıydı) | `:229` |
| `screen_reading_off` / `screen_reading_on` | — | `GpsService.kt:122` (WEEM: `:capture` uyarı dinleyicisi) |

`Store.event` ek alanlardan sonra `t`, `type`, `loc` ekler. `errors.jsonl`: `{t, source, error}` (1.8).

Not (gizlilik, **WEEM farkı**): `click.text` müşteri kartındaki bir öğenin metni olabilir (dokunulan telefon numarası dahil); Rawstats onu `redact` etmeden yazıyordu. Bu, "müşteri telefonu JSONL'e girmez" kuralının (`VERI_SOZLESMESI` 9, `PhoneK25Test`) açık kalmış bir deliğidir: WEEM `text`'i de `NoteFields.redact`'ten geçirir (yalnız telefon `[tel]` olur; `:orders`'a giden etiketler bellekte ham kalır). Gölge modda fark yalnız telefon içeren tıklama satırlarında çıkar.

### 6.4 Tercihler ve bellek

- `kuryelog` tercihleri: `targets` (StringSet), `userPicked` (Bool, false), `ocr` (Bool, **true**; Daha fazla'da "Görünür ekran OCR yedeği" / "Görünən ekran üçün OCR ehtiyatı" anahtarı, RS `MainActivity.kt:1140`). `overlay`, `bx`, `by` `:orders`'ındır (R7).
- Yalnız bellek (diske yazılmaz): son okuma ve son "Accept" anı, görülen teklif sayısı (tanı kartı); navigasyon özeti; telefon uygulamasından okunan numara (`:orders`'a gider; orada yalnız `courier.db`'ye, `ORDERS.md`); OCR görüntü izleri ve satırları.

### 6.5 Gölge mod (A3.9) için

- **A3.9 (17 Ekim):** karşılaştırıcı `logic/orders/src/shadow` (`ORDERS.md` 16.5); ayrıştırıcı eşitliği kuralı `parser.stage` (sert).
- İki uygulama aynı kareyi farklı anlarda okur: `screens.jsonl` satırları birebir eşleşmez. Ayrıştırıcı eşitliği için Rawstats'ın `screens.jsonl` satırları (`pkg`, `source`, `texts`, `actions`, `t`) WEEM ayrıştırıcısına verilir ve `stage`/`offerLike` Rawstats'ın yazdığıyla karşılaştırılır (metinler `redact`'li: telefon çıkarımı dışında sonuç aynı olmalı). Sipariş ve teklif eşitliği `ORDERS.md`'de.
- JSON karşılaştırması **ayrıştırılmış değerle** yapılır, bayt bayt değil: Android `org.json` alan sırasını korur, JVM'deki `org.json` korumaz.
- Gölge modda iki erişilebilirlik servisi ve iki rozet aynı anda çalışır. Kendi satırları tanıma (2.10) iki uygulamanın rozetini de tanır (aynı biçim). Tüm ekran görüntüsünde (yalnız API < 34 ya da geçersiz pencere sonrası) her uygulama yalnız kendi penceresini boyar: öbürünün rozeti/topu OCR'a girebilir. Pixel 10 Pro (API 36) normalde pencere görüntüsü alır; fark raporunda `screenshot_unavailable` olan dakikalar ayrıca işaretlenir.

## 7. WEEM yerleşimi ve kesişen kurallar

### 7.1 Modüller ve arayüzler

**`logic/capture` (saf, JVM testli; paket `com.kuryelog.logic.capture`):** `CourierParser`, `CourierObservation`, `ScreenStage`, `DeliveryDetails`, `BoostSlot`, `CourierScreens`, `OfferRules`, `ScreenDeduper`, `SliderSpinner`, `NoteFields`, `Dialer`, `NameMatch` (2.10), `OwnLines` (2.10), `OfferCapture`, `OcrPace`, `OcrAcceptance` (3.5), `CaptureTiming`, `NavDistance`, `NotificationDedup` (4.1), `ScreenReading`, `ScreenReadingGuard`, `TargetApps` (1.9), satır biçimleri (`ScreenRow`, `NotifRow`: 6.1–6.2'deki alanlardan `JSONObject`), `CaptureSink`, metin tablosu (`CaptureText`, TR/AZ).

**`CaptureSink`** (`logic/capture`; `:orders` uygular, `:app` bağlar; bağlanmamışsa `NONE` = hiçbir şey yapmaz, `awaitingPhone` false):

```kotlin
interface CaptureSink {
    fun onScreen(pkg: String, texts: List<String>, actions: List<String>, at: Long, source: String,
                 spinner: Boolean, checked: Boolean?, parsed: CourierObservation)   // OrderTracker.onScreen
    fun onClick(pkg: String, label: String, at: Long)                             // OrderTracker.onClick
    fun onNotification(pkg: String, text: String, at: Long)                       // OrderTracker.notification
    fun onNavigation(lines: List<String>, at: Long)                               // OrderTracker.navigation
    fun awaitingPhone(now: Long): Boolean                                         // OrderTracker.awaitingPhone
    fun onDialer(texts: List<String>, at: Long)                                   // OrderTracker.dialerPhone
}
```

Çağrılar bugünkü sırayla ve aynı iş parçacığında (ana) yapılır; kayıt iznine `:capture` bakar, `:orders` kendi tarafında yeniden bakabilir (Rawstats `OrderTracker` her girişte bakar).

**Android `:capture` (paket `com.kuryelog.app` servis adları için, gövde `com.kuryelog.app.capture`):** `WatchService`, `NotifService` (adlar değişmez), ekran görüntüsü + ML Kit + maskeleme, E7 uyarı dinleyicisi (`ShiftListeners`, 5.2), `CaptureHost`:

```kotlin
interface CaptureHost {                       // :orders uygular (rozet, balonlar, sipariş tiki); :app bağlar
    fun onConnected(service: AccessibilityService)                // OrderTracker.init + DeliveryReminder kurulumu
    fun onTick(service: AccessibilityService, now: Long, shiftOn: Boolean)  // 1.6 adım 3–4
    fun onDisconnected(service: AccessibilityService)             // pencereleri kaldır
}
```

`TYPE_ACCESSIBILITY_OVERLAY` pencereleri yalnız erişilebilirlik servisinin bağlamıyla açılır; bu yüzden `:orders` rozet ve balonları `service` üzerinden açar, `:capture` onları tanımaz. Maskeleme için `:core`'daki `OwnOverlays` kaydı (3.5).

**Bağlama:** `WeemApplication` (`:app`) açılışta `Capture.sink = <:orders>` ve `Capture.host = <:orders>` yazar; `:capture` yalnız arayüzü görür. Bağlanmamış hâlde (Aşama 3 kodu gelene kadar) servis okur, satır yazar, hiçbir sipariş işi yapmaz.

**Taşınanlar:** `ManualSessionPolicy` (A2.4'te `logic/core`), `Store.canRecordScreen` → Android `Store.canRecordScreen(ctx)` (`ManualSessionPolicy.canCapture` + `importing`), `Store.targets`, `Store.phoneBattery`, `Store.locJson` → `LogStore.locJson` (A2.3), `Store.failure` → `LogStore.failure`.

### 7.2 Metinler

Kullanıcıya görünen her `:capture` metni (`OCR ekranı okunamadı…`, `Rozet açılamadı:`, E7 başlık/metin/kanal adı, `OCR yedeği` anahtarı, izlenen uygulamalar kartı) `CaptureText` tablosunda TR ve AZ; `TextCoverageTest` benzeri kapsam testi; Android kaynağında düz TR/AZ metin yok (A1.3 kuralı).

### 7.3 Ağ yasağı

- `:capture` (saf ve Android) kaynakta ağ sınıfı kullanmaz: `java.net.*` (`URL`, `HttpURLConnection`, `Socket`, `InetAddress`…), `javax.net.*`, `okhttp3`, `android.net.http`, `Ktor`, `Retrofit`, `WebView` yok — **kaynak taraması testi** (A3.4).
- **Çalışma sınıf yolu izin listesi** (A3.4): `:capture`'ın `releaseRuntimeClasspath`'i bir izin listesiyle karşılaştırılır (`logic/capture`, `logic/core`, Kotlin stdlib, coroutines, AndroidX çekirdek, ML Kit text-recognition ve zorunlu bağımlılıkları); listede olmayan yeni bağımlılık testi kırar.
- **ML Kit telemetrisi (risk):** ML Kit, model cihazda olsa da API kullanım ve performans ölçümlerini Google'a göndermek için Google veri aktarım kütüphanesini (`com.google.android.datatransport`) getirir. Rawstats'ta INTERNET izni yoktu, gönderemiyordu; WEEM internetlidir (harita, hava, uzaktan kullanım). Varsayılan çözüm: `:capture`'ta ML Kit bağımlılığından gönderim arka ucu (`transport-backend-cct`) çıkarılır; A3.4 bağımlılık ağacını (`./gradlew :capture:dependencies`) bu bölüme ekler; A3.7 OCR'ın çıkarılmış arka uçla cihazda çalıştığını doğrular (doğrulanmadı). Çıkarma OCR'ı bozarsa kullanıcıya sorulur (9).
- **A3.4 sonucu (17 Ekim, bu ortamda; cihazda doğrulanmadı):**
  - `./gradlew :capture:dependencies --configuration releaseRuntimeClasspath`: ML Kit `text-recognition:16.0.1` (paketli model, `assets/mlkit-google-ocr-models`) → `text-recognition-bundled-common:17.0.0`, `play-services-mlkit-text-recognition(-common):19.x`, `mlkit:common:18.11.0`, `vision-common:17.3.0`, `vision-interfaces:16.3.0`, `play-services-base/basement/tasks`, `firebase-components/encoders(-json)/annotations`, `odml:image`, AndroidX (appcompat, fragment, lifecycle…) ve `datatransport:transport-api:2.2.1`, `transport-runtime:2.2.6`, **`transport-backend-cct:2.3.3`** (tek HTTP gönderen; manifesti `INTERNET` ister ve `TransportBackendDiscovery` servisine `backend:…CctBackendFactory` kaydını koyar). Tam liste: `capture/runtime-allowlist.txt` (63 modül).
  - **Kütüphaneyi çıkarmak OCR'ı bozar:** ML Kit'in kayıt sınıfları (`mlkit:common` `zzsp`, `vision-common` `zzmp`) yapıcıda `CCTDestination.INSTANCE`'a doğrudan bakar, `try` yok → `transport-backend-cct` dışlanırsa ilk kullanımda `NoClassDefFoundError`.
  - **Uygulanan (S17 "telemetri çıkar"):** sınıflar kalır, **arka uç kaydı kaldırılır** — `capture/src/main/AndroidManifest.xml` `TransportBackendDiscovery` servisindeki `backend:com.google.android.datatransport.cct.CctBackendFactory` meta-data'sı `tools:node="remove"` (birleşik `:app` manifestinde servis meta-data'sız kaldığı doğrulandı). `transport-runtime` arka uç bulamaz (`MetadataBackendRegistry`: "returning empty list of transport backends"), yükleyici olayı **göndermeden siler** (`Uploader`: "Unknown backend … deleting event batch"). Telefonda OCR'ın çalıştığı ve ağ trafiği olmadığı A3.7'de denenir (doğrulanmadı).
  - **Sınıf yolu izin listesi:** `:capture:verifyRuntimeAllowlist` (`preBuild`'e bağlı; CI her Android derlemesinde koşar) `releaseRuntimeClasspath`'teki her `grup:ad`'ı `capture/runtime-allowlist.txt` ile karşılaştırır. Kaynak taraması ve izin listesi içeriği: `logic/capture` `NoNetworkTest`.
- 2 telefon modunda `:capture` hiçbir şey göndermez: Receiver'a giden her şey `:orders` mesajlarıdır (plan 4.4); ham satırlar (`screens`, `notifs`) Provider'dan çıkmaz.

### 7.4 Hiçbir şeye basmama

- `accessibility_config.xml`'de `canPerformGestures` yok; `WatchService` yalnız okur.
- **Kaynak taraması testi** (A3.7, `:remote` hariç tüm modüller): `performAction(`, `dispatchGesture(`, `performGlobalAction(`, `GestureDescription` yok; `AccessibilityNodeInfo.ACTION_` sabiti yok. Uzaktan kullanımın dokunuşu ayrı bir serviste (plan 4.6; A9 kendi kuralını yazar).
- Okunan pencereye hiçbir şey yazılmaz (`ACTION_SET_TEXT` yok), pano okunmaz (`ClipboardManager` `:capture`'ta yok).

### 7.5 Desenler: ICU ve JDK 21

- **Yasak yapılar** (RT `IcuRegexTest.kt:25-39`, WEEM'de **tüm modüller**: `logic/*/src/main`, Android modülleri, `app`): satır içi `(?U)` (ve `U` içeren bayrak grupları), `UNICODE_CHARACTER_CLASS`, `CANON_EQ`, `\p{java…}`. Yorum satırları sayılmaz. Gerekçe: Android her deseni ICU'ya verir; ICU bunları tanımaz, sınıf yüklenemez (0.25.0'da ilk bildirimde uygulama kapandı).
- **Sınıf anlamı farkı (yeni bulgu, 17 Ekim):** JDK 19'dan beri `\b` yalnız ASCII sözcük karakterlerine bakar (`\w` ile tutarlı); Android ICU'da `\b`, `\w`, `\d`, `\s` Unicode'dur. Rawstats testleri JDK 17'de koşuyordu (orada `\b` Unicode harfini sözcük sayardı); WEEM testleri JDK 21'de koşar. Bu ortamda denendi: `\b\d+\s*(?:min|мин|ч)\b` "3 мин · 1,2 км"de JDK 21'de **eşleşmez** (ICU'da eşleşir); `кв\b` "кв 5"te eşleşmez; `этаж\s*(\d{1,3})\b` "этаж 5й"de eşleşir (ICU'da eşleşmez).
- **Kural (WEEM):** ASCII olmayan metin görebilen desenlerde `\b` yerine açık sınır `(?<![\p{L}\p{N}_])` / `(?![\p{L}\p{N}_])` (Rawstats'ın `NavDistance.durationU` ve boost sözcüğünde zaten kullandığı biçim), boşlukta NBSP olabilecek yerde `[\s\p{Z}]`. Cihaz davranışı değişmez (ICU'nun `\b`'si bunun karşılığıdır); JVM testi cihazdaki sonucu verir. Bilinen yerler: `NavDistance` `duration` (`summary`), `NoteFields` `:16-45` (`\b` rakamdan sonra/önce) ve `otherFieldWord` `:108` (`\bkv\b`, `кв\b`, `\bkat\b`), `ScreenDeduper` `clock`/`metric`, `CourierParser.isAccept` (`\b\d+\s*(min|deq|dk)\b`) ve parser'daki diğer `\b`'ler. A3.4 bütün `Regex(` kurulumlarını (birleştirilerek kurulanlar dahil) tarar, gerekenleri çevirir ve her çevrilen desen için Kiril/NBSP girdili bir JVM testi ekler (beklenen = ICU sonucu). Taramanın sonucu bu bölüme liste olarak eklenir.
- **A3.4 taraması (17 Ekim, `logic/capture` 84 `Regex`/`rx` kurulumu):** çevrilenler — `CourierParser.norm` ve `OfferRules.norm` `\s+` → `[\s\p{Z}]+` (NBSP boşluk olur; normlanmış metni okuyan bütün ayrıştırıcı desenleri artık ASCII boşluk görür), `CourierParser.isAccept` süre etiketi, `NavDistance.summary` (`duration` kaldırıldı, `durationU` kullanılır), `NoteFields` 13 kural + `ORD` + `otherFieldWord`, `ScreenDeduper` `clock`/`metric` (`\b` → açık sınır, `\s*` → `[\s\p{Z}]*`) ve `countdown`. Sınırlar `CourierParser.WORD_START`/`WORD_END`. Bilerek bırakılanlar: `^now\W*boost$` (normlanmış, `now` ile `boost` arasında harf beklenmez), `\d` (ICU'da Unicode rakam; Wolt/Bolt ASCII rakam yazar). Test: `RegexPortabilityTest` (6; düzeltmeden önce 6'sı da kırmızıydı) — Kiril birim, `кв 5`, `этаж 5й`, `дом5 этаж`, NBSP'li süre/kabul/km, `к12:30`; ayrıca `logic/capture` kodunda `\b` kalmadığını tarar.

## 8. Test eşlemesi (A3.2 bunu izler)

Kural: `logic/capture` testleri `:orders` sınıflarını göremez (bağımlılık yönü). Bu yüzden `TEST_ENVANTERI`'nde `:capture` hedefli 15 dosyanın testleri **gövdelerine göre** bölünür: yalnız ayrıştırıcıya ve saf `:capture` sınıflarına dokunan test `logic/capture`'a; `DeliveryCore`, `OrderTracker`, `RecordJson`, `OnlineSessions`, `CashPayment`, `Readiness`, `OrderHistory`, `BoostLog`, `PlatformHistory` ya da `OverlayText` biçimlendiricisine dokunan test `logic/orders`'a (aynı dosya adı, iki modülde). Ortak yardımcılar (kare kurucular, saha kareleri yükleyicisi) `logic/capture`'ın **`testFixtures`**'ında (Gradle `java-test-fixtures`); `logic/orders` testleri oradan alır. Sınıflandırma test gövdeleri ve kullandıkları sınıf/yardımcı alanlarıyla yapıldı (A3.1 betiği), sonra elle denetlendi.

| Eski dosya (RT) | Test | `logic/capture` | `logic/orders` | Başka | Not |
|---|---:|---:|---:|---|---|
| `BoltBubbleR2Test` | 5 | 4 | 1 | | orders: `restaurantBubbleCountsDownFromTheBlueBubble` (`Readiness`) |
| `CashScreenM16Test` | 7 | — | 7 | | `CashPayment` (K31/M16) `:orders`'ta (`ORDERS.md` R8) |
| `OcrPaceD6Test` | 11 | 9 | 2 | | orders: `offerRecordsWhenItsFeeWasRead`, `lateFrameWithTheFeeFillsItsTime`; `sahaCardsWithoutAFeeFirstAreHot` saha karelerini kullanır |
| `ParserEconomicsTest` | 23 | 20 | 3 | | orders: `ocrOfferWithBadgeFeedsTheTripWithTheCardFee`, `panelFormatsMatchTheFieldBadge`, `everyPanelLineIsRecognisedInBothLanguages`; yalnız tanıma yapan `ocrVariantsOfPanelLinesAreRecognised`, `courierAppLinesAreNeverPanelLines` `OwnLines` ile capture'da |
| `RecordingPolicyTest` | 12 | 4 | — | core 2, çevrilmez 6 | capture: `OfferRules` 2, `ScreenDeduper` 2; core: `restoreOnlyAcceptsKnownLogsInsideAnActualDay`, `reimportKeepsNewRowsAndDoesNotDoubleOldOrders` (`BackupFiles`, A2.3 kodu; WEEM'de henüz testi yok); çevrilmez: `MotionGate` 4, `OfferIdentity` 2 (2.9) |
| `ScreenReadingE7Test` | 4 | 3 + 1 uyarlanır | — | | uyarlanan `shiftServiceWarnsAndTheHomeShowsIt` kaynak taraması: `:capture` uyarı dinleyicisinde `ScreenReadingGuard(`, metin tablosundaki başlık, `ACTION_ACCESSIBILITY_SETTINGS`, `setOngoing(true)`, `TYPE_APPLICATION_OVERLAY`; elle vardiya başlatma ekran okumaya bakmaz; vardiya kartı uyarıyı gösterir; `WatchService` bağlılığı yazar; iki serviste `HomeWatch.refresh(` |
| `WoltDeliverySliderTest` | 9 | 3 | 6 | | |
| `WoltOfferFeeK37Test` | 6 | 2 | 4 | | capture: `theSyntheticFramesAreOfferCardsWithoutAFee`, `ocrFallsBackToTheDisplayAfterAnInvalidWindow` |
| `WoltSliderSpinnerK33Test` | 9 | 2 | 7 | | capture: `onlyTheProgressBarClassIsASpinner`, `aRoundIndicatorOnTheSliderRowCountsALinearBarDoesNot` |
| `WoltStatsF81Test` | 6 | 6 | — | | `statsPeriod` testleri saat dilimini `Asia/Baku` verir |
| `IcuRegexTest` | 2 | 2 | — | | tarama **tüm modüllerde** (7.5); Kiril `metres` testi aynen |
| `CourierFlowTest` | 44 | 15 | 27 | core 2 | core: `automaticAndStickyStartsCannotCreateShift`, `captureNeedsManualSessionOnly` (`ManualSessionPolicy`; WEEM'de henüz testi yok) |
| `DeliveryRecognitionTest` | 24 | 11 | 13 | | capture: müşteri kartı aşaması, destek/istatistik sayfaları, bekleme paneli, iki çevrimdışı ekran, not alanları (5), `truncatedNoteDoesNotReplaceTheFullOne`, `routeDistanceComesOnlyFromATripSummary` |
| `SahaTekrarTest` | 16 | 6 | 10 | | **altın.** capture: `karelerYuklenirVeKisiselVeriYok`, `d22HazirEtiketleri`, `d19BeklemeKarelerininEnAz95iTaninir`, `d5BolunmusEkranKarelerininHepsiBekleme`, `d1KartKazanciOkunurParaUstuAsla`, `d21ExpectedEarningsTekBasinaTeklifIsareti` |
| `WoltFlowK1K5Test` | 25 | 5 | 20 | | capture: `demandMapAloneIsNoAvailabilityEvidence`, `ocrSliderVariantsAreRead`, `waitingSheetWithToolbarIconIsNotTheSideMenu`, `woltBuildingNameAndColumnOrderedRow`, `ocrRowOrderEntranceFloorApartment` |
| **Toplam** | **203** | **93** | **100** | core 4, çevrilmez 6 | |

Karışık dosyalarda capture'a düşen test adları (geri kalanı orders): `CourierFlowTest` — `pricedBoltButtonIsAccept`, `woltOfferHasDistinctStopAndOrderCounts`, `plusCodeIsRetainedAsRestaurantAddress`, `customerAddressInOfferIsCaptured`, `readyTimeIsSeparateFromDeliveryEstimate`, `negativeClockIsNotTravelEta`, `woltCollectTranslationIsNotDelivery`, `customerDetailsCaptureSparseFieldsOnly`, `notesContainingPhoneStayOriginal`, `longDirectionsStayComplete`, `checkIdRequirementCaptured`, `sliderIsContextualArrivalNotCollection`, `futureBonusIsRecordedWithStartTimeNotAnOffer`, `woltBanIsOfflineNotAnOrder`, `multiplierIsNotMoney`. `DeliveryRecognitionTest` — `woltCustomerCardIsDropoffWithDeliverySlider`, `supportStatsAndSettingsPagesAreNotOrderScreens`, `woltWaitingPanelWithQuickLinksIsStillWaiting`, `bothOfflineScreensAreOffline`, `emptyFieldsAreFilledFromTheCustomerNote`, `platformFieldWinsOverTheNote`, `azerbaijaniNoteWordsAreRecognised`, `phoneNumbersInNotesAreFound`, `ordinaryNumbersAreNotPhones`, `truncatedNoteDoesNotReplaceTheFullOne`, `routeDistanceComesOnlyFromATripSummary`. `OcrPaceD6Test` — iki orders testi dışındakiler.

**Saha kaynakları:** RR `saha/saha_kareleri_2026-10-11_12.jsonl.gz` (6.481 kare, kişisel veriden temizlenmiş; satır `{gun, t, pkg, bounds, src, texts, actions}`) ve `saha/saha_beklenen_2026-10-11_12.json` (`surum, kaynak, fixture, gunler, ozel_durumlar, kabul_olcutleri`) **bayt bayt** `logic/capture/src/testFixtures/resources/saha/`'ya kopyalanır (iki modülün testleri de okur). `karelerYuklenirVeKisiselVeriYok` kopyanın bütünlüğünü de doğrular (6.481 kare, telefon yok).

**Eski testi olmayan davranışlar için yeni testler** (A3.4; karakterizasyon, beklenen = Rawstats kodunun çıktısı):

- `TargetAppsTest`: kendiliğinden ekleme kuralı (Wolt partner/courier/kuryer; Bolt/mtakso + courier/food/driver/kuryer/partner; yalnız ekler; `userPicked`'te hiçbir şey), elle seçim sırası.
- `NotificationDedupTest` (R2'nin eski testi yok): aynı anahtar + aynı içerik yazılmaz; içerik değişince yazılır; "removed" anahtarı siler (izin yokken de); izin yokken süzgeç boşalır; navigasyon paketleri hiç yazılmaz; hedef dışı paket yok sayılır; satır alanları ve `redact`.
- `OcrAcceptanceTest`: 1 800 ms bayatlık sınırı (1 799 kabul, 1 800 atılır), pencere gitti, vardiya değişti, satır yok; erişilebilirlik PICKUP/DROPOFF iken OCR OFFER/IDLE/OFFLINE atılır, OCR DROPOFF kabul edilir; "vardım" eylemi yalnız `slide to confirm` varken türetilir.
- `CaptureTimingTest`: 180 ms ikinci okuma, 500/2 000 ms tik, 5 sn ev tiki sabitleri tek yerde.
- `RegexPortabilityTest` (7.5): `NavDistance.summary(["3 мин · 1,2 км"])` = satırın kendisi; `NoteFields.holdsOtherFields("кв 5")` = true; `NoteFields.extract("этаж 5й").floor` = null; NBSP'li "22 min" kabul etiketi; çevrilen her desen için bir satır.
- `NoNetworkTest` (7.3) ve `NoGestureTest` (7.4): kaynak taramaları; `:capture` sınıf yolu izin listesi (Android build'inde, A3.7).

**A3.7 durumu (17 Ekim):** Android `:capture` yazıldı — `WatchService`, `NotifService` (Rawstats satır satır; farklar 1.2, 1.6, 1.8, 6.3 ve `ORDERS.md` 16.3), `CaptureHost`/`CaptureState`, `ScreenReadingWarning` (E7, `ShiftListeners` dinleyicisi) ve `CaptureStatus` (vardiya kartı satırı, erişilebilirlik ayarı intent'i); `OwnOverlays` `:core`'da. Testler: `ScreenReadingE7Test` 4/4 yeşil, `NoGestureTest` 4 (dokunuş/hareket yok, yapılandırma, pano yok, `acc_desc` sözü — eski `OfferNetLineTest.accessibilityDescriptionSaysTheNumberStaysOnThePhone`), `CaptureCrashGuardTest` 6 (eski `CrashGuardTest`'in ekran okuma ve sipariş adımı payı). Cihazda doğrulanmadı: OCR'ın çıkarılmış CCT arka ucuyla çalışması ve ağ trafiği olmaması (7.3), ekran okuma, rozet, balonlar.

**A3.4 durumu (17 Ekim):** yazıldı — `TargetAppsTest` (3), `NotificationDedupTest` (6; satır alanları dahil, `NotifRowTest` yerine), `OcrAcceptanceTest` (4), `CaptureRowsTest` (4; `ScreenRowTest` + `CaptureTimingTest`), `RegexPortabilityTest` (6), `NoNetworkTest` (3: kaynak taraması, bağımlılık izin listesi, ML Kit arka ucu), `CaptureTextTest` (3; Android `:capture`'ta düz TR/AZ metin yok). Kalan: `NoGestureTest` (A3.7, Android kaynağı yazılınca); `ScreenReadingE7Test.shiftServiceWarnsAndTheHomeShowsIt` Android bağlantısını taradığı için A3.7'ye kadar kırmızı (metin tablosu kısmı A3.4'te yeşil).
- `CaptureTextCoverageTest` (7.2).
- Kayıt satırları: `ScreenRowTest`, `NotifRowTest` (6.1–6.3 alanları, null alanın yazılmaması, `click.text` `redact`'i).

## 9. Açık noktalar, riskler ve bilerek farklar

**Kullanıcıya sorulabilecek (şimdi değil; olursa):**
1. **ML Kit ve ağ** (7.3): A3.4'te arka uç kütüphanesi değil **kaydı** kaldırıldı (kütüphaneyi çıkarmak `NoClassDefFoundError` verirdi). Cihazda OCR çalışmazsa seçim kullanıcının: OCR'sız (yalnız erişilebilirlik; Wolt kartları okunmaz) ya da ML Kit'in ölçüm göndermesine izin (A3.7'de denenir).

**Riskler (doğrulanmadı):**
2. Pixel 10 Pro (API 36) üzerinde `takeScreenshotOfWindow`, ekran görüntüsü aralığı (333 ms) ve erişilebilirlik ağacının bugünkü Wolt 2.202.6 / Bolt sürümlerinde aynı davranması; ayrıştırıcı bugünkü ekran metinlerine bağlı (saha kareleri 11–12 Ekim). Wolt/Bolt güncellemesi bozarsa gölge mod (A3.9–A3.10) yakalar.
3. JDK 21 `\b` farkı (7.5): A3.4 taraması tamamlanana kadar JVM'de geçen bir ayrıştırıcı testi cihazda farklı sonuç verebilir (Kiril, NBSP).
4. Restoran/müşteri balonları tüm ekran görüntüsünde maskelenmez (Rawstats'ta da); balon metni OCR satırlarına karışabilir. Saha karelerinde görülmedi; gölge modda izlenir.
5. Gölge modda iki uygulamanın pencereleri birbirinin tüm ekran görüntüsüne girebilir (6.5).

**Bilerek farklar (WEEM ≠ Rawstats; gölge mod raporu bunları "beklenen fark" sayar):**
- Ekran okuma uyarısı bildirimi 5103 → **5104** (`CORE.md` 3.3); metindeki servis adı "WEEM ekran kaydı".
- `click.text` `redact`'lenir (6.3).
- `ScooterLink.publish` tikten çıkar (YolGo içeride).
- `MotionGate`, `OfferIdentity` yazılmaz (2.9).
- ASCII dışı metin gören desenlerde açık sınır (7.5; cihaz sonucu aynı).
- Koruma (`guarded`) `VirtualMachineError` dışındaki her `Throwable`'ı yakalar (1.8).
- Manifest `<queries>`'de `az.duzyol.app` yok (S7).

**Ham notlardaki düzeltmeler (`bulgular/R_capture.md`):** "Wolt kartında km yalnız tam satır" yanlış — tam satır yoksa her karede olduğu gibi metnin ilk `N km`'si (2.6). "R16 kapsamı kullanıcı kararı" kapandı: R16 ilk sürüm listesinde, kalır; YolGo kaynağının yerini `NavPort` alır (4.2). "Rozet `:orders`, servis `:capture` ayrımı kararı gerekir" kapandı: `CaptureHost` (7.1). E7 metinlerinin Azerbaycanca karşılıkları eklendi (5.2). Vardiyada her tikte okuma kurulduğu (1.6) ve bildirim olayının ikinci (180 ms) okuma kurmadığı (1.3) eklendi.
