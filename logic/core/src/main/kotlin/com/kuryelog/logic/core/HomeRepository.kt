package com.kuryelog.logic.core

/** Tek ev kaynağı (CORE.md 2.1). Android tercihleri bağdaştırıcıdır; seçim taslakları bu depoya girmez. */
class HomeRepository(private val storage: Storage) {
    data class Saved(val lat: String? = null, val lon: String? = null, val updatedAt: Long? = null,
        val startBlocked: Boolean = false, val endArmed: Boolean = false)
    data class Point(val lat: Double, val lon: Double, val updatedAt: Long?)
    interface Storage { fun read(): Saved; fun write(value: Saved) }
    private var cached: HomeShift? = null

    @Synchronized fun point(): Point? {
        val s = storage.read()
        val p = HomeShift.parseHome(s.lat, s.lon) ?: return null
        return Point(p.first, p.second, s.updatedAt)
    }
    @Synchronized fun set(lat: Double, lon: Double, now: Long) {
        require(HomeShift.parseHome(lat.toString(), lon.toString()) != null)
        storage.write(Saved(lat.toString(), lon.toString(), maxOf(now, (point()?.updatedAt ?: 0L) + 1)))
        cached = null
    }
    @Synchronized fun clear() { storage.write(Saved()); cached = null }
    @Synchronized fun engine(): HomeShift? {
        val p = point() ?: run { cached = null; return null }
        cached?.takeIf { it.homeLat == p.lat && it.homeLon == p.lon }?.let { return it }
        val s = storage.read()
        return HomeShift(p.lat, p.lon, s.startBlocked, s.endArmed).also { cached = it }
    }
    @Synchronized fun observe(fix: HomeShift.Fix, active: Boolean, busy: Boolean = false): HomeShift.Decision {
        val e = engine() ?: return HomeShift.Decision.NONE
        val before = e.startBlocked to e.endArmed
        return e.observe(fix, active, busy).also { persist(e, before) }
    }
    @Synchronized fun changeGuard(change: (HomeShift) -> Unit) {
        val e = engine() ?: return
        val before = e.startBlocked to e.endArmed
        change(e); persist(e, before)
    }
    private fun persist(e: HomeShift, before: Pair<Boolean, Boolean>) {
        if (before != (e.startBlocked to e.endArmed))
            storage.write(storage.read().copy(startBlocked = e.startBlocked, endArmed = e.endArmed))
    }
    @Synchronized fun acceptsRevision(revision: Long?): Boolean = point()?.let {
        revision != null && revision == (it.updatedAt ?: 0L)
    } ?: false
    fun mayWatch(active: Boolean, importing: Boolean, fine: Boolean, background: Boolean) =
        point() != null && !active && !importing && fine && background
}
