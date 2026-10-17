# WEEM — AI devir notu

Kısa ve güncel; en yeni en üstte. Bölüm başına en fazla 10 satır; dosya 150 satırı geçerse en alttaki bölümler silinir (git'te kalır).

## 2026-10-17 — Repo Myapplab'a taşındı, son plan kontrolü (Claude; `9a4c75a`)
- Yapılan: kullanıcı repoyu `mehemmedsh/Weem` → `Myapplab/Weem` taşıdı (eski adres yönleniyor). Plana 2 telefon akış ayrıntıları, Receiver donanım ölçütleri, en kritik bilinmeyen (Pixel cepteyken teklif okuma), kod sadeleştirme adımı (A10.2), satır tahmini eklendi. İş listesine Aşama 0: A0.1 (planın son hâli, ultracode) ve K0.1 (kodsuz saha denemesi).
- Doğrulama: yalnız belge. Kod yok, CI yok.
- Açık kalan: A0.1 yeni hesapta ultracode ile; sonra A1.1.

## 2026-10-17 — Proje kuruldu (Claude; ilk commit)
- Yapılan: plan (`docs/WEEM_PLANI.md`), iş listesi (`docs/WEEM_ISLER.md`), bekleyen özellikler, kurallar (hesaplar arası devam, model/effort), oturum başlangıç kancası (`.claude/hooks/session-start.sh`).
- Kararların geçmişi: `Myapplab/kurye_nav` oturum dalı `ccr-4d54acb0-ohiz3j` (commit `007c8e3`, `05935f5`) ve özellik seçim sayfası (plan Bölüm 3).
- Doğrulama: yalnız belge + kanca yerelde çalıştırıldı. Kod yok, CI yok.
- Ortam: o oturumda `dl.google.com` kapalıydı (Android SDK ve Google Maven inmiyor); JDK 21, Gradle, Maven Central açık.
- Açık kalan: A1.1 (iskelet). Kullanıcı kararları: simge, Receiver telefonu, ortam ağ ayarında `dl.google.com`.
