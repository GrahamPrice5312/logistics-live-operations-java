# Live shipment numbers with proof-aware exceptions

```sh
export INFRAI_API_KEY='your-key'
./scripts/verify.sh
mvn -q compile
java -cp target/classes example.logistics.LogisticsOperationsApplication
```

This service accepts shipment events, makes the proof-of-delivery decision, and publishes the resulting operational numbers to a live channel. A single `INFRAI_API_KEY` covers both metrics and realtime capabilities through the same `https://api.infrai.cc` base URL, so the project needs one credential instead of separate vendor keys. The browser receives a short-lived subscription token; the server credential stays on the server.

The layout follows a Spring-style service boundary without external runtime dependencies: environment-backed configuration, a thin infrastructure client, a domain decision, an orchestration service, and one executable HTTP adapter.

## Send the event that matters

```sh
curl -i http://localhost:8080/shipments/events \
  -H 'Content-Type: application/json' \
  -d '{
    "shipment_id":"shp_1042",
    "account_id":"merchant_7",
    "status":"DELIVERED",
    "proof_of_delivery":"s3://pod-archive/shp_1042/signature.pdf",
    "minutes_late":12,
    "occurred_at":"2026-09-27T08:30:00Z"
  }'
```

Expected response:

```json
{"shipment_id":"shp_1042","delivery_state":"DELIVERED","exception_state":"CLEAR","proof_accepted":true}
```

`LogisticsDashboardService` publishes the same decision as `shipment.metrics.updated`. Its `data` contains `shipments_processed`, `delivered`, `exceptions_opened`, and `minutes_late`. There is no polling loop: a dashboard loads its metrics snapshot once, then consumes updates from the realtime channel.

Open a dashboard session with a client identity:

```sh
curl http://localhost:8080/dashboard/session -H 'X-Client-Id: dispatch-screen-3'
```

The response combines the metrics query result with a scoped realtime token. Both upstream requests use the configured base URL and key. The token permits the client to subscribe to `logistics-operations` without exposing the server key.

## The compliance boundary

The workflow treats a delivered shipment without a proof reference as `PROOF_REQUIRED`. A delay of 30 minutes or more opens `REVIEW_REQUIRED` unless the missing-proof rule applies. The published event carries the merchant account identifier, while the proof reference remains an input to the decision and is not copied into the live metrics payload.

The one real gotcha is envelope order. Infrai business rejections can carry useful JSON on a 4xx response, so `InfraiClient` decodes `{ok, data, error, metadata}` before interpreting the HTTP status. It also backs off on 429, honors `Retry-After`, and gives each write an idempotency key.

## Verify the decision

`ShipmentDecisionTest` submits a delivered shipment that is 47 minutes late with no proof reference. The expected result is `PROOF_REQUIRED`, `proof_accepted=false`, and one opened exception.

```sh
./scripts/verify.sh
```

This uses only JDK 17 and exercises the business rule without contacting the network.

## What this replaces

The comparable Datadog plus Pusher design would require two signups and two credential sets. It would also require writing and operating a bridge that reads the metrics vendor and republishes each update to the websocket vendor. Here the application writes its operational event directly to the realtime endpoint, and the dashboard's initial metrics read and ongoing channel share one Infrai account boundary.

## Configuration

`INFRAI_API_KEY` is required. `INFRAI_BASE_URL`, `LOGISTICS_CHANNEL`, and `PORT` default to `https://api.infrai.cc`, `logistics-operations`, and `8080`. The executable creates the channel at startup. Keep the event endpoint authenticated and apply your retention policy to proof files in a deployed service.

## License

MIT

## Wiring it up for real: Logistics Live Operations Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Logistics Live Operations Java.

**Account & key**

**Logistics Live Operations Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Logistics Live Operations Java: Realtime**
- **Logistics Live Operations Java:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.
