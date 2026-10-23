# SCOOTER — Aşama 5 şartnamesi (`:scooter`: S1–S8, R14)

Durum: A5.1, 17 Ekim 2026 (Claude Opus · high, tek ajan) — bölüm 0–14 yazıldı. **A5.2 (17 Ekim, Opus · xhigh):** protokol, şifreleme, oturum, eşleşme, yeniden bağlanma kodu ve 14 suit `logic/scooter`'da yeşil; taşıma kaydı ve WEEM farkları 13.4, 14. **A5.3 (17 Ekim, Sonnet · high):** servis, kayıt, dışa aktarma, sayaç, enerji, `ScooterBridge`, vardiya bağı yazıldı; taşıma kaydı 13.5. Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04` (Rawstats 0.25.1; Sürüş Paneli `BuildInfo.VERSION` **0.4.3**); satır numaraları bu commit'te açılıp doğrulandı. Ham bulgu yok (`bulgular/` scooter içermez; `X_protocol.md` yalnız `scooter/v1/live` yükü, P4); bu belge kaynak koddan tam çıkarmadır.

Kısaltmalar: **NB** = `ninebot-logger/app/src/main/java/az/ninebot/logger/` (Sürüş Paneli, Java, Gradle `:surus-paneli`, namespace `az.ninebot.logger`), **NT** = `ninebot-logger/tests/` (main() tabanlı suitler), **NR** = `ninebot-logger/tools/reference/`, **RS** = `app/src/main/java/com/kuryelog/app/`, **RT** = `app/src/test/java/com/kuryelog/app/` (Rawstats). Davranış metni kodun söylediğidir; "WEEM" satırları bu şartnamenin kararıdır.

**Kim kullanır:** A5.2 (protokol, şifreleme, oturum, eşleşme, yeniden bağlanma ve testler: Bölüm 2–5, 13), A5.3 (canlı panel, kayıt, sayaç, harici batarya, enerji: Bölüm 3, 6–9, 11), A5.4 (yüzen panel, tanı, Scooter sekmesi: Bölüm 6, 10, 12), A8 (2 telefon: Bölüm 12). Bölümler: 0 özet · 1 kaynak · 2 S1 protokol · 3 okunan değerler · 4 eşleşme ve anahtar deposu · 5 S6 yeniden bağlanma · 6 S2/S7 panel · 7 S3 kayıt · 8 S4 sayaç ve R14 enerji · 9 S5 harici batarya · 10 S8 tanı · 11 veri sözleşmesi · 12 WEEM yerleşimi · 13 test eşlemesi · 14 açık noktalar ve riskler. Plan bağlamı: `WEEM_PLANI.md` 3 (S1–S8, R14), 4.2–4.4 (`:scooter`, `ScooterBridge`, `ScooterLive`/`BatterySummary`/`Energy*`), 8 (S8 net kazanç, S11 tek bağlantı).

## 0. Özet: WEEM kuralları

