package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.view.ActiveEffect;
import io.ib67.yuanban.bot.view.BossBarSnapshot;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.view.EntitySnapshot;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * 一条观察记录：服务端发来的数据包、由此引起的模型变化，或 bot 自身的事件。全部子类型为不可变 record。
 */
public sealed interface Observation {
    /**
     * 收到的数据包（来自服务端），在它应用到模型之后、它产生的模型层观察记录之后投递。
     */
    record PacketReceived(MinecraftPacket packet) implements Observation {
    }

    /**
     * 发出的数据包（发往服务端），在它交给连接之后投递。
     */
    record PacketSent(MinecraftPacket packet) implements Observation {
    }

    /**
     * bot 进入了一个世界：Login (play) 或 Respawn 应用之后。
     */
    record WorldEntered(WorldInfo world) implements Observation {
    }

    /**
     * 区块安装到世界模型中。
     */
    record ChunkLoaded(int x, int z) implements Observation {
    }

    /**
     * 区块从世界模型中卸载：Unload Chunk，或者落到加载区域之外。
     */
    record ChunkUnloaded(int x, int z) implements Observation {
    }

    /**
     * Block Update 或 Update Section Blocks 改变了一个方块。区块加载与卸载不产生这条记录。
     *
     * @param pos 方块坐标
     */
    record BlockChanged(Point pos, Block old, Block now) implements Observation {
    }

    /**
     * Block Entity Data 改变了一个方块实体的数据。所在区块没有加载、或者该位置的方块没有这个类型的方块实体时，
     * Block Entity Data 被忽略（原版客户端行为），不产生这条记录。
     */
    record BlockEntityChanged(Point pos, NbtMap nbt) implements Observation {
    }

    /**
     * 实体加入世界模型（Spawn Entity）。
     */
    record EntitySpawned(EntitySnapshot entity) implements Observation {
    }

    /**
     * 实体从世界模型中移除（Remove Entities；Spawn Entity 的编号已有实体时，旧实体先被移除）。
     *
     * @param last 移除前的最后状态
     */
    record EntityRemoved(EntitySnapshot last) implements Observation {
    }

    /**
     * 一个改变实体位置的数据包。坐标为模型算出的绝对位置。
     */
    record EntityMoved(int entityId, Point from, Point to, boolean onGround) implements Observation {
    }

    /**
     * Set Entity Metadata 改变了实体的元数据。
     *
     * @param changed 数据包中的条目，按索引
     */
    record EntityMetadataChanged(int entityId, Map<Integer, EntityMetadata<?, ?>> changed) implements Observation {
        public EntityMetadataChanged {
            changed = Map.copyOf(changed);
        }
    }

    /**
     * 实体一个装备槽位的物品改变：Set Equipment 改变模型中的实体；Set Player Inventory Slot 的下标 41、42 改变本地玩家的身体护甲与鞍，
     * 这两个槽位在窗口 0 中没有对应的槽位，{@code entityId} 为本地玩家的实体编号。空物品为 null。
     */
    record EntityEquipmentChanged(int entityId, EquipmentSlot slot, @Nullable ItemStack old, @Nullable ItemStack now)
            implements Observation {
    }

    /**
     * Damage Event。
     *
     * @param type           伤害类型在 {@code minecraft:damage_type} 注册表中的名称
     * @param sourceId       造成伤害的实体；没有时为 null
     * @param directSourceId 直接造成伤害的实体（例如箭）；没有时为 null
     * @param sourcePos      伤害来源的位置；没有时为 null
     */
    record EntityDamaged(int entityId, Key type, @Nullable Integer sourceId,
                         @Nullable Integer directSourceId, @Nullable Point sourcePos) implements Observation {
    }

    /**
     * Entity Event。
     */
    record EntityStatusReceived(int entityId, EntityEvent event) implements Observation {
    }

    /**
     * Pickup Item。
     */
    record ItemPickedUp(int collectedId, int collectorId, int count) implements Observation {
    }

    /**
     * bot 发送了 Player Loaded，动作队列开始执行。
     */
    record PlayerLoaded() implements Observation {
    }

