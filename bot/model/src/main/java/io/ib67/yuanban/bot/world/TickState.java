package io.ib67.yuanban.bot.world;

/**
 * Set Ticking State 与 Step Tick 给出的 tick 状态（原版 {@code TickRateManager}）。
 */
public final class TickState {
    public static final float DEFAULT_RATE = 20;

    private float tickRate = DEFAULT_RATE;
    private boolean frozen;
    private int stepTicks;
    private boolean runsNormally = true;

    public void apply(float tickRate, boolean frozen) {
        this.tickRate = tickRate;
        this.frozen = frozen;
    }

    public void step(int steps) {
        this.stepTicks = steps;
    }

    public float tickRate() {
        return tickRate;
    }

    public boolean frozen() {
        return frozen;
    }

    public int stepTicks() {
        return stepTicks;
    }

    /**
     * 每个 tick 开始时调用（原版 {@code Minecraft.tick} 开头的 {@code TickRateManager.tick}）：没有冻结或者还有要步进的 tick 时，
     * 这个 tick 正常运行；要步进的 tick 数随之减一
     */
    public void tick() {
        runsNormally = !frozen || stepTicks > 0;
        if (stepTicks > 0) {
            stepTicks--;
        }
    }

    /**
     * 原版 {@code TickRateManager.runsNormally}：本 tick 是否正常运行，由 {@link #tick()} 决定。
     * 为 false 时没有玩家乘客的实体不 tick，世界边框与时间不推进
     */
    public boolean runsNormally() {
        return runsNormally;
    }
}
