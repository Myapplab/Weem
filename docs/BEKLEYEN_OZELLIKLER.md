# Bekleyen özellikler — WEEM 1.0'dan sonra

Kullanıcı kararı (17 Ekim 2026): bu özellikler WEEM 1.0'da **yok**. WEEM tamamen hazır olunca kullanıcıya tek tek sorulur ("şimdi ekleyelim mi?"); onay gelmeden kod yazılmaz. Kapsamın tamamı: `docs/WEEM_PLANI.md` Bölüm 3.

| Kod | Özellik | Ne yapar | Eski kod (başvuru) | Not |
|---|---|---|---|---|
| Y13 | Yüzey ve titreşim ölçümü | Telefon sensörüyle bozuk yol algılama, kötü noktaları Ses− ile işaretleme | `yolgo-test` @ `870ab6e`: `lib/surface/`, `lib/ui/surface_screen.dart`, `android_overlay/SurfaceSignal.kt` | Scooter telefonunda (sabit tutucu) daha iyi ölçer |
| Y14 | Yol kalibrasyonu | 3-2-1 sayımlı ölçüm sürüşüyle titreşim ayarı | `lib/calibration/`, `android_overlay/RoadCalibration*.kt` | Y13'e bağlı |
| N7 | Sesli uyarılar | Teklif değeri, batarya %20, sıcaklık | Yeni | — |

**1.0'a alınan:** Y12 Yol kalitesi öğrenme — kullanıcı kararı 17 Ekim (S3); sıfırdan öğrenir, Ses+/Ses− tuşu yakalama dahil (`WEEM_ISLER.md` A6.11).

**S32 (21 Ekim, A10.5b):** Y13 + Y14 → WEEM 1.1 (A10.4b, geçişten hemen sonra); S20 → WEEM 1.1 (A10.4a, **yapıldı** 21 Ekim: `ORDERS.md` 16.7); N7 Sesli uyarılar çıkarıldı (yazılmayacak). Tabloda bekleyen kalmadı.

**Çıkarılan:** N9 Bakım hatırlatıcı (scooter km'sine göre lastik, fren, servis) — kullanıcı kararı, yazılmayacak.

Kullanıcı yeni bir "sonra" isteği verirse bu tabloya eklenir.
