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

    @type encoding :: :json | :form | :xml | :blob | :none
    @type decoding :: :json | :xml | :query_xml | :raw

    @type t :: %__MODULE__{
            name: atom(),
            action: String.t() | nil,
            http: %{method: String.t(), uri: String.t()},
            input: map(),
            output_shape: atom(),
            auth: :sigv4 | :none,
            static_headers: [{String.t(), String.t()}],
            content_type: String.t(),
            encoding: encoding(),
            decoding: decoding(),
            api_version: String.t() | nil,
            parse_error_fn: (term(), map() -> {:error, term()}) | nil,
            rename_map: %{String.t() => String.t()},
            nested_rename_map: %{String.t() => %{String.t() => String.t()}}
          }

    defstruct [
      :name,
      :action,
      :http,
      :input,
      :output_shape,
      auth: :none,
      static_headers: [],
      content_type: "application/json",
      encoding: :json,
      decoding: :json,
      api_version: nil,
      parse_error_fn: nil,
      rename_map: %{},
      nested_rename_map: %{}
    ]
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
    with {:ok, url}      <- build_url(op, client),
         {:ok, headers}  <- build_headers(op, client),
         {:ok, body}     <- encode_body(op),
         {:ok, headers}  <- maybe_sign(op, url, headers, body, client),
         {:ok, raw}      <- send_request(op, url, headers, body) do
      decode_response(op.decoding, raw)
    end
  end

  defp build_url(%Operation{http: %{uri: uri}}, config) do
    endpoint = Map.fetch!(config, :endpoint)
    url = String.trim_trailing(endpoint, "/") <> uri
    {:ok, url}
  end

  defp build_headers(%Operation{content_type: ct, static_headers: sh} = _op, config) do
    host = URI.parse(Map.fetch!(config, :endpoint)).host
    headers = [{"host", host}, {"content-type", ct}] ++ sh
    {:ok, headers}
  end

  # AWS Query / EC2 Query — form-urlencoded body with Action= and Version=
  defp encode_body(%Operation{encoding: :form, action: action, input: input, api_version: ver,
                               rename_map: rename_map, nested_rename_map: nested_rename_map}) do
    action_str = action || raise "query-protocol Operation is missing :action"
    wire_input = SmithyQuery.apply_renames(input, rename_map, nested_rename_map)
    {:ok, apply(SmithyQuery, :encode, [action_str, wire_input, ver])}
  end

  # JSON body — always send {} for empty inputs (AWS JSON 1.x requires it)
  defp encode_body(%Operation{encoding: :json, input: input}) when map_size(input) == 0 do
    {:ok, "{}"}
  end

  defp encode_body(%Operation{encoding: :json, input: input}) do
    Jason.encode(input)
  end

  # XML body
  defp encode_body(%Operation{encoding: :xml, input: input}) when map_size(input) == 0 do
    {:ok, ""}
  end

  defp encode_body(%Operation{encoding: :xml, input: input}) do
    {:ok, IO.iodata_to_binary(apply(SmithyXml, :encode, [input]))}
  end

  # Blob body — @httpPayload operations (e.g. S3 PutObject) send just the
  # designated payload member as the raw HTTP body.  The member is named "Body"
  # in the input map (both string and atom keys are supported).
  defp encode_body(%Operation{encoding: :blob, input: input}) do
    body = Map.get(input, "Body") || Map.get(input, :body) || ""
    {:ok, body}
  end

  # No body
  defp encode_body(%Operation{encoding: :none}), do: {:ok, ""}

  defp maybe_sign(%Operation{auth: :none}, _url, headers, _body, _config) do
    {:ok, headers}
  end

  defp maybe_sign(%Operation{auth: :sigv4, http: %{method: method}}, url, headers, body, config) do
    SmithySigV4.sign_request(
      %{method: method, url: url, headers: headers, body: body},
      config: config
    )
  end

  defp send_request(op, url, headers, body) do
    method = op.http.method |> String.downcase()
    req_opts = [method: method, url: url, headers: headers, body: body]

    case Req.request(req_opts) do
      {:ok, %Req.Response{status: status, body: resp_body}} when status in 200..299 ->
        {:ok, resp_body}

      {:ok, %Req.Response{status: status, body: resp_body}} ->
        handle_error(op, status, resp_body)

      {:error, reason} ->
        {:error, reason}
    end
  end

  # Dispatch HTTP errors through the generated parse_error/2 when available.
  defp handle_error(%Operation{parse_error_fn: nil}, status, body) do
    {:error, {:http_error, status, body}}
  end

  defp handle_error(%Operation{parse_error_fn: parse_fn, decoding: decoding}, status, body) do
    # For :raw operations (e.g. S3 GetObject), error responses are still XML.
    # Fall back to :xml decoding so the error body can be parsed correctly.
    error_decoding = if decoding == :raw, do: :xml, else: decoding

    parsed =
      case decode_response(error_decoding, body) do
        {:ok, map} -> map
        _ -> body
      end

    # AWS JSON protocols embed the error type in the "__type" field of the
    # response body.  The generated parse_error/2 dispatches on that string
    # (e.g. "ParameterNotFound"), not the HTTP status integer.
    error_code = extract_error_code(parsed, status)
    parse_fn.(error_code, parsed)
  end

  # Extract the error code to use as the first argument of parse_error/2.
  # AWS JSON 1.x: error type is in the "__type" field, optionally namespaced.
  defp extract_error_code(%{"__type" => type}, _status) when is_binary(type) do
    case String.split(type, "#", parts: 2) do
      [_namespace, name] -> name
      _ -> type
    end
  end

  # All other protocols (REST-JSON, AWS-Query, REST-XML without __type):
  # fall back to the HTTP status code so integer-dispatch parse_error clauses match.
  defp extract_error_code(_parsed, status), do: status

  # REST-XML decode (S3, CloudFront, etc.) — plain XML, no query-envelope unwrapping.
  # Empty body is valid for operations like PutObject that return HTTP 200 with no body.
  defp decode_response(:xml, body) when is_binary(body) and byte_size(body) == 0, do: {:ok, %{}}

  defp decode_response(:xml, body) when is_binary(body) do
    case apply(SmithyXml, :decode, [body]) do
      {:ok, _map} = ok -> ok
      {:error, _} = err -> err
    end
  end

  defp decode_response(:xml, body) when is_map(body), do: {:ok, body}

  # awsQuery / ec2Query XML decode — unwrap the {OperationResult -> ...} response envelope.
  defp decode_response(:query_xml, body) when is_binary(body) and byte_size(body) == 0, do: {:ok, %{}}

  defp decode_response(:query_xml, body) when is_binary(body) do
    case apply(SmithyXml, :decode, [body]) do
      {:ok, map} -> apply(SmithyQuery, :unwrap_response, [map])
      {:error, _} = err -> err
    end
  end

  defp decode_response(:query_xml, body) when is_map(body), do: {:ok, body}

  # Raw decode — @httpPayload operations (e.g. S3 GetObject) return a binary blob.
  # Return the body as-is without any parsing.
  defp decode_response(:raw, body), do: {:ok, body}

  # JSON decode — Req may have already parsed the body into a map with string keys.
  # When the body is still binary, decode it with string keys to match the auto-decoded case.
  defp decode_response(:json, body) when is_map(body), do: {:ok, body}

  defp decode_response(:json, body) when is_binary(body) do
    case Jason.decode(body) do
      {:ok, _} = ok -> ok
      {:error, reason} -> {:error, {:decode_error, reason}}
    end
  end

  defp decode_response(_decoding, body), do: {:ok, body}

  defp do_retry(fun, 0), do: fun.()

  defp do_retry(fun, n) do
    case fun.() do
      {:ok, _} = ok -> ok
      {:error, _} -> do_retry(fun, n - 1)
    end
  end
end
