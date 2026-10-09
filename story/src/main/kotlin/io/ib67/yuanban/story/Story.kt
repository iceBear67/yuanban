package io.ib67.yuanban.story

import io.ib67.yuanban.story.runtime.StoryExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/** 故事：JUnit 的测试方法，方法体调用一次 story { } */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Test
@ExtendWith(StoryExtension::class)
annotation class Story

/**
 * 故事的入口，只能在 @Story 方法中调用一次。故事失败时抛出 StoryFailure，
 * 信息中有故事记录、失败时的状态与故事期间服务端记录的异常
 */
fun story(body: StoryScope.() -> Unit) {
    val env = checkNotNull(StoryExtension.current.get()) { "story { } 只能在 @Story 方法中调用" }
    env.runStory(body)
}
