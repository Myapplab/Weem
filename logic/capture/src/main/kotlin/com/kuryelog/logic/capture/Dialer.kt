package com.kuryelog.logic.capture

/** Phone/dialer apps across Android vendors (Google, AOSP, Samsung, Xiaomi, Huawei, OnePlus…). */
object Dialer {
    private val exact = setOf("com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.android.incallui", "com.android.contacts", "com.android.server.telecom", "com.oneplus.dialer", "com.miui.contacts", "com.huawei.contacts")
    fun isDialer(pkg: String): Boolean = pkg in exact || pkg.endsWith(".dialer") || pkg.endsWith(".incallui")
}
