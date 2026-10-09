package io.ib67.yuanban.story

import io.ib67.yuanban.story.vanilla.attacks
import io.ib67.yuanban.story.vanilla.breaks
import io.ib67.yuanban.story.vanilla.drops
import io.ib67.yuanban.story.vanilla.eats
import io.ib67.yuanban.story.vanilla.holds
import io.ib67.yuanban.story.vanilla.interactsWith
import io.ib67.yuanban.story.vanilla.jumpsTo
import io.ib67.yuanban.story.vanilla.looksAt
import io.ib67.yuanban.story.vanilla.messages
import io.ib67.yuanban.story.vanilla.messagesFrom
import io.ib67.yuanban.story.vanilla.opens
import io.ib67.yuanban.story.vanilla.places
import io.ib67.yuanban.story.vanilla.respawns
import io.ib67.yuanban.story.vanilla.runs
import io.ib67.yuanban.story.vanilla.says
import io.ib67.yuanban.story.vanilla.swapsHands
import io.ib67.yuanban.story.vanilla.uses
import io.ib67.yuanban.story.vanilla.usesItem
import io.ib67.yuanban.story.vanilla.walks
import io.ib67.yuanban.story.vanilla.walksTo
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import net.kyori.adventure.text.Component
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import net.minestom.server.crypto.FilterMask
import net.minestom.server.crypto.LastSeenMessages
import net.minestom.server.crypto.SignedMessageBody
import net.minestom.server.entity.EntityType
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.message.ChatType
import net.minestom.server.network.packet.server.play.PlayerChatMessagePacket
import java.time.Instant
import java.util.BitSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class VocabularyStories {
    // 移动

    @Story
    fun `walksTo 走到方块格`() = story {
        val alice = player("alice")
        alice.walksTo(spawn.east(3))
        alice.position shouldBeAt spawn.east(3)
    }

    @Story
    fun `walks 沿朝向走若干格`() = story {
        val alice = player("alice") { facing = net.minestom.server.utils.Direction.NORTH }
        alice.walks(2)
        alice.position shouldBeAt spawn.north(2)
    }

    @Story
    fun `jumpsTo 跳上高一格的方块`() = story {
        // 从静止起跳，空中前进不到一格：站在台阶旁边起跳
        val step = place(Block.STONE, at = spawn.east())
        val alice = player("alice")
        alice.jumpsTo(step)
        alice.position shouldBeAt step.up()
    }

    @Story
    fun `looksAt 看向方块`() = story {
        val alice = player("alice")
        alice.looksAt(spawn.east(5))
        // 东方的偏航角为 -90
        alice.position should Matcher { position: Pos ->
            MatcherResult(abs(position.yaw() + 90f) < 1f, { "偏航角应为 -90，实际为 ${position.yaw()}" }, { "偏航角不应为 -90" })
        }
    }

    // 手

    @Story
    fun `holds 选中快捷栏中的物品`() = story {
        val alice = player("alice") {
            hand(Material.DIRT)
            inventory(Material.STONE * 1)
        }
        alice.holds(Material.STONE)
        alice.hand shouldBe Material.STONE * 1
    }

    @Story
    fun `holds 把背包中的物品换到手中`() = story {
        val alice = player("alice")
        server { alice.player.inventory.setItemStack(20, ItemStack.of(Material.STONE, 3)) }
        alice.holds(Material.STONE)
        alice.hand shouldBe Material.STONE * 3
    }

    @Story
    fun `holds 的先决条件不满足时写出原因`() = story {
        val alice = player("alice")
        val failure = attempt { alice.holds(Material.DIAMOND) }.failure.shouldNotBeNull()
        failure.message.shouldNotBeNull() shouldContain "物品栏中没有 diamond"
    }

    @Story
    fun `usesItem 使用主手中的物品`() = story {
        val alice = player("alice") { hand(Material.STONE) }
        val used = env.expectEvent(PlayerUseItemEvent::class, { it.player === alice.player }, defaults.timeout) { alice.usesItem() }
        used.itemStack.material() shouldBe Material.STONE
    }

    @Story
    fun `动作超时之后被取消，之后的手部动作照常执行`() = story {
        val dirt = place(Block.DIRT, at = spawn.east())
        val alice = player("alice") { hand(Material.STONE) }
        // 按住 60 秒，超过 defaults.timeout
        val failure = attempt { alice.usesItem(holdTicks = 1200) }.failure.shouldNotBeNull()
        alice.breaks(dirt)
        dirt.block shouldBe Block.AIR
        failure.message.shouldNotBeNull() shouldContain "已经取消这个动作"
    }

    @Story
    fun `eats 按住使用食物`() = story {
        val alice = player("alice") { inventory(Material.BREAD * 1) }
        val begun = env.expectEvent(PlayerBeginItemUseEvent::class, { it.player === alice.player }, defaults.timeout) { alice.eats(Material.BREAD) }
        begun.itemStack.material() shouldBe Material.BREAD
    }

    @Story
    fun `drops 丢出主手中的一个物品`() = story {
        val alice = player("alice") { hand(Material.STONE * 5) }
        alice.drops(Material.STONE)
        alice.hand shouldBe Material.STONE * 4
    }

    @Story
    fun `swapsHands 交换主手与副手`() = story {
        val alice = player("alice") { hand(Material.STONE) }
        alice.swapsHands()
        alice.hand shouldBe null
    }

    // 方块

    @Story
    fun `breaks 挖掉方块`() = story {
        val dirt = place(Block.DIRT, at = spawn.north())
        val alice = player("alice")
        alice.breaks(dirt)
        dirt.block shouldBe Block.AIR
    }

    @Story
    fun `交互距离超出时写出实际距离与上限`() = story {
        val far = place(Block.DIRT, at = spawn.north(10))
        val alice = player("alice")
        val failure = attempt { alice.breaks(far) }.failure.shouldNotBeNull()
        val message = failure.message.shouldNotBeNull()
        message shouldContain "交互距离"
        message shouldContain "上限为 4.5"
    }

    @Story
    fun `breaks 超时之后取消挖掘，之后的手部动作照常执行`() = story {
        val bedrock = place(Block.BEDROCK, at = spawn.north())
        val dirt = place(Block.DIRT, at = spawn.east())
        val alice = player("alice")
        val failure = attempt { alice.breaks(bedrock) }.failure.shouldNotBeNull()
        alice.breaks(dirt)
        dirt.block shouldBe Block.AIR
        bedrock.block shouldBe Block.BEDROCK
        failure.message.shouldNotBeNull() shouldContain "已经取消挖掘"
    }

    @Story
    fun `places 放置方块`() = story {
        val alice = player("alice") { hand(Material.STONE * 2) }
        alice.places(Material.STONE, against = spawn.north(2).down())
        spawn.north(2).block shouldBe Block.STONE
        alice.hand shouldBe Material.STONE * 1
    }

    @Story
    fun `uses 使用方块`() = story {
        val table = place(Block.CRAFTING_TABLE, at = spawn.north(2))
        val alice = player("alice")
        val event = env.expectEvent(PlayerBlockInteractEvent::class, { it.player === alice.player }, defaults.timeout) { alice.uses(table) }
        event.blockPosition.blockZ() shouldBe table.z
    }

    // 实体

    @Story
    fun `attacks 攻击实体`() = story {
        val zombie = summon(EntityType.ZOMBIE, at = spawn.east(2))
        val alice = player("alice")
        val attack = env.expectEvent(EntityAttackEvent::class, { it.entity === alice.player }, defaults.timeout) { alice.attacks(zombie) }
        attack.target shouldBeSameInstanceAs zombie.entity
    }

    @Story
    fun `interactsWith 与实体交互`() = story {
        val zombie = summon(EntityType.ZOMBIE, at = spawn.east(2))
        val alice = player("alice")
        val interaction = env.expectEvent(PlayerEntityInteractEvent::class, { it.player === alice.player }, defaults.timeout) {
            alice.interactsWith(zombie)
        }
        interaction.target shouldBeSameInstanceAs zombie.entity
    }

    // 聊天

    @Story
    fun `says 发送聊天消息`() = story {
        val alice = player("alice")
        val chat = env.expectEvent(PlayerChatEvent::class, { it.player === alice.player }, defaults.timeout) { alice.says("你好") }
        chat.rawMessage shouldBe "你好"
    }

    @Story
    fun `messages 为聊天记录中每一行的文本`() = story {
        val alice = player("alice")
        server { alice.player.sendMessage(Component.text("欢迎")) }
        alice.messages shouldContain "欢迎"
    }

    @Story
    fun `messagesFrom 按照发送者筛选玩家聊天`() = story {
        val alice = player("alice")
        val bob = player("bob")
        server("向 bob 发送 alice 的玩家聊天") {
            val chatType = env.process.chatType().getId(ChatType.CHAT)
            bob.player.sendPacket(
                PlayerChatMessagePacket(
                    0, alice.player.uuid, 0, null,
                    SignedMessageBody.Packed("你好", Instant.now(), 0L, LastSeenMessages.Packed(listOf())),
                    null, FilterMask(FilterMask.Type.PASS_THROUGH, BitSet()),
                    chatType + 1, Component.text(alice.username), null,
                ),
            )
        }
        bob.messagesFrom(alice) shouldContain "你好"
    }

    @Story
    fun `runs 执行命令`() = story {
        val alice = player("alice")
        val executed = CompletableFuture<String>()
        val command = Command("story_runs_${alice.username.lowercase()}").apply {
            setDefaultExecutor { sender, _ -> executed.complete((sender as net.minestom.server.entity.Player).username) }
        }
        env.process.command().register(command)
        env.onClose { env.process.command().unregister(command) }

        alice.runs(command.name)
        executed.get(10, TimeUnit.SECONDS) shouldBe alice.username
    }

    // 窗口

    @Story
    fun `opens 打开窗口，put 与 take 移动物品`() = story {
        val chest = place(Block.CHEST, at = spawn.north(2))
        val alice = player("alice") { inventory(Material.STONE * 5) }
        val inventory = Inventory(InventoryType.CHEST_1_ROW, "测试")
        env.events.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.player === alice.player) {
                // 打开窗口，不把手中的石头放在箱子上
                event.isBlockingItemUse = true
                event.player.openInventory(inventory)
            }
        }

        val window = alice.opens(chest)
        window.put(Material.STONE * 2, into = 0)
        window.slot(0) shouldBe Material.STONE * 2
        alice.inventory shouldContain Material.STONE * 3

        window.take(0)
        window.slot(0) shouldBe null
        alice.inventory shouldContain Material.STONE * 5

        window.close()
        alice.client.window shouldBe null
    }

    // 生命

    @Story
    fun `respawns 死亡之后重生`() = story {
        val alice = player("alice")
        server("让 alice 死亡") { alice.player.kill() }
        alice.alive shouldBe false

        alice.respawns()
        alice.alive shouldBe true
    }
}
