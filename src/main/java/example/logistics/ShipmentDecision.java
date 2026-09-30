package example.logistics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShipmentDecision {
    public record ShipmentEvent(String shipmentId, String accountId, String status,
                                String proofOfDelivery, int minutesLate, Instant occurredAt) {}

    public record Outcome(String shipmentId, String deliveryState, String exceptionState,
                          boolean proofAccepted, Map<String, Object> operationalNumbers) {}

    public Outcome evaluate(ShipmentEvent event) {
        boolean delivered = "DELIVERED".equals(event.status());
        boolean proofAccepted = delivered && event.proofOfDelivery() != null
                && !event.proofOfDelivery().isBlank();
        String exceptionState = event.minutesLate() >= 30 ? "REVIEW_REQUIRED" : "CLEAR";
        if (delivered && !proofAccepted) exceptionState = "PROOF_REQUIRED";

        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("shipments_processed", 1);
        numbers.put("delivered", delivered ? 1 : 0);
        numbers.put("exceptions_opened", "CLEAR".equals(exceptionState) ? 0 : 1);
        numbers.put("minutes_late", event.minutesLate());
        numbers.put("occurred_at", event.occurredAt().toString());
        return new Outcome(event.shipmentId(), delivered ? "DELIVERED" : "IN_TRANSIT",
                exceptionState, proofAccepted, Map.copyOf(numbers));
    }
}
