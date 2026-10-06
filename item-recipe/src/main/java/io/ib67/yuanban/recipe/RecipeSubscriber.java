package io.ib67.yuanban.recipe;

import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.PrepareContext;
import io.ib67.yuanban.datapack.subscribe.Subscriber;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import io.ib67.yuanban.itemtag.ItemTagSubscriber;
import io.ib67.yuanban.recipe.definition.RecipeDefinition;
import io.ib67.yuanban.recipe.internal.MinestomRecipe;
import io.ib67.yuanban.recipe.internal.MinestomWorkarounds;
import io.ib67.yuanban.recipe.internal.RecipeCodecs;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 读取数据包中的配方，按本次加载的物品标签解码，提交阶段注册到 Minestom 的 {@code RecipeManager}，
 * 并给在线玩家重新发送配方数据和配方书。
 */
public final class RecipeSubscriber implements Subscriber<RecipeSet> {
    public static final SubscriberKey<RecipeSet> KEY = SubscriberKey.of("yuanban:recipes");
    public static final Topic.Single TOPIC = Topic.json("recipe");

    private final ServerProcess process;
    private List<MinestomRecipe> registered = List.of();
    private volatile RecipeSet current = RecipeSet.EMPTY;

    public RecipeSubscriber(ServerProcess process) {
        this.process = Objects.requireNonNull(process, "process");
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

    @Override
    public RecipeSet prepare(PrepareContext context) throws IOException {
        var codecs = new RecipeCodecs(context.dependency(ItemTagSubscriber.KEY), process);
        var recipes = new TreeMap<Key, RecipeDefinition>();
        var failures = new ArrayList<String>();
        for (var entry : context.contents(TOPIC).files().entrySet()) {
            var resource = entry.getValue();
            var decoded = codecs.decode(resource.readJson());
            decoded.error().ifPresent(error -> failures.add("Couldn't parse data file '" + entry.getKey().asString()
                    + "' from " + resource + ": " + error.message()));
            decoded.result().ifPresent(recipe -> recipes.put(entry.getKey(), recipe));
        }
        if (!failures.isEmpty()) {
            throw new RecipeException(String.join("\n", failures));
        }
        return new RecipeSet(recipes);
    }

    @Override
    public void apply(RecipeSet prepared) {
        var manager = process.recipe();
        for (var recipe : registered) {
            manager.removeRecipe(recipe);
        }
        var recipes = new ArrayList<MinestomRecipe>(prepared.recipes().size());
        prepared.recipes().forEach((id, definition) -> recipes.add(MinestomRecipe.of(id, definition)));
        for (var recipe : recipes) {
            manager.addRecipe(recipe);
        }
        registered = List.copyOf(recipes);
        MinestomWorkarounds.invalidateDeclareRecipes(manager);
        for (var player : process.connection().getOnlinePlayers()) {
            player.refreshRecipes();
        }
        current = prepared;
    }
}
