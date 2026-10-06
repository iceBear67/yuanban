package io.ib67.yuanban.datapack;

/**
 * 订阅关系错误：key 重复、依赖未注册、依赖有环、topic 不合法。
 */
public class SubscriptionException extends DataPackException {
    public SubscriptionException(String message) {
        super(message);
    }
}
