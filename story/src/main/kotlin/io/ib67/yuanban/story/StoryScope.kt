package io.ib67.yuanban.story

import io.ib67.yuanban.story.runtime.StoryEnv
import io.ib67.yuanban.story.runtime.StoryEnvImpl
import io.kotest.matchers.Matcher
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.utils.Direction
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration

/** story { } 的接收者：场景、角色、结论、时间与结构 */
class StoryScope internal constructor(private val impl: StoryEnvImpl) : Expectations {
    /** 运行时，供词汇使用 */
    val env: StoryEnv get() = impl
    val defaults: StoryDefaults get() = impl.defaults

    private val transcript get() = impl.transcript
    private val worldLock = Any()
    private var defaultWorld: WorldActor? = null
    private var defaultSpec: WorldSpec.() -> Unit = {}
    private val worlds = CopyOnWriteArrayList<WorldActor>()
    internal val actors = CopyOnWriteArrayList<PlayerActor>()

    // 世界

    /** 默认世界，第一次使用时按照 StoryDefaults.world 与 world { } 创建 */
    val world: WorldActor
        get() = synchronized(worldLock) {
            defaultWorld ?: createWorld(DEFAULT_WORLD, defaultSpec).also { defaultWorld = it }
        }

    /** world.spawn */
    val spawn: Spot get() = world.spawn

    /** 设置默认世界；默认世界创建之后调用时抛出 IllegalStateException */
    fun world(spec: WorldSpec.() -> Unit) = synchronized(worldLock) {
        check(defaultWorld == null) { "默认世界已经创建，world { } 必须在第一次使用默认世界之前调用" }
        defaultSpec = spec
    }

    /** 另一个世界 */
    fun world(name: String, spec: WorldSpec.() -> Unit = {}): WorldActor {
        require(name != DEFAULT_WORLD && worlds.none { it.name == name }) { "这个故事中已经有名为 $name 的世界" }
        return createWorld(name, spec)
    }

    private fun createWorld(name: String, spec: WorldSpec.() -> Unit): WorldActor = step("创建世界 $name", StepKind.SETUP) {
        val worldSpec = WorldSpec()
        defaults.world(worldSpec)
        worldSpec.spec()
        val (generator, spawnAt) = worldSpec.resolve()
        val instance = impl.createInstance(worldSpec.dimension)
        val chunks = impl.onServer {
            instance.setGenerator(generator)
            worldSpec.time?.let { instance.setTime(it) }
            worldSpec.weather?.let { instance.setWeather(it) }
            worldSpec.configurations.forEach { it(instance) }
            val centerX = spawnAt.first shr 4
            val centerZ = spawnAt.third shr 4
            val radius = worldSpec.preload
            (-radius..radius).flatMap { dx -> (-radius..radius).map { dz -> instance.loadChunk(centerX + dx, centerZ + dz) } }
        }
        impl.waitFor(java.util.concurrent.CompletableFuture.allOf(*chunks.toTypedArray()), defaults.timeout) {
            StepFailure("世界 $name 的区块没有在 ${formatDuration(defaults.timeout)}内加载完毕")
        }
        WorldActor(name, this, instance, spawnAt).also { worlds += it }
    }

    fun place(block: Block, at: Spot): Spot = step("放置 ${block.key().value()}：$at", StepKind.SETUP) {
        impl.onServer { at.world.instance.setBlock(at.x, at.y, at.z, withHandler(block)) }
        syncWorld(at.world)
        at
    }

    fun fill(from: Spot, to: Spot, block: Block) = step("用 ${block.key().value()} 填充 $from 到 $to", StepKind.SETUP) {
        require(from.world === to.world) { "fill 的两端必须在同一个世界中：$from、$to" }
        impl.onServer {
            val placed = withHandler(block)
            for (x in minOf(from.x, to.x)..maxOf(from.x, to.x)) {
                for (y in minOf(from.y, to.y)..maxOf(from.y, to.y)) {
                    for (z in minOf(from.z, to.z)..maxOf(from.z, to.z)) {
                        from.world.instance.setBlock(x, y, z, placed)
                    }
                }
            }
        }
        syncWorld(from.world)
    }

    /** 被测代码通过 BlockManager.registerHandler 注册了这种方块的处理器时，放置的方块带有这个处理器，与玩家放置的方块相同 */
    private fun withHandler(block: Block): Block {
        val handler = impl.process.block().getHandler(block.key().asString())
        return if (handler != null) block.withHandler(handler) else block
    }

    // 角色

