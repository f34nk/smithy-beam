# smithy_beam

Hex package for the [smithy-beam](https://github.com/f34nk/smithy-beam) code generator.

Generates Elixir and Erlang code from [Smithy](https://smithy.io/) models (client and server).

Mix and rebar3 helper that runs the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html) to generate Elixir and Erlang code from Smithy models.

The Java codegen plugins are resolved from Maven Central. This Hex package is the BEAM-native compile-time glue.

Generation runs at **compile time** in the consumer project. Compiling `smithy_beam` itself as a dependency does not generate code.

## Prerequisites

1. [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/cli_installation.html) on `PATH`
   - macOS: `brew tap smithy-lang/tap && brew install smithy-cli`
   - Verify: `smithy --help`
2. A JVM that can load the published codegen JARs (Java 21+)
3. Network access on first build so the CLI can download Maven dependencies

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

Explicit task:

```shell
mix smithy_beam.generate
mix smithy_beam.generate --force
```

## Erlang (rebar3)

```erlang
{project_plugins, [smithy_beam]}.

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

Explicit task:

```shell
rebar3 smithy_beam generate
```

## Configuration reference

| Key | Required | Default | Notes |
| --- | --- | --- | --- |
| `models` | yes (unless `config_file`) | | Model file or directory |
| `output` | no | `lib/generated` / `src/generated` | Destination for generated sources |
| `language` | yes | | `:elixir` or `:erlang` |
| `kind` | yes | | `:client`, `:server`, or `:types` |
| `edition` | yes (auto config) | | Plugin edition, e.g. `"2026"` |
| `service` | no | | Shape id when the model has multiple services |
| `name` | no | | Optional module/file name stem |
| `codegen_version` | no | `0.3.1` | Maven codegen artifact version |
| `maven_deps` | no | `[]` | Extra GAV strings appended after defaults |
| `config_file` | no | | Escape hatch: use an existing `smithy-build.json` |
| `plugin` | with `config_file` | derived | Plugin id when using `config_file` |

Default Maven dependencies always include the matching `codegen-elixir` or `codegen-erlang` coordinate plus common Smithy AWS-related artifacts. Consumer `maven_deps` are appended.

## Versioning

Hex package version (`smithy_beam`) and Maven codegen versions are independent. Override the generator with `codegen_version` when needed.

## Generated sources and git

Whether `lib/generated` / `src/generated` is committed is a consumer choice.

## Custom HTTP clients

Generated clients dispatch HTTP through a pluggable adapter. By default Elixir uses `RuntimeHttpClient.Req` and Erlang uses `runtime_http_client_httpc` (OTP `httpc`). Override the adapter by setting `http_client` on the client config map passed into generated operations.

The adapter must implement the runtime HTTP client behaviour: one callback, `request/1`, that takes a built client request and returns an HTTP response or an error.

### Elixir

Implement `@behaviour RuntimeHttpClient`. The request is a `%RuntimeHttpClient.Request{}` (`method`, `url`, `headers`, `body`). Return `{:ok, %RuntimeTypes.HttpResponse{}}` or `{:error, reason}`.

```elixir
defmodule MyHttpClient do
  @behaviour RuntimeHttpClient

  alias RuntimeHttpClient.Request
  alias RuntimeTypes.HttpResponse

  @impl RuntimeHttpClient
  def request(%Request{method: method, url: url, headers: headers, body: body}) do
    # call your HTTP stack with method, url, headers, and body
    {:ok, %HttpResponse{status: 200, headers: [], body: ""}}
  end
end
```

Pass the module in config:

```elixir
config = %{
  base_url: "https://api.example",
  http_client: MyHttpClient
}

MyServiceClient.get_item(config, input)
```

### Erlang

Implement `-behaviour(runtime_http_client).` Include `runtime_http_client.hrl` and `runtime_types.hrl`. The request is `#http_client_request{}` (`method`, `url`, `headers`, `body`). Return `{ok, #http_response{}}` or `{error, Reason}`.

```erlang
-module(my_http_client).
-behaviour(runtime_http_client).

-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

-export([request/1]).

request(#http_client_request{
    method = Method,
    url = Url,
    headers = Headers,
    body = Body
}) ->
    %% call your HTTP stack with Method, Url, Headers, and Body
    {ok, #http_response{status = 200, headers = [], body = <<>>}}.
```


Pass the module in config:

```erlang
Config = #{
    base_url => <<"https://api.example">>,
    http_client => my_http_client
},
my_service_client:get_item(Config, Input).
```

Typical uses: swap in Finch, Gun, or another HTTP stack; inject mocks in tests; add tracing or custom TLS settings around the request.
