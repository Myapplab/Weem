# WEEM planı — native (Kotlin + Compose), sıfırdan

Durum: **17 Ekim 2026 — karar verildi, kapsam seçildi; kod yazılmadı.** YolGo + Rawstats + Sürüş Paneli tek native uygulama **WEEM** olarak sıfırdan yazılır. Önceki "modül olarak birleştir" planı (`TEK_UYGULAMA_PLANI.md`, 10–17 Ekim) iptal; bu dosya onun yerine geçti (eski hâli git geçmişinde).

**Başvuru noktaları (şartname):** `yolgo-test` @ `870ab6e` (YolGo 1.11.0, Release `yolgo-test-78`) ve `rawstats` @ `6388c04` (Rawstats 0.25.1 / kod 33 + Sürüş Paneli modülü, Release `rawstats-35`). Eski uygulamalar WEEM 1.0'a kadar kullanılır; onlara yalnız kritik düzeltme girer.

Repo: **`Myapplab/Weem`**. Bu dosya WEEM'in tek kaynağıdır; ayrıntılı iş listesi ve "şu an" durumu `docs/WEEM_ISLER.md`'de. Oturum kuralları (hesaplar arası devam dahil): `AGENTS.md`.

---

## 1. Hedef

- Tek APK **WEEM** ("Work Efficient, Earn More"), Kotlin + Jetpack Compose; Flutter yok.
- **Aynı davranış:** eski uygulamalarda ne çalışıyorsa WEEM'de de aynı çalışır. Davranışın şartnamesi eski testler ve kodlardır (Bölüm 5).
- İki telefonda tam işlevli; **2 telefon modu** (Provider / Receiver, her telefon her rolü alabilir); **uzaktan kullanım**; altta 5 sekme.
- **WEEM 1.0 = Bölüm 3'teki "İlk sürüm" özelliklerinin tamamı** (kullanıcı kararı: hepsi kesinlikle ilk sürümde).

## 2. Değişmeyecekler (kimlik ve veri)

- `applicationId` **`com.kuryelog.app`**, imza **`ci/kuryelog-test.keystore`** (alias `kuryelogtest`): Pixel'de Rawstats'ın üstüne kurulur; erişilebilirlik ve bildirim izni, Ninebot eşleşmesi (Android Keystore anahtarları uygulama kimliğine bağlı) ve tüm kayıtlar kalır. Sürüm kodu 33'ten büyük, tek yönlü artar.
- **Servis adları aynı:** erişilebilirlik `com.kuryelog.app.WatchService`, bildirim `com.kuryelog.app.NotifService` (izin bileşen adına bağlı; değişirse izin kapanır).
- **Kayıt biçimleri aynı:** gün klasörleri `files/logs/YYYY-MM-DD/`, JSONL dosya adları ve şemaları (ekonomi v2 logları, `economics-energy.jsonl`, `notifs.jsonl`, `events.jsonl`, `gps.csv`, `crashes.jsonl` …), `courier.db`, `ninebot-logger.db`, `pairing-v1`, SharedPreferences anahtarları. İç yapı yeni olabilir; WEEM ilk açılışta eski veriyi okur, analiz araçları çalışmaya devam eder.
- **YolGo verisi** (`az.duzyol.app`): eski YolGo'dan dışa aktarma → WEEM'de içe aktarma (ev, sokak kararları, ışıklar, POI düzeltmeleri, kayıtlar, ayarlar).
- minSdk 29, target/compile 36. Debug = `applicationIdSuffix ".dev"` → eski uygulamaların yanına ayrı kurulur (gölge mod).

## 3. Özellik kapsamı (kullanıcı seçimi, 17 Ekim)

Kaynak: seçim sayfası <https://claude.ai/artifact/5RTwBJxg8JHXCgUTbLAGyD> (kayıt `secim/v1`). Her özelliğin ayrıntılı davranışı başvuru noktalarındaki kod ve testlerdir.

