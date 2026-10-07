package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.ChunkNotLoadedException;
import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.registry.TagTable;
import io.ib67.yuanban.bot.world.ClientWorld;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.collision.PhysicsUtils;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.component.UseEffects;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

/**
 * 每 tick 的移动计算，按原版 {@code LocalPlayer.aiStep}、{@code LivingEntity.aiStep}、{@code Player.travel} 与
 * {@code LivingEntity.travelInAir} 的顺序：飞行时的竖直输入 → 速度归零 → 起跳 → 水平加速 → 移动与碰撞 → 竖直速度、阻力与方块速度系数。
 * 移动与碰撞交给 Minestom 的 {@link PhysicsUtils}，它只负责移动、碰撞与水平阻力（阻力取原版在移动之前读出的值），
 * 竖直速度按原版在碰撞之后计算。
 */
public final class Kinematics {
    /**
     * 原版 {@code LocalPlayer.modifyInput}：移动输入乘以 0.98
     */
    public static final float INPUT_SCALE = 0.98F;
    /**
     * 原版 {@code Player.getFlyingSpeed}：不飞行时空中的加速度
     */
    public static final float AIR_ACCELERATION = 0.02F;
    public static final float SPRINT_AIR_ACCELERATION = 0.025999999F;
    public static final double SPRINT_JUMP_BOOST = 0.2;
    /**
     * 原版 {@code travelInAir} 的水平与竖直空气阻力（再按 {@code minecraft:air_drag_modifier} 属性修正）
     */
    static final float AIR_DRAG = 0.91F;
    static final float VERTICAL_AIR_DRAG = 0.98F;
    /**
     * 原版 {@code getBlockPosBelowThatAffectsMyMovement} 的向下偏移
     */
    static final float BELOW_OFFSET = 0.500001F;
    /**
     * 查找支撑方块时，包围盒底面以下的检查深度。原版为 1.0E-6；Minestom 的碰撞计算让着地的实体停在方块顶面之上最多约 1.0E-5 处，
     * 并且 {@code Shape.intersectBox} 要求各方向重叠超过 1.0E-6，所以加深到 1.0E-4
     */
    static final double SUPPORT_DEPTH = 1.0E-4;
    /**
     * 原版 {@code LivingEntity.noJumpDelay}：起跳之后按住跳跃不再起跳的 tick 数
     */
    static final int JUMP_DELAY_TICKS = 10;

    private Kinematics() {
    }

