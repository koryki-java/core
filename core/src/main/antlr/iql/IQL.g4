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

grammar IQL;

query
    : (cte)? set EOF
    ;

cte : WITH (block) (COMMA block)* ;

block :
    id=ID AS LEFT_PAREN set RIGHT_PAREN
    | id=ID PLACEHOLDER
;


set : set INTERSECT set // first precedence
    | set (UNION | UNIONALL | MINUS) set
    | LEFT_PAREN set RIGHT_PAREN
    | select
;

// place heterogeneous filters from inner joins here
// you are not allowed to reference sources form outer joins here
select
    : SELECT DISTINCT? ((entity=join_entity)) link? (ALL filter? having? out* group* order*)? ROLLUP? limitClause?
    | LEFT_PAREN select RIGHT_PAREN
;

link
    : (JOIN join OWNER)+
;

// use local filters only here
// you are only allowed to reference the source itself, but subselects are allowed here too!
join_entity
    : source out* filter? group* having? order*
;

// use local filters only here
// you are only allowed to reference the table itself, but subselects are allowed here too!
exists_entity
    : source filter? group* having?
;

// A join names either a criterion the catalog resolves or the columns themselves; never both.
join
    : OPTIONAL?  (crit=ID | on=joincolumns)
        (REF ref=ID
        |
        (
          ( entity=join_entity // join an table
          )
          child=link?)
        )
;

// The columns an explicitly written join compares, always as pairs. KQL also has the shorthand
// [a, b] for equal names on both sides, but the mapper expands it, so the intermediate form keeps
// one spelling -- a second one here would be a second thing to read back byte-identically.
joincolumns
    : LEFT_BRACKET left+=ID EQUALS right+=ID (COMMA left+=ID EQUALS right+=ID)* RIGHT_BRACKET
;

exists
    :  parent=ID (crit=ID | on=joincolumns) (
          entity=exists_entity  // exists table
        ) child=link?
        // residual clauses the push rules could not move onto a single source:
        // OR/NOT over several aliases and correlated conjuncts referencing the outer query
        filter? having?
;

expression
    : LEFT_PAREN expression RIGHT_PAREN
    | MINUS_SIGN expression
    | PLUS_SIGN expression
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
    | DURATION
;

function
    : ID LEFT_PAREN (argument (COMMA argument)*)? RIGHT_PAREN window?
;

// check, if stronger constraints are useful
// 1. most common aggregate functions: SUM, AVG, COUNT, MIN, MAX, ROW_NUMBER, LAG, LEAD
// 2. No ORDER BY outside window-function
// 3. avoid mixing aggregate and window-function, enforce to use CTE/block
window
    : OVER LEFT_PAREN (PARTITION partitionex+=expression (COMMA partitionex+=expression)*)? (ORDER orderex+=expression (COMMA orderex+=expression)* (ASC|DESC)?)? frame? RIGHT_PAREN
   ;

// Default frame for ORDER: RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
frame
    : ROWS BETWEEN lower=limit AND upper=limit
;

limit
    : UNBOUNDED (PRECEDING | FOLLOWING)
    | CURRENT ROW
    | INT (PRECEDING | FOLLOWING)
;

argument
    // expression first — see KQL.g4.
    : expression | logical_expression | identity=ID
;

field
    : alias=ID DOT col=ID
;

source
    :  tab=ID alias=ID
;

out
    : OUT expression (h=ID (label=STRING)?)? (idx=INT)?
;

group
    : GROUP expression (idx=INT)?
;

order
    : ORDER (expression) (ASC | DESC)? (idx=INT)?
;

filter
    : FILTER logical_expression
;

having
    : HAVING logical_expression
;

logical_expression
    : NOT negate=logical_expression
    | left=logical_expression AND right=logical_expression
    | left=logical_expression OR right=logical_expression
    | unary_logical_expression
    ;

