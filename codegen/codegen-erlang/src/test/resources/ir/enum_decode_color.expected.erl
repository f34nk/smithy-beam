decode_color(<<"RED">>) -> red;
decode_color(<<"BLUE">>) -> blue;
decode_color(V) when is_binary(V) -> {unknown, V};
decode_color(null) -> undefined;
decode_color(undefined) -> undefined.
