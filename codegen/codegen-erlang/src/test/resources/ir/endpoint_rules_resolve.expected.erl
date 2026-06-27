-spec resolve(client_config(), endpoint_params()) -> {ok, #{url := binary()}} | {error, term()}.
resolve(Config, Params) ->
    aws_endpoint_rules:evaluate(?ENDPOINT_RULE_SET, merge_params(Config, Params)).
