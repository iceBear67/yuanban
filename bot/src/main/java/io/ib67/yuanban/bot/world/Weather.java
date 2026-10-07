package io.ib67.yuanban.bot.world;

/**
 * 天气：Game Event 7（雨量）与 8（雷暴强度）。
 * 事件 1、2 的效果与名称相反，原版服务端紧接着会发送事件 7，所以忽略。
 */
public final class Weather {
    private float rainLevel;
    private float thunderLevel;

    public void rainLevel(float rainLevel) {
        this.rainLevel = rainLevel;
    }

    public void thunderLevel(float thunderLevel) {
        this.thunderLevel = thunderLevel;
    }

    public float rainLevel() {
        return rainLevel;
    }

    public float thunderLevel() {
        return thunderLevel;
    }
}
