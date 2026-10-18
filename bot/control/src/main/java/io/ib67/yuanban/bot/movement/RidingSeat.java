package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.data.EntityTypeData;
import io.ib67.yuanban.bot.entity.ClientEntity;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.view.EntityMeta;
import io.ib67.yuanban.bot.view.EntityMetaIndex;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 骑乘时本地玩家的位置（原版 {@code Entity.positionRider}）：载具位置 + 载具的乘客挂点 − 玩家的载具挂点。
 * <ul>
 *     <li>默认的乘客挂点按玩家在载具乘客列表中的序号取（超出时取最后一个，原版 {@code EntityAttachments.getClamped}），
 *     随载具的偏航角旋转；活体载具的尺寸与挂点乘以 scale 属性，幼年时换成幼年的尺寸；</li>
 *     <li>船与竹筏、史莱姆与岩浆怪、骆驼按原版各自的计算覆盖默认值；马、驴、骡、骷髅马、僵尸马在默认值上加上
 *     人立动画的偏移；羊驼、矿车、鸡使用默认值；</li>
 *     <li>玩家的载具挂点为 (0, 0.6, 0) 乘以玩家的 scale 属性。</li>
 * </ul>
 * 偏航角的旋转使用 {@link SineTable}。载具位置取模型中服务端最后给出的值，不插值（7.4）。
 */
// FIXME(Minestom M12): Minestom 的实体类型数据（net.minestom:data 的 entity_type.json）只有成年实体默认姿态的尺寸与挂点，
//  没有幼年与坐下姿态的尺寸和挂点；这里内置原版 26.2 各实体类的取值，见 MINESTOM-BUG-REPORT.md
public final class RidingSeat {
    /**
     * 原版 {@code Player.DEFAULT_VEHICLE_ATTACHMENT}
     */
    private static final Vec PLAYER_VEHICLE_ATTACHMENT = new Vec(0.0, 0.6, 0.0);
    /**
     * 在默认挂点上加人立动画偏移的 {@code AbstractHorse}（羊驼与骆驼覆盖了挂点的计算，不包括在内）
     */
    private static final Set<EntityType> REARING_HORSES = Set.of(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE);
    static final Set<EntityType> CAMELS = Set.of(EntityType.CAMEL, EntityType.CAMEL_HUSK);
    private static final Set<EntityType> CUBE_MOBS = Set.of(EntityType.SLIME, EntityType.MAGMA_CUBE);
    /**
     * 本库识别幼年状态的载具类型（原版中都是 {@code AgeableMob}）；其余实体按成年处理。
     * 史莱姆与岩浆怪虽然在原版中继承 {@code AgeableMob}，但 {@code canBeABaby} 为 false，幼年标记恒为假，不列入
     */
    private static final Set<EntityType> AGEABLE = Set.of(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE, EntityType.LLAMA, EntityType.TRADER_LLAMA,
            EntityType.CAMEL, EntityType.CAMEL_HUSK, EntityType.PIG, EntityType.STRIDER, EntityType.CHICKEN,
            EntityType.HAPPY_GHAST, EntityType.NAUTILUS, EntityType.ZOMBIE_NAUTILUS);
    static final Set<EntityType> BOATS = Arrays.stream(EntityType.values())
            .filter(type -> name(type).endsWith("_boat") || name(type).endsWith("_raft"))
            .collect(Collectors.toUnmodifiableSet());

    private RidingSeat() {
    }

    /**
     * 船与竹筏（原版 {@code AbstractBoat}）
     */
    public static boolean boat(EntityType type) {
        return BOATS.contains(type);
    }

    /**
     * @param gameTime 世界的 World Age，骆驼的姿态动画使用
     */
    static Pos seat(ClientEntity vehicle, LocalPlayer player, long gameTime) {
        var point = passengerPoint(vehicle, player.entityId(), gameTime);
        var playerAttachment = PLAYER_VEHICLE_ATTACHMENT.mul((float) player.scale());
        var vehiclePosition = vehicle.position();
        return player.position().withCoord(
                vehiclePosition.x() + point.x() - playerAttachment.x(),
                vehiclePosition.y() + point.y() - playerAttachment.y(),
                vehiclePosition.z() + point.z() - playerAttachment.z());
    }

