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
package ai.koryki.security;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import java.time.ZoneId;

/**
 * KQL in, rendered SQL out — the whole pipeline the security tests are about, in one call.
 *
 * <p>The dialect is the DuckDB base one, because it is the dialect the shared goldens are written
 * against and the one every other dialect starts from; where a defence is a property of {@link
 * SqlDialect} itself (quoting, reserved words) the tests say so and exercise the interface rather
 * than this.
 *
 * <p>The resolver is the real Northwind catalog, not a stub: a stub would let a test pass because
 * nothing resolved rather than because the escaping held.
 */
final class Kql {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private Kql() {}

    /** The shared Northwind catalog — loaded once; nothing here mutates it. */
    static LinkResolver northwind() {
        return Holder.NORTHWIND;
    }

    static String sql(String kql) {
        return sql(northwind(), kql);
    }

    static String sql(LinkResolver resolver, String kql) {
        return sql(resolver, DuckdbBaseDialect.INSTANCE, kql);
    }

    static String sql(LinkResolver resolver, SqlDialect dialect, String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(dialect.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(dialect, UTC));
    }

    private static final class Holder {
        private static final LinkResolver NORTHWIND = NorthwindService.resolver();
    }
}
