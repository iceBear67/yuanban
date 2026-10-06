package io.ib67.yuanban.datapack;

/**
 * 启用列表不满足规则，例如默认数据包不可用、启用未知的数据包、禁用特性数据包。
 */
public class SelectionException extends DataPackException {
    public SelectionException(String message) {
        super(message);
    }
}
