# Changelog

All notable changes to **smithy-beam** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Erlang server runtime modules** in `runtime-erlang/server/`: three new modules providing HTTP dispatch helpers, input validation, and error mapping as the runtime foundation for generated Erlang server dispatchers.
  - **`smithy_server`**: HTTP abstraction layer — `extract/1` reads method, path, headers, and body from a Cowboy request; `response/2`, `error_response/1`, `validation_error/1`, and `not_found/0` return framework-agnostic `{StatusCode, Headers, Body}` tuples consumed by the generated dispatcher and the application's Cowboy handler.
  - **`smithy_validator`**: required-field validation — `validate/2` checks that all required binary-keyed fields are present in an input map and returns `ok` or `{error, {missing_required_fields, [binary()]}}`. `format/1` converts the error term to a human-readable binary message (`<<"Missing required fields: …">>`).
  - **`smithy_error_map`**: error-to-HTTP mapping — `to_http/1` maps Smithy error atoms and tuples (`{not_found, Msg}`, `{conflict, Msg}`, `{unauthorized, Msg}`, etc.) to `{HttpStatusCode, MessageBinary}` pairs. Serves as the generic fallback for generated per-service overrides that add modeled `@httpError` shapes.
- **Unit tests** for all three Erlang server runtime modules in `runtime-erlang/server/test/`, verified with `rebar3 eunit`. Includes `cowboy_req.erl` — a test-only mock accepting plain maps — so `smithy_server:extract/1` can be exercised without a live Cowboy server.
- **Test harness improvement** (`Makefile`, `test/runtime-erlang` target): adds `jsx` as an eunit dependency, redirects all output to `erlang-runtime-test.log`, and detects failures and syntax errors via `grep` with a clear exit code and log reference.

- **`ErlangReservedWords`** in `codegen-erlang` (`symbol` package): complete set of Erlang reserved words with `isReserved()` and `escape()` helpers; reserved words are escaped by appending `_`.
- **`ErlangSymbolProvider`** in `codegen-erlang` (`symbol` package): converts Smithy names to Erlang identifiers — `toModuleName`/`toFunctionName` (PascalCase → snake_case), `toTypeName` (snake_case with trailing `()`), `toVarName` (PascalCase for Erlang variable syntax), and internal `toSnakeCase`/`toPascalCase` helpers. New `toAtomTag(name)` static helper converts member names to Erlang atom tags, wrapping Erlang reserved words in single quotes (e.g. `'and'`, `'end'`) so they remain syntactically valid.
- **`ErlangWriter`** in `codegen-erlang`: full `LanguageWriter` implementation replacing the empty stub.
  - Module structure: `moduleHeader` (`-module`), `exportSection` (multiline `-export`, one entry per line), `exportTypes` (multiline `-export_type`), `renderModuleComment` (`%% Generated Smithy client for <Service>`), `behaviourDeclaration` (`-behaviour`), `moduleFooter` (empty — Erlang has no footer).
  - Type rendering: `renderStructType` (Erlang map type with `=>` for all fields; required annotation enforced at call sites via `validate_*` helpers), `renderEnumType` (union of lowercase atoms, e.g. `celsius | fahrenheit`), `renderUnionType` (tagged tuples), `renderCallbackDeclaration` (`-callback`), `renderFunctionSpec` (`-spec`), `renderFunctionHead`, `renderFunctionEnd`.
  - Map operations: `renderMapGet` (`maps:get/3` with binary key), `renderMapBuild` (map literal with binary keys).
  - Serialization: `renderJsonEncode`/`renderJsonDecode` (`jsx`), `renderXmlEncode`/`renderXmlDecode` (`aws_xml`), `renderFormEncode` (`aws_query`).
  - HTTP: `renderUriSubstitution` (Erlang binary string with label interpolation and optional `url_encode`), `renderQueryStringBuilder` (`uri_string:compose_query` with `ensure_binary/1` coercion for all values), `renderHeaderBuilder` (protocol-correct `Content-Type` base header with numbered accumulator variables `Headers0`, `Headers1`, …; literal `X-Amz-Target` value emitted for AWS JSON operations), `renderHttpClientBlock` (`httpc:request/4` with success/error pattern matching).
  - Auth and retry: `renderAuthWrapper` (prepends SigV4 signing via `aws_sigv4:sign_request` when required; no-op otherwise), `renderRetryWrapper` (`aws_retry:with_retry`).
  - Response and errors: `renderResponseHandler` (generates `deserialize_<op>/1` extracting body members), `renderModuleParseError` (protocol-aware — see **Fixed** below).
  - Pagination: `renderPaginationHelper` (generates `<op>_stream/2,3` that loops, accumulates items, and threads the output token back as input).
  - Runtime name helpers: `jsonEncodeCall`, `jsonDecodeCall`, `sigv4SignCall`, `retryCall`.
