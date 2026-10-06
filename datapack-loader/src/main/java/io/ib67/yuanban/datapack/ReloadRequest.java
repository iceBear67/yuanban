package io.ib67.yuanban.datapack;

import java.util.List;

/**
 * 重载请求。
 */
public sealed interface ReloadRequest {
    /**
     * 对应 {@code /reload}：重新发现数据包，按启动流程的选择算法追加新出现的数据包。
     */
    record Rescan() implements ReloadRequest {
    }

    /**
     * 对应 {@code /datapack enable|disable}：不重新发现数据包，按给定顺序加载。
     *
     * @param ids 数据包 ID，优先级从低到高
     */
    record Select(List<String> ids) implements ReloadRequest {
        public Select {
            ids = List.copyOf(ids);
        }
    }
}
