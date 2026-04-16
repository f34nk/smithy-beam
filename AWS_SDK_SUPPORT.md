# AWS SDK Support

This document lists AWS-oriented features from the [Smithy AWS integrations](https://smithy.io/2.0/aws/index.html) specification and how they relate to **generated** Erlang and Elixir clients in smithy-beam.

**Legend:**
- ✅ Supported - Feature is implemented and affects code generation
- ⚠️ Partial - Feature is partially implemented with limitations
- ❌ Not Supported - Feature is not implemented
- ➖ N/A - Feature is not applicable to client code generation

---

## Server Generation

Features of the generated Erlang server (`erlang-server-codegen`). The generated `<svc>_server.erl` module acts as both the Cowboy handler and the Smithy behaviour definition; user code implements only `<svc>_impl.erl`.

| Feature | Status | Notes |
|---------|--------|-------|
| Cowboy 2.x HTTP handler | ✅ | `init/2` generated inside `<svc>_server.erl`; delegates to `handle/3` and replies via `cowboy_req:reply/4`. No separate handler module needed. |
| Request routing | ✅ | `route/2` generated with one clause per operation; `restJson1` routes on HTTP method + URI prefix; `awsJson1.0` routes on `X-Amz-Target` header; catch-all returns `{error, not_found}`. |
| Request deserialization | ✅ | `deserialize_<op>/3` extracts path labels (binary pattern match), header values, and body members; `restJson1` decodes JSON body with `jsx:decode`; `awsJson` passes the full decoded body map as input. |
| Response serialization | ✅ | `serialize_<op>/1` encodes the output map with `jsx:encode`. |
| Behaviour callbacks | ✅ | One `-callback <op>(Input :: <op>_input(), Context :: map()) -> {ok, <op>_output()} \| {error, term()}.` per operation. |
| Impl scaffold | ✅ | `<svc>_impl.erl` generated once (never overwritten); declares `-behaviour(<svc>_server)`, remote-typed `-spec` annotations (`<svc>_server:<type>()`), and `{error, not_implemented}` stubs. |
| Input validation | ✅ | `smithy_validator` runtime module available; generated dispatch functions expose the `validate_<struct>/1` pattern from the server runtime. |
| Error mapping | ✅ | `smithy_error_map` runtime module maps Smithy error atoms/tuples to HTTP status codes; called from `smithy_server:error_response/1`. |
| `restJson1` server | ✅ | Fully implemented — `analyzeServerOperation` in `RestJsonProtocolAnalyzer`; round-trip tested (`weather-service` example). |
| `awsJson1.0` server | ⚠️ | `analyzeServerOperation` implemented in `AwsJsonProtocolAnalyzer`; routing via `X-Amz-Target`; no example yet. |
| `restXml` / `awsQuery` / `ec2Query` server | ❌ | `analyzeServerOperation` not yet implemented for these protocols. |
| Request streaming | ❌ | `@streaming` not implemented. |
| WebSocket / event streams | ❌ | Not implemented. |

---

## Elixir Server Runtime

Runtime modules in `runtime-elixir/server/` that support generated Elixir server dispatchers. `elixir-server-codegen` generates a consolidated `<svc>_server.ex` using `Plug.Router` for routing and dispatching; the impl scaffold is written once to `<scaffoldDir>`. Round-trip tested via `examples/elixir/weather-service`.

| Feature | Status | Notes |
|---------|--------|-------|
| Plug.Router dispatcher | ✅ | `elixir-server-codegen` generates a `<svc>_server.ex` containing `<Svc>.Dispatcher` (Plug.Router with one route block per operation and a catch-all 404), `<Svc>.Handler` (behaviour), and writes `<Svc>.Impl` once as a separate editable file. |
| Request routing | ✅ | One `get`/`post`/… macro block per operation; `match _ do` catch-all returns 404. |
| Request deserialization | ✅ | `deserialize_<op>/1` extracts path params via `conn.path_params` and reads raw body via `Plug.Conn.read_body` + `Jason.decode` for JSON body members. |
| Response serialization | ✅ | `serialize_<op>/1` encodes the output map with `Jason.encode!`. |
| Behaviour callbacks | ✅ | `@callback <op>(input :: map(), ctx :: map()) :: {:ok, map()} \| {:error, term()}` per operation in the `<Svc>.Handler` module. |
| Impl scaffold | ✅ | `<svc>_impl.ex` generated once (never overwritten); declares `@behaviour <Svc>.Handler` and `{:error, :not_implemented}` stubs with correct Elixir dot-notation module names. |
| Plug-compatible request extraction | ✅ | `SmithyServer.extract/1` reads method, path, headers (as a `%{}` map), and body from a `Plug.Conn`. |
| JSON response helpers | ✅ | `SmithyServer.response/3` sets `content-type: application/json`; accepts either a pre-encoded `binary()` or any term (encoded via `Jason.encode!`). |
| Error response | ✅ | `SmithyServer.error_response/2` delegates status code and message lookup to `SmithyErrorMap.to_http/1` and sends a `%{message: …}` JSON body. |
| Validation error response | ✅ | `SmithyServer.validation_error/2` sends a 400 response with the message from `SmithyValidator.format/1`. |
| Not found response | ✅ | `SmithyServer.not_found/1` sends a plain-text 404 response. |
| Input validation | ✅ | `SmithyValidator.validate/2` checks required keys in an input map; returns `:ok` or `{:error, {:missing_required_fields, [term()]}}`. `SmithyValidator.format/1` formats the error as a human-readable string. |
| Error mapping | ✅ | `SmithyErrorMap.to_http/1` maps Smithy error atoms/tuples (`{:not_found, msg}`, `{:conflict, msg}`, `{:unauthorized, msg}`, etc.) to `{http_status_code, message}` pairs; called from `SmithyServer.error_response/2`. |
| `restJson1` server | ✅ | Fully implemented — `analyzeServerOperation` in `RestJsonProtocolAnalyzer`; round-trip tested (`elixir/weather-service` example). |
| Bandit / Plug.Cowboy integration | ⚠️ | Generated dispatcher is a standard `Plug`; wiring to a transport (Bandit, Plug.Cowboy, etc.) is left to the application. |
| Request streaming | ❌ | `@streaming` not implemented. |
| WebSocket / event streams | ❌ | Not implemented. |

---

## Elixir Client Runtime

Runtime modules in `runtime-elixir/client/` that support generated Elixir clients. `elixir-client-codegen` is fully registered and delegates to `ClientPipeline` with `ElixirWriter`.

| Feature | Status | Notes |
|---------|--------|-------|
| Operation execution | ✅ | `SmithyClient.request/3` accepts `(client, %SmithyClient.Operation{}, opts)`, builds the URL, encodes the body with `Jason`, optionally signs with `SmithySigV4`, sends via `Req`, and decodes the JSON or XML response. `client` is the config map returned by `new/1`; `opts` is a plain map (e.g. `%{enable_retry: false}`). |
| Pagination helpers | ✅ | `SmithyClient.stream/3` follows `next_token` automatically, emitting individual items via `Stream.unfold/2`. |
| Retry | ✅ | `SmithyClient.with_retry/2` retries on error up to a configurable limit (default: 3 additional attempts). |
| AWS Signature Version 4 (SigV4) | ✅ | `SmithySigV4.sign_request/2` adds `Authorization` and `X-Amz-Date` headers; `X-Amz-Security-Token` added when a session token is present. Reads credentials from `config.credentials` when nested, or directly from the config. Service name derived from endpoint URL when absent from config. |
| Session token support | ✅ | `X-Amz-Security-Token` header included when `:session_token` is set in credentials. |
| Credential Provider Chain | ✅ | `SmithyCredentials` loads credentials from environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`) or `~/.aws/credentials`. |
| XML encode/decode | ✅ | `SmithyXml` — encode and decode XML for `restXml` and `ec2Query` protocols. Uses Erlang's `:xmerl` at runtime via `elem/2` tuple access (no compile-time header dependency). |
| Query protocol encoding | ✅ | `SmithyQuery` — encodes nested maps into dot-notation form parameters with 1-based list indexing for `awsQuery` and `ec2Query` protocols. |
| S3 URL building | ✅ | `SmithyS3` — builds virtual-hosted and path-style S3 URLs and computes `Content-MD5` headers. |
| HTTP transport | ✅ | `Req` used for all HTTP calls; 2xx responses decoded; non-2xx returned as `{:error, {:http_error, status, body}}`. |
| AWS Signature Version 4A (SigV4A) | ❌ | Multi-region asymmetric signing not implemented. |
| Request streaming | ❌ | `@streaming` not implemented. |
| Waiters | ❌ | `@waitable` not implemented. |

---

## Client SDK Features

General SDK features not specific to AWS traits.

| Feature | Status | Notes |
|---------|--------|-------|
| HTTP Protocol Bindings | ✅ | `@httpLabel` (URI substitution with optional percent-encoding), `@httpQuery` (via `uri_string:compose_query`), `@httpHeader` (request headers and response header extraction), `@httpPayload` (request: raw member value sent as body; response: raw body blob returned under member name), `@httpResponseCode` (response: HTTP status integer placed in result map) — all read into IR and emitted in generated operation functions |
| Input Validation | ✅ | `validate_<struct>/1` helper generated for every struct with at least one `@required` member; returns `ok` or `{error, {missing_required_fields, [binary()]}}` |
| Operations on resource shapes | ❌ | Client includes operations from the full service closure (`TopDownIndex`), not only `service`‑listed operations—required for services like Lambda where most APIs are resource-bound |
| Pagination Helpers | ⚠️ | `@paginated` tokens read into `PaginationSpec` IR; `renderPaginationHelper` emits `<op>_stream/2,3`; wired in pipeline when `op.pagination()` is non-null |
| Retry with Exponential Backoff | ✅ | Every operation gets a 3-arity wrapper that calls `aws_retry:with_retry/2`; retry can be disabled per-call via `#{enable_retry => false}` in the options map |
| Error Handling | ✅ | Error shapes read into `ErrorSpec` IR; a single `parse_error/2` is generated per module, deduplicated by Smithy error name. Dispatch and return format are protocol-specific: REST_XML and AWS_JSON dispatch by error code string and return `{error, #{error_type => atom, message => binary()}}` maps; REST_JSON and AWS_QUERY dispatch by HTTP status code integer and return `{error, {atom, Body}}` tuples |
| HTTP Prefix Headers | ❌ | `@httpPrefixHeaders` trait not implemented (used for S3 metadata) |
| Idempotency Token | ❌ | `@idempotencyToken` trait not implemented |
| Host Label | ❌ | `@hostLabel` trait not implemented |
| Endpoint Override | ❌ | `@endpoint` trait not implemented |
| Request Compression | ❌ | `@requestCompression` trait not implemented |
| Streaming | ❌ | `@streaming` trait not implemented |
| Waiters | ❌ | `@waitable` trait not implemented |

---

## AWS Protocols

Protocol implementations for AWS services. All built-in generators are discovered via **Java `ServiceLoader` SPI** — third-party protocol generators can be registered in any JAR without modifying smithy-beam (see `examples/custom-protocol`).

| Feature | Status | Notes |
|---------|--------|-------|
| [AWS EC2 Query protocol](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html) | ✅ | Fully implemented — `Ec2QueryProtocolAnalyzer` reads `@ec2QueryName` traits at codegen time; top-level overrides stored in `BodySpec.wireNameOverrides()`; nested structure member overrides stored in `BodySpec.nestedWireNameOverrides()` and applied via `aws_query:rename_map_keys/2`; response envelopes stripped by `aws_query:unwrap_response/1`; EC2 `<Response><Errors>` error format dispatched; end-to-end examples (`erlang/ec2-demo`, `elixir/ec2-demo`) |
| [AWS JSON 1.0 protocol](https://smithy.io/2.0/aws/protocols/aws-json-1_0-protocol.html) | ✅ | Fully implemented — `AwsJsonProtocolAnalyzer`, `Content-Type: application/x-amz-json-1.0`, literal `X-Amz-Target` header, `jsx:encode(Input)` body, `__type`-based error dispatch; end-to-end examples (`dynamodb-demo`, `sqs-demo`) |
| [AWS JSON 1.1 protocol](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html) | ✅ | Fully implemented — `AwsJson11ProtocolAnalyzer` with `application/x-amz-json-1.1`; end-to-end examples (`erlang/ssm-demo`, `elixir/ssm-demo`, `firehose-demo`, `kinesis-demo`) |
| [AWS Query protocol](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html) | ✅ | Fully implemented — `AwsQueryProtocolAnalyzer`, `POST /` with form-encoded body, `aws_query:encode/3` with `Action` + `Version`, response envelopes stripped by `aws_query:unwrap_response/1`; end-to-end examples (`iam-demo`, `sns-demo`, `rds-demo`) |
| [AWS restJson1 protocol](https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html) | ✅ | Fully implemented — operation analysis (`RestJsonProtocolAnalyzer`), pipeline wiring, and end-to-end examples (`erlang/weather-service`, `erlang/storage-service`, `erlang/lambda-demo`, `elixir/weather-service`) |
| [AWS restXml protocol](https://smithy.io/2.0/aws/protocols/aws-restxml-protocol.html) | ✅ | Fully implemented — `RestXmlProtocolAnalyzer`, XML body encoding, `aws_xml.erl` / `SmithyXml` runtime, `aws_s3.erl` / `SmithyS3` for S3 services (URL building), XML error code string dispatch; output shape analyzed for `@httpPayload` (raw blob body returned under member name), `@httpHeader` (response headers merged into decoded body map), and `@httpResponseCode` bindings; end-to-end examples (`erlang/s3-demo`, `elixir/s3-demo`) |
| Custom protocols via `@protocolDefinition` | ❌ | Detect `@protocolDefinition` traits and resolve generators via Java `ServiceLoader`; fall back to a stub when none is registered |
| [HTTP Protocol Compliance Tests](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html) | ❌ | Emit language-appropriate tests from `@httpRequestTests` / `@httpResponseTests` |

---

## XML Binding Traits

XML serialization traits for REST-XML and other XML-based protocols.

| Feature | Status | Notes |
|---------|--------|-------|
| [`@xmlName`](https://smithy.io/2.0/spec/protocol-traits.html#xmlname-trait) | ⚠️ | Partially implemented for `ec2Query`: top-level input member renames and one level of nested structure member renames are read by `Ec2QueryProtocolAnalyzer` and applied at request-encode time via `aws_query:rename_map_keys/2`. Not implemented for `restXml` body encoding or XML response decoding. |
| [`@xmlFlattened`](https://smithy.io/2.0/spec/protocol-traits.html#xmlflattened-trait) | ❌ | List/map flattening not implemented |
| [`@xmlNamespace`](https://smithy.io/2.0/spec/protocol-traits.html#xmlnamespace-trait) | ❌ | XML namespace declarations not implemented |
| [`@xmlAttribute`](https://smithy.io/2.0/spec/protocol-traits.html#xmlattribute-trait) | ❌ | XML attributes not implemented |

---

## AWS Authentication

Authentication mechanisms for AWS services.

| Feature | Status | Notes |
|---------|--------|-------|
| [AWS Signature Version 4 (SigV4)](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4-trait) | ✅ | `@aws.auth#sigv4` detected; signing name read into `AuthSpec` IR; `aws_sigv4.erl` and `aws_credentials.erl` runtime modules copied into output when any operation requires signing; `aws_sigv4:sign_request/5` called inside `make_<op>_request/2` |
| [Credential Provider Chain](https://smithy.io/2.0/aws/aws-auth.html) | ❌ | Environment variables, `~/.aws/credentials`, provider chain; copied only for `@aws.auth#sigv4` services |
| [AWS Signature Version 4A (SigV4A)](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4a-trait) | ❌ | Multi-region asymmetric signing not implemented |
| [Cognito User Pools Authentication](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-cognitouserpools-trait) | ❌ | Cognito authentication not implemented |
| [Unsigned Payload](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-unsignedpayload-trait) | ❌ | Skipping payload signing not implemented |

---

## AWS Core Specification

Core AWS service traits and metadata.

| Feature | Status | Notes |
|---------|--------|-------|
| [Service Trait (`aws.api#service`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-service-trait) | ❌ | Read service metadata from the model (sdkId, endpoint prefix, etc.) |
| [Endpoint Discovery](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientendpointdiscovery-trait) | ❌ | Dynamic endpoint discovery not implemented |
| [HTTP Checksum (`aws.protocols#httpChecksum`)](https://smithy.io/2.0/aws/aws-core.html#aws-protocols-httpchecksum-trait) | ❌ | Request/response checksums not implemented |
| [ARN References (`aws.api#arnReference`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-arnreference-trait) | ➖ | Server-side resource modeling |
| [ARN Templates (`aws.api#arn`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-arn-trait) | ➖ | Server-side resource modeling |
| [Control Plane / Data Plane](https://smithy.io/2.0/aws/aws-core.html#aws-api-controlplane-trait) | ➖ | Service classification metadata |
| [Data Classification (`aws.api#data`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-data-trait) | ➖ | Compliance metadata |
| [Tagging (`aws.api#taggable`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-taggable-trait) | ➖ | Resource tagging metadata |

---

## AWS Endpoint Resolution

Endpoint resolution and regional configuration.

| Feature | Status | Notes |
|---------|--------|-------|
| [Partition Support](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | aws, aws-cn, aws-us-gov partitions |
| [Region Configuration](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Full region support via config |
| [Static Endpoint Resolution](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Uses bundled `endpoints.json` for region lookup |
| [Dual-Stack Endpoints](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-dualstackonlyendpoints-trait) | ❌ | Dual-stack rules not applied; requires endpoint resolution |
| [FIPS Endpoints](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | FIPS rules not applied; requires endpoint resolution |
| [Declarative Endpoint Traits](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Static endpoints.json used instead |
| [Rules-Based Endpoint Resolution](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-rulesbasedendpoints-trait) | ❌ | Dynamic rules engine not implemented |

---

## AWS Service Customizations

Service-specific behaviors and customizations.

### Amazon S3 Customizations

| Feature | Status | Notes |
|---------|--------|-------|
| [S3 Path-Style Bucket Addressing](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-bucket-virtual-hosting) | ❌ | `s3.region.amazonaws.com/bucket` format |
| [S3 Virtual-Hosted Bucket Addressing](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-bucket-virtual-hosting) | ❌ | `bucket.s3.region.amazonaws.com` format |
| [S3 Access Points](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Access point ARN resolution not implemented |
| [S3 Dual-Stack Endpoints](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-dual-stack-endpoints) | ❌ | `.dualstack.` endpoint modifier not implemented |
| [S3 Multi-Region Access Points](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | MRAP not implemented |
| [S3 Multipart Upload](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Multipart upload helpers not implemented |
| [S3 Presigned URLs](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Presigned URL generation not implemented |
| [S3 Transfer Acceleration](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-transfer-acceleration-endpoints) | ❌ | `s3-accelerate.amazonaws.com` not implemented |

### Other Service Customizations

| Feature | Status | Notes |
|---------|--------|-------|
| [Amazon Glacier Customizations](https://smithy.io/2.0/aws/customizations/glacier-customizations.html) | ❌ | Tree hash checksums not implemented |
| [Amazon Machine Learning Customizations](https://smithy.io/2.0/aws/customizations/machinelearning-customizations.html) | ❌ | Predict endpoint resolution not implemented |
| [Amazon API Gateway Customizations](https://smithy.io/2.0/aws/customizations/apigateway-customizations.html) | ➖ | API Gateway deployment configuration |

---

## AWS Rules Engine

Endpoint rules engine for dynamic endpoint resolution.

| Feature | Status | Notes |
|---------|--------|-------|
| [Rules Engine Specification](https://smithy.io/2.0/aws/rules-engine/index.html) | ❌ | Uses static endpoints.json instead |
| [`@endpointRuleSet`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Endpoint rule set trait not processed |
| [`@contextParam`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Context parameter bindings not implemented |
| [`@staticContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Static context parameters not implemented |
| [`@clientContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Client context parameters not implemented |
| [Authentication Scheme Validators](https://smithy.io/2.0/aws/rules-engine/auth-schemes.html) | ❌ | Auth scheme validation not implemented |
| [AWS Rules Engine Built-ins](https://smithy.io/2.0/aws/rules-engine/built-ins.html) | ❌ | Built-in functions not implemented |
| [AWS Rules Engine Library Functions](https://smithy.io/2.0/aws/rules-engine/library-functions.html) | ❌ | Library functions (`aws.partition`, `aws.parseArn`, etc.) not implemented |

---

## Amazon Event Stream

Event streaming for real-time data.

| Feature | Status | Notes |
|---------|--------|-------|
| [Event Headers](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Event header encoding not implemented |
| [Event Payloads](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Event payload handling not implemented |
| [Event Stream Specification](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Binary event stream protocol not implemented |

---

## AWS IAM Traits

IAM policy generation traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [Condition Keys](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation |
| [IAM Action Traits](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation |
| [IAM Resource Traits](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation |

---

## Amazon API Gateway Traits

API Gateway integration traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [API Gateway Authorizers](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment |
| [API Gateway Integrations](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment |
| [Request Validators](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment |

---

## AWS CloudFormation Traits

CloudFormation resource generation traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [CloudFormation Resource Traits](https://smithy.io/2.0/aws/aws-cloudformation.html) | ➖ | CloudFormation resource schema generation |

---

*Structure and categories follow [Smithy 2.0 AWS integrations](https://smithy.io/2.0/aws/index.html). Revise rows when client behavior changes.*
