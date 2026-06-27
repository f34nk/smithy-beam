-spec resolve(client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve(Config) ->
    case maps:get(credentials, Config, undefined) of
        undefined -> resolve_chain(Config);
        Creds -> {ok, Creds}
    end.
