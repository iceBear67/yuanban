package io.ib67.yuanban.story.debug

import io.ib67.yuanban.story.Story
import io.ib67.yuanban.story.story

/**
 * 给 DebugMonitorTest 使用的两个简单故事：各自有两个步骤，供调试服务端的 step 断点使用。
 * 这两个故事也会被普通的 :story:test 正常发现并运行：那时没有附加调试服务端（没有注册 DebugMonitor），
 * breakpoints 是空的，`stepStarted` 直接返回，不会在任何步骤上等待，正常跑完
 */
class DebugMonitorTestStories {
    @Story
    fun `调试器测试故事一`() = story {
        val player = player("debugA")
        "第一步" {
            player.position shouldBeAt spawn
        }
        "第二步" {
            player.position shouldBeAt spawn
        }
    }

    @Story
    fun `调试器测试故事二`() = story {
        val player = player("debugB")
        "第一步" {
            player.position shouldBeAt spawn
        }
        "第二步" {
            player.position shouldBeAt spawn
        }
    }
}
