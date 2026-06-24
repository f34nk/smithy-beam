decode_basic_item(undefined) -> undefined;
decode_basic_item(null) -> undefined;
decode_basic_item(Map) when is_map(Map) ->
    #basic_item{
        name = maps:get(<<"name">>, Map, undefined),
        count = maps:get(<<"count">>, Map, undefined)
    }.

encode_basic_item(undefined) -> undefined;
encode_basic_item(Record) -> maps:filter(fun (_, V) ->
    V =/= undefined
end, #{<<"name">> => Record#basic_item.name, <<"count">> => Record#basic_item.count}).
