# smithy-beam architecture

**smithy-beam** is a [Smithy](https://smithy.io/) code generation project targeting **Erlang** and **Elixir** clients and servers. It is implemented in **Java** (Smithy Build plugins and codegen) with runtimes and examples under `runtime-*` and `examples/`.

---

## High-level pipeline (target)

1. **Model** — Smithy semantic model (`software.amazon.smithy:smithy-model`) is the source of truth.
2. **Protocol analysis** — Per-protocol **analyzers** (in `codegen-protocols`, depending on `codegen-core`) map services and operations to an internal **IR** (records under `io.smithy.beam.core.ir`).
3. **Pipeline** — Build plugins orchestrate analysis, then **writers** emit Erlang and/or Elixir sources.
4. **Runtime** — Hand-written Erlang and Elixir modules in `runtime-erlang` and `runtime-elixir` support generated code (HTTP, auth, etc.).

Custom or third-party protocol traits are intended to integrate via **Java `ServiceLoader`** (SPI), with service files under each codegen module’s `src/main/resources/META-INF/services/`.

---

## Gradle modules

| Module | Role |
|--------|------|
| **codegen-core** | Shared IR, pipeline interfaces, model helpers, settings, file output utilities. Depends on Smithy model and build APIs. |
| **codegen-protocols** | Protocol analyzers and AWS-oriented traits. Depends on `codegen-core` and Smithy AWS traits. |
| **codegen-erlang** | Erlang writer, symbols, client/server plugins. Depends on `codegen-core` and `codegen-protocols`. |
| **codegen-elixir** | Elixir writer, symbols, client/server plugins. Same dependency pattern as Erlang. |

Coordinates: **`io.smithy.beam`** (see root `build.gradle.kts`). **`./gradlew publishToMavenLocal`** publishes all four JARs to the local Maven repository.

---

## Repository layout (not exhaustive)

```text
smithy-beam/
├── codegen-core/          # Java: core IR, pipeline, writers (shared)
├── codegen-protocols/     # Java: protocol analyzers
├── codegen-erlang/        # Java: Erlang codegen + META-INF/services
├── codegen-elixir/        # Java: Elixir codegen + META-INF/services
├── runtime-erlang/        # Erlang sources (client / server)
├── runtime-elixir/        # Elixir sources (client / server)
└── examples/              # Smithy models + demos (erlang / elixir)
```

---

## Related documentation

- **`TRAITS.md`** — Smithy trait inventory vs. planned codegen behavior.
- **`AWS_SDK_SUPPORT.md`** — AWS SDK–style features vs. planned support.
- **`CHANGELOG.md`** — Release history.
