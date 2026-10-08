package io.ib67.yuanban.codec;

import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponent;
import net.minestom.server.entity.VillagerType;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.block.BlockEntityType;
import net.minestom.server.registry.Registries;
import net.minestom.server.world.attribute.EnvironmentAttribute;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 解码时可见的注册表，对应原版 {@code RegistryOps.RegistryInfoLookup}。
 * <p>
 * {@link #builder(Registries)} 缺省提供 Minestom 进程的全部注册表及其标签；订阅者用
 * {@link Builder#put(RegistryId, RegistryGetter)} 替换某个注册表，例如物品标签换成同一次加载的结果。
 */
public final class RegistryLookup {
    private final Registries registries;
    private final Map<Key, RegistryGetter<?>> getters;

    private RegistryLookup(Registries registries, Map<Key, RegistryGetter<?>> getters) {
        this.registries = registries;
        this.getters = Map.copyOf(getters);
    }

    /**
     * Minestom 的注册表，供 {@link MinestomCodecs} 适配的 Minestom 编解码器使用。
     */
    public Registries registries() {
        return registries;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<RegistryGetter<T>> lookup(RegistryId<T> registry) {
        return Optional.ofNullable((RegistryGetter<T>) getters.get(registry.key()));
    }

    /**
     * 以 {@code registries} 中的注册表为缺省内容的构造器。
     */
    public static Builder builder(Registries registries) {
        return new Builder(registries);
    }

    public static final class Builder {
        private final Registries registries;
        private final Map<Key, RegistryGetter<?>> getters = new HashMap<>();

        private Builder(Registries registries) {
            this.registries = Objects.requireNonNull(registries, "registries");
            put(RegistryIds.ITEM, RegistryGetter.of(registries.material()));
            put(RegistryIds.BLOCK, RegistryGetter.of(registries.blocks()));
            put(RegistryIds.ENTITY_TYPE, RegistryGetter.of(registries.entityType()));
            put(RegistryIds.FLUID, RegistryGetter.of(registries.fluid()));
            put(RegistryIds.POTION, RegistryGetter.of(registries.potionType()));
            put(RegistryIds.MOB_EFFECT, RegistryGetter.of(registries.potionEffect()));
            put(RegistryIds.GAME_EVENT, RegistryGetter.of(registries.gameEvent()));
            put(RegistryIds.BLOCK_ENTITY_TYPE, RegistryGetter.of(BlockEntityType.staticRegistry()));
            // Minestom 没有为下面几个内置注册表提供标签
            put(RegistryIds.ATTRIBUTE, RegistryGetter.of(id -> Attribute.fromKey(id) != null, _ -> Optional.empty()));
            put(RegistryIds.DATA_COMPONENT_TYPE, RegistryGetter.of(id -> DataComponent.fromKey(id) != null, _ -> Optional.empty()));
            put(RegistryIds.VILLAGER_TYPE, RegistryGetter.of(
                    id -> Arrays.stream(VillagerType.values()).anyMatch(type -> type.key().equals(id)), _ -> Optional.empty()));
            put(RegistryIds.ENVIRONMENT_ATTRIBUTE, RegistryGetter.of(
                    id -> EnvironmentAttribute.values().stream().anyMatch(attribute -> attribute.key().equals(id)), _ -> Optional.empty()));
            put(RegistryIds.MAP_DECORATION_TYPE, RegistryGetter.of(MapDecorationTypes.IDS, Map.of()));

            put(RegistryIds.CHAT_TYPE, RegistryGetter.of(registries.chatType()));
            put(RegistryIds.DIMENSION_TYPE, RegistryGetter.of(registries.dimensionType()));
            put(RegistryIds.BIOME, RegistryGetter.of(registries.biome()));
            put(RegistryIds.DAMAGE_TYPE, RegistryGetter.of(registries.damageType()));
            put(RegistryIds.TRIM_MATERIAL, RegistryGetter.of(registries.trimMaterial()));
            put(RegistryIds.TRIM_PATTERN, RegistryGetter.of(registries.trimPattern()));
            put(RegistryIds.BANNER_PATTERN, RegistryGetter.of(registries.bannerPattern()));
            put(RegistryIds.ENCHANTMENT, RegistryGetter.of(registries.enchantment()));
            put(RegistryIds.PAINTING_VARIANT, RegistryGetter.of(registries.paintingVariant()));
            put(RegistryIds.JUKEBOX_SONG, RegistryGetter.of(registries.jukeboxSong()));
            put(RegistryIds.INSTRUMENT, RegistryGetter.of(registries.instrument()));
            put(RegistryIds.WOLF_VARIANT, RegistryGetter.of(registries.wolfVariant()));
            put(RegistryIds.WOLF_SOUND_VARIANT, RegistryGetter.of(registries.wolfSoundVariant()));
            put(RegistryIds.CAT_VARIANT, RegistryGetter.of(registries.catVariant()));
            put(RegistryIds.CAT_SOUND_VARIANT, RegistryGetter.of(registries.catSoundVariant()));
            put(RegistryIds.CHICKEN_VARIANT, RegistryGetter.of(registries.chickenVariant()));
            put(RegistryIds.CHICKEN_SOUND_VARIANT, RegistryGetter.of(registries.chickenSoundVariant()));
            put(RegistryIds.COW_VARIANT, RegistryGetter.of(registries.cowVariant()));
            put(RegistryIds.COW_SOUND_VARIANT, RegistryGetter.of(registries.cowSoundVariant()));
            put(RegistryIds.FROG_VARIANT, RegistryGetter.of(registries.frogVariant()));
            put(RegistryIds.PIG_VARIANT, RegistryGetter.of(registries.pigVariant()));
            put(RegistryIds.PIG_SOUND_VARIANT, RegistryGetter.of(registries.pigSoundVariant()));
            put(RegistryIds.ZOMBIE_NAUTILUS_VARIANT, RegistryGetter.of(registries.zombieNautilusVariant()));
            put(RegistryIds.DIALOG, RegistryGetter.of(registries.dialog()));
            put(RegistryIds.TIMELINE, RegistryGetter.of(registries.timeline()));
            put(RegistryIds.WORLD_CLOCK, RegistryGetter.of(registries.worldClock()));
        }

        /**
         * 设置或替换 {@code registry} 的内容。
         */
        public <T> Builder put(RegistryId<T> registry, RegistryGetter<T> getter) {
            getters.put(registry.key(), Objects.requireNonNull(getter, "getter"));
            return this;
        }

        public RegistryLookup build() {
            return new RegistryLookup(registries, getters);
        }
    }
}
