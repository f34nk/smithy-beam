defmodule WeatherServer.Impl do
  @moduledoc false
  @behaviour WeatherServer.Handler

  # This file will NOT be overwritten. Add your business logic here.

  def get_weather(_input, _ctx), do: {:error, :not_implemented}
  def list_cities(_input, _ctx), do: {:error, :not_implemented}
end
