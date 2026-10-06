package io.ib67.yuanban.datapack.pack;

import java.util.List;

/**
 * 数据包的 {@code filter} 段落，只作用于优先级比所属数据包低的数据包。
 */
public record ResourceFilter(List<FilterRule> block) {
    public static final ResourceFilter NONE = new ResourceFilter(List.of());

    public ResourceFilter {
        block = List.copyOf(block);
    }

    public boolean isEmpty() {
        return block.isEmpty();
    }

    /**
     * 判断文件位置是否被屏蔽。规则和原版 {@code MultiPackResourceManager} 相同：
     * 有一条规则的命名空间匹配，并且有一条规则的路径匹配，两个条件可以由不同的规则分别满足。
     *
     * @param namespace 命名空间
     * @param path      命名空间目录下的完整相对路径，包括 topic 目录和扩展名
     */
    public boolean blocks(String namespace, String path) {
        return block.stream().anyMatch(rule -> rule.matchesNamespace(namespace))
                && block.stream().anyMatch(rule -> rule.matchesPath(path));
    }
}
