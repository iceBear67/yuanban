package io.ib67.yuanban.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * 原版 {@code net.minecraft.resources.DelegatingOps}：把全部操作转交给另一个 {@link DynamicOps}。
 * DataFixerUpper 10.0.21 本身不包含这个类。
 */
public abstract class DelegatingOps<T> implements DynamicOps<T> {
    protected final DynamicOps<T> delegate;

    protected DelegatingOps(DynamicOps<T> delegate) {
        this.delegate = delegate;
    }

    /**
     * 被转交的 {@link DynamicOps}。
     */
    public DynamicOps<T> delegate() {
        return delegate;
    }

    @Override
    public T empty() {
        return delegate.empty();
    }

    @Override
    public T emptyMap() {
        return delegate.emptyMap();
    }

    @Override
    public T emptyList() {
        return delegate.emptyList();
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, T input) {
        @SuppressWarnings("unchecked")
        U same = (U) input;
        return Objects.equals(outOps, delegate) ? same : delegate.convertTo(outOps, input);
    }

    @Override
    public DataResult<Number> getNumberValue(T input) {
        return delegate.getNumberValue(input);
    }

    @Override
    public T createNumeric(Number i) {
        return delegate.createNumeric(i);
    }

    @Override
    public T createByte(byte value) {
        return delegate.createByte(value);
    }

    @Override
    public T createShort(short value) {
        return delegate.createShort(value);
    }

    @Override
    public T createInt(int value) {
        return delegate.createInt(value);
    }

    @Override
    public T createLong(long value) {
        return delegate.createLong(value);
    }

    @Override
    public T createFloat(float value) {
        return delegate.createFloat(value);
    }

    @Override
    public T createDouble(double value) {
        return delegate.createDouble(value);
    }

    @Override
    public DataResult<Boolean> getBooleanValue(T input) {
        return delegate.getBooleanValue(input);
    }

    @Override
    public T createBoolean(boolean value) {
        return delegate.createBoolean(value);
    }

    @Override
    public DataResult<String> getStringValue(T input) {
        return delegate.getStringValue(input);
    }

    @Override
    public T createString(String value) {
        return delegate.createString(value);
    }

    @Override
    public DataResult<T> mergeToList(T list, T value) {
        return delegate.mergeToList(list, value);
    }

    @Override
    public DataResult<T> mergeToList(T list, List<T> values) {
        return delegate.mergeToList(list, values);
    }

    @Override
    public DataResult<T> mergeToMap(T map, T key, T value) {
        return delegate.mergeToMap(map, key, value);
    }

    @Override
    public DataResult<T> mergeToMap(T map, MapLike<T> values) {
        return delegate.mergeToMap(map, values);
    }

    @Override
    public DataResult<T> mergeToMap(T map, Map<T, T> values) {
        return delegate.mergeToMap(map, values);
    }

    @Override
    public DataResult<T> mergeToPrimitive(T prefix, T value) {
        return delegate.mergeToPrimitive(prefix, value);
    }

    @Override
    public DataResult<Stream<Pair<T, T>>> getMapValues(T input) {
        return delegate.getMapValues(input);
    }

    @Override
    public DataResult<Consumer<BiConsumer<T, T>>> getMapEntries(T input) {
        return delegate.getMapEntries(input);
    }

    @Override
    public T createMap(Map<T, T> map) {
        return delegate.createMap(map);
    }

    @Override
    public T createMap(Stream<Pair<T, T>> map) {
        return delegate.createMap(map);
    }

    @Override
    public DataResult<MapLike<T>> getMap(T input) {
        return delegate.getMap(input);
    }

    @Override
    public DataResult<Stream<T>> getStream(T input) {
        return delegate.getStream(input);
    }

    @Override
    public DataResult<Consumer<Consumer<T>>> getList(T input) {
        return delegate.getList(input);
    }

