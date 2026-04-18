-module(dynamo_client).

%% Generated Smithy client for dynamo_client
-export([
    new/1,
    get_item/2,
    get_item/3,
    parse_error/2
]).
-export_type([
    get_item_input/0,
    get_item_output/0
]).
-dialyzer([no_contracts, no_match]).

-type get_item_input() :: #{
    table_name => binary(),
    key => binary()
}.
-type get_item_output() :: #{
    item => binary()
}.

-spec new(Config :: map()) -> {ok, map()}.
new(Config) ->
    {ok, Config}.

%% Calls the GetItem operation
-spec get_item(Client :: map(), Input :: get_item_input()) ->
    {ok, get_item_output()} | {error, term()}.
get_item(Client, Input) ->
    get_item(Client, Input, #{}).

%% Calls the GetItem operation with options
-spec get_item(Client :: map(), Input :: get_item_input(), Options :: map()) ->
    {ok, get_item_output()} | {error, term()}.
get_item(Client, Input, Options) when is_map(Input), is_map(Options) ->
    RequestFun = fun() -> make_get_item_request(Client, Input) end,
    case maps:get(enable_retry, Options, true) of
        true -> smithy_retry:with_retry(RequestFun, Options);
        false -> RequestFun()
    end.

%% Internal function to make the GetItem request
-spec make_get_item_request(Client :: map(), Input :: get_item_input()) ->
    {ok, get_item_output()} | {error, term()}.
make_get_item_request(Client, Input) when is_map(Input) ->
    Method = <<"POST">>,
    QueryString = <<>>,
    Endpoint = maps:get(endpoint, Client),
    Uri = <<"/">>,
    Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,
    Body = jsx:encode(Input),
    Headers0 = [{<<"Content-Type">>, <<"application/x-amz-json-1.0">>}],
    Headers1 = [{<<"X-Amz-Target">>, <<"DynamoService.GetItem">>} | Headers0],
    Headers = Headers1,
    case smithy_sigv4:sign_request(Method, Url, Headers, Body, Client) of
        {ok, SignedHeaders} ->
            StringUrl = binary_to_list(Url),
            StringHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- SignedHeaders],
            Request = {StringUrl, StringHeaders, "application/x-amz-json-1.0", Body},
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
                    try
                        ErrorMap = jsx:decode(ErrorBody, [return_maps]),
                        ErrorType = maps:get(<<"__type">>, ErrorMap, <<"Unknown">>),
                        parse_error(ErrorType, ErrorMap)
                    catch
                        _:_ -> {error, {http_error, ErrStatusCode, ErrorBody}}
                    end;
                {error, Reason} ->
                    {error, {http_error, Reason}}
            end;
        {error, SignError} ->
            {error, {signing_error, SignError}}
    end.
-spec parse_error(binary(), map()) -> {error, term()}.
parse_error(_, Body) ->
    {error, #{error_type => unknown, body => Body}}.
