package ai.koryki.iql.query;

public class Limit {
    public static Limit UNBOUNDED_PRECEDING() { return  new Limit("UNBOUNDED PRECEDING"); }
    public static Limit UNBOUNDED_FOLLOWING() { return  new Limit("UNBOUNDED FOLLOWING"); }
    public static Limit CURRENT_ROW() { return new Limit("CURRENT ROW"); }
    public static Limit PRECEDING(int n) { return new Limit("PRECEDING", n); }
    public static Limit FOLLOWING(int n) { return new Limit("FOLLOWING", n); }

    private int num;
    private String name;

    private Limit(String name, int n) {
        this.name = name;
        this.num = n;
    }

    private Limit(String name) {
        this.name = name;
    }

    public int getNum() {
        return num;
    }

    public String getName() {
        return name;
    }

}
