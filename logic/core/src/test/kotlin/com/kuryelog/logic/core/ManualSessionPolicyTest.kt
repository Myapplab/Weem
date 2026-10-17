package com.kuryelog.logic.core

import org.junit.Assert.*
import org.junit.Test

/** Rawstats `CourierFlowTest`'in iki testi (A3.2; CAPTURE.md 8): vardiyayı elle başlatma ve kayıt izni kuralı `:core`'da. */
class ManualSessionPolicyTest {
    @Test fun automaticAndStickyStartsCannotCreateShift() { assertFalse(ManualSessionPolicy.mayStart(false,false,false)); assertTrue(ManualSessionPolicy.mayStart(true,false,false)); assertTrue(ManualSessionPolicy.mayStart(false,true,false)); assertFalse(ManualSessionPolicy.mayStart(true,false,true)) }
    @Test fun captureNeedsManualSessionOnly() { assertFalse(ManualSessionPolicy.canCapture(true,false,false)); assertFalse(ManualSessionPolicy.canCapture(false,true,false)); assertFalse(ManualSessionPolicy.canCapture(true,true,true)); assertTrue(ManualSessionPolicy.canCapture(true,true,false)) }
}