| Konu | WEEM kuralı | Neden / kaynak |
|---|---|---|
| Cihaz ve yol | Segway-Ninebot Max G3; tek BLE GATT bağlantısı, Nordic UART (`6e400001/2/3-b5a3-f393-e0a9-e50e24dcca9e`), yazma `WRITE_NO_RESPONSE`, MTU 247 istenir; GATT sırası `NinebotLink` ile, her adımda 15 sn bekçi | 2.1; NB `BleCapture.java`, `NinebotLink.java` |
| Salt okuma | Yalnız okuma (`0x01`) ve el sıkışma (kart 4: `0x5b` PRE_COMM, `0x5c` PAIR, `0x5d` AUTH); başka komut atılamaz (koruma + kaynak taraması testi) | 2.6; `NinebotSession.java:75-78` |
| Çerçeve | `5a a5 LEN 3e BOARD CMD INDEX DATA` (+6 bayt şifreli kuyruk); birleştirme sınırları 3 sn / 2.048 / 268 bayt | 2.2 |
| Şifreleme | "Encryption2": SHA-1 türetilmiş AES-128 anahtar; PRE_COMM sabit `FW` + sağlama; oturumda sayaçlı AES-CTR + 4 baytlık CBC-MAC; tekrar/düşürme reddi; birebir Kotlin, referans `nb_crypto.py` (Apache-2.0) | 2.3, 10.3 |
| El sıkışma | PRE_COMM (5 sn × 3) → anahtar varsa AUTH (5 sn × 3, veri = 14 bayt seri) → READING; eşleştirmede PAIR (anahtar = SecureRandom 16 bayt, 2 sn'de bir, 300 sn pencere, ilk yanıtta titreşim + bildirim 8) | 2.4, 4.2 |
| Oturum sabitleri | **Tek bekleyen istek**, **1,2 sn** yanıt penceresi, aralık 5/20 ms (izleniyor/izlenmiyor), hız ≥150/400 ms ve arada ≥2 okuma, seyrek sayaçlar her 3. turda, yanıtsız register 3'te düşer, **12 ardışık zaman aşımı** = yeniden bağlan | 2.5 |
| Okunan değerler | VCU `0x16`: pil %, menzil, odometre, yaşam sayaçları, sıcaklık, hız/sürüş km/sürüş sn (aday); BMS `0x8c` V, `0x8d` A (işaretli), `0x8f` %, `0x8a` ham; küçük uçlu, ölçek/aralık tablosu; tazelik 3/15 sn; güç = V×A (iç + onaylı harici) | 3 |
| Eşleşme deposu | `pairing-v1` + Keystore `NinebotLogger.Pairing.v1` (AES-GCM, AAD = adres); aday/etkin/önceki; **adlar ve biçim değişmez** | 4.1; `VERI_SOZLESMESI` 5.3 |
| Yeniden bağlanma (S6) | 1, 2, 4, 8 sn, sonra 15 sn (24 denemeye kadar), sonra 60 sn; okuyan bağlantı sıfırlar. M23: veri kesildi/geldi bildirimi 4902 (30 sn, ≥10 km/s) | 5 |
| Kim bağlanır (S11) | Tek telefonda WEEM; 2 telefonda **yalnız Receiver**; Provider BLE açmaz | 5.3, 12.3 |
| Canlı panel ve yüzen panel (S2, S7) | Kare her ölçümde; 5 sn bayat; boş değer "–" (asla 0); yüzen top 4 değer, 80 ms çizim, geometri `FloatGeometry`; PiP yalnız üstte gösterme izni yokken | 6 |
| Kayıt (S3) | Canlı kip (yazmaz) / kayıt (SQLite `ninebot-logger.db` + GPS 1 Hz, durunca 3 sn); ayrıntılı teşhis ilk 10 dk, bütçeler 10.000 + 10.000, kuyruk 2.048; ZIP izin listesi aynı | 7 |
| Sayaç ve enerji (S4, R14) | 1 Hz örnek; adım ≤3 sn, yalnız iki paketin gücü (D10); blok kapsama ≥%80, uç ≤15 sn; `economics-scooter.jsonl` aynı; sipariş enerjisi `ScooterBridge.steps` (yeni arayüz, A5.3) | 8, 12.2 |
| Harici batarya (S5) | Bir kerelik salt okuma yoklaması (31 aday, voltaj 30–60 V, yüzde onaylar; sahada `0x06`); sessiz paket 15 sn'de bir (D12); duyuru 60 sn kararlılık | 9 |
| Tanı (S8) | Sweep `0x16:00–c0`, ayrıntılı teşhis, tam ZIP (hassas uyarısı), bilgi sayfası; dizüstü araçları CI dışı | 10 |
| Modül sınırı | `logic/scooter` saf (protokol, kurallar, panel, enerji, politikalar); `:scooter` Android (GATT, servis, Keystore, SQLite, arayüz); `:scooter` → yalnız `:core`; dışarıya yalnız `ScooterBridge` | 12.1 |
| Açık soru | S18: `fail()` sonrası bağlantının boşta kalması (yeniden deneme yok) | 14, R-S3 |

## 1. Kaynak haritası

### 1.1 Sürüş Paneli (NB, 33 dosya, 1.980 satır; satırlar çok uzun, toplam ~238 KB)

| Katman | Dosyalar (satır) | Android? | WEEM |
|---|---|---|---|
| Protokol çekirdeği | `NinebotCrypto` (33), `NinebotFrames` (18), `NinebotSession` (172), `NinebotMetrics` (81), `NinebotMode` (9), `NinebotLink` (16), `SessionKey` (24), `Timing` (17) | hayır | `logic/scooter` (A5.2), satır satır |
| Saf yardımcılar | `NinebotDashboard` (41), `ScooterEnergy` (74), `TripMeter` (54), `PackPresence` (28), `NinebotEventQueue` (10), `NinebotLogPolicy` (23), `NinebotGpsPolicy` (11), `FloatGeometry` (40), `PanelField` (42) | hayır | `logic/scooter` (A5.3, A5.4) |
| BLE ve servis | `BleCapture` (93), `RecordingService` (189), `KeyVault` (27), `PairAttention` (27), `LoggerPreferences` (21) | evet | `:scooter` Android (A5.2, A5.3) |
| Kayıt deposu | `LogStore` (76), `LogExport` (35) | `LogStore` evet, `LogExport` org.json | `LogExport` saf; `LogStore` Android (A5.3) |
| Arayüz | `DashboardActivity` (220), `DashboardView` (110), `MainActivity` (130), `FloatingPanel` (146), `FloatPainter` (40), `InfoSheet` (35), `Ui` (40), `RawPalette` (25) | evet | Compose Scooter sekmesi + yüzen panel (A5.4) |
| Dış yüz | `SurusPaneli` (66: Rawstats'ın kullandığı tek giriş), `BuildInfo` (7) | evet | kalkar: `:scooter` `ScooterBridge` uygular (11) |

Testler: NT 14 suit (`run.sh` sırası: `TimingTest`, `ProtocolTest`, `LinkTest`, `PairFlowTest`, `SweepTest`, `PowerCadenceTest`, `DashboardTest`, `SpeedCadenceTest`, `EventQueueTest`, `LogPolicyTest`, `BatteryProbeTest`, `EnergyTest`, `TripMeterTest`, `FloatingTest`) + `ReferenceProbe.java` (suit değil, yardımcı); Gradle sarmalayıcı `app/src/test/java/SurusPaneliJvmTest.java`; cihazda `app/src/androidTest/.../KeyVaultInstrument.java`, `LogExportTest.java`; Python (CI dışı) `reference_parity.py`, `laptop_client_test.py`, `upgrade_smoke.py`, `emulator_smoke.py`. 17 Ekim'de bu oturumda `bash ninebot-logger/tests/run.sh` JDK 21'de çalıştırıldı: 14 suit yeşil (çıktıdaki sayılar Bölüm 13).

Belgeler (davranış kaynağı değil, bağlam): NB üstü `README.md`, `VALIDATION.md`, `docs/G3_HANDSHAKE_0.2.2.md`, `G3_PAIR_0.2.3.md`, `G3_HCI_2026-10-04.md`, `G3_ARASTIRMA_2026-10-04.md`, `FIELD_TEST_2026-10-03.md`, `FIELD_TEST_2026-10-09.md`, `APK_ANALYSIS.md`, `KURYEPLUS_BIRLESTIRME.md`; araç `tools/g3_readonly.py` (dizüstü istemci, S8).

### 1.2 Rawstats tarafı (RS)

| Dosya | Ne yapar | WEEM |
|---|---|---|
| `ScooterBridge.kt` (44) | KuryePlus'ın scooter'la tek dikişi: `Source` (`energy`, `batteryPct`, `orderAccepted`, `orderDone`); her çağrı `runCatching` | `logic/core` `ScooterBridge` (A2.5, `Ports.kt:36-50`); `:scooter` uygular |
| `ScooterLink.kt` (220) | `Source` uygulaması; vardiya başında canlı bağlantı açar, sonunda kapatır; 1 Hz örneği `economics-scooter.jsonl`'e yazar, siparişe enerji adımı verir, D8 odometre; M23 bildirimi; ana kart satırı | `:scooter` (A5.3) |
| `ScooterReconnect.kt` (41) | M23 saf durum makinesi: veri kesildi / geldi | `logic/scooter` (A5.2) |
| `ScooterSamples.kt` (52) | `economics-scooter.jsonl` satırı ve iki örnek arası enerji adımı | `logic/scooter` (A5.3) |
| `ScooterFeed.kt` (38) | `scooter/v1/live` yükü (YolGo'ya) | `logic/core` `ScooterLive` (A2.5); sağlayıcı kalkar (S7) |
| `EnergySegments.kt` | `energy` bloğu biçimi, `energyFrom(map)`, `PREF_SCOOTER`, `DEFAULT_SCOOTER` | `:economics` (A4.1); `:scooter` bloğu üretir |

## 2. S1 — Scooter bağlantısı: GATT, çerçeve, şifreleme, el sıkışma

Hedef cihaz: Segway-Ninebot **Max G3** (alan adları "G3"; "Gen2" yalnız şifreleme girdisinin adı). Bütün iletişim tek BLE GATT bağlantısı, tek profil ("profile 0" = Nordic UART). **Salt okuma:** yalnız okuma (komut `0x01`) ve el sıkışma (kart `0x04`, komut `0x5b/0x5c/0x5d`) gönderilir (2.6).

### 2.1 UUID'ler ve GATT sırası (NB `BleCapture.java`)

| Ad | UUID | Kullanım (satır) |
|---|---|---|
| Nordic UART servisi | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | karakteristikleri `chars`'a alınır (22, 79) |
| Ninebot özel servisi | `6e400001-0000-0000-006e-696e65626f74` (son 7 bayt ASCII "ninebot"; A5.2 düzeltmesi, önce "6 bayt" yazıyordu) | karakteristikleri `chars`'a alınır ama hiçbiri kullanılmaz (22, 79) |
| Yazma (RX) | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | `WRITE_TYPE_NO_RESPONSE`; özelliğinde `PROPERTY_WRITE_NO_RESPONSE` yoksa profil "yok" sayılır (45, 49) |
| Bildirim (TX) | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | yalnız Nordic servisindeki ve NOTIFY ya da INDICATE özellikli olanı; her biri kendi `NinebotFrames`'i (79) |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` | NOTIFY varsa `ENABLE_NOTIFICATION_VALUE`, yoksa `ENABLE_INDICATION_VALUE` (23, 44) |
| Cihaz adı | `00002a00-0000-1000-8000-00805f9b34fb` | READ özelliği varsa okunur; boş değilse oturum adı olur (79-82) |

Sıra (`NinebotLink`, NB `NinebotLink.java:5-15`; her geçiş yalnız beklenen aşamada kabul edilir, yinelenen ya da sırasız geri çağrı yok sayılır):

1. `connectGatt(context, autoConnect=false, callback, TRANSPORT_LE)` (38). Adaptör yok ya da kapalı → `failed("Bluetooth kapalı; GPS kaydı sürüyor")` (yeniden deneme sayacı ilerler, 5).
2. **Bekçi:** her GATT isteğinden sonra 15 sn zamanlayıcı (`watch`, 37); dolarsa `failed("Bluetooth işlem zaman aşımı")` (24). Yazma geri çağrısı gelince kaldırılır (87).
3. `onConnectionStateChange`: `status≠0` ya da `DISCONNECTED` → `failed("Bağlantı koptu (<status>); GPS kaydı sürüyor")`; `CONNECTED` → olay `ble_connected`, `discoverServices()` (77).
4. `onServicesDiscovered` (`DISCOVERING` → `NAME` ya da `MTU`, 78-80): bütün servis/karakteristik listesi olay `gatt_inventory`'ye; bildirim karakteristiği yoksa durum "GATT kaydedildi; desteklenen kanal yok" ve **hiçbir şey yapılmaz** (bağlantı açık kalır, bekçi kaldırılmıştır; 14, R-S3).
5. Ad okuma (`NAME` → `MTU`, 82-84): UTF-8, `\u0000` silinir, `trim`; okuma hatası adı değiştirmez.
6. MTU (`MTU` → `SUBSCRIBING`, 43, 85): `requestMtu(247)`; istek reddedilirse 23 ile sürer; `onMtuChanged` `status==0` ise `mtu=value`; olay `ble_mtu`.
7. Abonelik (`SUBSCRIBING`, 44, 86): karakteristik başına `setCharacteristicNotification` + CCCD yazımı (API ≥33 `writeDescriptor(d, value)`, eskide `setValue` + `writeDescriptor`); tek bekleyen tanımlayıcı (`expectDescriptor`/`descriptorReady`). Hiçbiri başarılmazsa `failed("Bluetooth bildirim kanalı açılamadı; GPS sürüyor")`.
8. `SETTLING` → **200 ms** sonra `PROTOCOL` (25, 44): olay `ble_subscribed` (`channels`, `mtu`, `ready_mono_ms`, `mode`, `profile`), anahtar okunur, `NinebotSession` kurulur (46-62; 2.4).

Yazma (49-50, 64, 87): çerçeve `mtu−3` bayta sığmalı (yoksa `IllegalStateException("MTU cannot carry complete frame")`); kuyruk en çok **4** yazma (yoksa `"write queue full"`); aynı anda tek yazma (`busy`), `onCharacteristicWrite` gelince sıradaki; `status≠0` → `failed("BLE yazım hatası <status>")`. API ≥33 `writeCharacteristic(c, bytes, type)`.

Alma (91): değer kopyalanır, duvar saati + `elapsedRealtimeNanos`; olay `ble_raw` (sık: Bölüm 7.3); karakteristiğin `NinebotFrames`'i → her tam çerçeve `session.receive(raw, elapsedRealtime)`; ardından `schedulePulse`.

İş parçacığı: bütün geri çağrılar BLE `Handler`'ına (`HandlerThread "scooter-link"`, NB `RecordingService.java:56`) gönderilir ve `g==gatt` değilse (eski bağlantının geri çağrısı) atılır; çalışırken `RuntimeException` → `failed("BLE işlem hatası: <Sınıf>")` (42). Nabız: `session.tick(elapsedRealtime)` `session.nextDelayMs` sonra yeniden (26-27; 2.5).

Bağlantı önceliği (31-33, servis tikinden saniyede bir): değerleri gören biri varsa (`RecordingService.dashboardVisible` ya da yüzen top görünür **ve** ekran açık) `CONNECTION_PRIORITY_HIGH` + `session.panelVisible(true)`, yoksa `BALANCED` + `panelVisible(false)`; değişince olay `ble_priority` (`requested`, `accepted`, `dashboard_visible`, `ball_visible`).

Kapanış (`closeGatt`, 40): son değerler boşalır, panele `null` kare, oturum `close()` (anahtar sıfırlanır), bağlantı `disconnect()` + `close()`, `mtu=23`.

### 2.2 Çerçeve (NB `NinebotFrames.java`)

Düz çerçeve (`request`, 16): `5a a5 LEN 3e BOARD CMD INDEX DATA…` — `LEN` = veri baytı (≤255, yoksa `IllegalArgumentException("payload")`), `3e` = kaynak (uygulama), `BOARD` = hedef kart. Uzunluk `LEN+7`.

Yanıt eşleşmesi (`reply`, 17): `p.length == size+7` ve `p[3]==board` (kaynak), `p[4]==0x3e` (hedef), `p[5]==cmd`, `p[6]==index`.

Şifreli tel çerçevesi = düz + 6 bayt = `LEN+13` (2.3).

Birleştirme (`feed`, 11-14; bildirim karakteristiği başına bir örnek):

- Son parçadan **>3.000 ms** sonra gelen parça: yarım kalan tampon atılır ("stale incomplete frame").
- Parça **>2.048** bayt: atılır, tampon temizlenir ("chunk exceeds reassembly bound").
- Eşzamanlama: `5a a5` **ya da** `5a b5`; öncesindeki baytlar atılır ("bytes before supported sync"). Çerçeve boyu `bayt[2]+13`; eksikse beklenir.
- Kalan tampon **>268** bayt: atılır ("incomplete frame exceeds bound").
- Atılan her parça `Reject` dinleyicisine → olay `ble_reassembly_rejected` (`reason`, `characteristic`, `wire_hex`; NB `BleCapture.java:79`).
- Not: `5a b5` birleştirmeden geçer ama `decrypt` yalnız `5a a5` kabul eder → "wire length/header" ile reddedilir.

### 2.3 Şifreleme (NB `NinebotCrypto.java`; yayımlanmış "Encryption2" algoritmasının bağımsız uygulaması)

| Öğe | Tanım (satır) |
|---|---|
| Sabit `FW` | `97cfb802844143de56002b3b34780a5d` (16 bayt; NR `nb_crypto.py` `FW_DATA`, "libnbcrypto.so @ 0x45A80") (10) |
| Anahtar | `key = SHA-1(pad16(k1) ‖ pad16(k2 ?: 0¹⁶))[0..16)`; `pad16` = `Arrays.copyOf(x,16)` (kısaysa sıfırla doldurur, uzunsa keser) (14) |
| Durum | `auth` (16 bayt, başta sıfır), `staticInput` (başta sıfır; `firmwareInput` ise `FW`), `tx=0`, `rx=-1`, `sn=false` (11-13) |
| `start(challenge)` | 16 bayt değilse hata; `auth=challenge`, `sn=true`, `tx=1`, `rx=-1` (15) |
| Blok `block(c, flag, end)` | `b[0]=flag`, `b[1..4]=c` (32 bit, büyük uçlu), `b[5..12]=auth[0..8)`, `b[13]=b[14]=0`, `b[15]=end` (17) |
| Anahtar akışı | 3. bayttan itibaren 16'lık parçalar, `index=1,2,…`: `ks = AES-ECB(key, c==0 ? staticInput : block(c,1,index))`, XOR; ilk 3 bayt (`5a a5 LEN`) açık (20) |
| MAC (`c≠0`) | `x=AES(block(c,0x59,len−3))`; `x ^= (p[0..3) + 13 sıfır)`; `x=AES(x)`; 3. bayttan 16'lık parçalar: `x ^= parça; x=AES(x)`; etiket `x[0..4)` (18-19) |
| Şifreleme | `c = sn ? ++tx : 0` (`tx≥65535` → `IllegalStateException("counter exhausted; reconnect")`; ilk oturum çerçevesinin sayacı **2**); son 6 bayt: `c==0` → `00 00 crcLo crcHi 00 00` (`crc = ~Σ düz[3..n) & 0xffff`); `c≠0` → `(etiket ^ AES(block(c,1,0))[0..4)) ‖ c>>8 ‖ c&255` (22-24) |
| Çözme | sırayla reddeder: boy `<13` ya da `≠LEN+13` ya da başlık `≠5a a5` → "wire length/header"; `sn` ve `c==0` → "counter downgrade"; `!sn` ve `c≠0` → "unexpected session frame"; `sn` ve `c≤rx` → "replay"; sağlama → "checksum"; MAC (sabit süreli `MessageDigest.isEqual`) → "MAC"; düz boy → "frame". Kabulde `rx=c`, `tx=max(tx,c)` (26-31) |

Sayaç 16 bit tel alanında; oturum başına ~65.533 gönderim sonra yeniden bağlanmak gerekir (2.5 `"Şifreli oturum yenileniyor"`).

**WEEM:** Kotlin'de birebir; AES-ECB/NoPadding ve SHA-1 `javax.crypto`/`java.security` (JVM ve Android aynı). `Arrays.copyOf` dolgusu ve imzalı bayt `&255` maskeleri korunur. Referans eşitliği `ProtocolTest` + `reference_parity.py` (13).

### 2.4 El sıkışma ve oturum durumu (NB `NinebotSession.java`)

Durumlar: `PRE_COMM`, `NEEDS_KEY`, `PAIR`, `AUTH`, `READING`, `FAILED`, `CLOSED` (21). `fail(msg)` = `FAILED` + eşleştirme uyarısı kapanır + durum metni + not `protocol_state` (72); **bağlantıyı yeniden kurmaz**. `port.fault(msg)` = `BleCapture.failed` → GATT kapanır, yeniden bağlanma (5). Hangisinin çağrıldığı aşağıda her satırda yazılı.

| Adım | Gönderilen | Bekleme | Yanıt ve sonuç (satır) |
|---|---|---|---|
| Kurulum | — | — | Okuma listesi = araç registerları + `voltage(7)`, `voltage(0x22)`, `voltage(0x23)` (+ sweep, 10.1); `pairWindow = clamp(window, 0, 300.000)`, `pairUntil = now + pairWindow`; ad boşsa `fail("Bluetooth cihaz adı okunamadı; GPS kaydı sürüyor")`; not `handshake_started` (56) |
| PRE_COMM | `NinebotCrypto(ad UTF-8, FW, firmwareInput=true)`; kart 4, komut `0x5b`, index 0, veri yok (sayaç 0: sabit anahtar akışı `AES(key, FW)` + sağlama, MAC yok) | 5.000 ms; en çok 3 deneme | Yazma karakteristiği yoksa ya da 3 deneme bittiyse `fail("G3 başlangıç yanıtı yok; yalnız Nordic profile 0 denendi. Bağlantı ZIP'ini gönder")` (73-74). Yanıt `reply(p,4,0x5b,idx,LEN)` ve `p.length≥37`: `challenge = p[7..23)` (16), `serial = p[23..37)` (14); seri baytları 0 ya da 32–126 değilse `fail("G3 cihaz kimliği beklenen biçimde değil")`; not `pre_comm_ok` (`stored_key = idx≠0`); `crypto.start(challenge)` (100-102) |
| Dal | — | — | Eşleştirme istendi ve pencere açık → PAIR (103). Değilse anahtar yok **ya da** `idx==0` (scooter'da kayıtlı anahtar yok) **ya da** eşleştirme istendi (pencere kapalı) → `NEEDS_KEY`, durum "G3 yanıt verdi • ölçümler için G3 ile eşleştir düğmesini kullan" (104). Yoksa AUTH (105) |
| PAIR | Yeni 16 bayt anahtar: uygulamada `SessionKey.random()` (SecureRandom), referans kurucuda `SessionKey.derive(challenge, duvar saati)` (2.7); `port.key(k, false)` (aday kaydedilir); `crypto.setKey(ad, challenge)`; kart 4, `0x5c`, index 0, **veri = anahtar** | 2.000 ms aralıkla yeniden gönderilir (120) | Pencere dolunca `fail("Scooter eşleştirme süresi doldu (300 sn pencere); GPS kaydı sürüyor. Kaydı durdurup ZIP'i gönder")` (84). Yanıt (86-95): yönü/boyu yanlış ya da aşama PAIR değil → red; ilk yanıtta not `pair_first_reply` + **eşleştirme uyarısı** (titreşim + bildirim 8, 4.3); veri dolu ve ilk kez → AUTH (`trigger=nonempty_payload`, deneysel); `idx==1` → AUTH (`trigger=index_1`); veri dolu (ikinci kez) → "PAIR veri yanıtının anlamı bilinmiyor • onay bekleniyor (N sn)"; `idx==0` → "Scooter yanıt verdi • eşleştirme onayı bekleniyor (N sn). Güç düğmesine bir kez kısa bas"; başka → red "unknown SET_PWD status". Her yanıtta ayrıntılı not `pair_reply` |
| AUTH | `crypto.setKey(anahtar, challenge)`; kart 4, `0x5d`, index 0, **veri = seri (14 bayt)**; durum "G3 kimlik doğrulanıyor • deneme n/3" | 5.000 ms; en çok 3 deneme (121) | Yanıt `reply(p,4,0x5d,idx,0)` (boş veri), not `auth_reply`. `idx==1` → `port.key(k, true)` (onaylı), `READING`, uyarı kapanır, durum "G3 bağlı • deneysel ölçüm okuma", not `authenticated` (108). `idx≠1` ya da 3 zaman aşımı → `recoverPair` |
| `recoverPair` | Pencere açıksa PAIR'e döner: `crypto.setKey(ad, challenge)`, **aynı aday** yeniden `0x5c` (not `pair_resumed`, durum "AUTH kabul edilmedi • eşleştirme onayı bekleniyor (N sn)"); değilse `fail("G3 doğrulaması başarısız; mevcut anahtar korunuyor. GPS kaydı sürüyor")`. Her durumda not `auth_failed` `existing_key_preserved=true` (85) | | |
| READING | 2.5 | | |

`receive` (97-117): `CLOSED`/`FAILED`/`NEEDS_KEY`'de her çerçeve "inactive stage" ile reddedilir; çözülemeyen çerçeve çözme hatası ile; beklenmeyen kaynak/komut/index/boy "unexpected source/command/index/length for <aşama>". Her red not `protocol_rejected` (`stage`, `reason`, `bytes`, `counter`, `wire_hex`; 80).

`nextDelayMs` (170): `CLOSED`/`FAILED`/`NEEDS_KEY` 1.000; `READING`'de bekleyen istek yoksa `due`, varsa `deadline`; diğerlerinde `deadline`; PAIR'de ve eşleştirmeli AUTH'ta `pairUntil` ile kısalır; sonuç `[1, 1000]` ms.

`close()` (171): `CLOSED`, uyarı kapanır, değerler silinir, anahtar baytları sıfırlanır.

### 2.5 Okuma oturumu: sabitler ve tempo (NB `NinebotSession.java`)

| Sabit | Değer | Anlam (satır) |
|---|---|---|
| Bekleyen istek | **en çok 1** | `pending`; yanıt ya da zaman aşımı olmadan yenisi gönderilmez; boru hattı yok (31-35, 129) |
| Yanıt penceresi | **1.200 ms** | `deadline = now + 1200` (129); yoklama (probe) okumalarında da aynı (42) |
| `VISIBLE_GAP_MS` / `BACKGROUND_GAP_MS` | 5 / 20 ms | yanıttan sonra sıradaki isteğe kadar (36, 110, 168) |
| `VISIBLE_SPEED_MS` / `BACKGROUND_SPEED_MS` | **150** / 400 ms | hız registerının en kısa aralığı (36, 139); açıklama satırı "180 ms" der, kod 150 (14, R-S1) |
| Hız araya girişi | arada ≥2 başka okuma | `sinceSpeed ≥ 2` ve hız en az bir kez yanıtlanmış (139) |
| `RARE_EVERY` | 3 | seyrek sayaçlar her 3. turda (38, 144) |
| `MAX_UNANSWERED` | 3 | hiç yanıtlamamış register 3 zaman aşımında bu bağlantı için düşer (`read_disabled`) (40, 125) |
| Kayıp bağlantı | 12 | sweep/yoklama/sessiz olmayan **ardışık** 12 zaman aşımı → `FAILED` + değerler silinir + `port.fault("Ölçüm yanıtı kesildi; yeniden bağlanıyor")` (128); her kabul edilen değer sayacı sıfırlar (112, 152) |
| Sayaç bitti | — | `encrypt` `IllegalStateException` → `FAILED` + `port.fault("Şifreli oturum yenileniyor")` (129) |
| `PROBE_TRIES` | 2 | harici batarya yoklamasında kart başına zaman aşımı (42; Bölüm 9) |
| `QUIET_RETRY_MS` | 15.000 ms | sessiz harici batarya: yalnız voltajı, 15 sn'de bir, turun dışında (50, 136) |
| `PAIR_WINDOW_MS` | 300.000 ms | eşleştirme penceresi (22) |
| PRE_COMM / AUTH | 5.000 ms × 3 | (74, 81) |
| PAIR yeniden gönderim | 2.000 ms | (103, 120) |

İstek: kart = register'ın kartı, komut `0x01`, index = register, veri = **1 bayt: okunacak boy** (2 ya da 4) (129). Yanıt: `reply(p, kart, 0x04, index, boy)` — yanıt komutu **`0x04`** (109).

Tur (`next`, 134-150): liste değişince (`scheduleDirty`) şeritler yeniden kurulur: `fast` (bütün `_voltage_v`, `_current_a_charge_positive`, `scooter_speed_kmh`), `slow` (diğer panel registerları), `rare` (`odometer_km`, `lifetime_*`, `_reg8a_raw`), `sweep`, `probe`; sessiz kartın sweep olmayan registerları şeritlere girmez.

1. Sessiz kart zamanı geldiyse onun voltajı (`0x8c`, yoksa ilk register'ı) — tur imleçleri ilerlemez (136, 163).
2. Hız araya girme kuralı (139).
3. Tur: önce bütün `fast` sırayla; sonra **bir** `slow` (dönerek); sonra (her `RARE_EVERY`. turda ya da `slow` boşsa) **bir** `rare`; sonra **bir** sweep; sonra **bir** yoklama; tur biter (`passCount++`). En çok 6 deneme döngüsü, bulunamazsa `null` (141-149).

Okuma kabulü (110-113): `pending` boşalır, `due = now + gap`; yoklama registerıysa önce `probeVoltage` (Bölüm 9); `metrics.accept` değer döndürürse: `misses=0`, register "yanıtladı" kümesine, kartın kayıp serisi silinir; kart sessizdeyse geri döner (not `battery_back`, tur baştan); `port.reading(v, data)`; `0x16` dışı bir kart ilk kez yanıtladıysa o kartın batarya registerları (akım, yüzde, `0x8a`) voltajın hemen arkasına eklenir; `0x8f` yanıtı yoklama adayını onaylar. Değer sınır dışıysa not `measurement_rejected` + red; `0x8f` adayıysa aday düşer (`battery_probe_partial`).

Zaman aşımı (122-128): `pending` boşalır, `due=now`; yoklama → `probeTimeout`; sessiz kart → sık not `battery_quiet_timeout`; değilse not `read_timeout` (`board`, `index`, `sweep`), hiç yanıtlamamışsa 3.'de düşer (imleç bir geri alınır), yanıtlamış harici kart (`7` ve `0x16` dışı) 3 ardışıkta **sessiz** olur (`battery_quiet`, D12), sonra `misses` kuralı.

**WEEM:** sabitler ve tur sırası aynen (`SpeedCadenceTest`, `PowerCadenceTest`, `BatteryProbeTest` ölçer). Tek bekleyen istek ve 1,2 sn pencere değişmez (kullanıcı kuralı; scooter'ın ~70 ms yanıtı tavan, taşma yok).

### 2.6 Salt okuma koruması

`NinebotSession.send` (75-78): komut `0x01` değilse ve (kart 4 + `0x5b/0x5c/0x5d`) değilse `IllegalArgumentException("read/handshake commands only")`. PAIR (`0x5c`) scooter'daki eşleşme anahtarını değiştirir (kullanıcının açıkça başlattığı eşleştirme); okuma hiçbir ayarı değiştirmez. `BleCapture` yalnız yazma karakteristiğine ve CCCD'ye yazar.

**WEEM:** aynı koruma + kaynak taraması testi: `:scooter` kodunda `writeCharacteristic` yalnız tek yerde (çerçeve gönderici), gönderilen komut kümesi `{0x01, 0x5b, 0x5c, 0x5d}` (A5.2 test eki; regresyon güvencesi).

### 2.7 Oturum anahtarı üretimi (NB `SessionKey.java`)

- `random()` (15): `SecureRandom` 16 bayt. **Uygulamanın kullandığı** (NB `BleCapture.java:58` `SessionKey::random`; not `pair_started` `generator=secure_random`).
- `derive(auth, wallMs)` (16-23): `seed = wallMs + Σ (int)auth[i] << ((i%8)*8)` — Java `int` kaydırması: kaydırma `& 31` (32→0, 40→8, 48→16, 56→24), bayt işaretli; toplam `long`'a. `java.util.Random(seed).nextBytes(16)` → `SHA-256(…)[0..16)`; ara dizi sıfırlanır. Yalnız referans/eşitlik kurucusunda (`keyFactory == null`; `generator=reference_java_lcg_sha256`).
- **WEEM:** `derive` Kotlin'de `java.util.Random` ile (kotlin.random değil) ve `Int.shl` (aynı 5 bit maske) ile birebir; yalnız testlerde. Uygulama `SecureRandom`.
- Var olan anahtar hiç döndürülmez (yalnız açık eşleştirme yeni anahtar üretir; `SessionKey.java:14`).

## 3. Okunan değerler ve ölçekler (NB `NinebotMetrics.java`)

### 3.1 Register tablosu

Çözme (`decode`, 76): veri boyu register boyuna eşit ve 2 ya da 4 olmalı; **küçük uçlu** işaretsiz tamsayı; `signed` ve boy 2 ise `short`'a çevrilir (4 baytlık işaretli yok); `değer = n × ölçek`; `[min, max]` dışında `null` (red, `measurement_rejected`). Son değer anahtar başına tutulur (`LinkedHashMap`, 77-80); bağlantı düşünce silinir.

| Kart | Reg | Boy | Anahtar | Birim | İşaret | Ölçek | Aralık | Şerit | Satır |
|---|---|---|---|---|---|---|---|---|---|
| `0x16` (VCU) | `0x55` | 2 | `battery_percent` | % | – | 1 | 0–100 | slow | 13 |
| `0x16` | `0x5f` | 2 | `remaining_range_km` | km | – | 0,01 | 0–200 | slow | 14 |
| `0x16` | `0x62` | 4 | `odometer_km` | km | – | 0,1 | 0–1.000.000 | rare | 15 |
| `0x16` | `0x64` | 4 | `lifetime_awake_seconds` | s | – | 1 | 0–4.294.967.295 | rare | 16 |
| `0x16` | `0x66` | 4 | `lifetime_riding_seconds` | s | – | 1 | 0–4.294.967.295 | rare | 17 |
| `0x16` | `0x6b` | 2 | `vehicle_temperature_c` | °C | – | 0,1 | 0–120 | slow | 18 |
| `0x16` | `0x57` | 2 | `scooter_speed_kmh` (aday) | km/h | – | 0,1 | 0–120 | fast (+ araya girme) | 25 |
| `0x16` | `0x68` | 2 | `dashboard_trip_km` (aday) | km | – | **0,1** (topluluk haritası 0,01; saha 7 Ekim düzeltti) | 0–10.000 | slow | 26 |
| `0x16` | `0x6a` | 2 | `dashboard_ride_seconds` (aday) | s | – | **1** (topluluk "dakika"; saha düzeltti) | 0–1.000.000 | slow | 27 |
| her BMS `b` | `0x8c` | 2 | `bms_<b>_voltage_v` | V | – | 0,01 | 20–80 | fast | 40 |
| BMS `b` (ilk yanıttan sonra eklenir) | `0x8d` | 2 | `bms_<b>_current_a_charge_positive` | A | **işaretli** (+ şarj, − deşarj) | 0,01 | −200–200 | fast | 41 |
| BMS `b` | `0x8f` | 2 | `bms_<b>_percent` | % | – | 1 | 0–100 | slow | 41 |
| BMS `b` | `0x8a` | 2 | `bms_<b>_reg8a_raw` (anlamı bilinmiyor: iç %81'de sabit 1217, harici %58'de 1000) | raw | – | 1 | 0–65.535 | rare | 42-45 |
| sweep kartı | `from..to` | 2 | `sweep_<b>_<i>` | raw | işaretli | 1 | −32.768–32.767 | sweep | 51 |

`<b>` iki haneli küçük onaltılık (`String.format(Locale.ROOT,"bms_%02x_…")`, 39-41). Kartlar: `PRIMARY_BMS = 7` (iç batarya, 30); bağlantıda her zaman `voltage(7)`, `voltage(0x22)`, `voltage(0x23)` okunur (`NinebotSession.java:56`; G3'te `0x22/0x23` hiç yanıtlamaz → 3 zaman aşımında düşer); harici batarya kartı yoklamayla bulunur (sahada `0x06`; Bölüm 9). Batarya registerları bir kart **ilk kez** yanıtlayınca eklenir (`battery(b)` listesinin voltaj dışındaki üçü, `NinebotSession.java:112`).

Bütün araç registerları ve ölçekler "topluluk G3 haritası" (Ninebot-BLE-MQTT-Bridge) + saha düzeltmeleridir; `device_verified=false`, `mapping=community_g3` her ölçüm olayında yazılır (`BleCapture.java:57`). Resmî uygulamanın W değeri V×A değildir, kaynağı bilinmiyor (19-24).

### 3.2 Tazelik ve türetilen değerler

| Kural | Değer (satır) |
|---|---|
| `FAST_FRESH_MS` | 3.000 ms — fast şeridi (voltaj, akım, hız) (57-58) |
| `SLOW_FRESH_MS` | 15.000 ms — diğerleri (57-58) |
| `freshValue(values, key, now, freshMs)` | anahtarın `0 ≤ yaş ≤ freshMs` en yeni değeri, yoksa `null` (75) |
| `PACK_CONFIRM_MS` | 60.000 ms — harici paket güç toplamına yalnız yüzdesi bu kadar taze iken girer (38, 66) |
| `packPowerW(b)` | taze `voltaj × akım` (W, + şarj / − deşarj), biri yoksa `null` (64) |
| `powerW` | iç paket (`7`) yoksa `null`; varsa iç + onaylı harici paketlerin toplamı (62, 66) |
| `packBoards` | sweep olmayan `bms_` değerlerinin kartları, sıralı (67) |
| `exportable(key)` | araç anahtarları ya da `bms_[0-9a-f]{2}_(voltage_v\|current_a_charge_positive\|percent\|reg8a_raw)`; sweep asla (70) |
| `parseBoards(text)` / `addBoard` | virgüllü onaltılık; `0<b<256`, `7`, `0x16`, `0x04` hariç, yinelenen atılır; `addBoard` sıralı `%02x` yazar (72-73) |

`BleCapture.telemetry()` (65-72; kayıt `sample` satırı): her değer için `{value (taze değilse null), last_value, age_ms, fresh, unit, board, register, decoded_mono_ms}` + `mapping="community_g3_candidate"`, `device_verified=false`, `scooter_speed_kmh`, `dashboard_power_w`, `dashboard_power_source` (`voltage_times_current` ya da harici varsa `voltage_times_current_all_packs`), `internal_power_w`, `external_power_w`, `dashboard_trip_km`, `dashboard_ride_minutes` (= sn/60), `dashboard_ride_seconds`.

`BleCapture.measurements()` (75): ayar ekranındaki durum metni (etiketler "Pil", "Menzil", "Toplam km", "Araç sıcaklığı", "Hız (aday)", "Sürüş km (aday)", "Sürüş sn (aday)", "Pil [kk] • Voltaj/Akım (+ şarj)/Reg 0x8a (kapasite?)/Yüzde"; `lifetime` gizli; güç "Güç: N W (V×A • doğrulanmamış)").

**WEEM:** tablo, ölçek, aralık, işaret ve tazelik birebir (`logic/scooter`); anahtar adları kayıtta korunur (11). "Aday" ve "doğrulanmamış" uyarıları arayüzde kalır (A5.4).

## 4. Eşleşme ve anahtar deposu (`pairing-v1`, Keystore)

### 4.1 Depo (NB `KeyVault.java`)

| Öğe | Değer (satır) |
|---|---|
| SharedPreferences | **`pairing-v1`**, `MODE_PRIVATE` (14) |
| Keystore | `AndroidKeyStore`, takma ad **`NinebotLogger.Pairing.v1`**; yoksa üretilir: AES, `PURPOSE_ENCRYPT\|DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, anahtar boyu verilmez (sağlayıcı varsayılanı), kullanıcı doğrulaması yok (13, 15) |
| Şifreleme | `AES/GCM/NoPadding`, IV sağlayıcıdan (12 bayt), etiket 128 bit, **AAD = cihaz adresi UTF-8**; değer = `Base64.NO_WRAP(IV ‖ şifreli+etiket)` (16 bayt anahtar → 44 bayt → 60 karakter; A5.2 düzeltmesi, `PairingVaultTest`) (16, 22) |
| Anahtarlar (adres başına; adres = BLE MAC, ör. `AA:BB:…`) | `<adres>` (etkin), `<adres>.confirmed` (bool), `<adres>.pending` (aday), `<adres>.previous`, `<adres>.previous.confirmed` (16-26) |
| `get` | etkin değer yoksa `null`; boy <28 ya da çözülen ≠16 bayt ya da her hata → `KeyUnavailableException("Yerel eşleştirme anahtarı açılamadı")` (16-17) |
| `has` / `confirmed` / `hasPending` | yalnız tercih okur, şifre çözmez (19-21) |
| `save(adres, k, confirmed=false)` | yalnız `.pending` yazılır; etkin anahtar değişmez (23) |
| `save(adres, k, confirmed=true)` | etkin anahtar farklıysa (ya da okunamıyorsa) eskisi `.previous` + `.previous.confirmed`'a; yeni etkin + `.confirmed=true`; `.pending` silinir. Aynı anahtarla yeniden kayıt `.previous`'u silmez (24) |
| `restore` | `.previous` yoksa false; varsa etkin ← önceki, onay işareti önceki değerden (uydurulmaz), `.previous*` ve `.pending` silinir (26) |
| Yazma | `commit()` (eşzamanlı); başarısızsa `IllegalStateException("Eşleştirme anahtarı saklanamadı")` (25) |

Akış (NB `BleCapture.java:46-58`):

- Bağlantı başında `vault.get(adres)`; açılamazsa: eşleştirme isteniyorsa anahtar `null` + olay `ble_key_error {reason: local_key_unavailable, explicit_repair: true}`; istenmiyorsa `close()` (yeniden bağlanma **yok**), durum + uyarı "Yerel anahtar açılamadı; Bluetooth durduruldu, GPS sürüyor. Kaydı durdurup G3 ile eşleştir veya Gelişmiş bağlantı anahtarı ile kurtar." + olay `ble_key_error {retry: false}`.
- Olay `local_key_state {present, confirmed, pending_candidate}`.
- Eşleştirme penceresi bağlantılar arası tek: ilk kurulumda `pairEnd = now + 300 sn`; yeniden bağlanmada kalan süre geçer; süre dolmuşsa oturum kurulmaz, durum "Eşleştirme süresi doldu; yeniden denemek için kaydı durdur" (46-47).
- `Port.key(k, confirmed)` → `vault.save`; onaylı kayıttan sonra `allowPair=false` (sonraki yeniden bağlanmalar normal AUTH); dizi her durumda sıfırlanır (54).

Elle anahtar (NB `MainActivity.java:122-123`, yalnız bağlantı kapalıyken): "Kendi scooter'ının bağlantı anahtarı" — 32 onaltılık karakter (boşluklar silinir), 16 bayt değilse "Anahtar kaydedilemedi. 32 hex karakter gerekir."; önce aday sonra onaylı kaydedilir (`.previous` korunur); "Önceki anahtarı geri al" → `restore`. Durum metni (`keyState`): "Bu scooter için anahtar yok." / "Onaylanmamış aday anahtar var; etkin anahtar yok." / "Bu scooter için anahtar kayıtlı. Yerel onay işareti var; bağlantıda AUTH gerekir." / "…Önceki sürümden onaysız anahtar; bağlantıda AUTH gerekir." / "Saklanan anahtar açılamadı."

Eşleştirme başlatma (NB `DashboardActivity.java:172`): bağlantı, dışa aktarma ve bakım yokken, izinler tamam ve scooter seçiliyse onay penceresi "G3 ile eşleştir" ("Segway Mobility bağlantısını kapat. İstenirse scooter'ın güç düğmesine kısa bas. Bluetooth anahtarı değişebilir; Segway ile yeniden eşleşmen gerekebilir. Önceki logger anahtarı ayarlardan geri alınabilir. GPS kaydı da başlar.") → "Eşleştir ve kaydet" = servis `pair=true` (eşleştirme her zaman kayıtla açılır; `RecordingService.java:38-40`).

### 4.2 Eşleştirme uyarısı (NB `PairAttention.java`)

- Metin `MESSAGE = "ŞİMDİ güç düğmesine bir kez kısa bas"` (10); bildirim no **8**, kanal `pairing-attention` "Eşleştirme onayı", `IMPORTANCE_HIGH`, ses yok, kanal titreşimi kapalı (11, 22); başlık "Scooter yanıt verdi", dokununca ayarlar (`MainActivity`).
- Servis başına **bir kez** (yeniden bağlanmalar dahil): titreşim `[0, 300, 120, 300]` ms (titreşim motoru varsa), olay `pair_attention {message, first_reply, vibrator_available, vibration_requested, vibration_error}` (17-26). Kapanınca bildirim silinir. Oturum `pairPrompt(true)` ilk PAIR yanıtında, `false` READING'e geçince, başarısızlıkta ve kapanışta.
- Ayarlar ekranı (`refreshPairPrompt`, NB `MainActivity.java:18, 58`) ve canlı panel (`DashboardActivity.java:103`) uyarı satırını `PairAttention.visible()` iken gösterir (saniyede bir denetlenir).

### 4.3 Kimlik ve göç

- Keystore anahtarı uygulamanın UID'sine bağlı: WEEM aynı `applicationId` (`com.kuryelog.app`) ve imzayla Rawstats'ın üstüne kurulunca `pairing-v1` + `NinebotLogger.Pairing.v1` aynen okunur (plan Bölüm 2). Sürüş Paneli'nin bağımsız APK'sı (`az.ninebot.logger`, 0.3.0'da bitti; NB `build.gradle:2`) ayrı UID'di; onun anahtarları zaten taşınmadı.
- Göç sınaması: NT `upgrade_smoke.py` + `KeyVaultInstrument` `upgrade_seed` (eski APK'da sentetik anahtar `00:11:22:33:44:55` / `00112233…eeff` yazılır, yeni APK okur).
- `crypto_mode`: tek mod `gen2_fw` (`NinebotMode.java:3-8`); eski `gen3_zero` değeri `GEN2_FW`'ye çevrilir, başka değer `IllegalArgumentException("unknown crypto mode")`; ayarlar açılınca `gen2_fw` yazılır (NB `MainActivity.java:20`).

**WEEM:** `pairing-v1` dosyası, beş anahtar biçimi, AAD, IV düzeni ve Keystore takma adı **değişmez** (taşınmazsa scooter yeniden eşleşmeli; `VERI_SOZLESMESI` 5.3). `KeyVault` Android'de kalır (Keystore); kural kısmı (aday/etkin/önceki geçişi) saf `PairingStore` arayüzüyle test edilir, Android uygulaması `KeyVaultInstrument`'in 9 denetimini cihaz testine çevirir (A5.2). Eşleştirme penceresi 300 sn ve "bir kez titreşim" aynen; bildirim numarası `NotificationIds` tablosuna girer (11.4).

## 5. S6 — Yeniden bağlanma

### 5.1 Bağlantı katmanı (NB `BleCapture.java`, `Timing.java`)

`failed(reason)` (39): bekçi ve bekleyen yeniden bağlanma iptal, `closeGatt()`, durum metni = neden, olay `ble_state {state: neden, attempt}`, `Timing.retryMs(attempt++)` sonra `connect()`. Her kabul edilen ölçümde `attempt = 0` (57): okuyan bağlantı 1 sn'den başlar.

`Timing.retryMs(a)` (`Timing.java:10-11`): `a = max(0, a)`; `a ≥ FAST_RETRIES (24)` → **60.000 ms**; değilse `min(15.000, 1.000 << min(4, a))` → 1, 2, 4, 8 sn, sonra 15 sn (a=4…23). İlk 24 deneme ~11 dk (15 sn bağlanma bekçisiyle; restoran/kapı durağı), sonra dakikada bir.

| Yeniden bağlanır (`failed`) | Bağlantıda kalır, okumaz (`fail`, `FAILED`/`NEEDS_KEY`) | Durur (`close`) |
|---|---|---|
| Bluetooth kapalı; `connectGatt` null ya da hata; 15 sn GATT bekçisi; kopma (`status≠0`/`DISCONNECTED`); servis keşfi başlatılamadı/hatası; abonelik başlatılamadı/hatası/hiç kanal; yazma reddi ya da `status≠0`; BLE geri çağrısında `RuntimeException`; protokol başlangıcı/nabız hatası; oturum `fault` (12 ardışık zaman aşımı "Ölçüm yanıtı kesildi; yeniden bağlanıyor", sayaç bitti "Şifreli oturum yenileniyor") | Ad boş; PRE_COMM 3 deneme yanıtsız; seri biçimi bozuk; anahtar yok / scooter `idx==0` (`NEEDS_KEY`); eşleştirme penceresi doldu; AUTH 3 kez başarısız (eşleştirmesiz); bildirim karakteristiği yok ("GATT kaydedildi; desteklenen kanal yok"); pencere dolmuş eşleştirme bağlantısında oturum kurulmaz | Yerel anahtar açılamadı ve eşleştirme istenmedi; kullanıcı/servis durdurdu (`close`: `closed=true`, bekleyen yeniden bağlanma iptal, 41) |

"Bağlantıda kalır" satırı: GATT açık, nabız durmuş (`schedulePulse` yalnız `CLOSED/FAILED/NEEDS_KEY` dışında kurar, 27), bekçi son yazma geri çağrısında kaldırılmış; scooter kapanınca kopma gelir ve normal yeniden bağlanma başlar. Kullanıcı ancak kaydı durdurup yeniden başlatarak tekrar dener (14, R-S3).

Servis (`RecordingService`): `START_NOT_STICKY` (57-69); süreç ölürse Sürüş Paneli kendini yeniden başlatmaz. Rawstats'ta vardiya servisi yapışkan yeniden başladığında `ScooterLink.onShiftStart` canlı bağlantıyı yeniden açar (RS `GpsService.kt:98`, `ScooterLink.kt:92-97`).

### 5.2 M23 — "Scooter verileri alınıyor / alınamıyor" (RS `ScooterReconnect.kt`, saf)

Sürüş Paneli bağlanma anını dışarı vermez; durum karelerden okunur. `tick(now, linkActive, lastFrameAt, speedKmh)` (20-34), sabitler `LOST_MS = 30.000`, `NO_DATA_MS = 30.000`, `RIDE_KMH = 10.0` (37-39):

1. `linkActive == false` → her şey sıfırlanır, `null`.
2. `fresh` = `lastFrameAt` var ve öncekinden büyük; öyleyse `lastFrame = lastFrameAt`.
3. Kayıp durumunda: taze kare → kayıp biter, `RECEIVING`; uyarı verildiyse `null`; GPS hızı ≥10 km/s ise sürüş başlangıcı tutulur (altına düşünce silinir); sürüş ≥30 sn → **bir kez** `NOT_RECEIVING`.
4. Kayıp değilken: taze kare → "görüldü"; görülmüş ve son kareden ≥30 sn → kayıp. Bağlantının ilk kareleri yeniden bağlanma sayılmaz.

Çağrı (RS `ScooterLink.kt:38-53`, `GpsService.kt:248`): vardiyada her GPS fix'inde (hız m/s × 3,6), yalnız vardiya açık ve scooter seçiliyken; saat `elapsedRealtime`, kare zamanı `SurusPaneli.live()?.monoMs` (bağlantı düşünce kare `null`). Bildirim: `events.jsonl` satırı `{type: "scooter_link", action: "scooter_data_back" | "scooter_data_missing", reason: null}`; bildirim **4902**, kanal `scooter_link` "Scooter bağlantısı" (AZ "Skuter bağlantısı"), `IMPORTANCE_DEFAULT`, `setAutoCancel(true)`, `setOnlyAlertOnce(false)`; `RECEIVING`: "Scooter verileri alınıyor" / "Bağlantı yeniden kuruldu, değerler geliyor." (AZ "Skuter məlumatları alınır" / "Bağlantı bərpa olundu, dəyərlər gəlir."), 15 sn sonra kalkar; `NOT_RECEIVING`: "Scooter verileri alınamıyor" / "Sürüyorsun ama scooter'dan değer gelmiyor; Sürüş Paneli'ne bak." (AZ "Skuter məlumatları alınmır" / "Sürürsən, amma skuterdən dəyər gəlmir; Sürüş Panelinə bax."), zaman aşımı yok.

### 5.3 Vardiya bağlantısı (RS `ScooterLink.kt:86-137`)

- Tercih `scooter.autoConnect` (`kuryelog`, varsayılan **true**; ayar "Vardiyada scooter'a otomatik bağlan", RS `MainActivity.kt:1165-1166`).
- Vardiya başı (`onShiftStart`, 93-97; vardiya servisinin yapışkan yeniden başlaması dahil): otomatik kapalıysa `scooter_link {action: skip, reason: auto_off}`; değilse `open`: sırayla `already_active`, `no_scooter`, `not_paired` (`KeyVault.has`), `no_bluetooth_permission`, denetim hatası `check_failed` → `skip`. Sonra canlı bağlantı (`startLive`, kayıt yok): başarılıysa `scooter.shiftLink = true`, `{action: start}`, 5102 silinir; reddedilirse (ör. `ForegroundServiceStartNotAllowedException`) `{action: failed, reason: <Sınıf> | refused}` + bildirim **5102** "Scooter bağlanmadı" / "Bağlanmak için dokun." (AZ "Skuter qoşulmadı" / "Qoşulmaq üçün toxun."; RS `AutoStartNotice.kt:19, 34-38`; kanal ve zaman aşımı `CORE.md` 3.3).
- Vardiya sonu (`onShiftEnd`, 126-137): yalnız `scooter.shiftLink` true ise (silinir): bağlantı yoksa bir şey yapılmaz; kullanıcı kayda çevirdiyse `{action: kept_recording}`; değilse durdurulur `{action: stop}`; hata `{action: stop_failed, reason}`. Kullanıcının kendi açtığı bağlantıya dokunulmaz.
- Vardiya içinde sonradan (`canConnectNow`/`connectNow`, 100-103): ana kartta "Scooter'a şimdi bağlan" (vardiya açık, bağlantı yok, seçili + eşleşmiş + izinli) ve "Scooter için Bluetooth izni ver" (API ≥31; `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`); izin verilince otomatikse bağlanır (RS `MainActivity.kt:567-571, 1410`).
- Ana kart satırı (`status`, 203-219): "Scooter seçilmedi · Sürüş Paneli'nde ara ve seç" / "Eşleştirilmedi · Sürüş Paneli'nde G3 ile eşleştir" / "Bluetooth izni gerekli" / "Bağlı değil · vardiyada otomatik bağlantı kapalı" / "Bağlı değil" / "Vardiya başlayınca bağlanır" / "Bağlanıyor · scooter verisi bekleniyor" / canlı: "%<pil> · <hız> km/h · <W> W[ · harici %<p>][ · bu sipariş <Wh> Wh]" (TR/AZ).
- Sürüş Paneli'nin kendi "Açılışta canlı paneli bağla" anahtarı (`auto_live`, varsayılan false; NB `DashboardActivity.java:178-181`): panel açılınca, bağlantı yok + scooter seçili + izin + Bluetooth açıksa canlı bağlantı.

**WEEM:** 5.1 sabitleri, sınıflandırma ve M23 aynen (`TimingTest`, `ScooterReconnectM23Test`). S11: 2 telefon modunda **yalnız Receiver** bağlanır; Provider rolünde `:scooter` BLE'yi hiç açmaz (vardiya başı `open` "role_provider" nedeniyle atlanır, yeni neden adı: 11.3). Tek telefon modunda bugünkü gibi. Bildirim numaraları 4902, 5102 `NotificationIds`'te (`CORE.md` 3.3).

## 6. S2 — Canlı panel ve S7 — yüzen panel

### 6.1 Panel karesi (NB `NinebotDashboard.java`, saf)

`snapshot(values, now)` (19-27) her kabul edilen ölçümde (`BleCapture.java:57`) üretilir, servis yayımlar (`RecordingService.publishFrame`, 48; dinleyiciler ana iş parçacığında, yalnız son kare):

| Alan | Kaynak | Tazelik |
|---|---|---|
| `speedKmh` | `scooter_speed_kmh` | 3 sn |
| `maxSpeedKmh` | oturumun en yüksek taze hızı (`reset()` ile −1; resmî uygulamanınkiyle aynı olmayabilir) | — |
| `powerW` | `NinebotMetrics.powerW` (iç + onaylı harici, + şarj / − harcama) | 3 sn |
| `voltageV`, `currentA` | iç paket `bms_07_voltage_v`, `bms_07_current_a_charge_positive` (harici paket kutucuklara girmez) | 3 sn |
| `batteryPct` | `battery_percent`, yoksa `bms_07_percent` | 15 sn |
| `rangeKm`, `tripKm`, `rideSec`, `tempC`, `odometerKm` | `remaining_range_km`, `dashboard_trip_km`, `dashboard_ride_seconds`, `vehicle_temperature_c`, `odometer_km` | 15 sn |
| `extBatteryPct` | `7` dışındaki ilk kartın taze yüzdesi (`packBoards` sırasıyla) | 15 sn |
| `monoMs` | kare anı (`elapsedRealtime`) | — |

`sameValues` (14) zaman dışında bütün alanları karşılaştırır (gereksiz çizim yok). Biçimler (29-40): `DASH = "–"` (değer yoksa, asla 0 uydurulmaz); `speed`/`oneDecimal` `%.1f`; `watts` yuvarlanmış tamsayı (−0 → "0"); `whole` yuvarlanmış; `clock` `m:ss`, ≥1 sa `h:mm:ss`; `ride` `["N","sn"]` / `["M","dk","S","sn"]` / `["H","sa","M","dk"]`; `gauge` = hız/30 (0–1).

### 6.2 Canlı panel ekranı (NB `DashboardView.java`, `DashboardActivity.java`)

- Gösterge: büyük hız, halka (`gauge`), altında enerji/güç/batarya/menzil/voltaj/akım/sıcaklık kutucukları ve "BU SÜRÜŞ" ya da (Rawstats siparişi sayacı sıfırladıysa) "BU SİPARİŞ" satırı (Wh, ort. hız, pil kullanımı; `DashboardView.java:33-34, 58`). Kare **5 sn**'den eskiyse "VERİ BEKLENİYOR" (`STALE_MS`, 11, 37, 70); değerler hiçbir zaman ara değerlenmez ya da GPS ile doldurulmaz.
- Dokunma: tam ekran (araç çubuğu kayar; `immersive` örnek durumunda saklanır, tercih değil); geri tuşu tam ekrandan çıkar (`DashboardActivity.java:60-99`). Ekran açık tut: bağlantı varken ve `keep_screen_on` (varsayılan true) (109).
- Kalp atışı saniyede bir (bayatlık, araç çubuğu, 34). Görünürken `RecordingService.dashboardVisible = true` (BLE yüksek öncelik ve hızlı tempo; 2.1, 2.5) (205-206).
- Resim içinde resim (PiP): yalnız yüzen panel açık ama "üstte göster" izni **yokken** ve cihaz destekliyorsa (`pipEnabled`, 213); oran 16:10; API ≥31 otomatik giriş, eskide `onUserLeaveHint` (107, 211-216).
- Başlat (168-171): bağlantı varsa ve kayıt yoksa "Kayda geç" ("Bağlantı kopmadan GPS ve sürüş kaydı başlar."); scooter seçili değilse yalnız GPS kaydı; değilse "Nasıl başlasın?" → "Canlı panel • kayıt yok" / "Panel + kayıt • GPS dahil".
- Tarama (128-164): `startScan(filtre yok, SCAN_MODE_LOW_LATENCY)`, **4 sn** (`SCAN_MS`), sonuçlar canlı (yeni cihaz, ad gelince ya da RSSI ≥6 dB değişince liste yenilenir); sıra: kayıtlı cihaz, adlı, adsız; etiket "<ad | İsimsiz BLE cihazı> • <adres> • <rssi> dBm"; boşsa "Cihaz bulunamadı. Scooter'ı aç, Segway bağlantısını kapat ve tekrar dene."; seçim `device`, `device_name` (etiket), `ble_name` (yayın adı) yazar.
- Bildirim (servis, NB `RecordingService.java:126-127, 168-186`): no **7**, kanal `recording` "Scooter kaydı" `IMPORTANCE_LOW`, sürekli, kategori `NAVIGATION`; başlık "Sürüş Paneli • kayıt sürüyor" / "… • canlı • Rawstats vardiyası kaydediyor" / "… • canlı panel (kayıt yok)"; metin: uyarı varsa uyarı, değer geliyorsa **2 sn**'de bir "<hız> km/h • <W> W[ • <Wh> Wh][ • ort. <v> km/h][ • %<pil>]", yoksa 10 sn'de bir bağlantı durumu; eylemler "Kayda geç" (canlıyken) ve "Durdur"; aynı metin yeniden gönderilmez.

### 6.3 S7 — Yüzen panel (NB `FloatingPanel.java`, `FloatGeometry.java`, `PanelField.java`, `FloatPainter.java`)

- Ne zaman (24-36): bağlantı açıkken uygulamanın son ekranı da durduktan **400 ms** sonra (ekranlar arası geçişte değil), `floating` (varsayılan **true**) ve `Settings.canDrawOverlays`; Rawstats'ın ekranları da sayaca katılır (`SurusPaneli.screenStarted/Stopped`). Bağlantı bitince kalkar (tik saniyede bir, 51; servis kapanışı `FloatingPanel.hide()`).
- Pencere: `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS | FLAG_WATCH_OUTSIDE_TOUCH` (53); konum `float_x`/`float_y` (varsayılan sağ kenar, ekranın 1/3'ü), bırakınca en yakın kenara kayar ve kaydedilir.
- Hareketler (sınıf açıklaması 10-17): hemen sürükle; dokun → iki ok (sağ: yatay bar, alt: dikey sütun); açık bara dokun ya da ters yöne kaydır → kapanır; çift dokunma (`ViewConfiguration.getDoubleTapTimeout`) → Sürüş Paneli ayarları; uzun basma → canlı panel; "×" hedefine bırakma (alttan 170 dp, 64 dp, yakınlık `1,1 × boy`) → `floating=false` (yalnız ayardan geri açılır).
- Çizim: kare her ölçümde gelir; yalnız gösterilen değer değiştiyse ve en sık **80 ms**'de bir (`MIN_FRAME_MS`, 46, 57).
- Geometri (`FloatGeometry.java:10-39`, saf): top `BALL_DP=100` (64–160), bar kalınlığı topun `THICK_PCT=125`%'i (80–200), yatay uzunluk 4 değerde uzun kenarın `H_PCT=80`%'i (30–100), dikey `V_PCT=60`%'i (25–95), yazı `TEXT_PCT=100`% (60–180; dikey/yatay ekran ayrı); `length = round(max(top, min(uzunKenar × pay × n/4, ekran − 2×kenar)))`, `n = clamp(alan sayısı, 1, 4)`; `cross`, `start` barı ekran içinde tutar; `direction(dx, dy, slop)`: iki eksende de `<slop` ise dokunma, yoksa büyük eksen.
- Alanlar (`PanelField.java:6-42`): 16 değer — `speed`, `power`, `battery`, `range`, `voltage`, `current`, `temp`, `ext_battery`, `max_speed`, `odometer`, `scooter_trip`, `ride_time`, `trip_wh`, `trip_pct`, `trip_km`, `avg_speed`; en çok **4** (`MAX`); varsayılan `power, trip_wh, battery, avg_speed` (hız scooter'ın kendi ekranında); `float_fields` virgüllü anahtarlar (bilinmeyen/yinelenen atılır, boşsa varsayılan); `float_ball_field` topta gösterilen (yoksa ilk alan); `value` boş değerde "–", `trip_km` hareket yoksa "–"; `caption` "Wh • sürüş", "% • sürüş", "km • sürüş", "ort. km/h", "% • harici", "maks. km/h", "toplam km", "süre".
- `SurusPaneli.floatingBounds()` → Rawstats ekran okuması topu OCR'da maskeler (RS `WatchService.kt:296`; WEEM `OwnOverlays`, `CAPTURE.md` 7.1).
- Ayarlar (NB `MainActivity.java:37-70, 95-102`): "Sıra ve top değeri" (oklarla sıra, daire = top), boyut kaydırıcıları (`float_ball_dp`, `float_thick_pct`, `float_h_pct`, `float_v_pct`, `float_text_port_pct`, `float_text_land_pct`).

**WEEM:** 6.1 saf ve aynen (`DashboardTest`, `ScooterFeedTest`); 6.2–6.3 Compose Scooter sekmesi + `:app` üstü pencere (A5.4): metinler, eşikler (5 sn bayat, 80 ms, 400 ms, 2 sn/10 sn bildirim), alanlar ve geometri aynen (`FloatingTest`). "Sürüş Paneli" adı ekranda "Scooter" sekmesi olur; bildirim başlıklarındaki "Rawstats vardiyası" → "WEEM vardiyası" (bilerek fark, 14).

## 7. S3 — Sürüş kaydı (NB `RecordingService.java`, `LogStore.java`, `LogExport.java`, politikalar)

### 7.1 Kip ve servis

| Kip | Başlatan | GPS | SQLite | Ön plan türü |
|---|---|---|---|---|
| Canlı panel (`live_only`) | Panel "Canlı panel • kayıt yok", `auto_live`, Rawstats vardiyası (`SurusPaneli.startLive`) | yok | yok | `CONNECTED_DEVICE` |
| Kayıt | Panel "Panel + kayıt", eşleştirme (her zaman kayıt) | var | var | `LOCATION \| CONNECTED_DEVICE` |
| Canlıdan kayda | "Kayda geç" (bildirim/panel; `RECORD` eylemi) | açılır | açılır (`upgraded_from_live_panel: true`) | türe konum eklenir; reddedilirse uyarı "Kayıt açık, ancak arka planda konum izni verilmedi; GPS uygulama açıkken kaydedilir" |

- Başlatma niyeti (39-40): `device`, `device_name` (= `ble_name`, yoksa `device_name`'in " • " öncesi), `pair`, `live_only` (= canlı ve eşleştirme değil), `crypto_mode = gen2_fw`, `sweep` (tercih), `sweep_board = 0x16`, `sweep_from = 0x00`, `sweep_to = 0xc0`.
- İzin (71): Bluetooth (API ≥31 `BLUETOOTH_CONNECT`) **ve** (ince konum **ya da** canlı + API ≥31).
- Durum bayrakları (`active`, `recording`, `maintenance`, `dashboardVisible`, `summary`, `warning`, `dashboard`; 15-26): `active` başka başlatmayı yok sayar; `maintenance` (eski kayıt silme) sırasında kayıt başlamaz.
- `begin` (73-94): `trip.reset("")`; kayıtsa depo; oturum olayı `session_start {sdk, device_model, version, decoder_available: true, device_verified: false, crypto_mode, profile: 0, sweep, sweep_board/from/to, wire_transcript_sensitive: true}`; `PARTIAL_WAKE_LOCK "NinebotLogger:Recording"` 24 sa; GPS; scooter seçiliyse `BleCapture.start` (BLE iş parçacığında), değilse durum "GPS kaydı • scooter seçilmedi".
- Saniyelik tik (113-131, yazıcı iş parçacığı `logger-writer`): BLE önceliği; kuyruk taşması uyarısı ("Teşhis kuyruğu doldu: N BLE ayrıntısı atlandı; GPS ve sürüş kaydı sürüyor" + `log_backpressure`); **1 Hz örnek** `ble.sample()` → `energy.add`, `trip.add`, ana uygulamanın `SampleSink`'i (hatası bağlantıyı durdurmaz; `true` dönerse `hostLogging`); kayıttaysa olay `sample` (7.2); bildirim; uyandırma kilidi tazelenir.
- Bitiş (`finish`/`finishStorage`, 154-167): BLE önce kapanır, sonra depo: `log_summary {dropped_ble_events, omitted_diagnostic_events}`, oturum durumu `failed` (özet "Kayıt durdu:" ile başlıyorsa) / `stopped_with_log_gaps` (atlanan BLE olayı varsa) / `stopped`; kilit bırakılır, `null` kare, özet "Canlı panel kapatıldı • kayıt yapılmadı" ya da "Kayıt tamamlandı • GPS N • BLE M". Ölümcül hata (`fatal`): uyarı "Kayıt durdu: depolama veya servis hatası. Ayarlar bölümünü kontrol et." ve kapanış.

### 7.2 GPS (kayıtta; `NinebotGpsPolicy.java:5-9`, `RecordingService.java:97-112, 132`)

- `GPS_PROVIDER`, `minDistance 0`; aralık 1.000 ms; sabit duruş (hız ≤0,3 m/s, doğruluk ≤15 m, hız doğruluğu ≤0,5 m/s) **15 sn** sürünce 3.000 ms; hareket, belirsizlik ya da >5 sn fix boşluğu → 1.000 ms; sağlayıcı açılınca/kapanınca sıfırlanır; değişince olay `gps_cadence {requested_interval_ms}`.
- Olay `gps_fix`: `fix_wall_ms`, `fix_mono_ns`, `gps_age_ms`, `lat`, `lon`, `accuracy_m`, `gps_speed_kmh`, `speed_accuracy_mps`, `bearing_accuracy_deg`, `bearing_deg`, `altitude_m`, `mock`; `gps_state {state, provider}`.
- Olay `sample`: `elapsed_ms`, `gps` (son fix), `gps_fresh` (≤5 sn), `gps_request_interval_ms`, `ble_state`, `scooter_telemetry` (3.2).

### 7.3 Depo (`ninebot-logger.db`, NB `LogStore.java`)

- SQLite sürüm 1, WAL, `PRAGMA foreign_keys=ON`; `sessions(id INTEGER PRIMARY KEY, started_ms INTEGER NOT NULL, ended_ms INTEGER, state TEXT NOT NULL, device TEXT)`, `events(id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES sessions(id), wall_ms INTEGER NOT NULL, mono_ns INTEGER NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL)`, `INDEX event_session(session_id,id)`; `onUpgrade` → `IllegalStateException("Migration required")` (12-20).
- `start(device)`: açık kalmış `recording` oturumları `interrupted` olur, yeni oturum `recording` (21-29); `end(id, state)` (30); `event` hazır deyimle (31).
- `json(k, v, …)`: `null` → `JSONObject.NULL` (33).
- `deleteCompletedBefore(cutoff)`: yalnız `recording` olmayan ve `started_ms < cutoff`; ardından `VACUUM`; yalnız ayarlardaki "Eski kayıtları sil" (30 gün; onaylı; NB `MainActivity.java:112-118`).

Olay politikası (`NinebotLogPolicy.java:9-21`): sık türler `ble_raw`, `ble_write`, `ble_write_result`, `protocol_tx` (READING'deki), `ble_measurement` yalnız "Ayrıntılı teşhis" (`detailed_diagnostics`) açıkken ve kaydın ilk **10 dk**'sında (`DETAIL_MS`), en çok **10.000**; diğer BLE olayları ayrı **10.000** bütçe; el sıkışma TX'i (`stage=READING` içermeyen `protocol_tx`) politikada `handshake_tx` adıyla **nadir** bütçeye sayılır, satır yine `protocol_tx` yazılır (`RecordingService.java:137-139`); atlananlar `omitted`. Yazma kuyruğu (`NinebotEventQueue.java:8`): sınır **2.048** (`RecordingService.java:56`); dolu ise BLE olayı atılır ve sayılır; GPS ve örnek satırları kuyruğu atlar (doğrudan yazıcı). Canlı kipte hiçbir olay yazılmaz.

### 7.4 Dışa aktarma ZIP (NB `LogStore.java:45-75`, `LogExport.java:7-34`; `DashboardActivity.java:185-200`)

- Seçenekler: "Son kayıt • konumsuz" (varsayılan), "Son kayıt • GPS rotası dahil", "Tüm kayıtlar • GPS rotaları dahil", "Tam teşhis • son kayıt • HASSAS" (ek uyarı: "Bu dosyadan scooter eşleştirme anahtarı çıkarılabilir…"); kayıt sırasında yapılmaz. Dosya `ACTION_CREATE_DOCUMENT`, ad `NinebotLogger-<ms>.zip`.
- İçerik: `manifest.json` (`schema: 1`, `app: "Sürüş Paneli"`, `version`, `decoder_available: true`, `device_verified: false`, `utc_export_ms`, `wire_transcript_sensitive`, `full_diagnostics`, `location_included`, `latest_session_only`, `note`), `sessions.jsonl`, `events.jsonl` (satır = sütunlar; `device` filtreli ZIP'te `null`).
- Filtreli (izin listesi, eski veritabanları dahil): `sample` (`elapsed_ms`, `gps_fresh`, `gps_request_interval_ms`, `gps`, `scooter_telemetry`: özet alanlar + `exportable` alanlar), `gps_fix` (konum seçilmediyse `lat`, `lon`, `altitude_m`, `bearing_deg`, `bearing_accuracy_deg` çıkar), `log_backpressure`/`log_summary`, `session_start` (`version`, `sdk`, `decoder_available`, `device_verified`, `sweep`), `gps_cadence`, `battery_config` (`probe`, `known_boards`), `battery_boards`/`battery_probe_*`/`battery_lost`/`battery_quiet`/`battery_back` (`detail`); başka tür **çıkmaz** (ham tel, el sıkışma, serbest metin). `pick` yalnız düz değer (sayı, bool, **metin**, JSON null) kopyalar; iç içe nesne/dizi kopyalanmaz (A5.3: kod böyle, belge düzeltildi).
- Tam teşhis: bütün yükler, `device` ve konum dahil.

**WEEM:** dosya adı, şema, durum değerleri, olay türleri ve alanları, politika sabitleri, ZIP biçimi ve izin listesi **aynı** (`VERI_SOZLESMESI` 7–8; eski ZIP'leri okuyan araçlar çalışır). `LogExport` ve politikalar `logic/scooter`'da saf (`LogPolicyTest`, `EventQueueTest`, androidTest `LogExportTest` → JVM testine). Dosya adı `NinebotLogger-<ms>.zip` ve `app: "Sürüş Paneli"` korunur (analiz araçları; değiştirmek kullanıcı kararı, 14). GPS kaydı: 2 telefonda yalnız Receiver'da (scooter orada).

## 8. S4 — Yolculuk sayacı ve R14 — sipariş başına enerji

### 8.1 1 Hz örnek (NB `ScooterEnergy.java:27-47`, `BleCapture.java:74`)

`Sample(wallMs, monoMs, intPct, intV, intA, extPct, extV, extA, powerW, tempC, speedKmh, odometerKm, externalKnown)` — `ScooterEnergy.sample(values, wall, mono, harici kart)`: `intPct` = `bms_07_percent`, yoksa `battery_percent` (15 sn); `intV`/`intA` iç paket (3 sn); harici kart >0 ise `extPct` (15 sn), `extV`/`extA` (3 sn); `powerW` = `NinebotMetrics.powerW` (3 sn); `tempC` (15 sn), `speedKmh` (3 sn), `odometerKm` (15 sn); `externalKnown` = harici kart biliniyor (taze olmasa da). Harici kart = `external_bms_boards`'un **ilk** kartı. `bothPacks()` = harici bilinmiyor ya da `extV` ve `extA` var (D10). `empty()` = `intV, intA, powerW, speedKmh, intPct, extV` hepsi null. Değer yoksa örnek de yok (`latestValues` boş → null).

Servis tiki her saniye örneği `energy` deposuna, `trip` sayacına ve ana uygulamanın `SampleSink`'ine verir (`RecordingService.java:118-119`).

### 8.2 Enerji bloğu (NB `ScooterEnergy.java`, saf)

Sabitler: `SOURCE = "surus_paneli"` (13), `MAX_STEP_MS = 3.000` (15), `MIN_COVERAGE = 0,8` (17), `CAPACITY = 43.200` (~12 sa; 19), `FEEDING_A = −0,1` A (21), `EDGE_MS = 15.000` (24). `add` boş, sırasız (`monoMs ≤ son`) örneği atar; kapasite aşılınca en eski düşer (47).

`block(from, to)` (51-66; pencere duvar saatiyle, kapalı aralık):
- `source` = örnek yoksa `not_measured`, varsa `surus_paneli`; `power_verified = false`.
- Adım (ardışık iki örnek): `0 < dt ≤ 3.000` ms, iki güç de var ve iki örnek de `bothPacks` ise `covered += dt`, `wh += −ort(güç) × dt / 3.600.000` (tüketim pozitif), `wSum += ort × dt`.
- `enough` = `span > 0` ve `covered ≥ 0,8 × span`.
- `internal`/`external` = `{pct_start, pct_end, v_avg, a_avg}`: uçlar `edge` (pencere ucuna en yakın değerli örnek, uca **≤15 sn** değilse `null`), ortalamalar yalnız `enough` ise.
- `external_attached` = herhangi bir harici değer varsa `true`, yoksa `null`; `external_feeding` = harici değer varsa (`extA < −0,1` olan örnek var mı), yoksa `null`.
- `vxa_w_avg` = `enough` ve `wMs>0` ise `−wSum/wMs` (tüketim pozitif); `wh` = `enough` ise; `esc_w_avg` = `null` (kaynak yok); `temp_c` = bütün örneklerin ortalaması (her zaman); `scooter_speed_kmh_avg` = `enough` ise; `odometer_km_start/end` = `edge`.
- Anahtar ve iç içe yapı Rawstats `EnergySegments.energy()` ile aynı (RS `EnergySegments.kt:28-56`); Rawstats `energyFrom` yalnız şema anahtarlarını, sonlu sayıları ve bilinen `source`'u (`not_measured`, `surus_paneli`, `surus_paneli_ble`, `manual`) alır, yoksa tüm blok `not_measured`.
- RS `ScooterBridge.energy(from, to?)` (33-37): kaynak yoksa ya da pencere açık/ters (`to == null || to ≤ from`) ise `not_measured` (kısmi rakam bütün gibi görünmesin); hata `not_measured`.

### 8.3 Yolculuk sayacı (NB `TripMeter.java`, saf)

- `MOVING_KMH = 1,0` (14); `reset(label, wall)` (37); `end(label, wall)`: etiket eşitse etiketsiz sıfırlar ve `true` (başka siparişin sayacına dokunmaz; 40).
- `add(s)` (41-50): boş/sırasız atılır; ilk örnekte başlangıç zamanı; pil başlangıç/son (iç ve harici ayrı); `externalKnown` yapışkan; `powerW` son değer; `dt ≤ 3.000` ms ise: iki güç var ve iki örnek `bothPacks` → `e = −ort × dt/3.600.000`, `e ≥ 0` ise `wh += e`, değilse `inWh −= e` (giren enerji ayrı), `coveredMs += dt`; iki güç var ama paket eksik → `partialMs += dt`; iki hız ≥1 km/s → `movingMs += dt`, `km += ort hız × dt/3.600.000`.
- `stats()` (51-53): `wh`/`inWh` hiç tam adım yoksa `null`; `pctUsed` = iç + harici düşüş (ikisi de varsa), harici bilinmiyorsa yalnız iç, harici bilinip yüzdesi okunmadıysa `null`; `max(0, …)`; `avgMovingKmh` = `km / (movingMs sa)` (hareket yoksa `null`); `km`, `movingMs`, son `powerW`.
- Sıfırlama: bağlantı başında (`RecordingService.java:75`, etiket boş), Rawstats'ta sipariş kabulünde `resetTrip(tripId)`, teslimde `endTrip(tripId)` (RS `ScooterLink.kt:80-81`, `OrderTracker.kt:517-519`).

### 8.4 Rawstats: saniyelik satır ve siparişe enerji (RS `ScooterLink.kt:154-200`, `ScooterSamples.kt`)

Her 1 Hz örnekte (`sample`, Sürüş Paneli'nin yazıcı iş parçacığında):

1. Önceki örnek saklanır; canlı yük duyurulur (`publish`, YolGo paylaşımı açıksa; S7 ile kalkar).
2. Vardiyada kayıt izni yoksa (`Store.canRecordScreen`) `false` (bir şey yazılmaz).
3. Enerji adımı `ScooterSamples.step(önceki mono, önceki güç, mono, güç, 3.000, önceki.bothPacks && şimdiki.bothPacks)` (37-42): `dt ≤ 0`, `> 3.000`, güç eksik/sonsuz → `null`; `wh = ort(güç) × dt / 3.600.000` (panel işareti, + şarj); `usedWh = max(0, −wh)`, `recoveredWh = max(0, wh)`, `complete`. → `OrderTracker.scooterEnergy` → `core.addEnergy` (yalnız vardiyada; `ORDERS.md` 3.11 `addEnergy`).
4. D8 odometre (`ShiftDistance.observe`; bellek + tercih `shiftOdoFirstKm`, `shiftOdoLastKm` (m), `shiftOdoLastAt`; ilk değer değişmedikçe en sık 30 sn'de bir yazılır; vardiya başı/sonu sıfırlanır; `CORE.md` 1.4 D8).
5. Satır **`economics-scooter.jsonl`** (gün klasörü, örneğin duvar saatinin günü; `ScooterSamples.row`, 44-51): `schemaVersion: 1`, `kind: "scooter_sample"`, `wall_ms`, `mono_ns` (= mono ms × 10⁶), `source: "surus_paneli"`, `scooter_id` (tercih `economics.scooterId`, yoksa `scooter-1`), `scooter_id_source` (`settings` / `default_single_scooter`), `power_verified: false`, `speed_kmh` (1 ondalık), **`vxa_w`** = −panel gücü (tüketim pozitif, 1), `int_pct` (0), `int_v`, `int_a` (2; BMS işareti), `ext_pct` (0), `ext_v`, `ext_a` (2), `temp_c` (1), `odometer_km` (1); eksik/sonsuz `null`, `−0.0` yazılmaz (21-26). Konum yok (`gps.csv`'de). `true` dönerse panel "Rawstats vardiyası kaydediyor" der.

Teklif anında scooter pili: `ScooterBridge.batteryPct()` = taze (≤15 sn, `ScooterLink.FRESH_MS`) karenin `batteryPct`'i, sonlu ve 0–100 (RS `ScooterBridge.kt:39`, `ScooterLink.kt:27, 79, 84`; `ORDERS.md` 4.3 adım 16).

**WEEM:** 8.1–8.3 saf ve aynen (`EnergyTest`, `TripMeterTest`, `ScooterSamplesTest`, `ScooterOrderEnergyTest`, `ScooterBridgeTest`); `energy(from, to)` `logic/core` `EnergyBlock` döndürür (`not_measured` aynı kural). `economics-scooter.jsonl` adı, şeması ve yuvarlamaları aynı; `source` `surus_paneli` korunur (analiz araçları ayırt etmek için `scooter_id_source` yeter). Sipariş enerjisi akışı 12.2.

## 9. S5 — Harici batarya ("Battery 2")

### 9.1 Yoklama (NB `NinebotSession.java:57-69, 151-166`; `NinebotMetrics.java:31-36`)

- Tercih `battery_probe` (ayar "Harici batarya taraması", varsayılan false; "Bir kerelik, salt-okunur arama"): açıksa sonraki bağlantıda aday kartlar yoklanır, bitince `false` yazılır (NB `BleCapture.java:56`).
- Adaylar `EXTERNAL_BMS_CANDIDATES` (31 kart, sırayla): `08, 06, 09, 05, 0a, 03, 0b, 01, 0c, 0d, 0e, 0f, 10, 11, 12, 13, 14, 15, 17, 18, 19, 1a, 1b, 1c, 1d, 1e, 1f, 20, 21, 24, 25` (bilinen `00/16` VCU, `02` MCU, `04` BLE, `07` iç BMS ve her zaman okunan `22/23` yok).
- `batteryBoards(known, probe)` (63-69; AUTH bitmeden): bilinen kartların voltajı iç paket voltajının **hemen arkasına** (G3'te hep sessiz `22/23`'ün önüne), yoklama kartları sona; `0x16` ve `4` ile zaten listedekiler atlanır; not `battery_boards {known, probe, tries}`; yoklanacak kart kalmadıysa hemen `batteryProbeDone(false)`.
- Her turda **bir** yoklama okuması (2.5 tur sırası); yanıt: voltaj `30 ≤ V ≤ 60` ise aday (`battery_probe_candidate`, kartın batarya registerları eklenir), değilse kart düşer (`battery_probe_reject {board, raw_hex}`). Zaman aşımı kart başına `PROBE_TRIES = 2` (`battery_probe_timeout` / `battery_probe_miss`); yoklama zaman aşımı kayıp bağlantı sayılmaz.
- Adayın yüzdesi (`0x8f`) yanıtlarsa **onay** (`battery_probe_found {board, board_hex}`; `port.batteryFound` → `external_bms_boards = addBoard(…)`), kalan yoklama ve diğer adaylar biter (`battery_probe_partial reason=other_pack_confirmed`; tek harici paket beklenir). Yüzde sınır dışı/sessiz → aday düşer (`reason=percent_out_of_range` / `percent_silent`).
- Bitiş (`maybeProbeDone`): not `battery_probe_done {found, boards}`, durum "G3 bağlı • harici batarya bulundu (kart kk)" / "G3 bağlı • harici batarya bulunamadı".
- Olay `battery_config {probe, known_boards}` bağlantı başında (NB `BleCapture.java:60-61`).
- Saha: kullanıcının scooter'ında harici paket **`0x06`** (9 Ekim; `docs/FIELD_TEST_2026-10-09.md`), voltaj 49,0–49,8 V, akım deşarjda eksi.

### 9.2 Sessiz paket (D12, NB `NinebotSession.java:45-51, 124-127, 162-163`)

Daha önce yanıtlamış harici kart (`7` ve `0x16` değil) 3 ardışık zaman aşımında **düşmez**: sessiz olur (`battery_quiet {board, timeouts, retry_ms}`), değerleri bayatlar, yalnız voltajı **15 sn'de bir** turun dışında sorulur; sessiz okumalar kayıp bağlantıya sayılmaz; ilk yanıtta bütün registerları geri gelir (`battery_back`). `quietPacks()` panel ve testler için.

### 9.3 Güç ve enerjide harici paket

- Güç toplamına yalnız yüzdesi ≤60 sn önce okunmuş harici paket girer (`PACK_CONFIRM_MS`; 3.2).
- Harici paket sürüşte iç paketi besler (9 Ekim): yalnız iç paket okunursa tüketim eksik (~%40) ya da negatif görünür; bu yüzden enerji ve sayaçta yalnız iki paketin gücü olan adımlar sayılır (D10; 8.2–8.4). InfoSheet metni bunu kullanıcıya söyler (NB `InfoSheet.java`).

### 9.4 Bağlandı / koptu duyurusu (NB `PackPresence.java`, saf; `RecordingService.java:41-47`)

- `STABLE_MS = 60.000` (10). `observe(present, now)` (15-23): yeni durum 60 sn sürmeden konuşmaz; aynı durumu tekrar söylemez; hiç görülmemiş paket "koptu" denmez. `linkDown(now)` (25): bağlantı ≥60 sn kopuk kalırsa sonraki kare **yeni bağlantı** (B3: tekrar söylenir); kısa kopma (3–5 sn) aynı bağlantı.
- Servis her karede (`present` = `extBatteryPct != null`); `null` kare → `linkDown`. Toast (uzun): "İkinci batarya bağlı • %<p>" / "İkinci batarya 60 sn'dir okunmuyor".

**WEEM:** aday listesi, eşikler, yoklama sırası ve D12 aynen (`BatteryProbeTest` 64 satır / 854 çalışma denetimi); `external_bms_boards` ve `battery_probe` tercihleri aynı. Toast yerine Scooter sekmesinde kısa bildirim çubuğu olabilir (metin aynı; A5.4 kararı, davranış aynı).

## 10. S8 — Tanı araçları

### 10.1 Register keşfi (sweep)

- Tercih `sweep` (ayar "Register keşif (sweep) modu", "Salt-okunur teşhis • normal sürüşte kapalı tut", varsayılan false; bağlantı yokken değişir).
- Kapsam sabit: kart `0x16`, `0x00…0xc0` (193 register; `RecordingService.java:40`), her biri 2 bayt işaretli ham (`sweep_16_xx`, 3.1); `Sweep` doğrulaması: kart 0–255, `0 ≤ from ≤ to ≤ 255`, aralık ≤255; hatalı tanım `sweep_config_rejected` ile atlanır (`NinebotMetrics.java:47-51`, `RecordingService.java:76`).
- Her turda **bir** sweep okuması; sweep zaman aşımı register düşürmez ve kayıp bağlantıya sayılmaz; sweep değerleri panelde, güçte, dışa aktarmada yok (`exportable`, `packBoards`).

### 10.2 Ayrıntılı teşhis ve tam ZIP

- `detailed_diagnostics` (ayar "Ayrıntılı teşhis", "Sonraki kaydın ilk 10 dakikası"): sık olaylar (`ble_raw`, `ble_write`, `ble_write_result`, READING `protocol_tx`, `ble_measurement`) yazılır (7.3); kapalıyken satır nesnesi hiç kurulmaz (`frequent` + `Supplier`).
- Tam teşhis ZIP (7.4): ham tel, el sıkışma, adres, konum; tel kayıtlarından eşleşme anahtarı çıkarılabilir (`wire_transcript_sensitive: true`).
- Ayar ekranı durum paneli (`summaryWatched`): saniyede bir "KAYIT AÇIK • N dk / GPS … / <BLE durumu> / GPS: N ölçüm • BLE: M paket / <ölçümler>" ya da "CANLI PANEL • …" (`RecordingService.java:122-124`).
- Bilgi sayfası (`InfoSheet.java`): "Bağlantı bilgisi", "Eşleştirme", "Gelişmiş bağlantı", "Güç nasıl hesaplanır?", "Harici batarya", "Register keşfi", "GPS, Bluetooth ve pil", "Canlı panel ve küçük pencere", "Rawstats vardiyası" bölümleri (metinler kaynakta).

### 10.3 Dizüstü araçları (NB üstü `tools/`, CI dışı)

- `tools/g3_readonly.py` (222 satır; `bleak 3.0.2`, `pycryptodome 3.23.0`): aynı UUID'ler ve `gen2_fw`; izin verilen çerçeveler `(4,0x5b,0,0)`, `(4,0x5d,0,14)`, `--pair` ile `(4,0x5c,0,16)`, ve yalnız sabit 7 okuma (`16:55`, `16:5f`, `16:62`, `16:6b`, `07:8c`, `22:8c`, `23:8c`).
- `tools/reference/nb_crypto.py` — **lisans:** Apache License 2.0, `NootNooot/segway-ninebot-ble-cli` @ `bb6f3d325237fa91e683df27f2197f9cb771e841` (2026-10-03), SHA-256 `1302a7c864a95471a73a3c9f6be9892bfc09a3a324c0f1211dc35e71d775607c`, değiştirilmemiş; `LICENSE` ve `NOTICE` (Copyright 2026 NootNooot; Segway/Ninebot ile ilişkisiz, birlikte çalışabilirlik için tersine mühendislik) yanında (NR `PROVENANCE.md`). Modülün kendi tekrar denetimi kapalı; çalıştırıcı katı RX denetimleri ekler. Eşitlik testi Java şifrelemesini, parola üretimini ve sentetik PRE_COMM / SET_PWD / AUTH / ilk okuma dizilerini buna karşı sınar; G3 uyumluluğu ya da gerçek el sıkışma kanıtı değildir.

**WEEM:** `sweep`, `detailed_diagnostics` ve tam ZIP aynı tercih adları ve sınırlarla (`SweepTest`, `LogPolicyTest`); Scooter sekmesinde "Tanı" altında. `nb_crypto.py` WEEM reposuna **yalnız test referansı** olarak, `LICENSE` + `NOTICE` + `PROVENANCE.md` ile birlikte değiştirilmeden kopyalanır (Apache-2.0 koşulu: lisans ve bildirim korunur); APK'ya girmez. Python araçları CI'da çalışmaz (bugünkü gibi); `ProtocolTest` sabit vektörleri JVM'de yeter (13).

## 11. Veri sözleşmesi (`VERI_SOZLESMESI` 5.2, 5.3, 6, 7.2, 8 ile aynı; A5.1 düzeltmeleri işaretli)

### 11.1 Tercihler

- **`logger`** (`LoggerPreferences.get`, NB `LoggerPreferences.java:8-20`): ilk açılışta eski `MainActivity` dosyasından **bir kez** göç: `device` yoksa `device`, `device_name`, `ble_name` birlikte (yeni seçimle eski BLE adı karışmasın), `sweep`, `crypto_mode`; sonra `legacy_main_migrated=true`. Anahtarlar (tür, varsayılan): `auto_live` (Bool, false), `battery_probe` (Bool, false), `ble_name` (String, yayın adı), `crypto_mode` (String, `gen2_fw`), `detailed_diagnostics` (Bool, false), `device` (String, BLE adresi), `device_name` (String, "<ad> • <adres> • <rssi> dBm"), `external_bms_boards` (String, "06" gibi), `float_ball_dp` (Int, 100), `float_ball_field` (String), `float_fields` (String), `float_h_pct` (80), `float_text_land_pct` (100), `float_text_port_pct` (100), `float_thick_pct` (125), `float_v_pct` (60), `float_x`, `float_y` (Int), `floating` (Bool, **true**), `keep_screen_on` (Bool, **true**), `legacy_main_migrated` (Bool), `sweep` (Bool, false).
- **A5.1 düzeltmesi:** `export_full`, `export_latest`, `export_location`, `immersive` **tercih değildir**: `DashboardActivity` örnek durum paketi (`onSaveInstanceState`, NB `DashboardActivity.java:36, 61, 203`). `VERI_SOZLESMESI` 5.2 listesinden çıkarıldı.
- **`kuryelog`** (Rawstats): `scooter.autoConnect` (Bool, true), `scooter.shiftLink` (Bool), `shiftOdoFirstKm`, `shiftOdoLastKm` (Long m, −1), `shiftOdoLastAt` (Long ms), `economics.scooterId` (String; yoksa `scooter-1`).
- **`pairing-v1`** + Keystore `NinebotLogger.Pairing.v1`: 4.1 (beş anahtar biçimi, AAD, IV düzeni).

### 11.2 `ninebot-logger.db` (7.3)

Ad, sürüm 1, WAL, iki tablo ve dizin aynı. `sessions.state`: `recording`, `interrupted`, `stopped`, `stopped_with_log_gaps`, `failed`. `events.kind` (kaynakta yazılan bütün türler): `session_start`, `sample`, `gps_fix`, `gps_state`, `gps_cadence`, `log_backpressure`, `log_summary`, `sweep_config_rejected`, `pair_attention`, `pair_attention_notification`, `ble_connected`, `ble_state`, `ble_mtu`, `ble_subscribed`, `ble_priority`, `ble_raw`, `ble_write`, `ble_write_result`, `ble_measurement`, `ble_reassembly_rejected`, `ble_key_error`, `gatt_inventory`, `local_key_state`, `battery_config`, ve oturum notları (yük `{"detail": "<anahtar=değer; …>"}`): `handshake_started`, `pre_comm_attempt`, `pre_comm_ok`, `pair_started`, `pair_reply`, `pair_first_reply`, `pair_to_auth`, `pair_resumed`, `pair_window_expired`, `auth_reply`, `auth_failed`, `authenticated`, `protocol_tx`, `protocol_rejected`, `protocol_state`, `read_timeout`, `read_disabled`, `measurement_rejected`, `battery_boards`, `battery_probe_candidate`, `battery_probe_reject`, `battery_probe_timeout`, `battery_probe_miss`, `battery_probe_found`, `battery_probe_partial`, `battery_probe_done`, `battery_quiet`, `battery_quiet_timeout`, `battery_back`. `battery_lost` dışa aktarma izin listesinde var ama bugün yazılmıyor (eski veritabanları için). `detail` metninin biçimi (`;` ile ayrılmış `anahtar=değer`) testlerde alt dize olarak denetlenir → **birebir**.

### 11.3 Gün klasörü ve olaylar (Rawstats)

- `economics-scooter.jsonl` (8.4; `BackupFiles.logNames` içinde, analiz dışa aktarmasında `KEEP_ALL`, konum alanı yok).
- `events.jsonl` `type: "scooter_link"`, `action` ∈ `skip` (`reason` ∈ `auto_off`, `already_active`, `no_scooter`, `not_paired`, `no_bluetooth_permission`, `check_failed`), `start`, `failed` (`reason` = sınıf adı ya da `refused`), `kept_recording`, `stop`, `stop_failed`, `scooter_data_back`, `scooter_data_missing` (`reason: null`). **WEEM ek:** `skip` `reason: "role_provider"` (S11, 2 telefon modunda Provider; yeni değer, eski araçlar bilinmeyen nedeni yok sayar).
- `economics-energy.jsonl` `energy` bloğu (8.2): `:economics` yazar (A4.1), `:scooter` üretir.
- Eski `scooter.jsonl`, `scooter_events.jsonl`, `scooter_averages.jsonl`: yalnız okunur (`VERI_SOZLESMESI` 7.1).

### 11.4 Bildirimler (`CORE.md` 3.3 tablosu)

| No | Kanal | Önem | Ne | Kaynak |
|---|---|---|---|---|
| 7 | `recording` "Scooter kaydı" | LOW, sürekli (FGS) | servis bildirimi (6.2) | NB `RecordingService.java:64-66, 178-186` |
| 8 | `pairing-attention` "Eşleştirme onayı" | HIGH, sessiz | eşleştirme uyarısı (4.2) | NB `PairAttention.java:11, 22-24` |
| 4902 | `scooter_link` "Scooter bağlantısı" | DEFAULT | M23 (5.2) | RS `ScooterLink.kt:31-52` |
| 5102 | `auto_start` | HIGH | "Scooter bağlanmadı" (5.3) | RS `AutoStartNotice.kt:19, 34-38` |

### 11.5 Canlı değer mesajları (`logic/core`, A2.5)

`ScooterLive` v1 (`ScooterMessages.kt`; bugünkü `scooter/v1/live` alanları: `schemaVersion`, `generatedAtMs`, `source`, `linkActive`, `ageMs`, `scooter{internalPct, externalPct, speedKmh, powerW, voltageV, currentA, rangeKm, tempC, odometerKm}`, `trip{wh, pctUsed, avgMovingKmh, km, movingMs, startedAtMs}`; `source` WEEM'de `weem_surus_paneli`), `BatterySummary` v1, `EnergyRequest`/`EnergyResult` v1, `EnergyBlock`. Doldurma kuralı (RS `ScooterLink.liveValues`, 66-76): `linkActive` = bağlantı servisi çalışıyor; `ageMs` = son kare yaşı (bağlantı yoksa `null`); değerler son kareden (bayatsa da; tazelik `ageMs ≤ 15 sn` ile okunur); `trip` yalnız bağlantı varken; hata → `linkActive=false`, hepsi `null`. Sağlayıcı (`content://com.kuryelog.app.economics/scooter/v1/live`) kalkar (S7).

### 11.6 Serbest (iç) adlar

Servis eylemleri `az.ninebot.logger.STOP`/`RECORD`, niyet ekstraları (`device`, `pair`, `live_only`, `sweep_*` …), iş parçacığı adları, uyandırma kilidi etiketi, `SurusPaneli` dış yüzü: dışa açık değil (`exported=false`) → WEEM'de ad serbest (`VERI_SOZLESMESI` 2: ad serbest, tür aynı `location|connectedDevice`).

## 12. WEEM yerleşimi

### 12.1 Modüller

| Yer | İçerik | Görev |
|---|---|---|
| `logic/scooter` (saf Kotlin) | `NinebotCrypto`, `NinebotFrames`, `NinebotSession` (+ `Port`), `NinebotMetrics`, `NinebotMode`, `NinebotLink`, `SessionKey`, `Timing`, `ScooterReconnect` (M23), `PairingRules` (KeyVault aday/etkin/önceki kuralları, depo arayüzü `PairingStore`) | A5.2 |
| `logic/scooter` | `NinebotDashboard`, `ScooterEnergy`, `TripMeter`, `PackPresence`, `NinebotEventQueue`, `NinebotLogPolicy`, `NinebotGpsPolicy`, `LogExport` (org.json), `ScooterSamples`, `ScooterLiveMapper` (kare + sayaç → `ScooterLive`), `ScooterLinkRules` (5.3 vardiya aç/kapat kararları, saf) | A5.3 |
| `logic/scooter` | `FloatGeometry`, `PanelField`, `ScooterUiText`, `ScooterPanel`, `FloatRules`, `ScooterSettings` | A5.4 (bitti, 13.6) |
| `:scooter` (Android) | `BleCapture` (GATT), `ScooterService` (bugünkü `RecordingService`; FGS türü `location\|connectedDevice`), `KeyVault` (Keystore, `pairing-v1`), `PairAttention`, `LoggerPreferences` (göç dahil), `LogStore` (`ninebot-logger.db`), `ScooterBridgeImpl` (`logic/core` `ScooterBridge`), vardiya dinleyicisi (`ShiftListeners`'a takılır: başta `open`, sonda `close`) | A5.2–A5.3 |
| `:scooter` + `:app` | Compose Scooter sekmesi (canlı panel, tarama, eşleştirme, ayarlar, tanı, dışa aktarma), yüzen panel (`TYPE_APPLICATION_OVERLAY`; WEEM'in kendi ekranları açıkken gizli), PiP | A5.4 |

Bağımlılık: `:scooter` → yalnız `:core` (plan 4.2). `:orders` ve `:economics` scooter'ı yalnız `ScooterBridge` üstünden görür.

**A5.2 durumu (17 Ekim):** ilk satır bitti; ikinci ve üçüncü satırın saf sınıflarından `NinebotDashboard`, `ScooterEnergy`, `TripMeter`, `PackPresence`, `NinebotEventQueue`, `NinebotLogPolicy`, `NinebotGpsPolicy`, `FloatGeometry`, `PanelField` da A5.2'de (suitleri için, 13.4). Ek saf parçalar: `LinkRetry`, `PairWindow`, `GattPlan` (`BleCapture` kuralları), `PairingVault` + `PairingStore` (`KeyVault` kuralı ve biçimi; `PairingRules` yerine bu ad). Android `:scooter`'da: `BleLink` (`BleCapture`'ın GATT/oturum/yeniden bağlanma kısmı), `KeyVault` (Keystore), `LoggerPreferences`, `json` (`LogStore.json`). A5.3'e kalan: `LogExport`, `ScooterSamples`, `ScooterLiveMapper`, `ScooterLinkRules`; Android `ScooterService`, `LogStore`, `PairAttention`, `ScooterBridgeImpl`, vardiya dinleyicisi, `BleLink`'e `telemetry`/`sample`/`measurements`. **A5.3 bitti (13.5).**

### 12.2 `ScooterBridge` ve sipariş enerjisi

- `logic/core` `ScooterBridge` (A2.5): `orderAccepted(tripId, atWallMs)` → `TripMeter.reset(tripId)`; `orderDone(tripId, atWallMs)` → `TripMeter.end(tripId)`; `energy(from, to)` → `ScooterEnergy.block` → `EnergyBlock` (açık/ters pencere `not_measured`, hata `not_measured`); `batteryPct()` → taze (≤15 sn) kare `batteryPct`, 0–100; `live` → `ScooterLive` akışı (her kare ve bağlantı değişimi).
- **Eksik arayüz (A5.3 ekler):** Rawstats'ta `:orders`'ın `core.addEnergy` beslemesi her 1 Hz örnekten gelen `ScooterSamples.Step`'tir (8.4). `logic/core`'a `ScooterBridge.steps: SharedFlow<EnergyStep>` (`usedWh`, `recoveredWh`, `ms`, `complete`) eklenir; `:orders` vardiyadayken toplar (`OrderFlow` → `DeliveryCore.addEnergy`). `NONE` adım üretmez.
- Tek telefon: hepsi aynı süreçte, bugünkü gibi.
- 2 telefon (A8): saniyelik veri Receiver'dan çıkmaz (plan 4.4) → Provider'daki `addEnergy` beslenemez. Öneri (A8.1 kesinleştirir): Receiver, `:link` üstünden gelen `OrderEvents` (uzak) ile aynı atamayı yapar ve sipariş kapanınca sipariş enerjisini (`wh`, `inWh`, `coveredMs`, `partialMs`, `shared`) tek mesajla Provider'a yollar; bacak/bekleme blokları `EnergyRequest`/`EnergyResult` ile (plan 4.4). Bu A5'i engellemez (14, R-S5).

### 12.3 Roller ve bağlantı sahibi (S11)

- `:scooter` BLE'yi yalnız **tek telefon** modunda ya da **Receiver** rolünde açar; Provider rolünde vardiya başı `open` `skip role_provider` yazar, Scooter sekmesi "Scooter Receiver telefonuna bağlı" der (canlı değerler `BatterySummary` ile).
- Receiver'da vardiya bağlantısı: vardiya durumu `:link`'ten gelir (A8); bağlantı kopukken Receiver'da Scooter sekmesi ve `auto_live` ile bağlantı açılabilir (bugünkü Sürüş Paneli davranışı). Kesin kural A8.1'de.

### 12.4 Metinler

**A7.2 (G3):** bağlantı/kayıt durum metinleri (`NinebotSession`, `BleLink`, `ScooterService`, `ScooterTelemetry`; kayda Türkçe yazılır) ekrana giderken `logic/scooter` `ScooterStatusText.localize` ile AZ olur: servis bildirimi, Scooter sekmesinin uyarı satırı (`ScooterPanel.status`), Scooter ayarları → Durum; `ScooterStatusTextTest` kaynak taramasıyla yeni Türkçe metnin çevrilmeden kalmasını yakalar. TR/AZ ikili metinler (`ScooterLink`, `ScooterReconnect` bildirimleri) aynen; Sürüş Paneli metinleri yalnız TR (bugün) → AZ karşılıkları A5.4'te eklenir (G3 dil kuralı, `scooter_language_section_test`). Ekranda "Rawstats" → "WEEM" (A3.1 kuralı); "Sürüş Paneli" adı Scooter sekmesinin başlığında kalabilir (A5.4 kararı); kayıt içi dizgiler (`app: "Sürüş Paneli"`, `source: "surus_paneli"`, `NinebotLogger-<ms>.zip`) değişmez.

### 12.5 Kesişen kurallar

- **Salt okuma** (2.6) + kaynak taraması testi.
- **Ağsız (öneri, kullanıcı kuralı değil):** `:scooter`'ın ağa ihtiyacı yok (BLE dışında iletişim yok); ağ sınıfı kullanmadığı `NoNetworkTest` benzeri bir kaynak taramasıyla korunabilir (A5.2; `CAPTURE.md` 7.3 yöntemi).
- **ICU:** `NinebotMetrics.exportable` deseni `bms_[0-9a-f]{2}_(…)` ICU güvenli; bütün desenler `RegexPortabilityTest`'e girer.
- **Anahtar gizliliği:** anahtar baytları kullanımdan sonra sıfırlanır; anahtar hiçbir zaman olay/ZIP'e yazılmaz (yalnız tam teşhis ZIP'indeki tel kayıtlarından çıkarılabilir; uyarı metni aynı).
- **Müşteri verisi:** scooter tarafına sipariş kimliği (`tripId`) dışında bir şey gitmez; `trip.label` = `tripId` (bugünkü gibi).

## 13. Test eşlemesi (A5.2 bunu izler)

### 13.1 Sürüş Paneli suitleri (NT, main() tabanlı; 14 suit, **324**)

Sayılar: **satır** = `grep -c 'check('` (envanterdeki 324; her dosyanın `check` tanım satırı dahil) · **çağrı** = `check(` çağrı yeri (toplam 336; bir satırda birden çok olabilir) · **çalışma** = suitin bastığı geçen denetim sayısı (döngüler dahil; 17 Ekim, JDK 21, `bash ninebot-logger/tests/run.sh`, hepsi yeşil).

| Suit | Satır | Çağrı | Çalışma | Ne sınar | Kullandığı sınıflar | WEEM hedefi · yeşil |
|---|---|---|---|---|---|---|
| `TimingTest` | 10 | 9 | 9 | `ageMs`, `retryMs` dizisi, `hex`/`unhex` | `Timing` | `logic/scooter` · A5.2 ✓ |
| `ProtocolTest` | 33 | 55 | 408 | şifreleme vektörleri, sağlama, sayaç/tekrar/düşürme reddi, PRE_COMM→AUTH→READING, okuma koruması, kayıtlı anahtarla AUTH, PRE_COMM 3 deneme, sweep çözme, birleştirme sınırları | `NinebotCrypto`, `NinebotFrames`, `NinebotMetrics`, `NinebotMode`, `NinebotSession`, `Timing` | A5.2 ✓ |
| `LinkTest` | 23 | 23 | 33 | GATT sırası, yinelenen/sırasız geri çağrılar (ör. Android 17'de keşiften önce gelen MTU) | `NinebotLink` | A5.2 ✓ |
| `PairFlowTest` | 26 | 26 | 37 | eşleştirme: aday, yanlış yön, veri yoklaması, tam pencere, AUTH'ta yanlış kaynak terfi ettirmez | `NinebotCrypto`, `NinebotFrames`, `NinebotMetrics`, `NinebotMode`, `NinebotSession`, `SessionKey` | A5.2 ✓ |
| `SweepTest` | 22 | 21 | 24 | sweep aralığı doğrulaması, V×A gücü | `NinebotMetrics` | A5.2 ✓ |
| `PowerCadenceTest` | 23 | 22 | 49 | fast şerit tazeliği, güç girdilerinin yaşı | `NinebotCrypto`, `NinebotMetrics`, `NinebotSession` | A5.2 ✓ |
| `SpeedCadenceTest` | 23 | 22 | 61 | 70 ms yanıtlı sahaya benzer akış, harici paket `0x06`; hız aralığı (en büyük boşluk 300/240 ms), arka plan/ön plan istek sayısı (704/792), GPS temposu | `NinebotCrypto`, `NinebotGpsPolicy`, `NinebotMetrics`, `NinebotSession` | A5.2 ✓ |
| `BatteryProbeTest` | 64 | 64 | 854 | yoklama, aday/onay/düşme, D12 sessiz paket, `PackPresence` | `NinebotCrypto`, `NinebotDashboard`, `NinebotMetrics`, `NinebotSession`, `PackPresence` | A5.2 ✓ (`NinebotDashboard`, `PackPresence` bu görevde yazıldı) |
| `DashboardTest` | 15 | 14 | 14 | kare tazeliği, oturum en yüksek hızı, 7 Ekim saha değerleri, biçimler | `NinebotDashboard`, `NinebotMetrics` | A5.2 ✓ |
| `EventQueueTest` | 7 | 6 | 13 | sınırlı kuyruk, reddedilen gönderim | `NinebotEventQueue` | A5.2 ✓ (plan A5.3; 13.4) |
| `LogPolicyTest` | 14 | 13 | (basmaz) | normal/ayrıntılı sınırlar, tavan, aşırı yükten sonra GPS | `NinebotEventQueue`, `NinebotLogPolicy` | A5.2 ✓ (plan A5.3; 13.4) |
| `EnergyTest` | 21 | 20 | 20 | enerji bloğu anahtarları, işaret, boşluk, harici bayraklar, D10 | `NinebotMetrics`, `ScooterEnergy` | A5.2 ✓ (plan A5.3; 13.4) |
| `TripMeterTest` | 20 | 19 | 19 | sayaç (10 dk 18 km/s −400 W + 5 dk duruş), D10 | `ScooterEnergy`, `TripMeter` | A5.2 ✓ (plan A5.3; 13.4) |
| `FloatingTest` | 23 | 22 | 22 | alan seçimi (en çok 4), bar boyları (Pixel 10 Pro 1280 × 2856) | `FloatGeometry`, `NinebotDashboard`, `PanelField` | A5.2 ✓ (plan A5.4; 13.4) |

Çeviri kuralı: her suit bir Kotlin test sınıfı; her `check(koşul, etiket)` bir doğrulama (etiket mesajı aynı); döngüdeki denetimler döngüde kalır. **Kabul:** suit başına çağrı yeri sayısı (yukarıdaki "Çağrı") ve çalışma sayısı (basılıyorsa) aynı; sayıyı düşüren çeviri sapmadır ve `TEST_ENVANTERI`'ne yazılır. JUnit sarmalayıcı (`SurusPaneliJvmTest`, 14 `@Test`) gerekmez: suitler doğrudan JUnit. `ReferenceProbe.java` (yardımcı) çevrilir ya da gerektiği yere gömülür.

Cihaz ve Python testleri: `KeyVaultInstrument` (9 kasa denetimi + `LogExportTest`, bildirim, depolama politikası, yazıcı kuyruğu, panel önizleme; `upgrade_seed`) → `:scooter` `androidTest` (A5.2; CI emülatörde, kullanıcı cihazında değil); `LogExportTest` gizlilik denetimleri → JVM (`LogExport` saf). `reference_parity.py`, `laptop_client_test.py`, `upgrade_smoke.py`, `emulator_smoke.py`: CI dışı, çevrilmez; `reference_parity.py`'nin sabit vektörleri `ProtocolTest`'te zaten var.

### 13.2 Rawstats scooter testleri (RT)

| Dosya | Test | WEEM |
|---|---|---|
| `ScooterReconnectM23Test` | 4 | `logic/scooter` aynen · A5.2 ✓ |
| `ScooterBridgeTest` | 6 | `logic/scooter` `ScooterBridgeImpl` çekirdeği + `EnergyBlock` (5 test); `dumpForSchemaCheck` (`KL_ROWS_OUT` ortam değişkeniyle şema dökümü) korunur · A5.3 |
| `ScooterSamplesTest` | 5 | `logic/scooter` (4); `fileIsBackedUpAndExported` `BackupFiles.logNames`/`AnalysisExport` ifadeleri `:core`/A4.3'e · A5.3 |
| `ScooterFeedTest` | 5 | 3 test `ScooterLiveMapper`'a uyarlanır (alanlar, bağlantı yokken null — sonsuz değer null olur —, sipariş/konum yok; bugünkü yükte `trip` hep nesne, WEEM `ScooterLive.trip` bağlantı yokken `null` olabilir: test buna göre); `pathMatchesYolGoReader`, `scooterReaderStillNeedsAllGates` **çevrilmez** (sağlayıcı kalkar, S7) · A5.3 |
| `ScooterOrderEnergyTest` | 7 | `:economics`/`:orders` (envanter: `:economics`); `step` kısmı `logic/scooter` `ScooterSamples` ile · A5.3 (adım akışı 12.2) |
| `ShiftDistanceD8Test` | 4 | `:core` (A2.2'de çevrildi) |
| `AutoStartNoticeTest` | 1 | scooter kısmı (5102) A5.3'te kaynak taraması |

Toplam `:scooter` hedefi (envanter): 14 suit (324) + `ScooterBridgeTest` 6 + `ScooterFeedTest` 5 + `ScooterReconnectM23Test` 4 + `ScooterSamplesTest` 5 = **344**; bunun 2'si çevrilmez (S7), 1'i (`fileIsBackedUpAndExported`) `:core`'a gider.

### 13.3 WEEM ekleri (eski testte yok)

- Salt okuma kaynak taraması (2.6).
- `SessionKey.derive` Kotlin eşitliği: Java `Random` ve `int` kaydırma (2.7) için `ProtocolTest`/`PairFlowTest` vektörleri yeter; ek olarak `kotlin.random` kullanılmadığı taranır.
- `VERI_SOZLESMESI` adları: `pairing-v1`, `NinebotLogger.Pairing.v1`, `ninebot-logger.db`, `logger`, `economics-scooter.jsonl` sabitlerinin değeri test edilir (A2'deki kimlik testleri gibi).

### 13.4 A5.2 taşıma kaydı (17 Ekim 2026)

- **Suitler:** 14 suitin hepsi `logic/scooter/src/test` altında, her biri bir JUnit sınıfı (tek `@Test`, Java `main` sırası). `check(koşul, etiket)` ortak `NinebotSuite`'te (etiket aynı, geçen denetim sayılır); her suit sonunda çalışma sayısı eskiyle aynı: Timing 9, Protocol 408, Link 33, PairFlow 37, Sweep 24, PowerCadence 49, SpeedCadence 61, BatteryProbe 854, Dashboard 14, EventQueue 13, Energy 20, TripMeter 19, Floating 22; LogPolicy eskide basılmaz, döngülerden 20.015. `SuiteCountTest` çağrı yerlerini eski suitlerle karşılaştırır (toplam **336**). `ProtocolTest.Port` paylaşılır; eski statik sayaç gibi başka suitin sayısına girmez (`NinebotSuite.ASSERT_ONLY`). `ReferenceProbe` Kotlin'e çevrildi (suit değil).
- **Rawstats:** `ScooterReconnectM23Test` 4 test aynen (A5.2). `ScooterBridgeTest`, `ScooterFeedTest`, `ScooterSamplesTest`, `ScooterOrderEnergyTest` A5.3'te.
- **Python referansı (CI dışı, elle):** `tools/scooter-reference-parity.sh <kurye_nav klonu> [pycryptodome dizini]` — `reference_parity.py`'yi Java yerine Kotlin `ReferenceProbe` ile çalıştırır. 17 Ekim: **320/320** vektör (64 tam el sıkışma; `nb_crypto.py` SHA sabit) bayt bayt aynı; Java taban çizgisi de 320/320.
- **WEEM ekleri (testler):** `PairingVaultTest` (`KeyVaultInstrument` 9 kasa denetimi + bozuk anahtar 3 + göç fikstürü, JVM'de yazılım anahtarıyla; `pairing-v1` biçimi), `ScooterGuardTest` (salt okuma: oturum yalnız `0x01` ve kart 4 `0x5b/0x5c/0x5d` gönderir, çerçeve yalnız korumanın arkasında, Android'de GATT yazımı yalnız pompada ve CCCD aboneliğinde; ağ sınıfı yok; `kotlin.random` yok; oturum not türleri = 11.2 listesi), `LinkRulesTest` (`LinkRetry`, `PairWindow`, `GattPlan`).
- **Kod farkları (davranış aynı):** Java alıcıları Kotlin özelliği (`stage`, `probing`, `profile`, `metrics`, `quietPacks`, `phase`, `size`, `omitted`, `announced`); kurucu aşırı yüklemeleri varsayılan parametre; `Read` kimlik eşitliğinde (oturum kümeleri ve `indexOf` buna dayanır); Java `String.trim()` → `trim { it <= ' ' }`, `Math.round` (NaN → 0) ve `Objects.equals` (`sameValues`) aynen; Java alan başlatma sırası için `NinebotSession.init` en sonda. R-S1 açıklaması 150 ms olarak düzeltildi.
- **Belge düzeltmeleri:** 2.1 "ninebot" son **7** bayt; 4.1 değer 44 bayt → **60** karakter.
- **Doğrulanmadı:** cihazda ve scooter'da hiçbir şey (GATT, Keystore, Rawstats'ın üstüne kurulumda `pairing-v1` okunması); A5.5'te.

### 13.5 A5.3 taşıma kaydı (17 Ekim 2026)

- **Testler (Rawstats → Kotlin, aynı girdi → aynı çıktı):** `ScooterSamplesTest` (4 + `dumpForSchemaCheck`; `fileIsBackedUpAndExported` yedek/dışa aktarma ifadeleri `:core` `BackupFiles` testine gider, A4.3 — burada dosya adı ve "konum yok") + `ScooterOrderEnergyTest`'in adım payı (`step` 2 test, D10 adımı); `ScooterBridgeTest` 6 (`ScooterBridgeImpl` + `EnergyShape`; `fromLegs`/`fromIdle` bacak satırları `:economics`'te A4.1, pencere denetimi köprü çağrılarıyla); `ScooterLiveMapperTest` (eski `ScooterFeedTest`'in 3 testi + 1; sağlayıcıya ait 2 test çevrilmedi, S7); `logic/orders` `ScooterOrderEnergyTest` 5 (sipariş tarafı: `addEnergy`/`closeEnergy`, kayıt, kart satırı, D10; `:orders` `:scooter`'ı tanımadığı için adımlar değer olarak verilir). Eski androidTest `LogExportTest` JVM'de (`LogExportTest`: 12 gizlilik denetimi + ZIP düzeni 3 test). WEEM ekleri: `ScooterLinkRulesTest` 7, `ScooterTelemetryTest` 5, `ScooterContractTest` 5 (adlar, servis türü, bildirim numaraları, dinleyici sırası, modül sınırı).
- **Saf (`logic/scooter`):** `ScooterSamples` (`step`, `stepOf`, `row`), `ScooterBridgeImpl` (+ `Source`, `EnergyShape`: enerji bloğunu şemanın tam şekline sokar; `EnergySegments.energy/energyFrom` yerine, A4.1 aynı anahtar kümesini kullanır), `ScooterLiveMapper`, `LogExport` (izin listesi + manifest + ZIP düzeni; depo `Source` arayüzü), `ScooterLinkRules` (+ `ScooterText` TR/AZ), `ScooterTelemetry` (`sample` olayının `scooter_telemetry` alanı, 1 Hz örnek, ayar metni). `logic/core`: `EnergyStep` ve `ScooterBridge.steps` (12.2'de öngörülen eksik arayüz; `NONE` adım üretmez).
- **Android (`:scooter`):** `ScooterService` (eski `RecordingService`; `START_NOT_STICKY`; tür canlıda `connectedDevice`, kayıtta `location|connectedDevice`), `LogStore` (`ninebot-logger.db`, `LogExport.Source` verir), `PairAttention` (bildirim 8), `Scooter` (süreç durumu, `bridge`, `live`, `steps`, `startLive/record/stop`), `ScooterShiftLink` (`ShiftMachine.Hooks`: `onRunning` → `open`, `onFix` → M23, `onEnd` → kapat, `odometer()` D8; 1 Hz örnek → `economics-scooter.jsonl` + enerji adımı), `BleLink.telemetry/sample/measurements`. `:app` `WeemApplication`: dinleyici `orders`'tan sonra, `screen_reading`'den önce; `Orders.bindScooter(this, Scooter.bridge)`. `:orders` `Orders.scooter` (`OrderFlow.Env.scooter`) ve `steps` toplayıcısı (`OrderFlow.scooterEnergy`, vardiya kapalıysa yok sayar). Vardiya kartı (`ShiftCard`) scooter durum satırı + "Scooter'a şimdi bağlan" + "Bluetooth izni ver".
- **Kod farkları:** (1) `orderAccepted/orderDone` olay anını (`atWallMs`) sayaca verir (Rawstats `System.currentTimeMillis()`). (2) `ScooterBridge.energy(from, to)` açık pencereyi `to ≤ from` ile ifade eder. (3) `role_provider` nedeni `open` içinde ilk sırada (`auto_off` kontrolünden sonra); Provider rolü `ScooterShiftLink.providerRole` ile A8'de takılır, tek telefonda hep false. (4) Rawstats'ın YolGo yayını (`publish`, `FeedChanges`) yok (S7); `live` her saniye tikte ve bağlantı değişince güncellenir. (5) Bildirim simgeleri sistem simgesi (`stat_sys_data_bluetooth`); Sürüş Paneli'nin kendi simgesi A5.4. (6) Bildirim ve panel metinleri Sürüş Paneli'nde TR; AZ A5.4. (7) `LogExport.VERSION` "0.4.3" (kayıt biçimi sürümü, WEEM sürümü değil).
- **Doğrulanmadı (cihazda/scooter'da hiçbiri):** servis ömrü (ön plan türü, arka plandan başlatma reddi), GATT, kayıt/GPS, ZIP'in telefondan çıkarılması (paylaşma arayüzü A5.4), vardiya bağı akışı, M23, enerji adımının siparişe düşmesi. A5.5'te yedek telefonda.

### 13.6 A5.4 taşıma kaydı (18 Ekim 2026)

- **Saf (`logic/scooter`):** `ScooterUiText` (sekme, yüzen panel, ayar, tanı, bilgi sayfası ve servis bildirimi metinleri; TR Sürüş Paneli'nden aynen, "Rawstats" → "WEEM", AZ yeni, F-S4), `ScooterPanel` (durum satırı ve noktası `syncToolbar`/`secondPack`, eylem çubuğu kuralı, 5 sn bayatlık, `tripLine`, kadran eşikleri, erişilebilirlik metni, `ExportChoice` 4 seçenek + uyarı + sonuç metni, `ScanList` canlı tarama listesi: sıra, ≥6 dB, etiket), `FloatRules` (400 ms, 80 ms, ilk konum, kenara kayma, sürükleme sınırı, "×" yakınlığı, okların yeri, ekran sayacı, PiP yedeği), `ScooterSettings` (6 boyut kaydırıcısı, alt satırlar, anahtar durumu, 16 bayt hex, alan seçimi en çok 4); `PanelField.title/caption/unit(lang)` (TR aynen). Test: `ScooterUiTest` 23 (WEEM eki; NB arayüzünün testi yoktu).
- **Android (`:scooter`):** `DashboardView` (NB `DashboardView` çizimi satır satır: gösterge, 2 × 2 kadran, sayaç kadranları, yatay düzen, PiP ızgarası; tema ve dil dışarıdan), `FloatPainter`, `FloatingPanel` (düz `View` + `WindowManager`, R-S11: Compose yaşam döngüsü gerekmez; sürükle, kenara kay, dokun → oklar, hızlı fırlat → bar, dışarı dokun → kapan, çift dokun → Scooter ayarları, uzun bas → canlı panel, "×" → `floating=false`; `Scooter.Viewers.ball`; dikdörtgeni `OwnOverlays`'e kayıtlı), `BleScan` (4 sn, filtresiz, düşük gecikme), `Scooter.state/select/startRecording/autoLive/export/cleanOld`, `OPEN_EXTRA`. Bildirim 7/8 başlık, eylem ve kanal adları ile ikinci batarya duyurusu dile göre (`Store.lang`).
- **`:app`:** `ScooterTab` (başlık + durum noktası/satırı, eylem çubuğu ayarlar/eşleştir/ara/başlat/durdur/ZIP, eşleştirme uyarısı, `DashboardView` `AndroidView` içinde; dokun → tam ekran: başlık, çubuk, alt menü ve sistem çubukları gizlenir, geri çıkar; görünürken `Viewers.dashboard`; `keep_screen_on`; açılışta `auto_live`; PiP yedeği), `ScooterSettingsScreen` (durum paneli `summaryWatched`, Panel: `auto_live`, vardiyada otomatik bağlantı `scooter.autoConnect`, `floating` + izin penceresi, değerler, sıra/top, boyut; Kayıt ve depolama: 30 gün temizliği; Sistem: `keep_screen_on`, uygulama ve GPS ayarları; Bağlantı: bilgi sayfası, `battery_probe`, gelişmiş anahtar; **Tanı**: `detailed_diagnostics`, `sweep`, tam ZIP ipucu), `ScooterPip`; `MainActivity` `onStart/onStop` yüzen top sayacı, `onUserLeaveHint` (<31), `onPictureInPictureModeChanged`, `OPEN_EXTRA`; manifest `supportsPictureInPicture`.
- **Düzeltme:** `ScooterService` reddedilen ön plan başlatmasında (`active`, bakım, izin yok) `stopSelf()` yerine `refuseStart()`: önce `startForeground` (Android aksi hâlde uygulamayı kapatır); `CrashGuardTest` A5.3 birleşmesinden beri `main`'de kırmızıydı, şimdi `ScooterService.startIntent` çağrılarını ve bu yolu da denetler.
- **Bilerek farklar:** F-S9…F-S14 (14).
- **Doğrulanmadı (cihazda hiçbiri):** tarama, eşleştirme akışı, yüzen topun dokunuşları ve WEEM üstünde gizlenmesi, PiP, tam ekran, ZIP'in İndirilenler/WEEM'e yazılıp paylaşılması, temizlik, anahtar girişi. A5.5'te yedek telefonda.

## 14. Açık noktalar, riskler ve bilerek farklar

| No | Konu | Durum / öneri |
|---|---|---|
| R-S1 | `VISIBLE_SPEED_MS = 150`, açıklama "180 ms" (`NinebotSession.java:31-36`) | Kod geçerli (testler 150'ye göre); WEEM 150 + açıklama düzeltildi (A5.2) |
| R-S2 | Register haritası topluluk kaynaklı (`device_verified=false`); hız, `0x68`, `0x6a` "aday"; resmî W kaynağı bilinmiyor; `0x8a` anlamsız | Aynen; arayüzde "aday/doğrulanmamış" kalır. Saha doğrulaması A5.5 |
| R-S3 | `fail()` durumları (PRE_COMM 3 yanıtsız, AUTH başarısız, `NEEDS_KEY`, ad/seri, bildirim kanalı yok) GATT'ı açık ve **boşta** bırakır; yeniden deneme yok; M23 uyarmaz (hiç kare gelmedi) | Davranış şartnamesi gereği aynen; **kullanıcı sorusu S18** (vardiya bağlantısında yeniden deneme). S11 ile Pixel'deki resmî uygulama aynı anda bağlıysa Poco'da bu duruma düşülebilir |
| R-S4 | Eşleştirme penceresi 300 sn bağlantılar arası tek; pencere dolunca kayıt durdurulup yeniden başlatılmalı | Aynen |
| R-S5 | 2 telefonda sipariş enerjisi (12.2): saniyelik adımlar Receiver'dan çıkmaz | A8.1 kararı; tek telefonda bugünkü gibi. A5'i engellemez |
| R-S6 | Receiver'da vardiya bağlantısı vardiya durumunu `:link`'ten alır; `:link` kopukken scooter bağlantısı açılmaz | A8.1; o zamana kadar tek telefon kuralı |
| R-S7 | `SessionKey.derive` Java `int` kaydırma ve `java.util.Random`; Kotlin'de yanlış tür (Long kaydırma, `kotlin.random`) sessizce başka anahtar üretir | Yalnız testlerde kullanılır; vektör testleri yakalar (13.3) |
| R-S8 | Sayaç 16 bit (`tx ≥ 65535` → "Şifreli oturum yenileniyor", yeniden bağlanma). Yanıt sayacı `tx`'i ileri çeker (`tx = max(tx, c)`); sahada sayaç her alışverişte 2 artıyor (NB üstü `VALIDATION.md:91` "PAIR RX 6,8,…,28") → ~32.700 okuma; hızlı tempoda (~10–13 okuma/sn) **tahminen 40–60 dk'da bir** oturum düşer | Tahmin, sahada doğrulanmadı. Bugünkü davranış, aynen; A5.5'te `ble_state` "Şifreli oturum yenileniyor" sıklığı ölçülür (D12'deki günde 240–290 yeniden bağlanmanın bir kısmı bu olabilir) |
| R-S9 | Keystore anahtarı uygulama UID'sine bağlı: WEEM aynı `applicationId` + imza ile kurulmazsa eşleşme kaybolur | Plan Bölüm 2 (kimlik değişmez); A5.2 cihaz testinde `upgrade_seed` yolu |
| R-S10 | Bildirim 7 ve 8 küçük sayılar (NB), Rawstats'la aynı ad alanında | `CORE.md` 3.3 tablosunda; aynen |
| R-S11 | Yüzen panel `SYSTEM_ALERT_WINDOW` ister; Compose'a taşıma (`ComposeView` + `WindowManager`) yaşam döngüsü sahibi gerektirir | A5.4 teknik notu |
| R-S12 | `battery_lost` olayı izin listesinde ama yazılmıyor | Aynen (eski veritabanları) |
| F-S1 | Ekranda "Rawstats" → "WEEM" (bildirim 7 başlığı, panel "Rawstats vardiyası" metinleri, InfoSheet bölümü) | Bilerek fark (A3.1 kuralı); kayıt dizgileri değişmez |
| F-S2 | `ScooterLive.source` `weem_surus_paneli` (bugün `rawstats_surus_paneli`), sağlayıcı yok | A2.5'te kararlaştı (S7) |
| F-S3 | `scooter_link` `skip` nedeni `role_provider` (yeni) | S11 |
| F-S4 | Sürüş Paneli metinlerine AZ karşılığı | G3 kuralı (A5.4) |
| F-S5 | `KeyVault` ikiye ayrıldı: kural ve biçim saf `PairingVault` (+ `PairingStore`), Android `KeyVault` yalnız tercih dosyası ve Keystore ana anahtarı. Base64 `java.util` (yazım `android.util` `NO_WRAP` ile aynı metin; okuma MIME çözücüsü, Android'inki gibi yabancı karakteri atlar; her durumda GCM doğrulaması). Ana anahtar üretimi `@Synchronized` (eskide iki iş parçacığı ilk eşleştirmede iki anahtar üretebilirdi) | A5.2; biçim ve adlar aynı (`PairingVaultTest`) |
| F-S6 | `KeyVaultInstrument` `androidTest` olarak eklenmedi: CI'da emülatör yok, `androidTest` bağımlılığı yok. Kasa denetimleri JVM'de (`PairingVaultTest`); Keystore yolu elle, cihazda (A5.5) | A5.2 kararı; emülatör işi kullanıcı/CI kararı |
| F-S7 | `BleCapture` → `BleLink` + saf `LinkRetry`/`PairWindow`/`GattPlan`; `RecordingService.dashboardVisible`/`FloatingPanel.shown` yerine `BleLink.Viewers` (A5.3/A5.4 doldurur); `telemetry`/`sample`/`measurements` A5.3/A5.4'te `latestValues`'tan | A5.2; GATT sırası, olay adları ve yükleri aynı |
| F-S8 | Plan A5.3/A5.4'e koymuştu: `EventQueueTest`, `LogPolicyTest`, `EnergyTest`, `TripMeterTest`, `FloatingTest` ve saf sınıfları A5.2'de çevrildi (kullanıcı kapsamı "14 suit / 324 denetim"; `main` yeşil kalsın diye kırmızı iskelet bırakılmadı) | A5.3/A5.4 bunları yeniden yazmaz |
| F-S9 | ZIP: `ACTION_CREATE_DOCUMENT` dosya seçici yerine İndirilenler/WEEM'e yazılır ve paylaşma seçicisi açılır (A4.3 yedekleriyle aynı yol); ad, biçim ve içerik aynı | A5.4 |
| F-S10 | Canlı gösterge Compose `Canvas` değil, NB çizimi taşınan `DashboardView` (`AndroidView` içinde); sekmenin geri kalanı Compose | A5.4 (çizim birebir) |
| F-S11 | Bilgi sayfası "Canlı panel ve küçük pencere": eski metin 0.22.0 öncesi hareketleri (kaydır → aç, dokun → uygulama) anlatıyordu; bugünkü hareketlerle düzeltildi. "Rawstats vardiyası" bölümü "WEEM vardiyası"; "Kayıtlar" bölümüne İndirilenler/WEEM | A5.4 |
| F-S12 | Ayarlara "Vardiyada scooter'a otomatik bağlan" (`kuryelog` `scooter.autoConnect`, RS'de Rawstats Ayarlar → Scooter'daydı); "Tanı" bölümü (ayrıntılı teşhis, sweep; NB'de "Kayıt ve depolama" ve "Bağlantı" altındaydı) | A5.4 (12.1 yerleşimi) |
| F-S13 | Göstergenin erişilebilirlik dokunma eylemi adlandırılmaz (`NoGestureTest` WEEM kaynağında `AccessibilityNodeInfo.ACTION_*` yasaklar); ekran okuyucu metni aynı. Başlıktaki "RAW Stats'a geç" düğmesi yok (sekme zaten WEEM'in içinde) | A5.4 |
| F-S14 | Ekranda değil kayıtta kalan dizgiler TR: tanı özeti (`Scooter.summary`), BLE durum satırları, `PairAttention` denetim metni, `device_name` etiketi ("İsimsiz BLE cihazı") | A5.4 (veri) |
| S18 | **Kapandı (S35, A10.4f, 22 Ekim): (b).** `fail()` sonrası boşta kalan bağlantı (G3 yanıtsız, AUTH başarısız, ad/seri, bildirim kanalı yok) → "Scooter bağlantısı koptu · 60 sn sonra yeniden denenecek" bildirimi (4902, kanal `scooter_link`, "Şimdi dene" düğmesi → `ScooterService.RETRY`); kullanıcı durdurmaz ya da denemezse 60 sn sonra GATT kapanıp yeni bağlantı (`LinkRetry.idleRetryMs`, `Timing.IDLE_RETRY_MS`, olay `ble_idle` / `ble_state idle_retry`); okuma gelince bildirim kalkar. `NEEDS_KEY` ve dolmuş eşleştirme penceresi beklemez (eşleştirme gerekir). Normal kopmada (`failed()`) 1-2-4-8-15 sn yeniden bağlanma aynen. Ölçülmeyen süre sipariş kartında "veri eksik N dk", gün raporunda "N siparişte scooter verisi eksik". Cihazda denenmedi |
