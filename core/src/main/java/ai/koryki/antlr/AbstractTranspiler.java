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
package ai.koryki.antlr;

import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.util.List;

/**
 * Shared parse plumbing for the IQL/KQL transpilers: a chain of memoized pure
 * stages (see {@link Lazy}). {@link PanicException} is raised by the stage that
 * detects it and replayed identically on every later access — never stored and
 * re-thrown from unrelated call sites.
 */
public abstract class AbstractTranspiler<R extends AbstractReader<?, ?, C>, C extends ParseTree> {

    private final Lazy<R> reader = Lazy.of(this::parse);
    private final Lazy<C> ctx = Lazy.of(this::buildCtx);

    /** Creates the reader for the source text; called at most once. */
    protected abstract R newReader() throws IOException;

    private R parse() {
        try {
            R r = newReader();
            checkPanic(r.getPanic());
            return r;
        } catch (IOException e) {
            throw new KorykiaiException(e);
        }
    }

    private C buildCtx() {
        R r = reader.get();
        C c = r.getCtx();
        checkPanic(r.getPanic());
        return c;
    }

    private static void checkPanic(List<Interval> panic) {
        if (panic != null && !panic.isEmpty()) {
            throw new PanicException(panic);
        }
    }

    public R getReader() {
        return reader.get();
    }

    public C getCtx() {
        return ctx.get();
    }

    public String getDescription() {
        return getReader().getDescription();
    }
}
