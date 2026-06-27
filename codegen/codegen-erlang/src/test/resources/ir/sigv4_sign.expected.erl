-spec sign(client_config(), Operation :: atom(), http_request()) -> http_request().
sign(Config, Operation, Request) ->
    Credentials = maps:get(credentials, Config),
    Region = maps:get(region, Config, <<"us-east-1">>),
    Service = maps:get(signing_name, Config),
    Unsigned = maps:get({unsigned_payload, Operation}, Config, false),
    Opts = #{
        unsigned_payload => Unsigned,
        endpoint_host => endpoint_host_from_config(Config)
    },
    sign_request(Request, Credentials, Region, Service, Opts).
