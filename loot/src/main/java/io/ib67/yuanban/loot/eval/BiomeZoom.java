package io.ib67.yuanban.loot.eval;

import net.minestom.server.instance.Instance;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.biome.Biome;

/**
 * 原版 {@code BiomeManager#getBiome(BlockPos)}：方块位置的生物群系按世界的散列种子在周围八个四分之一区块格点中挑选。
 * 种子取 {@link Instance#getHashedSeed()}，它就是原版 {@code BiomeManager.obfuscateSeed} 的结果；
 * 格点的生物群系由 {@link Instance#getBiome} 读取，纵向超出世界高度时按原版 {@code ChunkAccess#getNoiseBiome} 夹到范围内。
 */
final class BiomeZoom {
    private BiomeZoom() {
    }

    static RegistryKey<Biome> biome(Instance level, int x, int y, int z) {
        int absX = x - 2;
        int absY = y - 2;
        int absZ = z - 2;
        int parentX = absX >> 2;
        int parentY = absY >> 2;
        int parentZ = absZ >> 2;
        double fractX = (absX & 3) / 4.0;
        double fractY = (absY & 3) / 4.0;
        double fractZ = (absZ & 3) / 4.0;
        long seed = level.getHashedSeed();
        int minI = 0;
        double minFiddledDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            boolean xEven = (i & 4) == 0;
            boolean yEven = (i & 2) == 0;
            boolean zEven = (i & 1) == 0;
            int cornerX = xEven ? parentX : parentX + 1;
            int cornerY = yEven ? parentY : parentY + 1;
            int cornerZ = zEven ? parentZ : parentZ + 1;
            double distanceX = xEven ? fractX : fractX - 1.0;
            double distanceY = yEven ? fractY : fractY - 1.0;
            double distanceZ = zEven ? fractZ : fractZ - 1.0;
            double next = fiddledDistance(seed, cornerX, cornerY, cornerZ, distanceX, distanceY, distanceZ);
            if (minFiddledDistance > next) {
                minI = i;
                minFiddledDistance = next;
            }
        }
        int biomeX = (minI & 4) == 0 ? parentX : parentX + 1;
        int biomeY = (minI & 2) == 0 ? parentY : parentY + 1;
        int biomeZ = (minI & 1) == 0 ? parentZ : parentZ + 1;
        return noiseBiome(level, biomeX, biomeY, biomeZ);
    }

    /**
     * 原版 {@code ChunkAccess#getNoiseBiome}：四分之一区块坐标的生物群系，纵向坐标夹在世界高度内。
     * 原版对未加载的区块改用生成器的生物群系来源，Minestom 不提供它。
     */
    private static RegistryKey<Biome> noiseBiome(Instance level, int quartX, int quartY, int quartZ) {
        if (!level.isChunkLoaded(quartX >> 2, quartZ >> 2)) {
            // TODO(minestom): 原版对未加载区块的生物群系使用世界生成器的生物群系来源，Minestom 不提供它
            throw LootEvaluation.unsupported("biome of the unloaded chunk " + (quartX >> 2) + ", " + (quartZ >> 2));
        }
        var dimension = level.getCachedDimensionType();
        int minQuart = dimension.minY() >> 2;
        int maxQuart = minQuart + (dimension.height() >> 2) - 1;
        int clampedY = Mth.clamp(quartY, minQuart, maxQuart);
        return level.getBiome(quartX << 2, clampedY << 2, quartZ << 2);
    }

    private static double fiddledDistance(long seed, int xRandom, int yRandom, int zRandom,
                                          double distanceX, double distanceY, double distanceZ) {
        long rval = next(seed, xRandom);
        rval = next(rval, yRandom);
        rval = next(rval, zRandom);
        rval = next(rval, xRandom);
        rval = next(rval, yRandom);
        rval = next(rval, zRandom);
        double fiddleX = fiddle(rval);
        rval = next(rval, seed);
        double fiddleY = fiddle(rval);
        rval = next(rval, seed);
        double fiddleZ = fiddle(rval);
        return square(distanceZ + fiddleZ) + square(distanceY + fiddleY) + square(distanceX + fiddleX);
    }

    /**
     * 原版 {@code LinearCongruentialGenerator.next}。
     */
    private static long next(long rval, long c) {
        rval *= rval * 6364136223846793005L + 1442695040888963407L;
        return rval + c;
    }

    private static double fiddle(long rval) {
        double uniform = Math.floorMod(rval >> 24, 1024) / 1024.0;
        return (uniform - 0.5) * 0.9;
    }

    private static double square(double value) {
        return value * value;
    }
}
