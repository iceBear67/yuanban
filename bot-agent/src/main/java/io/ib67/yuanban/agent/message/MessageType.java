package io.ib67.yuanban.agent.message;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息类型。{@link Direction#CONTROL} 由 AgentKernel、TUI、游戏内输入、PlanBundle 与 ToolGateway 发给脚本；
 * {@link Direction#EVENT} 由脚本发出，经过 ScriptEventBus 交给 AgentKernel 或所属的 PlanBundle。
 */
public enum MessageType {
    /**
     * 用户目标，有序
     */
    GOAL(Direction.CONTROL, Delivery.ORDERED),
    /**
     * 希望脚本关注的风险或事件；没有 dedupeKey 时后到的替换还没有投递的旧消息
     */
    FOCUS(Direction.CONTROL, Delivery.LATEST_WINS),
    /**
     * 预算、权限与行为策略的更新，有序
     */
    POLICY(Direction.CONTROL, Delivery.ORDERED),
    /**
     * 停止脚本并取消它的动作，有序
     */
    STOP(Direction.CONTROL, Delivery.ORDERED),
    /**
     * 对脚本提问的回答
     */
    USER_REPLY(Direction.CONTROL, Delivery.ORDERED),
    /**
     * PlanBundle 暂停了 primary：它改变世界的工具调用等到恢复
     */
    PAUSE(Direction.CONTROL, Delivery.ORDERED),
    RESUME(Direction.CONTROL, Delivery.ORDERED),
    /**
     * primary 结束，supervisor 处理完最后一批事件后返回
     */
    DRAIN(Direction.CONTROL, Delivery.ORDERED),
    /**
     * capability lease 的授予、拒绝与收回
     */
    LEASE(Direction.CONTROL, Delivery.ORDERED),
    /**
     * 发给 supervisor 的低成本信号：位置、伤害、聊天、世界重建、区块、primary 的工具调用与进度
     */
    SIGNAL(Direction.CONTROL, Delivery.DROPPABLE),
    /**
     * 断线、bot 结束、世界重建与玩家死亡
     */
    BOT_EVENT(Direction.CONTROL, Delivery.ORDERED),

    PROGRESS(Direction.EVENT, Delivery.DROPPABLE),
    ATTENTION(Direction.EVENT, Delivery.DURABLE),
    RESULT(Direction.EVENT, Delivery.ORDERED),
    FAULT(Direction.EVENT, Delivery.DURABLE),
    CHECKPOINT(Direction.EVENT, Delivery.DURABLE),
    QUESTION(Direction.EVENT, Delivery.DURABLE),
    /**
     * background plan 的观察：只写入 trace 与它自己的记忆，不唤醒模型
     */
    OBSERVATION(Direction.EVENT, Delivery.DROPPABLE),
    /**
     * background plan 认为值得注意的事情，转为带证据引用的 attention
     */
    INTEREST(Direction.EVENT, Delivery.DURABLE),
    /**
     * background plan 提议的目标，不直接替换用户目标
     */
    SUGGESTION(Direction.EVENT, Delivery.ORDERED),
    /**
     * background plan 请求短暂取得 capability lease
     */
    ACTION_REQUEST(Direction.EVENT, Delivery.ORDERED),
    /**
     * supervisor 暂停 primary 并启动 preflight
     */
    NEED_PREFLIGHT(Direction.EVENT, Delivery.ORDERED);

    private static final Map<String, MessageType> BY_WIRE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(MessageType::wire, Function.identity()));

    private final Direction direction;
    private final Delivery delivery;

    MessageType(Direction direction, Delivery delivery) {
        this.direction = direction;
        this.delivery = delivery;
    }

    public Direction direction() {
        return direction;
    }

    public Delivery delivery() {
        return delivery;
    }

    /**
     * 线上名称：小写，单词之间用下划线连接
     */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @throws IllegalArgumentException 没有这个类型
     */
    public static MessageType fromWire(String wire) {
        var type = BY_WIRE.get(wire);
        if (type == null) {
            throw new IllegalArgumentException("unknown message type " + wire);
        }
        return type;
    }

    public enum Direction {
        CONTROL,
        EVENT
    }

    /**
     * 投递保证
     */
    public enum Delivery {
        /**
         * 按序号投递，不丢弃
         */
        ORDERED,
        /**
         * 没有 dedupeKey 时，后到的同类消息替换还没有投递的旧消息
         */
        LATEST_WINS,
        /**
         * 可以合并或丢弃
         */
        DROPPABLE,
        /**
         * 先持久化到 trace，再投递，接收方必须确认
         */
        DURABLE
    }
}
