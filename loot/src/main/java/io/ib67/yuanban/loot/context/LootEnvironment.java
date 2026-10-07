package io.ib67.yuanban.loot.context;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * 求值期的服务端环境。宿主按自己的运行环境给出实现；{@code LootSelector} 只在取用声明了
 * {@code random_sequence} 的表时读取 {@link #worldSeed()}，其余方法按求值步骤在用到时调用。
 */
public interface LootEnvironment {
    ServerProcess process();

    /**
     * 记分板的只读视图，{@code score} 数字提供器与 {@code entity_scores} 条件使用。
     */
    ScoreboardView scoreboards();

    /**
     * 命令存储的只读视图，{@code storage} 数字提供器使用。
     */
    StorageView storage();

    /**
     * 按原版 {@code LootParams$Builder#getLuck} 的规则计算一次取用的运气值：玩家运气属性
     * （{@code minecraft:luck}），在钓鱼（{@code contextType} 为 {@code minecraft:fishing}）
     * 时再加上钓竿的 {@code fishing_luck_bonus}。其中玩家、工具均可为空；{@code contextType}
     * 是表的 {@code type}，缺省时为 {@code minecraft:generic}。
     */
    float luckOf(@Nullable Player player, @Nullable ItemStack tool, Key contextType);

    /**
     * 动态掉落（{@code dynamic} 条目）的取用来源：原版把 {@code contents} / {@code sherds}
     * 委托给 {@code LootParams$DynamicDrop}，本模块由宿主直接产出物品流。
     */
    Stream<ItemStack> dynamicLoot(Key name);

    /**
     * 查找 {@code item_modifier} 对应的物品修饰器，供 {@code reference} 修饰器求值使用
     * （DESIGN 8.5）。原版找不到时记录日志后按恒等处理。
     */
    Optional<LootFunction> findFunction(Key id);

    /**
     * 查找 {@code predicate} 对应的条件，供 {@code reference} 条件求值使用（DESIGN 8.5）。
     */
    Optional<LootCondition> findCondition(Key id);

    /**
     * 世界种子：原版从 {@code ServerLevel#getSeed} 拿出，用来配合 {@code random_sequence}
     * 经 {@code RandomSequence#seedForKey}（{@code RandomSupport.seedFromHashOf} 对序列 ID
     * 求散列后与种子异或）派生表的稳定随机序列。Minestom 26.2 的 {@code Instance} 只保存
     * 哈希后的种子且不对外开放，不可能从 {@code ServerProcess} 取回，因此由宿主显式提供。
     */
    long worldSeed();
}
