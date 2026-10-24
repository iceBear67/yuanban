package io.ib67.yuanban.story.debug

import org.junit.platform.engine.CancellationToken
import org.junit.platform.engine.DiscoverySelector
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.TagFilter
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherExecutionRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import java.util.concurrent.atomic.AtomicBoolean

/** 在调试服务端进程中发现并运行故事 */
internal class StoryRunner(private val output: (String) -> Unit = ::println) {
    private val running = AtomicBoolean()
    @Volatile private var cancellation: CancellationToken? = null
    private val discoveryLock = Any()

    /** 发现结果的缓存：按键盘每一次补全都重新发现一次会在 tick 线程上做一次完整的 JUnit 发现，卡住 tick */
    @Volatile private var discovered: List<String>? = null

    /**
     * 已发现的故事类与方法（`类名#方法名`），按名称排序，供 /story run 的补全使用。
     * 使用缓存，第一次调用时发现一次并保存；refresh 为 true 时（/story list）强制重新发现
     */
    fun list(prefix: String? = null, refresh: Boolean = false): List<String> {
        if (refresh) {
            synchronized(discoveryLock) { discovered = null }
        }
        val requestedPrefix = prefix ?: ""
        return discoverAll().filter { it.startsWith(requestedPrefix) }
    }

    private fun discoverAll(): List<String> {
        discovered?.let { return it }
        synchronized(discoveryLock) {
            discovered?.let { return it }
            val plan = LauncherFactory.create().discover(request())
            val all = plan.roots.flatMap { root -> plan.getDescendants(root) }.mapNotNull { identifier ->
                when (val source = identifier.source.orElse(null)) {
                    is MethodSource -> "${source.className}#${source.methodName}"
                    is ClassSource -> source.className
                    else -> null
                }
            }.distinct().sorted()
            discovered = all
            return all
        }
    }

    /** @return 这次选中的全部故事是否都通过了（没有任何失败） */
    fun run(vararg selections: String): Boolean {
        check(running.compareAndSet(false, true)) { "a story is already running" }
        try {
            val request = request(*selections)
            val summary = SummaryGeneratingListener()
            LauncherFactory.openSession().use { session ->
                val token = CancellationToken.create()
                cancellation = token
                session.launcher.execute(
                    LauncherExecutionRequestBuilder.request(request)
                        .listeners(listener, summary)
                        .cancellationToken(token)
                        .build(),
                )
            }
            return summary.summary.totalFailureCount == 0L
        } finally {
            cancellation = null
            running.set(false)
        }
    }

    fun stop() {
        cancellation?.cancel()
    }

    private fun request(vararg selections: String): LauncherDiscoveryRequest {
        val nonTagSelections = selections.filter { !it.startsWith("tag:") }
        // 只给 tag: 选择器时，没有其它选择器可以发现任何测试：选择整个 classpath，交给下面的 TagFilter 过滤
        val selectors = if (nonTagSelections.isEmpty()) {
            listOf(DiscoverySelectors.selectPackage(""))
        } else {
            nonTagSelections.map(::selection)
        }
        val builder = LauncherDiscoveryRequestBuilder.request().selectors(*selectors.toTypedArray())
            // test 资源中的 junit-platform.properties 把默认超时设置为 2 分钟，调试服务端的故事可能暂停更久，这里禁用它
            .configurationParameter("junit.jupiter.execution.timeout.mode", "disabled")
        val tags = selections.filter { it.startsWith("tag:") }.map { it.removePrefix("tag:") }
        // 一次 includeTags 调用传入全部 tag：满足任意一个即可；分开调用会要求同时满足全部 tag
        if (tags.isNotEmpty()) {
            builder.filters(TagFilter.includeTags(tags))
        }
        return builder.build()
    }

    private fun selection(value: String): DiscoverySelector = when {
        value.startsWith("package:") -> DiscoverySelectors.selectPackage(value.removePrefix("package:"))
        '#' in value -> DiscoverySelectors.selectMethod(value.substringBefore('#'), value.substringAfter('#'))
        value.isEmpty() -> DiscoverySelectors.selectPackage("")
        else -> DiscoverySelectors.selectClass(value)
    }

    private val listener = object : TestExecutionListener {
        override fun executionStarted(testIdentifier: org.junit.platform.launcher.TestIdentifier) {
            if (testIdentifier.isTest) output("story started: ${testIdentifier.displayName}")
        }

        override fun executionFinished(testIdentifier: org.junit.platform.launcher.TestIdentifier, result: org.junit.platform.engine.TestExecutionResult) {
            if (testIdentifier.isTest) {
                val failure = result.throwable.orElse(null)
                output("story finished: ${testIdentifier.displayName}: ${result.status}" +
                    (failure?.let { ", failure: $it" } ?: ""))
            }
        }
    }
}
