# Rawstats — ekonomi, enerji, hava, yedek, dışa aktarma bulguları
Kaynak: `rawstats @ 6388c04`; kök `/home/user/kurye_nav`. Kısaltma: `K/` = `app/src/main/java/com/kuryelog/app/`, `T/` = `app/src/test/java/com/kuryelog/app/`, `E/` = `docs/ekonomi-v2/`.
(Dosya parça parça yazılıyor; "YAZILACAK" işaretli bölümler sonra dolar.)

## 1. Özellikler

### R17 Yedek ve geri yükleme (ZIP yedek/geri yükleme, günlük otomatik paket, eski kayıtları içe aktarma)
- Eski kod: `K/BackupFiles.kt:5-48` (`logNames`, `checkedPath`, `mergeLines`); `K/LogImporter.kt:9-68` (`restore`); `K/MainActivity.kt:1243-1258` (`renderRecordTools`: "Vardiya bitince günlük veri paketi" anahtarı, `export_all`, "Analiz için paylaş (anonim)", `import_logs`), `:1477-1491` (onActivityResult 31 → içe aktarma), `:1557-1577` (`exportLogs`), `:1600-1610` (`stage`), `:1612-1630` (`saveAndShare`); `K/DailyPack.kt:16-63`; `K/DailyRecords.kt:7-52`; `K/LegacyRecords.kt:6-25` + `K/OrderTracker.kt:907-931` (`reloadImportedStats`/`rebuildRecords`); `K/GpsService.kt:284` (vardiya sonu `DailyPack.afterShift`).
- Davranış:
  - Kayıt kökü `filesDir/logs/<yyyy-MM-dd>/<dosya>` (`K/Store.kt:53-54`, gün `SimpleDateFormat("yyyy-MM-dd", Locale.US)` cihaz saat dilimi `:43`). Ek: `filesDir/restaurants.json` (kökte).
  - **Tam yedek ZIP** (`exportLogs`): ad `Rawstats_<tarih|all>_<dosya adı .'dan önce|logs>_<HHmmssSSS>.zip` (`K/MainActivity.kt:1565`, `APP_NAME = "Rawstats"` `:54`); yer `Downloads/Rawstats/` (MediaStore, `IS_PENDING` 1→0; hata olursa satır silinir `:1612-1624`), sonra `ACTION_SEND` paylaşım. ZIP girdileri `YYYY-MM-DD/<logName>` + yalnız tüm-gün dışa aktarmada kökte `restaurants.json` (`:1600-1609`). Manifest YOK (tam yedekte işaret dosyası yok). Kopyalama `synchronized(Store)` altında, `Store.importing` iken reddedilir.
  - Tek gün / tek dosya paylaşımı da aynı `exportLogs(date, name)` (`:1535-1536`).
  - **Geri yükleme** (`LogImporter.restore`): yalnız vardiya kapalıyken (`import_stop` tostu, `:1253`,`:1479`); `ACTION_OPEN_DOCUMENT` `application/zip`, istek kodu 31. Her girdi `BackupFiles.checkedPath`: `\` ya da `/` ile başlayan ad reddi; `restaurants.json` kökte; diğerleri tam 2 parça `tarih/ad`, ad `logNames` içinde, tarih `\d{4}-\d{2}-\d{2}` ve `LocalDate.parse` geçerli. Dizin girdileri atlanır. Kopya girdi reddi; en çok 2000 dosya; dosya ≤ 32 MiB, toplam ≤ 256 MiB (`K/LogImporter.kt:20-33`). Önce hepsi doğrulanır: `.jsonl` her boş olmayan satır `JSONObject`, `restaurants.json` JSON, `.csv` ilk satır tam `t,lat,lon,acc_m,speed_ms,bearing,alt_m` (`:36-39`). Boş ZIP reddi.
  - **Birleştirme kuralı (üzerine yazma yok):** log dosyaları `mergeLines` = mevcut + gelen satırlar, boşlar atılır, `LinkedHashSet` ile birebir aynı satır tekilleştirilir, sıra korunur, `\n` ile biter (`K/BackupFiles.kt:41-47`); aynı yedeği iki kez almak istatistiği ikilemez. `restaurants.json`: anahtar bazında, gelen kaydın `n` değeri mevcuttan büyükse ya da mevcut yoksa gelen yazılır (`K/LogImporter.kt:48-58`). Yazma `<ad>.restore` geçici dosya + `renameTo` (`:60-62`). Sonra `OrderTracker.reloadImportedStats` (veritabanı baştan kurulur).
  - İçe aktarma sırasında `Store.importing = true`; OOM dahil her `Throwable` yakalanır (16 Ekim saha düzeltmesi, `K/MainActivity.kt:1486-1489`).
  - **Eski kayıtları içe aktarma** (`LegacyRecords.delivered`): `orders.jsonl` satırı `type=="delivered"` ve `record` alanı yoksa eski (KuryeLog) teslim satırı `TripRecord`'a çevrilir: `t` → teslim anı; `total_min` ≥0 ise kabul = t − total_min·60000; `pkg` yoksa `"unknown"`; `offer_at` yoksa kabul anı; gözlem kaynağı `"legacy_log"`; alanlar `gross`, `km`, `min_lo`, `min_hi`, `stops`, `restaurant`, `rest_addr`, `cust_addr`, `note`, `commission_pct` (yoksa 0.0); kimlik `legacy-<job|pkg-at>`, teklif kimliği `legacy-offer:<id>`, faz `FINISHED`, tek müşteri `customer-1`. Eski tahmini sonuçlar yeni kanıta yükseltilmez (`K/LegacyRecords.kt:5`). `rebuildRecords` bütün gün klasörlerini ada göre sırayla tarar, aynı kimlikte en geç `t` kazanır (`K/OrderTracker.kt:910-930`).
  - **Günlük otomatik paket** (`DailyPack`, 0.22.0, kullanıcı 10 Ekim 2026): vardiya bitince (`GpsService.kt:284`, `guarded("shift_end")`), tercih `dailyPack` (varsayılan `true`) açıksa, vardiyanın değdiği gün(ler) (başlangıç ve bitiş günü, `distinct`; gece yarısını geçen vardiya iki gün) için ANONİM analiz ZIP'i (`AnalysisExport`, el ile "anonim analiz" ile aynı izin listesi ve dışa aktarma başına yeni gizli anahtar) yazılır: `Downloads/Rawstats/gunluk/Rawstats_gunluk_<yyyy-MM-dd>.zip`; aynı günün önceki paketi önce silinir (yalnız uygulamanın kendi MediaStore satırları görünür) (`K/DailyPack.kt:17-50`). Ayrı iş parçacığı `daily-pack`; hata `daily_pack_failed` olayı (`day`, `error` sınıf adı), başarı `daily_pack` olayı (`day`, `files`) (`:29`,`:60`). `Store.importing` iken yazmaz; günün dosyası yoksa yazmaz. Ağ kodu yok. Tam (geri yüklenebilir) yedek DEĞİL: anonim paket geri yükleme için kullanılamaz (`ANALIZ_ANONIM.json` + takma adlar; `checkedPath` bunu reddeder — doğrulanmadı, bkz. R18).
  - Kayıtlar sayfası: gün listesi `DailyRecords.days()` yalnız `logNames` içindeki dosyaları sayar; arşiv `.archived` / `.archived-<ad>` işaret dosyalarıyla yalnız görünürlüğü değiştirir; silme yalnız tanınan dosyaları siler (`K/DailyRecords.kt:6-52`). Bugünün kaydı vardiya açıkken silinemez (`K/MainActivity.kt:1547`). (Kayıtlar ekranı başka alana da ait olabilir.)
- Testler: `DailyRecordsTest` (8) — yalnız tanınan tarihli loglar gün kaydı, arşiv/geri alma bayt korur, tek dosya silme, bilinmeyen dosyaya dokunmama, yol geçişi reddi, ekonomi loglarının dışa/içe aktarılıp günle silinmesi, uygulamanın yazdığı her dosyanın `logNames`'te olması; `DailyPackTest` (2) — ad/klasör, vardiya sonunda anonim paket ve ağsızlık; `RecordJsonTest` (6, kısmen) — `oldDeliveredLogKeepsPriceAndDurationWithoutGuessingNewRejects` (LegacyRecords), `unknownFieldsRemainNullInBackup`, `deliveryDeadlinesStaySignedAfterBackup`. Ayrı `LogImporter`/`BackupFiles` test dosyası yok (bkz. Bölüm 3).
- Veri: tercih `dailyPack` (`kuryelog` SharedPreferences); `logNames` listesi (Bölüm 2); `restaurants.json`; olaylar `daily_pack`, `daily_pack_failed` (`events.jsonl`).
- Bağımlı: R18 (günlük paket = anonim dışa aktarım), R4/R14/R15 (yedeklenen ekonomi dosyaları), N3 (bugün şifreli/otomatik tam yedek YOK).
- WEEM: `:core` (BackupFiles/checkedPath/mergeLines/LegacyRecords saf `logic/`; LogImporter ZIP doğrulaması da saf yapılabilir, MediaStore/Activity Android) · Aşama 4 ("yedek"); LegacyRecords Aşama 2 (:core eski biçimler).
- Not: Tam yedekte manifest/sürüm yok; şifreleme yok; `restaurants.json` yalnız tüm-gün dışa aktarmada. İçe aktarmada gün klasöründeki tanınmayan dosyalar reddedilir (eski yedekte bugün listede olmayan dosya varsa bütün içe aktarma başarısız olur — doğrulanmadı, `checkedPath` `require` hatası).

(R4, R14, R15, R18, N3, N4, N5, N8 — YAZILACAK)

## 2. Veri sözleşmesi kalemleri
(YAZILACAK)

## 3. Test envanteri
Sayım komutu: `cd app/src/test/java/com/kuryelog/app; grep -o '@Test' <dosya>.kt | wc -l` (satır başına bir `@Test`; `grep -c` ile aynı sonuç).

| Test dosyası | Sayı | Neyi doğrular | Hedef |
|---|---|---|---|
| EconomicsLogsTest | 21 | (YAZILACAK) | :economics R4 |
| EconomicsObservationTest | 13 | bekleme/görünürlük gözlemi (WaitObservation) ve feed gizliliği | :orders/:economics (bekleme) |
| EconomicsV2GoldenTest | 8 | ref.py altın vektörleri, enerji, sıralama, <5 ms, Python yuvarlama | :economics R4 |
| EconomicsV2PriorsTest | 4 | önseller ref.py ile anahtar anahtar | :economics R4 |
| OfferScoreE4Test | 8 | 1–10 bantları, kayıt, kurye puanı, gün özeti | :economics R4 |
| OfferNetLineTest | 6 | net satırı (brüt − filo komisyonu), anonim dışa aktarım net satırını atar | :economics R4 / R18 |
| OfferGeoTest | 12 | Plus Code/DMS → kaba bölge, saat bloğu | :economics R4 |
| OfferWeatherK28Test | 2 | taze/eski/yabancı hava; kayıt gidiş-dönüş | :economics R15 |
| ShadowEvaluatorTest | 11 | gölge değerlendirme | :economics R4 |
| AnalysisExportTest | 13 | anonim analiz ZIP | :core/:economics R18 |
| ExportKeysParityTest | 1 | Kotlin izin listesi = tools/analysis | R18 |
| RedactorParityTest | 4 | Redactor = Python redact.py | R18 |
| DailyPackTest | 2 | günlük paket adı/klasör, ağsız | R17 |
| EnergySegmentsTest | 4 | bacak/bekleme enerji satırı, null enerji, hava şekli | :scooter/:economics R14 |
| ScooterBridgeTest | 6 | kaynaksız no-op, şema şekli, bozuk değer null | :scooter R14 |
| ScooterOrderEnergyTest | 7 | sipariş başına Wh, kapsam, D10 | :scooter R14 |
| ScooterSamplesTest | 5 | 1 Hz satır şeması, işaretler, yedekte | :scooter R14 |
| ScooterFeedTest | 5 | YolGo'ya canlı scooter akışı | :scooter/:link (başka alan: Y/S) |
| ScooterReconnectM23Test | 4 | yeniden bağlanma bildirimi | :scooter (başka alan) |
| RecordJsonTest | 6 | kayıt JSON; LegacyRecords; enerji kapsamı/komisyon restart | :core (R17 kısmen) |
| DailyRecordsTest | 8 | gün kayıtları, arşiv, silme, yedek listesi | :core R17 |

Altın test adayları (`app/src/test/resources/economics/`, sayım `python3 -I` ile JSON anahtar uzunlukları):
- `golden_vectors.json` (248 107 B): `specVersion 2.1.0`, `generatedBy ref.py`, tolerans "1e-6 absolute after rounding to 6 decimals; strings/lists exact"; `v1Parity` 2, `priors` 33 anahtar, `offerVectors` 34, `auxVectors` 8, `counts` 3. `cmp` ile `docs/ekonomi-v2/golden_vectors.json` AYNI.
- `offer_geo_vectors.json` (6 941 B): `bakuReference` 2, `hub` 2, `zones` 4, `hourBlocks` 6, `rows` 55; tolerans |fark| ≤ 1e-7, `zone`/`hourBlock` birebir. `docs/ekonomi-v2/vektorler/offer_geo_vectors.json` ile AYNI.
- `redact_vectors.json` (5 894 B): `secret "test-secret"`, `rows` 13, `free` 11; birebir eşitlik. `docs/ekonomi-v2/vektorler/redact_vectors.json` ile AYNI.
- `shadow_corpus.json` (109 202 B): şema `kuryeplus-shadow-corpus/1`, `sources` 2, `scenarios` 28, `parser` 5; üretici `docs/ekonomi-v2/araclar/shadow_parity.py --make-corpus`; docs'ta kopyası YOK.
- `docs/ekonomi-v2/redact.py` = `tools/analysis/redact.py` (cmp aynı).
- `app/src/test/resources/saha/`: `saha_beklenen_2026-10-11_12.json`, `saha_kareleri_2026-10-11_12.jsonl.gz` (saha kareleri; büyük olasılıkla :capture alanı — YAZILACAK kullanım).

## 4. Kapsamda olmayan eski özellikler
(YAZILACAK)

## 5. Çelişkiler ve açık sorular
(YAZILACAK)

## 6. Bilinen hatalar ve "baştan doğru" maddeleri
(YAZILACAK)