    fun player(name: String, setup: PlayerSetup.() -> Unit = {}): PlayerActor {
        require(actors.none { it.name == name }) { "这个故事中已经有名为 $name 的玩家" }
        val playerSetup = PlayerSetup()
        defaults.player(playerSetup)
        playerSetup.setup()
        val at = playerSetup.at ?: world.spawn
        require(playerSetup.facing.horizontal()) { "玩家的朝向必须是水平方向：${playerSetup.facing}" }
        return step("$name ${playerSetup.describe(at)}", StepKind.SETUP) {
            val spawn = at.center.withView(yawOf(playerSetup.facing), 0f)
            val joined = impl.join(name, at.world.instance, spawn, { playerSetup.clientOptions.forEach { option -> option(this) } })
            val actor = PlayerActor(name, this, joined)
            actors += actor
            impl.onServer { configurePlayer(joined.player, playerSetup) }
            impl.barrier(joined)
            actor
        }
    }

    private fun configurePlayer(player: Player, setup: PlayerSetup) {
        player.gameMode = setup.gameMode
        setup.health?.let { player.health = it }
        setup.food?.let { player.food = it }
        if (setup.op) player.permissionLevel = 4
        setup.handItem?.let {
            player.inventory.setItemStack(0, ItemStack.of(it.material, it.count))
            player.setHeldItemSlot(0)
        }
        setup.items.forEach {
            check(player.inventory.addItemStack(ItemStack.of(it.material, it.count))) { "${player.username} 的物品栏放不下 $it" }
        }
        setup.equipment.forEach { (slot, material) -> player.setEquipment(slot, ItemStack.of(material)) }
        setup.effects.forEach(player::addEffect)
    }

    fun summon(type: EntityType, at: Spot, setup: EntitySetup.() -> Unit = {}): EntityActor =
        step("召唤 ${type.key().value()}：$at", StepKind.SETUP) {
            val entitySetup = EntitySetup().apply(setup)
            val entity = defaults.entityFactory(type)
            val added = impl.onServer {
                entitySetup.health?.let {
                    val living = entity as? LivingEntity ?: throw IllegalArgumentException("${type.key().value()} 不是 LivingEntity，不能设置生命值")
                    living.health = it
                }
                entitySetup.customName?.let { entity.set(DataComponents.CUSTOM_NAME, it) }
                entitySetup.configurations.forEach { it(entity) }
                entity.setInstance(at.world.instance, at.center)
            }
            impl.waitFor(added, defaults.timeout) {
                StepFailure("${type.key().value()} 没有在 ${formatDuration(defaults.timeout)}内加入 ${at.world}")
            }
            syncWorld(at.world)
            EntityActor(entity)
        }

    // 结论

    /** 单独一项期望：故事在这里等到它成立，时限为 defaults.timeout。Expectations 的其余函数都转换为这个函数 */
    override infix fun <T> Fact<T>.should(matcher: Matcher<T>) {
        expectItems(listOf(ExpectItem(this, matcher)), ExpectMode.Within(defaults.timeout))
    }

    /** body 中的各项在同一个 tick 的检查中同时成立；时限按照故事时钟计算 */
    fun expect(within: Duration = defaults.timeout, body: ExpectScope.() -> Unit) =
        expectItems(collect(body), ExpectMode.Within(within))

    /** 时限为 within 个 tick 的检查 */
    fun expect(within: Int, body: ExpectScope.() -> Unit) {
        require(within > 0) { "tick 检查的次数必须大于 0：$within" }
        expectItems(collect(body), ExpectMode.WithinChecks(within))
    }

    fun expectThroughout(ticks: Int, body: ExpectScope.() -> Unit) {
        require(ticks > 0) { "tick 数必须大于 0：$ticks" }
        expectItems(collect(body), ExpectMode.Throughout(ticks))
    }

    fun expectThroughout(duration: Duration, body: ExpectScope.() -> Unit) =
        expectItems(collect(body), ExpectMode.ThroughoutDuration(duration))

    private fun collect(body: ExpectScope.() -> Unit): List<ExpectItem<*>> {
        val scope = ExpectScope().apply(body)
        require(scope.items.isNotEmpty()) { "expect { } 中没有期望" }
        return scope.items
    }

    private fun expectItems(items: List<ExpectItem<*>>, mode: ExpectMode) {
        val label = "期望（${mode.label}）"
        step(label, StepKind.EXPECT) { awaitFacts(label, items, mode, transcript.currentParent()) }
    }

    /**
     * 使用 tick 检查等到 items 成立；不成立时抛出 StepFailure。
     * record 不为 null 时，把最后一个 tick 检查中每一项的结果写入它的详情
     */
    internal fun awaitFacts(label: String, items: List<ExpectItem<*>>, mode: ExpectMode, record: StepRecord?) {
        impl.requireNotServerThread()
        val run = ExpectationRun(impl, label, items, mode)
        run.start()
        try {
            val timeout = (mode as? ExpectMode.Within)?.timeout
            val outcome = impl.waitFor(run.outcome, timeout) { StepFailure("${label}没有成立：\n${report(run.last)}") }
            if (outcome is ExpectationRun.Outcome.Failed) throw StepFailure("${label}没有成立：\n${report(outcome.last)}")
        } finally {
            run.close()
            record?.details?.addAll(run.last?.describe() ?: listOf("没有得出任何一个 tick 检查的结果"))
        }
    }

