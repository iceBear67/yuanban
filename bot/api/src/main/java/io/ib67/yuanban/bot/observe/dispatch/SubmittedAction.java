package io.ib67.yuanban.bot.observe.dispatch;

import io.ib67.yuanban.bot.Bot;

/**
 * 由 bot 提交的动作。内置 observer 在注册之前（{@link RegistrationCheck}）据此检查交给自己的动作是否由同一个 bot 提交。
 */
public interface SubmittedAction {
    /**
     * 提交这个动作的 bot
     */
    Bot bot();
}
