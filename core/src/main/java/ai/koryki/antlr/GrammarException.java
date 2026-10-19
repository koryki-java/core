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

import java.util.List;

public class GrammarException extends PositionException {

    private static final long serialVersionUID = -4825677103798201408L;

    private List<String> didYouMean = List.of();
    private List<String> ruleStack = List.of();

    public GrammarException(int line, int pos) {
        super(line, pos);
    }

    public GrammarException(int line, int pos, String msg) {

        super(line, pos, msg);
    }

    public GrammarException(int line, int pos, String msg, Throwable cause) {

        super(line, pos, msg, cause);
    }

    public GrammarException(int line, int pos, String msg, List<String> didYouMean) {

        super(line, pos, msg);
        this.didYouMean = List.copyOf(didYouMean);
    }

    /** Keywords that were probably meant; empty when nothing is close. */
    public List<String> getDidYouMean() {
        return didYouMean;
    }

    /** The grammar rules the parser was in, outermost first; for debugging the grammar. */
    public List<String> getRuleStack() {
        return ruleStack;
    }

    void setRuleStack(List<String> ruleStack) {
        this.ruleStack = List.copyOf(ruleStack);
    }
}
