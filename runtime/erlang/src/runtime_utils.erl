%% Shared smithy-beam Erlang runtime helpers.
-module(runtime_utils).
-export([
    split_base_url/1,
    endpoint_host_from_config/1
]).

%% @doc Split a base URL into scheme prefix and authority.
-spec split_base_url(binary()) -> {binary(), binary()}.
split_base_url(<<>>) ->
    {<<>>, <<>>};
split_base_url(BaseUrl) ->
    case uri_string:parse(binary_to_list(BaseUrl)) of
        #{scheme := Scheme, host := Host} = Parts ->
            PortSuffix =
                case maps:get(port, Parts, undefined) of
                    undefined -> <<>>;
                    Port -> <<":", (integer_to_binary(Port))/binary>>
                end,
            {<<(list_to_binary(Scheme))/binary, "://">>, <<
                (list_to_binary(Host))/binary, PortSuffix/binary
            >>};
        _ ->
            {<<>>, BaseUrl}
    end.

%% @doc Resolve the HTTP host from client config or AWS endpoint metadata.
-spec endpoint_host_from_config(#{binary() => term()}) -> binary() | undefined.
endpoint_host_from_config(Config) ->
    case maps:get(base_url, Config, undefined) of
        undefined ->
            case
                {
                    maps:get(endpoint_prefix, Config, undefined),
                    maps:get(region, Config, <<"us-east-1">>)
                }
            of
                {undefined, _} -> undefined;
                {Prefix, Region} -> <<Prefix/binary, ".", Region/binary, ".amazonaws.com">>
            end;
        BaseUrl ->
            {_Scheme, Authority} = split_base_url(BaseUrl),
            Authority
    end.
