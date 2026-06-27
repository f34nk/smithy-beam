%% @doc Encode HTTP error response for smithy.beam.demo.http#NotFoundError.
-spec encode_not_found_error_response(#not_found_error{}) -> #http_response{}.
encode_not_found_error_response(#not_found_error{message = Message}) ->
    BodyMap = #{<<"__type">> => <<"NotFoundError">>, <<"message">> => Message},
    Body = jsone:encode(BodyMap),
    #http_response{
        status = 404,
        headers = [{<<"Content-Type">>, <<"application/json">>}],
        body = Body
    }.
