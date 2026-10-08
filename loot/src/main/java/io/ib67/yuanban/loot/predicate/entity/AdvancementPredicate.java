package io.ib67.yuanban.loot.predicate.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 进度谓词，对应原版 {@code PlayerPredicate$AdvancementPredicate}：布尔值表示进度是否完成，
 * 对象表示各准则是否完成。
 */
public sealed interface AdvancementPredicate {
    /**
     * 原版 {@code AdvancementDonePredicate}。
     */
    record Done(boolean done) implements AdvancementPredicate {
    }

    /**
     * 原版 {@code AdvancementCriterionsPredicate}：准则名到期望的完成状态，保持 JSON 中的顺序。
     */
    record Criteria(Map<String, Boolean> criteria) implements AdvancementPredicate {
        public Criteria {
            criteria = Collections.unmodifiableMap(new LinkedHashMap<>(criteria));
        }
    }
}
