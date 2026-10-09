package io.ib67.yuanban.agent.view;

import org.jetbrains.annotations.Nullable;

/**
 * 当前世界。
 *
 * @param epoch      bot 进入的第几个世界，从 1 开始
 * @param entryCause 进入的原因：{@code JOIN}、{@code RECONFIGURATION}、{@code RESPAWN}、{@code TRANSFER}
 * @param difficulty 难度名称；没有收到时为 null
 */
public record WorldStatusView(String dimension, long epoch, String entryCause, long worldAge, float rain,
                              float thunder, @Nullable String difficulty, boolean frozen) {
}
