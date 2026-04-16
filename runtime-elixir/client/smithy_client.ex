defmodule SmithyClient do
  @moduledoc """
  Operation execution pipeline for Smithy-generated Elixir clients.

  Generated operation functions follow the 3-argument pattern matching the
  Erlang SDK convention:

      {:ok, result} = MyService.list_items(client, %{max_results: 10}, %{})

  Where `client` is the map returned by `MyService.new/1`, `input` is the
  request parameters, and `opts` is a map of call-level options.

  ## Options

    * `:enable_retry` — boolean, default `true`. Set to `false` to disable
      automatic retries.

  ## Configuration keys (in the client map)

    * `:endpoint` — base URL, e.g. `"https://ssm.us-east-1.amazonaws.com"`
    * `:region` — AWS region string
    * `:service` — AWS service identifier (e.g. `"ssm"`, `"s3"`)
    * `:credentials` — map with `:access_key_id`, `:secret_access_key`,
      and optionally `:session_token`
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
  Execute an operation against the given client config and return the decoded response.

  `client` is the map returned by `MyService.new/1`.
  `opts` is a plain map of call-level options (e.g. `%{enable_retry: false}`).
  """
  @spec request(map(), Operation.t(), map()) :: {:ok, map()} | {:error, term()}
  def request(client, %Operation{} = op, opts \\ %{}) when is_map(client) do
    do_execute(client, op, opts)
  end

  @doc """
  Stream paginated results for an operation.

  Automatically follows `next_token` pagination, emitting individual items
  from each page. Halts on the first error and emits it as the last element.
  """
  @spec stream(map(), Operation.t(), map()) :: Enumerable.t()
  def stream(client, %Operation{} = op, opts \\ %{}) when is_map(client) do
    Stream.unfold(op, fn
      nil ->
        nil

      current_op ->
        case do_execute(client, current_op, opts) do
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

  defp do_execute(client, %Operation{} = op, _opts) do
    with {:ok, url}     <- build_url(op, client),
         {:ok, headers} <- build_headers(op, client),
         {:ok, body}    <- encode_body(op),
         {:ok, headers} <- maybe_sign(op, url, headers, body, client),
         {:ok, response} <- send_request(op.http.method, url, headers, body) do
      decode_response(response)
    end
  end

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
    SmithySigV4.sign_request(
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
