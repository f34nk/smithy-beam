%% @doc Encode HTTP request for smithy.beam.demo.http#GetName.
-spec encode_get_name_request(get_name_input()) -> #http_request{}.
encode_get_name_request(Input = #get_name_input{name = Name}) ->
    Path = <<"/names/", (uri_encode(to_binary(Name)))/binary>>,
    Query = [],
    Headers = [{<<"Content-Type">>, <<"application/json">>}],
    Body = <<>>,
    #http_request{
        method = <<"GET">>,
        path = Path,
        query = maps:from_list(Query),
        headers = Headers,
        body = Body
    }.
