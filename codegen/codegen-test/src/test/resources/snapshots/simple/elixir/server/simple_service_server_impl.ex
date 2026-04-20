defmodule SimpleService.Server.Impl do
  @moduledoc false
  @behaviour SimpleService.Server

  # This file will NOT be overwritten. Add your business logic here.

  @impl true
  def get_item(_input, _ctx), do: {:error, :not_implemented}

end
