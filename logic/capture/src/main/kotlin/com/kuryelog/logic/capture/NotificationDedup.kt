package com.kuryelog.logic.capture

/**
 * The notification listener's rules (R2, CAPTURE.md 4.1; RS `NotifService.kt:28-80`). Android `NotifService` reads the extras
 * and writes; what goes where, and which refresh is a repeat, is decided here.
 */
class NotificationDedup {
    enum class Route {
        /** A navigation app (R16): only its trip summary, in memory; nothing is written. Checked before the targets. */
        NAVIGATION,
        /** Not a watched courier app. */
        IGNORE,
        /** A watched courier app. */
        WATCHED,
    }

    private val lastByKey = HashMap<String, String>()

    fun route(pkg: String, targets: Set<String>): Route = when (pkg) {
        in NavDistance.packages -> Route.NAVIGATION
        in targets -> Route.WATCHED
        else -> Route.IGNORE
    }

    /** A watched notification was posted: true when it is written and forwarded; false without recording permission (the
     *  memory of earlier contents is dropped) or when the same [key] posts the same content again (a refreshed notification). */
    fun posted(key: String, title: String?, text: String?, big: String?, sub: String?, canRecord: Boolean): Boolean {
        if (!canRecord) { lastByKey.clear(); return false }
        val content = "$title|$text|$big|$sub"
        if (lastByKey[key] == content) return false
        lastByKey[key] = content
        return true
    }

    /** A watched notification was removed: forgotten (with or without permission); true when the "removed" row is written. */
    fun removed(key: String, canRecord: Boolean): Boolean {
        lastByKey.remove(key)
        return canRecord
    }

    companion object {
        /** The text `:orders` gets (`OrderTracker.notification`): the non-empty fields, each once, one per line. */
        fun forOrders(title: String?, text: String?, big: String?, sub: String?): String =
            listOfNotNull(title, text, big, sub).distinct().joinToString("\n")
    }
}
