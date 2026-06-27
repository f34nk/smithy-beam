headers_set(Name, Value, Headers) -> lists:keystore(Name, 1, Headers, {Name, Value}).
