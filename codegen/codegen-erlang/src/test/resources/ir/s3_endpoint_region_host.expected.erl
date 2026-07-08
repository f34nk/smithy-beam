-spec region_host(client_config()) -> binary().
region_host(Config) ->
    BaseUrl = maps:get(base_url, Config, <<>>),
    {_Scheme, Authority} = runtime_helpers:split_base_url(BaseUrl),
    Authority.