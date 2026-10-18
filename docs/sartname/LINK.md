# LINK.md — 2 telefon modu (`:link`) şartnamesi

Durum: A8.1 (18 Ekim 2026, Claude Opus · xhigh) — Bölüm 1 (taşıma) ve Bölüm 2 (Bluetooth ↔ scooter BLE ölçüm planı; ölçüm telefonda, henüz yapılmadı). A8.2 (18 Ekim 2026, Claude Opus · xhigh) — Bölüm 3 (eşleştirme, kimlik doğrulama, şifreleme, zarf, kuyruk, `ack`, saat farkı, sürüm); Bölüm 1'in değişen yerleri 3.4–3.5'te. A8.3 (roller, mesajlar, ekranlar) bu belgeye bölüm olarak eklenir. Üst kaynak: `WEEM_PLANI.md` 4.4–4.5 ve Bölüm 8 (S5, S6, S11, S23, S24). Eski kod yok (N1 yeni; `OZELLIK_HARITASI.md` N1).

## 1. Taşıma (A8.1)

### 1.1 Katmanlar

- `logic/link` (saf, yerelde test edilir): `Frames` (çerçeve), `Beacon` (UDP işareti), `LanTargets` (yayın adresleri), `BtTargets`, `Backoff`, `LinkMachine` (olay alır, komut verir; zaman parametre, monoton ms). Testler sahte ağla (`FakeNet`): iki makine, TCP/UDP/Bluetooth benzetimi, gecikme, parça parça okuma, kopma.
- `:link` (Android, ince): `LinkRuntime` makineyi tek iş parçacığında sürer; TCP, UDP işareti, RFCOMM soketleri ve ağ izleme. Servis değildir: barındıran ön plan servisi ve ekran A8.3'te (`:app`).
- Üst katman (A8.2) yalnız opak `DATA` yükünü görür: `send(bytes)` → sonuç; gelen yük `onData(bytes, taşıma)`. Kimlik doğrulama ve şifreleme A8.2'de: **A8.1 kanalı kimliği doğrulanmamış bir kanaldır**, uygulamaya A8.2'den önce bağlanmaz. A8.2'de kanal ancak Noise el sıkışmasıyla doğrulanınca açılır ve `DATA` şifrelidir (3.4–3.5); üst katman `Messenger` (zarf, kuyruk, onay; 3.6–3.9).
- Kalıcı bilgi (beklenen eş kimliği, öğrenilen Bluetooth adresi) makineye parametre olarak verilir; değişince geri çağrıyla bildirilir. Saklamak A8.2/A8.3'ün işi.

### 1.2 Wi-Fi (birincil; S6)

