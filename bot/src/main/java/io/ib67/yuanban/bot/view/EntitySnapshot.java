package io.ib67.yuanban.bot.view;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.item.ItemStack;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
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
 * @param metadata    元数据，按索引；通过 {@link #meta(MetadataDef.Entry)} 读取命名条目
 * @param effects     药水效果，{@link Potion#duration()} 为收到时的持续时间
 * @param vehicle     乘坐的实体；没有乘坐时为 null
 * @param leashHolder 拴住它的实体（Link Entities）；没有时为 null
 * @param boundingBox 包围盒：活体按原版的姿势、幼年与固定尺寸计算，非固定尺寸乘以 {@code minecraft:scale} 属性；
 *                    其余实体为 {@link EntityType} 的宽高
 */
public record EntitySnapshot(int entityId, UUID uuid, EntityType type, int data,
                             Pos position, float headYaw, Vec velocity, boolean onGround,
                             Map<Integer, Metadata.Entry<?>> metadata,
                             Map<EquipmentSlot, ItemStack> equipment,
                             Map<Attribute, Double> attributes,
                             Map<PotionEffect, Potion> effects,
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
    @SuppressWarnings("unchecked")
    public <T> T meta(MetadataDef.Entry<T> entry) {
        var value = metadata.get(entry.index());
        if (value == null) {
            return entry.defaultValue();
        }
        return switch (entry) {
            case MetadataDef.Entry.Index<T> _ -> (T) value.value();
            case MetadataDef.Entry.BitMask bitMask -> {
                var mask = (byte) value.value();
                yield (T) (Boolean) ((mask & bitMask.bitMask()) == bitMask.bitMask());
            }
            case MetadataDef.Entry.ByteMask byteMask -> {
                var mask = (byte) value.value();
                yield (T) (Byte) (byte) ((mask & byteMask.byteMask()) >> byteMask.offset());
            }
        };
    }

    /**
     * 包围盒的中心
     */
    public Vec center() {
        return position.asVec().add(0, boundingBox.height() / 2, 0);
    }
}
