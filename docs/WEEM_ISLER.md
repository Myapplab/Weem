# WEEM iş listesi

Kurallar: `AGENTS.md`. Durumlar: `bekliyor` · `sürüyor (<tarih saat>)` · `bitti (<tarih; PR/commit>)` · `engel (<neden>)`. (K) = kullanıcının telefonda yapacağı iş. Özellik kodları (Y/R/S/G/N) `WEEM_PLANI.md` Bölüm 3'te; kodun yeri `docs/KOD_HARITASI.md`.

**Bu dosya kısa kalır** (oturum kancası "Şu an"ı her oturumda okur): "Şu an" en çok 4 satır, satır başına ~400 karakter; eski "Son yapılan" silinir (ayrıntı `AI_HANDOFF.md` ve git'te). Bitmiş görev satırı tek satır durum; uzun açıklama commit mesajına.

## Şu an

- **Son yapılan:** 26 Ekim — S42 telefon geri bildirimi 4 (PR #25 CI yeşil → `main` `543dcdf`; APK `weem-build-16`): harita elle döndürülür, dokunuş ve elle hareket "yalnız harita" (S37 tersine), Ses+ ters yön önerisini öğrenme kapalıyken de onaylar, yeni hava simgeleri; SORU-S39-1 madde 1–3 silindi (gölge mod, içe aktarma, OSM raster); A10.7 öncesi 16 maddelik liste ve Google anahtarı yanıtı `docs/OPTIMIZASYON.md` 5–6. Önceki: S41 repo düzeni (PR #24).
- **Devam eden (yarım) iş:** A10.7 ön denetim (hata + optimizasyon taraması, yeni APK) — sürüyor (28 Ekim 04:10; dal `ccr-62d7f101-3q8w0h`).
- **Sıradaki:** kullanıcı yeni APK'yı telefonda dener (harita döndürme ve yalnız harita, Ses+ onayı, hava simgeleri; A10.4 maddeleri) ve `OPTIMIZASYON.md` 5'teki "Karar" maddeleri ile SORU-S39-1 madde 4–11 ve SORU-S39-2'yi yanıtlar; Çarşamba A10.7 büyük denetim (liste girdi).
- **Kullanıcıdan ayrıca:** Google Maps anahtarı isteğe bağlı (yeni anahtar kart ister; eski anahtar Cloud Console'dan → Actions `GOOGLE_MAPS_API_KEY`; `OPTIMIZASYON.md` 6); `weem-v1.0.0` etiketi (A10.3); metin onayları (`MAP.md` 17.3, `SORU-T1-1`); ev konumu + "Her zaman" konum izniyle otomatik vardiya denemesi; Pixel–Poco Bluetooth eşleştirmesi (S23); uygulama simgesi; kalıcı Receiver telefonu.

## Aşama 0–9 (bitti)

Ayrıntılı görev tabloları: `git show bc790c5:docs/WEEM_ISLER.md`. Ara saha denemeleri (K0.1, A3.10, A4.4, A5.5, A6.10, A7.3, A8.5, A9.3) **S28** ile kaldırıldı: sorunlar WEEM 1.0 sonrası gerçek kullanımda düzeltilir.

| Aşama | İçerik | Şartname | Bitti |
|---|---|---|---|
| 0 | Plan, kapsam, şartname haritası, açık sorular | `docs/sartname/` | 17 Ekim |
| 1 | Gradle iskeleti, kimlik ve imza, CI, kabuk (alt menü, tema, TR/AZ) | — | 17 Ekim |
| 2 | `:core`: depolama, tek vardiya/gün/ev kuralı, bildirim tablosu, çökme kaydı, modül arayüzleri | `CORE.md` | 17 Ekim |
| 3 | `:capture` + `:orders` (R1–R3, R5–R13, R15–R16, R19) | `CAPTURE.md`, `ORDERS.md` | 20 Ekim |
| 4 | `:economics` + yedek (R4, R17, R18, N3, N4, N8) | `ECONOMICS.md` | 18 Ekim |
| 5 | `:scooter` (S1–S8, R14) | `SCOOTER.md` | 18 Ekim |
| 6 | `:map` (Y1–Y12, Y15–Y21, N5) | `MAP.md` | 20 Ekim |
| 7 | YolGo kayıtlarını içe aktarma, ilk açılış kurulumu (G1–G5) | `MAP.md`, `CORE.md` | 20 Ekim |
| 8 | `:link` 2 telefon modu (N1) + hırsızlık koruması (N6) | `LINK.md` | 21 Ekim (PR #8) |
| 9 | `:remote` uzaktan kullanım (N2) | `REMOTE.md` | 21 Ekim (PR #9) |

## Aşama 10 — WEEM 1.0

| No | İş | Bitti sayılır | Model · effort | Bağımlı | Durum |
|---|---|---|---|---|---|
| A10.1 | Optimizasyon: pil, bellek, APK boyutu, arka planda kalma | Ölçümler belgede | Opus · high | A7.2, A8.4, A9.2 | bitti (21 Ekim; `docs/OPTIMIZASYON.md`) |
| A10.2 | Geçiş öncesi güvenlik kontrolü (S27), `/code-review high` | Bulgular kapandı | Opus · high | A10.1 | bitti (21 Ekim; PR #12; `VERI_SOZLESMESI` 9) |
| A10.3 | Geçiş rehberi (S31) + release APK (`apk.yml`) | Rehber + Release'te 1.0 APK | Sonnet · high | A10.2 | bitti (21 Ekim; `docs/GECIS_REHBERI.md`); `weem-v1.0.0` etiketi kullanıcıda: Releases → Draft a new release → `weem-v1.0.0` (hedef `main`) → Publish |
| A10.4 | (K) Geçiş (S31): arşiv ZIP → Rawstats ve YolGo kaldırılır → WEEM 1.0 temiz kurulur | İş telefonlarında WEEM | — | A10.3 | sürüyor (kullanıcı; 23 Ekim: eski uygulamalar silindi; WEEM denemesi bekleniyor, son APK `weem-build-15`) |
| A10.4a | Siparişler: çevrimiçi oturumlar + Araçlar (S20, S32) | Testler yeşil | Sonnet · high | A10.4 | bitti (21 Ekim; `ORDERS.md` 16.7) |
| A10.4b | Y13 yüzey/titreşim ölçümü + Y14 yol kalibrasyonu (S32, S33) | Testler yeşil | Opus · high | A10.4 | bitti (21 Ekim; `MAP.md` 18) |
| A10.4c | Y13 `ACTIVITY_RECOGNITION` izni + ham tanı kaydı (S34) | Testler yeşil | Sonnet · high | A10.4b | bitti (22 Ekim; `MAP.md` 18.2) |
| A10.4d | Tasarım yenileme (S35, S36): `docs/TASARIM.md`, örnekler `docs/tasarim/project/` | Bütün ekranlar yeni tasarımda | Opus · xhigh (uygulama Sonnet · high) | A10.4c | bitti (22 Ekim; PR #18) |
| A10.4e | N5 menzil uyarısı (S35) | Testler yeşil | Opus · high | A10.4c | bitti (23 Ekim; `MAP.md` 10.6) |
| A10.4f | Scooter bağlantısı koptu uyarısı + 60 sn sonra yeniden bağlanma (S18, S35) | Testler yeşil | Opus · high | A10.4c | bitti (23 Ekim; `SCOOTER.md` 14) |
| A10.4g | Ekran yönü ayarı (S35) | Testler yeşil | Sonnet · high | A10.4c | bitti (22 Ekim) |
| A10.4h | Haritanın küçük verisi haftalık yedekte (S35) | Testler yeşil | Sonnet · high | A10.4c | bitti (22 Ekim; `MAP.md` 9.13) |
| A10.4i | 2 telefonda ortak gün verisi, görevler ve hatırlatıcılar (S35) | Testler yeşil | Opus · high | A10.4c | bitti (23 Ekim; `LINK.md` 4.2) |
| A10.4j | Telefon geri bildirimi 1 (S37) | Testler yeşil | Opus · high | A10.4i | bitti (23 Ekim; PR #20) |
| A10.4k | Telefon geri bildirimi 2 (S38) | Testler yeşil | Opus · high | A10.4j | bitti (23 Ekim; PR #21) |
| A10.4l | Telefon geri bildirimi 3 (S39): gömülü harita, dokunma tepkisi, pil; sadeleştirme raporu | Testler yeşil | Opus · high | A10.4k | bitti (24 Ekim; PR #22; S40 düzeltmesi PR #23) |
| A10.4m | Telefon geri bildirimi 4 (S42): harita döndürme/yalnız harita, Ses+, hava simgeleri, SORU-S39-1 1–3 silme, A10.7 öncesi liste | Testler yeşil | Opus · high | A10.4l | bitti (26 Ekim; PR #25; `OPTIMIZASYON.md` 5) |
| A10.5 | (K) Gerçek kullanım dönemi: kullanıcı gereksiz özellikleri ve aynı motora birleşebilecekleri yazar | Kullanıcının listesi | — | A10.4 | bekliyor |
| A10.5b | Bekleyen özellikler kullanıcıya soruldu (S29, S32) | Kararlar plan Bölüm 8'de | Sonnet · medium | A10.5 | bitti (21 Ekim, erken: kullanıcı istedi) |
| A10.6 | Sadeleştirme: kod düzeyi + özellik düzeyi (kullanıcı kararıyla; kararlar Bölüm 8'e) | Testler yeşil, satır sayısı belgede | Opus · high | A10.5, A10.5b | kod düzeyi bitti (24 Ekim; `docs/SADELESTIRME.md`); özellik düzeyi SORU-S39-1'i bekliyor |
| A10.7 | Büyük denetim: güvenlik, izinler, `/code-review xhigh` (son kod üzerinde) | Bulgular kapandı | Opus · xhigh + ultracode (S13) | A10.6 | bekliyor |

WEEM 1.0 sonrası: `docs/BEKLEYEN_OZELLIKLER.md` (kullanıcı sormadan yazılmaz).

## Açık sorular

Yanıt gelince karar `WEEM_PLANI.md` Bölüm 8'e tarihle yazılır, soru buradan silinir.

| No | Soru | Neden / seçenekler | Engellediği |
|---|---|---|---|
| SORU-S39-1 | Sadeleştirme: hangi özellikler kalkar ya da birleşir? (`docs/SADELESTIRME.md` 4) — **madde 1–3 yanıtlandı ve silindi (S42)**; kalan: 4 uygulama içi harita indirme, 5 Y13/Y14, 6 uzaktan kullanım, 7 hırsızlık koruması, 8 teklif veri seti, 9 ekonomi v2, 10 modül birleştirme, 11 belgeler | Öneri: 5 kapat/varsayılan kapalı; 4, 6–8 kullanılmıyorsa kaldır | A10.6 kalan kısmı |
| SORU-S39-2 | Pil seçenekleri (`docs/SADELESTIRME.md` 5 P1–P5): rota yokken ekran kapanabilsin mi, Y13 sensörleri, OCR sıklığı, scooter kaydı uyandırma kilidi, GPS 2 Hz | Davranış değişikliği: kullanıcı kararı | — |
