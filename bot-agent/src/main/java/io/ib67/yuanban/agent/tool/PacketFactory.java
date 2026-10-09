package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.ib67.yuanban.bot.protocol.PacketDirection;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.UUID;

/**
 * {@code unsafe.send} 的数据包构造：按类名找到 MCProtocolLib 中发往服务端的数据包类，按参数个数选择公开构造方法，把 JSON 参数按形参类型转换。
 * 支持的形参类型：基本类型与包装类型、String、枚举（按名称）、UUID、adventure Key、Component（纯文本）、Vector3i、Vector3f、Vector3d
 * （{@code [x, y, z]}）。同一个参数个数有多个构造方法都能转换时拒绝。
 */
final class PacketFactory {
    private static final String PACKET_PACKAGE = "org.geysermc.mcprotocollib.protocol.packet.";

    private PacketFactory() {
    }

    /**
     * @throws IllegalArgumentException 类不存在、不是发往服务端的数据包、没有可用的构造方法或参数不能转换
     */
    @SuppressWarnings("unchecked")
    static MinecraftPacket create(String className, JsonArray args) {
        if (!className.startsWith(PACKET_PACKAGE)) {
            throw new IllegalArgumentException(className + " is not in " + PACKET_PACKAGE);
        }
        Class<?> type;
        try {
            type = Class.forName(className, false, PacketFactory.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("no packet class " + className, e);
        }
        if (!MinecraftPacket.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(className + " is not a MinecraftPacket");
        }
        PacketDirection.requireServerbound((Class<? extends MinecraftPacket>) type);
        var candidates = new ArrayList<Object[]>();
        Constructor<?> chosen = null;
        for (var constructor : type.getConstructors()) {
            if (!Modifier.isPublic(constructor.getModifiers()) || constructor.getParameterCount() != args.size()) {
                continue;
            }
            var converted = convertAll(constructor.getParameterTypes(), args);
            if (converted != null) {
                candidates.add(converted);
                chosen = constructor;
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("no public constructor of " + className + " accepts " + args);
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("several constructors of " + className + " accept " + args);
        }
        try {
            return (MinecraftPacket) chosen.newInstance(candidates.getFirst());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("cannot construct " + className, e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("the constructor of " + className + " rejected " + args, e.getCause());
        }
    }

    private static Object[] convertAll(Class<?>[] types, JsonArray args) {
        var values = new Object[types.length];
        for (var i = 0; i < types.length; i++) {
            try {
                values[i] = convert(args.get(i), types[i]);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return values;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convert(JsonElement value, Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return value.getAsInt();
        }
        if (type == long.class || type == Long.class) {
            return value.getAsLong();
        }
        if (type == float.class || type == Float.class) {
            return value.getAsFloat();
        }
        if (type == double.class || type == Double.class) {
            return value.getAsDouble();
        }
        if (type == boolean.class || type == Boolean.class) {
            if (!value.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("not a boolean");
            }
            return value.getAsBoolean();
        }
        if (type == short.class || type == Short.class) {
            return value.getAsShort();
        }
        if (type == byte.class || type == Byte.class) {
            return value.getAsByte();
        }
        if (type == String.class) {
            if (!value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("not a string");
            }
            return value.getAsString();
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, value.getAsString());
        }
        if (type == UUID.class) {
            return UUID.fromString(value.getAsString());
        }
        if (type == Key.class) {
            return Key.key(value.getAsString());
        }
        if (type == Component.class) {
            return Component.text(value.getAsString());
        }
        if (type == Vector3i.class) {
            var array = value.getAsJsonArray();
            return Vector3i.from(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
        }
        if (type == Vector3f.class) {
            var array = value.getAsJsonArray();
            return Vector3f.from(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
        }
        if (type == Vector3d.class) {
            var array = value.getAsJsonArray();
            return Vector3d.from(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
        }
        throw new IllegalArgumentException("unsupported parameter type " + type.getName());
    }
}
