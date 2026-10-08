package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.predicate.DamageSourcePredicate;
import io.ib67.yuanban.loot.predicate.entity.DistancePredicate;
import io.ib67.yuanban.loot.predicate.entity.EffectInstancePredicate;
import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntitySubPredicate;
import io.ib67.yuanban.loot.predicate.entity.StatMatcher;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.metadata.AgeableMobMeta;
import net.minestom.server.entity.metadata.animal.SheepMeta;
import net.minestom.server.entity.metadata.cube.AbstractCubeMeta;
import net.minestom.server.entity.metadata.monster.PiglinMeta;
import net.minestom.server.entity.metadata.monster.ZoglinMeta;
import net.minestom.server.entity.metadata.monster.raider.RaiderMeta;
import net.minestom.server.entity.metadata.monster.zombie.ZombieMeta;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.potion.Potion;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.statistic.StatisticCategory;
import net.minestom.server.statistic.StatisticType;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 原版 {@code EntityPredicate}、各个 {@code EntitySubPredicate} 与 {@code DamageSourcePredicate} 的求值。
 * 世界是上下文的世界，位置是上下文的 {@code origin}，与原版 {@code LootItemEntityPropertyCondition} 传入的参数相同。
 */
final class EntityPredicates {
    /**
     * 原版 {@code EntityPredicate.PREDICATE_TYPE_ORDER}：实体类型最先，载具、乘客、目标、闪电、玩家其次，NBT 最后。
     */
    private static final Comparator<EntitySubPredicate> PREDICATE_TYPE_ORDER = Comparator.comparingInt(part -> switch (part) {
        case EntitySubPredicate.EntityTypes ignored -> -1;
        case EntitySubPredicate.Vehicle ignored -> 1;
        case EntitySubPredicate.Passenger ignored -> 1;
        case EntitySubPredicate.TargetedEntity ignored -> 1;
        case EntitySubPredicate.Lightning ignored -> 1;
        case EntitySubPredicate.Player ignored -> 1;
        case EntitySubPredicate.Nbt ignored -> 2;
        default -> 0;
    });

    private EntityPredicates() {
    }

