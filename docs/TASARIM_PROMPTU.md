# Tasarım yenileme promptu (A10.4d)

Kullanıcı kararı (22 Ekim, S35): mevcut görünüm beğenilmedi; Facebook / YouTube / Instagram / TikTok düzeyinde, üst düzey bir tasarım istiyor.
**Model · effort:** Opus · **xhigh** (tasarım dili ve örnek ekranlar). `max` önerilmez (AGENTS.md: aşırı düşünür, usage yer; tek istisna A3.1). Örnekler onaylandıktan sonra ekranlara uygulama Sonnet · high ile yapılabilir.
Yeni oturumda `AGENTS.md` devam promptundan sonra bunu yapıştır.

```
WEEM'in görünümünü baştan tasarla (görev A10.4d). Kullanıcı mevcut tasarımı hiç beğenmedi ve Facebook, YouTube, Instagram, TikTok gibi
üst düzey uygulamalar seviyesinde bir sonuç istiyor. Bu işi ciddiye al: önce tasarım, sonra kod.

BAĞLAM
- WEEM: tek native Android uygulaması (Kotlin + Jetpack Compose, Material 3). Wolt/Bolt kuryesi scooter'la sürerken kullanır: telefon gidonda ya da cepte;
  bakış çok kısa, tek elle, titreşimli yolda, gündüz güneşte ve gece. Türkçe ve Azerice (her metin iki dilde).
- Sekmeler: Harita, Siparişler, Scooter, Uzak (2 telefon), Daha fazla. Üstte vardiya kartı. Ayrıca ilk açılış kurulumu, ayarlar, kayıtlar,
  Yollarım (yol puanları), gün raporu, istatistik. Ekran envanteri: `app/src/main/kotlin/com/kuryelog/app/**` ve `docs/sartname/*.md`.
- Davranış, veri, metin anlamı ve kimlik DEĞİŞMEZ (AGENTS.md "Temel kurallar"). Yalnız görünüm, yerleşim, hareket ve etkileşim.

NE İSTİYORUM (benden bunu üret)
1. Ekran envanteri ve mevcut sorunların kısa dürüst teşhisi (hangi ekran neden kötü: hiyerarşi, yoğunluk, renk, dokunma alanı, tutarsızlık).
2. Tasarım dili: renk (gündüz/gece, yüksek kontrast, scooter/Wolt/Bolt için ayırt edici tonlar), yazı ölçeği, boşluk ve köşe yarıçapı,
   gölge/yükseklik, simgeler (emoji yerine tutarlı simge seti), hareket (geçişler, mikro animasyonlar, haptik), boş/yükleniyor/hata durumları.
3. Bileşen kitaplığı: üst çubuk, alt gezinme, kart, liste satırı, düğme türleri, rozet, anahtar, bölümler, sayfa/alt sayfa, geri bildirim.
4. En çok kullanılan 4 ekranın (Harita, Siparişler, vardiya kartı, Scooter) YÜKSEK KALİTELİ örnek tasarımı: HTML/CSS taslak olarak
   (Artifact), telefon genişliğinde, gündüz + gece, TR ve AZ uzun metinle. Kullanıcı bunlardan birini seçer ya da değiştirtir.
5. Onaydan sonra: `WeemTheme.kt` + ortak bileşenler (`:app`), sonra ekranlar tek tek yeni tasarıma geçer. Her ekranın testi yeşil kalır.

"ÜST DÜZEY" NE DEMEK (Instagram/TikTok/YouTube'dan alınacak ilkeler)
- Net hiyerarşi: her ekranda tek ana şey büyük ve ilk bakışta okunur; ikincil bilgi sessiz.
- Kart ve akış düzeni, geniş boşluk, büyük ve yumuşak köşeler, tutarlı simge seti; yazı ağırlığıyla hiyerarşi.
- Akıcı hareket: yumuşak geçişler, dokununca tepki, kayarak açılan alt sayfalar, iskelet (skeleton) yükleme; ama dikkat dağıtmaz.
- Büyük dokunma alanları (≥ 48 dp, gidonda ≥ 56 dp), ana eylem başparmağın erişiminde (altta), tek elle kullanım.
- Gece modunda göz yormayan koyu tema (saf siyah değil), gündüz güneşte yüksek kontrast.
- Tutarlılık: aynı şey her yerde aynı görünür ve aynı yerde durur.
- Sürüş güvenliği: sürerken yoğun metin, küçük yazı, kaydırma ve çok adımlı akış yok; kritik bilgi (sipariş durumu, batarya, uyarı) tek bakışta.

SINIRLAR (değişmez kurallar)
- Metin kuralı: `:app` kaynaklarında yerel harfli düz metin yok; tüm metin `logic/*` iki dilli tablolarda (`TextCoverageTest`).
- Paket `com.kuryelog.app`, imza, kayıt ve tercih adları değişmez. Wolt/Bolt'ta WEEM kendiliğinden hiçbir şeye basmaz.
- Ağ yok / reklam yok / analiz kütüphanesi yok. Yeni ağır kütüphane ekleme (APK ≈ 32 MB release; Compose Material 3 + yerel simge seti yeter).
- Cihazda denenmedi sayılır: "çalışıyor" deme. Dar ekran (360 dp) ve büyük yazı ayarında bozulmasın.
- Küçük adım: tasarım dili → örnekler (kullanıcı onayı) → tema/bileşenler → ekran ekran; her adımda commit + push, `WEEM_ISLER.md` "Devam eden" güncel.
- Alt ajan yok (AGENTS.md), kullanıcı açıkça istemedikçe.

ÇIKTI
Önce (1)–(4)'ü sun ve DUR: kullanıcı örnekleri onaylayana kadar koda geçme.
```
