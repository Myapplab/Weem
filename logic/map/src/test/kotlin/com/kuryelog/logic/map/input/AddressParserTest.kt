package com.kuryelog.logic.map.input

import com.kuryelog.logic.map.input.ParsedDestination.Address
import com.kuryelog.logic.map.input.ParsedDestination.Coordinate
import com.kuryelog.logic.map.input.ParsedDestination.ShortLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YolGo `address_parser_test.dart` "AddressParser" 15 test + `assistant_test.dart` açık görev girişi 1 test (A6.2b, MAP.md 8.4, 8.11, 16.3;
 * "Plus Code" 2 test `PlusCodeTest`'te).
 * Girdi ve beklentiler birebir; Dart `isA<T>` → Kotlin `as`/`is`. WEEM ek vektörleri (NBSP, Kiril'e bitişik telefon, büyük harf, `BAKİ`; 8.13 F-N2) A6.6'da eklenir.
 */
class AddressParserTest {
    private fun parse(s: String) = AddressParser.parse(s)

    private fun at(link: String, lat: Double, lon: Double) {
        val r = parse(link)
        assertTrue(link, r is Coordinate)
        val p = (r as Coordinate).point
        assertEquals(link, lat, p.lat, 1e-6)
        assertEquals(link, lon, p.lon, 1e-6)
    }

    @Test
    fun `Bolt Plus Code metni koordinata döner`() {
        val r = parse("9RQW+344 Baku, Baku, AZ1000")
        assertTrue(r is Coordinate)
        assertEquals(40.3876, (r as Coordinate).point.lat, 0.001)
    }

    @Test
    fun `Wolt Sokak Numara biçimi güçlü adres sayılır`() {
        val r = parse("Mehdi Hüseyn 99")
        assertTrue(r is Address)
        assertTrue((r as Address).strong)
        assertEquals("Mehdi Hüseyn 99, Bakı", r.query)
    }

    @Test
    fun `Wolt Rusça şehir adı korunur`() {
        val r = parse("Təbriz 52, Баку") as Address
        assertTrue(r.strong)
        assertEquals("Təbriz 52, Баку", r.query)
    }

    @Test
    fun `Bolt İngilizce adres`() {
        val r = parse("Sabail district, Uzeyir Hajibeyov str., 27, AZ1000 Baku")
        assertTrue(r is Address)
        assertTrue((r as Address).strong)
    }

    @Test
    fun `Kapı detayları ayıklanır, telefon silinir`() {
        val r = parse("Nizami küç. 12, mənzil 45, 7-ci mərtəbə, giriş 2, domofon 1234, +994 50 123 45 67") as Address
        assertEquals("Nizami küçəsi 12, Bakı", r.query)
        assertEquals("Mənzil 45 · Mərtəbə 7 · Giriş 2 · Kod 1234", r.unitNote)
    }

    @Test
    fun `WhatsApp konum linki`() {
        val r = parse("https://maps.google.com/maps?q=40.3712,49.8345&z=17&hl=en")
        assertTrue(r is Coordinate)
        val p = (r as Coordinate).point
        assertEquals(40.3712, p.lat, 1e-6)
        assertEquals(49.8345, p.lon, 1e-6)
    }

    @Test
    fun `Yandex boylam enlem sırası çevrilir`() {
        val r = parse("https://yandex.az/maps/?pt=49.8345,40.3712&z=17") as Coordinate
        assertEquals(40.3712, r.point.lat, 1e-6)
    }

    @Test
    fun `Harita butonu geo ve google navigation`() {
        val a = parse("geo:40.3712,49.8345?q=40.3712,49.8345(Xengeland)") as Coordinate
        assertEquals("Xengeland", a.label)
        val b = parse("google.navigation:q=40.3712,49.8345&mode=b") as Coordinate
        assertEquals(49.8345, b.point.lon, 1e-6)
        assertTrue(parse("geo:0,0?q=Nizami+k%C3%BC%C3%A7+12") is Address)
    }

    @Test
    fun `Kısa link tanınır`() {
        assertTrue(parse("https://maps.app.goo.gl/AbCdEf123") is ShortLink)
    }

    @Test
    fun `Waze`() {
        at("waze://?ll=40.4093,49.8671&navigate=yes", 40.4093, 49.8671)
        at("https://waze.com/ul?ll=40.4093%2C49.8671&navigate=yes", 40.4093, 49.8671)
        assertTrue(parse("waze://?q=Nizami%20k%C3%BC%C3%A7%C9%99si%2012&navigate=yes") is Address)
    }

    @Test
    fun `Yandex hedef son nokta`() {
        at("yandexnavi://build_route_on_map?lat_to=40.4093&lon_to=49.8671", 40.4093, 49.8671)
        at("yandexmaps://maps.yandex.ru/?rtext=40.3700,49.8000~40.4093,49.8671&rtt=auto", 40.4093, 49.8671)
        at("https://yandex.ru/maps/?rtext=~40.4093%2C49.8671&rtt=auto", 40.4093, 49.8671)
    }

    @Test
    fun `2GIS boylam enlem ve HERE`() {
        at("dgis://2gis.ru/routeSearch/rsType/car/to/49.8671,40.4093", 40.4093, 49.8671)
        at("https://2gis.ru/baku/routeSearch/rsType/car/to/49.8671,40.4093", 40.4093, 49.8671)
        at("here-route://mylocation/40.4093,49.8671,Musteri", 40.4093, 49.8671)
        at("https://share.here.com/r/mylocation/40.4093,49.8671,Musteri", 40.4093, 49.8671)
    }

    @Test
    fun `Google yol tarifi hedef son durak, başlangıç ve görünüm değil`() {
        at("https://www.google.com/maps/dir/40.3700,49.8000/40.4093,49.8671/@40.39,49.83,14z", 40.4093, 49.8671)
        val r = parse("https://www.google.com/maps/dir/40.3700,49.8000/Nizami+k%C3%BC%C3%A7%C9%99si+10,+Bak%C4%B1/@40.37,49.80,15z")
        assertTrue(r is Address)
        assertTrue((r as Address).query.contains("Nizami"))
    }

    @Test
    fun `Waze ve Yandex web adresleri metin hedefini korur`() {
        for (link in listOf(
            "https://www.waze.com/ul?q=Nizami%20k%C3%BC%C3%A7%C9%99si%2012&navigate=yes",
            "https://yandex.az/maps/?text=Nizami%20k%C3%BC%C3%A7%C9%99si%2012",
        )) {
            assertEquals("Nizami küçəsi 12, Bakı", (parse(link) as Address).query)
        }
    }

    @Test
    fun `Adres olmayan metinler yok sayılır`() {
        assertNull(parse("Salam, 5 dəqiqəyə gəlirəm"))
        assertNull(parse("#CCUV5"))
        assertNull(parse("https://instagram.com/xyz"))
        assertNull(parse("Toplam 12.50, 3.20 AZN"))
    }

    @Test
    fun `açık görev girişi ASAN ve banka mekân adını kabul eder, pano filtresi korunur`() {
        assertTrue(AddressParser.parseExplicit("ASAN Xidmət") is Address)
        assertTrue(AddressParser.parseExplicit("Birbank") is Address)
        assertNull(AddressParser.parse("ASAN Xidmət"))
        assertNull(AddressParser.parseExplicit("https://instagram.com/example"))
    }
}
