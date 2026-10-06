package io.ib67.yuanban.datapack.pack;

import java.nio.file.Path;

/**
 * 打开数据包内容的方式。
 */
public sealed interface PackRoot {
    /**
     * 默认文件系统中的目录。
     */
    record Directory(Path path) implements PackRoot {
    }

    /**
     * 默认文件系统中的 zip 文件，打开的时候新建 zip 文件系统，由所在批次负责关闭。
     */
    record Zip(Path file) implements PackRoot {
    }

    /**
     * 宿主已经打开的文件系统中的目录，例如原版 jar 内部。文件系统归宿主所有。
     */
    record Embedded(Path path) implements PackRoot {
    }
}
