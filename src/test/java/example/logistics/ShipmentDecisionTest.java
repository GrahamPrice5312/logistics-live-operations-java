package example.logistics;

import java.time.Instant;

public final class ShipmentDecisionTest {
    public static void main(String[] args) {
        ShipmentDecision decision = new ShipmentDecision();
        ShipmentDecision.Outcome outcome = decision.evaluate(new ShipmentDecision.ShipmentEvent(
                "shp_1042", "merchant_7", "DELIVERED", null, 47,
                Instant.parse("2026-09-27T08:30:00Z")));

        check("DELIVERED".equals(outcome.deliveryState()), "delivery state");
        check("PROOF_REQUIRED".equals(outcome.exceptionState()), "missing proof takes priority");
        check(!outcome.proofAccepted(), "proof acceptance");
        check(Integer.valueOf(1).equals(outcome.operationalNumbers().get("exceptions_opened")), "exception metric");
        System.out.println("ShipmentDecisionTest passed");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
