package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.data.Items;
import io.ib67.yuanban.bot.entity.ClientEntity;
import io.ib67.yuanban.bot.inventory.Window;
import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.scheduler.BotState;
import io.ib67.yuanban.bot.view.EntityMetaIndex;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.AttackRange;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 26.2 客户端判断能否攻击目标实体的距离部分：准星选取（{@code LocalPlayer.raycastHitResult}）选中目标的位置，
 * 以及 {@code Minecraft.startAttack} 按主手物品的 {@code attack_range} 组件检查这个位置的距离。
 * 只对目标实体计算：不计算射线上的方块与其它实体，也不判断目标能否被选取（原版 {@code isPickable}，选取半径除外）。
 */
final class AttackReach {
    private static final Key ENTITY_TYPE_REGISTRY = Key.key("entity_type");
    private static final Key REDIRECTABLE_PROJECTILE = Key.key("redirectable_projectile");
    /**
     * 原版 26.2 中继承 {@code Projectile} 的实体：可选取时选取半径为 1（{@code Projectile.getPickRadius}）
     */
    private static final Set<EntityType> PROJECTILES = EnumSet.of(EntityType.ARROW, EntityType.SPECTRAL_ARROW,
            EntityType.TRIDENT, EntityType.FIREWORK_ROCKET, EntityType.FISHING_BOBBER, EntityType.DRAGON_FIREBALL,
            EntityType.FIREBALL, EntityType.SMALL_FIREBALL, EntityType.BREEZE_WIND_CHARGE, EntityType.WIND_CHARGE,
            EntityType.WITHER_SKULL, EntityType.LLAMA_SPIT, EntityType.SHULKER_BULLET, EntityType.SNOWBALL, EntityType.EGG,
            EntityType.ENDER_PEARL, EntityType.EXPERIENCE_BOTTLE, EntityType.LINGERING_POTION, EntityType.SPLASH_POTION);
    /**
     * 原版 26.2 中继承 {@code AbstractArrow} 的实体：插在方块上时不可选取
     */
    private static final Set<EntityType> ARROWS = EnumSet.of(EntityType.ARROW, EntityType.SPECTRAL_ARROW, EntityType.TRIDENT);

    private AttackReach() {
    }

    /**
     * 朝向为 rotation 时，原版客户端是否在攻击距离内选中 target 并攻击它：
     * <ol>
     *     <li>当前物品有 {@code attack_range} 组件时，先沿它的距离选取（{@code AttackRange.getClosesetHit}）；
     *     手部队列一次运行一个动作，攻击时不在使用物品，当前物品就是主手物品（{@code LivingEntity.getActiveItem}，旁观模式为空）；</li>
     *     <li>没有这个组件或者没有选中时，按方块与实体交互距离中较大的一个沿视线选取，选中的位置到眼睛的距离小于实体交互距离才算选中
     *     （{@code LocalPlayer.pick}、{@code filterHitResult}）；</li>
     *     <li>选中之后，非旁观模式下主手物品有 {@code attack_range} 组件时，选中的位置到眼睛的距离还要在它的范围内（{@code startAttack}）。</li>
     * </ol>
     */
    static boolean inRange(BotState state, ClientEntity target, Rotation rotation) {
        var player = state.player();
        var eye = player.eyePosition();
        var look = rotation.viewVector();
        var spectator = player.gameMode() == GameMode.SPECTATOR;
        var creative = player.gameMode() == GameMode.CREATIVE;
        var range = spectator ? null
                : Items.component(state.inventory().player().get(Window.PLAYER_HOTBAR_START + player.heldSlot()),
                DataComponentTypes.ATTACK_RANGE);
        var box = Box.of(target);
        Vec hit = null;
        if (range != null) {
            hit = alongRange(eye, look, player.velocity(), range, creative, box);
        }
        if (hit == null) {
            hit = pick(state, eye, look, target, box);
        }
        if (hit == null) {
            return false;
        }
        if (range == null) {
            return true;
        }
        // 原版 AttackRange.isInRange(LivingEntity, Vec3)：距离在 [最小距离 − hitboxMargin, 最大距离 + hitboxMargin] 之内
        var distance = Math.sqrt(hit.distanceSquared(eye));
        return distance >= (double) (minRange(range, creative) - range.hitboxMargin())
                && distance <= (double) (maxRange(range, creative) + range.hitboxMargin());
    }

