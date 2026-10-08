package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.ChunkNotLoadedException;
import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.entity.ClientEntity;
import io.ib67.yuanban.bot.observe.Observation.PlayerMoved;
import io.ib67.yuanban.bot.player.AttributeMap;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.protocol.Vectors;
import io.ib67.yuanban.bot.scheduler.BotState;
import io.ib67.yuanban.bot.world.ClientWorld;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.UseEffects;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;

/**
 * 移动队列与每 tick 的移动步骤：先进先出，一次运行一个，按 tick 推进。只在调度线程上使用。
 * <p>
 * movement suspension 期间 moveTo、moveForward、jumpTo 不推进（期望方向与移动方式保持暂停开始时的值），轮到的也不开始，
 * 速度与位置的计算不执行；朝向、移动包、Player Input 与疾跑 Player Command 照常。
 * <p>
 * 骑乘模型中的载具时按原版 {@code LocalPlayer.tick} 中乘客的分支执行，见 {@link #rideTick}；
 * 最下层的载具或者中间的载具所在区块未加载时原版不 tick 乘客（{@link #vehicleTicking}），这一步什么都不做。
 */
public final class MovementController {
    /**
     * 疾跑修饰符（原版 {@code LivingEntity.SPEED_MODIFIER_SPRINTING}）
     */
    static final AttributeModifier SPRINTING = new AttributeModifier(AttributeMap.SPRINTING_ID, 0.3,
            ModifierOperation.ADD_MULTIPLIED_TOTAL);
    /**
     * 期望方向相对朝向的分量超过这个值时视为按下对应的按键
     */
    static final double INPUT_THRESHOLD = 1.0E-3;
    /**
     * 没有按下任何按键的 Player Input（原版新玩家的 {@code lastSentInput}）
     */
    private static final ServerboundPlayerInputPacket NO_INPUT =
            new ServerboundPlayerInputPacket(false, false, false, false, false, false, false);

    private final BotState state;
    private final ArrayDeque<MovementAction> queue = new ArrayDeque<>();
    private final RotationArbiter arbiter = new RotationArbiter();
    private MovementSender sender = new MovementSender();
    private @Nullable MovementAction current;
    private MoveIntent lastIntent = MoveIntent.NONE;
    /**
     * 上一次发送的 Player Input
     */
    private ServerboundPlayerInputPacket lastInput = NO_INPUT;
    /**
     * 本地的疾跑状态（属性上带疾跑修饰符）
     */
    private boolean sprinting;
    /**
     * 上一次用 Player Command 告知服务端的疾跑状态（原版 {@code LocalPlayer.wasSprinting}）
     */
    private boolean sentSprinting;

    public MovementController(BotState state) {
        this.state = state;
    }

    public LocalPlayer player() {
        return state.player();
    }

    public ClientWorld world() {
        return state.world();
    }

    public RotationArbiter arbiter() {
        return arbiter;
    }

    public void enqueue(MovementAction action) {
        state.checkThread();
        action.handle().canceller(reason -> cancel(action, reason));
        queue.addLast(action);
    }

    /**
     * 当前移动动作的移动方式；没有正在运行的移动动作时为 null
     */
    public @Nullable MovementMode currentMode() {
        return current != null && current.moves() && !current.handle().isDone() && lastIntent.moving() ? lastIntent.mode() : null;
    }

    /**
     * 世界重建或玩家死亡：取消执行中和排队中的全部动作
     */
    public void cancelAll(String reason) {
        state.checkThread();
        var all = new ArrayList<MovementAction>();
        if (current != null) {
            all.add(current);
        }
        all.addAll(queue);
        current = null;
        queue.clear();
        lastIntent = MoveIntent.NONE;
        for (var action : all) {
            action.handle().cancelled(reason);
        }
    }

    public void onBlockChanged(Point blockPosition) {
        if (current != null) {
            current.onBlockChanged(blockPosition);
        }
    }

