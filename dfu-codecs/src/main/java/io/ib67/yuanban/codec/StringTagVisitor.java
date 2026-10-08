package io.ib67.yuanban.codec;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.ByteArrayBinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.EndBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.LongArrayBinaryTag;
import net.kyori.adventure.nbt.LongBinaryTag;
import net.kyori.adventure.nbt.ShortBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 原版 {@code net.minecraft.nbt.StringTagVisitor}：原版 {@code Tag.toString()} 的 SNBT 形式。原版错误文本中的 NBT
 * 都按这个形式打印，例如 {@code Not a compound tag: [1,2]}。复合标签的键按字典顺序输出。
 */
public final class StringTagVisitor {
    private static final Pattern UNQUOTED_KEY_MATCH = Pattern.compile("[A-Za-z._]+[A-Za-z0-9._+-]*");
    /**
     * 原版 {@code SnbtGrammar.HEX_ESCAPE}。
     */
    private static final HexFormat HEX_ESCAPE = HexFormat.of().withUpperCase();

    private StringTagVisitor() {
    }

    /**
     * 原版 {@code Tag.toString()}。
     */
    public static String toString(BinaryTag tag) {
        var builder = new StringBuilder();
        visit(tag, builder);
        return builder.toString();
    }

    private static void visit(BinaryTag tag, StringBuilder builder) {
        switch (tag) {
            case StringBinaryTag string -> quoteAndEscape(string.value(), builder);
            case ByteBinaryTag value -> builder.append(value.value()).append('b');
            case ShortBinaryTag value -> builder.append(value.value()).append('s');
            case IntBinaryTag value -> builder.append(value.value());
            case LongBinaryTag value -> builder.append(value.value()).append('L');
            case FloatBinaryTag value -> builder.append(value.value()).append('f');
            case DoubleBinaryTag value -> builder.append(value.value()).append('d');
            case ByteArrayBinaryTag array -> {
                builder.append("[B;");
                byte[] data = array.value();
                for (int i = 0; i < data.length; i++) {
                    if (i != 0) {
                        builder.append(',');
                    }
                    builder.append(data[i]).append('B');
                }
                builder.append(']');
            }
            case IntArrayBinaryTag array -> {
                builder.append("[I;");
                int[] data = array.value();
                for (int i = 0; i < data.length; i++) {
                    if (i != 0) {
                        builder.append(',');
                    }
                    builder.append(data[i]);
                }
                builder.append(']');
            }
            case LongArrayBinaryTag array -> {
                builder.append("[L;");
                long[] data = array.value();
                for (int i = 0; i < data.length; i++) {
                    if (i != 0) {
                        builder.append(',');
                    }
                    builder.append(data[i]).append('L');
                }
                builder.append(']');
            }
            case ListBinaryTag list -> {
                builder.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i != 0) {
                        builder.append(',');
                    }
                    visit(list.get(i), builder);
                }
                builder.append(']');
            }
            case CompoundBinaryTag compound -> {
                builder.append('{');
                List<Map.Entry<String, ? extends BinaryTag>> entries = new ArrayList<>();
                compound.forEach(entries::add);
                entries.sort(Comparator.comparing(Map.Entry::getKey));
                for (int i = 0; i < entries.size(); i++) {
                    if (i != 0) {
                        builder.append(',');
                    }
                    handleKeyEscape(entries.get(i).getKey(), builder);
                    builder.append(':');
                    visit(entries.get(i).getValue(), builder);
                }
                builder.append('}');
            }
            case EndBinaryTag _ -> builder.append("END");
            default -> throw new IllegalArgumentException("Unknown tag type: " + tag.type());
        }
    }

    private static void handleKeyEscape(String key, StringBuilder builder) {
        if (!key.equalsIgnoreCase("true") && !key.equalsIgnoreCase("false") && UNQUOTED_KEY_MATCH.matcher(key).matches()) {
            builder.append(key);
        } else {
            quoteAndEscape(key, builder);
        }
    }

    /**
     * 原版 {@code StringTag.quoteAndEscape(String)}：优先使用双引号，字符串先出现双引号时改用单引号。
     */
    public static String quoteAndEscape(String input) {
        var builder = new StringBuilder();
        quoteAndEscape(input, builder);
        return builder.toString();
    }

    /**
     * 原版 {@code StringTag.quoteAndEscape(String, StringBuilder)}。
     */
    public static void quoteAndEscape(String input, StringBuilder result) {
        int quoteMarkIndex = result.length();
        result.append(' ');
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\') {
                result.append("\\\\");
            } else if (c == '"' || c == '\'') {
                if (quote == 0) {
                    quote = c == '"' ? '\'' : '"';
                }
                if (quote == c) {
                    result.append('\\');
                }
                result.append(c);
            } else {
                String escaped = escapeControlCharacters(c);
                if (escaped != null) {
                    result.append('\\');
                    result.append(escaped);
                } else {
                    result.append(c);
                }
            }
        }
        if (quote == 0) {
            quote = '"';
        }
        result.setCharAt(quoteMarkIndex, quote);
        result.append(quote);
    }

    /**
     * 原版 {@code SnbtGrammar.escapeControlCharacters}。
     */
    private static String escapeControlCharacters(char c) {
        return switch (c) {
            case '\b' -> "b";
            case '\t' -> "t";
            case '\n' -> "n";
            case '\f' -> "f";
            case '\r' -> "r";
            default -> c < ' ' ? "x" + HEX_ESCAPE.toHexDigits((byte) c) : null;
        };
    }
}
