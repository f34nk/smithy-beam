Req = codec:encode_request(Input),
case transport:dispatch(Config, Req) of
    {ok, Resp} -> codec:decode_response(Resp);
    {error, Reason} -> {error, Reason}
end.
