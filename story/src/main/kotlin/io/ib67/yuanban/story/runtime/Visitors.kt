package io.ib67.yuanban.story.runtime

import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.Instance

/** 处理用户名不在路由表中的玩家（访客） */
internal interface VisitorPolicy {
    /** 在 AsyncPlayerConfigurationEvent 中调用，处理一个用户名不在路由表中的玩家 */
    fun configure(event: AsyncPlayerConfigurationEvent)
    fun isVisitor(player: Player): Boolean

    /** 在服务端线程上调用：把位于 instance 中的访客移到其它 Instance */
    fun evacuate(instance: Instance)
}

/** 默认的访客处理器：没有访客，踢出用户名不在路由表中的玩家，并把这次加入作为服务端异常记录 */
internal object RejectVisitors : VisitorPolicy {
    override fun configure(event: AsyncPlayerConfigurationEvent) {
        val username = event.player.username
        event.player.kick(Component.text("用户名 $username 不属于任何故事"))
        MinecraftServer.process().exception()
            .handleException(IllegalStateException("用户名 $username 不在路由表中，默认的访客处理器踢出了这个玩家"))
    }

    override fun isVisitor(player: Player): Boolean = false

    override fun evacuate(instance: Instance) {}
}
