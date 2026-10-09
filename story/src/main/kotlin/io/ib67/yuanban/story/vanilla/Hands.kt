package io.ib67.yuanban.story.vanilla

import io.ib67.yuanban.story.ItemCount
import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.StepFailure
import net.minestom.server.component.DataComponents
import net.minestom.server.item.Material
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand

/** 窗口 0 中快捷栏的槽位为 36 到 44，背包为 9 到 35 */
private val HOTBAR = 36..44
private val BACKPACK = 9..35

/**
 * 主手拿起这种物品：物品在快捷栏时选中那一格；在背包中时先与当前选中的快捷栏槽位交换。
 * 完成的条件为客户端的主手为这种物品
 */
fun PlayerActor.holds(material: Material) = story.step("$name 拿起 ${material.key().value()}") {
    requireInInventory(material)
    data class Slots(val slots: List<ItemCount?>, val held: Int, val windowOpen: Boolean)
    val (slots, held, windowOpen) = bot.inspect { state ->
        Slots(state.inventory().slots().map { ItemCount.of(it) }, state.player().heldSlot(), state.openWindow() != null)
    }
    val hotbar = HOTBAR.firstOrNull { slots[it]?.material == material }
    if (hotbar != null) {
        if (hotbar - HOTBAR.first != held) await(bot.selectHotbar(hotbar - HOTBAR.first), "$name 选中快捷栏第 ${hotbar - HOTBAR.first} 格")
    } else {
        val slot = BACKPACK.firstOrNull { slots[it]?.material == material }
            ?: throw StepFailure("$name 的 ${material.key().value()} 不在快捷栏与背包中（在副手、装备或者合成格中）")
        if (windowOpen) throw StepFailure("$name 打开了窗口，原版客户端在窗口打开时不能把背包中的物品换到手中；先关闭窗口")
        await(bot.inventory().swapWithHotbar(slot, held), "$name 把背包槽位 $slot 与快捷栏第 $held 格交换")
    }
    awaitFact(client.hand, "为 ${material.key().value()}") { it?.material == material }
}

/** 使用主手中的物品，holdTicks 为按住使用的 tick 数，为 0 时按一下 */
fun PlayerActor.usesItem(holdTicks: Int = 0) = story.step("$name 使用主手中的物品" + if (holdTicks > 0) " $holdTicks tick" else "") {
    require(holdTicks >= 0) { "按住使用的 tick 数不能为负数：$holdTicks" }
    val action = if (holdTicks == 0) bot.useItem(Hand.MAIN_HAND) else bot.useItem(Hand.MAIN_HAND, holdTicks)
    await(action, "$name 使用主手中的物品")
}

/** 按住使用这种食物，时长为物品的 consumable 组件给出的时长 */
fun PlayerActor.eats(material: Material) = story.step("$name 吃 ${material.key().value()}") {
    requireInInventory(material)
    val consumable = material.prototype().get(DataComponents.CONSUMABLE)
        ?: throw StepFailure("${material.key().value()} 没有 consumable 组件，不能食用")
    // 原版 Consumable.consumeTicks
    val ticks = (consumable.consumeSeconds() * 20.0f).toInt()
    holds(material)
    await(bot.useItem(Hand.MAIN_HAND, ticks), "$name 吃 ${material.key().value()}")
}

/** 丢出主手中的这种物品，all 为 true 时丢出整组 */
fun PlayerActor.drops(material: Material, all: Boolean = false) =
    story.step("$name 丢出${if (all) "一组" else "一个"} ${material.key().value()}") {
        requireInInventory(material)
        holds(material)
        await(bot.drop(all), "$name 丢出 ${material.key().value()}")
    }

fun PlayerActor.swapsHands() = story.step("$name 交换主手与副手的物品") {
    await(bot.swapHands(), "$name 交换主手与副手的物品")
}
