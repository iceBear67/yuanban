package io.ib67.yuanban.agent.bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.tool.ObserverSpec;
import io.ib67.yuanban.agent.view.BlockPos;
import io.ib67.yuanban.agent.view.LiveGameView;
import io.ib67.yuanban.agent.view.Vec3;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Region;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.builtin.BlockAtObserver;
import io.ib67.yuanban.bot.observe.builtin.BlockBrokenObserver;
import io.ib67.yuanban.bot.observe.builtin.BlockChangesObserver;
import io.ib67.yuanban.bot.observe.builtin.ChatObserver;
import io.ib67.yuanban.bot.observe.builtin.DamageObserver;
import io.ib67.yuanban.bot.observe.builtin.DamageRecord;
import io.ib67.yuanban.bot.observe.builtin.FirstObserver;
import io.ib67.yuanban.bot.observe.builtin.MovementCriteria;
import io.ib67.yuanban.bot.observe.builtin.MovementObserver;
import io.ib67.yuanban.bot.observe.builtin.ObservationObserver;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import io.ib67.yuanban.bot.observe.builtin.ParticleObserver;
import io.ib67.yuanban.bot.observe.builtin.SoundObserver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * 把 {@link ObserverSpec} 映射到 bot 的 observer，以及 observer 关闭之后读取结论的函数。
 */
final class LiveObservers {
    private LiveObservers() {
    }

    /**
     * @param observer 交给 {@code Bot.observeDuring} 的 observer
     * @param conclude 在 observer 关闭之后、任意线程上读取结论
     */
    record Binding(Observer observer, Function<Observer, JsonElement> conclude) {
    }

    static Binding bind(ObserverSpec spec) {
        return switch (spec) {
            case ObserverSpec.MovementReached reached -> {
                var observer = new ReachObserver(reached.target(), reached.distance());
                yield new Binding(observer, _ -> observer.conclusion());
            }
            case ObserverSpec.BlockBroken broken -> new Binding(Observers.blockBroken(broken.pos().toVec()), observer -> {
                var typed = (BlockBrokenObserver) observer;
                return Json.object("broken", typed.broken(), "original", block(typed.original()), "changedTo", block(typed.changedTo()));
            });
            case ObserverSpec.BlockAt at -> new Binding(Observers.blockAt(at.pos().toVec(), block -> at.names().contains(block.name())), observer -> {
                var typed = (BlockAtObserver) observer;
                var history = new JsonArray();
                typed.history().forEach(change -> history.add(change(change)));
                return Json.object("met", typed.met(), "current", block(typed.current()), "changes", history);
            });
            case ObserverSpec.BlockChanges changes -> new Binding(
                    Observers.blockChanges(region(changes.min(), changes.max())).limit(changes.limit()), observer -> {
                        var typed = (BlockChangesObserver) observer;
                        var list = new JsonArray();
                        typed.changes().forEach(change -> list.add(change(change)));
                        return Json.object("changes", list, "truncated", typed.truncated());
                    });
            case ObserverSpec.Damage damage -> new Binding(Observers.damageTaken().limit(damage.limit()), observer -> {
                var typed = (DamageObserver) observer;
                var list = new JsonArray();
                for (var record : typed.damages()) {
                    list.add(switch (record) {
                        case DamageRecord.Event(var event) -> Json.object("kind", "event", "type", event.type().asString(),
                                "sourceId", event.sourceId(), "directSourceId", event.directSourceId(),
                                "sourcePos", event.sourcePos() == null ? null : Vec3.of(event.sourcePos()));
                        case DamageRecord.HealthDrop(var change) -> Json.object("kind", "healthDrop",
                                "oldHealth", change.oldHealth(), "health", change.health());
                    });
                }
                return Json.object("damages", list, "truncated", typed.truncated());
            });
            case ObserverSpec.Chat chat -> new Binding(Observers.chatMessages(line -> chat.contains() == null
                    || line.plainText().contains(chat.contains())).limit(chat.limit()), observer -> {
                var typed = (ChatObserver) observer;
                return Json.object("lines", Json.array(typed.lines().stream().map(LiveGameView::chatView).toList()),
                        "truncated", typed.truncated());
            });
            case ObserverSpec.EntityMovement movement -> {
                var bot = movement.hasCriteria()
                        ? Observers.movementOf(entity -> matches(movement, entity.entityId(), LiveGameView.entityType(entity.type())), criteria(movement))
                        : Observers.movementOf(entity -> matches(movement, entity.entityId(), LiveGameView.entityType(entity.type())));
                yield new Binding(bot, observer -> {
                    var typed = (MovementObserver) observer;
                    var tracks = new JsonArray();
                    for (var track : typed.tracks()) {
                        tracks.add(Json.object("entityId", track.entityId(), "type", LiveGameView.entityType(track.type()),
                                "start", Vec3.of(track.start()), "current", Vec3.of(track.current()), "removed", track.removed()));
                    }
                    var result = Json.object("tracks", tracks);
                    if (movement.hasCriteria()) {
                        result.addProperty("met", typed.met());
                        result.add("metBy", Json.array(typed.metBy().stream().map(track -> track.entityId()).toList()));
                    }
                    return result;
                });
            }
            case ObserverSpec.Pickups pickups -> {
                var observer = new PickupObserver(pickups.limit());
                yield new Binding(observer, _ -> observer.conclusion());
            }
            case ObserverSpec.Slots slots -> new Binding(Observers.observations(Observation.SlotChanged.class).limit(slots.limit()), observer -> {
                @SuppressWarnings("unchecked")
                var typed = (ObservationObserver<Observation.SlotChanged>) observer;
                var list = new JsonArray();
                for (var change : typed.observations()) {
                    list.add(Json.object("windowId", change.windowId(), "slot", change.slot(),
                            "old", LiveGameView.item(change.slot(), change.old()), "now", LiveGameView.item(change.slot(), change.now())));
                }
                return Json.object("changes", list, "truncated", typed.truncated());
            });
            case ObserverSpec.WindowOpened _ -> new Binding(Observers.first(Observation.WindowOpened.class, _ -> true), observer -> {
                @SuppressWarnings("unchecked")
                var typed = (FirstObserver<Observation.WindowOpened>) observer;
                var matched = typed.matched();
                if (matched.isEmpty()) {
                    return Json.object("opened", false);
                }
                var window = matched.get();
                return Json.object("opened", true, "windowId", window.windowId(),
                        "type", window.type() == null ? null : window.type().name(),
                        "title", PlainTextComponentSerializer.plainText().serialize(window.title()));
            });
            case ObserverSpec.Sounds sounds -> new Binding(Observers.sounds(region(sounds.min(), sounds.max())).limit(sounds.limit()), observer -> {
                var typed = (SoundObserver) observer;
                var list = new JsonArray();
                for (var sound : typed.sounds()) {
                    list.add(Json.object("sound", sound.sound().getName(), "category", sound.category().name(),
                            "position", Vec3.of(sound.position()), "volume", sound.volume(), "pitch", sound.pitch()));
                }
                return Json.object("sounds", list, "truncated", typed.truncated());
            });
            case ObserverSpec.Particles particles -> new Binding(Observers.particlesWithin(region(particles.min(), particles.max())).limit(particles.limit()), observer -> {
                var typed = (ParticleObserver) observer;
                var list = new JsonArray();
                for (var particle : typed.particles()) {
                    list.add(Json.object("particle", particle.particle().getType().name(), "center", Vec3.of(particle.center()),
                            "count", particle.count()));
                }
                return Json.object("particles", list, "truncated", typed.truncated());
            });
        };
    }