- **`HeaderBinding.literalValue`** field in `codegen-core/ir`: when non-null, the header value is a hard-coded string constant emitted verbatim rather than read from the caller's `Input` map. Used for `X-Amz-Target` in AWS JSON protocols.
- **`OperationSpec`** extended with five new fields in `codegen-core/ir`: `outputTypeName`, `inputTypeName`, `responseEncoding`, `protocolContentType` (wire `Content-Type`, e.g. `"application/x-amz-json-1.0"`), and `protocolErrorStrategy` (one of `REST_XML`, `AWS_JSON`, `REST_JSON`, `AWS_QUERY`). Writers use these to emit protocol-correct wire code without re-analyzing the model.
- **`LanguageWriter`** interface extended in `codegen-core/writer`: new default and abstract methods — `renderModuleComment`, `renderToolingAttributes`, `exportTypes`, `renderClientConstructor`, `renderClientOperation`, `renderEnumCodec`, `renderUnionCodec`, `renderValidateHelper`, `renderSharedHelpers`, `clientRuntimeModules`, and a protocol-aware overload `renderModuleParseError(errors, ErrorCodeStrategy)`.
- **`ClientPipeline`** in `codegen-core`: fully implemented end-to-end Erlang client module generation.
  - Emits `-module`, `-export` (multiline), `-export_type` (multiline), and `-dialyzer` attributes, plus a module-level comment.
  - Renders all struct/enum/union/error type definitions in order.
  - Generates `new/1` constructor with `-spec` annotation.
  - Generates a 3-function block per operation: 2-arity public wrapper, 3-arity options/retry wrapper, and an internal `make_<op>_request/2` that builds the full URL (URI label substitution + query strings, with S3-specific `aws_s3:build_url` for S3 services), constructs the request body (JSON or form-encoded; direct `jsx:encode(Input)` for AWS JSON protocols), sets headers with the protocol-correct `Content-Type`, conditionally signs with SigV4, calls `httpc:request/4`, and decodes the response or dispatches to `parse_error/2`.
  - Generates `url_encode/1` and `ensure_binary/1` internal helpers.
  - Generates `encode_<enum>/1` and `decode_<enum>/1` helpers using lowercase atom representation for enum values.
  - Generates `encode_<union>/1` and `decode_<union>/1` helpers using nested `maps:find` dispatch for all union types.
  - Generates `validate_<struct>/1` helpers restricted to operation input types (not all structs).
  - Generates a single module-level `parse_error/2` function, deduplicated by Smithy error name across all operations, with protocol-specific dispatch and return format.
  - Conditionally copies `aws_sigv4.erl` and `aws_credentials.erl` runtime modules when any operation requires SigV4; always copies `aws_retry.erl` and `aws_config.erl`.
