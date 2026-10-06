package io.ib67.yuanban.datapack.selection;

import io.ib67.yuanban.datapack.SelectionException;
import io.ib67.yuanban.datapack.internal.PackSelector;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

/**
 * 为 {@code /datapack enable|disable} 计算新的启用列表，校验规则来自原版 {@code DataPackCommand}。
 * 返回的列表交给 {@code DataPackSession.reload(new ReloadRequest.Select(...))}。
 */
public final class SelectionEditor {
    private final SortedMap<String, Pack> available;
    private final List<Pack> enabled;
    private final Set<Key> enabledFeatures;

    @ApiStatus.Internal
    public SelectionEditor(SortedMap<String, Pack> available, List<Pack> enabled, Set<Key> enabledFeatures) {
        this.available = available;
        this.enabled = List.copyOf(enabled);
        this.enabledFeatures = Set.copyOf(enabledFeatures);
    }

    public List<String> enable(String id, Placement placement) {
        var pack = requireAvailable(id);
        if (enabled.contains(pack)) {
            throw new SelectionException("数据包 " + id + " 已经启用");
        }
        if (!enabledFeatures.containsAll(pack.metadata().features())) {
            throw new SelectionException("数据包 " + id + " 所需的特性 " + PackSelector.missingFeatures(enabledFeatures, pack) + " 没有启用");
        }
        var list = new ArrayList<>(enabled);
        switch (placement) {
            case Placement.DefaultPosition() -> PackSelector.insertAtDefaultPosition(list, pack);
            case Placement.First() -> list.addFirst(pack);
            case Placement.Last() -> list.addLast(pack);
            case Placement.Before(String existing) -> list.add(indexOfEnabled(existing), pack);
            case Placement.After(String existing) -> list.add(indexOfEnabled(existing) + 1, pack);
        }
        return ids(list);
    }

    public List<String> disable(String id) {
        var pack = requireAvailable(id);
        if (!enabled.contains(pack)) {
            throw new SelectionException("数据包 " + id + " 没有启用");
        }
        if (pack.origin().equals(PackOrigin.FEATURE) && !pack.metadata().features().isEmpty()) {
            throw new SelectionException("数据包 " + id + " 是特性数据包，不能禁用");
        }
        if (pack.selectionConfig().required()) {
            throw new SelectionException("数据包 " + id + " 必须启用，不能禁用");
        }
        var list = new ArrayList<>(enabled);
        list.remove(pack);
        return ids(list);
    }

    private Pack requireAvailable(String id) {
        var pack = available.get(id);
        if (pack == null) {
            throw new SelectionException("数据包 " + id + " 不存在");
        }
        return pack;
    }

    private int indexOfEnabled(String id) {
        var pack = requireAvailable(id);
        var index = enabled.indexOf(pack);
        if (index < 0) {
            throw new SelectionException("数据包 " + id + " 没有启用");
        }
        return index;
    }

    private static List<String> ids(List<Pack> packs) {
        return packs.stream().map(Pack::id).toList();
    }
}
