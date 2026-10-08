package io.ib67.yuanban.codec;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 原版 {@code net.minecraft.commands.arguments.selector.EntitySelectorParser} 与
 * {@code options.EntitySelectorOptions}：解析玩家名、UUID 或者 {@code @} 开头的选择器及其选项。
 * <p>
 * 接受的输入、解析时进行的检查（选项是否适用、取值范围、实体类型是否存在）和错误文本都与原版相同。
 * 原版解析器同时构造求值用的条件；文本组件的解码只需要检查选择器，所以这里不构造求值条件，
 * 结果 {@link Selector} 只包含原版选择器中不依赖世界的属性。
 */
final class EntitySelectorParser {
    static final SimpleCommandExceptionType ERROR_INVALID_NAME_OR_UUID = CommandMessages.simple("argument.entity.invalid");
    static final DynamicCommandExceptionType ERROR_UNKNOWN_SELECTOR_TYPE = CommandMessages.dynamic("argument.entity.selector.unknown");
    static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = CommandMessages.simple("argument.entity.selector.not_allowed");
    static final SimpleCommandExceptionType ERROR_MISSING_SELECTOR_TYPE = CommandMessages.simple("argument.entity.selector.missing");
    static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_OPTIONS = CommandMessages.simple("argument.entity.options.unterminated");
    static final DynamicCommandExceptionType ERROR_EXPECTED_OPTION_VALUE = CommandMessages.dynamic("argument.entity.options.valueless");
    static final DynamicCommandExceptionType ERROR_UNKNOWN_OPTION = CommandMessages.dynamic("argument.entity.options.unknown");
    static final DynamicCommandExceptionType ERROR_INAPPLICABLE_OPTION = CommandMessages.dynamic("argument.entity.options.inapplicable");
    static final SimpleCommandExceptionType ERROR_RANGE_NEGATIVE = CommandMessages.simple("argument.entity.options.distance.negative");
    static final SimpleCommandExceptionType ERROR_LEVEL_NEGATIVE = CommandMessages.simple("argument.entity.options.level.negative");
    static final SimpleCommandExceptionType ERROR_LIMIT_TOO_SMALL = CommandMessages.simple("argument.entity.options.limit.toosmall");
    static final DynamicCommandExceptionType ERROR_SORT_UNKNOWN = CommandMessages.dynamic("argument.entity.options.sort.irreversible");
    static final DynamicCommandExceptionType ERROR_GAME_MODE_INVALID = CommandMessages.dynamic("argument.entity.options.mode.invalid");
    static final DynamicCommandExceptionType ERROR_ENTITY_TYPE_INVALID = CommandMessages.dynamic("argument.entity.options.type.invalid");
    /**
     * 原版 {@code MinMaxBounds.ERROR_EMPTY}。
     */
    static final SimpleCommandExceptionType ERROR_RANGE_EMPTY = CommandMessages.simple("argument.range.empty");
    /**
     * 原版 {@code MinMaxBounds.ERROR_SWAPPED}。
     */
    static final SimpleCommandExceptionType ERROR_RANGE_SWAPPED = CommandMessages.simple("argument.range.swapped");
    /**
     * 原版 {@code EntitySelectorOptions.OPTIONS}。
     */
    private static final Map<String, Option> OPTIONS = new HashMap<>();

