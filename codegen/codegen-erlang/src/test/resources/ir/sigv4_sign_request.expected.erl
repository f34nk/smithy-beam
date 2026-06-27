-spec sign_request(http_request(), map(), binary(), binary(), map()) -> http_request().
sign_request(Request, Credentials, Region, Service, Opts) ->
    AccessKeyId = maps:get(access_key_id, Credentials),
    SecretAccessKey = maps:get(secret_access_key, Credentials),
    DateTime = calendar:universal_time(),
    Host = resolve_host(Request, Opts),
    Url = build_url(Host, Request#http_request.path, Request#http_request.query),
    Headers0 = ensure_host_header(Request#http_request.headers, Host),
    Headers1 = maybe_add_session_token(Headers0, maps:get(session_token, Credentials, undefined)),
    SignOpts = sign_options(Service, Opts),
    SignedHeaders = aws_signature:sign_v4(
        AccessKeyId,
        SecretAccessKey,
        Region,
        Service,
        DateTime,
        Request#http_request.method,
        Url,
        Headers1,
        Request#http_request.body,
        SignOpts
    ),
    Request#http_request{headers = SignedHeaders}.
