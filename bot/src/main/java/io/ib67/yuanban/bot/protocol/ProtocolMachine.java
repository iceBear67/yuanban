package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.ResourcePackPolicy;
import io.ib67.yuanban.bot.chat.LastSeenTracker;
import io.ib67.yuanban.bot.decode.DecodeContext;
import io.ib67.yuanban.bot.decode.DecodedChunk;
import io.ib67.yuanban.bot.decode.SectionDecoder;
import io.ib67.yuanban.bot.entity.ClientEntity;
import io.ib67.yuanban.bot.inventory.InventoryModel;
import io.ib67.yuanban.bot.movement.RidingSeat;
import io.ib67.yuanban.bot.movement.SineTable;
import io.ib67.yuanban.bot.observe.ActionBarShown;
import io.ib67.yuanban.bot.observe.BlockChanged;
import io.ib67.yuanban.bot.observe.BlockEntityChanged;
import io.ib67.yuanban.bot.observe.BossBarChanged;
import io.ib67.yuanban.bot.observe.ChatReceived;
import io.ib67.yuanban.bot.observe.ChunkLoaded;
import io.ib67.yuanban.bot.observe.ChunkUnloaded;
import io.ib67.yuanban.bot.observe.CursorChanged;
import io.ib67.yuanban.bot.observe.EffectChanged;
import io.ib67.yuanban.bot.observe.EntityDamaged;
import io.ib67.yuanban.bot.observe.EntityEquipmentChanged;
import io.ib67.yuanban.bot.observe.EntityMetadataChanged;
import io.ib67.yuanban.bot.observe.EntityMoved;
import io.ib67.yuanban.bot.observe.EntityRemoved;
import io.ib67.yuanban.bot.observe.EntitySpawned;
import io.ib67.yuanban.bot.observe.EntityStatusReceived;
import io.ib67.yuanban.bot.observe.ExperienceChanged;
import io.ib67.yuanban.bot.observe.GameModeChanged;
import io.ib67.yuanban.bot.observe.HealthChanged;
import io.ib67.yuanban.bot.observe.HeldSlotChanged;
import io.ib67.yuanban.bot.observe.ItemPickedUp;
import io.ib67.yuanban.bot.observe.PlayerDied;
import io.ib67.yuanban.bot.observe.PlayerTeleported;
import io.ib67.yuanban.bot.observe.ScoreChanged;
import io.ib67.yuanban.bot.observe.SlotChanged;
import io.ib67.yuanban.bot.observe.TitleShown;
import io.ib67.yuanban.bot.observe.WindowClosed;
import io.ib67.yuanban.bot.observe.WindowOpened;
import io.ib67.yuanban.bot.observe.WorldEntered;
import io.ib67.yuanban.bot.observe.WorldEntryCause;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.observe.dispatch.ObservationDispatcher;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.registry.TagTable;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import io.ib67.yuanban.bot.scheduler.BotState;
import io.ib67.yuanban.bot.view.ChatKind;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.world.ClientWorld;
import it.unimi.dsi.fastutil.longs.LongList;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.crypto.MessageSignature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.message.ChatType;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.common.ClientCookieResponsePacket;
import net.minestom.server.network.packet.client.common.ClientKeepAlivePacket;
import net.minestom.server.network.packet.client.common.ClientPluginMessagePacket;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.common.ClientResourcePackStatusPacket;
import net.minestom.server.network.packet.client.common.ClientSettingsPacket;
import net.minestom.server.network.packet.client.configuration.ClientAcceptCodeOfConductPacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.configuration.ClientSelectKnownPacksPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginPluginResponsePacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.play.ClientChatAckPacket;
import net.minestom.server.network.packet.client.play.ClientChunkBatchReceivedPacket;
import net.minestom.server.network.packet.client.play.ClientCloseWindowPacket;
import net.minestom.server.network.packet.client.play.ClientConfigurationAckPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.CookieRequestPacket;
import net.minestom.server.network.packet.server.common.CookieStorePacket;
import net.minestom.server.network.packet.server.common.DisconnectPacket;
import net.minestom.server.network.packet.server.common.KeepAlivePacket;
import net.minestom.server.network.packet.server.common.PingPacket;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import net.minestom.server.network.packet.server.common.ResourcePackPushPacket;
import net.minestom.server.network.packet.server.common.ServerLinksPacket;
import net.minestom.server.network.packet.server.common.TagsPacket;
import net.minestom.server.network.packet.server.common.TransferPacket;
import net.minestom.server.network.packet.server.configuration.CodeOfConductPacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.configuration.ResetChatPacket;
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket;
import net.minestom.server.network.packet.server.configuration.UpdateEnabledFeaturesPacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.packet.server.login.LoginPluginRequestPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import net.minestom.server.network.packet.server.play.ActionBarPacket;
import net.minestom.server.network.packet.server.play.AttachEntityPacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.network.packet.server.play.BlockEntityDataPacket;
import net.minestom.server.network.packet.server.play.BossBarPacket;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.network.packet.server.play.ChunkBatchFinishedPacket;
import net.minestom.server.network.packet.server.play.ChunkBatchStartPacket;
import net.minestom.server.network.packet.server.play.ChunkBiomesPacket;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.network.packet.server.play.ClearTitlesPacket;
import net.minestom.server.network.packet.server.play.CloseWindowPacket;
import net.minestom.server.network.packet.server.play.CollectItemPacket;
import net.minestom.server.network.packet.server.play.DamageEventPacket;
import net.minestom.server.network.packet.server.play.DeathCombatEventPacket;
import net.minestom.server.network.packet.server.play.DeleteChatPacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.DisguisedChatPacket;
import net.minestom.server.network.packet.server.play.DisplayScoreboardPacket;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.packet.server.play.EntityEffectPacket;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityPositionAndRotationPacket;
import net.minestom.server.network.packet.server.play.EntityPositionPacket;
import net.minestom.server.network.packet.server.play.EntityPositionSyncPacket;
import net.minestom.server.network.packet.server.play.EntityRotationPacket;
import net.minestom.server.network.packet.server.play.EntityStatusPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.ExplosionPacket;
import net.minestom.server.network.packet.server.play.HeldItemChangePacket;
import net.minestom.server.network.packet.server.play.InitializeWorldBorderPacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import net.minestom.server.network.packet.server.play.MultiBlockChangePacket;
import net.minestom.server.network.packet.server.play.OpenHorseWindowPacket;
import net.minestom.server.network.packet.server.play.OpenWindowPacket;
import net.minestom.server.network.packet.server.play.PlayerAbilitiesPacket;
import net.minestom.server.network.packet.server.play.PlayerChatMessagePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.PlayerListHeaderAndFooterPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import net.minestom.server.network.packet.server.play.PlayerRotationPacket;
import net.minestom.server.network.packet.server.play.RemoveEntityEffectPacket;
import net.minestom.server.network.packet.server.play.ResetScorePacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.network.packet.server.play.ScoreboardObjectivePacket;
import net.minestom.server.network.packet.server.play.ServerDataPacket;
import net.minestom.server.network.packet.server.play.ServerDifficultyPacket;
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import net.minestom.server.network.packet.server.play.SetCursorItemPacket;
import net.minestom.server.network.packet.server.play.SetExperiencePacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.SetTickStatePacket;
import net.minestom.server.network.packet.server.play.SetTimePacket;
import net.minestom.server.network.packet.server.play.SetTitleSubTitlePacket;
import net.minestom.server.network.packet.server.play.SetTitleTextPacket;
import net.minestom.server.network.packet.server.play.SetTitleTimePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.SpawnPositionPacket;
import net.minestom.server.network.packet.server.play.StartConfigurationPacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.network.packet.server.play.TickStepPacket;
import net.minestom.server.network.packet.server.play.TradeListPacket;
import net.minestom.server.network.packet.server.play.UnloadChunkPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.network.packet.server.play.UpdateScorePacket;
import net.minestom.server.network.packet.server.play.UpdateSimulationDistancePacket;
import net.minestom.server.network.packet.server.play.UpdateViewDistancePacket;
import net.minestom.server.network.packet.server.play.UpdateViewPositionPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import net.minestom.server.network.packet.server.play.WindowPropertyPacket;
import net.minestom.server.network.packet.server.play.WorldBorderCenterPacket;
import net.minestom.server.network.packet.server.play.WorldBorderLerpSizePacket;
import net.minestom.server.network.packet.server.play.WorldBorderSizePacket;
import net.minestom.server.network.packet.server.play.WorldBorderWarningDelayPacket;
import net.minestom.server.network.packet.server.play.WorldBorderWarningReachPacket;
import net.minestom.server.network.packet.server.play.data.PlayerSpawnInfo;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 协议状态机：在调度线程上应用收到的数据包、产生观察记录、写入自动应答。两种连接共用。
 * 每个数据包产生的模型层观察记录按变化发生的顺序产生，最后产生它的 {@code PacketReceived}。
 */
