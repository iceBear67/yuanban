package io.ib67.yuanban.loot.eval;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.ib67.yuanban.codec.NbtPath;
import io.ib67.yuanban.codec.NbtUtils;
import io.ib67.yuanban.loot.function.CopyOperation;
import io.ib67.yuanban.loot.function.MergeStrategy;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.NumberBinaryTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 按 NBT 路径读写标签：{@code storage} 数字提供器的取值，以及 {@code copy_custom_data} 的三种合并策略。
 * 路径的解析与匹配由 {@link NbtPath} 完成，这里只移植调用它的原版代码。
 */
final class NbtPaths {
    private NbtPaths() {
    }

    /**
     * 原版 {@code StorageValue#getNumericTag}：路径恰好匹配一个数值标签时返回它的值，否则返回 {@code defaultValue}。
     * 路径没有匹配时 {@link NbtPath#get} 抛出 {@link CommandSyntaxException}，原版在这里捕获它并返回默认值。
     */
    static Number numeric(NbtPath path, CompoundBinaryTag root, Number defaultValue) {
        List<BinaryTag> selected;
        try {
            selected = path.get(root);
        } catch (CommandSyntaxException nothingFound) {
            return defaultValue;
        }
        if (selected.size() == 1 && selected.getFirst() instanceof NumberBinaryTag number) {
            return boxed(number);
        }
        return defaultValue;
    }

    /**
     * 原版 {@code NumericTag#box}：按标签的类型取出对应的包装值，取整与取浮点数按该类型的规则进行。
     */
    private static Number boxed(NumberBinaryTag number) {
        return switch (number) {
            case net.kyori.adventure.nbt.ByteBinaryTag b -> b.value();
            case net.kyori.adventure.nbt.ShortBinaryTag s -> s.value();
            case net.kyori.adventure.nbt.IntBinaryTag i -> i.value();
            case net.kyori.adventure.nbt.LongBinaryTag l -> l.value();
            case net.kyori.adventure.nbt.FloatBinaryTag f -> f.value();
            case net.kyori.adventure.nbt.DoubleBinaryTag d -> d.value();
        };
    }

    /**
     * 原版 {@code CopyCustomDataFunction#run} 中对全部操作的处理：自定义数据的副本在第一个找到来源值的操作用到时才创建；
     * 没有任何操作用到它时返回 {@code null}，物品保持不变。
     *
     * @param current 物品当前的自定义数据，没有该组件时为空的复合标签
     * @return 修改之后的自定义数据；没有操作找到来源值时为 {@code null}
     */
    static @Nullable CompoundBinaryTag copy(List<CopyOperation> operations, BinaryTag source, CompoundBinaryTag current) {
        CompoundBinaryTag result = null;
        for (var operation : operations) {
            List<BinaryTag> sourceTags;
            try {
                sourceTags = operation.source().get(source);
            } catch (CommandSyntaxException nothingFound) {
                // 原版 CopyOperation#apply 捕获路径的异常，跳过这个操作
                continue;
            }
            if (sourceTags.isEmpty()) {
                continue;
            }
            var target = result == null ? current : result;
            result = target;
            try {
                result = (CompoundBinaryTag) merge(operation.op(), target, operation.target(), sourceTags);
            } catch (CommandSyntaxException failed) {
                // 原版在合并失败时同样跳过，已经取得的副本保留
            }
        }
        return result;
    }

    /**
     * 原版 {@code CopyCustomDataFunction$MergeStrategy#merge}。
     */
    private static BinaryTag merge(MergeStrategy strategy, CompoundBinaryTag target, NbtPath path, List<BinaryTag> sources)
            throws CommandSyntaxException {
        return switch (strategy) {
            case REPLACE -> path.set(target, sources.getLast()).root();
            case APPEND -> path.getOrCreate(target, ListBinaryTag::empty, existing -> {
                if (!(existing instanceof ListBinaryTag list)) {
                    return existing;
                }
                var builder = ListBinaryTag.heterogeneousListBinaryTag(list.size() + sources.size());
                list.forEach(builder::add);
                sources.forEach(builder::add);
                return builder.build();
            });
            case MERGE -> path.getOrCreate(target, CompoundBinaryTag::empty, existing -> {
                if (!(existing instanceof CompoundBinaryTag compound)) {
                    return existing;
                }
                var merged = compound;
                for (var source : sources) {
                    if (source instanceof CompoundBinaryTag sourceCompound) {
                        merged = NbtUtils.merge(merged, sourceCompound);
                    }
                }
                return merged;
            });
        };
    }
}
