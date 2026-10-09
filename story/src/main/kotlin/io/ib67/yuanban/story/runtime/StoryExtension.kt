package io.ib67.yuanban.story.runtime

import io.ib67.yuanban.story.IsolatedJvm
import io.ib67.yuanban.story.ModuleSetup
import io.ib67.yuanban.story.ServerSetup
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method
import kotlin.reflect.KClass

/** @Story 的 JUnit 扩展：准备共享服务端与故事的运行时，执行测试方法，故事结束时清理 */
class StoryExtension : BeforeEachCallback, AfterEachCallback, InvocationInterceptor {
    override fun beforeEach(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        val isolatedClass = isolatedClassOf(testClass)
        val owner = if (isolatedClass != null) ServerOwner.Isolated(isolatedClass) else ServerOwner.Module
        val (server, created) = SharedServer.obtainForStory(owner) { setupClassOf(isolatedClass, context) }
        if (created) {
            // 全部测试结束时 JUnit 关闭根上下文的 Store 中实现 AutoCloseable 的值
            context.root.getStore(NAMESPACE).put(SERVER_KEY, AutoCloseable { server.process.stop() })
        }
        val env = StoryEnvImpl(server, testClass, context.requiredTestMethod.name, isolatedClass != null)
        context.getStore(NAMESPACE).put(ENV_KEY, env)
        env.start()
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void?>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        val env = envOf(extensionContext)
        // 在故事中通过 EngineTestKit 运行另一个故事时，测试方法在同一个线程上嵌套执行
        val outer = current.get()
        current.set(env)
        try {
            invocation.proceed()
        } finally {
            if (outer == null) current.remove() else current.set(outer)
        }
        check(env.wasStoryCalled) { "@Story 方法没有调用 story { }" }
    }

    override fun afterEach(context: ExtensionContext) {
        val env = context.getStore(NAMESPACE).get(ENV_KEY, StoryEnvImpl::class.java) ?: return
        env.finish(context.executionException.orElse(null))
    }

    internal companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(StoryExtension::class.java)
        private const val ENV_KEY = "env"
        private const val SERVER_KEY = "server"

        /** 测试方法执行期间，当前线程上的故事 */
        val current = ThreadLocal<StoryEnvImpl>()

        private fun envOf(context: ExtensionContext): StoryEnvImpl =
            checkNotNull(context.getStore(NAMESPACE).get(ENV_KEY, StoryEnvImpl::class.java)) { "故事的运行时没有创建" }

        /**
         * 在所在的类与外层类上查找 @IsolatedJvm，返回带有它的类。
         * 标注在嵌套类上时抛出 ExtensionConfigurationException：JUnit 运行外层类时同时运行其中的嵌套类，嵌套类无法独占所在的 JVM
         */
        private fun isolatedClassOf(testClass: Class<*>): Class<*>? {
            val annotated = generateSequence(testClass) { it.enclosingClass }
                .firstOrNull { it.isAnnotationPresent(IsolatedJvm::class.java) } ?: return null
            if (annotated.enclosingClass != null) {
                throw ExtensionConfigurationException("@IsolatedJvm 只能标注在顶层类上：${annotated.name} 是嵌套类")
            }
            return annotated
        }

        /** isolated 类的 @IsolatedJvm.setup；这个值为 ModuleSetup，或者不是 isolated 类时，读取配置参数 yuanban.story.setup */
        private fun setupClassOf(isolatedClass: Class<*>?, context: ExtensionContext): KClass<out ServerSetup<*>>? {
            val declared = isolatedClass?.getAnnotation(IsolatedJvm::class.java)?.setup
            if (declared != null && declared != ModuleSetup::class) return declared
            val name = context.getConfigurationParameter(SETUP_PARAMETER).orElse(null) ?: return null
            val type = try {
                Class.forName(name, true, Thread.currentThread().contextClassLoader)
            } catch (e: ClassNotFoundException) {
                throw ExtensionConfigurationException("$SETUP_PARAMETER 给出的类 $name 不存在", e)
            }
            if (!ServerSetup::class.java.isAssignableFrom(type)) {
                throw ExtensionConfigurationException("$SETUP_PARAMETER 给出的类 $name 没有实现 ServerSetup")
            }
            @Suppress("UNCHECKED_CAST")
            return (type as Class<out ServerSetup<*>>).kotlin
        }

        private const val SETUP_PARAMETER = "yuanban.story.setup"
    }
}
