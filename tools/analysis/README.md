# KuryePlus analiz aracı (`tools/analysis/`)

Bilgisayarda, internetsiz çalışan betimleyici analiz. KuryePlus (KuryeLog) dışa aktarımlarından
kullanıcının sorularına tablo ile cevap verir:

| Soru | Raporda |
|---|---|
| H7 — çevrimiçi saat başına ne kazanıyorum? | 1. Gün, saat bloğu (B0–B5), platform ve açık uygulama birleşimine göre net ₼/saat |
| H1 — teklifler nerede, ne zaman geliyor? | 2. Bölge (Z1–Z4) × saat bloğu, boşta ve çevrimiçi saat başına teklif; Gamma–Poisson daraltması (SPEC §16.1) |
| H3 — ücret ne kadar değişken? | 3. Platform başına Theil–Sen: ücret ~ km ve ücret ~ gösterilen dakika; artık MAD, en büyük artıklar |
| H2 — art arda teklif geliyor mu? | 4. Teslimden sonraki ilk teklife kadar geçen süre, sipariş sürerken gelen teklif payı, son teslim → sonraki restoran mesafesi |
| H4 — havadan yakın, yoldan uzak mı? | 5. Kurye → restoran (→ teslim) kuş uçuşu km ile platform km'si oranı; oran > 1.6 olan bölge çiftleri |
| H5 — ne kadar boş bekliyorum? | 6. Bölge × blok boş bekleme süreleri, Kaplan–Meier (sağdan sansürlü) |
| H6 — reddetmenin bedeli var mı? | 7. Teklif sonuçları, red uyarı şeritleri, "red yüzünden çevrimdışı", performans ekranları; olaydan önce/sonra teklif/saat |
| Veri kalitesi | 8. Okunamayan ücret, OCR payı, kabul dokunuşu olmadan açılan ("hayalet") siparişler, kanıtsız oturumlar |

Rapor yalnızca **betimler**: kabul/ret önerisi, karar ya da "iyi/kötü teklif" hükmü yoktur. Her sayının
yanında `n` yazar; küçük örnekler **⚠ az veri** ile işaretlenir.

## Gereksinim

* Python 3 (standart kütüphane yeterli; `pip` paketi gerekmez), internet bağlantısı gerekmez ve kullanılmaz.
* Komutları depo kökünden `python3 -I` ile çalıştırın (`-I`: kullanıcı ortamından modül yüklenmez).

## Kullanım

