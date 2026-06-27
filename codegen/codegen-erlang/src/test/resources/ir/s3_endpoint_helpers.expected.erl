key_path(<<>>) -> <<>>;
key_path(Key) -> <<"/", Key/binary>>.

virtual_host(Config, Bucket, RegionHost) ->
    case maps:get(s3_use_accelerate, Config, false) of
        true ->
            <<Bucket/binary, ".s3-accelerate.amazonaws.com">>;
        false ->
            Suffix = s3_host_suffix(Config),
            <<Bucket/binary, Suffix/binary, RegionHost/binary>>
    end.

s3_host_suffix(Config) ->
    case maps:get(s3_use_dualstack, Config, false) of
        true -> <<".s3.dualstack.">>;
        false -> <<".s3.">>
    end.

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
