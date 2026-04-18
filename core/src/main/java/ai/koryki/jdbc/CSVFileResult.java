package ai.koryki.jdbc;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class CSVFileResult<C extends ColumnInfo> implements ResultProcessor<C> {

    private File file;
    private PrintWriter writer;
    private List<C> infos;

    public CSVFileResult(File file) {
        this(file, StandardCharsets.UTF_8);
    }


    public CSVFileResult(File file, Charset cs)  {
        this.file = file;

        try {
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), cs));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean append(List<Object> row) {

        writer.println(toCSV(formatRow(row, getInfos())));
        return true;
    }

    public static String toCSV(List<String> row) {
        return row.stream().map(c -> c != null ? mask(c.toString()) : "").collect(Collectors.joining(", ")) + System.lineSeparator();
    }

    private static String mask(String text) {
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void close() {
        if (writer != null)              {
            writer.close();
        }
    }

    public File getFile() {
        return file;
    }

    public List<C> getInfos() {
        return infos;
    }

    @Override
    public void setInfos(List<C> infos) {
        this.infos = infos;
    }
}
