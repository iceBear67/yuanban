package io.ib67.yuanban.bot.chat;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * 一条玩家聊天的签名（256 字节）。按内容比较
 */
public record Signature(byte[] bytes) {
    public Signature {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Signature(byte[] other) && Arrays.equals(bytes, other);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public @NotNull String toString() {
        return "Signature[" + HexFormat.of().formatHex(bytes) + "]";
    }
}
