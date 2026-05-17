package wintahh.rageutils.module;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleRegistry {
    private static final Map<String, Module> modules = new LinkedHashMap<>();

    public static void register(Module module) {
        modules.put(module.getName().toLowerCase(), module);
    }

    public static Module get(String name) {
        return modules.get(name.toLowerCase());
    }

    public static Collection<Module> getAll() {
        return modules.values();
    }
}