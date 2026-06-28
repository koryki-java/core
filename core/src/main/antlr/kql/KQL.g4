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
 * specific language governing permissions and limitations
 * under the License.
 */

grammar KQL;

query
 : (WITH (block) (COMMA block)*)? set EOF
;

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
| from=ID? VIA crit=ID PLUS? source
| from=ID? PLUS? source VIA crit=ID
;

existslink : from=ID source
| from=ID VIA crit=ID source
| from=ID source VIA crit=ID
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
  expression operator
  (
    expression? // nothing or single expression
    | (expression AND expression) // pair of expressions
    | LEFT_PAREN expression (COMMA expression)* RIGHT_PAREN // set of expressions
  )
| LEFT_PAREN logical_expression RIGHT_PAREN
| EXISTS LEFT_PAREN exists RIGHT_PAREN
| expression operator? PLACEHOLDER
;

exists:
existslink (COMMA link)* filterClause?
;

operator
 : BETWEEN
| EQUALS
| GREATER
| GREATEREQ
| IN
| ISNULL
| LESS
| LESSEQ
| LIKE
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

// Standard-Frame für ORDER: RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
frame
    : ROWS BETWEEN lower=frame_bound AND upper=frame_bound
;

frame_bound
    : UNBOUNDED (PRECEDING | FOLLOWING)
    | CURRENT ROW
    | INT (PRECEDING | FOLLOWING)
;

argument
 : logical_expression | expression | identity=ID
;

field
 : alias=ID DOT name=ID
;


OVER : 'OVER';
ORDER : 'ORDER';
PARTITION : 'PARTITION';
ROWS : 'ROWS';
RANGE : 'RANGE';
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
SEMICOLON: ';';
COLON: ':';
PLUS: '+';
MULT: '*';
DIV: '/';
LEFT_PAREN: '(';
RIGHT_PAREN: ')';
LEFT_CURLY: '{';
RIGHT_CURLY: '}';

LEFT_BRACKET: '[';
RIGHT_BRACKET: ']';

LESS: '<';
GREATER: '>';
LESSEQ: '<=';
GREATEREQ: '>=';
LIKE: 'LIKE';
BETWEEN: 'BETWEEN';
ISNULL: 'ISNULL';
IN: 'IN';
NULL: 'NULL';

PLACEHOLDER :
HASHMARK (LOWER | DIGIT)*
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