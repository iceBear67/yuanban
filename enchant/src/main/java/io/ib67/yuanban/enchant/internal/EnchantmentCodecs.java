package io.ib67.yuanban.enchant.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.ib67.yuanban.enchant.EnchantmentMut;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.component.DataComponent;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryTranscoder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 附魔定义和附魔提供器的 JSON 解码。附魔按字段逐个解码，标签引用字段只取出 ID，
 * 存在性检查由订阅者在全部标签解码之后做；提供器按 {@code type} 字段走对应的原版结构。
 */
@ApiStatus.Internal
public final class EnchantmentCodecs {
    public static final String SINGLE = "single";
    public static final String BY_COST = "by_cost";
    public static final String BY_COST_WITH_DIFFICULTY = "by_cost_with_difficulty";
    private static final Set<String> PROVIDER_TYPES = Set.of(SINGLE, BY_COST, BY_COST_WITH_DIFFICULTY);

    /**
     * 原版 {@code EnchantmentDefinition.CODEC} 的范围检查：weight 在 [1, 1024]，max_level 在 [1, 255]，
     * anvil_cost 非负。Minestom 的编解码器没有编码这些范围，准备阶段单独检查。
     */
    private static final int MIN_WEIGHT = 1;
    private static final int MAX_WEIGHT = 1024;
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 255;

    private EnchantmentCodecs() {
    }

    /**
     * 判断 {@code type} 是否是三个原版提供器类型之一。
     */
    public static boolean isKnownProviderType(String type) {
        return PROVIDER_TYPES.contains(type);
    }

    /**
     * 解码一个附魔文件。标签引用字段在这里只取出 ID，存在性检查由订阅者在全部标签解码之后做。
     * 结构字段（description、weight、cost、slots）直接按字段名逐个解码，
     * 因为 {@link Enchantment#REGISTRY_CODEC} 会把 {@code supported_items} 等标签引用字段
     * 先解析成注册表标签对象，而本模块需要在这些字段存在性检查失败的时候先报具体字段名。
     */
    public static Result<EnchantmentMut> decodeEnchantment(Registries registries, JsonObject json) {
        var coder = new RegistryTranscoder<>(Transcoder.JSON, registries);

        var description = requiredField(json, "description", Codec.COMPONENT, coder);
        if (description instanceof Result.Error<Component> error) {
            return fieldError("description", error.message());
        }
        var supportedItems = readTagId(json, "supported_items");
        if (supportedItems == null) {
            return fieldError("supported_items", "expected a tag reference (#namespace:path)");
        }
        Key primaryItems = null;
        if (json.has("primary_items")) {
            primaryItems = readTagId(json, "primary_items");
            if (primaryItems == null) {
                return fieldError("primary_items", "expected a tag reference (#namespace:path)");
            }
        }
        Key exclusiveSet = null;
        if (json.has("exclusive_set")) {
            exclusiveSet = readTagId(json, "exclusive_set");
            if (exclusiveSet == null) {
                return fieldError("exclusive_set", "expected a tag reference (#namespace:path)");
            }
        }
        var weight = requiredField(json, "weight", Codec.INT, coder);
        if (weight instanceof Result.Error<Integer> error) {
            return fieldError("weight", error.message());
        }
        var weightValue = ((Result.Ok<Integer>) weight).value();
        if (weightValue < MIN_WEIGHT || weightValue > MAX_WEIGHT) {
            return fieldError("weight", "value must be in range [1, 1024]: " + weightValue);
        }
        var maxLevel = requiredField(json, "max_level", Codec.INT, coder);
        if (maxLevel instanceof Result.Error<Integer> error) {
            return fieldError("max_level", error.message());
        }
        var maxLevelValue = ((Result.Ok<Integer>) maxLevel).value();
        if (maxLevelValue < MIN_LEVEL || maxLevelValue > MAX_LEVEL) {
            return fieldError("max_level", "value must be in range [1, 255]: " + maxLevelValue);
        }
        var minCost = requiredField(json, "min_cost", Enchantment.Cost.CODEC, coder);
        if (minCost instanceof Result.Error<Enchantment.Cost> error) {
            return fieldError("min_cost", error.message());
        }
        var maxCost = requiredField(json, "max_cost", Enchantment.Cost.CODEC, coder);
        if (maxCost instanceof Result.Error<Enchantment.Cost> error) {
            return fieldError("max_cost", error.message());
        }
        var anvilCost = requiredField(json, "anvil_cost", Codec.INT, coder);
        if (anvilCost instanceof Result.Error<Integer> error) {
            return fieldError("anvil_cost", error.message());
        }
        var anvilCostValue = ((Result.Ok<Integer>) anvilCost).value();
        if (anvilCostValue < 0) {
            return fieldError("anvil_cost", "value must be non-negative: " + anvilCostValue);
        }
        var slots = requiredField(json, "slots", EquipmentSlotGroup.CODEC.list(), coder);
        if (slots instanceof Result.Error<List<EquipmentSlotGroup>> error) {
            return fieldError("slots", error.message());
        }
        var effects = readEffects(registries, json);
        if (effects instanceof Result.Error<List<Map.Entry<DataComponent<?>, Object>>> error) {
            return new Result.Error<>(error.message());
        }
        return new Result.Ok<>(new EnchantmentMut(
                ((Result.Ok<Component>) description).value(),
                supportedItems, primaryItems, exclusiveSet,
                weightValue, maxLevelValue,
                ((Result.Ok<Enchantment.Cost>) minCost).value(),
                ((Result.Ok<Enchantment.Cost>) maxCost).value(),
                anvilCostValue,
                ((Result.Ok<List<EquipmentSlotGroup>>) slots).value(),
                ((Result.Ok<List<Map.Entry<DataComponent<?>, Object>>>) effects).value()));
    }

