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

/**
 * The documentation metadata of a {@link FunctionDefinition}: its category plus the generated-docs
 * prose — a one-line {@code description} and an optional extra {@code paragraph}.
 *
 * <p>Held as one value so a dialect overlay's copy carries it atomically and can never silently drop
 * a field (a {@code paragraph} once was, when the copy constructor still copied fields by hand). It
 * has no role in typing or rendering; only the doc generators ({@code tools/docs}) consume it.
 *
 * <p>There is deliberately no {@code example} here. Usage is documented by each function's sample
 * query — a real fixture that is transpiled to every dialect for the page's "Generated SQL" block
 * and executed against every live database by the engine tests. A hand-written example string was a
 * second, unverified copy of the same thing: it drifted (one taught {@code '1997-01-01'} where KQL
 * dates are {@code "1997-01-01"}, and about half named columns no schema had), and every function
 * that carried one already had a sample directly beneath it.
 */
public record FunctionDoc(FunctionCategory category, String description, String paragraph) {

    static final FunctionDoc NONE = new FunctionDoc(FunctionCategory.OTHER, null, null);

    FunctionDoc withCategory(FunctionCategory category) {
        return new FunctionDoc(category, description, paragraph);
    }

    FunctionDoc withDescription(String description) {
        return new FunctionDoc(category, description, paragraph);
    }

    FunctionDoc withParagraph(String paragraph) {
        return new FunctionDoc(category, description, paragraph);
    }
}
