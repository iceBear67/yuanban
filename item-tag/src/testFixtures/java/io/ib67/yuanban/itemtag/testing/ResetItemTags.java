package io.ib67.yuanban.itemtag.testing;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.server.registry.BuiltinRegistries;
import net.minestom.server.registry.RegistryData;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 每个测试方法开始之前，把 {@link Material#staticRegistry()} 的标签恢复成 Minestom 自带的标签。
 * <p>
 * 物品注册表保存在静态字段中，同一个 JVM 只有一份。{@code item-tag} 提交时会删除数据包中没有的标签，
 * 之后新建的 {@code ServerProcess} 解码附魔注册表时会找不到它引用的物品标签。{@code @EnvTest} 在解析测试方法的
 * {@code Env} 参数时才新建 {@code ServerProcess}，这一步在全部 {@link BeforeEachCallback} 之后，所以恢复先于进程创建。
 * <p>
 * 只替换标签，保留注册表和其中的物品对象：重新创建整个注册表会得到新的 {@code MaterialImpl}，它们和
 * {@code Material.STONE} 等常量不相等。注册表内部保存标签的 {@code Map} 可以修改，清空之后放入
 * {@code RegistryData.loadTags} 重新生成的标签，和 Minestom 启动时创建的内容相同。
 */
@SuppressWarnings("removal") // Minestom 把 RegistryData 标注为将要删除，它是 Minestom 创建自带标签所用的方法
public final class ResetItemTags implements BeforeEachCallback {
    private static final Field TAGS;
    private static final Method LOAD_TAGS;

    static {
        try {
            TAGS = Material.staticRegistry().getClass().getDeclaredField("tags");
            TAGS.setAccessible(true);
            LOAD_TAGS = RegistryData.class.getDeclaredMethod("loadTags", Key.class);
            LOAD_TAGS.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws ReflectiveOperationException {
        reset();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void reset() throws ReflectiveOperationException {
        var tags = (Map) TAGS.get(Material.staticRegistry());
        var builtIn = (Map) LOAD_TAGS.invoke(null, BuiltinRegistries.ITEM.key());
        tags.clear();
        tags.putAll(builtIn);
    }
}
