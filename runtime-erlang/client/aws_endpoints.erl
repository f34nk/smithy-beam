-module(aws_endpoints).

%% AWS Endpoint Resolution Module
%% Resolves AWS service endpoints based on service name, region, and options.
%% Supports standard regions, China regions, GovCloud, global services, FIPS, and dual-stack.

%% Public API
-export([
    resolve/2,
    resolve/3,
    is_global_service/1,
    get_partition/1,
    get_dns_suffix/1,
    get_signing_region/2
]).

%% Types
-type service() :: binary() | string().
-type region() :: binary() | string().
-type partition() :: binary().
-type endpoint_info() :: #{
    hostname := binary(),
    url := binary(),
    signing_region := binary(),
    signing_name := binary(),
    dns_suffix := binary(),
    partition := binary()
}.
-type options() :: #{
    use_fips => boolean(),
    use_dual_stack => boolean(),
    endpoint_override => binary()
}.

-export_type([endpoint_info/0, options/0]).

%% Global services that use a single endpoint regardless of region
-define(GLOBAL_SERVICES, [
    <<"iam">>,
    <<"route53">>,
    <<"cloudfront">>,
    <<"waf">>,
    <<"organizations">>,
    <<"sts">>
]).

%% Partition configuration
-define(PARTITIONS, #{
    <<"aws">> => #{
        dns_suffix => <<"amazonaws.com">>,
        regions => [
            <<"us-east-1">>, <<"us-east-2">>, <<"us-west-1">>, <<"us-west-2">>,
            <<"eu-west-1">>, <<"eu-west-2">>, <<"eu-west-3">>, <<"eu-central-1">>,
            <<"eu-north-1">>, <<"eu-south-1">>, <<"eu-south-2">>, <<"eu-central-2">>,
            <<"ap-northeast-1">>, <<"ap-northeast-2">>, <<"ap-northeast-3">>,
            <<"ap-southeast-1">>, <<"ap-southeast-2">>, <<"ap-southeast-3">>,
            <<"ap-southeast-4">>, <<"ap-southeast-5">>, <<"ap-south-1">>, <<"ap-south-2">>,
            <<"ap-east-1">>, <<"ap-east-2">>,
            <<"sa-east-1">>, <<"ca-central-1">>, <<"ca-west-1">>,
            <<"me-south-1">>, <<"me-central-1">>, <<"af-south-1">>,
            <<"il-central-1">>, <<"mx-central-1">>
        ],
        region_regex => "^(us|eu|ap|sa|ca|me|af|il|mx)\\-\\w+\\-\\d+$"
    },
    <<"aws-cn">> => #{
        dns_suffix => <<"amazonaws.com.cn">>,
        regions => [<<"cn-north-1">>, <<"cn-northwest-1">>],
        region_regex => "^cn\\-\\w+\\-\\d+$"
    },
    <<"aws-us-gov">> => #{
        dns_suffix => <<"amazonaws.com">>,
        regions => [<<"us-gov-west-1">>, <<"us-gov-east-1">>],
        region_regex => "^us\\-gov\\-\\w+\\-\\d+$"
    }
}).

%% Global service endpoints (service -> {hostname, signing_region})
-define(GLOBAL_ENDPOINTS, #{
    <<"iam">> => {<<"iam.amazonaws.com">>, <<"us-east-1">>},
    <<"route53">> => {<<"route53.amazonaws.com">>, <<"us-east-1">>},
    <<"cloudfront">> => {<<"cloudfront.amazonaws.com">>, <<"us-east-1">>},
    <<"waf">> => {<<"waf.amazonaws.com">>, <<"us-east-1">>},
    <<"organizations">> => {<<"organizations.amazonaws.com">>, <<"us-east-1">>}
}).

