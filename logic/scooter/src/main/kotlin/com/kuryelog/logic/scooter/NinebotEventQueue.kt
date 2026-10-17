package com.kuryelog.logic.scooter

import java.util.concurrent.atomic.AtomicInteger

/** Bounded handoff to the serial disk writer. Never blocks the Bluetooth callback thread. (SCOOTER.md 7.3; NB `NinebotEventQueue.java`) */
class NinebotEventQueue(private val dispatcher: Dispatcher, capacity: Int) {
    fun interface Dispatcher {
        fun post(task: Runnable): Boolean
    }

    private val limit: Int
    private val pending = AtomicInteger()

    init {
        if (capacity < 1) throw IllegalArgumentException("capacity")
        limit = capacity
    }

    fun post(event: Runnable): Boolean {
        if (pending.incrementAndGet() > limit) { pending.decrementAndGet(); return false }
        var accepted = false
        try {
            accepted = dispatcher.post { try { event.run() } finally { pending.decrementAndGet() } }
            return accepted
        } finally {
            if (!accepted) pending.decrementAndGet()
        }
    }

    val size: Int get() = pending.get()
}
