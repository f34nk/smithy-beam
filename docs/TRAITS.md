# Smithy Trait Support

This document lists Smithy 2.0 traits and their implementation status in **generated** Erlang and Elixir code in smithy-beam.

The **types** plugins generate scalar aliases, lists, maps, enums, intEnums, unions, structures, and error shapes for the selected service closure.

The **client** and **server** plugins run after types generation. For services using the REST JSON 1 protocol, they emit HTTP dispatch, REST JSON codecs, paginator helpers, and server routers with request decoders. Other protocol traits are not yet implemented.

Trait filtering via the `@deprecated` trait is applied when the Smithy-Build `relativeDate` or `relativeVersion` plugin settings are configured.

A separate column is used for each language to indicate support status with a checkbox.

**Legend:**

- ✅ Supported: the trait is read or implied by the model and changes generated output for that language.
- ❌ Not supported: the trait has no effect on generated code for that language, or generation fails when the feature is required.
- ➖ Not applicable: the trait does not apply to the current generators (for example build-only validation, or tooling-only metadata).

**Scope notes:**

- HTTP binding traits are honored for REST JSON 1 client and server generation. Bindings such as query maps, prefix headers, explicit response codes, and modeled HTTP errors are not yet emitted into generated codecs.
- `@documentation` is emitted on client and server operation stubs and on types output.
  Erlang types use `%% @doc` blocks above records and type aliases, with per-field edoc
  lines for documented members. Elixir types use `@moduledoc` on nested shape modules,
  `@typedoc` on union aliases, a Members appendix on structures when members carry docs,
  and comment lines above documented preamble aliases. Service-level documentation
  replaces the generic types file header when present.
- `@deprecated` removes shapes from generated output when the Smithy-Build `relativeDate` or `relativeVersion` setting is set. Without those settings, the trait has no effect on generated code.
- `@streaming` affects generated type comments and symbol metadata. Event-stream framing and payload streaming are not yet implemented in protocol codecs.
- Constraint traits (`length`, `range`, `pattern`, and similar) do not narrow generated Dialyzer or typespec surfaces.

---

## Type Refinement Traits

