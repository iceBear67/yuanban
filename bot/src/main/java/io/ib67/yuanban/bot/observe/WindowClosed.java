package io.ib67.yuanban.bot.observe;

/**
 * 窗口关闭：服务端发来 Close Container，或 bot 发出 Close Container。
 */
public record WindowClosed(int windowId) implements Observation {
}
