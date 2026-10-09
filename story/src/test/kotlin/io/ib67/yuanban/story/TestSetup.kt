package io.ib67.yuanban.story

import net.minestom.server.ServerProcess
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType

/** story 模块自身的测试使用的服务端：注册一个测试维度类型 */
object TestSetup : ServerSetup<TestWorlds> {
    override fun configure(process: ServerProcess, defaults: StoryDefaults): TestWorlds {
        val dimension = process.dimensionType().register(
            "story:test_dimension",
            DimensionType.builder().minY(0).height(128).logicalHeight(128).build(),
        )
        return TestWorlds(dimension)
    }
}

/** TestSetup 的返回值 */
data class TestWorlds(val dimension: RegistryKey<DimensionType>)
