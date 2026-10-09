package io.ib67.yuanban.story.vanilla

import io.ib67.yuanban.bot.BotStatus
import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.Spot
import io.ib67.yuanban.story.StepFailure

/** 死亡之后重生。完成的条件为服务端与客户端都不处于死亡状态 */
fun PlayerActor.respawns() = story.step("$name 重生") {
    if (!bot.inspect { it.player().dead() }) throw StepFailure("$name 的客户端没有处于死亡状态")
    await(bot.respawn(), "$name 重生")
    awaitFact(alive, "为 true") { it }
}

/** 断开连接。完成的条件为服务端的玩家离线 */
fun PlayerActor.leaves() = story.step("$name 离开服务端") {
    if (!story.env.onServer { player.isOnline }) throw StepFailure("$name 已经不在线")
    bot.disconnect()
    awaitFact(online, "为 false") { !it }
}

/** 以同一个用户名创建新的 bot 再次加入，出生在 at。完成的条件为 bot 发送 Player Loaded */
fun PlayerActor.rejoins(at: Spot = story.spawn) = story.step("$name 再次加入 $at") {
    if (bot.status() != BotStatus.DISCONNECTED) throw StepFailure("$name 还没有离开：先调用 leaves()")
    joined = story.env.rejoin(joined, at.world.instance, at.center)
    story.env.barrier(joined)
}
