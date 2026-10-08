package io.ib67.yuanban.loot.predicate.entity;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.predicate.LocationPredicate;
import io.ib67.yuanban.loot.predicate.component.ComponentPredicate;
import io.ib67.yuanban.loot.slot.SlotRange;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.utils.Range;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 实体子谓词，对应原版 {@code net.minecraft.advancements.predicates.entity.EntitySubPredicate}。
 * 子类型与原版 {@code EntitySubPredicates} 注册的 25 种类型一一对应，各记录的注释写明注册名。
 */
public sealed interface EntitySubPredicate {

    /**
     * {@code entity_type}：实体类型在集合中。
     */
    record EntityTypes(HolderSet<EntityType> types) implements EntitySubPredicate {
        public EntityTypes {
            Objects.requireNonNull(types, "types");
        }
    }

    /**
     * {@code location}：实体所在位置满足位置谓词。
     */
    record Location(LocationPredicate predicate) implements EntitySubPredicate {
        public Location {
            Objects.requireNonNull(predicate, "predicate");
        }
    }

    /**
     * {@code stepping_on}：实体站立的方块（着地时）满足位置谓词。
     */
    record SteppingOn(LocationPredicate predicate) implements EntitySubPredicate {
        public SteppingOn {
            Objects.requireNonNull(predicate, "predicate");
        }
    }

    /**
     * {@code movement_affected_by}：影响实体移动的方块满足位置谓词。
     */
    record MovementAffectedBy(LocationPredicate predicate) implements EntitySubPredicate {
        public MovementAffectedBy {
            Objects.requireNonNull(predicate, "predicate");
        }
    }

    /**
     * {@code distance}：实体与上下文 {@code origin} 的距离。
     */
    record Distance(DistancePredicate distance) implements EntitySubPredicate {
        public Distance {
            Objects.requireNonNull(distance, "distance");
        }
    }

    /**
     * {@code movement}：每秒的速度分量与下落距离。
     */
    record Movement(Range.Double x, Range.Double y, Range.Double z, Range.Double speed, Range.Double horizontalSpeed,
                    Range.Double verticalSpeed, Range.Double fallDistance) implements EntitySubPredicate {
        public Movement {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
            Objects.requireNonNull(z, "z");
            Objects.requireNonNull(speed, "speed");
            Objects.requireNonNull(horizontalSpeed, "horizontalSpeed");
            Objects.requireNonNull(verticalSpeed, "verticalSpeed");
            Objects.requireNonNull(fallDistance, "fallDistance");
        }
    }

    /**
     * {@code effects}：状态效果到效果实例谓词的映射，保持 JSON 中的顺序。
     */
    record Effects(Map<PotionEffect, EffectInstancePredicate> effects) implements EntitySubPredicate {
        public Effects {
            effects = Collections.unmodifiableMap(new LinkedHashMap<>(effects));
        }
    }

    /**
     * {@code nbt}：实体的 NBT 部分匹配。
     */
    record Nbt(CompoundBinaryTag nbt) implements EntitySubPredicate {
        public Nbt {
            Objects.requireNonNull(nbt, "nbt");
        }
    }

    /**
     * {@code flags}：实体的状态标志，各字段对应 JSON 中 {@code is_on_ground}、{@code is_sneaking} 等同名字段。
     */
    record Flags(Optional<Boolean> isOnGround, Optional<Boolean> isOnFire, Optional<Boolean> isSneaking,
                 Optional<Boolean> isSprinting, Optional<Boolean> isSwimming, Optional<Boolean> isFlying,
                 Optional<Boolean> isBaby, Optional<Boolean> isInWater, Optional<Boolean> isFallFlying)
            implements EntitySubPredicate {
        public Flags {
            Objects.requireNonNull(isOnGround, "isOnGround");
            Objects.requireNonNull(isOnFire, "isOnFire");
            Objects.requireNonNull(isSneaking, "isSneaking");
            Objects.requireNonNull(isSprinting, "isSprinting");
            Objects.requireNonNull(isSwimming, "isSwimming");
            Objects.requireNonNull(isFlying, "isFlying");
            Objects.requireNonNull(isBaby, "isBaby");
            Objects.requireNonNull(isInWater, "isInWater");
            Objects.requireNonNull(isFallFlying, "isFallFlying");
        }
    }

    /**
     * {@code equipment}：各装备槽的物品谓词。
     */
    record Equipment(Optional<ItemPredicate> head, Optional<ItemPredicate> chest, Optional<ItemPredicate> legs,
                     Optional<ItemPredicate> feet, Optional<ItemPredicate> body, Optional<ItemPredicate> mainhand,
                     Optional<ItemPredicate> offhand) implements EntitySubPredicate {
        public Equipment {
            Objects.requireNonNull(head, "head");
            Objects.requireNonNull(chest, "chest");
            Objects.requireNonNull(legs, "legs");
            Objects.requireNonNull(feet, "feet");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(mainhand, "mainhand");
            Objects.requireNonNull(offhand, "offhand");
        }
    }

