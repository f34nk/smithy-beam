# Smithy Trait Support

This document lists Smithy 2.0 traits and how they relate to **generated** Erlang and Elixir code in smithy-beam. Notes describe Erlang output unless marked otherwise; the `ElixirWriter` is implemented but the `codegen-elixir` Smithy Build plugin is not yet registered, so traits do not yet affect generated Elixir code.

**Legend:**
- ✅ Supported - Trait is read and affects code generation
- ⚠️ Partial - Trait is recognized but not fully implemented
- ❌ Not Supported - Trait has no effect on generated code
- ➖ N/A - Trait is not applicable to client code generation

---

## HTTP Binding Traits

Traits for HTTP protocol bindings.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#http`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-http-trait) | ✅ | Method, URI, and success code read into `HttpSpec`. **Client:** `make_<op>_request/2` uses the method, substitutes URI labels, and pattern-matches the success code in the `httpc` response. **Server:** `route/2` dispatches on method + URI prefix; `dispatch_<op>/5` returns the success code via `smithy_server:response/2`. |
| [`smithy.api#httpHeader`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpheader-trait) | ✅ | Read into `HeaderBinding` for both input and output members. **Client request:** operation functions build a header list with content-type base and conditional member guards. **Client response:** output members with `@httpHeader` are stored in `OperationSpec.responseHeaders()` and extracted from the `httpc` response via `proplists:get_value/2`; values are converted from strings to binaries and placed in the result map. **Server:** `awsJson` routing reads `X-Amz-Target` from headers extracted by `smithy_server:extract/1`. |
| [`smithy.api#httpLabel`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httplabel-trait) | ✅ | Read into `LabelBinding` (with `requiresEncoding` flag). **Client:** URI path segments are substituted as Erlang binary interpolation with optional `url_encode/1`. **Server:** `deserialize_<op>/3` extracts label values via binary pattern matching on the request path. |
| [`smithy.api#httpPayload`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httppayload-trait) | ✅ | Detected in both input and output shapes and stored in `BodySpec.payloadMember()` / `OperationSpec.responsePayloadMember()`. **Client request:** the member value is sent as the raw HTTP body without JSON wrapping. **Client response:** the raw response body is returned as-is under the member name without JSON-decoding. |
| [`smithy.api#httpQuery`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpquery-trait) | ✅ | Read into `QueryBinding`. **Client:** operation functions append `uri_string:compose_query/1` output to the URL when query members are present. **Server:** not yet extracted from the request in `deserialize_<op>/3`. |
| [`smithy.api#cors`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-cors-trait) | ❌ | CORS configuration for service |
| [`smithy.api#httpChecksumRequired`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpchecksumrequired-trait) | ❌ | Requires checksum header |
| [`smithy.api#httpError`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httperror-trait) | ✅ | HTTP status code read into `ErrorBinding`. **Client:** a single `parse_error/2` is generated per module, deduplicated by Smithy error name; for REST_XML / AWS_JSON dispatch is on the error code string, returning `#{error_type => atom, message => binary()}` maps; for REST_JSON / AWS_QUERY dispatch is on the HTTP status integer, returning `{error, {atom, Body}}` tuples. **Server:** error shapes are typed in `-callback` declarations; `smithy_server:error_response/1` and `smithy_error_map:to_http/1` map errors to HTTP responses. |
| [`smithy.api#httpPrefixHeaders`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpprefixheaders-trait) | ❌ | Binds map to prefixed headers |
| [`smithy.api#httpQueryParams`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpqueryparams-trait) | ❌ | Binds map to query parameters |
| [`smithy.api#httpResponseCode`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpresponsecode-trait) | ✅ | Detected on output shapes; stored in `OperationSpec.responseCodeMember()`. **Client:** the HTTP status integer from the `httpc` response is placed in the result map under this member name. |

---

## Protocol & Serialization Traits

Traits for serialization and protocol behavior.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#jsonName`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-jsonname-trait) | ❌ | Uses custom JSON key name in serialization |
| [`smithy.api#xmlAttribute`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlattribute-trait) | ❌ | Serializes member as XML attribute |
| [`smithy.api#xmlFlattened`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlflattened-trait) | ❌ | Flattens list/map in XML serialization |
| [`smithy.api#xmlName`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlname-trait) | ❌ | Uses custom XML element name |
| [`smithy.api#xmlNamespace`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlnamespace-trait) | ❌ | Defines XML namespace |
| [`smithy.api#mediaType`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-mediatype-trait) | ❌ | Defines MIME type for blob/string |
| [`smithy.api#timestampFormat`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-timestampformat-trait) | ❌ | Specifies timestamp wire format |
| [`smithy.api#protocolDefinition`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-protocoldefinition-trait) | ❌ | Used by `ProtocolDetector` to identify the active protocol trait on a service — enables custom protocol discovery |

