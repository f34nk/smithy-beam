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

  def request(%Request{} = req) do
    {:error, {:unexpected_request, req}}
  end
end
