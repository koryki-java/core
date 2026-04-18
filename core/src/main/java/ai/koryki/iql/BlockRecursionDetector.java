package ai.koryki.iql;

import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;

import java.util.Deque;
import java.util.List;

public class BlockRecursionDetector implements Collector<Boolean> {
    private boolean recursive;

    private LinkResolver resolver;


    public BlockRecursionDetector(LinkResolver resolver) {
        this.resolver = resolver;
    }

    public boolean visit(Deque<Object> deque, Select select) {
        apply(select.getStart(), select.getJoin());
        return true;
    }

    protected void apply(Source left, List<Join> join) {

        for (Join j : join) {
            joinColumns(left, j);
            apply(j.getSource(), j.getJoin());
        }
    }

    private void joinColumns(Source left, Join join) {

        Source right = join.getSource();
        if (right != null) {
            //boolean invers = join.isInvers();
            boolean invers = resolver.isInverse(join.getCrit());
            Source start = invers ? right : left;
            Source end = invers ? left : right;

            joinColumns(start, end);
        }
    }

    protected void joinColumns(Source start, Source end) {

        String startTable = start.getName();
        String endTable = end.getName();

        boolean b1 = resolver.isTableInDatabase(startTable);
        boolean b2 = resolver.isTableInDatabase(endTable);

        recursive |= !b1 || !b2;
    }

    @Override
    public Boolean collect() {
        return recursive;
    }
}
