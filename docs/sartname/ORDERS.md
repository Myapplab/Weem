# ORDERS — Aşama 3 şartnamesi (`:orders`: R3 yaşam döngüsü, R5–R13, R15, R19)

Durum: A3.1, 17 Ekim 2026 (Claude Opus · max, tek ajan; S14) — bölüm 0–16 yazıldı. Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04`; satır numaraları bu commit'te açılıp doğrulandı. `bulgular/R_orders.md`'nin yerini alır.

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

## 3. R5 — Sipariş durum makinesi (`DeliveryCore.react` ve yardımcıları, RS `DeliveryCore.kt:282-448, 474-898`)

### 3.1 Kare başında (`react`, `:282-298`)

1. "Decline offer?" karesi → onay anı (2.3). Ret afişi varsa → 2.4.
2. OFFER, PICKUP ya da DROPOFF karesi platformun "bekleme haritası başlangıcı"nı siler (M22).
3. `current` = platformun **en son açılan** açık siparişi (`active`).
4. **Kapanmış kart (K34):** DROPOFF karesi `closedOrderOfCard` (3.5) ile doğrulanmamış teslim olarak kapanmış bir siparişin kartıysa: bekleme sayacı silinir, bayatlama (2.6), `lateCard` (3.5); dön.
5. "Teslim edildi" yazan kare ve açık sipariş yoksa: dön.
6. **Tekrar koruması:** açık sipariş ve bekleyen teklif yokken gelen PICKUP/DROPOFF karesi, platformun ≤ 30 sn önce biten siparişine aitse (sipariş numaraları varsa hepsi o siparişin müşterilerinde; numara yoksa müşteri adresi o siparişin bir müşterisinin adresiyle aynı) yeni sipariş açmaz; dön.

### 3.2 Görev karesi: açılış ve sahiplik (PICKUP/DROPOFF, `:310-341`)

- Bekleme sayacı silinir. Teklif kullanılır mı: bekleyen teklif var ∧ ret dokunuşu yok ∧ (açık sipariş yok ∨ kare açık siparişe **ait değil**). Ait olma (`belongsTo`): karede 4+ karakterli sipariş numarası ve siparişte müşteri varsa numaralardan biri onun; yoksa karede restoran ve siparişte alış varsa restoran adı (`norm`) eşit; yoksa karede müşteri adı ve siparişte müşteri varsa ad eşit; hiçbiri yoksa **ait** (açık siparişin karesi, üstte gösterilen yeni teklifin kabulü değil; kurye onu reddetmiş olabilir). Kullanılmıyorsa bayatlama (2.6).
- Sipariş: teklif kullanılmıyorsa ve kare DROPOFF ise `ownerOf` — kart, açık siparişin değil de platformun **başka bir açık siparişinin** müşterisini adlandırıyorsa o sipariş (K34; aksi hâlde en yeni sipariş müşteri aşamasına itilirdi); diğer durumda `current`. Adlandırma (`names`): 4+ karakterli numara varsa onunla; yoksa ad (`NameMatch.sameName`, OCR varyantları); ikisi de yoksa kimseyi adlandırmaz.
- **D3(b) yeni sipariş:** PICKUP karesi, teklif yok, sipariş müşteri aşamasında, kaydırıcısı geçilmiş (`pastSlider`: kaydırıcısı görülen müşteri = şimdiki müşteri) ve kare **başka bir restoranı** adlandırıyor (`otherRestaurant`: karedeki restoran siparişin hiçbir restoranına/teklif restoranına `OfferMatch.similar` değil ve karedeki numaralar onun değil) → yeni sipariş açılacak (önceki, kaçırılan teklifin siparişi sayılır).
- **Açılış** (teklif kullanıldı ya da sipariş yok): kare "esaslı" değilse (restoran, 4+ numara, müşteri adı/adresi, teklifteki müşteri adresi, teslim kontrolü ya da alış kodu yok) sipariş açılmaz (kullanılan teklif geri konur); dön. Afiş sayacı silinir; `TripRecord` (`missedOffer` = teklif yok); `accepted` (2.7) ve D25.
- Her görev karesinde: `latest` = kare; Wolt kısa numaraları eklenir; `expectedOrders` = max(önceki, `quantity` ?: 1, 4+ numara sayısı).

### 3.3 Restoran aşaması (PICKUP, `:342-362`)

- Son alış yoksa ya da (alınmış ∧ karede farklı bir restoran adı) → yeni `PickupRecord` (restoran/adres kareden, yoksa tekliften), faz **TO_RESTAURANT**; ilk alışsa hazır sayacı tekliften başlar (`Readiness.fromOffer`: teklifin "Pickup N min" son okunuşu (`readyInAt`, yoksa `lastSeenAt`) + N dk).
- Karedeki restoran ve adres alışa yazılır; `nextAtRestaurant` (3.6, D3(b)).
- `readyIn` → `readyAt` (yalnız ilk) ve `readyHintAt` = kare + N dk (Bolt'un "Arrive in N min"i kuryenin varış hedefidir, hazır süresi değil: ayrıştırıcı onu `pickupClock`'a koyar). `ready` → `readyObservedAt` (ilk); `preparing` → `preparingAt` (ilk); not, kod, ürünler.
- `arrived` ve alışta varış yoksa → `arrivedAt`, `arrivalSource = platform_status`, faz **WAITING**, olay `restaurant_arrived`.
- Karedeki 4+ numaralar müşteri kaydı olarak açılır (`customers.getOrPut`).

### 3.4 Müşteri aşaması (DROPOFF, `:363-406`)

1. Son alış alınmamışsa `pickedAt` = kare, olay `picked_up`. Faz TO_RESTAURANT/WAITING ise **TO_CUSTOMER**.
2. Müşteri kimliği: ilk 4+ numara; yoksa ad varsa aynı siparişte aynı adlı, teslim edilmemiş müşteri (`CustomerMatch.find`: farklı rakamlı adres ayırır) ya da `name:<norm(ad)>`; yoksa şimdiki müşteri; yoksa `customer-1`. Tek müşteri `customer-1` yer tutucusuyken gerçek kimlik gelirse kayıt bilgisiyle birlikte yeniden adlandırılır.
3. **Önceki müşteriye geçiş:** şimdiki müşteri başkaysa ve teslim edilmemişse — son teslim dokunuşu < 30 sn önceyse `next_customer_after_confirm`, kaydırıcısı görülen müşteri oysa `next_customer_after_delivery_slider` → önceki müşterinin `deliveredAt` = kare, teslim niyeti silinir, olay `customer_delivered` (kaydırma tıklama olayı üretmez; kaydırıcı + sonraki müşterinin kartı teslimin kanıtıdır).
4. Müşteri değiştiyse faz TO_CUSTOMER; şimdiki müşteri = kimlik; ayrıntılar birleşir (`merge`), `detailsAt`; `deadlineClock` = kare saati; varsa `deadlineAt` = `DeliveryDeadline.merge(eski, kare + N dk)` (2 dk'dan küçük fark eskiyi bırakır).
5. Teslim kontrolü görüldüyse `deliverSeenFor/At` = bu müşteri/kare; görülmediyse ve kaydırıcı başka müşterinin değilse silinir.
6. Wolt'ta K33 göstergesi → `handoverHint` (`spinner`); değilse Wolt'ta nakit kutusu işaretli (M16) → `handoverHint` (`cash_checked`).
7. `arrived` ve müşteride varış yoksa → `arrivedAt`, faz **AT_CUSTOMER**.
8. `sameCard` (3.6, R1); `delivered` → `completeCustomerOrTrip(platform_completed)` (3.7); sipariş müşteri aşamasındaysa ve `mayBeClosedCard` değilse → `oneAtCustomer` (3.5, K34).

### 3.5 Bir anda tek müşteri (K34) ve kapanmış kartlar (`:497-573`)

- **`oneAtCustomer(trip, at)`:** her **başka** açık sipariş müşteri aşamasındaysa (son görev karesi bu kareden yeni değilse ve aynı "N dropoffs to" kartında değilse) → END_UNCONFIRMED, teslim niyeti silinir, `deliveredInferredAt = at`, `deliveredInferredBy = trip.id`, neden: kaydırıcısı şimdiki müşterisinde görülmüşse `next_order_at_customer_after_slider` (gösterge de varsa `..._after_slider_spinner`), değilse `next_order_at_customer`; olay `delivered_inferred`. **Asla `delivered` değil; `completedAt` ve müşteri `deliveredAt` yazılmaz.** Kural kullanıcınındır (8 Ekim 2026: kurye bir anda tek sipariş taşır; iki platform dahil).
- **`closedOrderOfCard(o, current)`** (DROPOFF karesi, kapanmış siparişin kartı mı):
  - Kare platformun doğrulanmamış teslim olarak kapanmış bir siparişinin (en sonuncusunun) müşterisini adlandırıyor ve (açık sipariş yok ∨ açık siparişin müşterisini adlandırmıyor ∧ açık sipariş kapanıştan önce ya da aynı anda kabul edilmiş) → o sipariş. (Kapanıştan sonra kabul edilen sipariş kartı sahiplenir: Wolt kartları yalnız ad taşır, sonraki müşteri aynı adı taşıyabilir.)
  - Adlandırmıyorsa: açık sipariş varsa → yok. Yoksa platformun son görev ekranı kapanmış bir siparişin kartıysa (`closedOrderShown`), karenin anı o kartın son görülüşünden ya da kapanıştan (hangisi sonraysa) en çok `SLIDER_TO_WAITING_MS` (30 dk) sonra ise, karede o siparişte olmayan bir 4+ numara yoksa ve kare platformun başka bir siparişini adlandırmıyorsa → o sipariş; ancak ret dokunuşu almamış bir teklif bekliyorsa yalnız kare o siparişe aitse (`belongsTo`) (kare teklifin kabulü olabilir).
- **`lateCard(u, o)`:** kapanıştan sonra platformda yeni sipariş kabul edildiyse hiçbir şey. Değilse kartın geç görülüşü yazılır (bellek; 200'ü aşınca var olmayan siparişler atılır); karedeki müşteri (numara, ad ya da şimdiki); teslim kontrolü → kaydırıcı o müşteride; değilse ve başka müşterideyse silinir; "teslim edildi" → `finishInferred(platform_completed)`.
- **`mayBeClosedCard(t)`:** alışı görülmemiş (kart görülerek açılmış) sipariş, aynı platformun başka bir doğrulanmamış teslim siparişinin kartı hâlâ ekranda olabilirken (kabulü, o kartın son görülüşü/kapanışından ≤ 30 dk sonra; kapatan o değilse) açıldıysa → öteki siparişleri kapatmaz (OCR'ın yanlış okuduğu ad/numara olabilir).
- **`finishInferred(u, at, neden)`:** kaydırıcı ve teslim niyeti silinir, `finish` (3.7): sipariş `delivered` olur; `deliveredInferredAt/By/Reason` geçmiş olarak kalır (elle düzeltme sonrası gibi).

### 3.6 Wolt'a özgü kapanışlar (`:574-634`)

- **`sameCard` (R1, 16 Ekim):** Wolt kartında ≥ 2 kısa numara ("Orders #072, #387") varsa, kısa numaralarından biri bu kartta olan her başka açık Wolt siparişi bu kartın siparişidir: karşılıklı `sameDropoff`; alınmamış alışı `pickedAt` = kare (`picked_up` / `same_dropoff_card`); aynı müşteri kaydı (ayrıntılar birleşir); faz TO_CUSTOMER; şimdiki müşteri; `arrived` → AT_CUSTOMER. Bunlar K34 ile birbirini kapatmaz; biri teslim edilince hepsi (`companionsDone`). Kısa numaralar müşteri anahtarı değildir (K10).
- **`nextAtRestaurant` (D3(b), saha 11–12 Ekim):** Wolt bir anda tek görev gösterir. Bir Wolt siparişinin **restoran ekranı**, başka bir açık Wolt siparişinin müşteri aşamasında ve (kaydırıcısı geçilmiş ∨ teslim işareti var, `hinted`) iken gelirse (o siparişin son karesi bu kareden yeni değilse) → o sipariş `delivered_inferred`, neden `next_order_at_restaurant_after_slider` (gösterge: `..._after_slider_spinner`) ya da `next_order_at_restaurant_after_<işaret>` (`spinner`/`cash_checked`), `deliveredInferredBy` = bu sipariş. (Sahada 35 kaydırıcının yalnız ~%26'sını kararlı bekleme haritası izledi.) Başka platformun restoran ekranı hiçbir şey kanıtlamaz.
- **Bekleme haritası (M22, Wolt):** 3.8.

### 3.7 Teslim yardımcıları (`:784-818`)

- **`finish(trip, at, neden)`:** zaten bitmişse hiçbir şey; şimdiki müşterinin `deliveredAt`'ı yoksa = at; `completedAt` = at; faz **FINISHED**; olay `delivered`.
- **`completeCustomerOrTrip(trip, at, neden)`:** teslim niyeti silinir; `companionsDone`; tek sipariş ve ≤ 1 müşteri → `finish`. Değilse şimdiki müşteri `deliveredAt` (yoksa) + `customer_delivered`; teslim edilmiş müşteri sayısı ≥ max(beklenen sipariş, müşteri sayısı) → `finish` (paket teslimat her müşteri onaylanana kadar açık; tekrarlanan "tamamlandı" başka müşteriyi bitirmez).
- **`completeOnWaiting(trip, at)`** (neden `delivery_slider_then_waiting`): kaydırıcı silinir; `companionsDone`; tek sipariş ∧ ≤ 1 müşteri → `finish`; değilse şimdiki müşteri teslim + `customer_delivered`; hepsi teslim ∧ müşteri sayısı ≥ beklenen → `finish`.
- **`companionsDone`:** `sameDropoff`'taki müşteri aşamasındaki siparişler aynı nedenle `finish`.
- **`inferDelivered(u, at, by, neden)`:** END_UNCONFIRMED, niyet silinir, `deliveredInferredAt/By/Reason`, `delivered_inferred`.
- **`confirmDelivered(id, at)`:** kuryenin elle düzeltmesi → `finish(courier_manual_correction)`.

### 3.8 Bekleme ve çevrimdışı kareleri (IDLE/OFFLINE, `:407-448`)

Yalnız **tam** kare. Platformun ilk bekleme karesi (`since`) saklanır; IDLE ise ayrıca "bekleme haritası başlangıcı" (M22: UNKNOWN kareler bunu silmez).

- Kare `since`'ten ≥ 250 ms sonraysa (kararlı):
  1. Bekleyen teklif → `offerLeft` (2.5).
  2. `closed` = platformun son görev ekranı kapanmış bir siparişin kartıysa o sipariş: IDLE ∧ onun teslim niyeti < 30 sn önce → `finishInferred(confirmed_delivery_to_idle)`; değilse IDLE ∧ kaydırıcısı şimdiki müşterisinde ∧ kaydırıcıdan bu yana ≤ 30 dk ∧ kararlılık ≥ 1 000 ms → `finishInferred(delivery_slider_then_waiting)`.
  3. `shown` = platformun **son görev ekranı görülen** açık siparişi (eşitlikte en yeni; tıklama ve bekleme haritası ona aittir): `closed` yok ∧ IDLE ∧ teslim niyeti < 30 sn → `completeCustomerOrTrip(confirmed_delivery_to_idle)`; değilse `closed` yok ∧ IDLE ∧ son görev karesi DROPOFF ∧ kaydırıcı şimdiki müşteride ∧ ≤ 30 dk ∧ ≥ 1 000 ms → `completeOnWaiting`; değilse OFFLINE → END_UNCONFIRMED, `end_unconfirmed` / `offline_without_completion`.
- **Wolt bekleme haritası kuralı** (kuryenin kuralı: Wolt haritası görünürken Wolt görevi kalmamıştır): IDLE ∧ Wolt ∧ kare − (bekleme haritası başlangıcı ?: `since`) ≥ `WAITING_MAP_CLOSES_MS` = 3 000 ms → platformun her açık siparişi: kaydırıcısı geçilmiş ∧ ≤ 30 dk → `completeOnWaiting`; hâlâ açıksa: bir müşterisi teslim edilmiş → `finish(waiting_map_after_delivery)`; müşteri aşamasında → `inferDelivered(waiting_map_at_customer)`; değilse END_UNCONFIRMED, `end_unconfirmed` / `waiting_map_before_pickup` (hiç alış alınmamış) ya da `waiting_map_without_delivery`.
- Tam olmayan IDLE/OFFLINE ve UNKNOWN karesi bekleme sayacını siler (bekleme haritası başlangıcını değil).
- IDLE, OFFLINE ve UNKNOWN karelerinde bayatlama (2.6).

### 3.9 Dokunuşlar (`click`, `:709-744`)

- Ret/kabul etiketleri: 2.3. Genel onay etiketleri: `confirm, complete, finish, done, ✓, ✔, teslim et, tehvil ver`.
- Platformun son görev ekranı kapanmış bir siparişin kartıysa: teslim ya da genel onay → o siparişin `finishIntentAt`; başka bir şey yapılmaz.
- Değilse dokunuş `shown` siparişine aittir (ör. eski siparişin müşteri kartındaki "Arrived", K34): alış etiketi → `pickupIntentAt`; arama düğmesi (sipariş müşteri aşamasında ve şimdiki müşteri varsa) → `callFor/callAt` (telefon numarası penceresi, 4.8); son görev karesi DROPOFF ∧ (teslim ∨ genel onay) → `finishIntentAt`; "vardım": TO_RESTAURANT → son alış `arrivedAt` + `arrivalSource = user_arrived_action`, faz WAITING; TO_CUSTOMER → müşteri `arrivedAt`, faz AT_CUSTOMER. **Dokunuş olay üretmez** (kayıt satırı yalnız `events.jsonl` `click`, `CAPTURE.md` 6.3).

### 3.10 Gösterge ve ödeme kanıtı (`spinner` `:771-783`, `payment` `:745-770`)

- **K33 göstergesi:** Wolt karesinde gösterge var; platformun son gösterilen siparişi (açık ya da doğrulanmamış teslim) için kaydırıcısı şimdiki müşterisinde görülmüş; kaydırıcıdan bu yana 0–`SPINNER_AFTER_SLIDER_MS` (10 sn); bu müşteri için ilk kez → `deliverSpinnerAt/For`, olay `delivery_spinner_seen` / `after_delivery_slider`. Hiçbir şeyi bitirmez.
- **K31 ödeme** (Wolt teslim adımları ekranı; `cashOrder`, ya da tam karede adımlar başlığı): OFFER karesinde asla (teklif kartının "Cash order"ı teklifindir, D25). Sipariş: son gösterilen açık Wolt siparişi, yoksa tek açık Wolt siparişi. Nakit adımı: para üstü varsa `cashChangeAzn`/`cashNoChange`; kutu işaretli ve ilk kez → `cashCheckedAt` + `cash_checked_seen`; tutar = kareden ?: kayıtlı; zaten nakit ve tutar aynıysa dur; değilse `paymentMethod = cash`, kaynak `wolt_cash_screen` (tutar yok ve önceki kaynak `wolt_offer_card` ise o kalır), `payment_seen` / `cash` ya da `cash_amount_unread`. Nakit adımı yok: ödeme hiç bilinmiyorsa `card_inferred` / `wolt_delivery_steps_no_cash`, `payment_seen` / `card_inferred` (gözlenmiş nakdi asla ezmez).

### 3.11 Dışarıdan gelen kararlar (`:635-708, 819-846`)

- **`cashCollected(id, at, tutar, now)`** (D3(d), Wolt Finances "Cash collection" satırı; sipariş başına bir kez): `cashCollectedAt/Azn`; `verifiedBy = wolt_cash_collection`, `verifiedAt = now`; nakit değilse nakit yapılır (`paymentSource = wolt_cash_collection`) ve/veya bilinmeyen tutar doldurulur → `payment_seen` / `cash_collection`. Teslim anı o dakikaya (`at` … `at + 59 999`) çekilir: teslim edilmişse saat dakikanın dışındaysa düzeltilir (müşteri teslimleri dahil) → `delivery_time_corrected` / `wolt_cash_collection`; doğrulanmamış teslim → `finishInferred` (dakikaya çekilmiş kapanış anıyla); açık → `finish(wolt_cash_collection)` (son görev karesi anıyla).
- **`listVerified(id, by, now)`** (D3(c), platformun kazanç listesi eşleşmesi): `verifiedBy/At` (yalnız ilk).
- **`venueArrived(id, index, at, kaynak, now, replace)`** (D9, 4.2): çekirdek çalışıyorsa; varış alıştan sonraysa hiçbir şey; platformun ya da kuryenin varışı asla değişmez (yalnız `replace` ile ve yalnız GPS kaynaklı varış düzeltilir); aynı anı yeniden yazmaz. `arrivedAt = at` (null = varış geri alındı), `arrivalSource`; alınmamış son alışta TO_RESTAURANT → WAITING. Olay (`now` anında) `restaurant_arrived` ya da `restaurant_arrival_corrected`, neden = kaynak ya da `gps_venue_mismatch` (at null).
- **`customerArrived(id, at, kaynak, now)`** (M19): yalnız TO_CUSTOMER'da ve müşteri varmamış/teslim edilmemişse → `arrivedAt`, `arrivalSource`, AT_CUSTOMER, `customer_arrived` / kaynak.
- **`readinessNotice(pkg, metin, at)`**: platformun açık siparişi restoran aşamasında, alınmamış alış: `ready in\s*(\d+)\s*min` → `readyHintAt` = at + N dk; değilse `ready for pickup|order is ready|is ready|hazirdir` → `readyObservedAt` (ilk).
- **`addDistance(m)`** (K9): yol, son görev ekranı görülen **tek** açık siparişe (bölünmez, çoğaltılmaz).
- **`addEnergy(used, recovered, ms, complete)` / `closeEnergy(trip)`** (R14, D10): enerji de yol gibi son görülen açık siparişe birikir; yalnız iki paketin gücü (tam adım) Wh'e girer, iç paket tek başına ölçüm boşluğu süresine (`energyPartialMs`); aynı anda başka açık sipariş varsa `energyShared`; sipariş bitince (teslim, çıkarım ya da doğrulanamayan) bir kez kayda geçer. Sahibi `:scooter`/`:economics` (A5.3); `:orders` biriktirme yerini korur.

### 3.12 Sabitler (`:847-866`) — WEEM: aynen `logic/orders`

| Ad | Değer |
|---|---:|
| `SLIDER_TO_WAITING_MS` | 30 dk |
| `SPINNER_AFTER_SLIDER_MS` | 10 sn |
| `WAITING_MAP_CLOSES_MS` | 3 sn |
| `OFFER_STALE_MS` / `OFFER_STALE_ON_TASK_MS` | 3 dk / 20 dk |
| `LATE_OFFER_FRAME_MS` | 2 sn |
| `DECLINE_DIALOG_MS` | 30 sn |
| `CALL_TO_DIALER_MS` | 60 sn |
| `BANNER_RESET_MS` | 5 dk |
| `EXPIRY_MARGIN_MS` | 2 sn |
| `DECLINE_WINDOW_MS` | 60 dk (`OfferWindows`, 2.13) |
| kararlı bekleme / kaydırıcıdan sonra kararlılık / niyet penceresi / bitiş sonrası tekrar | 250 ms / 1 000 ms / 30 sn / 30 sn (kodda düz sayı; WEEM'de adlı sabit) |

### 3.13 Olaylar ve nedenler (tam liste)

| Olay | Nedenler |
|---|---|
| `offer` | — |
| `accepted` | `active_screen`, `active_screen_offer_missed` |
| `rejected` | `courier_declined`, `courier_declined_confirmed`, `courier_declined_banner`, `offer_to_idle_map` |
| `offer_unconfirmed` | `replaced_without_idle`, `expired_inferred`, `offer_not_seen`, `manual_shift_stop` |
| `payment_seen` | `cash_offer_card`, `cash`, `cash_amount_unread`, `card_inferred`, `cash_collection` |
| `cash_checked_seen` | — |
| `restaurant_arrived` / `restaurant_arrival_corrected` | — (platform), VenueArrival kaynağı (4.2), `gps_venue_mismatch` |
| `picked_up` | —, `same_dropoff_card` |
| `customer_arrived` | `gps_near`, `nav_yolgo`, `nav_google_maps` |
| `customer_delivered` | `next_customer_after_confirm`, `next_customer_after_delivery_slider`, `platform_completed`, `confirmed_delivery_to_idle`, `delivery_slider_then_waiting` |
| `delivered` | `platform_completed`, `confirmed_delivery_to_idle`, `delivery_slider_then_waiting`, `waiting_map_after_delivery`, `wolt_cash_collection`, `courier_manual_correction` |
| `delivered_inferred` | `next_order_at_customer`, `next_order_at_customer_after_slider`, `next_order_at_customer_after_slider_spinner`, `next_order_at_restaurant_after_slider`, `next_order_at_restaurant_after_slider_spinner`, `next_order_at_restaurant_after_spinner`, `next_order_at_restaurant_after_cash_checked`, `waiting_map_at_customer` |
| `end_unconfirmed` | `offline_without_completion`, `waiting_map_before_pickup`, `waiting_map_without_delivery`, `manual_shift_stop` |
| `delivery_spinner_seen` | `after_delivery_slider` |
| `delivery_time_corrected` | `wolt_cash_collection` |
| `penalty_offline` | `declines_in_window=<n>` |

`ORDER_ENDS` = `delivered`, `delivered_inferred`, `end_unconfirmed` (siparişin sonu: enerji kapanır, scooter köprüsüne bildirilir, 4.4).

## 4. Sipariş akışı (`OrderTracker`, RS `OrderTracker.kt:1-927`) — WEEM: saf `OrderFlow` + Android katmanı

`OrderTracker` tek nesnedir; her girişi `@Synchronized` (tek kilit) ve ana iş parçacığındadır. İçinde: `DeliveryCore`, `OnlineSessions` (R11), `BoostLog` (R10), `VenueArrival` (D9), `ScreenTrail` (E8), hatırlatıcılar (R6), ekonomi kaydedicileri (`EconomicsRecorder`, `WaitObservation`, `ShadowMode`, `OfferScore`, `EnergySegments`: `:economics`), YolGo akışları (`OfferFeed`, `FeedChanges`, `YolGoNavReader`: WEEM'de yok).

### 4.1 Açılış ve geri yükleme (`init`, `:237-274`)

1. (Rawstats: `ScooterLink.install`; WEEM'de `:scooter` kendi `ScooterBridge`'ini kaydeder.) `ensureManualMode`; `courier.db` açılır.
2. Bugünün **ve dünün** `trips` satırlarından `RecordJson.restorable` olanlar belleğe (gece yarısından önce kabul edilen sipariş yeniden başlatmadan sonra da bitebilsin ve vardiya sonu onu kapatabilsin).
3. `session` satırındaki bekleyen teklifler: ilk görülüşü bugün ve vardiya açık ise geri gelir, değilse "düşen" listesine; düşen varsa `session` yeniden yazılır. (Ekonomi: `econ.restart(now, bekleyen, düşen)` satırları — süreç ölümünde bekleyen teklif K21 gereği yeniden görülene kadar dışa verilmez, sonucu "gözlenmedi" (SPEC 17.2 `recording_stopped`).)
4. Vardiya açıksa çekirdek başlar.
5. (Ekonomi: açık boşta dönemi `economicsIdle.v1` ve açık beklemeler `openWaits.v1` tercihlerden geri yüklenip "sansürlü" satır olarak yazılır, tercih silinir.)
6. Açık çevrimiçi oturumlar `onlineSessions.v1`'den; vardiya kapalıysa hepsi `restart_after_shift` ile kapatılır (son çevrimiçi ekranda, Bölüm 9).
7. `structuredMigration07` false ise **kayıtların yeniden kurulması** (4.12) ve işaret true. Her adım ayrı korunur (`database`, `economics_idle`, `open_waits`, `online_sessions`).

### 4.2 Vardiya kancaları (RS `GpsService.kt:84-100, 208-250, 278-292`) → WEEM `ShiftListeners` (A2.5)

| `ShiftMachine.Hooks` | Rawstats çağrısı (sıra korunur) |
|---|---|
| `onNewShift(now, neden)` | `PocketCashPrompt.ask(now)` (R9, 4901) → `cashPendingShift(SHIFT_START, now, now)` (M17) |
| `onRunning()` (yapışkan yeniden başlatma dahil) | `start`: `init`; çekirdek başlar; hatırlatıcı, kapatılan balonlar, varış düzeltmeleri, ekran izi, YolGo bağı sıfırlanır; `persist(force)` |
| `onFix(fix, hız, now)` | `location(fix, hız, now)` (4.7) → `tick(now)` (4.6) |
| `onDistance(m)` | `distance(m)`: `core.addDistance(m)` (+ ekonomi `econ.distance`) |
| `busy()` | `hasOpenOrder()` = aktif sipariş var (F4.5b; A2.5'te `OrderEvents.busy`) |
| `onEnd(başlangıç, now, neden)` | `cashPendingShift(SHIFT_END, başlangıç, now)` → (`:core` `DailyPack.afterShift`, A4.3) → `stop` (4.3 sonu) |

- **Kayıt sırası (WEEM, `:app`):** `orders.cashPending` → `core.dailyPack` → `orders.tracker` → `scooter`. Böylece `onEnd`'de CASH PENDING sonu günlük paketten, günlük paket siparişlerin kapanmasından önce yazılır (Rawstats sırası). Bildirim **12**'nin vardiya sonunda iptali `:core` vardiya servisinde (A2.4, `SHIFT_END_CANCEL`).
- `stop`: oturumlar kapanır ve yazılır (zorla); (ekonomi: beklemeler, açık beklemeler, `econ.stop`, açık boşta dönemi); `core.stop` olayları `process`; hatırlatıcı yok; `persist(force)`.

### 4.3 Kare akışı (`onScreen`, `:300-381`) — sıra korunur

Girdi: `:capture`'ın her karesi (`CaptureSink.onScreen`). Kayıt izni yoksa hiçbir şey; `init`.

1. **F8.3 resmî toplamlar:** `PlatformTotals.woltDaily` + `boltDays` + Wolt nakit satırları (`cashTotals`) → değişen toplam `economics-platform-totals.jsonl`'e (Bölüm 11).
2. **K36 kazanç listesi:** kare Wolt Finances / Bolt All deliveries listesiyse yeni satırlar `economics-platform-history.jsonl`'e, D15 ek bekleme/bahşiş, D3(c) doğrulama (Bölüm 11) ve **kare burada biter** (liste ekranı asla teklif/sipariş ekranı değildir).
3. **D3(d):** Wolt "Cash collection" satırları → `core.cashCollected` (3.11).
4. **M17:** "CASH PENDING" ve "Cash offload" satırları (Bölüm 8).
5. Ayrıştırma: `:capture`'ın gözlemi (aynı paket, an, kaynak, `complete`), yoksa yeniden `parse`; erişilebilirlik göstergesi → `deliverSpinner = true`; `CashPayment.attach(gözlem, CashPayment.parse(pkg, metinler, checked))` (K31/M16, Bölüm 7).
6. **F8.1/F8.4:** `PlatformTotals.fromStats(gözlem)` → toplam yazımı.
7. **D21:** OFFER → `offerVisibleUntil` = now + 2 500 ms; tam ve UNKNOWN olmayan başka kare → 0 (rozet kaçışı, Bölüm 6).
8. **R11:** `sessions.observe(gözlem)` (kapanan oturum yazılacak).
9. `before` = bekleyen tekliflerin kopyası; `events = core.observe(gözlem)` + `pickupArrivals` (yeni `picked_up` olaylarının D9 varışı, `VenueArrival.collected`, 4.8).
10. **E8 ekran izi:** tam ve UNKNOWN olmayan kare → `trail.record(now, pkg, aşama, görev aşamasında shown siparişin id'si)`.
11. **D6:** bu kare teklifin (bekleyen ya da açık siparişinkinin) ücretini getirdiyse (`feeAt == now`) `feeOcrMs` = şimdi − kare anı.
12. Her olay: `linkSession` (teklif → o anki oturum id; kabul → teklifinkiyle ya da o anki), `sessions.event(olay, müşteri sayısı, çıkarım mı)`; oturumlar yazılır (olay varsa ya da açık oturum sayısı değiştiyse zorla).
13. (Ekonomi: `delivered` → bekleme nedeni `after_delivery_observed`; `rejected` → son 30 sn'de ret dokunuşu varsa `after_decline_observed`, değilse `after_offer_disappearance`; `waitCore.observe(gözlem, araç, bölge, Bakü saati)`; açık beklemeler; platform başına ≥ 5 sn'de bir ya da olay varken `economics-exposure.jsonl` satırı `{at, pkg, stage, complete, source, demand, vehicleMode, zone, bakuHour, dispatchAvailabilityVerified: false}`.)
14. (Rawstats: `OfferFeed.refreshGrant`; IDLE'da hava önbelleği tazelenir. WEEM: ikisi de yok — YolGo izni kalktı (S7), hava `WeatherPort`'tan anında okunur.)
15. **K27 boost:** `boosts.observe(gözlem, gün, konum)` satırları `economics-boosts.jsonl`'e (Bölüm 9) — teklif son boost'u almadan önce.
16. **Teklif anı (yalnız bu karede `offer` olayı varsa):** telefon pili; `notifiedAt` = platformun son bildirimi (teklifin ilk karesinden en çok 30 sn önce); scooter pili (`ScooterBridge.batteryPct()`, ≤ 15 sn taze; anı ve kaynak); hava (`WeatherPort.current()`, Bölüm 12); boost (`boosts.atOffer`: değer, birim, kaynak, görülme anı); son konum (enlem, boylam, doğruluk, yaş).
17. (Ekonomi: gölge karar `shadow.frame` ilk tam teklif karesinde ve ücret/km değişince; `econ.frame(gözlem, olaylar, before, …)` satırları; açık boşta dönemi.)
18. `process(olaylar)` (4.4).
19. **`platforms.jsonl`:** IDLE/OFFLINE ya da çarpanlı kare → `{pkg, stage, demand, bonus_pct, bonus_from, multipliers, offline_until}`; içerik özeti platform için değiştiyse `t` eklenip yazılır.
20. **Koordinat ve kart:** platformun `shown` siparişinin şimdiki müşterisi koordinatsız ve adresliyse çözülür (4.10); açık müşteri kartı bu müşteriyse ayrıntı, revizyon ve son tarih tazelenir.
21. `persist(olay varsa zorla)`; `tick(now)`.

### 4.4 Olayların işlenmesi (`process`, `:511-545`)

Her olay için sırayla:
- `accepted` (sipariş var): `commissionPct` = tercih `commission:<pkg>` (yoksa `commission`, Float, %); `priceIsNet = false`; `ScooterBridge.orderAccepted(tripId)`.
- `ORDER_ENDS` (`delivered`, `delivered_inferred`, `end_unconfirmed`): `core.closeEnergy(trip)`; `ScooterBridge.orderDone(tripId)`; sipariş artık aktif değilse bitiş anı (E8 `stages` için) saklanır.
- **`orders.jsonl` satırı:** `t` (olay anı), `type`, `pkg`, `offer_id`, `id` (sipariş), `reason`, `session_id` (sipariş ?: o teklif ?: platformun o anki oturumu); sipariş varsa `record` (= `RecordJson.trip`, telefonsuz, `latest` dahil), `gross`, `net`, `total_min`, `wait_min`, `km` (= `distanceM`/1000), `restaurant` (ilk alış); olay bekleyen teklifinse `offer` (= `RecordJson.offer`) ve teklif `courier.db`'ye; `offer` dışı her olayın teklifi `courier.db`'de bulunursa `result` = olay tipi; `loc`; `phone_battery_pct`. Null alan yazılmaz.
- `offer`: tanı sayaçları (son teklif anı, platformun teklif sayısı; `:capture` tanı kartı bunları gösterir).
- **K32:** `delivered`/`delivered_inferred` ∧ `CashTips.shouldAsk(trip)` ∧ siparişin günündeki bahşiş cevabı yok ∧ bu süreçte sorulmamış → `CashTipPrompt.ask` (Bölüm 7). (Bellek: yeniden başlatmada bir kez daha sorabilir.)

### 4.5 Kalıcılık (`persist`, `:547-573`)

- Zorla değilse en çok 1,5 sn'de bir. Tek `courier.db` işleminde yalnız **değişen** satırlar: her sipariş `trips` (= `RecordJson.trip(t, withPhone = true)` — **müşteri telefonu yalnız burada**; `allowBackup=false`, cihaz aktarımı yok), her bekleyen teklif `offers`, `session` = `{pending: [RecordJson.offer…]}`.
- `orders.jsonl` **`record_snapshot`** satırı `{t, type: record_snapshot, record}`: siparişin `latest`'siz içeriği değiştiyse ve (zorla ∨ `now % 10 000 < 2 000`) — en çok ~10 sn'de bir anlık görüntü; ham kare `screens.jsonl`'dedir.
- Hata: bellekteki "yazıldı" kayıtları silinir (sonraki seferde hepsi yeniden yazılır), `database` hatası.

### 4.6 Tik (`tick`, `:574-641`)

- Rawstats: önce YolGo akışları duyurulur (`publishFeeds`). WEEM: `OrderEvents` akışları her değişiklikte güncellenir (Bölüm 13); duyuru yok.
- Vardiya ya da çekirdek yoksa hatırlatıcı kalkar; dön.
- Rawstats: YolGo navigasyon okuyucusu tiki + hedef bağı. WEEM: `NavPort.route` değişince bağ (4.9).
- **E16 müşteri kartı açılışı** ve **E14 restoran balonu** kararları, açık hatırlatıcının güncellenmesi: Bölüm 5.
- Son: `persist` (zorlamasız).
- Tik iki yerden gelir: ekran okuma servisinin tiki (vardiyada 500 ms, `CAPTURE.md` 1.6) ve her GPS fix'i (4.2).

### 4.7 Konum (`location`, `:704-716`)

- Vardiya ve çekirdek yoksa hiçbir şey. Fix'in duvar saati anı = şimdi − (monotonik saatle fix yaşı) (ekran kareleriyle aynı saat). `VenueArrival.Fix(an, enlem, boylam, doğruluk, hız km/sa)` belleğe (hız: fix'in kendi hızı ya da öncekinden türetilen, A2.4 `GpsTrack`).
- Olaylar = `venues.live(...)` (Wolt restoran varışları, 4.8) + `venues.customers(...)` (müşteri varışları); varsa (ekonomi: `econ.events(idleUpdate = false)`), `process`, `persist(force)`.

