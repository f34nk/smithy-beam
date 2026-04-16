defmodule SmithyClientTest do
  use ExUnit.Case, async: true

  alias SmithyClient.Operation

  # -------------------------------------------------------------------------
  # Operation struct
  # -------------------------------------------------------------------------

  describe "Operation struct" do
    test "can be created with all fields" do
      op = %Operation{
        name: :get_item,
        http: %{method: "POST", uri: "/"},
        input: %{table_name: "Users"},
        output_shape: :GetItemOutput,
        auth: :sigv4
      }

      assert op.name == :get_item
      assert op.http == %{method: "POST", uri: "/"}
      assert op.input == %{table_name: "Users"}
      assert op.output_shape == :GetItemOutput
      assert op.auth == :sigv4
    end

    test "auth defaults to :none" do
      op = %Operation{name: :list_items, http: %{method: "GET", uri: "/items"}, input: %{}, output_shape: :ListItemsOutput}
      assert op.auth == :none
    end

    test "allows nil fields to be set explicitly" do
      op = %Operation{}
      assert op.name == nil
      assert op.http == nil
      assert op.input == nil
      assert op.output_shape == nil
    end

    test "is a struct, not a plain map" do
      op = %Operation{name: :op}
      assert is_struct(op, Operation)
    end
  end

  # -------------------------------------------------------------------------
  # with_retry/2
  # -------------------------------------------------------------------------

  describe "with_retry/2" do
    test "success on first attempt returns immediately" do
      fun = fn -> {:ok, :result} end
      assert SmithyClient.with_retry(fun) == {:ok, :result}
    end

    test "success on first attempt with explicit opts" do
      fun = fn -> {:ok, "data"} end
      assert SmithyClient.with_retry(fun, max_retries: 5) == {:ok, "data"}
    end

    test "retries on error and succeeds eventually" do
      counter = :counters.new(1, [])

      fun = fn ->
        n = :counters.get(counter, 1)
        :counters.add(counter, 1, 1)
        if n < 2, do: {:error, :transient}, else: {:ok, :recovered}
      end

      assert SmithyClient.with_retry(fun, max_retries: 3) == {:ok, :recovered}
      assert :counters.get(counter, 1) == 3
    end

    test "returns last error when max retries exhausted" do
      fun = fn -> {:error, :always_fails} end
      # max_retries: 2 means 1 initial attempt + 2 retries = 3 total calls
      assert SmithyClient.with_retry(fun, max_retries: 2) == {:error, :always_fails}
    end

    test "default max_retries is 3" do
      counter = :counters.new(1, [])
      fun = fn ->
        :counters.add(counter, 1, 1)
        {:error, :fail}
      end
      SmithyClient.with_retry(fun)
      # 1 initial + 3 retries = 4 total
      assert :counters.get(counter, 1) == 4
    end

    test "max_retries: 0 calls function exactly once" do
      counter = :counters.new(1, [])
      fun = fn ->
        :counters.add(counter, 1, 1)
        {:error, :fail}
      end
      SmithyClient.with_retry(fun, max_retries: 0)
      assert :counters.get(counter, 1) == 1
    end

    test "does not retry when first call succeeds" do
      counter = :counters.new(1, [])
      fun = fn ->
        :counters.add(counter, 1, 1)
        {:ok, :done}
      end
      SmithyClient.with_retry(fun, max_retries: 5)
      assert :counters.get(counter, 1) == 1
    end

    test "accepts any {:ok, value} from fun" do
      assert SmithyClient.with_retry(fn -> {:ok, %{key: "value"}} end) == {:ok, %{key: "value"}}
    end
  end

  # -------------------------------------------------------------------------
  # stream/3 — pure structural tests (no HTTP)
  # -------------------------------------------------------------------------

  describe "stream/3 contract" do
    test "stream/3 returns an enumerable (function)" do
      client = %{endpoint: "http://localhost"}
      op = %Operation{name: :list, http: %{method: "POST", uri: "/"}, input: %{}, output_shape: :Out, auth: :none}
      # We cannot execute the stream (requires HTTP), but we can verify the return type.
      result = SmithyClient.stream(client, op, %{})
      assert is_function(result) or (is_struct(result) and Enumerable.impl_for(result) != nil)
    end
  end
end
