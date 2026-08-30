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

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Memoizing supplier for side-effect-free lazy pipelines: the supplier runs at
 * most once, and every subsequent {@link #get()} replays the same outcome —
 * the cached value or the same {@link RuntimeException}. This makes lazy
 * accessors idempotent: a stage that failed on first access fails identically
 * on every later access instead of silently changing behavior.
 */
public final class Lazy<T> implements Supplier<T> {

    private Supplier<T> supplier;
    private T value;
    private RuntimeException failure;

    private Lazy(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    public static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Lazy<>(supplier);
    }

    @Override
    public synchronized T get() {
        if (failure != null) {
            throw failure;
        }
        if (supplier != null) {
            try {
                value = supplier.get();
                supplier = null;
            } catch (RuntimeException e) {
                failure = e;
                supplier = null;
                throw e;
            }
        }
        return value;
    }
}
