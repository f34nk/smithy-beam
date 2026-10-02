-module(smithy_beam_prv_generate).

-export([init/1, do/1, format_error/1]).

-define(PROVIDER, generate).
-define(NAMESPACE, smithy_beam).
-define(DEPS, [{default, app_discovery}]).

-define(CODEGEN_VERSION, "0.3.0").

-define(COMMON_MAVEN_DEPS, [
    "software.amazon.smithy:smithy-aws-traits:1.64.0",
    "software.amazon.smithy:smithy-aws-endpoints:1.64.0",
    "software.amazon.smithy:smithy-rules-engine:1.70.0",
    "software.amazon.smithy:smithy-aws-smoke-test-model:1.64.0",
    "software.amazon.smithy:smithy-aws-iam-traits:1.64.0",
    "software.amazon.smithy:smithy-waiters:1.64.0"
]).

-spec init(rebar_state:t()) -> {ok, rebar_state:t()}.
init(State) ->
    Provider = providers:create([
        {name, ?PROVIDER},
        {module, ?MODULE},
        {namespace, ?NAMESPACE},
        {bare, true},
        {deps, ?DEPS},
        {example, "rebar3 smithy_beam generate"},
        {opts, []},
        {short_desc, "Generate BEAM sources from Smithy models"},
        {desc, "Generate BEAM sources from Smithy models via the Smithy CLI"}
    ]),
    {ok, rebar_state:add_provider(State, Provider)}.

-spec do(rebar_state:t()) -> {ok, rebar_state:t()} | {error, string()}.
do(State) ->
    case rebar_state:get(State, smithy_beam, undefined) of
        undefined ->
            rebar_api:info("smithy_beam: no {smithy_beam, [...]} config; skipping", []),
            {ok, State};
        Cfg when is_list(Cfg) ->
            case generate(Cfg, State) of
                ok ->
                    {ok, State};
                {error, Reason} ->
                    {error, {?MODULE, Reason}}
            end
    end.

-spec format_error(any()) -> iolist().
format_error(Reason) ->
    io_lib:format("~s", [Reason]).

generate(Cfg, State) ->
    case os:find_executable("smithy") of
        false ->
            {error,
                "smithy_beam: Smithy CLI not found on PATH.\n"
                "Install: brew tap smithy-lang/tap && brew install smithy-cli\n"
                "Docs: https://smithy.io/2.0/guides/smithy-cli/cli_installation.html\n"
                "Verify with: smithy --help\n"};
        Smithy ->
            Root = rebar_dir:root_dir(State),
            Language = proplists:get_value(language, Cfg, erlang),
            Kind = proplists:get_value(kind, Cfg, client),
            Models = proplists:get_value(models, Cfg, "model"),
            Output = proplists:get_value(output, Cfg, "src/generated"),
            Edition = proplists:get_value(edition, Cfg),
            case Edition of
                undefined ->
                    {error, "smithy_beam: missing required {edition, \"...\"}"};
                _ ->
                    Plugin = plugin_id(Language, Kind),
                    CodegenVersion = proplists:get_value(codegen_version, Cfg, ?CODEGEN_VERSION),
                    ExtraDeps = proplists:get_value(maven_deps, Cfg, []),
                    Service = proplists:get_value(service, Cfg, undefined),
                    Name = proplists:get_value(name, Cfg, undefined),
                    ModelsAbs = filename:absname(Models, Root),
                    OutputAbs = filename:absname(Output, Root),
                    Scratch = filename:join(Root, "_build/smithy_beam"),
                    ScratchOut = filename:join(Scratch, "out"),
                    ok = filelib:ensure_dir(filename:join(Scratch, "dummy")),
                    ok = filelib:ensure_dir(filename:join(ScratchOut, "dummy")),
                    ConfigPath = filename:join(Scratch, "smithy-build.json"),
                    Json = build_json(Language, CodegenVersion, ExtraDeps, ModelsAbs, Plugin, Edition, Service, Name),
                    ok = file:write_file(ConfigPath, Json),
                    Cmd = string:join(
                        [
                            Smithy,
                            "build",
                            "--config",
                            ConfigPath,
                            "--output",
                            ScratchOut,
                            "--plugin",
                            Plugin
                        ],
                        " "
                    ),
                    rebar_api:info("smithy_beam: ~s", [Cmd]),
                    case run_cmd(Cmd, Root) of
                        {0, _} ->
                            copy_plugin_out(ScratchOut, Plugin, OutputAbs);
                        {Status, Out} ->
                            {error,
                                io_lib:format(
                                    "smithy_beam: smithy build failed (exit ~p)~n~s",
                                    [Status, Out]
                                )}
                    end
            end
    end.

plugin_id(elixir, client) -> "elixir-client-codegen";
plugin_id(elixir, server) -> "elixir-server-codegen";
plugin_id(elixir, types) -> "elixir-types-codegen";
plugin_id(erlang, client) -> "erlang-client-codegen";
plugin_id(erlang, server) -> "erlang-server-codegen";
plugin_id(erlang, types) -> "erlang-types-codegen";
plugin_id(L, K) ->
    erlang:error({unknown_language_kind, L, K}).

