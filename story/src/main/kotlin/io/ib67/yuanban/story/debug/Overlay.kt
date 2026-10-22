package io.ib67.yuanban.story.debug

import net.kyori.adventure.text.Component

/** Brief status display for debug server visitors. */
internal class Overlay(private val visitors: DebugVisitors, private val monitor: DebugMonitor) {
    fun update() {
        val text = Component.text(monitor.status())
        visitors.all().filter { it.isOnline }.forEach { it.sendActionBar(text) }
    }
}
