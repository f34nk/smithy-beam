-module(weather_client).

%% Generated Smithy client for weather_client
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

-type get_weather_input() :: #{
    city => binary()
}.
-type get_weather_output() :: #{
    temperature => float()
}.
-type list_cities_input() :: #{
    filter => binary(),
    custom_header => binary(),
    next_token => binary(),
    max_results => integer()
}.
-type list_cities_output() :: #{
    next_token => binary(),
    cities => [binary()]
}.

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
get_weather(Client, Input, Options) when is_map(Input), is_map(Options) ->
    RequestFun = fun() -> make_get_weather_request(Client, Input) end,
    case maps:get(enable_retry, Options, true) of
        true -> smithy_retry:with_retry(RequestFun, Options);
        false -> RequestFun()
    end.

%% Internal function to make the GetWeather request
-spec make_get_weather_request(Client :: map(), Input :: get_weather_input()) ->
    {ok, get_weather_output()} | {error, term()}.
make_get_weather_request(Client, Input) when is_map(Input) ->
    Method = <<"GET">>,
    QueryString = <<>>,
    Endpoint = maps:get(endpoint, Client),
    Uri0 = <<"/weather/{city}">>,
    CityValue = maps:get(<<"city">>, Input),
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
                        <<>> -> {ok, #{}};
                        _ ->
                            try jsx:decode(ResponseBody, [return_maps]) of
                                DecodedBody -> {ok, DecodedBody}
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
list_cities(Client, Input, Options) when is_map(Input), is_map(Options) ->
    RequestFun = fun() -> make_list_cities_request(Client, Input) end,
    case maps:get(enable_retry, Options, true) of
        true -> smithy_retry:with_retry(RequestFun, Options);
        false -> RequestFun()
    end.

%% Internal function to make the ListCities request
-spec make_list_cities_request(Client :: map(), Input :: list_cities_input()) ->
    {ok, list_cities_output()} | {error, term()}.
make_list_cities_request(Client, Input) when is_map(Input) ->
    Method = <<"GET">>,
    QsParams = [{<<"filter">>, maps:get(<<"filter">>, Input, undefined)}, {<<"nextToken">>, maps:get(<<"nextToken">>, Input, undefined)}, {<<"maxResults">>, maps:get(<<"maxResults">>, Input, undefined)}],
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
    Headers1 = case maps:get(<<"customHeader">>, Input, undefined) of
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
                        <<>> -> {ok, #{}};
                        _ ->
                            try jsx:decode(ResponseBody, [return_maps]) of
                                DecodedBody -> {ok, DecodedBody}
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
-spec validate_get_weather_input(map()) ->
    ok | {error, {missing_required_fields, [binary()]}}.
validate_get_weather_input(Input) ->
    RequiredFields = [<<"city">>],
    Missing = [F || F <- RequiredFields, not maps:is_key(F, Input)],
    case Missing of
        [] -> ok;
        _ -> {error, {missing_required_fields, Missing}}
    end.

-spec parse_error(integer(), binary()) -> {error, term()}.
parse_error(StatusCode, Body) ->
    {error, {http_error, StatusCode, Body}}.
