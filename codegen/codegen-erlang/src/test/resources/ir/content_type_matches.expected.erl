content_type_matches(Headers, Expected) ->
    case proplists:get_value(<<"Content-Type">>, Headers, undefined) of
        Expected -> true;
        <<_/binary>> = CT -> ct_base(CT) =:= ct_base(Expected);
        _ -> false
    end.