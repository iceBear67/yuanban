package io.ib67.yuanban.bot.view;

import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.observe.WorldInfo;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 只读地访问 bot 在某一时刻的模型。方法在调度线程上执行，返回不可变的值。
 */
public interface StateReader {
    BotStatus status();

    /**
     * 自上次 Login (play) 或 Respawn 以来 bot 是否已经发送 Player Loaded
     */
    boolean loaded();

    /**
     * 实际的暂停状态：bot 自身或 {@code BotRuntime} 任意一级处于暂停状态
     */
    boolean movementSuspended();

    /**
     * bot 已经执行的 tick 数
     */
    long ticks();

    /**
     * 当前世界；不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）为 null
     */
    @Nullable WorldInfo world();

    /**
     * 本地玩家
     *
     * @throws IllegalStateException 不在任何世界中
     */
    PlayerSnapshot player();

    /**
     * 方块状态，不带方块实体的 NBT（见 {@link #blockEntity}）；维度高度范围之外为空气
     *
     * @throws io.ib67.yuanban.bot.ChunkNotLoadedException 所在区块没有加载
     * @throws IllegalStateException                       不在任何世界中
     */
    Block block(int x, int y, int z);

    default Block block(Point pos) {
        return block(pos.blockX(), pos.blockY(), pos.blockZ());
    }

    /**
     * 方块实体的 NBT（区块数据与 Block Entity Data 发给客户端的内容）；该位置没有方块实体时为 null
     *
     * @throws io.ib67.yuanban.bot.ChunkNotLoadedException 所在区块没有加载
     * @throws IllegalStateException                       不在任何世界中
     */
    @Nullable NbtMap blockEntity(int x, int y, int z);

    default @Nullable NbtMap blockEntity(Point pos) {
        return blockEntity(pos.blockX(), pos.blockY(), pos.blockZ());
    }

    /**
     * 区块是否已经加载；不在任何世界中时为 false
     */
    boolean chunkLoaded(int chunkX, int chunkZ);

    /**
     * 实体；不在模型中时为 null
     */
    @Nullable EntitySnapshot entity(int entityId);

    /**
     * 当前世界中的全部实体，按编号排列
     */
    List<EntitySnapshot> entities();

    /**
     * 窗口 0（玩家物品栏）
     */
    WindowSnapshot inventory();

    /**
     * 打开的窗口；没有打开窗口时为 null
     */
    @Nullable WindowSnapshot openWindow();

    /**
     * 当前窗口的光标物品；为空时为 null
     */
    @Nullable ItemStack cursor();

    /**
     * 本地玩家在窗口 0 中没有槽位的装备：身体护甲与鞍（Set Player Inventory Slot 的下标 41、42）；没有时为 null。
     * 其余装备都在窗口 0 中，见 {@link #inventory()}
     *
     * @param slot {@link EquipmentSlot#BODY} 或 {@link EquipmentSlot#SADDLE}
     * @throws IllegalArgumentException 其它装备槽位
     */
    @Nullable ItemStack equipment(EquipmentSlot slot);

    /**
     * 聊天日志，按收到顺序
     */
    List<ChatLine> chat();

    HudSnapshot hud();
}
