package io.ib67.yuanban.story.vanilla

import io.ib67.yuanban.story.ClientFact
import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.plain

/** 发送聊天消息 */
fun PlayerActor.says(text: String) = story.step("$name 说「$text」") {
    await(bot.chat(text), "$name 发送聊天消息")
}

/** 执行命令，command 不带开头的斜线 */
fun PlayerActor.runs(command: String) = story.step("$name 执行命令 /$command") {
    await(bot.command(command), "$name 执行命令 /$command")
}

/** 客户端聊天记录中每一行的 plainText */
val PlayerActor.messages: ClientFact<List<String>>
    get() = ClientFact("$name 的聊天记录", this) { state -> state.chat().map { it.plainText() } }

/** 客户端聊天记录中发送者为 other 的 UUID 的行，每行 message 的纯文本 */
fun PlayerActor.messagesFrom(other: PlayerActor): ClientFact<List<String>> {
    val sender = other.uuid
    return ClientFact("$name 收到的 ${other.name} 的聊天消息", this) { state ->
        state.chat().filter { it.sender() == sender }.map { plain(it.message()) }
    }
}
