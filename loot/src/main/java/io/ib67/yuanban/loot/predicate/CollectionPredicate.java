package io.ib67.yuanban.loot.predicate;

import net.minestom.server.utils.Range;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 集合谓词，对应原版 {@code net.minecraft.advancements.predicates.CollectionPredicate}，
 * 用于容器内容、烟花爆炸、书页、属性修饰符等列表。
 *
 * @param contains JSON 字段 {@code contains}：每个元素谓词都至少匹配集合中的一个元素（原版 {@code CollectionContentsPredicate}）
 * @param count    JSON 字段 {@code count}：每一项的谓词匹配的元素个数落在范围内（原版 {@code CollectionCountsPredicate}）
 * @param size     JSON 字段 {@code size}：集合的大小
 * @param <P>      元素谓词的类型
 */
public record CollectionPredicate<P>(Optional<List<P>> contains, Optional<List<CountEntry<P>>> count,
                                     Optional<Range.Int> size) {
    public CollectionPredicate {
        contains = contains.map(List::copyOf);
        count = count.map(List::copyOf);
        Objects.requireNonNull(size, "size");
    }

    /**
     * 原版 {@code CollectionCountsPredicate$Entry}。
     *
     * @param test  元素谓词，JSON 字段 {@code test}
     * @param count 匹配的个数，JSON 字段 {@code count}
     */
    public record CountEntry<P>(P test, Range.Int count) {
        public CountEntry {
            Objects.requireNonNull(test, "test");
            Objects.requireNonNull(count, "count");
        }
    }
}