**İlk sürüm (58) — WEEM 1.0'da kesinlikle olacak:**
- **Harita ve navigasyon (YolGo):** Y1 Harita ekranı · Y2 İkinci harita görünümü (OSM) · Y3 Kendi rota motoru · Y4 Navigasyon · Y5 Wolt/Bolt navigasyon bağlantıları · Y6 Adres bulma · Y7 Harita verisi · Y8 Sokak kararları · Y9 Hız ve batarya tahmini · Y10 Hava durumu · Y11 Trafik ışığı duruşları · Y15 Bisiklet yolu katmanı · Y16 Restoran noktaları · Y17 İstatistik ekranı · Y18 Sürüş kayıtları · Y19 Teklif veri seti ekranı · Y20 Görev kartları ve hatırlatıcılar · Y21 Bölge bilgisi
- **Siparişler ve kazanç (Rawstats):** R1 Wolt ve Bolt ekranını okuma · R2 Bildirim okuma · R3 Teklif kaydı · R4 Teklif puanı · R5 Sipariş takibi · R6 Restoran ve müşteri balonları · R7 Kurye uygulaması üstünde gösterge · R8 Nakit ve bahşiş · R9 Cepteki nakit · R10 Boost / Earn extra · R11 Çevrimiçi süreler · R12 Siparişler ve gün sayfaları · R13 Resmî değerlerle karşılaştırma · R14 Sipariş başına enerji · R15 Teklif anının havası · R16 Başka navigasyon uygulamasından mesafe · R17 Yedek ve geri yükleme · R18 Anonim analiz dışa aktarımı · R19 Uyarılar · R20 Çökme kaydı
- **Scooter (Sürüş Paneli):** S1 Scooter bağlantısı · S2 Canlı panel · S3 Sürüş kaydı · S4 Yolculuk sayacı · S5 Harici batarya · S6 Yeniden bağlanma · S7 Yüzen panel · S8 Tanı araçları
- **Genel:** G1 Tek vardiya / gün · G2 Ev konumu · G3 Dil (Türkçe / Azərbaycanca) · G4 Az bildirim · G5 İlk açılış kurulumu
- **Yeni:** N1 2 telefon modu · N2 Uzaktan kullanım · N3 Otomatik şifreli yedek · N4 Gün sonu raporu ve net kazanç · N5 Menzil uyarısı · N6 Hırsızlık koruması · N8 Günlük kazanç hedefi

**Sonra (4):** Y12 Yol kalitesi öğrenme · Y13 Yüzey ve titreşim ölçümü · Y14 Yol kalibrasyonu · N7 Sesli uyarılar → `docs/BEKLEYEN_OZELLIKLER.md`. WEEM 1.0 tamamen hazır olunca kullanıcıya sorulur; o zamana kadar yazılmaz.

**Çıkar (1):** N9 Bakım hatırlatıcı — yazılmaz.

## 4. Mimari

