package com.portal.auth.adapter.out.mqtt;

import java.util.concurrent.atomic.AtomicLong;

public final class DbMqttMetrics {
    private static final AtomicLong totalCalls = new AtomicLong();
    private static final AtomicLong totalSuccess = new AtomicLong();
    private static final AtomicLong totalErrors = new AtomicLong();
    private static final AtomicLong totalRetries = new AtomicLong();
    private static final AtomicLong totalLatencyMs = new AtomicLong();
    private static final AtomicLong maxLatencyMs = new AtomicLong();

    private DbMqttMetrics() {
    }

    public static void recordSuccess(long latencyMs, int retriesUsed) {
        totalCalls.incrementAndGet();
        totalSuccess.incrementAndGet();
        totalRetries.addAndGet(Math.max(0, retriesUsed));
        totalLatencyMs.addAndGet(Math.max(0L, latencyMs));
        updateMax(latencyMs);
    }

    public static void recordError(long latencyMs, int retriesUsed) {
        totalCalls.incrementAndGet();
        totalErrors.incrementAndGet();
        totalRetries.addAndGet(Math.max(0, retriesUsed));
        totalLatencyMs.addAndGet(Math.max(0L, latencyMs));
        updateMax(latencyMs);
    }

    public static String asJson() {
        long calls = totalCalls.get();
        long latencyTotal = totalLatencyMs.get();
        long avg = calls == 0 ? 0 : latencyTotal / calls;

        return "{" +
                "\"component\":\"db_mqtt\"," +
                "\"totalCalls\":" + calls + "," +
                "\"totalSuccess\":" + totalSuccess.get() + "," +
                "\"totalErrors\":" + totalErrors.get() + "," +
                "\"totalRetries\":" + totalRetries.get() + "," +
                "\"avgLatencyMs\":" + avg + "," +
                "\"maxLatencyMs\":" + maxLatencyMs.get() +
                "}";
    }

    private static void updateMax(long candidate) {
        long value = Math.max(0L, candidate);
        while (true) {
            long current = maxLatencyMs.get();
            if (value <= current) {
                return;
            }
            if (maxLatencyMs.compareAndSet(current, value)) {
                return;
            }
        }
    }
}
