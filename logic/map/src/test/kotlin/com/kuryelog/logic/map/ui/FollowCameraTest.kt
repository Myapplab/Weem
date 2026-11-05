package com.kuryelog.logic.map.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S44: takipte kamera merkezi ve yönü sıçramadan hedefe gelir (konuma dönüş, sığdırmadan takibe, varış odağı). */
class FollowCameraTest {
    private val lat = 40.41
    private val lon = 49.87

    @Test
    fun aciFarkiKisaYoldan() {
        assertEquals(20.0, CameraEase.angleDelta(350.0, 10.0), 1e-9)
        assertEquals(-20.0, CameraEase.angleDelta(10.0, 350.0), 1e-9)
        assertEquals(-180.0, CameraEase.angleDelta(0.0, 180.0), 1e-9)
        assertEquals(0.0, CameraEase.easeAngle(350.0, 10.0, .5), 1e-9)
        assertEquals(355.0, CameraEase.easeAngle(10.0, 340.0, .5), 1e-9)
    }

    @Test
    fun takipBaslarkenKameraBulunduguYerdenIsareteKayarSicramaz() {
        val cam = FollowCamera.start(lat + 0.002, lon, 0.0, lat, lon) // ~220 m kuzeyde
        val (c1, _, _) = cam.step(lat, lon, lat, lon, 0.0, 80)
        assertTrue("ilk karede sıçramaz", c1 - lat > 0.0015)
        assertFalse(cam.settled)
        var c = c1
        repeat(40) { c = cam.step(lat, lon, lat, lon, 0.0, 80).first }
        assertEquals(lat, c, 1e-9)
        assertTrue(cam.settled)
    }

    @Test
    fun uzakKameraBirAndaGelir() {
        val cam = FollowCamera.start(lat + 0.05, lon, 90.0, lat, lon) // ~5,5 km
        val (c, _, b) = cam.step(lat, lon, lat, lon, 10.0, 80)
        assertEquals(lat, c, 1e-12)
        assertEquals(10.0, b!!, 1e-12)
        assertTrue(cam.settled)
    }

    @Test
    fun yonKisaYoldanYakalanirSonraDogrudanIzlenir() {
        val cam = FollowCamera.start(lat, lon, 350.0, lat, lon)
        val b1 = cam.step(lat, lon, lat, lon, 30.0, 80).third!!
        assertTrue("kısa yoldan (350 → 30, saat yönünde)", b1 > 350.0 || b1 < 30.0)
        assertTrue(b1 != 30.0)
        repeat(30) { cam.step(lat, lon, lat, lon, 30.0, 80) }
        assertEquals(31.0, cam.step(lat, lon, lat, lon, 31.0, 80).third!!, 1e-12)
    }

    @Test
    fun yuruyenIsaretleBirlikteGiderYalnizFarkKapanir() {
        val cam = FollowCamera.start(lat, lon, 0.0, lat, lon)
        // Varış odağı: hedef merkez işaretin 50 m kuzeyi; merkez yavaşça oraya kayar, işaret yürürken fark işarete göre korunur.
        val off = 50 / 111_195.08
        val first = cam.step(lat, lon, lat + off, lon, 0.0, 80).first
        assertTrue(first - lat in 0.0..off * .5)
        repeat(60) { cam.step(lat, lon, lat + off, lon, 0.0, 80) }
        val moved = cam.step(lat + 0.001, lon, lat + 0.001 + off, lon, 0.0, 80).first
        assertEquals(lat + 0.001 + off, moved, 1e-9)
    }

    @Test
    fun yonBilinmiyorsaKameraYonuDegismez() {
        val cam = FollowCamera.start(lat, lon, 45.0, lat, lon)
        assertEquals(null, cam.step(lat, lon, lat, lon, null, 80).third)
        assertTrue(cam.settled)
    }
}
