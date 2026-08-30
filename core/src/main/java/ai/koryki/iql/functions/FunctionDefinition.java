/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.iql.functions;

import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Function;
import ai.koryki.catalog.types.TypeDescriptor;

/**
 * One function overload: identity (name + kind), one signature, return type
 * inference, rendering, and documentation metadata.
 *
 * <p>Metadata is attached fluently after construction (works with the anonymous
 * render() subclass pattern):
 * <pre>
 * new FunctionDefinition("substr", ReturnTypes.TEXT)
 *         .args(arg("string", TEXT), arg("start", INTEGER), optionalArg("length", INTEGER))
 *         .category(FunctionCategory.STRING)
 *         .doc("Extracts a substring of *length* characters starting at *start* (1-based).")
 * </pre>
 *
 * <p>Usage examples are not declared here — they come from the function's sample query fixture,
 * which is transpiled to every dialect and executed against every live database. See
 * {@link FunctionDoc}.
 */
public class FunctionDefinition {

    private final String name;
    private final ReturnTypeInference returnType;
    private final FunctionKind kind;

    private FunctionSignature signature;
    private FunctionDoc documentation = FunctionDoc.NONE;
    private SqlTemplate template;
    private boolean unsupported;
    private boolean windowUnsupported;
    /**
     * What the author can do instead, appended to the rejection. Optional, and worth having only
     * where a real alternative exists: "not supported here" leaves someone stuck, while
     * {@code count_distinct} on Oracle has a concrete way out that nobody guesses on their own.
     */
    private String unsupportedHint;
    private Fixity fixity = Fixity.PREFIX;

    public FunctionDefinition(String name, ReturnTypeInference returnType) {
        this(name, returnType, FunctionKind.SCALAR);
    }

    public FunctionDefinition(String name, ReturnTypeInference returnType, FunctionKind kind) {
        this.name = name;
        this.returnType = returnType;
        this.kind = kind;
    }

    /**
     * Copy constructor for dialect overlays — carries <em>every</em> value field. Keep it in
     * sync with the fields above so an overlay (override / overrideAll / unsupported) can never
     * silently drop one: {@code fixity} and {@code paragraph} used to be lost, which reset an
     * overridden operator (LIKE / BETWEEN / IN) to {@link Fixity#PREFIX} and dropped it out of
     * comparison dispatch. Note it does not preserve a {@code render()} override on {@code base}
     * — an overlay always replaces rendering with a template or marks the call unsupported.
     */
    public FunctionDefinition(FunctionDefinition base) {
        this(base.name, base.returnType, base.kind);
        this.signature = base.signature;
        this.documentation = base.documentation;
        this.template = base.template;
        this.unsupported = base.unsupported;
        this.windowUnsupported = base.windowUnsupported;
        this.unsupportedHint = base.unsupportedHint;
        this.fixity = base.fixity;
    }

    public FunctionDefinition signature(FunctionSignature signature) {
        this.signature = signature;
        return this;
    }

    public FunctionDefinition args(FunctionArg... args) {
        return signature(FunctionSignature.of(args));
    }

    public FunctionDefinition variadic(FunctionArg... args) {
        return signature(FunctionSignature.ofVariadic(args));
    }

    public FunctionDefinition category(FunctionCategory category) {
        this.documentation = documentation.withCategory(category);
        return this;
    }

    public FunctionDefinition doc(String description) {
        this.documentation = documentation.withDescription(description);
        return this;
    }

    /** Extra prose for the generated docs — a free-form paragraph after the one-line description. */
    public FunctionDefinition paragraph(String paragraph) {
        this.documentation = documentation.withParagraph(paragraph);
        return this;
    }

    /** Declarative rendering via {@link SqlTemplate}; alternative to overriding {@link #render}. */
    public FunctionDefinition template(String template) {
        this.template = new SqlTemplate(template);
        return this;
    }

