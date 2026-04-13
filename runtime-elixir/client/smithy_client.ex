defmodule SmithyClient do
  @moduledoc """
  Operation execution pipeline for Smithy-generated Elixir clients.

  Operations are values (`%SmithyClient.Operation{}` structs) that are built
  by generated code and executed explicitly via `request/2` or `stream/2`.
  This follows the ExAws-style operations-as-values pattern.

  ## Usage

      op = MyService.list_items(%{max_results: 10})
      {:ok, result} = SmithyClient.request(op, config: config)

      SmithyClient.stream(op, config: config) |> Enum.to_list()
  """

  defmodule Operation do
    @moduledoc """
    A value representing a single Smithy HTTP operation ready to be executed.
    """

    @type t :: %__MODULE__{
            name: atom(),
            http: %{method: String.t(), uri: String.t()},
            input: map(),
            output_shape: atom(),
            auth: :sigv4 | :none
          }

    defstruct [:name, :http, :input, :output_shape, auth: :none]
  end

  @doc """
  Execute an operation and return the decoded response.

  ## Options

    * `:config` (required) — a map with at minimum `:endpoint`, and for SigV4
      auth: `:access_key_id`, `:secret_access_key`, `:region`, `:service`.
      Optionally `:session_token`.

  ## Return values

    * `{:ok, map()}` on success (2xx).
    * `{:error, {:http_error, status, body}}` on non-2xx responses.
    * `{:error, reason}` on transport or decode errors.
  """
  @spec request(Operation.t(), keyword()) :: {:ok, map()} | {:error, term()}
  def request(%Operation{} = op, opts \\ []) do
    config = Keyword.fetch!(opts, :config)

    with {:ok, url} <- build_url(op, config),
         {:ok, headers} <- build_headers(op, config),
         {:ok, body} <- encode_body(op),
         {:ok, headers} <- maybe_sign(op, url, headers, body, config),
         {:ok, response} <- send_request(op.http.method, url, headers, body) do
      decode_response(response)
    end
  end

  @doc """
  Stream paginated results for an operation.

  Automatically follows `next_token` pagination, emitting individual items
  from each page. Halts on the first error and emits it as the last element.

  ## Options

  Same as `request/2`.
  """
  @spec stream(Operation.t(), keyword()) :: Enumerable.t()
  def stream(%Operation{} = op, opts \\ []) do
    Stream.unfold(op, fn
      nil ->
        nil

      current_op ->
        case request(current_op, opts) do
          {:ok, response} ->
            items = Map.get(response, :items, [])
            next_token = Map.get(response, :next_token)

            next_op =
              if next_token,
                do: put_in(current_op.input[:next_token], next_token),
                else: nil

            {items, next_op}

          {:error, _} = err ->
            {[err], nil}
        end
    end)
    |> Stream.flat_map(fn
      {:error, _} = err -> [err]
      items -> items
    end)
  end

  @doc """
  Retry `fun` up to `max_retries` times on error.

  ## Options

    * `:max_retries` — number of additional attempts after the first failure
      (default: `3`).
  """
  @spec with_retry((() -> {:ok, term()} | {:error, term()}), keyword()) ::
          {:ok, term()} | {:error, term()}
  def with_retry(fun, opts \\ []) do
    max = Keyword.get(opts, :max_retries, 3)
    do_retry(fun, max)
  end

  # ---------------------------------------------------------------------------
  # Private helpers
  # ---------------------------------------------------------------------------

  defp build_url(%Operation{http: %{uri: uri}}, config) do
    endpoint = Map.fetch!(config, :endpoint)
    url = String.trim_trailing(endpoint, "/") <> uri
    {:ok, url}
  end

  defp build_headers(%Operation{} = _op, config) do
    host = URI.parse(Map.fetch!(config, :endpoint)).host
    headers = [{"host", host}, {"content-type", "application/json"}]
    {:ok, headers}
  end

  defp encode_body(%Operation{input: input}) when map_size(input) == 0 do
    {:ok, ""}
  end

  defp encode_body(%Operation{input: input}) do
    Jason.encode(input)
  end

  defp maybe_sign(%Operation{auth: :none}, _url, headers, _body, _config) do
    {:ok, headers}
  end

  defp maybe_sign(%Operation{auth: :sigv4}, url, headers, body, config) do
    SmithyAuth.sign_request(
      %{method: "POST", url: url, headers: headers, body: body},
      config: config
    )
  end

  defp send_request(method, url, headers, body) do
    req_opts = [method: String.downcase(method), url: url, headers: headers, body: body]

    case Req.request(req_opts) do
      {:ok, %Req.Response{status: status, body: resp_body}} when status in 200..299 ->
        {:ok, resp_body}

      {:ok, %Req.Response{status: status, body: resp_body}} ->
        {:error, {:http_error, status, resp_body}}

      {:error, reason} ->
        {:error, reason}
    end
  end

  defp decode_response(body) when is_map(body), do: {:ok, body}

  defp decode_response(body) when is_binary(body) do
    case Jason.decode(body, keys: :atoms) do
      {:ok, _} = ok -> ok
      {:error, reason} -> {:error, {:decode_error, reason}}
    end
  end

  defp do_retry(fun, 0), do: fun.()

  defp do_retry(fun, n) do
    case fun.() do
      {:ok, _} = ok -> ok
      {:error, _} -> do_retry(fun, n - 1)
    end
  end
end
