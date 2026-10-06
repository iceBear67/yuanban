package io.ib67.yuanban.datapack;

import io.ib67.yuanban.datapack.selection.Selection;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;

/**
 * 成功的重载。返回这个结果的时候，新的启用列表已经生效，宿主应当把 {@code selection().config()} 写回存档。
 *
 * @param selection    新的启用列表
 * @param closeFailure 会话切换到新批次之后，关闭旧批次失败的原因；关闭成功或者旧批次需要保持打开的时候为空
 */
public record ReloadResult(Selection selection, Optional<UncheckedIOException> closeFailure) {
    public ReloadResult {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(closeFailure, "closeFailure");
    }
}
