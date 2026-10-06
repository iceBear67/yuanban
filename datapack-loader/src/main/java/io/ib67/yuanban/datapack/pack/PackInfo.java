package io.ib67.yuanban.datapack.pack;

import java.util.Objects;
import java.util.Optional;

/**
 * 资源和标签条目所属数据包的简要信息。
 */
public record PackInfo(String id, PackOrigin origin, Optional<KnownPack> knownPack) {
    public PackInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(knownPack, "knownPack");
    }
}
