Req = http_service_rest_json_1:encode_get_name_request(Input),
SignedReq = sign_request(Config, get_name, Req),
case runtime_http:dispatch(Config, SignedReq) of
    {ok, Resp} ->
        http_service_rest_json_1:decode_get_name_response(Resp);
    {error, Reason} ->
        {error, Reason}
end
