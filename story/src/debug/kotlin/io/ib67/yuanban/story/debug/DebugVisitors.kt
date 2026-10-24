package io.ib67.yuanban.story.debug

import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.Instance
import net.minestom.server.coordinate.Pos
import java.util.concurrent.ConcurrentHashMap

/** 用户名不在故事路由表中的玩家 */
internal class DebugVisitors : io.ib67.yuanban.story.runtime.VisitorPolicy {
    private val players = ConcurrentHashMap.newKeySet<Player>()
    @Volatile
    var lobby: Instance? = null

    override fun configure(event: AsyncPlayerConfigurationEvent) {
        val instance = checkNotNull(lobby) { "lobby has not been created" }
        val player = event.player
        players += player
        event.spawningInstance = instance
        player.respawnPoint = Pos(0.5, 64.0, 0.5)
        player.gameMode = GameMode.SPECTATOR
        player.sendMessage(Component.text(
            "story commands: /story list, /story run, /story pause, /story resume, /story step, " +
                "/story inspect, /story watch <故事> [玩家], /story lobby",
        ))
    }

    override fun isVisitor(player: Player): Boolean = player in players

    override fun evacuate(instance: Instance) {
        val target = checkNotNull(lobby) { "lobby has not been created" }
        players.filter { it.isOnline && it.instance === instance }.forEach {
            it.setInstance(target, Pos(0.5, 64.0, 0.5)).join()
        }
    }

    fun disconnected(player: Player) {
        players -= player
    }

    fun all(): Set<Player> = players.toSet()
}
