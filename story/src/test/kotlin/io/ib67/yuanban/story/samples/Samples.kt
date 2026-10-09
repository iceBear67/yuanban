package io.ib67.yuanban.story.samples

import io.ib67.yuanban.bot.Bot
import io.ib67.yuanban.bot.BotOptions
import io.ib67.yuanban.bot.observe.Observation
import io.ib67.yuanban.bot.observe.builtin.Observers
import io.ib67.yuanban.bot.transport.TcpTransport
import io.ib67.yuanban.story.IsolatedJvm
import io.ib67.yuanban.story.Story
import io.ib67.yuanban.story.runtime.StoryEnvImpl
import io.ib67.yuanban.story.story
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// 由 EngineTestKit 显式选择运行的示例故事；两个测试任务都排除 sample 标签

@Tag("sample")
class TranscriptSamples {
    @Story
    fun `失败的示例故事`() = story {
        player("alice")
        "一个物品的烧炼时间为 200 tick" {
            expect(within = 300.milliseconds) { spawn.north(2).block shouldBe Block.GOLD_BLOCK }
        }
    }

    @Story
    fun `成功的示例故事`() = story {
        val alice = player("alice")
        alice.position shouldBeAt spawn
    }
}

@Tag("sample")
class CleanupSamples {
    @Story
    fun `清理的示例故事`() = story {
        val alice = player("alice")
        val other = world("other")
        val impl = env as StoryEnvImpl
        val closed = CopyOnWriteArrayList<String>()
        env.onClose { closed += "first" }
        env.onClose { closed += "second" }
        record = Record(
            instances = listOf(world.instance, other.instance),
            players = listOf(alice.player),
            usernames = listOf(alice.username),
            events = env.events,
            closed = closed,
            impl = impl,
        )
    }

    internal class Record(
        val instances: List<Instance>,
        val players: List<Player>,
        val usernames: List<String>,
        val events: EventNode<Event>,
        val closed: List<String>,
        val impl: StoryEnvImpl,
    )

    internal companion object {
        @Volatile
        var record: Record? = null
    }
}

@Tag("sample")
class EntrySamples {
    @Story
    fun `没有调用 story 的方法`() {
    }

    @Story
    fun `调用两次 story 的方法`() {
        story { }
        story { }
    }
}

@Tag("sample")
@IsolatedJvm
class IsolatedSample {
    @Story
    fun `isolated 类中的示例故事`() = story { }
}

@Tag("sample")
class NestedIsolatedSamples {
    @Nested
    @IsolatedJvm
    inner class Inner {
        @Story
        fun `嵌套类中的示例故事`() = story { }
    }
}

/** 这些示例故事使服务端记录异常，只在单独的 JVM 中运行 */
@Tag("sample")
class ServerExceptionSamples {
    @Story
    fun `服务端调度的任务抛出异常`() = story {
        env.process.scheduler().scheduleNextTick { throw IllegalStateException("示例异常") }
        env.awaitServer(10.seconds) { false }
    }

    @Story
    fun `bot 因异常结束`() = story {
        val alice = player("alice")
        alice.bot.observe(Observers.listener(Observation.BotTicked::class.java) { throw IllegalStateException("observer 的异常") })
        env.awaitServer(10.seconds) { alice.bot.failure() != null }
    }

    @Story
    fun `用户名不在路由表中的玩家`() = story {
        val options = BotOptions.builder()
            .username("visitor")
            .transport(TcpTransport(env.address.hostString, env.address.port))
            .build()
        visitor = env.runtime.connect(options)
        env.awaitServer(10.seconds) { false }
    }

    companion object {
        @Volatile
        var visitor: Bot? = null
    }
}
