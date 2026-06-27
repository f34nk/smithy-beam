to_binary(V) when is_binary(V) -> V;
to_binary(V) when is_list(V) -> list_to_binary(V);
to_binary(true) -> <<"true">>;
to_binary(false) -> <<"false">>;
to_binary(V) when is_atom(V) -> atom_to_binary(V, utf8);
to_binary(V) when is_integer(V) -> integer_to_binary(V);
to_binary(V) when is_float(V) -> float_to_binary(V).

encode_query_value(V) when is_boolean(V) -> atom_to_binary(V, utf8);
encode_query_value(V) when is_integer(V) -> integer_to_binary(V);
encode_query_value(V) when is_float(V) -> float_to_binary(V);
encode_query_value(V) when is_binary(V) -> V;
encode_query_value(V) when is_atom(V) -> atom_to_binary(V, utf8).

uri_encode(Value) -> uri_string:quote(Value).

uri_decode(Value) when is_binary(Value) -> uri_string:unquote(Value);
uri_decode(undefined) -> undefined.

decode_query_param(undefined) -> undefined;
decode_query_param(<<"true">>) -> true;
decode_query_param(<<"false">>) -> false;
decode_query_param(V) when is_binary(V) -> V.

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

decode_json_body(<<>>) ->
    #{};
decode_json_body(Body) ->
    case jsone:try_decode(Body) of
        {ok, V, _} when is_map(V) -> V;
        _ -> #{}
    end.

content_type_matches(Headers, Expected) ->
    case proplists:get_value(<<"Content-Type">>, Headers, undefined) of
        Expected -> true;
        <<_/binary>> = CT -> ct_base(CT) =:= ct_base(Expected);
        _ -> false
    end.

ct_base(CT) ->
    case binary:split(CT, <<";">>) of
        [Base | _] -> Base;
        _ -> CT
    end.

decode_sparse_list(undefined) ->
    undefined;
decode_sparse_list(null) ->
    undefined;
decode_sparse_list(List) when is_list(List) ->
    [
        case V of
            null -> undefined;
            _ -> V
        end
     || V <- List
    ].

decode_list(undefined) -> undefined;
decode_list(null) -> undefined;
decode_list(List) when is_list(List) -> [V || V <- List, V =/= null].

decode_sparse_map(undefined) ->
    undefined;
decode_sparse_map(Map) when is_map(Map) ->
    maps:map(
        fun
            (_K, null) ->
                undefined;
            (_K, V) ->
                V
        end,
        Map
    ).

encode_sparse_list(undefined) ->
    null;
encode_sparse_list(List) when is_list(List) ->
    [
        case V of
            undefined -> null;
            _ -> V
        end
     || V <- List
    ].

encode_sparse_map(undefined) ->
    null;
encode_sparse_map(Map) when is_map(Map) ->
    maps:map(
        fun
            (_K, undefined) ->
                null;
            (_K, V) ->
                V
        end,
        Map
    ).

encode_timestamp_epoch_seconds({Mega, Secs, _Micro}) -> Mega * 1000000 + Secs;
encode_timestamp_epoch_seconds(undefined) -> undefined.

encode_timestamp_date_time({Mega, Secs, _Micro}) ->
    EpochSecs = Mega * 1000000 + Secs,
    {{Y, Mo, D}, {H, Mi, S}} = calendar:gregorian_seconds_to_datetime(EpochSecs + 62167219200),
    iolist_to_binary(
        io_lib:format("~4..0B-~2..0B-~2..0BT~2..0B:~2..0B:~2..0BZ", [Y, Mo, D, H, Mi, S])
    );
encode_timestamp_date_time(undefined) ->
    undefined.

decode_timestamp_epoch_seconds(null) ->
    undefined;
decode_timestamp_epoch_seconds(undefined) ->
    undefined;
decode_timestamp_epoch_seconds(V) when is_number(V) ->
    Mega = V div 1000000,
    Secs = V rem 1000000,
    {Mega, Secs, 0}.

decode_timestamp_date_time(null) ->
    undefined;
decode_timestamp_date_time(undefined) ->
    undefined;
decode_timestamp_date_time(V) when is_number(V) ->
    EpochSecs = trunc(V),
    Mega = EpochSecs div 1000000,
    {Mega, EpochSecs rem 1000000, 0};
decode_timestamp_date_time(V) when is_binary(V) ->
    try
        <<Y:4/binary, "-", Mo:2/binary, "-", D:2/binary, "T", H:2/binary, ":", Mi:2/binary, ":",
            S:2/binary, _/binary>> = V,
        Dt = {{binary_to_integer(Y), binary_to_integer(Mo), binary_to_integer(D)}, {
            binary_to_integer(H), binary_to_integer(Mi), binary_to_integer(S)
        }},
        GregorianSecs = calendar:datetime_to_gregorian_seconds(Dt),
        EpochSecs = GregorianSecs - 62167219200,
        Mega = EpochSecs div 1000000,
        {Mega, EpochSecs rem 1000000, 0}
    catch
        _:_ -> undefined
    end.

generate_uuid() -> list_to_binary(uuid:to_string(uuid:v4())).
