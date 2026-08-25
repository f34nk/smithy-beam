case validate_checksum(Body, Headers, [
    <<"x-checksum-a">>,
    <<"x-checksum-b">>
]) of
    ok -> {ok, Output};
    {error, Reason} -> {error, {checksum_failed, Reason}}
end
