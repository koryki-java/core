package ai.koryki.iql.rules;

public enum Math {

    add( "+"),
    minus( "-"),
    multiply( "*"),
    divide( "/");

    private String operator;

    Math(String operator) {
        this.operator = operator;
    }

    public String getOperator() {
        return operator;
    }

    public static String operator(String name) {
        if (add.name().equals(name)) {
            return add.getOperator();
        } if (minus.name().equals(name)) {
            return minus.getOperator();
        } if (multiply.name().equals(name)) {
            return multiply.getOperator();
        } if (divide.name().equals(name)) {
            return divide.getOperator();
        } else {
            return null;
        }
    }
}
