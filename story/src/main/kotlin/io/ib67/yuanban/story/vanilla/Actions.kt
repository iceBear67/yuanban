package io.ib67.yuanban.story.vanilla

import io.ib67.yuanban.bot.Action
import io.ib67.yuanban.bot.BotTimeoutException
import io.ib67.yuanban.bot.player.AttributeMap
import io.ib67.yuanban.bot.player.LocalPlayer
import io.ib67.yuanban.bot.view.StateReader
import io.ib67.yuanban.story.EntityActor
import io.ib67.yuanban.story.ExpectItem
import io.ib67.yuanban.story.ExpectMode
import io.ib67.yuanban.story.Fact
import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.Spot
import io.ib67.yuanban.story.StepFailure
import io.ib67.yuanban.story.clientInventory
import io.ib67.yuanban.story.formatDuration
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.Material
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType
import kotlin.math.sqrt
import kotlin.time.toJavaDuration

// 动作共用的先决条件、bot 调用的等待与完成的条件

/**
 * 等到 bot 的动作执行完毕。动作被取消、没有在 StoryDefaults.timeout 内执行完毕时抛出 StepFailure
 *
 * @param what 写在失败信息中的动作，例如「alice 走到 world (0, 64, 3)」
 */
internal fun PlayerActor.await(action: Action, what: String) {
    val timeout = story.defaults.timeout
    val completed = try {
        action.await(timeout.toJavaDuration())
    } catch (e: BotTimeoutException) {
        cancelAndWait(action)
        throw StepFailure("$what 没有在 ${formatDuration(timeout)}内执行完毕，已经取消这个动作", e)
    }
    if (!completed) throw StepFailure("$what 被取消：${action.future().exceptionNow().message}")
}

/**
 * 取消 bot 的动作并等到它结束，时限为 StoryDefaults.timeout。bot 的移动动作与手部动作各自排队执行，
 * 步骤失败之后仍在运行的动作会继续操作这个玩家，之后提交的同类动作也会一直排在它后面
 */
internal fun PlayerActor.cancelAndWait(action: Action) {
    action.cancel()
    action.await(story.defaults.timeout.toJavaDuration())
}

/** 完成的条件为服务端或者客户端的状态时，使用与期望相同的 tick 检查等到它成立，时限为 StoryDefaults.timeout */
internal fun <T> PlayerActor.awaitFact(fact: Fact<T>, text: String, test: (T) -> Boolean) {
    val matcher = Matcher<T> { value -> MatcherResult(test(value), { "${fact.description}$text" }, { "${fact.description}不$text" }) }
    val mode = ExpectMode.Within(story.defaults.timeout)
    story.awaitFacts("完成的条件（${fact.description}$text）", listOf(ExpectItem(fact, matcher)), mode, null)
}

/** 先决条件：客户端物品栏中有这种物品 */
internal fun PlayerActor.requireInInventory(material: Material) {
    if (bot.inspect { clientInventory(it) }.count(material) == 0) {
        throw StepFailure("$name 的物品栏中没有 ${material.key().value()}：在 player(\"$name\") { inventory(...) } 中放入，或者先让 $name 拿到它")
    }
}

/** 原版客户端的眼睛位置与交互距离：站立时眼睛高度为 1.62F 乘以 minecraft:scale，与原版相同以 float 计算 */
internal class Reach(val eye: Vec, val block: Double, val entity: Double)

internal fun PlayerActor.reach(): Reach = bot.inspect { state -> reachOf(state) }

private fun reachOf(state: StateReader): Reach {
    val player = state.player()
    fun attribute(type: AttributeType.Builtin) = player.attributes()[type] ?: AttributeMap.PLAYER_BASE.applyAsDouble(type)
    val eyeHeight = LocalPlayer.STANDING_EYE_HEIGHT * attribute(AttributeType.Builtin.SCALE).toFloat()
    val eye = player.position().asVec().add(0.0, eyeHeight.toDouble(), 0.0)
    return Reach(
        eye,
        attribute(AttributeType.Builtin.BLOCK_INTERACTION_RANGE),
        attribute(AttributeType.Builtin.ENTITY_INTERACTION_RANGE),
    )
}

/** 先决条件：眼睛到方块包围盒的距离不超过客户端中 block_interaction_range 属性的值 */
internal fun PlayerActor.requireBlockInReach(spot: Spot): Reach {
    val reach = reach()
    val distance = boxDistance(reach.eye, spot.x.toDouble(), spot.y.toDouble(), spot.z.toDouble(), spot.x + 1.0, spot.y + 1.0, spot.z + 1.0)
    if (distance > reach.block) {
        throw StepFailure("$spot 不在 $name 的交互距离之内：眼睛到方块的距离为 ${"%.2f".format(distance)}，上限为 ${reach.block}；先让 $name 走近")
    }
    return reach
}

/** 先决条件：眼睛到实体包围盒的距离不超过客户端中 entity_interaction_range 属性的值 */
internal fun PlayerActor.requireEntityInReach(entity: EntityActor) {
    val id = entity.entity.entityId
    val (reach, snapshot) = bot.inspect { reachOf(it) to it.entity(id) }
    if (snapshot == null) throw StepFailure("$name 的客户端中没有 $entity")
    val box = snapshot.boundingBox()
    val position = snapshot.position()
    val distance = boxDistance(
        reach.eye,
        position.x() + box.minX(), position.y() + box.minY(), position.z() + box.minZ(),
        position.x() + box.maxX(), position.y() + box.maxY(), position.z() + box.maxZ(),
    )
    if (distance > reach.entity) {
        throw StepFailure("$entity 不在 $name 的交互距离之内：眼睛到实体的距离为 ${"%.2f".format(distance)}，上限为 ${reach.entity}；先让 $name 走近")
    }
}

private fun boxDistance(point: Vec, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Double {
    val dx = maxOf(minX - point.x(), 0.0, point.x() - maxX)
    val dy = maxOf(minY - point.y(), 0.0, point.y() - maxY)
    val dz = maxOf(minZ - point.z(), 0.0, point.z() - maxZ)
    return sqrt(dx * dx + dy * dy + dz * dz)
}
