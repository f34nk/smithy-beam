defmodule SmithyValidatorTest do
  use ExUnit.Case, async: true

  # -------------------------------------------------------------------------
  # validate/2
  # -------------------------------------------------------------------------

  describe "validate/2" do
    test "returns :ok when all required fields present" do
      input = %{"name" => "Alice", "email" => "a@example.com"}
      assert SmithyValidator.validate(input, ["name", "email"]) == :ok
    end

    test "returns :ok when required fields list is empty" do
      assert SmithyValidator.validate(%{"x" => 1}, []) == :ok
    end

    test "returns :ok when input has more keys than required" do
      input = %{"name" => "Alice", "email" => "a@example.com", "age" => 30}
      assert SmithyValidator.validate(input, ["name"]) == :ok
    end

    test "returns error for a single missing field" do
      input = %{"email" => "a@example.com"}
      assert SmithyValidator.validate(input, ["name", "email"]) ==
               {:error, {:missing_required_fields, ["name"]}}
    end

    test "returns error listing all missing fields" do
      input = %{}
      assert {:error, {:missing_required_fields, missing}} =
               SmithyValidator.validate(input, ["name", "email"])

      assert Enum.sort(missing) == ["email", "name"]
    end

    test "returns error when input is completely empty" do
      assert {:error, {:missing_required_fields, ["id"]}} =
               SmithyValidator.validate(%{}, ["id"])
    end

    test "preserves field order in the error list" do
      input = %{}
      fields = ["a", "b", "c"]
      assert {:error, {:missing_required_fields, ^fields}} =
               SmithyValidator.validate(input, fields)
    end

    test "works with atom keys" do
      input = %{name: "Alice"}
      assert SmithyValidator.validate(input, [:name]) == :ok
    end

    test "atom keys not found by string keys (strict key matching)" do
      input = %{name: "Alice"}
      assert {:error, {:missing_required_fields, ["name"]}} =
               SmithyValidator.validate(input, ["name"])
    end

    test "returns :ok when input has nil values for required fields" do
      # nil is a valid map value — the key exists
      input = %{"name" => nil}
      assert SmithyValidator.validate(input, ["name"]) == :ok
    end
  end

  # -------------------------------------------------------------------------
  # format/1
  # -------------------------------------------------------------------------

  describe "format/1" do
    test "formats a single missing field" do
      result = SmithyValidator.format({:missing_required_fields, ["name"]})
      assert result == "Missing required fields: name"
    end

    test "formats multiple missing fields joined by comma" do
      result = SmithyValidator.format({:missing_required_fields, ["name", "email"]})
      assert result == "Missing required fields: name, email"
    end

    test "returns a string" do
      result = SmithyValidator.format({:missing_required_fields, ["x"]})
      assert is_binary(result)
    end

    test "starts with 'Missing required fields:'" do
      result = SmithyValidator.format({:missing_required_fields, ["x", "y"]})
      assert String.starts_with?(result, "Missing required fields:")
    end

    test "contains all field names in the output" do
      fields = ["alpha", "beta", "gamma"]
      result = SmithyValidator.format({:missing_required_fields, fields})

      for field <- fields do
        assert String.contains?(result, field)
      end
    end
  end

  # -------------------------------------------------------------------------
  # Integration: validate then format
  # -------------------------------------------------------------------------

  describe "validate/2 → format/1 integration" do
    test "error from validate can be formatted" do
      input = %{"email" => "a@b.com"}
      {:error, reason} = SmithyValidator.validate(input, ["name", "email"])
      message = SmithyValidator.format(reason)
      assert message =~ "name"
      assert String.starts_with?(message, "Missing required fields:")
    end

    test "round-trip for multiple missing fields" do
      {:error, reason} = SmithyValidator.validate(%{}, ["x", "y", "z"])
      message = SmithyValidator.format(reason)
      assert message == "Missing required fields: x, y, z"
    end
  end
end
