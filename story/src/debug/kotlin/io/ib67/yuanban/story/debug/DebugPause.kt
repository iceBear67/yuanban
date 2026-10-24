package io.ib67.yuanban.story.debug

import io.ib67.yuanban.bot.Action
import io.ib67.yuanban.bot.BotRuntime
import io.ib67.yuanban.bot.BotStatus
import io.ib67.yuanban.story.runtime.JoinedPlayer
import io.ib67.yuanban.story.runtime.PausePoint
import io.ib67.yuanban.story.runtime.StoryClock
import io.ib67.yuanban.story.runtime.StoryEnv
import io.ib67.yuanban.story.runtime.StoryMonitor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import net.minestom.server.network.packet.server.common.PingPacket

/** 调试服务端的暂停状态与步进请求 */
internal class DebugPause(private val visitors: DebugVisitors) : PausePoint {
    private val runtimes = CopyOnWriteArrayList<BotRuntime>()
    private val owners = ConcurrentHashMap<StoryEnv, BotRuntime>()
    private val players = ConcurrentHashMap<StoryEnv, CopyOnWriteArrayList<JoinedPlayer>>()
    @Volatile private var requested = false
    @Volatile private var paused = false
    private val steps = AtomicInteger()
    @Volatile private var lastTick = 0L
    private var botsAfterTick = false
    private var lastPingNanos = 0L
    private var visitorPingId = 0
    @Volatile private var clock: StoryClock? = null
    @Volatile private var overlayUpdate: (() -> Unit)? = null

    fun attach(clock: StoryClock) {
        this.clock = clock
    }

    fun attachOverlay(update: () -> Unit) {
        overlayUpdate = update
    }

    fun monitor(): StoryMonitor = object : StoryMonitor {
        override fun runtimeCreated(story: StoryEnv, runtime: BotRuntime) {
            runtimes += runtime
            owners[story] = runtime
            if (paused) runtime.suspendTicking()
        }

        override fun playerJoined(story: StoryEnv, player: JoinedPlayer) {
            players.computeIfAbsent(story) { CopyOnWriteArrayList() } += player
        }

        override fun storyFinishing(story: StoryEnv, failure: Throwable?) {
            owners.remove(story)?.let { runtimes -= it }
            players.remove(story)
        }
    }

    fun requestPause() {
        requested = true
    }

    fun resume() {
        requested = false
        steps.set(0)
        botsAfterTick = false
        paused = false
        runtimes.forEach(BotRuntime::resumeTicking)
    }

    fun step(count: Int = 1) {
        require(count > 0) { "step count must be positive" }
        requested = true
        steps.addAndGet(count)
    }

    fun isPaused(): Boolean = paused
    fun tick(): Long = lastTick

    override fun reached(tick: Long) {
        lastTick = tick
        overlayUpdate?.invoke()
        val startingPause = requested && !paused
        if (startingPause) {
            paused = true
            lastPingNanos = 0L
        }
        if (!paused) return
        // 从这里开始到方法返回（无论是正常返回还是抛出异常）之间，故事时钟都处于暂停状态；
        // try/finally 保证两者成对出现，否则异常会让时钟卡在暂停状态，之后的 waitFor 都不会超时
        clock?.startPause()
        try {
            if (startingPause) {
                runtimes.forEach(BotRuntime::suspendTicking)
                awaitRuntimesSuspended()
            }
            awaitStoryPlayers()
            while (paused) {
                if (botsAfterTick) {
                    stepPlayingBots().forEach { it.future().get(10, TimeUnit.SECONDS) }
                    botsAfterTick = false
                } else if (steps.get() > 0) {
                    steps.decrementAndGet()
                    botsAfterTick = true
                    // 让服务端执行一个 tick：返回之后 finally 结束这次暂停，下一次进入 reached 时重新开始
                    return
                }
                val now = System.nanoTime()
                visitors.all().forEach { if (it.isOnline) it.tick(now) }
                if (now - lastPingNanos >= KEEP_ALIVE_NANOS) {
                    lastPingNanos = now
                    visitors.all().forEach { if (it.isOnline) it.sendPacket(PingPacket(nextVisitorPing())) }
                    awaitStoryPlayers()
                }
                overlayUpdate?.invoke()
                LockSupport.parkNanos(PAUSE_INTERVAL_NANOS)
            }
        } finally {
            clock?.endPause()
        }
    }

    /**
     * 让每个 bot 执行一个 tick；status 不是 PLAY 的 bot 不会被 BotInstance 的 tick 推进
     * （tickDue 只在 PLAY 状态执行），取消它们的步进动作，否则会在配置阶段积累、进入 PLAY 后连续执行。
     * 只返回 PLAY 状态 bot 的动作，调用方只需要等待这些
     */
    private fun stepPlayingBots(): List<Action> = runtimes.flatMap { runtime ->
        val notEnded = runtime.bots().filter { it.status() != BotStatus.DISCONNECTED }
        val actions = runtime.stepTicks(1)
        check(notEnded.size == actions.size) {
            "stepTicks 返回了 ${actions.size} 个动作，但有 ${notEnded.size} 个未结束的 bot：" +
                "两次调用之间有 bot 断开或者创建"
        }
        notEnded.zip(actions).mapNotNull { (bot, action) ->
            if (bot.status() == BotStatus.PLAY) action else {
                action.cancel()
                null
            }
        }
    }

    private fun awaitRuntimesSuspended() {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_NANOS
        while (true) {
            val suspended = runtimes.all { runtime ->
                runtime.bots().all { bot ->
                    bot.status() == BotStatus.DISCONNECTED || bot.inspect { state -> state.tickingSuspended() }
                }
            }
            if (suspended) return
            if (System.nanoTime() >= deadline) {
                throw IllegalStateException("story bots did not suspend ticking after the pause request")
            }
            LockSupport.parkNanos(1_000_000L)
        }
    }

    private fun awaitStoryPlayers() {
        val current = players.entries.flatMap { (story, joined) -> joined.map { story to it } }
            .filter { (_, player) -> player.bot.status() != BotStatus.DISCONNECTED }
        if (current.isEmpty()) return
        val arrivals = current.map { (story, joined) ->
            CompletableFuture<Unit>().also { arrived ->
                story.sendMark(
                    joined,
                    { arrived.complete(Unit) },
                    { arrived.completeExceptionally(IllegalStateException("bot ${joined.username} disconnected")) },
                )
            }
        }
        try {
            CompletableFuture.allOf(*arrivals.toTypedArray()).get(AWAIT_TIMEOUT_NANOS, TimeUnit.NANOSECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw IllegalStateException("story bots did not apply all server packets before the pause", e.cause)
        } catch (e: java.util.concurrent.TimeoutException) {
            throw IllegalStateException("story bots did not apply server packets during the pause", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("interrupted while waiting for story bot synchronization marks", e)
        }
    }

    private fun nextVisitorPing(): Int {
        visitorPingId = if (visitorPingId == Int.MAX_VALUE) 1 else visitorPingId + 1
        return visitorPingId
    }

    private companion object {
        const val PAUSE_INTERVAL_NANOS = 50_000_000L
        const val KEEP_ALIVE_NANOS = 5_000_000_000L
        const val AWAIT_TIMEOUT_NANOS = 10_000_000_000L
    }
}
