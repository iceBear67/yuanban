package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.ResourcePackPolicy;
import io.ib67.yuanban.bot.chat.LastSeenTracker;
import io.ib67.yuanban.bot.chat.Signature;
import io.ib67.yuanban.bot.decode.DecodeContext;
import io.ib67.yuanban.bot.decode.DecodedChunk;
import io.ib67.yuanban.bot.entity.ClientEntity;
import io.ib67.yuanban.bot.inventory.InventoryModel;
import io.ib67.yuanban.bot.movement.Kinematics;
import io.ib67.yuanban.bot.movement.RidingSeat;
import io.ib67.yuanban.bot.movement.SineTable;
import io.ib67.yuanban.bot.observe.Observation.ActionBarShown;
import io.ib67.yuanban.bot.observe.Observation.BlockChanged;
import io.ib67.yuanban.bot.observe.Observation.BlockEntityChanged;
import io.ib67.yuanban.bot.observe.Observation.BossBarChanged;
import io.ib67.yuanban.bot.observe.Observation.ChatReceived;
import io.ib67.yuanban.bot.observe.Observation.ChunkLoaded;
import io.ib67.yuanban.bot.observe.Observation.ChunkUnloaded;
import io.ib67.yuanban.bot.observe.Observation.CursorChanged;
import io.ib67.yuanban.bot.observe.Observation.EffectChanged;
import io.ib67.yuanban.bot.observe.Observation.EntityDamaged;
import io.ib67.yuanban.bot.observe.Observation.EntityEquipmentChanged;
import io.ib67.yuanban.bot.observe.Observation.EntityMetadataChanged;
import io.ib67.yuanban.bot.observe.Observation.EntityMoved;
import io.ib67.yuanban.bot.observe.Observation.EntityRemoved;
import io.ib67.yuanban.bot.observe.Observation.EntitySpawned;
import io.ib67.yuanban.bot.observe.Observation.EntityStatusReceived;
import io.ib67.yuanban.bot.observe.Observation.ExperienceChanged;
import io.ib67.yuanban.bot.observe.Observation.GameModeChanged;
import io.ib67.yuanban.bot.observe.Observation.HealthChanged;
import io.ib67.yuanban.bot.observe.Observation.HeldSlotChanged;
import io.ib67.yuanban.bot.observe.Observation.ItemPickedUp;
import io.ib67.yuanban.bot.observe.Observation.PlayerDied;
import io.ib67.yuanban.bot.observe.Observation.PlayerTeleported;
import io.ib67.yuanban.bot.observe.Observation.ScoreChanged;
import io.ib67.yuanban.bot.observe.Observation.SlotChanged;
import io.ib67.yuanban.bot.observe.Observation.TitleShown;
import io.ib67.yuanban.bot.observe.Observation.WindowClosed;
import io.ib67.yuanban.bot.observe.Observation.WindowOpened;
import io.ib67.yuanban.bot.observe.Observation.WorldEntered;
import io.ib67.yuanban.bot.observe.WorldEntryCause;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.observe.dispatch.ObservationDispatcher;
import io.ib67.yuanban.bot.player.Interpolation;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.registry.SyncedRegistries;
import io.ib67.yuanban.bot.registry.TagTable;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import io.ib67.yuanban.bot.scheduler.BotState;
import io.ib67.yuanban.bot.view.ActiveEffect;
import io.ib67.yuanban.bot.view.ChatKind;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.world.ClientWorld;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongList;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatFilterType;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatType;
import org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RainStrengthValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ThunderStrengthValue;
import org.geysermc.mcprotocollib.protocol.data.handshake.HandshakeIntent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundServerLinksPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundStoreCookiePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundTransferPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundUpdateTagsPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundCodeOfConductPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundResetChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundSelectKnownPacks;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundUpdateEnabledFeaturesPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundAcceptCodeOfConductPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundSelectKnownPacks;
import org.geysermc.mcprotocollib.protocol.packet.cookie.clientbound.ClientboundCookieRequestPacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.serverbound.ServerboundCookieResponsePacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundChangeDifficultyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCooldownPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDeleteChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoRemovePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundServerDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTabListPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTickingStatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTickingStepPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRotateHeadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityLinkPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTakeItemEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerRotationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMerchantOffersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundMountScreenOpenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetCursorItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchStartPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundExplodePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheRadiusPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetSimulationDistancePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderLerpSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDelayPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDistancePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundResetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetDisplayObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitlesAnimationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundCustomQueryPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 协议状态机：在调度线程上应用收到的数据包、产生观察记录、写入自动应答。各种连接共用。
 * 每个数据包产生的模型层观察记录按变化发生的顺序产生，最后产生它的 {@code PacketReceived}。
 */
public final class ProtocolMachine {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /**
     * 原版 {@code ChatListener.CHAT_VALIDATION_ERROR}：发送者不在玩家列表中的玩家聊天，原版显示这段文本代替消息
     */
    private static final Component CHAT_VALIDATION_ERROR = Component.translatable("chat.validation_error",
            NamedTextColor.RED, TextDecoration.ITALIC);
    private static final Key BRAND = Key.key(Key.MINECRAFT_NAMESPACE, "brand");

    private final BotInstance bot;
    private final BotState state;
    private final ObservationDispatcher observations;
    private final Outbox outbox;
    private final ChunkBatchTracker chunkBatches = new ChunkBatchTracker();
    private WorldEntryCause nextJoinCause = WorldEntryCause.JOIN;
    private long worldEpoch;
    /**
     * 被移除的、本地玩家直接或间接乘坐过的实体编号（原版 {@code ClientPacketListener.removedPlayerVehicleId}）
     */
    private @Nullable Integer removedPlayerVehicle;

    public ProtocolMachine(BotInstance bot) {
        this.bot = bot;
        this.state = bot.state();
        this.observations = state.observations();
        this.outbox = state.outbox();
    }

    /**
     * 新连接建立：发送 Handshake 与 Login Start。这时连接状态已经是 LOGIN（初始状态，或者 Transfer 设置的）
     *
     * @param transfer 是否为 Transfer 之后的连接（握手意图为 TRANSFER）
     * @throws IllegalStateException 连接状态不是 LOGIN
     */
    public void startLogin(String serverAddress, int serverPort, boolean transfer) {
        state.checkThread();
        if (bot.status() != BotStatus.LOGIN) {
            throw new IllegalStateException("a new connection starts in " + bot.status() + ", expected LOGIN");
        }
        nextJoinCause = transfer ? WorldEntryCause.TRANSFER : WorldEntryCause.JOIN;
        outbox.send(new ClientIntentionPacket(MinecraftCodec.CODEC.getProtocolVersion(), serverAddress, serverPort,
                transfer ? HandshakeIntent.TRANSFER : HandshakeIntent.LOGIN));
        outbox.send(new ServerboundHelloPacket(bot.username(), offlineUuid(bot.username())));
    }

    /**
     * 离线 UUID：由 {@code OfflinePlayer:<名称>} 生成的 UUID v3
     */
    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public void handle(InboundPacket inbound) {
        state.checkThread();
        var packet = inbound.packet();
        apply(packet, inbound.attachment());
        observations.emitReceived(packet);
    }

