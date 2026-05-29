# AWS SDK Support

This document lists AWS-oriented features from the [Smithy AWS integrations](https://smithy.io/2.0/aws/index.html) specification and how they relate to **generated** Erlang and Elixir code in smithy-beam today.

smithy-beam is a Smithy DirectedCodegen project for the BEAM. AWS service clients are a long-term goal; the current baseline focuses on REST JSON 1 over HTTP bindings with generated codecs, dispatch, routers, and paginator helpers. There is no bundled SigV4 runtime, endpoint rules engine, or AWS Query/JSON/XML protocol stack yet.

For trait-level detail across all Smithy specs, see [TRAITS.md](TRAITS.md).

**Legend:**

- ✅ Supported - Feature is implemented and affects generated code
- ⚠️ Partial - Feature is partially implemented with known limitations
- ❌ Not Supported - Feature is not implemented
- ➖ N/A - Feature is not applicable to current client or server generation

---

## Generated Types Features

Output from `erlang-types-codegen` and `elixir-types-codegen`.

| Feature | Status | Notes |
|---------|--------|-------|
| Scalar, list, and map aliases | ✅ | Named shapes in the service closure become type aliases in one types file per model namespace. |
| Enum and intEnum modules | ✅ | Nested Elixir modules with conversion helpers; Erlang atom unions with unknown wire preservation. |
| Union type aliases | ✅ | Tagged tuple unions with an unknown variant. |
| Structure records and structs | ✅ | Erlang `-record` and `-type`; Elixir nested modules with `@type t` and `defstruct`. |
| Error shape types | ✅ | `@error` structures become typed records (Erlang) or `defexception` modules (Elixir) with fault kind metadata. |
| Shape and member documentation | ✅ | `@documentation` on shapes and members flows into generated type comments. Service docs replace the generic types file header when present. |
| Deprecation filtering | ✅ | `@deprecated` removes shapes from generated output when Smithy-Build `relativeDate` or `relativeVersion` is configured. |
| Sparse collections | ✅ | `@sparse` widens list element and map value types to include `undefined` (Erlang) or `nil` (Elixir). REST JSON codecs encode and decode sparse nulls on the wire. |
| Streaming blob metadata | ⚠️ | `@streaming` affects generated type comments and symbol metadata only; wire streaming is not implemented. |
| Erlang reserved-word escaping | ✅ | Erlang keywords and colliding identifiers are escaped in types output; client and server codecs use the same escaped record names. |
| Service shape rename maps | ⚠️ | Rename targets flow into types output and operation record names in codecs. Nested document members decode as raw maps without nested record literals. |

---

## Generated Client Features

Output from `erlang-client-codegen` and `elixir-client-codegen`.

| Feature | Status | Notes |
|---------|--------|-------|
| Operation stubs | ✅ | One function per operation; REST JSON 1 services wire encode, HTTP dispatch, and decode. Other protocols emit `{error, not_implemented}` stubs. |
| REST JSON 1 request encoding | ✅ | Per-service codec module encodes path labels, query params, headers, and JSON document members into an `http_request` record or map. |
| REST JSON 1 response decoding | ✅ | Codec decodes JSON document, header, and payload bindings into typed output records or structs. |
| HTTP dispatch | ✅ | Erlang uses OTP `httpc` via a generated `<prefix>_http` module. Elixir uses `Req`. Both honor a configurable HTTP client module in client config for tests. |
| Default endpoint in generated config | ❌ | Callers pass `base_url` in the client config map at runtime. No smithy-build endpoint seeding in generated clients. |
| Pagination helpers | ✅ | `@paginated` operations get a generated paginator module that walks output tokens and accumulates item lists. |
| Operation documentation | ✅ | `@documentation` on operations is emitted into generated client function docs. |
| Type and shape documentation | ✅ | Types plugins emit shape and member docs into generated type files alongside operation docs on client stubs. |
| Error shape types | ✅ | `@error` structures become typed records (Erlang) or `defexception` modules (Elixir) with fault kind metadata. |
| Retry | ❌ | No generated retry wrappers or backoff. |
| SigV4 signing | ❌ | No request signing in generated clients. |
| Credential providers | ❌ | No AWS credential chain in generated output. |
| Endpoint discovery | ❌ | Not implemented. |
| Input validation helpers | ❌ | `@required` affects generated types only; no runtime `validate_*` helpers. |
| HTTP prefix headers | ❌ | `@httpPrefixHeaders` not implemented. |
| HTTP response code binding | ✅ | `@httpResponseCode` members populate the modeled output field from the HTTP status on decode. |
| Modeled HTTP errors | ✅ | Client codecs dispatch `@httpError` status codes before type-discriminated errors. Server codecs encode error responses with modeled status codes. |
| Idempotency token | ❌ | `@idempotencyToken` not implemented. |
| Host label | ❌ | `@hostLabel` not implemented. |
| Endpoint override trait | ❌ | `@endpoint` not implemented. |
| Request compression | ❌ | `@requestCompression` not implemented. |
| Streaming | ⚠️ | `@streaming` affects generated type metadata and comments only; wire streaming is not implemented. |
| Waiters | ❌ | `@waitable` not implemented. |

---

## Generated Server Features

Output from `erlang-server-codegen` and `elixir-server-codegen`.

| Feature | Status | Notes |
|---------|--------|-------|
| Handler stubs | ✅ | One handler function per operation with typed input and output; default body returns `{error, not_implemented}`. |
| HTTP router | ✅ | Generated router matches HTTP method and URI template (literal segments and labeled path prefixes) and delegates to handler functions. |
| REST JSON 1 request decoders | ✅ | Per-service codec decodes wire-bound fields (labels, query, headers, JSON body) into input types. |
| REST JSON 1 response encoding | ✅ | Server codec modules encode success and error responses into `http_response` records or maps. |
| Runtime helpers | ✅ | Label parsing helpers shared by router and codec modules. |
| Operation documentation | ✅ | `@documentation` on operations is emitted into generated handler docs. |
| Type and shape documentation | ✅ | Types plugins emit shape and member docs into generated type files alongside operation docs on handler stubs. |
| Transport integration | ❌ | No Cowboy, Bandit, or Plug handler is generated. Applications wire the router to their HTTP stack. |
| Input validation | ❌ | No generated server-side validation helpers. |
| Error to HTTP mapping | ⚠️ | Server codecs encode modeled errors with `@httpError` status codes and JSON bodies. Applications still wire handler results to the codec layer. |
| Request streaming | ❌ | `@streaming` wire handling not implemented. |
| WebSocket / event streams | ❌ | Not implemented. |

---

## AWS Protocols

Protocol selection uses `@protocolDefinition` traits on the service. `BeamProtocolResolver` validates the configured or inferred protocol trait. `BeamProtocolCodegenFactory` currently registers one built-in implementation.

| Feature | Status | Notes |
|---------|--------|-------|
| [AWS restJson1 protocol](https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html) | ⚠️ | Request encoding, client response decoding, server request decoding, server response encoding, routing, and paginators are implemented for Erlang and Elixir. Codecs honor `@jsonName`, `@httpQueryParams`, `@httpResponseCode`, `@httpError`, `@timestampFormat`, and sparse collection nulls. Content type is fixed to `application/json`. Set `"protocol": "aws.protocols#restJson1"` in plugin settings to enable codec emission. |
| [AWS JSON 1.0 protocol](https://smithy.io/2.0/aws/protocols/aws-json-1_0-protocol.html) | ❌ | Not implemented. |
| [AWS JSON 1.1 protocol](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html) | ❌ | Not implemented. |
| [AWS Query protocol](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html) | ❌ | Not implemented. |
| [AWS EC2 Query protocol](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html) | ❌ | Not implemented. |
| [AWS restXml protocol](https://smithy.io/2.0/aws/protocols/aws-restxml-protocol.html) | ❌ | Not implemented. |
| Custom protocols via `@protocolDefinition` | ⚠️ | Protocol traits are discovered and validated at codegen time. `BeamProtocolResolver` walks the service closure and fails with one aggregated diagnostic when shapes are unsupported for the selected protocol (for example streaming blobs or `bigDecimal`). Additional protocols require a new `BeamProtocolCodegen` implementation. |
| [HTTP Protocol Compliance Tests](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html) | ❌ | No test emission from `@httpRequestTests` or `@httpResponseTests`. |

---

## REST JSON 1 HTTP Bindings

Bindings honored in generated REST JSON 1 codecs and routers today.

| Binding | Status | Notes |
|---------|--------|-------|
| `@http` (method and URI) | ✅ | Drives client URI construction and server router clauses. |
| `@httpLabel` | ✅ | URI substitution on the client; labeled path matching and label extraction on the server. |
| `@httpQuery` | ✅ | Query string encoding and decoding. |
| `@httpHeader` | ✅ | Request and response header binding. |
| `@httpPayload` | ✅ | Request and response payload members. |
| `@httpQueryParams` | ✅ | Map members expand into query string key/value pairs on encode and decode. |
| `@httpPrefixHeaders` | ❌ | Not implemented. |
| `@httpResponseCode` | ✅ | Response status populates the bound output member on client decode and server request decode. |
| `@httpError` | ✅ | Status-code clauses in client error dispatch; server error response encoders use modeled HTTP status. |
| `@jsonName` | ✅ | Wire JSON keys follow `@jsonName` when present. |
| `@timestampFormat` | ✅ | Timestamp helpers follow binding location and `@timestampFormat` (epoch seconds or date-time). |
| `@mediaType` | ❌ | JSON requests use a fixed `application/json` content type. |

---

## XML Binding Traits

XML serialization traits for REST-XML and query protocols.

| Feature | Status | Notes |
|---------|--------|-------|
| [`@xmlName`](https://smithy.io/2.0/spec/protocol-traits.html#xmlname-trait) | ❌ | Not implemented. |
| [`@xmlFlattened`](https://smithy.io/2.0/spec/protocol-traits.html#xmlflattened-trait) | ❌ | Not implemented. |
| [`@xmlNamespace`](https://smithy.io/2.0/spec/protocol-traits.html#xmlnamespace-trait) | ❌ | Not implemented. |
| [`@xmlAttribute`](https://smithy.io/2.0/spec/protocol-traits.html#xmlattribute-trait) | ❌ | Not implemented. |

---

## AWS Authentication

Authentication mechanisms for AWS services.

| Feature | Status | Notes |
|---------|--------|-------|
| [AWS Signature Version 4 (SigV4)](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4-trait) | ❌ | `@aws.auth#sigv4` is not read into generated clients. |
| [Credential Provider Chain](https://smithy.io/2.0/aws/aws-auth.html) | ❌ | Not implemented. |
| [AWS Signature Version 4A (SigV4A)](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4a-trait) | ❌ | Not implemented. |
| [Cognito User Pools Authentication](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-cognitouserpools-trait) | ❌ | Not implemented. |
| [Unsigned Payload](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-unsignedpayload-trait) | ❌ | Not implemented. |

---

## AWS Core Specification

Core AWS service traits and metadata.

| Feature | Status | Notes |
|---------|--------|-------|
| [Service Trait (`aws.api#service`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-service-trait) | ❌ | Service metadata (sdkId, endpoint prefix, and similar) is not read into generated config. |
| [Endpoint Discovery](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientendpointdiscovery-trait) | ❌ | Not implemented. |
| [HTTP Checksum (`aws.protocols#httpChecksum`)](https://smithy.io/2.0/aws/aws-core.html#aws-protocols-httpchecksum-trait) | ❌ | Not implemented. |
| [ARN References (`aws.api#arnReference`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-arnreference-trait) | ➖ | Server-side resource modeling metadata. |
| [ARN Templates (`aws.api#arn`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-arn-trait) | ➖ | Server-side resource modeling metadata. |
| [Control Plane / Data Plane](https://smithy.io/2.0/aws/aws-core.html#aws-api-controlplane-trait) | ➖ | Service classification metadata. |
| [Data Classification (`aws.api#data`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-data-trait) | ➖ | Compliance metadata. |
| [Tagging (`aws.api#taggable`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-taggable-trait) | ➖ | Resource tagging metadata. |

---

## AWS Endpoint Resolution

Endpoint resolution and regional configuration.

| Feature | Status | Notes |
|---------|--------|-------|
| [Partition Support](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented. |
| [Region Configuration](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented. |
| [Static Endpoint Resolution](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented. |
| [Dual-Stack Endpoints](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-dualstackonlyendpoints-trait) | ❌ | Not implemented. |
| [FIPS Endpoints](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented. |
| [Declarative Endpoint Traits](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented. |
| [Rules-Based Endpoint Resolution](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-rulesbasedendpoints-trait) | ❌ | Not implemented. |

---

## AWS Service Customizations

Service-specific behaviors and customizations.

### Amazon S3 Customizations

| Feature | Status | Notes |
|---------|--------|-------|
| [S3 Path-Style Bucket Addressing](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-bucket-virtual-hosting) | ❌ | Not implemented. |
| [S3 Virtual-Hosted Bucket Addressing](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-bucket-virtual-hosting) | ❌ | Not implemented. |
| [S3 Access Points](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Not implemented. |
| [S3 Dual-Stack Endpoints](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-dual-stack-endpoints) | ❌ | Not implemented. |
| [S3 Multi-Region Access Points](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Not implemented. |
| [S3 Multipart Upload](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Not implemented. |
| [S3 Presigned URLs](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Not implemented. |
| [S3 Transfer Acceleration](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-transfer-acceleration-endpoints) | ❌ | Not implemented. |

### Other Service Customizations

| Feature | Status | Notes |
|---------|--------|-------|
| [Amazon Glacier Customizations](https://smithy.io/2.0/aws/customizations/glacier-customizations.html) | ❌ | Not implemented. |
| [Amazon Machine Learning Customizations](https://smithy.io/2.0/aws/customizations/machinelearning-customizations.html) | ❌ | Not implemented. |
| [Amazon API Gateway Customizations](https://smithy.io/2.0/aws/customizations/apigateway-customizations.html) | ➖ | API Gateway deployment configuration. |

---

## AWS Rules Engine

Endpoint rules engine for dynamic endpoint resolution.

| Feature | Status | Notes |
|---------|--------|-------|
| [Rules Engine Specification](https://smithy.io/2.0/aws/rules-engine/index.html) | ❌ | Not implemented. |
| [`@endpointRuleSet`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Not implemented. |
| [`@contextParam`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Not implemented. |
| [`@staticContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Not implemented. |
| [`@clientContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ❌ | Not implemented. |
| [Authentication Scheme Validators](https://smithy.io/2.0/aws/rules-engine/auth-schemes.html) | ❌ | Not implemented. |
| [AWS Rules Engine Built-ins](https://smithy.io/2.0/aws/rules-engine/built-ins.html) | ❌ | Not implemented. |
| [AWS Rules Engine Library Functions](https://smithy.io/2.0/aws/rules-engine/library-functions.html) | ❌ | Not implemented. |

---

## Amazon Event Stream

Event streaming for real-time data.

| Feature | Status | Notes |
|---------|--------|-------|
| [Event Headers](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Not implemented. |
| [Event Payloads](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Not implemented. |
| [Event Stream Specification](https://smithy.io/2.0/aws/amazon-eventstream.html) | ❌ | Not implemented. |

---

## AWS IAM Traits

IAM policy generation traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [Condition Keys](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation. |
| [IAM Action Traits](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation. |
| [IAM Resource Traits](https://smithy.io/2.0/aws/aws-iam.html) | ➖ | IAM policy generation. |

---

## Amazon API Gateway Traits

API Gateway integration traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [API Gateway Authorizers](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment. |
| [API Gateway Integrations](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment. |
| [Request Validators](https://smithy.io/2.0/aws/amazon-apigateway.html) | ➖ | API Gateway deployment. |

---

## AWS CloudFormation Traits

CloudFormation resource generation traits. Not applicable to client code generation.

| Feature | Status | Notes |
|---------|--------|-------|
| [CloudFormation Resource Traits](https://smithy.io/2.0/aws/aws-cloudformation.html) | ➖ | CloudFormation resource schema generation. |

---

*Structure and categories follow [Smithy 2.0 AWS integrations](https://smithy.io/2.0/aws/index.html). Revise rows when client or server behavior changes.*
