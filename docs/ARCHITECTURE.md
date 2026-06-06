# smithy-beam Architecture

Plugin layout, module boundaries, and design rationale.

---

## 1. Why Six Plugins

smithy-beam exposes six Smithy-Build plugins:

| Plugin name | Target language | Role |
|---|---|---|
| `erlang-types-codegen` | Erlang | Type header generation (`.hrl` records and type aliases) |
| `erlang-client-codegen` | Erlang | Client dispatch and serialization |
| `erlang-server-codegen` | Erlang | OTP behaviour callbacks and routing |
| `elixir-types-codegen` | Elixir | Type module generation (structs and type specs) |
| `elixir-client-codegen` | Elixir | Client dispatch and serialization |
| `elixir-server-codegen` | Elixir | GenServer callbacks and routing |

The [Smithy DirectedCodegen](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html) guidelines prescribe a separate Smithy-Build plugin for each use case, client, server, and types, backed by a shared Java implementation. smithy-beam packages type generation both ways: public type-only plugins for standalone use, and a reusable type-generation entry class per language module that client and server plugins call directly before emitting their own files. Shared settings and cross-cutting utilities live in `codegen-core`. Smithy-Build does not infer or add a matching types plugin from projection inheritance, plugin ordering, `runBefore`, or `runAfter`, so direct delegation is the stable way to avoid duplicate user configuration.

Client and server concerns are fundamentally different (see section 3), and Erlang and Elixir generate different idioms even though they share the BEAM VM (see section 4). Combining any two of these dimensions into one plugin would require the plugin to branch on configuration flags rather than being a self-contained, single-purpose code generator. Focused plugins keep each plugin's scope narrow and its output predictable.

All six plugins are discovered by Smithy-Build through the [Java Service Provider Interface](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html) (SPI). Each is named following the pattern `<language>-<type>-codegen` (see section 5).

---

## 2. Why codegen-core

The `codegen-core` Gradle submodule contains shared Java code used by all six plugins. It is not published as a Smithy-Build plugin itself and has no Java SPI plugin registration.

Shared components that live in `codegen-core`:

