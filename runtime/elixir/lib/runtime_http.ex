defmodule RuntimeHttp do
  @moduledoc "Generated HTTP dispatcher for Smithy service clients. Uses Req."

  alias RuntimeHttpClient
  alias RuntimeTypes.HttpRequest
  alias RuntimeTypes.HttpResponse

  @spec dispatch(map(), HttpRequest.t()) :: {:ok, HttpResponse.t()} | {:error, term()}
  def dispatch(config, req) do
    http_client = Map.get(config, :http_client, RuntimeHttpClient.Req)
    dispatch(http_client, config, req)
  end

  @spec dispatch(module(), map(), HttpRequest.t()) ::
          {:ok, HttpResponse.t()} | {:error, term()}
  def dispatch(http_client, config, req = %HttpRequest{}) do
    client_req = RuntimeHttpClient.build_request(config, req)
    http_client.request(client_req)
  end

  @doc "Invokes fun with exponential backoff when a retryable error is returned."
  @spec with_retry((-> term()), keyword()) :: term()
  def with_retry(fun, opts) do
    max_attempts = Keyword.get(opts, :max_attempts, 3)
    base_delay_ms = Keyword.get(opts, :base_delay_ms, 100)
    should_retry = Keyword.get(opts, :should_retry, fn _ -> false end)
    with_retry(fun, max_attempts, base_delay_ms, 1, should_retry)
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
end
