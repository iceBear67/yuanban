package io.ib67.yuanban.bot.entity;

import io.ib67.yuanban.bot.data.EntityTypeData;
import io.ib67.yuanban.bot.view.EntityMeta;
import io.ib67.yuanban.bot.view.EntityMetaIndex;
import net.minestom.server.collision.BoundingBox;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.util.Map;
import java.util.Set;

/**
 * 实体的包围盒尺寸（原版 {@code Entity.getDimensions}、{@code LivingEntity.getDimensions} 与各实体类的 {@code getDefaultDimensions}）：
 * <ul>
 *     <li>活体以外的实体：实体类型的尺寸；</li>
 *     <li>活体（原版 {@code LivingEntity}，见 {@link EntityTypeData#living}）：睡觉的姿势为固定尺寸 0.2 × 0.2；
 *     其余为默认尺寸乘以 {@code minecraft:scale} 属性，默认尺寸为固定尺寸时不乘；</li>
 *     <li>默认尺寸：玩家与人体模型（原版 {@code Avatar}）按姿势取原版的尺寸，死亡的姿势为固定尺寸 0.2 × 0.2；
 *     其余实体幼年时取原版各实体类的幼年尺寸（{@code BABY_DIMENSIONS}，没有时为实体类型的尺寸乘以 0.5），否则取实体类型的尺寸。</li>
 * </ul>
 * 幼年按原版各实体类的 {@code isBaby} 判断：{@code AgeableMob} 读取它的幼年标记（史莱姆、岩浆怪、鹦鹉、僵尸鹦鹉螺、青蛙、骆驼尸壳不会是幼年；
 * 史莱姆一类的尺寸与年龄无关），僵尸一类、猪灵、僵尸疣猪兽读取各自的幼年标记，盔甲架为小型盔甲架。
 * 其它随状态变化的尺寸本库不覆盖，取上面的尺寸。
 * <p>
 * 与原版 {@code EntityDimensions} 相同，尺寸以 float 保存与计算：实体类型的宽高换成 float，scale 属性先换成 float（原版
 * {@code LivingEntity.getScale}）再相乘。
 */
// FIXME(Minestom M12): Minestom 的实体类型数据（net.minestom:data 的 entity_type.json）只有成年实体默认姿态的尺寸，
//  没有幼年与玩家各姿势的尺寸；这里内置原版 26.2 各实体类的取值，见 MINESTOM-BUG-REPORT.md
final class EntityDimensions {
    /**
     * 原版 {@code LivingEntity.SLEEPING_DIMENSIONS} 与 {@code Avatar.POSES} 中死亡姿势的固定尺寸
     */
    private static final Size FIXED_SMALL = new Size(0.2F, 0.2F, true);
    /**
     * 原版 {@code Avatar}：玩家与人体模型
     */
    private static final Set<EntityType> AVATARS = Set.of(EntityType.PLAYER, EntityType.MANNEQUIN);
    /**
     * 幼年时的尺寸与判断幼年的元数据（原版各实体类的 {@code BABY_DIMENSIONS} 与 {@code isBaby}）；骆驼另外按姿势区分
     */
    private static final Map<EntityType, Baby> BABIES = Map.ofEntries(
            ageable(EntityType.ARMADILLO, 0.6F),
            ageable(EntityType.AXOLOTL, 0.375F, 0.21F),
            ageable(EntityType.BEE, 0.5F),
            ageable(EntityType.CAT, 0.3F, 0.35F),
            ageable(EntityType.CHICKEN, 0.3F, 0.4F),
            ageable(EntityType.COW, 0.45F, 0.7F),
            ageable(EntityType.DOLPHIN, 0.65F),
            ageable(EntityType.DONKEY, 0.5F),
            ageable(EntityType.MULE, 0.5F),
            // 原版 Llama.BABY_DIMENSIONS 按羊驼的尺寸计算，行商羊驼同样使用它
            ageable(EntityType.LLAMA, width(EntityType.LLAMA) * 0.5F, height(EntityType.LLAMA) * 0.5F),
            ageable(EntityType.TRADER_LLAMA, width(EntityType.LLAMA) * 0.5F, height(EntityType.LLAMA) * 0.5F),
            ageable(EntityType.FOX, 0.6F),
            ageable(EntityType.SQUID, 0.5F, 0.5F),
            ageable(EntityType.GLOW_SQUID, 0.5F, 0.5F),
            ageable(EntityType.GOAT, 0.45F, 0.65F),
            ageable(EntityType.HAPPY_GHAST, 0.2375F),
            ageable(EntityType.HOGLIN, 0.75F, 0.85F),
            ageable(EntityType.HORSE, 0.7F),
            ageable(EntityType.SKELETON_HORSE, 0.7F),
            ageable(EntityType.ZOMBIE_HORSE, 0.7F),
            ageable(EntityType.MOOSHROOM, 0.45F, 0.7F),
            ageable(EntityType.NAUTILUS, 0.5F),
            ageable(EntityType.OCELOT, 0.3F, 0.35F),
            ageable(EntityType.PANDA, 0.5F),
            ageable(EntityType.PIG, 0.45F, 0.45F),
            ageable(EntityType.POLAR_BEAR, 0.7F, 0.7F),
            ageable(EntityType.RABBIT, 0.24F, 0.4F),
            ageable(EntityType.SHEEP, 0.45F, 0.65F),
            ageable(EntityType.SNIFFER, 0.5F),
            ageable(EntityType.STRIDER, 0.45F, 0.85F),
            ageable(EntityType.TURTLE, 0.3F),
            ageable(EntityType.VILLAGER, 0.49F, 0.98F),
            ageable(EntityType.WANDERING_TRADER, 0.5F),
            ageable(EntityType.WOLF, 0.3F, 0.425F),
            baby(EntityType.ZOMBIE, EntityMetaIndex.ZOMBIE_IS_BABY, 0.49F, 0.98F),
            baby(EntityType.HUSK, EntityMetaIndex.ZOMBIE_IS_BABY, 0.49F, 0.98F),
            baby(EntityType.DROWNED, EntityMetaIndex.ZOMBIE_IS_BABY, 0.49F, 0.98F),
            baby(EntityType.ZOMBIE_VILLAGER, EntityMetaIndex.ZOMBIE_IS_BABY, 0.49F, 0.98F),
            baby(EntityType.ZOMBIFIED_PIGLIN, EntityMetaIndex.ZOMBIE_IS_BABY, 0.49F, 0.98F),
            baby(EntityType.PIGLIN, EntityMetaIndex.PIGLIN_IS_BABY, 0.49F, 0.98F),
            baby(EntityType.ZOGLIN, EntityMetaIndex.ZOGLIN_IS_BABY, 0.75F, 0.85F),
            baby(EntityType.ARMOR_STAND, EntityMetaIndex.ARMOR_STAND_IS_SMALL,
                    width(EntityType.ARMOR_STAND) * 0.5F, height(EntityType.ARMOR_STAND) * 0.5F));

