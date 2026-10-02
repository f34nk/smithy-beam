# smithy_beam

Mix and rebar3 helper that runs the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html) to generate Elixir and Erlang code from Smithy models.

Generation runs at **compile time** in the consumer project (not while compiling this package as a dependency).

## Prerequisites

Install the Smithy CLI:

- Docs: https://smithy.io/2.0/guides/smithy-cli/cli_installation.html
- macOS: `brew tap smithy-lang/tap && brew install smithy-cli`

Verify with `smithy --help`.

## Elixir (Mix)

```elixir
def project do
  [
    compilers: [:smithy_beam] ++ Mix.compilers(),
    elixirc_paths: ["lib", "lib/generated"],
    smithy_beam: [
      models: "model",
      output: "lib/generated",
      language: :elixir,
      kind: :client,
      edition: "2026"
    ],
    deps: [
      {:smithy_beam, "~> 0.1", runtime: false}
    ]
  ]
end
```

Or run explicitly:

```shell
mix smithy_beam.generate
```

## Erlang (rebar3)

```erlang
{plugins, [smithy_beam]}.

{provider_hooks, [
  {pre, [{compile, {smithy_beam, generate}}]}
]}.

{smithy_beam, [
  {models, "model"},
  {output, "src/generated"},
  {language, erlang},
  {kind, client},
  {edition, "2026"}
]}.
```

Or run explicitly:

```shell
rebar3 smithy_beam generate
```

## Notes

- Hex package version and Maven codegen artifact version are independent.
- Extra Maven coordinates can be appended with `maven_deps` / `{maven_deps, [...]}`.
- Whether generated sources are committed is a consumer choice.
