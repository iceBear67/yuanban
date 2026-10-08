package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.entity.ClientEntity;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.registry.SyncedRegistries;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.view.EntitySnapshot;
import io.ib67.yuanban.bot.view.HudSnapshot;
import io.ib67.yuanban.bot.view.PlayerSnapshot;
import io.ib67.yuanban.bot.view.StateReader;
import io.ib67.yuanban.bot.view.WindowSnapshot;
import io.ib67.yuanban.bot.view.WorldStateSnapshot;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.ServerLink;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 直接读取 bot 模型的 {@link StateReader}，只在调度线程上使用。
 */
final class StateReaderImpl implements StateReader {
    private final BotInstance bot;

    StateReaderImpl(BotInstance bot) {
        this.bot = bot;
    }

    private BotState state() {
        return bot.state();
    }

    @Override
    public BotStatus status() {
        return bot.status();
    }

    @Override
    public boolean loaded() {
        return state().loadTracker().loaded();
    }

    @Override
    public boolean movementSuspended() {
        return bot.movementSuspended();
    }

    @Override
    public long ticks() {
        return state().ticks();
    }

    @Override
    public @Nullable String serverBrand() {
        return state().serverBrand();
    }

    @Override
    public List<ServerLink> serverLinks() {
        return state().serverLinks();
    }

    @Override
    public List<Key> featureFlags() {
        return state().featureFlags();
    }

    @Override
    public Map<Key, Double> clocks() {
        return Map.copyOf(state().clocks().clocks());
    }

    @Override
    public @Nullable WorldInfo world() {
        return state().worldInfo();
    }

    @Override
    public WorldStateSnapshot worldState() {
        var world = state().world();
        var tickState = world.tickState();
        var ticking = new WorldStateSnapshot.Ticking(tickState.tickRate(), tickState.frozen(), tickState.stepTicks(),
                tickState.runsNormally());
        return new WorldStateSnapshot(world.worldAge(), world.difficulty(), world.defaultSpawn(),
                world.viewDistance(), world.simulationDistance(), ticking, world.border().snapshot(),
                world.weather().rainLevel(), world.weather().thunderLevel());
    }

    @Override
    public PlayerSnapshot player() {
        state().world();
        return state().player().snapshot();
    }

    @Override
    public Block block(int x, int y, int z) {
        return state().world().getBlock(x, y, z, Block.Getter.Condition.NONE);
    }

    @Override
    public @Nullable NbtMap blockEntity(int x, int y, int z) {
        return state().world().blockEntity(x, y, z);
    }

    @Override
    public Key biome(int x, int y, int z) {
        var id = state().world().biome(x, y, z);
        return state().registries().requireEntry(SyncedRegistries.BIOME, id);
    }

    @Override
    public boolean chunkLoaded(int chunkX, int chunkZ) {
        var world = state().worldOrNull();
        return world != null && world.chunkLoaded(chunkX, chunkZ);
    }

    @Override
    public @Nullable EntitySnapshot entity(int entityId) {
        var world = state().worldOrNull();
        if (world == null) {
            return null;
        }
        var entity = world.entities().get(entityId);
        return entity == null ? null : entity.snapshot();
    }

    @Override
    public List<EntitySnapshot> entities() {
        var world = state().worldOrNull();
        if (world == null) {
            return List.of();
        }
        return world.entities().all().stream().map(ClientEntity::snapshot).toList();
    }

    @Override
    public WindowSnapshot inventory() {
        return state().inventory().player().snapshot();
    }

    @Override
    public @Nullable WindowSnapshot openWindow() {
        var open = state().inventory().open();
        return open == null ? null : open.snapshot();
    }

    @Override
    public @Nullable ItemStack cursor() {
        return state().inventory().cursor();
    }

    @Override
    public @Nullable ItemStack equipment(EquipmentSlot slot) {
        return state().inventory().equipment(slot);
    }

    @Override
    public List<ChatLine> chat() {
        return state().chat().lines();
    }

    @Override
    public HudSnapshot hud() {
        return state().hud().snapshot();
    }
}
