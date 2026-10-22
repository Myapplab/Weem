# WEEM tasarım dili (A10.4d)

Durum: **onaylandı** (S36, 22 Ekim: önerilerin hepsi; vurgu laciverte yaklaştırıldı) — **uygulandı** (22 Ekim, A10.4d bitti; kabul ölçütleri Bölüm 14 `AcceptanceTest` ve APK +88 KB; cihazda denenmedi). Örnek ekranlar: Artifact tuvali <https://claude.ai/artifact/UNcHAcWBebiWH9Xqg373W5> (17 çalışma alanı; yalnız bu Claude hesabında açılır); kaynakları `docs/tasarim/project/` (`.dc.html`: düz HTML'e yakın, renk ve ölçüler doğrudan okunur; başka hesap tuvali açamazsa buradan okur). Bu belge eski `docs/TASARIM_PROMPTU.md`'nin yerini alır (sınırlar burada).

Kural: **davranış, veri, metin anlamı ve kimlik değişmez** (`AGENTS.md`); yalnız görünüm, yerleşim, hareket ve etkileşim değişir. Metin kuralı sürer: `:app`'te yerel harfli düz metin yok, metinler `logic/*` iki dilli tablolarda (`TextCoverageTest`). Yeni kütüphane yok: Compose Material 3 + yerel simge seti (+ isteğe bağlı gömülü yazı tipi, K2).

## 0. Kullanıcı kararları (S36, 22 Ekim)

