decode_json_body(<<>>) ->
    #{};
decode_json_body(Body) ->
    case jsone:try_decode(Body) of
        {ok, V, _} when is_map(V) -> V;
        _ -> #{}
    end.
