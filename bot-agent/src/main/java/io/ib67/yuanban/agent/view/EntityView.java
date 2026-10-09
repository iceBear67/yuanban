package io.ib67.yuanban.agent.view;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 一个实体。
 *
 * @param type     实体类型名称，例如 {@code minecraft:zombie}
 * @param name     玩家的用户名或实体的自定义名称；都没有时为 null
 * @param distance 与本地玩家脚部位置的距离；不在世界中时为 -1
 * @param health   活体的生命值元数据；不是活体时为 null
 * @param vehicle  乘坐的实体；没有时为 null
 */
public record EntityView(int id, String uuid, String type, @Nullable String name, Vec3 position, double distance,
                         boolean onGround, @Nullable Float health, @Nullable Integer vehicle, List<Integer> passengers,
                         double width, double height) {
    public EntityView {
        passengers = List.copyOf(passengers);
    }
}
