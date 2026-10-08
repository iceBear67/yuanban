package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.view.BotView;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.view.EntitySnapshot;
import io.ib67.yuanban.bot.view.HudSnapshot;
import io.ib67.yuanban.bot.view.PlayerSnapshot;
import io.ib67.yuanban.bot.view.WindowSnapshot;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 每个方法作为查询任务交给调度线程。
 */
final class BotViewImpl implements BotView {
    private final BotInstance bot;

    BotViewImpl(BotInstance bot) {
        this.bot = bot;
    }

    @Override
    public BotStatus status() {
        return bot.status();
    }

    @Override
    public boolean loaded() {
        return bot.query(() -> bot.reader().loaded());
    }

    @Override
    public boolean movementSuspended() {
        return bot.query(() -> bot.reader().movementSuspended());
    }

    @Override
    public long ticks() {
        return bot.query(() -> bot.reader().ticks());
    }

    @Override
    public @Nullable WorldInfo world() {
        return bot.query(() -> bot.reader().world());
    }

    @Override
    public PlayerSnapshot player() {
        return bot.query(() -> bot.reader().player());
    }

    @Override
    public Block block(int x, int y, int z) {
        return bot.query(() -> bot.reader().block(x, y, z));
    }

    @Override
    public @Nullable NbtMap blockEntity(int x, int y, int z) {
        return bot.query(() -> bot.reader().blockEntity(x, y, z));
    }

    @Override
    public boolean chunkLoaded(int chunkX, int chunkZ) {
        return bot.query(() -> bot.reader().chunkLoaded(chunkX, chunkZ));
    }

    @Override
    public @Nullable EntitySnapshot entity(int entityId) {
        return bot.query(() -> bot.reader().entity(entityId));
    }

    @Override
    public List<EntitySnapshot> entities() {
        return bot.query(() -> bot.reader().entities());
    }

    @Override
    public WindowSnapshot inventory() {
        return bot.query(() -> bot.reader().inventory());
    }

    @Override
    public @Nullable WindowSnapshot openWindow() {
        return bot.query(() -> bot.reader().openWindow());
    }

    @Override
    public @Nullable ItemStack cursor() {
        return bot.query(() -> bot.reader().cursor());
    }

    @Override
    public @Nullable ItemStack equipment(EquipmentSlot slot) {
        return bot.query(() -> bot.reader().equipment(slot));
    }

    @Override
    public List<ChatLine> chat() {
        return bot.query(() -> bot.reader().chat());
    }

    @Override
    public HudSnapshot hud() {
        return bot.query(() -> bot.reader().hud());
    }
}
