package io.ib67.yuanban.story.debug

import io.ib67.yuanban.bot.BotRuntime
import io.ib67.yuanban.bot.BotStatus
import io.ib67.yuanban.story.runtime.JoinedPlayer
import io.ib67.yuanban.story.runtime.PausePoint
import io.ib67.yuanban.story.runtime.StoryClock
import io.ib67.yuanban.story.runtime.StoryEnv
import io.ib67.yuanban.story.runtime.StoryMonitor
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import net.minestom.server.network.packet.server.common.PingPacket

/** Pause state and step requests for the debug server. */
internal class DebugPause(private val visitors: DebugVisitors) : PausePoint {
    private val runtimes = CopyOnWriteArrayList<BotRuntime>()
    private val owners = ConcurrentHashMap<StoryEnv, BotRuntime>()
    private val players = ConcurrentHashMap<StoryEnv, CopyOnWriteArrayList<JoinedPlayer>>()
    @Volatile private var requested = false
    @Volatile private var paused = false
    @Volatile private var steps = 0
    @Volatile private var lastTick = 0L
    private var botsAfterTick = false
    private var lastPingNanos = 0L
    private var visitorPingId = 0
    private var pauseStarted = 0L
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
        steps = 0
        botsAfterTick = false
        paused = false
        runtimes.forEach(BotRuntime::resumeTicking)
    }

    fun step(count: Int = 1) {
        require(count > 0) { "step count must be positive" }
        requested = true
        steps += count
    }

    fun isPaused(): Boolean = paused
    fun tick(): Long = lastTick

    override fun reached(tick: Long) {
        lastTick = tick
        overlayUpdate?.invoke()
        if (requested && !paused) {
            paused = true
            pauseStarted = System.nanoTime()
            lastPingNanos = 0L
            runtimes.forEach(BotRuntime::suspendTicking)
            awaitRuntimesSuspended()
        }
        if (!paused) return

        awaitStoryPlayers()
        while (paused) {
            if (botsAfterTick) {
                runtimes.flatMap { it.stepTicks(1) }.forEach { it.await(Duration.ofSeconds(10)) }
                botsAfterTick = false
            } else if (steps > 0) {
                steps--
                botsAfterTick = true
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
        if (pauseStarted != 0L) {
            clock?.addPaused(System.nanoTime() - pauseStarted)
            pauseStarted = 0L
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
