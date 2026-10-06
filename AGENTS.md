你目前处在 yuanban 的工作区。Minestom 是一个开源的 Minecraft: Java Edition 服务端实现，yuanban 是一个在 Minestom 上实现游戏特性的项目。

无特别指定的情况下，本项目使用 Java 25 + Lombok + Gradle。Maven 仓库，通用依赖有关的全局配置统一在根目录的 `build.gradle.kts` 设置。本项目还大量使用 JetBrains 提供的 `java-annotations` 用于标注 API.

你在一个沙盒内工作，跟宿主的文件系统有一定程度的隔离，因此尽可能使用 IDEA MCP 来进行搜索符号，重构等工作。
