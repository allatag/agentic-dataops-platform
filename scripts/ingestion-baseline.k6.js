import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

const tenants = ["tenant-a", "tenant-b", "tenant-c"];
const sources = ["payment-service", "checkout-api", "inventory-worker", "fraud-detector"];
const eventTypes = ["LATENCY_SPIKE", "ERROR_RATE_INCREASE", "QUEUE_BACKLOG", "CPU_SATURATION"];
const severities = ["LOW", "MEDIUM", "HIGH"];

export const options = {
  scenarios: {
    warmup: {
      executor: "constant-arrival-rate",
      rate: 1,
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 2,
      maxVUs: 5,
      tags: { phase: "warmup" },
    },
    baseline: {
      executor: "constant-arrival-rate",
      rate: 5,
      timeUnit: "1s",
      duration: "2m",
      startTime: "30s",
      preAllocatedVUs: 10,
      maxVUs: 20,
      tags: { phase: "baseline" },
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
  },
};

function pick(values, offset = 0) {
  return values[(__VU + __ITER + offset) % values.length];
}

export default function () {
  const tenantId = pick(tenants);
  const source = pick(sources, 1);
  const eventType = pick(eventTypes, 2);
  const severity = pick(severities, 3);
  const uniqueSuffix = `${Date.now()}-${__VU}-${__ITER}`;

  const payload = {
    tenantId,
    source,
    eventType,
    severity,
    message: `${eventType} observed for ${source} in ${tenantId} during baseline run ${uniqueSuffix}`,
  };

  const response = http.post(`${BASE_URL}/api/events`, JSON.stringify(payload), {
    headers: {
      "Content-Type": "application/json",
    },
    tags: {
      endpoint: "POST /api/events",
      tenant: tenantId,
      source,
      event_type: eventType,
    },
  });

  check(response, {
    "POST /api/events returned 202": (res) => res.status === 202,
  });
}
