package io.ib67.yuanban.bot;

/**
 * 动作被取消时 {@link Action#future()} 完成使用的异常，异常信息为取消原因。
 */
public final class ActionCancelledException extends RuntimeException {
    /**
     * 用户调用了 {@link Action#cancel()}
     */
    public static final String BY_USER = "被用户取消";
    /**
     * 寻路找不到到达目标的路径
     */
    public static final String NO_PATH = "找不到路径";
    /**
     * 移动连续被阻挡
     */
    public static final String BLOCKED = "移动被阻挡";
    /**
     * 目标实体不在模型中
     */
    public static final String NO_ENTITY = "实体不存在";
    /**
     * 移动范围涉及未加载的区块
     */
    public static final String CHUNK_NOT_LOADED = "区块未加载";
    /**
     * 世界重建，移动与手部交互队列中在上一个世界里提交的动作被取消：离开世界（Start Configuration、Transfer），
     * 或者在世界中收到 Login (play) 或 Respawn。不在任何世界中时收到的 Login (play) 不取消这期间提交的动作
     */
    public static final String WORLD_REBUILT = "世界重建";
    /**
     * 玩家死亡，队列被清空
     */
    public static final String PLAYER_DIED = "玩家死亡";

    public ActionCancelledException(String reason) {
        super(reason);
    }
}
