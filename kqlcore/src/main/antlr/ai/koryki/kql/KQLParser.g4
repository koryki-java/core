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

parser grammar KQLParser;

options { tokenVocab=KQLLexer; }

import KQLRules;

// The core variant of the start rule: no visualiseClause. VISUALISE is therefore already unknown
// to the parser here -- a query using it fails at this point with a syntax error.
query
 : (WITH (block) (COMMA block)*)? set EOF
;
