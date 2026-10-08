package io.ib67.yuanban.bot.observe;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Point;
import org.jetbrains.annotations.Nullable;

/**
 * Damage Event。
 *
 * @param type           伤害类型在 {@code minecraft:damage_type} 注册表中的名称
 * @param sourceId       造成伤害的实体；没有时为 null
 * @param directSourceId 直接造成伤害的实体（例如箭）；没有时为 null
 * @param sourcePos      伤害来源的位置；没有时为 null
 */
public record EntityDamaged(int entityId, Key type, @Nullable Integer sourceId,
                            @Nullable Integer directSourceId, @Nullable Point sourcePos) implements Observation {
}
