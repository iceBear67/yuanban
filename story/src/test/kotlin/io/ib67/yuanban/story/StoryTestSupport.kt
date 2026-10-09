package io.ib67.yuanban.story

import io.ib67.yuanban.story.runtime.StoryEnv
import io.ib67.yuanban.story.runtime.StoryEnvImpl
import io.ib67.yuanban.story.runtime.StoryMonitor
import org.junit.platform.engine.DiscoverySelector
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.testkit.engine.EngineExecutionResults
import org.junit.platform.testkit.engine.EngineTestKit
import org.junit.platform.testkit.engine.Event
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.CopyOnWriteArrayList

internal val StoryScope.impl: StoryEnvImpl get() = env as StoryEnvImpl

/** 记录这个故事之后的每个 tick 检查的结果，故事结束时停止记录 */
internal fun StoryScope.recordChecks(): List<TickCheckRecord> {
    val self = impl
    val records = CopyOnWriteArrayList<TickCheckRecord>()
    val monitor = object : StoryMonitor {
        override fun tickChecked(story: StoryEnv, check: TickCheckRecord) {
            if (story === self) records += check
        }
    }
    self.server.monitors += monitor
    env.onClose { self.server.monitors -= monitor }
    return records
}

/** 服务端在这一刻正在执行的 tick 的编号（tick 结束时的编号）；在服务端线程上调用 */
internal fun StoryScope.currentTick(): Long = impl.server.tickEnd.tick + 1

private val sampleLock = Any()

/**
 * 由 EngineTestKit 运行示例故事。示例故事依次运行，不并发。
 * 运行期间写到标准输出的内容同时记录下来；同一个 JVM 中并发运行的其它故事写出的内容也在其中。
 * 运行期间替换 System.out，所以各次运行之间串行
 */
internal fun runSamples(vararg selectors: DiscoverySelector): SampleResults = synchronized(sampleLock) {
    val original = System.out
    val captured = ByteArrayOutputStream()
    val tee = object : OutputStream() {
        override fun write(b: Int) = synchronized(captured) {
            original.write(b)
            captured.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) = synchronized(captured) {
            original.write(b, off, len)
            captured.write(b, off, len)
        }
    }
    System.setOut(PrintStream(tee, true, Charsets.UTF_8))
    val results = try {
        EngineTestKit.engine("junit-jupiter")
            .selectors(*selectors)
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
            .configurationParameter("yuanban.story.setup", TestSetup::class.java.name)
            .execute()
    } finally {
        System.setOut(original)
    }
    SampleResults(results, synchronized(captured) { captured.toString(Charsets.UTF_8) })
}

internal class SampleResults(val results: EngineExecutionResults, val stdout: String) {
    /** 按照方法名找到示例故事的结果 */
    fun result(method: String): TestExecutionResult =
        results.testEvents().finished().stream()
            .filter { methodName(it) == method }
            .map { it.getRequiredPayload(TestExecutionResult::class.java) }
            .findFirst()
            .orElseThrow { AssertionError("示例故事 $method 没有运行；全部事件：${results.allEvents().list()}") }

    fun failure(method: String): Throwable {
        val result = result(method)
        check(result.status == TestExecutionResult.Status.FAILED) { "示例故事 $method 应该失败，实际为 ${result.status}" }
        return result.throwable.orElseThrow()
    }

    fun requireSucceeded(method: String) {
        val result = result(method)
        check(result.status == TestExecutionResult.Status.SUCCESSFUL) {
            "示例故事 $method 应该成功，实际为 ${result.status}：${result.throwable.map { it.stackTraceToString() }.orElse("")}"
        }
    }

    private fun methodName(event: Event): String? =
        (event.testDescriptor.source.orElse(null) as? MethodSource)?.methodName
}
