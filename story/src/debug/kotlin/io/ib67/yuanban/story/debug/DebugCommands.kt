package io.ib67.yuanban.story.debug

import net.kyori.adventure.text.Component
import net.minestom.server.command.CommandSender
import net.minestom.server.command.ConsoleSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.command.builder.suggestion.Suggestion
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player

/** `/story` 命令：在游戏里（访客）或者控制台上调试、运行故事 */
internal class DebugCommands(
    private val visitors: DebugVisitors,
    private val pause: DebugPause,
    private val runner: StoryRunner,
    private val monitor: DebugMonitor,
    private val breakpoints: Breakpoints,
) {
    fun command(): Command = Command("story").apply {
        setCondition { sender, _ -> sender is ConsoleSender || (sender is Player && visitors.isVisitor(sender)) }
        setDefaultExecutor { sender, _ -> usage(sender) }

        addSyntax({ sender, _ -> list(sender, null) }, ArgumentType.Literal("list"))
        addSyntax(
            { sender, context -> list(sender, context.get("prefix")) },
            ArgumentType.Literal("list"), ArgumentType.Word("prefix"),
        )

        addSyntax({ sender, _ -> send(sender, "story selection is missing") }, ArgumentType.Literal("run"))
        // 每个选择项是一个可以加引号的 ArgumentString（支持带空格、中文的类名#方法名），Loop 接受一个或多个。
        // 两处都设置 suggestion callback：CommandParserImpl 解析真正的补全请求时读的是当前节点（Loop 本身）的
        // callback；GraphConverter 生成客户端提示用的声明命令包时，ArgumentLoop 被展开成内层参数各自的节点，
        // ask_server 标记按内层参数的 hasSuggestion() 计算，所以内层也要有
        val selection = ArgumentType.String("selection").setSuggestionCallback(::runSuggestions)
        val selections = ArgumentType.Loop("selections", selection).setSuggestionCallback(::runSuggestions)
        addSyntax(
            { sender, context -> run(sender, context.get("selections")) },
            ArgumentType.Literal("run"), selections,
        )

        addSyntax({ _, _ -> stop() }, ArgumentType.Literal("stop"))
        addSyntax({ _, _ -> pause.requestPause() }, ArgumentType.Literal("pause"))
        addSyntax({ _, _ -> pause.resume() }, ArgumentType.Literal("resume"))
        addSyntax({ _, _ -> pause.step(1) }, ArgumentType.Literal("step"))
        val stepCount = ArgumentType.Integer("count").min(1)
        setArgumentCallback({ sender, e -> send(sender, "ticks must be a positive integer: ${e.input}") }, stepCount)
        addSyntax({ _, context -> pause.step(context.get("count")) }, ArgumentType.Literal("step"), stepCount)

        addSyntax({ sender, _ -> if (!monitor.next()) send(sender, "没有等待中的断点") }, ArgumentType.Literal("next"))

        addSyntax({ sender, _ -> send(sender, "usage: /story hold on|off") }, ArgumentType.Literal("hold"))
        val holdValue = ArgumentType.Word("value").from("on", "off").setSuggestionCallback(::holdSuggestions)
        setArgumentCallback({ sender, _ -> send(sender, "usage: /story hold on|off") }, holdValue)
        addSyntax(
            { _, context -> monitor.setHold(context.get<String>("value") == "on") },
            ArgumentType.Literal("hold"), holdValue,
        )

        addSyntax({ sender, _ -> send(sender, monitor.status()) }, ArgumentType.Literal("status"))

        addSyntax({ sender, _ -> send(sender, "usage: /story inspect <player>") }, ArgumentType.Literal("inspect"))
        addSyntax(
            { sender, context -> send(sender, monitor.inspect(context.get("player"))) },
            ArgumentType.Literal("inspect"), ArgumentType.Word("player").setSuggestionCallback(::playerSuggestions),
        )

        addSyntax({ sender, _ -> breakpoints.all().forEach { send(sender, "${it.number}: ${it.text}") } }, ArgumentType.Literal("breakpoints"))
        addSyntax({ _, _ -> breakpoints.clear() }, ArgumentType.Literal("clear"))
        val clearNumber = ArgumentType.Integer("number").setSuggestionCallback(::breakpointSuggestions)
        setArgumentCallback({ sender, e -> send(sender, "breakpoint number must be an integer: ${e.input}") }, clearNumber)
        addSyntax({ sender, context -> clear(sender, context.get("number")) }, ArgumentType.Literal("clear"), clearNumber)

        addSyntax({ sender, _ -> send(sender, "usage: /story break <...>") }, ArgumentType.Literal("break"))
        addSyntax(
            { sender, context -> addBreakpoint(sender, context.get("spec")) },
            ArgumentType.Literal("break"), ArgumentType.StringArray("spec").setSuggestionCallback(::breakSuggestions),
        )

        addSyntax({ sender, _ -> lobby(sender) }, ArgumentType.Literal("lobby"))

        addSyntax({ sender, _ -> send(sender, "usage: /story watch <故事> [玩家]") }, ArgumentType.Literal("watch"))
        // 故事名是类名.方法名，可能带空格或者中文（方法名是故事的叙述），用可以加引号的 ArgumentString
        val watchStory = ArgumentType.String("story").setSuggestionCallback(::storySuggestions)
        addSyntax(
            { sender, context -> watch(sender, context.get("story"), null) },
            ArgumentType.Literal("watch"), watchStory,
        )
        addSyntax(
            { sender, context -> watch(sender, context.get("story"), context.get("player")) },
            ArgumentType.Literal("watch"), watchStory,
            ArgumentType.Word("player").setSuggestionCallback(::watchPlayerSuggestions),
        )
    }

    private fun usage(sender: CommandSender) = send(
        sender,
        "usage: /story list, run, stop, pause, resume, step, next, hold, break, breakpoints, clear, status, inspect, watch, lobby",
    )

    private fun list(sender: CommandSender, prefix: String?) {
        // /story list 允许重新发现（例如重新编译之后），补全使用缓存，避免每次按键都做一次完整的 JUnit 发现
        runner.list(prefix, refresh = true).forEach { send(sender, it) }
    }

    private fun run(sender: CommandSender, selections: List<String>) {
        Thread.ofPlatform().name("yuanban-story-runner").start {
            try {
                runner.run(*selections.toTypedArray())
            } catch (e: Throwable) {
                send(sender, "story run failed: $e")
            }
        }
    }

    /** 取消 JUnit 的 CancellationToken；交给每个正在运行的故事一个异常并断开它的 bot；恢复暂停、唤醒全部 next 等待者 */
    private fun stop() {
        runner.stop()
        monitor.stopAll()
        pause.resume()
        monitor.setHold(false)
        breakpoints.clear()
    }

    private fun clear(sender: CommandSender, number: Int) {
        if (!breakpoints.remove(number)) {
            send(sender, "breakpoint not found: $number")
        }
    }

    private fun addBreakpoint(sender: CommandSender, words: Array<String>) {
        try {
            breakpoints.add(words.joinToString(" "))
        } catch (e: IllegalArgumentException) {
            send(sender, "breakpoint error: ${e.message}")
        }
    }

    private fun lobby(sender: CommandSender) {
        if (sender !is Player) {
            send(sender, "lobby is for players only")
            return
        }
        val lobby = visitors.lobby
        if (lobby == null) {
            send(sender, "lobby has not been created")
            return
        }
        sender.setInstance(lobby, Pos(0.5, 64.0, 0.5))
    }

    /**
     * 把访客以旁观模式传送到故事 storyName 中 playerName 所在的 Instance 与位置；不给 playerName 时使用第一个加入的玩家
     */
    private fun watch(sender: CommandSender, storyName: String, playerName: String?) {
        if (sender !is Player) {
            send(sender, "watch is for players only")
            return
        }
        val story = monitor.storyNamed(storyName)
        if (story == null) {
            send(sender, "story not found: $storyName")
            return
        }
        val joined = monitor.playersOf(story).let { joined ->
            if (playerName != null) {
                joined.firstOrNull { it.username == playerName || it.username.startsWith("${playerName}_") }
            } else {
                joined.firstOrNull()
            }
        }
        if (joined == null) {
            send(sender, "story has no players yet: $storyName")
            return
        }
        val target = joined.player
        sender.setInstance(target.instance, target.position)
    }

    private fun runSuggestions(sender: CommandSender, context: CommandContext, suggestion: Suggestion) {
        val typed = currentWord(suggestion)
        val literals = listOf("package:", "tag:").filter { it.startsWith(typed) }
        val names = runner.list(typed).map(::quoteIfNeeded)
        (names + literals).distinct().sorted().forEach { suggestion.addEntry(SuggestionEntry(it)) }
    }

    private fun playerSuggestions(sender: CommandSender, context: CommandContext, suggestion: Suggestion) {
        val typed = currentWord(suggestion)
        monitor.allPlayerNames().distinct().filter { it.startsWith(typed) }.sorted()
            .forEach { suggestion.addEntry(SuggestionEntry(it)) }
    }

    private fun breakpointSuggestions(sender: CommandSender, context: CommandContext, suggestion: Suggestion) {
        val typed = currentWord(suggestion)
        breakpoints.all().map { it.number.toString() }.filter { it.startsWith(typed) }
            .forEach { suggestion.addEntry(SuggestionEntry(it)) }
    }

    private fun storySuggestions(sender: CommandSender, context: CommandContext, suggestion: Suggestion) {
        val typed = currentWord(suggestion)
        monitor.runningStories().distinct().filter { it.startsWith(typed) }.sorted().map(::quoteIfNeeded)
            .forEach { suggestion.addEntry(SuggestionEntry(it)) }
    }

    private fun holdSuggestions(sender: CommandSender, context: CommandContext, suggestion: Suggestion) {
        val typed = currentWord(suggestion)
        listOf("on", "off").filter { it.startsWith(typed) }.forEach { suggestion.addEntry(SuggestionEntry(it)) }
    }

    private fun watchPlayerSuggestions(sender: CommandSender, context: CommandContext, suggestion: Suggestion) {
        val story = monitor.storyNamed(context.get("story")) ?: return
        val typed = currentWord(suggestion)
        monitor.playersOf(story).map { it.username }.filter { it.startsWith(typed) }
            .forEach { suggestion.addEntry(SuggestionEntry(it)) }
    }

    /** break 的断点类型关键字，以及目前已知的玩家名：第一个词两者都可能是，第二个词只在第一个词是玩家名时建议类型关键字 */
    private fun breakSuggestions(sender: CommandSender, context: CommandContext, suggestion: Suggestion) {
        val typed = currentWord(suggestion)
        // completedWords 是整条命令已经输入完整的词，去掉命令名与字面量子命令 "story break" 之后才是 spec 自己的词
        val specWordsBefore = completedWords(suggestion).drop(2)
        val playerNames = monitor.allPlayerNames().distinct()
        val candidates = when {
            specWordsBefore.isEmpty() -> listOf("step", "mismatch", "event") + playerNames
            specWordsBefore.size == 1 && specWordsBefore[0] in playerNames ->
                listOf("observation", "packet", "sent", "block")
            else -> emptyList()
        }
        candidates.filter { it.startsWith(typed) }.forEach { suggestion.addEntry(SuggestionEntry(it)) }
    }

    /**
     * 当前正在输入的词。suggestion.input 是整条命令的文本（已经去掉开头的 '/'），suggestion.start 是按
     * 客户端原文（含 '/'）算出来的下一个词的起始位置，所以要减 1 才是 input 里的位置；以空格结尾时 Minestom
     * 会在末尾追加一个 '\u0000' 占位（TabCompleteListener.getSuggestion），这里去掉它
     */
    private fun currentWord(suggestion: Suggestion): String =
        suggestion.input.substring(suggestion.start - 1).removeSuffix("\u0000").removePrefix("\"")

    /** 当前词之前已经完整输入的词，按空白切分 */
    private fun completedWords(suggestion: Suggestion): List<String> =
        suggestion.input.substring(0, suggestion.start - 1).trim().split(Regex("\\s+")).filter(String::isNotEmpty)

    /**
     * 客户端 brigadier 的不加引号字符串只允许 0-9、A-Z、a-z、_、-、.、+（StringReader.isAllowedInUnquotedString），
     * 含有这个范围以外的任何字符（包括空格、# 这样的符号、中文）就加引号，内部的引号与反斜杠转义
     */
    private fun quoteIfNeeded(value: String): String =
        if (value.any { !isAllowedInUnquotedString(it) }) {
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            value
        }

    private fun isAllowedInUnquotedString(c: Char): Boolean =
        c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z' || c == '_' || c == '-' || c == '.' || c == '+'

    private fun send(sender: CommandSender, text: String) = sender.sendMessage(Component.text(text))
}
