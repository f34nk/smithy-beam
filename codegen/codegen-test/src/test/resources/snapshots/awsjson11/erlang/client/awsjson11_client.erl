-module(awsjson11_client).
-export([describe_item/2, errors/0, is_error/1, error_to_atom/1]).

-spec describe_item(Client :: map(), Input :: describe_item_input()) ->
    {ok, describe_item_output()} | {error, term()}.

make_describe_item_request(Client, Input) when is_record(Input, describe_item_input) ->
    Method = <<"POST">>,
    QueryString = <<>>,
    Endpoint = maps:get(endpoint, Client),
    Uri = <<"/">>,
    Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,
    Headers = [
        {<<"Content-Type">>, <<"application/x-amz-json-1.0">>},
        {<<"X-Amz-Target">>, <<"AwsJson11Service.DescribeItem">>}
    ],
    Body = jsx:encode(#{    <<"itemId">> => Input#describe_item_input.item_id}),
    case smithy_sigv4:sign_request(Method, Url, Headers, Body, Client) of
        {ok, SignedHeaders} ->
            StringUrl = binary_to_list(Url),
            StringHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- SignedHeaders],
            ContentType = binary_to_list(proplists:get_value(<<"Content-Type">>, Headers, <<"application/x-amz-json-1.0">>)),
            Request = {StringUrl, StringHeaders, ContentType, Body},
            case httpc:request(post, Request, [], [{body_format, binary}]) of
                {ok, {{_, StatusCode, _}, _RespHeaders, ResponseBody}} when StatusCode >= 200, StatusCode < 300 ->
                    case ResponseBody of
                        <<>> -> {ok, #describe_item_output{}};
                        _ ->
                            try jsx:decode(ResponseBody, [return_maps]) of
                                Decoded -> {ok, #describe_item_output{
                                    name = maps:get(<<"name">>, Decoded, undefined),
                                    description = maps:get(<<"description">>, Decoded, undefined)
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

describe_item(Config, Input) ->
    {error, not_implemented}.

parse_describe_item_error(StatusCode, Body) ->
    try jsx:decode(Body, [return_maps]) of
        #{<<"__type">> := ErrorType} -> {error, {ErrorType, Body}};
        #{<<"code">> := Code} -> {error, {Code, Body}};
        _ -> {error, {http_error, StatusCode, Body}}
        catch
            _:_ -> {error, {http_error, StatusCode, Body}}
    end.


errors() ->
    [].

is_error({error, Err}) when is_tuple(Err), is_atom(element(1, Err)) ->
    lists:member(element(1, Err), errors());
is_error(_) ->
    false.

error_to_atom({error, Err}) when is_tuple(Err), is_atom(element(1, Err)) ->
    case lists:member(element(1, Err), errors()) of
        true -> element(1, Err);
        false -> unknown_error
    end;
error_to_atom(_) ->
    unknown_error.
