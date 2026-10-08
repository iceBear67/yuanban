package io.ib67.yuanban.datapack.pack;

import org.jetbrains.annotations.NotNull;

/**
 * 客户端可能已经拥有的数据包，对应原版同名类型。服务端同步注册表的时候，
 * 条目所属的数据包是客户端已有的已知数据包，就可以省略条目内容。
 */
public record KnownPack(String namespace, String id, String version) {
    @Override
    public @NotNull String toString() {
        return namespace + ":" + id + ":" + version;
    }
}
