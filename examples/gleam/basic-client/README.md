# Gleam basic client

Generates a Gleam restJson1 sans-io client from the shared basic Smithy model,
copies runtime helpers, and optionally builds or runs with the Gleam CLI.

## Prerequisites

- Java 21 and the repo Gradle wrapper
- Smithy CLI (`smithy build`)
- [Gleam CLI](https://gleam.run/getting-started/installing/) on `PATH` (optional;
  without it, generation still works and `make build` skips compile/run)

Publish the local Gleam codegen plugin once:

```shell
./gradlew :codegen:codegen-gleam:publishToMavenLocal
```

## Usage

```shell
make generate   # smithy build + copy generated modules into src/
make build      # generate, then gleam build when Gleam CLI is available
make demo       # build, then gleam run (httpc against localhost:8080)
```

## Sans-io flow

Generated operations expose `*_request` / `*_response` pairs. The demo builds a
request, sends with `gleam_httpc`, then parses the response. Transport errors are
expected if no server is listening.

## Cross-language smoke

| Direction | How |
|-----------|-----|
| Gleam client -> Elixir/Erlang server | Run a compatible restJson1 server for the same model, then `make demo` (edit `base_url` if the port differs). |
| Elixir/Erlang client -> Gleam server | See `examples/gleam/basic-server` (pure `handle/2`; listening adapter is caller-owned). |

Automated cross-language CI is not required for this milestone.
