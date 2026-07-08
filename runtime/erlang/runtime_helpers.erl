%% Shared smithy-beam Erlang runtime helpers.
-module(runtime_helpers).
-export([
    parse_labels/2,
    headers_set/3,
    split_base_url/1,
    resolve_base_url/1
]).
-spec parse_labels(binary(), binary()) -> {ok, map()} | {error, path_mismatch}.
parse_labels(Path, Template) ->
    case match_segments(segments(Path), segments(Template), #{}) of
        {ok, Labels} -> {ok, Labels};
        error -> {error, path_mismatch}
    end.

segments(Path) -> Parts = binary:split(Path, <<"/">>, [global]),
[S || S <- Parts, S =/= <<>>].

match_segments([], [], Acc) ->
    {ok, Acc};
match_segments([Seg | RestPath], [TplSeg | RestTpl], Acc) ->
    case label_name(TplSeg) of
        {ok, Key} ->
            Val = uri_string:unquote(Seg),
            match_segments(RestPath, RestTpl, Acc#{Key => Val});
        error ->
            case (Seg =:= TplSeg) of
                true -> match_segments(RestPath, RestTpl, Acc);
                false -> error
            end
    end;
match_segments(_, _, _) ->
    error.

label_name(<<"{", Rest/binary>>) ->
    case binary:split(Rest, <<"}">>) of
        [Label | [<<>>]] -> {ok, Label};
        _ -> error
    end;
label_name(_) ->
    error.

headers_set(Name, Value, Headers) -> lists:keystore(Name, 1, Headers, {Name, Value}).

-spec resolve_base_url(map()) -> binary().
resolve_base_url(Config) ->
    Prefix = maps:get(endpoint_prefix, Config),
    Region = maps:get(region, Config, <<"us-east-1">>),
    <<"https://", Prefix/binary, ".", Region/binary, ".amazonaws.com">>.

split_base_url(<<>>) ->
    {<<>>, <<>>};
split_base_url(BaseUrl) ->
    case uri_string:parse(binary_to_list(BaseUrl)) of
        #{scheme := Scheme, host := Host} = Parts ->
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
