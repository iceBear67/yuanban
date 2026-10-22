package io.ib67.yuanban.story.debug

import net.kyori.adventure.text.Component
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.entity.Player

internal class DebugCommands(
    private val visitors: DebugVisitors,
    private val pause: DebugPause,
    private val runner: StoryRunner,
    private val monitor: DebugMonitor,
    private val breakpoints: Breakpoints,
) {
    fun command(): Command = Command("story").apply {
        setCondition { sender, _ -> sender is Player && visitors.isVisitor(sender) }
        setDefaultExecutor { sender, context -> execute(sender, context.getInput().removePrefix("story").trim()) }
    }

    fun execute(sender: CommandSender, input: String) {
        val words = input.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) return send(sender, "usage: /story list, run, stop, pause, resume, step, next, hold, break, clear, status, inspect")
        when (words[0]) {
            "list" -> runner.list(words.getOrNull(1)).forEach { send(sender, it) }
            "run" -> run(sender, words.drop(1))
            "stop" -> { runner.stop(); pause.resume(); monitor.setHold(false); breakpoints.clear() }
            "pause" -> pause.requestPause()
            "resume" -> pause.resume()
            "step" -> pause.step(words.getOrNull(1)?.toIntOrNull() ?: 1)
            "next" -> monitor.next()
            "hold" -> when (words.getOrNull(1)) {
                "on" -> monitor.setHold(true)
                "off" -> monitor.setHold(false)
                else -> send(sender, "usage: /story hold on|off")
            }
            "status" -> send(sender, monitor.status())
            "inspect" -> words.getOrNull(1)?.let { send(sender, monitor.inspect(it)) }
                ?: send(sender, "usage: /story inspect <player>")
            "breakpoints" -> breakpoints.all().forEach { send(sender, "${it.number}: ${it.text}") }
            "clear" -> if (words.size == 1) breakpoints.clear() else words[1].toIntOrNull()?.let(breakpoints::remove)
                ?: send(sender, "breakpoint number must be an integer")
            "break" -> if (words.size > 1) breakpoints.add(words.drop(1).joinToString(" "))
            "lobby" -> (sender as? Player)?.let { player -> visitors.lobby?.let { lobby -> player.setInstance(lobby, net.minestom.server.coordinate.Pos(0.5, 64.0, 0.5)) } }
            else -> send(sender, "unknown command: ${words[0]}")
        }
    }

    private fun run(sender: CommandSender, selections: List<String>) {
        if (selections.isEmpty()) return send(sender, "story selection is missing")
        Thread.ofPlatform().name("yuanban-story-runner").start {
            try { runner.run(*selections.toTypedArray()) }
            catch (e: Throwable) { send(sender, "story run failed: $e") }
        }
    }

    private fun send(sender: CommandSender, text: String) = sender.sendMessage(Component.text(text))
}