---

## AWS Protocol Traits (`aws.protocols#*`)

AWS-specific protocol traits.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.protocols#awsJson1_0`](https://smithy.io/2.0/aws/protocols/aws-json-1_0-protocol.html#aws-protocols-awsjson1_0-trait) | ✅ | Fully implemented — `AwsJsonProtocolAnalyzer`, `Content-Type: application/x-amz-json-1.0`, literal `X-Amz-Target` header, `jsx:encode(Input)` body, `__type`-based error dispatch, structured error maps; end-to-end example (`dynamodb-demo`) |
| [`aws.protocols#awsJson1_1`](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html#aws-protocols-awsjson1_1-trait) | ✅ | Fully implemented — `AwsJson11ProtocolAnalyzer` with `application/x-amz-json-1.1`; end-to-end examples (`firehose-demo`, `kinesis-demo`, `ssm-demo`) |
| [`aws.protocols#awsQuery`](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html#aws-protocols-awsquery-trait) | ✅ | Fully implemented — `AwsQueryProtocolAnalyzer`, `POST /` with form-encoded body, `aws_query:encode/3` with `Action` + `Version`, response envelopes stripped by `aws_query:unwrap_response/1`; end-to-end examples (`iam-demo`, `sns-demo`, `rds-demo`) |
| [`aws.protocols#ec2Query`](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html#aws-protocols-ec2query-trait) | ✅ | Fully implemented — `Ec2QueryProtocolAnalyzer` reads `@ec2QueryName` traits at codegen time; wire-name overrides stored in `BodySpec.wireNameOverrides()`; response envelopes stripped by `aws_query:unwrap_response/1`; EC2 error format dispatched; end-to-end example (`ec2-demo`) |
| [`aws.protocols#restJson1`](https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html#aws-protocols-restjson1-trait) | ✅ | Fully implemented — operation analysis, pipeline wiring, and end-to-end examples (`weather-service`, `storage-service`) |
| [`aws.protocols#restXml`](https://smithy.io/2.0/aws/protocols/aws-restxml-protocol.html#aws-protocols-restxml-trait) | ✅ | Fully implemented — `RestXmlProtocolAnalyzer`, XML body encoding, `aws_xml.erl` runtime, `aws_s3.erl` for S3 services (URL building via `aws_s3:build_url`), XML error code string dispatch; end-to-end example (`s3-demo`) |
| [`aws.protocols#awsQueryCompatible`](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html#aws-protocols-awsquerycompatible-trait) | ❌ | Query protocol compatibility mode |
| [`aws.protocols#httpChecksum`](https://smithy.io/2.0/aws/aws-core.html#aws-protocols-httpchecksum-trait) | ❌ | HTTP checksum configuration |
| [`aws.protocols#awsQueryError`](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html#aws-protocols-awsqueryerror-trait) | ➖ | Custom error code for Query protocol |
| [`aws.protocols#ec2QueryName`](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html#aws-protocols-ec2queryname-trait) | ✅ | Read by `Ec2QueryProtocolAnalyzer` at codegen time; overrides are stored in `BodySpec.wireNameOverrides()` and emitted as the wire key in the generated `make_*_request` body-builder map (the Smithy name is still used to look up the value from the caller's `Input` map) |

---

## AWS Authentication Traits (`aws.auth#*`)

AWS-specific authentication traits.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.auth#sigv4`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4-trait) | ✅ | Detected on service; signing name read into `AuthSpec` IR; `aws_sigv4.erl` and `aws_credentials.erl` runtime modules copied into output; `aws_sigv4:sign_request/5` called inside `make_<op>_request/2` for services that require signing |
| [`aws.auth#cognitoUserPools`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-cognitouserpools-trait) | ❌ | Cognito User Pools authentication |
| [`aws.auth#sigv4a`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4a-trait) | ❌ | Multi-region SigV4a signing |
| [`aws.auth#unsignedPayload`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-unsignedpayload-trait) | ❌ | Skips payload signing |

---

## Type Refinement Traits

Traits that refine or modify type semantics.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#enumValue`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-enumvalue-trait) | ✅ | Wire values (e.g. `"Celsius"`) are used in generated `encode_<enum>/1` and `decode_<enum>/1` helpers; enum atoms in generated code are lowercase (e.g. `celsius`) |
| [`smithy.api#error`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-error-trait) | ✅ | Error shapes are rendered as struct types; dispatched in the protocol-aware `parse_error/2` |
| [`smithy.api#input`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-input-trait) | ❌ | Marks structure as operation input |
| [`smithy.api#output`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-output-trait) | ❌ | Marks structure as operation output |
| [`smithy.api#required`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-required-trait) | ✅ | All struct map fields use `=>`; required members are enforced at call sites via `validate_<struct>/1` helpers (generated only for operation input types) which return `{error, {missing_required_fields, [binary()]}}` |
| [`smithy.api#addedDefault`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-addeddefault-trait) | ❌ | Indicates member had default added after initial release |
| [`smithy.api#clientOptional`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-clientoptional-trait) | ❌ | Indicates a member is optional for clients |
| [`smithy.api#default`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-default-trait) | ❌ | Sets default value for a member |
| [`smithy.api#mixin`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-mixin-trait) | ❌ | Defines a shape as a mixin |
| [`smithy.api#sparse`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-sparse-trait) | ❌ | Allows null values in lists/maps |

---

## Constraint Traits

Traits that constrain or validate values.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#enum`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-enum-trait) | ✅ | `EnumSpec` assembled from both legacy `@enum` strings and Smithy 2.0 `enum` shapes; `-type name() :: celsius \| fahrenheit.` emitted as lowercase atoms; `encode_<enum>/1` and `decode_<enum>/1` helpers generated using wire values; Erlang reserved words quoted (e.g. `'and'`) |
| [`smithy.api#idRef`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-idref-trait) | ❌ | Constrains string to be a valid shape ID |
| [`smithy.api#length`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-length-trait) | ❌ | Constrains length of strings, lists, or blobs |
| [`smithy.api#pattern`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-pattern-trait) | ❌ | Requires string values to match a regular expression |
| [`smithy.api#private`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-private-trait) | ❌ | Prevents shapes from being accessible outside namespace |
| [`smithy.api#range`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-range-trait) | ❌ | Constrains numeric values to a minimum and/or maximum |
| [`smithy.api#uniqueItems`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-uniqueitems-trait) | ➖ | List constraint - Erlang lists don't enforce uniqueness |

---

## Documentation Traits

Traits that provide documentation metadata.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#documentation`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-documentation-trait) | ⚠️ | Recognized but not yet extracted to EDoc comments |
| [`smithy.api#deprecated`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-deprecated-trait) | ❌ | Marks shape as deprecated |
| [`smithy.api#examples`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-examples-trait) | ❌ | Provides example input/output for operations |
| [`smithy.api#externalDocumentation`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-externaldocumentation-trait) | ❌ | Links to external documentation |
| [`smithy.api#internal`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-internal-trait) | ❌ | Marks shape as internal implementation detail |
| [`smithy.api#recommended`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-recommended-trait) | ❌ | Suggests a member should be set |
| [`smithy.api#sensitive`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-sensitive-trait) | ❌ | Marks data as sensitive (could mask in logs) |
| [`smithy.api#since`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-since-trait) | ❌ | Documents when shape was added |
| [`smithy.api#tags`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-tags-trait) | ❌ | Arbitrary tags for categorization |
| [`smithy.api#title`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-title-trait) | ❌ | Human-readable title for documentation |
| [`smithy.api#unstable`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-unstable-trait) | ❌ | Marks shape as unstable/experimental |

---

## Behavior Traits

Traits that define operation behavior.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#paginated`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-paginated-trait) | ⚠️ | Token and items members read into `PaginationSpec` IR; `renderPaginationHelper` emits `<op>_stream/2,3` accumulation loop; wired in pipeline when `op.pagination()` is non-null; no examples yet |
| [`smithy.api#idempotencyToken`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-idempotencytoken-trait) | ❌ | Auto-generates unique token for idempotent operations |
| [`smithy.api#idempotent`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-idempotent-trait) | ❌ | Marks operation as idempotent |
| [`smithy.api#readonly`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-readonly-trait) | ❌ | Marks operation as read-only |
| [`smithy.api#requestCompression`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-requestcompression-trait) | ❌ | Enables compressed request payloads (e.g. gzip) |
| [`smithy.api#retryable`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-retryable-trait) | ❌ | Marks error as retryable (retry logic uses HTTP status codes) |

---

## Resource Traits

Traits for modeling resources and attaching operations to resource shapes.

**Client codegen** typically walks the **service closure** with `TopDownIndex` and emit client functions for every operation reachable from the service, including operations attached via **resource** shapes. The table below lists resource metadata traits; many are server- or documentation-oriented and may not change client surface area.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#nestedProperties`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-nestedproperties-trait) | ➖ | Server-side resource modeling |
| [`smithy.api#noReplace`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-noreplace-trait) | ➖ | Server-side resource modeling |
| [`smithy.api#notProperty`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-notproperty-trait) | ➖ | Server-side resource modeling |
| [`smithy.api#property`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-property-trait) | ➖ | Server-side resource modeling |
| [`smithy.api#references`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-references-trait) | ➖ | Server-side resource modeling |
| [`smithy.api#resourceIdentifier`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-resourceidentifier-trait) | ➖ | Server-side resource modeling |

---

## Authentication Traits

Traits for authentication schemes.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#auth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-auth-trait) | ❌ | Defines supported auth schemes for service |
| [`smithy.api#authDefinition`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-authdefinition-trait) | ❌ | Defines a new authentication scheme |
| [`smithy.api#httpApiKeyAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-httpapikeyauth-trait) | ❌ | API key authentication |
| [`smithy.api#httpBasicAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-httpbasicauth-trait) | ❌ | HTTP Basic authentication |
| [`smithy.api#httpBearerAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-httpbearerauth-trait) | ❌ | HTTP Bearer token authentication |
| [`smithy.api#httpDigestAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-httpdigestauth-trait) | ❌ | HTTP Digest authentication |
| [`smithy.api#optionalAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-optionalauth-trait) | ❌ | Marks operation as optionally authenticated |

---

## Streaming Traits

Traits for streaming data.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#eventHeader`](https://smithy.io/2.0/spec/streaming.html#smithy-api-eventheader-trait) | ❌ | Event stream header binding |
| [`smithy.api#eventPayload`](https://smithy.io/2.0/spec/streaming.html#smithy-api-eventpayload-trait) | ❌ | Event stream payload binding |
| [`smithy.api#requiresLength`](https://smithy.io/2.0/spec/streaming.html#smithy-api-requireslength-trait) | ❌ | Requires Content-Length for streaming |
| [`smithy.api#streaming`](https://smithy.io/2.0/spec/streaming.html#smithy-api-streaming-trait) | ❌ | Enables streaming of large payloads |

---

## Endpoint Traits

Traits for endpoint configuration.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#endpoint`](https://smithy.io/2.0/spec/endpoint-traits.html#smithy-api-endpoint-trait) | ❌ | Defines host prefix pattern |
| [`smithy.api#hostLabel`](https://smithy.io/2.0/spec/endpoint-traits.html#smithy-api-hostlabel-trait) | ❌ | Binds member to host prefix segment |

---

## Model Validation Traits

Traits for model validation rules.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.api#suppress`](https://smithy.io/2.0/spec/model-validation.html#smithy-api-suppress-trait) | ➖ | Suppresses validation events (build-time only) |
| [`smithy.api#trait`](https://smithy.io/2.0/spec/model.html#smithy-api-trait-trait) | ➖ | Defines a trait (meta-trait) |
| [`smithy.api#traitValidators`](https://smithy.io/2.0/spec/model-validation.html#smithy-api-traitvalidators-trait) | ➖ | Custom trait validators (build-time only) |

---

## AWS Core Traits (`aws.api#*`)

AWS service metadata traits.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.api#service`](https://smithy.io/2.0/aws/aws-core.html#aws-api-service-trait) | ❌ | AWS service metadata (sdkId, arnNamespace, etc.) |
| [`aws.api#clientDiscoveredEndpoint`](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientdiscoveredendpoint-trait) | ❌ | Operation uses discovered endpoint |
| [`aws.api#clientEndpointDiscovery`](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientendpointdiscovery-trait) | ❌ | Service supports endpoint discovery |
| [`aws.api#clientEndpointDiscoveryId`](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientendpointdiscoveryid-trait) | ❌ | Member used for endpoint discovery cache |
| [`aws.api#arn`](https://smithy.io/2.0/aws/aws-core.html#aws-api-arn-trait) | ➖ | Defines ARN template - server-side resource modeling |
| [`aws.api#arnReference`](https://smithy.io/2.0/aws/aws-core.html#aws-api-arnreference-trait) | ➖ | References ARN for validation |
| [`aws.api#controlPlane`](https://smithy.io/2.0/aws/aws-core.html#aws-api-controlplane-trait) | ➖ | Marks as control plane operation |
| [`aws.api#data`](https://smithy.io/2.0/aws/aws-core.html#aws-api-data-trait) | ➖ | Data classification for compliance |
| [`aws.api#dataPlane`](https://smithy.io/2.0/aws/aws-core.html#aws-api-dataplane-trait) | ➖ | Marks as data plane operation |
| [`aws.api#tagEnabled`](https://smithy.io/2.0/aws/aws-core.html#aws-api-tagenabled-trait) | ➖ | Service supports resource tagging |
| [`aws.api#taggable`](https://smithy.io/2.0/aws/aws-core.html#aws-api-taggable-trait) | ➖ | Resource is taggable |

---

## AWS API Gateway Traits (`aws.apigateway#*`)

Traits for Amazon API Gateway integration.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.apigateway#apiKeySource`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-apikeysource-trait) | ➖ | API Gateway configuration |
| [`aws.apigateway#authorizer`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-authorizer-trait) | ➖ | API Gateway configuration |
| [`aws.apigateway#authorizers`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-authorizers-trait) | ➖ | API Gateway configuration |
| [`aws.apigateway#integration`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-integration-trait) | ➖ | API Gateway configuration |
| [`aws.apigateway#mockIntegration`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-mockintegration-trait) | ➖ | API Gateway configuration |
| [`aws.apigateway#requestValidator`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-requestvalidator-trait) | ➖ | API Gateway configuration |

---

## AWS CloudFormation Traits (`aws.cloudformation#*`)

Traits for CloudFormation resource generation. Not applicable to client generation.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.cloudformation#cfnAdditionalIdentifier`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnadditionalidentifier-trait) | ➖ | CloudFormation resource generation |
| [`aws.cloudformation#cfnDefaultValue`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfndefaultvalue-trait) | ➖ | CloudFormation resource generation |
| [`aws.cloudformation#cfnExcludeProperty`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnexcludeproperty-trait) | ➖ | CloudFormation resource generation |
| [`aws.cloudformation#cfnMutability`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnmutability-trait) | ➖ | CloudFormation resource generation |
| [`aws.cloudformation#cfnName`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnname-trait) | ➖ | CloudFormation resource generation |
| [`aws.cloudformation#cfnResource`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnresource-trait) | ➖ | CloudFormation resource generation |

---

## AWS Endpoints Traits (`aws.endpoints#*`)

Traits for endpoint resolution rules.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.endpoints#dualStackOnlyEndpoints`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-dualstackonlyendpoints-trait) | ➖ | Uses static endpoints.json |
| [`aws.endpoints#endpointsModifier`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-endpointsmodifier-trait) | ➖ | Uses static endpoints.json |
| [`aws.endpoints#rulesBasedEndpoints`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-rulesbasedendpoints-trait) | ➖ | Uses static endpoints.json |
| [`aws.endpoints#standardPartitionalEndpoints`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-standardpartitionalendpoints-trait) | ➖ | Uses static endpoints.json |
| [`aws.endpoints#standardRegionalEndpoints`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-standardregionalendpoints-trait) | ➖ | Uses static endpoints.json |

---

## AWS IAM Traits (`aws.iam#*`)

Traits for IAM policy generation. Not applicable to client generation.

| Trait | Status | Notes |
|-------|--------|-------|
| [`aws.iam#actionName`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-actionname-trait) | ➖ | IAM policy generation |
| [`aws.iam#actionPermissionDescription`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-actionpermissiondescription-trait) | ➖ | IAM policy generation |
| [`aws.iam#conditionKeyValue`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-conditionkeyvalue-trait) | ➖ | IAM policy generation |
| [`aws.iam#conditionKeys`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-conditionkeys-trait) | ➖ | IAM policy generation |
| [`aws.iam#defineConditionKeys`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-defineconditionkeys-trait) | ➖ | IAM policy generation |
| [`aws.iam#disableConditionKeyInference`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-disableconditionkeyinference-trait) | ➖ | IAM policy generation |
| [`aws.iam#iamAction`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-iamaction-trait) | ➖ | IAM policy generation |
| [`aws.iam#iamResource`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-iamresource-trait) | ➖ | IAM policy generation |
| [`aws.iam#requiredActions`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-requiredactions-trait) | ➖ | IAM policy generation |
| [`aws.iam#serviceResolvedConditionKeys`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-serviceresolvedconditionkeys-trait) | ➖ | IAM policy generation |
| [`aws.iam#supportedPrincipalTypes`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-supportedprincipaltypes-trait) | ➖ | IAM policy generation |

---

## Additional Specs

Traits from additional Smithy specifications.

### AI Traits (`smithy.ai#*`)

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.ai#prompts`](https://smithy.io/2.0/additional-specs/ai-traits.html#smithy-ai-prompts-trait) | ➖ | AI/ML prompt templates |

### MQTT Traits (`smithy.mqtt#*`)

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.mqtt#publish`](https://smithy.io/2.0/additional-specs/mqtt.html#smithy-mqtt-publish-trait) | ➖ | MQTT publish operation |
| [`smithy.mqtt#subscribe`](https://smithy.io/2.0/additional-specs/mqtt.html#smithy-mqtt-subscribe-trait) | ➖ | MQTT subscribe operation |
| [`smithy.mqtt#topicLabel`](https://smithy.io/2.0/additional-specs/mqtt.html#smithy-mqtt-topiclabel-trait) | ➖ | MQTT topic parameter |

### OpenAPI Traits (`smithy.openapi#*`)

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.openapi#specificationExtension`](https://smithy.io/2.0/guides/model-translations/converting-to-openapi.html#smithy-openapi-specificationextension-trait) | ➖ | OpenAPI extension generation |

### Protocol Traits (`smithy.protocols#*`)

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.protocols#rpcv2Cbor`](https://smithy.io/2.0/additional-specs/protocols/smithy-rpc-v2.html#smithy-protocols-rpcv2cbor-trait) | ❌ | Smithy RPC v2 CBOR protocol |

### Rules Engine Traits (`smithy.rules#*`)

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.rules#clientContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-clientcontextparams-trait) | ➖ | Endpoint rules engine |
| [`smithy.rules#contextParam`](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-contextparam-trait) | ➖ | Endpoint rules engine |
| [`smithy.rules#endpointBdd`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html#smithy-rules-endpointbdd-trait) | ➖ | Endpoint rules engine |
| [`smithy.rules#endpointRuleSet`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html#smithy-rules-endpointruleset-trait) | ➖ | Endpoint rules engine |
| [`smithy.rules#operationContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-operationcontextparams-trait) | ➖ | Endpoint rules engine |
| [`smithy.rules#staticContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-staticcontextparams-trait) | ➖ | Endpoint rules engine |

### Test Traits (`smithy.test#*`)

[HTTP protocol compliance tests](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html) define expected wire requests/responses. A full generator would parse these traits and emit test modules appropriate for Erlang (e.g. EUnit) and/or Elixir.

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.test#httpMalformedRequestTests`](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html#smithy-test-httpmalformedrequesttests-trait) | ➖ | Server-focused malformed-request tests; not used by client codegen |
| [`smithy.test#httpRequestTests`](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html#smithy-test-httprequesttests-trait) | ❌ | Request-side assertions (method, URI, headers, body) |
| [`smithy.test#httpResponseTests`](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html#smithy-test-httpresponsetests-trait) | ❌ | Response-side assertions (status, headers, body) |
| [`smithy.test#smokeTests`](https://smithy.io/2.0/additional-specs/smoke-tests.html#smithy-test-smoketests-trait) | ➖ | Smoke test definitions |

### Waiter Traits (`smithy.waiters#*`)

| Trait | Status | Notes |
|-------|--------|-------|
| [`smithy.waiters#waitable`](https://smithy.io/2.0/additional-specs/waiters.html#smithy-waiters-waitable-trait) | ❌ | Defines waiter for polling long-running operations |

## Custom protocol traits (SPI)

Custom **protocol traits** use `@protocolDefinition` so tooling can discover them as first-class protocols:

```smithy
@protocolDefinition
@trait(selector: "service")
structure ndjsonProtocol {}

@ndjsonProtocol
service GreetingService { ... }
```

(The service body would list `version`, `operations`, and the usual operation and shape definitions.)

**Integration:** Java resolves the active protocol `ShapeId` on a service and looks up a matching analyzer or writer implementation via **`ServiceLoader`** (JARs register implementations under `META-INF/services/`). Third-party JARs can ship additional protocols without modifying the core repositories. If no implementation is registered, generation may emit stubs or fail with a clear diagnostic.

Arbitrary **non-protocol** custom traits do not drive codegen unless explicit extension points are added later.

---

*Trait inventory structure follows [Smithy 2.0 Trait Index](https://smithy.io/2.0/trait-index.html). Rows reflect the current generators and are revised when behavior changes.*
