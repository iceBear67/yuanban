package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.view.ChatLine;

/**
 * Player Chat Message、Disguised Chat Message、不带 overlay 的 System Chat Message。
 */
public record ChatReceived(ChatLine line) implements Observation {
}
