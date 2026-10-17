# WEEM iş listesi

Kurallar: `AGENTS.md` → "Birden çok Claude hesabı". Durumlar: `bekliyor` · `sürüyor (<tarih saat>)` · `bitti (<commit>)` · `engel (<neden>)`. Model · effort önerisi her görevde; özellik kodları (Y/R/S/G/N) `WEEM_PLANI.md` Bölüm 3'te. (K) = kullanıcının telefonda yapacağı deneme.

## Şu an

- **Son yapılan:** 17 Ekim — proje kuruldu ve Myapplab'a taşındı; son plan kontrolü yapıldı.
- **Sıradaki iş:** A0.1 (planın son hâli, ultracode, yeni hesapta). Ardından A1.1.
- **Devam eden (yarım) iş:** A0.1 sürüyor (dal `claude/weem-plan-specification-dq4yxx`)
- **Kullanıcıdan beklenen:** K0.1 kodsuz saha denemesi; uygulama simgesi; Receiver (scooter) telefonu; her hesabın ortam ağ ayarında `dl.google.com` izni (Android'i yerelde derlemek için).

## Aşama 0 — Planın son hâli

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A0.1 | Planın son hâli (kullanıcı isteği): `docs/sartname/OZELLIK_HARITASI.md` (63 özellik → eski kod, testler, veri, bağımlılık, modül, aşama), `VERI_SOZLESMESI.md` (korunacak bileşen, izin, tercih, veritabanı, dosya, şema, anahtar adları), `TEST_ENVANTERI.md` (eski testler ve hedef modülleri); `WEEM_PLANI.md` mimari ve arayüzleri ayrıntılandırılır (Bölüm 8 kararları değişmez); bu iş listesi oturumluk görevlere bölünür; sonda "Açık sorular". Kod yazılmaz | Belgeler `main`'de; kullanıcı açık soruları yanıtladı | Opus (yoksa Sonnet) · xhigh + ultracode | sürüyor (2026-10-17 08:03 UTC) |
| K0.1 | (K) Kodsuz saha denemesi, 1–2 vardiya: yedek telefon scooter'da (Sürüş Paneli + YolGo, hedef elle), Pixel'de Wolt + Bolt + Rawstats, Pixel'deki Sürüş Paneli kapalı. Bakılacak: scooter Bluetooth'u kopuyor mu, ısınma, güneşte okunma, hotspot'un Pixel piline etkisi, **Pixel cepteyken teklifler okunuyor mu** | Kullanıcı sonuçları yazar | — | bekliyor |

## Aşama 1 — İskelet

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A1.1 | Gradle iskeleti: wrapper 9.3.1, AGP 9.1.0, Kotlin 2.4.0 (Rawstats ile aynı), sürüm kataloğu. Saf Kotlin mantık ayrı Gradle build'inde (`logic/`, Android eklentisi yok; uygulama `includeBuild` ile kullanır) — böylece `dl.google.com` kapalı ortamda da derlenip test edilir. Modüller: `:app :core :capture :orders :economics :scooter :map :link :remote` | Yerelde `logic` testleri yeşil (örnek bir test) | Opus · high | bekliyor |
| A1.2 | Kimlik: `applicationId` `com.kuryelog.app`, debug `.dev`; imza = `kurye_nav` `rawstats` `ci/kuryelog-test.keystore` (alias ve şifreler `rawstats` `app/build.gradle.kts`'teki gibi); sürüm kodu tek kaynaktan, 33'ten büyük; minSdk 29, target/compile 36; `allowBackup=false` + `dataExtractionRules`; manifestte `com.kuryelog.app.WatchService` ve `NotifService` adları (içleri boş) | CI'da `aapt2 dump badging`: paket, sürüm, imza SHA-256 Rawstats ile aynı | Opus · high | bekliyor |
| A1.3 | Kabuk: tek Activity + Compose alt menü (Harita · Siparişler · Scooter · Uzaktan · Daha fazla), her sekme durumunu korur, açılış Harita; gece/gündüz teması; Türkçe / Azərbaycanca | Kabuk testleri yeşil; `.dev` APK derlenir | Sonnet · high | bekliyor |
| A1.4 | CI: `ci.yml` (PR: `logic` testleri + Android birim testleri + lint + `assembleDebug`; Gradle önbelleği; `.md` hariç) ve `apk.yml` (`weem-v*` etiketi ya da elle → Release) | Bir yeşil koşu | Sonnet · high | bekliyor |
| A1.5 | (K) `.dev` APK yedek telefonda açılır, sekmeler arası geçişte durum korunur | Kullanıcı onayı | — | bekliyor |

## Aşama 2 — Çekirdek (`:core`)

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A2.1 | Şartname: Rawstats `Store`, `CourierDb`, `DailyRecords`, `HomeShift`, `GpsService`, `HomeRepository` + YolGo `home_day`, `shift_engine`, `rawstats_shift` → `docs/sartname/CORE.md` (davranış listesi + eski test listesi) | Belge | Opus · high | bekliyor |
| A2.2 | İlgili eski testleri Kotlin'e çevir (`logic`) | Testler derlenir, kırmızı | Sonnet · high | bekliyor |
| A2.3 | Depolama: eski biçimleri okuma/yazma (`files/logs/YYYY-MM-DD/`, JSONL şemaları, `courier.db`, tercih anahtarları) | A2.2'nin depolama testleri yeşil | Opus · high | bekliyor |
| A2.4 | Vardiya / gün / ev tek kural (G1, G2; Rawstats K38 + YolGo Y8 + Ekim planı F4), saat ve cihaz kimliği, bildirim numarası ve kanal tablosu | A2.2 testleri yeşil | Opus · high | bekliyor |
| A2.5 | (K) Eski Rawstats verisi WEEM'de okunur | Kullanıcı onayı | — | bekliyor |

## Aşama 3 — Ekran okuma ve siparişler (R1–R3, R5–R13, R15–R16, R19–R20)

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A3.1 | Şartname: `WatchService`, `CourierParser`, `OrderTracker`, `DeliveryCore`, balonlar, nakit/bahşiş, boost, resmî değerler + saha kareleri → `docs/sartname/CAPTURE.md`, `ORDERS.md` | Belge | Ultracode · xhigh (taze usage) | bekliyor |
| A3.2 | İlgili Rawstats testlerini Kotlin'e çevir | Testler derlenir, kırmızı | Sonnet · high | bekliyor |
| A3.3 | `:capture`: erişilebilirlik okuma, OCR, bildirim okuma; ağ yasağı testi; ICU desen testi | A3.2 testleri yeşil | Opus · high | bekliyor |
| A3.4 | `:orders`: teklif ve sipariş takibi, aşamalar, varış, bekleme, gecikme | Testler yeşil | Opus · high | bekliyor |
| A3.5 | `:orders`: nakit/bahşiş, cepteki nakit, boost, çevrimiçi süre, resmî değerler, başka navigasyondan mesafe | Testler yeşil | Sonnet · high | bekliyor |
| A3.6 | Siparişler sekmesi, gün sayfaları, balonlar, rozet, uyarılar, çökme kaydı | Testler yeşil | Sonnet · high | bekliyor |
| A3.7 | Gölge mod raporu (WEEM `.dev` ile Rawstats aynı günü karşılaştırır); (K) Pixel'de 1–2 vardiya | Fark yok | Opus · high | bekliyor |

## Aşama 4 — Ekonomi ve yedek (R4, R17, R18, N3, N4, N8)

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A4.1 | Ekonomi v2 (`kurye_nav` `rawstats` `docs/ekonomi-v2`: ref.py + test vektörleri) ve teklif puanı | Altın vektörler geçer | Opus · high | bekliyor |
| A4.2 | Gün sonu raporu, net kazanç, günlük kazanç hedefi | Testler yeşil | Sonnet · high | bekliyor |
| A4.3 | Yedek ve geri yükleme (ZIP), günlük paket, anonim dışa aktarma, otomatik şifreli yedek | Geri yükleme testi yeşil | Opus · high | bekliyor |
| A4.4 | (K) Gün sonu eski verilerle tutarlı | Kullanıcı onayı | — | bekliyor |

## Aşama 5 — Scooter (S1–S8, R14)

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A5.1 | Şartname + Sürüş Paneli testlerinin çevirisi (`ninebot-logger/tests`, `app/src/test`) | Testler derlenir, kırmızı | Opus · high | bekliyor |
| A5.2 | Bluetooth: şifreleme, oturum, eşleşme (`pairing-v1` ve anahtar deposu korunur), yeniden bağlanma | Testler yeşil | Opus · xhigh | bekliyor |
| A5.3 | Canlı panel, sürüş kaydı, yolculuk sayacı, harici batarya, sipariş başına enerji, yüzen panel, tanı araçları | Testler yeşil | Sonnet · high | bekliyor |
| A5.4 | (K) Yedek telefonda Sürüş Paneli ile aynı değerler | Kullanıcı onayı | — | bekliyor |

## Aşama 6 — Harita ve navigasyon (Y1–Y11, Y15–Y21, N5)

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A6.1 | Şartname: YolGo `lib/` (115 dosya) + `android_overlay` → `docs/sartname/MAP.md` | Belge | Ultracode · xhigh (taze usage) | bekliyor |
| A6.2 | YolGo Dart testlerini (~640) Kotlin'e çevir | Testler derlenir, kırmızı | Sonnet · high | bekliyor |
| A6.3 | Harita verisi: gömülü OSM verisi, Overpass güncellemesi, yükseklik, yol grafiği | Testler yeşil | Opus · high | bekliyor |
| A6.4 | Rota motoru: A*, maliyetler, bisiklet/park yolları, yaya yolunda itme, ters yön, sokak kararları | Testler yeşil; büyük haritada süre ölçümü | Opus · xhigh | bekliyor |
| A6.5 | Harita ekranı (Google Maps) + OSM görünümü (kütüphane seçimi kullanıcıya sorulur) + tema | Testler yeşil | Opus · high | bekliyor |
| A6.6 | Navigasyon (sapma, yaklaşma, varış), Wolt/Bolt bağlantıları, adres bulma | Testler yeşil | Opus · high | bekliyor |
| A6.7 | Hız ve batarya tahmini, menzil uyarısı (N5), hava | Testler yeşil | Sonnet · high | bekliyor |
| A6.8 | Trafik ışıkları, bisiklet katmanı, restoran noktaları, istatistik, sürüş kayıtları, veri seti, görevler ve hatırlatıcılar, bölge bilgisi | Testler yeşil | Sonnet · high | bekliyor |
| A6.9 | (K) Eski YolGo ile yan yana sürüş | Aynı rotalar | — | bekliyor |

## Aşama 7 — YolGo verisi ve genel (G1–G5)

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A7.1 | YolGo verisini içe aktarma (ev, sokak kararları, ışıklar, POI düzeltmeleri, kayıtlar, ayarlar). Eski YolGo'da dışa aktarma yoksa `kurye_nav`'a küçük ek gerekir: kullanıcıya sor | Yedek telefonda tam taşıma | Opus · high | bekliyor |
| A7.2 | İlk açılış kurulumu (izinler, harita verisi, Xiaomi/Pixel pil rehberi), dil, az bildirim | Testler yeşil | Sonnet · high | bekliyor |

## Aşama 8 — 2 telefon modu (N1, N6)

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A8.1 | Bağlantı: Wi-Fi (hotspot) soket + Bluetooth yedek, QR eşleştirme, şifreleme | Testler yeşil | Opus · xhigh | bekliyor |
| A8.2 | Mesajlar, kuyruk, yeniden gönderme, saat farkı, sürüm denetimi | Testler yeşil | Opus · high | bekliyor |
| A8.3 | Provider / Receiver rolleri; sekmelerde öteki telefonun verisi; enerji sonucunun dönüşü | Testler yeşil | Opus · high | bekliyor |
| A8.4 | Hırsızlık koruması (N6) | Testler yeşil | Sonnet · high | bekliyor |
| A8.5 | (K) İki telefonla gerçek vardiya | Kullanıcı onayı | — | bekliyor |

## Aşama 9 — Uzaktan kullanım (N2)

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A9.1 | Ekran yakalama + H.264 + akış (yalnız Wi-Fi) | Testler yeşil | Opus · high | bekliyor |
| A9.2 | Dokunuş iletimi (ayrı erişilebilirlik servisi), oturum işaretleri | Testler yeşil | Opus · high | bekliyor |
| A9.3 | (K) Yakınken öteki telefon kullanılır | Kullanıcı onayı | — | bekliyor |

## Aşama 10 — WEEM 1.0

| No | İş | Bitti sayılır | Model · effort | Durum |
|---|---|---|---|---|
| A10.1 | Optimizasyon: pil, bellek, APK boyutu, arka planda kalma | Ölçümler belgede | Opus · high | bekliyor |
| A10.2 | Kod sadeleştirme: tekrar eden kod, gereksiz katman, ölü kod (`/simplify`); davranış aynı, testler yeşil | Testler yeşil, satır sayısı belgede | Opus · high | bekliyor |
| A10.3 | Büyük denetim: güvenlik, izinler, `/code-review xhigh` | Bulgular kapandı | Ultracode · xhigh | bekliyor |
| A10.4 | Geri yükleme provası, geçiş rehberi | Belge + deneme | Sonnet · high | bekliyor |
| A10.5 | (K) Geçiş: ZIP yedek → WEEM Rawstats'ın üstüne → YolGo içe aktar | İş telefonlarında WEEM | — | bekliyor |

## Aşama 11 — Sonrası

| No | İş | Durum |
|---|---|---|
| A11.1 | `docs/BEKLEYEN_OZELLIKLER.md` kullanıcıya tek tek sorulur | bekliyor |
