# smithy_beam

Mix and rebar3 helper that runs the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html) to generate Elixir and Erlang code from Smithy models.

Generation runs at **compile time** in the consumer project (not while compiling this package as a dependency).

## Prerequisites

Install the Smithy CLI:

- Docs: https://smithy.io/2.0/guides/smithy-cli/cli_installation.html
- macOS: `brew tap smithy-lang/tap && brew install smithy-cli`

Verify with `smithy --help`.

## Status

Package scaffold. Consumer setup docs will follow.
