package io.ib67.yuanban.codec;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.BuiltInExceptionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 原版命令解析器的错误信息。原版服务端用 {@code Component.translatable} 构造错误信息，
 * {@link CommandSyntaxException#getMessage()} 取到的是 {@code assets/minecraft/lang/en_us.json} 中的英文文本；
 * 这里在代码中列出用到的文本（取自原版 26.2 服务端），按原版 {@code TranslatableContents} 的规则代入参数。
 * <p>
 * 类初始化时按原版 {@code Bootstrap} 的做法把 {@link CommandSyntaxException#BUILT_IN_EXCEPTIONS} 换成
 * {@link BrigadierExceptions}，使 Brigadier 自带的错误（例如 {@code Expected integer}）也是原版文本。
 * 这是对整个 JVM 生效的全局设置，与原版服务端相同。
 */
final class CommandMessages {
    /**
     * 原版 {@code TranslatableContents.FORMAT_PATTERN}。
     */
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static final Map<String, String> EN_US = Map.ofEntries(
            Map.entry("argument.entity.invalid", "Invalid name or UUID"),
            Map.entry("argument.entity.selector.unknown", "Unknown selector type '%s'"),
            Map.entry("argument.entity.selector.not_allowed", "Selector not allowed"),
            Map.entry("argument.entity.selector.missing", "Missing selector type"),
            Map.entry("argument.entity.options.unterminated", "Expected end of options"),
            Map.entry("argument.entity.options.valueless", "Expected value for option '%s'"),
            Map.entry("argument.entity.options.unknown", "Unknown option '%s'"),
            Map.entry("argument.entity.options.inapplicable", "Option '%s' isn't applicable here"),
            Map.entry("argument.entity.options.distance.negative", "Distance cannot be negative"),
            Map.entry("argument.entity.options.level.negative", "Level shouldn't be negative"),
            Map.entry("argument.entity.options.limit.toosmall", "Limit must be at least 1"),
            Map.entry("argument.entity.options.sort.irreversible", "Invalid or unknown sort type '%s'"),
            Map.entry("argument.entity.options.mode.invalid", "Invalid or unknown game mode '%s'"),
            Map.entry("argument.entity.options.type.invalid", "Invalid or unknown entity type '%s'"),
            Map.entry("argument.range.empty", "Expected value or range of values"),
            Map.entry("argument.range.swapped", "Min cannot be bigger than max"),
            Map.entry("argument.id.invalid", "Invalid ID"),
            Map.entry("argument.nbt.trailing", "Unexpected trailing data"),
            Map.entry("argument.nbt.expected.compound", "Expected compound tag"),
            Map.entry("arguments.nbtpath.node.invalid", "Invalid NBT path element"),
            Map.entry("arguments.nbtpath.too_deep", "Resulting NBT too deeply nested"),
            Map.entry("arguments.nbtpath.nothing_found", "Found no elements matching %s"),
            Map.entry("commands.data.modify.expected_list", "Expected a list: got %s"),
            Map.entry("commands.data.modify.invalid_index", "Invalid list index: %s"),
            Map.entry("argument.pos3d.incomplete", "Incomplete (expected 3 coordinates)"),
            Map.entry("argument.pos.mixed", "Cannot mix world & local coordinates (everything must either use ^ or not)"),
            Map.entry("argument.pos.missing.double", "Expected a coordinate"),
            Map.entry("argument.pos.missing.int", "Expected a block position"),
            Map.entry("command.trailing_data", "Trailing data found: %s"),
            Map.entry("parsing.expected", "Expected '%s'"),
            Map.entry("parsing.float.invalid", "Invalid float '%s'"),
            Map.entry("parsing.double.invalid", "Invalid double '%s'"),
            Map.entry("parsing.long.invalid", "Invalid long '%s'"),
            Map.entry("parsing.int.invalid", "Invalid integer '%s'"),
            Map.entry("parsing.bool.invalid", "Invalid boolean: expected 'true' or 'false' but found '%s'"),
            Map.entry("parsing.quote.escape", "Invalid escape sequence '\\%s' in quoted string"),
            Map.entry("argument.literal.incorrect", "Expected literal %s"),
            Map.entry("argument.long.big", "Long must not be more than %s: found %s"),
            Map.entry("argument.long.low", "Long must not be less than %s: found %s"),
            Map.entry("argument.integer.big", "Integer must not be more than %s: found %s"),
            Map.entry("argument.integer.low", "Integer must not be less than %s: found %s"),
            Map.entry("argument.float.big", "Float must not be more than %s: found %s"),
            Map.entry("argument.float.low", "Float must not be less than %s: found %s"),
            Map.entry("argument.double.big", "Double must not be more than %s: found %s"),
            Map.entry("argument.double.low", "Double must not be less than %s: found %s"),
            Map.entry("parsing.quote.expected.start", "Expected quote to start a string"),
            Map.entry("parsing.quote.expected.end", "Unclosed quoted string"),
            Map.entry("parsing.int.expected", "Expected integer"),
            Map.entry("parsing.long.expected", "Expected long"),
            Map.entry("parsing.double.expected", "Expected double"),
            Map.entry("parsing.float.expected", "Expected float"),
            Map.entry("parsing.bool.expected", "Expected boolean"),
            Map.entry("command.unknown.command", "Unknown or incomplete command. See below for error"),
            Map.entry("command.unknown.argument", "Incorrect argument for command"),
            Map.entry("command.expected.separator", "Expected whitespace to end one argument, but found trailing data"),
            Map.entry("command.exception", "Could not parse command: %s")
    );

    static {
        CommandSyntaxException.BUILT_IN_EXCEPTIONS = new BrigadierExceptions();
    }

    private CommandMessages() {
    }

    /**
     * 原版 {@code Component.translatable(key)}/{@code translatableEscape(key, args)} 作为 Brigadier 的错误信息：
     * {@link Message#getString()} 是代入参数后的英文文本，参数按 {@link String#valueOf(Object)} 转换。
     */
    static Message translatable(String key, Object... args) {
        String format = Objects.requireNonNull(EN_US.get(key), () -> "Missing translation: " + key);
        String text = format(format, args);
        return () -> text;
    }

    static SimpleCommandExceptionType simple(String key) {
        return new SimpleCommandExceptionType(translatable(key));
    }

    static DynamicCommandExceptionType dynamic(String key) {
        return new DynamicCommandExceptionType(arg -> translatable(key, arg));
    }

    /**
     * 原版 {@code TranslatableContents.decomposeTemplate}：{@code %s} 按顺序取参数，{@code %n$s} 取第 n 个参数，
     * {@code %%} 是百分号。
     */
    private static String format(String format, Object[] args) {
        var builder = new StringBuilder();
        Matcher matcher = FORMAT_PATTERN.matcher(format);
        int replacementIndex = 0;
        int current = 0;
        while (matcher.find(current)) {
            int start = matcher.start();
            int end = matcher.end();
            builder.append(format, current, start);
            String formatType = matcher.group(2);
            if ("%".equals(formatType) && "%%".equals(format.substring(start, end))) {
                builder.append('%');
            } else if ("s".equals(formatType)) {
                String possiblePositionIndex = matcher.group(1);
                int index = possiblePositionIndex != null ? Integer.parseInt(possiblePositionIndex) - 1 : replacementIndex++;
                builder.append(String.valueOf(args[index]));
            } else {
                throw new IllegalStateException("Unsupported format: '" + format.substring(start, end) + "'");
            }
            current = end;
        }
        builder.append(format, current, format.length());
        return builder.toString();
    }

    /**
     * 原版 {@code net.minecraft.commands.BrigadierExceptions}：Brigadier 自带错误的原版文本。
     */
    private static final class BrigadierExceptions implements BuiltInExceptionProvider {
        private static final Dynamic2CommandExceptionType DOUBLE_TOO_SMALL = new Dynamic2CommandExceptionType(
                (found, min) -> translatable("argument.double.low", min, found));
        private static final Dynamic2CommandExceptionType DOUBLE_TOO_BIG = new Dynamic2CommandExceptionType(
                (found, max) -> translatable("argument.double.big", max, found));
        private static final Dynamic2CommandExceptionType FLOAT_TOO_SMALL = new Dynamic2CommandExceptionType(
                (found, min) -> translatable("argument.float.low", min, found));
        private static final Dynamic2CommandExceptionType FLOAT_TOO_BIG = new Dynamic2CommandExceptionType(
                (found, max) -> translatable("argument.float.big", max, found));
        private static final Dynamic2CommandExceptionType INTEGER_TOO_SMALL = new Dynamic2CommandExceptionType(
                (found, min) -> translatable("argument.integer.low", min, found));
        private static final Dynamic2CommandExceptionType INTEGER_TOO_BIG = new Dynamic2CommandExceptionType(
                (found, max) -> translatable("argument.integer.big", max, found));
        private static final Dynamic2CommandExceptionType LONG_TOO_SMALL = new Dynamic2CommandExceptionType(
                (found, min) -> translatable("argument.long.low", min, found));
        private static final Dynamic2CommandExceptionType LONG_TOO_BIG = new Dynamic2CommandExceptionType(
                (found, max) -> translatable("argument.long.big", max, found));
        private static final DynamicCommandExceptionType LITERAL_INCORRECT = dynamic("argument.literal.incorrect");
        private static final SimpleCommandExceptionType READER_EXPECTED_START_OF_QUOTE = simple("parsing.quote.expected.start");
        private static final SimpleCommandExceptionType READER_EXPECTED_END_OF_QUOTE = simple("parsing.quote.expected.end");
        private static final DynamicCommandExceptionType READER_INVALID_ESCAPE = dynamic("parsing.quote.escape");
        private static final DynamicCommandExceptionType READER_INVALID_BOOL = dynamic("parsing.bool.invalid");
        private static final DynamicCommandExceptionType READER_INVALID_INT = dynamic("parsing.int.invalid");
        private static final SimpleCommandExceptionType READER_EXPECTED_INT = simple("parsing.int.expected");
        private static final DynamicCommandExceptionType READER_INVALID_LONG = dynamic("parsing.long.invalid");
        private static final SimpleCommandExceptionType READER_EXPECTED_LONG = simple("parsing.long.expected");
        private static final DynamicCommandExceptionType READER_INVALID_DOUBLE = dynamic("parsing.double.invalid");
        private static final SimpleCommandExceptionType READER_EXPECTED_DOUBLE = simple("parsing.double.expected");
        private static final DynamicCommandExceptionType READER_INVALID_FLOAT = dynamic("parsing.float.invalid");
        private static final SimpleCommandExceptionType READER_EXPECTED_FLOAT = simple("parsing.float.expected");
        private static final SimpleCommandExceptionType READER_EXPECTED_BOOL = simple("parsing.bool.expected");
        private static final DynamicCommandExceptionType READER_EXPECTED_SYMBOL = dynamic("parsing.expected");
        private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_COMMAND = simple("command.unknown.command");
        private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_ARGUMENT = simple("command.unknown.argument");
        private static final SimpleCommandExceptionType DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR = simple("command.expected.separator");
        private static final DynamicCommandExceptionType DISPATCHER_PARSE_EXCEPTION = dynamic("command.exception");

        @Override
        public Dynamic2CommandExceptionType doubleTooLow() {
            return DOUBLE_TOO_SMALL;
        }

        @Override
        public Dynamic2CommandExceptionType doubleTooHigh() {
            return DOUBLE_TOO_BIG;
        }

        @Override
        public Dynamic2CommandExceptionType floatTooLow() {
            return FLOAT_TOO_SMALL;
        }

        @Override
        public Dynamic2CommandExceptionType floatTooHigh() {
            return FLOAT_TOO_BIG;
        }

        @Override
        public Dynamic2CommandExceptionType integerTooLow() {
            return INTEGER_TOO_SMALL;
        }

        @Override
        public Dynamic2CommandExceptionType integerTooHigh() {
            return INTEGER_TOO_BIG;
        }

        @Override
        public Dynamic2CommandExceptionType longTooLow() {
            return LONG_TOO_SMALL;
        }

        @Override
        public Dynamic2CommandExceptionType longTooHigh() {
            return LONG_TOO_BIG;
        }

        @Override
        public DynamicCommandExceptionType literalIncorrect() {
            return LITERAL_INCORRECT;
        }

        @Override
        public SimpleCommandExceptionType readerExpectedStartOfQuote() {
            return READER_EXPECTED_START_OF_QUOTE;
        }

        @Override
        public SimpleCommandExceptionType readerExpectedEndOfQuote() {
            return READER_EXPECTED_END_OF_QUOTE;
        }

        @Override
        public DynamicCommandExceptionType readerInvalidEscape() {
            return READER_INVALID_ESCAPE;
        }

        @Override
        public DynamicCommandExceptionType readerInvalidBool() {
            return READER_INVALID_BOOL;
        }

        @Override
        public DynamicCommandExceptionType readerInvalidInt() {
            return READER_INVALID_INT;
        }

        @Override
        public SimpleCommandExceptionType readerExpectedInt() {
            return READER_EXPECTED_INT;
        }

        @Override
        public DynamicCommandExceptionType readerInvalidLong() {
            return READER_INVALID_LONG;
        }

        @Override
        public SimpleCommandExceptionType readerExpectedLong() {
            return READER_EXPECTED_LONG;
        }

        @Override
        public DynamicCommandExceptionType readerInvalidDouble() {
            return READER_INVALID_DOUBLE;
        }

        @Override
        public SimpleCommandExceptionType readerExpectedDouble() {
            return READER_EXPECTED_DOUBLE;
        }

        @Override
        public DynamicCommandExceptionType readerInvalidFloat() {
            return READER_INVALID_FLOAT;
        }

        @Override
        public SimpleCommandExceptionType readerExpectedFloat() {
            return READER_EXPECTED_FLOAT;
        }

        @Override
        public SimpleCommandExceptionType readerExpectedBool() {
            return READER_EXPECTED_BOOL;
        }

        @Override
        public DynamicCommandExceptionType readerExpectedSymbol() {
            return READER_EXPECTED_SYMBOL;
        }

        @Override
        public SimpleCommandExceptionType dispatcherUnknownCommand() {
            return DISPATCHER_UNKNOWN_COMMAND;
        }

        @Override
        public SimpleCommandExceptionType dispatcherUnknownArgument() {
            return DISPATCHER_UNKNOWN_ARGUMENT;
        }

        @Override
        public SimpleCommandExceptionType dispatcherExpectedArgumentSeparator() {
            return DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR;
        }

        @Override
        public DynamicCommandExceptionType dispatcherParseException() {
            return DISPATCHER_PARSE_EXCEPTION;
        }
    }
}
