package io.ib67.yuanban.datapack.selection;

/**
 * {@code /datapack enable} 插入数据包的位置。
 */
public sealed interface Placement {
    /**
     * 按数据包的默认位置插入，跳过固定在这一端的数据包。
     */
    record DefaultPosition() implements Placement {
    }

    /**
     * 优先级最低。
     */
    record First() implements Placement {
    }

    /**
     * 优先级最高。
     */
    record Last() implements Placement {
    }

    /**
     * 插在已启用的数据包 {@code existing} 之前。
     */
    record Before(String existing) implements Placement {
    }

    /**
     * 插在已启用的数据包 {@code existing} 之后。
     */
    record After(String existing) implements Placement {
    }
}
