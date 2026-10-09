package io.ib67.yuanban.story.runtime

import io.ib67.yuanban.bot.observe.CloseReason
import io.ib67.yuanban.bot.observe.Observation
import io.ib67.yuanban.bot.observe.Observer
import io.ib67.yuanban.bot.observe.ObserverContext
import io.ib67.yuanban.bot.observe.Subscription
import io.ib67.yuanban.bot.view.StateReader
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket
import java.util.concurrent.atomic.AtomicInteger

/**
 * 标记 observer：每个 bot 加入时注册一个，只处理负数编号的 Ping。
 * bot 应用标记的时刻，就是它应用完服务端在标记之前发给这个玩家的全部数据包的时刻
 */
internal class MarkObserver(private val story: StoryEnvImpl) : Observer {
    private class Pending(val onArrival: (StateReader) -> Unit, val onLost: () -> Unit)

    private val pending = HashMap<Int, Pending>()
    private var closed = false

    override fun subscription(): Subscription = Subscription.NONE.received(ClientboundPingPacket::class.java)

    /** 登记一个标记的回调；observer 已经关闭（bot 已经断开）时返回 false */
    fun register(id: Int, onArrival: (StateReader) -> Unit, onLost: () -> Unit): Boolean = synchronized(this) {
        if (closed) return false
        pending[id] = Pending(onArrival, onLost)
        true
    }

    override fun onObservation(observation: Observation, context: ObserverContext) {
        val ping = (observation as Observation.PacketReceived).packet as ClientboundPingPacket
        if (ping.id >= 0) return
        // 回调抛出的异常只关闭这个 observer（之后的标记都不会再到达），所以交给故事
        val callbacks = synchronized(this) { pending.remove(ping.id) }
            ?: return story.fail(IllegalStateException("收到没有登记的标记 ${ping.id}"))
        try {
            callbacks.onArrival(context.state())
        } catch (e: Throwable) {
            story.fail(e)
        }
    }

    override fun onClose(reason: CloseReason, context: ObserverContext) {
        val lost = synchronized(this) {
            closed = true
            pending.values.toList().also { pending.clear() }
        }
        lost.forEach {
            try {
                it.onLost()
            } catch (e: Throwable) {
                story.fail(e)
            }
        }
    }

    companion object {
        private val ids = AtomicInteger()

        /** JVM 中递减的负数编号 */
        fun nextId(): Int = ids.decrementAndGet()
    }
}
