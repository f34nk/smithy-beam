defmodule BasicServiceImpl do
  @behaviour BasicServiceBehaviour

  alias BasicServiceTypes

  @impl true
  def handle_get_type_closure(_ctx, _input, _meta), do: {:error, :not_implemented}

  @impl true
  def handle_list_basic_items(_ctx, _input, _meta), do: {:error, :not_implemented}
end
