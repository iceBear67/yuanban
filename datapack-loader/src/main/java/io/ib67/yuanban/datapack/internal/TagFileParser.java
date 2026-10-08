package io.ib67.yuanban.datapack.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.ExtraCodecs.TagOrElementLocation;
import io.ib67.yuanban.datapack.TagFileException;
import io.ib67.yuanban.datapack.tag.TagEntry;
import io.ib67.yuanban.datapack.topic.Resource;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.util.List;

/**
 * 解析标签文件，移植原版 {@code TagFile.CODEC} 和 {@code TagEntry.CODEC}。和原版 {@code TagLoader} 相同，
 * 用 {@link JsonOps#INSTANCE} 解码：未知字段忽略，值是 {@code null} 的字段按缺失处理。错误文本是编解码器给出的文本。
 */
@ApiStatus.Internal
public final class TagFileParser {
    /**
     * 原版 {@code TagEntry.FULL_CODEC}：对象写法，{@code required} 缺省为 {@code true}。
     */
    private static final Codec<TagEntry> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("id").forGetter(TagFileParser::elementOrTag),
            Codec.BOOL.optionalFieldOf("required", true).forGetter(TagEntry::required)
    ).apply(i, (elementOrTag, required) -> new TagEntry(elementOrTag.id(), elementOrTag.tag(), required)));
    /**
     * 原版 {@code TagEntry.CODEC}：先按字符串写法解码，失败再按对象写法解码。字符串写法的条目是必需的。
     */
    private static final Codec<TagEntry> ENTRY_CODEC = Codec.either(ExtraCodecs.TAG_OR_ELEMENT_ID, FULL_CODEC).xmap(
            either -> either.map(elementOrTag -> new TagEntry(elementOrTag.id(), elementOrTag.tag(), true), entry -> entry),
            entry -> entry.required() ? Either.left(elementOrTag(entry)) : Either.right(entry));
    /**
     * 原版 {@code TagFile.CODEC}。
     */
    private static final Codec<ParsedTagFile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRY_CODEC.listOf().fieldOf("values").forGetter(ParsedTagFile::entries),
            Codec.BOOL.optionalFieldOf("replace", false).forGetter(ParsedTagFile::replace)
    ).apply(i, (entries, replace) -> new ParsedTagFile(replace, entries)));

    private TagFileParser() {
    }

    public static ParsedTagFile parse(Resource resource) {
        JsonElement json;
        try {
            json = resource.readJson();
        } catch (IOException e) {
            throw new TagFileException("无法读取标签文件 " + resource, e);
        } catch (JsonParseException e) {
            throw new TagFileException("标签文件 " + resource + " 的 JSON 语法错误：" + e.getMessage(), e);
        }
        return CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new TagFileException("标签文件 " + resource + " 不合法：" + message));
    }

    private static TagOrElementLocation elementOrTag(TagEntry entry) {
        return new TagOrElementLocation(entry.id(), entry.tag());
    }

    public record ParsedTagFile(boolean replace, List<TagEntry> entries) {
    }
}
