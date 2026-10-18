# LINK.md — 2 telefon modu (`:link`) şartnamesi

Durum: A8.1 (18 Ekim 2026, Claude Opus · xhigh) — Bölüm 1 (taşıma). Bölüm 2 (Bluetooth ↔ scooter BLE ölçüm planı) A8.1 sonunda yazılır. A8.2 (eşleştirme, şifreli zarf, kuyruk, `ack`, saat farkı, sürüm) ve A8.3 (roller, mesajlar) bu belgeye bölüm olarak eklenir. Üst kaynak: `WEEM_PLANI.md` 4.4–4.5 ve Bölüm 8 (S5, S6, S11, S23). Eski kod yok (N1 yeni; `OZELLIK_HARITASI.md` N1).

## 1. Taşıma (A8.1)

### 1.1 Katmanlar

- `logic/link` (saf, yerelde test edilir): `Frames` (çerçeve), `Beacon` (UDP işareti), `LanTargets` (yayın adresleri), `BtTargets`, `Backoff`, `LinkMachine` (olay alır, komut verir; zaman parametre, monoton ms). Testler sahte ağla (`FakeNet`): iki makine, TCP/UDP/Bluetooth benzetimi, gecikme, parça parça okuma, kopma.
- `:link` (Android, ince): `LinkRuntime` makineyi tek iş parçacığında sürer; TCP, UDP işareti, RFCOMM soketleri ve ağ izleme. Servis değildir: barındıran ön plan servisi ve ekran A8.3'te (`:app`).
- Üst katman (A8.2) yalnız opak `DATA` yükünü görür: `send(bytes)` → sonuç; gelen yük `onData(bytes, taşıma)`. Kimlik doğrulama ve şifreleme A8.2'de: **A8.1 kanalı kimliği doğrulanmamış bir kanaldır**, uygulamaya A8.2'den önce bağlanmaz.
- Kalıcı bilgi (beklenen eş kimliği, öğrenilen Bluetooth adresi) makineye parametre olarak verilir; değişince geri çağrıyla bildirilir. Saklamak A8.2/A8.3'ün işi.

### 1.2 Wi-Fi (birincil; S6)

- Hotspot hangi telefondaysa o telefonun hotspot arayüzü (`ap0`, `swlan0`, `wlan1` …) ile öteki telefonun Wi-Fi'ı aynı yerel ağdadır. Rol (Provider/Receiver) ile hotspot sahibi bağımsızdır.
- Her telefon TCP **47310**'u dinler (tüm arayüzler).
- Keşif üç yoldan, iki yönde: (a) Wi-Fi istemcisi olan telefon ağ geçidine (= hotspot sahibi) doğrudan bağlanır; (b) bağlı değilken UDP **47311** işareti her uygun IPv4 arayüzün yayın adresine gider (özel adres aralığı; mobil veri ve tünel arayüzleri hariç: `rmnet*`, `ccmni*`, `v4-*`, `tun*`, `ppp*`, `wwan*`, `pdp*`, `seth*`); ilk 60 sn 2 sn'de bir, sonra 5 sn'de bir; (c) işareti alan, gönderenin adresine bağlanır. Böylece hotspot hangi telefonda olursa olsun iki taraf da ötekini bulur.
- İşaret 24 bayt: `WEEM` · sürüm 1 · tür 1 · kimlik 16 bayt (bugün `DeviceId`; A8.2 eşleşme kimliğine çevirebilir) · TCP portu (u16). Kendi işareti ve beklenen eşten başka kimlik yok sayılır.
- targetSdk 36: yerel ağa erişim `INTERNET` izniyle örtük. targetSdk 37'ye geçince Android 17'nin `ACCESS_LOCAL_NETWORK` çalışma zamanı izni gerekir (risk; o gün eklenir).

### 1.3 Bluetooth (yedek; S23: klasik)

