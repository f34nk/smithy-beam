defmodule RuntimeHttpClient.Request do
  @moduledoc false

  @type t :: %__MODULE__{
          method: atom(),
          url: String.t(),
          headers: [{String.t(), String.t()}],
          body: iodata()
        }

  defstruct method: nil,
            url: "",
            headers: [],
            body: ""
end

defmodule RuntimeHttpClient do
  @moduledoc "HTTP client behaviour and shared request-building helpers."

  alias RuntimeHttpClient.Request
  alias RuntimeTypes.HttpRequest
  alias RuntimeTypes.HttpResponse
  alias RuntimeUtils

  @callback request(Request.t()) :: {:ok, HttpResponse.t()} | {:error, term()}

  @spec build_url(map(), HttpRequest.t()) :: String.t()
  def build_url(config, %HttpRequest{path: path, query: query, host: host}) do
    base_url = Map.get(config, :base_url)

    query_str =
      case Map.to_list(query) do
        [] -> ""
        pairs -> "?" <> URI.encode_query(pairs)
      end

    {scheme, default_authority} = RuntimeUtils.split_base_url(base_url || "")

    authority =
      case host do
        nil -> default_authority
        value -> value
      end

    scheme <> authority <> path <> query_str
  end

  @spec build_request(map(), HttpRequest.t()) :: Request.t()
  def build_request(config, %HttpRequest{} = req) do
    %Request{
      method: method_atom(req.method),
      url: build_url(config, req),
      headers: req.headers,
      body: req.body
    }
  end

  @spec method_atom(String.t()) :: atom()
  def method_atom(method) when is_binary(method) do
    method |> String.downcase() |> String.to_atom()
  end

  @spec content_type([{String.t(), String.t()}]) :: String.t()
  def content_type(headers) do
    case List.keyfind(headers, "Content-Type", 0) do
      {"Content-Type", value} -> value
      nil -> "application/octet-stream"
    end
  end
end

defmodule RuntimeHttpClient.Req do
  @moduledoc false
  @behaviour RuntimeHttpClient

  alias RuntimeHttpClient.Request
  alias RuntimeTypes.HttpResponse

  @impl RuntimeHttpClient
  @spec request(Request.t()) :: {:ok, HttpResponse.t()} | {:error, term()}
  def request(%Request{} = client_req) do
    case Req.request(to_req_opts(client_req)) do
      {:ok, response} ->
        from_req_response(response)

      {:error, reason} ->
        {:error, reason}
    end
  end

  @spec to_req_opts(Request.t()) :: keyword()
  def to_req_opts(%Request{method: method, url: url, headers: headers, body: body}) do
    [
      method: method,
      url: url,
      headers: headers,
      body: body,
      decode_body: false
    ]
  end

  @spec from_req_response(map()) :: {:ok, HttpResponse.t()}
  def from_req_response(response) do
    {:ok,
     %HttpResponse{
       status: response.status,
       headers: Enum.map(response.headers, fn {k, v} -> {k, v} end),
       body: response.body
     }}
  end
end