- **`BeamSettings`** — parses and validates the [smithy-build.json](https://smithy.io/2.0/guides/smithy-build-json.html) plugin configuration block that is common to all plugins (namespace, package version, edition, optional `protocol` on client and server plugins, etc.).
- **`BeamModelTransforms`** — applies pre-generation model transforms that are identical across all plugins (e.g. flattening mixins, applying protocol traits).
- **`BeamPreludeIntegration`** — the base [SmithyIntegration](https://smithy.io/2.0/guides/building-codegen/making-codegen-pluggable.html#creating-a-smithyintegrations) that registers shared interceptors; each language plugin extends this rather than re-implementing it.
- **`Mode`** — a sealed type (`CLIENT` / `SERVER`) used throughout the client/server generation pipeline to branch between client and server concerns without duplicating the surrounding logic. The types plugins do not use `Mode`; they call the language-specific type-generation entry class directly.

Type-generation entry classes **`ErlangTypeGeneration`** and **`ElixirTypeGeneration`** live in **`codegen-erlang`** and **`codegen-elixir`**. Each one configures [CodegenDirector](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html#running-directedcodegen-using-a-codegendirector) for the [DirectedCodegen](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html#directedcodegen) implementation of that language. The standalone types plugins call these classes, and later client/server plugins in the same language module call the same class before emitting client or server output. Type generation is implemented once per language and reused; it is not duplicated per plugin.

Without `codegen-core`, each plugin would duplicate shared settings, transforms, and integration scaffolding, causing drift and requiring wide fixes for every bug. The shared module is a compile-time dependency only; it adds no BEAM runtime dependency.

---

## 3. Client vs. Server Distinction

The [Smithy guide](https://smithy.io/2.0/guides/building-codegen/overview-and-concepts.html#client-generation) states directly: *servers are authoritative; clients must guard against backward-compatible changes.*

### Server plugins

- Generate [behaviour callback definitions](https://www.erlang.org/doc/system/design_principles.html#behaviours) (Erlang) or [@behaviour module declarations](https://hexdocs.pm/elixir/Behaviour.html) (Elixir) that model the service contract.
- Generate OTP callback router modules that dispatch incoming requests to the correct handler by operation name.
- Generate implementation stubs (empty callback implementations) that developers fill in.
- May assume they control the wire format and can enforce constraints strictly.

### Client plugins

- Generate dispatch modules that call remote operations by name.
- Generate serialization and deserialization code for every operation's request and response shapes.
- Must handle unknown enum values, unknown union variants, and additive schema changes gracefully; any value that does not match a known variant is carried through as an opaque term rather than rejected.
- Do not generate server-side routing or behaviour declarations.

Both sides generate all routing, serialization, and deserialization at codegen-time. No Smithy JARs or model files are needed at BEAM runtime (see section 7).

---

## 4. BEAM Target Rationale

Erlang and Elixir compile to BEAM bytecode and share the same runtime. They differ in syntax, standard library conventions, and tooling:

| Concern | Erlang | Elixir |
|---|---|---|
| Package manager | [rebar3](https://rebar3.org) | [Mix](https://hexdocs.pm/mix/Mix.html) |
| Module naming | `lowercase_module` atoms | `CamelCase` aliases |
| String type | [binary()](https://www.erlang.org/doc/system/data_types.html#bit-strings-and-binaries) | [String.t()](https://hexdocs.pm/elixir/String.html) |
| Concurrency abstraction | [gen_server behaviour](https://www.erlang.org/doc/design_principles/gen_server_concepts.html) | [GenServer behaviour](https://hexdocs.pm/elixir/GenServer.html) |
| Error conventions | `{ok, Value} \| {error, Reason}` tagged tuples | same, plus `!`-suffix bang variants |
| Header/type sharing | [.hrl include files](https://www.erlang.org/doc/system/modules.html) | type specs in `.ex` modules |

### Erlang type headers

Generated Erlang services emit one `{model}_types.hrl` per Smithy model file. All structure
records and named type aliases for that model live in this header and are included by client,
server, codec, and test modules.

This differs from Inaka's recommendation to avoid sharing record definitions across modules via
headers. smithy-beam keeps a single types file so client and server share the same Smithy shape
surface and Dialyzer types without duplicating record definitions per module.

A single plugin targeting "BEAM" and branching internally on a `language` flag would produce a plugin that is harder to reason about and harder to extend. Separate plugins with a shared core give each language its own [SymbolProvider](https://smithy.io/2.0/guides/building-codegen/mapping-shapes-to-languages.html), [SymbolWriter](https://smithy.io/2.0/guides/building-codegen/decoupling-codegen-with-symbols.html), and file-layout logic while sharing the parts that are truly identical.

---

## 5. Naming Conventions

### Plugin names

Plugin names follow `<language>-<type>-codegen` where `<type>` is one of `types`, `client`, or `server`:

```
erlang-types-codegen
erlang-client-codegen
erlang-server-codegen
elixir-types-codegen
elixir-client-codegen
elixir-server-codegen
```

Type-only generation lists a public types plugin:

```json
{
  "plugins": {
    "erlang-types-codegen": {
      "service": "com.example#MyService",
      "package": "my_service"
    }
  }
}
```

Client or server generation lists only the client or server plugin. The plugin
still emits the type files it needs by calling the matching type-generation
entry class for that language (for example `ErlangTypeGeneration` or `ElixirTypeGeneration`):

```json
{
  "plugins": {
    "elixir-client-codegen": {
      "service": "com.example#MyService",
      "package": "my_service"
    }
  }
}
```

If a projection lists both a standalone types plugin and a client or server
plugin, both paths must use the same language-specific type-generation entry
class and produce byte-identical type files. If the selected file-manifest layout
cannot support duplicate writes to the same artifact location, users should keep
the standalone types plugin and the client or server plugin in separate projections.

### Java packages

All Java source lives under `io.smithy.beam`:

| Gradle module | Java package root |
|---|---|
| `codegen-core` | `io.smithy.beam.core` |
| `codegen-erlang` | `io.smithy.beam.erlang` |
| `codegen-elixir` | `io.smithy.beam.elixir` |
| `codegen-test` | `io.smithy.beam.test` |

[SPI](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html) registration files live at `META-INF/services/software.amazon.smithy.build.SmithyBuildPlugin` inside each language module's JAR.

---

## 6. Generation Phases

Code generation passes through three phases, following the Smithy guide:

1. **Codegen-time** (Java on the JVM)
   - Smithy-Build loads the plugin from the classpath via [SPI](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html).
   - [CodegenDirector](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html#running-directedcodegen-using-a-codegendirector) orchestrates shape traversal.
   - [DirectedCodegen](https://smithy.io/2.0/guides/building-codegen/implementing-the-generator.html#directedcodegen) methods are called per shape type.
   - [SymbolProvider](https://smithy.io/2.0/guides/building-codegen/mapping-shapes-to-languages.html) maps each Smithy shape to a target-language symbol.
   - [SymbolWriter](https://smithy.io/2.0/guides/building-codegen/decoupling-codegen-with-symbols.html) renders source files from the symbol graph.
   - [SmithyIntegration](https://smithy.io/2.0/guides/building-codegen/making-codegen-pluggable.html#creating-a-smithyintegrations) hooks allow protocol-specific interceptors to modify the output. Language integrations may implement `createProtocolCodegen` to register custom `BeamProtocolCodegen` implementations for protocol traits.
   - Output: generated `.erl`/`.hrl` or `.ex` source files, plus `rebar.config` or `mix.exs`.

2. **Compile-time** ([rebar3](https://rebar3.org) or [Mix](https://hexdocs.pm/mix/Mix.html) on the developer's machine)
   - `rebar3 compile` or `mix compile` compiles the generated BEAM source.
   - [Dialyzer](https://www.erlang.org/doc/apps/dialyzer/dialyzer_chapter.html) / [Credo](https://hexdocs.pm/credo/overview.html) / type specs can be checked here.
   - No Java or Smithy tooling is required.

3. **Runtime** (BEAM VM)
   - Generated modules are loaded and executed by the BEAM.
   - No Smithy JARs, no model files, and no Java process are present or needed.

---

## 7. Model-Ignorant Generation

All routing, serialization, and deserialization logic is emitted as explicit BEAM source code during codegen-time. This means:

- The BEAM runtime never parses or interprets Smithy model files.
- Adding a new operation to a service requires re-running codegen and recompiling; the runtime code does not inspect the model dynamically.
- Generated code is auditable: a developer can read the `.erl` or `.ex` files and understand exactly what each operation does without consulting the model.

Any model metadata that must influence runtime behavior (e.g. HTTP binding information, auth schemes) is inlined as literals or data structures in the generated source, not loaded from external model artifacts.
