package io.ib67.yuanban.story.vanilla

import io.ib67.yuanban.story.EntityActor
import io.ib67.yuanban.story.PlayerActor
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand

/** 看向实体，然后攻击它 */
fun PlayerActor.attacks(entity: EntityActor) = story.step("$name 攻击 $entity") {
    requireEntityInReach(entity)
    val id = entity.entity.entityId
    await(bot.lookAt(id), "$name 看向 $entity")
    await(bot.attack(id), "$name 攻击 $entity")
}

/** 用主手与实体交互（右键） */
fun PlayerActor.interactsWith(entity: EntityActor) = story.step("$name 与 $entity 交互") {
    requireEntityInReach(entity)
    await(bot.interact(entity.entity.entityId, Hand.MAIN_HAND), "$name 与 $entity 交互")
}
