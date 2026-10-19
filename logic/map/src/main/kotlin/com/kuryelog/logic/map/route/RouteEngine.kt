package com.kuryelog.logic.map.route

/**
 * Arka plan rota motoru (YolGo `RouteWorker`; `MAP.md` 3.11): her istek ayrı iş parçacığında hesaplanır, zaman aşımı ve iptal vardır;
 * kapatılan motor yeni iş kabul etmez (`IllegalStateException`, Dart `StateError`). İskelet (A6.2a): gövdeler A6.4'te.
 */
class RouteEngine(val timeoutMs: Long = 25_000) : AutoCloseable {
    suspend fun compute(request: PlanRequest): PlanResult = TODO("A6.4")

    /** Test kancası: çalışan iş parçacığını öldürür (çöken işçi benzetimi). */
    fun debugKillWorker(): Unit = TODO("A6.4")

    override fun close(): Unit = TODO("A6.4")
}