unary_logical_expression
    : expression
        ( // BETWEEN owns its pair: the AND belongs to the range and binds tighter than the logical
          // AND, exactly as in SQL. That is why it is a production of its own and no longer a
          // member of `operator` — the keyword announces the pair unambiguously.
          //
          // It used to be written as a leading `(expression AND expression)` alternative available
          // after ANY operator. That silently swallowed predicates: since a bare expression is a
          // predicate, `a > 1 AND b` was ambiguous, ANTLR resolved it to the lowest-numbered
          // alternative, and `b` became a third operand of `>`. Its template `{0} > {1}` never
          // renders a third operand, so the predicate vanished without a violation or a warning —
          // and, worse, only in that operand order.
          //
          // PostgreSQL has the same ambiguity and resolves it by restricting what may follow
          // BETWEEN to a narrower expression class (b_expr in gram.y):
          // https://www.postgresql.org/docs/current/functions-comparison.html
          // Here the separate production suffices, because our `expression` cannot contain a
          // logical AND at all — one can only enter through the parentheses of a function call. So we
          // need neither the narrower class nor its parentheses: `BETWEEN 1 + 2 AND 5` parses as written.
          //
          // Kept identical to KQLRules.g4: the two grammars must accept the same language, or the
          // IQL round-trip stops reproducing the SQL it started from.
          BETWEEN expression AND expression
        | operator
            ( expression? // nothing or single expression
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
    // necessarily LOWER CASE, because ID is `LOWER (LOWER | DIGIT)*` while every operator above is
    // an upper-case keyword literal — so the two alternatives can never compete for the same text.
    //
    // That also means a lower-case `distinct` is NOT the DISTINCT alternative; it lands here, as a
    // custom operator that merely shares the name. It used to render worse than the pass-through it
    // looked like: SqlDialect.negatedOperatorTemplate normalises its argument and handed back the
    // ANSI `IS NOT DISTINCT FROM`, while the MariaDB (`<=>`) and Oracle (`DECODE`) overrides compare
    // exactly and never fired — both engines reject that form. FunctionValidator.checkKnownOperator
    // therefore rejects a spelling that differs from a real operator only in case, naming the
    // correct one; a genuinely foreign operator still only warns.
    //
    // Restricting this alternative to an upper-case token was considered and dropped: it would make
    // the collision structurally impossible, but the resulting parse error reads `mismatched input
    // 'distinct'` instead of naming the fix, and it would introduce the language's only upper-case
    // identifier class.
    | custom=ID
;

limitClause
 : LIMIT INT
;

OVER
    : 'OVER'
;

PARTITION
    : 'PARTITION'
;

ROWS
    : 'ROWS'
;

RANGE
    : 'RANGE'
;

UNBOUNDED
    : 'UNBOUNDED'
;

PRECEDING
    : 'PRECEDING'
;

FOLLOWING
    : 'FOLLOWING'
;

CURRENT
    : 'CURRENT'
;

ROW
    : 'ROW'
;

LIMIT
    : 'LIMIT'
;

QUERY
    : 'QUERY'
;

SELECT
    : 'SELECT'
;

UNION
    : 'UNION'
;

DISTINCT
    : 'DISTINCT'
;

ROLLUP
    : 'ROLLUP'
;

UNIONALL
    : 'UNIONALL'
;

MINUS
    : 'MINUS'
;

INTERSECT
    : 'INTERSECT'
;

REF
    : 'REF'
;

JOIN
    : 'JOIN'
;

EXISTS
    : 'EXISTS'
;

OWNER
    : 'OWNER'
;

OPTIONAL
    : 'OPTIONAL'
;

ALL
    : 'ALL'
;

NOT
    : 'NOT'
;

OUT
    : 'OUT'
;

GROUP
    : 'GROUP'
;

ORDER
    : 'ORDER'
;

ASC
    : 'ASC'
;

DESC
    : 'DESC'
;

AS
    : 'AS'
;

WITH
    : 'WITH'
;

FILTER
    : 'FILTER'
;

HAVING
    : 'HAVING'
;

NULL
    : 'NULL'
;

AND
    : 'AND'
;

OR
    : 'OR'
;

TIMESTAMP_STRING
    : '"' YYYY '-' MM '-' DD ' ' HH ':' MI ':' SS ('.' DIGIT DIGIT DIGIT)? (('+'|'-') HH ':' MI | 'Z')? '"'
;

DATE_STRING
    : '"' YYYY '-' MM '-' DD '"'
;

TIME_STRING
    : '"' HH ':' MI ':' SS ('.' DIGIT DIGIT DIGIT)? (('+'|'-') HH ':' MI | 'Z')? '"'
;

fragment YYYY
    : DIGIT DIGIT DIGIT DIGIT
;

fragment MM
    : '0' [1-9]
    | '1' [0-2]
;

fragment DD
    : '0' [1-9]
    | [12] DIGIT
    | '3' [01]
;

fragment HH
    : [01] DIGIT
    | '2' [0-3]
;

fragment MI
    : [0-5] DIGIT
;

fragment SS
    : [0-5] DIGIT
;

INT
:DIGIT+
;

NUMBER
    : ('.' DIGIT+ | DIGIT+ '.' DIGIT*)
;

fragment DIGIT
    : [0-9]
    ;

/** "any double-quoted string ("...") possibly containing escaped quotes" */
STRING
    : '"' ('\\"' | .)*? '"'
    ;

/** "any single-quoted string ('...') possibly containing escaped single-quotes" */
SQ_STRING
    : SINGLE_QUOTE ('\\\'' | .)*? SINGLE_QUOTE
    ;

fragment LOWER
: [a-z_]
;

HASHMARK: '#';
SINGLE_QUOTE:               '\'';
DOT :                       '.';
EQUALS:                     '=';
COMMA:                      ',';
SEMICOLON:                  ';';
COLON:                      ':';
LEFT_PAREN:                '(';
RIGHT_PAREN:               ')';
LEFT_CURLY:                '{';
RIGHT_CURLY:               '}';
PLUS_SIGN:                 '+';
MINUS_SIGN :               '-';
LEFT_BRACKET:              '[';
RIGHT_BRACKET:             ']';
LESS:                      '<';
GREATER:                   '>';
LESSEQ:                    '<=';
GREATEREQ:                 '>=';
// '<>' is the canonical spelling (SQL standard); '!=' is accepted and normalized to it
// by the query mapper, so the catalog and the model carry one surface text.
NOTEQ:                     '<>' | '!=';
LIKE:                      'LIKE' ;
BETWEEN:                   'BETWEEN';
ISNULL:                    'ISNULL';
IN:                        'IN';

PLACEHOLDER :
HASHMARK (LOWER | DIGIT)+
;

ID
    : LOWER (LOWER | DIGIT)*
;

DURATION : (DIGIT+ ('ms'|'s'|'min'|'h'|'d'|'w'|'mo'|'q'|'y'))+;

COMMENT
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

LINE_COMMENT
    : '//' .*? '\r'? '\n' -> channel(HIDDEN)
    ;

WS
    : [ \t\n\r]+ -> channel(HIDDEN)
;