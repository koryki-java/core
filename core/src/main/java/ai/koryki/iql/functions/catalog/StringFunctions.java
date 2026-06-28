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

import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.Families;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import java.util.List;

import static ai.koryki.iql.functions.FunctionArg.arg;
import static ai.koryki.iql.functions.FunctionArg.optionalArg;

/** String functions, mirroring the PostgreSQL "String Functions" chapter. */
public final class StringFunctions {

    private static final CoreTypeFamily TEXT = CoreTypeFamily.TEXT;
    private static final CoreTypeFamily INT = CoreTypeFamily.INTEGER;

    private StringFunctions() {
    }

    public static void register(FunctionRegistry r) {
        for (String name : List.of("char_length", "character_length", "length")) {
            r.register(def(name, ReturnTypes.INTEGER).args(arg("string", TEXT))
                    .doc("Number of characters in the string.")
                    .example(name + "(c.company_name)"));
        }
        r.register(def("octet_length", ReturnTypes.INTEGER).args(arg("string", TEXT))
                .doc("Number of bytes in the string.")
                .example("octet_length(c.company_name)"));
        r.register(def("bit_length", ReturnTypes.INTEGER).args(arg("string", TEXT))
                .doc("Number of bits in the string.")
                .example("bit_length(c.company_name)"));

        r.register(def("upper", ReturnTypes.TEXT).args(arg("string", TEXT))
                .doc("Converts the string to upper case.")
                .example("upper(c.company_name)"));
        r.register(def("lower", ReturnTypes.TEXT).args(arg("string", TEXT))
                .doc("Converts the string to lower case.")
                .example("lower(c.company_name)"));
        r.register(def("initcap", ReturnTypes.TEXT).args(arg("string", TEXT))
                .doc("Capitalizes the first letter of each word.")
                .example("initcap(c.contact_name)"));

        r.register(def("trim", ReturnTypes.TEXT).args(arg("string", TEXT), optionalArg("characters", TEXT))
                .doc("Removes *characters* (default spaces) from both ends.")
                .example("trim(c.company_name)"));
        r.register(def("ltrim", ReturnTypes.TEXT).args(arg("string", TEXT), optionalArg("characters", TEXT))
                .doc("Removes *characters* (default spaces) from the start.")
                .example("ltrim(c.postal_code)"));
        r.register(def("rtrim", ReturnTypes.TEXT).args(arg("string", TEXT), optionalArg("characters", TEXT))
                .doc("Removes *characters* (default spaces) from the end.")
                .example("rtrim(c.postal_code)"));
        r.register(def("btrim", ReturnTypes.TEXT).args(arg("string", TEXT), optionalArg("characters", TEXT))
                .doc("Removes *characters* (default spaces) from both ends (alias of trim)."));

        for (String name : List.of("substr", "substring")) {
            r.register(def(name, ReturnTypes.TEXT)
                    .args(arg("string", TEXT, "the source text"),
                          arg("start", INT, "1-based index of the first character to keep"),
                          optionalArg("length", INT, "number of characters to take; to the end of the string if omitted"))
                    .doc("Extracts the substring starting at *start* (1-based), optionally limited to *length* characters.")
                    .example("substr(c.company_name, 1, 3)"));
        }
        r.register(def("left", ReturnTypes.TEXT).args(arg("string", TEXT), arg("n", INT))
                .doc("First *n* characters.")
                .example("left(c.postal_code, 2)"));
        r.register(def("right", ReturnTypes.TEXT).args(arg("string", TEXT), arg("n", INT))
                .doc("Last *n* characters.")
                .example("right(c.phone, 4)"));
        r.register(def("reverse", ReturnTypes.TEXT).args(arg("string", TEXT))
                .doc("Reverses the string.")
                .example("reverse(c.company_name)"));
        r.register(def("repeat", ReturnTypes.TEXT).args(arg("string", TEXT), arg("number", INT))
                .doc("Repeats the string *number* times.")
                .example("repeat('-', 10)"));

        r.register(def("lpad", ReturnTypes.TEXT).args(arg("string", TEXT), arg("length", INT), optionalArg("fill", TEXT))
                .doc("Pads the string on the left to *length* using *fill* (default space).")
                .example("lpad(c.postal_code, 6, '0')"));
        r.register(def("rpad", ReturnTypes.TEXT).args(arg("string", TEXT), arg("length", INT), optionalArg("fill", TEXT))
                .doc("Pads the string on the right to *length* using *fill* (default space).")
                .example("rpad(c.postal_code, 10)"));

        r.register(def("concat", ReturnTypes.TEXT).variadic(arg("value", Families.ANY))
                .doc("Concatenates the text form of all arguments; null arguments are ignored.")
                .example("concat(e.first_name, ' ', e.last_name)"));
        r.register(def("concat_ws", ReturnTypes.TEXT).variadic(arg("separator", Families.ANY))
                .doc("Concatenates all arguments after the first, separated by *separator*.")
                .example("concat_ws(', ', c.city, c.country)"));

        r.register(def("replace", ReturnTypes.TEXT).args(arg("string", TEXT), arg("from", TEXT), arg("to", TEXT))
                .doc("Replaces all occurrences of *from* with *to*.")
                .example("replace(c.phone, '-', '')"));
        r.register(def("translate", ReturnTypes.TEXT).args(arg("string", TEXT), arg("from", TEXT), arg("to", TEXT))
                .doc("Replaces each character in *from* with the corresponding character in *to*.")
                .example("translate(c.company_name, 'äöü', 'aou')"));
        r.register(def("overlay", ReturnTypes.TEXT)
                .args(arg("string", TEXT), arg("replacement", TEXT), arg("start", INT), optionalArg("length", INT))
                .doc("Replaces a substring (dialect-specific syntax)."));

        r.register(def("starts_with", ReturnTypes.BOOLEAN).args(arg("string", TEXT), arg("prefix", TEXT))
                .doc("True if the string begins with *prefix*.")
                .example("starts_with(c.company_name, 'A')"));
        r.register(def("strpos", ReturnTypes.INTEGER).args(arg("string", TEXT), arg("substring", TEXT))
                .doc("Position of the first occurrence of *substring* (1-based, 0 if absent).")
                .example("strpos(c.phone, '-')"));
        r.register(def("split_part", ReturnTypes.TEXT).args(arg("string", TEXT), arg("delimiter", TEXT), arg("n", INT))
                .doc("Splits at *delimiter* and returns the *n*-th field (1-based).")
                .example("split_part(c.phone, '-', 1)"));

        r.register(def("ascii", ReturnTypes.INTEGER).args(arg("character", TEXT))
                .doc("Numeric code of the first character.")
                .example("ascii(c.company_name)"));
        r.register(def("chr", ReturnTypes.TEXT).args(arg("code", INT))
                .doc("Character with the given numeric code.")
                .example("chr(65)"));
        r.register(def("to_hex", ReturnTypes.TEXT).args(arg("number", INT))
                .doc("Hexadecimal representation of the number.")
                .example("to_hex(255)"));
        r.register(def("md5", ReturnTypes.TEXT).args(arg("string", TEXT))
                .doc("MD5 hash as a hexadecimal string.")
                .example("md5(c.company_name)"));

        r.register(positionFunction());
    }

    /** ANSI {@code POSITION(substr IN str)}; dialects without it override (e.g. Oracle INSTR). */
    public static FunctionDefinition positionFunction() {
        return def("position", ReturnTypes.INTEGER)
                .args(arg("substr", TEXT), arg("str", TEXT))
                .template("POSITION({0} IN {1})")
                .doc("Position of the first occurrence of *substr* in *str* (1-based, 0 if absent).")
                .example("position('-', c.phone)");
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.STRING);
    }
}