    private EntityDimensions() {
    }

    /**
     * @param scale {@code minecraft:scale} 属性的值，只用于活体
     */
    static BoundingBox boundingBox(EntityType type, Map<Integer, EntityMetadata<?, ?>> metadata, double scale) {
        if (!EntityTypeData.living(type)) {
            // 原版 Entity.getDimensions：活体以外的实体没有属性，取实体类型的尺寸
            return new BoundingBox(width(type), height(type), width(type));
        }
        var pose = EntityMetaIndex.get(metadata, EntityMetaIndex.POSE);
        var size = pose == Pose.SLEEPING ? FIXED_SMALL : defaultSize(type, pose, metadata);
        if (!size.fixed()) {
            var factor = (float) scale;
            size = new Size(size.width() * factor, size.height() * factor, false);
        }
        return new BoundingBox(size.width(), size.height(), size.width());
    }

    /**
     * 活体的默认尺寸（原版各实体类的 {@code getDefaultDimensions} 中与姿势、幼年有关的部分）
     */
    private static Size defaultSize(EntityType type, Pose pose, Map<Integer, EntityMetadata<?, ?>> metadata) {
        if (AVATARS.contains(type)) {
            // 原版 Avatar.POSES；没有列出的姿势取站立的尺寸
            return switch (pose) {
                case SNEAKING -> new Size(0.6F, 1.5F, false);
                case FALL_FLYING, SWIMMING, SPIN_ATTACK -> new Size(0.6F, 0.6F, false);
                case DYING -> FIXED_SMALL;
                default -> new Size(0.6F, 1.8F, false);
            };
        }
        if (type == EntityType.CAMEL && EntityMetaIndex.get(metadata, EntityMetaIndex.AGEABLE_IS_BABY)) {
            // 原版 Camel.BABY_SITTING_DIMENSIONS 与 BABY_STANDING_DIMENSIONS
            return pose == Pose.SITTING ? new Size(0.95F, 0.425F, false) : new Size(0.95F, 1.4F, false);
        }
        var baby = BABIES.get(type);
        if (baby != null && EntityMetaIndex.get(metadata, baby.flag())) {
            return new Size(baby.width(), baby.height(), false);
        }
        return new Size(width(type), height(type), false);
    }

    /**
     * 实体类型的宽（原版 {@code EntityType} 的尺寸为 float）
     */
    private static float width(EntityType type) {
        return (float) EntityTypeData.width(type);
    }

    /**
     * 实体类型的高（原版 {@code EntityType} 的尺寸为 float）
     */
    private static float height(EntityType type) {
        return (float) EntityTypeData.height(type);
    }

    /**
     * {@code AgeableMob}：幼年尺寸为实体类型的尺寸乘以 factor
     */
    private static Map.Entry<EntityType, Baby> ageable(EntityType type, float factor) {
        return ageable(type, width(type) * factor, height(type) * factor);
    }

    private static Map.Entry<EntityType, Baby> ageable(EntityType type, float width, float height) {
        return baby(type, EntityMetaIndex.AGEABLE_IS_BABY, width, height);
    }

    private static Map.Entry<EntityType, Baby> baby(EntityType type, EntityMeta<Boolean> flag, float width, float height) {
        return Map.entry(type, new Baby(flag, width, height));
    }

    /**
     * @param fixed 固定尺寸（原版 {@code EntityDimensions.fixed}）：不乘以 scale 属性
     */
    private record Size(float width, float height, boolean fixed) {
    }

    private record Baby(EntityMeta<Boolean> flag, float width, float height) {
    }
}
