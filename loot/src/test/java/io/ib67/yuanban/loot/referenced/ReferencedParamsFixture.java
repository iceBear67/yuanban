package io.ib67.yuanban.loot.referenced;

import io.ib67.yuanban.loot.context.ContextKey;

import java.util.Set;
import java.util.stream.Collectors;

final class ReferencedParamsFixture {

    private ReferencedParamsFixture() {
    }

    static Set<String> ids(Set<ContextKey<?>> params) {
        return params.stream().map(contextKey -> contextKey.id().toString()).collect(Collectors.toSet());
    }
}
