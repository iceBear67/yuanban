package io.ib67.yuanban.story.vanilla

import io.ib67.yuanban.story.EntityActor
import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.Spot

// 移动与朝向：完成的条件为 bot 的动作执行完毕

fun PlayerActor.walksTo(spot: Spot) = story.step("$name 走到 $spot") {
    await(bot.moveTo(spot.center), "$name 走到 $spot")
}

/** 沿当前的水平朝向走 blocks 格 */
fun PlayerActor.walks(blocks: Int) = story.step("$name 向前走 $blocks 格") {
    await(bot.moveForward(blocks.toDouble()), "$name 向前走 $blocks 格")
}

/** 跳上 spot 处的方块：bot 以落在目标方块的顶面为目标起跳，起跳后再次着地时执行完毕 */
fun PlayerActor.jumpsTo(spot: Spot) = story.step("$name 跳上 $spot 处的方块") {
    await(bot.jumpTo(spot.center), "$name 跳上 $spot 处的方块")
}

/** 看向方块格的中心 */
fun PlayerActor.looksAt(spot: Spot) = story.step("$name 看向 $spot") {
    await(bot.lookAt(spot.middle), "$name 看向 $spot")
}

fun PlayerActor.looksAt(entity: EntityActor) = story.step("$name 看向 $entity") {
    await(bot.lookAt(entity.entity.entityId), "$name 看向 $entity")
}
