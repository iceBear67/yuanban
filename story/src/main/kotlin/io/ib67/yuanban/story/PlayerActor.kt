package io.ib67.yuanban.story

import io.ib67.yuanban.bot.Bot
import io.ib67.yuanban.bot.BotOptions
import io.ib67.yuanban.bot.view.EntitySnapshot
import io.ib67.yuanban.bot.view.StateReader
import io.ib67.yuanban.bot.view.WindowSnapshot
import io.ib67.yuanban.story.runtime.JoinedPlayer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.utils.Direction
import java.util.UUID
import kotlin.math.floor
import kotlin.time.Duration

/** 玩家的设置：StoryDefaults.player 与故事的 player(name) { } 依次应用到同一个对象 */
class PlayerSetup internal constructor() {
    /** 出生的位置，默认为默认世界的出生点 */
    var at: Spot? = null
    var facing: Direction = Direction.NORTH
    var gameMode: GameMode = GameMode.SURVIVAL
    var health: Float? = null
    var food: Int? = null

    /** 权限等级 4 */
    var op: Boolean = false

    internal var handItem: ItemCount? = null
    internal val items = mutableListOf<ItemCount>()
    internal val equipment = mutableListOf<Pair<EquipmentSlot, Material>>()
    internal val effects = mutableListOf<Potion>()
    internal val clientOptions = mutableListOf<BotOptions.BotOptionsBuilder.() -> Unit>()

    /** 放入主手（快捷栏第 0 格，并选中这一格） */
    fun hand(item: ItemCount) {
        handItem = item
    }

    fun hand(material: Material) = hand(material * 1)

    /** 依次加入物品栏 */
    fun inventory(vararg items: ItemCount) {
        this.items += items
    }

    fun equip(slot: EquipmentSlot, material: Material) {
        equipment += slot to material
    }

    fun effect(effect: PotionEffect, amplifier: Int = 0, duration: Duration = Duration.INFINITE) {
        val ticks = if (duration == Duration.INFINITE) Potion.INFINITE_DURATION else (duration.inWholeMilliseconds / 50).toInt()
        effects += Potion(effect, amplifier, ticks)
    }

    /** 修改 bot 的选项，例如语言与视距 */
    fun client(options: BotOptions.BotOptionsBuilder.() -> Unit) {
        clientOptions += options
    }

    /** 故事记录中加入步骤的描述 */
    internal fun describe(spot: Spot): String = buildString {
        append("加入 ").append(spot).append('，').append(gameModeText(gameMode))
        val all = listOfNotNull(handItem) + items
        if (all.isNotEmpty()) append("，物品：").append(all.joinToString("，"))
    }
}

/** 故事中的一个玩家，由 bot 扮演 */
class PlayerActor internal constructor(
    /** 故事中的名称，例如 alice */
    val name: String,
    val story: StoryScope,
    /** 当前的 bot 与服务端 Player；rejoins 之后为新的对象 */
    @Volatile internal var joined: JoinedPlayer,
) {
    /** 实际的用户名，例如 alice_1f */
    val username: String get() = joined.username
    val bot: Bot get() = joined.bot

    /** 服务端对象，只在 server { } 中读写；Transfer 或者 rejoins 之后为新的对象 */
    val player: Player get() = joined.player

    internal val uuid: UUID get() = joined.player.uuid

    /** alice 的客户端看到的内容 */
    val client: ClientView = ClientView(this)

    // 位置：调用时在服务端读取一次

    val feet: Spot
        get() = story.env.onServer {
            val position = player.position
            story.worldOf(player).at(position.blockX(), position.blockY(), position.blockZ())
        }

    val below: Spot get() = feet.down()

    /** 面朝的水平方向上第 blocks 格 */
    fun front(blocks: Int = 1): Spot {
        val (spot, yaw) = story.env.onServer {
            val position = player.position
            story.worldOf(player).at(position.blockX(), position.blockY(), position.blockZ()) to position.yaw()
        }
        val direction = Direction.HORIZONTAL[Math.floorMod(floor((yaw + 45) / 90.0).toInt(), 4)]
        return Spot(spot.world, spot.x + direction.normalX() * blocks, spot.y, spot.z + direction.normalZ() * blocks)
    }

    // 服务端与 alice 自己的客户端都要满足的事实

    val position: SyncedFact<Pos> get() = synced("$name 的位置", { player.position }, { it.player().position() })
    val health: SyncedFact<Float> get() = synced("$name 的生命值", { player.health }, { it.player().health() })
    val food: SyncedFact<Int> get() = synced("$name 的饥饿值", { player.food }, { it.player().food() })
    val gameMode: SyncedFact<GameMode> get() = synced("$name 的游戏模式", { player.gameMode }, ::clientGameMode)
    val inventory: SyncedFact<Inventory> get() = synced("$name 的物品栏", ::serverInventory, ::clientInventory)
    val hand: SyncedFact<ItemCount?> get() = synced("$name 的主手", { ItemCount.of(player.itemInMainHand) }, ::clientHand)
    val alive: SyncedFact<Boolean> get() = synced("$name 是否存活", { !player.isDead }, { !it.player().dead() })

    // 只在服务端的事实

    val online: ServerFact<Boolean> get() = ServerFact("$name 是否在线") { player.isOnline }

    /** client.block(spot) */
    fun sees(spot: Spot): ClientFact<Block> = client.block(spot)

    /** alice 的客户端中存在这个实体 */
    fun sees(entity: EntityActor): ClientFact<Boolean> =
        ClientFact("$name 的客户端中是否存在 $entity", this) { it.entity(entity.entity.entityId) != null }

    override fun toString(): String = name

    private fun <T> synced(description: String, server: () -> T, client: (StateReader) -> T): SyncedFact<T> =
        SyncedFact(description, ServerFact(description, server)) { listOf(ClientFact(description, this, client)) }

    private fun serverInventory(): Inventory = Inventory(player.inventory.itemStacks.map { ItemCount.of(it) })

    /** 连接结束的原因与 bot 的异常 */
    internal fun disconnectText(): String {
        val reason = bot.disconnectReason()?.let(::plain)
        val failure = bot.failure()
        return listOfNotNull(reason, failure?.let { "bot 的异常：$it" }).joinToString("；").ifEmpty { "连接仍未结束" }
    }

    /** 失败报告中这个玩家在服务端的状态；在服务端线程上调用 */
    internal fun serverState(): String {
        val player = player
        val world = player.instance?.let { story.worldNameOf(it) } ?: "不在任何世界中"
        val position = player.position
        return "$world ${describeValue(position)}，生命 ${player.health}，饥饿 ${player.food}，${gameModeText(player.gameMode)}，" +
            "${handText(ItemCount.of(player.itemInMainHand))}，${if (player.isOnline) "在线" else "离线"}"
    }

    /** 失败报告中这个玩家的客户端的状态；在 bot 的调度线程上调用 */
    internal fun clientState(state: StateReader): List<String> = buildList {
        val world = state.world()
        if (world == null) {
            add("不在任何世界中")
        } else {
            val local = state.player()
            add(
                "${world.dimension().value()} ${describeValue(local.position())}，生命 ${local.health()}，" +
                    "${gameModeText(clientGameMode(state))}，${handText(clientHand(state))}",
            )
        }
        state.openWindow()?.let { window ->
            val slots = window.slots().mapIndexedNotNull { index, stack -> ItemCount.of(stack)?.let { "槽位 $index $it" } }
            add("打开的窗口 ${window.type()?.name?.lowercase() ?: window.windowId()}：${slots.joinToString("，").ifEmpty { "空" }}")
        }
        val chat = state.chat().takeLast(5).map { it.plainText() }
        add("最近的聊天：${if (chat.isEmpty()) "无" else chat.joinToString("；")}")
    }
}

