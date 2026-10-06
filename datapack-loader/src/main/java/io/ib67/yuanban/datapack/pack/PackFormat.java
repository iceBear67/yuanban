package io.ib67.yuanban.datapack.pack;

import java.util.Locale;

/**
 * 数据包格式版本，由主版本和次版本组成。次版本是 {@link #ANY_MINOR} 的时候表示主版本下的任意次版本。
 */
public record PackFormat(int major, int minor) implements Comparable<PackFormat> {
    public static final int ANY_MINOR = Integer.MAX_VALUE;

    public PackFormat {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("pack format must be non-negative: " + major + "." + minor);
        }
    }

    public static PackFormat of(int major, int minor) {
        return new PackFormat(major, minor);
    }

    public static PackFormat of(int major) {
        return new PackFormat(major, 0);
    }

    /**
     * 从这个版本到同一主版本下任意次版本的范围。
     */
    public FormatRange minorRange() {
        return new FormatRange(this, new PackFormat(major, ANY_MINOR));
    }

    @Override
    public int compareTo(PackFormat other) {
        int result = Integer.compare(major, other.major);
        return result != 0 ? result : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return minor == ANY_MINOR
                ? String.format(Locale.ROOT, "%d.*", major)
                : String.format(Locale.ROOT, "%d.%d", major, minor);
    }
}
