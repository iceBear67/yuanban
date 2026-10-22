package io.ib67.yuanban.story.debug

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Breakpoint registry for the debug server. */
internal class Breakpoints {
    data class Entry(val number: Int, val text: String)

    private val next = AtomicInteger()
    private val entries = CopyOnWriteArrayList<Entry>()

    fun add(text: String): Entry = Entry(next.incrementAndGet(), text).also(entries::add)
    fun remove(number: Int): Boolean = entries.removeIf { it.number == number }
    fun clear() = entries.clear()
    fun all(): List<Entry> = entries.toList()
}
