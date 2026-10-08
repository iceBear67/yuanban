package io.ib67.yuanban.loot.slot;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一组槽位编号的名字，对应原版 {@code net.minecraft.world.inventory.SlotRange} 与 {@code SlotRanges}。
 * {@link #VALUES} 按原版 {@code SlotRanges} 的登记顺序列出全部名字，编号与原版相同：
 * 容器 0–53，物品栏 9–35，快捷栏 0–8，末影箱 200–226，生物物品栏 300–307，马的物品栏 500–514，
 * 主手 98、副手 99，盔甲 100–103，身体 105，鞍 106，光标与马的箱子 499，玩家合成格 500–503。
 *
 * @param name  JSON 中的名字，例如 {@code hotbar.*}、{@code armor.head}
 * @param slots 槽位编号，按原版的顺序
 */
public record SlotRange(String name, List<Integer> slots) {
    /**
     * 原版 {@code SlotRanges.SLOTS}，按登记顺序。
     */
    public static final List<SlotRange> VALUES = createAll();

    public SlotRange {
        Objects.requireNonNull(name, "name");
        slots = List.copyOf(slots);
    }

    private static List<SlotRange> createAll() {
        var values = new ArrayList<SlotRange>();
        single(values, "contents", 0);
        range(values, "container.", 0, 54);
        range(values, "hotbar.", 0, 9);
        range(values, "inventory.", 9, 27);
        range(values, "enderchest.", 200, 27);
        range(values, "mob.inventory.", 300, 8);
        range(values, "horse.", 500, 15);
        // 原版 EquipmentSlot.getIndex(base)：主手 0、副手 1；脚 0、腿 1、胸 2、头 3；身体与鞍 0
        int mainhand = 98;
        int offhand = 99;
        single(values, "weapon", mainhand);
        single(values, "weapon.mainhand", mainhand);
        single(values, "weapon.offhand", offhand);
        values.add(new SlotRange("weapon.*", List.of(mainhand, offhand)));
        int head = 103;
        int chest = 102;
        int legs = 101;
        int feet = 100;
        int body = 105;
        single(values, "armor.head", head);
        single(values, "armor.chest", chest);
        single(values, "armor.legs", legs);
        single(values, "armor.feet", feet);
        single(values, "armor.body", body);
        values.add(new SlotRange("armor.*", List.of(head, chest, legs, feet, body)));
        single(values, "saddle", 106);
        single(values, "horse.chest", 499);
        single(values, "player.cursor", 499);
        range(values, "player.crafting.", 500, 4);
        return List.copyOf(values);
    }

    private static void single(List<SlotRange> values, String name, int slot) {
        values.add(new SlotRange(name, List.of(slot)));
    }

    private static void range(List<SlotRange> values, String prefix, int offset, int size) {
        var all = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            values.add(new SlotRange(prefix + i, List.of(offset + i)));
            all.add(offset + i);
        }
        values.add(new SlotRange(prefix + "*", all));
    }

    @Override
    public @NotNull String toString() {
        return name;
    }
}
