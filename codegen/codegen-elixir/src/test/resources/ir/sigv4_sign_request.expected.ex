defp sign_request(request = %HttpRequest{}, credentials, region, service, opts) do
  access_key_id = Map.fetch!(credentials, :access_key_id)
  secret_access_key = Map.fetch!(credentials, :secret_access_key)
  datetime = :calendar.universal_time()
  host = resolve_host(request, opts)
  url = build_url(host, request.path, request.query)
  headers0 = ensure_host_header(request.headers, host)
  headers1 = maybe_add_session_token(headers0, Map.get(credentials, :session_token))
  sign_opts = Kernel.++([{:uri_encode_path, service != "s3"}], body_digest_option(opts))
  signed_headers = :aws_signature.sign_v4(to_bin(access_key_id), to_bin(secret_access_key), to_bin(region), to_bin(service), datetime, to_bin(request.method), to_bin(url), to_erl_headers(headers1), to_bin(request.body), sign_opts)
  %HttpRequest{request | headers: from_erl_headers(signed_headers)}
end