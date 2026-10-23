package com.kuryelog.logic.scooter

import com.kuryelog.logic.core.Lang
import org.junit.Assert.*
import org.junit.Test

/** SCOOTER.md 5.3 / 11.3: vardiya bağlantısı kararları ve ana kart satırı (Rawstats'ta testsizdi; WEEM eki, `role_provider` S11). */
class ScooterLinkRulesTest {
    private val ready = ScooterLinkRules.Facts(selected = true, paired = true, permitted = true, active = false)

    @Test fun startOpensOnlyWhenEverythingIsReadyAndNamesTheFirstMissingThing() {
        assertNull(ScooterLinkRules.startSkip(true, ready))
        assertEquals("auto_off", ScooterLinkRules.startSkip(false, ready))
        // S37: açık bağlantı engel değil (kayda çevrilir); izinsiz ama bağlıyken de açılır.
        assertNull(ScooterLinkRules.startSkip(true, ready.copy(active = true)))
        assertNull(ScooterLinkRules.startSkip(true, ready.copy(active = true, permitted = false)))
        assertEquals("no_scooter", ScooterLinkRules.startSkip(true, ready.copy(selected = false, paired = false)))
        assertEquals("not_paired", ScooterLinkRules.startSkip(true, ready.copy(paired = false, permitted = false)))
        assertEquals("no_bluetooth_permission", ScooterLinkRules.startSkip(true, ready.copy(permitted = false)))
    }

    @Test fun providerNeverOpensTheLinkEvenWhenAutoIsOffOrNotPaired() {
        val provider = ready.copy(providerRole = true)
        assertEquals("role_provider", ScooterLinkRules.startSkip(true, provider))
        assertEquals("role_provider", ScooterLinkRules.openSkip(provider.copy(paired = false)))
        assertFalse(ScooterLinkRules.canConnectNow(true, provider))
        // auto kapalıyken yine auto_off (vardiya başı sırası: önce tercih).
        assertEquals("auto_off", ScooterLinkRules.startSkip(false, provider))
    }

    @Test fun connectNowNeedsOpenShiftAndAReadyScooter() {
        assertTrue(ScooterLinkRules.canConnectNow(true, ready))
        assertFalse(ScooterLinkRules.canConnectNow(false, ready))
        // S37: canlı bağlantı (eşleştirme sonrası) kayda çevrilebilir; zaten kayıttaysa düğme yok.
        assertTrue(ScooterLinkRules.canConnectNow(true, ready.copy(active = true)))
        assertFalse(ScooterLinkRules.canConnectNow(true, ready.copy(active = true, recording = true)))
        assertFalse(ScooterLinkRules.canConnectNow(true, ready.copy(permitted = false)))
    }

    @Test fun shiftOpensARecordingUpgradesALiveLinkOrAdoptsARunningRecording() {
        assertEquals(ScooterLinkRules.Open.RECORD, ScooterLinkRules.openAction(ready))
        assertEquals(ScooterLinkRules.Open.UPGRADE, ScooterLinkRules.openAction(ready.copy(active = true)))
        assertEquals(ScooterLinkRules.Open.ADOPT, ScooterLinkRules.openAction(ready.copy(active = true, recording = true)))
        assertEquals(listOf("start", "upgrade", "adopt"), ScooterLinkRules.Open.entries.map { it.action })
    }

    @Test fun shiftEndStopsTheRecordingTheShiftOwnsAndNothingElse() {
        assertEquals(ScooterLinkRules.End.NOTHING, ScooterLinkRules.shiftEnd(owned = false, active = true)) // vardiyanın olmayan bağlantı (ör. eşleştirme)
        assertEquals(ScooterLinkRules.End.NOTHING, ScooterLinkRules.shiftEnd(owned = true, active = false))
        // S37: kayıt vardiyayla aynı anda biter (eskiden kullanıcı kayda çevirdiyse sürerdi).
        assertEquals(ScooterLinkRules.End.STOP, ScooterLinkRules.shiftEnd(owned = true, active = true))
        assertNull(ScooterLinkRules.endAction(ScooterLinkRules.End.NOTHING))
        assertEquals("stop", ScooterLinkRules.endAction(ScooterLinkRules.End.STOP))
    }

    @Test fun eventBodyHasActionAndNullReason() {
        val e = ScooterLinkRules.event("start", null)
        assertEquals("start", e.getString("action")); assertTrue(e.isNull("reason"))
        assertEquals("auto_off", ScooterLinkRules.event("skip", "auto_off").getString("reason"))
    }

    @Test fun reconnectTextsAreTheTwoLanguageNotices() {
        val back = ScooterLinkRules.reconnectText(ScooterReconnect.Notice.RECEIVING, Lang.TR)
        assertEquals("Scooter verileri alınıyor", back.first); assertEquals("Bağlantı yeniden kuruldu, değerler geliyor.", back.second)
        val lost = ScooterLinkRules.reconnectText(ScooterReconnect.Notice.NOT_RECEIVING, Lang.AZ)
        assertEquals("Skuter məlumatları alınmır", lost.first)
        assertEquals("scooter_data_back", ScooterLinkRules.reconnectAction(ScooterReconnect.Notice.RECEIVING))
        assertEquals("scooter_data_missing", ScooterLinkRules.reconnectAction(ScooterReconnect.Notice.NOT_RECEIVING))
    }

    @Test fun cardLineFollowsTheLinkState() {
        fun s(f: ScooterLinkRules.Facts, auto: Boolean = true, shift: Boolean = false, live: ScooterLinkRules.Live? = null, lang: Lang = Lang.TR) =
            ScooterLinkRules.status(f, auto, shift, live, lang)
        assertEquals("Scooter seçilmedi · Sürüş Paneli'nde ara ve seç", s(ready.copy(selected = false)))
        assertEquals("Eşleştirilmedi · Sürüş Paneli'nde G3 ile eşleştir", s(ready.copy(paired = false)))
        assertEquals("Bluetooth izni gerekli", s(ready.copy(permitted = false)))
        assertEquals("Bağlı değil · vardiyada otomatik bağlantı kapalı", s(ready, auto = false))
        assertEquals("Bağlı değil", s(ready, shift = true))
        assertEquals("Vardiya başlayınca bağlanır", s(ready))
        assertEquals("Qoşulmayıb", s(ready, shift = true, lang = Lang.AZ))
        val active = ready.copy(active = true)
        assertEquals("Bağlanıyor · scooter verisi bekleniyor", s(active))
        val frame = NinebotDashboard.Snapshot(18.46, -312.4, null, null, 77.6, null, null, null, null, 18.46, 64.0, null, 1_000)
        val trip = TripMeter.Stats("t1", 1, 12.34, null, 0, 0, null, null, 0.0, 0, null)
        assertEquals("%78 · 18.5 km/h · -312 W · harici %64 · bu sipariş 12.3 Wh", s(active, live = ScooterLinkRules.Live(frame, trip)))
        // Sayaç siparişsizse (etiket boş) satıra girmez.
        val free = TripMeter.Stats("", 1, 12.34, null, 0, 0, null, null, 0.0, 0, null)
        assertEquals("%78 · 18.5 km/h · -312 W · harici %64", s(active, live = ScooterLinkRules.Live(frame, free)))
        assertEquals(ScooterText.PROVIDER_ROLE.tr, s(ready.copy(providerRole = true)))
    }
}
