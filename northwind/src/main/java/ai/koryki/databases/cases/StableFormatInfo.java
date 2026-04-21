package ai.koryki.databases.cases;

import ai.koryki.jdbc.ColumnInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class StableFormatInfo implements ColumnInfo {

    private String header;

    @Override
     public String toString(Object o) {

        if (o instanceof Float || o instanceof  Double || o instanceof  BigDecimal ) {
            return formatNumber((Number) o);
//        } if (o instanceof BigDecimal b) {
//            return formatBigDecimal(b);
        }

        return o != null ? o.toString() : "";
    }

    public static String formatNumber(Number number) {

        BigDecimal bd = (number instanceof BigDecimal)
                ? (BigDecimal) number
                : new BigDecimal(number.toString());
        return bd.setScale(2, RoundingMode.DOWN).toString();

//        double value = number.doubleValue();
//        return String.format(Locale.US, "%.2f", value);
    }

    public static String formatBigDecimal(BigDecimal bd) {

        if (bd.stripTrailingZeros().scale() > 0) {
            return bd.setScale(2, RoundingMode.HALF_UP).toString();
        }

        return String.format(Locale.US, "%.2f", bd);
    }

    public String getHeader() {
        return header;
    }

    @Override
    public void setHeader(String header) {
        this.header = header;
    }

    @Override
    public String toString() {
        return getHeader();
    }
}
