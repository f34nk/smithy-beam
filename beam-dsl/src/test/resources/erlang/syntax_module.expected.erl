-moduledoc "Syntax coverage fixture for Erlang IR rendering.".
-module(syntax_fixture).
-include("syntax_types.hrl").
-export([
    with_spec_and_doc/1,
    with_spec_no_doc/1,
    no_spec_no_doc/1,
    decode/1,
    encode/1,
    decode_enum/1,
    encode_enum/1,
    decode_union/1,
    encode_union/1,
    dispatch/2,
    dispatch/3,
    match_patterns/1,
    transform/2,
    with_retry/2,
    risky_call/1,
    helpers/0
]).

-type config() :: #{binary() => term()}.

%% @doc Decode input with spec and documentation.
-spec decode(undefined | null | map()) -> undefined | #item{}.
decode(undefined) ->
    undefined;
decode(null) ->
    undefined;
decode(Map = #{}) when is_map(Map) ->
    #item{
        name = maps:get(<<"name">>, Map, undefined),
        count = maps:get(<<"count">>, Map, undefined)
    }.

-spec encode(item() | undefined) -> map() | undefined.
encode(undefined) ->
    undefined;
encode(Record) ->
    maps:filter(
        fun(_, V) -> (V =/= undefined) end,
        #{<<"name">> => Record#item.name, <<"count">> => Record#item.count}
    ).

no_spec_no_doc(undefined) -> undefined;
no_spec_no_doc(Value) -> Value.

%% @doc Return a plain map from a record.
-spec with_spec_and_doc(item()) -> map().
with_spec_and_doc(Record) -> #{name => Record#item.name, count => Record#item.count}.

-spec with_spec_no_doc(binary()) -> atom() | {unknown, binary()}.
with_spec_no_doc(<<"a">>) -> a;
with_spec_no_doc(<<"b">>) -> b;
with_spec_no_doc(V) when is_binary(V) -> {unknown, V}.

decode_enum(<<"a">>) -> a;
decode_enum(<<"b">>) -> b;
decode_enum(V) when is_binary(V) -> {unknown, V};
decode_enum(null) -> undefined;
decode_enum(undefined) -> undefined.

encode_enum(a) -> <<"a">>;
encode_enum(b) -> <<"b">>;
encode_enum(undefined) -> undefined.

