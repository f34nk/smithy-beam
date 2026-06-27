%% @doc Encode AWS JSON response for smithy.beam.test.awsjson11#GetUser.
-spec encode_get_user_response(get_user_output()) -> #http_response{}.
encode_get_user_response(#get_user_output{user_name = UserName}) ->
    BodyMap = maps:filter(
        fun(_, V) ->
            V =/= undefined
        end,
        #{<<"userName">> => UserName}
    ),
    Body = jsone:encode(BodyMap),
    #http_response{
        status = 200,
        headers = [{<<"Content-Type">>, <<"application/x-amz-json-1.1">>}],
        body = Body
    }.
