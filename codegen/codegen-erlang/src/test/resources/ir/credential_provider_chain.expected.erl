-spec resolve_chain(client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve_chain(Config) -> resolve_chain(Config, [env, profile, ecs, ec2]).

resolve_chain(_Config, []) ->
    {error, not_found};
resolve_chain(Config, [Provider | Rest]) ->
    case resolve_provider(Provider, Config) of
        {ok, Creds} -> {ok, Creds};
        _ -> resolve_chain(Config, Rest)
    end.

-spec resolve_provider(atom(), client_config()) -> {ok, aws_credentials()} | {error, term()}.
resolve_provider(env, Config) -> resolve_from_env(Config);
resolve_provider(profile, Config) -> resolve_from_profile(Config);
resolve_provider(ecs, Config) -> resolve_from_ecs(Config);
resolve_provider(ec2, Config) -> resolve_from_ec2(Config).
