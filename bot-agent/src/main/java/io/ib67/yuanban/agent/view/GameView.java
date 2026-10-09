package io.ib67.yuanban.agent.view;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 工具、条件与 observer 结论读取的游戏状态：实时模式下由 bot 的模型转换，重放模式下来自 fixture。
 * 实现只在一次读取中使用，返回的都是不可变的值。
 */
public interface GameView {
    BotStatusView status();

    /**
     * 不在任何世界中时为 null
     */
    @Nullable PlayerView player();

    /**
     * 不在任何世界中时为 null
     */
    @Nullable WorldStatusView world();

    /**
     * @throws io.ib67.yuanban.bot.ChunkNotLoadedException 所在区块没有加载
     * @throws IllegalStateException                       不在任何世界中
     */
    BlockView block(BlockPos pos);

    boolean chunkLoaded(int chunkX, int chunkZ);

    /**
     * 当前世界中的全部实体（不含本地玩家），按与本地玩家的距离从近到远排列
     */
    List<EntityView> entities();

    @Nullable EntityView entity(int id);

    InventoryView inventory();

    /**
     * 最近的 limit 条聊天，按收到顺序
     */
    List<ChatView> chat(int limit);
}