    /**
     * 原版 {@code EntityPredicate#matches(ServerLevel, Vec3, Entity)}：实体为 {@code null} 时不通过；
     * 子谓词按原版的类型顺序稳定排序后依次检查。
     */
    static boolean matches(EntityPredicate predicate, @Nullable Instance level, @Nullable Point position,
                           @Nullable Entity entity, ServerProcess process) {
        if (entity == null) {
            return false;
        }
        var parts = predicate.parts();
        if (parts.size() > 1) {
            parts = parts.stream().sorted(PREDICATE_TYPE_ORDER).toList();
        }
        for (var part : parts) {
            if (!matches(part, entity, level, position, process)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(EntitySubPredicate part, Entity entity, @Nullable Instance level, @Nullable Point position,
                                   ServerProcess process) {
        return switch (part) {
            case EntitySubPredicate.EntityTypes types ->
                    Holders.contains(types.types(), entity.getEntityType().key(), process.entityType());
            case EntitySubPredicate.Location location -> {
                var pos = entity.getPosition();
                yield LocationPredicates.matches(location.predicate(), requireLevel(level), pos.x(), pos.y(), pos.z(), process);
            }
            case EntitySubPredicate.SteppingOn ignored -> {
                if (!entity.isOnGround()) {
                    yield false;
                }
                // TODO(minestom): Minestom 不记录实体脚下支撑它的方块（原版 Entity#getOnPos）
                throw LootEvaluation.unsupported("block supporting the entity " + entity.getEntityType().key().asString());
            }
            // TODO(minestom): Minestom 不记录影响实体移动的方块（原版 Entity#getBlockPosBelowThatAffectsMyMovement）
            case EntitySubPredicate.MovementAffectedBy ignored -> throw LootEvaluation.unsupported(
                    "block affecting the movement of the entity " + entity.getEntityType().key().asString());
            case EntitySubPredicate.Distance distance -> position != null && matches(distance.distance(),
                    position.x(), position.y(), position.z(), entity.getPosition().x(), entity.getPosition().y(), entity.getPosition().z());
            case EntitySubPredicate.Movement movement -> matchesMovement(movement, entity);
            case EntitySubPredicate.Effects effects -> {
                if (!(entity instanceof LivingEntity)) {
                    yield false;
                }
                for (var entry : effects.effects().entrySet()) {
                    var instance = entity.getEffect(entry.getKey());
                    if (!matches(entry.getValue(), instance == null ? null : instance.potion(),
                            instance == null ? 0 : remainingDuration(entity, instance))) {
                        yield false;
                    }
                }
                yield true;
            }
            // TODO(minestom): Minestom 没有原版保存实体时写出的 NBT（原版 NbtPredicate.getEntityTagToCompare）
            case EntitySubPredicate.Nbt ignored -> throw LootEvaluation.unsupported(
                    "NBT of the entity " + entity.getEntityType().key().asString());
            case EntitySubPredicate.Flags flags -> matchesFlags(flags, entity);
            case EntitySubPredicate.Equipment equipment -> matchesEquipment(equipment, entity, process);
            case EntitySubPredicate.PeriodicTick tick -> entity.getAliveTicks() % tick.period() == 0;
            case EntitySubPredicate.Vehicle vehicle -> matches(vehicle.vehicle(), level, position, entity.getVehicle(), process);
            case EntitySubPredicate.Passenger passenger -> {
                for (var rider : entity.getPassengers()) {
                    if (matches(passenger.passenger(), level, position, rider, process)) {
                        yield true;
                    }
                }
                yield false;
            }
            case EntitySubPredicate.TargetedEntity targeted -> matches(targeted.targetedEntity(), level, position,
                    entity instanceof EntityCreature creature ? creature.getTarget() : null, process);
            case EntitySubPredicate.Team team -> team.team().equals(teamName(entity, process));
            case EntitySubPredicate.Slots slots -> {
                for (var entry : slots.slots().entrySet()) {
                    if (!matchesSlots(entity, entry.getKey().slots(), entry.getValue(), process)) {
                        yield false;
                    }
                }
                yield true;
            }
            case EntitySubPredicate.Components components -> {
                for (var expected : components.components().entrySet()) {
                    if (!java.util.Objects.equals(expected.value(), entity.get(expected.component()))) {
                        yield false;
                    }
                }
                yield true;
            }
            case EntitySubPredicate.Predicates predicates -> ComponentPredicates.testPartial(predicates.predicates(), entity, process);
            // TODO(minestom): Minestom 没有实体的命令标签（原版 Entity#entityTags）
            case EntitySubPredicate.EntityTags ignored -> throw LootEvaluation.unsupported(
                    "command tags of the entity " + entity.getEntityType().key().asString());
            case EntitySubPredicate.Lightning lightning -> {
                if (entity.getEntityType() != EntityType.LIGHTNING_BOLT) {
                    yield false;
                }
                if (lightning.blocksSetOnFire().min() == null && lightning.blocksSetOnFire().max() == null
                        && lightning.entityStruck().isEmpty()) {
                    yield true;
                }
                // TODO(minestom): Minestom 的闪电不记录点燃的方块数和击中的实体
                throw LootEvaluation.unsupported("blocks set on fire and entities struck by the lightning bolt");
            }
            case EntitySubPredicate.FishingHook hook -> {
                if (hook.inOpenWater().isEmpty()) {
                    yield true;
                }
                if (entity.getEntityType() != EntityType.FISHING_BOBBER) {
                    yield false;
                }
                // TODO(minestom): Minestom 的浮漂不判断是否在开阔水域（原版 FishingHook#isOpenWaterFishing）
                throw LootEvaluation.unsupported("whether the fishing hook is in open water");
            }
            case EntitySubPredicate.Player player -> matchesPlayer(player, entity, process);
            case EntitySubPredicate.CubeMob cube ->
                    entity.getEntityMeta() instanceof AbstractCubeMeta meta && cube.size().inRange(meta.getSize());
            case EntitySubPredicate.Raider ignored -> {
                if (!(entity.getEntityMeta() instanceof RaiderMeta)) {
                    yield false;
                }
                // TODO(minestom): Minestom 没有袭击，也不记录袭击者是否为队长
                throw LootEvaluation.unsupported("raid and captain state of the raider " + entity.getEntityType().key().asString());
            }
            case EntitySubPredicate.Sheep sheep -> entity.getEntityMeta() instanceof SheepMeta meta
                    && (sheep.sheared().isEmpty() || meta.isSheared() == sheep.sheared().get());
        };
    }

    private static Instance requireLevel(@Nullable Instance level) {
        if (level == null) {
            throw new io.ib67.yuanban.loot.LootException("The loot context has no level, but the location predicate needs one");
        }
        return level;
    }

    /**
     * 原版 {@code DistancePredicate#matches}：坐标差按单精度浮点数计算。
     */
    private static boolean matches(DistancePredicate distance, double x0, double y0, double z0, double x1, double y1, double z1) {
        float xd = (float) (x0 - x1);
        float yd = (float) (y0 - y1);
        float zd = (float) (z0 - z1);
        if (!LootEvaluation.matches(distance.x(), Math.abs(xd)) || !LootEvaluation.matches(distance.y(), Math.abs(yd))
                || !LootEvaluation.matches(distance.z(), Math.abs(zd))) {
            return false;
        }
        return LootEvaluation.matchesSqr(distance.horizontal(), xd * xd + zd * zd)
                && LootEvaluation.matchesSqr(distance.absolute(), xd * xd + yd * yd + zd * zd);
    }

    /**
     * 原版 {@code MovementPredicate#matches(Entity, ServerLevel, Vec3)}：速度是每秒的移动距离。原版玩家用客户端上报的最近一次移动，
     * 这里用玩家最近一次位置更新的位移；其他实体用 Minestom 的速度（每秒的方块数，与原版每刻的速度乘以 20 相同）。
     */
    private static boolean matchesMovement(EntitySubPredicate.Movement movement, Entity entity) {
        Vec velocity = entity instanceof Player
                ? entity.getPosition().sub(entity.getPreviousPosition()).asVec().mul(20.0)
                : entity.getVelocity();
        double x = velocity.x();
        double y = velocity.y();
        double z = velocity.z();
        if (!(LootEvaluation.matches(movement.x(), x) && LootEvaluation.matches(movement.y(), y)
                && LootEvaluation.matches(movement.z(), z))) {
            return false;
        }
        if (!LootEvaluation.matchesSqr(movement.speed(), Mth.lengthSquared(x, y, z))) {
            return false;
        }
        if (!LootEvaluation.matchesSqr(movement.horizontalSpeed(), Mth.lengthSquared(x, z))) {
            return false;
        }
        if (!LootEvaluation.matches(movement.verticalSpeed(), Math.abs(y))) {
            return false;
        }
        if (movement.fallDistance().min() == null && movement.fallDistance().max() == null) {
            return true;
        }
        // TODO(minestom): Minestom 不记录实体的下落距离（原版 Entity#fallDistance）
        throw LootEvaluation.unsupported("fall distance of the entity " + entity.getEntityType().key().asString());
    }

    /**
     * 原版 {@code MobEffectInstance#getDuration}：剩余的刻数，无限时长为 -1。
     */
    private static int remainingDuration(Entity entity, net.minestom.server.potion.TimedPotion instance) {
        int duration = instance.potion().duration();
        if (duration == Potion.INFINITE_DURATION) {
            return duration;
        }
        return (int) (duration - (entity.getAliveTicks() - instance.startingTicks()));
    }

    /**
     * 原版 {@code MobEffectsPredicate$MobEffectInstancePredicate#matches}。
     */
    private static boolean matches(EffectInstancePredicate predicate, @Nullable Potion instance, int duration) {
        if (instance == null) {
            return false;
        }
        if (!predicate.amplifier().inRange(instance.amplifier())) {
            return false;
        }
        if (!predicate.duration().inRange(duration)) {
            return false;
        }
        if (predicate.ambient().isPresent() && predicate.ambient().get() != instance.isAmbient()) {
            return false;
        }
        return predicate.visible().isEmpty() || predicate.visible().get() == instance.hasParticles();
    }

    /**
     * 原版 {@code EntityFlagsPredicate#matches}。下蹲按姿势判断（原版 {@code Entity#isCrouching}），
     * 飞行包括鞘翅滑翔与玩家的飞行。
     */
    private static boolean matchesFlags(EntitySubPredicate.Flags flags, Entity entity) {
        var meta = entity.getEntityMeta();
        if (flags.isOnGround().isPresent() && entity.isOnGround() != flags.isOnGround().get()) {
            return false;
        }
        if (flags.isOnFire().isPresent() && entity.isOnFire() != flags.isOnFire().get()) {
            return false;
        }
        if (flags.isSneaking().isPresent() && (entity.getPose() == EntityPose.SNEAKING) != flags.isSneaking().get()) {
            return false;
        }
        if (flags.isSprinting().isPresent() && entity.isSprinting() != flags.isSprinting().get()) {
            return false;
        }
        if (flags.isSwimming().isPresent() && meta.isSwimming() != flags.isSwimming().get()) {
            return false;
        }
        if (flags.isFlying().isPresent()) {
            boolean flying = entity instanceof LivingEntity && (meta.isFlyingWithElytra()
                    || entity instanceof Player player && player.isFlying());
            if (flying != flags.isFlying().get()) {
                return false;
            }
        }
        if (flags.isInWater().isPresent()) {
            // TODO(minestom): Minestom 不判断实体是否接触水（原版 Entity#isInWater）
            throw LootEvaluation.unsupported("whether the entity " + entity.getEntityType().key().asString() + " is in water");
        }
        if (flags.isFallFlying().isPresent() && entity instanceof LivingEntity
                && meta.isFlyingWithElytra() != flags.isFallFlying().get()) {
            return false;
        }
        return flags.isBaby().isEmpty() || !(entity instanceof LivingEntity) || isBaby(entity) == flags.isBaby().get();
    }

    /**
     * 原版 {@code LivingEntity#isBaby} 的各个实现：可成长的生物、僵尸、猪灵、僵尸疣猪兽按幼年标记，盔甲架按小型标记。
     */
    private static boolean isBaby(Entity entity) {
        return switch (entity.getEntityMeta()) {
            case AgeableMobMeta ageable -> ageable.isBaby();
            case ZombieMeta zombie -> zombie.isBaby();
            case PiglinMeta piglin -> piglin.isBaby();
            case ZoglinMeta zoglin -> zoglin.isBaby();
            case ArmorStandMeta armorStand -> armorStand.isSmall();
            default -> false;
        };
    }

    /**
     * 原版 {@code EntityEquipmentPredicate#matches}：只对生物生效。
     */
    private static boolean matchesEquipment(EntitySubPredicate.Equipment equipment, Entity entity, ServerProcess process) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        return slotMatches(equipment.head(), living, EquipmentSlot.HELMET, process)
                && slotMatches(equipment.chest(), living, EquipmentSlot.CHESTPLATE, process)
                && slotMatches(equipment.legs(), living, EquipmentSlot.LEGGINGS, process)
                && slotMatches(equipment.feet(), living, EquipmentSlot.BOOTS, process)
                && slotMatches(equipment.body(), living, EquipmentSlot.BODY, process)
                && slotMatches(equipment.mainhand(), living, EquipmentSlot.MAIN_HAND, process)
                && slotMatches(equipment.offhand(), living, EquipmentSlot.OFF_HAND, process);
    }

    private static boolean slotMatches(Optional<io.ib67.yuanban.loot.predicate.ItemPredicate> predicate, LivingEntity entity,
                                       EquipmentSlot slot, ServerProcess process) {
        return predicate.isEmpty() || ItemPredicates.test(predicate.get(), entity.getEquipment(slot), process);
    }

    /**
     * 原版 {@code Entity#getTeam}：生物读取它所在的队伍，其他实体按记分板名称（UUID）在全部队伍中查找。
     */
    private static @Nullable String teamName(Entity entity, ServerProcess process) {
        if (entity instanceof LivingEntity living) {
            var team = living.getTeam();
            return team == null ? null : team.getTeamName();
        }
        var holder = entity.getUuid().toString();
        for (var team : process.team().getTeams()) {
            if (team.getMembers().contains(holder)) {
                return team.getTeamName();
            }
        }
        return null;
    }

    /**
     * 原版 {@code SlotsPredicate#matchSlots}：范围中任意一个存在的槽位满足物品谓词即通过。
     */
    private static boolean matchesSlots(Entity entity, List<Integer> slots, io.ib67.yuanban.loot.predicate.ItemPredicate predicate,
                                        ServerProcess process) {
        for (int slot : slots) {
            ItemStack item = EntitySlots.get(entity, slot);
            if (item != null && ItemPredicates.test(predicate, item, process)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 原版 {@code PlayerPredicate#matches}，检查顺序与原版相同。
     */
    private static boolean matchesPlayer(EntitySubPredicate.Player predicate, Entity entity, ServerProcess process) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (!predicate.level().inRange(player.getLevel())) {
            return false;
        }
        if (!(predicate.food().level().inRange(player.getFood())
                && LootEvaluation.matches(predicate.food().saturation(), player.getFoodSaturation()))) {
            return false;
        }
        GameMode gameMode = player.getGameMode();
        if (!predicate.gameModes().contains(gameMode)) {
            return false;
        }
        for (var stat : predicate.stats()) {
            if (!stat.value().inRange(statValue(player, stat))) {
                return false;
            }
        }
        if (!predicate.recipes().isEmpty()) {
            // TODO(minestom): Minestom 没有玩家的配方书
            throw LootEvaluation.unsupported("recipe book of the player");
        }
        if (!predicate.advancements().isEmpty()) {
            // TODO(minestom): Minestom 没有玩家的进度
            throw LootEvaluation.unsupported("advancements of the player");
        }
        if (predicate.lookingAt().isPresent()) {
            var target = lookingAt(player);
            if (target == null || !matches(predicate.lookingAt().get(), player.getInstance(), player.getPosition(), target, process)
                    || !hasLineOfSight(player, target)) {
                return false;
            }
        }
        if (predicate.input().isEmpty()) {
            return true;
        }
        var input = predicate.input().get();
        var inputs = player.inputs();
        return matches(input.forward(), inputs.forward()) && matches(input.backward(), inputs.backward())
                && matches(input.left(), inputs.left()) && matches(input.right(), inputs.right())
                && matches(input.jump(), inputs.jump()) && matches(input.sneak(), inputs.shift())
                && matches(input.sprint(), inputs.sprint());
    }

    /**
     * 原版 {@code LivingEntity#hasLineOfSight(Entity)}：两者在同一个世界、眼睛之间的距离不超过 128 格，
     * 且两眼之间的连线不被方块的碰撞箱挡住。连线与方块的相交由 Minestom 的 {@link Entity#hasLineOfSight(Entity)} 判断，
     * 原版按方块的碰撞形状逐格追踪。
     */
    private static boolean hasLineOfSight(Player player, Entity target) {
        if (target.getInstance() != player.getInstance()) {
            return false;
        }
        var from = player.getPosition().add(0, player.getEyeHeight(), 0);
        var to = target.getPosition().add(0, target.getEyeHeight(), 0);
        return !(to.distance(from) > 128.0) && player.hasLineOfSight(target);
    }

    private static boolean matches(Optional<Boolean> expected, boolean actual) {
        return expected.map(value -> value == actual).orElse(true);
    }

    /**
     * 原版 {@code StatsCounter#getValue}：没有记录的统计项为 0。Minestom 的统计项按类别与该类别注册表中的数字 ID 保存。
     */
    private static int statValue(Player player, StatMatcher stat) {
        int id = statisticId(stat.category(), stat.stat());
        for (var entry : player.getStatisticValueMap().entrySet()) {
            if (entry.getKey().getCategory() == stat.category() && entry.getKey().getStatisticId() == id) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private static int statisticId(StatisticCategory category, Key stat) {
        return switch (category) {
            case MINED -> java.util.Objects.requireNonNull(net.minestom.server.instance.block.Block.fromKey(stat)).id();
            case CRAFTED, USED, BROKEN, PICKED_UP, DROPPED -> java.util.Objects.requireNonNull(net.minestom.server.item.Material.fromKey(stat)).id();
            case KILLED, KILLED_BY -> java.util.Objects.requireNonNull(EntityType.fromKey(stat)).id();
            case CUSTOM -> java.util.Objects.requireNonNull(StatisticType.fromKey(stat)).id();
        };
    }

    /**
     * 原版 {@code PlayerPredicate} 的 {@code looking_at}：从眼睛沿视线 100 格，按 {@code ProjectileUtil.getEntityHitResult}
     * 找最近的被击中实体；搜索区域是线段的包围盒向外扩 1 格，旁观模式的玩家不参与。
     */
    private static @Nullable Entity lookingAt(Player player) {
        var position = player.getPosition();
        double fromX = position.x();
        double fromY = position.y() + player.getEyeHeight();
        double fromZ = position.z();
        // 原版 Entity#calculateViewVector：用查表的正弦与余弦
        float pitch = position.pitch() * ((float) Math.PI / 180.0F);
        float yaw = -position.yaw() * ((float) Math.PI / 180.0F);
        float yawCos = Mth.cos(yaw);
        float yawSin = Mth.sin(yaw);
        float pitchCos = Mth.cos(pitch);
        float pitchSin = Mth.sin(pitch);
        double viewX = yawSin * pitchCos;
        double viewY = -pitchSin;
        double viewZ = yawCos * pitchCos;
        double toX = fromX + viewX * 100.0;
        double toY = fromY + viewY * 100.0;
        double toZ = fromZ + viewZ * 100.0;
        double minX = Math.min(fromX, toX) - 1.0;
        double minY = Math.min(fromY, toY) - 1.0;
        double minZ = Math.min(fromZ, toZ) - 1.0;
        double maxX = Math.max(fromX, toX) + 1.0;
        double maxY = Math.max(fromY, toY) + 1.0;
        double maxZ = Math.max(fromZ, toZ) + 1.0;
        double nearest = Double.MAX_VALUE;
        Entity hit = null;
        for (var entity : player.getInstance().getEntities()) {
            if (entity == player || entity instanceof Player other && other.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            var box = worldBox(entity);
            if (!(box[0] < maxX && box[3] > minX && box[1] < maxY && box[4] > minY && box[2] < maxZ && box[5] > minZ)) {
                continue;
            }
            double scale = clip(box, fromX, fromY, fromZ, toX - fromX, toY - fromY, toZ - fromZ);
            if (scale < 0) {
                continue;
            }
            double dx = scale * (toX - fromX);
            double dy = scale * (toY - fromY);
            double dz = scale * (toZ - fromZ);
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearest) {
                hit = entity;
                nearest = distance;
            }
        }
        return hit;
    }

    private static double[] worldBox(Entity entity) {
        BoundingBox box = entity.getBoundingBox();
        var position = entity.getPosition();
        return new double[]{
                position.x() + box.minX(), position.y() + box.minY(), position.z() + box.minZ(),
                position.x() + box.maxX(), position.y() + box.maxY(), position.z() + box.maxZ()};
    }

    /**
     * 原版 {@code AABB#clip}：线段第一次穿入包围盒的位置在线段上的比例，没有穿入时为 -1。
     */
    private static double clip(double[] box, double fromX, double fromY, double fromZ, double dx, double dy, double dz) {
        double[] scale = {1.0};
        boolean hit = false;
        if (dx > 1.0E-7) {
            hit |= clipPoint(scale, dx, dy, dz, box[0], box[1], box[4], box[2], box[5], fromX, fromY, fromZ);
        } else if (dx < -1.0E-7) {
            hit |= clipPoint(scale, dx, dy, dz, box[3], box[1], box[4], box[2], box[5], fromX, fromY, fromZ);
        }
        if (dy > 1.0E-7) {
            hit |= clipPoint(scale, dy, dz, dx, box[1], box[2], box[5], box[0], box[3], fromY, fromZ, fromX);
        } else if (dy < -1.0E-7) {
            hit |= clipPoint(scale, dy, dz, dx, box[4], box[2], box[5], box[0], box[3], fromY, fromZ, fromX);
        }
        if (dz > 1.0E-7) {
            hit |= clipPoint(scale, dz, dx, dy, box[2], box[0], box[3], box[1], box[4], fromZ, fromX, fromY);
        } else if (dz < -1.0E-7) {
            hit |= clipPoint(scale, dz, dx, dy, box[5], box[0], box[3], box[1], box[4], fromZ, fromX, fromY);
        }
        return hit ? scale[0] : -1;
    }

    private static boolean clipPoint(double[] scale, double da, double db, double dc, double point,
                                     double minB, double maxB, double minC, double maxC,
                                     double fromA, double fromB, double fromC) {
        double s = (point - fromA) / da;
        double pb = fromB + s * db;
        double pc = fromC + s * dc;
        if (0.0 < s && s < scale[0] && minB - 1.0E-7 < pb && pb < maxB + 1.0E-7 && minC - 1.0E-7 < pc && pc < maxC + 1.0E-7) {
            scale[0] = s;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 伤害来源
    // ------------------------------------------------------------------

    /**
     * 原版 {@code DamageSourcePredicate#matches(ServerLevel, Vec3, DamageSource)}：直接来源是造成伤害的实体
     * （{@link Damage#getSource()}），根本来源是攻击者（{@link Damage#getAttacker()}），两者相同时为直接伤害。
     */
    static boolean matches(DamageSourcePredicate predicate, @Nullable Instance level, @Nullable Point position, Damage damage,
                           ServerProcess process) {
        for (var tag : predicate.tags()) {
            var members = process.damageType().getTag(tag.tag());
            boolean inTag = members != null && members.contains(RegistryKey.of(damage.getType().key()));
            if (inTag != tag.expected()) {
                return false;
            }
        }
        if (predicate.directEntity().isPresent()
                && !matches(predicate.directEntity().get(), level, position, damage.getSource(), process)) {
            return false;
        }
        if (predicate.sourceEntity().isPresent()
                && !matches(predicate.sourceEntity().get(), level, position, damage.getAttacker(), process)) {
            return false;
        }
        return predicate.isDirect().isEmpty() || predicate.isDirect().get() == (damage.getAttacker() == damage.getSource());
    }
}
