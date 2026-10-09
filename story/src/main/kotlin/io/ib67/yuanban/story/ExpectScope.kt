package io.ib67.yuanban.story

import io.ib67.yuanban.bot.ChunkNotLoadedException
import io.ib67.yuanban.bot.view.StateReader
import io.ib67.yuanban.story.runtime.StoryEnvImpl
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.be
import io.kotest.matchers.collections.contain
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/** 期望的写法，StoryScope 与 ExpectScope 都实现这个接口 */
interface Expectations {
    infix fun <T> Fact<T>.should(matcher: Matcher<T>)

    // 以下函数都转换为 should(matcher)

    infix fun <T> Fact<T>.shouldBe(expected: T) =
        should(DescribedMatcher("应为 ${describeValue(expected)}", be(expected)))

    infix fun <T> Fact<T>.shouldNotBe(unexpected: T) =
        should(DescribedMatcher("不应为 ${describeValue(unexpected)}", be(unexpected).invert()))

    /** 比较方块的类型（Block.compare），不比较属性 */
    infix fun Fact<Block>.shouldBe(expected: Block) =
        should(DescribedMatcher("应为 ${expected.key().value()}", Matcher { actual: Block ->
            MatcherResult(
                actual.compare(expected),
                { "方块应为 ${expected.key().value()}，实际为 ${describeValue(actual)}" },
                { "方块不应为 ${expected.key().value()}" },
            )
        }))

    /** 比较方块的状态 */
    infix fun Fact<Block>.shouldBeExactly(expected: Block) =
        should(DescribedMatcher("应为 ${describeValue(expected)}", Matcher { actual: Block ->
            MatcherResult(
                actual.compare(expected, Block.Comparator.STATE),
                { "方块应为 ${describeValue(expected)}，实际为 ${describeValue(actual)}" },
                { "方块不应为 ${describeValue(expected)}" },
            )
        }))

    /** 这种物品至少 1 个 */
    infix fun Fact<Inventory>.shouldContain(material: Material) =
        should(DescribedMatcher("应包含 ${material.key().value()}", inventoryCount(material, 1)))

    /** 这种物品的数量之和至少为 items.count */
    infix fun Fact<Inventory>.shouldContain(items: ItemCount) =
        should(DescribedMatcher("应包含 $items", inventoryCount(items.material, items.count)))

    infix fun Fact<Inventory>.shouldNotContain(material: Material) =
        should(DescribedMatcher("不应包含 ${material.key().value()}", inventoryCount(material, 1).invert()))

    infix fun <T> Fact<out Collection<T>>.shouldContain(element: T) {
        @Suppress("UNCHECKED_CAST")
        (this as Fact<Collection<T>>).should(DescribedMatcher("应包含 ${describeValue(element)}", contain(element)))
    }

    /** 位置所在的方块格为 spot */
    infix fun Fact<Pos>.shouldBeAt(spot: Spot) =
        should(DescribedMatcher("应位于 $spot", Matcher { actual: Pos ->
            MatcherResult(
                actual.blockX() == spot.x && actual.blockY() == spot.y && actual.blockZ() == spot.z,
                { "位置应在 $spot 的方块格中，实际为 ${describeValue(actual)}" },
                { "位置不应在 $spot 的方块格中" },
            )
        }))
}

/** expect { } 的接收者：should 把这一项加入这个期望 */
class ExpectScope internal constructor() : Expectations {
    internal val items = mutableListOf<ExpectItem<*>>()

    override fun <T> Fact<T>.should(matcher: Matcher<T>) {
        items += ExpectItem(this, matcher)
    }
}

/** 带有一段说明的 Matcher，说明用于故事记录，例如「应为 iron_ingot ×1」 */
class DescribedMatcher<T>(val text: String, private val inner: Matcher<T>) : Matcher<T> {
    override fun test(value: T): MatcherResult = inner.test(value)
}

