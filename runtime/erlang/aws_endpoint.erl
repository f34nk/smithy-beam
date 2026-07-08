%% Shared smithy-beam Erlang AWS endpoint resolver.
%% TODO: Placeholder once full endpoint rules are implemented
%% The intended design is:
%% resolve/2: run embedded @endpointRuleSet rules when present
%% resolve_base_url/1: simple static fallback when rules are absent or fail
%% aws-examples always point at LocalStack by setting base_url from AWS_ENDPOINT. region, endpoint_prefix, and signing_name are still set for SigV4 and S3 addressing, but URL construction does not go through aws_endpoint:resolve/2 or resolve_base_url/1 in the demo path.
-module(aws_endpoint).
-export([
    resolve/2,
    resolve_base_url/1,
    endpoint_host_from_config/1
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
    maps:merge(ConfigParams, Params).

config_to_rule_params(Config) ->
    case maps:get(region, Config, undefined) of
        undefined -> #{};
        Value -> #{<<"Region">> => Value}
    end.

% TODO: fully implement endpoint rules per service
% client_context_params(Config) ->
%     maps:merge(
%         optional_param(Config, force_path_style, <<"ForcePathStyle">>),
%         optional_param(Config, use_arn_region, <<"UseArnRegion">>),
%         optional_param(Config, disable_multi_region_access_points,
%             <<"DisableMultiRegionAccessPoints">>
%         ),
%         optional_param(Config, accelerate, <<"Accelerate">>),
%         optional_param(Config, disable_s3express_session_auth, <<"DisableS3ExpressSessionAuth">>)
%     ).

% optional_param(Config, Key, RuleKey) ->
%     case maps:get(Key, Config, undefined) of
%         undefined -> #{};
%         Value -> #{RuleKey => Value}
%     end.

-spec resolve_base_url(client_config()) -> binary().
resolve_base_url(Config) ->
    Prefix = maps:get(endpoint_prefix, Config),
    Region = maps:get(region, Config, <<"us-east-1">>),
    <<"https://", Prefix/binary, ".", Region/binary, ".amazonaws.com">>.

-spec endpoint_host_from_config(client_config()) -> binary() | undefined.
endpoint_host_from_config(Config) ->
    case maps:get(base_url, Config, undefined) of
        undefined ->
            case {maps:get(endpoint_prefix, Config, undefined),
                  maps:get(region, Config, <<"us-east-1">>)} of
                {undefined, _} -> undefined;
                {Prefix, Region} -> <<Prefix/binary, ".", Region/binary, ".amazonaws.com">>
            end;
        BaseUrl ->
            {_Scheme, Authority} = runtime_helpers:split_base_url(BaseUrl),
            Authority
    end.
