package io.ib67.yuanban.bot.registry;

import net.minestom.server.MinecraftServer;
import net.minestom.server.registry.Registries;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端使用的「原版注册表」。
 * <p>
 * 当前 JVM 中有 Minestom 服务端进程时返回这个进程；否则第一次使用时调用 {@link Registries#vanilla()} 构造一份，之后一直复用。
 * <p>
 * JVM 中第一个构造的 {@code VanillaRegistries} 把全局静态的 {@code Material} 原型（物品的默认组件）绑定到自己身上，
 * 之后构造的实例不再改变它们。所以有服务端进程时直接使用服务端进程的注册表；没有服务端进程时本类构造的实例绑定物品原型，
 * 之后在同一个 JVM 中创建的服务端进程使用的物品原型仍然引用这份实例（例如下界合金的 {@code damage_resistant}、
 * 盾牌的 {@code blocks_attacks} 引用其中的伤害类型标签），服务端修改自己的标签不会反映到这些组件上。
 * 这时本类在下一次被调用时抛出异常：同一个 JVM 中需要服务端进程时，要先创建服务端进程，再使用 bot。
 */
public final class VanillaReference {
    private static volatile @Nullable Registries constructed;

    private VanillaReference() {
    }

    /**
     * @throws IllegalStateException 本类已经构造过自己的实例之后，JVM 中又创建了服务端进程
     */
    public static Registries get() {
        var process = MinecraftServer.process();
        var own = constructed;
        if (process != null) {
            if (own != null) {
                // FIXME(Minestom M17): Registries.vanilla() 把全局静态的 Material 原型绑定到 JVM 中第一个构造的实例上，
                //  之后创建的服务端进程的物品原型引用本类构造的实例，Minestom 没有重新绑定的方法，见 MINESTOM-BUG-REPORT.md
                throw new IllegalStateException("a Minestom server process was created after the bot constructed its own vanilla "
                        + "registries: the Material prototypes of the server are bound to the bot's registries; "
                        + "create the server process before using bots in the same JVM");
            }
            return process;
        }
        if (own != null) {
            return own;
        }
        synchronized (VanillaReference.class) {
            if (constructed == null) {
                constructed = Registries.vanilla();
            }
            return constructed;
        }
    }
}
