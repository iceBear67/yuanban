package io.ib67.yuanban.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 按 DataFixerUpper {@code JsonOps}（非压缩）的规则读取 JSON 的 {@link Transcoder}，供 {@link MinestomCodecs}
 * 适配的 Minestom 编解码器使用。除下面几处之外都转交给 {@link Transcoder#JSON}：
 * <ul>
 *   <li>{@link #getString} 只接受 JSON 字符串，{@link #getBoolean} 只接受 JSON 布尔值；
 *   Minestom 的版本接受任意原始值，并把非布尔值按 {@link Boolean#parseBoolean} 读取。</li>
 *   <li>值为 JSON {@code null} 的字段按字段缺失处理，与 {@code JsonOps} 的 {@code MapLike} 相同。</li>
 *   <li>{@link #convertTo} 按 {@code JsonOps.convertTo} 把整数收窄到 byte、short、int、long，把小数收窄到
 *   float、double；Minestom 的版本把全部数字转换成 double。</li>
 * </ul>
 */
public final class StrictJsonTranscoder implements Transcoder<JsonElement> {
    public static final StrictJsonTranscoder INSTANCE = new StrictJsonTranscoder();

    private StrictJsonTranscoder() {
    }

    @Override
    public JsonElement createNull() {
        return JSON.createNull();
    }

    @Override
    public Result<Boolean> getBoolean(JsonElement value) {
        if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return new Result.Ok<>(primitive.getAsBoolean());
        }
        return new Result.Error<>("Not a boolean: " + value);
    }

    @Override
    public JsonElement createBoolean(boolean value) {
        return JSON.createBoolean(value);
    }

    @Override
    public Result<Byte> getByte(JsonElement value) {
        return JSON.getByte(value);
    }

    @Override
    public JsonElement createByte(byte value) {
        return JSON.createByte(value);
    }

    @Override
    public Result<Short> getShort(JsonElement value) {
        return JSON.getShort(value);
    }

    @Override
    public JsonElement createShort(short value) {
        return JSON.createShort(value);
    }

    @Override
    public Result<Integer> getInt(JsonElement value) {
        return JSON.getInt(value);
    }

    @Override
    public JsonElement createInt(int value) {
        return JSON.createInt(value);
    }

    @Override
    public Result<Long> getLong(JsonElement value) {
        return JSON.getLong(value);
    }

    @Override
    public JsonElement createLong(long value) {
        return JSON.createLong(value);
    }

    @Override
    public Result<Float> getFloat(JsonElement value) {
        return JSON.getFloat(value);
    }

    @Override
    public JsonElement createFloat(float value) {
        return JSON.createFloat(value);
    }

    @Override
    public Result<Double> getDouble(JsonElement value) {
        return JSON.getDouble(value);
    }

    @Override
    public JsonElement createDouble(double value) {
        return JSON.createDouble(value);
    }

    @Override
    public Result<Number> getNumber(JsonElement value) {
        return JSON.getNumber(value);
    }

    @Override
    public JsonElement createNumber(Number value) {
        return JSON.createNumber(value);
    }

    @Override
    public Result<String> getString(JsonElement value) {
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            return new Result.Ok<>(primitive.getAsString());
        }
        return new Result.Error<>("Not a string: " + value);
    }

    @Override
    public JsonElement createString(String value) {
        return JSON.createString(value);
    }

    @Override
    public Result<List<JsonElement>> getList(JsonElement value) {
        return JSON.getList(value);
    }

    @Override
    public JsonElement emptyList() {
        return JSON.emptyList();
    }

    @Override
    public ListBuilder<JsonElement> createList(int expectedSize) {
        return JSON.createList(expectedSize);
    }

    @Override
    public Result<MapLike<JsonElement>> getMap(JsonElement value) {
        if (!(value instanceof JsonObject object)) {
            return new Result.Error<>("Not an object: " + value);
        }
        return new Result.Ok<>(new MapLike<>() {
            @Override
            public Collection<String> keys() {
                return object.entrySet().stream()
                        .filter(entry -> !entry.getValue().isJsonNull())
                        .map(Map.Entry::getKey)
                        .toList();
            }

            @Override
            public boolean hasValue(String key) {
                var element = object.get(key);
                return element != null && !element.isJsonNull();
            }

            @Override
            public Result<JsonElement> getValue(String key) {
                var element = object.get(key);
                if (element == null || element.isJsonNull()) {
                    return new Result.Error<>("No such key: " + key);
                }
                return new Result.Ok<>(element);
            }
        });
    }

    @Override
    public JsonElement emptyMap() {
        return JSON.emptyMap();
    }

    @Override
    public MapBuilder<JsonElement> createMap() {
        return JSON.createMap();
    }

    @Override
    public <O> Result<O> convertTo(Transcoder<O> coder, JsonElement value) {
        return switch (value) {
            case JsonObject object -> {
                final MapBuilder<O> map = coder.createMap();
                for (var entry : object.entrySet()) {
                    switch (convertTo(coder, entry.getValue())) {
                        case Result.Ok<O>(O data) -> map.put(coder.createString(entry.getKey()), data);
                        case Result.Error<O>(String message) -> {
                            yield new Result.Error<>(entry.getKey() + ": " + message);
                        }
                    }
                }
                yield new Result.Ok<>(map.build());
            }
            case JsonArray array -> {
                if (array.isEmpty()) {
                    yield new Result.Ok<>(coder.emptyList());
                }
                final ListBuilder<O> list = coder.createList(array.size());
                for (int i = 0; i < array.size(); i++) {
                    switch (convertTo(coder, array.get(i))) {
                        case Result.Ok<O>(O data) -> list.add(data);
                        case Result.Error<O>(String message) -> {
                            final int index = i;
                            yield new Result.Error<>(index + ": " + message);
                        }
                    }
                }
                yield new Result.Ok<>(list.build());
            }
            case JsonNull _ -> new Result.Ok<>(coder.createNull());
            case JsonPrimitive primitive when primitive.isString() -> new Result.Ok<>(coder.createString(primitive.getAsString()));
            case JsonPrimitive primitive when primitive.isBoolean() -> new Result.Ok<>(coder.createBoolean(primitive.getAsBoolean()));
            case JsonPrimitive primitive -> new Result.Ok<>(convertNumber(coder, primitive.getAsBigDecimal()));
            default -> new Result.Error<>("Unknown JSON type: " + value);
        };
    }

    /**
     * DataFixerUpper {@code JsonOps.convertTo} 对数字的处理。
     */
    private static <O> O convertNumber(Transcoder<O> coder, BigDecimal value) {
        try {
            final long l = value.longValueExact();
            if ((byte) l == l) {
                return coder.createByte((byte) l);
            }
            if ((short) l == l) {
                return coder.createShort((short) l);
            }
            if ((int) l == l) {
                return coder.createInt((int) l);
            }
            return coder.createLong(l);
        } catch (ArithmeticException e) {
            final double d = value.doubleValue();
            if ((float) d == d) {
                return coder.createFloat((float) d);
            }
            return coder.createDouble(d);
        }
    }
}
