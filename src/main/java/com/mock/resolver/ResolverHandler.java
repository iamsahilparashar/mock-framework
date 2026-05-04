package com.mock.resolver;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mock.resolver.model.MockScenario;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

public class ResolverHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ConfigurableApplicationContext context;
    private final ScenarioService  scenarioService;
    private final LatencyInjector  latencyInjector;
    private final FixtureFetcher   fixtureFetcher;

    static {
        context = SpringApplication.run(ResolverApplication.class);
    }

    public ResolverHandler() {
        this.scenarioService = context.getBean(ScenarioService.class);
        this.latencyInjector = context.getBean(LatencyInjector.class);
        this.fixtureFetcher  = context.getBean(FixtureFetcher.class);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent event, Context lambdaContext) {

        String method = event.getHttpMethod() != null
            ? event.getHttpMethod().toUpperCase()
            : "GET";
        String path   = event.getPath() != null ? event.getPath() : "/";

        String scenarioOverride = getHeader(event, "x-mock-scenario");

        lambdaContext.getLogger().log(
            "Resolving: " + method + " " + path +
                (scenarioOverride != null ? " [scenario=" + scenarioOverride + "]" : "")
        );

        MockScenario scenario = scenarioService.resolve(method, path, scenarioOverride);

        if (scenario == null) {
            return response(404,
                "{\"error\":\"no mock configured for this route" +
                    (scenarioOverride != null ? " with scenario=" + scenarioOverride : "") +
                    "\"}");
        }

        if (scenarioService.shouldInjectError(scenario.getErrorRate())) {
            return response(500, "{\"error\":\"injected fault\"}");
        }

        latencyInjector.apply(scenario.getLatencyP50Ms(), scenario.getLatencyP99Ms());

        String payload = fixtureFetcher.fetch(scenario.getS3Bucket(), scenario.getFixtureS3Key());

        return response(scenario.getStatusCode(), payload);
    }

    private String getHeader(APIGatewayProxyRequestEvent event, String headerName) {
        Map<String, String> headers = event.getHeaders();
        if (headers == null) return null;
        if (headers.containsKey(headerName)) return headers.get(headerName);
        return headers.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase(headerName))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private APIGatewayProxyResponseEvent response(int status, String body) {
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(status)
            .withHeaders(Map.of("Content-Type", "application/json"))
            .withBody(body);
    }
}