### 4.8 Varış kuralları (`VenueArrival`, RS `VenueArrival.kt:1-166`; `ArrivalDistance.kt:1-38`)

- **Fix belleği:** sonlu koordinat ve artan an; `KEEP_MS` = 90 dk'dan eskisi atılır. Doğruluk > `MAX_ACCURACY_M` (60 m) olan fix "kullanılamaz".
- **Kalış (D9, `stayStart`):** [`from`, `to`] arasında, restorana ≤ `RADIUS_M` (60 m) ve hızı < `STOP_KMH` (3 km/sa) (hızsız fix duruyor sayılır) ilk fix kalışı başlatır; sonraki fix'ler 60 m içinde ve < `RIDE_KMH` (10 km/sa) kaldıkça sürer (kırmızı ışık kalış değil, yürüyerek girmek kalış); başlangıçtan `DWELL_MS` (20 sn) sonra hâlâ sürüyorsa varış = başlangıç. Alışta (`atPickup`) kalış son fix'ten alışa kadar sürmüş sayılabilir (içeride GPS kaybı): alış − başlangıç ≥ 20 sn.
- **Pencere:** alış `i`'nin penceresi siparişin kabulünden (ilk alış) ya da önceki alışın `pickedAt`'ından başlar.
- **`live` (her fix; yalnız Wolt siparişleri, alınmamış son alış):** varışı platformdan/kuryeden (kalış olmayan kaynakla) yazılmışsa atla. Önce **M18/M20 yakınlık:** taze (≤ `FRESH_MS` 30 sn, pencere içinde) navigasyon mesafesi ≤ `RESTAURANT_M` (5 m) ve son fix yavaş (< `SLOW_KMH` 10 km/sa) → o an, kaynak `nav_yolgo`/`nav_google_maps`; yoksa restoran koordinatına GPS yakınlığı (`nearAt`: ilk yavaş fix, mesafe − min(doğruluk, 20 m) ≤ 5 m; doğruluk > 30 m hiçbir şey söylemez) → kaynak `gps_near_yolgo_target`/`gps_near_geocode`. Yakınlık, önceki kalış varışının yerine geçer (`replace`). Yoksa kalış varışı yoksa `stayStart` → kaynak koordinatın kaynağı (`gps_dwell_yolgo_target`/`gps_dwell_geocode`).
- **`collected` (alış anında, `picked_up` olayıyla):** yalnız Wolt; varış platform/kurye kaynaklıysa hiçbir şey. Kuryenin alıştan önceki son kullanılabilir fix'i (≤ `PICKUP_FIX_MAX_AGE_MS` 5 dk). Restoran koordinatı bu fix'ten > `VENUE_MISMATCH_M` (150 m) ise yanlış sayılır (geocode edilmiş sokak) ve kullanılmaz. Koordinatlı varış zaten var ve koordinat doğruysa dokunulmaz. Değilse kalış koordinatta, yoksa kuryenin alış noktasında (`gps_dwell_pickup_point`) aranır; bulunamazsa varış silinir (`restaurant_arrival_corrected` / `gps_venue_mismatch`).
- **`customers` (her fix; her platform, TO_CUSTOMER):** bacak başlangıcı (son alış ya da önceki müşteri teslimi; yoksa kabul) sonrası; taze navigasyon mesafesi ≤ `CUSTOMER_M` (10 m) ∧ yavaş → o an ve kaynak; yoksa müşteri koordinatına GPS yakınlığı (≤ 10 m, aynı pay) → `gps_near` → `customerArrived` (3.11).
- **`ArrivalDistance.pick(yolgo, maps, gps, now)`:** taze (≤ 30 sn) navigasyon değerlerinin en yenisi, yoksa GPS. `near(kaynak, sınır)`: navigasyon değeri doğrudan; GPS'te doğruluk yoksa doğrudan, varsa yukarıdaki pay. `slow(hız)` = hız yok ∨ < 10 km/sa.
- Mesafe `VenueArrival.metres`: büyük çember, yarıçap 6 371 008,8 m.

