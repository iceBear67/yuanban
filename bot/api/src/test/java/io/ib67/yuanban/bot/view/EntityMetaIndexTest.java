package io.ib67.yuanban.bot.view;

import net.minestom.server.entity.MetadataDef;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMetaIndexTest {
    @Test
    void indicesAndMasksMatchTheMinestomDefinitions() {
        assertIndex(MetadataDef.HAS_NO_GRAVITY, EntityMetaIndex.HAS_NO_GRAVITY);
        assertEquals(MetadataDef.POSE.index(), EntityMetaIndex.POSE.index());
        assertIndex(MetadataDef.LivingEntity.HEALTH, EntityMetaIndex.LIVING_HEALTH);
        assertMask(MetadataDef.ArmorStand.IS_SMALL, EntityMetaIndex.ARMOR_STAND_IS_SMALL);
        assertIndex(MetadataDef.AgeableMob.IS_BABY, EntityMetaIndex.AGEABLE_IS_BABY);
        assertIndex(MetadataDef.Zombie.IS_BABY, EntityMetaIndex.ZOMBIE_IS_BABY);
        assertIndex(MetadataDef.Zoglin.IS_BABY, EntityMetaIndex.ZOGLIN_IS_BABY);
        assertIndex(MetadataDef.Piglin.IS_BABY, EntityMetaIndex.PIGLIN_IS_BABY);
        assertIndex(MetadataDef.AbstractCubeMob.SIZE, EntityMetaIndex.CUBE_SIZE);
        assertIndex(MetadataDef.AbstractHorse.ABSTRACT_HORSE_FLAGS, EntityMetaIndex.HORSE_FLAGS);
        assertMask(MetadataDef.AbstractHorse.IS_TAME, EntityMetaIndex.HORSE_IS_TAME);
        assertMask(MetadataDef.AbstractHorse.IS_REARING, EntityMetaIndex.HORSE_IS_REARING);
        assertMask(MetadataDef.TameableAnimal.IS_TAMED, EntityMetaIndex.TAMEABLE_IS_TAMED);
        assertIndex(MetadataDef.HappyGhast.STAYS_STILL, EntityMetaIndex.HAPPY_GHAST_STAYS_STILL);
        assertIndex(MetadataDef.Camel.LAST_POSE_CHANGE_TICK, EntityMetaIndex.CAMEL_LAST_POSE_CHANGE_TICK);
    }

    @Test
    void missingEntriesReadTheDefaultValue() {
        Map<Integer, EntityMetadata<?, ?>> metadata = Map.of();
        assertFalse(EntityMetaIndex.get(metadata, EntityMetaIndex.HAS_NO_GRAVITY));
        assertEquals(Pose.STANDING, EntityMetaIndex.get(metadata, EntityMetaIndex.POSE));
        assertEquals(1F, EntityMetaIndex.get(metadata, EntityMetaIndex.LIVING_HEALTH));
        assertEquals(1, EntityMetaIndex.get(metadata, EntityMetaIndex.CUBE_SIZE));
        assertEquals(0L, EntityMetaIndex.get(metadata, EntityMetaIndex.CAMEL_LAST_POSE_CHANGE_TICK));
        assertFalse(EntityMetaIndex.get(metadata, EntityMetaIndex.HORSE_IS_REARING));
    }

    @Test
    void entriesAndBitsAreRead() {
        var metadata = new HashMap<Integer, EntityMetadata<?, ?>>();
        metadata.put(5, new BooleanEntityMetadata(5, MetadataTypes.BOOLEAN, true));
        metadata.put(6, new ObjectEntityMetadata<>(6, MetadataTypes.POSE, Pose.SNEAKING));
        metadata.put(18, new ByteEntityMetadata(18, MetadataTypes.BYTE, (byte) 0x22));
        assertTrue(EntityMetaIndex.get(metadata, EntityMetaIndex.HAS_NO_GRAVITY));
        assertEquals(Pose.SNEAKING, EntityMetaIndex.get(metadata, EntityMetaIndex.POSE));
        assertEquals((byte) 0x22, EntityMetaIndex.get(metadata, EntityMetaIndex.HORSE_FLAGS));
        assertTrue(EntityMetaIndex.get(metadata, EntityMetaIndex.HORSE_IS_TAME));
        assertTrue(EntityMetaIndex.get(metadata, EntityMetaIndex.HORSE_IS_REARING));
        assertFalse(EntityMetaIndex.get(metadata, EntityMetaIndex.TAMEABLE_IS_TAMED));
        metadata.put(18, new IntEntityMetadata(18, MetadataTypes.INT, 3));
        assertEquals(3, EntityMetaIndex.get(metadata, EntityMetaIndex.CUBE_SIZE));
    }

    /**
     * 按下标读取的条目：下标与默认值相同
     */
    private static void assertIndex(MetadataDef.Entry<?> expected, EntityMeta<?> actual) {
        assertEquals(expected.index(), actual.index());
        assertEquals(expected.defaultValue(), actual.defaultValue());
        assertEquals(0, actual.mask());
    }

    /**
     * 标记字节中的一位：下标与掩码相同
     */
    private static void assertMask(MetadataDef.Entry<?> expected, EntityMeta<?> actual) {
        var bitMask = (MetadataDef.Entry.BitMask) expected;
        assertEquals(bitMask.index(), actual.index());
        assertEquals(bitMask.bitMask(), actual.mask());
        assertEquals(false, actual.defaultValue());
    }
}
