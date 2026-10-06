package io.ib67.yuanban.datapack.pack;

/**
 * 数据包声明的格式范围和当前游戏格式的关系。只作为信息提供，不兼容的数据包仍然可以启用和加载。
 */
public enum PackCompatibility {
    TOO_OLD,
    TOO_NEW,
    COMPATIBLE;

    public static PackCompatibility of(FormatRange declared, PackFormat current) {
        if (declared.max().compareTo(current) < 0) {
            return TOO_OLD;
        }
        if (current.compareTo(declared.min()) < 0) {
            return TOO_NEW;
        }
        return COMPATIBLE;
    }
}
