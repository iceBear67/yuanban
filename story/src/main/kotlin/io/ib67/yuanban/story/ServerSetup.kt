package io.ib67.yuanban.story

import net.minestom.server.ServerProcess
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 准备 JVM 中的服务端：被测模块在这里加载数据、注册监听器，并修改 StoryDefaults */
interface ServerSetup<T> {
    /** 在 MinecraftServer.init() 之前调用，用于调用 ServerProperties 中属性的 set 方法；只有 isolatedTest 任务的 JVM 中属性可以写入 */
    fun beforeInit() {}

    /** 在 MinecraftServer.init() 之后、服务端开始监听与 tick 之前调用一次，在创建服务端的测试线程上执行。返回值由 setup<T>() 提供给故事与词汇 */
    fun configure(process: ServerProcess, defaults: StoryDefaults): T
}

/** 故事的默认设置，只能在 ServerSetup.configure 中修改 */
class StoryDefaults {
    @Volatile
    private var frozen = false

    /** 动作与 expect 的默认时限。等待 bot 的动作与 observer 时由 bot 计算，其余等待按照故事时钟计算 */
    var timeout: Duration = 10.seconds
        set(value) {
            checkMutable()
            field = value
        }

    /** 在故事的 world { } 之前应用于每个世界 */
    var world: WorldSpec.() -> Unit = {}
        set(value) {
            checkMutable()
            field = value
        }

    /** 在 player(name) { } 之前应用于每个玩家 */
    var player: PlayerSetup.() -> Unit = {}
        set(value) {
            checkMutable()
            field = value
        }

    /** summon 创建实体的方式 */
    var entityFactory: (EntityType) -> Entity = { EntityCreature(it) }
        set(value) {
            checkMutable()
            field = value
        }

    internal fun freeze() {
        frozen = true
    }

    private fun checkMutable() {
        check(!frozen) { "StoryDefaults 只能在 ServerSetup.configure 中修改" }
    }
}
