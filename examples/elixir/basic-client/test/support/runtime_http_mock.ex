defmodule RuntimeHttpMock do
  @moduledoc false

  def request(req_opts) do
    method = Keyword.fetch!(req_opts, :method)
    url = Keyword.fetch!(req_opts, :url)
    headers = Keyword.get(req_opts, :headers, [])

    case {method, url, headers} do
      {:get, "https://api.example/items", headers} ->
        case List.keyfind(headers, "Content-Type", 0) do
          {"Content-Type", "application/json"} ->
            {:ok, %{status: 200, headers: [{"etag", "\"v1\""}], body: ~s({"ok":true})}}

          _ ->
            {:error, {:unexpected_request, req_opts}}
        end

      {:get, "https://api.example/items?" <> _query, _} ->
        {:ok, %{status: 200, headers: [], body: ""}}

      {:get, "https://api.example/fail", _} ->
        {:error, :timeout}

      {:get, "https://api.example/basic-items", _} ->
        body =
          Jason.encode!(%{
            "items" => [%{"name" => "alpha", "count" => 1}],
            "nextToken" => "page2"
          })

        {:ok, %{status: 200, headers: [], body: body}}

      {:get, "https://api.example/basic-items?" <> query, _} ->
        case URI.decode_query(query) do
          %{"nextToken" => "page2"} ->
            body = Jason.encode!(%{"items" => [%{"name" => "beta", "count" => 2}]})
            {:ok, %{status: 200, headers: [], body: body}}

          _ ->
            {:error, {:unexpected_request, req_opts}}
        end

      _ ->
        {:error, {:unexpected_request, req_opts}}
    end
  end
end