decode_union(Map = #{}) ->
    case maps:to_list(Map) of
        [{<<"left">>, V}] -> {left, V};
        [{<<"right">>, V}] -> {right, V};
        [{K, _V}] -> {unknown, K};
        _ -> undefined
    end;
decode_union(undefined) ->
    undefined;
decode_union(null) ->
    undefined.

encode_union({left, V}) -> #{<<"left">> => V};
encode_union({right, V}) -> #{<<"right">> => V};
encode_union({unknown, K}) when is_binary(K) -> #{K => null};
encode_union(undefined) -> undefined.

dispatch(Config, Request) ->
    Handler = maps:get(handler, Config, ?DEFAULT_HANDLER),
    dispatch(Handler, Config, Request).

dispatch(Handler, _Config, #request{method = Method, path = Path}) when is_atom(Handler) ->
    {Handler, Method, Path}.

-spec match_patterns(term()) -> term().
match_patterns(undefined) ->
    undefined;
match_patterns(null) ->
    undefined;
match_patterns(#{<<"key">> := Value}) ->
    {map, Value};
match_patterns(#item{name = Name}) ->
    {record, Name};
match_patterns(#tagged_error{}) ->
    {error_record, true};
match_patterns({tag, Value}) ->
    {tuple, Value};
match_patterns([H | T]) when is_list(T) ->
    {list, H, length(T)};
match_patterns(<<_/binary>> = Bin) ->
    {binary, Bin};
match_patterns(V) when is_function(V, 1) ->
    V(syntax);
match_patterns(V) when is_integer(V) ->
    {int, V};
match_patterns(V) when is_atom(V) ->
    {atom, V}.

-spec transform(config(), request()) -> response().
transform(Config, #request{path = Path, query = Query, headers = Headers}) ->
    BaseUrl =
        case maps:get(base_url, Config, undefined) of
            undefined ->
                coalesce([
                    maps:get(endpoint, Config, undefined),
                    resolve_host(Config)
                ]);
            GivenUrl ->
                GivenUrl
        end,
    QueryStr =
        case maps:to_list(Query) of
            [] ->
                <<>>;
            Pairs ->
                Encoded = uri_string:compose_query([{K, V} || {K, V} <- Pairs]),
                <<"?", Encoded/binary>>
        end,
    {Scheme, Authority} = split_base_url(BaseUrl),
    ReqUrl = <<Scheme/binary, Authority/binary, Path/binary, QueryStr/binary>>,
    #response{
        status = 200,
        headers = Headers,
        body = ReqUrl
    }.

%% @doc Invoke {@code Fun} with exponential backoff on retryable errors.
-spec with_retry(fun(() -> term()), map()) -> term().
with_retry(Fun, Opts) ->
    Max = maps:get(max_attempts, Opts, 3),
    Base = maps:get(base_delay_ms, Opts, 100),
    with_retry(Fun, Max, Base, 1).

with_retry(Fun, 0, _, _) ->
    Fun();
with_retry(Fun, Attempts, Base, N) ->
    case Fun() of
        {ok, _} = Ok ->
            Ok;
        {error, _} = Err ->
            case retryable(Err) of
                true when Attempts > 1 ->
                    timer:sleep(trunc((Base * math:pow(2, (N - 1))))),
                    with_retry(Fun, (Attempts - 1), Base, (N + 1));
                _ ->
                    Err
            end
    end.

retryable({error, #tagged_error{}}) -> true;
retryable(_) -> false.

-spec risky_call(fun(() -> term())) -> term() | {error, term()}.
risky_call(Fun) ->
    try
        Fun()
    catch
        _:Reason -> {error, Reason}
    end.

-spec helpers() -> term().
helpers() ->
    Result =
        case fetch(<<"x">>) of
            {ok, Value} -> Value;
            error -> default
        end,
    Filtered = lists:filtermap(
        fun
            (V) when V =/= undefined -> {true, encode_value(V)};
            (_) -> false
        end,
        [Result]
    ),
    if
        length(Filtered) > 0 -> hd(Filtered);
        true -> undefined
    end.

fetch(Key) ->
    case maps:get(Key, #{}, undefined) of
        undefined -> error;
        V -> {ok, V}
    end.

encode_value(V) when is_boolean(V) -> atom_to_binary(V, utf8);
encode_value(V) when is_integer(V) -> integer_to_binary(V);
encode_value(V) when is_float(V) -> float_to_binary(V);
encode_value(V) when is_binary(V) -> V;
encode_value(V) when is_atom(V) -> atom_to_binary(V, utf8).

coalesce([H | Rest]) ->
    case H of
        undefined -> coalesce(Rest);
        <<>> -> coalesce(Rest);
        Value -> Value
    end;
coalesce([]) ->
    undefined.

resolve_host(Config) -> maps:get(host, Config, <<"localhost">>).

split_base_url(<<>>) ->
    {<<>>, <<>>};
split_base_url(BaseUrl) ->
    case uri_string:parse(binary_to_list(BaseUrl)) of
        Parts = #{scheme := Scheme, host := Host} ->
            PortSuffix =
                case maps:get(port, Parts, undefined) of
                    undefined -> <<>>;
                    Port -> <<":", (integer_to_binary(Port))/binary>>
                end,
            {<<(list_to_binary(Scheme))/binary, "://">>, <<(list_to_binary(Host))/binary,
                PortSuffix/binary>>};
        _ ->
            {<<>>, BaseUrl}
    end.

parse_header([$[ | _] = Line) -> string:trim(Line);
parse_header(Line) -> string:trim(Line).

flatten_pairs(Map) when is_map(Map) ->
    lists:append([
        flatten_entry(<<Key/binary, ".", (integer_to_binary(I))/binary>>, V)
     || {I, {Key, V}} <- lists:enumerate(maps:to_list(Map)),
        V =/= undefined
    ]).

flatten_entry(_Key, undefined) -> [];
flatten_entry(Key, Value) when is_map(Value) -> [{Key, Value}];
flatten_entry(Key, Value) -> [{Key, Value}].

content_type_matches(Headers, Expected) ->
    case proplists:get_value(<<"content-type">>, Headers, undefined) of
        Expected -> true;
        <<_/binary>> = CT -> (ct_base(CT) =:= ct_base(Expected));
        _ -> false
    end.

ct_base(CT) ->
    case binary:split(CT, <<";">>) of
        [Base | _] -> Base;
        _ -> CT
    end.

update_request(Req, Host) ->
    Req#request{host = Host}.

dispatch_handler(Fun, Ctx, Input, Meta) ->
    Handlers = maps:get(?HANDLERS_KEY, #{}, #{}),
    case maps:get(Fun, Handlers, undefined) of
        Handler when is_function(Handler, 3) -> Handler(Ctx, Input, Meta);
        _ -> {error, not_implemented}
    end.

decode_sparse_map(undefined) ->
    undefined;
decode_sparse_map(Map) when is_map(Map) ->
    maps:map(
        fun
            (_K, null) -> undefined;
            (_K, V) -> V
        end,
        Map
    ).

decode_list(undefined) -> undefined;
decode_list(null) -> undefined;
decode_list(List) when is_list(List) -> [V || V <- List, V =/= null].

decode_json_body(<<>>) ->
    #{};
decode_json_body(Body) ->
    case jsone:try_decode(Body) of
        {ok, V, _} when is_map(V) -> V;
        _ -> #{}
    end.

merge_params(Config, Params) ->
    ConfigParams = config_to_params(Config),
    ClientParams = client_params(Config),
    maps:merge(maps:merge(ConfigParams, ClientParams), Params).

config_to_params(Config) ->
    case maps:get(region, Config, undefined) of
        undefined -> #{};
        Value -> #{<<"Region">> => Value}
    end.

client_params(Config) ->
    maps:merge(
        optional_param(Config, region, <<"Region">>),
        optional_param(Config, bucket, <<"Bucket">>)
    ).

optional_param(Config, Key, ParamKey) ->
    case maps:get(Key, Config, undefined) of
        undefined -> #{};
        Value -> #{ParamKey => Value}
    end.

prefix_headers_to_list(_Prefix, undefined) ->
    [];
prefix_headers_to_list(Prefix, Map) when is_map(Map) ->
    [{<<Prefix/binary, H/binary>>, to_binary(V)} || {H, V} <- maps:to_list(Map)].

prefix_headers_from_list(Headers, Prefix) ->
    Map = maps:from_list([
        {binary:part(Name, byte_size(Prefix)), Val}
     || {Name, Val} <- Headers,
        byte_size(Name) > byte_size(Prefix),
        binary:part(Name, 0, byte_size(Prefix)) =:= Prefix
    ]),
    case maps:size(Map) of
        0 -> undefined;
        _ -> Map
    end.

to_binary(V) when is_binary(V) -> V;
to_binary(V) when is_atom(V) -> atom_to_binary(V, utf8);
to_binary(V) when is_integer(V) -> integer_to_binary(V).
