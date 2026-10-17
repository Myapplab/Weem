# WEEM — AI devir notu

Kısa ve güncel; en yeni en üstte. Bölüm başına en fazla 10 satır; dosya 150 satırı geçerse en alttaki bölümler silinir (git'te kalır).

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
