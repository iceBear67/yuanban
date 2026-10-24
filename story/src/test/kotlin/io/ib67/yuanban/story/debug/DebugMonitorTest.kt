package io.ib67.yuanban.story.debug

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * DebugMonitor.next()/awaitNext() 的并发行为。不用 mock、不用假的 StoryEnv 实现：启动真实的调试服务端
 * （StoryServer.start），注册一个 step 断点，并发运行 DebugMonitorTestStories 里两个真实的故事
 * （各自两个步骤），等它们都停在断点上（DebugMonitor.pendingNextCount，不靠 sleep），循环 next 直到都结束，
 * 确认两个故事都通过、结束后 next 在没有等待者时返回 false。
 *
 * SharedServer 每个 JVM 只能创建一个服务端（StoryServer.start 用 ServerOwner.Module），
 * 所以这个测试标记 isolated，在单独的 JVM 中运行（./gradlew :story:isolatedTest），
 * 不会和普通 :story:test 里其它故事共用的服务端冲突。
 */
@Tag("isolated")
class DebugMonitorTest {
    @Test
    fun twoStoriesWaitingAtAStepBreakpointBothEventuallyWakeUp() {
        StoryServer.start(arrayOf("--port", "0")).use { handle ->
            handle.breakpoints.add("step")

            var passed = false
            val runner = Thread.ofPlatform().name("debug-monitor-test-runner").start {
                passed = handle.runner.run(
                    "io.ib67.yuanban.story.debug.DebugMonitorTestStories#调试器测试故事一",
                    "io.ib67.yuanban.story.debug.DebugMonitorTestStories#调试器测试故事二",
                )
            }

            // 两个故事各有两个步骤：每次停下都调用一次 next，循环到两个故事都跑完为止
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (runner.isAlive) {
                check(System.nanoTime() < deadline) { "timed out waiting for the stories to finish" }
                if (handle.monitor.pendingNextCount() > 0) {
                    handle.monitor.next()
                } else {
                    Thread.onSpinWait()
                }
            }
            runner.join(TimeUnit.SECONDS.toMillis(10))

            passed shouldBe true
            // 两个故事都结束了，没有等待者：next() 什么都不做
            handle.monitor.next() shouldBe false
        }
    }
}