    /**
     * 服务端传送了玩家：清空上一次的模拟结果，触发重新寻路。
     * 传送的应答不计入移动包的上一次发送记录，着地与顶住墙壁标记不变（原版客户端行为）
     */
    public void onTeleport() {
        state.player().previousResult(null);
        if (current != null) {
            current.onTeleport();
        }
    }

    /**
     * 服务端设置了玩家的朝向（不骑乘时的 Synchronize Player Position、Player Rotation、应用到本地玩家的
     * Synchronize Vehicle Position）：原版 {@code handleMovePlayer}、{@code handleRotatePlayer} 设置的朝向一直保持，
     * 所以 rotate、lookAt 留下的朝向不再生效，之后没有其它来源时保持服务端给出的朝向
     */
    public void onServerRotation() {
        state.checkThread();
        arbiter.clearManual();
    }

    /**
     * 本地玩家是否控制这个实体（原版 {@code isLocalInstanceAuthoritative}，条件见 {@link VehicleControl}）
     */
    public boolean controlledLocally(ClientEntity entity) {
        return VehicleControl.controlledBy(entity, state);
    }

    /**
     * 新的本地玩家（Login (play)、Respawn）：移动包的上一次发送记录重新开始。
     * keepInput 为 false 时上一次发送的 Player Input 为空、疾跑状态为 false（Respawn 带 Data kept 0x02 时保留）
     */
    public void newPlayer(boolean keepInput) {
        sender = new MovementSender();
        if (keepInput) {
            // 原版新玩家的 wasSprinting 取旧玩家此刻的疾跑状态
            sentSprinting = sprinting;
        } else {
            lastInput = NO_INPUT;
            sprinting = false;
            sentSprinting = false;
        }
    }

    /**
     * 一个 tick 的移动步骤
     *
     * @param suspended    movement suspension 是否生效
     * @param handRotation 正在运行、需要对准目标的手部动作要看向的朝向
     * @param itemInUse    正在使用的物品的 {@code use_effects} 组件；没有在使用物品时为 null（{@code HandController.itemInUse}）
     */
    public void tick(boolean suspended, @Nullable Rotation handRotation, @Nullable UseEffects itemInUse) {
        state.checkThread();
        var player = state.player();
        var vehicle = vehicle();
        if (vehicle != null && !vehicleTicking(vehicle)) {
            return;
        }
        startNextIfIdle(suspended);
        var action = current;
        var intent = MoveIntent.NONE;
        Rotation moveRotation = null;
        if (action != null && action.moves()) {
            if (suspended) {
                intent = lastIntent.frozen();
            } else {
                intent = action.tick(this);
                lastIntent = intent;
            }
            if (intent.moving()) {
                moveRotation = Rotation.horizontal(intent.direction().x(), intent.direction().z());
            }
        } else if (action != null) {
            action.tick(this);
        }
        var moving = action != null && action.moves() && !action.handle().isDone();
        if (!moving) {
            intent = MoveIntent.NONE;
        }

        var previous = player.position();
        var rotation = arbiter.decide(handRotation, moveRotation, moving,
                new Rotation(previous.yaw(), previous.pitch()));
        player.position(previous.withView(rotation.yaw(), rotation.pitch()));
        // 原版 LocalPlayer.aiStep 中的 crouching：飞行时按住潜行键不蹲下
        player.sneaking(intent.mode() == MovementMode.SNEAK && !player.abilities().flying());
        updateSprinting(intent, rotation, vehicle, itemInUse);
        if (vehicle != null) {
            rideTick(vehicle, intent, rotation, moving ? action : null, suspended);
            finish(action);
            return;
        }
        // 原版 LocalPlayer.tick 在移动计算（super.tick）之后发送 Player Input，按键在移动计算之前确定
        var input = inputPacket(intent, rotation);

        if (!suspended && !player.dead()) {
            var from = player.position();
            try {
                Kinematics.step(player, state.world(), state.tags(), intent, sprinting, itemInUse);
                stopFlyingOnGround();
                var to = player.position();
                if (!from.samePoint(to) && state.observations().wants(PlayerMoved.class)) {
                    state.observations().emit(new PlayerMoved(from, to, player.onGround()));
                }
                if (moving && !action.handle().isDone()) {
                    action.afterMove(this, from, to, player.onGround());
                }
            } catch (ChunkNotLoadedException e) {
                if (moving && !action.handle().isDone()) {
                    action.handle().cancelled(ActionCancelledException.CHUNK_NOT_LOADED);
                }
            }
        }

        sendInput(input);
        sendSprintingIfNeeded();
        sender.send(state.outbox(), player.position(), player.onGround(), player.horizontalCollision());
        finish(action);
    }

