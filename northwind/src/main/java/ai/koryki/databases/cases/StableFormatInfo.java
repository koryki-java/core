package ai.koryki.databases.cases;

import ai.koryki.jdbc.ColumnInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class StableFormatInfo implements ColumnInfo {

    private String header;

    @Override
     public String toString(Object o) {

        if (o instanceof Number) {
            return formatNumber((Number) o);
        }

        return o != null ? o.toString() : "";
    }

    public static String formatNumber(Number number) {

        BigDecimal bd = (number instanceof BigDecimal)
                ? (BigDecimal) number
                : new BigDecimal(number.toString());
        return bd.setScale(1, RoundingMode.HALF_DOWN).toString();
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
