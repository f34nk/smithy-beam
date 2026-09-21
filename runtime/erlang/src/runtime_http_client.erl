%% HTTP client behaviour and shared request-building helpers.
-module(runtime_http_client).
-include("runtime_types.hrl").
-include("runtime_http_client.hrl").

-export([
    build_url/2,
    build_request/2,
    method_atom/1,
    content_type/1
]).

-callback request(http_client_request()) -> {ok, http_response()} | {error, term()}.

-spec build_url(#{binary() => term()}, http_request()) -> binary().
build_url(Config, #http_request{path = Path, query = Query, host = Host}) ->
    BaseUrl = maps:get(base_url, Config, undefined),
    QueryStr =
        case maps:to_list(Query) of
            [] ->
                <<>>;
            Pairs ->
                Encoded = uri_string:compose_query([{K, V} || {K, V} <- Pairs]),
                <<"?", Encoded/binary>>
        end,
    {Scheme, DefaultAuthority} = runtime_utils:split_base_url(BaseUrl),
    Authority =
        case Host of
            undefined -> DefaultAuthority;
            _ -> Host
        end,
    <<Scheme/binary, Authority/binary, Path/binary, QueryStr/binary>>.

-spec build_request(#{binary() => term()}, http_request()) -> http_client_request().
build_request(Config, #http_request{} = Request) ->
    #http_client_request{
        method = method_atom(Request#http_request.method),
        url = build_url(Config, Request),
        headers = Request#http_request.headers,
        body = Request#http_request.body
    }.

-spec method_atom(binary()) -> atom().
method_atom(Method) when is_binary(Method) ->
    binary_to_atom(string:lowercase(Method), utf8).

-spec content_type([{binary(), binary()}]) -> binary().
content_type(Headers) ->
    case proplists:get_value(<<"Content-Type">>, Headers) of
        undefined -> <<"application/octet-stream">>;
        CT -> CT
    end.
