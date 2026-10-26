# Gleam types compile check

Optional harness that generates Gleam types from the shared basic Smithy model
and runs `gleam build`.

## Prerequisites

- Java 21 and the repo Gradle wrapper (always required for generation)
- [Gleam CLI](https://gleam.run/getting-started/installing/) on `PATH` (optional;
  without it, generation still works and `make build` skips the compile step)

Also publish the local Gleam codegen plugin once:

```shell
./gradlew :codegen:codegen-gleam:publishToMavenLocal
```

Smithy CLI must be installed to run `smithy build` in this directory.

## Usage

```shell
make generate   # smithy build + copy into src/
make build      # generate, then gleam build when Gleam CLI is available
```
