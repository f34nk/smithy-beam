-spec presign_url(client_config(), Operation :: atom(), http_request()) ->
    {ok, binary()} | {error, term()}.
presign_url(Config, Operation, Request) ->
    Credentials = maps:get(credentials, Config),
    Region = maps:get(region, Config, <<"us-east-1">>),
    Service = maps:get(signing_name, Config),
    Expires = maps:get(presign_expires, Config, 900),
    Unsigned = maps:get({unsigned_payload, Operation}, Config, false),
    Opts = #{
        expires => Expires,
        unsigned_payload => Unsigned,
        endpoint_host => sigv4test_service_sigv4:endpoint_host_from_config(Config)
    },
    sigv4test_service_sigv4:presign(Request, Credentials, Region, Service, Opts).
