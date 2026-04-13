defmodule SmithyServer do
  @moduledoc """
  Plug-compatible HTTP abstraction layer for Smithy-generated Elixir servers.

  Generated dispatcher modules call these helpers to extract request data and
  build responses, keeping protocol details out of service business logic.

  Mirrors `smithy_server.erl` from the Erlang runtime.

  ## Usage in a generated dispatcher

      def call(%Plug.Conn{} = conn, _opts) do
        {method, path, headers, body} = SmithyServer.extract(conn)
        case route(method, path, headers, body) do
          {:ok, result}    -> SmithyServer.response(conn, 200, result)
          {:error, err}    -> SmithyServer.error_response(conn, err)
          :not_found       -> SmithyServer.not_found(conn)
        end
      end
  """

  @doc """
  Extract method, path, headers, and body from a `Plug.Conn`.

  Returns `{method, path, headers, body}` where:

    * `method` — uppercase HTTP method string, e.g. `"POST"`.
    * `path` — request path string, e.g. `"/items/42"`.
    * `headers` — request headers as a `%{String.t() => String.t()}` map
      (all names lowercased, matching Plug conventions).
    * `body` — raw request body binary.
  """
  @spec extract(Plug.Conn.t()) ::
          {method :: String.t(), path :: String.t(), headers :: map(), body :: binary()}
  def extract(%Plug.Conn{} = conn) do
    {:ok, body, _conn} = Plug.Conn.read_body(conn)
    headers = conn.req_headers |> Map.new()
    {conn.method, conn.request_path, headers, body}
  end

  @doc """
  Send a successful JSON response.

  Sets `content-type: application/json` and serialises `body` with `Jason`.
  """
  @spec response(Plug.Conn.t(), pos_integer(), term()) :: Plug.Conn.t()
  def response(conn, code, body) do
    conn
    |> Plug.Conn.put_resp_content_type("application/json")
    |> Plug.Conn.send_resp(code, Jason.encode!(body))
  end

  @doc """
  Send a JSON error response for a modelled Smithy error term.

  Delegates HTTP status code and message lookup to `SmithyErrorMap.to_http/1`.
  """
  @spec error_response(Plug.Conn.t(), term()) :: Plug.Conn.t()
  def error_response(conn, err) do
    {code, msg} = SmithyErrorMap.to_http(err)
    response(conn, code, %{message: msg})
  end

  @doc """
  Send a 400 Bad Request response for a validation failure.

  `reason` must be a term accepted by `SmithyValidator.format/1`.
  """
  @spec validation_error(Plug.Conn.t(), term()) :: Plug.Conn.t()
  def validation_error(conn, reason) do
    response(conn, 400, %{message: SmithyValidator.format(reason)})
  end

  @doc """
  Send a 404 Not Found response.
  """
  @spec not_found(Plug.Conn.t()) :: Plug.Conn.t()
  def not_found(conn) do
    Plug.Conn.send_resp(conn, 404, "Not Found")
  end
end
