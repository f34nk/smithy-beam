%% @doc Encode AWS JSON request for smithy.beam.test.awsjson11#GetUser.
-spec encode_get_user_request(get_user_input()) -> #http_request{}.
encode_get_user_request(Input = #get_user_input{user_name = UserName}) ->
    BodyMap = maps:filter(
        fun(_, V) ->
            V =/= undefined
        end,
        #{<<"userName">> => UserName}
    ),
    Body = jsone:encode(BodyMap),
    #http_request{
        method = <<"POST">>,
        path = <<"/">>,
        query = #{},
        headers = [
            {<<"Content-Type">>, <<"application/x-amz-json-1.1">>},
            {<<"X-Amz-Target">>, <<"Json11Service.GetUser">>}
        ],
        body = Body
    }.
