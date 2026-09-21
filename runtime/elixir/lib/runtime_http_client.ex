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
