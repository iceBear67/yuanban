package io.ib67.yuanban.bot.state;

import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.chat.ChatModel;
import io.ib67.yuanban.bot.hud.HudModel;
import io.ib67.yuanban.bot.inventory.InventoryModel;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.observe.dispatch.ObservationDispatcher;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.player.PlayerLoadTracker;
import io.ib67.yuanban.bot.registry.SyncedRegistries;
import io.ib67.yuanban.bot.registry.TagTable;
import io.ib67.yuanban.bot.world.ClientWorld;
import io.ib67.yuanban.bot.world.WorldClocks;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.ServerLink;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 一个 bot 的全部模型。只在调度线程上读写。
 */
public final class BotState {
    private final BotOptions options;
    private final Outbox outbox = new Outbox();
    private final ObservationDispatcher observations;
    private LocalPlayer player;
    private InventoryModel inventory = new InventoryModel();
    private final ChatModel chat;
    private final HudModel hud = new HudModel();
    private final CookieStore cookies = new CookieStore();
    private final PlayerLoadTracker loadTracker = new PlayerLoadTracker();
    private WorldClocks clocks = new WorldClocks();
    private @Nullable ClientWorld world;
    private @Nullable WorldInfo worldInfo;
    private @Nullable SyncedRegistries registries;
    private TagTable tags = TagTable.EMPTY;
    private long ticks;
    private @Nullable String serverBrand;
    private List<Key> featureFlags = List.of();
    private List<ServerLink> serverLinks = List.of();
    private final BooleanSupplier onSchedulerThread;

    public BotState(BotOptions options, ObservationDispatcher observations, LocalPlayer player, BooleanSupplier onSchedulerThread) {
        this.options = options;
        this.observations = observations;
        this.player = player;
        this.chat = new ChatModel(options.chatLogSize());
        this.onSchedulerThread = onSchedulerThread;
    }

    /**
     * 修改模型的入口在调用之前检查当前线程
     *
     * @throws IllegalStateException 在调度线程以外调用
     */
    public void checkThread() {
        if (!onSchedulerThread.getAsBoolean()) {
            throw new IllegalStateException("the bot model can only be modified on the scheduler thread, not on "
                    + Thread.currentThread());
        }
    }

    public BotOptions options() {
        return options;
    }

    public Outbox outbox() {
        return outbox;
    }

    public ObservationDispatcher observations() {
        return observations;
    }

    public LocalPlayer player() {
        return player;
    }

    /**
     * 换成新的本地玩家与空的物品栏（原版客户端新建 {@code LocalPlayer}，它的物品栏与窗口随之重新开始）
     */
    public void newPlayer(LocalPlayer player) {
        this.player = player;
        this.inventory = new InventoryModel();
    }

    public InventoryModel inventory() {
        return inventory;
    }

    public ChatModel chat() {
        return chat;
    }

    public HudModel hud() {
        return hud;
    }

    public CookieStore cookies() {
        return cookies;
    }

    public PlayerLoadTracker loadTracker() {
        return loadTracker;
    }

    /**
     * Update Time 给出的各时钟：属于连接的游戏阶段，不属于世界
     */
    public WorldClocks clocks() {
        return clocks;
    }

    /**
     * Finish Configuration：原版新建 {@code ClientPacketListener}，它持有的 {@code ClientClockManager} 随之重新开始
     */
    public void newClocks() {
        this.clocks = new WorldClocks();
    }

    public @Nullable ClientWorld worldOrNull() {
        return world;
    }

    /**
     * @throws IllegalStateException 不在任何世界中
     */
    public ClientWorld world() {
        if (world == null) {
            throw new IllegalStateException("the bot is not in a world");
        }
        return world;
    }

    public @Nullable WorldInfo worldInfo() {
        return worldInfo;
    }

    public void enterWorld(ClientWorld world, WorldInfo info) {
        this.world = world;
        this.worldInfo = info;
    }

    /**
     * 离开当前世界（Start Configuration、Transfer）：到下一次 Login (play) 之前不在任何世界中
     */
    public void leaveWorld() {
        this.world = null;
        this.worldInfo = null;
    }

    /**
     * @throws IllegalStateException 还没有完成配置
     */
    public SyncedRegistries registries() {
        if (registries == null) {
            throw new IllegalStateException("the bot has not finished configuration");
        }
        return registries;
    }

    public void registries(SyncedRegistries registries) {
        this.registries = registries;
    }

    public TagTable tags() {
        return tags;
    }

    public void tags(TagTable tags) {
        this.tags = tags;
    }

    public long ticks() {
        return ticks;
    }

    public long nextTick() {
        return ++ticks;
    }

    public @Nullable String serverBrand() {
        return serverBrand;
    }

    public void serverBrand(String serverBrand) {
        this.serverBrand = serverBrand;
    }

    public List<Key> featureFlags() {
        return featureFlags;
    }

    public void featureFlags(List<Key> featureFlags) {
        this.featureFlags = List.copyOf(featureFlags);
    }

    public List<ServerLink> serverLinks() {
        return serverLinks;
    }

    public void serverLinks(List<ServerLink> serverLinks) {
        this.serverLinks = List.copyOf(serverLinks);
    }
}