    /**
     * 原版 {@code LocalPlayer.aiStep} 的最后：旁观模式以外飞行中着地时停止飞行，并用 Player Abilities 告知服务端
     * （原版 {@code onUpdateAbilities}，标记只有「正在飞行」一位，这时为 0）
     */
    private void stopFlyingOnGround() {
        var player = state.player();
        if (player.onGround() && player.abilities().flying() && player.gameMode() != GameMode.SPECTATOR) {
            player.abilities().stopFlying();
            state.outbox().send(new ServerboundPlayerAbilitiesPacket(false));
        }
    }

    /**
     * 本 tick 的动作：设置朝向的动作在本 tick 执行完毕；结束的动作离开队列
     */
    private void finish(@Nullable MovementAction action) {
        if (action != null && !action.moves()) {
            action.handle().succeed();
        }
        if (action != null && action.handle().isDone()) {
            current = null;
        }
    }

    /**
     * 本地玩家乘坐的载具；没有乘坐时为 null
     */
    private @Nullable ClientEntity vehicle() {
        var id = state.player().vehicle();
        if (id == null) {
            return null;
        }
        // 载具被移除时乘客停止乘坐，Set Passengers 的载具未知时整个数据包被忽略，所以载具一定在模型中
        return Objects.requireNonNull(state.world().entities().get(id), () -> "the player rides unknown entity " + id);
    }

    /**
     * 原版 {@code ClientLevel.tickNonPassenger} 与 {@code tickPassenger}：乘客只在最下层的载具执行之后沿乘坐关系依次执行，
     * 所以最下层的载具所在区块要已加载，中间的载具除玩家以外所在区块也要已加载
     */
    private boolean vehicleTicking(ClientEntity vehicle) {
        var world = state.world();
        var current = vehicle;
        while (current.vehicle() != null) {
            if (current.type() != EntityType.PLAYER && !world.chunkLoaded(current.position().chunkX(), current.position().chunkZ())) {
                return false;
            }
            var below = (int) current.vehicle();
            current = Objects.requireNonNull(world.entities().get(below), () -> "unknown vehicle " + below);
        }
        return world.chunkLoaded(current.position().chunkX(), current.position().chunkZ());
    }

