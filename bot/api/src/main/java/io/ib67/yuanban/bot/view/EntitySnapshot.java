package io.ib67.yuanban.bot.view;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.ObjectData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一个实体的快照。位置为服务端最后给出的值，不插值。
 *
 * @param data        Spawn Entity 的附加数据
 * @param position    位置与朝向（身体）
 * @param velocity    Spawn Entity、Set Entity Velocity、Synchronize Vehicle Position 最后给出的速度（原版不使用 Teleport Entity 中的速度）
 * @param metadata    元数据，按索引；通过 {@link #meta(EntityMeta)} 读取命名条目
 * @param equipment   装备，没有装备的槽位不在其中
 * @param effects     药水效果，{@link ActiveEffect#duration()} 为收到时的持续时间
 * @param vehicle     乘坐的实体；没有乘坐时为 null
 * @param leashHolder 拴住它的实体（Link Entities）；没有时为 null
 * @param boundingBox 包围盒：活体按原版的姿势、幼年与固定尺寸计算，非固定尺寸乘以 {@code minecraft:scale} 属性；
 *                    其余实体为实体类型的宽高
 */
public record EntitySnapshot(int entityId, UUID uuid, EntityType type, ObjectData data,
                             Pos position, float headYaw, Vec velocity, boolean onGround,
                             Map<Integer, EntityMetadata<?, ?>> metadata,
                             Map<EquipmentSlot, ItemStack> equipment,
                             Map<AttributeType, Double> attributes,
                             Map<Effect, ActiveEffect> effects,
                             @Nullable Integer vehicle, List<Integer> passengers,
                             @Nullable Integer leashHolder,
                             BoundingBox boundingBox) {
    public EntitySnapshot {
        metadata = Map.copyOf(metadata);
        equipment = Map.copyOf(equipment);
        attributes = Map.copyOf(attributes);
        effects = Map.copyOf(effects);
        passengers = List.copyOf(passengers);
    }

    /**
     * 读取命名的元数据条目，没有收到时返回默认值
     */
    public <T> T meta(EntityMeta<T> entry) {
        return EntityMetaIndex.get(metadata, entry);
    }

    /**
     * 包围盒的中心
     */
    public Vec center() {
        return position.asVec().add(0, boundingBox.height() / 2, 0);
    }
}
