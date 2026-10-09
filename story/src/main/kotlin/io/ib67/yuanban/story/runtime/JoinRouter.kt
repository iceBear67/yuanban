package io.ib67.yuanban.story.runtime

import io.ib67.yuanban.bot.Bot
import io.ib67.yuanban.bot.BotOptions
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.Instance
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 路由表中的一项：一个用户名属于哪个故事，再次经过配置阶段时出生在哪里 */
internal class Route(
    val story: StoryEnvImpl,
    /** 故事中的名称，例如 alice */
    val name: String,
    @Volatile var instance: Instance,
    @Volatile var spawn: Pos,
    val options: BotOptions.BotOptionsBuilder.() -> Unit,
    val configuration: AsyncPlayerConfigurationEvent.() -> Unit,
) {
    /** 当前的 bot 与服务端 Player；第一次加入完成之前为 null */
    @Volatile
    var joined: JoinedPlayerImpl? = null

    /** 正在等待的加入：join 与 rejoin 在连接之前设置 */
    @Volatile
    var pending: CompletableFuture<Player>? = null

    fun arrived(player: Player) {
        val future = pending
        if (future != null) {
            pending = null
            future.complete(player)
        } else {
            checkNotNull(joined) { "${player.username} 再次经过配置阶段时没有正在等待的加入，也没有已经加入的 bot" }.player = player
        }
    }
}

/** 玩家加入时选择 Instance：按照用户名找到路由表中的登记项，用户名不在路由表中的玩家交给访客处理器 */
internal class JoinRouter(private val visitors: VisitorPolicy) {
    /** Minestom 按照优先级从小到大调用子节点，所以加入路由在其它监听器之后执行，出生的 Instance 由它最终决定 */
    val node: EventNode<Event> = EventNode.all("story-join-router").setPriority(Int.MAX_VALUE)
    private val routes = ConcurrentHashMap<String, Route>()

    init {
        node.addListener(AsyncPlayerConfigurationEvent::class.java, ::configure)
    }

    fun register(username: String, route: Route) {
        check(routes.putIfAbsent(username, route) == null) { "用户名 $username 已经在路由表中" }
    }

    fun route(username: String): Route = checkNotNull(routes[username]) { "用户名 $username 不在路由表中" }

    fun remove(username: String) {
        routes.remove(username)
    }

    private fun configure(event: AsyncPlayerConfigurationEvent) {
        val player = event.player
        val route = routes[player.username] ?: return visitors.configure(event)
        route.story.addPlayer(player)
        event.spawningInstance = route.instance
        player.respawnPoint = route.spawn
        route.configuration(event)
        route.arrived(player)
    }

    companion object {
        private val counter = AtomicLong()

        /** 用户名的序号：JVM 中递增的计数，以 36 进制写出，最多 5 位 */
        fun nextSuffix(): String {
            val suffix = counter.incrementAndGet().toString(36)
            check(suffix.length <= 5) { "用户名的序号超出 5 位" }
            return suffix
        }
    }
}

internal class JoinedPlayerImpl(
    override val username: String,
    override val bot: Bot,
    @Volatile override var player: Player,
    val marks: MarkObserver,
) : JoinedPlayer
