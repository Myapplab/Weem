# AGENTS.md — WEEM için yapay zekâ kuralları

Claude, Codex ve diğer ajanlar için tek kural dosyası; `CLAUDE.md` bunu yükler (ayrıca açma). Her oturumun bağlamına girer: kısa tut.

## Proje

**WEEM** (Work Efficient, Earn More; repo `Myapplab/Weem`) = YolGo (harita, navigasyon) + Rawstats (Wolt/Bolt sipariş ve kazanç kaydı) + Sürüş Paneli (scooter): tek native Android uygulaması, Kotlin + Jetpack Compose. Kod yazıldı (Aşama 0–9); şimdi Aşama 10: geçiş, telefon geri bildirimi, sadeleştirme, büyük denetim.

| Dosya | Ne zaman |
|---|---|
| `docs/WEEM_ISLER.md` | "Şu an" (kanca her oturumda yazar), görev tablosu |
| `docs/KOD_HARITASI.md` | Kod değiştirmeden önce: özellik → dosya → hangi test |
| `docs/WEEM_PLANI.md` | Kimlik/veri (2), kapsam (3), mimari (4), kullanıcı kararları (8): yalnız ilgili bölüm |
| `docs/sartname/` | Davranışın nedeni ya da eski uygulamadaki karşılığı gerekince, `rg -n` ile; korunacak adlar `VERI_SOZLESMESI.md` |
| `docs/BEKLEYEN_OZELLIKLER.md` | WEEM 1.0 sonrası özellikler: kullanıcı sormadan yazılmaz |
| `AI_HANDOFF.md` | Yalnız en üst bölüm (kanca yazar) |

Eski uygulamalar (şartname, salt okunur): `Myapplab/kurye_nav` — `yolgo-test` @ `870ab6e` (YolGo, Flutter), `rawstats` @ `6388c04` (Rawstats + Sürüş Paneli). O repoya yazılmaz (istisna yalnız kullanıcı isterse). Erişim: `add_repo` ya da `git clone --depth 1 -b rawstats https://github.com/Myapplab/kurye_nav /tmp/ref-rawstats` (YolGo: `-b yolgo-test /tmp/ref-yolgo`).

## Birden çok Claude hesabı — süreklilik (ZORUNLU)

Kullanıcı 2–3 Claude hesabından sırayla geliştirir; usage biten hesabın işini öteki aynı yerden sürer. İş hiçbir an kaybolmamalı.

