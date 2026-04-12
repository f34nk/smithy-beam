# smithy-beam architecture

**smithy-beam** is a [Smithy](https://smithy.io/) code generation project targeting **Erlang** and **Elixir** clients and servers. Codegen is implemented in **Java** (Smithy Build plugins and libraries under `codegen-*`). Hand-written runtimes and examples live under `runtime-*` and `examples/`.

---

## Current state

**`codegen-core`** provides shared **IR** (immutable records under `io.smithy.beam.core.ir`), the **`LanguageWriter`** and **`ProtocolAnalyzer`** interfaces, **codegen settings** and **file output** helpers, model utilities (`UriTemplate`, `ShapeIndex`, `TypeSpecBuilder`, `ProtocolDetector`, `ProtocolAnalyzerFactory`), and the **`ClientPipeline`** / **`ServerPipeline`** orchestrators.

**`codegen-protocols`** implements three protocol analyzers that fully populate `OperationSpec` IR from the Smithy model: `RestJsonProtocolAnalyzer` (`aws.protocols#restJson1`), `AwsJsonProtocolAnalyzer` (`aws.protocols#awsJson1_0`), and `AwsJson11ProtocolAnalyzer` (`aws.protocols#awsJson1_1`). `ProtocolRegistrations.init()` registers all three at plugin startup.

**`codegen-erlang`** ships `ErlangClientPlugin` (a working Smithy Build plugin registered via `META-INF/services`), `ErlangWriter` (stub — all render methods return empty strings), and eight Erlang client runtime modules bundled in the JAR so `FileOutput.copyRuntime` can copy them into the build output.

End-to-end **writer rendering** (Erlang/Elixir source emission) and **server-side codegen** are not yet implemented; the pipeline skeletons and writer stub are in place for the next phase.

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
└── examples/              # Smithy models + demos (erlang / elixir)
```

---

## Related documentation

- **`TRAITS.md`** — Smithy trait inventory vs. support in **generated** Erlang/Elixir.
- **`AWS_SDK_SUPPORT.md`** — AWS-oriented features vs. support in **generated** clients.
- **`CHANGELOG.md`** — Release history.
