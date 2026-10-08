package io.ib67.yuanban.codec;

import com.mojang.serialization.MapCodec;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.entity.EntityType;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.registry.Holder;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 原版文本组件的数据结构，{@link ComponentSerialization} 的编解码器在这个结构上按原版的方式工作，
 * 之后再转换成 adventure 的组件。保留原版的结构是为了重现原版只能从这个结构得到的行为，
 * 例如 {@link ComponentSerialization#flatRestrictedCodec} 按原版的编码结果计算长度。
 */
final class VanillaText {
    private VanillaText() {
    }

    /**
     * 原版 {@code MutableComponent}：内容、子组件和样式。
     */
    record TextComponent(Contents contents, List<TextComponent> siblings, TextStyle style) {
        TextComponent {
            siblings = List.copyOf(siblings);
        }

        /**
         * 原版 {@code Component.tryCollapseToString}：没有样式和子组件的纯文本组件写成字符串。
         */
        @Nullable String tryCollapseToString() {
            if (contents instanceof PlainText(String text) && siblings.isEmpty() && style.isEmpty()) {
                return text;
            }
            return null;
        }

        static TextComponent literal(String text) {
            return new TextComponent(new PlainText(text), List.of(), TextStyle.EMPTY);
        }
    }

    /**
     * 原版 {@code ComponentContents}。
     */
    sealed interface Contents permits PlainText, Translatable, Keybind, Score, Selector, Nbt, ObjectContents {
        MapCodec<? extends Contents> codec();
    }

    /**
     * 原版 {@code PlainTextContents}。
     */
    record PlainText(String text) implements Contents {
        @Override
        public MapCodec<PlainText> codec() {
            return ComponentSerialization.PLAIN_TEXT;
        }
    }

    /**
     * 原版 {@code TranslatableContents}：参数是字符串、数字、布尔值或者 {@link TextComponent}。
     */
    record Translatable(String key, @Nullable String fallback, List<Object> args) implements Contents {
        Translatable {
            args = List.copyOf(args);
        }

        @Override
        public MapCodec<Translatable> codec() {
            return ComponentSerialization.TRANSLATABLE;
        }
    }

    /**
     * 原版 {@code KeybindContents}。
     */
    record Keybind(String name) implements Contents {
        @Override
        public MapCodec<Keybind> codec() {
            return ComponentSerialization.KEYBIND;
        }
    }

    /**
     * 原版 {@code ScoreContents}：{@code name} 是选择器或者计分项持有者的名字，两者都按原文保存。
     */
    record Score(String name, String objective) implements Contents {
        @Override
        public MapCodec<Score> codec() {
            return ComponentSerialization.SCORE;
        }
    }

    /**
     * 原版 {@code SelectorContents}。
     */
    record Selector(CompilableString<EntitySelectorParser.Selector> selector, Optional<TextComponent> separator) implements Contents {
        @Override
        public MapCodec<Selector> codec() {
            return ComponentSerialization.SELECTOR;
        }
    }

    /**
     * 原版 {@code NbtContents}。
     */
    record Nbt(CompilableString<NbtPath> nbtPath, boolean interpreting, boolean plain, Optional<TextComponent> separator,
               DataSource dataSource) implements Contents {
        @Override
        public MapCodec<Nbt> codec() {
            return ComponentSerialization.NBT;
        }
    }

    /**
     * 原版 {@code ObjectContents}。
     */
    record ObjectContents(ObjectInfo contents, Optional<TextComponent> fallback) implements Contents {
        @Override
        public MapCodec<ObjectContents> codec() {
            return ComponentSerialization.OBJECT;
        }
    }

    /**
     * 原版 {@code DataSource}。
     */
    sealed interface DataSource permits EntityDataSource, BlockDataSource, StorageDataSource {
        MapCodec<? extends DataSource> codec();
    }

    /**
     * 原版 {@code EntityDataSource}。
     */
    record EntityDataSource(CompilableString<EntitySelectorParser.Selector> selector) implements DataSource {
        @Override
        public MapCodec<EntityDataSource> codec() {
            return ComponentSerialization.ENTITY_DATA_SOURCE;
        }
    }

    /**
     * 原版 {@code BlockDataSource}。
     */
    record BlockDataSource(CompilableString<BlockPosArgument.Coordinates> coordinates) implements DataSource {
        @Override
        public MapCodec<BlockDataSource> codec() {
            return ComponentSerialization.BLOCK_DATA_SOURCE;
        }
    }

    /**
     * 原版 {@code StorageDataSource}。
     */
    record StorageDataSource(Key id) implements DataSource {
        @Override
        public MapCodec<StorageDataSource> codec() {
            return ComponentSerialization.STORAGE_DATA_SOURCE;
        }
    }

    /**
     * 原版 {@code ObjectInfo}。
     */
    sealed interface ObjectInfo permits AtlasSprite, PlayerSprite {
        MapCodec<? extends ObjectInfo> codec();
    }

    /**
     * 原版 {@code AtlasSprite}。
     */
    record AtlasSprite(Key atlas, Key sprite) implements ObjectInfo {
        @Override
        public MapCodec<AtlasSprite> codec() {
            return ComponentSerialization.ATLAS_SPRITE;
        }
    }

    /**
     * 原版 {@code PlayerSprite}，玩家资料使用 Minestom 的 {@link ResolvableProfile}。
     */
    record PlayerSprite(ResolvableProfile player, boolean hat) implements ObjectInfo {
        @Override
        public MapCodec<PlayerSprite> codec() {
            return ComponentSerialization.PLAYER_SPRITE;
        }
    }

    /**
     * 原版 {@code Style}：每一项都可以缺失。
     */
    record TextStyle(@Nullable TextColor color, @Nullable Integer shadowColor, @Nullable Boolean bold, @Nullable Boolean italic,
                     @Nullable Boolean underlined, @Nullable Boolean strikethrough, @Nullable Boolean obfuscated,
                     @Nullable ClickEvent clickEvent, @Nullable HoverEvent hoverEvent, @Nullable String insertion,
                     @Nullable Key font) {
        static final TextStyle EMPTY = new TextStyle(null, null, null, null, null, null, null, null, null, null, null);

        /**
         * 原版 {@code Style.isEmpty}：每一项都缺失。
         */
        boolean isEmpty() {
            return color == null && shadowColor == null && bold == null && italic == null && underlined == null
                    && strikethrough == null && obfuscated == null && clickEvent == null && hoverEvent == null
                    && insertion == null && font == null;
        }
    }

    /**
     * 原版 {@code TextColor}：{@code name} 是解码时使用的颜色名，用十六进制写出的颜色没有名字。
     */
    record TextColor(int value, @Nullable String name) {
        /**
         * 原版 {@code TextColor.serialize}。
         */
        String serialize() {
            return name != null ? name : String.format(java.util.Locale.ROOT, "#%06X", value);
        }
    }

    /**
     * 原版 {@code ClickEvent}。
     */
    sealed interface ClickEvent permits OpenUrl, OpenFile, RunCommand, SuggestCommand, ShowDialog, ChangePage, CopyToClipboard, Custom {
        ComponentSerialization.ClickAction action();
    }

    record OpenUrl(URI uri) implements ClickEvent {
        @Override
        public ComponentSerialization.ClickAction action() {
            return ComponentSerialization.ClickAction.OPEN_URL;
        }
    }

    record OpenFile(String path) implements ClickEvent {
        @Override
        public ComponentSerialization.ClickAction action() {
            return ComponentSerialization.ClickAction.OPEN_FILE;
        }
    }

    record RunCommand(String command) implements ClickEvent {
        @Override
        public ComponentSerialization.ClickAction action() {
            return ComponentSerialization.ClickAction.RUN_COMMAND;
        }
    }

    record SuggestCommand(String command) implements ClickEvent {
        @Override
        public ComponentSerialization.ClickAction action() {
            return ComponentSerialization.ClickAction.SUGGEST_COMMAND;
        }
    }

    record ShowDialog(Holder<Dialog> dialog) implements ClickEvent {
        @Override
        public ComponentSerialization.ClickAction action() {
            return ComponentSerialization.ClickAction.SHOW_DIALOG;
        }
    }

    record ChangePage(int page) implements ClickEvent {
        @Override
        public ComponentSerialization.ClickAction action() {
            return ComponentSerialization.ClickAction.CHANGE_PAGE;
        }
    }

    record CopyToClipboard(String value) implements ClickEvent {
        @Override
        public ComponentSerialization.ClickAction action() {
            return ComponentSerialization.ClickAction.COPY_TO_CLIPBOARD;
        }
    }

    record Custom(Key id, Optional<BinaryTag> payload) implements ClickEvent {
        @Override
        public ComponentSerialization.ClickAction action() {
            return ComponentSerialization.ClickAction.CUSTOM;
        }
    }

    /**
     * 原版 {@code HoverEvent}。
     */
    sealed interface HoverEvent permits ShowText, ShowItem, ShowEntity {
        ComponentSerialization.HoverAction action();
    }

    record ShowText(TextComponent value) implements HoverEvent {
        @Override
        public ComponentSerialization.HoverAction action() {
            return ComponentSerialization.HoverAction.SHOW_TEXT;
        }
    }

    record ShowItem(ItemStackTemplate item) implements HoverEvent {
        @Override
        public ComponentSerialization.HoverAction action() {
            return ComponentSerialization.HoverAction.SHOW_ITEM;
        }
    }

    /**
     * 原版 {@code HoverEvent.ShowEntity} 与 {@code EntityTooltipInfo}。
     */
    record ShowEntity(EntityType type, UUID uuid, Optional<TextComponent> name) implements HoverEvent {
        @Override
        public ComponentSerialization.HoverAction action() {
            return ComponentSerialization.HoverAction.SHOW_ENTITY;
        }
    }
}