private fun inventoryCount(material: Material, atLeast: Int): Matcher<Inventory> = Matcher { inventory: Inventory ->
    val count = inventory.count(material)
    MatcherResult(
        count >= atLeast,
        { "${material.key().value()} 的数量应至少为 $atLeast，实际为 $count" },
        { "不应有 ${material.key().value()}，实际有 $count 个" },
    )
}

/** 写在报告中的值 */
fun describeValue(value: Any?): String = when (value) {
    null -> "空"
    is Block -> {
        val properties = value.properties()
        if (properties.isEmpty()) value.key().value()
        else value.key().value() + properties.entries.joinToString(",", "[", "]") { "${it.key}=${it.value}" }
    }
    is Material -> value.key().value()
    is Point -> "(%.2f, %.2f, %.2f)".format(value.x(), value.y(), value.z())
    is String -> "\"$value\""
    is Collection<*> -> if (value.isEmpty()) "空" else value.joinToString("，", transform = ::describeValue)
    else -> value.toString()
}

/** 期望中的一项 */
internal class ExpectItem<T>(val fact: Fact<T>, val matcher: Matcher<T>) {
    val text: String = if (matcher is DescribedMatcher<*>) matcher.text else "应满足条件"

    override fun toString(): String = joinWords(fact.description, text)
}

/** 连接两段文字：两个汉字之间不加空格，其余加一个空格，例如「alice 的位置应为」「槽位 2 应为」 */
internal fun joinWords(left: String, right: String): String =
    if (isHan(left.last()) && isHan(right.first())) left + right else "$left $right"

private fun isHan(char: Char): Boolean = Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN

/** 期望的时限 */
internal sealed interface ExpectMode {
    val label: String

    /** 故事时钟经过 timeout 之后仍没有一个 tick 的检查成立时失败 */
    data class Within(val timeout: Duration) : ExpectMode {
        override val label get() = "${formatDuration(timeout)}之内"
    }

    /** 从下一个 tick 开始的 checks 个 tick 的检查都不成立时失败 */
    data class WithinChecks(val checks: Int) : ExpectMode {
        override val label get() = "$checks 个 tick 检查之内"
    }

    /** 从下一个 tick 开始连续 checks 个 tick 的检查都必须成立 */
    data class Throughout(val checks: Int) : ExpectMode {
        override val label get() = "之后 $checks 个 tick 一直成立"
    }

    /** 故事时钟经过 duration 之前结束的每个 tick 的检查都必须成立 */
    data class ThroughoutDuration(val duration: Duration) : ExpectMode {
        override val label get() = "之后 ${formatDuration(duration)}一直成立"
    }
}

internal fun formatDuration(duration: Duration): String {
    val millis = duration.inWholeMilliseconds
    return when {
        millis % 1000 == 0L -> "${millis / 1000} 秒"
        millis >= 1000 -> "%.2f 秒".format(millis / 1000.0)
        else -> "$millis 毫秒"
    }
}

/** 一项事实在一侧（服务端或者一个客户端）的检查结果 */
internal sealed interface SideResult {
    val passed: Boolean

    /** 读到了值，并调用了 Matcher */
    class Checked(val value: Any?, val result: MatcherResult) : SideResult {
        override val passed get() = result.passed()
    }

    /** 这一侧这一次没有值：同步事实略过这个客户端，单独的客户端事实这一次不成立 */
    class Absent(val reason: String) : SideResult {
        override val passed get() = false
    }
}

/** 一项期望在一个 tick 检查中的结果 */
internal class ItemResult(
    val item: ExpectItem<*>,
    /** 服务端事实与同步事实的服务端；单独的客户端事实为 null */
    val server: SideResult?,
    /** 玩家的名称 → 这个客户端的结果 */
    val clients: List<Pair<String, SideResult>>,
    val passed: Boolean,
) {
    fun describe(tick: Long): String {
        val values = buildList {
            server?.let { add(if (clients.isEmpty()) sideText(it) else "服务端 ${sideText(it)}") }
            clients.forEach { (name, side) -> add(if (server == null && clients.size == 1) sideText(side) else "$name ${sideText(side)}") }
        }
        val head = "${if (passed) "✔" else "✘"} $item；最后一次检查（tick $tick）：${values.joinToString("，")}"
        val failures = buildList {
            server?.let { if (it is SideResult.Checked && !it.passed) add(it.result.failureMessage()) }
            clients.forEach { (name, side) -> if (side is SideResult.Checked && !side.passed) add("$name：${side.result.failureMessage()}") }
        }
        return if (failures.isEmpty()) head else head + failures.joinToString("") { "\n    $it" }
    }

    private fun sideText(side: SideResult): String = when (side) {
        is SideResult.Checked -> describeValue(side.value)
        is SideResult.Absent -> "没有值（${side.reason}）"
    }
}

