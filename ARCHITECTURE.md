# Architecture

**smithy-beam** is a [Smithy](https://smithy.io/) code generator targeting BEAM languages (**Erlang**, **Elixir**, **Gleam**). 

It is designed to generate idiomatic client and server code from Smithy models.

The generator is implemented in **Java** following the official [Codegen guidelines](https://smithy.io/2.0/guides/index.html).

Language plugins live under `codegen-*`. Shared protocol analyzers live in `protocol-analyzer/`. Re-usable runtime modules and examples live under `runtime-*` and `examples/`.

Please refer to [TRAITS](https://github.com/f34nk/smithy-beam/blob/v1/TRAITS.md) and [AWS_SDK_SUPPORT](https://github.com/f34nk/smithy-beam/blob/v1/AWS_SDK_SUPPORT.md) for a full list of supported features.

> Erlang client and server generators are fully supported.
> Elixir: `ElixirWriter` is fully implemented; Smithy Build plugin registration is coming soon.
> Gleam is coming soon.

```shell
.
├── codegen-core // shared generator logic
├── codegen-erlang // Erlang plugin and code writer
├── codegen-elixir // Elixir plugin and code writer
├── protocol-analyzer // protocol-specific operation analyzers (shared across language plugins)
├── runtime-erlang // re-usable Erlang modules
│   ├── client
│   └── server
├── runtime-elixir // re-usable Elixir modules
│   ├── client
│   └── server
└── examples
    ├── elixir
    └── erlang
```

## Smithy Model DSL

A smithy model, for example `weather.smithy`, consists of a `namespace` and a `service` shape.

A `resource` is contained within a `service` or another `resource`. Resources have identifiers, operations, and any number of child resources.

See [quickstart](https://smithy.io/2.0/quickstart.html) for more.

```smithy
$version: "2"
namespace example.weather

use aws.protocols#restJson1

@restJson1
service Weather {
    version: "1.0"
    operations: [
        GetWeather
    ]
}

@readonly
@http(method: "GET", uri: "/weather/{city}", code: 200)
operation GetWeather {
    input: GetWeatherInput
    output: GetWeatherOutput
    errors: [WeatherServiceError]
}

@input
structure GetWeatherInput {
    @required
    @httpLabel
    city: String
}

@output
structure GetWeatherOutput {
    temperature: Float
    unit: TemperatureUnit
}

/// Temperature unit enum.
enum TemperatureUnit {
    CELSIUS    = "Celsius"
    FAHRENHEIT = "Fahrenheit"
}

/// Returned when the weather service encounters an error.
@error("client")
@httpError(400)
structure WeatherServiceError {
    @required
    message: String
    code: String
}
```

## Build Configuration

The build configuration is used to describe how a model is created and what projections of the model to create.

In this example, we will generate an Erlang `erlang-server-codegen` server module `src/generated/weather_service_server.erl` from the model inside `./model`.

The service is identified by `example.weather#Weather`.

The runtime modules are copied into the `scaffoldDir`, which is defined as `./src`.

See [using smithy-build.json](https://smithy.io/2.0/guides/smithy-build-json.html) for more.

```json
{
  "version": "1.0",
  "sources": ["model"],
  "maven": {
    "dependencies": [
      "software.amazon.smithy:smithy-aws-traits:1.53.0",
      "io.smithy.beam:codegen-erlang:0.1.0"
    ],
    "repositories": [
      {
        "url": "https://repo1.maven.org/maven2"
      }
    ]
  },
  "plugins": {
    "erlang-server-codegen": {
      "service": "example.weather#WeatherService",
      "module": "weather_service",
      "outputDir": "src/generated",
      "scaffoldDir": "src"
    }
  }
}
```

Generate code:

```shell
smithy build
```

## Code Generation

The entry point is a Smithy Build plugin (e.g. `ErlangClientPlugin`). Smithy loads and validates the model, then calls `execute(PluginContext)`.

### 1. Detect the protocol

`ProtocolDetector` inspects the service shape for a known protocol trait (e.g. `aws.protocols#restJson1`). `ProtocolAnalyzerFactory` resolves the matching `ProtocolAnalyzer` registered via Java `ServiceLoader`.

### 2. Analyze operations

For each operation in the service closure, the analyzer calls `analyzeClientOperation` (or `analyzeServerOperation`). It reads the Smithy model — HTTP spec, input/output members, trait bindings (`@httpLabel`, `@httpHeader`, `@httpQuery`, `@httpPayload`, `@required`, `@paginated`, `@aws.auth#sigv4`, etc.) — and produces an `OperationSpec` as an intermediate representation.

A **client** `OperationSpec` captures what the *caller* needs:
outgoing HTTP method, body serialisation, SigV4 signing, retry config.

A **server** `OperationSpec` would capture what the *handler* needs:
incoming request parsing, response serialisation, routing metadata.

Type shapes (structs, enums, unions, errors) are converted to `StructSpec` / `EnumSpec` / `UnionSpec` by `TypeSpecBuilder`.

### 3. Run the pipeline

`ClientPipeline` (or `ServerPipeline`) iterates the collected spec. For every item it calls the appropriate `LanguageWriter` method — e.g. `renderStructType`, `renderFunctionSpec`, `renderHttpClientBlock` — which emits target-language source text into a `CodeBuffer`. The pipeline contains no language-specific string literals; all target-language text is owned by the writer.

### 4. Map Smithy types to target-language types

Inside the writer, `ErlangSymbolProvider` / `ElixirSymbolProvider` converts Smithy names to idiomatic identifiers:

**Example:**


| Smithy           | Erlang                                                    | Elixir                  |
| ---------------- | --------------------------------------------------------- | ----------------------- |
| `PascalCaseName` | `pascal_case_name` (module) / `pascal_case_name()` (type) | `PascalCaseName`        |
| struct member    | `<<"member_name">>` binary key                            | `:member_name` atom key |
| enum value       | lowercase atom (`celsius`)                                | atom (`celsius`)        |
| union variant    | tagged tuple (`{ok, Value}`)                              | tagged tuple            |
| reserved word    | quoted atom (`'end'`)                                     | —                       |


### 5. Write output files

`FileOutput` writes each `CodeBuffer` to the configured `outputDir`. In plugin mode (`FileOutput.forPlugin`) files are written directly to the filesystem; in test mode a Smithy `FileManifest` is used instead.

### 6. Copy runtime modules

After source files are written, the pipeline calls `writer.clientRuntimeModules()` (or `serverRuntimeModules()`). `FileOutput.copyRuntime` extracts the listed resource paths from the JAR (bundled under `META-INF/smithy-beam/runtime/<lang>/`) and copies them into the output directory alongside the generated files. Which modules are copied depends on the protocol and auth requirements (e.g. `aws_sigv4.erl` only when SigV4 is required; `aws_xml.erl` only for XML protocols).
