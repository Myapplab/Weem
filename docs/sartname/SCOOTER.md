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
| Şifreleme | `AES/GCM/NoPadding`, IV sağlayıcıdan (12 bayt), etiket 128 bit, **AAD = cihaz adresi UTF-8**; değer = `Base64.NO_WRAP(IV ‖ şifreli+etiket)` (16 bayt anahtar → 44 karakter) (16, 22) |
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
- Ayarlar ekranı aynı metni saniyede bir yeniler (`refreshPairPrompt`, NB `MainActivity.java:18`).

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

Olay politikası (`NinebotLogPolicy.java:9-21`): sık türler `ble_raw`, `ble_write`, `ble_write_result`, `protocol_tx` (READING'deki), `ble_measurement` yalnız "Ayrıntılı teşhis" (`detailed_diagnostics`) açıkken ve kaydın ilk **10 dk**'sında (`DETAIL_MS`), en çok **10.000**; diğer BLE olayları ayrı **10.000** bütçe; el sıkışma TX'i (`stage=READING` içermeyen `protocol_tx`) `handshake_tx` bütçesine sayılır (`RecordingService.java:137-139`); atlananlar `omitted`. Yazma kuyruğu (`NinebotEventQueue.java:8`): sınır **2.048** (`RecordingService.java:56`); dolu ise BLE olayı atılır ve sayılır; GPS ve örnek satırları kuyruğu atlar (doğrudan yazıcı). Canlı kipte hiçbir olay yazılmaz.

### 7.4 Dışa aktarma ZIP (NB `LogStore.java:45-75`, `LogExport.java:7-34`; `DashboardActivity.java:185-200`)

- Seçenekler: "Son kayıt • konumsuz" (varsayılan), "Son kayıt • GPS rotası dahil", "Tüm kayıtlar • GPS rotaları dahil", "Tam teşhis • son kayıt • HASSAS" (ek uyarı: "Bu dosyadan scooter eşleştirme anahtarı çıkarılabilir…"); kayıt sırasında yapılmaz. Dosya `ACTION_CREATE_DOCUMENT`, ad `NinebotLogger-<ms>.zip`.
- İçerik: `manifest.json` (`schema: 1`, `app: "Sürüş Paneli"`, `version`, `decoder_available: true`, `device_verified: false`, `utc_export_ms`, `wire_transcript_sensitive`, `full_diagnostics`, `location_included`, `latest_session_only`, `note`), `sessions.jsonl`, `events.jsonl` (satır = sütunlar; `device` filtreli ZIP'te `null`).
- Filtreli (izin listesi, eski veritabanları dahil): `sample` (`elapsed_ms`, `gps_fresh`, `gps_request_interval_ms`, `gps`, `scooter_telemetry`: özet alanlar + `exportable` alanlar), `gps_fix` (konum seçilmediyse `lat`, `lon`, `altitude_m`, `bearing_deg`, `bearing_accuracy_deg` çıkar), `log_backpressure`/`log_summary`, `session_start` (`version`, `sdk`, `decoder_available`, `device_verified`, `sweep`), `gps_cadence`, `battery_config` (`probe`, `known_boards`), `battery_boards`/`battery_probe_*`/`battery_lost`/`battery_quiet`/`battery_back` (`detail`); başka tür **çıkmaz** (ham tel, el sıkışma, serbest metin). `pick` yalnız sayı/bool/dizi/null kopyalar.
- Tam teşhis: bütün yükler, `device` ve konum dahil.

**WEEM:** dosya adı, şema, durum değerleri, olay türleri ve alanları, politika sabitleri, ZIP biçimi ve izin listesi **aynı** (`VERI_SOZLESMESI` 7–8; eski ZIP'leri okuyan araçlar çalışır). `LogExport` ve politikalar `logic/scooter`'da saf (`LogPolicyTest`, `EventQueueTest`, androidTest `LogExportTest` → JVM testine). Dosya adı `NinebotLogger-<ms>.zip` ve `app: "Sürüş Paneli"` korunur (analiz araçları; değiştirmek kullanıcı kararı, 14). GPS kaydı: 2 telefonda yalnız Receiver'da (scooter orada).
