package io.ib67.yuanban.enchant.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.ib67.yuanban.enchant.EnchantmentMut;
import io.ib67.yuanban.enchant.ProviderEntry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
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
import java.util.function.BiFunction;

/**
 * 附魔定义和附魔提供器的 JSON 解码。附魔按字段逐个解码，标签引用字段只取出 ID，
 * 存在性检查由订阅者在全部标签解码之后做；提供器按 {@code type} 字段走对应的原版结构。
 */
@ApiStatus.Internal
public final class EnchantmentCodecs {
    public static final String SINGLE = "single";
    public static final String BY_COST = "by_cost";
    public static final String BY_COST_WITH_DIFFICULTY = "by_cost_with_difficulty";

    /**
     * 原版 {@code EnchantmentDefinition.CODEC} 的范围检查：weight 在 [1, 1024]，max_level 在 [1, 255]，
     * anvil_cost 非负。Minestom 的编解码器没有编码这些范围，准备阶段单独检查。
     */
    private static final int MIN_WEIGHT = 1;
    private static final int MAX_WEIGHT = 1024;
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 255;

    /**
     * 原版 {@code EnchantmentProviderByCostWithDifficulty} 的范围检查：
     * {@code min_cost} 用 {@code intRange(1, 10000)}，{@code max_cost_span} 用 {@code intRange(0, 10000)}。
     */
    private static final int MIN_PROVIDER_COST = 1;
    private static final int MAX_PROVIDER_COST = 10000;
    private static final int MAX_PROVIDER_COST_SPAN = 10000;

    /**
     * 整数提供器对象的 {@code type} 字段目前只有 {@code minecraft:uniform} 一种取值。
     */
    private static final String UNIFORM = "uniform";

