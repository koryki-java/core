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
package ai.koryki.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface Database<C extends ResultConsumer<?>> extends AutoCloseable {

    @Override
    void close() throws SQLException;

    void execute(String sql, Consumer<PreparedStatement> consumer);

    default C execute(String sql, Supplier<C> processor) {

        try (C p = processor.get()) {
            execute(sql, s -> {

                execute(s, p);
            });
            return p;
        }
    }

     void execute(PreparedStatement statement, C processor) ;


}


