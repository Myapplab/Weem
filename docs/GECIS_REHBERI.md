# WEEM 1.0 geçiş rehberi (S31: sıfırdan kurulum)

Eski uygulamalardan WEEM'e geçiş. **Kayıtlar WEEM'e taşınmaz**: önce arşiv ZIP'leri alınır, sonra Rawstats ve YolGo kaldırılır, WEEM temiz kurulur. Cihazda denenmedi; ilk gerçek geçiş A10.4. Bir adım rehberdeki gibi çıkmazsa not al, A10.5'te düzeltilir.

**Telefonlar:** Pixel = Wolt/Bolt telefonu (Provider); Poco = scooter telefonu (Receiver). Her telefonda aşağıdaki 1–4 yapılır; 5–6 yalnız ilgili telefonda.

## 0. Başlamadan

- İki telefon da şarjda, Wi-Fi/mobil veri açık; geçişi vardiya dışında yap (bir saat yeter).
- Dosyalar için bilgisayar ya da bulut (Drive) hazır tut: arşivi ikinci bir yere de kopyala.
- Silinecekler: **Rawstats** (içinde Sürüş Paneli de var), **YolGo**, varsa **WEEM dev** (`.dev` deneme sürümü; WEEM 1.0'la aynı ekranı okur, yan yana kalmasın). Hangisi hangi telefonda kuruluysa onu sil.
- Sıra önemli: **önce arşiv (1), sonra kaldırma (2), sonra kurulum (3)**. Kaldırınca uygulamanın içindeki her şey gider.

## 1. Arşiv (silmeden önce)

Arşiv yalnız saklamak içindir; WEEM'e yüklenmez. Üç parça var; telefonunda olanı al.

1. **Rawstats siparişleri ve kazanç** (Wolt/Bolt telefonu, Pixel): Rawstats → sağ üstte **Kayıtlar** simgesi → **Yedek** → **Tüm kayıtları paylaş (.zip)**. Dosya `İndirilenler/Rawstats/Rawstats_all_logs_<saat>.zip` olarak yazılır, paylaşma penceresi açılır (Drive'a ya da kendine gönder). Hazırlanıyor yazısı bitene kadar bekle (büyük kayıt birkaç saniye sürebilir).
2. **Sürüş Paneli (scooter kayıtları)** (scooter telefonu, Poco): Rawstats → sağ üstte **Sürüş Paneli** (hız göstergesi) simgesi → dosya simgesi **Kayıtları ZIP olarak kaydet** → **Tüm kayıtlar • GPS rotaları dahil** → **Devam et** → açılan pencerede **İndirilenler**'i seç → **Kaydet**. Dosya `NinebotLogger-<sayı>.zip`. Kayıt sürerken ya da temizlik sürerken çalışmaz; önce durdur. *"Tam teşhis"i seçme* (scooter eşleşme anahtarını içerir).
3. **YolGo sürüş kayıtları** (YolGo'yu kullandığın telefon): YolGo → dişli **Ayarlar** → **Yollarım** → **Sürüş kayıtları** → sağ üst **⤓** → **Tüm günlüğü tek dosyada kaydet** → açılan pencerede **İndirilenler**. Dosya `YolGo_tum_kayitlar.json`. (YolGo'nun öteki ayarları — sokak kararları, ışıklar, POI düzeltmeleri, görevler — dışa aktarılamaz; WEEM'de yeniden girilir ya da yeniden öğrenilir, S1.)

Kontrol (her telefonda): **Dosyalar** uygulaması → **İndirilenler** → dosyaların boyutu sıfır değil, adları yukarıdakiler. Dosyaları bilgisayara/Drive'a kopyala. **Kopyalamadan kaldırma adımına geçme.**

İstersen geri dönüş için: Rawstats ve YolGo'nun son APK'larını da sakla (eski repo `kurye_nav` sürümleri). Geri dönersen WEEM kaldırılır, Rawstats kurulur, ZIP'i Rawstats'ın kendi **Eski kayıtları içe aktar (.zip)** satırıyla yüklersin.

## 2. Eski uygulamaları kaldır

Her telefonda: Ayarlar → Uygulamalar → **Rawstats** → **Kaldır**; sonra **YolGo**, varsa **WEEM dev**.

Rawstats kaldırılınca **gider**: kayıtlar ve siparişler, ayarlar, ev konumu, filo komisyonları, tüm izinler ve **Ninebot (scooter) eşleşme anahtarı** (anahtar uygulamaya bağlıdır). Hepsi aşağıda yeniden kurulur. Scooter telefonunda scooter'ın Segway Mobility ile eşleşmesi bozulabilir (bölüm 6).

> **Uyarı:** WEEM 1.0 Rawstats ile aynı paket (`com.kuryelog.app`) ve aynı imzayla yapıldı. Rawstats **kaldırılmadan** kurarsan WEEM Rawstats'ın üstüne güncelleme olarak kurulur ve eski kayıtlar, ayarlar ve izinler kalır — yani temiz kurulum olmaz. Temiz istiyorsan önce kaldır; yanlışlıkla üstüne kurduysan Ayarlar → Uygulamalar → WEEM → Depolama → **Verileri temizle** (izinler de silinir) ya da kaldırıp yeniden kur.

## 3. WEEM 1.0'ı kur

1. Telefonun tarayıcısında GitHub'a giriş yap (depo özel); **Myapplab/Weem → Releases → WEEM 1.0.0** → **`weem-1.0.0.apk`** indir (yanındaki `weem-1.0.0.apk.sha256` isteğe bağlı: bilgisayarda `sha256sum -c` ile doğrulanır).
2. Açınca Android "Bu kaynaktan yüklemeye izin ver" derse tarayıcı/Dosyalar için aç, geri dön, **Yükle**.
3. Play Protect uyarırsa (uygulama Play'den değil, test imzalı): **Yine de yükle**. Bu beklenen davranış.
4. Kurulu uygulama: **WEEM** (paket `com.kuryelog.app`). Sürüm Daha fazla sekmesinin en altında **1.0.0** görünür.

## 4. İlk açılış kurulumu

WEEM ilk açılışta 4 adımlık kurulum açar (**İleri** / **Geri** / **Kurulumu atla**; her adımı sonra **Daha fazla** sekmesinden yapabilirsin; kurulumu yeniden göstermek: **Daha fazla → İzinler ve pil ayarları → İlk açılış kurulumunu yeniden göster**).

1. **Dil:** Türkçe ya da Azerice.
2. **İzinler** (eksik izin kırmızı **! n/m** olarak kalır; vardiya ve kayıt o izne bağlıdır):
   - **Ekran okuma (Erişilebilirlik):** Android Ayarlar → Erişilebilirlik → indirilen/yüklü uygulamalar → **WEEM** → aç. (WEEM Wolt/Bolt'a kendiliğinden hiçbir şeye basmaz, yalnız okur; ağ kullanmaz.) *Scooter telefonunda (Receiver) açma:* orada ekran okunmaz.
   - **Bildirim okuma:** Ayarlar → Bildirimler → Cihaz ve uygulama bildirimleri (bildirim erişimi) → **WEEM** → aç.
   - **Konum + bildirim:** uygulama içinde "İzin ver" → konum **Uygulama kullanılırken** + bildirim izni.
   - **Konum: her zaman (isteğe bağlı):** ev konumundan otomatik vardiya için. Konum izin ekranında **Her zaman izin ver**.
   - **Scooter Bluetooth izni** (Android 12+): scooter seçilince istenir.
   - **Android 13 ve üstünde "Kısıtlanmış ayar" uyarısı:** APK Play Store dışından kurulduğu için Erişilebilirlik ve Bildirim okuma anahtarı gri olabilir. Ayarlar → Uygulamalar → **WEEM** → sağ üst **⋮** → **Kısıtlanmış ayarlara izin ver** (önce Erişilebilirlik/Bildirim anahtarına bir kez dokunup uyarıyı gör), sonra izni aç. Menü adı üreticiye göre değişir; cihazda doğrulanmadı.
3. **Harita verisi:** Bakü yol haritası telefona iner (Merkez/Geniş Bakü). Wi-Fi'de başlat; arka planda sürer, beklemeden ilerleyebilirsin. Sonra **Harita** sekmesinden de indirilir.
4. **Pil ayarları** (isteğe bağlı ama önerilir): telefonun markasına göre rehber açılır. Pixel: uygulama bilgisinde Pil → **Kısıtlama yok**. Xiaomi/Redmi/POCO: **Otomatik başlatma** aç + Pil tasarrufu → **Kısıtlama yok**. Ayrıntı ekranın kendisinde.

**WEEM'i aç** ile bitir. Sonra **Daha fazla** sekmesinde yeniden gir (bunlar eski uygulamadan gelmez):

| Nerede | Ne gireceksin |
|---|---|
| Daha fazla → Ev konumu ve otomatik vardiya | Ev konumu (evden çıkınca vardiya kendiliğinden başlar; "her zaman" konum gerekir) |
| Daha fazla → Siparişler ayarları | **Filo komisyonları** (Wolt/Bolt), günlük hedef, kWh fiyatı (net kazanç) |
| Daha fazla → Harita ayarları | Harita görünümü, harita verisi, Google Maps; yol kalitesi öğrenme varsayılan kapalı |
| Daha fazla → Kayıtlar → Şifreli yedek | İsteğe bağlı: parola belirle (haftalık şifreli yedek, İndirilenler/WEEM/yedek) |

Daha fazla → Kayıtlar'daki **Eski kayıtları içe aktar (.zip)** geçişte **kullanılmaz** (S31).

## 5. Scooter (Ninebot) eşleşmesi — scooter telefonu (Poco)

Anahtar Rawstats'la gitti; yeniden eşleşmen gerekir.

1. Telefonda **Bluetooth** açık, **Segway Mobility** uygulamasında scooter bağlantısı **kapalı** (uygulamayı da kapat).
2. WEEM → **Scooter** sekmesi → arama (büyüteç) simgesi → listeden **kendi scooter'ına** dokun ("Kayıtlı scooter" olarak görünür).
3. **Bluetooth (eşleştir)** simgesi ("G3 ile eşleştir") → "Eşleştir ve kaydet"; istendiğinde scooter'ın **güç düğmesine kısa bas**. Eşleşme GPS kaydını da başlatır; "Scooter yanıt verdi" bildirimi gelir.
4. Gerekirse sonra Segway Mobility'de scooter'ı yeniden eşleştir (Bluetooth anahtarı değişir).
5. Vardiya başlayınca WEEM seçili scooter'a kendisi bağlanır (Scooter ayarlarında kapatılır).

## 6. İki telefon eşleştirme (Pixel ↔ Poco)

1. **Bluetooth yedeği için:** iki telefonu bir kez Android **Ayarlar → Bluetooth**'ta birbirine eşleştir.
2. **Hotspot:** bir telefonda (bugünkü düzen: Poco) hotspot açık, öteki ona bağlı. Hotspot hangi telefondaysa orada **"Hotspot'u otomatik kapat" kapalı** olmalı.
3. WEEM → **Daha fazla → 2 telefon modunu aç** → **Bu telefonun rolü:** Pixel'de **Provider — Wolt/Bolt telefonu**, Poco'da **Receiver — scooter telefonu**. İki telefonda aynı rol seçme.
4. Bir telefonda **Eşleştirme QR'ı göster**, ötekinde **Öteki telefonun QR'ını tara** (QR 5 dakika geçerli). İki ekranda **parmak izi aynı** olmalı. Durum satırı "Eşleşti" ve "Bağlı · Wi-Fi" (Wi-Fi yoksa "Bağlı · Bluetooth") olur.
5. Eşleşme tutmazsa: **Eşleşmeyi kaldır** → iki telefonda yeniden. İki WEEM sürümü farklıysa ikisini de güncelle.
6. İsteğe bağlı: **Hırsızlık koruması** (Receiver'da, 2 telefon ekranında) ve **Uzaktan** sekmesi (öteki telefonun ekranını görme/yönetme; yönetmek için ötekinde Erişilebilirlik → **WEEM uzaktan dokunuş** açılır).

## 7. İlk vardiya kontrolü (A10.4)

- [ ] Harita sekmesinde **Vardiyayı BAŞLAT**; GPS çalışıyor, km artıyor.
- [ ] Wolt/Bolt'ta bir teklif/sipariş geldiğinde **ekran okuma** onu görüyor (Siparişler sekmesinde satır; rozet/balon çıkıyor). *En önemli kontrol:* R8'li release APK ve "yalnız izlenen uygulamalar" kapsamı (A10.2) telefonda ilk kez denenir.
- [ ] Scooter bağlandı, Scooter sekmesinde hız/batarya canlı.
- [ ] Provider'da siparişler akıyor, Receiver'da rota/scooter verisi görünüyor (2 telefon).
- [ ] Vardiya bitince gün özeti ve bildirim geliyor.
- [ ] Gün sonunda pil ve ısınma: bugünkü durumla karşılaştır (ölçüm yolu `docs/OPTIMIZASYON.md` 4).

Bir şey çalışmıyorsa: **Daha fazla → İzinler ve pil ayarları** kartındaki kırmızı satıra bak; çökmede çökme kaydı uygulamanın içindedir (müşteri verisi içermez, telefon numaraları `[tel]` yazılır). Bulduklarını A10.5 listesine ekle (hangi telefon, ne yaptın, ne oldu).

## 8. Sonra

Arşiv ZIP'lerini silme. A10.5'te gerçek kullanımdan sonra gereksiz özellikleri yazarsın; rehberde çıkmayan adımlar da orada düzeltilir.
