package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.data.Items;
import io.ib67.yuanban.bot.inventory.Window;
import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.protocol.Vectors;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import io.ib67.yuanban.bot.scheduler.BotState;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSpectatorActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

/**
 * {@code attemptBreakBlock}：一次点击，按原版 {@code Minecraft.startAttack}、{@code continueAttack} 与
 * {@code MultiPlayerGameMode} 的挖掘逻辑执行。第 1 tick 为开始的 tick：
 * <ul>
 *     <li>目标为空气时直接执行完毕，不发送数据包；</li>
 *     <li>旁观模式不挖掘，只发送不带实体编号的 Spectator Action；主手拿着穿刺武器时不挖掘，发送 Stab 与 Swing Arm；
 *     这两种情况数据包写出即执行完毕；</li>
 *     <li>冒险模式不能破坏目标、目标在世界边框之外时不发送 Started digging，只发送 Swing Arm，
 *     再执行一次「继续」中的挖掘延迟步骤；这几种情况随即执行完毕；</li>
 *     <li>能力中有瞬间破坏时发送 Started digging（带序号）与 Swing Arm，挖掘延迟设为 5；
 *     本 tick 的进度已经达到 1 时发送 Started digging 与 Swing Arm，挖掘延迟不变；这两种情况收到确认即执行完毕；</li>
 *     <li>其余情况发送 Started digging 与 Swing Arm，然后在同一 tick 执行一次「继续」。</li>
 * </ul>
 * 「继续」在之后的每个 tick 执行：主手拿着穿刺武器时什么都不做；目标变成空气时直接执行完毕，不发送数据包；
 * 挖掘延迟大于 0 时减一并发送 Swing Arm，不累加进度；能力中有瞬间破坏并且目标在世界边框之内时，挖掘延迟设为 5，
 * 以新的序号发送 Started digging 与 Swing Arm，收到确认即执行完毕；主手物品与开始挖掘时不同时重新开始挖掘（{@link #restart}）；
 * 否则累加进度，达到 1 时先发送 Finished digging（带序号），挖掘延迟设为 5，再发送 Swing Arm，收到确认即执行完毕；未达到时只发送 Swing Arm。
 * <p>
 * 读取的方块（目标，以及计算进度时眼睛所在的方块）所在区块未加载时抛出的 {@link io.ib67.yuanban.bot.ChunkNotLoadedException}
 * 由 {@link HandController#tick} 处理：开始的 tick 中这些读取都在发送数据包之前，取消时什么都不发送；挖掘中取消时由 {@link #cleanup} 发送 Cancelled digging。
 */
public final class DigTask extends HandAction {
    private final Point position;
    private final Vector3i blockPosition;
    private final @Nullable Direction requestedFace;
    private Direction face = Direction.UP;
    private Stage stage = Stage.STARTING;
    /**
     * 与原版客户端相同，进度以 float 累加
     */
    private float progress;
    private int pendingSequence;
    /**
     * 开始挖掘时的主手物品（原版 {@code MultiPlayerGameMode.destroyingItem}）；空手时为 null
     */
    private @Nullable ItemStack destroyingItem;

    public DigTask(ActionImpl handle, Point position, @Nullable Direction face) {
        super(handle);
        this.position = new Vec(position.blockX(), position.blockY(), position.blockZ());
        this.blockPosition = Vectors.blockPos(position);
        this.requestedFace = face;
    }

