package io.ib67.yuanban.bot.observe;

import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;

/**
 * Entity Event。
 */
public record EntityStatusReceived(int entityId, EntityEvent event) implements Observation {
}
