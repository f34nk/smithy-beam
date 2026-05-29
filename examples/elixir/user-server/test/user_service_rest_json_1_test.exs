defmodule UserServiceRestJson1Test do
  use ExUnit.Case, async: true

  alias UserTypes.{DeleteUserOutput, GetUserOutput}
  alias RuntimeTypes.HttpRequest

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
      input = UserServiceRestJson1.decode_get_user_request(req, label_map)
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
      input = UserServiceRestJson1.decode_get_user_request(req, label_map)
      assert input.user_id == "hello world"
    end
  end

  describe "encode_get_user_response/1" do
    test "success with JSON body" do
      out = %GetUserOutput{
        user: %{
          "userId" => "u-1",
          "email" => "alice@example.com",
          "displayName" => "Alice"
        }
      }

      resp = UserServiceRestJson1.encode_get_user_response(out)
      assert resp.status == 200
      assert Jason.decode!(resp.body)["user"]["userId"] == "u-1"
    end

    test "omits nil fields" do
      out = %GetUserOutput{user: nil}

      resp = UserServiceRestJson1.encode_get_user_response(out)
      assert resp.status == 200
      assert resp.body == "{}"
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
      input = UserServiceRestJson1.decode_update_user_request(req, label_map)
      assert input.user_id == "u-1"
      assert input.email == nil
      assert input.display_name == "Alice"
    end
  end

  describe "decode_create_user_request/1" do
    test "decodes JSON body" do
      req = %HttpRequest{
        method: "POST",
        path: "/users",
        query: %{},
        headers: [{"Content-Type", "application/json"}],
        body: Jason.encode!(%{"email" => "a@example.com"})
      }

      input = UserServiceRestJson1.decode_create_user_request(req)
      assert input.email == "a@example.com"
    end
  end

  describe "encode_delete_user_response/1" do
    test "success with 204" do
      resp = UserServiceRestJson1.encode_delete_user_response(%DeleteUserOutput{})
      assert resp.status == 204
      assert resp.body == ""
    end
  end
end
