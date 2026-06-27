Req = http_service_rest_json_1:encode_get_name_request(Input),
SignedReq = case maps:get(credentials, Config, undefined) of
    undefined -> Req;
    _ -> http_service_sigv4:sign(Config, get_name, Req)
end,
case runtime_http:dispatch(Config, SignedReq) of
    {ok, Resp} ->
        http_service_rest_json_1:decode_get_name_response(Resp);
    {error, Reason} ->
        {error, Reason}
end.