    @Override
    void start(HandController controller) {
        var state = controller.state();
        var player = state.player();
        var world = state.world();
        var block = world.getBlock(position, Block.Getter.Condition.TYPE);
        if (block.air()) {
            handle.succeed();
            return;
        }
        // 原版 startAttack：旁观模式不挖掘、不挥手，目标不是实体时发送不带实体编号的 Spectator Action（spectatorNoAction）
        if (player.gameMode() == GameMode.SPECTATOR) {
            state.outbox().send(new ServerboundSpectatorActionPacket(OptionalInt.empty()));
            state.outbox().completeWhenWritten(handle);
            return;
        }
        // 原版 startAttack：主手拿着穿刺武器时不挖掘，调用 MultiPlayerGameMode.piercingAttack 发送 Stab，然后挥手
        if (Items.has(mainHand(state), DataComponentTypes.PIERCING_WEAPON)) {
            state.outbox().send(new ServerboundPlayerActionPacket(PlayerAction.STAB, Vector3i.ZERO, Direction.DOWN, 0));
            swing(state);
            state.outbox().completeWhenWritten(handle);
            return;
        }
        if (restrictedInAdventure(state, block, position) || !world.border().withinBounds(position.blockX(), position.blockZ())) {
            swing(state);
            if (controller.destroyDelay() > 0) {
                controller.destroyDelay(controller.destroyDelay() - 1);
                swing(state);
            }
            handle.succeed();
            return;
        }
        face = requestedFace != null ? requestedFace : faceTowards(player.eyePosition());
        // 原版 startDestroyBlock 在 startPrediction 中先判断本 tick 的进度，再发出 Started digging；能力中有瞬间建造时不判断进度
        var instantBreak = player.abilities().instantBreak();
        var finishedThisTick = !instantBreak && progressThisTick(controller) >= 1;
        var sequence = controller.sequence().next();
        state.outbox().send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, blockPosition, face, sequence));
        if (instantBreak) {
            swing(state);
            controller.destroyDelay(HandController.DESTROY_DELAY_TICKS);
            awaitAcknowledgement(controller, sequence);
            return;
        }
        if (finishedThisTick) {
            swing(state);
            awaitAcknowledgement(controller, sequence);
            return;
        }
        swing(state);
        destroyingItem = mainHand(state);
        stage = Stage.DIGGING;
        continueDigging(controller);
    }

    @Override
    void tick(HandController controller) {
        if (stage == Stage.DIGGING) {
            continueDigging(controller);
        }
    }

    /**
     * 原版 {@code Minecraft.continueAttack} 与 {@code MultiPlayerGameMode.continueDestroyBlock}，加上调用方随后的 Swing Arm
     */
    private void continueDigging(HandController controller) {
        var state = controller.state();
        // 原版 continueAttack：主手拿着穿刺武器时不调用 continueDestroyBlock，也不挥手
        if (Items.has(mainHand(state), DataComponentTypes.PIERCING_WEAPON)) {
            return;
        }
        if (state.world().getBlock(position, Block.Getter.Condition.TYPE).air()) {
            handle.succeed();
            return;
        }
        if (controller.destroyDelay() > 0) {
            controller.destroyDelay(controller.destroyDelay() - 1);
            swing(state);
            return;
        }
        // 原版 continueDestroyBlock：能力中有瞬间建造并且目标在世界边框之内时走创造模式的分支，不累加进度，也不看主手物品是否改变：
        // 挖掘延迟设为 5，以新的序号发送 Started digging，调用方随后挥手；收到确认即执行完毕
        if (state.player().abilities().instantBreak() && state.world().border().withinBounds(position.blockX(), position.blockZ())) {
            controller.destroyDelay(HandController.DESTROY_DELAY_TICKS);
            var sequence = controller.sequence().next();
            state.outbox().send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, blockPosition, face, sequence));
            swing(state);
            awaitAcknowledgement(controller, sequence);
            return;
        }
        // 原版 sameDestroyTarget：主手物品（物品与组件）与开始挖掘时不同就不是同一个挖掘目标
        if (!Items.sameItemSameComponents(mainHand(state), destroyingItem)) {
            restart(controller);
            return;
        }
        progress += (float) progressThisTick(controller);
        if (progress >= 1) {
            var sequence = controller.sequence().next();
            state.outbox().send(new ServerboundPlayerActionPacket(PlayerAction.FINISH_DIGGING, blockPosition, face, sequence));
            controller.destroyDelay(HandController.DESTROY_DELAY_TICKS);
            swing(state);
            awaitAcknowledgement(controller, sequence);
            return;
        }
        swing(state);
    }

    /**
     * 原版 {@code continueDestroyBlock} 中目标不同的分支调用的 {@code startDestroyBlock}（这时正在挖掘），加上调用方随后的 Swing Arm。
     * 这个 tick 不累加进度：
     * <ul>
     *     <li>冒险模式不能破坏目标、目标在世界边框之外时什么都不发送，调用方也不挥手，下一 tick 再次尝试；</li>
     *     <li>否则先中止原来的挖掘（Cancelled digging，面为当前的面），再以新的序号发送 Started digging；
     *     本 tick 的进度已经达到 1 时发送 Swing Arm，收到这个序号的确认即执行完毕；否则进度归零，记下现在的主手物品，发送 Swing Arm。</li>
     * </ul>
     */
    private void restart(HandController controller) {
        var state = controller.state();
        var block = state.world().getBlock(position, Block.Getter.Condition.TYPE);
        if (restrictedInAdventure(state, block, position) || !state.world().border().withinBounds(position.blockX(), position.blockZ())) {
            return;
        }
        state.outbox().send(new ServerboundPlayerActionPacket(PlayerAction.CANCEL_DIGGING, blockPosition, face, 0));
        var sequence = controller.sequence().next();
        state.outbox().send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, blockPosition, face, sequence));
        if (progressThisTick(controller) >= 1) {
            swing(state);
            awaitAcknowledgement(controller, sequence);
            return;
        }
        progress = 0;
        destroyingItem = mainHand(state);
        swing(state);
    }

    /**
     * 取消：发送「Cancelled digging」，面为 -Y
     */
    @Override
    void cleanup(HandController controller) {
        if (pendingSequence != 0) {
            controller.sequence().forget(pendingSequence);
        }
        if (stage == Stage.DIGGING) {
            controller.state().outbox().send(new ServerboundPlayerActionPacket(PlayerAction.CANCEL_DIGGING, blockPosition,
                    Direction.DOWN, 0));
        }
    }

    @Override
    @Nullable Rotation lookRotation(HandController controller) {
        return Rotation.lookAt(controller.state().player().eyePosition(), position.add(0.5, 0.5, 0.5));
    }

    private void awaitAcknowledgement(HandController controller, int sequence) {
        stage = Stage.AWAITING_ACK;
        pendingSequence = sequence;
        controller.sequence().await(sequence, () -> {
            pendingSequence = 0;
            handle.succeed();
        });
    }

    /**
     * 原版 {@code Player.blockActionRestricted} 中冒险模式的部分：不能建造时，主手物品的 {@code can_break} 必须匹配目标方块
     */
    private static boolean restrictedInAdventure(BotState state, Block block, Point position) {
        var player = state.player();
        if (player.gameMode() != GameMode.ADVENTURE || player.abilities().mayBuild()) {
            return false;
        }
        var held = mainHand(state);
        var canBreak = Items.component(held, DataComponentTypes.CAN_BREAK);
        return held == null || canBreak == null
                || !BlockPredicates.test(canBreak, block, state.world().blockEntity(position.blockX(), position.blockY(),
                position.blockZ()), position, state.tags());
    }

    private static @Nullable ItemStack mainHand(BotState state) {
        return state.inventory().player().get(Window.PLAYER_HOTBAR_START + state.player().heldSlot());
    }

    private static void swing(BotState state) {
        state.outbox().send(new ServerboundSwingPacket(Hand.MAIN_HAND));
    }

    private double progressThisTick(HandController controller) {
        var state = controller.state();
        var player = state.player();
        var world = state.world();
        var block = world.getBlock(position, Block.Getter.Condition.TYPE);
        var held = mainHand(state);
        var eye = world.getBlock(player.eyePosition(), Block.Getter.Condition.TYPE);
        var eyesInWater = eye.compare(Block.WATER) || "true".equals(eye.getProperty("waterlogged"));
        return DigSpeed.progress(block, held, state.tags(), player.attributes()::value, player.effects()::level,
                player.onGround(), eyesInWater);
    }

    /**
     * 朝向玩家眼睛的那一面：方块中心指向眼睛的向量中绝对值最大的分量
     */
    private Direction faceTowards(Vec eye) {
        var dx = eye.x() - (position.x() + 0.5);
        var dy = eye.y() - (position.y() + 0.5);
        var dz = eye.z() - (position.z() + 0.5);
        var ax = Math.abs(dx);
        var ay = Math.abs(dy);
        var az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private enum Stage {
        STARTING,
        DIGGING,
        AWAITING_ACK
    }
}