    /**
     * 模拟一个 tick，结果写回 player。
     *
     * @param tags      判断支撑方块是否为墙与栅栏门（原版 {@code getOnPos}）
     * @param sprinting 本地的疾跑状态（{@link MovementController} 按原版的条件决定）
     * @param itemInUse 正在使用的物品的 {@code use_effects} 组件；没有在使用物品时为 null
     * @return 模拟结果
     * @throws ChunkNotLoadedException 本 tick 的移动范围涉及未加载区块，这时不执行模拟
     */
    public static PhysicsResult step(LocalPlayer player, ClientWorld world, TagTable tags, MoveIntent intent, boolean sprinting,
                                     @Nullable UseEffects itemInUse) {
        var position = player.position();
        var yaw = position.yaw();
        var velocity = player.velocity();
        var boundingBox = player.boundingBox();
        checkLoaded(world, position, velocity, boundingBox.width());

        var attributes = player.attributes();
        var abilities = player.abilities();
        var flying = abilities.flying();
        var shift = intent.mode() == MovementMode.SNEAK;
        // 原版 LocalPlayer.aiStep：飞行时跳跃键向上、潜行键向下加速
        if (flying) {
            var vertical = (intent.jump() ? 1 : 0) - (shift ? 1 : 0);
            if (vertical != 0) {
                velocity = velocity.add(0, vertical * abilities.flyingSpeed() * 3.0F, 0);
            }
        }
        // 原版 LivingEntity.aiStep：玩家水平速度的平方小于 9.0E-6 时水平速度归零，竖直速度的绝对值小于 0.003 时归零
        if (player.noJumpDelay() > 0) {
            player.noJumpDelay(player.noJumpDelay() - 1);
        }
        if (velocity.x() * velocity.x() + velocity.z() * velocity.z() < 9.0E-6) {
            velocity = new Vec(0, velocity.y(), 0);
        }
        if (Math.abs(velocity.y()) < 0.003) {
            velocity = velocity.withY(0);
        }
        // 原版 LocalPlayer.modifyInput：使用物品时乘以物品的速度系数，蹲下（潜行并且不在飞行）时乘以 sneaking_speed 属性
        var inputScale = INPUT_SCALE;
        if (itemInUse != null) {
            inputScale *= itemInUse.speedMultiplier();
        }
        if (shift && !flying) {
            inputScale *= (float) attributes.value(Attribute.SNEAKING_SPEED);
        }
        // 原版 LivingEntity.aiStep：按住跳跃、着地并且起跳延迟为 0 时起跳；飞行时不起跳（isAffectedByFluids 为 false）
        var onGround = player.onGround();
        if (intent.jump() && !flying) {
            if (onGround && player.noJumpDelay() == 0) {
                velocity = jumpFromGround(player, world, tags, position, velocity, sprinting);
                player.noJumpDelay(JUMP_DELAY_TICKS);
            }
        } else {
            player.noJumpDelay(0);
        }

        // 原版 travelInAir：着地时的摩擦按移动之前脚下的方块读取
        var blockFriction = onGround ? modifiedFriction(block(world, below(world, tags, player, position)).friction(),
                (float) attributes.value(Attribute.FRICTION_MODIFIER)) : 1.0F;
        var speed = frictionInfluencedSpeed(player, onGround, blockFriction, flying, sprinting);
        var verticalBeforeTravel = velocity.y();
        velocity = velocity.add(intent.direction().mul((double) inputScale * speed));

        var airDragModifier = (float) attributes.value(Attribute.AIR_DRAG_MODIFIER);
        var friction = blockFriction * modifiedFriction(AIR_DRAG, airDragModifier);
        var hasPhysics = player.gameMode() != GameMode.SPECTATOR;
        // PhysicsUtils 只做移动、碰撞与水平阻力：水平阻力直接给出原版的值（着地标记传 false，它不再按移动之后的位置读取方块摩擦），
        // 不施加重力（重力 0、竖直阻力 1），所以返回的竖直速度就是碰撞之后的竖直速度
        var result = PhysicsUtils.simulateMovement(position, velocity, boundingBox, world.border().toWorldBorder(), world,
                new Aerodynamics(0, friction, 1), false, hasPhysics, false, false, player.previousResult());

        var newPosition = result.newPosition().withView(yaw, position.pitch());
        var collided = result.newVelocity();
        double movementY;
        var levitation = player.effects().get(PotionEffect.LEVITATION);
        if (levitation != null) {
            movementY = collided.y() + (0.05 * (levitation.amplifier() + 1) - collided.y()) * 0.2;
        } else {
            movementY = collided.y() - effectiveGravity(player, collided.y());
        }
        // 原版 Player.travel：飞行时竖直速度为移动之前的竖直速度乘以 0.6
        var newY = flying ? verticalBeforeTravel * 0.6 : movementY * modifiedFriction(VERTICAL_AIR_DRAG, airDragModifier);

        var horizontalCollision = result.collisionX() || result.collisionZ();
        var movement = newPosition.sub(position);
        var desired = intent.direction().mul(inputScale);
        player.position(newPosition);
        player.movementFlags(result.isOnGround(), horizontalCollision,
                horizontalCollision && minorHorizontalCollision(desired, movement));
        updateSupport(player, world, newPosition, boundingBox, result.isOnGround(), movement);
        // 原版 Entity.move 的最后：水平速度乘以方块速度系数（按移动之后的位置读取）
        var speedFactor = speedFactor(player, world, tags, newPosition);
        player.velocity(new Vec(collided.x() * speedFactor, newY, collided.z() * speedFactor));
        player.previousResult(result);
        return result;
    }

    /**
     * 原版 {@code LivingEntity.jumpFromGround}：竖直速度取起跳力与当前竖直速度中较大的一个，起跳力小于等于 1.0E-5 时不起跳；
     * 疾跑时沿朝向增加 0.2 的水平速度。以 float 计算
     */
    private static Vec jumpFromGround(LocalPlayer player, ClientWorld world, TagTable tags, Pos position, Vec velocity,
                                      boolean sprinting) {
        var jumpBoost = player.effects().get(PotionEffect.JUMP_BOOST);
        var jumpBoostPower = jumpBoost != null ? 0.1F * (jumpBoost.amplifier() + 1.0F) : 0.0F;
        var jumpPower = (float) player.attributes().value(Attribute.JUMP_STRENGTH) * 1.0F * jumpFactor(player, world, tags, position)
                + jumpBoostPower;
        if (jumpPower <= 1.0E-5F) {
            return velocity;
        }
        velocity = new Vec(velocity.x(), Math.max((double) jumpPower, velocity.y()), velocity.z());
        if (sprinting) {
            var angle = position.yaw() * (float) (Math.PI / 180.0);
            velocity = velocity.add(-SineTable.sin(angle) * SPRINT_JUMP_BOOST, 0, SineTable.cos(angle) * SPRINT_JUMP_BOOST);
        }
        return velocity;
    }

