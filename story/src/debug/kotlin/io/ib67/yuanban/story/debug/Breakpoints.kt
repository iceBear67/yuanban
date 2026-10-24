package io.ib67.yuanban.story.debug

import net.minestom.server.event.Event
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.network.packet.PacketRegistry
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket
import org.geysermc.mcprotocollib.protocol.data.ProtocolState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** 断点的种类 */
internal enum class BreakpointKind { OBSERVATION, PACKET, SENT, BLOCK, EVENT, STEP, MISMATCH }

/**
 * 解析后的断点。`player` 只有按玩家区分的断点（observation、packet、sent、block）才有；
 * `resolvedClass` 为 packet、sent 断点查找到的数据包类，或者 event 断点查找到的事件类，其它种类为 null
 */
internal data class BreakpointSpec(
    val number: Int,
    val player: String?,
    val kind: BreakpointKind,
    val argument: String,
    val arguments: List<String>,
    val resolvedClass: Class<*>? = null,
)

/**
 * 断点登记表。添加时就完成解析（包括按名称查找事件类和数据包类），结果保存在 [Entry.spec] 中，之后不再解析：
 * 格式错误的断点在添加时就会抛出异常，不会让之后的故事反复失败；解析失败时不分配编号
 */
internal class Breakpoints {
    data class Entry(val number: Int, val text: String, val spec: BreakpointSpec)

    private val next = AtomicInteger()
    private val entries = CopyOnWriteArrayList<Entry>()

    /** @throws IllegalArgumentException 断点格式错误，或者引用的事件类、数据包类不存在 */
    fun add(text: String): Entry {
        // 先解析，成功之后再分配编号：格式错误的断点不占用编号。解析时的 number 是占位值，这里再替换成真正的编号
        val spec = parse(text)
        val number = next.incrementAndGet()
        return Entry(number, text, spec.copy(number = number)).also(entries::add)
    }

    fun remove(number: Int): Boolean = entries.removeIf { it.number == number }
    fun clear() = entries.clear()
    fun all(): List<Entry> = entries.toList()
}

private fun parse(text: String): BreakpointSpec {
    val words = text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    require(words.isNotEmpty()) { "breakpoint text must not be blank" }
    return when (words[0]) {
        "step" -> BreakpointSpec(0, null, BreakpointKind.STEP, "", emptyList())
        "mismatch" -> BreakpointSpec(0, null, BreakpointKind.MISMATCH, "", emptyList())
        "event" -> {
            require(words.size == 2) { "event breakpoint requires an event class name" }
            BreakpointSpec(0, null, BreakpointKind.EVENT, words[1], emptyList(), loadEvent(words[1]))
        }
        else -> {
            require(words.size >= 3) { "player breakpoint requires a player name, kind, and argument: $text" }
            val kind: BreakpointKind
            val resolved: Class<*>?
            when (words[1]) {
                "observation" -> {
                    kind = BreakpointKind.OBSERVATION
                    resolved = null
                }
                "packet" -> {
                    kind = BreakpointKind.PACKET
                    resolved = loadPacket(words[2], received = true)
                }
                "sent" -> {
                    kind = BreakpointKind.SENT
                    resolved = loadPacket(words[2], received = false)
                }
                "block" -> {
                    kind = BreakpointKind.BLOCK
                    resolved = null
                }
                else -> throw IllegalArgumentException("unknown breakpoint kind: ${words[1]}")
            }
            BreakpointSpec(0, words[0], kind, words[2], words.drop(2), resolved)
        }
    }
}

/**
 * 按类的简单名称，在 MCProtocolLib 的编解码注册表（MinecraftCodec 里各协议状态的 PacketRegistry）中查找数据包类，
 * 不再按子包名猜测路径：实际的数据包分散在 ingame/clientbound/entity、level、inventory 等很多子包里，猜不全。
 * PacketRegistry 没有公开的遍历方法，但每个协议状态里一个方向的编号从 0 开始连续排列，用公开的
 * getClientboundClass/getServerboundClass 逐个取；遇到没有登记的编号时它们抛出 IllegalArgumentException
 * （javap 可见的 getClientboundDefinition 实现），说明这个状态的编号已经取完，结束这个状态的遍历。
 *
 * 编号连续、没有空洞这一点的依据（javap 反汇编 MinecraftPacketRegistry 确认）：MinecraftCodec 的静态初始化块
 * 只通过 MinecraftPacketRegistry.registerClientboundPacket/registerServerboundPacket 登记数据包，这两个方法
 * 不接受调用方指定的编号，而是各自使用 nextClientboundId/nextServerboundId 字段（构造时为 0，每登记一个加 1）
 * 分配编号；没有其它方式能往这两个方向的某个协议状态里登记数据包，所以编号必然是 0、1、2…… 连续递增，不会跳号。
 */
private fun loadPacket(name: String, received: Boolean): Class<out MinecraftPacket> {
    val found = ProtocolState.entries.asSequence()
        .mapNotNull { state -> MinecraftCodec.CODEC.getCodec(state) }
        .flatMap { registry -> packetClasses(registry, received) }
        .firstOrNull { it.simpleName == name }
        ?: throw IllegalArgumentException("packet class not found: $name")
    check(MinecraftPacket::class.java.isAssignableFrom(found)) { "$name 不是 MinecraftPacket 的子类型" }
    @Suppress("UNCHECKED_CAST")
    return found as Class<out MinecraftPacket>
}

/** registry 中一个方向已经登记的全部数据包类，编号从 0 开始连续排列，遇到未登记的编号就结束 */
private fun packetClasses(registry: PacketRegistry, received: Boolean): Sequence<Class<out Packet>> = sequence {
    var id = 0
    while (true) {
        val type = try {
            if (received) registry.getClientboundClass(id) else registry.getServerboundClass(id)
        } catch (_: IllegalArgumentException) {
            break
        }
        yield(type)
        id++
    }
}

/** 接受完整类名；不含包名时按常见子包猜测，结果必须是 net.minestom.server.event.Event 的子类型 */
private fun loadEvent(name: String): Class<*> {
    val type = if ('.' in name) {
        Class.forName(name)
    } else {
        listOf(
            "net.minestom.server.event.$name",
            "net.minestom.server.event.player.$name",
            "net.minestom.server.event.entity.$name",
            "net.minestom.server.event.instance.$name",
            "net.minestom.server.event.inventory.$name",
            "net.minestom.server.event.server.$name",
        ).asSequence().mapNotNull { runCatching { Class.forName(it) }.getOrNull() }.firstOrNull()
            ?: throw IllegalArgumentException("event class not found: $name")
    }
    check(Event::class.java.isAssignableFrom(type)) { "$name 不是 net.minestom.server.event.Event 的子类型" }
    return type
}
