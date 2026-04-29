package com.mock.resolver;

import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class LatencyInjector {

    private final Random random = new Random();

    public void apply(int p50Ms, int p99Ms) {
        if (p50Ms <= 0) return;
        int latency = random.nextDouble() < 0.5
            ? p50Ms
            : p50Ms + random.nextInt(Math.max(1, p99Ms - p50Ms));
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
