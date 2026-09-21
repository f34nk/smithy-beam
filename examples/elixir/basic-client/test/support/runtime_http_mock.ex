defmodule RuntimeHttpMock do
  @moduledoc false
  @behaviour RuntimeHttpClient

  alias RuntimeHttpClient
  alias RuntimeHttpClient.Request
  alias RuntimeTypes.HttpResponse

  @impl RuntimeHttpClient
  def request(%Request{method: :get, url: "https://api.example/items", headers: headers})
      when headers != [] do
    case RuntimeHttpClient.content_type(headers) do
      "application/json" ->
        {:ok,
         %HttpResponse{
           status: 200,
           headers: [{"etag", "\"v1\""}],
           body: ~s({"ok":true})
         }}

      _ ->
        {:error, {:unexpected_request, headers}}
    end
  end

  def request(%Request{method: :get, url: "https://api.example/items?" <> _query}) do
    {:ok, %HttpResponse{status: 200, headers: [], body: ""}}
  end

  def request(%Request{method: :get, url: "https://api.example/fail"}) do
    {:error, :timeout}
  end

  def request(%Request{method: :get, url: "https://api.example/basic-items"}) do
    body =
      Jason.encode!(%{
        "items" => [%{"name" => "alpha", "count" => 1}],
        "nextToken" => "page2"
      })

    {:ok, %HttpResponse{status: 200, headers: [], body: body}}
  end

  def request(%Request{method: :get, url: "https://api.example/basic-items?" <> query}) do
    case URI.decode_query(query) do
      %{"nextToken" => "page2"} ->
        body = Jason.encode!(%{"items" => [%{"name" => "beta", "count" => 2}]})
        {:ok, %HttpResponse{status: 200, headers: [], body: body}}

      _ ->
        {:error, {:unexpected_request, query}}
    end
  end

  def request(%Request{} = req) do
    {:error, {:unexpected_request, req}}
  end
end
