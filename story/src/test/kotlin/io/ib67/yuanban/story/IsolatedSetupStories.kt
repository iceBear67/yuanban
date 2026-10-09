package io.ib67.yuanban.story

import io.kotest.matchers.shouldBe
import net.minestom.server.ServerProcess
import net.minestom.server.property.ServerProperties
import kotlin.time.Duration.Companion.seconds

/** 在 MinecraftServer.init() 之前修改服务端属性，并修改 StoryDefaults */
object IsolatedSetup : ServerSetup<String> {
    override fun beforeInit() {
        ServerProperties.CHUNK_VIEW_DISTANCE.set(3)
    }

    override fun configure(process: ServerProcess, defaults: StoryDefaults): String {
        defaults.timeout = 7.seconds
        return "isolated"
    }
}

@IsolatedJvm(setup = IsolatedSetup::class)
class IsolatedSetupStories {
    @Story
    fun `beforeInit 修改的服务端属性生效`() = story {
        val alice = player("alice")
        alice.client.fact("服务端视距") { it.worldState().viewDistance() } shouldBe 3
    }

    @Story
    fun `configure 的返回值与修改过的 StoryDefaults 生效`() = story {
        setup<String>() shouldBe "isolated"
        defaults.timeout shouldBe 7.seconds
    }
}
