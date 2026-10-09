package io.ib67.yuanban.story

import io.ib67.yuanban.bot.BotStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import net.minestom.server.instance.block.Block
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class BarrierStories {
    @Story
    fun `服务端放置方块之后，barrier 返回时 bot 的模型中已经是新方块`() = story {
        val alice = player("alice")
        val target = spawn.north(2)

        env.onServer { world.instance.setBlock(target.x, target.y, target.z, Block.GOLD_BLOCK) }
        env.barrier(alice.joined)

        alice.bot.inspect { it.block(target.x, target.y, target.z) }.key() shouldBe Block.GOLD_BLOCK.key()
    }

    @Story
    fun `标记到达之前 bot 断开时 barrier 失败，sendMark 调用 onLost`() = story {
        val alice = player("alice")
        alice.bot.disconnect()
        env.awaitServer(defaults.timeout) { alice.bot.status() == BotStatus.DISCONNECTED }

        val lost = CompletableFuture<Unit>()
        env.sendMark(alice.joined, { lost.completeExceptionally(AssertionError("bot 已经断开，标记不应到达")) }, { lost.complete(Unit) })
        lost.get(10, TimeUnit.SECONDS)

        val failure = shouldThrow<StepFailure> { env.barrier(alice.joined) }
        failure.message shouldContain "标记丢失"
    }
}