public final class ProtocolMachine {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /**
     * 原版 {@code ChatListener.CHAT_VALIDATION_ERROR}：发送者不在玩家列表中的玩家聊天，原版显示这段文本代替消息
     */
    private static final Component CHAT_VALIDATION_ERROR = Component.translatable("chat.validation_error",
            NamedTextColor.RED, TextDecoration.ITALIC);
    /**
     * 原版 {@code FilterMask.FILTERED_STYLE}：部分过滤的玩家聊天中被过滤的字符的样式
     */
    private static final Style FILTERED_STYLE = Style.style(NamedTextColor.DARK_GRAY,
            HoverEvent.showText(Component.translatable("chat.filtered")));

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
     * 新连接建立：发送 Handshake 与 Login Start
     *
     * @param transfer 是否为 Transfer 之后的连接（握手意图为 TRANSFER）
     */
    public void startLogin(String serverAddress, int serverPort, boolean transfer) {
        state.checkThread();
        nextJoinCause = transfer ? WorldEntryCause.TRANSFER : WorldEntryCause.JOIN;
        bot.status(BotStatus.LOGIN);
        outbox.send(new ClientHandshakePacket(MinecraftServer.PROTOCOL_VERSION, serverAddress, serverPort,
                transfer ? ClientHandshakePacket.Intent.TRANSFER : ClientHandshakePacket.Intent.LOGIN));
        outbox.send(new ClientLoginStartPacket(bot.username(), offlineUuid(bot.username())));
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

    private void apply(ServerPacket packet, @Nullable Object attachment) {
        switch (packet) {
            // 登录
            case LoginDisconnectPacket disconnect -> bot.disconnected(disconnect.kickMessage());
            case LoginPluginRequestPacket request -> outbox.send(new ClientLoginPluginResponsePacket(request.messageId(),
                    state.options().loginPluginHandler().respond(request.channel(), request.data())));
            case LoginSuccessPacket _ -> {
                outbox.send(new ClientLoginAcknowledgedPacket());
                bot.status(BotStatus.CONFIGURATION);
                outbox.send(new ClientPluginMessagePacket("minecraft:brand",
                        NetworkBuffer.makeArray(NetworkBuffer.STRING, state.options().brand())));
                outbox.send(new ClientSettingsPacket(state.options().clientSettings()));
            }
            // 配置
            case SelectKnownPacksPacket knownPacks -> outbox.send(new ClientSelectKnownPacksPacket(knownPacks.entries().stream()
                    .filter(entry -> entry.namespace().equals(Key.MINECRAFT_NAMESPACE) && entry.id().equals("core")
                            && entry.version().equals(MinecraftServer.VERSION_NAME))
                    .toList()));
            case TagsPacket _ -> state.tags((TagTable) Objects.requireNonNull(attachment));
            case UpdateEnabledFeaturesPacket features -> state.featureFlags(features.features());
            case CodeOfConductPacket _ -> {
                if (state.options().acceptCodeOfConduct()) {
                    outbox.send(new ClientAcceptCodeOfConductPacket());
                }
            }
            case FinishConfigurationPacket _ -> {
                var finished = (DecodeContext.ConfigurationFinished) Objects.requireNonNull(attachment);
                state.registries(finished.registries());
                state.tags(finished.tags());
                // 原版进入游戏阶段时新建 ClientPacketListener，它的 ClientClockManager 随之重新开始
                state.newClocks();
                outbox.send(new ClientFinishConfigurationPacket());
                bot.status(BotStatus.PLAY);
            }
            case ResetChatPacket _ -> state.chat().clear();
            case StartConfigurationPacket _ -> {
                // 原版 handleConfigurationStart：未确认的消息数大于 0 时先发送 Acknowledge Message，然后清除客户端世界（clearClientLevel）
                var unacknowledged = state.chat().lastSeen().takeOffset();
                if (unacknowledged > 0) {
                    outbox.send(new ClientChatAckPacket(unacknowledged));
                }
                leaveWorld();
                outbox.send(new ClientConfigurationAckPacket());
                bot.status(BotStatus.CONFIGURATION);
            }
            // 任意阶段的自动应答
            case KeepAlivePacket keepAlive -> outbox.send(new ClientKeepAlivePacket(keepAlive.id()));
            case PingPacket ping -> outbox.send(new ClientPongPacket(ping.id()));
            case CookieRequestPacket request -> outbox.send(new ClientCookieResponsePacket(request.key(),
                    state.cookies().get(request.key())));
            case CookieStorePacket store -> state.cookies().store(store.key(), store.value());
            case ResourcePackPushPacket push -> resourcePack(push);
            case PluginMessagePacket message -> {
                if (message.channel().equals("minecraft:brand")) {
                    state.serverBrand(NetworkBuffer.wrap(message.data(), 0, message.data().length).read(NetworkBuffer.STRING));
                }
            }
            case ServerLinksPacket links -> state.serverLinks(links.entries());
            case TransferPacket transfer -> bot.transfer(transfer.host(), transfer.port());
            case DisconnectPacket disconnect -> bot.disconnected(disconnect.message());
            case ChunkBatchStartPacket _ -> chunkBatches.start(System.nanoTime());
            case ChunkBatchFinishedPacket finished -> outbox.send(new ClientChunkBatchReceivedPacket(
                    chunkBatches.finish(System.nanoTime(), finished.batchSize())));
            case PlayerPositionAndLookPacket teleport -> teleport(teleport);
            // 世界
            case JoinGamePacket join -> join(join);
            case RespawnPacket respawn -> respawn(respawn);
            case ChunkDataPacket _ -> installChunk((DecodedChunk) Objects.requireNonNull(attachment));
            case UnloadChunkPacket unload -> {
                if (world().unloadChunk(unload.chunkX(), unload.chunkZ()) && observations.wants(ChunkUnloaded.class)) {
                    observations.emit(new ChunkUnloaded(unload.chunkX(), unload.chunkZ()));
                }
            }
            case UpdateViewPositionPacket center -> unloaded(world().center(center.chunkX(), center.chunkZ()));
            case UpdateViewDistancePacket distance -> unloaded(world().viewDistance(distance.viewDistance()));
            case UpdateSimulationDistancePacket distance -> world().simulationDistance(distance.simulationDistance());
            case BlockChangePacket change -> setBlock(change.blockPosition().blockX(), change.blockPosition().blockY(),
                    change.blockPosition().blockZ(), change.blockStateId());
            case MultiBlockChangePacket change -> multiBlockChange(change);
            case ChunkBiomesPacket biomes -> {
                var serializer = SectionDecoder.biomeSerializer(state.registries().biome().size());
                for (var chunk : biomes.chunks()) {
                    world().setBiomes(chunk.chunkX(), chunk.chunkZ(), chunk.data(), serializer);
                }
            }
            case BlockEntityDataPacket data -> blockEntity(data);
            case SetTimePacket time -> {
                // 原版 handleSetTime：世界的 World Age 设为数据包的值，然后时钟按它推进并替换列出的时钟
                world().worldAge(time.gameTime());
                state.clocks().apply(time);
            }
            case SetTickStatePacket tickState -> world().tickState().apply(tickState.tickRate(), tickState.isFrozen());
            case TickStepPacket step -> world().tickState().step(step.steps());
            case InitializeWorldBorderPacket border -> world().border().initialize(border);
            case WorldBorderCenterPacket center -> world().border().center(center.x(), center.z());
            case WorldBorderLerpSizePacket lerp -> world().border().lerp(lerp.oldDiameter(), lerp.newDiameter(), lerp.speed());
            case WorldBorderSizePacket size -> world().border().size(size.diameter());
            case WorldBorderWarningDelayPacket delay -> world().border().warningTime(delay.warningTime());
            case WorldBorderWarningReachPacket reach -> world().border().warningBlocks(reach.warningBlocks());
            case ServerDifficultyPacket difficulty -> world().difficulty(difficulty.difficulty());
            case SpawnPositionPacket spawn -> world().defaultSpawn(spawn.worldPos().blockPosition());
            case ChangeGameStatePacket change -> gameState(change);
            // 实体
            case SpawnEntityPacket spawn -> spawnEntity(spawn);
            case EntityPositionPacket move -> moveEntity(move.entityId(), move.deltaX(), move.deltaY(), move.deltaZ(), null, null, move.onGround());
            case EntityPositionAndRotationPacket move ->
                    moveEntity(move.entityId(), move.deltaX(), move.deltaY(), move.deltaZ(), move.yaw(), move.pitch(), move.onGround());
            case EntityRotationPacket rotation -> {
                var entity = otherEntity(rotation.entityId());
                if (entity != null) {
                    entity.position(entity.position().withView(rotation.yaw(), rotation.pitch()), rotation.onGround());
                }
            }
            case EntityHeadLookPacket headLook -> {
                var entity = otherEntity(headLook.entityId());
                if (entity != null) {
                    entity.headYaw(headLook.yaw());
                }
            }
            case EntityPositionSyncPacket sync -> {
                var entity = otherEntity(sync.entityId());
                if (entity != null) {
                    entity.velocity(sync.delta().asVec());
                    var position = sync.position();
                    entityMoved(entity, new Pos(position.x(), position.y(), position.z(), sync.yaw(), sync.pitch()), sync.onGround());
                }
            }
            case EntityTeleportPacket teleport -> teleportEntity(teleport);
            case EntityVelocityPacket velocity -> {
                if (velocity.entityId() == state.player().entityId()) {
                    state.player().velocity(velocity.velocity());
                } else {
                    var entity = world().entities().get(velocity.entityId());
                    if (entity != null) {
                        entity.velocity(velocity.velocity());
                    }
                }
            }
            case EntityMetaDataPacket metadata -> {
                var target = metadata.entityId() == state.player().entityId() ? state.player().metadata()
                        : entityMetadata(metadata.entityId());
                if (target != null) {
                    target.putAll(metadata.entries());
                    if (observations.wants(EntityMetadataChanged.class)) {
                        observations.emit(new EntityMetadataChanged(metadata.entityId(), metadata.entries()));
                    }
                }
            }
            case EntityEquipmentPacket equipment -> {
                var entity = world().entities().get(equipment.entityId());
                if (entity != null) {
                    equipment.equipments().forEach((slot, item) -> {
                        var old = Objects.requireNonNullElse(entity.equipment().put(slot, item), ItemStack.AIR);
                        if (observations.wants(EntityEquipmentChanged.class)) {
                            observations.emit(new EntityEquipmentChanged(equipment.entityId(), slot, old, item));
                        }
                    });
                }
            }
            case EntityAttributesPacket attributes -> {
                if (attributes.entityId() == state.player().entityId()) {
                    state.player().attributes().apply(attributes.properties());
                } else {
                    var entity = world().entities().get(attributes.entityId());
                    if (entity != null) {
                        entity.attributes().apply(attributes.properties());
                    }
                }
            }
            case EntityEffectPacket effect -> {
                if (effect.entityId() == state.player().entityId()) {
                    state.player().effects().put(effect.potion());
                    if (observations.wants(EffectChanged.class)) {
                        observations.emit(new EffectChanged(effect.potion().effect(), new TimedPotion(effect.potion(), state.ticks())));
                    }
                } else {
                    var entity = world().entities().get(effect.entityId());
                    if (entity != null) {
                        entity.effects().put(effect.potion().effect(), effect.potion());
                    }
                }
            }
            case RemoveEntityEffectPacket remove -> {
                if (remove.entityId() == state.player().entityId()) {
                    state.player().effects().remove(remove.potionEffect());
                    if (observations.wants(EffectChanged.class)) {
                        observations.emit(new EffectChanged(remove.potionEffect(), null));
                    }
                } else {
                    var entity = world().entities().get(remove.entityId());
                    if (entity != null) {
                        entity.effects().remove(remove.potionEffect());
                    }
                }
            }
            case SetPassengersPacket passengers -> passengers(passengers);
            case AttachEntityPacket attach -> {
                var entity = world().entities().get(attach.attachedEntityId());
                if (entity != null) {
                    // 拴绳另一端的实体编号为 -1 时表示解开（wiki Link Entities）
                    entity.leashHolder(attach.holdingEntityId() == -1 ? null : attach.holdingEntityId());
                }
            }
            case DestroyEntitiesPacket destroy -> {
                for (var id : destroy.entityIds()) {
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
            case CollectItemPacket collect -> {
                if (observations.wants(ItemPickedUp.class)) {
                    observations.emit(new ItemPickedUp(collect.collectedEntityId(), collect.collectorEntityId(), collect.pickupItemCount()));
                }
            }
            case EntityStatusPacket status -> {
                if (observations.wants(EntityStatusReceived.class)) {
                    observations.emit(new EntityStatusReceived(status.entityId(), status.status()));
                }
            }
            case DamageEventPacket damage -> {
                if (observations.wants(EntityDamaged.class)) {
                    observations.emit(new EntityDamaged(damage.targetEntityId(), damage.damageType(), damage.sourceEntityId(),
                            damage.sourceDirectId(), damage.sourcePos()));
                }
            }
            // 本地玩家
            case PlayerRotationPacket rotation -> {
                var position = state.player().position();
                var yaw = rotation.relativeYaw() ? position.yaw() + rotation.yaw() : rotation.yaw();
                var pitch = rotation.relativePitch() ? position.pitch() + rotation.pitch() : rotation.pitch();
                state.player().position(position.withView(yaw, Math.clamp(pitch, -90f, 90f)));
                bot.movement().onServerRotation();
            }
            case UpdateHealthPacket health -> health(health);
            case DeathCombatEventPacket death -> {
                if (death.playerId() == state.player().entityId()) {
                    if (observations.wants(PlayerDied.class)) {
                        observations.emit(new PlayerDied(death.message()));
                    }
                    playerDied();
                }
            }
            case PlayerAbilitiesPacket abilities -> state.player().abilities().apply(abilities);
            case SetExperiencePacket experience -> {
                state.player().experience(experience.percentage(), experience.level(), experience.totalExperience());
                if (observations.wants(ExperienceChanged.class)) {
                    observations.emit(new ExperienceChanged(experience.percentage(), experience.level(), experience.totalExperience()));
                }
            }
            case SetCooldownPacket cooldown -> {
                if (cooldown.cooldownTicks() <= 0) {
                    state.player().cooldowns().remove(cooldown.cooldownGroup());
                } else {
                    state.player().cooldowns().put(cooldown.cooldownGroup(), cooldown.cooldownTicks());
                }
            }
            case PlayerInfoUpdatePacket info -> {
                var self = state.player().uuid();
                var listedBefore = state.hud().player(self);
                state.hud().playerInfo(info);
                if (info.actions().contains(PlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) {
                    for (var entry : info.entries()) {
                        // 不在玩家列表中的玩家的更新被忽略，包括自己（原版客户端行为）
                        if (entry.uuid().equals(self) && state.hud().hasPlayer(self)) {
                            // 原版 applyPlayerInfoUpdate：玩家列表中自己原来的游戏模式（新条目为原版 PlayerInfo 的初始值 SURVIVAL）
                            // 与新的不同时调用 LocalPlayer.onGameModeChanged，改成旁观模式时竖直速度清零
                            var previous = listedBefore == null ? GameMode.SURVIVAL : listedBefore.gameMode();
                            if (previous != entry.gameMode() && entry.gameMode() == GameMode.SPECTATOR) {
                                state.player().velocity(state.player().velocity().withY(0));
                            }
                            gameMode(entry.gameMode());
                        }
                    }
                }
            }
            case PlayerInfoRemovePacket remove -> state.hud().removePlayers(remove.uuids());
            case ExplosionPacket explosion -> {
                if (explosion.playerKnockback() != null) {
                    state.player().velocity(state.player().velocity().add(explosion.playerKnockback()));
                }
            }
            case AcknowledgeBlockChangePacket acknowledge -> bot.hand().acknowledge(acknowledge.sequence());
            // 物品栏
            case OpenWindowPacket open -> {
                var type = InventoryType.values()[open.windowType()];
                state.inventory().open(open.windowId(), type, open.title(), slotSink());
                if (observations.wants(WindowOpened.class)) {
                    observations.emit(new WindowOpened(open.windowId(), type, open.title()));
                }
            }
            case OpenHorseWindowPacket open -> openMount(open);
            // 原版 handleContainerClose 不看窗口编号，关闭当前打开的窗口
            case CloseWindowPacket _ -> {
                var closed = state.inventory().closeOpen(slotSink());
                if (closed != null && observations.wants(WindowClosed.class)) {
                    observations.emit(new WindowClosed(closed));
                }
            }
            case WindowItemsPacket items ->
                    state.inventory().windowItems(items.windowId(), items.stateId(), items.items(), items.carriedItem(), slotSink());
            case SetSlotPacket slot ->
                    state.inventory().setSlot(slot.windowId(), slot.stateId(), slot.slot(), slot.itemStack(), slotSink());
            case SetPlayerInventorySlotPacket slot ->
                    state.inventory().setPlayerInventorySlot(slot.slot(), slot.itemStack(), slotSink());
            case SetCursorItemPacket cursor -> state.inventory().cursor(cursor.itemStack(), slotSink());
            case WindowPropertyPacket property ->
                    state.inventory().property(property.windowId(), property.property(), property.value());
            case TradeListPacket trades -> state.inventory().trades(trades);
            case HeldItemChangePacket held -> heldSlot(held.slot());
            // 聊天与界面
            case PlayerChatMessagePacket chat -> playerChat(chat);
            case DisguisedChatPacket chat -> chatLine(new ChatLine(ChatKind.DISGUISED, null, chatType(chat.type()),
                    chat.senderName(), chat.targetName(), chat.message(), PLAIN.serialize(chat.message())), null);
            case SystemChatPacket chat -> {
                if (chat.overlay()) {
                    actionBar(chat.message());
                } else {
                    chatLine(new ChatLine(ChatKind.SYSTEM, null, null, null, null, chat.message(),
                            PLAIN.serialize(chat.message())), null);
                }
            }
            case DeleteChatPacket delete -> {
                var signature = delete.signature().fullSignature() != null ? delete.signature().fullSignature()
                        : state.chat().signatures().get(delete.signature().id());
                // 原版 handleDeleteChat：还没有随 Chat Message 告知服务端的这条消息不再计入已读
                state.chat().lastSeen().ignorePending(signature);
                state.chat().delete(signature);
            }
            case ActionBarPacket actionBar -> actionBar(actionBar.text());
            case SetTitleTextPacket title -> {
                state.hud().title(title.title());
                titleShown();
            }
            case SetTitleSubTitlePacket subtitle -> {
                state.hud().subtitle(subtitle.subtitle());
                titleShown();
            }
            case SetTitleTimePacket times -> state.hud().titleTimes(times.fadeIn(), times.stay(), times.fadeOut());
            case ClearTitlesPacket clear -> state.hud().clearTitles(clear.reset());
            case BossBarPacket bossBar -> {
                if (state.hud().bossBar(bossBar.uuid(), bossBar.action()) && observations.wants(BossBarChanged.class)) {
                    observations.emit(new BossBarChanged(bossBar.uuid(), state.hud().currentBossBar(bossBar.uuid())));
                }
            }
            case PlayerListHeaderAndFooterPacket list -> state.hud().headerFooter(list.header(), list.footer());
            case ScoreboardObjectivePacket objective -> state.hud().objective(objective);
            case DisplayScoreboardPacket display -> state.hud().display(display.position(), display.scoreName());
            case UpdateScorePacket score -> {
                if (state.hud().score(score.entityName(), score.objectiveName(), score.score())
                        && observations.wants(ScoreChanged.class)) {
                    observations.emit(new ScoreChanged(score.objectiveName(), score.entityName(), score.score()));
                }
            }
            case ResetScorePacket reset -> {
                for (var objective : state.hud().resetScore(reset.owner(), reset.objective())) {
                    if (observations.wants(ScoreChanged.class)) {
                        observations.emit(new ScoreChanged(objective, reset.owner(), null));
                    }
                }
            }
            case TeamsPacket team -> state.hud().team(team);
            case ServerDataPacket data -> state.hud().serverMotd(data.motd());
            default -> {
                // 其余数据包没有对应的模型状态，只产生 PacketReceived
            }
        }
    }

    private ClientWorld world() {
        return state.world();
    }

    private void resourcePack(ResourcePackPushPacket push) {
        if (state.options().resourcePackPolicy() == ResourcePackPolicy.DECLINE) {
            outbox.send(new ClientResourcePackStatusPacket(push.id(), ResourcePackStatus.DECLINED));
            return;
        }
        outbox.send(new ClientResourcePackStatusPacket(push.id(), ResourcePackStatus.ACCEPTED));
        outbox.send(new ClientResourcePackStatusPacket(push.id(), ResourcePackStatus.DOWNLOADED));
        outbox.send(new ClientResourcePackStatusPacket(push.id(), ResourcePackStatus.SUCCESSFULLY_LOADED));
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
    private void join(JoinGamePacket join) {
        var cause = nextJoinCause;
        nextJoinCause = WorldEntryCause.RECONFIGURATION;
        var spawnInfo = join.playerSpawnInfo();
        var world = newWorld(spawnInfo, join.viewDistance(), join.simulationDistance());
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
        player.entityId(join.entityId());
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
    private void respawn(RespawnPacket respawn) {
        var current = world();
        var old = state.player();
        var spawnInfo = respawn.playerSpawnInfo();
        var keepData = (respawn.copyData() & 0x02) != 0;
        var open = state.inventory().open();
        if (open != null) {
            outbox.send(new ClientCloseWindowPacket(open.id()));
            state.inventory().closeOpen(slotSink());
            if (observations.wants(WindowClosed.class)) {
                observations.emit(new WindowClosed(open.id()));
            }
        }
        bot.movement().cancelAll(ActionCancelledException.WORLD_REBUILT);
        bot.hand().cancelAll(ActionCancelledException.WORLD_REBUILT);
        var world = Key.key(spawnInfo.world()).equals(current.dimension()) ? current
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
        if ((respawn.copyData() & 0x01) != 0) {
            player.attributes().copyAll(old.attributes());
        } else {
            player.attributes().copyBaseValues(old.attributes());
        }
        state.newPlayer(player);
        bot.movement().newPlayer(keepData);
        enterWorld(world, spawnInfo, WorldEntryCause.RESPAWN);
    }

    private ClientWorld newWorld(PlayerSpawnInfo spawnInfo, int viewDistance, int simulationDistance) {
        var type = Objects.requireNonNull(state.registries().dimensionType().get(spawnInfo.dimensionType()),
                () -> "unknown dimension type id " + spawnInfo.dimensionType());
        return new ClientWorld(Key.key(spawnInfo.world()), spawnInfo.dimensionType(), type.minY(), type.height(),
                viewDistance, simulationDistance);
    }

    private void enterWorld(ClientWorld world, PlayerSpawnInfo spawnInfo, WorldEntryCause cause) {
        var info = new WorldInfo(++worldEpoch, world.dimension(), spawnInfo.dimensionType(), cause);
        state.enterWorld(world, info);
        // 原版 startWaitingForNewLevel：开始等待加载，30 秒的超时从这里开始计算
        state.loadTracker().start(System.nanoTime());
        bot.enteredWorld();
        localGameMode(spawnInfo.gameMode());
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
            if (!collidesWithBlocks(world, position, player.boundingBox())) {
                break;
            }
        }
        player.position(position.withPitch(0));
        player.velocity(Vec.ZERO);
        player.health((float) player.attributes().value(Attribute.MAX_HEALTH), player.food(), player.saturation());
    }

    private static boolean collidesWithBlocks(ClientWorld world, Pos position, BoundingBox box) {
        // 碰撞箱可以超出所在的格子（例如栅栏），所以向四周多检查一格
        var minX = (int) Math.floor(position.x() + box.minX()) - 1;
        var minY = (int) Math.floor(position.y() + box.minY()) - 1;
        var minZ = (int) Math.floor(position.z() + box.minZ()) - 1;
        var maxX = (int) Math.floor(position.x() + box.maxX()) + 1;
        var maxY = (int) Math.floor(position.y() + box.maxY()) + 1;
        var maxZ = (int) Math.floor(position.z() + box.maxZ()) + 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.chunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    var block = world.getBlock(x, y, z, Block.Getter.Condition.TYPE);
                    if (block.collisionShape().intersectBox(position.sub(x, y, z), box)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
     * Update Section Blocks：区段坐标 x 为高 22 位，z 为中间 22 位，y 为低 20 位；每项为状态编号左移 12 位加上区段内坐标
     */
    private void multiBlockChange(MultiBlockChangePacket change) {
        var section = change.chunkSectionPosition();
        var sectionX = (int) (section >> 42);
        var sectionY = (int) (section << 44 >> 44);
        var sectionZ = (int) (section << 22 >> 42);
        for (var entry : change.blocks()) {
            var stateId = (int) (entry >>> 12);
            var x = sectionX * 16 + (int) ((entry >> 8) & 0xF);
            var z = sectionZ * 16 + (int) ((entry >> 4) & 0xF);
            var y = sectionY * 16 + (int) (entry & 0xF);
            setBlock(x, y, z, stateId);
        }
    }

    /**
     * Block Entity Data：原版 {@code handleBlockEntityData} 只应用到 {@code level.getBlockEntity(pos, type)} 存在的方块实体，
     * 即所在区块已加载、位置在维度高度范围之内，并且该位置的方块有同一类型的方块实体；其余情况忽略（原版客户端行为）
     */
    private void blockEntity(BlockEntityDataPacket data) {
        var position = data.blockPosition();
        var world = world();
        if (position.blockY() < world.minY() || position.blockY() >= world.minY() + world.height()
                || !world.chunkLoaded(position.chunkX(), position.chunkZ())
                || !data.type().equals(world.getBlock(position, Block.Getter.Condition.TYPE).blockEntityType())) {
            return;
        }
        var nbt = Objects.requireNonNullElse(data.data(), CompoundBinaryTag.empty());
        world().blockEntities().put(position.blockX(), position.blockY(), position.blockZ(), nbt);
        if (observations.wants(BlockEntityChanged.class)) {
            observations.emit(new BlockEntityChanged(new Vec(position.blockX(), position.blockY(), position.blockZ()), nbt));
        }
    }

    private void gameState(ChangeGameStatePacket change) {
        switch (change.reason()) {
            case CHANGE_GAMEMODE -> localGameMode(GameMode.values()[(int) change.value()]);
            case RAIN_LEVEL_CHANGE -> world().weather().rainLevel(change.value());
            case THUNDER_LEVEL_CHANGE -> world().weather().thunderLevel(change.value());
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

    private void spawnEntity(SpawnEntityPacket spawn) {
        // 原版 handleAddEntity：这个编号的实体重新出现，之后的数据包不再应用到本地玩家
        if (Objects.equals(removedPlayerVehicle, spawn.entityId())) {
            removedPlayerVehicle = null;
        }
        var entity = new ClientEntity(spawn.entityId(), spawn.uuid(), spawn.type(), spawn.data(), spawn.position(),
                spawn.headRot(), spawn.velocity());
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

    private @Nullable Map<Integer, Metadata.Entry<?>> entityMetadata(int entityId) {
        var entity = world().entities().get(entityId);
        return entity == null ? null : entity.metadata();
    }

    /**
     * Update Entity Position (and Rotation)：位移为 {@code delta / 4096}
     */
    private void moveEntity(int entityId, short dx, short dy, short dz, @Nullable Float yaw, @Nullable Float pitch, boolean onGround) {
        var entity = otherEntity(entityId);
        if (entity == null) {
            return;
        }
        var from = entity.position();
        var to = from.add(dx / 4096.0, dy / 4096.0, dz / 4096.0);
        if (yaw != null && pitch != null) {
            to = to.withView(yaw, pitch);
        }
        entityMoved(entity, to, onGround);
    }

    /**
     * Synchronize Vehicle Position（原版 {@code handleTeleportEntity}）：位置、朝向与速度按 Synchronize Player Position 的计算得出
     * （{@link #calculateAbsolute}：相对字段、俯仰角限制到 [-90, 90]、带 ROTATE_DELTA 时旋转当前速度）。
     * 实体不在模型中、并且是被移除的本地玩家乘坐过的实体时，应用到本地玩家
     */
    private void teleportEntity(EntityTeleportPacket teleport) {
        var entity = otherEntity(teleport.entityId());
        if (entity == null) {
            if (Objects.equals(removedPlayerVehicle, teleport.entityId())) {
                teleportToRemovedVehicle(teleport);
            }
            return;
        }
        var target = teleport.position();
        var absolute = calculateAbsolute(entity.position(), entity.velocity(), target, teleport.delta(),
                target.yaw(), target.pitch(), teleport.flags());
        entity.velocity(absolute.velocity());
        entityMoved(entity, absolute.position(), teleport.onGround());
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
    private void passengers(SetPassengersPacket packet) {
        var world = world();
        var vehicleId = packet.vehicleEntityId();
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
        for (var passenger : packet.passengersId()) {
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
    private void teleport(PlayerPositionAndLookPacket teleport) {
        var player = state.player();
        var old = player.position();
        var position = old;
        if (player.vehicle() == null) {
            position = applyPosition(player, teleport.position(), teleport.delta(), teleport.yaw(), teleport.pitch(), teleport.flags());
            bot.movement().onServerRotation();
        }
        outbox.send(new ClientTeleportConfirmPacket(teleport.teleportId()));
        outbox.send(new ClientPlayerPositionAndRotationPacket(position, false, false));
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
    private void teleportToRemovedVehicle(EntityTeleportPacket teleport) {
        var player = state.player();
        var old = player.position();
        var target = teleport.position();
        var position = applyPosition(player, target, teleport.delta(), target.yaw(), target.pitch(), teleport.flags());
        bot.movement().onServerRotation();
        outbox.send(new ClientPlayerPositionAndRotationPacket(position, false, false));
        bot.movement().onTeleport();
        if (observations.wants(PlayerTeleported.class)) {
            observations.emit(new PlayerTeleported(old, position));
        }
    }

    /**
     * 原版 {@code setValuesFromPositionPacket}（不插值）：按 {@link #calculateAbsolute} 设置本地玩家的位置、朝向与速度
     *
     * @return 新的位置与朝向
     */
    private static Pos applyPosition(LocalPlayer player, Point change, Point delta, float changeYaw, float changePitch, int flags) {
        var absolute = calculateAbsolute(player.position(), player.velocity(), change, delta, changeYaw, changePitch, flags);
        player.velocity(absolute.velocity());
        player.position(absolute.position());
        return absolute.position();
    }

    /**
     * 原版 {@code PositionMoveRotation.calculateAbsolute}：带 X、Y、Z、YAW、PITCH 标志的字段为当前值加数据包的值，其余取数据包的值，
     * 俯仰角再限制到 [-90, 90]；带 ROTATE_DELTA 时，先把当前速度绕 x 轴旋转「原俯仰角 − 新俯仰角」、再绕 y 轴旋转「原偏航角 − 新偏航角」
     * （原版 {@code Vec3.xRot}、{@code yRot}，见 {@link SineTable}），然后带 DELTA 标志的分量为旋转后的速度加数据包的值，其余取数据包的值
     *
     * @param old      当前的位置与朝向
     * @param velocity 当前的速度
     */
    private static Absolute calculateAbsolute(Pos old, Vec velocity, Point change, Point delta, float changeYaw, float changePitch, int flags) {
        var yaw = (flags & RelativeFlags.YAW) != 0 ? old.yaw() + changeYaw : changeYaw;
        var pitch = Math.clamp((flags & RelativeFlags.PITCH) != 0 ? old.pitch() + changePitch : changePitch, -90f, 90f);
        if ((flags & RelativeFlags.ROTATE_DELTA) != 0) {
            velocity = SineTable.xRot(velocity, (float) Math.toRadians(old.pitch() - pitch));
            velocity = SineTable.yRot(velocity, (float) Math.toRadians(old.yaw() - yaw));
        }
        var position = new Pos(
                (flags & RelativeFlags.X) != 0 ? old.x() + change.x() : change.x(),
                (flags & RelativeFlags.Y) != 0 ? old.y() + change.y() : change.y(),
                (flags & RelativeFlags.Z) != 0 ? old.z() + change.z() : change.z(),
                yaw, pitch);
        return new Absolute(position, new Vec(
                (flags & RelativeFlags.DELTA_X) != 0 ? velocity.x() + delta.x() : delta.x(),
                (flags & RelativeFlags.DELTA_Y) != 0 ? velocity.y() + delta.y() : delta.y(),
                (flags & RelativeFlags.DELTA_Z) != 0 ? velocity.z() + delta.z() : delta.z()));
    }

    /**
     * {@link #calculateAbsolute} 得出的新位置、朝向与速度
     */
    private record Absolute(Pos position, Vec velocity) {
    }

    private void health(UpdateHealthPacket health) {
        var player = state.player();
        var old = player.health();
        player.health(health.health(), health.food(), health.foodSaturation());
        if (observations.wants(HealthChanged.class)) {
            observations.emit(new HealthChanged(old, health.health(), health.food(), health.foodSaturation()));
        }
        if (health.health() <= 0) {
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
    private void openMount(OpenHorseWindowPacket open) {
        var entity = world().entities().get(open.entityId());
        if (entity == null) {
            return;
        }
        int columns;
        if (ClientEntity.HORSES.contains(entity.type())) {
            columns = open.slotCount();
        } else if (ClientEntity.NAUTILUSES.contains(entity.type())) {
            columns = 0;
        } else {
            return;
        }
        state.inventory().openHorse(open.windowId(), columns, slotSink());
        if (observations.wants(WindowOpened.class)) {
            observations.emit(new WindowOpened(open.windowId(), null, Component.empty()));
        }
    }

    private InventoryModel.SlotChangeSink slotSink() {
        return new InventoryModel.SlotChangeSink() {
            @Override
            public void slotChanged(int windowId, int slot, ItemStack old, ItemStack now) {
                if (observations.wants(SlotChanged.class)) {
                    observations.emit(new SlotChanged(windowId, slot, old, now));
                }
            }

            @Override
            public void cursorChanged(ItemStack old, ItemStack now) {
                if (observations.wants(CursorChanged.class)) {
                    observations.emit(new CursorChanged(old, now));
                }
            }

            @Override
            public void equipmentChanged(EquipmentSlot slot, ItemStack old, ItemStack now) {
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
     *     <li>部分过滤（PARTIALLY_FILTERED）的消息显示为过滤之后的消息正文（{@link #partiallyFiltered}），不使用未签名内容；</li>
     *     <li>其余显示为未签名内容，没有时为消息正文。</li>
     * </ul>
     * 带签名时交给已读跟踪：显示的消息计入已读，没有显示的只计入未确认的消息数（原版 {@code markMessageAsProcessed}）
     */
    private void playerChat(PlayerChatMessagePacket chat) {
        var chatType = chatType(chat.msgTypeId());
        state.chat().checkGlobalIndex(chat.globalIndex());
        var body = chat.messageBody();
        var lastSeen = new ArrayList<MessageSignature>();
        var signatures = state.chat().signatures();
        for (var packed : body.lastSeen().entries()) {
            lastSeen.add(packed.fullSignature() != null ? packed.fullSignature() : signatures.get(packed.id()));
        }
        signatures.push(lastSeen, chat.signature());
        boolean shown;
        if (!state.hud().hasPlayer(chat.sender())) {
            shown = false;
            chatLine(new ChatLine(ChatKind.PLAYER, chat.sender(), chatType, chat.msgTypeName(), chat.msgTypeTarget(),
                    CHAT_VALIDATION_ERROR, PLAIN.serialize(CHAT_VALIDATION_ERROR)), null);
        } else {
            var message = switch (chat.filterMask().type()) {
                case PASS_THROUGH -> chat.unsignedContent() != null ? chat.unsignedContent() : Component.text(body.content());
                case FULLY_FILTERED -> null;
                case PARTIALLY_FILTERED -> partiallyFiltered(chat.filterMask().mask(), body.content());
            };
            shown = message != null;
            if (shown) {
                chatLine(new ChatLine(ChatKind.PLAYER, chat.sender(), chatType, chat.msgTypeName(), chat.msgTypeTarget(),
                        message, PLAIN.serialize(message)), chat.signature());
            }
        }
        var signature = chat.signature();
        if (signature != null) {
            var tracker = state.chat().lastSeen();
            tracker.add(signature, shown);
            if (tracker.offset() > LastSeenTracker.ACKNOWLEDGE_THRESHOLD) {
                outbox.send(new ClientChatAckPacket(tracker.takeOffset()));
            }
        }
    }

    /**
     * 原版 {@code FilterMask.applyWithFormatting} 中部分过滤的分支：从第 0 个字符开始，按过滤标记交替取出被过滤与没有被过滤的连续字符，
     * 被过滤的字符换成同样数量的 {@code #}，以深灰色显示并带有 {@code chat.filtered} 的悬停文本，依次作为空文本的子组件。
     * 过滤标记超出消息长度时，原版在之后取子字符串时抛出异常，这里相同
     */
    private static Component partiallyFiltered(BitSet mask, String text) {
        var result = Component.text();
        int previous = 0;
        var filtered = mask.get(0);
        while (true) {
            int next = filtered ? mask.nextClearBit(previous) : mask.nextSetBit(previous);
            next = next < 0 ? text.length() : next;
            if (next == previous) {
                return result.build();
            }
            if (filtered) {
                result.append(Component.text("#".repeat(next - previous), FILTERED_STYLE));
            } else {
                result.append(Component.text(text.substring(previous, next)));
            }
            filtered = !filtered;
            previous = next;
        }
    }

    /**
     * Player Chat Message 与 Disguised Chat Message 的聊天类型。wiki 中这个字段为「ID or Chat Type」：
     * 0 表示数据包随后给出聊天类型的定义，其余为 {@code minecraft:chat_type} 注册表中的编号加一
     *
     * @throws IllegalStateException 编号不在注册表中（原版解码时出错，客户端断开连接），或者为 0（Minestom 的限制）
     */
    private RegistryKey<ChatType> chatType(int id) {
        // FIXME(Minestom M18): PlayerChatMessagePacket 与 DisguisedChatPacket 把聊天类型当作普通的 VarInt，
        //  不能表示 0 之后随数据包给出的定义（TCP 连接中这个定义被当作之后的字段读取），本库无法得到这种聊天类型，
        //  见 MINESTOM-BUG-REPORT.md
        if (id == 0) {
            throw new IllegalStateException("chat types defined inline in the packet are not supported");
        }
        var key = state.registries().chatType().getKey(id - 1);
        if (key == null) {
            throw new IllegalStateException("unknown chat type id " + (id - 1));
        }
        return key;
    }

    private void chatLine(ChatLine line, @Nullable MessageSignature signature) {
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
