package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.codec.NbtUtils;
import io.ib67.yuanban.loot.predicate.CollectionPredicate;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.component.AttributeEntryPredicate;
import io.ib67.yuanban.loot.predicate.component.ComponentPredicate;
import io.ib67.yuanban.loot.predicate.component.EnchantmentPredicate;
import io.ib67.yuanban.loot.predicate.component.FireworkPredicate;
import net.kyori.adventure.text.Component;
import net.minestom.server.ServerProcess;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.book.FilteredText;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.registry.Holder;
import net.minestom.server.registry.Registry;
import net.minestom.server.registry.RegistryKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * 原版 {@code DataComponentMatchers} 与各个 {@code DataComponentPredicate} 的求值，用于物品和实体的组件。
 */
final class ComponentPredicates {
    private ComponentPredicates() {
    }

    /**
     * 原版 {@code DataComponentMatchers#test}：先比较精确值（{@code DataComponentExactPredicate}），再逐个检查部分谓词。
     */
    static boolean test(ComponentMatchers matchers, DataComponent.Holder holder, ServerProcess process) {
        for (var expected : matchers.exact().entrySet()) {
            if (!Objects.equals(expected.value(), holder.get(expected.component()))) {
                return false;
            }
        }
        return testPartial(matchers.partial(), holder, process);
    }

