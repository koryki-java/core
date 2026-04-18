package ai.koryki.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ListResult<C extends ColumnInfo> implements ResultProcessor<C> {

    private List<C> infos;
    private List<List<Object>> rows = new ArrayList<>();

    public ListResult() {

    }

    @Override
    public boolean append(List<Object> row) {
        return rows.add(row);
    }

    @Override
    public void close() {

    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public String toCSV() {
        StringBuilder b = new StringBuilder();

        if (getInfos() != null) {
            b.append(CSVFileResult.toCSV(formatRow(getInfos(), getInfos())));
        }

        rows.forEach(l -> {

            b.append(CSVFileResult.toCSV(formatRow(l, getInfos())));
        });

        return b.toString();
    }

    public String toSortedCSV() {
        StringBuilder b = new StringBuilder();

        if (getInfos() != null) {
            b.append(CSVFileResult.toCSV(formatRow(getInfos(), getInfos())));
        }

        List<String> rl = new ArrayList<>(rows.stream().map(r -> CSVFileResult.toCSV(formatRow(r, getInfos()))).toList());
        Collections.sort(rl);

        rl.forEach(b::append);

        return b.toString();
    }


    public List<C> getInfos() {
        return infos;
    }

    @Override
    public void setInfos(List<C> infos) {
        this.infos = infos;
    }
}
