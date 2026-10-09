package io.ib67.yuanban.story.runtime

import io.ib67.yuanban.story.ServerSetup
import io.ib67.yuanban.story.StoryDefaults
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerProcess
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.property.ServerProperties
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/** 每个 tick 结束时的暂停点 */
internal fun interface PausePoint {
    /** 在 tick 调度线程上调用，参数为 tick 编号。返回之后 TickSchedulerThread 才开始下一个 tick */
    fun reached(tick: Long)
}

/** 故事时钟：单调的真实时间减去暂停点暂停服务端的累计时间，单位为纳秒 */
internal class StoryClock {
    private val paused = AtomicLong()

    fun now(): Long = System.nanoTime() - paused.get()

    /** 暂停点暂停服务端的时间不计入故事时钟 */
    fun addPaused(nanos: Long) {
        paused.addAndGet(nanos)
    }
}

/** tick 结束时的执行：ServerTickMonitorEvent 的监听器是 tick 中最后执行的代码，这个 tick 的全部数据包都已经交给各个连接 */
internal class TickEnd(private val pausePoint: PausePoint) {
    private val tasks = CopyOnWriteArrayList<TickTask>()

    @Volatile
    var tick: Long = 0
        private set

    fun register(owner: StoryEnvImpl, body: (Long) -> Unit): TickTask = TickTask(this, owner, body).also { tasks += it }

    fun remove(task: TickTask) {
        tasks.remove(task)
    }

    /** 在 tick 调度线程上 */
    fun run() {
        val tick = ++tick
        tasks.forEach { it.run(tick) }
        pausePoint.reached(tick)
    }
}

/** everyTickEnd 登记的任务；抛出的异常交给登记它的故事，这个任务随即关闭 */
internal class TickTask(
    private val tickEnd: TickEnd,
    private val owner: StoryEnvImpl,
    private val body: (Long) -> Unit,
) : AutoCloseable {
    @Volatile
    private var closed = false

    fun run(tick: Long) {
        if (closed) return
        try {
            body(tick)
        } catch (e: Throwable) {
            close()
            owner.fail(e)
        }
    }

    override fun close() {
        closed = true
        tickEnd.remove(this)
    }
}

