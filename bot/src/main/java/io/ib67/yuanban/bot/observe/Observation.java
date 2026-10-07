package io.ib67.yuanban.bot.observe;

/**
 * 一条观察记录：服务端发来的数据包、由此引起的模型变化，或 bot 自身的事件。全部子类型为不可变 record。
 */
public sealed interface Observation permits
        PacketReceived, PacketSent,
        WorldEntered, ChunkLoaded, ChunkUnloaded, BlockChanged, BlockEntityChanged,
        EntitySpawned, EntityRemoved, EntityMoved, EntityMetadataChanged, EntityEquipmentChanged,
        EntityDamaged, EntityStatusReceived, ItemPickedUp,
        PlayerLoaded, PlayerMoved, PlayerTeleported, HealthChanged, PlayerDied, GameModeChanged,
        EffectChanged, ExperienceChanged,
        SlotChanged, CursorChanged, WindowOpened, WindowClosed, HeldSlotChanged,
        ChatReceived, ActionBarShown, TitleShown, BossBarChanged, ScoreChanged,
        ActionCompleted, MovementSuspensionChanged, BotTicked, StateUpdated {
}
