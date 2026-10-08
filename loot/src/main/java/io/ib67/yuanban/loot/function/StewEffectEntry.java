package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.number.NumberProvider;
import net.minestom.server.potion.PotionEffect;

import java.util.Objects;

/**
 * {@code set_stew_effect} 的一项效果：原版 {@code SetStewEffectFunction$EffectEntry}。
 * JSON 字段 {@code type}（状态效果 ID，按状态效果注册表解码）、{@code duration}（数字提供器，单位是秒，
 * 瞬间效果按刻）。
 */
public record StewEffectEntry(PotionEffect effect, NumberProvider duration) {
    public StewEffectEntry {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(duration, "duration");
    }
}