    static {
        register("name", parser -> {
            int start = parser.reader.getCursor();
            boolean inverted = parser.shouldInvertValue();
            parser.reader.readString();
            if (!parser.nameOption.canParseElement(inverted)) {
                throw rollbackAndThrow(parser, start, ERROR_INAPPLICABLE_OPTION, "name");
            }
            parser.nameOption.markParsedElement(inverted);
        }, parser -> parser.nameOption.canParseAny());
        register("distance", parser -> {
            int start = parser.reader.getCursor();
            Bounds<Double> value = Bounds.fromReader(parser.reader, Double::parseDouble,
                    CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidDouble);
            if (value.areSwapped()) {
                parser.reader.setCursor(start);
                throw ERROR_RANGE_SWAPPED.createWithContext(parser.reader);
            }
            if (value.min().isPresent() && value.min().get() < 0.0 || value.max().isPresent() && value.max().get() < 0.0) {
                throw rollbackAndThrow(parser, start, ERROR_RANGE_NEGATIVE);
            }
            parser.distance = value;
        }, parser -> parser.distance == null);
        register("level", parser -> {
            int start = parser.reader.getCursor();
            Bounds<Integer> value = Bounds.fromReader(parser.reader, Integer::parseInt,
                    CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidInt);
            if (value.areSwapped()) {
                parser.reader.setCursor(start);
                throw ERROR_RANGE_SWAPPED.createWithContext(parser.reader);
            }
            if (value.min().isPresent() && value.min().get() < 0 || value.max().isPresent() && value.max().get() < 0) {
                throw rollbackAndThrow(parser, start, ERROR_LEVEL_NEGATIVE);
            }
            parser.level = value;
            parser.includesEntities = false;
        }, parser -> parser.level == null);
        register("x", parser -> parser.x = parser.reader.readDouble(), parser -> parser.x == null);
        register("y", parser -> parser.y = parser.reader.readDouble(), parser -> parser.y == null);
        register("z", parser -> parser.z = parser.reader.readDouble(), parser -> parser.z == null);
        register("dx", parser -> parser.deltaX = parser.reader.readDouble(), parser -> parser.deltaX == null);
        register("dy", parser -> parser.deltaY = parser.reader.readDouble(), parser -> parser.deltaY == null);
        register("dz", parser -> parser.deltaZ = parser.reader.readDouble(), parser -> parser.deltaZ == null);
        register("x_rotation", parser -> parser.rotX = Bounds.fromReader(parser.reader, Float::parseFloat,
                CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidFloat), parser -> parser.rotX == null);
        register("y_rotation", parser -> parser.rotY = Bounds.fromReader(parser.reader, Float::parseFloat,
                CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidFloat), parser -> parser.rotY == null);
        register("limit", parser -> {
            int start = parser.reader.getCursor();
            int count = parser.reader.readInt();
            if (count < 1) {
                throw rollbackAndThrow(parser, start, ERROR_LIMIT_TOO_SMALL);
            }
            parser.maxResults = count;
            parser.limitedOption.markParsed();
        }, parser -> !parser.currentEntity && parser.limitedOption.canParse());
        register("sort", parser -> {
            int start = parser.reader.getCursor();
            String name = parser.reader.readUnquotedString();
            switch (name) {
                case "nearest", "furthest", "random", "arbitrary" -> {
                }
                default -> throw rollbackAndThrow(parser, start, ERROR_SORT_UNKNOWN, name);
            }
            parser.sortedOption.markParsed();
        }, parser -> !parser.currentEntity && parser.sortedOption.canParse());
        register("gamemode", parser -> {
            InvertableSetOptionState state = parser.gamemodeOption;
            int start = parser.reader.getCursor();
            boolean inverted = parser.shouldInvertValue();
            if (!state.canParseElement(inverted)) {
                throw rollbackAndThrow(parser, start, ERROR_INAPPLICABLE_OPTION, "gamemode");
            }
            String name = parser.reader.readUnquotedString();
            if (gameModeByName(name) == null) {
                throw rollbackAndThrow(parser, start, ERROR_GAME_MODE_INVALID, name);
            }
            parser.includesEntities = false;
            state.markParsedElement(inverted);
        }, parser -> parser.gamemodeOption.canParseAny());
        register("team", parser -> {
            InvertableSetOptionState state = parser.teamOption;
            int start = parser.reader.getCursor();
            boolean inverted = parser.shouldInvertValue();
            parser.reader.readUnquotedString();
            if (!state.canParseElement(inverted)) {
                throw rollbackAndThrow(parser, start, ERROR_INAPPLICABLE_OPTION, "team");
            }
            state.markParsedElement(inverted);
        }, parser -> parser.teamOption.canParseAny());
        register("type", parser -> {
            InvertableSetOptionState state = parser.typeOption;
            int start = parser.reader.getCursor();
            boolean inverted = parser.shouldInvertValue();
            if (parser.isTag()) {
                if (!state.canParseAnyTag()) {
                    throw rollbackAndThrow(parser, start, ERROR_INAPPLICABLE_OPTION, "type");
                }
                Key id = Identifiers.read(parser.reader);
                if (!state.canParseTag(id)) {
                    throw rollbackAndThrow(parser, start, ERROR_INAPPLICABLE_OPTION, "type");
                }
                state.markParsedTag(id);
            } else {
                if (!state.canParseElement(inverted)) {
                    throw rollbackAndThrow(parser, start, ERROR_INAPPLICABLE_OPTION, "type");
                }
                Key id = Identifiers.read(parser.reader);
                EntityType type = EntityType.fromKey(id);
                if (type == null) {
                    throw rollbackAndThrow(parser, start, ERROR_ENTITY_TYPE_INVALID, id.asString());
                }
                if (type == EntityType.PLAYER && !inverted) {
                    parser.includesEntities = false;
                }
                state.markParsedElement(inverted);
            }
        }, parser -> parser.typeOption.canParseAny());
        register("tag", parser -> {
            parser.shouldInvertValue();
            parser.reader.readUnquotedString();
        }, parser -> true);
        register("nbt", parser -> {
            parser.shouldInvertValue();
            TagParser.parseCompoundAsArgument(parser.reader);
        }, parser -> true);
        register("scores", parser -> {
            StringReader reader = parser.reader;
            Map<String, Bounds<Integer>> expected = new HashMap<>();
            reader.expect('{');
            reader.skipWhitespace();
            while (reader.canRead() && reader.peek() != '}') {
                reader.skipWhitespace();
                String name = reader.readUnquotedString();
                reader.skipWhitespace();
                reader.expect('=');
                reader.skipWhitespace();
                int start = reader.getCursor();
                Bounds<Integer> value = Bounds.fromReader(reader, Integer::parseInt,
                        CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidInt);
                if (value.areSwapped()) {
                    reader.setCursor(start);
                    throw ERROR_RANGE_SWAPPED.createWithContext(reader);
                }
                expected.put(name, value);
                reader.skipWhitespace();
                if (reader.canRead() && reader.peek() == ',') {
                    reader.skip();
                }
            }
            reader.expect('}');
            parser.scoresOption.markParsed();
        }, parser -> parser.scoresOption.canParse());
        register("advancements", parser -> {
            StringReader reader = parser.reader;
            Set<Key> expected = new HashSet<>();
            reader.expect('{');
            reader.skipWhitespace();
            while (reader.canRead() && reader.peek() != '}') {
                reader.skipWhitespace();
                Key name = Identifiers.read(reader);
                reader.skipWhitespace();
                reader.expect('=');
                reader.skipWhitespace();
                if (reader.canRead() && reader.peek() == '{') {
                    reader.skipWhitespace();
                    reader.expect('{');
                    reader.skipWhitespace();
                    while (reader.canRead() && reader.peek() != '}') {
                        reader.skipWhitespace();
                        reader.readUnquotedString();
                        reader.skipWhitespace();
                        reader.expect('=');
                        reader.skipWhitespace();
                        reader.readBoolean();
                        reader.skipWhitespace();
                        if (reader.canRead() && reader.peek() == ',') {
                            reader.skip();
                        }
                    }
                    reader.skipWhitespace();
                    reader.expect('}');
                    reader.skipWhitespace();
                } else {
                    reader.readBoolean();
                }
                expected.add(name);
                reader.skipWhitespace();
                if (reader.canRead() && reader.peek() == ',') {
                    reader.skip();
                }
            }
            reader.expect('}');
            if (!expected.isEmpty()) {
                parser.includesEntities = false;
            }
            parser.advancementsOption.markParsed();
        }, parser -> parser.advancementsOption.canParse());
        register("predicate", parser -> {
            parser.shouldInvertValue();
            Identifiers.read(parser.reader);
        }, parser -> true);
    }

