package io.ib67.yuanban.loot.function;

import net.kyori.adventure.key.Key;

/**
 * 旗帜图案一层：原版 {@code BannerPatternLayers.Layer}。JSON 字段 {@code pattern}（图案注册 ID）、
 * {@code color}（原版 {@code DyeColor} 名字，例如 {@code red}）。
 */
public record BannerLayer(Key pattern, String color) {
}
