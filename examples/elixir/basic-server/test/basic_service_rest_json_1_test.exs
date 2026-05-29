defmodule BasicServiceRestJson1Test do
  use ExUnit.Case, async: true

  alias BasicTypes.GetTypeClosureOutput
  alias RuntimeTypes.HttpRequest

  describe "decode_get_type_closure_request/2" do
    test "minimal request" do
      req = %HttpRequest{
        method: "GET",
        path: "/types/widget",
        query: %{},
        headers: [{"Content-Type", "application/json"}],
        body: ""
      }

      label_map = %{"name" => "widget"}
      input = BasicServiceRestJson1.decode_get_type_closure_request(req, label_map)
      assert input.name == "widget"
      assert input.verbose == nil
      assert input.request_tag == nil
    end

    test "full request with optional fields" do
      req = %HttpRequest{
        method: "GET",
        path: "/types/widget",
        query: %{"verbose" => "true"},
        headers: [{"X-Request-Tag", "trace-1"}],
        body: ""
      }

      label_map = %{"name" => "widget"}
      input = BasicServiceRestJson1.decode_get_type_closure_request(req, label_map)
      assert input.name == "widget"
      assert input.verbose == true
      assert input.request_tag == "trace-1"
    end

    test "URI-decodes path label" do
      req = %HttpRequest{
        method: "GET",
        path: "/types/hello%20world",
        query: %{},
        headers: [],
        body: ""
      }

      label_map = %{"name" => "hello world"}
      input = BasicServiceRestJson1.decode_get_type_closure_request(req, label_map)
      assert input.name == "hello world"
    end
  end

  describe "encode_get_type_closure_response/1" do
    test "success with empty body" do
      out = %GetTypeClosureOutput{etag: "\"v1\"", basic_string: nil}

      resp = BasicServiceRestJson1.encode_get_type_closure_response(out)
      assert resp.status == 200
      assert resp.body == "{}"
    end

    test "success with JSON body" do
      out = %GetTypeClosureOutput{
        etag: "\"etag\"",
        basic_string: "hello",
        basic_integer: 42,
        basic_boolean: true
      }

      resp = BasicServiceRestJson1.encode_get_type_closure_response(out)
      assert resp.status == 200

      assert Jason.decode!(resp.body) == %{
               "basicString" => "hello",
               "basicInteger" => 42,
               "basicBoolean" => true
             }
    end
  end
end
