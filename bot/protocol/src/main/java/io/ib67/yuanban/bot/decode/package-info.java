/**
 * 数据包交给调度线程之前的处理：解码上下文、区段解码与共享。在连接交出数据包的线程上执行（TCP 连接为 Netty 事件循环）。
 */
@ApiStatus.Internal
package io.ib67.yuanban.bot.decode;

import org.jetbrains.annotations.ApiStatus;
