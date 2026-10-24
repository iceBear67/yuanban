package io.ib67.yuanban.story.runtime

import io.ib67.yuanban.bot.Bot
import io.ib67.yuanban.bot.BotOptions
import io.ib67.yuanban.bot.BotRuntime
import io.ib67.yuanban.bot.view.StateReader
import io.ib67.yuanban.story.StoryDefaults
import net.minestom.server.ServerProcess
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import java.net.InetSocketAddress
import kotlin.reflect.KClass
import kotlin.time.Duration

/** 一个故事的运行时，词汇通过它使用服务端与 bot */
interface StoryEnv {
    /** 类名与方法名：JUnit 配置了并发执行，可能同时有多个故事在运行，用它区分 */
    val name: String
    /** 故事所在的类或者外层类带有 @IsolatedJvm */
    val isolated: Boolean
    /** JVM 中的服务端进程，即 MinecraftServer.process() */
    val process: ServerProcess
    /** 服务端监听的回环地址与端口 */
    val address: InetSocketAddress
    /** 这个故事的 BotRuntime，第一次使用时创建，故事结束时关闭 */
    val runtime: BotRuntime
    val defaults: StoryDefaults
    /** ServerSetup.configure 的返回值；返回值不属于 type 时抛出 IllegalStateException */
    fun <T : Any> setup(type: KClass<T>): T

    /** 在下一个 tick 开始时于 tick 调度线程上执行 body，等待它执行完毕 */
    fun <T> onServer(body: () -> T): T
    /** 每个 tick 结束时（这个 tick 的数据包全部交给连接之后）在 tick 调度线程上执行 body，参数为 tick 编号；关闭返回的句柄之后不再执行，故事结束时自动关闭 */
    fun everyTickEnd(body: (tick: Long) -> Unit): AutoCloseable
    /** 每个 tick 结束时在 tick 调度线程上检查 condition，等到它成立 */
    fun awaitServer(timeout: Duration, condition: () -> Boolean)
    /** 等到服务端再结束 ticks 个 tick */
    fun awaitTicks(ticks: Int)
    /** 这个故事运行期间服务端记录过异常，或者事件过滤条件抛出过异常时，立即抛出 */
    fun checkServerExceptions()

    /** 创建一个没有生成器的 InstanceContainer，故事结束时注销 */
    fun createInstance(dimension: RegistryKey<DimensionType>): InstanceContainer

    /** 创建 bot，让它进入 instance 并出生在 spawn，等到 bot 发送 Player Loaded */
    fun join(
        name: String,
        instance: Instance,
        spawn: Pos,
        options: BotOptions.BotOptionsBuilder.() -> Unit = {},
        configuration: AsyncPlayerConfigurationEvent.() -> Unit = {},
    ): JoinedPlayer
    /** 以 previous 的用户名创建新的 bot；previous 的 bot 必须已经断开 */
    fun rejoin(previous: JoinedPlayer, instance: Instance, spawn: Pos): JoinedPlayer
    /** 修改这个玩家之后再次经过配置阶段时出生的 Instance 与位置 */
    fun route(player: JoinedPlayer, instance: Instance, spawn: Pos)
    /** 立即向 player 发送一个标记。bot 应用了它之前的全部数据包时，以这一刻的模型调用 onArrival；标记到达之前 bot 断开时调用 onLost。两者都在 bot 的调度线程上执行 */
    fun sendMark(player: JoinedPlayer, onArrival: (StateReader) -> Unit, onLost: () -> Unit)
    /** 等到 bot 应用了服务端在此之前发给这个玩家的全部数据包 */
    fun barrier(player: JoinedPlayer)

    /** 只接收与这个故事的 Instance、玩家有关的事件的节点，故事结束时移除 */
    val events: EventNode<Event>
    fun <E : Event> recordEvents(type: KClass<E>, filter: (E) -> Boolean): EventRecorder<E>
    /** 先开始监听，再在当前线程上执行 trigger，等到第一个满足 filter 的事件并返回它 */
    fun <E : Event> expectEvent(type: KClass<E>, filter: (E) -> Boolean, timeout: Duration, trigger: () -> Unit): E

    /** 故事结束时按照注册的相反顺序执行 */
    fun onClose(cleanup: () -> Unit)
}

interface JoinedPlayer {
    val username: String
    val bot: Bot
    /** 服务端中当前的 Player；Transfer 之后服务端创建新的 Player，这里返回新的对象 */
    val player: Player
}

interface EventRecorder<E : Event> : AutoCloseable {
    /** 到目前为止记录的事件，按照触发顺序 */
    val events: List<E>
    /** 停止记录。故事结束时自动停止 */
    override fun close()
}
