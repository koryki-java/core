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
import ai.koryki.catalog.schema.types.TypeDescriptor;

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
 *         .example("substr(c.company_name, 2, 3)")
 * </pre>
 */
public class FunctionDefinition {

    private final String name;
    private final ReturnTypeInference returnType;
    private final FunctionKind kind;

    private FunctionSignature signature;
    private FunctionCategory category = FunctionCategory.OTHER;
    private String description;
    private String paragraph;
    private String example;
    private SqlTemplate template;
    private boolean unsupported;
    private Fixity fixity = Fixity.PREFIX;

    public FunctionDefinition(String name, ReturnTypeInference returnType) {
        this(name, returnType, FunctionKind.SCALAR);
    }

    public FunctionDefinition(String name, ReturnTypeInference returnType, FunctionKind kind) {
        this.name = name;
        this.returnType = returnType;
        this.kind = kind;
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
        this.category = category;
        return this;
    }

    public FunctionDefinition doc(String description) {
        this.description = description;
        return this;
    }

    /** Extra prose for the generated docs — a free-form paragraph after the one-line description. */
    public FunctionDefinition paragraph(String paragraph) {
        this.paragraph = paragraph;
        return this;
    }

    public FunctionDefinition example(String example) {
        this.example = example;
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

    public FunctionCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getParagraph() {
        return paragraph;
    }

    public String getExample() {
        return example;
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

    public boolean isUnsupported() {
        return unsupported;
    }

    public ReturnTypeInference getReturnTypeInference() {
        return returnType;
    }

    // null = FunctionRegistry applies default rendering: name(args) + OVER
    public String render(SqlSelectRenderer renderer, Function function, int indent) {
        if (unsupported) {
            throw new UnsupportedOperationException("function '" + name + "' is not supported by this dialect");
        }
        if (template != null) {
            checkArity(function);
            return template.render(renderer, function, indent);
        }
        return null;
    }

    /** Arity guard for templated definitions; same failure mode as the hand-written render() checks. */
    protected void checkArity(Function function) {
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
