package io.ib67.yuanban.story.runtime

/** JVM 中的服务端属于模块，还是属于某个 isolated 类 */
internal sealed interface ServerOwner {
    /** 使用模块的 ServerSetup */
    data object Module : ServerOwner {
        override fun toString(): String = "模块"
    }

    /** 使用这个类的 @IsolatedJvm.setup */
    data class Isolated(val type: Class<*>) : ServerOwner {
        override fun toString(): String = "isolated 类 ${type.name}"
    }
}
