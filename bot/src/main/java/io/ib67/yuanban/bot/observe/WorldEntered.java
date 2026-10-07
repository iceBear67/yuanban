package io.ib67.yuanban.bot.observe;

/**
 * bot 进入了一个世界：Login (play) 或 Respawn 应用之后。
 */
public record WorldEntered(WorldInfo world) implements Observation {
}
