package ai.koryki.iql.query;

public class Limit {
    public static Limit UNBOUNDED_PRECEDING() { return  new Limit("UNBOUNDED PRECEDING"); }
    public static Limit UNBOUNDED_FOLLOWING() { return  new Limit("UNBOUNDED FOLLOWING"); }
    public static Limit CURRENT_ROW() { return new Limit("CURRENT ROW"); }
    public static Limit PRECEDING(int n) { return new Limit("PRECEDING", n); }
    public static Limit FOLLOWING(int n) { return new Limit("FOLLOWING", n); }

    private int num;
    /**
     * Whether this bound carries a count at all. Without this flag "no count" was
     * indistinguishable from "count is zero" -- both had num == 0 -- and the renderers omitted the
     * number for {@code 0 PRECEDING}. That produced a bare PRECEDING, which all eight dialects
     * reject, although 0 PRECEDING is valid SQL (equivalent to CURRENT ROW).
     */
    private boolean counted;
    private String name;

    private Limit(String name, int n) {
        this.name = name;
        this.num = n;
        this.counted = true;
    }

    private Limit(String name) {
        this.name = name;
    }

    public boolean isCounted() {
        return counted;
    }

    public int getNum() {
        return num;
    }

    public String getName() {
        return name;
    }

}
