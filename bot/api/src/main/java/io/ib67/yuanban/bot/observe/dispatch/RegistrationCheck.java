package io.ib67.yuanban.bot.observe.dispatch;

import io.ib67.yuanban.bot.Bot;

/**
 * 内置 observer 在注册之前、在调用 {@code observe} 的线程上做的检查。检查失败时 {@code observe} 抛出异常，bot 不受影响。
 */
public interface RegistrationCheck {
    void checkRegistration(Bot bot);
}