### 4.9 Navigasyon bağı (E8; RS `YolGoNav.kt:1-108`, `OrderTracker.kt:27-56, 717-740`)

- **Rawstats:** YolGo'nun `nav/v1/current` yükü (`schemaVersion` 1, `source` `yolgo`, `generatedAtMs`, `updatedAtMs`, `target{lat, lon, kind restaurant|customer|unknown, origin intent|clipboard|search|map|order, requestedAtMs, sourcePackage}`, `route{remainingM 0–500 000, remainingTurns 0–1 000, measuredAtMs}`) içerik sağlayıcıdan okunur.
- **WEEM:** aynı bilgi `NavPort.route: StateFlow<RouteStatus?>` (`RouteStatus.target`/`route` aynı alanlar; A2.5 `RouteStatus.fromNavFeed` bugünkü yükü okur). Kaynak adları korunur: navigasyon mesafesi `nav_yolgo`, restoran koordinatı `gps_dwell_yolgo_target`/`gps_near_yolgo_target` (kayıtlarda geçen dizgiler; gölge modla karşılaştırılabilsin). Hedef değişince 4.9'daki bağ kurulur.
- **`pickupOf(hedef, siparişler, iz, now)`:** yalnız `origin = intent` (kurye uygulamasının "Navigate" isteği) ve istek −60 sn ile `TARGET_MAX_AGE_MS` (90 dk) arasında. Adaylar: aktif Wolt siparişleri (gönderen paket biliniyorsa o paket), alış penceresi isteği kapsayan alış. İstek anında platformun ekranı (`ScreenTrail`, ≤ `TRAIL_MAX_GAP_MS` 2 dk önceki son değişim): DROPOFF ise yok; PICKUP ise o ekrandaki siparişin (ya da tek) adayı; ekran bilinmiyorsa yalnız `kind = restaurant` ve tek aday. Bağlanınca restoran koordinatı = hedef (geocode'dan önce gelir, `preferred`); olay `events.jsonl` `yolgo_venue {trip, pickup, kind, source_package, geocode_gap_m}` (koordinat yazılmaz); açık alış hemen yeniden denetlenir (`venues.live`). WEEM: olay adı `yolgo_venue` korunur.
- **`ScreenTrail`:** platform başına aşama değişimleri (an, aşama, sipariş id), 30 dk; eski an ya da aynı (aşama, sipariş) tekrar yazılmaz.
- **Navigasyon mesafesi seçimi:** restoran için hedef bu alışa bağlıysa rota mesafesi (`nav_yolgo`), sipariş restoran aşamasındaysa başka uygulamanın bildirim özeti (`nav_google_maps`, `CAPTURE.md` 4.2); müşteri için hedef `customer` ve bu bacakta istenmişse rota, sipariş TO_CUSTOMER'da ise bildirim özeti; `ArrivalDistance.pick`. Müşteri koordinatı: bu bacakta istenmiş müşteri hedefi, yoksa çözülmüş adres (4.10).

### 4.10 Adres → koordinat (Android `Geocoder`, RS `OrderTracker.kt:664-697`)

- Restoran: alış adresi (sipariş:alış:adres başına bir kez), arka planda `getFromLocationName(adres, 3)` ilk koordinatlı sonuç → bellekte (`restaurantCoords`). Müşteri: (sipariş:müşteri:adres başına bir kez) sonuç varsa ve son fix yoksa ya da sonuç ona < 50 km ise `lat/lon`, `coordinateSource = android_geocoder_address_estimate`, `persist(force)`; sonuç yoksa `events.jsonl` `customer_coordinate_unavailable {trip, customer}`. Adres o arada değiştiyse sonuç atılır.
- `Geocoder` sistem servisidir (ağı sistem kullanır). Kapsam listesindeki "adı olmayan eski davranış" (S9: kalır); `:orders`'ta, `:capture` ağsız kalır. WEEM'in kendi adres bulması (Y6, `:map`) bunun yerine geçmez (davranış aynı kalsın; değişiklik ancak kullanıcı kararıyla).

### 4.11 Telefon numarası (K25; RS `OrderTracker.kt:757-771`)

- `awaitingPhone(now)` = çekirdek çalışıyor ∧ (son 60 sn'de (`CALL_TO_DIALER_MS`) arama düğmesine basılmış, teslim edilmemiş müşteri var ∨ müşteri aşamasında bir siparişin platform ekranı son 60 sn'de (`PHONE_WINDOW_MS`) okunmuş).
- `dialerPhone(metinler, now)`: kayıt izni; metinlerdeki ilk telefon (`NoteFields.phones`); müşteri = arama hedefi, yoksa müşteri aşamasındaki ve platform ekranı 60 sn'de okunmuş siparişlerin son görüleninin şimdiki müşterisi; numara aynıysa dur; `details.phone` (bellek → `courier.db`), açık kart güncellenir.
- WEEM: "platform ekranı son okunma anı" `:orders`'ın kendi tuttuğu değerdir (her `CaptureSink.onScreen` karesinde paket için an; Rawstats `Store.seen` ile aynı anlam).

### 4.12 Kayıtların yeniden kurulması (`rebuildRecords`, `:910-926`; ZIP içe aktarma sonrası `reloadImportedStats`)

- `courier.db`, bellekteki siparişler, teklifler, anlık görüntü özetleri silinir. Gün klasörleri ada göre sırayla; her `orders.jsonl` satırı: `record` (yoksa `LegacyRecords.delivered(satır)` → `RecordJson.trip`) sipariş başına **en yenisi**; `offer` teklif başına en yenisi; `offer` dışı olayların `offer_id` → sonuç (en yenisi). Siparişler ve sonuçlu teklifler `courier.db`'ye; bugünün siparişleri belleğe.
- A2.3 `LogImport` ZIP'ten sonra bunu çağırır (A2.6'da Rawstats ZIP'i içe aktarıldı; sipariş tarafı A3'te bağlanır).

### 4.13 Elle işlemler ve okuyucular

- `confirmDelivered(id)` (kart "Teslim edildi") → 3.7; `setTip(id, tutar)` → `tip`, `tipSource = user_manual`; `showDetails(id)` → kartı açar ("Teslimat bilgileri"); `dismissReminder()` (restoran balonu kapatıldı → o alış için bir daha açılmaz, bellek); `markCancellationObservation(pkg)` (kullanıcı iptal bildirdi: ekonomi beklemesi kırılır + `events.jsonl` `cancellation_user_reported {pkg, platformActionPerformed: false}`); `rateOffer` (E4, `:economics`).
- Okuyucular: `today` (bugün kabul edilenler, yeniden eskiye), `stored` (tüm `courier.db` siparişleri; bozuk satır atlanır), `stats` (Bölüm 10), `onlineToday` (Bölüm 9), `badgeLines` (Bölüm 6), `platform(pkg)` (`wolt` → "Wolt"; `bolt`/`mtakso` → "Bolt Food"; diğer → paketin son parçası), `commission(pkg)`, gün dosyası okuyucuları (`cashTipRows`, `boostRows`, `cashPendingRows`, `pocketCashRows`, `platformTotals`, `platformHistoryRows`, `offerScores/Ratings`): dosya yoksa ya da okunamıyorsa boş.

### 4.14 WEEM yerleşimi ve `:economics` sınırı

- **`logic/orders` `OrderFlow` (saf):** 4.1–4.13'ün karar ve sırası; durum (`DeliveryCore`, `OnlineSessions`, `BoostLog`, `VenueArrival`, `ScreenTrail`, hatırlatıcı, D21 penceresi, navigasyon bağı, resmî değer ve nakit eşlemeleri). Dış dünyaya: `OrderStore` (gün satırı ekleme, `courier.db` satırları, tercihler; Android'de `:core` `Store`/`CourierDb`), `Clock`, `Geocode` (istek; sonuç geri çağrıyla), bildirim/pencere istekleri (`NotifyPort`, R6 penceresi), `ScooterBridge`, `NavPort`, `WeatherPort`. JVM'de sahte uygulamalarla test edilir (`SahaTekrarTest` gibi uçtan uca testler buna dayanır).
- **Android `:orders`:** `OrderFlow`'un Android uygulamaları (`Geocoder`, `courier.db`, bildirimler, `CashTipPrompt`/`PocketCashPrompt`/`TipAmountActivity`, rozet ve balon pencereleri `CaptureHost` üstünden).
- **`OrderObserver` (`:orders` tanımlar, `:economics` uygular, `:app` bağlar; bağlı değilse `NONE`):** Rawstats'ta `OrderTracker`'ın ekonomi kaydedicilerine yaptığı her çağrı burada **aynı noktada ve sırada** bir kancadır: açılış (`econ.restart`, açık boşta dönemi ve beklemelerin geri yüklenmesi), her kare (4.3 adım 13 ve 17: bekleme nedenleri, `waitCore.observe`, `economics-exposure.jsonl`, gölge karar, `econ.frame`), olay üreten diğer girişler (dokunuş, nakit toplama, konum, navigasyon bağı, elle teslim: `econ.events`), yol (`econ.distance`), vardiya sonu (`waitCore.stop`, `econ.stop`), iptal bildirimi. Kanca, olayları, olaydan önceki bekleyen teklifleri, salt okunur sipariş görünümünü, açık oturumları, kaba bölgeyi (`OfferGeo.coarseZone`, son 2 dk'lık fix) ve ücret tabanını (`commission` ayarlıysa `gross_before_park`, değilse `unknown`) alır. A4.1 bunları uygular; A3.5–A3.7'de kanca boştur ve ekonomi dosyaları yazılmaz (gölge modda bilinen fark, Bölüm 14).
- **Teklif özeti için ekonomi** (`OfferSummary.score`, `netAzn`): `:orders` `OfferAnnotator` arayüzü (teklif id → puan, net), `:economics` uygular; yoksa null (veri uydurulmaz, A2.5 kuralı).

## 5. R6 — Restoran balonu, müşteri balonu ve kartı, bildirim 12 (RS `OrderTracker.kt:226-234, 574-663, 744-749, 781-785`; `DeliveryReminder.kt`, `RestaurantBubble.kt`, `CustomerCard.kt`, `CustomerBubble.kt`, `Readiness.kt`)

### 5.1 Hatırlatıcı (`Reminder`) ve kim açar

- Tek açık hatırlatıcı: `Reminder(trip, customer, title, details, estimate, revision, kind customer|restaurant, headline, body, place, ready, deadlineAt, orders)`. Restoran hatırlatıcısının `customer` alanı `pickup-<i>`.
- **Müşteri kartı açılışı (E16, tikte):** müşteri aşamasındaki her aktif siparişin şimdiki müşterisi için **bir kez** (`reminderAt` yok, teslim edilmemiş): ilk tutan sebep — düz çizgide adrese ≤ `STRAIGHT_M` (300 m) (fix ≤ `FIX_MAX_AGE_MS` 15 sn taze ve doğruluğu bilinen ≤ 40 m; hedef: bu müşteriye giden rota varsa onun hedefi, yoksa çözülmüş adres), bu müşteriye giden rotada kalan ≤ `ROUTE_M` (300 m), rotanın son ≤ `LAST_TURNS` (2) dönüşü (dönüş sayısı bilinmiyorsa tek başına tetiklemez). "Bu müşteriye giden rota" (`routeFor`): rota ölçümü −5…+30 sn içinde, hedef restoran değil, istek bacak başlangıcından (alış ya da önceki teslim) en çok `REQUEST_SLACK_MS` (60 sn) önce ya da sonra. Açılınca `reminderAt` = şimdi, başlık `"<Platform> · <ad ?: müşteri id>"`, ikinci satır sebep metni ("Adrese yaklaşık N m (kuş uçuşu)" / "Rotada yaklaşık N m kaldı (YolGo)" / "Son N dönüş (YolGo, <mesafe>)" + AZ), son tarih, sipariş sayısı (1 + aynı karttaki açık siparişler); bildirim 12; `persist(force)`.
- **Restoran balonu (E14, tikte):** restoran aşamasındaki her aktif siparişin alınmamış son alışı: adres varsa koordinatı bir kez çözülür (4.10); balon açıksa ya da kurye bu alış için kapattıysa atla; mesafe = taze (15 sn), doğruluğu ≤ 40 m fix'ten restoran koordinatına (D9'un koordinatı: bağlı navigasyon hedefi, yoksa geocode) düz çizgi. `RestaurantBubble.visible(gösteriliyor, mesafe, varış görüldü, alındı)`: alındı → kapalı; varış görüldü → açık; mesafe yok → değişmez (yeni açılmaz); açıkken ≤ `HIDE_M` (150 m) kalır; kapalıyken ≤ `SHOW_M` (50 m) açılır. Alış başına ilk açılış bildirim 12'yi atar (`reminderAt`). Tahmin satırı: taze (≤ 20 sn) navigasyon bildirimi özeti "Kalan: …" / "Qalıb: …", yoksa "Restorandasın" (varış) / "Restorana yaklaşık N m (kuş uçuşu)" / "Restorana yakınsın" + AZ.
- **Açık hatırlatıcının güncellenmesi:** restoran — sipariş yok/kapandı, alış alındı ya da sipariş restoran aşamasında değil → kapanır; 150 m'yi aştı → kapanır (50 m'de yeniden açılabilir); değilse yeniden kurulur, E15 başlığı değiştiyse bildirim **sessizce** güncellenir. Müşteri — sipariş kapandı, şimdiki müşteri değişti ya da teslim edildi → kapanır; değilse canlı satır: bu müşteriye giden rota "Kalan: <mesafe> · N dönüş (YolGo)", yoksa navigasyon bildirimi özeti.
- Kare akışında (4.3 adım 20) açık müşteri kartı ayrıntı, revizyon ve son tarihle tazelenir; telefon okununca da (4.11).
- Elle: `showDetails(id)` kartı açar (tahmin "Teslimat bilgileri"); kurye balonu/kartı kapatırsa `dismissReminder` (restoran: o alış için bellekte "kapatıldı"; müşteri: `reminderAt` kaldığı için kendiliğinden yeniden açılmaz).
- **Restoran hatırlatıcısının içeriği:** başlık `"<Platform> · <restoran ?: Restoran>"`; başlık satırı E15 (`Readiness.headline`: "✓ HAZIR", "Şimdi (süre doldu)", "N dk sonra hazır", "Hazırlanıyor", "Hazır olup olmadığı bilinmiyor" + AZ); gövde "Restoran notu: …", "Sipariş: <kod · ürünler>", adres (boş satırla ayrık); balon dairesi `Readiness.short` ("hazır", "şimdi", "N dk", "…", "?").
- **E15 hazır durumu** (`Readiness.of(alış, now)`): `readyObservedAt` → READY; değilse (`readyHintAt` ?: `readyAt`) varsa kalan dakika yukarı yuvarlanır: > 0 → COUNTDOWN(N), değilse NOW ("şimdi" süre doldu demektir, hazır değil); değilse `preparingAt` → PREPARING; yoksa UNKNOWN.

### 5.2 Pencereler (`DeliveryReminder`; hepsi `TYPE_ACCESSIBILITY_OVERLAY`, odaklanmaz, dokunuşu geçirir, üstte, y = 28 dp)

- Tercih `customerCard` (varsayılan true) false ise **hiçbiri** açılmaz (restoran balonu dahil).
- **Müşteri balonu** (F7.2/F7.3): genişlik = `restaurantBubbleWidthPct` (70/80/90/100, varsayılan 90; geçersiz değer → 90) × ekran genişliği; yazı boyu = kart boyu ölçeği (`customerCardSizePct` 70/85/100/115, varsayılan 85). Sol satır `CustomerBubble.line`: bina (≤ 4 karakterse "Blok X"), "N. kat"/"N-cu mərtəbə", daire ("D N"/"M N"; "Oda N" → "Otaq N"), "Kod N" — yalnız bilinenler, " · " ile; hiçbiri yoksa ad, o da yoksa "Müşteri". "N sipariş" (≥ 2, R1). Sağda daire: son tarihe kalan dakika (yukarı yuvarlı; gecikmede eksi; süre dolunca kehribar). Altta notlar: kimlik kontrolü, adres detayı, not, (nottan farklıysa) talimat, kısaltılmış not uyarısı; boşsa "Müşteri detay paneli henüz okunmadı. Kurye uygulamasında paneli açınca bu kart güncellenir." + AZ; notlar en çok ekran yüksekliğinin %25'i (kayar). Telefon biliniyorsa "Ara <numara>" düğmesi → `ACTION_DIAL tel:<yalnız rakam ve +>` (numarayı çevirir, aramaz). Kaydırma: yatay > balon genişliğinin %25'i → kapat; dikey > 48 dp → notları katla/aç; değilse tıklama.
- **Restoran balonu** (E14): aynı genişlik ayarı; ad (17 sp × ölçek, tek satır) solda, hazır dairesi sağda (renk hazır durumuna göre); dokununca **tam kart** açılır.
- **Tam kart** (yalnız açılan restoran hatırlatıcısı): genişlik ekran (%2 kenar), yükseklik = ekran × (0,40, "Büyüt"te 0,62) × kart boyu/100; başlık 17 sp, alt satır 13 sp, gövde 16 sp (kayar), "Büyüt/Küçült", "Kapat" (× ölçek). Renkler Sürüş Paneli paleti (gece/gündüz).
- Hata: "Müşteri kartı açılamadı: " / "Restoran balonu açılamadı: " + mesaj (son hata).

