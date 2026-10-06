package io.ib67.yuanban.datapack.provider;

import com.google.gson.JsonObject;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.PackStructureException;
import io.ib67.yuanban.datapack.internal.PackDetector;
import io.ib67.yuanban.datapack.internal.PackMetadataReader;
import io.ib67.yuanban.datapack.pack.KnownPack;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackMetadata;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.pack.PackPosition;
import io.ib67.yuanban.datapack.pack.PackRoot;
import io.ib67.yuanban.datapack.pack.PackSelectionConfig;
import io.ib67.yuanban.datapack.pack.ResourceFilter;
import net.kyori.adventure.key.Key;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 原版服务端 jar 专用，对应原版 {@code ServerPacksSource}。给出 {@code vanilla} 数据包，
 * 以及 {@code data/minecraft/datapacks/} 下的内置特性数据包。
 * 和原版 {@code exposeNamespace("minecraft")} 相同，{@code vanilla} 只读取 {@code minecraft} 命名空间，
 * jar 中 {@code data/} 下的其他条目（例如 {@code data/.mcassetsroot}）忽略。
 */
public final class BuiltInPackProvider implements PackProvider {
    public static final String VANILLA_ID = "vanilla";
    public static final String CORE_KNOWN_PACK_ID = "core";
    private static final PackSelectionConfig VANILLA_SELECTION_CONFIG = new PackSelectionConfig(false, PackPosition.BOTTOM, false);
    private static final PackSelectionConfig FEATURE_SELECTION_CONFIG = new PackSelectionConfig(false, PackPosition.TOP, false);
    private static final Key VANILLA_FEATURE = Key.key(Key.MINECRAFT_NAMESPACE, "vanilla");

    private final Path root;
    private final String versionName;

    /**
     * @param root        原版数据根目录，其中包含 {@code data/minecraft/...}，通常是宿主用 zip 文件系统打开的服务端 jar 的根目录
     * @param versionName 版本名，例如 {@code 26.2}，用于已知数据包信息
     */
    public BuiltInPackProvider(Path root, String versionName) {
        this.root = Objects.requireNonNull(root, "root");
        this.versionName = Objects.requireNonNull(versionName, "versionName");
    }

    @Override
    public void discover(LoaderSettings settings, Consumer<Pack> output) {
        if (!Files.isDirectory(root)) {
            throw new PackStructureException("原版数据根目录 " + root.toUri() + " 不存在或者不是目录");
        }
        output.accept(Pack.create(settings, VANILLA_ID, PackOrigin.BUILT_IN,
                new KnownPack(Key.MINECRAFT_NAMESPACE, CORE_KNOWN_PACK_ID, versionName),
                VANILLA_SELECTION_CONFIG, vanillaMetadata(settings), packRoot(root), Set.of(Key.MINECRAFT_NAMESPACE)));
        var featurePacks = root.resolve("data").resolve(Key.MINECRAFT_NAMESPACE).resolve("datapacks");
        if (!Files.isDirectory(featurePacks)) {
            return;
        }
        try (var entries = Files.newDirectoryStream(featurePacks)) {
            for (var entry : entries) {
                var packRoot = PackDetector.detect(entry, path -> false);
                if (packRoot == null) {
                    continue;
                }
                var id = stripZipSuffix(entry.getFileName().toString());
                var metadata = PackMetadataReader.read(id, packRoot, settings);
                output.accept(Pack.create(settings, id, PackOrigin.FEATURE,
                        new KnownPack(Key.MINECRAFT_NAMESPACE, id, versionName),
                        FEATURE_SELECTION_CONFIG, metadata, packRoot));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("无法扫描内置特性数据包目录 " + featurePacks.toUri(), e);
        }
    }

    private static PackMetadata vanillaMetadata(LoaderSettings settings) {
        var description = new JsonObject();
        description.addProperty("translate", "dataPack.vanilla.description");
        return new PackMetadata(description, settings.dataFormat().minorRange(), Set.of(VANILLA_FEATURE),
                ResourceFilter.NONE, List.of());
    }

    private static PackRoot packRoot(Path path) {
        return path.getFileSystem() == FileSystems.getDefault() ? new PackRoot.Directory(path) : new PackRoot.Embedded(path);
    }

    private static String stripZipSuffix(String name) {
        return name.endsWith(PackDetector.ZIP_SUFFIX) ? name.substring(0, name.length() - PackDetector.ZIP_SUFFIX.length()) : name;
    }
}
