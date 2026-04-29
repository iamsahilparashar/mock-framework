package com.mock.resolver.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class MockScenario {

    private String useCaseId;
    private String route;
    private String scenarioName;
    private int    statusCode;
    private int    latencyP50Ms;
    private int    latencyP99Ms;
    private double errorRate;
    private String fixtureS3Key;
    private String s3Bucket;
    private String isActive;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("use_case_id")
    public String getUseCaseId()            { return useCaseId; }
    public void   setUseCaseId(String v)    { this.useCaseId = v; }

    @DynamoDbSortKey
    @DynamoDbSecondaryPartitionKey(indexNames = {"RouteIndex"})
    public String getRoute()                { return route; }
    public void   setRoute(String v)        { this.route = v; }

    @DynamoDbAttribute("scenario_name")
    public String getScenarioName()         { return scenarioName; }
    public void   setScenarioName(String v) { this.scenarioName = v; }

    @DynamoDbAttribute("status_code")
    public int  getStatusCode()             { return statusCode; }
    public void setStatusCode(int v)        { this.statusCode = v; }

    @DynamoDbAttribute("latency_p50_ms")
    public int  getLatencyP50Ms()           { return latencyP50Ms; }
    public void setLatencyP50Ms(int v)      { this.latencyP50Ms = v; }

    @DynamoDbAttribute("latency_p99_ms")
    public int  getLatencyP99Ms()           { return latencyP99Ms; }
    public void setLatencyP99Ms(int v)      { this.latencyP99Ms = v; }

    @DynamoDbAttribute("error_rate")
    public double getErrorRate()            { return errorRate; }
    public void   setErrorRate(double v)    { this.errorRate = v; }

    @DynamoDbAttribute("fixture_s3_key")
    public String getFixtureS3Key()         { return fixtureS3Key; }
    public void   setFixtureS3Key(String v) { this.fixtureS3Key = v; }

    @DynamoDbAttribute("s3_bucket")
    public String getS3Bucket()             { return s3Bucket; }
    public void   setS3Bucket(String v)     { this.s3Bucket = v; }

    @DynamoDbAttribute("is_active")
    public String getIsActive()             { return isActive; }
    public void   setIsActive(String v)     { this.isActive = v; }
}
