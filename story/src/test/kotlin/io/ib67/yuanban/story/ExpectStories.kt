package io.ib67.yuanban.story

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.beGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockChangePacket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class ExpectStories {
    @Story
    fun `服务端在 tick N 放置方块之后，期望在 bot 应用 tick N 的标记时成立，成立的检查为 tick N`() = story {
        val alice = player("alice")
        val target = spawn.north(2)
        val checks = recordChecks()
        val placedAt = AtomicLong()

        simultaneously(
            { target.block shouldBe Block.GOLD_BLOCK },
            {
                // 期望已经开始检查之后，在 tick N 放置方块
                env.awaitServer(defaults.timeout) { checks.isNotEmpty() }
                placedAt.set(env.onServer {
                    world.instance.setBlock(target.x, target.y, target.z, Block.GOLD_BLOCK)
                    currentTick()
                })
            },
        )

        val passed = checks.single { it.passed }
        passed.tick shouldBe placedAt.get()
        checks.filter { it.tick < placedAt.get() }.all { !it.passed } shouldBe true
        // alice 的客户端在 tick N 的检查中已经是新方块
        val clients = passed.items.single().clients
        clients.map { it.first } shouldBe listOf(alice.name)
    }

    @Story
    fun `服务端向一个玩家单独发送与服务端状态不同的方块时，同步事实在这个玩家的客户端不成立`() = story {
        val alice = player("alice")
        player("bob")
        val target = spawn.north(2)
        val checks = recordChecks()

        env.onServer { alice.player.sendPacket(BlockChangePacket(Vec(target.x.toDouble(), target.y.toDouble(), target.z.toDouble()), Block.STONE.stateId())) }
        env.barrier(alice.joined)

        attempt { expect(within = 5) { target.block shouldBe Block.AIR } }.failed shouldBe true
        val last = checks.last().items.single()
        last.server!!.passed shouldBe true
        last.clients.toMap().getValue("alice").passed shouldBe false
        last.clients.toMap().getValue("bob").passed shouldBe true
    }

    @Story
    fun `故事中直接写出的一项与只有这一项的 expect 相同`() = story {
        player("alice")
        val target = spawn.north(2)
        val checks = recordChecks()

        target.block shouldBe Block.AIR
        val direct = checks.last()
        expect { target.block shouldBe Block.AIR }
        val scoped = checks.last()

        direct.expectation shouldBe scoped.expectation
        direct.items.map { it.item.toString() } shouldBe scoped.items.map { it.item.toString() }
        direct.passed shouldBe true
        scoped.passed shouldBe true
    }

    @Story
    fun `within 为 Int 时按照 tick 检查的次数计算`() = story {
        player("alice")
        val checks = recordChecks()

        attempt { expect(within = 5) { spawn.north(2).block shouldBe Block.GOLD_BLOCK } }.failed shouldBe true

        checks shouldHaveSize 5
    }

    @Story
    fun `超时的报告列出最后一个 tick 检查的编号与两侧的值`() = story {
        player("alice")
        val target = spawn.north(2)
        val checks = recordChecks()

        val failure = attempt { expect(within = 300.milliseconds) { target.block shouldBe Block.GOLD_BLOCK } }.failure.shouldNotBeNull()

        val message = failure.message.shouldNotBeNull()
        message shouldContain "最后一次检查（tick ${checks.last().tick}）"
        message shouldContain "服务端 air"
        message shouldContain "alice air"
    }

    @Story
    fun `expectThroughout 在第一个不成立的 tick 失败`() = story {
        player("alice")
        val target = spawn.north(2)
        val checks = recordChecks()
        val placedAt = AtomicLong()
        val result = CompletableFuture<Attempt>()

        simultaneously(
            { result.complete(attempt { expectThroughout(200) { target.block shouldBe Block.AIR } }) },
            {
                env.awaitServer(defaults.timeout) { checks.size >= 3 }
                placedAt.set(env.onServer {
                    world.instance.setBlock(target.x, target.y, target.z, Block.GOLD_BLOCK)
                    currentTick()
                })
            },
        )

        result.get(0, TimeUnit.SECONDS).failed shouldBe true
        checks.last().tick shouldBe placedAt.get()
        checks.dropLast(1).all { it.passed } shouldBe true
    }

    @Story
    fun `没有加载区块的客户端被同步事实略过`() = story {
        // 客户端的视距为 2：alice 在区块 -2，看不到区块 2
        player("alice") { at = world.at(-30, 64, 0) }
        val target = world.at(40, 64, 0)
        val checks = recordChecks()

        target.block shouldBe Block.AIR

        val client = checks.last().items.single().clients.single().second
        client.shouldBeInstanceOf<SideResult.Absent>().reason shouldContain "没有加载区块"
    }

    @Story
    fun `期望使用 Kotest 的 Matcher`() = story {
        val alice = player("alice")
        alice.food should beGreaterThan(0)
        expect { alice.health should io.kotest.matchers.floats.beGreaterThan(0f) }
    }

    @Story
    fun `attempt 捕获 StepFailure，其它异常照常抛出`() = story {
        player("alice")

        attempt { }.succeeded shouldBe true
        attempt { expect(within = 2) { spawn.north(2).block shouldBe Block.GOLD_BLOCK } }.failed shouldBe true
        shouldThrow<IllegalStateException> { attempt { throw IllegalStateException("不是 StepFailure") } }
    }
}
