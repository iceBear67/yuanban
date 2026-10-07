/**
 * 数据包交给调度线程之前的解码：TCP 帧读取、解码上下文、区段解码与共享。
 * TCP 连接在入站线程上执行，对象级连接在发送数据包的服务端线程上执行（帧读取只属于 TCP 连接）。
 */
@ApiStatus.Internal
package io.ib67.yuanban.bot.decode;

import org.jetbrains.annotations.ApiStatus;
