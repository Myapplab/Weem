# Teklif veri seti kontrolü

```sh
python3 -I tools/veri_seti/kontrol.py --self-test
python3 -I tools/veri_seti/kontrol.py WEEM_teklif_2026-10-10_Cumartesi.zip
```

Standart Python 3.9+ yeterlidir. Ağ çağrısı, ek paket, ZIP açarak diske yazma ve OCR yoktur. Ekran görüntüsü içerikleri incelenmez; yalnız bayt boyutu ve SHA-256 doğrulanır. Öz-test verileri sentetiktir; gerçek günlük ve ekran görüntülerini Git'e eklemeyin.

Çıkış kodları: `0` şema/hash geçerli, `1` tutarsızlık bulundu, `2` ZIP okunamadı. Eksik alanlar, kaynakta olmayan dosyalar, scooter kopuklukları ve ölçülmemiş batarya etkisi raporda ayrıca gösterilir. Sıfır şema hatası verilerin eksiksiz olduğunu veya telefonda doğrulandığını göstermez.

Eşleşme oranının paydası: eşleşen + yalnız gün kayıtları + yalnız canlı kayıt. `offerKey` önce gelir. Anahtar eşleşmezse aynı platform, ilk görülme ±5 saniye ve ücret ±0,01 AZN; birden fazla aday varsa eşleştirilmez.
