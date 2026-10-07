package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;

/**
 * {@code set_stew_effect} 的一项效果：原版 {@code SetStewEffectFunction$EffectEntry}。
 * JSON 字段 {@code type}（药水效果 ID）、{@code duration}（数字提供器）。
 */
public record StewEffectEntry(Key effect, NumberProvider duration) {
}
