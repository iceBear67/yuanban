# 连接与解码

[目录](README.md) · 上一篇：[线程与调度](threading.md) · 下一篇：[协议状态机](protocol.md)

本篇目录：

- [连接接口](#连接接口)
- [连接的代数](#连接的代数)
- [TCP 连接](#tcp-连接)
  - [建立连接](#建立连接)
  - [入站](#入站)
  - [出站](#出站)
  - [关闭](#关闭)
  - [读取超时](#读取超时)
  - [加密](#加密)
- [进程内连接](#进程内连接)
- [解码上下文](#解码上下文)
- [区段解码](#区段解码)
- [区段池](#区段池)

相关源码：[`transport`](../../bot/src/main/java/io/ib67/yuanban/bot/transport)、[`decode`](../../bot/src/main/java/io/ib67/yuanban/bot/decode)。

## 连接接口

```java
public interface Transport {
    /// 打开连接。inbound 接收连接交出的数据包与连接关闭通知
    ClientConnection open(String username, InboundSink inbound);
}

public interface ClientConnection {
    String serverAddress();   // 握手包中的服务端地址
    int serverPort();         // 握手包中的服务端端口

    /// 只由调度线程调用；本轮的数据包一次交出，保持顺序，全部是发往服务端的数据包。
    /// 返回的 future 在数据包交给连接之后完成（TCP 连接在写进套接字之后）；连接在写出之前关闭时以异常完成
    CompletableFuture<Void> send(List<MinecraftPacket> packets);

    /// 关闭连接，不等待对端读取：已经交出的数据包按顺序写出，写到连接不能再立即接受数据为止，
    /// 没有写出的部分被丢弃，然后关闭；之后 InboundSink.closed 被调用一次
    void close();
}

public interface InboundSink {
    /// 按到达顺序交出一个来自服务端的数据包。调用不会并发
    void accept(MinecraftPacket packet);

    /// 连接关闭，只调用一次。error 为连接上的异常；正常关闭（对端关闭或本地调用 close）时为 null
    void closed(@Nullable Throwable error);
}

@FunctionalInterface
public interface TransportResolver {
    TransportResolver TCP = TcpTransport::new;   // 默认实现
    Transport resolve(String host, int port);
}
```

- 本库提供的实现为 `TcpTransport`。进程内连接由外部模块实现 `Transport` 与 `ClientConnection`。
- `TransportResolver` 在处理 Transfer 时为新地址选择连接方式，测试可以把特定地址映射到别的 `Transport`。
- `ProtocolMachine` 只面对 `MinecraftPacket` 对象，与连接方式无关。

## 连接的代数

`BotInstance` 用一个整数 `generation` 区分连接：

- `open()`（第一条连接，在调用线程上）与 `transfer()`（Transfer 线程上的新连接）都先把代数加一，再用这个代数创建 `Sink`。
- `Sink` 交给入站队列的每个条目都带有它的代数。调度线程丢弃代数与当前不同的条目，所以 Transfer 之后旧连接的数据包与关闭通知都被忽略。
- 新连接打开之后，接入任务（`attach`）在调度线程上检查代数：代数已经改变或者 bot 已经结束时关闭这条连接；否则把它设为当前连接，新建 `BundleBuffer`，调用 `ProtocolMachine.startLogin` 写入 Handshake 与 Login Start。
- 每条连接有自己的 `DecodeContext`（在 `Sink` 中创建）。

## TCP 连接

`TcpConnection` 使用 MCProtocolLib 的 Netty 管线完成分帧、压缩与编解码。MCProtocolLib 的默认监听器（自动完成登录与配置、自动回应 Keep Alive 等）不启用：协议阶段由 `TcpConnection` 按数据包切换，登录与配置的应答由 bot 的协议状态机产生。

### 建立连接

`TcpTransport.open` 调用 `TcpConnection.open(host, port, inbound)`：

- 每条连接新建一个 `MinecraftProtocol(MinecraftCodec.CODEC)`，调用 `setUseDefaultListeners(false)`。它保存这条连接的入站与出站阶段，只在事件循环上读写。
- 会话为包内可见的 `BotClientSession extends ClientNetworkSession`：
  - 数据包处理执行器为 `Runnable::run`，全部数据包在 Netty 事件循环上按到达顺序处理。MCProtocolLib 默认把一部分数据包（例如区块数据）交给另一个线程处理，那样它们与其余数据包之间的顺序没有保证；
  - 事件循环组为 `NettyGroup`；
  - 管线中的 `read-timeout` 处理器换成 `LastReadHandler`。
- 不做 SRV 解析（`ATTEMPT_SRV_RESOLVE` 为 false），远端地址为 `new InetSocketAddress(host, port)`。MCProtocolLib 开启 `TCP_NODELAY`。
- 在调用线程上等待连接建立。失败时 `IOException` 包装成 `UncheckedIOException` 抛出，其它异常原样抛出；连接在建立之前关闭时抛出说明这件事的 `IOException`（同样包装）。

### 入站

在 Netty 事件循环上（`TcpConnection.Listener.packetReceived`）：

1. MCProtocolLib 按当前入站阶段分帧、解压、解码，数据包读完之后还有剩余字节时出错。
2. 收到 Set Compression 时立即调用 `setCompression`：阈值不小于 0 时为 `new CompressionConfig(阈值, new ZlibCompression(), false)`（最后一个参数与原版客户端的 `validateDecompressed` 相同），否则关闭压缩。同一次读取中紧跟的数据包已经是压缩格式，MCProtocolLib 按通道属性中的设置逐帧处理，所以下一帧就按新的设置解压。
3. 调用 `InboundSink.accept`。
4. 交给 `InboundSink` 之后立即切换入站阶段：Login Success 与 Start Configuration 之后为配置阶段，Finish Configuration 之后为游戏阶段。MCProtocolLib 解码出这些数据包时停止从套接字读取，`switchInboundState` 切换之后恢复，所以同一次读取中已经收到的后续帧按新的阶段解码。
5. 解码失败与 `accept` 抛出的异常由 MCProtocolLib 以这个异常断开会话，连接以这个异常关闭。

### 出站

`send(List<MinecraftPacket>)`：

- 已经调用过 `close()` 时，返回以 `ClosedChannelException` 完成的 future。
- 否则在通道的事件循环上执行一个任务：通道已经不活动时以 `ClosedChannelException` 完成；否则对每个数据包调用 `channel.write`（编码按当前出站阶段完成），每写入一个改变阶段的数据包之后立即切换阶段，最后 flush 一次。同一 tick 的数据包因此一起写出（wiki FAQ 的建议）。
- 阶段的切换：

  | 写入的数据包 | 切换 |
  |---|---|
  | `ClientIntentionPacket` | 出站与入站都切换为登录阶段（意图为 STATUS 时为状态阶段）；服务端从这时开始按登录阶段回应 |
  | `ServerboundLoginAcknowledgedPacket` | 出站切换为配置阶段 |
  | `ServerboundFinishConfigurationPacket` | 出站切换为游戏阶段 |
  | `ServerboundConfigurationAcknowledgedPacket` | 出站切换为配置阶段 |

- 返回的 future 在最后一个数据包写进套接字时完成。任何一个写出失败（编码失败、套接字错误）时，先以这个异常完成 future，然后在没有调用过 `close()` 时关闭通道，记下这个异常作为关闭通知的原因。关闭通道时同一批中还没有写出的数据包以 `ClosedChannelException` 失败，future 已经先完成，所以带有的是第一个失败的原因。写出失败因此一定结束连接。
- MCProtocolLib 的 `switchOutboundState` 与 `disconnect` 会等待通道上的操作完成，所以调度线程上不调用它们。

### 关闭

按原版客户端的行为：原版 `Connection.send` 把每个数据包作为任务交给事件循环，`disconnect` 调用 `channel.close()`，关闭任务排在之前的发送任务之后；Netty 关闭通道时丢弃还没有写进套接字的数据，已经在套接字发送缓冲区中的数据由操作系统照常发出。

`close()` 标记开始关闭，然后调用 `channel.close()`。所以：

- `close()` 之前交出的批次按顺序写出，写到套接字不能再接受数据为止；没有写进套接字的部分以异常完成。
- 关闭从不等待对端读取：对端长时间不读取、发送缓冲区已满时，连接随即关闭，套接字被释放。
- `close()` 之后交出的批次立即以异常完成。
- 连接因对端关闭或出错而结束时，还没有写完的批次都以异常完成。每个交给连接的批次都有确定的结局。
- `InboundSink.closed` 只调用一次：调用过 `close()` 时为 null，否则为断开的原因或者写出失败的异常；对端正常关闭时为 null。

### 读取超时

- `LastReadHandler` 位于管线的最前面，每次读到数据时以 volatile 记录 `System.nanoTime()`。
- 调度线程每轮检查（`BotInstance.checkReadTimeout`）：距离上次读到数据超过 `BotOptions.readTimeout` 时，以「timed out after … without data from the server」断开。默认 30 秒，与原版客户端 `Connection` 中的 `ReadTimeoutHandler(30)` 相同；`Duration.ZERO` 表示不检测。
- 登录与配置阶段不 tick，`round` 的返回值包含超时到期的时间，调度线程按它醒来检查。
- MCProtocolLib 自己的读取超时以整数秒计，所以换掉。
- 读取超时只对 `TcpConnection` 检查。

### 加密

不支持。解码上下文收到 Encryption Request（`ClientboundHelloPacket`）时抛出 `UnsupportedOperationException`，连接以这个异常关闭，bot 结束。正版验证不在本库范围内；离线模式的原版服务端不使用加密。

## 进程内连接

本库不提供进程内连接，由外部模块实现 `Transport` 与 `ClientConnection`，要求如下：

- 把服务端发出的数据包对象按顺序交给 `InboundSink.accept`，调用必须串行。解码上下文与区段解码在调用它的线程上执行。
- 把 bot 交出的数据包按顺序交给服务端；`send` 返回的 future 在数据包交给服务端之后完成，连接在交出之前关闭时以异常完成。
- `close()` 之后调用一次 `InboundSink.closed`。
- 读取超时只对 `TcpConnection` 检查，进程内连接没有读取超时。
- 服务端需要手动推进时设置推进函数，见[线程与调度 · 阻塞等待与推进函数](threading.md#阻塞等待与推进函数)。

测试中的 `TestPeer` 是一个这样的实现，见[测试](testing.md#testpeer)。

## 解码上下文

每条连接一个 `DecodeContext`，在连接调用 `InboundSink.accept` 的线程上按数据包到达顺序执行。它维护数据包到达调度线程之前需要的信息：

| 内容 | 用途 | 更新时机 |
|---|---|---|
| `SyncedRegistriesBuilder` | 累积配置阶段的 Registry Data，构建 `SyncedRegistries` | Login Success 与 Start Configuration 开始一个新的配置阶段；Registry Data 累积条目 |
| `TagTable` | 随 Update Tags 与 Finish Configuration 交给调度线程 | Update Tags 按标签逐个替换；同一个配置阶段的第一个 Registry Data 删除之前全部同步注册表的标签 |
| `SyncedRegistries` | 维度类型的最低 y 与高度、生物群系数量 | Finish Configuration |
| 当前维度（`DimensionInfo`） | 区段数量 = 高度 / 16；方块实体所在的区段 | Login (play)、Respawn：按 `PlayerSpawnInfo` 中维度类型的编号从 `SyncedRegistries` 取出 |

`DecodeContext.process` 的返回值作为附加数据，随数据包一起放进 `InboundPacket`：

| 数据包 | 附加数据 |
|---|---|
| `ClientboundHelloPacket`（Encryption Request） | 抛出 `UnsupportedOperationException` |
| `ClientboundLevelChunkWithLightPacket` | 解码出的 `DecodedChunk`；还没有收到 Login (play) 时出错 |
| `ClientboundUpdateTagsPacket` | 更新后的 `TagTable` |
| `ClientboundFinishConfigurationPacket` | `ConfigurationFinished(SyncedRegistries, TagTable)` |
| 其它 | null |

注册表与标签的构建规则见[注册表与静态数据](registries-and-data.md)。

## 区段解码

`SectionDecoder.decode(packet, minY, height, biomeCount)`：

1. 对 `getChunkData()` 依次切出每个区段的原始字节，只跳过、不解码：两个计数共 4 个字节，然后是方块与生物群系两个调色板容器。每个容器先是位数：位数为 0 时是一个 VarInt；位数不超过这种调色板的间接调色板上限（方块 8，生物群系 3）时是调色板长度与各个编号；位数不为 0 时之后是存放全部条目所需的 long，每个 long 放 `64 / 位数` 个条目。
2. 以字节内容与生物群系数量为键在 `SectionPool` 中查找，命中时直接复用。
3. 未命中时用 `MinecraftTypes.readChunkSection(字节, BlockStates.REGISTRY_SIZE, 生物群系数量)` 解码为方块与生物群系两个 `DataPalette`，构造 `SharedSection` 放入池中。
4. 区段数为维度高度除以 16，读完之后数据还有剩余字节时出错。
5. 光照数据与高度图在这一步丢弃。
6. 方块实体由区段中该位置的方块决定：对维度高度范围之内的每个 `BlockEntityInfo`，取区段中该位置的方块，Minestom `Block.fromStateId(状态).blockEntityType()` 的编号等于数据包中类型的序号时才保留，否则忽略（原版 `LevelChunk.replaceWithPacketData` 的行为）。没有 NBT 时为空的 `NbtMap`。
7. 结果为 `DecodedChunk(区块 x, 区块 z, SectionRef[], Map<chunkBlockIndex, NbtMap>)`，每个 `SectionRef` 标记为共享。

调度线程只安装解码结果，见[世界模型 · 区块](world-model.md#区块)。

## 区段池

`SectionPool.SHARED` 在同一个 JVM 的全部 bot 之间共用：

- 键为区段的原始字节、字节的哈希值与生物群系数量，比较时比较全部字节。
- 值为 `SharedSection`，以 Caffeine 的弱引用值保存，没有 bot 引用时由垃圾回收释放。
- 按原始字节去重的原因：MCProtocolLib 的 `DataPalette.equals` 与调色板中条目的顺序有关，而且查找之前要先解码。
- 生物群系数量参与比较，因为它决定写入生物群系时扩展出的全局调色板的位数。

共享的区段永远不被写入，写入之前先复制，见[世界模型 · 区段共享与写入时复制](world-model.md#区段共享与写入时复制)。

[目录](README.md) · 上一篇：[线程与调度](threading.md) · 下一篇：[协议状态机](protocol.md)