    /**
     * 原版 {@code getBlockJumpFactor}：脚部所在方块的系数不为 1 时取它，否则取脚下的方块
     */
    private static float jumpFactor(LocalPlayer player, ClientWorld world, TagTable tags, Pos position) {
        var here = block(world, position).jumpFactor();
        return here == 1.0 ? block(world, below(world, tags, player, position)).jumpFactor() : here;
    }

    /**
     * 原版 {@code LivingEntity.getFrictionInfluencedSpeed}：着地时为移动速度属性（方块摩擦大于 0.6 时乘以 {@code 0.21600002 / 摩擦³}），
     * 空中为 {@code Player.getFlyingSpeed}：飞行时取能力中的飞行速度，疾跑时乘以 2；不飞行时为 0.02，疾跑时为 0.026
     */
    private static float frictionInfluencedSpeed(LocalPlayer player, boolean onGround, float blockFriction, boolean flying,
                                                 boolean sprinting) {
        if (onGround) {
            var speed = (float) player.attributes().value(Attribute.MOVEMENT_SPEED);
            return blockFriction > 0.6 ? speed * (0.21600002F / (blockFriction * blockFriction * blockFriction)) : speed;
        }
        if (flying) {
            var flyingSpeed = player.abilities().flyingSpeed();
            return sprinting ? flyingSpeed * 2.0F : flyingSpeed;
        }
        return sprinting ? SPRINT_AIR_ACCELERATION : AIR_ACCELERATION;
    }

    /**
     * 原版 {@code LivingEntity.getEffectiveGravity}：没有重力（元数据 NoGravity）时为 0，否则为 gravity 属性；
     * 竖直速度不大于 0 并且有缓降效果时不超过 0.01
     *
     * @param verticalVelocity 碰撞之后的竖直速度
     */
    private static double effectiveGravity(LocalPlayer player, double verticalVelocity) {
        var noGravity = player.metadata().get(MetadataDef.HAS_NO_GRAVITY.index());
        var gravity = noGravity != null && (Boolean) noGravity.value() ? 0.0 : player.attributes().value(Attribute.GRAVITY);
        return verticalVelocity <= 0.0 && player.effects().get(PotionEffect.SLOW_FALLING) != null ? Math.min(gravity, 0.01) : gravity;
    }

    /**
     * 原版 {@code LivingEntity.computeModifiedFriction}
     */
    private static float modifiedFriction(float friction, float modifier) {
        return Math.clamp(1.0F - (1.0F - friction) * modifier, 0.0F, 1.0F);
    }

    /**
     * 原版 {@code LocalPlayer.isHorizontalCollisionMinor}：期望的水平移动与实际移动的夹角小于 0.13962634 弧度（8 度）时，
     * 顶住墙壁不让疾跑停止
     */
    private static boolean minorHorizontalCollision(Vec desired, Point movement) {
        var desiredSquared = desired.x() * desired.x() + desired.z() * desired.z();
        var movementSquared = movement.x() * movement.x() + movement.z() * movement.z();
        if (desiredSquared < 1.0E-5F || movementSquared < 1.0E-5F) {
            return false;
        }
        var dot = desired.x() * movement.x() + desired.z() * movement.z();
        return Math.acos(dot / Math.sqrt(desiredSquared * movementSquared)) < 0.13962634F;
    }

    /**
     * 原版 {@code getBlockPosBelowThatAffectsMyMovement}（{@code getOnPos(0.500001F)}）：有支撑方块时取它所在的列，
     * 支撑方块为墙或栅栏门时取支撑方块本身；没有时取脚部所在的列。高度为脚部向下 0.500001 格
     */
    static Point below(ClientWorld world, TagTable tags, LocalPlayer player, Pos position) {
        var y = (int) Math.floor(position.y() - BELOW_OFFSET);
        var support = player.supportingBlock();
        if (support == null) {
            return new Vec(position.blockX(), y, position.blockZ());
        }
        var block = block(world, support);
        if (tags.contains("minecraft:block", "minecraft:walls", block.id())
                || tags.contains("minecraft:block", "minecraft:fence_gates", block.id())) {
            return support;
        }
        return new Vec(support.blockX(), y, support.blockZ());
    }

