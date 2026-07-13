defmodule RuntimeHttpTest do
  use ExUnit.Case, async: true

  alias RuntimeTypes.HttpRequest

  describe "dispatch/3" do
    test "builds url without query" do
      config = %{base_url: "https://api.example"}

      req = %HttpRequest{
        method: "GET",
        path: "/items",
        query: %{},
        headers: [],
        body: ""
      }

      assert {:ok, resp} = RuntimeHttp.dispatch(HttpMock, config, req)
      assert resp.status == 200
      assert resp.body == ~s({"ok":true})
    end

    test "appends query string" do
      config = %{base_url: "https://api.example"}

      req = %HttpRequest{
        method: "GET",
        path: "/items",
        query: %{"verbose" => "true"},
        headers: [],
        body: ""
      }

      assert {:ok, resp} = RuntimeHttp.dispatch(HttpMock, config, req)
      assert resp.status == 200
      assert resp.body == ""
    end

    test "sends request body" do
      config = %{base_url: "https://api.example"}

      req = %HttpRequest{
        method: "POST",
        path: "/items",
        query: %{},
        headers: [{"Content-Type", "application/json"}],
        body: ~s({"name":"item"})
      }

      assert {:ok, resp} = RuntimeHttp.dispatch(HttpMock, config, req)
      assert resp.status == 201
      assert resp.body == ~s({"id":1})
    end

    test "propagates client error" do
      config = %{base_url: "https://api.example"}

      req = %HttpRequest{
        method: "GET",
        path: "/fail",
        query: %{},
        headers: [],
        body: ""
      }

      assert {:error, :timeout} == RuntimeHttp.dispatch(HttpMock, config, req)
    end
  end

  describe "with_retry/2" do
    test "returns ok without retry" do
      {:ok, ref} = Agent.start_link(fn -> 0 end)

      fun = fn ->
        Agent.update(ref, &(&1 + 1))
        {:ok, :done}
      end

      assert {:ok, :done} =
               RuntimeHttp.with_retry(fun, max_attempts: 3, base_delay_ms: 0)

      assert Agent.get(ref, & &1) == 1
      Agent.stop(ref)
    end

    test "retries retryable error" do
      {:ok, ref} = Agent.start_link(fn -> 0 end)

      fun = fn ->
        count = Agent.get_and_update(ref, fn n -> {n + 1, n + 1} end)

        if count == 1 do
          {:error, :retryable}
        else
          {:ok, :done}
        end
      end

      should_retry = fn
        {:error, :retryable} -> true
        _ -> false
      end

      assert {:ok, :done} =
               RuntimeHttp.with_retry(fun,
                 max_attempts: 3,
                 base_delay_ms: 0,
                 should_retry: should_retry
               )

      assert Agent.get(ref, & &1) == 2
      Agent.stop(ref)
    end

    test "stops on non-retryable error" do
      {:ok, ref} = Agent.start_link(fn -> 0 end)

      fun = fn ->
        Agent.update(ref, &(&1 + 1))
        {:error, :fatal}
      end

      assert {:error, :fatal} =
               RuntimeHttp.with_retry(fun, max_attempts: 3, base_delay_ms: 0)

      assert Agent.get(ref, & &1) == 1
      Agent.stop(ref)
    end
  end
end
