defmodule UserRestJson1Test do
  use ExUnit.Case, async: true

  alias User.{GetUserInput, UpdateUserInput}
  alias UserRuntimeTypes.{HttpRequest, HttpResponse}

  describe "encode_get_user_request/1" do
    test "minimal request" do
      input = %GetUserInput{user_id: "u-1"}
      req = UserRestJson1.encode_get_user_request(input)

      assert req.method == "GET"
      assert req.path == "/users/u-1"
      assert req.query == %{}
      assert req.headers == [{"Content-Type", "application/json"}]
      assert req.body == ""
    end

    test "URI-encodes path label" do
      input = %GetUserInput{user_id: "a/b c"}
      req = UserRestJson1.encode_get_user_request(input)

      assert req.path == "/users/a/b%20c"
    end
  end

  describe "decode_get_user_request/2" do
    test "uses label map" do
      req = %HttpRequest{
        method: "GET",
        path: "/users/u-1",
        query: %{},
        headers: [],
        body: ""
      }

      label_map = %{"userId" => "u-1"}
      input = UserRestJson1.decode_get_user_request(req, label_map)
      assert input.user_id == "u-1"
    end

    test "URI-decodes path label" do
      req = %HttpRequest{
        method: "GET",
        path: "/users/hello%20world",
        query: %{},
        headers: [],
        body: ""
      }

      label_map = %{"userId" => "hello world"}
      input = UserRestJson1.decode_get_user_request(req, label_map)
      assert input.user_id == "hello world"
    end
  end

  describe "decode_get_user_response/1" do
    test "success with JSON body" do
      body =
        Jason.encode!(%{
          "user" => %{
            "userId" => "u-1",
            "email" => "alice@example.com",
            "displayName" => "Alice"
          }
        })

      resp = %HttpResponse{status: 200, headers: [], body: body}

      assert {:ok, out} = UserRestJson1.decode_get_user_response(resp)
      assert out.user["userId"] == "u-1"
      assert out.user["email"] == "alice@example.com"
      assert out.user["displayName"] == "Alice"
    end

    test "success with empty body" do
      resp = %HttpResponse{status: 200, headers: [], body: ""}

      assert {:ok, out} = UserRestJson1.decode_get_user_response(resp)
      assert out.user == nil
    end

    test "invalid JSON raises" do
      resp = %HttpResponse{status: 200, headers: [], body: "{not json"}

      assert_raise Jason.DecodeError, fn ->
        UserRestJson1.decode_get_user_response(resp)
      end
    end

    test "unknown error" do
      resp = %HttpResponse{status: 404, body: ~s({"message":"missing"})}

      assert {:error, {:unknown_error, 404, ~s({"message":"missing"})}} ==
               UserRestJson1.decode_get_user_response(resp)
    end
  end

  describe "encode_update_user_request/1" do
    test "builds PUT with partial JSON body" do
      input = %UpdateUserInput{user_id: "u-1", email: "new@example.com", display_name: nil}
      req = UserRestJson1.encode_update_user_request(input)

      assert req.method == "PUT"
      assert req.path == "/users/u-1"
      assert req.body == Jason.encode!(%{"email" => "new@example.com"})
    end
  end

  describe "decode_update_user_request/2" do
    test "decodes path label and JSON body" do
      req = %HttpRequest{
        method: "PUT",
        path: "/users/u-1",
        query: %{},
        headers: [{"Content-Type", "application/json"}],
        body: Jason.encode!(%{"displayName" => "Alice"})
      }

      label_map = %{"userId" => "u-1"}
      input = UserRestJson1.decode_update_user_request(req, label_map)
      assert input.user_id == "u-1"
      assert input.email == nil
      assert input.display_name == "Alice"
    end
  end

  describe "decode_delete_user_response/1" do
    test "success with 204" do
      resp = %HttpResponse{status: 204, headers: [], body: ""}

      assert {:ok, out} = UserRestJson1.decode_delete_user_response(resp)
      assert out == %User.DeleteUserOutput{}
    end
  end
end
