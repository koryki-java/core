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

link : from=ID? (BAR|PLUS)? source
| from=ID? BAR crit=ID PLUS? source
| from=ID? PLUS? source BAR crit=ID
;

existslink : from=ID (BAR)? source
| from=ID BAR crit=ID source
| from=ID source BAR crit=ID
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
 : expression (h=ID)? (label=STRING)? ((ASC | DESC) idx=INT?)?
;

expression
 : LEFT_PAREN expression RIGHT_PAREN
| left=expression (MULT | DIV) right=expression
| left=expression (PLUS | BAR) right=expression
| date_literal
| field
| function
| INT
| NUMBER
| SQ_STRING
| LEFT_PAREN select RIGHT_PAREN
;

date_literal
 : DATE DATE_FORMAT
| TIME TIME_FORMAT
| TIMESTAMP TIMESTAMP_FORMAT
;

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
    : ROWS BETWEEN lower=limit AND upper=limit
;

limit
    : UNBOUNDED (PRECEDING | FOLLOWING)
    | CURRENT ROW
    | INT (PRECEDING | FOLLOWING)
;

argument
 : expression | identity=ID
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
DATE : 'DATE';
TIME : 'TIME';
TIMESTAMP : 'TIMESTAMP';
SET_UNION : 'UNION';
SET_UNIONALL : 'UNIONALL';
SET_MINUS : 'MINUS';
SET_INTERSECT : 'INTERSECT';
EXISTS : 'EXISTS';

DISTINCT : 'DISTINCT';
ROLLUP : 'ROLLUP';


DATE_FORMAT
 : SINGLE_QUOTE YEAR '-' MONTH '-' DAY SINGLE_QUOTE // 'YYYY-MM-DD'
;

TIME_FORMAT
 : SINGLE_QUOTE HOUR ':' MINUTE ':' SECOND ( '.' DIGIT DIGIT DIGIT)? (('+'|'-') TZ)? SINGLE_QUOTE // 'HH:MI:SS'
;

TIMESTAMP_FORMAT
 : SINGLE_QUOTE YEAR '-' MONTH '-' DAY ' ' HOUR ':' MINUTE ':' SECOND ( '.' DIGIT DIGIT DIGIT)? (('+'|'-') TZ)? SINGLE_QUOTE // 'YYYY-MM-DD HH:MI:SS'
;

TZ
 : HOUR ':' MINUTE
;


fragment DIGIT
 : [0-9]
;


fragment YEAR
 : DIGIT DIGIT DIGIT DIGIT
;

fragment MONTH
 : '0' [1-9]
| '1' [0-2]
;

fragment DAY
 : '0' [1-9]
| [12] DIGIT
| '3' [01]
;

fragment HOUR
 : [01] DIGIT
| '2' [0-3]
;

fragment MINUTE
: [0-5] DIGIT
;

fragment SECOND
: [0-5] DIGIT
;

INT
:DIGIT+
;

NUMBER
: '-'? ('.' DIGIT+ | DIGIT+ ( '.' DIGIT*)?)
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

PLACEHOLDER :
HASHMARK (LOWER | DIGIT)*
;

ID
: LOWER (LOWER | DIGIT)*
;

COMMENT
: '/*' .*? '*/' -> channel(HIDDEN)
;

LINE_COMMENT
: '//' .*? '\r'? '\n' -> channel(HIDDEN)
;

WS
: [ \t\n\r]+ -> channel(HIDDEN)
;