    private static Vec passengerPoint(ClientEntity vehicle, int passengerId, long gameTime) {
        var type = vehicle.type();
        var passengers = vehicle.passengers();
        var index = passengers.indexOf(passengerId);
        var yaw = vehicle.position().yaw();
        if (BOATS.contains(type)) {
            return boatPoint(type, index, passengers.size(), yaw);
        }
        var scale = (float) vehicle.attributes().value(AttributeType.Builtin.SCALE);
        var baby = AGEABLE.contains(type) && meta(vehicle, EntityMetaIndex.AGEABLE_IS_BABY);
        if (CUBE_MOBS.contains(type)) {
            // 原版 AbstractCubeMob：尺寸按大小缩放，挂点在顶面向下 size / 64 处，不随偏航角旋转
            int size = meta(vehicle, EntityMetaIndex.CUBE_SIZE);
            var height = (float) EntityTypeData.height(type) * size * scale;
            return new Vec(0.0, height - 0.015625 * size * scale, 0.0);
        }
        if (CAMELS.contains(type)) {
            return camelPoint(vehicle, Math.max(index, 0), passengers.size(), scale, baby, yaw, gameTime);
        }
        var attachments = baby ? babyPassengerAttachments(type) : adultPassengerAttachments(type);
        var point = attachments.get(Math.clamp(index, 0, attachments.size() - 1)).mul(scale);
        point = rotate(point, yaw);
        if (REARING_HORSES.contains(type)) {
            // 原版 AbstractHorse.getPassengerAttachmentPoint：人立时乘客随之后仰，使用载具上一 tick 的人立动画进度
            var animScale = scale * ageScale(type, baby);
            var standAnim = vehicle.standAnimO();
            point = point.add(rotate(new Vec(0.0, 0.15 * standAnim * animScale, -0.7 * standAnim * animScale), yaw));
        }
        return point;
    }

    /**
     * 原版 {@code AbstractBoat.getPassengerAttachmentPoint}：船与运输船高度的三分之一，竹筏高度的 0.8888889；
     * 只有一个乘客时向前偏移 0（带箱子的为 0.15），多个乘客时第一个为 0.2、其余为 -0.6
     */
    private static Vec boatPoint(EntityType type, int index, int passengerCount, float yaw) {
        var name = name(type);
        var chest = name.endsWith("_chest_boat") || name.endsWith("_chest_raft");
        var height = (float) EntityTypeData.height(type);
        float rideHeight = name.endsWith("_raft") ? height * 0.8888889F : height / 3.0F;
        var offset = chest ? 0.15F : 0.0F;
        if (passengerCount > 1) {
            offset = index == 0 ? 0.2F : -0.6F;
        }
        return rotate(new Vec(0.0, rideHeight, offset), yaw);
    }

    /**
     * 原版 {@code Camel.getPassengerAttachmentPoint} 与 {@code getBodyAnchorAnimationYOffset}：
     * 高度随坐下与站起的过渡动画变化，第一个乘客在前（0.5），多个乘客时其余在后（-0.7）
     */
    private static Vec camelPoint(ClientEntity vehicle, int index, int passengerCount, float scale, boolean baby, float yaw,
                                  long gameTime) {
        var driver = index == 0;
        var offset = 0.5F;
        if (passengerCount > 1 && !driver) {
            offset = -0.7F;
        }
        var sittingPose = meta(vehicle, EntityMetaIndex.POSE) == Pose.SITTING;
        var dimensionHeight = camelHeight(vehicle.type(), sittingPose, baby) * scale;
        var anchorScale = scale * ageScale(vehicle.type(), baby);
        var height = (float) bodyAnchorOffset(vehicle, driver, dimensionHeight, anchorScale, baby, gameTime);
        return rotate(new Vec(0.0, height, offset * anchorScale), yaw);
    }

    private static float camelHeight(EntityType type, boolean sitting, boolean baby) {
        if (sitting) {
            return baby ? 0.425F : (float) EntityTypeData.height(type) - 1.43F;
        }
        return baby ? 1.4F : (float) EntityTypeData.height(type);
    }

