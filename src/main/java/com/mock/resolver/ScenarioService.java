package com.mock.resolver;

import java.util.Map;
import java.util.regex.Pattern;

import com.mock.resolver.model.MockScenario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

@Service
public class ScenarioService {

    private final DynamoDbClient       dynamoDbClient;
    private final DynamoDbTable<MockScenario> table;
    private final String               tableName;

    public ScenarioService(
        DynamoDbClient dynamoDbClient,
        DynamoDbEnhancedClient enhancedClient,
        @Value("${DYNAMODB_TABLE:MockUseCases}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName      = tableName;
        this.table          = enhancedClient.table(tableName, TableSchema.fromBean(MockScenario.class));
    }

    /**
     * Resolves a scenario for the incoming request.
     * 1. Try exact match first:  "GET#/orders/123"
     * 2. Scan all routes for the method and test path-pattern match: "GET#/orders/{id}"
     */
    public MockScenario resolve(String method, String path) {
        // Step 1 — exact match (fast, cheap)
        MockScenario exact = exactMatch(method, path);
        if (exact != null) return exact;

        // Step 2 — pattern match (scan + regex test)
        return patternMatch(method, path);
    }

    // ── Exact match via GSI ───────────────────────────────────────────────────

    private MockScenario exactMatch(String method, String path) {
        String route = method + "#" + path;

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
            .queryConditional(
                QueryConditional.keyEqualTo(
                    Key.builder().partitionValue(route).build()))
            .filterExpression(Expression.builder()
                .expression("is_active = :active")
                .expressionValues(Map.of(
                    ":active", AttributeValue.fromS("Y")))
                .build())
            .limit(1)
            .build();

        return table.index("RouteIndex")
            .query(request)
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst()
            .orElse(null);
    }

    // ── Pattern match ─────────────────────────────────────────────────────────
    // Scans all active routes for this HTTP method and checks if any
    // declared path template matches the real request path.
    // e.g.  template "/orders/{id}"  matches  "/orders/123"

    private MockScenario patternMatch(String method, String path) {
        // Scan the table filtering by is_active = Y
        ScanRequest scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("is_active = :active")
            .expressionAttributeValues(Map.of(
                ":active", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS("Y")))
            .build();

        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

        for (Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item
            : scanResponse.items()) {

            String route = item.get("route").s();   // e.g. "GET#/orders/{id}"
            if (!route.startsWith(method + "#")) continue;

            String templatePath = route.substring(method.length() + 1); // "/orders/{id}"

            if (pathMatches(templatePath, path)) {
                // Convert the raw DynamoDB item → MockScenario bean
                return toScenario(item);
            }
        }
        return null;
    }

    /**
     * Converts a path template like "/orders/{id}/items/{itemId}"
     * into a regex and tests it against the real path.
     */
    private boolean pathMatches(String template, String realPath) {
        // Replace {anything} with a regex segment that matches one path segment
        String regex = template
            .replaceAll("\\{[^/]+\\}", "[^/]+")   // {id} → [^/]+
            .replace("/", "\\/");                  // escape slashes
        return Pattern.matches(regex, realPath);
    }

    /**
     * Manually maps a raw DynamoDB item map to a MockScenario.
     * Needed because we used the low-level scan API, not the enhanced client.
     */
    private MockScenario toScenario(
        Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item) {
        MockScenario s = new MockScenario();
        s.setUseCaseId  (strVal(item, "use_case_id"));
        s.setRoute      (strVal(item, "route"));
        s.setScenarioName(strVal(item, "scenario_name"));
        s.setStatusCode (intVal(item,  "status_code"));
        s.setLatencyP50Ms(intVal(item, "latency_p50_ms"));
        s.setLatencyP99Ms(intVal(item, "latency_p99_ms"));
        s.setErrorRate  (dblVal(item,  "error_rate"));
        s.setFixtureS3Key(strVal(item, "fixture_s3_key"));
        s.setS3Bucket   (strVal(item,  "s3_bucket"));
        s.setIsActive   (strVal(item,  "is_active"));
        return s;
    }

    private String strVal(Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> m, String k) {
        return m.containsKey(k) ? m.get(k).s() : null;
    }
    private int intVal(Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> m, String k) {
        return m.containsKey(k) ? Integer.parseInt(m.get(k).n()) : 0;
    }
    private double dblVal(Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> m, String k) {
        return m.containsKey(k) ? Double.parseDouble(m.get(k).n()) : 0.0;
    }

    public boolean shouldInjectError(double errorRate) {
        return Math.random() < errorRate;
    }
}