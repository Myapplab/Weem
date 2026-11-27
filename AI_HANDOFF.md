# WEEM — AI devir notu

En yeni en üstte; **en çok 5 bölüm**, bölüm başına en çok 6 satır (başlık + Yapılan · Doğrulama · Açık). Yeni bölüm eklenince en alttaki silinir (eski bölümler git'te). Okurken yalnız en üst bölüm.

## 2026-11-28 — S45c: G3 ekranında WEEM navigasyonu + rota açma (Claude Opus; dal `claude/g3-display`, APK `weem-build-24`)
- Yapılan: `G3Display`/`G3Guide` (kart 0x23 cmd 0x71; dönüş kodları ekran testi videosundan), `RouteMeasure` sıradaki dönüş + kalan süre, `RouteProgress.guide` (iyi fix yokken plan), `G3NavBridge`; Tanı'da "G3 ekran testi"; `Planner.computeNearRoad` (60 m'de yol yoksa 250 m içindeki en yakın yoldan).
- Doğrulama: logic scooter/map yeşil (Windows'a özgü eski kırmızılar hariç); CI APK #24 yeşil; **Pixel + G3'te**: ekran testi okları, bina içinde rota açıldı ve G3'te ok/mesafe göründü.
- Açık: sürüşte denenmedi (mesafe azalması, dönüş değişimi); rota bitince G3 navigasyon ekranının kapanışı bilinmiyor (kapatma mesajı yok).

## 2026-11-28 — S45b: G3 eşleştirme 32 bayt (Claude Opus; dal `claude/g3-pair-32`, APK `weem-build-19`)
- Yapılan: güncel G3 16 baytlık SET_PWD'ye kayıtlı anahtarla yanıt veriyordu (eşleşme takılıyordu); `NinebotSession.pairPayload` artık 32 bayt (ilk 16 = anahtar, ikinci 16 = SHA-256(anahtar)[:16]; Segway'in ikinci yarısının kaynağı bilinmiyor). `PairFormatTest`.
- Doğrulama: `logic:scooter` yeşil (önceden kırık `ScooterContractTest` hariç, Windows'ta değişiklikten bağımsız); CI APK #19 yeşil; **Pixel'de denendi**: eşleşme + güç tuşu → bağlı, canlı veri.
- Açık: Segway uygulaması artık yeni anahtarla eşleşmek isteyecek (güç tuşu); `ScooterContractTest` Windows hatası incelenmedi.

## 2026-11-28 — S45: G3 ekranına harita araştırması (Claude Opus; `main`, yalnız belge)
- Yapılan: Segway verisi temizlenip yeniden eşleşirken HCI kaydı alındı ve tamamen çözüldü (SET_PWD 32 bayt, AUTH anahtarı ilk 16 bayt). Resmî navigasyon = kart `0x23` cmd `0x71` (başlat, mesafe, sokak adı); görüntü komutu yok → canlı harita stok yazılımda yok. Ayrıntı `BEKLEYEN_OZELLIKLER.md` N10, karar `WEEM_PLANI.md` S45.
- Doğrulama: yalnız kayıt çözümü (MAC'ler geçti); WEEM'den scooter'a hiçbir şey gönderilmedi, kod değişmedi.
- Açık: dönüş kodları ve güncelleme sıklığı bilinmiyor (kayıt 43 sn); Segway yeniden eşleştiği için WEEM'in G3 anahtarı geçersiz olabilir. Kullanıcı şimdilik ek kayıt istemedi.

## 2026-11-05 — S44: telefon geri bildirimi 5 (Claude Opus; PR #27 CI yeşil, `main` `23d4978`, APK `weem-build-18`)
- Yapılan: MapLibre kaydırma eşiği = dokunma payı × 1,5 (dokunuş kaydırma da sayılıp takip/otomatik zoom kapanıyordu → "zoom yok, yaklaşınca yakınlaşmıyor"); `MapTab` tek ağaç (yüzey taşınmaz, GL yeniden kurulmaz) + `AnimatedVisibility`; `FollowCamera` yumuşak takip, sığdırma 650 ms; `ApproachZoom` üst 18. `HomeWatchService` (5105, MIN) + `HomeWatchPlan` boş pencere beklemesi, `startBlocked`'ta GPS yok. `DialShare`/`DialNumber`/`DialNotice` (S5 istisnası, ayar `dial_share`). `rememberMapData` iptal.
- Doğrulama: `logic` core/map/capture/link/app/orders yeşil, Android derlemesi yerelde yeşil; lint (app, map, core, capture, link) yerelde yeşil; Robolectric yerelde Maven 429, CI #27 (test + lint + Robolectric) yeşil. Telefonda denenmedi.
- Açık: rota motoru değişmedi (kullanıcıdan örnek); numara yalnız Provider → Receiver (Receiver ekran okumaz); `StreamNet.listen` bind hatası (S43'ten).

## 2026-10-28 — S43: A10.7 ön denetim (Claude Opus; PR #26, APK `weem-build-17`)
- Yapılan: `/code-review high` (app, map, scooter, link, capture, orders, core, remote) → 9 düzeltme: `LinkService` `on` bayrağı (STICKY), `StreamNet` kabul çökmesi, `ScooterService` canlı FGS reddi, `LinkRuntime` UDP yeniden açma, `TheftRuntime` PIN tuzsuz, `ScooterShiftLink` 4902, `WatchService.onFailure` guarded, `OrdersHost` rozet 30 sn; `TheftGuard.sirenOn` + test. `MapScreen` poi seçimi `produceState`+Default. `apk.yml` elle: `weem-<sürüm>-build-N.apk`.
- Doğrulama: `logic:link` yeşil (197); Android derlemesi yerelde Maven Central 429 → yalnız CI. Telefonda denenmedi.
- Açık: `StreamNet.listen` bind hatası `HostMachine`'e bildirilmiyor; regresyon testleri Android düzeltmeleri için yok (Robolectric servis testi yazılabilir); `weem-build-16` hiç indirilmemişti (indirme sayısı 0), neden bilinmiyor.
