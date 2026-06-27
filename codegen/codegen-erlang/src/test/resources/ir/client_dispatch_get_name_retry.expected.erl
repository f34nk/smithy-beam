RetryOpts = maps:get(retry, Config, #{}),
retry_mod:with_retry(
    fun() ->
        Req = http_service_rest_json_1:encode_get_name_request(Input),
        case runtime_http:dispatch(Config, Req) of
            {ok, Resp} ->
                http_service_rest_json_1:decode_get_name_response(Resp);
            {error, Reason} ->
                {error, Reason}
        end
    end,
    RetryOpts
).
