package io.ib67.yuanban.story

import io.ib67.yuanban.story.samples.TranscriptSamples
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod

/** 这个测试本身是故事：共享服务端由外层的测试引擎创建，示例故事在 EngineTestKit 中使用它 */
class TranscriptTest {
    @Story
    fun `失败的示例故事抛出 StoryFailure，信息中有按照叙述分组的步骤、期望的每一项、失败时的状态`() = story {
        val samples = runSamples(selectMethod(TranscriptSamples::class.java, "失败的示例故事"))
        val failure = samples.failure("失败的示例故事").shouldBeInstanceOf<StoryFailure>()
        failure.cause.shouldBeInstanceOf<StepFailure>()

        val message = failure.message.shouldNotBeNull()
        message shouldContain "故事：失败的示例故事（TranscriptSamples）"
        message shouldContain "  ✔ alice 加入 world (0, 64, 0)，生存模式"
        message shouldContain "  ✘ 一个物品的烧炼时间为 200 tick"
        message shouldContain "    ✘ 期望（300 毫秒之内）"
        message shouldContain "✘ world (0, 64, -2) 处的方块应为 gold_block；最后一次检查（tick "
        message shouldContain "失败时的状态："
        message shouldContain "    服务端：world (0.50, 64.00, 0.50)，生命 20.0，饥饿 20，生存模式，主手为空，在线"
        message shouldContain "    客户端："
        message shouldContain "最近的聊天："
        message shouldContain "故事期间服务端记录的异常：无"
    }

    @Story
    fun `成功的示例故事把记录写到标准输出`() = story {
        val samples = runSamples(selectMethod(TranscriptSamples::class.java, "成功的示例故事"))
        samples.requireSucceeded("成功的示例故事")
        samples.stdout shouldContain "故事：成功的示例故事（TranscriptSamples）"
        samples.stdout shouldContain "✔ 期望（10 秒之内）"
    }
}
