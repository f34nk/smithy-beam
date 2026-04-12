# Changelog

All notable changes to **smithy-beam** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **`ErlangReservedWords`** in `codegen-erlang` (`symbol` package): complete set of Erlang reserved words with `isReserved()` and `escape()` helpers; reserved words are escaped by appending `_`.
- **`ErlangSymbolProvider`** in `codegen-erlang` (`symbol` package): converts Smithy names to Erlang identifiers — `toModuleName`/`toFunctionName` (PascalCase → snake_case), `toTypeName` (snake_case with trailing `()`), `toVarName` (PascalCase for Erlang variable syntax), and internal `toSnakeCase`/`toPascalCase` helpers.
- **`ErlangWriter`** in `codegen-erlang`: full `LanguageWriter` implementation replacing the empty stub.
  - Module structure: `moduleHeader` (`-module`), `exportSection` (`-export`), `behaviourDeclaration` (`-behaviour`), `moduleFooter` (empty — Erlang has no footer).
  - Type rendering: `renderStructType` (Erlang map type with `:=` for required fields and `=>` for optional), `renderEnumType` (union of quoted atoms), `renderUnionType` (tagged tuples), `renderCallbackDeclaration` (`-callback`), `renderFunctionSpec` (`-spec`), `renderFunctionHead`, `renderFunctionEnd`.
  - Map operations: `renderMapGet` (`maps:get/3` with binary key), `renderMapBuild` (map literal with binary keys).
  - Serialization: `renderJsonEncode`/`renderJsonDecode` (`jsx`), `renderXmlEncode`/`renderXmlDecode` (`aws_xml`), `renderFormEncode` (`aws_query`).
  - HTTP: `renderUriSubstitution` (Erlang binary string with label interpolation and optional `url_encode`), `renderQueryStringBuilder` (`uri_string:compose_query`), `renderHeaderBuilder` (content-type base header with conditional member headers), `renderHttpClientBlock` (`httpc:request/4` with success/error pattern matching).
  - Auth and retry: `renderAuthWrapper` (prepends SigV4 signing via `aws_sigv4:sign_request` when required; no-op otherwise), `renderRetryWrapper` (`aws_retry:with_retry`).
  - Response and errors: `renderResponseHandler` (generates `deserialize_<op>/1` extracting body members), `renderErrorSerializer` (generates `parse_error/2` dispatching HTTP status codes to error atoms).
  - Pagination: `renderPaginationHelper` (generates `<op>_stream/2,3` that loops, accumulates items, and threads the output token back as input).
  - Runtime name helpers: `jsonEncodeCall`, `jsonDecodeCall`, `sigv4SignCall`, `retryCall`.
- **`ClientPipeline`** and **`ServerPipeline`** skeletons in `codegen-core`: orchestrate IR assembly, protocol analysis, and `LanguageWriter` rendering per service; `ClientPipeline` conditionally copies client runtime resources from the plugin JAR.
- **`ErlangClientPlugin`** in `codegen-erlang`: Smithy Build plugin wired via `META-INF/services`; delegates to `ClientPipeline` and resolves the active protocol via `ProtocolAnalyzerFactory`.
- **Erlang client runtime modules** in `runtime-erlang/client/`: `aws_sigv4`, `aws_credentials`, `aws_retry`, `aws_config`, `aws_xml`, `aws_query`, `aws_s3`, `aws_endpoints`; bundled into the `codegen-erlang` JAR under `META-INF/smithy-beam/runtime/erlang/` for `FileOutput.copyRuntime`.
- **`RestJsonProtocolAnalyzer`** in `codegen-protocols`: fully analyzes `aws.protocols#restJson1` client operations into `OperationSpec` IR — HTTP spec from `@http`, label/query/header/body bindings from input members, error bindings from `@httpError`, SigV4 auth from `@aws.auth#sigv4`, default retry, and pagination from `@paginated`.
- **`AwsJsonProtocolAnalyzer`** in `codegen-protocols`: analyzes `aws.protocols#awsJson1_0` — all operations `POST /`, all input members in JSON body, `X-Amz-Target` header per operation.
- **`AwsJson11ProtocolAnalyzer`** in `codegen-protocols`: analyzes `aws.protocols#awsJson1_1` with `application/x-amz-json-1.1`; extends `AwsJsonProtocolAnalyzer`.
- **`ProtocolRegistrations.init()`** in `codegen-protocols`: thread-safe, idempotent registration of all three built-in analyzers with `ProtocolAnalyzerFactory`.
- Unit tests for `RestJsonProtocolAnalyzer` and `AwsJsonProtocolAnalyzer`/`AwsJson11ProtocolAnalyzer` covering HTTP spec, binding classification, auth, pagination, and content types.
- Unit test `ErlangClientPipelineTest` covering pipeline execution and JAR-bundled runtime resource copying.

## [0.1.0] — 2026-04-12

### Added

- Gradle multi-project build (`codegen-core`, `codegen-protocols`, `codegen-erlang`, `codegen-elixir`) with Java 17, JUnit 5, AssertJ, and `publishToMavenLocal` via `maven-publish`.
- Version catalog for Smithy (`1.54.0`), Smithy AWS traits, and test libraries.
- Gradle wrapper (8.5) and GitHub Actions CI workflow.
- Source tree placeholders under `runtime-erlang`, `runtime-elixir`, and `examples` for future runtimes and demos.
- **Core IR** (`io.smithy.beam.core.ir`): primitive/binding enums, `TypeRef`, operation records (`OperationSpec`, HTTP/bindings, errors, auth, retry, pagination), and type records (`StructSpec`, `EnumSpec`, `UnionSpec`, `ModuleTypeSpec`, etc.).
- **Interfaces** (`io.smithy.beam.core.writer`, `io.smithy.beam.core.protocol`): `LanguageWriter`, `ProtocolAnalyzer`, supporting value types (`ExportSpec`, `MapEntrySpec`, `ParamSpec`), and `ProtocolAnalyzerFactory` for registering analyzers by protocol `ShapeId`.
- **Infrastructure** (`io.smithy.beam.core`): `CodegenException`; `CodegenSettings`; `CodeBuffer` and `FileOutput`; `UriTemplate`, `ShapeIndex`, `TypeSpecBuilder`, `ProtocolDetector`.
- Unit test: `TypeSpecBuilderTest` for `ModuleTypeSpec` assembly from a minimal Smithy model.
- Public documentation: `ARCHITECTURE.md`, `AWS_SDK_SUPPORT.md`, `TRAITS.md` (support matrices for AWS and traits vs. generated output).
