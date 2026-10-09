package io.ib67.yuanban.story

import org.opentest4j.AssertionFailedError
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** 一个步骤没有完成：先决条件不满足、bot 的动作被取消，或者完成的条件没有在时限内成立 */
open class StepFailure(message: String, cause: Throwable? = null) : AssertionFailedError(message, cause)

/** 故事失败，信息中带有故事记录；原因为原来的异常 */
class StoryFailure(message: String, cause: Throwable?) : AssertionFailedError(message, cause)

internal enum class StepKind {
    /** 准备：世界、方块、玩家、实体 */
    SETUP,

    /** 动作：词汇中的每个动作 */
    ACTION,

    /** 期望 */
    EXPECT,

    /** 等待 */
    WAIT,

    /** 叙述 */
    NARRATION,

    /** 服务端代码 */
    SERVER,

    /** simultaneously 的一个分支，其中的步骤单独成组 */
    BRANCH,
}

internal enum class StepResult(val symbol: String, val suffix: String) {
    RUNNING("…", "（没有结束）"),
    PASSED("✔", ""),
    FAILED("✘", ""),
    EXPECTED_FAILURE("✘", "（预期之内的失败）"),
}

/** 故事记录中的一个步骤 */
internal class StepRecord(
    val index: Int,
    val depth: Int,
    val text: String,
    val kind: StepKind,
    /** 故事时钟，纳秒 */
    val startedAt: Long,
) {
    @Volatile
    var finishedAt: Long? = null

    @Volatile
    var result: StepResult = StepResult.RUNNING

    /** 详情：期望的每一项 */
    val details = CopyOnWriteArrayList<String>()
    val children = CopyOnWriteArrayList<StepRecord>()
}

/** 一个故事的步骤记录。每个线程记录自己当前所在的步骤，新的步骤成为它的子步骤 */
internal class Transcript(private val title: String, private val clock: () -> Long) {
    private val roots = CopyOnWriteArrayList<StepRecord>()
    private val counter = AtomicInteger()
    private val current = ThreadLocal<StepRecord?>()

    fun currentParent(): StepRecord? = current.get()

    /** 在当前线程上以 parent 为当前步骤执行 body */
    fun <T> withParent(parent: StepRecord?, body: () -> T): T {
        val previous = current.get()
        current.set(parent)
        try {
            return body()
        } finally {
            current.set(previous)
        }
    }

    fun begin(text: String, kind: StepKind): StepRecord {
        val parent = current.get()
        val record = StepRecord(counter.incrementAndGet(), (parent?.depth ?: -1) + 1, text, kind, clock())
        (parent?.children ?: roots).add(record)
        return record
    }

    fun finish(record: StepRecord, result: StepResult) {
        record.finishedAt = clock()
        record.result = result
    }

    /** parent 之下从第 from 个子步骤开始失败的步骤（包括更深的步骤）改为预期之内的失败 */
    fun markExpectedFailures(parent: StepRecord?, from: Int) {
        val siblings = parent?.children ?: roots
        siblings.drop(from).forEach(::markExpected)
    }

    fun childCount(parent: StepRecord?): Int = (parent?.children ?: roots).size

    private fun markExpected(record: StepRecord) {
        if (record.result == StepResult.FAILED) record.result = StepResult.EXPECTED_FAILURE
        record.children.forEach(::markExpected)
    }

    fun render(): String = buildString {
        append("故事：").append(title).append('\n')
        roots.forEach { render(it, this) }
    }

    private fun render(record: StepRecord, out: StringBuilder) {
        val indent = "  ".repeat(record.depth + 1)
        val head = "$indent${record.result.symbol} ${record.text}${record.result.suffix}"
        out.append(head)
        val finished = record.finishedAt
        if (finished != null) {
            val seconds = "%.2f 秒".format((finished - record.startedAt) / 1e9)
            out.append(" ".repeat((DURATION_COLUMN - displayWidth(head)).coerceAtLeast(1))).append(seconds)
        }
        out.append('\n')
        record.details.forEach { detail -> detail.lines().forEach { out.append(indent).append("  ").append(it).append('\n') } }
        record.children.forEach { render(it, out) }
    }

    private companion object {
        const val DURATION_COLUMN = 76

        /** 终端中的显示宽度：中日韩文字与全角符号占两列 */
        fun displayWidth(text: String): Int = text.codePoints().map { if (isWide(it)) 2 else 1 }.sum()

        fun isWide(codePoint: Int): Boolean =
            codePoint in 0x1100..0x115F || codePoint in 0x2E80..0xA4CF || codePoint in 0xAC00..0xD7A3 ||
                codePoint in 0xF900..0xFAFF || codePoint in 0xFE30..0xFE4F || codePoint in 0xFF00..0xFF60 ||
                codePoint in 0xFFE0..0xFFE6
    }
}
