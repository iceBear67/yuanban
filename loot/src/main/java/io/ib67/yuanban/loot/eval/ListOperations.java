package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.function.AppendOperation;
import io.ib67.yuanban.loot.function.InsertOperation;
import io.ib67.yuanban.loot.function.ListOperation;
import io.ib67.yuanban.loot.function.ReplaceAllOperation;
import io.ib67.yuanban.loot.function.ReplaceSectionOperation;
import io.ib67.yuanban.loot.function.StandAloneOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版 {@code ListOperation#apply} 的四种实现。原版在偏移越界或结果超过上限时记录错误日志并返回原列表，这里返回原列表，
 * 不记录日志。
 */
final class ListOperations {
    private ListOperations() {
    }

    /**
     * 原版 {@code ListOperation#apply(List, List, int)}。
     */
    static <T> List<T> apply(ListOperation operation, List<T> original, List<T> replacement, int maxSize) {
        return switch (operation) {
            case ReplaceAllOperation ignored -> replacement;
            case ReplaceSectionOperation section -> replaceSection(section, original, replacement, maxSize);
            case InsertOperation insert -> insert(insert, original, replacement, maxSize);
            case AppendOperation ignored -> {
                if (original.size() + replacement.size() > maxSize) {
                    yield original;
                }
                var result = new ArrayList<>(original);
                result.addAll(replacement);
                yield List.copyOf(result);
            }
        };
    }

    /**
     * 原版 {@code ListOperation$StandAlone#apply}：上限为 {@link Integer#MAX_VALUE}。
     */
    static <T> List<T> apply(StandAloneOperation<T> operation, List<T> original) {
        return apply(operation.operation(), original, operation.values(), Integer.MAX_VALUE);
    }

    private static <T> List<T> insert(InsertOperation insert, List<T> original, List<T> replacement, int maxSize) {
        int originalSize = original.size();
        if (insert.offset() > originalSize || originalSize + replacement.size() > maxSize) {
            return original;
        }
        var result = new ArrayList<>(original.subList(0, insert.offset()));
        result.addAll(replacement);
        result.addAll(original.subList(insert.offset(), originalSize));
        return List.copyOf(result);
    }

    private static <T> List<T> replaceSection(ReplaceSectionOperation section, List<T> original, List<T> replacement, int maxSize) {
        int originalSize = original.size();
        if (section.offset() > originalSize) {
            return original;
        }
        var result = new ArrayList<>(original.subList(0, section.offset()));
        result.addAll(replacement);
        int resumeIndex = section.offset() + section.size().orElse(replacement.size());
        if (resumeIndex < originalSize) {
            result.addAll(original.subList(resumeIndex, originalSize));
        }
        return result.size() > maxSize ? original : List.copyOf(result);
    }
}
