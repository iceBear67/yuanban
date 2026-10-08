package io.ib67.yuanban.bot.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Equippable;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.HolderSet;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.PiercingWeapon;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ToolData;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.UseEffects;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.Sound;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * 物品的编号与默认组件，取自 {@code item.json}。默认组件只转换本库读取的四种：{@code tool}、{@code equippable}、
 * {@code use_effects}、{@code piercing_weapon}。JSON 是原版编解码器的格式，取默认值的字段被省略，转换时按原版编解码器的默认值补上。
 */
public final class ItemData {
    private static final Map<String, BuiltinSound> SOUNDS = Arrays.stream(BuiltinSound.values())
            .collect(Collectors.toUnmodifiableMap(BuiltinSound::getName, Function.identity()));
    private static final Map<Key, Integer> IDS = new HashMap<>();
    private static final ToolData[] TOOLS;
    private static final Equippable[] EQUIPPABLES;
    private static final UseEffects[] USE_EFFECTS;
    private static final PiercingWeapon[] PIERCING_WEAPONS;

    static {
        var json = MinestomDataFiles.read("item.json");
        var count = json.size();
        TOOLS = new ToolData[count];
        EQUIPPABLES = new Equippable[count];
        USE_EFFECTS = new UseEffects[count];
        PIERCING_WEAPONS = new PiercingWeapon[count];
        for (var item : json.entrySet()) {
            var entry = item.getValue().getAsJsonObject();
            var id = entry.get("id").getAsInt();
            if (id < 0 || id >= count || IDS.containsValue(id)) {
                throw new IllegalStateException("item " + item.getKey() + " has an invalid id " + id + " in item.json");
            }
            IDS.put(Key.key(item.getKey()), id);
            var components = entry.getAsJsonObject("components");
            TOOLS[id] = convert(components, "minecraft:tool", ItemData::tool);
            EQUIPPABLES[id] = convert(components, "minecraft:equippable", ItemData::equippable);
            USE_EFFECTS[id] = convert(components, "minecraft:use_effects", ItemData::useEffects);
            PIERCING_WEAPONS[id] = convert(components, "minecraft:piercing_weapon", ItemData::piercingWeapon);
        }
    }

    private ItemData() {
    }

    /**
     * @throws IllegalArgumentException 没有这个物品
     */
    public static int id(Key name) {
        var id = IDS.get(name);
        if (id == null) {
            throw new IllegalArgumentException("unknown item " + name);
        }
        return id;
    }

    /**
     * 物品的默认组件
     *
     * @return 物品没有这个默认组件时为 null
     * @throws IllegalArgumentException 不是本类转换的四种组件之一，或者没有这个物品编号
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T prototype(int itemId, DataComponentType<T> type) {
        if (itemId < 0 || itemId >= TOOLS.length) {
            throw new IllegalArgumentException("unknown item id " + itemId);
        }
        if (type == DataComponentTypes.TOOL) {
            return (T) TOOLS[itemId];
        }
        if (type == DataComponentTypes.EQUIPPABLE) {
            return (T) EQUIPPABLES[itemId];
        }
        if (type == DataComponentTypes.USE_EFFECTS) {
            return (T) USE_EFFECTS[itemId];
        }
        if (type == DataComponentTypes.PIERCING_WEAPON) {
            return (T) PIERCING_WEAPONS[itemId];
        }
        throw new IllegalArgumentException("default component " + type + " is not loaded");
    }

    private static <T> @Nullable T convert(JsonObject components, String name, Function<JsonObject, T> converter) {
        var json = components.get(name);
        return json == null ? null : converter.apply(json.getAsJsonObject());
    }

    /**
     * 原版 {@code Tool.CODEC}：{@code default_mining_speed} 默认 1，{@code damage_per_block} 默认 1，
     * {@code can_destroy_blocks_in_creative} 默认 true；规则的 {@code speed} 与 {@code correct_for_drops} 可以省略
     */
    private static ToolData tool(JsonObject json) {
        var rules = new ArrayList<ToolData.Rule>();
        for (var element : json.getAsJsonArray("rules")) {
            var rule = element.getAsJsonObject();
            rules.add(new ToolData.Rule(holderSet(rule.get("blocks"), ItemData::blockId),
                    rule.has("speed") ? rule.get("speed").getAsFloat() : null,
                    rule.has("correct_for_drops") ? rule.get("correct_for_drops").getAsBoolean() : null));
        }
        return new ToolData(rules, floatOr(json, "default_mining_speed", 1.0F), intOr(json, "damage_per_block", 1),
                booleanOr(json, "can_destroy_blocks_in_creative", true));
    }

