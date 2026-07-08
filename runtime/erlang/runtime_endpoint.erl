%% Shared smithy-beam Erlang endpoint resolver.
%% Replaces generated <service>_endpoints modules (e.g. s3_endpoints).
-module(runtime_endpoint).
-export([
    resolve/2
]).

-type client_config() :: #{binary() => term()}.
-type endpoint_params() :: #{binary() => term()}.

-spec resolve(client_config(), endpoint_params()) -> {ok, #{url := binary()}} | {error, term()}.
resolve(Config, Params) ->
    evaluate(merge_params(Config, Params)).

-spec evaluate(map()) -> {ok, #{url := binary(), headers := map()}} | {error, term()}.
evaluate(Params) ->
    Region = maps:get(<<"Region">>, Params, maps:get('Region', Params, undefined)),
    case Region of
        undefined -> {error, "Invalid Configuration: Missing Region"};
        Value -> {ok, #{url => <<"https://ec2.", Value/binary, ".amazonaws.com">>, headers => #{}}}
    end.

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
        optional_param(Config, force_path_style, <<"ForcePathStyle">>),
        optional_param(Config, use_arn_region, <<"UseArnRegion">>),
        optional_param(Config, disable_multi_region_access_points,
            <<"DisableMultiRegionAccessPoints">>
        ),
        optional_param(Config, accelerate, <<"Accelerate">>),
        optional_param(Config, disable_s3express_session_auth, <<"DisableS3ExpressSessionAuth">>)
    ).

optional_param(Config, Key, RuleKey) ->
    case maps:get(Key, Config, undefined) of
        undefined -> #{};
        Value -> #{RuleKey => Value}
    end.