    private static double bodyAnchorOffset(ClientEntity camel, boolean front, float height, float scale, boolean baby, long gameTime) {
        long lastPoseChange = meta(camel, EntityMetaIndex.CAMEL_LAST_POSE_CHANGE_TICK);
        var sitting = lastPoseChange < 0L;
        var poseTime = gameTime - Math.abs(lastPoseChange);
        double ageSitOffset = baby ? 0.09375 : 0.375;
        double base = height - ageSitOffset;
        var sittingHeightDifference = scale * 1.43F;
        var verticalDrop = sittingHeightDifference - scale * 0.2F;
        var bottomPoint = sittingHeightDifference - verticalDrop;
        var inTransition = poseTime < (sitting ? 40 : 52);
        if (inTransition) {
            var duration = sitting ? 40 : 52;
            int halfPoint;
            float flexOffset;
            if (sitting) {
                halfPoint = 28;
                flexOffset = front ? 0.5F : 0.1F;
            } else {
                halfPoint = front ? 24 : 32;
                flexOffset = front ? 0.6F : 0.35F;
            }
            var time = Math.clamp((float) poseTime, 0.0F, (float) duration);
            var firstPart = time < halfPoint;
            var part = firstPart ? time / halfPoint : (time - halfPoint) / (duration - halfPoint);
            var flexPoint = sittingHeightDifference - flexOffset * verticalDrop;
            base += sitting
                    ? lerp(part, firstPart ? sittingHeightDifference : flexPoint, firstPart ? flexPoint : bottomPoint)
                    : lerp(part, firstPart ? bottomPoint - sittingHeightDifference : bottomPoint - flexPoint,
                    firstPart ? bottomPoint - flexPoint : 0.0F);
        }
        if (sitting && !inTransition) {
            base += bottomPoint;
        }
        return base;
    }

    /**
     * 原版 {@code Mth.lerp(float, float, float)}
     */
    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /**
     * 原版各实体的 {@code getAgeScale}：幼年骆驼为 0.6，幼年快乐恶魂为 0.2375，其余幼年为 0.5
     */
    private static float ageScale(EntityType type, boolean baby) {
        if (!baby) {
            return 1.0F;
        }
        if (CAMELS.contains(type)) {
            return 0.6F;
        }
        if (type == EntityType.HAPPY_GHAST) {
            return 0.2375F;
        }
        return 0.5F;
    }

    /**
     * 成年实体的乘客挂点：实体类型数据中的 PASSENGER，没有时为实体高度处（原版 {@code EntityAttachment.Fallback.AT_HEIGHT}）
     */
    private static List<Vec> adultPassengerAttachments(EntityType type) {
        var attachments = EntityTypeData.passengerAttachments(type);
        return attachments != null ? attachments : List.of(new Vec(0.0, (float) EntityTypeData.height(type), 0.0));
    }

    /**
     * 幼年实体的乘客挂点（原版各实体类的 BABY_DIMENSIONS），其余幼年实体为成年挂点乘以年龄缩放
     */
    private static List<Vec> babyPassengerAttachments(EntityType type) {
        var height = (float) EntityTypeData.height(type);
        if (type == EntityType.HORSE) {
            return List.of(new Vec(0.0, height - 0.125F, 0.0).mul(0.7F));
        }
        if (type == EntityType.SKELETON_HORSE || type == EntityType.ZOMBIE_HORSE) {
            return List.of(new Vec(0.0, height - 0.25F, 0.0).mul(0.7F));
        }
        if (type == EntityType.DONKEY || type == EntityType.MULE) {
            return List.of(new Vec(0.0, height + 0.03125F, -0.3125F).mul(0.5F));
        }
        if (type == EntityType.LLAMA || type == EntityType.TRADER_LLAMA) {
            return List.of(new Vec(0.0, (float) EntityTypeData.height(EntityType.LLAMA) - 0.25F, -0.3F).mul(0.5F));
        }
        if (type == EntityType.PIG) {
            return List.of(new Vec(0.0, 0.5F, 0.0));
        }
        if (type == EntityType.STRIDER) {
            return List.of(new Vec(0.0, 0.65625F, 0.0));
        }
        if (type == EntityType.NAUTILUS) {
            return List.of(new Vec(0.0, 0.5F, 0.0));
        }
        if (type == EntityType.CHICKEN) {
            return List.of(new Vec(0.0, 0.375F, 0.0));
        }
        var ageScale = ageScale(type, true);
        return adultPassengerAttachments(type).stream().map(point -> point.mul(ageScale)).toList();
    }

    /**
     * 原版 {@code EntityAttachments.transformPoint}：绕 y 轴旋转 −偏航角
     */
    private static Vec rotate(Vec point, float yaw) {
        return SineTable.yRot(point, -yaw * (float) (Math.PI / 180.0));
    }

    private static <T> T meta(ClientEntity entity, EntityMeta<T> entry) {
        return EntityMetaIndex.get(entity.metadata(), entry);
    }

    /**
     * 实体类型在注册表中的名称（不带命名空间）：MCProtocolLib 常量名的小写形式
     */
    private static String name(EntityType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
