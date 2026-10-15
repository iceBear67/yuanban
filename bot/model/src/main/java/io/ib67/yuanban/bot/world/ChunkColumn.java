package io.ib67.yuanban.bot.world;

/**
 * 一个已加载的区块列：从维度最低 y 开始的各区段。
 */
public final class ChunkColumn {
    private final SectionRef[] sections;

    public ChunkColumn(SectionRef[] sections) {
        this.sections = sections;
    }

    /**
     * @param index 区段序号，0 为维度最低的区段
     */
    public SectionRef section(int index) {
        return sections[index];
    }

    public int sectionCount() {
        return sections.length;
    }
}
