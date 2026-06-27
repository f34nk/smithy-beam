parse_query_params(Body) ->
    maps:from_list(uri_string:dissect_query(Body)).

form_value(Params, Key) ->
    maps:get(Key, Params, undefined).

form_list_values_aws(Params, Key) ->
    Prefix = <<Key/binary, ".member.">>,
    indexed_form_values(Params, Prefix).

indexed_form_values(Params, Prefix) ->
    Entries = [
        {form_index(K, Prefix), maps:get(K, Params)}
     || K <- maps:keys(Params), binary:match(K, Prefix) =:= {0, byte_size(Prefix)}
    ],
    case lists:sort(Entries) of
        [] -> undefined;
        Sorted -> [V || {_, V} <- Sorted]
    end.

form_index(Key, Prefix) ->
    Rest = binary:part(Key, byte_size(Prefix), byte_size(Key) - byte_size(Prefix)),
    binary_to_integer(Rest).