    private final StringReader reader;
    private final boolean allowSelectors;
    private int maxResults;
    private boolean includesEntities;
    private @Nullable Bounds<Double> distance;
    private @Nullable Bounds<Integer> level;
    private @Nullable Double x;
    private @Nullable Double y;
    private @Nullable Double z;
    private @Nullable Double deltaX;
    private @Nullable Double deltaY;
    private @Nullable Double deltaZ;
    private @Nullable Bounds<Float> rotX;
    private @Nullable Bounds<Float> rotY;
    private boolean currentEntity;
    private @Nullable String playerName;
    private @Nullable UUID entityUUID;
    private boolean usesSelectors;
    private final InvertableSetOptionState nameOption = new InvertableSetOptionState();
    private final SetOnceOptionState limitedOption = new SetOnceOptionState();
    private final SetOnceOptionState sortedOption = new SetOnceOptionState();
    private final InvertableSetOptionState gamemodeOption = new InvertableSetOptionState();
    private final InvertableSetOptionState teamOption = new InvertableSetOptionState();
    private final InvertableSetOptionState typeOption = new InvertableSetOptionState();
    private final SetOnceOptionState scoresOption = new SetOnceOptionState();
    private final SetOnceOptionState advancementsOption = new SetOnceOptionState();

    EntitySelectorParser(StringReader reader, boolean allowSelectors) {
        this.reader = reader;
        this.allowSelectors = allowSelectors;
    }

    private static void register(String name, Modifier modifier, Predicate<EntitySelectorParser> canUse) {
        OPTIONS.put(name, new Option(modifier, canUse));
    }

