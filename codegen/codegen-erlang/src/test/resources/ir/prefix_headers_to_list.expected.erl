prefix_headers_to_list(_Prefix, undefined) ->
    [];
prefix_headers_to_list(Prefix, Map) when is_map(Map) ->
    [{<<Prefix/binary, H/binary>>, to_binary(V)} || {H, V} <- maps:to_list(Map)].
