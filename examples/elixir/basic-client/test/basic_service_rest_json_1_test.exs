defmodule BasicServiceRestJson1Test do
  use ExUnit.Case, async: true

  alias BasicServiceTypes.GetTypeClosureInput
  alias RuntimeTypes.HttpResponse

  describe "encode_get_type_closure_request/1" do
    test "minimal request" do
      input = %GetTypeClosureInput{name: "widget"}
      req = BasicServiceRestJson1.encode_get_type_closure_request(input)

      assert req.method == "GET"
      assert req.path == "/types/widget"
      assert req.query == %{}
      assert req.headers == [{"Content-Type", "application/json"}]
      assert req.body == ""
    end

    test "full request with optional fields" do
      input = %GetTypeClosureInput{
        name: "widget",
        verbose: true,
        request_tag: "trace-1"
      }

      req = BasicServiceRestJson1.encode_get_type_closure_request(input)

      assert req.query == %{"verbose" => "true"}
      assert {"X-Request-Tag", "trace-1"} in req.headers
      assert {"Content-Type", "application/json"} in req.headers
    end

    test "URI-encodes path label" do
      input = %GetTypeClosureInput{name: "a/b c"}
      req = BasicServiceRestJson1.encode_get_type_closure_request(input)

      assert req.path == "/types/a/b%20c"
    end

    test "omits optional fields when nil" do
      input = %GetTypeClosureInput{name: "x", verbose: nil, request_tag: nil}
      req = BasicServiceRestJson1.encode_get_type_closure_request(input)

      assert req.query == %{}
      assert req.headers == [{"Content-Type", "application/json"}]
    end
  end

  describe "decode_get_type_closure_response/1" do
    test "success with empty body" do
      resp = %HttpResponse{
        status: 200,
        headers: [{"ETag", "\"v1\""}],
        body: ""
      }

      assert {:ok, out} = BasicServiceRestJson1.decode_get_type_closure_response(resp)
      assert out.etag == "\"v1\""
      assert out.basic_string == nil
    end

    test "success with JSON body" do
      body =
        Jason.encode!(%{
          "basicString" => "hello",
          "basicInteger" => 42,
          "basicBoolean" => true
        })

      resp = %HttpResponse{
        status: 200,
        headers: [{"ETag", "\"etag\""}],
        body: body
      }

      assert {:ok, out} = BasicServiceRestJson1.decode_get_type_closure_response(resp)
      assert out.etag == "\"etag\""
      assert out.basic_string == "hello"
      assert out.basic_integer == 42
      assert out.basic_boolean == true
    end

    test "invalid JSON yields empty decoded fields" do
      resp = %HttpResponse{
        status: 200,
        headers: [],
        body: "{not json"
      }

      assert {:ok, out} = BasicServiceRestJson1.decode_get_type_closure_response(resp)
      assert out.basic_string == nil
      assert out.basic_integer == nil
    end

    test "unknown error" do
      resp = %HttpResponse{status: 404, body: ~s({"message":"missing"})}

      assert {:error, {:unknown_error, 404, ~s({"message":"missing"})}} ==
               BasicServiceRestJson1.decode_get_type_closure_response(resp)
    end
  end
end
