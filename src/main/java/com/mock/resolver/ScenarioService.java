package com.mock.resolver;

import com.mock.resolver.model.MockScenario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ScenarioService {

    private final DynamoDbClient             dynamoDbClient;
    private final DynamoDbTable<MockScenario> table;
    private final String                     tableName;

    public ScenarioService(
        DynamoDbClient dynamoDbClient,
        DynamoDbEnhancedClient enhancedClient,
        @Value("${DYNAMODB_TABLE:MockUseCases}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName      = tableName;
        this.table          = enhancedClient.table(tableName, TableSchema.fromBean(MockScenario.class));
    }

    /**
     * Resolution order:
     *  1. Override header present → find that specific scenario name for this route
     *  2. No override             → find the default scenario (is_default = Y)
     *  3. Fallback                → any active scenario for this route
     */
    public MockScenario resolve(String method, String path, String scenarioOverride) {

        if (scenarioOverride != null && !scenarioOverride.isBlank()) {
            // Explicit scenario override via x-mock-scenario header
            MockScenario overridden = scanForScenario(method, path, scenarioOverride, false);
            if (overridden != null) return overridden;
        }

        // No override — prefer the default scenario
        MockScenario defaultScenario = scanForScenario(method, path, null, true);
        if (defaultScenario != null) return defaultScenario;

        // Last resort — any active matching scenario
        return scanForScenario(method, path, null, false);
    }

    /**
     * Scans DynamoDB for a route matching method+path with optional filters.
     *
     * @param scenarioName  if non-null, filter by scenario_name = this value
     * @param defaultOnly   if true, filter by is_default = Y
     */
    private MockScenario scanForScenario(
        String method, String path,
        String scenarioName, boolean defaultOnly) {

        StringBuilder filterExpr = new StringBuilder("is_active = :active");
        Map<String, AttributeValue> exprValues = new java.util.HashMap<>();
        exprValues.put(":active", AttributeValue.fromS("Y"));

        if (scenarioName != null) {
            filterExpr.append(" AND scenario_name = :scenario");
            exprValues.put(":scenario", AttributeValue.fromS(scenarioName));
        }

        if (defaultOnly) {
            filterExpr.append(" AND is_default = :def");
            exprValues.put(":def", AttributeValue.fromS("Y"));
        }

        ScanRequest scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression(filterExpr.toString())
            .expressionAttributeValues(exprValues)
            .build();

        ScanResponse response = dynamoDbClient.scan(scanRequest);

        for (Map<String, AttributeValue> item : response.items()) {
            String route = item.get("route").s();
            if (!route.startsWith(method + "#")) continue;

            String templatePath = route.substring(method.length() + 1);

            if (templatePath.equals(path) || pathMatches(templatePath, path)) {
                return toScenario(item);
            }
        }
        return null;
    }

    // ── Path pattern matching ─────────────────────────────────────────────────
    // "/orders/{id}" → regex "/orders/[^/]+" → matches "/orders/123"

    private boolean pathMatches(String template, String realPath) {
        String regex = template
            .replaceAll("\\{[^/]+\\}", "[^/]+")
            .replace("/", "\\/");
        return Pattern.matches(regex, realPath);
    }

    // ── DynamoDB item → MockScenario ──────────────────────────────────────────

    private MockScenario toScenario(Map<String, AttributeValue> item) {
        MockScenario s = new MockScenario();
        s.setUseCaseId   (strVal(item, "use_case_id"));
        s.setRoute       (strVal(item, "route"));
        s.setScenarioName(strVal(item, "scenario_name"));
        s.setStatusCode  (intVal(item, "status_code"));
        s.setLatencyP50Ms(intVal(item, "latency_p50_ms"));
        s.setLatencyP99Ms(intVal(item, "latency_p99_ms"));
        s.setErrorRate   (dblVal(item, "error_rate"));
        s.setFixtureS3Key(strVal(item, "fixture_s3_key"));
        s.setS3Bucket    (strVal(item, "s3_bucket"));
        s.setIsActive    (strVal(item, "is_active"));
        return s;
    }

    private String strVal(Map<String, AttributeValue> m, String k) {
        return m.containsKey(k) && m.get(k).s() != null ? m.get(k).s() : null;
    }
    private int intVal(Map<String, AttributeValue> m, String k) {
        return m.containsKey(k) && m.get(k).n() != null ? Integer.parseInt(m.get(k).n()) : 0;
    }
    private double dblVal(Map<String, AttributeValue> m, String k) {
        return m.containsKey(k) && m.get(k).n() != null ? Double.parseDouble(m.get(k).n()) : 0.0;
    }

    public boolean shouldInjectError(double errorRate) {
        return Math.random() < errorRate;
    }
}