    /**
     * 原版 {@code AttackRange.getClosesetHit}（{@code ProjectileUtil.getHitEntitiesAlong}、{@code getManyEntityHitResult}）对目标的部分：
     * 线段从 眼睛 + 视线 × 最小距离 到 眼睛 + 视线 × (最大距离 + max(0, 速度 · 视线))；起点在包围盒内时选中起点，
     * 否则取线段与包围盒的交点；都没有时，线段与按 hitboxMargin 扩大的包围盒相交，就从这个交点向包围盒中心取与包围盒的交点
     *
     * @return 选中的位置；没有选中时为 null
     */
    private static @Nullable Vec alongRange(Vec eye, Vec look, Vec velocity, AttackRange range, boolean creative, Box box) {
        var from = eye.add(look.mul(minRange(range, creative)));
        var to = eye.add(look.mul(maxRange(range, creative) + Math.max(0.0, velocity.dot(look))));
        if (box.contains(from)) {
            return from;
        }
        var hit = box.clip(from, to);
        if (hit != null || range.hitboxMargin() <= 0.0F) {
            return hit;
        }
        var near = box.inflate(range.hitboxMargin()).clip(from, to);
        if (near == null) {
            return null;
        }
        return box.clip(near, box.center());
    }

    /**
     * 原版 {@code LocalPlayer.pick} 与 {@code ProjectileUtil.getEntityHitResult} 对目标的部分：线段从眼睛沿视线到方块与实体交互距离中
     * 较大的一个，目标包围盒按选取半径扩大。眼睛在框内时，可以从内部选取的目标选中位置为线段与框的交点（没有时为眼睛）；
     * 否则取交点，目标与玩家骑乘同一个最下层载具时不选中。选中的位置到眼睛的距离平方小于实体交互距离的平方才算选中
     *
     * @return 选中的位置；没有选中时为 null
     */
    private static @Nullable Vec pick(BotState state, Vec eye, Vec look, ClientEntity target, Box box) {
        var attributes = state.player().attributes();
        var entityRange = attributes.value(AttributeType.Builtin.ENTITY_INTERACTION_RANGE);
        var maxRange = Math.max(attributes.value(AttributeType.Builtin.BLOCK_INTERACTION_RANGE), entityRange);
        var to = eye.add(look.mul(maxRange));
        var pickBox = box.inflate(pickRadius(state, target));
        var clip = pickBox.clip(eye, to);
        Vec hit;
        if (pickBox.contains(eye)) {
            if (!canBePickedFromInside(target)) {
                return null;
            }
            hit = clip != null ? clip : eye;
        } else {
            if (clip == null || rootVehicle(state, target.id()) == rootVehicle(state, state.player().entityId())) {
                return null;
            }
            hit = clip;
        }
        return hit.distanceSquared(eye) < entityRange * entityRange ? hit : null;
    }

    private static float minRange(AttackRange range, boolean creative) {
        return creative ? range.minCreativeReach() : range.minReach();
    }

    private static float maxRange(AttackRange range, boolean creative) {
        return creative ? range.maxCreativeReach() : range.maxReach();
    }

    /**
     * 原版 {@code Entity.getPickRadius}：可选取的 {@code Projectile} 为 1，其余为 0。{@code Projectile.isPickable} 为实体类型属于
     * {@code minecraft:redirectable_projectile}；潜影贝子弹总是可选取；箭类还要求没有插在方块上
     */
    private static double pickRadius(BotState state, ClientEntity target) {
        var type = target.type();
        if (!PROJECTILES.contains(type)) {
            return 0.0;
        }
        var pickable = type == EntityType.SHULKER_BULLET
                || state.tags().contains(ENTITY_TYPE_REGISTRY, REDIRECTABLE_PROJECTILE, type.ordinal())
                && !(ARROWS.contains(type) && EntityMetaIndex.get(target.metadata(), EntityMetaIndex.ARROW_IN_GROUND));
        return pickable ? 1.0 : 0.0;
    }

