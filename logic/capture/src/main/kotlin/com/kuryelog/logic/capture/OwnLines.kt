package com.kuryelog.logic.capture

// İskelet (A3.2): Rawstats `OverlayText.isOwn/withoutOwn` (OverlayText.kt:99-134 @ 6388c04); CAPTURE.md 2.10. Gövdeler A3.4'te.
object OwnLines {
    /** True for a line WEEM draws itself, as the accessibility tree or OCR may read it. */
    fun isOwn(line: String): Boolean = TODO("A3.4")
    /** [texts] without WEEM's own lines; a multi-line text keeps its other lines. */
    fun withoutOwn(texts: List<String>): List<String> = TODO("A3.4")
}
