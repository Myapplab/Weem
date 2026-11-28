# Bekleyen özellikler — WEEM 1.0'dan sonra

Kullanıcı kararı (17 Ekim 2026): bu özellikler WEEM 1.0'da **yok**. WEEM tamamen hazır olunca kullanıcıya tek tek sorulur ("şimdi ekleyelim mi?"); onay gelmeden kod yazılmaz. Kapsamın tamamı: `docs/WEEM_PLANI.md` Bölüm 3.

| Kod | Özellik | Ne yapar | Eski kod (başvuru) | Not |
|---|---|---|---|---|
| N7 | Sesli uyarılar | Teklif değeri, batarya %20, sıcaklık | Yeni | — |
| N10 | G3 ekranına harita | WEEM navigasyonu G3 TFT'sinde | Yeni; NB `G3_HCI_2026-10-04.md` | Kullanıcı istedi (27 Kasım, S45); **yazıldı 28 Kasım** (ok/mesafe/süre/sokak, build-24; canlı harita görüntüsü yok). **28 Kasım HCI (Pixel, Segway 6.10.x, veri temizlenip yeniden eşleşme):** SET_PWD artık 32 bayt (`0x5c`, anahtar ad+auth); AUTH anahtarı bunun **ilk 16 baytı**; oturumun tamamı çözüldü. Resmî navigasyon = kart **`0x23`**, komut **`0x71`** (yazma), index'ler: `00` veri `01` (başlat; `0x23` boş yanıt verir) · `03` 18 bayt (LE u32 kalan m, u32 ikinci değer, u16 ×2 aday: dönüş tipi?, 6 bayt 0) · `10` ve `24` NUL sonlu sokak adı · `01` LE u32 kalan m. **Görüntü komutu yok** → canlı harita stok yazılımda yok; önce WEEM'den ok + mesafe + sokak adı. Açık: dönüş kodları ve güncelleme sıklığı (kayıt yalnız 1 güncelleme içeriyor, 43 sn) · **Canlı harita (28 Kasım, kullanıcı: yedek yok, bu dashboard'da):** stok yazılımda görüntü yok → özel dashboard yazılımı gerek. Bilinen: TFT kartı 0x23 (segMod wiki 'ECU 0x23', sürücü st7735 benzeri), BLE IAP komutları 0x07–0x0B (nootnooot), SWD: açılışta kilitli, NRST şaseye (segMod 'ST-Link'); MCU (AT32F415? / Cortex-M33?), RDP, imza: doğrulanmadı. Sıra: kartı aç + çip fotoğrafı → ST-Link ile yalnız bağlan/RDP → tam dump → ancak sonra yazma (kullanıcıya ayrıca sorulur) · **ST-Link'siz yol (28 Kasım):** açık stok DIS dosyası yok (scooterhacking/segMod'da x3 yok); StarCFW/OPENX3 dashboard imajı `FIRM.bin.enc`, anahtar kapalı; telefonda Segway önbelleğinde dosya yok → BLE IAP ile değiştirme anahtarsız kapalı. Kalan yol SWD (ST-Link) · **Kart fotoğrafı (28 Kasım, ekran kaldırılarak):** TI TCAN1044AV (CAN alıcı-verici → dashboard CAN hattında), NS8002 (ses yükselteci/korna), AO3401 MOSFET, JW (Joulwatt) regülatör, 6R8 bobin, 12 pinli ekran FPC; FPC yanında altın yuvarlak test noktaları ve 'TP' baskısı (SWD olabilir, doğrulanmadı). Ana MCU görünmedi (ekran çerçevesinin altında) |

**1.0'a alınan:** Y12 Yol kalitesi öğrenme — kullanıcı kararı 17 Ekim (S3); sıfırdan öğrenir, Ses+/Ses− tuşu yakalama dahil (`WEEM_ISLER.md` A6.11).

**S32 (21 Ekim, A10.5b):** Y13 + Y14 → WEEM 1.1 (A10.4b, **yapıldı** 21 Ekim: `MAP.md` 18); S20 → WEEM 1.1 (A10.4a, **yapıldı** 21 Ekim: `ORDERS.md` 16.7); N7 Sesli uyarılar çıkarıldı (yazılmayacak). Tabloda bekleyen kalmadı.

**Çıkarılan:** N9 Bakım hatırlatıcı (scooter km'sine göre lastik, fren, servis) — kullanıcı kararı, yazılmayacak.

Kullanıcı yeni bir "sonra" isteği verirse bu tabloya eklenir.
