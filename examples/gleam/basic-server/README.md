# Gleam basic server

Generates Gleam restJson1 server modules (types, behaviour, impl, router, codec,
server adapter) from the shared basic Smithy model and compiles them with the
Gleam CLI.

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
make demo       # build, then gleam run (synthetic Request through handle_default)
```

## Pure router edge

Generated `handle/2` feeds a `gleam/http` Request into the pure router and
returns a Response. There is no embedded listener and no mist/wisp import.
Callers bind `handle` (or `handle_default`) from their HTTP stack.

Default impl stubs return not_implemented errors, so the demo expects
HTTP 500 until you replace handlers.

## Cross-language smoke

| Direction | How | Status |
|-----------|-----|--------|
| Gleam client -> Elixir/Erlang server | Point `examples/gleam/basic-client` at a listening restJson1 server for the same model | Manual; documented in the client example |
| Elixir/Erlang client -> Gleam server | Generate this example, wrap `handle/2` in an HTTP listener, call from Elixir/Erlang client | Gap: no packaged listener adapter yet; pure router path verified via `make demo` |

Automated cross-language CI is not required for this milestone. File wire bugs
found during manual smoke before protocol expansion work.