    /**
     * 原版 {@code Equippable.CODEC}：{@code equip_sound} 默认 {@code item.armor.equip_generic}，{@code dispensable}、
     * {@code swappable}、{@code damage_on_hurt} 默认 true，{@code equip_on_interact}、{@code can_be_sheared} 默认 false，
     * {@code shearing_sound} 默认 {@code item.shears.snip}；{@code asset_id}（MCProtocolLib 中的 model）、
     * {@code camera_overlay}、{@code allowed_entities} 可以省略
     */
    private static Equippable equippable(JsonObject json) {
        return new Equippable(slot(json.get("slot").getAsString()),
                json.has("equip_sound") ? sound(json.get("equip_sound")) : BuiltinSound.ITEM_ARMOR_EQUIP_GENERIC,
                json.has("asset_id") ? Key.key(json.get("asset_id").getAsString()) : null,
                json.has("camera_overlay") ? Key.key(json.get("camera_overlay").getAsString()) : null,
                json.has("allowed_entities") ? holderSet(json.get("allowed_entities"), ItemData::entityTypeId) : null,
                booleanOr(json, "dispensable", true), booleanOr(json, "swappable", true),
                booleanOr(json, "damage_on_hurt", true), booleanOr(json, "equip_on_interact", false),
                booleanOr(json, "can_be_sheared", false),
                json.has("shearing_sound") ? sound(json.get("shearing_sound")) : BuiltinSound.ITEM_SHEARS_SNIP);
    }

    /**
     * 原版 {@code UseEffects.CODEC}：默认为 {@code (false, true, 0.2F)}
     */
    private static UseEffects useEffects(JsonObject json) {
        return new UseEffects(booleanOr(json, "can_sprint", false), booleanOr(json, "interact_vibrations", true),
                floatOr(json, "speed_multiplier", 0.2F));
    }

    /**
     * 原版 {@code PiercingWeapon.CODEC}：{@code deals_knockback} 默认 true，{@code dismounts} 默认 false，两个声音可以省略
     */
    private static PiercingWeapon piercingWeapon(JsonObject json) {
        return new PiercingWeapon(booleanOr(json, "deals_knockback", true), booleanOr(json, "dismounts", false),
                json.has("sound") ? sound(json.get("sound")) : null,
                json.has("hit_sound") ? sound(json.get("hit_sound")) : null);
    }

    /**
     * 原版 {@code HolderSet} 的编解码器：{@code "#标签"}、单个名称或名称列表
     */
    private static HolderSet holderSet(JsonElement json, ToIntFunction<Key> ids) {
        if (json.isJsonArray()) {
            var holders = new IntArrayList();
            for (var element : json.getAsJsonArray()) {
                holders.add(ids.applyAsInt(Key.key(element.getAsString())));
            }
            return new HolderSet(holders);
        }
        var text = json.getAsString();
        if (text.startsWith("#")) {
            return new HolderSet(Key.key(text.substring(1)));
        }
        return new HolderSet(IntList.of(ids.applyAsInt(Key.key(text))));
    }

    private static int blockId(Key name) {
        return Objects.requireNonNull(Block.fromKey(name), () -> "unknown block " + name).id();
    }

    /**
     * MCProtocolLib {@link EntityType} 的序号即注册表编号，常量名的小写形式即名称
     */
    private static int entityTypeId(Key name) {
        if (!name.namespace().equals(Key.MINECRAFT_NAMESPACE)) {
            throw new IllegalStateException("unknown entity type " + name);
        }
        return EntityType.valueOf(name.value().toUpperCase(Locale.ROOT)).ordinal();
    }

    /**
     * 原版 {@code EquipmentSlot} 的序列化名称
     */
    private static EquipmentSlot slot(String name) {
        return switch (name) {
            case "mainhand" -> EquipmentSlot.MAIN_HAND;
            case "offhand" -> EquipmentSlot.OFF_HAND;
            case "feet" -> EquipmentSlot.BOOTS;
            case "legs" -> EquipmentSlot.LEGGINGS;
            case "chest" -> EquipmentSlot.CHESTPLATE;
            case "head" -> EquipmentSlot.HELMET;
            case "body" -> EquipmentSlot.BODY;
            case "saddle" -> EquipmentSlot.SADDLE;
            default -> throw new IllegalStateException("unknown equipment slot " + name);
        };
    }

    /**
     * 原版的声音（{@code minecraft} 命名空间）。默认组件中的声音都是注册表中的条目，以名称给出
     */
    private static Sound sound(JsonElement json) {
        var key = Key.key(json.getAsString());
        var sound = key.namespace().equals(Key.MINECRAFT_NAMESPACE) ? SOUNDS.get(key.value()) : null;
        if (sound == null) {
            throw new IllegalStateException("unknown sound " + key);
        }
        return sound;
    }

    private static boolean booleanOr(JsonObject json, String field, boolean fallback) {
        return json.has(field) ? json.get(field).getAsBoolean() : fallback;
    }

    private static float floatOr(JsonObject json, String field, float fallback) {
        return json.has(field) ? json.get(field).getAsFloat() : fallback;
    }

    private static int intOr(JsonObject json, String field, int fallback) {
        return json.has(field) ? json.get(field).getAsInt() : fallback;
    }
}
