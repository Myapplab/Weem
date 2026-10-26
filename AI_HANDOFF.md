# WEEM — AI devir notu

En yeni en üstte; **en çok 5 bölüm**, bölüm başına en çok 6 satır (başlık + Yapılan · Doğrulama · Açık). Yeni bölüm eklenince en alttaki silinir (eski bölümler git'te). Okurken yalnız en üst bölüm.

## 2026-10-26 — S42: telefon geri bildirimi 4 + SORU-S39-1 madde 1–3 (Claude Opus; dal `ccr-cefc95af-uz12sl`)
- Yapılan: harita iki parmakla döner; dokunuş/elle hareket "yalnız harita" (S37 tersine; `MapTab` `mapOnly`, `show-panels`), takibe dönüş hareket bitince; Ses+ ters yön önerisini öğrenme kapalıyken onaylar (`VolumeDispatch.captures`/`approveSuggestion`); yeni hava simgeleri (`WeatherGlyph`, `WeatherGlyphs.kt`); gölge mod (eşitlik testi test kaynağında), ZIP/YolGo içe aktarma (`RestoreHook`), OSM raster silindi; simge üreticisi yalnız kullanılanı yazar.
- Belgeler: `OPTIMIZASYON.md` 5 (A10.7 öncesi 16 madde, "Karar" sütunu kullanıcıda) ve 6 (Google anahtarı); plan Bölüm 8 S42.
- Doğrulama: `tools/test.sh logic app map lint:app lint:map lint:orders lint:core` yeşil (1.599 + logic:map 690); hava simgeleri Robolectric çizimiyle bakıldı. Telefonda denenmedi.
- Açık: SORU-S39-1 madde 4–11, SORU-S39-2, `OPTIMIZASYON.md` 5 madde 1/10/11 kararları; Çarşamba A10.7.

## 2026-10-25 — S41: repo düzeni ve usage tasarrufu (Claude Opus; PR #24 CI yeşil, `main` `5476f9a`)
- Yapılan: kanca `LC_ALL=C.UTF-8` (logic/ ısıtması her oturum `InvalidPathException`'la kırılıyordu) ve ~1,8 KB çıktı; `.claude/settings.json` env; `tools/test.sh` (özet + başarısız testler, güncel sayılıp koşmayanlar, daemon/429 yeniden deneme); `docs/KOD_HARITASI.md`; logic testleri kaynak taramasının okuduğu Android/tools dosyalarını girdi sayar.
- Belgeler: "Şu an" 4 satır, Aşama 0–9 özet (ayrıntı `bc790c5`), AI_HANDOFF en çok 5 bölüm, AGENTS.md tek yerde kısa belge kuralı; `tools/test-translate/` ve `.pyc` silindi.
- Doğrulama: `tools/test.sh logic` yeşil (1.931 + core 249), `map` Robolectric 10/10, `compile:core`/`lint:core`; CI #24 yeşil. Uygulama kodu değişmedi.
- Açık: `docs/sartname/bulgular/` bırakıldı (60+ başvuru); özellik düzeyi sadeleştirme SORU-S39-1'de.

## 2026-10-24 — S40: gömülü yol grafı kurulmuyordu (Claude Opus; dal `ccr-e1fa4a64-vy2iqs`)
- Yapılan: AGP `.gz` asset'leri açıp uzantıyı siliyor; `MapDataController.installBundled` artık `.gz` yoksa düz adı açar (gzip imzasına göre). Build 1–12'de gömülü graf hiç kurulmamış olabilir.
- Doğrulama: weem-build-12 APK'sından çıkarılan dosyalarla eski kod MISSING, yeni kod READY; `logic` testleri yeşil; telefonda denenmedi.
- Ayrıca (kullanıcı): Scooter sekmesinde ayarlar/ZIP düğmesi yok; vardiya kartı her yerde tek satır (`Text.SHIFT_START` silindi).
- APK `weem-build-15` (APK işi yeşil; build 13–14 kırmızıydı: `MapDataTest` yer tutucu varsayımı, Robolectric kabuk testlerinde MapLibre `UnsatisfiedLinkError` → `mapViewOrBlank`). APK işinde Robolectric gömülü grafı gerçekten kurdu (READY).
- Açık: telefonda kurulum kartının çıkmadığı denenmeli. `rememberMapData` yüklemesi kompozisyon iptal olursa yeniden denenmiyor (`MapScreenLoad.loadedRegion` önceden yazılıyor) — küçük risk, dokunulmadı.

## 2026-10-24 — A10.4l bitti: S39 telefon geri bildirimi 3 + sadeleştirme (Claude Opus; dal `ccr-bd4f7a85-wefii4`; PR #22 CI yeşil, `main` `272d1f6`; APK `weem-build-12`)
- Yapılan: gömülü altlık (`ci/build_tiles.sh`, `tools/basemap/make_style.cjs`, `map/src/main/assets/basemap/`, `logic/map` `BasemapInstaller`, `:map` `Basemap`, `MapLibreHolder` yerel stil + 60 fps); akışlı graf açma (`MapAssets.open`, `AtomicFile.write(InputStream)`), bellek eşlemeli graf/SRTM; Harita sekmesi arkada (`WeemApp`, `MapScreen(visible)`, `collectWhile`); `baseline-prof.txt`; `WatchService` içerik okuma sınırı (`CaptureTiming.CONTENT_READ_MS`); Google işaret farkı + `PoiLayerRules.selectGoogle`; `PassiveSession`/`OrderSwitch` bağlandı; ölü kod ve 2 kopya kaldırıldı.
- Doğrulama: `-p logic test` 2.177/0; `:app` 114/114, `:map` 10/10; `lintDebug` 0 hata; release yerelde 32.570.447 bayt (karosuz); telefonda denenmedi.
- CI: `build_tiles.sh` APK işinde çalıştı (2 dk); APK 70.989.967 bayt (önceki 57,8 MB → karolar ~13 MB, z0–15). İlk CI kırmızısı `CrashGuardTest` (kabuk metnini birebir arıyordu; yerelde Gradle testi güncel saymıştı → kaynak-metin testlerinde `--rerun-tasks`).
- Açık kalan: MapLibre `pmtiles://file://` ve `file://` glifleri cihazda doğrulanmadı; sipariş değişiminin 2 telefonda Provider'da da rota açması (zararsız) gözlenecek.
- Kullanıcı kararları bekliyor: SORU-S39-1 (özellik sadeleştirme), SORU-S39-2 (pil seçenekleri) — `docs/SADELESTIRME.md` 4–5.

## 2026-10-23 — A10.4k bitti: S38 telefon geri bildirimi 2 (Claude Opus; dal `ccr-5395b034-i2z288`; PR #21 CI yeşil, `main` `be6db4f`; APK `weem-build-10`)
- Yapılan: yüzey kaynak farkı (`MapLibreHolder`/`GoogleHolder`, `baseLines`, `MapLive`, `GeoJson.Builder`); `CompassSensor` + `logic/map` `CompassSmoother`, `PuckAnimator`/`PuckHeading`, `CameraEase`; `MapScreen` takip `snapshotFlow`, `CameraSpot`, `PoiLayerRules.refreshNeeded`/`SELECT_MARGIN`, `RouteLights` + `POI_LIGHT_ROUTE`; `BatteryEstimator.EXTERNAL_HOLD_MS`, `ScooterPanel.batterySub`; `MapStyles.DEFAULT=google` + `googleDefault`/`MapSettingsRepo.preferGoogle`; `apk.yml` anahtar adımı; `NavLocation` 500/1000 ms; hava hapı durağan; `CrashReport` LowMemory/ExcessiveResourceUsage.
- Doğrulama: `-p logic test` 2.164/0; `:app` 114/114, `:map` 10/10; 4 modülde lint 0 hata; telefonda denenmedi.
- Açık kalan: pusula ekseni ve gidonda yön telefonda doğrulanmadı; kapanıp açılmanın nedeni cihazda görülmedi (kart çıkarsa metni istenecek); Google anahtarı GitHub'da yoksa APK yine vektörle derlenir (günlük satırı söyler).
- CI notu: `PhoneBridgeTest.receiverDayReachesTheProvider` yarışı (Default'ta geç abonelik) CI'da iki kez kırmızıydı → `UNDISPATCHED` abonelik + zaman aşımlı bekleme.
- APK `weem-build-10` (57.805.586 bayt) hazır; Maps anahtarı Weem'in beş adında da boş (günlük `Google Maps anahtarı yok`): kullanıcı Weem → Settings → Secrets and variables → Actions'a `GOOGLE_MAPS_API_KEY` eklemeli (ya da kuruluş gizli değişkenini Weem'e açmalı), sonra APK yeniden.
- Sıradaki: kullanıcı `weem-build-10`'u telefonda dener (pusula, harita dönüşü, çizgiler, pil, Google Maps); sonra A10.5 (K) / A10.6.
