package com.kuryelog.logic.map.logs

/** Sürüş kayıtları ekranının durum yazısı (`rideLogStatus`, MAP.md 12.10): (TR, AZ). */
object RideLogStatus {
    /** Üç durum: kayıt sürüyor · gün başlayınca kaydedilir · kapalı. */
    fun texts(logging: Boolean, dayActive: Boolean): Pair<String, String> = when {
        !logging -> "Kayıt kapalı" to "Qeyd bağlıdır"
        dayActive -> "● Kayıt sürüyor" to "● Qeyd gedir"
        else -> "Gün başlayınca kaydedilir" to "Gün başlayanda qeyd olunur"
    }
}
