uri_decode(Value) when is_binary(Value) -> uri_string:unquote(Value);
uri_decode(undefined) -> undefined.