    private fun report(last: TickCheckRecord?): String =
        last?.describe()?.joinToString("\n") ?: "没有得出任何一个 tick 检查的结果"

    // 时间

    /** 等到服务端再结束 ticks 个 tick，适合表示游戏规则中的时间 */
    fun wait(ticks: Int) = step("等待 $ticks tick", StepKind.WAIT) { impl.awaitTicks(ticks) }

    /** 等到故事时钟经过 duration */
    fun wait(duration: Duration) = step("等待 ${formatDuration(duration)}", StepKind.WAIT) { impl.sleep(duration) }

    // 结构

    /** 叙述：以这段文字为标题记录 body 中的步骤 */
    operator fun String.invoke(body: () -> Unit) = step(this, StepKind.NARRATION, body)

    /** 记录一个步骤，词汇中的每个动作都写在 step 中 */
    fun <T> step(description: String, body: () -> T): T = step(description, StepKind.ACTION, body)

    internal fun <T> step(description: String, kind: StepKind, body: () -> T): T {
        val record = transcript.begin(description, kind)
        val failures = mutableListOf<Throwable>()
        var result: T? = null
        try {
            impl.server.monitors.forEach { it.stepStarted(impl, record) }
            result = transcript.withParent(record, body)
            transcript.finish(record, StepResult.PASSED)
        } catch (e: Throwable) {
            transcript.finish(record, StepResult.FAILED)
            failures += e
        }
        try {
            impl.server.monitors.forEach { it.stepFinished(impl, record) }
        } catch (e: Throwable) {
            failures += e
        }
        if (failures.isNotEmpty()) {
            val first = failures.first()
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** 每个分支在一个虚拟线程上执行，全部结束之后返回；抛出第一个失败，其余通过 addSuppressed 附加到它上面 */
    fun simultaneously(vararg branches: () -> Unit) {
        val parent = transcript.currentParent()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val threads = branches.mapIndexed { index, branch ->
            Thread.ofVirtual().name("story-branch-${index + 1}").start {
                transcript.withParent(parent) {
                    try {
                        step("分支 ${index + 1}", StepKind.BRANCH, branch)
                    } catch (e: Throwable) {
                        failures += e
                    }
                }
            }
        }
        threads.forEach(Thread::join)
        val first = failures.poll() ?: return
        failures.forEach(first::addSuppressed)
        throw first
    }

    /** 捕获其中抛出的 StepFailure，故事记录中失败的步骤标为「预期之内的失败」 */
    fun attempt(body: () -> Unit): Attempt {
        val parent = transcript.currentParent()
        val from = transcript.childCount(parent)
        return try {
            body()
            Attempt(null)
        } catch (e: StepFailure) {
            transcript.markExpectedFailures(parent, from)
            Attempt(e)
        }
    }

    // 服务端

    /** 在下一个 tick 开始时于服务端线程上执行代码，返回它的结果，然后对故事中的每个玩家调用同步点 */
    fun <T> server(description: String = "在服务端执行代码", body: () -> T): T = step(description, StepKind.SERVER) {
        val result = impl.onServer(body)
        syncPlayers { true }
        result
    }

    inline fun <reified T : Any> setup(): T = env.setup(T::class)

    // 内部

    /** 对这个世界中的每个玩家调用一次同步点 */
    internal fun syncWorld(world: WorldActor) = syncPlayers { it.instance === world.instance }

    private fun syncPlayers(filter: (Player) -> Boolean) {
        val targets = impl.onServer { actors.filter { it.player.isOnline && filter(it.player) } }
        targets.forEach { impl.barrier(it.joined) }
    }

    /** 服务端 Player 所在的世界；在服务端线程上调用 */
    internal fun worldOf(player: Player): WorldActor {
        val instance = checkNotNull(player.instance) { "${player.username} 不在任何世界中" }
        return checkNotNull(worlds.firstOrNull { it.instance === instance }) { "${player.username} 所在的 $instance 不是这个故事创建的世界" }
    }

    internal fun worldNameOf(instance: Instance): String = worlds.firstOrNull { it.instance === instance }?.name ?: instance.toString()

    /** 这个故事中位于这个世界的全部玩家（服务端 Player 所在的 Instance 为这个世界）；在服务端线程上调用 */
    internal fun playersIn(world: WorldActor): List<PlayerActor> = actors.filter { it.player.instance === world.instance }

    private companion object {
        const val DEFAULT_WORLD = "world"

        /** 原版的偏航角：南 0，西 90，北 180，东 -90 */
        fun yawOf(direction: Direction): Float = when (direction) {
            Direction.SOUTH -> 0f
            Direction.WEST -> 90f
            Direction.NORTH -> 180f
            Direction.EAST -> -90f
            else -> throw IllegalArgumentException("没有水平方向的偏航角：$direction")
        }
    }
}
