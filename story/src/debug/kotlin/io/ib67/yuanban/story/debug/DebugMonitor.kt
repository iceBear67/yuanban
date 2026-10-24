package io.ib67.yuanban.story.debug

import io.ib67.yuanban.bot.Bot
import io.ib67.yuanban.bot.BotRuntime
import io.ib67.yuanban.bot.observe.Observation
import io.ib67.yuanban.bot.observe.Observer
import io.ib67.yuanban.bot.observe.ObserverContext
import io.ib67.yuanban.bot.observe.ObserverHandle
import io.ib67.yuanban.bot.observe.Subscription
import io.ib67.yuanban.story.ItemResult
import io.ib67.yuanban.story.SideResult
import io.ib67.yuanban.story.StepRecord
import io.ib67.yuanban.story.TickCheckRecord
import io.ib67.yuanban.story.runtime.JoinedPlayer
import io.ib67.yuanban.story.runtime.StoryEnv
import io.ib67.yuanban.story.runtime.StoryEnvImpl
import io.ib67.yuanban.story.runtime.StoryMonitor
import net.minestom.server.event.Event
import net.minestom.server.event.EventListener
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** 把故事运行时的状态暴露给调试命令与访客 */
internal class DebugMonitor(private val pause: DebugPause, private val breakpoints: Breakpoints) : StoryMonitor {
    private val names = ConcurrentHashMap<StoryEnv, String>()
    private val runtimes = ConcurrentHashMap<StoryEnv, BotRuntime>()
    private val players = ConcurrentHashMap<StoryEnv, MutableList<JoinedPlayer>>()
    private val observers = ConcurrentHashMap<StoryEnv, MutableList<ObserverHandle<*>>>()
    private val eventListeners = ConcurrentHashMap<StoryEnv, MutableList<EventListener<*>>>()
    private val nextLock = Any()
    private val nextWaiters = CopyOnWriteArrayList<CompletableFuture<Unit>>()
    @Volatile private var hold = false

    override fun storyStarted(story: StoryEnv) {
        names[story] = story.name
        registerEventBreakpoints(story)
    }

    override fun runtimeCreated(story: StoryEnv, runtime: BotRuntime) {
        runtimes[story] = runtime
    }

    override fun playerJoining(story: StoryEnv, name: String, bot: Bot) {
        val specs = breakpoints.all().map { it.spec }.filter { it.player == name }
        val observations = specs.filter { it.kind == BreakpointKind.OBSERVATION || it.kind == BreakpointKind.BLOCK }
        if (observations.isNotEmpty()) {
            registerObservationBreakpoints(story, name, bot, observations)
        }
        specs.filter { it.kind == BreakpointKind.PACKET || it.kind == BreakpointKind.SENT }.forEach { spec ->
            @Suppress("UNCHECKED_CAST")
            val packetClass = spec.resolvedClass as Class<out MinecraftPacket>
            val subscription = if (spec.kind == BreakpointKind.PACKET) {
                Subscription.NONE.received(packetClass)
            } else {
                Subscription.NONE.sent(packetClass)
            }
            val handle = bot.observe(object : Observer {
                override fun subscription() = subscription

                override fun onObservation(observation: Observation, context: ObserverContext) {
                    val packet = when (observation) {
                        is Observation.PacketReceived -> observation.packet
                        is Observation.PacketSent -> observation.packet
                        else -> return
                    }
                    if (packet.javaClass == packetClass) {
                        hit(story, spec, "$name ${packet.javaClass.simpleName}")
                    }
                }
            })
            observers.computeIfAbsent(story) { CopyOnWriteArrayList() } += handle
        }
    }

    override fun playerJoined(story: StoryEnv, player: JoinedPlayer) {
        players.computeIfAbsent(story) { CopyOnWriteArrayList() } += player
    }

    override fun storyFinishing(story: StoryEnv, failure: Throwable?) {
        if (hold) awaitNext()
        println("story finished: ${names.remove(story)}${failure?.let { ", failed: $it" } ?: ", passed"}")
        runtimes.remove(story)
        players.remove(story)
        observers.remove(story)?.forEach(ObserverHandle<*>::close)
        eventListeners.remove(story)?.forEach { listener -> story.events.removeListener(listener) }
    }

    override fun stepStarted(story: StoryEnv, step: StepRecord) {
        val spec = breakpoints.all().map { it.spec }.firstOrNull { it.kind == BreakpointKind.STEP } ?: return
        println("breakpoint ${spec.number}: step started ${step.text}")
        awaitNext()
    }

    override fun tickChecked(story: StoryEnv, check: TickCheckRecord) {
        val spec = breakpoints.all().map { it.spec }.firstOrNull { it.kind == BreakpointKind.MISMATCH } ?: return
        if (hasMismatch(check)) {
            println("breakpoint ${spec.number}: server and client results differ at tick ${check.tick}")
            // 与 hit() 一致：立即暂停 bot 的 tick，否则 bot 会比服务端多执行一个 tick 才暂停
            runtimes[story]?.suspendTicking()
            pause.requestPause()
        }
    }

    fun setHold(value: Boolean) {
        hold = value
        if (!value) releaseAll()
    }

    /**
     * 唤醒最早的等待者；没有等待者时什么都不做
     *
     * @return 是否唤醒了一个等待者
     */
    fun next(): Boolean = synchronized(nextLock) {
        val waiting = nextWaiters.firstOrNull() ?: return@synchronized false
        nextWaiters.remove(waiting)
        waiting.complete(Unit)
        true
    }

    /** 唤醒全部等待者：hold 关闭或者故事被中止时，不需要逐个用 next 确认 */
    fun releaseAll() {
        synchronized(nextLock) {
            nextWaiters.forEach { it.complete(Unit) }
            nextWaiters.clear()
        }
    }

