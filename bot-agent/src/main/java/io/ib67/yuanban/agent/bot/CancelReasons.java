package io.ib67.yuanban.agent.bot;

import io.ib67.yuanban.bot.ActionCancelledException;

import java.util.Map;

/**
 * 把 {@link ActionCancelledException} 的取消原因（中文常量）换成稳定的名称。
 */
public final class CancelReasons {
    public static final String BY_USER = "BY_USER";
    public static final String NO_PATH = "NO_PATH";
    public static final String BLOCKED = "BLOCKED";
    public static final String NO_ENTITY = "NO_ENTITY";
    public static final String CHUNK_NOT_LOADED = "CHUNK_NOT_LOADED";
    public static final String OUT_OF_RANGE = "OUT_OF_RANGE";
    public static final String WORLD_REBUILT = "WORLD_REBUILT";
    public static final String PLAYER_DIED = "PLAYER_DIED";

    private static final Map<String, String> NAMES = Map.of(
            ActionCancelledException.BY_USER, BY_USER,
            ActionCancelledException.NO_PATH, NO_PATH,
            ActionCancelledException.BLOCKED, BLOCKED,
            ActionCancelledException.NO_ENTITY, NO_ENTITY,
            ActionCancelledException.CHUNK_NOT_LOADED, CHUNK_NOT_LOADED,
            ActionCancelledException.OUT_OF_RANGE, OUT_OF_RANGE,
            ActionCancelledException.WORLD_REBUILT, WORLD_REBUILT,
            ActionCancelledException.PLAYER_DIED, PLAYER_DIED);

    private CancelReasons() {
    }

    /**
     * @throws IllegalArgumentException 不是 {@link ActionCancelledException} 中的常量
     */
    public static String name(ActionCancelledException exception) {
        var name = NAMES.get(exception.getMessage());
        if (name == null) {
            throw new IllegalArgumentException("unknown cancel reason " + exception.getMessage());
        }
        return name;
    }
}
