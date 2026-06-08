defmodule UserServiceImpl do
  @behaviour UserServiceBehaviour

  alias UserServiceTypes

  @impl true
  def handle_create_user(_ctx, _input, _meta), do: {:error, :not_implemented}

  @impl true
  def handle_delete_user(_ctx, _input, _meta), do: {:error, :not_implemented}

  @impl true
  def handle_get_user(_ctx, _input, _meta), do: {:error, :not_implemented}

  @impl true
  def handle_list_users(_ctx, _input, _meta), do: {:error, :not_implemented}

  @impl true
  def handle_update_user(_ctx, _input, _meta), do: {:error, :not_implemented}
end
