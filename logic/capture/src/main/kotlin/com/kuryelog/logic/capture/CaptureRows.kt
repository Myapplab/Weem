package com.kuryelog.logic.capture

import com.kuryelog.logic.core.LogStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * The rows `:capture` writes (CAPTURE.md 6.1–6.3; RS `WatchService.kt:161-166, 89, 229`, `NotifService.kt:49-62, 73-78`).
 * Every raw text that reaches the disk goes through [NoteFields.redact] (phone → `[tel]`). A null field is not written.
 */
object CaptureRows {
    /** One accessibility node with a value: text, content description, view id, class, `Rect.flattenToString()`, actionable. */
    data class Node(val text: String, val desc: String, val viewId: String, val cls: String, val bounds: String, val actionable: Boolean)

    /** `screens.jsonl`; [nodes] only for an accessibility read. */
    fun screen(log: LogStore, at: Long, pkg: String, window: Int, bounds: String, source: String, stage: ScreenStage,
               texts: List<String>, actions: List<String>, nodes: List<Node>?, batteryPct: Int?, spinner: Boolean): JSONObject {
        val j = JSONObject().put("t", log.ts(at)).put("pkg", pkg).put("window", window).put("bounds", bounds)
            .put("source", source).put("offerLike", stage == ScreenStage.OFFER).put("stage", stage.name)
            .put("texts", JSONArray(texts.map { NoteFields.redact(it) })).put("actions", JSONArray(actions.map { NoteFields.redact(it) }))
            .put("nodes", if (source == "accessibility") nodes?.let(::nodeRows) else null).put("phone_battery_pct", batteryPct)
        if (source == "accessibility" && spinner) j.put("deliver_spinner", true)
        log.locJson()?.let { j.put("loc", it) }
        return j
    }

    private fun nodeRows(nodes: List<Node>) = JSONArray().also { out ->
        for (n in nodes) out.put(JSONArray().put(NoteFields.redact(n.text)).put(NoteFields.redact(n.desc)).put(n.viewId).put(n.cls).put(n.bounds).put(n.actionable))
    }

    /** `notifs.jsonl` "posted" ([at] = now, [postTime] = `sbn.postTime`). */
    fun posted(log: LogStore, at: Long, postTime: Long, pkg: String, key: String, ongoing: Boolean, channel: String?,
               title: String?, text: String?, big: String?, sub: String?): JSONObject {
        val o = JSONObject().put("t", log.ts(at)).put("posted", log.ts(postTime)).put("ev", "posted").put("pkg", pkg).put("key", key)
            .put("ongoing", ongoing).put("channel", channel)
            .put("title", NoteFields.redact(title)).put("text", NoteFields.redact(text)).put("big", NoteFields.redact(big)).put("sub", NoteFields.redact(sub))
        log.locJson()?.let { o.put("loc", it) }
        return o
    }

    /** `notifs.jsonl` "removed" (no location). */
    fun removed(log: LogStore, at: Long, pkg: String, key: String): JSONObject =
        JSONObject().put("t", log.ts(at)).put("ev", "removed").put("pkg", pkg).put("key", key)

    /** `events.jsonl` `click` extras: the distinct labels joined by a space, null when there is none. WEEM: `text` is
     *  redacted too (Rawstats wrote it raw; a tapped phone number must not reach the disk, CAPTURE.md 6.3). */
    fun click(pkg: String, labels: List<String>): JSONObject? {
        val txt = labels.distinct().joinToString(" ").trim()
        return if (txt.isEmpty()) null else JSONObject().put("pkg", pkg).put("text", NoteFields.redact(txt))
    }

    /** `events.jsonl` `screenshot_unavailable` extras. */
    fun screenshotUnavailable(pkg: String, code: Int, display: Boolean): JSONObject =
        JSONObject().put("pkg", pkg).put("code", code).put("display", display)
}