    private static CommandSyntaxException rollbackAndThrow(EntitySelectorParser parser, int start, SimpleCommandExceptionType type) {
        parser.reader.setCursor(start);
        return type.createWithContext(parser.reader);
    }

    private static CommandSyntaxException rollbackAndThrow(EntitySelectorParser parser, int start, DynamicCommandExceptionType type, String argument) {
        parser.reader.setCursor(start);
        return type.createWithContext(parser.reader, argument);
    }

    /**
     * 原版 {@code GameType.byName(name, null)}。
     */
    private static @Nullable GameMode gameModeByName(String name) {
        for (GameMode mode : GameMode.values()) {
            if (mode.name().toLowerCase(Locale.ROOT).equals(name)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * 原版 {@code EntitySelectorParser.parse}。
     */
    Selector parse() throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            if (!allowSelectors) {
                throw ERROR_SELECTORS_NOT_ALLOWED.createWithContext(reader);
            }
            reader.skip();
            parseSelector();
        } else {
            parseNameOrUUID();
        }
        return new Selector(maxResults, includesEntities, currentEntity, playerName, entityUUID, usesSelectors);
    }

    private void parseSelector() throws CommandSyntaxException {
        usesSelectors = true;
        if (!reader.canRead()) {
            throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(reader);
        }
        int start = reader.getCursor();
        char type = reader.read();
        switch (type) {
            case 'a' -> {
                maxResults = Integer.MAX_VALUE;
                includesEntities = false;
            }
            case 'e' -> {
                maxResults = Integer.MAX_VALUE;
                includesEntities = true;
            }
            case 'n' -> {
                maxResults = 1;
                includesEntities = true;
            }
            case 'p', 'r' -> {
                maxResults = 1;
                includesEntities = false;
            }
            case 's' -> {
                maxResults = 1;
                includesEntities = true;
                currentEntity = true;
            }
            default -> {
                reader.setCursor(start);
                throw ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(reader, "@" + type);
            }
        }
        if (reader.canRead() && reader.peek() == '[') {
            reader.skip();
            parseOptions();
        }
    }

    private void parseNameOrUUID() throws CommandSyntaxException {
        int start = reader.getCursor();
        String name = reader.readString();
        try {
            entityUUID = UUID.fromString(name);
            includesEntities = true;
        } catch (IllegalArgumentException e) {
            if (name.isEmpty() || name.length() > 16) {
                reader.setCursor(start);
                throw ERROR_INVALID_NAME_OR_UUID.createWithContext(reader);
            }
            includesEntities = false;
            playerName = name;
        }
        maxResults = 1;
    }

    private void parseOptions() throws CommandSyntaxException {
        reader.skipWhitespace();
        while (true) {
            if (reader.canRead() && reader.peek() != ']') {
                reader.skipWhitespace();
                int start = reader.getCursor();
                String key = reader.readString();
                Modifier modifier = getOption(key, start);
                reader.skipWhitespace();
                if (!reader.canRead() || reader.peek() != '=') {
                    reader.setCursor(start);
                    throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, key);
                }
                reader.skip();
                reader.skipWhitespace();
                modifier.handle(this);
                reader.skipWhitespace();
                if (!reader.canRead()) {
                    continue;
                }
                if (reader.peek() == ',') {
                    reader.skip();
                    continue;
                }
                if (reader.peek() != ']') {
                    throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(reader);
                }
            }
            if (reader.canRead()) {
                reader.skip();
                return;
            }
            throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(reader);
        }
    }

    /**
     * 原版 {@code EntitySelectorOptions.get}。
     */
    private Modifier getOption(String key, int start) throws CommandSyntaxException {
        Option option = OPTIONS.get(key);
        if (option == null) {
            throw rollbackAndThrow(this, start, ERROR_UNKNOWN_OPTION, key);
        }
        if (!option.canUse().test(this)) {
            throw rollbackAndThrow(this, start, ERROR_INAPPLICABLE_OPTION, key);
        }
        return option.modifier();
    }

    private boolean shouldInvertValue() {
        reader.skipWhitespace();
        if (reader.canRead() && reader.peek() == '!') {
            reader.skip();
            reader.skipWhitespace();
            return true;
        }
        return false;
    }

    private boolean isTag() {
        reader.skipWhitespace();
        if (reader.canRead() && reader.peek() == '#') {
            reader.skip();
            reader.skipWhitespace();
            return true;
        }
        return false;
    }

