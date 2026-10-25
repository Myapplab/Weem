# WEEM

**Work Efficient, Earn More** — kurye için tek Android uygulaması: harita ve navigasyon (eski YolGo), Wolt/Bolt sipariş ve kazanç kaydı (eski Rawstats), scooter paneli (eski Sürüş Paneli). Kotlin + Jetpack Compose ile sıfırdan yazılıyor.

**Durum:** WEEM 1.0 kodu hazır (cihazda denenmedi); kurulum ve geçiş adımları `docs/GECIS_REHBERI.md`. APK: GitHub Releases (`weem-build-N` ön sürümleri, `apk.yml` elle; 1.0 sürümü `weem-v*` etiketiyle).

**Derleme:** saf Kotlin mantık `logic/` ayrı build'de, her ortamda: `./gradlew -p logic test` (kısa çıktılı sarmalayıcı: `tools/test.sh`). Android (`:app` ve modüller) Android SDK ve `dl.google.com` ister: `tools/setup-android-sdk.sh` (SDK + `local.properties`), sonra `./gradlew assembleDebug` (paket `com.kuryelog.app.dev`). APK kimlik denetimi: `ci/check_identity.sh <apk> <paket>`. Modüller arası izinli bağımlılıklar: `gradle/module-graph.properties`.

| Dosya | Ne için |
|---|---|
| `AGENTS.md` | Yapay zekâ ajanlarının kuralları (`CLAUDE.md` yükler); hesaplar arası devam protokolü burada |
| `docs/KOD_HARITASI.md` | Hangi özellik hangi dosyada, değişince hangi test koşulur |
| `docs/WEEM_ISLER.md` | İş listesi ve "şu an" durumu |
| `docs/WEEM_PLANI.md` | Plan: kapsam, mimari, kurallar, yol haritası, kararlar |
| `docs/sartname/` | Şartname: özellik haritası, veri sözleşmesi, test envanteri (eski uygulamalardan) |
| `docs/GECIS_REHBERI.md` | Eski uygulamalardan WEEM 1.0'a geçiş (arşiv, kaldırma, kurulum, eşleştirmeler) |
| `docs/BEKLEYEN_OZELLIKLER.md` | WEEM 1.0'dan sonra kullanıcıya sorulacak özellikler |
| `AI_HANDOFF.md` | Oturumların kısa devir notu |
| `CHANGELOG.md` | Sürüm geçmişi |

Eski uygulamalar (şartname, salt okunur): `Myapplab/kurye_nav` — `yolgo-test` (YolGo) ve `rawstats` (Rawstats + Sürüş Paneli). WEEM 1.0'a kadar telefonda onlar kullanılır.
