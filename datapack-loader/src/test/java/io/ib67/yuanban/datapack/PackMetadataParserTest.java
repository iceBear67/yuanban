package io.ib67.yuanban.datapack;

import com.google.gson.JsonParser;
import io.ib67.yuanban.datapack.internal.PackMetadataParser;
import io.ib67.yuanban.datapack.pack.FormatRange;
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
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackMetadataParserTest {
    private static final LoaderSettings SETTINGS = LoaderSettings.MINECRAFT_26_2;

    private static PackMetadata parse(String json) {
        return PackMetadataParser.parse("test", JsonParser.parseString(json), SETTINGS);
    }

    private static PackMetadataException fail(String json) {
        return assertThrows(PackMetadataException.class, () -> parse(json));
    }

    private static String pack(String formats) {
        return "{\"pack\": {\"description\": \"d\", " + formats + "}}";
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "107; 107; 107.0; 107.*",
            "[107]; [107]; 107.0; 107.*",
            "[107, 1]; [108, 3]; 107.1; 108.3",
            "[107, 1, 9]; 107; 107.1; 107.*",
            "0; [0, 0]; 0.0; 0.0",
            "107.9; [108.5, 2.7]; 107.0; 108.2"
    })
    void formatForms(String min, String max, String expectedMin, String expectedMax) {
        var metadata = parse(pack("\"min_format\": " + min + ", \"max_format\": " + max));
        assertEquals(expectedMin, metadata.formats().min().toString());
        assertEquals(expectedMax, metadata.formats().max().toString());
    }

    @Test
    void legacyFieldsAreIgnored() {
        var metadata = parse("""
                {"pack": {"description": "d", "pack_format": 48, "supported_formats": [48, 107], "min_format": 48, "max_format": 107},
                 "overlays": {"entries": [{"directory": "ov", "formats": [48, 107], "min_format": 48, "max_format": 107}]}}
                """);
        assertEquals(new FormatRange(PackFormat.of(48), PackFormat.of(107, PackFormat.ANY_MINOR)), metadata.formats());
        assertEquals(1, metadata.overlays().size());
    }

    @Test
    void optionalSectionsDefaultToEmpty() {
        var metadata = parse(pack("\"min_format\": 107, \"max_format\": 107"));
        assertTrue(metadata.features().isEmpty());
        assertTrue(metadata.filter().isEmpty());
        assertTrue(metadata.overlays().isEmpty());
    }

    @Test
    void descriptionKeepsRawComponent() {
        var metadata = parse("{\"pack\": {\"description\": {\"translate\": \"x\"}, \"min_format\": 107, \"max_format\": 107}}");
        assertEquals("x", metadata.description().getAsJsonObject().get("translate").getAsString());
    }

    @Test
    void parsesAllSections() {
        var metadata = parse("""
                {"pack": {"description": "d", "min_format": 107, "max_format": 107},
                 "features": {"enabled": ["minecraft:trade_rebalance"]},
                 "filter": {"block": [{"namespace": "minecraft", "path": "recipe/.*"}, {"path": "loot"}]},
                 "overlays": {"entries": [{"directory": "Over_lay.1", "min_format": [107, 1], "max_format": 200}]}}
                """);
        assertEquals(Set.of(Key.key("minecraft:trade_rebalance")), metadata.features());
        assertEquals(2, metadata.filter().block().size());
        assertNull(metadata.filter().block().get(1).namespace());
        assertEquals("Over_lay.1", metadata.overlays().getFirst().directory());
        assertEquals(PackFormat.of(107, 1), metadata.overlays().getFirst().formats().min());
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "[]; <root>",
            "{}; pack",
            "{\"pack\": []}; pack",
            "{\"pack\": {\"min_format\": 107, \"max_format\": 107}}; pack.description",
            "{\"pack\": {\"description\": 1, \"min_format\": 107, \"max_format\": 107}}; pack.description",
            "{\"pack\": {\"description\": \"d\", \"pack_format\": 107}}; pack.min_format",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107}}; pack.max_format",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 108, \"max_format\": 107}}; pack.min_format",
            "{\"pack\": {\"description\": \"d\", \"min_format\": -1, \"max_format\": 107}}; pack.min_format",
            "{\"pack\": {\"description\": \"d\", \"min_format\": -0.5e1, \"max_format\": 107}}; pack.min_format",
            "{\"pack\": {\"description\": \"d\", \"min_format\": \"107\", \"max_format\": 107}}; pack.min_format",
            "{\"pack\": {\"description\": \"d\", \"min_format\": [], \"max_format\": 107}}; pack.min_format",
            "{\"pack\": {\"description\": \"d\", \"min_format\": [107, -1], \"max_format\": 107}}; pack.min_format[1]",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"features\": {}}; features.enabled",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"features\": {\"enabled\": [\"minecraft:unknown\"]}}; features.enabled[0]",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"features\": {\"enabled\": [\"Bad Key\"]}}; features.enabled[0]",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"filter\": {}}; filter.block",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"filter\": {\"block\": [{\"path\": \"(\"}]}}; filter.block[0].path",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"filter\": {\"block\": [{\"namespace\": 1}]}}; filter.block[0].namespace",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"overlays\": {}}; overlays.entries",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"overlays\": {\"entries\": [{\"directory\": \"a/b\", \"min_format\": 107, \"max_format\": 107}]}}; overlays.entries[0].directory",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"overlays\": {\"entries\": [{\"directory\": \"a\", \"formats\": 107}]}}; overlays.entries[0].min_format",
            "{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}, \"overlays\": {\"entries\": [{\"directory\": \"a\", \"min_format\": 107}]}}; overlays.entries[0].max_format"
    })
    void rejectsInvalidMetadata(String json, String field) {
        var exception = fail(json);
        assertEquals("test", exception.getPackId());
        assertEquals(field.equals("<root>") ? "" : field, exception.getField());
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
