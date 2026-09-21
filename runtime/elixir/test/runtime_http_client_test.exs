defmodule RuntimeHttpClientTest do
  use ExUnit.Case, async: true

  alias RuntimeHttpClient
  alias RuntimeTypes.HttpRequest

  test "build_url/2 without query" do
    config = %{base_url: "https://api.example"}

    req = %HttpRequest{
      method: "GET",
      path: "/items",
      query: %{},
      headers: [],
      body: ""
    }

    assert "https://api.example/items" == RuntimeHttpClient.build_url(config, req)
  end

  test "build_url/2 with query" do
    config = %{base_url: "https://api.example"}

    req = %HttpRequest{
      method: "GET",
      path: "/items",
      query: %{"verbose" => "true"},
      headers: [],
      body: ""
    }

    url = RuntimeHttpClient.build_url(config, req)
    assert String.contains?(url, "?verbose=true")
  end

  test "build_request/2" do
    config = %{base_url: "https://api.example"}

    req = %HttpRequest{
      method: "POST",
      path: "/items",
      query: %{},
      headers: [{"Content-Type", "application/json"}],
      body: ~s({"name":"item"})
    }

    client_req = RuntimeHttpClient.build_request(config, req)
    assert client_req.method == :post
    assert client_req.url == "https://api.example/items"
    assert client_req.headers == req.headers
    assert client_req.body == req.body
  end

  test "content_type/1 defaults" do
    assert "application/octet-stream" == RuntimeHttpClient.content_type([])
  end

  test "content_type/1 from header" do
    headers = [{"Content-Type", "application/json"}]
    assert "application/json" == RuntimeHttpClient.content_type(headers)
  end
end
