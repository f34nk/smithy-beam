-module(weather_client).

%% Generated Smithy client for weather_client
-include("weather_client_types.hrl").

-export([
    new/1,
    get_weather/2,
    get_weather/3,
    list_cities/2,
    list_cities/3,
    validate_get_weather_input/1,
    parse_error/2
]).
-export_type([
    get_weather_input/0,
    get_weather_output/0,
    list_cities_input/0,
    list_cities_output/0
]).
-dialyzer([no_contracts, no_match]).

-spec new(Config :: map()) -> {ok, map()}.
new(Config) ->
    {ok, Config}.

url_encode(Binary) when is_binary(Binary) ->
    url_encode(binary_to_list(Binary));
url_encode(String) when is_list(String) ->
    list_to_binary(uri_string:quote(String)).

ensure_binary(Bin) when is_binary(Bin) -> Bin;
ensure_binary(List) when is_list(List) -> list_to_binary(List);
ensure_binary(Int) when is_integer(Int) -> integer_to_binary(Int);
ensure_binary(Float) when is_float(Float) -> float_to_binary(Float);
ensure_binary(Atom) when is_atom(Atom) -> atom_to_binary(Atom, utf8);
ensure_binary(Other) -> list_to_binary(io_lib:format("~p", [Other])).


%% Calls the GetWeather operation
-spec get_weather(Client :: map(), Input :: get_weather_input()) ->
    {ok, get_weather_output()} | {error, term()}.
get_weather(Client, Input) ->
    get_weather(Client, Input, #{}).

%% Calls the GetWeather operation with options
-spec get_weather(Client :: map(), Input :: get_weather_input(), Options :: map()) ->
    {ok, get_weather_output()} | {error, term()}.
get_weather(Client, Input, Options) when is_record(Input, get_weather_input), is_map(Options) ->
    RequestFun = fun() -> make_get_weather_request(Client, Input) end,
    case maps:get(enable_retry, Options, true) of
        true -> smithy_retry:with_retry(RequestFun, Options);
        false -> RequestFun()
    end.

%% Internal function to make the GetWeather request
-spec make_get_weather_request(Client :: map(), Input :: get_weather_input()) ->
    {ok, get_weather_output()} | {error, term()}.
make_get_weather_request(Client, Input) when is_record(Input, get_weather_input) ->
    Method = <<"GET">>,
    QueryString = <<>>,
    Endpoint = maps:get(endpoint, Client),
    Uri0 = <<"/weather/{city}">>,
    CityValue = Input#get_weather_input.city,
    CityEncoded = url_encode(ensure_binary(CityValue)),
    Uri1 = binary:replace(Uri0, <<"{city}">>, CityEncoded),
    Uri = Uri1,
    Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,
    Body = <<>>,
    Headers0 = [{<<"Content-Type">>, <<"application/json">>}],
    Headers = Headers0,
    case smithy_sigv4:sign_request(Method, Url, Headers, Body, Client) of
        {ok, SignedHeaders} ->
            StringUrl = binary_to_list(Url),
            StringHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- SignedHeaders],
            Request = {StringUrl, StringHeaders},
            case httpc:request(binary_to_atom(string:lowercase(Method), utf8), Request, [], [{body_format, binary}]) of
                {ok, {{_, StatusCode, _}, _RespHeaders, ResponseBody}} when StatusCode >= 200, StatusCode < 300 ->
                    case ResponseBody of
                        <<>> -> {ok, #get_weather_output{}};
                        _ ->
                            try jsx:decode(ResponseBody, [return_maps]) of
                                Decoded -> {ok, #get_weather_output{
                                    temperature = maps:get(<<"temperatureC">>, Decoded, undefined)
                                }}
                            catch
                                _:DecodeError -> {error, {json_decode_error, DecodeError}}
                            end
                    end;
                {ok, {{_, ErrStatusCode, _}, _RespHeaders, ErrorBody}} ->
                    parse_error(ErrStatusCode, ErrorBody);
                {error, Reason} ->
                    {error, {http_error, Reason}}
            end;
        {error, SignError} ->
            {error, {signing_error, SignError}}
    end.

