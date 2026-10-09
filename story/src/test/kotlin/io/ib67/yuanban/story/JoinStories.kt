package io.ib67.yuanban.story

import io.ib67.yuanban.story.vanilla.leaves
import io.ib67.yuanban.story.vanilla.rejoins
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import net.minestom.server.entity.Player
import java.util.concurrent.CompletableFuture

class JoinStories {
    @Story
    fun `玩家出生在指定世界的指定位置`() = story {
        val arena = world("arena") { spawnAt(5, 64, 7) }
        val alice = player("alice") { at = arena.spawn.east(2) }

        alice.position shouldBeAt arena.at(7, 64, 7)
        env.onServer { alice.player.instance } shouldBe arena.instance
    }

    @Story
    fun `同名的两个玩家得到不同的用户名`() = story {
        val first = env.join("alice", world.instance, spawn.center)
        val second = env.join("alice", world.instance, spawn.center)

        first.username shouldNotBe second.username
        first.username shouldMatch Regex("alice_[0-9a-z]{1,5}")
        second.username shouldMatch Regex("alice_[0-9a-z]{1,5}")
    }

    @Story
    fun `名称不合法时抛出异常`() = story {
        shouldThrow<IllegalArgumentException> { env.join("alice!", world.instance, spawn.center) }
        shouldThrow<IllegalArgumentException> { env.join("", world.instance, spawn.center) }
        shouldThrow<IllegalArgumentException> { env.join("abcdefghijk", world.instance, spawn.center) }
    }

    @Story
    fun `加入失败之后断开 bot`() = story {
        val release = CompletableFuture<Unit>()
        env.onClose { release.complete(Unit) }
        val configuring = CompletableFuture<Player>()
        val failure = attempt {
            // 服务端的配置阶段停在这里，bot 等不到 Player Loaded
            env.join("bob", world.instance, spawn.center) {
                configuring.complete(player)
                release.join()
            }
        }.failure.shouldNotBeNull()
        failure.message.shouldNotBeNull() shouldContain "Player Loaded"
        val bob = configuring.getNow(null).shouldNotBeNull()

        release.complete(Unit)
        env.awaitServer(defaults.timeout) { !bob.isOnline }
    }

    @Story
    fun `route 之后重新进入配置阶段的玩家出生在新的世界`() = story {
        val alice = player("alice")
        val other = world("other") { spawnAt(3, 64, 3) }

        env.route(alice.joined, other.instance, other.spawn.center)
        env.onServer { alice.player.startConfigurationPhase() }

        env.awaitServer(defaults.timeout) { alice.player.instance === other.instance }
        alice.position shouldBeAt other.spawn
    }

    @Story
    fun `Transfer 到 address 之后 player 为新的 Player`() = story {
        val alice = player("alice")
        val before = alice.player

        env.onServer { before.playerConnection.transfer(env.address.hostString, env.address.port) }

        env.awaitServer(defaults.timeout) { alice.player !== before && alice.player.instance === world.instance }
        alice.player shouldNotBeSameInstanceAs before
        alice.online shouldBe true
        alice.position shouldBeAt spawn
    }

    @Story
    fun `leaves 与 rejoins`() = story {
        val alice = player("alice")

        alice.leaves()
        alice.online shouldBe false

        alice.rejoins(spawn.east(2))
        alice.online shouldBe true
        alice.position shouldBeAt spawn.east(2)
    }
}
