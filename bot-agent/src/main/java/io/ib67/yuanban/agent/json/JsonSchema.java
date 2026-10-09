package io.ib67.yuanban.agent.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.util.List;

/**
 * 一个 JSON Schema（draft 2020-12）：原始文档用于发给模型与写入文档，编译结果用于校验。
 */
public final class JsonSchema {
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private final JsonObject document;
    private final Schema compiled;

    private JsonSchema(JsonObject document) {
        this.document = document.deepCopy();
        this.compiled = REGISTRY.getSchema(Json.write(document), InputFormat.JSON);
        this.compiled.initializeValidators();
    }

    /**
     * @throws com.networknt.schema.SchemaException 不是合法的 schema
     */
    public static JsonSchema of(JsonObject document) {
        return new JsonSchema(document);
    }

    public static JsonSchema parse(String document) {
        return of(Json.parseObject(document));
    }

    public JsonObject document() {
        return document.deepCopy();
    }

    /**
     * @return 每个错误一条说明（实例位置与原因）；通过校验时为空
     */
    public List<String> validate(JsonElement value) {
        return compiled.validate(Json.write(value), InputFormat.JSON).stream().map(Error::toString).toList();
    }

    /**
     * @throws SchemaViolationException 没有通过校验
     */
    public void require(JsonElement value, String what) {
        var errors = validate(value);
        if (!errors.isEmpty()) {
            throw new SchemaViolationException(what, errors);
        }
    }
}
