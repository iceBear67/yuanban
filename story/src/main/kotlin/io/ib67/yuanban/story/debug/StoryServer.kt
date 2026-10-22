package io.ib67.yuanban.story.debug

import io.ib67.yuanban.story.IsolatedJvm
import io.ib67.yuanban.story.ModuleSetup
import io.ib67.yuanban.story.ServerSetup
import io.ib67.yuanban.story.runtime.ServerOwner
import io.ib67.yuanban.story.runtime.SharedServer
import io.ib67.yuanban.story.runtime.RunningServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.Generator
import net.minestom.server.world.DimensionType
import net.minestom.server.tag.TagHandler
import net.kyori.adventure.identity.Identity
import org.junit.platform.launcher.core.LauncherFactory
import java.net.InetSocketAddress
import java.util.Properties
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/** Debug server for running story tests in the game. */
object StoryServer {
    @JvmStatic
    fun main(args: Array<String>) { start(args).use { Thread.currentThread().join() } }

    fun start(args: Array<String> = emptyArray()): Handle {
        val arguments = args.toList()
        val port = arguments.windowed(2, 1).firstOrNull { it[0] == "--port" }?.get(1)?.toInt() ?: 25565
        val isolated = arguments.windowed(2, 1).firstOrNull { it[0] == "--isolated" }?.get(1)
        val setup = setupClass(isolated)
        val visitors = DebugVisitors()
        val pause = DebugPause(visitors)
        val server = SharedServer.create(
            if (isolated == null) ServerOwner.Module else ServerOwner.Isolated(Class.forName(isolated)),
            { setup }, port, visitors, pause,
        )
        pause.attach(server.clock)
        val lobby = createLobby(server)
        visitors.lobby = lobby
        val breakpoints = Breakpoints()
        val monitor = DebugMonitor(pause, breakpoints)
        val overlay = Overlay(visitors, monitor)
        pause.attachOverlay(overlay::update)
        server.monitors += pause.monitor()
        server.monitors += monitor
        server.process.eventHandler().addListener(PlayerDisconnectEvent::class.java) { visitors.disconnected(it.player) }
        val runner = StoryRunner { message -> visitors.all().forEach { it.sendMessage(net.kyori.adventure.text.Component.text(message)) }; println(message) }
        val commands = DebugCommands(visitors, pause, runner, monitor, breakpoints)
        server.process.command().register(commands.command())
        Thread.ofPlatform().name("yuanban-story-console").daemon().start {
            while (server.process.isAlive) {
                val line = readlnOrNull() ?: break
                commands.execute(object : net.minestom.server.command.CommandSender {
                    override fun tagHandler() = TagHandler.newHandler()
                    override fun identity() = Identity.nil()
                }, line)
            }
        }
        println("story debug server: ${server.address}, ServerSetup=${setup?.qualifiedName ?: "none"}")
        return Handle(server, commands.command())
    }

    private fun createLobby(server: RunningServer) = CompletableFuture<net.minestom.server.instance.InstanceContainer>().also { result ->
        server.process.scheduler().scheduleNextTick {
            val lobby = server.process.instance().createInstanceContainer(DimensionType.OVERWORLD)
            lobby.setGenerator(Generator { unit -> unit.modifier().fillHeight(0, 64, Block.STONE) })
            result.complete(lobby)
        }
    }.join()

    private fun setupClass(isolated: String?): KClass<out ServerSetup<*>>? {
        val isolatedType = isolated?.let { Class.forName(it) }
        val annotated = isolatedType?.getAnnotation(IsolatedJvm::class.java)?.setup
        if (annotated != null && annotated != ModuleSetup::class) return annotated
        val name = (properties()["yuanban.story.setup"] as? String) ?: return null
        val type = Class.forName(name)
        check(ServerSetup::class.java.isAssignableFrom(type)) { "$name does not implement ServerSetup" }
        @Suppress("UNCHECKED_CAST")
        return type.kotlin as KClass<out ServerSetup<*>>
    }

    private fun properties(): Properties = Properties().also { properties ->
        Thread.currentThread().contextClassLoader.getResourceAsStream("junit-platform.properties")?.use(properties::load)
    }

    class Handle internal constructor(private val server: RunningServer, private val command: net.minestom.server.command.builder.Command) : AutoCloseable {
        val address: InetSocketAddress get() = server.address
        override fun close() {
            server.process.command().unregister(command)
            server.process.stop()
        }
    }
}
