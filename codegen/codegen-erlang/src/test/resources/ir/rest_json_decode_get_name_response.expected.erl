%% @doc Decode HTTP response for smithy.beam.demo.http#GetName.
-spec decode_get_name_response(#http_response{}) -> {'ok', get_name_output()} | {'error', term()}.
decode_get_name_response(#http_response{status = 200, headers = Headers, body = Body}) ->
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
    {ok, #get_name_output{
        name = maps:get(<<"name">>, Decoded, undefined)
    }};
decode_get_name_response(#http_response{status = Status, headers = RespHeaders, body = Body}) ->
    decode_get_name_response_error(Status, RespHeaders, Body).
