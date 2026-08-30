/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitationsf
 * under the License.
 *
 * koryki.ai's KQL-parser was inspired by and partially derived from the excellent ggsql
 * project by Posit (https://github.com/posit-dev/ggsql).
 * The visualiseClause and its Grammar-of-Graphics concepts are not part of this grammar;
 * they live in the separate kqlvisualise project.
 */

parser grammar KQLRules;

// All query rules EXCEPT `query`. The start rule deliberately does not live here but in each base
// grammar: it is the only rule that differs between the core and the visualise variant. Were it
// here and replaced by an override, the core variant would drag the viz rules along as dead code
// -- measured: the imported rule stays in the parser even when the overriding query no longer
// calls it.

block :
  ID AS LEFT_PAREN set RIGHT_PAREN
| ID PLACEHOLDER
;

set : set SET_INTERSECT set  // first precedence
| set (SET_UNION | SET_UNIONALL | SET_MINUS) set
| LEFT_PAREN set RIGHT_PAREN
| select
;

select
 : FIND source (COMMA link)* filterClause? fetchClause? limitClause?
;

link : from=ID? (PLUS)? source
| from=ID? join  PLUS? source
| from=ID? PLUS? source join
;

existslink : from=ID source
| from=ID join source
| from=ID source join
;

join:
     VIA crit=ID
|    LEFT_BRACKET col+=ID (COMMA col+=ID)* RIGHT_BRACKET
|    LEFT_BRACKET left+=ID EQUALS right+=ID (COMMA left+=ID EQUALS right+=ID)* RIGHT_BRACKET
;

source
 : name=ID alias=ID
;

filterClause
 : FILTER logical_expression
;

limitClause
 : LIMIT INT
;

logical_expression
 : NOT negate=logical_expression
| left=logical_expression AND right=logical_expression
| left=logical_expression OR right=logical_expression
| unary_logical_expression
;

unary_logical_expression :
  expression
  (
    // BETWEEN owns its pair: the AND belongs to the range and binds tighter than the logical AND,
    // exactly as in SQL. That is why it is a production of its own and no longer a member of
    // `operator` — the keyword announces the pair unambiguously.
    //
    // It used to be written as a leading `(expression AND expression)` alternative available after
    // ANY operator. That silently swallowed predicates: since a bare expression is a predicate,
    // `a > 1 AND b` was ambiguous, ANTLR resolved it to the lowest-numbered alternative, and `b`
    // became a third operand of `>`. Its template `{0} > {1}` never renders a third operand, so the
    // predicate vanished without a violation or a warning — and, worse, only in that operand order.
    //
    // PostgreSQL has the same ambiguity and resolves it by restricting what may follow BETWEEN to a
    // narrower expression class (b_expr in gram.y):
    // https://www.postgresql.org/docs/current/functions-comparison.html
    // Here the separate production suffices, because our `expression` cannot contain a logical AND
    // at all — one can only enter through the parentheses of a function call. So we need neither
    // the narrower class nor its parentheses: `BETWEEN 1 + 2 AND 5` parses as written.
    BETWEEN expression AND expression
    | operator
      (
        expression? // nothing or single expression
        | LEFT_PAREN expression (COMMA expression)* RIGHT_PAREN // set of expressions
      )
  )
| LEFT_PAREN logical_expression RIGHT_PAREN
| EXISTS LEFT_PAREN exists RIGHT_PAREN
| expression operator? PLACEHOLDER
// A boolean-valued expression standing alone as a predicate: FILTER starts_with(c.name, 'A'),
// FILTER p.discontinued. Last alternative, so it only matches when no operator follows.
// FunctionValidator enforces that the expression is actually BOOLEAN.
| expression
;

exists:
existslink (COMMA link)* filterClause?
;

// BETWEEN is deliberately absent: it lives in its own production in unary_logical_expression, so
// that its AND cannot be confused with the logical one. `a BETWEEN 1` is therefore a parse error
// rather than a range with a missing bound that only fails deep inside the renderer.
operator
 : EQUALS
| NOTEQ
| GREATER
| GREATEREQ
| IN
| DISTINCT
| ISNULL
| LESS
| LESSEQ
| LIKE
// The escape hatch: an operator with no catalog entry renders verbatim into the SQL. It is
// necessarily LOWER CASE, because ID is `LOWER (LOWER | DIGIT)*` while every operator above is an
// upper-case keyword literal — so the two alternatives can never compete for the same text.
//
// That also means a lower-case `distinct` is NOT the DISTINCT alternative; it lands here, as a
// custom operator that merely shares the name. It used to render worse than the pass-through it
// looked like: SqlDialect.negatedOperatorTemplate normalises its argument and handed back the ANSI
// `IS NOT DISTINCT FROM`, while the MariaDB (`<=>`) and Oracle (`DECODE`) overrides compare exactly
// and never fired — both engines reject that form. FunctionValidator.checkKnownOperator therefore
// rejects a spelling that differs from a real operator only in case, naming the correct one; a
// genuinely foreign operator still only warns.
//
// Restricting this alternative to an upper-case token was considered and dropped: it would make the
// collision structurally impossible, but the resulting parse error reads `mismatched input
// 'distinct'` instead of naming the fix, and it would introduce the language's only upper-case
// identifier class.
| custom=ID
;

fetchClause
 : FETCH DISTINCT? fetchItem (COMMA fetchItem)* ROLLUP?
;

fetchItem
 : expression (h=ID (label=STRING)?)? ((ASC | DESC) idx=INT?)?
;

expression
 : LEFT_PAREN expression RIGHT_PAREN
| BAR expression
| PLUS expression
| left=expression (MULT | DIV) right=expression
| left=expression (PLUS | BAR) right=expression
| temporal_literal
| field
| function
| INT
| NUMBER
| SQ_STRING
| NULL
| LEFT_PAREN select RIGHT_PAREN
;

temporal_literal
 : DATE_STRING
| TIME_STRING
| TIMESTAMP_STRING
| DURATION;

function
 : func=ID LEFT_PAREN (argument (COMMA argument)*)? RIGHT_PAREN window?
;

// check, if stronger constraints are useful
// 1. most common aggregate functions: SUM, AVG, COUNT, MIN, MAX, ROW_NUMBER, LAG, LEAD
// 2. No ORDER BY outside window-function
// 3. avoid mixing aggregate and window-function, enforce to use CTE/block
window
    : OVER LEFT_PAREN (PARTITION partitionex+=expression (COMMA partitionex+=expression)*)? (ORDER orderex+=expression (COMMA orderex+=expression)* (ASC|DESC)? )? frame? RIGHT_PAREN
   ;

// Default frame for ORDER: RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
frame
    : ROWS BETWEEN lower=frame_bound AND upper=frame_bound
;

frame_bound
    : UNBOUNDED (PRECEDING | FOLLOWING)
    | CURRENT ROW
    | INT (PRECEDING | FOLLOWING)
;

argument
// expression first: a bare expression is now also a valid logical_expression (the boolean
// predicate alternative), so listing logical_expression first would swallow every plain argument.
// A genuine predicate like `c.nr > 0` is not a viable expression, so it still picks the second.
 : expression | logical_expression | identity=ID
;

field
 : alias=ID DOT name=ID
;
