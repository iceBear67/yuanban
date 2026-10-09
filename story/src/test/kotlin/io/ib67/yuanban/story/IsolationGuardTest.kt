package io.ib67.yuanban.story

import io.ib67.yuanban.story.samples.IsolatedSample
import io.ib67.yuanban.story.samples.NestedIsolatedSamples
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

/** 这个测试本身是故事：共享服务端由外层的测试引擎创建，示例故事在 EngineTestKit 中使用它 */
class IsolationGuardTest {
    @Story
    fun `在共享服务端已经创建的 JVM 中运行 @IsolatedJvm 示例类，它以说明需要单独 JVM 的错误失败`() = story {
        val failure = runSamples(selectClass(IsolatedSample::class.java)).failure("isolated 类中的示例故事")
        failure.shouldBeInstanceOf<ExtensionConfigurationException>().message.shouldNotBeNull() shouldContain "isolated 类需要单独的 JVM"
    }

    @Story
    fun `标注在嵌套类上的 @IsolatedJvm 被拒绝`() = story {
        val failure = runSamples(selectClass(NestedIsolatedSamples::class.java)).failure("嵌套类中的示例故事")
        failure.shouldBeInstanceOf<ExtensionConfigurationException>().message.shouldNotBeNull() shouldContain "只能标注在顶层类上"
    }
}