    /**
     * 自身的移动模拟使位置变化的 tick。
     */
    record PlayerMoved(Pos from, Pos to, boolean onGround) implements Observation {
    }

    /**
     * Synchronize Player Position。
     */
    record PlayerTeleported(Pos from, Pos to) implements Observation {
    }

    /**
     * Set Health。
     */
    record HealthChanged(float oldHealth, float health, int food, float saturation) implements Observation {
    }

    /**
     * Combat Death，目标为自己。
     */
    record PlayerDied(Component message) implements Observation {
    }

    /**
     * 本地玩家的游戏模式改变。
     */
    record GameModeChanged(GameMode old, GameMode now) implements Observation {
    }

    /**
     * Entity Effect 或 Remove Entity Effect，目标为自己。
     *
     * @param now  新的效果；移除时为 null
     * @param tick 收到时 bot 已经执行的 tick 数
     */
    record EffectChanged(Effect effect, @Nullable ActiveEffect now, long tick) implements Observation {
    }

    /**
     * Set Experience。
     */
    record ExperienceChanged(float progress, int level, int total) implements Observation {
    }

    /**
     * 窗口中一个槽位的物品改变，包括打开的窗口中玩家物品栏部分的修改同步到窗口 0 的那一条。空物品为 null。
     */
    record SlotChanged(int windowId, int slot, @Nullable ItemStack old, @Nullable ItemStack now) implements Observation {
    }

    /**
     * 光标上的物品改变；空物品为 null。
     */
    record CursorChanged(@Nullable ItemStack old, @Nullable ItemStack now) implements Observation {
    }

    /**
     * Open Screen 或 Open Horse Screen。
     *
     * @param type 窗口类型；马与鹦鹉螺的物品栏没有对应的 {@link ContainerType}，为 null
     */
    record WindowOpened(int windowId, @Nullable ContainerType type, Component title) implements Observation {
    }

    /**
     * 窗口关闭：服务端发来 Close Container，或 bot 发出 Close Container。
     */
    record WindowClosed(int windowId) implements Observation {
    }

    /**
     * 快捷栏选中的槽位改变（0 到 8）。
     */
    record HeldSlotChanged(int old, int now) implements Observation {
    }

    /**
     * Player Chat Message、Disguised Chat Message、不带 overlay 的 System Chat Message。
     */
    record ChatReceived(ChatLine line) implements Observation {
    }

    /**
     * Set Action Bar Text，或带 overlay 的 System Chat Message。
     */
    record ActionBarShown(Component text) implements Observation {
    }

    /**
     * Set Title Text 或 Set Subtitle Text 之后的标题与副标题。
     */
    record TitleShown(@Nullable Component title, @Nullable Component subtitle) implements Observation {
    }

    /**
     * Boss Bar。
     *
     * @param now 变化之后的状态；移除时为 null
     */
    record BossBarChanged(UUID id, @Nullable BossBarSnapshot now) implements Observation {
    }

    /**
     * Update Score 或 Reset Score。
     *
     * @param score 新的分数；移除时为 null
     */
    record ScoreChanged(String objective, String holder, @Nullable Integer score) implements Observation {
    }

    /**
     * 一个动作结束。
     *
     * @param error 执行完毕时为 null，其余情况为 {@link Action#future()} 完成时的异常
     */
    record ActionCompleted(Action action, @Nullable Throwable error) implements Observation {
    }

    /**
     * bot 的实际暂停状态改变。
     */
    record MovementSuspensionChanged(boolean suspended) implements Observation {
    }

    /**
     * bot 实际的 tick 暂停状态改变。
     */
    record TickingSuspensionChanged(boolean suspended) implements Observation {
    }

    /**
     * 一次 tick 结束。
     *
     * @param tick bot 已经执行的 tick 数，从 1 开始
     */
    record BotTicked(long tick) implements Observation {
    }

    /**
     * 一轮调度中该 bot 的状态有变化时一条，在这一轮的最后投递。
     */
    record StateUpdated() implements Observation {
    }
}
