package io.ib67.yuanban.agent.message;

import io.ib67.yuanban.agent.json.Json;
import org.junit.jupiter.api.Test;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailboxTest {
    private final MessageFactory messages = new MessageFactory(InstantSource.system());
    private final List<Envelope> delivered = new ArrayList<>();
    private final List<Envelope> dropped = new ArrayList<>();
    private final List<Envelope> overflowed = new ArrayList<>();

    private Mailbox mailbox(int capacity, int droppable) {
        return new Mailbox(capacity, droppable, delivered::add, dropped::add, overflowed::add);
    }

    private Envelope message(MessageType type, String text, String dedupeKey) {
        return messages.create("run-1", type, Priority.NORMAL, "kernel", null, Json.object("text", text), dedupeKey, null);
    }

    @Test
    void orderedMessagesKeepTheirOrder() {
        var mailbox = mailbox(10, 10);
        mailbox.offer(message(MessageType.GOAL, "a", null));
        mailbox.offer(message(MessageType.POLICY, "b", null));
        mailbox.offer(message(MessageType.GOAL, "c", null));
        assertEquals("a", mailbox.poll(Mailbox.Filter.ANY).payload().get("text").getAsString());
        assertEquals("b", mailbox.poll(Mailbox.Filter.ANY).payload().get("text").getAsString());
        assertEquals("c", mailbox.poll(Mailbox.Filter.ANY).payload().get("text").getAsString());
        assertEquals(3, delivered.size());
    }

    @Test
    void focusWithoutDedupeKeyReplacesTheQueuedFocus() {
        var mailbox = mailbox(10, 10);
        mailbox.offer(message(MessageType.FOCUS, "old", null));
        mailbox.offer(message(MessageType.POLICY, "policy", null));
        mailbox.offer(message(MessageType.FOCUS, "new", null));
        var focus = new Mailbox.Filter(Set.of(MessageType.FOCUS), null);
        assertEquals("new", mailbox.poll(focus).payload().get("text").getAsString());
        assertNull(mailbox.poll(focus));
        assertEquals("policy", mailbox.poll(Mailbox.Filter.ANY).payload().get("text").getAsString());
        assertEquals(1, dropped.size());
    }

    @Test
    void anOlderMessageWithTheSameDedupeKeyIsDropped() {
        var mailbox = mailbox(10, 10);
        var older = message(MessageType.POLICY, "older", "speed");
        var newer = message(MessageType.POLICY, "newer", "speed");
        assertEquals(Mailbox.Offer.QUEUED, mailbox.offer(newer));
        assertEquals(Mailbox.Offer.STALE, mailbox.offer(older));
        assertEquals("newer", mailbox.poll(Mailbox.Filter.ANY).payload().get("text").getAsString());
        assertNull(mailbox.poll(Mailbox.Filter.ANY));
    }

    @Test
    void aNewerMessageWithTheSameDedupeKeyReplacesTheQueuedOne() {
        var mailbox = mailbox(10, 10);
        mailbox.offer(message(MessageType.POLICY, "first", "speed"));
        mailbox.offer(message(MessageType.POLICY, "second", "speed"));
        assertEquals("second", mailbox.poll(Mailbox.Filter.ANY).payload().get("text").getAsString());
        assertNull(mailbox.poll(Mailbox.Filter.ANY));
    }

    @Test
    void waitersReceiveMatchingMessagesInOrder() {
        var mailbox = mailbox(10, 10);
        var stopWaiter = mailbox.next(new Mailbox.Filter(Set.of(MessageType.STOP), null));
        var anyWaiter = mailbox.next(Mailbox.Filter.ANY);
        assertEquals(Mailbox.Offer.DELIVERED, mailbox.offer(message(MessageType.GOAL, "goal", null)));
        assertTrue(anyWaiter.isDone());
        assertFalse(stopWaiter.isDone());
        mailbox.offer(message(MessageType.STOP, "stop", null));
        assertEquals(MessageType.STOP, stopWaiter.join().type());
    }

    @Test
    void stopSatisfiesAReplyFilter() {
        var mailbox = mailbox(10, 10);
        var waiter = mailbox.next(new Mailbox.Filter(Set.of(MessageType.FOCUS, MessageType.STOP), "msg-99"));
        mailbox.offer(message(MessageType.FOCUS, "unrelated", null));
        assertFalse(waiter.isDone());
        mailbox.offer(message(MessageType.STOP, "stop", null));
        assertEquals(MessageType.STOP, waiter.join().type());
    }

    @Test
    void signalsBeyondTheirCapacityDropTheOldest() {
        var mailbox = mailbox(10, 2);
        mailbox.offer(message(MessageType.SIGNAL, "1", null));
        mailbox.offer(message(MessageType.SIGNAL, "2", null));
        mailbox.offer(message(MessageType.SIGNAL, "3", null));
        assertEquals("2", mailbox.poll(Mailbox.Filter.ANY).payload().get("text").getAsString());
        assertEquals("3", mailbox.poll(Mailbox.Filter.ANY).payload().get("text").getAsString());
        assertEquals(1, dropped.size());
    }

    @Test
    void controlMessagesBeyondCapacityOverflow() {
        var mailbox = mailbox(1, 2);
        mailbox.offer(message(MessageType.GOAL, "a", null));
        assertEquals(Mailbox.Offer.OVERFLOW, mailbox.offer(message(MessageType.GOAL, "b", null)));
        assertEquals(1, overflowed.size());
    }

    @Test
    void closingCompletesWaitersWithNull() {
        var mailbox = mailbox(10, 10);
        var waiter = mailbox.next(Mailbox.Filter.ANY);
        mailbox.close();
        assertNull(waiter.join());
        assertEquals(Mailbox.Offer.CLOSED, mailbox.offer(message(MessageType.GOAL, "late", null)));
    }

    @Test
    void cursorIsTheLastDeliveredSequence() {
        var mailbox = mailbox(10, 10);
        var first = message(MessageType.GOAL, "a", null);
        var second = message(MessageType.GOAL, "b", null);
        mailbox.offer(first);
        mailbox.offer(second);
        mailbox.poll(Mailbox.Filter.ANY);
        assertEquals(first.sequence(), mailbox.cursor());
    }
}
