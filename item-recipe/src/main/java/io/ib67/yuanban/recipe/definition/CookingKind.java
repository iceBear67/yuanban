package io.ib67.yuanban.recipe.definition;

/**
 * 四种烧炼配方，{@code defaultCookingTime} 是 {@code cookingtime} 缺省时的取值。
 */
public enum CookingKind {
    SMELTING("smelting", 200),
    BLASTING("blasting", 100),
    SMOKING("smoking", 100),
    CAMPFIRE_COOKING("campfire_cooking", 100);

    private final String type;
    private final int defaultCookingTime;

    CookingKind(String type, int defaultCookingTime) {
        this.type = type;
        this.defaultCookingTime = defaultCookingTime;
    }

    /**
     * 配方文件中 {@code type} 的路径部分。
     */
    public String type() {
        return type;
    }

    public int defaultCookingTime() {
        return defaultCookingTime;
    }
}
