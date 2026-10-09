package io.ib67.yuanban.agent.message;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 一个 agent 的消息编号与序号。全部 run 共用一个计数，所以子计划自然继承序号。
 */
public final class MessageFactory {
    private final AtomicLong sequence = new AtomicLong();
    private final InstantSource clock;

    public MessageFactory(InstantSource clock) {
        this.clock = clock;
    }

    public Envelope create(String run, MessageType type, String source, @Nullable String topic, JsonObject payload) {
        return create(run, type, Priority.NORMAL, source, topic, payload, null, null);
    }

    public Envelope create(String run, MessageType type, Priority priority, String source, @Nullable String topic,
                           JsonObject payload, @Nullable String dedupeKey, @Nullable String replyTo) {
        var seq = sequence.incrementAndGet();
        return new Envelope("msg-" + seq, run, seq, Envelope.SCHEMA_VERSION, type, priority, source, topic, payload,
                requiresAck(type), dedupeKey, replyTo, clock.millis());
    }

    /**
     * 最近分配的序号
     */
    public long lastSequence() {
        return sequence.get();
    }

    private static boolean requiresAck(MessageType type) {
        return type.delivery() == MessageType.Delivery.DURABLE
                || type == MessageType.GOAL || type == MessageType.STOP || type == MessageType.POLICY;
    }
}
