package io.ib67.yuanban.datapack.pack;

import io.ib67.yuanban.datapack.LoaderSettings;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 一个已发现的数据包。数据包以 ID 区分。
 */
public final class Pack {
    private final String id;
    private final PackOrigin origin;
    private final @Nullable KnownPack knownPack;
    private final PackSelectionConfig selectionConfig;
    private final PackMetadata metadata;
    private final PackCompatibility compatibility;
    private final List<String> activeOverlays;
    private final PackRoot root;
    private final PackInfo info;

    private Pack(String id,
                 PackOrigin origin,
                 @Nullable KnownPack knownPack,
                 PackSelectionConfig selectionConfig,
                 PackMetadata metadata,
                 PackCompatibility compatibility,
                 List<String> activeOverlays,
                 PackRoot root) {
        this.id = id;
        this.origin = origin;
        this.knownPack = knownPack;
        this.selectionConfig = selectionConfig;
        this.metadata = metadata;
        this.compatibility = compatibility;
        this.activeOverlays = activeOverlays;
        this.root = root;
        this.info = new PackInfo(id, origin, Optional.ofNullable(knownPack));
    }

    /**
     * 创建数据包，按 {@link LoaderSettings#dataFormat()} 计算兼容性和生效的 overlay 目录。
     */
    public static Pack create(LoaderSettings settings,
                              String id,
                              PackOrigin origin,
                              @Nullable KnownPack knownPack,
                              PackSelectionConfig selectionConfig,
                              PackMetadata metadata,
                              PackRoot root) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(selectionConfig, "selectionConfig");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(root, "root");
        var current = settings.dataFormat();
        var overlays = metadata.overlays().stream()
                .filter(entry -> entry.formats().contains(current))
                .map(OverlayEntry::directory)
                .toList();
        return new Pack(id, origin, knownPack, selectionConfig, metadata,
                PackCompatibility.of(metadata.formats(), current), overlays, root);
    }

    public String id() {
        return id;
    }

    public PackOrigin origin() {
        return origin;
    }

    public Optional<KnownPack> knownPack() {
        return Optional.ofNullable(knownPack);
    }

    public PackSelectionConfig selectionConfig() {
        return selectionConfig;
    }

    public PackMetadata metadata() {
        return metadata;
    }

    public PackCompatibility compatibility() {
        return compatibility;
    }

    /**
     * 当前格式下生效的 overlay 目录，保持 {@code pack.mcmeta} 中的顺序，越靠后优先级越高。
     */
    public List<String> activeOverlays() {
        return activeOverlays;
    }

    public PackRoot root() {
        return root;
    }

    public PackInfo info() {
        return info;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Pack other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Pack[" + id + "]";
    }
}
