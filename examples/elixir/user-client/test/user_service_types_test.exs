defmodule UserTypesTest do
  use ExUnit.Case, async: true

  describe "UserData struct" do
    test "can be created with all fields" do
      user = %UserServiceTypes.UserData{
        user_id: "u-1",
        email: "alice@example.com",
        display_name: "Alice"
      }

      assert user.user_id == "u-1"
      assert user.email == "alice@example.com"
      assert user.display_name == "Alice"
    end

    test "display_name defaults to nil when only required fields are set" do
      user = %UserServiceTypes.UserData{user_id: "u-1", email: "alice@example.com"}
      assert user.display_name == nil
    end

    test "supports struct update syntax" do
      user0 = %UserServiceTypes.UserData{user_id: "u-1", email: "a@example.com", display_name: "A"}
      user1 = %{user0 | display_name: "Alice"}
      assert user1.user_id == "u-1"
      assert user1.display_name == "Alice"
    end
  end

  describe "CreateUserInput struct" do
    test "can be created with all fields" do
      input = %UserServiceTypes.CreateUserInput{email: "bob@example.com", display_name: "Bob"}
      assert input.email == "bob@example.com"
      assert input.display_name == "Bob"
    end

    test "display_name defaults to nil" do
      input = %UserServiceTypes.CreateUserInput{email: "bob@example.com"}
      assert input.display_name == nil
    end
  end
end
