package com.kuryelog.logic.core

/** Çökmeyen hatanın gideceği yer (Android'de `Store.failure` → `errors.jsonl`, CORE.md 4.2). */
fun interface FailureSink {
    fun failure(source: String, e: Throwable)
}

/**
 * Sistemin çağırdığı her giriş bununla korunur (CORE.md 4.2, RS `GpsService.kt:113-115`): hata kaydedilir, fırlatılmaz; sonuç
 * yoksa null. `VirtualMachineError` (bellek, yığın taşması) yeniden fırlatılır: süreç kapanır ve çökme raporu yazılır.
 * Kaydın kendisi hata verirse o da yutulur (koruma hiçbir zaman fırlatmaz).
 */
inline fun <T> guarded(source: String, sink: FailureSink, block: () -> T): T? = attempt(source, sink, block).getOrNull()

/** [guarded] gibi; hatayı da döndürür (çizilemeyen sekmenin kartı onu gösterir). */
inline fun <T> attempt(source: String, sink: FailureSink, block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: Throwable) {
    if (e is VirtualMachineError) throw e
    try { sink.failure(source, e) } catch (inner: Throwable) { if (inner is VirtualMachineError) throw inner }
    Result.failure(e)
}
