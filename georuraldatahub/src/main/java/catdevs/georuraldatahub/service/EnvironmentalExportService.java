package catdevs.georuraldatahub.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class EnvironmentalExportService {
    public byte[] workbook(List<List<Object>> rows) {
        if (rows == null || rows.isEmpty() || rows.size() > 5000)
            throw new IllegalArgumentException("Relatorio deve ter entre 1 e 5000 linhas.");
        StringBuilder sheet = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols><col min=\"1\" max=\"4\" width=\"32\" customWidth=\"1\"/></cols><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            var row = rows.get(r);
            if (row == null || row.size() > 10)
                throw new IllegalArgumentException("Linha invalida.");
            sheet.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < row.size(); c++) {
                Object value = row.get(c);
                String ref = "" + (char) ('A' + c) + (r + 1);
                if (value instanceof Number n && Double.isFinite(n.doubleValue()))
                    sheet.append("<c r=\"").append(ref).append("\"><v>").append(n).append("</v></c>");
                else {
                    if (value != null && !(value instanceof String))
                        throw new IllegalArgumentException("Celula invalida.");
                    String text = value == null ? "Nao disponivel" : value.toString();
                    if (text.length() > 32767)
                        throw new IllegalArgumentException("Texto excede o limite da celula Excel.");
                    sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(xml(text)).append("</t></is></c>");
                }
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
            entry(zip, "[Content_Types].xml",
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entry(zip, "_rels/.rels",
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip, "xl/workbook.xml",
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Indicador ambiental\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels",
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            entry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            zip.finish();
            return bytes.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Nao foi possivel gerar Excel.", e);
        }
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String xml(String value) {
        return value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
