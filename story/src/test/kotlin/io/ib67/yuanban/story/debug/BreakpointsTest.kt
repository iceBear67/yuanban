package io.ib67.yuanban.story.debug

import io.kotest.matchers.shouldBe
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundShowDialogGamePacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket
import org.junit.jupiter.api.Test

/**
 * Breakpoints.add 对 packet 断点的解析：按简单名称在 MCProtocolLib 的编解码注册表中查找数据包类
 * （loadPacket，见 Breakpoints.kt 的注释）。不需要真实服务端，Breakpoints 本身不依赖服务端。
 *
 * 分别用一个在 ingame/clientbound/entity 子包里的数据包，与一个编号靠后的数据包（GAME 状态 clientbound
 * 方向注册的最后一个，也就是编号最大的一个，按 javap 反汇编 MinecraftPacketRegistry 的静态注册过程核实过）
 * 验证查找能跨越很多个协议状态、很多个编号找到目标类，不是只能找到靠前的几个
 */
class BreakpointsTest {
    @Test
    fun resolvesAPacketInANestedSubpackage() {
        val breakpoints = Breakpoints()
        val entry = breakpoints.add("alice packet ClientboundSetEntityDataPacket")
        entry.spec.resolvedClass shouldBe ClientboundSetEntityDataPacket::class.java
    }

    @Test
    fun resolvesALateNumberedPacket() {
        val breakpoints = Breakpoints()
        val entry = breakpoints.add("alice packet ClientboundShowDialogGamePacket")
        entry.spec.resolvedClass shouldBe ClientboundShowDialogGamePacket::class.java
    }
}
