package com.kuryelog.logic.scooter

import org.junit.Assert.assertTrue

/**
 * Sürüş Paneli main() suitlerinin ortak `check(koşul, etiket)`'i (SCOOTER.md 13.1 çeviri kuralı): etiket aynı, geçen her denetim
 * [count]'a eklenir; suit sonunda sayı eski çıktının "çalışma" sayısıyla karşılaştırılır. Çağrı yeri sayıları `SuiteCountTest`'te.
 */
abstract class NinebotSuite {
    /** Eski suitler gibi doğrudan da artırılabilir (`SweepTest.rejects`, `check` çağırmadan sayar). */
    protected var count = 0

    protected fun check(ok: Boolean, label: String) {
        assertTrue(label, ok)
        count++
    }

    companion object {
        /**
         * Başka bir suitin paylaşılan yardımcısı içindeki denetim (ör. `ProtocolTest.Port`, `PowerCadenceTest.run`): eskide o suitin
         * statik sayacına giderdi, çağıranın sayısına girmezdi; burada da yalnız doğrulanır.
         */
        val ASSERT_ONLY: (Boolean, String) -> Unit = { ok, label -> assertTrue(label, ok) }
    }
}
