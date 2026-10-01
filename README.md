## *WORK IN PROGRESS*

![smithy-beam](https://github.com/f34nk/smithy-beam/blob/v3/smithy-beam.png)

# smithy-beam

Code generator for the [Smithy](https://smithy.io/) interface modelling language. 

Support for **BEAM languages**: Erlang, Elixir, Gleam.

> This implementation follows the **"official"** [Creating a Code Generator](https://smithy.io/2.0/guides/building-codegen/index.html) guidelines.

## Build and Test

Prerequisites:
- Java 21+
- Gradle 8+
- [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/cli_installation.html)
- Erlang/OTP 24+
- rebar3 3+
- Elixir 1+

```shell
make build
make test
```

## Examples

```shell
make examples
```

## Releasing

`gradle.properties` on the branch stays on a SNAPSHOT version. Releases are
cut from GitHub Actions:

- Dry-run or manual publish: Actions -> Release -> Run workflow
  (`version`, `dry_run`).
- Production publish: tag the intended commit and push it:

```shell
git tag -a v0.2.0 -m "v0.2.0"
git push origin v0.2.0
```

The tag name must be `v` plus the Maven version (example: `v0.2.0` publishes
`0.2.0`). Tag pushes deploy for real; use workflow_dispatch with
`dry_run=true` to validate without deploying.
