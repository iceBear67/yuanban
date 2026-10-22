package io.ib67.yuanban.story.runtime

import io.ib67.yuanban.bot.Bot
import io.ib67.yuanban.bot.BotRuntime
import io.ib67.yuanban.story.StepRecord
import io.ib67.yuanban.story.TickCheckRecord

/** Story monitor registered in SharedServer and called in registration order. */
internal interface StoryMonitor {
    /** On the test thread before the story body runs. */
    fun storyStarted(story: StoryEnv) {}

    /** On the thread that creates the BotRuntime, after creation. */
    fun runtimeCreated(story: StoryEnv, runtime: BotRuntime) {}

    /** On the join or rejoin thread after the bot and mark observer are registered. */
    fun playerJoining(story: StoryEnv, name: String, bot: Bot) {}

    /** Called on the join or rejoin thread after the player has completed joining. */
    fun playerJoined(story: StoryEnv, player: JoinedPlayer) {}

    /** On the step thread before the step body runs. */
    fun stepStarted(story: StoryEnv, step: StepRecord) {}

    /** On the step thread after the step completes. */
    fun stepFinished(story: StoryEnv, step: StepRecord) {}

    /** On the thread that produces a tick check result. */
    fun tickChecked(story: StoryEnv, check: TickCheckRecord) {}

    /** On the afterEach thread before cleanup; failure is null for a successful story. */
    fun storyFinishing(story: StoryEnv, failure: Throwable?) {}
}
