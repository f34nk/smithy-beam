defmodule BasicHttpMock do
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

defmodule BasicHttpTest do
  use ExUnit.Case, async: true

  alias BasicRuntimeTypes.HttpRequest

  describe "dispatch/3" do
    test "builds url without query" do
      config = %{base_url: "https://api.example"}
      req = %HttpRequest{
        method: "GET",
        path: "/items",
        query: %{},
        headers: [{"Content-Type", "application/json"}],
        body: ""
      }

      assert {:ok, resp} = BasicHttp.dispatch(BasicHttpMock, config, req)
      assert resp.status == 200
      assert resp.headers == [{"etag", "\"v1\""}]
      assert resp.body == ~s({"ok":true})
    end

    test "appends query params" do
      config = %{base_url: "https://api.example"}
      req = %HttpRequest{
        method: "GET",
        path: "/items",
        query: %{"verbose" => "true"},
        headers: [],
        body: ""
      }

      assert {:ok, resp} = BasicHttp.dispatch(BasicHttpMock, config, req)
      assert resp.status == 200
      assert resp.body == ""
    end

    test "propagates client error" do
      config = %{base_url: "https://api.example"}
      req = %HttpRequest{
        method: "GET",
        path: "/fail",
        query: %{},
        headers: [],
        body: ""
      }

      assert {:error, :timeout} == BasicHttp.dispatch(BasicHttpMock, config, req)
    end
  end

  describe "dispatch/2" do
    test "exports two- and three-arity dispatch and Req client" do
      assert {:dispatch, 2} in BasicHttp.__info__(:functions)
      assert {:dispatch, 3} in BasicHttp.__info__(:functions)
      assert {:request, 1} in BasicHttp.ReqClient.__info__(:functions)
    end
  end
end
