%% Generated endpoint rule resolver for smithy.beam.test.endpoints#EndpointRulesService.
-module(endpoint_rules_service_endpoints).
-include("runtime_types.hrl").
-export([resolve/2]).
-type client_config() :: #{binary() => term()}.
-type endpoint_params() :: #{binary() => term()}.

-spec resolve(client_config(), endpoint_params()) -> {ok, #{url := binary()}} | {error, term()}.
resolve(Config, Params) ->
    aws_endpoint_rules:evaluate(?ENDPOINT_RULE_SET, merge_params(Config, Params)).

merge_params(Config, Params) ->
    ConfigParams = config_to_rule_params(Config),
    ClientParams = client_context_params(Config),
    maps:merge(maps:merge(ConfigParams, ClientParams), Params).

config_to_rule_params(Config) ->
    case maps:get(region, Config, undefined) of
        undefined -> #{};
        Value -> #{<<"Region">> => Value}
    end.

client_context_params(Config) ->
    maps:merge(
        optional_param(Config, region, <<"Region">>), optional_param(Config, bucket, <<"Bucket">>)
    ).

optional_param(Config, Key, RuleKey) ->
    case maps:get(Key, Config, undefined) of
        undefined -> #{};
        Value -> #{RuleKey => Value}
    end.
