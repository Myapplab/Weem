# WEEM — AI devir notu

Kısa ve güncel; en yeni en üstte. Bölüm başına en fazla 10 satır; dosya 150 satırı geçerse en alttaki bölümler silinir (git'te kalır).

## 2026-10-17 — A3.3 testleri çevir: orders para, platform, rozet, balon, gün sayfası (Claude Sonnet · high; dal `ccr-e90bca59-fxabvl`)
- Yapılan: 141 Rawstats testi çevrildi — `logic/orders` 135, `logic/capture` 6 (`NoteFieldsF71Test`). Betikler `tools/test-translate/` (`cfg.py` `A33`, `gen_a33.py`, `patch_a33.py`, `fixup_a33.py`). Yeni iskeletler (`TODO`): `PocketCash`, `CashPending`, `PlatformTotals`, `RestaurantBubble`, `CustomerCard`, `CustomerBubble`, `RecordPages` (A3.6), `OfferSummaries.of(active, offers, now, startedAtMs)` (A3.5; `OrderEvents.offers`).
- Sapmalar (`TEST_ENVANTERI` "A3.3 çeviri durumu"): 20 kaynak taraması yazılmadı (A3.7/A3.8; "yedeğe girer" üçü `:core`); `AnalysisExport` ifadeleri (`kindOf`, `isPanelLine`, `screen`, `missed_by_kuryeplus` dışa aktarımı) A4.3'e; `YolGoNav` → `RouteStatus.fromNavFeed`/`RouteStatus.Route`; "(YolGo)" → "(WEEM)", "Rawstats:" → "WEEM:", "hava bilgisi gelmedi" (AZ "hava məlumatı gəlmədi": ben seçtim, onay bekliyor); `OfferFeedV2Test` 4 test `OfferSummaries`'e, 2 test çevrilmedi (v1 bayt eşitliği, E8 değişim anahtarı).
- Doğrulama: `./gradlew -p logic compileTestKotlin` yeşil; `-p logic test` capture 100 (97 kırmızı), orders 374 (371 kırmızı), core 216 yeşil; kök Android `:orders`/`:capture` `compileDebugKotlin` yeşil. CI/telefon yok (`[skip ci]`).
- **`main`'e birleşmedi** (testler bilerek kırmızı). Dal `ccr-e90bca59-fxabvl` (kod `cd9a845`); `main`'e yalnız `WEEM_ISLER.md` + bu not gider.
- Açık kalan: A3.4 (Opus · high) sırada. A3.6 "A3.3 testleri yeşil" = balon/rozet/gün sayfası kodu dahil. S15–S17 açık (engel değil).

## 2026-10-17 — A3.2 testleri çevir: capture + orders sipariş akışı (Claude Sonnet · high; dal `ccr-e90bca59-fxabvl`)
- Yapılan: 335 Rawstats testi çevrildi — `logic/capture` 93, `logic/orders` 238, `logic/core` 4 (yeşil). Testler derlenir, `:capture`/`:orders` kırmızı. Ortak yardımcılar `logic/capture` `testFixtures` (`SahaFrames`, `RepoSource`, saha kareleri). İskelet imzalar `TODO("A3.4"/"A3.5")` (`stubber.py` ile Rawstats'tan; gövde yok). Betikler `tools/test-translate/` (README; A3.3 için aynı hat).
- Yeni adlar (A3.5 uygular): `OrderStages.of/trip` (`TripRecord` → `logic/core` `TripStages`), `NavBinding.pickupOf/preferred` + `ScreenTrail` (`RouteStatus.Target`), `NameMatch` (capture), `OwnLines`.
- Sapmalar (`TEST_ENVANTERI` "A3.2 çeviri durumu"): `ArrivalDistanceM20Test` 4 test orders'ta (belge 2); kaynak taramaları A3.7'de; `CustomerStageK34LateDeliveryTest`'teki 6 `legs` ifadesi A4.1'e; `olcumRaporu` orders'ta.
- Doğrulama: `./gradlew -p logic compileTestKotlin` yeşil; `-p logic test` yalnız capture (91/94) ve orders (238/239) kırmızı, geri kalan yeşil; kök Android `:capture`/`:orders` derlemesi yeşil. CI/telefon yok (`[skip ci]`).
- **`main`'e birleşmedi:** testler bilerek kırmızı (CI `logic/ testleri` kırar; `AGENTS.md` madde 4). Dal `ccr-e90bca59-fxabvl` (kod `f78cf22`); A3.3 ve sonrası aynı dalda. `main`'de yalnız `WEEM_ISLER.md` + bu not var.
- Açık kalan: A3.3 (Sonnet · high). S15–S17 hâlâ açık (engel değil). Kaynak taraması testleri (E2 `cardAndListShowIt`, E15 bildirim, A3.3'tekiler) Android adları belli olunca A3.7/A3.8'de.

## 2026-10-17 — A3.1 Aşama 3 şartnamesi (Claude Opus · max yazdı, kalan 6 adımı Sonnet · medium bitirdi; dal `ccr-26498e7e-jg66o9`)
- Yapılan: `docs/sartname/CAPTURE.md` (R1, R2, R16, R19 E7; 526 satır) ve `ORDERS.md` (R3 yaşam döngüsü, R5–R13, R15, `DeliveryCore` durum makinesi, `OrderTracker` → saf `OrderFlow` + Android; 729 satır). Kaynak Rawstats @ `6388c04` satır satır doğrulandı; ham `bulgular/R_capture.md`/`R_orders.md`'nin yerini aldı (README işaretlendi).
- Güncellendi: `TEST_ENVANTERI` (hedefler; 1.601 çevrilir), `WEEM_PLANI`, `OZELLIK_HARITASI` (Aşama 3 Ayrıntı bağlantıları; R19 modülü `:capture` + `:app` + `:core`; R8 bildirim 10000–14095, R19 5104), `VERI_SOZLESMESI` 7.1 A3.1 notları.
- Bilerek farklar (gölge mod "beklenen fark" sayar): bildirim 5103→5104 ve 4000–8095→10000–14095; `click.text` `redact`'lenir (Rawstats yazmıyordu: telefon JSONL'e girebiliyordu); `MotionGate`/`OfferIdentity` yazılmaz; ekranda "WEEM" adı. `weather_source` `yolgo_open_meteo` ve `nav_yolgo` dizgileri korunur; boost/hava null'ları `JSONObject.NULL`.
- Kararlar: `:capture` testleri bölünür (203 testten 93 capture, 100 orders); ortak yardımcılar `logic/capture` `testFixtures`. `OrderTracker` bölünmesi en büyük taşıma riski (`SahaTekrarTest` güvence). `:economics` kanca sırası A4.1'de birebir olmalı.
- Doğrulama: yalnız belge; kod, test ve CI yok (`[skip ci]`). Telefonda denenecek bir şey yok.
- Açık kalan: kullanıcı soruları S15 (bildirim 12'de telefon), S16 (gölge modda çift soru), S17 (ML Kit ağı) → `WEEM_ISLER.md` "Açık sorular". Sıradaki A3.2 (Sonnet · high); önce `/code-review high` önerilir.

## 2026-10-17 — A2.3, A2.4, A2.5 ard arda (Claude Opus · high, tek ajan; dal `claude/weem-a2-3-4-5-cl7hm8`, `main`'e birleşti)
- A2.3: saf `LogStore` (tek kilit, gün klasörü, olay/hata satırı), `LogTime`, `LogImporter` (önce hepsi denetlenir), `CourierSchema` (DDL birebir), `CrashFile`, `guarded`/`attempt`, `KuryelogPrefs`; `Lang` `logic/core`'a taşındı (+`CoreText`). Android: `Store`, `CourierDb`, `CrashLog`, `LogImport`, `WeemApplication`, çökme kartı, `GuardedPage` sekme sınırı, Daha fazla → Kayıtlar.
- A2.4: saf `ShiftMachine` (1.2–1.5 sırası, H4), `HomeWatchCore`, `GpsTrack` (D8; mesafe Android'de `Location.distanceBetween`), `NotificationIds`, `DeviceId`, `Clock`; Android `ShiftService`, `HomeWatch`, `HomeFixSampler`, `Home`, `Notices`; Harita sekmesinde vardiya kartı, Daha fazla → Ev konumu. Testler: controller 1–3 uçtan uca (izleyici → servis), H4, F45b `busy`, kaynak taramaları.
- A2.5: `Ports.kt` arayüzleri + `NONE`, `ShiftListeners`, `Notify`; mesaj tipleri (`OfferSummary`, `TripStages`, `NavTarget`, `RouteStatus`, `DaySummary`, `ScooterLive`, `BatterySummary`, `Energy*`, `WeatherNow`) + bugünkü feed okuyucuları; Android `ShiftHub`. Coroutines 1.9.0 eklendi.
- Doğrulama: `./gradlew -p logic test` (core 212, app 16) ve kök `test lintDebug assembleDebug assembleRelease` yerelde yeşil; `ci/check_identity.sh` iki APK'da doğru; CI: son PR'da; telefonda denenmedi (servis, izleyici, H4, içe aktarma).
- Bilerek küçük farklar (CORE.md'de): `append` günü gerçek tarih olmalı; gün duyurusu kancası A6'ya; ayar metnindeki bitiş süresi sabitten (30 sn; Rawstats "2 dakika" diyordu); ön plan bildirim metni yalnız GPS.
- A2.6 (K) bitti: `weem-dev-2` APK'da Rawstats ZIP'i içe aktarıldı, tüm kayıtlar tamam; elle vardiya ve bildirimi çalıştı (kullanıcı onayı). Açık kalan: ev kaydı + otomatik vardiya telefonda denenmedi; uygulama simgesi yok. Sıradaki ajan işi A3.1 (Opus · xhigh + ultracode).

## 2026-10-17 — A2.2 `:core` testleri (Claude Sonnet · high, tek ajan; dal `ccr-ff8b6e08-4a0i1z`, `main`'e birleşti)
- Yapılan: `logic/core` — Rawstats `:core` testleri (HomeShiftK38/E6/F45b, H4, F4.1, D8, EndedShiftMinutes, HomeRepository/Draft/Fixes, CrashReport, DailyRecords) ve YolGo `home_day_*` (aynen / F4.5b ölçekli / 1.9 fark beklentileriyle), `rawstats_shift_end` → `ShiftFollower`, `HomePickResult`, `NotifyPolicy`. Çevirileri doğrulamak için saf sınıflar eski koddan taşındı (WEEM değişiklikleri: `CrashReport` "WEEM" + `com.kuryelog.logic` kareleri, `distM` kendi haversine'i, `ShiftState`/`ShiftFollower` Dart'tan). `org.json` `compileOnly`+test (kataloğa `org-json 20240303`).
- Doğrulama: `./gradlew -p logic test` yeşil (core: 126 test). Çeviride tek hata kendi eklediğim sıçrama-testimdeydi (ilk fix sıçrama sayılmıştı); kaynak testlerin hepsi ilk seferde geçti. kök `test lintDebug assembleDebug` yerelde yeşil (ilk denemeler Maven Central 429 verdi; `--max-workers=1` ile geçti); CI koşusu yok; telefonda denenmedi.
- Açık kalan: A2.3 (depolama + R20: `Store`, `LogImporter`, `courier.db` DDL, `guarded`, çökme yazımı) ve A2.4 (`ShiftMachine`, servis, izleyici, `NotificationIds`); satırlarda neyin bittiği/kaldığı yazılı. Sıradaki A2.3 (Opus · high).

## 2026-10-17 — A2.1 Aşama 2 şartnamesi (Claude Opus · high, tek ajan; dal `claude/weem-continuation-pmd9q3`, `main`'e push)
- Yapılan: `docs/sartname/CORE.md` — G1 tek vardiya kuralı = Rawstats `HomeShift` (sabitler, durum geçişleri, başlatma/bitirme sırası, H4, ev izleyici, D8 mesafe; kaynak satırları doğrulandı), G2 ev kaydı/taslak, G4 `NotificationIds` (ekran okuma 5103→5104, nakit bahşiş 4000+→10000–14095, 7400 vardiya servisine), R20 çökme kaydı + `guarded`, depolama (gün klasörü, `logNames`, ZIP içe aktarma A2.3'e, `courier.db` DDL), test eşlemesi (Bölüm 6).
- YolGo `home_day_*` senaryoları Rawstats kuralının Python kopyasıyla koşturuldu: çoğu aynı; 6 fark (sıçrama tabanı, 120 sn çift, elle bitirme sonrası 50 m koruması, doğruluk paysız kurulma, `end_rule` 36. sn) 1.9'da beklentileriyle.
- Belge düzeltmeleri: `VERI_SOZLESMESI` 5.1 tam tercih listesi (tek kaynak; `manualPolicy` Int, `home.ui.*` tercih değil), 8.2 numaralar; `TEST_ENVANTERI` hedefleri (4 dosya başka modüle, 3 çevrilmez; çevrilecek 1.607).
- Doğrulama: yalnız belge; kod yok, CI yok. Açık kalan: iki km ölçüsü (D8 / YolGo) hangisi gösterilir — A6.1'de kullanıcıya sorulacak. Sıradaki A2.2 (Sonnet · high).

## 2026-10-17 — A1.3 kabuk (Claude Sonnet; dal `claude/weem-continuation-jj51eu`, `main`'e birleşti)
- Yapılan: `:app` Compose kabuğu — `MainActivity`, `WeemApp` (alt menü: Harita · Siparişler · Scooter · Uzaktan · Daha fazla; `SaveableStateHolder` ile sekme durumu korunur; açılış Harita), `WeemTheme`, "Daha fazla" = ayarlar (dil: telefon/TR/AZ, tema: telefon/gündüz/gece). Saf mantık yeni `logic/app` projesinde (`Tab`, `Lang.resolve`, `ThemeMode`, `Palette`+`Contrast`, `Text` iki dilli tablo). Testler: sekme/dil/tema, `PaletteContrastTest` (4,5:1), `TextCoverageTest` (`I18nCoverageTest` karşılığı; `app/` kaynağında düz TR/AZ metin yasak).
- Tercihler `kuryelog` dosyasında: `lang` (eski anahtar) + yeni `theme` (`system|light|dark`). Debug (.dev) yapıda her yer tutucu sekmede sayaç düğmesi var (A1.5 için).
- Compose BOM `2026.02.01`: yenileri (1.12+) compileSdk 37 ister, AGP 9.1.0 en çok 36 destekler; BOM yükseltmesi AGP ile birlikte yapılır.
- Doğrulama: yerelde `-p logic test`, kök `test lintDebug assembleRelease`, iki APK'da `check_identity.sh` yeşil. CI koşusu ve telefon (A1.5) yok. Menü simgeleri geçici emoji.
- Telefonda denendi (kullanıcı, A1.5): sekme sayacı korunuyor, TR/AZ, gündüz/gece, ayarlar yeniden açılışta kalıyor. Açık kalan: Aşama 2 (A2.1 şartname, Opus · high).

## 2026-10-17 — A1.4 CI (Claude; dal `ccr-49f4c0da-gcd3vx`, PR #1, `main`'e birleşti)
- Yapılan: `.github/workflows/ci.yml` (PR + elle; `.md`/`docs/` hariç; `logic/` testleri, Android `test lintDebug assembleDebug assembleRelease`, iki APK'da `ci/check_identity.sh`), `apk.yml` (`weem-v*` etiketi ya da elle → yalnız `.dev` APK, `gh release create`; elle koşuda `weem-dev-<no>` önsürüm), `lint.xml` (sürüm ve yazım uyarıları kapalı; simge uyarısı açık) + `abortOnError`.
- Doğrulama: yerelde hepsi yeşil; GitHub'da PR #1 koşusu yeşil (3 dk 24 sn). `apk.yml` henüz koşmadı (etiket/elle gerekir); telefonda denenmedi.
- Açık kalan: A1.3 kabuk. Runner'da `ANDROID_HOME` hazır SDK'yı kullanır, `setup-android-sdk.sh` eksikleri tamamlar. Simge hâlâ kullanıcıdan.

## 2026-10-17 — A1.2 kimlik ve imza (Claude; dal `ccr-2794f98b-53t67j`, `main`'e birleşti)
- Yapılan: `ci/kuryelog-test.keystore` (Rawstats'tan), `app/build.gradle.kts` signingConfig `kuryelog` → debug (`.dev`, `-dev`, etiket "WEEM dev") ve release (`com.kuryelog.app`); sürüm `gradle.properties` `weem.versionCode=34`, `weem.versionName=0.1.0` (>33 derlemede `require`). Manifest: izinler (`VERI_SOZLESMESI` 3), `allowBackup=false` + Rawstats `data_extraction_rules.xml`. `:capture`: `com.kuryelog.app.WatchService` / `NotifService` boş gövde + Rawstats `accessibility_config.xml`, `acc_desc`.
- `ci/check_identity.sh <apk> <paket>`: aapt2 (paket, sürüm, min/target), apksigner (SHA-256 = sabit pin = keystore), manifestte iki servis ve yedek ayarı. `tools/setup-android-sdk.sh`: kanca bunu zaten çağırıyordu ama dosya yoktu; SDK 36 + `local.properties`.
- Doğrulama: `assembleDebug assembleRelease` yerelde derlendi; betik iki APK'da yeşil; yanlış paket ve başka anahtarla imza denemede yakalandı. `lintDebug` 0 hata / 6 uyarı (simge yok, AGP/Kotlin yeni sürüm, "adres" yazım). `test` ve `-p logic test` yeşil. CI yok; telefonda denenmedi.
- Açık kalan: A1.4 CI (betiği çağırsın; lint ayarı Rawstats gibi `abortOnError` + `lint.xml`; `apk.yml` yalnız `.dev` dağıtsın — release WEEM 1.0'a kadar telefona kurulmamalı, Rawstats'ın yerini alır). Simge kullanıcıdan.

## 2026-10-17 — A0.2 açık soruların yanıtı + A1.1 Gradle iskeleti (Claude; dal `ccr-2e70dcaf-vhiw6n`, `main`'e birleşti)
- Yapılan: S1–S13 kararları plan Bölüm 8'e; plan, iş listesi, `sartname/` ve `BEKLEYEN_OZELLIKLER` güncellendi (Y12 1.0'da → A6.11; YolGo'ya ek yok; MapLibre; hava her telefonda; scooter yalnız Receiver'a; net/hedef tanımı). `AGENTS.md`: paralel ajan en çok 2–3 + bölüm bölüm push, "Devam promptu", "Ortam" (`dl.google.com`).
- A1.1: wrapper 9.3.1 (Rawstats'tan), `gradle/libs.versions.toml` (AGP 9.1.0, Kotlin 2.4.0, JUnit 4.13.2), `logic/` (8 modül, paket `com.kuryelog.logic.*`, grup `com.kuryelog.logic`, JVM 17), kök build `includeBuild("logic")` + `:app` ve 8 ince Android modülü (her biri `com.kuryelog.logic:<ad>`'a bağlı). Modül yönü: `gradle/module-graph.properties` + `gradle/module-graph.gradle.kts` (iki build ortak; `test`/`check` öncesi `checkModuleGraph`).
- Doğrulama: `./gradlew -p logic test` yeşil (8 test + grafik denetimi); yasak bağımlılık ve döngü denemede yakalandı; composite bağlama boş bir kopya projede denendi. Android katmanı derlenmedi (`dl.google.com` kapalı); CI yok.
- Not: Maven Central proxy üstünden ara sıra 429 veriyor → `--max-workers=1` ya da yeniden dene (kanca bunu yapıyor).
- Açık kalan: A1.2 + A1.4 (CI ilk derleme; kök Android betikleri ilk kez orada doğrulanır). `app` sürüm kodu geçici 34.

## 2026-10-17 — A0.1 planın son hâli: şartname + mimari + iş listesi (Claude; dal `claude/weem-plan-specification-dq4yxx`)
- Yapılan: `docs/sartname/OZELLIK_HARITASI.md` (63 özellik → eski kod, test, veri, bağımlılık, modül/aşama), `VERI_SOZLESMESI.md` (bileşen, izin, tercih, veritabanı, dosya, Keystore, bildirim no; 5103 ve 4000–8095 çakışması), `TEST_ENVANTERI.md` (1.761 test/denetim, 1.582'si çevrilir; betikle sayıldı), `bulgular/` (ham). Plan 4.1–4.7: `logic/` ayrı build, modül sınırları, `:core` arayüzleri, 2 telefon mesajları alan alan. İş listesi 47 oturumluk göreve bölündü; Açık sorular S1–S13.
- Önemli bulgular: YolGo'da ayar/sokak/ışık/POI için dışa aktarma yok (S1); Y12 rota maliyetini etkiliyor (S3); Rawstats'ta INTERNET izni kaldırılmış (WEEM internetli, `:capture` ağsız).
- Usage: 10 paralel okuma ajanı hesabın usage'ını ~1 saatte bitirdi; kullanıcı isteğiyle durduruldu. Sürüş Paneli ve bazı Y/R özelliklerinin ayrıntısı şartname görevlerinde (A2.1, A3.1, A5.1, A6.1, tek ajan) çıkarılacak.
- Doğrulama: yalnız belge; mekanik denetim (634 başvurudan 18'i geçersiz, test sayıları eşleşti). Kod yok, CI yok.
- Açık kalan: kullanıcı açık soruları yanıtlar (A0.2); sıradaki iş A1.1.

## 2026-10-17 — Repo Myapplab'a taşındı, son plan kontrolü (Claude; `9a4c75a`)
- Yapılan: kullanıcı repoyu `mehemmedsh/Weem` → `Myapplab/Weem` taşıdı (eski adres yönleniyor). Plana 2 telefon akış ayrıntıları, Receiver donanım ölçütleri, en kritik bilinmeyen (Pixel cepteyken teklif okuma), kod sadeleştirme adımı (A10.2), satır tahmini eklendi. İş listesine Aşama 0: A0.1 (planın son hâli, ultracode) ve K0.1 (kodsuz saha denemesi).
- Doğrulama: yalnız belge. Kod yok, CI yok.
- Açık kalan: A0.1 yeni hesapta ultracode ile; sonra A1.1.

## 2026-10-17 — Proje kuruldu (Claude; ilk commit)
- Yapılan: plan (`docs/WEEM_PLANI.md`), iş listesi (`docs/WEEM_ISLER.md`), bekleyen özellikler, kurallar (hesaplar arası devam, model/effort), oturum başlangıç kancası (`.claude/hooks/session-start.sh`).
- Kararların geçmişi: `Myapplab/kurye_nav` oturum dalı `ccr-4d54acb0-ohiz3j` (commit `007c8e3`, `05935f5`) ve özellik seçim sayfası (plan Bölüm 3).
- Doğrulama: yalnız belge + kanca yerelde çalıştırıldı. Kod yok, CI yok.
- Ortam: o oturumda `dl.google.com` kapalıydı (Android SDK ve Google Maven inmiyor); JDK 21, Gradle, Maven Central açık.
- Açık kalan: A1.1 (iskelet). Kullanıcı kararları: simge, Receiver telefonu, ortam ağ ayarında `dl.google.com`.