%% Calls the ListCities operation
-spec list_cities(Client :: map(), Input :: list_cities_input()) ->
    {ok, list_cities_output()} | {error, term()}.
list_cities(Client, Input) ->
    list_cities(Client, Input, #{}).

%% Calls the ListCities operation with options
-spec list_cities(Client :: map(), Input :: list_cities_input(), Options :: map()) ->
    {ok, list_cities_output()} | {error, term()}.
list_cities(Client, Input, Options) when is_record(Input, list_cities_input), is_map(Options) ->
    RequestFun = fun() -> make_list_cities_request(Client, Input) end,
    case maps:get(enable_retry, Options, true) of
        true -> smithy_retry:with_retry(RequestFun, Options);
        false -> RequestFun()
    end.

%% Internal function to make the ListCities request
-spec make_list_cities_request(Client :: map(), Input :: list_cities_input()) ->
    {ok, list_cities_output()} | {error, term()}.
make_list_cities_request(Client, Input) when is_record(Input, list_cities_input) ->
    Method = <<"GET">>,
    QsParams = [
        {<<"filter">>, Input#list_cities_input.filter},
        {<<"nextToken">>, Input#list_cities_input.next_token},
        {<<"maxResults">>, Input#list_cities_input.max_results}
    ],
    QsFiltered = [{K, ensure_binary(V)} || {K, V} <- QsParams, V =/= undefined],
    QueryString = case QsFiltered of
        [] -> <<>>;
        _ -> <<"?", (uri_string:compose_query(QsFiltered))/binary>>
    end,
    Endpoint = maps:get(endpoint, Client),
    Uri = <<"/cities">>,
    Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,
    Body = <<>>,
    Headers0 = [{<<"Content-Type">>, <<"application/json">>}],
    Headers1 = case Input#list_cities_input.custom_header of
        undefined -> Headers0;
        Val1 -> [{<<"X-Custom-Header">>, ensure_binary(Val1)} | Headers0]
    end,
    Headers = Headers1,
    case smithy_sigv4:sign_request(Method, Url, Headers, Body, Client) of
        {ok, SignedHeaders} ->
            StringUrl = binary_to_list(Url),
            StringHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- SignedHeaders],
            Request = {StringUrl, StringHeaders},
            case httpc:request(binary_to_atom(string:lowercase(Method), utf8), Request, [], [{body_format, binary}]) of
                {ok, {{_, StatusCode, _}, _RespHeaders, ResponseBody}} when StatusCode >= 200, StatusCode < 300 ->
                    case ResponseBody of
                        <<>> -> {ok, #list_cities_output{}};
                        _ ->
                            try jsx:decode(ResponseBody, [return_maps]) of
                                Decoded -> {ok, #list_cities_output{
                                    next_token = maps:get(<<"nextToken">>, Decoded, undefined),
                                    cities = maps:get(<<"cities">>, Decoded, undefined)
                                }}
                            catch
                                _:DecodeError -> {error, {json_decode_error, DecodeError}}
                            end
                    end;
                {ok, {{_, ErrStatusCode, _}, _RespHeaders, ErrorBody}} ->
                    parse_error(ErrStatusCode, ErrorBody);
                {error, Reason} ->
                    {error, {http_error, Reason}}
            end;
        {error, SignError} ->
            {error, {signing_error, SignError}}
    end.

-spec validate_get_weather_input(get_weather_input()) ->
    ok | {error, {missing_required_fields, [atom()]}}.
validate_get_weather_input(#get_weather_input{city = undefined}) ->
    {error, {missing_required_fields, [city]}};
validate_get_weather_input(#get_weather_input{}) ->
    ok.

-spec parse_error(integer(), binary()) -> {error, term()}.
parse_error(StatusCode, Body) ->
    {error, {http_error, StatusCode, Body}}.
