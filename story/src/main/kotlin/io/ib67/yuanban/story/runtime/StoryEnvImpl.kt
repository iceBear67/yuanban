package io.ib67.yuanban.story.runtime

import io.ib67.yuanban.bot.BotOptions
import io.ib67.yuanban.bot.BotRuntime
import io.ib67.yuanban.bot.BotStatus
import io.ib67.yuanban.bot.BotTimeoutException
import io.ib67.yuanban.bot.RuntimeOptions
import io.ib67.yuanban.bot.transport.TcpTransport
import io.ib67.yuanban.bot.transport.Transport
import io.ib67.yuanban.bot.view.StateReader
import io.ib67.yuanban.story.StepFailure
import io.ib67.yuanban.story.StoryDefaults
import io.ib67.yuanban.story.StoryFailure
import io.ib67.yuanban.story.StoryScope
import io.ib67.yuanban.story.TickCheckRecord
import io.ib67.yuanban.story.Transcript
import io.ib67.yuanban.story.formatDuration
import io.ib67.yuanban.story.plain
import net.minestom.server.ServerProcess
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventListener
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.trait.EntityEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.network.packet.server.common.PingPacket
import net.minestom.server.registry.RegistryKey
import net.minestom.server.thread.MinestomThread
import net.minestom.server.world.DimensionType
import org.opentest4j.AssertionFailedError
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** 一个故事的运行时。线程安全：simultaneously 的各个分支同时使用它 */
internal class StoryEnvImpl(
    val server: RunningServer,
    testClass: Class<*>,
    methodName: String,
    override val isolated: Boolean,
) : StoryEnv {
    /** 类名与方法名，用于服务端异常的运行故事名单 */
    val name = "${testClass.name}.$methodName"
    override val process: ServerProcess get() = server.process
    override val address: InetSocketAddress get() = server.address
    override val defaults: StoryDefaults get() = server.defaults
    val clock: StoryClock get() = server.clock
    val transcript = Transcript("$methodName（${testClass.simpleName}）") { server.clock.now() }

    private val instances: MutableSet<Instance> = ConcurrentHashMap.newKeySet()
    private val players: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    private val joinedPlayers = CopyOnWriteArrayList<JoinedPlayerImpl>()
    private val usernames = CopyOnWriteArrayList<String>()
    private val tickTasks = CopyOnWriteArrayList<TickTask>()
    private val closeActions = CopyOnWriteArrayList<() -> Unit>()
    private val recorders = CopyOnWriteArrayList<AutoCloseable>()
    private val pendingErrors = ConcurrentLinkedQueue<Throwable>()
    private val reportedServerErrors: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val storyCalled = AtomicBoolean()

    @Volatile
    private var runtimeInstance: BotRuntime? = null

    override val events: EventNode<Event> = EventNode.event("story:$name", EventFilter.ALL, ::accepts)

    override val runtime: BotRuntime
        get() = runtimeInstance ?: synchronized(this) {
            runtimeInstance ?: BotRuntime.create(RuntimeOptions.builder().waitPump(::checkServerExceptions).build()).also { runtime ->
                runtimeInstance = runtime
                server.monitors.forEach { it.runtimeCreated(this, runtime) }
            }
        }

    // 故事的开始与结束

    /** 在 beforeEach 中：加入事件节点，登记为运行中的故事 */
    fun start() {
        process.eventHandler().addChild(events)
        server.running += this
    }

    val wasStoryCalled: Boolean get() = storyCalled.get()

    /** story { } 的入口 */
    fun runStory(body: StoryScope.() -> Unit) {
        check(storyCalled.compareAndSet(false, true)) { "story { } 在一个 @Story 方法中只能调用一次" }
        val scope = StoryScope(this)
        try {
            server.monitors.forEach { it.storyStarted(this) }
            scope.body()
        } catch (e: Throwable) {
            throw StoryFailure(failureReport(scope), e)
        }
    }

    /**
     * 故事结束时的清理。每一步都执行，即使之前的步骤失败；各步骤的错误按照发生顺序收集，
     * 以 StoryFailure 抛出第一个错误，其余通过 addSuppressed 附加到它上面
     */
    fun finish(failure: Throwable?) {
        val errors = mutableListOf<Throwable>()
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                errors += e
            }
        }
        attempt { server.monitors.forEach { it.storyFinishing(this, failure) } }
        // 1. onClose 的任务，然后是 everyTickEnd 的任务
        closeActions.reversed().forEach(::attempt)
        tickTasks.forEach { attempt(it::close) }
        // 2. 断开全部 bot，等待调度线程退出
        runtimeInstance?.let { attempt(it::close) }
        // 3. 等待这个故事的每个 Player 都已离线
        attempt(::awaitPlayersOffline)
        // 4. 路由表
        usernames.forEach(server.router::remove)
        // 5. 把访客移出这个故事的 Instance，然后注销它
        instances.forEach { instance ->
            attempt {
                onServer(defaults.timeout, checkExceptions = false) {
                    server.visitors.evacuate(instance)
                    process.instance().unregisterInstance(instance)
                }
            }
        }
        // 6. 事件节点与 EventRecorder
        attempt { process.eventHandler().removeChild(events) }
        recorders.forEach { attempt(it::close) }
        // 7. 运行中的故事
        server.running -= this
        // 8. 服务端异常、事件过滤条件等交给这个故事的异常、bot 的异常
        server.log.involving(this).filter { reportedServerErrors.add(it.seq) }.forEach { errors += serverError(it) }
        generateSequence { pendingErrors.poll() }.forEach { errors += it }
        joinedPlayers.forEach { joined ->
            joined.bot.failure()?.let { errors += IllegalStateException("${joined.username} 的 bot 因异常结束", it) }
        }
        // 9. 故事记录
        val record = transcript.render()
        println(record)
        if (errors.isNotEmpty()) {
            val first = StoryFailure("${record}故事结束时的清理发现错误：${errors.first()}", errors.first())
            errors.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    private fun awaitPlayersOffline() {
        val offline = CompletableFuture<Unit>()
        server.tickEnd.register(this) { if (players.none { it.isOnline }) offline.complete(Unit) }.use {
            waitFor(offline, defaults.timeout, checkExceptions = false) {
                IllegalStateException(
                    "故事的玩家没有在 ${formatDuration(defaults.timeout)}内离线：" +
                        players.filter { it.isOnline }.joinToString("，") { it.username },
                )
            }
        }
    }

    // 异常

    /** 交给这个故事的异常：事件过滤条件、标记的回调、everyTickEnd 的任务抛出的异常，在下一个检查点抛出 */
    fun fail(error: Throwable) {
        pendingErrors += error
    }

    override fun checkServerExceptions() {
        pendingErrors.poll()?.let { throw it }
        val entry = server.log.involving(this).firstOrNull { reportedServerErrors.add(it.seq) } ?: return
        throw serverError(entry)
    }

    private fun serverError(entry: ServerExceptionLog.Entry) =
        AssertionFailedError("服务端记录了异常 ${entry.describe()}", entry.error)

    // 等待

    fun requireNotServerThread() {
        check(Thread.currentThread() !is MinestomThread) {
            "不能在 Minestom 的服务端线程上调用会等待的方法：这个线程会等待一个被自己阻塞的 tick"
        }
    }

    /**
     * 在当前线程上等待 future，每 50 毫秒检查一次服务端异常；timeout 按照故事时钟计算，为 null 时不限时。
     * future 的异常原样抛出
     */
    fun <T> waitFor(
        future: CompletableFuture<T>,
        timeout: Duration?,
        checkExceptions: Boolean = true,
        onTimeout: () -> Throwable,
    ): T {
        requireNotServerThread()
        val deadline = timeout?.let { clock.now() + it.inWholeNanoseconds }
        while (true) {
            try {
                return future.get(50, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
            } catch (e: ExecutionException) {
                throw e.cause!!
            }
            if (checkExceptions) checkServerExceptions()
            if (deadline != null && clock.now() >= deadline) throw onTimeout()
        }
    }

    /** 等到故事时钟经过 duration，期间每 50 毫秒检查一次服务端异常 */
    fun sleep(duration: Duration) {
        requireNotServerThread()
        val deadline = clock.now() + duration.inWholeNanoseconds
        while (true) {
            val remaining = deadline - clock.now()
            if (remaining <= 0) return
            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(remaining).coerceIn(1, 50))
            checkServerExceptions()
        }
    }

    // 服务端线程上的执行

    override fun <T> onServer(body: () -> T): T = onServer(null, checkExceptions = true, body)

    fun <T> onServer(timeout: Duration?, checkExceptions: Boolean, body: () -> T): T {
        requireNotServerThread()
        val result = CompletableFuture<T>()
        process.scheduler().scheduleNextTick {
            try {
                result.complete(body())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        return waitFor(result, timeout, checkExceptions) { IllegalStateException("服务端没有在 ${timeout?.let(::formatDuration)}内执行代码") }
    }

    /** 这个故事登记的 tick 结束时的任务，故事结束时关闭 */
    fun storyTickTask(body: (Long) -> Unit): TickTask = server.tickEnd.register(this, body).also { tickTasks += it }

    override fun everyTickEnd(body: (tick: Long) -> Unit): AutoCloseable = storyTickTask(body)

    override fun awaitServer(timeout: Duration, condition: () -> Boolean) {
        val satisfied = CompletableFuture<Unit>()
        val task = storyTickTask {
            if (satisfied.isDone) return@storyTickTask
            try {
                if (condition()) satisfied.complete(Unit)
            } catch (e: Throwable) {
                satisfied.completeExceptionally(e)
            }
        }
        task.use { waitFor(satisfied, timeout) { StepFailure("服务端的条件没有在 ${formatDuration(timeout)}内成立") } }
    }

    override fun awaitTicks(ticks: Int) {
        require(ticks >= 0) { "tick 数不能为负数：$ticks" }
        if (ticks == 0) return
        val done = CompletableFuture<Unit>()
        var counted = 0
        storyTickTask { if (++counted >= ticks) done.complete(Unit) }.use {
            waitFor(done, null) { IllegalStateException("没有时限的等待不会超时") }
        }
    }

    // 世界与玩家

    override fun <T : Any> setup(type: KClass<T>): T = server.setupResult(type)

    override fun createInstance(dimension: RegistryKey<DimensionType>): InstanceContainer = onServer {
        process.instance().createInstanceContainer(dimension).also { instances += it }
    }

    fun addPlayer(player: Player) {
        players += player
    }

    override fun join(
        name: String,
        instance: Instance,
        spawn: Pos,
        options: BotOptions.BotOptionsBuilder.() -> Unit,
        configuration: AsyncPlayerConfigurationEvent.() -> Unit,
    ): JoinedPlayer {
        requireNotServerThread()
        require(NAME.matches(name)) { "玩家的名称只能包含字母、数字与下划线，长度为 1 到 10：$name" }
        require(instance in instances) { "$instance 不是这个故事创建的 Instance" }
        val username = "${name}_${JoinRouter.nextSuffix()}"
        val route = Route(this, name, instance, spawn, options, configuration)
        server.router.register(username, route)
        usernames += username
        return connect(route, username)
    }

    override fun rejoin(previous: JoinedPlayer, instance: Instance, spawn: Pos): JoinedPlayer {
        requireNotServerThread()
        check(previous.bot.status() == BotStatus.DISCONNECTED) { "${previous.username} 的 bot 还没有断开" }
        require(instance in instances) { "$instance 不是这个故事创建的 Instance" }
        val route = server.router.route(previous.username)
        route.instance = instance
        route.spawn = spawn
        return connect(route, previous.username)
    }

    override fun route(player: JoinedPlayer, instance: Instance, spawn: Pos) {
        require(instance in instances) { "$instance 不是这个故事创建的 Instance" }
        val route = server.router.route(player.username)
        route.instance = instance
        route.spawn = spawn
    }

    private fun connect(route: Route, username: String): JoinedPlayerImpl {
        val arrived = CompletableFuture<Player>()
        route.pending = arrived
        val builder = BotOptions.builder()
        route.options(builder)
        builder.username(username)
            .transport(TcpTransport(address.hostString, address.port))
            .transportResolver(::resolveTransport)
        val bot = runtime.connect(builder.build())
        val marks = MarkObserver(this)
        bot.observe(marks)
        server.monitors.forEach { it.playerJoining(this, route.name, bot) }
        try {
            val timeout = defaults.timeout
            val loaded = try {
                bot.loaded().await(timeout.toJavaDuration())
            } catch (e: BotTimeoutException) {
                throw StepFailure("$username 没有在 ${formatDuration(timeout)}内发送 Player Loaded", e)
            }
            if (!loaded) throw StepFailure("$username 等待 Player Loaded 的动作被取消")
            val player = waitFor(arrived, timeout) { StepFailure("$username 没有在 ${formatDuration(timeout)}内经过服务端的配置阶段") }
            val joined = JoinedPlayerImpl(username, bot, player, marks)
            route.joined = joined
            joinedPlayers += joined
            val actual = onServer { player.instance }
            if (actual !== route.instance) {
                throw StepFailure("$username 加入之后位于 $actual，路由表中的 Instance 为 ${route.instance}")
            }
            return joined
        } catch (e: StepFailure) {
            // 加入以 StepFailure 失败时断开 bot：attempt 捕获这个失败之后，这个 bot 不会再进入故事的世界
            bot.disconnect()
            throw e
        }
    }

    /** Transfer 的目标只能是这个服务端 */
    private fun resolveTransport(host: String, port: Int): Transport {
        require(host == address.hostString && port == address.port) {
            "Transfer 的目标 $host:$port 不是测试服务端 ${address.hostString}:${address.port}"
        }
        return TcpTransport(host, port)
    }

    // 标记与同步点

    override fun sendMark(player: JoinedPlayer, onArrival: (StateReader) -> Unit, onLost: () -> Unit) {
        val joined = player as JoinedPlayerImpl
        val id = MarkObserver.nextId()
        if (!joined.marks.register(id, onArrival, onLost)) {
            onLost()
            return
        }
        joined.player.sendPacket(PingPacket(id))
    }

    override fun barrier(player: JoinedPlayer) {
        requireNotServerThread()
        val arrived = CompletableFuture<Unit>()
        val sent = AtomicBoolean()
        // 标记在调用之后第一次 tick 结束时发送，排在这个 tick 的 flush 之后
        val task = storyTickTask {
            if (!sent.compareAndSet(false, true)) return@storyTickTask
            sendMark(player, { arrived.complete(Unit) }, {
                arrived.completeExceptionally(StepFailure("${player.username} 的同步点的标记丢失：${disconnectText(player)}"))
            })
        }
        task.use {
            waitFor(arrived, defaults.timeout) {
                StepFailure("${player.username} 的同步点没有在 ${formatDuration(defaults.timeout)}内完成")
            }
        }
    }

    private fun disconnectText(player: JoinedPlayer): String =
        listOfNotNull(
            player.bot.disconnectReason()?.let { "bot 已经断开：${plain(it)}" },
            player.bot.failure()?.let { "bot 因异常结束：$it" },
        ).joinToString("；").ifEmpty { "bot 已经断开" }

    // 事件

    /** 事件节点的条件：只接收与这个故事的玩家、Instance 有关的事件 */
    private fun accepts(event: Event): Boolean {
        if (event is PlayerEvent && server.visitors.isVisitor(event.player)) return false
        if (event is EntityEvent && (event.entity as? Player)?.let(server.visitors::isVisitor) == true) return false
        return when (event) {
            is PlayerEvent -> event.player in players
            is InstanceEvent -> event.instance in instances
            is EntityEvent -> event.entity.instance?.let { it in instances } ?: false
            else -> false
        }
    }

    override fun <E : Event> recordEvents(type: KClass<E>, filter: (E) -> Boolean): EventRecorder<E> {
        val recorded = Collections.synchronizedList(mutableListOf<E>())
        val listener = EventListener.of(type.java) { event ->
            try {
                if (filter(event)) recorded += event
            } catch (e: Throwable) {
                fail(e)
            }
        }
        events.addListener(listener)
        val recorder = object : EventRecorder<E> {
            override val events: List<E> get() = synchronized(recorded) { recorded.toList() }

            override fun close() {
                this@StoryEnvImpl.events.removeListener(listener)
            }
        }
        recorders += recorder
        return recorder
    }

    override fun <E : Event> expectEvent(type: KClass<E>, filter: (E) -> Boolean, timeout: Duration, trigger: () -> Unit): E {
        requireNotServerThread()
        val first = CompletableFuture<E>()
        val listener = EventListener.of(type.java) { event ->
            try {
                if (!first.isDone && filter(event)) first.complete(event)
            } catch (e: Throwable) {
                fail(e)
            }
        }
        events.addListener(listener)
        try {
            trigger()
            return waitFor(first, timeout) { StepFailure("没有在 ${formatDuration(timeout)}内等到 ${type.simpleName}") }
        } finally {
            events.removeListener(listener)
        }
    }

    override fun onClose(cleanup: () -> Unit) {
        closeActions += cleanup
    }

    // 故事监听器与报告

    fun tickChecked(record: TickCheckRecord) {
        server.monitors.forEach {
            try {
                it.tickChecked(this, record)
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    /** 失败报告：故事记录、失败时的状态、故事期间服务端记录的异常 */
    private fun failureReport(scope: StoryScope): String = buildString {
        append(transcript.render())
        append("失败时的状态：\n")
        val actors = scope.actors
        if (actors.isEmpty()) append("  没有玩家\n")
        val serverStates = try {
            onServer(defaults.timeout, checkExceptions = false) { actors.map { it.serverState() } }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            actors.map { "无法读取：线程被中断" }
        } catch (e: Throwable) {
            actors.map { "无法读取：$e" }
        }
        actors.forEachIndexed { index, actor ->
            append("  ").append(actor.name).append('（').append(actor.username).append("）\n")
            append("    服务端：").append(serverStates[index]).append('\n')
            val client = try {
                actor.bot.inspect(actor::clientState)
            } catch (e: Throwable) {
                listOf("无法读取：$e")
            }
            client.forEachIndexed { line, text -> append(if (line == 0) "    客户端：" else "            ").append(text).append('\n') }
            if (actor.bot.status() == BotStatus.DISCONNECTED) append("    连接已经结束：").append(actor.disconnectText()).append('\n')
        }
        val serverErrors = server.log.involving(this@StoryEnvImpl)
        append("故事期间服务端记录的异常：")
        if (serverErrors.isEmpty()) append("无\n") else serverErrors.forEach { append("\n  ").append(it.describe()) }
    }

    private companion object {
        val NAME = Regex("[A-Za-z0-9_]{1,10}")
    }
}
