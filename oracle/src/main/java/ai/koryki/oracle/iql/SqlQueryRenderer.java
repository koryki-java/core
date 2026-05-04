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
package ai.koryki.oracle.iql;

import ai.koryki.iql.*;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Set;

import java.util.List;

public class SqlQueryRenderer extends ai.koryki.iql.SqlQueryRenderer {

    public SqlQueryRenderer() {
        this(new FunctionRenderer() {
        });
    }

    public SqlQueryRenderer(FunctionRenderer functionTranslator) {
        super(functionTranslator);
    }

    @Override
    protected String mapOperator(Set set) {
        if (set.getOperator().equals("MINUS")) {
            return "EXCEPT";
        }

        return super.mapOperator(set);
    }

    @Override
    protected StringBuilder toRecursive(LinkResolver resolver, List<Block> block, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(Identifier.indent(indent) + WITH + " ");

        boolean recursive = block.stream().anyMatch(x -> Walker.apply(x, new BlockRecursionDetector(resolver)));
        if (recursive) {
            //b.append("RECURSIVE ");
        }
        return b;
    }

}
