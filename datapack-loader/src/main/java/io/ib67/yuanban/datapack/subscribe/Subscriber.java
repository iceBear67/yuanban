package io.ib67.yuanban.datapack.subscribe;

/**
 * 模块注册到框架的处理对象。
 *
 * @param <R> 准备结果的类型
 */
public interface Subscriber<R> {
    /**
     * 准备阶段：在执行器上运行，读取文件、解析内容，返回不可变的准备结果。
     * 不允许修改游戏状态，任何一个订阅者失败，整次加载都会放弃。
     */
    R prepare(PrepareContext context) throws Exception;

    /**
     * 提交阶段：在调用 {@code start} 或 {@code reload} 的线程上按依赖顺序运行，把准备结果填入游戏。
     * 只做替换引用这类不会失败的操作。
     */
    void apply(R prepared);
}
