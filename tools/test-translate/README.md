# Test çeviri araçları (A3.2)

Rawstats testlerini (`kurye_nav` rawstats @ `6388c04`, salt okunur; `RS_TEST=…/app/src/test/java/com/kuryelog/app/`) WEEM modüllerine bölüp çeviren ve eksik üretim sınıflarının **iskelet imzalarını** (`TODO("A3.x")`) üreten Python betikleri. A3.2'de yazıldı; A3.3 (para, platform, rozet, balon, gün sayfası testleri) aynı hattı kullanır. Python 3, ek paket yok.

| Dosya | İş |
|---|---|
| `cfg.py` | `CAPTURE.md` 8'e göre hangi test `logic/capture`'a gider (`CAP`), hangisi hiçbir yere (`NOT_ORDERS`) |
| `tlib.py` | Kotlin test dosyasını üyelere böler (başlık, yardımcılar, `@Test`'ler) ve yazar |
| `gen_capture.py`, `gen_orders.py` | `logic/capture` ve `logic/orders` test dosyalarını üretir (paket, içe aktarmalar, saha yükleyici `SahaFrames`, `CustomerMatch.sameName` → `NameMatch`) |
| `patch_orders.py` | `gen_orders.py` sonrası elle uyarlamalar (ekonomi kaydedicisi çıkarıldı, `TripStages` tiplerine yazıldı, `LogTime.ts`, `OwnLines`) |
| `stubber.py`, `types.json`, `regen_stubs.sh`, `fixup.py` | Rawstats üretim dosyasından iskelet imza üretir (private üyeler atılır; türü çıkarılamayanlar `types.json`'dan); `fixup.py` sonradan düzeltir |

Kullanım (kök `WEEM_ROOT`, varsayılan `/home/user/Weem`): `python3 tools/test-translate/gen_capture.py` · `python3 tools/test-translate/gen_orders.py && python3 tools/test-translate/patch_orders.py` · `tools/test-translate/regen_stubs.sh DeliveryCore Readiness … && python3 tools/test-translate/fixup.py`. **Dikkat:** `gen_*` üretilmiş test dosyalarının üzerine yazar; A3.2'den sonra elle yapılan değişiklikler (ör. `ScreenReadingE7Test` ve `IcuRegexTest` kaynak taramaları elle yazıldı) için betikleri çalıştırmadan önce `git diff` ile bak. Stub üretimi `logic/orders/src/main`'in iskelet dosyalarının üzerine yazar: A3.5'ten sonra **çalıştırma**.