/** 一个 tick 检查的结果：期望、tick 编号、每一项的服务端值与每个客户端的值、是否成立 */
internal class TickCheckRecord(
    val expectation: String,
    val tick: Long,
    val items: List<ItemResult>,
    val passed: Boolean,
) {
    fun describe(): List<String> = items.map { it.describe(tick) }
}

/**
 * 一个期望的运行：每个 tick 结束时开始一次 tick 检查，检查按照 tick 的顺序得出结果，
 * 结果决定期望成立、失败或者继续等待
 */
internal class ExpectationRun(
    private val env: StoryEnvImpl,
    private val label: String,
    private val items: List<ExpectItem<*>>,
    private val mode: ExpectMode,
) {
    sealed interface Outcome {
        data object Passed : Outcome
        class Failed(val last: TickCheckRecord) : Outcome
    }

    val outcome = CompletableFuture<Outcome>()

    @Volatile
    var last: TickCheckRecord? = null
        private set

    private val lock = Any()
    private val checks = ArrayDeque<TickCheck>()
    private var resolved = 0
    private var passedInRow = 0
    private var stopped = false
    private val startedAt = env.clock.now()
    private lateinit var task: AutoCloseable

    fun start() {
        task = env.storyTickTask(::onTickEnd)
    }

    fun close() {
        task.close()
    }

    private fun onTickEnd(tick: Long) {
        if (outcome.isDone) return
        if (mode is ExpectMode.ThroughoutDuration && env.clock.now() - startedAt >= mode.duration.inWholeNanoseconds) {
            synchronized(lock) {
                stopped = true
                if (checks.isEmpty()) outcome.complete(Outcome.Passed)
            }
            task.close()
            return
        }
        val check = TickCheck(tick)
        synchronized(lock) { checks.addLast(check) }
        check.begin()
    }

    /** 在得出结果的线程上：按照 tick 的顺序处理已经得出结果的检查 */
    private fun checkResolved() {
        synchronized(lock) {
            while (checks.isNotEmpty() && checks.first().record != null) {
                val record = checks.removeFirst().record!!
                if (!outcome.isDone) accept(record)
            }
            if (stopped && checks.isEmpty()) outcome.complete(Outcome.Passed)
        }
    }

    private fun accept(record: TickCheckRecord) {
        last = record
        env.tickChecked(record)
        resolved++
        when (mode) {
            is ExpectMode.Within -> if (record.passed) outcome.complete(Outcome.Passed)
            is ExpectMode.WithinChecks -> when {
                record.passed -> outcome.complete(Outcome.Passed)
                resolved >= mode.checks -> outcome.complete(Outcome.Failed(record))
            }
            is ExpectMode.Throughout -> when {
                !record.passed -> outcome.complete(Outcome.Failed(record))
                ++passedInRow >= mode.checks -> outcome.complete(Outcome.Passed)
            }
            is ExpectMode.ThroughoutDuration -> if (!record.passed) outcome.complete(Outcome.Failed(record))
        }
    }

    private fun error(e: Throwable) {
        outcome.completeExceptionally(e)
    }

    /** tick N 的检查 */
    private inner class TickCheck(val tick: Long) {
        @Volatile
        var record: TickCheckRecord? = null

        private val serverValues = arrayOfNulls<Any?>(items.size)
        private val clientValues = Array(items.size) { mutableListOf<Pair<PlayerActor, ClientValue>>() }
        private val remaining = AtomicInteger()

        /** 在 tick 调度线程上：读取服务端，向需要读取客户端的玩家发送标记 */
        fun begin() {
            val reads = LinkedHashMap<PlayerActor, MutableList<Pair<Int, ClientFact<*>>>>()
            try {
                items.forEachIndexed { index, item ->
                    when (val fact = item.fact) {
                        is ServerFact -> serverValues[index] = fact.read()
                        is ClientFact -> reads.getOrPut(fact.actor) { mutableListOf() } += index to fact
                        is SyncedFact -> {
                            serverValues[index] = fact.server.read()
                            fact.clients().forEach { reads.getOrPut(it.actor) { mutableListOf() } += index to it }
                        }
                    }
                }
            } catch (e: Throwable) {
                error(e)
                return
            }
            remaining.set(reads.size + 1)
            reads.forEach { (actor, facts) ->
                val player = actor.joined.player
                if (!player.isOnline || player.instance == null) {
                    absent(actor, facts, "${actor.name} 不在线或者不在任何世界中")
                    arrive()
                    return@forEach
                }
                env.sendMark(actor.joined, { state ->
                    readClient(actor, facts, state)
                    arrive()
                }, {
                    absent(actor, facts, "标记到达之前 bot 断开：${actor.disconnectText()}")
                    arrive()
                })
            }
            arrive()
        }

        /** 在 bot 的调度线程上，以标记到达时的模型读取 */
        private fun readClient(actor: PlayerActor, facts: List<Pair<Int, ClientFact<*>>>, state: StateReader) {
            facts.forEach { (index, fact) ->
                val value = try {
                    ClientValue.Read(fact.read(state))
                } catch (e: ChunkNotLoadedException) {
                    ClientValue.Missing("没有加载区块 ${e.chunkX()}, ${e.chunkZ()}")
                } catch (e: Throwable) {
                    error(e)
                    return
                }
                synchronized(clientValues[index]) { clientValues[index] += actor to value }
            }
        }

        private fun absent(actor: PlayerActor, facts: List<Pair<Int, ClientFact<*>>>, reason: String) {
            facts.forEach { (index, _) -> synchronized(clientValues[index]) { clientValues[index] += actor to ClientValue.Missing(reason) } }
        }

        private fun arrive() {
            if (remaining.decrementAndGet() != 0) return
            if (outcome.isDone) return
            val results = try {
                items.mapIndexed(::evaluate)
            } catch (e: Throwable) {
                error(e)
                return
            }
            record = TickCheckRecord(label, tick, results, results.all { it.passed })
            checkResolved()
        }

        @Suppress("UNCHECKED_CAST")
        private fun evaluate(index: Int, item: ExpectItem<*>): ItemResult {
            val matcher = item.matcher as Matcher<Any?>
            fun check(value: Any?) = SideResult.Checked(value, matcher.test(value))
            val clients = synchronized(clientValues[index]) { clientValues[index].toList() }
                .sortedBy { it.first.name }
                .map { (actor, value) ->
                    actor.name to when (value) {
                        is ClientValue.Read -> check(value.value)
                        is ClientValue.Missing -> SideResult.Absent(value.reason)
                    }
                }
            return when (item.fact) {
                is ServerFact -> {
                    val server = check(serverValues[index])
                    ItemResult(item, server, emptyList(), server.passed)
                }
                is ClientFact -> ItemResult(item, null, clients, clients.single().second.passed)
                is SyncedFact -> {
                    val server = check(serverValues[index])
                    // 没有值的客户端（没有加载区块、不在线、标记丢失）被略过
                    val checked = clients.filter { it.second is SideResult.Checked }
                    ItemResult(item, server, clients, server.passed && checked.all { it.second.passed })
                }
            }
        }
    }

    /** 标记到达时读到的客户端的值；全部标记到达或者丢失之后统一调用 Matcher */
    private sealed interface ClientValue {
        class Read(val value: Any?) : ClientValue
        class Missing(val reason: String) : ClientValue
    }
}
