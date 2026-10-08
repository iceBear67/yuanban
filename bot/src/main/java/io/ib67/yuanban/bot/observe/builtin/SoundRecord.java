package io.ib67.yuanban.bot.observe.builtin;

import net.minestom.server.coordinate.Point;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.Sound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;

/**
 * 一个音效数据包。
 *
 * @param position 音效位置；实体音效为收到时该实体在模型中的位置
 * @param entityId 实体音效的实体编号；位置音效为 null
 */
public record SoundRecord(Sound sound, SoundCategory category, Point position, float volume, float pitch,
                          @Nullable Integer entityId) {
}
