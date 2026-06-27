%% @doc Decode AWS Query server request for smithy.beam.test.awsquery#ListUsers.
-spec decode_list_users_request(#http_request{}) -> list_users_input().
decode_list_users_request(#http_request{body = Body}) ->
    Params = parse_query_params(Body),
    parse_list_users_input_input(Params).
