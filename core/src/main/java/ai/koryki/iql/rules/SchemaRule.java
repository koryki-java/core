package ai.koryki.iql.rules;

import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Field;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Source;
import ai.koryki.scaffold.domain.Model;

import java.util.Deque;

public class SchemaRule {

    private final Model model;

    public SchemaRule(Model model) {
        this.model = model;
    }

    public void apply(Query query) {

        SchemaRule.SchemaVisitor v = new SchemaRule.SchemaVisitor(model);
        new Walker().walk(query, v);

    }

    private static class SchemaVisitor implements Visitor {
        private final Model model;

        public SchemaVisitor(Model model) {
            this.model = model;
        }
        public boolean visit(Deque<Object> deque, Source source) {

            model.getTable(source.getName());

            return true;
        }

        public boolean visit(Deque<Object> deque, Field column) {
            return true;
        }

    }

}
