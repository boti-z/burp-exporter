import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.time.Instant;

public class CapturedRequest {
    private final int order;
    private final HttpRequest request;
    private final HttpResponse response;
    private final Instant timestamp;
    private final String url;

    public CapturedRequest(int order, HttpRequest request, HttpResponse response) {
        this.order = order;
        this.request = request;
        this.response = response;
        this.timestamp = Instant.now();
        this.url = request.url();
    }

    public int getOrder() {
        return order;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return request.method();
    }

    public boolean hasResponse() {
        return response != null;
    }
}
