package com.kuryelog.logic.map.route

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Arka plan rota motoru — YolGo `RouteWorker` (izole işçi) karşılığı (MAP.md 3.11). Graf paylaşılan değişmez nesnedir (kopya yok);
 * hesaplar [dispatcher] üstünde tek sıralı kuyrukta, gelen sırayla. Yanıt [timeoutMs] içinde gelmezse [RouteTimeout] (bekleme
 * dahil, YolGo gibi); takılan hesap iptal edilir ve kuyruk kilitlenmez: A* döngüsü askıya alınmaz, her 1.024 yığın çekişinde ve
 * her bacakta iptal bayrağına bakar. Çağıranın coroutine'i iptal edilirse hesap da durur. Kapatılan motor iş kabul etmez
 * (`IllegalStateException`, Dart `StateError`); kapatma ya da çökme anında bekleyen istekler hemen aynı hatayı alır.
 *
 * Fark: YolGo zaman aşımında işçiyi öldürdüğü için sıradaki bütün istekler de zaman aşımı alıyordu; burada her istek kendi süresiyle
 * sürer (sonucu atan çağıran için görünen davranış aynı). Çağıran başına ayrı motor ya da paylaşılan motor serbest.
 */
class RouteEngine(val timeoutMs: Long = 25_000, dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    private val queue = dispatcher.limitedParallelism(1)
    private val lock = Any()
    private var worker = Worker()
    private var disposed = false

    /** Bir "işçi": hesapların ortak üst işi; durdurulunca bekleyen ve çalışan hesaplar [stopped] nedeniyle biter. */
    private class Worker {
        val job = SupervisorJob()

        @Volatile
        var stopped: String? = null

        fun stop(reason: String) {
            stopped = reason
            job.cancel()
        }
    }

    suspend fun compute(request: PlanRequest): PlanResult {
        val w = synchronized(lock) {
            check(!disposed) { DISPOSED }
            worker
        }
        val task = CoroutineScope(w.job + queue).async {
            val job = coroutineContext.job
            Planner.computePlans(request, RouteProbe { job.ensureActive() })
        }
        try {
            // Yalnız bu motorun süresi RouteTimeout olur; çağıranın kendi zaman aşımı ya da iptali olduğu gibi geçer.
            return withTimeoutOrNull(timeoutMs) { task.await() } ?: run {
                task.cancel()
                throw RouteTimeout()
            }
        } catch (e: CancellationException) {
            task.cancel()
            // Çağıran iptal edilmediyse işçi durduruldu: çökme ya da kapatma.
            if (currentCoroutineContext().isActive) throw IllegalStateException(w.stopped ?: STOPPED, e)
            throw e
        }
    }

    /** Motoru kapatır: çalışan ve bekleyen hesaplar biter, sonraki [compute] hata verir. */
    fun dispose() {
        synchronized(lock) {
            disposed = true
            worker.stop(DISPOSED)
        }
    }

    /** Test kancası: işçinin beklenmedik ölümü (YolGo `debugKillIsolate`); bekleyenler hata alır, sonraki istek yeni işçiyle çalışır. */
    internal fun debugKillWorker() {
        synchronized(lock) {
            val old = worker
            worker = Worker()
            old.stop(STOPPED)
        }
    }

    private companion object {
        const val DISPOSED = "Route worker disposed"
        const val STOPPED = "Route worker stopped"
    }
}
