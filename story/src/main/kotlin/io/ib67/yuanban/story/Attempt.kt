package io.ib67.yuanban.story

/** attempt { } 的结果：其中抛出的 StepFailure，没有失败时为 null */
class Attempt(val failure: StepFailure?) {
    val succeeded: Boolean get() = failure == null
    val failed: Boolean get() = failure != null

    override fun toString(): String = if (failure == null) "成功" else "失败：${failure.message}"
}
