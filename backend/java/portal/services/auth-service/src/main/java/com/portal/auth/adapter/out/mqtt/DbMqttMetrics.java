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

    public static String asPrometheus() {
        long calls = totalCalls.get();
        long latencyTotal = totalLatencyMs.get();
        long avg = calls == 0 ? 0 : latencyTotal / calls;

        return "# HELP db_mqtt_total_calls Total db_mqtt RPC calls\n" +
                "# TYPE db_mqtt_total_calls counter\n" +
                "db_mqtt_total_calls " + calls + "\n" +
                "# HELP db_mqtt_total_success Total successful db_mqtt RPC calls\n" +
                "# TYPE db_mqtt_total_success counter\n" +
                "db_mqtt_total_success " + totalSuccess.get() + "\n" +
                "# HELP db_mqtt_total_errors Total failed db_mqtt RPC calls\n" +
                "# TYPE db_mqtt_total_errors counter\n" +
                "db_mqtt_total_errors " + totalErrors.get() + "\n" +
                "# HELP db_mqtt_total_retries Total retries used by db_mqtt RPC calls\n" +
                "# TYPE db_mqtt_total_retries counter\n" +
                "db_mqtt_total_retries " + totalRetries.get() + "\n" +
                "# HELP db_mqtt_avg_latency_ms Average latency of db_mqtt RPC calls in ms\n" +
                "# TYPE db_mqtt_avg_latency_ms gauge\n" +
                "db_mqtt_avg_latency_ms " + avg + "\n" +
                "# HELP db_mqtt_max_latency_ms Maximum latency of db_mqtt RPC calls in ms\n" +
                "# TYPE db_mqtt_max_latency_ms gauge\n" +
                "db_mqtt_max_latency_ms " + maxLatencyMs.get() + "\n";
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
