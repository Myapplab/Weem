package com.kuryelog.logic.scooter

/**
 * The G3's GATT layout and the link's fixed numbers (SCOOTER.md 2.1; NB `BleCapture.java:22-25, 37, 43-50, 79-82`): Nordic UART
 * "profile 0" (write RX, notify TX), the Ninebot service (kept, unused), the device-name read, MTU 247 asked (23 until changed),
 * a 15 s watchdog after every GATT request, 200 ms settling before the protocol, at most 4 queued writes that fit `mtu − 3`.
 */
object GattPlan {
    const val NORDIC = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    /** The last seven bytes are ASCII "ninebot". */
    const val CUSTOM = "6e400001-0000-0000-006e-696e65626f74"
    const val WRITE = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val NOTIFY = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"
    const val DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb"

    const val MTU = 247
    const val DEFAULT_MTU = 23
    const val WATCHDOG_MS = 15000L
    const val SETTLE_MS = 200L
    const val MAX_WRITES = 4

    /** Characteristics of these services are kept for writing; only the Nordic TX one with notify/indicate is subscribed. */
    fun kept(service: String): Boolean = service == CUSTOM || service == NORDIC

    fun subscribed(service: String, characteristic: String, notifyOrIndicate: Boolean): Boolean =
        service == NORDIC && characteristic == NOTIFY && notifyOrIndicate

    /** The read device name: UTF-8, NULs removed, Java `trim()`; empty keeps the advertised name. */
    fun deviceName(value: ByteArray): String = String(value, Charsets.UTF_8).replace("\u0000", "").trim { it <= ' ' }

    /** A frame is written whole or not at all; at most [MAX_WRITES] wait (the session keeps one read outstanding anyway). */
    fun checkWrite(size: Int, mtu: Int, queued: Int) {
        if (size > mtu - 3) throw IllegalStateException("MTU cannot carry complete frame")
        if (queued >= MAX_WRITES) throw IllegalStateException("write queue full")
    }
}
