defmodule Sigv4testServiceSigv4 do
  @moduledoc "false"
  alias RuntimeTypes, as: RuntimeTypes
  alias RuntimeTypes.HttpRequest, as: HttpRequest

  @spec sign(map(), atom(), RuntimeTypes.HttpRequest.t()) :: RuntimeTypes.HttpRequest.t()
  def sign(config, operation, request) do
    credentials = Map.fetch!(config, :credentials)
    region = Map.get(config, :region, "us-east-1")
    service = Map.fetch!(config, :signing_name)
    unsigned = Map.get(config, {:unsigned_payload, operation}, false)
    opts =
      %{
        unsigned_payload: unsigned,
        endpoint_host: endpoint_host_from_config(config)
      }
    sign_request(request, credentials, region, service, opts)
  end

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

  def endpoint_host_from_config(config) do
    case Map.get(config, :base_url) do
      nil ->
        case {Map.get(config, :endpoint_prefix), Map.get(config, :region, "us-east-1")} do
          {nil, _} -> nil
          {prefix, region} -> "#{prefix}.#{region}.amazonaws.com"
        end
      base_url ->
        {_scheme, authority} = split_base_url(base_url)
        authority
    end
  end

  defp resolve_host(request = %HttpRequest{}, opts) do
    coalesce([request.host, Map.get(opts, :host), Map.get(opts, :endpoint_host), header_host(request.headers)])
  end

  defp coalesce([nil | rest]), do: coalesce(rest)
  defp coalesce(["" | rest]), do: coalesce(rest)
  defp coalesce([value | _]), do: value
  defp coalesce([]), do: "localhost"

  defp build_url(host, path, query) when map_size(query) == 0, do: "https://#{host}#{path}"
  defp build_url(
    host,
    path,
    query
  ) do
    params = URI.encode_query(query)
    "https://#{host}#{path}?#{params}"
  end

  defp ensure_host_header(headers, host) do
    case header_host(headers) do
      nil -> [{"host", host} | headers]
      _ -> headers
    end
  end

  defp header_host(headers) do
    Enum.find_value(headers, fn
  {"host", value} ->
    value;
  {"Host", value} ->
    value;
  _ ->
    nil
end)
  end

  defp maybe_add_session_token(headers, nil), do: headers
  defp maybe_add_session_token(
    headers,
    token
  ) do
    if Enum.any?(headers, fn {k, _} -> String.downcase(k) == "x-amz-security-token" end) do
      headers
    else
      [{"x-amz-security-token", token} | headers]
    end
  end

  defp body_digest_option(opts) do
    if Map.get(opts, :unsigned_payload, false) do
      [{:body_digest, "UNSIGNED-PAYLOAD"}]
    else
      []
    end
  end

  defp session_token_option(nil), do: []
  defp session_token_option(token), do: [{:session_token, to_bin(token)}]

  defp split_base_url(""), do: {"", ""}
  defp split_base_url(base_url) do
    case URI.parse(base_url) do
      %URI{scheme: scheme, host: host} = uri when is_binary(host) ->
        port_suffix =
          case uri.port do
            nil -> ""
            port -> ":#{port}"
          end
    
        {"#{scheme}://", "#{host}#{port_suffix}"}
    
      _ ->
        {"", base_url}
    end
  end

  defp to_bin(value) when is_binary(value), do: value
  defp to_bin(value) when is_atom(value), do: Atom.to_string(value)
  defp to_bin(value), do: Kernel.to_string(value)

  defp to_erl_headers(headers) do
    Enum.map(headers, fn {k, v} -> {to_bin(k), to_bin(v)} end)
  end

  defp from_erl_headers(headers) do
    Enum.map(headers, fn {k, v} -> {Kernel.to_string(k), Kernel.to_string(v)} end)
  end
end