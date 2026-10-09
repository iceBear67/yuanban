package io.ib67.yuanban.story

import io.ib67.yuanban.story.samples.EntrySamples
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

class StoryEntryTest {
    /** 这个测试本身是故事：共享服务端由外层的测试引擎创建，示例故事在 EngineTestKit 中使用它 */
    @Story
    fun `没有调用 story 的 @Story 方法失败，调用两次时失败`() = story {
        val samples = runSamples(selectClass(EntrySamples::class.java))
        samples.failure("没有调用 story 的方法").message.shouldNotBeNull() shouldContain "没有调用 story { }"
        samples.failure("调用两次 story 的方法").message.shouldNotBeNull() shouldContain "只能调用一次"
    }

    @Test
    fun `在 @Story 方法之外调用 story 时失败`() {
        shouldThrow<IllegalStateException> { story { } }.message.shouldNotBeNull() shouldContain "只能在 @Story 方法中调用"
    }
}
