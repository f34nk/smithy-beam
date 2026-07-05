%% @doc Decode AWS Query response for smithy.beam.test.awsquery#ListUsers.
-spec decode_list_users_response(#http_response{}) ->
    {'ok', list_users_output()} | {'error', term()}.
decode_list_users_response(#http_response{status = 200, body = Body}) ->
    case unwrap_query_result(Body, <<"ListUsersResult">>) of
        {ok, Result} ->
            {ok, #list_users_output{
                users = xml_child_list(Result, <<"Users">>, <<"member">>)
            }};
        {error, Reason} ->
            {error, Reason}
    end;
decode_list_users_response(#http_response{status = Status, body = Body}) ->
    decode_query_error(Status, Body).
