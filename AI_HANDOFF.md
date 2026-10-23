# WEEM — AI devir notu

Kısa ve güncel; en yeni en üstte. Bölüm başına en fazla 10 satır; dosya 150 satırı geçerse en alttaki bölümler silinir (git'te kalır).

## 2026-10-23 — A10.4j bitti: S37 telefon geri bildirimi (Claude Opus; dal `ccr-6f346ba4-mrxlzj`)
- Yapılan: harita araması görünür (`MapScreenLayers`), yalnız-harita kipi kalktı, tek satır `ShiftCard(floating)`, hava listesi `WSheet`; ayarlar `MoreScreens.kt` (`RoadInfoScreen`, `DailyToolsScreen`), `MapSettingsScreen(onHome)`, dataset Sipariş ayarlarında, Daha fazla `Segmented`; scooter `Scooter.pair` (canlı), `ScooterLinkRules.openAction`/`shiftEnd(owned, active)`, `ScooterPanel.badge`, konum FGS reddinde bağlantı türüne düşer; `AllExport` + `RecordExport.exportEverything`, Kayıtlar → Scooter kayıtları; `MapBaseSection` anahtarsızda Google pasif.
- Doğrulama: `-p logic test` 2.149/0; `:app` 114/114, `:map` 9/9; 3 modülde lint 0 hata; telefonda denenmedi.
- Açık kalan: Google Maps için kullanıcı GitHub'a `GOOGLE_MAPS_API_KEY` ekleyip APK'yı yeniden derletmeli; vardiya açıkken eşleştirilen scooter kendiliğinden kayda geçmez (rozete dokununca geçer).
- Sıradaki: APK (kullanıcı istedi) → A10.5 (K) liste → A10.6 sadeleştirme (kullanıcı bugün/yarın istiyor; aday liste ve tahmin sohbette).

## 2026-10-23 — A10.4e–i bitti: S35'in beş işi (Claude Opus; dal `ccr-6c3acbb6-bsytto`; PR #19 CI yeşil, `main` `e792b61`; APK `weem-build-8`)
- Yapılan: g ekran yönü (`ScreenOrientation`, `orientation`); h harita verisi haftalık yedekte (`MapBackup`, `EncryptedBackup` `map/…`, açılışta bekleyen geri yükleme); e N5 menzil uyarısı (`OfferRange`, `ShadowEvaluator.Range`, `OverlayText.rangeWarning`, `OfferSummary.rangeWarnKm`, `RangeWarnings`, `range.warn`); f S18 (b) boşta bağlantıda bildirim + 60 sn yeniden deneme (`LinkRetry.idleRetryMs`, `ScooterService.RETRY`), "veri eksik"; i `DaySnapshot`/`DayReport` R→P, `DayRuntime.setRemote`, görev/hatırlatıcı iki telefonda.
- Doğrulama: `-p logic test` 2.144/0; `:app` 111/111, `:map` 9/9; 5 modülde lint 0 hata; telefonda denenmedi.
- Açık kalan: N5 yalnız kartta Plus Code/DMS varsa (adres geokodlama kullanıcı kararı); Provider'da İstatistik ışık sorusu yereldir; `logic/map` `RangeInputs` artık kullanılmıyor (A10.6'da silinebilir).
- Sıradaki: A10.4 (K) geçiş; sonra A10.5 (K), A10.6.

## 2026-10-22 — A10.4d bitti: tasarım yenileme tamam (Claude Opus; dal `ccr-b2a13552-nsq0si`; PR #18 CI yeşil, `main` `efa3df5`)
- Yapılan: wip dal (`ccr-6a6f738a-pn3a16`) alındı ve doğrulandı; Sürüş Paneli düzeltmeleri; Uzaktan, hata/çökme kartı, sistem üstü katmanlar (rozet metni/biçimi aynı); ev seçici iğnesi; `Segmented` 48 dp; `AcceptanceTest`; `/code-review high` 10 bulgu (7 düzeltildi, 1 değişmedi, 1 belge).
- Doğrulama: `-p logic test` 2.126/0; `:app` 106/106, `:map` 9/9; 7 modülde lint 0 hata; release APK +88 KB (`main` ile yerelde karşılaştırıldı); telefonda denenmedi.
- Açık kalan: bahşiş/cep nakdi pencereleri sistem teması; `WeemTopBar` `BackHandler` ana işleyicileri gölgeliyor (bugün aynı sonuç); görsel tur Robolectric yaklaşığı.
- Sıradaki: S35 — A10.4e–i tek oturum/tek PR (g → h → e → f → i; Opus · high). Yeni kapsayıcıda Maven aynası betiğini yeniden yaz (`WEEM_ISLER.md` ortam notu).

