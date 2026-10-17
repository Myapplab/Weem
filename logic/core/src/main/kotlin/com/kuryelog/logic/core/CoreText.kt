package com.kuryelog.logic.core

/** `:core`'un ekrana ya da kayda giden metinleri, iki dil yan yana (G3). Android `:core` kaynağında düz metin testle yakalanır. */
enum class CoreText(override val tr: String, override val az: String) : Bilingual {
    // RS `Store.kt:69`: yazılamayan kayıt satırı (`LogStore.lastError`).
    WRITE_FAILED("Kayıt yazılamadı: ", "Qeyd yazılmadı: "),
}
