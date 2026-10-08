package io.ib67.yuanban.bot.decode;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.ib67.yuanban.bot.world.SharedSection;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 同一个 JVM 中全部 bot 共用的区段池：以区段的原始字节为键，值以弱引用保存，没有 bot 引用时由垃圾回收释放。
 * 按原始字节去重：MCProtocolLib 的 {@code DataPalette} 虽然定义了 {@code equals}，但它与调色板中条目的顺序有关，
 * 而且查找之前要先解码。
 */
public final class SectionPool {
    public static final SectionPool SHARED = new SectionPool();

    private final Cache<SectionKey, SharedSection> cache = Caffeine.newBuilder().weakValues().build();

    /**
     * @param bytes      区段的原始字节，调用之后不能再修改
     * @param biomeCount 生物群系数量，决定写入生物群系时扩展出的全局调色板的位数
     * @param decode     未命中时解码
     */
    public SharedSection get(byte[] bytes, int biomeCount, Supplier<SharedSection> decode) {
        return cache.get(new SectionKey(bytes, Arrays.hashCode(bytes), biomeCount), _ -> decode.get());
    }

    /**
     * 当前池中的区段数量
     */
    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    private record SectionKey(byte[] bytes, int hash, int biomeCount) {
        @Override
        public boolean equals(Object o) {
            return o instanceof SectionKey(byte[] otherBytes, int otherHash, int otherBiomeCount)
                    && hash == otherHash && biomeCount == otherBiomeCount && Arrays.equals(bytes, otherBytes);
        }

        @Override
        public int hashCode() {
            return 31 * hash + biomeCount;
        }
    }
}
