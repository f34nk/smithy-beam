defmodule RestJson1Service.Server.Impl do
  @moduledoc false
  @behaviour RestJson1Service.Server

  # This file will NOT be overwritten. Add your business logic here.

  @impl true
  def echo_message(_input, _ctx), do: {:error, :not_implemented}

end
