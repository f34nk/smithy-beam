# AWS SDK Support

This document lists AWS-oriented features from the [Smithy AWS integrations](https://smithy.io/2.0/aws/index.html) specification and how they relate to **generated** Erlang and Elixir code in smithy-beam today.

smithy-beam is a Smithy DirectedCodegen project for the BEAM. AWS service clients are a long-term goal; the current baseline implements REST JSON 1, AWS JSON 1.0 and 1.1, AWS Query, EC2 Query, and REST-XML protocol stacks with generated codecs, dispatch, routers, paginator helpers, retry wrappers, waiters, and optional HTTP compliance tests. Generated SigV4 clients emit signing hooks, a default credential resolution chain, endpoint resolution (rules engine when an endpoint rule set is present, static regional fallback otherwise), and S3 bucket addressing helpers when the model requires them.

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
| Scalar, list, and map aliases | ✅ | Named shapes in the service closure become type aliases in one types file per service name. |
| Enum and intEnum modules | ✅ | Nested Elixir modules with conversion helpers; Erlang atom unions with unknown wire preservation. |
| Union type aliases | ✅ | Tagged tuple unions with an unknown variant. |
| Structure records and structs | ✅ | Erlang `-record` and `-type`; Elixir nested modules with `@type t` and `defstruct`. |
| Error shape types | ✅ | `@error` structures become typed records (Erlang) or `defexception` modules (Elixir) with fault kind metadata. |
| Shape and member documentation | ✅ | `@documentation` on shapes and members flows into generated type comments. Service docs replace the generic types file header when present. |
| Deprecation filtering | ✅ | `@deprecated` removes shapes from generated output when Smithy-Build `relativeDate` or `relativeVersion` is configured. |
| Sparse collections | ✅ | `@sparse` widens list element and map value types to include `undefined` (Erlang) or `nil` (Elixir). REST JSON codecs encode and decode sparse nulls on the wire. |
| Streaming blob metadata | ✅ | `@streaming` blob payloads encode and decode on the wire in REST JSON 1 codecs. Event-stream unions are handled separately (see Amazon Event Stream). |
| Erlang reserved-word escaping | ✅ | Erlang keywords and colliding identifiers are escaped in types output; client and server codecs use the same escaped record names. |
| Service shape rename maps | ✅ | Rename targets flow into types output, module filenames, and operation record names in codecs. |

---

## Generated Client Features

Output from `erlang-client-codegen` and `elixir-client-codegen`.

| Feature | Status | Notes |
|---------|--------|-------|
| Operation stubs | ✅ | One function per operation; supported AWS protocols wire encode, HTTP dispatch, and decode. Unsupported protocols emit `{error, not_implemented}` stubs. |
| REST JSON 1 request encoding | ✅ | Per-service codec module encodes path labels, query params, headers, and JSON document members into an `http_request` record or map. |
| REST JSON 1 response decoding | ✅ | Codec decodes JSON document, header, and payload bindings into typed output records or structs. |
| HTTP dispatch | ✅ | Erlang uses OTP `httpc` via a generated `<prefix>_http` module. Elixir uses `Req`. Both honor a configurable HTTP client module in client config for tests. |
| Default endpoint in generated config | ✅ | Generated clients emit `default_config/0` and endpoint resolution helpers. HTTP dispatch merges a resolved base URL when `base_url` is unset: rules engine evaluation when `@endpointRuleSet` is present, static regional fallback from `endpointPrefix` and region otherwise. |
| Pagination helpers | ✅ | `@paginated` operations get a generated paginator module that walks output tokens and accumulates item lists. |
| Operation documentation | ✅ | `@documentation` on operations is emitted into generated client function docs. |
| Type and shape documentation | ✅ | Types plugins emit shape and member docs into generated type files alongside operation docs on client stubs. |
| Error shape types | ✅ | `@error` structures become typed records (Erlang) or `defexception` modules (Elixir) with fault kind metadata. |
| Retry | ✅ | Generated retry module wraps client calls with exponential backoff for `@retryable` errors. |
| SigV4 signing | ✅ | Generated signing module invoked from HTTP dispatch when `@aws.auth#sigv4` is present. Golden-vector verified. Callers may supply credentials in client config or rely on the generated credential chain. SigV4A is not supported. |
| Credential providers | ✅ | Generated credential provider module resolves ENV, shared profile, ECS, and EC2 instance credentials when config credentials are unset. |
| Endpoint discovery | ❌ | Not implemented. |
| Input validation helpers | ❌ | `@required` affects generated types only; no runtime `validate_*` helpers. |
| HTTP prefix headers | ✅ | Map members bound with `@httpPrefixHeaders` expand into prefixed request headers on encode and reconstruct on decode. |
| HTTP response code binding | ✅ | `@httpResponseCode` members populate the modeled output field from the HTTP status on decode. |
| Modeled HTTP errors | ✅ | Client codecs dispatch `@httpError` status codes before type-discriminated errors. Server codecs encode error responses with modeled status codes. |
| Idempotency token | ✅ | `@idempotencyToken` members receive an auto-generated UUID on encode when unset. |
| Host label | ✅ | `@hostLabel` members substitute into URI templates and operation `@endpoint` host prefixes on encode. |
| Endpoint override trait | ❌ | Service-level `@endpoint` host override is not implemented. Operation `hostPrefix` is honored via host label expansion. |
| Request compression | ✅ | `@requestCompression` members are compressed on encode when configured in the model. |
| Streaming | ✅ | REST JSON 1 codecs encode and decode `@streaming` blob payloads on the wire. `@streaming` event-stream unions use generated event stream helpers (see Amazon Event Stream). |
| Waiters | ✅ | `@waitable` operations get generated waiter helpers that poll until success, failure, or timeout. |

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
| Request streaming | ✅ | REST JSON 1 server codecs decode and encode `@streaming` blob payloads. Event-stream union payloads use generated event stream helpers. |
| WebSocket / event streams | ✅ | Amazon Event Stream framing is generated for `@streaming` union shapes in edition 2026 services. |

---

## AWS Protocols

Protocol selection reads the sole `@protocolDefinition` trait on the selected service by default. `BeamProtocolResolver.resolveServiceProtocol` returns that trait id, or empty when the service declares none. Client and server plugins may instead set the optional `protocol` smithy-build key to a protocol trait shape id; when present, that id is used for wire emission without requiring the trait on the service. Custom protocols register a `BeamProtocolCodegen` implementation through `ErlangIntegration` or `ElixirIntegration` on the Smithy classpath (or the codegen-core SPI for built-in AWS protocols). `BeamProtocolCodegenFactory` registers built-in implementations for REST JSON 1, AWS JSON 1.0, AWS JSON 1.1, AWS Query, EC2 Query, and REST-XML.

| Feature | Status | Notes |
|---------|--------|-------|
| [AWS restJson1 protocol](https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html) | ✅ | Request encoding, client response decoding, server request decoding, server response encoding, routing, and paginators for Erlang and Elixir when the service carries `@restJson1`. Codecs honor `@jsonName`, `@httpQueryParams`, `@httpPrefixHeaders`, `@httpResponseCode`, `@httpError`, `@timestampFormat`, `@mediaType`, `@hostLabel`, `@idempotencyToken`, sparse collection nulls, and `@streaming` blob payloads. |
| [AWS JSON 1.0 protocol](https://smithy.io/2.0/aws/protocols/aws-json-1_0-protocol.html) | ✅ | Client and server codecs plus router dispatch for POST / with X-Amz-Target and content type application/x-amz-json-1.0 (Erlang and Elixir). |
| [AWS JSON 1.1 protocol](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html) | ✅ | Client and server codecs plus router dispatch for POST / with X-Amz-Target and content type application/x-amz-json-1.1 (Erlang and Elixir). Same wire rules as JSON 1.0. |
| [AWS Query protocol](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html) | ✅ | Form-urlencoded request encoding and XML response decoding for Erlang and Elixir clients and servers. |
| [AWS EC2 Query protocol](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html) | ✅ | EC2 Query name mapping and form encoding with XML response decoding. |
| [AWS restXml protocol](https://smithy.io/2.0/aws/protocols/aws-restxml-protocol.html) | ✅ | HTTP-bound request and response encoding with XML payload members. Honors `@xmlName`, `@xmlAttribute`, `@xmlFlattened`, and `@xmlNamespace` in generated codecs. |
| Custom protocols via `@protocolDefinition` | ⚠️ | Protocol traits are discovered and validated at codegen time. `BeamProtocolResolver` walks the service closure and fails with one aggregated diagnostic when shapes are unsupported for the selected protocol (for example event streams or `bigDecimal`). Additional protocols register via ErlangIntegration/ElixirIntegration or codegen-core SPI. |
| [HTTP Protocol Compliance Tests](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html) | ⚠️ | Emits test modules from `@httpRequestTests` and `@httpResponseTests` when the model defines those traits for the configured service. |

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
| `@httpPrefixHeaders` | ✅ | Map members expand into prefixed HTTP headers on encode and collapse back into a map on decode. |
| `@httpResponseCode` | ✅ | Response status populates the bound output member on client decode and server request decode. |
| `@httpError` | ✅ | Status-code clauses in client error dispatch; server error response encoders use modeled HTTP status. |
| `@jsonName` | ✅ | Wire JSON keys follow `@jsonName` when present. |
| `@timestampFormat` | ✅ | Timestamp helpers follow binding location and `@timestampFormat` (epoch seconds or date-time). |
| `@mediaType` | ✅ | Request and response Content-Type headers follow HttpBindingIndex negotiation. |

---

## XML Binding Traits

XML serialization traits for REST-XML and query protocols.

| Feature | Status | Notes |
|---------|--------|-------|
| [`@xmlName`](https://smithy.io/2.0/spec/protocol-traits.html#xmlname-trait) | ✅ | Wire element names follow `@xmlName` in REST-XML and Query XML codecs. |
| [`@xmlFlattened`](https://smithy.io/2.0/spec/protocol-traits.html#xmlflattened-trait) | ✅ | Flattened list serialization in REST-XML and Query XML codecs. |
| [`@xmlNamespace`](https://smithy.io/2.0/spec/protocol-traits.html#xmlnamespace-trait) | ✅ | Namespace URI and prefix on XML payload elements. |
| [`@xmlAttribute`](https://smithy.io/2.0/spec/protocol-traits.html#xmlattribute-trait) | ✅ | Member values serialize as XML attributes in REST-XML and Query XML codecs. |

---

## AWS Authentication

Authentication mechanisms for AWS services.

| Feature | Status | Notes |
|---------|--------|-------|
| [AWS Signature Version 4 (SigV4)](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4-trait) | ✅ | Generated signing module with golden-vector verified behavior. Credential chain resolves credentials when unset. |
| [Credential Provider Chain](https://smithy.io/2.0/aws/aws-auth.html) | ✅ | Generated provider module walks ENV, profile, ECS, and EC2 sources. |
| [AWS Signature Version 4A (SigV4A)](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4a-trait) | ❌ | Not implemented. |
| [Cognito User Pools Authentication](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-cognitouserpools-trait) | ❌ | Not implemented. |
| [Unsigned Payload](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-unsignedpayload-trait) | ✅ | Service- and operation-level `@unsignedPayload` sets the SigV4 payload hash to UNSIGNED-PAYLOAD on encode and sign. |

---

## AWS Core Specification

Core AWS service traits and metadata.

| Feature | Status | Notes |
|---------|--------|-------|
| [Service Trait (`aws.api#service`)](https://smithy.io/2.0/aws/aws-core.html#aws-api-service-trait) | ✅ | `sdkId`, `endpointPrefix`, and signing name flow into generated `default_config/0` and `resolve_base_url/1` when the trait is present. |
| [Endpoint Discovery](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientendpointdiscovery-trait) | ❌ | Not implemented. |
| [HTTP Checksum (`aws.protocols#httpChecksum`)](https://smithy.io/2.0/aws/aws-core.html#aws-protocols-httpchecksum-trait) | ✅ | Request and response checksum headers are computed and validated in generated codecs when the trait is present. |
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
| [Region Configuration](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ✅ | Generated `default_config/0` seeds a default region. Callers override via client config. |
| [Static Endpoint Resolution](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ✅ | Generated `resolve_base_url/1` builds regional HTTPS URLs from `endpointPrefix` and config region when no endpoint rule set is present. HTTP dispatch applies it when `base_url` is unset. |
| [Dual-Stack Endpoints](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-dualstackonlyendpoints-trait) | ❌ | Not implemented for general services. S3 dual-stack host suffix is available via client config (see S3 customizations). |
| [FIPS Endpoints](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented. |
| [Declarative Endpoint Traits](https://smithy.io/2.0/aws/aws-endpoints-region.html) | ❌ | Not implemented. |
| [Rules-Based Endpoint Resolution](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-rulesbasedendpoints-trait) | ✅ | When `@endpointRuleSet` is present, generated endpoint module evaluates rules at runtime and HTTP dispatch prefers that URL over static resolution. |

---

## AWS Service Customizations

Service-specific behaviors and customizations.

### Amazon S3 Customizations

| Feature | Status | Notes |
|---------|--------|-------|
| [S3 Path-Style Bucket Addressing](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-bucket-virtual-hosting) | ✅ | Generated S3 endpoint helper rewrites bucket labels into the path when `s3_addressing_style` is path_style. |
| [S3 Virtual-Hosted Bucket Addressing](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-bucket-virtual-hosting) | ✅ | Generated S3 endpoint helper moves the bucket into the host when `s3_addressing_style` is virtual_host (default). |
| [S3 Access Points](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Not implemented. |
| [S3 Dual-Stack Endpoints](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-dual-stack-endpoints) | ⚠️ | Dual-stack host suffix via `s3_use_dualstack` client config. Full dual-stack endpoint rules are not implemented. |
| [S3 Multi-Region Access Points](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Not implemented. |
| [S3 Multipart Upload](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ❌ | Not implemented. |
| [S3 Presigned URLs](https://smithy.io/2.0/aws/customizations/s3-customizations.html) | ✅ | Generated SigV4 module exposes presign helpers for query-string authentication. |
| [S3 Transfer Acceleration](https://smithy.io/2.0/aws/customizations/s3-customizations.html#s3-transfer-acceleration-endpoints) | ⚠️ | Accelerate host via `s3_use_accelerate` client config only. Endpoint rules and full acceleration lifecycle are not implemented. |

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
| [Rules Engine Specification](https://smithy.io/2.0/aws/rules-engine/index.html) | ⚠️ | Generated clients embed serialized rule sets and call the aws_endpoint_rules runtime when `@endpointRuleSet` is present. Full AWS rules engine surface is not otherwise exposed. |
| [`@endpointRuleSet`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ✅ | Rule set serialized into generated runtime types and evaluated by the service endpoints module. |
| [`@contextParam`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ➖ | Model metadata for rule parameters; operation input binding is not generated yet. |
| [`@staticContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ➖ | Model metadata for rule parameters; static values are not merged into generated resolvers yet. |
| [`@clientContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html) | ✅ | Client config keys are merged into rule evaluation parameters. |
| [Authentication Scheme Validators](https://smithy.io/2.0/aws/rules-engine/auth-schemes.html) | ⚠️ | Delegated to aws_endpoint_rules runtime when rules reference auth schemes. |
| [AWS Rules Engine Built-ins](https://smithy.io/2.0/aws/rules-engine/built-ins.html) | ⚠️ | Delegated to aws_endpoint_rules runtime. |
| [AWS Rules Engine Library Functions](https://smithy.io/2.0/aws/rules-engine/library-functions.html) | ⚠️ | Delegated to aws_endpoint_rules runtime. |

---

## Amazon Event Stream

Event streaming for real-time data.

| Feature | Status | Notes |
|---------|--------|-------|
| [Event Headers](https://smithy.io/2.0/aws/amazon-eventstream.html) | ✅ | Generated event stream module encodes and decodes event headers for `@streaming` union members. |
| [Event Payloads](https://smithy.io/2.0/aws/amazon-eventstream.html) | ✅ | Event payload members inside streaming unions are framed on the wire. |
| [Event Stream Specification](https://smithy.io/2.0/aws/amazon-eventstream.html) | ✅ | Amazon Event Stream framing for `@streaming` union shapes in edition 2026 services. Blob `@streaming` members remain plain payload streaming. |

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
