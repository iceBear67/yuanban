package io.ib67.yuanban.bot;

import org.jetbrains.annotations.Nullable;

/**
 * 按频道回应 Login Plugin Request。在调度线程上调用。
 */
@FunctionalInterface
public interface LoginPluginHandler {
    /**
     * 原版客户端的行为：总是回应未理解
     */
    LoginPluginHandler NOT_UNDERSTOOD = (_, _) -> null;

    /**
     * @return 回应的数据；null 表示未理解，回应包中的数据缺省
     */
    byte @Nullable [] respond(String channel, byte[] data);
}
