package io.ib67.yuanban.loot.entry;

import com.google.gson.JsonObject;

/**
 * 槽位来源：原版 {@code net.minecraft.world.item.slot.SlotSource}。原版 26.2 的原版数据未使用
 * {@code slots} 条目，求值器遇到时按原版约定直接抛出"当前版本不支持 slots 条目"。
 * 本模块只承载它的 JSON 原始内容供解析层透传。
 */
public record SlotSource(JsonObject json) {
}
