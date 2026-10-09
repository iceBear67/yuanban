package io.ib67.yuanban.story.vanilla

import io.ib67.yuanban.bot.BotTimeoutException
import io.ib67.yuanban.bot.ObserverFailureException
import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.ServerFact
import io.ib67.yuanban.story.Spot
import io.ib67.yuanban.story.StepFailure
import io.ib67.yuanban.story.formatDuration
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.Material
import net.minestom.server.utils.Direction
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand
import kotlin.math.abs
import kotlin.time.toJavaDuration
import org.geysermc.mcprotocollib.protocol.data.game.entity.`object`.Direction as ProtocolDirection

/** 挖掉方块：完成的条件为 BlockBrokenObserver.broken() 为 true */
fun PlayerActor.breaks(spot: Spot) = story.step("$name 挖掉 $spot 处的方块") {
    requireBlockInReach(spot)
    val timeout = story.defaults.timeout
    val observed = bot.breakBlock(spot.point)
    val observer = try {
        observed.await(timeout.toJavaDuration())
    } catch (e: BotTimeoutException) {
        // observer 绑定在动作上，动作结束时以 ACTION_COMPLETED 关闭
        cancelAndWait(observed.action())
        throw StepFailure("$name 没有在 ${formatDuration(timeout)}内挖掉 $spot 处的方块，已经取消挖掘", e)
    } catch (e: ObserverFailureException) {
        // observer 以 FAILED 关闭时 await 立即抛出，不等动作结束，也不取消动作
        cancelAndWait(observed.action())
        throw e
    }
    if (!observer.broken()) {
        val future = observed.action().future()
        val reason = if (future.isCompletedExceptionally) "：动作被取消，${future.exceptionNow().message}" else ""
        throw StepFailure("$spot 处的方块没有被破坏$reason")
    }
}

/**
 * 主手拿起这种方块物品，对着 against 的 face 一面放置。
 * 完成的条件为服务端 against 在 face 方向上相邻的方块变为 material.block() 的类型
 */
fun PlayerActor.places(material: Material, against: Spot, face: Direction = Direction.UP) =
    story.step("$name 对着 $against 的${faceText(face)}放置 ${material.key().value()}") {
        requireInInventory(material)
        val block = material.block() ?: throw StepFailure("${material.key().value()} 不是方块物品")
        requireBlockInReach(against)
        holds(material)
        await(bot.placeBlock(against.point, face.protocol(), Hand.MAIN_HAND), "$name 放置 ${material.key().value()}")
        val target = against.relative(face)
        val placed = ServerFact("$target 处的方块") { target.world.instance.getBlock(target.x, target.y, target.z) }
        awaitFact(placed, "为 ${block.key().value()}") { it.compare(block) }
    }

/** 用主手对着方块朝向玩家的一面的中心使用（右键） */
fun PlayerActor.uses(spot: Spot) = story.step("$name 使用 $spot 处的方块") {
    val reach = requireBlockInReach(spot)
    val face = faceToward(spot, reach.eye)
    val cursor = Vec(0.5 + face.normalX() * 0.5, 0.5 + face.normalY() * 0.5, 0.5 + face.normalZ() * 0.5)
    await(bot.useItemOn(spot.point, face.protocol(), Hand.MAIN_HAND, cursor), "$name 使用 $spot 处的方块")
}

/** 方块朝向眼睛的一面：从方块中心到眼睛的向量中绝对值最大的分量 */
private fun faceToward(spot: Spot, eye: Vec): Direction {
    val delta = eye.sub(spot.middle)
    return when {
        abs(delta.y()) >= abs(delta.x()) && abs(delta.y()) >= abs(delta.z()) -> if (delta.y() > 0) Direction.UP else Direction.DOWN
        abs(delta.x()) >= abs(delta.z()) -> if (delta.x() > 0) Direction.EAST else Direction.WEST
        else -> if (delta.z() > 0) Direction.SOUTH else Direction.NORTH
    }
}

internal fun Direction.protocol(): ProtocolDirection = ProtocolDirection.valueOf(name)

private fun faceText(face: Direction): String = when (face) {
    Direction.UP -> "上面"
    Direction.DOWN -> "下面"
    Direction.NORTH -> "北面"
    Direction.SOUTH -> "南面"
    Direction.EAST -> "东面"
    Direction.WEST -> "西面"
}
