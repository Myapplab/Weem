# AGENTS.md — WEEM için yapay zekâ kuralları

Claude, Codex ve diğer tüm ajanlar için tek kural dosyası. `CLAUDE.md` bunu otomatik yükler; ayrıca açma.

## Proje

**WEEM** (Work Efficient, Earn More; repo `Myapplab/Weem`) = YolGo (harita, navigasyon) + Rawstats (Wolt/Bolt sipariş ve kazanç kaydı) + Sürüş Paneli (scooter): tek native Android uygulaması, **Kotlin + Jetpack Compose, sıfırdan**.

| Dosya | Ne zaman |
|---|---|
| `docs/WEEM_ISLER.md` | Her oturum başında "Şu an" bölümü; sıradaki iş buradan |
| `docs/WEEM_PLANI.md` | Kapsam, kimlik/veri, mimari, kurallar, yol haritası, kullanıcı kararları; gerektiğinde ilgili bölüm |
| `docs/BEKLEYEN_OZELLIKLER.md` | WEEM 1.0 sonrası özellikler: kullanıcı sormadan yazılmaz |
| `AI_HANDOFF.md` | Yalnız en üstteki 1–2 bölüm |

**Şartname (eski uygulamalar, salt okunur):** `Myapplab/kurye_nav` — `yolgo-test` @ `870ab6e` (YolGo, Flutter) ve `rawstats` @ `6388c04` (Rawstats + Sürüş Paneli). O repoya yazılmaz (kritik düzeltme ve dışa aktarma eki gibi istisnalar yalnız kullanıcı isterse). Erişim: cloud oturumunda `add_repo` ile `Myapplab/kurye_nav`, ya da `git clone --depth 1 -b rawstats https://github.com/Myapplab/kurye_nav /tmp/ref-rawstats` (YolGo için `-b yolgo-test /tmp/ref-yolgo`). Bütün dosyayı okuma: `rg -n` ile yeri bul, yalnız o aralığı aç.

## Birden çok Claude hesabı — süreklilik (ZORUNLU)

Kullanıcı WEEM'i 2–3 Claude hesabından sırayla geliştirir; bir hesabın usage'ı bitince öteki aynı yerden devam eder. Oturum ne zaman kesilirse kesilsin iş kaybolmamalı.

1. **Başlarken:** bu dosya → `docs/WEEM_ISLER.md` "Şu an" → `AI_HANDOFF.md` en üst bölüm → `git log --oneline -10`. "Devam eden" iş varsa önce o.
2. **Üstlen:** görevin durumunu `sürüyor (<tarih saat>)` yap; commit + push. Sonra başla.
3. **Küçük adım, sık push:** her yeşil adımda (testler geçti) commit + push; en geç 30 dakikada bir. Push edilmemiş iş usage bitince kaybolur (bulut kapsayıcı silinir).
4. **`main` her zaman son hâl.** Kullanıcının kalıcı izni (17 Ekim 2026): oturum dalından `main`'e PR açıp birleştirmek ya da doğrudan `main`'e push etmek serbest. Yasak: force-push, geçmişi yeniden yazmak, dal/etiket/Release silmek. Testleri kırmızı kod `main`'e girmez: yarım iş oturum dalında kalır; `WEEM_ISLER.md` → "Devam eden" satırına dal adı, son commit ve sıradaki adım yazılır, bu not `main`'e push edilir.
5. **Her push'ta güncel tut:** `WEEM_ISLER.md` (görev durumu + "Şu an") ve oturum sonunda `AI_HANDOFF.md`.
6. **Aynı anda iki hesap** varsayılan değil. Gerekirse yalnız farklı aşama/modülde, her biri kendi görevini "sürüyor" diye işaretleyerek.
7. **Karar kullanıcının:** kapsam, yeni özellik, bekleyen özellik, plan değişikliği → önce sor; kararı `WEEM_PLANI.md` Bölüm 8'e tarihle yaz.

## Model ve effort (kalite önce, usage dikkatli)

