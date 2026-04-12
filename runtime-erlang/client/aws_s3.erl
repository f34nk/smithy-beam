-module(aws_s3).

%% AWS S3 Special Handling Module
%%
%% Provides S3-specific URL building and header handling:
%% - Bucket-in-hostname routing (virtual-hosted style)
%% - Path-style fallback for localhost/MinIO/Moto
%% - Content-MD5 calculation for PUT/POST operations
%% - S3-specific header handling

-export([
    build_url/4,
    build_url/5,
    calculate_content_md5/1,
    is_localhost/1,
    use_path_style/1
]).

%% @doc Build S3 URL with appropriate bucket routing
%%
%% Uses virtual-hosted style by default (bucket.s3.amazonaws.com)
%% Falls back to path style for localhost, IP addresses, or when
%% path_style option is explicitly set.
%%
%% @param Client Client configuration map
%% @param Bucket S3 bucket name
%% @param Key S3 object key (can be empty for bucket operations)
%% @param QueryString Query string to append (including ?)
%% @returns Fully constructed URL binary
-spec build_url(map(), binary(), binary(), binary()) -> binary().
build_url(Client, Bucket, Key, QueryString) ->
    build_url(Client, Bucket, Key, QueryString, #{}).

%% @doc Build S3 URL with options
%%
%% Options:
%% - path_style: Force path-style URL (default: auto-detect)
%% - use_accelerate: Use S3 Transfer Acceleration endpoint
%% - use_dualstack: Use dualstack (IPv4/IPv6) endpoint
%%
%% @param Client Client configuration map
%% @param Bucket S3 bucket name
%% @param Key S3 object key (can be empty for bucket operations)
%% @param QueryString Query string to append (including ?)
%% @param Options Additional options
%% @returns Fully constructed URL binary
-spec build_url(map(), binary(), binary(), binary(), map()) -> binary().
build_url(Client, Bucket, Key, QueryString, Options) ->
    Endpoint = maps:get(endpoint, Client, <<"https://s3.amazonaws.com">>),

    UsePathStyle =
        case maps:get(path_style, Options, auto) of
            true -> true;
            false -> false;
            auto -> use_path_style(Endpoint)
        end,

    case UsePathStyle of
        true ->
            build_path_style_url(Endpoint, Bucket, Key, QueryString);
        false ->
            Region = maps:get(region, Client, <<"us-east-1">>),
            build_virtual_hosted_url(Endpoint, Region, Bucket, Key, QueryString)
    end.

%% @doc Build path-style S3 URL
%% Format: https://endpoint/bucket/key
-spec build_path_style_url(binary(), binary(), binary(), binary()) -> binary().
build_path_style_url(Endpoint, Bucket, Key, QueryString) ->
    %% Remove trailing slash from endpoint if present
    Endpoint1 =
        case binary:last(Endpoint) of
            $/ -> binary:part(Endpoint, 0, byte_size(Endpoint) - 1);
            _ -> Endpoint
        end,

    %% Build path
    Path =
        case {Bucket, Key} of
            {<<>>, <<>>} -> <<"/">>;
            {<<>>, _} -> <<"/", Key/binary>>;
            {_, <<>>} -> <<"/", Bucket/binary>>;
            {_, _} -> <<"/", Bucket/binary, "/", Key/binary>>
        end,

    <<Endpoint1/binary, Path/binary, QueryString/binary>>.

%% @doc Build virtual-hosted-style S3 URL
%% Format: https://bucket.s3.region.amazonaws.com/key
-spec build_virtual_hosted_url(binary(), binary(), binary(), binary(), binary()) -> binary().
build_virtual_hosted_url(Endpoint, Region, Bucket, Key, QueryString) ->
    %% Parse the endpoint to get scheme and host
    {Scheme, Host} = parse_endpoint(Endpoint),

    %% Build virtual-hosted host
    VirtualHost =
        case Bucket of
            <<>> ->
                %% No bucket - use original host (e.g., ListBuckets)
                Host;
            _ ->
                %% Add bucket prefix to host
                %% Check if host is s3.amazonaws.com (need to add region)
                case Host of
                    <<"s3.amazonaws.com">> ->
                        <<Bucket/binary, ".s3.", Region/binary, ".amazonaws.com">>;
                    <<"s3.", _/binary>> ->
                        %% Already has region, just add bucket
                        <<Bucket/binary, ".", Host/binary>>;
                    _ ->
                        %% Custom endpoint - add bucket prefix
                        <<Bucket/binary, ".", Host/binary>>
                end
        end,

    %% Build path (key only for virtual-hosted)
    Path =
        case Key of
            <<>> -> <<"/">>;
            _ -> <<"/", Key/binary>>
        end,

    <<Scheme/binary, "://", VirtualHost/binary, Path/binary, QueryString/binary>>.

%% @doc Parse endpoint into scheme and host
-spec parse_endpoint(binary()) -> {binary(), binary()}.
parse_endpoint(Endpoint) ->
    case Endpoint of
        <<"https://", Rest/binary>> ->
            Host = strip_port_and_path(Rest),
            {<<"https">>, Host};
        <<"http://", Rest/binary>> ->
            Host = strip_port_and_path(Rest),
            {<<"http">>, Host};
        _ ->
            %% No scheme - assume https
            Host = strip_port_and_path(Endpoint),
            {<<"https">>, Host}
    end.

%% @doc Strip port and path from host
-spec strip_port_and_path(binary()) -> binary().
strip_port_and_path(HostWithPort) ->
    %% Remove path if present
    Host1 =
        case binary:split(HostWithPort, <<"/">>) of
            [H | _] -> H;
            [] -> HostWithPort
        end,
    %% Keep port for now (needed for localhost:5050 etc)
    Host1.

%% @doc Calculate Content-MD5 header value for S3 PUT/POST
%%
%% S3 uses Content-MD5 for integrity verification. The value is
%% the base64-encoded MD5 digest of the request body.
%%
%% @param Body Request body binary
%% @returns Base64-encoded MD5 digest
-spec calculate_content_md5(binary()) -> binary().
calculate_content_md5(Body) when is_binary(Body) ->
    Digest = crypto:hash(md5, Body),
    base64:encode(Digest).

%% @doc Check if endpoint is localhost or IP address
%%
%% Used to determine if path-style URLs should be used.
%% Virtual-hosted style doesn't work with localhost or IP addresses.
%%
%% @param Endpoint Endpoint URL or host
%% @returns true if localhost or IP address
-spec is_localhost(binary()) -> boolean().
is_localhost(Endpoint) ->
    {_, Host} = parse_endpoint(Endpoint),
    %% Strip port if present
    HostOnly =
        case binary:split(Host, <<":">>) of
            [H | _] -> H;
            [] -> Host
        end,
    is_localhost_host(HostOnly).

%% @doc Check if host is localhost or IP address
-spec is_localhost_host(binary()) -> boolean().
is_localhost_host(<<"localhost">>) ->
    true;
is_localhost_host(<<"127.0.0.1">>) ->
    true;
is_localhost_host(<<"0.0.0.0">>) ->
    true;
is_localhost_host(<<"::1">>) ->
    true;
is_localhost_host(Host) ->
    %% Check if it's an IP address (contains only digits and dots)
    is_ip_address(Host) orelse
        %% Check for docker-style hostnames
        is_docker_host(Host).

%% @doc Check if string looks like an IP address
-spec is_ip_address(binary()) -> boolean().
is_ip_address(Host) ->
    case re:run(Host, "^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$") of
        {match, _} -> true;
        nomatch -> false
    end.

%% @doc Check for Docker-style hostnames (commonly used in docker-compose)
-spec is_docker_host(binary()) -> boolean().
is_docker_host(<<"moto">>) -> true;
is_docker_host(<<"localstack">>) -> true;
is_docker_host(<<"minio">>) -> true;
is_docker_host(<<"s3-local">>) -> true;
is_docker_host(_) -> false.

%% @doc Determine if path-style URLs should be used
%%
%% Path-style is preferred for:
%% - Localhost and IP addresses (virtual-hosted doesn't work)
%% - Docker container hostnames (moto, localstack, minio)
%% - Endpoints with custom ports
%%
%% @param Endpoint Endpoint URL
%% @returns true if path-style should be used
-spec use_path_style(binary()) -> boolean().
use_path_style(Endpoint) ->
    {_, Host} = parse_endpoint(Endpoint),
    %% Check for port in host (indicates non-standard endpoint)
    HasPort = binary:match(Host, <<":">>) =/= nomatch,
    %% Check if localhost or IP
    IsLocalhost = is_localhost(Endpoint),
    HasPort orelse IsLocalhost.
