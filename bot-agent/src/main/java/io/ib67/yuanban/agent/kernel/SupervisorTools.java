package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.model.ToolCall;
import io.ib67.yuanban.agent.model.ToolDefinition;
import io.ib67.yuanban.agent.tool.ToolCatalog;
import io.ib67.yuanban.agent.tool.ToolSpec;
import io.ib67.yuanban.agent.trace.TraceReader;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import io.ib67.yuanban.agent.workspace.ModuleTester;
import io.ib67.yuanban.agent.workspace.Workspace;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 模型在一个回合中可以调用的只读查询，以及最后必须调用的 {@code submit_decision}。查询在模型回合的虚拟线程上执行。
 */
final class SupervisorTools {
    /**
     * 一次查询结果的最大字符数，超过时截断并标记 truncated
     */
    private static final int MAX_RESULT_CHARS = 60_000;
    private static final int MAX_TRACE_RECORDS = 50;

    private final Workspace workspace;
    private final ModuleTester tester;
    private final ToolCatalog catalog;
    private final Path tracePath;
    private final Supplier<JsonObject> status;

    SupervisorTools(Workspace workspace, ModuleTester tester, ToolCatalog catalog, Path tracePath, Supplier<JsonObject> status) {
        this.workspace = workspace;
        this.tester = tester;
        this.catalog = catalog;
        this.tracePath = tracePath;
        this.status = status;
    }

    /**
     * 全部只读查询；每个模式从中选择自己提供给模型的部分
     */
    static List<ToolDefinition> queries() {
        return List.of(
                tool("workspace_list_modules", "列出 workspace 中的全部模块（名称@版本、状态、角色、能力、说明、输入 schema）", "{}"),
                tool("workspace_search_modules", "按名称、说明、角色或能力搜索模块",
                        "{\"query\": {\"type\": \"string\", \"minLength\": 1, \"maxLength\": 100}}", "query"),
                tool("workspace_get_module", "读取一个模块版本的 manifest 与源码",
                        "{\"module\": {\"type\": \"string\", \"description\": \"name@version\"}}", "module"),
                tool("workspace_test_module", "对一个模块运行 module.test（replay/dry-run fixture），返回报告；draft 全部通过时变为 tested",
                        "{\"module\": {\"type\": \"string\", \"description\": \"name@version\"}}", "module"),
                tool("read_reference", "读取冷数据引用：trace:<序号>、trace:<起>-<止>、trace:msg/<消息编号>、workspace:module/<名称>/<版本>、"
                                + "workspace:report/<名称>/<版本>、workspace:checkpoint/<lineage>、workspace:memory/<键>",
                        "{\"ref\": {\"type\": \"string\", \"maxLength\": 200}}", "ref"),
                tool("memory_read", "读取一条记忆", "{\"key\": {\"type\": \"string\", \"maxLength\": 64}}", "key"),
                tool("agent_status", "当前的 bundle、计划、lease、预算、等待中的问题的完整状态", "{}"),
                tool("tool_catalog", "脚本可以使用的 bot 工具的参数 schema；可以按层过滤（observe、locomotion、interaction、external、unsafe）",
                        "{\"layer\": {\"enum\": [\"observe\", \"locomotion\", \"interaction\", \"external\", \"unsafe\"]}}"));
    }

    /**
     * {@code submit_decision}，参数为当前模式的决策 schema
     */
    static ToolDefinition decision(JsonSchema schema) {
        return new ToolDefinition(Decisions.TOOL_NAME, "提交本回合的决策（每个回合最后必须调用一次）", schema.document());
    }

    /**
     * 上下文中的 bot 工具目录：按层列出工具名
     */
    static String toolOverview(ToolCatalog catalog) {
        var tools = catalog.all().stream()
                .collect(Collectors.groupingBy(tool -> tool.layer().wire(), LinkedHashMap::new,
                        Collectors.mapping(ToolSpec::name, Collectors.toList())));
        return "# bot 工具（按层；完整参数用 tool_catalog 查询）\n\nobserve 自动允许；locomotion 与 interaction 需要 lease；"
                + "external 按策略允许或拒绝；unsafe 只在 unsafe profile 中可用。\n\n" + Json.PRETTY.toJson(Json.tree(tools));
    }

