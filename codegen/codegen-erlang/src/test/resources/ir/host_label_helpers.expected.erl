split_base_url(<<>>) ->
    {<<>>, <<>>};
split_base_url(BaseUrl) ->
    case uri_string:parse(binary_to_list(BaseUrl)) of
        #{scheme := Scheme, host := Host} = Parts ->
            PortSuffix =
                case maps:get(port, Parts, undefined) of
                    undefined -> <<>>;
                    Port -> <<":", (integer_to_binary(Port))/binary>>
                end,
            {<<(list_to_binary(Scheme))/binary, "://">>, <<
                (list_to_binary(Host))/binary, PortSuffix/binary
            >>};
        _ ->
            {<<>>, BaseUrl}
    end.

build_host(#get_tenant_data_input{tenant = Tenant}, Config) ->
    BaseUrl = maps:get(base_url, Config, <<>>),
    {_Scheme, Authority} = split_base_url(BaseUrl),
    Prefix = <<(uri_encode(to_binary(Tenant)))/binary, ".">>,
    <<Prefix/binary, Authority/binary>>.
