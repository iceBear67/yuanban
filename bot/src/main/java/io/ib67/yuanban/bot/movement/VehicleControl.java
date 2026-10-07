package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.entity.ClientEntity;
import io.ib67.yuanban.bot.inventory.Window;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.registry.TagTable;
import io.ib67.yuanban.bot.scheduler.BotState;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.Objects;
import java.util.Set;

/**
 * 本地玩家是否控制一个载具（原版各实体的 {@code getControllingPassenger} 与 {@code isLocalInstanceAuthoritative}）。
 * 控制载具的前提都是本地玩家为它的第一个乘客：
 * <ul>
 *     <li>船与竹筏：没有其它条件；</li>
 *     <li>马、驴、骡、骷髅马、僵尸马、羊驼、行商羊驼、骆驼、鹦鹉螺：装有鞍；</li>
 *     <li>猪、炽足兽：装有鞍，并且玩家手持胡萝卜钓竿、诡异菌钓竿；</li>
 *     <li>快乐恶魂：佩戴挽具，并且没有保持静止；</li>
 *     <li>其余实体（包括矿车）：不能控制。</li>
 * </ul>
 * 「装有鞍」「佩戴挽具」按原版 {@code Mob.hasValidEquippableItemForSlot}：该槽位有物品，物品的 equippable 组件对应这个槽位
 * 并且允许这个实体类型装备，实体可以使用这个槽位（存活，不是幼年；马类与鹦鹉螺还要已被驯服，羊驼没有限制）。
 */
final class VehicleControl {
    private static final String ENTITY_TYPE_REGISTRY = "minecraft:entity_type";
    private static final Set<EntityType> SADDLED_HORSES = Set.of(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE, EntityType.CAMEL, EntityType.CAMEL_HUSK);
    private static final Set<EntityType> LLAMAS = Set.of(EntityType.LLAMA, EntityType.TRADER_LLAMA);
    private static final Set<EntityType> NAUTILUSES = Set.of(EntityType.NAUTILUS, EntityType.ZOMBIE_NAUTILUS);

    private VehicleControl() {
    }

    static boolean controlledBy(ClientEntity vehicle, BotState state) {
        var player = state.player();
        if (vehicle.passengers().isEmpty() || vehicle.passengers().getInt(0) != player.entityId()) {
            return false;
        }
        var type = vehicle.type();
        var tags = state.tags();
        if (RidingSeat.BOATS.contains(type)) {
            return true;
        }
        if (SADDLED_HORSES.contains(type) || NAUTILUSES.contains(type)) {
            var tamed = SADDLED_HORSES.contains(type) ? flag(vehicle, MetadataDef.AbstractHorse.IS_TAME)
                    : flag(vehicle, MetadataDef.TameableAnimal.IS_TAMED);
            return equipped(vehicle, EquipmentSlot.SADDLE, alive(vehicle) && !baby(vehicle) && tamed, tags);
        }
        if (LLAMAS.contains(type)) {
            return equipped(vehicle, EquipmentSlot.SADDLE, true, tags);
        }
        if (type == EntityType.PIG || type == EntityType.STRIDER) {
            var rod = type == EntityType.PIG ? Material.CARROT_ON_A_STICK : Material.WARPED_FUNGUS_ON_A_STICK;
            return equipped(vehicle, EquipmentSlot.SADDLE, alive(vehicle) && !baby(vehicle), tags) && holding(state, player, rod);
        }
        if (type == EntityType.HAPPY_GHAST) {
            return equipped(vehicle, EquipmentSlot.BODY, alive(vehicle) && !baby(vehicle), tags)
                    && !flag(vehicle, MetadataDef.HappyGhast.STAYS_STILL);
        }
        return false;
    }

    /**
     * 原版 {@code Entity.canSprint}：载具中只有骆驼可以疾跑
     */
    static boolean canSprint(ClientEntity vehicle) {
        return RidingSeat.CAMELS.contains(vehicle.type());
    }

    private static boolean equipped(ClientEntity entity, EquipmentSlot slot, boolean canUseSlot, TagTable tags) {
        var item = entity.equipment().getOrDefault(slot, ItemStack.AIR);
        if (item.isAir() || !canUseSlot) {
            return false;
        }
        var equippable = item.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot) {
            return false;
        }
        var allowed = equippable.allowedEntities();
        if (allowed == null) {
            return true;
        }
        var tagKey = allowed.key();
        if (tagKey != null) {
            return tags.contains(ENTITY_TYPE_REGISTRY, tagKey.key().asString(), entity.type().id());
        }
        for (var key : allowed) {
            if (key.key().equals(entity.type().key())) {
                return true;
            }
        }
        return false;
    }

    private static boolean holding(BotState state, LocalPlayer player, Material material) {
        var window = state.inventory().player();
        return window.get(Window.PLAYER_HOTBAR_START + player.heldSlot()).material() == material
                || window.get(Window.PLAYER_OFFHAND).material() == material;
    }

    private static boolean alive(ClientEntity entity) {
        var health = entity.metadata().get(MetadataDef.LivingEntity.HEALTH.index());
        return (health == null ? MetadataDef.LivingEntity.HEALTH.defaultValue() : (Float) health.value()) > 0;
    }

    private static boolean baby(ClientEntity entity) {
        return flag(entity, MetadataDef.AgeableMob.IS_BABY);
    }

    private static boolean flag(ClientEntity entity, MetadataDef.Entry<Boolean> entry) {
        var value = entity.metadata().get(entry.index());
        if (value == null) {
            return Objects.requireNonNull(entry.defaultValue());
        }
        if (entry instanceof MetadataDef.Entry.BitMask mask) {
            return ((Byte) value.value() & mask.bitMask()) != 0;
        }
        return (Boolean) value.value();
    }
}