- **`ServerPipeline`** skeleton in `codegen-core`.
- **`FileOutput.forPlugin(outputDir)`** factory in `codegen-core`: filesystem-mode `FileOutput` that writes generated files directly to `<cwd>/<outputDir>` on disk rather than Smithy's internal build cache. Runtime resources copied via `copyRuntime` are written using only their base filename (directory segments in the resource path are stripped).
- **`ErlangClientPlugin`** in `codegen-erlang`: Smithy Build plugin wired via `META-INF/services`; delegates to `ClientPipeline` and resolves the active protocol via `ProtocolAnalyzerFactory`. Generated files are written to the project's configured `outputDir`.
- **Erlang client runtime modules** in `runtime-erlang/client/`: `aws_sigv4`, `aws_credentials`, `aws_retry`, `aws_config`, `aws_xml`, `aws_query`, `aws_s3`, `aws_endpoints`; bundled into the `codegen-erlang` JAR under `META-INF/smithy-beam/runtime/erlang/` for `FileOutput.copyRuntime`.
- **`RestJsonProtocolAnalyzer`** in `codegen-protocols`: fully analyzes `aws.protocols#restJson1` client operations into `OperationSpec` IR — HTTP spec from `@http`, label/query/header/body bindings from input members, error bindings from `@httpError`, SigV4 auth from `@aws.auth#sigv4`, default retry, and pagination from `@paginated`. Also exposes `outputTypeName` and `inputTypeName` static helpers reused by other analyzers.
- **`AwsJsonProtocolAnalyzer`** in `codegen-protocols`: analyzes `aws.protocols#awsJson1_0` — all operations `POST /`, all input members in JSON body, literal `X-Amz-Target: <Service>.<Operation>` header per operation, `Content-Type: application/x-amz-json-1.0`, `ErrorCodeStrategy.AWS_JSON`.
- **`AwsJson11ProtocolAnalyzer`** in `codegen-protocols`: analyzes `aws.protocols#awsJson1_1` with `application/x-amz-json-1.1`; extends `AwsJsonProtocolAnalyzer`.
- **`AwsQueryProtocolAnalyzer`** in `codegen-protocols`: analyzes `aws.protocols#awsQuery` — `POST /` with `Content-Type: application/x-www-form-urlencoded`, all input members placed in the form body (the `Action` and `Version` parameters are added by `aws_query.erl` at runtime), `ErrorCodeStrategy.AWS_QUERY`. `requiresQueryRuntime()` returns `true`.
- **`Ec2QueryProtocolAnalyzer`** in `codegen-protocols`: analyzes `aws.protocols#ec2Query`; extends `AwsQueryProtocolAnalyzer` and overrides only the protocol `ShapeId`. EC2 member name title-casing and `@ec2QueryName` rewriting is performed by `aws_query.erl` at runtime.
- **`RestXmlProtocolAnalyzer`** in `codegen-protocols`: analyzes `aws.protocols#restXml` — same HTTP/label/query/header analysis as `RestJsonProtocolAnalyzer` but with `BodyEncoding.XML`, `Content-Type: application/xml`, and `ErrorCodeStrategy.REST_XML`. `requiresXmlRuntime()` returns `true`. `requiresS3Runtime(service)` returns `true` when the `aws.api#service` trait's `arnNamespace` starts with `"s3"`, triggering `aws_s3.erl` to be copied alongside `aws_xml.erl`.
- **`ProtocolAnalyzer.requiresS3Runtime(ServiceShape)`** default method in `codegen-core`: extension point that returns `false` by default; overridden by `RestXmlProtocolAnalyzer` to detect S3-like services.
- **`ProtocolRegistrations.init()`** in `codegen-protocols`: thread-safe, idempotent registration of all six built-in analyzers with `ProtocolAnalyzerFactory`.
- **`examples/erlang/weather-service`**: working end-to-end example using `aws.protocols#restJson1` — covers GET with `@httpLabel`, POST with JSON body, `@enum` with wire values, `@httpError` error shapes, and required-field validation. Includes `smithy-build.json`, `rebar.config`, `app.src`, and an EUnit test suite.
- **`examples/erlang/storage-service`**: working end-to-end example using `aws.protocols#restJson1` — covers union types, enum encode/decode round-trips, required-field validation, `parse_error/2` dispatch, and path labels. Includes `smithy-build.json`, `rebar.config`, `app.src`, and an EUnit test suite.
- **`examples/erlang/s3-demo`**: working end-to-end example using `aws.protocols#restXml` (Amazon S3) — covers XML body encoding, S3-specific URL building via `aws_s3:build_url`, SigV4 signing, multi-part header construction, `parse_error/2` dispatch by XML error code string, and `aws_s3.erl` / `aws_xml.erl` runtime modules. Includes `smithy-build.json`, `rebar.config`, `app.src`, Makefile, and Terraform config.
- **`examples/erlang/dynamodb-demo`**: working end-to-end example using `aws.protocols#awsJson1_0` (Amazon DynamoDB) — covers `Content-Type: application/x-amz-json-1.0`, literal `X-Amz-Target` header, direct `jsx:encode(Input)` body encoding, SigV4 signing, `parse_error/2` dispatch by JSON `__type` error code string, and structured `#{error_type => ..., message => ...}` error maps. Includes `smithy-build.json`, `rebar.config`, `app.src`, Makefile, and Terraform config.
- Unit tests for `RestJsonProtocolAnalyzer`, `AwsJsonProtocolAnalyzer`/`AwsJson11ProtocolAnalyzer`, `AwsQueryProtocolAnalyzer`, `Ec2QueryProtocolAnalyzer`, and `RestXmlProtocolAnalyzer` covering protocol ID, content type, HTTP spec, body encoding, binding classification, auth, error strategy, and S3 runtime detection. Test fixtures: `sqs.smithy` (awsQuery), `ec2.smithy` (ec2Query), `s3.smithy` (restXml — includes both S3 and CloudFront services).
- Unit test `ErlangClientPipelineTest` covering pipeline execution and JAR-bundled runtime resource copying.

