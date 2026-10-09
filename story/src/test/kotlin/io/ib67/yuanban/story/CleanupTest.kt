package io.ib67.yuanban.story

import io.ib67.yuanban.story.samples.CleanupSamples
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

/** 这个测试本身是故事：共享服务端由外层的测试引擎创建，示例故事在 EngineTestKit 中使用它 */
class CleanupTest {
    @Story
    fun `示例故事结束之后，它创建的世界已注销，玩家已离线，事件节点已移除，路由表中没有它的登记项，onClose 按照相反顺序执行`() = story {
        runSamples(selectClass(CleanupSamples::class.java)).requireSucceeded("清理的示例故事")
        val record = CleanupSamples.record.shouldNotBeNull()

        env.onServer {
            record.instances.all { it !in env.process.instance().instances } shouldBe true
            record.instances.all { !it.isRegistered } shouldBe true
            record.players.all { !it.isOnline } shouldBe true
        }
        record.events.parent.shouldBeNull()
        record.usernames.forEach { username -> shouldThrow<IllegalStateException> { record.impl.server.router.route(username) } }
        (record.impl in record.impl.server.running) shouldBe false
        record.closed shouldBe listOf("second", "first")
    }
}
