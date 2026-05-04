# Mock API Framework

A serverless, config-driven mock API framework built on AWS. Teams define mock scenarios in YAML — response payloads, latency distributions, error rates — and get a real HTTPS endpoint back. No code changes needed to add or modify mock behavior.

**Live demo:** `https://rcvanri09j.execute-api.us-east-1.amazonaws.com/prod`

```bash
# Default response
curl https://rcvanri09j.execute-api.us-east-1.amazonaws.com/prod/orders/123

# Force a timeout scenario
curl -H "x-mock-scenario: timeout" \
  https://rcvanri09j.execute-api.us-east-1.amazonaws.com/prod/orders/123
```

---

## Why this exists

Teams building microservices spend too much time waiting for dependent services to be ready. This framework lets any team mock an API dependency in minutes — with realistic latency, configurable error rates, and scenario overrides for QA testing — without spinning up a real service.

---

## Architecture

```
Request
  │
  ▼
API Gateway (REST)
  │
  ▼
Lambda (Spring Boot)
  │
  ├── ScenarioService  → DynamoDB (route + scenario lookup)
  ├── LatencyInjector  → Thread.sleep(p50..p99 ms)
  └── FixtureFetcher   → S3 (JSON response payload)
```

| Component    | Technology              | Purpose                          |
|-------------|-------------------------|----------------------------------|
| API Gateway | AWS REST API            | Exposes HTTPS endpoints          |
| Lambda      | Spring Boot + Java 21   | Request handler                  |
| DynamoDB    | AWS DynamoDB            | Scenario config store            |
| S3          | AWS S3                  | Fixture payload store            |
| IaC         | AWS SAM                 | Infrastructure as code           |

---

## Features

| Feature | Description |
|--------|-------------|
| **Path parameter matching** | `GET /orders/{id}` matches any order ID |
| **Scenario override** | Force any scenario via `x-mock-scenario` header |
| **Latency simulation** | Configurable p50/p99 latency per scenario |
| **Error injection** | Percentage-based random fault injection |
| **Config-driven** | All behavior defined in YAML, no code changes |
| **Local dev** | Run locally with SAM + DynamoDB Local |

---

## Project Structure

```
mock-framework/
├── template.yaml                          # SAM infrastructure definition
├── resolver-lambda/                       # Spring Boot Lambda
│   └── src/main/java/com/mock/resolver/
│       ├── ResolverHandler.java           # Lambda entry point
│       ├── ScenarioService.java           # Route + scenario matching
│       ├── LatencyInjector.java           # Latency simulation
│       ├── FixtureFetcher.java            # S3 fixture loader
│       ├── AwsConfig.java                 # AWS SDK Spring beans
│       └── model/MockScenario.java        # DynamoDB entity
└── use-cases/                             # Mock scenario configs
    └── orders/
        ├── get-order.yaml                 # Route definitions
        └── fixtures/
            ├── order_200.json             # Happy path response
            └── order_404.json             # Not found response
```

---

## How it works

### 1. Define a use case in YAML

```yaml
use_cases:
  - id: get_order_success
    route_method: GET
    route_path: /orders/{id}
    scenario: happy_path
    response:
      status: 200
      latency_ms:
        p50: 40
        p99: 120
      error_rate: 0.0
      fixture: fixtures/order_200.json

  - id: get_order_timeout
    route_method: GET
    route_path: /orders/{id}
    scenario: timeout
    response:
      status: 504
      latency_ms:
        p50: 5000
        p99: 6000
      error_rate: 0.0
      fixture: fixtures/order_504.json
```

### 2. Load into DynamoDB

```bash
aws dynamodb put-item \
  --table-name MockUseCases \
  --item '{
    "use_case_id":    {"S": "get_order_success"},
    "route":          {"S": "GET#/orders/{id}"},
    "scenario_name":  {"S": "happy_path"},
    "status_code":    {"N": "200"},
    "latency_p50_ms": {"N": "40"},
    "latency_p99_ms": {"N": "120"},
    "error_rate":     {"N": "0.0"},
    "fixture_s3_key": {"S": "fixtures/order_200.json"},
    "s3_bucket":      {"S": "mock-fixtures-YOUR_ACCOUNT_ID"},
    "is_default":     {"S": "Y"},
    "is_active":      {"S": "Y"}
  }'
```

### 3. Upload fixture to S3

```bash
aws s3 cp fixtures/order_200.json \
  s3://mock-fixtures-YOUR_ACCOUNT_ID/fixtures/order_200.json
```

### 4. Call the mock

