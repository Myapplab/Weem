# Bekleyen özellikler — WEEM 1.0'dan sonra

Kullanıcı kararı (17 Ekim 2026): bu özellikler WEEM 1.0'da **yok**. WEEM tamamen hazır olunca kullanıcıya tek tek sorulur ("şimdi ekleyelim mi?"); onay gelmeden kod yazılmaz. Kapsamın tamamı: `docs/WEEM_PLANI.md` Bölüm 3.

| Kod | Özellik | Ne yapar | Eski kod (başvuru) | Not |
|---|---|---|---|---|
| N7 | Sesli uyarılar | Teklif değeri, batarya %20, sıcaklık | Yeni | — |
| N10 | G3 ekranına harita | WEEM haritası/navigasyonu G3 TFT'sinde (240×320, RGB565) | Yeni; NB `G3_HCI_2026-10-04.md` | Kullanıcı istedi (27 Kasım, S45), **araştırma**. Bilinen: ekran Cortex-M33 + LVGL (StarCFW), BLE AES-128, resmî nav mesaj formatı bilinmiyor; Segway yeniden bağlanırken bulut/kayıtlı 16 bayt parola kullanıyor, HCI'yi çözmek için kayıtta yeni eşleştirme (SET_PWD) gerekli. Canlı görüntü stok yazılımda yok sayılır (doğrulanmadı) |

**1.0'a alınan:** Y12 Yol kalitesi öğrenme — kullanıcı kararı 17 Ekim (S3); sıfırdan öğrenir, Ses+/Ses− tuşu yakalama dahil (`WEEM_ISLER.md` A6.11).

**S32 (21 Ekim, A10.5b):** Y13 + Y14 → WEEM 1.1 (A10.4b, **yapıldı** 21 Ekim: `MAP.md` 18); S20 → WEEM 1.1 (A10.4a, **yapıldı** 21 Ekim: `ORDERS.md` 16.7); N7 Sesli uyarılar çıkarıldı (yazılmayacak). Tabloda bekleyen kalmadı.

**Çıkarılan:** N9 Bakım hatırlatıcı (scooter km'sine göre lastik, fren, servis) — kullanıcı kararı, yazılmayacak.

Kullanıcı yeni bir "sonra" isteği verirse bu tabloya eklenir.