%% @doc Resolve endpoint for a service in a region.
%% 
%% This is the main entry point for endpoint resolution. It determines the
%% correct endpoint URL based on the service name and region.
%%
%% @param Service The AWS service name (e.g., <<"s3">>, <<"dynamodb">>)
%% @param Region The AWS region (e.g., <<"us-east-1">>, <<"eu-west-1">>)
%% @returns Endpoint info map with hostname, url, signing_region, etc.
%%
%% Example:
%% ```
%% Info = aws_endpoints:resolve(<<"s3">>, <<"us-east-1">>),
%% Url = maps:get(url, Info).
%% %% -> <<"https://s3.us-east-1.amazonaws.com">>
%% ```
-spec resolve(service(), region()) -> endpoint_info().
resolve(Service, Region) ->
    resolve(Service, Region, #{}).

%% @doc Resolve endpoint for a service in a region with options.
%%
%% Options:
%% - use_fips: Use FIPS-compliant endpoints (default: false)
%% - use_dual_stack: Use IPv6-enabled dual-stack endpoints (default: false)
%% - endpoint_override: Use a custom endpoint URL (highest priority)
%%
%% @param Service The AWS service name
%% @param Region The AWS region
%% @param Options Resolution options map
%% @returns Endpoint info map
%%
%% Example:
%% ```
%% %% Use FIPS endpoint
%% Info1 = aws_endpoints:resolve(<<"s3">>, <<"us-east-1">>, #{use_fips => true}),
%%
%% %% Use custom endpoint (for LocalStack, etc.)
%% Info2 = aws_endpoints:resolve(<<"s3">>, <<"us-east-1">>, 
%%     #{endpoint_override => <<"http://localhost:4566">>}).
%% ```
-spec resolve(service(), region(), options()) -> endpoint_info().
resolve(Service, Region, Options) when is_list(Service) ->
    resolve(list_to_binary(Service), Region, Options);
resolve(Service, Region, Options) when is_list(Region) ->
    resolve(Service, list_to_binary(Region), Options);
resolve(Service, Region, Options) ->
    %% Check for endpoint override first
    case maps:get(endpoint_override, Options, undefined) of
        undefined ->
            resolve_standard(Service, Region, Options);
        Override ->
            %% Use custom endpoint
            #{
                hostname => remove_protocol(Override),
                url => ensure_protocol(Override),
                signing_region => Region,
                signing_name => Service,
                dns_suffix => <<"">>,
                partition => <<"custom">>
            }
    end.

%% @doc Check if a service is a global service.
%%
%% Global services use a single endpoint regardless of the specified region.
%% Examples: IAM, Route53, CloudFront, WAF, Organizations.
%%
%% @param Service The AWS service name
%% @returns true if the service is global, false otherwise
-spec is_global_service(service()) -> boolean().
is_global_service(Service) when is_list(Service) ->
    is_global_service(list_to_binary(Service));
is_global_service(Service) ->
    lists:member(string:lowercase(Service), ?GLOBAL_SERVICES).

%% @doc Get the partition for a region.
%%
%% Returns the partition identifier (aws, aws-cn, aws-us-gov) based on the region.
%%
%% @param Region The AWS region
%% @returns Partition identifier binary
-spec get_partition(region()) -> partition().
get_partition(Region) when is_list(Region) ->
    get_partition(list_to_binary(Region));
get_partition(Region) ->
    find_partition_for_region(Region).

%% @doc Get the DNS suffix for a partition.
%%
%% @param Partition The partition identifier (aws, aws-cn, aws-us-gov)
%% @returns DNS suffix (e.g., <<"amazonaws.com">>)
-spec get_dns_suffix(partition()) -> binary().
get_dns_suffix(Partition) ->
    case maps:get(Partition, ?PARTITIONS, undefined) of
        undefined -> <<"amazonaws.com">>;
        PartitionInfo -> maps:get(dns_suffix, PartitionInfo)
    end.

%% @doc Get the signing region for a service.
%%
%% For global services, returns the global signing region (usually us-east-1).
%% For regional services, returns the requested region.
%%
%% @param Service The AWS service name
%% @param Region The requested region
%% @returns The signing region to use
-spec get_signing_region(service(), region()) -> binary().
get_signing_region(Service, Region) when is_list(Service) ->
    get_signing_region(list_to_binary(Service), Region);
get_signing_region(Service, Region) when is_list(Region) ->
    get_signing_region(Service, list_to_binary(Region));
