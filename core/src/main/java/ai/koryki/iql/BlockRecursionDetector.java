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
package ai.koryki.iql;

import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;
import java.util.Deque;
import java.util.List;

public class BlockRecursionDetector implements Collector<Boolean> {
    private boolean recursive;

    private final LinkResolver resolver;

    /**
     * The id of the block this detector was made for. A join naming some other block that is not a
     * catalog entity either is not recursion -- it is one block using an earlier sibling, which
     * {@link ai.koryki.iql.SqlQueryRenderer#toRecursive} must not answer {@code WITH RECURSIVE} for
     * -- so {@link #joinColumns(Source, Source)} compares the referenced name against this one
     * instead of merely asking whether it is a catalog entity at all.
     */
    private final String blockId;

    public BlockRecursionDetector(LinkResolver resolver, String blockId) {
        this.resolver = resolver;
        this.blockId = blockId;
    }

    public boolean visit(Deque<Object> deque, Select select) {
        apply(select.getStart(), select.getJoin());
        return true;
    }

    protected void apply(Source left, List<Join> join) {

        for (Join j : join) {
            joinColumns(left, j);
            apply(j.getSource(), j.getJoin());
        }
    }

    private void joinColumns(Source left, Join join) {

        Source right = join.getSource();
        if (right != null) {
            // boolean invers = join.isInvers();
            // An explicit join carries no criterion, and its direction is fixed by what the
            // author wrote -- there is nothing to invert.
            boolean invers = join.getColumns() == null && resolver.isInverse(join.getCrit());
            Source start = invers ? right : left;
            Source end = invers ? left : right;

            joinColumns(start, end);
        }
    }

    protected void joinColumns(Source start, Source end) {

        String startTable = start.getName();
        String endTable = end.getName();

        boolean b1 = resolver.isEntity(startTable);
        boolean b2 = resolver.isEntity(endTable);

        recursive |= (!b1 && blockId.equals(startTable)) || (!b2 && blockId.equals(endTable));
    }

    @Override
    public Boolean collect() {
        return recursive;
    }
}
