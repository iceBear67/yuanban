package io.ib67.yuanban.bot.data;

import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 实体类型的成年默认尺寸、乘客挂点与是否为活体，取自 {@code entity_type.json}，按 MCProtocolLib {@link EntityType} 的序号索引
 * （序号即注册表编号，加载时逐项检查）。
 */
public final class EntityTypeData {
    private static final Entry[] ENTRIES = load();

    private EntityTypeData() {
    }

    public static double width(EntityType type) {
        return ENTRIES[type.ordinal()].width();
    }

    public static double height(EntityType type) {
        return ENTRIES[type.ordinal()].height();
    }

    /**
     * 原版只有活体（{@code LivingEntity}）有属性。数据中 {@code packetType} 为 LIVING 或 PLAYER 的类型是活体
     */
    public static boolean living(EntityType type) {
        return ENTRIES[type.ordinal()].living();
    }

    /**
     * 乘客挂点；数据中没有时为 null（原版 {@code EntityAttachment.Fallback.AT_HEIGHT}，由调用方按实体高度计算）
     */
    public static @Nullable List<Vec> passengerAttachments(EntityType type) {
        return ENTRIES[type.ordinal()].passengerAttachments();
    }

    private static Entry[] load() {
        var json = MinestomDataFiles.read("entity_type.json");
        var types = EntityType.values();
        var entries = new Entry[types.length];
        for (var type : types) {
            var name = "minecraft:" + type.name().toLowerCase(Locale.ROOT);
            var entry = Objects.requireNonNull(json.getAsJsonObject(name), () -> "entity_type.json has no " + name);
            if (entry.get("id").getAsInt() != type.ordinal()) {
                throw new IllegalStateException("entity type " + name + " has id " + entry.get("id")
                        + " in entity_type.json, but " + type.ordinal() + " in MCProtocolLib");
            }
            var packetType = entry.get("packetType").getAsString();
            List<Vec> passenger = null;
            var attachments = entry.getAsJsonObject("attachments");
            if (attachments != null && attachments.has("PASSENGER")) {
                var points = new ArrayList<Vec>();
                for (var point : attachments.getAsJsonArray("PASSENGER")) {
                    var xyz = point.getAsJsonArray();
                    points.add(new Vec(xyz.get(0).getAsDouble(), xyz.get(1).getAsDouble(), xyz.get(2).getAsDouble()));
                }
                passenger = List.copyOf(points);
            }
            entries[type.ordinal()] = new Entry(entry.get("width").getAsDouble(), entry.get("height").getAsDouble(),
                    packetType.equals("LIVING") || packetType.equals("PLAYER"), passenger);
        }
        return entries;
    }

    private record Entry(double width, double height, boolean living, @Nullable List<Vec> passengerAttachments) {
    }
}
