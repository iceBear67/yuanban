package io.ib67.yuanban.story

import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.Weather
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.Generator
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType

/** 世界的设置：StoryDefaults.world 与故事的 world { } 依次应用到同一个对象 */
class WorldSpec internal constructor() {
    var dimension: RegistryKey<DimensionType> = DimensionType.OVERWORLD
    internal var terrain: Terrain = Terrain.Unset
    internal var spawn: Triple<Int, Int, Int>? = null
    internal val configurations = mutableListOf<InstanceContainer.() -> Unit>()

    /** 从 y = 0 开始向上依次叠放的方块层 */
    fun layer(block: Block, thickness: Int = 1) {
        require(thickness > 0) { "方块层的厚度必须大于 0：$thickness" }
        val layers = terrain as? Terrain.Layers ?: Terrain.Layers(mutableListOf()).also { terrain = it }
        layers.layers += block to thickness
    }

    /** 使用这个生成器，替换方块层 */
    fun generator(generator: Generator) {
        terrain = Terrain.Custom(generator)
    }

    /** 不生成任何方块 */
    fun empty() {
        terrain = Terrain.Empty
    }

    /** 出生点所在的方块格；没有设置时为 (0, 最上层方块之上, 0)，empty 与 generator 时为 (0, 64, 0) */
    fun spawnAt(x: Int, y: Int, z: Int) {
        spawn = Triple(x, y, z)
    }

    /** Instance.setTime：默认时钟的时间 */
    var time: Long? = null
    var weather: Weather? = null

    /** 创建之后立即加载的区块半径，以出生点所在的区块为中心 */
    var preload: Int = 2

    /** 创建之后在服务端线程上执行 */
    fun configure(body: InstanceContainer.() -> Unit) {
        configurations += body
    }

    /** 方块层的生成器与出生点 */
    internal fun resolve(): Pair<Generator?, Triple<Int, Int, Int>> = when (val terrain = terrain) {
        Terrain.Unset -> layersGenerator(DEFAULT_LAYERS) to (spawn ?: Triple(0, DEFAULT_LAYERS.sumOf { it.second }, 0))
        is Terrain.Layers -> layersGenerator(terrain.layers.toList()) to (spawn ?: Triple(0, terrain.layers.sumOf { it.second }, 0))
        is Terrain.Custom -> terrain.generator to (spawn ?: Triple(0, 64, 0))
        Terrain.Empty -> null to (spawn ?: Triple(0, 64, 0))
    }

    internal sealed interface Terrain {
        data object Unset : Terrain
        class Layers(val layers: MutableList<Pair<Block, Int>>) : Terrain
        class Custom(val generator: Generator) : Terrain
        data object Empty : Terrain
    }

    private companion object {
        /** y = 0 基岩，y 为 1 到 59 石头，60 到 62 泥土，63 草方块 */
        val DEFAULT_LAYERS = listOf(Block.BEDROCK to 1, Block.STONE to 59, Block.DIRT to 3, Block.GRASS_BLOCK to 1)

        fun layersGenerator(layers: List<Pair<Block, Int>>) = Generator { unit ->
            var y = 0
            layers.forEach { (block, thickness) ->
                unit.modifier().fillHeight(y, y + thickness, block)
                y += thickness
            }
        }
    }
}

/** 故事中的一个世界 */
class WorldActor internal constructor(
    val name: String,
    internal val story: StoryScope,
    /** 服务端对象，只在 server { } 中读写 */
    val instance: InstanceContainer,
    private val spawnAt: Triple<Int, Int, Int>,
) {
    val spawn: Spot get() = Spot(this, spawnAt.first, spawnAt.second, spawnAt.third)

    fun at(x: Int, y: Int, z: Int): Spot = Spot(this, x, y, z)

    val time: ServerFact<Long> get() = ServerFact("$name 的时间") { instance.time }

    override fun toString(): String = name
}
