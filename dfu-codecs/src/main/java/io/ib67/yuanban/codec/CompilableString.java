package io.ib67.yuanban.codec;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

/**
 * 原版 {@code net.minecraft.util.CompilableString}：保存原文和解析结果的字符串，相等只比较原文。
 *
 * @param source   原文
 * @param compiled 解析结果
 * @param <T>      解析结果的类型
 */
record CompilableString<T>(String source, T compiled) {
    /**
     * 原版 {@code CompilableString.CommandParserHelper.TRAILING_DATA}：{@code Trailing data found: <已解析>[<剩余>]}。
     */
    private static final DynamicCommandExceptionType TRAILING_DATA = CommandMessages.dynamic("command.trailing_data");

    /**
     * 原版 {@code CompilableString.codec(CommandParserHelper)}：整个字符串必须被 {@code parser} 读完，否则为
     * {@link #TRAILING_DATA}；解析错误由 {@code errorMessage} 转换成文本。
     */
    static <T> Codec<CompilableString<T>> codec(Parser<T> parser, BiFunction<String, CommandSyntaxException, String> errorMessage) {
        return Codec.STRING.comapFlatMap(contents -> compile(contents, parser, errorMessage), CompilableString::source);
    }

    /**
     * 原版 {@code CompilableString.CommandParserHelper.apply}：解析 {@code contents}。
     */
    static <T> DataResult<CompilableString<T>> compile(String contents, Parser<T> parser,
                                                       BiFunction<String, CommandSyntaxException, String> errorMessage) {
        var reader = new StringReader(contents);
        try {
            T result = parser.parse(reader);
            if (reader.canRead()) {
                String parsed = reader.getString().substring(0, reader.getCursor());
                String leftovers = reader.getString().substring(reader.getCursor());
                throw TRAILING_DATA.create(parsed + "[" + leftovers + "]");
            }
            return DataResult.success(new CompilableString<>(contents, result));
        } catch (CommandSyntaxException e) {
            return DataResult.error(() -> errorMessage.apply(contents, e));
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CompilableString<?> that && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return source;
    }

    /**
     * 原版 {@code CommandParserHelper.parse}。
     */
    @FunctionalInterface
    interface Parser<T> {
        T parse(StringReader reader) throws CommandSyntaxException;
    }
}