Traits that refine or modify type semantics.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#required`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-required-trait) | ✅ | ✅ |
| [`smithy.api#enumValue`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-enumvalue-trait) | ❌ | ✅ |
| [`smithy.api#error`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-error-trait) | ✅ | ✅ |
| [`smithy.api#input`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-input-trait) | ❌ | ❌ |
| [`smithy.api#output`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-output-trait) | ❌ | ❌ |
| [`smithy.api#addedDefault`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-addeddefault-trait) | ❌ | ❌ |
| [`smithy.api#clientOptional`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-clientoptional-trait) | ❌ | ❌ |
| [`smithy.api#default`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-default-trait) | ❌ | ❌ |
| [`smithy.api#mixin`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-mixin-trait) | ❌ | ❌ |
| [`smithy.api#sparse`](https://smithy.io/2.0/spec/type-refinement-traits.html#smithy-api-sparse-trait) | ✅ | ✅ |

---

## Constraint Traits

Traits that constrain or validate values.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#enum`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-enum-trait) | ✅ | ✅ |
| [`smithy.api#idRef`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-idref-trait) | ❌ | ❌ |
| [`smithy.api#length`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-length-trait) | ❌ | ❌ |
| [`smithy.api#pattern`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-pattern-trait) | ❌ | ❌ |
| [`smithy.api#private`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-private-trait) | ❌ | ❌ |
| [`smithy.api#range`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-range-trait) | ❌ | ❌ |
| [`smithy.api#uniqueItems`](https://smithy.io/2.0/spec/constraint-traits.html#smithy-api-uniqueitems-trait) | ➖ | ➖ |

---

## HTTP Binding Traits

Traits for HTTP protocol bindings.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#http`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-http-trait) | ✅ | ✅ |
| [`smithy.api#httpHeader`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpheader-trait) | ✅ | ✅ |
| [`smithy.api#httpLabel`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httplabel-trait) | ✅ | ✅ |
| [`smithy.api#httpPayload`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httppayload-trait) | ✅ | ✅ |
| [`smithy.api#httpQuery`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpquery-trait) | ✅ | ✅ |
| [`smithy.api#cors`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-cors-trait) | ❌ | ❌ |
| [`smithy.api#httpChecksumRequired`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpchecksumrequired-trait) | ❌ | ❌ |
| [`smithy.api#httpError`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httperror-trait) | ❌ | ❌ |
| [`smithy.api#httpPrefixHeaders`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpprefixheaders-trait) | ❌ | ❌ |
| [`smithy.api#httpQueryParams`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpqueryparams-trait) | ❌ | ❌ |
| [`smithy.api#httpResponseCode`](https://smithy.io/2.0/spec/http-bindings.html#smithy-api-httpresponsecode-trait) | ❌ | ❌ |

---

## Protocol & Serialization Traits

Traits for serialization and protocol behavior.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#jsonName`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-jsonname-trait) | ❌ | ❌ |
| [`smithy.api#xmlAttribute`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlattribute-trait) | ❌ | ❌ |
| [`smithy.api#xmlFlattened`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlflattened-trait) | ❌ | ❌ |
| [`smithy.api#xmlName`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlname-trait) | ❌ | ❌ |
| [`smithy.api#xmlNamespace`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-xmlnamespace-trait) | ❌ | ❌ |
| [`smithy.api#mediaType`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-mediatype-trait) | ❌ | ❌ |
| [`smithy.api#timestampFormat`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-timestampformat-trait) | ❌ | ❌ |
| [`smithy.api#protocolDefinition`](https://smithy.io/2.0/spec/protocol-traits.html#smithy-api-protocoldefinition-trait) | ✅ | ✅ |

---

## AWS Protocol Traits (`aws.protocols#*`)

AWS-specific protocol traits.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`aws.protocols#awsJson1_0`](https://smithy.io/2.0/aws/protocols/aws-json-1_0-protocol.html#aws-protocols-awsjson1_0-trait) | ❌ | ❌ |
| [`aws.protocols#awsJson1_1`](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html#aws-protocols-awsjson1_1-trait) | ❌ | ❌ |
| [`aws.protocols#awsQuery`](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html#aws-protocols-awsquery-trait) | ❌ | ❌ |
| [`aws.protocols#ec2Query`](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html#aws-protocols-ec2query-trait) | ❌ | ❌ |
| [`aws.protocols#restJson1`](https://smithy.io/2.0/aws/protocols/aws-restjson1-protocol.html#aws-protocols-restjson1-trait) | ✅ | ✅ |
| [`aws.protocols#restXml`](https://smithy.io/2.0/aws/protocols/aws-restxml-protocol.html#aws-protocols-restxml-trait) | ❌ | ❌ |
| [`aws.protocols#awsQueryCompatible`](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html#aws-protocols-awsquerycompatible-trait) | ❌ | ❌ |
| [`aws.protocols#httpChecksum`](https://smithy.io/2.0/aws/aws-core.html#aws-protocols-httpchecksum-trait) | ❌ | ❌ |
| [`aws.protocols#awsQueryError`](https://smithy.io/2.0/aws/protocols/aws-query-protocol.html#aws-protocols-awsqueryerror-trait) | ➖ | ➖ |
| [`aws.protocols#ec2QueryName`](https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html#aws-protocols-ec2queryname-trait) | ❌ | ❌ |

---

## AWS Authentication Traits (`aws.auth#*`)

AWS-specific authentication traits.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`aws.auth#sigv4`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4-trait) | ❌ | ❌ |
| [`aws.auth#cognitoUserPools`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-cognitouserpools-trait) | ❌ | ❌ |
| [`aws.auth#sigv4a`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-sigv4a-trait) | ❌ | ❌ |
| [`aws.auth#unsignedPayload`](https://smithy.io/2.0/aws/aws-auth.html#aws-auth-unsignedpayload-trait) | ❌ | ❌ |

---

## Documentation Traits

Traits that provide documentation metadata.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#documentation`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-documentation-trait) | ✅ | ✅ |
| [`smithy.api#deprecated`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-deprecated-trait) | ✅ | ✅ |
| [`smithy.api#examples`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-examples-trait) | ❌ | ❌ |
| [`smithy.api#externalDocumentation`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-externaldocumentation-trait) | ❌ | ❌ |
| [`smithy.api#internal`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-internal-trait) | ❌ | ❌ |
| [`smithy.api#recommended`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-recommended-trait) | ❌ | ❌ |
| [`smithy.api#sensitive`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-sensitive-trait) | ❌ | ❌ |
| [`smithy.api#since`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-since-trait) | ❌ | ❌ |
| [`smithy.api#tags`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-tags-trait) | ❌ | ❌ |
| [`smithy.api#title`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-title-trait) | ❌ | ❌ |
| [`smithy.api#unstable`](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-unstable-trait) | ❌ | ❌ |

The `@documentation` trait is emitted on operation stubs (client and server) and on types
output. On types, shape docs appear above records, enums, unions, error shapes, and
preamble type aliases. Documented structure members appear as field-level comments in
Erlang and in a Members section of the shape `@moduledoc` in Elixir. Shapes without the
trait keep existing fallback module or type header strings.

---

## Behavior Traits

Traits that define operation behavior.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#paginated`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-paginated-trait) | ✅ | ✅ |
| [`smithy.api#idempotencyToken`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-idempotencytoken-trait) | ❌ | ❌ |
| [`smithy.api#idempotent`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-idempotent-trait) | ❌ | ❌ |
| [`smithy.api#readonly`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-readonly-trait) | ❌ | ❌ |
| [`smithy.api#requestCompression`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-requestcompression-trait) | ❌ | ❌ |
| [`smithy.api#retryable`](https://smithy.io/2.0/spec/behavior-traits.html#smithy-api-retryable-trait) | ✅ | ✅ |

---

## Resource Traits

Traits for modeling resources and attaching operations to resource shapes.

**Client and server codegen** emit per-resource lifecycle helper modules for bound create,
read, update, delete, list, and collection operations. Helpers build operation input from
identifier arguments and delegate to the flat operation stubs. Property and reference traits
remain metadata only.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#nestedProperties`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-nestedproperties-trait) | ➖ | ➖ |
| [`smithy.api#noReplace`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-noreplace-trait) | ➖ | ➖ |
| [`smithy.api#notProperty`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-notproperty-trait) | ➖ | ➖ |
| [`smithy.api#property`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-property-trait) | ➖ | ➖ |
| [`smithy.api#references`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-references-trait) | ➖ | ➖ |
| [`smithy.api#resourceIdentifier`](https://smithy.io/2.0/spec/resource-traits.html#smithy-api-resourceidentifier-trait) | ➖ | ➖ |

---

## Authentication Traits

Traits for authentication schemes.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#auth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-auth-trait) | ❌ | ❌ |
| [`smithy.api#authDefinition`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-authdefinition-trait) | ❌ | ❌ |
| [`smithy.api#httpApiKeyAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-httpapikeyauth-trait) | ❌ | ❌ |
| [`smithy.api#httpBasicAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-httpbasicauth-trait) | ❌ | ❌ |
| [`smithy.api#httpBearerAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-httpbearerauth-trait) | ❌ | ❌ |
| [`smithy.api#httpDigestAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-httpdigestauth-trait) | ❌ | ❌ |
| [`smithy.api#optionalAuth`](https://smithy.io/2.0/spec/authentication-traits.html#smithy-api-optionalauth-trait) | ❌ | ❌ |

---

## Streaming Traits

Traits for streaming data.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#eventHeader`](https://smithy.io/2.0/spec/streaming.html#smithy-api-eventheader-trait) | ❌ | ❌ |
| [`smithy.api#eventPayload`](https://smithy.io/2.0/spec/streaming.html#smithy-api-eventpayload-trait) | ❌ | ❌ |
| [`smithy.api#requiresLength`](https://smithy.io/2.0/spec/streaming.html#smithy-api-requireslength-trait) | ❌ | ❌ |
| [`smithy.api#streaming`](https://smithy.io/2.0/spec/streaming.html#smithy-api-streaming-trait) | ✅ | ✅ |

---

## Endpoint Traits

Traits for endpoint configuration.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#endpoint`](https://smithy.io/2.0/spec/endpoint-traits.html#smithy-api-endpoint-trait) | ❌ | ❌ |
| [`smithy.api#hostLabel`](https://smithy.io/2.0/spec/endpoint-traits.html#smithy-api-hostlabel-trait) | ❌ | ❌ |

---

## Model Validation Traits

Traits for model validation rules.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.api#suppress`](https://smithy.io/2.0/spec/model-validation.html#smithy-api-suppress-trait) | ➖ | ➖ |
| [`smithy.api#trait`](https://smithy.io/2.0/spec/model.html#smithy-api-trait-trait) | ➖ | ➖ |
| [`smithy.api#traitValidators`](https://smithy.io/2.0/spec/model-validation.html#smithy-api-traitvalidators-trait) | ➖ | ➖ |

---

## AWS Core Traits (`aws.api#*`)

AWS service metadata traits.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`aws.api#service`](https://smithy.io/2.0/aws/aws-core.html#aws-api-service-trait) | ❌ | ❌ |
| [`aws.api#clientDiscoveredEndpoint`](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientdiscoveredendpoint-trait) | ❌ | ❌ |
| [`aws.api#clientEndpointDiscovery`](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientendpointdiscovery-trait) | ❌ | ❌ |
| [`aws.api#clientEndpointDiscoveryId`](https://smithy.io/2.0/aws/aws-core.html#aws-api-clientendpointdiscoveryid-trait) | ❌ | ❌ |
| [`aws.api#arn`](https://smithy.io/2.0/aws/aws-core.html#aws-api-arn-trait) | ➖ | ➖ |
| [`aws.api#arnReference`](https://smithy.io/2.0/aws/aws-core.html#aws-api-arnreference-trait) | ➖ | ➖ |
| [`aws.api#controlPlane`](https://smithy.io/2.0/aws/aws-core.html#aws-api-controlplane-trait) | ➖ | ➖ |
| [`aws.api#data`](https://smithy.io/2.0/aws/aws-core.html#aws-api-data-trait) | ➖ | ➖ |
| [`aws.api#dataPlane`](https://smithy.io/2.0/aws/aws-core.html#aws-api-dataplane-trait) | ➖ | ➖ |
| [`aws.api#tagEnabled`](https://smithy.io/2.0/aws/aws-core.html#aws-api-tagenabled-trait) | ➖ | ➖ |
| [`aws.api#taggable`](https://smithy.io/2.0/aws/aws-core.html#aws-api-taggable-trait) | ➖ | ➖ |

---

## AWS API Gateway Traits (`aws.apigateway#*`)

Traits for Amazon API Gateway integration.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`aws.apigateway#apiKeySource`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-apikeysource-trait) | ➖ | ➖ |
| [`aws.apigateway#authorizer`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-authorizer-trait) | ➖ | ➖ |
| [`aws.apigateway#authorizers`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-authorizers-trait) | ➖ | ➖ |
| [`aws.apigateway#integration`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-integration-trait) | ➖ | ➖ |
| [`aws.apigateway#mockIntegration`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-mockintegration-trait) | ➖ | ➖ |
| [`aws.apigateway#requestValidator`](https://smithy.io/2.0/aws/amazon-apigateway.html#aws-apigateway-requestvalidator-trait) | ➖ | ➖ |

---

## AWS CloudFormation Traits (`aws.cloudformation#*`)

Traits for CloudFormation resource generation. Not applicable to current BEAM codegen.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`aws.cloudformation#cfnAdditionalIdentifier`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnadditionalidentifier-trait) | ➖ | ➖ |
| [`aws.cloudformation#cfnDefaultValue`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfndefaultvalue-trait) | ➖ | ➖ |
| [`aws.cloudformation#cfnExcludeProperty`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnexcludeproperty-trait) | ➖ | ➖ |
| [`aws.cloudformation#cfnMutability`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnmutability-trait) | ➖ | ➖ |
| [`aws.cloudformation#cfnName`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnname-trait) | ➖ | ➖ |
| [`aws.cloudformation#cfnResource`](https://smithy.io/2.0/aws/aws-cloudformation.html#aws-cloudformation-cfnresource-trait) | ➖ | ➖ |

---

## AWS Endpoints Traits (`aws.endpoints#*`)

Traits for endpoint resolution rules.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`aws.endpoints#dualStackOnlyEndpoints`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-dualstackonlyendpoints-trait) | ➖ | ➖ |
| [`aws.endpoints#endpointsModifier`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-endpointsmodifier-trait) | ➖ | ➖ |
| [`aws.endpoints#rulesBasedEndpoints`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-rulesbasedendpoints-trait) | ➖ | ➖ |
| [`aws.endpoints#standardPartitionalEndpoints`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-standardpartitionalendpoints-trait) | ➖ | ➖ |
| [`aws.endpoints#standardRegionalEndpoints`](https://smithy.io/2.0/aws/aws-endpoints-region.html#aws-endpoints-standardregionalendpoints-trait) | ➖ | ➖ |

---

## AWS IAM Traits (`aws.iam#*`)

Traits for IAM policy generation. Not applicable to current BEAM codegen.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`aws.iam#actionName`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-actionname-trait) | ➖ | ➖ |
| [`aws.iam#actionPermissionDescription`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-actionpermissiondescription-trait) | ➖ | ➖ |
| [`aws.iam#conditionKeyValue`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-conditionkeyvalue-trait) | ➖ | ➖ |
| [`aws.iam#conditionKeys`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-conditionkeys-trait) | ➖ | ➖ |
| [`aws.iam#defineConditionKeys`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-defineconditionkeys-trait) | ➖ | ➖ |
| [`aws.iam#disableConditionKeyInference`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-disableconditionkeyinference-trait) | ➖ | ➖ |
| [`aws.iam#iamAction`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-iamaction-trait) | ➖ | ➖ |
| [`aws.iam#iamResource`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-iamresource-trait) | ➖ | ➖ |
| [`aws.iam#requiredActions`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-requiredactions-trait) | ➖ | ➖ |
| [`aws.iam#serviceResolvedConditionKeys`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-serviceresolvedconditionkeys-trait) | ➖ | ➖ |
| [`aws.iam#supportedPrincipalTypes`](https://smithy.io/2.0/aws/aws-iam.html#aws-iam-supportedprincipaltypes-trait) | ➖ | ➖ |

---

## Additional Specs

Traits from additional Smithy specifications.

### AI Traits (`smithy.ai#*`)

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.ai#prompts`](https://smithy.io/2.0/additional-specs/ai-traits.html#smithy-ai-prompts-trait) | ➖ | ➖ |

### MQTT Traits (`smithy.mqtt#*`)

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.mqtt#publish`](https://smithy.io/2.0/additional-specs/mqtt.html#smithy-mqtt-publish-trait) | ➖ | ➖ |
| [`smithy.mqtt#subscribe`](https://smithy.io/2.0/additional-specs/mqtt.html#smithy-mqtt-subscribe-trait) | ➖ | ➖ |
| [`smithy.mqtt#topicLabel`](https://smithy.io/2.0/additional-specs/mqtt.html#smithy-mqtt-topiclabel-trait) | ➖ | ➖ |

### OpenAPI Traits (`smithy.openapi#*`)

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.openapi#specificationExtension`](https://smithy.io/2.0/guides/model-translations/converting-to-openapi.html#smithy-openapi-specificationextension-trait) | ➖ | ➖ |

### Protocol Traits (`smithy.protocols#*`)

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.protocols#rpcv2Cbor`](https://smithy.io/2.0/additional-specs/protocols/smithy-rpc-v2.html#smithy-protocols-rpcv2cbor-trait) | ❌ | ❌ |

### Rules Engine Traits (`smithy.rules#*`)

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.rules#clientContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-clientcontextparams-trait) | ➖ | ➖ |
| [`smithy.rules#contextParam`](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-contextparam-trait) | ➖ | ➖ |
| [`smithy.rules#endpointBdd`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html#smithy-rules-endpointbdd-trait) | ➖ | ➖ |
| [`smithy.rules#endpointRuleSet`](https://smithy.io/2.0/additional-specs/rules-engine/specification.html#smithy-rules-endpointruleset-trait) | ➖ | ➖ |
| [`smithy.rules#operationContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-operationcontextparams-trait) | ➖ | ➖ |
| [`smithy.rules#staticContextParams`](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-staticcontextparams-trait) | ➖ | ➖ |

### Test Traits (`smithy.test#*`)

[HTTP protocol compliance tests](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html) define expected wire requests and responses. The current repository does not emit test modules from these traits.

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.test#httpMalformedRequestTests`](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html#smithy-test-httpmalformedrequesttests-trait) | ➖ | ➖ |
| [`smithy.test#httpRequestTests`](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html#smithy-test-httprequesttests-trait) | ❌ | ❌ |
| [`smithy.test#httpResponseTests`](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html#smithy-test-httpresponsetests-trait) | ❌ | ❌ |
| [`smithy.test#smokeTests`](https://smithy.io/2.0/additional-specs/smoke-tests.html#smithy-test-smoketests-trait) | ➖ | ➖ |

### Waiter Traits (`smithy.waiters#*`)

| Trait | Erlang | Elixir |
|-------|--------|--------|
| [`smithy.waiters#waitable`](https://smithy.io/2.0/additional-specs/waiters.html#smithy-waiters-waitable-trait) | ❌ | ❌ |

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
