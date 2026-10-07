package io.ib67.yuanban.bot.player;

import io.ib67.yuanban.bot.view.PlayerSnapshot;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.attribute.Attribute;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本地玩家。只在调度线程上使用。
 * <p>
 * 新的连接或者 Start Configuration 之后的 Login (play)，以及 Respawn，都创建新的本地玩家（原版客户端新建 {@code LocalPlayer}）：
 * 新玩家位于原点，偏航角 -180，其余为初始值。
 */
public final class LocalPlayer {
    /**
     * 原版客户端常量：站立与潜行时的眼睛高度、包围盒高度
     */
    public static final double STANDING_EYE_HEIGHT = 1.62;
    public static final double SNEAKING_EYE_HEIGHT = 1.27;
    public static final double WIDTH = 0.6;
    public static final double STANDING_HEIGHT = 1.8;
    public static final double SNEAKING_HEIGHT = 1.5;

    private final UUID uuid;
    private final String username;
    private int entityId;
    private GameMode gameMode = GameMode.SURVIVAL;
    private final Abilities abilities = new Abilities();
    private float health = 20;
    private int food = 20;
    private float saturation = 5;
    private boolean dead;
    private float experienceProgress;
    private int level;
    private int totalExperience;
    private final AttributeMap attributes = new AttributeMap(AttributeMap.PLAYER_BASE);
    private final EffectMap effects = new EffectMap();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private final Map<Integer, Metadata.Entry<?>> metadata = new Int2ObjectOpenHashMap<>();
    private int heldSlot;
    private Pos position = new Pos(0, 0, 0, -180, 0);
    private Vec velocity = Vec.ZERO;
    private boolean onGround;
    private boolean horizontalCollision;
    private boolean minorHorizontalCollision;
    /**
     * 实际支撑玩家的方块（原版 {@code Entity.mainSupportingBlockPos}）；不着地或者没有找到时为 null
     */
    private @Nullable Point supportingBlock;
    /**
     * 着地但没有找到支撑方块（原版 {@code Entity.onGroundNoBlocks}）
     */
    private boolean onGroundNoBlocks;
    /**
     * 原版 {@code LivingEntity.noJumpDelay}：起跳之后 10 tick 内按住跳跃也不再起跳
     */
    private int noJumpDelay;
    private boolean sneaking;
    private @Nullable PhysicsResult previousResult;
    private @Nullable Integer vehicle;

    public LocalPlayer(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public UUID uuid() {
        return uuid;
    }

    public String username() {
        return username;
    }

    public int entityId() {
        return entityId;
    }

    public void entityId(int entityId) {
        this.entityId = entityId;
    }

    public GameMode gameMode() {
        return gameMode;
    }

    public void gameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public Abilities abilities() {
        return abilities;
    }

    public float health() {
        return health;
    }

    public void health(float health, int food, float saturation) {
        this.health = health;
        this.food = food;
        this.saturation = saturation;
    }

    public int food() {
        return food;
    }

    public float saturation() {
        return saturation;
    }

    public boolean dead() {
        return dead;
    }

    public void dead(boolean dead) {
        this.dead = dead;
    }

    public void experience(float progress, int level, int total) {
        this.experienceProgress = progress;
        this.level = level;
        this.totalExperience = total;
    }

    public AttributeMap attributes() {
        return attributes;
    }

    public EffectMap effects() {
        return effects;
    }

    public Map<String, Integer> cooldowns() {
        return cooldowns;
    }

    public Map<Integer, Metadata.Entry<?>> metadata() {
        return metadata;
    }

    public int heldSlot() {
        return heldSlot;
    }

    public void heldSlot(int heldSlot) {
        this.heldSlot = heldSlot;
    }

    public Pos position() {
        return position;
    }

    public void position(Pos position) {
        this.position = position;
    }

    public Vec velocity() {
        return velocity;
    }

    public void velocity(Vec velocity) {
        this.velocity = velocity;
    }

    public boolean onGround() {
        return onGround;
    }

    public boolean horizontalCollision() {
        return horizontalCollision;
    }

    public void movementFlags(boolean onGround, boolean horizontalCollision) {
        movementFlags(onGround, horizontalCollision, false);
    }

    /**
     * @param minorHorizontalCollision 顶住墙壁并且实际移动方向与期望方向相差很小（原版 {@code minorHorizontalCollision}），
     *                                 这种碰撞不让疾跑停止
     */
    public void movementFlags(boolean onGround, boolean horizontalCollision, boolean minorHorizontalCollision) {
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
        this.minorHorizontalCollision = minorHorizontalCollision;
    }

    public boolean minorHorizontalCollision() {
        return minorHorizontalCollision;
    }

    public @Nullable Point supportingBlock() {
        return supportingBlock;
    }

    public boolean onGroundNoBlocks() {
        return onGroundNoBlocks;
    }

    public void supportingBlock(@Nullable Point supportingBlock, boolean onGroundNoBlocks) {
        this.supportingBlock = supportingBlock;
        this.onGroundNoBlocks = onGroundNoBlocks;
    }

    public int noJumpDelay() {
        return noJumpDelay;
    }

    public void noJumpDelay(int noJumpDelay) {
        this.noJumpDelay = noJumpDelay;
    }

    public boolean sneaking() {
        return sneaking;
    }

    public void sneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public @Nullable PhysicsResult previousResult() {
        return previousResult;
    }

    public void previousResult(@Nullable PhysicsResult previousResult) {
        this.previousResult = previousResult;
    }

    /**
     * 乘坐的实体；没有乘坐时为 null
     */
    public @Nullable Integer vehicle() {
        return vehicle;
    }

    public void vehicle(@Nullable Integer vehicle) {
        this.vehicle = vehicle;
    }

    /**
     * 每 tick 递减物品冷却与药水效果剩余时间
     */
    public void tickTimers() {
        cooldowns.replaceAll((_, ticks) -> ticks - 1);
        cooldowns.values().removeIf(ticks -> ticks <= 0);
        effects.tick();
    }

    public double scale() {
        return attributes.value(Attribute.SCALE);
    }

    public double eyeHeight() {
        return (sneaking ? SNEAKING_EYE_HEIGHT : STANDING_EYE_HEIGHT) * scale();
    }

    public Vec eyePosition() {
        return position.asVec().add(0, eyeHeight(), 0);
    }

    /**
     * 宽 0.6、高 1.8（潜行时 1.5），乘以 {@code minecraft:scale} 属性
     */
    public BoundingBox boundingBox() {
        var scale = scale();
        return new BoundingBox(WIDTH * scale, (sneaking ? SNEAKING_HEIGHT : STANDING_HEIGHT) * scale, WIDTH * scale);
    }

    public PlayerSnapshot snapshot() {
        return new PlayerSnapshot(entityId, uuid, username, position, velocity, onGround, health, food, saturation, dead,
                gameMode, abilities.snapshot(), experienceProgress, level, totalExperience, attributes.values(),
                effects.all(), heldSlot);
    }
}
