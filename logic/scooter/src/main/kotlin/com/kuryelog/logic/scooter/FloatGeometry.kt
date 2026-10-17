package com.kuryelog.logic.scooter

/**
 * Size and gesture rules of the floating ball. With MAX values the horizontal bar takes 80 % of the screen's long side
 * (the phone held landscape; Pixel 10 Pro: 2856 px → 2285 px) and the vertical column 60 % of it (portrait height:
 * 2856 px → 1714 px); fewer values take proportionally less. A bar never leaves the current screen. Android-free.
 * (SCOOTER.md 6.3; NB `FloatGeometry.java`, line by line.)
 */
object FloatGeometry {
    const val HORIZONTAL_SHARE = .80
    const val VERTICAL_SHARE = .60

    /** User-adjustable sizes (Settings → Panel → Yüzen panel boyutu): defaults and allowed ranges. Percent = share of the long side at 4 values. */
    const val BALL_DP = 100
    const val BALL_MIN = 64
    const val BALL_MAX = 160
    const val THICK_PCT = 125
    const val THICK_MIN = 80
    const val THICK_MAX = 200
    const val H_PCT = 80
    const val H_MIN = 30
    const val H_MAX = 100
    const val V_PCT = 60
    const val V_MIN = 25
    const val V_MAX = 95

    /** 0.18.0: text size of the ball and bar, separately for a portrait and a landscape screen (landscape read too small). */
    const val TEXT_PCT = 100
    const val TEXT_MIN = 60
    const val TEXT_MAX = 180

    fun clamp(v: Int, lo: Int, hi: Int): Int = Math.max(lo, Math.min(hi, v))

    enum class Direction {
        NONE, RIGHT, LEFT, DOWN, UP;

        fun horizontal(): Boolean = this == RIGHT || this == LEFT

        fun opposite(): Direction = when (this) {
            RIGHT -> LEFT
            LEFT -> RIGHT
            DOWN -> UP
            UP -> DOWN
            else -> NONE
        }
    }

    /** Swipe direction from finger travel; anything shorter than slop on both axes is a tap. */
    fun direction(dx: Float, dy: Float, slop: Float): Direction {
        if (Math.abs(dx) < slop && Math.abs(dy) < slop) return Direction.NONE
        if (Math.abs(dx) >= Math.abs(dy)) return if (dx > 0) Direction.RIGHT else Direction.LEFT
        return if (dy > 0) Direction.DOWN else Direction.UP
    }

    /** Length of the opened bar in px along its axis, for `count` values, the ball diameter and the current screen; [share] is the
     *  user's share (0.80 = 80 % of the long side at 4 values), by default the fixed one of the axis. */
    fun length(
        count: Int, horizontal: Boolean, screenW: Int, screenH: Int, ball: Int, margin: Int,
        share: Double = if (horizontal) HORIZONTAL_SHARE else VERTICAL_SHARE,
    ): Int {
        val n = Math.max(1, Math.min(PanelField.MAX, count))
        val longSide = Math.max(screenW, screenH)
        val target = longSide * share * n / PanelField.MAX
        val room = (if (horizontal) screenW else screenH) - 2 * margin
        return Math.round(Math.max(ball.toDouble(), Math.min(target, room.toDouble()))).toInt()
    }

    /** Opened bar thickness against the ball diameter (user, 2026-10-09: 1.0 too thin, 1.6 too big). */
    const val THICKNESS = 1.25

    fun thickness(ball: Int): Int = Math.round(ball * THICKNESS).toInt()

    fun thickness(ball: Int, percent: Int): Int = Math.round(ball * clamp(percent, THICK_MIN, THICK_MAX) / 100.0).toInt()

    /** Cross-axis start: the thick bar stays centred on the ball, then is pulled back inside the screen. */
    fun cross(ballStart: Int, ball: Int, thick: Int, screen: Int, margin: Int): Int =
        Math.max(margin, Math.min(ballStart + (ball - thick) / 2, screen - margin - thick))

    /** Start coordinate of an opened bar: grows from the ball toward the swipe, then is pulled back inside the screen. */
    fun start(ballStart: Int, ball: Int, length: Int, towardEnd: Boolean, screen: Int, margin: Int): Int {
        val s = if (towardEnd) ballStart else ballStart + ball - length
        return Math.max(margin, Math.min(s, screen - margin - length))
    }
}