    /** 当前停在断点上、等待 next 的故事数量，供测试判断"故事已经停下"而不必靠 sleep */
    fun pendingNextCount(): Int = synchronized(nextLock) { nextWaiters.size }

    fun status(): String = buildString {
        append("paused=").append(pause.isPaused()).append(", tick=").append(pause.tick()).append('\n')
        names.values.forEach { append(it).append('\n') }
        breakpoints.all().forEach { append("breakpoint ").append(it.number).append(": ").append(it.text).append('\n') }
    }

    fun inspect(name: String): String {
        val match = players.values.asSequence().flatMap { it.asSequence() }
            .firstOrNull { it.username == name || it.username.startsWith("${name}_") }
            ?: return "player not found: $name"
        val player = match.player
        val server = "position=${player.position}, health=${player.health}, food=${player.food}, " +
            "gameMode=${player.gameMode}, online=${player.isOnline}"
        val client = match.bot.inspect { state ->
            "status=${state.status()}, ticks=${state.ticks()}, loaded=${state.loaded()}, " +
                "world=${state.world()?.dimension()?.value()}, player=${state.world()?.let { state.player().position() }}"
        }
        return "$name\nserver: $server\nclient: $client"
    }

    /** 这个故事中已经加入的玩家；没有加入任何玩家时为空 */
    fun playersOf(story: StoryEnv): List<JoinedPlayer> = players[story]?.toList() ?: emptyList()

    /** 按名称查找正在运行的故事 */
    fun storyNamed(name: String): StoryEnv? = names.keys.firstOrNull { it.name == name }

    /** 正在运行的全部故事名称 */
    fun runningStories(): List<String> = names.values.toList()

    /** 全部正在运行的故事中已经加入的玩家用户名 */
    fun allPlayerNames(): List<String> = players.values.flatMap { it.map(JoinedPlayer::username) }

    /**
     * 中止全部正在运行的故事：通过 StoryEnvImpl 的 fail 交给每个故事一个说明「被调试命令中止」的异常，
     * 并断开它的全部 bot（两者都促使故事线程尽快结束：fail 的异常在下一次 checkServerExceptions 时抛出，
     * 断开 bot 让正在等待 bot 的调用立即失败）
     */
    fun stopAll() {
        names.keys.toList().forEach { story ->
            (story as StoryEnvImpl).fail(IllegalStateException("被调试命令中止"))
            runtimes[story]?.bots()?.forEach { it.disconnect() }
        }
    }

    private fun awaitNext() {
        // 每次调用都新建一个 future：如果取走已有的 future，两个故事同时停在断点上时，
        // 后一个会取走前一个登记的 future，列表变空，next/hold off/stop 都唤醒不了，永久卡住
        val future = CompletableFuture<Unit>()
        synchronized(nextLock) { nextWaiters.add(future) }
        try {
            future.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("interrupted while waiting for story next", e)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw IllegalStateException("story next failed", e.cause)
        }
    }

    private fun registerObservationBreakpoints(
        story: StoryEnv,
        name: String,
        bot: Bot,
        specs: List<BreakpointSpec>,
    ) {
        val handle = bot.observe(object : Observer {
            override fun subscription() = Subscription.of(Observation::class.java)

            override fun onObservation(observation: Observation, context: ObserverContext) {
                val spec = specs.firstOrNull { matches(it, observation) } ?: return
                hit(story, spec, "$name ${observation.javaClass.simpleName}, ${snapshot(context)}")
            }
        })
        observers.computeIfAbsent(story) { CopyOnWriteArrayList() } += handle
    }

    private fun registerEventBreakpoints(story: StoryEnv) {
        breakpoints.all().map { it.spec }.filter { it.kind == BreakpointKind.EVENT }.forEach { spec ->
            @Suppress("UNCHECKED_CAST")
            val type = spec.resolvedClass as Class<Event>
            val listener = EventListener.of(type) { event ->
                println("breakpoint ${spec.number}: event ${event.javaClass.simpleName}: $event")
                pause.requestPause()
            }
            story.events.addListener(listener)
            eventListeners.computeIfAbsent(story) { CopyOnWriteArrayList() } += listener
        }
    }

    private fun hit(story: StoryEnv, spec: BreakpointSpec, detail: String) {
        println("breakpoint ${spec.number}: $detail")
        runtimes[story]?.suspendTicking()
        pause.requestPause()
    }

    private fun snapshot(context: ObserverContext): String {
        val state = context.state()
        val world = state.world()
        val player = world?.let { state.player() }
        return "ticks=${state.ticks()}, loaded=${state.loaded()}, world=${world?.dimension()?.value()}, " +
            "position=${player?.position()}"
    }

    private fun matches(spec: BreakpointSpec, observation: Observation): Boolean {
        if (spec.kind == BreakpointKind.OBSERVATION) {
            return observation.javaClass.simpleName.equals(spec.argument, ignoreCase = true)
        }
        if (spec.kind != BreakpointKind.BLOCK || observation !is Observation.BlockChanged) return false
        val coordinates = spec.arguments.mapNotNull(String::toDoubleOrNull)
        return coordinates.size == 3 && coordinates.zip(listOf(observation.pos.x(), observation.pos.y(), observation.pos.z()))
            .all { (expected, actual) -> expected == actual }
    }

    private fun hasMismatch(check: TickCheckRecord): Boolean = check.items.any { item: ItemResult ->
        val server = item.server as? SideResult.Checked ?: return@any false
        item.clients.any { (_, client) ->
            client is SideResult.Checked && (client.value != server.value || client.passed != server.passed)
        }
    }
}
