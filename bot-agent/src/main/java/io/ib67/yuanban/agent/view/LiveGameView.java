package io.ib67.yuanban.agent.view;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.data.EntityTypeData;
import io.ib67.yuanban.bot.view.ChatKind;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.view.EntityMetaIndex;
import io.ib67.yuanban.bot.view.EntitySnapshot;
import io.ib67.yuanban.bot.view.HudSnapshot;
import io.ib67.yuanban.bot.view.StateReader;
import io.ib67.yuanban.bot.view.WindowSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 把 bot 的模型转换为 {@link GameView}。只能在 {@link Bot#inspect} 的读取函数或 observer 回调中使用。
 */
public final class LiveGameView implements GameView {
    /**
     * 实体自定义名称在元数据中的索引（原版 {@code Entity.DATA_CUSTOM_NAME}）
     */
    private static final int CUSTOM_NAME_INDEX = 2;

    private final Bot bot;
    private final StateReader state;
    private @Nullable HudSnapshot hud;

    public LiveGameView(Bot bot, StateReader state) {
        this.bot = bot;
        this.state = state;
    }

    @Override
    public BotStatusView status() {
        var reason = bot.disconnectReason();
        var failure = bot.failure();
        return new BotStatusView(state.status().name(), state.loaded(), state.world() != null, state.movementSuspended(),
                reason == null ? null : plain(reason), failure == null ? null : failure.toString(), state.ticks());
    }

    @Override
    public @Nullable PlayerView player() {
        var world = state.world();
        if (world == null) {
            return null;
        }
        var player = state.player();
        var position = player.position();
        var effects = player.effects().values().stream()
                .map(effect -> new PlayerView.EffectView(key(effect.effect().name()), effect.amplifier(), effect.duration()))
                .sorted(Comparator.comparing(PlayerView.EffectView::effect))
                .toList();
        return new PlayerView(player.entityId(), player.uuid().toString(), player.username(), Vec3.of(position),
                position.yaw(), position.pitch(), Vec3.of(player.velocity()), player.onGround(), player.health(),
                player.food(), player.saturation(), player.dead(), player.gameMode().name(), player.abilities().flying(),
                player.heldSlot(), player.level(), effects, world.dimension().asString());
    }

    @Override
    public @Nullable WorldStatusView world() {
        var world = state.world();
        if (world == null) {
            return null;
        }
        var worldState = state.worldState();
        var difficulty = worldState.difficulty();
        return new WorldStatusView(world.dimension().asString(), world.epoch(), world.cause().name(), worldState.worldAge(),
                worldState.rainLevel(), worldState.thunderLevel(), difficulty == null ? null : difficulty.name(),
                worldState.ticking().frozen());
    }

    @Override
    public BlockView block(BlockPos pos) {
        var block = state.block(pos.x(), pos.y(), pos.z());
        return new BlockView(pos, block.name(), block.properties(), block.air(), block.solid(), block.blocksMotion(),
                block.liquid());
    }

    @Override
    public boolean chunkLoaded(int chunkX, int chunkZ) {
        return state.chunkLoaded(chunkX, chunkZ);
    }

    @Override
    public List<EntityView> entities() {
        if (state.world() == null) {
            return List.of();
        }
        var self = state.player();
        var origin = Vec3.of(self.position());
        var result = new ArrayList<EntityView>();
        for (var entity : state.entities()) {
            if (entity.entityId() != self.entityId()) {
                result.add(convert(entity, origin));
            }
        }
        result.sort(Comparator.comparingDouble(EntityView::distance));
        return result;
    }

    @Override
    public @Nullable EntityView entity(int id) {
        var entity = state.entity(id);
        if (entity == null) {
            return null;
        }
        var origin = state.world() == null ? null : Vec3.of(state.player().position());
        return convert(entity, origin);
    }

    @Override
    public InventoryView inventory() {
        var window = state.inventory();
        var heldSlot = state.world() == null ? 0 : state.player().heldSlot();
        var open = state.openWindow();
        return new InventoryView(heldSlot, items(window), item(36 + heldSlot, window.slot(36 + heldSlot)),
                item(45, window.slot(45)), item(-1, state.cursor()), open == null ? null : windowView(open));
    }

    @Override
    public List<ChatView> chat(int limit) {
        var lines = state.chat();
        return lines.subList(Math.max(0, lines.size() - limit), lines.size()).stream().map(LiveGameView::chatView).toList();
    }

    public static ChatView chatView(ChatLine line) {
        return new ChatView(line.kind().name(), line.sender() == null ? null : line.sender().toString(),
                line.senderName() == null ? null : plain(line.senderName()), line.plainText(),
                line.kind() == ChatKind.PLAYER ? "UNVERIFIED" : "NONE");
    }

    public static @Nullable ItemView item(int slot, @Nullable ItemStack stack) {
        return stack == null ? null : new ItemView(slot, ItemNames.name(stack.getId()), stack.getAmount());
    }

    public static String entityType(EntityType type) {
        return key(type.name());
    }

    private static InventoryView.WindowView windowView(WindowSnapshot window) {
        return new InventoryView.WindowView(window.windowId(), window.type() == null ? null : window.type().name(),
                plain(window.title()), window.slots().size(), items(window));
    }

    private static List<ItemView> items(WindowSnapshot window) {
        var items = new ArrayList<ItemView>();
        var slots = window.slots();
        for (var slot = 0; slot < slots.size(); slot++) {
            var view = item(slot, slots.get(slot));
            if (view != null) {
                items.add(view);
            }
        }
        return items;
    }

    private EntityView convert(EntitySnapshot entity, @Nullable Vec3 origin) {
        var position = Vec3.of(entity.position());
        Float health = EntityTypeData.living(entity.type()) ? entity.meta(EntityMetaIndex.LIVING_HEALTH) : null;
        return new EntityView(entity.entityId(), entity.uuid().toString(), entityType(entity.type()), name(entity), position,
                origin == null ? -1 : origin.distance(position), entity.onGround(), health, entity.vehicle(),
                entity.passengers(), entity.boundingBox().width(), entity.boundingBox().height());
    }

    private @Nullable String name(EntitySnapshot entity) {
        if (entity.type() == EntityType.PLAYER) {
            if (hud == null) {
                hud = state.hud();
            }
            var entry = hud.playerList().get(entity.uuid());
            if (entry != null) {
                return entry.username();
            }
        }
        var custom = entity.metadata().get(CUSTOM_NAME_INDEX);
        if (custom != null && custom.getValue() instanceof Optional<?> optional
                && optional.orElse(null) instanceof Component component) {
            return plain(component);
        }
        return null;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String key(String enumName) {
        return "minecraft:" + enumName.toLowerCase(Locale.ROOT);
    }
}