/** 已经启动的服务端进程，以及故事共用的状态 */
internal class RunningServer(
    val process: ServerProcess,
    /** 服务端监听的回环地址与端口 */
    val address: InetSocketAddress,
    val owner: ServerOwner,
    val defaults: StoryDefaults,
    /** ServerSetup.configure 的返回值；没有配置 ServerSetup 时为 null */
    private val setup: Setup?,
    val visitors: VisitorPolicy,
    val router: JoinRouter,
    val tickEnd: TickEnd,
    val log: ServerExceptionLog,
    val running: MutableSet<StoryEnvImpl>,
    val clock: StoryClock,
) {
    class Setup(val type: KClass<out ServerSetup<*>>, val value: Any?)

    val monitors = CopyOnWriteArrayList<StoryMonitor>()

    fun <T : Any> setupResult(type: KClass<T>): T {
        val setup = checkNotNull(setup) { "没有配置 ServerSetup：在 junit-platform.properties 中设置 yuanban.story.setup" }
        val value = setup.value
        check(type.isInstance(value)) { "${setup.type.qualifiedName} 的返回值 $value 不属于 ${type.qualifiedName}" }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}

/** JVM 中唯一的服务端 */
internal object SharedServer {
    private val lock = ReentrantLock()
    private var server: RunningServer? = null
    private var failure: Throwable? = null

    /**
     * 由故事取得服务端，没有时创建；返回服务端与这一次是否创建了它。
     * setupClass 只在创建时调用：isolated 类的 @IsolatedJvm.setup，或者模块的配置参数给出的类
     */
    fun obtainForStory(owner: ServerOwner, setupClass: () -> KClass<out ServerSetup<*>>?): Pair<RunningServer, Boolean> =
        lock.withLock {
            val existing = server
            val (result, created) = if (existing != null) {
                existing to false
            } else {
                // 默认的暂停点直接返回
                create(owner, setupClass, 0, RejectVisitors) {} to true
            }
            if (MinecraftServer.process() !== result.process) {
                throw ExtensionConfigurationException("MinecraftServer.process() 被其它代码替换，服务端不再是故事共用的进程")
            }
            if (result.owner != owner) {
                throw ExtensionConfigurationException(
                    "这个 JVM 中的服务端属于${result.owner}，故事要求的服务端属于$owner：isolated 类需要单独的 JVM，" +
                        "使用 ./gradlew :模块:isolatedTest --tests 类名 运行",
                )
            }
            result to created
        }

    /**
     * 创建服务端；任何一步抛出异常时记下这个异常，之后的每一次请求都以它为原因失败。
     * setupClass 给出 ServerSetup 的类，没有配置时为 null
     */
    fun create(
        owner: ServerOwner,
        setupClass: () -> KClass<out ServerSetup<*>>?,
        port: Int,
        visitors: VisitorPolicy,
        pausePoint: PausePoint,
    ): RunningServer = lock.withLock {
        failure?.let { throw ExtensionConfigurationException("服务端创建失败", it) }
        check(server == null) { "JVM 中已经有服务端" }
        try {
            createUnchecked(owner, setupClass, port, visitors, pausePoint).also { server = it }
        } catch (e: Throwable) {
            failure = e
            throw e as? ExtensionConfigurationException ?: ExtensionConfigurationException("服务端创建失败", e)
        }
    }

    private fun createUnchecked(
        owner: ServerOwner,
        setupClassOf: () -> KClass<out ServerSetup<*>>?,
        port: Int,
        visitors: VisitorPolicy,
        pausePoint: PausePoint,
    ): RunningServer {
        // minestom.inside-test 关闭动态注册表的冻结，并让 ConnectionManager.updateWaitingPlayers 在 tick 中等待玩家出生完成
        if (ServerProperties.INSIDE_TEST.get()) {
            throw ExtensionConfigurationException("minestom.inside-test 必须为 false：它使测试服务端的行为与生产环境不同")
        }
        if (MinecraftServer.process() != null) {
            throw ExtensionConfigurationException("JVM 中已经有其它代码创建的 Minestom 进程（例如 net.minestom:testing 的 @EnvTest）")
        }
        val setupClass = setupClassOf()
        val setup = setupClass?.let(::instantiate)
        setup?.beforeInit()

        // bot 不支持加密，使用离线模式。这是 JVM 中第一次创建 Registries.vanilla()，物品原型绑定到这个进程的注册表
        val minecraftServer = MinecraftServer.init(Auth.Offline())
        val process = MinecraftServer.process()

        val running: MutableSet<StoryEnvImpl> = ConcurrentHashMap.newKeySet()
        val log = ServerExceptionLog(running)
        process.exception().setExceptionHandler(log::record)

        val router = JoinRouter(visitors)
        process.eventHandler().addChild(router.node)

        val tickEnd = TickEnd(pausePoint)
        process.eventHandler().addListener(ServerTickMonitorEvent::class.java) { tickEnd.run() }

        val defaults = StoryDefaults()
        val value = setup?.configure(process, defaults)
        defaults.freeze()

        val loopback = InetAddress.getLoopbackAddress()
        minecraftServer.start(InetSocketAddress(loopback, port))
        // 使用 IP 地址的字面值，bot 连接时不经过域名解析
        val address = InetSocketAddress(loopback.hostAddress, process.server().port)

        return RunningServer(
            process, address, owner, defaults,
            setupClass?.let { RunningServer.Setup(it, value) },
            visitors, router, tickEnd, log, running, StoryClock(),
        )
    }

    /** object 读取 objectInstance，其它类调用无参构造方法 */
    private fun instantiate(type: KClass<out ServerSetup<*>>): ServerSetup<*> =
        type.objectInstance ?: try {
            type.createInstance()
        } catch (e: IllegalArgumentException) {
            throw ExtensionConfigurationException("${type.qualifiedName} 既不是 object，也没有无参构造方法", e)
        }
}
