package io.ib67.yuanban.datapack.internal;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ComponentSerialization;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.Identifiers;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.PackMetadataException;
import io.ib67.yuanban.datapack.internal.PackFormatCodecs.IntermediaryFormat;
import io.ib67.yuanban.datapack.internal.PackFormatCodecs.IntermediaryFormatHolder;
import io.ib67.yuanban.datapack.pack.FilterRule;
import io.ib67.yuanban.datapack.pack.FormatRange;
import io.ib67.yuanban.datapack.pack.OverlayEntry;
import io.ib67.yuanban.datapack.pack.PackMetadata;
import io.ib67.yuanban.datapack.pack.ResourceFilter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 解析 {@code pack.mcmeta}，移植原版 26.2 读取数据包元数据所用的编解码器：{@code PackMetadataSection}、
 * {@code FeatureFlagsMetadataSection}、{@code OverlayMetadataSection}、{@code ResourceFilterSection}。
 * 和原版 {@code ResourceMetadata.getSection} 相同，每个段落存在时单独用 {@link JsonOps#INSTANCE} 解码。
 * 原版遇到错误时改用只读取描述的编解码器、跳过数据包或者忽略 {@code filter}；这里任何错误都抛出 {@link PackMetadataException}，
 * 原因是编解码器给出的错误文本。
 */
@ApiStatus.Internal
public final class PackMetadataParser {
    /**
     * 原版 {@code OverlayMetadataSection.DIR_VALIDATOR}。
     */
    private static final Pattern DIR_VALIDATOR = Pattern.compile("[-_a-zA-Z0-9.]+");
    /**
     * 原版 {@code PackMetadataSection.codecForPackType(PackType.SERVER_DATA)}。
     */
    private static final Codec<PackSection> PACK_CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("description").forGetter(PackSection::description),
            PackFormatCodecs.packCodec().forGetter(PackSection::formats)
    ).apply(i, PackSection::new));
    /**
     * 原版 {@code OverlayMetadataSection.OverlayEntry.IntermediateEntry.CODEC}。
     */
    private static final Codec<IntermediateEntry> INTERMEDIATE_ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            IntermediaryFormat.OVERLAY_CODEC.forGetter(IntermediateEntry::format),
            Codec.STRING.validate(PackMetadataParser::validateOverlayDir).fieldOf("directory").forGetter(IntermediateEntry::overlay)
    ).apply(i, IntermediateEntry::new));
    /**
     * 原版 {@code OverlayMetadataSection.OverlayEntry.listCodecForPackType(PackType.SERVER_DATA)}。
     */
    private static final Codec<List<OverlayEntry>> OVERLAY_LIST_CODEC = INTERMEDIATE_ENTRY_CODEC.listOf().flatXmap(
            list -> PackFormatCodecs.validateHolderList(list, PackFormatCodecs.LAST_PRE_MINOR_VERSION,
                    (entry, formats) -> new OverlayEntry(entry.overlay(), formats)),
            list -> DataResult.success(list.stream()
                    .map(entry -> new IntermediateEntry(
                            IntermediaryFormat.fromRange(entry.formats(), PackFormatCodecs.LAST_PRE_MINOR_VERSION), entry.directory()))
                    .toList()));
    /**
     * 原版 {@code IdentifierPattern.CODEC}：两项都可以省略，省略的一项匹配任何字符串。
     */
    private static final Codec<FilterRule> IDENTIFIER_PATTERN_CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.PATTERN.optionalFieldOf("namespace").forGetter(rule -> Optional.ofNullable(rule.namespace())),
            ExtraCodecs.PATTERN.optionalFieldOf("path").forGetter(rule -> Optional.ofNullable(rule.path()))
    ).apply(i, (namespace, path) -> new FilterRule(namespace.orElse(null), path.orElse(null))));
    /**
     * 原版 {@code PackMetadataSection.SERVER_TYPE}。
     */
    private static final SectionType<PackSection> PACK = new SectionType<>("pack", PACK_CODEC);
    /**
     * 原版 {@code OverlayMetadataSection.SERVER_TYPE}。
     */
    private static final SectionType<List<OverlayEntry>> OVERLAYS = new SectionType<>("overlays",
            OVERLAY_LIST_CODEC.fieldOf("entries").codec());
    /**
     * 原版 {@code ResourceFilterSection.TYPE}。
     */
    private static final SectionType<ResourceFilter> FILTER = new SectionType<>("filter",
            Codec.list(IDENTIFIER_PATTERN_CODEC).fieldOf("block").xmap(ResourceFilter::new, ResourceFilter::block).codec());

    private PackMetadataParser() {
    }

    /**
     * 按原版 {@code Pack.readPackMetadata} 的顺序读取 {@code pack}、{@code features}、{@code overlays}，最后读取 {@code filter}。
     *
     * @param metadata {@code pack.mcmeta} 的根对象
     */
    public static PackMetadata parse(String packId, JsonObject metadata, LoaderSettings settings) {
        var pack = PACK.read(packId, metadata)
                .orElseThrow(() -> new PackMetadataException(packId, "Missing metadata in pack " + packId));
        var features = featureFlagsSection(settings.featureFlags()).read(packId, metadata).orElse(Set.of());
        var overlays = OVERLAYS.read(packId, metadata).orElse(List.of());
        var filter = FILTER.read(packId, metadata).orElse(ResourceFilter.NONE);
        // 文本组件只做校验，记录中保存 JSON 原文，由宿主转换
        var description = metadata.getAsJsonObject(PACK.name()).get("description").deepCopy();
        return new PackMetadata(description, pack.formats(), features, filter, overlays);
    }

    /**
     * 原版 {@code FeatureFlagsMetadataSection.TYPE}，{@code enabled} 使用原版 {@code FeatureFlagRegistry.codec()} 的规则：
     * 未知的 ID 收集到 {@link HashSet} 中一次报出，集合的顺序与原版相同，因为 {@link Key} 和原版 {@code Identifier} 的散列值相同。
     *
     * @param known 当前游戏已知的全部特性开关
     */
    private static SectionType<Set<Key>> featureFlagsSection(Set<Key> known) {
        Codec<Set<Key>> flags = Identifiers.CODEC.listOf().comapFlatMap(ids -> {
            var unknownIds = new HashSet<Key>();
            var result = new HashSet<Key>();
            for (var id : ids) {
                if (known.contains(id)) {
                    result.add(id);
                } else {
                    unknownIds.add(id);
                }
            }
            var flagSet = Set.copyOf(result);
            return !unknownIds.isEmpty()
                    ? DataResult.error(() -> "Unknown feature ids: " + unknownIds, flagSet)
                    : DataResult.success(flagSet);
        }, List::copyOf);
        return new SectionType<>("features", flags.fieldOf("enabled").codec());
    }

    /**
     * 原版 {@code OverlayMetadataSection.validateOverlayDir}。
     */
    private static DataResult<String> validateOverlayDir(String path) {
        return !DIR_VALIDATOR.matcher(path).matches()
                ? DataResult.error(() -> path + " is not accepted directory name")
                : DataResult.success(path);
    }

    /**
     * 原版 {@code MetadataSectionType} 和 {@code ResourceMetadata.getSection}：根对象中有这个段落的时候用编解码器解码，
     * 段落的值是 {@code null} 也交给编解码器。
     */
    private record SectionType<T>(String name, Codec<T> codec) {
        Optional<T> read(String packId, JsonObject metadata) {
            var rawSection = metadata.get(name);
            if (rawSection == null) {
                return Optional.empty();
            }
            return Optional.of(codec.parse(JsonOps.INSTANCE, rawSection)
                    .getOrThrow(message -> new PackMetadataException(packId, message)));
        }
    }

    /**
     * 原版 {@code PackMetadataSection}。
     */
    private record PackSection(Component description, FormatRange formats) {
    }

    /**
     * 原版 {@code OverlayMetadataSection.OverlayEntry.IntermediateEntry}。{@link #toString()} 返回目录名，用在错误文本中。
     */
    private record IntermediateEntry(IntermediaryFormat format, String overlay) implements IntermediaryFormatHolder {
        @Override
        public @NotNull String toString() {
            return overlay;
        }
    }
}
