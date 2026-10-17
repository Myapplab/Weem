package com.kuryelog.logic.scooter

import org.junit.Assert.assertEquals
import org.junit.Test

/** NT `LinkTest.java` (S1; 23 çağrı, 33 çalışma): GATT sırası, yinelenen ve sırasız geri çağrılar. */
class LinkTest : NinebotSuite() {
    @Test fun gattOrderingAssertions() {
        // Android 17 capture: cached MTU callback arrived before service discovery,
        // then another MTU event arrived during subscription setup. Start only once.
        var link = NinebotLink()
        check(!link.mtuReady(), "cached MTU cannot start protocol before inventory")
        check(link.servicesReady(false), "inventory selects MTU phase")
        check(!link.servicesReady(false), "duplicate service discovery ignored")
        check(link.mtuReady(), "requested MTU opens subscriptions")
        check(!link.mtuReady(), "duplicate MTU cannot start another subscription")
        for (id in arrayOf("nordic3", "custom4", "custom6")) {
            check(link.expectDescriptor(id), "subscribe channel")
            check(!link.subscriptionsReady(), "descriptor must complete before protocol")
            check(!link.descriptorReady("unrelated"), "unrelated descriptor ignored")
            check(link.descriptorReady(id), "expected descriptor completed")
            check(!link.descriptorReady(id), "duplicate descriptor ignored")
        }
        check(link.subscriptionsReady(), "all subscription acknowledgements received")
        check(!link.mtuReady(), "MTU during settling cannot restart setup")
        check(link.protocolReady(), "settle completes once")
        check(!link.protocolReady(), "no second session or pulse")
        check(!link.subscriptionsReady(), "late callback cannot replace live session")
        link.close(); check(!link.protocolReady() && !link.servicesReady(false), "closed connection cannot resume")
        link = NinebotLink(); check(!link.protocolReady(), "reconnect starts from fresh discovery")
        check(link.servicesReady(true), "optional name read precedes MTU request")
        check(!link.mtuReady(), "unsolicited MTU cannot interrupt name read")
        check(link.nameReady(), "name read completes"); check(!link.nameReady(), "duplicate name callback ignored")
        check(link.mtuReady(), "new connection negotiates MTU")
        link.expectDescriptor("custom4"); link.close(); check(!link.descriptorReady("custom4"), "disconnect cancels pending descriptor")
        assertEquals("GATT ordering assertions passed; no physical scooter was used", 33, count)
    }
}
