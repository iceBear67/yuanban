package io.ib67.yuanban.datapack.provider;

import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.PackStructureException;
import io.ib67.yuanban.datapack.internal.PackMetadataReader;
import io.ib67.yuanban.datapack.pack.KnownPack;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.pack.PackRoot;
import io.ib67.yuanban.datapack.pack.PackSelectionConfig;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 给出单个 zip 数据包，{@code pack.mcmeta} 位于 zip 的根目录。zip 文件必须位于默认文件系统中，文件名不限。
 */
public final class ZipPackProvider implements PackProvider {
    private final Path zipFile;
    private final String id;
    private final PackOrigin origin;
    private final PackSelectionConfig selectionConfig;
    private final @Nullable KnownPack knownPack;

    /**
     * @param zipFile         zip 文件
     * @param id              数据包 ID
     * @param origin          数据包的类别
     * @param selectionConfig 数据包在启用列表中的位置规则
     * @param knownPack       已知数据包信息，没有的时候传入 {@code null}
     */
    public ZipPackProvider(Path zipFile, String id, PackOrigin origin, PackSelectionConfig selectionConfig, @Nullable KnownPack knownPack) {
        this.zipFile = Objects.requireNonNull(zipFile, "zipFile");
        this.id = Objects.requireNonNull(id, "id");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.selectionConfig = Objects.requireNonNull(selectionConfig, "selectionConfig");
        this.knownPack = knownPack;
    }

    @Override
    public void discover(LoaderSettings settings, Consumer<Pack> output) {
        if (zipFile.getFileSystem() != FileSystems.getDefault()) {
            throw new PackStructureException("无法打开位于非默认文件系统中的 zip 数据包 " + zipFile.toUri());
        }
        if (!Files.isRegularFile(zipFile)) {
            throw new PackStructureException("数据包 " + id + " 的 zip 文件 " + zipFile + " 不存在或者不是普通文件");
        }
        var root = new PackRoot.Zip(zipFile);
        var metadata = PackMetadataReader.read(id, root, settings);
        output.accept(Pack.create(settings, id, origin, knownPack, selectionConfig, metadata, root));
    }
}
