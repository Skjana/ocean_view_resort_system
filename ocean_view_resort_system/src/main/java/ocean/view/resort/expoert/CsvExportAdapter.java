package ocean.view.resort.expoert;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CsvExportAdapter implements ExportAdapter {

    @Override
    public String getFormat() { return "csv"; }

    @Override
    public String getContentType() { return "text/csv; charset=UTF-8"; }

    @Override
    public String getFileExtension() { return "csv"; }

    @Override
    public byte[] export(List<String> headers, List<Map<String, Object>> rows) {
        if (headers == null || headers.isEmpty()) {
            if (rows.isEmpty()) return "".getBytes(StandardCharsets.UTF_8);
            headers = rows.get(0).keySet().stream().sorted().collect(Collectors.toList());
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers.stream().map(this::escape).collect(Collectors.toList()))).append("\n");
        for (Map<String, Object> row : rows) {
            sb.append(headers.stream()
                    .map(h -> escape(row.get(h) != null ? row.get(h).toString() : ""))
                    .collect(Collectors.joining(","))).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String s) {
        if (s == null) return "\"\"";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}