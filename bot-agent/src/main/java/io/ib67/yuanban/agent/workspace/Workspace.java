package io.ib67.yuanban.agent.workspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.plan.PlanRole;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceStore;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 磁盘上的 workspace。脚本只能通过 ToolGateway 提供的虚拟 API 访问它，没有文件系统权限。
 * <pre>
 * modules/&lt;名称&gt;/&lt;版本&gt;/module.js、manifest.json、fixtures/*.json、reports/*.json
 * scripts/&lt;lineage&gt;/checkpoints/*.json、latest、ledger.json
 * memories/&lt;键&gt;.json
 * runs/&lt;run&gt;.json
 * </pre>
 */
public final class Workspace {
    public static final Pattern MEMORY_KEY = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    /**
     * 内置模块所在的资源目录，其中 {@code index.json} 列出 {@code [{"ref": "name@version", "fixtures": [文件名...]}]}
     */
    private static final String BUILTIN_ROOT = "/io/ib67/yuanban/agent/modules/";

    private final Path root;
    private final TraceStore trace;
    private final InstantSource clock;
    private final AtomicLong checkpointCounter = new AtomicLong();

    private Workspace(Path root, TraceStore trace, InstantSource clock) {
        this.root = root;
        this.trace = trace;
        this.clock = clock;
    }

