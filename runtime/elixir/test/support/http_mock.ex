defmodule HttpMock do
  @moduledoc false
  @behaviour RuntimeHttpClient

  alias RuntimeHttpClient.Request
  alias RuntimeTypes.HttpResponse

  @impl RuntimeHttpClient
  def request(%Request{method: :get, url: "https://api.example/items", body: ""}) do
    {:ok,
     %HttpResponse{
       status: 200,
       headers: [{"etag", "\"v1\""}],
       body: ~s({"ok":true})
     }}
  end

  def request(%Request{method: :get, url: "https://api.example/fail"}) do
    {:error, :timeout}
  end

  def request(%Request{method: :get, url: "https://api.example/items?" <> _query, body: ""}) do
    {:ok, %HttpResponse{status: 200, headers: [], body: ""}}
  end

  def request(%Request{
        method: :post,
        url: "https://api.example/items",
        body: ~s({"name":"item"})
      }) do
    {:ok, %HttpResponse{status: 201, headers: [], body: ~s({"id":1})}}
  end

  def request(%Request{} = req) do
    {:error, {:unexpected_request, req}}
  end
end
