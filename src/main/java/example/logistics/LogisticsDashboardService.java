package example.logistics;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LogisticsDashboardService {
    private final ShipmentDecision decision;
    private final InfraiClient infrai;

    public LogisticsDashboardService(ShipmentDecision decision, InfraiClient infrai) {
        this.decision = decision;
        this.infrai = infrai;
    }

    public ShipmentDecision.Outcome record(ShipmentDecision.ShipmentEvent event)
            throws IOException, InterruptedException {
        ShipmentDecision.Outcome outcome = decision.evaluate(event);
        Map<String, Object> liveUpdate = new LinkedHashMap<>(outcome.operationalNumbers());
        liveUpdate.put("shipment_id", outcome.shipmentId());
        liveUpdate.put("delivery_state", outcome.deliveryState());
        liveUpdate.put("exception_state", outcome.exceptionState());
        liveUpdate.put("proof_accepted", outcome.proofAccepted());
        infrai.publish("shipment.metrics.updated", liveUpdate, event.accountId(),
                "shipment-" + event.shipmentId() + "-" + event.occurredAt().toEpochMilli());
        return outcome;
    }

    public Map<String, Object> openDashboard(String clientId) throws IOException, InterruptedException {
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("metrics", infrai.queryMetrics());
        handoff.put("realtime_token", infrai.issueDashboardToken(clientId));
        return handoff;
    }
}
