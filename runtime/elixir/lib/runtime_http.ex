defmodule RuntimeHttp do
  @moduledoc "Generated HTTP dispatcher for Smithy service clients. Uses Req."

  alias RuntimeTypes
  alias Utils

  @spec dispatch(map(), RuntimeTypes.HttpRequest.t()) ::
          {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
  def dispatch(config, req) do
    http_client = Map.get(config, :http_client, __MODULE__.ReqClient)
    dispatch(http_client, config, req)
  end

  @spec dispatch(module(), map(), RuntimeTypes.HttpRequest.t()) ::
          {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
  def dispatch(http_client, config, req = %RuntimeTypes.HttpRequest{}) do
    dispatch_signed(http_client, config, req)
  end

  @doc "Invokes fun with exponential backoff when a retryable error is returned."
  @spec with_retry((-> term()), keyword()) :: term()
  def with_retry(fun, opts) do
    max_attempts = Keyword.get(opts, :max_attempts, 3)
    base_delay_ms = Keyword.get(opts, :base_delay_ms, 100)
    should_retry = Keyword.get(opts, :should_retry, fn _ -> false end)
    with_retry(fun, max_attempts, base_delay_ms, 1, should_retry)
  end

  @spec dispatch_signed(module(), map(), RuntimeTypes.HttpRequest.t()) ::
          {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
  defp dispatch_signed(http_client, config, req = %RuntimeTypes.HttpRequest{}) do
    base_url =
      case Map.get(config, :base_url) do
        nil ->
          case Map.get(config, :endpoint_prefix) do
            nil -> ""
            _ -> Utils.resolve_base_url(config)
          end

        url ->
          url
      end

    {scheme, default_authority} = Utils.split_base_url(base_url)

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

  defp with_retry(fun, 0, _base, _n, _should_retry), do: fun.()

  defp with_retry(fun, attempts, base, n, should_retry) do
    case fun.() do
      {:ok, _} = ok ->
        ok

      {:error, _} = err ->
        if should_retry.(err) and attempts > 1 do
          Process.sleep(trunc(base * :math.pow(2, n - 1)))
          with_retry(fun, attempts - 1, base, n + 1, should_retry)
        else
          err
        end
    end
  end

  defmodule ReqClient do
    @moduledoc "false"

    @spec request(keyword()) :: {:ok, map()} | {:error, term()}
    def request(req_opts) do
      case Req.request(req_opts) do
        {:ok, response} ->
          {:ok,
           %{
             status: response.status,
             headers: response.headers,
             body: response.body
           }}

        {:error, reason} ->
          {:error, reason}
      end
    end
  end
end
