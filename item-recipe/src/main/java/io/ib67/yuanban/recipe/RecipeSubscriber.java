package io.ib67.yuanban.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.ib67.yuanban.crafting.recipe.Recipe;
import io.ib67.yuanban.crafting.recipe.RecipeManager;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Resource;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.recipe.internal.RecipeCodecs;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 读取数据包中的配方，按本次加载的物品标签解码，提交阶段替换 {@code crafting} 模块 {@link RecipeManager} 中的全部配方。
 */
public final class RecipeSubscriber implements Subscriber<RecipeSet> {
    public static final SubscriberKey<RecipeSet> KEY = SubscriberKey.of("yuanban:recipes");
    public static final Topic.Single TOPIC = Topic.json("recipe");

    private final ServerProcess process;
    private final RecipeManager recipes;
    private volatile RecipeSet current = RecipeSet.EMPTY;

    /**
     * @param process 解码时按它的注册表及其标签检查引用，并用它的注册表解码物品组件和内联纹饰样式；物品标签换成本次加载的结果
     * @param recipes 提交阶段把配方交给它，通常是 {@code Crafting.recipes()}
     */
    public RecipeSubscriber(ServerProcess process, RecipeManager recipes) {
        this.process = Objects.requireNonNull(process, "process");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
    }

    /**
     * 生命周期为 {@link Lifecycle#RELOADABLE}、读取 {@link #TOPIC}、依赖 {@link ItemTagSubscriber#KEY} 的订阅。
     * 宿主同时需要注册 {@link ItemTagSubscriber#subscription(ServerProcess)}。
     */
    public Subscription<RecipeSet> subscription() {
        return Subscription.builder(KEY, this)
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(TOPIC)
                .after(ItemTagSubscriber.KEY)
                .build();
    }

    /**
     * 最近一次提交的配方，提交之前是 {@link RecipeSet#EMPTY}。
     */
    public RecipeSet current() {
        return current;
    }

    /**
     * 按配方 ID 的顺序解码全部文件，JSON 语法错误和解码错误都按文件记入错误汇总。
     */
    @Override
    public RecipeSet prepare(PrepareContext context) throws IOException {
        var ops = RecipeCodecs.registryOps(process, context.dependency(ItemTagSubscriber.KEY));
        var decoded = new TreeMap<Key, Recipe<?>>();
        var failures = new ArrayList<String>();
        for (var entry : context.contents(TOPIC).files().entrySet()) {
            var resource = entry.getValue();
            JsonElement json;
            try {
                json = resource.readJson();
            } catch (JsonParseException e) {
                failures.add(failure(entry.getKey(), resource, e.getMessage()));
                continue;
            }
            var result = RecipeCodecs.CODEC.parse(ops, json);
            result.error().ifPresent(error -> failures.add(failure(entry.getKey(), resource, error.message())));
            result.result().ifPresent(recipe -> decoded.put(entry.getKey(), recipe));
        }
        if (!failures.isEmpty()) {
            throw new RecipeException(String.join("\n", failures));
        }
        return new RecipeSet(decoded);
    }

    private static String failure(Key id, Resource resource, String message) {
        return "Couldn't parse data file '" + id.asString() + "' from " + resource + ": " + message;
    }

    /**
     * {@code item-tag} 先于本模块提交，{@link RecipeManager} 网络编码标签原料时 Minestom 注册表中已经是新标签。
     */
    @Override
    public void apply(RecipeSet prepared) {
        recipes.replaceRecipes(prepared.recipes());
        current = prepared;
    }
}
