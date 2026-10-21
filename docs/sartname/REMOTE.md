# REMOTE.md — Uzaktan kullanım (`:remote`, N2) şartnamesi

Durum: A9.1 (21 Ekim 2026, Claude Opus · high) — Bölüm 1 (ekran yakalama, H.264, akış; iki yönlü). Dokunuş iletimi, oturum işareti ve bitirme A9.2'de (Bölüm 2). Üst kaynak: `WEEM_PLANI.md` 4.6 ve Bölüm 8 (S12: iki yönlü ve tam). Eski kod ve eski test yok (N2 yeni; `OZELLIK_HARITASI.md` N2): testler WEEM'de yazılır (karakterizasyon değil, tasarım testi).

## 1. Ekran akışı (A9.1)

### 1.1 Roller ve kapsam

- Her telefonda iki bağımsız taraf (S12): **paylaşan** (bu telefonun ekranını yakalar, kodlar, yollar) ve **izleyen** (öteki telefonun ekranını çözer, gösterir). İkisi aynı anda da çalışabilir; 2 telefon rolünden (Provider/Receiver) bağımsızdır.
- Ön koşul: 2 telefon modu açık, eşleşmiş ve bağlantı **Wi-Fi** üstünden (`LinkStatus.transport == WIFI`). Bluetooth'ta uzaktan kullanım yok (yalnız Wi-Fi; plan 4.6): izleyen "Yalnız Wi-Fi" der, istek yollamaz.
- Video `:link` kanalından **geçmez**: link kanalı 65 KiB'lık iletiler, onay ve kuyrukla küçük mesajlar içindir; saniyede megabitlerce video oradaki canlılık ve teslim zorunlu iletileri bekletirdi. Link yalnız oturum denetimini taşır (1.3); video ayrı bir TCP akışındadır (1.4), anahtarı link üstünden geçici ECDH ile kurulur.
- Görüntü kaydedilmez, diske yazılmaz, loga girmez. S5 süzgeci (müşteri adı/telefonu Receiver'a gitmez) ekran görüntüsüne uygulanamaz: S12 kararı (yanındaki telefondaki her şey öteki telefondan yönetilir) bunu bilerek kapsar; görüntü yalnız eşleşmiş telefona, şifreli gider.

### 1.2 Paylaşma izni (MediaProjection)

- Ekran yakalama yalnız paylaşan telefonda kullanıcının sistem onayıyla başlar (Android 14+: her yakalama oturumunda yeniden). WEEM onay penceresine kendiliğinden basmaz.
- **Paylaşım oturumu** = bir onay. Onay verilince paylaşım `IDLE_STOP_MS` = **10 dk** izleyen olmadan geçene, kullanıcı "Paylaşımı bitir" diyene, sistem durdurana ya da 2 telefon bağlantısı/eşleşmesi kalkana dek açık kalır; aradaki izleme istekleri yeniden onay istemez (telefon cepteyken her seferinde onay olmasın). Kodlayıcı yalnız izleyen bağlıyken çalışır; boşta sanal ekranın yüzeyi `null`'dır.
- İstek geldiğinde paylaşım kapalıysa paylaşan telefonda bildirim (7405, kanal `remote`, 60 sn): "Öteki telefon bu ekranı görmek istiyor · İzin ver"; dokununca onay penceresi (`ConsentActivity`). Android 14+'ta tam ekran seçimi zorlanır (`createConfigForDefaultDisplay`). Reddedilirse izleyene `refused`.
- Paylaşım açıkken ön plan servisi (`mediaProjection`, bildirim 7404, kanal `remote`, ongoing): "Ekran paylaşılıyor" + izleyen bağlıysa "· izleniyor" + "Bitir" eylemi.

### 1.3 Oturum denetimi (link iletileri, teslim zorunlu, TTL 20 sn)

JSON (UTF-8), `schemaVersion` 1; bilinmeyen alan yok sayılır, bozuk yük atılır.

| Tür | Yön | Alanlar |
|---|---|---|
| `remote.ask` | izleyen → paylaşan | `session` (UUID), `pub` (izleyenin geçici P-256 açık anahtarı, base64, 65 bayt), `maxSide` (izleyenin istediği en uzun kenar) |
| `remote.offer` | paylaşan → izleyen | `session`, `state` (`consent` · `ready` · `refused` · `unavailable`), `ready`'de `pub` (paylaşanın geçici açık anahtarı) ve `port` |
| `remote.end` | iki yön | `session`, `reason` (`viewer` · `host` · `stopped` · `idle` · `error`) |

- Yeni `ask` eski oturumu (varsa) kapatır: izleyen bir tanedir. Gönderilme zamanı (`sentAtMs`, saat farkıyla) 20 sn'den eskiyse `ask` atılır (bayat istek paylaşım açmasın).
- Paylaşan her `ask` için yeni geçici anahtar üretir; `ready` onay ve paylaşım hazır olunca gider (`consent` önce). Ortak sır = ECDH(izleyen, paylaşan); özel anahtarlar yalnız bellekte, oturum bitince silinir.
- İzleyen `offer`'ı 15 sn içinde alamazsa "Öteki telefon yanıt vermiyor"; `consent`'te 60 sn bekler.

### 1.4 Akış (TCP, yalnız yerel ağ)

- Paylaşan, oturum varken TCP **47312**'yi dinler; yalnız yerel ağ alt ağından gelen bağlantı kabul edilir (`:link` kuralı). İzleyen paylaşanın adresine (`LinkStatus.peerHost`: etkin Wi-Fi link kanalının karşı adresi) bağlanır; soket Wi-Fi ağına bağlanır (mobil veri varsayılan ağ olsa da).
- Çerçeve: `[uzunluk u32 BE = 1 + gövde][tür u8][gövde]`, uzunluk ≤ 4 MiB; bozuk uzunluk/tür → kapat.
- **El sıkışma (düz):** tür 1 `HELLO` = `WRS1` · oturum 16 · nonce 16 (36 bayt). İzleyen bağlanınca yollar; paylaşan oturum tutarsa kendi `HELLO`'sunu (kendi nonce'u) yollar, tutmazsa kapatır. Anahtarlar: `prk = HMAC-SHA256(oturum ‖ nonce_izleyen ‖ nonce_paylaşan, ortak sır)`, `k_p→i = HMAC(prk, "WEEM remote 1 host" ‖ 0x01)`, `k_i→p = HMAC(prk, "WEEM remote 1 viewer" ‖ 0x01)` (HKDF-SHA256, 32 bayt). Her bağlantıda yeni nonce → yeni anahtar (yeniden bağlanmada nonce tekrarı yok).
- **Şifreli:** tür 2 `SEALED` = AES-256-GCM (nonce = 4 sıfır ‖ sayaç u64 BE, yön başına 0'dan, AD boş). Çözülemeyen (değişmiş, tekrarlanmış, sırası bozuk) → kapat. Paylaşan, izleyenden ilk geçerli şifreli ileti gelene dek hiçbir şey yollamaz ve 5 sn içinde gelmezse kapatır (anahtarı bilmeyen bağlantı kaynak tutmasın). Oturum başına tek akış: doğrulanan yeni akış eskisini kapatır.
- İç iletiler (`tür u8 · gövde`): paylaşan → izleyen 10 `CONFIG` (genişlik u16, yükseklik u16, SPS/PPS Annex-B), 11 `FRAME` (seq u32, pts µs i64, bayrak u8: bit 0 anahtar kare, veri Annex-B), 12 `PING`, 13 `BYE` (neden u8); izleyen → paylaşan 20 `ACK` (son seq u32, alınan bayt u64), 21 `KEY` (anahtar kare iste), 22 `BYE`. A9.2 dokunuşu buraya ekler (bilinmeyen iç tür yok sayılır).
- Canlılık: izleyen en geç 1 sn'de bir `ACK` (kare geldikçe en çok 50 ms'de bir); paylaşan 1 sn kare yollamadıysa `PING`. 5 sn hiçbir şey gelmezse taraf kapatır. İzleyen kopunca oturum sürüyorsa 1, 2, 4 sn (üst 4) aralıkla yeniden bağlanır; 3 başarısız denemeden sonra `ask` yeniler.

