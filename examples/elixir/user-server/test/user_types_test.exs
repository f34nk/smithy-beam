defmodule UserTypesTest do
  use ExUnit.Case, async: true

  describe "UserData struct" do
    test "can be created with all fields" do
      user = %User.UserData{
        user_id: "u-1",
        email: "alice@example.com",
        display_name: "Alice"
      }

      assert user.user_id == "u-1"
      assert user.email == "alice@example.com"
      assert user.display_name == "Alice"
    end

    test "display_name defaults to nil when only required fields are set" do
      user = %User.UserData{user_id: "u-1", email: "alice@example.com"}
      assert user.display_name == nil
    end

    test "supports struct update syntax" do
      user0 = %User.UserData{user_id: "u-1", email: "a@example.com", display_name: "A"}
      user1 = %{user0 | display_name: "Alice"}
      assert user1.user_id == "u-1"
      assert user1.display_name == "Alice"
    end
  end

  describe "GetUserInput struct" do
    test "requires user_id" do
      input = %User.GetUserInput{user_id: "u-1"}
      assert input.user_id == "u-1"
    end
  end
end
