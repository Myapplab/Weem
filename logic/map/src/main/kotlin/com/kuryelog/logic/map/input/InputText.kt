package com.kuryelog.logic.map.input

import com.kuryelog.logic.core.Lang

/** İki dilli metin parçası (parametreli mesajlar için; sabitler [com.kuryelog.logic.map.ui.MapText]'te). */
data class Msg(val tr: String, val az: String) {
    fun get(lang: Lang): String = if (lang == Lang.AZ) az else tr
}

internal fun tl(lang: Lang, tr: String, az: String) = if (lang == Lang.AZ) az else tr