- Klasik Bluetooth RFCOMM, güvenli soket, sabit hizmet UUID'si `55a2849b-5970-4a15-a703-6e6dfd08f28f`. Telefonlar bir kez Android Ayarlar → Bluetooth'tan eşleştirilir; WEEM tarama ya da yayın yapmaz, eşleştirme penceresi açmaz.
- Hedef: öğrenilen eş adresi (ilk Bluetooth bağlantısında hatırlanır) eşleşmiş listedeyse yalnız o; yoksa eşleşmiş cihazlardan sınıfı telefon olanlar, adres sırasıyla. Eşleşmiş telefon yoksa durumda "eşleşmiş telefon yok".
- Ne zaman: açık TCP kanalı yokken; yerel ağ hiç yoksa hemen, yerel ağ var ama eş bulunamıyorsa 10 sn sonra. Wi-Fi kanalı açılınca etkin kanal Wi-Fi olur ve Bluetooth kanalı 3 sn sonra `BYE` ile kapanır (scooter BLE'sine sürekli yük olmasın; ölçüm Bölüm 2).
- Yalnız küçük mesaj: Bluetooth'ta yük ≤ **4 KiB**; büyüğü `send` → `TOO_BIG_FOR_BT` (A8.2 kuyruğu Wi-Fi'ı bekler).
- `listenOnly` (A8.3: scooter'a bağlı telefon): yalnız dinler, aramaz; arayan öteki telefondur (sayfalama radyoyu daha çok kullanır).
- İki telefonda da Bluetooth açık olmalı; Pixel'in Bluetooth'u kapalıyken (S11) yedek çalışmaz.

### 1.4 Çerçeve

`[uzunluk u32 BE = 1 + yük][tür u8][yük]`. Türler: 1 `HELLO` (`WEEM` · bağlantı sürümü 1 · kimlik 16 bayt), 2 `DATA` (opak; A8.2 zarfı), 3 `PING` / 4 `PONG` (8 bayt: gönderenin monoton ms'i; RTT), 5 `BYE` (1 bayt neden: 1 çift kanal, 2 Wi-Fi'a geçildi, 3 kapanış, 4 protokol hatası, 5 yanlış eş, 6 sürüm, 7 zaman aşımı). Yük üst sınırı Wi-Fi'da 1 MiB. Bozuk uzunluk ya da bilinmeyen tür → `BYE(4)` + kapat.

### 1.5 El sıkışma ve tek kanal

- İki taraf da bağlantı kurulunca ilk çerçeve olarak `HELLO` yollar; 5 sn içinde gelmezse kanal kapanır. Kimlik kendisiyse `BYE(5)`; beklenen eş verilmişse ve kimlik o değilse `BYE(5)`; sürüm farklıysa `BYE(6)`.
- Aynı taşımada aynı eşle iki kanal açılırsa (iki taraf aynı anda bağlandı): **açan tarafın kimliği küçük olan** kanal kalır, öteki `BYE(1)` ile kapanır; iki telefon aynı sonuca varır.
- Etkin kanal: açık TCP varsa o, yoksa açık Bluetooth. `DATA` her açık kanaldan alınır (geçişte kayıp olmasın; tekrarı A8.2 `id` ile atar).

### 1.6 Canlılık ve yeniden bağlanma

- Wi-Fi: 5 sn çerçeve gönderilmediyse `PING`; 15 sn hiç çerçeve gelmezse kanal ölü sayılır, `BYE(7)` + kapat. Bluetooth: 10 / 30 sn.
- Bağlanma zaman aşımı: TCP 3 sn, Bluetooth 8 sn.
- Geri çekilme hedef başına (ağ geçidi, işaret adresi, Bluetooth adresi): 1, 2, 4, 8, 16, 30 sn (üst sınır), ±%20 sapma; kanal açılınca o hedefinki sıfırlanır; ağ değişince (Wi-Fi ya da hotspot açıldı/kapandı, Bluetooth açıldı) hepsi sıfırlanır ve hemen denenir.
- Açık kanal koparsa o hedef bir kez hemen denenir, sonra geri çekilme.

### 1.7 Durum

`LinkStatus`: `OFF` (2 telefon modu kapalı) · `SEARCHING` (sinceMs, yerel ağ var mı, Bluetooth hazır mı, eşleşmiş telefon var mı) · `LINKED` (taşıma `WIFI|BT`, eş kimliği, sinceMs, son RTT). A8.3 gösterir.

### 1.8 Testler (sahte taşıma, `logic/link`)

Çerçeve: gidiş-dönüş, bayt bayt ve birleşik okuma, sınır, bozuk. İşaret: gidiş-dönüş, yabancı ve eski sürüm. Yayın adresi: arayüz süzme, /24 ve /28. Geri çekilme: dizi, sapma, sıfırlama. Makine + `FakeNet`: hotspot A'da ya da B'de (iki yön), ağ geçidi yolu, işaret yolu, çapraz bağlantı tek kanala iner, `HELLO` zaman aşımı, yanlış eş, `PING`/ölü kanal, kopunca yeniden bağlanma ve geri çekilme, Wi-Fi yok → Bluetooth, Wi-Fi geri gelince Bluetooth kapanır, Bluetooth'ta büyük yük reddi, `listenOnly`, eşleşmiş telefon yok, kapatma.
