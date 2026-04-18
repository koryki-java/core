package ai.koryki.iql.rules;

import java.util.Arrays;
import java.util.List;

public interface Aggregate {

    List<String> aggregats = Arrays.asList(
            "count",
            "sum",
            "avg",
            "min",
            "max"
    );

    default String isAggregat(String name) {

        return aggregats.stream().filter(a -> a.equals(name)).findFirst().orElse(null);
    }

}
