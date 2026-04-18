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

import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.RuleNode;

public class PositionedTreeWalker extends ParseTreeWalker {

    @Override
    protected void enterRule(ParseTreeListener listener, RuleNode r) {
        try {
            super.enterRule(listener, r);
        } catch (PositionException e) {
            throw e;
        } catch (RuntimeException e) {

            if (r == null) {
                throw new NullPointerException();
            }
            Position start = Range.range(r).getStart();
            throw new PositionException(start.getLine(), start.getPos(), e);
        }
    }

    protected void exitRule(ParseTreeListener listener, RuleNode r) {
        try {
            super.exitRule(listener, r);
        } catch (PositionException e) {
            throw e;
        } catch (RuntimeException e) {
            Position start = Range.range(r).getStart();
            throw new PositionException(start.getLine(), start.getPos(), e);
        }
    }
}
