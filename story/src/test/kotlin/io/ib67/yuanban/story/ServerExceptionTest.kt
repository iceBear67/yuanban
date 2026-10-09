package io.ib67.yuanban.story

import io.ib67.yuanban.bot.BotStatus
import io.ib67.yuanban.story.samples.ServerExceptionSamples
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.opentest4j.AssertionFailedError

/**
 * 服务端异常使当时运行中的每个故事失败，所以这个测试在单独的 JVM 中运行，示例故事依次运行。
 * 示例故事创建的服务端在 EngineTestKit 的执行结束时关闭，所以全部示例故事在一次执行中运行
 */
@Tag("isolated")
class ServerExceptionTest {
    @Test
    fun `服务端异常、bot 异常与用户名不在路由表中的玩家使示例故事失败`() {
        val samples = runSamples(selectClass(ServerExceptionSamples::class.java))

        // 示例故事在服务端调度一个抛出异常的任务，示例故事以这个异常为原因失败，信息中列出当时运行中的故事
        val scheduled = samples.failure("服务端调度的任务抛出异常").shouldBeInstanceOf<StoryFailure>()
        val serverError = scheduled.cause.shouldBeInstanceOf<AssertionFailedError>()
        // Minestom 把调度任务抛出的异常包装在 RuntimeException("Exception in scheduled task") 中
        serverError.cause.shouldNotBeNull().cause.shouldNotBeNull().message shouldBe "示例异常"
        serverError.message.shouldNotBeNull() shouldContain "ServerExceptionSamples.服务端调度的任务抛出异常"
        scheduled.message.shouldNotBeNull() shouldContain "故事期间服务端记录的异常：\n  #"

        // bot 因异常结束时示例故事失败
        val botFailure = samples.failure("bot 因异常结束").shouldBeInstanceOf<StoryFailure>()
        botFailure.stackTraceToString() shouldContain "observer 的异常"

        // 用户名不在路由表中的玩家被默认的访客处理器踢出，并记为服务端异常
        val visitorFailure = samples.failure("用户名不在路由表中的玩家").shouldBeInstanceOf<StoryFailure>()
        visitorFailure.stackTraceToString() shouldContain "用户名 visitor 不在路由表中"
        val visitor = ServerExceptionSamples.visitor.shouldNotBeNull()
        visitor.status() shouldBe BotStatus.DISCONNECTED
        visitor.disconnectReason().shouldNotBeNull()
        visitor.failure() shouldBe null
    }
}
