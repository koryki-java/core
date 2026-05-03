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

join
    : OPTIONAL?  crit=ID
        (REF ref=ID
        |
        (
          ( entity=join_entity // join an table
          )
          child=link?)
        )
;

subset : LEFT_PAREN set RIGHT_PAREN alias=ID ;

exists
    :  parent=ID crit=ID (
          entity=exists_entity  // exists table
        ) child=link?
;

expression
    : LEFT_PAREN expression RIGHT_PAREN
    | date_literal
    | field
    | function
    | INT
    | NUMBER
    | SQ_STRING
    | NULL
    | LEFT_PAREN select RIGHT_PAREN
;

date_literal
    : DATE DATE_FORMAT
    | TIME TIME_FORMAT
    | TIMESTAMP TIMESTAMP_FORMAT
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
    : alias=ID DOT col=ID
;

source
    :  tab=ID alias=ID
;

out
    : OUT expression (h=ID)? (label=STRING)? (idx=INT)?
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
    : expression operator
        ( expression? // nothing or single expression
        | (expression AND expression) // pair of expressions
        | LEFT_PAREN expression (COMMA expression)* RIGHT_PAREN // set of expressions
        )
    | LEFT_PAREN logical_expression RIGHT_PAREN
    | EXISTS LEFT_PAREN exists RIGHT_PAREN
    | expression operator? PLACEHOLDER
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

INVERS
    : 'INVERS'
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

DATE
    : 'DATE'
;

TIME
    : 'TIME'
;

TIMESTAMP
    :  'TIMESTAMP'
;

DATE_FORMAT
    : SINGLE_QUOTE YEAR '-' MONTH '-' DAY SINGLE_QUOTE                           // 'YYYY-MM-DD'
    ;

TIME_FORMAT
    : SINGLE_QUOTE HOUR ':' MINUTE ':' SECOND ( '.' DIGIT DIGIT DIGIT)? (('+'|'-') OFFSET)? SINGLE_QUOTE                         // 'HH:MI:SS'
    ;

TIMESTAMP_FORMAT
    : SINGLE_QUOTE YEAR '-' MONTH '-' DAY ' ' HOUR ':' MINUTE ':' SECOND ( '.' DIGIT DIGIT DIGIT)? (('+'|'-') OFFSET)?  SINGLE_QUOTE                      // 'YYYY-MM-DD HH:MI:SS'
    ;

OFFSET
    : HOUR ':' MINUTE
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
PLUS_SIGN:                      '+';
MINUS_SIGN : '-';
MULT_SIGN:                      '*';
DIV_SIGN:                      '/';

LEFT_BRACKET:                '[';
RIGHT_BRACKET:               ']';

LESS:                   '<';
GREATER:                '>';
LESSEQ:                 '<=';
GREATEREQ:              '>=';
LIKE:                   'LIKE' ;
BETWEEN:                'BETWEEN';
ISNULL:                 'ISNULL';
IN:                     'IN';

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