codegen_artifact(elixir) -> "codegen-elixir";
codegen_artifact(erlang) -> "codegen-erlang".

build_json(Language, CodegenVersion, ExtraDeps, ModelsAbs, Plugin, Edition, Service, Name) ->
    Primary =
        "io.github.f34nk.smithy.beam:" ++ codegen_artifact(Language) ++ ":" ++ CodegenVersion,
    Deps = unique([Primary | (?COMMON_MAVEN_DEPS ++ ExtraDeps)]),
    Settings0 = [{<<"edition">>, iolist_to_binary(Edition)}],
    Settings1 = maybe_setting(Settings0, <<"service">>, Service),
    Settings = maybe_setting(Settings1, <<"name">>, Name),
    Map = #{
        <<"version">> => <<"1.0">>,
        <<"sources">> => [iolist_to_binary(ModelsAbs)],
        <<"maven">> => #{
            <<"dependencies">> => [iolist_to_binary(D) || D <- Deps],
            <<"repositories">> => [#{<<"url">> => <<"https://repo1.maven.org/maven2">>}]
        },
        <<"plugins">> => #{
            iolist_to_binary(Plugin) => maps:from_list(Settings)
        }
    },
    encode_json(Map).

maybe_setting(Settings, _Key, undefined) ->
    Settings;
maybe_setting(Settings, Key, Value) ->
    [{Key, iolist_to_binary(Value)} | Settings].

unique(List) ->
    lists:reverse(
        lists:foldl(
            fun(E, Acc) ->
                case lists:member(E, Acc) of
                    true -> Acc;
                    false -> [E | Acc]
                end
            end,
            [],
            List
        )
    ).

copy_plugin_out(ScratchOut, Plugin, OutputAbs) ->
    Source = filename:join([ScratchOut, "source", Plugin]),
    case filelib:is_dir(Source) of
        false ->
            {error, io_lib:format("smithy plugin output not found: ~s", [Source])};
        true ->
            _ = file:del_dir_r(OutputAbs),
            ok = filelib:ensure_dir(filename:join(OutputAbs, "dummy")),
            {ok, Names} = file:list_dir(Source),
            lists:foreach(
                fun(Name) ->
                    From = filename:join(Source, Name),
                    To = filename:join(OutputAbs, Name),
                    case filelib:is_dir(From) of
                        true ->
                            ok = copy_dir(From, To);
                        false ->
                            {ok, _} = file:copy(From, To),
                            ok
                    end
                end,
                Names
            ),
            ok
    end.

copy_dir(From, To) ->
    ok = filelib:ensure_dir(filename:join(To, "dummy")),
    {ok, Names} = file:list_dir(From),
    lists:foreach(
        fun(Name) ->
            F = filename:join(From, Name),
            T = filename:join(To, Name),
            case filelib:is_dir(F) of
                true -> copy_dir(F, T);
                false -> {ok, _} = file:copy(F, T)
            end
        end,
        Names
    ),
    ok.

run_cmd(Cmd, Cwd) ->
    Port = open_port(
        {spawn, Cmd},
        [exit_status, stderr_to_stdout, {cd, Cwd}, binary, stream]
    ),
    collect(Port, []).

collect(Port, Acc) ->
    receive
        {Port, {data, Data}} ->
            collect(Port, [Data | Acc]);
        {Port, {exit_status, Status}} ->
            {Status, binary_to_list(iolist_to_binary(lists:reverse(Acc)))}
    end.

encode_json(Value) ->
    iolist_to_binary(encode_value(Value)).

encode_value(Map) when is_map(Map) ->
    Pairs = maps:to_list(Map),
    [
        ${,
        join(
            [
                [encode_string(K), $:, encode_value(V)]
             || {K, V} <- Pairs
            ],
            $,
        ),
        $}
    ];
encode_value(List) when is_list(List) ->
    [$[, join([encode_value(V) || V <- List], $,), $]];
encode_value(Bin) when is_binary(Bin) ->
    encode_string(Bin);
encode_value(Int) when is_integer(Int) ->
    integer_to_list(Int);
encode_value(true) ->
    "true";
encode_value(false) ->
    "false";
encode_value(null) ->
    "null".

encode_string(Bin) when is_binary(Bin) ->
    [$", escape(binary_to_list(Bin)), $"].

escape([]) ->
    [];
escape([$" | Rest]) ->
    [$\\, $" | escape(Rest)];
escape([$\\ | Rest]) ->
    [$\\, $\\ | escape(Rest)];
escape([C | Rest]) ->
    [C | escape(Rest)].

join([], _Sep) ->
    [];
join([H], _Sep) ->
    [H];
join([H | T], Sep) ->
    [H, Sep | join(T, Sep)].
