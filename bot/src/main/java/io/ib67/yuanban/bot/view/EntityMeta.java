package io.ib67.yuanban.bot.view;

/**
 * 实体元数据中的一个命名条目，常量见 {@link EntityMetaIndex}
 *
 * @param index        元数据的索引
 * @param defaultValue 没有收到这个条目时的值
 * @param mask         为 0 时条目的值就是索引处的值；不为 0 时索引处是一个字节，条目的值为其中这个位是否为 1（类型为 Boolean）
 */
public record EntityMeta<T>(int index, T defaultValue, byte mask) {
    public static <T> EntityMeta<T> index(int index, T defaultValue) {
        return new EntityMeta<>(index, defaultValue, (byte) 0);
    }

    public static EntityMeta<Boolean> bit(int index, int mask) {
        if (mask == 0) {
            throw new IllegalArgumentException("mask must not be zero");
        }
        return new EntityMeta<>(index, false, (byte) mask);
    }
}
