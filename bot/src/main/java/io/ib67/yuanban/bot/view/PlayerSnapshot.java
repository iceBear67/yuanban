package io.ib67.yuanban.bot.view;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.util.Map;
import java.util.UUID;

/**
 * 本地玩家的快照。
 *
 * @param velocity   每 tick 的速度
 * @param attributes 各属性按修饰符计算之后的取值
 * @param effects    药水效果，{@link ActiveEffect#duration()} 为剩余的 tick 数
 * @param heldSlot   快捷栏选中的槽位（0 到 8）
 */
public record PlayerSnapshot(int entityId, UUID uuid, String username,
                             Pos position, Vec velocity, boolean onGround,
                             float health, int food, float saturation, boolean dead,
                             GameMode gameMode, AbilitiesSnapshot abilities,
                             float experienceProgress, int level, int totalExperience,
                             Map<AttributeType, Double> attributes, Map<Effect, ActiveEffect> effects,
                             int heldSlot) {
    public PlayerSnapshot {
        attributes = Map.copyOf(attributes);
        effects = Map.copyOf(effects);
    }
}
