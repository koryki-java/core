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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class MsgErrorListener extends BaseErrorListener {

    private static final long serialVersionUID = -4124163589762870231L;

    /** More alternatives than this is no help to read; the message then names none. */
    private static final int MAX_EXPECTED = 8;

    private final boolean abort;

    /** First character offset not yet covered by a reported lexer error. */
    private int lexerReach;

    private final List<Interval> panic = new ArrayList<>();

    public MsgErrorListener(boolean abort) {
        this.abort = abort;
    }

    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e) {

        if (recognizer instanceof Parser) {

            Parser parser = (Parser) recognizer;
            Token t = (offendingSymbol instanceof Token) ? (Token) offendingSymbol : null;
            SyntaxHints.Hint hint =
                    t == null ? null : SyntaxHints.wrongCase(parser.getVocabulary(), t.getText());
            String hinted = hint == null ? msg : msg + "; " + hint.message();

            if (abort) {
                abort(parser, line, charPositionInLine, hinted, hint);
            } else {
                panic(parser, t, line, charPositionInLine, hinted, hint);
            }
        } else if (recognizer instanceof Lexer) {
            Lexer lexer = (Lexer) recognizer;
            CharStream input = lexer.getInputStream();
            int from = lexer._tokenStartCharIndex;
            int to = Math.max(from, input.index());
            String text = input.getText(org.antlr.v4.runtime.misc.Interval.of(from, to));
            SyntaxHints.Hint hint = SyntaxHints.unknownWord(lexer.getVocabulary(), text);
            String hinted =
                    "unrecognized input '"
                            + text.strip()
                            + "'"
                            + (hint == null ? "" : " \u2014 " + hint.message());
            List<String> didYouMean = hint == null ? List.of() : List.of(hint.keyword());

            if (abort) {
                throw new GrammarException(line, column(charPositionInLine), hinted, didYouMean);
            }
            // After a bad character ANTLR skips one and tries again, so a mistyped word is reported
            // once per remaining letter. Only the first report says anything; the rest lie inside
            // it.
            if (from >= lexerReach) {
                lexerReach = to + 1;
                panic.add(
                        new Interval(
                                from,
                                to,
                                hinted,
                                line,
                                column(charPositionInLine),
                                List.of(),
                                didYouMean));
            }
        } else if (recognizer == null) {
            GrammarException se =
                    new GrammarException(line, column(charPositionInLine), "null" + Text.NL);
            throw se;
        } else {
            GrammarException se =
                    new GrammarException(
                            line,
                            column(charPositionInLine),
                            recognizer.getClass().getSimpleName() + Text.NL + msg);
            throw se;
        }
    }

    /** ANTLR's 0-based {@code charPositionInLine} as the 1-based column {@link Position} uses. */
    private static int column(int charPositionInLine) {
        return charPositionInLine + 1;
    }

    private static void abort(
            Parser recognizer,
            int line,
            int charPositionInLine,
            String msg,
            SyntaxHints.Hint hint) {
        List<String> stack = recognizer.getRuleInvocationStack();
        Collections.reverse(stack);

        // The rule stack names the grammar's rules, which means nothing to whoever wrote the
        // query; it stays on the exception for whoever debugs the grammar.
        GrammarException e =
                new GrammarException(
                        line,
                        column(charPositionInLine),
                        msg,
                        hint == null ? List.of() : List.of(hint.keyword()));
        e.setRuleStack(stack);
        throw e;
    }

    private void panic(
            Parser recognizer,
            Token offendingToken,
            int line,
            int charPositionInLine,
            String msg,
            SyntaxHints.Hint hint) {
        ParserRuleContext context = recognizer.getContext();
        int startIndex = least(context);
        int stopIndex = offendingToken != null ? offendingToken.getTokenIndex() : startIndex;
        List<String> expected = expected(recognizer);
        List<String> didYouMean = hint == null ? List.of() : List.of(hint.keyword());

        if (startIndex < stopIndex) {
            panic.add(
                    new Interval(
                            startIndex + 1,
                            stopIndex,
                            msg,
                            line,
                            column(charPositionInLine),
                            expected,
                            didYouMean));
        } else {
            panic.add(
                    new Interval(
                            startIndex,
                            stopIndex,
                            msg,
                            line,
                            column(charPositionInLine),
                            expected,
                            didYouMean));
        }
    }

    /**
     * The keywords and punctuation the grammar would have accepted at the failure, in the spelling
     * a user types them. Tokens without a fixed spelling (identifiers, numbers) have no name worth
     * showing and are left out, and so is a list too long to help.
     */
    private static List<String> expected(Parser recognizer) {
        try {
            Vocabulary vocabulary = recognizer.getVocabulary();
            List<String> names = new ArrayList<>();
            for (int type : recognizer.getExpectedTokens().toArray()) {
                String literal = SyntaxHints.literal(vocabulary, type);
                if (literal != null) {
                    names.add(literal);
                }
            }
            Collections.sort(names);
            return names.size() > MAX_EXPECTED ? List.of() : names;
        } catch (RuntimeException e) {
            // the expected set is a courtesy; the error itself must still be reported
            return List.of();
        }
    }

    private static int least(ParseTree context) {

        if (context instanceof ParserRuleContext) {
            ParserRuleContext c = (ParserRuleContext) context;
            if (c.children != null && !c.children.isEmpty()) {
                return least(c.children.get(c.children.size() - 1));
            } else if (c.stop != null) {
                return c.stop.getTokenIndex();
            } else {
                return c.start.getTokenIndex();
            }
        } else if (context instanceof TerminalNode) {
            TerminalNode n = (TerminalNode) context;
            return n.getSymbol().getTokenIndex();
        } else {
            throw new KorykiaiException();
        }
    }

    public List<Interval> getPanic() {
        return Collections.unmodifiableList(panic);
    }

    public boolean isAbort() {
        return abort;
    }
}
