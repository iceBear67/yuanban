package io.ib67.yuanban.bot.observe;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Set Title Text 或 Set Subtitle Text 之后的标题与副标题。
 */
public record TitleShown(@Nullable Component title, @Nullable Component subtitle) implements Observation {
}
