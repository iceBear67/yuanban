package io.ib67.yuanban.codec;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;

import java.io.IOException;

/**
 * 原版 {@code net.minecraft.nbt.TagParser}：解析 SNBT。
 * <p>
 * 原版 26.2 用自己的 {@code SnbtGrammar} 解析；这里用 adventure 的 {@link TagStringIO}（接受元素类型不同的列表，
 * 不接受 1.13 之前的旧格式）。两者的方言不同：
 * <ul>
 *   <li>原版的内置运算 {@code bool(..)}、{@code uuid(..)}，以及引号字符串中的十六进制转义和 Unicode 转义，
 *   adventure 不支持，解析失败或者得到不同的结果；</li>
 *   <li>原版不允许无引号字符串以数字、正负号或小数点开头，adventure 把这样的内容读作字符串；</li>
 *   <li>语法错误的文本来自 adventure，不是原版的文本，错误位置统一报告为开始解析的位置。</li>
 * </ul>
 * 原版在解析之后进行的检查（结果不是复合标签、文末有多余内容）使用原版的错误文本和位置。
 */
public final class TagParser {
    /**
     * 原版 {@code TagParser.ERROR_TRAILING_DATA}。
     */
    public static final SimpleCommandExceptionType ERROR_TRAILING_DATA = CommandMessages.simple("argument.nbt.trailing");
    /**
     * 原版 {@code TagParser.ERROR_EXPECTED_COMPOUND}。
     */
    public static final SimpleCommandExceptionType ERROR_EXPECTED_COMPOUND = CommandMessages.simple("argument.nbt.expected.compound");
    private static final TagStringIO SNBT = TagStringIO.builder()
            .acceptHeterogeneousLists(true)
            .acceptLegacy(false)
            .build();

    private TagParser() {
    }

    /**
     * 原版 {@code TagParser.parseFully(String)}：整个字符串是一个标签，之后只能有空白。
     */
    public static BinaryTag parseFully(String input) throws CommandSyntaxException {
        return parseFully(new StringReader(input));
    }

    /**
     * 原版 {@code TagParser.parseFully(StringReader)}。
     */
    public static BinaryTag parseFully(StringReader reader) throws CommandSyntaxException {
        BinaryTag result = parseAsArgument(reader);
        reader.skipWhitespace();
        if (reader.canRead()) {
            throw ERROR_TRAILING_DATA.createWithContext(reader);
        }
        return result;
    }

    /**
     * 原版 {@code TagParser.parseCompoundFully}。
     */
    public static CompoundBinaryTag parseCompoundFully(String input) throws CommandSyntaxException {
        var reader = new StringReader(input);
        return castToCompoundOrThrow(reader, parseFully(reader));
    }

    /**
     * 原版 {@code TagParser.parseAsArgument}：从 {@code reader} 的当前位置读取一个标签，读取器停在标签之后。
     */
    public static BinaryTag parseAsArgument(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String remaining = reader.getRemaining();
        var rest = new StringBuilder();
        BinaryTag result;
        try {
            result = SNBT.asTag(remaining, rest);
        } catch (IOException e) {
            String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new CommandSyntaxException(new SimpleCommandExceptionType(new LiteralMessage(message)),
                    new LiteralMessage(message), reader.getString(), start);
        }
        reader.setCursor(start + remaining.length() - rest.length());
        return result;
    }

    /**
     * 原版 {@code TagParser.parseCompoundAsArgument}：读取一个标签，它必须是复合标签。
     */
    public static CompoundBinaryTag parseCompoundAsArgument(StringReader reader) throws CommandSyntaxException {
        BinaryTag result = parseAsArgument(reader);
        return castToCompoundOrThrow(reader, result);
    }

    private static CompoundBinaryTag castToCompoundOrThrow(StringReader reader, BinaryTag result) throws CommandSyntaxException {
        if (result instanceof CompoundBinaryTag compound) {
            return compound;
        }
        throw ERROR_EXPECTED_COMPOUND.createWithContext(reader);
    }
}