## 2026-10-22 — A10.4d uygulama sürdü: vardiya kartı, Siparişler, Scooter (Claude Opus; dal `ccr-6a6f738a-pn3a16`, son `5324e78` wip) — kullanıcı durdurdu
- Yapılan: önceki dal (`ccr-6b7cf7c4-aiesxn` `c6c3f93`: bütün harita alt ekranları bitmişti) bu dala alındı. `f9e1097`: vardiya kartı, Siparişler sekmesinin tamamı (+`OrderRow.phase`), bileşenlere titreşim. `5324e78` (wip): Scooter sekmesi + `DashboardView`/`FloatPainter` yeni çizim, `OverlayPalette` = Palette v2 (+test), `:core` `WeemFonts` (Inter), isteğe bağlı `ScreenshotTour` (Robolectric PNG).
- Doğrulama: `f9e1097`'de `:app` 97/97, `:map` 9/9, `-p logic test` 2.125/0 kırmızı; `5324e78` yalnız derlendi + görsel tur 7 ekran çizdi (Scooter bekleme hâlinde etiket çakışması görüldü). Telefonda denenmedi; CI koşmadı (PR yok).
- Açık kalan: `WEEM_ISLER.md` "Devam eden" (1)–(7): wip doğrulama, Scooter düzeltmeleri, Uzaktan + hata kartları + sistem üstü katmanlar, tarama, kabul ölçütleri, `/code-review high`, PR/CI/`main`.
- Sıradaki: aynı satırdaki (1). Yeni kapsayıcıda Maven aynası betiğini yeniden yaz.

## 2026-10-22 — A10.4d tasarım yenileme: onaylandı (S36), uygulama yarımda — kullanıcı durdurdu (Claude Opus; dal `ccr-6b7cf7c4-aiesxn`, son `29dd7f1` wip)
- Yapılan (tasarım): `docs/TASARIM.md` + Artifact tuvali <https://claude.ai/artifact/UNcHAcWBebiWH9Xqg373W5> (kaynak `docs/tasarim/project/`); S36: önerilerin hepsi, vurgu lacivert `#2C3EA8`/`#9BB1F5`, Inter gömülü, K4 etkileşim değişiklikleri.
- Yapılan (kod, dalda): Palette v2; tema (Inter, yazı ölçeği, köşeler, renk şeması, durum çubuğu); `app/.../ui/` bileşen kitaplığı + `ComponentsTest`; 137 Material simgesi (`tools/icons/weem_icons.py`); kabuk, Daha fazla, ilk açılış, bütün ayar ekranları, Kayıtlar, Ev konumu, 2 telefon, hırsızlık, Harita HUD (arama + hava hapı, gösterge karoları, 56 dp düğmeler, gün kartı, rota kartı, bildirimler, arama, uzun basış).
- Doğrulama: `:app` 97/97 yeşil (`c703ea7`), `com.kuryelog.app.map.*` yeşil (`a25e6d1`); telefonda denenmedi; CI koşmadı (PR yok). Testlerde değişen: `SmartLocateButtonTest` boy sabiti, `RoutePanelTest` "⚠ " önekisiz metin + kapat simgesi.
- Açık kalan: `29dd7f1` derlenmiyor (yol kartları paletle; `RoadsScreen.kt`/`SurfaceScreens.kt` uyarlanacak — ayrıntı `WEEM_ISLER.md` "Devam eden"); kalan ekranlar: Yollarım ve öteki harita alt ekranları, vardiya kartı, Siparişler, Scooter sekmesi + `:scooter` çizimleri, Uzaktan, sistem üstü katmanlar; sonra `/code-review high`, PR, CI, `main`.
- Sıradaki: `WEEM_ISLER.md` "Devam eden" adım (1)–(8) (Sonnet · high; harita ve scooter çizimleri Opus · high). Yeni kapsayıcıda Maven aynası betiğini yeniden yaz.

## 2026-10-22 — A10.4c ACTIVITY_RECOGNITION izni + ham tanı kaydı (Claude Sonnet · high; dal `ccr-03d281e4-dglbwh`; S34)
- Yapılan: kullanıcı kararı. `:map` manifestine `ACTIVITY_RECOGNITION`; "Ölçüm ve kalibrasyon"da izin satırı + "İzin ver" (verilince `SurfaceRuntime.motionPermissionChanged`). Ham tanı kaydı: `logic/map` `RawDiagnostics`/`RawSample`, `SurfaceFeed.rawSink`, `files/surface-raw-v2/` (son 10 dk, `surface.raw.debug`, varsayılan kapalı, konumsuz), ekranda anahtar + "Ham JSONL kaydını paylaş". `PrivacyAuditTest`: izin yalnız `:map`'te. S15 (a), M-Y13-1 onayı plan Bölüm 8'de (S34).
- Doğrulama: `-p logic test --rerun-tasks` yeşil; `:app` derleme + lint 0 hata + `SurfaceScreensTest`; telefonda ve CI'da (PR sonucu aşağıda/ISLER'de) denenmedi.
- Açık kalan: izin penceresi ve adım dedektörünün gerçek etkisi cihazda; ilk açılış kurulumunun izin listesine eklenmedi (yalnız Ölçüm ve kalibrasyon ekranından istenir). Bekleyen kullanıcı cevapları: T1-1, S17, S18, A6.1, A6.5 (`WEEM_ISLER.md` Açık sorular).
- Sıradaki: A10.4 (K, kullanıcı geçişi) → A10.5.

