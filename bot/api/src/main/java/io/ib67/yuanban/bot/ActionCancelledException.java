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
     * 寻路计算抛出异常
     */
    public static final String PATH_FAILED = "寻路计算失败";
    /**
     * 观察者因满足条件（距离、与动作绑定的其它条件）触发了这个动作的取消
     */
    public static final String BY_OBSERVER = "被观察者取消";
    /**
     * 移动连续被阻挡
     */
    public static final String BLOCKED = "移动被阻挡";
    /**
     * {@code jumpTo} 起跳后长时间没有着地
     */
    public static final String AIR_TIMEOUT = "起跳后长时间没有着地";
    /**
     * 目标实体不在模型中
     */
    public static final String NO_ENTITY = "实体不存在";
    /**
     * 移动范围涉及未加载的区块；{@code moveTo} 找不到路径（或者只能走到最近点）并且寻路时目标所在区块未加载；
     * 或者手部交互动作（挖掘、对方块使用物品）读取的方块所在区块未加载
     */
    public static final String CHUNK_NOT_LOADED = "区块未加载";
    /**
     * {@code attack} 发送时目标不在原版客户端的攻击距离内
     */
    public static final String OUT_OF_RANGE = "超出攻击距离";
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
