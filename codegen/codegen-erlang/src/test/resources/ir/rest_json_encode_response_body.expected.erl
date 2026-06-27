Headers = [{<<"Content-Type">>, <<"application/json">>}]
BodyMap = maps:filter(fun (_, V) ->
    V =/= undefined
end, #{<<"name">> => Name})
Body = jsone:encode(BodyMap)
#http_response{
    status = 200,
    headers = Headers,
    body = Body
}