    private void apply(MinecraftPacket packet, @Nullable Object attachment) {
        switch (packet) {
            // 登录
            case ClientboundLoginDisconnectPacket disconnect -> bot.disconnected(disconnect.getReason());
            // 回应的数据为 null 表示未理解，必须使用两个参数的构造方法（一个参数的构造方法写出空数组，表示理解）
            case ClientboundCustomQueryPacket request -> outbox.send(new ServerboundCustomQueryAnswerPacket(request.getMessageId(),
                    state.options().loginPluginHandler().respond(request.getChannel(), request.getData())));
            case ClientboundLoginFinishedPacket _ -> {
                outbox.send(new ServerboundLoginAcknowledgedPacket());
                bot.status(BotStatus.CONFIGURATION);
                outbox.send(new ServerboundCustomPayloadPacket(BRAND, writeString(state.options().brand())));
                outbox.send(state.options().clientInformation());
            }
            // 配置
            case ClientboundSelectKnownPacks knownPacks -> outbox.send(new ServerboundSelectKnownPacks(knownPacks.getKnownPacks()
                    .stream()
                    .filter(pack -> pack.getNamespace().equals(Key.MINECRAFT_NAMESPACE) && pack.getId().equals("core")
                            && pack.getVersion().equals(MinecraftCodec.CODEC.getMinecraftVersion()))
                    .toList()));
            case ClientboundUpdateTagsPacket _ -> state.tags((TagTable) Objects.requireNonNull(attachment));
            case ClientboundUpdateEnabledFeaturesPacket features -> state.featureFlags(List.of(features.getFeatures()));
            case ClientboundCodeOfConductPacket _ -> {
                if (state.options().acceptCodeOfConduct()) {
                    outbox.send(ServerboundAcceptCodeOfConductPacket.INSTANCE);
                }
            }
            case ClientboundFinishConfigurationPacket _ -> {
                var finished = (DecodeContext.ConfigurationFinished) Objects.requireNonNull(attachment);
                state.registries(finished.registries());
                state.tags(finished.tags());
                // 原版进入游戏阶段时新建 ClientPacketListener，它的 ClientClockManager 随之重新开始
                state.newClocks();
                outbox.send(ServerboundFinishConfigurationPacket.INSTANCE);
                bot.status(BotStatus.PLAY);
            }
            case ClientboundResetChatPacket _ -> state.chat().clear();
            case ClientboundStartConfigurationPacket _ -> {
                // 原版 handleConfigurationStart：未确认的消息数大于 0 时先发送 Acknowledge Message，然后清除客户端世界（clearClientLevel）
                var unacknowledged = state.chat().lastSeen().takeOffset();
                if (unacknowledged > 0) {
                    outbox.send(new ServerboundChatAckPacket(unacknowledged));
                }
                leaveWorld();
                outbox.send(new ServerboundConfigurationAcknowledgedPacket());
                bot.status(BotStatus.CONFIGURATION);
            }
            // 任意阶段的自动应答
            case ClientboundKeepAlivePacket keepAlive -> outbox.send(new ServerboundKeepAlivePacket(keepAlive.getPingId()));
            case ClientboundPingPacket ping -> outbox.send(new ServerboundPongPacket(ping.getId()));
            case ClientboundCookieRequestPacket request -> outbox.send(new ServerboundCookieResponsePacket(request.getKey(),
                    state.cookies().get(request.getKey())));
            case ClientboundStoreCookiePacket store -> state.cookies().store(store.getKey(), store.getPayload());
            case ClientboundResourcePackPushPacket push -> resourcePack(push);
            case ClientboundCustomPayloadPacket message -> {
                if (message.getChannel().equals(BRAND)) {
                    state.serverBrand(MinecraftTypes.readString(Unpooled.wrappedBuffer(message.getData())));
                }
            }
            case ClientboundServerLinksPacket links -> state.serverLinks(links.getLinks());
            case ClientboundTransferPacket transfer -> bot.transfer(transfer.getHost(), transfer.getPort());
            case ClientboundDisconnectPacket disconnect -> bot.disconnected(disconnect.getReason());
            case ClientboundChunkBatchStartPacket _ -> chunkBatches.start(System.nanoTime());
            case ClientboundChunkBatchFinishedPacket finished -> outbox.send(new ServerboundChunkBatchReceivedPacket(
                    chunkBatches.finish(System.nanoTime(), finished.getBatchSize())));
            case ClientboundPlayerPositionPacket teleport -> teleport(teleport);
            // 世界
            case ClientboundLoginPacket join -> join(join);
            case ClientboundRespawnPacket respawn -> respawn(respawn);
            case ClientboundLevelChunkWithLightPacket _ -> installChunk((DecodedChunk) Objects.requireNonNull(attachment));
            case ClientboundForgetLevelChunkPacket unload -> {
                if (world().unloadChunk(unload.getX(), unload.getZ()) && observations.wants(ChunkUnloaded.class)) {
                    observations.emit(new ChunkUnloaded(unload.getX(), unload.getZ()));
                }
            }
            case ClientboundSetChunkCacheCenterPacket center -> unloaded(world().center(center.getChunkX(), center.getChunkZ()));
            case ClientboundSetChunkCacheRadiusPacket distance -> unloaded(world().viewDistance(distance.getViewDistance()));
            case ClientboundSetSimulationDistancePacket distance -> world().simulationDistance(distance.getSimulationDistance());
            case ClientboundBlockUpdatePacket change -> {
                var position = change.getEntry().getPosition();
                setBlock(position.getX(), position.getY(), position.getZ(), change.getEntry().getBlock());
            }
            // MCProtocolLib 已经把每一项解码成绝对坐标
            case ClientboundSectionBlocksUpdatePacket change -> {
                for (var entry : change.getEntries()) {
                    var position = entry.getPosition();
                    setBlock(position.getX(), position.getY(), position.getZ(), entry.getBlock());
                }
            }
            case ClientboundChunksBiomesPacket biomes -> {
                var biomeCount = state.registries().biomeCount();
                for (var chunk : biomes.getChunkBiomeData()) {
                    world().setBiomes(chunk.getX(), chunk.getZ(), chunk.getBuffer(), biomeCount);
                }
            }
            case ClientboundBlockEntityDataPacket data -> blockEntity(data);
            case ClientboundSetTimePacket time -> {
                // 原版 handleSetTime：世界的 World Age 设为数据包的值，然后时钟按它推进并替换列出的时钟
                world().worldAge(time.getGameTime());
                state.clocks().apply(time, state.registries());
            }
            case ClientboundTickingStatePacket tickState -> world().tickState().apply(tickState.getTickRate(), tickState.isFrozen());
            case ClientboundTickingStepPacket step -> world().tickState().step(step.getTickSteps());
            case ClientboundInitializeBorderPacket border -> world().border().initialize(border);
            case ClientboundSetBorderCenterPacket center -> world().border().center(center.getNewCenterX(), center.getNewCenterZ());
            case ClientboundSetBorderLerpSizePacket lerp ->
                    world().border().lerp(lerp.getOldSize(), lerp.getNewSize(), lerp.getLerpTime());
            case ClientboundSetBorderSizePacket size -> world().border().size(size.getSize());
            case ClientboundSetBorderWarningDelayPacket delay -> world().border().warningTime(delay.getWarningDelay());
            case ClientboundSetBorderWarningDistancePacket reach -> world().border().warningBlocks(reach.getWarningBlocks());
            case ClientboundChangeDifficultyPacket difficulty -> world().difficulty(difficulty.getDifficulty());
            case ClientboundSetDefaultSpawnPositionPacket spawn ->
                    world().defaultSpawn(Vectors.vec(spawn.getGlobalPos().getPosition()));
            case ClientboundGameEventPacket change -> gameState(change);
            // 实体
            case ClientboundAddEntityPacket spawn -> spawnEntity(spawn);
            case ClientboundMoveEntityPosPacket move ->
                    moveEntity(move.getEntityId(), move.getMoveX(), move.getMoveY(), move.getMoveZ(), null, null, move.isOnGround());
            case ClientboundMoveEntityPosRotPacket move -> moveEntity(move.getEntityId(), move.getMoveX(), move.getMoveY(),
                    move.getMoveZ(), move.getYaw(), move.getPitch(), move.isOnGround());
            case ClientboundMoveEntityRotPacket rotation -> {
                var entity = otherEntity(rotation.getEntityId());
                if (entity != null) {
                    entity.position(entity.position().withView(rotation.getYaw(), rotation.getPitch()), rotation.isOnGround());
                }
            }
            case ClientboundRotateHeadPacket headLook -> {
                var entity = otherEntity(headLook.getEntityId());
                if (entity != null) {
                    entity.headYaw(headLook.getHeadYaw());
                }
            }
            case ClientboundEntityPositionSyncPacket sync -> positionSync(sync);
            case ClientboundTeleportEntityPacket teleport -> teleportEntity(teleport);
            case ClientboundSetEntityMotionPacket velocity -> {
                if (velocity.getEntityId() == state.player().entityId()) {
                    state.player().velocity(Vectors.vec(velocity.getMovement()));
                } else {
                    var entity = world().entities().get(velocity.getEntityId());
                    if (entity != null) {
                        entity.velocity(Vectors.vec(velocity.getMovement()));
                    }
                }
            }
            case ClientboundSetEntityDataPacket metadata -> {
                var target = metadata.getEntityId() == state.player().entityId() ? state.player().metadata()
                        : entityMetadata(metadata.getEntityId());
                if (target != null) {
                    var changed = new LinkedHashMap<Integer, EntityMetadata<?, ?>>();
                    for (var entry : metadata.getMetadata()) {
                        changed.put(entry.getId(), entry);
                    }
                    target.putAll(changed);
                    if (observations.wants(EntityMetadataChanged.class)) {
                        observations.emit(new EntityMetadataChanged(metadata.getEntityId(), changed));
                    }
                }
            }
            case ClientboundSetEquipmentPacket equipment -> {
                var entity = world().entities().get(equipment.getEntityId());
                if (entity != null) {
                    for (var change : equipment.getEquipment()) {
                        // 空物品从装备表中移除，装备表中没有空物品
                        var item = change.getItem();
                        var old = item == null ? entity.equipment().remove(change.getSlot())
                                : entity.equipment().put(change.getSlot(), item);
                        if (observations.wants(EntityEquipmentChanged.class)) {
                            observations.emit(new EntityEquipmentChanged(equipment.getEntityId(), change.getSlot(), old, item));
                        }
                    }
                }
            }
            case ClientboundUpdateAttributesPacket attributes -> {
                if (attributes.getEntityId() == state.player().entityId()) {
                    state.player().attributes().apply(attributes.getAttributes());
                } else {
                    var entity = world().entities().get(attributes.getEntityId());
                    if (entity != null) {
                        entity.attributes().apply(attributes.getAttributes());
                    }
                }
            }
            case ClientboundUpdateMobEffectPacket effect -> {
                var active = new ActiveEffect(effect.getEffect(), effect.getAmplifier(), effect.getDuration(), effect.isAmbient(),
                        effect.isShowParticles(), effect.isShowIcon(), effect.isBlend());
                if (effect.getEntityId() == state.player().entityId()) {
                    state.player().effects().put(active);
                    if (observations.wants(EffectChanged.class)) {
                        observations.emit(new EffectChanged(effect.getEffect(), active, state.ticks()));
                    }
                } else {
                    var entity = world().entities().get(effect.getEntityId());
                    if (entity != null) {
                        entity.effects().put(effect.getEffect(), active);
                    }
                }
            }
            case ClientboundRemoveMobEffectPacket remove -> {
                if (remove.getEntityId() == state.player().entityId()) {
                    state.player().effects().remove(remove.getEffect());
                    if (observations.wants(EffectChanged.class)) {
                        observations.emit(new EffectChanged(remove.getEffect(), null, state.ticks()));
                    }
                } else {
                    var entity = world().entities().get(remove.getEntityId());
                    if (entity != null) {
                        entity.effects().remove(remove.getEffect());
                    }
                }
            }
            case ClientboundSetPassengersPacket passengers -> passengers(passengers);
            case ClientboundSetEntityLinkPacket attach -> {
                // 被拴住的实体为 entityId，拴住它的实体为 attachedToId
                var entity = world().entities().get(attach.getEntityId());
                if (entity != null) {
                    // 拴绳另一端的实体编号为 -1 时表示解开（wiki Link Entities）
                    entity.leashHolder(attach.getAttachedToId() == -1 ? null : attach.getAttachedToId());
                }
            }
            case ClientboundRemoveEntitiesPacket destroy -> {
                for (var id : destroy.getEntityIds()) {
                    var removed = world().entities().get(id);
                    if (removed == null) {
                        continue;
                    }
                    // 原版 handleRemoveEntities：移除的是本地玩家直接或间接乘坐的实体时记下它的编号
                    if (ridesIndirectly(state.player(), id)) {
                        removedPlayerVehicle = id;
                    }
                    world().entities().remove(id);
                    stopRiding(removed);
                    if (observations.wants(EntityRemoved.class)) {
                        observations.emit(new EntityRemoved(removed.snapshot()));
                    }
                }
            }
            case ClientboundTakeItemEntityPacket collect -> {
                if (observations.wants(ItemPickedUp.class)) {
                    observations.emit(new ItemPickedUp(collect.getCollectedEntityId(), collect.getCollectorEntityId(),
                            collect.getItemCount()));
                }
            }
            case ClientboundEntityEventPacket status -> {
                if (observations.wants(EntityStatusReceived.class)) {
                    observations.emit(new EntityStatusReceived(status.getEntityId(), status.getEvent()));
                }
            }
            case ClientboundDamageEventPacket damage -> {
                // 原版解码时就要求伤害类型的编号在注册表中，与是否有 observer 无关
                var type = state.registries().requireEntry(SyncedRegistries.DAMAGE_TYPE, damage.getSourceTypeId());
                if (observations.wants(EntityDamaged.class)) {
                    // MCProtocolLib 以 -1 表示没有来源实体
                    var cause = damage.getSourceCauseId();
                    var direct = damage.getSourceDirectId();
                    var position = damage.getSourcePosition();
                    observations.emit(new EntityDamaged(damage.getEntityId(), type, cause == -1 ? null : cause,
                            direct == -1 ? null : direct, position == null ? null : Vectors.vec(position)));
                }
            }
            // 本地玩家
            case ClientboundPlayerRotationPacket rotation -> {
                // relativeY 对应偏航角（y 轴旋转），relativeX 对应俯仰角（x 轴旋转）
                var position = state.player().position();
                var yaw = rotation.isRelativeY() ? position.yaw() + rotation.getYRot() : rotation.getYRot();
                var pitch = rotation.isRelativeX() ? position.pitch() + rotation.getXRot() : rotation.getXRot();
                state.player().position(position.withView(yaw, Math.clamp(pitch, -90f, 90f)));
                bot.movement().onServerRotation();
            }
            case ClientboundSetHealthPacket health -> health(health);
            case ClientboundPlayerCombatKillPacket death -> {
                if (death.getPlayerId() == state.player().entityId()) {
                    if (observations.wants(PlayerDied.class)) {
                        observations.emit(new PlayerDied(death.getMessage()));
                    }
                    playerDied();
                }
            }
            case ClientboundPlayerAbilitiesPacket abilities -> state.player().abilities().apply(abilities);
            case ClientboundSetExperiencePacket experience -> {
                state.player().experience(experience.getExperience(), experience.getLevel(), experience.getTotalExperience());
                if (observations.wants(ExperienceChanged.class)) {
                    observations.emit(new ExperienceChanged(experience.getExperience(), experience.getLevel(),
                            experience.getTotalExperience()));
                }
            }
            case ClientboundCooldownPacket cooldown -> {
                if (cooldown.getCooldownTicks() <= 0) {
                    state.player().cooldowns().remove(cooldown.getCooldownGroup());
                } else {
                    state.player().cooldowns().put(cooldown.getCooldownGroup(), cooldown.getCooldownTicks());
                }
            }
            case ClientboundPlayerInfoUpdatePacket info -> {
                var self = state.player().uuid();
                var listedBefore = state.hud().player(self);
                state.hud().playerInfo(info);
                if (info.getActions().contains(PlayerListEntryAction.UPDATE_GAME_MODE)) {
                    for (var entry : info.getEntries()) {
                        // 不在玩家列表中的玩家的更新被忽略，包括自己（原版客户端行为）
                        if (entry.getProfileId().equals(self) && state.hud().hasPlayer(self)) {
                            // 原版 applyPlayerInfoUpdate：玩家列表中自己原来的游戏模式（新条目为原版 PlayerInfo 的初始值 SURVIVAL）
                            // 与新的不同时调用 LocalPlayer.onGameModeChanged，改成旁观模式时竖直速度清零
                            var previous = listedBefore == null ? GameMode.SURVIVAL : listedBefore.gameMode();
                            if (previous != entry.getGameMode() && entry.getGameMode() == GameMode.SPECTATOR) {
                                state.player().velocity(state.player().velocity().withY(0));
                            }
                            gameMode(entry.getGameMode());
                        }
                    }
                }
            }
            case ClientboundPlayerInfoRemovePacket remove -> state.hud().removePlayers(remove.getProfileIds());
            case ClientboundExplodePacket explosion -> {
                if (explosion.getPlayerKnockback() != null) {
                    state.player().velocity(state.player().velocity().add(Vectors.vec(explosion.getPlayerKnockback())));
                }
            }
            case ClientboundBlockChangedAckPacket acknowledge -> bot.hand().acknowledge(acknowledge.getSequence());
            // 物品栏
            case ClientboundOpenScreenPacket open -> {
                state.inventory().open(open.getContainerId(), open.getType(), open.getTitle(), slotSink());
                if (observations.wants(WindowOpened.class)) {
                    observations.emit(new WindowOpened(open.getContainerId(), open.getType(), open.getTitle()));
                }
            }
            case ClientboundMountScreenOpenPacket open -> openMount(open);
            // 原版 handleContainerClose 不看窗口编号，关闭当前打开的窗口
            case ClientboundContainerClosePacket _ -> {
                var closed = state.inventory().closeOpen(slotSink());
                if (closed != null && observations.wants(WindowClosed.class)) {
                    observations.emit(new WindowClosed(closed));
                }
            }
            case ClientboundContainerSetContentPacket items -> state.inventory().windowItems(items.getContainerId(),
                    items.getStateId(), Arrays.asList(items.getItems()), items.getCarriedItem(), slotSink());
            case ClientboundContainerSetSlotPacket slot ->
                    state.inventory().setSlot(slot.getContainerId(), slot.getStateId(), slot.getSlot(), slot.getItem(), slotSink());
            case ClientboundSetPlayerInventoryPacket slot ->
                    state.inventory().setPlayerInventorySlot(slot.getSlot(), slot.getContents(), slotSink());
            case ClientboundSetCursorItemPacket cursor -> state.inventory().cursor(cursor.getContents(), slotSink());
            case ClientboundContainerSetDataPacket property ->
                    state.inventory().property(property.getContainerId(), property.getRawProperty(), property.getValue());
            case ClientboundMerchantOffersPacket trades -> state.inventory().trades(trades);
            case ClientboundSetHeldSlotPacket held -> heldSlot(held.getSlot());
            // 聊天与界面
            case ClientboundPlayerChatPacket chat -> playerChat(chat);
            case ClientboundDisguisedChatPacket chat -> chatLine(new ChatLine(ChatKind.DISGUISED, null,
                    chatTypeName(chat.getChatType()), inlineChatType(chat.getChatType()), chat.getName(), chat.getTargetName(),
                    chat.getMessage(), PLAIN.serialize(chat.getMessage())), null);
            case ClientboundSystemChatPacket chat -> {
                if (chat.isOverlay()) {
                    actionBar(chat.getContent());
                } else {
                    chatLine(new ChatLine(ChatKind.SYSTEM, null, null, null, null, null, chat.getContent(),
                            PLAIN.serialize(chat.getContent())), null);
                }
            }
            case ClientboundDeleteChatPacket delete -> {
                var signature = signature(delete.getMessageSignature());
                // 原版 handleDeleteChat：还没有随 Chat Message 告知服务端的这条消息不再计入已读
                state.chat().lastSeen().ignorePending(signature);
                state.chat().delete(signature);
            }
            case ClientboundSetActionBarTextPacket actionBar -> actionBar(actionBar.getText());
            case ClientboundSetTitleTextPacket title -> {
                state.hud().title(title.getText());
                titleShown();
            }
            case ClientboundSetSubtitleTextPacket subtitle -> {
                state.hud().subtitle(subtitle.getText());
                titleShown();
            }
            case ClientboundSetTitlesAnimationPacket times ->
                    state.hud().titleTimes(times.getFadeIn(), times.getStay(), times.getFadeOut());
            case ClientboundClearTitlesPacket clear -> state.hud().clearTitles(clear.isResetTimes());
            case ClientboundBossEventPacket bossBar -> {
                if (state.hud().bossBar(bossBar) && observations.wants(BossBarChanged.class)) {
                    observations.emit(new BossBarChanged(bossBar.getUuid(), state.hud().currentBossBar(bossBar.getUuid())));
                }
            }
            case ClientboundTabListPacket list -> state.hud().headerFooter(list.getHeader(), list.getFooter());
            case ClientboundSetObjectivePacket objective -> state.hud().objective(objective);
            case ClientboundSetDisplayObjectivePacket display -> state.hud().display(display.getPosition(), display.getName());
            case ClientboundSetScorePacket score -> {
                if (state.hud().score(score.getOwner(), score.getObjective(), score.getValue())
                        && observations.wants(ScoreChanged.class)) {
                    observations.emit(new ScoreChanged(score.getObjective(), score.getOwner(), score.getValue()));
                }
            }
            case ClientboundResetScorePacket reset -> {
                for (var objective : state.hud().resetScore(reset.getOwner(), reset.getObjective())) {
                    if (observations.wants(ScoreChanged.class)) {
                        observations.emit(new ScoreChanged(objective, reset.getOwner(), null));
                    }
                }
            }
            case ClientboundSetPlayerTeamPacket team -> state.hud().team(team);
            case ClientboundServerDataPacket data -> state.hud().serverMotd(data.getMotd());
            default -> {
                // 其余数据包没有对应的模型状态，只产生 PacketReceived
            }
        }
    }

