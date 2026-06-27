%% @doc Encode HTTP response for smithy.beam.demo.http#GetName.
-spec encode_get_name_response(get_name_output()) -> #http_response{}.
encode_get_name_response(#get_name_output{name = Name}) ->
    Headers = [{<<"Content-Type">>, <<"application/json">>}],
    BodyMap = maps:filter(
        fun(_, V) ->
            V =/= undefined
        end,
        #{<<"name">> => Name}
    ),
    Body = jsone:encode(BodyMap),
    #http_response{
        status = 200,
        headers = Headers,
        body = Body
    }.
