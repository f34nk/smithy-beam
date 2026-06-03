defmodule UserServiceRestJson1Test do
  use ExUnit.Case, async: true

  alias UserServiceTypes.{CreateUserInput, GetUserInput, ListUsersInput}
  alias RuntimeTypes.{HttpRequest, HttpResponse}

  describe "encode_get_user_request/1" do
    test "minimal request" do
      input = %GetUserInput{user_id: "u-1"}
      req = UserServiceRestJson1.encode_get_user_request(input)

      assert req.method == "GET"
      assert req.path == "/users/u-1"
      assert req.query == %{}
      assert req.headers == [{"Content-Type", "application/json"}]
      assert req.body == ""
    end

    test "URI-encodes path label" do
      input = %GetUserInput{user_id: "a/b c"}
      req = UserServiceRestJson1.encode_get_user_request(input)

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

      assert {:ok, out} = UserServiceRestJson1.decode_get_user_response(resp)
      assert out.user["userId"] == "u-1"
      assert out.user["email"] == "alice@example.com"
      assert out.user["displayName"] == "Alice"
    end

    test "success with empty body" do
      resp = %HttpResponse{status: 200, headers: [], body: ""}

      assert {:ok, out} = UserServiceRestJson1.decode_get_user_response(resp)
      assert out.user == nil
    end

    test "unknown error" do
      resp = %HttpResponse{status: 404, body: ~s({"message":"missing"})}

      assert {:error, {:unknown_error, 404, ~s({"message":"missing"})}} ==
               UserServiceRestJson1.decode_get_user_response(resp)
    end
  end

  describe "encode_create_user_request/1" do
    test "builds POST with JSON body" do
      input = %CreateUserInput{email: "bob@example.com", display_name: "Bob"}
      req = UserServiceRestJson1.encode_create_user_request(input)

      assert req.method == "POST"
      assert req.path == "/users"
      assert req.body == Jason.encode!(%{"email" => "bob@example.com", "displayName" => "Bob"})
    end
  end

  describe "decode_create_user_response/1" do
    test "success with 201" do
      body =
        Jason.encode!(%{
          "user" => %{"userId" => "u-2", "email" => "bob@example.com"}
        })

      resp = %HttpResponse{status: 201, headers: [], body: body}

      assert {:ok, out} = UserServiceRestJson1.decode_create_user_response(resp)
      assert out.user["userId"] == "u-2"
    end
  end

  describe "encode_list_users_request/1" do
    test "builds GET /users" do
      input = %ListUsersInput{}
      req = UserServiceRestJson1.encode_list_users_request(input)

      assert req.method == "GET"
      assert req.path == "/users"
      assert req.body == ""
    end
  end

  describe "decode_list_users_response/1" do
    test "success with user list" do
      body =
        Jason.encode!(%{
          "users" => [
            %{"userId" => "u-1", "email" => "a@example.com"},
            %{"userId" => "u-2", "email" => "b@example.com"}
          ]
        })

      resp = %HttpResponse{status: 200, headers: [], body: body}

      assert {:ok, out} = UserServiceRestJson1.decode_list_users_response(resp)
      assert length(out.users) == 2
    end
  end
end
