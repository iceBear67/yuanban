package io.ib67.yuanban.datapack.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.InclusiveRange;
import io.ib67.yuanban.datapack.pack.FormatRange;
import io.ib67.yuanban.datapack.pack.PackFormat;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 原版 {@code net.minecraft.server.packs.metadata.pack.PackFormat} 中的编解码器和格式字段的校验规则，
 * 格式版本使用 {@link PackFormat}，原版的 {@code InclusiveRange<PackFormat>} 使用 {@link FormatRange}。错误文本与原版相同。
 */
@ApiStatus.Internal
public final class PackFormatCodecs {
    /**
     * 原版 {@code PackFormat.lastPreMinorVersion(PackType.SERVER_DATA)}：数据包格式中最后一个没有次版本的主版本。
     */
    public static final int LAST_PRE_MINOR_VERSION = 81;
    /**
     * 原版 {@code PackFormat.BOTTOM_CODEC}：省略次版本时次版本是 0。
     */
    public static final Codec<PackFormat> BOTTOM_CODEC = fullCodec(0);
    /**
     * 原版 {@code PackFormat.TOP_CODEC}：省略次版本时表示任意次版本。
     */
    public static final Codec<PackFormat> TOP_CODEC = fullCodec(PackFormat.ANY_MINOR);

    private PackFormatCodecs() {
    }

    /**
     * 原版 {@code PackFormat.fullCodec}：非负整数，或者长度 1 到 256 的非负整数列表，列表只使用前两个元素。
     */
    private static Codec<PackFormat> fullCodec(int defaultMinor) {
        return ExtraCodecs.compactListCodec(ExtraCodecs.NON_NEGATIVE_INT, ExtraCodecs.NON_NEGATIVE_INT.listOf(1, 256)).xmap(
                list -> list.size() > 1 ? PackFormat.of(list.getFirst(), list.get(1)) : PackFormat.of(list.getFirst(), defaultMinor),
                format -> format.minor() != defaultMinor ? List.of(format.major(), format.minor()) : List.of(format.major()));
    }

    /**
     * 原版 {@code PackFormat.packCodec(PackType.SERVER_DATA)}：{@code pack} 段落中的格式字段。
     */
    public static MapCodec<FormatRange> packCodec() {
        return IntermediaryFormat.PACK_CODEC.flatXmap(
                format -> format.validate(LAST_PRE_MINOR_VERSION, true, false, "Pack", "supported_formats"),
                range -> DataResult.success(IntermediaryFormat.fromRange(range, LAST_PRE_MINOR_VERSION)));
    }

    /**
     * 原版 {@code PackFormat.validateHolderList}：校验 overlay 条目列表。
     * 有条目的最低主版本不超过 {@code lastPreMinorVersion} 的时候，每个条目都必须有旧字段。
     * 三个格式字段都没有的条目跳过，原版在这里记录日志，框架不输出日志。遇到第一个不合法的条目就返回它的错误。
     */
    public static <R, H extends IntermediaryFormatHolder> DataResult<List<R>> validateHolderList(
            List<H> list, int lastPreMinorVersion, BiFunction<H, FormatRange, R> constructor) {
        int minVersion = list.stream()
                .map(IntermediaryFormatHolder::format)
                .mapToInt(IntermediaryFormat::effectiveMinMajorVersion)
                .min()
                .orElse(Integer.MAX_VALUE);
        var result = new ArrayList<R>(list.size());
        for (H entry : list) {
            var format = entry.format();
            if (format.min().isEmpty() && format.max().isEmpty() && format.supported().isEmpty()) {
                continue;
            }
            var entryResult = format.validate(lastPreMinorVersion, false, minVersion <= lastPreMinorVersion,
                    "Overlay \"" + entry + "\"", "formats");
            if (!entryResult.isSuccess()) {
                return DataResult.error(entryResult.error().orElseThrow()::message);
            }
            result.add(constructor.apply(entry, entryResult.getOrThrow()));
        }
        return DataResult.success(List.copyOf(result));
    }

    /**
     * 原版 {@code PackFormat.IntermediaryFormatHolder}：带有格式字段的条目。
     */
    public interface IntermediaryFormatHolder {
        IntermediaryFormat format();
    }

