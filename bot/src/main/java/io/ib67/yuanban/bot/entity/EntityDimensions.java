package io.ib67.yuanban.bot.entity;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 实体的包围盒尺寸（原版 {@code Entity.getDimensions}、{@code LivingEntity.getDimensions} 与各实体类的 {@code getDefaultDimensions}）：
 * <ul>
 *     <li>活体以外的实体：实体类型的尺寸；</li>
 *     <li>活体（原版 {@code LivingEntity}，即 Minestom 数据中同步属性的实体类型）：睡觉的姿势为固定尺寸 0.2 × 0.2；
 *     其余为默认尺寸乘以 {@code minecraft:scale} 属性，默认尺寸为固定尺寸时不乘；</li>
 *     <li>默认尺寸：玩家与人体模型（原版 {@code Avatar}）按姿势取原版的尺寸，死亡的姿势为固定尺寸 0.2 × 0.2；
 *     其余实体幼年时取原版各实体类的幼年尺寸（{@code BABY_DIMENSIONS}，没有时为实体类型的尺寸乘以 0.5），否则取实体类型的尺寸。</li>
 * </ul>
 * 幼年按原版各实体类的 {@code isBaby} 判断：{@code AgeableMob} 读取它的幼年标记（史莱姆、岩浆怪、鹦鹉、僵尸鹦鹉螺、青蛙、骆驼尸壳不会是幼年；
 * 史莱姆一类的尺寸与年龄无关），僵尸一类、猪灵、僵尸疣猪兽读取各自的幼年标记，盔甲架为小型盔甲架。
 * 其它随状态变化的尺寸本库不覆盖，取上面的尺寸。
 */
// FIXME(Minestom M12): Minestom 的实体类型数据只有成年实体默认姿态的尺寸，没有幼年与玩家各姿势的尺寸；
//  这里内置原版 26.2 各实体类的取值，见 MINESTOM-BUG-REPORT.md
final class EntityDimensions {
    /**
     * 原版 {@code LivingEntity.SLEEPING_DIMENSIONS} 与 {@code Avatar.POSES} 中死亡姿势的固定尺寸
     */
    private static final Size FIXED_SMALL = new Size(0.2, 0.2, true);
    /**
     * 原版 {@code Avatar}：玩家与人体模型
     */
    private static final Set<EntityType> AVATARS = Set.of(EntityType.PLAYER, EntityType.MANNEQUIN);
    /**
     * 幼年时的尺寸与判断幼年的元数据（原版各实体类的 {@code BABY_DIMENSIONS} 与 {@code isBaby}）；骆驼另外按姿势区分
     */
    private static final Map<EntityType, Baby> BABIES = Map.ofEntries(
            ageable(EntityType.ARMADILLO, 0.6),
            ageable(EntityType.AXOLOTL, 0.375, 0.21),
            ageable(EntityType.BEE, 0.5),
            ageable(EntityType.CAT, 0.3, 0.35),
            ageable(EntityType.CHICKEN, 0.3, 0.4),
            ageable(EntityType.COW, 0.45, 0.7),
            ageable(EntityType.DOLPHIN, 0.65),
            ageable(EntityType.DONKEY, 0.5),
            ageable(EntityType.MULE, 0.5),
            // 原版 Llama.BABY_DIMENSIONS 按羊驼的尺寸计算，行商羊驼同样使用它
            ageable(EntityType.LLAMA, EntityType.LLAMA.width() * 0.5, EntityType.LLAMA.height() * 0.5),
            ageable(EntityType.TRADER_LLAMA, EntityType.LLAMA.width() * 0.5, EntityType.LLAMA.height() * 0.5),
            ageable(EntityType.FOX, 0.6),
            ageable(EntityType.SQUID, 0.5, 0.5),
            ageable(EntityType.GLOW_SQUID, 0.5, 0.5),
            ageable(EntityType.GOAT, 0.45, 0.65),
            ageable(EntityType.HAPPY_GHAST, 0.2375),
            ageable(EntityType.HOGLIN, 0.75, 0.85),
            ageable(EntityType.HORSE, 0.7),
            ageable(EntityType.SKELETON_HORSE, 0.7),
            ageable(EntityType.ZOMBIE_HORSE, 0.7),
            ageable(EntityType.MOOSHROOM, 0.45, 0.7),
            ageable(EntityType.NAUTILUS, 0.5),
            ageable(EntityType.OCELOT, 0.3, 0.35),
            ageable(EntityType.PANDA, 0.5),
            ageable(EntityType.PIG, 0.45, 0.45),
            ageable(EntityType.POLAR_BEAR, 0.7, 0.7),
            ageable(EntityType.RABBIT, 0.24, 0.4),
            ageable(EntityType.SHEEP, 0.45, 0.65),
            ageable(EntityType.SNIFFER, 0.5),
            ageable(EntityType.STRIDER, 0.45, 0.85),
            ageable(EntityType.TURTLE, 0.3),
            ageable(EntityType.VILLAGER, 0.49, 0.98),
            ageable(EntityType.WANDERING_TRADER, 0.5),
            ageable(EntityType.WOLF, 0.3, 0.425),
            baby(EntityType.ZOMBIE, MetadataDef.Zombie.IS_BABY, 0.49, 0.98),
            baby(EntityType.HUSK, MetadataDef.Zombie.IS_BABY, 0.49, 0.98),
            baby(EntityType.DROWNED, MetadataDef.Zombie.IS_BABY, 0.49, 0.98),
            baby(EntityType.ZOMBIE_VILLAGER, MetadataDef.Zombie.IS_BABY, 0.49, 0.98),
            baby(EntityType.ZOMBIFIED_PIGLIN, MetadataDef.Zombie.IS_BABY, 0.49, 0.98),
            baby(EntityType.PIGLIN, MetadataDef.Piglin.IS_BABY, 0.49, 0.98),
            baby(EntityType.ZOGLIN, MetadataDef.Zoglin.IS_BABY, 0.75, 0.85),
            baby(EntityType.ARMOR_STAND, MetadataDef.ArmorStand.IS_SMALL,
                    EntityType.ARMOR_STAND.width() * 0.5, EntityType.ARMOR_STAND.height() * 0.5));

