package io.ib67.yuanban.story

import io.ib67.yuanban.bot.view.StateReader

/** 故事中可以检查的一项事实 */
sealed interface Fact<T> {
    /** 名词短语，例如「alice 的生命值」 */
    val description: String
}

/** tick 结束时在 tick 调度线程上读取 */
class ServerFact<T>(override val description: String, val read: () -> T) : Fact<T> {
    override fun toString(): String = factText(description)
}

/** 标记到达 actor 的客户端时，在 bot 的调度线程上以这一刻的模型读取 */
class ClientFact<T>(override val description: String, val actor: PlayerActor, val read: (StateReader) -> T) : Fact<T> {
    override fun toString(): String = factText(description)
}

/** 服务端与若干客户端都要满足；clients 在每个 tick 结束时与服务端一同读取 */
class SyncedFact<T>(
    override val description: String,
    val server: ServerFact<T>,
    val clients: () -> List<ClientFact<T>>,
) : Fact<T> {
    override fun toString(): String = factText(description)
}

fun <T, R> Fact<T>.map(description: String, transform: (T) -> R): Fact<R> = when (this) {
    is ServerFact -> mapServer(description, transform)
    is ClientFact -> mapClient(description, transform)
    is SyncedFact -> SyncedFact(description, server.mapServer(description, transform)) {
        clients().map { it.mapClient(description, transform) }
    }
}

private fun <T, R> ServerFact<T>.mapServer(description: String, transform: (T) -> R): ServerFact<R> =
    ServerFact(description) { transform(read()) }

private fun <T, R> ClientFact<T>.mapClient(description: String, transform: (T) -> R): ClientFact<R> =
    ClientFact(description, actor) { transform(read(it)) }

/**
 * 词汇的代码在 story { } 之外对事实使用 Kotest 的 shouldBe 时，Kotest 比较的是事实对象本身，失败信息中出现这段文字
 */
private fun factText(description: String) = "事实：$description（只能在 story { } 与 expect { } 中检查）"
