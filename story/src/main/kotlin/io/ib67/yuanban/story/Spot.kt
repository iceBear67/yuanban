package io.ib67.yuanban.story

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.Direction

/** 一个世界中的方块格 */
data class Spot(val world: WorldActor, val x: Int, val y: Int, val z: Int) {
    /** z 减少 */
    fun north(blocks: Int = 1): Spot = copy(z = z - blocks)

    /** z 增加 */
    fun south(blocks: Int = 1): Spot = copy(z = z + blocks)

    /** x 增加 */
    fun east(blocks: Int = 1): Spot = copy(x = x + blocks)

    /** x 减少 */
    fun west(blocks: Int = 1): Spot = copy(x = x - blocks)

    fun up(blocks: Int = 1): Spot = copy(y = y + blocks)
    fun down(blocks: Int = 1): Spot = copy(y = y - blocks)

    /** face 方向上相邻的方块格 */
    fun relative(face: Direction): Spot = copy(x = x + face.normalX(), y = y + face.normalY(), z = z + face.normalZ())

    /** 方块格底面的中心：站在这一格中的位置 */
    val center: Pos get() = Pos(x + 0.5, y.toDouble(), z + 0.5)

    /** 方块格的中心 */
    internal val middle: Vec get() = Vec(x + 0.5, y + 0.5, z + 0.5)

    internal val point: Vec get() = Vec(x.toDouble(), y.toDouble(), z.toDouble())

    /** 服务端的方块，以及这个世界中已经加载这个区块的每个玩家的客户端看到的方块 */
    val block: SyncedFact<Block>
        get() {
            val description = "$this 处的方块"
            return SyncedFact(description, ServerFact(description) { world.instance.getBlock(x, y, z) }) {
                world.story.playersIn(world).map { actor -> ClientFact(description, actor) { it.block(x, y, z) } }
            }
        }

    override fun toString(): String = "${world.name} ($x, $y, $z)"
}