    /**
     * 按类型检查一个附魔提供器文件的结构是否合法。
     * 提供器定义存有它引用的附魔 ID 集合，由订阅者检查存在性。
     */
    public static Result<Void> validateProvider(Registries registries, String type, JsonObject definition) {
        var codec = switch (type) {
            case SINGLE -> StructCodec.struct(
                    "enchantment", Codec.KEY, ignored -> null,
                    "level", Codec.INT, ignored -> null,
                    (enchantment, level) -> Boolean.TRUE);
            case BY_COST -> StructCodec.struct(
                    "enchantments", Codec.STRING, ignored -> null,
                    "cost", Codec.INT, ignored -> null,
                    (enchantments, cost) -> Boolean.TRUE);
            case BY_COST_WITH_DIFFICULTY -> StructCodec.struct(
                    "enchantments", Codec.STRING, ignored -> null,
                    "min_cost", Codec.INT, ignored -> null,
                    "max_cost_span", Codec.INT, ignored -> null,
                    (enchantments, minCost, maxCostSpan) -> Boolean.TRUE);
            default -> null;
        };
        if (codec == null) {
            return new Result.Error<>("Unknown enchantment provider type: " + type);
        }
        var decoded = codec.decodeFromMap(new RegistryTranscoder<>(Transcoder.JSON, registries), mapOf(definition));
        if (decoded instanceof Result.Error<?> error) {
            return new Result.Error<>(error.message());
        }
        return new Result.Ok<>(null);
    }

    /**
     * {@code single} 的 {@code enchantment} 字段引用的附魔 ID。
     */
    public static @Nullable Key referencedEnchantment(JsonObject definition) {
        var element = definition.get("enchantment");
        return element == null ? null : Key.key(element.getAsString());
    }

    /**
     * {@code by_cost*} 的 {@code enchantments} 字段引用的附魔标签 ID；不是 {@code #tag} 形式时返回 {@code null}。
     */
    public static @Nullable Key referencedEnchantmentTag(JsonObject definition) {
        var element = definition.get("enchantments");
        if (element == null || element.isJsonArray()) {
            return null;
        }
        var reference = element.getAsString();
        return reference.startsWith("#") ? Key.key(reference.substring(1)) : null;
    }

    /**
     * 读取 {@code field} 字段的字符串值，剥掉 {@code #} 之后解析为 ID。格式不正确的时候返回 {@code null}。
     */
    private static @Nullable Key readTagId(JsonObject object, String field) {
        if (!(object.get(field) instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return null;
        }
        var value = primitive.getAsString();
        if (!value.startsWith("#")) {
            return null;
        }
        try {
            return Key.key(value.substring(1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Result<List<Map.Entry<DataComponent<?>, Object>>> readEffects(Registries registries, JsonObject object) {
        if (!object.has("effects")) {
            return new Result.Ok<>(List.of());
        }
        if (!(object.get("effects") instanceof JsonObject effects)) {
            return new Result.Error<>("effects: Not an object: " + object.get("effects"));
        }
        var coder = new RegistryTranscoder<>(Transcoder.JSON, registries);
        var result = new ArrayList<Map.Entry<DataComponent<?>, Object>>(effects.size());
        for (var entry : effects.entrySet()) {
            Key key;
            try {
                key = Key.key(entry.getKey());
            } catch (IllegalArgumentException e) {
                return new Result.Error<>("Unknown enchantment effect component: " + entry.getKey());
            }
            var component = EffectComponent.fromKey(key);
            if (component == null) {
                return new Result.Error<>("Unknown enchantment effect component: " + entry.getKey());
            }
            var decoded = ((DataComponent<Object>) component).decode(coder, entry.getValue());
            switch (decoded) {
                case Result.Ok<Object> ok -> result.add(new AbstractMap.SimpleImmutableEntry<>(component, ok.value()));
                case Result.Error<Object> error -> {
                    return new Result.Error<>(entry.getKey() + ": " + error.message());
                }
            }
        }
        return new Result.Ok<>(List.copyOf(result));
    }

    /**
     * 把现成的 {@link JsonObject} 交给 StructCodec 按字段解码。
     */
    private static Transcoder.MapLike<JsonElement> mapOf(JsonObject object) {
        return new Transcoder.MapLike<>() {
            @Override
            public Set<String> keys() {
                return object.keySet();
            }

            @Override
            public boolean hasValue(String key) {
                return object.has(key);
            }

            @Override
            public Result<JsonElement> getValue(String key) {
                var value = object.get(key);
                return value == null ? new Result.Error<>("No such key: " + key) : new Result.Ok<>(value);
            }

            @Override
            public int size() {
                return object.size();
            }
        };
    }

    /**
     * 从 JSON 对象中取一个必需字段并按编解码器解码。字段不存在或者不匹配的时候返回错误。
     */
    private static <T> Result<T> requiredField(JsonObject object, String field, Codec<T> codec,
                                               RegistryTranscoder<JsonElement> coder) {
        var value = object.get(field);
        if (value == null) {
            return new Result.Error<>("No value for field: " + field);
        }
        return codec.decode(coder, value);
    }

    private static <T> Result<T> fieldError(String field, String message) {
        return new Result.Error<>(field + ": " + message);
    }
}
