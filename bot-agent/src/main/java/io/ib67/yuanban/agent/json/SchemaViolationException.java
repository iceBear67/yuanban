package io.ib67.yuanban.agent.json;

import java.util.List;

/**
 * 一个值没有通过 JSON Schema 校验。
 */
public final class SchemaViolationException extends RuntimeException {
    private final List<String> errors;

    public SchemaViolationException(String what, List<String> errors) {
        super(what + " does not match its schema: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
