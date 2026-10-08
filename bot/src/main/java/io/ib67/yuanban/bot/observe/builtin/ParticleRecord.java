package io.ib67.yuanban.bot.observe.builtin;

import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;

/**
 * 一个 Particle 数据包。实际的粒子由客户端在中心附近按偏移随机生成（原版客户端行为）。
 *
 * @param center       数据包中的中心坐标
 * @param offset       各轴的偏移
 * @param speed        最大速度
 * @param count        数量
 * @param longDistance 是否远距离
 */
public record ParticleRecord(Particle particle, Vec center, Vec offset, float speed, int count, boolean longDistance) {
}
