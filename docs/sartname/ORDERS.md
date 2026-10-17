# ORDERS — Aşama 3 şartnamesi (`:orders`: R3 yaşam döngüsü, R5–R13, R15, R19)

Durum: A3.1, 17 Ekim 2026 (Claude Opus · max, tek ajan; S14) — **yazılıyor, bölüm bölüm**. Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04`; satır numaraları bu commit'te açılıp doğrulandı. `bulgular/R_orders.md`'nin yerini alır.

Kısaltmalar: **RS** = `app/src/main/java/com/kuryelog/app/`, **RT** = `app/src/test/java/com/kuryelog/app/` (rawstats). Ekran okuma, ayrıştırıcı ve gözlem tipi (`CourierObservation`): `CAPTURE.md`. Davranış metni kodun söylediğidir; "WEEM" satırları bu şartnamenin kararıdır.

**Kim kullanır:** A3.2 (sipariş akışı testleri: Bölüm 15 + `CAPTURE.md` 8'in orders payı), A3.3 (para, platform, rozet, balon, gün sayfası testleri: Bölüm 15), A3.5 (durum makinesi: Bölüm 1–4), A3.6 (para ve platform: Bölüm 7–12), A3.7 (Android: Bölüm 4–6, 13), A3.8 (Siparişler sekmesi: Bölüm 11), A3.9 (gölge mod: Bölüm 14). Plan bağlamı: `WEEM_PLANI.md` 4.2–4.4, 8 (S5, S7, S8, S9).

**Taşıma kuralı (WEEM):** saf sınıflar (`DeliveryCore` ve yardımcıları, `VenueArrival`, `ArrivalDistance`, `DeliveryDeadline`, `Readiness`, `TripStages`, `OverlayText`, `CashTips`/`CashPayment`, `PocketCash`, `CashPending`, `BoostLog`, `OnlineSessions`, `OrderHistory`, `RecordPages`, `RecordJson`, `PlatformTotals`, `PlatformHistory`, `OfferWeather`) Kotlin'den Kotlin'e **satır satır kopyalanır** (`logic/orders`, paket `com.kuryelog.logic.orders`); değişenler yalnız bu belgedeki "WEEM" satırları. `OrderTracker` (Android yapıştırıcısı) ise saf bir `OrderFlow` (karar ve sıra) + ince Android katmanına bölünür (Bölüm 4).

## 0. Özet: WEEM kuralları

| Konu | WEEM kuralı | Neden / kaynak |
|---|---|---|
| Durum makinesi | `DeliveryCore` aynen: platform başına tek bekleyen teklif, sipariş fazları `TO_RESTAURANT → WAITING → TO_CUSTOMER → AT_CUSTOMER → FINISHED`, doğrulanamayan son `END_UNCONFIRMED`; her geçiş bir olay (`CoreEvent`) ve neden dizgisi | RS `DeliveryCore.kt:1-898` |
| Teklif sonucu | Kabul = teklif ekrandayken (ret dokunuşu yok) görev ekranı; ret = ret dokunuşu/afişi/onayı ya da kararlı bekleme haritası; belgeli pencere dolmuşsa zaman aşımı (`expired_inferred`); hiçbiri `rejected` sayılmayan sonuçlar ayrı | Bölüm 2 |
| Bir anda tek müşteri | K34: bir sipariş müşteri aşamasına girince öteki müşteri aşamasındakiler "doğrulanmamış teslim" (`delivered_inferred`) olur; asla `delivered` değil | RS `DeliveryCore.kt:555-573`; kullanıcı kuralı 8 Ekim |
| Modül sınırı | `:orders` → `:core`, `:capture` (gözlem tipleri). `:economics` (R4, ekonomi v2 kayıtları, bekleme gözlemi) ve rota/enerji `:orders`'a arayüzle bağlanır: `:orders` `OrderObserver` tanımlar, `:economics` uygular; scooter `ScooterBridge`, rota `NavPort`, hava `WeatherPort` (`:core`) | Plan 4.2–4.3; Bölüm 13 |
| Ekonomi öncülleri | `DeliveryCore`'un okuduğu iki değer (ret penceresi 60 dk, belgeli teklif penceresi Wolt/Bolt 60 sn) `:orders` sabiti; `:economics` testi `PRIORS` ile eşitliğini denetler | RS `DeliveryCore.kt:863-872`, `EconomicsV2.kt:84-105` |
| Kayıt | `orders.jsonl`, `courier.db` (`trips`, `offers`, `session`), `online-sessions.jsonl`, `platforms.jsonl`, `economics-boosts/cash-tips/pocket-cash/cash-pending/platform-totals/platform-history.jsonl`: ad, alan, anlam aynı | `VERI_SOZLESMESI` 6, 7.1 |
| Müşteri verisi | Telefon yalnız `courier.db` + bellek + kart/balon + bildirim 12; ad `courier.db`, `orders.jsonl` (`record`) ve `latest` gözleminde; 2 telefon modunda Receiver'a ad, telefon, not gitmez; koordinat ve adres metni gider (S5) | `VERI_SOZLESMESI` 9 |
| YolGo yerine | Rawstats'ın YolGo ile konuştuğu her yol (içerik sağlayıcı feed v1/v2, `stages`, `nav/v1/current`, hava akışı, değişim duyurusu) WEEM'de süreç içi arayüzdür: `OrderEvents.offers/stages/day`, `NavPort.route`, `WeatherPort.current` | S7; plan 4.3–4.4 |
| Bildirim numaraları | 12 (`delivery_notes`), 4901 (`pocket_cash`), **10000–14095** (`cash_tips`, eskiden 4000–8095) | `CORE.md` 3.3 |

## 1. Veri modeli (RS `DeliveryCore.kt:1-172`)

### 1.1 Fazlar ve teklif

- `TripPhase` = `TO_RESTAURANT | WAITING | TO_CUSTOMER | AT_CUSTOMER | FINISHED | END_UNCONFIRMED` (`:3`). Rozet metinleri `OverlayText.PHASES` (Bölüm 6).
- **`OfferState`** (`:4-75`): `id` = `offer-<firstAt>-<sıra>` (sıra çekirdek başına artar), `pkg`, `firstAt`, `data` (birleşik gözlem); teklif anı eklentileri `phoneBattery`, `lat`, `lon`, `gpsAccuracyM`, `gpsAgeMs`, `scooterBatteryPct`, `scooterObservedAt`, `scooterSource`; boost `boostValue`, `boostUnit`, `boostSource`, `boostSeenAt` (K27; kaynak var ama değer yok = "boost'suz görüldü", kaynak null = eski kayıt); hava `weather` (K28); `lastSeenAt` (kartın son karesi); `sessionId`; `declineClickAt`; `optional` (Bolt); `fieldSource` (alan → `accessibility`/`ocr`); D6 zamanları `feeAt` (ücretin ilk okunduğu kare), `feeGapMs` (önceki kareden bu yana, ilk ya da geç karede null), `feeOcrMs`, `notifiedAt`; `readyInAt` (E15; yalnız bellek).
- **`enrich(o)`** (aynı kartın yeni karesi): ücret ilk kez geldiyse `feeAt` = kare anı, `feeGapMs` = önceki kareden fark (> 0 ise); `lastSeenAt` = en büyüğü. Alan başına seçim: yeni değer yoksa eski; eski var ve yeni kare OCR ve alanın kaynağı OCR değilse **eski** (OCR erişilebilirlik değerini asla ezmez, N11/K7); değilse yeni (kaynağı yazılır). Birleşen alanlar: `gross, km, minLo, minHi, stops, quantity, restaurant, restaurantAddress, customerAddress, readyIn, orderIds (boş = yok), approachMin, deliveryMin`; `offerCash` VEYA; `changeNeeded` yeni ?: eski. Diğer bütün alanlar (aşama, ham satırlar, boost…) **yeni kareden** gelir. `readyIn` bu kareden alındıysa `readyInAt` = kare anı.
- **`backfill(o)`** (K37, geç gelen eski kare): yalnız hâlâ boş alanları doldurur (yukarıdaki liste, `orderIds` hariç); `offerCash` VEYA, `changeNeeded` eski ?: yeni; ücret ilk kez → `feeAt`; `readyIn` ilk kez → `readyInAt`. Hiçbir değeri ezmez.
- **`same(o)`** (aynı kart mı, K7): restoran benzer **ve** (iki ücret de varsa fark < 0,011) **ve** (iki km de varsa fark < 0,151) **ve** müşteri adresi benzer. **`OfferMatch.similar(a, b)`** (`:945-960`): bir taraf yoksa true; sözcükler `norm` + harf/rakam dışı boşluk + OCR karışıkları (`1`, `i`, `|` → `l`; `0` → `o`); boş kalırsa true; kısa metnin ilk sözcüğü uzununkine eşit ve her sözcüğü uzunda var (≥ 5 harfli sözcükte 1 düzenleme farkı eşit).

### 1.2 Sipariş kayıtları

- **`PickupRecord`** (`:76-82`): `restaurant`, `address`, `readyAt` (ilk "ready in"), `arrivedAt`, `pickedAt`, `arrivalSource`, `readyObservedAt`, `note`, `code`, `items`, `readyHintAt` (son "ready in"), `preparingAt`, `reminderAt`.
- **`CustomerRecord`** (`:83-90`): `id`, `details` (`DeliveryDetails`, `CAPTURE.md` 2.8), `arrivedAt`, `deliveredAt`, `lat`, `lon`, `coordinateSource`, `reminderAt`, `detailsAt`, `deadlineClock`, `deadlineAt` (K29), `arrivalSource` (M19).
- **`TripRecord`** (`:91-163`): `id` = `trip-<acceptedAt>-<sıra>`, `pkg`, `offer`, `acceptedAt`, `phase`, `pickups`, `customers` (sıralı), `currentCustomer`, `expectedOrders` (1), `completedAt`, `distanceM`, `usedWh`, `recoveredWh`, `energyCoveredMs`, `energyShared`, `tip`, `commissionPct`, `priceIsNet` (eski kayıt), `pickupIntentAt`, `finishIntentAt`, `latest` (son görev karesi), `missedOffer`, `deliverSeenFor/At` (teslim kaydırıcısı görülen müşteri); `sessionId`; K34 `deliveredInferredAt/By/Reason`; K33 `deliverSpinnerAt/For`; K31/M16 `paymentMethod` (`cash`/`card_inferred`), `paymentSource`, `cashAmount`, `paymentAt`, `cashNoChange`, `cashChangeAzn`, `cashCheckedAt`; `tipSource` (`user_cash_input`/`user_manual`); D3 `verifiedBy/At`, `cashCollectedAt/Azn`; D15 `extraWaitAzn`, `platformTipAzn`; D10 `energyPartialMs`. **Yalnız bellek:** `woltShortIds`, `sameDropoff` (R1), `callFor/callAt`, `handoverHintFor/At/Kind` (M22).
- Türetilenler: `active` = FINISHED ve END_UNCONFIRMED değil; `deliveredInferred` = `deliveredInferredAt` var ∧ faz END_UNCONFIRMED; `countsAsDelivered` = `completedAt` var ∨ `deliveredInferred` (D3(c): gün sayısına, nete, ortalamalara girer); `atCustomer` = aktif ∧ TO/AT_CUSTOMER; `actualMinutes` = (`completedAt` − `acceptedAt`) / 60 000; `gross` = teklif ücreti; `earnings` = `gross` + `extraWaitAzn`; **`net` = `earnings` × (1 − `commissionPct`/100) + `tip`** (teklif fiyatı her iki platformda brüt; `priceIsNet` artık komisyonu atlatmaz); `waitMinutes` = Σ(`pickedAt` − `arrivedAt`, ≥ 0); `lateMinutes` = Σ(`pickedAt` − max(hazır, `arrivedAt`), ≥ 0), hazır = `readyAt` ?: `readyObservedAt`; `spinnerOnSlider` = göstergenin müşterisi = kaydırıcınınki = şimdiki müşteri.
- **`CoreEvent(type, pkg, at, offerId, tripId, reason)`** (`:164`); `OfferOutcome(offerId, pkg, at, type, reason, optional)` ve `DeclineWindow(at, countsAsDecline, explicit, windowMs)` (`:166-171`).
- WEEM: tipler `logic/orders`'ta; `:economics` "kayıt tipleri" istisnasıyla görür (plan 4.2). S8 (17 Ekim) "Net = brüt + bahşiş − filo komisyonu − enerji maliyeti" gün sonu netidir (`:economics`, N4); sipariş kaydının `net`'i bugünkü formülle kalır (enerji yok).

## 2. R3 — Teklif yaşam döngüsü (RS `DeliveryCore.kt:200-309, 449-473, 709-724, 853-895`)

### 2.1 Gözlem girişi (`observe`, `:258-271`)

1. Çekirdek çalışmıyorsa (vardiya yok) hiçbir şey.
2. **Geç kare:** kare anı platformun son anlamlı (UNKNOWN olmayan) karesinden eskiyse yalnız `backfillLate` (2.8), başka hiçbir şey (OCR eşzamansız; eski kare kabul edilmiş teklifi diriltmez).
3. UNKNOWN değilse "son anlamlı an" = kare anı.
4. Sırayla: `react` (2.2–2.7 ve Bölüm 3), `spinner` (3.7), `payment` (3.7), sonuçların hatırlanması (2.9), `penalty` (2.10).

### 2.2 Teklif karesi (OFFER, `:299-309`)

- Platformun bekleme sayacı silinir. Bekleyen teklif **aynı kart** (`same`) ise `enrich` (ve Bolt "Optional" görüldüyse `optional`).
- Farklıysa: eski teklif ret dokunuşu almışsa `rejected` / `courier_declined`, almamışsa `offer_unconfirmed` / `replaced_without_idle`; yeni `OfferState` (`optional` = Bolt "Optional" satırı; ücret varsa `feeAt` = an) ve olay `offer`.

### 2.3 Ret dokunuşları ve "Decline offer?" (`click`, `:709-724`; `react` `:283`)

- Ret etiketi (`CourierParser.isDecline`) bekleyen teklifte: ilk dokunuş → `declineClickAt`. İkinci dokunuş, ilkinden sonra görülen onay sayfasından (`declineDialog` karesi) en çok `DECLINE_DIALOG_MS` = 30 sn sonra ise teklif hemen `rejected` / `courier_declined_confirmed` (Wolt: teklifte "Decline" onay sayfasını açar, orada "Decline" onaylar).
- `return to offer details` ya da kabul etiketi → `declineClickAt` ve onay anı silinir (vazgeçti).
- Her ret etiketi dokunuşu platformun "son ret dokunuşu" anını yazar (afiş için).
- Onay sayfası karesi aşamayı UNKNOWN yapar (`CAPTURE.md` 2.5); teklif bekler.

### 2.4 Ret afişi "N deliveries declined" (`declineBanner`, `:457-465`)

- Her afişte son değer ve an saklanır (`BANNER_RESET_MS` = 5 dk). Teklif karesinin üstündeki afiş karar vermez.
- Önceki afiş (5 dk içinde) yoksa: son 5 dk'da ret dokunuşu **varsa** bekleyen teklif `rejected` / `courier_declined_banner`. Önceki afiş varsa: sayı **arttıysa** aynı. Yeni sipariş açılınca afiş sayacı silinir.

### 2.5 Teklifin bekleme ekranına dönmesi (`offerLeft`, `:449-456`; çağrı `:411-412`)

- Kararlı bekleme: tam (`complete`) IDLE/OFFLINE karesi, platformun ilk bekleme karesinden ≥ 250 ms sonra (iki kararlı okuma; anlık kapanma/yükleme ret sayılmasın). O anda bekleyen teklif kalkar:
  - ret dokunuşu var → `rejected` / `courier_declined`;
  - platformun belgeli penceresi varsa (Wolt ve Bolt 60 000 ms — **doğrulanmamış üst sınır**) ve ilk bekleme karesi ≥ `firstAt` + pencere − `EXPIRY_MARGIN_MS` (2 sn) → `offer_unconfirmed` / `expired_inferred` (zaman aşımı, ret değil);
  - değilse → `rejected` / `offer_to_idle_map`.
- Başka platformun bekleme ekranı bu platformun teklifine dokunmaz (kuyruklar platform başına).

### 2.6 Bayatlama (`expireStale`, `:466-473`)

- IDLE/OFFLINE/UNKNOWN karelerinde, teklifin kullanılmadığı görev karelerinde ve kapanmış kart karelerinde: kartın son karesinden bu yana ≥ `OFFER_STALE_MS` = 3 dk (platformun açık siparişi varken `OFFER_STALE_ON_TASK_MS` = 20 dk: görevdeyken gösterilen teklif sonra kabul edilebilir) → teklif kalkar: ret dokunuşu varsa `rejected` / `courier_declined`, yoksa `offer_unconfirmed` / `offer_not_seen`.

### 2.7 Kabul (`react`, `:310-338`)

- PICKUP/DROPOFF karesi + bekleyen teklif + ret dokunuşu yok + (açık sipariş yok ∨ kare açık siparişe ait değil, `belongsTo`) → teklif bu karede kabul edildi: yeni sipariş, `accepted` / `active_screen`. Teklif yoksa (ya da kullanılmadıysa) ve kare yeni sipariş açıyorsa: `accepted` / `active_screen_offer_missed` (`missedOffer = true`). Ayrıntı: Bölüm 3.2.
- **D25:** teklif kartı "Cash order" dediyse sipariş açılırken `paymentMethod = cash`, `paymentSource = wolt_offer_card`, `paymentAt`; `changeNeeded` varsa `cashChangeAzn` ve `cashNoChange` (= 0,0 mı); olay `payment_seen` / `cash_offer_card`.

### 2.8 Geç kare doldurma (K37, `backfillLate`, `:272-281`)

- Yalnız OFFER karesi. Bekleyen teklif aynı kart ve kare `firstAt` − `LATE_OFFER_FRAME_MS` (2 sn) ya da sonrasıysa `backfill`. Değilse platformun açık siparişinin teklifi: kare, teklifin ilk karesinden en çok 2 sn önce ile kabul anı arasındaysa (ikisi dahil) ve aynı kartsa → `backfill`. Teklif asla dirilmez.

### 2.9 Sonuçlar ve ret penceresi (SPEC 2.1.0 §17.2; `:192-225, 878-887`)

- `rejected`, `offer_unconfirmed`, `accepted` olayları teklifin `optional` bayrağıyla hatırlanır; en yeni sonuçtan 2 × pencereden eski olanlar atılır (vardiya durup başlasa da kalır; platform sayaçları gibi). Yeniden başlatmada teklif kayıtlarından geri yüklenir (`restoreOutcome`, tekrar eklenmez).
- `recentDeclines(pkg, at)`: son `DECLINE_WINDOW_MS` = **60 dk** içindeki sonuçlardan `countsAsDecline` = ¬optional ∧ (`rejected` ∨ `offer_unconfirmed` ∧ neden `expired_inferred`/`offer_not_seen`); `explicitDecline` = ¬optional ∧ `rejected` ∧ neden `courier_declined`/`courier_declined_confirmed`/`courier_declined_banner`. Kabul, değiştirilme, vardiya sonu ve Bolt "Optional" asla sayılmaz.

### 2.10 Ret cezası (K18, `penalty`, `:226-234, 888-892`)

- Tam OFFLINE karesinde ceza metni (`norm`'lanmış satır + sonraki satırda `(?:set offline|offline until).{0,120}declin`) varsa ve bu çevrimdışı dönemde henüz bildirilmediyse: olay `penalty_offline`, neden `declines_in_window=<countsAsDecline>`; o anki pencere saklanır (`penaltyCounts`). OFFLINE ya da UNKNOWN olmayan bir kare dönemi bitirir. "Declining too many can mean being set offline" uyarısı ceza değildir.

### 2.11 Bolt "Optional" (`:893-895`)

- Bolt paketinde tam satır `^optional( order| delivery)?$` → teklif isteğe bağlı: reddi ya da zaman aşımı cezaya sayılmaz (SPEC §9.2; etiket telefonda henüz görülmedi).

### 2.12 Vardiya sonu (`stop`, `:201-209`)

- Çekirdek durur; sayaçlar silinir. Bekleyen her teklif `offer_unconfirmed` / `manual_shift_stop`; her açık sipariş END_UNCONFIRMED, `end_unconfirmed` / `manual_shift_stop`. Sonuçlar hatırlanır (2.9). `start` bekleme, ret, afiş, anlamlı an ve ceza sayaçlarını sıfırlar.

### 2.13 WEEM

- `DECLINE_WINDOW_MS` (60 dk) ve `offerWindowMs` (Wolt, Bolt 60 000 ms; başka paket null) `logic/orders` `OfferWindows` sabiti olur; Rawstats bunları `EconomicsV2.PRIORS`'tan (`wolt_K_support.window_min`, `documented_window_ms`) okuyordu. `:economics` (A4.1) testine "`OfferWindows` = `PRIORS`" denetimi eklenir; öncül değişirse iki yer birlikte değişir.
