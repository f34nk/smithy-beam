defmodule RuntimeHttp do
  @moduledoc "Generated HTTP dispatcher for Smithy service clients. Uses Req."
  alias RuntimeTypes, as: RuntimeTypes
  alias RuntimeHelpers, as: RuntimeHelpers

  @spec dispatch(map(), RuntimeTypes.HttpRequest.t()) ::
          {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
  def dispatch(
    config,
    req
  ) do
    http_client = Map.get(config, :http_client, __MODULE__.ReqClient),
    dispatch(http_client, config, req)
  end

  @spec dispatch(module(), map(), RuntimeTypes.HttpRequest.t()) ::
          {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
  def dispatch(
    http_client,
    config,
    req = %RuntimeTypes.HttpRequest{}
  ) do
    dispatch_signed(http_client, config, req)
  end

  @spec dispatch_signed(module(), map(), RuntimeTypes.HttpRequest.t()) ::
          {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
  defp dispatch_signed(
    http_client,
    config,
    req = %RuntimeTypes.HttpRequest{}
  ) do
    base_url =
      case Map.get(config, :base_url) do
        nil ->
          case Map.get(config, :endpoint_prefix) do
            nil -> ""
            _ -> RuntimeHelpers.resolve_base_url(config)
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

  defp split_base_url(""), do: {"", ""}
  defp split_base_url(base_url) do
    case URI.parse(base_url) do
      %URI{scheme: scheme, host: host} = uri when is_binary(host) ->
        port_suffix =
          case {uri.scheme, uri.port} do
            {"https", 443} -> ""
            {"http", 80} -> ""
            {_, nil} -> ""
            {_, port} -> ":#{port}"
          end
    
        {scheme <> "://", host <> port_suffix}
    
      _ ->
        {"", base_url}
    end
  end

  defmodule ReqClient do
    @moduledoc "false"
    @spec request(keyword()) :: {:ok, map()} | {:error, term()}
    def request(req_opts) do
      case Req.request(req_opts) do
        {:ok, %Req.Response{status: status, headers: headers, body: body}} -> {:ok, %{status: status, headers: headers, body: body}}

        {:error, reason} -> {:error, reason}
      end
    end
  end
end