get_signing_region(Service, Region) ->
    ServiceLower = string:lowercase(Service),
    case maps:get(ServiceLower, ?GLOBAL_ENDPOINTS, undefined) of
        undefined -> Region;
        {_, SigningRegion} -> SigningRegion
    end.

%%====================================================================
%% Internal Functions
%%====================================================================

%% @private
%% Standard endpoint resolution
-spec resolve_standard(binary(), binary(), options()) -> endpoint_info().
resolve_standard(Service, Region, Options) ->
    ServiceLower = string:lowercase(Service),
    Partition = find_partition_for_region(Region),
    DnsSuffix = get_dns_suffix(Partition),
    
    %% Check for global service
    case maps:get(ServiceLower, ?GLOBAL_ENDPOINTS, undefined) of
        {GlobalHostname, SigningRegion} ->
            %% Global service - use fixed endpoint
            #{
                hostname => GlobalHostname,
                url => <<"https://", GlobalHostname/binary>>,
                signing_region => SigningRegion,
                signing_name => Service,
                dns_suffix => DnsSuffix,
                partition => Partition
            };
        undefined ->
            %% Regional service - construct endpoint
            Hostname = build_hostname(Service, Region, DnsSuffix, Options),
            #{
                hostname => Hostname,
                url => <<"https://", Hostname/binary>>,
                signing_region => Region,
                signing_name => Service,
                dns_suffix => DnsSuffix,
                partition => Partition
            }
    end.

%% @private
%% Build hostname for regional services
-spec build_hostname(binary(), binary(), binary(), options()) -> binary().
build_hostname(Service, Region, DnsSuffix, Options) ->
    UseFips = maps:get(use_fips, Options, false),
    UseDualStack = maps:get(use_dual_stack, Options, false),
    
    case {UseFips, UseDualStack} of
        {true, true} ->
            %% FIPS + Dual-stack
            <<Service/binary, "-fips.dualstack.", Region/binary, ".", DnsSuffix/binary>>;
        {true, false} ->
            %% FIPS only
            <<Service/binary, "-fips.", Region/binary, ".", DnsSuffix/binary>>;
        {false, true} ->
            %% Dual-stack only
            <<Service/binary, ".dualstack.", Region/binary, ".", DnsSuffix/binary>>;
        {false, false} ->
            %% Standard
            <<Service/binary, ".", Region/binary, ".", DnsSuffix/binary>>
    end.

%% @private
%% Find partition for a region
-spec find_partition_for_region(binary()) -> partition().
find_partition_for_region(Region) ->
    %% Check each partition
    case find_partition(Region, maps:to_list(?PARTITIONS)) of
        {ok, Partition} -> Partition;
        not_found -> <<"aws">> %% Default to aws
    end.

%% @private
-spec find_partition(binary(), [{partition(), map()}]) -> {ok, partition()} | not_found.
find_partition(Region, [{Partition, Info} | Rest]) ->
    Regions = maps:get(regions, Info, []),
    RegionRegex = maps:get(region_regex, Info, undefined),
    
    case lists:member(Region, Regions) of
        true -> 
            {ok, Partition};
        false ->
            %% Try regex match
            case RegionRegex of
                undefined ->
                    find_partition(Region, Rest);
                Regex ->
                    case re:run(binary_to_list(Region), Regex) of
                        {match, _} -> {ok, Partition};
                        nomatch -> find_partition(Region, Rest)
                    end
            end
    end;
find_partition(_Region, []) ->
    not_found.

%% @private
%% Ensure URL has protocol
-spec ensure_protocol(binary()) -> binary().
ensure_protocol(Url) ->
    case Url of
        <<"http://", _/binary>> -> Url;
        <<"https://", _/binary>> -> Url;
        _ -> <<"https://", Url/binary>>
    end.

%% @private
%% Remove protocol from URL
-spec remove_protocol(binary()) -> binary().
remove_protocol(<<"http://", Rest/binary>>) -> Rest;
remove_protocol(<<"https://", Rest/binary>>) -> Rest;
remove_protocol(Url) -> Url.
