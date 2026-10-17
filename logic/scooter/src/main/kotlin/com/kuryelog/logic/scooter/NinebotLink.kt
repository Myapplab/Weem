package com.kuryelog.logic.scooter

/**
 * Orders asynchronous GATT setup and ignores duplicate/out-of-order callbacks (SCOOTER.md 2.1; NB `NinebotLink.java`, line by
 * line): discovery → optional name read → MTU → one descriptor at a time → settling → protocol, each step accepted once.
 */
class NinebotLink {
    enum class Phase { DISCOVERING, NAME, MTU, SUBSCRIBING, SETTLING, PROTOCOL, CLOSED }

    var phase = Phase.DISCOVERING
        private set
    private var descriptor: String? = null

    fun servicesReady(readName: Boolean): Boolean {
        if (phase != Phase.DISCOVERING) return false
        phase = if (readName) Phase.NAME else Phase.MTU
        return true
    }

    fun nameReady(): Boolean {
        if (phase != Phase.NAME) return false
        phase = Phase.MTU
        return true
    }

    fun mtuReady(): Boolean {
        if (phase != Phase.MTU) return false
        phase = Phase.SUBSCRIBING
        return true
    }

    fun expectDescriptor(id: String): Boolean {
        if (phase != Phase.SUBSCRIBING || descriptor != null) return false
        descriptor = id
        return true
    }

    fun descriptorReady(id: String): Boolean {
        if (phase != Phase.SUBSCRIBING || descriptor == null || descriptor != id) return false
        descriptor = null
        return true
    }

    fun subscriptionsReady(): Boolean {
        if (phase != Phase.SUBSCRIBING || descriptor != null) return false
        phase = Phase.SETTLING
        return true
    }

    fun protocolReady(): Boolean {
        if (phase != Phase.SETTLING) return false
        phase = Phase.PROTOCOL
        return true
    }

    fun close() {
        phase = Phase.CLOSED
        descriptor = null
    }
}
