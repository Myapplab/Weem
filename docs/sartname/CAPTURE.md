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
