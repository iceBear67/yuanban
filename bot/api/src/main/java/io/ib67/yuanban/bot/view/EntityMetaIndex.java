package io.ib67.yuanban.bot.view;

import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;

import java.util.Map;

/**
 * 本库读取的实体元数据条目（wiki Entity metadata；索引包括父类的条目）。
 */
public final class EntityMetaIndex {
    /**
     * Entity：无重力
     */
    public static final EntityMeta<Boolean> HAS_NO_GRAVITY = EntityMeta.index(5, false);
    /**
     * Entity：姿势
     */
    public static final EntityMeta<Pose> POSE = EntityMeta.index(6, Pose.STANDING);
    /**
     * LivingEntity：生命值
     */
    public static final EntityMeta<Float> LIVING_HEALTH = EntityMeta.index(9, 1F);
    /**
     * AbstractArrow（箭、光灵箭、三叉戟）：插在方块上
     */
    public static final EntityMeta<Boolean> ARROW_IN_GROUND = EntityMeta.index(10, false);
    /**
     * ArmorStand：小型盔甲架
     */
    public static final EntityMeta<Boolean> ARMOR_STAND_IS_SMALL = EntityMeta.bit(15, 0x01);
    /**
     * AgeableMob：幼年
     */
    public static final EntityMeta<Boolean> AGEABLE_IS_BABY = EntityMeta.index(16, false);
    /**
     * Zombie：幼年
     */
    public static final EntityMeta<Boolean> ZOMBIE_IS_BABY = EntityMeta.index(16, false);
    /**
     * Zoglin：幼年
     */
    public static final EntityMeta<Boolean> ZOGLIN_IS_BABY = EntityMeta.index(16, false);
    /**
     * Piglin：幼年
     */
    public static final EntityMeta<Boolean> PIGLIN_IS_BABY = EntityMeta.index(17, false);
    /**
     * AbstractCubeMob（史莱姆、岩浆怪）：大小
     */
    public static final EntityMeta<Integer> CUBE_SIZE = EntityMeta.index(18, 1);
    /**
     * AbstractHorse：标记字节
     */
    public static final EntityMeta<Byte> HORSE_FLAGS = EntityMeta.index(18, (byte) 0);
    /**
     * AbstractHorse：已驯服
     */
    public static final EntityMeta<Boolean> HORSE_IS_TAME = EntityMeta.bit(18, 0x02);
    /**
     * AbstractHorse：正在人立
     */
    public static final EntityMeta<Boolean> HORSE_IS_REARING = EntityMeta.bit(18, 0x20);
    /**
     * TameableAnimal（包括鹦鹉螺）：已驯服
     */
    public static final EntityMeta<Boolean> TAMEABLE_IS_TAMED = EntityMeta.bit(18, 0x04);
    /**
     * HappyGhast：保持静止
     */
    public static final EntityMeta<Boolean> HAPPY_GHAST_STAYS_STILL = EntityMeta.index(19, false);
    /**
     * Camel：上一次改变姿势的游戏刻
     */
    public static final EntityMeta<Long> CAMEL_LAST_POSE_CHANGE_TICK = EntityMeta.index(20, 0L);

    private EntityMetaIndex() {
    }

    /**
     * 读取条目，没有收到时返回默认值
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Map<Integer, EntityMetadata<?, ?>> metadata, EntityMeta<T> entry) {
        var value = metadata.get(entry.index());
        if (value == null) {
            return entry.defaultValue();
        }
        if (entry.mask() == 0) {
            return (T) value.getValue();
        }
        return (T) (Boolean) (((Byte) value.getValue() & entry.mask()) != 0);
    }
}