    /**
     * 原版 {@code PackFormat.IntermediaryFormat}：格式字段解码之后、校验之前的内容。
     *
     * @param min       {@code min_format}
     * @param max       {@code max_format}
     * @param format    {@code pack} 段落中是 {@code pack_format}；overlay 条目中是 {@code min_format} 的主版本
     * @param supported {@code pack} 段落中是 {@code supported_formats}；overlay 条目中是 {@code formats}
     */
    public record IntermediaryFormat(Optional<PackFormat> min,
                                     Optional<PackFormat> max,
                                     Optional<Integer> format,
                                     Optional<InclusiveRange<Integer>> supported) {
        /**
         * 原版 {@code IntermediaryFormat.PACK_CODEC}。
         */
        static final MapCodec<IntermediaryFormat> PACK_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BOTTOM_CODEC.optionalFieldOf("min_format").forGetter(IntermediaryFormat::min),
                TOP_CODEC.optionalFieldOf("max_format").forGetter(IntermediaryFormat::max),
                Codec.INT.optionalFieldOf("pack_format").forGetter(IntermediaryFormat::format),
                InclusiveRange.codec(Codec.INT).optionalFieldOf("supported_formats").forGetter(IntermediaryFormat::supported)
        ).apply(i, IntermediaryFormat::new));
        /**
         * 原版 {@code IntermediaryFormat.OVERLAY_CODEC}。
         */
        public static final MapCodec<IntermediaryFormat> OVERLAY_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BOTTOM_CODEC.optionalFieldOf("min_format").forGetter(IntermediaryFormat::min),
                TOP_CODEC.optionalFieldOf("max_format").forGetter(IntermediaryFormat::max),
                InclusiveRange.codec(Codec.INT).optionalFieldOf("formats").forGetter(IntermediaryFormat::supported)
        ).apply(i, (min, max, formats) -> new IntermediaryFormat(min, max, min.map(PackFormat::major), formats)));

        /**
         * 原版 {@code IntermediaryFormat.fromRange}：编码时由格式范围还原各个字段。
         */
        public static IntermediaryFormat fromRange(FormatRange range, int lastPreMinorVersion) {
            var majorRange = new InclusiveRange<>(range.min().major(), range.max().major());
            return new IntermediaryFormat(
                    Optional.of(range.min()),
                    Optional.of(range.max()),
                    majorRange.isValueInRange(lastPreMinorVersion) ? Optional.of(majorRange.minInclusive()) : Optional.empty(),
                    majorRange.isValueInRange(lastPreMinorVersion)
                            ? Optional.of(new InclusiveRange<>(majorRange.minInclusive(), majorRange.maxInclusive()))
                            : Optional.empty());
        }

        /**
         * 原版 {@code IntermediaryFormat.effectiveMinMajorVersion}。
         */
        public int effectiveMinMajorVersion() {
            if (min.isPresent()) {
                return supported.isPresent()
                        ? Math.min(min.get().major(), supported.get().minInclusive())
                        : min.get().major();
            }
            return supported.isPresent() ? supported.get().minInclusive() : Integer.MAX_VALUE;
        }

        /**
         * 原版 {@code IntermediaryFormat.validate}。
         *
         * @param hasPackFormatField 是否检查 {@code pack_format}
         * @param requireOldField    是否必须有旧字段 {@code oldFieldName}
         * @param context            错误文本的开头
         * @param oldFieldName       旧字段的名字
         */
        public DataResult<FormatRange> validate(int lastPreMinorVersion, boolean hasPackFormatField, boolean requireOldField,
                                                String context, String oldFieldName) {
            if (min.isPresent() != max.isPresent()) {
                return DataResult.error(() -> context + " missing field, must declare both min_format and max_format");
            }
            if (requireOldField && supported.isEmpty()) {
                return DataResult.error(() -> context + " missing required field " + oldFieldName
                        + ", must be present in all overlays for any overlays to work across game versions");
            }
            if (min.isPresent()) {
                return validateNewFormat(lastPreMinorVersion, hasPackFormatField, requireOldField, context, oldFieldName);
            }
            if (supported.isPresent()) {
                return validateOldFormat(lastPreMinorVersion, hasPackFormatField, context, oldFieldName);
            }
            if (hasPackFormatField && format.isPresent()) {
                int mainFormat = format.get();
                return mainFormat > lastPreMinorVersion
                        ? DataResult.error(() -> context + " declares support for version newer than " + lastPreMinorVersion
                        + ", but is missing mandatory fields min_format and max_format")
                        : legacyFormat(mainFormat).map(single -> new FormatRange(single, single));
            }
            return DataResult.error(() -> context + " could not be parsed, missing format version information");
        }

        /**
         * 原版 {@code IntermediaryFormat.validateNewFormat}。
         */
        private DataResult<FormatRange> validateNewFormat(int lastPreMinorVersion, boolean hasPackFormatField, boolean requireOldField,
                                                          String context, String oldFieldName) {
            int majorMin = min.orElseThrow().major();
            int majorMax = max.orElseThrow().major();
            if (min.get().compareTo(max.orElseThrow()) > 0) {
                return DataResult.error(() -> context + " min_format (" + min.get() + ") is greater than max_format (" + max.get() + ")");
            }
            if (majorMin > lastPreMinorVersion && !requireOldField) {
                if (supported.isPresent()) {
                    return DataResult.error(() -> context + " key " + oldFieldName + " is deprecated starting from pack format "
                            + (lastPreMinorVersion + 1) + ". Remove " + oldFieldName + " from your pack.mcmeta.");
                }
                if (hasPackFormatField && format.isPresent()) {
                    String packFormatError = validatePackFormatForRange(majorMin, majorMax);
                    if (packFormatError != null) {
                        return DataResult.error(() -> packFormatError);
                    }
                }
            } else {
                if (supported.isEmpty()) {
                    return DataResult.error(() -> context + " declares support for format " + majorMin
                            + ", but game versions supporting formats 17 to " + lastPreMinorVersion + " require a " + oldFieldName
                            + " field. Add \"" + oldFieldName + "\": [" + majorMin + ", " + lastPreMinorVersion
                            + "] or require a version greater or equal to " + (lastPreMinorVersion + 1) + ".0.");
                }
                var oldSupportedVersions = supported.get();
                if (oldSupportedVersions.minInclusive() != majorMin) {
                    return DataResult.error(() -> context + " version declaration mismatch between " + oldFieldName
                            + " (from " + oldSupportedVersions.minInclusive() + ") and min_format (" + min.get() + ")");
                }
                if (oldSupportedVersions.maxInclusive() != majorMax && oldSupportedVersions.maxInclusive() != lastPreMinorVersion) {
                    return DataResult.error(() -> context + " version declaration mismatch between " + oldFieldName
                            + " (up to " + oldSupportedVersions.maxInclusive() + ") and max_format (" + max.get() + ")");
                }
                if (hasPackFormatField) {
                    if (format.isEmpty()) {
                        return DataResult.error(() -> context + " declares support for formats up to " + lastPreMinorVersion
                                + ", but game versions supporting formats 17 to " + lastPreMinorVersion
                                + " require a pack_format field. Add \"pack_format\": " + majorMin
                                + " or require a version greater or equal to " + (lastPreMinorVersion + 1) + ".0.");
                    }
                    String packFormatError = validatePackFormatForRange(majorMin, majorMax);
                    if (packFormatError != null) {
                        return DataResult.error(() -> packFormatError);
                    }
                }
            }
            return DataResult.success(new FormatRange(min.get(), max.get()));
        }

        /**
         * 原版 {@code IntermediaryFormat.validateOldFormat}：只有旧字段的时候，范围两端的次版本都是 0。
         */
        private DataResult<FormatRange> validateOldFormat(int lastPreMinorVersion, boolean hasPackFormatField,
                                                          String context, String oldFieldName) {
            var oldSupportedVersions = supported.orElseThrow();
            int minVersion = oldSupportedVersions.minInclusive();
            int maxVersion = oldSupportedVersions.maxInclusive();
            if (maxVersion > lastPreMinorVersion) {
                return DataResult.error(() -> context + " declares support for version newer than " + lastPreMinorVersion
                        + ", but is missing mandatory fields min_format and max_format");
            }
            if (hasPackFormatField) {
                if (format.isEmpty()) {
                    return DataResult.error(() -> context + " declares support for formats up to " + lastPreMinorVersion
                            + ", but game versions supporting formats 17 to " + lastPreMinorVersion
                            + " require a pack_format field. Add \"pack_format\": " + minVersion
                            + " or require a version greater or equal to " + (lastPreMinorVersion + 1) + ".0.");
                }
                String packFormatError = validatePackFormatForRange(minVersion, maxVersion);
                if (packFormatError != null) {
                    return DataResult.error(() -> packFormatError);
                }
            }
            return legacyFormat(minVersion).map(bottom -> new FormatRange(bottom, PackFormat.of(maxVersion)));
        }

        /**
         * 原版 {@code IntermediaryFormat.validatePackFormatForRange}。
         */
        private @Nullable String validatePackFormatForRange(int minVersion, int maxVersion) {
            int mainFormat = format.orElseThrow();
            if (mainFormat >= minVersion && mainFormat <= maxVersion) {
                return mainFormat < 15
                        ? "Multi-version packs cannot support minimum version of less than 15, since this will leave versions in range unable to load pack."
                        : null;
            }
            return "Pack declared support for versions " + minVersion + " to " + maxVersion + " but declared main format is " + mainFormat;
        }

        /**
         * 原版用 {@code PackFormat.of} 直接转换旧字段中的整数，负数也得到格式版本。
         * {@link PackFormat} 不允许负数，所以这里对负数返回错误，文本取自原版 {@code ExtraCodecs.NON_NEGATIVE_INT}。
         * {@code supported_formats} 的下端不小于 0 的时候上端也不小于 0，所以范围只检查下端。
         */
        private static DataResult<PackFormat> legacyFormat(int major) {
            return major < 0
                    ? DataResult.error(() -> "Value must be non-negative: " + major)
                    : DataResult.success(PackFormat.of(major));
        }
    }
}
