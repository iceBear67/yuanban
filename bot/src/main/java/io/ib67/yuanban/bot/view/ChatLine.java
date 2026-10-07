package io.ib67.yuanban.bot.view;

import net.kyori.adventure.text.Component;
import net.minestom.server.message.ChatType;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 聊天日志中的一条消息。
 *
 * @param sender     玩家聊天的发送者；其余为 null
 * @param chatType   玩家聊天与伪装聊天的聊天类型（{@code minecraft:chat_type} 注册表中的条目）；系统消息为 null
 * @param senderName 玩家聊天与伪装聊天的发送者名称（聊天类型修饰消息时的 sender 参数）；系统消息为 null
 * @param targetName 玩家聊天与伪装聊天的目标名称（聊天类型修饰消息时的 target 参数）；没有目标名称时与系统消息为 null
 * @param message    消息内容，不经过聊天类型的修饰：玩家聊天为未签名内容，没有时为消息正文；部分过滤的玩家聊天为过滤之后的消息正文；
 *                   发送者不在玩家列表中的玩家聊天为原版显示的 {@code chat.validation_error}；其余为数据包中的消息
 * @param plainText  去掉格式后的文本（adventure 的 {@code PlainTextComponentSerializer}）：翻译组件不翻译，它本身与翻译参数都不出现在文本中，
 *                   只保留它的子组件；按翻译键匹配消息使用 {@code Observers.firstTranslatedChat}
 */
public record ChatLine(ChatKind kind, @Nullable UUID sender, @Nullable RegistryKey<ChatType> chatType,
                       @Nullable Component senderName, @Nullable Component targetName,
                       Component message, String plainText) {
}
