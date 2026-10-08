package io.ib67.yuanban.loot.slot;

import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;

import java.util.List;

/**
 * 装有物品的组件，对应原版 {@code ContainerComponentManipulators} 登记的三种：
 * {@code container}、{@code bundle_contents}、{@code charged_projectiles}。
 * {@code set_contents}、{@code modify_contents} 和 {@code contents} 槽位来源用它选择组件。
 */
public enum ContainerComponent {
    CONTAINER(DataComponents.CONTAINER),
    BUNDLE_CONTENTS(DataComponents.BUNDLE_CONTENTS),
    CHARGED_PROJECTILES(DataComponents.CHARGED_PROJECTILES);

    private final DataComponent<List<ItemStack>> component;

    ContainerComponent(DataComponent<List<ItemStack>> component) {
        this.component = component;
    }

    /**
     * 对应的 Minestom 组件，三种组件在 Minestom 中都是物品列表。
     */
    public DataComponent<List<ItemStack>> component() {
        return component;
    }
}
