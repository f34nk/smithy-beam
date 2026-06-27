%% @doc Encode AWS Query request for smithy.beam.test.awsquery#ListUsers.
-spec encode_list_users_request(list_users_input()) -> #http_request{}.
encode_list_users_request(Input = #list_users_input{path_prefix = PathPrefix}) ->
    Pairs = [
        {<<"Action">>, <<"ListUsers">>},
        {<<"Version">>, <<"2010-05-08">>}
        | flatten_query_input(Input)
    ],
    Body = uri_string:compose_query([{K, enc(V)} || {K, V} <- Pairs, V =/= undefined]),
    #http_request{
        method = <<"POST">>,
        path = <<"/">>,
        query = #{},
        headers = [{<<"Content-Type">>, <<"application/x-www-form-urlencoded">>}],
        body = Body
    }.
