package ai.koryki.iql.query;

import java.util.List;

public class Window {
    private List<Expression> partition;
    private List<Expression> order;
    private Limit upper;
    private Limit lower;

    public List<Expression> getPartition() {
        return partition;
    }

    public void setPartition(List<Expression> partition) {
        this.partition = partition;
    }

    public List<Expression> getOrder() {
        return order;
    }

    public void setOrder(List<Expression> order) {
        this.order = order;
    }

    public Limit getUpper() {
        return upper;
    }

    public void setUpper(Limit upper) {
        this.upper = upper;
    }

    public Limit getLower() {
        return lower;
    }

    public void setLower(Limit lower) {
        this.lower = lower;
    }
}
