package ai.koryki.snowflake.covid19;

import ai.koryki.jdbc.CSVFileResult;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class StreamingHandler extends DefaultHandler {

    private int row = 0;
    private StringBuilder buffer = new StringBuilder();

    List<String> cells = new ArrayList<>();

    private PrintWriter writer;

    public StreamingHandler(PrintWriter writer) {
        this.writer = writer;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {

        buffer.setLength(0); // reset
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        buffer.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {

        if ("Cell".equals(qName)) {
            cells.add(buffer.toString());
            buffer.setLength(0); // reset
        } else if ("Row".equals(qName)) {
            writer.print(CSVFileResult.toCSV(cells));
            cells.clear();
            row++;
        } else if ("Result".equals(qName)) {
            System.out.print(row);
        }
    }
}
