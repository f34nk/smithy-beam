# smithy-beam architecture

**smithy-beam** is a [Smithy](https://smithy.io/) code generation project targeting **Erlang** and **Elixir** clients and servers. Codegen is implemented in **Java** (Smithy Build plugins and libraries under `codegen-*`). Hand-written runtimes and examples live under `runtime-*` and `examples/`.

---

## Current state

**`codegen-core`** provides shared **IR** (immutable records under `io.smithy.beam.core.ir`), the **`LanguageWriter`** and **`ProtocolAnalyzer`** interfaces, **codegen settings** and **file output** helpers, model utilities (`UriTemplate`, `ShapeIndex`, `TypeSpecBuilder`, `ProtocolDetector`, `ProtocolAnalyzerFactory`), and the **`ClientPipeline`** / **`ServerPipeline`** orchestrators.

**`codegen-protocols`** implements six protocol analyzers that fully populate `OperationSpec` IR from the Smithy model:

| Analyzer | Protocol | Notes |
|---|---|---|
| `RestJsonProtocolAnalyzer` | `aws.protocols#restJson1` | Full HTTP binding analysis; end-to-end examples |
| `AwsJsonProtocolAnalyzer` | `aws.protocols#awsJson1_0` | `POST /`, JSON body, `X-Amz-Target` header |
| `AwsJson11ProtocolAnalyzer` | `aws.protocols#awsJson1_1` | Extends `AwsJsonProtocolAnalyzer` |
| `AwsQueryProtocolAnalyzer` | `aws.protocols#awsQuery` | `POST /`, form-encoded body, `requiresQueryRuntime` |
| `Ec2QueryProtocolAnalyzer` | `aws.protocols#ec2Query` | Extends `AwsQueryProtocolAnalyzer` |
| `RestXmlProtocolAnalyzer` | `aws.protocols#restXml` | Full HTTP binding, XML body, `requiresXmlRuntime`; S3 detection via `requiresS3Runtime` |

`ProtocolRegistrations.init()` registers all six at plugin startup.

**`codegen-erlang`** ships `ErlangClientPlugin` (a working Smithy Build plugin registered via `META-INF/services`), `ErlangWriter` (fully implemented — all `LanguageWriter` methods emit real Erlang text), `ErlangSymbolProvider` (Smithy name → Erlang identifier conversions, including `toAtomTag` for reserved-word-safe atom tags), `ErlangReservedWords` (reserved-word detection and escaping), and eight Erlang client runtime modules bundled in the JAR so `FileOutput.copyRuntime` can copy them into the build output.

The `ErlangWriter` covers: module header, multiline `-export` / `-export_type` declarations, module-level comment, `-behaviour`, struct/enum/union type declarations (enums as lowercase atoms; struct fields all use `=>`), function specs and callback declarations, map operations, JSON/XML/form serialization, URI substitution, protocol-aware query-string and header builders (with `ensure_binary/1` coercion and indexed accumulator variables), `httpc` request blocks, SigV4 auth and retry wrappers, response handlers, protocol-aware `parse_error/2` error dispatchers (string-dispatch for REST_XML and AWS_JSON; status-code-dispatch for REST_JSON and AWS_QUERY), and pagination stream helpers.

**`ClientPipeline`** is fully implemented and drives end-to-end Erlang client generation. For each service it emits: module/export/type-export attributes, all type definitions (structs, enums, unions, error shapes), a `new/1` constructor, a 3-function block per operation (2-arity wrapper → 3-arity retry wrapper → internal `make_<op>_request/2` with URL construction, body building with protocol-correct `Content-Type`, optional `aws_s3:build_url` for S3 services, optional SigV4, `httpc` call, and protocol-aware response/error decoding), enum/union encode–decode helpers, required-field `validate_*` functions (restricted to operation input types), `url_encode/1`, `ensure_binary/1`, and a unified `parse_error/2` deduplicated by Smithy error name. Runtime modules are copied selectively based on the protocol and auth requirements. All language-specific string emission is delegated to `LanguageWriter`; `ClientPipeline` contains no target-language literals.

`FileOutput` supports two modes: **manifest mode** (for tests, delegates to Smithy `FileManifest`) and **filesystem mode** (for plugins, via `FileOutput.forPlugin(outputDir)`, writes directly to the project source tree).

Working **examples** under `examples/erlang/` demonstrate the full `smithy build` → Erlang compilation → Dialyzer → EUnit pipeline:
- **`weather-service`** — `restJson1` with GET + path label, enum, POST + JSON body, error shape.
- **`storage-service`** — `restJson1` with union types, enums, required-field validation, and path labels.
- **`s3-demo`** — `restXml` with XML body encoding, S3-specific URL building (`aws_s3:build_url`), SigV4 signing, and `parse_error/2` dispatch by XML error code string.
- **`dynamodb-demo`** — `awsJson1_0` with `Content-Type: application/x-amz-json-1.0`, literal `X-Amz-Target` header, direct `jsx:encode(Input)` body, SigV4 signing, and `parse_error/2` dispatch by JSON `__type` error code string.

**Server-side codegen** is not yet implemented.

---

## Pipeline

1. **Model** — Smithy semantic model (`software.amazon.smithy:smithy-model`) is the source of truth.
2. **Protocol analysis** — Per-protocol **analyzers** (in `codegen-protocols`, depending on `codegen-core`) map services and operations to **IR** (`io.smithy.beam.core.ir`).
3. **Pipeline** — Build plugins orchestrate analysis, then **writers** emit Erlang and/or Elixir sources.
4. **Runtime** — Erlang and Elixir modules under `runtime-erlang` and `runtime-elixir` support generated code (HTTP, auth, etc.).

Custom protocol traits are expected to integrate via **Java `ServiceLoader`**, with registrations under each codegen module’s `src/main/resources/META-INF/services/`.

---

## Gradle modules

| Module | Role |
|--------|------|
| **codegen-core** | Shared IR, `LanguageWriter` / `ProtocolAnalyzer`, model helpers (`TypeSpecBuilder`, …), `CodegenSettings`, `FileOutput`. Depends on Smithy model and build APIs. |
| **codegen-protocols** | Protocol analyzers and AWS-oriented traits. Depends on `codegen-core` and Smithy AWS traits. |
| **codegen-erlang** | Erlang writer, symbols, client/server plugins. Depends on `codegen-core` and `codegen-protocols`. |
| **codegen-elixir** | Elixir writer, symbols, client/server plugins. Same dependency pattern as Erlang. |

Maven coordinates: **`io.smithy.beam`** (see root `build.gradle.kts`). **`./gradlew publishToMavenLocal`** publishes all four JARs to the local Maven repository.

---

## Repository layout (not exhaustive)

```text
smithy-beam/
├── codegen-core/          # Java: IR, writer/protocol interfaces, settings, output, model
├── codegen-protocols/     # Java: protocol analyzers
├── codegen-erlang/        # Java: Erlang codegen + META-INF/services
├── codegen-elixir/        # Java: Elixir codegen + META-INF/services
├── runtime-erlang/        # Erlang sources (client / server)
├── runtime-elixir/        # Elixir sources (client / server)
    └── examples/
    └── erlang/
        ├── weather-service/   # restJson1: GET+label, enum, POST body, error
        ├── storage-service/   # restJson1: union types, enum, validation, path label
        ├── s3-demo/           # restXml: XML body, S3 URL building, SigV4, XML error dispatch
        └── dynamodb-demo/     # awsJson1_0: X-Amz-Target, jsx body, SigV4, __type error dispatch
```

---

## Related documentation

- **`TRAITS.md`** — Smithy trait inventory vs. support in **generated** Erlang/Elixir.
- **`AWS_SDK_SUPPORT.md`** — AWS-oriented features vs. support in **generated** clients.
- **`CHANGELOG.md`** — Release history.
