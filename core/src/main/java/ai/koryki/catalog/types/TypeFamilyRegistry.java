package ai.koryki.catalog.types;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TypeFamilyRegistry {

    private static final Map<String, TypeFamily> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (CoreTypeFamily f : CoreTypeFamily.values()) {
            REGISTRY.put(f.name(), f);
        }
    }

    private TypeFamilyRegistry() {}

    public static void register(TypeFamily family) {
        REGISTRY.put(family.name(), family);
    }

    public static TypeFamily of(String name) {
        TypeFamily family = REGISTRY.get(name);
        if (family == null) throw new IllegalArgumentException("Unknown TypeFamily: " + name);
        return family;
    }
}
