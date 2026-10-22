package io.ib67.yuanban.story.debug

import org.junit.platform.engine.DiscoverySelector
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.Launcher
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.core.LauncherExecutionRequestBuilder
import org.junit.platform.launcher.TagFilter
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.CancellationToken
import java.util.concurrent.atomic.AtomicBoolean

/** Discovers and runs stories inside the debug server process. */
internal class StoryRunner(private val output: (String) -> Unit = ::println) {
    private val running = AtomicBoolean()
    @Volatile private var launcher: Launcher? = null
    @Volatile private var cancellation: CancellationToken? = null

    fun list(prefix: String? = null): List<String> {
        val requestedPrefix = prefix ?: ""
        val request = request(if (requestedPrefix.isEmpty()) "" else "package:${requestedPrefix.substringBeforeLast('.', "")}")
        val plan = LauncherFactory.create().discover(request)
        return plan.roots.flatMap { root -> plan.getDescendants(root).mapNotNull { identifier ->
            val source = identifier.source.orElse(null) as? ClassSource ?: return@mapNotNull null
            source.className
        } }.filter { it.startsWith(requestedPrefix) }.distinct().sorted()
    }

    fun run(vararg selections: String): Boolean {
        check(running.compareAndSet(false, true)) { "a story is already running" }
        try {
            val request = request(*selections)
            LauncherFactory.openSession().use { session ->
                val current = session.launcher
                val token = CancellationToken.create()
                launcher = current
                cancellation = token
                current.execute(
                    LauncherExecutionRequestBuilder.request(request)
                        .listeners(listener)
                        .cancellationToken(token)
                        .build(),
                )
            }
            return true
        } finally {
            cancellation = null
            launcher = null
            running.set(false)
        }
    }

    fun stop() {
        cancellation?.cancel()
    }

    private fun request(vararg selections: String): LauncherDiscoveryRequest {
        val selectors = selections.filter { !it.startsWith("tag:") }.map { selection(it) }
        val builder = LauncherDiscoveryRequestBuilder.request().selectors(*selectors.toTypedArray())
        selections.filter { it.startsWith("tag:") }.forEach { builder.filters(TagFilter.includeTags(it.removePrefix("tag:"))) }
        return builder.build()
    }

    private fun selection(value: String): DiscoverySelector = when {
        value.startsWith("package:") -> DiscoverySelectors.selectPackage(value.removePrefix("package:"))
        value.startsWith("tag:") -> DiscoverySelectors.selectPackage("")
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
