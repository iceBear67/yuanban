package io.ib67.yuanban.bot.observe;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

/**
 * Damage Event。
 */
public record EntityDamaged(int entityId, RegistryKey<DamageType> type, @Nullable Integer sourceId,
                            @Nullable Integer directSourceId, @Nullable Point sourcePos) implements Observation {
}
