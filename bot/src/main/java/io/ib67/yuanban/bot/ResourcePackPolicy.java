package io.ib67.yuanban.bot;

/**
 * 收到 Add Resource Pack 时的回应方式。本库不下载资源包。
 */
public enum ResourcePackPolicy {
    /**
     * 依次回应 Accepted、Downloaded、Successfully downloaded
     */
    ACCEPT,
    /**
     * 回应 Declined
     */
    DECLINE
}
