package io.ib67.yuanban.loot.eval;

import net.kyori.adventure.key.Key;

import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * 战利品表 {@code random_sequence} 的稳定随机数源，按原版 26.2
 * {@code net.minecraft.world.RandomSequence} 的规则派生：
 * {@code seedForKey(id)} 是对序列 ID（{@code id.toString()}）做 UTF-8 编码后求 MD5，散列的
 * 前 8 字节大端为低 64 位、后 8 字节大端为高 64 位（原版
 * {@code RandomSupport.seedFromHashOf(String)}），再用
 * {@code RandomSupport.upgradeSeedTo128bitUnmixed(worldSeed)}（{@code lo = seed ^ 7640891576956012809L}、
 * {@code hi = lo + (-7046029254386353131L)}）与之 xor，之后走 {@code Seed128bit.mixed()} 对两半各做一次
 * {@link #mixStafford13(long)}，装入 {@code Xoroshiro128++}（原版
 * {@code XoroshiroRandomSource(Xoroshiro128PlusPlus)}）。
 * <p>
 * 算法本体对应原版 {@code Xoroshiro128PlusPlus#nextLong}；原版
 * {@code Xoroshiro128PlusPlus} 构造器在 {@code (seedLo|seedHi) == 0} 时改用
 * {@code lo=-7046029254386353131L, hi=7640891576956012809L} 的兜底状态，这里保留同样规则。
 */
final class SequenceRandom extends Random {
    private long stateLo;
    private long stateHi;
    /**
     * {@link Random} 的构造器会调用 {@link #setSeed(long)}，那时本类的构造器还没有执行，这个字段为 {@code false}。
     */
    private final boolean constructed;

    SequenceRandom(long worldSeed, Key sequenceId) {
        var digest = md5(sequenceId.asString().getBytes(StandardCharsets.UTF_8));
        var buffer = ByteBuffer.wrap(digest).order(ByteOrder.BIG_ENDIAN);
        long keyLo = buffer.getLong(0);
        long keyHi = buffer.getLong(8);
        long mixLo = worldSeed ^ 7640891576956012809L;
        long mixHi = mixLo + (-7046029254386353131L);
        long stateLo = mixStafford13(mixLo ^ keyLo);
        long stateHi = mixStafford13(mixHi ^ keyHi);
        if ((stateLo | stateHi) == 0L) {
            stateLo = -7046029254386353131L;
            stateHi = 7640891576956012809L;
        }
        this.stateLo = stateLo;
        this.stateHi = stateHi;
        this.constructed = true;
    }

    /**
     * 原版 {@code RandomSupport.mixStafford13(long)} 的 {@code Stafford 13} 混合。
     */
    private static long mixStafford13(long seed) {
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        return seed ^ (seed >>> 31);
    }

    @Override
    protected int next(int bits) {
        // 原版 RandomSource.next(int) 取 nextLong() 的高 bits 位
        return (int) (nextLongRaw() >>> (64 - bits));
    }

    @Override
    public long nextLong() {
        return nextLongRaw();
    }

    /**
     * 原版 {@code Xoroshiro128PlusPlus#nextLong}：
     * {@code result = rotl(s0 + s1, 17) + s0; s1 ^= s0; lo = rotl(s0, 49) ^ s1 ^ (s1 << 21); hi = rotl(s1, 28)}。
     */
    private long nextLongRaw() {
        long s0 = stateLo;
        long s1 = stateHi;
        long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        stateLo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        stateHi = Long.rotateLeft(s1, 28);
        return result;
    }

    @Override
    public void setSeed(long seed) {
        // java.util.Random 的构造器调用这个方法时状态还没有派生，这次调用不改变任何状态；构造完成之后不允许覆盖已派生的状态
        if (constructed) {
            throw new UnsupportedOperationException("SequenceRandom seed is derived at construction time");
        }
    }

    private static byte[] md5(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("MD5").digest(input);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