    /**
     * 原版 {@code EntitySelector} 中不依赖世界的属性。
     *
     * @param maxResults       最多选中的实体个数
     * @param includesEntities 是否可能选中玩家以外的实体
     * @param currentEntity    是否为 {@code @s}
     * @param playerName       按玩家名选择时的名字
     * @param entityUUID       按 UUID 选择时的 UUID
     * @param usesSelectors    是否以 {@code @} 开头
     */
    record Selector(int maxResults, boolean includesEntities, boolean currentEntity, @Nullable String playerName,
                    @Nullable UUID entityUUID, boolean usesSelectors) {
    }

    @FunctionalInterface
    private interface Modifier {
        void handle(EntitySelectorParser parser) throws CommandSyntaxException;
    }

    private record Option(Modifier modifier, Predicate<EntitySelectorParser> canUse) {
    }

    /**
     * 原版 {@code InvertableSetOptionState}。
     */
    private static final class InvertableSetOptionState {
        private Limitation state = Limitation.NONE;
        private final Set<Key> tags = new HashSet<>();

        boolean canParsePositiveElement() {
            return state == Limitation.NONE;
        }

        boolean canParseNegativeElement() {
            return state != Limitation.SINGLE;
        }

        boolean canParseAnyTag() {
            return state != Limitation.SINGLE;
        }

        boolean canParseTag(Key tag) {
            return canParseAnyTag() && !tags.contains(tag);
        }

        boolean canParseAny() {
            return state != Limitation.SINGLE;
        }

        boolean canParseElement(boolean inverted) {
            return inverted ? canParseNegativeElement() : canParsePositiveElement();
        }

        void markParsedTag(Key tag) {
            state = Limitation.MULTIPLE;
            tags.add(tag);
        }

        void markParsedElement(boolean inverted) {
            state = inverted ? Limitation.MULTIPLE : Limitation.SINGLE;
        }

        private enum Limitation {
            NONE, SINGLE, MULTIPLE
        }
    }

    /**
     * 原版 {@code SetOnceOptionState}。
     */
    private static final class SetOnceOptionState {
        private boolean hasValue;

        boolean canParse() {
            return !hasValue;
        }

        void markParsed() {
            hasValue = true;
        }
    }

    /**
     * 原版 {@code MinMaxBounds.Bounds} 从命令字符串读取的部分：{@code a}、{@code a..}、{@code ..b}、{@code a..b}。
     */
    private record Bounds<T extends Number & Comparable<T>>(Optional<T> min, Optional<T> max) {
        boolean areSwapped() {
            return min.isPresent() && max.isPresent() && min.get().compareTo(max.get()) > 0;
        }

        /**
         * 原版 {@code MinMaxBounds.Bounds.fromReader}：数字无法解析时读取器回到开始的位置。
         */
        static <T extends Number & Comparable<T>> Bounds<T> fromReader(StringReader reader, Function<String, T> converter,
                                                                         Supplier<DynamicCommandExceptionType> parseExc)
                throws CommandSyntaxException {
            if (!reader.canRead()) {
                throw ERROR_RANGE_EMPTY.createWithContext(reader);
            }
            int start = reader.getCursor();
            try {
                Optional<T> min = readNumber(reader, converter, parseExc);
                Optional<T> max;
                if (reader.canRead(2) && reader.peek() == '.' && reader.peek(1) == '.') {
                    reader.skip();
                    reader.skip();
                    max = readNumber(reader, converter, parseExc);
                } else {
                    max = min;
                }
                if (min.isEmpty() && max.isEmpty()) {
                    throw ERROR_RANGE_EMPTY.createWithContext(reader);
                }
                return new Bounds<>(min, max);
            } catch (CommandSyntaxException e) {
                reader.setCursor(start);
                throw new CommandSyntaxException(e.getType(), e.getRawMessage(), e.getInput(), start);
            }
        }

        private static <T extends Number> Optional<T> readNumber(StringReader reader, Function<String, T> converter,
                                                                 Supplier<DynamicCommandExceptionType> parseExc)
                throws CommandSyntaxException {
            int start = reader.getCursor();
            while (reader.canRead() && isAllowedInputChar(reader)) {
                reader.skip();
            }
            String number = reader.getString().substring(start, reader.getCursor());
            if (number.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(converter.apply(number));
            } catch (NumberFormatException e) {
                throw parseExc.get().createWithContext(reader, number);
            }
        }

        private static boolean isAllowedInputChar(StringReader reader) {
            char c = reader.peek();
            if (c >= '0' && c <= '9' || c == '-') {
                return true;
            }
            if (c != '.') {
                return false;
            }
            return !reader.canRead(2) || reader.peek(1) != '.';
        }
    }
}
