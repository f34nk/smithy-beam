-spec resolve_base_url(map()) -> binary().
resolve_base_url(Config) ->
    Prefix = maps:get(endpoint_prefix, Config),
    Region = maps:get(region, Config, <<"us-east-1">>),
    <<"https://", Prefix/binary, ".", Region/binary, ".amazonaws.com">>.
