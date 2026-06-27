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
