%% @doc Decode REST-XML response for smithy.beam.demo.http#GetName.
-spec decode_get_name_response(#http_response{}) -> {'ok', get_name_output()} | {'error', term()}.
decode_get_name_response(#http_response{status = 200, headers = Headers, body = Body}) ->
    Parsed =
        case parse_xml_root(Body, <<"GetNameOutput">>) of
            {ok, Root} -> Root;
            {error, _} -> undefined
        end,
    Name =
        case Parsed of
            undefined -> undefined;
            _ -> xml_child_text(Parsed, <<"Name">>)
        end,
    {ok, #get_name_output{
        name = Name
    }};
decode_get_name_response(#http_response{status = Status, body = Body}) ->
    decode_rest_xml_error(Status, Body).