/** 客户端的游戏模式按照编号转换为 Minestom 的类型 */
internal fun clientGameMode(state: StateReader): GameMode = GameMode.entries[state.player().gameMode().ordinal]

internal fun clientInventory(state: StateReader): Inventory = Inventory(state.inventory().slots().map { ItemCount.of(it) })

/** 窗口 0 中快捷栏的槽位为 36 到 44 */
internal fun clientHand(state: StateReader): ItemCount? = ItemCount.of(state.inventory().slot(36 + state.player().heldSlot()))

internal fun plain(component: Component): String = PlainTextComponentSerializer.plainText().serialize(component)

internal fun gameModeText(gameMode: GameMode): String = when (gameMode) {
    GameMode.SURVIVAL -> "生存模式"
    GameMode.CREATIVE -> "创造模式"
    GameMode.ADVENTURE -> "冒险模式"
    GameMode.SPECTATOR -> "旁观模式"
}

private fun handText(item: ItemCount?): String = if (item == null) "主手为空" else "主手为 $item"

/** 一个玩家的客户端看到的内容 */
class ClientView internal constructor(private val actor: PlayerActor) {
    private val name get() = actor.name

    fun block(spot: Spot): ClientFact<Block> = ClientFact("$name 看到的 $spot 处的方块", actor) { it.block(spot.x, spot.y, spot.z) }

    val position: ClientFact<Pos> get() = ClientFact("$name 的客户端中的位置", actor) { it.player().position() }
    val health: ClientFact<Float> get() = ClientFact("$name 的客户端中的生命值", actor) { it.player().health() }
    val food: ClientFact<Int> get() = ClientFact("$name 的客户端中的饥饿值", actor) { it.player().food() }
    val gameMode: ClientFact<GameMode> get() = ClientFact("$name 的客户端中的游戏模式", actor, ::clientGameMode)
    val inventory: ClientFact<Inventory> get() = ClientFact("$name 的客户端中的物品栏", actor, ::clientInventory)
    val hand: ClientFact<ItemCount?> get() = ClientFact("$name 的客户端中的主手", actor, ::clientHand)
    val window: ClientFact<WindowSnapshot?> get() = ClientFact("$name 打开的窗口", actor) { it.openWindow() }
    val actionBar: ClientFact<String?> get() = ClientFact("$name 的动作栏", actor) { state -> state.hud().actionBar()?.let(::plain) }
    val title: ClientFact<String?> get() = ClientFact("$name 的标题", actor) { state -> state.hud().title()?.let(::plain) }

    fun entity(entity: EntityActor): ClientFact<EntitySnapshot?> =
        ClientFact("$name 的客户端中的 $entity", actor) { it.entity(entity.entity.entityId) }

    /** 读取 bot 模型中的任意内容 */
    fun <T> fact(description: String, read: (StateReader) -> T): ClientFact<T> = ClientFact(description, actor, read)
}
