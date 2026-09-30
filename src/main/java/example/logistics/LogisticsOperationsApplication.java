package example.logistics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;

public final class LogisticsOperationsApplication {
    private LogisticsOperationsApplication() {}

    public static void main(String[] args) throws Exception {
        ServiceConfig config = ServiceConfig.fromEnvironment();
        InfraiClient infrai = new InfraiClient(config);
        infrai.createChannel("channel-" + config.channel());
        LogisticsDashboardService service = new LogisticsDashboardService(new ShipmentDecision(), infrai);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/shipments/events", exchange -> shipmentEvent(exchange, service));
        server.createContext("/dashboard/session", exchange -> dashboardSession(exchange, service));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Logistics operations service listening on http://localhost:" + config.port());
    }

    private static void shipmentEvent(HttpExchange exchange, LogisticsDashboardService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "POST required")); return; }
        try {
            Map<String, Object> input = Json.object(Json.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            ShipmentDecision.ShipmentEvent event = new ShipmentDecision.ShipmentEvent(
                    required(input, "shipment_id"), required(input, "account_id"), required(input, "status"),
                    (String) input.get("proof_of_delivery"), ((Number) input.getOrDefault("minutes_late", 0)).intValue(),
                    Instant.parse(required(input, "occurred_at")));
            ShipmentDecision.Outcome result = service.record(event);
            respond(exchange, 202, Map.of("shipment_id", result.shipmentId(), "delivery_state", result.deliveryState(),
                    "exception_state", result.exceptionState(), "proof_accepted", result.proofAccepted()));
        } catch (InfraiClient.InfraiException error) {
            int status = error.statusCode() >= 400 && error.statusCode() < 500 ? error.statusCode() : 502;
            respond(exchange, status, Map.of("error", error.code(), "details", error.details()));
        } catch (IllegalArgumentException error) {
            respond(exchange, 400, Map.of("error", "invalid_request", "message", error.getMessage()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            respond(exchange, 503, Map.of("error", "request_interrupted"));
        }
    }

    private static void dashboardSession(HttpExchange exchange, LogisticsDashboardService service) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "GET required")); return; }
        String clientId = exchange.getRequestHeaders().getFirst("X-Client-Id");
        if (clientId == null || clientId.isBlank()) { respond(exchange, 400, Map.of("error", "X-Client-Id required")); return; }
        try {
            respond(exchange, 200, service.openDashboard(clientId));
        } catch (InfraiClient.InfraiException error) {
            int status = error.statusCode() >= 400 && error.statusCode() < 500 ? error.statusCode() : 502;
            respond(exchange, status, Map.of("error", error.code(), "details", error.details()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            respond(exchange, 503, Map.of("error", "request_interrupted"));
        }
    }

    private static String required(Map<String, Object> input, String name) {
        Object value = input.get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " is required");
        return text;
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