- Kotlin, Jetpack Compose, **tek Activity + alt menü:** Harita | Siparişler | Scooter | Uzaktan | Daha fazla. Her sekme kaldığı yerden devam eder; açılış sekmesi Harita.
- **Modüller** (mantık Android'siz saf Kotlin, üstünde ince Android katmanı):

| Modül | İçerik |
|---|---|
| `:app` | Kabuk, alt menü, izin ve ilk açılış akışı, tema, dil |
| `:core` | Ortak veri modeli, depolama (eski biçimleri okur/yazar), vardiya/gün/ev kuralları (tek kaynak), ayarlar, saat ve cihaz kimliği, bildirim numarası tablosu |
| `:capture` | Wolt/Bolt ekran ve bildirim okuma, OCR; **ağ sınıfı yok**, yalnız okur |
| `:orders` | Teklif ve sipariş takibi, balonlar, rozet, nakit/bahşiş, boost, çevrimiçi süre, resmî değerler, uyarılar |
| `:economics` | Ekonomi v2 (`docs/ekonomi-v2` ref.py + test vektörleri), teklif puanı, gün sonu ve net kazanç, menzil, hedef |
| `:scooter` | Ninebot BLE (şifreleme, oturum, eşleşme), canlı panel, kayıt, enerji, harici batarya, yüzen panel, tanı |
| `:map` | Harita (Google Maps + OSM görünümü), harita verisi, rota motoru (OSM grafı), navigasyon, adres/yer arama, sokak kararları, hava, ışıklar, bisiklet yolları, POI, istatistik, sürüş kayıtları, veri seti, görevler, bölge |
| `:link` | 2 telefon modu: Wi-Fi (hotspot) + Bluetooth, QR eşleştirme, şifreli mesajlar, kuyruk, saat farkı |
| `:remote` | Uzaktan kullanım: ekran yakalama + H.264, ayrı erişilebilirlik servisi, yalnız Wi-Fi |

- Modüller arası **dar arayüzler** (bugünkü `ScooterBridge` gibi: `orderAccepted`, `orderDone`, `energy(from, to)`, `batteryPct`). Mesaj yükleri bugünkü JSON şemaları (feed v2 `stages`, `scooter/v1/live`, `nav/v1/current`, `economics-energy` satırı): aynı mesaj tek telefonda modüller arası, 2 telefon modunda bağlantı üstünden gider.
- **2 telefon modu** (kararlar 16–17 Ekim): Provider = Wolt/Bolt telefonu (bugün Pixel): okuma, siparişler, kazanç, net kazanç. Receiver = scooter telefonu: harita, Sürüş Paneli, saniyelik scooter verisi, enerji kartı. Provider → Receiver: sipariş olayları, teklif özeti, rota hedefi, vardiya. Receiver → Provider: sipariş başına enerji sonucu, batarya özeti, rota durumu, hava. Saniyelik veri Receiver'dan çıkmaz; müşteri adı/telefonu Receiver'a gitmez. Receiver'ın interneti Provider'ın hotspot'u.
- Bağımlılıklar az ve sabit sürümlü (Compose BOM, Play Services Location, Maps SDK, WorkManager, coroutines; OSM görünümü için kütüphane Aşama 6'da seçilir). Sunucu, analitik, reklam yok.

## 5. Kurallar (ZORUNLU)

- **Şartname testlerdir:** bir modül yazılmadan önce eski testleri (Rawstats ~800, YolGo ~640, Sürüş Paneli) Kotlin'e çevrilir: aynı girdi → aynı çıktı. Yeni kod bunları geçmeden birleşmez. Saha kayıtları (ekran kareleri, günlükler) varsa altın test olur.
- **Gölge mod:** her aşamanın `.dev` sürümü eski uygulamaların yanında çalışır; gün sonunda iki kaydı karşılaştıran rapor. Fark sıfırlanmadan geçiş yok.
- **Bilinen eski hatalar baştan doğru:** Android'de derlenmeyen desen (0.25.1, ICU) → tüm Regex desenleri ICU testinden geçer; bildirim numaraları tek tabloda (eski 5103 ve nakit bahşiş çakışması yok); ev bitişi GPS kayması (F4.5b), ışık kuralı ≤1 m, A* performansı için test.
- Ekran okuma kodu ağ kullanmaz (test denetler). **WEEM Wolt/Bolt'ta kendiliğinden hiçbir şeye basmaz**; uzaktan kullanım yalnız kullanıcının dokunuşunu iletir, okuma servisi dokunma yetkisi almaz.
- `allowBackup=false` + `dataExtractionRules`; dışa aktarmada müşteri bilgisi gizlenir.
- **Derleme:** 17 Ekim'deki çalışma ortamında `dl.google.com` kapalıydı (Android SDK ve Google Maven inmiyor). Bu yüzden saf Kotlin mantık ayrı bir Gradle build'inde (`logic/`, Android eklentisi yok) durur ve her ortamda derlenip test edilir (JDK 21, Maven Central); Android katmanı CI'da derlenir. Ortam ağ ayarında `dl.google.com`'a izin verilirse yerelde tam derleme yapılır.
- **CI:** PR'da test + lint (Gradle önbellekli, `.md` hariç); APK yalnız `weem-v*` etiketi ya da elle; ara commit'ler `[skip ci]`; Actions ayda 2.000 dk (Myapplab ortak havuzu, `kurye_nav` ile paylaşılır).
- Commit: `feat:` `fix:` `perf:` `refactor:` `test:` `docs:` `build:`; bir mantıksal değişiklik = bir commit. Cihazda denenmeyene "çalışıyor" denmez.

## 6. Yol haritası

Her aşama: testler yeşil → `.dev` APK → telefonda gölge mod denemesi (kullanıcı). Süreler kabaca, oturum sayısı.

| Aşama | İçerik | Bitti sayılır | Süre |
|---|---|---|---|
| 0 | Kapsam seçimi | Bölüm 3 | bitti |
| 1 | Gradle iskeleti (`logic/` ayrı build) ve modüller; alt menü kabuğu, tema, dil; imza, sürüm, `.dev`; CI | Boş sekmeli `.dev` APK kurulur | 1–2 |
| 2 | `:core`: depolama, eski Rawstats verisini okuma, vardiya/gün/ev tek kural, saat/cihaz kimliği, bildirim tablosu | Eski kayıtlar WEEM'de okunur | 2–3 |
| 3 | `:capture` + `:orders` (R1–R3, R5–R13, R15–R16, R19–R20) | Pixel'de gölge mod: WEEM ile Rawstats aynı günü aynı kaydeder | 5–8 |
| 4 | `:economics` + yedek (R4, R17–R18, N3, N4, N8) | Altın vektörler geçer; gün sonu eski verilerle tutarlı | 2–3 |
| 5 | `:scooter` (S1–S8, R14) | Yedek telefonda Sürüş Paneli ile aynı değerler | 2–3 |
| 6 | `:map` (Y1–Y11, Y15–Y21, N5) | YolGo testleri geçer; yan yana sürüşte aynı rotalar | 8–13 |
| 7 | YolGo verisini içe aktarma; G1–G5 bütünlüğü | Yedek telefonda YolGo'dan tam taşıma | 1–2 |
| 8 | `:link` 2 telefon modu (N1) + hırsızlık koruması (N6) | İki telefonla gerçek vardiya | 4–6 |
| 9 | `:remote` uzaktan kullanım (N2) | Yakınken öteki telefon kullanılır | 3–5 |
| 10 | Optimizasyon (pil, bellek, APK boyutu, arka planda kalma), güvenlik, geri yükleme provası; **WEEM 1.0**; geçiş: ZIP yedek → WEEM Rawstats'ın üstüne → YolGo içe aktar | İş telefonlarında WEEM | 1–2 |
| 11 | `BEKLEYEN_OZELLIKLER.md` kullanıcıya sorulur | Kullanıcı kararı | — |

Toplam kabaca 30–45 oturum + telefon denemeleri. Ara `.dev` sürümleri gölge modda sahada denenir; geçiş yalnız WEEM 1.0 ile.

## 7. Riskler

- Süre uzun ve WEEM 1.0 tüm ilk sürüm özellikleriyle geliyor → iş telefonundaki geçiş geç olur. Önlem: her aşama gölge modda sahada.
- İkinci harita görünümü (Y2) ve uzaktan kullanım (N2) ek iş; Android 14+ ekran yakalama onayı, Play Protect, Xiaomi arka plan kısıtları.
- Yeniden yazımda sahada düzeltilmiş durumların kaçması → testler ve gölge mod karşılaştırması.
- Harici batarya enerjisi hâlâ ölçülmüyor (açık sorun).

## 8. Kullanıcı kararları

| Karar | Seçim |
|---|---|
| Uygulama | **WEEM**, tamamı **native, sıfırdan** (Kotlin + Compose), davranış aynı — 17 Ekim. Simge: _bekliyor_ |
| Kapsam | Bölüm 3; ilk sürüm seçilenler kesinlikle 1.0'da; sonra seçilenler `BEKLEYEN_OZELLIKLER.md`'de, 1.0 tam hazır olunca sorulur — 17 Ekim |
| Paket / imza | `com.kuryelog.app`, Rawstats anahtarı — 10 Ekim |
| İnternet | Uygulama internetli, ekran okuma kısmı ağsız — 10 Ekim |
| Açılış ekranı | Harita — 10 Ekim |
| Wolt/Bolt | Pixel 10 Pro'da (Provider) — 16 Ekim |
| Receiver interneti | Pixel'in hotspot'u — 16 Ekim |
| Pixel sürüşte | Çoğunlukla cepte — 16 Ekim |
| 2 telefon modu | Provider / Receiver; her telefon her rolü alabilir; WEEM iki telefonda tam işlevli — 17 Ekim |
| Veri bölüşümü | Provider sipariş olaylarını gönderir; scooter verisi ve enerji kartı Receiver'da; net kazanç Provider'da — 17 Ekim |
| Bağlantı | Wi-Fi (hotspot) + Bluetooth — 17 Ekim |
| Arayüz | Altta 4–5 sekme, sekme durumu korunur — 17 Ekim |
| Receiver telefonu | _bekliyor_ (önce yedek Poco/S22 ile deneme) |

## 9. İlerleme

| Tarih | Adım | Kim | Sonuç |
|---|---|---|---|
| 2026-10-10 | Modül birleşme planı (`tek-uygulama`) | Claude | 17 Ekim'de iptal |
| 2026-10-10 | Rawstats araç yükseltmesi: Gradle 9.3.1, AGP 9.1.0, Kotlin 2.4.0, SDK 36 | Codex + Claude | `rawstats`'ta; WEEM aynı sürümlerle başlar |
| 2026-10-17 | Native sıfırdan kararı; kapsam seçimi (58 ilk sürüm, 4 sonra, 1 çıkar); bu plan | Claude | `kurye_nav` oturum dalı `ccr-4d54acb0-ohiz3j` |
| 2026-10-17 | Repo `Myapplab/Weem` kuruldu: kurallar (hesaplar arası devam, model/effort), iş listesi, oturum başlangıç kancası | Claude | İlk commit |

**Sıradaki:** `docs/WEEM_ISLER.md` → "Şu an".