    private static boolean matches(ObserverSpec.EntityMovement movement, int entityId, String type) {
        return (movement.id() == null || movement.id() == entityId) && (movement.type() == null || movement.type().equals(type));
    }

    private static MovementCriteria criteria(ObserverSpec.EntityMovement movement) {
        var criteria = MovementCriteria.create().entities(movement.count());
        if (movement.atLeast() != null) {
            criteria = criteria.atLeast(movement.atLeast());
        }
        if (movement.towards() != null) {
            criteria = criteria.towards(movement.towards().toVec(), movement.angle());
        }
        if (movement.intoMin() != null && movement.intoMax() != null) {
            criteria = criteria.into(region(movement.intoMin(), movement.intoMax()));
        }
        return criteria;
    }

    private static Region region(Vec3 min, Vec3 max) {
        return new Region(min.toVec(), max.toVec());
    }

    private static @Nullable JsonElement block(@Nullable Block block) {
        return block == null ? null : Json.object("name", block.name(), "properties", block.properties());
    }

    private static JsonObject change(Observation.BlockChanged change) {
        return Json.object("pos", BlockPos.of(change.pos()), "old", block(change.old()), "now", block(change.now()));
    }

    /**
     * movementReached：记录玩家位置与目标的最近距离与最后距离
     */
    private static final class ReachObserver implements Observer {
        private final Vec3 target;
        private final double distance;
        private volatile @Nullable Vec3 last;
        private volatile double minDistance = Double.POSITIVE_INFINITY;

        private ReachObserver(Vec3 target, double distance) {
            this.target = target;
            this.distance = distance;
        }

        @Override
        public Subscription subscription() {
            return Subscription.of(Observation.PlayerMoved.class, Observation.PlayerTeleported.class);
        }

        @Override
        public void onStart(ObserverContext context) {
            if (context.world() != null) {
                update(context.state().player().position());
            }
        }

        @Override
        public void onObservation(Observation observation, ObserverContext context) {
            switch (observation) {
                case Observation.PlayerMoved moved -> update(moved.to());
                case Observation.PlayerTeleported teleported -> update(teleported.to());
                default -> {
                }
            }
        }

        private void update(Point position) {
            var now = Vec3.of(position);
            last = now;
            minDistance = Math.min(minDistance, now.distance(target));
        }

        JsonElement conclusion() {
            var position = last;
            if (position == null) {
                return Json.object("reached", false, "finalPosition", null, "distance", null, "minDistance", null);
            }
            var finalDistance = position.distance(target);
            return Json.object("reached", finalDistance <= distance, "finalPosition", position, "distance", finalDistance,
                    "minDistance", minDistance);
        }
    }

    /**
     * pickups：本地玩家捡起的物品
     */
    private static final class PickupObserver implements Observer {
        private final int limit;
        private final JsonArray pickups = new JsonArray();
        private volatile int total;
        private volatile boolean truncated;

        private PickupObserver(int limit) {
            this.limit = limit;
        }

        @Override
        public Subscription subscription() {
            return Subscription.of(Observation.ItemPickedUp.class);
        }

        @Override
        public void onObservation(Observation observation, ObserverContext context) {
            if (observation instanceof Observation.ItemPickedUp(int collected, int collector, int count)
                    && context.world() != null && collector == context.state().player().entityId()) {
                total += count;
                synchronized (pickups) {
                    if (pickups.size() < limit) {
                        var entity = context.state().entity(collected);
                        pickups.add(Json.object("collectedId", collected, "count", count,
                                "type", entity == null ? null : LiveGameView.entityType(entity.type())));
                    } else {
                        truncated = true;
                    }
                }
            }
        }

        JsonElement conclusion() {
            synchronized (pickups) {
                return Json.object("count", total, "pickups", pickups.deepCopy(), "truncated", truncated);
            }
        }
    }
}
