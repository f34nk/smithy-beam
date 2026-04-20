defmodule SmithyHttpClient do
  @moduledoc """
  Generic HTTP adapter for Smithy-generated Elixir clients.

  Wraps `Req` (the default) behind a small, swappable interface so that
  generated operation modules are decoupled from the underlying HTTP library.

  ## Backend selection

  Set the application environment key `:smithy_http_backend` to an atom that
  names a module implementing `request/5`.  The bundled backends are:

    * `:req`     — (default) uses the `Req` library (`~> 0.5`).
    * `:httpc`   — OTP built-in, no external dependencies.
    * Any other module atom — must export `request/5` matching this module's
      `request/5` spec.

  ## Response format

  All functions return `{:ok, status, headers, body}` or `{:error, reason}`.

  `status`  — integer HTTP status code.
  `headers` — list of `{name, value}` binary tuples (lowercased names).
  `body`    — binary response body.
  """

  @type method   :: :get | :post | :put | :delete | :patch | :head | :options
  @type headers  :: [{String.t(), String.t()}]
  @type body     :: binary() | iodata()
  @type opts     :: map()
  @type response :: {:ok, non_neg_integer(), headers(), binary()} | {:error, term()}

  # ---------------------------------------------------------------------------
  # Convenience wrappers
  # ---------------------------------------------------------------------------

  @doc "Issue a GET request."
  @spec get(String.t(), headers()) :: response()
  def get(url, headers \\ []), do: request(:get, url, headers, "", %{})

  @doc "Issue a POST request."
  @spec post(String.t(), headers(), body()) :: response()
  def post(url, headers, body), do: request(:post, url, headers, body, %{})

  @doc "Issue a PUT request."
  @spec put(String.t(), headers(), body()) :: response()
  def put(url, headers, body), do: request(:put, url, headers, body, %{})

  @doc "Issue a DELETE request."
  @spec delete(String.t(), headers()) :: response()
  def delete(url, headers \\ []), do: request(:delete, url, headers, "", %{})

  # ---------------------------------------------------------------------------
  # Core request/4,5
  # ---------------------------------------------------------------------------

  @doc """
  Execute an HTTP request.

  ## Parameters

    * `method`  — HTTP method atom (`:get`, `:post`, …).
    * `url`     — Target URL string.
    * `headers` — List of `{"name", "value"}` tuples.
    * `body`    — Request body binary or iodata; use `""` for bodyless methods.
    * `opts`    — Call-level options map (passed to the backend).

  ## Returns

  `{:ok, status, headers, body}` or `{:error, reason}`.
  """
  @spec request(method(), String.t(), headers(), body()) :: response()
  @spec request(method(), String.t(), headers(), body(), opts()) :: response()
  def request(method, url, headers, body, opts \\ %{}) do
    case backend() do
      :req   -> request_req(method, url, headers, body, opts)
      :httpc -> request_httpc(method, url, headers, body, opts)
      mod    -> mod.request(method, url, headers, body, opts)
    end
  end

  # ---------------------------------------------------------------------------
  # Backend: Req  (default)
  # ---------------------------------------------------------------------------

  defp request_req(method, url, headers, body, _opts) do
    req_opts = [method: method, url: url, headers: headers, body: body, decode_body: false]

    case Req.request(req_opts) do
      {:ok, %Req.Response{status: status, headers: resp_headers, body: resp_body}} ->
        normalised_headers = Enum.map(resp_headers, fn {k, v} -> {k, v} end)
        {:ok, status, normalised_headers, resp_body}

      {:error, reason} ->
        {:error, reason}
    end
  end

  # ---------------------------------------------------------------------------
  # Backend: :httpc  (OTP built-in, no external dependency)
  # ---------------------------------------------------------------------------

  defp request_httpc(method, url, headers, body, _opts) do
    url_cl      = to_charlist(url)
    headers_cl  = Enum.map(headers, fn {k, v} -> {to_charlist(k), to_charlist(v)} end)
    content_type = content_type(headers)

    request =
      if method in [:get, :head, :delete] do
        {url_cl, headers_cl}
      else
        {url_cl, headers_cl, content_type, IO.iodata_to_binary(body)}
      end

    case :httpc.request(method, request, [ssl: [verify: :verify_peer]], body_format: :binary) do
      {:ok, {{_proto, status, _reason}, resp_headers, resp_body}} ->
        normalised =
          Enum.map(resp_headers, fn {k, v} ->
            {IO.iodata_to_binary(k), IO.iodata_to_binary(v)}
          end)

        {:ok, status, normalised, resp_body}

      {:error, reason} ->
        {:error, reason}
    end
  end

  # ---------------------------------------------------------------------------
  # Helpers
  # ---------------------------------------------------------------------------

  defp backend do
    Application.get_env(:smithy_beam, :smithy_http_backend, :req)
  end

  defp content_type(headers) do
    headers
    |> Enum.find(fn {k, _} -> String.downcase(k) == "content-type" end)
    |> case do
      {_, ct} -> to_charlist(ct)
      nil     -> ~c"application/octet-stream"
    end
  end
end
