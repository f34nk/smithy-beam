%% @doc Encode AWS Query server response for smithy.beam.test.awsquery#ListUsers.
-spec encode_list_users_response(list_users_output()) -> #http_response{}.
encode_list_users_response(#list_users_output{users = Users}) ->
    ResultContent = maps:filter(
        fun(_, V) ->
            V =/= undefined
        end,
        #{<<"Users">> => Users}
    ),
    Body = wrap_aws_query_response(
        <<"ListUsersResult">>, ResultContent, <<"ListUsersResponse">>, xml_namespace()
    ),
    #http_response{
        status = 200,
        headers = [{<<"Content-Type">>, <<"text/xml">>}],
        body = Body
    }.
