build_host(#get_tenant_data_input{tenant = Tenant}, Config) ->
    BaseUrl = maps:get(base_url, Config, <<>>),
    {_Scheme, Authority} = runtime_utils:split_base_url(BaseUrl),
    Prefix = <<(uri_encode(to_binary(Tenant)))/binary, ".">>,
    <<Prefix/binary, Authority/binary>>.