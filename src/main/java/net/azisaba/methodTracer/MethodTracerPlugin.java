package net.azisaba.methodTracer;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import net.blueberrymc.native_util.ClassDefinition;
import net.blueberrymc.native_util.NativeUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MethodTracerPlugin extends JavaPlugin {
    private final Map<String, Supplier<byte[]>> transformQueue = new HashMap<>();

    @Override
    public void onLoad() {
        ClassPool cp = ClassPool.getDefault();
        List<ClassDefinition> classDefinitions = new ArrayList<>();
        getConfig().getValues(true).forEach((cl, maybeList) -> {
            String cname = cl.replace('/', '.');
            getLogger().info("Processing " + cname);
            Class<?> clazz = getClass(cname);
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

    @SuppressWarnings("unchecked")
    private byte[] transformClass(ClassPool cp, String cl, Object maybeList) {
        try {
            CtClass cc = cp.get(cl.replace('/', '.'));
            List<String> list;
            if (maybeList instanceof String) {
                list = Collections.singletonList((String) maybeList);
            } else if (maybeList instanceof List) {
                if (!((List<?>) maybeList).isEmpty() && !(((List<?>) maybeList).get(0) instanceof String)) {
                    getLogger().warning("Wrong type of list: expected String, but got " + (((List<?>) maybeList).get(0).getClass().getTypeName()));
                    return null;
                }
                list = (List<String>) maybeList;
            } else {
                getLogger().warning("Unknown type of object: expected List or String, but got " + (maybeList == null ? "null" : maybeList.getClass().getTypeName()));
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
                            "        if (plugin != null && plugin.getClass().getTypeName().equals(\"net.azisaba.methodTracer.MethodTracerPlugin\") && plugin.isEnabled()) Thread.dumpStack();" +
                            "}");
                } catch (CannotCompileException e) {
                    getLogger().warning("Failed to compile source code on method of class " + cl + ", name: " + name + ", desc: " + desc);
                    e.printStackTrace();
                }
            }
            byte[] bytes = cc.toBytecode();
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
}
