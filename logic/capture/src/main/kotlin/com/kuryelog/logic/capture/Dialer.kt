package com.kuryelog.logic.capture

/** Phone/dialer apps across Android vendors (Google, AOSP, Samsung, Xiaomi, Huawei, OnePlus…). */
object Dialer {
    private val exact = setOf("com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.android.incallui", "com.android.contacts", "com.android.server.telecom", "com.oneplus.dialer", "com.miui.contacts", "com.huawei.contacts")
    fun isDialer(pkg: String): Boolean = pkg in exact || pkg.endsWith(".dialer") || pkg.endsWith(".incallui")

    /**
     * A10.2: ekran kaydı açıkken erişilebilirlik olaylarının kapsamı = izlenen uygulamalar + arama uygulamaları (bilinenler ve telefonun
     * varsayılanı). Başka uygulamaların (banka, mesaj) olayları servise hiç gelmez. İzlenen uygulama yoksa yalnız [own].
     */
    fun eventScope(targets: Set<String>, defaultDialer: String?, own: String): Set<String> =
        if (targets.isEmpty()) setOf(own) else (targets + exact + listOfNotNull(defaultDialer?.takeIf { it.isNotBlank() })).toSortedSet()
}
