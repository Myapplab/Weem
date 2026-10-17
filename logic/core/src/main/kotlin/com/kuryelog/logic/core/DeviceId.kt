package com.kuryelog.logic.core

import java.util.Locale
import java.util.UUID

/**
 * WEEM'in cihaz kimliği (CORE.md 1.10; eski uygulamalarda yok): ilk açılışta rastgele 128 bit, `kuryelog` `weem.deviceId`'de küçük
 * harfli UUID dizgisi (36 karakter). Donanım kimliğinden türetilmez; yedeğe girmez. İlk kullanıcı 2 telefon modu (A8) ve N3.
 */
object DeviceId {
    private val pattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun valid(id: String?): Boolean = id != null && pattern.matches(id)

    /** Kayıtlı geçerli kimlik; yoksa ya da bozuksa yenisi üretilip yazılır. */
    fun ensure(p: KuryelogPrefs.Values, random: () -> UUID = UUID::randomUUID): String {
        p.getString(KuryelogPrefs.DEVICE_ID, null)?.takeIf(::valid)?.let { return it }
        val id = random().toString().lowercase(Locale.ROOT)
        p.put(mapOf(KuryelogPrefs.DEVICE_ID to id))
        return id
    }
}
