@spec dispatch_signed(module(), map(), RuntimeTypes.HttpRequest.t()) ::
        {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
defp dispatch_signed(http_client, config, req = %RuntimeTypes.HttpRequest{}) do
  config1 =
    case Map.get(config, :credentials) do
      nil ->
        case Credentials.resolve(config) do
          {:ok, creds} -> Map.put(config, :credentials, creds)
          _ -> config
        end
  
      _ -> config
    end
  
  base_url =
    case Map.get(config1, :base_url) do
      nil ->
        case Map.get(config1, :endpoint_prefix) do
          nil -> ""
          _ ->
            case Endpoints.resolve(config1, %{}) do
              {:ok, %{url: url}} -> url
              _ -> RuntimeHelpers.resolve_base_url(config1)
            end
        end
  
      url ->
        url
    end
  
  {scheme, default_authority} = split_base_url(base_url)
  
  authority =
    case req.host do
      nil -> default_authority
      host -> host
    end
  
  url = scheme <> authority <> req.path
  req_opts = [
    method: String.downcase(req.method) |> String.to_atom(),
    url: url,
    params: req.query,
    headers: req.headers,
    body: req.body,
    decode_body: false
  ]
  case http_client.request(req_opts) do
    {:ok, %{status: status, headers: headers, body: body}} ->
      {:ok,
       %RuntimeTypes.HttpResponse{
         status: status,
         headers: Enum.map(headers, fn {k, v} -> {k, v} end),
         body: body
       }}
  
    {:error, reason} ->
      {:error, reason}
  end
end