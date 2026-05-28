defmodule UserHttpMock do
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

      _ ->
        {:error, {:unexpected_request, req_opts}}
    end
  end
end
