package io.ib67.yuanban.story.runtime

import io.ib67.yuanban.bot.Bot
import io.ib67.yuanban.bot.BotRuntime
import io.ib67.yuanban.story.StepRecord
import io.ib67.yuanban.story.TickCheckRecord

/** 故事监听器，登记在 SharedServer 中，按照登记顺序调用；抛出的异常使这个故事失败 */
internal interface StoryMonitor {
    /** 在测试线程上，故事的 body 执行之前 */
    fun storyStarted(story: StoryEnv) {}

    /** 在创建 BotRuntime 的线程上，创建之后 */
    fun runtimeCreated(story: StoryEnv, runtime: BotRuntime) {}

    /** 在调用 join 或者 rejoin 的线程上，bot 创建并注册标记 observer 之后、等待加入完成之前 */
    fun playerJoining(story: StoryEnv, name: String, bot: Bot) {}

    /** 在执行步骤的线程上，步骤的 body 执行之前 */
    fun stepStarted(story: StoryEnv, step: StepRecord) {}

    /** 在执行步骤的线程上，步骤结束之后（成功或者失败） */
    fun stepFinished(story: StoryEnv, step: StepRecord) {}

    /** 一个 tick 检查得出结果时，在得出结果的线程上 */
    fun tickChecked(story: StoryEnv, check: TickCheckRecord) {}

    /** 在 afterEach 的线程上，清理之前；failure 为故事的失败，成功时为 null */
    fun storyFinishing(story: StoryEnv, failure: Throwable?) {}
}
