package ocean.view.resort.expoert;

import java.util.List;
import java.util.Map;

public interface ExportAdapter {
    String getFormat();
    String getContentType();
    String getFileExtension();
    byte[] export(List<String> headers, List<Map<String, Object>> rows) throws Exception;
}
