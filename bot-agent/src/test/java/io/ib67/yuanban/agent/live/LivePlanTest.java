package io.ib67.yuanban.agent.live;

import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.bot.LiveBotFacade;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.plan.BundleResult;
import io.ib67.yuanban.agent.plan.PlanStack;
import io.ib67.yuanban.agent.script.ScriptLimits;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.transport.TcpTransport;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计划层连接真实的 Minestom 服务端：bot 通过 TCP 加入，内置模块在受限 Context 中运行，效果在服务端一侧检查。
 */
class LivePlanTest {
    private static LiveServer server;

    @TempDir
    Path dir;
    private BotRuntime runtime;
    private PlanStack stack;

    @BeforeAll
    static void startServer() {
        server = LiveServer.get();
    }

    private PlanStack connect(String username) throws Exception {
        runtime = BotRuntime.create();
        var bot = runtime.connect(BotOptions.builder().username(username)
                .transport(new TcpTransport("127.0.0.1", server.port)).build());
        var facade = new LiveBotFacade(bot);
        facade.loaded().get(30, TimeUnit.SECONDS);
        stack = new PlanStack(dir, facade, AgentProfile.defaults(), ScriptLimits.DEFAULT);
        return stack;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stack != null) {
            stack.close();
        }
        if (runtime != null) {
            runtime.close();
        }
    }

    private Pos position(String username) {
        return Objects.requireNonNull(server.player(username), username + " is not online").getPosition();
    }

    @Test
    void travelBundleMovesTheBotToTheTarget() throws Exception {
        var stack = connect("travel1");
        var bundle = stack.bundle("""
                {"primary": "travel@1", "input": {"target": {"x": 6.5, "y": 64, "z": 3.5}}, "supervisor": "movement-watchdog@1"}
                """);
        var result = bundle.done().get(60, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.COMPLETED, result.status(), Json.write(result.toJson()));
        assertEquals("arrived", Objects.requireNonNull(result.primary()).value().getAsJsonObject().get("kind").getAsString());
        assertEquals("drained", Objects.requireNonNull(result.supervisor()).value().getAsJsonObject().get("kind").getAsString());
        assertTrue(position("travel1").distance(new Vec(6.5, 64, 3.5)) < 2, "server position " + position("travel1"));
    }

    @Test
    void cancellingTheBundleStopsTheMovement() throws Exception {
        var stack = connect("travel2");
        var bundle = stack.bundle("""
                {"primary": "travel@1", "input": {"target": {"x": 30.5, "y": 64, "z": 0.5}}, "supervisor": "movement-watchdog@1"}
                """);
        Thread.sleep(1500);
        var moved = position("travel2");
        assertTrue(moved.distance(LiveServer.SPAWN) > 1, "the bot did not start moving: " + moved);
        bundle.cancel("test stop");
        var result = bundle.done().get(10, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.CANCELLED, result.status());
        Thread.sleep(500);
        var stopped = position("travel2");
        Thread.sleep(1000);
        assertTrue(position("travel2").distance(stopped) < 0.2, "the bot kept moving after cancel");
        assertTrue(stopped.x() < 29, "the bot reached the target before the cancel took effect");
    }

    @Test
    void breakingABlockInAnUnloadedChunkDoesNotEndTheBot() throws Exception {
        var stack = connect("guard1");
        var module = stack.draft("far-break", """
                export async function main(api) {
                    const result = await api.attempt(api.actions.breakBlock({x: 4000, y: 63, z: 4000}));
                    const status = await api.observe.status();
                    return {kind: result.kind, reason: result.reason, status: status.status};
                }
                """, "{\"roles\": [\"primary\"], \"capabilities\": [\"observe.*\", \"interaction.breakBlock\"], \"description\": \"test\"}");
        var result = stack.bundle("{\"primary\": \"" + module + "\"}").done().get(20, TimeUnit.SECONDS);
        var value = Objects.requireNonNull(result.primary()).value().getAsJsonObject();
        assertEquals("ACTION_CANCELLED", value.get("kind").getAsString(), Json.write(result.toJson()));
        assertEquals("CHUNK_NOT_LOADED", value.get("reason").getAsString());
        assertEquals(BotStatus.PLAY.name(), value.get("status").getAsString());
    }

    @Test
    void mineBlockBreaksTheBlockOnTheServer() throws Exception {
        var stack = connect("miner1");
        server.world.setBlock(2, 64, 0, Block.DIRT);
        var result = stack.bundle("{\"primary\": \"mine-block@1\", \"input\": {\"pos\": {\"x\": 2, \"y\": 64, \"z\": 0}}}")
                .done().get(60, TimeUnit.SECONDS);
        var value = Objects.requireNonNull(result.primary()).value().getAsJsonObject();
        assertEquals("broken", value.get("kind").getAsString(), Json.write(result.toJson()));
        assertTrue(server.world.getBlock(2, 64, 0).air(), "the block is still " + server.world.getBlock(2, 64, 0));
    }
}
