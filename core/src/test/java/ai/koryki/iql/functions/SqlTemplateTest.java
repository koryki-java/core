package ai.koryki.iql.functions;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.koryki.iql.functions.FunctionArg.arg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlTemplateTest {

    @Test
    void literalOnlyTemplateNeedsNoRenderer() {
        Function f = function("now");
        String sql = new SqlTemplate("CURRENT_TIMESTAMP").render(null, f, 0);
        assertEquals("CURRENT_TIMESTAMP", sql);
    }

    @Test
    void unclosedPlaceholderIsRejectedAtConstruction() {
        assertThrows(KorykiaiException.class, () -> new SqlTemplate("POSITION({0 IN {1})"));
    }

    @Test
    void nonNumericPlaceholderIsRejectedAtConstruction() {
        assertThrows(KorykiaiException.class, () -> new SqlTemplate("FOO({x})"));
    }

    @Test
    void outOfRangeArgumentFailsAtRenderTime() {
        Function f = function("foo");
        SqlTemplate t = new SqlTemplate("FOO({0})");
        assertThrows(KorykiaiException.class, () -> t.render(null, f, 0));
    }

    @Test
    void templatedDefinitionEnforcesArityBeforeRendering() {
        FunctionDefinition def = new FunctionDefinition("position", ReturnTypes.INTEGER)
                .args(arg("substr"), arg("str"))
                .template("POSITION({0} IN {1})");

        Function call = function("position");
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> def.render(null, call, 0));
        assertEquals("position expects (substr, str), got 0 arguments", e.getMessage());
    }

    private static Function function(String name) {
        Function f = new Function();
        f.setFunc(name);
        f.setArguments(List.<Expression>of());
        return f;
    }
}
