package com.kuryelog.logic.capture

// İskelet (A3.2): imzalar Rawstats'tan (CourierParser.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.4'te.
object CourierParser {
    val WOLT: Set<String> = setOf("com.wolt.courierapp", "com.wolt.partner")
    fun norm(s: String): String = TODO("A3.4")
    fun amount(s: String): Double? = TODO("A3.4")
    fun isAccept(s: String): Boolean = TODO("A3.4")
    fun isDecline(s: String): Boolean = TODO("A3.4")
    fun isCollect(s: String): Boolean = TODO("A3.4")
    fun isDeliver(s: String): Boolean = TODO("A3.4")
    fun isCall(s: String): Boolean = TODO("A3.4")
    fun isArrive(s: String): Boolean = TODO("A3.4")
    fun statsPeriod(ns: List<String>, at: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): String? = TODO("A3.4")
    fun parse(pkg: String, texts: List<String>, actions: List<String>, at: Long, source: String = "accessibility", complete: Boolean = true): CourierObservation = TODO("A3.4")
}
