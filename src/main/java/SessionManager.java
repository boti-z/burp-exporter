import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SessionManager {
    private final MontoyaApi api;
    private final List<CapturedRequest> capturedRequests;
    private final List<SessionListener> listeners;
    private int requestCounter;

    public SessionManager(MontoyaApi api) {
        this.api = api;
        this.capturedRequests = new ArrayList<>();
        this.listeners = new ArrayList<>();
        this.requestCounter = 0;
    }

    public void addRequest(HttpRequest request, HttpResponse response) {
        requestCounter++;
        CapturedRequest captured = new CapturedRequest(requestCounter, request, response);
        capturedRequests.add(captured);
        notifyListeners();
        api.logging().logToOutput(String.format("Added request #%d: %s %s",
            requestCounter, request.method(), request.url()));
    }

    public List<CapturedRequest> getCapturedRequests() {
        return new ArrayList<>(capturedRequests);
    }

    public int getCount() {
        return capturedRequests.size();
    }

    public void clearSession() {
        capturedRequests.clear();
        requestCounter = 0;
        notifyListeners();
        api.logging().logToOutput("Session cleared");
    }

    public void removeRequest(int index) {
        if (index >= 0 && index < capturedRequests.size()) {
            capturedRequests.remove(index);
            // Renumber remaining requests
            requestCounter = 0;
            for (CapturedRequest req : capturedRequests) {
                requestCounter++;
            }
            notifyListeners();
        }
    }

    public boolean exportSession(String baseDirectory) {
        if (capturedRequests.isEmpty()) {
            api.logging().logToError("No requests to export");
            return false;
        }

        // Create session directory with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String sessionDir = baseDirectory + File.separator + "session_" + timestamp;
        File dir = new File(sessionDir);

        if (!dir.mkdirs()) {
            api.logging().logToError("Failed to create directory: " + sessionDir);
            return false;
        }

        try {
            // Export each request-response pair
            for (CapturedRequest captured : capturedRequests) {
                String filePrefix = String.format("%03d", captured.getOrder());

                // Write request
                writeToFile(sessionDir + File.separator + filePrefix + ".request",
                    captured.getRequest().toByteArray().getBytes());

                // Write response if available
                if (captured.hasResponse()) {
                    writeToFile(sessionDir + File.separator + filePrefix + ".response",
                        captured.getResponse().toByteArray().getBytes());
                } else {
                    // Create empty response file or marker
                    writeToFile(sessionDir + File.separator + filePrefix + ".response",
                        "No response captured".getBytes(StandardCharsets.UTF_8));
                }
            }

            // Write metadata file
            writeMetadata(sessionDir, timestamp);

            api.logging().logToOutput(String.format("Session exported successfully to: %s", sessionDir));
            api.logging().logToOutput(String.format("Exported %d request-response pairs", capturedRequests.size()));
            return true;

        } catch (IOException e) {
            api.logging().logToError("Export failed: " + e.getMessage());
            return false;
        }
    }

    private void writeToFile(String filePath, byte[] data) throws IOException {
        try (var fos = new java.io.FileOutputStream(filePath)) {
            fos.write(data);
        }
    }

    private void writeMetadata(String sessionDir, String timestamp) throws IOException {
        StringBuilder metadata = new StringBuilder();
        metadata.append("{\n");
        metadata.append(String.format("  \"session_timestamp\": \"%s\",\n", timestamp));
        metadata.append(String.format("  \"request_count\": %d,\n", capturedRequests.size()));
        metadata.append("  \"requests\": [\n");

        for (int i = 0; i < capturedRequests.size(); i++) {
            CapturedRequest req = capturedRequests.get(i);
            metadata.append("    {\n");
            metadata.append(String.format("      \"order\": %d,\n", req.getOrder()));
            metadata.append(String.format("      \"method\": \"%s\",\n", req.getMethod()));
            metadata.append(String.format("      \"url\": \"%s\",\n", escapeJson(req.getUrl())));
            metadata.append(String.format("      \"timestamp\": \"%s\",\n", req.getTimestamp().toString()));
            metadata.append(String.format("      \"has_response\": %s\n", req.hasResponse()));
            metadata.append(i < capturedRequests.size() - 1 ? "    },\n" : "    }\n");
        }

        metadata.append("  ]\n");
        metadata.append("}\n");

        writeToFile(sessionDir + File.separator + "metadata.json",
            metadata.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    public void addListener(SessionListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (SessionListener listener : listeners) {
            listener.onSessionChanged();
        }
    }

    public interface SessionListener {
        void onSessionChanged();
    }
}
