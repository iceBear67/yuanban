package io.ib67.yuanban.story

import io.ib67.yuanban.story.vanilla.says
import io.ib67.yuanban.story.vanilla.walksTo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerMoveEvent
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class EventNodeStories {
    @Story
    fun `两个并发故事各自的 events 只收到自己的玩家与世界的事件（第一个故事）`() = ownEventsOnly()

    @Story
    fun `两个并发故事各自的 events 只收到自己的玩家与世界的事件（第二个故事）`() = ownEventsOnly()

    private fun ownEventsOnly() = story {
        val moves = env.recordEvents(PlayerMoveEvent::class) { true }
        val ticks = env.recordEvents(InstanceTickEvent::class) { true }
        val alice = player("alice")

        // 两个故事的玩家同时移动
        meeting.await(60, TimeUnit.SECONDS)
        alice.walksTo(spawn.east(3))
        wait(5)

        moves.events.shouldNotBeEmpty()
        moves.events.all { it.player === alice.player } shouldBe true
        ticks.events.shouldNotBeEmpty()
        ticks.events.all { it.instance === world.instance } shouldBe true
    }

    @Story
    fun `recordEvents 与 expectEvent`() = story {
        val alice = player("alice")
        val chats = env.recordEvents(PlayerChatEvent::class) { it.player === alice.player }

        val chat = env.expectEvent(PlayerChatEvent::class, { it.player === alice.player }, defaults.timeout) {
            alice.says("你好")
        }

        chat.rawMessage shouldBe "你好"
        chats.events.single() shouldBeSameInstanceAs chat
        chats.close()
        alice.says("再见")
        wait(5)
        chats.events.size shouldBe 1
    }

    @Story
    fun `事件过滤条件抛出的异常在下一个检查点抛出`() = story {
        val alice = player("alice")
        env.recordEvents(PlayerChatEvent::class) { throw IllegalStateException("过滤条件的异常") }

        alice.says("你好")

        val failure = shouldThrow<IllegalStateException> { env.awaitServer(10.seconds) { false } }
        failure.message shouldBe "过滤条件的异常"
    }

    private companion object {
        val meeting = CyclicBarrier(2)
    }
}
