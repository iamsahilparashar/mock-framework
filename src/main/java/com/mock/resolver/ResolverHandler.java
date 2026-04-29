package com.mock.resolver;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.mock.resolver.model.MockScenario;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

public class ResolverHandler
    implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

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
    public APIGatewayV2HTTPResponse handleRequest(
            APIGatewayV2HTTPEvent event, Context lambdaContext) {

        APIGatewayV2HTTPEvent.RequestContext.Http http = event.getRequestContext().getHttp();
        String method = http.getMethod();
        String path   = http.getPath();

        lambdaContext.getLogger().log("Resolving: " + method + " " + path);

        MockScenario scenario = scenarioService.resolve(method, path);
        if (scenario == null) {
            return response(404, "{\"error\":\"no mock configured for this route\"}");
        }

        if (scenarioService.shouldInjectError(scenario.getErrorRate())) {
            return response(500, "{\"error\":\"injected fault\"}");
        }

        latencyInjector.apply(scenario.getLatencyP50Ms(), scenario.getLatencyP99Ms());

        String payload = fixtureFetcher.fetch(scenario.getS3Bucket(), scenario.getFixtureS3Key());

        return response(scenario.getStatusCode(), payload);
    }

    private APIGatewayV2HTTPResponse response(int status, String body) {
        return APIGatewayV2HTTPResponse.builder()
            .withStatusCode(status)
            .withHeaders(Map.of("Content-Type", "application/json"))
            .withBody(body)
            .build();
    }
}
