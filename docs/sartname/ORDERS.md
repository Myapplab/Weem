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