    @Override
    public T createList(Stream<T> input) {
        return delegate.createList(input);
    }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(T input) {
        return delegate.getByteBuffer(input);
    }

    @Override
    public T createByteList(ByteBuffer input) {
        return delegate.createByteList(input);
    }

    @Override
    public DataResult<IntStream> getIntStream(T input) {
        return delegate.getIntStream(input);
    }

    @Override
    public T createIntList(IntStream input) {
        return delegate.createIntList(input);
    }

    @Override
    public DataResult<LongStream> getLongStream(T input) {
        return delegate.getLongStream(input);
    }

    @Override
    public T createLongList(LongStream input) {
        return delegate.createLongList(input);
    }

    @Override
    public T remove(T input, String key) {
        return delegate.remove(input, key);
    }

    @Override
    public boolean compressMaps() {
        return delegate.compressMaps();
    }

    @Override
    public ListBuilder<T> listBuilder() {
        return new DelegateListBuilder(delegate.listBuilder());
    }

    @Override
    public RecordBuilder<T> mapBuilder() {
        return new DelegateRecordBuilder(delegate.mapBuilder());
    }

    protected class DelegateListBuilder implements ListBuilder<T> {
        private final ListBuilder<T> original;

        protected DelegateListBuilder(ListBuilder<T> original) {
            this.original = original;
        }

        @Override
        public DynamicOps<T> ops() {
            return DelegatingOps.this;
        }

        @Override
        public DataResult<T> build(T prefix) {
            return original.build(prefix);
        }

        @Override
        public ListBuilder<T> add(T value) {
            original.add(value);
            return this;
        }

        @Override
        public ListBuilder<T> add(DataResult<T> value) {
            original.add(value);
            return this;
        }

        @Override
        public <E> ListBuilder<T> add(E value, Encoder<E> encoder) {
            original.add(encoder.encodeStart(ops(), value));
            return this;
        }

        @Override
        public <E> ListBuilder<T> addAll(Iterable<E> values, Encoder<E> encoder) {
            values.forEach(v -> original.add(encoder.encode(v, ops(), ops().empty())));
            return this;
        }

        @Override
        public ListBuilder<T> withErrorsFrom(DataResult<?> result) {
            original.withErrorsFrom(result);
            return this;
        }

        @Override
        public ListBuilder<T> mapError(UnaryOperator<String> onError) {
            original.mapError(onError);
            return this;
        }

        @Override
        public DataResult<T> build(DataResult<T> prefix) {
            return original.build(prefix);
        }
    }

    protected class DelegateRecordBuilder implements RecordBuilder<T> {
        private final RecordBuilder<T> original;

        protected DelegateRecordBuilder(RecordBuilder<T> original) {
            this.original = original;
        }

        @Override
        public DynamicOps<T> ops() {
            return DelegatingOps.this;
        }

        @Override
        public RecordBuilder<T> add(T key, T value) {
            original.add(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(T key, DataResult<T> value) {
            original.add(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(DataResult<T> key, DataResult<T> value) {
            original.add(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(String key, T value) {
            original.add(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(String key, DataResult<T> value) {
            original.add(key, value);
            return this;
        }

        @Override
        public <E> RecordBuilder<T> add(String key, E value, Encoder<E> encoder) {
            return original.add(key, encoder.encodeStart(ops(), value));
        }

        @Override
        public RecordBuilder<T> withErrorsFrom(DataResult<?> result) {
            original.withErrorsFrom(result);
            return this;
        }

        @Override
        public RecordBuilder<T> setLifecycle(Lifecycle lifecycle) {
            original.setLifecycle(lifecycle);
            return this;
        }

        @Override
        public RecordBuilder<T> mapError(UnaryOperator<String> onError) {
            original.mapError(onError);
            return this;
        }

        @Override
        public DataResult<T> build(T prefix) {
            return original.build(prefix);
        }

        @Override
        public DataResult<T> build(DataResult<T> prefix) {
            return original.build(prefix);
        }
    }
}
