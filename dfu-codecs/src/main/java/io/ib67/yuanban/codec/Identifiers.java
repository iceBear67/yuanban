package io.ib67.yuanban.codec;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.kyori.adventure.key.Key;

/**
 * 原版 {@code net.minecraft.resources.Identifier} 的解析规则和编解码器，结果类型是 adventure 的 {@link Key}。
 * 字符规则与原版相同：命名空间 {@code [a-z0-9_.-]}（不能是 {@code ..}），路径 {@code [a-z0-9/._-]}；
 * 省略命名空间或者以 {@code :} 开头时命名空间为 {@code minecraft}。
 */
public final class Identifiers {
    /**
     * 原版 {@code Identifier.CODEC}。
     */
    public static final Codec<Key> CODEC = Codec.STRING.comapFlatMap(Identifiers::read, Key::asString).stable();
    /**
     * 原版 {@code Identifier.ERROR_INVALID}：{@code Invalid ID}。
     */
    private static final SimpleCommandExceptionType ERROR_INVALID = CommandMessages.simple("argument.id.invalid");

    private Identifiers() {
    }

    /**
     * 原版 {@code Identifier.read(String)}：解析失败时返回
     * {@code Not a valid resource location: <input> <原因>}。
     */
    public static DataResult<Key> read(String input) {
        try {
            return DataResult.success(parse(input));
        } catch (IdentifierException e) {
            return DataResult.error(() -> "Not a valid resource location: " + input + " " + e.getMessage());
        }
    }

    /**
     * 原版 {@code Identifier.parse(String)}：按第一个 {@code :} 分开命名空间和路径。
     *
     * @throws IdentifierException 命名空间或路径含有不允许的字符
     */
    public static Key parse(String identifier) {
        int separator = identifier.indexOf(':');
        if (separator < 0) {
            return Key.key(Key.MINECRAFT_NAMESPACE, assertValidPath(Key.MINECRAFT_NAMESPACE, identifier));
        }
        var path = identifier.substring(separator + 1);
        if (separator == 0) {
            return Key.key(Key.MINECRAFT_NAMESPACE, assertValidPath(Key.MINECRAFT_NAMESPACE, path));
        }
        var namespace = identifier.substring(0, separator);
        return Key.key(assertValidNamespace(namespace, path), assertValidPath(namespace, path));
    }

    /**
     * 原版 {@code Identifier.read(StringReader)}：读取 {@link #isAllowedInIdentifier} 允许的连续字符并解析；
     * 不合法时读取器回到开始的位置，错误为 {@code Invalid ID}。
     */
    public static Key read(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowedInIdentifier(reader.peek())) {
            reader.skip();
        }
        String text = reader.getString().substring(start, reader.getCursor());
        try {
            return parse(text);
        } catch (IdentifierException e) {
            reader.setCursor(start);
            throw ERROR_INVALID.createWithContext(reader);
        }
    }

    /**
     * 原版 {@code Identifier.isAllowedInIdentifier}。
     */
    public static boolean isAllowedInIdentifier(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c == '_' || c == ':' || c == '/' || c == '.' || c == '-';
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (!validPathChar(path.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidNamespace(String namespace) {
        if (namespace.equals("..")) {
            return false;
        }
        for (int i = 0; i < namespace.length(); i++) {
            if (!validNamespaceChar(namespace.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String assertValidNamespace(String namespace, String path) {
        if (!isValidNamespace(namespace)) {
            throw new IdentifierException("Non [a-z0-9_.-] character in namespace of identifier: " + namespace + ":" + path);
        }
        return namespace;
    }

    private static String assertValidPath(String namespace, String path) {
        if (!isValidPath(path)) {
            throw new IdentifierException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + path);
        }
        return path;
    }

    private static boolean validPathChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.';
    }

    private static boolean validNamespaceChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.';
    }

    /**
     * 原版 {@code net.minecraft.IdentifierException}。
     */
    public static final class IdentifierException extends RuntimeException {
        public IdentifierException(String message) {
            super(message);
        }
    }
}
