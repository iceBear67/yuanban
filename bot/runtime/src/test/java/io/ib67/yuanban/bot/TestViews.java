package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.observe.WorldInfo;
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
 * 测试中按 {@link StateReader} 的方法逐个读取 bot 的模型：每次调用都是一次 {@link Bot#inspect}。
 * 需要多项读取在同一时刻完成时直接使用 {@code inspect}。
 */
public final class TestViews implements StateReader {
    private final Bot bot;

    private TestViews(Bot bot) {
        this.bot = bot;
    }

    public static TestViews of(Bot bot) {
        return new TestViews(bot);
    }

    @Override
    public BotStatus status() {
        return bot.status();
    }

    @Override
    public boolean loaded() {
        return bot.inspect(StateReader::loaded);
    }

    @Override
    public boolean movementSuspended() {
        return bot.inspect(StateReader::movementSuspended);
    }

    @Override
    public long ticks() {
        return bot.inspect(StateReader::ticks);
    }

    @Override
    public @Nullable String serverBrand() {
        return bot.inspect(StateReader::serverBrand);
    }

    @Override
    public List<ServerLink> serverLinks() {
        return bot.inspect(StateReader::serverLinks);
    }

    @Override
    public List<Key> featureFlags() {
        return bot.inspect(StateReader::featureFlags);
    }

    @Override
    public Map<Key, Double> clocks() {
        return bot.inspect(StateReader::clocks);
    }

    @Override
    public @Nullable WorldInfo world() {
        return bot.inspect(StateReader::world);
    }

    @Override
    public WorldStateSnapshot worldState() {
        return bot.inspect(StateReader::worldState);
    }

    @Override
    public PlayerSnapshot player() {
        return bot.inspect(StateReader::player);
    }

    @Override
    public Block block(int x, int y, int z) {
        return bot.inspect(state -> state.block(x, y, z));
    }

    @Override
    public @Nullable NbtMap blockEntity(int x, int y, int z) {
        return bot.inspect(state -> state.blockEntity(x, y, z));
    }

    @Override
    public Key biome(int x, int y, int z) {
        return bot.inspect(state -> state.biome(x, y, z));
    }

    @Override
    public boolean chunkLoaded(int chunkX, int chunkZ) {
        return bot.inspect(state -> state.chunkLoaded(chunkX, chunkZ));
    }

    @Override
    public @Nullable EntitySnapshot entity(int entityId) {
        return bot.inspect(state -> state.entity(entityId));
    }

    @Override
    public List<EntitySnapshot> entities() {
        return bot.inspect(StateReader::entities);
    }

    @Override
    public WindowSnapshot inventory() {
        return bot.inspect(StateReader::inventory);
    }

    @Override
    public @Nullable WindowSnapshot openWindow() {
        return bot.inspect(StateReader::openWindow);
    }

    @Override
    public @Nullable ItemStack cursor() {
        return bot.inspect(StateReader::cursor);
    }

    @Override
    public @Nullable ItemStack equipment(EquipmentSlot slot) {
        return bot.inspect(state -> state.equipment(slot));
    }

    @Override
    public List<ChatLine> chat() {
        return bot.inspect(StateReader::chat);
    }

    @Override
    public HudSnapshot hud() {
        return bot.inspect(StateReader::hud);
    }
}
