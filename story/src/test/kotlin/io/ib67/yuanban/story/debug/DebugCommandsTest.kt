package io.ib67.yuanban.story.debug

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentLoop
import net.minestom.server.command.builder.arguments.ArgumentString
import net.minestom.server.listener.TabCompleteListener
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * `/story` 命令的补全与参数解析。使用真实服务端的 CommandManager（process.command()）与它的
 * consoleSender，通过 TabCompleteListener.getSuggestion 得到补全结果：这个方法本身就是
 * `parseCommand(sender, text).suggestion(sender)` 加上去掉开头 '/'、以空格结尾时追加 '\u0000'
 * 这两步处理（net.minestom.server.listener.TabCompleteListener 的字节码可以确认这一点），
 * 直接调用它比在测试里重新实现一遍同样的处理更可靠。不使用 mock，command() 是 StoryServer.start
 * 真正注册的 DebugCommands 实例产生的 Command。
 *
 * 覆盖 run、watch、break、hold、clear 的补全候选（前缀过滤、需要时加引号）。其中 run 的补全同时
 * 验证了 CommandParserImpl.parseNode 确实读取了 Loop 节点自己的 suggestion callback：
 * selections 参数是 ArgumentType.Loop，如果补全只能从内层的 String 参数上取得，这里就会得不到候选。
 *
 * 另外验证 run 的多个带引号选择项、watch 的带引号故事名被正确解析：直接对 DebugCommands 注册的
 * 真实 ArgumentLoop、ArgumentString 调用 parse，不经过完整的命令执行（避免真的触发一次故事运行）。
 *
 * SharedServer 每个 JVM 只能创建一个服务端，所以这个测试标记 isolated，在单独的 JVM 中运行。
 */
@Tag("isolated")
class DebugCommandsTest {
    @Test
    fun completionAndParsing() {
        StoryServer.start(arrayOf("--port", "0")).use { handle ->
            val manager = MinecraftServer.process().command()
            val sender = manager.consoleSender
            val command = checkNotNull(manager.getCommand("story")) { "/story 没有注册" }

            // break：还没有玩家加入、spec 的第一个词还没有输入完整时，候选是 step、mismatch、event
            suggest(sender, "story break s").map { it.entry } shouldBe listOf("step")

            // hold：on/off 按前缀过滤，保持声明顺序
            suggest(sender, "story hold o").map { it.entry } shouldBe listOf("on", "off")
            suggest(sender, "story hold of").map { it.entry } shouldBe listOf("off")

            // clear：已登记的断点编号按前缀过滤
            handle.breakpoints.add("step")
            handle.breakpoints.add("mismatch")
            suggest(sender, "story clear 1").map { it.entry } shouldBe listOf("1")
            handle.breakpoints.clear()

            // run：按前缀过滤 JUnit 发现的故事，候选中含中文的故事名需要加引号
            val classPrefix = "io.ib67.yuanban.story.debug.DebugMonitorTestStories"
            val runEntries = suggest(sender, "story run $classPrefix").map { it.entry }
            runEntries shouldContain "\"$classPrefix#调试器测试故事一\""
            runEntries shouldContain "\"$classPrefix#调试器测试故事二\""

            // run 的选择项解析：带引号的一项内部可以有空格和 #，不会被当作两个词
            runSelectionArgument(command).parse(sender, "\"a b#c d\" tag:x") shouldBe listOf("a b#c d", "tag:x")

            // watch：只有故事正在运行时才有候选。用一个真实故事在 step 断点上暂停来制造这个窗口
            handle.breakpoints.add("step")
            val runner = Thread.ofPlatform().name("debug-commands-test-runner").start {
                handle.runner.run("$classPrefix#调试器测试故事一")
            }
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (handle.monitor.pendingNextCount() == 0) {
                    check(System.nanoTime() < deadline) { "故事没有在时限内停在断点上" }
                    Thread.onSpinWait()
                }
                val storyName = handle.monitor.runningStories().single()
                val quotedStoryName = "\"$storyName\""
                // 故事名里的中文不在 brigadier 不加引号的字符集中，候选必须带引号
                suggest(sender, "story watch ").map { it.entry } shouldBe listOf(quotedStoryName)
                watchStoryArgument(command).parse(sender, quotedStoryName) shouldBe storyName
            } finally {
                // 清掉断点并唤醒全部等待者，让故事能跑完，而不必逐步 next
                handle.breakpoints.clear()
                handle.monitor.releaseAll()
                runner.join(TimeUnit.SECONDS.toMillis(10))
            }
        }
    }

    private fun suggest(sender: CommandSender, text: String) =
        checkNotNull(TabCompleteListener.getSuggestion(sender, text)) { "没有补全结果：$text" }.entries

    /** `/story run <selections>` 的 Loop 参数：真实注册的那一个，不是重新构造的同类型对象 */
    @Suppress("UNCHECKED_CAST")
    private fun runSelectionArgument(command: Command): ArgumentLoop<String> =
        command.syntaxes.map { it.arguments }.first { it.size == 2 && it[1] is ArgumentLoop<*> }[1] as ArgumentLoop<String>

    /** `/story watch <故事>` 的 String 参数：真实注册的那一个 */
    private fun watchStoryArgument(command: Command): ArgumentString =
        command.syntaxes.map { it.arguments }
            .first { it.size == 2 && it[0].id == "watch" && it[1] is ArgumentString }[1] as ArgumentString
}
