dispatch_signed(HttpClient, Config, #http_request{
    method = Method, path = Path, query = Query, headers = Headers, body = Body, host = Host
}) ->
    Config1 =
        case maps:get(credentials, Config, undefined) of
            undefined ->
                case credentials:resolve(Config) of
                    {ok, Creds} -> Config#{credentials => Creds};
                    _ -> Config
                end;
            _ ->
                Config
        end,
    BaseUrl =
        case maps:get(base_url, Config1, undefined) of
            undefined ->
                case maps:get(endpoint_prefix, Config1, undefined) of
                    undefined -> <<>>;
                    _ -> runtime_helpers:resolve_base_url(Config1)
                end;
            GivenUrl ->
                GivenUrl
        end,
    QueryStr =
        case maps:to_list(Query) of
            [] ->
                <<>>;
            Pairs ->
                Encoded = uri_string:compose_query([{K, V} || {K, V} <- Pairs]),
                <<"?", Encoded/binary>>
        end,
    {Scheme, DefaultAuthority} = split_base_url(BaseUrl),
    Authority =
        case Host of
            undefined -> DefaultAuthority;
            _ -> Host
        end,
    ReqUrl = <<Scheme/binary, Authority/binary, Path/binary, QueryStr/binary>>,
    HttpcHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- Headers],
    Req =
        case Body of
            <<>> -> {binary_to_list(ReqUrl), HttpcHeaders};
            _ -> {binary_to_list(ReqUrl), HttpcHeaders, mime(Headers), Body}
        end,
    case
        HttpClient:request(binary_to_atom(string:lowercase(Method), utf8), Req, [], [
            {body_format, binary}
        ])
    of
        {ok, {{_, Status, _}, RespHeaders, RespBody}} ->
            BinHeaders = [{list_to_binary(K), list_to_binary(V)} || {K, V} <- RespHeaders],
            {ok, #http_response{
                status = Status,
                headers = BinHeaders,
                body = RespBody
            }};
        {error, Reason} ->
            {error, Reason}
    end.
