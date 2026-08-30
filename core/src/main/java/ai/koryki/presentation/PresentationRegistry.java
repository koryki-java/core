package ai.koryki.presentation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a {@link Presentation} from its serialised name, so a presentation can travel as a single
 * string — in {@code db.json} beside {@code typeEncoding}, or on the wire.
 *
 * <p>Deliberately the same shape as
 * {@link ai.koryki.catalog.types.TypeEncodingRegistry TypeEncodingRegistry}: fixed names in the map,
 * prefix-parameterised ones parsed once and cached. An unknown name is an error rather than a silent
 * null — a typo in a catalog annotation must not read as "no presentation wanted".
 */
public final class PresentationRegistry {

    private static final Map<String, Presentation> REGISTRY = new ConcurrentHashMap<>();

    static {
        REGISTRY.put(PlainPresentation.NAME, PlainPresentation.INSTANCE);
    }

    private PresentationRegistry() {}

    public static void register(Presentation presentation) {
        REGISTRY.put(presentation.name(), presentation);
    }

    /** @param name the serialised form, or null when the column declares none */
    public static Presentation ofNullable(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Presentation p = REGISTRY.get(name);
        if (p == null) {
            p = parseParameterized(name);
            if (p != null) {
                REGISTRY.put(name, p);
            }
        }
        if (p == null) {
            throw new IllegalArgumentException("Unknown Presentation: " + name);
        }
        return p;
    }

    /** Prefix-parameterized presentations: {@code DECIMALS:<n>}, {@code SIGNIFICANT:<n>}, {@code SCALE:<factor>:<symbol>}. */
    private static Presentation parseParameterized(String name) {
        if (name.startsWith(DecimalsPresentation.PREFIX)) {
            return DecimalsPresentation.parse(name);
        }
        if (name.startsWith(SignificantPresentation.PREFIX)) {
            return SignificantPresentation.parse(name);
        }
        if (name.startsWith(ScalePresentation.PREFIX)) {
            return ScalePresentation.parse(name);
        }
        return null;
    }
}
