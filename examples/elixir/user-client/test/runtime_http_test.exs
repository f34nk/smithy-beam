defmodule RuntimeHttpTest do
  use ExUnit.Case, async: true

  alias RuntimeTypes.HttpRequest

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

      assert {:ok, resp} = RuntimeHttp.dispatch(RuntimeHttpMock, config, req)
      assert resp.status == 200
      assert resp.headers == [{"etag", "\"v1\""}]
      assert resp.body == ~s({"ok":true})
    end

    test "appends query string" do
      config = %{base_url: "https://api.example"}
      req = %HttpRequest{
        method: "GET",
        path: "/items",
        query: %{"verbose" => "true"},
        headers: [],
        body: ""
      }

      assert {:ok, resp} = RuntimeHttp.dispatch(RuntimeHttpMock, config, req)
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

      assert {:error, :timeout} == RuntimeHttp.dispatch(RuntimeHttpMock, config, req)
    end
  end

  describe "dispatch exports" do
    test "exports two- and three-arity dispatch" do
      assert {:dispatch, 2} in RuntimeHttp.__info__(:functions)
      assert {:dispatch, 3} in RuntimeHttp.__info__(:functions)
    end
  end
end
