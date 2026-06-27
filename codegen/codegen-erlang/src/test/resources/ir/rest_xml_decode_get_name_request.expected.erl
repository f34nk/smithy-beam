%% @doc Decode REST-XML request for smithy.beam.demo.http#GetName.
-spec decode_get_name_request(map(), #http_request{}) ->
    {'ok', get_name_input()} | {'error', term()}.
decode_get_name_request(Labels, #http_request{query = Query, headers = Headers, body = Body}) ->
    Name = maps:get(<<"name">>, Labels, undefined),
    {ok, #get_name_input{
        name = Name
    }}.