    /**
     * 原版 {@code LocalPlayer.tick} 中乘客的分支：
     * <ul>
     *     <li>疾跑按 {@link #updateSprinting} 的条件：只有载具可以疾跑（骆驼）并且由本地玩家控制时保持或开始疾跑，否则本地停止疾跑；</li>
     *     <li>Player Input 照常在变化时发送；不执行移动模拟，速度清零（原版 {@code rideTick}），着地与顶住墙壁为 false；</li>
     *     <li>每 tick 发送 Set Player Rotation；本地玩家控制最下层的载具时，再发送载具在模型中的位置、朝向与着地（Move Vehicle），
     *     然后在疾跑状态改变时发送 Player Command；不控制时不发送疾跑的 Player Command，下马之后的第一次移动包之前再发送；</li>
     *     <li>位置设为座位位置（{@link RidingSeat}），不发送移动包，移动包的上一次发送记录不变；有正在进行的插值时先插值一步
     *     （{@link Kinematics#interpolate}，位置随后被座位覆盖，朝向保留，Set Player Rotation 发送插值之后的朝向）；
     *     movement suspension 期间位置与速度不变。</li>
     * </ul>
     */
    private void rideTick(ClientEntity vehicle, MoveIntent intent, Rotation rotation, @Nullable MovementAction moving,
                          boolean suspended) {
        var player = state.player();
        sendInput(inputPacket(intent, rotation));
        player.movementFlags(false, false);
        if (!suspended) {
            var from = player.position();
            player.velocity(Vec.ZERO);
            player.previousResult(null);
            Kinematics.interpolate(player, state.world());
            var to = RidingSeat.seat(vehicle, player, state.world().worldAge());
            player.position(to);
            if (!from.samePoint(to) && state.observations().wants(PlayerMoved.class)) {
                state.observations().emit(new PlayerMoved(from, to, player.onGround()));
            }
            if (moving != null && !moving.handle().isDone()) {
                moving.afterMove(this, from, to, player.onGround());
            }
        }
        var look = player.position();
        state.outbox().send(new ServerboundMovePlayerRotPacket(false, false, look.yaw(), look.pitch()));
        var root = vehicle;
        while (root.vehicle() != null) {
            var below = (int) root.vehicle();
            root = Objects.requireNonNull(state.world().entities().get(below), () -> "unknown vehicle " + below);
        }
        if (VehicleControl.controlledBy(root, state)) {
            var position = root.position();
            state.outbox().send(new ServerboundMoveVehiclePacket(Vectors.vector(position), position.yaw(), position.pitch(),
                    root.onGround()));
            sendSprintingIfNeeded();
        }
    }

    /**
     * 原版 {@code LocalPlayer.sendIsSprintingIfNeeded}：本地的疾跑状态与上次告知服务端的不同时发送 Player Command
     */
    private void sendSprintingIfNeeded() {
        if (sprinting == sentSprinting) {
            return;
        }
        sentSprinting = sprinting;
        state.outbox().send(new ServerboundPlayerCommandPacket(state.player().entityId(), sprinting
                ? PlayerState.START_SPRINTING : PlayerState.STOP_SPRINTING));
    }

    /**
     * 当前动作结束时开始队列中的下一个。movement suspension 期间轮到的 moveTo、moveForward、jumpTo 不开始
     * （不寻路、不接管朝向、不清除 rotate 或 lookAt 设置的朝向），留在队首等到恢复，排在它后面的动作随之等待
     */
    private void startNextIfIdle(boolean suspended) {
        while (current == null || current.handle().isDone()) {
            var next = queue.peekFirst();
            if (next == null || (suspended && next.moves() && !next.handle().isDone())) {
                current = null;
                return;
            }
            current = queue.pollFirst();
            if (current.handle().isDone()) {
                continue;
            }
            if (current.moves()) {
                arbiter.clearManual();
                lastIntent = MoveIntent.NONE;
            }
            current.start(this);
        }
    }

    private void cancel(MovementAction action, String reason) {
        if (action == current) {
            current = null;
            lastIntent = MoveIntent.NONE;
        } else {
            queue.remove(action);
        }
        action.handle().cancelled(reason);
    }

