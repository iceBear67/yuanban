package io.ib67.yuanban.datapack;

import com.google.gson.JsonPrimitive;
import io.ib67.yuanban.datapack.internal.PackSelector;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackFormat;
import io.ib67.yuanban.datapack.pack.PackMetadata;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.pack.PackPosition;
import io.ib67.yuanban.datapack.pack.PackRoot;
import io.ib67.yuanban.datapack.pack.PackSelectionConfig;
import io.ib67.yuanban.datapack.pack.ResourceFilter;
import io.ib67.yuanban.datapack.selection.DataPackConfig;
import io.ib67.yuanban.datapack.selection.Placement;
import io.ib67.yuanban.datapack.selection.SelectionEditor;
import io.ib67.yuanban.datapack.selection.SelectionMode;
import io.ib67.yuanban.datapack.selection.SelectionRequest;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackSelectorTest {
    private static final LoaderSettings SETTINGS = LoaderSettings.MINECRAFT_26_2;
    private static final Key VANILLA = Key.key("minecraft:vanilla");
    private static final Key TRADE = Key.key("minecraft:trade_rebalance");
    private static final Key REDSTONE = Key.key("minecraft:redstone_experiments");
    private static final PackSelectionConfig TOP = new PackSelectionConfig(false, PackPosition.TOP, false);
    private static final PackSelectionConfig BOTTOM = new PackSelectionConfig(false, PackPosition.BOTTOM, false);

    private static Pack pack(String id, PackOrigin origin, PackSelectionConfig config, Key... features) {
        var metadata = new PackMetadata(new JsonPrimitive(id), PackFormat.of(107).minorRange(), Set.of(features),
                ResourceFilter.NONE, List.of());
        return Pack.create(SETTINGS, id, origin, null, config, metadata, new PackRoot.Directory(Path.of(id)));
    }

    private static SortedMap<String, Pack> available(Pack... packs) {
        return new TreeMap<>(Arrays.stream(packs).collect(Collectors.toMap(Pack::id, pack -> pack)));
    }

    private static SortedMap<String, Pack> standard(Pack... extra) {
        var map = available(
                pack("vanilla", PackOrigin.BUILT_IN, BOTTOM, VANILLA),
                pack("trade_rebalance", PackOrigin.FEATURE, TOP, TRADE),
                pack("redstone_experiments", PackOrigin.FEATURE, TOP, REDSTONE),
                pack("file/b", PackOrigin.WORLD, TOP),
                pack("file/a", PackOrigin.WORLD, TOP));
        for (var pack : extra) {
            map.put(pack.id(), pack);
        }
        return map;
    }

    private static SelectionRequest request(List<String> enabled, List<String> disabled, SelectionMode mode, Key... features) {
        return new SelectionRequest(new DataPackConfig(enabled, disabled), Set.of(features), mode, false);
    }

    @Test
    void createWorldWithDefaultInitialPacks() {
        var selection = PackSelector.select(standard(), request(List.of("vanilla"), List.of(), SelectionMode.CREATE_WORLD), SETTINGS);
        assertEquals(List.of("vanilla", "file/a", "file/b"), selection.ids());
        assertEquals(Set.of(VANILLA), selection.enabledFeatures());
        assertEquals(List.of("redstone_experiments", "trade_rebalance"), selection.config().disabled());
        assertEquals(List.of("file/a", "file/b"), selection.report().autoEnabled());
    }

    @Test
    void createWorldEnablesFeaturesOfInitialPacks() {
        var selection = PackSelector.select(standard(),
                request(List.of("vanilla", "trade_rebalance"), List.of(), SelectionMode.CREATE_WORLD), SETTINGS);
        assertEquals(List.of("vanilla", "trade_rebalance", "file/a", "file/b"), selection.ids());
        assertEquals(Set.of(VANILLA, TRADE), selection.enabledFeatures());
    }

    @Test
    void loadWorldKeepsOrderReportsMissingAndAddsNewPacks() {
        var selection = PackSelector.select(standard(),
                request(List.of("vanilla", "file/b", "file/gone"), List.of("file/a"), SelectionMode.LOAD_WORLD, VANILLA), SETTINGS);
        assertEquals(List.of("vanilla", "file/b"), selection.ids());
        assertEquals(List.of("file/gone"), selection.report().missing());
        assertEquals(List.of(), selection.report().autoEnabled());

        var withNew = PackSelector.select(standard(pack("file/c", PackOrigin.WORLD, TOP)),
                request(List.of("vanilla", "file/b"), List.of("file/a", "trade_rebalance", "redstone_experiments"),
                        SelectionMode.LOAD_WORLD, VANILLA), SETTINGS);
        assertEquals(List.of("vanilla", "file/b", "file/c"), withNew.ids());
        assertEquals(List.of("file/a", "redstone_experiments", "trade_rebalance"), withNew.config().disabled());
    }

    @Test
    void loadWorldRemovesAndSkipsPacksWithoutFeatures() {
        var needsTrade = pack("file/needs_trade", PackOrigin.WORLD, TOP, TRADE);
        var selection = PackSelector.select(standard(needsTrade, pack("file/also_trade", PackOrigin.WORLD, TOP, TRADE)),
                request(List.of("vanilla", "file/needs_trade", "file/a", "file/b"), List.of(), SelectionMode.LOAD_WORLD, VANILLA),
                SETTINGS);
        assertEquals(List.of("vanilla", "file/a", "file/b"), selection.ids());
        assertEquals(List.of("file/needs_trade"), selection.report().removedForFeatures());
        assertEquals(List.of("file/also_trade"), selection.report().skippedForFeatures());
    }

    @Test
    void emptySelectionFallsBackToDefaultPack() {
        var selection = PackSelector.select(standard(),
                request(List.of(), List.of("vanilla", "file/a", "file/b"), SelectionMode.LOAD_WORLD, VANILLA), SETTINGS);
        assertEquals(List.of("vanilla"), selection.ids());
    }

    @Test
    void missingDefaultPackIsAnError() {
        var available = available(pack("file/a", PackOrigin.WORLD, TOP));
        assertThrows(SelectionException.class, () -> PackSelector.select(available,
                request(List.of(), List.of("file/a"), SelectionMode.LOAD_WORLD), SETTINGS));
    }

    @Test
    void safeModeOnlyEnablesDefaultAndForcedPacks() {
        var selection = PackSelector.select(standard(), new SelectionRequest(
                new DataPackConfig(List.of("vanilla", "file/a"), List.of()), Set.of(VANILLA, TRADE), SelectionMode.LOAD_WORLD, true), SETTINGS);
        assertEquals(List.of("vanilla", "trade_rebalance"), selection.ids());
        assertEquals(List.of(), selection.config().disabled());
        assertEquals(List.of("trade_rebalance"), selection.report().forcedFeaturePacks());
    }

    @Test
    void forcesFeaturePacksForWorldFeatures() {
        var selection = PackSelector.select(standard(),
                request(List.of("vanilla", "file/a", "file/b"), List.of("trade_rebalance", "redstone_experiments"),
                        SelectionMode.LOAD_WORLD, VANILLA, TRADE), SETTINGS);
        assertEquals(List.of("vanilla", "file/a", "file/b", "trade_rebalance"), selection.ids());
        assertEquals(Set.of(VANILLA, TRADE), selection.enabledFeatures());
        assertEquals(List.of("redstone_experiments"), selection.config().disabled());
    }

    @Test
    void requiredPacksAreInsertedNextToFixedPacks() {
        var fixedBottom = pack("core", PackOrigin.FEATURE, new PackSelectionConfig(true, PackPosition.BOTTOM, true));
        var requiredBottom = pack("extra", PackOrigin.FEATURE, new PackSelectionConfig(true, PackPosition.BOTTOM, false));
        var fixedTop = pack("overrides", PackOrigin.FEATURE, new PackSelectionConfig(true, PackPosition.TOP, true));
        var requiredTop = pack("zz_top", PackOrigin.FEATURE, new PackSelectionConfig(true, PackPosition.TOP, false));
        var selection = PackSelector.select(standard(fixedBottom, requiredBottom, fixedTop, requiredTop),
                request(List.of("vanilla"), List.of("file/a", "file/b"), SelectionMode.LOAD_WORLD, VANILLA), SETTINGS);
        assertEquals(List.of("core", "extra", "vanilla", "zz_top", "overrides"), selection.ids());
    }

    @Test
    void explicitSelectionValidatesInput() {
        var available = standard(pack("must", PackOrigin.WORLD, new PackSelectionConfig(true, PackPosition.TOP, false)));
        var features = Set.of(VANILLA);
        var selection = PackSelector.explicit(available, List.of("file/b", "vanilla", "must"), features);
        assertEquals(List.of("file/b", "vanilla", "must"), selection.ids());
        assertEquals(List.of("file/a", "redstone_experiments", "trade_rebalance"), selection.config().disabled());

        assertThrows(SelectionException.class, () -> PackSelector.explicit(available, List.of("vanilla", "must", "nope"), features));
        assertThrows(SelectionException.class, () -> PackSelector.explicit(available, List.of("vanilla", "must", "vanilla"), features));
        assertThrows(SelectionException.class, () -> PackSelector.explicit(available, List.of("vanilla", "must", "trade_rebalance"), features));
        assertThrows(SelectionException.class, () -> PackSelector.explicit(available, List.of("vanilla"), features));
    }

    @Test
    void editorEnablesAtPlacements() {
        var available = standard(pack("pinned", PackOrigin.WORLD, new PackSelectionConfig(false, PackPosition.TOP, true)));
        var enabled = List.of(available.get("vanilla"), available.get("file/a"), available.get("pinned"));
        var editor = new SelectionEditor(available, enabled, Set.of(VANILLA));
        assertEquals(List.of("vanilla", "file/a", "file/b", "pinned"), editor.enable("file/b", new Placement.DefaultPosition()));
        assertEquals(List.of("file/b", "vanilla", "file/a", "pinned"), editor.enable("file/b", new Placement.First()));
        assertEquals(List.of("vanilla", "file/a", "pinned", "file/b"), editor.enable("file/b", new Placement.Last()));
        assertEquals(List.of("vanilla", "file/b", "file/a", "pinned"), editor.enable("file/b", new Placement.Before("file/a")));
        assertEquals(List.of("vanilla", "file/a", "file/b", "pinned"), editor.enable("file/b", new Placement.After("file/a")));
    }

    @Test
    void editorRejectsInvalidChanges() {
        var available = standard(pack("must", PackOrigin.WORLD, new PackSelectionConfig(true, PackPosition.TOP, false)));
        var enabled = List.of(available.get("vanilla"), available.get("trade_rebalance"), available.get("must"));
        var editor = new SelectionEditor(available, enabled, Set.of(VANILLA, TRADE));
        assertThrows(SelectionException.class, () -> editor.enable("nope", new Placement.Last()));
        assertThrows(SelectionException.class, () -> editor.enable("vanilla", new Placement.Last()));
        assertThrows(SelectionException.class, () -> editor.enable("redstone_experiments", new Placement.Last()));
        assertThrows(SelectionException.class, () -> editor.enable("file/a", new Placement.Before("file/b")));
        assertThrows(SelectionException.class, () -> editor.disable("file/a"));
        assertThrows(SelectionException.class, () -> editor.disable("trade_rebalance"));
        assertThrows(SelectionException.class, () -> editor.disable("must"));
        assertEquals(List.of("trade_rebalance", "must"), editor.disable("vanilla"));
        assertEquals(List.of("vanilla", "file/a", "trade_rebalance", "must"), editor.enable("file/a", new Placement.After("vanilla")));
    }
}