### Fixed

- **`TypeSpecBuilder`**: `TypeRef.Named` instances are now built from `shape.getId().getName()` (the local shape name) instead of `shape.getId().toString()` (the fully-qualified Smithy ID including namespace). Previously, type references such as `example.weather#TemperatureUnit` were emitted verbatim into Erlang type specs, producing a syntax error.
- **`TypeSpecBuilder`**: `EnumSpec` values are now populated from `getEnumValues().values()` (wire string values, e.g. `"Celsius"`) rather than `keySet()` (member names, e.g. `"CELSIUS"`), so generated `encode_<enum>/1` and `decode_<enum>/1` helpers match the JSON wire format.
- **`ErlangWriter`** — `renderEnumType` / `renderEnumCodec`: enum values are now emitted as lowercase atoms (e.g. `celsius`) matching Erlang conventions; `encode_<enum>/1` functions no longer include an `Other` fallthrough clause.
- **`ErlangWriter`** — `renderStructType`: all map fields now use the `=>` operator; required fields are enforced at call sites via `validate_*` helpers rather than in type specs.  Member names that are Erlang reserved words (e.g. `and`, `end`) are wrapped in single quotes via `ErlangSymbolProvider.toAtomTag`.
- **`ErlangWriter`** — `appendHeaders`: header accumulation uses uniquely indexed variables (`Headers0`, `Headers1`, …) to satisfy Erlang's single-assignment rule; `Content-Type` is derived from `op.protocolContentType()` so each protocol emits its correct wire value; the literal `X-Amz-Target` value is emitted directly rather than being read from `Input`.
- **`ErlangWriter`** — `appendBody`: for AWS JSON protocols the body is `Body = jsx:encode(Input)` (full input map, no undefined-member filtering); `Body = <<>>` is only emitted when SigV4 is required and no body members are present.
- **`ErlangWriter`** — `appendQueryString`: all query parameter values are coerced through `ensure_binary/1` before being passed to `uri_string:compose_query/1`, preventing `badarg` crashes for integer members such as `MaxKeys`.
- **`ErlangWriter`** — `appendHttpcCall` and `renderModuleParseError`: protocol-aware error dispatch.
  - `REST_XML` and `AWS_JSON`: the error body is parsed (XML `<Code>` element or JSON `__type` field respectively); `parse_error/2` dispatches on the extracted binary error code string and returns `{error, #{error_type => atom, message => binary()}}` structured maps.
  - `REST_JSON` and `AWS_QUERY`: `parse_error/2` dispatches on the HTTP status code integer and returns `{error, {atom, Body}}` tuples.
  - `parse_error` clauses are deduplicated by Smithy error name across all operations (previously by HTTP status code, which conflated distinct errors sharing the same code).
- **`ClientPipeline`** — `validate_` scope: `validate_<struct>/1` helpers and their `-export` entries are now generated only for shapes that appear as an operation's input type, not for every struct in the model.
- **`ClientPipeline`** — `-export` / `-export_type` declarations are now emitted as multiline blocks (one entry per line) matching standard Erlang style.
- **`ErlangSymbolProvider`** — Erlang reserved words used as enum values or union variant names (e.g. `AND` → `and`) are quoted as `'and'` in generated code instead of being appended with `_`, so the original wire name is preserved.

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
