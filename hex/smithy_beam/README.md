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

Default Maven dependencies always include
`io.github.f34nk.smithy.beam:codegen-elixir` or `codegen-erlang` plus common Smithy
AWS-related artifacts. Consumer `maven_deps` are appended.

## Versioning

Hex package version (`smithy_beam`) and Maven codegen versions are independent. Override the generator with `codegen_version` when needed.

Keep `@version` in `mix.exs` and `{vsn, ...}` in `src/smithy_beam.app.src` equal when releasing.

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

## Integrating a generated server

Server codegen emits a typed service contract and an HTTP router. It does **not** emit Plug, Bandit, Cowboy, or Ranch listeners. You generate with `kind: :server`, implement the behaviour, call `init_handlers/0` at application start, then adapt your HTTP stack to the generated router.

Module and file names come from the Smithy service name. For a service `BookstoreCatalog`, Elixir emits modules such as `BookstoreCatalogServer` and Erlang emits `bookstore_catalog_server.erl`.

Typical generated pieces (names vary by service and protocol):

| Role | Elixir | Erlang |
| --- | --- | --- |
| Types | `*Types` | `*_types.hrl` |
| Behaviour | `*Behaviour` | `*_behaviour.erl` |
| Server dispatcher | `*Server` | `*_server.erl` |
| HTTP router | `*Router` | `*_router.erl` |
| Protocol codec | e.g. `*RestJson1` | e.g. `*_rest_json_1.erl` |
| Runtime HTTP carriers | `RuntimeTypes` | `runtime_types.hrl` |

You write the impl module yourself. The server dispatcher looks for a default impl named `{Service}Impl` / `{service}_impl` that implements the behaviour.

### 1. Generate server code

Elixir (`mix.exs`): same as the client setup above, but `kind: :server`.

```elixir
smithy_beam: [
  models: "model",
  output: "lib/generated",
  language: :elixir,
  kind: :server,
  edition: "2026"
]
```

Erlang (`rebar.config`): same as the client setup above, but `{kind, server}`. Include generated headers:

```erlang
{erl_opts, [debug_info, {i, "src/generated"}]}.
{src_dirs, [{"src", [{recursive, true}]}]}.

{smithy_beam, [
  {models, "model"},
  {output, "src/generated"},
  {language, erlang},
  {kind, server},
  {edition, "2026"}
]}.
```

Add JSON deps your codec needs (commonly `jason` on Elixir, `jsone` on Erlang).

### 2. Implement the behaviour

Each operation becomes `handle_<operation>/3` with `(ctx, input, meta)`. Return `{:ok, output}` / `{ok, Output}` or an error.

Elixir:

```elixir
defmodule BookstoreCatalogImpl do
  @behaviour BookstoreCatalogBehaviour

  @impl true
  def handle_get_book(_ctx, input, _meta) do
    {:ok, %BookstoreCatalogTypes.GetBookOutput{title: input.isbn}}
  end
end
```

Erlang:

```erlang
-module(bookstore_catalog_impl).
-behaviour(bookstore_catalog_behaviour).

-include("bookstore_catalog_types.hrl").

-export([handle_get_book/3]).

handle_get_book(_Ctx, Input, _Meta) ->
    {ok, #get_book_output{title = Input#get_book_input.isbn}}.
```

### 3. Register handlers at startup

Call once before serving traffic (application `start/2`, supervision init, or test helper):

```elixir
:ok = BookstoreCatalogServer.init_handlers()
```

```erlang
ok = bookstore_catalog_server:init_handlers().
```

`init_handlers/0` loads callbacks from the default impl into persistent term. Without it, router calls through the server module return `{error, not_implemented}`.

### 4. Wire your HTTP stack to the router

Build a `RuntimeTypes.HttpRequest` / `#http_request{}` from the inbound connection (`method`, `path`, `query`, `headers`, `body`), then dispatch:

```elixir
req = %RuntimeTypes.HttpRequest{
  method: method,
  path: path,
  query: query_map,
  headers: headers,
  body: body
}

case BookstoreCatalogRouter.dispatch(BookstoreCatalogServer, req) do
  {:ok, output} ->
    resp = BookstoreCatalogRestJson1.encode_get_book_response(output)
    # resp.status, resp.headers, resp.body -> send via Plug / Bandit / cowboy_req

  {:error, {:not_found, _method, _path}} ->
    # 404

  {:error, reason} ->
    # map modeled errors with codec encode_*_response helpers when applicable
end
```

```erlang
Req = #http_request{
    method = Method,
    path = Path,
    query = QueryMap,
    headers = Headers,
    body = Body
},
case bookstore_catalog_router:dispatch(bookstore_catalog_server, Req) of
    {ok, Output} ->
        Resp = bookstore_catalog_rest_json_1:encode_get_book_response(Output),
        %% Resp#http_response.status/headers/body -> cowboy_req:reply/4 or similar
        {ok, Resp};
    {error, {not_found, _Method, _Path}} ->
        {error, not_found};
    {error, Reason} ->
        {error, Reason}
end.
```

`Router.dispatch/2` decodes the wire request, calls `handle_<op>/3`, and returns the handler result. Encoding the success (or error) payload into an HTTP response is a separate codec call that you perform in the adapter.

You may also pass any module that exports the `handle_<op>/3` functions directly to `dispatch/2` (useful in tests) instead of the generated `*Server` module.