## 2026-10-22 — A10.4b PR #15 kırmızısı kapandı: ekran metinleri logic/map'e (Claude Opus · high; dal `ccr-4b659265-9jxyuk`, PR #16)
- Yapılan: PR #15 dalı + `main` birleştirildi. `TextCoverageTest` (`:app`'te yerel harfli düz metin yok) için `SurfaceScreens.kt` ve `RoadCalibrationScreen.kt` metinleri `logic/map`'e: `SurfaceUiText` (TR/AZ), `SurfaceText.detailLines/impactTitle/impactSubtitle/technical/bandStatus/diagnostics/conflictLine`, `CalibrationText` sabitleri + `profileLine/levelLine/customLabel/remaining/failed` (yalnız TR, YolGo gibi). Çelişki satırı artık `roadScore` satırlarında "ve/və" aranarak bulunmuyor (`conflictLine`). `MAP.md` 18.2/18.3.
- Doğrulama: `-p logic test --rerun-tasks` 2.116 test, 0 kırmızı; `:app` ana kaynağı derlendi; `:app`/`:map` Robolectric + lint yerelde Maven Central 429 (repo.maven.apache.org ve repo1); **PR #16 CI yeşil** (13 dk) → `main` `a4ffac3` (PR #15 de birleşmiş sayıldı). Telefonda denenmedi.
- Açık kalan: önceki bölümdeki A10.4b açıkları (SORU-Y13-1, cihaz sınaması; M-Y13-1 AZ metinleri kullanıcı tarafından onaylandı, 22 Ekim).
- Sıradaki: A10.4 (K, kullanıcı geçişi) → A10.5.

## 2026-10-21 — A10.4b Y13 yüzey/titreşim + Y14 yol kalibrasyonu (Claude Sonnet · high; dal `ccr-03d281e4-dglbwh`; S33: geçiş ve telefon beklenmedi, APK yok)
- Yapılan: `MAP.md` 18. `logic/map` `surface`/`calibration`/`roads`/`ui`/`route`/`nav`: sinyal, kapı, hat (`SurfaceFeed`), işlemci, depo (`files/surface-v2/`), puanlama, kalibrasyon motoru/referanslar, E11 sokak puanları, rota maliyeti (yüzen "kapalı" işaret, düzgün yol adayı), `NavController.surfaceInputs`. `:map`: `SurfaceSensors`/`SurfaceRuntime`, `RoadCalibrationService`+`Overlay`. `:app`: Yol puanları, sokak parçaları, ayrıntı, Ölçüm ve kalibrasyon, Kalibrasyon, harita katmanı.
- Doğrulama: `logic/map` +144 test (eski Kotlin testleri 40 aynen), `:app` +5; `-p logic test` yeşil, lint 0 hata; telefonda ve CI'da denenmedi (PR sonucu ISLER'de).
- Açık kalan: cihaz sınaması (sensör hızı, yavaşlatma, yüzen panel, servis); `ACTIVITY_RECOGNITION` izni eklensin mi (SORU-Y13-1); AZ onayı M-Y13-1; ham tanı kaydı ve dayanıklı günlük taşınmadı (`MAP.md` 18.2).
- DURDURULDU (kullanıcı isteği, 21 Ekim): PR #15 CI kırmızı (`TextCoverageTest`: `:app` `SurfaceScreens.kt` 66 + `RoadCalibrationScreen.kt` 17 yerel harfli düz metin). Sıradaki adım ve doğrulama komutu `docs/WEEM_ISLER.md` → "Devam eden" satırında; yalnız `--rerun-tasks` ile doğrula.
- Sıradaki: A10.4 (K, kullanıcı geçişi) → A10.5. Not: CI `timeout-minutes` 30 → 45 yapıldı (A10.4a koşusu 30 dk sınırında iptal oldu, yeniden çalıştırınca 22 dk'da yeşil: PR #14 `main`'de).

