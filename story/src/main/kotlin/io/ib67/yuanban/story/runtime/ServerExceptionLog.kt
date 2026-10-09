package io.ib67.yuanban.story.runtime

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** 服务端异常处理器收到的异常，以及异常发生时运行中的全部故事 */
internal class ServerExceptionLog(private val running: Collection<StoryEnvImpl>) {
    class Entry(val seq: Int, val error: Throwable, val thread: String, val stories: List<StoryEnvImpl>) {
        fun describe(): String =
            "#$seq（线程 $thread）：$error；异常发生时运行中的故事：${stories.joinToString("，") { it.name }.ifEmpty { "无" }}"
    }

    private val entries = CopyOnWriteArrayList<Entry>()
    private val counter = AtomicInteger()

    fun record(error: Throwable) {
        val entry = Entry(counter.incrementAndGet(), error, Thread.currentThread().name, running.toList())
        entries += entry
        synchronized(System.err) {
            System.err.println("服务端异常 ${entry.describe()}")
            error.printStackTrace()
        }
    }

    /** 运行故事名单包含 story 的记录 */
    fun involving(story: StoryEnvImpl): List<Entry> = entries.filter { story in it.stories }
}
