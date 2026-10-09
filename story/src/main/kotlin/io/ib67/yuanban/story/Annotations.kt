package io.ib67.yuanban.story

import net.minestom.server.ServerProcess
import org.junit.jupiter.api.Tag
import kotlin.reflect.KClass

/** 这个类在单独的 JVM 中运行，JVM 中的服务端由 setup 准备。只能标注在顶层类上 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("isolated")
annotation class IsolatedJvm(val setup: KClass<out ServerSetup<*>> = ModuleSetup::class)

/** IsolatedJvm.setup 的默认值：使用模块的配置参数 yuanban.story.setup。只作为标记，story 模块不会调用它 */
object ModuleSetup : ServerSetup<Unit> {
    override fun configure(process: ServerProcess, defaults: StoryDefaults): Unit =
        throw IllegalStateException("ModuleSetup 只是 @IsolatedJvm 的标记")
}
