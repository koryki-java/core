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

lexer grammar KQLTokens;

// Only the tokens core actually needs.
//
// Not here: the 22 VISUALISE keywords. They live in KQLVizLexer.g4 of the standalone project
// kqlvisualise (repository ai.koryki.visualise). So the core lexer does not know the word
// VISUALISE -- such a query already fails at lexing. That is the price of the clean separation:
// the message does not name 'VISUALISE' but breaks the word up into unknown character sequences.
//
// Also removed, because no rule on either side references them:
//   RANGE SEMICOLON COLON LEFT_CURLY RIGHT_CURLY
// Kept despite having no parser reference: SINGLE_QUOTE and HASHMARK (used by SQ_STRING and
// PLACEHOLDER respectively in the lexer itself) as well as COMMENT/LINE_COMMENT/WS (channel
// HIDDEN).

// Every token lives here, and the ORDER IS SIGNIFICANT: the first rule that matches wins, so
// the keywords must stay ahead of ID. (In this grammar ID is lowercase-only, so the keywords
// could not collide anyway — but the ordering is preserved verbatim rather than relied upon.)

OVER : 'OVER';
ORDER : 'ORDER';
PARTITION : 'PARTITION';
ROWS : 'ROWS';
UNBOUNDED : 'UNBOUNDED';
PRECEDING : 'PRECEDING';
FOLLOWING : 'FOLLOWING';
CURRENT : 'CURRENT';
ROW : 'ROW';

WITH : 'WITH';
AS : 'AS';
LIMIT : 'LIMIT';
DESC : 'DESC';
ASC : 'ASC';
FIND : 'FIND';
FILTER : 'FILTER';
FETCH : 'FETCH';
AND : 'AND';
OR : 'OR';
NOT : 'NOT';
SET_UNION : 'UNION';
SET_UNIONALL : 'UNIONALL';
SET_MINUS : 'MINUS';
SET_INTERSECT : 'INTERSECT';
EXISTS : 'EXISTS';

DISTINCT : 'DISTINCT';
ROLLUP : 'ROLLUP';

TIMESTAMP_STRING
 : '"' YYYY '-' MM '-' DD ' ' HH ':' MI ':' SS ('.' DIGIT DIGIT DIGIT)? (('+'|'-') HH ':' MI | 'Z')? '"'
;

DATE_STRING
 : '"' YYYY '-' MM '-' DD '"'
;

TIME_STRING
 : '"' HH ':' MI ':' SS ('.' DIGIT DIGIT DIGIT)? (('+'|'-') HH ':' MI | 'Z')? '"'
;

fragment DIGIT
 : [0-9]
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

STRING
: '"' ('\\"' | .)*? '"'
;

SQ_STRING
: SINGLE_QUOTE ('\\\'' | .)*? SINGLE_QUOTE
;

fragment LOWER
: [a-z_]
;

SINGLE_QUOTE: '\'';
DOT: '.';
BAR: '-';
VIA: 'VIA';

EQUALS: '=';
HASHMARK: '#';
COMMA: ',';
PLUS: '+';
MULT: '*';
DIV: '/';
LEFT_PAREN: '(';
RIGHT_PAREN: ')';
LEFT_BRACKET:              '[';
RIGHT_BRACKET:             ']';

LESS: '<';
GREATER: '>';
LESSEQ: '<=';
GREATEREQ: '>=';
// '<>' is the canonical spelling (SQL standard); '!=' is accepted and normalized to it
// by the query mapper, so the catalog and the model carry one surface text.
NOTEQ: '<>' | '!=';
LIKE: 'LIKE';
BETWEEN: 'BETWEEN';
ISNULL: 'ISNULL';
IN: 'IN';
NULL: 'NULL';

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
