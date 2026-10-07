package io.ib67.yuanban.bot.observe.builtin;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Point;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 一个音效数据包。
 *
 * @param position 音效位置；实体音效为收到时该实体在模型中的位置
 * @param entityId 实体音效的实体编号；位置音效为 null
 */
public record SoundRecord(SoundEvent sound, Sound.Source source, Point position, float volume, float pitch,
                          @Nullable Integer entityId) {
}
