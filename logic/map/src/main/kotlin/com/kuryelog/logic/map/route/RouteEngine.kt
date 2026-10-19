package com.kuryelog.logic.map.route

/**
 * Arka plan rota motoru (YolGo `RouteWorker`; MAP.md 3.11): tek sıralı kuyruk, [timeoutMs] zaman aşımı, kapatılınca iş kabul etmez
 * (`IllegalStateException`). İskelet (A6.2a): gövde A6.4'te.
 */
class RouteEngine(val timeoutMs: Long = 25_000) {
    suspend fun compute(request: PlanRequest): PlanResult = TODO("A6.4")

    fun dispose(): Unit = TODO("A6.4")

    /** Test kancası: çalışan hesabı çöker (YolGo `debugKillIsolate`); sonraki istek yeni işçiyle çalışır. */
    internal fun debugKillWorker(): Unit = TODO("A6.4")
}
