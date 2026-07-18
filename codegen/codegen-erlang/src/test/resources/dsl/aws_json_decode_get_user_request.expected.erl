%% @doc Decode AWS JSON request for smithy.beam.test.awsjson11#GetUser.
-spec decode_get_user_request(#http_request{}) -> get_user_input().
decode_get_user_request(#http_request{body = Body}) ->
    Decoded = decode_json_body(Body),
    #get_user_input{
        user_name = maps:get(<<"userName">>, Decoded, undefined)
    }.
