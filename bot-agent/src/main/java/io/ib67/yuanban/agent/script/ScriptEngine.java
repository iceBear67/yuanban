package io.ib67.yuanban.agent.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.io.IOAccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 全部自治脚本共用的 GraalJS Engine（共享代码缓存），按 {@link SandboxPolicy#CONSTRAINED} 创建受限的 Context：
 * 只能访问 {@link HostAccess#CONSTRAINED} 导出的方法，没有 IO、原生访问、线程创建、子进程与 polyglot 访问，
 * 标准输出与标准错误交给调用方（写入 trace）。
 * <p>
 * 主机类查找与加载保持默认的关闭状态，不设置查找过滤器：设置过滤器（即使总是返回 false）时 GraalJS 会定义全局的 {@code Java} 对象。
 */
public final class ScriptEngine implements AutoCloseable {
    private final Engine engine;
    private final String prelude;

    public ScriptEngine() {
        this.engine = Engine.newBuilder("js")
                .sandbox(SandboxPolicy.CONSTRAINED)
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        this.prelude = resource("prelude.js");
    }

    /**
     * @param out 脚本的标准输出与标准错误
     */
    Context newContext(OutputStream out, ScriptLimits limits) {
        return Context.newBuilder("js")
                .engine(engine)
                .sandbox(SandboxPolicy.CONSTRAINED)
                .allowHostAccess(HostAccess.CONSTRAINED)
                .allowIO(IOAccess.NONE)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .out(out)
                .err(out)
                .in(InputStream.nullInputStream())
                .resourceLimits(ResourceLimits.newBuilder().statementLimit(limits.maxStatementsPerTurn(), null).build())
                .option("js.esm-eval-returns-exports", "true")
                .build();
    }

    String prelude() {
        return prelude;
    }

    @Override
    public void close() {
        engine.close();
    }

    private static String resource(String name) {
        try (var stream = Objects.requireNonNull(ScriptEngine.class.getResourceAsStream(name), () -> "missing resource " + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read resource " + name, e);
        }
    }
}
