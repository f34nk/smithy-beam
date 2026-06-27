decode_query_param(undefined) -> undefined;
decode_query_param(<<"true">>) -> true;
decode_query_param(<<"false">>) -> false;
decode_query_param(V) when is_binary(V) -> V.
