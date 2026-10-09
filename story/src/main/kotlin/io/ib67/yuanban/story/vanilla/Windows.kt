package io.ib67.yuanban.story.vanilla

import io.ib67.yuanban.bot.view.StateReader
import io.ib67.yuanban.bot.view.WindowSnapshot
import io.ib67.yuanban.story.ClientFact
import io.ib67.yuanban.story.ItemCount
import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.Spot
import io.ib67.yuanban.story.StepFailure
import net.minestom.server.item.Material
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType

/** 使用方块，等到客户端打开了窗口 */
fun PlayerActor.opens(spot: Spot): WindowActor = story.step("$name 打开 $spot 处的方块") {
    uses(spot)
    awaitFact(client.window, "已经打开") { it != null }
    WindowActor(this, bot.inspect { checkNotNull(it.openWindow()).windowId() })
}

/** 一个玩家打开的窗口。槽位编号为协议中这个窗口的编号，与 wiki 的 Inventory 页面相同 */
class WindowActor internal constructor(val owner: PlayerActor, private val windowId: Int) {
    private val name get() = owner.name

    fun slot(index: Int): ClientFact<ItemCount?> =
        ClientFact("$name 的窗口槽位 $index", owner) { ItemCount.of(window(it).slot(index)) }

    val slots: ClientFact<List<ItemCount?>>
        get() = ClientFact("$name 的窗口的全部槽位", owner) { state -> window(state).slots().map { ItemCount.of(it) } }

    fun property(index: Int): ClientFact<Int?> =
        ClientFact("$name 的窗口属性 $index", owner) { window(it).properties()[index] }

    fun click(index: Int) = owner.story.step("$name 左键点击窗口槽位 $index") { leftClick(index) }

    fun rightClick(index: Int) = owner.story.step("$name 右键点击窗口槽位 $index") {
        requireOpen()
        owner.await(owner.bot.inventory().rightClick(index), "$name 右键点击窗口槽位 $index")
    }

    fun shiftClick(index: Int) = owner.story.step("$name 按住 Shift 点击窗口槽位 $index") { shift(index) }

    /**
     * 把玩家物品栏部分中的物品放入 into 槽位：拿起第一个数量足够的一组；数量等于整组时左键点击 into，否则右键点击 into items.count 次；
     * 光标中还有物品时左键点击原来的槽位放回。完成的条件为客户端 into 槽位中这种物品的数量增加了 items.count
     */
    fun put(items: ItemCount, into: Int) = owner.story.step("$name 把 $items 放入窗口槽位 $into") {
        val (source, stack, before) = owner.bot.inspect { state ->
            val window = window(state)
            val (source, stack) = playerSlots(window)
                .map { index -> index to ItemCount.of(window.slot(index)) }
                .firstOrNull { (_, item) -> item != null && item.material == items.material && item.count >= items.count }
                ?: throw StepFailure("$name 的窗口的玩家物品栏部分中没有一组数量不少于 ${items.count} 的 ${items.material.key().value()}")
            Triple(source, checkNotNull(stack), countIn(window, into, items.material))
        }
        leftClick(source)
        if (stack.count == items.count) {
            leftClick(into)
        } else {
            repeat(items.count) {
                owner.await(owner.bot.inventory().rightClick(into), "$name 右键点击窗口槽位 $into")
            }
        }
        owner.awaitFact(slotCount(into, items.material), "为 ${before + items.count}") { it == before + items.count }
        if (owner.bot.inspect { it.cursor() } != null) {
            leftClick(source)
            owner.awaitFact(owner.client.fact("$name 的光标上的物品") { it.cursor() }, "为空") { it == null }
        }
    }

    /** shiftClick：把槽位中的物品移入玩家物品栏。完成的条件为玩家物品栏中这种物品的数量增加 */
    fun take(index: Int) = owner.story.step("$name 取出窗口槽位 $index 中的物品") {
        val (material, before) = owner.bot.inspect { state ->
            val item = ItemCount.of(window(state).slot(index)) ?: throw StepFailure("$name 的窗口槽位 $index 为空")
            item.material to inventoryCount(state, item.material)
        }
        shift(index)
        val inventory = owner.client.fact("$name 的物品栏中 ${material.key().value()} 的数量") { inventoryCount(it, material) }
        owner.awaitFact(inventory, "大于 $before") { it > before }
    }

    /** 关闭窗口。完成的条件为客户端没有打开的窗口 */
    fun close() = owner.story.step("$name 关闭窗口") {
        requireOpen()
        owner.await(owner.bot.inventory().close(), "$name 关闭窗口")
        owner.awaitFact(owner.client.window, "已经关闭") { it == null }
    }

    private fun leftClick(index: Int) {
        requireOpen()
        owner.await(owner.bot.inventory().leftClick(index), "$name 左键点击窗口槽位 $index")
    }

    private fun shift(index: Int) {
        requireOpen()
        owner.await(owner.bot.inventory().shiftClick(index), "$name 按住 Shift 点击窗口槽位 $index")
    }

    private fun requireOpen() {
        val current = owner.bot.inspect { it.openWindow()?.windowId() }
        if (current != windowId) throw StepFailure("$name 的窗口已经关闭")
    }

    /** 客户端中这个窗口；已经关闭时抛出 IllegalStateException */
    private fun window(state: StateReader): WindowSnapshot =
        state.openWindow()?.takeIf { it.windowId() == windowId } ?: throw IllegalStateException("$name 的窗口已经关闭")

    private fun slotCount(index: Int, material: Material): ClientFact<Int> =
        ClientFact("$name 的窗口槽位 $index 中 ${material.key().value()} 的数量", owner) { countIn(window(it), index, material) }

    override fun toString(): String = "$name 的窗口"

    private companion object {
        fun countIn(window: WindowSnapshot, index: Int, material: Material): Int =
            ItemCount.of(window.slot(index))?.takeIf { it.material == material }?.count ?: 0

        /** 窗口 0 中背包与快捷栏（9 到 44）里这种物品的数量 */
        fun inventoryCount(state: StateReader, material: Material): Int =
            (9..44).sumOf { ItemCount.of(state.inventory().slot(it))?.takeIf { item -> item.material == material }?.count ?: 0 }

        /**
         * 窗口中玩家物品栏的槽位编号：一般的窗口为最后 36 格，先是背包 27 格，然后是快捷栏 9 格；
         * 合成器的结果槽位排在快捷栏之后；讲台不包含玩家物品栏
         */
        fun playerSlots(window: WindowSnapshot): IntRange {
            val size = window.slots().size
            val start = when (window.type()) {
                ContainerType.LECTERN -> return IntRange.EMPTY
                ContainerType.CRAFTER_3x3 -> size - 37
                else -> size - 36
            }
            return start until start + 36
        }
    }
}

object FurnaceSlots {
    const val INPUT = 0
    const val FUEL = 1
    const val RESULT = 2
}

object CraftingTableSlots {
    const val RESULT = 0
    fun grid(row: Int, column: Int): Int = 1 + row * 3 + column
}

object ChestSlots {
    fun at(row: Int, column: Int): Int = row * 9 + column
}
