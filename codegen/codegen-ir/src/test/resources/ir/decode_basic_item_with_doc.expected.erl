%% @doc Decode a BasicItem from a JSON map.
-spec decode_basic_item(undefined | null | map()) -> undefined | #basic_item{}.
decode_basic_item(undefined) ->
    undefined;
decode_basic_item(null) ->
    undefined;
decode_basic_item(Map) when is_map(Map) ->
    #basic_item{
        name = maps:get(<<"name">>, Map, undefined),
        count = maps:get(<<"count">>, Map, undefined)
    }.
