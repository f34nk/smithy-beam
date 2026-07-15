key_path(<<>>) -> <<>>;
key_path(Key) -> <<"/", Key/binary>>.

virtual_host(Config, Bucket, RegionHost) ->
    case maps:get(s3_use_accelerate, Config, false) of
        true -> <<Bucket/binary, ".s3-accelerate.amazonaws.com">>;
        false ->
            Suffix = s3_host_suffix(Config),
            <<Bucket/binary, Suffix/binary, RegionHost/binary>>
    end.

s3_host_suffix(Config) ->
    case maps:get(s3_use_dualstack, Config, false) of
        true -> <<".s3.dualstack.">>;
        false -> <<".s3.">>
    end.