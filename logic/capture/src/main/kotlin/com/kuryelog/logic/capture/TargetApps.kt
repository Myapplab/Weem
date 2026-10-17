package com.kuryelog.logic.capture

import java.util.Locale

/** Which apps are watched (tercih `targets`, CAPTURE.md 1.9; RS `MainActivity.kt:1428-1462`). The list itself lives in Android. */
object TargetApps {
    private fun key(pkg: String, label: String) = (pkg + " " + label).lowercase(Locale.ROOT)

    /** A launchable Wolt or Bolt courier app, added on its own while the courier has not picked the list by hand. */
    fun autoDetected(pkg: String, label: String): Boolean {
        val s = key(pkg, label)
        return (s.contains("wolt") && (s.contains("partner") || s.contains("courier") || s.contains("kuryer"))) ||
            ((s.contains("bolt") || s.contains("mtakso")) &&
                (s.contains("courier") || s.contains("food") || s.contains("driver") || s.contains("kuryer") || s.contains("partner")))
    }

    /** Listed first when picking by hand. */
    fun courierLike(pkg: String, label: String): Boolean {
        val s = key(pkg, label)
        return s.contains("wolt") || s.contains("bolt") || s.contains("mtakso") || s.contains("courier") || s.contains("kurye") || s.contains("kuryer")
    }

    /** The watched set after an app start: [current] plus every auto-detected launchable app (package to label); never removes;
     *  unchanged while [userPicked]. */
    fun autoAdded(current: Set<String>, launchable: List<Pair<String, String>>, userPicked: Boolean): Set<String> =
        if (userPicked) current else current + launchable.filter { autoDetected(it.first, it.second) }.map { it.first }

    /** The pick list: courier-like apps first, then by label. */
    fun pickOrder(launchable: List<Pair<String, String>>): List<Pair<String, String>> =
        launchable.sortedWith(compareBy<Pair<String, String>>({ !courierLike(it.first, it.second) }, { it.second.lowercase(Locale.ROOT) }))
}
