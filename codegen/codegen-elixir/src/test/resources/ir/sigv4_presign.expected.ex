@spec presign(HttpRequest.t(), map(), String.t(), String.t(), map()) ::
        {:ok, String.t()} | {:error, term()}
def presign(request = %HttpRequest{}, credentials, region, service, opts) do
  access_key_id = Map.fetch!(credentials, :access_key_id)
  secret_access_key = Map.fetch!(credentials, :secret_access_key)
  datetime = :calendar.universal_time()
  host = resolve_host(request, opts)
  url = build_url(host, request.path, request.query)
  ttl = Map.get(opts, :expires, 900)
  query_opts =
    [{:ttl, ttl}, {:uri_encode_path, service != "s3"}]
    |> Kernel.++(body_digest_option(opts))
    |> Kernel.++(session_token_option(Map.get(credentials, :session_token)))
  try do
    {:ok,
     :aws_signature.sign_v4_query_params(
       to_bin(access_key_id),
       to_bin(secret_access_key),
       to_bin(region),
       to_bin(service),
       datetime,
       to_bin(request.method),
       to_bin(url),
       query_opts
     )
     |> to_string()}
  catch
    _, reason -> {:error, reason}
  end
end
