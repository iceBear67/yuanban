package io.ib67.yuanban.agent.bot;

import io.ib67.yuanban.agent.tool.ObserverSpec;
import io.ib67.yuanban.agent.view.BlockPos;
import io.ib67.yuanban.agent.view.Vec3;
import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.MovementMode;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 一个改变世界的 bot 请求，由工具参数转换而来。{@link #submit} 在 bot 上提交它（可能在调度线程上，见 {@code Bot.observeDuring}）。
 */
public sealed interface ActionSpec {
    /**
     * 对应的工具名称
     */
    String tool();

    Action submit(Bot bot);

    /**
     * 调用方没有给出 observer 时使用的 observer；没有时为 null
     */
    default @Nullable ObserverSpec defaultObserver() {
        return null;
    }

    private static MoveOptions.MoveOptionsBuilder options(@Nullable MovementMode mode) {
        return MoveOptions.DEFAULT.toBuilder().mode(mode);
    }

    record MoveTo(Vec3 target, @Nullable MovementMode mode, double closeDistance, double maxDistance) implements ActionSpec {
        @Override
        public String tool() {
            return "locomotion.moveTo";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.moveTo(target.toVec(), options(mode).closeDistance(closeDistance).maxDistance(maxDistance).build());
        }
    }

    /**
     * @param direction 只使用水平分量；为 null 时取开始时的水平朝向
     */
    record MoveForward(double blocks, @Nullable Vec3 direction, @Nullable MovementMode mode) implements ActionSpec {
        @Override
        public String tool() {
            return "locomotion.moveForward";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.moveForward(blocks, options(mode).direction(direction == null ? null : direction.toVec()).build());
        }
    }

    record JumpTo(Vec3 target, @Nullable MovementMode mode) implements ActionSpec {
        @Override
        public String tool() {
            return "locomotion.jumpTo";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.jumpTo(target.toVec(), options(mode).build());
        }
    }

    record Rotate(float yaw, float pitch) implements ActionSpec {
        @Override
        public String tool() {
            return "locomotion.rotate";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.rotate(yaw, pitch);
        }
    }

    record LookAtPoint(Vec3 target) implements ActionSpec {
        @Override
        public String tool() {
            return "locomotion.lookAt";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.lookAt(target.toVec());
        }
    }

    record LookAtEntity(int entityId) implements ActionSpec {
        @Override
        public String tool() {
            return "locomotion.lookAt";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.lookAt(entityId);
        }
    }

    /**
     * 默认使用 blockBroken observer，与 {@code Bot.breakBlock} 相同
     */
    record BreakBlock(BlockPos pos, @Nullable Direction face) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.breakBlock";
        }

        @Override
        public Action submit(Bot bot) {
            return face == null ? bot.attemptBreakBlock(pos.toVec()) : bot.attemptBreakBlock(pos.toVec(), face);
        }

        @Override
        public ObserverSpec defaultObserver() {
            return new ObserverSpec.BlockBroken(new Vec3(pos.x(), pos.y(), pos.z()));
        }
    }

    record PlaceBlock(BlockPos against, Direction face, Hand hand) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.placeBlock";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.placeBlock(against.toVec(), face, hand);
        }
    }

    record UseItemOn(BlockPos pos, Direction face, Hand hand, Vec3 cursor) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.useItemOn";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.useItemOn(pos.toVec(), face, hand, cursor.toVec());
        }
    }

    /**
     * @param holdTicks 为 null 时不保持
     */
    record UseItem(Hand hand, @Nullable Integer holdTicks) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.useItem";
        }

        @Override
        public Action submit(Bot bot) {
            return holdTicks == null ? bot.useItem(hand) : bot.useItem(hand, holdTicks);
        }
    }

    record Attack(int entityId) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.attack";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.attack(entityId);
        }
    }

    record Interact(int entityId, Hand hand) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.interact";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.interact(entityId, hand);
        }
    }

    record SelectHotbar(int slot) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.selectHotbar";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.selectHotbar(slot);
        }
    }

    record SwapHands() implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.swapHands";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.swapHands();
        }
    }

    /**
     * @param mode       {@code left}、{@code right}、{@code shift} 或 {@code swapHotbar}
     * @param hotbarSlot mode 为 {@code swapHotbar} 时的快捷栏槽位
     */
    record Click(int slot, String mode, @Nullable Integer hotbarSlot) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.click";
        }

        @Override
        public Action submit(Bot bot) {
            var inventory = bot.inventory();
            return switch (mode) {
                case "left" -> inventory.leftClick(slot);
                case "right" -> inventory.rightClick(slot);
                case "shift" -> inventory.shiftClick(slot);
                case "swapHotbar" -> inventory.swapWithHotbar(slot, Objects.requireNonNull(hotbarSlot, "hotbarSlot"));
                default -> throw new IllegalArgumentException("unknown click mode " + mode);
            };
        }
    }

    record ClickButton(int buttonId) implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.clickButton";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.inventory().clickButton(buttonId);
        }
    }

    record CloseWindow() implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.closeWindow";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.inventory().close();
        }
    }

    record Respawn() implements ActionSpec {
        @Override
        public String tool() {
            return "interaction.respawn";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.respawn();
        }
    }

    record Chat(String message) implements ActionSpec {
        @Override
        public String tool() {
            return "external.chat";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.chat(message);
        }
    }

    /**
     * @param command 不带开头的斜线
     */
    record Command(String command) implements ActionSpec {
        @Override
        public String tool() {
            return "external.command";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.command(command);
        }
    }

    record Drop(boolean wholeStack) implements ActionSpec {
        @Override
        public String tool() {
            return "external.drop";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.drop(wholeStack);
        }
    }

    record DropSlot(int slot, boolean wholeStack) implements ActionSpec {
        @Override
        public String tool() {
            return "external.dropSlot";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.inventory().dropSlot(slot, wholeStack);
        }
    }

    record Send(MinecraftPacket packet) implements ActionSpec {
        @Override
        public String tool() {
            return "unsafe.send";
        }

        @Override
        public Action submit(Bot bot) {
            return bot.send(packet);
        }
    }
}
