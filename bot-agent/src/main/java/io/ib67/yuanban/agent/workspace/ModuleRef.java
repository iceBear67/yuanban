package io.ib67.yuanban.agent.workspace;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * 模块的名称与版本，写作 {@code name@version}。
 */
public record ModuleRef(String name, int version) {
    public static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,47}");

    public ModuleRef {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid module name " + name);
        }
        if (version < 1) {
            throw new IllegalArgumentException("module version must be positive, got " + version);
        }
    }

    /**
     * @throws IllegalArgumentException 不是 {@code name@version}
     */
    public static ModuleRef parse(String text) {
        var at = text.lastIndexOf('@');
        if (at <= 0 || at == text.length() - 1) {
            throw new IllegalArgumentException("module reference must be name@version, got " + text);
        }
        try {
            return new ModuleRef(text.substring(0, at), Integer.parseInt(text.substring(at + 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("module version is not a number in " + text, e);
        }
    }

    @Override
    public @NotNull String toString() {
        return name + "@" + version;
    }
}
