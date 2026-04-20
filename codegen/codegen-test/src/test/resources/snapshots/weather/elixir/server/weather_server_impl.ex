defmodule Weather.Server.Impl do
  @moduledoc false
  @behaviour Weather.Server

  # This file will NOT be overwritten. Add your business logic here.

  @impl true
  def get_current_time(_input, _ctx), do: {:error, :not_implemented}

  @impl true
  def get_forecast(_input, _ctx), do: {:error, :not_implemented}

end
