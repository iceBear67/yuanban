package io.ib67.yuanban.recipe;

import io.ib67.yuanban.datapack.DataPackException;

/**
 * 配方文件无法解码。
 */
public final class RecipeException extends DataPackException {
    public RecipeException(String message) {
        super(message);
    }
}