    /**
     * 原版 {@code Entity.canBePickedFromInside}：只有身体槽位有物品的硫磺立方不能从内部选取
     */
    private static boolean canBePickedFromInside(ClientEntity target) {
        return target.type() != EntityType.SULFUR_CUBE || target.equipment().get(EquipmentSlot.BODY) == null;
    }

    /**
     * 最下层载具的实体编号；没有乘坐时为自己
     */
    private static int rootVehicle(BotState state, int entityId) {
        Integer vehicle = entityId == state.player().entityId() ? state.player().vehicle()
                : Objects.requireNonNull(state.world().entities().get(entityId), () -> "unknown entity " + entityId).vehicle();
        var root = entityId;
        while (vehicle != null) {
            root = vehicle;
            var below = root;
            vehicle = Objects.requireNonNull(state.world().entities().get(below), () -> "unknown vehicle " + below).vehicle();
        }
        return root;
    }

    /**
     * 原版 {@code AABB} 中这里用到的部分
     */
    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        /**
         * 实体位置上的包围盒
         */
        static Box of(ClientEntity entity) {
            var position = entity.position();
            var box = entity.boundingBox();
            return new Box(position.x() + box.minX(), position.y() + box.minY(), position.z() + box.minZ(),
                    position.x() + box.maxX(), position.y() + box.maxY(), position.z() + box.maxZ());
        }

        Box inflate(double amount) {
            return new Box(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
        }

        boolean contains(Vec point) {
            return point.x() >= minX && point.x() < maxX && point.y() >= minY && point.y() < maxY
                    && point.z() >= minZ && point.z() < maxZ;
        }

        Vec center() {
            return new Vec((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
        }

        /**
         * 原版 {@code AABB.clip}：线段从 from 到 to 第一次进入这个框的位置；不进入时为 null
         */
        @Nullable Vec clip(Vec from, Vec to) {
            var scale = new double[]{1.0};
            var dx = to.x() - from.x();
            var dy = to.y() - from.y();
            var dz = to.z() - from.z();
            var hit = false;
            if (dx > 1.0E-7) {
                hit |= clipPoint(scale, dx, dy, dz, minX, minY, maxY, minZ, maxZ, from.x(), from.y(), from.z());
            } else if (dx < -1.0E-7) {
                hit |= clipPoint(scale, dx, dy, dz, maxX, minY, maxY, minZ, maxZ, from.x(), from.y(), from.z());
            }
            if (dy > 1.0E-7) {
                hit |= clipPoint(scale, dy, dz, dx, minY, minZ, maxZ, minX, maxX, from.y(), from.z(), from.x());
            } else if (dy < -1.0E-7) {
                hit |= clipPoint(scale, dy, dz, dx, maxY, minZ, maxZ, minX, maxX, from.y(), from.z(), from.x());
            }
            if (dz > 1.0E-7) {
                hit |= clipPoint(scale, dz, dx, dy, minZ, minX, maxX, minY, maxY, from.z(), from.x(), from.y());
            } else if (dz < -1.0E-7) {
                hit |= clipPoint(scale, dz, dx, dy, maxZ, minX, maxX, minY, maxY, from.z(), from.x(), from.y());
            }
            if (!hit) {
                return null;
            }
            return from.add(scale[0] * dx, scale[0] * dy, scale[0] * dz);
        }

        /**
         * 原版 {@code AABB.clipPoint}：与 a 轴上的平面 planeA 的交点在另外两个轴的范围内（各放宽 1.0E-7），并且比已有的交点更近时记下它
         */
        private static boolean clipPoint(double[] scale, double da, double db, double dc, double planeA,
                                         double minB, double maxB, double minC, double maxC,
                                         double startA, double startB, double startC) {
            var s = (planeA - startA) / da;
            var b = startB + s * db;
            var c = startC + s * dc;
            if (0.0 < s && s < scale[0] && minB - 1.0E-7 < b && b < maxB + 1.0E-7 && minC - 1.0E-7 < c && c < maxC + 1.0E-7) {
                scale[0] = s;
                return true;
            }
            return false;
        }
    }
}