## 2026-10-21 — A10.4a Siparişler: çevrimiçi oturumlar + Araçlar (Claude Sonnet · high; dal `ccr-03d281e4-dglbwh`)
- Yapılan: S20/S32 (WEEM 1.1). `logic/orders` `OrdersSessions` (+ `SessionsPage.cards()`) ve `OrdersTools`; `OrdersSource` `todayTrips/targets/lastSeen/markCancellationObservation`; `OrderFlow.markCancellationObservation` Boolean. `:app` `SessionsPageView`, `ToolsCard`, `SplitScreen` (350 ms sonra `LAUNCH_ADJACENT`), `OrdersAccess.withTools` (`Store.targets` + `CaptureState.seen`). Araçlar yalnız vardiya açıkken; müşteri verisi sayfada yok.
- Doğrulama: yeni testler `OrdersSessionsTest` 7, `OrdersToolsTest` 7, `OrdersTabWiringTest` +1, `:app` `OrdersTabTest` +3; `-p logic test` yeşil; `:app` lint 0 hata; CI: PR checks (logic + Android testleri, lint, APK). Telefonda denenmedi.
- Açık kalan: bölünmüş ekran üretici/Android sürümüne bağlı (bazı telefonlar `LAUNCH_ADJACENT`'i yok sayar → yönerge metni çıkar); AZ metinleri Rawstats'tan alındı (onay M-S20-1 yok, yeni metin "Gözlem kaydedilemedi. Vardiya açık olmalı." AZ onayı bekler).
- Sıradaki: A10.4 (K, kullanıcı geçişi) → A10.4b (Y13 + Y14, Opus · high) → A10.5.

## 2026-10-21 — A10.3 geçiş rehberi + WEEM 1.0 release APK (Claude Sonnet · high; dal `claude/a10-3-u-yap-olz20w`)
- Yapılan: `docs/GECIS_REHBERI.md` (arşiv ZIP'leri → kaldırma → kurulum → kurulum sihirbazı/izinler → Ninebot ve 2 telefon eşleşmesi → ilk vardiya listesi; menü yolları eski uygulamaların kaynağından). Sürüm 1.0.0 (kod 35). `apk.yml`: `weem-v*` etiketi = release APK (`weem-<sürüm>.apk` + SHA-256) Release'te; elle çalıştırma `release` girdisiyle release APK'yı da ekler; etiket ≠ `weem.versionName` ise hata.
- Doğrulama: yerelde `:app:assembleRelease` (32,0 MB, haritasız) + `ci/check_identity.sh` yeşil; `apk.yml` elle çalıştırma (release=true) yeşil → `weem-dev-6` (release APK 57 MB, harita dahil); telefonda denenmedi.
- Açık kalan: rehber cihazda izlenmedi (A10.4); "Kısıtlanmış ayarlara izin ver" menü adı üreticiye göre değişebilir; WEEM 1.0 Rawstats'la aynı paket+imza olduğundan kaldırmadan kurulursa üstüne yazar.
- Sıradaki: A10.4 (K, kullanıcı) → A10.5.

## 2026-10-21 — A10.2 geçiş öncesi güvenlik kontrolü bitti (Claude Opus · high; PR #12 CI yeşil → `main` `652cc0b`)
- Yapılan: `/code-review high` (gizlilik/izin odaklı) 8 bulgu, hepsi doğrulandı ve düzeltildi: Plus Code/çok satırlı kopyalanan metinden Receiver'a ve geokoda ad-telefon-not gitmez (`AddressParser.addressLines`, `Stop.navTarget` telefonu gizler); bildirim 12 kilit ekranında gizli; Receiver ekran/bildirim okumaz (`CaptureState.receiverRole`, TwoPhone takar); `errors.jsonl`/çökme kaydında telefon `[tel]` + 200 karakter (`:core` `Phones`, `NoteFields` ona bağlı); S5 süzgeci çökertmez (`PhoneBridge.publish` yükü `try`'da kurar); `ACTIVITY_RECOGNITION` kaldırıldı; vardiyada erişilebilirlik olayları yalnız izlenenler + arama uygulamaları (`Dialer.eventScope`).
- Kendi taramam: birleşik release manifesti (izinlerin hepsi kullanılıyor; `ACCESS_WIFI_STATE` MapLibre'den), dışa açık bileşenler, PendingIntent bayrakları (değişken olanlar açık hedefli), logcat yok, pano yalnız çökme kaydı, ağ: geokod yalnız kullanıcı/uygulama isteğiyle; ekrandan okunan adres yalnız Android `Geocoder`'a (S9, Rawstats ile aynı).
- Doğrulama: `-p logic test` 1.950 test, 0 kırmızı; Android: `:capture`, `:orders`, `:app` derlendi; `:app` 84 + `:map` 9 test yeşil (bir Robolectric testi ilk koşuda indirme hatası verdi, tek başına yeşil); `:app`/`:capture`/`:orders`/`:link` lint 0 hata; birleşik release manifesti incelendi. Telefonda denenmedi.
- Açık kalan: adla aynı satırdaki adres ve `geo:` etiketindeki ad ayıklanamaz; bildirim 12 kanal ayarı yalnız temiz kurulumda; `packageNames` kapsamı cihazda doğrulanmadı (A10.4'te ilk vardiyada ekran okuma çalışıyor mu bak).
- Sıradaki: A10.3 (geçiş rehberi + release APK).

## 2026-10-21 — A10.1 optimizasyon bitti (Claude Opus · high; dal `ccr-a451c797-hjk38y`)
- Yapılan: S30 (kullanıcı): yalnız `arm64-v8a` (`ndk.abiFilters`), release'te R8 + `isShrinkResources` (`app/proguard-rules.pro`); `ci/check_identity.sh` ABI ve dex'te `WatchService`/`NotifService` adını denetler; `rememberMapPuck` Activity STARTED değilken GPS dinlemez. Ölçüm ve arka plan envanteri `docs/OPTIMIZASYON.md`.
- Ölçüm: `.dev` 135.832.909 → 69.718.203 bayt; release 31.994.290 bayt (dex 41,2 → 5,8 MiB).
- Doğrulama: `-p logic test` yeşil; `:app` 84 test (ilk koşuda 1 test bir kez kırmızı, adı kaydedilmedi; `--rerun-tasks` ile 3 koşu yeşil — kararsız test olabilir), `:map` 9 test (+`MapPuckLifecycleTest`, düzeltmesiz kırmızı); `:app`/`:map` lint 0 hata; iki APK kimlik denetimi yeşil. Telefonda denenmedi.
- Açık kalan: R8'li release telefonda ilk kez A10.4'te açılır (sorunda `isMinifyEnabled = false`); pil/bellek sayıları telefonda (`OPTIMIZASYON.md` 4).
- Sıradaki: A10.2 (geçiş öncesi güvenlik kontrolü).

## 2026-10-21 — Aşama 8–9 sonu denetimi: `/code-review high`, 10 bulgu düzeltildi (Claude Opus · high; PR #10 → `main` `e66aafa`)
- Yapılan: uzaktan akış döndürmede donmuyor (`FlowControl.restart`, sayaçlar bağlantının); izleyen Wi-Fi adresini en çok 15 sn bekler; geç `NavTarget` Receiver'da açılmaz (zarf gönderiminden 6 sn, `NAV_OPEN_WITHIN_MS`); duran teklif tikten yenilenir; hırsızlık koruması devredeyken Poco'da PIN'siz kapatma yolu yok (`Status.guarding` kilidi: aç/kapa, rol, eşleşme, PIN, SMS; `TheftAlarm` tam ekran ve dokunuşu yutar; `ARMED`'da kartta PIN/3 sn); rol değişince koruma/SMS sıfırlanır; siren denemesi 3 sn; `LinkService` bayrağı reddedilince düşer; yeni köprü son saat farkıyla başlar; `TwoPhone` parçaları ayrı `Store.guarded`.
- Doğrulama: `-p logic test` 1.941 test, 0 kırmızı (+5 regresyon); `:app` 84 test; `:app`/`:link`/`:remote` lint 0 hata (yerelde, Maven Central 429 sonrası); PR #10 CI yeşil; APK `apk.yml` #4 yeşil → Release `weem-dev-4` (`weem-0.1.0-dev.apk`, ≈154,4 MiB). Telefonda denenmedi.
- Açık kalan: PIN 4 hane ve deneme sınırı yok (alarm ekranında kaba deneme mümkün; S10 varsayılanı); Android katmanı düzeltmeleri birim testsiz; yeni AZ metni `TheftText.LOCKED` onay bekler.
- Sıradaki: A10.1 (optimizasyon).

## 2026-10-21 — A9.2 bitti: uzaktan dokunuş, oturum işareti, bitirme (Claude Opus · high; dal `ccr-23fdbfa6-pw6gzn`)
- Yapılan: saf `logic/remote/Touch.kt` (`TouchRecorder`, `TouchPlayer`, `GlobalAction`), akışa `TOUCH`/`ACTION`/`CONTROL`, `HostMachine`/`ViewerMachine` dokunuş; `:remote` `TouchService` (ayrı erişilebilirlik servisi, yalnız `canPerformGestures`; parçalar `dispatchGesture` + `continueStroke` ile sırayla; üstte kırmızı oturum işareti, tek dokunuş paylaşımı bitirir); `RemoteHub.pointer/action/touchAvailable`; `:app` Uzaktan sekmesinde dokunuş + tuş satırı + servis durumu. `REMOTE.md` 2, `VERI_SOZLESMESI` (yeni sabit ad `com.kuryelog.app.remote.TouchService`).
- Önemli karar: dokunuş parmak kalkmadan 100 ms'lik parçalarla akar (sürükleme canlı); paylaşan bozuk/sırası kaçmış/yön değişmiş parçada ve 1 sn sessizlikte parmağı kaldırır; dokunuş yalnız paylaşım açık + doğrulanmış izleyen + oynayan görüntü + servis açıkken.
- Doğrulama: `-p logic test` 1.936 test (A8.4 ile birleşik), 0 kırmızı (`logic/remote` 82); `:app` 84 test; `:remote`/`:app` lint 0 hata (yerelde); PR #9 CI yeşil, `main`'e birleşti (`0eaf4c8`). Telefonda denenmedi.
- Açık kalan: `REMOTE.md` 2.6 (`continueStroke` cihaz farkları, Android 13+ kısıtlı ayar, işaret konumu), AZ metin onayı M-R-2; `TouchService` kurulum izin listesinde yok.
- Sıradaki: Aşama 9 sonu `/code-review high` + APK bir kez; sonra A10.1.

## 2026-10-21 — A8.4 bitti: hırsızlık koruması N6 (Claude Opus · high; PR #8, dal `ccr-23db5dd8-emcied`)
- Yapılan: S10 kararı (scooter + Poco; otomatik + elle; alarm Pixel ses/titreşim + Poco siren + ekran; bağlantı koparsa Poco SMS'i). Saf `TheftGuard` (OFF→ARMING 20 sn→ARMED→WARNING 8 sn→ALARM; tetik hız ≥4 km/s 2 sn, ivme, GPS 25 m), `TheftPin`, `TheftSms`, `TheftState`/`TheftCommand` + `PhoneBridge`; `:link` `TheftRuntime`/`TheftReceiver`; `:app` `TheftCard`, `TheftOverlay`. `LINK.md` 5, `VERI_SOZLESMESI` 5.3d.
- Not: A9.1 7404/7405'i aldı → `THEFT` = **7406**; main birleştirildi.
- Doğrulama: PR #8 CI (test + lint + derleme). Bu oturumda Maven Central/Gradle indirmesi kapalıydı; `TheftGuardTest`/`TheftSmsTest` Gradle'ın Kotlin 2.0.21'iyle elle koşuldu. Telefonda denenmedi.
- Açık kalan: AZ metin onayı (`TheftText`); Xiaomi'de servis SMS'i ve tam ekran bildirim izni; restoran yakınında uzun kırmızı ışıkta yanlış alarm riski (geri sayımda İptal, Pixel'den Kapat); tek telefon modunda koruma yok.

## 2026-10-21 — A9.1 bitti: uzaktan kullanım ekran akışı (Claude Opus · high; dal `ccr-f89752d7-vriy4p`)
- Yapılan: saf `logic/remote` (akış çerçevesi, HKDF + AES-GCM akış uçları, `remote.ask/offer/end` + geçici P-256 ECDH, `CaptureGeometry`, `AnnexB`, `FlowControl`, `HostMachine`/`ViewerMachine`, `RemoteText`); `LinkStatus.peerHost`; `:link` `LinkHub.onRemote/publishReliable/onLan/bindToLan`; `:remote` `RemoteHub`, `CaptureService` (MediaProjection + H.264), `ConsentActivity`, `RemoteNotices`, `StreamNet`, `Player`; `:app` `RemoteScreen`. `REMOTE.md` 1.
- Önemli karar: video `:link` kanalından geçmez (65 KiB ileti, onay kuyruğu, canlılık bekler) → ayrı TCP 47312, anahtar link üstünden geçici ECDH; paylaşım bir onayla açık kalır (izleyensiz 10 dk), cepteki telefonda her izlemede onay olmasın.
- Doğrulama: `-p logic test` 1.872 test, 0 kırmızı (`logic/remote` 44); `:app` 83 test, `:remote`/`:link`/`:app` lint 0 sorun (yerelde); PR #7 CI yeşil, `main`'e birleşti. Telefonda denenmedi.
- Açık kalan: `REMOTE.md` 1.9 (kilit ekranı, `FLAG_SECURE`, gecikme/bit hızı ölçülmedi), AZ metin onayı M-R-1.
- Sıradaki: A9.2 (dokunuş iletimi) ya da A8.4 (S10, önce kullanıcıya sorulur).

## 2026-10-20 — A8.3 bitti: 2 telefon roller ve mesajlar (Claude Opus · high; dal `ccr-dcc0d014-9kome5`)
- Yapılan: saf `link.PhoneBridge` (uzak `OrderEvents`/`ScooterBridge`/`NavPort`; plan 4.4 türleri `LinkTopic`, hız sınırları, Receiver vardiya izleme, hedef iletme + 8 sn yerel yedek, enerji dönüşü), `LinkPayloads` (saat düzeltmesi, S5 `ReceiverSafety`), `LinkRole` (S11), `LinkText`/`LinkView`; `ScooterBridge.late` + `EconomicsObserver.lateEnergy` (düzeltme satırı); `OrderDaySummary`, `Orders.events`; `NavController.forward`; `:link` `LinkHub` + `LinkService` (7403); `:app` `TwoPhone`, 2 telefon ekranı (ZXing + code scanner), Receiver/Provider kartları. `LINK.md` 4.
- Önemli karar: Provider uzak enerjiyi beklemez (ekonomi satırı ana iş parçacığında `runBlocking`; bekleme ANR) → satır `not_measured`, yanıt düzeltme satırı.
- Doğrulama: `-p logic test` 1.829 test (A7.2 ile birleşik), 0 kırmızı (RS `FeedChangesE8Test` 8 çevrildi); `:app` 82 + `:map` 8 Android testi yeşil, `:app`/`:link`/`:scooter` lint 0 hata (yerelde); PR #6 CI yeşil, `main`'e birleşti. Telefonda denenmedi.
- Açık kalan: `LINK.md` 4.11 (Provider'da sipariş başına Wh yok; Receiver'da arka plan vardiya başlatma kısıtı; Receiver ev kuralı), AZ metin onayı M-L-1.
- Sıradaki: A8.4 (hırsızlık koruması; başta kullanıcıya sorulur, S10).

## 2026-10-20 — A7.2 bitti: ilk açılış kurulumu, dil bütünlüğü, az bildirim (Claude Opus · high; dal `ccr-c0e88a81-nubqjp`)
- Yapılan: saf `logic/app` `Setup.kt` (`SetupStep`, `Permissions` RS sırası/"! n/m", `Vendor`/`BatteryGuide` Xiaomi·Pixel·diğer, `SetupText`); `:app` `SetupScreen` (dil → izinler → harita → pil; bir kez, `weem.setupDone`, yedeğe girmez), `PermissionsCard`/`BatteryGuideCard`, Daha fazla → İzinler (+ kurulumu yeniden göster), `MapDataStep`; G3 `logic/scooter` `ScooterStatusText` (servis bildirimi, uyarı satırı, Durum ekranda AZ; kayıt TR); G4 `NotificationAuditTest`; `BrandingK26Test` uyarlandı (2).
- Doğrulama: `-p logic test` 1.788 test, 0 kırmızı; `:app` 82 + `:map` 8 test yeşil, `:app`/`:scooter` lint 0 hata (yerelde; Maven 429 birkaç denemede geçti); PR #5 CI yeşil, `main`'e birleşti. Telefonda denenmedi.
- Açık kalan: yeni AZ metinleri onay bekler (M-G5-1); MIUI otomatik başlatma sayfası bileşen adıyla açılır (açılmazsa uygulama bilgisi) — cihazda doğrulanmadı; uygulama simgesi yok (kullanıcı).
- Sıradaki: A8.3 (Opus · high).

## 2026-10-20 — A7.1 bitti: YolGo kayıtlarını içe aktarma (Claude Opus · high; dal `ccr-1b2259ea-0dmgmm`)
- Yapılan: saf `logs.JournalImport` + `JsonPull` (YolGo `duzyol-journal-v1` akışla okunur, `files/map/logs/.import-<ns>/`'e açılır, eksiksizse ezmeden taşınır; ad kalıbı 12.4, etkin gün/sürüş atlanır), `RideLogWriter.importJournal`, `RideLogStore.isLogName`; `:app` Sürüş kayıtları ⤓ menüsü → "YolGo kayıtlarını içe aktar" (`OpenDocument`), sonuç "N kayıt eklendi · M zaten vardı". `MAP.md` 12.17.
- Doğrulama: `-p logic test` 1.767 test, 0 kırmızı (`JournalImportTest` 12); `:app` `RideLogsScreenTest` (+1) ve lint yerelde koşmadı (Maven Central 429); PR #4 CI yeşil (test + lintDebug + assemble), `main`'e birleşti. Telefonda denenmedi.
- Açık kalan: AZ metin onayı M-12-4; YolGo paylaşım menüsünden doğrudan WEEM'e gönderme (ACTION_SEND) yok, dosya seçiciyle alınır.
- Sıradaki: A7.2 ya da A8.3.

## 2026-10-20 — A6.11 bitti: Y12 yol kalitesi öğrenme; Aşama 6 `main`'de (Claude Opus · high; dal `ccr-81cb765e-vxs3px`)
- Yapılan: saf `roads` (YolGo `road_knowledge`/`road_controller`/`day_contributions`/`quality_prompt`/`street_ratio` birebir; `RoadBook`, `VolumeDispatch`, `QualityPrompt`, `LearnFixes`, `RoadsText`), `surface.MarkStore`; S26 `MapSettings.roadLearning` (varsayılan kapalı); `:map` `RoadsRuntime` (+ `MainActivity.onKeyDown/onKeyUp`, gün silme kancası, rota girdileri); `:app` kartlar, Yollarım, uzun basış satırları, Harita ayarları → Yollarım, katman. Ayrıntı `MAP.md` 9.14.
- Doğrulama: `-p logic test` 1.755 test, 0 kırmızı; `./gradlew :app:testDebugUnitTest :map:testDebugUnitTest :app:lintDebug :map:lintDebug` → `:app` 76 test, `:map` 8 test yeşil, lint 0 hata. Telefonda denenmedi; CI koşmadı.
- Açık kalan: rota sırasında "Yol yok" seçicisi, Y8 saat düzenleyicisi + onay/kara liste katmanı, `OrderEvents` (aşama ile `excludeStops`), AZ metin onayları (9.14), 2 telefon rolü (A8.3).
- Sıradaki: Aşama sonu `/code-review high` önerilir; A6.10 (K) için APK derlet; A8.3.

## 2026-10-20 — A6.9 bitti: sürüş günlüğü, İstatistik, Sürüş kayıtları, veri seti, görevler, hatırlatıcılar (Claude Sonnet · high; dal `ccr-1736f30d-of4a0s`)
- Yapılan: `logic/map` `logs` (`RideLogWriter/Store`, `RouteRecorder`, `RouteRecording`, `DayLogBridge`), `stats` (`DayStats`, `DayReportFile`, `DayHistory`, `StatsTabs`, `LightStats`, `DayDeletionPlan`…), `tasks`, `reminders` (+`ReminderController`); `logic/orders` `dataset` (`DatasetContract/Pack/DayLogSnapshot/OfferRowGate/Recorder/Shifts/Manual`); `:map` `RideLogRuntime`, `DayReportWriter`, `DayDeletionRuntime`, `TaskRuntime`, `ReminderRuntime`; `:app` `StatsScreen`, `RideLogsScreen`, `TasksScreen`+düzenleyici+`TaskAlertCard`, `ReminderSettingsSection`, `ReminderCard` (kabukta), `DatasetScreen`/`AppDatasetRuntime`/`DatasetManualSheet`; `tools/veri_seti`. Ayrıntı `MAP.md` 12.16, 13.21.
- Doğrulama: `-p logic test` 1.723 test, 24 kırmızı (yalnız A6.11 `roads` iskeleti); `:app` 72 + `:map` 8 test yeşil; `lintDebug` 0 hata; `kontrol.py` WEEM ZIP'ini geçirir. Telefonda denenmedi; CI koşmadı.
- Açık kalan: veri setinde rota/yükseklik ölçümü (`RouteMeasurePort`) yok (alanlar `unavailable`); elle ekleme AZ metinleri onay bekler; `DayDeletionRuntime.deleteContributions` ve Yollarım girişi A6.11'de; `OrderEvents` Android uygulayıcısı yok (A6.8'den); İstatistik mini haritası MapLibre OSM (cihazda denenmedi).
- Sıradaki: A6.11 (Opus · high) bu daldan; bitince dal `main`'e birleşir.

## 2026-10-20 — A3.11 gölge mod anahtarı (Claude Sonnet · high; dal `claude/a3-10-u-yap-keet02` → `main`)
- Yapılan: tercih `shadowMode` (yalnız `.dev`): açıkken nakit bahşiş/cep nakdi soruları, bildirim 12, rozet ve balon/kart pencereleri kapanır, kayıt sürer (`OrderFlow.shadowMode`, `OrdersHost`, `Orders.AndroidEnv`); Ayarlar'da anahtar; rapor tek taraflı bahşiş cevabını `order.tip_answer`/`day.tip_answer` beklenen sayar. `ORDERS.md` 16.6. Belge onarımı: CHANGELOG ve AI_HANDOFF'taki birleşme işaretleri silindi.
- Doğrulama: `-p logic :orders:test :app:test` yeşil; `:orders:compileDebugKotlin`, `:app` `OrdersSettingsScreenTest` (7) yeşil. Telefonda denenmedi; lint koşmadı.
- Açık kalan: **A3.10 (K)** kullanıcıda — `.dev` APK derlet, Pixel'de gölge mod açık 1–2 vardiya, iki ZIP + `shadowReport`. Ekran okuma uyarısı 5104 gölge modda da çıkar (beklenen fark).

## 2026-10-20 — A6.8 bitti: gün motoru, POI, bisiklet yolu, bölge bilgisi (Claude Sonnet · high; dal `ccr-e899edd4-bztcog`)
- Yapılan: `logic/map` `day` (motor `feed`/`endDay`, `LightTracker`, `LightsDb`, `SidewalkMarks`, `StopReconciler`, `RoadInfoBuilder`, `CourierStageFollower`/`StageFeed`, `DayController`, `DayText`), `graph` (`Pois`/`PoiSet`, `PoiCorrections`, güncelleme sonrası POI indirme), bisiklet (`BikewayMarksStore`, `BikewayMenu`, `BikewayLayerRules`), `ui.PoiLayerRules`/`PlaceText`; Android `DayRuntime`, `MapPois`, `MarkerKind.POI_*`, `MapScreen` (gün kartı gerçek durum, toast/titreşim, yer+ışık işaretleri, bisiklet çizgileri, uzun basış satırları, işletme penceresi), `BikewayTab`, bölge bilgisi ⓘ. `:core` `ShiftFollower.handled()`. Ayrıntı `MAP.md` 5.9, 11.19.
- Doğrulama: `-p logic test` 1.688 test, 75 kırmızı (A6.9 51, A6.11 24 iskeleti; A6.7 dalıyla birleşik); `:map`/`:app` derleniyor; yeni Robolectric testleri yeşil; `lintDebug` koşmadı (Maven 429). Telefonda denenmedi.
- Açık kalan: `OrderEvents` Android uygulayıcısı yok → `DayRuntime.bindOrders` çağrılmıyor, aşama izleyici cihazda çalışmaz; yer adı etiketleri ve bölge önizleme haritası yok; `BikewayTab` Yollarım ekranı (A6.11) gelince takılır; sürüş günlüğü/gün raporu A6.9 (`DayRuntime.onStep`).
- Sıradaki: A6.9 (Sonnet · high) bu daldan, sonra A6.11. (A6.7 başka hesapta bitti, dala birleşti.)

