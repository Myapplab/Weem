# Rawstats — sipariş takibi ve sipariş çevresi (orders) bulguları
Kaynak: rawstats @ 6388c04; kök /home/user/kurye_nav (yollar `app/src/main/java/com/kuryelog/app/` = `M/`, `app/src/test/java/com/kuryelog/app/` = `T/` kısaltmasıyla)

> DURUM: yazım sürüyor (ara kayıt). Bölümler tamamlandıkça eklenir.

## 1. Özellikler

### R5 Sipariş takibi
- Eski kod: `M/DeliveryCore.kt` (veri modeli 1–172, `DeliveryCore` 174–898, `CustomerMatch` 900–943, `OfferMatch` 945–960);
  `M/OrderTracker.kt` (Android tutkalı: `onScreen` 300–382, `process` 511–545, `persist` 551–573, `tick` 574–642, `location` 706–717,
  `onClick` 483, `confirmDelivered` 786); `M/VenueArrival.kt` (D9/M18/M19); `M/ArrivalDistance.kt` (M18–M20); `M/DeliveryDeadline.kt`
  (K29); `M/Readiness.kt` (E15 hazır sayacı); `M/TripStages.kt` (E8 YolGo'ya aşama zaman çizelgesi).
- Durumlar: `enum class TripPhase { TO_RESTAURANT, WAITING, TO_CUSTOMER, AT_CUSTOMER, FINISHED, END_UNCONFIRMED }` (`M/DeliveryCore.kt:3`).
  `active` = FINISHED ve END_UNCONFIRMED dışı (:139); `countsAsDelivered` = `completedAt != null || deliveredInferred` (:142);
  `deliveredInferred` = `deliveredInferredAt != null && phase == END_UNCONFIRMED` (:111); `atCustomer` = active ∧ TO_CUSTOMER/AT_CUSTOMER (:144).
- Kayıtlar: `TripRecord` (:91–162; id `"trip-${o.at}-${++sequence}"` :323), `PickupRecord` (:76–82: restaurant, address, readyAt,
  arrivedAt, pickedAt, arrivalSource, readyObservedAt, note, code, items, readyHintAt, preparingAt, reminderAt), `CustomerRecord`
  (:83–90: id, details:DeliveryDetails, arrivedAt, deliveredAt, lat, lon, coordinateSource, reminderAt, detailsAt, deadlineClock,
  deadlineAt, arrivalSource). `CoreEvent(type, pkg, at, offerId, tripId, reason)` (:164).
- Geçişler (`react`, :282–448):
  - Açılış: PICKUP/DROPOFF karesi + bekleyen teklif (decline tıklaması yok, açık siparişe ait değil) → yeni Trip, olay `accepted`
    reason `active_screen`; teklif yoksa `missedOffer=true`, reason `active_screen_offer_missed` (:322–326). Restoran/müşteri/kod
    içermeyen ("substantive" değil, :474) kare sipariş açamaz (:321). Bitişten ≤30 s içinde aynı id/adresli kare yeni sipariş açmaz (:291–296).
  - D25: teklif kartında "Cash order" → açılışta `paymentMethod="cash"`, `paymentSource="wolt_offer_card"`, olay `payment_seen` reason `cash_offer_card` (:329–334).
  - PICKUP: son pickup alınmışsa ve restoran farklıysa yeni `PickupRecord`, faz TO_RESTAURANT (:339–346); ilk pickup'ta teklifin "Pickup N min"
    hazır sayacını başlatır (`Readiness.fromOffer`, :345). `o.readyIn` → `readyAt` (ilk), `readyHintAt` (son) (:350); `o.arrived` → `arrivedAt`,
    `arrivalSource="platform_status"`, faz WAITING, olay `restaurant_arrived` (:354–357).
  - DROPOFF: açık pickup'ın `pickedAt` = kare anı, olay `picked_up` (:360–362); faz TO_CUSTOMER (:363). Müşteri kimliği: 4+ karakterli sipariş
    id'si (`keyIds`, :874), yoksa ad (`CustomerMatch.find`, OCR varyantı) → `"name:<norm>"`, yoksa `"customer-1"` (:366–367); yer tutucu gerçek id ile
    değişir (:369–372). Önceki müşteriye geçiş: onay tıklamasından <30 s → `next_customer_after_confirm`, kaydırıcı görülmüşse
    `next_customer_after_delivery_slider` → `customer_delivered` (:374–385). `o.arrived` → AT_CUSTOMER (:398).
  - Bitiş: `o.delivered` → `completeCustomerOrTrip(... "platform_completed")` (:400); tek müşteri/1 sipariş → `finish` (olay `delivered`,
    `completedAt`, faz FINISHED, :813–818); çoklu teslimatta tüm beklenen müşteriler teslim edilince (:799–812).
  - Bekleme haritası (IDLE) ≥250 ms kararlı (:405): onaydan <30 s → `confirmed_delivery_to_idle`; kaydırıcı sonra harita (≤`SLIDER_TO_WAITING_MS`
    30 dk, kararlı ≥1000 ms) → `delivery_slider_then_waiting` (:417–421, 789–798); OFFLINE → `end_unconfirmed` reason `offline_without_completion` (:422).
  - Wolt bekleme haritası ≥`WAITING_MAP_CLOSES_MS` 3000 ms (M22: ilk harita karesinden, :427): açık Wolt siparişleri kapanır — teslim edilmiş müşteri
    varsa `waiting_map_after_delivery`; müşteri aşamasındaysa `delivered_inferred` reason `waiting_map_at_customer`; yoksa `end_unconfirmed`
    reason `waiting_map_before_pickup` / `waiting_map_without_delivery` (:427–440).
  - K34 "bir anda tek müşteri": bir sipariş müşteri aşamasına girince diğer müşteri aşamasındakiler `delivered_inferred` (END_UNCONFIRMED,
    `deliveredInferredAt/By/Reason`; reason `next_order_at_customer[_after_slider[_spinner]]`) (:563–574). D3(b): Wolt'ta kaydırıcıdan sonra başka
    siparişin restoran ekranı → `next_order_at_restaurant_after_slider[_spinner]` / `..._after_<hintKind>` (:616–627). Kapanmış kartın geç
    görünmesi sipariş açmaz (`closedOrderOfCard` :510–524, `lateCard` :526–540, `mayBeClosedCard` :542–545).
  - R1 aynı müşteriye çoklu sipariş kartı ("2 dropoffs to", Wolt 3 haneli #) birlikte teslim (`sameCard` :580–594, `companionsDone` :596–598).
  - M22/M16: Wolt kaydırıcıda spinner ya da nakit kutusu ✓ → `handoverHintFor/Kind` (`spinner`/`cash_checked`) (:394–396). K33 spinner kanıt
    olayı `delivery_spinner_seen` (≤`SPINNER_AFTER_SLIDER_MS` 10 s, :776–783); tek başına kapatmaz.
  - Elle: `confirmDelivered` → reason `courier_manual_correction` (:784–787); `stop` → açık siparişler `end_unconfirmed` reason
    `manual_shift_stop` (:201–210). Tıklamalar: "Arrived" → restoranda WAITING (`arrivalSource="user_arrived_action"`) / müşteride
    AT_CUSTOMER; çağrı düğmesi → `callFor/callAt` (dialer numarası için ≤`CALL_TO_DIALER_MS` 60 s) (:709–748, 254–257).
  - D3(d) Wolt "Cash collection" satırı: teslim saatini o dakikaya düzeltir (`delivery_time_corrected`), açık/çıkarımlı siparişi `delivered`
    reason `wolt_cash_collection` yapar, `verifiedBy="wolt_cash_collection"` (:642–667). D3(c) `listVerified` (:669–674).
- Varış algılama (Wolt restoranı, `M/VenueArrival.kt`): D9 duraklama — `RADIUS_M`=60 m içinde `STOP_KMH`<3 km/h başlayıp `DWELL_MS`=20 s
  kalma, `RIDE_KMH`≥10 durağı bozar, doğruluk >`MAX_ACCURACY_M` 60 m yok sayılır, düzeltmeler bellekte `KEEP_MS` 90 dk (:156–163, 50–60).
  M18/M20: taze (≤`FRESH_MS` 30 s) navigasyon mesafesi (YolGo `nav_yolgo` / Google Maps `nav_google_maps`) ya da GPS ≤`RESTAURANT_M` 5 m,
  hız <`SLOW_KMH` 10 → anında varış; GPS payı mesafe − min(doğruluk, 20 m), doğruluk >30 m hiçbir şey söylemez (`M/ArrivalDistance.kt:12–37`).
  M19 müşteri ≤`CUSTOMER_M` 10 m → `customer_arrived` reason `gps_near`/`nav_*` (`VenueArrival.customers` :93–109; `DeliveryCore.customerArrived` :694–701).
  Pickup anında: koordinat kuryenin konumundan >`VENUE_MISMATCH_M` 150 m ise yanlış (geocode) sayılır, son düzeltme ≤`PICKUP_FIX_MAX_AGE_MS` 5 dk
  (`collected` :116–129). Kaynak adları: `gps_dwell_geocode`, `gps_dwell_yolgo_target`, `gps_dwell_pickup_point`, `gps_near_geocode`,
  `gps_near_yolgo_target`, `gps_near` (:164–178). Olaylar `restaurant_arrived` / `restaurant_arrival_corrected` / reason `gps_venue_mismatch`
  (`DeliveryCore.venueArrived` :680–692; platform durumu ya da "Arrived" tıklaması asla değiştirilmez).
- Restoran bekleme ve hazır: `waitMinutes` = Σ(pickedAt − arrivedAt) (:154); `lateMinutes` = Σ(pickedAt − max(ready, arrivedAt)), ready =
  `readyAt ?: readyObservedAt` (:156–157). E15 `Readiness.of`: `readyObservedAt` → READY; `readyHintAt ?: readyAt` → ceil(dk) >0 COUNTDOWN yoksa NOW;
  `preparingAt` → PREPARING; aksi UNKNOWN (`M/Readiness.kt`). Bildirimden "ready in N min" / "ready for pickup|order is ready|is ready|hazirdir"
  (`DeliveryCore.readinessNotice` :702–708).
- Teslim süresi / gecikme (K29, `M/DeliveryDeadline.kt`): son tarih = Bolt "Drop off in N min" → `at + N dk` (`PLATFORM`=`platform_dropoff_clock`),
  ≤`MOVE_MS` 2 dk fark yok sayılır (`merge`); yoksa teklifin üst tahmini `offer.firstAt + minHi dk` (`OFFER_ESTIMATE`=`offer_upper_estimate`);
  hiçbiri yoksa null (tahmin yok). `margins`: pickup ve teslimde kalan dk (negatif = gecikme). `actualMinutes` = (completedAt − acceptedAt)/60000 (:146).
- Mesafe/enerji siparişe: `addDistance` en son ekranı görülen aktif siparişe (K9, :820–824); enerji `addEnergy`/`closeEnergy` (R14 alanı) (:826–846).
- E8 `TripStages.feed`: açık (≤3) ve son 10 dk'da biten (≤3) siparişlerin aşama/zaman listesi, YolGo feed v2 `stages`; müşteri verisi yok
  (`M/TripStages.kt:13–83`). Sonuç: `delivered`, `delivered_unverified`, `cancelled` (pickup yok), `unconfirmed`.
- Testler: (bölüm 3'te sayılarla)
- Veri: `courier.db` (trips/offers tabloları — bölüm 2), `orders.jsonl` gün dosyası (bkz. bölüm 2), tercih `onlineSessions.v1`, `openWaits.v1`,
  `economicsIdle.v1` (OrderTracker :19, 189, 119).
- Bağımlı: R1 (CourierObservation/ScreenStage üretir), R3 (teklif → sipariş kabulü, `pending`), R14 (enerji), R16 (NavDistance), Y4/E8 (YolGo nav).
- WEEM: `:orders` — `DeliveryCore`, `VenueArrival`, `ArrivalDistance`, `DeliveryDeadline`, `Readiness`, `TripStages`, `CustomerMatch` saf → `logic/`;
  `OrderTracker` Android katmanı (ince tutulmalı) · Aşama 3.
- Not: `WaitObservation.kt` restoran beklemesi DEĞİL: teklifler arası boşta bekleme dönemleri (ekonomi v2, `:economics`); WaitObservationProcessDeathTest oraya ait.

### R6 Restoran ve müşteri balonları (taslak — tamamlanacak)
- Eski kod: `M/DeliveryReminder.kt` (overlay; 3 pencere: tam kart, restoran balonu, müşteri balonu), `M/CustomerBubble.kt`, `M/CustomerCard.kt`,
  `M/RestaurantBubble.kt`, `M/Readiness.kt`, `M/NoteFields.kt`; tetik `OrderTracker.tick` 574–642.
- Overlay türü: üçü de `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`, `Gravity.TOP`, y=28dp
  (`M/DeliveryReminder.kt:152–154, 190–192, 273–275`) → erişilebilirlik servisinden (`WatchService`) çizilir; SYSTEM_ALERT_WINDOW izni yok.
- Restoran balonu E14: `SHOW_M`=50 m düz çizgi ya da varış görüldü → açılır; Collect ya da >`HIDE_M` 150 m → kapanır; düzeltme ≤15 s, doğruluk ≤40 m;
  genişlik `restaurantBubbleWidthPct` varsayılan 90, seçenek 70/80/90/100 (`M/RestaurantBubble.kt`).
- Müşteri kartı E16: düz ≤`STRAIGHT_M` 300 m, YolGo rotası ≤`ROUTE_M` 300 m ya da son `LAST_TURNS` 2 dönüş; boy `customerCardSizePct` varsayılan 85,
  seçenek 70/85/100/115; yükseklik ekranın %40 (büyüt %62) × boy (`M/CustomerCard.kt`). Müşteri balonu F7.2: sol Blok·Kat·D·Kod (yoksa ad), sağ
  son tarihe kalan dk (ceil, negatif=gecikme; geç kalınca stil değişir), alt notlar ekranın ≤%25'i; yatay kaydırma >%25 genişlik kapatır, dikey >48 dp
  notları katlar (`M/CustomerBubble.kt`). Ayar `customerCard` (varsayılan true) kapalıysa hiçbiri açılmaz (`M/DeliveryReminder.kt:68`).
- Bildirim: kanal `delivery_notes` (IMPORTANCE_HIGH, titreşim), id **12**, içerik müşteri `details.display()` (ad/not/adres içerir — telefon? doğrulanacak) (`M/DeliveryReminder.kt:301–310`); `GpsService.kt:291` cancel(12).
- Ara düğmesi: `Intent.ACTION_DIAL` `tel:` (yalnız rakam ve +) — müşteri telefonu overlay'de bellekte (`phone`).

### R8 Nakit ve bahşiş (taslak — tamamlanacak)
- K31 `CashPayment` (`M/CashTips.kt:1–80`): Wolt dizgileri `Cash order`, `Collect cash payment`, `I have collected the cash (AZN 20.40)`,
  `Steps to complete the delivery`, `No change needed`, `(Total) change needed`; kaynaklar `wolt_cash_screen`, `wolt_delivery_steps_no_cash`,
  `wolt_offer_card`, `wolt_cash_collection`, `not_observed`. Bolt nakit ekranı tanınmıyor.
- K32 `CashTips`: dosya `economics-cash-tips.jsonl` schemaVersion 1, ekleme-yalnız, tip = alınan − nakit (BigDecimal HALF_EVEN 2 hane), eksi
  saklanır `negative_tip=true`; hızlı düğmeler 1/2/3/5 ₼; via `notification`/`card`/`notification_quick`/`tip_window`; kaynak `user_cash_input`/
  `user_quick_tip`; nakit kaynağı `user_manual` (F4.9). Soru koşulu: cash ∧ (completedAt ∨ deliveredInferred).
- Bildirim `CashTipPrompt`: kanal `cash_tips` (HIGH), id = `4000 + (tripId.hashCode() and 0xFFF)` → 4000..8095 aralığı (`M/CashTipPrompt.kt:22`);
  RemoteInput anahtar `cash_received`, extras `trip_id`, `pkg`, `cash_amount`, `tip_azn`; alıcı `CashTipReceiver`; pencere `TipAmountActivity`;
  kayıt sonrası bildirim 15 s zaman aşımı.

## 2. Veri sözleşmesi kalemleri (ara)
| Tür | Ad | Ayrıntı | Kaynak |
|---|---|---|---|
| bildirim kanalı + no | `delivery_notes` / 12 | HIGH, titreşim; restoran/müşteri kartı | `M/DeliveryReminder.kt:303–308` |
| bildirim kanalı + no | `cash_tips` / 4000+(hash&0xFFF) | HIGH; K32 soru + kayıt bildirimi | `M/CashTipPrompt.kt:20–24, 70, 84` |
| bildirim no | 5103 (İKİ sabit) | `ScreenReadingGuard.NOTIFICATION_ID` (kanal `screen_reading`) VE `AutoStartNotice.SHIFT_ID` (kanal `auto_start`); ayrıca CashTipPrompt aralığı 4000..8095 içinde | `M/ScreenReading.kt:38`, `M/AutoStartNotice.kt:20`, `M/GpsService.kt:131` |
| bildirim no | 5101 / 5102 | `AutoStartNotice.HOME_ID` / `SCOOTER_ID` (aralık içinde) | `M/AutoStartNotice.kt:18–19` |
| bildirim no | 4902 | `ScooterLink.NOTICE_ID` kanal `scooter_link` (aralık içinde) | `M/ScooterLink.kt:31–32` |
| bildirim no | 1 | GpsService foreground, kanal `shift` LOW | `M/GpsService.kt:174, 316` |
| dosya | `economics-cash-tips.jsonl` | schemaVersion 1; alanlar schemaVersion, kind=`cash_tip`, trip_id, pkg, platform, at, via, received_azn, cash_amount_azn, cash_amount_source, tip_azn, tip_source, negative_tip, delivery | `M/CashTips.kt:100–145` |
| dosya | `economics-cash-pending.jsonl` | schemaVersion 1; kind `pending`/`offload`/`shift_start`/`shift_end`/`order_snapshot` | `M/CashPending.kt` |
| tercih | `customerCard` (bool, true), `customerCardSizePct` (int, 85), `restaurantBubbleWidthPct` (int, 90) | | `M/DeliveryReminder.kt:68`, `M/CustomerCard.kt`, `M/RestaurantBubble.kt` |

### (ara kayıt 2) Ek bulgular — R5/R6/R8 için, sonra birleştirilecek
- `M/OrderTracker.kt` akışı: `onScreen` (300–382) sırası: PlatformTotals → `platformHistory` (K36 liste karesiyse burada `return`) → `cashCollections`
  (D3d) → `cashPending` (M17) → parse + `CashPayment.attach` → `sessions.observe` → `core.observe` + `pickupArrivals` → `sessions.event` →
  `waitCore` → `economics-exposure.jsonl` (≥5 s) → `boosts.observe` → teklife boost/hava/konum → `econ.frame` → `process` → `platforms.jsonl`
  (IDLE/OFFLINE/çarpan) → müşteri adres geocode (`resolve`) → `persist` + `tick`.
- `process` (511–541): her CoreEvent `orders.jsonl`'a satır (`t`, `type`, `pkg`, `offer_id`, `id`, `reason`, `session_id`, `record` (=RecordJson.trip,
  `latest` dahil!), `gross`, `net`, `total_min`, `wait_min`, `km`, `restaurant`, `offer`, `loc`, `phone_battery_pct`); `ORDER_ENDS` =
  `delivered`, `delivered_inferred`, `end_unconfirmed` (544) → enerji kapanır, `ScooterBridge.orderDone`. K32 sorusu: `delivered`/`delivered_inferred` +
  `CashTips.shouldAsk` + o güne ait cevap yok + `cashAsked` (yalnız bellek; yeniden başlatmada bir kez daha sorabilir) (537–543).
- `persist` (551–573): en geç 1,5 s; courier.db'ye yalnız değişen satırlar tek transaction; `RecordJson.trip(t, withPhone = true)` → telefon YALNIZ courier.db
  (yorum: "allowBackup=false, no device transfer", 557); `orders.jsonl` `record_snapshot` (latest çıkarılmış; force ya da `now % 10000 < 2000`).
- `RecordJson.details` (`M/RecordJson.kt:19–21`): müşteri `name` düz yazılır; `address`, `note`, `instructions`, `address_details` `NoteFields.redact` ile
  telefonları `[tel]` yapılır; `phone` alanı yalnız `withPhone` (82). `observation()` `raw`/`actions` redact edilir ama `customer.name` içerir (23–26).
  → Müşteri ADI courier.db + `orders.jsonl` + `latest` gözleminde düz durur; TELEFON yalnız courier.db + bellek + overlay + bildirim 12.
- `tick` (574–642): müşteri kartı (E16) — müşteri başına bir kez (`c.reminderAt`), başlık `"<Platform> · <ad ?: id>"`, `DeliveryReminder.notify`;
  restoran balonu (E14) — pickup başına ilk açılışta bildirim (`p.reminderAt`), kurye kapatırsa `bubbleDismissed` (bellek) tekrar açılmaz; hazır
  sayacı değişince bildirim sessiz güncellenir (`update = true`). Canlı satır: YolGo rotası ya da navigasyon bildirimi ≤20 s (`live`, 652).
- Geocode: `android.location.Geocoder` (`getFromLocationName(address, 3)`), müşteri için mevcut konumdan <50 km ise `coordinateSource =
  "android_geocoder_address_estimate"`; bulunamazsa olay `customer_coordinate_unavailable` (674–698). Restoran koordinatı `restaurantCoords` bellekte.
- Dialer (K25): `awaitingPhone` (758–760; müşteri aşamasında ve platform ekranı ≤`PHONE_WINDOW_MS` 60 s önce görüldüyse), `dialerPhone` (762–770):
  `NoteFields.phones` ile ilk numara → `c.details.phone` (bellek + courier.db). `Dialer.isDialer` paket listesi (`M/Dialer.kt`).
- `notification` (501–509): `events.jsonl` `notification_hint` (metin `NoteFields.redact`), `core.readinessNotice`.
- Bildirim 12 içeriği: başlık müşteri ADI, büyük metin `DeliveryDetails.display()` → **"Telefon: …" satırını içerir** (`M/CourierParser.kt:60–61`).
  WEEM'de scooter telefonuna gitmemesi gereken veri (plan) — bildirim yansıtma riski not edilmeli.

### R7 Kurye uygulaması üstünde gösterge (rozet)
- Eski kod: `M/OverlayText.kt` (biçim + kendi satırlarını tanıma), `OrderTracker.badgeLines` (892–896), `deliveredToday` (897–900); rozet penceresi
  `WatchService.updateBadge` (bkz. aşağıda doğrulanacak), `OrderTracker.offerVisible`/`OFFER_VISIBLE_MS` 2500 ms (D21: teklif kartı varken rozet yer değiştirir, 293–296).
- Davranış: satırlar: mesafe `"● 12.3 km"`; teklif `"Wolt · 2.21₼ · <restoran|Teklif>"` (+ komisyon ayarlıysa `net 3.15₼`, `OverlayText.net`, komisyon
  `commission:<pkg>` yoksa `commission`); aktif sipariş `"Wolt · <restoran|müşteri|Sipariş> · <faz>"`; K35 overlay'de ad yok: `"Wolt · Müşteriye gidiliyor"`
  (`names=false`); boşta `"Wolt · Sipariş bekleniyor"` / `"Wolt · Çevrimdışı"` (K30, platform başına tek satır, online kazanır); gün `"Bugün: 7 sipariş"`.
  Faz metinleri TR/AZ `PHASES` (`M/OverlayText.kt:28–34`). Para `BigDecimal HALF_EVEN`.
- `isOwn`/`withoutOwn`: parser rozet satırlarını (OCR bozulmalarıyla) kendi okumasından atar (N11) — `:capture` ile sözleşme.
- Veri: tercih `commission`, `commission:<pkg>` (float). Gün sayısı `OrderHistory.deliveredOrders` (çıkarımlı dahil).
- WEEM: `:orders` (`OverlayText` saf → `logic/`), pencere `:app`/`:capture` erişilebilirlik servisi · Aşama 3.

### R9 Cepteki nakit + Wolt nakit bakiyesi
- Eski kod: `M/PocketCash.kt` (M15), `M/PocketCashPrompt.kt` (`PocketCashPrompt`, `PocketCashReceiver`, `PocketCashActivity`), `M/CashPending.kt` (M17),
  `OrderTracker.cashPending` 807–819, `cashPendingShift` 820–828, `lastPending` 805, `recordPocketCash` 839–843.
- Cep: vardiya başında soru (kanal `pocket_cash`, id **4901**, HIGH, RemoteInput anahtar `pocket_cash`, extra `shift_start_ms`); 0 geçerli cevap (boş cep);
  dosya `economics-pocket-cash.jsonl` (schemaVersion 1: `shift_start_ms`, `amount_azn` HALF_EVEN 2, `answered_at_ms`, `via` = `notification_reply`/`window`),
  vardiya başının gün klasörüne; vardiya başına en yeni cevap kazanır; "Kaydedildi" bildirimi 10 s zaman aşımı.
- Wolt "CASH PENDING": Finances karesinde etiket satırı ya da sonraki 2 satırdaki tutar (`-`/`AZN`/`₼`, işaret önde/arkada); `+` = Wolt'a yatırılacak,
  `-` = fazla yatırılmış. Yazma: değer ≥0,005 değiştiyse ya da son yazım ≥`REWRITE_MS` 5 dk (`shouldWrite`). Son değer tercihte `cashPending.azn` (string),
  `cashPending.at` (long). "Cash offload" satırları bir kez (`offloadsWritten`, bellek). Vardiya başı/sonu satırı (`shift_start`/`shift_end`) + vardiya sonunda
  her sipariş için `order_snapshot`.
- Mutabakat (M17): `start + collected − offloaded = expected`; `diff = end − expected`; `ok` |diff| ≤ 0,01; satır `"Pending: 8.21 → 12.40₼ · beklenen 12.40₼ · fark 1.20₼"`.
  `collected` = vardiyanın teslim edilmiş nakit siparişlerinin nakdi (`CashPayment.dayTotal`, çıkarımlı dahil).
- Bağımlı: R8 (nakit tutarı), R13 (`PlatformHistory.offloadRows`/`cashRows`), G1 (vardiya başı/sonu tetikleri — çağıran yer doğrulanacak).
- WEEM: `:orders` (`PocketCash`, `CashPending` saf → `logic/`; prompt/receiver/activity Android) · Aşama 3.
- Not: F8 kuralı "bakiye yazılmaz" bu tek sayı için kullanıcı isteğiyle değişti (`M/CashPending.kt:6–11`, `M/PlatformTotals.kt:11–12`).

### R10 Boost / Earn extra
- Eski kod: `M/BoostLog.kt` (K27, D11/D24, M21); `OrderTracker.onScreen` 352 (`boosts.observe` → `Store.append(BoostLog.FILE)`), 356–363 (`atOffer` teklif alanlarına);
  `OrderTracker.boostRows` 796.
- Davranış: yalnız tam, ekrandan (bildirim değil) IDLE/OFFLINE/OFFER kareleri; `change` satırı değer/birim/aktif/pencere değişince (günün ilki
  `first_seen=true`, `prev_known`); Bolt `slot` her 30 dk (`SLOT_MS`), gözlenmeyen yarım saatler `observed=false` en çok 8 slot (4 saat) geriye
  (`MAX_GAP_SLOTS`); Wolt plan satırları (`kind=plan`, bekleme sayfası ve "Earn extra → Coming up later" listesi; liste kazanır, M21), saat dilimi
  `Asia/Baku`; "… 20% from 16:00" uçsuz dilim 60 dk (`FROM_SLOT_MIN`); "17/10" tarihi 180 günden eskiyse sonraki yıl.
- Teklife boost (`atOffer`): Bolt tek çarpan `x` → `offer_screen`; teklif kartı yüzdesi; Wolt planı (`wolt_plan` / `wolt_earn_extra`); taze (≤30 dk,
  `FRESH_MS`) ana ekran gözlemi yalnız aktifse; yoksa `not_observed`. Duyurulmuş (aktif olmayan) Wolt boost uygulanmaz.
- Dosya `economics-boosts.jsonl` schemaVersion 1; alanlar: `kind` (`change`/`slot`/`plan`), `pkg`, `platform` (`wolt`/`bolt`), `at`, `value`, `unit`
  (`pct`/`x`), `active`, `window`, `starts_in_min`, `stage`, `lat`, `lon`, `gps_accuracy_m`, `gps_age_s`, `first_seen`, `prev_value`, `prev_unit`, `prev_known`,
  `slot_start`, `observed`; plan: `from`, `slot_start_min`, `slot_end_min`, `plan_source`, `slot_day`. Konum yalnız telefonda (yedek hariç).
- Gün sayfası istatistiği `BoostLog.stats` (changes, slotsObserved/Missing/Boosted, max).
- Bağımlı: R1 (`boostPct`, `bonusPct`, `multipliers`, `boostSlots`, `boostWindow`), R3/R4 (teklif kaydı ve puanı boost'u kullanır).
- WEEM: `:orders` (saf → `logic/`) · Aşama 3.

### R11 Çevrimiçi süreler
- Eski kod: `M/OnlineSessions.kt`; `OrderTracker` `OPEN_SESSIONS = "onlineSessions.v1"` (19), `saveSessions` 279–285, `init` 266–270, `onlineToday` 772–780,
  `linkSession` 494–500.
- Davranış: yalnız olumlu kanıt durum değiştirir (online: "Go offline" kaydırıcısı, bekleme metni, teklif/pickup/dropoff ekranı → `task_screen`;
  offline: "Go online", çevrimdışı mesajı, Wolt Feedback sayfası `wolt_feedback_after_go_offline`). Talep haritası hiçbir şey söylemez. OCR kanıtı
  iki OCR karesi uyuşunca geçerli (ilk karenin zamanı). Geç kare oturum açıp kapatmaz. Kapanış sonu = max(kare, son online). Vardiya sonu: son online ekranda
  kapanır (`endObserved=false`, reason `shift_stop`; süreç vardiya bittikten sonra öldüyse `restart_after_shift`).
- Sayaçlar: offers, accepted, rejected, delivered (paket teslimatta müşteri sayısı, çifte sayım engelli; `delivered_inferred` sayılır, sonraki `delivered` eklemez);
  `offerIds`/`tripIds` en çok 200 (`MAX_IDS`). Oturum id `"wolt-<startMs>"`/`"bolt-<startMs>"`.
- Dosya `online-sessions.jsonl` (gün klasörü) schemaVersion 2 alanları: `pkg`, `sessionId`, `startAtMs`, `endAtMs`, `durationMinutes`, `startObserved`, `endObserved`,
  `endReason`, `lastOnlineSeenAtMs`, `startEvidence`, `offersSeen`, `accepted`, `rejected`, `delivered`, `offerIds`, `tripIds` (+ `t`); v1 okunur. Açık oturumlar
  tercih `onlineSessions.v1` (JSON dizi; en geç 10 s'de bir ya da değişimde).
- Rozet durum satırı `sessions.state(pkg)` kullanır (R7).
- WEEM: `:orders` (saf → `logic/`) · Aşama 3.
