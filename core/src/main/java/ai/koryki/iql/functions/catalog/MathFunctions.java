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
package ai.koryki.iql.functions.catalog;

import ai.koryki.antlr.Text;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import java.util.List;

import static ai.koryki.iql.functions.FunctionArg.arg;
import static ai.koryki.iql.functions.FunctionArg.optionalArg;

/**
 * Mathematical functions, mirroring the PostgreSQL "Mathematical Functions"
 * chapter. Deliberately functions only — PostgreSQL's operator spellings
 * ({@code %}, {@code |/}, {@code ||/}, {@code ^}) are not exposed in KQL:
 * the audience is business analysts, and Excel-style {@code mod}/{@code sqrt}/
 * {@code power} read better than operator symbols ({@code %} in particular
 * reads as "percent").
 */
public final class MathFunctions {

    /**
     * The one point where {@code greatest}/{@code least} are not portable — measured, not copied.
     * A constant, so both functions carry the same wording and it is not updated in one place and
     * forgotten in the other.
     */
    private static final String NULL_HANDLING_DIFFERS =
            "**Not portable when an argument can be NULL.** The SQL standard lets NULL win: the "
                    + "result is NULL as soon as any argument is NULL. PostgreSQL deviates and "
                    + "ignores NULL arguments, returning the largest (or smallest) of the rest."
                    + Text.NL
                    + Text.NL
                    + "With one NULL argument — `greatest(NULL, 3)`:"
                    + Text.NL
                    + Text.NL
                    + "| Result | Dialects |" + Text.NL
                    + "|---|---|" + Text.NL
                    + "| `3` — NULL ignored | duckdb, mssql, postgresql |" + Text.NL
                    + "| `NULL` — NULL wins | mariadb, oracle, snowflake, sqlite, trino |"

                    + Text.NL
                    + Text.NL
                    + "koryki does not paper over this: unifying it would mean rewriting one side, and "
                    + "with a variable number of arguments only through a replacement construct. Make "
                    + "the intent explicit instead — wrap each argument in `coalesce` with a neutral "
                    + "value to ignore NULLs, or keep them out of the call to let NULL win everywhere."
                    + Text.NL
                    + Text.NL
                    + "See: [PostgreSQL — GREATEST and LEAST]"
                    + "(https://www.postgresql.org/docs/current/functions-conditional.html).";

