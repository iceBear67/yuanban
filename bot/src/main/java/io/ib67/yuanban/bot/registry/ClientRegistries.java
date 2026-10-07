package io.ib67.yuanban.bot.registry;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.animal.ChickenSoundVariant;
import net.minestom.server.entity.metadata.animal.ChickenVariant;
import net.minestom.server.entity.metadata.animal.CowSoundVariant;
import net.minestom.server.entity.metadata.animal.CowVariant;
import net.minestom.server.entity.metadata.animal.FrogVariant;
import net.minestom.server.entity.metadata.animal.PigSoundVariant;
import net.minestom.server.entity.metadata.animal.PigVariant;
import net.minestom.server.entity.metadata.animal.ZombieNautilusVariant;
import net.minestom.server.entity.metadata.animal.tameable.CatSoundVariant;
import net.minestom.server.entity.metadata.animal.tameable.CatVariant;
import net.minestom.server.entity.metadata.animal.tameable.WolfSoundVariant;
import net.minestom.server.entity.metadata.animal.tameable.WolfVariant;
import net.minestom.server.entity.metadata.cube.SulfurCubeArchetype;
import net.minestom.server.entity.metadata.other.PaintingVariant;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.instance.block.jukebox.JukeboxSong;
import net.minestom.server.instance.block.predicate.DataComponentPredicate;
import net.minestom.server.item.armor.TrimMaterial;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.item.enchant.EntityEffect;
import net.minestom.server.item.enchant.LevelBasedValue;
import net.minestom.server.item.enchant.LocationEffect;
import net.minestom.server.item.enchant.ValueEffect;
import net.minestom.server.item.instrument.Instrument;
import net.minestom.server.message.ChatType;
import net.minestom.server.registry.BuiltinRegistries;
import net.minestom.server.registry.ClientDynamicRegistry;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.Registry;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.TagKey;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.biome.Biome;
import net.minestom.server.world.clock.WorldClock;
import net.minestom.server.world.timeline.Timeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 与服务端编号一致的注册表。静态注册表使用 {@link Registries} 接口的默认方法（Minestom 的全局静态注册表），
 * 同步注册表返回按 Registry Data 建立的实例，不经过网络同步的注册表取自 {@link VanillaReference}。
 */
public final class ClientRegistries implements Registries {
    /**
     * Minestom 有对应类型的同步注册表，按 {@code VanillaRegistries} 构造方法中的顺序排列
     */
    static final List<Spec<?>> SYNCED = List.of(
            new Spec<>(BuiltinRegistries.CHAT_TYPE, ChatType.REGISTRY_CODEC, Registries::chatType),
            new Spec<>(BuiltinRegistries.DIALOG, Dialog.REGISTRY_CODEC, Registries::dialog),
            new Spec<>(BuiltinRegistries.BIOME, Biome.REGISTRY_CODEC, Registries::biome),
            new Spec<>(BuiltinRegistries.DAMAGE_TYPE, DamageType.REGISTRY_CODEC, Registries::damageType),
            new Spec<>(BuiltinRegistries.TRIM_MATERIAL, TrimMaterial.REGISTRY_CODEC, Registries::trimMaterial),
            new Spec<>(BuiltinRegistries.TRIM_PATTERN, TrimPattern.REGISTRY_CODEC, Registries::trimPattern),
            new Spec<>(BuiltinRegistries.BANNER_PATTERN, BannerPattern.REGISTRY_CODEC, Registries::bannerPattern),
            new Spec<>(BuiltinRegistries.ENCHANTMENT, Enchantment.REGISTRY_CODEC, Registries::enchantment),
            new Spec<>(BuiltinRegistries.PAINTING_VARIANT, PaintingVariant.REGISTRY_CODEC, Registries::paintingVariant),
            new Spec<>(BuiltinRegistries.JUKEBOX_SONG, JukeboxSong.REGISTRY_CODEC, Registries::jukeboxSong),
            new Spec<>(BuiltinRegistries.INSTRUMENT, Instrument.REGISTRY_CODEC, Registries::instrument),
            new Spec<>(BuiltinRegistries.WOLF_VARIANT, WolfVariant.REGISTRY_CODEC, Registries::wolfVariant),
            new Spec<>(BuiltinRegistries.WOLF_SOUND_VARIANT, WolfSoundVariant.REGISTRY_CODEC, Registries::wolfSoundVariant),
            new Spec<>(BuiltinRegistries.CAT_VARIANT, CatVariant.REGISTRY_CODEC, Registries::catVariant),
            new Spec<>(BuiltinRegistries.CAT_SOUND_VARIANT, CatSoundVariant.REGISTRY_CODEC, Registries::catSoundVariant),
            new Spec<>(BuiltinRegistries.CHICKEN_VARIANT, ChickenVariant.REGISTRY_CODEC, Registries::chickenVariant),
            new Spec<>(BuiltinRegistries.CHICKEN_SOUND_VARIANT, ChickenSoundVariant.REGISTRY_CODEC, Registries::chickenSoundVariant),
            new Spec<>(BuiltinRegistries.COW_VARIANT, CowVariant.REGISTRY_CODEC, Registries::cowVariant),
            new Spec<>(BuiltinRegistries.COW_SOUND_VARIANT, CowSoundVariant.REGISTRY_CODEC, Registries::cowSoundVariant),
            new Spec<>(BuiltinRegistries.FROG_VARIANT, FrogVariant.REGISTRY_CODEC, Registries::frogVariant),
            new Spec<>(BuiltinRegistries.PIG_VARIANT, PigVariant.REGISTRY_CODEC, Registries::pigVariant),
            new Spec<>(BuiltinRegistries.PIG_SOUND_VARIANT, PigSoundVariant.REGISTRY_CODEC, Registries::pigSoundVariant),
            new Spec<>(BuiltinRegistries.ZOMBIE_NAUTILUS_VARIANT, ZombieNautilusVariant.REGISTRY_CODEC, Registries::zombieNautilusVariant),
            new Spec<>(BuiltinRegistries.WORLD_CLOCK, WorldClock.REGISTRY_CODEC, Registries::worldClock),
            new Spec<>(BuiltinRegistries.TIMELINE, Timeline.REGISTRY_CODEC, Registries::timeline),
            new Spec<>(BuiltinRegistries.DIMENSION_TYPE, DimensionType.REGISTRY_CODEC, Registries::dimensionType),
            new Spec<>(BuiltinRegistries.SULFUR_CUBE_ARCHETYPE, SulfurCubeArchetype.REGISTRY_CODEC, Registries::sulfurCubeArchetype)
    );