    /**
     * 原版 {@code EntityPartialComponentsPredicate#matches} 与 {@code DataComponentMatchers} 的部分谓词：全部满足时通过。
     */
    static boolean testPartial(List<ComponentPredicate> predicates, DataComponent.Holder holder, ServerProcess process) {
        for (var predicate : predicates) {
            if (!matches(predicate, holder, process)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 原版各个 {@code DataComponentPredicate#matches}。除 {@code AnyValue}、{@code damage}、{@code custom_data} 外，
     * 都是原版 {@code SingleComponentItemPredicate}：组件不存在时不通过。
     */
    static boolean matches(ComponentPredicate predicate, DataComponent.Holder holder, ServerProcess process) {
        return switch (predicate) {
            case ComponentPredicate.AnyValue any -> holder.get(any.component()) != null;
            case ComponentPredicate.Damage damage -> {
                var value = holder.get(DataComponents.DAMAGE);
                if (value == null) {
                    yield false;
                }
                int maxDamage = holder.get(DataComponents.MAX_DAMAGE, 0);
                yield damage.durability().inRange(maxDamage - value) && damage.damage().inRange(value);
            }
            case ComponentPredicate.Enchantments enchantments -> {
                var value = holder.get(DataComponents.ENCHANTMENTS);
                yield value != null && containedInAll(enchantments.enchantments(), value, process);
            }
            case ComponentPredicate.StoredEnchantments enchantments -> {
                var value = holder.get(DataComponents.STORED_ENCHANTMENTS);
                yield value != null && containedInAll(enchantments.enchantments(), value, process);
            }
            case ComponentPredicate.Potions potions -> {
                var value = holder.get(DataComponents.POTION_CONTENTS);
                yield value != null && value.potion() != null
                        && Holders.contains(potions.potions(), value.potion().key(), process.potionType());
            }
            case ComponentPredicate.CustomData customData -> {
                // 原版 NbtPredicate#matches(DataComponentGetter)：组件不存在时按空的复合标签比较
                var value = holder.get(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                yield NbtUtils.compareNbt(customData.tag(), value.nbt(), true);
            }
            case ComponentPredicate.Container container -> {
                var value = holder.get(DataComponents.CONTAINER);
                yield value != null && (container.items().isEmpty()
                        || collection(container.items().get(), nonEmpty(value), (p, item) -> ItemPredicates.test(p, item, process)));
            }
            case ComponentPredicate.BundleContents bundle -> {
                var value = holder.get(DataComponents.BUNDLE_CONTENTS);
                yield value != null && (bundle.items().isEmpty()
                        || collection(bundle.items().get(), nonEmpty(value), (p, item) -> ItemPredicates.test(p, item, process)));
            }
            case ComponentPredicate.FireworkExplosion explosion -> {
                var value = holder.get(DataComponents.FIREWORK_EXPLOSION);
                yield value != null && test(explosion.predicate(), value);
            }
            case ComponentPredicate.Fireworks fireworks -> {
                var value = holder.get(DataComponents.FIREWORKS);
                if (value == null) {
                    yield false;
                }
                if (fireworks.explosions().isPresent()
                        && !collection(fireworks.explosions().get(), value.explosions(), ComponentPredicates::test)) {
                    yield false;
                }
                yield fireworks.flightDuration().inRange(value.flightDuration());
            }
            case ComponentPredicate.WritableBook book -> {
                var value = holder.get(DataComponents.WRITABLE_BOOK_CONTENT);
                yield value != null && (book.pages().isEmpty()
                        || collection(book.pages().get(), value.pages(), (String page, FilteredText<String> text) -> text.text().equals(page)));
            }
            case ComponentPredicate.WrittenBook book -> {
                var value = holder.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (value == null) {
                    yield false;
                }
                if (book.author().isPresent() && !book.author().get().equals(value.author())) {
                    yield false;
                }
                if (book.title().isPresent() && !book.title().get().equals(value.title().text())) {
                    yield false;
                }
                if (!book.generation().inRange(value.generation())) {
                    yield false;
                }
                if (book.resolved().isPresent() && book.resolved().get() != value.resolved()) {
                    yield false;
                }
                yield book.pages().isEmpty()
                        || collection(book.pages().get(), value.pages(), (Component page, FilteredText<Component> text) -> text.text().equals(page));
            }
            case ComponentPredicate.AttributeModifiers modifiers -> {
                var value = holder.get(DataComponents.ATTRIBUTE_MODIFIERS);
                yield value != null && (modifiers.modifiers().isEmpty()
                        || collection(modifiers.modifiers().get(), value.modifiers(), ComponentPredicates::test));
            }
            case ComponentPredicate.Trim trim -> {
                var value = holder.get(DataComponents.TRIM);
                if (value == null) {
                    yield false;
                }
                if (trim.material().isPresent() && !holderIn(trim.material().get(), value.material(), process.trimMaterial())) {
                    yield false;
                }
                yield trim.pattern().isEmpty() || holderIn(trim.pattern().get(), value.pattern(), process.trimPattern());
            }
            case ComponentPredicate.JukeboxPlayable jukebox -> {
                var value = holder.get(DataComponents.JUKEBOX_PLAYABLE);
                yield value != null && (jukebox.song().isEmpty()
                        || Holders.contains(jukebox.song().get(), value.key(), process.jukeboxSong()));
            }
            case ComponentPredicate.VillagerVariant variant -> {
                var value = holder.get(DataComponents.VILLAGER_VARIANT);
                yield value != null && Holders.containsWithoutTags(variant.villagerTypes(), value.key(), "minecraft:villager_type");
            }
        };
    }

    /**
     * 原版 {@code HolderSet#contains(Holder)}：内联的值不属于任何列表或标签。
     */
    private static <T> boolean holderIn(io.ib67.yuanban.codec.HolderSet<T> set, Holder<T> holder, Registry<T> registry) {
        return !holder.isDirect() && Holders.contains(set, holder.asKey().key(), registry);
    }

    private static List<ItemStack> nonEmpty(List<ItemStack> items) {
        var result = new ArrayList<ItemStack>(items.size());
        for (var item : items) {
            if (!item.isAir()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 原版 {@code EnchantmentsPredicate#matches}：每个附魔谓词都满足。
     */
    private static boolean containedInAll(List<EnchantmentPredicate> predicates, EnchantmentList enchantments, ServerProcess process) {
        for (var predicate : predicates) {
            if (!containedIn(predicate, enchantments, process)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 原版 {@code EnchantmentPredicate#containedIn}。原版用引用比较判断等级范围是否为缺省的 {@code ANY}，
     * 显式写出的空范围 {@code {}} 不等于缺省值，走按等级比较的分支。
     */
    static boolean containedIn(EnchantmentPredicate predicate, EnchantmentList enchantments, ServerProcess process) {
        if (predicate.enchantments().isPresent()) {
            for (RegistryKey<net.minestom.server.item.enchant.Enchantment> enchantment
                    : Holders.members(predicate.enchantments().get(), process.enchantment())) {
                int level = enchantments.level(enchantment);
                if (level != 0 && (predicate.levels() == MinMaxBounds.ANY_INT || predicate.levels().inRange(level))) {
                    return true;
                }
            }
            return false;
        }
        if (predicate.levels() != MinMaxBounds.ANY_INT) {
            for (int level : enchantments.enchantments().values()) {
                if (predicate.levels().inRange(level)) {
                    return true;
                }
            }
            return false;
        }
        return !enchantments.enchantments().isEmpty();
    }

    /**
     * 原版 {@code FireworkExplosionPredicate$FireworkPredicate#test}。
     */
    private static boolean test(FireworkPredicate predicate, FireworkExplosion explosion) {
        if (predicate.shape().isPresent() && predicate.shape().get() != explosion.shape()) {
            return false;
        }
        if (predicate.twinkle().isPresent() && predicate.twinkle().get() != explosion.hasTwinkle()) {
            return false;
        }
        return predicate.trail().isEmpty() || predicate.trail().get() == explosion.hasTrail();
    }

    /**
     * 原版 {@code AttributeModifiersPredicate$EntryPredicate#test}。属性没有 Minestom 注册表，标签形式无法解析。
     */
    private static boolean test(AttributeEntryPredicate predicate, AttributeList.Modifier modifier) {
        if (predicate.attribute().isPresent()
                && !Holders.containsWithoutTags(predicate.attribute().get(), modifier.attribute().key(), "minecraft:attribute")) {
            return false;
        }
        if (predicate.id().isPresent() && !predicate.id().get().equals(modifier.modifier().id())) {
            return false;
        }
        if (!LootEvaluation.matches(predicate.amount(), modifier.modifier().amount())) {
            return false;
        }
        if (predicate.operation().isPresent() && predicate.operation().get() != modifier.modifier().operation()) {
            return false;
        }
        return predicate.slot().isEmpty() || predicate.slot().get() == modifier.slot();
    }

    /**
     * 原版 {@code CollectionPredicate#test}：{@code contains}、{@code count}、{@code size} 依次检查。
     */
    static <P, T> boolean collection(CollectionPredicate<P> predicate, List<T> values, BiPredicate<P, T> test) {
        if (predicate.contains().isPresent() && !contains(predicate.contains().get(), values, test)) {
            return false;
        }
        if (predicate.count().isPresent()) {
            for (var entry : predicate.count().get()) {
                int count = 0;
                for (var value : values) {
                    if (test.test(entry.test(), value)) {
                        count++;
                    }
                }
                if (!entry.count().inRange(count)) {
                    return false;
                }
            }
        }
        return predicate.size().isEmpty() || predicate.size().get().inRange(values.size());
    }

    /**
     * 原版 {@code CollectionContentsPredicate}：没有元素谓词时通过；一个时有任意值满足即通过；
     * 多个时按值的顺序逐个划掉被满足的谓词，全部划掉时通过。
     */
    private static <P, T> boolean contains(List<P> tests, List<T> values, BiPredicate<P, T> test) {
        if (tests.isEmpty()) {
            return true;
        }
        var remaining = new ArrayList<>(tests);
        for (var value : values) {
            remaining.removeIf(p -> test.test(p, value));
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