    /**
     * @return 交给模型的结果文本（JSON）
     */
    String execute(ToolCall call) {
        try {
            var args = call.arguments().isBlank() ? new JsonObject() : Json.parseObject(call.arguments());
            var result = switch (call.name()) {
                case "workspace_list_modules" -> Workspace.describe(workspace.list());
                case "workspace_search_modules" -> Workspace.describe(workspace.search(Json.requireString(args, "query")));
                case "workspace_get_module" -> {
                    var module = workspace.load(ModuleRef.parse(Json.requireString(args, "module")));
                    yield Json.object("manifest", module.manifest().toJson(), "source", module.source(),
                            "fixtures", Json.array(module.fixtures()));
                }
                case "workspace_test_module" -> tester.testAndRecord(ModuleRef.parse(Json.requireString(args, "module")));
                case "read_reference" -> reference(Json.requireString(args, "ref"));
                case "memory_read" -> {
                    var memory = workspace.readMemory(Json.requireString(args, "key"));
                    yield memory == null ? JsonNull.INSTANCE : memory;
                }
                case "agent_status" -> status.get();
                case "tool_catalog" -> {
                    var layer = Json.string(args, "layer");
                    var tools = new JsonArray();
                    for (var tool : catalog.describe()) {
                        if (layer == null || layer.equals(Json.string(tool.getAsJsonObject(), "layer"))) {
                            tools.add(tool);
                        }
                    }
                    yield tools;
                }
                default -> throw new IllegalArgumentException("unknown function " + call.name());
            };
            var text = Json.write(result);
            if (text.length() > MAX_RESULT_CHARS) {
                return Json.write(Json.object("truncated", true, "characters", text.length(),
                        "partial", text.substring(0, MAX_RESULT_CHARS)));
            }
            return text;
        } catch (RuntimeException e) {
            return Json.write(Json.object("error", e.toString()));
        }
    }

    private JsonElement reference(String ref) {
        if (ref.startsWith("trace:")) {
            var records = TraceReader.read(tracePath).resolve(ref);
            var array = new JsonArray();
            records.stream().limit(MAX_TRACE_RECORDS).forEach(array::add);
            return Json.object("records", array, "truncated", records.size() > MAX_TRACE_RECORDS, "total", records.size());
        }
        if (!ref.startsWith("workspace:")) {
            throw new IllegalArgumentException("unknown reference " + ref);
        }
        var parts = ref.substring("workspace:".length()).split("/");
        return switch (parts[0]) {
            case "module" -> {
                var module = workspace.load(new ModuleRef(parts[1], Integer.parseInt(parts[2])));
                yield Json.object("manifest", module.manifest().toJson(), "source", module.source());
            }
            case "report" -> {
                var report = workspace.testReport(new ModuleRef(parts[1], Integer.parseInt(parts[2])));
                yield report == null ? JsonNull.INSTANCE : report;
            }
            case "checkpoint" -> {
                var checkpoint = workspace.latestCheckpoint(parts[1]);
                yield checkpoint == null ? JsonNull.INSTANCE : checkpoint.toJson();
            }
            case "memory" -> {
                var memory = workspace.readMemory(parts[1]);
                yield memory == null ? JsonNull.INSTANCE : memory;
            }
            default -> throw new IllegalArgumentException("unknown workspace reference " + ref);
        };
    }

    private static ToolDefinition tool(String name, String description, String properties, String... required) {
        var schema = Json.parseObject("{\"type\": \"object\", \"additionalProperties\": false, \"properties\": " + properties + "}");
        schema.add("required", Json.array(List.of(required)));
        return new ToolDefinition(name, description, schema);
    }
}
