package io.ib67.yuanban.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.text.BlockNBTComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.DataComponentValue;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.adventure.MinestomDataComponentValue;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.registry.RegistryKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static io.ib67.yuanban.codec.CodecTestSupport.json;
import static io.ib67.yuanban.codec.CodecTestSupport.registryOps;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@link ComponentSerialization}：期望的结果和错误文本取自原版 26.2 服务端对同一输入的解码。
 */
class ComponentSerializationTest {
    private static final Codec<Component> CODEC = ComponentSerialization.CODEC;

    /**
     * 输入是对象且最终按对象解码失败时，原版的错误文本：先尝试字符串和列表，再尝试对象。
     */
    private static String objectError(String compactJson, String inner) {
        return "Failed to parse either. First: Failed to parse either. First: Not a string: " + compactJson
                + "; Second: Not a json array: " + compactJson + "; Second: " + inner;
    }

    @Test
    void stringIsLiteral() {
        assertEquals(Component.text("hello"), decode(CODEC, "\"hello\""));
        assertEquals("\"hello\"", encode(CODEC, Component.text("hello")));
        assertEquals(Component.text(""), decode(CODEC, "\"\""));
    }

    @Test
    void emptyListIsRejected() {
        assertEquals("Failed to parse either. First: Failed to parse either. First: Not a string: []; "
                + "Second: List must have contents; Second: Not a JSON object: []", error(CODEC, "[]"));
    }

    @Test
    void listUsesFirstElementAsBase() {
        assertEquals(Component.text("a").append(Component.text("b", NamedTextColor.RED)),
                decode(CODEC, "[\"a\", {\"text\": \"b\", \"color\": \"red\"}]"));
        // 第一个元素的样式由其余元素继承，原版 createFromList
        Component bold = decode(CODEC, "[{\"text\":\"a\",\"bold\":true},\"b\"]");
        assertEquals(Component.text("a").decoration(TextDecoration.BOLD, true).append(Component.text("b")), bold);
        assertEquals("{\"text\":\"a\",\"extra\":[\"b\"],\"bold\":true}", encode(CODEC, bold));
    }