### 5.3 Bildirim 12 (`DeliveryReminder.notify`)

- Kanal `delivery_notes` ("Müşteri teslimat notu" / "Müştəri çatdırılma qeydi", IMPORTANCE_HIGH, titreşim); numara `NotificationIds.DELIVERY_NOTE` = 12; dokununca uygulama; `autoCancel`; güncellemede `onlyAlertOnce`.
- Başlık = hatırlatıcı başlığı. Kısa metin: restoran → E15 başlığı ?: tahmin; müşteri → `facts()` " · " ile, boşsa not ?: talimat ?: adres ?: "Teslimat bilgileri". Büyük metin: restoran → başlık satırı, tahmin, gövde; müşteri → **`details.display()` (telefon satırı dahil)**.
- Vardiya sonunda iptal (`CORE.md` 3.3). WEEM: `NotifyPort.post(Notify(DELIVERY_NOTE, …))`.

### 5.4 WEEM

- Kurallar saf (`logic/orders`: `RestaurantBubble`, `CustomerCard`, `CustomerBubble`, `Readiness`, hatırlatıcı kararları `OrderFlow`'da); pencereler Android `:orders`'ta, `CaptureHost.onTick` ile gelen erişilebilirlik servisi bağlamında açılır (`CAPTURE.md` 7.1). `CustomerCard.routeFor` `YolGoNav.State` yerine `NavPort.route`'u (`RouteStatus`) okur.
- Metindeki "(YolGo)" etiketleri: WEEM'in kendi rotası olduğundan **"(WEEM)"** yazılır (ör. "Son 2 dönüş (WEEM, 300 m)"); başka uygulamanın bildirim satırı "Kalan: …" olarak kalır. (Bilerek fark; kullanıcı başka söz isterse metin tablosunda değişir.)
- 2 telefon modunda balon ve kart **yalnız Provider'da** (Wolt/Bolt telefonu) açılır; Receiver'a müşteri ayrıntısı gitmez (S5). Bildirim 12'nin telefon satırı bugünkü gibi kalır; ayrıca soru (Bölüm 16).

## 6. R7 — Kurye uygulaması üstündeki rozet (RS `OverlayText.kt`, `OrderTracker.kt:293-296, 891-900`, `WatchService.kt:347-401`)

### 6.1 Metin (`OverlayText.badge`; saf, `BigDecimal` HALF_EVEN)

- İlk satır mesafe: `"● <vardiya km, 1 hane> km"` (vardiya D8 mesafesi).
- Sonra, satır satır: **her bekleyen teklif** `"<Platform> · <ücret, 2 hane>₼ [· net <net>₼] · <restoran|Teklif>"` (ücret okunmadıysa 0.00₼; net yalnız komisyon ayarlıysa: ücret × (1 − komisyon/100), bahşişsiz; komisyon = `commission:<pkg>` ?: `commission`); **her aktif sipariş** `"<Platform> · <son alışın restoranı ?: şimdiki müşterinin adı ?: Sipariş> · <faz>"`; **teklifi ve siparişi olmayan her bilinen platform** (oturumu olanlar; platform başına tek satır, paketlerinden biri çevrimiçiyse çevrimiçi) `"<Platform> · Sipariş bekleniyor"` ya da `"<Platform> · Çevrimdışı"` (K30); son satır `"Bugün: N sipariş"` (`OrderHistory.deliveredOrders`, çıkarımlı dahil; Bölüm 10).
- **K35 rozet (`names = false`):** teklifte restoran yerine "Teklif", siparişte yalnız `"<Platform> · <faz>"` — rozette **hiçbir ad yok**. Ana sayfa (`names = true`) adları gösterir.
- Faz metinleri TR/AZ: Restorana gidiliyor / Restorana gedilir; Restoranda bekleniyor / Restoranda gözlənilir; Müşteriye gidiliyor / Müştəriyə gedilir; Müşteriye varıldı / Müştəriyə çatıldı; Teslim edildi / Çatdırıldı; Sonuç doğrulanamadı / Nəticə təsdiqlənmədi. Platform adı `platform(pkg)` (4.13). Ayraç `" · "`.
- Her satır ayrıştırıcının kendi satırları tanımasıyla (`CAPTURE.md` 2.10) eşleşmelidir: tek söz dağarcığı `OwnLines` (`logic/capture`); test her biçimlendirilen satırın `isOwn` olduğunu denetler (`ParserEconomicsTest` `everyPanelLineIsRecognisedInBothLanguages`).

### 6.2 Pencere (`WatchService`, `CAPTURE.md` 1.6)

- Vardiyada ve tercih `overlay` (varsayılan true) açıkken; vardiya dışında kaldırılır. 12 sp sans-serif-medium, yazı dolgusuz, dolgu 10/7 dp, yükselti 4 dp, köşe 16 dp, Sürüş Paneli paleti (gece/gündüz: yazı, yüzey, çizgi); `TYPE_ACCESSIBILITY_OVERLAY`, `WRAP_CONTENT`, odaklanmaz, dokunuşu geçirir; konum üst-sağ, x = `bx` (12), y = `by` (95) **piksel**; sürüklenir (x = başlangıç − dx, y = başlangıç + dy), bırakınca `bx`/`by` yazılır. 500 ms'de bir; metin aynıysa dokunulmaz.
- **D21 kaçış** (13 Ekim saha fotoğrafları: rozet Wolt'un Decline'ını ya da kartın "Expected earnings" satırını örtüyordu): teklif kartı ekrandayken (son teklif karesinden 2 500 ms içinde) rozet ekran yüksekliğinin %15–50 bandına iner (kayıtlı y bu banttaysa o, değilse %30) ve 10 sp olur; kart gidince kayıtlı konum ve 12 sp.
- Hata: "Rozet açılamadı: " + mesaj (son hata).

### 6.3 WEEM

- Biçim `logic/orders` `OverlayText` (söz dağarcığı `OwnLines`'tan); pencere Android `:orders`'ta (`CaptureHost`), dikdörtgeni `OwnOverlays`'e kaydolur (tüm ekran OCR maskesi, `CAPTURE.md` 3.5). Gölge modda iki uygulamanın rozeti üst üste gelebilir; WEEM `.dev`'in rozeti Daha fazla'dan kapatılabilir (`overlay`).

## 7. R8 — Nakit sipariş ve bahşiş (K31, K32, M16, F4.6–F4.9; RS `CashTips.kt`, `CashTipPrompt.kt`, `TipAmountActivity.kt`, `res/layout/tip_quick.xml`)

### 7.1 Nakit ekranı (`CashPayment`, K31/M16; saf)

- Yalnız Wolt. Satırlar küçük harf (Kotlin `lowercase()` = yerel ayardan bağımsız) ve `[\s\p{Z}]+` tek boşluk. Nakit işaretleri: `^cash order$`, `^collect cash payment$`, `^i have collected the cash` (sözcük sınırlı); adımlar başlığı `^steps to complete the delivery$`. İkisi de yoksa ekran değildir (null).
- Tutar: satırlar boşlukla birleşik metinde `i have collected the cash (azn|₼ N[.NN])` (yalnız nokta ondalık; > 0). Para üstü: `^no change needed$` → 0,0; değilse `^(total )?change needed$` satırının sonrasındaki, yoksa öncesindeki `[-](azn|₼) N[.NN]`. Kutunun işareti yalnız erişilebilirlikten (OCR göremez) ve yalnız nakit ekranında.
- `attach(gözlem, ekran)` → `cashOrder, cashAmount, cashStepsSeen, cashChecked, cashChangeAzn` (ayrıştırıcının sözleşmesi değişmesin diye gözleme sonradan yazılır). Siparişe uygulanışı: 3.10.
- **Kart satırı** (`cardLine`, yalnız nakit sipariş): "Nakit 20.40₼" ya da "Nakit (tutar okunamadı)" · "para üstü yok" / "para üstü N₼" · "kutu ✓ HH:mm" ya da "kutu: görülmedi" (+ AZ: Nağd, qalıq, qutu).
- **Gün nakdi** (`dayTotal`): teslim sayılan (çıkarımlı dahil) nakit siparişlerin tutar toplamı, 2 hane HALF_EVEN.
- Bolt'un nakit ekranı hiç görülmedi, tanınmaz.

### 7.2 Bahşiş sorusu ve kaydı (K32; `CashTips`)

- **Ne zaman:** nakit sipariş teslim edildi (`delivered`) ya da doğrulanmamış teslim (`delivered_inferred`), o güne ait cevap yok, bu süreçte sorulmadı (4.4). Doğrulanmamış teslimin sonraki gerçek sinyali ya da D3 nakit satırı yeniden sordurmaz.
- **Bildirim** (`CashTipPrompt.ask`): kanal `cash_tips` ("Nakit bahşiş sorusu" / "Nağd çaypulu sualı", HIGH); numara Rawstats'ta `4000 + (tripId.hashCode() and 0xFFF)`, **WEEM `NotificationIds.cashTip(tripId)` = 10000 + (aynı)**; başlık "Ne kadar nakit aldın?" / "Nə qədər nağd pul aldın?"; metin `"<Platform> · Nakit: X₼; bahşiş = aldığın − X₼"` ya da (tutar yok) `"<Platform> · nakit sipariş; tutar okunamadı, dokun ve yaz"`, yeniden soruda başta "Sayı okunamadı, örn. 25 veya 25.50. " (+ AZ); kategori mesaj; "Yanıtla" (`RemoteInput` anahtar `cash_received`, etiket "Alınan nakit (₼)"; değiştirilebilir `PendingIntent`, kendi alıcısı, dışa açık değil); tutar biliniyorsa hızlı düğmeler **+1, +2, +3, +5 ₼** ve "Diğer" (özel görünüm `tip_quick`); dokunuş küçük pencereyi açar. Müşteri verisi **yok** (yalnız platform ve tutar).
- **Cevap:** yazılan tutar `parseInput` (`25`, `25.5`, `25,50`, `25 ₼`, `AZN 25`; 1–4 hane, en çok 2 ondalık) → kayıt; sayı değilse soru ipucuyla yeniden. Hızlı düğme: alınan = nakit + bahşiş (tutar bilinmiyorsa düğme yok). Pencere (`TipAmountActivity`, dışa açık değil, son uygulamalarda görünmez): üstte "<Platform> · Nakit: X ₼" ya da "Nakit tutarı okunamadı — aşağıya yaz"; tutar yoksa "Nakit tutarı (₼), okunamadı" alanı; "Aldığın para (₼)"; canlı "Bahşiş: N ₼" / "Fark: −N ₼ (eksi)" / "Bahşiş: —"; Kaydet (geçerli sayı ve tutar varken) / Vazgeç. Sipariş kartında da aynı giriş (`via = card`, Bölüm 10).
- **Kayıt** (`recordCashTip` + `CashTips.record`): sipariş bellekte ya da `courier.db`'de, nakit değilse hiçbir şey. `economics-cash-tips.jsonl`'e (siparişin kabul gününe) satır: `schemaVersion` 1, `kind` `cash_tip`, `trip_id`, `pkg`, `platform` (`wolt`/`bolt`), `at`, `via` (`notification`, `notification_quick`, `tip_window`, `card`), `received_azn` (2 hane), `cash_amount_azn` (yoksa null), `cash_amount_source` (pencerede yazılan tutar → `user_manual`; okunan tutar → siparişin `paymentSource`'u ?: `not_observed`), `tip_azn` = alınan − nakit (`BigDecimal` HALF_EVEN, 2 hane; nakit yoksa null), `tip_source` (`user_cash_input` ya da hızlı düğmede `user_quick_tip`; bahşiş yoksa null), `negative_tip`, `delivery` (`delivered`/`delivered_inferred`). Eksi fark **kırpılmaz**, işaretlenir. Sipariş: tutar okunmamış ve yazılmışsa `cashAmount`; bahşiş varsa `tip`, `tipSource`; bellekteyse `persist`, değilse yalnız `courier.db` satırı (gün dosyaları asla yeniden yazılmaz).
- **Okuma** (`latest`): geçerli (`schemaVersion` 1, `trip_id`, `received_azn`) satırlardan sipariş başına en yenisi (eşit anda sonraki satır).
- **Onay bildirimi** (`saved`): "Nakit kaydedildi" / "Nağd pul qeyd edildi"; "Alınan X₼ · bahşiş Y₼" ya da "· fark −Y₼ (eksi, kartta kontrol et)" ya da "· nakit tutarı okunamadı, bahşiş hesaplanmadı"; 15 sn sonra kalkar; dokununca uygulama.
- WEEM: kural ve satır saf (`logic/orders`); bildirim, alıcı (`CashTipReceiver`) ve pencere Android `:orders` (manifestte dışa kapalı). İstek kodları (`PendingIntent`) bildirim numarasından türer (`id`, hızlı düğme `id × 8 + i + 1`).

## 8. R9 — Cepteki nakit (M15) ve Wolt CASH PENDING (M17) (RS `PocketCash.kt`, `PocketCashPrompt.kt`, `CashPending.kt`, `OrderTracker.kt:800-842`)

### 8.1 Vardiya başı cep nakdi (M15)

- Yeni vardiya başında (4.2 `onNewShift`) bildirim **4901**: kanal `pocket_cash` ("Vardiya başı nakit sorusu" / "Növbə əvvəli nağd sualı", HIGH), başlık "Şu an cebinde ne kadar nakit var?" / "İndi cibində nə qədər nağd pul var?", metin "Vardiya başladı. Yaz ya da dokun." (yeniden soruda başta "Sayı okunamadı, örn. 30 veya 12.50. "), "Yanıtla" (`RemoteInput` anahtar `pocket_cash`, ek `shift_start_ms`), dokunuş küçük pencere (`PocketCashActivity`).
- Cevap `PocketCash.parse` (bahşişle aynı biçimler; **0 geçerli** = boş cep). Kayıt `economics-pocket-cash.jsonl`'e **vardiya başlangıcının gün klasörüne**: `{schemaVersion: 1, shift_start_ms, amount_azn (2 hane), answered_at_ms, via: notification_reply|window}`; vardiya başına en yeni cevap geçerli (düzeltme = yeni satır). Onay "Kaydedildi" + "Vardiya başında cepte: X₼" (10 sn).
- Okuma: `latest(satırlar, vardiyaBaşı)`, `forDay(satırlar, gün)` (Bakü günü, en eski vardiya önce).

### 8.2 CASH PENDING ve nakit yatırma (M17)

- **Okuma** (Wolt Finances karesi, 4.3 adım 4): bir metin "pending" içeriyorsa satırlar (bölünmüş, küçük harf); `cash pending` ile başlayan satırın kalanında ya da sonraki 2 satırda `[-] (azn|₼) [-] N.NN` (işaret önde ya da arkada; `+` = Wolt'a yatırılacak, `-` = fazla yatırılmış). Son değer tercihte: `cashPending.azn` (String), `cashPending.at` (Long).
- **Yazma** `economics-cash-pending.jsonl` (`schemaVersion` 1; satırlarda `t` yok, an `observedAtMs`): `pending` `{pending_azn, observedAtMs}` — değer son yazılandan ≥ 0,005 farklıysa ya da son yazım ≥ `REWRITE_MS` (5 dk) önceyse; `offload` `{day, clock, amount_azn, observedAtMs}` — "Cash offload" satırı başına bir kez (bellek), satırın gününe; `shift_start`/`shift_end` `{shiftStartMs, pending_azn?, pendingObservedAtMs?, observedAtMs}` — vardiya başı ve sonu (vardiya başının gününe; değer hiç görülmediyse alan yok); vardiya sonunda vardiyanın her siparişi için `order_snapshot` `{tripId, shiftStartMs, pending_azn?, observedAtMs}` (sipariş kaydı değişmez).
- **Mutabakat** (`reconcile`): başlangıç (vardiya başı satırı, yoksa vardiyada görülen ilk değer) + toplanan (vardiyanın teslim sayılan nakit siparişlerinin nakdi, `dayTotal` biçimi) − yatırılan (vardiya içindeki tekil yatırma satırları, Bakü saati) = beklenen; fark = son (vardiya sonu satırı, yoksa son görülen) − beklenen; |fark| ≤ 0,01 → tamam. Satır: `"Pending: 8.21 → 12.40₼ · beklenen 12.40₼"` (+ `" · fark 1.20₼"`). Değerlerden biri yoksa mutabakat yok.
- Not: F8'in "bakiye yazılmaz" kuralı bu tek sayı için kullanıcı isteğiyle değişti (RS `CashPending.kt:6-11`).

## 9. R10 — Boost / Earn extra (K27, D11, D24, M21, E3; RS `BoostLog.kt`) ve R11 — Çevrimiçi süreler (RS `OnlineSessions.kt`)

### 9.1 Boost gözlemi (`BoostLog.seen`, `observe`)

- Yalnız **tam** ve **ekrandan** (bildirim değil) IDLE/OFFLINE/OFFER kareleri. Bolt: tek canlı çarpan (`x`, aktif); birden çok farklı çarpan → gözlem yok. Diğer: `boostPct` ?: `bonusPct` → yüzde (aktif = `bonusPct` var), pencere, "başlar"; teklif kartında boost yoksa gözlem yok (kart ana ekran boost'unu göstermez); IDLE/OFFLINE'da boost yoksa "boost yok" gözlemi (`value` null).
- **`change` satırı:** platformun gözlem anahtarı (birim, değer, aktif, pencere) değişti ya da gün değişti → `first_seen` (günün ilki), `prev_value`, `prev_unit`, `prev_known`.
- **`slot` satırı (Bolt):** her 30 dk dilim (`SLOT_MS`) ilk gözlemde `observed: true`; aynı gün içinde ara 2–9 dilimse aradaki dilimler `observed: false` (`at` null; en çok `MAX_GAP_SLOTS` = 8 dilim = 4 saat geriye; uydurulmaz).
- **`plan` satırı (Wolt, D24/M21):** OFFER dışındaki tam ekran karelerinde `boostSlots`; dilimin günü satırdaki "17/10" (180 günden eskiyse gelecek yıl) ya da bugün (Bakü); başlangıç–bitiş (pencereden; bitiş ≤ başlangıçsa ertesi gün; yalnız "from HH:MM" ise 60 dk, `FROM_SLOT_MIN`); kaynak: karede "coming up later" varsa `earn_extra` (liste), değilse `home_screen`. Aynı dilim aynı yüzdeyle yeniden yazılmaz (yalnız şimdi "Now · Boost" altında görüldüyse plan belleği güncellenir). Satır: `{kind: plan, pkg, platform, at, value (yüzde), unit: pct, active, window, from, slot_start_min, slot_end_min, prev_value, plan_source, slot_day, stage, lat, lon}`; bellek en çok 400 dilim.
- `economics-boosts.jsonl` (`schemaVersion` 1) satır alanları: `kind`, `pkg`, `platform` (`wolt`/`bolt`), `at`, `value`, `unit` (`pct`/`x`), `active`, `window`, `starts_in_min`, `stage`, `lat`, `lon`, `gps_accuracy_m`, `gps_age_s` (+ türüne göre yukarıdakiler). **Burada null değerler `JSONObject.NULL` olarak yazılır** (anahtar vardır, değer null) — diğer dosyalardan farklı; WEEM aynen. Konum yalnız telefonda (yedek hariç).
- Saat dilimi `Asia/Baku` (`dayOf`, `minuteOfDay`).

### 9.2 Teklifin boost'u (`atOffer`, 4.3 adım 16)

- Bolt'ta karttaki tek çarpan → `offer_screen`; kartın aktif yüzdesi (`bonusPct`) → `offer_screen`; Wolt planında teklif anını kapsayan dilim (görülme anı ≤ teklif + 60 sn; liste dilimi ana ekranınkini yener; eşitlikte "Now · Boost" okuması) → `wolt_earn_extra` ya da `wolt_plan`; ≤ `FRESH_MS` (30 dk) taze ana ekran gözlemi → aktifse değeriyle, değilse değersiz `home_screen` (duyurulmuş ama başlamamış Wolt boost'u bu teklife uygulanmaz); hiçbiri → `not_observed`.
- Gün sayfası istatistiği (`stats`): değişim sayısı (`prev_known`), gözlenen/eksik/boost'lu dilim, en yüksek aktif değer ve birimi.

### 9.3 Çevrimiçi oturumlar (R11, `OnlineSessions`)

- **Durumu yalnız olumlu kanıt değiştirir:** tam karede `availability` (`CAPTURE.md` 2.5) ya da görev ekranı (OFFER/PICKUP/DROPOFF → çevrimiçi, kanıt `task_screen`). Talep haritası hiçbir şey söylemez. Platformun son karesinden eski kare (geç OCR) oturum açmaz/kapatmaz.
- **OCR kanıtı:** Wolt Feedback sayfası dışında, durumu değiştiren OCR kanıtı ancak ikinci bir OCR karesi aynı durumu söyleyince geçerli; geçerlilik anı ilk karenin anı.
- Çevrimiçi: oturum yoksa açılır (`startObserved` = önce çevrimdışı görülmüştü; kanıt), varsa son çevrimiçi an ileri. Çevrimdışı: platform "çevrimdışı görüldü"; açık oturum kapanır, son = max(an, son çevrimiçi), neden `wolt_feedback_after_go_offline` ya da `offline_screen`, `endObserved: true`.
- **Sayaçlar** (olaylardan, açık oturumda): `offer` (+ id, en çok 200), `accepted` (+ sipariş id, tekil, en çok 200), `rejected`, `customer_delivered` (+1, sipariş başına sayılır), `delivered` (sipariş müşteri sayısı − önceden sayılan; çıkarımla daha önce sayıldıysa eklemez), `delivered_inferred` (müşteri sayısı − sayılan; sonraki gerçek `delivered` eklemez). Paket teslimatta çift sayım yok.
- **Vardiya sonu:** açık oturumlar son çevrimiçi ekranda kapanır (`endObserved: false`, neden `shift_stop`); süreç vardiya bittikten sonra öldüyse açılışta `restart_after_shift`.
- **Kimlik** `"<wolt|bolt|paket sonu>-<başlangıç ms>"` (`bolt` yalnız paket adında "bolt" varsa; `mtakso` → paketin son parçası).
- **Dosya** `online-sessions.jsonl` (gün klasörü; kapanan her oturum): `{schemaVersion: 2, pkg, sessionId, startAtMs, endAtMs, durationMinutes, startObserved, endObserved, endReason, lastOnlineSeenAtMs, startEvidence, offersSeen, accepted, rejected, delivered, offerIds, tripIds, t (= bitiş)}`; okuyucu v1'i (yalnız sayaçlar) de okur. Açık oturumlar tercih `onlineSessions.v1` (JSON dizi: `pkg, start, startObserved, last, offers, accepted, delivered, id, rejected, offerIds, tripIds, startEvidence`): kapanan oturum varsa ya da zorla, değilse en çok 10 sn'de bir.
- Rozet durum satırı `known()`/`state()` kullanır (6.1); bugünkü süreler (`onlineToday`): günün dosyası + açık oturumlar (son = max(son çevrimiçi, şimdi), neden `open`).
- **R11'in eski testi yok:** A3.3 karakterizasyon testi yazar (`OnlineSessionsTest`: olumlu kanıt, OCR çift kare, geç kare, sayaç çift sayımı, vardiya sonu, v1 okuma, kodlama gidiş-dönüş). Rawstats'ta oturum davranışı `DeliveryRecognitionTest` ve `WoltFlowK1K5Test` içindeki birkaç testle dolaylı sınanıyordu (`CAPTURE.md` 8).

## 10. R12 — Siparişler, gün sayfaları ve kayıt biçimi (RS `OrderHistory.kt`, `RecordPages.kt`, `RecordJson.kt`, `MainActivity.kt:524-1121, 1504`)

### 10.1 Kayıt biçimi (`RecordJson`; `courier.db` payload'u ve `orders.jsonl` `record`/`offer`) — WEEM: **aynı alanlar**

- **`trip(t, withPhone)`**: `id, pkg, day (kabulün yerel günü), accepted_at, phase, offer (= offer(…)), completed_at, expected_orders, current_customer, distance_m, used_wh, recovered_wh, energy_covered_ms, energy_shared, tip, commission_pct, price_is_net, missed_offer, pickup_intent_at, finish_intent_at, latest (= observation(…)), deliver_seen_for, deliver_seen_at, session_id`; yalnız varsa: `delivered_inferred_at/by/reason`, `deliver_spinner_at/for`, `payment_method/source`, `cash_amount_azn` (null olabilir), `payment_at`, `cash_no_change`, `cash_change_azn`, `cash_checked_at`, `tip_source`, `verified_by/at`, `cash_collected_at/azn`, `extra_wait_azn` (> 0), `platform_tip_azn`, `energy_partial_ms` (> 0); `pickups[]` `{restaurant, address, ready_at, arrived_at, picked_at, arrival_source, ready_observed_at, note (redact), code, items, ready_hint_at, preparing_at, reminder_at}`; `customers[]` `{id, details, arrived_at, delivered_at, lat, lon, coordinate_source, reminder_at, details_at, deadline_clock, deadline_at?, arrival_source?, phone (yalnız withPhone)}`.
- **`details(d)`**: `name` (**düz**), `address`, `note`, `instructions`, `address_details` (`NoteFields.redact`), `building, entrance, floor, apartment, door_code, check_id` (düz); telefon **hiçbir zaman** burada değil.
- **`observation(o)`**: `pkg, at, stage, complete, source, raw (redact), actions (redact), gross, km, min_lo, min_hi, stops, quantity, restaurant, restaurant_address, customer_address, ids, customer (= details), ready_in, pickup_clock, dropoff_clock, travel_eta, arrived, ready, delivered, deliver_control, approach_min, delivery_min, demand, bonus_pct, multipliers`.
- **`offer(o)`**: `id, pkg, day (ilk görülüşün günü), at (= firstAt), battery_pct, data (= observation), lat, lon, gps_accuracy_m, gps_age_ms, scooter_battery_pct, scooter_observed_at, scooter_source, session_id, last_seen_at, decline_click_at, optional, field_source{}`; hava varsa `weather_*` (Bölüm 12); boost kaynağı varsa `boost_value, boost_unit, boost_source, boost_seen_at` (null'lar `JSONObject.NULL`); `fee_at` varsa `fee_gap_ms, fee_ocr_ms`; `notified_at`.
- Okuyucular eksik alanı eski kayıt sayar (null/0/false); `restorable(bugün, dün)`: dünün **aktif** kayıtları + bugünün hepsi, bozuk satır atlanır.
- WEEM: `day` alanı `LogTime.day` (A2.3; cihazın yerel günü, Rawstats `Store.day` ile aynı). `JSONObject` alan sırası karşılaştırmada önemsizdir (`CAPTURE.md` 6.5).

### 10.2 Gün ve sipariş özetleri (`OrderHistory`, saf)

- **`Day(date, trips)`**: `delivered` = teslim sayılanlar (çıkarımlı dahil, D3(c)); `orders` = Σ `expectedOrders` (paket teslimatta her sipariş); `gross` = Σ (ücret + `extraWaitAzn`); `net` = Σ `net`; `tips`; `unpriced` (ücretsiz teslim); `unconfirmed` = END_UNCONFIRMED ∧ çıkarım değil; `inferred` = doğrulanmamış ∧ listede doğrulanmamış; `waitMinutes`, `lateMinutes`.
- **`days(stored, live, platform, …)`**: canlı kopya kayıtlıyı ezer; platform null = "Tüm siparişler" (yalnız Wolt ve Bolt Food; başka izlenen uygulamalar hariç); kabulün gününe göre; yeniden eskiye. `PLATFORMS` = Wolt, Bolt Food.
- **`deliveredOrders(trips)`** = teslim sayılanların Σ `expectedOrders` (Bugün sayısı, D4).
- **Ortalamalar (E1, `averages`)**: yalnız ölçüldüğü siparişlerde: enerji (`energyOf`: `energyCoveredMs` > 0 ∧ `energyPartialMs` × 4 ≤ kapsanan), restoranda bekleme, müşteride bekleme (varış–teslim), toplam süre (teklif → teslim), teklif öncesi bekleme (aynı platformun önceki siparişinin bitişinden ya da çevrimiçi başlangıcından, hangisi sonraysa; başka sipariş sürüyorsa 0; önceki doğrulanmamış teslimle kapandıysa bilinmiyor). `metricRows`: ortalamaya giren her sipariş, değeri ya da neden olmadığı ("restoran ekranı görülmedi", "teslim anı görülmedi (doğrulanmamış teslim)", "harici batarya okunmayan süre %N (sınır %25)" …).
- **Durum metni** (`status`): doğrulanmamış teslim "Teslim edildi (doğrulanmadı)", listede doğrulanmışsa "Teslim edildi (listede doğrulandı)", diğer faz metni (+ AZ).
- **Liste saati** (`listTime`): Wolt = kabul anı (`acceptTime` = teklif kartının son karesi, kabul anından sonra değilse; D23), Bolt = teslim (yoksa çıkarım, yoksa kabul) — platformun kendi listesindeki saatle aynı.
- **Kart satırları:** restoranda bekleme (E2; "(GPS)"/"(platform)" kaynağı ya da "—" ve neden), boost ve sıcaklık (E3: "+%30", "1.5x", "yok", "—" ve kaynağı: Wolt planı / Wolt Earn extra listesi / teklif ekranı / ana ekran; sıcaklık "kabul anı"), enerji (`energyLine`: Wh, Wh/km (≥ 300 m ve kısmi ölçüm yokken), "bataryaya giren … Wh (harici batarya/şarj)" (≥ 0,05 Wh), ölçülen/süre dk, harici batarya okunmayan dk, "başka siparişle aynı anda"), okuma hızı (D6 `readLine`: bildirim → kart, kart → tutar, OCR), tarih etiketi ("6 Ekim 2026" / "6 oktyabr 2026").
- **`RecordPages`**: tarihler (sipariş günleri + kayıt günleri) tekil, yeniden eskiye (sola kaydırma eski güne); seçili gün yoksa bugün, o da yoksa ilk.

### 10.3 Ekranlar (Rawstats → WEEM yeri; A3.8 Compose ile yazar, içerik aynı)

| Rawstats | İçerik | WEEM |
|---|---|---|
| Ana sayfa "ŞU AN" + Bugün kartı (`renderHome`, `renderTodayPlatforms`) | rozet satırları (adlı), net kazanç, teslim, saat başına net, çevrimiçi süre, görülen teklif / kabul / ret, brüt ve bahşiş, E1 ortalamaları (dokununca liste), platform başına gün kartı (teslim, brüt, net, bekleme, hazır sonrası bekleme, fiyatsız uyarısı), doğrulanmamış teslim notu | Siparişler sekmesinin başı ("Bugün") |
| Vardiya düğmesi, ev, ekran okuma satırı | — | Harita sekmesi vardiya kartı (A2.4; E7 satırı A3.7) |
| Araçlar: "Wolt + Bolt bölünmüş ekranı aç", "<platform>: iptal gözlemi kaydet" | bölünmüş ekran (`FLAG_ACTIVITY_LAUNCH_ADJACENT`, olmazsa yönerge), iptal gözlemi (4.13) | Siparişler → Araçlar (S9: listede adı olmayan davranışlar kalır) |
| Siparişler (gün sayfaları, `renderDayPages`) | "Siparişler / Teklif puanı" seçici; gün çağrı sayfası (‹ ›, tarih seçici, "N / M gün"); başlıkta toplamlar ("N sipariş · Brüt · Net"), km ve aktif süre (o günün `shift_end` satırlarının km/dk toplamı + açık vardiya); platform seçici (Tümü / Wolt / Bolt Food); sipariş satırları (liste saati, restoran, "doğrulanmadı", E2/E3 satırı, durum, Brüt/Net ya da "Fiyat okunamadı"); "Günün tüm ayrıntıları" | Siparişler sekmesi (A3.8) |
| Gün ayrıntıları (`renderOrderDay`) | başlık kartı; **nakit kartı** (cep nakdi, toplanan nakit, CASH PENDING mutabakatı); **resmî değerler kartı** (F8.5, Bölüm 11); kaçırılan siparişler (K36); boost (gün içi: değişim, en yüksek); her sipariş tam kart | Siparişler → gün ayrıntısı |
| Sipariş kartı (`renderTrip`) | durum, E2/E3, zaman çizelgesi (teklif, kabul, restorana varış, alındı, teslim, Wolt nakit tahsilatı), süre ve bekleme (teklif öncesi, toplam, teklif süresi / gerçek / üst süreye fark, hazır sonrası), teslim süresi (K29: "N dk kalmıştı / geç", platform süresi ya da teklifin üst süresi; alışta/teslimde), hava (teklif anı), ödeme (nakit satırı, bahşiş, "Ne kadar nakit aldın?" girişi), komisyon, telefon pili, enerji, teklif ayrıntıları, okuma hızı, platform listesi satırı; düğmeler: bahşişi düzenle, müşteri kartını aç, "Teslim ettim" (düzeltme) | aynı |
| Çevrimiçi oturumlar (`renderSessions`) | oturum listesi (Bölüm 9) | Siparişler → Çevrimiçi |
| Kayıtlar (`renderRecords`) | gün sayfaları + arşiv/etkin, yedek araçları, gün kayıtları (`renderDailyRecords`) | Daha fazla → Kayıtlar (A2.3'te liste ve ZIP; gün sayfası bağlantısı A3.8) |
| Ayarlar | filo komisyonu (platform başına %, 0–99,99, paketlerin hepsine `commission:<pkg>`; "Teklif fiyatı brüt sayılır; net = brüt − komisyon + bahşiş."), rozet (`overlay`), OCR yedeği (`ocr`), müşteri kartı (`customerCard`), restoran balonu genişliği, müşteri kartı boyutu; ~~YolGo paylaşımı~~ (S7: kalktı) | Daha fazla |

- **Metinlerde ad:** "Rawstats'ın kaçırdığı siparişler", "Toplanan nakit (Rawstats)", resmî değer satırındaki "Rawstats:" → **"WEEM"**; "YolGo'dan hava gelmedi" → "hava bilgisi gelmedi" (+ AZ). Testlerdeki beklenen dizgiler buna göre değişir (Bölüm 15). A2.2'deki çökme kartı ("Rawstats" → "WEEM") ile aynı kural.

## 11. R13 — Resmî değerlerle karşılaştırma (F8.1–F8.5, K36, D3(c), D15, D23; RS `PlatformTotals.kt`, `PlatformHistory.kt`)

### 11.1 Platform kazanç listesi (K36, `PlatformHistory`)

- **Wolt** (Finances → Gross earnings → Deliveries/Daily details): satır = restoran (saatten önceki ≤ 3 satırdan ilk "aday": boş, saat, tutar, "Task offer", Bolt satırı, sayı, tarih, etiket — `finances, gross earnings, cash pending, deliveries, daily details, all deliveries, balance, cash, task offer, extra waiting time, tip` — 7+ rakamlı ya da harfsiz değil), saat (`H:MM`; teklif/kabul saati), `Task offer (N km)`, `AZN N.NN` (Task offer'dan sonra, OCR'da saatle Task offer arasında da olabilir). Altındaki "Extra waiting time" ve "Tip" (tutar etiketin önünde ya da arkasında) o satırındır (D15). Gün başlığı `11 October 2026` / `11 OCT 2026` / `11 0CT 2026`.
- **Bolt** (All deliveries): gün başlığı + hemen altında gün toplamı `10,90 ₼` + `Deliveries: N`; satır = restoran, `AA/GG, SS:DD, #KOD`, `N,NN ₼` (sonraki 2 satırda). Yıl: başlığın yılı (başlık bu güne aitse), değilse yakalama anının yılı (gelecekteki ay-gün bir önceki yıl).
- Liste karesi `onScreen`'de normal ayrıştırmadan **önce** yakalanır ve kare orada biter (4.3 adım 2). Aynı metin (özet) tekrar işlenmez.
- **Eşleme** (`match`, aynı gün, daha önce eşlenmemiş, aynı platform): sipariş kodu (Bolt; siparişin `#` kimlikleri, ≤ 12 saat) → `order_code`; ≤ `MATCH_WINDOW_MS` (2 dk) ve aynı restoran (OCR toleranslı `sameRestaurant`: 0/O, 1/l/i/|, kısaltılmış ad, eksik şube sözcüğü) → `restaurant_time`; ≤ `FEE_MATCH_WINDOW_MS` (15 dk), aynı ücret (± 0,011) ve restoran (D23: paket sipariş dakikalar sonra listelenir) → `restaurant_fee`; restoranı okunmamış tek siparişse yalnız saat → `time_only_restaurant_unknown`. En yakın saat kazanır; saat farkı `OrderHistory.listTime`'a göre.
- **Kayıt** `economics-platform-history.jsonl` (satırın gününe; `schemaVersion` 1): `source: platform_history, verified: true, platform, pkg, observed_at, capture_source, day (ekranda yoksa null), day_source (screen_header | not_on_screen | row_date_header_year | row_date_capture_year), match_day (screen | capture_day), clock, listed_at (gelecekteki saat → null), restaurant, amount, amount_text, currency: AZN, amount_basis (wolt: gross_earnings_list; bolt: unknown), distance_km, has_order_code, day_total_text, day_deliveries, extra_wait_azn, tip_azn, key, match {trip_id, by, dt_s, trip_state, trip_gross} | null, missed_by_kuryeplus (eşleşti: false; eşleşmedi: true; gelecekteki saat: null), confirms_inferred_delivery`. **Alan adları aynen** (`missed_by_kuryeplus` dahil; analiz araçları bekler). Satır anahtarı (platform, gün, saat, restoran normu, boşluksuz tutar) ile bir kez; OCR varyantı aynı teslim (`sameDelivery`: aynı ücret ve km, saat ≤ 5 dk, restoran benzer; km'siz Bolt satırı hariç) bu karede günlükte olandan fazla görünmedikçe yazılmaz. Gün başına anahtarlar, eşlenen siparişler ve satırlar dosyadan bir kez yüklenir (yeniden ziyaret/başlatmada çift yazım yok). Ham kayıtlar hiç yeniden yazılmaz.
- **Etkiler:** eşleşen satır siparişi doğrular (`listVerified`, `wolt_earnings_list` / `bolt_deliveries_list`; bellekte değilse yalnız `courier.db` satırı güncellenir); D15 ek bekleme ve uygulama içi bahşiş siparişe yazılır (`extraWaitAzn`, `platformTipAzn`; önceki karede yazılmış satır üzerinden de).
- Kart satırı: "Platform listesi (doğrulandı): <tutar> · kabul HH:MM · teslim edildi [(brüt/net belirsiz)] [· doğrulanmamış teslimi onayladı]"; kaçırılan satır "HH:MM · <restoran> · <tutar>".

### 11.2 Nakit tahsilatı ve yatırma satırları (D3(d), M17)

- Wolt Finances → Recent transactions: gün başlığı (OCR boşluğu yutabilir: `8October 2026`) · `Cash collection` (ya da `Cash offload`) · `[-]AZN N.NN` · restoran (uzun ad tutarın etrafına sarılabilir) · `HH:MM` (tutar saatten sonra da gelebilir); ilk başlıktan önceki satırlar atlanır. Yalnız bu alanlar okunur.
- **Eşleme** (`cashMatch`): Wolt siparişi, tahsilatı henüz yok, kabulü satır saatinden en çok 1 dk sonra ve en çok 3 saat önce, alış yapılmış ya da teslim sayılmış, aynı restoran; kayıtlı bitişi saate en yakın olan. Sonuç `core.cashCollected` (3.11).

### 11.3 Resmî toplamlar (F8.1–F8.3, `PlatformTotals`)

- Kaynaklar: Wolt Stats (`wolt_stats`: dönem `CAPTURE.md` 2.7 `statsPeriod`; teslim, kazanç, bahşişsiz kazanç, bahşiş, mesafe), Wolt Daily details (`wolt_daily_details`: `GG/AA/YYYY` + `AZN N.NN`, aynı satırda ya da yanında; OCR tüm tutarları tarihlerinden önce koyabilir; liste üstündeki dönem toplamı gün değildir), Wolt nakit tahsilatı (`wolt_cash_collection`: güne göre tekil satırların toplamı), Bolt All deliveries (`bolt_all_deliveries`: gün başlığı, altındaki toplam, sonraki satır/başlıktan önceki `Deliveries: N`; `amount_basis: unknown`).
- **Kayıt** `economics-platform-totals.jsonl` (gün dönemi o güne, hafta/ay görüldüğü güne): `{schemaVersion: 1, kind: platform_total, platform, period, source_screen, observedAtMs, deliveries?, earnings_azn?, earnings_no_tips_azn?, tips_azn?, distance_km?, cash_azn?, amount_basis?}`; (platform, dönem, kaynak) için son yazılandan farklıysa. Okuma: anahtar başına en yeni gözlem.

### 11.4 Karşılaştırma (F8.4) ve gün kartı (F8.5)

- WEEM tarafı (`ours`): teslim (çıkarımlı dahil), brüt (ücret + ek bekleme), net, uygulama içi bahşiş, nakit sipariş tutarı.
- Yalnız **gün** dönemleri: Wolt Stats → teslim sayısı, kazanç (bahşişsiz) = brüt, bahşiş = uygulama içi bahşiş, mesafe (o günün vardiya km'si; fark ≤ %10 ya da km bilinmiyorsa tamam; "Wolt mesafesi yalnız çevrimiçi süreyi sayar"); Wolt Daily → kazanç = brüt; Wolt nakit → nakit; Bolt → teslim sayısı, kazanç = brüt **ya da** net (hangisi tutarsa o gösterilir). Para eşitliği < 0,005.
- Kart: "Resmî değerler" (+ " · N fark" kırmızı); satır "<etiket> — <Wolt|Bolt>: <resmî> · WEEM: <bizim|—>" (etiketler: Teslim; Kazanç (bahşişsiz, Stats); Gün toplamı (Finances); Gün toplamı; Bahşiş (uygulama içi, Stats); Nakit toplama; Mesafe); değer yoksa "Wolt Stats/Finances'e (Bolt: All deliveries) girince dolar."; fark varsa ücreti farklı Wolt siparişleri ("HH:MM · <restoran> · Wolt: X ₼ · WEEM: Y ₼").

## 12. R15 — Teklif anının havası (K28; RS `OfferWeather.kt`, `WeatherFeedReader.kt`)

- Rawstats: YolGo'nun cihaz içi hava akışı (`content://az.duzyol.app.weather/weather/current`, tek satır `json`; `schemaVersion` 1, `source` `yolgo_open_meteo`, `fetched_at`, `temp_c, feels_c, rain_mm, rain_pct, wind_kmh, gust_kmh, code`), en çok 2 dk'da bir arka planda okunan önbellek; teklif anında önbellekteki değer ≤ `MAX_AGE_MS` (90 dk) ise teklife yazılır, değilse yazılmaz ("gözlenmedi"; asla eski değer).
- **WEEM:** kaynak `WeatherPort.current()` (≤ 90 dk taze `WeatherNow`, Y10 `:map`; A6'ya kadar `NONE` → null). Teklife yazılan alanlar **aynı**: `weather_source`, `weather_fetched_at`, `weather_temp_c`, `weather_feels_c`, `weather_rain_mm`, `weather_rain_pct`, `weather_wind_kmh`, `weather_gust_kmh`, `weather_code` (null'lar `JSONObject.NULL`); `weather_source` değeri **`yolgo_open_meteo` korunur** (kayıt değeri: eski ve yeni kayıtlar aynı okunur, `fromRecord` yalnız bunu tanır, analiz araçları ve `EnergySegments` bunu bekler; kaynak yine Open-Meteo, WEEM'in YolGo kısmı çeker). Önbellek ve içerik sağlayıcı okuyucusu yazılmaz.
- Kartta: "Hava (teklif anı)": sıcaklık, his, yağış %, yağış mm, rüzgâr km/s, hamle; WMO kodu grubu (`kind`: ≥ 95 gök gürültülü, 71–77/85/86 karlı, ≥ 51 yağmurlu, 45/48 sisli, ≥ 2 bulutlu, diğer açık + AZ); yoksa "yok (son 90 dk'da hava bilgisi gelmedi)".

## 13. Arayüzler ve bağlantılar (plan 4.3–4.4; A2.5 `Ports.kt`)

### 13.1 `:capture` → `:orders`

- `CaptureSink` (`CAPTURE.md` 7.1): `onScreen` → 4.3, `onClick` → 4.13/3.9, `onNotification` → 13.6, `onNavigation` → navigasyon özeti (`CAPTURE.md` 4.2), `awaitingPhone`/`onDialer` → 4.11. `CaptureHost` → hatırlatıcı ve rozet pencereleri (Bölüm 5–6), tik (4.6).
- Gözlem tipleri (`CourierObservation`, `ScreenStage`, `DeliveryDetails`, `BoostSlot`) ve yardımcılar (`NoteFields`, `NameMatch`, `OwnLines`, `Dialer`, `CourierParser.norm/isAccept/isDecline/isCollect/isDeliver/isCall/isArrive`, `CourierParser.WOLT`) `:capture`'tan.

### 13.2 `:orders` → başkaları: `OrderEvents` (üretici `:orders`)

- **`offers: StateFlow<List<OfferSummary>>`** — Rawstats feed v2 tekliflerinin karşılığı (RS `OfferFeed.kt:70-116`, `EconomicsLogs.kt:75-89, 140-153`): çekirdek çalışıyorken, `now − lastSeenAt ≤ MAX_UNSEEN_MS` (90 sn; K21: hayalet teklif yok) ve son görülüş süreç başlangıcından sonra olan teklifler, en çok 8, platformu bilinen (`wolt`/`bolt`). Alanlar: `offerKey` = teklif id; `platform`; `firstSeenAtMs`; `lastSeenAtMs` = max(son, ilk); `visibleNow` = son görülüş ≤ 5 sn önce; `expiresAtMs` = ilk görülüş + belgeli pencere (60 sn; doğrulanmamış üst sınır) ya da null; `displayedFeeAzn` = ücret; `platformDistanceKm` = km; `restaurant` (≤ 200); `dropoffKnown` = müşteri adresi okundu (Bolt kartı göstermez, K8); `dropoffAddress` = müşteri adres metni (≤ 1 000; S5: Receiver'a gider); `shown` = {`minLo, minHi, km, stops, readyInMin, approachMin, deliveryMin`, `totalMin` (Bolt'ta `minLo = minHi` ise o), `multiplier` (tek çarpan), `boostPct` (= aktif `bonusPct`)}; `pickupZone`/`dropoffZone`, `score`, `netAzn` → `OfferAnnotator` (`:economics`; yoksa `unknown`/null); `dropoffLat/Lon` → çözülmüşse (teklif anında yok; null). Değişince (veri sınıfı eşitliği) yayınlanır: her kare, dokunuş ve tikten sonra.
- **`stages: StateFlow<TripStages>`** (E8; RS `TripStages.kt`): en çok 3 açık (kabul sırasına göre son 3) + son 10 dk'da biten en çok 3 sipariş; her biri `tripId, platformPackage, active, stage` (açıkken `to_pickup | at_pickup | to_dropoff | at_dropoff`, `Platforms.stage(faz)`), `outcome` (`delivered`, `delivered_unverified` (çıkarım), `cancelled` (hiç alış yok), `unconfirmed`), `endedAtMs` (kaydın kendi anı, yoksa kapanış olayının anı), `events[{stage, atMs, pickup?, source? gps|screen}]` — kabul (`to_pickup`, alış 0), her alışın varışı (`at_pickup`) ve alınışı (sonraki alışa `to_pickup` ya da `to_dropoff`), müşteriler teslim sırasıyla (`at_dropoff` varış, sonraki müşteriye `to_dropoff`); zamanı görülmemiş aşama yazılmaz. **Müşteri verisi yok.** WEEM tipi `logic/core` `TripStages` (A2.5).
- **`day: StateFlow<DaySummary?>`** (yeni; plan 4.4): bugünün `OrderHistory.Day`'inden platform başına `orders`, `grossAzn`, `netAzn`, `onlineMin` (bugünkü oturumlar) ve `cashAzn` (`CashPayment.dayTotal`); bir sipariş bitince, en çok dakikada bir.
- **`busy()`**: aktif sipariş var — `:orders` bunu `stages` akışından değil doğrudan çekirdekten verir (akışın gecikmesi ev kuralını etkilemesin; RS `hasOpenOrder`).
- Tüketiciler: `:map` (görev kartları Y20, veri seti Y19, istatistik Y17), `:economics` (R4, N4), `:link` (Receiver'a, plan 4.5). Rawstats'ın içerik sağlayıcıları (feed v1/v2, `stages`, `changedAtMs`, değişim duyurusu `FeedChanges`/`FeedNotify`, imza kapısı) **yazılmaz** (S7).

### 13.3 `:economics` ↔ `:orders`

- `:economics` → `:orders` bağımlıdır (kayıt tipleri). `:orders` iki arayüz tanımlar, `:economics` uygular, `:app` bağlar: **`OrderObserver`** (4.14: ekonomi kayıtlarının bütün kancaları) ve **`OfferAnnotator`** (teklif bölgeleri, puan, net). İkisinin `NONE`'u veri uydurmaz.
- `:orders`'ın bugün `:economics`'ten aldığı saf yardımcılar `:orders`'a taşınır, `:economics` onları buradan kullanır: `EconomicsLogs.platformOf` → `Platforms.of(pkg)` (`wolt` / `bolt`(+`mtakso`) / null), `EconomicsLogs.stage` → `Platforms.stage(faz)`, `EconomicsV2.PRIORS`'tan iki değer → `OfferWindows` (2.13). `OfferGeo` (bölge) ve `courierState` `:economics`'te kalır.
- E4 teklif puanı ekranı ("Teklif puanı" seçicisi, `rateOffer`, puan/değerlendirme dosyaları) `:economics`'indir (A4.1); Siparişler sekmesindeki yeri A3.8'de boş bırakılır (sekme seçicisi A4.1'de eklenir).

### 13.4 `:scooter` (`ScooterBridge`, A5)

- `orderAccepted(tripId, an)` kabulde, `orderDone(tripId, an)` siparişin sonunda (4.4); `batteryPct()` teklif anında (≤ 15 sn taze); enerji adımları `core.addEnergy` (scooter'ın 1 Hz örneği; Rawstats `ScooterLink` → `OrderTracker.scooterEnergy`) — A5.3 bağlar. Sipariş başına enerji satırları (`economics-energy.jsonl`, R14) `:economics`'in `OrderObserver`'ındadır. A5'e kadar `NONE`: pil ve enerji yok.

### 13.5 `:map` (`NavPort`, `WeatherPort`)

- `NavPort.route` → 4.9 (YolGo nav yükünün yerine). `NavPort.open(NavTarget)` `:orders`'tan çağrılmaz (Rawstats'ta sipariş tarafı navigasyon açmıyordu; görev kartındaki "Rota Oluştur" `:map`'in kendisi, S9).
- `WeatherPort.current()` → Bölüm 12.

### 13.6 `:core` (`NotifyPort`, `ShiftListeners`, depolama)

- Bildirimler `NotifyPort.post(Notify(…))` ile: 12 (`DELIVERY_NOTE`), 4901 (`POCKET_CASH`), `cashTip(tripId)` (10000–14095). Kanal kimlikleri aynı (`delivery_notes`, `pocket_cash`, `cash_tips`).
- Vardiya kancaları `ShiftListeners` (4.2, kayıt sırası dahil).
- **Bildirim ipucu** (`OrderTracker.notification`, RS `:501-509`): kayıt izni; `events.jsonl` `notification_hint {pkg, text (redact), observed_at}`; platformun son bildirim anı (ileri; D6 `notifiedAt` için); `core.readinessNotice` (3.11). Bildirim kabul, ücret ya da ret kanıtı **değildir**.
- Depolama: gün satırları `LogStore.append` (A2.3), `courier.db` `CourierDb` (A2.3; şema 1), tercihler `kuryelog`.

### 13.7 R19'un kalanı (izinler ve pil; RS `MainActivity.kt:1186-1210`, `AutoStartNotice.kt`)

- **İzinler kartı** (`:app`, G5 ilk açılış ve Daha fazla): erişilebilirlik (ayar `ENABLED_ACCESSIBILITY_SERVICES`'te `"<paket>/"`), bildirim dinleyici (`enabled_notification_listeners`'ta `"<paket>/"`; "Bildirim okuma izni, navigasyon bildirimindeki kalan rota mesafesini okumak için de kullanılır (kaydedilmez)."), konum, arka plan konumu (ev ayarlıysa "her zaman" izni açıklaması), Android 12+'da scooter seçiliyse Bluetooth; eksik varsa başlık kırmızı "! n/m" ve kart açık gelir. Pil optimizasyonu yalnız bilgi satırı ("isteğe bağlı"; `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), ayrı bildirim yok; "Kısıtlı ayarlar" açılır bölümü (uygulama bilgisi, tüm uygulamalar). Metin anahtarları `battery_free`, `battery_restricted`, `battery_help`, `restricted_title`, `restricted_help`.
- Otomatik başlatma bildirimleri: 5101 (ev) ve 5103 (vardiya kesildi) A2.4'te yazıldı; 5102 (scooter) A5. `AutoStartNoticeTest` (1) A2.4'te karşılandıysa çevrilmez, karşılanmadıysa `:core`'a (Bölüm 15).
- E7 ekran okuma uyarısı: `CAPTURE.md` 5.

## 14. Veri, gizlilik ve gölge mod

### 14.1 `:orders`'ın yazdıkları (ad, alan, anlam aynı; `VERI_SOZLESMESI` 5.1, 6, 7.1)

| Yer | İçerik | Bölüm |
|---|---|---|
| `orders.jsonl` | olay satırları; `record_snapshot` | 4.4, 4.5 |
| `courier.db` `trips`, `offers` (`result`), `session` (`k='active'`) | `RecordJson` payload'u (telefon yalnız `trips`'te) | 4.5, 10.1 |
| `online-sessions.jsonl` (v2) | kapanan oturumlar | 9.3 |
| `platforms.jsonl` | bekleme/çevrimdışı/çarpan durumu | 4.3 adım 19 |
| `economics-boosts.jsonl` | `change`, `slot`, `plan` (null'lar açık) | 9.1 |
| `economics-cash-tips.jsonl`, `economics-pocket-cash.jsonl`, `economics-cash-pending.jsonl` | K32, M15, M17 | 7, 8 |
| `economics-platform-totals.jsonl`, `economics-platform-history.jsonl` | F8.3, K36 | 11 |
| `events.jsonl` | `notification_hint`, `customer_coordinate_unavailable`, `yolgo_venue`, `cancellation_user_reported` | 13.6, 4.10, 4.9, 4.13 |
| `errors.jsonl` kaynakları | `database`, `online_sessions`, `shift_orders`, `gps_orders`, `shift_start`, `shift_end` (+ ekonomi: `economics_idle`, `open_waits`, `shadow_verdict`, `offer_score`) | 4 |
| `kuryelog` tercihleri | `onlineSessions.v1`, `cashPending.azn`/`.at`, `commission`, `commission:<pkg>`, `customerCard`, `customerCardSizePct`, `restaurantBubbleWidthPct`, `overlay`, `bx`, `by`, `structuredMigration07` (+ ekonomi: `economicsIdle.v1`, `openWaits.v1`, `economics.vehicle/zone/appMode`) | — |

- Ekonomi dosyaları (`economics-offers/legs/idle/penalties/waits/exposure/offer-scores/offer-ratings/energy.jsonl`) `OrderObserver` ile `:economics`'te (A4.1); A3'te yazılmaz.
- Gün klasörü: kaydın yerel günü; istisnalar adlı gün klasörüne yazar: cep nakdi ve CASH PENDING vardiya satırları **vardiya başının günü**, bahşiş cevabı **siparişin kabul günü**, yatırma ve platform satırları **satırın kendi günü**, gün dönemi toplamları o güne.

### 14.2 Gizlilik

| Veri | Nerede | Nerede değil |
|---|---|---|
| Müşteri **telefonu** | `courier.db` `trips` (`withPhone`), bellek, kart/balon "Ara" düğmesi, bildirim 12 büyük metni | JSONL, `record_snapshot`, yedek/ZIP, dışa aktarma, Receiver |
| Müşteri **adı** | `courier.db`, `orders.jsonl` (`record.customers[].details.name`, `latest.customer.name`), kart başlığı, bildirim 12 başlığı | rozet (K35), `TripStages`, `OfferSummary`, dışa aktarma (gizlenir), Receiver |
| Adres, not, talimat, adres detayı | `courier.db` ve `orders.jsonl`'de **`redact`'li** (telefon `[tel]`) | — |
| Müşteri koordinatı, adres metni | `courier.db`; `OfferSummary.dropoffAddress` | — (S5: Receiver'a **gider**) |
| Kurye konumu | teklif ve boost satırları, `loc` alanları (telefonda) | dışa aktarma (gizlenir), Receiver'a ham satır olarak |

- 2 telefon modunda Receiver'a giden yalnız `OrderEvents` mesajlarıdır (`OfferSummary`, `TripStages`, `DaySummary`); balon, kart ve bildirim 12 yalnız Provider'da.

### 14.3 Gölge mod (A3.9) — karşılaştırma kuralları

- Sipariş ve teklif kimlikleri iki uygulamada **farklıdır** (`trip-<kabul anı>-<sıra>`: kare anı milisaniyeyle değişir, sıra süreç başına sıfırdan). Eşleme: platform + restoran (`sameRestaurant`) + kabul anı ± 60 sn (teklif için ilk görülüş ± 60 sn ve ücret).
- **Sert fark** (sıfır olmalı): gün başına sipariş sayısı, teslim/çıkarım/doğrulanamayan sonuçları, brüt ve net toplamları, sipariş ücreti, ödeme türü, doğrulama (`verifiedBy`), teklif sonuçlarının türü (kabul/ret/zaman aşımı), boost/plan satırlarının kümesi, platform toplamları ve liste satırları (anahtarla), CASH PENDING değerleri.
- **Yumuşak fark** (raporda süre farkı olarak): olay anları, varış anları (D9 kalışı fix'lere bağlı), `feeAt`/`feeGapMs`/`feeOcrMs`, OCR tempo ölçüleri, `record_snapshot` sıklığı.
- **Beklenen fark** (sayılmaz): ekonomi dosyaları (A4.1'e kadar yalnız Rawstats'ta), bildirim numaraları (5104, 10000+), `click.text` `redact`'i, ad metinleri (WEEM), YolGo kökenli alanlar (WEEM'in kendi navigasyonu A6'ya kadar yok: `nav_yolgo` kaynakları, `yolgo_venue`, rota tetikli müşteri kartı), hava (A6'ya kadar WEEM'de yok), scooter pili/enerjisi (A5'e kadar yok).
- Gölge modda iki uygulama da soru sorar (nakit bahşiş, cep nakdi) ve pencere çizer (rozet, balonlar): Bölüm 16.

## 15. Test eşlemesi (A3.2, A3.3)

Kaynak: `TEST_ENVANTERI` `:orders` hedefli **52 dosya, 325 test** (Rawstats; envanterdeki "53 / 331" bir YolGo Dart dosyasını da sayıyordu, aşağıda) + `CAPTURE.md` 8'den gelen **100** sipariş akışı testi. Sınıflandırma test gövdeleri ve kullandıkları sınıf/yardımcılarla yapıldı (A3.1 betiği), karışık dosyalar elle denetlendi. "Kaynak taraması" = eski testin Android kaynağında dizgi araması; WEEM'de aynı denetim WEEM dosyalarına yeniden yazılır ve Android kodu gelene (A3.7/A3.8) kadar kırmızı kalır.

### 15.1 A3.2 — sipariş akışı (`logic/orders`)

| Dosya | Test | Yer | Not |
|---|---:|---|---|
| `CourierFlowTest`, `DeliveryRecognitionTest`, `WoltFlowK1K5Test`, `SahaTekrarTest`, `WoltDeliverySliderTest`, `WoltSliderSpinnerK33Test`, `WoltOfferFeeK37Test`, `OcrPaceD6Test`, `BoltBubbleR2Test`, `ParserEconomicsTest`, `CashScreenM16Test` (orders payı) | 100 | orders | `CAPTURE.md` 8 (dosya başına sayılar); saha kareleri `logic/capture` `testFixtures`'tan |
| `CoreLifecycleV2Test` | 23 | orders 15 · **economics 8** | economics: `waitWhileTheOtherAppIsBusyIsFlagged`, `bannerPopupHourAndDemandStayInsideOneSpell`, `aLongUnreadableStretchStillEndsTheSpell`, `ocrOfferAtTheSameInstantAsAnUnreadableFrameIsTheEvent`, `offlineScreenEndsTheWaitAsWentOffline`, `vehicleChangeStillSplits`, `midnightSpellIsFiledUnderItsStartDay`, `segmentsSurviveProcessDeath` (`WaitObservation`, A4.1) |
| `CustomerStageK34Test`, `…ClosedCardTest`, `…LateDeliveryTest`, `…RoutingTest` | 10, 10, 7, 5 | orders | |
| `CustomerStageK34RecordTest` | 10 | orders 6 · economics 1 · core 2 · uyarlanır 1 | yardımcı `day()` ekonomi kaydedicisini sürer; orders'ta kaydedicisiz kopya. economics: `legsRowIsNeverADelivery`; core (R18, A4.3): `analysisExportKeepsTheFlagAndDropsTheCustomer`, `analysisExportKeepsTheOrderEvent`; uyarlanır: `feedV2ShowsOneOrderAtTheCustomer` → `OrderEvents.offers/stages` |
| `DeliveryCountD3Test` | 9 | orders | |
| `VenueArrivalD9Test`, `VenueNearM18Test`, `CustomerNearM19Test`, `ArrivalDistanceM20Test` | 11, 5, 5, 4 | orders (D9'dan 1 economics: `legsAveragesAndRecordsKeepTheGpsArrival`; M20'den 2 kaynak taraması) | |
| `StuckCustomerM22Test` | 8 | orders | |
| `RestaurantWaitE2Test` | 6 | orders 5 · tarama 1 (`cardAndListShowIt`) | |
| `ReadinessE15Test` | 5 | orders 4 · tarama 1 (`restaurantNotificationUsesTheCounterAndFollowsIt`) | |
| `WoltSameCustomerR1Test` | 5 | orders | |
| `CustomerMatchK21Test` | 5 | orders | `NameMatch` `:capture`'tan |
| `DeliveryDeadlineK29Test` | 4 | orders | |
| `TripStagesE8Test` | 6 | orders 4 · economics 2 | economics: `activeOrderCarriesEveryStageWithItsRealTime`, `attachingKeepsTheClosedCourierStateSchema` (`courierState`); orders testleri `logic/core` `TripStages` tipine yazılır |
| `YolGoNavE8Test` | 13 | uyarlanır 10 · A2.5'te karşılandı 2 · çevrilmez 1 | `pickupOf`, `ScreenTrail`, koordinat önceliği, varış ve düzeltme → `RouteStatus.Target` ile orders; yük okuma (`payloadIsRead…`, `anythingElse…`) `MessagesTest` `RouteStatus.fromNavFeed`'te; `pathsMatchYolGoProvider` çevrilmez (sağlayıcı yok) |
| `WaitObservationProcessDeathTest` | 5 | **economics** (A4.1) | bekleme gözlemi `:economics`'in |
| `RecordJsonTest` | 6 | orders | `CORE.md` 6.1'den |

### 15.2 A3.3 — para, platform, rozet, balon, gün sayfası (`logic/orders`)

| Dosya | Test | Yer |
|---|---:|---|
| `CashOrderK31K32Test` | 16 | orders |
| `TipRecordF48Test` | 5 | orders |
| `TipNotificationF46Test` | 4 | **tarama 4** (mesaj stili + `RemoteInput` + hızlı düğmeler + pencere; `tip_quick` düzeni) — A3.7 |
| `PocketCashM15Test`, `CashPendingM17Test`, `CashReconcileM17Test` | 4, 5, 5 | orders (M15/M17'den birer tarama: "günlük yedeğe ve dışa aktarmaya girer" → `BackupFiles.logNames`/`AnalysisExport` denetimi, `:core`) |
| `BoostLogK27Test`, `BoostListM21Test` | 7, 4 | orders |
| `BoostTempE3Test` | 5 | orders 4 · tarama 1 (`cardTopShowsIt`); beklenen metin "YolGo'dan hava gelmedi" → "hava bilgisi gelmedi" |
| `OfferWeatherK28Test` | 2 | orders (`WeatherNow` → `OfferWeather`, `weather_source` değeri aynı) |
| `PlatformHistoryK36Test`, `PlatformListD15Test` | 10, 5 | orders |
| `PlatformTotalsF82Test`, `F83Test`, `F84Test`, `F85Test` | 6, 5, 7, 3 | orders (F83'ten 1 tarama: yedek/dışa aktarma; F85'ten 1 tarama: gün sayfasında kart — A3.8); F85 satırında "Rawstats:" → "WEEM:" |
| `OverlayBadgeK35Test` | 4 | orders 3 · tarama 1 (`overlayUsesTheNamelessLinesAndHomeKeepsNames`) |
| `DualOfferStatusK30Test`, `OfferNetLineTest` | 3, 6 | orders (OfferNet'ten 2 tarama: dışa aktarma düğmesinin yeri (A4.3), `acc_desc` sözü (A3.7)) |
| `RestaurantBubbleE14Test` | 6 | orders 4 · tarama 2 |
| `CustomerCardE16Test` | 6 | uyarlanır 3 (`RouteStatus`; metin "(WEEM)") · orders 2 · tarama 1 (`oldNotificationTriggerIsGone`) |
| `CustomerBubbleF72Test` | 7 | orders |
| `CustomerBubbleViewF73Test` | 3 | **tarama 3** (A3.7) |
| `NoteFieldsF71Test` | 6 | **capture** (`NoteFields` `:capture`'ta) |
| `PhoneK25Test` | 3 | orders |
| `AverageListE1Test` | 6 | orders 5 · tarama 1 |
| `OrderHistoryTest`, `TodayAveragesTest`, `RecordPagesTest` | 9, 2, 2 | orders |
| `OfferFeedV2Test` | 6 | uyarlanır 4 (`OrderEvents.offers`: 90 sn hayalet kuralı, yeniden başlatma öncesi teklif, en çok 8, v2 alan eşlemesi) · çevrilmez 2 (`v1IsByteIdenticalForOffersOnScreen`, `e8ChangeKeyOverTheRealPayloads`: feed ve değişim anahtarı yok) |
| `AutoStartNoticeTest` | 1 | **core** tarama (A2.4 `Notices`/`HomeWatch`/`ShiftService`'e; scooter kısmı A5) |

### 15.3 Toplamlar ve öbürleri

- 52 dosya, 325 test: **orders 253** (+ capture'dan gelen 100 = 353), kaynak taraması 24 (Android `:orders` 18, `:core` 4 — `AutoStartNoticeTest` ve üç "yedeğe/dışa aktarmaya girer" —, `:capture` 1 — `acc_desc` —, A4.3 1), economics 17 (`CoreLifecycleV2Test` 8, `WaitObservationProcessDeathTest` 5, `TripStagesE8Test` 2, D9 1, K34 kayıt 1), core (R18) 2, uyarlanır 18, çevrilmez 3 (`pathsMatchYolGoProvider`, feed v1 ve değişim anahtarı), A2.5'te karşılanan 2, capture 6 (`NoteFieldsF71Test`). (Sayılar betik çıktısıdır; A3.2/A3.3 test gövdesini okuyunca küçük kaymalar `TEST_ENVANTERI`'ne işlenir.)
- **YolGo Dart `offer_feed_versions_test` (6):** uygulamalar arası okuyucunun v2→v1 geri düşüşü, imza reddi, paylaşım kapalı → **çevrilmez** (tek uygulama, S7); "v2 sürüm, oturum, boyut, teklif biçimi ve sınırı" denetimi 2 telefon mesajlarının çözümünde (`:link`, A8) karşılık bulur.
- **Eski testi olmayanlar için yeni testler** (A3.3/A3.5; karakterizasyon, beklenen = Rawstats kodunun çıktısı): `OnlineSessionsTest` (9.3), `OrderFlowOrderTest` (4.3 kare sırası: liste karesi erken biter, teklif anı eklentileri yalnız `offer` olayında, olay işleme alanları), `PersistTest` (değişen satır, `record_snapshot` kuralı, telefon yalnız `courier.db`), `RebuildRecordsTest` (4.12), `OfferSummaryFromOrdersTest` (13.2), `ShiftHooksOrderTest` (4.2 sırası), `OrderObserverCallsTest` (4.14: kanca noktaları ve sırası, `NONE` ile dosya yazılmaz), `PlatformsTest` (`Platforms.of/stage`), `OfferWindowsTest` (`:economics` A4.1'de `PRIORS` eşitliği).

## 16. Açık noktalar, riskler ve bilerek farklar

**Kullanıcıya sorulacaklar (A3.1'i bekletmez; ilgili görevden önce sorulur):**
1. **Bildirim 12'de müşteri telefonu** (5.3): bugün büyük metin `display()` ile telefonu da gösterir; bildirim yansıtma (saat, bilgisayar, öteki telefon) varsa numara oraya gider. Seçenek: aynen kalsın (varsayılan, davranış aynı) ya da bildirimden telefon satırı çıksın (kartta kalır). — A3.7'den önce.
2. **Gölge modda çift soru ve pencere** (14.3): `.dev` ile Rawstats aynı anda nakit bahşiş ve cep nakdi sorar, iki rozet ve iki balon çizer. Öneri: `.dev`'de "gölge mod" anahtarı (soruları ve pencereleri kapatır, kaydı sürdürür); bahşiş farkları beklenen fark sayılır. — A3.9'dan önce.
3. (`CAPTURE.md` 9) ML Kit ve ağ.

**Varsayılan olarak seçilenler (bilerek farklar; gölge mod "beklenen fark" sayar):**
- Nakit bahşiş bildirimleri 4000–8095 → **10000–14095**; ekran okuma uyarısı 5103 → 5104 (`CORE.md` 3.3).
- Kullanıcı metninde uygulama adı **WEEM** ("Rawstats" ve "KuryePlus" yerine); kaynak olarak "(YolGo)" → "(WEEM)"; "YolGo'dan hava gelmedi" → "hava bilgisi gelmedi". Testlerin beklenen dizgileri buna göre (15.2).
- YolGo ile konuşan her yol (feed v1/v2, `stages`, değişim duyurusu, YolGo nav ve hava sağlayıcı okuyucuları, YolGo paylaşım anahtarı) yazılmaz; yerine `OrderEvents`, `NavPort`, `WeatherPort` (S7).
- `weather_source` kayıt değeri `yolgo_open_meteo` **korunur**; `nav_yolgo`, `gps_dwell_yolgo_target`, `gps_near_yolgo_target`, `yolgo_venue` adları korunur (kayıt dizgileri).
- `OfferWindows` (ret penceresi 60 dk, teklif penceresi 60 sn) ve `Platforms.of/stage` `:orders`'a taşındı; `:economics` buradan kullanır.
- `busy()` doğrudan çekirdekten.
- Ekonomi kayıtları A4.1'e kadar yazılmaz (`OrderObserver.NONE`).

**Riskler (doğrulanmadı):**
4. `OrderTracker`'ın saf `OrderFlow` + Android'e bölünmesi en büyük taşıma riski: sıra (4.3) ve kalıcılık zamanlaması (4.5) testlerle kilitlenmeli; `SahaTekrarTest` uçtan uca güvencedir.
5. `:economics`'in kanca sırası (4.14) A4.1'de Rawstats'la birebir olmalı; aksi hâlde ekonomi satırlarının içeriği (önceki bekleyen teklifler, açık oturumlar) değişir.
6. Android `Geocoder` cihazda ağ ve Google hizmeti ister; yoksa müşteri/restoran koordinatı gelmez (Rawstats'ta da). E14/E16 ve D9/M19 buna bağlı; WEEM'in kendi rotası (A6) gelince rota tetikleri devreye girer.
7. Wolt/Bolt metinlerine bağlı kurallar (saha 11–12 Ekim, Wolt 2.202.6); platform güncellemesi gölge modda yakalanır.
8. JDK 21 `\b` farkı `:orders` desenlerinde de taranır (`CAPTURE.md` 7.5; ör. `PlatformHistory`, `CashPayment`, `CashPending` açık sınıf kullanıyor, `DeliveryCore.readinessNotice` ASCII).

**Ham notlardaki düzeltmeler (`bulgular/R_orders.md`):** bildirim 12 büyük metninde telefonun olduğu doğrulandı (`DeliveryReminder.kt:301-310` → `DeliveryDetails.display`); restoran varışı (D9/M18) **yalnız Wolt** siparişlerinde, müşteri varışı (M19) her platformda; M18/M20 navigasyon varışının nedeni de `nav_*` olabilir; R9 "çağıran yer doğrulanacak" kapandı (vardiya başı `GpsService.kt:89-90`, sonu `:283`); `economics-*.jsonl` satırlarında `t` yok (`observedAtMs`/`at`); boost satırlarında null alanlar `JSONObject.NULL` ile yazılır.

### 16.1 A3.5 taşıma kaydı (17 Ekim 2026; dal `ccr-e90bca59-fxabvl`)

Satır satır kopyalanan (değişiklik yalnız paket satırı ve importlar): `DeliveryCore`, `Readiness`, `DeliveryDeadline`, `ArrivalDistance`, `VenueArrival`, `LegacyRecords`, `RecordJson`, `PlatformHistory`, `CashTips` (`CashPayment` dahil), `OnlineSessions`, `OfferWeather`, `BoostLog`, `OrderHistory`. Farklar:

- WEEM: `DeliveryCore.DECLINE_WINDOW_MS` ve `offerWindowMs` → `OfferWindows` (60 dk; Wolt/Bolt 60 000 ms; 2.13). Değer ve platform seçimi aynı.
- WEEM: `EconomicsLogs.platformOf` / `stage` → `Platforms.of` / `stage` (13.3); `PlatformHistory`'deki dört çağrı buna döndü.
- WEEM: `Store.parseTs` / `Store.day` → `logic/core` `LogTime.parseTs` / `LogTime.day` (`LegacyRecords`, `RecordJson`; biçim ve varsayılan saat dilimi aynı).
- WEEM: `BoostLog.observe` — `b.window`/`b.from` yerel değişkene alındı (tip `:capture`'ta, Kotlin akıllı dönüşüm yapmıyor); davranış aynı.
- WEEM: `OrderHistory.boostTempLine` — "YolGo'dan hava gelmedi" → "hava bilgisi gelmedi" / AZ "hava məlumatı gəlmədi" (16; AZ metni kullanıcı onayı 17 Ekim, plan S19).
- WEEM: `OverlayText` — söz dağarcığı ve tanıma `logic/capture` `OwnLines`'ta (CAPTURE.md 2.10), burada yalnız biçimlendiriciler; `TripPhase` → `OwnLines.PHASES` anahtarı enum adı. `OverlayTextOwnLinesTest` her biçimlenen satırın `OwnLines.isOwn` olduğunu denetler.
- WEEM: `TripStages.feed/trip/events/outcome/endedAt` → `OrderStages.of/trip/…`, JSON yerine `logic/core` `TripStages` tipi; `attach` (feed v2'ye ekleme) yok (S7). Sınırlar `TripStages.MAX_ACTIVE/MAX_ENDED/ENDED_KEEP_MS` (aynı değerler).
- WEEM: `YolGoNav.pickupOf/preferred` + `ScreenTrail` → `NavBinding` (aynı kural); hedef `RouteStatus.Target` (`origin`/`kind` enum), yük okuyucusu (`parse`, URI, imza) yok (4.9). `yolgo_venue`, `nav_yolgo`, `gps_*_yolgo_target` dizgileri korunur.
- WEEM: `OfferFeedContract.snapshotV2` + `EconomicsLogs.feedOfferV2/shown` → `OfferSummaries` (`OfferSummary` listesi; 13.2). `restartedAtMs` global değil, parametre; bölge, puan, net `:economics`'in (UNKNOWN/null); `feeBasis`, `producer`, `courierState`, `duringTripId`, `optional`, `parse.feeSource` `OfferSummary`'de yok.
- WEEM: `OfferWeather.from(WeatherNow, now)` eklendi (kaynak `WeatherPort`, 12); 90 dk dışı yazılmaz, `weather_source` = `yolgo_open_meteo`.
- WEEM: `OrderTracker` → saf `OrderFlow` (4.14). Dış dünya `OrderFlow.Env`: `LogStore` (gün dosyaları, `events.jsonl`, `errors.jsonl`, son konum), `KuryelogPrefs.Values`, `CourierRecords` (`courier.db`; testte `Memory`), `Clock`, `ScooterBridge`, `WeatherPort`, `OrderObserver`, `geocode`, `askCashTip`, `phoneBattery`, `tl`, `importing`. `CaptureSink` ve `ShiftMachine.Hooks`'u uygular. Kare sırası (4.3), `process` (4.4), `persist` (4.5), `location` (4.7), navigasyon bağı (4.9, `navigation(RouteStatus?)`), telefon (4.11, `seen` `OrderFlow`'un kendi haritası), yeniden kurma (4.12), okuyucular (4.13) Rawstats sırasıyla.
  - Yok (YolGo, S7): `publishFeeds`, `OfferFeed.refreshGrant/revoke`, `YolGoNavReader`, IDLE'da hava önbelleği. Ekonomi çağrıları `OrderObserver` kancalarıdır (`restart`, `waits` adım 13, `frame` adım 17, `events`, `declineTap`, `distance`, `stopping`, `stop`, `cancellation`); A3.5–A3.7'de `NONE`.
  - `commissionSet`: `Values`'ta `contains` yok; `getFloat(k, NaN)` ile aynı soru.
  - A3.6'ya kalan adımlar (4.3 adım 1/4/6, 4.2 `onNewShift`/`onEnd`, 4.6 E14/E16, restoran adres çözümü, `cashPendingShift`, `recordPocketCash`, gün dosyası okuyucuları) 17 Ekim'de bağlandı: 16.2.
- Desenler: yeni desen yok; taşınanlarda `\b` yok (`IcuRegexTest`, `RegexPortabilityTest` tüm modülleri tarar). JVM `\s` (ASCII) / ICU `\s` (Unicode) farkı `CashPayment`, `PlatformHistory`'de `[\s\p{Z}]` ile zaten kapalı; `DeliveryCore` desenleri `CourierParser.norm`'lanmış metne bakar.

### 16.2 A3.6 taşıma kaydı (17 Ekim 2026; dal `ccr-e90bca59-fxabvl`)

Satır satır kopyalanan (değişiklik yalnız paket satırı ve importlar): `PlatformTotals`, `CashPending`, `PocketCash`, `RecordPages`, `RestaurantBubble`, `CustomerBubble`, `CustomerCard`. Farklar:

- WEEM: `PlatformTotals` — `EconomicsLogs.platformOf` → `Platforms.of` (üç çağrı). Karşılaştırma satırı (`line`) ve fark satırı (`diffLine`) "· Rawstats: …" yerine "· WEEM: …" (`PlatformTotalsF85Test`; kullanıcıya görünen metin).
- WEEM: `CustomerCard` — `YolGoNav.State/Route` → `RouteStatus`/`RouteStatus.Route` (`NavPort.route`, 4.9, 5.4): ölçüm anı `measuredAtMs`, yaş sınırı `RouteStatus.ROUTE_MAX_AGE_MS` (30 sn), restoran hedefi `NavKind.RESTAURANT`, istek anı `requestedAtMs`. Metinlerdeki "(YolGo)" → "(WEEM)" (5.4). `trigger`: `remainingTurns` başka modülün tipi olduğundan Kotlin akıllı dönüşüm yapmıyor, `takeIf` ile yazıldı (aynı karar).
- WEEM: `OrderFlow` — Android'e ait iki istek `Env`'e geri çağrı: `askPocketCash(shiftStartMs)` (`PocketCashPrompt.ask`, bildirim 4901) ve `notifyReminder(reminder, update)` (`DeliveryReminder.notify`, bildirim 12; `update` sessiz güncelleme). Pencereleri (balon, kart) Android `:orders` `reminder`'ı okuyarak açar (A3.7).
- WEEM: vardiya kancaları ikiye bölündü (4.2 kayıt sırası): `OrderFlow` (`onRunning`, `onFix`, `onDistance`, `busy`, `onEnd` = `stop`) ve `OrderFlow.cashPendingHooks` (`onNewShift`: `askPocketCash` korumalı, sonra `cashPendingShift(SHIFT_START)`; `onEnd`: `cashPendingShift(SHIFT_END)`). `:app` sırası `cashPendingHooks` → `core.dailyPack` → `OrderFlow` → `scooter`.
- WEEM: `Store.append/day/prefs/logsRoot` → `env.log.append/day`, `env.prefs.put`, `dayFile`; `System.currentTimeMillis` → `env.clock.wallMs()` (`recordPocketCash`, `restaurantReminder`). `Store.lastLocation` → `env.log.lastLocation` (`LogStore.Loc`): `hasAccuracy` alanı yok, Android bilinmeyen doğruluğu 0 verir ve gerçek fix hep > 0 olduğundan `accM <= 0` "doğruluk bilinmiyor" (bubble/kart ölçmez) sayılır.
- WEEM: restoran adresi çözümü `Geocoder` + `main.post` yerine `env.geocode` (arka plan ve sonuçta `synchronized(this)` `OrderFlow`'da); sonuç yoksa hiçbir şey yazılmaz (Rawstats gibi).
- WEEM: `cashPendingRows` ve `pocketCashRows` `jsonRows` ile (Rawstats ile aynı okuma); `platformTotals(day)`, `lastPending()`, `recordPocketCash` Rawstats imzalarından `ctx` çıkmış hâliyle. Tercih anahtarları aynı: `cashPending.azn`, `cashPending.at`.
- Yok (Android, A3.7): `PocketCashPrompt`/`PocketCashReceiver`/`PocketCashActivity`, `DeliveryReminder`, `CustomerBubbleView` ve pencere kodu. `CustomerBubbleViewF73Test` 3 kaynak taramasıdır, A3.7'de WEEM dosyalarına yazılır.
- Desenler: yeni desen yok; `\b` yok (`IcuRegexTest`, `RegexPortabilityTest` yeşil).
- Test: `logic/orders` 387 test yeşil. 47 A3.6 testi Rawstats gövdeleriyle yeşile döndü; `OrderFlowTest` +4: saha ekranlarından resmî toplamlar ve CASH PENDING/offload satırları (tekrar yazılmaz, tercihte kalır), vardiya kancaları (cep nakdi sorusu, başlangıç/bitiş satırı, sipariş anlık görüntüsü, en yeni cevap), E14 restoran balonu (50 m aç, 150 m kapat, bildirim bir kez, kapatılınca açılmaz, yanlış/eski fix ölçmez), E16 müşteri kartı (300 m, bir kez).

### 16.3 A3.7 taşıma kaydı — Android katmanı (17 Ekim 2026; dal `ccr-e90bca59-fxabvl`)

Satır satır kopyalanan (Rawstats @ `6388c04`): `WatchService` (`:capture`), `NotifService` (`:capture`), `DeliveryReminder`, `CashTipPrompt` + `CashTipReceiver`, `TipAmountActivity`, `PocketCashPrompt` + `PocketCashReceiver` + `PocketCashActivity`, `res/layout/tip_quick.xml` (`:orders`, paket `com.kuryelog.app.orders`); rozet kodu `WatchService.kt:347-401` → `OrdersHost`; E7 uyarısı `GpsService.kt:25-31, 117-155, 293, 316-335` → `:capture` `ScreenReadingWarning`. Farklar:

- WEEM: `OrderTracker.x(ctx, …)` → `Orders.flow(ctx).x(…)` (`OrderFlow`); `I18n.tl` → `Orders.tl` (TR/AZ çifti aynı, `Store.lang`); `OrderTracker.f2/platform` → `Orders.f2` / `OrderFlow.platform`; `Store.lastError = …` → `Store.log(ctx).lastError = …`; `R.drawable.ic_stat` → Android sistem simgesi (WEEM simgesi gelince değişir; kullanıcı kararı bekleniyor).
- WEEM: renkler `SurusPaneli.palette(dark)` → `logic/core` `OverlayPalette.forMode(dark)` (Sürüş Paneli `RawPalette` değerleri aynen; kabuğun `Palette`'i yalnız Compose ekranlarında). Gölge modda iki uygulamanın rozeti ve balonları aynı renkte.
- WEEM: bildirim numaraları tablodan: 12 `NotificationIds.DELIVERY_NOTE`, 4901 `POCKET_CASH`, nakit bahşiş `NotificationIds.cashTip(id)` = 10000 + (Rawstats 4000 +); hızlı düğme istek kodu `id × 8 + i + 1` aynı formül. Bildirim 12'nin dokunuşu `MainActivity` yerine uygulamanın açılış Activity'si (`:orders` `:app`'i görmez; sonuç aynı).
- WEEM: rozet ve hatırlatıcı pencereleri `CaptureHost` (`OrdersHost.onConnected/onTick/onDisconnected`) üstünden; tik sırası Rawstats'taki gibi: ev izleyici → olay kapsamı → (vardiyada) sipariş tiki, rozet, hatırlatıcılar → okuma. Rozetin dikdörtgeni `OwnOverlays`'e kayıtlı (tüm ekran OCR maskesi; Sürüş Paneli topu A5'te kaydolur). `ScooterLink.publish` yok (CAPTURE.md 1.6).
- WEEM: vardiya dinleyicileri `WeemApplication`'da `cash_pending` (`OrderFlow.cashPendingHooks`) → (`core.dailyPack`, A4.3) → `orders` (`OrderFlow`) → (scooter, A5) → `screen_reading` (E7). Hata kaynağı `ShiftListeners` biçiminde `shift_start/cash_pending`, `gps_orders/orders` vb. (Rawstats: `shift_start`, `gps_orders`).
- WEEM: `Geocoder` `Orders.AndroidEnv.geocode`'da (tek iş parçacığı, sonuç ana iş parçacığında `Store.guarded("geocode")` içinde; Rawstats korumasızdı). ZIP içe aktarmadan sonra `LogImport.afterImport` → `OrderFlow.reloadImportedStats` (korumalı, kaynak `import`; Rawstats `safely("import")`).
- WEEM: Activity/alıcı adları `com.kuryelog.app.orders.*` (ad serbest, `VERI_SOZLESMESI` 2); manifest öznitelikleri aynı (dışa kapalı, `Theme.DeviceDefault.Dialog`, `excludeFromRecents`, `taskAffinity=""`).
- Ayarlar ekranındaki seçimler (`overlay`, `customerCard`, `customerCardSizePct`, `restaurantBubbleWidthPct`, `ocr`, izlenen uygulamalar) ve sipariş kartındaki bahşiş girişi A3.8 (Compose); tercih anahtarları ve varsayılanları burada okunur.
- Test (kaynak taramaları, WEEM dosyalarına uyarlandı): `CustomerBubbleViewF73Test` 3, `TipNotificationF46Test` 4, `RestaurantBubbleE14Test` +2, `CustomerCardE16Test` +1, `ReadinessE15Test` +1, `OverlayBadgeK35Test` +1 (`overlayUsesTheNamelessLines`; ana sayfa adları A3.8), `OrdersWiringTest` 3 (bağlama, dinleyici sırası, `Env` istekleri). A3.8'e kalan taramalar: `RestaurantWaitE2Test.cardAndListShowIt`, `BoostTempE3Test.cardTopShowsIt`, `PlatformTotalsF85Test.dayPageHasTheCard`, `AverageListE1Test.eachAverageOpensItsList`, E14/E16 ayar kısımları; A4.3'e: yedek/dışa aktarma taramaları.

### 16.4 A3.8 taşıma kaydı (17 Ekim 2026; dal `claude/a3-8-orders-tab-vfg3r9`)

Rawstats `MainActivity.kt` @ `6388c04` ekran kodu (`renderHome` Bugün kartları, `renderTodayPlatforms`, `showMetric`, `renderDayPages`, `dayOrders`, `renderOrderDay`, `cashNote`, `officialNote`, `missedNote`, `boostNote`, `renderTrip`, `cashSection`, `renderDailyRecords`) metin ve sırasıyla; arayüz Compose.

- WEEM mimarisi: **içerik `logic/orders`'ta** (Android'siz, testli), Compose yalnız çizer. `OrdersSource` (okuma + düzeltme arayüzü) → `LogOrdersSource(log, db, prefs, clock, flow?)`: saklı siparişler `courier.db`'den, gün dosyaları (`economics-*.jsonl`, `events.jsonl`) doğrudan; canlı akış (`OrderFlow`) bağlıysa canlı kopya saklıyı ezer (RS `OrderHistory.days`), "ŞU AN" `badgeLines()` (adlı), bahşiş/nakit/teslim/müşteri kartı düzeltmeleri akışa gider (`canAct`). `OrdersScreen(src, lang)`: `today()` (ŞU AN, Net kazanç kartı, platform kartları), `metricList` (E1), `dates/index/header/page` (gün sayfaları), `dayDetail` + `cashCard/officialCard/missedCard/boostCard/tripCard`; çıktı `Block`/`Card`/`Tile` modeli. `:app`: `OrdersTab`, `DayPages` (HorizontalPager; tarihler azalan, sola kaydırma eski gün, ‹ ›, tarih seçici, "N / M gün"), `OrdersBlocks`, `OrdersAccess` (`Orders.flow`/`Orders.records`, A3.7), `RecordsScreen` (Kayıtlar gün sayfaları: arşiv/etkin, kayıt dosyaları, arşivle/çıkar, sil).
- Metin farkları (bilerek): "Toplanan nakit (Rawstats)" → "(WEEM)", "Rawstats'ın kaçırdığı siparişler" → "WEEM'in kaçırdığı siparişler" (AZ "WEEM-in buraxdığı sifarişlər"), hava yoksa "yok (son 90 dk'da hava bilgisi gelmedi)" (YolGo adı yok), resmî değer satırı "WEEM:" (A3.6). Sipariş kartında **Teklif puanı satırı yok** (E4 `:economics`, A4.1). "Bugün" kartları Siparişler sekmesinin başında (Rawstats ana sayfasındaydı).
- Davranış: bir görüntü = bir `OrdersScreen` örneği (saklı + canlı kayıtlar bir kez okunur); vardiya açıkken sekme 5 sn'de bir yenilenir (Rawstats her render'da okurdu). "Sil" yalnız canlı akış bağlıyken açık (`courier.db` günlüklerden `reloadImportedStats` ile yeniden kurulur); bugünün kaydı vardiya açıkken silinmez/arşivlenmez (RS `record_stop`). Paylaş (kayıt ZIP'i) A4.3'te.
- **Yapılmadı (görev dışı; karar bekliyor, WEEM_ISLER S20):** "Çevrimiçi oturumlar" listesi (`renderSessions`), Araçlar (bölünmüş ekran, iptal gözlemi), Ayarlar ekranındaki seçimler (`overlay`, `customerCard`, boyutlar, `ocr`, izlenen uygulamalar, filo komisyonu), kayıt günü "Paylaş". `OrderFlow.markCancellationObservation` ve komisyon okuması hazır; yalnız ekran yok.
- Test: `OrdersScreenTest` 17 (Rawstats'ın dört kaynak taraması — `PlatformTotalsF85Test.dayPageHasTheCard`, `BoostTempE3Test.cardTopShowsIt`, `RestaurantWaitE2Test.cardAndListShowIt`, `AverageListE1Test.eachAverageOpensItsList` — modelin çıktısına yazıldı: sıra ve içerik; ayrıca sayfa sırası, başlık, platform süzgeci, arşiv/etkin, nakit ve resmî değerler kartları, kaçırılanlar, boost, sipariş kartı, saklı kaynak), `OrdersTabWiringTest` 4 (bağlantı, ana sayfa adları/rozet adsız (K35), kurye uygulamasına dokunmama, silme kapısı); `:app` Robolectric + Compose `OrdersTabTest` 4 ve `RecordsScreenTest` 1 (ekranlar JVM'de gerçekten çizilir: Bugün kartı, sola kaydırma eski gün, günün ayrıntıları ve geri, satır → sipariş kartı, ortalama → liste, arşiv/etkin, arşivle). `RecordJson` artık public (testler kayıt yazar). CI: `testReleaseUnitTest` kapalı (Compose test manifesti yalnız debug'da).
- Doğrulanmadı: gerçek cihazda (Pixel/Poco) çizim, kaydırma hissi, büyük veri ile hız (tüm `courier.db` ana iş parçacığında bir kez okunur), erişilebilirlik (TalkBack) etiketleri.


### 16.5 A3.9 kaydı — gölge mod raporu (17 Ekim 2026; dal `claude/a3-9-shadow-mode-report-7vy1z6`)

Yer: `logic/orders/src/shadow` (ayrı kaynak seti; APK'ya girmez, modül grafiği değişmedi): `ShadowExport` (ZIP/klasör; `YYYY-MM-DD/<logNames>` + `restaurants.json`, bir üst klasöre izin, bozuk satır sayılır), `ShadowCompare` (kurallar), `ShadowRules` (bilerek farklar), `ShadowReport` (metin + JSON `weem-shadow-report-v1`), `ShadowMain` (`./gradlew -p logic :orders:shadowReport --args="rawstats.zip weem.zip"`; kullanım `WEEM_ISLER` A3.10).

- **Eşleme (14.3):** sipariş = platform + `sameRestaurant` (iki yönde; ikisi de adsızsa eş) + kabul anı ± 60 sn, en yakın; teklif = platform + ilk görülüş ± 60 sn + ücret (±0,005). Sipariş kaydı `kl_export.trips` gibi seçilir (faz > bitiş > satır anı), `RecordJson.trip` ile okunur; gün = kaydın ilk göründüğü gün klasörü; gün toplamları `OrderHistory.Day` (sipariş sayısı `expectedOrders` toplamı, brüt = ücret + ek bekleme).
- **Sert:** `order.unmatched`, `order.outcome` (`PlatformHistory.state`), `order.fee`, `order.net`, `order.extra_wait`, `order.orders`, `order.payment`, `order.verified_by`; `day.trips/orders/delivered/delivered_inferred/unconfirmed/gross/net`; `offer.unmatched`, `offer.outcome` (kabul / ret / zaman aşımı = `offer_unconfirmed`+`expired_inferred` / belirsiz / açık); `parser.stage` (Rawstats `screens.jsonl` satırı WEEM `CourierParser`'dan başka aşama ya da `offerLike`); `boost.set` (gözlem anı/konum/`first_seen` hariç küme); `platform_total.*` (platform|dönem|ekran anahtarıyla son satır); `platform_list.*` (`key` ile; gözlem anı, okuma kaynağı, `match.trip_id`/`dt_s` hariç); `cash_pending` (anlar ve sipariş kimliği hariç çoklu küme).
- **Yumuşak:** sipariş anları (`accepted_at`, `completed_at`, `delivered_inferred_at`, `cash_collected_at`, alım, müşteri teslimi), varış anı/kaynağı, `fee_at`/`fee_gap_ms`/`fee_ocr_ms`, `record_snapshot` sayısı, sipariş olay türleri; teklif ilk görülüş/karar anı, aynı türde başka neden; `screens.jsonl` satır sayısı; bildirim eşleşmeyen/içerik; olay türü sayıları, eşleşmeyen tıklama (± 2 sn); `errors.jsonl` kaynak sayıları; `gps.csv` satırları; çevrimiçi oturum sayısı/dakikası; `platforms.jsonl` durumları; `restaurants.json` anahtarları; bozuk satırlar; günün açık siparişi.
- **Beklenen (`ShadowRules`):** A4.1/A5'e kadar yazılmayan dosyalar (`economics-offers/legs/idle/penalties/waits/exposure/energy/offer-scores/offer-ratings/scooter`, `scooter*.jsonl`); kullanıcı cevapları (`economics-cash-tips`, `economics-pocket-cash`; S16); kendi bildirimimizin numarası (5103 → 5104, 4000–8095 → 10000–14095; `.dev` paketi ve uid önemsiz); metinde WEEM adı ("Rawstats", "KuryePlus", "(YolGo)" → "(WEEM)", YolGo hava metni); `click.text` `redact`'i; YolGo kökenli varış (`nav_yolgo`, `gps_*_yolgo_target`) ve `yolgo_venue`; teklif havası (`weather_*`) ve scooter pili/sipariş enerjisi yalnız Rawstats'ta.
- **"Bitti" ölçütü:** `ShadowSahaTest` — saha kareleri (6.481) iki yoldan: Rawstats tarafı `tools/shadow-fixture` ile Rawstats @ `6388c04`'ün kendi saf sınıflarından (`OrderTracker.onScreen`/`tick` sırası, konumsuz; fikstür `logic/orders/src/test/resources/shadow/`, yeniden üretim komutu `tools/shadow-fixture/build.gradle.kts` başında), WEEM tarafı `OrderFlow` + `CaptureRows.screen`. Sonuç: sert 0, yumuşak 0, beklenen yalnız `economics-exposure.jsonl`; ayrıştırıcı 6481/6481; gün özetleri saha beklenenleriyle (11 Ekim 10 sipariş, 12 Ekim 13 sipariş/14 kayıt, 2 iptal teslim sayılmaz). Kurallar `ShadowCompareTest` (14).
- Doğrulanmadı: gerçek iki uygulamanın ayrı OCR'ı (aynı kareyi farklı anda okur) yumuşak farkları ve OCR kaynaklı ücret farklarını (sert) ne kadar üretir — A3.10'da görülür. WEEM `.dev` dışa aktarması yok (S21).