    /** Surface-syntax shape; {@link Fixity#PREFIX} (a function call) unless set to an operator shape. */
    public FunctionDefinition fixity(Fixity fixity) {
        this.fixity = fixity;
        return this;
    }

    public Fixity getFixity() {
        return fixity;
    }

    public String getName() {
        return name;
    }

    public FunctionKind getKind() {
        return kind;
    }

    /** Declared signature, or {@code null} for legacy definitions without arity metadata. */
    public FunctionSignature getSignature() {
        return signature;
    }

    /** The documentation metadata (category + generated-docs prose); consumed by the doc generators. */
    public FunctionDoc getDoc() {
        return documentation;
    }

    public FunctionCategory getCategory() {
        return documentation.category();
    }

    public String getDescription() {
        return documentation.description();
    }

    public String getParagraph() {
        return documentation.paragraph();
    }

    public TypeDescriptor returnType(FunctionBinding binding) {
        return returnType.infer(binding);
    }

    public SqlTemplate getTemplate() {
        return template;
    }

    /** Marks the function as rejected by this dialect; rendering fails, validation can report it. */
    public FunctionDefinition unsupported() {
        this.unsupported = true;
        return this;
    }

    /** The engine cannot evaluate this function with an OVER clause (e.g. MySQL GROUP_CONCAT). */
    public FunctionDefinition windowUnsupported() {
        this.windowUnsupported = true;
        return this;
    }

    /** Names the way out, for a rejection where one exists. See {@link #unsupportedHint}. */
    public FunctionDefinition unsupportedHint(String hint) {
        this.unsupportedHint = hint;
        return this;
    }

    public String getUnsupportedHint() {
        return unsupportedHint;
    }

    public boolean isUnsupported() {
        return unsupported;
    }

    public boolean isWindowUnsupported() {
        return windowUnsupported;
    }

    public ReturnTypeInference getReturnTypeInference() {
        return returnType;
    }

    /**
     * Renders a call to this function. The {@code unsupported} guard is applied here and cannot be
     * bypassed — overrides customise {@link #renderBody}, never this method. Keeping it {@code final}
     * is the invariant that a dialect-rejected function always throws, however it renders.
     */
    public final String render(SqlSelectRenderer renderer, Function function, int indent) {
        if (unsupported) {
            throw new UnsupportedOperationException("function '" + name + "' is not supported by this dialect");
        }
        if (windowUnsupported && function.getWindow() != null) {
            throw new UnsupportedOperationException(
                    "function '" + name + "' does not support an OVER clause in this dialect");
        }
        checkArity(function);
        String body = renderBody(renderer, function, indent);
        if (body == null) {
            // fall back to FunctionRegistry.defaultRender, which appends the OVER clause itself
            return null;
        }
        // the OVER clause belongs to the call, not the body: append it here so no
        // template or renderBody override can silently drop a window
        return body + renderer.toSql(function.getWindow(), indent);
    }

    /**
     * Produces the SQL for this function, run after the {@code unsupported} guard. The default renders
     * the declarative {@link SqlTemplate} (arity-checked), or returns {@code null} to fall back to
     * {@code FunctionRegistry}'s default {@code name(args) + OVER} rendering. Subclasses and dialect
     * overlays override this — the guard above stays enforced for all of them.
     */
    protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
        return template != null ? template.render(renderer, function, indent) : null;
    }

    /** Arity guard applied to every render (see {@link #render}): a declared signature must match the call's argument count, variadic-aware. */
    private void checkArity(Function function) {
        int argCount = function.getArguments().size();
        if (signature != null && !signature.matchesArity(argCount)) {
            throw new IllegalArgumentException(name + " expects " + signature + ", got " + argCount + " arguments");
        }
    }

    // null = fall back to render() in predicate (WHERE/HAVING/JOIN ON) context
    public String renderPredicate(SqlSelectRenderer renderer, Function function, int indent) {
        return null;
    }
}
