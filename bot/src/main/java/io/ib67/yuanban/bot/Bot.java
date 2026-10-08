package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverHandle;
import io.ib67.yuanban.bot.observe.builtin.BlockBrokenObserver;
import io.ib67.yuanban.bot.view.BotView;
import io.ib67.yuanban.bot.view.StateReader;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Point;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 一个 bot。动作方法只把请求放进队列，立即返回 {@link Action}；
 * 动作的效果由 observer 观察。
 */
public interface Bot {
    String username();

    BotStatus status();

    /**
     * 连接结束的原因（服务端给出的断开原因，或者本地断开、连接关闭的说明）；连接没有结束，或者 bot 在连接结束之前因异常结束时为 null。
     * 连接结束时 observer 的 {@code onClose} 抛出异常的，{@link #failure()} 为这个异常，这里仍为断开原因
     */
    @Nullable Component disconnectReason();

    /**
     * bot 因异常结束时的异常
     */
    @Nullable Throwable failure();

    // 查询与观察

    BotView view();

    /**
     * 在调度线程上执行函数，多项读取在同一时刻完成。函数返回值不应包含 {@link StateReader} 给出的可变对象
     */
    <T> T inspect(Function<StateReader, T> reader);

    /**
     * 注册 observer。在其它线程调用时等到注册完成才返回，之后应用的数据包和发生的变化都会被观察到；
     * 在调度线程上调用时立即注册，在 observer 的回调中调用时，从这个回调返回之后产生的观察记录开始投递。
     *
     * @throws IllegalStateException    observer 已经注册过
     * @throws BotDisconnectedException bot 已经断开；这时 observer 不算注册过
     * @throws BotFailureException      bot 因异常结束；在注册之前已经结束的，observer 不算注册过
     */
    <O extends Observer> ObserverHandle<O> observe(O observer);

    /**
     * 在调度线程上的同一个任务中依次完成：注册 observer（调用 onStart）、调用 submit 提交一个动作。
     * observer 最晚在这个动作的 {@link io.ib67.yuanban.bot.observe.Observation.ActionCompleted} 投递之后关闭，
     * 关闭原因为 {@link io.ib67.yuanban.bot.observe.CloseReason#ACTION_COMPLETED}。
     *
     * @param submit 在调度线程上执行，必须返回它执行期间由这个 bot 提交的一个动作
     */
    <O extends Observer> ObservedAction<O> observeDuring(O observer, Function<Bot, Action> submit);

    /**
     * 等同于 {@code observeDuring(Observers.blockBroken(pos), bot -> bot.attemptBreakBlock(pos))}
     */
    ObservedAction<BlockBrokenObserver> breakBlock(Point pos);

    /**
     * 等同于 {@code observeDuring(Observers.blockBroken(pos), bot -> bot.attemptBreakBlock(pos, face))}
     */
    ObservedAction<BlockBrokenObserver> breakBlock(Point pos, Direction face);

    /**
     * 注册一个 {@code Observers.condition(condition)} 并等它关闭。等待期间执行推进函数。
     *
     * @throws BotTimeoutException 超时；这时关闭这个 observer
     */
    void await(Predicate<StateReader> condition, Duration timeout);

    /**
     * Login (play) 处理完成时执行完毕；bot 已经在世界中时在下一轮调度中执行完毕。
     * 不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）等待下一次 Login (play)
     */
    Action joined();

    /**
     * 发送 Player Loaded 时执行完毕；当前世界已经加载时在下一轮调度中执行完毕。
     * Login (play)、Respawn 与离开世界之后加载状态重置，这时调用的等待下一次加载
     */
    Action loaded();

    // 移动

    Action moveTo(Point target);

    Action moveTo(Point target, MoveOptions options);

    Action moveForward(double blocks);

    Action moveForward(double blocks, MoveOptions options);

    Action jumpTo(Point target);

    Action jumpTo(Point target, MoveOptions options);

    /**
     * @param yaw   偏航角，单位为度
     * @param pitch 俯仰角，单位为度
     * @throws IllegalArgumentException 俯仰角不在 [-90, 90] 之内（包括 NaN）
     */
    Action rotate(float yaw, float pitch);

    Action lookAt(Point target);

    Action lookAt(int entityId);

    // 手部交互

    Action attemptBreakBlock(Point pos);

    Action attemptBreakBlock(Point pos, Direction face);

    Action placeBlock(Point against, Direction face, Hand hand);

    /**
     * @param cursor 光标在方块中的位置，各分量在 [0, 1] 之间
     */
    Action useItemOn(Point pos, Direction face, Hand hand, Point cursor);

    Action useItem(Hand hand);

    Action useItem(Hand hand, int holdTicks);

    Action attack(int entityId);

    Action interact(int entityId, Hand hand);

    // 一次性请求

    /**
     * @throws IllegalArgumentException 消息超过 256 个字符
     */
    Action chat(String message);

    /**
     * @param command 不带开头的斜线
     * @throws IllegalArgumentException 命令超过 32767 个字符
     */
    Action command(String command);

    /**
     * @param slot 快捷栏槽位（0 到 8）
     */
    Action selectHotbar(int slot);

    Action swapHands();

    Action drop(boolean wholeStack);

    Action respawn();

    InventoryActions inventory();

    /**
     * 发送任意数据包，用于本库没有封装的操作
     *
     * @throws IllegalArgumentException 不是客户端发往服务端的数据包
     */
    Action send(MinecraftPacket packet);

    // movement suspension

    void suspendMovement();

    void resumeMovement();

    /**
     * 关闭连接。未结束的动作以 {@link BotDisconnectedException} 结束，observer 以 DISCONNECTED 关闭
     */
    void disconnect();
}
