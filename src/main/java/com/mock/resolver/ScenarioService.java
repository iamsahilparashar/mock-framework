package com.mock.resolver;

import com.mock.resolver.model.MockScenario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

@Service
public class ScenarioService {

    private final DynamoDbTable<MockScenario> table;

    public ScenarioService(
            DynamoDbEnhancedClient client,
            @Value("${DYNAMODB_TABLE:MockUseCases}") String tableName) {
        this.table = client.table(tableName, TableSchema.fromBean(MockScenario.class));
    }

    public MockScenario resolve(String method, String path) {
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

    public boolean shouldInjectError(double errorRate) {
        return Math.random() < errorRate;
    }
}
