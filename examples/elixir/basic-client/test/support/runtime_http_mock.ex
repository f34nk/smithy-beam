defmodule RuntimeHttpMock do
  @moduledoc false

  def request(req_opts) do
    method = Keyword.fetch!(req_opts, :method)
    url = Keyword.fetch!(req_opts, :url)
    params = Keyword.get(req_opts, :params, %{})
    headers = Keyword.get(req_opts, :headers, [])

    case {method, url, params, headers} do
      {:get, "https://api.example/items", params, headers} when map_size(params) == 0 ->
        case List.keyfind(headers, "Content-Type", 0) do
          {"Content-Type", "application/json"} ->
            {:ok, %{status: 200, headers: [{"etag", "\"v1\""}], body: ~s({"ok":true})}}

          _ ->
            {:error, {:unexpected_request, req_opts}}
        end

      {:get, "https://api.example/items", %{"verbose" => "true"}, _} ->
        {:ok, %{status: 200, headers: [], body: ""}}

      {:get, "https://api.example/fail", _, _} ->
        {:error, :timeout}

      {:get, "https://api.example/basic-items", params, _} ->
        case params do
          %{"nextToken" => "page2"} ->
            body = Jason.encode!(%{"items" => [%{"name" => "beta", "count" => 2}]})
            {:ok, %{status: 200, headers: [], body: body}}

          %{} ->
            body =
              Jason.encode!(%{
                "items" => [%{"name" => "alpha", "count" => 1}],
                "nextToken" => "page2"
              })

            {:ok, %{status: 200, headers: [], body: body}}
        end

      _ ->
        {:error, {:unexpected_request, req_opts}}
    end
  end
end
