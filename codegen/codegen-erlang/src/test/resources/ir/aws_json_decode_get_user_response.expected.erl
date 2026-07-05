%% @doc Decode AWS JSON response for smithy.beam.test.awsjson11#GetUser.
-spec decode_get_user_response(#http_response{}) -> {'ok', get_user_output()} | {'error', term()}.
decode_get_user_response(#http_response{status = 200, body = Body}) ->
    Decoded =
        case Body of
            <<>> ->
                #{};
            _ ->
                case jsone:try_decode(Body) of
                    {ok, Val, _} -> Val;
                    {error, _} -> #{}
                end
        end,
    {ok, #get_user_output{
        user_name = maps:get(<<"userName">>, Decoded, undefined)
    }};
decode_get_user_response(#http_response{status = Status, headers = RespHeaders, body = Body}) ->
    decode_get_user_response_error(Status, RespHeaders, Body).