```bash
# Returns happy_path scenario (default)
curl https://YOUR_API_URL/prod/orders/123

# Returns timeout scenario (override)
curl -H "x-mock-scenario: timeout" \
  https://YOUR_API_URL/prod/orders/123
```

---

## Scenario override

Any scenario can be forced using the `x-mock-scenario` request header. This lets QA teams test edge cases against a running service without changing any config.

```bash
# Force a 504 timeout
curl -H "x-mock-scenario: timeout"    https://YOUR_API_URL/prod/orders/123

# Force a 404 not found
curl -H "x-mock-scenario: not_found"  https://YOUR_API_URL/prod/orders/123

# Force a 500 error
curl -H "x-mock-scenario: error"      https://YOUR_API_URL/prod/orders/123
```

---

## Local development

### Prerequisites
- Java 21
- Maven 3.9+
- AWS CLI
- SAM CLI
- Docker

### Run locally

```bash
# 1. Start DynamoDB Local
docker run -p 8000:8000 amazon/dynamodb-local

# 2. Create table
aws dynamodb create-table \
  --table-name MockUseCases \
  --attribute-definitions \
    AttributeName=use_case_id,AttributeType=S \
    AttributeName=route,AttributeType=S \
  --key-schema \
    AttributeName=use_case_id,KeyType=HASH \
    AttributeName=route,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --global-secondary-indexes '[{
    "IndexName": "RouteIndex",
    "KeySchema": [{"AttributeName":"route","KeyType":"HASH"}],
    "Projection": {"ProjectionType":"ALL"}
  }]' \
  --endpoint-url http://localhost:8000

# 3. Seed a scenario
aws dynamodb put-item \
  --table-name MockUseCases \
  --endpoint-url http://localhost:8000 \
  --item '{
    "use_case_id":    {"S": "get_order_success"},
    "route":          {"S": "GET#/orders/{id}"},
    "scenario_name":  {"S": "happy_path"},
    "status_code":    {"N": "200"},
    "latency_p50_ms": {"N": "40"},
    "latency_p99_ms": {"N": "120"},
    "error_rate":     {"N": "0.0"},
    "fixture_s3_key": {"S": "fixtures/order_200.json"},
    "s3_bucket":      {"S": "mock-fixtures-local"},
    "is_default":     {"S": "Y"},
    "is_active":      {"S": "Y"}
  }'

# 4. Build
cd resolver-lambda && mvn package -DskipTests && cd ..

# 5. Start mock API
sam local start-api \
  --parameter-overrides DynamoDbEndpoint=http://host.docker.internal:8000

# 6. Test
curl http://localhost:3000/orders/123
curl -H "x-mock-scenario: timeout" http://localhost:3000/orders/123
```

---

## Deploy to AWS

```bash
# First deploy (guided)
sam deploy --guided

# Subsequent deploys
sam deploy --config-file samconfig.toml
```

SAM creates:
- API Gateway (REST API)
- Lambda function (Spring Boot, Java 21)
- DynamoDB table with GSI
- S3 bucket for fixtures
- IAM role with least-privilege permissions

---

## DynamoDB schema

| Field | Type | Description |
|-------|------|-------------|
| `use_case_id` | String (PK) | Unique scenario ID |
| `route` | String (SK) | `METHOD#/path/{param}` |
| `scenario_name` | String | Scenario label |
| `status_code` | Number | HTTP response status |
| `latency_p50_ms` | Number | Median latency (ms) |
| `latency_p99_ms` | Number | p99 latency (ms) |
| `error_rate` | Number | Fault injection rate (0.0–1.0) |
| `fixture_s3_key` | String | S3 key for response payload |
| `s3_bucket` | String | S3 bucket name |
| `is_default` | String | `Y` = default scenario for route |
| `is_active` | String | `Y` = active, `N` = disabled |

---

## Roadmap

- [ ] Config sync script (YAML → DynamoDB automatically)
- [ ] Header/query/body matching
- [ ] Stateful scenarios (first call PENDING, second call SUCCESS)
- [ ] TPS throttling with DynamoDB atomic counters
- [ ] PII masking in responses
- [ ] Management dashboard UI
- [ ] k6 load test examples
- [ ] OpenAPI spec import

---

## Tech stack

- **Java 21** — Lambda runtime
- **Spring Boot 3.2** — Dependency injection, configuration
- **AWS Lambda** — Serverless compute
- **AWS API Gateway** — HTTP routing
- **AWS DynamoDB** — Scenario config store
- **AWS S3** — Fixture payload store
- **AWS SAM** — Infrastructure as code + local dev
