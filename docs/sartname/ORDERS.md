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
