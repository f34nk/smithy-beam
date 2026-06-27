%% @doc Encode REST-XML response for smithy.beam.demo.http#GetName.
-spec encode_get_name_response(get_name_output()) -> #http_response{}.
encode_get_name_response(#get_name_output{name = Name}) ->
    Headers = [{<<"Content-Type">>, <<"application/xml">>}],
    XmlNs = xml_namespace(),
    MemberMap = maps:filter(
        fun(_, V) ->
            V =/= undefined
        end,
        #{<<"Name">> => Name}
    ),
    Body = encode_xml(#{<<"GetNameOutput">> => MemberMap}, XmlNs),
    #http_response{
        status = 200,
        headers = Headers,
        body = Body
    }.