1. **Başlarken:** kanca çıktısı ("Şu an", son devir notu, ortam) + `git log --oneline -5` çoğu zaman yeter. "Devam eden" iş varsa önce o.
2. **Üstlen:** "Devam eden" satırına `<iş> — sürüyor (<tarih saat>; dal)`; commit + push (`main`'e de).
3. **Küçük adım, sık push:** her yeşil adımda commit + push, en geç 30 dakikada bir (push edilmemiş iş kapsayıcıyla silinir); "Devam eden" satırı her push'ta güncel.
4. **`main` her zaman son hâl.** Kalıcı izin (17 Ekim): oturum dalından `main`'e PR açıp birleştirmek ya da doğrudan push serbest. Yasak: force-push, geçmişi yeniden yazmak, dal/etiket/Release silmek. Kırmızı test `main`'e girmez: yarım iş dalda kalır, "Devam eden"e dal + son commit + sıradaki adım yazılıp `main`'e push edilir.
5. **Aynı anda iki hesap** varsayılan değil; gerekirse farklı modülde, her biri kendi işini "sürüyor" diye işaretler.
6. **Karar kullanıcının:** kapsam, yeni/bekleyen özellik, plan değişikliği → önce sor; kararı `WEEM_PLANI.md` Bölüm 8'e tarihle yaz.

## Model ve effort (kalite önce, usage dikkatli)

- **Opus · high** (çok zorsa xhigh): mimari, ekran okuma, Ninebot Bluetooth/şifreleme, rota motoru, 2 telefon, uzaktan kullanım, zor hata ayıklama. **Sonnet · high:** net tanımlı iş (Compose ekranı, ayar, testi hazır kod, küçük geri bildirim düzeltmesi). **Sonnet · medium:** belge, sürüm numarası. Denetim: `/code-review high`; A10.7'de `/code-review xhigh` + ultracode.
- `max` kullanma. Opus yoksa Opus işleri Sonnet · high (zorsa xhigh). Görev satırındaki öneriye göre ayarla: `/model opus|sonnet`, `/effort high|xhigh|medium`, `/effort ultracode` (kapatma `/effort ultracode off`).
- **Alt ajan ve ultracode (S13):** yalnız A10.7'de (kullanıcı açıkça istemedikçe başka yerde yok); aynı anda en çok 2–3 ajan; her ajan çıktısını repoya bölüm bölüm yazar, her bölümde commit + push.

## Devam promptu (her profilde bunu yapıştır)

```
WEEM'e devam. AGENTS.md kurallarını uygula: kanca çıktısındaki "Şu an" (önce "Devam eden") ve son devir notu, git log --oneline -5. Sıradaki görevi üstlen (sürüyor + push) ve görevdeki model · effort'u söyle. Kodu docs/KOD_HARITASI.md ile bul, testi tools/test.sh ile koş. Küçük adımlarla commit + push; alt ajanı yalnız AGENTS.md izin veriyorsa kullan; usage'a dikkat. Kapsam ya da plan kararı gerekirse dur ve bana sor. Bitince main'e birleştir, WEEM_ISLER.md ve AI_HANDOFF.md'yi güncelle, bana kısa rapor ver: ne değişti, doğrulama, kalan risk, sıradaki iş.
```

Kullanıcı prompta görev ya da yanıt ekleyebilir; ek yoksa "Şu an"daki sıradaki iş yapılır.

## Ortam (bulut oturumu)

- Kabuk `POSIX`: Türkçe adlı test işlevleri derleyicide kırılır (`InvalidPathException`). `.claude/settings.json` ve `tools/test.sh` `LC_ALL=C.UTF-8` verir; başka kabukta `LC_ALL=C.UTF-8 ./gradlew …` (POSIX'le açılmış daemon varsa önce `./gradlew --stop`). Test adında `. ; : / [ ] < >` kullanma.
- `dl.google.com` kapalıysa Android modülleri burada derlenmez (`logic/` derlenir, Android'i CI derler; kanca yazar). Açmak (kullanıcı, hesap başına bir kez): ortam menüsü → **Edit** → **Network access** → "Allowed domains"e `dl.google.com` (<https://code.claude.com/docs/en/cloud-environments#network-access>).
- Android Gradle ilk indirmede 429/çözümleme hatası verebilir: `--max-workers=1` ile yeniden (`tools/test.sh` kendisi yapar). Robolectric burada koşar.
- Geofabrik, Overpass ve GitHub blob/sürüm indirmeleri kapalı (403); CI kayıtları MCP `get_job_logs` (`return_content`) ile okunur.

## Usage tasarrufu (ZORUNLU)

- **Kod:** önce `docs/KOD_HARITASI.md`, sonra `rg -n`; dosyayı baştan sona okuma, aralık aç. Üretilmiş dosyaları (`WeemIcons.kt`, test `.json`/`.gz`) açma.
- **Test:** `tools/test.sh <hedef>` (yalnız özet ve hatalar; tam günlük `build/test-sh.log`). Önce değişen `logic` modülü; Android testi/lint yalnız Android kodu değiştiyse. Elle Gradle: `-q`, `| tail -30`.
- **Belge tek yerde, kısa:** "Şu an"da satırı değiştir (ekleme yok); `AI_HANDOFF.md`'ye ≤ 6 satır; kullanıcıya görünen değişiklikse `CHANGELOG.md`'ye tek satır. Aynı özeti başka belgeye kopyalama, ayrıntı commit mesajında. Şartnameye oturum günlüğü yazılmaz; yalnız davranış kuralı değişince ilgili madde.
- **Büyük belge:** yeri `rg -n` ile bul, yalnız o aralığı aç ya da betikle değiştir; `CHANGELOG.md`/`AI_HANDOFF.md`'ye eklemek için ilk ~10 satır yeter.
- Kullanıcıya kısa rapor (değişen + doğrulama + kalan risk); planı ya da kodu sohbete tekrar yazma. Yeni belge açmadan var olanı güncelle; işi biten belge ve aracı sil (git'te kalır).

## Temel kurallar

- **Davranış eski uygulamalarla aynı** (aynı girdi → aynı çıktı). Eski uygulamadan gelen yeni özellikte önce eski testler Kotlin'e çevrilir; kod onları geçmeden `main`'e girmez. Ara saha denemesi ve gölge mod karşılaştırması yok (S28): sorunlar gerçek kullanımda düzeltilir.
- **Kimlik ve veri değişmez** (plan Bölüm 2): paket `com.kuryelog.app`, imza, `com.kuryelog.app.WatchService` / `NotifService` adları, kayıt dosya adları ve şemaları, tercih anahtarları.
- WEEM Wolt/Bolt'ta kendiliğinden hiçbir şeye basmaz; ekran okuma kodu ağ kullanmaz; müşteri adı/telefonu dışa aktarmada gizlenir ve scooter telefonuna gitmez.
- Karar veren kod `logic/`'te (Android'siz, yerelde test edilir); Android katmanı ince.
- Cihazda denenmeyene "çalışıyor" deme; bilinmeyen sonuç "doğrulanmadı".
- Hata düzeltirken regresyon testi ekle. Anahtar, token, şifre, kullanıcı verisi koda ya da belgeye yazılmaz (istisna: Rawstats'tan gelen test imza anahtarı ve şifresi; değiştirmek kullanıcı kararı).

## Commit ve CI

- Commit: `feat:` `fix:` `perf:` `refactor:` `test:` `docs:` `build:` + ne değişti; bir mantıksal değişiklik = bir commit. Ara ve belge commit'lerine `[skip ci]` (PR'ın son commit'ine değil).
- CI: PR'da test + lint (`.md`, `docs/`, `.claude/` hariç); APK yalnız `weem-v*` etiketi ya da elle (`apk.yml`). Actions dakikası kısıtlı (Myapplab ortak, `kurye_nav` ile; ayda 2.000 dk): testleri push'tan önce yerelde koş, APK'yı iş sonunda bir kez derlet.

## Oturum sonu

1. Değişen modüllerin testleri yeşil (`tools/test.sh`); Android kodu değiştiyse ilgili lint.
2. `WEEM_ISLER.md` "Şu an": "Son yapılan" tek satır (öncekini sil), "Devam eden", "Sıradaki"; görev satırında kısa durum.
3. `AI_HANDOFF.md` en üstüne ≤ 6 satır: `## <tarih> — <konu> (<ajan>; PR/commit)` → Yapılan · Doğrulama (CI no / telefonda denenmedi) · Açık; 5 bölümü geçerse en alttakini sil.
4. Kullanıcıya görünen değişiklik → `CHANGELOG.md` `Unreleased` altına tek satır.
5. Push; iş tamsa `main`'e birleştir.
