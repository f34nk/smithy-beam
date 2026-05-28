defmodule BasicRouterTest do
  use ExUnit.Case, async: true

  alias BasicRuntimeTypes.HttpRequest

  @moduledoc """
  Request flow: %HttpRequest{} -> BasicRouter.dispatch/2 ->
  BasicRestJson1.decode_get_type_closure_request/1 -> Handler.handle_get_type_closure/3.
  Pass BasicServer as the handler module to invoke the generated server stub.
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

  test "dispatch routes to BasicServer stub handler" do
    assert {:error, :not_implemented} ==
             BasicRouter.dispatch(BasicServer, sample_request())
  end

  test "dispatch decodes wire request before handler" do
    assert {:ok, out} = BasicRouter.dispatch(BasicServerProbe, sample_request())
    assert out.basic_string == "widget"
    assert out.basic_boolean == true
  end

  test "dispatch not found for unknown route" do
    request = %{sample_request() | method: "POST"}

    assert {:error, {:not_found, "POST", "/types/widget"}} ==
             BasicRouter.dispatch(BasicServer, request)
  end
end
