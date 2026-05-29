defmodule UserServiceRouterTest do
  use ExUnit.Case, async: true

  alias RuntimeTypes.HttpRequest

  @moduledoc """
  Request flow: %HttpRequest{} -> UserServiceRouter.dispatch/2 ->
  UserServiceRestJson1.decode_get_user_request/2 -> Handler.handle_get_user/3.
  Pass UserServiceServer as the handler module to invoke the generated server stub.
  """

  defp get_user_request do
    %HttpRequest{
      method: "GET",
      path: "/users/u-1",
      query: %{},
      headers: [],
      body: ""
    }
  end

  test "dispatch routes list users before user id path" do
    request = %HttpRequest{
      method: "GET",
      path: "/users",
      query: %{},
      headers: [],
      body: ""
    }

    assert {:error, :not_implemented} == UserServiceRouter.dispatch(UserServiceServer, request)
  end

  test "dispatch not found for extra user path segments" do
    request = %HttpRequest{
      method: "GET",
      path: "/users/u-1/extra",
      query: %{},
      headers: [],
      body: ""
    }

    assert {:error, {:not_found, "GET", "/users/u-1/extra"}} ==
             UserServiceRouter.dispatch(UserServiceServer, request)
  end

  test "dispatch routes to UserServer stub handler" do
    assert {:error, :not_implemented} == UserServiceRouter.dispatch(UserServiceServer, get_user_request())
  end

  test "dispatch decodes wire request before handler" do
    assert {:ok, out} = UserServiceRouter.dispatch(UserServiceServerProbe, get_user_request())
    assert out.user == %{"userId" => "u-1"}
  end

  test "dispatch not found for unknown route" do
    request = %{get_user_request() | method: "POST"}

    assert {:error, {:not_found, "POST", "/users/u-1"}} ==
             UserServiceRouter.dispatch(UserServiceServer, request)
  end

  test "dispatch routes create user" do
    request = %HttpRequest{
      method: "POST",
      path: "/users",
      query: %{},
      headers: [{"Content-Type", "application/json"}],
      body: Jason.encode!(%{"email" => "a@example.com"})
    }

    assert {:error, :not_implemented} == UserServiceRouter.dispatch(UserServiceServer, request)
  end
end
