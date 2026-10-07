package io.ib67.yuanban.loot.number;

/**
 * 常量数字提供器：原版 {@code providers.number.ConstantValue}，JSON 字段 {@code value}；
 * 裸数字在解析时归约为本类型。
 */
public record ConstantValue(float value) implements NumberProvider {
    public static final ConstantValue ZERO = new ConstantValue(0.0f);

    public static ConstantValue of(float value) {
        return value == 0.0f ? ZERO : new ConstantValue(value);
    }
}
