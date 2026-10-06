package io.ib67.yuanban.datapack.pack;

/**
 * 数据包的类别，对应原版 {@code PackSource}。
 *
 * @param name       类别名称
 * @param autoEnable 第一次被发现的时候是否自动加入启用列表
 */
public record PackOrigin(String name, boolean autoEnable) {
    /**
     * 原版内置数据包，例如 {@code vanilla}。
     */
    public static final PackOrigin BUILT_IN = new PackOrigin("built_in", true);
    /**
     * 原版自带的特性数据包。世界启用的特性缺少提供它的数据包的时候，从这个类别中挑选数据包强制启用；
     * 声明了特性的这类数据包不能被禁用。
     */
    public static final PackOrigin FEATURE = new PackOrigin("feature", false);
    /**
     * 世界 {@code datapacks/} 目录下的数据包。
     */
    public static final PackOrigin WORLD = new PackOrigin("world", true);
}