| İş | Model · effort |
|---|---|
| Mimari ve zor kararlar; ekran okuma (`:capture`), Ninebot Bluetooth/şifreleme, rota motoru, 2 telefon bağlantısı, uzaktan kullanım; zor hata ayıklama | Opus · high (çok zorsa xhigh) |
| Net tanımlı iş: testi hazır modül kodu, Compose ekranları, test çevirisi, ayarlar | Sonnet · high |
| Belge, küçük düzeltme, sürüm numarası | Sonnet · medium |
| Aşama sonu denetimi | `/code-review high`; WEEM 1.0 öncesi `/code-review xhigh` |
| Ultracode (çok ajanlı, çok usage) | Yalnız: Aşama 3 ve 6 başında şartname çıkarma, WEEM 1.0 öncesi büyük denetim; hesabın usage'ı tazeyken |

- `max` kullanma (aşırı düşünür, usage yer). Hesapta Opus yoksa Opus satırları Sonnet · high (zorsa xhigh) ile yapılır.
- Ayarlar: `/model opus|sonnet`, `/effort high` (ya da `xhigh`, `medium`), ultracode `/effort ultracode` (kapatma `/effort ultracode off`).
- `WEEM_ISLER.md`'deki her görevde önerilen model · effort yazılıdır; oturum başında ona göre ayarla.

## Usage tasarrufu

- Başlangıçta yalnız: bu dosya, `WEEM_ISLER.md` "Şu an" ve ilgili aşama tablosu, `AI_HANDOFF.md` en üst bölüm, `git log --oneline -10`.
- Dosyayı baştan sona okuma; önce `rg -n`, sonra aralık. Komut çıktısını kısalt (`| tail -30`, `-q`); testlerde yalnız hatalar.
- Kullanıcıya kısa rapor: değişen + doğrulama + kalan risk. Planı ya da kodu sohbete tekrar yazma.
- Yeni belge açmadan önce var olanı güncelle; işi biten belgeyi sil (git'te kalır).

## Temel kurallar

- **Davranış şartnamesi:** bir modül yazılmadan önce eski testleri Kotlin'e çevrilir (aynı girdi → aynı çıktı); yeni kod onları geçmeden `main`'e girmez. Gölge mod karşılaştırması yeşil olmadan geçiş yok (plan Bölüm 5).
- **Kimlik ve veri değişmez** (plan Bölüm 2): paket `com.kuryelog.app`, imza, `com.kuryelog.app.WatchService` / `NotifService` adları, kayıt dosya adları ve şemaları, tercih anahtarları.
- WEEM Wolt/Bolt'ta kendiliğinden hiçbir şeye basmaz; ekran okuma kodu ağ kullanmaz; müşteri adı/telefonu dışa aktarmada gizlenir ve scooter telefonuna gitmez.
- Saf mantık Android'siz Kotlin'de (yerelde test edilir); Android katmanı ince. Bu ortamda `dl.google.com` kapalı olabilir: o zaman Android modülleri yerelde derlenmez, CI derler (oturum başlangıç kancası durumu yazar).
- Cihazda denenmeyene "çalışıyor" deme; bilinmeyen sonuç "doğrulanmadı".
- Hata düzeltirken regresyon testi ekle. Anahtar, token, şifre ve kullanıcı verisi koda ya da belgeye yazılmaz (istisna: Rawstats'tan gelen test imza anahtarı ve şifresi, bugünkü düzen gibi; değiştirmek kullanıcı kararı).

## Commit ve CI

- Commit: `feat:` `fix:` `perf:` `refactor:` `test:` `docs:` `build:` + ne değişti. Bir mantıksal değişiklik = bir commit. Ara ve belge commit'lerine `[skip ci]`; PR'ın son commit'ine yazma.
- CI: PR'da test + lint (Gradle önbellekli, `.md` hariç); APK yalnız `weem-v*` etiketi ya da elle. GitHub Actions dakikası kısıtlı (Myapplab ortak havuzu, `kurye_nav` ile paylaşılır; ayda 2.000 dk): yerelde çalışan testleri push'tan önce çalıştır, APK'yı aşama sonunda bir kez derlet.

## Oturum sonu

1. Testler/analiz.
2. `WEEM_ISLER.md`: görev durumları + "Şu an" (son yapılan, sıradaki iş, devam eden).
3. `AI_HANDOFF.md` en üstüne en fazla 10 satır: `## <tarih> — <konu> (<ajan>; PR/commit)` → Yapılan · Doğrulama (CI no / telefonda denenmedi) · Açık kalan. Dosya 150 satırı geçerse en alttaki bölümleri sil.
4. Kullanıcıya görünen değişiklik varsa `CHANGELOG.md` → `Unreleased`.
5. Push; iş tamsa `main`'e birleştir.
