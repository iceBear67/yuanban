package io.ib67.yuanban.codec;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.ByteArrayBinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.LongArrayBinaryTag;
import net.kyori.adventure.nbt.LongBinaryTag;
import net.kyori.adventure.nbt.NumberBinaryTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * 原版 {@code net.minecraft.commands.arguments.NbtPathArgument.NbtPath} 及其解析器 {@code NbtPathArgument.parse}。
 * <p>
 * 原版直接修改传入的 NBT；adventure 的标签不可修改，所以原版中修改 NBT 的操作在这里返回修改之后的新根标签
 * （{@link Modified}），其余语义与原版相同：节点按原版的顺序在原版结构的可修改副本上执行，执行完毕后再转换回
 * adventure 的标签。
 */
public final class NbtPath {
    /**
     * 原版 {@code NbtPathArgument.ERROR_INVALID_NODE}：{@code Invalid NBT path element}。
     */
    public static final SimpleCommandExceptionType ERROR_INVALID_NODE = CommandMessages.simple("arguments.nbtpath.node.invalid");
    /**
     * 原版 {@code NbtPathArgument.ERROR_DATA_TOO_DEEP}：{@code Resulting NBT too deeply nested}。
     */
    public static final SimpleCommandExceptionType ERROR_DATA_TOO_DEEP = CommandMessages.simple("arguments.nbtpath.too_deep");
    /**
     * 原版 {@code NbtPathArgument.ERROR_NOTHING_FOUND}：{@code Found no elements matching <path>}。
     */
    public static final DynamicCommandExceptionType ERROR_NOTHING_FOUND = CommandMessages.dynamic("arguments.nbtpath.nothing_found");
    private static final DynamicCommandExceptionType ERROR_EXPECTED_LIST = CommandMessages.dynamic("commands.data.modify.expected_list");
    private static final DynamicCommandExceptionType ERROR_INVALID_INDEX = CommandMessages.dynamic("commands.data.modify.invalid_index");
    /**
     * 原版 {@code NbtPathArgument.NbtPath.CODEC}：解析失败时为 {@code Failed to parse path <path>: <错误>}。
     * 与原版相同，路径之后的空格及其后的内容不报错，路径只取到空格之前。
     */
    public static final Codec<NbtPath> CODEC = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(parse(new StringReader(string)));
        } catch (CommandSyntaxException e) {
            return DataResult.error(() -> "Failed to parse path " + string + ": " + e.getMessage());
        }
    }, NbtPath::asString);

    private final String original;
    private final Map<Node, Integer> nodeToOriginalPosition;
    private final Node[] nodes;

    private NbtPath(String original, Node[] nodes, Map<Node, Integer> nodeToOriginalPosition) {
        this.original = original;
        this.nodes = nodes;
        this.nodeToOriginalPosition = nodeToOriginalPosition;
    }

    /**
     * 原版 {@code NbtPath.of}：从字符串开头解析，遇到空格时停止。
     */
    public static NbtPath of(String string) throws CommandSyntaxException {
        return parse(new StringReader(string));
    }

    /**
     * 原版 {@code NbtPathArgument.parse}：从 {@code reader} 的当前位置解析，遇到空格或末尾时停止。
     */
    public static NbtPath parse(StringReader reader) throws CommandSyntaxException {
        List<Node> nodes = new ArrayList<>();
        int start = reader.getCursor();
        Map<Node, Integer> nodeToOriginalPosition = new HashMap<>();
        boolean firstNode = true;
        while (reader.canRead() && reader.peek() != ' ') {
            Node node = parseNode(reader, firstNode);
            nodes.add(node);
            nodeToOriginalPosition.put(node, reader.getCursor() - start);
            firstNode = false;
            if (reader.canRead()) {
                char next = reader.peek();
                if (next != ' ' && next != '[' && next != '{') {
                    reader.expect('.');
                }
            }
        }
        return new NbtPath(reader.getString().substring(start, reader.getCursor()), nodes.toArray(Node[]::new), nodeToOriginalPosition);
    }

    private static Node parseNode(StringReader reader, boolean firstNode) throws CommandSyntaxException {
        return switch (reader.peek()) {
            case '"', '\'' -> readObjectNode(reader, reader.readString());
            case '[' -> {
                reader.skip();
                int next = reader.peek();
                if (next == '{') {
                    CompoundBinaryTag pattern = TagParser.parseCompoundAsArgument(reader);
                    reader.expect(']');
                    yield new MatchElementNode(pattern);
                } else if (next == ']') {
                    reader.skip();
                    yield AllElementsNode.INSTANCE;
                } else {
                    int index = reader.readInt();
                    reader.expect(']');
                    yield new IndexedElementNode(index);
                }
            }
            case '{' -> {
                if (!firstNode) {
                    throw ERROR_INVALID_NODE.createWithContext(reader);
                }
                CompoundBinaryTag pattern = TagParser.parseCompoundAsArgument(reader);
                yield new MatchRootObjectNode(pattern);
            }
            default -> readObjectNode(reader, readUnquotedName(reader));
        };
    }

    private static Node readObjectNode(StringReader reader, String name) throws CommandSyntaxException {
        if (name.isEmpty()) {
            throw ERROR_INVALID_NODE.createWithContext(reader);
        }
        if (reader.canRead() && reader.peek() == '{') {
            CompoundBinaryTag pattern = TagParser.parseCompoundAsArgument(reader);
            return new MatchObjectNode(name, pattern);
        }
        return new CompoundChildNode(name);
    }

    private static String readUnquotedName(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowedInUnquotedName(reader.peek())) {
            reader.skip();
        }
        if (reader.getCursor() == start) {
            throw ERROR_INVALID_NODE.createWithContext(reader);
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    private static boolean isAllowedInUnquotedName(char c) {
        return c != ' ' && c != '"' && c != '\'' && c != '[' && c != ']' && c != '.' && c != '{' && c != '}';
    }

    private static Predicate<MutableTag> createTagPredicate(CompoundBinaryTag pattern) {
        return tag -> NbtUtils.compareNbt(pattern, tag == null ? null : tag.toTag(), true);
    }

    /**
     * 原版 {@code NbtPath.get}：路径匹配的全部标签；某个节点没有匹配时为 {@code Found no elements matching <到该节点为止的路径>}。
     */
    public List<BinaryTag> get(BinaryTag tag) throws CommandSyntaxException {
        List<MutableTag> result = Collections.singletonList(MutableTag.of(tag));
        for (Node node : nodes) {
            result = node.get(result);
            if (result.isEmpty()) {
                throw createNotFoundException(node);
            }
        }
        return toTags(result);
    }

    /**
     * 原版 {@code NbtPath.countMatching}：路径匹配的标签个数，没有匹配时为 0。
     */
    public int countMatching(BinaryTag tag) {
        List<MutableTag> result = Collections.singletonList(MutableTag.of(tag));
        for (Node node : nodes) {
            result = node.get(result);
            if (result.isEmpty()) {
                return 0;
            }
        }
        return result.size();
    }

    private List<MutableTag> getOrCreateParents(MutableTag tag) throws CommandSyntaxException {
        List<MutableTag> result = Collections.singletonList(tag);
        for (int i = 0; i < nodes.length - 1; i++) {
            Node node = nodes[i];
            Node next = nodes[i + 1];
            result = node.getOrCreate(result, next::createPreferredParentTag);
            if (result.isEmpty()) {
                throw createNotFoundException(node);
            }
        }
        return result;
    }

    private List<MutableTag> getOrCreate(MutableTag tag, Supplier<MutableTag> newTagValue) throws CommandSyntaxException {
        List<MutableTag> result = getOrCreateParents(tag);
        Node lastNode = nodes[nodes.length - 1];
        return lastNode.getOrCreate(result, newTagValue);
    }

    /**
     * 原版 {@code NbtPath.getOrCreate}：沿路径取得目标，中间缺少的节点按下一个节点的类型创建，
     * 最后一个节点缺少的目标用 {@code newTagValue} 创建。
     * <p>
     * 原版返回目标本身供调用者直接修改；这里对每个目标依次调用 {@code update}，用其结果替换该目标，返回修改之后的根标签。
     * 与原版只能修改容器的内容相同，{@code update} 的结果必须与目标的类型相同：
     * 复合标签、列表和数组在原位置替换内容；其它标签不能修改，结果必须与原值相等。
     *
     * @throws IllegalArgumentException {@code update} 改变了目标的类型，或者修改了不能修改的标签
     */
    public BinaryTag getOrCreate(BinaryTag tag, Supplier<? extends BinaryTag> newTagValue, UnaryOperator<BinaryTag> update)
            throws CommandSyntaxException {
        MutableTag root = MutableTag.of(tag);
        List<MutableTag> targets = getOrCreate(root, () -> MutableTag.of(newTagValue.get()));
        for (MutableTag target : targets) {
            BinaryTag current = target.toTag();
            BinaryTag updated = update.apply(current);
            target.replaceWith(updated);
        }
        return root.toTag();
    }

    private static boolean isTooDeep(BinaryTag tag, int depth) {
        if (depth >= 512) {
            return true;
        }
        if (tag instanceof CompoundBinaryTag compound) {
            for (var entry : compound) {
                if (isTooDeep(entry.getValue(), depth + 1)) {
                    return true;
                }
            }
        } else if (tag instanceof ListBinaryTag list) {
            for (BinaryTag element : list) {
                if (isTooDeep(element, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 原版 {@code NbtPath.set}：把路径匹配的每个目标设为 {@code toAdd}，缺少的中间节点会被创建。
     *
     * @return 修改之后的根标签，以及值发生变化的目标个数
     */
    public Modified set(BinaryTag tag, BinaryTag toAdd) throws CommandSyntaxException {
        if (isTooDeep(toAdd, estimatePathDepth())) {
            throw ERROR_DATA_TOO_DEEP.create();
        }
        MutableTag root = MutableTag.of(tag);
        List<MutableTag> result = getOrCreateParents(root);
        if (result.isEmpty()) {
            return new Modified(root.toTag(), 0);
        }
        Node lastNode = nodes[nodes.length - 1];
        int count = 0;
        for (MutableTag target : result) {
            count += lastNode.setTag(target, () -> MutableTag.of(toAdd));
        }
        return new Modified(root.toTag(), count);
    }

    private int estimatePathDepth() {
        return nodes.length;
    }

    /**
     * 原版 {@code NbtPath.insert}：在路径匹配的每个列表或数组的 {@code index} 处插入 {@code toInsert}；
     * {@code index} 为负数时从末尾数起（{@code -1} 表示末尾）。
     *
     * @return 修改之后的根标签，以及发生变化的目标个数
     * @throws CommandSyntaxException 目标不是列表或数组（{@code Expected a list: got <tag>}），
     *                                位置越界（{@code Invalid list index: <index>}）
     */
    public Modified insert(int index, CompoundBinaryTag target, List<BinaryTag> toInsert) throws CommandSyntaxException {
        for (BinaryTag tag : toInsert) {
            if (isTooDeep(tag, estimatePathDepth())) {
                throw ERROR_DATA_TOO_DEEP.create();
            }
        }
        MutableTag root = MutableTag.of(target);
        List<MutableTag> targets = getOrCreate(root, MutableList::new);
        int modifiedCount = 0;
        for (MutableTag targetTag : targets) {
            if (!(targetTag instanceof CollectionTag targetList)) {
                throw ERROR_EXPECTED_LIST.create(StringTagVisitor.toString(targetTag.toTag()));
            }
            boolean modified = false;
            int actualIndex = index < 0 ? targetList.size() + index + 1 : index;
            for (BinaryTag sourceTag : toInsert) {
                try {
                    if (targetList.addTag(actualIndex, MutableTag.of(sourceTag))) {
                        actualIndex++;
                        modified = true;
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw ERROR_INVALID_INDEX.create(actualIndex);
                }
            }
            modifiedCount += modified ? 1 : 0;
        }
        return new Modified(root.toTag(), modifiedCount);
    }

    /**
     * 原版 {@code NbtPath.remove}：移除路径匹配的全部目标。
     *
     * @return 修改之后的根标签，以及被移除的目标个数
     */
    public Modified remove(BinaryTag tag) {
        MutableTag root = MutableTag.of(tag);
        List<MutableTag> result = Collections.singletonList(root);
        for (int i = 0; i < nodes.length - 1; i++) {
            result = nodes[i].get(result);
        }
        Node lastNode = nodes[nodes.length - 1];
        int count = 0;
        for (MutableTag parent : result) {
            count += lastNode.removeTag(parent);
        }
        return new Modified(root.toTag(), count);
    }

    private CommandSyntaxException createNotFoundException(Node node) {
        int index = nodeToOriginalPosition.get(node);
        return ERROR_NOTHING_FOUND.create(original.substring(0, index));
    }

    /**
     * 解析时读取的原文。
     */
    public String asString() {
        return original;
    }

    @Override
    public String toString() {
        return original;
    }

    private static List<BinaryTag> toTags(List<MutableTag> tags) {
        List<BinaryTag> result = new ArrayList<>(tags.size());
        for (MutableTag tag : tags) {
            result.add(tag.toTag());
        }
        return result;
    }

    /**
     * 修改操作的结果。
     *
     * @param root  修改之后的根标签
     * @param count 原版返回的计数
     */
    public record Modified(BinaryTag root, int count) {
    }

    /**
     * 原版 {@code NbtPathArgument.Node}。
     */
    private interface Node {
        void getTag(MutableTag parent, List<MutableTag> output);

        void getOrCreateTag(MutableTag parent, Supplier<MutableTag> child, List<MutableTag> output);

        MutableTag createPreferredParentTag();

        int setTag(MutableTag parent, Supplier<MutableTag> toAdd);

        int removeTag(MutableTag parent);

        default List<MutableTag> get(List<MutableTag> tags) {
            return collect(tags, this::getTag);
        }

        default List<MutableTag> getOrCreate(List<MutableTag> tags, Supplier<MutableTag> child) {
            return collect(tags, (tag, output) -> getOrCreateTag(tag, child, output));
        }

        default List<MutableTag> collect(List<MutableTag> tags, BiConsumer<MutableTag, List<MutableTag>> collector) {
            List<MutableTag> result = new ArrayList<>();
            for (MutableTag tag : tags) {
                collector.accept(tag, result);
            }
            return result;
        }
    }

    /**
     * 原版 {@code NbtPathArgument.CompoundChildNode}：{@code name}。
     */
    private static final class CompoundChildNode implements Node {
        private final String name;

        CompoundChildNode(String name) {
            this.name = name;
        }

        @Override
        public void getTag(MutableTag parent, List<MutableTag> output) {
            if (parent instanceof MutableCompound compound) {
                MutableTag result = compound.get(name);
                if (result != null) {
                    output.add(result);
                }
            }
        }

        @Override
        public void getOrCreateTag(MutableTag parent, Supplier<MutableTag> child, List<MutableTag> output) {
            if (parent instanceof MutableCompound compound) {
                MutableTag result;
                if (compound.contains(name)) {
                    result = compound.get(name);
                } else {
                    result = child.get();
                    compound.put(name, result);
                }
                output.add(result);
            }
        }

        @Override
        public MutableTag createPreferredParentTag() {
            return new MutableCompound();
        }

        @Override
        public int setTag(MutableTag parent, Supplier<MutableTag> toAdd) {
            if (parent instanceof MutableCompound compound) {
                MutableTag newValue = toAdd.get();
                MutableTag previousValue = compound.put(name, newValue);
                if (!MutableTag.sameValue(newValue, previousValue)) {
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int removeTag(MutableTag parent) {
            if (parent instanceof MutableCompound compound && compound.contains(name)) {
                compound.remove(name);
                return 1;
            }
            return 0;
        }
    }

    /**
     * 原版 {@code NbtPathArgument.IndexedElementNode}：{@code [index]}，负数从末尾数起。
     */
    private static final class IndexedElementNode implements Node {
        private final int index;

        IndexedElementNode(int index) {
            this.index = index;
        }

        @Override
        public void getTag(MutableTag parent, List<MutableTag> output) {
            if (parent instanceof CollectionTag list) {
                int size = list.size();
                int actualIndex = index < 0 ? size + index : index;
                if (0 <= actualIndex && actualIndex < size) {
                    output.add(list.get(actualIndex));
                }
            }
        }

        @Override
        public void getOrCreateTag(MutableTag parent, Supplier<MutableTag> child, List<MutableTag> output) {
            getTag(parent, output);
        }

        @Override
        public MutableTag createPreferredParentTag() {
            return new MutableList();
        }

        @Override
        public int setTag(MutableTag parent, Supplier<MutableTag> toAdd) {
            if (parent instanceof CollectionTag list) {
                int size = list.size();
                int actualIndex = index < 0 ? size + index : index;
                if (0 <= actualIndex && actualIndex < size) {
                    MutableTag previousValue = list.get(actualIndex);
                    MutableTag newValue = toAdd.get();
                    if (!MutableTag.sameValue(newValue, previousValue) && list.setTag(actualIndex, newValue)) {
                        return 1;
                    }
                }
            }
            return 0;
        }

        @Override
        public int removeTag(MutableTag parent) {
            if (parent instanceof CollectionTag list) {
                int size = list.size();
                int actualIndex = index < 0 ? size + index : index;
                if (0 <= actualIndex && actualIndex < size) {
                    list.remove(actualIndex);
                    return 1;
                }
            }
            return 0;
        }
    }

    /**
     * 原版 {@code NbtPathArgument.MatchElementNode}：{@code [{pattern}]}，列表中满足 {@code pattern} 的元素。
     */
    private static final class MatchElementNode implements Node {
        private final CompoundBinaryTag pattern;
        private final Predicate<MutableTag> predicate;

        MatchElementNode(CompoundBinaryTag pattern) {
            this.pattern = pattern;
            this.predicate = createTagPredicate(pattern);
        }

        @Override
        public void getTag(MutableTag parent, List<MutableTag> output) {
            if (parent instanceof MutableList list) {
                list.elements.stream().filter(predicate).forEach(output::add);
            }
        }

        @Override
        public void getOrCreateTag(MutableTag parent, Supplier<MutableTag> child, List<MutableTag> output) {
            boolean foundAnything = false;
            if (parent instanceof MutableList list) {
                for (MutableTag tag : List.copyOf(list.elements)) {
                    if (predicate.test(tag)) {
                        output.add(tag);
                        foundAnything = true;
                    }
                }
                if (!foundAnything) {
                    MutableTag newTag = MutableTag.of(pattern);
                    list.elements.add(newTag);
                    output.add(newTag);
                }
            }
        }

        @Override
        public MutableTag createPreferredParentTag() {
            return new MutableList();
        }

        @Override
        public int setTag(MutableTag parent, Supplier<MutableTag> toAdd) {
            int changedCount = 0;
            if (parent instanceof MutableList list) {
                int size = list.size();
                if (size == 0) {
                    list.elements.add(toAdd.get());
                    changedCount++;
                } else {
                    for (int i = 0; i < size; i++) {
                        MutableTag currentValue = list.get(i);
                        if (predicate.test(currentValue)) {
                            MutableTag newValue = toAdd.get();
                            if (!MutableTag.sameValue(newValue, currentValue) && list.setTag(i, newValue)) {
                                changedCount++;
                            }
                        }
                    }
                }
            }
            return changedCount;
        }

        @Override
        public int removeTag(MutableTag parent) {
            int changedCount = 0;
            if (parent instanceof MutableList list) {
                for (int i = list.size() - 1; i >= 0; i--) {
                    if (predicate.test(list.get(i))) {
                        list.remove(i);
                        changedCount++;
                    }
                }
            }
            return changedCount;
        }
    }

    /**
     * 原版 {@code NbtPathArgument.AllElementsNode}：{@code []}，列表或数组的全部元素。原版只有一个实例，
     * 路径中多次出现时共用同一个位置记录。
     */
    private static final class AllElementsNode implements Node {
        static final AllElementsNode INSTANCE = new AllElementsNode();

        private AllElementsNode() {
        }

        @Override
        public void getTag(MutableTag parent, List<MutableTag> output) {
            if (parent instanceof CollectionTag collection) {
                for (int i = 0; i < collection.size(); i++) {
                    output.add(collection.get(i));
                }
            }
        }

        @Override
        public void getOrCreateTag(MutableTag parent, Supplier<MutableTag> child, List<MutableTag> output) {
            if (parent instanceof CollectionTag list) {
                if (list.isEmpty()) {
                    MutableTag result = child.get();
                    if (list.addTag(0, result)) {
                        output.add(result);
                    }
                } else {
                    for (int i = 0; i < list.size(); i++) {
                        output.add(list.get(i));
                    }
                }
            }
        }

        @Override
        public MutableTag createPreferredParentTag() {
            return new MutableList();
        }

        @Override
        public int setTag(MutableTag parent, Supplier<MutableTag> toAdd) {
            if (!(parent instanceof CollectionTag list)) {
                return 0;
            }
            int size = list.size();
            if (size == 0) {
                list.addTag(0, toAdd.get());
                return 1;
            }
            MutableTag newValue = toAdd.get();
            int unchanged = 0;
            for (int i = 0; i < size; i++) {
                if (MutableTag.sameValue(newValue, list.get(i))) {
                    unchanged++;
                }
            }
            int changedCount = size - unchanged;
            if (changedCount == 0) {
                return 0;
            }
            list.clear();
            if (!list.addTag(0, newValue)) {
                return 0;
            }
            for (int i = 1; i < size; i++) {
                list.addTag(i, toAdd.get());
            }
            return changedCount;
        }

        @Override
        public int removeTag(MutableTag parent) {
            if (parent instanceof CollectionTag list) {
                int size = list.size();
                if (size > 0) {
                    list.clear();
                    return size;
                }
            }
            return 0;
        }
    }

    /**
     * 原版 {@code NbtPathArgument.MatchObjectNode}：{@code name{pattern}}。
     */
    private static final class MatchObjectNode implements Node {
        private final String name;
        private final CompoundBinaryTag pattern;
        private final Predicate<MutableTag> predicate;

        MatchObjectNode(String name, CompoundBinaryTag pattern) {
            this.name = name;
            this.pattern = pattern;
            this.predicate = createTagPredicate(pattern);
        }

        @Override
        public void getTag(MutableTag parent, List<MutableTag> output) {
            if (parent instanceof MutableCompound compound) {
                MutableTag result = compound.get(name);
                if (predicate.test(result)) {
                    output.add(result);
                }
            }
        }

        @Override
        public void getOrCreateTag(MutableTag parent, Supplier<MutableTag> child, List<MutableTag> output) {
            if (parent instanceof MutableCompound compound) {
                MutableTag result = compound.get(name);
                if (result == null) {
                    MutableTag created = MutableTag.of(pattern);
                    compound.put(name, created);
                    output.add(created);
                } else if (predicate.test(result)) {
                    output.add(result);
                }
            }
        }

        @Override
        public MutableTag createPreferredParentTag() {
            return new MutableCompound();
        }

        @Override
        public int setTag(MutableTag parent, Supplier<MutableTag> toAdd) {
            if (parent instanceof MutableCompound compound) {
                MutableTag currentValue = compound.get(name);
                if (predicate.test(currentValue)) {
                    MutableTag newValue = toAdd.get();
                    if (!MutableTag.sameValue(newValue, currentValue)) {
                        compound.put(name, newValue);
                        return 1;
                    }
                }
            }
            return 0;
        }

        @Override
        public int removeTag(MutableTag parent) {
            if (parent instanceof MutableCompound compound) {
                MutableTag current = compound.get(name);
                if (predicate.test(current)) {
                    compound.remove(name);
                    return 1;
                }
            }
            return 0;
        }
    }

    /**
     * 原版 {@code NbtPathArgument.MatchRootObjectNode}：路径开头的 {@code {pattern}}。
     */
    private static final class MatchRootObjectNode implements Node {
        private final Predicate<MutableTag> predicate;

        MatchRootObjectNode(CompoundBinaryTag pattern) {
            this.predicate = createTagPredicate(pattern);
        }

        @Override
        public void getTag(MutableTag self, List<MutableTag> output) {
            if (self instanceof MutableCompound && predicate.test(self)) {
                output.add(self);
            }
        }

        @Override
        public void getOrCreateTag(MutableTag self, Supplier<MutableTag> child, List<MutableTag> output) {
            getTag(self, output);
        }

        @Override
        public MutableTag createPreferredParentTag() {
            return new MutableCompound();
        }

        @Override
        public int setTag(MutableTag parent, Supplier<MutableTag> toAdd) {
            return 0;
        }

        @Override
        public int removeTag(MutableTag parent) {
            return 0;
        }
    }

    /**
     * 原版可修改的 NBT 结构：复合标签、列表和数组可以修改，其它标签不可修改。用于在 adventure 不可修改的标签上
     * 重现原版直接修改 NBT 的过程；节点之间按对象身份区分，与原版相同。
     */
    private sealed interface MutableTag permits MutableCompound, MutableList, MutableArray, ImmutableValue {
        BinaryTag toTag();

        /**
         * 把这个标签的内容替换成 {@code value} 的内容。
         *
         * @throws IllegalArgumentException 类型不同，或者这是不可修改的标签而值不同
         */
        void replaceWith(BinaryTag value);

        static MutableTag of(BinaryTag tag) {
            return switch (tag) {
                case CompoundBinaryTag compound -> {
                    var result = new MutableCompound();
                    for (var entry : compound) {
                        result.put(entry.getKey(), of(entry.getValue()));
                    }
                    yield result;
                }
                case ListBinaryTag list -> {
                    var result = new MutableList();
                    for (BinaryTag element : list) {
                        result.elements.add(of(element));
                    }
                    yield result;
                }
                case ByteArrayBinaryTag array -> MutableArray.of(array);
                case IntArrayBinaryTag array -> MutableArray.of(array);
                case LongArrayBinaryTag array -> MutableArray.of(array);
                default -> new ImmutableValue(tag);
            };
        }

        /**
         * 原版 {@code Tag.equals}：按值比较。
         */
        static boolean sameValue(MutableTag a, @Nullable MutableTag b) {
            return b != null && a.toTag().equals(b.toTag());
        }
    }

    /**
     * 原版 {@code CollectionTag}：列表和三种数组。
     */
    private sealed interface CollectionTag permits MutableList, MutableArray {
        int size();

        MutableTag get(int index);

        boolean setTag(int index, MutableTag tag);

        boolean addTag(int index, MutableTag tag);

        void remove(int index);

        void clear();

        default boolean isEmpty() {
            return size() == 0;
        }
    }

    /**
     * 原版 {@code CompoundTag}，以 {@link HashMap} 保存。
     */
    private static final class MutableCompound implements MutableTag {
        private final Map<String, MutableTag> tags = new HashMap<>();

        boolean contains(String key) {
            return tags.containsKey(key);
        }

        @Nullable MutableTag get(String key) {
            return tags.get(key);
        }

        @Nullable MutableTag put(String key, MutableTag value) {
            return tags.put(key, value);
        }

        void remove(String key) {
            tags.remove(key);
        }

        @Override
        public BinaryTag toTag() {
            var builder = CompoundBinaryTag.builder();
            tags.forEach((key, value) -> builder.put(key, value.toTag()));
            return builder.build();
        }

        @Override
        public void replaceWith(BinaryTag value) {
            if (!(value instanceof CompoundBinaryTag compound)) {
                throw new IllegalArgumentException("Cannot replace a compound tag with " + StringTagVisitor.toString(value));
            }
            tags.clear();
            for (var entry : compound) {
                tags.put(entry.getKey(), MutableTag.of(entry.getValue()));
            }
        }
    }

    /**
     * 原版 {@code ListTag}：元素类型可以不同。
     */
    private static final class MutableList implements MutableTag, CollectionTag {
        private final List<MutableTag> elements = new ArrayList<>();

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public MutableTag get(int index) {
            return elements.get(index);
        }

        @Override
        public boolean setTag(int index, MutableTag tag) {
            elements.set(index, tag);
            return true;
        }

        @Override
        public boolean addTag(int index, MutableTag tag) {
            elements.add(index, tag);
            return true;
        }

        @Override
        public void remove(int index) {
            elements.remove(index);
        }

        @Override
        public void clear() {
            elements.clear();
        }

        @Override
        public BinaryTag toTag() {
            var builder = ListBinaryTag.heterogeneousListBinaryTag(elements.size());
            for (MutableTag element : elements) {
                builder.add(element.toTag());
            }
            return builder.build();
        }

        @Override
        public void replaceWith(BinaryTag value) {
            if (!(value instanceof ListBinaryTag list)) {
                throw new IllegalArgumentException("Cannot replace a list tag with " + StringTagVisitor.toString(value));
            }
            elements.clear();
            for (BinaryTag element : list) {
                elements.add(MutableTag.of(element));
            }
        }
    }

    /**
     * 原版 {@code ByteArrayTag}、{@code IntArrayTag}、{@code LongArrayTag}：元素是对应类型的数值标签，
     * 只能插入或设置数值标签，数值按原版 {@code NumericTag} 的规则转换成元素类型。
     */
    private static final class MutableArray implements MutableTag, CollectionTag {
        private final ArrayKind kind;
        private final LongArrayList values = new LongArrayList();

        private MutableArray(ArrayKind kind) {
            this.kind = kind;
        }

        static MutableArray of(ByteArrayBinaryTag array) {
            var result = new MutableArray(ArrayKind.BYTE);
            for (byte value : array.value()) {
                result.values.add(value);
            }
            return result;
        }

        static MutableArray of(IntArrayBinaryTag array) {
            var result = new MutableArray(ArrayKind.INT);
            for (int value : array.value()) {
                result.values.add(value);
            }
            return result;
        }

        static MutableArray of(LongArrayBinaryTag array) {
            var result = new MutableArray(ArrayKind.LONG);
            for (long value : array.value()) {
                result.values.add(value);
            }
            return result;
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public MutableTag get(int index) {
            long value = values.getLong(index);
            return new ImmutableValue(switch (kind) {
                case BYTE -> ByteBinaryTag.byteBinaryTag((byte) value);
                case INT -> IntBinaryTag.intBinaryTag((int) value);
                case LONG -> LongBinaryTag.longBinaryTag(value);
            });
        }

        @Override
        public boolean setTag(int index, MutableTag tag) {
            if (tag instanceof ImmutableValue(NumberBinaryTag number)) {
                values.set(index, narrow(number));
                return true;
            }
            return false;
        }

        @Override
        public boolean addTag(int index, MutableTag tag) {
            if (tag instanceof ImmutableValue(NumberBinaryTag number)) {
                values.add(index, narrow(number));
                return true;
            }
            return false;
        }

        private long narrow(NumberBinaryTag number) {
            return switch (kind) {
                case BYTE -> number.byteValue();
                case INT -> number.intValue();
                case LONG -> number.longValue();
            };
        }

        @Override
        public void remove(int index) {
            values.removeLong(index);
        }

        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public BinaryTag toTag() {
            return switch (kind) {
                case BYTE -> {
                    byte[] bytes = new byte[values.size()];
                    for (int i = 0; i < bytes.length; i++) {
                        bytes[i] = (byte) values.getLong(i);
                    }
                    yield ByteArrayBinaryTag.byteArrayBinaryTag(bytes);
                }
                case INT -> {
                    int[] ints = new int[values.size()];
                    for (int i = 0; i < ints.length; i++) {
                        ints[i] = (int) values.getLong(i);
                    }
                    yield IntArrayBinaryTag.intArrayBinaryTag(ints);
                }
                case LONG -> LongArrayBinaryTag.longArrayBinaryTag(values.toLongArray());
            };
        }

        @Override
        public void replaceWith(BinaryTag value) {
            MutableArray replacement = switch (value) {
                case ByteArrayBinaryTag array when kind == ArrayKind.BYTE -> of(array);
                case IntArrayBinaryTag array when kind == ArrayKind.INT -> of(array);
                case LongArrayBinaryTag array when kind == ArrayKind.LONG -> of(array);
                default -> throw new IllegalArgumentException("Cannot replace " + StringTagVisitor.toString(toTag())
                        + " with " + StringTagVisitor.toString(value));
            };
            values.clear();
            values.addAll(replacement.values);
        }

        private enum ArrayKind {
            BYTE, INT, LONG
        }
    }

    /**
     * 原版中不可修改的标签：数值、字符串。
     */
    private record ImmutableValue(BinaryTag tag) implements MutableTag {
        @Override
        public BinaryTag toTag() {
            return tag;
        }

        @Override
        public void replaceWith(BinaryTag value) {
            if (!tag.equals(value)) {
                throw new IllegalArgumentException("Cannot modify " + StringTagVisitor.toString(tag)
                        + " in place, got " + StringTagVisitor.toString(value));
            }
        }
    }
}
