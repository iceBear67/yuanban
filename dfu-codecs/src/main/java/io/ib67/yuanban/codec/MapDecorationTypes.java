package io.ib67.yuanban.codec;

import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.stream.Stream;

/**
 * 原版 26.2 {@code map_decoration_type} 内置注册表的 ID，按原版 {@code MapDecorationTypes} 的注册顺序。
 * 这个类同时用作 {@link RegistryIds#MAP_DECORATION_TYPE} 的类型参数，没有实例。
 */
// FIXME(Minestom M23): Minestom 中地图装饰的类型只是字符串，没有 map_decoration_type 注册表，这里列出原版 26.2 的 ID。
//  Minestom 提供这个注册表之后改为查询它。
public final class MapDecorationTypes {
    public static final List<Key> IDS = Stream.of(
            "player", "frame", "red_marker", "blue_marker", "target_x", "target_point", "player_off_map",
            "player_off_limits", "mansion", "monument",
            "banner_white", "banner_orange", "banner_magenta", "banner_light_blue", "banner_yellow", "banner_lime",
            "banner_pink", "banner_gray", "banner_light_gray", "banner_cyan", "banner_purple", "banner_blue",
            "banner_brown", "banner_green", "banner_red", "banner_black",
            "red_x", "village_desert", "village_plains", "village_savanna", "village_snowy", "village_taiga",
            "jungle_temple", "swamp_hut", "trial_chambers"
    ).map(Key::key).toList();

    private MapDecorationTypes() {
    }
}
