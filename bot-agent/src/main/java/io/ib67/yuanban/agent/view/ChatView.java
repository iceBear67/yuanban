package io.ib67.yuanban.agent.view;

import org.jetbrains.annotations.Nullable;

/**
 * 一条聊天消息。
 *
 * @param kind       {@code PLAYER}、{@code DISGUISED} 或 {@code SYSTEM}
 * @param sender     玩家聊天的发送者 UUID；其余为 null
 * @param senderName 玩家聊天与伪装聊天的发送者名称；系统消息为 null
 * @param text       去掉格式的文本
 * @param signature  签名的校验结果：bot 不校验聊天签名，玩家聊天为 {@code UNVERIFIED}，其余为 {@code NONE}
 */
public record ChatView(String kind, @Nullable String sender, @Nullable String senderName, String text, String signature) {
}
