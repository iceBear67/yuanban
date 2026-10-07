package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * {@code rotate}、{@code lookAt}：在移动队列中轮到它的那个 tick 计算一次朝向，这个 tick 结束时执行完毕。
 * movement suspension 期间照常执行。
 */
public final class RotateAction extends MovementAction {
    private final Function<MovementController, @Nullable Rotation> rotation;

    /**
     * @param rotation 计算朝向；返回 null 表示目标实体不在模型中，动作被取消
     */
    public RotateAction(ActionImpl handle, Function<MovementController, @Nullable Rotation> rotation) {
        super(handle);
        this.rotation = rotation;
    }

    @Override
    boolean moves() {
        return false;
    }

    @Override
    MoveIntent tick(MovementController controller) {
        var result = rotation.apply(controller);
        if (result == null) {
            handle.cancelled(ActionCancelledException.NO_ENTITY);
        } else {
            controller.arbiter().manual(result);
        }
        return MoveIntent.NONE;
    }
}
