package io.ib67.yuanban.bot.view;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.util.Map;
import java.util.UUID;

/**
 * 本地玩家的快照。
 *
 * @param velocity   每 tick 的速度
 * @param eyeHeight  眼睛高出脚部的高度：站立 1.62、潜行 1.27，乘以 {@code minecraft:scale} 属性（原版 {@code LivingEntity.getEyeHeight}）
 * @param attributes 各属性按修饰符计算之后的取值
 * @param effects    药水效果，{@link ActiveEffect#duration()} 为剩余的 tick 数
 * @param heldSlot   快捷栏选中的槽位（0 到 8）
 * @param cooldowns  物品冷却：冷却组 → 剩余的 tick 数
 * @param metadata   Set Entity Metadata 给出的元数据，按索引；通过 {@link #meta(EntityMeta)} 读取命名条目
 */
public record PlayerSnapshot(int entityId, UUID uuid, String username,
                             Pos position, Vec velocity, boolean onGround, double eyeHeight,
                             float health, int food, float saturation, boolean dead,
                             GameMode gameMode, AbilitiesSnapshot abilities,
                             float experienceProgress, int level, int totalExperience,
                             Map<AttributeType, Double> attributes, Map<Effect, ActiveEffect> effects,
                             int heldSlot, Map<Key, Integer> cooldowns, Map<Integer, EntityMetadata<?, ?>> metadata) {
    public PlayerSnapshot {
        attributes = Map.copyOf(attributes);
        effects = Map.copyOf(effects);
        cooldowns = Map.copyOf(cooldowns);
        metadata = Map.copyOf(metadata);
    }

    /**
     * 读取命名的元数据条目，没有收到时返回默认值
     */
    public <T> T meta(EntityMeta<T> entry) {
        return EntityMetaIndex.get(metadata, entry);
    }
}