    private final Map<Key, ClientDynamicRegistry<?>> synced = new LinkedHashMap<>();
    private final Map<Key, List<Key>> unsupported;
    private final Registries vanilla;

    /**
     * 创建空的同步注册表，由 {@link ClientRegistriesBuilder} 按顺序填入条目
     */
    ClientRegistries(Registries vanilla, Map<Key, List<Key>> unsupported) {
        this.vanilla = vanilla;
        this.unsupported = Map.copyOf(unsupported);
        for (var spec : SYNCED) {
            synced.put(spec.key().key(), new ClientDynamicRegistry<>(spec.key().key()));
        }
    }

    @SuppressWarnings("unchecked")
    <T> ClientDynamicRegistry<T> writer(RegistryKey<Registry<T>> key) {
        return (ClientDynamicRegistry<T>) synced.get(key.key());
    }

    /**
     * 为同步注册表创建 {@code tags} 中属于它们的标签，让数据包中按名称引用的标签可以解析。
     * 标签成员的判断以 {@link TagTable} 为准
     */
    public void createTags(TagTable tags) {
        for (var entry : synced.entrySet()) {
            var registry = entry.getValue().registry();
            for (var tag : tags.tagNames(entry.getKey().asString())) {
                registry.getOrCreateTag(TagKey.of(Key.key(tag)));
            }
        }
    }

    /**
     * Minestom 没有对应类型的同步注册表：注册表名称 → 条目名称
     */
    public Map<Key, List<Key>> unsupportedRegistries() {
        return unsupported;
    }

    private <T> DynamicRegistry<T> get(RegistryKey<Registry<T>> key) {
        return writer(key).registry();
    }

    @Override
    public DynamicRegistry<ChatType> chatType() {
        return get(BuiltinRegistries.CHAT_TYPE);
    }

    @Override
    public DynamicRegistry<DimensionType> dimensionType() {
        return get(BuiltinRegistries.DIMENSION_TYPE);
    }

    @Override
    public DynamicRegistry<Biome> biome() {
        return get(BuiltinRegistries.BIOME);
    }

    @Override
    public DynamicRegistry<DamageType> damageType() {
        return get(BuiltinRegistries.DAMAGE_TYPE);
    }

    @Override
    public DynamicRegistry<TrimMaterial> trimMaterial() {
        return get(BuiltinRegistries.TRIM_MATERIAL);
    }

    @Override
    public DynamicRegistry<TrimPattern> trimPattern() {
        return get(BuiltinRegistries.TRIM_PATTERN);
    }

