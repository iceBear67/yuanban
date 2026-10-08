package io.ib67.yuanban.loot.context;

import io.ib67.yuanban.loot.number.EntityTarget;

import java.util.Objects;

/**
 * 从上下文读取一项值的来源，对应原版 {@code net.minecraft.world.level.storage.loot.LootContextArg}。
 * <p>
 * 原版按用途组合不同的取值集合：{@code copy_name} 与 {@code slot_range} 接受实体和方块实体，
 * {@code copy_components} 另外接受工具，NBT 提供器接受实体和方块实体。某个字段接受哪些取值由它的编解码器决定，
 * 本类型只表示读取的参数。
 */
public sealed interface LootContextArg {
    /**
     * 原版 {@code LootContextArg#contextParam}：读取的上下文参数。
     */
    ContextKey<?> contextParam();

    /**
     * JSON 中的名字，与原版 {@code ArgCodecBuilder} 登记的名字相同。
     */
    String serializedName();

    /**
     * 原版 {@code LootContext$EntityTarget}：上下文中的实体。
     */
    record Entity(EntityTarget target) implements LootContextArg {
        public Entity {
            Objects.requireNonNull(target, "target");
        }

        @Override
        public ContextKey<?> contextParam() {
            return target.contextParam();
        }

        @Override
        public String serializedName() {
            return target.serializedName();
        }
    }

    /**
     * 原版 {@code LootContext$BlockEntityTarget#BLOCK_ENTITY}：上下文中的方块实体，名字 {@code block_entity}。
     */
    record BlockEntity() implements LootContextArg {
        public static final BlockEntity INSTANCE = new BlockEntity();

        @Override
        public ContextKey<?> contextParam() {
            return ContextKey.BLOCK_ENTITY;
        }

        @Override
        public String serializedName() {
            return "block_entity";
        }
    }

    /**
     * 原版 {@code LootContext$ItemStackTarget#TOOL}：上下文中的工具，名字 {@code tool}。
     */
    record Tool() implements LootContextArg {
        public static final Tool INSTANCE = new Tool();

        @Override
        public ContextKey<?> contextParam() {
            return ContextKey.TOOL;
        }

        @Override
        public String serializedName() {
            return "tool";
        }
    }
}
