package io.ib67.yuanban.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.ByteArrayBinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.EndBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.LongArrayBinaryTag;
import net.kyori.adventure.nbt.LongBinaryTag;
import net.kyori.adventure.nbt.ShortBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * 原版 {@code net.minecraft.nbt.NbtOps}：以 adventure 的 {@link BinaryTag} 为数据的 {@link DynamicOps}，
 * 错误文本与原版相同，其中的 NBT 按 {@link StringTagVisitor} 打印。
 * <p>
 * 与原版的差别全部来自 adventure 的标签不可修改：
 * <ul>
 *   <li>原版的列表允许元素类型不同；这里用 {@link ListBinaryTag#heterogeneousListBinaryTag()} 构造列表，
 *   同样允许元素类型不同，内容与原版相同。</li>
 *   <li>原版的复合标签以 {@link java.util.HashMap} 保存，adventure 的构造器同样使用 {@link java.util.HashMap}，
 *   但两者扩容的过程不同，元素较多时遍历顺序可能与原版不同。只有把复合标签按顺序转换到别的格式
 *   （例如 JSON 对象的键顺序）时才能观察到这一点。</li>
 * </ul>
 */
public final class NbtOps implements DynamicOps<BinaryTag> {
    public static final NbtOps INSTANCE = new NbtOps();

    private NbtOps() {
    }

    @Override
    public BinaryTag empty() {
        return EndBinaryTag.endBinaryTag();
    }

    @Override
    public BinaryTag emptyList() {
        return ListBinaryTag.empty();
    }

    @Override
    public BinaryTag emptyMap() {
        return CompoundBinaryTag.empty();
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, BinaryTag input) {
        return switch (input) {
            case EndBinaryTag _ -> outOps.empty();
            case ByteBinaryTag tag -> outOps.createByte(tag.value());
            case ShortBinaryTag tag -> outOps.createShort(tag.value());
            case IntBinaryTag tag -> outOps.createInt(tag.value());
            case LongBinaryTag tag -> outOps.createLong(tag.value());
            case FloatBinaryTag tag -> outOps.createFloat(tag.value());
            case DoubleBinaryTag tag -> outOps.createDouble(tag.value());
            case ByteArrayBinaryTag tag -> outOps.createByteList(ByteBuffer.wrap(tag.value()));
            case StringBinaryTag tag -> outOps.createString(tag.value());
            case ListBinaryTag _ -> convertList(outOps, input);
            case CompoundBinaryTag _ -> convertMap(outOps, input);
            case IntArrayBinaryTag tag -> outOps.createIntList(Arrays.stream(tag.value()));
            case LongArrayBinaryTag tag -> outOps.createLongList(Arrays.stream(tag.value()));
            default -> throw new IllegalArgumentException("Unknown tag type: " + input.type());
        };
    }

    /**
     * 原版 {@code Tag.asNumber()}：数值标签按自身类型装箱。
     */
    @Override
    public DataResult<Number> getNumberValue(BinaryTag input) {
        Number number = switch (input) {
            case ByteBinaryTag tag -> tag.value();
            case ShortBinaryTag tag -> tag.value();
            case IntBinaryTag tag -> tag.value();
            case LongBinaryTag tag -> tag.value();
            case FloatBinaryTag tag -> tag.value();
            case DoubleBinaryTag tag -> tag.value();
            default -> null;
        };
        return number != null ? DataResult.success(number) : DataResult.error(() -> "Not a number");
    }

    @Override
    public BinaryTag createNumeric(Number i) {
        return DoubleBinaryTag.doubleBinaryTag(i.doubleValue());
    }

    @Override
    public BinaryTag createByte(byte value) {
        return ByteBinaryTag.byteBinaryTag(value);
    }

    @Override
    public BinaryTag createShort(short value) {
        return ShortBinaryTag.shortBinaryTag(value);
    }

    @Override
    public BinaryTag createInt(int value) {
        return IntBinaryTag.intBinaryTag(value);
    }

    @Override
    public BinaryTag createLong(long value) {
        return LongBinaryTag.longBinaryTag(value);
    }

    @Override
    public BinaryTag createFloat(float value) {
        return FloatBinaryTag.floatBinaryTag(value);
    }

    @Override
    public BinaryTag createDouble(double value) {
        return DoubleBinaryTag.doubleBinaryTag(value);
    }

    /**
     * 原版：数值不为 0 时为 {@code true}，其它标签不是布尔值。
     */
    @Override
    public DataResult<Boolean> getBooleanValue(BinaryTag input) {
        return getNumberValue(input).map(number -> number.doubleValue() != 0.0);
    }

    @Override
    public BinaryTag createBoolean(boolean value) {
        return value ? ByteBinaryTag.ONE : ByteBinaryTag.ZERO;
    }

    @Override
    public DataResult<String> getStringValue(BinaryTag input) {
        if (input instanceof StringBinaryTag string) {
            return DataResult.success(string.value());
        }
        return DataResult.error(() -> "Not a string");
    }

    @Override
    public BinaryTag createString(String value) {
        return StringBinaryTag.stringBinaryTag(value);
    }

    @Override
    public DataResult<BinaryTag> mergeToList(BinaryTag list, BinaryTag value) {
        return createCollector(list)
                .map(collector -> DataResult.success(collector.accept(value).result()))
                .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + StringTagVisitor.toString(list), list));
    }

    @Override
    public DataResult<BinaryTag> mergeToList(BinaryTag list, List<BinaryTag> values) {
        return createCollector(list)
                .map(collector -> DataResult.success(collector.acceptAll(values).result()))
                .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + StringTagVisitor.toString(list), list));
    }

    @Override
    public DataResult<BinaryTag> mergeToMap(BinaryTag map, BinaryTag key, BinaryTag value) {
        if (!(map instanceof CompoundBinaryTag) && !(map instanceof EndBinaryTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + StringTagVisitor.toString(map), map);
        }
        if (!(key instanceof StringBinaryTag stringKey)) {
            return DataResult.error(() -> "key is not a string: " + StringTagVisitor.toString(key), map);
        }
        var output = copyOf(map);
        output.put(stringKey.value(), value);
        return DataResult.success(output.build());
    }

    @Override
    public DataResult<BinaryTag> mergeToMap(BinaryTag map, MapLike<BinaryTag> values) {
        if (!(map instanceof CompoundBinaryTag) && !(map instanceof EndBinaryTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + StringTagVisitor.toString(map), map);
        }
        var iterator = values.entries().iterator();
        if (!iterator.hasNext()) {
            return map instanceof EndBinaryTag ? DataResult.success(emptyMap()) : DataResult.success(map);
        }
        var output = copyOf(map);
        List<BinaryTag> missed = new ArrayList<>();
        iterator.forEachRemaining(entry -> {
            if (entry.getFirst() instanceof StringBinaryTag key) {
                output.put(key.value(), entry.getSecond());
            } else {
                missed.add(entry.getFirst());
            }
        });
        if (!missed.isEmpty()) {
            CompoundBinaryTag partial = output.build();
            return DataResult.error(() -> "some keys are not strings: " + printList(missed), partial);
        }
        return DataResult.success(output.build());
    }

    @Override
    public DataResult<BinaryTag> mergeToMap(BinaryTag map, Map<BinaryTag, BinaryTag> values) {
        if (!(map instanceof CompoundBinaryTag) && !(map instanceof EndBinaryTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + StringTagVisitor.toString(map), map);
        }
        if (values.isEmpty()) {
            return map instanceof EndBinaryTag ? DataResult.success(emptyMap()) : DataResult.success(map);
        }
        var output = copyOf(map);
        List<BinaryTag> missed = new ArrayList<>();
        for (var entry : values.entrySet()) {
            if (entry.getKey() instanceof StringBinaryTag key) {
                output.put(key.value(), entry.getValue());
            } else {
                missed.add(entry.getKey());
            }
        }
        if (!missed.isEmpty()) {
            CompoundBinaryTag partial = output.build();
            return DataResult.error(() -> "some keys are not strings: " + printList(missed), partial);
        }
        return DataResult.success(output.build());
    }

    @Override
    public DataResult<Stream<Pair<BinaryTag, BinaryTag>>> getMapValues(BinaryTag input) {
        if (input instanceof CompoundBinaryTag compound) {
            return DataResult.success(compound.stream().map(entry -> Pair.<BinaryTag, BinaryTag>of(createString(entry.getKey()), entry.getValue())));
        }
        return DataResult.error(() -> "Not a map: " + StringTagVisitor.toString(input));
    }

    @Override
    public DataResult<Consumer<BiConsumer<BinaryTag, BinaryTag>>> getMapEntries(BinaryTag input) {
        if (input instanceof CompoundBinaryTag compound) {
            return DataResult.success(consumer -> {
                for (var entry : compound) {
                    consumer.accept(createString(entry.getKey()), entry.getValue());
                }
            });
        }
        return DataResult.error(() -> "Not a map: " + StringTagVisitor.toString(input));
    }

    @Override
    public DataResult<MapLike<BinaryTag>> getMap(BinaryTag input) {
        if (input instanceof CompoundBinaryTag compound) {
            return DataResult.success(new MapLike<>() {
                @Override
                public @Nullable BinaryTag get(BinaryTag key) {
                    if (key instanceof StringBinaryTag string) {
                        return compound.get(string.value());
                    }
                    throw new UnsupportedOperationException("Cannot get map entry with non-string key: " + StringTagVisitor.toString(key));
                }

                @Override
                public @Nullable BinaryTag get(String key) {
                    return compound.get(key);
                }

                @Override
                public Stream<Pair<BinaryTag, BinaryTag>> entries() {
                    return compound.stream().map(entry -> Pair.<BinaryTag, BinaryTag>of(createString(entry.getKey()), entry.getValue()));
                }

                @Override
                public String toString() {
                    return "MapLike[" + StringTagVisitor.toString(compound) + "]";
                }
            });
        }
        return DataResult.error(() -> "Not a map: " + StringTagVisitor.toString(input));
    }

    @Override
    public BinaryTag createMap(Stream<Pair<BinaryTag, BinaryTag>> map) {
        var output = CompoundBinaryTag.builder();
        map.forEach(entry -> {
            if (entry.getFirst() instanceof StringBinaryTag key) {
                output.put(key.value(), entry.getSecond());
            } else {
                throw new UnsupportedOperationException("Cannot create map with non-string key: " + StringTagVisitor.toString(entry.getFirst()));
            }
        });
        return output.build();
    }

    /**
     * 原版 {@code CollectionTag}：列表和三种数组；数组的元素是对应类型的数值标签。
     */
    @Override
    public DataResult<Stream<BinaryTag>> getStream(BinaryTag input) {
        Stream<BinaryTag> elements = collectionStream(input);
        return elements != null ? DataResult.success(elements) : DataResult.error(() -> "Not a list");
    }

    @Override
    public DataResult<Consumer<Consumer<BinaryTag>>> getList(BinaryTag input) {
        if (collectionStream(input) != null) {
            return DataResult.success(consumer -> collectionStream(input).forEach(consumer));
        }
        return DataResult.error(() -> "Not a list: " + StringTagVisitor.toString(input));
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(BinaryTag input) {
        if (input instanceof ByteArrayBinaryTag array) {
            return DataResult.success(ByteBuffer.wrap(array.value()));
        }
        return DynamicOps.super.getByteBuffer(input);
    }

    @Override
    public BinaryTag createByteList(ByteBuffer input) {
        ByteBuffer wholeBuffer = input.duplicate().clear();
        byte[] bytes = new byte[input.capacity()];
        wholeBuffer.get(0, bytes, 0, bytes.length);
        return ByteArrayBinaryTag.byteArrayBinaryTag(bytes);
    }

    @Override
    public DataResult<IntStream> getIntStream(BinaryTag input) {
        if (input instanceof IntArrayBinaryTag array) {
            return DataResult.success(Arrays.stream(array.value()));
        }
        return DynamicOps.super.getIntStream(input);
    }

    @Override
    public BinaryTag createIntList(IntStream input) {
        return IntArrayBinaryTag.intArrayBinaryTag(input.toArray());
    }

    @Override
    public DataResult<LongStream> getLongStream(BinaryTag input) {
        if (input instanceof LongArrayBinaryTag array) {
            return DataResult.success(Arrays.stream(array.value()));
        }
        return DynamicOps.super.getLongStream(input);
    }

    @Override
    public BinaryTag createLongList(LongStream input) {
        return LongArrayBinaryTag.longArrayBinaryTag(input.toArray());
    }

    /**
     * 原版 {@code new ListTag(list)}：元素类型可以不同。
     */
    @Override
    public BinaryTag createList(Stream<BinaryTag> input) {
        var builder = ListBinaryTag.heterogeneousListBinaryTag();
        input.forEach(builder::add);
        return builder.build();
    }

    @Override
    public BinaryTag remove(BinaryTag input, String key) {
        if (input instanceof CompoundBinaryTag compound) {
            return compound.remove(key);
        }
        return input;
    }

    @Override
    public String toString() {
        return "NBT";
    }

    @Override
    public RecordBuilder<BinaryTag> mapBuilder() {
        return new NbtRecordBuilder();
    }

    private static CompoundBinaryTag.Builder copyOf(BinaryTag map) {
        var output = CompoundBinaryTag.builder();
        if (map instanceof CompoundBinaryTag compound) {
            output.put(compound);
        }
        return output;
    }

    /**
     * 原版打印 {@code List<Tag>} 的形式：{@code [a, b]}，元素按 {@link StringTagVisitor} 打印。
     */
    private static String printList(List<BinaryTag> tags) {
        return tags.stream().map(StringTagVisitor::toString).toList().toString();
    }

    /**
     * 原版 {@code CollectionTag.stream()}；不是集合标签时为 {@code null}。
     */
    private static @Nullable Stream<BinaryTag> collectionStream(BinaryTag input) {
        return switch (input) {
            case ListBinaryTag list -> list.stream();
            case ByteArrayBinaryTag array -> {
                byte[] values = array.value();
                yield IntStream.range(0, values.length).mapToObj(i -> ByteBinaryTag.byteBinaryTag(values[i]));
            }
            case IntArrayBinaryTag array -> Arrays.stream(array.value()).mapToObj(IntBinaryTag::intBinaryTag);
            case LongArrayBinaryTag array -> Arrays.stream(array.value()).mapToObj(LongBinaryTag::longBinaryTag);
            default -> null;
        };
    }

    /**
     * 原版 {@code NbtOps.createCollector}。
     */
    private static Optional<ListCollector> createCollector(BinaryTag tag) {
        if (tag instanceof EndBinaryTag) {
            return Optional.of(new GenericListCollector());
        }
        return switch (tag) {
            case ListBinaryTag list when list.isEmpty() -> Optional.of(new GenericListCollector());
            case ByteArrayBinaryTag array when array.size() == 0 -> Optional.of(new GenericListCollector());
            case IntArrayBinaryTag array when array.size() == 0 -> Optional.of(new GenericListCollector());
            case LongArrayBinaryTag array when array.size() == 0 -> Optional.of(new GenericListCollector());
            case ListBinaryTag list -> Optional.of(new GenericListCollector(list));
            case ByteArrayBinaryTag array -> Optional.of(new ByteListCollector(array.value()));
            case IntArrayBinaryTag array -> Optional.of(new IntListCollector(array.value()));
            case LongArrayBinaryTag array -> Optional.of(new LongListCollector(array.value()));
            default -> Optional.empty();
        };
    }

    /**
     * 原版 {@code NbtOps.NbtRecordBuilder}。
     */
    private static final class NbtRecordBuilder extends RecordBuilder.AbstractStringBuilder<BinaryTag, CompoundBinaryTag.Builder> {
        private NbtRecordBuilder() {
            super(INSTANCE);
        }

        @Override
        protected CompoundBinaryTag.Builder initBuilder() {
            return CompoundBinaryTag.builder();
        }

        @Override
        protected CompoundBinaryTag.Builder append(String key, BinaryTag value, CompoundBinaryTag.Builder builder) {
            return builder.put(key, value);
        }

        @Override
        protected DataResult<BinaryTag> build(CompoundBinaryTag.Builder builder, BinaryTag prefix) {
            if (prefix == null || prefix instanceof EndBinaryTag) {
                return DataResult.success(builder.build());
            }
            if (prefix instanceof CompoundBinaryTag compound) {
                return DataResult.success(CompoundBinaryTag.builder().put(compound).put(builder.build()).build());
            }
            return DataResult.error(() -> "mergeToMap called with not a map: " + StringTagVisitor.toString(prefix), prefix);
        }
    }

    /**
     * 原版 {@code NbtOps.ListCollector}。
     */
    private interface ListCollector {
        ListCollector accept(BinaryTag tag);

        default ListCollector acceptAll(Iterable<BinaryTag> tags) {
            ListCollector collector = this;
            for (BinaryTag tag : tags) {
                collector = collector.accept(tag);
            }
            return collector;
        }

        BinaryTag result();
    }

    /**
     * 原版 {@code NbtOps.GenericListCollector}：元素类型可以不同的列表。
     */
    private static final class GenericListCollector implements ListCollector {
        private final List<BinaryTag> result = new ArrayList<>();

        private GenericListCollector() {
        }

        private GenericListCollector(ListBinaryTag initial) {
            initial.forEach(result::add);
        }

        private GenericListCollector(ByteArrayList initials) {
            for (int i = 0; i < initials.size(); i++) {
                result.add(ByteBinaryTag.byteBinaryTag(initials.getByte(i)));
            }
        }

        private GenericListCollector(IntArrayList initials) {
            for (int i = 0; i < initials.size(); i++) {
                result.add(IntBinaryTag.intBinaryTag(initials.getInt(i)));
            }
        }

        private GenericListCollector(LongArrayList initials) {
            for (int i = 0; i < initials.size(); i++) {
                result.add(LongBinaryTag.longBinaryTag(initials.getLong(i)));
            }
        }

        @Override
        public ListCollector accept(BinaryTag tag) {
            result.add(tag);
            return this;
        }

        @Override
        public BinaryTag result() {
            var builder = ListBinaryTag.heterogeneousListBinaryTag(result.size());
            result.forEach(builder::add);
            return builder.build();
        }
    }

    /**
     * 原版 {@code NbtOps.ByteListCollector}：遇到其它类型的元素时转为 {@link GenericListCollector}。
     */
    private static final class ByteListCollector implements ListCollector {
        private final ByteArrayList values = new ByteArrayList();

        private ByteListCollector(byte[] initialValues) {
            values.addElements(0, initialValues);
        }

        @Override
        public ListCollector accept(BinaryTag tag) {
            if (tag instanceof ByteBinaryTag value) {
                values.add(value.value());
                return this;
            }
            return new GenericListCollector(values).accept(tag);
        }

        @Override
        public BinaryTag result() {
            return ByteArrayBinaryTag.byteArrayBinaryTag(values.toByteArray());
        }
    }

    /**
     * 原版 {@code NbtOps.IntListCollector}。
     */
    private static final class IntListCollector implements ListCollector {
        private final IntArrayList values = new IntArrayList();

        private IntListCollector(int[] initialValues) {
            values.addElements(0, initialValues);
        }

        @Override
        public ListCollector accept(BinaryTag tag) {
            if (tag instanceof IntBinaryTag value) {
                values.add(value.value());
                return this;
            }
            return new GenericListCollector(values).accept(tag);
        }

        @Override
        public BinaryTag result() {
            return IntArrayBinaryTag.intArrayBinaryTag(values.toIntArray());
        }
    }

    /**
     * 原版 {@code NbtOps.LongListCollector}。
     */
    private static final class LongListCollector implements ListCollector {
        private final LongArrayList values = new LongArrayList();

        private LongListCollector(long[] initialValues) {
            values.addElements(0, initialValues);
        }

        @Override
        public ListCollector accept(BinaryTag tag) {
            if (tag instanceof LongBinaryTag value) {
                values.add(value.value());
                return this;
            }
            return new GenericListCollector(values).accept(tag);
        }

        @Override
        public BinaryTag result() {
            return LongArrayBinaryTag.longArrayBinaryTag(values.toLongArray());
        }
    }
}
