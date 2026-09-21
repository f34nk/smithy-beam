defmodule RuntimeHttpClient.ReqTest do
  use ExUnit.Case, async: true

  alias RuntimeHttpClient.Req
  alias RuntimeHttpClient.Request
  alias RuntimeTypes.HttpResponse

  test "to_req_opts/1 without body" do
    req = %Request{
      method: :get,
      url: "https://api.example/items",
      headers: [{"accept", "application/json"}],
      body: ""
    }

    assert [
             method: :get,
             url: "https://api.example/items",
             headers: [{"accept", "application/json"}],
             body: "",
             decode_body: false
           ] == Req.to_req_opts(req)
  end

  test "to_req_opts/1 with body" do
    req = %Request{
      method: :post,
      url: "https://api.example/items",
      headers: [{"Content-Type", "application/json"}],
      body: ~s({"name":"item"})
    }

    assert [
             method: :post,
             url: "https://api.example/items",
             headers: [{"Content-Type", "application/json"}],
             body: ~s({"name":"item"}),
             decode_body: false
           ] == Req.to_req_opts(req)
  end

  test "from_req_response/1" do
    req_response = %{
      status: 200,
      headers: [{"etag", "\"v1\""}],
      body: ~s({"ok":true})
    }

    assert {:ok, %HttpResponse{status: 200, headers: [{"etag", "\"v1\""}], body: body}} =
             Req.from_req_response(req_response)

    assert body == ~s({"ok":true})
  end
end
