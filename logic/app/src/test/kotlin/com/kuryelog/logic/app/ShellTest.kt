package com.kuryelog.logic.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellTest {
    @Test fun menuHasFiveTabsInPlanOrderAndOpensOnMap() {
        assertEquals(listOf(Tab.MAP, Tab.ORDERS, Tab.SCOOTER, Tab.REMOTE, Tab.MORE), Tab.entries)
        assertEquals(Tab.MAP, Tab.start)
    }

    @Test fun savedTabKeyRoundTripsAndUnknownFallsBackToMap() {
        for (tab in Tab.entries) assertEquals(tab, Tab.fromKey(tab.key))
        assertEquals(Tab.MAP, Tab.fromKey(null))
        assertEquals(Tab.MAP, Tab.fromKey("nope"))
    }

    @Test fun tabKeysAreUnique() {
        assertEquals(Tab.entries.size, Tab.entries.map { it.key }.toSet().size)
    }

    @Test fun explicitLanguageWinsOverPhoneLanguage() {
        assertEquals(Lang.TR, Lang.resolve("tr", "az"))
        assertEquals(Lang.AZ, Lang.resolve("az", "tr"))
    }

    @Test fun emptySettingFollowsPhoneLanguage() {
        assertEquals(Lang.AZ, Lang.resolve("", "az"))
        assertEquals(Lang.AZ, Lang.resolve(null, "AZ"))
        assertEquals(Lang.TR, Lang.resolve("", "tr"))
        assertEquals(Lang.TR, Lang.resolve("", "en"))
        assertEquals(Lang.TR, Lang.resolve(null, null))
    }

    @Test fun themeModeResolvesDarkness() {
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertFalse(ThemeMode.LIGHT.isDark(true))
        assertTrue(ThemeMode.DARK.isDark(false))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("x"))
        for (m in ThemeMode.entries) assertEquals(m, ThemeMode.fromKey(m.key))
    }

    @Test fun preferenceKeysMatchTheKuryelogFile() {
        assertEquals("lang", Lang.PREF_KEY)
    }
}
