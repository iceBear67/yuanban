package io.ib67.yuanban.loot.predicate.component;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.loot.predicate.CollectionPredicate;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponent;
import net.minestom.server.entity.VillagerType;
import net.minestom.server.instance.block.jukebox.JukeboxSong;
import net.minestom.server.item.armor.TrimMaterial;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.potion.PotionType;
import net.minestom.server.utils.Range;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 组件谓词，对应原版 {@code net.minecraft.core.component.predicates.DataComponentPredicate}。
 * <p>
 * 子类型与原版 {@code data_component_predicate_type} 注册表的 15 种类型一一对应，另有 {@link AnyValue}：
 * 原版 {@code predicates} 字段的键也可以是任意组件类型 ID，表示只检查组件存在。
 * 本模块不使用 Minestom 的 {@code DataComponentPredicates}：它在解码时就把标签绑定到 Minestom 现有的标签对象，
 * 并且 {@code attribute_modifiers} 的 {@code attribute}、{@code villager/variant} 和组件缺失时的
 * {@code custom_data} 与原版不同。
 */
public sealed interface ComponentPredicate {

    /**
     * 原版 {@code AnyValue}：组件存在即匹配。JSON 中以组件类型 ID 作键，值是空对象。
     */
    record AnyValue(DataComponent<?> component) implements ComponentPredicate {
        public AnyValue {
            Objects.requireNonNull(component, "component");
        }
    }

    /**
     * 原版 {@code damage}：{@code durability}（剩余耐久）与 {@code damage}（已损耗）。
     */
    record Damage(Range.Int durability, Range.Int damage) implements ComponentPredicate {
        public Damage {
            Objects.requireNonNull(durability, "durability");
            Objects.requireNonNull(damage, "damage");
        }
    }

    /**
     * 原版 {@code enchantments}：每个附魔谓词都要被物品的 {@code enchantments} 组件满足。
     */
    record Enchantments(List<EnchantmentPredicate> enchantments) implements ComponentPredicate {
        public Enchantments {
            enchantments = List.copyOf(enchantments);
        }
    }

    /**
     * 原版 {@code stored_enchantments}：与 {@link Enchantments} 相同，检查 {@code stored_enchantments} 组件。
     */
    record StoredEnchantments(List<EnchantmentPredicate> enchantments) implements ComponentPredicate {
        public StoredEnchantments {
            enchantments = List.copyOf(enchantments);
        }
    }

    /**
     * 原版 {@code potion_contents}：药水在集合中。
     */
    record Potions(HolderSet<PotionType> potions) implements ComponentPredicate {
        public Potions {
            Objects.requireNonNull(potions, "potions");
        }
    }

    /**
     * 原版 {@code custom_data}：自定义数据按原版 {@code NbtUtils.compareNbt} 部分匹配，组件缺失时按空复合标签比较。
     */
    record CustomData(CompoundBinaryTag tag) implements ComponentPredicate {
        public CustomData {
            Objects.requireNonNull(tag, "tag");
        }
    }

    /**
     * 原版 {@code container}：{@code items} 检查容器中不为空的物品。
     */
    record Container(Optional<CollectionPredicate<ItemPredicate>> items) implements ComponentPredicate {
        public Container {
            Objects.requireNonNull(items, "items");
        }
    }

    /**
     * 原版 {@code bundle_contents}：{@code items} 检查收纳袋中的物品。
     */
    record BundleContents(Optional<CollectionPredicate<ItemPredicate>> items) implements ComponentPredicate {
        public BundleContents {
            Objects.requireNonNull(items, "items");
        }
    }

    /**
     * 原版 {@code firework_explosion}：检查烟花之星的爆炸效果。
     */
    record FireworkExplosion(FireworkPredicate predicate) implements ComponentPredicate {
        public FireworkExplosion {
            Objects.requireNonNull(predicate, "predicate");
        }
    }

    /**
     * 原版 {@code fireworks}：{@code explosions} 与 {@code flight_duration}。
     */
    record Fireworks(Optional<CollectionPredicate<FireworkPredicate>> explosions, Range.Int flightDuration)
            implements ComponentPredicate {
        public Fireworks {
            Objects.requireNonNull(explosions, "explosions");
            Objects.requireNonNull(flightDuration, "flightDuration");
        }
    }

    /**
     * 原版 {@code writable_book_content}：{@code pages} 的元素是页面原文，与书页的原始文本比较。
     */
    record WritableBook(Optional<CollectionPredicate<String>> pages) implements ComponentPredicate {
        public WritableBook {
            Objects.requireNonNull(pages, "pages");
        }
    }

    /**
     * 原版 {@code written_book_content}：书页比较原始文本，另有作者、标题原文、代数和是否已解析。
     */
    record WrittenBook(Optional<CollectionPredicate<Component>> pages, Optional<String> author, Optional<String> title,
                       Range.Int generation, Optional<Boolean> resolved) implements ComponentPredicate {
        public WrittenBook {
            Objects.requireNonNull(pages, "pages");
            Objects.requireNonNull(author, "author");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(generation, "generation");
            Objects.requireNonNull(resolved, "resolved");
        }
    }

    /**
     * 原版 {@code attribute_modifiers}：{@code modifiers} 检查属性修饰符列表。
     */
    record AttributeModifiers(Optional<CollectionPredicate<AttributeEntryPredicate>> modifiers)
            implements ComponentPredicate {
        public AttributeModifiers {
            Objects.requireNonNull(modifiers, "modifiers");
        }
    }

    /**
     * 原版 {@code trim}：纹饰材料与纹饰样式。
     */
    record Trim(Optional<HolderSet<TrimMaterial>> material, Optional<HolderSet<TrimPattern>> pattern)
            implements ComponentPredicate {
        public Trim {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(pattern, "pattern");
        }
    }

    /**
     * 原版 {@code jukebox_playable}：唱片的曲目。
     */
    record JukeboxPlayable(Optional<HolderSet<JukeboxSong>> song) implements ComponentPredicate {
        public JukeboxPlayable {
            Objects.requireNonNull(song, "song");
        }
    }

    /**
     * 原版 {@code villager/variant}：村民类型在集合中。
     */
    record VillagerVariant(HolderSet<VillagerType> villagerTypes) implements ComponentPredicate {
        public VillagerVariant {
            Objects.requireNonNull(villagerTypes, "villagerTypes");
        }
    }
}
