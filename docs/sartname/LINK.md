# LINK.md — 2 telefon modu (`:link`) şartnamesi

Durum: A8.3 (20 Ekim 2026, Claude Opus · high) — Bölüm 4 (roller, mesajlar, portlar, ekranlar). A8.1 (18 Ekim 2026, Claude Opus · xhigh) — Bölüm 1 (taşıma) ve Bölüm 2 (Bluetooth ↔ scooter BLE ölçüm planı; ölçüm telefonda, henüz yapılmadı). A8.2 (18 Ekim 2026, Claude Opus · xhigh) — Bölüm 3 (eşleştirme, kimlik doğrulama, şifreleme, zarf, kuyruk, `ack`, saat farkı, sürüm); Bölüm 1'in değişen yerleri 3.4–3.5'te. Üst kaynak: `WEEM_PLANI.md` 4.4–4.5 ve Bölüm 8 (S5, S6, S11, S23, S24). Eski kod yok (N1 yeni; `OZELLIK_HARITASI.md` N1).

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
  - msg2 ← `e, ee, s, es` (yük boş)
  - msg3 → `s, se` (eşleştirmede `+ psk`) + başlatanın **bilgi yükü**
  - onay ← yanıtlayanın ilk taşıma iletisi + yanıtlayanın bilgi yükü
  - Bilgi yükü: `cihaz kimliği 16 · protokol ana u8 · küçük u8 · uygulama sürüm kodu u32 · sürüm adı (u8 boy + UTF-8 ≤ 32) · cihaz adı (u8 boy + UTF-8 ≤ 32)`; fazla bayt yok sayılır (ileri uyum). Bilgi yükü yalnız doğrulanmış eşe gider: yanıtlayanınki msg3'ten sonra, başlatanınki yanıtlayanı msg2'de doğruladıktan sonra (eşleştirmede veren başlatansa psk henüz doğrulanmamıştır; zararsız: adı ve anahtarı QR'da zaten var).
- **Denetim:** gelen kimlik anahtarı = eşleşme kaydındaki (eşleşmiş) ya da QR'daki (B) anahtar; A eşleştirmede her anahtarı alır, psk doğrular. Bilgi yükündeki kimlik = `HELLO`'daki. Tutmazsa `BYE(9)`. Başlatan msg3'ü yanıtlayanı msg2'de doğruladıktan sonra yollar; tutmazsa kendi anahtarını ve bilgisini göndermeden bırakır.
- **Anahtar onayı:** yanıtlayan msg3'ü doğrulayınca ilk taşıma iletisini (kendi bilgi yükü) yollar ve kanalı açar; başlatan kanalı bu iletiyi çözüp bilgi yükünü denetleyince açar (eşleştirmede başlatan, yanıtlayanın jetonu bildiğini ancak burada görür).
- El sıkışma (iki `HELLO` dahil) **10 sn**'de bitmezse `BYE(7)`; ilk `HELLO` için 5 sn (A8.1). Çözülemeyen ileti, geçersiz anahtar → `BYE(9)`; yanlış sırada/türde çerçeve → `BYE(4)`; geri çekilme A8.1 1.6 (ret).
- **Tek kanal kuralı** (A8.1 1.5) yalnız açılmış (doğrulanmış) kanallar arasında: sahte `HELLO` gerçek kanalı kapatamaz.
- **`LinkStatus`'a eklenen:** `channel` (etkin kanalın numarası; değişince son durumlar yeniden gider, 3.7), `issue` (son ret nedeni `VERSION` · `AUTH` · `PAIRING`; bağlanınca silinir; A8.3 gösterir).

### 3.5 Şifreli iletim

- Açık kanalda `DATA` yükü = Noise taşıma iletisi (yanıtlayanın ilk iletisi anahtar onayıdır, 3.4): AES-256-GCM, nonce = 4 sıfır bayt ‖ 64 bit büyük uçlu sayaç (yön başına 0'dan, her iletide +1, hiç tekrar etmez), AD boş, etiket 16 bayt. Yön anahtarları `Split()`'ten (başlatan → yanıtlayan ve tersi). Her kanalın anahtarı ayrı: Wi-Fi ↔ Bluetooth geçişinde iki kanal birlikte açıkken her biri kendi anahtarıyla.
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
- Protokol sürümü (bilgi yükü; bugün **1.0**, `LinkProtocol`): ana sürüm farklıysa iki taraf da el sıkışmayı bitirir (yanıtlayan onay iletisini yine yollar; bilgi yükleri doğrulanmış gelir), `PeerInfo` bildirir, `BYE(6)` ile kapatır, `issue = VERSION` (A8.3: "Öteki telefondaki WEEM uyumsuz (<sürüm adı>); ikisini de güncelleyin"). Küçük fark bağlanır (bilinmeyen alan ve tür yok sayılır). Uygulama sürümü farklıysa yalnız bildirilir (Daha fazla'da uyarı; A8.3).

### 3.11 Eşleşmeyi kaldırma

Kaldıran telefon bağlıysa `link.unpair` yollar (en iyi çaba), sonra eş kaydını, kuyrukları ve görülenleri siler, 2 telefon modunu durdurur; kimlik anahtarı kalır. `link.unpair` alan (yalnız doğrulanmış eşten gelebilir) aynısını yapar ve `Unpaired` bildirir. Bağlı değilken kaldırılırsa öteki telefon aramayı sürdürür ama bağlanamaz (`issue = AUTH`); orada da kaldırılır ya da yeniden eşleştirilir.

### 3.12 Saklanan veri ve loglar

- Tercih dosyası **`weem-link`** (`MODE_PRIVATE`, eşzamanlı `commit`): `identity.key` (Base64 NO_WRAP: IV 12 ‖ AES-GCM(PKCS#8) + etiket 16; AAD `weem-link/identity/v1`), `identity.public` (Base64 SEC1 65 bayt), `peer.id`, `peer.public`, `peer.name`, `peer.pairedAtMs`, `peer.btAddress`.
- Keystore takma adı **`WEEM.Link.v1`**: AES-256-GCM, şifrele/çöz, kullanıcı doğrulaması yok (ekran kilitliyken arka planda çalışır).
- Dosyalar `files/link/`: `outbox/<seq, 20 hane>.env` (teslim zorunlu zarflar; uygulamanın öteki kayıtları gibi özel depoda, ek şifresiz), `seq`, `seen`. Yedeğe ve dışa aktarmaya girmez.
- Saklanmaz: oturum ve geçici anahtarlar, jeton (yalnız bellekte). Saklanan kimlik bozuksa (etiket ya da Keystore anahtarı tutmuyor) yenisi üretilir ve yeniden eşleştirme gerekir (öteki telefonda `issue = AUTH`); Keystore'a o an ulaşılamıyorsa kimlik yenilenmez, bağlantı sonra yeniden denenir.
- **Loglar:** anahtar, jeton, QR metni ve düz metin yük hiçbir loga, `FailureSink`'e ya da çökme kaydına girmez; sır taşıyan sınıfların `toString`'i içeriği yazmaz; hata iletileri yalnız tür ve neden taşır.

### 3.13 Katmanlar (kod)

- `logic/link` (saf, JVM'de test): `Noise` (CipherState, SymmetricState, HandshakeState; `XX`, `XXpsk3`), `P256`, `Pairing` (`PairingQr`, `PeerInfo`, `LocalIdentity`, `PeerRecord`, `Trust`: eşleşmiş · veren · okuyan, `Fingerprint`), `SecureHandshake` (kanal başına el sıkışma), `LinkMachine` (A8.1 + el sıkışma + şifreli `DATA`; `LinkStatus.channel`/`issue`), `Envelope`, `Messenger` (kuyruklar, onay, tekrar atma, saat farkı), `ClockSync`, `MessengerStore` (`FileMessengerStore` = `files/link/`, `MemoryMessengerStore`), `LinkStack` (makine + `Messenger` tek kilit altında; `LinkRuntime` ve `FakeNet` aynısını kullanır), `IdentityVault` (sarma kuralı ve tercih anahtarları; `PairingVault` gibi ana anahtar dışarıdan).
- **Teslim zorunlu onayı:** `LinkStack` iletiyi `LinkCommand.Message` olarak verir; çağıran dinleyicileri çalıştırdıktan sonra `delivered()` der → `id` kalıcı görülenlere yazılır, onay gider (3.8). Dinleyici hata atsa da `delivered()` çağrılır (zehirli ileti döngüye girmesin).
- `:link` (Android, ince): `LinkVault` (Keystore `WEEM.Link.v1` + `weem-link`), `LinkRuntime` (`LinkStack`'i sürer): `start(listenOnly)` (eşleşme yoksa `false`), `startPairing()` → QR metni, `pairWith(qr)` → `QrResult`, `cancelPairing()`, `unpair()`, `publishLatest`/`publishReliable` → `id`, `fingerprint()`, `peer`, `status`; dinleyici: durum, ileti, eşleşti, pencere bitti, eşleşme kaldırıldı, eş bilgisi, onaylandı, süresi geçti, saat. Eş kaydını ve Bluetooth adresini kendisi saklar; saklanan kimlik bozuksa yenisini üretir, Keystore'a ulaşılamıyorsa kimliğe dokunmaz, bağlantıyı açmaz ve yalnız istisna türünü `FailureSink`'e verir (sonraki `start` yeniden dener). A8.1'in ham `send`/`onData`'sı kalktı.

### 3.14 Testler ve doğrulama

`logic/link` **131 test** (A8.1'in 46'sı `HELLO` v2 ve eşleşmiş güvenle güncellendi + 2 çerçeve testi; A8.2'nin 83'ü):

- `NoiseVectorsTest` 3: resmî vektörler (cacophony `XX` + `XXpsk3`, snow `XX`; `src/test/resources/noise/official-25519.json`, kaynak ve SHA-256 dosyada) her iletisi ve el sıkışma özetiyle birebir; değiştirilmiş msg2; CipherState nonce düzeni ve tekrar.
- `NoiseP256VectorsTest` 3: `tools/link-noise-reference.py`'nin ürettiği P-256 vektörleri (`XX`, `XXpsk3`, rolleri değişmiş `XX`; prologue, bilgi yükleri, onay, iki yönde taşıma) birebir; prologue ve bilgi yükü düzeni; ECDH bilinen yanıt. Betik `python3 -I` ile çalışır ve önce resmî vektörleri kendisi doğrular (`--check`; `--p256-verify` dosyayı yeniden üretip karşılaştırır).
- `P256Test` 4, `PairingQrTest` 8, `EnvelopeTest` 4, `IdentityVaultTest` 9, `MessengerStoreTest` 4, `ClockSyncTest` 3: kodlama ve geçersiz nokta reddi; QR gidiş-dönüş, ad kırpma, bilinmeyen parametre, yeni sürüm, bozuk alanlar, `toString`'de jeton yok, parmak izi, bilgi yükü ileri uyumu; zarf düzeni ve bozuk girdi; sarma/açma, AAD, bozuk ya da uyuşmayan anahtarın yenilenmesi, Keystore'a geçici olarak ulaşılamayınca kimliğin **yenilenmemesi**, eş kaydı; dosya düzeni, yeniden açma, bozuk dosya; fark/gecikme formülü ve süzgeç.
- `MessengerTest` 20 (tek başına, sahte kanal): üstel yeniden gönderim zamanları (0, 3, 9, 21, 45, 93, 153, 213 sn), kanal değişince hemen yeniden, en çok 16 onaysız, Bluetooth'ta büyük ileti Wi-Fi'ı bekler, son durum birleşir/TTL'de düşer/yalnız kanal değişince yeniden, alıcı her tekrarı onaylar ama bir kez verir, onay ancak `delivered`'dan sonra, eski `seq` ve TTL'si geçmiş son durum atılır, bozuk zarf sayılır, görülenler yeniden başlatmadan sağ çıkar ve TTL + 1 sa sonra unutulur, gönderen yeniden başlayınca aynı `id`/`seq`, `seq` yeniden başlatmada artar, kuyruk sınırı ve TTL'de `Expired`, TTL sınırları, saat ölçümü (kanal değişimi + 5 dk) ve yanıtı, düzeltilmiş zaman, eşleşme kaldırma, sıfırlama.
- `SecureLinkTest` 16 (`FakeNet`, uçtan uca): Wi-Fi'da okuyan ya da veren başlatan eşleşme, Bluetooth'ta eşleşme, yanlış jeton (jeton harcanmaz), süresi geçmiş jeton, kullanılmış jeton (üçüncü telefon), araya giren (iki yönü üstüne çeken sahte A: kimse eşleşmez, B msg3'ü ve şifreli veriyi hiç yollamaz), özel anahtarı olmayan sahte eş, anahtarı değişmiş eş (`AUTH`), değiştirilmiş şifreli metin ve tekrarlanan çerçeve (kanal kapanır, ileti bir kez), ana/küçük sürüm farkı, eşleşmeyi kaldırma, eşleştirme penceresinde eski eş bağlanamaz, takılan el sıkışma zaman aşımı.
- `MessengerLinkTest` 9 (`FakeNet`): iki yönlü akış ve onay, alıcı yeniden başlayınca son durum yeniden gelir, gönderen yeniden başlayınca teslim zorunlu gider, onayı kaybolan ileti alıcı yeniden başlasa da bir kez işlenir, Bluetooth'ta büyük ileti Wi-Fi'ı bekler, Wi-Fi ↔ Bluetooth geçişinde iki kanaldan gelen tekrar ve eski durum atılır, saati 7,3 sn farklı telefonlar (fark tam, ± gecikme / 2; 20 dk'da 8 ölçüm iletisi), yolda bayatlayan son durum, gönderilemeden süresi geçen teslim zorunlu.
- Android `:link` derlenir ve lint'ten geçer; Keystore, dosya yolu ve soketler **cihazda doğrulanmadı** (A8.3 + A8.5).

## 4. Roller ve mesajlar (A8.3)

Plan 4.3–4.5'in kodu. Saf kısım `logic/link` (`LinkRoles`, `LinkPayloads`, `PhoneBridge`, `LinkText`), Android `:link` (`LinkHub`, `LinkService`), bağ `:app` `TwoPhone` (modüller birbirini tanımaz). Cihazda doğrulanmadı.

### 4.1 Roller

- `LinkRole` `off | provider | receiver`, `weem-link` → `role` (tanınmayan değer `off`). Daha fazla → 2 telefon modu'ndan seçilir; değişince köprü yeniden kurulur ve portlar yeniden takılır (uygulama yeniden başlamaz).
- **S11:** Provider'da `ScooterShiftLink.providerRole` true: vardiya bağlantıyı açmaz (`role_provider`), `Scooter.startLive`/`startRecording` false döner, `ScooterService` başlatışı reddeder (`refuseStart`; CrashGuard 4 ret). Rol Provider'a geçince açık scooter bağlantısı kapanır.
- `listenOnly` = Receiver (scooter telefonu Bluetooth yedeğinde yalnız bekler, 1.3).
- İki telefon aynı roldeyse (`LinkRole` iletisi, iki yönde, bağlanınca) iki ekranda uyarı; Provider hedefi Receiver'a yollamaz.

### 4.2 Türler

| Tür (zarf adı) | Sınıf | TTL | Gönderen | Ne zaman |
|---|---|---|---|---|
| `ShiftState` | son durum | 24 sa | P | içerik değişince (`ShiftHub.state`; yalnız `generatedAtMs` değişimi sayılmaz) |
| `OfferSummary` (liste) | son durum | 2 dk | P | liste değişince (`lastSeenAtMs` yalnız 5 sn dilimine girince sayılır; RS E8), teklif varken 5 sn'de bir; arası ≥ 200 ms; boş liste = teklif yok |
| `TripStages` | teslim zorunlu | 1 sa | P | her değişimde |
| `DaySummary` | son durum | 24 sa | P | değişince, en çok dakikada bir (gün değişince hemen); `OrderDaySummary`: platform başına teslim, brüt, net; çevrimiçi dakika ve nakit ölçülmez (null) |
| `NavTarget` | teslim zorunlu | 2 dk | P | Wolt/Bolt "Navigasyon" ya da pano hedefi çözülünce |
| `EnergyRequest` | teslim zorunlu | 24 sa | P | ekonomi bir bacak ya da boşta dönem satırı yazarken |
| `EnergyResult` | teslim zorunlu | 24 sa | R | istek gelince (yerel `ScooterBridge.energy`; kaynak yoksa `not_measured`) |
| `BatterySummary` | son durum | 5 dk | R | `BatterySummary.due` (60 sn, %1, bağlantı değişimi) ya da kare tazeliğini (≤ 15 sn) yitirince/kazanınca; 10 sn'de bir denenir; scooter hiç yoksa bağlantısız özet |
| `RouteStatus` | son durum | 60 sn | R | hedef ya da rota ölçümü değişince, en çok 1/sn; rota yoksa hedefsiz ve ölçümsüz |
| `LinkRole` | son durum | 24 sa | ikisi | bağlanınca |

`WeatherNow` ve saniyelik `ScooterLive` tür değildir (S6; plan 4.4). Yanlış yönden gelen tür (iki Provider), bilinmeyen tür ve bozuk yük atılır.

### 4.3 Yükler ve saat

- Yük = `:core` mesajının JSON'u (`toJson`/`parse`), UTF-8; teklifler `{schemaVersion: 1, offers: [...]}`, rol `{schemaVersion: 1, role}`.
- Eşten gelen yüklerdeki zamanlar `yerel = eşin saati − fark` ile bu telefonun saatine çevrilir (3.9): `ShiftState` başlangıç/bitiş/üretim, teklif ilk/son görülme ve bitiş, aşama olayları ve bitiş, hedef istek anı, enerji penceresi (Receiver kendi örneklerini kendi saatiyle okur), batarya özeti anı, rota hedefi/ölçümü/güncellemesi. Gün özeti saat taşımaz.
- Vardiya izlemede kimlik ham (eşin saatindeki) başlangıç/bitiştir: saat farkı tahmini oynasa da aynı vardiya bir kez işlenir.

### 4.4 Portlar (modüller farkı görmez)

- **Provider:** `Orders.bindScooter` ve `BatteryRuntime.bind` uzak `ScooterBridge`'i alır: `live` = son `BatterySummary` (yalnız iç/harici %, menzil, sıcaklık; hız yok), bağlıyken ve özet ≤ 75 sn ise canlı; `batteryPct()` yalnız canlıyken; `steps` boş (sipariş başına Wh Provider'da ölçülmez, enerji satırlarında); `orderAccepted/Done` Receiver'da aşamalardan türetilir (4.6). `NavController.forward` hedefi Receiver'a yollar.
- **Receiver:** `DayRuntime.bindOrders` uzak `OrderEvents`'i alır (teklifler 15 sn ileti gelmezse ve bağlantı kopunca boşalır; aşamalar ve gün özeti son hâlinde kalır). Siparişler sekmesinin üstünde Provider kartı (vardiya, teklifler, açık sipariş, gün özeti).
- **Tek telefon:** `Orders.events` (yeni `OrderEvents` üreticisi: vardiyada 500 ms, kapalıyken 5 sn; gün özeti 5 sn) `DayRuntime.bindOrders`'a bağlanır (A6.8'in açık işi kapandı).

### 4.5 Navigasyon hedefi

Provider'da `LinkIntake` → `NavController.onExternalText` hedefi çözer; 60 m yakınlık denetiminden sonra `forward` bağlıysa `NavTarget`'ı yollar ("Rota öteki telefonda açılıyor"), bu telefonda rota açılmaz. Bağlı değilse ya da 8 sn içinde onay gelmezse rota Provider'da açılır. Receiver aynı `requestId`'yi bir kez açar ve harita sekmesine geçer; TTL'den (2 dk) eski hedefi açmaz (Provider zaten açtı). Etiket restoran adı ya da adres metni (S5).

### 4.6 Enerji dönüşü

- Provider'da ekonomi satırı yazılırken `energy(from, to)`: istek teslim zorunlu gider ve çağrı **beklemeden** null döner; satır `not_measured` yazılır. Neden: satır ekran okuma olayında ana iş parçacığında `runBlocking` ile yazılır (`OrderFlow` gözlemcisi), uzak yanıtı beklemek ANR'dir. (`PhoneBridge` bekleme süresi parametre; 0'dan büyüğü yalnız testte.)
- Geç yanıt `ScooterBridge.late`'e düşer (bağlıyken Wi-Fi'da ~0,1 sn sonra) → `EconomicsObserver.lateEnergy` pencerenin (başladığı/bittiği gün) ölçülmemiş son satırının kopyasını yeni damga ve ölçülen enerjiyle ekler (okuyucular dönem başına son satırı alır). İstek kimliği pencereyi taşır (`e1:<from>:<to>:<uuid>`): Provider yeniden başlasa da eşleşir. Zaten ölçülmüş satır ve ölçülmemiş yanıt düzeltme yazmaz.
- Receiver aynı `requestId`'ye aynı yanıtı verir (teslim zorunlu tekrarı). Aşamalardan: yeni açık sipariş → `orderAccepted` (ilk olay anı), kapanan → `orderDone` (bitiş anı); ilk iletideki açık siparişler sürüyor sayılır (sayaç sıfırlanmaz).

### 4.7 Android

- `LinkHub` (süreçte tek): rol, `LinkRuntime`, `PhoneBridge`, eş kaydı/bilgisi, eşleştirme penceresi, bildirimler (eşleşti, süre doldu, eş kaldırdı); saniyede bir `tick`. `:app` `TwoPhone.start` `NavRuntime`'dan sonra `localFor` (role göre yerel portlar) ve `onBridge` (yeniden bağlama) verir.
- `LinkService`: mod açık ve eşleşmişken ya da eşleştirme penceresinde ön plan servisi, `connectedDevice` (`CHANGE_NETWORK_STATE`), bildirim **7403** kanal **`link`** (LOW, ongoing; metin bağlantı durumu). Her başlatış `startForeground`'a varır (CrashGuard).
- Vardiya (Receiver): Provider'ın her başlangıcı/bitişi bir kez `ShiftHub.start/stop("link")`; Receiver'da daha sonra elle verilen karar ezilmez (yerel bitiş Provider'ın başlangıcından sonraysa yeniden açılmaz; yerel başlangıç Provider'ın bitişinden sonraysa durdurulmaz).

### 4.8 Gizlilik (S5)

Giden her yük `ReceiverSafety`'den geçer: anahtarında `customername`, `customerphone`, `phone`, `tel`, `note`, `comment`, `instruction`, `firstname`, `lastname`, `fullname`, `recipient` geçen ya da `name`/`customer` olan yük yollanmaz (`UnsafePayload`, hata kaydı yalnız anahtar adı). Tipler bu alanları zaten taşımaz; `TwoPhonePrivacyTest` sipariş akışında adı, telefonu, notu, talimatı ve kapı kodu olan bir teklifin ve siparişin yükünde bunların olmadığını, adres metninin olduğunu denetler.

### 4.9 Testler

`logic/link` +34 (toplam 165): eski `FeedChangesE8Test` 8 çevrildi (RS @ 6388c04: içerik değişince yayın, kare saati/kare yaşı/üretim saati değişim değil, görünen teklif 5 sn dilimle yeniden, ≥ 200 ms aralıkta son değişiklik mutlaka çıkar, titreşim gitmez, scooter tazeliğini yitirince bildirilir, gün özeti birleştirilir); `LinkPayloadsTest` 5 (tür tablosu, gidiş-dönüş, saat düzeltmesi, bozuk yük, S5 süzgeci), `PhoneBridgeTest` 17 (rol çakışması, teklif hız sınırı ve tazeleme, sessizlik/kopmada boşalma, aşamalar ve sipariş sayacı, gün özeti, vardiya izleme ve elle karar, hedefin bir kez açılması, yerel yedek, enerji yanıtı, geç enerji ve yeniden başlatmadan sonra eşleşme, tekrar isteğe aynı yanıt, batarya özeti → canlı değer, rota hız sınırı, yön süzgeci, saat), `TwoPhoneLinkTest` 2 (gerçek `LinkStack` + `FakeNet`, saati 7,3 sn farklı telefonlar: vardiya, teklif, hedef, batarya, enerji penceresi), `LinkViewTest` 2; `logic/economics` `LateEnergyTest` 2; `logic/orders` `OrderDaySummaryTest` 1; `logic/map` `NavControllerForwardTest` 3; `logic/app` `TwoPhonePrivacyTest` 1; `CrashGuardTest` ve `NotificationIdsTest` güncellendi.

### 4.10 Ekranlar

Daha fazla → **2 telefon modu**: rol (Kapalı / Provider / Receiver) ve rol notu, hotspot notu, durum kartı (`LinkView.lines`: bağlı Wi-Fi/Bluetooth + RTT, aranıyor + ret nedeni, yerel ağ/Bluetooth eksikliği, rol çakışması, uygulama sürümü farkı), bu telefonun ve eşin parmak izi, "Eşleştirme QR'ı göster" (ZXing, 5 dk geri sayım, vazgeç), "Öteki telefonun QR'ını tara" (Google code scanner), "Eşleşmeyi kaldır" (onaylı). Receiver'da Siparişler sekmesinin, Provider'da Scooter sekmesinin üstünde öteki telefonun kartı.

### 4.11 Açık kalan (A8.3 sonrası)

- Provider'da sipariş başına Wh (`TripRecord.usedWh`, gün raporunun sipariş enerjisi) ölçülmez: enerji yalnız `economics-energy.jsonl` bacak satırlarında. Gün raporunun bacak satırlarından Wh alması ayrı iş (karar gerekir).
- Receiver'da vardiyanın Provider'ı izleyerek arka planda açılması Android 12+ ön plan servisi başlatma kısıtına takılabilir (hata kaydedilir; o zaman vardiya Receiver açılınca elle). Telefonda görülür.
- Receiver'ın kendi ev kuralı (otomatik vardiya) kapatılmadı; iki telefon birlikte olduğundan çakışma beklenmez, telefonda görülür.
- Her uzak enerji iki satır olur (önce `not_measured`, sonra düzeltme); bacak satırlarının okuyucusu yazılırsa o da pencere başına son satırı almalı.
- Tanı ekranı (`MessengerStats`, kuyruk boyu) ve LINK.md Bölüm 2 ölçümü A8.4/A10'da.

### 4.12 Metin onayı

`LinkText` ve `NavText.FORWARDED` AZ metinleri onay bekler (M-L-1).

## 5. Hırsızlık koruması (A8.4, N6)

Kullanıcı kararı S10 (21 Ekim): scooter + Poco, otomatik + elle, alarm iki telefonda + Poco ekranı, bağlantı koparsa Poco SMS ile konum. Aşağıdaki sayılar ve kurallar ajanın varsayılanıdır (kullanıcıya bildirildi; değiştirmek kullanıcı kararı). WEEM scooter'a **salt okur** (`SCOOTER.md` 2): kilitleyemez, scooter alarmını çaldıramaz; koruma = algılama + alarm. Yalnız 2 telefon modunda ve **Receiver'da** çalışır; tek telefonda kapalı. Cihazda doğrulanmadı.

### 5.1 Durumlar (saf `logic/link` `TheftGuard`, Receiver'da saniyede bir `tick`)

`OFF` → `ARMING` → `ARMED` → `WARNING` → `ALARM`; her durumdan `disarm` → `OFF`.

- **Devreye alma (otomatik, ayar açıksa):** (a) aşama: açık siparişlerden biri `AT_PICKUP` ya da `AT_DROPOFF` (`TripStages`); (b) Pixel uzak: bağlantı ≥ 60 sn kopuk. İkisinde de scooter durmuş olmalı: hız ≤ 2 km/s (ya da scooter karesi yok) ≥ 5 sn. Sonra `ARMING` 20 sn (iniş payı); bu sürede scooter > 2 km/s olursa geri sayım baştan.
- **Elle:** "Park" — Pixel'den (`TheftCommand arm`) ya da Poco'dan; koşulsuz `ARMING` 20 sn.
- **Kendiliğinden kapanma** (yalnız otomatik devreye alındıysa ve `WARNING`/`ALARM` değilken): (a) ile alındıysa o sipariş artık `AT_PICKUP`/`AT_DROPOFF` değil (aşama Pixel'den gelir = sahibin işlemi); (b) ile alındıysa bağlantı geri geldi. Elle alınan yalnız elle kapanır.
- **Tetik (`ARMED`):** (T1) taze scooter karesinde hız ≥ 4 km/s, ≥ 2 sn; (T2) Poco hareketi: doğrusal ivme ≥ 1,2 m/s² olan en az 3 örnek, 5 sn pencerede ve ilk ile son arası ≥ 1 sn; (T3) GPS: doğruluk ≤ 20 m iken devreye alma noktasından ≥ 25 m. Scooter Bluetooth kopması tek başına tetik değil (scooter kendiliğinden kapanabilir).
- **`WARNING` 8 sn:** Poco ekranında uyarı + kısa bip, Pixel'de titreşimli bildirim; süre dolunca (kapatılmadıysa) `ALARM`.
- **`ALARM`:** Poco yüksek siren (alarm sesi kanalı, sessiz kipi aşar; 10 dk sürekli, sonra dakikada 30 sn), kırmızı tam ekran uyarı; Pixel yüksek alarm + titreşim + tam ekran bildirim. Kapatılana dek sürer.
- **Kapatma:** Pixel'den her zaman (`TheftCommand disarm`; kanal kimlikli ve şifreli, hırsız taklit edemez). Poco'da 4 haneli PIN ile (PIN ayarlanmamışsa 3 sn basılı tutma; ayar ekranı uyarır). PIN Poco'da yalnız tuzlu SHA-256 özeti olarak saklanır.

### 5.2 Mesajlar

| Tür | Sınıf | TTL | Gönderen | Yük |
|---|---|---|---|---|
| `TheftState` | son durum | 60 sn | R | `schemaVersion` 1, `state`, `armedBy` (`stage`\|`away`\|`manual`\|null), `sinceMs`, `trigger` (`speed`\|`motion`\|`gps`\|null), `lat`, `lon`, `accuracyM`, `fixAtMs`; durum değişince ve `WARNING`/`ALARM`'da 10 sn'de bir |
| `TheftCommand` | teslim zorunlu | 2 dk | P | `schemaVersion` 1, `commandId`, `action` (`arm`\|`disarm`), `atMs` |

Pixel'de son `TheftState` ve son konum (saatiyle) bağlantı kopsa da gösterilir ("Son bilinen konum: … önce", haritada aç).

### 5.3 SMS (Poco)

- `ALARM` sürerken Pixel bağlantısı ≥ 15 sn kopuksa ilk SMS; sonra konum ≥ 50 m değiştiyse 3 dk'da bir, değişmediyse 10 dk'da bir; alarm başına en çok 20 SMS. Alarm kapanınca bir "kapatıldı" SMS'i (en az biri gittiyse).
- Metin: `WEEM ALARM: scooter hareket etti. Konum: https://maps.google.com/?q=<lat>,<lon> (±<m> m, <SS:dd>)`; konum yoksa "konum yok".
- Numara (en çok 2) yalnız Poco ayarında; bağlantı yüküne girmez (S5 süzgeci zaten `phone` anahtarını engeller). İzin `SEND_SMS` (Xiaomi'de ek "servis SMS" izni gerekebilir; ayar ekranı yönlendirir). SIM yoksa ya da izin yoksa SMS atlanır, hata kaydedilir, Poco ekranında not.

### 5.4 Ayarlar

Daha fazla → 2 telefon modu → **Hırsızlık koruması**: Açık/Kapalı (varsayılan kapalı), otomatik devreye alma (aşama / Pixel uzak, ikisi de açık), Poco'da: PIN, SMS numaraları, SMS izni, "Siren dene" (3 sn). Durum kartı iki telefonda: durum, kim aldı, son konum.
