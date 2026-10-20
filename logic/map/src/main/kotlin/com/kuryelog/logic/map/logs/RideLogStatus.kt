package com.kuryelog.logic.map.logs

/** Sürüş kayıtları ekranının durum yazısı (`rideLogStatus`, MAP.md 12.7): (TR, AZ). İskelet (A6.2b): gövde A6.9'da. */
object RideLogStatus {
    /** Üç durum: kayıt sürüyor · gün başlayınca kaydedilir · kapalı. */
    fun texts(logging: Boolean, dayActive: Boolean): Pair<String, String> = TODO("A6.9")
}
