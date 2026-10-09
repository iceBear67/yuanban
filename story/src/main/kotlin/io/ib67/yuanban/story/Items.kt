package io.ib67.yuanban.story

import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

/** 物品与数量，不包含组件 */
data class ItemCount(val material: Material, val count: Int) {
    override fun toString(): String = "${material.key().value()} ×$count"

    companion object {
        /** 服务端的物品；空气为 null */
        internal fun of(stack: ItemStack): ItemCount? =
            if (stack.isAir) null else ItemCount(stack.material(), stack.amount())

        /** 客户端的物品，按照原版注册表中的编号转换为 Material；空槽位为 null */
        internal fun of(stack: org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack?): ItemCount? {
            if (stack == null) return null
            val material = requireNotNull(Material.fromId(stack.id)) { "客户端的物品编号 ${stack.id} 不在原版注册表中" }
            return ItemCount(material, stack.amount)
        }
    }
}

operator fun Material.times(count: Int): ItemCount = ItemCount(this, count)

/** 一个物品栏的全部槽位，用于检查包含关系；槽位的顺序为各自一侧的编号 */
class Inventory(val slots: List<ItemCount?>) {
    fun count(material: Material): Int = slots.sumOf { if (it?.material == material) it.count else 0 }

    override fun toString(): String {
        val items = slots.filterNotNull()
        return if (items.isEmpty()) "空" else items.joinToString("，")
    }
}