1. Telefonda: **Ayarlar → Kayıtlar → "Analiz için paylaş (anonim)"** ile ZIP'i bilgisayara alın
   (bu düğme WP9'da bağlanır; telefonda denenmedi). ZIP'in kökünde `ANALIZ_ANONIM.json` bulunur.
2. Bilgisayarda:

```bash
python3 -I tools/analysis/run.py KuryePlus_analiz.zip --out rapor/
```

Sonra `rapor/rapor.md` dosyasını açın (telefonda da okunur; tablolar dar tutuldu).

Seçenekler:

| Seçenek | Anlamı |
|---|---|
| `DOSYA.zip` veya `KLASÖR` | Bir ya da birkaç dışa aktarım; satırlar uygulamadaki gibi birleştirilir, tekrar eden satır bir kez sayılır. Anonimlik her girdi için ayrı denetlenir (aşağıya bakın) |
| `--out KLASÖR` | Rapor klasörü (zorunlu) |
| `--redact` | Anonim olmayan tam dışa aktarım ("Tüm kayıtları paylaş") için: anonim olmayan girdiler önce bellekte `anonymize.py` ile anonimleştirilir, sonra anonim girdilerle birleştirilir; ham veri hiçbir yere yazılmaz ve analize girmez |
| `--hub ENLEM,BOYLAM` / `--hub default` | Bölge halkalarının merkezi. Verilmezse: ≥ 30 teslim almada teslim alma noktalarının medyanı, yoksa Bakı merkezi (40.4093, 49.8671) |

Çıkış kodları: `0` rapor yazıldı, `2` kullanım hatası, `3` reddedildi (anonim değil ve `--redact` yok), `4` okunamadı.

`ANALIZ_ANONIM.json` olmayan bir dışa aktarım `--redact` verilmeden **işlenmez**. Bu denetim birleştirmeden önce
**her girdi için ayrı** yapılır: bir girdideki işaret dosyası başka bir girdiyi anonim yapmaz. Örneğin
`run.py anonim.zip tam.zip --out x` çıkış kodu `3` ile reddedilir ve rapor yazılmaz; `--redact` eklenirse yalnız
`tam.zip` bellekte anonimleştirilir ve `anonim.zip` ile öyle birleştirilir. Aynı kural tek bir klasör ya da ZIP için de
geçerlidir: işaret dosyası girdideki her dosyanın yanında olmalıdır (yan yana iki dışa aktarım klasörü içeren bir
klasör, biri anonim olsa bile anonim sayılmaz).

## Çıktılar (`--out` klasörü)

| Dosya | İçerik |
|---|---|
| `rapor.md` | Sekiz bölümlük Türkçe rapor |
| `1_net_gun.csv`, `1_net_blok.csv`, `1_net_platform.csv`, `1_net_uygulama.csv` | Çevrimiçi saat, teslim edilen sipariş n, net ₼, ₼/saat |
| `2_teklif_orani.csv` | Bölge × blok: boş saat, teklif n, ham oran, öncül, daraltılmış oran ve durumu (`personal`/`blend`/`prior`), çevrimiçi saat başına teklif |
| `3_ucret_uyumu.csv`, `3_ucret_artiklar.csv` | Theil–Sen `a`, `b`, kapı (n ≥ 15, x aralığı ≥ 2), artık MAD; en büyük 5 artık (zaman + bölge, adres yok) |
| `4_ardisik.csv` | H2 ölçüleri (medyan, çeyrekler, n, sansürlü) |
| `5_hava_yol.csv` | Oran dağılımı ve oran > 1.6 bölge çiftleri |
| `6_bosta_km.csv` | Kaplan–Meier: n, olay, sansürlü, medyan dk, S(5…60) |
| `7_teklif_sonuclari.csv`, `7_olaylar.csv`, `7_performans.csv`, `7_gunluk_redler.csv` | H6 tabloları |
| `8_veri_kalitesi.csv` | Veri kalitesi sayaçları |

CSV dosyaları UTF-8, virgülle ayrılmış; sütun adları ASCII.

## Raporu okurken

* **Saat:** Bakü saati (UTC+4). Saat blokları SPEC'teki gibi: B0 00:00–08:00, B1 08:00–11:45, B2 11:45–15:00,
  B3 15:00–18:00, B4 18:00–21:30, B5 21:30–24:00. Çevrimiçi süre gece yarısında ve blok sınırlarında bölünür;
  bir sipariş **kabul edildiği** güne ve bloğa yazılır (uygulamadaki kayıt günü gibi).
* **Bölge:** merkeze uzaklık; Z1 < 2.5 km, Z2 < 6 km, Z3 < 12 km, Z4 ≥ 12 km. O anda GPS yoksa `unknown`.
* **Net:** gösterilen ücret × (1 − komisyon) + bahşiş; komisyon kayıttaki değerdir (uygulamadaki hesapla aynı).
* **Boş:** en az bir uygulama çevrimiçi ve aktif sipariş yok. Kabulle bitmeyen boş süreler (çevrimdışı, vardiya sonu)
  Kaplan–Meier'de sağdan sansürlü sayılır, atılmaz.
* **⚠ az veri** eşikleri: sipariş < 10 veya saat < 3 (bölüm 1), teklif < 20 veya boş saat < 5 (bölüm 2),
  Theil–Sen kapısı geçilmedi (bölüm 3), n < 10 (bölüm 4–5), boş bekleme < 20 (bölüm 6).
* **Öncüller:** Gamma–Poisson öncülü (6 teklif/saat × bölge × blok çarpanı, a₀ = 4) SPEC Ek A'daki PRIOR
  değerleridir, ölçüm değildir; tabloda durum sütunu bunu söyler. Web tarifeleri araçta yoktur; ücret yalnız kayıttan okunur.
* **Hayalet sipariş:** kabul dokunuşu kaydı olmadan açılan sipariş (ya da "teklif görülmedi" işaretli). Teslim almadan
  sonuçsuz kapananlar ayrıca sayılır.
* **Doğrulanmamış teslim (K34):** sonraki sipariş (iki uygulamadan biri) müşteri aşamasına geçince kapatılan sipariş
  (`orders.jsonl` olayı `delivered_inferred`, kayıtta `delivered_inferred_at/_by/_reason`, aşama `END_UNCONFIRMED`).
  Teslim saati görülmediği için teslim, net ve süre hesaplarına girmez, hayalet sayılmaz; bölüm 8'de ayrı satır.
  `economics-legs.jsonl` satırı `tripLegs` v1 kalıbında kalır: `outcome` `unknown`, `deliveredAtMs` boş,
  `excludeFromCalibration` true; hangi sipariş olduğu `tripId` ile `orders.jsonl`'dan okunur.

## Okunan dosyalar

Gün klasörleri `YYYY-MM-DD/…` ve kökteki `restaurants.json`, `ANALIZ_ANONIM.json`; tek bir üst klasör sorun değildir.

* KuryeLog 0.10.0–0.14.0: `orders.jsonl` (`offer`, `accepted`, `delivered`, `rejected`, `offer_unconfirmed`,
  `end_unconfirmed`, `record_snapshot`; `record` olmayan eski `delivered` satırları uygulamadaki `LegacyRecords` gibi),
  `online-sessions.jsonl` şema 1 ve 2, `economics-waits.jsonl` v1, `gps.csv`, `events.jsonl` (dokunuşlar),
  `screens.jsonl`, `platforms.jsonl`.
* Varsa SPEC 2.1.0 §17.2 v2 kayıtları: `economics-offers.jsonl`, `economics-idle.jsonl`, `economics-legs.jsonl`,
  `economics-penalties.jsonl`, `economics-payouts.jsonl`.
* Satırlar `schemaVersion` alanına göre okunur. Bozuk satırlar ve bilinmeyen şema sürümleri tahmin edilmez,
  sayılır ve bölüm 8'de gösterilir.

## Gizlilik

* `--redact` ile `anonymize.py`, WP3'teki `AnalysisExport` izin listesinin Python ikizidir: listede olmayan her alan
  atılır; her `lat`/`lon` 3 ondalığa yuvarlanır; her `loc` `{lat, lon, acc, age_s}` olur; kimlikler her çalıştırmada
  yeni, saklanmayan 32 baytlık anahtarla takma ada çevrilir; serbest metin `redact.py` ile maskelenir
  (`docs/ekonomi-v2/redact.py` dosyasının birebir kopyası); KuryePlus'ın kendi panel satırları çıkarılır.
  Ek olarak, dışa aktarımın kendisinde geçen müşteri adı/adresi/notu kalan serbest metinlerden de silinir.
* Anonim kopya yalnız bellekte tutulur; ham veri `--out` klasörüne veya başka bir yere yazılmaz.
* Rapor ve CSV'lerde yalnız toplamlar vardır: platform, gün, saat, blok, bölge, sayı, ₼, dk, km.
  Müşteri adı, telefon, adres, not, kapı kodu, restoran adı/adresi, sipariş ve teklif kimliği yazılmaz.

## Öz-test

```bash
python3 -I tools/analysis/selftest.py
```

`synth.py` bilinen cevaplı 30 günlük sentetik bir dışa aktarım üretir (gerçek veri değildir; sahte müşteri
adları, telefonlar, adresler, notlar, kapı kodları, `name:` kimlikleri ve OCR metnine karışmış panel satırları
bilerek eklenir). Öz-test şunları denetler:

* Theil–Sen eğimi gerçeğin %5'i içinde, Kaplan–Meier medyanı bir aralık (5 dk) içinde, teklif oranları %10 içinde;
* gece yarısını geçen vardiya ve siparişler, iki uygulamanın aynı anda açık olduğu saatler;
* H2, H4, H6 ve veri kalitesi sayıları; şema 1/2 ve bilinmeyen şema ayrımı; eski `delivered` satırı;
* `anonymize.py` sonrası hiçbir müşteri dizgesi kalmaması, yuvarlanmış koordinatlar, değişmeyen ücret/zaman/km,
  işaret dosyası, tanınmayan dosyanın dışarıda kalması;
* raporda ve CSV'lerde kişisel veri olmaması; anonim olmayan dışa aktarımın `--redact` olmadan reddedilmesi
  (anonim ve tam dışa aktarım birlikte verildiğinde de; `--redact` ile tam olanın birleştirmeden önce
  anonimleştirilmesi ve analiz edilen satırlarda hiçbir müşteri dizgesinin kalmaması);
* 30 günlük verinin 60 saniyeden kısa sürede işlenmesi.

Sentetik veriyi elle üretmek için:

```bash
python3 -I tools/analysis/synth.py sentetik/            # gün klasörleri
python3 -I tools/analysis/synth.py sentetik.zip --zip   # uygulamanın tam dışa aktarımı gibi tek ZIP (işaret dosyası yok)
python3 -I tools/analysis/run.py sentetik.zip --out rapor/ --redact
```

## Dosyalar

| Dosya | Görev |
|---|---|
| `run.py` | Komut satırı: her girdiyi ayrı yükle ve anonimlik denetimi yap → (gerekirse ve `--redact` ile anonimleştir) → birleştir → analiz → rapor |
| `kl_export.py` | ZIP/klasör okuyucu, gün klasörleri, `schemaVersion` ayrımı; bölge, saat bloğu, Plus Code/DMS yardımcıları (`ref.py` 2.1.0 ve WP2 ile aynı) |
| `anonymize.py` | WP3 izin listesinin ikizi (yalnız `--redact` ile) |
| `redact.py` | `docs/ekonomi-v2/redact.py` kopyası (değiştirmeyin; öz-test birebir aynılığı denetler) |
| `descriptive.py` | H1–H7 ve veri kalitesi hesapları |
| `report.py` | Markdown + CSV |
| `synth.py` | Bilinen cevaplı sentetik dışa aktarım |
| `selftest.py` | Öz-test |

## Sınırlar

* Sonuçlar kayıtlar kadar iyidir: ekran okuma ve OCR hataları kayıtlara olduğu gibi yansır (bölüm 8'e bakın).
* Restoran konumu yalnız karttaki Plus Code/DMS'den veya teslim alma anındaki GPS'ten alınır; adres geocode edilmez.
* Gerçek telefon dışa aktarımıyla henüz denenmedi (telefonda denenmedi); yalnız sentetik veriyle doğrulandı.