    private MathFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(def("abs", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC, "the number to take the absolute value of"))
                .doc("Absolute value."));
        r.register(def("ceil", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC, "the number to round up"))
                .doc("Nearest integer greater than or equal to *value*."));
        r.register(def("floor", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC, "the number to round down"))
                .doc("Nearest integer less than or equal to *value*."));
        r.register(def("round", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC, "the number to round"))
                .doc("Rounds to the nearest whole number."));
        // Split from the 1-arg form so PostgreSQL can cast: round(double precision, integer) does
        // not exist there, so round(ln(x), 6) failed outright until this override.
        r.register(def("round", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC, "the number to round"),
                        arg("scale", CoreTypeFamily.INTEGER, "number of decimal places to keep"))
                .doc("Rounds to *scale* decimal places.")
                .paragraph("Half-way rounding of binary-float inputs is engine-defined: a value such as "
                        + "`0.15` is stored as `0.149999…`, so engines may round it up or down."
                        + Text.NL
                        + Text.NL
                        + "See: [What Every Computer Scientist Should Know About Floating-Point Arithmetic]"
                        + "(https://docs.oracle.com/cd/E19957-01/806-3568/ncg_goldberg.html)."));
        // Two definitions rather than one with an optional scale, because the dialects need a
        // different template per arity (T-SQL ROUND(x, 0, 1) vs ROUND(x, n, 1)) and an arity
        // override can only pick out a distinct overload.
        r.register(def("trunc", ReturnTypes.ARG0)
                .args(arg("value", Families.NUMERIC, "the number to truncate"))
                .doc("Truncates toward zero — 1.99 becomes 1, and -1.99 becomes -1. Unlike `round` "
                        + "it never carries, so use it where a value must not be inflated and `round` "
                        + "where it should be nearest."));
        r.register(def("trunc", ReturnTypes.ARG0)
                .args(arg("value", Families.NUMERIC, "the number to truncate"),
                        arg("scale", CoreTypeFamily.INTEGER, "number of decimal places to keep"))
                .doc("Truncates toward zero, keeping *scale* decimal places."));
        // The NULLIF mirrors the divide-by-zero guard on `/` (see Families/SqlDialect.renderDivision).
        // Measured, a bare mod(7, 0) errors on postgresql/mssql/trino, is NULL on mariadb/sqlite/duckdb
        // and returns *7* on Oracle — the only case in the catalog where a zero divisor yields a
        // plausible-looking wrong number rather than an error or a blank.
        r.register(def("mod", ReturnTypes.ARG0).args(arg("dividend", Families.NUMERIC, "the number being divided"),
                        arg("divisor", Families.NUMERIC, "the number to divide by"))
                .template("mod({0}, NULLIF({1}, 0))")
                .doc("Remainder of *dividend* / *divisor*. A zero *divisor* yields no value rather "
                        + "than an error, as with `/`."));
        r.register(def("sign", ReturnTypes.INTEGER)
                .args(arg("value", Families.NUMERIC, "the number whose sign is taken"))
                .doc("Sign of the argument: -1, 0 or 1."));

        // Row-wise, not aggregate: greatest(a, b) compares columns within one row, where max(a)
        // collapses rows. Operands are ORDERED rather than NUMERIC — comparing dates or text is
        // just as meaningful, and it is the same total order the < operator needs.
        // NULL handling: deliberately NOT unified but documented. The dialects disagree here and
        // both camps have a case -- the SQL standard lets NULL propagate, PostgreSQL deviates
        // explicitly. Aligning them would mean rewriting one side, and variadically it would need
        // a substitute construction; the note is the more honest answer.
        r.register(def("greatest", ReturnTypes.LEAST_RESTRICTIVE)
                // At least TWO values. With one argument the question is meaningless, and it was
                // answered differently: MariaDB rejected it, SQLite read max(x)/min(x) as an
                // aggregate and silently folded every row into one.
                .variadic(arg("value", Families.ORDERED, "the values to compare"),
                          arg("more", Families.ORDERED, "further values to compare against"))
                .doc("Largest of the arguments, compared across the columns of one row — not to be "
                        + "confused with `max`, which collapses rows into one.")
                .paragraph(NULL_HANDLING_DIFFERS));
        r.register(def("least", ReturnTypes.LEAST_RESTRICTIVE)
                // At least TWO values. With one argument the question is meaningless, and it was
                // answered differently: MariaDB rejected it, SQLite read max(x)/min(x) as an
                // aggregate and silently folded every row into one.
                .variadic(arg("value", Families.ORDERED, "the values to compare"),
                          arg("more", Families.ORDERED, "further values to compare against"))
                .doc("Smallest of the arguments, compared across the columns of one row — the mirror "
                        + "of `greatest`, and not to be confused with `min`.")
                .paragraph(NULL_HANDLING_DIFFERS));

        r.register(def("power", ReturnTypes.FLOAT).args(arg("base", Families.NUMERIC, "the number to raise"),
                        arg("exponent", Families.NUMERIC, "the power to raise the base to"))
                .doc("*base* raised to the power of *exponent*."));
        r.register(def("sqrt", ReturnTypes.FLOAT).args(arg("value", Families.NUMERIC, "the number to take the square root of"))
                .doc("Square root."));
        r.register(def("exp", ReturnTypes.FLOAT)
                .args(arg("value", Families.NUMERIC, "the exponent to raise e to"))
                .doc("Exponential — e raised to *value*."));

        // There is deliberately no one-argument log(). Measured, log(100) returns 2.0 on PostgreSQL
        // and SQLite (base 10) but 4.605 on MariaDB and SQL Server (natural log) — the same call
        // meaning two different functions. No dialect override can fix an ambiguous *meaning*, only
        // a name that avoids it, so the two readings are spelled out and the bare name is not taken.
        r.register(def("ln", ReturnTypes.FLOAT).args(arg("value", Families.NUMERIC, "the number to take the natural logarithm of"))
                .doc("Natural logarithm — the logarithm to base e."));
        r.register(def("log10", ReturnTypes.FLOAT).args(arg("value", Families.NUMERIC, "the number to take the base-10 logarithm of"))
                .doc("Base-10 logarithm."));
        r.register(def("log", ReturnTypes.FLOAT)
                .args(arg("base", Families.NUMERIC, "the base of the logarithm"),
                        arg("value", Families.NUMERIC, "the number to take the logarithm of"))
                .doc("Logarithm of *value* to the given *base*: log(2, 8) is 3. The base comes first, "
                        + "as it reads aloud."));

        // Trigonometry, in radians. Included for completeness of the common set; the inverse-hyperbolic
        // and cot/atan2/degrees/radians spellings are not, being both less portable and outside what
        // this catalog's audience reaches for (see math-review.md for the measurements).
        for (String name : List.of("sin", "cos", "tan", "asin", "acos", "atan")) {
            r.register(def(name, ReturnTypes.FLOAT)
                    .args(arg("value", Families.NUMERIC, "the angle in radians"))
                    .doc("Trigonometric " + name + " (argument in radians)."));
        }

        r.register(def("pi", ReturnTypes.FLOAT).args()
                .doc("Approximate value of π."));
        r.register(def("random", ReturnTypes.FLOAT).args()
                .doc("Random value in the range 0.0 <= x < 1.0."));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.MATH);
    }
}