    /**
     * {@code periodic_tick}：实体存在的刻数能被 {@code period} 整除，{@code period} 为正数。
     */
    record PeriodicTick(int period) implements EntitySubPredicate {
        public PeriodicTick {
            if (period <= 0) {
                throw new IllegalArgumentException("period must be positive: " + period);
            }
        }
    }

    /**
     * {@code vehicle}：实体乘坐的载具满足实体谓词。
     */
    record Vehicle(EntityPredicate vehicle) implements EntitySubPredicate {
        public Vehicle {
            Objects.requireNonNull(vehicle, "vehicle");
        }
    }

    /**
     * {@code passenger}：任一乘客满足实体谓词。
     */
    record Passenger(EntityPredicate passenger) implements EntitySubPredicate {
        public Passenger {
            Objects.requireNonNull(passenger, "passenger");
        }
    }

    /**
     * {@code targeted_entity}：生物的攻击目标满足实体谓词。
     */
    record TargetedEntity(EntityPredicate targetedEntity) implements EntitySubPredicate {
        public TargetedEntity {
            Objects.requireNonNull(targetedEntity, "targetedEntity");
        }
    }

    /**
     * {@code team}：实体所在队伍的名字。
     */
    record Team(String team) implements EntitySubPredicate {
        public Team {
            Objects.requireNonNull(team, "team");
        }
    }

    /**
     * {@code slots}：每个槽位范围中至少一个槽位的物品满足物品谓词，保持 JSON 中的顺序。
     */
    record Slots(Map<SlotRange, ItemPredicate> slots) implements EntitySubPredicate {
        public Slots {
            slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        }
    }

    /**
     * {@code components}：实体组件的值与这里完全相同。
     */
    record Components(DataComponentMap components) implements EntitySubPredicate {
        public Components {
            Objects.requireNonNull(components, "components");
        }
    }

    /**
     * {@code predicates}：按组件谓词逐个检查实体的组件。
     */
    record Predicates(List<ComponentPredicate> predicates) implements EntitySubPredicate {
        public Predicates {
            predicates = List.copyOf(predicates);
        }
    }

    /**
     * {@code entity_tags}：实体的命令标签，{@code any_of}、{@code all_of}、{@code none_of} 都可选。
     */
    record EntityTags(Optional<List<String>> anyOf, Optional<List<String>> allOf, Optional<List<String>> noneOf)
            implements EntitySubPredicate {
        public EntityTags {
            anyOf = anyOf.map(List::copyOf);
            allOf = allOf.map(List::copyOf);
            noneOf = noneOf.map(List::copyOf);
        }
    }

    /**
     * {@code type_specific/lightning}：闪电点燃的方块数与击中的实体。
     */
    record Lightning(Range.Int blocksSetOnFire, Optional<EntityPredicate> entityStruck) implements EntitySubPredicate {
        public Lightning {
            Objects.requireNonNull(blocksSetOnFire, "blocksSetOnFire");
            Objects.requireNonNull(entityStruck, "entityStruck");
        }
    }

    /**
     * {@code type_specific/fishing_hook}：鱼钩是否在开阔水域。
     */
    record FishingHook(Optional<Boolean> inOpenWater) implements EntitySubPredicate {
        public FishingHook {
            Objects.requireNonNull(inOpenWater, "inOpenWater");
        }
    }

    /**
     * {@code type_specific/player}：玩家的等级、饥饿、游戏模式、统计、配方、进度、注视的实体和输入。
     *
     * @param gameModes   JSON 字段 {@code gamemode}，缺省是全部游戏模式
     * @param recipes     配方 ID 到期望的是否已解锁，保持 JSON 中的顺序
     * @param advancements 进度 ID 到进度谓词，保持 JSON 中的顺序
     */
    record Player(Range.Int level, FoodPredicate food, List<GameMode> gameModes, List<StatMatcher> stats,
                  Map<Key, Boolean> recipes, Map<Key, AdvancementPredicate> advancements,
                  Optional<EntityPredicate> lookingAt, Optional<InputPredicate> input) implements EntitySubPredicate {
        public Player {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(food, "food");
            gameModes = List.copyOf(gameModes);
            stats = List.copyOf(stats);
            recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
            advancements = Collections.unmodifiableMap(new LinkedHashMap<>(advancements));
            Objects.requireNonNull(lookingAt, "lookingAt");
            Objects.requireNonNull(input, "input");
        }
    }

    /**
     * {@code type_specific/cube_mob}：史莱姆一类生物的大小。
     */
    record CubeMob(Range.Int size) implements EntitySubPredicate {
        public CubeMob {
            Objects.requireNonNull(size, "size");
        }
    }

    /**
     * {@code type_specific/raider}：袭击者是否在袭击中、是否是队长，两项缺省为 {@code false}。
     */
    record Raider(boolean hasRaid, boolean isCaptain) implements EntitySubPredicate {
    }

    /**
     * {@code type_specific/sheep}：羊是否被剪过毛。
     */
    record Sheep(Optional<Boolean> sheared) implements EntitySubPredicate {
        public Sheep {
            Objects.requireNonNull(sheared, "sheared");
        }
    }
}
