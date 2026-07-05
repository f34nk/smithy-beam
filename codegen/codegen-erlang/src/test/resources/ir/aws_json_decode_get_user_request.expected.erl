%% @doc Decode AWS JSON request for smithy.beam.test.awsjson11#GetUser.
-spec decode_get_user_request(#http_request{}) -> get_user_input().
decode_get_user_request(#http_request{body = Body}) ->
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
    #get_user_input{
        user_name = maps:get(<<"userName">>, Decoded, undefined)
    }.
