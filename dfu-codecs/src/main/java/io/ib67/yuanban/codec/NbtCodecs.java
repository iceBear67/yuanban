package io.ib67.yuanban.codec;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;

/**
 * 原版 NBT 相关的编解码器，结果是 adventure 的 {@link CompoundBinaryTag}。
 */
public final class NbtCodecs {
    /**
     * 原版 {@code ExtraCodecs.NBT}：把输入转换成 {@link NbtOps} 的标签，任何输入都可以转换。
     */
    public static final Codec<BinaryTag> NBT = ExtraCodecs.converter(NbtOps.INSTANCE);
    /**
     * 原版 {@code CompoundTag.CODEC}：输入按 {@link NbtOps} 转换（JSON 数字按 {@code JsonOps.convertTo} 的规则收窄），
     * 结果不是复合标签时为 {@code Not a compound tag: <tag>}，标签按 {@link StringTagVisitor} 打印。
     */
    public static final Codec<CompoundBinaryTag> COMPOUND_TAG = Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
        BinaryTag tag = dynamic.convert(NbtOps.INSTANCE).getValue();
        return tag instanceof CompoundBinaryTag compound
                ? DataResult.success(compound)
                : DataResult.error(() -> "Not a compound tag: " + StringTagVisitor.toString(tag));
    }, compound -> new Dynamic<>(NbtOps.INSTANCE, compound));
    /**
     * 原版 {@code TagParser.FLATTENED_CODEC}：SNBT 字符串，结果不是复合标签时为
     * {@code Expected compound tag, got <tag>}；语法错误为 {@link CommandSyntaxException#getMessage()}。
     * SNBT 的方言见 {@link TagParser}。
     */
    public static final Codec<CompoundBinaryTag> FLATTENED_COMPOUND_TAG = Codec.STRING.comapFlatMap(string -> {
        try {
            BinaryTag result = TagParser.parseFully(string);
            return result instanceof CompoundBinaryTag compound
                    ? DataResult.success(compound, Lifecycle.stable())
                    : DataResult.error(() -> "Expected compound tag, got " + StringTagVisitor.toString(result));
        } catch (CommandSyntaxException e) {
            return DataResult.error(e::getMessage);
        }
    }, StringTagVisitor::toString);
    /**
     * 原版 {@code TagParser.LENIENT_CODEC}：先按 SNBT 字符串解码，失败时按结构化数据解码（{@link #COMPOUND_TAG}）。
     */
    public static final Codec<CompoundBinaryTag> LENIENT_COMPOUND_TAG = Codec.withAlternative(FLATTENED_COMPOUND_TAG, COMPOUND_TAG);

    private NbtCodecs() {
    }
}
