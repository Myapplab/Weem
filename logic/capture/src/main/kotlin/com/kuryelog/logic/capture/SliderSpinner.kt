package com.kuryelog.logic.capture

/**
 * K33: after the courier swipes Wolt's `Order delivered` slider, a spinning progress indicator appears in the middle of the
 * slider, often too briefly to be read. Accessibility exposes progress indicators with the class `android.widget.ProgressBar`
 * (Jetpack Compose maps a progress indicator to that class too). No recorded frame has shown this spinner yet, so the rule is
 * conservative and the result is only extra evidence for the slider logic in [DeliveryCore], never a delivery by itself:
 *  - only a node whose class is `ProgressBar` counts, and only a roughly square one (a spinner, not a linear bar);
 *  - when the same frame still shows the slider label, the spinner must sit on the label's row;
 *  - without the label (the spinner may replace it) the frame counts, and [DeliveryCore] accepts it only within
 *    [DeliveryCore.SPINNER_AFTER_SLIDER_MS] of that customer's slider frame.
 * Pure code: no Android types.
 */
object SliderSpinner {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    fun isSpinnerClass(cls: String?): Boolean {
        val c = cls?.trim() ?: return false
        return c == "ProgressBar" || c.endsWith(".ProgressBar")
    }

    /** Roughly square and not empty: a circular indicator, not a horizontal progress bar. */
    fun isRound(b: Box): Boolean = b.width > 0 && b.height > 0 && b.width <= 2 * b.height && b.height <= 2 * b.width

    /** [spinners]: boxes of visible `ProgressBar` nodes; [labels]: boxes of visible nodes whose text is a deliver label. */
    fun onSlider(spinners: List<Box>, labels: List<Box>): Boolean {
        val round = spinners.filter(::isRound)
        if (round.isEmpty()) return false
        if (labels.isEmpty()) return true
        return round.any { s ->
            val cy = (s.top + s.bottom) / 2
            labels.any { l -> cy >= l.top - l.height && cy <= l.bottom + l.height }
        }
    }
}
