package io.ib67.yuanban.story.debug

import net.kyori.adventure.text.Component

/** 给调试服务端的访客展示的简要状态 */
internal class Overlay(private val visitors: DebugVisitors, private val monitor: DebugMonitor) {
    fun update() {
        val text = Component.text(monitor.status())
        visitors.all().filter { it.isOnline }.forEach { it.sendActionBar(text) }
    }
}
