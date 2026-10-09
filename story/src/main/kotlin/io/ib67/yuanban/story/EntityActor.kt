package io.ib67.yuanban.story

import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity

/** summon 的设置 */
class EntitySetup internal constructor() {
    var health: Float? = null
    var customName: Component? = null
    internal val configurations = mutableListOf<Entity.() -> Unit>()

    /** 加入世界之前在服务端线程上执行 */
    fun configure(body: Entity.() -> Unit) {
        configurations += body
    }
}

/** 故事中的一个实体 */
class EntityActor internal constructor(
    /** 服务端对象，只在 server { } 中读写 */
    val entity: Entity,
) {
    val type: EntityType get() = entity.entityType
    private val name = "${type.key().value()}（编号 ${entity.entityId}）"

    val position: ServerFact<Pos> get() = ServerFact("$name 的位置") { entity.position }

    /** 只用于 LivingEntity */
    val health: ServerFact<Float>
        get() {
            val living = entity as? LivingEntity ?: throw IllegalStateException("$name 不是 LivingEntity，没有生命值")
            return ServerFact("$name 的生命值") { living.health }
        }

    val alive: ServerFact<Boolean>
        get() = ServerFact("$name 是否存活") { !entity.isRemoved && (entity as? LivingEntity)?.isDead != true }

    val onFire: ServerFact<Boolean> get() = ServerFact("$name 是否着火") { entity.isOnFire }

    override fun toString(): String = name
}
