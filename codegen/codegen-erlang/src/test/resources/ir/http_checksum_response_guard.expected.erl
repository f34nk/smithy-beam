case validate_response_checksum(Body, Headers, [<<"x-amz-checksum-crc32c">>, <<"x-amz-checksum-sha256">>]) of
    ok -> {ok, Output};
    {error, Reason} -> {error, {checksum_validation_failed, Reason}}
end