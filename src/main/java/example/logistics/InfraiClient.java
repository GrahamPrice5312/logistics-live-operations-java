package example.logistics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InfraiClient {
    private final HttpClient http;
    private final ServiceConfig config;

    public InfraiClient(ServiceConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiClient(ServiceConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Object createChannel(String operationId) throws IOException, InterruptedException {
        return send("POST", "/v1/realtime/channel/create", Map.of(
                "channel", config.channel(), "type", "public", "vendor", "infrai"), operationId);
    }

    public Object publish(String event, Map<String, Object> data, String accountId, String operationId)
            throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", config.channel());
        body.put("event", event);
        body.put("data", data);
        body.put("account_id", accountId);
        return send("POST", "/v1/realtime/publish", body, operationId);
    }

    public Object queryMetrics() throws IOException, InterruptedException {
        return send("GET", "/v1/metrics/query?name=shipments_processed&agg=sum", null, null);
    }

    public Object issueDashboardToken(String clientId) throws IOException, InterruptedException {
        return send("POST", "/v1/realtime/token/issue", Map.of(
                "client_id", clientId,
                "channels", List.of(config.channel()),
                "capabilities", List.of("subscribe"),
                "ttl_seconds", 900), "token-" + clientId);
    }

    private Object send(String method, String path, Object body, String operationId)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(config.infraiBaseUrl().resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.infraiApiKey())
                    .header("Accept", "application/json");
            if (operationId != null) request.header("Idempotency-Key", operationId);
            if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)));

            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope;
            try {
                envelope = Json.object(Json.parse(response.body()));
            } catch (RuntimeException invalidJson) {
                throw new IOException("Infrai returned an invalid response envelope", invalidJson);
            }
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = Json.object(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.get("code")), error, response.statusCode());
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport status " + response.statusCode());
            return envelope.get("data");
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Duration.ofSeconds(Long.parseLong(value)).toMillis(); }
                    catch (NumberFormatException ignored) { return 250L << attempt; }
                }).orElse(250L << attempt);
    }

    public static final class InfraiException extends IOException {
        private final String code;
        private final Map<String, Object> details;
        private final int statusCode;

        InfraiException(String code, Map<String, Object> details, int statusCode) {
            super(code);
            this.code = code;
            this.details = Map.copyOf(details);
            this.statusCode = statusCode;
        }

        public String code() { return code; }
        public Map<String, Object> details() { return details; }
        public int statusCode() { return statusCode; }
    }
}
