defmodule BasicServiceRouterTest do
  use ExUnit.Case, async: true

  alias RuntimeTypes.HttpRequest

  @moduledoc """
  Request flow: %HttpRequest{} -> BasicServiceRouter.dispatch/2 ->
  BasicServiceRestJson1.decode_get_type_closure_request/2 -> Handler.handle_get_type_closure/3.
  Pass BasicServiceServer as the handler module to invoke the generated server stub.
  """

  defp sample_request do
    %HttpRequest{
      method: "GET",
      path: "/types/widget",
      query: %{"verbose" => "true"},
      headers: [{"X-Request-Tag", "trace-1"}],
      body: ""
    }
  end

  test "dispatch routes list basic items before type prefix" do
    request = %HttpRequest{
      method: "GET",
      path: "/basic-items",
      query: %{},
      headers: [],
      body: ""
    }

    assert {:error, :not_implemented} == BasicServiceRouter.dispatch(BasicServiceServer, request)
  end

  test "dispatch not found for extra type path segments" do
    request = %HttpRequest{
      method: "GET",
      path: "/types/a/b",
      query: %{},
      headers: [],
      body: ""
    }

    assert {:error, {:not_found, "GET", "/types/a/b"}} ==
             BasicServiceRouter.dispatch(BasicServiceServer, request)
  end

  test "dispatch routes to BasicServer stub handler" do
    assert {:error, :not_implemented} ==
             BasicServiceRouter.dispatch(BasicServiceServer, sample_request())
  end

  test "dispatch decodes wire request before handler" do
    assert {:ok, out} = BasicServiceRouter.dispatch(BasicServiceServerProbe, sample_request())
    assert out.basic_string == "widget"
    assert out.basic_boolean == true
  end

  test "dispatch not found for unknown route" do
    request = %{sample_request() | method: "POST"}

    assert {:error, {:not_found, "POST", "/types/widget"}} ==
             BasicServiceRouter.dispatch(BasicServiceServer, request)
  end
end
