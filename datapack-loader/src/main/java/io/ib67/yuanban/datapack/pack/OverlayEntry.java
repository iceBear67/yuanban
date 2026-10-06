package io.ib67.yuanban.datapack.pack;

import java.util.Objects;

/**
 * {@code overlays.entries} 中的一项。
 */
public record OverlayEntry(String directory, FormatRange formats) {
    public OverlayEntry {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(formats, "formats");
    }
}