    private EnchantmentCodecs() {
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
     * 按类型解码一个附魔提供器文件为 {@link ProviderEntry}。
     * 类型分派、字段解码、范围校验（{@code min_cost} 在 [1, 10000]、{@code max_cost_span} 在 [0, 10000]）
     * 和引用信息提取都在这里完成；引用存在性检查由订阅者通过
     * {@link ProviderEntry#referencedEnchantment()} 和 {@link ProviderEntry#referencedEnchantmentTag()} 做。
     */
    public static Result<ProviderEntry> decodeProvider(Registries registries, JsonObject definition) {
        var coder = new RegistryTranscoder<>(Transcoder.JSON, registries);
        var typeElement = definition.get("type");
        if (typeElement == null) {
            return new Result.Error<>("Missing type, expected to find a string");
        }
        if (!(typeElement instanceof JsonPrimitive typePrimitive) || !typePrimitive.isString()) {
            return new Result.Error<>("Invalid type: " + typeElement);
        }
        String typeValue;
        try {
            typeValue = Key.key(typePrimitive.getAsString()).value();
        } catch (IllegalArgumentException e) {
            return new Result.Error<>("Invalid type: " + typeElement);
        }
        return switch (typeValue) {
            case SINGLE -> {
                var enchantment = requiredField(definition, "enchantment", Codec.KEY, coder);
                if (enchantment instanceof Result.Error<Key> error) {
                    yield fieldError("enchantment", error.message());
                }
                var level = requiredField(definition, "level", coder, EnchantmentCodecs::decodeIntProvider);
                if (level instanceof Result.Error<ProviderEntry.IntProvider> error) {
                    yield fieldError("level", error.message());
                }
                yield new Result.Ok<>(new ProviderEntry.Single(
                        ((Result.Ok<Key>) enchantment).value(),
                        ((Result.Ok<ProviderEntry.IntProvider>) level).value()));
            }
            case BY_COST -> {
                var enchantments = decodeEnchantmentTagReference(definition);
                if (enchantments instanceof Result.Error<Key> error) {
                    yield fieldError("enchantments", error.message());
                }
                var cost = requiredField(definition, "cost", coder, EnchantmentCodecs::decodeIntProvider);
                if (cost instanceof Result.Error<ProviderEntry.IntProvider> error) {
                    yield fieldError("cost", error.message());
                }
                yield new Result.Ok<>(new ProviderEntry.ByCost(
                        ((Result.Ok<Key>) enchantments).value(),
                        ((Result.Ok<ProviderEntry.IntProvider>) cost).value()));
            }
            case BY_COST_WITH_DIFFICULTY -> {
                var enchantments = decodeEnchantmentTagReference(definition);
                if (enchantments instanceof Result.Error<Key> error) {
                    yield fieldError("enchantments", error.message());
                }
                var minCost = requiredField(definition, "min_cost", Codec.INT, coder);
                if (minCost instanceof Result.Error<Integer> error) {
                    yield fieldError("min_cost", error.message());
                }
                var minCostValue = ((Result.Ok<Integer>) minCost).value();
                if (minCostValue < MIN_PROVIDER_COST || minCostValue > MAX_PROVIDER_COST) {
                    yield fieldError("min_cost", "value must be in range [1, 10000]: " + minCostValue);
                }
                var maxCostSpan = requiredField(definition, "max_cost_span", Codec.INT, coder);
                if (maxCostSpan instanceof Result.Error<Integer> error) {
                    yield fieldError("max_cost_span", error.message());
                }
                var maxCostSpanValue = ((Result.Ok<Integer>) maxCostSpan).value();
                if (maxCostSpanValue < 0 || maxCostSpanValue > MAX_PROVIDER_COST_SPAN) {
                    yield fieldError("max_cost_span", "value must be in range [0, 10000]: " + maxCostSpanValue);
                }
                yield new Result.Ok<>(new ProviderEntry.ByCostWithDifficulty(
                        ((Result.Ok<Key>) enchantments).value(), minCostValue, maxCostSpanValue));
            }
            default -> new Result.Error<>("Unknown enchantment provider type: " + typeValue);
        };
    }

    /**
     * 解码 {@code by_cost*} 的 {@code enchantments} 字段：附魔标签引用（{@code #namespace:path}），
     * 返回去掉 {@code #} 之后的标签 ID。
     */
    private static Result<Key> decodeEnchantmentTagReference(JsonObject definition) {
        var element = definition.get("enchantments");
        if (element == null) {
            return new Result.Error<>("No value for field: enchantments");
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return new Result.Error<>("expected a tag reference (#namespace:path)");
        }
        var reference = primitive.getAsString();
        if (!reference.startsWith("#")) {
            return new Result.Error<>("expected a tag reference (#namespace:path)");
        }
        try {
            return new Result.Ok<>(Key.key(reference.substring(1)));
        } catch (IllegalArgumentException e) {
            return new Result.Error<>("Not a valid tag id: " + reference);
        }
    }

    /**
     * 解码一个整数提供器：整数字面量解码为常量；对象按 {@code type} 字段分派，
     * 目前只有 {@code minecraft:uniform}（{@code min_inclusive} / {@code max_inclusive}）。
     */
    private static Result<ProviderEntry.IntProvider> decodeIntProvider(RegistryTranscoder<JsonElement> coder, JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            try {
                return new Result.Ok<>(new ProviderEntry.IntProvider.ConstantInt(primitive.getAsInt()));
            } catch (NumberFormatException e) {
                return new Result.Error<>("Not an integer: " + element);
            }
        }
        if (element instanceof JsonObject object) {
            var typeElement = object.get("type");
            if (typeElement == null) {
                return new Result.Error<>("Missing type, expected to find a string");
            }
            String type;
            try {
                type = Key.key(typeElement.getAsString()).value();
            } catch (IllegalArgumentException | IllegalStateException e) {
                return new Result.Error<>("Invalid type: " + typeElement);
            }
            if (!UNIFORM.equals(type)) {
                return new Result.Error<>("Unknown int provider type: " + type);
            }
            var minInclusive = requiredField(object, "min_inclusive", Codec.INT, coder);
            if (minInclusive instanceof Result.Error<Integer> error) {
                return new Result.Error<>("min_inclusive: " + error.message());
            }
            var maxInclusive = requiredField(object, "max_inclusive", Codec.INT, coder);
            if (maxInclusive instanceof Result.Error<Integer> error) {
                return new Result.Error<>("max_inclusive: " + error.message());
            }
            return new Result.Ok<>(new ProviderEntry.IntProvider.UniformInt(
                    ((Result.Ok<Integer>) minInclusive).value(),
                    ((Result.Ok<Integer>) maxInclusive).value()));
        }
        return new Result.Error<>("Not an integer or an object: " + element);
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
     * 从 JSON 对象中取一个必需字段并按解码函数解码。字段不存在或者不匹配的时候返回错误。
     */
    private static <T> Result<T> requiredField(JsonObject object, String field,
                                               RegistryTranscoder<JsonElement> coder,
                                               BiFunction<RegistryTranscoder<JsonElement>, JsonElement, Result<T>> decoder) {
        var value = object.get(field);
        if (value == null) {
            return new Result.Error<>("No value for field: " + field);
        }
        return decoder.apply(coder, value);
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
