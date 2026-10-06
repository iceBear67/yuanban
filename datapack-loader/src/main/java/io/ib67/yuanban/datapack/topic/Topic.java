package io.ib67.yuanban.datapack.topic;

import io.ib67.yuanban.datapack.tag.TagDeclarations;

/**
 * 命名空间目录下的一个子目录加一个扩展名。目录可以有多级，例如 {@code worldgen/biome}。
 * 合法性在 {@code DataPackLoader.Builder.build()} 中检查。
 *
 * @param <C> 订阅者读到的内容类型
 */
public sealed interface Topic<C> permits Topic.Single, Topic.Layered, Topic.Tags {
    String JSON = ".json";

    String directory();

    String extension();

    /**
     * 覆盖模式：同一个资源 ID 只保留优先级最高的文件。
     */
    record Single(String directory, String extension) implements Topic<TopicFiles> {
    }

    /**
     * 分层模式：同一个资源 ID 的全部版本按优先级从低到高排列。
     */
    record Layered(String directory, String extension) implements Topic<TopicLayers> {
    }

    /**
     * 标签：目录 {@code tags/<registry>}，扩展名 {@code .json}，按原版规则合并。
     *
     * @param registry 注册表 ID 的路径部分，例如 {@code block}、{@code worldgen/biome}
     */
    record Tags(String registry) implements Topic<TagDeclarations> {
        @Override
        public String directory() {
            return "tags/" + registry;
        }

        @Override
        public String extension() {
            return JSON;
        }
    }

    static Single single(String directory, String extension) {
        return new Single(directory, extension);
    }

    static Single json(String directory) {
        return new Single(directory, JSON);
    }

    static Layered layered(String directory, String extension) {
        return new Layered(directory, extension);
    }

    static Tags tags(String registry) {
        return new Tags(registry);
    }
}
