package io.ib67.yuanban.story

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ServerThreadStories {
    @Story
    fun `onServer 与 everyTickEnd 在 tick 调度线程上执行`() = story {
        env.onServer { Thread.currentThread().name } shouldBe MinecraftServer.THREAD_NAME_TICK_SCHEDULER

        val thread = CompletableFuture<String>()
        env.everyTickEnd { thread.complete(Thread.currentThread().name) }.use {
            thread.get(10, TimeUnit.SECONDS) shouldBe MinecraftServer.THREAD_NAME_TICK_SCHEDULER
        }
    }

    @Story
    fun `everyTickEnd 的任务执行时，这个 tick 中放置方块的广播数据包已经发出`() = story {
        val alice = player("alice")
        val target = spawn.north(2)
        val placed = AtomicBoolean()
        val seen = CompletableFuture<Block>()
        env.everyTickEnd {
            if (placed.get() && !seen.isDone) {
                env.sendMark(alice.joined, { state -> seen.complete(state.block(target.x, target.y, target.z)) }, {
                    seen.completeExceptionally(AssertionError("标记丢失"))
                })
            }
        }.use {
            // 方块在 tick 开始时放置，同一个 tick 结束时发送的标记到达时 bot 的模型中已经是新方块
            env.onServer {
                world.instance.setBlock(target.x, target.y, target.z, Block.DIAMOND_BLOCK)
                placed.set(true)
            }
            seen.get(10, TimeUnit.SECONDS).key() shouldBe Block.DIAMOND_BLOCK.key()
        }
    }

    @Story
    fun `onServer 的代码抛出的异常在测试线程上原样抛出`() = story {
        val error = IllegalArgumentException("来自服务端的异常")
        shouldThrow<IllegalArgumentException> { env.onServer { throw error } } shouldBeSameInstanceAs error
    }

    @Story
    fun `在服务端线程上调用阻塞方法时抛出 IllegalStateException`() = story {
        val alice = player("alice")
        env.onServer { runCatching { env.barrier(alice.joined) }.exceptionOrNull() }.shouldBeInstanceOf<IllegalStateException>()
        env.onServer { runCatching { env.onServer { } }.exceptionOrNull() }.shouldBeInstanceOf<IllegalStateException>()
    }

    @Story
    fun `awaitServer 与 awaitTicks`() = story {
        val ticks = AtomicLong()
        env.everyTickEnd { ticks.incrementAndGet() }

        env.awaitTicks(10)
        ticks.get() shouldBeGreaterThanOrEqualTo 10

        env.awaitServer(5.seconds) { ticks.get() >= 20 }
        ticks.get() shouldBeGreaterThanOrEqualTo 20

        shouldThrow<StepFailure> { env.awaitServer(200.milliseconds) { false } }
        val error = IllegalStateException("条件的异常")
        shouldThrow<IllegalStateException> { env.awaitServer(5.seconds) { throw error } } shouldBeSameInstanceAs error
    }
}
