package io.ib67.yuanban.datapack.internal;

import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.SelectionException;
import io.ib67.yuanban.datapack.pack.Pack;
import io.ib67.yuanban.datapack.pack.PackOrigin;
import io.ib67.yuanban.datapack.pack.PackPosition;
import io.ib67.yuanban.datapack.selection.DataPackConfig;
import io.ib67.yuanban.datapack.selection.Selection;
import io.ib67.yuanban.datapack.selection.SelectionMode;
import io.ib67.yuanban.datapack.selection.SelectionReport;
import io.ib67.yuanban.datapack.selection.SelectionRequest;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

/**
 * 启用列表的选择算法，和原版 {@code MinecraftServer.configurePackRepository} 相同。
 */
@ApiStatus.Internal
public final class PackSelector {
    private PackSelector() {
    }

    /**
     * @param available 按 ID 排序的全部可用数据包
     */
    public static Selection select(SortedMap<String, Pack> available, SelectionRequest request, LoaderSettings settings) {
        var createWorld = request.mode() == SelectionMode.CREATE_WORLD;
        Set<Key> forced = createWorld ? Set.of() : request.worldFeatures();
        Set<Key> allowed = createWorld ? settings.featureFlags() : request.worldFeatures();
        var config = request.config();

        var missing = new ArrayList<String>();
        var autoEnabled = new ArrayList<String>();
        var skippedForFeatures = new ArrayList<String>();
        var removedForFeatures = new ArrayList<String>();
        var forcedFeaturePacks = new ArrayList<String>();

        var ids = new LinkedHashSet<String>();
        boolean disableInactive;
        if (request.safeMode()) {
            ids.add(requireDefaultPack(available, settings));
            disableInactive = false;
        } else {
            for (var id : config.enabled()) {
                if (available.containsKey(id)) {
                    ids.add(id);
                } else {
                    missing.add(id);
                }
            }
            var disabled = Set.copyOf(config.disabled());
            for (var pack : available.values()) {
                if (disabled.contains(pack.id())) {
                    continue;
                }
                var features = pack.metadata().features();
                var selected = ids.contains(pack.id());
                if (!selected && pack.origin().autoEnable()) {
                    if (allowed.containsAll(features)) {
                        ids.add(pack.id());
                        autoEnabled.add(pack.id());
                    } else {
                        skippedForFeatures.add(pack.id());
                    }
                }
                if (selected && !allowed.containsAll(features)) {
                    ids.remove(pack.id());
                    removedForFeatures.add(pack.id());
                }
            }
            if (ids.isEmpty()) {
                ids.add(requireDefaultPack(available, settings));
            }
            disableInactive = true;
        }

        var packs = rebuild(ids, available);

        var missingFeatures = new HashSet<>(forced);
        missingFeatures.removeAll(requestedFeatures(packs));
        if (!missingFeatures.isEmpty()) {
            var forcedIds = new LinkedHashSet<String>();
            packs.forEach(pack -> forcedIds.add(pack.id()));
            for (var pack : available.values()) {
                if (missingFeatures.isEmpty()) {
                    break;
                }
                var features = pack.metadata().features();
                if (pack.origin().equals(PackOrigin.FEATURE)
                        && !features.isEmpty()
                        && features.stream().anyMatch(missingFeatures::contains)
                        && forced.containsAll(features)) {
                    if (!forcedIds.add(pack.id())) {
                        throw new IllegalStateException("tried to force feature pack " + pack.id() + ", but it was already enabled");
                    }
                    forcedFeaturePacks.add(pack.id());
                    missingFeatures.removeAll(features);
                }
            }
            packs = rebuild(forcedIds, available);
        }

        var enabledFeatures = new HashSet<>(requestedFeatures(packs));
        enabledFeatures.addAll(forced);
        var enabledIds = packs.stream().map(Pack::id).toList();
        var disabledIds = disableInactive ? inactive(available, enabledIds) : List.<String>of();
        return new Selection(packs, enabledFeatures, new DataPackConfig(enabledIds, disabledIds),
                new SelectionReport(missing, autoEnabled, skippedForFeatures, removedForFeatures, forcedFeaturePacks));
    }

    /**
     * {@code ReloadRequest.Select} 的选择：不重新发现数据包，按给定顺序加载。
     */
    public static Selection explicit(SortedMap<String, Pack> available, List<String> ids, Set<Key> worldFeatures) {
        var seen = new HashSet<String>();
        var packs = new ArrayList<Pack>(ids.size());
        for (var id : ids) {
            var pack = available.get(id);
            if (pack == null) {
                throw new SelectionException("数据包 " + id + " 不存在");
            }
            if (!seen.add(id)) {
                throw new SelectionException("数据包 " + id + " 在列表中重复出现");
            }
            if (!worldFeatures.containsAll(pack.metadata().features())) {
                throw new SelectionException("数据包 " + id + " 所需的特性 " + missingFeatures(worldFeatures, pack) + " 没有启用");
            }
            packs.add(pack);
        }
        for (var pack : available.values()) {
            if (pack.selectionConfig().required() && !seen.contains(pack.id())) {
                throw new SelectionException("数据包 " + pack.id() + " 必须启用");
            }
        }
        return new Selection(packs, worldFeatures, new DataPackConfig(ids, inactive(available, ids)), SelectionReport.EMPTY);
    }

    /**
     * 按数据包的默认位置插入，跳过列表这一端连续的固定位置数据包，和原版 {@code Pack.Position.insert} 相同。
     */
    public static void insertAtDefaultPosition(List<Pack> list, Pack pack) {
        var position = pack.selectionConfig().defaultPosition();
        if (position == PackPosition.BOTTOM) {
            int index = 0;
            while (index < list.size() && isFixedAt(list.get(index), position)) {
                index++;
            }
            list.add(index, pack);
        } else {
            int index = list.size() - 1;
            while (index >= 0 && isFixedAt(list.get(index), position)) {
                index--;
            }
            list.add(index + 1, pack);
        }
    }

    public static Set<Key> missingFeatures(Set<Key> enabled, Pack pack) {
        var result = new HashSet<>(pack.metadata().features());
        result.removeAll(enabled);
        return result;
    }

    private static boolean isFixedAt(Pack pack, PackPosition position) {
        return pack.selectionConfig().fixedPosition() && pack.selectionConfig().defaultPosition() == position;
    }

    private static List<Pack> rebuild(Collection<String> ids, SortedMap<String, Pack> available) {
        var list = new ArrayList<Pack>(ids.size());
        for (var id : ids) {
            list.add(available.get(id));
        }
        for (var pack : available.values()) {
            if (pack.selectionConfig().required() && !list.contains(pack)) {
                insertAtDefaultPosition(list, pack);
            }
        }
        return list;
    }

    private static Set<Key> requestedFeatures(List<Pack> packs) {
        var result = new HashSet<Key>();
        packs.forEach(pack -> result.addAll(pack.metadata().features()));
        return result;
    }

    private static List<String> inactive(SortedMap<String, Pack> available, Collection<String> enabled) {
        var enabledSet = Set.copyOf(enabled);
        return available.keySet().stream().filter(id -> !enabledSet.contains(id)).toList();
    }

    private static String requireDefaultPack(SortedMap<String, Pack> available, LoaderSettings settings) {
        var id = settings.defaultPackId();
        if (!available.containsKey(id)) {
            throw new SelectionException("默认数据包 " + id + " 不可用");
        }
        return id;
    }
}
