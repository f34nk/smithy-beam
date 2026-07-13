defmodule HttpMock do
  @moduledoc false

  def request(req_opts) do
    method = Keyword.fetch!(req_opts, :method)
    url = Keyword.fetch!(req_opts, :url)
    body = Keyword.get(req_opts, :body, "")

    case {method, url, body} do
      {:get, "https://api.example/items", ""} ->
        {:ok, %{status: 200, headers: [{"etag", "\"v1\""}], body: ~s({"ok":true})}}

      {:get, "https://api.example/items?" <> _query, ""} ->
        {:ok, %{status: 200, headers: [], body: ""}}

      {:get, "https://api.example/fail", _} ->
        {:error, :timeout}

      {:post, "https://api.example/items", ~s({"name":"item"})} ->
        {:ok, %{status: 201, headers: [], body: ~s({"id":1})}}

      _ ->
        {:error, {:unexpected_request, req_opts}}
    end
  end
end
