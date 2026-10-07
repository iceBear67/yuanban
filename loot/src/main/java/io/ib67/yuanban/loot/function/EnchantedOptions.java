package io.ib67.yuanban.loot.function;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 魔咒配置来源：原版 {@code HolderSet<Enchantment>}，可以是魔咒 ID 列表或者是以 {@code #} 前缀的
 * 魔咒标签。本模块按原样承载。
 */
public record EnchantedOptions(@Nullable List<Key> enchantments,
                               @Nullable Key enchantmentTag) {
    public EnchantedOptions {
        if (enchantments != null) enchantments = List.copyOf(enchantments);
    }

    public static EnchantedOptions ofList(List<Key> enchantments) {
        return new EnchantedOptions(enchantments, null);
    }

    public static EnchantedOptions ofTag(Key enchantmentTag) {
        return new EnchantedOptions(null, enchantmentTag);
    }
}
