package ai.koryki.jdbc;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

public class XMLFileResult<C extends ColumnInfo> implements ResultProcessor<C> {

    private File file;
    private final XMLStreamWriter writer;
    private final BufferedOutputStream outputStream;

    private static String ROOT_TAG = "Result";
    private static String ROW_TAG = "Row";
    private static String CELL_TAG = "Cell";
    private List<C> infos;

    private boolean spaces;

    public XMLFileResult(File file) {
        this(file, StandardCharsets.UTF_8);
    }

    public XMLFileResult(File file, Charset cs)  {
        this(file, cs, false);
    }

    public XMLFileResult(File file, Charset cs, boolean spaces)  {
        this.file = file;
        this.spaces = spaces;

        try {
            this.outputStream = new BufferedOutputStream(
                    Files.newOutputStream(file.toPath(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING),
                    64 * 1024  // 64KB Buffer
            );
            // XML Output Factory konfigurieren
            XMLOutputFactory factory = XMLOutputFactory.newInstance();

            // Pretty-Print aktivieren (optional)
            writer = factory.createXMLStreamWriter(outputStream, cs.name());

            writer.writeStartDocument(cs.name(), "1.0");
            writer.writeCharacters(System.lineSeparator());

            writer.writeStartElement(ROOT_TAG);
            writer.writeCharacters(System.lineSeparator());


        } catch (XMLStreamException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean append(List<Object> row) {


        writeRow(row.stream().map(o -> o != null ? o.toString() : "").collect(Collectors.toList()));

        return true;
    }

    private void writeRow(List<String> row) {

        try {
            writeIndent(2);
            writer.writeStartElement(ROW_TAG);
            formatRow(row, getInfos()).forEach(c -> writeCell(c != null ? c.toString() : ""));
            writeIndent(2);
            writer.writeEndElement();
            writer.writeCharacters(System.lineSeparator());
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

    }

    private void writeIndent(int depth) throws XMLStreamException {
        writeIndent(depth, "  ");
    }

    private void writeIndent(int depth, String indent) throws XMLStreamException {
        for (int i = 0; i < depth; i++) {
            if (spaces) {
                writer.writeCharacters(indent);
            }
        }
    }

    private void writeCell(String cell) {

        try {
            writeIndent(3);
            writer.writeStartElement(CELL_TAG);
            writer.writeCharacters(cell);
            writer.writeEndElement();
            writer.writeCharacters(System.lineSeparator());
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        if (writer != null)              {
            try {
                writer.writeEndElement();
                writer.writeCharacters(System.lineSeparator());
                writer.writeEndDocument();
                writer.close();
                outputStream.close();
            } catch (XMLStreamException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public File getFile() {
        return file;
    }

    public boolean isSpaces() {
        return spaces;
    }

    public void setSpaces(boolean spaces) {
        this.spaces = spaces;
    }

    public List<C> getInfos() {
        return infos;
    }

    @Override
    public void setInfos(List<C> infos) {
        this.infos = infos;
    }
}
