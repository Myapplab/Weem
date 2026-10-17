# SCOOTER — Aşama 5 şartnamesi (`:scooter`: S1–S8, R14)

Durum: A5.1, 17 Ekim 2026 (Claude Opus · high, tek ajan) — yazılıyor; biten bölümler `WEEM_ISLER.md` "Şu an"da. Kaynak: `Myapplab/kurye_nav` — `rawstats` @ `6388c04` (Rawstats 0.25.1; Sürüş Paneli `BuildInfo.VERSION` **0.4.3**); satır numaraları bu commit'te açılıp doğrulandı. Ham bulgu yok (`bulgular/` scooter içermez; `X_protocol.md` yalnız `scooter/v1/live` yükü, P4); bu belge kaynak koddan tam çıkarmadır.

Kısaltmalar: **NB** = `ninebot-logger/app/src/main/java/az/ninebot/logger/` (Sürüş Paneli, Java, Gradle `:surus-paneli`, namespace `az.ninebot.logger`), **NT** = `ninebot-logger/tests/` (main() tabanlı suitler), **NR** = `ninebot-logger/tools/reference/`, **RS** = `app/src/main/java/com/kuryelog/app/`, **RT** = `app/src/test/java/com/kuryelog/app/` (Rawstats). Davranış metni kodun söylediğidir; "WEEM" satırları bu şartnamenin kararıdır.

**Kim kullanır:** A5.2 (protokol, şifreleme, oturum, eşleşme, yeniden bağlanma ve testler: Bölüm 2–5, 13), A5.3 (canlı panel, kayıt, sayaç, harici batarya, enerji: Bölüm 3, 6–9, 11), A5.4 (yüzen panel, tanı, Scooter sekmesi: Bölüm 6, 10, 12), A8 (2 telefon: Bölüm 12). Bölümler: 0 özet · 1 kaynak · 2 S1 protokol · 3 okunan değerler · 4 eşleşme ve anahtar deposu · 5 S6 yeniden bağlanma · 6 S2/S7 panel · 7 S3 kayıt · 8 S4 sayaç ve R14 enerji · 9 S5 harici batarya · 10 S8 tanı · 11 veri sözleşmesi · 12 WEEM yerleşimi · 13 test eşlemesi · 14 açık noktalar ve riskler. Plan bağlamı: `WEEM_PLANI.md` 3 (S1–S8, R14), 4.2–4.4 (`:scooter`, `ScooterBridge`, `ScooterLive`/`BatterySummary`/`Energy*`), 8 (S8 net kazanç, S11 tek bağlantı).

## 0. Özet: WEEM kuralları

_(A5.1 sonunda yazılır.)_

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
| Ninebot özel servisi | `6e400001-0000-0000-006e-696e65626f74` (son 6 bayt ASCII "ninebot") | karakteristikleri `chars`'a alınır ama hiçbiri kullanılmaz (22, 79) |
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
| PRE_COMM | `NinebotCrypto(ad UTF-8, FW, firmwareInput=true)`; kart 4, komut `0x5b`, index 0, veri yok (açık + sağlama, sayaç 0) | 5.000 ms; en çok 3 deneme | Yazma karakteristiği yoksa ya da 3 deneme bittiyse `fail("G3 başlangıç yanıtı yok; yalnız Nordic profile 0 denendi. Bağlantı ZIP'ini gönder")` (73-74). Yanıt `reply(p,4,0x5b,idx,LEN)` ve `p.length≥37`: `challenge = p[7..23)` (16), `serial = p[23..37)` (14); seri baytları 0 ya da 32–126 değilse `fail("G3 cihaz kimliği beklenen biçimde değil")`; not `pre_comm_ok` (`stored_key = idx≠0`); `crypto.start(challenge)` (100-102) |
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
