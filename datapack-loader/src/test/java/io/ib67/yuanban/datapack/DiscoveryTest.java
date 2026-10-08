package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.pack.FormatRange;
import io.ib67.yuanban.datapack.pack.KnownPack;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackCompatibility;
import io.ib67.yuanban.datapack.pack.PackFormat;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.pack.PackPosition;
import io.ib67.yuanban.datapack.pack.PackRoot;
import io.ib67.yuanban.datapack.pack.PackSelectionConfig;
import io.ib67.yuanban.datapack.provider.BuiltInPackProvider;
import io.ib67.yuanban.datapack.provider.FolderPackProvider;
import io.ib67.yuanban.datapack.provider.ZipPackProvider;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;

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
    void packWithOnlyLegacyPackFormatIsTooOld() {
        var folder = tempDir.resolve("datapacks");
        TestPack.withMcmeta("{\"pack\": {\"description\": \"d\", \"pack_format\": 48}}").writeDirectory(folder.resolve("old"));
        var pack = discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false)).get("file/old");
        assertEquals(new FormatRange(PackFormat.of(48), PackFormat.of(48)), pack.metadata().formats());
        assertEquals(PackCompatibility.TOO_OLD, pack.compatibility());
    }

    @Test
    void invalidMcmetaReportsPackId() {
        var folder = tempDir.resolve("datapacks");
        TestPack.withMcmeta("{\"pack\": {\"description\": \"d\", \"pack_format\": 107}}").writeDirectory(folder.resolve("new"));
        var exception = assertThrows(PackMetadataException.class,
                () -> discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false)));
        assertEquals("file/new", exception.getPackId());
        assertEquals("数据包 file/new 的 pack.mcmeta 不合法：Pack declares support for version newer than 81,"
                + " but is missing mandatory fields min_format and max_format", exception.getMessage());

        TestPack.withMcmeta("{\"pack\": {\"description\": \"d\", \"min_format\": 107, \"max_format\": 107}} trailing")
                .writeDirectory(folder.resolve("new"));
        var trailing = assertThrows(PackMetadataException.class,
                () -> discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false)));
        assertTrue(trailing.getMessage().startsWith("数据包 file/new 的 pack.mcmeta 不合法：JSON 语法错误："), trailing.getMessage());

        TestPack.withMcmeta("[]").writeDirectory(folder.resolve("new"));
        assertEquals("数据包 file/new 的 pack.mcmeta 不合法：JSON 语法错误：Expected a com.google.gson.JsonObject"
                        + " but was com.google.gson.JsonArray; at path $",
                assertThrows(PackMetadataException.class,
                        () -> discover(new FolderPackProvider(folder, PackOrigin.WORLD, path -> false))).getMessage());
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

    /**
     * 新建世界，返回 {@code recipe} topic 中每个资源 ID 对应的文件内容。
     */
    private static Map<String, String> recipes(DataPackLoader.Builder builder) {
        var topic = Topic.json("recipe");
        var subscriber = new TestSubscriber<Map<String, String>>(context -> {
            var result = new TreeMap<String, String>();
            for (var entry : context.contents(topic).files().entrySet()) {
                result.put(entry.getKey().asString(), new String(entry.getValue().readAllBytes(), StandardCharsets.UTF_8));
            }
            return result;
        });
        try (var ignored = builder
                .subscribe(Subscription.builder(SubscriberKey.<Map<String, String>>of("test:recipes"), subscriber)
                        .lifecycle(Lifecycle.RELOADABLE)
                        .topic(topic)
                        .build())
                .build()
                .start(TestWorld.createWorld())) {
            return subscriber.last();
        }
    }

    @Test
    void vanillaOnlyReadsMinecraftNamespace() throws IOException {
        var world = new TestWorld(tempDir)
                .vanilla("minecraft", "recipe/a.json", "vanilla a")
                .vanilla("Not_A_Namespace", "recipe/b.json", "ignored");
        Files.writeString(world.vanillaRoot.resolve("data").resolve(".mcassetsroot"), "");
        assertEquals(Map.of("minecraft:a", "vanilla a"), recipes(world.loader()));
    }

    @Test
    void vanillaJarWithMarkerFileLoads() throws IOException {
        var jar = tempDir.resolve("server.jar");
        TestPack.withoutMcmeta()
                .file("data/.mcassetsroot", "")
                .file("data/minecraft/recipe/a.json", "jar a")
                .writeZip(jar);
        try (var fileSystem = java.nio.file.FileSystems.newFileSystem(jar)) {
            var root = fileSystem.getRootDirectories().iterator().next();
            var builder = DataPackLoader.builder(SETTINGS)
                    .provider(new BuiltInPackProvider(root, "26.2"))
                    .executor(ForkJoinPool.commonPool());
            assertEquals(Map.of("minecraft:a", "jar a"), recipes(builder));
        }
    }

    @Test
    void nonPackDirectoriesWithSymlinksAreSkipped() throws IOException {
        var world = new TestWorld(tempDir);
        var notes = Files.createDirectories(world.datapacks.resolve("notes"));
        Files.createSymbolicLink(notes.resolve("link.txt"), Files.writeString(tempDir.resolve("target.txt"), "x"));
        var packs = discover(new FolderPackProvider(world.datapacks, PackOrigin.WORLD, path -> false));
        assertTrue(packs.isEmpty());
    }

    @Test
    void allowedSymlinksInsidePacksAreFollowed() throws IOException {
        var world = new TestWorld(tempDir);
        var pack = TestPack.create().data("minecraft", "recipe/own.json", "own").writeDirectory(world.datapacks.resolve("p"));
        // 整个命名空间目录是链接
        var linkedNamespace = TestPack.withoutMcmeta().file("recipe/a.json", "linked namespace a")
                .writeDirectory(tempDir.resolve("outside_namespace"));
        Files.createSymbolicLink(pack.resolve("data").resolve("example"), linkedNamespace);
        // topic 路径中间的一级是链接
        var linkedDirectory = TestPack.withoutMcmeta().file("b.json", "linked directory b")
                .writeDirectory(tempDir.resolve("outside_directory"));
        Files.createSymbolicLink(pack.resolve("data").resolve("minecraft").resolve("recipe").resolve("nested"), linkedDirectory);
        // 文件本身是链接
        Files.createSymbolicLink(pack.resolve("data").resolve("minecraft").resolve("recipe").resolve("c.json"),
                Files.writeString(tempDir.resolve("c.json"), "linked file c"));

        assertEquals(Map.of(
                "example:a", "linked namespace a",
                "minecraft:c", "linked file c",
                "minecraft:nested/b", "linked directory b",
                "minecraft:own", "own"), recipes(world.loader(path -> true)));
    }

    @Test
    void brokenSymlinksInsidePacksFail() throws IOException {
        var world = new TestWorld(tempDir);
        var pack = TestPack.create().data("minecraft", "recipe/own.json", "own").writeDirectory(world.datapacks.resolve("p"));
        Files.createSymbolicLink(pack.resolve("data").resolve("minecraft").resolve("recipe").resolve("gone.json"),
                tempDir.resolve("does_not_exist.json"));
        var exception = assertThrows(LoadFailedException.class, () -> recipes(world.loader(path -> true)));
        assertInstanceOf(PackStructureException.class, exception.getCause());
    }

    @Test
    void zipProviderLoadsASingleZip() {
        var world = new TestWorld(tempDir).vanilla("minecraft", "recipe/a.json", "vanilla a");
        var zip = TestPack.create().data("minecraft", "recipe/a.json", "extra a").writeZip(tempDir.resolve("extra.jar"));
        var config = new PackSelectionConfig(true, PackPosition.TOP, true);
        var builder = world.loader().provider(new ZipPackProvider(zip, "extra", PackOrigin.BUILT_IN, config,
                new KnownPack("example", "extra", "1")));
        assertEquals(Map.of("minecraft:a", "extra a"), recipes(builder));

        var packs = discover(new ZipPackProvider(zip, "extra", PackOrigin.BUILT_IN, config, null));
        var pack = packs.get("extra");
        assertInstanceOf(PackRoot.Zip.class, pack.root());
        assertEquals(config, pack.selectionConfig());
        assertTrue(pack.knownPack().isEmpty());
    }

    @Test
    void zipProviderRejectsInvalidFiles() throws IOException {
        var config = new PackSelectionConfig(false, PackPosition.TOP, false);
        assertThrows(PackStructureException.class, () -> discover(
                new ZipPackProvider(tempDir.resolve("missing.zip"), "x", PackOrigin.WORLD, config, null)));
        assertThrows(PackStructureException.class, () -> discover(
                new ZipPackProvider(Files.createDirectories(tempDir.resolve("dir.zip")), "x", PackOrigin.WORLD, config, null)));
        var noMcmeta = TestPack.withoutMcmeta().file("data/x/recipe/a.json", "{}").writeZip(tempDir.resolve("no_mcmeta.zip"));
        assertThrows(PackStructureException.class, () -> discover(
                new ZipPackProvider(noMcmeta, "x", PackOrigin.WORLD, config, null)));

        var outer = TestPack.withoutMcmeta().file("inner.zip", "not opened").writeZip(tempDir.resolve("outer.zip"));
        try (var fileSystem = java.nio.file.FileSystems.newFileSystem(outer)) {
            var inner = fileSystem.getPath("/inner.zip");
            assertThrows(PackStructureException.class, () -> discover(new ZipPackProvider(inner, "x", PackOrigin.WORLD, config, null)));
        }
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
