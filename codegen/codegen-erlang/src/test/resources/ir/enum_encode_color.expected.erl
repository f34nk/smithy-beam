encode_color(red) -> <<"RED">>;
encode_color(blue) -> <<"BLUE">>;
encode_color({unknown, V}) when is_binary(V) -> V;
encode_color(undefined) -> undefined.