### 1.5 Kodlama ve akış denetimi

- Boyut (`CaptureGeometry`): ekranın en uzun kenarı `min(maxSide, 1280)`'e indirilir, oran korunur, iki kenar 16'nın katına yuvarlanır (en az 16). Kodlayıcı açılamazsa sırayla 1024, 800, 640 denenir; hepsi düşerse `unavailable`.
- H.264 donanım kodlayıcı, yüzey girişi: 30 kare/sn üst sınır (`max-fps-to-encoder`), değişmeyen ekranda 100 ms sonra kare tekrarı, anahtar kare 10 sn'de bir ve istek üzerine, B kare yok, düşük gecikme önceliği. Bit hızı başlangıç `0,1 × piksel × 30` (1280×576 → 2,2 Mb/sn), sınır 0,5–6 Mb/sn.
- `FlowControl` (paylaşan): yolda (gönderilmiş, onaylanmamış) bayt > `max(256 KiB, 0,5 sn × bit hızı)` ise yeni P kareleri **atılır**; ilk atmada kodlayıcıdan anahtar kare istenir ve bir anahtar kare yollanana dek P kare yollanmaz (bozuk referans olmasın). Tıkanma (atma) olunca bit hızı ×0,7 (en çok 1 sn'de bir); 3 sn tıkanmasız geçerse +250 kb/sn (üst sınıra dek).
- İzleyen: `CONFIG` gelince çözücü (yeniden) kurulur (`csd-0` SPS, `csd-1` PPS); anahtar kare gelene dek P kare çözülmez. `seq` atlarsa (paylaşan atmış) ya da çözücü hata verirse `KEY` istenir (en çok 500 ms'de bir). Ekran döndürülünce paylaşan yeni boyutla `CONFIG` + anahtar kare yollar.

### 1.6 Durum ve metinler

- İzleyen: `IDLE` · `ASKING` · `CONSENT` (öteki telefonda izin bekleniyor) · `CONNECTING` · `PLAYING` (boyut, kare/sn, kb/sn) · `ENDED` (neden: `refused`, `unavailable`, `noAnswer`, `noWifi`, `host`, `lost`). Paylaşan: `OFF` · `CONSENT` · `SHARING` (izleyen bağlı mı, kb/sn).
- Uzaktan sekmesi: üstte "Öteki telefonun ekranı" (Göster / Kapat; video oran korunarak, siyah zemin), altta "Bu telefonun ekranı" (Paylaşımı aç / bitir, durum). Metinler `RemoteText` (TR/AZ; AZ onay bekler, M-R-1).

### 1.7 Android katmanı (`:remote`, ince)

- `RemoteHub` (süreç merkezi): `LinkHub`'tan `remote.*` iletilerini ve bağlantı durumunu alır, saf `HostMachine`/`ViewerMachine`'i tek iş parçacığında sürer, komutları uygular. `CaptureService` (ön plan, `mediaProjection`): MediaProjection, sanal ekran, `MediaCodec` kodlayıcı. `ConsentActivity` (saydam): onay penceresi. `StreamSockets`: dinleyen/bağlanan TCP, okuma/yazma döngüleri. `Player`: `MediaCodec` çözücü → `SurfaceView` yüzeyi.
- `:link`'e eklenen: `LinkStatus.peerHost`, `LinkHub.onRemote` (`remote.*` iletileri köprüye değil buraya), `LinkHub.publishReliable`, `LinkHub.onLan`/`bindToLan`.

### 1.8 Testler (`logic/remote`)

Çerçeve (gidiş-dönüş, parça parça, sınır, bozuk), anahtar türetme ve şifreli iletim (iki yön ayrı, tekrar/değişiklik reddi, yeniden bağlanmada yeni anahtar), denetim iletileri (JSON, bozuk, eski), `CaptureGeometry` (oran, 16'nın katı, döndürme, yedek boyutlar), `FlowControl` (atma, anahtar kare bekleme, bit hızı azalt/artır), Annex-B (SPS/PPS ayırma), `HostMachine` + `ViewerMachine` uçtan uca sahte link ve sahte akışla: istek → onay → oynatma, onay reddi, paylaşım açıkken onaysız ikinci izleme, yanlış anahtarlı bağlantı, kopma ve yeniden bağlanma, kare atılınca `KEY`, Bluetooth'ta istek yok, boşta 10 dk sonra paylaşım kapanır, iki yön aynı anda.

### 1.9 Açık kalan ve riskler (cihazda doğrulanmadı)

- Android 15+'ta kilit ekranında sistem paylaşımı durdurabilir ya da kilit ekranı görünür; cepteki telefon kilitliyse yeniden onay gerekebilir. Wolt/Bolt `FLAG_SECURE` kullanırsa onların ekranı siyah görünür. Gecikme ve bit hızı hotspot'ta ölçülmedi. Bazı kodlayıcılar 16'nın katı olmayan boyutu reddeder (yedek boyutlar).
