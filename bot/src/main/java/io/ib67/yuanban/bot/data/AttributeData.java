package io.ib67.yuanban.bot.data;

import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 属性的默认值与取值范围，取自 {@code attribute.json}。MCProtocolLib 的 {@link AttributeType.Builtin} 也带有这些数值，
 * 但其中 {@code jump_strength} 的默认值为 0.42，原版为 {@code (double) 0.42F}
 */
public final class AttributeData {
    private static final Map<AttributeType, Range> RANGES = load();

    private AttributeData() {
    }

    public static double defaultValue(AttributeType attribute) {
        return range(attribute).defaultValue();
    }

    public static double min(AttributeType attribute) {
        return range(attribute).min();
    }

    public static double max(AttributeType attribute) {
        return range(attribute).max();
    }

    private static Range range(AttributeType attribute) {
        return Objects.requireNonNull(RANGES.get(attribute), () -> "unknown attribute " + attribute.getIdentifier());
    }

    private static Map<AttributeType, Range> load() {
        var json = MinestomDataFiles.read("attribute.json");
        var result = new HashMap<AttributeType, Range>();
        for (var attribute : AttributeType.Builtin.values()) {
            var entry = Objects.requireNonNull(json.getAsJsonObject(attribute.getIdentifier().asString()),
                    () -> "attribute.json has no " + attribute.getIdentifier());
            if (entry.get("id").getAsInt() != attribute.getId()) {
                throw new IllegalStateException("attribute " + attribute.getIdentifier() + " has id " + entry.get("id")
                        + " in attribute.json, but " + attribute.getId() + " in MCProtocolLib");
            }
            result.put(attribute, new Range(entry.get("defaultValue").getAsDouble(), entry.get("minValue").getAsDouble(),
                    entry.get("maxValue").getAsDouble()));
        }
        return Map.copyOf(result);
    }

    private record Range(double defaultValue, double min, double max) {
    }
}
