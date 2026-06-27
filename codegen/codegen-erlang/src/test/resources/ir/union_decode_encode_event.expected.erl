decode_event(Map = #{}) ->
    case maps:to_list(Map) of
        [{<<"message">>, V}] -> {message, V};
        [{<<"code">>, V}] -> {code, V};
        [{K, _V}] -> {unknown, K};
        _ -> undefined
    end;
decode_event(undefined) ->
    undefined;
decode_event(null) ->
    undefined.

encode_event({message, V}) -> #{<<"message">> => V};
encode_event({code, V}) -> #{<<"code">> => V};
encode_event({unknown, K}) when is_binary(K) -> #{K => null};
encode_event(undefined) -> undefined.
