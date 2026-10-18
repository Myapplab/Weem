# ECONOMICS — Aşama 4.1 şartnamesi ve taşıma kaydı (`:economics`: R4 teklif puanı, E4, ekonomi v2 kayıtları)

Kaynak: Rawstats `rawstats` @ `6388c04` (salt okunur). Ayrı bir ekonomi şartnamesi yoktu (`bulgular/R_economics.md` "YAZILACAK"); bu belge A4.1'de Rawstats kaynağından ve `docs/ekonomi-v2/` (SPEC 2.1.0, `ref.py`, altın vektörler) belgelerinden çıkarıldı. Sipariş tarafının kancaları `ORDERS.md` 4.14 ve 13.3'te.

## 0. Özet: WEEM kuralları

- Saf mantık `logic/economics` (Android'siz, yerelde test); Android `:economics` yalnız ortam (gün dosyaları, tercihler, saat, scooter köprüsü) ve E4 okuyucuları.
- Rawstats dosyaları **satır satır** kopyalandı; değişiklik yalnız paket satırı, importlar ve 4. bölümdeki WEEM farkları.
- Kayıt dosya adları, şemaları ve tercih anahtarları **aynı** (VERI_SOZLESMESI 5, 7.1). Ekonomi kodu ağ kullanmaz; satırlarda müşteri adı/telefonu/adresi/koordinatı yok (bölge kaba: `Z1`–`Z4`/`unknown`).
- WEEM Wolt/Bolt'ta hiçbir şeye basmaz: gölge karar (`verdictShadow`) yalnız kayda yazılır, gösterilmez; puan ekranı yalnız okur ve kuryenin kendi puanını yazar.

## 1. Dosyalar (Rawstats → WEEM)

| Rawstats (`app/src/main/java/com/kuryelog/app/`) | WEEM (`logic/economics/.../economics/`) | Ne |
|---|---|---|
| `EconomicsV2.kt` (917) | `EconomicsV2.kt` | Motor (SPEC 2.1.0): öncüller `PRIORS`, `evaluate`, `r6`, Python yuvarlaması |
| `EconomicsV2Estimators.kt` | aynı | Tahminciler (g, bekleme, enerji) |
| `EconomicsLogs.kt` (`EconomicsLogs`, `Timeline`, `Odometer`, `EconomicsRecorder`) | aynı | SPEC §17.2 satırları: `economics-offers/legs/idle/penalties.jsonl`, bekleme v2 |
| `WaitObservation.kt` | aynı | Bekleme gözlemi (`economics-waits.jsonl`, açık beklemeler `openWaits.v1`) |
| `ShadowEvaluator.kt` (`ShadowEvaluator`, `ShadowMode`) | aynı | SPEC §19 gölge karar (teklif kartından, kararı göstermeden) |
| `OfferScore.kt` | aynı | E4: 1–10 puan, `economics-offer-scores.jsonl`, kuryenin puanı `economics-offer-ratings.jsonl` |
| `OfferGeo.kt` | aynı | Plus Code/DMS → kaba bölge, saat bloğu, haversine |
| `EnergySegments.kt` | aynı | `economics-energy.jsonl` (bacak ve boşta dönemi başına enerji satırı) |
| `RecordJson.kt:6-10` (JSON yardımcıları) | `Json.kt` | `:orders`'takiler `internal`; aynısı |
| `OrderTracker.kt:97-215` ve kancaları | `EconomicsObserver.kt` | `:orders` `OrderObserver` + `OfferAnnotator` (3. bölüm) |
| `MainActivity.kt:686-697, 741, 804-869, 1092-1093` | `OfferScorePage.kt` + `:app` `OfferScoresPage.kt` | E4 ekranı (6. bölüm) |
| `MainActivity.kt:849-867` (`offerScoreOf`, `pickRating`, 10 sn önbellek) | `OfferScoreBook.kt` | E4 okuyucusu: puanlar teklifin gününden, gün başına 10 sn önbellek, puan verince temizlenir; kart satırı |
| `OrderTracker` nesnesi (ortam: `Store`, `SystemClock`) | Android `:economics` `Economics.kt` | Süreçte tek `EconomicsObserver` + `OfferScoreBook`; ortam: `Store.log`, `Store.values`, `Shift.AndroidClock`, `SystemClock.elapsedRealtimeNanos`, scooter köprüsü `Orders.scooter` |

**Android bağlama (A4.1):** `WeemApplication.onCreate` → `ShiftHub.attach`'tan hemen sonra, `Orders.bind`'dan önce `Economics.bind(ctx)` → `Orders.bindEconomics(observer, annotator)` (ikisi de aynı `EconomicsObserver`). `:orders` `AndroidEnv.observer/annotator` her çağrıda `Orders.observer/annotator`'ı okur; bağlanmadan önce `NONE`. Akışın ilk girişi (`init`: `restart`, `restored`) bağlamadan sonra olmalı: `Application.onCreate` her servis ve Activity'den önce çalıştığı için öyle. Scooter köprüsü ayrı bağlanmaz: `Economics` ortamı `Orders.scooter`'ı okur (`Orders.bindScooter`, A5.3). Modül sınırı: `economics → core, orders` (`gradle/module-graph.properties`); `:app` `logic/economics`'i E4 için doğrudan görür.

## 2. Kayıtlar ve tercihler (değişmez; VERI_SOZLESMESI 5, 7.1)

- Gün klasörü dosyaları: `economics-offers.jsonl`, `economics-legs.jsonl`, `economics-idle.jsonl`, `economics-penalties.jsonl`, `economics-waits.jsonl`, `economics-exposure.jsonl` (`{at, pkg, stage, complete, source, demand, vehicleMode, zone, bakuHour, dispatchAvailabilityVerified: false}`, platform başına ≥ 5 sn'de bir ya da olay varken), `economics-energy.jsonl`, `economics-offer-scores.jsonl`, `economics-offer-ratings.jsonl`. Satırın günü `EconomicsRecorder.Row.day` (teklifin/dönemin Bakü günü), yoksa yazıldığı gün.
- Hepsi `BackupFiles.logNames`'te ve anonim dışa aktarmada `AnalysisExport.Kind.KEEP_ALL` (`economics-*` bütün kalır; `tools/analysis/anonymize.py` aynı kural) — `OfferScoreE4Test.recordedExportedAndShownOnTheOrdersPage`, A4.3 testleri.
- Tercihler: `economicsIdle.v1` (açık boşta dönemi; süreç ölümünde sansürlü satır), `openWaits.v1` (açık beklemeler; 10 sn'de bir ya da küme değişince), `economics.zone`, `economics.vehicle` (bekleme ve araç profili; yoksa `unknown`), `economics.appMode` (`shadow` varsayılan, `off`: gölge karar hesaplanmaz), `economics.scooterId` (yoksa `scooter-1`, kaynak `default_single_scooter`), `commission` / `commission:<pkg>` (ücret tabanı: biri varsa `gross_before_park`, yoksa `unknown`).

## 3. Kancalar (`OrderObserver`, ORDERS.md 4.14) — Rawstats satırları ve sıra

| `OrderObserver` | `OrderFlow`'daki yeri | Rawstats `OrderTracker` | `EconomicsObserver` |
|---|---|---|---|
| `restart(now, kept, dropped)` | `init`, veritabanı geri yüklenince | `:253` | `econ.restart` satırları |
| `restored()` (A4.1'de eklendi) | `init`, `initialized = true`'dan sonra, oturumlardan önce | `:259-264` | `economicsIdle.v1` → sansürlü `idle` satırı (hata `economics_idle`), `openWaits.v1` → sansürlü bekleme (hata `open_waits`); iki tercih silinir |
| `waits(o, events, now)` | `onScreen`, oturumlar yazıldıktan sonra (4.3 adım 13) | `:333-349` | bekleme nedeni (`after_delivery_observed`, son 30 sn'de ret dokunuşu → `after_decline_observed`, değilse `after_offer_disappearance`), `waitCore.observe(o, araç, bölge, Bakü saati)`, açık beklemeler, `economics-exposure.jsonl` |
| `frame(o, events, before, core, open, now)` | `onScreen`, teklif anı eklentilerinden sonra, `process`'ten önce (adım 17) | `:364-365` | `shadow.frame` (ilk tam teklif karesinde; ücret/km değişince yeniden; ret dokunuşundan sonra donar; hata `shadow_verdict`), `econ.frame`, açık boşta dönemi |
| `events(now, events, before, core, open, idleUpdate)` | nakit toplama, dokunuş, konum/varış, navigasyon bağı, elle teslim | `:467, 489, 714, 738, 788` | `econ.events` |
| `declineTap(pkg, at)` | dokunuş, "Decline" metni | `:485` | ret niyeti anı |
| `clicked(pkg, events)` (A4.1'de eklendi) | dokunuşun olayları oturumlara işlenirken | `:488` | `rejected` → `nextCause(after_decline_observed)` |
| `distance(now, m)` | `distance` | `:741` | odometre |
| `stopping(now)` | `stop`, oturumlar kapanınca | `:287` | `waitCore.stop()` satırları, açık beklemeler (zorla) |
| `stop(now, events, before, core)` | `stop`, `core.stop`'tan sonra | `:288-289` | `econ.stop`, açık boşta dönemi (zorla) |
| `cancellation(pkg)` | `markCancellationObservation` | `:211-215` | beklemeyi kır (`manual_annotation`), açık beklemeler, `nextCause(after_cancellation_user_reported)` |

- `writeRows` (`:123-135`): her satır gün dosyasına; `economics-offers` satırı → E4 puan satırı (teklif başına bir kez, süreçte 512 kimlik); `legs`/`idle` satırları → `economics-energy.jsonl` satırları (`EnergySegments.fromLegs/fromIdle`).
- Enerji bloğu: `ScooterBridge.energy(başlangıç, bitiş)` (A5.3 `ScooterBridgeImpl`; bitişsiz ya da ters pencere, köprü yok ya da hata → bütün değerler null, `not_measured`). Damga: duvar saati, `SystemClock.elapsedRealtimeNanos`, `economics.scooterId`, hava (`WeatherPort`, 90 dk taze; A6'ya kadar yok → `not_observed`).
- Gölge karar bağlamı (`shadowContext`): `courierState(now)` (açık oturumların paketleri, son 2 dk'lık fix'in kaba bölgesi ve hızı), komisyon, `economics.vehicle`, `economics.appMode`.
- **`OfferAnnotator`** (`OrderEvents.offers`, ORDERS.md 13.2): `pickupZone`/`dropoffZone` = feed v2'deki gibi (`EconomicsLogs.pickupZone/dropoffZone`; yalnız kartta Plus Code ya da DMS varsa), `score` = kartın gölge değerlendirmesinin E4 puanı (`OfferScore.of`, `OfferScore.row`'un sonra yazacağı değer; değerlendirme yoksa null), `netAzn` = rozetin net satırı (`OverlayText.net`; komisyon ayarlı değilse null).

## 4. WEEM farkları (bilerek)

- `EconomicsLogs.platformOf` / `stage` → `:orders` `Platforms.of` / `Platforms.stage` (ORDERS.md 13.3; aynı kural). `DeliveryCore.offerWindowMs`/`DECLINE_WINDOW_MS` `:orders`'ta `OfferWindows`'tan; eşitlik `OfferWindowsTest`'te (`PRIORS`).
- Feed v1/v2 ve YolGo akışları yok (S7): `EconomicsLogs.feedOfferV2`, `courierState` dışa verilmez (yalnız gölge karar bağlamı); `OfferFeedContract.restartedAtMs` yerine `OrderFlow.restartedAtMs`.
- `EconomicsLogs.weather`: Kotlin'in modül dışı akıllı dönüşüm kuralı için `w.rainMm!!` (davranış aynı).
- Hız: `LogStore.Loc.speedMs` (A4.1'de eklendi; Rawstats `Location.hasSpeed()/speed`), `courierState.speedKmh` = m/s × 3,6; fix'in hızı yoksa null.
- Hava `WeatherPort.current()` → `OfferWeather.from` (ORDERS.md 12); A6'ya kadar `NONE`.
- Tercih okuma: `KuryelogPrefs.Values`'ta `contains` yok; `commission` sorusu `getFloat(k, NaN)` ile (OrderFlow ile aynı).
- `OrderObserver`'a iki kanca (`restored`, `clicked`) ve `frame`'e `now` eklendi: Rawstats'ta bu adımlar `OrderTracker`'ın içindeydi; WEEM'de `:orders` `:economics`'i görmediği için kanca oldu (yer ve sıra aynı).
- E4 ekranı Compose (6. bölüm); metinler aynı.

## 5. Testler (Rawstats → Kotlin, aynı girdi → aynı çıktı)

| Dosya (`logic/economics`) | Test | Kaynak |
|---|---:|---|
| `EconomicsLogsTest` | 21 | RS aynı ad |
| `EconomicsObservationTest` | 13 | RS aynı ad; 2'si feed v1 yerine `OfferSummaries` (uyarlandı) |
| `EconomicsV2GoldenTest` | 8 | RS; `golden_vectors.json` (34 teklif + 8 yardımcı vektör, 6 ondalık/1e-6, Python yuvarlaması, < 5 ms) |
| `EconomicsV2PriorsTest` | 4 | RS; öncüller `ref.py` ile anahtar anahtar |
| `OfferScoreE4Test` | 8 | RS; kaynak taraması WEEM dosyalarına (`EconomicsObserver`, `BackupFiles`, `AnalysisExport`, `anonymize.py`) |
| `OfferGeoTest` | 12 | RS; `offer_geo_vectors.json` |
| `ShadowEvaluatorTest` | 11 | RS; `shadow_corpus.json` |
| `WaitObservationProcessDeathTest` | 5 | RS |
| `EnergySegmentsTest` | 4 | RS |
| `CoreLifecycleV2EconomicsTest` | 8 | RS `CoreLifecycleV2Test`'in ekonomi payı (öbür 15'i `logic/orders`) |
| `CustomerStageK34LateDeliveryLegsTest` | 7 | RS `CustomerStageK34LateDeliveryTest` (kaydedicili; `legs` ifadeleri dahil; kaydedicisiz kopya `logic/orders`) |
| `CustomerStageK34RecordLegsTest` | 1 | RS `legsRowIsNeverADelivery` |
| `VenueArrivalD9LegsTest` | 1 | RS `legsAveragesAndRecordsKeepTheGpsArrival` |
| `TripStagesE8EconomicsTest` | 2 | RS; feed/`attach` yerine `OrderStages` (uyarlandı, S7) |
| `OfferWindowsTest` | 1 | yeni (ORDERS.md 15.3): `OfferWindows` = `PRIORS` |
| `SahaEconomicsTest` | 4 | yeni: saha uçtan uca (aşağıda) |
| `OfferScorePageTest` | 3 | yeni (karakterizasyon): E4 sayfası |
| `OfferScoreBookTest` | 2 | yeni: E4 okuyucusu (teklifin günü, 10 sn önbellek, düzeltme yeni satır, kart satırı) |

- **Saha uçtan uca (`SahaEconomicsTest`):** 11–12 Ekim'in 6.481 saha karesi WEEM `OrderFlow` + `EconomicsObserver`'dan geçer; Rawstats'ın kendi sınıflarıyla `tools/shadow-fixture --economics` üretilmiş `shadow/saha_rawstats_ekonomi_2026-10-11_12.zip` ile **16 dosyanın her satırı aynı** (offers 31, legs 24, idle 23, penalties 51, waits 75, exposure, energy 84, offer-scores 31; JSON anahtar sırası hariç). Puanlar gerçekten hesaplanmış (≥ 28 puan, her teklifte gölge karar). `OrderObserver.NONE` ekonomi dosyası yazmaz. Bir kanca nedenini bozan deneme testi kırdı (bozma geri alındı).
- `:orders` `OrdersScreenTest.offerScoreLineOpensTheOfferDetails` (kart satırı), `:app` `OrdersTabTest.offerScoreTabRatesAnOrder` (Robolectric, Compose: seçici, puan sayfası, 1–10 sorusu, `economics-offer-ratings.jsonl` satırı, puan sekmesinde platform seçicisi ve "Günün tüm ayrıntıları" yok, sipariş kartında "Teklif puanı" satırı).
- Python referansı: `ref.py`'yi yeniden çalıştırmak gerekmedi (vektörler Rawstats'ınkiyle bayt bayt aynı, `golden_vectors.json` = `docs/ekonomi-v2/golden_vectors.json`).

## 6. E4 "Teklif puanı" ekranı (Rawstats `MainActivity`)

- Siparişler sekmesinde "Siparişler | Teklif puanı" seçicisi (RS `renderOrders` `segmented`); gün sayfaları ortak. Puan sekmesinde her gün sayfası: açıklama kartı (motor sürümü `EconomicsV2 2.1.0`, eşleme), "Ortalama fark (uygulama − sen)", "En büyük farklar" (en çok 3), sonra o günün siparişleri teklif anına göre: "SS:dd · platform · restoran" ve "Uygulama N · Sen M (fark ±k)" (ikisi de varsa vurgulu). Teklifsiz sipariş "Teklif görülmedi, puan yok" (dokunulmaz). Dokununca "Bu teklife puanın (1–10)" (1 · çok kötü … 10 · çok iyi); seçim `economics-offer-ratings.jsonl`'e yeni satır (`via: day_screen`), düzeltme de yeni satır; kaydedilemezse "Puan kaydedilemedi".
- Puan sekmesinde platform seçicisi ve "Günün tüm ayrıntıları" gizli, seçili platform süzgeci geçerli (RS aynı).
- Sipariş kartı, "Teklif ayrıntıları"nın ilk satırı: "Teklif puanı: Uygulama N · Sen M" (`OrdersSource.offerScoreLine`; `:app` `OrdersAccess.withOfferScores` kaynağı sarar → `OfferScoreBook.cardLine`, Android'de `Economics.scores`).
- Compose (`:app` `OfferScoresPage.kt`): seçici `SingleChoiceSegmentedButtonRow` (RS `segmented`), Bugün kartlarının altında, gün sayfalarının üstünde (RS'de Bugün kartları Ana sayfadaydı); `DayPages(scoreDay = …)` puan sekmesinde sayfa gövdesini değiştirir. Açıklama kartındaki eşleme metni "ⓘ" düğmesiyle açılır (RS `info`, ad "Açıklamayı göster"). Puan sorusu tek seçimli liste (seçili = son puanım), "İptal". Metinlerin hepsi `OfferScorePage`'te TR/AZ.
- Telefonda denenmedi; JVM'de Robolectric ile çizildi.

## 7. Açık noktalar ve riskler

1. **Telefonda denenmedi:** Android bağlama (`Economics.bind` → `Orders.bindEconomics`, akışın ilk girişinden önce; scooter `Orders.scooter`), gerçek vardiyada satırların yazılması, E4 ekranı. A3.10 (K) gölge modda Rawstats'la yan yana bakılabilir. Bağlamanın kendisi JVM'de test edilmedi (Android `Application` sırası); `SahaEconomicsTest` aynı gözlemciyi `OrderFlow.Env.observer` ile uçtan uca sınar.
2. Gölge raporu (`:orders:shadowReport`) ekonomi dosyalarının içeriğini karşılaştırmaz (iki uygulamanın teklif/sipariş kimlikleri farklı); yalnız sayar. İçerik eşitliği `SahaEconomicsTest`'te aynı karelerle kanıtlı.
3. Hava (A6) ve konum hızı gelene dek bazı alanlar null/`not_observed` (Rawstats'ta da YolGo yokken aynı).
4. `OfferSummary.score` (teklif özetinde canlı puan) Rawstats feed'inde yoktu; değer `OfferScore.row`'un sonra yazacağı puanla aynı, yalnız `:map`/`:link` (A6, A8) okuyacak. Görünür bir yerde kullanılması o görevlerde kullanıcı kararıdır.
5. `EconomicsObserver` `OrderFlow`'un kilidine güvenir (Rawstats: tek kilit); E4 okuyucuları ve `rateOffer` yalnız `LogStore` kilidini kullanır (Rawstats'ta `rateOffer` `OrderTracker` kilidindeydi; yazılan dosya ayrı, etkisi yok).
