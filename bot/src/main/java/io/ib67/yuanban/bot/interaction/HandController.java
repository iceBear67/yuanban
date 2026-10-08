package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.movement.MovementController;
import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.data.Items;
import io.ib67.yuanban.bot.scheduler.BotState;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.UseEffects;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * 手部交互队列：先进先出，一次运行一个，按 tick 推进。只在调度线程上使用。
 * <p>
 * 同时保存原版 {@code MultiPlayerGameMode} 中跨动作的状态：挖掘延迟计数与已经发送的快捷栏槽位，
 * 它们在 Login (play) 时归零，Respawn 时不变；方块交互序号属于世界，在新建世界时归零。
 */
public final class HandController {
    /**
     * 原版 {@code MultiPlayerGameMode}：挖掘完成或者瞬间破坏之后的挖掘延迟
     */
    public static final int DESTROY_DELAY_TICKS = 5;
    /**
     * 原版 {@code UseEffects.DEFAULT}：物品没有 {@code use_effects} 组件时使用
     */
    public static final UseEffects DEFAULT_USE_EFFECTS = new UseEffects(false, true, 0.2F);

    private final BotState state;
    private final MovementController movement;
    private final BlockSequence sequence = new BlockSequence();
    private final ArrayDeque<HandAction> queue = new ArrayDeque<>();
    private @Nullable HandAction current;
    private int destroyDelay;
    private int carriedIndex;

    public HandController(BotState state, MovementController movement) {
        this.state = state;
        this.movement = movement;
    }

    public BotState state() {
        return state;
    }

    public BlockSequence sequence() {
        return sequence;
    }

    /**
     * 当前移动动作是否为潜行
     */
    boolean sneaking() {
        return movement.currentMode() == MovementMode.SNEAK;
    }

    public void enqueue(HandAction action) {
        state.checkThread();
        action.handle().canceller(reason -> cancel(action, reason));
        queue.addLast(action);
    }

    /**
     * 原版 {@code MultiPlayerGameMode.ensureHasSentCarriedItem}：本地的快捷栏槽位与上次发送的不同时发送 Set Held Item。
     * 每个 tick 开始时调用（原版在 {@code MultiPlayerGameMode.tick} 中调用），服务端改变快捷栏槽位之后也会因此发回
     */
    public void ensureHasSentCarriedItem() {
        state.checkThread();
        var selected = state.player().heldSlot();
        if (selected != carriedIndex) {
            carriedIndex = selected;
            state.outbox().send(new ServerboundSetCarriedItemPacket(selected));
        }
    }

    /**
     * {@code selectHotbar} 已经发送了 Set Held Item
     */
    public void carriedItemSent(int slot) {
        state.checkThread();
        carriedIndex = slot;
    }

    /**
     * 推进一步
     */
    public void tick() {
        state.checkThread();
        if (current != null && !current.handle().isDone()) {
            current.tick(this);
            return;
        }
        current = null;
        while (!queue.isEmpty()) {
            var next = queue.pollFirst();
            if (next.handle().isDone()) {
                continue;
            }
            current = next;
            current.start(this);
            return;
        }
    }

    /**
     * 本地玩家的朝向是否已经是 rotation。手部步骤在移动步骤之前（原版 {@code handleKeybinds} 在 {@code tickEntities} 之前），
     * 这时本地玩家的朝向是之前的移动步骤（或者服务端的传送）确定并已经发出的朝向，所以为 true 时服务端已经收到这个朝向
     */
    boolean aimed(Rotation rotation) {
        var position = state.player().position();
        return position.yaw() == rotation.yaw() && position.pitch() == rotation.pitch();
    }

    /**
     * 正在使用的物品的 {@code use_effects} 组件（没有这个组件时为原版的默认值 {@link #DEFAULT_USE_EFFECTS}，空手使用时同样如此）；
     * 没有在使用物品时为 null。移动步骤按它减速、判断能否开始疾跑
     */
    public @Nullable UseEffects itemInUse() {
        if (current == null || current.handle().isDone() || !current.usingItem()) {
            return null;
        }
        return Objects.requireNonNullElse(Items.component(current.itemInUse(), DataComponentTypes.USE_EFFECTS),
                DEFAULT_USE_EFFECTS);
    }

    /**
     * 正在运行、需要对准目标的手部动作要看向的朝向
     */
    public @Nullable Rotation lookRotation() {
        if (current == null || current.handle().isDone()) {
            return null;
        }
        return current.lookRotation(this);
    }

    public void acknowledge(int sequenceNumber) {
        state.checkThread();
        sequence.acknowledge(sequenceNumber);
    }

    int destroyDelay() {
        return destroyDelay;
    }

    void destroyDelay(int destroyDelay) {
        this.destroyDelay = destroyDelay;
    }

    /**
     * 世界重建或玩家死亡：取消执行中和排队中的全部动作，执行中的动作完成清理（可能发送数据包）
     */
    public void cancelAll(String reason) {
        state.checkThread();
        var all = new ArrayList<HandAction>();
        if (current != null && !current.handle().isDone()) {
            current.cleanup(this);
            all.add(current);
        }
        all.addAll(queue);
        current = null;
        queue.clear();
        for (var action : all) {
            action.handle().cancelled(reason);
        }
    }

    /**
     * 离开世界：取消执行中和排队中的全部动作，不发送清理数据包，丢弃等待中的确认
     * （原版客户端离开世界时丢弃整个游戏模式控制器，不发送数据包）
     */
    public void discardAll(String reason) {
        state.checkThread();
        var all = new ArrayList<HandAction>();
        if (current != null && !current.handle().isDone()) {
            all.add(current);
        }
        all.addAll(queue);
        current = null;
        queue.clear();
        sequence.reset();
        for (var action : all) {
            action.handle().cancelled(reason);
        }
    }

    /**
     * Login (play)：新的游戏模式控制器，挖掘延迟与已经发送的快捷栏槽位归零
     */
    public void newGameMode() {
        state.checkThread();
        destroyDelay = 0;
        carriedIndex = 0;
    }

    /**
     * 新建世界（Login (play)、维度名称改变的 Respawn）：方块交互序号从 0 开始
     */
    public void newWorld() {
        state.checkThread();
        sequence.reset();
    }

    private void cancel(HandAction action, String reason) {
        if (action == current) {
            action.cleanup(this);
            current = null;
        } else {
            queue.remove(action);
        }
        action.handle().cancelled(reason);
    }
}
