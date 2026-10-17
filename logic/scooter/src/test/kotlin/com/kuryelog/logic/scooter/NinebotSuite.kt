package com.kuryelog.logic.scooter

import org.junit.Assert.assertTrue

/**
 * Sürüş Paneli main() suitlerinin ortak `check(koşul, etiket)`'i (SCOOTER.md 13.1 çeviri kuralı): etiket aynı, geçen her denetim
 * [count]'a eklenir; suit sonunda sayı eski çıktının "çalışma" sayısıyla karşılaştırılır. Çağrı yeri sayıları `SuiteCountTest`'te.
 */
abstract class NinebotSuite {
    protected var count = 0
        private set

    protected fun check(ok: Boolean, label: String) {
        assertTrue(label, ok)
        count++
    }
}
