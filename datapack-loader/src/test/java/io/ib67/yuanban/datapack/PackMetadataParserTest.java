package io.ib67.yuanban.datapack;

import com.google.gson.JsonParser;
import io.ib67.yuanban.datapack.internal.PackMetadataParser;
import io.ib67.yuanban.datapack.pack.FormatRange;
import io.ib67.yuanban.datapack.pack.OverlayEntry;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackCompatibility;
import io.ib67.yuanban.datapack.pack.PackFormat;
import io.ib67.yuanban.datapack.pack.PackMetadata;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.pack.PackPosition;
import io.ib67.yuanban.datapack.pack.PackRoot;
import io.ib67.yuanban.datapack.pack.PackSelectionConfig;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackMetadataParserTest {
    private static final LoaderSettings SETTINGS = LoaderSettings.MINECRAFT_26_2;
    private static final String MESSAGE_PREFIX = "数据包 test 的 pack.mcmeta 不合法：";

    private static PackMetadata parse(String json) {
        return PackMetadataParser.parse("test", JsonParser.parseString(json).getAsJsonObject(), SETTINGS);
    }

    /**
     * 解析失败，返回异常信息中编解码器给出的错误文本。
     */
    private static String error(String json) {
        var exception = assertThrows(PackMetadataException.class, () -> parse(json));
        assertEquals("test", exception.getPackId());
        assertTrue(exception.getMessage().startsWith(MESSAGE_PREFIX), exception.getMessage());
        return exception.getMessage().substring(MESSAGE_PREFIX.length());
    }

    private static String pack(String formats) {
        return "{\"pack\": {\"description\": \"d\", " + formats + "}}";
    }

    private static String withSection(String section) {
        return "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, " + section + "}";
    }

    private static FormatRange range(PackFormat min, PackFormat max) {
        return new FormatRange(min, max);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "107; 107; 107.0; 107.*",
            "[107]; [107]; 107.0; 107.*",
            "[107, 1]; [108, 3]; 107.1; 108.3",
            "[107, 1, 9]; 107; 107.1; 107.*",
            "82; [82, 0]; 82.0; 82.0",
            "107.9; [108.5, 2.7]; 107.0; 108.2"
    })
    void formatForms(String min, String max, String expectedMin, String expectedMax) {
        var metadata = parse(pack("\"min_format\": " + min + ", \"max_format\": " + max));
        assertEquals(expectedMin, metadata.formats().min().toString());
        assertEquals(expectedMax, metadata.formats().max().toString());
    }

    @Test
    void packFormatOnlyGivesSingleMajorVersion() {
        var metadata = parse(pack("\"pack_format\": 48"));
        assertEquals(range(PackFormat.of(48), PackFormat.of(48)), metadata.formats());
        assertEquals(PackCompatibility.TOO_OLD, PackCompatibility.of(metadata.formats(), SETTINGS.dataFormat()));
        assertEquals(range(PackFormat.of(81), PackFormat.of(81)), parse(pack("\"pack_format\": 81")).formats());
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "\"pack_format\": 48, \"supported_formats\": [40, 60]; 40; 60",
            "\"pack_format\": 50, \"supported_formats\": 50; 50; 50",
            "\"pack_format\": 48, \"supported_formats\": {\"min_inclusive\": 40, \"max_inclusive\": 81}; 40; 81"
    })
    void supportedFormatsOnlyGivesMajorVersions(String fields, int min, int max) {
        assertEquals(range(PackFormat.of(min), PackFormat.of(max)), parse(pack(fields)).formats());
    }

    @Test
    void legacyFieldsMatchingNewFieldsAreAccepted() {
        var metadata = parse(pack("\"pack_format\": 48, \"supported_formats\": [48, 107], \"min_format\": 48, \"max_format\": 107"));
        assertEquals(range(PackFormat.of(48), PackFormat.of(107, PackFormat.ANY_MINOR)), metadata.formats());
        // supported_formats 的上端可以停在 81
        assertEquals(range(PackFormat.of(48), PackFormat.of(107, PackFormat.ANY_MINOR)),
                parse(pack("\"pack_format\": 48, \"supported_formats\": [48, 81], \"min_format\": 48, \"max_format\": 107")).formats());
        // min_format 高于 81 的时候 pack_format 只需要在范围内
        assertEquals(range(PackFormat.of(90), PackFormat.of(107, PackFormat.ANY_MINOR)),
                parse(pack("\"pack_format\": 100, \"min_format\": 90, \"max_format\": 107")).formats());
    }

    static Stream<Arguments> invalidFormats() {
        return Stream.of(
                Arguments.of("\"pack_format\": 82",
                        "Pack declares support for version newer than 81, but is missing mandatory fields min_format and max_format"),
                Arguments.of("\"pack_format\": 48, \"supported_formats\": [48, 107]",
                        "Pack declares support for version newer than 81, but is missing mandatory fields min_format and max_format"),
                Arguments.of("\"supported_formats\": [40, 60]",
                        "Pack declares support for formats up to 81, but game versions supporting formats 17 to 81 require a pack_format field."
                                + " Add \"pack_format\": 40 or require a version greater or equal to 82.0."),
                Arguments.of("\"pack_format\": 70, \"supported_formats\": [40, 60]",
                        "Pack declared support for versions 40 to 60 but declared main format is 70"),
                Arguments.of("\"pack_format\": 10, \"supported_formats\": [10, 20]",
                        "Multi-version packs cannot support minimum version of less than 15, since this will leave versions in range unable to load pack."),
                Arguments.of("\"min_format\": 82, \"max_format\": 107, \"supported_formats\": [82, 107]",
                        "Pack key supported_formats is deprecated starting from pack format 82. Remove supported_formats from your pack.mcmeta."),
                Arguments.of("\"min_format\": 90, \"max_format\": 107, \"pack_format\": 48",
                        "Pack declared support for versions 90 to 107 but declared main format is 48"),
                Arguments.of("\"min_format\": 48, \"max_format\": 107",
                        "Pack declares support for format 48, but game versions supporting formats 17 to 81 require a supported_formats field."
                                + " Add \"supported_formats\": [48, 81] or require a version greater or equal to 82.0."),
                Arguments.of("\"min_format\": 48, \"max_format\": 107, \"supported_formats\": [40, 107], \"pack_format\": 48",
                        "Pack version declaration mismatch between supported_formats (from 40) and min_format (48.0)"),
                Arguments.of("\"min_format\": 48, \"max_format\": 107, \"supported_formats\": [48, 90], \"pack_format\": 48",
                        "Pack version declaration mismatch between supported_formats (up to 90) and max_format (107.*)"),
                Arguments.of("\"min_format\": 48, \"max_format\": 107, \"supported_formats\": [48, 107]",
                        "Pack declares support for formats up to 81, but game versions supporting formats 17 to 81 require a pack_format field."
                                + " Add \"pack_format\": 48 or require a version greater or equal to 82.0."),
                Arguments.of("\"min_format\": 107",
                        "Pack missing field, must declare both min_format and max_format"),
                Arguments.of("\"min_format\": 108, \"max_format\": 107",
                        "Pack min_format (108.0) is greater than max_format (107.*)"),
                Arguments.of("\"min_format\": [107, 2], \"max_format\": [107, 1]",
                        "Pack min_format (107.2) is greater than max_format (107.1)"),
                Arguments.of("\"max_format\": 107, \"min_format\": \"107\"",
                        "Failed to parse either. First: Not a json array: \"107\"; Second: Not a number: \"107\";"
                                + " Pack missing field, must declare both min_format and max_format"),
                Arguments.of("\"max_format\": 107, \"min_format\": []",
                        "Failed to parse either. First: List is too short: 0, expected range [1-256]; Second: Not a number: [];"
                                + " Pack missing field, must declare both min_format and max_format"),
                Arguments.of("\"max_format\": 107, \"min_format\": -1",
                        "Failed to parse either. First: Not a json array: -1; Second: Value must be non-negative: -1;"
                                + " Pack missing field, must declare both min_format and max_format"),
                Arguments.of("\"pack_format\": -1",
                        "Value must be non-negative: -1"),
                Arguments.of("\"pack_format\": 48, \"supported_formats\": [-1, 48]",
                        "Value must be non-negative: -1"),
                Arguments.of("",
                        "Pack could not be parsed, missing format version information")
        );
    }

    @ParameterizedTest
    @MethodSource
    void invalidFormats(String fields, String message) {
        assertEquals(message, error("{\"pack\": {\"description\": \"d\"" + (fields.isEmpty() ? "" : ", " + fields) + "}}"));
    }

    @Test
    void optionalSectionsDefaultToEmpty() {
        var metadata = parse(pack("\"min_format\": 107, \"max_format\": 107"));
        assertTrue(metadata.features().isEmpty());
        assertTrue(metadata.filter().isEmpty());
        assertTrue(metadata.overlays().isEmpty());
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "\"plain\"",
            "{\"translate\": \"x\", \"color\": \"red\"}",
            "[\"a\", {\"text\": \"b\"}]",
            "[[\"nested\"]]"
    })
    void descriptionKeepsRawComponent(String description) {
        var metadata = parse("{\"pack\": {\"description\": " + description + ", \"min_format\": 107, \"max_format\": 107}}");
        assertEquals(JsonParser.parseString(description), metadata.description());
    }

    @Test
    void invalidDescriptions() {
        assertEquals("Failed to parse either. First: Failed to parse either. First: Not a string: 1; Second: Not a json array: 1;"
                        + " Second: Not a JSON object: 1",
                error("{\"pack\": {\"description\": 1, \"min_format\": 107, \"max_format\": 107}}"));
        assertEquals("Failed to parse either. First: Failed to parse either. First: Not a string: []; Second: List must have contents;"
                        + " Second: Not a JSON object: []",
                error("{\"pack\": {\"description\": [], \"min_format\": 107, \"max_format\": 107}}"));
        assertEquals("No key description in MapLike[{\"min_format\":107,\"max_format\":107}]",
                error("{\"pack\": {\"min_format\": 107, \"max_format\": 107}}"));
        assertEquals("No key description in MapLike[{\"description\":null,\"min_format\":107,\"max_format\":107}]",
                error("{\"pack\": {\"description\": null, \"min_format\": 107, \"max_format\": 107}}"));
    }

    @Test
    void parsesAllSections() {
        var metadata = parse("""
                {"pack": {"description": "d", "min_format": 107, "max_format": 107},
                 "features": {"enabled": ["minecraft:trade_rebalance", "trade_rebalance"]},
                 "filter": {"block": [{"namespace": "minecraft", "path": "recipe/.*"}, {"path": "loot"}, {}]},
                 "overlays": {"entries": [{"directory": "Over_lay.1", "min_format": [107, 1], "max_format": 200}]}}
                """);
        assertEquals(Set.of(Key.key("minecraft:trade_rebalance")), metadata.features());
        assertEquals(3, metadata.filter().block().size());
        assertEquals("recipe/.*", metadata.filter().block().getFirst().path().pattern());
        assertNull(metadata.filter().block().get(1).namespace());
        assertNull(metadata.filter().block().get(2).path());
        assertEquals("Over_lay.1", metadata.overlays().getFirst().directory());
        assertEquals(PackFormat.of(107, 1), metadata.overlays().getFirst().formats().min());
    }

    @Test
    void unknownFeatureIdsAreReportedTogether() {
        // 原版把未知 ID 收集到 HashSet 中，顺序由散列值决定；Key 的散列值与原版 Identifier 的 31 * namespace + path 相同
        var key = Key.key("example", "a");
        assertEquals(31 * "example".hashCode() + "a".hashCode(), key.hashCode());
        assertEquals("Unknown feature ids: [example:b, example:a, minecraft:unknown]", error(withSection(
                "\"features\": {\"enabled\": [\"example:b\", \"minecraft:vanilla\", \"example:a\", \"unknown\", \"example:b\"]}")));
    }

    @Test
    void featureSectionErrors() {
        assertEquals("No key enabled in MapLike[{}]", error(withSection("\"features\": {}")));
        assertEquals("Not a JSON object: null", error(withSection("\"features\": null")));
        assertEquals("Not a valid resource location: Bad Key Non [a-z0-9/._-] character in path of location: minecraft:Bad Key",
                error(withSection("\"features\": {\"enabled\": [\"Bad Key\"]}")));
    }

    @Test
    void filterPatternErrors() {
        var syntaxError = assertThrows(PatternSyntaxException.class, () -> Pattern.compile("("));
        assertEquals("Invalid regex pattern '(': " + syntaxError.getMessage(),
                error(withSection("\"filter\": {\"block\": [{\"path\": \"(\"}]}")));
        assertEquals("Not a string: 1", error(withSection("\"filter\": {\"block\": [{\"namespace\": 1}]}")));
        assertEquals("No key block in MapLike[{}]", error(withSection("\"filter\": {}")));
    }

    @Test
    void overlayEntriesWithoutFormatsAreSkipped() {
        var metadata = parse(withSection("""
                "overlays": {"entries": [{"directory": "skipped"}, {"directory": "kept", "min_format": 107, "max_format": 107}]}"""));
        assertEquals(List.of("kept"), metadata.overlays().stream().map(OverlayEntry::directory).toList());
    }

    @Test
    void legacyOverlayFormats() {
        var metadata = parse(withSection("""
                "overlays": {"entries": [
                  {"directory": "old", "min_format": 48, "max_format": 107, "formats": [48, 107]},
                  {"directory": "new", "min_format": 107, "max_format": 107, "formats": [107, 107]},
                  {"directory": "legacy", "formats": [40, 60]}]}"""));
        assertEquals(List.of(
                        range(PackFormat.of(48), PackFormat.of(107, PackFormat.ANY_MINOR)),
                        range(PackFormat.of(107), PackFormat.of(107, PackFormat.ANY_MINOR)),
                        range(PackFormat.of(40), PackFormat.of(60))),
                metadata.overlays().stream().map(OverlayEntry::formats).toList());
    }

    static Stream<Arguments> invalidOverlays() {
        return Stream.of(
                Arguments.of("""
                                [{"directory": "old", "min_format": 48, "max_format": 107, "formats": [48, 107]},
                                 {"directory": "new", "min_format": 107, "max_format": 107}]""",
                        "Overlay \"new\" missing required field formats, must be present in all overlays for any overlays to work across game versions"),
                Arguments.of("[{\"directory\": \"a\", \"min_format\": 48, \"max_format\": 107}]",
                        "Overlay \"a\" missing required field formats, must be present in all overlays for any overlays to work across game versions"),
                Arguments.of("[{\"directory\": \"a\", \"min_format\": 107, \"max_format\": 107, \"formats\": [107, 107]}]",
                        "Overlay \"a\" key formats is deprecated starting from pack format 82. Remove formats from your pack.mcmeta."),
                Arguments.of("[{\"directory\": \"a\", \"formats\": 107}]",
                        "Overlay \"a\" declares support for version newer than 81, but is missing mandatory fields min_format and max_format"),
                Arguments.of("[{\"directory\": \"a\", \"min_format\": 107}]",
                        "Overlay \"a\" missing field, must declare both min_format and max_format"),
                Arguments.of("[{\"directory\": \"a/b\", \"min_format\": 107, \"max_format\": 107}]",
                        "a/b is not accepted directory name"),
                Arguments.of("[{\"directory\": \"a/b\"}]",
                        "a/b is not accepted directory name"),
                Arguments.of("[{\"min_format\": 107, \"max_format\": 107}]",
                        "No key directory in MapLike[{\"min_format\":107,\"max_format\":107}]")
        );
    }

    @ParameterizedTest
    @MethodSource
    void invalidOverlays(String entries, String message) {
        assertEquals(message, error(withSection("\"overlays\": {\"entries\": " + entries + "}")));
    }

    @Test
    void missingSections() {
        assertEquals("Missing metadata in pack test", error("{}"));
        assertEquals("Not a JSON object: []", error("{\"pack\": []}"));
        assertEquals("Not a JSON object: null", error("{\"pack\": null}"));
        assertEquals("No key entries in MapLike[{}]", error(withSection("\"overlays\": {}")));
    }

    @Test
    void compatibility() {
        var current = PackFormat.of(107, 1);
        assertEquals(PackCompatibility.COMPATIBLE, PackCompatibility.of(PackFormat.of(107).minorRange(), current));
        assertEquals(PackCompatibility.TOO_OLD, PackCompatibility.of(new FormatRange(PackFormat.of(100), PackFormat.of(107, 0)), current));
        assertEquals(PackCompatibility.TOO_NEW, PackCompatibility.of(new FormatRange(PackFormat.of(107, 2), PackFormat.of(110)), current));
    }

    @Test
    void activeOverlaysFollowCurrentFormat() {
        var metadata = parse("""
                {"pack": {"description": "d", "min_format": 100, "max_format": 200},
                 "overlays": {"entries": [
                   {"directory": "a", "min_format": 107, "max_format": 107},
                   {"directory": "b", "min_format": [107, 2], "max_format": 200},
                   {"directory": "c", "min_format": 100, "max_format": [107, 1]},
                   {"directory": "d", "min_format": 100, "max_format": [107, 0]}]}}
                """);
        var pack = Pack.create(SETTINGS, "test", PackOrigin.WORLD, null,
                new PackSelectionConfig(false, PackPosition.TOP, false), metadata, new PackRoot.Directory(Path.of(".")));
        assertEquals(List.of("a", "c"), pack.activeOverlays());
        assertEquals(PackCompatibility.COMPATIBLE, pack.compatibility());
        assertFalse(pack.knownPack().isPresent());
    }

    @Test
    void settingsRejectOldFormats() {
        assertThrows(IllegalArgumentException.class, () -> new LoaderSettings(PackFormat.of(107, 0), Set.of(), "vanilla"));
    }
}
