package com.kuryelog.logic.capture

/**
 * Wolt / Bolt Food screens that are not part of an order: side menu, support,
 * statistics, earnings, settings. Their wording ("Deliveries completed",
 * "Recent orders", "Online", "An accepted order went missing") must never be
 * read as an order, a delivery or a waiting map. Labels are normalised (CourierParser.norm).
 */
object CourierScreens {
    /** Side-menu entries. Three or more on screen at once means the menu is open. */
    private val menuItems = setOf(
        // Wolt
        "delivery settings", "insights", "finances", "stats", "earn extra", "benefits and insurance", "referrals", "help center",
        // Bolt Food
        "all deliveries", "earnings", "campaigns", "inbox", "performance", "profile", "help", "invite friends", "about", "settings", "support"
    )
    /** Titles of full-screen pages reached from the menu or support. */
    private val pageTitles = setOf(
        // Wolt
        "previous task", "no previous completed task found.", "set offline time", "your profile", "customize your delivery settings",
        "deliveries completed", "tasks accepted", "task completion rate", "gross earnings", "recent transactions", "transactions",
        "payouts", "your stats", "earn extra", "coming up later", "benefits and insurance", "refer and earn", "navigation app",
        "navigation mode", "appearance", "start week on", "task sound", "feedback", "skip feedback", "how can we help you?",
        "select the relevant order", "recent orders", "other topics", "app help", "my partner account", "payments", "complaints",
        "contact courier operations team", "my conversations", "wolt support", "you have no open conversations",
        "keyfiyyeti qoruyaq!",
        // Bolt Food
        "all deliveries", "earnings schedule", "campaigns", "inbox", "performance", "completed deliveries",
        "acceptance rate", "cases", "select an issue", "select order", "help articles", "communication preferences",
        "delivery method", "map settings", "navigator", "invite friends", "disclaimer", "tip information", "bolt support",
        "account issue", "earnings issue", "bonus issue", "language"
    )
    /** Wolt's waiting sheet itself shows "EARN EXTRA" (chart legend) and "Support" (quick link);
     *  with one toolbar icon that already looked like an open menu and hid the waiting map. */
    private val alsoOnWaitingSheet = setOf("earn extra", "support")
    /** Performance pages: Wolt "Your stats", Bolt "Performance" / "Acceptance rate". The "Stats" toolbar icon of
     *  Wolt's waiting sheet and the menu entries are not; neither is the "Scheduled offline" quick link. */
    private val statsTitles = setOf("your stats", "deliveries completed", "tasks accepted", "task completion rate", "earnings (estimate)",
        "performance", "acceptance rate", "completion rate", "completed deliveries")
    fun isMenu(lines: List<String>): Boolean = lines.distinct().count { it in menuItems && it !in alsoOnWaitingSheet } >= 3
    fun isPage(lines: List<String>): Boolean = lines.any { it in pageTitles || it.startsWith("select the relevant o") }
    fun isStats(lines: List<String>): Boolean = !isMenu(lines) && lines.any { it in statsTitles }
}
