package io.ib67.yuanban.codec;

import net.minestom.server.component.DataComponent;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.VillagerType;
import net.minestom.server.entity.attribute.Attribute;
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
import net.minestom.server.entity.metadata.other.PaintingVariant;
import net.minestom.server.game.GameEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockEntityType;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.instance.block.jukebox.JukeboxSong;
import net.minestom.server.instance.fluid.Fluid;
import net.minestom.server.item.Material;
import net.minestom.server.item.armor.TrimMaterial;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.item.instrument.Instrument;
import net.minestom.server.message.ChatType;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import net.minestom.server.world.biome.Biome;
import net.minestom.server.world.clock.WorldClock;
import net.minestom.server.world.timeline.Timeline;

/**
 * 原版 {@code net.minecraft.core.registries.Registries} 中数据包解码用到的注册表 ID。
 */
public final class RegistryIds {
    // 原版内置（静态）注册表
    public static final RegistryId<Material> ITEM = RegistryId.of("item");
    public static final RegistryId<Block> BLOCK = RegistryId.of("block");
    public static final RegistryId<EntityType> ENTITY_TYPE = RegistryId.of("entity_type");
    public static final RegistryId<Fluid> FLUID = RegistryId.of("fluid");
    public static final RegistryId<PotionType> POTION = RegistryId.of("potion");
    public static final RegistryId<PotionEffect> MOB_EFFECT = RegistryId.of("mob_effect");
    public static final RegistryId<GameEvent> GAME_EVENT = RegistryId.of("game_event");
    public static final RegistryId<Attribute> ATTRIBUTE = RegistryId.of("attribute");
    public static final RegistryId<DataComponent<?>> DATA_COMPONENT_TYPE = RegistryId.of("data_component_type");
    public static final RegistryId<BlockEntityType> BLOCK_ENTITY_TYPE = RegistryId.of("block_entity_type");
    public static final RegistryId<VillagerType> VILLAGER_TYPE = RegistryId.of("villager_type");
    public static final RegistryId<EnvironmentAttribute<?>> ENVIRONMENT_ATTRIBUTE = RegistryId.of("environment_attribute");
    public static final RegistryId<MapDecorationTypes> MAP_DECORATION_TYPE = RegistryId.of("map_decoration_type");

    // 原版数据驱动（动态）注册表
    public static final RegistryId<ChatType> CHAT_TYPE = RegistryId.of("chat_type");
    public static final RegistryId<DimensionType> DIMENSION_TYPE = RegistryId.of("dimension_type");
    public static final RegistryId<Biome> BIOME = RegistryId.of("worldgen/biome");
    public static final RegistryId<Structure> STRUCTURE = RegistryId.of("worldgen/structure");
    public static final RegistryId<DamageType> DAMAGE_TYPE = RegistryId.of("damage_type");
    public static final RegistryId<TrimMaterial> TRIM_MATERIAL = RegistryId.of("trim_material");
    public static final RegistryId<TrimPattern> TRIM_PATTERN = RegistryId.of("trim_pattern");
    public static final RegistryId<BannerPattern> BANNER_PATTERN = RegistryId.of("banner_pattern");
    public static final RegistryId<Enchantment> ENCHANTMENT = RegistryId.of("enchantment");
    public static final RegistryId<PaintingVariant> PAINTING_VARIANT = RegistryId.of("painting_variant");
    public static final RegistryId<JukeboxSong> JUKEBOX_SONG = RegistryId.of("jukebox_song");
    public static final RegistryId<Instrument> INSTRUMENT = RegistryId.of("instrument");
    public static final RegistryId<WolfVariant> WOLF_VARIANT = RegistryId.of("wolf_variant");
    public static final RegistryId<WolfSoundVariant> WOLF_SOUND_VARIANT = RegistryId.of("wolf_sound_variant");
    public static final RegistryId<CatVariant> CAT_VARIANT = RegistryId.of("cat_variant");
    public static final RegistryId<CatSoundVariant> CAT_SOUND_VARIANT = RegistryId.of("cat_sound_variant");
    public static final RegistryId<ChickenVariant> CHICKEN_VARIANT = RegistryId.of("chicken_variant");
    public static final RegistryId<ChickenSoundVariant> CHICKEN_SOUND_VARIANT = RegistryId.of("chicken_sound_variant");
    public static final RegistryId<CowVariant> COW_VARIANT = RegistryId.of("cow_variant");
    public static final RegistryId<CowSoundVariant> COW_SOUND_VARIANT = RegistryId.of("cow_sound_variant");
    public static final RegistryId<FrogVariant> FROG_VARIANT = RegistryId.of("frog_variant");
    public static final RegistryId<PigVariant> PIG_VARIANT = RegistryId.of("pig_variant");
    public static final RegistryId<PigSoundVariant> PIG_SOUND_VARIANT = RegistryId.of("pig_sound_variant");
    public static final RegistryId<ZombieNautilusVariant> ZOMBIE_NAUTILUS_VARIANT = RegistryId.of("zombie_nautilus_variant");
    public static final RegistryId<Dialog> DIALOG = RegistryId.of("dialog");
    public static final RegistryId<Timeline> TIMELINE = RegistryId.of("timeline");
    public static final RegistryId<WorldClock> WORLD_CLOCK = RegistryId.of("world_clock");

    private RegistryIds() {
    }
}