    /**
     * 原版 {@code Entity.getBlockSpeedFactor} 与 {@code LivingEntity.getBlockSpeedFactor}：脚部所在方块的系数不为 1
     * （或者它是水、气泡柱）时取它，否则取脚下的方块；再按 {@code minecraft:movement_efficiency} 属性向 1 插值
     */
    private static float speedFactor(LocalPlayer player, ClientWorld world, TagTable tags, Pos position) {
        var here = block(world, position);
        var factor = here.speedFactor();
        if (here.id() != Block.WATER.id() && here.id() != Block.BUBBLE_COLUMN.id() && factor == 1.0) {
            factor = block(world, below(world, tags, player, position)).speedFactor();
        }
        var efficiency = (float) player.attributes().value(Attribute.MOVEMENT_EFFICIENCY);
        return factor + efficiency * (1.0F - factor);
    }

    /**
     * 原版 {@code Entity.checkSupportingBlock}：着地时在包围盒底面以下查找碰撞箱与它相交的方块，取中心离玩家位置最近的一个；
     * 没有找到、并且上一次不是「着地但没有支撑方块」时，按这次的水平移动退回去再找一次
     */
    private static void updateSupport(LocalPlayer player, ClientWorld world, Pos position, BoundingBox box, boolean onGround,
                                      Point movement) {
        if (!onGround) {
            player.supportingBlock(null, false);
            return;
        }
        var found = findSupport(world, position, box, 0, 0);
        if (found == null && !player.onGroundNoBlocks()) {
            found = findSupport(world, position, box, -movement.x(), -movement.z());
        }
        player.supportingBlock(found, found == null);
    }

    /**
     * 原版 {@code CollisionGetter.findSupportingBlock}：距离相同时取方块坐标较大的一个（先比较 y，再比较 z，最后比较 x）
     */
    private static @Nullable Point findSupport(ClientWorld world, Pos position, BoundingBox box, double offsetX, double offsetZ) {
        var area = new BoundingBox(box.width(), SUPPORT_DEPTH, box.depth());
        var areaPosition = new Vec(position.x() + offsetX, position.y() - SUPPORT_DEPTH, position.z() + offsetZ);
        var minX = (int) Math.floor(areaPosition.x() + area.minX()) - 1;
        var maxX = (int) Math.floor(areaPosition.x() + area.maxX()) + 1;
        var minY = (int) Math.floor(areaPosition.y()) - 1;
        var maxY = (int) Math.floor(position.y()) + 1;
        var minZ = (int) Math.floor(areaPosition.z() + area.minZ()) - 1;
        var maxZ = (int) Math.floor(areaPosition.z() + area.maxZ()) + 1;
        Vec best = null;
        var bestDistance = Double.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    var shape = world.getBlock(x, y, z, Block.Getter.Condition.TYPE).collisionShape();
                    if (!shape.intersectBox(areaPosition.sub(x, y, z), area)) {
                        continue;
                    }
                    var dx = x + 0.5 - position.x();
                    var dy = y + 0.5 - position.y();
                    var dz = z + 0.5 - position.z();
                    var distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance || distance == bestDistance && compare(best, x, y, z) < 0) {
                        best = new Vec(x, y, z);
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /**
     * 原版 {@code Vec3i.compareTo}：先比较 y，再比较 z，最后比较 x
     */
    private static int compare(Vec a, int x, int y, int z) {
        if (a.blockY() != y) {
            return Integer.compare(a.blockY(), y);
        }
        if (a.blockZ() != z) {
            return Integer.compare(a.blockZ(), z);
        }
        return Integer.compare(a.blockX(), x);
    }

    private static Block block(ClientWorld world, Point position) {
        return world.getBlock(position.blockX(), position.blockY(), position.blockZ(), Block.Getter.Condition.TYPE);
    }

    private static void checkLoaded(ClientWorld world, Pos position, Vec velocity, double width) {
        // 包围盒按速度扩展，再加上一格读取脚下方块与加速度的余量
        var margin = width / 2 + 1;
        var minX = position.x() - margin + Math.min(0, velocity.x());
        var maxX = position.x() + margin + Math.max(0, velocity.x());
        var minZ = position.z() - margin + Math.min(0, velocity.z());
        var maxZ = position.z() + margin + Math.max(0, velocity.z());
        for (int chunkX = CoordConversion.globalToChunk(minX); chunkX <= CoordConversion.globalToChunk(maxX); chunkX++) {
            for (int chunkZ = CoordConversion.globalToChunk(minZ); chunkZ <= CoordConversion.globalToChunk(maxZ); chunkZ++) {
                if (!world.chunkLoaded(chunkX, chunkZ)) {
                    throw new ChunkNotLoadedException(chunkX, chunkZ);
                }
            }
        }
    }
}