    private EntityDimensions() {
    }

    /**
     * @param scale {@code minecraft:scale} 属性的值，只用于活体
     */
    static BoundingBox boundingBox(EntityType type, Map<Integer, Metadata.Entry<?>> metadata, double scale) {
        if (!type.shouldSendAttributes()) {
            // 原版 Entity.getDimensions：活体以外的实体没有属性，取实体类型的尺寸
            return new BoundingBox(type.width(), type.height(), type.width());
        }
        var pose = Objects.requireNonNullElse(metaOrNull(metadata, MetadataDef.POSE), MetadataDef.POSE.defaultValue());
        var size = pose == EntityPose.SLEEPING ? FIXED_SMALL : defaultSize(type, pose, metadata);
        if (!size.fixed()) {
            size = new Size(size.width() * scale, size.height() * scale, false);
        }
        return new BoundingBox(size.width(), size.height(), size.width());
    }

    /**
     * 活体的默认尺寸（原版各实体类的 {@code getDefaultDimensions} 中与姿势、幼年有关的部分）
     */
    private static Size defaultSize(EntityType type, EntityPose pose, Map<Integer, Metadata.Entry<?>> metadata) {
        if (AVATARS.contains(type)) {
            // 原版 Avatar.POSES；没有列出的姿势取站立的尺寸
            return switch (pose) {
                case SNEAKING -> new Size(0.6, 1.5, false);
                case FALL_FLYING, SWIMMING, SPIN_ATTACK -> new Size(0.6, 0.6, false);
                case DYING -> FIXED_SMALL;
                default -> new Size(0.6, 1.8, false);
            };
        }
        if (type == EntityType.CAMEL && flag(metadata, MetadataDef.AgeableMob.IS_BABY)) {
            // 原版 Camel.BABY_SITTING_DIMENSIONS 与 BABY_STANDING_DIMENSIONS
            return pose == EntityPose.SITTING ? new Size(0.95, 0.425, false) : new Size(0.95, 1.4, false);
        }
        var baby = BABIES.get(type);
        if (baby != null && flag(metadata, baby.flag())) {
            return new Size(baby.width(), baby.height(), false);
        }
        return new Size(type.width(), type.height(), false);
    }

    /**
     * {@code AgeableMob}：幼年尺寸为实体类型的尺寸乘以 factor
     */
    private static Map.Entry<EntityType, Baby> ageable(EntityType type, double factor) {
        return ageable(type, type.width() * factor, type.height() * factor);
    }

    private static Map.Entry<EntityType, Baby> ageable(EntityType type, double width, double height) {
        return baby(type, MetadataDef.AgeableMob.IS_BABY, width, height);
    }

    private static Map.Entry<EntityType, Baby> baby(EntityType type, MetadataDef.Entry<Boolean> flag, double width, double height) {
        return Map.entry(type, new Baby(flag, width, height));
    }

    private static boolean flag(Map<Integer, Metadata.Entry<?>> metadata, MetadataDef.Entry<Boolean> entry) {
        var value = metadata.get(entry.index());
        if (value == null) {
            return Objects.requireNonNull(entry.defaultValue());
        }
        if (entry instanceof MetadataDef.Entry.BitMask mask) {
            return ((Byte) value.value() & mask.bitMask()) != 0;
        }
        return (Boolean) value.value();
    }

    @SuppressWarnings("unchecked")
    private static <T> T metaOrNull(Map<Integer, Metadata.Entry<?>> metadata, MetadataDef.Entry<T> entry) {
        var value = metadata.get(entry.index());
        return value == null ? null : (T) value.value();
    }

    /**
     * @param fixed 固定尺寸（原版 {@code EntityDimensions.fixed}）：不乘以 scale 属性
     */
    private record Size(double width, double height, boolean fixed) {
    }

    private record Baby(MetadataDef.Entry<Boolean> flag, double width, double height) {
    }
}