- Hotspot hangi telefondaysa o telefonun hotspot arayüzü (`ap0`, `swlan0`, `wlan1` …) ile öteki telefonun Wi-Fi'ı aynı yerel ağdadır. Rol (Provider/Receiver) ile hotspot sahibi bağımsızdır.
- Her telefon TCP **47310**'u dinler (tüm arayüzler); gelen bağlantı ve işaret yalnız yerel ağ arayüzlerinin alt ağından kabul edilir (mobil veri tarafından gelen reddedilir).
- Keşif üç yoldan, iki yönde: (a) Wi-Fi istemcisi olan telefon ağ geçidine (= hotspot sahibi) doğrudan bağlanır; (b) bağlı değilken UDP **47311** işareti her uygun IPv4 arayüzün yayın adresine gider (özel adres aralığı; mobil veri ve tünel arayüzleri hariç: `rmnet*`, `ccmni*`, `v4-*`, `tun*`, `ppp*`, `wwan*`, `pdp*`, `seth*`); ilk 60 sn 2 sn'de bir, sonra 5 sn'de bir; (c) işareti alan, gönderenin adresine bağlanır. Böylece hotspot hangi telefonda olursa olsun iki taraf da ötekini bulur.
- İşaret 24 bayt: `WEEM` · sürüm 1 · tür 1 · kimlik 16 bayt (`DeviceId`; A8.2'de de aynı, 3.3) · TCP portu (u16). Kendi işareti ve beklenen eşten başka kimlik yok sayılır.
- targetSdk 36: yerel ağa erişim `INTERNET` izniyle örtük. targetSdk 37'ye geçince Android 17'nin `ACCESS_LOCAL_NETWORK` çalışma zamanı izni gerekir (risk; o gün eklenir).

### 1.3 Bluetooth (yedek; S23: klasik)

- Klasik Bluetooth RFCOMM, güvenli soket, sabit hizmet UUID'si `55a2849b-5970-4a15-a703-6e6dfd08f28f`. Telefonlar bir kez Android Ayarlar → Bluetooth'tan eşleştirilir; WEEM tarama ya da yayın yapmaz, eşleştirme penceresi açmaz.
- Hedef: öğrenilen eş adresi (ilk Bluetooth bağlantısında hatırlanır) eşleşmiş listedeyse yalnız o; yoksa eşleşmiş cihazlardan sınıfı telefon olanlar, adres sırasıyla. Eşleşmiş telefon yoksa durumda "eşleşmiş telefon yok".
- Ne zaman: açık TCP kanalı yokken; yerel ağ hiç yoksa hemen, yerel ağ var ama eş bulunamıyorsa 10 sn sonra. Wi-Fi kanalı açılınca etkin kanal Wi-Fi olur ve Bluetooth kanalı 3 sn sonra `BYE` ile kapanır (scooter BLE'sine sürekli yük olmasın; ölçüm Bölüm 2).
- Yalnız küçük mesaj: Bluetooth'ta yük ≤ **4 KiB**; büyüğü `send` → `TOO_BIG_FOR_BT` (A8.2 kuyruğu Wi-Fi'ı bekler).
- `listenOnly` (A8.3: scooter'a bağlı telefon): yalnız dinler, aramaz; arayan öteki telefondur (sayfalama radyoyu daha çok kullanır).
- İki telefonda da Bluetooth açık olmalı; Pixel'in Bluetooth'u kapalıyken (S11) yedek çalışmaz.

### 1.4 Çerçeve

`[uzunluk u32 BE = 1 + yük][tür u8][yük]`. Türler: 1 `HELLO` (`WEEM` · bağlantı sürümü 1 · kimlik 16 bayt), 2 `DATA` (opak; A8.2 zarfı), 3 `PING` / 4 `PONG` (8 bayt: gönderenin monoton ms'i; RTT), 5 `BYE` (1 bayt neden: 1 çift kanal, 2 Wi-Fi'a geçildi, 3 kapanış, 4 protokol hatası, 5 yanlış eş, 6 sürüm, 7 zaman aşımı). Yük üst sınırı Wi-Fi'da 1 MiB. Bozuk uzunluk ya da bilinmeyen tür → `BYE(4)` + kapat. **A8.2:** `HELLO` v2 (bayraklı), yeni tür 6 `NOISE`, `BYE` 8 ve 9, Wi-Fi sınırı 65 535 bayt (3.4–3.5).

### 1.5 El sıkışma ve tek kanal

- İki taraf da bağlantı kurulunca ilk çerçeve olarak `HELLO` yollar; 5 sn içinde gelmezse kanal kapanır. Kimlik kendisiyse `BYE(5)`; beklenen eş verilmişse ve kimlik o değilse `BYE(5)`; sürüm farklıysa `BYE(6)`.
- Aynı taşımada aynı eşle iki kanal açılırsa (iki taraf aynı anda bağlandı): **açan tarafın kimliği küçük olan** kanal kalır, öteki `BYE(1)` ile kapanır; iki telefon aynı sonuca varır.
- Etkin kanal: açık TCP varsa o, yoksa açık Bluetooth. `DATA` her açık kanaldan alınır (geçişte kayıp olmasın; tekrarı A8.2 `id` ile atar).
- **A8.2:** beklenen eş artık eşleşme kaydından gelir (3.3); kimlik ve tek kanal kuralı yalnız doğrulanmış kanallarda uygulanır (3.4).

### 1.6 Canlılık ve yeniden bağlanma

- Wi-Fi: 5 sn çerçeve gönderilmediyse `PING`; 15 sn hiç çerçeve gelmezse kanal ölü sayılır, `BYE(7)` + kapat. Bluetooth: 10 / 30 sn.
- Bağlanma zaman aşımı: TCP 3 sn, Bluetooth 8 sn.
- Geri çekilme hedef başına (ağ geçidi, işaret adresi, Bluetooth adresi): 1, 2, 4, 8, 16, 30 sn (üst sınır), ±%20 sapma. Ret (`BYE` yanlış eş/sürüm/protokol/kapanış), kurulamayan bağlantı ve el sıkışma zaman aşımı geri çekilmeyi büyütür. Ağ değişince (Wi-Fi ya da hotspot açıldı/kapandı, Bluetooth açıldı/kapandı) hepsi sıfırlanır ve hemen denenir.
- Açık kanal koparsa: en az 10 sn açık kaldıysa o hedef hemen denenir (geri çekilme sıfırlanır); daha kısa ömürlüyse geri çekilme sürer (açılıp hemen kapanan kanal kısır döngüye girmesin). Çift kanal ve Wi-Fi'a geçiş kapanışı geri çekilmeyi etkilemez.
- Ağ değişince ve eş Bluetooth'tan gelince açık TCP kanalı yoklanır (`PING`); 3 sn yanıt yoksa ölü sayılır (yarı açık bağlantı beklemesin).

### 1.7 Durum

`LinkStatus`: `OFF` (2 telefon modu kapalı) · `SEARCHING` (sinceMs, yerel ağ var mı, Bluetooth hazır mı, eşleşmiş telefon var mı) · `LINKED` (taşıma `WIFI|BT`, eş kimliği, sinceMs, son RTT). A8.3 gösterir.

### 1.8 Testler (sahte taşıma, `logic/link`)

Çerçeve: gidiş-dönüş, bayt bayt ve birleşik okuma, sınır, bozuk. İşaret: gidiş-dönüş, yabancı ve eski sürüm. Yayın adresi: arayüz süzme, /24 ve /28. Geri çekilme: dizi, sapma, sıfırlama. Makine + `FakeNet`: hotspot A'da ya da B'de (iki yön), ağ geçidi yolu, işaret yolu, çapraz bağlantı tek kanala iner, `HELLO` zaman aşımı, yanlış eş, `PING`/ölü kanal, kopunca yeniden bağlanma ve geri çekilme, Wi-Fi yok → Bluetooth, Wi-Fi geri gelince Bluetooth kapanır, Bluetooth'ta büyük yük reddi, `listenOnly`, eşleşmiş telefon yok, kapatma.

## 2. Ölçüm planı: Bluetooth yedeği scooter BLE'sini bozuyor mu? (A8.1; ölçüm telefonda, K)

**Soru:** Poco scooter'a BLE ile bağlıyken (Sürüş Paneli okuması) Pixel'le klasik Bluetooth kanalı (S23) ve hotspot Wi-Fi'ı, scooter okumasını yavaşlatıyor, yanıtsız bırakıyor ya da koparıyor mu? Telefonlarda Bluetooth ve 2,4 GHz Wi-Fi çoğu zaman aynı radyo ve anteni paylaşır; bu yüzden Wi-Fi koşulu da ölçülür.

**Hazırlık (A8.3'te yapılır, A8.1 kodu hazır):** Daha fazla → Tanı'ya "Bağlantı ölçümü": koşul seçimi (aşağıdaki K1–K4), sahte yük üreteci (`LinkRuntime.send`; eş yankılar), ölçüm kaydı `İndirilenler/WEEM/olcum/link-<koşul>-<ms>.json` (gönderilen/alınan, RTT, kopma ve yeniden bağlanma zamanları, etkin taşıma) ve `tools/link-ble-olcum.py` (ZIP + JSON → tablo). Telefonlar Android Ayarlar'dan bir kez eşleştirilmiş olmalı (S23).

**Düzen:** scooter açık, tekerler dururken; Poco gidondaki yerinde (scooter'a ~30 cm), Pixel cepte (~1 m); Scooter sekmesi açık ve ekran açık (hızlı okuma temposu, `SCOOTER.md` 3); her koşul **10 dk**, her koşulda Scooter → "kayda geç" yeniden başlatılır (ayrıntılı teşhis ilk 10 dk'yı kapsar), bitince Scooter ZIP'i ve ölçüm JSON'u alınır.

| Koşul | Poco | Pixel | Ne ölçülür |
|---|---|---|---|
| K0 taban | 2 telefon modu kapalı, hotspot kapalı | — | scooter okumasının kendi hâli (başta ve sonda iki kez: kayma denetimi) |
| K1 BT boşta | 2 telefon modu açık, `listenOnly`; hotspot kapalı | BT kanalı açık, yalnız `PING` (10 sn) | bağlı ama sessiz kanalın yükü |
| K2 BT yüklü | K1 gibi | saniyede 1 ileti (~1 KiB, iki yön) + 10 sn'de bir 4 KiB | vardiyadaki gerçek mesaj trafiğinin üst sınırı (plan 4.4) |
| K3 BT arama | 2 telefon modu açık, Bluetooth'ta **arayan** (`listenOnly` kapalı) | Bluetooth kapalı | Poco'nun sayfalama denemelerinin (8 sn'ye kadar, geri çekilmeyle) etkisi; `listenOnly` gerekli mi |
| K4 Wi-Fi | hotspot açık (2,4 GHz; ayarda 5 GHz seçilebiliyorsa ikisi ayrı ayrı), 2 telefon modu açık | Wi-Fi'da, K2'nin yükü | Wi-Fi'ın (hotspot) kendi etkisi |
| S sürüş | K0 ve K2, aynı güzergâhta ikişer 15 dk | | gerçek sürüşte fark |

**Ölçütler (Poco'nun Scooter ZIP'inden, koşul başına):** (1) dakikadaki kabul edilen register okuması; (2) dakikadaki yanıtsız istek (1,2 sn penceresi) ve "12 ardışık yanıtsız → yeniden bağlanma" sayısı; (3) BLE kopması (`ble_state`) ve M23 "veri kesildi" bildirimi; (4) 1 Hz örnekte (`economics-scooter.jsonl`) 3 sn'den uzun boşluk sayısı; bağlantı tarafında (ölçüm JSON'u) RTT medyan/p95, kayıp ileti, kanal kopması.

**Karar eşiği (öneri; sonuç kullanıcıya sunulur):** K1–K3, K0'a göre okuma hızında ≤ %5 düşüş, yanıtsız istekte ≤ 1/dk artış, 0 BLE kopması, 0 yeni 3 sn boşluk → Bluetooth yedeği bugünkü kurallarla kalır. Aşılırsa seçenekler: (a) Bluetooth yedeği yalnız scooter dururken, (b) Poco hep `listenOnly` + mesaj seyreltme (son durum mesajları 5 sn'de bir), (c) Bluetooth yedeği kapalı (yalnız Wi-Fi). K4 eşiği aşarsa hotspot 5 GHz'e alınır ya da hotspot Pixel'e taşınır. Sonuç ve karar `WEEM_PLANI.md` Bölüm 8'e yazılır.


## 3. Güvenlik ve teslim (A8.2)

A8.1 kanalının üstüne kimlik doğrulama, şifreleme ve teslim katmanı. Türden bağımsızdır (yük opak bayt); mesaj türleri, roller, ekranlar (QR ekranı dahil, S24), ön plan servisi ve uygulamaya bağlama A8.3'te.

### 3.1 Tehdit modeli

- **Korunan:** mesaj içeriği (teklif, müşteri adres metni ve koordinatı S5, kazanç, rota hedefi, enerji), bütünlük (sahte `NavTarget` kuryeyi yanlış yere götürür, sahte enerji ekonomiyi bozar), eşleşmenin kendisi.
- **Saldırgan:** (a) aynı yerel ağdaki cihaz (hotspot parolasını bilen, ev/kafe Wi-Fi'ı): dinler, paket ekler, değiştirir, tekrarlar, siler; TCP 47310'a bağlanır, sahte işaret yollar, araya girer (ARP); (b) Bluetooth'ta eşleşmiş başka cihaz (RFCOMM'a bağlanabilir); (c) aynı telefondaki başka uygulama (telefonun kendi yerel ağ adresinden 47310'a bağlanabilir); (d) eşleştirmede QR'ı gören.
- **Hedefler:** (H1) kanal yalnız eşleşmiş eşle, iki yönlü kimlik doğrulamayla açılır; (H2) her `DATA` gizli ve bütün (AEAD); (H3) her bağlantıda yeni oturum anahtarı (ileri gizlilik: kimlik anahtarı sonradan çalınsa da eski trafik açılmaz); (H4) kanal içinde tekrar, sıra bozma ve silme fark edilir; kanallar arası ve yeniden gönderim tekrarları `id`/`seq` ile atılır; (H5) yalnız QR'ı okuyan ve jetonu süresinde kullanan telefon eşleşir; (H6) bozuk girdi çökertmez, bellek sınırlı; anahtar, jeton ve düz metin yük loga ya da çökme kaydına girmez.
- **Kapsam dışı (kabul):** ağdaki varlığın gizlenmesi (işaret ve `HELLO` cihaz kimliğini düz taşır; kimlik rastgele, donanımdan türetilmez, yedeğe girmez: `CORE.md` 1.10); trafik çözümlemesi (boy, zaman); yerel ağdaki saldırganın bağlantıyı düşürmesi (DoS: yalnız geciktirir, teslim zorunlu mesajlar yeniden gider); root'lu ya da ele geçirilmiş telefon; kilidi açık çalınan telefon (öteki telefondan eşleşme kaldırılır, 3.11). QR'ı omuz üstünden gören biri 5 dk içinde önce eşleşebilir: şifreleme bunu ayıramaz; önlem kısa pencere, tek kullanım ve iki ekranda eşleşen cihazın adı + parmak izi (A8.3).

### 3.2 Algoritma ve gerekçe

**Noise Protocol Framework** (rev. 34): her bağlantıda `XX`, eşleştirmede `XXpsk3` (psk = QR jetonu); DH **P-256** (secp256r1), şifre **AES-256-GCM**, özet **SHA-256** → `Noise_XX_P256_AESGCM_SHA256`, `Noise_XXpsk3_P256_AESGCM_SHA256`.

- **Kripto icat edilmez:** el sıkışma kalıbı, anahtar türetme (HKDF, `MixKey`/`MixHash`/`MixKeyAndHash`), nonce düzeni ve `Split` Noise'un tanımıyla birebir. Yalnız DH işlevi Noise'un listesinde yok (25519/448); P-256 olarak tanımlanır (Lightning'in secp256k1'i gibi): açık anahtar 65 bayt SEC1 sıkıştırılmamış (`04 ‖ X ‖ Y`; iletide DHLEN = 65), DH çıktısı 32 bayt X (JCA `ECDH`); gelen anahtar eğri üstünde mi denetlenir (boy, önek, `X, Y < p`, `y² = x³ − 3x + b mod p`; P-256'da kofaktör 1), değilse el sıkışma biter.
- **Yalnız JCA, minSdk 29'da hepsi var:** `KeyPairGenerator`/`KeyFactory` `EC`, `KeyAgreement` `ECDH`, `AES/GCM/NoPadding`, `HmacSHA256`, `SHA-256`. X25519'un genel API'si Android'de API 33'ten (`XECPublicKeySpec`; Poco'nun Android sürümüne bağlanırdı), ChaCha20-Poly1305'in parametre sınıfı API 35'ten → seçilmedi; iki telefonda da AES donanımla.
- **Kimlik anahtarı Keystore'un içinde tutulamaz** (Keystore'da anahtar anlaşması `PURPOSE_AGREE_KEY` API 31'den): yazılım anahtarı, PKCS#8 hâli Keystore AES-256-GCM anahtarıyla sarılı saklanır (`:scooter` `KeyVault`, `:app` `BackupVault` deseni; 3.12).
- **Neden `XX`:** iki taraf kimlik anahtarını şifreli yollar, alıcı onu eşleşmede sakladığı anahtarla karşılaştırır (sabitleme); ileri gizlilik `ee` ile; başlatanın kimliği ancak yanıtlayan doğrulandıktan sonra gider; aynı kalıp `psk3` ile eşleştirmeyi de taşır. 1,5 gidiş-dönüş + anahtar onayı (3.4).
- **Doğrulama:** Kotlin Noise çekirdeği resmî Noise vektörlerini birebir geçer (cacophony ve snow: `Noise_XX_25519_AESGCM_SHA256`, `Noise_XXpsk3_25519_AESGCM_SHA256`; X25519 yalnız testte, JDK'nın `XDH`'ıyla); P-256 vektörleri bağımsız Python uygulamasıyla (`tools/link-noise-reference.py`, `cryptography`/OpenSSL; o da resmî vektörleri geçer) birebir (3.14).

### 3.3 Kimlik ve eşleştirme (QR)

- **Kimlik:** cihaz kimliği = `weem.deviceId` (A8.1 `PeerId`); kimlik anahtarı = P-256 anahtar çifti (2 telefon modu ilk açıldığında üretilir; eşleşme kaldırılınca kalır). **Parmak izi** = SHA-256(açık anahtar)'ın ilk 10 baytı, onaltılık büyük harf, 4'lü gruplar (`ABCD 1234 …`, 20 karakter; A8.3 iki ekranda gösterir).
- **QR (veren telefon, A):** `weem://pair?v=1&id=<cihaz kimliği>&k=<açık anahtar, base64url dolgusuz, 87 karakter>&t=<jeton, 32 bayt, base64url, 43 karakter>&n=<cihaz adı, URL kodlu, ≤ 32 bayt UTF-8>`. Okuyan bilinmeyen parametreyi yok sayar; `v` > 1 → "WEEM'i güncelleyin". Jeton `SecureRandom`, yalnız A'nın belleğinde; **5 dk** (`PAIRING_WINDOW_MS`, A'nın monoton saati) ve **tek kullanım**: ilk başarılı eşleşmede ya da pencere bitince silinir. Yanlış jetonla deneme jetonu harcamaz (256 bit, tahmin edilemez; harcasaydı ağdaki saldırgan eşleştirmeyi engellerdi). Pencere açıkken A yalnız eşleştirme el sıkışması kabul eder (eski eşi de bağlanamaz); pencere bitince eski eşleşmesine döner.
- **Okuyan telefon (B):** QR'ı ayrıştırır; yalnız QR'daki kimliğe bağlanır ve yalnız QR'daki anahtarı kabul eder; B'nin de taramadan sonra 5 dk penceresi var.
- **Akış:** A8.1 keşfi (Wi-Fi ya da Bluetooth) → iki `HELLO`'da eşleştirme bayrağı → `XXpsk3` → B, A'nın kimlik anahtarını QR'dakiyle karşılaştırır (tutmazsa bırakır, kendi anahtarını ve bilgisini göndermeden); A, B'nin jetonu bildiğini psk'yle doğrular → iki taraf `Paired(eş kaydı)` bildirir; kayıt saklanır, kanal açık kalır ve olağan oturum olarak sürer. **Eş kaydı:** cihaz kimliği, açık anahtar, cihaz adı, eşleşme zamanı (+ A8.1'in öğrendiği Bluetooth adresi).
- Tek eş: yeni eşleşme eskisinin yerine geçer, eski eşin kuyrukları ve görülenler listesi (3.7–3.8) silinir.

### 3.4 Kanal el sıkışması (A8.1 1.4–1.5'in güncel hâli)

- **Çerçeve türleri:** 1 `HELLO`, 2 `DATA`, 3 `PING`, 4 `PONG`, 5 `BYE`, **6 `NOISE`** (el sıkışma iletisi). `BYE` nedenlerine eklenen: **8** eşleştirme modu uyuşmuyor, **9** kimlik doğrulanamadı.
- **`HELLO` v2 (22 bayt):** `WEEM` · bağlantı sürümü **2** · cihaz kimliği 16 · bayrak (bit 0 = eşleştirme). İki taraf da bağlantı kurulunca yollar (A8.1 gibi). Kabul güvene göre: eşleşmiş telefon yalnız eşin kimliğini ve bayrak 0'ı; B (okuyan) yalnız QR'daki kimliği ve bayrak 1'i; A (veren, pencere açık) kendisi dışındaki her kimliği ve bayrak 1'i. Bayrak uyuşmazsa `BYE(8)`, kimlik tutmazsa `BYE(5)`, sürüm `BYE(6)`.
- **Noise:** iki `HELLO` da alınınca başlar; **başlatan = bağlantıyı açan** (TCP/RFCOMM istemcisi). prologue = `"WEEM link 2"` ‖ başlatanın `HELLO` yükü ‖ yanıtlayanınki (iki `HELLO` da el sıkışmaya bağlanır).
  - msg1 → `e` (yük boş)
  - msg2 ← `e, ee, s, es` + yanıtlayanın **bilgi yükü**
  - msg3 → `s, se` (eşleştirmede `+ psk`) + başlatanın bilgi yükü
  - Bilgi yükü: `cihaz kimliği 16 · protokol ana u8 · küçük u8 · uygulama sürüm kodu u32 · sürüm adı (u8 boy + UTF-8 ≤ 32) · cihaz adı (u8 boy + UTF-8 ≤ 32)`; fazla bayt yok sayılır (ileri uyum).
- **Denetim:** gelen kimlik anahtarı = eşleşme kaydındaki (eşleşmiş) ya da QR'daki (B) anahtar; A eşleştirmede her anahtarı alır, psk doğrular. Bilgi yükündeki kimlik = `HELLO`'daki. Tutmazsa `BYE(9)`. Başlatan, msg3'ü yanıtlayanı msg2'de doğruladıktan sonra yollar.
- **Anahtar onayı:** yanıtlayan msg3'ü doğrulayınca boş bir taşıma iletisi (16 bayt) yollar ve kanalı açar; başlatan kanalı bu iletiyi çözünce açar (eşleştirmede başlatan, yanıtlayanın jetonu bildiğini ancak burada görür).
- El sıkışma (iki `HELLO` dahil) **10 sn**'de bitmezse `BYE(7)`; ilk `HELLO` için 5 sn (A8.1). Çözülemeyen ileti, geçersiz anahtar → `BYE(9)`; yanlış sırada/türde çerçeve → `BYE(4)`; geri çekilme A8.1 1.6 (ret).
- **Tek kanal kuralı** (A8.1 1.5) yalnız açılmış (doğrulanmış) kanallar arasında: sahte `HELLO` gerçek kanalı kapatamaz.
- **`LinkStatus`'a eklenen:** `channel` (etkin kanalın numarası; değişince son durumlar yeniden gider, 3.7), `issue` (son ret nedeni `VERSION` · `AUTH` · `PAIRING`; bağlanınca silinir; A8.3 gösterir).

### 3.5 Şifreli iletim

- Açık kanalda `DATA` yükü = Noise taşıma iletisi: AES-256-GCM, nonce = 4 sıfır bayt ‖ 64 bit büyük uçlu sayaç (yön başına 0'dan, her iletide +1, hiç tekrar etmez), AD boş, etiket 16 bayt. Yön anahtarları `Split()`'ten (başlatan → yanıtlayan ve tersi). Her kanalın anahtarı ayrı: Wi-Fi ↔ Bluetooth geçişinde iki kanal birlikte açıkken her biri kendi anahtarıyla.
- Çözülemeyen `DATA` (değişmiş, tekrarlanmış, sırası bozulmuş ya da arada silinmiş ileti: sayaç tutmaz) → `BYE(9)` + kapat. Kanal içi tekrar saldırısı böyle düşer.
- Sınır: şifreli yük Wi-Fi'da ≤ **65 535** bayt (Noise ileti sınırı; A8.1'in 1 MiB'ı indi), Bluetooth'ta ≤ 4 096 → düz metin ≤ 65 519 / 4 080 bayt.
- `PING`/`PONG`/`BYE` şifresiz kalır (içerik yok; canlılık ve kapanış). Sahtesi en çok bağlantıyı düşürür ya da ölü kanalı biraz canlı gösterir (DoS düzeyi, kabul). Saat ölçümü şifreli (3.9).

### 3.6 Zarf

`v u8 = 1 · sınıf u8 · ad boyu u8 · ad (ASCII [A-Za-z0-9._-], 1–48) · id 16 (UUID) · seq u64 · sentAtMs i64 (gönderenin duvar saati) · ttlMs u32 · yük` (büyük uçlu; başlık 39 + ad boyu bayt).

- Plandaki `{v, type, id, seq, sentAtMs, ttlMs, payload}` alanlarına **sınıf** eklendi: 1 son durum, 2 teslim zorunlu, 3 onay, 4 denetim. Alıcı sınıfı türü tanımadan görür: yeni sürümün bilinmeyen türü de doğru onaylanır ya da birleşir, uygulamaya gider ve orada yok sayılır.
- `link.` önekli adlar bu katmanındır: `link.ack`, `link.time`, `link.unpair`.
- Bozuk zarf (sürüm, sınıf, ad, boy) atılır ve sayılır; kanal kapanmaz (kanal zaten doğrulanmış).

### 3.7 Kuyruk

| | Son durum (sınıf 1) | Teslim zorunlu (sınıf 2) |
|---|---|---|
| Örnek (A8.3) | `ShiftState`, `OfferSummary`, `BatterySummary`, `RouteStatus`, `DaySummary` | `TripStages` olayları, `EnergyRequest/Result`, yedek parçaları |
| Kuyrukta | tür başına yalnız en yenisi (bellekte) | hepsi, **kalıcı** (`files/link/outbox/`; yayınlama dönmeden diske yazılır) |
| TTL | zorunlu, 1 sn – 24 sa; geçince gönderilmez | varsayılan 24 sa, en çok 7 gün; geçince silinir + `Expired` |
| Gönderim | yayınlanınca (bağlıysa) ve **etkin kanal her değişince** (bağlanış, Wi-Fi ↔ Bluetooth, yeniden bağlanma) hepsi yeniden | `seq` sırasıyla, aynı anda en çok 16 onaysız; onay gelmezse 3, 6, 12, 24, 48, 60, 60 … sn sonra yeniden; etkin kanal değişince onaysızların hepsi hemen yeniden |
| Onay | yok | `link.ack` gelince silinir + `Acked` |
| Bluetooth'ta şifreli boy > 4 KiB | Wi-Fi'ı bekler (yenisi gelirse onun yerine geçer) | Wi-Fi'ı bekler; küçükler Bluetooth'tan gitmeyi sürdürür |
| Yeniden başlatma | kaybolur (uygulama açılışta güncel durumu yeniden yayınlar; A8.3) | dosyadan geri yüklenir, ilk bağlantıda gider |

- `seq`: gönderen başına artan, yeniden başlatmada da (64 bit; `files/link/seq`'te 256'lık blok ayrılır, blok bitince yenisi yazılır; atlanan numara sorun değil). `id`: rastgele UUID. Yeniden gönderimde ikisi de aynı.
- Kalıcı kuyrukta en çok 2 000 ileti; aşılırsa en eskisi `Expired`. Yük ≤ 65 519 − başlık bayt; büyüğü yayınlanamaz (A8.3 parçalar).

### 3.8 Onay ve tekrar atma

- Teslim zorunlu her zarf için alıcı **her seferinde** (yeni ya da tekrar) `link.ack` yollar (yük `sayı u16 · id × 16 bayt`, en çok 128 id; aynı anda gelenler tek onayda). Onay kuyruğa girmez, onaylanmaz; kaybolursa gönderen yeniden yollar, alıcı yine onaylar.
- Teslim zorunlu alım: `id` görüldüyse atılır; değilse uygulamaya verilir, sonra `id` kalıcı "görülenler"e yazılır (`files/link/seen`; alındığı an + TTL + 1 sa tutulur, en çok 4 096, dolunca en eskisi çıkar), sonra onaylanır. Böylece aynı `id` bir kez işlenir; yalnız teslim ile yazma arasındaki çökme bir tekrar doğurabilir (en az bir kez) → A8.3 işleyicileri `id`/`requestId` ile tekrarı zararsız kılar.
- Son durum alımı: tür başına son `seq` bellekte; gelen `seq` ≤ son → eski ya da tekrar, atılır (iki kanaldan sırası karışan iletiler); TTL'si geçmiş (saat farkıyla düzeltilmiş) → atılır. Yeniden başlayan alıcı ilk geleni alır.
- Tekrar saldırısı iki katta düşer: kanal içinde AEAD sayacı (3.5); kanallar arasında ve yeniden gönderimde `id`/`seq`.

### 3.9 Saat farkı

- `link.time` (denetim): istek `{1, t1}`, yanıt `{2, t1, t2, t3}` (duvar saati ms; yanıtı alan t4'ü kendi okur). Her etkin kanal değişiminde ve bağlıyken **5 dk'da bir**; iki taraf da kendisi ölçer.
- `fark = ((t2 − t1) + (t3 − t4)) / 2` (eşin saati − benimki), `gecikme = (t4 − t1) − (t3 − t2)`. Gecikmesi 0–10 sn olan örnek alınır; tahmin = son 8 örnekten gecikmesi en küçük olanın farkı (belirsizlik ± gecikme / 2).
- Kullanım: zarfın `sentAtMs`'i ve yüklerdeki zamanlar `yerel = eşin saati − fark` (yüklerdekini A8.3 düzeltir); son durum TTL'si bununla. İlk örnekten önce fark 0. Eşleşme değişince örnekler silinir.

### 3.10 Sürüm denetimi

- Bağlantı sürümü (`HELLO`, 2) ya da Noise adı tutmazsa kanal kurulmaz (`BYE(6)` / `BYE(9)`).
- Protokol sürümü (bilgi yükü; bugün **1.0**, `LinkProtocol`): ana sürüm farklıysa iki taraf da el sıkışmayı bitirir (bilgi yükü doğrulanmış gelir), `PeerInfo` bildirir, `BYE(6)` ile kapatır, `issue = VERSION` (A8.3: "Öteki telefondaki WEEM uyumsuz (<sürüm adı>); ikisini de güncelleyin"). Küçük fark bağlanır (bilinmeyen alan ve tür yok sayılır). Uygulama sürümü farklıysa yalnız bildirilir (Daha fazla'da uyarı; A8.3).

### 3.11 Eşleşmeyi kaldırma

Kaldıran telefon bağlıysa `link.unpair` yollar (en iyi çaba), sonra eş kaydını, kuyrukları ve görülenleri siler, 2 telefon modunu durdurur; kimlik anahtarı kalır. `link.unpair` alan (yalnız doğrulanmış eşten gelebilir) aynısını yapar ve `Unpaired` bildirir. Bağlı değilken kaldırılırsa öteki telefon aramayı sürdürür ama bağlanamaz (`issue = AUTH`); orada da kaldırılır ya da yeniden eşleştirilir.

### 3.12 Saklanan veri ve loglar

- Tercih dosyası **`weem-link`** (`MODE_PRIVATE`, eşzamanlı `commit`): `identity.key` (Base64 NO_WRAP: IV 12 ‖ AES-GCM(PKCS#8) + etiket 16; AAD `weem-link/identity/v1`), `identity.public` (Base64 SEC1 65 bayt), `peer.id`, `peer.public`, `peer.name`, `peer.pairedAtMs`, `peer.btAddress`.
- Keystore takma adı **`WEEM.Link.v1`**: AES-256-GCM, şifrele/çöz, kullanıcı doğrulaması yok (ekran kilitliyken arka planda çalışır).
- Dosyalar `files/link/`: `outbox/<seq, 20 hane>.env` (teslim zorunlu zarflar; uygulamanın öteki kayıtları gibi özel depoda, ek şifresiz), `seq`, `seen`. Yedeğe ve dışa aktarmaya girmez.
- Saklanmaz: oturum ve geçici anahtarlar, jeton (yalnız bellekte). Kimlik anahtarı açılamazsa yenisi üretilir ve yeniden eşleştirme gerekir (öteki telefonda `issue = AUTH`).
- **Loglar:** anahtar, jeton, QR metni ve düz metin yük hiçbir loga, `FailureSink`'e ya da çökme kaydına girmez; sır taşıyan sınıfların `toString`'i içeriği yazmaz; hata iletileri yalnız tür ve neden taşır.

### 3.13 Katmanlar (kod)

- `logic/link` (saf, JVM'de test): `Noise` (CipherState, SymmetricState, HandshakeState; `XX`, `XXpsk3`), `P256`, `Pairing` (QR, kimlik, eş kaydı, güven: eşleşmiş · veren · okuyan, parmak izi), `SecureHandshake` (kanal başına el sıkışma), `LinkMachine` (A8.1 + el sıkışma + şifreli `DATA`), `Envelope`, `Messenger` (kuyruklar, onay, tekrar atma, saat farkı), `MessengerStore` (dosya ve bellek), `LinkStack` (makine + `Messenger` tek kilit altında; `LinkRuntime` ve testler aynısını kullanır), `IdentityVault` (sarma kuralı ve tercih anahtarları; `PairingVault` gibi, ana anahtar dışarıdan).
- `:link` (Android, ince): `LinkVault` (Keystore `WEEM.Link.v1` + `weem-link`), `LinkRuntime` (`LinkStack`'i sürer; `publishLatest`/`publishReliable`, `startPairing()` → QR metni, `pairWith(qr)`, `cancelPairing()`, `unpair()`; dinleyici: durum, mesaj, eşleşti, eşleşme kaldırıldı, eş bilgisi, onaylandı, süresi geçti). A8.1'in ham `send`/`onData`'sı kalkar.

### 3.14 Testler ve doğrulama

- `NoiseVectorsTest`: resmî vektörler (cacophony `XX` + `XXpsk3`, snow `XX`; `src/test/resources/noise/`) her iletisi ve el sıkışma özetiyle birebir.
- `NoiseP256VectorsTest`: `tools/link-noise-reference.py`'nin ürettiği P-256 vektörleri (`XX` ve `XXpsk3`; el sıkışma + taşıma iletileri) birebir. Betik `python3 -I` ile çalışır, önce resmî vektörleri kendisi doğrular.
- `P256Test`: kodlama; yanlış boy, önek, `≥ p` koordinat ve eğri dışı nokta reddi; iki yönlü anlaşma.
- `PairingQrTest`, `EnvelopeTest`, `IdentityVaultTest`, `MessengerStoreTest`, `ClockSyncTest`: gidiş-dönüş, sınırlar, bozuk girdi; sarma/açma ve AAD; dosya deposu ve `seq` bloğu; fark/gecikme formülü ve süzgeç.
- `SecureLinkTest` (`FakeNet`, uçtan uca): doğru eşleşme (Wi-Fi'da A başlatan ve yanıtlayan, Bluetooth'ta); yanlış, süresi geçmiş ve kullanılmış jeton (üçüncü telefon); araya giren (sahte A'ya yönlendirme; MITM) el sıkışmayı tamamlayamaz ve B'nin bilgisini alamaz; değiştirilmiş şifreli metin ve tekrarlanan çerçeve kanalı kapatır, ileti bir kez teslim edilir; yanlış eş; anahtarı değişmiş eş (`AUTH`); ana/küçük sürüm farkı; eşleşmeyi kaldırma.
- `MessengerTest` (`FakeNet`): son durum birleşir, TTL'de düşer, sırası karışan eski durum atılır, bağlanışta ve kanal değişiminde yeniden gider; teslim zorunlu onaylanır, onay kaybında üstel yeniden gönderim, aynı `id` bir kez işlenir (alıcı yeniden başlasa da), gönderen yeniden başlayınca da gider, Bluetooth'ta 4 KiB üstü Wi-Fi'ı bekler, TTL'de `Expired`; Wi-Fi ↔ Bluetooth geçişinde iki kanaldan gelen tekrar atılır; iki telefonun saati farklıyken fark tahmini (± gecikme / 2) ve 5 dk yenileme.
- A8.1'in 46 testi `HELLO` v2 ve eşleşmiş güvenle yeşil kalır.
- Android `:link` CI'da derlenir; Keystore ve dosya yolu **cihazda doğrulanmadı** (A8.3 + A8.5).