    /**
     * @throws UncheckedIOException 不能创建目录
     */
    public static Workspace open(Path root, TraceStore trace, InstantSource clock) {
        try {
            for (var dir : List.of("modules", "scripts", "memories", "runs")) {
                Files.createDirectories(root.resolve(dir));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create workspace " + root, e);
        }
        return new Workspace(root, trace, clock);
    }

    public Path root() {
        return root;
    }

    /**
     * 把内置模块安装为 tested；已经存在的同一版本必须有相同的源码
     *
     * @throws IllegalStateException workspace 中同一版本的源码与内置模块不同
     */
    public synchronized void installBuiltins() {
        var index = Json.parse(resource("index.json")).getAsJsonArray();
        for (var element : index) {
            var entry = element.getAsJsonObject();
            var ref = ModuleRef.parse(Json.requireString(entry, "ref"));
            var base = ref.name() + "/" + ref.version() + "/";
            var source = resource(base + "module.js");
            var fields = Json.parseObject(resource(base + "manifest.json"));
            var hash = Json.sha256(source);
            var existing = manifestFile(ref);
            if (Files.exists(existing)) {
                var manifest = load(ref).manifest();
                if (!manifest.sourceHash().equals(hash)) {
                    throw new IllegalStateException("workspace module " + ref + " differs from the builtin module with the same version");
                }
                continue;
            }
            var fixtures = new ArrayList<JsonObject>();
            for (var fixture : entry.getAsJsonArray("fixtures")) {
                fixtures.add(Json.parseObject(resource(base + "fixtures/" + fixture.getAsString())));
            }
            var manifest = ModuleManifest.draft(ref, fields, hash, "builtin", clock.millis())
                    .withStatus(ModuleStatus.TESTED, null);
            write(ref, manifest, source, fixtures);
        }
    }

    /**
     * 内置模块清单中的全部模块
     */
    public static List<ModuleRef> builtinModules() {
        var result = new ArrayList<ModuleRef>();
        for (var element : Json.parse(resource("index.json")).getAsJsonArray()) {
            result.add(ModuleRef.parse(Json.requireString(element.getAsJsonObject(), "ref")));
        }
        return result;
    }

    public synchronized List<ModuleManifest> list() {
        var dir = root.resolve("modules");
        var result = new ArrayList<ModuleManifest>();
        try (Stream<Path> names = Files.list(dir)) {
            for (var name : names.sorted().toList()) {
                try (Stream<Path> versions = Files.list(name)) {
                    for (var version : versions.toList()) {
                        var manifest = version.resolve("manifest.json");
                        if (Files.exists(manifest)) {
                            result.add(ModuleManifest.fromJson(Json.parseObject(Files.readString(manifest))));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list modules in " + dir, e);
        }
        result.sort(Comparator.comparing(ModuleManifest::name).thenComparingInt(ModuleManifest::version));
        return result;
    }

    /**
     * 名称、说明、角色或能力中包含查询词（不区分大小写）的模块
     */
    public List<ModuleManifest> search(String query) {
        var needle = query.toLowerCase(Locale.ROOT);
        return list().stream().filter(manifest -> manifest.name().contains(needle)
                || manifest.description().toLowerCase(Locale.ROOT).contains(needle)
                || manifest.roles().stream().anyMatch(role -> role.wire().equals(needle))
                || manifest.capabilities().stream().anyMatch(capability -> capability.toLowerCase(Locale.ROOT).contains(needle))).toList();
    }

    public synchronized Optional<Module> get(ModuleRef ref) {
        return Files.exists(manifestFile(ref)) ? Optional.of(load(ref)) : Optional.empty();
    }

    /**
     * 读取模块并检查源码 hash
     *
     * @throws IllegalArgumentException 没有这个模块
     * @throws IllegalStateException    源码与 manifest 中的 hash 不同
     */
    public synchronized Module load(ModuleRef ref) {
        var dir = moduleDir(ref);
        if (!Files.exists(dir.resolve("manifest.json"))) {
            throw new IllegalArgumentException("no module " + ref + " in the workspace");
        }
        try {
            var manifest = ModuleManifest.fromJson(Json.parseObject(Files.readString(dir.resolve("manifest.json"))));
            var source = Files.readString(dir.resolve("module.js"));
            if (!Json.sha256(source).equals(manifest.sourceHash())) {
                throw new IllegalStateException("source of " + ref + " does not match its hash " + manifest.sourceHash());
            }
            var fixtures = new ArrayList<JsonObject>();
            var fixtureDir = dir.resolve("fixtures");
            if (Files.isDirectory(fixtureDir)) {
                try (Stream<Path> files = Files.list(fixtureDir)) {
                    for (var file : files.sorted().toList()) {
                        fixtures.add(Json.parseObject(Files.readString(file)));
                    }
                }
            }
            return new Module(manifest, source, fixtures);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read module " + ref, e);
        }
    }

    /**
     * 最新的一个版本；没有时为 null
     */
    public @Nullable ModuleManifest latest(String name) {
        return list().stream().filter(manifest -> manifest.name().equals(name))
                .max(Comparator.comparingInt(ModuleManifest::version)).orElse(null);
    }

    /**
     * 保存新的 draft 版本（已有的最大版本加一）
     *
     * @throws io.ib67.yuanban.agent.json.SchemaViolationException manifest 字段不符合 schema
     */
    public synchronized ModuleManifest saveDraft(String name, String source, JsonObject fields, List<JsonObject> fixtures,
                                                 String createdBy) {
        var latest = latest(name);
        var ref = new ModuleRef(name, latest == null ? 1 : latest.version() + 1);
        var manifest = ModuleManifest.draft(ref, fields, Json.sha256(source), createdBy, clock.millis());
        write(ref, manifest, source, fixtures);
        trace.append(TraceKind.MODULE, Json.object("event", "draft", "module", ref.toString(), "sourceHash",
                manifest.sourceHash(), "createdBy", createdBy));
        return manifest;
    }

    /**
     * 修改模块状态，写入测试报告
     */
    public synchronized ModuleManifest setStatus(ModuleRef ref, ModuleStatus status, @Nullable JsonObject testReport) {
        var manifest = load(ref).manifest();
        String reportName = manifest.testReport();
        if (testReport != null) {
            reportName = "report-" + clock.millis() + ".json";
            writeFile(moduleDir(ref).resolve("reports").resolve(reportName), Json.PRETTY.toJson(testReport));
        }
        var updated = manifest.withStatus(status, reportName);
        writeFile(manifestFile(ref), Json.PRETTY.toJson(updated.toJson()));
        trace.append(TraceKind.MODULE, Json.object("event", status.wire(), "module", ref.toString(), "testReport", reportName));
        return updated;
    }

    public synchronized @Nullable JsonObject testReport(ModuleRef ref) {
        var manifest = load(ref).manifest();
        if (manifest.testReport() == null) {
            return null;
        }
        try {
            return Json.parseObject(Files.readString(moduleDir(ref).resolve("reports").resolve(manifest.testReport())));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read the test report of " + ref, e);
        }
    }

    // 检查点

    public Checkpoint saveCheckpoint(String planId, String lineage, ModuleRef module, String sourceHash, String name,
                                     long mailboxCursor, JsonElement data, List<String> inFlight, JsonObject ledger) {
        var checkpoint = new Checkpoint("cp-" + checkpointCounter.incrementAndGet() + "-" + clock.millis(), planId, lineage,
                module, sourceHash, name, mailboxCursor, data, inFlight, ledger, clock.millis());
        var dir = scriptDir(lineage);
        writeFile(dir.resolve("checkpoints").resolve(checkpoint.id() + ".json"), Json.write(checkpoint.toJson()));
        writeFile(dir.resolve("latest"), checkpoint.id());
        return checkpoint;
    }

    public synchronized @Nullable Checkpoint latestCheckpoint(String lineage) {
        var dir = scriptDir(lineage);
        var latest = dir.resolve("latest");
        if (!Files.exists(latest)) {
            return null;
        }
        try {
            var id = Files.readString(latest).trim();
            return Checkpoint.fromJson(Json.parseObject(Files.readString(dir.resolve("checkpoints").resolve(id + ".json"))));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read the checkpoint of " + lineage, e);
        }
    }

    public IdempotencyLedger ledger(String lineage) {
        return IdempotencyLedger.open(scriptDir(lineage).resolve("ledger.json"));
    }

    /**
     * 删除 lineage 以 prefix 开头的检查点与账本（模块测试结束之后）
     */
    public synchronized void deleteScripts(String prefix) {
        try (Stream<Path> dirs = Files.list(root.resolve("scripts"))) {
            for (var dir : dirs.filter(dir -> dir.getFileName().toString().startsWith(prefix)).toList()) {
                try (Stream<Path> files = Files.walk(dir)) {
                    for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(file);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to delete test scripts " + prefix, e);
        }
    }

    // 记忆

    /**
     * @return 没有这个键时为 null
     * @throws IllegalArgumentException 键不合法
     */
    public synchronized @Nullable JsonObject readMemory(String key) {
        var file = memoryFile(key);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Json.parseObject(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read memory " + key, e);
        }
    }

    /**
     * 写入一条事实
     */
    public synchronized void writeMemory(String key, JsonElement value, String source) {
        writeFile(memoryFile(key), Json.PRETTY.toJson(Json.object("key", key, "value", value, "source", source,
                "at", clock.millis())));
        trace.append(TraceKind.MODULE, Json.object("event", "memory", "key", key, "source", source));
    }

    public synchronized List<String> memoryKeys() {
        try (Stream<Path> files = Files.list(root.resolve("memories"))) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length())).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list memories", e);
        }
    }

    // 运行记录

    public void writeRun(String runId, JsonObject metadata) {
        writeFile(root.resolve("runs").resolve(runId + ".json"), Json.PRETTY.toJson(metadata));
    }

    private void write(ModuleRef ref, ModuleManifest manifest, String source, List<JsonObject> fixtures) {
        var dir = moduleDir(ref);
        writeFile(dir.resolve("module.js"), source);
        for (var i = 0; i < fixtures.size(); i++) {
            var fixture = fixtures.get(i);
            var name = Json.string(fixture, "name");
            var file = String.format(Locale.ROOT, "%02d-%s.json", i, name == null ? "fixture" : name.replaceAll("[^a-zA-Z0-9_-]", "_"));
            writeFile(dir.resolve("fixtures").resolve(file), Json.PRETTY.toJson(fixture));
        }
        writeFile(dir.resolve("manifest.json"), Json.PRETTY.toJson(manifest.toJson()));
    }

    private Path moduleDir(ModuleRef ref) {
        return root.resolve("modules").resolve(ref.name()).resolve(String.valueOf(ref.version()));
    }

    private Path manifestFile(ModuleRef ref) {
        return moduleDir(ref).resolve("manifest.json");
    }

    private Path scriptDir(String lineage) {
        if (!lineage.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("invalid lineage " + lineage);
        }
        return root.resolve("scripts").resolve(lineage);
    }

    private Path memoryFile(String key) {
        if (!MEMORY_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid memory key " + key);
        }
        return root.resolve("memories").resolve(key + ".json");
    }

    private static void writeFile(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            var temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + file, e);
        }
    }

    private static String resource(String path) {
        try (InputStream stream = Objects.requireNonNull(Workspace.class.getResourceAsStream(BUILTIN_ROOT + path),
                () -> "missing builtin module resource " + path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read builtin module resource " + path, e);
        }
    }

    /**
     * 把清单列表转换为给模型看的 JSON
     */
    public static JsonArray describe(List<ModuleManifest> manifests) {
        var array = new JsonArray();
        for (var manifest : manifests) {
            array.add(Json.object("module", manifest.ref().toString(), "status", manifest.status().wire(),
                    "roles", manifest.roles().stream().map(PlanRole::wire).toList(),
                    "capabilities", manifest.capabilities(), "description", manifest.description(),
                    "inputSchema", manifest.inputSchema(), "fallback", manifest.fallback().wire()));
        }
        return array;
    }
}