| No | Karar | Seçilen |
|---|---|---|
| K1 | Vurgu rengi | **Lacivert**: önerilen mor, kullanıcı isteğiyle laciverte yaklaştırıldı (gündüz `#2C3EA8`, gece `#9BB1F5`; Bölüm 4). Siyah-beyaz seçilmedi (tuvalde karşılaştırma olarak duruyor) |
| K2 | Yazı tipi | **Inter** uygulamaya gömülü (tek değişken dosya, Latin + Türkçe/Azerice harfler + ₼ alt kümesi ≈ 0,2–0,4 MB; ₼, ə, ğ, ı, ş Inter'de var) |
| K3 | Örnek ekranlar | Onaylı |
| K4 | Etkileşim değişiklikleri | Hepsi (Bölüm 12) |

## 1. Ekran envanteri

Kullanım: **S** sürüşte sürekli · **V** her vardiya / sipariş arası · **G** günlük-haftalık · **A** kurulum ve ayar.

| Alan | Ekran ya da bileşen | Dosya | Kullanım |
|---|---|---|---|
| Kabuk | Alt menü (5 sekme, emoji simgeler), çökme kartı, hata kartı, hatırlatıcı kartı, görev uyarısı, hırsızlık geri sayımı ve alarmı | `WeemApp.kt`, `Guarded.kt`, `map/ReminderCard.kt`, `TheftUi.kt` | S |
| Harita | Harita + HUD: arama çubuğu, hava yuvarlağı/şeridi/ayrıntısı, sol göstergeler (batarya, rota süresi, durağa kalan, ortalama, hız kutusu, scooter ve hız sayfaları), gün kartı (katlı/açık), konum ve rota düğmeleri, rota paneli (durak, varış, yeni adres, eve dön önerisi, hata), bildirim şeritleri, ters yön ve yol kalitesi kartları, varış adresi kartı, arama katmanı, uzun basış sayfası, yer düzeltme, bölge bilgisi, harita verisi kurulum kartı | `map/MapScreen.kt`, `MapTab.kt`, `MapControls.kt`, `MapMetrics.kt`, `MapShiftActions.kt`, `MapNav.kt`, `MapDayUi.kt`, `WeatherUi.kt`, `RoadCards.kt` | S |
| Vardiya | Vardiya kartı (kapalı, açık, kesildi, ekran okuma uyarısı, scooter satırları) | `ShiftCard.kt` | V |
| Siparişler | ŞU AN kartı, Net kazanç kartı, gün sonu raporu, Wolt/Bolt kartları, Çevrimiçi oturumlar, Araçlar, Siparişler / Teklif puanı seçicisi, gün sayfaları (tarih, süzgeç, satırlar), gün ayrıntısı, sipariş kartı ve düzeltme düğmeleri, ortalama listesi, Receiver'da Provider kartı | `OrdersTab.kt`, `OrdersBlocks.kt`, `OrdersTools.kt`, `OfferScoresPage.kt`, `TwoPhoneScreen.kt` | V |
| Scooter | Sürüş Paneli: başlık + durum, eylem çubuğu, eşleştirme uyarısı, canlı gösterge (Canvas), tam ekran, PiP, tarama/kayıt/ZIP pencereleri; Scooter ayarları ve tanı; yüzen top | `ScooterTab.kt`, `ScooterSettingsScreen.kt`, `:scooter` `DashboardView.kt`, `FloatPainter.kt`, `FloatingPanel.kt` | S |
| Uzaktan | Öteki telefonun ekranı, tuşlar, paylaşım | `RemoteScreen.kt` | G |
| Harita alt ekranları | İstatistik, Sürüş kayıtları, Yollarım (Kalite, Ters yön, Yol yok, Bisiklet), Yol puanları, Ölçüm ve kalibrasyon, Kalibrasyon, Günlük işler, Teklif veri seti, Harita ayarları (görünüm, veri, Google Maps, hava, hatırlatıcılar, Yollarım), API ayarları | `map/StatsScreen.kt`, `RideLogsScreen.kt`, `RoadsScreen.kt`, `BikewayTab.kt`, `SurfaceScreens.kt`, `RoadCalibrationScreen.kt`, `TasksScreen.kt`, `DatasetScreen.kt`, `MapWeatherSettings.kt`, `MapSettingsSections.kt`, `ReminderSettingsSection.kt`, `MapApiSettingsScreen.kt` | G |
| Daha fazla | Ana liste, İzinler + pil rehberi, Ev konumu (+ haritadan seçim), Siparişler ayarları, Kayıtlar (yedek, şifreli yedek, gün kartları), 2 telefon modu (rol, QR, hırsızlık), dil, tema, sürüm | `WeemApp.kt`, `PermissionsCard.kt`, `HomeScreen.kt`, `OrdersSettingsScreen.kt`, `RecordsScreen.kt`, `TwoPhoneScreen.kt` | A |
| İlk açılış | Dil → izinler → harita verisi → pil | `SetupScreen.kt` | A |
| Sistem üstü | Wolt/Bolt üstündeki rozet, bahşiş ve cep nakdi soruları, teslim hatırlatıcısı, ekran okuma uyarısı, kalibrasyon paneli, uzaktan oturum işareti | `:orders`, `:capture`, `:map`, `:remote` | S |

Öncelik: S ve V ekranları önce (Bölüm 13).

## 2. Teşhis (dürüst)

İçerik doğru ve zengin; sorun **sunum**. Ölçümler `:app` kaynağından (22 Ekim).

1. **Üç uygulama, üç görsel dil.** Rawstats paleti (vurgu mavi `#0B5FA5`), YolGo harita paleti (mavi `#1765D1`, pembe, turuncu, degrade kutular), Sürüş Paneli/rozet paleti (yeşil `#216B57`, gri `#121212`). Aynı anlam her sekmede başka renk: "tehlike" `#B3261E` / `#D7002E` / `#AE4444`. Gece üç ayrı koyu zemin: `#0E1114`, `#0A101B`, `#121212`.
2. **Simge yok, emoji ve işaret var.** ~50 ayrı emoji/işaret simge yerine (alt menü 🗺 📋 🛵 📱 ⋯; gün kartı ⌂ ▥ ⏹ ›; harita ◎ ⌖ ➤ ⇅; scooter ⚙ 🔗 🔍 ▶ ■ ⤓; göstergeler 🔋 🪫 ⏱ 🛣 ⚡ ⏲; rota 📍 ✔ ⚠ ✕). Emoji Pixel'de ve Poco'da farklı çizilir, temaya boyanamaz, boyu ve taban çizgisi oynar. "Ucuz" görünümün en büyük nedeni bu.
3. **Hiyerarşi zayıf.** Net kazanç kartı tek kartta 5 karo satırı + 2 çizgi, hepsi aynı ağırlıkta; ŞU AN kartı düz çok satırlı metin; scooter durum satırı 4 parçalı uzun cümle; rota ayrıntısı 11 sp'de 7 değer.
4. **Yazı ölçeği yok.** Tema stillerinin yanında 23 ayrı elle yazılmış boy (8–48 sp); başlıklar bir yerde `headlineMedium`, bir yerde `titleMedium`, bir yerde 26 sp Black.
5. **Köşe, gölge, boşluk tutarsız.** 10 ayrı köşe (2–26 dp), 5 ayrı gölge, 3–20 dp iç boşluk; kart bazen Material `Card`, bazen 18/22 dp `Surface`; `:app`'te 22 elle yazılmış renk.
6. **Gezinme dağınık.** Alt ekranlarda üst çubuk yok: "Geri" yazılı düğme + başlık (`RecordsScreen`'de 14 "Geri" düğmesi). Daha fazla ekranı her satırda başlık + ayrı "Aç" düğmesi: iki okuma, küçük hedef.
7. **Aynı bileşen 5 kez.** Ayar satırı/bölüm: `WeemApp.Section/Choice`, `OrdersSettingsScreen.SettingsHeading/Row/Switch/ChoiceRow`, `ScooterSettingsScreen.Group/Section/PrefSwitch/Nav/Info`, `MapWeatherSettings.SwitchRow`, `MapSettingsSections.SectionCard`, `RoadsScreen.InfoCard`, `PermissionsCard.Info`. Her ayar ekranı başka görünür.
8. **Dokunma ve başparmak.** Önemli eylemler yazı düğmesi (`TextButton`): "Scooter'a şimdi bağlan", ekran okuma uyarısı, müşteri kartı. Gün kartı yalnız sağa kaydırınca açılır (ipucu tek "›"). Onay pencereleri ekranın ortasında, başparmaktan uzak.
9. **Durum dili yok.** Hata kırmızı düz satır + "  !"; boş durum düz metin; yükleme çoğu yerde boş ekran.
10. **Hareket ve dokunsal geri bildirim yok denecek kadar az.** Haritada 1 titreşim; alt sayfa animasyonsuz `Dialog`; aşama değişimi, yeni sipariş, kazanç artışı sessiz.
11. **Harita panelleri yarı saydam** (%90–97): yoğun haritada yazı okunurluğu düşüyor.
12. **Güneşte küçük renkli yazı.** Aşama metni turuncu `#E2580B` beyazda 3,7:1 (15 sp, 4,5:1 gerekir).

Korunacak iyi yanlar: harita önce, göstergeler solda, eylemler altta (YolGo yerleşimi doğru); test etiketleri ve içerik açıklamaları; karşıtlık testi; scooter göstergesinde sabit genişlikli rakam.

## 3. İlkeler

1. **Tek bakış.** Her ekranda tek ana şey: Harita'da harita ve aşama, Siparişler'de net kazanç, Scooter'da hız, vardiya kartında başlat/bitir. Bir saniyede okunur.
2. **Başparmak bölgesi.** Eylemler ekranın alt üçte birinde; üst kısım bilgi. Sürüş denetimleri ≥ 56 dp, öteki hedefler ≥ 48 dp, hedefler arası ≥ 8 dp.
3. **Sessiz arayüz, konuşan veri.** Gri yüzeyler + tek vurgu rengi; renk yalnız anlam için (Wolt, Bolt, aşama, uyarı, tehlike, tamam). Süs yok.
4. **Tek dil.** Tek simge seti, tek yazı ölçeği, tek köşe ve gölge ölçeği; her işin tek bileşeni.
5. **Gece ve güneş.** Gece katmanlı koyu gri (saf siyah değil); gündüz siyaha yakın yazı, ana sayılar ≥ 7:1.
6. **Hareket anlam taşır.** Değişimi anlatır (sayfa düğmenin olduğu yerden açılır, aşama rengi yumuşak geçer, kazanç sayısı yuvarlanır); 100–300 ms; telefonda animasyon kapalıysa hiç yok.

## 4. Renk

Hepsi `logic/app` `Palette`'e girer (v2); karşıtlık `PaletteContrastTest` + yeni çiftler (Bölüm 13). Aşağıdaki çiftlerin hepsi hesaplandı: metin ve ikincil metin her yüzeyde ≥ 4,5:1, ana metin `bg`/`surface` üstünde ≥ 7:1.

**Nötrler** (gündüz / gece)

| Anahtar | Gündüz | Gece | Kullanım |
|---|---|---|---|
| `bg` | `#F4F5F8` | `#0E0F13` | ekran zemini |
| `surface` | `#FFFFFF` | `#17191F` | kart, harita panelleri (tam opak) |
| `raised` | `#ECEEF2` | `#21242C` | karo, çip, iç kutu, segment zemini |
| `high` | `#E3E6EB` | `#2B2F38` | basılı, sayfa tutamağı, iskelet |
| `outline` | `#DCDFE5` | `#2E323C` | 1 dp çizgi (gece kart kenarı) |
| `text` | `#0E1015` | `#F3F4F7` | ana yazı |
| `muted` | `#565C6A` | `#A5ABB8` | ikincil yazı |

**Vurgu — lacivert** (K1, S36; önerilen mor laciverte yaklaştırıldı)

| Anahtar | Gündüz | Gece |
|---|---|---|
| `accent` (dolgu) | `#2C3EA8` | `#9BB1F5` |
| `accentInk` (dolgu üstü yazı) | `#FFFFFF` | `#0E1533` |
| `accentText` (bağlantı, seçili yazı) | `#2838A1` | `#AABFFC` |
| `accentContainer` (seçili gösterge, tonal düğme) | `#E5ECFF` | `#1F2A56` |
| `onAccentContainer` | `#1C297C` | `#DDE4F9` |

Karşıtlık: dolgu üstü yazı 8,9:1 (gündüz) / 8,5:1 (gece); vurgu yazısı her yüzeyde ≥ 8:1; `onAccentContainer` ≥ 10:1.

**Durum ve platform** (yazı / zemin; gündüz · gece)

| Anlam | Gündüz | Gece |
|---|---|---|
| tamam (`positive`) | `#0B7A43` / `#DDF4E7` | `#62D69B` / `#102E1F` |
| uyarı (`warn`) | `#8A4B00` / `#FFEDD5` | `#FFC27A` / `#382607` |
| tehlike (`danger`) | `#C4291C` / `#FDE8E6` | `#FF8A80` / `#3A1614` |
| Wolt | `#00668F` / `#E0F3FB` | `#66CCF2` / `#0A2633` |
| Bolt Food | `#0B6E3B` / `#DFF5E8` | `#66D99A` / `#0E2A1B` |

**Sipariş aşaması** (hap yazısı / hap zemini; gündüz · gece). Haritadaki `MapPalette` çizgi renkleri aynı tonlardan; hap yazısı artık her zaman ≥ 4,5:1. "Yolda" hapı nötr: lacivert vurguyla karışmasın (S36); rengi simgede.

| Aşama | Gündüz | Gece |
|---|---|---|
| yolda (restorana / müşteriye) | `text` / `raised`, simge rota mavisi `#1765D1` | `text` / `raised`, simge `#8BB5FF` |
| restoranda | `#9A3E00` / `#FFE9D9` | `#FFB37A` / `#3A220E` |
| sipariş alındı | `#0B6B47` / `#DDF4EA` | `#7FE0B3` / `#0F2E22` |
| müşteride | `#A0156E` / `#FDE4F3` | `#FF9AD5` / `#3A1430` |
| yok | `muted` / `raised` | `muted` / `raised` |

**Harita:** `MapPalette` (eğim, rota, duraklar) anlam olarak aynı kalır; yalnız gece durak/müşteri rengi `#B68CFF` → `#FF7AC6` (vurgu ve aşama renkleriyle karışmasın) ve harita panelleri `MapPalette.panel` yerine uygulama `surface`'ı. **Scooter göstergesi ve rozetler:** `:scooter` `Ink` ve `OverlayPalette` aynı tablodan beslenir (gösterge zemini = `bg`, kadran = `surface`, iz = `raised`, yay = `accent`, güç = `#B45309` gündüz / `#FFB547` gece). Wolt/Bolt üstündeki rozetin **metin biçimi değişmez** (ekran okuma kendi satırını onunla tanır, `OwnLines`).

## 5. Yazı

Yazı tipi K2: **Inter** (öneri; `res/font`'ta tek değişken dosya, kalınlık 400–800, yalnız Latin + Latin genişletilmiş + ₼ alt kümesi, OFL; Compose `FontVariation.weight`, API 26+) ya da **Roboto**. Bütün değişen sayılar sabit genişlikli rakam (`tnum`). Hiçbir yazı 12 sp altında değil.

| Stil | sp / satır | Kalınlık | Nerede |
|---|---|---|---|
| `displayXL` | 88 / 92 | 800 tnum | scooter hızı |
| `displayM` | 40 / 44 | 800 tnum | net kazanç |
| `headline` | 28 / 34 | 700 | sekme başlığı (Siparişler, Sürüş Paneli, Daha fazla) |
| `titleL` | 22 / 28 | 700 | alt ekran başlığı, sayfa/pencere başlığı, kapı notu |
| `titleM` | 18 / 24 | 600 | kart başlığı, durak adresi, aşama |
| `titleS` | 16 / 22 | 600 | liste satırı başlığı |
| `bodyL` | 16 / 24 | 400 | sürüşte gövde, arama ipucu |
| `bodyM` | 15 / 22 | 400 | gövde (varsayılan) |
| `bodyS` | 13 / 18 | 400 | ikincil satır |
| `labelL` | 15 / 20 | 600 | düğme |
| `labelM` | 13 / 16 | 600 | çip, segment, durum hapı |
| `labelS` | 12 / 16 | 600 | alt menü etiketi, karo etiketi |
| `numM` | 22 / 26 | 700 tnum | karo ve gösterge değeri |
| `numS` | 16 / 20 | 600 tnum | satırdaki tutar, süre |

Kural: büyük harf yalnız metin zaten büyükse ("ŞU AN", "BATARYA"); hiyerarşi kalınlık ve boyla, renkle değil.

## 6. Boşluk, köşe, yükseklik

- **Boşluk** 4'ün katları: 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40. Ekran kenarı 16; kart içi 16 (büyük kart 20); kartlar arası 12; bölümler arası 24; satır içi simge-yazı 12.
- **Köşe:** `r8` rozet · `r12` küçük karo, giriş alanı · `r16` kart, gösterge karosu, liste grubu · `r24` harita panelleri, büyük kart, pencere · `r32` alt sayfanın üst köşeleri · `full` düğme, çip, arama, yuvarlak düğme, alt menü göstergesi.
- **Yükseklik:** `e0` düz (kart zeminden renkle ayrılır; gece +1 dp `outline`) · `e1` haritada yüzen öğeler (gündüz `0 1 2 /10%` + `0 4 12 /8%`; gece 1 dp `outline` + `0 4 16 /40%`) · `e2` alt sayfa ve pencere (`0 -8 32 /18%`). Başka gölge yok; harita panelleri tam opak.

## 7. Simgeler

**Material Symbols Rounded** (Apache-2.0), 24 dp (yoğun satırda 20, sürüş düğmesinde 28), çizgi kalınlığı 400; seçili/etkin hâl **dolu**. Kütüphane eklenmez: kullanılan ~50 simge `:app` `WeemIcons.kt`'ye `ImageVector` olarak kopyalanır (lisans notu dosyanın başında). Emoji hiçbir yerde simge olarak kullanılmaz.

| Bugün | Yeni | Bugün | Yeni |
|---|---|---|---|
| 🗺 Harita | `map` | ⌂ Eve dön | `home` |
| 📋 Siparişler | `receipt_long` | ▥ İstatistik | `bar_chart` |
| 🛵 Scooter | `electric_scooter` | ⏹ Günü bitir | `sports_score` |
| 📱 Uzaktan | `devices` | › tutamak | `expand_less` / `expand_more` |
| ⋯ Daha fazla | `menu` | ⌕ ara | `search` |
| ◎ konumuma dön | `my_location` | ⌖ / ➤ yön kipi | `navigation` / `explore` |
| ⇅ rota | `alt_route` | ✕ kapat / Bitir | `close` |
| … hesaplanıyor | ilerleme halkası | ⚙ ayarlar | `settings` |
| 🔗 G3 ile eşleştir | `link` | 🔍 scooter bul | `bluetooth_searching` |
| ▶ kaydı başlat | `fiber_manual_record` | ■ kaydı durdur | `stop` |
| ⤓ ZIP | `folder_zip` | 🔋 / 🪫 batarya | `battery_5_bar` / `battery_alert` |
| ⏱ rota süresi | `schedule` | 🛣 durağa kalan | `route` |
| ⚡ ortalama hız | `speed` | ⏲ hız kutusu | `timer` |
| 📍 durak | `location_on` | ✔ vardın | `check_circle` |
| ⚠ uyarı | `warning` | 📦 teslim | `inventory_2` |
| ↻ yenile | `refresh` | 👍 / 👎 | `thumb_up` / `thumb_down` |
| ✎ düzenle | `edit` | ＋ ekle | `add` |
| ● durum | 8 dp daire (şekil) | ‹ › gün | `chevron_left` / `chevron_right` |
| kapı notu | `door_front` | hava | `partly_cloudy_day` vb. |

## 8. Hareket ve dokunsal geri bildirim

- **Süreler:** 100 ms basış · 200 ms solma, açılma, durum · 280 ms sayfa girişi · 200 ms sayfa çıkışı · 350 ms sayı yuvarlama. **Eğri:** giriş `cubic(0.05, 0.7, 0.1, 1)`, çıkış `cubic(0.3, 0, 0.8, 0.15)`, olağan `cubic(0.2, 0, 0, 1)`.
- **Desenler:** alt sayfa aşağıdan kayar + örtü solar; kart açılır/kapanır (`animateContentSize`); aşama hapı rengi yumuşak geçer ve bir kez hafifçe nabız atar; para ve sayaç değişince sayı yukarı kayar (`AnimatedContent`); sekme geçişi 150 ms solma (harita kaymaz); iskelet parıltısı 1,2 sn. Hız gibi saniyede değişen sayılar animasyonsuz.
- Telefonda animasyon kapalıysa (`ValueAnimator.areAnimatorsEnabled()` false) hiçbiri oynamaz (testler de buna dayanır).
- **Dokunsal:** `Confirm` vardiya başlat/bitir, kayıt başlat/durdur, kaydet · `Reject` hata (rota hesaplanamadı) · `ToggleOn/Off` anahtar · `SegmentTick` çip ve segment · uzun basış bugünkü gibi. Yalnız kullanıcının dokunuşuna; arka plandaki olay titretmez.

## 9. Dokunma ve yerleşim

- En az 48 dp; **sürüş denetimleri 56 dp** (harita düğmeleri, gün kartı eylemleri, rota eylemleri, scooter eylem çubuğu, vardiya başlat/bitir).
- Birincil eylem altta ve başparmak tarafında; alt sayfada eylemler en altta. Ekranın üst çeyreğinde geri dışında kritik eylem yok.
- Uzun metin: başlık 1–2 satır + "…", sayı asla kesilmez (gerekirse küçülür: `autoSize`). 360 dp genişlik ve 1,3 yazı ölçeğinde düzen bozulmaz (Bölüm 14).

## 10. Durumlar

- **Boş:** 72 dp `raised` daire içinde simge + başlık (`titleS`) + açıklama (`bodyS`) + varsa tek düğme. Metinler bugünküler.
- **Yükleniyor:** 300 ms'den uzun sürerse iskelet blokları (`high`, `r8`); düğme içi işte ilerleme halkası.
- **Hata / uyarı:** satır içi **banner**: ton zemini + ton simgesi + metin + varsa eylem/›; kırmızı düz satır ve "  !" kalkar.
- **Başarı:** kısa bildirim şeridi (2 sn) + `Confirm` titreşimi.
- **Pasif:** `muted` + nedeni bir satırda.

## 11. Bileşen kitaplığı (`:app` `ui/`)

| Bileşen | Ölçü ve kural | Yerine geçtiği |
|---|---|---|
| `WeemNavBar` | 72 dp + sistem boşluğu; simge 24 (seçili dolu), etiket `labelS`; seçili gösterge 64×32 hap `accentContainer`; uyarı noktası 8 dp | emoji `NavigationBar` |
| `WeemTopBar` | 64 dp; geri oku (48 dp hedef) + başlık `titleL` (1 satır) + en çok 2 simge düğmesi; kayınca alt çizgi; sistem geri tuşu da geri götürür | "Geri" yazı düğmesi + `headlineMedium` |
| `ScreenTitle` | `headline` + isteğe bağlı durum hapı | sekme başlıkları |
| `WCard` | `surface`, `r16` (büyük `r24`), iç 16/20, `e0`; türler: hero, platform (başlıkta platform noktası + adı, zemin platform rengi), canlı (2 dp `accent` kenar + canlı nokta), uyarı | `Card`, `CardView`, `Surface` çeşitleri |
| `HeroNumber` | etiket `bodyS` üstte, değer `displayM` | `Block.Big` |
| `StatGrid` | 2–3 sütun; değer `numM`, etiket `labelS`; dokunulabilir karo ≥ 56 dp | `TileView` |
| `ListRow` | 56 dp tek satır / 72 dp iki satır; sol 40 dp simge kutusu (`raised`, `r12`) ya da platform noktası; başlık `titleS`, alt satır `bodyS`; sağda tutar (`numS`), ›, anahtar ya da rozet; bütün satır dokunulur | `OrderRowView`, `SettingsRow`, `Nav`, `PrefSwitch`, `ChoiceRow`, `Choice`, `SwitchRow`, `PermLine` |
| `SettingsGroup` | bölüm başlığı `labelM` `muted` (üstte 24) + `r16` kart içinde satırlar (araları 1 dp çizgi, 56 dp girinti) | `Section`, `Group`, `SectionCard`, `SettingsHeading` |
| Düğmeler | `Primary` (dolgu `accent`, 56/48 dp, hap, `labelL`, solda 20 dp simge) · `Tonal` (`accentContainer`) · `Outline` (1 dp) · `Text` (yalnız ikincil, ≥ 48 dp) · `Danger` (tonal; onayda dolgu) · `IconButton` 48/56 · `Fab` 56 yuvarlak `e1` | `Button`, `OutlinedButton`, `TextButton`, `FilledTonalIconButton` karışımı |
| `Chip`, `Segmented` | çip 36 dp görünür / 48 dp hedef, seçili `accentContainer` + ✓; segment 44 dp, `raised` zemin, seçili `surface` + `e1` | `FilterChip`, `SegmentedButton` |
| `StatusPill`, `Badge` | 28 dp hap: 8 dp nokta + `labelM`; tonlar canlı/tamam/uyarı/tehlike/nötr/aşama; sayı rozeti (+1) | "● " metinleri |
| `Banner` | `r16`, ton zemini, simge 20 + `bodyM` + eylem/› | kırmızı satır + "!" |
| `Sheet` | tutamak 32×4, üst köşe `r32`, yanlar 24, başlık `titleL`, eylemler altta; örtü + kayma | `BottomSheetHost`, çok seçenekli `AlertDialog` |
| `ConfirmDialog` | `r24`, başlık `titleL`, metin `bodyM`, iki düğme (tehlikeli olan `danger`) | kısa onay `AlertDialog` |
| `EmptyState`, `Skeleton` | Bölüm 10 | düz metin |
| Harita: `SearchPill` | 56 dp hap, `e1`; solda `search`, ipucu `bodyL` `muted`; sağda hava hapı (simge + "23°") | 52 dp çubuk + 62 dp hava karesi |
| Harita: `MetricTile` | genişlik `HudLayout.metricsWidthDp`, `r16`, `surface`, `e1`; ton simgesi 20 + `numM` + `labelS`; batarya altta 4 dp çubuk (yeşil / turuncu / gri) | degrade `Metric` kutuları |
| Harita: `MapButton` | 56 dp yuvarlak, `surface`, `e1`, simge 28; meşgulken halka, hatada `danger` nokta | 64 dp işaretli yuvarlak |
| Harita: `DayBar` | katlı: 56 dp hap (aşama noktası + 3 eylem 56 dp + tutamak), dokununca ya da kaydırınca açılır; açık: `r24` kart (aşama hapı + süre, gün süresi · km, tamamlanan, sıradakiler, simge + etiketli 3 eylem) | `MapShiftActions` |
| Harita: `RouteCard` | `r24`; durak satırı (40 dp pin kutusu + adres `titleM`, altında güzergâh `bodyS` tek satır), kapı notu bandı (`warn` zemin, 20 sp 800), alt satır: varış süresi `numM` `accentText` + "Bitir" tonal tehlike (48 dp); üstte varsa eve dön önerisi bandı ("Eve git" dolgu uyarı rengi + kapat), düşük güven banner'ı | `RoutePanel` |
| Harita: `Toast` | `r16`, `surface`, `e1`, ton simgesi + yazı | renkli kenarlı şerit |
| Scooter: `Gauge`, `DialTile` | 270° yay 12 dp, iz `raised`, değer `accent`; hız `displayXL`; güç hapı; 2×2 kadran (`r16`, yay 6 dp, değer `numM`) | NB çizimi (renk/ölçü) |

## 12. Dört ekran (örnekler tuvalde)

**Harita (Rotada):** Harita tam ekran. Üstte arama hapı + hava hapı; solda gösterge karoları (batarya % ve altta doluluk çubuğu, rota süresi + varış saati, durağa kalan, ortalama, hızlara göre süre). Altta sağda konum düğmesi, onun altında rota kartı (adres büyük, altında güzergâh, kapı notu turuncu bantta, varış süresi büyük ve vurgu renginde, "Bitir"), en altta gün kartı hapı (aşama noktası + üç eylem) ve sağında rota düğmesi (56 dp; panel açıkken seçili görünür). **Varış:** yalnız harita + üstte bildirim ("Müşteriye vardın") + altta büyük adres kartı (adres `titleL`, kapı notu 24 sp bantta).

**Vardiya kartı:** Kapalıyken üstte kart: simge + "Vardiya kapalı" + tam genişlik 56 dp "Vardiyayı BAŞLAT" (scooter satırları bugünkü gibi yalnız vardiya açıkken). Kesilmişken: başlıkta başlangıç saati, iki banner (GPS durdu · ekran okuma kapalı ›), scooter satırı + "Scooter'a şimdi bağlan" tonal düğme, altta alt alta "Vardiyayı BAŞLAT" (56 dp) + "Vardiyayı BİTİR" (tonal tehlike; Azerice uzun metin yan yana sığmıyor). **Gün kartı** (vardiya açık, haritada altta): katlı hap; açılınca tutamak, canlı nokta + gün süresi · km, tamamlanan sipariş, aşama hapı (aşama rengi) + süre, sıradakiler, simge + etiketli üç eylem karosu (72 dp).

**Siparişler:** Başlık "Siparişler". ŞU AN canlı kartı (vurgu kenarlı; platform noktalı satırlar + müşteri kartı tonal düğmesi). Net kazanç hero kartı: büyük tutar, altında 3 ana değer (teslim, saat başına, çevrimiçi); ardından teklif ve para değerleri, "Sipariş başına ortalama" karoları (dokunulur, ›). Wolt ve Bolt Food kartları platform renginde etiketli. Çevrimiçi oturumlar satırı (›), Araçlar kartı, Siparişler / Teklif puanı segmenti, gün başlığı (‹ tarih ›, toplamlar), süzgeç çipleri, sipariş satırları tek kartta (platform noktası, saat · restoran, alt satır, açık siparişte aşama hapı, sağda Brüt üstte ikincil, Net altta kalın), "Günün tüm ayrıntıları".

**Scooter (Sürüş Paneli):** Başlık + durum satırı (renkli nokta + "Canlı panel • …"). Büyük gösterge kartı: 270° hız yayı, ortada hız (80–88 sp), güç hapı, altta kapsam + dokunma ipucu; 2×2 kadran (batarya + menzil, voltaj, akım, sıcaklık; her birinde küçük halka); sipariş üçlüsü (enerji, pil kullanımı, ortalama hız); altta dört simge düğmesi (ayarlar, eşleştir, bul, ZIP) ve en altta büyük "Kaydı başlat" + yuvarlak durdur. Eşleştirmede "ŞİMDİ güç düğmesine…" büyük uyarı bandı; veri yokken değerler "—", yaylar boş. Tam ekranda başlık, düğmeler ve alt menü kaybolur.

**Tuvaldeki örnekler** (`docs/tasarim/project/`): `Main` Harita rotada (gündüz TR) · `Harita-Gece` (gece AZ, düşük batarya önerisi) · `Harita-Varis` (gece TR) · `Vardiya-Kapali` (gündüz TR) · `Vardiya-Kesildi` (gece AZ) · `Gun-Karti` (gündüz AZ) · `Siparisler` (gündüz TR) / `Siparisler-Gece` (gece AZ), tam kaydırma · `Scooter` (gece TR) · `Scooter-Gunduz` (gündüz AZ) · `Scooter-Bekleniyor` (eşleştirme, gece AZ) · `Mono-Harita`, `Mono-Scooter` (siyah-beyaz vurgu) · `DahaFazla` (ayar satırları) · `Bilesenler` (gündüz/gece bileşen kitaplığı) · `Renkler` · `Yazi-Olcu`. Telefon örneklerinde "Tweaks" ile gündüz/gece ve lacivert/siyah-beyaz değiştirilir. Harita altlığı temsilîdir (gerçek harita MapLibre/Google stilinden gelir; renkleri Bölüm 4'teki harita satırı).

**Etkileşim değişiklikleri (K4; işlev aynı):** (1) gün kartı dokununca da açılır (kaydırma kalır, 20 sn sonra katlanır), açıkken eylemler etiketli; (2) çok seçenekli ve uzun pencereler alttan açılan sayfa olur; (3) Daha fazla'da her satır tek dokunuşla açılır (simge + başlık + açıklama + ›); (4) alt ekranlarda üst çubuk ve sistem geri tuşu; (5) hava yuvarlağı arama hapının sağına iner (dokununca bugünkü gibi açılır); (6) metinlerdeki işaret önekleri ("▶  ", "■  ", "⚠ ", "✕  ", "⌕  ") simgeye döner, metnin kendisi aynı kalır; (7) scooter eylem çubuğunda kayıt düğmesi büyük, öteki dördü simge; (8) haritada konum düğmesi rota kartının üstüne, rota düğmesi gün kartının sağına iner; rota düğmesi panel açıkken "✕" yerine seçili görünür.

## 13. Uygulama planı (onaydan sonra)

Model: tema + bileşenler Sonnet · high; zor ekranlar (harita HUD, scooter Canvas) Opus · high. Her adım ayrı commit, testler yeşil, `:app` lint 0 hata.

1. **Belirteçler:** `logic/app` `Palette` v2 (eski alan adları kalır; yeni: `high`, `outline`, `accentContainer`, `onAccentContainer`, `dangerBg`, `power`, aşama tonları) + `PaletteContrastTest` yeni çiftler; lacivert vurgu (S36). `MapPalette` gece durak rengi + `MapPaletteTest`.
2. **Tema:** `:app` `WeemTheme` (renk şeması, `Typography` Bölüm 5, `Shapes`), `WeemTokens` (boşluk, köşe, yükseklik, hareket), yazı tipi (K2 a ise `res/font` + OFL), `WeemIcons.kt`.
3. **Bileşenler:** Bölüm 11 tablosu (`ui/` paketi), her biri için Robolectric örnek testi (en az 48 dp hedef, içerik açıklaması).
4. **Ekranlar** sırayla: kabuk (alt menü, üst çubuklar) → Harita HUD → vardiya kartı → Siparişler (`CardView` çizicisi tek yerde değişir, bütün kartlar düzelir) → Scooter (sekme + `DashboardView`/`FloatPainter` renk ve yazı) → Daha fazla ve ayar ekranları (`SettingsGroup`) → öteki ekranlar (kurulum, izinler, ev, kayıtlar, 2 telefon, uzaktan, hırsızlık, harita alt ekranları) → sistem üstü katmanlar (rozet metni hariç) → `/code-review high`.
5. **Testler:** `testTag`'ler ve içerik açıklamaları korunur; metni işaret önekiyle arayan testler yalın metne geçer; kaydırma ile açılan gün kartı testi kalır, dokunma testi eklenir.
6. **Dokunulmayacaklar:** `OwnLines`/`OverlayText` biçimleri (ekran okuma kendi rozetini bu metinle ayıklar), bildirim metinleri, tercih anahtarları, kayıt dosyaları, paket ve imza.

## 14. Kabul ölçütleri

- Dört örnek ekran ve tüm ekranlar onaylı tasarımda; `-p logic test` ve `:app`/`:map` Robolectric testleri yeşil; `:app` lint 0 hata.
- `PaletteContrastTest` v2 yeşil (gündüz/gece; metin ≥ 4,5:1, ana sayı ≥ 7:1).
- Kabuk, Harita, Siparişler, Scooter ve Daha fazla ekranları 360 dp genişlik ve 1,3 yazı ölçeğinde Robolectric'te açılır, dokunma hedefleri ≥ 48 dp (sürüş denetimleri ≥ 56 dp).
- `:app`'te emoji/işaret simgesi kalmaz (simgeler `WeemIcons`); elle yazılmış renk yalnız `Palette`/`MapPalette`'te.
- APK büyümesi ≤ 0,5 MB (yazı tipi dahil). Telefonda denenmeden "çalışıyor" denmez.
