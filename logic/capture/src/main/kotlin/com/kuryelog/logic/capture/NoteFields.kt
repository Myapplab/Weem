package com.kuryelog.logic.capture

// İskelet (A3.2): imzalar Rawstats'tan (NoteFields.kt @ 6388c04); testlerin derlenmesi için. Gövdeler A3.4'te.
object NoteFields {
    data class Found(val building: String? = null, val entrance: String? = null, val floor: String? = null,
        val apartment: String? = null, val doorCode: String? = null, val phones: List<String> = emptyList(),
        /** F7.1: an office/room number ("16 cu otag", "ofis 16"); shown as "Oda 16" when there is no flat. */
        val room: String? = null)

    fun phones(raw: String?): List<String> = TODO("A3.4")
    fun redact(raw: String?): String? = TODO("A3.4")
    fun extract(vararg texts: String?): Found = TODO("A3.4")
    fun holdsOtherFields(value: String?): Boolean = TODO("A3.4")
    fun truncated(text: String?): Boolean = TODO("A3.4")
}
