package net.azisaba.methodtracer;

import javassist.*;
import net.blueberrymc.nativeutil.ClassDefinition;
import net.blueberrymc.nativeutil.NativeUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MethodTracerPlugin extends JavaPlugin {
    private static final String DEFAULT_CODE = "Thread.dumpStack();";
    private final Map<String, Supplier<byte[]>> transformQueue = new HashMap<>();

    @Override
    public void onLoad() {
        ClassPool cp = ClassPool.getDefault();
        List<ClassDefinition> classDefinitions = new ArrayList<>();
        getConfig().getValues(true).forEach((cl, maybeList) -> {
            String cname = cl.replace('/', '.');
            getLogger().info("Processing " + cname);
            Class<?> clazz = getClass(cname);
            cp.appendClassPath(new ClassClassPath(clazz));
            if (clazz != null) {
                getLogger().info(cname + " is already loaded, redefining");
                byte[] bytes = transformClass(cp, cl, maybeList);
                if (bytes != null) {
                    classDefinitions.add(new ClassDefinition(clazz, bytes));
                }
            } else {
                getLogger().info("Added " + cname + " into queue");
                transformQueue.put(cname, () -> transformClass(cp, cl, maybeList));
            }
        });
        if (!classDefinitions.isEmpty()) {
            NativeUtil.redefineClasses(classDefinitions.toArray(new ClassDefinition[0]));
            getLogger().info("Successfully redefined " + classDefinitions.size() + " classes");
        }
        NativeUtil.registerClassLoadHook((classLoader, cl, clazz, protectionDomain, bytes) -> {
            Supplier<byte[]> byteArraySupplier = transformQueue.remove(cl.replace('/', '.'));
            if (byteArraySupplier == null) return null;
            return byteArraySupplier.get();
        });
    }

    private byte[] transformClass(ClassPool cp, String cl, Object maybeList) {
        try {
            CtClass cc = cp.get(cl.replace('/', '.'));
            Set<String> list;
            Map<String, String> code = new HashMap<>();
            if (maybeList instanceof String) {
                list = Collections.singleton((String) maybeList);
            } else if (maybeList instanceof List) {
                if (!((List<?>) maybeList).isEmpty() && !(((List<?>) maybeList).get(0) instanceof String)) {
                    getLogger().warning("Wrong type of list: expected String, but got " + (((List<?>) maybeList).get(0).getClass().getTypeName()));
                    return null;
                }
                list = new HashSet<>();
                for (Object o : ((Collection<?>) maybeList)) {
                    if (o instanceof String) {
                        list.add((String) o);
                    } else if (o instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) o;
                        if (map.isEmpty()) {
                            return null;
                        }
                        getLogger().info("Map: " + map);
                        Object keyValue = map.keySet().stream().findFirst().orElseThrow(IllegalArgumentException::new);
                        if (!(keyValue instanceof String)) {
                            getLogger().warning("Wrong type of map (key): expected String, but got " + keyValue.getClass().getTypeName());
                        }
                        keyValue = map.values().stream().findFirst().orElseThrow(IllegalArgumentException::new);
                        if (!(keyValue instanceof String)) {
                            getLogger().warning("Wrong type of map (value): expected String, but got " + keyValue.getClass().getTypeName());
                        }
                        list = map.keySet().stream().map(Object::toString).collect(Collectors.toSet());
                        code.putAll(map.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString())));
                    }
                }
            } else {
                getLogger().warning("Unknown type of object: expected Map, List or String, but got " + (maybeList == null ? "null" : maybeList.getClass().getTypeName()));
                return null;
            }
            for (String signature : list) {
                String name = signature.replaceFirst("^(.*)\\(.*\\).*$", "$1");
                String desc = signature.replaceFirst("^.*(\\(.*\\).*)$", "$1");
                CtMethod cm;
                try {
                    cm = cc.getMethod(name, desc);
                } catch (NotFoundException e) {
                    getLogger().warning("Could not find method of class " + cl + ", name: " + name + ", desc: " + desc);
                    continue;
                }
                try {
                    cm.insertBefore("{" +
                            "        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin(\"MethodTracer\");\n" +
                            "        if (plugin != null && plugin.getClass().getTypeName().equals(\"net.azisaba.methodtracer.MethodTracerPlugin\") && plugin.isEnabled()) {\n" +
                            "            " + code.getOrDefault(signature, DEFAULT_CODE) + "\n" +
                            "        }" +
                            "}");
                } catch (CannotCompileException e) {
                    getLogger().warning("Failed to compile source code on method of class " + cl + ", name: " + name + ", desc: " + desc);
                    e.printStackTrace();
                }
            }
            byte[] bytes = cc.toBytecode();
            writeFile(".methodtracer/transformed/" + cl + ".class", bytes);
            getLogger().info("Successfully transformed " + cl);
            return bytes;
        } catch (NotFoundException | IOException | CannotCompileException e) {
            getLogger().warning("Could not inject code to class " + cl);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Checks if the provided class is loaded. <b>Warning: This method could be very expensive and should NOT be used
     * frequently.</b>
     * @param clazz class to check
     * @return class if loaded, null otherwise
     */
    private static Class<?> getClass(String clazz) {
        try {
            return Class.forName(clazz);
        } catch (ClassNotFoundException ignore) {}
        return Arrays.stream(NativeUtil.getLoadedClasses())
                .filter(it -> it.getTypeName().equals(clazz))
                .findFirst()
                .orElse(null);
    }

    private static void writeFile(String path, byte[] bytes) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            String dir = path.substring(0, lastSlash);
            new File(dir).mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
