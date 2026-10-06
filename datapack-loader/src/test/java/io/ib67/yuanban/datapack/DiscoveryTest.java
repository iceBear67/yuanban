package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.pack.KnownPack;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackFormat;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.pack.PackRoot;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.datapack.provider.FolderPackProvider;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryTest {
    private static final LoaderSettings SETTINGS = LoaderSettings.MINECRAFT_26_2;

    @TempDir
    Path tempDir;

    private static Map<String, Pack> discover(io.ib67.yuanban.datapack.provider.PackProvider provider) {
        var result = new TreeMap<String, Pack>();
        provider.discover(SETTINGS, pack -> result.put(pack.id(), pack));
        return result;
    }

    @Test
    void folderProviderFindsDirectoryAndZipPacks() throws IOException {
        var folder = tempDir.resolve("datapacks");
        TestPack.create().writeDirectory(folder.resolve("dir_pack"));
        TestPack.create().writeZip(folder.resolve("zip_pack.zip"));
        TestPack.withoutMcmeta().file("data/x/a.json", "{}").writeDirectory(folder.resolve("not_a_pack"));
        Files.writeString(folder.resolve("readme.txt"), "hello");

        var packs = discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false));
        assertEquals(Set.of("file/dir_pack", "file/zip_pack.zip"), packs.keySet());
        var directory = packs.get("file/dir_pack");
        assertInstanceOf(PackRoot.Directory.class, directory.root());
        assertEquals(PackOrigin.WORLD, directory.origin());
        assertTrue(directory.knownPack().isEmpty());
        assertInstanceOf(PackRoot.Zip.class, packs.get("file/zip_pack.zip").root());
    }

    @Test
    void folderProviderCreatesMissingDirectory() {
        var folder = tempDir.resolve("missing").resolve("datapacks");
        assertTrue(discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false)).isEmpty());
        assertTrue(Files.isDirectory(folder));
    }

    @Test
    void zipWithoutMcmetaIsAnError() {
        var folder = tempDir.resolve("datapacks");
        TestPack.withoutMcmeta().file("data/x/a.json", "{}").writeZip(folder.resolve("broken.zip"));
        assertThrows(PackStructureException.class, () -> discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false)));
    }

    @Test
    void invalidMcmetaReportsPackId() {
        var folder = tempDir.resolve("datapacks");
        TestPack.withMcmeta("{\"pack\": {\"description\": \"d\", \"pack_format\": 48}}").writeDirectory(folder.resolve("old"));
        var exception = assertThrows(PackMetadataException.class,
                () -> discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false)));
        assertEquals("file/old", exception.getPackId());
        assertEquals("pack.min_format", exception.getField());

        TestPack.withMcmeta("{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}} trailing")
                .writeDirectory(folder.resolve("old"));
        assertEquals("", assertThrows(PackMetadataException.class,
                () -> discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false))).getField());
    }

    @Test
    void symlinksMustBeAllowed() throws IOException {
        var folder = tempDir.resolve("datapacks");
        Files.createDirectories(folder);
        var target = TestPack.create().writeDirectory(tempDir.resolve("elsewhere").resolve("linked"));
        Files.createSymbolicLink(folder.resolve("linked"), target);

        assertThrows(PackStructureException.class, () -> discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false)));
        var packs = discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> path.equals(target)));
        assertEquals(Set.of("file/linked"), packs.keySet());
    }

    @Test
    void symlinksInsidePacksMustBeAllowed() throws IOException {
        var folder = tempDir.resolve("datapacks");
        var pack = TestPack.create().writeDirectory(folder.resolve("inner"));
        var outside = Files.writeString(tempDir.resolve("outside.json"), "{}");
        Files.createDirectories(pack.resolve("data").resolve("x"));
        Files.createSymbolicLink(pack.resolve("data").resolve("x").resolve("link.json"), outside);
        assertThrows(PackStructureException.class, () -> discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false)));
    }

    @Test
    void builtInProviderGivesVanillaAndFeaturePacks() {
        var world = new TestWorld(tempDir).featurePack("trade_rebalance", "minecraft:trade_rebalance");
        TestPack.create().writeZip(world.vanillaRoot.resolve("data/minecraft/datapacks/zipped.zip"));
        var packs = discover(new BuiltInPackProvider(world.vanillaRoot, "26.2"));
        assertEquals(Set.of("vanilla", "trade_rebalance", "zipped"), packs.keySet());

        var vanilla = packs.get("vanilla");
        assertEquals(PackOrigin.BUILT_IN, vanilla.origin());
        assertEquals(new KnownPack("minecraft", "core", "26.2"), vanilla.knownPack().orElseThrow());
        assertEquals(Set.of(Key.key("minecraft:vanilla")), vanilla.metadata().features());
        assertEquals(PackFormat.of(107, 1), vanilla.metadata().formats().min());
        assertEquals(PackFormat.of(107, PackFormat.ANY_MINOR), vanilla.metadata().formats().max());
        assertEquals("dataPack.vanilla.description", vanilla.metadata().description().getAsJsonObject().get("translate").getAsString());

        var trade = packs.get("trade_rebalance");
        assertEquals(PackOrigin.FEATURE, trade.origin());
        assertEquals(new KnownPack("minecraft", "trade_rebalance", "26.2"), trade.knownPack().orElseThrow());
        assertEquals(Set.of(Key.key("minecraft:trade_rebalance")), trade.metadata().features());
    }

    @Test
    void builtInZipInsideNonDefaultFileSystemIsAnError() throws IOException {
        var jar = tempDir.resolve("server.jar");
        TestPack.withoutMcmeta()
                .file("data/minecraft/recipe/a.json", "{}")
                .file("data/minecraft/datapacks/nested.zip", "not really a zip")
                .writeZip(jar);
        try (var fileSystem = java.nio.file.FileSystems.newFileSystem(jar)) {
            var root = fileSystem.getRootDirectories().iterator().next();
            assertThrows(PackStructureException.class, () -> discover(new BuiltInPackProvider(root, "26.2")));
        }
    }

    @Test
    void duplicateIdsAreAnError() {
        var first = new TestWorld(tempDir.resolve("a"));
        var second = new TestWorld(tempDir.resolve("b"));
        var loader = first.loader().provider(new BuiltInPackProvider(second.vanillaRoot, "26.2")).build();
        assertThrows(PackStructureException.class, () -> loader.start(TestWorld.createWorld()));
    }

    @Test
    void embeddedRootIsUsedForJarContents() throws IOException {
        var jar = tempDir.resolve("server.jar");
        TestPack.withoutMcmeta().file("data/minecraft/recipe/a.json", "{}").writeZip(jar);
        try (var fileSystem = java.nio.file.FileSystems.newFileSystem(jar)) {
            var root = fileSystem.getRootDirectories().iterator().next();
            var packs = new ArrayList<Pack>();
            new BuiltInPackProvider(root, "26.2").discover(SETTINGS, packs::add);
            assertEquals(List.of("vanilla"), packs.stream().map(Pack::id).toList());
            assertInstanceOf(PackRoot.Embedded.class, packs.getFirst().root());
        }
    }
}