    /**
     * 按原版 {@code LocalPlayer.aiStep} 决定本地的疾跑状态，在移动计算之前，先判断开始、再判断停止：
     * <ul>
     *     <li>开始（原版 {@code canStartSprinting}，按住疾跑键时）：还没有疾跑，有向前的输入，可以疾跑，没有在使用不允许疾跑的物品；</li>
     *     <li>停止（原版 {@code shouldStopRunSprinting}）：不能疾跑，或者没有向前的输入，或者上一 tick 顶住墙壁并且不是
     *     擦过墙壁的碰撞（{@code minorHorizontalCollision}）；</li>
     *     <li>可以疾跑（原版 {@code isSprintingPossible}）：没有失明效果，并且骑乘时载具可以疾跑（骆驼）并且由本地玩家控制，
     *     不骑乘时饥饿值大于 6 或者允许飞行。</li>
     * </ul>
     * 本库没有单独的疾跑键：移动方式为 {@code SPRINT} 并且期望移动方向不为零时视为按住疾跑键；
     * 移动方式不是 {@code SPRINT} 时也停止疾跑（原版松开疾跑键不停止，本库的 {@link MovementMode} 表示这个动作要求的移动方式）。
     * 潜行与疾跑是不同的移动方式，所以原版「蹲下时不开始疾跑」的条件自然成立。
     * 疾跑状态改变时在属性上加入或移除疾跑修饰符（原版客户端行为）；告知服务端的 Player Command 由 {@link #sendSprintingIfNeeded} 发送
     */
    private void updateSprinting(MoveIntent intent, Rotation rotation, @Nullable ClientEntity vehicle,
                                 @Nullable UseEffects itemInUse) {
        var player = state.player();
        var sprintKey = intent.mode() == MovementMode.SPRINT && intent.moving();
        var forward = forwardInput(intent, rotation) > INPUT_THRESHOLD;
        var possible = player.effects().get(Effect.BLINDNESS) == null && (vehicle != null
                ? VehicleControl.canSprint(vehicle) && VehicleControl.controlledBy(vehicle, state)
                : player.food() > 6 || player.abilities().allowFlying());
        // 与原版相同，先判断开始，再判断停止：顶住墙壁时开始之后在同一个 tick 里立即停止
        var now = sprinting;
        if (!now && sprintKey && forward && possible && (itemInUse == null || itemInUse.canSprint())) {
            now = true;
        }
        if (now && (!sprintKey || !forward || !possible || player.horizontalCollision() && !player.minorHorizontalCollision())) {
            now = false;
        }
        if (now == sprinting) {
            return;
        }
        sprinting = now;
        if (now) {
            player.attributes().addModifier(AttributeType.Builtin.MOVEMENT_SPEED, SPRINTING);
        } else {
            player.attributes().removeModifier(AttributeType.Builtin.MOVEMENT_SPEED, AttributeMap.SPRINTING_ID);
        }
    }

    /**
     * 期望移动方向在当前朝向前方的分量
     */
    private static double forwardInput(MoveIntent intent, Rotation rotation) {
        var yaw = Math.toRadians(rotation.yaw());
        var direction = intent.direction();
        return -Math.sin(yaw) * direction.x() + Math.cos(yaw) * direction.z();
    }

    /**
     * Player Input：前、后、左、右按期望移动方向相对当前朝向的分量符号确定；跳跃为本 tick 是否起跳；
     * 疾跑为疾跑键（移动方式为 {@code SPRINT} 并且期望移动方向不为零，原版 {@code keyPresses.sprint}），与本地的疾跑状态无关
     */
    private ServerboundPlayerInputPacket inputPacket(MoveIntent intent, Rotation rotation) {
        var yaw = Math.toRadians(rotation.yaw());
        var direction = intent.direction();
        var forward = forwardInput(intent, rotation);
        var left = Math.cos(yaw) * direction.x() + Math.sin(yaw) * direction.z();
        var jump = intent.jump() && state.player().onGround();
        return new ServerboundPlayerInputPacket(forward > INPUT_THRESHOLD, forward < -INPUT_THRESHOLD,
                left > INPUT_THRESHOLD, left < -INPUT_THRESHOLD, jump,
                intent.mode() == MovementMode.SNEAK, intent.mode() == MovementMode.SPRINT && intent.moving());
    }

    /**
     * 与上次发送的不同时发送 Player Input
     */
    private void sendInput(ServerboundPlayerInputPacket packet) {
        if (!packet.equals(lastInput)) {
            lastInput = packet;
            state.outbox().send(packet);
        }
    }
}
