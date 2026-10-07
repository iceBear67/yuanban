package io.ib67.yuanban.bot.entity;

import io.ib67.yuanban.bot.player.AttributeMap;
import io.ib67.yuanban.bot.view.EntitySnapshot;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 世界模型中的一个实体。位置保存服务端最后给出的值，不插值。只在调度线程上使用。
 */
public final class ClientEntity {
    /**
     * 原版 {@code AbstractHorse} 的全部实体类型：每 tick 更新人立动画；Open Horse Screen 为它们打开马的窗口
     */
    public static final Set<EntityType> HORSES = Set.of(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE, EntityType.LLAMA, EntityType.TRADER_LLAMA,
            EntityType.CAMEL, EntityType.CAMEL_HUSK);
    /**
     * 原版 {@code AbstractNautilus} 的全部实体类型：Open Horse Screen 为它们打开鹦鹉螺的窗口
     */
    public static final Set<EntityType> NAUTILUSES = Set.of(EntityType.NAUTILUS, EntityType.ZOMBIE_NAUTILUS);

    private final int id;
    private final UUID uuid;
    private final EntityType type;
    private final int data;
    private Pos position;
    private float headYaw;
    private Vec velocity;
    private boolean onGround;
    private final Map<Integer, Metadata.Entry<?>> metadata = new Int2ObjectOpenHashMap<>();
    private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private final AttributeMap attributes = new AttributeMap(AttributeMap.DEFAULT_BASE);
    private final Map<PotionEffect, Potion> effects = new HashMap<>();
    private @Nullable Integer vehicle;
    private final IntList passengers = new IntArrayList();
    private @Nullable Integer leashHolder;
    /**
     * 马类的人立动画进度与上一 tick 的值（原版 {@code AbstractHorse.standAnim} 与 {@code standAnimO}）
     */
    private float standAnim;
    private float standAnimO;

    public ClientEntity(int id, UUID uuid, EntityType type, int data, Pos position, float headYaw, Vec velocity) {
        this.id = id;
        this.uuid = uuid;
        this.type = type;
        this.data = data;
        this.position = position;
        this.headYaw = headYaw;
        this.velocity = velocity;
    }

    public int id() {
        return id;
    }

    public EntityType type() {
        return type;
    }

    public Pos position() {
        return position;
    }

    public void position(Pos position, boolean onGround) {
        this.position = position;
        this.onGround = onGround;
    }

    public boolean onGround() {
        return onGround;
    }

    public void headYaw(float headYaw) {
        this.headYaw = headYaw;
    }

    public Vec velocity() {
        return velocity;
    }

    public void velocity(Vec velocity) {
        this.velocity = velocity;
    }

    public Map<Integer, Metadata.Entry<?>> metadata() {
        return metadata;
    }

    public Map<EquipmentSlot, ItemStack> equipment() {
        return equipment;
    }

    public AttributeMap attributes() {
        return attributes;
    }

    public Map<PotionEffect, Potion> effects() {
        return effects;
    }

    public @Nullable Integer vehicle() {
        return vehicle;
    }

    public void vehicle(@Nullable Integer vehicle) {
        this.vehicle = vehicle;
    }

    public IntList passengers() {
        return passengers;
    }

    /**
     * Link Entities：拴住它的实体；为 null 时解开
     */
    public void leashHolder(@Nullable Integer leashHolder) {
        this.leashHolder = leashHolder;
    }

    /**
     * 上一 tick 的人立动画进度（原版 {@code standAnimO}），乘客的位置使用它；不是马类时为 0
     */
    public float standAnimO() {
        return standAnimO;
    }

    /**
     * 原版客户端每 tick 对这个实体执行的 {@code tick()} 中本库模拟的部分（{@link io.ib67.yuanban.bot.world.ClientWorld#tickEntities}）：
     * 马类（原版 {@code AbstractHorse}）的人立动画，人立（元数据标记 0x20）时向 1 靠近，否则向 0 回落
     */
    public void tick() {
        if (!HORSES.contains(type)) {
            return;
        }
        standAnimO = standAnim;
        var flags = metadata.get(MetadataDef.AbstractHorse.ABSTRACT_HORSE_FLAGS.index());
        if (flags != null && ((Byte) flags.value() & 0x20) != 0) {
            standAnim = standAnim + ((1.0F - standAnim) * 0.4F + 0.05F);
            if (standAnim > 1.0F) {
                standAnim = 1.0F;
            }
        } else {
            standAnim = standAnim + ((0.8F * standAnim * standAnim * standAnim - standAnim) * 0.6F - 0.05F);
            if (standAnim < 0.0F) {
                standAnim = 0.0F;
            }
        }
    }

    /**
     * 包围盒：按原版的姿势、幼年与固定尺寸计算，活体的非固定尺寸乘以 {@code minecraft:scale} 属性，见 {@link EntityDimensions}
     */
    public BoundingBox boundingBox() {
        return EntityDimensions.boundingBox(type, metadata, attributes.value(Attribute.SCALE));
    }

    public EntitySnapshot snapshot() {
        return new EntitySnapshot(id, uuid, type, data, position, headYaw, velocity, onGround,
                metadata, equipment, attributes.values(), effects, vehicle, passengers, leashHolder, boundingBox());
    }
}