    @Test
    void listElementErrors() {
        String elementError = "Failed to parse either. First: Failed to parse either. First: Not a string: 5; "
                + "Second: Not a json array: 5; Second: Not a JSON object: 5";
        assertEquals("Failed to parse either. First: Failed to parse either. First: Not a string: [5]; Second: " + elementError
                + "; List must have contents; Second: Not a JSON object: [5]", error(CODEC, "[5]"));
        assertEquals(elementError, error(CODEC, "[\"a\", 5]"));
        assertEquals(elementError + "; List must have contents", error(CODEC, "{\"text\": \"a\", \"extra\": [5]}"));
        assertEquals(elementError, error(CODEC, "{\"text\": \"a\", \"extra\": [\"b\", 5]}"));
        assertEquals(elementError, error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_text\", \"value\": 5}}"));
    }

    /**
     * 用 Minestom 自己的 {@link Transcoder#JSON} 解码 {@code Codec.COMPONENT}。
     */
    private static Result<Component> minestomDecode(String input) {
        return net.minestom.server.codec.Codec.COMPONENT.decode(Transcoder.JSON, json(input));
    }

    @Test
    void minestomComponentCodecDiffersFromVanilla() {
        // M22 的依据：Minestom 的 Codec.COMPONENT 接受空列表，并把列表构造成空组件加子组件
        Codec<Component> minestom = MinestomCodecs.adapt(net.minestom.server.codec.Codec.COMPONENT);
        assertEquals(Component.empty(), decode(minestom, "[]"));
        assertEquals(Component.empty().children(List.of(Component.text("a").decoration(TextDecoration.BOLD, true), Component.text("b"))),
                decode(minestom, "[{\"text\":\"a\",\"bold\":true},\"b\"]"));
        // 下面的输入原版都报告错误，或者解码出其它结果
        assertEquals(new Result.Ok<>(Component.text("a")), minestomDecode("{\"text\": \"a\", \"extra\": []}"));
        assertEquals(new Result.Ok<>(Component.text("a")), minestomDecode("{\"type\": \"nope\", \"text\": \"a\"}"));
        assertEquals(new Result.Error<>("NBTComponent not yet supported"),
                minestomDecode("{\"type\": \"nbt\", \"nbt\": \"a\", \"storage\": \"foo:bar\"}"));
        assertEquals(new Result.Ok<>(Component.translatable("k", Component.text("1"), Component.text("true"))),
                minestomDecode("{\"translate\": \"k\", \"with\": [1, true]}"));
        assertEquals(new Result.Ok<>(Component.text("a").clickEvent(ClickEvent.openFile("x"))),
                minestomDecode("{\"text\": \"a\", \"click_event\": {\"action\": \"open_file\", \"path\": \"x\"}}"));
        assertEquals(new Result.Ok<>(Component.text("a").clickEvent(ClickEvent.openUrl("ftp://example.com"))),
                minestomDecode("{\"text\": \"a\", \"click_event\": {\"action\": \"open_url\", \"url\": \"ftp://example.com\"}}"));
        assertEquals(new Result.Ok<>(Component.text("a").hoverEvent(HoverEvent.showItem(Key.key("minecraft:apple"), 1))),
                minestomDecode("{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:apple\", "
                        + "\"components\": {\"minecraft:max_stack_size\": 5}}}"));
        Component custom = ((Result.Ok<Component>) minestomDecode(
                "{\"text\": \"a\", \"click_event\": {\"action\": \"custom\", \"id\": \"foo:bar\"}}")).value();
        ClickEvent.Payload.Custom payload = (ClickEvent.Payload.Custom) custom.clickEvent().payload();
        assertEquals(CompoundBinaryTag.empty(), MinestomAdventure.unwrapNbt(payload.nbt()));
    }

    @Test
    void extraIsNonEmptyList() {
        assertEquals("List must have contents", error(CODEC, "{\"text\": \"a\", \"extra\": []}"));
        Component nested = decode(CODEC, "{\"text\": \"a\", \"extra\": [\"b\", {\"translate\": \"x\"}]}");
        assertEquals(Component.text("a").append(Component.text("b")).append(Component.translatable("x")), nested);
        assertEquals("{\"text\":\"a\",\"extra\":[\"b\",{\"translate\":\"x\"}]}", encode(CODEC, nested));
        Component styled = decode(CODEC, "{\"text\": \"a\", \"color\": \"red\", \"extra\": [{\"text\": \"b\", \"bold\": false}]}");
        assertEquals(Component.text("a", NamedTextColor.RED).append(Component.text("b").decoration(TextDecoration.BOLD, false)), styled);
        assertEquals("{\"text\":\"a\",\"extra\":[{\"text\":\"b\",\"bold\":false}],\"color\":\"red\"}", encode(CODEC, styled));
        assertEquals(Component.text("").append(Component.text("a")), decode(CODEC, "{\"text\": \"\", \"extra\": [\"a\"]}"));
    }

    @Test
    void translatableArguments() {
        Component component = decode(CODEC, "{\"translate\": \"chat.type.text\", \"with\": [\"a\", 1, true, {\"text\": \"b\"}, "
                + "{\"text\":\"c\",\"italic\":true}, 1.5, [\"x\", \"y\"]]}");
        assertEquals(Component.translatable().key("chat.type.text").arguments(List.of(
                TranslationArgument.component(Component.text("a")),
                TranslationArgument.numeric((byte) 1),
                TranslationArgument.bool(true),
                TranslationArgument.component(Component.text("b")),
                TranslationArgument.component(Component.text("c").decoration(TextDecoration.ITALIC, true)),
                TranslationArgument.numeric(1.5F),
                TranslationArgument.component(Component.text("x").append(Component.text("y")))
        )).build(), component);
        assertEquals("{\"translate\":\"chat.type.text\",\"with\":[\"a\",1,true,\"b\",{\"text\":\"c\",\"italic\":true},1.5,"
                + "{\"text\":\"x\",\"extra\":[\"y\"]}]}", encode(CODEC, component));
        assertEquals(Component.translatable("k"), decode(CODEC, "{\"translate\": \"k\", \"with\": []}"));
        assertEquals("{\"translate\":\"k\"}", encode(CODEC, Component.translatable("k")));
    }

    @Test
    void translatableFallbackIsLenient() {
        assertEquals(Component.translatable("k"), decode(CODEC, "{\"translate\": \"k\", \"fallback\": 5}"));
        assertEquals(Component.translatable().key("k").fallback("f").build(), decode(CODEC, "{\"translate\": \"k\", \"fallback\": \"f\"}"));
    }

    @Test
    void translatableArgumentErrors() {
        assertEquals("Failed to parse either. First: This value needs to be parsed as component; Second: Failed to parse either. "
                        + "First: Failed to parse either. First: Not a string: null; Second: Not a json array: null; Second: Not a JSON object: null",
                error(CODEC, "{\"type\": \"translatable\", \"translate\": \"k\", \"with\": [null]}"));
        assertEquals("Failed to parse either. First: This value needs to be parsed as component; Second: Failed to parse either. "
                        + "First: Failed to parse either. First: Not a string: {\"a\":1}; Second: Not a json array: {\"a\":1}; "
                        + "Second: No matching codec found",
                error(CODEC, "{\"type\": \"translatable\", \"translate\": \"k\", \"with\": [{\"a\":1}]}"));
        // 没有 type 字段时逐个尝试内容类型，内部的错误不出现在结果中
        assertEquals(objectError("{\"translate\":\"k\",\"with\":[null]}", "No matching codec found"),
                error(CODEC, "{\"translate\": \"k\", \"with\": [null]}"));
    }

    @Test
    void contentTypeInference() {
        assertEquals(objectError("{\"foo\":\"bar\"}", "No matching codec found"), error(CODEC, "{\"foo\": \"bar\"}"));
        assertEquals(objectError("{\"text\":5}", "No matching codec found"), error(CODEC, "{\"text\": 5}"));
        assertEquals("Failed to parse either. First: Failed to parse either. First: Not a string: 5; Second: Not a json array: 5; "
                + "Second: Not a JSON object: 5", error(CODEC, "5"));
        // 按 text、translatable、keybind 的顺序尝试
        assertEquals(Component.text("a"), decode(CODEC, "{\"keybind\": \"key.jump\", \"text\": \"a\"}"));
        assertEquals(Component.translatable("k"), decode(CODEC, "{\"translate\": \"k\", \"keybind\": \"a\"}"));
        assertEquals(Component.text("a"), decode(CODEC, "{\"type\": \"text\", \"text\": \"a\"}"));
        assertEquals(objectError("{\"type\":\"text\",\"translate\":\"a\"}", "No key text in MapLike[{\"type\":\"text\",\"translate\":\"a\"}]"),
                error(CODEC, "{\"type\": \"text\", \"translate\": \"a\"}"));
        assertEquals(objectError("{\"type\":\"nope\",\"text\":\"a\"}", "Unknown element id: nope"),
                error(CODEC, "{\"type\": \"nope\", \"text\": \"a\"}"));
        assertEquals(objectError("{\"type\":\"keybind\"}", "No key keybind in MapLike[{\"type\":\"keybind\"}]"),
                error(CODEC, "{\"type\": \"keybind\"}"));
    }

    @Test
    void keybindAndScore() {
        assertEquals(Component.keybind("key.jump"), decode(CODEC, "{\"keybind\": \"key.jump\"}"));
        assertEquals(Component.score("@p", "obj"), decode(CODEC, "{\"score\": {\"name\": \"@p\", \"objective\": \"obj\"}}"));
        // 不是合法选择器的名字按普通字符串读取
        assertEquals(Component.score("@x", "obj"), decode(CODEC, "{\"score\": {\"name\": \"@x\", \"objective\": \"obj\"}}"));
        assertEquals("{\"score\":{\"name\":\"@x\",\"objective\":\"obj\"}}", encode(CODEC, Component.score("@x", "obj")));
        assertEquals(objectError("{\"type\":\"score\",\"score\":{\"name\":5,\"objective\":\"obj\"}}",
                        "Failed to parse either. First: Not a string: 5; Second: Not a string: 5"),
                error(CODEC, "{\"type\": \"score\", \"score\": {\"name\": 5, \"objective\": \"obj\"}}"));
        assertEquals(objectError("{\"type\":\"score\",\"score\":{\"name\":\"a\"}}", "No key objective in MapLike[{\"name\":\"a\"}]"),
                error(CODEC, "{\"type\": \"score\", \"score\": {\"name\": \"a\"}}"));
    }

    @Test
    void selectorsAccepted() {
        for (String selector : List.of("@e[type=zombie,limit=2]", "@e[gamemode=!creative,gamemode=!survival]",
                "@e[type=!zombie,type=!skeleton,type=#minecraft:skeletons]", "@e[limit=1,]", "@e[x=1.5,dx=2,y_rotation=-90..90]",
                "@e[nbt={a:1b}]", "@e[scores={a=1..2,b=3}]", "@e[advancements={minecraft:story/root=true,foo:bar={crit=false}}]",
                "@e[tag=!a,tag=b]", "@a[limit=1,sort=random,team=!red]", "00000001-0000-0002-0000-000300000004", "\"quoted name\"")) {
            String input = "{\"type\": \"selector\", \"selector\": " + json("\"" + selector.replace("\"", "\\\"") + "\"") + "}";
            assertEquals(Component.selector(selector), decode(CODEC, input), input);
        }
        Component withSeparator = decode(CODEC, "{\"selector\": \"Steve\", \"separator\": {\"text\": \", \"}}");
        assertEquals(Component.selector("Steve", Component.text(", ")), withSeparator);
        assertEquals("{\"selector\":\"Steve\",\"separator\":\", \"}", encode(CODEC, withSeparator));
    }

    @Test
    void selectorErrors() {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("@x", "Unknown selector type '@x' at position 1: @<--[HERE]");
        errors.put("@", "Missing selector type at position 1: @<--[HERE]");
        errors.put("@e[foo=1]", "Unknown option 'foo' at position 3: @e[<--[HERE]");
        errors.put("@e[limit=0]", "Limit must be at least 1 at position 9: @e[limit=<--[HERE]");
        errors.put("@s[limit=1]", "Option 'limit' isn't applicable here at position 3: @s[<--[HERE]");
        errors.put("@e[type=minecraft:nope]", "Invalid or unknown entity type 'minecraft:nope' at position 8: @e[type=<--[HERE]");
        errors.put("@e[type=Foo]", "Invalid or unknown entity type 'minecraft:' at position 8: @e[type=<--[HERE]");
        errors.put("@e[distance=-1]", "Distance cannot be negative at position 12: ...[distance=<--[HERE]");
        errors.put("@e[distance=5..1]", "Min cannot be bigger than max at position 12: ...[distance=<--[HERE]");
        errors.put("@e[distance=a]", "Expected value or range of values at position 12: ...[distance=<--[HERE]");
        errors.put("@e[level=..]", "Expected value or range of values at position 9: @e[level=<--[HERE]");
        errors.put("@e[level=-1]", "Level shouldn't be negative at position 9: @e[level=<--[HERE]");
        errors.put("@e[sort=foo]", "Invalid or unknown sort type 'foo' at position 8: @e[sort=<--[HERE]");
        errors.put("@e[gamemode=creativ]", "Invalid or unknown game mode 'creativ' at position 12: ...[gamemode=<--[HERE]");
        errors.put("@e[gamemode=creative,gamemode=survival]", "Option 'gamemode' isn't applicable here at position 21: ...=creative,<--[HERE]");
        errors.put("@e[type=zombie,type=skeleton]", "Option 'type' isn't applicable here at position 15: ...pe=zombie,<--[HERE]");
        errors.put("@e[type=#minecraft:skeletons,type=#minecraft:skeletons]",
                "Option 'type' isn't applicable here at position 34: ...tons,type=<--[HERE]");
        errors.put("@e[name=a,name=b]", "Option 'name' isn't applicable here at position 10: @e[name=a,<--[HERE]");
        errors.put("@e[limit=1", "Expected end of options at position 10: @e[limit=1<--[HERE]");
        errors.put("@e[limit=1 x]", "Expected end of options at position 11: ...e[limit=1 <--[HERE]");
        errors.put("@e[limit]", "Expected value for option 'limit' at position 3: @e[<--[HERE]");
        errors.put("@e[limit=x]", "Expected integer at position 9: @e[limit=<--[HERE]");
        errors.put("@e[x=a]", "Expected double at position 5: @e[x=<--[HERE]");
        errors.put("@e[nbt=[1]]", "Expected compound tag at position 10: @e[nbt=[1]<--[HERE]");
        errors.put("@e[scores={a=2..1}]", "Min cannot be bigger than max at position 13: ...scores={a=<--[HERE]");
        errors.put("@e[scores=a]", "Expected '{' at position 10: @e[scores=<--[HERE]");
        errors.put("@e[advancements={foo:bar={crit=maybe}}]",
                "Invalid boolean: expected 'true' or 'false' but found 'maybe' at position 31: ...bar={crit=<--[HERE]");
        errors.put("@e[predicate=Foo:bar]", "Expected end of options at position 13: ...predicate=<--[HERE]");
        errors.put("aVeryLongPlayerNameThatIsTooLong", "Invalid name or UUID at position 0: <--[HERE]");
        errors.put("", "Invalid name or UUID at position 0: <--[HERE]");
        errors.put("@e x", "Trailing data found: @e[ x]");
        errors.forEach((selector, message) -> {
            String compact = "{\"type\":\"selector\",\"selector\":\"" + selector + "\"}";
            assertEquals(objectError(compact, "Invalid selector component: " + selector + ": " + message),
                    error(CODEC, "{\"type\": \"selector\", \"selector\": \"" + selector + "\"}"), selector);
        });
        // 分隔符严格解码
        assertEquals("Failed to parse either. First: Failed to parse either. First: Not a string: 5; Second: Not a json array: 5; "
                        + "Second: Not a JSON object: 5",
                error(CODEC, "{\"type\": \"selector\", \"selector\": \"@p\", \"separator\": 5}"));
    }

    @Test
    void nbtContents() {
        assertEquals(Component.entityNBT().nbtPath("a.b[0]").selector("@s").build(),
                decode(CODEC, "{\"nbt\": \"a.b[0]\", \"entity\": \"@s\"}"));
        assertEquals(Component.entityNBT().nbtPath("a").selector("@s").build(),
                decode(CODEC, "{\"nbt\": \"a\", \"source\": \"entity\", \"entity\": \"@s\"}"));
        Component block = decode(CODEC, "{\"nbt\": \"a.b[0]\", \"block\": \"~ ~1 ~\"}");
        assertEquals(Component.blockNBT().nbtPath("a.b[0]").pos(BlockNBTComponent.WorldPos.worldPos(
                BlockNBTComponent.WorldPos.Coordinate.relative(0), BlockNBTComponent.WorldPos.Coordinate.relative(1),
                BlockNBTComponent.WorldPos.Coordinate.relative(0))).build(), block);
        assertEquals(Component.blockNBT().nbtPath("a.b[0]").pos(BlockNBTComponent.LocalPos.localPos(0, 0, 1)).build(),
                decode(CODEC, "{\"nbt\": \"a.b[0]\", \"block\": \"^ ^ ^1\"}"));
        Component storage = decode(CODEC, "{\"nbt\": \"a[{b:1}].c{d:\\\"e\\\"}\", \"storage\": \"foo:bar\"}");
        assertEquals(Component.storageNBT().nbtPath("a[{b:1}].c{d:\"e\"}").storage(Key.key("foo:bar")).build(), storage);
        assertEquals("{\"nbt\":\"a[{b:1}].c{d:\\\"e\\\"}\",\"storage\":\"foo:bar\"}", encode(CODEC, storage));
        assertEquals(Component.storageNBT().nbtPath("").storage(Key.key("foo:bar")).build(),
                decode(CODEC, "{\"type\": \"nbt\", \"nbt\": \"\", \"storage\": \"foo:bar\"}"));
        // interpret、plain、separator 宽松解码
        assertEquals(Component.storageNBT().nbtPath("a").storage(Key.key("foo:bar")).build(),
                decode(CODEC, "{\"nbt\": \"a\", \"storage\": \"foo:bar\", \"interpret\": \"x\", \"separator\": 5}"));
        assertEquals(Component.storageNBT().nbtPath("a").plain(true).storage(Key.key("foo:bar")).build(),
                decode(CODEC, "{\"nbt\": \"a\", \"storage\": \"foo:bar\", \"plain\": true}"));
    }

    @Test
    void nbtContentErrors() {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a.b[0]\",\"storage\":\"foo:bar\",\"interpret\":true,\"plain\":true}",
                "'interpret' and 'plain' flags can't be both on");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a..b\",\"storage\":\"foo:bar\"}",
                "Invalid NBT path: a..b: Invalid NBT path element at position 2: a.<--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a b\",\"storage\":\"foo:bar\"}", "Invalid NBT path: a b: Trailing data found: a[ b]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"{a:1}{b:2}\",\"storage\":\"foo:bar\"}",
                "Invalid NBT path: {a:1}{b:2}: Invalid NBT path element at position 5: {a:1}<--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a[x]\",\"storage\":\"foo:bar\"}",
                "Invalid NBT path: a[x]: Expected integer at position 2: a[<--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\"}", "No matching codec found");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"nope\",\"entity\":\"@s\"}", "Unknown element id: nope");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"entity\":\"@s\"}",
                "No key block in MapLike[{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"entity\":\"@s\"}]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"entity\",\"entity\":\"@x\"}",
                "Invalid selector component: @x: Unknown selector type '@x' at position 1: @<--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"block\":\"1 2\"}",
                "Invalid coordinates path: 1 2: Incomplete (expected 3 coordinates) at position 0: <--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"block\":\"1 ^ 2\"}",
                "Invalid coordinates path: 1 ^ 2: Cannot mix world & local coordinates (everything must either use ^ or not) at position 2: 1 <--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"block\":\"^1 2 3\"}",
                "Invalid coordinates path: ^1 2 3: Cannot mix world & local coordinates (everything must either use ^ or not) at position 0: <--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"block\":\"1.5 2 3\"}",
                "Invalid coordinates path: 1.5 2 3: Invalid integer '1.5' at position 0: <--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"block\":\"~ ~ ~ ~\"}",
                "Invalid coordinates path: ~ ~ ~ ~: Trailing data found: ~ ~ ~[ ~]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"block\":\"\"}",
                "Invalid coordinates path: : Expected a block position at position 0: <--[HERE]");
        errors.put("{\"type\":\"nbt\",\"nbt\":\"a\",\"source\":\"block\",\"block\":\"^ ^\"}",
                "Invalid coordinates path: ^ ^: Incomplete (expected 3 coordinates) at position 0: <--[HERE]");
        errors.forEach((input, message) -> assertEquals(objectError(input, message), error(CODEC, input), input));
    }

    @Test
    void fractionalRelativeBlockPositionCannotBeRepresented() {
        // 原版接受 ~0.5，adventure 的世界坐标只能是整数
        assertEquals("Block position ~0.5 ~1 ~ has a relative coordinate that is not a whole number, which adventure cannot represent",
                error(CODEC, "{\"nbt\": \"a.b[0]\", \"block\": \"~0.5 ~1 ~\"}"));
    }

    @Test
    void objectContents() {
        assertEquals(Component.object().contents(ObjectContents.sprite(Key.key("minecraft:blocks"), Key.key("minecraft:item/apple"))).build(),
                decode(CODEC, "{\"object\": \"atlas\", \"sprite\": \"item/apple\"}"));
        Component items = decode(CODEC, "{\"sprite\": \"item/apple\", \"atlas\": \"minecraft:items\"}");
        assertEquals(Component.object().contents(ObjectContents.sprite(Key.key("minecraft:items"), Key.key("minecraft:item/apple"))).build(), items);
        assertEquals("{\"atlas\":\"minecraft:items\",\"sprite\":\"minecraft:item/apple\"}", encode(CODEC, items));
        assertEquals("{\"sprite\":\"minecraft:item/apple\"}", encode(CODEC,
                decode(CODEC, "{\"object\": \"atlas\", \"sprite\": \"item/apple\"}")));
        assertEquals(Component.object().contents(ObjectContents.sprite(Key.key("minecraft:blocks"), Key.key("minecraft:item/apple")))
                .fallback(Component.text("x")).build(), decode(CODEC, "{\"type\": \"object\", \"sprite\": \"item/apple\", \"fallback\": \"x\"}"));
        assertEquals(Component.object().contents(ObjectContents.playerHead().name("Steve").build()).build(),
                decode(CODEC, "{\"player\": \"Steve\"}"));
        assertEquals(Component.object().contents(ObjectContents.playerHead().name("Steve").hat(false).build()).build(),
                decode(CODEC, "{\"player\": {\"name\": \"Steve\"}, \"hat\": false}"));
        for (String input : List.of("{\"type\":\"object\",\"player\":\"Ste ve\"}", "{\"type\":\"object\",\"player\":\"aaaaaaaaaaaaaaaaaaaa\"}",
                "{\"type\":\"object\",\"player\":{\"name\":\"Ste ve\"}}", "{\"type\":\"object\"}")) {
            assertEquals(objectError(input, "No matching codec found"), error(CODEC, input), input);
        }
        assertEquals(objectError("{\"type\":\"object\",\"object\":\"player\",\"sprite\":\"item/apple\"}",
                        "No key player in MapLike[{\"type\":\"object\",\"object\":\"player\",\"sprite\":\"item/apple\"}]"),
                error(CODEC, "{\"type\": \"object\", \"object\": \"player\", \"sprite\": \"item/apple\"}"));
    }

    @Test
    void playerSkinPatchCannotBeRepresented() {
        // 原版接受披风，adventure 的玩家头像只保存皮肤
        String message = error(CODEC, "{\"type\": \"object\", \"player\": {\"name\": \"Steve\", \"cape\": \"minecraft:cape\"}}");
        assertEquals(true, message.startsWith("Player profile skin patch with cape, elytra or model cannot be represented by adventure: "), message);
    }

    @Test
    void textColors() {
        // 原版保留颜色的写法；adventure 中与命名颜色数值相同的颜色总是命名颜色
        Component hex = decode(CODEC, "{\"text\": \"a\", \"color\": \"#FF5555\"}");
        assertEquals(Component.text("a", NamedTextColor.RED), hex);
        assertEquals("{\"text\":\"a\",\"color\":\"red\"}", encode(CODEC, hex));
        assertEquals(Component.text("a", net.kyori.adventure.text.format.TextColor.color(0x123456)),
                decode(CODEC, "{\"text\": \"a\", \"color\": \"#123456\"}"));
        assertEquals("{\"text\":\"a\",\"color\":\"#123456\"}", encode(CODEC,
                Component.text("a", net.kyori.adventure.text.format.TextColor.color(0x123456))));
        assertEquals("Invalid color value: #GG0000", error(CODEC, "{\"text\": \"a\", \"color\": \"#GG0000\"}"));
        assertEquals("Color value out of range: #1000000", error(CODEC, "{\"text\": \"a\", \"color\": \"#1000000\"}"));
        assertEquals("Color value out of range: #-1", error(CODEC, "{\"text\": \"a\", \"color\": \"#-1\"}"));
        assertEquals("Invalid color name: rainbow", error(CODEC, "{\"text\": \"a\", \"color\": \"rainbow\"}"));
    }

    @Test
    void styleFields() {
        assertEquals(Component.text("a").shadowColor(ShadowColor.shadowColor(-33024)),
                decode(CODEC, "{\"text\": \"a\", \"shadow_color\": [1.0, 0.5, 0.0, 1.0]}"));
        assertEquals("{\"text\":\"a\",\"shadow_color\":-33024}",
                encode(CODEC, decode(CODEC, "{\"text\": \"a\", \"shadow_color\": [1.0, 0.5, 0.0, 1.0]}")));
        assertEquals(Component.text("a").shadowColor(ShadowColor.shadowColor(-16777216)),
                decode(CODEC, "{\"text\": \"a\", \"shadow_color\": -16777216}"));
        assertEquals("Failed to parse either. First: Not a number: [1.0,0.5,0.0]; Second: Input is not a list of 4 elements",
                error(CODEC, "{\"text\": \"a\", \"shadow_color\": [1.0, 0.5, 0.0]}"));
        assertEquals("Failed to parse either. First: Not a number: \"x\"; Second: Not a json array: \"x\"",
                error(CODEC, "{\"text\": \"a\", \"shadow_color\": \"x\"}"));
        assertEquals(Component.text("a").font(Key.key("minecraft:alt")), decode(CODEC, "{\"text\": \"a\", \"font\": \"minecraft:alt\"}"));
        assertEquals("Not a valid resource location: Bad Non [a-z0-9/._-] character in path of location: minecraft:Bad",
                error(CODEC, "{\"text\": \"a\", \"font\": \"Bad\"}"));
        assertEquals(Component.text("a").insertion("i"), decode(CODEC, "{\"text\": \"a\", \"insertion\": \"i\"}"));
        assertEquals("Not a string: 5", error(CODEC, "{\"text\": \"a\", \"insertion\": 5}"));
        assertEquals("Not a boolean: \"yes\"", error(CODEC, "{\"text\": \"a\", \"bold\": \"yes\"}"));
        assertEquals(Component.text("a").decorations(Map.of(TextDecoration.BOLD, TextDecoration.State.TRUE,
                        TextDecoration.ITALIC, TextDecoration.State.FALSE, TextDecoration.UNDERLINED, TextDecoration.State.TRUE,
                        TextDecoration.STRIKETHROUGH, TextDecoration.State.FALSE, TextDecoration.OBFUSCATED, TextDecoration.State.TRUE)),
                decode(CODEC, "{\"text\": \"a\", \"bold\": true, \"italic\": false, \"underlined\": true, \"strikethrough\": false, \"obfuscated\": true}"));
    }

    @Test
    void clickEvents() {
        assertEquals(Component.text("a").clickEvent(ClickEvent.openUrl("https://example.com")),
                decode(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"open_url\", \"url\": \"https://example.com\"}}"));
        assertEquals("{\"text\":\"a\",\"click_event\":{\"url\":\"https://example.com\",\"action\":\"open_url\"}}",
                encode(CODEC, Component.text("a").clickEvent(ClickEvent.openUrl("https://example.com"))));
        assertEquals("Unsupported protocol in URI: ftp://example.com: ftp://example.com",
                error(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"open_url\", \"url\": \"ftp://example.com\"}}"));
        assertEquals("Missing protocol in URI: example.com: example.com",
                error(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"open_url\", \"url\": \"example.com\"}}"));
        assertEquals("Click event type not allowed: OPEN_FILE",
                error(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"open_file\", \"path\": \"x\"}}"));
        assertEquals("Disallowed chat character: '§'",
                error(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"run_command\", \"command\": \"/say §a\"}}"));
        assertEquals(Component.text("a").clickEvent(ClickEvent.suggestCommand("/x")),
                decode(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"suggest_command\", \"command\": \"/x\"}}"));
        assertEquals("No key command in MapLike[{\"action\":\"run_command\"}]",
                error(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"run_command\"}}"));
        assertEquals("Value must be positive: 0",
                error(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"change_page\", \"page\": 0}}"));
        assertEquals("Not a number: \"1\"",
                error(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"change_page\", \"page\": \"1\"}}"));
        assertEquals(Component.text("a").clickEvent(ClickEvent.changePage(3)),
                decode(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"change_page\", \"page\": 3}}"));
        assertEquals(Component.text("a").clickEvent(ClickEvent.copyToClipboard("v")),
                decode(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"copy_to_clipboard\", \"value\": \"v\"}}"));
        assertEquals("Unknown element name:nope", error(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"nope\"}}"));
        assertEquals("No key action in MapLike[{\"url\":\"https://example.com\"}]",
                error(CODEC, "{\"text\": \"a\", \"click_event\": {\"url\": \"https://example.com\"}}"));
    }

    @Test
    void customClickEvents() {
        Component noPayload = decode(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"custom\", \"id\": \"foo:bar\"}}");
        assertEquals(Component.text("a").clickEvent(ClickEvent.custom(Key.key("foo:bar"))), noPayload);
        assertEquals("{\"text\":\"a\",\"click_event\":{\"id\":\"foo:bar\",\"action\":\"custom\"}}", encode(CODEC, noPayload));
        Component withPayload = decode(CODEC,
                "{\"text\": \"a\", \"click_event\": {\"action\": \"custom\", \"id\": \"foo:bar\", \"payload\": {\"a\": [1, \"x\"]}}}");
        var payload = CompoundBinaryTag.builder().put("a", ListBinaryTag.heterogeneousListBinaryTag()
                .add(ByteBinaryTag.byteBinaryTag((byte) 1)).add(StringBinaryTag.stringBinaryTag("x")).build()).build();
        assertEquals(Component.text("a").clickEvent(ClickEvent.custom(Key.key("foo:bar"), MinestomAdventure.wrapNbt(payload))), withPayload);
        assertEquals("{\"text\":\"a\",\"click_event\":{\"id\":\"foo:bar\",\"payload\":{\"a\":[1,\"x\"]},\"action\":\"custom\"}}",
                encode(CODEC, withPayload));
        assertEquals(Component.text("a").clickEvent(ClickEvent.custom(Key.key("foo:bar"),
                        MinestomAdventure.wrapNbt(StringBinaryTag.stringBinaryTag("snbt")))),
                decode(CODEC, "{\"text\": \"a\", \"click_event\": {\"action\": \"custom\", \"id\": \"foo:bar\", \"payload\": \"snbt\"}}"));
    }

    @Test
    void hoverEvents() {
        assertEquals(Component.text("a").hoverEvent(HoverEvent.showText(Component.text("b"))),
                decode(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_text\", \"value\": \"b\"}}"));
        assertEquals("{\"text\":\"a\",\"hover_event\":{\"value\":\"b\",\"action\":\"show_text\"}}",
                encode(CODEC, Component.text("a").hoverEvent(HoverEvent.showText(Component.text("b")))));
        assertEquals("No key value in MapLike[{\"action\":\"show_text\"}]",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_text\"}}"));
        UUID uuid = new UUID(1L << 32 | 2, 3L << 32 | 4);
        Component entity = decode(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_entity\", \"id\": \"minecraft:pig\", "
                + "\"uuid\": [1,2,3,4], \"name\": \"Bob\"}}");
        assertEquals(Component.text("a").hoverEvent(HoverEvent.showEntity(Key.key("minecraft:pig"), uuid, Component.text("Bob"))), entity);
        assertEquals("{\"text\":\"a\",\"hover_event\":{\"id\":\"minecraft:pig\",\"uuid\":[1,2,3,4],\"name\":\"Bob\",\"action\":\"show_entity\"}}",
                encode(CODEC, entity));
        assertEquals(Component.text("a").hoverEvent(HoverEvent.showEntity(Key.key("minecraft:pig"), uuid)),
                decode(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_entity\", \"id\": \"minecraft:pig\", "
                        + "\"uuid\": \"00000001-0000-0002-0000-000300000004\"}}"));
        assertEquals("Failed to parse either. First: Input is not a list of 4 ints; Second: Not a string: [1,2,3]",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_entity\", \"id\": \"minecraft:pig\", \"uuid\": [1,2,3]}}"));
        assertEquals("Failed to parse either. First: Not a json array: \"x\"; Second: Invalid UUID x: Invalid UUID string: x; "
                        + "Unknown registry key in ResourceKey[minecraft:root / minecraft:entity_type]: minecraft:nope",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_entity\", \"id\": \"minecraft:nope\", \"uuid\": \"x\"}}"));
        assertEquals("No key uuid in MapLike[{\"action\":\"show_entity\",\"id\":\"minecraft:pig\"}]",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_entity\", \"id\": \"minecraft:pig\"}}"));
        assertEquals("Unknown element name:nope", error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"nope\"}}"));
    }

    @Test
    void showItem() {
        Component item = decode(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:apple\", \"count\": 2, "
                + "\"components\": {\"minecraft:max_stack_size\": 64, \"!minecraft:food\": {}}}}");
        Map<Key, DataComponentValue> components = new LinkedHashMap<>();
        components.put(Key.key("minecraft:max_stack_size"), MinestomDataComponentValue.dataComponentValue(64));
        components.put(Key.key("minecraft:food"), DataComponentValue.removed());
        assertEquals(Component.text("a").hoverEvent(HoverEvent.showItem(Key.key("minecraft:apple"), 2, components)), item);
        // 补丁按解码的内容原样保存，与物品原型相同的组件也保留；adventure 的 ShowItem 不保留组件的顺序，所以按内容比较
        assertEquals(json("{\"text\":\"a\",\"hover_event\":{\"id\":\"minecraft:apple\",\"count\":2,\"components\":"
                + "{\"minecraft:max_stack_size\":64,\"!minecraft:food\":{}},\"action\":\"show_item\"}}"), json(encode(CODEC, item)));
        assertEquals("Item must not be minecraft:air",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:air\"}}"));
        assertEquals("Value must be within range [1;99]: 100",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:apple\", \"count\": 100}}"));
        assertEquals("No component with type: 'minecraft:nope'; No component with type: 'minecraft:nope' missed input: {\"minecraft:nope\":1}",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:apple\", "
                        + "\"components\": {\"minecraft:nope\": 1}}}"));
        assertEquals("'minecraft:creative_slot_lock' is not a persistent component; 'minecraft:creative_slot_lock' is not a persistent "
                        + "component missed input: {\"minecraft:creative_slot_lock\":{}}",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:apple\", "
                        + "\"components\": {\"minecraft:creative_slot_lock\": {}}}}"));
        assertEquals("No component with type: 'null'; No component with type: 'null' missed input: {\"Bad:id\":{}}",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:apple\", "
                        + "\"components\": {\"Bad:id\": {}}}}"));
        assertEquals("Not a JSON object: 1 missed input: {\"!minecraft:food\":1}",
                error(CODEC, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:apple\", "
                        + "\"components\": {\"!minecraft:food\": 1}}}"));
    }

    @Test
    void flatRestrictedCodec() {
        assertEquals("Component was too large: greater than max size 10", error(ComponentSerialization.flatRestrictedCodec(10), "\"0123456789\""));
        assertEquals(Component.text("01234567"), decode(ComponentSerialization.flatRestrictedCodec(10), "\"01234567\""));
        assertEquals(Component.text("0123456"), decode(ComponentSerialization.flatRestrictedCodec(10), "{\"text\": \"0123456\"}"));
        assertEquals("Component was too large: greater than max size 21",
                error(ComponentSerialization.flatRestrictedCodec(21), "{\"translate\":\"k\",\"with\":[1]}"));
        // 长度按原版的编码计算：原版写出 #FF5555，adventure 会写成 red
        assertEquals("Component was too large: greater than max size 27",
                error(ComponentSerialization.flatRestrictedCodec(27), "{\"text\":\"a\",\"color\":\"#FF5555\"}"));
        assertEquals(Component.text("a", NamedTextColor.RED),
                decode(ComponentSerialization.flatRestrictedCodec(26), "{\"text\":\"a\",\"color\":\"red\"}"));
        assertEquals("Component was too large: greater than max size 25",
                error(ComponentSerialization.flatRestrictedCodec(25), "{\"text\":\"a\",\"color\":\"red\"}"));
        // 解码失败时报告解码的错误
        assertEquals("Failed to parse either. First: Failed to parse either. First: Not a string: []; "
                + "Second: List must have contents; Second: Not a JSON object: []", error(ComponentSerialization.flatRestrictedCodec(10), "[]"));
    }

    @Test
    void decodesFromNbt() {
        var tag = CompoundBinaryTag.builder().putString("text", "a").putByte("bold", (byte) 1)
                .put("extra", ListBinaryTag.from(List.of(StringBinaryTag.stringBinaryTag("b")))).build();
        assertEquals(Component.text("a").decoration(TextDecoration.BOLD, true).append(Component.text("b")),
                CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        assertEquals(tag, CODEC.encodeStart(NbtOps.INSTANCE, Component.text("a").decoration(TextDecoration.BOLD, true)
                .append(Component.text("b"))).getOrThrow());
    }

    /**
     * 需要注册表的部分：对话框按 ID 引用时检查注册表。
     */
    @Nested
    @EnvTest
    class WithRegistries {
        @Test
        void showDialog(Env env) {
            DynamicOps<JsonElement> ops = registryOps(env.process());
            assertEquals(Component.text("a").clickEvent(ClickEvent.showDialog(Dialog.forKey(RegistryKey.of("minecraft:server_links")))),
                    decode(CODEC, ops, "{\"text\": \"a\", \"click_event\": {\"action\": \"show_dialog\", \"dialog\": \"minecraft:server_links\"}}"));
            assertEquals("Failed to get element ResourceKey[minecraft:dialog / minecraft:nope]",
                    error(CODEC, ops, "{\"text\": \"a\", \"click_event\": {\"action\": \"show_dialog\", \"dialog\": \"minecraft:nope\"}}"));
            Component inline = decode(CODEC, ops,
                    "{\"text\": \"a\", \"click_event\": {\"action\": \"show_dialog\", \"dialog\": {\"type\": \"minecraft:notice\", \"title\": \"t\"}}}");
            var payload = (ClickEvent.Payload.Dialog) inline.clickEvent().payload();
            assertInstanceOf(Dialog.Notice.class, payload.dialog());
            assertEquals(Component.text("t"), ((Dialog.Notice) payload.dialog()).metadata().title());
            assertEquals("{\"text\":\"a\",\"click_event\":{\"dialog\":\"minecraft:server_links\",\"action\":\"show_dialog\"}}",
                    encode(CODEC, ops, Component.text("a").clickEvent(ClickEvent.showDialog(Dialog.forKey(RegistryKey.of("minecraft:server_links"))))));
        }

        @Test
        void showItemWithRegistries(Env env) {
            DynamicOps<JsonElement> ops = registryOps(env.process());
            Component item = decode(CODEC, ops, "{\"text\": \"a\", \"hover_event\": {\"action\": \"show_item\", \"id\": \"minecraft:diamond_sword\", "
                    + "\"components\": {\"minecraft:enchantments\": {\"minecraft:sharpness\": 3}}}}");
            var showItem = (HoverEvent.ShowItem) item.hoverEvent().value();
            assertEquals(Key.key("minecraft:diamond_sword"), showItem.item());
            assertEquals(1, showItem.count());
            assertEquals(1, showItem.dataComponents().size());
        }
    }

    @Test
    void plainJsonOpsDialogReferenceNeedsRegistry() {
        // 没有注册表时只能内联定义，ID 交给 Minestom 的对话框编解码器，解码失败
        error(CODEC, JsonOps.INSTANCE, "{\"text\": \"a\", \"click_event\": {\"action\": \"show_dialog\", \"dialog\": \"minecraft:server_links\"}}");
    }
}
