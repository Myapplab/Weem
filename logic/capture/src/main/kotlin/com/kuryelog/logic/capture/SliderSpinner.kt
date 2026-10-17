package com.kuryelog.logic.capture

// İskelet (A3.2): imzalar Rawstats'tan (SliderSpinner.kt @ 6388c04); K33. Gövdeler A3.4'te.
object SliderSpinner {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = TODO("A3.4")
        val height: Int get() = TODO("A3.4")
    }
    fun isSpinnerClass(cls: String?): Boolean = TODO("A3.4")
    /** Roughly square and not empty: a circular indicator, not a horizontal progress bar. */
    fun isRound(b: Box): Boolean = TODO("A3.4")
    /** [spinners]: boxes of visible `ProgressBar` nodes; [labels]: boxes of visible nodes whose text is a deliver label. */
    fun onSlider(spinners: List<Box>, labels: List<Box>): Boolean = TODO("A3.4")
}