    private ClientWorld world() {
        return state.world();
    }

    private void resourcePack(ClientboundResourcePackPushPacket push) {
        if (state.options().resourcePackPolicy() == ResourcePackPolicy.DECLINE) {
            outbox.send(new ServerboundResourcePackPacket(push.getId(), ResourcePackStatus.DECLINED));
            return;
        }
        outbox.send(new ServerboundResourcePackPacket(push.getId(), ResourcePackStatus.ACCEPTED));
        outbox.send(new ServerboundResourcePackPacket(push.getId(), ResourcePackStatus.DOWNLOADED));
        outbox.send(new ServerboundResourcePackPacket(push.getId(), ResourcePackStatus.SUCCESSFULLY_LOADED));
    }

    /**
     * 按协议的 String 格式（VarInt 长度加 UTF-8 字节）编码，用于 {@code minecraft:brand}
     */
    private static byte[] writeString(String value) {
        var buffer = Unpooled.buffer();
        try {
            MinecraftTypes.writeString(buffer, value);
            return ByteBufUtil.getBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    // 世界

    /**
     * 离开当前世界（Start Configuration、Transfer）。原版客户端清除世界、本地玩家与游戏模式控制器，不发送任何数据包：
     * 移动与手部两条队列中的动作以世界重建取消，不发送清理数据包；加载状态重置。
     * 到下一次 Login (play) 之前 bot 不在任何世界中、不 tick，这期间提交的动作与一次性请求等待进入下一个世界并加载之后执行。
     * 已经不在任何世界中时（进入第一个世界之前、重新配置期间），队列中只有这期间提交的动作，保留它们
     */
    public void leaveWorld() {
        state.checkThread();
        if (state.worldOrNull() == null) {
            return;
        }
        bot.movement().cancelAll(ActionCancelledException.WORLD_REBUILT);
        bot.hand().discardAll(ActionCancelledException.WORLD_REBUILT);
        state.leaveWorld();
        state.loadTracker().reset();
        // 原版之后的世界由新的 ClientPacketListener 处理，记下的载具编号随之清除
        removedPlayerVehicle = null;
    }

    /**
     * Login (play)（原版 {@code handleLogin}）：新建世界与游戏模式控制器（挖掘延迟与快捷栏同步记录归零，方块交互序号从 0 开始），
     * 已读跟踪、签名缓存与 Player Chat Message 的全局编号重新开始。
     * 不在任何世界中时（新的连接，或者 Start Configuration 之后）创建新的本地玩家，物品栏、移动包与 Player Input 的发送记录随之重新开始；
     * 已经在世界中时（服务端不经过重新配置再次发送 Login (play)）沿用当前玩家，只做原版 {@code resetPos}，
     * 移动与手部两条队列中的动作以世界重建取消，不发送清理数据包
     */
    private void join(ClientboundLoginPacket join) {
        var cause = nextJoinCause;
        nextJoinCause = WorldEntryCause.RECONFIGURATION;
        var spawnInfo = join.getCommonPlayerSpawnInfo();
        var world = newWorld(spawnInfo, join.getViewDistance(), join.getSimulationDistance());
        var player = state.player();
        if (state.worldOrNull() == null) {
            var created = new LocalPlayer(player.uuid(), player.username());
            created.gameMode(player.gameMode());
            state.newPlayer(created);
            bot.movement().newPlayer(false);
            player = created;
        } else {
            bot.movement().cancelAll(ActionCancelledException.WORLD_REBUILT);
            bot.hand().discardAll(ActionCancelledException.WORLD_REBUILT);
            player.vehicle(null);
            resetPos(player, world);
        }
        bot.hand().newGameMode();
        bot.hand().newWorld();
        state.chat().newSession();
        player.entityId(join.getEntityId());
        player.dead(false);
        enterWorld(world, spawnInfo, cause);
        bot.markJoined();
    }

    /**
     * Respawn（原版 {@code handleRespawn}）：
     * <ul>
     *     <li>维度名称改变时重新创建世界，方块交互序号从 0 开始；同一维度内保留区块、方块实体与其它实体；</li>
     *     <li>打开着容器窗口时先发送带该窗口编号的 Close Container，并在本地关闭窗口；</li>
     *     <li>移动与手部两条队列中的动作以世界重建取消；挖掘延迟与快捷栏同步记录不变；</li>
     *     <li>创建新的本地玩家，沿用实体编号：Data kept 0x02 保留元数据、生命值、速度、朝向、上次发送的 Player Input 与疾跑状态，
     *     没有 0x02 时做原版 {@code resetPos}，偏航角 -180；0x01 保留属性的基础值与全部修饰符，没有 0x01 时只保留基础值；
     *     物品栏为空，快捷栏槽位 0，没有物品冷却与药水效果，饥饿值 20，饱和度 5，经验为 0。</li>
     * </ul>
     */
    private void respawn(ClientboundRespawnPacket respawn) {
        var current = world();
        var old = state.player();
        var spawnInfo = respawn.getCommonPlayerSpawnInfo();
        var keepData = respawn.isKeepMetadata();
        var open = state.inventory().open();
        if (open != null) {
            outbox.send(new ServerboundContainerClosePacket(open.id()));
            state.inventory().closeOpen(slotSink());
            if (observations.wants(WindowClosed.class)) {
                observations.emit(new WindowClosed(open.id()));
            }
        }
        bot.movement().cancelAll(ActionCancelledException.WORLD_REBUILT);
        bot.hand().cancelAll(ActionCancelledException.WORLD_REBUILT);
        var world = spawnInfo.getWorldName().equals(current.dimension()) ? current
                : newWorld(spawnInfo, current.viewDistance(), current.simulationDistance());
        if (world != current) {
            bot.hand().newWorld();
        }
        var player = new LocalPlayer(old.uuid(), old.username());
        player.entityId(old.entityId());
        player.gameMode(old.gameMode());
        if (keepData) {
            player.metadata().putAll(old.metadata());
            player.health(old.health(), player.food(), player.saturation());
            player.velocity(old.velocity());
            player.position(player.position().withView(old.position().yaw(), old.position().pitch()));
        } else {
            resetPos(player, world);
        }
        if (respawn.isKeepAttributeModifiers()) {
            player.attributes().copyAll(old.attributes());
        } else {
            player.attributes().copyBaseValues(old.attributes());
        }
        state.newPlayer(player);
        bot.movement().newPlayer(keepData);
        enterWorld(world, spawnInfo, WorldEntryCause.RESPAWN);
    }

    /**
     * @throws IllegalStateException 维度类型的编号不在注册表中
     */
    private ClientWorld newWorld(PlayerSpawnInfo spawnInfo, int viewDistance, int simulationDistance) {
        var type = state.registries().dimensionType(spawnInfo.getDimension());
        return new ClientWorld(spawnInfo.getWorldName(), spawnInfo.getDimension(), type.minY(), type.height(),
                viewDistance, simulationDistance);
    }

    private void enterWorld(ClientWorld world, PlayerSpawnInfo spawnInfo, WorldEntryCause cause) {
        var info = new WorldInfo(++worldEpoch, world.dimension(), spawnInfo.getDimension(), cause);
        state.enterWorld(world, info);
        // 原版 startWaitingForNewLevel：开始等待加载，30 秒的超时从这里开始计算
        state.loadTracker().start(System.nanoTime());
        bot.enteredWorld();
        localGameMode(spawnInfo.getGameMode());
        if (observations.wants(WorldEntered.class)) {
            observations.emit(new WorldEntered(info));
        }
    }

    /**
     * 原版 {@code LocalPlayer.resetPos}：姿态为站立；在维度高度范围内从当前高度逐格向上，直到包围盒不与方块的碰撞箱相交
     * （区块未加载的位置没有方块）；然后速度与俯仰角归零，生命值设为最大生命值
     */
    private static void resetPos(LocalPlayer player, ClientWorld world) {
        player.sneaking(false);
        var position = player.position();
        var maxY = world.minY() + world.height() - 1;
        for (double y = position.y(); y > world.minY() && y <= maxY; y++) {
            position = position.withY(y);
            if (!world.collidesWithBlocks(position, player.boundingBox())) {
                break;
            }
        }
        player.position(position.withPitch(0));
        player.velocity(Vec.ZERO);
        player.health((float) player.attributes().value(AttributeType.Builtin.MAX_HEALTH), player.food(), player.saturation());
    }

    private void installChunk(DecodedChunk chunk) {
        var world = world();
        // 加载区域外的区块数据直接忽略（原版客户端行为）
        if (!world.inLoadArea(chunk.x(), chunk.z())) {
            return;
        }
        world.installChunk(chunk.x(), chunk.z(), chunk.sections(), chunk.blockEntities());
        if (observations.wants(ChunkLoaded.class)) {
            observations.emit(new ChunkLoaded(chunk.x(), chunk.z()));
        }
    }

    private void unloaded(LongList chunks) {
        if (!observations.wants(ChunkUnloaded.class)) {
            return;
        }
        for (int i = 0; i < chunks.size(); i++) {
            var key = chunks.getLong(i);
            observations.emit(new ChunkUnloaded(CoordConversion.chunkIndexGetX(key), CoordConversion.chunkIndexGetZ(key)));
        }
    }

    private void setBlock(int x, int y, int z, int stateId) {
        var old = world().setBlock(x, y, z, stateId);
        if (old == null) {
            return;
        }
        var position = new Vec(x, y, z);
        if (observations.wants(BlockChanged.class)) {
            observations.emit(new BlockChanged(position, old, Objects.requireNonNull(Block.fromStateId(stateId))));
        }
        bot.movement().onBlockChanged(position);
    }

    /**
     * Block Entity Data：原版 {@code handleBlockEntityData} 只应用到 {@code level.getBlockEntity(pos, type)} 存在的方块实体，
     * 即所在区块已加载、位置在维度高度范围之内，并且该位置的方块有同一类型的方块实体；其余情况忽略（原版客户端行为）。
     * 方块实体的类型按注册表编号比较
     */
    private void blockEntity(ClientboundBlockEntityDataPacket data) {
        var position = data.getPosition();
        var x = position.getX();
        var y = position.getY();
        var z = position.getZ();
        var world = world();
        if (y < world.minY() || y >= world.minY() + world.height() || !world.chunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        var blockType = world.getBlock(x, y, z, Block.Getter.Condition.TYPE).blockEntityType();
        if (blockType == null || blockType.id() != data.getType().ordinal()) {
            return;
        }
        var nbt = Objects.requireNonNullElse(data.getNbt(), NbtMap.EMPTY);
        world.blockEntities().put(x, y, z, nbt);
        if (observations.wants(BlockEntityChanged.class)) {
            observations.emit(new BlockEntityChanged(new Vec(x, y, z), nbt));
        }
    }

    /**
     * Game Event：MCProtocolLib 按事件解码数值，游戏模式为 {@link GameMode}（不认识的编号为生存模式，与原版相同），
     * 雨与雷暴强度限制到 [0, 1]
     */
    private void gameState(ClientboundGameEventPacket change) {
        switch (change.getNotification()) {
            case CHANGE_GAME_MODE -> localGameMode((GameMode) change.getValue());
            case RAIN_LEVEL_CHANGE -> world().weather().rainLevel(((RainStrengthValue) change.getValue()).getStrength());
            case THUNDER_LEVEL_CHANGE -> world().weather().thunderLevel(((ThunderStrengthValue) change.getValue()).getStrength());
            case LEVEL_CHUNKS_LOAD_START -> state.loadTracker().chunksLoadStarted();
            default -> {
                // 事件 1、2 的效果与名称相反，原版服务端紧接着会发送事件 7；其余事件没有模型状态
            }
        }
    }

    private void gameMode(GameMode now) {
        var player = state.player();
        var old = player.gameMode();
        player.gameMode(now);
        if (old != now && observations.wants(GameModeChanged.class)) {
            observations.emit(new GameModeChanged(old, now));
        }
    }

    /**
     * Login (play)、Respawn、Game Event 3 设置的游戏模式：同时按游戏模式设置能力（原版 {@code MultiPlayerGameMode.setLocalMode}）
     */
    private void localGameMode(GameMode now) {
        gameMode(now);
        state.player().abilities().gameMode(now);
    }

    // 实体

    /**
     * Spawn Entity（原版 {@code handleAddEntity}）：这个编号已有实体时，先按 {@code ClientLevel.addEntity} 移除它
     * （{@code setRemoved}，乘坐关系的处理与 Remove Entities 相同，产生 {@code EntityRemoved}），但不记下被移除的载具编号
     * （只有 {@code handleRemoveEntities} 记下），然后加入新的实体
     */
    private void spawnEntity(ClientboundAddEntityPacket spawn) {
        // 原版 handleAddEntity：这个编号的实体重新出现，之后的数据包不再应用到本地玩家
        if (Objects.equals(removedPlayerVehicle, spawn.getEntityId())) {
            removedPlayerVehicle = null;
        }
        var old = world().entities().remove(spawn.getEntityId());
        if (old != null) {
            stopRiding(old);
            if (observations.wants(EntityRemoved.class)) {
                observations.emit(new EntityRemoved(old.snapshot()));
            }
        }
        var entity = new ClientEntity(spawn.getEntityId(), spawn.getUuid(), spawn.getType(), spawn.getData(),
                new Pos(spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()), spawn.getHeadYaw(),
                Vectors.vec(spawn.getMovement()));
        world().entities().add(entity);
        if (observations.wants(EntitySpawned.class)) {
            observations.emit(new EntitySpawned(entity.snapshot()));
        }
    }

    /**
     * 本地玩家的实体编号不在实体模型中：服务端对它发送的实体移动包被忽略（原版客户端行为）
     */
    private @Nullable ClientEntity otherEntity(int entityId) {
        if (entityId == state.player().entityId()) {
            return null;
        }
        return world().entities().get(entityId);
    }

    private @Nullable Map<Integer, EntityMetadata<?, ?>> entityMetadata(int entityId) {
        var entity = world().entities().get(entityId);
        return entity == null ? null : entity.metadata();
    }

    /**
     * Update Entity Position (and Rotation)：MCProtocolLib 已经把位移除以 4096
     */
    private void moveEntity(int entityId, double dx, double dy, double dz, @Nullable Float yaw, @Nullable Float pitch,
                            boolean onGround) {
        var entity = otherEntity(entityId);
        if (entity == null) {
            return;
        }
        var from = entity.position();
        var to = from.add(dx, dy, dz);
        if (yaw != null && pitch != null) {
            to = to.withView(yaw, pitch);
        }
        entityMoved(entity, to, onGround);
    }

    /**
     * Teleport Entity（原版 {@code handleEntityPositionSync}）：设置绝对位置、朝向与是否着地，不使用数据包中的速度。
     * 由本地玩家控制的实体（原版 {@code isLocalInstanceAuthoritative}）不改变：原版只更新相对移动的基准位置，本库不记录它
     */
    private void positionSync(ClientboundEntityPositionSyncPacket sync) {
        var entity = otherEntity(sync.getId());
        if (entity == null || bot.movement().controlledLocally(entity)) {
            return;
        }
        var position = sync.getPosition();
        entityMoved(entity, new Pos(position.getX(), position.getY(), position.getZ(), sync.getYRot(), sync.getXRot()),
                sync.isOnGround());
    }

    /**
     * Synchronize Vehicle Position（原版 {@code handleTeleportEntity}）：位置、朝向与速度按 Synchronize Player Position 的计算得出
     * （{@link #calculateAbsolute}：相对字段、俯仰角限制到 [-90, 90]、带 ROTATE_DELTA 时旋转当前速度）。
     * 实体不在模型中、并且是被移除的本地玩家乘坐过的实体时，应用到本地玩家；编号是本地玩家自己的时见 {@link #teleportLocalPlayer}
     */
    private void teleportEntity(ClientboundTeleportEntityPacket teleport) {
        if (teleport.getId() == state.player().entityId()) {
            teleportLocalPlayer(teleport);
            return;
        }
        var entity = otherEntity(teleport.getId());
        if (entity == null) {
            if (Objects.equals(removedPlayerVehicle, teleport.getId())) {
                teleportToRemovedVehicle(teleport);
            }
            return;
        }
        var absolute = calculateAbsolute(entity.position(), entity.velocity(), Vectors.vec(teleport.getPosition()),
                Vectors.vec(teleport.getDeltaMovement()), teleport.getYRot(), teleport.getXRot(), relatives(teleport.getRelatives()));
        entity.velocity(absolute.velocity());
        entityMoved(entity, absolute.position(), teleport.isOnGround());
    }

    /**
     * 原版 {@code handleTeleportEntity} 中实体是本地玩家的情况（原版本地玩家在世界的实体中）：按 {@link #calculateAbsolute} 得出
     * 新的位置、朝向与速度，速度立即设置。本地玩家所在区块已加载（原版 {@code isTickingEntity}）或者 X、Y、Z 中有相对字段，
     * 并且新位置与当前位置的距离不超过 64 时，位置与朝向在之后的移动计算中插值（{@link Interpolation}，原版 {@code moveOrInterpolateTo}）；
     * 否则立即设置位置与朝向，产生 {@code PlayerTeleported}。然后设置着地标记（原版 {@code setOnGround}）。不发送任何数据包。
     * 与服务端的其它传送相同，之前 rotate、lookAt 设置的朝向不再生效，清空上一次的模拟结果并触发 moveTo 重新寻路
     */
    private void teleportLocalPlayer(ClientboundTeleportEntityPacket teleport) {
        var player = state.player();
        var old = player.position();
        var relatives = relatives(teleport.getRelatives());
        var absolute = calculateAbsolute(old, player.velocity(), Vectors.vec(teleport.getPosition()),
                Vectors.vec(teleport.getDeltaMovement()), teleport.getYRot(), teleport.getXRot(), relatives);
        var relative = relatives.contains(PositionElement.X) || relatives.contains(PositionElement.Y)
                || relatives.contains(PositionElement.Z);
        var ticking = world().chunkLoaded(old.chunkX(), old.chunkZ());
        var interpolate = (ticking || relative) && old.distanceSquared(absolute.position()) <= 4096.0;
        player.velocity(absolute.velocity());
        if (interpolate) {
            player.interpolation(Interpolation.start(player.interpolation(), old, absolute.position()));
        } else {
            player.position(absolute.position());
        }
        Kinematics.setOnGround(player, world(), teleport.isOnGround());
        bot.movement().onServerRotation();
        bot.movement().onTeleport();
        if (!interpolate && observations.wants(PlayerTeleported.class)) {
            observations.emit(new PlayerTeleported(old, absolute.position()));
        }
    }

    private void entityMoved(ClientEntity entity, Pos to, boolean onGround) {
        var from = entity.position();
        entity.position(to, onGround);
        if (observations.wants(EntityMoved.class)) {
            observations.emit(new EntityMoved(entity.id(), from.asVec(), to.asVec(), onGround));
        }
    }

    /**
     * Set Passengers（原版 {@code handleSetEntityPassengersPacket}）：载具不在模型中时整个数据包被忽略（原版客户端行为）。
     * 载具现有的乘客全部停止乘坐（{@code ejectPassengers}），然后列出的乘客按顺序乘坐它（{@code startRiding}，见 {@link #startRiding}），
     * 模型中没有的乘客编号被跳过。本地玩家在列出的乘客中时清除记下的被移除载具编号；新登上船或竹筏时（之前没有直接或间接乘坐它），
     * 偏航角设为船的偏航角。这两项与乘坐是否成功无关
     */
    private void passengers(ClientboundSetPassengersPacket packet) {
        var world = world();
        var vehicleId = packet.getEntityId();
        var vehicle = world.entities().get(vehicleId);
        if (vehicle == null) {
            return;
        }
        var player = state.player();
        var wasMounted = ridesIndirectly(player, vehicleId);
        for (var previous : vehicle.passengers()) {
            if (previous == player.entityId()) {
                player.vehicle(null);
            } else {
                Objects.requireNonNull(world.entities().get(previous), () -> "unknown passenger " + previous).vehicle(null);
            }
        }
        vehicle.passengers().clear();
        for (var passenger : packet.getPassengerIds()) {
            if (passenger == player.entityId()) {
                startRiding(passenger, vehicle);
                removedPlayerVehicle = null;
                if (!wasMounted && RidingSeat.boat(vehicle.type())) {
                    player.position(player.position().withYaw(vehicle.position().yaw()));
                }
            } else if (world.entities().get(passenger) != null) {
                startRiding(passenger, vehicle);
            }
        }
    }

    /**
     * 原版 {@code Entity.startRiding(vehicle, force = true, ...)}：已经乘坐这个载具、载具不接受乘客（标记、不祥之物生成器），
     * 或者乘客是载具直接或间接乘坐的实体（乘坐之后会形成循环）时不乘坐；否则先离开原来的载具，再加入这个载具的乘客列表末尾。
     * 实体乘坐自己时原版会在之后的乘坐计算中无限循环，这里抛出异常
     *
     * @param rider 模型中的实体或本地玩家的编号
     */
    private void startRiding(int rider, ClientEntity vehicle) {
        if (rider == vehicle.id()) {
            throw new IllegalStateException("entity " + rider + " cannot ride itself");
        }
        if (Objects.equals(vehicleOf(rider), vehicle.id())) {
            return;
        }
        if (vehicle.type() == EntityType.MARKER || vehicle.type() == EntityType.OMINOUS_ITEM_SPAWNER) {
            return;
        }
        for (var below = vehicleOf(vehicle.id()); below != null; below = vehicleOf(below)) {
            if (below == rider) {
                return;
            }
        }
        var old = vehicleOf(rider);
        if (old != null) {
            var oldVehicle = world().entities().get(old);
            if (oldVehicle != null) {
                oldVehicle.passengers().rem(rider);
            }
        }
        var player = state.player();
        if (rider == player.entityId()) {
            player.vehicle(vehicle.id());
        } else {
            Objects.requireNonNull(world().entities().get(rider)).vehicle(vehicle.id());
        }
        vehicle.passengers().add(rider);
    }

    /**
     * 本地玩家或模型中的实体乘坐的实体；没有乘坐、或者实体不在模型中时为 null
     */
    private @Nullable Integer vehicleOf(int id) {
        var player = state.player();
        if (id == player.entityId()) {
            return player.vehicle();
        }
        var entity = world().entities().get(id);
        return entity == null ? null : entity.vehicle();
    }

    /**
     * 本地玩家是否直接或间接（乘坐它的乘客）乘坐这个实体（原版 {@code hasIndirectPassenger}）
     */
    private boolean ridesIndirectly(LocalPlayer player, int vehicleId) {
        var current = player.vehicle();
        while (current != null) {
            if (current == vehicleId) {
                return true;
            }
            var entity = world().entities().get(current);
            current = entity == null ? null : entity.vehicle();
        }
        return false;
    }

    private void clearVehicle(int passenger, int vehicle) {
        var entity = world().entities().get(passenger);
        if (entity != null && Objects.equals(entity.vehicle(), vehicle)) {
            entity.vehicle(null);
        }
    }

    /**
     * 原版 {@code Entity.setRemoved}：被移除的实体离开它乘坐的载具，它的乘客（包括本地玩家）停止乘坐
     */
    private void stopRiding(ClientEntity removed) {
        var vehicleId = removed.vehicle();
        if (vehicleId != null) {
            var vehicle = world().entities().get(vehicleId);
            if (vehicle != null) {
                vehicle.passengers().rem(removed.id());
            }
        }
        if (Objects.equals(state.player().vehicle(), removed.id())) {
            state.player().vehicle(null);
        }
        for (var passenger : removed.passengers()) {
            clearVehicle(passenger, removed.id());
        }
    }

    // 本地玩家

    /**
     * Synchronize Player Position（原版 {@code handleMovePlayer}）：骑乘时位置、朝向、速度都不改变；
     * 不骑乘时按原版 {@code PositionMoveRotation.calculateAbsolute} 计算：带 ROTATE_DELTA 时，先把当前速度绕 x 轴旋转
     * 「原俯仰角 − 新俯仰角」、再绕 y 轴旋转「原偏航角 − 新偏航角」（原版 {@code Vec3.xRot}、{@code yRot}，见 {@link SineTable}），
     * 然后带 DELTA 标志的分量为旋转后的速度加数据包的值，其余取数据包的值。
     * 之后发送 Confirm Teleportation，再发送当前位置与朝向的 Set Player Position and Rotation（on ground 与 pushing against wall 为 false）
     */
    private void teleport(ClientboundPlayerPositionPacket teleport) {
        var player = state.player();
        var old = player.position();
        var position = old;
        if (player.vehicle() == null) {
            position = applyPosition(player, Vectors.vec(teleport.getPosition()), Vectors.vec(teleport.getDeltaMovement()),
                    teleport.getYRot(), teleport.getXRot(), relatives(teleport.getRelatives()));
            bot.movement().onServerRotation();
        }
        outbox.send(new ServerboundAcceptTeleportationPacket(teleport.getId()));
        outbox.send(positionAndRotation(position));
        bot.movement().onTeleport();
        if (observations.wants(PlayerTeleported.class)) {
            observations.emit(new PlayerTeleported(old, position));
        }
    }

    /**
     * 原版 {@code handleTeleportEntity} 中实体不存在的分支：本地玩家乘坐过的实体被移除之后，对这个编号的
     * Synchronize Vehicle Position 按 Synchronize Player Position 的计算应用到本地玩家，然后发送当前位置与朝向的
     * Set Player Position and Rotation（on ground 与 pushing against wall 为 false），不发送 Confirm Teleportation
     */
    private void teleportToRemovedVehicle(ClientboundTeleportEntityPacket teleport) {
        var player = state.player();
        var old = player.position();
        var position = applyPosition(player, Vectors.vec(teleport.getPosition()), Vectors.vec(teleport.getDeltaMovement()),
                teleport.getYRot(), teleport.getXRot(), relatives(teleport.getRelatives()));
        bot.movement().onServerRotation();
        outbox.send(positionAndRotation(position));
        bot.movement().onTeleport();
        if (observations.wants(PlayerTeleported.class)) {
            observations.emit(new PlayerTeleported(old, position));
        }
    }

    /**
     * 当前位置与朝向的 Set Player Position and Rotation，on ground 与 pushing against wall 为 false
     */
    private static ServerboundMovePlayerPosRotPacket positionAndRotation(Pos position) {
        return new ServerboundMovePlayerPosRotPacket(false, false, position.x(), position.y(), position.z(),
                position.yaw(), position.pitch());
    }

    private static Set<PositionElement> relatives(List<PositionElement> relatives) {
        var result = EnumSet.noneOf(PositionElement.class);
        result.addAll(relatives);
        return result;
    }

    /**
     * 原版 {@code setValuesFromPositionPacket}（不插值）：按 {@link #calculateAbsolute} 设置本地玩家的位置、朝向与速度
     *
     * @return 新的位置与朝向
     */
    private static Pos applyPosition(LocalPlayer player, Point change, Point delta, float changeYaw, float changePitch,
                                     Set<PositionElement> relatives) {
        var absolute = calculateAbsolute(player.position(), player.velocity(), change, delta, changeYaw, changePitch, relatives);
        player.velocity(absolute.velocity());
        player.position(absolute.position());
        return absolute.position();
    }

    /**
     * 原版 {@code PositionMoveRotation.calculateAbsolute}：带 X、Y、Z、Y_ROT（偏航角）、X_ROT（俯仰角）标志的字段为当前值加数据包的值，
     * 其余取数据包的值，俯仰角再限制到 [-90, 90]；带 ROTATE_DELTA 时，先把当前速度绕 x 轴旋转「原俯仰角 − 新俯仰角」、
     * 再绕 y 轴旋转「原偏航角 − 新偏航角」（原版 {@code Vec3.xRot}、{@code yRot}，见 {@link SineTable}），
     * 然后带 DELTA 标志的分量为旋转后的速度加数据包的值，其余取数据包的值
     *
     * @param old      当前的位置与朝向
     * @param velocity 当前的速度
     */
    private static Absolute calculateAbsolute(Pos old, Vec velocity, Point change, Point delta, float changeYaw, float changePitch,
                                              Set<PositionElement> relatives) {
        var yaw = relatives.contains(PositionElement.Y_ROT) ? old.yaw() + changeYaw : changeYaw;
        var pitch = Math.clamp(relatives.contains(PositionElement.X_ROT) ? old.pitch() + changePitch : changePitch, -90f, 90f);
        if (relatives.contains(PositionElement.ROTATE_DELTA)) {
            velocity = SineTable.xRot(velocity, (float) Math.toRadians(old.pitch() - pitch));
            velocity = SineTable.yRot(velocity, (float) Math.toRadians(old.yaw() - yaw));
        }
        var position = new Pos(
                relatives.contains(PositionElement.X) ? old.x() + change.x() : change.x(),
                relatives.contains(PositionElement.Y) ? old.y() + change.y() : change.y(),
                relatives.contains(PositionElement.Z) ? old.z() + change.z() : change.z(),
                yaw, pitch);
        return new Absolute(position, new Vec(
                relatives.contains(PositionElement.DELTA_X) ? velocity.x() + delta.x() : delta.x(),
                relatives.contains(PositionElement.DELTA_Y) ? velocity.y() + delta.y() : delta.y(),
                relatives.contains(PositionElement.DELTA_Z) ? velocity.z() + delta.z() : delta.z()));
    }

    /**
     * {@link #calculateAbsolute} 得出的新位置、朝向与速度
     */
    private record Absolute(Pos position, Vec velocity) {
    }

    private void health(ClientboundSetHealthPacket health) {
        var player = state.player();
        var old = player.health();
        player.health(health.getHealth(), health.getFood(), health.getSaturation());
        if (observations.wants(HealthChanged.class)) {
            observations.emit(new HealthChanged(old, health.getHealth(), health.getFood(), health.getSaturation()));
        }
        if (health.getHealth() <= 0) {
            playerDied();
        }
    }

    /**
     * 玩家死亡：移动与手部两条队列中执行中和排队中的动作被取消
     */
    private void playerDied() {
        var player = state.player();
        if (player.dead()) {
            return;
        }
        player.dead(true);
        bot.movement().cancelAll(ActionCancelledException.PLAYER_DIED);
        bot.hand().cancelAll(ActionCancelledException.PLAYER_DIED);
    }

    private void heldSlot(int slot) {
        var player = state.player();
        var old = player.heldSlot();
        player.heldSlot(slot);
        if (old != slot && observations.wants(HeldSlotChanged.class)) {
            observations.emit(new HeldSlotChanged(old, slot));
        }
    }

    /**
     * Open Horse Screen（原版 {@code handleMountScreenOpen}）：实体是马类（原版 {@code AbstractHorse}）时打开马的窗口；
     * 是鹦鹉螺（原版 {@code AbstractNautilus}）时打开鹦鹉螺的窗口，只有鞍与身体护甲 2 格，不看列数（原版 {@code NautilusInventoryMenu}）。
     * 实体不在模型中或者是其它类型时什么都不做，打开着的窗口保持打开（原版客户端行为）
     */
    private void openMount(ClientboundMountScreenOpenPacket open) {
        var entity = world().entities().get(open.getEntityId());
        if (entity == null) {
            return;
        }
        int columns;
        if (ClientEntity.HORSES.contains(entity.type())) {
            columns = open.getInventoryColumns();
        } else if (ClientEntity.NAUTILUSES.contains(entity.type())) {
            columns = 0;
        } else {
            return;
        }
        state.inventory().openHorse(open.getContainerId(), columns, slotSink());
        if (observations.wants(WindowOpened.class)) {
            observations.emit(new WindowOpened(open.getContainerId(), null, Component.empty()));
        }
    }

    private InventoryModel.SlotChangeSink slotSink() {
        return new InventoryModel.SlotChangeSink() {
            @Override
            public void slotChanged(int windowId, int slot, @Nullable ItemStack old, @Nullable ItemStack now) {
                if (observations.wants(SlotChanged.class)) {
                    observations.emit(new SlotChanged(windowId, slot, old, now));
                }
            }

            @Override
            public void cursorChanged(@Nullable ItemStack old, @Nullable ItemStack now) {
                if (observations.wants(CursorChanged.class)) {
                    observations.emit(new CursorChanged(old, now));
                }
            }

            @Override
            public void equipmentChanged(EquipmentSlot slot, @Nullable ItemStack old, @Nullable ItemStack now) {
                if (observations.wants(EntityEquipmentChanged.class)) {
                    observations.emit(new EntityEquipmentChanged(state.player().entityId(), slot, old, now));
                }
            }
        };
    }

    // 聊天与界面

    /**
     * Player Chat Message（原版 {@code handlePlayerChat} 与 {@code ChatListener}）：检查全局编号，解析已读列表中按编号引用的签名，
     * 把签名放进缓存，然后决定是否显示：
     * <ul>
     *     <li>发送者不在玩家列表中时不显示这条消息，日志中记下原版代替它显示的 {@code chat.validation_error}（原版 {@code handleChatMessageError}）；</li>
     *     <li>完全过滤（FULLY_FILTERED）的消息不显示，不写入日志；</li>
     *     <li>其余显示为未签名内容，没有时为消息正文。</li>
     * </ul>
     * 带签名时交给已读跟踪：显示的消息计入已读，没有显示的只计入未确认的消息数（原版 {@code markMessageAsProcessed}）
     *
     * @throws UnsupportedOperationException 部分过滤（PARTIALLY_FILTERED）的消息
     */
    private void playerChat(ClientboundPlayerChatPacket chat) {
        // TODO(T2): MCProtocolLib 的 ClientboundPlayerChatPacket 在 PARTIALLY_FILTERED 时不读取过滤标记的 BitSet，
        //  之后的聊天类型、发送者名称与目标名称都从错误的位置读出。本库不支持部分过滤的消息，见 bot/README.md 的待办
        if (chat.getFilterMask() == ChatFilterType.PARTIALLY_FILTERED) {
            throw new UnsupportedOperationException("partially filtered player chat is not supported");
        }
        var chatType = chat.getChatType();
        var chatTypeName = chatTypeName(chatType);
        state.chat().checkGlobalIndex(chat.getGlobalIndex());
        var lastSeen = new ArrayList<Signature>();
        for (var packed : chat.getLastSeenMessages()) {
            lastSeen.add(signature(packed));
        }
        var signature = chat.getMessageSignature() == null ? null : new Signature(chat.getMessageSignature());
        state.chat().signatures().push(lastSeen, signature);
        boolean shown;
        if (!state.hud().hasPlayer(chat.getSender())) {
            shown = false;
            chatLine(new ChatLine(ChatKind.PLAYER, chat.getSender(), chatTypeName, inlineChatType(chatType), chat.getName(),
                    chat.getTargetName(), CHAT_VALIDATION_ERROR, PLAIN.serialize(CHAT_VALIDATION_ERROR)), null);
        } else if (chat.getFilterMask() == ChatFilterType.FULLY_FILTERED) {
            shown = false;
        } else {
            shown = true;
            var message = chat.getUnsignedContent() != null ? chat.getUnsignedContent() : Component.text(chat.getContent());
            chatLine(new ChatLine(ChatKind.PLAYER, chat.getSender(), chatTypeName, inlineChatType(chatType), chat.getName(),
                    chat.getTargetName(), message, PLAIN.serialize(message)), signature);
        }
        if (signature != null) {
            var tracker = state.chat().lastSeen();
            tracker.add(signature, shown);
            if (tracker.offset() > LastSeenTracker.ACKNOWLEDGE_THRESHOLD) {
                outbox.send(new ServerboundChatAckPacket(tracker.takeOffset()));
            }
        }
    }

    /**
     * 按编号引用的签名从签名缓存中取出（MCProtocolLib 已经把编号减去 1），否则为数据包中的完整签名
     */
    private Signature signature(MessageSignature packed) {
        var bytes = packed.getMessageSignature();
        return bytes != null ? new Signature(bytes) : state.chat().signatures().get(packed.getId());
    }

    /**
     * Player Chat Message 与 Disguised Chat Message 的聊天类型为注册表中的编号时（MCProtocolLib 已经减去 1），
     * 换成 {@code minecraft:chat_type} 注册表中的名称；随数据包给出定义时为 null
     *
     * @throws IllegalStateException 编号不在注册表中（原版解码时出错，客户端断开连接）
     */
    private @Nullable Key chatTypeName(Holder<ChatType> chatType) {
        return chatType.isId() ? state.registries().requireEntry(SyncedRegistries.CHAT_TYPE, chatType.id()) : null;
    }

    /**
     * 随数据包给出的聊天类型定义；聊天类型为注册表中的编号时为 null
     */
    private static @Nullable ChatType inlineChatType(Holder<ChatType> chatType) {
        return chatType.isCustom() ? chatType.custom() : null;
    }

    private void chatLine(ChatLine line, @Nullable Signature signature) {
        state.chat().add(line, signature);
        if (observations.wants(ChatReceived.class)) {
            observations.emit(new ChatReceived(line));
        }
    }

    private void actionBar(Component text) {
        state.hud().actionBar(text);
        if (observations.wants(ActionBarShown.class)) {
            observations.emit(new ActionBarShown(text));
        }
    }

    private void titleShown() {
        if (observations.wants(TitleShown.class)) {
            observations.emit(new TitleShown(state.hud().title(), state.hud().subtitle()));
        }
    }
}