    @Override
    public DynamicRegistry<BannerPattern> bannerPattern() {
        return get(BuiltinRegistries.BANNER_PATTERN);
    }

    @Override
    public DynamicRegistry<Enchantment> enchantment() {
        return get(BuiltinRegistries.ENCHANTMENT);
    }

    @Override
    public DynamicRegistry<PaintingVariant> paintingVariant() {
        return get(BuiltinRegistries.PAINTING_VARIANT);
    }

    @Override
    public DynamicRegistry<JukeboxSong> jukeboxSong() {
        return get(BuiltinRegistries.JUKEBOX_SONG);
    }

    @Override
    public DynamicRegistry<Instrument> instrument() {
        return get(BuiltinRegistries.INSTRUMENT);
    }

    @Override
    public DynamicRegistry<WolfVariant> wolfVariant() {
        return get(BuiltinRegistries.WOLF_VARIANT);
    }

    @Override
    public DynamicRegistry<WolfSoundVariant> wolfSoundVariant() {
        return get(BuiltinRegistries.WOLF_SOUND_VARIANT);
    }

    @Override
    public DynamicRegistry<CatVariant> catVariant() {
        return get(BuiltinRegistries.CAT_VARIANT);
    }

    @Override
    public DynamicRegistry<CatSoundVariant> catSoundVariant() {
        return get(BuiltinRegistries.CAT_SOUND_VARIANT);
    }

    @Override
    public DynamicRegistry<ChickenVariant> chickenVariant() {
        return get(BuiltinRegistries.CHICKEN_VARIANT);
    }

    @Override
    public DynamicRegistry<ChickenSoundVariant> chickenSoundVariant() {
        return get(BuiltinRegistries.CHICKEN_SOUND_VARIANT);
    }

    @Override
    public DynamicRegistry<CowVariant> cowVariant() {
        return get(BuiltinRegistries.COW_VARIANT);
    }

    @Override
    public DynamicRegistry<CowSoundVariant> cowSoundVariant() {
        return get(BuiltinRegistries.COW_SOUND_VARIANT);
    }

    @Override
    public DynamicRegistry<FrogVariant> frogVariant() {
        return get(BuiltinRegistries.FROG_VARIANT);
    }

    @Override
    public DynamicRegistry<PigVariant> pigVariant() {
        return get(BuiltinRegistries.PIG_VARIANT);
    }

    @Override
    public DynamicRegistry<PigSoundVariant> pigSoundVariant() {
        return get(BuiltinRegistries.PIG_SOUND_VARIANT);
    }

    @Override
    public DynamicRegistry<ZombieNautilusVariant> zombieNautilusVariant() {
        return get(BuiltinRegistries.ZOMBIE_NAUTILUS_VARIANT);
    }

    @Override
    public DynamicRegistry<Dialog> dialog() {
        return get(BuiltinRegistries.DIALOG);
    }

    @Override
    public DynamicRegistry<Timeline> timeline() {
        return get(BuiltinRegistries.TIMELINE);
    }

    @Override
    public DynamicRegistry<WorldClock> worldClock() {
        return get(BuiltinRegistries.WORLD_CLOCK);
    }

    @Override
    public DynamicRegistry<SulfurCubeArchetype> sulfurCubeArchetype() {
        return get(BuiltinRegistries.SULFUR_CUBE_ARCHETYPE);
    }

    @Override
    public DynamicRegistry<StructCodec<? extends LevelBasedValue>> enchantmentLevelBasedValues() {
        return vanilla.enchantmentLevelBasedValues();
    }

    @Override
    public DynamicRegistry<StructCodec<? extends ValueEffect>> enchantmentValueEffects() {
        return vanilla.enchantmentValueEffects();
    }

    @Override
    public DynamicRegistry<StructCodec<? extends EntityEffect>> enchantmentEntityEffects() {
        return vanilla.enchantmentEntityEffects();
    }

    @Override
    public DynamicRegistry<StructCodec<? extends LocationEffect>> enchantmentLocationEffects() {
        return vanilla.enchantmentLocationEffects();
    }

    @Override
    public DynamicRegistry<Codec<? extends DataComponentPredicate>> componentPredicateTypes() {
        return vanilla.componentPredicateTypes();
    }

    /**
     * 一个同步注册表的名称、条目的编解码器，以及在 {@link Registries} 中取得同名注册表的方法
     */
    record Spec<T>(RegistryKey<Registry<T>> key, Codec<T> codec, Function<Registries, DynamicRegistry<T>> select) {
    }
}
