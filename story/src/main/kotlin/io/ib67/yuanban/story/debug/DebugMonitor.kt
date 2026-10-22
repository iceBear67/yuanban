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
import io.ib67.yuanban.story.runtime.StoryMonitor
import net.minestom.server.event.Event
import net.minestom.server.event.EventListener
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Exposes story runtime state to debug commands and visitors. */
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
        names[story] = story.javaClass.name
        println("story started: ${names[story]}")
        registerEventBreakpoints(story)
    }

    override fun runtimeCreated(story: StoryEnv, runtime: BotRuntime) {
        runtimes[story] = runtime
    }

    override fun playerJoining(story: StoryEnv, name: String, bot: Bot) {
        val specs = breakpoints.all().mapNotNull(::parse).filter { it.player == name }
        val observations = specs.filter { it.kind == Kind.OBSERVATION || it.kind == Kind.BLOCK }
        if (observations.isNotEmpty()) {
            registerObservationBreakpoints(story, name, bot, observations)
        }
        specs.filter { it.kind == Kind.PACKET || it.kind == Kind.SENT }.forEach { spec ->
            val packetClass = loadPacket(spec.argument, spec.kind == Kind.PACKET)
            val subscription = if (spec.kind == Kind.PACKET) {
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
        val spec = breakpoints.all().mapNotNull(::parse).firstOrNull { it.kind == Kind.STEP } ?: return
        println("breakpoint ${spec.number}: step started ${step.text}")
        awaitNext()
    }

    override fun tickChecked(story: StoryEnv, check: TickCheckRecord) {
        val spec = breakpoints.all().mapNotNull(::parse).firstOrNull { it.kind == Kind.MISMATCH } ?: return
        if (hasMismatch(check)) {
            println("breakpoint ${spec.number}: server and client results differ at tick ${check.tick}")
            pause.requestPause()
        }
    }

    fun setHold(value: Boolean) {
        hold = value
        if (!value) next()
    }

    fun next() {
        synchronized(nextLock) {
            nextWaiters.firstOrNull()?.let {
                nextWaiters.remove(it)
                it.complete(Unit)
            } ?: nextWaiters.add(CompletableFuture())
        }
    }

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

    private fun awaitNext() {
        val future = synchronized(nextLock) {
            val existing = nextWaiters.firstOrNull()
            if (existing != null) {
                nextWaiters.remove(existing)
                existing
            } else {
                CompletableFuture<Unit>().also(nextWaiters::add)
            }
        }
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
        specs: List<Spec>,
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
        breakpoints.all().mapNotNull(::parse).filter { it.kind == Kind.EVENT }.forEach { spec ->
            val type = loadEvent(spec.argument)
            @Suppress("UNCHECKED_CAST")
            val listener = EventListener.of(type as Class<Event>) { event ->
                println("breakpoint ${spec.number}: event ${event.javaClass.simpleName}: $event")
                pause.requestPause()
            }
            story.events.addListener(listener)
            eventListeners.computeIfAbsent(story) { CopyOnWriteArrayList() } += listener
        }
    }

    private fun hit(story: StoryEnv, spec: Spec, detail: String) {
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

    private fun matches(spec: Spec, observation: Observation): Boolean {
        if (spec.kind == Kind.OBSERVATION) return observation.javaClass.simpleName.equals(spec.argument, ignoreCase = true)
        if (spec.kind != Kind.BLOCK || observation !is Observation.BlockChanged) return false
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

    private fun parse(entry: Breakpoints.Entry): Spec? {
        val words = entry.text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) return null
        return when (words[0]) {
            "step" -> Spec(entry.number, null, Kind.STEP, "", emptyList())
            "mismatch" -> Spec(entry.number, null, Kind.MISMATCH, "", emptyList())
            "event" -> require(words.size == 2) { "event breakpoint requires an event class name" }.let { Spec(entry.number, null, Kind.EVENT, words[1], emptyList()) }
            else -> {
                require(words.size >= 3) { "player breakpoint requires a player name, kind, and argument: ${entry.text}" }
                val kind = when (words[1]) {
                    "observation" -> Kind.OBSERVATION
                    "packet" -> Kind.PACKET
                    "sent" -> Kind.SENT
                    "block" -> Kind.BLOCK
                    else -> throw IllegalArgumentException("unknown breakpoint kind: ${words[1]}")
                }
                Spec(entry.number, words[0], kind, words[2], words.drop(2))
            }
        }
    }

    private fun loadPacket(name: String, received: Boolean): Class<out MinecraftPacket> {
        val direction = if (received) "clientbound" else "serverbound"
        val candidates = listOf(
            "org.geysermc.mcprotocollib.protocol.packet.ingame.$direction.$name",
            "org.geysermc.mcprotocollib.protocol.packet.common.$direction.$name",
            "org.geysermc.mcprotocollib.protocol.packet.login.$direction.$name",
            "org.geysermc.mcprotocollib.protocol.packet.configuration.$direction.$name",
        )
        @Suppress("UNCHECKED_CAST")
        return candidates.asSequence().mapNotNull { runCatching { Class.forName(it) }.getOrNull() }
            .firstOrNull() as? Class<out MinecraftPacket>
            ?: throw IllegalArgumentException("packet class not found: $name")
    }

    private fun loadEvent(name: String): Class<*> = listOf(
        "net.minestom.server.event.$name",
        "net.minestom.server.event.player.$name",
        "net.minestom.server.event.entity.$name",
        "net.minestom.server.event.instance.$name",
        "net.minestom.server.event.inventory.$name",
        "net.minestom.server.event.server.$name",
    ).asSequence().mapNotNull { runCatching { Class.forName(it) }.getOrNull() }.firstOrNull()
        ?: throw IllegalArgumentException("event class not found: $name")

    private enum class Kind { OBSERVATION, PACKET, SENT, BLOCK, EVENT, STEP, MISMATCH }
    private data class Spec(
        val number: Int,
        val player: String?,
        val kind: Kind,
        val argument: String,
        val arguments: List<String>,
    )
}
