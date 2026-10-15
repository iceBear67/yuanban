package io.ib67.yuanban.bot.interaction;

import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.state.ActionHandle;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 手部交互队列中的一个动作。全部方法在调度线程上调用。
 */
public abstract class HandAction {
    protected final ActionHandle handle;

    protected HandAction(ActionHandle handle) {
        this.handle = handle;
    }

    public ActionHandle handle() {
        return handle;
    }

    /**
     * 成为当前动作的那个 tick 调用
     */
    abstract void start(HandController controller);

    /**
     * 之后每个 tick 调用
     */
    void tick(HandController controller) {
    }

    /**
     * 执行中被取消时的清理，在动作以取消结束之前调用
     */
    void cleanup(HandController controller) {
    }

    /**
     * 运行期间要看向的朝向；不需要对准目标时为 null
     */
    @Nullable Rotation lookRotation(HandController controller) {
        return null;
    }

    /**
     * 是否正在使用物品（包括空手使用）
     */
    boolean usingItem() {
        return false;
    }

    /**
     * 正在使用的物品；没有在使用物品或者空手使用时为 null
     */
    @Nullable ItemStack itemInUse() {
        return null;
    }
}
