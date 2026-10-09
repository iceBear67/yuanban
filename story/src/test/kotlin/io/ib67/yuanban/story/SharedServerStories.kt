package io.ib67.yuanban.story

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerProcess
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class SharedServerStories {
    @Story
    fun `两个并发故事取得同一个 ServerProcess（第一个故事）`() = story { meet(env.process) }

    @Story
    fun `两个并发故事取得同一个 ServerProcess（第二个故事）`() = story { meet(env.process) }

    /** 两个故事都运行到这里之后，比较各自取得的进程 */
    private fun meet(process: ServerProcess) {
        processes += process
        meeting.await(60, TimeUnit.SECONDS)
        processes.single() shouldBeSameInstanceAs MinecraftServer.process()
    }

    @Story
    fun `address 为回环地址`() = story {
        env.address.address.isLoopbackAddress shouldBe true
        env.address.port shouldBe env.process.server().port
    }

    @Story
    fun `setup 返回模块 ServerSetup 的结果，类型不符时抛出异常`() = story {
        setup<TestWorlds>().dimension.key().asString() shouldBe "story:test_dimension"
        shouldThrow<IllegalStateException> { setup<String>() }
    }

    @Story
    fun `测试维度类型已注册`() = story {
        val dimension = setup<TestWorlds>().dimension
        env.process.dimensionType().get(dimension).shouldNotBeNull()
        val test = world("test") { this.dimension = dimension }
        val alice = player("alice") { at = test.spawn }
        alice.position shouldBeAt test.spawn
    }

    @Story
    fun `StoryDefaults 在配置之后不能修改`() = story {
        shouldThrow<IllegalStateException> { defaults.timeout = 1.seconds }
        shouldThrow<IllegalStateException> { defaults.player = {} }
    }

    private companion object {
        val meeting = CyclicBarrier(2)
        val processes: MutableSet<ServerProcess> = ConcurrentHashMap.newKeySet()
    }
}
